# TASK064 — Phantom World М1–М7: единая серверная интеграция

**Тип:** реализация + native/ordinary server verification + автономная поставка. **Бюджет:** максимум 12 часов от первого действия; последние 90 минут зарезервированы на остановку, проверки, commit/push и отчёт. **Владелец заранее одобрил весь описанный scope**: дополнительное промежуточное подтверждение плана, теста, компиляции или публикации не требуется, если платформа не предъявляет собственного обязательного запроса.

**Git:** `https://github.com/kpCat/L2J`, ветка `feature/phantom-world`, baseline `fe2a24d389ed4af622cb14bc15a615b4b69e28f1`. **Рабочий корень:** `D:\Codex\repos\L2J-phantom-world`. **Единственный игровой модуль:** `L2J_Mobius_CT_2.6_HighFive`. `master`, `upstream/master`, старый `experiment/*`, Semantic Studio и другие проекты не изменять. Перепроверить фактические refs и Git status перед записью.

## 1. Цель и неизменяемые принципы

Получить как можно более законченный, действительно работающий **серверный** Phantom World М1–М7 без участия владельца: живые `Player`, личная и групповая деятельность, PvP/PK, рейд, поддержанные инстансы, самостоятельное развитие, корректное сохранение/продолжение. **Наличие Java-обработчика не является доказательством игрового поведения.** Работать в существующих Mobius/Phantom owner-интерфейсах; устранять общую причину конфликтов, а не писать обход конкретного NPC или теста.

М2 и М3 ранее `SERVER_VERIFIED` в явно ограниченном объёме: их сохранять и переиспользовать, не переписывать. TASK061=`CORE061_PASS`, но М7=`PARTIAL`. G4/G6 М1, полный М4/М5/М6 и межсистемное развитие остаются открытыми. Не ставить условием непрерывный прокачечный марафон 1→85, все квесты, все классы или участие владельца через клиент. Не закрывать технический FAIL словами `OWNER_QA_PENDING`.

## 2. Подготовка и самостоятельное обнаружение окружения

Перед первыми gameplay-тестами выполни **короткий, ограниченный read-only preflight** по `RUNTIME_INPUTS.md`. Новый checkout содержит актуальные исходники, Ant `build.xml`, Java, SQL, конфиги, семантические пакеты и инструменты; отсутствие Git-ignored JAR, личных GeoData и private runtime manifest **не означает потерю проекта**.

Найди установленный Java25/Ant, MariaDB 127.0.0.1:3308, проверенные GeoData и private runtime settings в известных старых каталогах. Старый checkout `C:\Users\ZBook\L2J_Mobius` — только источник сравнения/чтения ресурсов, **НЕ источник актуального кода**; он отстаёт и содержит 1852 ценных локальных изменений. Релизы057–061 — read-only источники manifests/config/geodata, их не запускать. Сверь версии, SHA/размеры и причины выбора; скопируй только подтверждённые ресурсы в **новую изолированную среду TASK064**, не в старый checkout и не в Git-отслеживаемый datapack.

Создай собственные TEST/runtime-конфиги, не переиспользуя старые идентификаторы, порты, owner credentials или БД как writable. Сначала проверь exact root/schema/owner/ports/JAR, отрицательные случаи wrong-root/schema/owner; затем `START/STATUS/STOP` только новой среды. При отсутствии безопасных предпосылок отмечай конкретный BLOCKED и продолжай независимые тесты вместо опасной импровизации.

## 3. Работа над игровыми вертикалями (приоритеты)

**P0 — M1 / сквозное управление.** Подтверди на обычном `Main/GameServer` (synthetic OFF) фактическое World presence → фарм/loot/EXP/SP → занятие, выбранное AI → save/STOP/sameDB START → полезное продолжение. Проверяй bounded переход World↔Background, законный cold admission, смерть/возврат. Сохраняй root/child, профили, заработанные предметы и idempotency. G4/G6 закрывай только при разрешённых точных доказательствах; запрещённые crash/attach/force действия не применять. Если G6 недоступен, честно оставить DEFERRED/BLOCKED.

**P1 — M4 / PvP–PK.** На собственных тестовых `Player` проверь lawful enemy/flag/karma/party/clan/ally/peace/instance, attack/focus/defense, реальный HP/CP damage, соответствующие навыки и реакцию поддержки. Ключевая цепочка: конфликт → результат или законное отступление → recovery/regroup → возврат к деятельности. Используй общую навигацию и threat model, не делай специальный алгоритм под Гиран. Негативные чужой игрок, мирная зона и stale owner обязательны.

**P1 — M5 / рейд.** Изучив известные неудачи `25199`, добей законный поддержанный рейд с подготовленным до измерения составом (native `Party`, tank/aggro, support/heal/recharge, ДД, adds/minions), фактической победой, stock reward, смертью/восстановлением, regroup и последующей жизнью. Не подменяй raid обычным Karik/Wolf, не меняй в ходе измерения HP/MP/атаки boss/rates/reward/fixtures ради PASS. Если именно25199 имеет доказанный независимый blocker, допускается другой заранее объявленный реальный поддержанный RaidBoss; старый FAIL сохраняется.

**P1 — M6 / инстансы.** Сохрани работающую Kamaloka57, заверши поддержанный Rift: готовность группы, fee/consent, native entry одной Party/instance, combat/EXP/reward, timed room transition, допустимые death/logout/disband/exit/reuse/closing. Проверь уже известный Pailaka earned-timer/drain blocker без обхода native timer owner. Один вход без законного выхода не полный M6 PASS; универсальные все-инстансы не создавать.

**P2 — M7 / развитие.** Сохрани Phantom-only последовательные профессии20/40/76 и `Noblesse` main80 без обязательных class quests. Проверь несколькими разными архетипами реальный skill learning/SP, выбор способностей и экипировки после класса, покупку/расход SS/BSS/лекарств на заработанные деньги, смену спотов; переход solo↔party, если solo unsafe/неэффективен. Для Dwarf/Kamael/healer/tank/mage допускаются заранее корректные near-level TEST fixtures; они не объявляются заработанной карьерой. Исследуй и доведи минимальную законную subclass-сцену, если существующий native owner поддерживает её безопасно; Noblesse не равен доказанному subclass. Не возвращать Q401 как обязательный gate.

**M2/M3 — preservation:** accepted invitation/consent/follow/assist/heal/buff/recharge/res/leave; directed RU commands, HOLD/RESUME, recall и social isolation. Affected regression + один совместный native сценарий, без новой Semantic Studio, роста словарей или LLM.

## 4. Принципы отладки и проверки

Основная цепочка: `Historical/Background → Decision/Goal → Materialization → Navigation/Combat/Party → native effects → owned Store → restart`. Writer/lease, time budget, region/instance, claim, calendar, transaction и cancellation сверяй по реальному source. Для одного root cause допускается затронуть несколько owners и десятки согласованных файлов; второй scheduler/store/navigation engine запрещён. Не менять ради теста server AI, EXP rates, Drop rate или реальных наград.

Используй один текущий контрольный набор из `ACCEPTANCE.md`. Проверяй native effects (World `Player`, HP/CP/MP, EXP/SP, items, party/instance identity, class, rewards), а не `issued=true`, сохранённые XYZ или планы. Fixture level/gear до старта = `PREPARED_INPUT`, не natural. RED→общий fix→focused GREEN; не повторять неизменённый failed episode. Сохраняй raw FAIL и негативы. В конце — один affected cumulative regression и clean committed JAR + ordinary START/STATUS/STOP/sameDB-start; не перегонять исторические сутки наблюдений.

Бюджет ориентировочный: preflight ≤60 мин, основной код/причины ~6ч, gameplay integration ~3ч, финал ≥90 мин. Переключайся между независимыми P1-срезами при ~45мин без причинного прогресса, но не объявляй неполное закрытым. Сразу сохраняй промежуточные **содержательные exact-path commits** и normal non-force push; не ждать последней минуты. Один компактный checkpoint вместо десятков отчётов. После context compaction не обнулять дату, бюджет, FAIL или scope.

## 5. Конфиги, производительность, БД, конкурентность

Не менять массово defaults Phantom, config real players, login credentials, DB grants или глобальные сервисы. `ACTIVE/materialized` cap, общие scheduler/path budgets, H5 GeoEngine, Login/Game ports и anti-dup транзакции соблюдать. Под нагрузкой не создавать Thread/таймер/БД-запрос на каждого фантома. Все skill/class/item/XP/reward операции принадлежат штатному Mobius/существующему owned store. Перед изменением mutation writer inventory: кто реально пишет Player/DB, когда требуется quiescence; отчёт о непроверенных writers = BLOCKED затронутого среза.

Свои новые изолированные schema/runtime — только по `AUTHORITY.md` и `RUNTIME_INPUTS.md`. Фоновые виртуальные изменения не должны бесплатно выдавать EXP/items/class. Никаких изменений старых real-player, PARTY/CLAN/кастомных настроек вне собственного candidate.

## 6. Конечный результат

Опубликуй в основной ветке `feature/phantom-world` отчёт `docs/phantoms/reports/PHANTOM-M1-M7-CLOSURE-064.md` и `docs/phantoms/tasks/PHANTOM-M1-M7-CLOSURE-064/ACCEPTANCE064.tsv` с честными состояниями каждой М1–М7: `SERVER_VERIFIED` / `PARTIAL` / `BLOCKED` и отдельно `OWNER_CLIENT_QA_DEFERRED`. Матрица требует конкретное native/ordinary доказательство и versioned JAR/DB/receipt, а не старые красивые цифры. Не переписывай старые отчёты и не называй `M1–M7=PASS` при технических FAIL.

Если удалось создать конечный `D:\PhantomWorld\World-Integrated-064-candidate`, поставь его STOPPED, с `START/STATUS/STOP`, `BUILD_INFO`, хешами, собственными конфигами/базой, коротким `START_HERE_064_RU.md`, сохранением после sameDB restart. Докажи отсутствие влияния на защищённые среды. При невозможности полной поставки публикуй coherent compilable проверенный срез, точный следующий blocker и handoff без автоматического продления срока.

**Промежуточные вопросы владельцу запрещены как рабочая привычка:** все безопасные стандартные действия внутри TASK064 уже разрешены этим пакетом. Если платформа требует персонального подтверждения или возникла действительно опасная неизвестная операция, НЕ обходить; оставить точный blocker, продолжить независимую работу. Работа завершается достижением результата, истечением 12ч или безопасной исчерпанностью доступного scope; не «после первого зелёного теста» и не запросом нового бюджета.
