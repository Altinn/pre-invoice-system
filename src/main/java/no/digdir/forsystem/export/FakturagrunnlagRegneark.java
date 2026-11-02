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
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataEndringRepository;
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
    static final List<String> KORRIGERING_KOLONNER;

    static {
        List<String> kolonner = new ArrayList<>(KOLONNER);
        kolonner.addAll(List.of("kjoringstype", "versjon", "bruksdata_endring_id", "endringstype",
                "gammel_verdi", "ny_verdi", "differanse"));
        KORRIGERING_KOLONNER = List.copyOf(kolonner);
    }

    /** Header plus typed rows (numbers stay numbers for XLSX). */
    public record Tabell(List<String> kolonner, List<List<Object>> rader) {
    }

    private final FakturakjoringService kjoringer;
    private final ProduktRepository produkter;
    private final BruksdataEndringRepository endringer;

    FakturagrunnlagRegneark(FakturakjoringService kjoringer, ProduktRepository produkter,
                            BruksdataEndringRepository endringer) {
        this.kjoringer = kjoringer;
        this.produkter = produkter;
        this.endringer = endringer;
    }

    public Tabell bygg(Fakturakjoring kj) {
        Map<Long, String> kodeById = new HashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> kodeById.put(p.id(), p.kode()));
        String periode = kj.periode().toString().substring(0, 7);
        Map<Long, BruksdataEndring> endringById = new HashMap<>();
        if (kj.erKorrigering()) {
            endringer.findAllById(kjoringer.valgteEndringIder(kj.id()))
                    .forEach(e -> endringById.put(e.id(), e));
        }
        List<List<Object>> rader = new ArrayList<>();
        for (Grunnlagslinje g : kjoringer.grunnlagslinjer(kj.id())) {
            Fakturalinje l = g.linje();
            List<Object> rad = new ArrayList<>(Arrays.asList(
                    periode, g.faktura().ordreNr(), g.faktura().kundenummer(), g.kundenavn(),
                    g.organisasjonsnummer(), g.faktura().fakturamottakerOrgnr(), g.faktura().tilleggstekst(),
                    kodeById.get(l.produktId()), g.type(), l.beskrivelse(), l.servicekode(), l.fakturareferanse(),
                    l.bestillingsnummer(), l.antall(), l.enhetspris(), l.belop(), l.bruksdataId(), l.prisId()));
            if (kj.erKorrigering()) {
                BruksdataEndring e = endringById.get(l.bruksdataEndringId());
                rad.addAll(Arrays.asList(kj.type(), kj.versjon(), l.bruksdataEndringId(),
                        e == null ? null : e.endringstype(), e == null ? null : e.gammelVerdi(),
                        e == null ? null : e.nyVerdi(), e == null ? null : e.delta()));
            }
            rader.add(rad);
        }
        return new Tabell(kj.erKorrigering() ? KORRIGERING_KOLONNER : KOLONNER, rader);
    }
}
