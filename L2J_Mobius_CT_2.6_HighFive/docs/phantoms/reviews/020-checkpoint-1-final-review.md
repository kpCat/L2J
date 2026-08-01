# Goal 020 Checkpoint 1 — final review

- Статус: `ACCEPT_WITH_ACTIVATION_GATE`.
- Принятый commit: `21ba300fc612f9777891912f80efc633f5b6db18`.
- Implementation commit: `e7ba469e63caa6dee113278087258fab005a435a`.
- Required parent: `384b521f2cd29f4162c9aca9116eb0ff40cbd681`.
- Completion subject: `fix(phantoms): complete conversation planning safety`.

## Решение

Checkpoint 1 принят как bounded observer-only слой наблюдения и планирования:
он наблюдает только фактически доставленный final-filtered chat, закрывает точный
delivery set по `DISPATCH_CLOSED`, ведёт ограниченное durable состояние и публикует
неисполняемый response/action plan.

Исторический verifier `020c1` закреплён на принятом commit и проверяет его exact
parent, subject, blobs и scope. Текущий HEAD и remote допустимы только как потомки
принятого дерева.

## Activation gate

Checkpoint 1 сам по себе не разрешает outbound chat или gameplay action. До
активации Checkpoint 2 обязательны:

1. PHANTOM-only ingress до queue, batch, context и persistence;
2. bounded delayed promotion, где каждый переход delayed → due расходует operation;
3. гарантированная терминализация forced overflow без остатка в индексах;
4. атомарная передача `conversation.state` + `conversation.execution`;
5. durable at-most-once outbound и canonical action execution без прямых gameplay
   mutations из conversation code.

Эти пункты принадлежат Goal 020 Checkpoint 2 и не изменяют историческое принятое
дерево Checkpoint 1.

## Зафиксированные статусы

- Goal 018: `ACCEPT` после закрытия activation gates в Checkpoint 1;
- Goal 019: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`;
- Goal 020 Checkpoint 1: `ACCEPT_WITH_ACTIVATION_GATE`;
- Goal 020 Checkpoint 2: `NOT_STARTED` на границе этого review;
- Goal 021 и Goal 025: `NOT_STARTED`.
