package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import no.digdir.forsystem.usage.KjoringStatusPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Billing-side implementation of the usage import's {@link KjoringStatusPort}. */
@Component
class KjoringStatusAdapter implements KjoringStatusPort {

    private static final String FORKASTET = "FORKASTET";

    private final FakturakjoringRepository kjoringer;
    private final KorrigeringskjoringEndringRepository valgteEndringer;
    private final JdbcTemplate jdbc;

    KjoringStatusAdapter(FakturakjoringRepository kjoringer,
                         KorrigeringskjoringEndringRepository valgteEndringer, JdbcTemplate jdbc) {
        this.kjoringer = kjoringer;
        this.valgteEndringer = valgteEndringer;
        this.jdbc = jdbc;
    }

    @Override
    public boolean finnesIkkeForkastetKjoring(LocalDate periode) {
        return kjoringer.existsByPeriodeAndStatusNot(periode, FORKASTET);
    }

    @Override
    public boolean finnesPaagaaendeKjoring(LocalDate periode) {
        return kjoringer.existsByPeriodeAndStatusIn(periode,
                List.of(Kjoringstatus.GENERERT.name(), Kjoringstatus.GODKJENT.name()));
    }

    @Override
    public Optional<EffektivtGrunnlag> effektivtGrunnlag(LocalDate periode) {
        Optional<Fakturakjoring> siste = kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(
                periode, Kjoringstatus.EKSPORTERT.name());
        Optional<Fakturakjoring> rot = kjoringer.findFirstByPeriodeAndTypeAndStatusOrderByVersjonAsc(
                periode, Kjoringstype.ORDINAER.name(), Kjoringstatus.EKSPORTERT.name());
        if (siste.isEmpty() || rot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EffektivtGrunnlag(siste.get().id(), siste.get().versjon(),
                rot.get().bruksdataImportId(), valgteEndringer.eksporterteEndringIder(periode)));
    }

    @Override
    public Map<String, FakturertKunde> fakturerteKunder(LocalDate periode) {
        // Newest exported version first; per orgnr only the rows of the first version that invoiced it count.
        Map<String, Integer> versjonPerOrgnr = new LinkedHashMap<>();
        Map<String, List<TreeSet<String>>> verdier = new LinkedHashMap<>();
        jdbc.query("""
                select f.organisasjonsnummer, f.kundenavn, f.kundenummer, l.id as linje_id, l.fakturareferanse,
                       l.bestillingsnummer, k.versjon
                  from faktura f
                  join fakturakjoring k on k.id = f.kjoring_id
                  left join fakturalinje l on l.faktura_id = f.id
                 where k.periode = ? and k.status = 'EKSPORTERT'
                 order by k.versjon desc, f.id, l.id
                """, rs -> {
            String orgnr = rs.getString("organisasjonsnummer");
            int versjon = rs.getInt("versjon");
            if (versjonPerOrgnr.computeIfAbsent(orgnr, o -> versjon) != versjon) {
                return;
            }
            List<TreeSet<String>> felt = verdier.computeIfAbsent(orgnr, o -> {
                List<TreeSet<String>> ny = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    ny.add(new TreeSet<>());
                }
                return ny;
            });
            felt.get(0).add(tekst(rs.getString("kundenavn")));
            felt.get(1).add(tekst(rs.getString("kundenummer")));
            // An invoice without lines has no reference to compare; it adds none.
            if (rs.getObject("linje_id") != null) {
                felt.get(2).add(tekst(rs.getString("fakturareferanse")));
                felt.get(3).add(tekst(rs.getString("bestillingsnummer")));
            }
        }, periode);
        Map<String, FakturertKunde> ut = new LinkedHashMap<>();
        verdier.forEach((orgnr, f) -> ut.put(orgnr, new FakturertKunde(List.copyOf(f.get(0)), List.copyOf(f.get(1)),
                List.copyOf(f.get(2)), List.copyOf(f.get(3)))));
        return ut;
    }

    private static String tekst(String verdi) {
        return verdi == null ? "" : verdi.strip();
    }
}
