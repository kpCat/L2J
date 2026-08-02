# Goal 021 Checkpoint 2 — manor/quest acquisition

## Status

`COMPLETED_PENDING_INDEPENDENT_REVIEW`

Это второй и последний заранее запланированный checkpoint Goal 021. Реализовано
финальное bounded покрытие feasible territories; Goal 022–027 не начинались.

## История

- accepted Checkpoint 1: `0045f60417f4605f46e3058b9a694278283b1456`;
- blocked Checkpoint 2 foundation: `365c014a48c7998eb880352b00503a28b2f27a2c`;
- loaded-boundary audit: `130a08a90c729dd94c13d782416bc0f1f727e6c7`;
- anchor-feasibility audit и required parent:
  `83b22f2338c297151a9b0881fdf566963ee5d571`;
- final ordinary child subject:
  `fix(phantoms): finalize feasible manor quest acquisition`.

Checkpoint 1 зафиксирован как `ACCEPT`. Verifier 021c1 читает исторические blobs
accepted commit и остаётся descendant-compatible.

## Результат

- `MANOR_CROP` использует только штатную цепочку Seed/Sow → existing Combat →
  Harvester/Harvesting.
- Schema-3 method binding, persisted dispatch/recovery и active/background formula
  parity сохранены.
- Background attempts берутся из `PhantomAcquisitionCatalog.Limits`; literals
  `3, 3` удалены из формулы, вариант `2/2` покрыт тестом.
- Pre-harvest crop baseline обновляется после terminal Combat под exact actor lease,
  сохраняется вместе с `HARVEST_PREPARED` до release и повторно проверяется перед
  вызовом Harvester.
- `QUEST_COLLECTION` ограничен двумя source-hashed rules для уже запущенных
  Q00102 и Q00152 и тремя exact target NPC: `20013`, `20019`, `20016`.
- Active quest path использует existing Combat и реальный delayed
  `OnAttackableKill`; `Quest.onKill` вручную не вызывается.
- Background quest path выполняет exact `character_quests` row validation под
  `FOR UPDATE` и одну atomic item/background/Goal/acquisition transaction.
- Quests не запускаются и не сдаются; `state`, `cond`, `vars` не изменяются.
- Прямые `setSeeded`, `takeHarvest`, `addItem`, `destroyItem` в production C2 paths
  отсутствуют. Crop procurement/reward exchange не добавлялись.

## Loaded territory boundary

`NpcSpawnTerritory.GeometrySnapshot` публикует immutable source-authoritative
копию уже загруженной polygon geometry: canonical relative source path, main и
bounded banned polygons, Z bounds и canonical SHA-256. Mutable `ZoneForm`,
`Polygon`, arrays и lists наружу не выдаются. Unsupported/legacy forms fail closed.

`SpawnData` передаёт полный relative datapack path. Production Game Knowledge
использует loaded snapshot без повторного XML parse и без reflection. `Spawn.java`
не изменялся.

## Feasible coverage

Factual loader inventory сохранён полностью:

| NPC | Territory occurrences | Configured amount | Feasible occurrences |
| --- | ---: | ---: | ---: |
| 20013 | 20 | 50 | 9 |
| 20019 | 17 | 49 | 7 |
| 20016 | 8 | 27 | 1 |

- unique factual territory identities: `35`;
- mapped feasible territories: `15`;
- unmapped distance-infeasible territories: `20`;
- unsupported target facts: `0`.

Все 35 identities остаются immutable Game Knowledge facts. Ровно 15 factual
polygons получили общие NPC-anonymous FARMING nodes/anchors. Для 20 too-wide
polygons не создавались invented anchors, split/merge geometry или expanded bounds.
Новых topology edges нет. `activeTargetDistance=2000` не изменён.

Mapping требует exact polygon с rotation/reverse equivalence, exact Z, instance,
source ref, единственный node и feasible anchor. Partial overlap, wrong Z/source,
duplicate node, exact-point Spawn и unsupported territory fail closed. Mapped
source сохраняет evidence о дополнительных unmapped natural territories.

## Exact target ownership

Active manor/quest target принимается только при совпадении NPC, instance,
source path, territory name и geometry hash выбранного factual source. Один NPC из
другой mapped territory, из unmapped territory и из exact-point Spawn отвергается.
Object replacement требует нового target claim. Exact source identity сохраняет
ownership при допустимом wandering внутри обычной live target validity.

## Quest audit

Аудировано ровно 12 уже существующих scripts. Приняты только:

1. `Q00102_SeaOfSporesFever` — NPC `20013/20019`, item `966`, STARTED/cond 2,
   cap 9, source SHA-256
   `cc3c1a893e6fe0763b806a17aa01e1d59a4c3f4743c3a577b2597bec07978d1f`;
2. `Q00152_ShardsOfGolem` — NPC `20016`, item `1010`, STARTED/cond 2, cap 4,
   source SHA-256
   `e086d06935b0515142f431486ded1f71b8caa4843f69605296e64a4e8ffdf378`.

Остальные десять отклонены из-за cond/vars/party/completion/дополнительных item
side effects. Generic quest interpreter не создавался; quest scripts не менялись.

## DB, schema и config

- Используется только `l2jmobiush5_phantom_test`.
- Seed: `phantom.goal021c2.seed=21002102`.
- Schema/migrations отсутствуют.
- Phantom World остаётся disabled by default.
- Production DB не использовалась.

## Проверки

До freeze выполнены focused проверки loaded topology/Game Knowledge, source
planners, active manor, active Q00102/Q00152, manor background/restart и
catalog-driven policy variant. Active tests доказали grant/no-grant branches и
negative source ownership controls. Итоги C2 focused routes:

- manor catalog/source: `3/3`, active: `2/2`, background: `3/3`, restart: `2/2`;
- quest active: `2/2`, background: `3/3`;
- checkpoint2 lifecycle/performance: `5/5`;
- все combined focused команды завершились `BUILD SUCCESSFUL` после устранения
  bounded async flake штатного Seed/Sow handler path.

Affected Goal 009/010/011 прошли. Goal 015 production audit был обновлён с одного
FARMING anchor до точного корпуса `16` (`1` прежний explicit + `15` новых
anonymous), сохранив exact проверку прежней production pair; полный Goal 015
aggregate прошёл. Schema-3 потребовала двух descendant-compatible обновлений C1
tests: exact `none` method-binding hash/resource count в atomic fixture и bounded
лимит `<=33` declared state fields при неизменном payload envelope `<=4096`.
После этого полный `phantom-acquisition-checkpoint1-test`, Decision core `35/35`,
Decision persistence `23/23` и shutdown handoff `7/7` завершились
`BUILD SUCCESSFUL`.

Обязательная terminal последовательность выполняется без изменения frozen
production/data/test/build/verifier artifacts:

1. verifier 021c1 в PowerShell 5.1 и 7.x;
2. verifier 021c2 working в PowerShell 5.1 и 7.x;
3. affected Goal 009/010/011/015/021c1 regressions;
4. ровно один final `phantom-acquisition-checkpoint2-test`;
5. ровно один plain `ant verify`;
6. standalone `ant jar`;
7. ordinary commit/push;
8. два byte-identical accepted verifier 021c2 runs.

Финальный handoff содержит фактические результаты terminal команд; verifier output
является детерминированным acceptance artifact.

## Scope и ограничения

- final child: `15` production/data/config и `24` total;
- new production/data: `0`;
- cumulative Checkpoint 2: `28` production/data/config и `52` unique total;
- не изменены `Player.java`, `Party.java`, `Attackable.java`, `Spawn.java`,
  `CastleManorManager`, handlers, quest scripts и schema;
- не добавлены worker/thread/executor/Future/task;
- другие хроники не затронуты.

## Риски и следующий шаг

Двадцать too-wide territories намеренно недоступны source planner при текущем
distance contract; это bounded partial coverage, а не потеря factual knowledge.
Следующий шаг — только независимый review этого checkpoint. Goal 022+ до принятия
gate не начинать.

## Git

- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- Commit/push выполняются одним ordinary child без amend/rebase/squash/merge/reset
  и без force push/force-with-lease.
- Разрешённые git-команды используются для обязательных baseline, scope, diff,
  commit и push checks этой задачи.
