# TASK058 — самостоятельная кампания игровых М2–М7

## 1. Идентификатор
`PHANTOM-M2-M7-CAMPAIGN-058`. Один новый диалог, существующая ветка/OWN, без субагентов. Это новая feature-кампания после057, не продолжение доказательной гонки М1.

## 2. Цель
Доставлять законченные игровые возможности по DESIGN_PLAN; движение к М7 разрешено, его достижение за14ч не гарантируется. Нельзя объявлять milestones по наличию classes или flags.

## 3. Зависимости
BASE и полномочия из AUTHORITY; источник порядка — MILESTONES038 и RELEASE_CONTRACT. Существующие Game/Login, JDK25, Ant1.10.17/WinPS5.1, MariaDB и лицензированная геодата. Новых runtime LLM/API/сервисов не вводить.

## 4. Контекст
Приняты обычные сценарии057: G1/G2, normal G3, G5, R1/O_SERVER. G4/G6/полная личная приёмка отложены владельцем. После057 клиент пользователя уже подключён на2106; REAL TestAdmin наблюдал видимых фантомов и сообщения loot. Это не полноценное G4 и не измерение всей экономики. Текущая игра защищена.

## 5. Обязательный предварительный аудит
Начальный product/test READ_SET<=12 в SOURCE_MAP; большие файлы — symbols и нужные диапазоны, не full reads>1000 строк. Пакет и обязательные локальные правила читать отдельно. До первой игровой правки не более6 широких поисков; расширять exact-read set небольшими партиями с причиной. На каждый следующий milestone читать только его связанные owners/tests, не всю историю.
Определи реальный путь вызовов и writers: chat observation, invitation service, party coordinator, combat/action/native callbacks, ordinary farm/background, persistence, autosave/reward. Не считать `synchronized(Player)` общей транзакцией без подтверждения других writers. Read-first summary — до15 строк в одном checkpoint; подготовку не превращать в отдельный многосотенный отчёт.

## 6. Архитектурное решение
Переиспользовать canonical Player/Party/Quest/Skill и имеющиеся semantic/social/party/combat/navigation/progression owners. Политика выбирает действие; execution service проверяет target/epoch/instance/resources и исполняет stock действие; typed результат подтверждает эффект. Кэш READY не заменяет текущие условия. Существующий controller не дублировать под новыми именами.

## 7. Scope
Весь необходимый связанный High Five code/test/config/data/SQL для М2–М7; число изменённых файлов искусственно не ограничено. Новые classes допустимы для реально отсутствующего отдельного поведения, а не параллельного framework. Центральные Player/Party/handlers менять только при подтверждённом разрыве, с обычными human regressions. Исправимый дефект М1, непосредственно блокирующий текущую функцию, можно исправить адресно.

## 8. Out of scope
Повторное закрытие G4/G6, видео и запросы владельцу, миграция/очистка старых worktrees, scale10000, новый оркестратор, универсальный solver всех квестов, М8–М10, изменение клиента, Zariche. Экономика/offline/QoL/семантические assets не удаляются; расширение торговой сети — будущий этап, не скрытая обязательная часть058. Сохранить в backlog владельца кристаллы/шмот/краски/AA/ценные куски, торговцев и крафтеров в разных городах, а не только соски и ресурсы; массовые200–1000 offline — не тест058.

## 9. Требуемые изменения
DESIGN_PLAN описывает порядок и acceptance-сценарии. Разобрать и исправить wiring реальной игры, не только unit-stub. First M2 needs both ordinary invitation directions and delivered reply. Затем остальные этапы по зависимости. Общий resolver актуальных навыков использовать уже в М2; полную карьеру завершать в М7, если дойдёт время.

## 10. Конфиги
Новые058 development settings отделить от M1/release057. Новые возможности должны попадать в ordinary datapack и иметь документированные bounded defaults, без task-ID условий в production. Сохранить существующие пользовательские QoL и настройки; копировать секреты в новый private config только через безопасный approved input, не вGit. В новой preview allowlist для наблюдателя `TestAdmin,kpCat`; не путать имена персонажей с account allowlist. `LocalPlaySyntheticCharacterName` остаётся одним валидным именем, Synthetic OFF в обычной поставке; не передавать CSV в singular ObserverCharacter.

## 11. Производительность
Не создавать поток/SQL-poll на каждого бота и не пересчитывать весь catalog каждый tick. Shared bounded schedulers, per-action deadlines, invalidation на фактические changes. Для overnight использовать выборочные typed events новых сценариев, не миллионы мелких файлов. Когда используется strict normal recorder, сохранять ring512/buffer16MiB/disk512MiB и весь заявленный stream; до заполнения штатный stop с запасом на tail. Потерянный tail не PASS.

## 12. Конкурентность и lifecycle
Party/raid/PvP takeover прекращает новые ordinary FARM roots, но завершает уже заработанные callbacks. Follow не перебивает cast; leave/revoke/stale epoch запрещают новые действия, но не стирают earned. Human leader остаётся управляемым человеком. Friendly heal, hostile attack и corpse resurrect имеют разные проверки. Сохранить single writer, identity, same-instance и native LOS/GeoEngine.

## 13. БД и транзакции
AUTHORITY разделяет изменяемые058t fixtures и накопительную058a. Перед началом episode фиксировать actor IDs, baseline/source, цель, ресурсы, длительность и ожидаемый эффект. Preboot fixture setup можно; SQL-heal/reseed после исхода нельзя. Сохранять настоящее списание SP/MP/items, результаты наград и нормальных restart, не повторять операции по старым receipts.

## 14. Автоматические тесты
Во время разработки: RED на конкретное отсутствие/дефект → минимальная правка → focused GREEN и относящиеся negatives. Уже работающий компонент сначала реальная targeted проверка; не выдумывать RED ради ритуала. На завершение milestone — native composition/ordinary runtime сценарий, persisted results и нужные unaffected-human controls. Тестовый партнёр не объявляется настоящим клиентским входом.

## 15. Команды проверки
Проверить доступные targets в текущем `build.xml` и task057 tools. Использовать фактический `javac/Ant` command и exit code, не сочинённые targets. Full output наD, в чат exit/summary и до80 строк ошибки. Полный baseline verify не повторять после каждой правки; один final full regression набор с affected suites, один targeted retry при причине. Для runtime проверок нужны актуальные JAR после semantic changes; в финале clean tracked-input build с class/source parity. Ещё одна сборка оправдана только изменившимся кодом/неуспешной проверкой.

## 16. Критерии приёмки
ACCEPTANCE. Server-verified этап допускает следующий без владельца; для dependent сценария требуются проверенные предпосылки. Если М2 не завершён, статус М2 PARTIAL, а независимый resolver/semantic patch можно доставить отдельно. Никаких стадий «все PASS» из-за одного удачного представителя с исключёнными остальными requirements.

## 17. Отчёт и checkpoints
Один приватный `.phantom-local/contract058a/CHECKPOINT058.md`, обновлять каждые30–45 минут и перед long command/compaction: startUTC, remaining, current commit/JAR, lane/PID, changed paths, done/fail/next, одна причинная гипотеза, точные команды продолжения. В одном `ACCEPTANCE058.tsv` — milestone/scenario/source/JAR/status/proof. Финальный tracked RESULT<=180 строк: фактические функции/ограничения, elapsed/read/test/build counts, краткие Git-команды, места логов и инструкция пользователю. Учёт времени не изображать, если не измерен.

## 18. Commit/push и поставка
Exact paths после каждого содержательного проверенного шага, не каждую строку и не каждый промежуточный FAIL; normal non-force push. Полезные новые helpers/fixture setup/tests tracked, runtime-функции никогда не только в `.phantom-local`. UTF-8; проверить mojibake и escaped Cyrillic отдельно. Новые docs не дублировать в десятках папок. Финальный отдельный candidate включает cumulative code/config/data/SQL/tools из этого же descendant; M1-057 не перезаписывается.

## 19. Поведение при блокировке и остановке
INPUT — до enrollment выбрать следующего кандидата по заранее объявленному правилу или подготовить отдельный fixture до нового episode; после enrollment состав не подменять. TOOLING — exact adaptation уже разрешена. PRODUCT — causal debug/fix/test. PLATFORM/AUTHORITY — запрет не обходить, сохранить и переключиться на независимый шаг. После любой поправки продолжать, не завершать всю кампанию автоматически. Завершить раньше срока можно при всех достигнутых целях либо отсутствии безопасной полезной работы после конкретного анализа; обязательно написать, почему каждая доступная ветка закрыта. При platform stop сохранить максимум доступного; не обещать автоматический запуск вне текущей сессии.
