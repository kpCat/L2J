# Goal 033A1 — Canonical population topology ingress

Дата: 2026-09-05
Результат: `SUCCESS`

## Status

Bounded prerequisite Goal033A1 завершён. Все authoritative creation positions, доступные managed population, представлены exact instance-zero ingress anchors и имеют factual `BACKGROUND` routes к real-spawn low-level `FARMING` anchors. Blocker `BLOCKED_033A_CANONICAL_TOPOLOGY_INGRESS_REQUIRED` закрыт Goal033A1.

Исторические результаты не переписаны: Goal033 остаётся `BLOCKED_033_CAUSAL_CATCHUP_ENTRYPOINT_REQUIRED`, Goal033A остаётся исторически `BLOCKED_033A_CANONICAL_TOPOLOGY_INGRESS_REQUIRED`. Planner/cursor/fence Goal033A и ecology Goal033 не реализовывались. Следующее действие — повторить Goal033A.

## Exact baseline/branch

- Модуль: `L2J_Mobius_CT_2.6_HighFive`.
- Ветка: `feature/phantom-world`.
- Exact required parent: `de457f01116ee42aeb06a8bc4a3b9fd8ac58f6a4`.
- До изменений `HEAD` и `origin/feature/phantom-world` совпадали с exact parent.
- Divergence отсутствовал; reset/rebase/force-push не использовались.

## Read-first audit

До изменений прочитаны:

- `Agents.md`, master plan, Roadmap, current status, reports Goal033/033A и полный Goal033A1 task package;
- `PhantomPopulationCatalog`, `PhantomPopulationStore`, `PlayerCreationInitializer`, `PlayerTemplateData`, StartingClass templates и `high-five-population-v1.xml`;
- topology loader/model/query, production validation backend, `L2jPhantomBackgroundAuthority` и Background travel contracts;
- семь High Five spawn corpora, `RandomSpawns.ini`, GeoEngine/Navigation и ближайшие production-composed test/DB-guard аналоги.

Root `README.md`, `docs/phantoms/CONTEXT_INDEX.md` и `docs/phantoms/DEVELOPMENT_CHAT_HANDOFF.md` в модуле не найдены; повторный поиск не выполнялся.

Переиспользован локальный паттерн: versioned immutable topology data с source evidence, exact anchors, one-way `backgroundEligible` edges, production Navigation validation, production-corpus assertions, launcher и guarded Ant target. Новые зависимости, runtime worker/timer, production Java layer и второй Player lifecycle не добавлялись.

## Creation-position inventory

| Population group | Class IDs | Positions | Exact ingress |
|---|---:|---:|---:|
| human-fighter | 0 | 4 | 4 |
| human-mystic | 10 | 4 | 4 |
| elf | 18, 25 | 6 | 6 |
| dark-elf | 31, 38 | 6 | 6 |
| orc | 44, 49 | 6 | 6 |
| dwarf | 53 | 6 | 6 |
| kamael | 123, 124 | 6 | 6 |
| **Итого** | **11 classes** | **38** | **38** |

Inventory получен из текущих StartingClass templates через production `PlayerTemplateData`/`PlayerCreationInitializer`. Raw и canonical coordinates записаны построчно в evidence manifest; creation points и initializer не изменялись.

## Authoritative source evidence

Creation sources — 11 существующих `data/stats/players/templates/StartingClass/*.xml`. Farming suitability подтверждена production `NpcData`/`SpawnData`: каждый NPC существует, attackable/targetable и относится к level-1/early corpus.

## Farming destination matrix

| Group | NPC | Spawn source | FARMING anchor |
|---|---:|---|---|
| human-fighter | 20545 | `data/spawns/Others/17_25.xml` | `population.farming.human-fighter.20545` |
| human-mystic | 20481 | `data/spawns/TalkingIsland/TalkingIslandMonsters.xml` | `population.farming.human-mystic.20481` |
| elf | 20534 | `data/spawns/ElvenTerritory/ElvenStarting.xml` | `population.farming.elf.20534` |
| dark-elf | 20529 | `data/spawns/DarkElfTerritory/DarkElfStarting.xml` | `population.farming.dark-elf.20529` |
| orc | 20535 | `data/spawns/OrcTerritory/OrcStarting.xml` | `population.farming.orc.20535` |
| dwarf | 20533 | `data/spawns/DwarvenTerritory/DwarvenStarting.xml` | `population.farming.dwarf.20533` |
| kamael | 22228 | `data/spawns/Kamael/IsleOfSouls.xml` | `population.farming.kamael.22228` |

`RandomSpawns.ini` разрешает fixed monster spawns 20545/22228 смещаться по X/Y до ±150. Поэтому только эти farming node/anchor используют evidence-backed radius/tolerance `213 = ceil(sqrt(150² + 150²))`; ingress остаются exact с tolerance `0`.
## Route evidence matrix

Все route sequences и SHA-256 evidence записаны в manifest. Hash строится из ordered edge ID, exact from/to coordinates и `ceil(validated3dDistance)`. Стоимость и время следуют существующим формулам `ceil(distance/1000)` и `ceil(distance/minGroupBaseRunSpeed*1000)`.

| Group | Ingress routes | Segments per route | Unique factual edges | Navigation result |
|---|---:|---:|---:|---|
| human-fighter | 4 | 4–6 | 10 | DIRECT_VALIDATED |
| human-mystic | 4 | 5 | 10 | DIRECT_VALIDATED |
| elf | 6 | 1 | 6 | DIRECT_VALIDATED |
| dark-elf | 6 | 4 | 9 | DIRECT_VALIDATED |
| orc | 6 | 5 | 10 | DIRECT_VALIDATED |
| dwarf | 6 | 5–8 | 14 | DIRECT_VALIDATED |
| kamael | 6 | 8–9 | 21 | DIRECT_VALIDATED |
| **Итого** | **38** |  | **80** | **80/80** |

Production Navigation выявил пять непрямых Dwarf ingress-сегментов. Они не были приняты как invented direct edges: topology split выполнен по возвращённым production waypoints `108552,-174024,-408`, `108616,-174152,-408`, `108728,-173944,-408`, `110440,-173944,-528`. После split каждый committed segment возвращает `DIRECT_VALIDATED`; Dwarf `i01` и downstream route не менялись.

## Topology data changes/dataset version

- `high-five-core.xml` поднят до `datasetVersion=3`.
- Corpus: 110 nodes, 110 anchors, 83 edges.
- Population addition: 38 exact ingress anchors, 7 farming destinations, factual route anchors и 80 one-way `BACKGROUND` edges.
- Все population edges имеют `backgroundEligible=true`, `bidirectional=false` и существующие Background channels.
- Public schema, production Java, creation templates и shipped Phantom config не менялись.

## Exact-anchor evidence

Focused suite создаёт managed profiles обычным `PhantomPopulationStore.createShell()`/`advanceCreation()` до покрытия всех семи групп, materializes level-1 `Player` без autosave и подтверждает:

- Player coordinates равны сохранённым production creation coordinates;
- production `L2jPhantomBackgroundAuthority.exactAnchor()` однозначно возвращает manifest ingress;
- все 38 ingress anchors instance-zero и tolerance `0`;
- negative fixtures fail closed при missing/duplicate ingress, изменённой creation location, fake source/route и non-Background edge.

Coordinates не менялись через `setXYZ`, teleport или Phantom-specific creation override. `exactAnchor()` не ослаблялся.

## Travel evidence

`PhantomTopologyQuery.routeHint(ingress, farming)` проверен для всех 38 manifest rows. Production `advanceTravel()` прошёл полный route для materialized representative каждой группы, достиг exact farming anchor, сохранил progress/vitals/inventory и дал одинаковый результат после deterministic restart. Direct teleport и второй Player lifecycle отсутствуют.

## Changed files

Bounded exception: 11 exact Goal033A1 paths.

- `dist/game/data/phantoms/topology/high-five-core.xml`;
- `test/resources/phantoms/topology/goal033a1-population-ingress.tsv`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomGoal033A1TopologyIngressSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`;
- `build.xml`;
- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`;
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`;
- `docs/phantoms/reports/033A1-canonical-population-topology-ingress.md`.
## Tests/results

- `compile-tests`: PASS; 132 test sources, только 2 существующих deprecation warnings `System.runFinalization()` вне scope.
- `phantom-canonical-population-topology-ingress-goal033a1-test`: PASS, 4/4.
- `phantom-topology-production-corpus-test`: PASS, 7/7.
- `phantom-background-production-audit-test`: PASS, 1/1.
- `phantom-background-position-canonicalization-test`: PASS, 2/2.
- `phantom-navigation-core-test`: PASS, 50/50.
- `phantom-topology-core-test`: PASS, 38/38.
- Goal032 ownership/reseed: PASS, 3/3 + 2/2.
- Goal031 readiness: PASS, 3/3.
- Goal030 CP3 restart/rollback: PASS, 3/3 + 3/3.
- Финальный `ant jar`: PASS, выполнен ровно один раз после focused gates.

## Production DB statement

Production DB `l2jmobiush5` не использовалась. Все DB-mutating production-composed suites запускались только через существующие schema/config guards против allowlisted `l2jmobiush5_phantom_test`. Goal033A1 cleanup удалял только exact profile/character/account IDs, созданные самим suite; broad cleanup не выполнялся.

## Known limitations

- Goal033A planner, initial capture orchestration, exactly-once historical cursor и materialization fence не реализованы.
- Goal033 ecology не реализована; historical blocker сохраняется.
- Route evidence относится к текущему High Five data/geodata/config corpus; изменение этих источников должно fail closed по manifest/corpus tests.
- Ручной игровой UX не проверялся и не является техническим gate этой bounded prerequisite.

## Commit/push

- Exact Goal033A1 paths включаются в один commit `phantom(goal-033a1): add canonical population topology ingress`.
- Push target: `origin feature/phantom-world`.
- TASK-authorized Git ограничен baseline fetch/rev-parse/status, final diff/scope checks, exact-path stage, commit и non-force push. Reset/rebase/force-push и unrelated untracked task packages не использовались.

## Next action

Повторить Goal033A с нового `origin/feature/phantom-world`. Не начинать Goal033/034 до результата повторного Goal033A gate.

```text
Population creation coverage: 38/38
Unique canonical ingress for every creation point: YES
Real low-level farming destination for every ingress: YES
Evidence-backed BACKGROUND route for every ingress: YES
Creation coordinates changed: NO
Direct/invented teleport used: NO
Production DB used: NO
Goal033A topology blocker closed: YES
Next: repeat Goal033A
```
