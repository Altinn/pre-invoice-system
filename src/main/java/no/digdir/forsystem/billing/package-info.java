/**
 * Fakturakjøring: the generation engine, controls (kontrollfunn), statuses and approval.
 * The billing period is always an explicit parameter, never derived from the clock.
 * Depends on {@code registry}, {@code usage}, {@code common}.
 */
package no.digdir.forsystem.billing;
