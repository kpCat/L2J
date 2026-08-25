# Goal 030 Checkpoint 2C — canonical headless WHISPER delivery unblock and CP2 resume

## Status

**BLOCKED — `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`**

Native headless WHISPER blocker исправлен и покрыт focused regression. Fresh CP2C runtime исчерпал два разрешённых запуска: production Population/Scheduler/materialization/autonomous decision проходит, real WHISPER теперь фактически доставляется headless Phantom, но Conversation/Party execution не создаёт real Party invitation и оставляет execution entries in-flight. После единственной разрешённой fixture correction (mutual visibility) сбой сохранился, поэтому второй production owner в этой задаче не изменялся.

Goal030 остаётся `IN_PROGRESS`; CP2 остаётся `BLOCKED / IN_PROGRESS`. Matrix сохранена `11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030`. PASS-only final gates и `jar` не запускались.

## ChatWhisper: old → new

Production scope ограничен `dist/game/data/scripts/handlers/chat/channels/ChatWhisper.java`.

Было:

```java
if ((receiver.getClient() == null) || receiver.getClient().isDetached())
```

Стало:

```java
if (receiver.isInOfflineMode())
```

Сообщение sender сохранено exact: `Player is in offline mode.` Jail, chat-ban, block-list, faction и silence semantics не менялись.

Существующие canonical факты подтверждены без production-изменений:

- `Player.hasHeadlessOutboundSession()` проверяет `SessionKind.HEADLESS`;
- `Player.isInOfflineMode()` равно `!hasHeadlessOutboundSession() && ((_client == null) || _client.isDetached())`;
- `Player.sendPacket(ServerPacket)` делегирует `PlayerOutboundSession.send`;
- `HeadlessPlayerOutboundSession.send` учитывает bounded effect и вызывает `packet.runImpl(player)` ровно один раз;
- `CreatureSay.runImpl` выполняет side effect и вызывает `ChatObservationService.publishDelivered`.

Fake/null-network `GameClient`, новый transport, Phantom-specific `CreatureSay` branch и второй observation path не добавлялись.

## Focused native regression

Target `phantom-headless-whisper-delivery-goal030cp2c-test`, seed `30003023`, forked, cwd `dist/game`, timeout `180000 ms`, existing guarded DB, no provisioning: **PASS 3/3**, total 33 s.

Canonical `ScriptEngine.MASTER_HANDLER_FILE` выполнен в этом target; changed Java-8 script closure скомпилирован, `ChatHandler.getHandler(ChatType.WHISPER)` вернул exact `handlers.chat.channels.ChatWhisper`.

Positive evidence:

- World-resolved receiver — canonical `Player`;
- `receiver.getClient() == null`;
- `hasHeadlessOutboundSession() == true`;
- `isInOfflineMode() == false`;
- receiver effect-count delta `+1`;
- receiver recorded packet delta: exact one `CreatureSay`;
- sender получил exact one canonical `CreatureSay` echo, а не offline rejection;
- client dispatch дал `2` actual delivered events (receiver + sender echo);
- temporary bounded observer получил delivered event exact sender → headless receiver.

Negative evidence:

- World-visible receiver с null client и без headless session;
- `isInOfflineMode() == true`;
- dispatch deliveries `0`;
- sender получил exact one existing `SystemMessage` offline result;
- source contract подтверждает наличие `receiver.isInOfflineMode()` и отсутствие `receiver.getClient()` в `ChatWhisper`.

## Fresh CP2 runtime

Target `phantom-cross-domain-autonomous-alpha-goal030cp2-test`, seed `30003002`, guarded `l2jmobiush5_phantom_test`, schema SHA-256 `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.

### Run 1/2

**FAIL, 2/6 passed, 4/6 failed**, total 1 min 29 s.

- PASS: Population → Scheduler ACTIVE → real materialization/headless/PHANTOM lease/cap → non-Population autonomous decision.
- Real WHISPER больше не остановлен offline predicate: dispatch delivery assertion прошёл.
- FAIL: `пригласи меня` не создал real Party invitation за 10 s.
- FAIL: `где взять адену` не завершил exactly-one generated ITEM57 outbound.
- FAIL: `покинь группу` не сохранил `party.leave` intent в downstream state этого запуска.
- PASS: withdraw/reactivate same profileId/characterObjectId/personality/memory.
- FAIL: shutdown precondition обнаружил retained Conversation execution entries; `afterAll` всё равно выполнил canonical system shutdown, exact profile cleanup, ThreadPool/Hikari cleanup.

### Единственная run-2 fixture correction

Run 1 впервые достиг native Party gate. Population создал Phantom в другой race creation area, а manual human fixture оставался в Human Fighter creation area. `PartyInvitationService` требует `target.isVisibleFor(requester)`. Existing local test pattern `setXYZ` использован только в CP2 suite: human перемещён на 20 units от materialized Phantom, затем mutual `isVisibleFor` asserted.

Run-2 evidence: human origin `-71417,258270,-3104`; Phantom `28395,11127,-4232`; bounded corrected distance `20`.

### Run 2/2

**FAIL, 2/6 passed, 4/6 failed**, total 1 min 27 s.

- materialization/autonomous case снова PASS;
- corrected mutual visibility PASS;
- real Party invitation всё равно не появилась;
- ITEM57 generated outbound не завершился;
- `party.leave` semantic processing продвинулось дальше, но durable social receipt не вырос;
- offline/reactivate same identity/memory PASS;
- execution entries остались in-flight перед shutdown.

Runtime budget исчерпан. Сбой находится после подтверждённой native WHISPER delivery и после устранения concrete visibility fixture defect. Дополнительный production owner audit/fix запрещён scope этой задачи, поэтому итог — `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`. Три utterance causal chain, Party/social completion, ITEM57 answer text/style, canonical leave и full clean shutdown chain как единый PASS не заявляются.

## Matrix and gates

Без CP2 PASS promotion не выполнен:

- `COVERED_PRIOR = 11`;
- `COVERED_CP1 = 6`;
- `COVERED_CP2 = 0`;
- `PENDING_GOAL030 = 3`.

Pending остаются:

- `activity-materialization` → CP2;
- `restart-failure-recovery` → CP3;
- `rollback-release-control` → CP3.

Statuses: CP2A corrective result remains accepted/frozen; CP2B corrective result remains accepted/frozen; CP2 `BLOCKED / IN_PROGRESS`; Goal030 `IN_PROGRESS`; CP3 `NOT_STARTED`.

Final PASS-only targets `phantom-release-baseline-goal030cp1-test`, `phantom-conversation-checkpoint2-test`, `phantom-party-server-integration-test`, `phantom-production-materialization-test` не запускались. `jar` invocations: `0`. CP2A smoke, CP2B utility, Goal024A, 030A/B/C, soak, verify и aggregates не перезапускались.

## Changed files

Production:

- `dist/game/data/scripts/handlers/chat/channels/ChatWhisper.java`

Tests/build:

- `test/java/org/l2jmobius/gameserver/phantoms/PhantomHeadlessWhisperDeliveryGoal030CP2CSuite.java`
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomCrossDomainAutonomousAlphaGoal030Checkpoint2Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`

Docs:

- `docs/phantoms/reports/030-checkpoint-2c-headless-whisper-delivery-unblock-and-resume.md`
- `docs/phantoms/reports/030-checkpoint-2-cross-domain-autonomous-alpha.md`
- `docs/phantoms/reports/030-checkpoint-2a-java8-handler-unblock-and-resume.md`
- `docs/phantoms/reports/030-checkpoint-2b-farming-utility-unblock-and-resume.md`

User-modified `PhantomReleaseBaselineGoal030Checkpoint1Suite.java` и untracked task packages сохранены и исключаются из staging. Player/session/CreatureSay/ChatObservation/other channels не менялись. Roadmap/release matrix не менялись, потому что CP2 не прошёл.

## Architecture, DB, config, performance

Production correction заменяет transport inference на существующий semantic Player predicate; архитектура outbound session не меняется. DB schema/migrations, shipped config, dependencies, threads и logging не менялись. Focused/CP2 использовали только existing allowlisted test DB; provisioning не выполнялся. Performance claims отсутствуют; приведены только bounded target wall times.

## Commands and process truth

- Exact parent `7374e5cc3f6bcceeeb48c264585a029cc3fd9c8e`, branch `feature/phantom-world`, upstream `origin/feature/phantom-world` подтверждены.
- TASK прочитан один раз; до первой правки выполнено ровно 4 targeted searches.
- `apply_patch`: ровно 1 invocation; ACL reject до чтения/mutation; applied changes `0`; retry `0`.
- Все successful mutations выполнены exact-anchor UTF-8-no-BOM temp + atomic `Move-Item`; новый suite/report создавались bounded chunks и атомарно promoted.
- Bare `ant` не найден в PATH и завершился до Ant/test execution.
- Project-local `.phantom-local/apache-ant-1.10.17/bin/ant.bat` использован для всех реальных targets.
- Focused CP2C invocations: `1/2`, PASS 3/3.
- CP2 runtime invocations: `2/2`; оба FAIL 2/6 после прохождения materialization/autonomy и real WHISPER delivery.
- Каждый target автоматически выполнил `compile`/`compile-tests`; standalone diagnostic compile cycle не запускался.
- Context compactions observed: `0`.
- Goal counter snapshot при подготовке отчёта: `274543 tokens`, `939 s`.

## Git

Git-команды использовались только потому, что task workflow их явно требует: initial root/status/branch/HEAD/upstream; final status/diff/diff-check/scope; staging exact allowlist; ordinary commit/push. History rewriting, amend, rebase, reset, merge и force push не выполняются.

Preferred subject: `fix(phantoms): allow headless whisper delivery`.

Commit SHA и push result возвращаются в final message, поскольку report входит в commit.

## Next step

Нужна отдельная bounded corrective task для production Conversation/Party execution defect после уже доказанной native WHISPER delivery: зафиксировать точное состояние first `party.invite` execution entry/goal/Party coordinator outcome и устранить владельца без расширения CP2C. После independent review CP2 должен получить новый runtime budget. CP3 начинать нельзя.
