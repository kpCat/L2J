# Goal034 closure 6 — acquisition/manor Player future cleanup

## 1. Идентификатор и baseline
Это bounded resume существующего Goal034 после closure 5 `BLOCKED`. Это НЕ Goal035.

- Task: `Goal034 closure 6`
- Branch: `feature/phantom-world`
- Required parent/HEAD/origin: `d2096a37d1458977c2dd5e81e329c6732e11aa59`
- Git repository root: `C:\Users\ZBook\L2J_Mobius\`
- Only module: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Previous report: `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-restart-ecology-fence-resume.md`
- Previous task: `docs/phantoms/tasks/034-closure5-restart-ecology-fence/TASK.md`
- Goal035 НЕ начинать даже после SUCCESS этой closure.

## 2. Цель
Закрыть единственный известный predecessor blocker closure 5:

`acquisition-manor-active.after-all`

с retained live Player future:

`[Player._skillListTask]`

Нужно:
1. focused воспроизвести либо детерминированно зафиксировать exact lifecycle edge;
2. доказать владельца и момент arm/re-arm `_skillListTask`;
3. исправить минимальный production lifecycle ownership/order bug без masking;
4. пройти focused regressions;
5. выполнить fresh full verify по bounded budget;
6. только после green verify выполнить standalone final jar;
7. выполнить один fresh real Goal034 local-stack acceptance gen1 -> native restart/drain -> gen2 -> drain -> exact cleanup;
8. объявить Goal034 SUCCESS только при полном final acceptance.

## 3. Зависимости и уже доказанное состояние
Closure 5 уже доказала и не должна переоткрываться без нового evidence:

- restart-only ecology scheduling permission gap был реально воспроизведён `8/9`;
- bounded per-entry permission-edge fix довёл Goal033 ecology до `9/9 PASS`;
- Goal034 contract `18/18 PASS`;
- DB negative guard `1/1 PASS`, driver loads `0`, connections `0`;
- missing gen2 evidence profiles `4615/4616/4619/4620` сохранён до cleanup;
- последующие real runs достигли schedule/admission parity `5/5/5` в gen1 и gen2;
- identity/ecology continuity была true;
- terminal lifecycle background capture focused regressions зелёные;
- первый post-fix full verify упал в combat cleanup, immediate focused repeat прошёл `20/20` без изменений;
- единственный full verify repeat упал независимо на `acquisition-manor-active.after-all` из-за `[Player._skillListTask]`;
- по closure5 Phase B это был корректный stop condition;
- post-fix final jar и новый real-stack run после terminal lifecycle fix НЕ выполнялись.

Не откатывать closure5 ecology/background/materialization fixes и не менять их semantics без нового доказанного regression.

## 4. Обязательный предварительный аудит
До production patch прочитать:

1. `Agents.md`;
2. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` — только relevant lifecycle/test/DB rules;
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
4. `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md`;
5. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
6. closure5 `TASK.md`, `CONTEXT.md`, `ACCEPTANCE.md`;
7. closure5 BLOCKED report;
8. `build.xml` target `phantom-acquisition-manor-active-test` and full `verify` dependency;
9. `PhantomTestLauncher` mapping for `acquisition-manor-active`;
10. `PhantomCombatServerIntegrationSuite` MANOR mode, `afterAll()` and cleanup only;
11. `PhantomHeadlessPlayerTestEnvironment` cleanup/assertFutureResidueTerminal only;
12. `Player.java`: `_skillListTask`, `sendSkillList()`, `stopAllTasks()`, `deleteMe()` relevant task shutdown path only;
13. `PhantomMaterializedPlayer.cleanup()`;
14. `PhantomMaterializationService.shutdown()/cleanup` relevant path;
15. closure5-changed background lifecycle hook implementation only where it participates in `beforeStore/afterStore`.

Initial searches <= 4. Не делать широкий повторный аудит всего Phantom World.

## 5. Подтверждённые source facts
На required parent подтверждено:

- `phantom-acquisition-manor-active-test` запускает `PhantomCombatServerIntegrationSuite(Mode.MANOR)`;
- `afterAll()` идёт в suite cleanup;
- materialized test Player после shutdown проверяется на retained resources;
- environment считает ошибкой любой Player `Future`, который не `done` и не `cancelled`;
- `Player.sendSkillList()` создаёт scheduled `_skillListTask` с короткой задержкой;
- callback сам обнуляет `_skillListTask` после отправки;
- `Player.stopAllTasks()` является canonical disconnection cleanup и cancel/null для `_skillListTask`;
- `PhantomMaterializedPlayer.cleanup()` вызывает `cleanupPlayer.stopAllTasks()` ПЕРЕД `_lifecycleSupport.beforeStore(...)`, `storeMe()`, `_lifecycleSupport.afterStore(...)` и `deleteMe()`.

Primary hypothesis:
после первого `stopAllTasks()` lifecycle hook или вызываемый им canonical operation снова вызывает `sendSkillList()`, и `_skillListTask` успевает оставаться live в момент exact after-all assertion.

Это только hypothesis. Перед fix требуется evidence exact arm site/order.

## 6. Phase A — focused reproduction BEFORE fix
Сначала запустить exact:

`ant phantom-acquisition-manor-active-test`

### Если exact target FAIL с `[Player._skillListTask]`
Сохранить:
- suite/test id;
- lifecycle stage;
- Player objectId/profileId;
- был ли future null/live/done/cancelled непосредственно:
  - до initial `stopAllTasks()`;
  - после initial `stopAllTasks()`;
  - после `beforeStore`;
  - после `storeMe`;
  - после `afterStore`;
  - перед/после `deleteMe`;
- точный production call path, который вызывает `sendSkillList()` после cleanup boundary.

Instrumentation допускается только bounded/test diagnostic и должна быть удалена либо превращена в устойчивую regression assertion до commit.

### Если exact target PASS на первом запуске
НЕ объявлять старый failure transient и НЕ идти сразу в full verify.

Создать минимальный deterministic regression через existing lifecycle path, который доказывает известный риск:
- materialized canonical Player имеет/получает lifecycle hook;
- после initial `stopAllTasks()` hook вызывает реальную production operation, которая re-arm-ит Player future, либо exact proven `sendSkillList()` path;
- cleanup должен завершиться без live Player futures.

Regression на required parent должен FAIL до production fix и PASS после fix.
Не допускается test-only прямое зануление private field.

## 7. Root-cause decision
После reproduction классифицировать одну из трёх причин:

A. `PhantomMaterializedPlayer` закрывает Player tasks слишком рано, а lifecycle hooks законно могут re-arm canonical tasks.

B. Конкретный background/lifecycle hook вызывает Player UI/task operation после точки, где такие operations уже запрещены; тогда исправлять hook/order на его ownership boundary.

C. `Player.stopAllTasks()`/canonical Player cleanup имеет реальный общий bug, который воспроизводится и вне Phantom lifecycle.

Не выбирать C только ради удобства. `Player.java` менять только если evidence доказывает general canonical defect.

## 8. Архитектурный invariant
После успешной Phantom dematerialization:

- action admission closed and drained;
- lifecycle capture/store semantics complete;
- Player offline and removed from World;
- autosave ownership removed;
- outbound session/client detached;
- Phantom identity lease released;
- **ни один Player Future, созданный до или во время cleanup lifecycle, не остаётся live**.

Task cleanup должен использовать canonical Player owner APIs. Phantom не должен знать private field names вроде `_skillListTask` в production code.

Если evidence подтверждает hypothesis A, preferred shape — сохранить ранний canonical stop для quiesce и гарантировать final canonical task stop после последнего lifecycle hook, способного re-arm Player tasks, до окончательного delete/release. Точный порядок должен быть доказан тестом; не копировать эту формулировку вслепую.

## 9. Forbidden wrong-layer fixes
Запрещено:

- `Thread.sleep(300/350/500...)` для ожидания `_skillListTask`;
- увеличение timeout/wait как primary fix;
- повторять focused test до случайного PASS;
- исключать `_skillListTask` из `assertFutureResidueTerminal`;
- ослаблять правило `Future done || cancelled`;
- reflection cancel/null private Player field;
- manor-specific `if (mode == MANOR)` cleanup;
- изменение delay внутри `sendSkillList()`;
- глобальное отключение `sendSkillList()`;
- новый thread/executor/timer;
- broad Player lifecycle rewrite;
- ослабление terminal background capture, ecology fence, admission или schedules;
- force online=5;
- скрывать leak удалением evidence/assertion.

## 10. Scope production files
Ожидаемый production scope — минимальный:

Primary candidate:
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java`

Only if evidence requires:
- exact closure5 background lifecycle hook owner;
- `PhantomMaterializationService.java`;
- canonical `Player.java` only for proven general defect.

Tests:
- existing exact suite/harness and/or the closest materialization lifecycle suite.

Не менять unrelated gameplay/domain code.

## 11. Конфиги
Новых shipped config flags не требуется.

Не менять:
- `EnablePhantomSystem=False` shipped default;
- population/ACTIVE shipped zero defaults;
- restart schedule semantics;
- local-play target 10/5;
- DB settings.

## 12. Производительность
Fix не должен:

- добавлять per-tick work;
- добавлять polling;
- добавлять sleeps;
- добавлять global Player scan;
- добавлять permanent reflection;
- создавать новые futures.

Дополнительный bounded canonical cleanup call на dematerialization boundary допустим только если он доказан ownership-correct и idempotent.

## 13. Конкурентность и lifecycle
Особенно проверить race:

`stopAllTasks -> lifecycle hook -> sendSkillList/re-arm -> delete/assert`

и возможный callback race:

- task scheduled but not started;
- task running while cleanup begins;
- cancel(false) returns while callback is already executing;
- callback may set its field to null after cleanup.

Fix обязан быть безопасен при этих состояниях и не зависеть от wall-clock ожидания.

Не использовать `cancel(true)` без доказанной необходимости.

## 14. БД и safety
Разрешена только guarded test DB:

- host `127.0.0.1` / `localhost`;
- port `3308`;
- database `l2jmobiush5_phantom_test`;
- user `l2j_phantom_test`;
- canonical guard-approved connection.

Production `l2jmobiush5` запрещена даже для probe/read/cleanup.

`prepare-phantom-test-db` НЕ выполнять.
Schema change запрещён.
No wildcard/global Java kill.

Существующие `.phantom-local` config/schema manifest использовать только если guard-valid.

## 15. Focused validation после fix
Минимально:

1. deterministic regression, который FAIL до fix / PASS после fix;
2. `ant phantom-acquisition-manor-active-test`;
3. `ant phantom-production-materialization-test`;
4. closest lifecycle/background focused suite, если production hook/background code был изменён;
5. `ant phantom-server-shutdown-handoff-test`, если materialization final cleanup order был изменён;
6. Goal033 ecology `9/9` только если затронут общий background/materialization edge;
7. Goal034 contract `18/18` если затронут Goal034 harness/materialization behavior;
8. DB negative guard `1/1` при DB-using focused validation.

Не запускать full `verify` до зелёного focused set.

## 16. Phase B — fresh predecessor verify budget
Known `_skillListTask` blocker и его fix НЕ считаются новым blocker.

После зелёного focused set:

- запустить **один fresh full `ant verify`**;
- если PASS -> Phase C;
- если появляется один новый независимый predecessor failure:
  - сохранить exact evidence;
  - выполнить максимум один focused confirmation;
  - если transient и focused PASS без кода, разрешён один full verify repeat;
  - если real defect, minimal fix + focused regression, затем один fresh full verify;
- второй новый независимый predecessor blocker => `BLOCKED` и STOP.

Никаких бесконечных repeat loops.

## 17. Final jar
Только после green full verify:

`ant jar`

Ровно один standalone final jar для final production state.

Если production code менялся после jar — jar становится недействительным; требуется новый green verify по budget до нового final jar.

## 18. Phase C — fresh real Goal034 acceptance
Только после green verify + final jar выполнить один fresh Goal034 real black-box run через существующий guarded harness.

Обязательно:

### gen1
- managed=10;
- schedule-aware desired ACTIVE вычисляется canonical oracle;
- expectedAdmitted=`min(activeTarget=5,maxMaterialized,desiredActiveCount)`;
- actualOnline count == expectedAdmitted;
- actualOnline subset desiredActive;
- no non-ACTIVE online.

### restart
- sandbox all-days native restart schedule;
- observed scheduled instant validated before wait;
- native GameServer restart;
- normal Phantom drain;
- LoginServer continuity;
- no forced kill on SUCCESS.

### gen2
Те же schedule-aware invariants.
Missing/unexpected IDs должны сохраняться до cleanup на FAIL.
Durable identity/ecology fingerprint должен оставаться stable.

### cleanup
- exact population cleanup;
- registration cleanup;
- exact child cleanup;
- no orphan PID/ports;
- forced=false on SUCCESS;
- working.integrity=true;
- canonical data fingerprint unchanged;
- production DB unused.

## 19. Phase C blocker budget
Известный `_skillListTask` predecessor blocker не относится к Phase C.

После fresh green verify/jar допускается максимум **один новый independent Phase-C blocker family**:
- exact evidence before cleanup;
- minimal fix;
- focused regression;
- fresh verify/jar required if production changed;
- one real black-box retry.

Второй новый independent Phase-C blocker => `BLOCKED`.

## 20. Documentation
### SUCCESS
Создать:

`docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-success.md`

Обновить существующие current Roadmap v4/status/handoff ровно настолько, чтобы Goal034 был честно `SUCCESS` и Goal035 оставался `NOT_STARTED`/next feature.

Однако **НЕ внедрять Roadmap v5 в этой задаче**.

Не добавлять сейчас:
- новый Humanized Semantic Goal;
- новый custom Semantic override contract;
- перенумерацию final gate на Goal039;
- расширенную 100% quest inventory спецификацию.

Эти уже согласованные требования будут отдельной documentation-only задачей сразу после Goal034 SUCCESS и ДО Goal035.

После SUCCESS этой closure STOP. Goal035 не начинать.

### BLOCKED
Создать bounded closure6 BLOCKED report с exact evidence.
SUCCESS docs не менять.
Goal035 не начинать.

Historical reports immutable.

## 21. Report format
Report <= 180 lines.

Обязательно:
- required parent/final status;
- exact focused reproduction before fix;
- root cause и arm/re-arm ordering;
- production fix и ownership rationale;
- regression FAIL-before/PASS-after;
- manor result;
- materialization/lifecycle results;
- Goal033/Goal034 focused results where applicable;
- full verify count/results;
- final jar result;
- real gen1 desired/expected/online IDs;
- restart expected/observed/drain;
- real gen2 desired/expected/online + missing/unexpected IDs;
- continuity;
- cleanup/integrity/orphans;
- DB guard/driver loads/connections;
- production DB unused;
- `prepare-phantom-test-db` not run;
- changed files;
- mojibake/escaped Cyrillic checks;
- user-owned files untouched;
- elapsed/tokens if available;
- commit SHA/push;
- explicit `Goal035 not started`;
- explicit `Roadmap v5 sync deferred to next documentation-only task`.

## 22. Git precondition
Before edits:

- `git fetch origin feature/phantom-world`
- `git rev-parse HEAD`
- `git rev-parse origin/feature/phantom-world`
- `git branch --show-current`
- `git status --short`

Require:

`HEAD == origin/feature/phantom-world == d2096a37d1458977c2dd5e81e329c6732e11aa59`

If not exact: do not reset/merge/rebase. STOP/BLOCKED with evidence unless divergence is only task package extraction that does not alter HEAD and is expected as untracked task files.

Task package files from this archive are expected untracked input and may be committed with the task.

## 23. Git rules
Allowed:
- bounded `status/diff/show/log/rev-parse`;
- exact-path staging;
- one task commit;
- non-force push to `feature/phantom-world`.

Forbidden:
- reset;
- restore;
- checkout of files;
- clean;
- rebase;
- merge;
- amend;
- stash user changes;
- force push;
- history rewrite.

User-owned staged/unstaged/untracked files outside exact task paths must remain untouched.

SUCCESS commit subject:

`phantom(goal-034): close player future cleanup acceptance`

BLOCKED commit subject:

`phantom(goal-034): record closure 6 blocker`

## 24. Mojibake / escaped Cyrillic
Before commit inspect exact changed text files for:
- mojibake markers;
- accidental escaped Cyrillic sequences in human-readable docs/data.

Do not mass-normalize unrelated files.

## 25. Out of scope
- Goal035 siege implementation;
- Goal036 quest/instance implementation;
- Goal037 rates/quest audit implementation;
- Roadmap v5 documentation migration;
- Goal038 humanized Semantic implementation;
- Goal039 final gate implementation;
- production DB;
- schema changes;
- scheduler/admission/ecology redesign;
- unrelated gameplay fixes;
- user-owned files.

## 26. SUCCESS condition
Goal034 closure 6 = SUCCESS iff:

- retained `_skillListTask` lifecycle edge is proven, not guessed;
- deterministic regression demonstrates FAIL-before/PASS-after;
- canonical cleanup leaves zero live Player futures;
- focused suites green;
- full verify green within budget;
- final jar green;
- fresh real gen1 parity PASS;
- native restart/drain PASS;
- fresh real gen2 parity/drain PASS;
- continuity PASS;
- exact cleanup/no-orphans/integrity PASS;
- production DB unused;
- SUCCESS docs/report exact;
- task commit + non-force push complete;
- Goal035 not started.

After that STOP and return control to the user for the next documentation-only Roadmap v5 task.
