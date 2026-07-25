# CONTEXT — Task 001

## 1. Назначение

Этот файл содержит предварительные наблюдения, уже проверенные при подготовке задания. Они уменьшают поисковую неопределённость, но не заменяют независимый аудит Codex.

Codex обязан сверить каждый пункт с актуальным `origin/master`. Любой drift фиксируется в `BASELINE.md`.

## 2. Репозиторий на момент подготовки

- default branch GitHub: `master`;
- review snapshot: `16d61833b3983a3976583d0e4813e0de9457a52f`;
- commit message snapshot: `docs`;
- remote `feature/phantom-world` на момент подготовки не обнаружена;
- master plan и workflow documents находятся внутри единственного High Five-модуля.

Не считать snapshot immutable. Перед началом выполнить `git fetch origin --prune`.

## 3. Предварительные доказательства по built-in Fake Players

### 3.1. NPC-based модель

Проверенные точки:

- `java/org/l2jmobius/gameserver/config/custom/FakePlayersConfig.java`;
- `java/org/l2jmobius/gameserver/data/xml/FakePlayerData.java`;
- `java/org/l2jmobius/gameserver/managers/FakePlayerChatManager.java`;
- `java/org/l2jmobius/gameserver/data/xml/NpcData.java`;
- `java/org/l2jmobius/gameserver/GameServer.java`.

Наблюдение:

- `<fakePlayer>` разбирается как часть `NpcTemplate`;
- fake player identity хранится как mapping name → NPC ID;
- chat manager ищет NPC spawn и отправляет `CreatureSay`;
- ответы основаны на шаблонах, substring matching и случайной задержке;
- это полезно как audit fixture, но не является полноценным persistent `Player`.

Проверить все связанные datapack-файлы и runtime paths.

## 4. Предварительные доказательства по `Player`

Проверенный файл:

`java/org/l2jmobius/gameserver/model/actor/Player.java`

Наблюдения:

- класс очень крупный и содержит inventory, skills, party, clan, quests, trade/store, tasks и persistence;
- основные конструкторы private;
- `Player.create(...)` создаёт persistent character;
- существует `Player.load(...)`;
- поле `GameClient _client` nullable в части кода;
- `getAccountName()` имеет явную ветку для `client == null`;
- конструктор создаёт AI, Radar и запускает vitality task;
- есть несколько per-player futures/tasks, требующих полного inventory и cancellation audit;
- comment класса исторически предполагает client-thread, кроме offline store.

Нельзя делать вывод, что весь класс null-client-safe, только из нескольких веток.

## 5. Предварительные доказательства по `GameClient`

Проверенный файл:

`java/org/l2jmobius/gameserver/network/GameClient.java`

Наблюдения:

- наследуется от network `Client<Connection<GameClient>>`;
- constructor принимает connection и обращается к remote address;
- `sendPacket(ServerPacket)` сначала вызывает network `writePacket`, затем `packet.runImpl(_player)`;
- packet transport и server-side packet effect связаны;
- disconnection взаимодействует с LoginServer и player lifecycle;
- fake `GameClient` с null connection выглядит рискованным до отдельного доказательства.

Критический вопрос аудита:

> Какие `ServerPacket.runImpl(Player)` реально изменяют серверное состояние и как сохранить их семантику, не отправляя bytes headless-клиенту?

## 6. Предварительные доказательства по offline lifecycle

Проверенные файлы:

- `java/org/l2jmobius/gameserver/data/sql/OfflinePlayTable.java`;
- `java/org/l2jmobius/gameserver/data/sql/OfflineTraderTable.java`;
- `java/org/l2jmobius/gameserver/network/Disconnection.java`.

Наблюдения:

- offline systems вызывают `Player.load`;
- устанавливают online/offline state;
- spawn-ят реальный `Player`;
- восстанавливают effects, auto-use, party либо private store;
- в error path используют `Disconnection.of(player)`;
- `Disconnection` поддерживает player при отсутствующем client и вызывает `stopAllTasks`, `storeMe`, `deleteMe`;
- offline trade явно рассматривает `getClient() == null` либо detached client.

Это сильное доказательство принципиальной materialization без активного socket, но не доказательство готовой общей headless-сессии.

## 7. Предварительные доказательства по enter world

Проверенный файл:

`java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java`

Наблюдение:

- handler тесно смешивает network session, LoginServer trace, packet initialization и большое количество доменного startup/lifecycle поведения;
- прямой вызов `EnterWorld` для phantom не является приемлемым server-side API;
- аудит должен разделить обязательные materialization steps и client-only steps.

## 8. Предварительные доказательства по build

Проверенный файл:

`build.xml`

Наблюдения:

- Ant проверяет JDK 25;
- `compile` компилирует `java`;
- `jar` создаёт `LoginServer.jar` и `GameServer.jar`;
- `jar` копирует оба JAR в runnable `dist/libs`;
- тестовые targets в текущем `build.xml` не видны;
- Task 001 не меняет build;
- Task 002 должна создать тестовую инфраструктуру.

## 9. Рабочая гипотеза

Наиболее вероятный итог — `FEASIBLE_WITH_SEAM`:

- canonical actor остаётся штатным `Player`;
- fake network client отвергается;
- вводится малый output/session seam;
- server packet side effects сохраняются;
- client packet business logic переносится за server-side facade только по мере нужды;
- offline lifecycle используется как ориентир;
- feasibility подтверждается Task 004.

Это только гипотеза. Codex обязан выбрать другой verdict, если актуальный код это доказывает.

## 10. Что нельзя пропустить

- прямые `player.getClient().…` без null check;
- `sendPacket` overloads в `Player`;
- `ServerPacket.runImpl`;
- `ClientPacket` handlers с business logic;
- task/future creation в constructor/restore/enter;
- world/known-list lifecycle;
- LoginServer/account online semantics;
- duplicate materialization и real-player login collision;
- inventory/trade/mail transaction boundaries;
- quest timers;
- instance/siege registration;
- offline restoration cleanup;
- shutdown ordering;
- startup and error logging frequency;
- geodata-disabled behavior.
