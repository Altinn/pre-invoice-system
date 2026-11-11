package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.digdir.forsystem.common.Jsonb;
import no.digdir.forsystem.common.Norsktall;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.usage.DwhEgenskaper;
import no.digdir.forsystem.web.Forside.Aktivitet;
import no.digdir.forsystem.web.Forside.Lenke;
import no.digdir.forsystem.web.Forside.Nokkeltall;
import no.digdir.forsystem.web.Forside.Produktfordeling;
import no.digdir.forsystem.web.Forside.Stolpe;
import no.digdir.forsystem.web.Forside.Varsel;
import no.digdir.forsystem.web.Fremdrift.Steg;
import no.digdir.forsystem.web.Fremdrift.Tilstand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the start page (specs/015). Read-only: it writes nothing, not even to the audit trail.
 *
 * <p>The dashboard period is chosen from the data, never from the clock (CLAUDE.md): the oldest unfinished
 * ordinary run wins, otherwise the newest period with an ordinary run or a usage import. Correction runs never
 * choose the period. The clock is used only for the greeting, today's date, «… siden» texts and the next
 * scheduled DWH fetch.
 */
@Service
public class ForsideService {

    private static final int ANTALL_AKTIVITETER = 6;

    /** Who is looking: the first name for the greeting and which actions the roles allow. */
    public record Bruker(String fornavn, boolean forvalter, boolean godkjenner) {
    }

    private record Import(long id, String kilde, String status, String lastetAv, OffsetDateTime lastetAt,
                          OffsetDateTime hentetAt) {
        boolean bekreftet() {
            return "VALIDERT".equals(status);
        }

        OffsetDateTime tidspunkt() {
            return hentetAt != null ? hentetAt : lastetAt;
        }
    }

    private record Kjoring(long id, String status, Long bruksdataImportId, String generertAv,
                           OffsetDateTime generertAt, String godkjentAv, OffsetDateTime godkjentAt) {
    }

    private record Eksport(String av, OffsetDateTime at) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Tallformat tall;
    private final DwhEgenskaper dwh;
    private final Clock klokke;

    @Autowired
    ForsideService(NamedParameterJdbcTemplate jdbc, Tallformat tall, DwhEgenskaper dwh) {
        this(jdbc, tall, dwh, Clock.system(Tidstekst.OSLO));
    }

    ForsideService(NamedParameterJdbcTemplate jdbc, Tallformat tall, DwhEgenskaper dwh, Clock klokke) {
        this.jdbc = jdbc;
        this.tall = tall;
        this.dwh = dwh;
        this.klokke = klokke;
    }

    @Transactional(readOnly = true)
    public Forside les(Bruker bruker) {
        ZonedDateTime naa = ZonedDateTime.now(klokke);
        String hilsen = Tidstekst.hilsen(naa) + (bruker.fornavn() == null ? "" : ", " + bruker.fornavn());
        Optional<ZonedDateTime> nesteHenting = nesteDwhHenting(naa);
        List<Aktivitet> aktivitet = aktivitet(naa);
        Optional<Periode> valgt = dashbordperiode();

        if (valgt.isEmpty()) {
            return new Forside(hilsen, Tidstekst.dag(naa), null,
                    "Det finnes ingen bruksdata eller kjøringer ennå. Start med å hente bruksdata for den første perioden.",
                    new Lenke("Hent bruksdata", "/bruksdata"), null, List.of(), null,
                    oppmerksomhet(null), aktivitet);
        }

        Periode periode = valgt.get();
        Optional<Import> imp = importFor(periode);
        Optional<Kjoring> kjoring = kjoringFor(periode);
        Optional<Eksport> eksport = kjoring.flatMap(k -> eksport(k.id()));
        long blokkerende = kjoring.map(k -> antallFunn(k.id(), true)).orElse(0L);
        long advarsler = kjoring.map(k -> antallFunn(k.id(), false)).orElse(0L);

        Fremdrift fremdrift = fremdrift(imp, kjoring, eksport);
        String status = status(periode, imp, kjoring, blokkerende, advarsler, nesteHenting);
        Lenke handling = handling(bruker, imp, kjoring, blokkerende, advarsler);

        return new Forside(hilsen, Tidstekst.dag(naa), periode.norskLabel(), status, handling, fremdrift,
                nokkeltall(periode, imp, kjoring, naa, nesteHenting),
                produkter(imp, kjoring), oppmerksomhet(periode), aktivitet);
    }

    // ---- The period rule ---------------------------------------------------------------------------------

    /** specs/015 Clarifications: unfinished ordinary run first, then the newest period with data. */
    Optional<Periode> dashbordperiode() {
        LocalDate uferdig = jdbc.queryForObject("""
                select min(periode) from fakturakjoring
                 where type = 'ORDINAER' and status in ('GENERERT', 'GODKJENT')""",
                new MapSqlParameterSource(), LocalDate.class);
        if (uferdig != null) {
            return Optional.of(new Periode(uferdig));
        }
        LocalDate nyeste = jdbc.queryForObject("""
                select max(periode) from (
                    select periode from fakturakjoring where type = 'ORDINAER' and status <> 'FORKASTET'
                    union all
                    select periode from bruksdata_import where status in ('MOTTATT', 'VALIDERT')) p""",
                new MapSqlParameterSource(), LocalDate.class);
        return Optional.ofNullable(nyeste).map(Periode::new);
    }

    private Optional<Import> importFor(Periode periode) {
        return jdbc.query("""
                        select id, kilde, status, lastet_av, lastet_at, hentet_at from bruksdata_import
                         where periode = :periode and status in ('MOTTATT', 'VALIDERT')
                         order by (status = 'VALIDERT') desc, lastet_at desc, id desc limit 1""",
                periode(periode),
                (rs, i) -> new Import(rs.getLong("id"), rs.getString("kilde"), rs.getString("status"),
                        rs.getString("lastet_av"), rs.getObject("lastet_at", OffsetDateTime.class),
                        rs.getObject("hentet_at", OffsetDateTime.class))).stream().findFirst();
    }

    private Optional<Kjoring> kjoringFor(Periode periode) {
        return jdbc.query("""
                        select id, status, bruksdata_import_id, generert_av, generert_at, godkjent_av, godkjent_at
                          from fakturakjoring
                         where periode = :periode and type = 'ORDINAER' and status <> 'FORKASTET'
                         order by versjon desc limit 1""",
                periode(periode),
                (rs, i) -> new Kjoring(rs.getLong("id"), rs.getString("status"),
                        (Long) rs.getObject("bruksdata_import_id"), rs.getString("generert_av"),
                        rs.getObject("generert_at", OffsetDateTime.class), rs.getString("godkjent_av"),
                        rs.getObject("godkjent_at", OffsetDateTime.class))).stream().findFirst();
    }

    private Optional<Eksport> eksport(long kjoringId) {
        return jdbc.query("""
                        select opprettet_av, opprettet_at from eksportfil
                         where kjoring_id = :id and type = 'LG04' order by opprettet_at desc limit 1""",
                new MapSqlParameterSource("id", kjoringId),
                (rs, i) -> new Eksport(rs.getString("opprettet_av"), rs.getObject("opprettet_at", OffsetDateTime.class)))
                .stream().findFirst();
    }

    /** Blocking findings, or warnings not yet accepted. */
    private long antallFunn(long kjoringId, boolean blokkerende) {
        String sql = blokkerende
                ? "select count(*) from kontrollfunn where kjoring_id = :id and alvorlighet = 'BLOKKERENDE'"
                : "select count(*) from kontrollfunn where kjoring_id = :id and alvorlighet = 'ADVARSEL' and akseptert_at is null";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", kjoringId), Long.class);
    }

    // ---- Progress, status sentence and the one action ------------------------------------------------------

    private static Fremdrift fremdrift(Optional<Import> imp, Optional<Kjoring> kjoring, Optional<Eksport> eksport) {
        String status = kjoring.map(Kjoring::status).orElse(null);
        boolean bruksdataKlar = kjoring.isPresent() || imp.map(Import::bekreftet).orElse(false);

        Steg bruksdata = bruksdataKlar
                ? new Steg("Bruksdata hentet", Tilstand.FERDIG, imp.map(Import::lastetAv).orElse(null),
                        imp.map(Import::tidspunkt).orElse(null))
                : new Steg("Bruksdata hentet", Tilstand.NAA, null, null);
        Steg generert = kjoring.isPresent()
                ? new Steg("Generert", Tilstand.FERDIG, kjoring.get().generertAv(), kjoring.get().generertAt())
                : new Steg("Generert", bruksdataKlar ? Tilstand.NAA : Tilstand.SENERE, null, null);
        boolean godkjent = "GODKJENT".equals(status) || "EKSPORTERT".equals(status);
        Steg kontrollert = godkjent
                ? new Steg("Kontrollert og godkjent", Tilstand.FERDIG, kjoring.get().godkjentAv(), kjoring.get().godkjentAt())
                : new Steg("Kontrollert og godkjent", "GENERERT".equals(status) ? Tilstand.NAA : Tilstand.SENERE, null, null);
        Steg eksportert = "EKSPORTERT".equals(status)
                ? new Steg("Eksportert", Tilstand.FERDIG, eksport.map(Eksport::av).orElse(null), eksport.map(Eksport::at).orElse(null))
                : new Steg("Eksportert", "GODKJENT".equals(status) ? Tilstand.NAA : Tilstand.SENERE, null, null);
        return new Fremdrift(List.of(bruksdata, generert, kontrollert, eksportert), null);
    }

    private static String status(Periode periode, Optional<Import> imp, Optional<Kjoring> kjoring,
                                 long blokkerende, long advarsler, Optional<ZonedDateTime> nesteHenting) {
        String p = periode.norskLabel();
        if (kjoring.isEmpty()) {
            if (imp.isPresent() && !imp.get().bekreftet()) {
                return "Bruksdata for " + p + " er hentet og venter på bekreftelse.";
            }
            return "Bruksdata for " + p + " er klar. Neste steg er å generere fakturagrunnlaget.";
        }
        return switch (kjoring.get().status()) {
            case "GENERERT" -> blokkerende > 0
                    ? "Fakturagrunnlaget for " + p + " har " + funn(blokkerende, "blokkerende funn", "blokkerende funn")
                      + " som må rettes før godkjenning."
                    : advarsler > 0
                    ? "Fakturagrunnlaget for " + p + " er generert og venter på godkjenning. "
                      + forstor(funn(advarsler, "advarsel", "advarsler")) + " må vurderes først."
                    : "Fakturagrunnlaget for " + p + " er generert og klart til godkjenning.";
            case "GODKJENT" -> "Fakturagrunnlaget for " + p + " er godkjent og klart til eksport.";
            default -> "Fakturagrunnlaget for " + p + " er eksportert."
                    + nesteHenting.map(t -> " Neste henting fra datavarehuset er " + Tidstekst.planlagt(t) + ".").orElse("");
        };
    }

    private static Lenke handling(Bruker bruker, Optional<Import> imp, Optional<Kjoring> kjoring,
                                  long blokkerende, long advarsler) {
        if (kjoring.isEmpty()) {
            if (imp.isPresent() && !imp.get().bekreftet()) {
                return new Lenke(bruker.forvalter() ? "Bekreft bruksdata" : "Se bruksdata", "/bruksdata/" + imp.get().id());
            }
            return bruker.forvalter() ? new Lenke("Generer kjøring", "/kjoringer") : new Lenke("Se bruksdata", "/bruksdata");
        }
        Kjoring k = kjoring.get();
        String side = "/kjoringer/" + k.id();
        return switch (k.status()) {
            case "GENERERT" -> blokkerende > 0
                    ? new Lenke("Se kontrollene", side + "/kontroller")
                    : advarsler > 0
                    ? new Lenke(bruker.godkjenner() ? "Vurder advarslene" : "Se kontrollene", side + "/kontroller")
                    : new Lenke(bruker.godkjenner() ? "Gå til godkjenning" : "Se kjøringen", side);
            case "GODKJENT" -> new Lenke(bruker.godkjenner() ? "Gå til eksport" : "Se kjøringen", side);
            default -> new Lenke("Se kjøringen", side);
        };
    }

    // ---- Key numbers and the per-product split ----------------------------------------------------------

    private List<Nokkeltall> nokkeltall(Periode periode, Optional<Import> imp, Optional<Kjoring> kjoring,
                                        ZonedDateTime naa, Optional<ZonedDateTime> nesteHenting) {
        List<Nokkeltall> fliser = new ArrayList<>();
        if (kjoring.isPresent()) {
            long id = kjoring.get().id();
            var sum = jdbc.queryForMap("select coalesce(sum(sum_belop), 0) as belop, count(*) as antall from faktura where kjoring_id = :id",
                    new MapSqlParameterSource("id", id));
            BigDecimal belop = (BigDecimal) sum.get("belop");
            long antall = ((Number) sum.get("antall")).longValue();
            long underTerskel = jdbc.queryForObject("select count(*) from faktura_under_terskel where kjoring_id = :id",
                    new MapSqlParameterSource("id", id), Long.class);
            List<String> endring = endringMotForrige(periode, belop).map(List::of).orElse(List.of());
            fliser.add(new Nokkeltall("Fakturagrunnlag", tall.belop(belop) + " kr", endring));
            fliser.add(new Nokkeltall("Fakturaer", tall.antall(antall),
                    List.of(underTerskel == 0 ? "Ingen under terskel" : tall.antall(underTerskel) + " under terskel")));
        } else {
            fliser.add(new Nokkeltall("Fakturagrunnlag", "—", List.of("Ikke generert ennå")));
        }

        Long importId = kjoring.map(Kjoring::bruksdataImportId).orElse(imp.map(Import::id).orElse(null));
        if (importId != null) {
            BigDecimal volum = jdbc.queryForObject("""
                    select coalesce(sum(antall), 0) from bruksdata where import_id = :id and type = 'BRUKSVOLUM'""",
                    new MapSqlParameterSource("id", importId), BigDecimal.class);
            fliser.add(new Nokkeltall("Transaksjoner", tall.antall(volum), List.of("Bruksvolum " + periode.norskLabel())));
        }

        OffsetDateTime sistHentet = jdbc.queryForObject(
                "select max(hentet_at) from bruksdata_import where kilde = 'DWH'", new MapSqlParameterSource(), OffsetDateTime.class);
        List<String> detaljer = new ArrayList<>();
        if (sistHentet != null) {
            detaljer.add(Tidstekst.datoTid(sistHentet));
        } else {
            detaljer.add("Ingen henting fra datavarehuset ennå");
        }
        nesteHenting.ifPresent(t -> detaljer.add("Neste: " + Tidstekst.planlagt(t)));
        fliser.add(new Nokkeltall("Sist hentet fra datavarehuset",
                sistHentet == null ? "Aldri" : forstor(Tidstekst.siden(sistHentet, naa)), detaljer));
        return fliser;
    }

    /** «+6,2 % mot desember 2026» against the previous period's newest exported ordinary run, if any. */
    private Optional<String> endringMotForrige(Periode periode, BigDecimal belop) {
        Periode forrige = periode.forrige();
        List<BigDecimal> tidligere = jdbc.query("""
                        select coalesce(sum(f.sum_belop), 0) from fakturakjoring k left join faktura f on f.kjoring_id = k.id
                         where k.periode = :periode and k.type = 'ORDINAER' and k.status = 'EKSPORTERT'
                         group by k.id, k.versjon order by k.versjon desc limit 1""",
                periode(forrige), (rs, i) -> rs.getBigDecimal(1));
        if (tidligere.isEmpty() || tidligere.get(0).signum() == 0) {
            return Optional.empty();
        }
        BigDecimal prosent = belop.subtract(tidligere.get(0))
                .multiply(BigDecimal.valueOf(100))
                .divide(tidligere.get(0), 1, RoundingMode.HALF_UP);
        String tekst = Norsktall.format("+#,##0.0;-#,##0.0").format(prosent);
        return Optional.of(tekst + " % mot " + forrige.norskLabel());
    }

    private Produktfordeling produkter(Optional<Import> imp, Optional<Kjoring> kjoring) {
        record Rad(String navn, BigDecimal verdi) {
        }
        List<Rad> rader;
        String tittel;
        String enhet;
        if (kjoring.isPresent()) {
            rader = jdbc.query("""
                            select p.navn, coalesce(sum(l.belop), 0) as verdi
                              from produkt p
                              left join (fakturalinje l join faktura f on f.id = l.faktura_id and f.kjoring_id = :id)
                                     on l.produkt_id = p.id
                             where p.aktiv
                             group by p.id, p.navn order by p.id""",
                    new MapSqlParameterSource("id", kjoring.get().id()),
                    (rs, i) -> new Rad(rs.getString("navn"), rs.getBigDecimal("verdi")));
            tittel = "Fakturagrunnlag per produkt";
            enhet = "kr";
        } else if (imp.isPresent()) {
            rader = jdbc.query("""
                            select p.navn, coalesce(sum(b.antall), 0) as verdi
                              from produkt p
                              left join bruksdata b on b.produkt_id = p.id and b.import_id = :id and b.type = 'BRUKSVOLUM'
                             where p.aktiv
                             group by p.id, p.navn order by p.id""",
                    new MapSqlParameterSource("id", imp.get().id()),
                    (rs, i) -> new Rad(rs.getString("navn"), rs.getBigDecimal("verdi")));
            tittel = "Bruksvolum per produkt";
            enhet = "transaksjoner";
        } else {
            return null;
        }
        BigDecimal storst = rader.stream().map(Rad::verdi).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (storst.signum() <= 0) {
            return null;
        }
        boolean kroner = "kr".equals(enhet);
        List<Stolpe> stolper = rader.stream()
                .map(r -> new Stolpe(r.navn(), kroner ? tall.belop(r.verdi()) : tall.antall(r.verdi()),
                        r.verdi().signum() <= 0 ? 0
                                : Math.max(1, r.verdi().multiply(BigDecimal.valueOf(100)).divide(storst, 0, RoundingMode.HALF_UP).intValue())))
                .toList();
        return new Produktfordeling(tittel, enhet, stolper);
    }

    // ---- Attention list --------------------------------------------------------------------------------

    private List<Varsel> oppmerksomhet(Periode periode) {
        List<Varsel> feil = new ArrayList<>();
        List<Varsel> advarsler = new ArrayList<>();
        List<Varsel> info = new ArrayList<>();

        jdbc.query("""
                        select k.id, k.periode, k.type,
                               count(f.id) filter (where f.alvorlighet = 'BLOKKERENDE') as blokkerende,
                               count(f.id) filter (where f.alvorlighet = 'ADVARSEL' and f.akseptert_at is null) as advarsler
                          from fakturakjoring k left join kontrollfunn f on f.kjoring_id = k.id
                         where k.status in ('GENERERT', 'GODKJENT')
                         group by k.id, k.periode, k.type order by k.periode, k.id""",
                new MapSqlParameterSource(), rs -> {
                    long id = rs.getLong("id");
                    String p = new Periode(rs.getObject("periode", LocalDate.class)).norskLabel();
                    boolean korrigering = "KORRIGERING".equals(rs.getString("type"));
                    String hva = (korrigering ? "korrigeringen" : "kjøringen") + " for " + p;
                    long b = rs.getLong("blokkerende");
                    long a = rs.getLong("advarsler");
                    if (b > 0) {
                        feil.add(new Varsel("feil", forstor(funn(b, "blokkerende funn", "blokkerende funn")) + " i " + hva,
                                "/kjoringer/" + id + "/kontroller"));
                    }
                    if (a > 0) {
                        advarsler.add(new Varsel("advarsel", forstor(funn(a, "advarsel", "advarsler")) + " ikke vurdert i " + hva,
                                "/kjoringer/" + id + "/kontroller"));
                    }
                    if (korrigering) {
                        info.add(new Varsel("info", "Korrigeringen for " + p + " er ikke fullført", "/kjoringer/" + id));
                    }
                });

        jdbc.query("select id, periode from bruksdata_sammenligning where status = 'AAPEN' order by periode",
                new MapSqlParameterSource(), rs -> {
                    String p = new Periode(rs.getObject("periode", LocalDate.class)).norskLabel();
                    advarsler.add(new Varsel("advarsel", "Ny bruksdata for " + p + " avviker fra det som er fakturert",
                            "/bruksdata/sammenligninger/" + rs.getLong("id")));
                });

        if (periode != null) {
            jdbc.query("""
                            select periode, bool_or(status = 'VALIDERT') as bekreftet from bruksdata_import
                             where periode > :periode and status in ('MOTTATT', 'VALIDERT')
                             group by periode order by periode""",
                    periode(periode), rs -> {
                        String p = new Periode(rs.getObject("periode", LocalDate.class)).norskLabel();
                        info.add(new Varsel("info", rs.getBoolean("bekreftet")
                                ? "Bruksdata for " + p + " er hentet"
                                : "Bruksdata for " + p + " venter på bekreftelse", "/bruksdata"));
                    });
        }

        long utenKontering = jdbc.queryForObject("""
                select count(*) from produkt
                 where aktiv and (konto is null or btrim(konto) = '' or artikkel_id is null)""",
                new MapSqlParameterSource(), Long.class);
        if (utenKontering > 0) {
            advarsler.add(new Varsel("advarsel", utenKontering == 1
                    ? "1 produkt mangler konto eller artikkel-id"
                    : utenKontering + " produkter mangler konto eller artikkel-id", "/produkter"));
        }

        if (periode == null) {
            if (!harAktivPrisversjon(null)) {
                advarsler.add(new Varsel("advarsel", "Ingen prisversjon er aktiv", "/prisversjoner"));
            }
        } else if (!harAktivPrisversjon(periode)) {
            feil.add(new Varsel("feil", "Ingen aktiv prisversjon dekker " + periode.norskLabel(), "/prisversjoner"));
        } else {
            Periode neste = new Periode(periode.førsteDag().plusMonths(1));
            if (!harAktivPrisversjon(neste)) {
                advarsler.add(new Varsel("advarsel", "Ingen aktiv prisversjon dekker " + neste.norskLabel() + " ennå",
                        "/prisversjoner"));
            }
        }

        List<Varsel> alle = new ArrayList<>(feil);
        alle.addAll(advarsler);
        alle.addAll(info);
        return alle;
    }

    /**
     * Whether an active price version covers the period — the rule of {@code Prisdekning}: both dates
     * inclusive against the period's first day, an empty «til» open-ended. {@code null} asks for any active one.
     */
    private boolean harAktivPrisversjon(Periode periode) {
        if (periode == null) {
            return jdbc.queryForObject("select exists(select 1 from prisversjon where status = 'AKTIV')",
                    new MapSqlParameterSource(), Boolean.class);
        }
        return jdbc.queryForObject("""
                select exists(select 1 from prisversjon
                               where status = 'AKTIV' and gyldig_fra <= :periode
                                 and (gyldig_til is null or gyldig_til >= :periode))""",
                periode(periode), Boolean.class);
    }

    // ---- Recent activity --------------------------------------------------------------------------------

    private List<Aktivitet> aktivitet(ZonedDateTime naa) {
        return jdbc.query("""
                        select tidspunkt, bruker, handling, entitet, detaljer::text as detaljer from hendelseslogg
                         where handling <> 'LASTET_NED'
                         order by tidspunkt desc, id desc limit :antall""",
                new MapSqlParameterSource("antall", ANTALL_AKTIVITETER),
                (rs, i) -> {
                    OffsetDateTime t = rs.getObject("tidspunkt", OffsetDateTime.class);
                    String tekst = Aktivitetstekst.av(rs.getString("handling"), rs.getString("entitet"),
                            Jsonb.of(rs.getString("detaljer")));
                    return new Aktivitet(
                            // Follows the user's name in the sentence: «kari genererte kjøring …».
                            tekst.isEmpty() ? tekst : Character.toLowerCase(tekst.charAt(0)) + tekst.substring(1),
                            Aktivitetstekst.bruker(rs.getString("bruker")),
                            Tidstekst.siden(t, naa),
                            t.toString());
                });
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    /** The next scheduled DWH staging, only when the DWH and its schedule are both enabled. */
    private Optional<ZonedDateTime> nesteDwhHenting(ZonedDateTime naa) {
        if (dwh == null || !dwh.enabled() || dwh.planlegging() == null || !dwh.planlegging().enabled()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(CronExpression.parse(dwh.planlegging().cron()).next(naa.withZoneSameInstant(Tidstekst.OSLO)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static MapSqlParameterSource periode(Periode periode) {
        return new MapSqlParameterSource("periode", Date.valueOf(periode.førsteDag()));
    }

    private static String funn(long antall, String entall, String flertall) {
        return antall + " " + (antall == 1 ? entall : flertall);
    }

    private static String forstor(String tekst) {
        return tekst.isEmpty() ? tekst : Character.toUpperCase(tekst.charAt(0)) + tekst.substring(1);
    }
}
