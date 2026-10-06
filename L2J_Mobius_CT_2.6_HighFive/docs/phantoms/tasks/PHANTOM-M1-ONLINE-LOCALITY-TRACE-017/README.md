# PHANTOM-M1-ONLINE-LOCALITY-TRACE-017

Diagnostic correction after TASK016.

TASK016 product fix is accepted as a real improvement:
- 55 exact adoptable orphan ownership rows in retained observe014;
- 0 conflicts;
- generic adoption tests 30/30, handoff 6/6, jar GREEN.

But the TASK016 live trace selected the wrong diagnostic cohort.

Current `PhantomHumanLocalityControl.onPulse()` calls:

```java
recorder.watch(profileId);
recorder.record(... online ? "ONLINE" : "OFFLINE" ...);
return online;
```

inside the topology eligibility predicate.

Therefore OFFLINE topology probes consume the recorder's first-eight watch slots before
accepted human-local ONLINE candidates are known.

TASK016 evidence simultaneously shows:
- all 392 watched LOCAL_CANDIDATE samples were OFFLINE;
- each HUMAN_REFRESH_SUMMARY had humanCount=1;
- final localCandidateCount=9;
- worldPhantomCount=0.

So `READY_PRESENCE_OFFLINE` for watched profile110 is true for that profile, but is NOT
proof that the 9 accepted human-local candidates hit the same boundary.

TASK017 fixes only observability and reruns the same causal path.
No gameplay/materialization semantic fix.
