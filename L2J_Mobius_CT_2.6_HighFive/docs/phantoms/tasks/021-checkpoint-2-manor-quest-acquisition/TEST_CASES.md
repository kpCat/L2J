# Test matrix — Goal 021 Checkpoint 2

## Checkpoint 1 regression

- historical verifier 021c1;
- death/spoil/recipe source and ambiguity;
- schema 1/2 readers;
- background operation conservation;
- no regression to Goal 015 ordinary farm.

## Manor catalog/source

- static/runtime seed parity;
- crop-only direct product;
- mature/reward exclusion;
- manor enabled/disabled;
- target canBeSown and level;
- exact castle anchor;
- owned seed and harvester;
- candidate/ambiguity bounds;
- authority/source hash drift.

## Active manor

- real headless Player;
- real Seed item handler and Sow effect;
- real Harvester item handler and Harvesting effect;
- exact item object/target/seeder;
- seed consumed on attempt;
- successful/failed sow;
- real Combat kill;
- successful/failed harvest;
- crop delta and no mature/reward item;
- wrong castle/dead/raid/chest/instance/owner;
- sow and harvest crash windows;
- no blind duplicate;
- zero retained claims.

## Background manor

- exact current formulas and boundaries;
- alternative seed;
- level penalties;
- strong-type multiplier;
- manor rate;
- seed depletion;
- sow/harvest retries;
- ordinary drops separate;
- item capacity;
- fault at every DB write;
- exact replay/source switch;
- active/background transition conservation.

## Quest catalog audit

- 2–4 real scripts and <=8 rules accepted;
- <=12 scripts inspected;
- every rejection reason documented;
- strict source hash and refs;
- hidden state/var/timer/party/global effects rejected;
- no arbitrary reflection/interpreter.

## Active quest

For every supported rule:

- create an exact already-started QuestState in test setup;
- kill a real exact target through Combat;
- actual OnAttackableKill and real loaded script;
- callback wait;
- item/no-item/cap;
- wrong state/cond/NPC/summon/script hash;
- no acquisition call to onKill;
- no acquisition quest mutation/start/turn-in.

## Background quest

For every supported rule:

- exact locked quest rows;
- exact deterministic formula;
- grant/no-grant/cap;
- quest rows byte-identical;
- item/background/Goal/acquisition atomicity;
- ordinary rewards;
- fault injection;
- replay and source/rule drift;
- subclass and profile mismatch;
- active/background item conservation.

## Lifecycle/performance

- 100k manor plans;
- 100k quest plans;
- 10k manor background operations;
- 10k quest background operations;
- no runtime script scan;
- no workers/tasks;
- shutdown at every external/callback/DB boundary.
