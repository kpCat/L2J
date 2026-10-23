# CLOSURE_POLICY072 — как не повторять бесконечные PARTIAL/R2

## Главное: не менять определение успеха под результат

Сделать доступную native сцену **до** сохранения живого актора; это допустимая корректная проверка, а не фальсификация. После начала измерения нельзя двигать/повышать персонажа, выдавать деньги или изменять мир ради PASS. Любой старый persistent profile из071 защищён; его `BLOCKED_INPUT` остаётся частью истории и не исчезает после появления отдельного green fixture072.

Задача создаёт **один действительно завершённый сквозной путь**, а не «PASS по десяти названиям методов». Если при наличии подходящей сцены product всё ещё зависает на одном жизненном шаге, исправить конкретный причинный механизм в существующем owner; затем одна связанная проверка. Если нет подходящей сцены в пределах бюджета, не превращать это в очередное обещание, что NPC у игроков нет: дать точное `SCENE_INPUT_MISSING` и перейти к CP/PvP/доставке.

## Матрица статусов

- `SUSTAINABLE_LIFE072=PASS`: только все hard native P0/P1/Safety/Delivery выполнены. Это **не** глобальная финальная приёмка всего Phantom World и не автоматическое закрытие исторического `FULL071=PARTIAL`.
- `SUSTAINABLE_LIFE072=PARTIAL`: реальная механика есть, но хотя бы один hard gate не доказан; назвать конкретное минимальное невыполненное действие и владелец/тест/production-контракт.
- `SUSTAINABLE_LIFE072=BLOCKED`: источник/маршрут/окружение действительно отсутствует, конфликтует или небезопасен, невозможно продолжать **без** изменения защищённого состояния; предложить одну конкретную необманную развязку, но не выполнять её без authority.
- Дополнительные `P1_PROACTIVE/P2_GEAR` MED и `X_EPIC_TIMER` OPTIONAL отслеживать явно, **не выдавать PASS_FULL071**, пока прежние соответствующие полные gates не доказаны.

## Что запрещено по времени

- Никаких extra R2/3 задач автоматически, повторов Main ради доказательства того же через полчаса, stack-wide soak.
- Не растягивать задачу ради идеальных reviewer notes: read-first<20m; причинные изменения<=2.5–3h; acceptance/runtime/release<=1–1.5h; до5h полный STOP/commit/push.
- Если source/JAR собран и новый product diff отсутствует, не перестраивать из-за отчёта/TSV; документы коммитить отдельным safe docs commit, если требуется.
- Не создавать тысячи логов/классов тестового harness ради одного native SELL/BUY; переиспользовать existing071 test runners и runtime guards путем копирования/адаптации только в **own072**, не модифицировать 071.
- Source/test branches conflict: не бросать uncommitted source; зафиксировать scope, compile green, bad changes reversible only own files; preserve all user dirt.

## Итог Codex — компактно

1. Общий статус (PASS/PARTIAL/BLOCKED) и elapsed wallclock.
2. Реальный `P0_EARNED` цепочкой 5 receipts: kill/earned -> buy -> travel -> kill/spend -> restart/new native act, с точными IDs/XP/SP/Adena/shot counts.
3. `P1_CP_PAID` coordinates source/actor + COMMITTED + usage; `P1_PVP_SUPPLY` episode and cause.
4. `P2_GEAR/P1_PROACTIVE` если не закрыты: короткая правда, не фиктивные PASS.
5. Committed source SHA, GameServer JAR SHA, runtime path and state STOPPED, isolated DB, normal push + verified remote HEAD.
6. Ссылки на report / acceptance / operational start; без просьбы делать новую задачу прежде, чем владелец посмотрит итог.
