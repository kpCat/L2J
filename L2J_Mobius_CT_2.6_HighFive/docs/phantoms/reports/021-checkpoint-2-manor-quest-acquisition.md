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
- near-final foundation и required parent terminal completion:
  `81e4d2a7044f8c1bafc7db6b5d3c66ce4df050aa`;
- terminal foundation: `906b8a043320deb955da02276cf27797e0c5fadd`, subject:
  `fix(phantoms): close manor attribution and quest service recovery`.
- final exact-delta child subject:
  `fix(phantoms): enforce exact quest callback item delta`.

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
- Независимый crop delta между Combat и Harvester записывается только generic
  `VERIFY` receipt и одной state/Goal mutation обновляет overall count/progress и
  handler binding baseline. Успешный handler получает отдельный
  `ACTIVE_MANOR_HARVEST` receipt строго от refreshed baseline.
- Drift до handler не вызывает Harvester, уменьшение inventory fail closed, а
  completion внешним delta очищает phase/target без handler call.
- `QUEST_COLLECTION` ограничен двумя source-hashed rules для уже запущенных
  Q00102 и Q00152 и тремя exact target NPC: `20013`, `20019`, `20016`.
- Active quest path использует existing Combat и реальный delayed
  `OnAttackableKill`; `Quest.onKill` вручную не вызывается.
- Full-service active gate проходит реальный planner-selected lifecycle для
  Q00102/NPC 20013, Q00102/NPC 20019 и Q00152/NPC 20016 через
  `PhantomAcquisitionService.activeAdvance`, exact acquisition Combat owner и
  persisted restarts в `QUEST_COMBAT_SUBMITTED`, `QUEST_COMBAT_TERMINAL` и
  `QUEST_CALLBACK_WAIT`.
- Callback deadline хранится как absolute epoch milliseconds через injected
  `LongSupplier`; restart его не сбрасывает, clock rollback/forward и legacy
  monotonic values завершаются bounded timeout, а уже появившийся item проверяется
  до deadline.
- Callback grant принимается только при exact current rule/state/cond/vars,
  `after <= cap` и delta в `minimumCount..maximumCount`; для Q00102/Q00152 это
  строго `+1`. Decrease, `+2` и cap violation fail closed без receipt/progress.
- Dedicated quest observation одной atomic state/Goal mutation создаёт
  `ACTIVE_QUEST_COLLECTION` receipt от `itemCountBeforeKill`, обновляет progress и
  сразу завершает Goal либо возвращает `TARGET_REQUIRED` без старого target.
- Quest Combat submission повторно проверяет exact item baseline/cap под actor
  lease. Legacy `QUEST_COLLECTION/VERIFYING` использует тот же exact validator;
  successful callback больше не создаёт промежуточный `VERIFYING`.
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

- manor catalog/source: `3/3`, active: `2/2`, background: `3/3`, restart: `3/3`;
- quest active: `3/3`, background: `3/3`;
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

Pre-commit terminal последовательность завершена без изменения frozen
production/data/test/build/verifier artifacts:

- verifier 021c1: PASS в PowerShell 5.1 и 7.6.3;
- verifier 021c2 working: PASS в PowerShell 5.1 и 7.6.3 с одинаковыми scope/hash
  значениями;
- combined affected batch, включая Goal 015 и полный Checkpoint 1 aggregate:
  `BUILD SUCCESSFUL` за 9:17;
- ровно один final `phantom-acquisition-checkpoint2-test`:
  `BUILD SUCCESSFUL` за 2:45;
- ровно один plain `ant verify` без global seed override:
  `BUILD SUCCESSFUL` за 13:27;
- standalone `ant jar`: `BUILD SUCCESSFUL` за 16 секунд;
- SHA-256 frozen build/authority/production/test/verifier artifacts до и после
  plain verify byte-identical.

После report-only terminal update остаются ordinary commit/push и два
byte-identical accepted verifier 021c2 runs.

Exact-delta focused evidence: `compile-tests` и
`phantom-acquisition-quest-active-test` прошли; full-service suite `4/4` покрыла
обе curated rules, real delayed callback, partial/completion, `+2`, cap, decrease,
pre-combat drift, legacy `VERIFYING`, state/cond/vars identity и zero claims.
Первый focused запуск также прошёл все `4/4`, но after-all обнаружил test-only
повторный `setPlayerClass`, оставивший `_skillListTask`; setup сделан идемпотентным,
повторный focused route завершился полностью зелёным.

Final exact-delta terminal sequence:

- historical verifier 021c1: PASS в PowerShell 5.1/7.6.3;
- working verifier 021c2: PASS в PowerShell 5.1/7.6.3, одинаковые
  `53/28` cumulative и `5/1/0` final scope/hash outputs;
- manor active + полный Checkpoint 1 affected batch: `BUILD SUCCESSFUL`, 6:31;
- единственный final `phantom-acquisition-checkpoint2-test`:
  `BUILD SUCCESSFUL`, 2:53;
- единственный plain `ant verify`: `BUILD SUCCESSFUL`, 13:31;
- standalone `ant jar`: `BUILD SUCCESSFUL`, 18 секунд; service class присутствует;
- frozen build/service/test/verifier/catalog/topology SHA-256 до и после terminal
  verify/jar byte-identical.

После этого report-only update остаются ordinary commit/push и два byte-identical
accepted verifier 021c2 runs.

Финальный handoff содержит фактические результаты terminal команд; verifier output
является детерминированным acceptance artifact.

## Scope и ограничения

- terminal foundation child: `2` production/data/config и `7` total;
- final exact-delta child: `1` production и `5` total, new production `0`;
- new production/data: `0`;
- cumulative Checkpoint 2: `28` production/data/config и `53` unique total;
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
- Exact-delta commit/push выполняются одним ordinary child
  `906b8a043320deb955da02276cf27797e0c5fadd` без amend/rebase/squash/merge/reset
  и без force push/force-with-lease.
- Разрешённые git-команды используются для обязательных baseline, scope, diff,
  commit и push checks этой задачи.
