package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.common.UuidV7;
import no.digdir.forsystem.registry.Pris;
import no.digdir.forsystem.registry.PrisRepository;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import no.digdir.forsystem.registry.PrisversjonStatus;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.BruksdataKunde;
import no.digdir.forsystem.usage.Kundeavvik;
import no.digdir.forsystem.usage.KundeavvikRepository;
import no.digdir.forsystem.usage.Kundedata;
import no.digdir.forsystem.usage.BruksdataImportRepository;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataEndringRepository;
import no.digdir.forsystem.usage.BruksdataSammenligning;
import no.digdir.forsystem.usage.BruksdataSammenligningRepository;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Importstatus;
import no.digdir.forsystem.usage.Sammenligningstatus;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The generation engine, controls and approval state machine (docs/04 Phase 3). Deterministic:
 * for the same inputs it produces identical invoices and lines, ordered by (kunde orgnr,
 * kundenummer) for invoices and (produkt kode, type) for lines (docs/05). The billing period is
 * always an explicit parameter.
 */
@Service
public class FakturakjoringService {

    /** Largest amount {@code faktura.sum_belop numeric(12,2)} can hold. */
    static final BigDecimal MAKS_FAKTURABELOP = new BigDecimal("9999999999.99");

    private static final BigDecimal AVVIK_GRENSE = new BigDecimal("0.30");

    private final FakturakjoringRepository kjoringer;
    private final FakturaRepository fakturaer;
    private final FakturalinjeRepository linjer;
    private final KontrollfunnRepository funn;
    private final EksportfilRepository eksportfiler;
    private final KontrollfunnBruksdataRepository funnBruksdata;
    private final FakturaUnderTerskelRepository underTerskel;
    private final ProduktRepository produkter;
    private final PrisRepository priser;
    private final PrisversjonRepository prisversjoner;
    private final UsageImportService usage;
    private final BruksdataImportRepository usageImports;
    private final BruksdataSammenligningRepository sammenligninger;
    private final BruksdataEndringRepository endringer;
    private final KundeavvikRepository kundeavvik;
    private final KorrigeringskjoringEndringRepository valgteEndringer;
    private final KjoringVersjonService versjoner;
    private final AvregningRepository avregninger;
    private final AuditService audit;
    private final BrukerContext brukerContext;

    FakturakjoringService(FakturakjoringRepository kjoringer, FakturaRepository fakturaer,
                          FakturalinjeRepository linjer, KontrollfunnRepository funn,
                          EksportfilRepository eksportfiler, KontrollfunnBruksdataRepository funnBruksdata,
                          FakturaUnderTerskelRepository underTerskel,
                          ProduktRepository produkter, PrisRepository priser,
                          PrisversjonRepository prisversjoner, UsageImportService usage, BruksdataImportRepository usageImports,
                          BruksdataSammenligningRepository sammenligninger,
                          BruksdataEndringRepository endringer, KundeavvikRepository kundeavvik,
                          KorrigeringskjoringEndringRepository valgteEndringer,
                          KjoringVersjonService versjoner, AvregningRepository avregninger,
                          AuditService audit, BrukerContext brukerContext) {
        this.kjoringer = kjoringer;
        this.fakturaer = fakturaer;
        this.linjer = linjer;
        this.funn = funn;
        this.eksportfiler = eksportfiler;
        this.funnBruksdata = funnBruksdata;
        this.underTerskel = underTerskel;
        this.produkter = produkter;
        this.priser = priser;
        this.prisversjoner = prisversjoner;
        this.usage = usage;
        this.usageImports = usageImports;
        this.sammenligninger = sammenligninger;
        this.endringer = endringer;
        this.kundeavvik = kundeavvik;
        this.valgteEndringer = valgteEndringer;
        this.versjoner = versjoner;
        this.avregninger = avregninger;
        this.audit = audit;
        this.brukerContext = brukerContext;
    }

    // ---- Generation -------------------------------------------------------------------------

    @Transactional
    public Fakturakjoring generer(Periode periode) {
        // Missing usage is reported before a missing price version: it is the step users skip, and a
        // month with neither used to say only «Ingen aktiv prisversjon» (kickoff: «feil i feilmelding»).
        Long importId = usage.gjeldendeImport(periode).map(BruksdataImport::id).orElse(null);
        List<Bruksdata> bruk = usage.bruksdataFor(periode);
        if (importId == null || bruk.isEmpty()) {
            throw new Regelbrudd("Ingen bruksdata for " + periode + " — importer bruk først.");
        }
        Prisversjon versjon = aktivVersjonFor(periode);
        versjoner.laas(periode.førsteDag());
        if (kjoringer.existsByPeriodeAndStatusNot(periode.førsteDag(), Kjoringstatus.FORKASTET.name())) {
            throw new Regelbrudd("Det finnes allerede en aktiv kjøring for " + periode
                    + ". Forkast den før du genererer på nytt.");
        }

        int kjoringVersjon = versjoner.neste(periode.førsteDag());
        Long forrigeKjoringId = kjoringer.findFirstByPeriodeOrderByVersjonDesc(periode.førsteDag())
                .map(Fakturakjoring::id).orElse(null);

        Fakturatekster tekster = Fakturatekster.standard(periode);
        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, periode.førsteDag(),
                Kjoringstatus.GENERERT.name(), versjon.id(), importId, brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), null, null, null, Kjoringstype.ORDINAER.name(), kjoringVersjon,
                forrigeKjoringId, null, null, tekster.topptekst(), tekster.bunntekst(), tekster.varReferanse()));

        var ctx = new Byggekontekst(periode, versjon, kjoring.id(), List.of(importId), false);
        bygg(ctx, bruk);
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(),
                Map.of("periode", periode.toString(), "antallFakturaer", ctx.antallFakturaer));
        return kjoring;
    }

    /**
     * Generate a delta correction for the ticked customers (specs/019 FR-009): every pending product change of
     * each customer. {@code alle} ticks every customer of the comparison a delta can correct, also those on other
     * pages.
     */
    @Transactional
    public Fakturakjoring genererKorrigeringForKunder(Long sammenligningId, List<String> orgnr, boolean alle,
                                                      String aarsak) {
        List<Kundeavvik> avvik = kundeavvik.findBySammenligningIdOrderByOrganisasjonsnummer(sammenligningId);
        List<Kundeavvik> valgte;
        if (alle) {
            // «Velg alle» means every customer a delta can correct; the others cannot be ticked on the page.
            Set<Long> medEndring = endringer.findBySammenligningIdOrderById(sammenligningId).stream()
                    .filter(e -> e.avvistAt() == null).map(BruksdataEndring::kundeavvikId)
                    .collect(Collectors.toSet());
            valgte = avvik.stream().filter(a -> !a.erAvvist())
                    .filter(a -> a.hindrerDifferanse().isEmpty() && medEndring.contains(a.id())).toList();
        } else {
            Set<String> onsket = orgnr == null ? Set.of() : new LinkedHashSet<>(orgnr);
            valgte = avvik.stream().filter(a -> onsket.contains(a.organisasjonsnummer())).toList();
            if (valgte.size() != onsket.size()) {
                throw new Regelbrudd("En eller flere valgte kunder finnes ikke i sammenligningen");
            }
        }
        if (valgte.isEmpty()) {
            throw new Regelbrudd("Velg minst én kunde som skal være med i korrigeringen");
        }
        List<Kundeavvik> avviste = valgte.stream().filter(Kundeavvik::erAvvist).toList();
        if (!avviste.isEmpty()) {
            throw new Regelbrudd("Disse kundene er avvist og kan ikke tas med: " + kunder(avviste)
                    + ". Gjenopprett dem først.");
        }
        avvisKundeverdiendringer(valgte);
        Set<Long> valgteIder = valgte.stream().map(Kundeavvik::id).collect(Collectors.toSet());
        List<BruksdataEndring> kundenesEndringer = endringer.findBySammenligningIdOrderById(sammenligningId).stream()
                .filter(e -> valgteIder.contains(e.kundeavvikId()) && e.avvistAt() == null).toList();
        Set<Long> medEndring = kundenesEndringer.stream().map(BruksdataEndring::kundeavvikId)
                .collect(Collectors.toSet());
        List<Kundeavvik> utenEndring = valgte.stream().filter(a -> !medEndring.contains(a.id())).toList();
        if (!utenEndring.isEmpty()) {
            // Only a changed name: it is not in the invoice file, so there is nothing a correction could change.
            throw new Regelbrudd("Disse kundene har ingen endring i bruk som kan korrigeres: "
                    + kunder(utenEndring) + ". Fjern krysset for dem.");
        }
        return genererKorrigering(sammenligningId,
                kundenesEndringer.stream().map(BruksdataEndring::id).toList(), aarsak);
    }

    /**
     * A delta cannot correct a changed fakturareferanse, bestillingsnummer or kundenummer: the delta invoice would
     * carry the new value while the invoice it corrects carried the old one (specs/019 FR-011). Awaits the credit
     * mechanism, TODO(OQ-6).
     */
    private void avvisKundeverdiendringer(List<Kundeavvik> valgte) {
        List<Kundeavvik> hindret = valgte.stream().filter(a -> !a.hindrerDifferanse().isEmpty()).toList();
        if (!hindret.isEmpty()) {
            throw new Regelbrudd("Endret fakturareferanse, bestillingsnummer eller kundenummer kan ikke korrigeres "
                    + "med differanser før avregningsmåten er avklart med Økonomi (OQ-6). Fjern krysset for "
                    + kunder(hindret) + ".");
        }
    }

    private static String kunder(List<Kundeavvik> avvik) {
        return avvik.stream().map(a -> a.navn().equals(a.organisasjonsnummer())
                        ? a.organisasjonsnummer() : a.navn() + " (" + a.organisasjonsnummer() + ")")
                .collect(Collectors.joining(", "));
    }

    /**
     * Where a period stands for corrections: the run in progress (GENERERT/GODKJENT), if any, and the latest
     * exported run. Read by the comparison page (specs/019 FR-013).
     */
    @Transactional(readOnly = true)
    public Korrigeringsstatus korrigeringsstatus(LocalDate periode) {
        Fakturakjoring paagaaende = kjoringer.findByPeriodeAndStatusIn(periode,
                List.of(Kjoringstatus.GENERERT.name(), Kjoringstatus.GODKJENT.name())).stream()
                .findFirst().orElse(null);
        Long sisteEksporterte = kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(
                periode, Kjoringstatus.EKSPORTERT.name()).map(Fakturakjoring::id).orElse(null);
        return new Korrigeringsstatus(paagaaende, sisteEksporterte);
    }

    public record Korrigeringsstatus(Fakturakjoring paagaaende, Long sisteEksporterteId) {
    }

    /** Unit prices of a run's price version per product id; a correction prices its deltas with these. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> enhetspriser(Long kjoringId) {
        Map<Long, BigDecimal> ut = new LinkedHashMap<>();
        Long versjonId = hent(kjoringId).prisversjonId();
        if (versjonId == null) {
            return ut;
        }
        priser.findByPrisversjonId(versjonId)
                .forEach(p -> ut.put(p.produktId(), p.enhetspris()));
        return ut;
    }

    /** Generate a draft containing only the explicitly selected signed differences. */
    @Transactional
    public Fakturakjoring genererKorrigering(Long sammenligningId, List<Long> endringIder, String aarsak) {
        if (aarsak == null || aarsak.isBlank()) {
            throw new Regelbrudd("Skriv hvorfor korrigeringen opprettes");
        }
        List<Long> unikeIder = endringIder == null ? List.of() : endringIder.stream().distinct().toList();
        if (unikeIder.isEmpty()) {
            throw new Regelbrudd("Velg minst én endring som skal være med i korrigeringen");
        }
        BruksdataSammenligning foerLaas = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        versjoner.laas(foerLaas.periode());
        BruksdataSammenligning sammenligning = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        if (!Sammenligningstatus.AAPEN.name().equals(sammenligning.status())) {
            throw new Regelbrudd("Sammenligningen er ikke lenger åpen. Sammenlign på nytt.");
        }
        Fakturakjoring grunnlag = hent(sammenligning.grunnlagKjoringId());
        Optional<Fakturakjoring> sisteEksporterte = kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(
                sammenligning.periode(), Kjoringstatus.EKSPORTERT.name());
        if (sisteEksporterte.isEmpty() || !sisteEksporterte.get().id().equals(grunnlag.id())) {
            throw new Regelbrudd("Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt.");
        }
        if (kjoringer.existsByPeriodeAndStatusIn(sammenligning.periode(),
                List.of(Kjoringstatus.GENERERT.name(), Kjoringstatus.GODKJENT.name()))) {
            throw new Regelbrudd("Det finnes allerede en pågående kjøring for "
                    + new Periode(sammenligning.periode()).norskLabel());
        }

        List<BruksdataEndring> valgte = endringer.findAllById(unikeIder);
        if (valgte.size() != unikeIder.size()
                || valgte.stream().anyMatch(e -> !sammenligningId.equals(e.sammenligningId())
                        || e.avvistAt() != null)) {
            throw new Regelbrudd("En eller flere valgte endringer tilhører ikke den åpne sammenligningen");
        }
        Set<Long> kundeIder = valgte.stream().map(BruksdataEndring::kundeavvikId).collect(Collectors.toSet());
        avvisKundeverdiendringer(kundeavvik.findAllById(kundeIder));
        BruksdataImport kandidat = usageImports.findById(sammenligning.kandidatImportId())
                .orElseThrow(() -> new Regelbrudd("Fant ikke kandidatimport " + sammenligning.kandidatImportId()));
        Prisversjon prisversjon = prisversjoner.findById(grunnlag.prisversjonId())
                .orElseThrow(() -> new Regelbrudd("Fant ikke prisversjonen fra " + grunnlag.versjonLabel()));
        int versjon = versjoner.neste(sammenligning.periode());
        Long forrigeId = kjoringer.findFirstByPeriodeOrderByVersjonDesc(sammenligning.periode())
                .map(Fakturakjoring::id).orElse(grunnlag.id());
        String bruker = brukerContext.naavaerendeBruker();
        OffsetDateTime naa = OffsetDateTime.now();
        Fakturatekster tekster = Fakturatekster.standard(new Periode(sammenligning.periode()));
        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, sammenligning.periode(),
                Kjoringstatus.GENERERT.name(), prisversjon.id(), kandidat.id(), bruker, naa,
                null, null, null, Kjoringstype.KORRIGERING.name(), versjon, forrigeId,
                aarsak.trim(), sammenligning.id(), tekster.topptekst(), tekster.bunntekst(),
                tekster.varReferanse()));
        valgteEndringer.lagre(kjoring.id(), unikeIder, bruker, naa);

        List<Bruksdata> differanser = valgte.stream().map(e -> new Bruksdata(
                e.id(), kandidat.id(), sammenligning.periode(), e.organisasjonsnummer(), e.produktId(),
                e.type(), e.deltaAntall(), e.deltaBelop())).toList();
        // The candidate's customer values first: they are what CRM says now. A removal can concern an orgnr the
        // candidate no longer has at all; it is credited with the values it was invoiced with, so the period's
        // exported imports follow, newest first (specs/007 FR-014).
        List<Long> kundedataImporter = new ArrayList<>();
        kundedataImporter.add(kandidat.id());
        kjoringer.findByPeriodeAndStatusOrderByVersjonDesc(sammenligning.periode(), Kjoringstatus.EKSPORTERT.name())
                .stream().map(Fakturakjoring::bruksdataImportId).filter(Objects::nonNull)
                .filter(id -> !kundedataImporter.contains(id)).forEach(kundedataImporter::add);
        var ctx = new Byggekontekst(new Periode(sammenligning.periode()), prisversjon, kjoring.id(),
                kundedataImporter, true);
        bygg(ctx, differanser);
        List<Faktura> korrigeringsfakturaer = fakturaer.findByKjoringIdOrderByOrdreNr(kjoring.id());
        if (!korrigeringsfakturaer.isEmpty() && korrigeringsfakturaer.stream()
                .flatMap(f -> linjer.findByFakturaIdOrderById(f.id()).stream())
                .allMatch(l -> l.belop().compareTo(BigDecimal.ZERO) == 0)) {
            throw new Regelbrudd("Alle valgte korreksjoner avrundes til 0 kroner. "
                    + "Kontroller differansene og prisgrunnlaget.");
        }

        sammenligninger.save(sammenligning.medStatus(Sammenligningstatus.KJORING_OPPRETTET.name(), null));
        usageImports.save(kandidat.medStatus(Importstatus.BEHANDLET.name()));
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(), Map.of(
                "periode", new Periode(sammenligning.periode()).toString(),
                "versjon", versjon, "type", Kjoringstype.KORRIGERING.name(),
                "sammenligningId", sammenligning.id(), "valgteEndringer", valgte.size(),
                "aarsak", aarsak.trim(), "antallFakturaer", ctx.antallFakturaer));
        return kjoring;
    }

    /**
     * Generate a settlement run (specs/020): per customer and product, the usage of the ticked months of {@code aar}
     * times the price adjustment entered for the product, in kr per unit. Products without an adjustment (or with 0)
     * are not settled; the application infrastructure never is (1:1 between booked cost and invoiced amount).
     */
    @Transactional
    public Fakturakjoring genererAvregning(int aar, List<Periode> maaneder, Map<Long, BigDecimal> justeringer) {
        List<Periode> valgte = maaneder == null ? List.of()
                : maaneder.stream().distinct().sorted(Comparator.comparing(Periode::førsteDag)).toList();
        if (valgte.isEmpty()) {
            throw new Regelbrudd("Velg minst én måned å avregne");
        }
        if (valgte.stream().anyMatch(m -> m.førsteDag().getYear() != aar)) {
            throw new Regelbrudd("Alle månedene må være i " + aar);
        }
        Map<Long, Produkt> produktById = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktById.put(p.id(), p));
        Map<Long, BigDecimal> aktive = new LinkedHashMap<>();
        for (var e : (justeringer == null ? Map.<Long, BigDecimal>of() : justeringer).entrySet()) {
            Produkt p = produktById.get(e.getKey());
            if (p == null) {
                throw new Regelbrudd("Fant ikke produkt " + e.getKey());
            }
            if (AvregningService.UNNTATT_PRODUKTKODE.equals(p.kode())) {
                throw new Regelbrudd(p.navn() + " avregnes ikke: bokført kostnad viderefaktureres 1:1");
            }
            if (e.getValue() != null && e.getValue().signum() != 0) {
                aktive.put(e.getKey(), e.getValue());
            }
        }
        if (aktive.isEmpty()) {
            throw new Regelbrudd("Legg inn prisjustering for minst ett produkt");
        }
        LocalDate anker = Periode.av(aar, 1).førsteDag();
        versjoner.laas(anker);
        if (kjoringer.finnesPaagaaendeAvregning(anker)) {
            throw new Regelbrudd("Det finnes allerede en avregning for " + aar
                    + " som ikke er eksportert eller forkastet. Forkast den før du lager en ny.");
        }

        // Each month's usage is what its current import holds; a month without usage cannot be settled.
        List<AvregningRepository.Maaned> grunnlag = new ArrayList<>();
        Map<List<Object>, BigDecimal> sumPerKundeOgProdukt = new LinkedHashMap<>();
        for (Periode m : valgte) {
            Long importId = usage.gjeldendeImport(m).map(BruksdataImport::id)
                    .orElseThrow(() -> new Regelbrudd("Ingen bruksdata for " + m + " — importer bruk først."));
            grunnlag.add(new AvregningRepository.Maaned(m.førsteDag(), importId));
            for (Bruksdata b : usage.bruksdataFor(m)) {
                if (aktive.containsKey(b.produktId()) && Bruksdatatype.valueOf(b.type()).erVolum()) {
                    sumPerKundeOgProdukt.merge(List.of(b.organisasjonsnummer(), b.produktId()),
                            b.antall() == null ? BigDecimal.ZERO : b.antall(), BigDecimal::add);
                }
            }
        }
        Long sisteImport = grunnlag.getLast().importId();
        List<Bruksdata> bruk = new ArrayList<>();
        sumPerKundeOgProdukt.forEach((nokkel, antall) -> {
            if (antall.signum() != 0) {
                bruk.add(new Bruksdata(null, sisteImport, anker, (String) nokkel.get(0), (Long) nokkel.get(1),
                        Bruksdatatype.BRUKSVOLUM.name(), antall, null));
            }
        });
        if (bruk.isEmpty()) {
            throw new Regelbrudd("Ingen bruk å avregne for produktene og månedene du valgte");
        }

        Fakturatekster tekster = Fakturatekster.standardAvregning(aar);
        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, anker, Kjoringstatus.GENERERT.name(), null,
                sisteImport, brukerContext.naavaerendeBruker(), OffsetDateTime.now(), null, null, null,
                Kjoringstype.AVREGNING.name(), kjoringer.nesteAvregningsversjon(anker), null, null, null,
                tekster.topptekst(), tekster.bunntekst(), tekster.varReferanse()));
        avregninger.lagre(kjoring.id(), grunnlag, aktive);

        // Customer values: the newest ticked month first, as in a correction (first match per orgnr wins).
        List<Long> kundedataImporter = grunnlag.reversed().stream().map(AvregningRepository.Maaned::importId).toList();
        var ctx = new Byggekontekst(Periode.av(aar, 1), null, kjoring.id(), kundedataImporter, false, aktive, aar);
        bygg(ctx, bruk);
        if (ctx.antallFakturaer == 0) {
            throw new Regelbrudd("Avregningen ga ingen fakturaer. Sjekk kontrollfunnene: kundedata mangler for "
                    + "kundene i bruksdataene.");
        }
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(), Map.of(
                "periode", Periode.av(aar, 1).toString(), "versjon", kjoring.versjon(),
                "type", Kjoringstype.AVREGNING.name(), "aar", aar,
                "maaneder", valgte.stream().map(Periode::toString).toList(),
                "antallFakturaer", ctx.antallFakturaer));
        return kjoring;
    }

    private void bygg(Byggekontekst ctx, List<Bruksdata> bruk) {
        Map<Long, Produkt> produktById = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktById.put(p.id(), p));
        Map<Long, Pris> prisByProdukt = new LinkedHashMap<>();
        if (ctx.versjon != null) {
            priser.findByPrisversjonId(ctx.versjon.id()).forEach(pr -> prisByProdukt.put(pr.produktId(), pr));
        }

        // Customer data comes with the usage (specs/018): the values the run's import delivered per orgnr, from
        // CRM through the DWH. Loaded once, so per-row work is in-memory (docs/04 Phase 5 load sanity).
        Map<String, BruksdataKunde> kundeByOrgnr = new LinkedHashMap<>();
        for (Long importId : ctx.kundedataImporter) {
            usage.kundedata(importId).forEach(k -> kundeByOrgnr.putIfAbsent(k.organisasjonsnummer(), k));
        }

        List<Kontrollfunn> funnListe = new ArrayList<>();
        Map<Gruppe, Gruppedata> grupper = new LinkedHashMap<>();
        // Skipped usage rows per finding key, so each can be linked to the finding that kept it off the
        // invoice (specs/002 D2): orgnr → MOTSTRIDENDE_KUNDEDATA or MANGLER_KUNDENUMMER, kode → MANGLER_PRIS.
        var motstridende = new LinkedHashMap<String, List<Long>>();
        var forLangReferanse = new LinkedHashMap<String, List<Long>>();
        var manglerKundenummer = new LinkedHashMap<String, List<Long>>();
        var manglerPris = new LinkedHashMap<String, List<Long>>();
        var manglerKontering = new java.util.LinkedHashSet<String>();

        // Deterministic processing order → deterministic "first" choices and grouping.
        List<Bruksdata> sortert = new ArrayList<>(bruk);
        sortert.sort(Comparator
                .comparing(Bruksdata::organisasjonsnummer)
                .thenComparing(b -> kode(produktById, b.produktId()))
                .thenComparing(Bruksdata::type)
                .thenComparing(b -> b.id() == null ? 0L : b.id()));

        for (Bruksdata b : sortert) {
            Produkt p = produktById.get(b.produktId());
            BruksdataKunde k = kundeByOrgnr.get(b.organisasjonsnummer());
            // Rows that disagree on the customer values: forsystem does not choose one (specs/018 FR-008).
            if (k != null && k.harMotstridende()) {
                motstridende.computeIfAbsent(b.organisasjonsnummer(), x -> new ArrayList<>()).add(b.id());
                continue;
            }
            // A reference that does not fit its LG04 field would be shortened in the file: block, never cut.
            if (k != null && (Kundedata.forLang(k.fakturareferanse()) || Kundedata.forLang(k.bestillingsnummer()))) {
                forLangReferanse.computeIfAbsent(b.organisasjonsnummer(), x -> new ArrayList<>()).add(b.id());
                continue;
            }
            if (p.konto() == null) {
                manglerKontering.add(p.kode());
            }
            if (k == null || k.kundenummer() == null || k.kundenummer().isBlank()) {
                manglerKundenummer.computeIfAbsent(b.organisasjonsnummer(), x -> new ArrayList<>()).add(b.id());
                continue;
            }

            BigDecimal antall = null;
            BigDecimal enhetspris = null;
            Long prisId = null;
            BigDecimal belop;
            String beskrivelse;
            if (ctx.avregning) {
                // A settlement bills the entered adjustment per unit, not a price from a price version.
                antall = b.antall();
                enhetspris = ctx.justeringer.get(p.id());
                belop = antall.multiply(enhetspris).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = "Avregning " + p.navn() + " " + ctx.aar;
            } else if (Bruksdatatype.valueOf(b.type()).erVolum()) {
                Pris pris = prisByProdukt.get(p.id());
                if (pris == null) {
                    manglerPris.computeIfAbsent(p.kode(), x -> new ArrayList<>()).add(b.id());
                    continue;
                }
                antall = b.antall() == null ? BigDecimal.ZERO : b.antall();
                enhetspris = pris.enhetspris();
                prisId = pris.id();
                belop = antall.multiply(enhetspris).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = (ctx.korrigering ? "Korrigering av " : "Bruk av ")
                        + p.navn() + " " + ctx.periode.norskLabel();
            } else {
                belop = (b.belop() == null ? BigDecimal.ZERO : b.belop()).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = beskrivKostnad(b.type(), p, ctx.periode);
            }

            Referansepar ref = Referansepar.av(k.fakturareferanse(), k.bestillingsnummer());
            var linje = new LinjeUtkast(p.id(), p.kode(), b.type(), beskrivelse, antall, enhetspris, belop,
                    prisId, ctx.korrigering ? null : b.id(), ctx.korrigering ? b.id() : null, null,
                    ref.fakturareferanse(), ref.bestillingsnummer());

            Gruppe g = new Gruppe(k.organisasjonsnummer(), k.kundenummer());
            grupper.computeIfAbsent(g, x -> new Gruppedata(k)).linjer.add(linje);
        }

        // BLOKKERENDE findings.
        // Each finding that skipped rows remembers them by its index in funnListe until it is saved.
        Map<Integer, List<Long>> utelattePerFunn = new LinkedHashMap<>();
        motstridende.forEach((orgnr, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            BruksdataKunde k = kundeByOrgnr.get(orgnr);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MOTSTRIDENDE_KUNDEDATA,
                    "Importen har motstridende kundedata for " + kundetekst(orgnr, k) + ": "
                            + String.join("; ", k.motstridende())));
        });
        forLangReferanse.forEach((orgnr, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            BruksdataKunde k = kundeByOrgnr.get(orgnr);
            List<String> deler = new ArrayList<>();
            if (Kundedata.forLang(k.fakturareferanse())) {
                deler.add("fakturareferanse «" + k.fakturareferanse().strip() + "» (" + k.fakturareferanse().strip().length()
                        + " tegn)");
            }
            if (Kundedata.forLang(k.bestillingsnummer())) {
                deler.add("bestillingsnummer «" + k.bestillingsnummer().strip() + "» ("
                        + k.bestillingsnummer().strip().length() + " tegn)");
            }
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.FOR_LANG_REFERANSE,
                    "Importen har for lang " + String.join(" og ", deler) + " for " + kundetekst(orgnr, k)
                            + ". LG04-filen har plass til " + Kundedata.MAKS_REFERANSELENGDE + " tegn."));
        });
        manglerKundenummer.forEach((orgnr, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KUNDENUMMER,
                    "Importen har ikke kundenummer for " + kundetekst(orgnr, kundeByOrgnr.get(orgnr))));
        });
        manglerPris.forEach((kode, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_PRIS,
                    "Produkt " + kode + " mangler pris i aktiv prisversjon"));
        });
        manglerKontering.forEach(kode -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KONTERING,
                "Produkt " + kode + " mangler kontering (OQ-2)")));
        // Threshold (specs/011, OQ-22): a customer whose total over all its would-be invoices is not over the
        // price version's threshold gets no invoice. Its groups leave `grupper` before any invoice is made, so
        // nothing downstream (LG04, PDF, sums) sees them; they are kept as faktura_under_terskel rows and
        // explained by one UNDER_TERSKEL finding linked to their usage. Correction runs carry signed
        // differences against an exported run and are exempt.
        BigDecimal terskel = ctx.versjon == null ? null : ctx.versjon.terskelverdi();
        Map<String, BigDecimal> kundesum = new LinkedHashMap<>();
        Map<Gruppe, Gruppedata> underTerskelGrupper = new LinkedHashMap<>();
        Integer underTerskelFunn = null;
        if (!ctx.korrigering && !ctx.avregning && terskel != null) {
            grupper.forEach((g, data) -> kundesum.merge(g.organisasjonsnummer(), sum(data.linjer), BigDecimal::add));
            grupper.keySet().stream()
                    .filter(g -> kundesum.get(g.organisasjonsnummer()).compareTo(terskel) <= 0)
                    .sorted(Comparator.comparing(Gruppe::organisasjonsnummer)
                            .thenComparing(Gruppe::kundenummer))
                    .toList()
                    .forEach(g -> underTerskelGrupper.put(g, grupper.remove(g)));
            if (!underTerskelGrupper.isEmpty()) {
                List<String> kunder = underTerskelGrupper.keySet().stream().map(Gruppe::organisasjonsnummer).distinct()
                        .toList();
                BigDecimal total = kunder.stream().map(kundesum::get).reduce(BigDecimal.ZERO, BigDecimal::add);
                underTerskelFunn = funnListe.size();
                utelattePerFunn.put(underTerskelFunn, underTerskelGrupper.values().stream()
                        .flatMap(d -> d.linjer.stream()).map(LinjeUtkast::bruksdataId).toList());
                funnListe.add(advarsel(ctx.kjoringId, null, Kontrollkode.UNDER_TERSKEL,
                        (kunder.size() == 1 ? "1 kunde" : kunder.size() + " kunder")
                                + " har samlet " + kroner(terskel) + " kr eksl. mva eller mindre i perioden og "
                                + "faktureres ikke (til sammen " + kroner(total) + " kr eksl. mva). Terskelverdien er "
                                + kroner(terskel) + " kr i prisversjonen «" + ctx.versjon.navn() + "»."));
            }
        }

        // Create invoices in deterministic order and assign ordre_nr.
        List<Gruppe> sorterteGrupper = new ArrayList<>(grupper.keySet());
        sorterteGrupper.sort(Comparator
                .comparing(Gruppe::organisasjonsnummer)
                .thenComparing(g -> g.kundenummer));

        Map<String, BigDecimal> sumPerKunde = new LinkedHashMap<>();
        Map<String, Long> forsteFakturaPerKunde = new LinkedHashMap<>();
        int ordre = 1;
        for (Gruppe g : sorterteGrupper) {
            Gruppedata data = grupper.get(g);
            data.linjer.sort(Comparator.comparing((LinjeUtkast l) -> l.produktKode).thenComparing(l -> l.type));
            BigDecimal sum = sum(data.linjer);
            if (sum.abs().compareTo(MAKS_FAKTURABELOP) > 0) {
                // faktura.sum_belop is numeric(12,2); fail with a reason instead of a DB overflow (500).
                throw new Regelbrudd("Fakturaen til " + data.navn() + " ("
                        + g.organisasjonsnummer() + ", kundenummer " + g.kundenummer + ") blir "
                        + sum.toPlainString() + " kr, over maksgrensen på " + MAKS_FAKTURABELOP.toPlainString()
                        + " kr. Sjekk enhetsprisene i prisversjonen og enheten på bruksdata (OQ-21).");
            }
            // The organisation itself receives the invoice: the DWH carries no deviating recipient (specs/018, OQ-28).
            Faktura faktura = fakturaer.save(new Faktura(null, ctx.kjoringId, g.organisasjonsnummer(), UuidV7.naa(),
                    ordre++, g.kundenummer, data.navn(), null, g.organisasjonsnummer(), sum));
            ctx.antallFakturaer++;
            for (LinjeUtkast l : data.linjer) {
                linjer.save(new Fakturalinje(null, faktura.id(), l.produktId, l.beskrivelse, l.antall,
                        l.enhetspris, l.belop, l.prisId, l.bruksdataId, l.servicekode,
                        l.fakturareferanse, l.bestillingsnummer, l.bruksdataEndringId));
            }
            if (data.linjer.stream().anyMatch(l -> l.belop.compareTo(BigDecimal.ZERO) == 0)) {
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.NULLBELOP,
                        "Fakturaen har minst én linje med beløp 0"));
            }
            // LG04 carries one reference pair per order, so the export splits this invoice (specs/008 FR-009).
            // Same key as the export's grouping (EksportService.byggOrdrer), so warning and file agree.
            Set<List<String>> referanser = new LinkedHashSet<>();
            data.linjer.forEach(l -> referanser.add(Arrays.asList(l.fakturareferanse, l.bestillingsnummer)));
            if (referanser.size() > 1) {
                String liste = referanser.stream().map(r -> referansetekst(r.get(0), r.get(1)))
                        .collect(Collectors.joining("; "));
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.DELT_REFERANSE,
                        "Fakturaen har " + referanser.size() + " ulike referanser (" + liste + ") og blir "
                                + referanser.size() + " fakturaer i Unit4"));
            }
            // Missing «Deres referanse» (specs/012): one warning per invoice, naming the customer and products,
            // since the Kontroller tab shows findings as text. The list below the findings has the same rows.
            List<String> utenReferanse = data.linjer.stream().filter(l -> l.fakturareferanse == null)
                    .map(l -> produktById.get(l.produktId).navn()).distinct().toList();
            if (!utenReferanse.isEmpty()) {
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.MANGLER_FAKTURAREFERANSE,
                        data.navn() + " (" + g.organisasjonsnummer() + "), kundenummer "
                                + g.kundenummer + ": ingen fakturareferanse for " + String.join(", ", utenReferanse)));
            }
            sumPerKunde.merge(g.organisasjonsnummer(), sum, BigDecimal::add);
            forsteFakturaPerKunde.putIfAbsent(g.organisasjonsnummer(), faktura.id());
        }

        if (!ctx.korrigering && !ctx.avregning) {
            leggTilForrigePeriodeAdvarsler(ctx, sumPerKunde, forsteFakturaPerKunde, funnListe);
        }
        List<Kontrollfunn> lagret = funn.saveAll(funnListe);
        List<KontrollfunnBruksdata> lenker = new ArrayList<>();
        if (!ctx.korrigering && !ctx.avregning) {
            utelattePerFunn.forEach((indeks, ider) ->
                    ider.forEach(id -> lenker.add(new KontrollfunnBruksdata(lagret.get(indeks).id(), id))));
        }
        funnBruksdata.lagre(lenker);
        if (underTerskelFunn != null) {
            Long funnId = lagret.get(underTerskelFunn).id();
            List<FakturaUnderTerskel> rader = new ArrayList<>();
            underTerskelGrupper.forEach((g, d) -> rader.add(new FakturaUnderTerskel(null, ctx.kjoringId,
                    g.organisasjonsnummer(), d.navn(), g.kundenummer(), null, sum(d.linjer), d.linjer.size(),
                    kundesum.get(g.organisasjonsnummer()), terskel, funnId)));
            underTerskel.saveAll(rader);
        }
    }

    private static BigDecimal sum(List<LinjeUtkast> linjer) {
        return linjer.stream().map(LinjeUtkast::belop).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** An amount in a finding's text, Norwegian style: 40,00. */
    private static String kroner(BigDecimal belop) {
        return belop.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private void leggTilForrigePeriodeAdvarsler(Byggekontekst ctx, Map<String, BigDecimal> sumPerKunde,
                                                Map<String, Long> forsteFakturaPerKunde, List<Kontrollfunn> ut) {
        Optional<Fakturakjoring> forrige = kjoringer.findFirstByPeriodeAndStatusNot(
                ctx.periode.forrige().førsteDag(), Kjoringstatus.FORKASTET.name());
        if (forrige.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> forrigeSum = new LinkedHashMap<>();
        for (Faktura f : fakturaer.findByKjoringIdOrderByOrdreNr(forrige.get().id())) {
            forrigeSum.merge(f.organisasjonsnummer(), f.sumBelop(), BigDecimal::add);
        }
        sumPerKunde.forEach((orgnr, sum) -> {
            Long fakturaId = forsteFakturaPerKunde.get(orgnr);
            BigDecimal forr = forrigeSum.get(orgnr);
            if (forr == null) {
                ut.add(advarsel(ctx.kjoringId, fakturaId, Kontrollkode.NY_KUNDE,
                        "Ny kunde denne perioden (ikke fakturert forrige periode)"));
            } else if (forr.signum() != 0) {
                BigDecimal avvik = sum.subtract(forr).abs()
                        .divide(forr.abs(), 4, RoundingMode.HALF_UP);
                if (avvik.compareTo(AVVIK_GRENSE) > 0) {
                    ut.add(advarsel(ctx.kjoringId, fakturaId, Kontrollkode.STORT_AVVIK,
                            "Sum avviker " + avvik.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
                                    + "% fra forrige periode"));
                }
            }
        });
    }

    // ---- State machine ----------------------------------------------------------------------

    @Transactional
    public void godkjenn(Long kjoringId) {
        Fakturakjoring kj = hentForOppdatering(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en generert kjøring kan godkjennes (status er " + kj.status() + ")");
        }
        if (funn.existsByKjoringIdAndAlvorlighet(kjoringId, Alvorlighet.BLOKKERENDE.name())) {
            throw new Regelbrudd("Kjøringen har blokkerende kontrollfunn og kan ikke godkjennes");
        }
        long aapne = antallAapneAdvarsler(kjoringId);
        if (aapne > 0) {
            throw new Regelbrudd(aapne + (aapne == 1 ? " advarsel er" : " advarsler er")
                    + " ikke sjekket ut. Kryss av «Funn akseptert» på fanen Kontroller før kjøringen godkjennes.");
        }
        String bruker = brukerContext.naavaerendeBruker();
        kjoringer.save(kj.medStatus(Kjoringstatus.GODKJENT.name(), bruker, OffsetDateTime.now()));
        audit.logg(Handling.GODKJENTE, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "type", kj.type()));
    }

    // ---- Accepting warnings (specs/013) ------------------------------------------------------

    /**
     * Mark one warning of a GENERERT run as accepted («Funn akseptert»), or remove the mark. Blocking findings
     * cannot be accepted — they are fixed by correcting the data and generating again. Logged on the run.
     */
    @Transactional
    public void settAksept(Long kjoringId, Long funnId, boolean akseptert) {
        Fakturakjoring kj = hentForAksept(kjoringId);
        Kontrollfunn f = funn.findById(funnId)
                .filter(x -> kjoringId.equals(x.kjoringId()))
                .orElseThrow(() -> new Regelbrudd("Fant ikke kontrollfunn " + funnId + " i kjøringen"));
        if (f.blokkerende()) {
            throw new Regelbrudd("Blokkerende funn kan ikke aksepteres. Rett dataene og generer kjøringen på nytt.");
        }
        endreAksept(kj, f.kode(), List.of(f), akseptert);
    }

    /**
     * Accept (or un-accept) every warning of one code in a GENERERT run in one audited action — the group row's
     * checkbox. Refused for a blocking code.
     */
    @Transactional
    public void settAkseptForKode(Long kjoringId, String kode, boolean akseptert) {
        Fakturakjoring kj = hentForAksept(kjoringId);
        List<Kontrollfunn> gruppe = funn.findByKjoringIdAndKodeOrderById(kjoringId, kode);
        if (gruppe.isEmpty()) {
            throw new Regelbrudd("Kjøringen har ingen kontrollfunn med kode " + kode);
        }
        if (gruppe.stream().anyMatch(Kontrollfunn::blokkerende)) {
            throw new Regelbrudd("Blokkerende funn kan ikke aksepteres. Rett dataene og generer kjøringen på nytt.");
        }
        endreAksept(kj, kode, gruppe, akseptert);
    }

    /** The run, row-locked like approval so an acceptance change and an approval never interleave. */
    private Fakturakjoring hentForAksept(Long kjoringId) {
        Fakturakjoring kj = hentForOppdatering(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Kontrollfunn kan bare sjekkes ut mens kjøringen venter på godkjenning (status er "
                    + kj.status() + ")");
        }
        return kj;
    }

    private void endreAksept(Fakturakjoring kj, String kode, List<Kontrollfunn> kandidater, boolean akseptert) {
        List<Kontrollfunn> endres = kandidater.stream().filter(f -> f.erAkseptert() != akseptert).toList();
        if (endres.isEmpty()) {
            return;
        }
        String bruker = brukerContext.naavaerendeBruker();
        OffsetDateTime naa = OffsetDateTime.now();
        funn.saveAll(endres.stream().map(f -> akseptert ? f.medAksept(bruker, naa) : f.utenAksept()).toList());
        Map<String, Object> detaljer = new LinkedHashMap<>();
        detaljer.put("periode", kj.periode().toString());
        detaljer.put("versjon", kj.versjon());
        detaljer.put("kode", kode);
        detaljer.put("antall", endres.size());
        detaljer.put("funnIder", endres.stream().map(Kontrollfunn::id).toList());
        audit.logg(akseptert ? Handling.AKSEPTERTE_FUNN : Handling.ANGRET_AKSEPT, "FAKTURAKJORING", kj.id(), detaljer);
    }

    /** Warnings not yet accepted: they hold back approval (specs/013). */
    @Transactional(readOnly = true)
    public long antallAapneAdvarsler(Long kjoringId) {
        return funn.countByKjoringIdAndAlvorlighetAndAkseptertAtIsNull(kjoringId, Alvorlighet.ADVARSEL.name());
    }

    /**
     * Change the run's invoice texts (specs/009). Only while GENERERT: from approval on they are part
     * of what was approved. Texts are trimmed and validated against the LG04 fields; each changed
     * field is logged with old and new value, and a save without changes logs nothing.
     */
    @Transactional
    public Fakturakjoring endreTekster(Long kjoringId, Fakturatekster nye) {
        Fakturakjoring kj = hentForOppdatering(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Tekstene på fakturaen er låst etter godkjenning (status er "
                    + kj.status() + ")");
        }
        Fakturatekster tekster = nye.normalisert();
        tekster.valider();
        Map<String, Map<String, String>> endringer = tekster.endringerFra(kj.tekster());
        if (endringer.isEmpty()) {
            return kj;
        }
        Fakturakjoring lagret = kjoringer.save(kj.medTekster(tekster));
        audit.logg(Handling.ENDRET, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "endringer", endringer));
        return lagret;
    }

    private Fakturakjoring hentForOppdatering(Long id) {
        return kjoringer.finnForOppdatering(id).orElseThrow(() -> new Regelbrudd("Fant ikke kjøring " + id));
    }

    /** Move an approved run to EKSPORTERT. Called by the export service once artifacts are archived. */
    @Transactional
    public void settEksportert(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        if (!Kjoringstatus.GODKJENT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en godkjent kjøring kan eksporteres (status er " + kj.status() + ")");
        }
        kjoringer.save(kj.medStatus(Kjoringstatus.EKSPORTERT.name(), kj.godkjentAv(), kj.godkjentAt()));
    }

    @Transactional
    public void forkast(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        if (Kjoringstatus.EKSPORTERT.name().equals(kj.status())) {
            throw new Regelbrudd("En eksportert kjøring kan ikke forkastes");
        }
        if (Kjoringstatus.FORKASTET.name().equals(kj.status())) {
            return;
        }
        kjoringer.save(kj.medStatus(Kjoringstatus.FORKASTET.name(), kj.godkjentAv(), kj.godkjentAt()));
        if (kj.erKorrigering() && kj.sammenligningId() != null) {
            BruksdataSammenligning sammenligning = sammenligninger.findById(kj.sammenligningId())
                    .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + kj.sammenligningId()));
            if (Sammenligningstatus.KJORING_OPPRETTET.name().equals(sammenligning.status())) {
                List<BruksdataSammenligning> nyereAapne = sammenligninger.findByPeriodeAndStatus(
                        kj.periode(), Sammenligningstatus.AAPEN.name());
                if (nyereAapne.isEmpty()) {
                    sammenligninger.save(sammenligning.medStatus(Sammenligningstatus.AAPEN.name(), null));
                } else {
                    sammenligninger.save(sammenligning.medStatus(
                            Sammenligningstatus.ERSTATTET.name(), nyereAapne.getLast().id()));
                }
            }
        }
        audit.logg(Handling.FORKASTET, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "type", kj.type()));
    }

    // ---- Queries ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Fakturakjoring> alle() {
        return kjoringer.findAllByOrderByGenerertAtDesc();
    }

    @Transactional(readOnly = true)
    public Fakturakjoring hent(Long id) {
        return kjoringer.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke kjøring " + id));
    }

    /** The would-be invoices the run left out under the threshold (specs/011), by orgnr and kundenummer. */
    @Transactional(readOnly = true)
    public List<FakturaUnderTerskel> underTerskel(Long kjoringId) {
        return underTerskel.findByKjoringIdOrderByOrganisasjonsnummerAscKundenummerAsc(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Faktura> fakturaer(Long kjoringId) {
        return fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
    }

    /**
     * The run's invoices with lines that have no fakturareferanse (specs/012), in invoice order — the
     * customers Økonomi has to get a «Deres referanse» for. Only invoiced customers: those left out under the
     * threshold or without usage are not on an invoice this month. TODO(OQ-24): Økonomi to say whether a
     * registry-wide list of all active customers is wanted as well.
     */
    @Transactional(readOnly = true)
    public List<FakturaUtenReferanse> utenFakturareferanse(Long kjoringId) {
        Map<Long, String> produktnavn = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktnavn.put(p.id(), p.navn()));
        List<Faktura> fs = fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
        List<FakturaUtenReferanse> ut = new ArrayList<>();
        for (Faktura f : fs) {
            List<Fakturalinje> alle = linjer.findByFakturaIdOrderById(f.id());
            List<Fakturalinje> uten = alle.stream().filter(l -> l.fakturareferanse() == null).toList();
            if (uten.isEmpty()) {
                continue;
            }
            List<String> produktliste = uten.stream()
                    .map(l -> produktnavn.getOrDefault(l.produktId(), String.valueOf(l.produktId())))
                    .distinct().toList();
            ut.add(new FakturaUtenReferanse(f.id(), f.ordreNr(), f.kundenavn(), f.organisasjonsnummer(), f.kundenummer(),
                    produktliste, uten.size(), uten.size() == alle.size(), f.sumBelop()));
        }
        return ut;
    }

    /** Headline figures for the run page and the covering email (specs/002 FR-001..003). */
    @Transactional(readOnly = true)
    public Kjoringssammendrag sammendrag(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        List<Faktura> fs = fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
        int antallLinjer = 0;
        BigDecimal sumLinjer = BigDecimal.ZERO;
        BigDecimal sumFakturaer = BigDecimal.ZERO;
        BigDecimal sumInklMva = BigDecimal.ZERO;
        for (Faktura f : fs) {
            sumFakturaer = sumFakturaer.add(f.sumBelop());
            sumInklMva = sumInklMva.add(
                    f.sumBelop().multiply(Kjoringssammendrag.MVA_FAKTOR).setScale(2, RoundingMode.HALF_UP));
            for (Fakturalinje l : linjer.findByFakturaIdOrderById(f.id())) {
                antallLinjer++;
                sumLinjer = sumLinjer.add(l.belop());
            }
        }
        return new Kjoringssammendrag(new Periode(kj.periode()), fs.size(), antallLinjer, sumLinjer, sumFakturaer,
                sumInklMva);
    }

    /**
     * The customer name to show per invoice (D9): the name frozen at generation. Invoices made before the name
     * was frozen got it from the registry when it was removed (V17, specs/018).
     */
    public Map<Long, String> kundenavn(List<Faktura> fs) {
        Map<Long, String> navn = new LinkedHashMap<>();
        fs.forEach(f -> navn.put(f.id(), f.kundenavn()));
        return navn;
    }

    /**
     * Every invoice line of a run with what a spreadsheet of the fakturagrunnlag needs beside the line:
     * its invoice, customer name and orgnr, and the usage type of the row it came from. Ordered like the
     * LG04 (invoice order, then product and type).
     */
    @Transactional(readOnly = true)
    public List<Grunnlagslinje> grunnlagslinjer(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        Map<Long, String> typeByBruksdata = new LinkedHashMap<>();
        Map<Long, String> typeByEndring = new LinkedHashMap<>();
        if (kj.bruksdataImportId() != null) {
            usage.rader(kj.bruksdataImportId()).forEach(b -> typeByBruksdata.put(b.id(), b.type()));
        }
        if (kj.erKorrigering()) {
            endringer.findAllById(valgteEndringer.endringIder(kjoringId))
                    .forEach(e -> typeByEndring.put(e.id(), e.type()));
        }
        List<Faktura> fs = fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
        List<Grunnlagslinje> ut = new ArrayList<>();
        for (Faktura f : fs) {
            for (Fakturalinje l : linjer.findByFakturaIdOrderById(f.id())) {
                String type = l.bruksdataEndringId() == null
                        ? typeByBruksdata.get(l.bruksdataId()) : typeByEndring.get(l.bruksdataEndringId());
                ut.add(new Grunnlagslinje(f, l, f.kundenavn(), f.organisasjonsnummer(), type));
            }
        }
        return ut;
    }

    /** One line of the fakturagrunnlag spreadsheet (see {@link #grunnlagslinjer}). */
    public record Grunnlagslinje(Faktura faktura, Fakturalinje linje, String kundenavn, String organisasjonsnummer,
                                 String type) {
    }

    @Transactional(readOnly = true)
    public Faktura hentFaktura(Long fakturaId) {
        return fakturaer.findById(fakturaId).orElseThrow(() -> new Regelbrudd("Fant ikke faktura " + fakturaId));
    }

    @Transactional(readOnly = true)
    public List<Fakturalinje> linjer(Long fakturaId) {
        return linjer.findByFakturaIdOrderById(fakturaId);
    }

    @Transactional(readOnly = true)
    public List<Kontrollfunn> funn(Long kjoringId) {
        return funn.findByKjoringId(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Kontrollfunn> funnForFaktura(Long fakturaId) {
        return funn.findByFakturaId(fakturaId);
    }

    @Transactional(readOnly = true)
    public boolean harBlokkerende(Long kjoringId) {
        return funn.existsByKjoringIdAndAlvorlighet(kjoringId, Alvorlighet.BLOKKERENDE.name());
    }

    @Transactional(readOnly = true)
    public List<Long> valgteEndringIder(Long kjoringId) {
        return valgteEndringer.endringIder(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Eksportfil> eksportfiler(Long kjoringId) {
        return eksportfiler.findByKjoringIdOrderByType(kjoringId);
    }

    @Transactional(readOnly = true)
    public Eksportfil hentEksportfil(Long id) {
        return eksportfiler.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke eksportfil " + id));
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private Prisversjon aktivVersjonFor(Periode periode) {
        LocalDate d = periode.førsteDag();
        List<Prisversjon> dekkende = prisversjoner.findByStatus(PrisversjonStatus.AKTIV.name()).stream()
                .filter(v -> !v.gyldigFra().isAfter(d) && (v.gyldigTil() == null || !v.gyldigTil().isBefore(d)))
                .toList();
        if (dekkende.isEmpty()) {
            throw new Regelbrudd("Ingen aktiv prisversjon dekker " + periode);
        }
        if (dekkende.size() > 1) {
            throw new Regelbrudd("Flere aktive prisversjoner dekker " + periode);
        }
        return dekkende.get(0);
    }

    /** «Navn (orgnr)», or just the orgnr when the DWH delivered no name. */
    private static String kundetekst(String orgnr, BruksdataKunde k) {
        return k == null || k.virksomhetsnavn() == null ? orgnr : k.virksomhetsnavn() + " (" + orgnr + ")";
    }

    private static String referansetekst(String fakturareferanse, String bestillingsnummer) {
        if (fakturareferanse == null && bestillingsnummer == null) {
            return "ingen referanse";
        }
        return (fakturareferanse == null ? "–" : fakturareferanse)
                + (bestillingsnummer == null ? "" : " / " + bestillingsnummer);
    }

    private static String beskrivKostnad(String type, Produkt p, Periode periode) {
        // Økonomi: "Azure-kostnad" does not belong in the description (kickoff row 47).
        String prefiks = "SMS_KOSTNAD".equals(type) ? "SMS-kostnad " : "Bruk av ";
        return prefiks + p.navn() + " " + periode.norskLabel();
    }

    private static String kode(Map<Long, Produkt> produktById, Long id) {
        Produkt p = produktById.get(id);
        return p == null ? "" : p.kode();
    }

    private static Kontrollfunn blokk(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return funn(kjoringId, fakturaId, Alvorlighet.BLOKKERENDE, kode, melding);
    }

    private static Kontrollfunn advarsel(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return funn(kjoringId, fakturaId, Alvorlighet.ADVARSEL, kode, melding);
    }

    // The Kontroller tab explains each code with its declared severity; raising it with another would mislead.
    private static Kontrollfunn funn(Long kjoringId, Long fakturaId, Alvorlighet alvorlighet, Kontrollkode kode,
                                     String melding) {
        if (kode.alvorlighet() != alvorlighet) {
            throw new IllegalStateException(kode + " er " + kode.alvorlighet() + ", ikke " + alvorlighet);
        }
        return new Kontrollfunn(null, kjoringId, fakturaId, alvorlighet.name(), kode.name(), melding);
    }

    private static final class Byggekontekst {
        final Periode periode;
        final Prisversjon versjon;
        final Long kjoringId;
        /**
         * The usage imports whose customer values the run uses (specs/018), first match per orgnr wins. An
         * ordinary run has one; a correction has its candidate followed by the period's exported imports.
         */
        final List<Long> kundedataImporter;
        final boolean korrigering;
        /** Product id to price adjustment (kr per unit) for a settlement run (specs/020); null otherwise. */
        final Map<Long, BigDecimal> justeringer;
        final int aar;
        final boolean avregning;
        int antallFakturaer;

        Byggekontekst(Periode periode, Prisversjon versjon, Long kjoringId, List<Long> kundedataImporter,
                      boolean korrigering) {
            this(periode, versjon, kjoringId, kundedataImporter, korrigering, null, 0);
        }

        Byggekontekst(Periode periode, Prisversjon versjon, Long kjoringId, List<Long> kundedataImporter,
                      boolean korrigering, Map<Long, BigDecimal> justeringer, int aar) {
            this.periode = periode;
            this.versjon = versjon;
            this.kjoringId = kjoringId;
            this.kundedataImporter = List.copyOf(kundedataImporter);
            this.korrigering = korrigering;
            this.justeringer = justeringer;
            this.aar = aar;
            this.avregning = justeringer != null;
        }
    }

    /** One invoice: an organisation and the one kundenummer the DWH delivered for it. */
    private record Gruppe(String organisasjonsnummer, String kundenummer) {
    }

    private static final class Gruppedata {
        final BruksdataKunde kunde;
        final List<LinjeUtkast> linjer = new ArrayList<>();

        Gruppedata(BruksdataKunde kunde) {
            this.kunde = kunde;
        }

        /** The name as the DWH delivered it; the orgnr when it delivered none (never an invented name). */
        String navn() {
            return kunde.virksomhetsnavn() != null ? kunde.virksomhetsnavn() : kunde.organisasjonsnummer();
        }
    }

    /**
     * The reference pair a line is snapshotted with. When bestillingsnummer is empty it takes the fakturareferanse
     * (Økonomi, kickoff; specs/012 FR-005): done here, at generation, so the import keeps what CRM/the DWH
     * delivered and a later fix to the fakturareferanse is picked up by regenerating. The rule maps the pair
     * (r, ∅) to (r, r), so two lines whose pairs differed before can only become equal — it never adds an
     * LG04 split (DELT_REFERANSE), and removes the split between (r, ∅) and (r, r).
     */
    record Referansepar(String fakturareferanse, String bestillingsnummer) {

        static Referansepar av(String fakturareferanseInn, String bestillingsnummerInn) {
            String fakturareferanse = tomTilNull(fakturareferanseInn);
            String bestillingsnummer = tomTilNull(bestillingsnummerInn);
            return new Referansepar(fakturareferanse, bestillingsnummer != null ? bestillingsnummer : fakturareferanse);
        }

        private static String tomTilNull(String s) {
            return s == null || s.isBlank() ? null : s;
        }
    }

    private record LinjeUtkast(Long produktId, String produktKode, String type, String beskrivelse,
                               BigDecimal antall, BigDecimal enhetspris, BigDecimal belop, Long prisId,
                               Long bruksdataId, Long bruksdataEndringId, String servicekode, String fakturareferanse,
                               String bestillingsnummer) {
    }
}
