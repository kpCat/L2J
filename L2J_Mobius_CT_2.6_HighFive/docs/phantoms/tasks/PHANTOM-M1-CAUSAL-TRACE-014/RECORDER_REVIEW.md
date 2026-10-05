# Observation-only review

Base: ccacd6c5bf8fa1234a5559ece708ee3a35efd536. No subagents, per owner instruction.

- Locality calls the original presence predicate exactly once in the existing topology eligibility callback and returns that result unchanged. It observes candidates before the online filter. Existing candidate construction, demand facts and signal decisions are unchanged.
- Scheduler hooks read existing slot fields under the existing monitor. They introduce no monitor or gameplay lock. Original admission conditions, queue mutation, transition plan, retry and outcomes are unchanged. Per-profile repeated scans are sampled once/second; promotion and boundary results are unsampled.
- Readiness preserves `online && locality` short-circuit order, the same ecology request and return outcomes. No ecology force, retry change, state mutation or additional repository read.
- Materialization invokes the original delegate exactly once with a bounded ThreadLocal diagnostic context. Tests verify identical return and exception. Hooks read existing result snapshots and existing object/state fields; Player.load, lifecycle, online, spawn and action admission statements are unchanged.
- Pilot BEGIN/SNAPSHOT/END execute after the existing session admission. Stop/revoke uses the existing cancellation path. Exports paginate below the existing 64 KiB reader limit, without changing production protocol size limits.
- Recorder defaults inactive, requires diagnostics plus BEGIN, expires after 120 seconds, watches at most eight positive profiles, retains at most 8192 bounded immutable events, and reports unavailable sequences. Recording has no database/file access, waits, logger or gameplay locks. Export sorting occurs only at requested snapshot/end.
- No configuration default, dependency, schema, catalog, gameplay or materialization policy change. Runtime-only changes stay private.

Focused RED: 0/3 passed against the inactive stub. Final GREEN: 3/3 passed.
Affected scheduler, local-priority and topology-perception targets plus jar: Ant exit 0, BUILD SUCCESSFUL. Two pre-existing deprecated `System.runFinalization` warnings.
Static exact-scope and separate encoding checks passed. Live causal chain remains unverified until the required manual login gate.
