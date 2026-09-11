# Resume-7 evidence reuse contract

## Reason

Resume 7 already spent roughly 84 minutes after focused lineage proving:

- Goal039 final-domain aggregate;
- Goal029 scale/environment/endurance;
- Goal030 rollback/release.

Those gates all PASS on the exact currently published parent:

`539688cda76c06bf48528f210cbff03524818871`

Resume 8 exists to correct the release-candidate environment, not to re-prove
unchanged domain behavior before every environment experiment.

## Reuse precondition

Before reusing Resume-7 domain/scale/rollback evidence, prove that from
`539688cda76c06bf48528f210cbff03524818871` to the pre-verify Resume-8 source overlay:

- production Java changed files: 0;
- production config/data changed files: 0;
- `build.xml` change: 0;
- SQL/schema changes: 0;
- Goal029 suite/targets changed files: 0;
- Goal030 rollback/release suite/targets changed files: 0;
- Goal033–038 domain implementation/test owner changes: 0.

Allowed pre-verify tracked source difference:

- `PhantomFullVisionGoal039Suite.java` — provenance constant only.

Task package files are operational docs and do not invalidate domain evidence.

If any other relevant source/test/build owner changed, do NOT reuse blindly;
classify why it changed.

## Evidence to carry forward

From the published Resume-7 report:

- final-domain aggregate: PASS / 50m21s;
- Goal029 scale/environment/endurance: PASS / 32m01s;
- Goal030 rollback/release-control: PASS / 1m56s.

Record these as:

`REUSED_EXACT_PARENT_EVIDENCE`

not as a new run.

## What must still be fresh in Resume 8

Resume 8 MUST freshly run:

1. candidate qualification canaries;
2. one successful full `ant verify`;
3. standalone final `ant -q jar`;
4. fresh Goal034 real local-stack acceptance using that exact final JAR;
5. final Goal039 documentation/freeze validator.

These are exactly the unfinished release gates.

## Invalidation

Any production Java/config/data/build change during Resume 8 invalidates this
reuse and is outside expected scope.

If such a change becomes necessary because of a proven new product defect, stop
and publish BLOCKED rather than silently extending Resume 8.
