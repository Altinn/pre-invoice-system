package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avregning (specs/020): what the Avregning tab shows and the entry point that turns ticked months and price
 * adjustments into a draft run. The generation itself is {@link FakturakjoringService#genererAvregning}, so the
 * run follows the ordinary controls, approval and export.
 */
@Service
public class AvregningService {

    /**
     * Altinn Studio applikasjonsinfrastruktur is never settled: it is invoiced at the booked Azure cost, so the
     * invoiced amount already equals the booked cost 1:1 and there is no price to adjust afterwards (OQ-30).
     */
    public static final String UNNTATT_PRODUKTKODE = "appinfra";

    /** A month of the year: whether usage is imported for it and whether an ordinary run has been exported. */
    public record Maaned(Periode periode, boolean harBruksdata, boolean eksportert) {
    }

    /** A product row of the form; {@code unntatt} products cannot be adjusted. */
    public record Produktrad(Produkt produkt, boolean unntatt) {
    }

    private final UsageImportService usage;
    private final FakturakjoringRepository kjoringer;
    private final ProduktRepository produkter;
    private final FakturakjoringService tjeneste;
    private final AvregningRepository avregninger;

    AvregningService(UsageImportService usage, FakturakjoringRepository kjoringer, ProduktRepository produkter,
                     FakturakjoringService tjeneste, AvregningRepository avregninger) {
        this.usage = usage;
        this.kjoringer = kjoringer;
        this.produkter = produkter;
        this.tjeneste = tjeneste;
        this.avregninger = avregninger;
    }

    @Transactional(readOnly = true)
    public List<Maaned> maaneder(int aar) {
        return IntStream.rangeClosed(1, 12).mapToObj(m -> {
            Periode p = Periode.av(aar, m);
            return new Maaned(p, usage.gjeldendeImport(p).isPresent(),
                    kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(p.førsteDag(),
                            Kjoringstatus.EKSPORTERT.name()).isPresent());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Produktrad> produkter() {
        return produkter.findAllByOrderByKode().stream().filter(Produkt::aktiv)
                .map(p -> new Produktrad(p, UNNTATT_PRODUKTKODE.equals(p.kode()))).toList();
    }

    /**
     * The year the form starts on: that of the newest ordinary run, else the current year. Only a form default;
     * the year of a settlement is always the explicit parameter of the request.
     */
    @Transactional(readOnly = true)
    public int standardAar() {
        return kjoringer.findAllByOrderByGenerertAtDesc().stream().filter(k -> !k.erAvregning()).findFirst()
                .map(k -> k.periode().getYear()).orElse(LocalDate.now().getYear());
    }

    @Transactional(readOnly = true)
    public List<Fakturakjoring> avregninger() {
        return kjoringer.findAllByOrderByGenerertAtDesc().stream().filter(Fakturakjoring::erAvregning).toList();
    }

    @Transactional(readOnly = true)
    public List<LocalDate> maanederFor(Long kjoringId) {
        return avregninger.maaneder(kjoringId).stream().map(AvregningRepository.Maaned::periode).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> justeringerFor(Long kjoringId) {
        return avregninger.justeringer(kjoringId);
    }

    public Fakturakjoring generer(int aar, List<Periode> maaneder, Map<Long, BigDecimal> justeringer) {
        return tjeneste.genererAvregning(aar, maaneder, justeringer);
    }
}
