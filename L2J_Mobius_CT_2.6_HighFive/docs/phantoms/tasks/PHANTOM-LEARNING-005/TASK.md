# TASK — PHANTOM-LEARNING-005: парные native PvE-испытания learned против stock

## 1. Идентификатор
`PHANTOM-LEARNING-005`; thinking **High**, Very High только при реальном конфликте lifecycle/двух владельцев.

## 2. Цель
Получить автономно воспроизводимый **TEST-only native benchmark**: в сопоставимых условиях провести реальные Solo и Party PvE-эпизоды штатной политикой и разрешённой обученной политикой, измерить **результат всего столкновения** и сделать честный вывод о наличии/отсутствии/неопределённости практического выигрыша. Сохранять пары, seeds, исходные условия, реальные действия, исходы, статистику и причины отказов; обеспечивать возможность добавить пары следующей ночью.

## 3. Зависимости
`wip/phantom-learning-004 @ dff800f4b95232c132b374f8ec97c932eda4a6b1`, immutable V1 accepted №1 и V3 accepted №2, Java25, Ant, MariaDB11.4.13, существующие Director/G0/Geo203/protected207 и native test fixtures. Не требовать одобрения владельца при рутинной TEST-работе; он отложил ручные TRAIN_*.cmd.

## 4. Контекст
004 доказала конвейер обучения и восстановление optimizer, но **не** превосходство реальных исходов по сравнению со stock. Модель №8 — отвергнутый DRAFT, не включать. Принятый V3 №2 первоначально обучен на HEAL; V1 №1 умеет атаковать. См. `CONTEXT.md`, `SOURCE_MAP.md`.

## 5. Обязательный предварительный аудит
Проверить exact Git SHA/ref, чистое independent clone, существующий native owner и V1/V3 модельные pin/SQL checks; реальные способы подготовить fresh actor и legal NPC, применить модель только в own TEST, получить end-of-episode. Понять какие значения действительно receipt и какие UNKNOWN. При отсутствии безопасного TEST-ON пути не изобретать обход существующего `apply-test` или чтение DRAFT. Записать PLAN с точными source seams и ограниченным diff.

## 6. Архитектурное решение
`matched scenario specification -> native physical pre-CUT setup -> STOCK arm -> fresh lawful matched LEARNED arm -> native action/terminal receipts -> immutable paired ledger -> offline report/statistical verdict`. Сравнения **по отдельным независимым направлениям**: `SOLO_DAMAGE_V1` и `PARTY_HEAL_V3`. Запрещено приписывать результат HEAL V3 обучению DAMAGE V3. Каждая ветка использует свои законно допущенные веса, актуальную конфигурацию и native owners. См. `EXPERIMENT_PROTOCOL.md`.

## 7. Scope
Новые ограниченные TEST/evaluator classes и при необходимости минимальные observation seams в имеющихся lifecycle, own SQL/JSON ledger, скрипт запуска через существующий Director (без нового supervisor), unit/focused/native проверка, русские отчёты. При возможности — без изменения production Combat/Party; любая такая правка требует точной причины и regression.

## 8. Out of scope
Нет новых семей действий, reward training, обучения DRAFT8, PvP 1×1/1×2/N×M/FFA, Mass Return, AOE паровозов, рейдов, осад, Олимпиады, нейронавигации, Semantic Pack; никаких MAIN/PLAY deploy и новых GPU тренеров. Не менять баланс/рейты/Skill/Geo/рандомизированные исходы ради победы модели. Не переписывать 002/003/004 конвейер.

## 9. Требуемые изменения
1. Версионированный план **пар**, а не статический файл из сотен готовых боёв. Подходящие реальные NPC/actors/equip/grade/skill из L2J. Идентичные стартовые *контролируемые* признаки у пары; order AB/BA сбалансирован. Вариации минимум по 2 измерениям (level/equipment/NPC/Party style), без угадывания результата.
2. Изолированные реальные эпизоды A=stock и B=accepted model; разные native actor/target incarnation, сохранение canonical attack/heal ownership. Лицензия TEST ON только для записанных принятых id/SHA; для invalid/missing checkpoint fail-closed.
3. SQL/immutable JSON pairs/arms with verified start/end/owner, model version+SHA, native event/target/kill keys, fixed context+seed, actual gear/HP/MP/abilities/buffs at CUT, expiry/status/reason, no replay duplicate.
4. Измерение завершения цель/EXP/SP/duration/death/remaining HP/MP, затрат и healed/damage только при receipt. Для Party суммировать credited EXP/SP с маркировкой, не называть PARTY damage суммой только выбранных магических кастов.
5. Отдельное `policy divergence`: реальная смена выбора skill/target и native исполнение; победа без такой смены не доказывает работу модели. Негативный контроль идентичных политик.
6. Повторяемый offline отчёт «что было лучше, в каком измерении, n, неопределённость, почему нельзя утверждать больше». Сохранить пары между процессами, безопасный Resume, STOP/EXPORT, не требуя 8 часов в Codex.

## 10. Конфиги
В новой own TEST конфигурации: `evaluation005Enabled=false` default, `evaluation005ActionScopes`, `evaluation005Seed`, `evaluation005MaxPairsPerScope`, `evaluation005Mode=OFF|STOCK|ACCEPTED_ONLY`, `evaluation005ModelPins`, `evaluation005CpuBudget`, если действительно нужны. Проверить валидаторы. Старые V1/V3 source configs остаются OFF/SHADOW, autoApply false. Параметры и точный run route документировать, не обещать, что простое добавление строки INI само реализует функцию.

## 11. Производительность
Один GameServer на ограниченный набор эпизодов, если это возможно без изменения truth/lifecycle. Пересобирать только при изменённом source fingerprint. Измерить startup vs prepare vs encounter, p95 native selection и CPU/RAM, throughput и долю INCOMPLETE; не повторять 128-logical benchmark 004 без нового hot-path изменения. Никаких горячих SQL, per-actor Thread, GPU/LLM в combat loop.

## 12. Конкурентность/lifecycle
Start→stop/drain→durable result. Запрет повторного credit при restart, freshness keys, exact action owner, no dead targets reused; stop at legal boundary. В случае crash старые STARTED переводить в INCOMPLETE только после доказанной остановки собственного native process. Данные сохранять.

## 13. БД/транзакции
Использовать только собственную TEST SQL/новую версионированную migration при необходимости. Транзакционная фиксация pair arms и hash, `UNIQUE(episode_id,event_key)` при наличии таблиц; valid no duplicate credit. Исторический модельный registry и 004 ledger не перезаписывать. Нет записей в пользовательскую MAIN/PLAY. Провайдер может использовать JSON if verified immutable + atomic own writes and cross process lock; никакой фальшивой SQL «успешности».

## 14. Автоматические тесты
DB-free contracts (pair identity, fairness, no-divergence, duplicate, unknown masks, statistics), focused native GameServer (Solo+Party, stock+learned), missed-cast and matched new actor/setup, cold evaluator REPORT, stop+resume. Один отрицательный тест MUST prove impossible accepted model is rejected before player actions.

## 15. Команды проверки
Найти корректный cwd/Ant и TEST runner из актуальных source. Focused тесты, compile-tests, jar, короткий native pair smoke, полный scoped comparator, audit F7/F8 если затронуты соответствующие seams; реальный STOP/порт/ownership. Команды и exit codes записать в `COMMANDS.md`, не выдавать сухой список команд за PASS.

## 16. Приёмка
`ACCEPTANCE.md`: G0–G8 с необходимыми native evidence. `FULL005_EVALUATOR` — честная демонстрация парного сравнения, а не гарантированная победа. Если статистики не хватает, `INCONCLUSIVE` допустим при функционирующем eval pipeline. Если нет настоящей смены действий, scope нельзя объявлять доказанным.

## 17. Отчёт
`docs/phantoms/reports/PHANTOM-LEARNING-005.md`, `docs/phantoms/tasks/PHANTOM-LEARNING-005/{PLAN,PROGRESS,RESULT,HANDOFF,COMMANDS}.md`. По-русски: таблица arm A/B и paired deltas, n, actual policy divergences, реальные исходы и unknown, затратная статистика, SHA checkpoint/JAR/source, причины недоказанного. Не выводить «+X% побед» из 4 пар.

## 18. Commit/push
Только exact scoped paths в новой `wip/phantom-learning-005`; обычный non-force push, remote HEAD readback и frozen protected refs. Никаких `git add .`, force/rebase/reset/clean/stash. Не коммитить SQL credentials, native datadir, Geodata, приватные полные логи, огромные binaries.

## 19. При BLOCKED
Оставить компилируемую безопасную часть и честный PARTIAL/BLOCKED. Не обходить admission или меня́ть исход боя ради PASS. Ограничить исправление реально найденных причин, не запускать бесконечно одинаковую сцену.
