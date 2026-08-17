# Goal 026 Checkpoint 2 — canonical CommandChannel lifecycle

## Status

- Historical implementation result: `SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Independent verdict: `ACCEPT`.
- Accepted commit: `bbd29495a19a322c0629509c85c31fe508ae8d07`.
- Goal 026 Checkpoint 1 + Goal 026A: `ACCEPT`.
- Goal 026 overall: `IN_PROGRESS`.

## Summary

- Добавлен generic transport-neutral `CommandChannelInvitationService` в ordinary server model.
- Exact pending invitation содержит monotonic sequence и object IDs requester/invitee; состояние ограничено одним pending на invitee и одним ownership slot на requester.
- `Player.activeRequester` и штатный request timeout остаются time/authority source; expiration выполняется лениво без scheduler, thread или Future.
- `ACCEPT` повторно проверяет exact Party leadership/identity, CommandChannel state и formation authority.
- Canonical mutation выполняют только существующие `CommandChannel` constructor, `addParty`, `removeParty` и `disbandChannel`.
- Ordinary MPCC packets сохраняют wire decode/name lookup и делегируют shared service.
- Goal017 backend получил узкий `MemberRef` seam `invite/respond/observe/dismiss` без выдачи mutable `Player`, `Party` или `CommandChannel`.
- Phantom `ACCEPT/REFUSE` остаются отдельной target-side операцией с exact identity; auto-accept отсутствует.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/model/groups/CommandChannelInvitationService.java`;
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestExAskJoinMPCC.java`;
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestExAcceptJoinMPCC.java`;
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestExOustFromMPCC.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`.

Tests/routes:

- `test/java/org/l2jmobius/tests/phantoms/PhantomCommandChannelLifecycleSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `build.xml`.

Documentation:

- `docs/phantoms/tasks/026-checkpoint-2-command-channel-lifecycle/*`;
- `docs/phantoms/reports/026-checkpoint-2-command-channel-lifecycle.md`.

Bounded exception относительно обычного лимита 8–10 файлов: task требовал один service, три packet adapters, два backend artifacts, focused/affected tests, Ant routes, task package и report. Независимые подсистемы не затрагивались.

## Architecture decisions

- Formation right сохранён точно: clan leader при clan level >= 5, либо item 8871, либо pledge class >= 5 вместе со skill 391.
- Ordinary packet передаёт exact найденный `Player`; service канонически требует текущего Party leader. Phantom seam отвергает `MemberRef`, который не является exact current target Party leader.
- Invitation identity обязательна для response. Wrong/old identity возвращает `STALE_INVITE` и не снимает более новый matching pending/request.
- `REFUSE`, expiry и terminal revalidation failure снимают только matching pending и matching `Player` request relation.
- Requester Party, target Party, leader или CC object drift между invite и `ACCEPT` обрабатываются fail-closed.
- Dismiss разрешается только exact current CC leader против другой Party того же CC. Own Party/self loophole закрыт; less-than-two disband выполняет штатный `CommandChannel`.
- Default methods `PhantomPartyBackend` возвращают typed `UNSUPPORTED`, чтобы unrelated in-memory backends не фабриковали MPCC. Production L2J backend переопределяет все четыре операции.
- Generic service не использует World/global discovery.

## DB, migrations and configs

- Schema, migrations и config keys не добавлялись.
- Production DB `l2jmobiush5` тестами не изменялась.
- Focused integration использовала allowlisted `l2jmobiush5_phantom_test`.
- Phantom enable/disable lifecycle не менялся; новых workers нет.

## Commands and results

Baseline:

- `git rev-parse --show-toplevel`: `C:/Users/endim/L2J_Mobius`;
- branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`;
- required parent: `e3f44333df659d3ba3f258739e1e0bba8bb6a53b`.

Compilation:

- `ant compile-tests`: initial FAIL — fallback patch обрезал tail нового service;
- affected compile rerun: production compile PASS, test compile FAIL — два assertions использовали отсутствующий `Player` shortcut, а новый interface seam ещё не имел fail-closed defaults;
- дефекты исправлены локально; следующий разрешённый focused target успешно выполнил production и test compilation.

Required gates:

- `ant phantom-command-channel-lifecycle-test`: PASS, 6/6, seed `26002621`;
- `ant phantom-party-server-integration-test`: PASS, 9/9, seed `17001701`;
- `ant phantom-command-channel-checkpoint2-test`: PASS, CP2 6/6 + Goal017 9/9;
- `ant jar`: PASS; собраны `LoginServer.jar`, `GameServer.jar` и `DatabaseInstaller.jar`.

Forbidden gates не запускались: plain `ant verify`, Goal025 aggregate, Goal026 CP1 aggregate, broad all-Phantom suite и stress loops.

Editing/tool deviation:

- packaged `apply_patch` был недоступен из-за Windows ACL (`Access is denied`);
- использован patch-based fallback через Git plumbing и `git apply`;
- shell overwrite production/test logic не использовался;
- три packet-файла после ConPTY patch input были точно нормализованы CRLF → LF до `git diff --check`.

## Test coverage

Focused CP2:

- service/packet delegation и negative scope;
- exact validation и три formation authority families;
- exact `REFUSE`, stale identity и retry ownership;
- canonical new/existing CC `ACCEPT`;
- Party identity drift и `Player` timeout fail-closed;
- exact CC-leader dismiss и canonical disband.

Goal017 affected regression:

- exact Phantom `MemberRef` requester и exact real target leader;
- отсутствие auto-accept;
- отдельные target-side `REFUSE` и `ACCEPT`;
- canonical shared CC identity;
- exact CC-leader dismiss.

## Performance

- Новый lifecycle не создаёт thread, scheduler или Future.
- Pending state ограничен индексами exact records и очищается лениво.
- Нет DB query, global player scan, candidate discovery или hot-path logging.
- Focused CP2 target: 32 s; Goal017 affected target: 29 s; final aggregate: 40 s; `ant jar`: 16 s.
- Отдельный performance smoke не запускался: TASK его не разрешал.

## Deviations

- Master plan не перечитывался в историческом CP2 запуске: task-specific read budget прямо запрещал master-plan/historical reread.
- Из-за недоступности packaged `apply_patch` применялся описанный unified-diff fallback.
- Первичные compile failures исправлены; frozen final gates были зелёными.

## Limitations and risks

- Invitation state остаётся transient in-memory и намеренно не persist-ится.
- Ordinary wire response не несёт sequence token; packet adapter отвечает на exact current observed pending identity. Phantom/backend API требует expected identity.
- Human target сохраняет normal MPCC prompt; будущая Phantom policy должна отдельно решить `ACCEPT/REFUSE`.
- Candidate discovery, recruitment scoring, chat, gathering, navigation, entry, combat, retreat, persistence и worker не реализованы CP2.

## Branch, parent, commit and push

- branch: `feature/phantom-world`;
- required parent: `e3f44333df659d3ba3f258739e1e0bba8bb6a53b`;
- commit subject: `feat(phantoms): add command channel lifecycle`;
- accepted commit / remote baseline: `bbd29495a19a322c0629509c85c31fe508ae8d07`;
- ordinary push `origin feature/phantom-world` был независимо подтверждён review.

## Unfinished findings

Блокирующих CP2 findings нет. CP2 независимо принят; Goal 026 overall остаётся `IN_PROGRESS`.

## Context compaction

occurred_context_compaction: yes

## Next step

Goal 026 Checkpoint 3 реализуется отдельным bounded checkpoint; CP2 product gates не повторяются только из-за этой UTF-8 документационной правки.

GOAL_026_CHECKPOINT_2_COMMAND_CHANNEL_LIFECYCLE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
