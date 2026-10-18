# AUTHORITY061 — полномочия нового диалога

## 1. Актуальное решение
Утверждённый /goal разрешает TASK061 до14ч: обычную игровую жизнь, устранение связанных разрывов запуска/допуска/исполнения, автономные профессии и накопительную поставку. Прямо разрешённые действия не требуют повторного вопроса. Защита платформы и данных сохраняется.

Наследовать: M1_TECHNICAL_STATUS=OPEN; M1_DEVELOPMENT_BASE=ACCEPTED_WITH_DEFERRED_GATES; G4=DEFERRED_BY_OWNER_NOT_PROVEN; G6=BLOCKED_PLATFORM_DEFERRED; FORWARD_WORK_M2_M7=AUTHORIZED. М2/М3 ранее SERVER_VERIFIED в указанном scope, OWNER_QA_PENDING. Добавить одну датированную поправку061 в RELEASE_CONTRACT, исторические отчёты не переписывать.

Разрешённые060 supply/offline manufacture/GK/Pet зависимости остаются разрешены. Приоритет061 — A/B из GOAL; никакой автоматической кампании «всё М8–М10». Исправление связанного дефекта допустимо, полная переработка движка и чужих модулей — нет.

## 2. Неприкосновенное
- `D:\PhantomWorld\M1-057-candidate` / release_057a; `M2-M7-058-candidate` / release_058a; `M3-M7-059-candidate` / release_059a; `Living-060-candidate` / release_060a. Не запускать, не останавливать, не менять JAR/config/БД/логи, даже если STOPPED. Можно адресно читать опубликованные отчёты, существующие логи/manifest и необходимые private inputs без вывода секретов.
- MAIN `C:\Users\ZBook\L2J_Mobius`, PLAY/040a/041final/051a/053a/057x72/057x73, другие worktrees, backups, raw, старые SQL. Общий Git store меняется лишь обычными согласованными Git-командами из OWN.
- Общая MariaDB: service/datadir/users/grants; OS security/network settings, клиент, NWindow.dll/l2.ini/hosts, другие проекты.
- Старые orphan058t698–705, quarantine883/990 и остальные ранее quarantined IDs из receipts. Их не удалять, не переиспользовать, не размораживать ради теста. Не повторять отклонённый эффект другой командой.

## 3. Свои изменяемые среды
Пути под High Five OWN, кроме кандидата:

| Назначение | Root | Exact schema |
|---|---|---|
| Mutable native fixtures | `.phantom-local/contract058t`, native cwd `runtime/game` | `l2jmobiush5_phantom_test_058t` |
| Обычная накопительная интеграция | `.phantom-local/contract058a/runtime` | `l2jmobiush5_phantom_test_058a` |
| Новая самостоятельная поставка | `D:\PhantomWorld\World-Career-061-candidate` | `l2jmobiush5_phantom_release_061a` |

Продолжение058t/058a разрешено. Не создавать061t/061a, не bootstrap/reset/reimport058a, не выдавать её за другую схему. Существующие phantom.contract058.* свойства применяются к реальной058 среде; continuation061 фиксирует новый authority/JAR/case. При resume использовать своё фактическое состояние, не требовать возврата на BASE.

Перед первыми изменениями: exact root/schema/role/JAR, текущие PID+incarnation, отсутствие чужого writer, один consistent stopped backup058a. Проверять только затрагиваемые данные, без хеширования миллионов старых файлов. БД и preimages сохраняются. Неразрушающие versioned migrations только в этих трёх схемах разрешены с preflight/version, preimage и повторным применением без потерь.

058t: разрешён конечный заранее записанный набор новых собственных TEST actors/предметов/магазинов до измерения; native cleanup только их новых rows по receipts и после остановки owners. Accounts и старые профили не удалять. При отказанном cleanup сохранить quarantine и использовать следующий заранее ограниченный native ID; не schema sweep/drop.

058a: сохранить всех имеющихся персонажей и earned EXP/SP/skills/items/memory. Сначала использовать пригодные текущие профили. При отсутствии подходящих разрешено до **4 новых канонически созданных тестовых профилей** для обычной демонстрации/карьеры. Их уровни, конечные средства/экипировку, lawful начальные предпочтения/расписание объявить до сцены. Создание по текущему времени через штатный lifecycle допустимо; сброс исторического cursor существующего персонажа — нет. Если только для сохранения старых и размещения новых профилей необходим population target32→не более36, это разрешено как явный preview input; ACTIVE8/materialized16 не повышать ради исчезновения отказа.

Подготовка может поставить новый career actor близко к требуемому уровню. В полном положительном квестовом эпизоде QuestState начинается ABSENT/CREATED без завершённых стадий/марки профессии, а нужные kills/items/переходы выполняются продуктом. Подготовленный level19 не называется прокачкой1→19. Повторные near-level actors допустимы только в058t, не reset накопительного актёра.

## 4. Инструменты и обычная проверка
Разрешены точная адаптация и сборка существующих TEST fixtures/launchers, stopped exporter, Build-Package/Runtime/Stop и их guards к061-кандидату. Сохранять старые explicit mappings. Новый layout принимает только exact root/schema/owner/role/JAR; positive и wrong-root/schema/role/JAR negatives до записи. Никаких wildcard всех test-баз.

Для диагностики разрешены обычные logs, native typed events, расширение существующего read-only in-process status собственных сред с ограничением частоты/объёма. Наблюдатель не управляет Player и не создаёт игровой прогресс. Запрещены diagnostic Attach, jcmd/jstack/jmap, debugger/injector, G6/fault/crash/process kill и обход platform refusal. Существующий штатный Stop-helper/STOP_MONITOR разрешён только для своих058/061 после проверки incarnation. Отказ штатного Stop — сохранить состояние и не прибегать к force.

Старый normal-only premain recorder можно использовать в058t/058a для адресного теста при exact guards и необходимости. Для acceptance ORDINARY_WORLD и финального ordinary запуска — без startup recorder, тестового writer, PhantomTestLauncher, Synthetic Player и внешнего pulse. Предустановка TEST данных до boot не делает управляющую тестовую JVM допустимой во время ordinary измерения.

Ошибки SQL/rollback/управляемая конкуренция в своей test-JVM допустимы; это не разрешение аварий ОС/общей БД.

## 5. Конфигурация и реальные люди
058a: Login2138/Game7838/control9138; при занятом порте не трогать его владельца.
061: предпочтительно Login2106/Game7841/control9141 при свободных портах и неработающих защищённых057–060. Иначе Login2141/Game7841/control9141, если свободны. Выбрать один согласованный layout до boot и повторно проверить ОС-таблицу слушателей; без TCP probes/forwarding/настройки клиента.

Существующий профиль population32/ACTIVE8/materialized16 — исходный. Не повышать cap и не отключать календарь/catch-up/retention ради W1. Разрешён объяснённый preview-input для новых профилей из§3 и конфигурирование штатного автономного viewpoint к пригодной области до теста; такой input не объявлять исправлением старой БД. Подтверждённый starvation/deadlock/readiness defect чинить в текущем общем owner, не под именем TestAdmin.

Synthetic OFF, autoattach имён TestAdmin,kpCat; поля singular — одно имя. Пассивный AUTONOMOUS viewpoint без Player разрешён. REAL только при настоящей сети; TEST actor не REAL-login. Ни управление человеческим персонажем, ни согласие на траты/TP не выводятся из его присутствия в Party. Никаких вопросов спящему владельцу ради native TEST-сцены.

Native rates/prices/MP/SP/quest requirements/геодата/время/наградная вероятность не подгоняются. Ленивая дематериализация сохраняется; боевого owner нельзя принудительно освободить. Пользовательские QoL сохранить, но AutoClass/AutoNoblesse/GM выдача не считаются настоящим квестом. Убирать их глобально для проверки нельзя: использовать подходящий обычный TEST account вне привилегий.

## 6. Git и доставка
Разрешены address-scoped status/diff/log/rev-parse/merge-base/ls-remote, diff-check, `git add -- <exact paths>`, содержательные commits, `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` без force, clean git archive. Detached HEAD допустим. BASE при первом старте должен совпасть, при resume быть предком своих061 commits; чужое продвижение не затирать.

Без branch/worktree creation, миграции, reset/clean/rebase/amend/force, add-all. Свой незавершённый код сохранить patch+preimage/пути. Адресный возврат своего фрагмента разрешён лишь после исключения чужих правок; сломанную сборку не публиковать ради clean. Неизменённые старые untracked не добавлять.

Новая release061a — один consistent stopped export/import058a после готовности основной конфигурации/данных; не восстанавливать весь dump для повторного опыта. Возможные последующие необходимые upgrade — только неразрушающие versioned migration либо штатная жизнь, без повторного импорта. Финальные JAR целиком из одного committed clean input; никаких product overlays.

Свои058 и новый061 в конце STOPPED. Секреты не в Git; новую OWNER_LOGIN061.txt можно создать локально из разрешённого private input, не печатая пароль, чтобы инструкция не зависела от старой папки. Один RESULT, один checkpoint, одна acceptance-матрица; review ZIP не создавать. Полный runtime ZIP нужен лишь при действительно полном М7, до него готовой папки достаточно.
