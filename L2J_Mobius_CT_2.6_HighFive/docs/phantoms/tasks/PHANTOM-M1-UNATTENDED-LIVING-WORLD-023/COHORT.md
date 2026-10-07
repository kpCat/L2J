# Полная final cohort

Frozen production SHA1b71464a9e09a80dad890e2bfd39df0f3b5d3b96; config/data fingerprint
48772DE9C4034EA475F9D3ECDD7258EFD770956899D63368503A25DAF787AE27,302 files одинаковые A/B/final.
ActorMode=Synthetic, ordinary clone TestAdmin, LOCALPLAY_TEST_HUMAN, GameClient none.
SceneA386.960607s,37 samples, first2 eligible primary fixed before baseline; никто не исключён.

| Profile | Primary | Cycles | Rewards | Targets | EXP counter delta | SP counter delta | Max idle seconds | Tail120 rewards | PASS |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
|281|yes|13|14|18|834|92|269.78|0|false|
|411|yes|2|2|6|174|20|354.99|0|false|
|459|no|12|12|15|725|82|269.79|0|false|
|663|no|7|7|10|520|57|344.38|0|false|
|1176|no|7|8|7|4200|200|248.17|0|false|

EXP/SP в этой таблице — raw native evidence counters. Они не приравнены к canonical SQL
delta; actual live/saved totals сопоставлены отдельно в LIFECYCLE_EVIDENCE.md.

281 отсутствовал в одном visible sample, вернулся с прежним epoch. Это не скрыто и не
признано непрерывным PASS.411/663 имеют native incident;663 также PHASE_DEADLINE overflow.
У остальных bounded LOCAL_FARM_UNAVAILABLE/travel.journey_deadline; idle не был прогрессом.
Natural loot у281/459; baseline/final native inventory SELECT сохранены.

Raw: evidence/FINAL_SCENE_A/{baseline-cohort,primary,all-samples,cohort-result,result}.json,
все request JSON/XML и raw inventory TSV. Provided evaluator independently returns false;
missing actor/incidents/final120s retained. Его13 focused self-checks PASS — это проверка
evaluator, не runtime gate. Normalize-Evidence023 не фильтрует source actors/samples.

SceneB: один setup TELEPORT_SELF в factual oren02_2119_07s,42400/41700/stock grounded Z.
60s warmup: только1 eligible/1 total. No second preselected pair or complete360s scene;
FARM_B=false/ENVIRONMENT_GAP. Все raw snapshots и Synthetic STOP PASS сохранены.
No producer edits between A/B. Не делалась новая удачная выборка взамен failed cohort.

Предварительный PROBE_B на afdf SHA сохранён со всей8 cohort, включая idle; он не final
acceptance и использован только как causal RED. Его результат не объединён с frozen A/B.
