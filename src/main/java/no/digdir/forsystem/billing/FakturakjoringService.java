package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.common.UuidV7;
import no.digdir.forsystem.registry.Kunde;
import no.digdir.forsystem.registry.KundeReferanse;
import no.digdir.forsystem.registry.KundeReferanseRepository;
import no.digdir.forsystem.registry.KundeRepository;
import no.digdir.forsystem.registry.KundenummerRegel;
import no.digdir.forsystem.registry.KundenummerRegelRepository;
import no.digdir.forsystem.registry.Pris;
import no.digdir.forsystem.registry.PrisRepository;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import no.digdir.forsystem.registry.PrisversjonStatus;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.BruksdataImportRepository;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataEndringRepository;
import no.digdir.forsystem.usage.BruksdataSammenligning;
import no.digdir.forsystem.usage.BruksdataSammenligningRepository;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Importstatus;
import no.digdir.forsystem.usage.Sammenligningstatus;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The generation engine, controls and approval state machine (docs/04 Phase 3). Deterministic:
 * for the same inputs it produces identical invoices and lines, ordered by (kunde orgnr,
 * kundenummer) for invoices and (produkt kode, type) for lines (docs/05). The billing period is
 * always an explicit parameter.
 */
@Service
public class FakturakjoringService {

    /** Largest amount {@code faktura.sum_belop numeric(12,2)} can hold. */
    static final BigDecimal MAKS_FAKTURABELOP = new BigDecimal("9999999999.99");

    private static final BigDecimal AVVIK_GRENSE = new BigDecimal("0.30");

    private final FakturakjoringRepository kjoringer;
    private final FakturaRepository fakturaer;
    private final FakturalinjeRepository linjer;
    private final KontrollfunnRepository funn;
    private final EksportfilRepository eksportfiler;
    private final KontrollfunnBruksdataRepository funnBruksdata;
    private final FakturaUnderTerskelRepository underTerskel;
    private final ProduktRepository produkter;
    private final PrisRepository priser;
    private final PrisversjonRepository prisversjoner;
    private final KundeRepository kunder;
    private final KundeReferanseRepository referanser;
    private final KundenummerRegelRepository regler;
    private final UsageImportService usage;
    private final BruksdataImportRepository usageImports;
    private final BruksdataSammenligningRepository sammenligninger;
    private final BruksdataEndringRepository endringer;
    private final KorrigeringskjoringEndringRepository valgteEndringer;
    private final KjoringVersjonService versjoner;
    private final AuditService audit;
    private final BrukerContext brukerContext;

    FakturakjoringService(FakturakjoringRepository kjoringer, FakturaRepository fakturaer,
                          FakturalinjeRepository linjer, KontrollfunnRepository funn,
                          EksportfilRepository eksportfiler, KontrollfunnBruksdataRepository funnBruksdata,
                          FakturaUnderTerskelRepository underTerskel,
                          ProduktRepository produkter, PrisRepository priser,
                          PrisversjonRepository prisversjoner, KundeRepository kunder,
                          KundeReferanseRepository referanser, KundenummerRegelRepository regler,
                          UsageImportService usage, BruksdataImportRepository usageImports,
                          BruksdataSammenligningRepository sammenligninger,
                          BruksdataEndringRepository endringer,
                          KorrigeringskjoringEndringRepository valgteEndringer,
                          KjoringVersjonService versjoner,
                          AuditService audit, BrukerContext brukerContext) {
        this.kjoringer = kjoringer;
        this.fakturaer = fakturaer;
        this.linjer = linjer;
        this.funn = funn;
        this.eksportfiler = eksportfiler;
        this.funnBruksdata = funnBruksdata;
        this.underTerskel = underTerskel;
        this.produkter = produkter;
        this.priser = priser;
        this.prisversjoner = prisversjoner;
        this.kunder = kunder;
        this.referanser = referanser;
        this.regler = regler;
        this.usage = usage;
        this.usageImports = usageImports;
        this.sammenligninger = sammenligninger;
        this.endringer = endringer;
        this.valgteEndringer = valgteEndringer;
        this.versjoner = versjoner;
        this.audit = audit;
        this.brukerContext = brukerContext;
    }

    /** Suffix for a customer name read from the registry because the invoice predates the frozen name. */
    public static final String NAAVAERENDE_NAVN = " (nåværende navn)";

    // ---- Generation -------------------------------------------------------------------------

    @Transactional
    public Fakturakjoring generer(Periode periode) {
        Prisversjon versjon = aktivVersjonFor(periode);
        versjoner.laas(periode.førsteDag());
        if (kjoringer.existsByPeriodeAndStatusNot(periode.førsteDag(), Kjoringstatus.FORKASTET.name())) {
            throw new Regelbrudd("Det finnes allerede en aktiv kjøring for " + periode
                    + ". Forkast den før du genererer på nytt.");
        }
        Long importId = usage.gjeldendeImport(periode).map(BruksdataImport::id).orElse(null);
        List<Bruksdata> bruk = usage.bruksdataFor(periode);
        if (importId == null || bruk.isEmpty()) {
            throw new Regelbrudd("Ingen bruksdata for " + periode + " — importer bruk først.");
        }

        int kjoringVersjon = versjoner.neste(periode.førsteDag());
        Long forrigeKjoringId = kjoringer.findFirstByPeriodeOrderByVersjonDesc(periode.førsteDag())
                .map(Fakturakjoring::id).orElse(null);

        Fakturatekster tekster = Fakturatekster.standard(periode);
        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, periode.førsteDag(),
                Kjoringstatus.GENERERT.name(), versjon.id(), importId, brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), null, null, null, Kjoringstype.ORDINAER.name(), kjoringVersjon,
                forrigeKjoringId, null, null, tekster.topptekst(), tekster.bunntekst(), tekster.varReferanse()));

        var ctx = new Byggekontekst(periode, versjon, kjoring.id(), false);
        bygg(ctx, bruk);
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(),
                Map.of("periode", periode.toString(), "antallFakturaer", ctx.antallFakturaer));
        return kjoring;
    }

    /** Generate a draft containing only the explicitly selected signed differences. */
    @Transactional
    public Fakturakjoring genererKorrigering(Long sammenligningId, List<Long> endringIder, String aarsak) {
        if (aarsak == null || aarsak.isBlank()) {
            throw new Regelbrudd("Skriv hvorfor korrigeringen opprettes");
        }
        List<Long> unikeIder = endringIder == null ? List.of() : endringIder.stream().distinct().toList();
        if (unikeIder.isEmpty()) {
            throw new Regelbrudd("Velg minst én endring som skal være med i korrigeringen");
        }
        BruksdataSammenligning foerLaas = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        versjoner.laas(foerLaas.periode());
        BruksdataSammenligning sammenligning = sammenligninger.findById(sammenligningId)
                .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + sammenligningId));
        if (!Sammenligningstatus.AAPEN.name().equals(sammenligning.status())) {
            throw new Regelbrudd("Sammenligningen er ikke lenger åpen. Sammenlign på nytt.");
        }
        Fakturakjoring grunnlag = hent(sammenligning.grunnlagKjoringId());
        Optional<Fakturakjoring> sisteEksporterte = kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(
                sammenligning.periode(), Kjoringstatus.EKSPORTERT.name());
        if (sisteEksporterte.isEmpty() || !sisteEksporterte.get().id().equals(grunnlag.id())) {
            throw new Regelbrudd("Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt.");
        }
        if (kjoringer.existsByPeriodeAndStatusIn(sammenligning.periode(),
                List.of(Kjoringstatus.GENERERT.name(), Kjoringstatus.GODKJENT.name()))) {
            throw new Regelbrudd("Det finnes allerede en pågående kjøring for "
                    + new Periode(sammenligning.periode()).norskLabel());
        }

        List<BruksdataEndring> valgte = endringer.findAllById(unikeIder);
        if (valgte.size() != unikeIder.size()
                || valgte.stream().anyMatch(e -> !sammenligningId.equals(e.sammenligningId())
                        || e.avvistAt() != null)) {
            throw new Regelbrudd("En eller flere valgte endringer tilhører ikke den åpne sammenligningen");
        }
        BruksdataImport kandidat = usageImports.findById(sammenligning.kandidatImportId())
                .orElseThrow(() -> new Regelbrudd("Fant ikke kandidatimport " + sammenligning.kandidatImportId()));
        Prisversjon prisversjon = prisversjoner.findById(grunnlag.prisversjonId())
                .orElseThrow(() -> new Regelbrudd("Fant ikke prisversjonen fra " + grunnlag.versjonLabel()));
        int versjon = versjoner.neste(sammenligning.periode());
        Long forrigeId = kjoringer.findFirstByPeriodeOrderByVersjonDesc(sammenligning.periode())
                .map(Fakturakjoring::id).orElse(grunnlag.id());
        String bruker = brukerContext.naavaerendeBruker();
        OffsetDateTime naa = OffsetDateTime.now();
        Fakturatekster tekster = Fakturatekster.standard(new Periode(sammenligning.periode()));
        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, sammenligning.periode(),
                Kjoringstatus.GENERERT.name(), prisversjon.id(), kandidat.id(), bruker, naa,
                null, null, null, Kjoringstype.KORRIGERING.name(), versjon, forrigeId,
                aarsak.trim(), sammenligning.id(), tekster.topptekst(), tekster.bunntekst(),
                tekster.varReferanse()));
        valgteEndringer.lagre(kjoring.id(), unikeIder, bruker, naa);

        List<Bruksdata> differanser = valgte.stream().map(e -> new Bruksdata(
                e.id(), kandidat.id(), sammenligning.periode(), e.organisasjonsnummer(), e.produktId(),
                e.type(), e.deltaAntall(), e.deltaBelop())).toList();
        var ctx = new Byggekontekst(new Periode(sammenligning.periode()), prisversjon, kjoring.id(), true);
        bygg(ctx, differanser);
        List<Faktura> korrigeringsfakturaer = fakturaer.findByKjoringIdOrderByOrdreNr(kjoring.id());
        if (!korrigeringsfakturaer.isEmpty() && korrigeringsfakturaer.stream()
                .flatMap(f -> linjer.findByFakturaIdOrderById(f.id()).stream())
                .allMatch(l -> l.belop().compareTo(BigDecimal.ZERO) == 0)) {
            throw new Regelbrudd("Alle valgte korreksjoner avrundes til 0 kroner. "
                    + "Kontroller differansene og prisgrunnlaget.");
        }

        sammenligninger.save(sammenligning.medStatus(Sammenligningstatus.KJORING_OPPRETTET.name(), null));
        usageImports.save(kandidat.medStatus(Importstatus.BEHANDLET.name()));
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(), Map.of(
                "periode", new Periode(sammenligning.periode()).toString(),
                "versjon", versjon, "type", Kjoringstype.KORRIGERING.name(),
                "sammenligningId", sammenligning.id(), "valgteEndringer", valgte.size(),
                "aarsak", aarsak.trim(), "antallFakturaer", ctx.antallFakturaer));
        return kjoring;
    }

    private void bygg(Byggekontekst ctx, List<Bruksdata> bruk) {
        Map<Long, Produkt> produktById = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktById.put(p.id(), p));
        Map<Long, Pris> prisByProdukt = new LinkedHashMap<>();
        priser.findByPrisversjonId(ctx.versjon.id()).forEach(pr -> prisByProdukt.put(pr.produktId(), pr));

        // Preload customers, rules and references once (maps), so per-row work is in-memory rather
        // than a query per usage row — keeps generation fast at scale (docs/04 Phase 5 load sanity).
        Map<String, Kunde> kundeByOrgnr = new LinkedHashMap<>();
        kunder.findAll().forEach(k -> kundeByOrgnr.put(k.organisasjonsnummer(), k));
        Map<Long, List<KundenummerRegel>> reglerByKunde = new LinkedHashMap<>();
        regler.findAll().forEach(r -> reglerByKunde.computeIfAbsent(r.kundeId(), x -> new ArrayList<>()).add(r));
        Map<Long, List<KundeReferanse>> referanserByKunde = new LinkedHashMap<>();
        referanser.findAll().forEach(r -> referanserByKunde.computeIfAbsent(r.kundeId(), x -> new ArrayList<>()).add(r));

        List<Kontrollfunn> funnListe = new ArrayList<>();
        Map<Gruppe, Gruppedata> grupper = new LinkedHashMap<>();
        // Skipped usage rows per finding key, so each can be linked to the finding that kept it off the
        // invoice (specs/002 D2): orgnr → MANGLER_KUNDE, orgnr/kode → MANGLER_KUNDENUMMER, kode → MANGLER_PRIS.
        var orgnrUtenKunde = new LinkedHashMap<String, List<Long>>();
        var manglerKundenummer = new LinkedHashMap<String, List<Long>>();
        var manglerPris = new LinkedHashMap<String, List<Long>>();
        var manglerKontering = new java.util.LinkedHashSet<String>();
        var kunderMedBruk = new LinkedHashMap<Long, Kunde>();

        // Deterministic processing order → deterministic "first" choices and grouping.
        List<Bruksdata> sortert = new ArrayList<>(bruk);
        sortert.sort(Comparator
                .comparing(Bruksdata::organisasjonsnummer)
                .thenComparing(b -> kode(produktById, b.produktId()))
                .thenComparing(Bruksdata::type)
                .thenComparing(b -> b.id() == null ? 0L : b.id()));

        for (Bruksdata b : sortert) {
            Produkt p = produktById.get(b.produktId());
            Kunde k = kundeByOrgnr.get(b.organisasjonsnummer());
            if (k == null) {
                orgnrUtenKunde.computeIfAbsent(b.organisasjonsnummer(), x -> new ArrayList<>()).add(b.id());
                continue;
            }
            kunderMedBruk.putIfAbsent(k.id(), k);
            if (p.konto() == null) {
                manglerKontering.add(p.kode());
            }
            KundenummerRegel regel = velgRegel(
                    reglerByKunde.getOrDefault(k.id(), List.of()), p.id());
            if (regel == null) {
                manglerKundenummer.computeIfAbsent(k.organisasjonsnummer() + "/" + p.kode(), x -> new ArrayList<>())
                        .add(b.id());
                continue;
            }

            BigDecimal antall = null;
            BigDecimal enhetspris = null;
            Long prisId = null;
            BigDecimal belop;
            String beskrivelse;
            if (Bruksdatatype.valueOf(b.type()).erVolum()) {
                Pris pris = prisByProdukt.get(p.id());
                if (pris == null) {
                    manglerPris.computeIfAbsent(p.kode(), x -> new ArrayList<>()).add(b.id());
                    continue;
                }
                antall = b.antall() == null ? BigDecimal.ZERO : b.antall();
                enhetspris = pris.enhetspris();
                prisId = pris.id();
                belop = antall.multiply(enhetspris).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = (ctx.korrigering ? "Korrigering av " : "Bruk av ")
                        + p.navn() + " " + ctx.periode.norskLabel();
            } else {
                belop = (b.belop() == null ? BigDecimal.ZERO : b.belop()).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = beskrivKostnad(b.type(), p, ctx.periode);
            }

            KundeReferanse ref = velgReferanse(
                    referanserByKunde.getOrDefault(k.id(), List.of()), p.id());
            var linje = new LinjeUtkast(p.id(), p.kode(), b.type(), beskrivelse, antall, enhetspris, belop,
                    prisId, ctx.korrigering ? null : b.id(), ctx.korrigering ? b.id() : null, regel.servicekode(),
                    ref == null ? null : ref.fakturareferanse(),
                    ref == null ? null : ref.bestillingsnummer());

            Gruppe g = new Gruppe(k.id(), regel.kundenummer());
            grupper.computeIfAbsent(g, x -> new Gruppedata(k, regel.tilleggstekst())).linjer.add(linje);
        }

        // BLOKKERENDE findings.
        // Each finding that skipped rows remembers them by its index in funnListe until it is saved.
        Map<Integer, List<Long>> utelattePerFunn = new LinkedHashMap<>();
        orgnrUtenKunde.forEach((orgnr, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KUNDE,
                    "Bruk for organisasjonsnummer " + orgnr + " har ingen registrert kunde"));
        });
        manglerKundenummer.forEach((kp, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KUNDENUMMER,
                    "Mangler kundenummerregel for " + kp));
        });
        manglerPris.forEach((kode, ider) -> {
            utelattePerFunn.put(funnListe.size(), ider);
            funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_PRIS,
                    "Produkt " + kode + " mangler pris i aktiv prisversjon"));
        });
        manglerKontering.forEach(kode -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KONTERING,
                "Produkt " + kode + " mangler kontering (OQ-2)")));
        kunderMedBruk.values().forEach(k -> {
            if (!"AKTIV".equals(k.avtalestatus())) {
                funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.INAKTIV_AVTALE,
                        "Kunde " + k.virksomhetsnavn() + " har avtalestatus " + k.avtalestatus()));
            }
        });

        // Completeness (specs/003 D5, BF1 T10): an active customer without a single usage row may be an
        // expected tjenesteeier missing from the basis. A warning, not a blocker — a month without
        // billable usage can be legitimate, and Økonomi decides.
        if (!ctx.korrigering) {
            Set<String> orgnrMedBruk = bruk.stream().map(Bruksdata::organisasjonsnummer).collect(Collectors.toSet());
            kundeByOrgnr.values().stream()
                    .filter(k -> "AKTIV".equals(k.avtalestatus()) && !orgnrMedBruk.contains(k.organisasjonsnummer()))
                    .sorted(Comparator.comparing(Kunde::organisasjonsnummer))
                    .forEach(k -> funnListe.add(advarsel(ctx.kjoringId, null, Kontrollkode.AKTIV_KUNDE_UTEN_BRUK,
                            "Kunde " + k.virksomhetsnavn() + " (" + k.organisasjonsnummer()
                                    + ") har aktiv avtale, men ingen bruk i perioden")));
        }

        // Threshold (specs/011, OQ-22): a customer whose total over all its would-be invoices is not over the
        // price version's threshold gets no invoice. Its groups leave `grupper` before any invoice is made, so
        // nothing downstream (LG04, PDF, sums) sees them; they are kept as faktura_under_terskel rows and
        // explained by one UNDER_TERSKEL finding linked to their usage. Correction runs carry signed
        // differences against an exported run and are exempt.
        BigDecimal terskel = ctx.versjon.terskelverdi();
        Map<Long, BigDecimal> kundesum = new LinkedHashMap<>();
        Map<Gruppe, Gruppedata> underTerskelGrupper = new LinkedHashMap<>();
        Integer underTerskelFunn = null;
        if (!ctx.korrigering && terskel != null) {
            grupper.forEach((g, data) -> kundesum.merge(g.kundeId(), sum(data.linjer), BigDecimal::add));
            grupper.keySet().stream()
                    .filter(g -> kundesum.get(g.kundeId()).compareTo(terskel) <= 0)
                    .sorted(Comparator.comparing((Gruppe g) -> grupper.get(g).kunde.organisasjonsnummer())
                            .thenComparing(Gruppe::kundenummer))
                    .toList()
                    .forEach(g -> underTerskelGrupper.put(g, grupper.remove(g)));
            if (!underTerskelGrupper.isEmpty()) {
                List<Long> kunder = underTerskelGrupper.keySet().stream().map(Gruppe::kundeId).distinct().toList();
                BigDecimal total = kunder.stream().map(kundesum::get).reduce(BigDecimal.ZERO, BigDecimal::add);
                underTerskelFunn = funnListe.size();
                utelattePerFunn.put(underTerskelFunn, underTerskelGrupper.values().stream()
                        .flatMap(d -> d.linjer.stream()).map(LinjeUtkast::bruksdataId).toList());
                funnListe.add(advarsel(ctx.kjoringId, null, Kontrollkode.UNDER_TERSKEL,
                        (kunder.size() == 1 ? "1 kunde" : kunder.size() + " kunder")
                                + " har samlet " + kroner(terskel) + " kr eksl. mva eller mindre i perioden og "
                                + "faktureres ikke (til sammen " + kroner(total) + " kr eksl. mva). Terskelverdien er "
                                + kroner(terskel) + " kr i prisversjonen «" + ctx.versjon.navn() + "»."));
            }
        }

        // Create invoices in deterministic order and assign ordre_nr.
        List<Gruppe> sorterteGrupper = new ArrayList<>(grupper.keySet());
        sorterteGrupper.sort(Comparator
                .comparing((Gruppe g) -> grupper.get(g).kunde.organisasjonsnummer())
                .thenComparing(g -> g.kundenummer));

        Map<Long, BigDecimal> sumPerKunde = new LinkedHashMap<>();
        Map<Long, Long> forsteFakturaPerKunde = new LinkedHashMap<>();
        int ordre = 1;
        for (Gruppe g : sorterteGrupper) {
            Gruppedata data = grupper.get(g);
            data.linjer.sort(Comparator.comparing((LinjeUtkast l) -> l.produktKode).thenComparing(l -> l.type));
            BigDecimal sum = sum(data.linjer);
            if (sum.abs().compareTo(MAKS_FAKTURABELOP) > 0) {
                // faktura.sum_belop is numeric(12,2); fail with a reason instead of a DB overflow (500).
                throw new Regelbrudd("Fakturaen til " + data.kunde.virksomhetsnavn() + " ("
                        + data.kunde.organisasjonsnummer() + ", kundenummer " + g.kundenummer + ") blir "
                        + sum.toPlainString() + " kr, over maksgrensen på " + MAKS_FAKTURABELOP.toPlainString()
                        + " kr. Sjekk enhetsprisene i prisversjonen og enheten på bruksdata (OQ-21).");
            }
            String mottaker = data.kunde.fakturamottakerOrgnr() != null
                    ? data.kunde.fakturamottakerOrgnr() : data.kunde.organisasjonsnummer();

            Faktura faktura = fakturaer.save(new Faktura(null, ctx.kjoringId, data.kunde.id(), UuidV7.naa(),
                    ordre++, g.kundenummer, data.kunde.virksomhetsnavn(), data.tilleggstekst, mottaker, sum));
            ctx.antallFakturaer++;
            for (LinjeUtkast l : data.linjer) {
                linjer.save(new Fakturalinje(null, faktura.id(), l.produktId, l.beskrivelse, l.antall,
                        l.enhetspris, l.belop, l.prisId, l.bruksdataId, l.servicekode,
                        l.fakturareferanse, l.bestillingsnummer, l.bruksdataEndringId));
            }
            if (data.linjer.stream().anyMatch(l -> l.belop.compareTo(BigDecimal.ZERO) == 0)) {
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.NULLBELOP,
                        "Fakturaen har minst én linje med beløp 0"));
            }
            // LG04 carries one reference pair per order, so the export splits this invoice (specs/008 FR-009).
            // Same key as the export's grouping (EksportService.byggOrdrer), so warning and file agree.
            Set<List<String>> referanser = new LinkedHashSet<>();
            data.linjer.forEach(l -> referanser.add(Arrays.asList(l.fakturareferanse, l.bestillingsnummer)));
            if (referanser.size() > 1) {
                String liste = referanser.stream().map(r -> referansetekst(r.get(0), r.get(1)))
                        .collect(Collectors.joining("; "));
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.DELT_REFERANSE,
                        "Fakturaen har " + referanser.size() + " ulike referanser (" + liste + ") og blir "
                                + referanser.size() + " fakturaer i Unit4"));
            }
            sumPerKunde.merge(data.kunde.id(), sum, BigDecimal::add);
            forsteFakturaPerKunde.putIfAbsent(data.kunde.id(), faktura.id());
        }

        if (!ctx.korrigering) {
            leggTilForrigePeriodeAdvarsler(ctx, sumPerKunde, forsteFakturaPerKunde, funnListe);
        }
        List<Kontrollfunn> lagret = funn.saveAll(funnListe);
        List<KontrollfunnBruksdata> lenker = new ArrayList<>();
        if (!ctx.korrigering) {
            utelattePerFunn.forEach((indeks, ider) ->
                    ider.forEach(id -> lenker.add(new KontrollfunnBruksdata(lagret.get(indeks).id(), id))));
        }
        funnBruksdata.lagre(lenker);
        if (underTerskelFunn != null) {
            Long funnId = lagret.get(underTerskelFunn).id();
            List<FakturaUnderTerskel> rader = new ArrayList<>();
            underTerskelGrupper.forEach((g, d) -> rader.add(new FakturaUnderTerskel(null, ctx.kjoringId,
                    d.kunde.id(), d.kunde.organisasjonsnummer(), d.kunde.virksomhetsnavn(), g.kundenummer(),
                    d.tilleggstekst, sum(d.linjer), d.linjer.size(), kundesum.get(g.kundeId()), terskel, funnId)));
            underTerskel.saveAll(rader);
        }
    }

    private static BigDecimal sum(List<LinjeUtkast> linjer) {
        return linjer.stream().map(LinjeUtkast::belop).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** An amount in a finding's text, Norwegian style: 40,00. */
    private static String kroner(BigDecimal belop) {
        return belop.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private void leggTilForrigePeriodeAdvarsler(Byggekontekst ctx, Map<Long, BigDecimal> sumPerKunde,
                                                Map<Long, Long> forsteFakturaPerKunde, List<Kontrollfunn> ut) {
        Optional<Fakturakjoring> forrige = kjoringer.findFirstByPeriodeAndStatusNot(
                ctx.periode.forrige().førsteDag(), Kjoringstatus.FORKASTET.name());
        if (forrige.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> forrigeSum = new LinkedHashMap<>();
        for (Faktura f : fakturaer.findByKjoringIdOrderByOrdreNr(forrige.get().id())) {
            forrigeSum.merge(f.kundeId(), f.sumBelop(), BigDecimal::add);
        }
        sumPerKunde.forEach((kundeId, sum) -> {
            Long fakturaId = forsteFakturaPerKunde.get(kundeId);
            BigDecimal forr = forrigeSum.get(kundeId);
            if (forr == null) {
                ut.add(advarsel(ctx.kjoringId, fakturaId, Kontrollkode.NY_KUNDE,
                        "Ny kunde denne perioden (ikke fakturert forrige periode)"));
            } else if (forr.signum() != 0) {
                BigDecimal avvik = sum.subtract(forr).abs()
                        .divide(forr.abs(), 4, RoundingMode.HALF_UP);
                if (avvik.compareTo(AVVIK_GRENSE) > 0) {
                    ut.add(advarsel(ctx.kjoringId, fakturaId, Kontrollkode.STORT_AVVIK,
                            "Sum avviker " + avvik.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
                                    + "% fra forrige periode"));
                }
            }
        });
    }

    // ---- State machine ----------------------------------------------------------------------

    @Transactional
    public void godkjenn(Long kjoringId) {
        Fakturakjoring kj = hentForOppdatering(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en generert kjøring kan godkjennes (status er " + kj.status() + ")");
        }
        if (funn.existsByKjoringIdAndAlvorlighet(kjoringId, Alvorlighet.BLOKKERENDE.name())) {
            throw new Regelbrudd("Kjøringen har blokkerende kontrollfunn og kan ikke godkjennes");
        }
        String bruker = brukerContext.naavaerendeBruker();
        kjoringer.save(kj.medStatus(Kjoringstatus.GODKJENT.name(), bruker, OffsetDateTime.now()));
        audit.logg(Handling.GODKJENTE, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "type", kj.type()));
    }

    /**
     * Change the run's invoice texts (specs/009). Only while GENERERT: from approval on they are part
     * of what was approved. Texts are trimmed and validated against the LG04 fields; each changed
     * field is logged with old and new value, and a save without changes logs nothing.
     */
    @Transactional
    public Fakturakjoring endreTekster(Long kjoringId, Fakturatekster nye) {
        Fakturakjoring kj = hentForOppdatering(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Tekstene på fakturaen er låst etter godkjenning (status er "
                    + kj.status() + ")");
        }
        Fakturatekster tekster = nye.normalisert();
        tekster.valider();
        Map<String, Map<String, String>> endringer = tekster.endringerFra(kj.tekster());
        if (endringer.isEmpty()) {
            return kj;
        }
        Fakturakjoring lagret = kjoringer.save(kj.medTekster(tekster));
        audit.logg(Handling.ENDRET, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "endringer", endringer));
        return lagret;
    }

    private Fakturakjoring hentForOppdatering(Long id) {
        return kjoringer.finnForOppdatering(id).orElseThrow(() -> new Regelbrudd("Fant ikke kjøring " + id));
    }

    /** Move an approved run to EKSPORTERT. Called by the export service once artifacts are archived. */
    @Transactional
    public void settEksportert(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        if (!Kjoringstatus.GODKJENT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en godkjent kjøring kan eksporteres (status er " + kj.status() + ")");
        }
        kjoringer.save(kj.medStatus(Kjoringstatus.EKSPORTERT.name(), kj.godkjentAv(), kj.godkjentAt()));
    }

    @Transactional
    public void forkast(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        if (Kjoringstatus.EKSPORTERT.name().equals(kj.status())) {
            throw new Regelbrudd("En eksportert kjøring kan ikke forkastes");
        }
        if (Kjoringstatus.FORKASTET.name().equals(kj.status())) {
            return;
        }
        kjoringer.save(kj.medStatus(Kjoringstatus.FORKASTET.name(), kj.godkjentAv(), kj.godkjentAt()));
        if (kj.erKorrigering() && kj.sammenligningId() != null) {
            BruksdataSammenligning sammenligning = sammenligninger.findById(kj.sammenligningId())
                    .orElseThrow(() -> new Regelbrudd("Fant ikke sammenligning " + kj.sammenligningId()));
            if (Sammenligningstatus.KJORING_OPPRETTET.name().equals(sammenligning.status())) {
                List<BruksdataSammenligning> nyereAapne = sammenligninger.findByPeriodeAndStatus(
                        kj.periode(), Sammenligningstatus.AAPEN.name());
                if (nyereAapne.isEmpty()) {
                    sammenligninger.save(sammenligning.medStatus(Sammenligningstatus.AAPEN.name(), null));
                } else {
                    sammenligninger.save(sammenligning.medStatus(
                            Sammenligningstatus.ERSTATTET.name(), nyereAapne.getLast().id()));
                }
            }
        }
        audit.logg(Handling.FORKASTET, "FAKTURAKJORING", kjoringId, Map.of(
                "periode", kj.periode().toString(), "versjon", kj.versjon(), "type", kj.type()));
    }

    // ---- Queries ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Fakturakjoring> alle() {
        return kjoringer.findAllByOrderByGenerertAtDesc();
    }

    @Transactional(readOnly = true)
    public Fakturakjoring hent(Long id) {
        return kjoringer.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke kjøring " + id));
    }

    /** The would-be invoices the run left out under the threshold (specs/011), by orgnr and kundenummer. */
    @Transactional(readOnly = true)
    public List<FakturaUnderTerskel> underTerskel(Long kjoringId) {
        return underTerskel.findByKjoringIdOrderByOrganisasjonsnummerAscKundenummerAsc(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Faktura> fakturaer(Long kjoringId) {
        return fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
    }

    /** Headline figures for the run page and the covering email (specs/002 FR-001..003). */
    @Transactional(readOnly = true)
    public Kjoringssammendrag sammendrag(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        List<Faktura> fs = fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
        int antallLinjer = 0;
        BigDecimal sumLinjer = BigDecimal.ZERO;
        BigDecimal sumFakturaer = BigDecimal.ZERO;
        BigDecimal sumInklMva = BigDecimal.ZERO;
        for (Faktura f : fs) {
            sumFakturaer = sumFakturaer.add(f.sumBelop());
            sumInklMva = sumInklMva.add(
                    f.sumBelop().multiply(Kjoringssammendrag.MVA_FAKTOR).setScale(2, RoundingMode.HALF_UP));
            for (Fakturalinje l : linjer.findByFakturaIdOrderById(f.id())) {
                antallLinjer++;
                sumLinjer = sumLinjer.add(l.belop());
            }
        }
        return new Kjoringssammendrag(new Periode(kj.periode()), fs.size(), antallLinjer, sumLinjer, sumFakturaer,
                sumInklMva);
    }

    /**
     * The customer name to show per invoice (D9): the name frozen at generation, or — for invoices
     * generated before it was frozen — the current registry name marked as such.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> kundenavn(List<Faktura> fs) {
        Map<Long, String> navn = new LinkedHashMap<>();
        Map<Long, Kunde> kundeById = new LinkedHashMap<>();
        for (Faktura f : fs) {
            if (f.kundenavn() != null) {
                navn.put(f.id(), f.kundenavn());
            } else {
                Kunde k = kundeById.computeIfAbsent(f.kundeId(), id -> kunder.findById(id).orElse(null));
                navn.put(f.id(), k == null ? "" : k.virksomhetsnavn() + NAAVAERENDE_NAVN);
            }
        }
        return navn;
    }

    /**
     * Every invoice line of a run with what a spreadsheet of the fakturagrunnlag needs beside the line:
     * its invoice, customer name and orgnr, and the usage type of the row it came from. Ordered like the
     * LG04 (invoice order, then product and type).
     */
    @Transactional(readOnly = true)
    public List<Grunnlagslinje> grunnlagslinjer(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        Map<Long, String> typeByBruksdata = new LinkedHashMap<>();
        Map<Long, String> typeByEndring = new LinkedHashMap<>();
        if (kj.bruksdataImportId() != null) {
            usage.rader(kj.bruksdataImportId()).forEach(b -> typeByBruksdata.put(b.id(), b.type()));
        }
        if (kj.erKorrigering()) {
            endringer.findAllById(valgteEndringer.endringIder(kjoringId))
                    .forEach(e -> typeByEndring.put(e.id(), e.type()));
        }
        List<Faktura> fs = fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
        Map<Long, String> navn = kundenavn(fs);
        Map<Long, String> orgnrByKunde = new LinkedHashMap<>();
        List<Grunnlagslinje> ut = new ArrayList<>();
        for (Faktura f : fs) {
            String orgnr = orgnrByKunde.computeIfAbsent(f.kundeId(),
                    id -> kunder.findById(id).map(Kunde::organisasjonsnummer).orElse(""));
            for (Fakturalinje l : linjer.findByFakturaIdOrderById(f.id())) {
                String type = l.bruksdataEndringId() == null
                        ? typeByBruksdata.get(l.bruksdataId()) : typeByEndring.get(l.bruksdataEndringId());
                ut.add(new Grunnlagslinje(f, l, navn.get(f.id()), orgnr, type));
            }
        }
        return ut;
    }

    /** One line of the fakturagrunnlag spreadsheet (see {@link #grunnlagslinjer}). */
    public record Grunnlagslinje(Faktura faktura, Fakturalinje linje, String kundenavn, String organisasjonsnummer,
                                 String type) {
    }

    @Transactional(readOnly = true)
    public Faktura hentFaktura(Long fakturaId) {
        return fakturaer.findById(fakturaId).orElseThrow(() -> new Regelbrudd("Fant ikke faktura " + fakturaId));
    }

    @Transactional(readOnly = true)
    public List<Fakturalinje> linjer(Long fakturaId) {
        return linjer.findByFakturaIdOrderById(fakturaId);
    }

    @Transactional(readOnly = true)
    public List<Kontrollfunn> funn(Long kjoringId) {
        return funn.findByKjoringId(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Kontrollfunn> funnForFaktura(Long fakturaId) {
        return funn.findByFakturaId(fakturaId);
    }

    @Transactional(readOnly = true)
    public boolean harBlokkerende(Long kjoringId) {
        return funn.existsByKjoringIdAndAlvorlighet(kjoringId, Alvorlighet.BLOKKERENDE.name());
    }

    @Transactional(readOnly = true)
    public List<Long> valgteEndringIder(Long kjoringId) {
        return valgteEndringer.endringIder(kjoringId);
    }

    @Transactional(readOnly = true)
    public List<Eksportfil> eksportfiler(Long kjoringId) {
        return eksportfiler.findByKjoringIdOrderByType(kjoringId);
    }

    @Transactional(readOnly = true)
    public Eksportfil hentEksportfil(Long id) {
        return eksportfiler.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke eksportfil " + id));
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private Prisversjon aktivVersjonFor(Periode periode) {
        LocalDate d = periode.førsteDag();
        List<Prisversjon> dekkende = prisversjoner.findByStatus(PrisversjonStatus.AKTIV.name()).stream()
                .filter(v -> !v.gyldigFra().isAfter(d) && (v.gyldigTil() == null || !v.gyldigTil().isBefore(d)))
                .toList();
        if (dekkende.isEmpty()) {
            throw new Regelbrudd("Ingen aktiv prisversjon dekker " + periode);
        }
        if (dekkende.size() > 1) {
            throw new Regelbrudd("Flere aktive prisversjoner dekker " + periode);
        }
        return dekkende.get(0);
    }

    private static KundenummerRegel velgRegel(List<KundenummerRegel> regler, Long produktId) {
        return regler.stream().filter(r -> produktId.equals(r.produktId()))
                .min(Comparator.comparing(KundenummerRegel::id))
                .or(() -> regler.stream().filter(r -> r.produktId() == null)
                        .min(Comparator.comparing(KundenummerRegel::id)))
                .orElse(null);
    }

    private static KundeReferanse velgReferanse(List<KundeReferanse> referanser, Long produktId) {
        return referanser.stream().filter(r -> produktId.equals(r.produktId())).findFirst()
                .or(() -> referanser.stream().filter(r -> r.produktId() == null).findFirst())
                .orElse(null);
    }

    private static String referansetekst(String fakturareferanse, String bestillingsnummer) {
        if (fakturareferanse == null && bestillingsnummer == null) {
            return "ingen referanse";
        }
        return (fakturareferanse == null ? "–" : fakturareferanse)
                + (bestillingsnummer == null ? "" : " / " + bestillingsnummer);
    }

    private static String beskrivKostnad(String type, Produkt p, Periode periode) {
        String prefiks = "SMS_KOSTNAD".equals(type) ? "SMS-kostnad" : "Azure-kostnad";
        return prefiks + " " + p.navn() + " " + periode.norskLabel();
    }

    private static String kode(Map<Long, Produkt> produktById, Long id) {
        Produkt p = produktById.get(id);
        return p == null ? "" : p.kode();
    }

    private static Kontrollfunn blokk(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return funn(kjoringId, fakturaId, Alvorlighet.BLOKKERENDE, kode, melding);
    }

    private static Kontrollfunn advarsel(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return funn(kjoringId, fakturaId, Alvorlighet.ADVARSEL, kode, melding);
    }

    // The Kontroller tab explains each code with its declared severity; raising it with another would mislead.
    private static Kontrollfunn funn(Long kjoringId, Long fakturaId, Alvorlighet alvorlighet, Kontrollkode kode,
                                     String melding) {
        if (kode.alvorlighet() != alvorlighet) {
            throw new IllegalStateException(kode + " er " + kode.alvorlighet() + ", ikke " + alvorlighet);
        }
        return new Kontrollfunn(null, kjoringId, fakturaId, alvorlighet.name(), kode.name(), melding);
    }

    private static final class Byggekontekst {
        final Periode periode;
        final Prisversjon versjon;
        final Long kjoringId;
        final boolean korrigering;
        int antallFakturaer;

        Byggekontekst(Periode periode, Prisversjon versjon, Long kjoringId, boolean korrigering) {
            this.periode = periode;
            this.versjon = versjon;
            this.kjoringId = kjoringId;
            this.korrigering = korrigering;
        }
    }

    private record Gruppe(Long kundeId, String kundenummer) {
    }

    private static final class Gruppedata {
        final Kunde kunde;
        final String tilleggstekst;
        final List<LinjeUtkast> linjer = new ArrayList<>();

        Gruppedata(Kunde kunde, String tilleggstekst) {
            this.kunde = kunde;
            this.tilleggstekst = tilleggstekst;
        }
    }

    private record LinjeUtkast(Long produktId, String produktKode, String type, String beskrivelse,
                               BigDecimal antall, BigDecimal enhetspris, BigDecimal belop, Long prisId,
                               Long bruksdataId, Long bruksdataEndringId, String servicekode, String fakturareferanse,
                               String bestillingsnummer) {
    }
}
