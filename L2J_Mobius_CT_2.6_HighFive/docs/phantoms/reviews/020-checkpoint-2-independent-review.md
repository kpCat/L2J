# Goal 020 Checkpoint 2 — independent review handoff

- Foundation verdict: `ACCEPT_WITH_EXECUTION_SAFETY_COMPLETION`.
- Accepted foundation commit: `6d7ac26ff614d0e565589fdfc303684743b32cd9`.
- Completion status: `PENDING_INDEPENDENT_REVIEW`.
- Completion subject: `fix(phantoms): finalize conversation execution safety`.
- Ветка: `feature/phantom-world`.
- Seed: `20002002`.

## Переданный результат

Foundation Checkpoint 2 принята только с обязательным завершением восьми
execution-safety findings в одном ordinary child. Completion резервирует receipt
capacity до planner handoff, сохраняет exact invitation binding, различает
неподтверждаемую отправку и отказ, а также восстанавливает accept/refuse без
повторной gameplay-команды.

Query boundary возвращает только bounded structured facts от текущих
Game Knowledge, topology и Party claim; русское представление принадлежит XML
catalog. Conversation-owned Goal сохраняет plan, party group/generation и
topology evidence. Замена ACTIVE membership Goal разрешена только для exact
leave и leader travel; member travel без отдельного контракта отклоняется.

WHISPER, PARTY, GENERAL и TRADE проходят текущие зарегистрированные handlers под
`Origin.PHANTOM_GENERATED`. Успех требует доставки exact counterpart; частичная
или неподтверждаемая доставка становится `UNCERTAIN`, нулевая — `FAILED`, и ни
один terminal outcome не отправляется повторно.

Support, assist и regroup остаются typed `DEFERRED` до Goal 024. Goal 021 и
Goal 025 не начаты.

## Что проверить независимо

1. Матрицу reservation: `15 receipts + 0 entries` допускает новый handoff,
   `15 + 1` и `16 + 0` отклоняются до изменения `conversation.state`.
2. Codec `CXE2`, чтение legacy `CXE1`, предел 4096 bytes и exact
   invitation sequence/requester/invitee/response.
3. Restart reconciliation: ACCEPT подтверждается только membership/Goal
   evidence, отказ без доказательства остаётся `UNCERTAIN`.
4. Exact Party replay key: plan + invitation identity + response kind + outcome.
5. `SUPPRESS_ACK`: действие выполняется без chat boundary, но query,
   clarification и factual error не подавляются.
6. Atomic Goal supersession и сохранность claim/group/generation/topology
   evidence.
7. Structured QueryFacts: не более восьми уникальных facts, без русских строк в
   production adapter.
8. Реальные handler-owned recipient/range/party/trade rules и отсутствие
   generated loop в conversation ingress.
9. Scope: нет `Player.java`, `Party.java`, handler implementation, schema,
   отдельного worker или прямой gameplay mutation из conversation code.

Этот файл принимает только foundation и не принимает completion. Итоговый
статус до отдельного независимого review — `PENDING_INDEPENDENT_REVIEW`.
