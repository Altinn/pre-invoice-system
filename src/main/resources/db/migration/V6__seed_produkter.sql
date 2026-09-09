-- Seed the six Altinn products under FinMod (docs/01, docs/03). Only kode + navn are known;
-- artikkel_id, konto and dim_* stay NULL until Økonomi answers OQ-2. The generation controls
-- (Phase 3) must treat NULL kontering as a BLOKKERENDE finding, so leaving them NULL is correct
-- and deliberate here — do not invent accounting codes (CLAUDE.md hard rule).

insert into produkt (kode, navn) values
    ('melding',      'Melding'),
    ('formidling',   'Formidling'),
    ('varsling',     'Varsling'),
    ('autorisasjon', 'Autorisasjon'),
    ('studio',       'Studio'),
    ('appinfra',     'Applikasjonsinfrastruktur');
