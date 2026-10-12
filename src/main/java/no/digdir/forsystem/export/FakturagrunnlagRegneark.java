package no.digdir.forsystem.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.billing.FakturakjoringService.Grunnlagslinje;
import no.digdir.forsystem.billing.Fakturalinje;
import no.digdir.forsystem.registry.ProduktRepository;
import org.springframework.stereotype.Component;

/**
 * The one column definition of the fakturagrunnlag spreadsheet (specs/002 D8, data-model.md), used both
 * for the files archived at export and for the download before approval — so the draft and the approved
 * basis can be compared column by column (FR-019). One row per invoice line, ordered like the LG04.
 */
@Component
public class FakturagrunnlagRegneark {

    static final List<String> KOLONNER = List.of("periode", "ordre", "kundenummer", "kundenavn",
            "organisasjonsnummer", "fakturamottaker", "tilleggstekst", "produkt", "type", "beskrivelse",
            "servicekode", "fakturareferanse", "bestillingsnummer", "antall", "enhetspris", "belop",
            "bruksdata_id", "pris_id");

    /** Header plus typed rows (numbers stay numbers for XLSX). */
    public record Tabell(List<String> kolonner, List<List<Object>> rader) {
    }

    private final FakturakjoringService kjoringer;
    private final ProduktRepository produkter;

    FakturagrunnlagRegneark(FakturakjoringService kjoringer, ProduktRepository produkter) {
        this.kjoringer = kjoringer;
        this.produkter = produkter;
    }

    public Tabell bygg(Fakturakjoring kj) {
        Map<Long, String> kodeById = new HashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> kodeById.put(p.id(), p.kode()));
        String periode = kj.periode().toString().substring(0, 7);
        List<List<Object>> rader = new ArrayList<>();
        for (Grunnlagslinje g : kjoringer.grunnlagslinjer(kj.id())) {
            Fakturalinje l = g.linje();
            rader.add(Arrays.asList(periode, g.faktura().ordreNr(), g.faktura().kundenummer(), g.kundenavn(),
                    g.organisasjonsnummer(), g.faktura().fakturamottakerOrgnr(), g.faktura().tilleggstekst(),
                    kodeById.get(l.produktId()), g.type(), l.beskrivelse(), l.servicekode(), l.fakturareferanse(),
                    l.bestillingsnummer(), l.antall(), l.enhetspris(), l.belop(), l.bruksdataId(), l.prisId()));
        }
        return new Tabell(KOLONNER, rader);
    }
}
