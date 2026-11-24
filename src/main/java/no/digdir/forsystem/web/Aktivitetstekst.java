package no.digdir.forsystem.web;

import java.util.Map;
import no.digdir.forsystem.common.Detaljtekst;
import no.digdir.forsystem.common.Jsonb;
import no.digdir.forsystem.common.Periode;

/**
 * One audit row as a short sentence for «Siste aktivitet» on the start page (specs/015):
 * {@code GENERERTE FAKTURAKJORING {"periode":"2027-01"}} reads «Genererte kjøring for januar 2027».
 * Unknown actions or entities fall back to the raw words in lower case, so nothing is hidden.
 */
final class Aktivitetstekst {

    private static final Map<String, String> VERB = Map.ofEntries(
            Map.entry("OPPRETTET", "Opprettet"),
            Map.entry("ENDRET", "Endret"),
            Map.entry("SLETTET", "Slettet"),
            Map.entry("AKTIVERTE", "Aktiverte"),
            Map.entry("ARKIVERTE", "Arkiverte"),
            Map.entry("IMPORTERTE", "Importerte"),
            Map.entry("AVVISTE", "Avviste"),
            Map.entry("GENERERTE", "Genererte"),
            Map.entry("GODKJENTE", "Godkjente"),
            Map.entry("FORKASTET", "Forkastet"),
            Map.entry("HENTET", "Hentet"),
            Map.entry("SAMMENLIGNET", "Sammenlignet"),
            Map.entry("EKSPORTERTE", "Eksporterte"),
            Map.entry("EKSPORT_STOPPET", "Stoppet eksport av"),
            Map.entry("LASTET_NED", "Lastet ned"),
            Map.entry("AKSEPTERTE_FUNN", "Aksepterte funn i"),
            Map.entry("ANGRET_AKSEPT", "Angret aksept av funn i"),
            Map.entry("NULLSTILTE", "Nullstilte"));

    private static final Map<String, String> ENTITET = Map.ofEntries(
            Map.entry("FAKTURAKJORING", "kjøring"),
            Map.entry("BRUKSDATA_IMPORT", "bruksdata"),
            Map.entry("BRUKSDATA_SAMMENLIGNING", "bruksdata"),
            Map.entry("PRISVERSJON", "prisversjon"),
            Map.entry("PRODUKT", "produkt"),
            Map.entry("PRODUKT_KILDENAVN", "kildenavn"),
            Map.entry("KUNDE", "kunde"),
            Map.entry("KUNDE_REFERANSE", "kundereferanse"),
            Map.entry("KUNDENUMMER_REGEL", "kundenummerregel"),
            Map.entry("PERIODE", "periode"),
            Map.entry("HENDELSESLOGG", "hendelseslogg"));

    private Aktivitetstekst() {
    }

    static String av(String handling, String entitet, Jsonb detaljer) {
        String verb = VERB.getOrDefault(handling, handling == null ? "" : handling.toLowerCase().replace('_', ' '));
        String objekt = entitet == null ? "" : ENTITET.getOrDefault(entitet, entitet.toLowerCase().replace('_', ' '));
        String tekst = (verb + " " + objekt).strip();
        String periode = periodeLabel(Detaljtekst.periode(detaljer));
        return periode == null ? tekst : tekst + " for " + periode;
    }

    private static String periodeLabel(String isoMaaned) {
        if (isoMaaned == null || isoMaaned.isBlank()) {
            return null;
        }
        try {
            return Periode.parse(isoMaaned).norskLabel();
        } catch (RuntimeException e) {
            return isoMaaned;
        }
    }

    /** The audit user as shown: the automatic jobs log as {@code system}. */
    static String bruker(String bruker) {
        return "system".equals(bruker) ? "System" : bruker;
    }
}
