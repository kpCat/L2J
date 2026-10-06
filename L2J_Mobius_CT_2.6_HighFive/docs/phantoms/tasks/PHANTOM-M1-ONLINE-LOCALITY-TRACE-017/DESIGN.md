# DESIGN

## Recorder selection rule

For topology query predicate:

```java
final boolean online = _online.test(profileId);
if (online && recorder.isRecording())
{
    recorder.watch(profileId);
    recorder.record(profileId, "LOCAL_CANDIDATE", "ONLINE", "", "topology.eligible", ...);
}
return online;
```

Do not watch OFFLINE topology probes.

After final `candidates` is built, existing physical-demand recording may call
`recorder.watch(profileId)` for each accepted candidate. This is desirable and guarantees
accepted local candidates can fill remaining watch slots.

## Global visibility of excluded probes

Do not add per-profile OFFLINE retention.

If useful, add one global summary event with scalar counts only:
`LOCALITY_FILTER_SUMMARY`
values = probed / onlineEligible / finalCandidates.

No DB/file I/O, locks, sleeps, or gameplay branching.

## Semantics invariant

The result passed to `nativeProfilesAt(...)` must remain exactly `_online.test(profileId)`.
Moving recorder calls must not change:
- `_online.test` call count for the predicate;
- topology query;
- candidate membership;
- physical demand facts;
- signal submission;
- signal state/TTL;
- locality state.

## Runtime watched-cohort acceptance

A valid TASK017 trace must prove:
- at least one watched profile has `LOCAL_CANDIDATE stateA=ONLINE` and/or
  `LOCAL_CANDIDATE stateA=LOCAL reason=physical.demand`;
- watched slots are not all consumed by OFFLINE topology probes;
- at least one watched accepted profile gets `LOCAL_SIGNAL_RESULT` from `human.local`
  unless signal admission itself is the first lost edge.

Ignore `background.native_context` attempts on unrelated OFFLINE profiles when naming
the human-local critical-path blocker.
