# Engineering self-review — TASK020

Reviewed CONTRACT F01–F14, TESTS V01–V18, complete source diff and focused regression summaries. Self-review follows the requesting-code-review checklist; user forbids subagents.
Only HistoricalBackgroundService and PhantomSystem have semantic changes; LocalPlayPilotActions adds the existing read-only census. Store decision fence, permitsNormalOperation, catchup state/goal, DecisionEngine internals, native execution and schema unchanged.
Admission retains the exact immutable catchup Snapshot, request, component, goal and successful character/epoch in memory. Checks require current exact components, ACTIVE/world/action admission, cleanup NONE/dematerializedAt0, canonical MATERIALIZED and profile/object identity. Per-profile compute serializes publication against revocation. Stop revokes transient records and prevents later publication; native cleanup still follows existing lifecycle.
beforeStore removes foreground ownership before existing cleanup/store validation; abort removes matching character ownership. No durable permit writes. Ordinary absent/COMPLETE catchup behavior unchanged. Historical baseline never publishes an active foreground record.
V17 exercises SNAPSHOT_PHANTOMS includeCensus against a real Player and asserts XYZ/instance/target/moving/teleporting unchanged, censusCount present, default unchanged.
No Critical/Important findings remain. Runtime same-epoch native farm and TestAdmin observation/logout are pending manual gates. NATIVE_WORK_SELF_DRAIN is outside this task and was not repaired.
