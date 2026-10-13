# TASK041 — PHANTOM-M1-FINAL-CLOSURE-041

## 1. Идентификатор и цель

Закончить М1 как проверенный вертикальный контур живого мира: native действие, законные паузы/восстановление, мягкая дематериализация, полезная фоновая жизнь, возврат того же персонажа, сохранения, обычный и аварийный restart, пассивный REAL-наблюдатель. Результат — рабочий продукт, а не новая папка отчётов.

## 2. Зависимости и приоритет договора

BASE041=cff7b7d479cc27413af6f4fdb317d7ced5d51b48, ветка публикации experiment/m1-candidate007-observe008. Product C4=5d84cbf46cfb97735fe2c7ef4b52e589de0d073b. Проверить HEAD/remote при старте. При возобновлении допустимы только собственные документированные descendants041; чужой drift не исправлять reset/rebase.

Прочитать Agents.md, PHANTOM_DEVELOPMENT_MASTER_PLAN.md, docs/phantoms/CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md и целевые040 RESULT/GATES/FIX_REVIEW/REAL_FINAL. Их ранние сведения об отсутствии geo, старом MAIN runtime и запрете crash не выдавать за текущую истину. Конкретные поздние полномочия этого041 и пользователя имеют приоритет; опасное неясное действие остановить, безопасную независимую работу продолжить.

## 3. Контекст

040 уже дал G1/G2/G7/G8 и actual REAL binding. Остались G3/G4/G5, теперь разрешён G6. CONTEXT.md различает подтверждённые результаты отчёта, пользовательские наблюдения и гипотезы. 93/93 не означает «все функции мира готовы». Две G2 группы — cohorts, не работающий party-AI.

## 4. Рабочая область

MAIN C:\Users\ZBook\L2J_Mobius — read-only после распаковки пользователем. Входной ZIP содержит верхний каталог L2J_Mobius_CT_2.6_HighFive, поэтому распаковка — в MAIN, не ещё раз в модуль. Codex копирует в OWN только семейство041, проверив PACKAGE_SHA256.txt.

OWN C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius, модуль L2J_Mobius_CT_2.6_HighFive. Worktree сохраняется, detached HEAD допустим. Старые040 source fixes уже в BASE; не переносить их патчами поверх самих себя.

Создаются только перечисленные AUTHORITY runtimes/schemas и новый каталог поставки M1-041. Не использовать default Ant cleanup без чтения delete/copy targets. Сборка в новом staging из отслеживаемых входов; ни build, ни copy jar не пишут в действующий040a и не имеют с ним hardlink/junction-alias изменяемых файлов.

## 5. Обязательный предварительный аудит

До первой записи: exact Git/OWN; live040a identity/ports/root/schema; источник согласованного экспорта; свободные порты и память; соответствие crash72/73 реальным точкам в существующих контроллерах; конечный безопасный путь сборки/запуска; отдельный клиентский endpoint для будущей041 приёмки. Последний пункт выяснить сразу, не после12часов.

В один CHECKPOINT041 записать read-first: факты, 3–5 основных гипотез, методы-владельцы, план ближайших шагов, authority, начальный бюджет. Не создавать отдельный отчёт на каждую проверку окружения. Не читать все исторические raw, только указанные индексы и причинные срезы.

## 6. Архитектурное решение

DESIGN.md: достроить существующий action/lifecycle/transaction contour, не переписать мир. Один владелец изменяемого состояния, actual commit как граница истины, ограниченный read-only witness. Пассивный observer не исполняет действий игрока. Release tooling отделено от product AI и от тестовых injectors.

## 7. Scope

SOURCE_MAP.tsv плюс причинно необходимый соседний существующий Phantom owner, объявленный точным путём/методом ДО правки. Локальный рефакторинг разрешён для устранения установленной причины. Исправление broken recorder допустимо, но не становится основной целью. Перенос необходимых deploy/config helpers в tracked tools и воспроизводимость текущего М1 входят в041.

## 8. Вне scope

Zariche/общая переделка Player casting; новая экономика, магазины/крафт/соски, пати/кланы/PvP/Камалоки, карьерный AI, все зоны/10k повторный масштабный soak, Recast/gdx-ai, C#/LLM, новые зависимости/службы/firewall, новые persistence schema/WAL. Это не отказ от пожеланий: DELIVERY.md сохраняет их в будущей поставке и реестре следующих этапов, без фиктивной готовности.

## 9. Требуемые изменения и результаты

A. Воспроизвести и исправить реальные G4/G5/G6 дефекты, а не только расширить trace.
B. Получить единый final-candidate набор всех обязательных native gates.
C. Обработать пользовательский симптом долгого idle при доступных целях; проверить фактические level/EXP/loot/decision, не считать отсутствие анимации отсутствием прогресса.
D. Замкнуть реальное REAL→AUTONOMOUS→REAL после выхода/входа и движение focus по текущему instance.
E. Удержать полезные035–040 изменения в обычной сборке; дать рабочую M1 поставку и обязательный cumulative delivery contract для М2–М7.

## 10. Конфиги

Сохранить игровые значения принятого baseline, в частности популяцию/радиусы/пороги/рейты/инвентарь. Exact runtime-specific DB/ports/root/observer identity отличаются по isolation manifest, не маскируются общим config hash. Новые operator config templates не содержат private passwords; private per-runtime overrides не коммитить.

В документации перечислить реальные файлы/ключи/defaults, необходимость restart/reload; основа PhantomPlayers.ini и data/phantoms, фактические LocalPlay настройки установить чтением. Не придумывать ключи. Windows PowerShell 5.1 через powershell.exe обязателен для новых пользовательских .ps1: pwsh в PATH пользователя отсутствует.

## 11. Производительность и бюджет

Новая автономная кампания041 — до 12 часов elapsed wall-clock от первого preflight, без сброса после compaction/перезапуска goal. Ожидание финального ответа пользователя после handoff не является активной работой и не требует polling. Резервировать последние 90 минут для окончательной проверки, безопасного завершения и поставки; аварийный сценарий не arm без оценённого времени на обе recovery boot и запас.

Ориентир: ≤1час аудит/первые RED; далее исправления и маленькие native проверки; затем final заморозка и совмещённые G3/G4/G5/G6; оставшееся — упаковка и acceptance runtime. Это не обязанность ждать1час до кода или остановиться на5часах при исправимом долге. Не вводить произвольную квоту «последний старт закончился — всё».

Одновременно с защищённым040a запускать не более одной тестовой Game/Login пары; fixtures, компиляции и БД-копии ограничить ресурсами и не мешать играющему пользователю. Если host capacity недостаточна, не останавливать040a и не снижать тестовый игровой профиль ради PASS.

## 12. Конкурентность и lifecycle

Сохранить035 inventory/overfull-save,036 earned/drain,037 useful debt,038 single-writer/target generation,039 commit-witness/progress ordering,040 capability/resource/capacity/stale/observer. Смерть не равна corruption, LEGAL REST не равен stuck, отсутствующий frame не равен исчезновению Player. Новая native epoch при rematerialization допустима при сохранённом stable profile и непрерывном ownership.

## 13. БД и транзакции

Никаких UPDATE-heal, сбросов pending/debt/receipts, повторного импорта в sameDB цепочке, удаления предметов или копирования live data-directory. Source export — существующий согласованный архив либо транзакционно согласованный read-only экспорт; сначала предпочесть сохранённый snapshot без чтения текущей040a. Shared MariaDB никогда не цель crash.

Commit/recovery факты из реальных владельцев: PREPARE не равен COMMIT; commit-outcome-unknown не равен success; SELECT позже не заменяет точную границу. Сохранять context, goal, FULL items/skills, native XYZ/instance и законные изменения XP/SP; повторный restart не начисляет уже учтённую операцию ещё раз.

## 14. Автоматические тесты

PLAN/SCENARIOS/ACCEPTANCE задают обязательные native/negative проверки. Существующие suite assertions сохраняются; новый тест ловит первопричину, а не подставляет ожидаемый счётчик. Jar-first production classes, один manifest происхождения. При изменении product повторить затронутые gates, до финала — полный baseline93/93 и lifecycle11/11 плюс affected/protected.

## 15. Команды проверки

Реальные команды прочитать из build.xml и040 runner. Target jar подтверждён Agents.md, но его фактические copy/delete side effects сначала прочитать. В clean staging дать точную Ant-команду с явными output roots. Короткий tracked Verify-M1.ps1 должен запускать существующие проверки без недокументированного ops040; command line и результат записать один раз. Не выдавать тестирование самого runner за native acceptance.

## 16. Приёмка и статусы

Все серверные G0–G8, O_SERVER и R1 PASS → M1=WAITING_REAL_FINAL, READY_FOR_M2=false. Текущий final user session+пассивный reconnect+явное принятие, без неразрешённых М1 долгов → M1=CLOSED, READY_FOR_M2=true. Реальный вход040 не заменяет проверку новой041 сборки. Зарич — отдельно DEFERRED_BY_USER, не «исправлен» и не вечный FAIL свежей ordinary приёмки.

## 17. Отчёт

HANDOFF.md: один FIX_REVIEW, GATES.tsv, RESULT, REAL_FINAL, CHECKPOINT; небольшой review ZIP и отдельная runtime поставка. Не записывать статус CLOSED в живом helper статической строкой. Process RUNNING, observer freshness и milestone acceptance — разные значения.

## 18. Commit/push и сохранность

Exact-path add/ordinary commit/normal push в ту же ветку; проверка remote SHA. Полезный source/test/config/tooling — tracked, не только .phantom-local или временный diff. CODE_COMMIT отдельно от doc-only REPORT_COMMIT. В MAIN не копировать runtime автоматически. Полные raw/старые worktrees/DB сохранить; удаление данных/истории запрещено; disposable041 build outputs — только по AUTHORITY §8.

## 19. Что делать при неудаче

Исправимый in-scope RED запускает диагностику/фикс в ЭТОМ041. После двух неудачных патчей одной причины: одна проверяемая смена гипотезы/переразбор владельца; не третья заплатка вслепую. Не изобретать новый task для каждого подсценария. Не останавливаться из-за отменённого crash-запрета.

Настоящая опасная граница/чужой drift/платформенный запрет/исчерпанное время: конкретный BLOCKED/FAILED/PARTIAL, точный выполненный объём, safe own stop/recovery, публикация полезного проверенного кода. «Последняя задача» — цель, не разрешение объявить невыполненное выполненным. Следующий milestone не запускать; переход к М2 — после подтверждённого закрытия.
