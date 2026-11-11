package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.web.Forside.Varsel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The start page dashboard (specs/015): the period rule, the status sentence and action, the attention list,
 * and that both an empty and a filled database render. Each test starts from no runs, imports or audit rows
 * (truncated inside the test transaction, so it is rolled back like everything else).
 */
@AutoConfigureMockMvc
@WithMockUser(username = "kari.nordmann@digdir.no", roles = {"LESER", "FORVALTER", "GODKJENNER"})
class ForsideIT extends IntegrationTest {

    private static final Periode DES = Periode.av(2026, 12);
    private static final Periode JAN = Periode.av(2027, 1);
    private static final Periode FEB = Periode.av(2027, 2);
    private static final ForsideService.Bruker ALLE_ROLLER = new ForsideService.Bruker("Kari", true, true);
    private static final ForsideService.Bruker LESER = new ForsideService.Bruker(null, false, false);

    @Autowired MockMvc mvc;
    @Autowired ForsideService forside;
    @Autowired JdbcTemplate jdbc;

    private long prisversjonId;

    @BeforeEach
    void tomStart() {
        jdbc.execute("truncate fakturakjoring, bruksdata_import, bruksdata_sammenligning, hendelseslogg cascade");
        prisversjonId = jdbc.queryForObject("""
                insert into prisversjon (navn, gyldig_fra, status, terskelverdi)
                values ('Prisliste test ' || clock_timestamp(), date '2026-01-01', 'AKTIV', 40) returning id""", Long.class);
    }

    // ---- Empty database ----------------------------------------------------------------------------

    @Test
    void tomDatabaseGirVelkomstOgForsteSteg() throws Exception {
        assertThat(forside.dashbordperiode()).isEmpty();
        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.periode()).isNull();
        assertThat(f.fremdrift()).isNull();
        assertThat(f.nokkeltall()).isEmpty();
        assertThat(f.handling().href()).isEqualTo("/bruksdata");
        assertThat(f.hilsen()).matches("God (morgen|dag|kveld), Kari");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hent bruksdata")))
                .andExpect(content().string(containsString("Ny i forsystem?")))
                .andExpect(content().string(containsString(", Kari</h1>")))
                .andExpect(content().string(not(containsString("Nøkkeltall"))));
    }

    @Test
    void forsidenGjentarIkkeMenyenSomKort() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Årlige prislister"))));
    }

    @Test
    void laeringErEnPlassholderForAlle() throws Exception {
        mvc.perform(get("/laering"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kommer snart")));
    }

    @Test
    void forsidenSkriverIkkeTilHendelsesloggen() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from hendelseslogg", Long.class)).isZero();
    }

    // ---- The period rule -----------------------------------------------------------------------------

    @Test
    void bekreftetImportUtenKjoringGirGenererSomNesteSteg() {
        nyImport(JAN, "VALIDERT");

        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.periode()).isEqualTo("januar 2027");
        assertThat(f.status()).contains("Bruksdata for januar 2027 er klar");
        assertThat(f.handling()).isEqualTo(new Forside.Lenke("Generer kjøring", "/kjoringer"));
        assertThat(f.fremdrift().steg()).extracting(Fremdrift.Steg::tilstand).containsExactly(
                Fremdrift.Tilstand.FERDIG, Fremdrift.Tilstand.NAA, Fremdrift.Tilstand.SENERE, Fremdrift.Tilstand.SENERE);
        assertThat(forside.les(LESER).handling().tekst()).isEqualTo("Se bruksdata");
    }

    @Test
    void ubekreftetDwhImportVenterPaaBekreftelse() {
        long imp = nyDwhImport(JAN, "MOTTATT");

        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.status()).isEqualTo("Bruksdata for januar 2027 er hentet og venter på bekreftelse.");
        assertThat(f.handling()).isEqualTo(new Forside.Lenke("Bekreft bruksdata", "/bruksdata/" + imp));
        assertThat(f.fremdrift().steg().get(0).tilstand()).isEqualTo(Fremdrift.Tilstand.NAA);
        assertThat(f.nokkeltall()).anySatisfy(n -> assertThat(n.etikett()).isEqualTo("Sist hentet fra datavarehuset"));
    }

    @Test
    void generertKjoringMedAdvarslerVenterPaaVurdering() {
        long imp = nyImport(JAN, "VALIDERT");
        long k = nyKjoring(JAN, "GENERERT", "ORDINAER", 1, imp);
        funn(k, "ADVARSEL", false);
        funn(k, "ADVARSEL", false);
        funn(k, "ADVARSEL", true);

        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.status()).isEqualTo(
                "Fakturagrunnlaget for januar 2027 er generert og venter på godkjenning. 2 advarsler må vurderes først.");
        assertThat(f.handling()).isEqualTo(new Forside.Lenke("Vurder advarslene", "/kjoringer/" + k + "/kontroller"));
        assertThat(f.oppmerksomhet()).extracting(Varsel::tekst)
                .contains("2 advarsler ikke vurdert i kjøringen for januar 2027");
    }

    @Test
    void uferdigKjoringHolderPeriodenSelvOmNyBruksdataErHentet() {
        long imp = nyImport(JAN, "VALIDERT");
        nyKjoring(JAN, "GODKJENT", "ORDINAER", 1, imp);
        nyImport(FEB, "VALIDERT");

        assertThat(forside.dashbordperiode()).contains(JAN);
        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.status()).isEqualTo("Fakturagrunnlaget for januar 2027 er godkjent og klart til eksport.");
        assertThat(f.oppmerksomhet()).extracting(Varsel::tekst).contains("Bruksdata for februar 2027 er hentet");
    }

    @Test
    void ferdigKjoringSlipperPeriodenVidereTilNyesteData() {
        long imp = nyImport(JAN, "VALIDERT");
        nyKjoring(JAN, "EKSPORTERT", "ORDINAER", 1, imp);
        nyImport(FEB, "VALIDERT");

        assertThat(forside.dashbordperiode()).contains(FEB);
    }

    @Test
    void forkastetKjoringOgAvvistImportTellerIkke() {
        long imp = nyImport(JAN, "VALIDERT");
        nyKjoring(JAN, "EKSPORTERT", "ORDINAER", 1, imp);
        long feb = nyImport(FEB, "AVVIST");
        nyKjoring(FEB, "FORKASTET", "ORDINAER", 1, feb);

        assertThat(forside.dashbordperiode()).contains(JAN);
    }

    @Test
    void uferdigKorrigeringVelgerIkkePerioden() {
        long desImp = nyImport(DES, "VALIDERT");
        long desKjoring = nyKjoring(DES, "EKSPORTERT", "ORDINAER", 1, desImp);
        long kandidat = nyImport(DES, "KANDIDAT");
        long sammenligning = jdbc.queryForObject("""
                insert into bruksdata_sammenligning (periode, kandidat_import_id, grunnlag_kjoring_id, status,
                                                     uendrede_rader, opprettet_av)
                values (?, ?, ?, 'KJORING_OPPRETTET', 0, 'test') returning id""",
                Long.class, dato(DES), kandidat, desKjoring);
        long korrigering = jdbc.queryForObject("""
                insert into fakturakjoring (periode, status, prisversjon_id, bruksdata_import_id, generert_av, type,
                                            versjon, forrige_kjoring_id, korrigeringsaarsak, sammenligning_id,
                                            topptekst, bunntekst, var_referanse)
                values (?, 'GENERERT', ?, ?, 'test', 'KORRIGERING', 2, ?, 'Rettet bruk', ?, 't', 'b', 'v') returning id""",
                Long.class, dato(DES), prisversjonId, kandidat, desKjoring, sammenligning);
        long janImp = nyImport(JAN, "VALIDERT");
        nyKjoring(JAN, "EKSPORTERT", "ORDINAER", 1, janImp);

        assertThat(forside.dashbordperiode()).contains(JAN);
        assertThat(forside.les(ALLE_ROLLER).oppmerksomhet())
                .contains(new Varsel("info", "Korrigeringen for desember 2026 er ikke fullført", "/kjoringer/" + korrigering));
    }

    // ---- Numbers and the page ------------------------------------------------------------------------

    @Test
    void eksportertKjoringViserBelopOgEndringMotForrigePeriode() throws Exception {
        long kunde = jdbc.queryForObject("""
                insert into kunde (organisasjonsnummer, virksomhetsnavn, oppdatert_av)
                values ('987654321', 'Testetaten', 'test') returning id""", Long.class);
        long desKjoring = nyKjoring(DES, "EKSPORTERT", "ORDINAER", 1, nyImport(DES, "VALIDERT"));
        faktura(desKjoring, kunde, 1, "1000.00");
        long janImp = nyImport(JAN, "VALIDERT");
        long janKjoring = nyKjoring(JAN, "EKSPORTERT", "ORDINAER", 1, janImp);
        long fakturaId = faktura(janKjoring, kunde, 1, "1062.00");
        long melding = jdbc.queryForObject("select id from produkt where kode = 'melding'", Long.class);
        jdbc.update("insert into fakturalinje (faktura_id, produkt_id, beskrivelse, belop) values (?, ?, 'x', 1062.00)",
                fakturaId, melding);
        jdbc.update("""
                insert into bruksdata (import_id, periode, organisasjonsnummer, produkt_id, type, antall)
                values (?, ?, '987654321', ?, 'BRUKSVOLUM', 48000)""", janImp, dato(JAN), melding);

        Forside f = forside.les(ALLE_ROLLER);
        assertThat(f.status()).startsWith("Fakturagrunnlaget for januar 2027 er eksportert.");
        assertThat(f.nokkeltall().get(0)).isEqualTo(
                new Forside.Nokkeltall("Fakturagrunnlag", "1 062,00 kr", java.util.List.of("+6,2 % mot desember 2026")));
        assertThat(f.nokkeltall()).anySatisfy(n -> assertThat(n.verdi()).isEqualTo("48 000"));
        assertThat(f.produkter().tittel()).isEqualTo("Fakturagrunnlag per produkt");
        assertThat(f.produkter().stolper()).anySatisfy(s -> {
            assertThat(s.verdi()).isEqualTo("1 062,00");
            assertThat(s.prosent()).isEqualTo(100);
        });

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("januar 2027")))
                .andExpect(content().string(containsString("1 062,00 kr")))
                .andExpect(content().string(containsString("Fakturagrunnlag per produkt")));
    }

    @Test
    void ingenForrigeEksportertPeriodeGirIngenEndring() {
        long kunde = jdbc.queryForObject("""
                insert into kunde (organisasjonsnummer, virksomhetsnavn, oppdatert_av)
                values ('987654322', 'Testetaten 2', 'test') returning id""", Long.class);
        long k = nyKjoring(JAN, "GENERERT", "ORDINAER", 1, nyImport(JAN, "VALIDERT"));
        faktura(k, kunde, 1, "500.00");

        assertThat(forside.les(ALLE_ROLLER).nokkeltall().get(0).detaljer()).isEmpty();
    }

    @Test
    void manglendePrisversjonForNesteMaanedVarsles() {
        jdbc.update("update prisversjon set gyldig_til = date '2027-01-31' where id = ?", prisversjonId);
        jdbc.update("update prisversjon set status = 'ARKIVERT' where status = 'AKTIV' and id <> ?", prisversjonId);
        nyImport(JAN, "VALIDERT");

        assertThat(forside.les(ALLE_ROLLER).oppmerksomhet())
                .contains(new Varsel("advarsel", "Ingen aktiv prisversjon dekker februar 2027 ennå", "/prisversjoner"));
    }

    @Test
    void sisteAktivitetLeserSomSetninger() {
        jdbc.update("""
                insert into hendelseslogg (bruker, handling, entitet, entitet_id, detaljer)
                values ('kari', 'GENERERTE', 'FAKTURAKJORING', 1, '{"periode":"2027-01"}'::jsonb),
                       ('kari', 'LASTET_NED', 'FAKTURAKJORING', 1, null)""");

        assertThat(forside.les(ALLE_ROLLER).aktivitet())
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.bruker()).isEqualTo("kari");
                    assertThat(a.tekst()).isEqualTo("genererte kjøring for januar 2027");
                });
    }

    // ---- Helpers ------------------------------------------------------------------------------------

    private long nyImport(Periode periode, String status) {
        return jdbc.queryForObject("""
                insert into bruksdata_import (filnavn, periode, kilde, status, antall_rader, lastet_av)
                values ('test.csv', ?, 'CSV', ?, 0, 'test') returning id""", Long.class, dato(periode), status);
    }

    private long nyDwhImport(Periode periode, String status) {
        return jdbc.queryForObject("""
                insert into bruksdata_import (filnavn, periode, kilde, status, antall_rader, lastet_av,
                                              raadata_url, raadata_sha256, hentet_at)
                values ('dwh.json', ?, 'DWH', ?, 0, 'system', 'fil:///x', 'abc', now()) returning id""",
                Long.class, dato(periode), status);
    }

    private long nyKjoring(Periode periode, String status, String type, int versjon, long importId) {
        return jdbc.queryForObject("""
                insert into fakturakjoring (periode, status, prisversjon_id, bruksdata_import_id, generert_av, type,
                                            versjon, topptekst, bunntekst, var_referanse)
                values (?, ?, ?, ?, 'test', ?, ?, 't', 'b', 'v') returning id""",
                Long.class, dato(periode), status, prisversjonId, importId, type, versjon);
    }

    private void funn(long kjoringId, String alvorlighet, boolean akseptert) {
        jdbc.update("""
                insert into kontrollfunn (kjoring_id, alvorlighet, kode, melding, akseptert_av, akseptert_at)
                values (?, ?, 'STORT_AVVIK', 'm', ?, ?)""",
                kjoringId, alvorlighet, akseptert ? "test" : null, akseptert ? java.sql.Timestamp.valueOf("2027-02-01 10:00:00") : null);
    }

    private long faktura(long kjoringId, long kundeId, int ordreNr, String belop) {
        return jdbc.queryForObject("""
                insert into faktura (kjoring_id, kunde_id, faktura_uuid, ordre_nr, kundenummer, fakturamottaker_orgnr, sum_belop)
                values (?, ?, gen_random_uuid(), ?, '1', '987654321', ?::numeric) returning id""",
                Long.class, kjoringId, kundeId, ordreNr, belop);
    }

    private static Date dato(Periode periode) {
        return Date.valueOf(periode.førsteDag());
    }
}
