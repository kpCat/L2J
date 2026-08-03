# Контракт многопользовательской экономики Phantom World

## Статус и границы

Этот контракт фиксирует Goal 022 Checkpoint 2 для `DIRECT_TRADE`,
`PRIVATE_STORE_BUY`, `PRIVATE_STORE_SELL` и `PLAYER_MANUFACTURE`.

Операцию инициирует Phantom-профиль. Второй владелец может быть другим Phantom,
обычным Player или уже существующим видимым offline-store Player. Невидимая
background-торговля и эмуляция offline login не поддерживаются.

## Участники

- Phantom: положительный `profile_id` и неизменяемая связь с точным character
  object ID из reservation snapshot.
- External: `profile_id = 0`, точный character object ID и evidence согласия,
  listing или manufacture price.
- Лимит включает уникальных character owners; в C2 их не больше двух.
- Поиск операции участника использует initiator `profile_id` или reservation
  `profile_id`, не текущую character link.
- Перед любым nonterminal idempotent успехом participant set и links
  перечитываются и проверяются заново.

## Порядок блокировок

```text
1. Phantom profiles по profile_id
2. durable offer
3. economy operation
4. reservations по canonical_resource_key
5. Player/TradeList owners по object ID
6. canonical character/item rows по принятому владельцу
7. Goal/store component при terminal reconciliation
```

После TradeList, character или item lock запрещено получать новый Phantom
profile lock. External owner не создаёт фиктивную строку профиля.

## Durable offer

Offer хранит точных участников, operation kind, Goal ID/revision, bounded payload,
line counts, expiry и content hash. Состояния:

```text
DRAFT → OFFERED → ACCEPTED → CONSUMED | INCONSISTENT | CANCELLED
          ├─────→ REJECTED
          └─────→ EXPIRED
```

Только `OFFERED` истекает в `EXPIRED`. Принятые условия неизменяемы; изменение
partner/object/count/price/listing/expiry создаёт другой offer ID.

## Шесть durable шагов

```text
DISCOVER_OR_LOAD_OFFER
→ OFFER_OR_ACCEPT
→ RESERVE
→ DISPATCH
→ OBSERVE_RECONCILE
→ CLOSE
```

Каждый Decision step выполняет не больше одного durable перехода. Background
может обнаружить возможность, но destructive execution возвращает
`ACTIVE_REQUIRED`. Каноническая mutation вызывается только для materialized
ACTIVE или NEARBY_PERCEPTIBLE участника.

## Канонические сервисы

- `DirectTradeService` владеет request/answer/add/confirm/cancel и вызывает
  существующий `TradeList` с object-ID ordered monitors.
- `PrivateStoreService` владеет BUY из SELL/PACKAGE_SELL и SELL в BUY; exact path
  запрещает client quantity clamp. Ordinary packets сохраняют punishment,
  flood и сообщения и только делегируют.
- `ManufactureService` проверяет текущую manufacture listing и передаёт работу
  `RecipeManager`; формулы, RNG и maker lifecycle не дублируются.
- Phantom-код не вызывает client packets и не создаёт fake `GameClient`.

## Граница эффекта

До первой Adena/item/ingredient mutation operation обязана стать `OBSERVING`.

```text
exact complete after  → COMMITTED
exact before, effect не выдан → ABORTED или безопасное resume до OBSERVING
partial/ambiguous     → INCONSISTENT
```

После `OBSERVING` повторный canonical dispatch запрещён. Автоматическая обратная
передача не выполняется. Item и Adena sums проверяются по двум владельцам;
manufacture дополнительно допускает только текущий RecipeManager product source.

## Private-store lifecycle

Phantom store plan хранится как bounded profile component и устанавливает только
канонические `TradeList`, manufacture list и `PrivateStoreType` после
materialization. Поддерживаются SELL, PACKAGE_SELL, BUY и MANUFACTURE.

Active store и accepted offer блокируют dematerialization. Остаток listing
сохраняется после transaction. Пустой store закрывается; economy observer
фиксирует точный transfer до headless store-close/disconnection cleanup.

## Manufacture evidence

`RecipeCraftObserver.Event` не содержит mutable Player/Item references и сообщает
manufacturer/customer, recipe, fee, ingredients, normal/rare/failure result,
HP/MP и текущие EXP/SP consequences. Fee, ingredients и product проверяются по
точным before/after deltas.

## Restart, cancel и shutdown

- pre-effect offer/operation можно отменить с освобождением всех reservations;
- `OBSERVING` после restart/cancel/shutdown fail-stop становится
  `INCONSISTENT` и не redispatch-ится;
- terminal operation имеет один audit, после чего offer согласуется с ней;
- shutdown закрывает admission, reconciles offers, закрывает Phantom stores и
  затем освобождает reservation kernel.

## Исключения scope

Контракт не включает mail, freight, warehouse, auction, direct offline-login
emulation, combat/clan, Goal 023 или новую inventory implementation. Он не меняет
`Player.java`, `Inventory`/`PlayerInventory` core и не создаёт worker/thread/task.
