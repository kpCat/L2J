# Контракт транзакций Goal 022 Checkpoint 1

## Граница

Checkpoint 1 поддерживает только `SELF_CRAFT` и `ITEM_ENCHANT`. Direct trade,
private stores, player manufacture, mail, clan warehouse и весь Goal 023+
остаются вне реализации.

`Player`, inventory, `RecipeManager`, `RecipeData`, `EnchantItemData` и текущие
enchant formulas остаются каноническими владельцами игровых правил. Economy
kernel владеет только admission, durable identity, reservation, dispatch state,
reconciliation и audit.

## Durable operation

Operation ID детерминирован из profile, character, Goal/revision, kind, attempt,
intent, authority, activity generation/tick. В payload сохраняются только
ограниченные before/intent facts; authoritative inventory и vitals не
дублируются.

Состояния:

```text
PREPARED -> RESERVED -> DISPATCHING -> OBSERVING -> COMMITTED
    |          |              |            |
    +----------+-> ABORTED     +------------+-> INCONSISTENT
    +----------+-> EXPIRED
```

`EXPIRED` разрешён только из `PREPARED`/`RESERVED`. После `DISPATCHING` нет
expiry, повторного dispatch или догадки о результате: неоднозначность всегда
переходит в `INCONSISTENT`.

## Lock order

Все записи используют один стабильный порядок:

1. participant profile IDs по возрастанию;
2. operation row;
3. reservation canonical keys;
4. background component rows;
5. character row;
6. class-specific recipe/skill rows;
7. item objects по `object_id`.

Reservation key включает owner и, для recipe/skill, class index. Один profile
имеет не более одной active operation; операция содержит не более 32
reservations, 24 distinct item IDs и четырёх participants.

## Craft

Active self-craft проходит только через packet-independent overload
`RecipeManager.requestMakeItem(..., RecipeCraftObserver)`. Observer получает
immutable accepted/consumed/terminal events и не меняет canonical craft.

Background self-craft принимает без преобразования exact Goal 021
`RecipePlan`, повторно проверяет current `RecipeData`, recipe/skill ownership,
ingredients, HP/MP и config authority, затем атомарно меняет item rows, vitals,
background component, acquisition state, Goal, operation и audit. ALT creation
для background закрывается как `ACTIVE_REQUIRED`.

## Enchant

`EnchantItemService` — единый packet-independent владелец active mutation.
`RequestEnchantItem` сохраняет прежнюю packet validation/timing границу и
делегирует exact target/scroll/support этому service. Phantom-код не создаёт и
не вызывает packet handlers.

Background projection использует те же immutable template, scroll group,
support, success, safe, blessed и crystal formulas. Ordinary destruction
разрешается только explicit Goal policy при достаточном replacement reserve.
Equipped background target, augmented/elemented/time-limited/leased items и
unsupported capacity переходят в `ACTIVE_REQUIRED` либо conflict без мутации.

## Integration и lifecycle

Один conflict port защищает accepted NPC commerce, acquisition/background
writers и materialization boundaries. Predispatch operation на boundary
абортируется и освобождает reservations; boundary при dispatched/observing
operation fail-stop блокируется до reconciliation.

Kernel не создаёт thread, executor, worker, `Future` или scheduled task. Его
вызывают существующие Decision steps, причём один step выполняет не более одной
durable transition: reserve, dispatch или reconcile.

## Audit и восстановление

Terminal audit содержит result/reason, consequence payload и явные суммы
items consumed/produced, adena source/sink, crystals produced и destroyed
targets. Audit не является mutable authority.

На restart `DISPATCHING`/`OBSERVING` не запускаются повторно и фиксируются как
`INCONSISTENT`. Shutdown закрывает admission, освобождает только predispatch
operations и не пересекает незавершённую canonical mutation.
