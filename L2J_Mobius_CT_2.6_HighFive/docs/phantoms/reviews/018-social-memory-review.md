# Independent review — Goal 018

Статус: `ACCEPT_WITH_ACTIVATION_GATE`.

Принятый commit:
`d30b657a9351d8cb099548e959854bf826b7d1d1`.

Subject:
`feat(phantoms): add social memory and relationships`.

## Решение

Goal 018 принимается как bounded social memory foundation. Реализованные
catalog, personality, compact state, relationships, memories, lazy decay,
idempotent ingestion, modifiers и downstream Party sink остаются принятыми.

Production social-код в Goal 019 не изменяется и не потребляется. Он не
становится authority для semantic understanding, intent selection или
grounding.

## Activation gate перед Goal 020

До подключения conversation/actions обязательно закрыть один temporal и
idempotency gate:

1. Retained-memory idempotency horizon должен иметь явно доказанную границу.
   Повтор canonical event после вытеснения retained ID не должен незаметно
   повторно применять relationship, reputation, debt или agreement effect.
2. Для out-of-order и expired events требуется однозначная causality policy.
   Старое событие не должно оживлять истёкшую memory, откатывать более новое
   состояние или менять результат в зависимости от порядка доставки.
3. `party.member.joined` должен публиковаться ровно при первом canonical
   membership transition. Retry, reload, reconciliation и повторное наблюдение
   уже существующего membership не являются новым join.
4. Gate требует focused regression evidence для duplicate beyond retained
   horizon, reordered/expired event и first-membership emission.

Эти пункты документируют обязательную границу, но не реализуются Goal 019.
Gate должен быть закрыт отдельной bounded задачей до начала Goal 020.

## Зафиксированные статусы

- Goal 017: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`;
- Goal 018: `ACCEPT_WITH_ACTIVATION_GATE`;
- Goal 019: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 020/021/025: `NOT_STARTED`.
