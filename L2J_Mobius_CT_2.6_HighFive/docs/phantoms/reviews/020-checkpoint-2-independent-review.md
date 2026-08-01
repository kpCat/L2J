# Goal 020 Checkpoint 2 — independent review handoff

- Статус: `PENDING_INDEPENDENT_REVIEW`.
- Implementation parent: `21ba300fc612f9777891912f80efc633f5b6db18`.
- Subject: `feat(phantoms): activate conversation responses and actions`.
- Ветка: `feature/phantom-world`.
- Seed: `20002002`.

## Переданный результат

Checkpoint 2 реализует durable execution envelope, строгую policy/data authority,
canonical read queries, conversation-owned Goal submission, exact pending Party
response и at-most-once generated chat через текущие WHISPER/PARTY/GENERAL/TRADE
handlers. Checkpoint 1 зафиксирован как `ACCEPT_WITH_ACTIVATION_GATE`; его ingress
и bounded housekeeping activation requirements закрыты в этом child.

Support, assist и regroup намеренно остаются `DEFERRED` до Goal 024. Goal 021 и
Goal 025 не начаты.

## Что проверить независимо

1. Atomicity `conversation.state + conversation.execution` и
   `conversation.execution + goal.runtime` при injected conflicts/crash.
2. Exact operation accounting, page size 256, отсутствие worker и residue.
3. Replay-horizon backpressure и отсутствие повторной отправки из
   `DISPATCHING` после restart.
4. Grounding каждого query в текущем Game Knowledge/topology/Party claim.
5. Exact invitation identity, counterpart и plan evidence для ACCEPT/REFUSE.
6. Реальные handler-owned recipient/range/party rules и отсутствие generated
   loop в conversation ingress.
7. Scope: нет `Player.java`, `Party.java`, handler implementation, schema или
   прямой gameplay mutation.

Этот файл не принимает Checkpoint 2. Итоговый статус до независимого review —
только `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
