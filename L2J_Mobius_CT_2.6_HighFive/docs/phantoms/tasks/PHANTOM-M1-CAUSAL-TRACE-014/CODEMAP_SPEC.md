# M1 CRITICAL PATH CODEMAP

Map only the actual M1 critical path from High Five source.

Required chain:

A. REAL
`GameClient -> EnterWorld -> REAL_LOGIN -> World Player -> PhantomHumanLocalityControl`

B. Demand
`HumanLocality.onPulse -> topology/native candidate query -> physical/local demand -> relevance signal -> PhantomScheduler requested state -> local promotion`

C. Readiness
`PhantomScheduler -> PhantomReconcileFirstActivityPort -> presence -> locality -> ecology.requestMaterializationDue -> current locality`

D. Materialization
`PhantomMaterializationServiceActivityPort -> PhantomMaterializationService -> identity lease -> PhantomMaterializedPlayer -> Player.load -> lifecycle afterPlayerLoad -> online -> World.spawnMe -> action admission`

E. Visible farm
`Decision -> PhantomVisibleFarmTravel -> PhantomVisibleAutoPlay -> AutoPlayTaskManager -> AutoUseTaskManager -> PlayerAI -> native combat/reward/pickup`

F. Return
`locality lost -> demotion -> close admission -> drain -> owned store PREPARE/NATIVE/FINALIZE -> World removal -> identity release -> background`

For every edge record:
- exact file/class/method;
- caller -> callee;
- thread/executor;
- owner/lock;
- source of truth;
- state read/write;
- DB write if any;
- fail/defer reasons;
- task014 diagnostic event;
- GOOD-vs-current commits touching that edge.

No broad High Five encyclopedia.
