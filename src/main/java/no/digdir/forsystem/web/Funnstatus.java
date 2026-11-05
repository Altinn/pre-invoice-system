package no.digdir.forsystem.web;

import java.util.List;

import no.digdir.forsystem.billing.Kontrollfunn;

/**
 * What a run's findings mean for approval (specs/013): blocking findings, warnings still to be accepted
 * («åpne») and warnings accepted. The Kontroller tab badge, the run list and the Oversikt checklist count
 * from here, so an accepted warning stops counting everywhere at once.
 */
public record Funnstatus(long antallBlokkerende, long antallAapne, long antallAksepterte) {

    public static Funnstatus av(List<Kontrollfunn> funn) {
        long blokkerende = funn.stream().filter(Kontrollfunn::blokkerende).count();
        long aapne = funn.stream().filter(Kontrollfunn::aapenAdvarsel).count();
        long aksepterte = funn.stream().filter(f -> !f.blokkerende() && f.erAkseptert()).count();
        return new Funnstatus(blokkerende, aapne, aksepterte);
    }

    /** Nothing holds back approval as far as the findings go. */
    public boolean kanGodkjennes() {
        return antallBlokkerende == 0 && antallAapne == 0;
    }

    /** The open warnings, e.g. «1 advarsel» (templates cannot call static methods). */
    public String aapneTekst() {
        return advarsler(antallAapne);
    }

    public String akseptertTekst() {
        return advarsler(antallAksepterte);
    }

    /** «1 advarsel» / «3 advarsler». */
    public static String advarsler(long n) {
        return n + (n == 1 ? " advarsel" : " advarsler");
    }
}
