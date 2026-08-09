# Goal 023C — Rift route failure closure report

Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

Corrective Goal 023C закрывает только `R023C-01` на branch `feature/phantom-world`, required parent `041e23502e5701716bab77dbe73304dc375a157e`, required commit subject `fix(phantoms): close rift route failure semantics`, seed `23002313`.

Independent review Goal 023B зафиксирован как `ACCEPT_WITH_REQUIRED_023C_ROUTE_FAILURE_CLOSURE`; `R023B-01` и `R023B-02` — `CLOSED`. Goal 023 overall остаётся `CHANGES_REQUIRED` до независимого review 023C. Goal 024+ — `NOT_STARTED`.

## Read-first audit и локальные аналоги

Прочитаны обязательные `Agents.md`, master plan, roadmap, workflow contract, task-package standard; все файлы task packages 023B/023C; отчёт и review 023B; Navigation result/policy/service; Goal 017 route/model/coordinator; Rift port/service; ближайшие Navigation, Party и Rift test fixtures; build targets и historical verifier 023B.

Локальные аналоги:

- typed `PhantomNavigationService.Submission` и `PhantomNavigationResult`;
- bounded completed-result receipt Navigation;
- `RouteActivity` и terminal reconciliation Goal 023B;
- deterministic deferred dispatcher из Navigation tests;
- Rift readiness/binding/request replan из `PhantomRiftSuite`;
- descendant-compatible first-parent verifier pattern 023/023A/023B.

`README.md`, `docs/README.md`, `CODE_MAP.md` и `docs/CODE_MAP.md` в рабочем модуле не найдены; повторный поиск не выполнялся. Родительский repository README прочитан.

Учтены High Five-only scope, JDK 25/Ant, отсутствие fake `GameClient`, новых workers/global scan, SQL/production DB/.l2j, Rift entry/combat и Goal 024+ изменений.

## Architecture decisions

`PhantomPartyRouteCoordinator.RouteAttempt` различает:

- accepted async — `PENDING`;
- completed usable route — `READY`;
- completed без usable route — `FAILED` с exact `PhantomNavigationResult.Status`;
- rejected submission — `REJECTED`;
- invalid/absent owner precondition — `UNAVAILABLE`;
- `NONE` — действительное отсутствие ownership.

Route/deadline ownership создаётся только в ветке `READY`. Sync completed receipt потребляется ровно один раз. Async terminal no-route сохраняется как один bounded terminal receipt на group, не является ownership и удаляется existing `cancel/reconcileTerminalRoute` path. Snapshot отдельно показывает navigation, route, deadline, movement claims и terminal receipts.

`PhantomPartyCoordinator.RouteRequestResult` проводит outcome, route identity/generation/destination, Navigation status и reason вверх. `L2jPhantomRiftPartyPort` больше не преобразует `NONE` в `PENDING`. `PhantomRiftService` входит в `OBSERVE_ROUTE` только для `PENDING`; любой terminal result сохраняет `EVALUATE_READINESS`, поэтому same-pulse resend невозможен, а дальнейший submit проходит обычную readiness/binding/request цепочку.

Navigation authority, Party/Rift entry/combat, public GamePackage schema, DB и configs не менялись. Новый worker, scheduler или thread не добавлялся.

## Dynamic evidence

Final frozen gate evidence:

- source/test/build/verifier freeze: 9 files, manifest SHA-256 `7A4EA958216AF1BEB6B876D3DD87A8B24A597D8AB2CA24F672F87194490B63B8` before and after all terminal gates;
- production compile: 2155 classes, PASS;
- test compile: 86 classes, PASS;
- `phantom-rift-goal023c-route-failure-test`, seed 23002313: 7/7 PASS;
- `phantom-rift-goal023c-replan-test`, seed 23002313: 2/2 PASS;
- preserved `phantom-rift-goal023b-route-closure-test`, seed 23002312: 5/5 PASS;
- preserved `phantom-rift-goal023b-managed-consent-test`, seed 23002312: 8/8 PASS;
- exact affected Navigation/Goal 017/020 matrix: PASS;
- exact historical Goal 023/023A/023B regressions and verifiers: PASS;
- final `phantom-rift-goal023c-test`: `BUILD SUCCESSFUL`, 1 minute 33 seconds;
- one completed plain `ant verify`: `BUILD SUCCESSFUL`, 17 minutes 58 seconds;
- standalone `ant jar`: `BUILD SUCCESSFUL`, 14 seconds; `GameServer.jar` contains `PhantomPartyRouteCoordinator.class`.

Focused matrix динамически покрывает sync `SERVICE_NOT_RUNNING` rejection, sync `NO_GEODATA` completed-no-route, async accepted→`NO_PATH`, async accepted→`BACKEND_FAILURE`, immediate/async usable route regressions, exact terminal reason/identity, zero stale Navigation/route/deadline/movement ownership и later replan после deterministic cooldown. Rift test подтверждает переход `OBSERVE_ROUTE`→`EVALUATE_READINESS`, отсутствие same-pulse route increment и последующий обычный replan.

Post-commit verifier в PowerShell 5.1 и PowerShell 7 и byte-identical stdout проверяются после ordinary commit/push, чтобы historical mode видел completion commit и remote branch.

## Changed files

Production (4):

- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java`;
- `java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java`.

Tests/build/tools (5):

- `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomRiftSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `build.xml`;
- `tools/phantoms/verify-task-023c.ps1`.

Docs/package (14): supplied package 023C (8), master plan, roadmap, Rift recruitment contract, reviews 023B/023C и этот report. Total exact scope: 23; changed production/data: 4; new production/data: 0; SQL: 0; other chronicles: 0.

## DB, migrations и configs

DB, migrations, SQL, production schema, `.l2j`, configs и production data не менялись. Managed-consent DB integration остаётся существующим regression target 023B; production DB не использовалась.

## Commands

Разработка и предварительные проверки:

```text
git status --short --branch
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse --abbrev-ref --symbolic-full-name @{upstream}
git show -s --format=%H%n%P%n%s%n%D HEAD
git diff --check
ant compile-tests
ant phantom-rift-goal023c-route-failure-test
ant phantom-rift-goal023c-replan-test
ant phantom-rift-goal023b-route-closure-test
```

Terminal gates по task contract:

```text
ant phantom-rift-goal023c-focused-test
ant phantom-rift-goal023c-affected-test
ant phantom-rift-goal023-test
ant phantom-rift-goal023a-test
ant phantom-rift-goal023b-test
ant phantom-static-verify-023
ant phantom-static-verify-023a
ant phantom-static-verify-023b
ant phantom-static-verify-023c
ant phantom-rift-goal023c-test
ant verify
ant jar
```

Локальный Ant executable: `C:\Users\endim\.cache\codex-ant\apache-ant-1.10.17\bin\ant.bat`; targets и arguments plain, без дополнительных Ant properties.

## Encoding checks

Mojibake-маркеры в изменённых файлах проверены отдельно: 0 совпадений.

Escaped Cyrillic в изменённых файлах проверен отдельно шестью обязательными regex-паттернами: 0 совпадений.

## Performance и lifecycle

Новых hot-path collection scans, DB calls, log spam или workers нет. Terminal receipt ограничен 4096 entries — тем же bounded active-group ceiling Goal 017 — и очищается при reconciliation/stop. Dynamic assertions проверяют нулевые active Navigation requests, completed results, route/deadline/movement claims и terminal receipts после failure reconciliation.

## Deviations

Встроенный `apply_patch` дважды столкнулся с Windows ACL helper error. Все правки после этого применялись тем же Codex apply-patch engine через доступный local binary с exact patch argument; shell write tricks, Python и broad rewrite не использовались.

Первая focused попытка обнаружила retained sync completed receipt и штатный Navigation cooldown; production получил exact consume, а replan test использует deterministic clock за cooldown. Первая Rift replan попытка уточнила фактическую обычную цепочку `EVALUATE_READINESS`→binding→request. Эти failed development runs не являются terminal gates.

Первые transport-вызовы final aggregate и plain verify были прерваны shell wrapper через 5 секунд из-за ошибочно короткого transport timeout, до получения Ant result. Полные terminal gates затем выполнены ровно по одному разу до `BUILD SUCCESSFUL`; frozen set между ними не менялся.

## Limitations и risks

- Геодата по-прежнему отсутствует; проверена deterministic headless semantics, не реальный geodata runtime.
- Goal 023C не меняет Rift entry/combat и не принимает Goal 023 самостоятельно.
- Completion требует independent review; self-accept запрещён.

## Git

Git-команды прямо разрешены task workflow. Branch: `feature/phantom-world`; parent: `041e23502e5701716bab77dbe73304dc375a157e`. Completion SHA и push result появляются в final handoff после ordinary commit/push; второй self-referential report commit не создаётся.

## Recommended next step

Только независимое review Goal 023C. Goal 023 overall до него остаётся `CHANGES_REQUIRED`; Goal 024+ не начинать.
