# Goal 023B — Rift route ownership and production managed-consent closure

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 023 baseline и Goal 023A baseline остаются `CHANGES_REQUIRED`; Goal 024+ — `NOT_STARTED`. Self-accept не выполнялся.

## Summary

Corrective Goal 023B закрывает `R023B-01` и `R023B-02` на branch `feature/phantom-world`, required parent `563752f6844076fdbaeb3be7c5cae979c757960a`, required subject `fix(phantoms): close rift route and consent gaps`, seed `23002312`. Принятые части 023A сохранены; Rift entry, combat и production DB не затрагиваются.

## Read-first audit и локальные аналоги

Прочитан весь exact read set из `TASK.md`: корневой `AGENTS.md`; master plan, roadmap, workflow contract и task-package standard; Goal 023 task/contract/report/review; Goal 023A task/architecture/report/review; все восемь файлов supplied package 023B; перечисленные production, test, build и verifier файлы. Дополнительно по доказанной необходимости read-only прочитаны `dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml` для factual Rift role/readiness authority и `dist/game/data/phantoms/party/high-five-party-roles-v1.xml` для exact RoleMatcher fixture. Эти data-файлы не изменялись.

`README.md`, `docs/README.md`, `CODE_MAP.md` и `docs/CODE_MAP.md` в рабочем модуле не найдены; повторный поиск не выполнялся.

Переиспользованы локальные паттерны:

- Goal 017 `PhantomPartyCoordinator`/`PhantomPartyRouteCoordinator` как единственные владельцы group claim, route manifest, navigation и movement;
- exact refresh непосредственно перед side effect из существующего `requestInvite` flow;
- canonical `PartyInvitationService` identity, expiry и terminal outcomes;
- существующая headless materialization/server-integration fixture без fake `GameClient`;
- descendant-compatible first-parent historical verifier pattern Goal 023.

Учтены High Five-only scope, JDK 25/Ant, отсутствие новых workers/global player scan/SQL/config/other chronicles и запрет Rift entry/combat mutations. Final 023B aggregate, plain verify и standalone jar выполнены после freeze. До commit остаются только точный stage/scope audit, ordinary commit/push и два post-commit byte-identical verifier run.

## Findings closure

| Finding | Точное исправление | Dynamic evidence |
|---|---|---|
| `R023B-01` | `PhantomPartyRouteCoordinator.RouteActivity` объединяет planner-pending, persisted route status и bounded current roster movement ownership. Content binding блокирует nonterminal route, terminal route очищается владельцем Goal 017 до stable binding; второй request не submit-ится. Rift port наблюдает тот же contract, а readiness больше не обходит active route. | planner-pending, MOVING, REGROUPING, ARRIVED, FAILED, duplicate-route и READY-after-cleanup cases: 5/5 |
| `R023B-02` | `evaluateManagedInvitation` перед `ACCEPT` повторно читает exact pending invitation, goal/binding, current vacancy, invitee/candidate eligibility и relationship. Canonical integration устанавливает actual `_service::evaluateManagedInvitation` provider через coordinator/port/service. | eligible ACCEPT, stale capability DEFER, negative relationship REFUSE, unavailable evidence DEFER→EXPIRED; server integration 8/8 |

## Route ownership before/after

До 023B Goal 017 content binding мог считать `ROUTE + COMMITTED` стабильным, не видя planner-pending и persisted `PLANNING`/`MOVING`/`REGROUPING`; rewrite мог стереть live route, разрешить второй submit или пропустить preparation в READY.

После 023B content binding сначала наблюдает bounded `RouteActivity`. Nonterminal ownership возвращает typed `PENDING` без rewrite. `ARRIVED`/`FAILED` сначала удаляются через Goal017-owned cleanup, после чего следующий read подтверждает `NONE`; только тогда разрешены stable binding и `READY_TO_ENTER`. Group id, generation и membership не меняются.

## Managed-provider integration topology

`PhantomRiftService -> L2jPhantomRiftPartyPort -> PhantomPartyCoordinator -> PartyInvitationService` создаёт canonical offer. Target-side coordinator вызывает установленный production method reference `PhantomRiftService::evaluateManagedInvitation`; policy не использует `ignored -> ACCEPT`. Explicit conversation/exact `party.join` остаётся authoritative и заставляет managed policy `DEFER`; ordinary real Player остаётся на обычном client consent path.

## Dynamic matrix and results

Development/focused evidence:

- production compile: 2155 classes, PASS; test compile: 86 classes, PASS;
- `phantom-rift-goal023b-route-closure-test`, seed 23002312: 5/5 PASS;
- `phantom-rift-goal023b-managed-consent-test`, seed 23002312: canonical server integration 8/8 PASS;
- `phantom-rift-goal023b-affected-test`: PASS; canonical invitation 6/6, recovery 6/6, roles 6/6, route 5/5, lifecycle 11/11, conversation actions 7/7, query 3/3;
- original `phantom-rift-goal023-test`: BUILD SUCCESSFUL, historical verifier 023 PASS;
- original `phantom-rift-goal023a-test`: BUILD SUCCESSFUL, 1 min 12 s; acceptance 10/10, server integration 8/8, historical verifier 023A PASS.

Test development включал две fixture-only корректировки route closure: вместо предположения о direct-path всегда issue-ится computed route, а terminal reconciliation проверяется через read-only observe до следующего stable bind. Production logic между этими перезапусками не менялась.

Terminal gates после freeze:

- единственный final `ant phantom-rift-goal023b-test`: BUILD SUCCESSFUL, 1 min 35 s; route closure 5/5, production provider server integration 8/8, Goal 023/023A aggregates и verifiers PASS;
- единственный plain `ant verify`: BUILD SUCCESSFUL, 19 min 2 s; intentional negative-control exit codes 1/2 ожидаемы;
- единственный standalone `ant jar`: BUILD SUCCESSFUL, 18 s;
- frozen 10-file source/test/build/verifier manifest не изменился после всех трёх gates;
- freeze manifest SHA-256: `BE5291D91FCB8678F79315405E38DDB9899C791E5AFBD32140ED9ED09716CCAD`;
- final `dist/libs/GameServer.jar` SHA-256: `9B8247A16546E5D7BA701804D37C2B6B1F6DF6C04F703C5D5F0685FC7C4B31F0`.

## Changed files

Production (4):

- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java`;
- `java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java`.

Tests/build/tools (6):

- `test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `build.xml`;
- `tools/phantoms/verify-task-023a.ps1`;
- new `tools/phantoms/verify-task-023b.ps1`.

Docs/package (14): supplied package 023B (8), master plan, roadmap, Rift recruitment contract, reviews 023A/023B и этот report. Total exact scope: 24; changed production/data: 4; new production/data: 0; SQL: 0; other chronicles: 0.

## Commands

Bounded Git inspection, прямо разрешённый task workflow:

```text
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse --abbrev-ref --symbolic-full-name @{u}
git show -s --format=%P HEAD
git show -s --format=%s HEAD
git status --short --untracked-files=all
git diff --check 563752f6844076fdbaeb3be7c5cae979c757960a --
git diff --name-only/--stat/--numstat и exact-path diff относительно required parent
git ls-files --others --exclude-standard
```

Build/test commands:

```text
ant compile
ant compile-tests
ant phantom-rift-goal023b-route-closure-test
ant phantom-rift-goal023b-managed-consent-test
ant phantom-rift-goal023b-affected-test
ant phantom-rift-goal023-test
ant phantom-rift-goal023a-test
ant phantom-static-verify-023
ant phantom-static-verify-023a
ant phantom-static-verify-023b
ant phantom-rift-goal023b-test
ant verify
ant jar
```

Git history-changing команды до terminal checks не использовались. Exact `git add`, `git commit` и `git push` выполняются после финального scope-аудита согласно task и отражаются в final handoff.

## Source freeze hashes

```text
789D506A460AD5CB869E5899F0431FF1E83B0D531D9D661C3DDA9C56721E91E4  build.xml
60B2AA7EEC151E6EA933CA64EE443951F89641707C920465210734FCEA0AE35C  java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
045AB3B67F662C8844E1009D9B3FB4AECA006ADE167F45F948FEF7097133EA8B  java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java
89B0B2A092D96A11B73FB8A0332F99E30E20C205D872734141EE08A980201764  java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java
C5AB1E7B5E4BBE4C69F9B7B420159589300E976933027885969EF841BC37782C  java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java
BA96C5069634DE0C698BBF7963DF3B50BF9E41040DDAC92A9B915EAA0C2DAE60  test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java
69114A0478D26C28352C5073430567653B85A48D03C4F794E55C57126FE38BF8  test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java
8BF25EABEB6BB162654975737A1D14953CA6B1A4D8F6A3EA6DFE7E6DB08B3826  test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
D0BD5EE88F6296820FFD27AB9B01FDEE4E7D79F2802782E802A907CC6B4D5493  tools/phantoms/verify-task-023a.ps1
CB771C431ACA5C7B79A44F209247AE3366CF700E9386573AAF9E2123770CE913  tools/phantoms/verify-task-023b.ps1
```

## Persistence, DB, configs and side effects

Production DB `l2jmobiush5` не изменялась. SQL/migration/schema/config/data changes отсутствуют. Server integration использует существующий test DB guard. Не добавлены `.l2j`, geodata, fake `GameClient`, global player scan, worker/thread/executor/Future, Rift entry/item consume/teleport/room/spawn/combat mutations.

## Performance and concurrency

Новые route reads — O(1) map lookups плюс bounded current party roster до девяти участников; global scan, отдельные задачи и блокирующий I/O отсутствуют. Dynamic route retries сохранили ровно один navigation submit. Exact consent policy выполняет один bounded candidate refresh перед решением, а discovery cap остаётся 32; новых hot-path logs и unbounded collections нет. Diagnostic wall-clock: focused final aggregate 95 s, full verify 1142 s, standalone jar 18 s.

## Verification and encoding

Pre-freeze historical verifier 023, historical verifier 023A и working-tree verifier 023B: PASS. Verifier 023B: `TASK023B_VERIFIER_OK`, `mode=working`, `scope=24`, `changed_production_data=4`.

Mojibake-маркеры в 24 изменённых файлах проверены отдельно: 0 совпадений.

Escaped Cyrillic в 24 изменённых файлах проверены отдельно шестью обязательными regex-паттернами: 0 совпадений. Техническое определение regex в verifier 023B не содержит полного escaped Cyrillic literal и само не совпадает с этими паттернами.

PowerShell 5.1 и PowerShell 7 post-commit verifier должны дать byte-identical stdout; фактические outputs/hashes записываются в финальном handoff, поскольку completion commit ещё не существует.

## Deviations

`apply_patch` не смог читать workspace из-за Windows sandbox ACL (`helper_unknown_error`). После обязательной попытки использованы bounded exact-string UTF-8 replacements через PowerShell с проверкой единственного совпадения; новые Markdown-файлы записаны UTF-8 без BOM. File-count ceiling не вводился.

## Limitations and risks

- Goal 023 и Goal 023A не приняты; corrective 023B ожидает независимое review.
- `READY_TO_ENTER` остаётся preparation-only; реальный Rift entry намеренно вне scope.
- Геодата и runtime navigation gate не проверялись и не менялись.
- Goal 024+ не начат.

## Git

Git-команды разрешены task workflow для baseline, scope guard, commit и push. Branch: `feature/phantom-world`; parent: `563752f6844076fdbaeb3be7c5cae979c757960a`. Completion SHA, push result и remote equality появляются только после включения этого отчёта в ordinary commit; фактические значения приводятся в финальном handoff без self-referential второго commit.

## Recommended next step

Только независимое review Goal 023B; Goal 024+ не начинать.
