# Контракт исходящих ответов и действий Goal 020

## Граница системы

Goal 020 завершает один причинный контур:

```text
фактическая CLIENT_CHAT-доставка управляемому Phantom
→ детерминированный conversation plan
→ атомарный conversation.state + conversation.execution
→ строгая авторизация
→ read-only query либо canonical Goal/Party action
→ один исходящий ответ через текущий chat handler
→ durable terminal receipt
```

Conversation не владеет gameplay-механикой. Код диалога не меняет `Player`,
`Party`, inventory, movement, combat или commerce напрямую и не вызывает client
packet handlers. Действия проходят через `goal.runtime`, Decision и узкий
canonical Party seam.

## Ingress

`ChatObservationService` остаётся общей delivery boundary. Вход принимается
только при `Origin.CLIENT_CHAT` и только если
`PhantomIdentityLeaseRegistry.getOwnerKind(recipientObjectId) == PHANTOM`.
Проверка выполняется до ingress queue, batch, context и DB. Identity повторно
проверяется перед построением context.

`Origin.PHANTOM_GENERATED` существует только для аудита исходящей доставки.
Generated callback не создаёт conversation batch. Loop prevention основан на
типизированном origin, а не на сравнении текста.

Delayed → due promotion расходует одну operation. Overflow немедленно закрывает
точный dispatch и удаляет его из managed membership; pulse не сканирует все
batch/due/delayed/tombstone структуры.

## Durable handoff

Компонент `conversation.execution`, schema version 1, хранится в существующей
таблице profile components. Payload ограничен 4096 байтами и содержит не более
четырёх entries и шестнадцати receipts. Codec имеет объявленный worst case 4076
байт, строгий UTF-8, полный uppercase SHA-256 plan ID, проверку enum/version/range,
запрет trailing bytes и строгие immutable transitions.

Planner в фазе PERSISTING вызывает одну сортированную multi-component транзакцию:

```text
conversation.state mutation
+ conversation.execution PREPARED entry
```

Обе записи фиксируются или откатываются вместе. Plan sink после commit — только
ограниченный wake signal. Истиной остаётся durable component. Startup читает
только профили с `conversation.execution` страницами не более 256 и по одному
budgeted entry восстанавливает due membership.

Receipt внутри `replayHorizonMinutes` не вытесняется. При заполненных шестнадцати
receipts terminal entry остаётся durable и блокирует дальнейшую ёмкость до
истечения старого receipt. Это сохраняет at-most-once ценой безопасного
backpressure.

## Shared-scheduler execution

Единственный writer имеет lifecycle `NEW → RUNNING → STOPPING → STOPPED`, без
собственного worker/thread/executor/Future. Общий pulse считает каждую границу:
recovery page/entry, delayed promotion, load, authorize, query, Goal
submit/observe, Party response, outbound prepare/dispatch и terminal store.
Configured operation budget не превышается. Внешний authority/canonical/chat
вызов не выполняется под внутренним monitor.

Shutdown сначала закрывает conversation admission и завершает atomic handoff,
затем закрывает execution admission. Начатый outbound либо получает durable
terminal state, либо остаётся `DISPATCHING` и после restart становится
`UNCERTAIN`. PREPARED work остаётся recoverable.

## Строгая политика

`high-five-ru-conversation-execution-v1.xml` читается strict UTF-8 и XXE-safe,
контент адресуется SHA-256. Loader требует точный набор proposal kinds, channels,
slots, target namespaces, Goal types, styles, reason keys, templates и bounds.
Java переключается только по проверенным proposal keys/kinds; русские шаблоны
остаются в data.

Query allowlist использует текущие immutable Game Knowledge, topology и Party
claims:

- `party.role.query` — текущая роль, generation и вакансии;
- `entity.locate` — только подтверждённый topology node для NPC/content/node;
- `item.acquire` и `item.source` — только известные drop/spoil/manor/recipe facts;
- `content.requirements` — текущая curated recommendation и capability facts.

Facts строятся прежде текста, ограничены 128 UTF-8 байтами и проходят строгий
renderer. Query не создаёт Goal и ничего не меняет.

## Goal и Party

`party.invite`, `party.leave`, `party.travel` создают conversation-owned Goal с
детерминированным positive ID и четырьмя частями plan hash в constraints.
Goal type берётся из execution policy. Invite содержит точный target; leave —
текущий party generation; travel — текущие generation, x/y/z/instance и
подтверждённый topology destination. Чужой ACTIVE Goal всегда даёт `goal.busy`.

`party.accept` атомарно создаёт точный `party.join` Goal до canonical response.
`party.refuse` Goal не создаёт. Оба пути используют только текущую pending
invitation identity и exact counterpart. ACCEPT дополнительно требует ACTIVE
conversation Goal с совпадающим plan evidence. Повтор того же plan идемпотентен,
другой plan и stale identity отклоняются.

`party.support`, `party.assist`, `party.regroup` остаются typed `DEFERRED` до
Goal 024 и не производят gameplay mutation.

## Исходящий ответ

Перед отправкой повторно проверяются profile/object ownership, materialization
action lease, exact counterpart, channel state и текстовые bounds. WHISPER,
PARTY, GENERAL и TRADE вызывают текущий зарегистрированный `IChatHandler`, поэтому
recipient/range/party rules остаются handler-owned.

Перед handler call entry атомарно переходит `PREPARED → DISPATCHING`. Нормальный
возврат даёт `SENT`; ошибка после границы даёт `UNCERTAIN`. Restart никогда не
повторяет `DISPATCHING`. Для одного source plan отправляется не более одного
сообщения; Goal completion по умолчанию не создаёт второе сообщение.

При выключенном Phantom World execution XML не загружается, observation/generated
dispatch не устанавливается и DB access к `conversation.execution` отсутствует.
