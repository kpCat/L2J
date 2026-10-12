# SOURCE → READ → COMMIT → PUBLISH039

Existing owning writer: PhantomBackgroundTransaction. Historical FINALIZED hook exports
intent.after plus later SQL; it lacks the actual completed row/PNC committed result.
No retrospective038 producer is asserted. Historical UNKNOWN remains UNKNOWN.

|Source|Already owned facts|Commit boundary|Optional publication|
|---|---|---|---|
|prepareOwnedStore|locked component/context, canonical, FULL items, skills, immutable intent|existing connection.commit before AFTER_PREPARE fault|PREPARE state/PNC increments and exact receipt/epoch; canonicalChecked=false for intended AFTER|
|resolveOwnedStore via finalize/resume/recovery|locked receipt/state/context/canonical/items/skills; existing after/before comparisons and chosen completed state|calling transaction's existing successful commit|FINALIZE/RECOVERY full completed payload, exact canonical comparison facts and resulting PNC|
|captureBaseline/markMaterialized/abort/reconcile|existing locked reads and existing validation|existing successful commit|state/PNC version/hash transitions; checked facts only where existing comparison succeeded|
|execute/background policy mutations|existing state write and VERIFY_PENDING; separate verification transaction|each existing successful commit|separate mutation and VERIFY promotion witnesses, never collapse two increments|

Proposed bounded observer-only seam: private optional Consumer of immutable scalar Map;
transaction-local scope captures existing encoded writes, clears on rollback/exit and publishes
only after the actual JDBC commit returns. Nested verification has its own scope. Scope holds
no Player/Connection for exporter. Disabled observer incurs no payload/digest capture.
Collector adds DB/boot/run/Jar identity and uses existing bounded512 ring; drop stays proof GAP.
No new SQL, locks, fsync, callback waits, schema, reward write or commit ordering.

Canonical encoder is existing state codec, inventory hash and ownedSkillsHash. Canonical
field comparisons use existing durableMatches/ownedCanonicalFactsMatch/canonicalVitals;
no new tolerance. Native epoch/receipt comes only from the actual owning intent.
Observer exceptions cannot convert successful commit into a business failure.

Required checks before acceptance: real native FINALIZE followed by another committed state
increment and late SQL; rollback emits no committed record; callback throw leaves commit
successful; missing/tampered/wrongepoch/drop proof rejected; current all-birth FULL_SAVE,
global drain and stopped consistent SQL separately. Structural checker alone proves no save.
