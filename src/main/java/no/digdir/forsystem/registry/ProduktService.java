package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains products. Every write is audited. */
@Service
@Transactional
public class ProduktService {

    static final String ENTITET = "PRODUKT";

    private final ProduktRepository produkter;
    private final AuditService audit;

    ProduktService(ProduktRepository produkter, AuditService audit) {
        this.produkter = produkter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Produkt> alle() {
        return produkter.findAllByOrderByKode();
    }

    @Transactional(readOnly = true)
    public Produkt hent(Long id) {
        return produkter.findById(id)
                .orElseThrow(() -> new Regelbrudd("Fant ikke produkt " + id));
    }

    public Produkt opprett(String kode, String navn, String enhet) {
        if (produkter.findByKode(kode).isPresent()) {
            throw new Regelbrudd("Produktkode finnes allerede: " + kode);
        }
        Produkt lagret = produkter.save(new Produkt(
                null, kode, navn, null, null, null, null, null,
                enhet == null || enhet.isBlank() ? "transaksjon" : enhet, true));
        audit.logg(Handling.OPPRETTET, ENTITET, lagret.id(), Map.of("kode", kode, "navn", navn));
        return lagret;
    }

    /** Update the display name, kontering (OQ-2), unit, and active flag. Product code is immutable. */
    public Produkt oppdater(Long id, String navn, Integer artikkelId, String konto,
                            String dim1, String dim2, String dim4, String enhet, boolean aktiv) {
        Produkt eksisterende = hent(id);
        Produkt oppdatert = new Produkt(
                eksisterende.id(), eksisterende.kode(), navn, artikkelId, konto,
                tomTilNull(dim1), tomTilNull(dim2), tomTilNull(dim4),
                enhet == null || enhet.isBlank() ? "transaksjon" : enhet, aktiv);
        produkter.save(oppdatert);
        audit.logg(Handling.ENDRET, ENTITET, id, Map.of(
                "navn", navn, "konto", Optional.ofNullable(konto).orElse(""), "aktiv", aktiv));
        return oppdatert;
    }

    private static String tomTilNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
