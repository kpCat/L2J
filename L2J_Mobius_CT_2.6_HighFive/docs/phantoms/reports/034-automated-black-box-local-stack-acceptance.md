# Goal034 — Automated black-box local stack acceptance

Дата: 2026-09-06
Статус: `BLOCKED`

## Итог

Goal034 не объявлен `SUCCESS`: обязательная последовательность `green ant verify → один final ant jar → real-process black-box` остановлена после исчерпания единственного разрешённого повтора полного `ant verify`. Первый run упал на некорректной negative fixture, повтор — на Java 8 script blocker. Обе root cause исправлены, оба упавших focused-target теперь green, но третий полный `verify` прямо запрещён efficiency standard.

Roadmap v4 применён. Goal035 не начат и остаётся `PLANNED`; следующий шаг — новый bounded closure Goal034 с новым разрешённым green `verify`.

Production DB `l2jmobiush5` не использовалась и даже не probe-илась. `ant prepare-phantom-test-db` не выполнялся. Разрешённая существующая DB: только `127.0.0.1:3308/l2jmobiush5_phantom_test`, user `l2j_phantom_test`.

## Baseline и safety/READ_SET audit

- Ветка: `feature/phantom-world`.
- Required parent, локальный `HEAD` и fetched `origin/feature/phantom-world`: `32ecbb5f159b9515c3b33f68788ac47747341d1c`.
- Initial READ_SET: 12/12 exact files; additional production files: 5/5.
- Repository discovery searches до первого patch: 6/6; последующие точечные root-cause/rate lookups: 7; всего 13 discovery `rg` invocations. Обязательные final marker scans считаются отдельно.
- Прочитаны обязательные policy/master/status/workflow/build/launcher/server lifecycle файлы, предыдущий Goal033 repeat report и Goal034 TASK/architecture/test-case/acceptance материалы.
- Переиспользованы локальные seams: `ProcessBuilder` test harness, `PhantomTestDatabaseGuard`, Goal032 `PhantomPopulationResetService`, native scheduled restart/`Shutdown`, существующие LoginServer/GameServer entrypoints.
- Writer audit охватил LS registration, GS login connection, population/background/progression/materialization/social/autosave и двойной shutdown drain.
- Ограничения: Java 8 datapack scripts, без permanent admin/test API, без wildcard kill, dynamic loopback ports, exact run-owned PID/process tree, exact owner cleanup.
- Непроверенным остался весь real-process acceptance, поскольку predecessor gate не стал green.

## Реализовано

- Новый test-side harness `PhantomBlackBoxLocalStackGoal034`: fail-closed DB allowlist до driver load/child spawn, run-id sandbox, dynamic ports, real LS/GS JVM orchestration, readiness/registration checks, native GS restart, generation continuity, bounded artifacts и exact cleanup.
- Sandbox копирует изменяемые login/config файлы, но переиспользует immutable canonical game data/scripts через абсолютные пути; working-source fingerprints проверяются между generation 1/2.
- Cleanup child инициализирует production `DatabaseFactory` только после повторного guard и вызывает production Goal032 reset service; registration row удаляется по exact server id/host.
- Добавлены Ant targets `phantom-black-box-local-stack-goal034-contract-test` и `phantom-black-box-local-stack-goal034-test`; actual target не вызывает destructive prepare.
- Contract покрывает production-DB rejection before spawn, exact sandbox property update и Roadmap v4 consistency.
- Исправлены только актуальные orchestration paths `C:\Users\endim\...` → `C:\Users\ZBook\...`; historical reports не переписывались.
- Bounded exception к обычному лимиту 8–10 файлов: TASK явно требует harness/build/report, четыре Roadmap v4 документа, три актуальных path-policy документа и допускает два доказанных direct blockers; независимые artifact families не добавлялись.

## Gates и telemetry

| Checkpoint | Результат | Evidence |
|---|---|---|
| Guarded preflight | PASS | 8/8, существующая test DB/schema/config пригодны; geodata warning не blocker |
| `ant compile-tests` | PASS | 2230 production + 136 test sources; только 2 existing `runFinalization` warnings |
| Goal034 contract | PASS | 8 запусков: 2 development, 4 промежуточных verifier после QA-правок, 2 final post-review; стабильно 3/3 |
| DB guard negative control | PASS | 1/1; expected exit 2; `driverLoads=0`, `connectionAttempts=0` |
| `ant verify` run 1 | FAIL | `decision-persistence` 22/23; negative fixture использовала допустимую current schema version `2` |
| Focused decision regression | PASS после fix | unknown-version fixture `2 → 3`; 23/23 |
| `ant verify` единственный repeat | FAIL | datapack Java 8 compiler отверг `var` в `AdminPhantom.java` |
| Focused combat/server integration | PASS после fix | concrete `PhantomPopulationEcologyService.Snapshot`; scripts compiled; 20/20 |
| Final static verifier x2 | PASS | deterministic PASS/SUMMARY byte-identical; SHA-256 `6147182cfba4adbf99a9fa71ed4fd05ddfd64f249587b8a9e074595a0451aaa8` |
| Final `git diff --check` / text scans | PASS | working и cached diff checks clean; оба text scans clean |

- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic и XML escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Direct blocker budget: 2/2, обе причины доказаны и исправлены минимально. Третий независимый blocker не найден. Полных `ant verify`: 2, оба failed; разрешённый repeat исчерпан. Focused target invocations: 13. Standalone final `ant jar`: 0; `jar` phases внутри двух `verify`: 2. Real-process Goal034 target: 0.

Полные логи сохранены локально и не коммитятся: `.phantom-local/logs/goal034/`.

## Real-process evidence

- Actual run sandbox: не создавался; Goal034 run-id отсутствует.
- Child JVM/PID: LoginServer — не запускался; GameServer generation 1/2 — не запускались.
- Allocated/listening ports: отсутствуют.
- LS registration/GS READY: не проверены real-process gate.
- Managed population 10 / ACTIVE cap 5 / ecology / terminal catch-up: не наблюдались в Goal034 real-process gate.
- Native restart exit 2, Phantom drain и LoginServer continuity: не наблюдались.
- Generation 2 durable identity/fingerprint continuity: не наблюдалась.
- Goal034-owned DB rows/listeners/processes: не создавались, поэтому harness cleanup не требовался и Goal034 orphans отсутствуют.
- Force-kill и wildcard kill не использовались.

Это не отрицательный результат harness: execution сознательно не начат после failed predecessor gate. Утверждения о 10/5, restart и cleanup не подменяются evidence прошлых Goal031/033.

## Rate-parity audit — evidence only

| Route | Canonical owner | Config keys | Verdict |
|---|---|---|---|
| ACTIVE XP/SP | `Npc.getExpReward/getSpReward` → `Attackable.calculateRewards` → real `Player` | `RateXp`, `RateSp`; DynamicExpRates when enabled | `CANONICAL_RATE_AWARE` |
| ACTIVE normal drop | `NpcTemplate` canonical reward calculation used by real combat | `DeathDropAmountMultiplier`, `DeathDropChanceMultiplier`, `HerbDrop*`, `DropAmountMultiplierByItemId`, `DropChanceMultiplierByItemId` | `CANONICAL_RATE_AWARE` |
| ACTIVE spoil | `NpcTemplate` `SPOIL` reward branch | `SpoilDropAmountMultiplier`, `SpoilDropChanceMultiplier` | `CANONICAL_RATE_AWARE` |
| BACKGROUND XP/SP | `L2jPhantomBackgroundAuthority` | `RateXp`, `RateSp`; DynamicExpRates when enabled | `CANONICAL_RATE_AWARE` |
| BACKGROUND normal drop | `L2jPhantomBackgroundAuthority` projection from canonical facts | death/herb amount/chance and per-item keys above | `CANONICAL_RATE_AWARE` |
| BACKGROUND spoil | `L2jPhantomBackgroundAuthority` spoil projection | `SpoilDropAmountMultiplier`, `SpoilDropChanceMultiplier` | `CANONICAL_RATE_AWARE` |
| Quest objective/item drop | Per-quest script callbacks/formulas; единый canonical multiplier owner в bounded audit не подтверждён | universal key не подтверждён | `UNKNOWN_WITH_EXACT_REASON`: требуется полный High Five script audit Goal037 |
| Quest completion XP/SP/Adena/items | `Quest.addExpAndSp` и `Quest.giveItems`; отдельные scripts могут обходить helpers | `RateQuestRewardXP`, `RateQuestRewardSP`, `RateQuestRewardAdena`, `RateQuestReward`, `UseQuestRewardMultipliers`, potion/scroll/recipe/material keys | `RATE_GAP`: helper rate-aware, all-applicable-script parity не доказана |

Rates и quest scripts в Goal034 не менялись. Таблица — обязательный input Goal037 и не является дополнительным blocker Goal034.

## Изменённые файлы

1. `Agents.md`
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
3. `docs/PHANTOM_BOTS_ROADMAP.md`
4. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`
5. `docs/phantoms/TASK_PACKAGE_STANDARD.md`
6. `docs/phantoms/PHANTOM_CURRENT_STATUS.md`
7. `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`
8. `build.xml`
9. `test/java/org/l2jmobius/tests/phantoms/PhantomBlackBoxLocalStackGoal034.java`
10. `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java`
11. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java`
12. `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance.md`

Pre-existing tracked user changes и untracked task packages исключены exact-path staging.

## Usage, Git и next action

Goal usage snapshot перед final QA/commit: 373810 tokens, 2605 seconds.

Git использован только в явно разрешённом bounded scope: `git fetch origin feature/phantom-world`; `git rev-parse HEAD`; `git rev-parse origin/feature/phantom-world`; `git branch --show-current`; bounded `git status`/exact-path `git diff`; `git diff --check`; exact-path `git add`; cached name/check inspection; один `git commit`; non-force `git push origin feature/phantom-world`. Reset/restore/rebase/merge/amend/force/history rewrite не использовались.

Commit subject: `phantom(goal-034): add black-box local stack acceptance`. Этот отчёт входит в тот же exact-path commit; immutable SHA и push result сообщаются в final handoff, поскольку commit не может содержать собственный SHA.

Next: Goal035 — `NOT_STARTED`. Сначала новый exact-parent closure Goal034: один разрешённый green `ant verify`, один final `ant jar`, затем real LS/GS generation 1/2 black-box, native restart, 10/5 continuity и cleanup/orphan proof.
