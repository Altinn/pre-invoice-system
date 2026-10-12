package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the usage reconciliation for a run (specs/002 US2, D2): the rows of the import the run was built
 * from, the run's lines, and the finding links written at generation. Read-only.
 */
@Service
public class BruksdataavstemmingService {

    static final String UKJENT_AARSAK = "årsak ikke registrert (kjøring generert før avstemming fantes)";

    private final FakturakjoringService kjoringer;
    private final FakturalinjeRepository linjer;
    private final KontrollfunnBruksdataRepository funnBruksdata;
    private final ProduktRepository produkter;
    private final UsageImportService usage;

    BruksdataavstemmingService(FakturakjoringService kjoringer, FakturalinjeRepository linjer,
                               KontrollfunnBruksdataRepository funnBruksdata, ProduktRepository produkter,
                               UsageImportService usage) {
        this.kjoringer = kjoringer;
        this.linjer = linjer;
        this.funnBruksdata = funnBruksdata;
        this.produkter = produkter;
        this.usage = usage;
    }

    @Transactional(readOnly = true)
    public Bruksdataavstemming forKjoring(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        Periode periode = new Periode(kj.periode());
        List<Produkt> alleProdukter = produkter.findAllByOrderByKode();
        Map<Long, Produkt> produktById = alleProdukter.stream()
                .collect(Collectors.toMap(Produkt::id, Function.identity()));

        BruksdataImport kilde = kj.bruksdataImportId() == null ? null : usage.hentImport(kj.bruksdataImportId());
        List<Bruksdata> importert = kilde == null ? List.of() : usage.rader(kilde.id());
        Map<Long, Bruksdata> importertById = importert.stream()
                .collect(Collectors.toMap(Bruksdata::id, Function.identity()));

        // Per (product, type): imported rows/sum, invoiced lines/sum, not-invoiced rows.
        Map<String, Akk> akk = new LinkedHashMap<>();
        for (Bruksdata b : importert) {
            Akk a = akk.computeIfAbsent(nokkel(b.produktId(), b.type()), x -> new Akk(b.produktId(), b.type()));
            a.importertRader++;
            a.importertSum = a.importertSum.add(verdi(b.type(), b.antall(), b.belop()));
        }
        Set<Long> fakturerte = new java.util.HashSet<>();
        int linjerUtenImport = 0;
        for (Fakturalinje l : linjer.findByKjoringId(kjoringId)) {
            Bruksdata b = l.bruksdataId() == null ? null : importertById.get(l.bruksdataId());
            if (b == null) {
                linjerUtenImport++;
                continue;
            }
            fakturerte.add(b.id());
            Akk a = akk.get(nokkel(b.produktId(), b.type()));
            a.fakturertLinjer++;
            a.fakturertSum = a.fakturertSum.add(verdi(b.type(), l.antall(), l.belop()));
        }

        Map<Long, KontrollfunnBruksdataRepository.Utelatt> aarsak = funnBruksdata.forKjoring(kjoringId).stream()
                .collect(Collectors.toMap(KontrollfunnBruksdataRepository.Utelatt::bruksdataId, Function.identity(),
                        (a, b) -> a));
        List<Bruksdataavstemming.IkkeFakturert> ikkeFakturert = new ArrayList<>();
        for (Bruksdata b : importert) {
            if (fakturerte.contains(b.id())) {
                continue;
            }
            akk.get(nokkel(b.produktId(), b.type())).ikkeFakturertRader++;
            var u = aarsak.get(b.id());
            ikkeFakturert.add(new Bruksdataavstemming.IkkeFakturert(b.id(), b.organisasjonsnummer(),
                    produktById.get(b.produktId()).kode(), b.type(), b.antall(), b.belop(),
                    u == null ? null : u.kode(), u == null ? UKJENT_AARSAK : u.melding()));
        }
        ikkeFakturert.sort(Comparator.comparing(Bruksdataavstemming.IkkeFakturert::organisasjonsnummer)
                .thenComparing(Bruksdataavstemming.IkkeFakturert::produktKode)
                .thenComparing(Bruksdataavstemming.IkkeFakturert::type));

        // Every active product appears, even without usage, so its absence is visible (T10).
        Set<Long> medBruk = akk.values().stream().map(a -> a.produktId).collect(Collectors.toSet());
        List<Bruksdataavstemming.Rad> rader = new ArrayList<>();
        for (Produkt p : alleProdukter) {
            List<Akk> forProdukt = akk.values().stream().filter(a -> a.produktId.equals(p.id()))
                    .sorted(Comparator.comparing(a -> a.type)).toList();
            for (Akk a : forProdukt) {
                rader.add(new Bruksdataavstemming.Rad(p.kode(), p.navn(), a.type, a.importertRader, a.importertSum,
                        a.fakturertLinjer, a.fakturertSum, a.ikkeFakturertRader));
            }
            if (p.aktiv() && !medBruk.contains(p.id())) {
                rader.add(new Bruksdataavstemming.Rad(p.kode(), p.navn(), null, 0, BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, 0));
            }
        }
        return new Bruksdataavstemming(kjoringId, periode, kilde, rader, ikkeFakturert, linjerUtenImport);
    }

    private static String nokkel(Long produktId, String type) {
        return produktId + "/" + type;
    }

    /** Volume types reconcile on quantity, pass-through cost types on amount. */
    private static BigDecimal verdi(String type, BigDecimal antall, BigDecimal belop) {
        BigDecimal v = Bruksdatatype.valueOf(type).erVolum() ? antall : belop;
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final class Akk {
        final Long produktId;
        final String type;
        int importertRader;
        BigDecimal importertSum = BigDecimal.ZERO;
        int fakturertLinjer;
        BigDecimal fakturertSum = BigDecimal.ZERO;
        int ikkeFakturertRader;

        Akk(Long produktId, String type) {
            this.produktId = produktId;
            this.type = type;
        }
    }
}
