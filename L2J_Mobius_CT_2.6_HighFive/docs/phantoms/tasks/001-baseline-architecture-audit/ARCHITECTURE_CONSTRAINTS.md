# ARCHITECTURE CONSTRAINTS — Task 001

## 1. Цель документа

Codex не должен изобретать широкую Phantom World-архитектуру в Task 001. Нужно доказательно проверить один узкий foundational contract: как использовать штатный `Player` без реального TCP-клиента.

## 2. Неподвижные принципы

1. NPC не является конечным Phantom domain actor.
2. `Player` и штатные игровые подсистемы переиспользуются максимально.
3. Нельзя fork-нуть большую часть `Player`.
4. Нельзя создавать отдельный thread/executor/scheduled loop на каждого phantom.
5. Runtime не зависит обязательно от LLM.
6. Production system позже будет disabled by default.
7. Packet/network abstraction не должна менять gameplay side effects.
8. Client packet classes не должны стать Phantom internal API.
9. Lifecycle обязан поддерживать failure rollback и idempotent cleanup.
10. Рабочая DB не используется тестами.
11. В Task 001 production-код не изменяется.

## 3. Целевой минимальный seam для проверки

Предварительное логическое разделение:

```text
Real client
  -> GameClient/network transport
  -> Player outbound/session seam
  -> ServerPacket side-effect policy

Headless phantom
  -> Phantom headless session / recording sink
  -> Player outbound/session seam
  -> same required ServerPacket side-effect policy
  -> no socket write
```

Это не предписывает точные имена production-классов. Codex должен определить минимальный реально совместимый seam и перечислить точные touch points Task 004.

## 4. Required invariants

### Identity

- persistent character ID уникален;
- account/character online status согласован;
- real client и phantom не владеют одной identity одновременно;
- objectId не дублируется в World.

### Lifecycle

- `NEW/LOADED → MATERIALIZING → ACTIVE → DEMATERIALIZING → STORED`;
- failure из любого промежуточного состояния ведёт к bounded rollback;
- cleanup повторяем;
- shutdown запрещает новые actions;
- tasks/futures отменяются.

### Packets

- headless path не пишет bytes;
- null packet — ошибка, не молчаливый success;
- обязательные `runImpl(Player)` effects сохраняются;
- packet-only visual updates можно discard;
- recording diagnostics bounded и выключены по умолчанию;
- broadcast другим реальным игрокам сохраняется там, где это gameplay visibility.

### Actions

- Phantom вызывает server-side actions;
- validation, inventory, adena, karma, skill reuse и transaction rules не обходятся;
- нет прямых DB mutations вместо domain API;
- сетевые request handlers не вызываются как основной внутренний API без отдельного обоснования.

### Concurrency

- shared scheduler;
- bounded queues;
- cancellation;
- no per-phantom thread;
- no unbounded per-phantom scheduled future;
- no hot-path INFO/WARNING logs.

## 5. Alternatives matrix

Codex обязан заполнить доказательствами:

| Вариант | Blast radius | Packet effects | Lifecycle | Compatibility | Testability | Verdict |
|---|---:|---|---|---|---|---|
| Fake `GameClient` |  |  |  |  |  |  |
| Nullable client everywhere |  |  |  |  |  |  |
| Small output/session seam |  |  |  |  |  |  |
| `PhantomPlayer extends Player` |  |  |  |  |  |  |
| Fork/copy `Player` |  |  |  |  |  |  |
| NPC-based final core |  |  |  |  |  |  |

## 6. Expected recommendation quality

Недостаточно написать:

- «добавим интерфейс»;
- «сделаем fake client»;
- «Player работает без клиента»;
- «offline trader уже всё решает».

Нужно указать:

- interface responsibility;
- owner;
- default implementation;
- headless implementation;
- exact call sites;
- packet side-effect semantics;
- lifecycle creation/teardown;
- error propagation;
- thread-safety;
- metrics/logging;
- tests;
- migration path;
- rollback;
- rejected alternatives.

## 7. Task 004 spike boundary

Task 001 должна подготовить Task 004, но не реализовывать её.

Минимальный будущий spike должен суметь автоматически:

1. загрузить либо создать fixture player в test DB;
2. materialize без TCP;
3. зарегистрировать в World;
4. проверить inventory/skills;
5. выполнить одну безопасную server-side action;
6. проверить packet sink semantics;
7. dematerialize;
8. проверить store;
9. повторить cleanup;
10. перезапустить/восстановить;
11. доказать отсутствие leaked tasks/world object/online flag;
12. работать с фиксированным seed.

Если для этого нужен fork `Player`, gate должен остановить план и инициировать отдельный plan revision.
