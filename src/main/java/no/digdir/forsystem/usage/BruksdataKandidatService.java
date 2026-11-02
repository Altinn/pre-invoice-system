package no.digdir.forsystem.usage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final UsageImportService usage;
    private final KjoringStatusPort kjoringer;
    private final BrukerContext brukerContext;
    private final AuditService audit;

    BruksdataKandidatService(BruksdataImportRepository importer, BruksdataRepository bruksdata,
                             BruksdataSammenligningRepository sammenligninger,
                             BruksdataEndringRepository endringer, UsageImportService usage,
                             KjoringStatusPort kjoringer, BrukerContext brukerContext, AuditService audit) {
        this.importer = importer;
        this.bruksdata = bruksdata;
        this.sammenligninger = sammenligninger;
        this.endringer = endringer;
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

        Optional<BruksdataSammenligning> eksisterende = sammenligninger
                .findByKandidatImportIdAndGrunnlagKjoringId(
                        kandidat.id(), grunnlag.sisteEksporterteKjoringId());
        if (eksisterende.isPresent()) {
            return new Resultat(kandidat, eksisterende.get(),
                    endringer.findBySammenligningIdOrderById(eksisterende.get().id()));
        }

        List<Bruksdata> gamle = effektivtGrunnlag(grunnlag);
        if (gamle.isEmpty()) {
            throw new Regelbrudd("Den eksporterte kjøringen har ikke et komplett bruksdatagrunnlag");
        }
        List<Bruksdata> nye = bruksdata.findByImportId(kandidat.id());
        BruksdataDifferanse.Resultat diff = BruksdataDifferanse.sammenlign(gamle, nye);

        List<BruksdataSammenligning> aapne = sammenligninger.findByPeriodeAndStatus(
                fv.periode().førsteDag(), Sammenligningstatus.AAPEN.name());
        aapne.forEach(s -> sammenligninger.save(s.medStatus(Sammenligningstatus.ERSTATTET.name(), null)));

        BruksdataSammenligning sammenligning = sammenligninger.save(new BruksdataSammenligning(
                null, fv.periode().førsteDag(), kandidat.id(), grunnlag.sisteEksporterteKjoringId(),
                Sammenligningstatus.AAPEN.name(), diff.uendredeRader(), brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), null));
        List<BruksdataEndring> lagredeEndringer = endringer.saveAll(diff.endringer().stream()
                .map(e -> BruksdataEndring.fra(sammenligning.id(), e)).toList());
        aapne.forEach(s -> sammenligninger.save(s.medStatus(
                Sammenligningstatus.ERSTATTET.name(), sammenligning.id())));

        audit.logg(Handling.SAMMENLIGNET, "BRUKSDATA_SAMMENLIGNING", sammenligning.id(), Map.of(
                "periode", fv.periode().toString(),
                "kandidatImportId", kandidat.id(),
                "grunnlagKjoringId", grunnlag.sisteEksporterteKjoringId(),
                "grunnlagVersjon", grunnlag.sisteVersjon(),
                "endringer", lagredeEndringer.size(),
                "uendrede", diff.uendredeRader(),
                "raadataSha256", sha256));
        return new Resultat(kandidat, sammenligning, lagredeEndringer);
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
        return new Resultat(kandidat, sammenligning, endringer.findBySammenligningIdOrderById(id));
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
            List<BruksdataEndring> endringer) {
    }
}
