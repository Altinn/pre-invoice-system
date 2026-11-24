/*
  forsystem page behaviour (specs/004 research D4). Everything is delegated from `document`, so it
  keeps working after htmx swaps page content in without a reload. Pages work without this file:
  forms then fall back to the browser's own validation and actions submit without a confirmation.
*/
(function () {
    'use strict';

    // ---- Inline validation messages (FR-010) ------------------------------------------------
    // A field inside <ds-field> with a [data-field="validation"] paragraph shows its
    // data-feilmelding (or the browser's message) there instead of the native bubble.
    // <ds-field> then sets aria-invalid and aria-describedby on the input by itself.

    function valideringsmelding(felt) {
        var dsField = felt.closest('ds-field, .ds-field');
        return dsField && dsField.querySelector(':scope > [data-field="validation"]');
    }

    var sistFokusert = 0;

    document.addEventListener('invalid', function (e) {
        var felt = e.target;
        var melding = valideringsmelding(felt);
        if (!melding) return;
        e.preventDefault();
        melding.textContent = felt.getAttribute('data-feilmelding') || felt.validationMessage;
        melding.hidden = false;
        // The browser fires `invalid` for every bad field on submit; focus only the first one.
        var naa = Date.now();
        if (naa - sistFokusert > 100) {
            sistFokusert = naa;
            felt.focus();
        }
    }, true);

    function ryddOppHvisGyldig(e) {
        var felt = e.target;
        if (!felt.validity || !felt.validity.valid) return;
        var melding = valideringsmelding(felt);
        if (melding && !melding.hidden) {
            melding.hidden = true;
            melding.textContent = '';
        }
    }
    document.addEventListener('input', ryddOppHvisGyldig);
    document.addEventListener('change', ryddOppHvisGyldig);

    // ---- Correction comparison: customers (specs/019) ---------------------------------------
    // Checkboxes start unchecked; one per customer. «Velg alle» ticks every customer of the comparison,
    // also those on other pages: the hidden `alle` field tells the server, and the summary then shows the
    // comparison's totals. Unticking any customer turns it back into an explicit selection.
    function kroner(tall) {
        return tall.toLocaleString('nb-NO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function oppdaterEndringsutvalg(skjema) {
        var bokser = Array.prototype.slice.call(skjema.querySelectorAll('[data-endring-checkbox]:not(:disabled)'));
        var omraade = skjema.closest('[data-endring-omraade]') || skjema.closest('section') || skjema;
        var alleFelt = skjema.querySelector('[data-velg-alle-felt]');
        var valgte = bokser.filter(function (boks) { return boks.checked; });
        var alleValgt = bokser.length > 0 && valgte.length === bokser.length;
        if (alleFelt && !alleValgt) alleFelt.value = 'false';
        var heleSammenligningen = alleFelt && alleFelt.value === 'true';
        var antall = valgte.length;
        var belop = 0;
        valgte.forEach(function (boks) { belop += Number(boks.getAttribute('data-kunde-belop')) || 0; });
        if (heleSammenligningen) {
            antall = Number(omraade.getAttribute('data-alle-antall')) || antall;
            belop = Number(omraade.getAttribute('data-alle-belop')) || belop;
        }
        var antallFelt = omraade.querySelector('[data-valgt-antall]');
        var belopFelt = omraade.querySelector('[data-valgt-belop]');
        var knapp = skjema.querySelector('[data-velg-alle]');
        if (antallFelt) antallFelt.textContent = String(antall);
        if (belopFelt) belopFelt.textContent = kroner(belop);
        if (knapp) knapp.textContent = alleValgt
            ? 'Fjern alle valg' : (knapp.getAttribute('data-velg-alle-tekst') || 'Velg alle');
    }

    document.addEventListener('change', function (e) {
        if (!e.target.matches('[data-endring-checkbox]')) return;
        oppdaterEndringsutvalg(e.target.closest('[data-endring-utvalg]'));
    });

    document.addEventListener('click', function (e) {
        var knapp = e.target.closest('[data-velg-alle]');
        if (!knapp) return;
        var skjema = knapp.closest('[data-endring-utvalg]');
        var bokser = Array.prototype.slice.call(skjema.querySelectorAll('[data-endring-checkbox]:not(:disabled)'));
        var velg = bokser.some(function (boks) { return !boks.checked; });
        bokser.forEach(function (boks) { boks.checked = velg; });
        var alleFelt = skjema.querySelector('[data-velg-alle-felt]');
        if (alleFelt) alleFelt.value = velg ? 'true' : 'false';
        oppdaterEndringsutvalg(skjema);
    });

    // «+» shows a customer's products; without this file they are shown at once (<noscript> style).
    document.addEventListener('click', function (e) {
        var knapp = e.target.closest('[data-utvid]');
        if (!knapp) return;
        var maal = document.getElementById(knapp.getAttribute('aria-controls'));
        if (!maal) return;
        var apen = knapp.getAttribute('aria-expanded') === 'true';
        maal.hidden = apen;
        knapp.setAttribute('aria-expanded', apen ? 'false' : 'true');
        knapp.textContent = apen ? '+' : '−';
        var navn = knapp.getAttribute('aria-label') || '';
        knapp.setAttribute('aria-label', apen ? navn.replace(/^Skjul/, 'Vis') : navn.replace(/^Vis/, 'Skjul'));
    });

    // ---- «Funn akseptert» checkboxes submit at once (specs/013) -----------------------------
    // Each checkbox sits in its own POST form; ticking it is the action. Without this file the
    // form's <noscript> «Lagre» button submits it.

    document.addEventListener('change', function (e) {
        if (!e.target.matches('input[data-auto-send]')) return;
        var skjema = e.target.form;
        if (!skjema) return;
        if (typeof skjema.requestSubmit === 'function') skjema.requestSubmit();
        else skjema.submit();
    });

    // ---- Confirmation before irreversible actions (FR-012/013) ------------------------------
    // <button type="submit" data-bekreft="dialog-id"> opens that <dialog> instead of submitting.
    // The dialog sits inside the same <form>; its own submit button performs the action.

    document.addEventListener('click', function (e) {
        var knapp = e.target.closest('button[data-bekreft]');
        if (!knapp || knapp.disabled) return;
        var dialog = document.getElementById(knapp.getAttribute('data-bekreft'));
        if (!dialog || typeof dialog.showModal !== 'function') return; // fall back: plain submit
        e.preventDefault();
        // Open after this click has finished propagating: otherwise the closedby="any" light
        // dismiss sees the very same click as a click outside the dialog and closes it again.
        setTimeout(function () { dialog.showModal(); }, 0);
    });

    // ---- Links to an explanation open it (specs/010 FR-008) --------------------------------
    // A link to a closed <details> (e.g. the Kontroller tab's «Alvorlighet»/«Kode» headers) opens
    // it before the browser scrolls there; without this file the link still scrolls to it.

    document.addEventListener('click', function (e) {
        var lenke = e.target.closest('a[href^="#"]');
        if (!lenke) return;
        var maal = document.getElementById(lenke.getAttribute('href').slice(1));
        if (maal && maal.tagName === 'DETAILS') maal.open = true;
    });
})();
