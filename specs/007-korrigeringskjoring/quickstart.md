# Quickstart: Validate versioned correction runs

This is the implementation acceptance walkthrough. It assumes local demo data or fake fixtures only.

## Automated verification

```bash
./mvnw verify
```

Required focused suites:

- `BruksdataSammenligningTest`: added/changed/removed/unchanged, decimal equality, old+delta=new,
  partial selections and effective basis after V2/V3.
- `BruksdataKandidatServiceIT`: raw archive/checksum, normalized candidate rows, idempotency,
  incomplete response refusal and supersession.
- `KorrigeringskjoringIT`: V1 exported → selected V2 deltas, root price version, snapshot reuse,
  unselected absence, version monotonicity, stale/concurrent request refusal.
- `KorrigeringsavstemmingIT`: each selected change invoiced or structurally explained; exact signed totals.
- `KorrigeringEksportIT`: signed CSV/XLSX/PDF totals and versioned filenames; export gate off/on.
- Existing LG04 golden suites: ordinary V1 bytes unchanged.

## Manual local walkthrough

1. Import a complete fake DWH January snapshot and generate, approve and export V1.
2. Submit a second complete January DWH snapshot with:
   - one unchanged key;
   - one increase;
   - one decrease;
   - one removed key;
   - one added key.
3. Verify the comparison shows four changes (increase, decrease, removed, added), counts the unchanged
   row but does not list it, and all product totals reconcile.
4. Select only the increase and removal, enter `DWH korrigerte januargrunnlaget`, and create V2.
5. Verify V2 contains exactly two selected changes and signed delta lines; V1 is unchanged.
6. Approve V2. If the OQ-6 export flag is off, verify export is blocked with the specified message.
7. Discard V2 and recreate the selection. Verify the new attempt is V3, not reused V2.
8. In a separate clean fixture, export V2, then compare the same candidate again. Verify only the
   previously unselected differences remain.
9. Open V1/V2 history and follow every traceability link to raw payload, normalized old/new values,
   selection, line, price and audit event.

## tt02 accounting gate

Only after OQ-6/OQ-23 are updated:

1. Generate a correction fixture containing a positive line, negative line, removal and a net-negative
   invoice using fake/test customers accepted by VIS.
2. Export with the confirmed correction mechanism and batch identifier.
3. VIS imports it into Unit4 tt02.
4. Compare Unit4's report with forsystem: invoice/order count, customer numbers, each signed amount and
   total must match exactly.
5. Archive the test evidence and record the result/date in OQ-6 and the decision log.
6. Only then enable correction export in production configuration.

## Regression invariants

- A period with no exported run follows the old normal-import flow.
- A generated/approved ordinary run still blocks re-import until discarded.
- An exported V1 is never changed or discarded.
- Test-only reset behavior remains behind its existing flag.
- Existing ordinary-run LG04 golden file is byte-identical.
