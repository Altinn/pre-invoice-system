package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the source product-name mapping ({@link ProduktKildenavn}). Every write is audited.
 * Changing a mapping never touches existing imports; it applies from the next fetch.
 */
@Service
@Transactional
public class ProduktKildenavnService {

    static final String ENTITET = "PRODUKT_KILDENAVN";
    public static final String KILDE_DWH = "DWH";
    private static final Set<String> TYPER = Set.of("BRUKSVOLUM", "AZURE_KOSTNAD", "SMS_KOSTNAD");

    private final ProduktKildenavnRepository kildenavn;
    private final ProduktRepository produkter;
    private final AuditService audit;

    ProduktKildenavnService(ProduktKildenavnRepository kildenavn, ProduktRepository produkter, AuditService audit) {
        this.kildenavn = kildenavn;
        this.produkter = produkter;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProduktKildenavn> alle() {
        return kildenavn.findByKildeOrderByKildenavn(KILDE_DWH);
    }

    public ProduktKildenavn opprett(String navn, Long produktId, String type) {
        String trimmet = navn == null ? "" : navn.trim();
        if (trimmet.isEmpty()) {
            throw new Regelbrudd("Kildenavn må fylles ut");
        }
        if (!TYPER.contains(type)) {
            throw new Regelbrudd("Ugyldig type: " + type);
        }
        Produkt produkt = produkter.findById(produktId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke produkt " + produktId));
        if (kildenavn.findByKildeAndKildenavn(KILDE_DWH, trimmet).isPresent()) {
            throw new Regelbrudd("Kildenavnet er allerede koblet: " + trimmet);
        }
        ProduktKildenavn lagret = kildenavn.save(new ProduktKildenavn(null, KILDE_DWH, trimmet, produktId, type));
        audit.logg(Handling.OPPRETTET, ENTITET, lagret.id(),
                Map.of("kildenavn", trimmet, "produkt", produkt.kode(), "type", type));
        return lagret;
    }

    public void slett(Long id) {
        ProduktKildenavn eksisterende = kildenavn.findById(id)
                .orElseThrow(() -> new Regelbrudd("Fant ikke kobling " + id));
        kildenavn.delete(eksisterende);
        audit.logg(Handling.SLETTET, ENTITET, id, Map.of("kildenavn", eksisterende.kildenavn()));
    }
}
