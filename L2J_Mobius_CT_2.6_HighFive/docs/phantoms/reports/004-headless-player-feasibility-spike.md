# Codex report — Task 004 headless Player feasibility spike

## Status and starting baseline

`SUCCESS`

Architecture verdict:

```text
FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW
```

- Branch: `feature/phantom-world`.
- Effective starting baseline:
  `1ca74a3d96e8fa51612ef3e5145c7398abf60f6d`.
- Baseline parent / accepted Task 003 commit:
  `eb008f2216b3e8381c0181d71ce200bbf4907ac7`.
- Seed: `20260725001`.
- Production DB: zero access.
- Test DB: only `l2jmobiush5_phantom_test`.
- Task 005: `NOT_STARTED`.
- ADR remains `Proposed`.

## Approved documentation-only baseline advancement

Во время Task 004 пользователь одобрил новый effective baseline:

```text
Commit: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Parent: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Subject: PHANTOM_BOTS_ROADMAP
Only path: L2J_Mobius_CT_2.6_HighFive/docs/PHANTOM_BOTS_ROADMAP.md
SHA-256: B049F85AE276906C969FE2FCC8A39F126B342AB1D8F256036E2EE6A60F1498D8
```

`git diff-tree` подтвердил единственный documentation path. Local HEAD уже
был fast-forward на этот commit. Roadmap не читался, не менялся и не
добавлялся в содержательный scope Task 004. Final scope guard сравнивается с
`1ca74a3d...`; Task 004 остаётся одним ordinary child commit.

## Task 003 review closure

В отчёт Task 003 добавлен immutable handoff:

```text
Commit: eb008f2216b3e8381c0181d71ce200bbf4907ac7
Parent: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5
Push: successful
Remote ref: exact
Final verifier 1: 72/72
Final verifier 2: 72/72
Identical SHA-256:
447FDBA9B5C2592C40250FF5026B5DB0E71C66520EF8E0F46CF9E3A252894F9D
Independent review: ACCEPT
```

Создан отдельный review record:
`docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md`.
Task 003 принят, revert не нужен, Task 004 разрешён.

## Touchpoint audit and hashes

Only-read audit выполнен до production-изменений. Зафиксированы baseline
SHA-256 и symbols для `Player`, `GameClient`, `CharacterSelect`,
`Disconnection`, `World`, `ServerPacket`, effect-bearing packet families,
offline systems, autosave, `IdManager`, build/test harness и Phantom skeleton.

Ключевые baseline hashes:

```text
Player.java          FC569FF715B031E64B06BA6C7BD89D3934F1C5F81CE5766AB86D9CC9C75F2E54
GameClient.java      5C7958A1ECBBA322791ABDCFBD6FB25C73C7BF486E6129CFFEE40E59819855B1
CharacterSelect.java 9C2D211C556EC9126CEDA6F62A40845F76D147546CDFD65D2C9671C986001236
Disconnection.java   D9FEBE2DDABA2C3416906DACB648DFC17C17CEBC8A2FCAC3AEED191C07F01D86
World.java           4AE2C1614FE09A69FBCCF6DC6E3784F8320D9BC99F046CB632E69048D539D779
ServerPacket.java    AA76DEC6F377B92047A2CEE2C1E33A8E568E6CE837E8827F0033CBDE575E72AC
```

Approved baseline advancement не меняет ни один audited production path,
поэтому hashes остаются точными. Stop-rule condition не обнаружен.

## Architecture verdict

Canonical `Player` безопасно материализуется без TCP через минимальный
Player-owned outbound/session seam. Для spike не потребовались:

- fake/null-network `GameClient` или `Connection`;
- `Player` subclass, fork или copy;
- `EnterWorld.runImpl`;
- Phantom API на базе client packet handlers;
- production DB или schema migration;
- отдельный поток/executor на фантома;
- изменение других хроник;
- большая часть `GameServer` startup.

Рекомендация — принять seam после независимого review. Это не переводит ADR в
`Accepted` и не начинает Task 005.

## Production changes

Production touch ограничен approved envelope:

- `Player`: generic outbound owner, tokenized attachment, headless online value
  `2`, explicit null-packet failure и полная отмена существующих coalescing
  futures в `stopAllTasks`;
- `PlayerOutboundSession`: generic interface и default real-client adapter;
- `HeadlessPlayerOutboundSession`: zero-transport effect sink;
- `PhantomIdentityLeaseRegistry`: process-local tokenized ownership;
- `PhantomPlayerMaterializationSpike`: test-instantiated bounded lifecycle;
- `PhantomActionFacade`: один reversible inventory action;
- `GameClient.load`: реальный `REAL_LOGIN` claim до canonical load;
- `CharacterSelect`: PHANTOM collision проходит через real-login hook, а
  post-load/bind failure всегда вызывает `Disconnection`;
- `Disconnection`: identity release во всех immediate/delayed final paths.

`GameServer`, `PhantomSystem`, `ServerPacket`, `EnterWorld`, `World`,
`PlayerAutoSaveTaskManager`, configs и SQL не менялись.

## Changed files

Task 004 изменяет ровно 31 scoped artifact:

- build/launcher: `build.xml`,
  `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- production seam and hooks:
  `java/org/l2jmobius/gameserver/model/actor/Player.java`,
  `java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java`,
  `java/org/l2jmobius/gameserver/network/GameClient.java`,
  `java/org/l2jmobius/gameserver/network/Disconnection.java`,
  `java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java`,
  `java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java`,
  `java/org/l2jmobius/gameserver/phantoms/player/PhantomActionFacade.java`,
  `java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java`,
  `java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java`;
- focused tests:
  `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerFixture.java`,
  `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`,
  `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java`,
  `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerPerformanceSuite.java`;
- Task package: восемь файлов в
  `docs/phantoms/tasks/004-headless-player-feasibility-spike/`;
- audit evidence: три файла в
  `docs/phantoms/audits/004-headless-player-feasibility-spike/`;
- documentation/provenance:
  `docs/phantoms/adr/0001-headless-player-integration-seam.md`,
  `docs/phantoms/reports/003-disabled-skeleton-config-metrics.md`,
  `docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md`,
  этот report;
- verifier: `tools/phantoms/verify-task-004.ps1`.

`docs/PHANTOM_BOTS_ROADMAP.md` сохранён byte-for-byte и не входит в Task 004
diff. Несвязанные `docs/agent-tasks/**` также не входят в scope и staging.

## Outbound/session seam

Default owner — singleton client-bound adapter. Он на каждом send читает
текущий `player.getClient()` и при наличии вызывает неизменённый
`GameClient.sendPacket(packet)`. Поэтому real transport ordering остаётся:

```text
writePacket(packet)
packet.runImpl(_player)
```

Headless attachment возможен только при null client и default outbound.
`setClient(non-null)` запрещён, пока headless owner активен. Attachment
одноразовый и token/identity-safe: stale close не снимает новый owner.
После cleanup возвращается default client-bound adapter.

## Packet-effect evidence

`HeadlessPlayerOutboundSession`:

- не содержит network transport type и не вызывает serialization/write;
- вызывает `packet.runImpl(player)` ровно в одном source location;
- propagates effect exception;
- ограничивает depth `1..256` и packets/root `1..4096`;
- recording выключен capacity `0` по умолчанию;
- optional ring ограничен `0..1024`, overwrite учитывается счётчиком;
- не логирует hot path.

Functional tests доказали:

- default null-client no-op;
- null packet fail-fast;
- no-effect packet;
- counter effect exactly once;
- throwing effect propagation;
- реальный `NpcHtmlMessage` меняет HTML action cache;
- реальный `TutorialCloseHtml` очищает tutorial scope;
- `ItemList -> ExQuestItemList` даёт ровно два effect и depth `2`;
- deliberate recursion останавливается exact depth guard;
- реальный `CreatureSay.runImpl` доставляет `CreatureSay` и `Snoop` observer-у.

## Online/session policy

Используются только штатные значения:

```text
real attached                -> 1
detached real                -> 2
active headless, client null -> 2
plain null without headless  -> 0
_isOnline=false              -> 0
```

Полный call-site audit находится в `ONLINE_SESSION_POLICY.md`. Headless `2`
разрешает World observer broadcast, исключает AutoPotion и HWID client
dereference (`==1`), а cleanup сохраняет DB online `0`. PcCafe routes не
разрешены facade и остаются явным later-task policy risk.

## Identity ownership and real-login hook

Единый `ConcurrentHashMap<objectId, tokenized entry>` поддерживает владельцев
`REAL_LOGIN` и `PHANTOM`. `putIfAbsent` даёт одного owner, compare-remove и
одноразовый lease защищают от stale release.

- PHANTOM claim выполняется до `Player.load`, с World checks до/после load.
- REAL_LOGIN claim встроен в настоящий `GameClient.load`.
- PHANTOM блокирует реальный select как до spawn, так и после World visibility.
- REAL_LOGIN блокирует Phantom load.
- PHANTOM блокирует второй Phantom.
- load failure освобождает reservation.
- CharacterSelect failure и Disconnection освобождают real lease.
- Phantom освобождает identity последним.
- существующая real-real World-visible cleanup сохранена; новый registry не
  заменяет её и не делает `World.addObject` normal arbitration.

Тесты не создают `GameClient`; реальный hook доказывается static contract
gate, а registry/collision semantics — executable concurrency tests.

## Test environment

Forked JVM запускается с working directory `dist/game`. До Hikari вызывается
существующий `PhantomTestDatabaseBootstrap`, который проверяет test-only user,
database allowlist и durable schema fingerprint:

```text
database=l2jmobiush5_phantom_test
schemaVersion=1
scriptCount=117
statementCount=205
aggregateSha256=A3C9FC62C662DC5E0E690D6E7D6E63B5B0268BAD3019348E75F565DA5C84453A
```

`ThreadPool` инициализируется один раз и закрывается вместе с Hikari в
`afterAll`. Report печатает 39 direct bootstrap components и 5 observed
transitive dependencies. Это exact subset, необходимый canonical
`Player.create/load/deleteMe`: effect master, skills/items/templates,
clan/castle/territory/zone dependencies, World, cleanup managers. Не
инициализируются `GameServer`, LoginServer, ConnectionManager, GameClient,
listener или общий script list; siege instances не активируются.

## Fixture lifecycle

Harness владеет только deterministic account `phantom_t004_20260725001` и
двумя character names с seed suffix. Последовательность:

```text
exact account cleanup/insert
real PlayerTemplate
Player.create
persist skill 194 and bounded item 57 baseline
store/delete
Player.load exact class Player
materialize/spawn/action/dematerialize
reload and verify inventory/skill
canonical delete by exact object ID
delete exact account
verify account/character/item rows zero
```

No arbitrary account/character CLI input используется. Final cleanup
проверяет items для всех object IDs, замеченных под owned account, и безопасен
при повторном вызове.

## Explicit materialization steps

Полная классификация находится в `MATERIALIZATION_STEPS.md`.

- `REQUIRED_NOW`: identity, canonical load, outbound attach, минимальная
  post-load normalization, online/spawn, action admission, store/delete,
  detach/release/clear.
- `DEFERRED_SAFE`: party/clan follow-up, quests, mail, trade/private store,
  autoplay, instance restore, client presentation and broader gameplay.
- `CLIENT_SESSION_ONLY`: connection state, tracert/LoginServer, HWID,
  owner-client packet set.
- `FORBIDDEN`: EnterWorld handler, fake network client, Player fork, client
  handlers as actions, production DB, full server startup, per-phantom worker.

## Action facade and conservation

Facade содержит один executable action:

```text
add item 57 x1 through canonical Inventory
run bounded injection hook
destroy exact item 57 x1 through canonical Inventory
assert count returned to baseline
```

Inventory monitor сериализует add/remove. `finally` удаляет delta при
исключении. Lifecycle фиксирует baseline после canonical load и повторно
восстанавливает его перед store/delete. Direct SQL и client packet handlers
отсутствуют. Trade, mail, movement, combat, skills, party, NPC commerce и chat
actions не экспонируются.

## Observer visibility

Два exact canonical Player одновременно materialize в одной стартовой region.
Оба получают `CharInfo` другого через штатные known-list/broadcast механизмы.
`CreatureSay` snoop effect доставляет observer-у `CreatureSay` и `Snoop`.
После теста оба отсутствуют в World и не оставляют output/lease/autosave.

## Cleanup and failure matrix

Cleanup сначала закрывает admission, затем ждёт admitted action count `0` с
timeout 5 секунд. После этого:

```text
stopAllTasks
restore item baseline
storeMe
deleteMe
detach outbound
release identity last
clear references
```

Concurrent action-vs-cleanup test блокирует action после mutation, запускает
cleanup, доказывает закрытый admission, запрещает новый action, затем
освобождает action и проверяет bounded drain.

Failure injector прошёл все `11/11` points:

```text
AFTER_IDENTITY_CLAIM
AFTER_PLAYER_LOAD
AFTER_IDENTITY_ATTACHMENT
AFTER_HEADLESS_OUTPUT_ATTACHMENT
AFTER_DOMAIN_INITIALIZATION
AFTER_ONLINE_ACTIVATION
AFTER_WORLD_SPAWN
AFTER_ACTION_ADMISSION
AFTER_ACTION_MUTATION
AFTER_STORE_BEFORE_DELETE
AFTER_DELETE_BEFORE_IDENTITY_RELEASE
```

Для каждого failure observed ровно один раз, cleanup вызван дважды, item
baseline восстановлен, identity/output сняты.

## Task, autosave and World residue

После каждого positive, collision, concurrent и injected-failure lifecycle
проверены:

```text
World Player absent
characters.online = 0
PlayerAutoSaveTaskManager absent
all reflected Player Future fields null/done/cancelled
party false
active requester null
active trade null
instance id 0
fixture item count baseline
headless output detached
identity lease absent
new retained non-daemon thread IDs = 0
```

После suite cleanup также account rows `0`, character rows `0`, all
fixture-owned item rows `0`, active leases `0`; Hikari и L2JMobius
infrastructure threads отсутствуют.

## One/ten fixture measurements

Отдельный forked performance smoke выполняет warm-up, один measured lifecycle
и десять последовательных lifecycle без concurrent DB burst:

| Measurement | Result | Limit |
|---|---:|---:|
| one fixture | `18,645,500 ns` | `30,000,000,000 ns` |
| ten sequential | `138,938,600 ns` | `120,000,000,000 ns` |
| one effects | `6` | bounded by root `128` |
| ten effects | `60` | bounded by root `128` each |
| recording capacity | `16` | fixed |
| dropped records | `0` | no overflow |

Оба performance cases PASS; World/lease/autosave/task/thread residue после
каждого lifecycle отсутствует.

## DB and network safety

- Production DB config не передавался и production DB не читалась/не менялась.
- Существующий guard сравнивает exact DB/user/schema до Hikari.
- Test DB была свежей; reprovision не потребовался.
- Единственный network I/O suite — JDBC к allowlisted localhost test DB.
- Game protocol TCP, listener, `ConnectionManager`, `GameClient` instance,
  packet serialization и writes отсутствуют.
- Headless sink вызывает только server-side `runImpl(Player)` effects.
- Credentials и `.phantom-local` artifacts не включены в scope.

## Disabled production behavior

Task 003 flags остаются `False`. `PhantomSystem` и startup/shutdown wiring не
менялись. Materialization spike не зарегистрирован в `GameServer` и не
создаётся production skeleton. Следовательно disabled path по-прежнему не
создаёт Player, task, queue work, DB или network work.

## Tests, counts and exit codes

Targeted и cumulative evidence:

```text
ant compile-tests                               compile 1905 + 26, exit 0
ant test                                        66/66 PASS, exit 0
ant phantom-skeleton-test                       12/12 PASS, exit 0
ant phantom-headless-player-test                13/13 PASS, exit 0
ant phantom-headless-player-performance-smoke   2/2 PASS, exit 0
ant phantom-negative-control                    expected child 1, Ant exit 0
ant phantom-db-guard-negative-control           1/1, expected child 2, Ant exit 0
ant phantom-provisioning-lock-control           PASS, Ant exit 0
ant phantom-schema-freshness-negative-control   1/1, expected child 2, Ant exit 0
ant phantom-lifecycle-negative-control          expected child 2, Ant exit 0
ant phantom-db-test                             9/9 PASS, exit 0
ant phantom-scenario-test                       1/1 PASS, exit 0
ant phantom-performance-smoke                   1/1 PASS, exit 0
```

Functional failure history использовалась для исправления fixture baseline и
валидного persisted skill; final reruns зелёные. Final cumulative command
повторил все targets в одном dependency graph и завершился успешно.

## Commands

Основные воспроизводимые команды выполнялись из корня модуля через официальный
Apache Ant 1.10.15 и JDK 25:

```text
ant compile-tests
ant test
ant phantom-skeleton-test
ant phantom-headless-player-test
ant phantom-headless-player-performance-smoke
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-004.ps1
```

Git scope/provenance использовали только разрешённые Task 004 команды:
`git status`, `git branch --show-current`, `git rev-parse`, `git fetch`,
`git pull --ff-only`, `git diff`, `git diff-tree`, `git diff --check`,
`git show`, `git log`, `git ls-files`, `git add` с exact paths,
`git commit`, обычный `git push` и `git ls-remote`. Amend, rebase, merge,
reset, restore и force push не использовались.

## Ant verify and jar

```text
ant verify
BUILD SUCCESSFUL
Total time: 52 seconds
exit 0

ant jar
compiled production sources: 1905
GameServer.jar: built and copied to dist/libs
LoginServer.jar: built and copied to dist/libs
BUILD SUCCESSFUL
Total time: 15 seconds
exit 0
```

Static JAR inspection: `0` entries under
`org/l2jmobius/tests/phantoms/`. Post-commit `verify` и `jar` повторяются как
immutable handoff gate.

## Static verifier

Initial structural run до создания этого report:

```text
SUMMARY: total=97 passed=87 failed=10
```

Ожидаемый missing-report gate и девять verifier-token defects были обнаружены
до final run и исправлены; это не product failure.

Final pre-commit run внутри `ant verify`:

```text
SUMMARY: total=97 passed=97 failed=0
exit 0
```

После окончательной report правки verifier повторяется. Два post-commit
verifier outputs сравниваются byte-for-byte и SHA-256 вне repository; сами
post-commit immutable данные остаются external handoff evidence.

Text-safety gates по всем изменённым текстовым файлам:

- mojibake-маркеры в изменённых файлах проверены: совпадений `0`;
- escaped Cyrillic в изменённых файлах проверены: совпадений `0`.

## Scope and conditional touches

Task scope состоит из одного связанного artifact family: пяти production
touchpoints, пяти новых seam/lifecycle classes, четырёх focused test classes,
launcher/build wiring, Task package, трёх audit documents, Task 003 closure,
ADR, report и verifier. Это bounded exception к обычному soft limit 8–10
файлов, прямо заданная Task 004 allowlist; независимого subsystem/refactor нет.

Conditional production paths:

```text
ServerPacket.java: NOT_TOUCHED
EnterWorld.java: NOT_TOUCHED
World.java: NOT_TOUCHED
PlayerAutoSaveTaskManager.java: NOT_TOUCHED
```

Другие хроники, production configs/schema, Task 005 и
`docs/agent-tasks/**` исключены. `docs/agent-tasks/**` не читался, не менялся и
не будет staged.

## Deviations, limitations and risks

- Test bootstrap вынужден загрузить castle/territory/clan-hall/zone data:
  canonical `Player.create` строит `UserInfo`, а `deleteMe` требует
  `ZoneManager`. Siege activation, full scripts и listener не запускаются.
- Реальный `GameClient` не создаётся тестом по прямому запрету; наличие и
  ordering настоящего login hook защищены static verifier, а registry
  semantics — executable tests.
- Recorder — диагностический ring, не production telemetry.
- Lifecycle class — bounded spike, не Task 006 final lifecycle и не подключён
  к scheduler/population.
- PcCafe and любые другие actions вне facade запрещены до отдельной policy.
- Геодата отсутствует и navigation не входит в Task 004.
- Independent review ещё не выполнен; ADR не принят.

## Branch, parent and subject

```text
Branch: feature/phantom-world
Parent: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d
Subject: feat(phantoms): prove headless player feasibility
Commit shape: one ordinary child commit
```

Запрещённые amend, rebase, merge commit и force push не использовались.

Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.

## Manual gate

`PENDING_INDEPENDENT_REVIEW`

Architecture verdict:

```text
FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW
```

ADR remains `Proposed`.

Task 005: `NOT_STARTED`.
