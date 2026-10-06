# ACCEPTANCE

TASK017 GREEN = trustworthy human-local diagnosis.

Required:
- diagnostic watch bug corrected;
- OFFLINE topology probes cannot fill watch slots;
- candidate semantics unchanged;
- tests GREEN;
- jar GREEN;
- real TestAdmin trace;
- >=1 watched accepted ONLINE/local profile;
- exact accepted-path FIRST_LOST_EDGE identified;
- no gameplay/materialization/persistence semantic fix;
- user logout/save verified;
- graceful stop.

Possible high-water outcomes:
A. `MAT_WORLD_SPAWN` — next task is visible farm observation.
B. `MATERIALIZE_CALL` but later failure — next task targets exact post-call edge.
C. readiness/scheduler blocker — next task targets that exact accepted profile edge.
D. no human.local accepted signal despite local candidate — signal admission is blocker.

Do not classify unrelated `background.native_context` on OFFLINE profiles as the
human-local blocker.

M1=OPEN.
