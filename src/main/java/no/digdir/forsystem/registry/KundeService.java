package no.digdir.forsystem.registry;

import java.time.OffsetDateTime;
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
 * Maintains customers and their references and kundenummer rules (docs/03 V2). Supports every
 * FinMod special case from docs/01: deviating fakturamottaker, product-specific fakturareferanse,
 * and multiple kundenummer per customer. Every write is audited and stamps {@code oppdatert_av}.
 */
@Service
@Transactional
public class KundeService {

    static final String ENTITET_KUNDE = "KUNDE";
    static final String ENTITET_REFERANSE = "KUNDE_REFERANSE";
    static final String ENTITET_REGEL = "KUNDENUMMER_REGEL";

    private final KundeRepository kunder;
    private final KundeReferanseRepository referanser;
    private final KundenummerRegelRepository regler;
    private final AuditService audit;
    private final BrukerContext brukerContext;
    private final BrregOppslag brreg;

    KundeService(KundeRepository kunder, KundeReferanseRepository referanser,
                 KundenummerRegelRepository regler, AuditService audit, BrukerContext brukerContext,
                 BrregOppslag brreg) {
        this.kunder = kunder;
        this.referanser = referanser;
        this.regler = regler;
        this.audit = audit;
        this.brukerContext = brukerContext;
        this.brreg = brreg;
    }

    @Transactional(readOnly = true)
    public List<Kunde> alle() {
        return kunder.findAllByOrderByVirksomhetsnavn();
    }

    @Transactional(readOnly = true)
    public Kunde hent(Long id) {
        return kunder.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke kunde " + id));
    }

    @Transactional(readOnly = true)
    public List<KundeReferanse> referanser(Long kundeId) {
        return referanser.findByKundeId(kundeId);
    }

    @Transactional(readOnly = true)
    public List<KundenummerRegel> regler(Long kundeId) {
        return regler.findByKundeId(kundeId);
    }

    public Kunde opprett(String organisasjonsnummer, String virksomhetsnavn,
                         String fakturamottakerOrgnr, Avtalestatus avtalestatus) {
        validerOrgnr(organisasjonsnummer);
        if (kunder.existsByOrganisasjonsnummer(organisasjonsnummer)) {
            throw new Regelbrudd("Kunde med organisasjonsnummer " + organisasjonsnummer + " finnes allerede");
        }
        validerMotBrreg(organisasjonsnummer);
        Kunde lagret = kunder.save(new Kunde(
                null, organisasjonsnummer, virksomhetsnavn, tomTilNull(fakturamottakerOrgnr),
                avtalestatus.name(), "MANUELL", brukerContext.naavaerendeBruker(), OffsetDateTime.now()));
        audit.logg(Handling.OPPRETTET, ENTITET_KUNDE, lagret.id(),
                Map.of("organisasjonsnummer", organisasjonsnummer, "virksomhetsnavn", virksomhetsnavn));
        return lagret;
    }

    public Kunde oppdater(Long id, String virksomhetsnavn, String fakturamottakerOrgnr,
                          Avtalestatus avtalestatus) {
        Kunde eksisterende = hent(id);
        Kunde oppdatert = new Kunde(
                eksisterende.id(), eksisterende.organisasjonsnummer(), virksomhetsnavn,
                tomTilNull(fakturamottakerOrgnr), avtalestatus.name(), eksisterende.kilde(),
                brukerContext.naavaerendeBruker(), OffsetDateTime.now());
        kunder.save(oppdatert);
        audit.logg(Handling.ENDRET, ENTITET_KUNDE, id,
                Map.of("virksomhetsnavn", virksomhetsnavn, "avtalestatus", avtalestatus.name()));
        return oppdatert;
    }

    public KundeReferanse leggTilReferanse(Long kundeId, Long produktId,
                                           String fakturareferanse, String bestillingsnummer) {
        hent(kundeId);
        KundeReferanse lagret = referanser.save(new KundeReferanse(
                null, kundeId, produktId, tomTilNull(fakturareferanse), tomTilNull(bestillingsnummer)));
        audit.logg(Handling.OPPRETTET, ENTITET_REFERANSE, lagret.id(),
                Map.of("kundeId", kundeId, "produktId", produktId == null ? "" : produktId));
        return lagret;
    }

    public void slettReferanse(Long referanseId) {
        KundeReferanse ref = referanser.findById(referanseId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke referanse " + referanseId));
        referanser.deleteById(referanseId);
        audit.logg(Handling.SLETTET, ENTITET_REFERANSE, referanseId, Map.of("kundeId", ref.kundeId()));
    }

    public KundenummerRegel leggTilRegel(Long kundeId, String kundenummer, Long produktId,
                                         String servicekode, String tilleggstekst) {
        hent(kundeId);
        if (kundenummer == null || kundenummer.isBlank()) {
            throw new Regelbrudd("Kundenummer må angis");
        }
        KundenummerRegel lagret = regler.save(new KundenummerRegel(
                null, kundeId, kundenummer, produktId, tomTilNull(servicekode), tomTilNull(tilleggstekst)));
        audit.logg(Handling.OPPRETTET, ENTITET_REGEL, lagret.id(),
                Map.of("kundeId", kundeId, "kundenummer", kundenummer));
        return lagret;
    }

    public void slettRegel(Long regelId) {
        KundenummerRegel regel = regler.findById(regelId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke kundenummerregel " + regelId));
        regler.deleteById(regelId);
        audit.logg(Handling.SLETTET, ENTITET_REGEL, regelId, Map.of("kundeId", regel.kundeId()));
    }

    private static void validerOrgnr(String orgnr) {
        if (orgnr == null || !orgnr.matches("^[0-9]{9}$")) {
            throw new Regelbrudd("Organisasjonsnummer må være 9 siffer");
        }
    }

    /**
     * Validate the organisasjonsnummer against BRREG (K-16). Blocks creation only when BRREG is
     * reachable and confirms the number does not exist; if BRREG is unavailable the check is skipped
     * (it is a validation aid, not a hard dependency). Offline profiles use a permissive stub.
     */
    private void validerMotBrreg(String orgnr) {
        if (brreg.slaaOpp(orgnr).erIkkeFunnet()) {
            throw new Regelbrudd("Organisasjonsnummer " + orgnr
                    + " finnes ikke i Enhetsregisteret (BRREG)");
        }
    }

    /**
     * The official BRREG name when it differs from the entered {@code virksomhetsnavn}, for a
     * non-blocking warning (K-16). Empty when the names match, the orgnr is unknown, or BRREG is
     * unavailable — a name mismatch is only advisory, never blocking.
     */
    @Transactional(readOnly = true)
    public Optional<String> brregNavnAdvarsel(String orgnr, String virksomhetsnavn) {
        var svar = brreg.slaaOpp(orgnr);
        if (svar.erFunnet() && svar.offisieltNavn() != null
                && !svar.offisieltNavn().strip().equalsIgnoreCase(
                        virksomhetsnavn == null ? "" : virksomhetsnavn.strip())) {
            return Optional.of(svar.offisieltNavn());
        }
        return Optional.empty();
    }

    private static String tomTilNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
