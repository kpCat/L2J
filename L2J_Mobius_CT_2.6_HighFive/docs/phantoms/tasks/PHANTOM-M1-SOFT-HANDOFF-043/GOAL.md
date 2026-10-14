# GOAL043 — native combat settlement → productive background → useful reentry

Прочитай весь пакет `docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043/` (`TASK.md`, `CONTEXT.md`, `DESIGN.md`, `PLAN.md`, `ACCEPTANCE.md`, `SOURCE_MAP.tsv`, `HANDOFF.md`) и необходимые `Agents.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, `docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/RESULT042.md`, `GATES042.tsv`, `REVIEW042.md`, `CHECKPOINT042.md`, а также `PHANTOM-M1-FINAL-CLOSURE-041/ACCEPTANCE.md`, `SCENARIOS.md`, `CURRENT_STATE041.md`.

Это отдельная **узкая TASK043**, а НЕ повтор ночной кампании041/042. Отправка этого `/goal` утверждает ограниченный план и обычную реализацию (без субагентов), не является разрешением crash, сетевых probes, перехвата клиента или доступа к старой БД.

BASE043=`9a378d0e8984b3673b41538a9f5f9c9823507317`; remote=`experiment/m1-candidate007-observe008`. Единственный модуль — High Five. OWN=`C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`; MAIN=`C:\Users\ZBook\L2J_Mobius` — только чтение входного пакета. Работай в уже имеющемся OWN, **не создавай новый worktree**, не переключай ветки, не делай reset/clean/restore/force.

**Цель:** получить один полный G4 PASS того же стабильного profile: фактическая полезная native работа → законное завершение native combat/soft-retire после ухода единственного наблюдателя → FULL_SAVE и owning handoff → подтверждённая полезная background операция по реальному прошедшему времени → legal reentry того же profile и новая native полезная работа. Если в042 удержание было законным из-за реальных incoming hits — это сохраняется как отрицательный сценарий, а не исправляется принудительной дематериализацией.

Сначала установить из текущего Java и B2 evidence, закрываются ли новые combat roots после ухода наблюдателя и **почему** `ACTIVE_ACTION` остаётся. Не менять продукт ради PASS, пока нет фактического causal RED на владельце. Если native combat не завершился законно, подобрать детерминированные исходные **предусловия до исхода**, без выбора победителя задним числом. После доказанного дефекта: causal RED → минимальное изменение действующего владельца → GREEN/negative → реальный native сценарий на окончательном JAR. Не создавать вторую систему AI, новое сохранение или автоматический форсированный stop боя.

Ранее G2 PASS (A5 8/8 + independent C24 8/8) на JAR SHA256 `5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c`. Не повторять G2, если игровая семантика и весь контракт не менялись; при влияющем source-fix — повторить обе группы на новом frozen candidate с исходным CONTINUITY_V2 и сохранить каждый исходный FAIL.

Бюджет: до **4 часов wall-clock**, последнее 25 минут оставить на безопасную штатную остановку и публикацию. Не требовать от меня ночных кликов/входа/решений. Если по истечении времени G4 не достигнут, сохранить root cause/точную границу, source и все data, не выдавать PARTIAL за PASS.

Строго запрещены: crash72/73 и любые intentional process kills/fault hooks; сетевой перехват/патч клиента/проверки защищённых портов; остановка/перенастройка общей службы MariaDB и запись в чужие схемы, старого живого040a, 041x72/x73, 041final, MAIN/PLAY/старых raw/DB; установка новых runtime-клиентов/библиотек; SQL/HP/MP/EXP/SP/loot/clock/rates/radius/limit heal; изменение45/90/120, Synthetic525, cap250, защита earned/CAS/debt; изменение evaluator028.

Итог: `RESULT043.md`, `GATES043.tsv`, `REVIEW043.md`, `CHECKPOINT043.md`, компактный проверенный `TASK043-review.zip`; exact-path обычный commit+non-force push и HEAD=remote, own043 JVM0, protected040a untouched, все DB/raw сохранены. M1=OPEN и READY_FOR_M2=false до отдельных G1/G3/G5/G6/R1/G8/REAL_FINAL. М2 не запускать.
