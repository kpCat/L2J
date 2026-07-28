# Commerce, supplies and travel contract

## Статус и границы

Контракт Goal 014 реализует только явные одиночные операции NPC commerce:
покупку одного unlimited buylist product, продажу одного owned item object/count и
один NORMAL teleport. Multisell доступен только как неизменяемый query catalog и
никогда не исполняется.

Источниками истины являются текущие item/skill XML, `BuyListData`,
`MultisellData`, `TeleporterData`, `ItemData` и `NpcData`. Каталог строится один
раз после загрузки authoritative data, имеет bounded paging (не более 256
элементов), component hashes и combined SHA-256. Никакие vendor, currency или
price не выводятся из display name либо памяти агента.

## Quote и каноническая мутация

Операция исполняется только под точным materialization `ActionLease`. Quote
копирует ограниченный immutable snapshot: adena, requested item/object counts,
load/capacity, class index, Noble, karma, dead/combat/casting/moving/teleporting,
instance, position, target и last Folk.

Непосредственно перед каждым side effect повторно проверяются Player, NPC
object/template, target/last Folk, range, instance, list/offer/route, price/fee,
tax, budget, weight/capacity, ownership, sellability и ограничения операции.
Мутация выполняется только штатными Player/inventory/teleport API. Packet
handlers, bypass, прямое изменение контейнера и multisell execution не являются
внутренним Phantom API.

Поддерживаемый buy — ровно один unlimited product. Limited stock и ненулевая
castle treasury часть отклоняются. Sell принимает ровно один object/count и
требует `checkItemManipulation`; refund и zero-sell-price modes отклоняются.
Teleport принимает ровно одну текущую NORMAL route и применяет актуальные
Noble/siege/karma/combat-flag/free-level/discount/fee правила с injected clock.

## Durable receipt

Координация использует существующее profile component storage:

```text
componentType: commerce.operation
schemaVersion: 1
payload: не более 4096 bytes
key: profileId + goalId + goalRevision + operationKind + canonical request hash
```

Одна receipt разрешена на профиль. `COMMITTING` сохраняется optimistic update до
первого канонического side effect. Штатные переходы:

```text
PREPARED -> COMMITTING -> COMMITTED
PREPARED -> ABORTED
COMMITTING -> INCONSISTENT
```

После ранее зафиксированного `COMMITTED` обнаруженная несовместимая exact delta
также переводит receipt в `INCONSISTENT`, чтобы durable fail-stop сохранялся
после restart.

Reconciliation сравнивает только exact before, first-effect и expected-after
facts. Exact after даёт idempotent success; exact before допускает один resume;
единственный однозначный partial позволяет выполнить только отсутствующий side
effect. Любая посторонняя или смешанная delta означает `INCONSISTENT`: replay,
предполагаемая компенсация и следующая commerce mutation запрещены.

Receipt не создаёт общей транзакции с обычными server writers и не является
заявлением о cross-server ACID. Локальные striped locks координируют только этот
service; внешние inventory/adena/teleport writers могут изменить состояние.
Exact reconciliation обнаруживает неоднозначность и безопасно останавливается.

## Decision и lifecycle

До seal регистрируются actions `commerce.observe`, `commerce.buy`,
`commerce.sell`, `commerce.teleport` и явные goal types `acquire.item`,
`maintain.supplies`, `sell.item`, `travel.teleport`. Candidate принимает только
persisted goal с валидным source и создаёт не более одного mutating step. Он не
создаёт goals, не сканирует World и не вызывает `progression.learn_skill`.

Service не имеет worker/thread/scan. Он запускается до scheduler work и
останавливается до materialization shutdown. При выключенном Phantom World
catalog, service, candidates, DB access, threads и commerce logs не создаются.
