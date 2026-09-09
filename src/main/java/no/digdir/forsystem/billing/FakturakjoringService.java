package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import no.digdir.forsystem.usage.Bruksdatatype;
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

    private static final BigDecimal AVVIK_GRENSE = new BigDecimal("0.30");

    private final FakturakjoringRepository kjoringer;
    private final FakturaRepository fakturaer;
    private final FakturalinjeRepository linjer;
    private final KontrollfunnRepository funn;
    private final EksportfilRepository eksportfiler;
    private final ProduktRepository produkter;
    private final PrisRepository priser;
    private final PrisversjonRepository prisversjoner;
    private final KundeRepository kunder;
    private final KundeReferanseRepository referanser;
    private final KundenummerRegelRepository regler;
    private final UsageImportService usage;
    private final AuditService audit;
    private final BrukerContext brukerContext;

    FakturakjoringService(FakturakjoringRepository kjoringer, FakturaRepository fakturaer,
                          FakturalinjeRepository linjer, KontrollfunnRepository funn,
                          EksportfilRepository eksportfiler,
                          ProduktRepository produkter, PrisRepository priser,
                          PrisversjonRepository prisversjoner, KundeRepository kunder,
                          KundeReferanseRepository referanser, KundenummerRegelRepository regler,
                          UsageImportService usage, AuditService audit, BrukerContext brukerContext) {
        this.kjoringer = kjoringer;
        this.fakturaer = fakturaer;
        this.linjer = linjer;
        this.funn = funn;
        this.eksportfiler = eksportfiler;
        this.produkter = produkter;
        this.priser = priser;
        this.prisversjoner = prisversjoner;
        this.kunder = kunder;
        this.referanser = referanser;
        this.regler = regler;
        this.usage = usage;
        this.audit = audit;
        this.brukerContext = brukerContext;
    }

    // ---- Generation -------------------------------------------------------------------------

    @Transactional
    public Fakturakjoring generer(Periode periode) {
        Prisversjon versjon = aktivVersjonFor(periode);
        if (kjoringer.existsByPeriodeAndStatusNot(periode.førsteDag(), Kjoringstatus.FORKASTET.name())) {
            throw new Regelbrudd("Det finnes allerede en aktiv kjøring for " + periode
                    + ". Forkast den før du genererer på nytt.");
        }
        List<Bruksdata> bruk = usage.bruksdataFor(periode);
        if (bruk.isEmpty()) {
            throw new Regelbrudd("Ingen bruksdata for " + periode + " — importer bruk først.");
        }

        Fakturakjoring kjoring = kjoringer.save(new Fakturakjoring(null, periode.førsteDag(),
                Kjoringstatus.GENERERT.name(), versjon.id(), brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), null, null, null));

        var ctx = new Byggekontekst(periode, versjon, kjoring.id());
        bygg(ctx, bruk);
        audit.logg(Handling.GENERERTE, "FAKTURAKJORING", kjoring.id(),
                Map.of("periode", periode.toString(), "antallFakturaer", ctx.antallFakturaer));
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
        var orgnrUtenKunde = new java.util.LinkedHashSet<String>();
        var manglerKundenummer = new java.util.LinkedHashSet<String>();
        var manglerPris = new java.util.LinkedHashSet<String>();
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
                orgnrUtenKunde.add(b.organisasjonsnummer());
                continue;
            }
            kunderMedBruk.putIfAbsent(k.id(), k);
            if (p.konto() == null) {
                manglerKontering.add(p.kode());
            }
            KundenummerRegel regel = velgRegel(
                    reglerByKunde.getOrDefault(k.id(), List.of()), p.id());
            if (regel == null) {
                manglerKundenummer.add(k.organisasjonsnummer() + "/" + p.kode());
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
                    manglerPris.add(p.kode());
                    continue;
                }
                antall = b.antall() == null ? BigDecimal.ZERO : b.antall();
                enhetspris = pris.enhetspris();
                prisId = pris.id();
                belop = antall.multiply(enhetspris).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = "Bruk av " + p.navn() + " " + ctx.periode.norskLabel();
            } else {
                belop = (b.belop() == null ? BigDecimal.ZERO : b.belop()).setScale(2, RoundingMode.HALF_UP);
                beskrivelse = beskrivKostnad(b.type(), p, ctx.periode);
            }

            KundeReferanse ref = velgReferanse(
                    referanserByKunde.getOrDefault(k.id(), List.of()), p.id());
            var linje = new LinjeUtkast(p.id(), p.kode(), b.type(), beskrivelse, antall, enhetspris, belop,
                    prisId, b.id(), regel.servicekode(),
                    ref == null ? null : ref.fakturareferanse(),
                    ref == null ? null : ref.bestillingsnummer());

            Gruppe g = new Gruppe(k.id(), regel.kundenummer());
            grupper.computeIfAbsent(g, x -> new Gruppedata(k, regel.tilleggstekst())).linjer.add(linje);
        }

        // BLOKKERENDE findings.
        orgnrUtenKunde.forEach(orgnr -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KUNDE,
                "Bruk for organisasjonsnummer " + orgnr + " har ingen registrert kunde")));
        manglerKundenummer.forEach(kp -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KUNDENUMMER,
                "Mangler kundenummerregel for " + kp)));
        manglerPris.forEach(kode -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_PRIS,
                "Produkt " + kode + " mangler pris i aktiv prisversjon")));
        manglerKontering.forEach(kode -> funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.MANGLER_KONTERING,
                "Produkt " + kode + " mangler kontering (OQ-2)")));
        kunderMedBruk.values().forEach(k -> {
            if (!"AKTIV".equals(k.avtalestatus())) {
                funnListe.add(blokk(ctx.kjoringId, null, Kontrollkode.INAKTIV_AVTALE,
                        "Kunde " + k.virksomhetsnavn() + " har avtalestatus " + k.avtalestatus()));
            }
        });

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
            BigDecimal sum = data.linjer.stream().map(l -> l.belop).reduce(BigDecimal.ZERO, BigDecimal::add);
            String mottaker = data.kunde.fakturamottakerOrgnr() != null
                    ? data.kunde.fakturamottakerOrgnr() : data.kunde.organisasjonsnummer();

            Faktura faktura = fakturaer.save(new Faktura(null, ctx.kjoringId, data.kunde.id(), UuidV7.naa(),
                    ordre++, g.kundenummer, data.tilleggstekst, mottaker, sum));
            ctx.antallFakturaer++;
            for (LinjeUtkast l : data.linjer) {
                linjer.save(new Fakturalinje(null, faktura.id(), l.produktId, l.beskrivelse, l.antall,
                        l.enhetspris, l.belop, l.prisId, l.bruksdataId, l.servicekode,
                        l.fakturareferanse, l.bestillingsnummer));
            }
            if (data.linjer.stream().anyMatch(l -> l.belop.compareTo(BigDecimal.ZERO) == 0)) {
                funnListe.add(advarsel(ctx.kjoringId, faktura.id(), Kontrollkode.NULLBELOP,
                        "Fakturaen har minst én linje med beløp 0"));
            }
            sumPerKunde.merge(data.kunde.id(), sum, BigDecimal::add);
            forsteFakturaPerKunde.putIfAbsent(data.kunde.id(), faktura.id());
        }

        leggTilForrigePeriodeAdvarsler(ctx, sumPerKunde, forsteFakturaPerKunde, funnListe);
        funn.saveAll(funnListe);
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
        Fakturakjoring kj = hent(kjoringId);
        if (!Kjoringstatus.GENERERT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en generert kjøring kan godkjennes (status er " + kj.status() + ")");
        }
        if (funn.existsByKjoringIdAndAlvorlighet(kjoringId, Alvorlighet.BLOKKERENDE.name())) {
            throw new Regelbrudd("Kjøringen har blokkerende kontrollfunn og kan ikke godkjennes");
        }
        String bruker = brukerContext.naavaerendeBruker();
        kjoringer.save(new Fakturakjoring(kj.id(), kj.periode(), Kjoringstatus.GODKJENT.name(),
                kj.prisversjonId(), kj.generertAv(), kj.generertAt(), bruker, OffsetDateTime.now(), kj.kommentar()));
        audit.logg(Handling.GODKJENTE, "FAKTURAKJORING", kjoringId, Map.of("periode", kj.periode().toString()));
    }

    /** Move an approved run to EKSPORTERT. Called by the export service once artifacts are archived. */
    @Transactional
    public void settEksportert(Long kjoringId) {
        Fakturakjoring kj = hent(kjoringId);
        if (!Kjoringstatus.GODKJENT.name().equals(kj.status())) {
            throw new Regelbrudd("Bare en godkjent kjøring kan eksporteres (status er " + kj.status() + ")");
        }
        kjoringer.save(new Fakturakjoring(kj.id(), kj.periode(), Kjoringstatus.EKSPORTERT.name(),
                kj.prisversjonId(), kj.generertAv(), kj.generertAt(), kj.godkjentAv(), kj.godkjentAt(),
                kj.kommentar()));
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
        kjoringer.save(new Fakturakjoring(kj.id(), kj.periode(), Kjoringstatus.FORKASTET.name(),
                kj.prisversjonId(), kj.generertAv(), kj.generertAt(), kj.godkjentAv(), kj.godkjentAt(),
                kj.kommentar()));
        audit.logg(Handling.FORKASTET, "FAKTURAKJORING", kjoringId, Map.of("periode", kj.periode().toString()));
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

    @Transactional(readOnly = true)
    public List<Faktura> fakturaer(Long kjoringId) {
        return fakturaer.findByKjoringIdOrderByOrdreNr(kjoringId);
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

    private static String beskrivKostnad(String type, Produkt p, Periode periode) {
        String prefiks = "SMS_KOSTNAD".equals(type) ? "SMS-kostnad" : "Azure-kostnad";
        return prefiks + " " + p.navn() + " " + periode.norskLabel();
    }

    private static String kode(Map<Long, Produkt> produktById, Long id) {
        Produkt p = produktById.get(id);
        return p == null ? "" : p.kode();
    }

    private static Kontrollfunn blokk(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return new Kontrollfunn(null, kjoringId, fakturaId, Alvorlighet.BLOKKERENDE.name(), kode.name(), melding);
    }

    private static Kontrollfunn advarsel(Long kjoringId, Long fakturaId, Kontrollkode kode, String melding) {
        return new Kontrollfunn(null, kjoringId, fakturaId, Alvorlighet.ADVARSEL.name(), kode.name(), melding);
    }

    private static final class Byggekontekst {
        final Periode periode;
        final Prisversjon versjon;
        final Long kjoringId;
        int antallFakturaer;

        Byggekontekst(Periode periode, Prisversjon versjon, Long kjoringId) {
            this.periode = periode;
            this.versjon = versjon;
            this.kjoringId = kjoringId;
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
                               Long bruksdataId, String servicekode, String fakturareferanse,
                               String bestillingsnummer) {
    }
}
