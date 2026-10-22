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
})();
