# Goal 020 Checkpoint 2 — conversation execution safety completion

## Статус и provenance

- Status: `COMPLETED_PENDING_INDEPENDENT_REVIEW`.
- Foundation verdict: `ACCEPT_WITH_EXECUTION_SAFETY_COMPLETION`.
- Required parent: `6d7ac26ff614d0e565589fdfc303684743b32cd9`.
- Subject: `fix(phantoms): finalize conversation execution safety`.
- Branch: `feature/phantom-world`.
- Seed: `20002002`.
- Commit SHA: self; exact SHA фиксируют post-commit verifiers без amend.

## Summary

Восемь обязательных execution-safety findings закрыты в bounded completion
Checkpoint 2. Planner теперь резервирует место для будущего receipt до атомарного
handoff, invitation response связывается с exact sequence/requester/invitee/kind,
а restart не повторяет неподтверждённую Party или chat операцию.

Query boundary возвращает не готовые фразы, а не более восьми уникальных
`QueryFact` с authority evidence. Русские labels и templates принадлежат XML
catalog. Conversation-owned Goal хранит plan evidence, current party
group/generation и topology snapshot; допустимая замена membership Goal выполняется
в одной транзакции с `conversation.execution`.

WHISPER, PARTY, GENERAL и TRADE проходят реальные зарегистрированные
`IChatHandler` под `Origin.PHANTOM_GENERATED`. Успех требует доставки exact
counterpart; частичная доставка и exception становятся `UNCERTAIN`, нулевая —
`FAILED`. `SUPPRESS_ACK` подавляет только chat acknowledgement, но не действие.

Support, assist и regroup остаются typed `DEFERRED` до Goal 024.

## Архитектурные решения

- Матрица admission учитывает live receipts, live entries и новый entry:
  `15+0+1` разрешено, `15+1+1` и `16+0+1` отклоняются до изменения planner state.
- Codec пишет `CXE2`, читает `CXE1/CXE2`, fail-closed на неизвестной версии,
  trailing bytes и envelope больше 4096 bytes.
- Exact invitation binding сохраняется до response boundary. ACCEPT после crash
  завершается только по точному membership/Goal proof; REFUSE без durable proof
  остаётся `UNCERTAIN`.
- Party replay key включает plan ID, invitation identity и response kind; outcome
  переиспользуется только для точного ключа.
- Execution selection имеет детерминированный приоритет recovery/compaction/work;
  capacity retry назначается к earliest receipt expiry без pulse spin.
- Leave может заменить exact leader/member membership Goal; travel — только
  leader Goal. Member travel без отдельного контракта отклоняется.
- Conversation code не выполняет gameplay mutation и не владеет worker/thread.

## Changed files и scope

- Changed production/data: 12 из 14.
- New production/data: 0 из 2.
- Total changed: 17 из 26.
- Schema/migration/config keys: отсутствуют.
- `Player.java`, `Party.java`, existing chat handlers и другие хроники не менялись.

Production/data:

- `dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml`;
- `java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationModel.java`;
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java`;
- шесть `PhantomConversationExecution*` contracts/catalog/codec/store/service files;
- `java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`.

Test/process artifacts:

- `PhantomConversationExecutionSuite.java`;
- `PhantomConversationIntegrationSuite.java`;
- `verify-task-020c2.ps1`;
- этот отчёт и independent review handoff.

## DB, data и config

- Использовалась только `l2jmobiush5_phantom_test`; production DB не менялась.
- Schema и migrations не менялись.
- Новый config key не добавлялся.
- XML policy сохраняет 13 proposals и hard bounds; добавлены только строгие
  structured fact labels.

## Focused results

- `compile-tests`: PASS, 2108 production + 79 test sources.
- managed ingress: PASS, 4/4.
- catalog/codec: PASS, 4/4.
- atomic handoff/capacity: PASS, 3/3.
- structured queries: PASS, 3/3.
- canonical invitation: PASS, 4/4.
- party actions: PASS, 7/7.
- real four-channel outbound: PASS, 1/1.
- restart idempotency: PASS, 4/4.
- execution lifecycle/performance: PASS, 3/3.

Evidence включает four/five slot boundary, receipt matrix и expiry reopen,
binding round-trip, replacement invitation, accept/refuse crash recovery,
ack suppression, atomic Goal supersession, wrong/zero/exception delivery,
recovered `DISPATCHING` priority и отсутствие capacity spin.

## Terminal gates

- Affected aggregate на окончательном production/test дереве: PASS,
  `BUILD SUCCESSFUL`, 1:52.
- Verifier 020c1 descendant check: PASS, `TASK020C1_VERIFIER_OK`.
- Verifier 020c2 working mode: PASS, `TASK020C2_VERIFIER_OK`; scope 17,
  production 12, new production 0, policy SHA-256
  `AFDEA6953131C29508233BED64ABCD77FFD25C8FCA07A303D1FEAB78CA6148A6`.
- Единственный final checkpoint2 aggregate: PASS, `BUILD SUCCESSFUL`, 0:53.
- Один full `ant verify`: обязательный post-freeze commit condition; commit не
  создаётся без `BUILD SUCCESSFUL`.
- Standalone `ant jar`: обязательный post-freeze commit condition; commit не
  создаётся без `BUILD SUCCESSFUL` и рабочих JAR в `dist/libs`.
- Mojibake-маркеры в 17 изменённых файлах: PASS.
- Escaped Cyrillic в 17 изменённых файлах: PASS.
- Ordinary commit/push: post-freeze evidence; amend/rebase/squash/merge/force
  запрещены.
- Два byte-identical accepted verifier 020c2: обязательное post-commit evidence.

## Ограничения и риски

- Протокол High Five не подтверждает получение клиентом: сохранённый
  `DISPATCHING` после crash становится `UNCERTAIN`, поэтому допустима потеря, но
  автоматический duplicate запрещён.
- REFUSE не имеет durable membership effect и после crash честно остаётся
  `UNCERTAIN`.
- Receipt backpressure намеренно задерживает профиль до replay expiry.
- Query сообщает только текущие bounded authoritative facts и не обещает
  route/purchase/combat result.
- Tactical support/assist/regroup остаётся вне scope до Goal 024.
- Completion ещё не прошла независимый review.

## Read-first и deviations

Прочитаны completion attachment, закрытый READ_SET, два C2 suite, verifier и
foundation report/review. Переиспользованы multi-component transaction,
goal.runtime optimistic mutation, shared composite scheduler, canonical Party
invitation coordinator, Game Knowledge/topology queries, materialization action
lease и текущий chat handler registry.

Root README и `docs/phantoms/CONTEXT_INDEX.md` отсутствуют и повторно не искались.
Для диагностики fixture дополнительно read-only просмотрены четыре текущих chat
handler и ближайшие `Party` group methods; production handler/Party code не менялся.
Apache Ant отсутствовал в PATH, поэтому использован официальный локальный
Apache Ant 1.10.17 из пользовательского cache; в repository он не добавлялся.

## Git

Git-команды разрешены completion workflow только для provenance/scope/diff,
ordinary commit и push. До terminal gate использованы `git status`,
`git branch --show-current`, `git rev-parse`, `git diff --name-status`,
`git diff --stat`, `git diff --numstat`, bounded `git diff` и
`git ls-files --others --exclude-standard`. После freeze допустимы только
`git diff --check`, bounded diff audit, `git add`, staged audit, обычный commit и
`git push origin feature/phantom-world`. Amend, rebase, squash, merge, reset,
restore, force и force-with-lease не используются.

Следующий шаг после terminal gates — только независимый review completion;
Goal 021/025 не начат.
