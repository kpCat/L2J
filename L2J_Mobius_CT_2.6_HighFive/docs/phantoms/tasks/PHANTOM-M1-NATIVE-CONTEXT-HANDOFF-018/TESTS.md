# TESTS

Required deterministic contracts:

H01 NORMAL + non-COMPLETE catchup remains catchup.normal_fenced.

H02 ordinary ecology COMPLETE due returns NORMAL request with empty claim.

H03 exact ecology.native_materialization_required returns
NATIVE_CONTEXT_HANDOFF + exact historical requestId.

H04 no demand / request mismatch / window mismatch / COMPLETE history / terminal /
not-ready => no handoff claim.

H05 NATIVE_CONTEXT_HANDOFF blank/wrong request claim rejected before Player.load.

H06 exact claim with non-COMPLETE catchup passes HistoricalBackground admission and
Background historical admission.

H07 catchup rowVersion/payload changes after admission => fail closed, no World spawn.

H08 goal component changes after admission => fail closed.

H09 Background afterPlayerLoad under exact handoff uses current native owner/epoch and
existing native-context attestation; no reward/progress/inventory fabrication.

H10 abort releases HistoricalBackground and Background admissions.

H11 successful handoff does NOT mark catchup COMPLETE, does NOT advance historical
interval/cursor, and does NOT rewrite requestId.

H12 normal materialization without catchup unchanged.

H13 HISTORICAL_BASELINE behavior unchanged.

H14 recorder emits bounded lifecycle admission subreason on rejection.

Regression gates:
- ecology suite 30/30;
- population-ecology-handoff 6/6;
- runtime-flight-recorder 3/3;
- affected native lifecycle/materialization focused suite(s);
- ant -q jar GREEN.

Prefer a focused integrated suite using existing PhantomHeadlessPlayerTestEnvironment
rather than mocks that skip lifecycle composition.
