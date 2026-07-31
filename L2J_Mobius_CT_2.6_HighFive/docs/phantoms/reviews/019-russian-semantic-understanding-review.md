# Independent review — Goal 019

Статус: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`.

Принятый commit:
`384b521f2cd29f4162c9aca9116eb0ff40cbd681`.

Parent:
`d30b657a9351d8cb099548e959854bf826b7d1d1`.

Subject:
`feat(phantoms): add russian semantic understanding`.

## Решение

Goal 019 принимается как bounded immutable Russian semantic understanding:
strict content-addressed XML/TSV, детерминированная нормализация,
data-declared aliases, bounded intent/slot matching, context-safe player refs и
grounding через current Game Knowledge, topology и party-role authority.

Результат semantic understanding остаётся только структурированной
интерпретацией. `ACCEPTED` не является разрешением отправить сообщение,
создать gameplay goal или выполнить party, movement, combat, trade, inventory
либо иное действие.

Historical verifier фиксируется на принятом commit, проверяет его exact parent,
subject, blobs и scope и допускает текущий HEAD/remote только как descendants.

## Explicit future contracts

Перед использованием в conversation planning Goal 020 Checkpoint 1 обязан:

1. Закрыть Goal 018 durable-causality gate: отдельные bounded receipts,
   атомарная запись `social.state` + `social.receipts`, stale/out-of-order policy
   и first canonical `party.member.joined` emission.
2. Усилить semantic identity и slot namespace validation, duplicate-slot и
   unsafe pattern-shape rejection.
3. Считать candidate-budget exhaustion неполным поиском и возвращать
   `clarify.complexity`, не выбирая частично исследованного winner.
4. Добавить bounded observer-only fragment resolution для clarification
   continuation и дождаться start-claim drain при остановке.
5. Доказать grounding на реальных production topology, Game Knowledge и
   party-role authorities, включая fail-closed missing target.

Goal 020 Checkpoint 1 может только наблюдать actual delivered chat, сохранять
bounded conversation context и публиковать observer-only response/action plans.
Outbound chat, durable delivery, canonical action authorization, execution,
flood control и generated-message loop suppression принадлежат следующему
action/outbound checkpoint и здесь не начинаются.

## Зафиксированные статусы

- Goal 018: `ACCEPT_WITH_ACTIVATION_GATE`;
- Goal 019: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`;
- Goal 020 Checkpoint 1: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` только после
  прохождения всех обязательных gates текущей задачи;
- Goal 020 action/outbound checkpoint: `NOT_STARTED`;
- Goal 021/025: `NOT_STARTED`.
