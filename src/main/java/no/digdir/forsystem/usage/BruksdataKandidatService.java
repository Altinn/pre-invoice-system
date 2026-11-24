package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import no.digdir.forsystem.common.Periode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a complete post-export DWH snapshot without replacing the period's VALIDERT import, then
 * freezes its comparison with the latest exported run. This is deliberately separate from ordinary
 * import confirmation.
 */
@Service
public class BruksdataKandidatService {

    private static final List<String> KANDIDATSTATUSER =
            List.of(Importstatus.KANDIDAT.name(), Importstatus.BEHANDLET.name());

    private final BruksdataImportRepository importer;
    private final BruksdataRepository bruksdata;
    private final BruksdataSammenligningRepository sammenligninger;
    private final BruksdataEndringRepository endringer;
    private final KundeavvikRepository kundeavvik;
    private final UsageImportService usage;
    private final KjoringStatusPort kjoringer;
    private final BrukerContext brukerContext;
    private final AuditService audit;

    BruksdataKandidatService(BruksdataImportRepository importer, BruksdataRepository bruksdata,
                             BruksdataSammenligningRepository sammenligninger,
                             BruksdataEndringRepository endringer, KundeavvikRepository kundeavvik,
                             UsageImportService usage, KjoringStatusPort kjoringer, BrukerContext brukerContext,
                             AuditService audit) {
        this.importer = importer;
        this.bruksdata = bruksdata;
        this.sammenligninger = sammenligninger;
        this.endringer = endringer;
        this.kundeavvik = kundeavvik;
        this.usage = usage;
        this.kjoringer = kjoringer;
        this.brukerContext = brukerContext;
        this.audit = audit;
    }

    /** Empty means the period has no exported baseline and must keep the ordinary preview flow. */
    @Transactional
    public Optional<Resultat> opprettHvisEksportert(Forhaandsvisning fv) {
        Optional<KjoringStatusPort.EffektivtGrunnlag> grunnlag =
                kjoringer.effektivtGrunnlag(fv.periode().førsteDag());
        if (grunnlag.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(opprett(fv, grunnlag.get()));
    }

    private Resultat opprett(Forhaandsvisning fv, KjoringStatusPort.EffektivtGrunnlag grunnlag) {
        if (fv.kilde() != Kilde.DWH || fv.raadata() == null) {
            throw new Regelbrudd("Korrigering krever en komplett eksport fra datavarehuset");
        }
        if (!fv.avviste().isEmpty() || fv.gyldige().isEmpty()) {
            throw new Regelbrudd("Kandidaten kan ikke sammenlignes før alle radfeil er rettet");
        }
        if (grunnlag.rotImportId() == null) {
            throw new Regelbrudd("Den eksporterte kjøringen mangler kobling til bruksdata og kan ikke sammenlignes");
        }

        String sha256 = sha256(fv.raadata().innhold());
        BruksdataImport kandidat = importer
                .findFirstByPeriodeAndRaadataSha256AndStatusIn(
                        fv.periode().førsteDag(), sha256, KANDIDATSTATUSER)
                .orElseGet(() -> lagreKandidat(fv, sha256));

        return sammenlign(kandidat, fv.periode().førsteDag(), grunnlag);
    }

    /**
     * «Sammenlign på nytt» (specs/019 FR-013): compare the period's latest candidate with the current effective
     * invoiced basis, without a new fetch or upload. Idempotent per (candidate, baseline).
     */
    @Transactional
    public Resultat sammenlignPaaNytt(Long sammenligningId) {
        BruksdataSammenligning forrige = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        LocalDate periode = forrige.periode();
        if (kjoringer.finnesPaagaaendeKjoring(periode)) {
            throw new Regelbrudd("Det finnes en pågående kjøring for " + new Periode(periode).norskLabel()
                    + ". Eksporter eller forkast den før du sammenligner på nytt.");
        }
        KjoringStatusPort.EffektivtGrunnlag grunnlag = kjoringer.effektivtGrunnlag(periode)
                .orElseThrow(() -> new Regelbrudd("Perioden har ingen eksportert kjøring å sammenligne med"));
        if (grunnlag.rotImportId() == null) {
            throw new Regelbrudd("Den eksporterte kjøringen mangler kobling til bruksdata og kan ikke sammenlignes");
        }
        BruksdataImport kandidat = importer.findFirstByPeriodeAndStatusInOrderByIdDesc(periode, KANDIDATSTATUSER)
                .orElseThrow(() -> new Regelbrudd("Perioden har ingen kandidat å sammenligne"));
        return sammenlign(kandidat, periode, grunnlag);
    }

    private Resultat sammenlign(BruksdataImport kandidat, LocalDate periode,
                                KjoringStatusPort.EffektivtGrunnlag grunnlag) {

        Optional<BruksdataSammenligning> eksisterende = sammenligninger
                .findByKandidatImportIdAndGrunnlagKjoringId(
                        kandidat.id(), grunnlag.sisteEksporterteKjoringId());
        if (eksisterende.isPresent()) {
            return resultat(kandidat, eksisterende.get());
        }

        List<Bruksdata> gamle = effektivtGrunnlag(grunnlag);
        if (gamle.isEmpty()) {
            throw new Regelbrudd("Den eksporterte kjøringen har ikke et komplett bruksdatagrunnlag");
        }
        List<Bruksdata> nye = bruksdata.findByImportId(kandidat.id());
        BruksdataDifferanse.Resultat diff = BruksdataDifferanse.sammenlign(gamle, nye);
        // Per customer (specs/019): product changes plus customer values that differ from what was invoiced.
        Map<String, BruksdataKunde> kandidatKunder = new LinkedHashMap<>();
        usage.kundedata(kandidat.id()).forEach(k -> kandidatKunder.put(k.organisasjonsnummer(), k));
        KundeavvikBygger.Resultat perKunde = KundeavvikBygger.bygg(diff.endringer(),
                gamle.stream().map(Bruksdata::organisasjonsnummer).collect(Collectors.toSet()),
                nye.stream().map(Bruksdata::organisasjonsnummer).collect(Collectors.toSet()),
                kjoringer.fakturerteKunder(periode), kandidatKunder);

        Optional<BruksdataSammenligning> forrigeForKandidat =
                sammenligninger.findFirstByKandidatImportIdOrderByOpprettetAtDesc(kandidat.id());
        List<BruksdataSammenligning> aapne = sammenligninger.findByPeriodeAndStatus(
                periode, Sammenligningstatus.AAPEN.name());
        aapne.forEach(s -> sammenligninger.save(s.medStatus(Sammenligningstatus.ERSTATTET.name(), null)));

        BruksdataSammenligning sammenligning = sammenligninger.save(new BruksdataSammenligning(
                null, periode, kandidat.id(), grunnlag.sisteEksporterteKjoringId(),
                Sammenligningstatus.AAPEN.name(), diff.uendredeRader(), brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), null, perKunde.uendredeKunder()));
        Map<String, Long> kundeavvikId = new LinkedHashMap<>();
        kundeavvik.saveAll(perKunde.avvik().stream().map(a -> a.tilKundeavvik(sammenligning.id())).toList())
                .forEach(a -> kundeavvikId.put(a.organisasjonsnummer(), a.id()));
        List<BruksdataEndring> lagredeEndringer = endringer.saveAll(diff.endringer().stream()
                .map(e -> BruksdataEndring.fra(sammenligning.id(),
                        kundeavvikId.get(e.nokkel().organisasjonsnummer()), e)).toList());
        aapne.forEach(s -> sammenligninger.save(s.medStatus(
                Sammenligningstatus.ERSTATTET.name(), sammenligning.id())));

        forrigeForKandidat.ifPresent(f -> viderefoerAvvisninger(f, sammenligning));

        audit.logg(Handling.SAMMENLIGNET, "BRUKSDATA_SAMMENLIGNING", sammenligning.id(), Map.of(
                "periode", new Periode(periode).toString(),
                "kandidatImportId", kandidat.id(),
                "grunnlagKjoringId", grunnlag.sisteEksporterteKjoringId(),
                "grunnlagVersjon", grunnlag.sisteVersjon(),
                "endringer", lagredeEndringer.size(),
                "kunderMedAvvik", kundeavvikId.size(),
                "uendrede", diff.uendredeRader(),
                "raadataSha256", kandidat.raadataSha256()));
        return resultat(kandidat, sammenligning);
    }

    /**
     * A dismissal carries over to a new comparison of the <em>same</em> candidate when the customer's deviation is
     * identical (FR-016): the same categories, values and product changes. Anything else is pending again.
     */
    private void viderefoerAvvisninger(BruksdataSammenligning forrige, BruksdataSammenligning ny) {
        List<Kundeavvik> avviste = kundeavvik.findBySammenligningIdOrderByOrganisasjonsnummer(forrige.id()).stream()
                .filter(Kundeavvik::erAvvist).toList();
        if (avviste.isEmpty()) {
            return;
        }
        Map<Long, List<BruksdataEndring>> gamleEndringer = perKunde(forrige.id());
        Map<Long, List<BruksdataEndring>> nyeEndringer = perKunde(ny.id());
        Map<String, Kundeavvik> nye = new LinkedHashMap<>();
        kundeavvik.findBySammenligningIdOrderByOrganisasjonsnummer(ny.id())
                .forEach(a -> nye.put(a.organisasjonsnummer(), a));
        List<String> videreført = new ArrayList<>();
        for (Kundeavvik gammel : avviste) {
            Kundeavvik kandidatAvvik = nye.get(gammel.organisasjonsnummer());
            if (kandidatAvvik == null || !fingeravtrykk(gammel, gamleEndringer.getOrDefault(gammel.id(), List.of()))
                    .equals(fingeravtrykk(kandidatAvvik, nyeEndringer.getOrDefault(kandidatAvvik.id(), List.of())))) {
                continue;
            }
            settAvvisning(kandidatAvvik, nyeEndringer.getOrDefault(kandidatAvvik.id(), List.of()),
                    gammel.avvistAv(), gammel.avvistAt(), gammel.avvistAarsak());
            videreført.add(gammel.organisasjonsnummer());
        }
        if (!videreført.isEmpty()) {
            audit.logg(Handling.AVVISTE, "BRUKSDATA_SAMMENLIGNING", ny.id(), Map.of(
                    "organisasjonsnumre", videreført,
                    "videreførtFraSammenligningId", forrige.id()));
        }
    }

    private Map<Long, List<BruksdataEndring>> perKunde(Long sammenligningId) {
        return endringer.findBySammenligningIdOrderById(sammenligningId).stream()
                .collect(Collectors.groupingBy(BruksdataEndring::kundeavvikId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private static String fingeravtrykk(Kundeavvik a, List<BruksdataEndring> endringerForKunden) {
        StringBuilder sb = new StringBuilder();
        sb.append(new TreeSet<>(Arrays.asList(a.kategorier())))
                .append(Arrays.toString(a.fakturertNavn())).append(Arrays.toString(a.fakturertKundenummer()))
                .append(Arrays.toString(a.fakturertFakturareferanse()))
                .append(Arrays.toString(a.fakturertBestillingsnummer()))
                .append('|').append(KundeavvikBygger.normaliser(a.kandidatNavn()))
                .append('|').append(KundeavvikBygger.normaliser(a.kandidatKundenummer()))
                .append('|').append(KundeavvikBygger.normaliser(a.kandidatFakturareferanse()))
                .append('|').append(KundeavvikBygger.normaliser(a.kandidatBestillingsnummer()));
        endringerForKunden.stream()
                .map(e -> e.produktId() + "/" + e.type() + "/" + e.endringstype() + "/" + tall(e.gammelVerdi())
                        + "/" + tall(e.nyVerdi()))
                .sorted().forEach(x -> sb.append(';').append(x));
        return sb.toString();
    }

    private static String tall(BigDecimal verdi) {
        return verdi == null ? "-" : verdi.stripTrailingZeros().toPlainString();
    }

    /** Dismiss one or more customers of an open comparison with a reason (FR-015). */
    @Transactional
    public void avvis(Long sammenligningId, List<String> orgnr, String aarsak) {
        if (aarsak == null || aarsak.isBlank()) {
            throw new Regelbrudd("Skriv hvorfor kunden avvises");
        }
        BruksdataSammenligning sammenligning = aapen(sammenligningId);
        List<Kundeavvik> valgte = kunder(sammenligning, orgnr);
        if (valgte.stream().anyMatch(Kundeavvik::erAvvist)) {
            throw new Regelbrudd("Kunden er allerede avvist");
        }
        String bruker = brukerContext.naavaerendeBruker();
        OffsetDateTime naa = OffsetDateTime.now();
        Map<Long, List<BruksdataEndring>> perKunde = perKunde(sammenligningId);
        for (Kundeavvik a : valgte) {
            settAvvisning(a, perKunde.getOrDefault(a.id(), List.of()), bruker, naa, aarsak.trim());
        }
        audit.logg(Handling.AVVISTE, "BRUKSDATA_SAMMENLIGNING", sammenligningId, Map.of(
                "organisasjonsnumre", valgte.stream().map(Kundeavvik::organisasjonsnummer).toList(),
                "endringer", valgte.stream().flatMap(a -> perKunde.getOrDefault(a.id(), List.of()).stream())
                        .map(BruksdataEndring::id).toList(),
                "aarsak", aarsak.trim()));
    }

    /** Make a dismissed customer pending again while the comparison is open (FR-015). */
    @Transactional
    public void gjenopprett(Long sammenligningId, String orgnr) {
        BruksdataSammenligning sammenligning = aapen(sammenligningId);
        Kundeavvik a = kunder(sammenligning, List.of(orgnr)).getFirst();
        if (!a.erAvvist()) {
            throw new Regelbrudd("Kunden er ikke avvist");
        }
        settAvvisning(a, perKunde(sammenligningId).getOrDefault(a.id(), List.of()), null, null, null);
        audit.logg(Handling.GJENOPPRETTET, "BRUKSDATA_SAMMENLIGNING", sammenligningId, Map.of(
                "organisasjonsnummer", orgnr,
                "avvistAv", a.avvistAv(),
                "avvistAarsak", a.avvistAarsak()));
    }

    private BruksdataSammenligning aapen(Long sammenligningId) {
        BruksdataSammenligning sammenligning = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        if (!Sammenligningstatus.AAPEN.name().equals(sammenligning.status())) {
            throw new Regelbrudd("Sammenligningen er ikke lenger åpen. Sammenlign på nytt.");
        }
        return sammenligning;
    }

    private List<Kundeavvik> kunder(BruksdataSammenligning sammenligning, List<String> orgnr) {
        if (orgnr == null || orgnr.isEmpty()) {
            throw new Regelbrudd("Velg minst én kunde");
        }
        Set<String> onsket = new LinkedHashSet<>(orgnr);
        List<Kundeavvik> valgte = kundeavvik.findBySammenligningIdOrderByOrganisasjonsnummer(sammenligning.id())
                .stream().filter(a -> onsket.contains(a.organisasjonsnummer())).toList();
        if (valgte.size() != onsket.size()) {
            throw new Regelbrudd("En eller flere kunder finnes ikke i sammenligningen");
        }
        return valgte;
    }

    private void settAvvisning(Kundeavvik a, List<BruksdataEndring> endringerForKunden, String av,
                               OffsetDateTime at, String aarsak) {
        kundeavvik.save(a.medAvvisning(av, at, aarsak));
        endringer.saveAll(endringerForKunden.stream().map(e -> e.medAvvisning(av, at, aarsak)).toList());
    }

    private Resultat resultat(BruksdataImport kandidat, BruksdataSammenligning sammenligning) {
        return new Resultat(kandidat, sammenligning,
                endringer.findBySammenligningIdOrderById(sammenligning.id()),
                kundeavvik.findBySammenligningIdOrderByOrganisasjonsnummer(sammenligning.id()));
    }

    private List<Bruksdata> effektivtGrunnlag(KjoringStatusPort.EffektivtGrunnlag grunnlag) {
        Map<BruksdataNokkel, Bruksdata> effektivt = new LinkedHashMap<>();
        bruksdata.findByImportId(grunnlag.rotImportId())
                .forEach(rad -> effektivt.put(BruksdataNokkel.fra(rad), rad));

        Map<Long, BruksdataEndring> endringById = new LinkedHashMap<>();
        endringer.findAllById(grunnlag.eksporterteEndringIder())
                .forEach(endring -> endringById.put(endring.id(), endring));
        for (Long endringId : grunnlag.eksporterteEndringIder()) {
            BruksdataEndring endring = endringById.get(endringId);
            if (endring == null) {
                throw new Regelbrudd("Det eksporterte korrigeringsgrunnlaget mangler endring " + endringId);
            }
            BruksdataNokkel nokkel = new BruksdataNokkel(
                    endring.organisasjonsnummer(), endring.produktId(), endring.type());
            if (Endringstype.FJERNET.name().equals(endring.endringstype())) {
                effektivt.remove(nokkel);
            } else {
                Bruksdata ny = bruksdata.findById(endring.nyBruksdataId())
                        .orElseThrow(() -> new Regelbrudd(
                                "Det eksporterte korrigeringsgrunnlaget mangler ny bruksdatarad for endring "
                                        + endringId));
                effektivt.put(nokkel, ny);
            }
        }
        return List.copyOf(effektivt.values());
    }

    private BruksdataImport lagreKandidat(Forhaandsvisning fv, String forventetSha256) {
        Raadata arkivert = fv.raadata().erArkivert() ? fv.raadata() : usage.arkiver(fv.periode(), fv.raadata());
        if (!forventetSha256.equals(arkivert.sha256())) {
            throw new Regelbrudd("Arkivert kandidat har en annen sjekksum enn mottatt rådata");
        }
        BruksdataImport kandidat = importer.save(new BruksdataImport(null, fv.filnavn(),
                fv.periode().førsteDag(), Kilde.DWH.name(), Importstatus.KANDIDAT.name(),
                fv.antallGyldige(), brukerContext.naavaerendeBruker(), OffsetDateTime.now(),
                arkivert.url(), arkivert.sha256(), arkivert.hentetAt()));
        bruksdata.saveAll(fv.gyldige().stream().map(rad -> new Bruksdata(null, kandidat.id(), rad.periode(),
                rad.organisasjonsnummer(), rad.produktId(), rad.type(), rad.antall(), rad.belop())).toList());
        // The correction run reads its customer values from the candidate (specs/018), as an ordinary run does
        // from its import.
        usage.lagreKunder(fv, kandidat.id());
        audit.logg(Handling.HENTET, "BRUKSDATA_IMPORT", kandidat.id(), Map.of(
                "periode", fv.periode().toString(), "status", Importstatus.KANDIDAT.name(),
                "antall", fv.antallGyldige(), "raadataSha256", forventetSha256));
        return kandidat;
    }

    @Transactional(readOnly = true)
    public Resultat hent(Long id) {
        BruksdataSammenligning sammenligning = sammenligninger.findById(id)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + id));
        BruksdataImport kandidat = importer.findById(sammenligning.kandidatImportId())
                .orElseThrow(() -> new Regelbrudd("Fant ikke kandidatimport " + sammenligning.kandidatImportId()));
        return resultat(kandidat, sammenligning);
    }

    @Transactional(readOnly = true)
    public Optional<BruksdataSammenligning> sisteSammenligningForKandidat(Long kandidatImportId) {
        return sammenligninger.findFirstByKandidatImportIdOrderByOpprettetAtDesc(kandidatImportId);
    }

    private static String sha256(byte[] innhold) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(innhold));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM mangler SHA-256", e);
        }
    }

    public record Resultat(
            BruksdataImport kandidat,
            BruksdataSammenligning sammenligning,
            List<BruksdataEndring> endringer,
            List<Kundeavvik> kundeavvik) {

        /** The product changes of each customer deviation, by {@link Kundeavvik#id()}. */
        public Map<Long, List<BruksdataEndring>> endringerPerKunde() {
            return endringer.stream().collect(Collectors.groupingBy(BruksdataEndring::kundeavvikId,
                    LinkedHashMap::new, Collectors.toList()));
        }
    }
}
