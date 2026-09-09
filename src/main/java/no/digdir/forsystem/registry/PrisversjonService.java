package no.digdir.forsystem.registry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Price-version lifecycle and validation (docs/04 Phase 1):
 * <ul>
 *   <li>prices may only be set on an UTKAST version;</li>
 *   <li>activation requires that every <em>active</em> product has a price in the version, and that
 *       no other AKTIV version overlaps this one's date range;</li>
 *   <li>lifecycle is one-way UTKAST → AKTIV → ARKIVERT.</li>
 * </ul>
 */
@Service
@Transactional
public class PrisversjonService {

    static final String ENTITET = "PRISVERSJON";

    private final PrisversjonRepository prisversjoner;
    private final PrisRepository priser;
    private final ProduktRepository produkter;
    private final AuditService audit;

    PrisversjonService(PrisversjonRepository prisversjoner, PrisRepository priser,
                       ProduktRepository produkter, AuditService audit) {
        this.prisversjoner = prisversjoner;
        this.priser = priser;
        this.produkter = produkter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Prisversjon> alle() {
        return prisversjoner.findAllByOrderByGyldigFraDesc();
    }

    @Transactional(readOnly = true)
    public Prisversjon hent(Long id) {
        return prisversjoner.findById(id)
                .orElseThrow(() -> new Regelbrudd("Fant ikke prisversjon " + id));
    }

    @Transactional(readOnly = true)
    public List<Pris> priser(Long prisversjonId) {
        return priser.findByPrisversjonId(prisversjonId);
    }

    public Prisversjon opprettUtkast(String navn, LocalDate gyldigFra, LocalDate gyldigTil) {
        if (gyldigFra == null) {
            throw new Regelbrudd("Gyldig fra må angis");
        }
        if (gyldigTil != null && gyldigTil.isBefore(gyldigFra)) {
            throw new Regelbrudd("Gyldig til kan ikke være før gyldig fra");
        }
        Prisversjon lagret = prisversjoner.save(new Prisversjon(
                null, navn, gyldigFra, gyldigTil, PrisversjonStatus.UTKAST.name()));
        audit.logg(Handling.OPPRETTET, ENTITET, lagret.id(), Map.of("navn", navn));
        return lagret;
    }

    /** Insert or update the unit price for a product in an UTKAST version. */
    public void settPris(Long prisversjonId, Long produktId, BigDecimal enhetspris) {
        Prisversjon versjon = hent(prisversjonId);
        if (versjon.statusEnum() != PrisversjonStatus.UTKAST) {
            throw new Regelbrudd("Priser kan bare endres i et utkast (versjonen er " + versjon.status() + ")");
        }
        if (enhetspris == null || enhetspris.signum() < 0) {
            throw new Regelbrudd("Enhetspris må være null eller positiv");
        }
        Pris pris = priser.findByPrisversjonIdAndProduktId(prisversjonId, produktId)
                .map(p -> new Pris(p.id(), p.prisversjonId(), p.produktId(), enhetspris))
                .orElseGet(() -> new Pris(null, prisversjonId, produktId, enhetspris));
        Pris lagret = priser.save(pris);
        audit.logg(Handling.ENDRET, ENTITET, prisversjonId,
                Map.of("prisId", lagret.id(), "produktId", produktId, "enhetspris", enhetspris));
    }

    public void aktiver(Long id) {
        Prisversjon versjon = hent(id);
        if (versjon.statusEnum() != PrisversjonStatus.UTKAST) {
            throw new Regelbrudd("Bare et utkast kan aktiveres (versjonen er " + versjon.status() + ")");
        }
        sjekkIngenOverlappendeAktiv(versjon);
        sjekkAlleAktiveProdukterHarPris(versjon);

        prisversjoner.save(new Prisversjon(
                versjon.id(), versjon.navn(), versjon.gyldigFra(), versjon.gyldigTil(),
                PrisversjonStatus.AKTIV.name()));
        audit.logg(Handling.AKTIVERTE, ENTITET, id, Map.of("navn", versjon.navn()));
    }

    public void arkiver(Long id) {
        Prisversjon versjon = hent(id);
        if (versjon.statusEnum() != PrisversjonStatus.AKTIV) {
            throw new Regelbrudd("Bare en aktiv versjon kan arkiveres (versjonen er " + versjon.status() + ")");
        }
        prisversjoner.save(new Prisversjon(
                versjon.id(), versjon.navn(), versjon.gyldigFra(), versjon.gyldigTil(),
                PrisversjonStatus.ARKIVERT.name()));
        audit.logg(Handling.ARKIVERTE, ENTITET, id, Map.of("navn", versjon.navn()));
    }

    private void sjekkIngenOverlappendeAktiv(Prisversjon kandidat) {
        for (Prisversjon aktiv : prisversjoner.findByStatus(PrisversjonStatus.AKTIV.name())) {
            if (!aktiv.id().equals(kandidat.id()) && overlapper(kandidat, aktiv)) {
                throw new Regelbrudd("Datointervallet overlapper med aktiv prisversjon: " + aktiv.navn());
            }
        }
    }

    private void sjekkAlleAktiveProdukterHarPris(Prisversjon versjon) {
        List<Long> medPris = priser.findByPrisversjonId(versjon.id()).stream().map(Pris::produktId).toList();
        List<String> mangler = produkter.findByAktivTrue().stream()
                .filter(p -> !medPris.contains(p.id()))
                .map(Produkt::kode)
                .sorted()
                .toList();
        if (!mangler.isEmpty()) {
            throw new Regelbrudd("Aktive produkter mangler pris i versjonen: " + String.join(", ", mangler));
        }
    }

    /** Two versions overlap when each starts on or before the other's end (null end = open-ended). */
    private static boolean overlapper(Prisversjon a, Prisversjon b) {
        boolean aStarterFørBSlutter = b.gyldigTil() == null || !a.gyldigFra().isAfter(b.gyldigTil());
        boolean bStarterFørASlutter = a.gyldigTil() == null || !b.gyldigFra().isAfter(a.gyldigTil());
        return aStarterFørBSlutter && bStarterFørASlutter;
    }
}
