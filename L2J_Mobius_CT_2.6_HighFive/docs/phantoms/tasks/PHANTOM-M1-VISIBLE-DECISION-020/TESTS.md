# TESTS

Required RED/GREEN:
V01 pre-fix exact successful NATIVE_CONTEXT_HANDOFF + non-COMPLETE catchup:
     permitsNormalOperation=false and DecisionEngine work remains sequence0.
V02 same state post-fix: permitsNormalOperation=false, permitsDecision=true.
V03 non-COMPLETE catchup + NORMAL/no handoff => false.
V04 HISTORICAL_BASELINE temporary Player => false.
V05 wrong requestId => false.
V06 catchup row/payload changed => false.
V07 goal component changed => false.
V08 materialization absent/STORED/FAILED => false.
V09 worldPresent=false => false.
V10 actionAdmissionOpen=false => false.
V11 character/epoch mismatch => false.
V12 Background not MATERIALIZED/identity mismatch => false.
V13 beforeStore removes permit.
V14 materializeAborted removes permit.
V15 absent/COMPLETE catchup remains ordinary true.
V16 permit check mutates no durable component.
V17 SNAPSHOT_PHANTOMS includeCensus leaves TestAdmin XYZ/target/movement unchanged.
V18 exact handoff + ACTIVE work increments decisionSequence and can select the existing
    farm.background visible candidate.

Regressions:
- TASK018 H01-H14
- ecology30/30
- population-ecology-handoff6/6
- recorder3/3
- background lifecycle4/4
- affected DecisionEngine focused suite
- `ant -q jar`

Do not fix NATIVE_WORK_SELF_DRAIN here. Retain it for later lifecycle work.
