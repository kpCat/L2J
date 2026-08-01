# Goal 020 — exact conversation invitation ownership micro-completion

## Статус и provenance

- Status: `COMPLETED_PENDING_INDEPENDENT_REVIEW`.
- Goal 020 Checkpoint 2 foundation: принята для bounded ownership completion.
- Required parent: `75e3a07324946adb69c87e8628b4f11ac749ce8f`.
- Subject: `fix(phantoms): close exact conversation invitation ownership`.
- Branch: `feature/phantom-world`.
- Seed: `20002002`.
- Commit SHA: self; exact SHA фиксируют post-commit verifiers без amend.

## Summary

Последний micro-completion Goal 020 закрывает единственную оставшуюся границу
ownership: generic `PhantomPartyCoordinator` pulse больше не принимает и не
отклоняет invitation по conversation-owned `party.join` Goal. Полный Goal
распознаётся по ACTIVE status, типу/purpose/reason, четырём частям plan hash и
exact sequence/requester/invitee constraints. Обычный non-conversation
`party.join` сохраняет canonical automatic accept.

Execution service остаётся единственным владельцем exact ACCEPT/REFUSE через
`respondToPending`. Coordinator предоставляет read-only process-local outcome
только для полного ключа plan + identity + response kind. Production
reconciliation сначала читает этот outcome, затем использует существующее
canonical membership/Goal evidence. Отсутствие proof после restart остаётся
`UNCERTAIN`; disappearance invitation не считается success.

Все ранее принятые результаты Goal 020 сохранены: atomic handoff, capacity
reservation, CXE1/CXE2, durable binding, structured query facts, SUPPRESS_ACK,
Goal supersession, exact-counterpart delivery, DISPATCHING recovery,
PHANTOM_GENERATED loop prevention, PHANTOM-only ingress и bounded lifecycle.
Support/assist/regroup остаются typed `DEFERRED` до Goal 024.

## Changed files и scope

- Production: 3 из 4; new production: 0.
- Total: 9 из 10.
- Data/schema/config: не менялись.
- `Player.java`, `Party.java`, chat handlers, worker/thread/executor/Future/task:
  не менялись и не добавлялись.

Production:

- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java`.

Tests/process:

- `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java`;
- `tools/phantoms/verify-task-020c2.ps1`;
- этот отчёт и independent review handoff.

## Focused evidence

- `compile-tests`: PASS, 2108 production + 79 test sources.
- canonical invitation: PASS, 6/6.
- real conversation outbound/ownership integration: PASS, 2/2 после исправления
  test-only response-act fixture; первый запуск честно упал до ownership boundary
  на неизвестном `party.accept` response act.

Dynamic evidence проверяет:

- ordinary ACTIVE non-conversation `party.join` продолжает auto-accept;
- exact conversation-owned Goal остаётся pending в generic Party pulse;
- execution service отвечает exact invitation ровно один раз;
- replacement sequence того же requester становится `STALE`;
- новый requester и другой response kind не наследуют consent;
- exact COMPLETED/STALE/REJECTED outcomes сохраняют тип;
- mismatch не наследует replay proof;
- coordinator restart теряет только process-local proof;
- ACCEPT/REFUSE без replay/canonical proof остаются `UNCERTAIN`;
- no-proof refusal получает `execution.failed`, а не misleading success.

## Terminal gates

- conversation party-action: PASS, 7/7;
- conversation restart: PASS, 5/5;
- Party lifecycle: PASS, 11/11;
- verifier 020c1: PASS;
- working verifier 020c2: PASS;
- единственный final Goal 020 aggregate: PASS, `BUILD SUCCESSFUL`, 57 s.

После content freeze выполняются один полный `ant verify`, отдельный `ant jar`,
ordinary commit/push и ровно два accepted verifier 020c2. Они являются
обязательными условиями передачи результата на independent review; при любом
отрицательном результате commit/push не выполняются и статус не считается
достигнутым.

## DB, performance и риски

- Используется только `l2jmobiush5_phantom_test`; production DB не меняется.
- Новый persistence component отсутствует; replay proof намеренно process-local и
  ограничен существующим bounded map на 512 записей.
- Generic Party pulse не создаёт новый response path и не удаляет pending entry.
- После полного restart REFUSE без proof не может быть подтверждён и остаётся
  `UNCERTAIN`; это безопаснее ложного success или повторной gameplay-команды.
- Goal 021 и Goal 025 не начаты.

## Read-first и Git

Прочитаны только attachment, четыре разрешённых production-кандидата, три
целевых suite, verifier и актуальные Goal 020 handoff-документы. Переиспользованы
существующие exact replay key, Goal evidence helpers, canonical Party backend и
shared execution pulse. Исходные Goal 020 packages, roadmap, master plan, старые
reports и unrelated subsystems не перечитывались.

Git разрешён micro-completion workflow для provenance/scope/diff, exact staging,
одного ordinary commit и обычного push. Amend, rebase, squash, merge, reset,
restore, force и force-with-lease не используются.
