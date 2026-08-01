# Goal 020 — exact conversation invitation ownership review handoff

- Foundation verdict: `ACCEPT_WITH_EXECUTION_SAFETY_COMPLETION`.
- Accepted execution-safety commit: `75e3a07324946adb69c87e8628b4f11ac749ce8f`.
- Ownership completion: `PENDING_INDEPENDENT_REVIEW`.
- Subject: `fix(phantoms): close exact conversation invitation ownership`.
- Branch: `feature/phantom-world`.
- Seed: `20002002`.

## Переданный micro-completion

Generic Party pulse теперь разделяет обычный explicit `party.join` и полностью
размеченный conversation-owned `party.accept`. Обычный Goal сохраняет automatic
accept. Conversation-owned Goal никогда не вызывает `_backend.respond` из
generic lane, не удаляет pending invitation и оставляет exact response execution
service.

Coordinator сохраняет bounded process-local outcome по полному ключу plan ID,
sequence, requester, invitee и ACCEPT/REFUSE. Новый read-only lookup не выполняет
gameplay mutation. Adapter проверяет exact replay до pending/canonical state:
COMPLETED, STALE и REJECTED сохраняют смысл; mismatch не наследует результат.
После restart ACCEPT требует exact canonical membership/Goal proof, REFUSE без
proof остаётся `UNCERTAIN`.

## Что проверить независимо

1. `conversationOwnsAccept` требует одновременно ACTIVE `party.join`,
   `conversation.action`, `conversation.party.accept`, четыре plan parts и три
   invitation identity constraints.
2. Generic pulse не отвечает ни на exact conversation invitation, ни на
   replacement/new-requester invitation по старому Goal.
3. Единственный mutation path — execution-owned `respondToPending` с durable
   exact binding; retry не вызывает backend повторно.
4. `conversationResponseOutcome` читает только полный exact key и не мутирует
   Party/gameplay state.
5. Reconciliation сначала использует replay outcome; disappearance invitation
   не превращается в success, replacement остаётся `STALE`.
6. Coordinator restart без replay proof остаётся консервативным; misleading
   success response отсутствует.
7. Scope: 3 production и 9 total, без data/schema/config, `Player.java`,
   `Party.java`, chat handlers, новых worker или persistence component.
8. Все ранее принятые Goal 020 contracts и typed `DEFERRED` до Goal 024
   неизменны; Goal 021/025 не начаты.

Этот handoff не принимает micro-completion самостоятельно. Финальный статус до
отдельного независимого review — `PENDING_INDEPENDENT_REVIEW`.
