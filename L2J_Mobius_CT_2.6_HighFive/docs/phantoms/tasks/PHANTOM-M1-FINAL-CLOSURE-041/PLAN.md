# TASK041 Implementation Plan — закрытие М1

> Исполнение: inline в существующем OWN, без субагентов; при наличии skills использовать executing-plans и причинную диагностику/TDD. Отдельное новое планирование вместо исполнения не требуется. Каждый шаг отмечать в одном CHECKPOINT041.

**Goal:** все обязательные gates М1 на одном конечном кандидате, отдельная работоспособная поставка, затем личная приёмка.
**Architecture:** существующие Phantom owners/stock native execution/atomic persistence; один ограниченный witness; tracked portable runtime tooling.
**Tech Stack:** существующие Java25/Ant/MariaDB, Windows PowerShell5.1; без новых зависимостей.
**Spec:** TASK.md, DESIGN.md, AUTHORITY.md, ACCEPTANCE.md, DELIVERY.md.

## Global constraints

Только High Five; защищённый040a не трогать. Crash только041x72/x73. Исходные gameplay assertions/thresholds/earned/identity/CAS сохраняются. Продукт не зависит от диагностики. Фактические функции соседних owners и команды runners локализовать в read-first, не выдумывать по названиям.

## Review focus

RF1: ошибка ownership/PID reuse/чужая schema → отказ ДО stop/crash. Проверяет шаг1/4/5.
RF2: original cohort не родился заново в данном boot → всё равно есть initial/final scope. Проверяет шаг2/4.
RF3: death в фоне/законный EXP loss → continuity той же личности, не замена ботом. Проверяет шаг3.
RF4: committed goal изменён, retry/recovery повторены → изменение объяснено, награда не удвоена. Проверяет шаг2/4/5.
RF5: source исправлен, но final dist запускает старый/private-only код или требует pwsh/Codex → сборочный/операторский RED. Проверяет шаг6/7.

## 1. Владение и исполнимость конечного маршрута

**Файлы:**040 RESULT/GATES/FIX_REVIEW/REAL_FINAL, ops040 runners — чтение; новые ops041 и CHECKPOINT041; Agents/build/фактические deployment tools — чтение.

- [ ] Подтвердить BASE/remote/foreign diff; скопировать только пакет041 из MAIN в OWN по manifest.
- [ ] Снять минимальный protected040a census; составить exact allowlist041 roots/schema/ports. Проверить commandline/JDBC и отсутствие shared writable aliases.
- [ ] Найти существующие callbacks/controllers crash72/73, записать их реальные pre/post условия и обещанные recovery гарантии. Проверить deny guard на данных чужого runtime.
- [ ] Проверить заранее, что новый041final можно подключить отдельным клиентским профилем на иных портах, не изменяя действующий клиент/040a. Использовать существующий проверенный механизм, не искать вслепую автологин заново.
- [ ] Подготовить одну новую ordinary041a и fixture041t из согласованного input; не импортировать остальные clones до нужного сценария.

**Результат:** безопасное пространство и исполнимый план ввода пользователя; не новый продуктовый PASS.

## 2. Убрать известные препятствия G3/G5 без платформы доказательств

**Владельцы:**PhantomBackgroundTransaction/PhantomBackgroundService, фактический goal commit owner из их call graph; private Contract032Observer/Run-RestartScenes/All-Birth-Cut040 копируются в041. Старые файлы не редактируются.

- [ ] На закрытом040 срезе воспроизвести пропуск original IDs и truncation/tail; короткий отрицательный тест отвергает их. Никакого полного повторного чтения876336 entries.
- [ ] Исправить collector/orchestration: initial cohort UNION all births, правильный process-exit/ownership result вместо ошибочного LASTEXITCODE, bounded closed stream. Positive+missing-tail negative. Ring/disk limits не увеличивать.
- [ ] Проследить действительные goal/context mutations до owning successful commit. Если нужен hook, получить focused RED отсутствующего факта и ввести минимальный optional after-commit witness в этом owner; rollback/unknown/observer-failure negatives.
- [ ] Проверить изменения goals между boot без canonical field deletion; raw gap остаётся gap. Producer witness без replay side effect; вывод logger не источник игрового состояния.

**Результат:** измеритель больше не создаёт известный blocker; product persistence не изменяется без найденного дефекта. Не тратить на дальнейшие «проверки проверок» основную кампанию.

## 3. Довести G4 и реальные idle-симптомы

**Владельцы:**PhantomHistoricalBackgroundService (requiresNativeMaterialization/recoverNativeContext), PhantomBackgroundService/Decision, PhantomHumanLocalityControl, PhantomMaterializationRetentionPolicy/Service, PhantomVisibleAutoPlay/FarmTravel и EngagementPlanner/Controller по установленной причине.
**Тесты:**новый PhantomM1Closure041Suite.java, при необходимости package-local PhantomM1Closure041Checks.java; существующие040/039/038 suites.

- [ ] До outcome выбрать живой natural profile с допустимым фоновым goal; записать native work и текущий owner. Исходный персонаж и его сохранение не подменяются.
- [ ] Выполнить S_LIFE. Если guard/переход застрял, локализовать owner и воспроизвести RED теми же допустимыми условиями.
- [ ] Исправить первопричину; focused GREEN и соседний отрицательный case (hard pin, stale epoch, death/unknown reason). Существующая законная смерть обрабатывается штатным recovery без HP-gift.
- [ ] Выполнить новый полный полезный away/background/return на том же candidate. Фактическая работа обязательна с обеих сторон перехода.
- [ ] Для замеченного idle: зафиксировать actual selectable targets/geo/range/capability/queue/resource/debt. При реальном необоснованном простое причинный fix→GREEN; иначе конкретная штатная причина. Уровень/EXP/loot/соски проверить фактами, не обещать ещё не реализованную карьеру.

**Результат:** законченный lifecycle, не очередная просьба игроку самому подобрать удачный спот.

## 4. Заморозка кандидата, обычные сценарии

- [ ] Зафиксировать конечный product source/tools/config/evaluator; clean build из tracked staging; получить CODE_COMMIT/JAR hashes. Проверить protected/affected suites до длинных прогонов.
- [ ] Запустить S_GROUP_A/B на двух самостоятельных histories/localities с исходным CONTINUITY_V2. 6–8 enrolled, melee/caster≥5cycles, остальные≥2, исходное~380s окно. Не исключать failed participants. Совместить подход/loot и save с этими сценами.
- [ ] На041a выполнить S_RESTART: initial scope + два consecutive normal sameDB restart без import; каждый restore/useful continuation/end/drain/canonical cut. Полные goal/reward producer chains и negative tampering test. Те же stops дают G3/G5, не запускать дубликаты ради отчётов.
- [ ] Если изменился product после freeze — сделать новый candidate и повторить зависимые final gates; не смешивать разные JAR в один PASS.

**Результат:** G1–G5 на конечной сборке, кроме отдельно заявленных negative/historical cases.

## 5. Оба разрешённых crash окна — с восстановлением

- [ ] Создать041x72 из согласованного input; pre-arm guard; enrolled native work; S_CRASH72 по действительной existing boundary.
- [ ] Fault OFF, recovery-before-admission, canonical проверка; второй sameDB normal boot, отсутствие double reward и orphan ownership; stock stop, данные сохранить.
- [ ] Повторить для041x73, не одновременно. Проверить отсутствие воздействия на040a read-only.
- [ ] При обнаруженном recovery defect: causal RED→existing-owner fix→GREEN+negative; заново проверить затронутые crash/normal gates на новом final product. Не маскировать неизвестный исход SQL-heal и не оставлять включённую fault-конфигурацию.

**Результат:** G6=PASS только по обоим actual final-candidate окнам и их повторному восстановлению. Разрешение уже есть — не спрашивать его ещё раз.

## 6. Непотерянный продукт и операторская поставка

**Файлы:**существующие deployment tools переиспользовать; при отсутствии нужного интерфейса tracked tools/phantom-runtime/Build-Package.ps1, Runtime.ps1, Verify-M1.ps1; dist/START.cmd,STOP.cmd,STATUS.cmd,START_HERE_RU.md и operator template; docs/phantoms/RELEASE_CONTRACT.md. Узкие build.xml copy/target изменения при необходимости.

- [ ] Составить retention map035–041: feature→owner→regression→config/data→dist inclusion. Existing QoL/features не удалять и не выключать молча.
- [ ] Добиться сборки целой M1 distribution из tracked input, без импорта private patched classes/configs. Уже существующие проверенные libs/геоданные — явные внешние входы, не ложное утверждение «Git содержит всё».
- [ ] Минимальный операторский contract: Start идемпотентен, Status read-only, Stop штатный/drain-aware и scoped; Windows PowerShell5.1, пути с пробелами, CWD независим, свой порт занят/чужой PID → безопасный отказ. Никакого force fallback.
- [ ] Deploy только в новый C:\Users\ZBook\PhantomWorld\M1-041 со своей041final DB и портами. Провести self-start→stop→start штатно, проверить реальный JAR/config и отсутствие ops/agent dependency. Финальный start оставить живым.
- [ ] Сохранить cumulative M7 delivery contract в tracked docs и одну ссылку в Agents.md: будущие задачи обязаны сохранять принятые features и обновлять обычную поставку. Это не объявляет М2–М7 выполненными.

**Результат:**R1 PASS, standalone текущий М1; требования к полной сборке послеМ7 не потеряются в переписке.

## 7. Финальная проверка и handoff

- [ ] Полный93/93, lifecycle11/11, affected/protected035–040 и новые041 tests на final JAR; no TEST-agent на acceptance. Проверить пассивный observer без shell и после прежнего525s Synthetic lifetime, совместив ожидание с другими завершающими работами.
- [ ] Adversarial self-review полного diff по RF1–RF5. Critical/Important unresolved, влияющие на М1, запрещают READY; Zariche отмечен отдельно DEFERRED_BY_USER.
- [ ] Exact-path commit/push, local=remote, один компактный review archive по HANDOFF, окончательный runtime read-only census. Не перепаковывать все исторические archives.
- [ ] Только когда серверные gates/R1 зелёные: оставить новый041final, дать фактическую команду входа и инструкцию 10–15минут. Состояние WAITING_REAL_FINAL, не CLOSED и не «М2 уже можно».

## 8. Настоящая приёмка в продолжении041

- [ ] Человек обычным персонажем без Zariche наблюдает финальную041 сборку: игра/перемещение/короткий уход-возврат/выход-вход. Одна сессия, не серия минутных JDI окон.
- [ ] Из собственного ограниченного status/transition output подтвердить actual REAL/координаты/instance/logout→AUTONOMOUS→REAL и сохранение управления; записать его явное решение.
- [ ] Все gate PASS + явное принятие → doc-only CLOSED/READY_FOR_M2=true, published final report. Не начинать М2. Если найден обычный М1 defect, продолжать041 в оставшемся бюджете с причинным fix/retest; не прятать его под known Zariche.
