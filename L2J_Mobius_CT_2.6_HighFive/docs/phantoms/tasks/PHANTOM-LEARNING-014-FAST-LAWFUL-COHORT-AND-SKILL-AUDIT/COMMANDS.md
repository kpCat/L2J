# Команды014 и основание

Разрешение: текущий `/goal`, TASK/GIT_AND_SAFETY и подтверждение IMPLEMENTATION_PLAN014/exact6 владельцем. SOURCE — существующий `D:\Codex\repos\L2J-learning-004`; TEST — существующий `D:\Codex\repos\L2J-learning-006`; MAIN — `D:\Codex\repos\L2J-phantom-world`, без записей. Приватные evidence лежат в текущей внешней папке Codex и private TEST; SQL/defaults/raw actors/models в Git не включаются.

## Git, только bounded scope

- MAIN/SOURCE/TEST: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git status --short`; MAIN `git worktree list --porcelain`. SOURCE/TEST42d6b675; MAIN7e997636 как вF3. Вывод MAIN сохранён как прежний dirt, без add/restore/reset.
- SOURCE: `git for-each-ref --format='%(refname) %(objectname)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`.
- SOURCE `git ls-tree HEAD --` два exact native source path: remote GitHub full-body SHA1 независимо совпал с локальным деревом.
- `git diff --name-only -- <exact task014 path>` в MAIN, затем `git diff --stat -- <exact4>`, `git diff --check -- <exact4>`, полный `git diff -- <exact6>` в SOURCE для review. Все разрешены bounded audit/current task. Историю команд013 не переисполняли.
- Финальная публикация: `git fetch origin feature/phantom-world`; `git rev-parse origin/feature/phantom-world`; `git diff --check -- <exact FINAL_SCOPE allowlist>`; `git add -- <exact allowlist>`; `git diff --cached --check -- <exact allowlist>`; `git diff --cached --name-only`; `git commit -m <scoped message>`; `git push origin feature/phantom-world`. Результаты и точные SHA фиксируются в RESULT/EVIDENCE/READBACK. Никаких add-dot, новых веток, worktrees, force или переписывания истории.

## Read-only safety и frozen SQL

- Штатный Windows PowerShell5.1: `Training.ps1 -Action Preflight -Autonomous -Root <own TEST>` — exit0 перед baseline и post-fix,203Geo/207protected/tools/ports/owner.
- `Training.ps1 -Action Export -Autonomous -Root <own TEST>` — cold export и own SQL start/stop, без GS. До baseline и после baseline exit0.
- Byte-identical прежний `Read-Lineage.ps1 -TestRepo <TEST> -Tag before/after`: только5SELECT,183rows, SHA2569A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D. После baseline equalBefore=true. Fresh checkpoint2/14/903/223, selected/terminalAdam SHA и pointer12 проверены.
- `Initialize-TrainWin32`, `Assert-TrainInstallation`, `Assert-TrainPortsFree @(33357,18001,19301)`, `Test-TrainProcessAbsent` для own director/worker/native/SQL — штатный PS5.1 exit0. Диагностический PS7 visibility UNKNOWN не считается canonical result.

## Проверки и builds

- `java -version`: JDK25.0.4.1; `ant -version`:1.10.17.
- `Test-Training.ps1 -Readiness014`: RED exit1 `READINESS014_SHARED_BUDGET_MISSING`; GREEN exit0:480+720, total1200, no replay/third session/unproven exit. Первый test-only UTC comparison исправлен после `BASELINE_CAP`; это не продуктовый behavioral RED.
- `Test-Training.ps1 -Headless013`: sandbox отказал CIM; штатный ordinary OS запуск exit0:timeout/cancel/recovery/orphan,3held child exits, SQL/GS0. Автоматическое разрешение расширенного запуска получено для canonical CIM/OS fixtures.
- `ant -f <module>/build.xml phantom-autonomous-training-test phantom-learning013-test jar`: telemetry build exit0,1m54s.
- `ant -f <module>/build.xml phantom-learning014-test phantom-learning013-test phantom-learning012-test phantom-learning011-test phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar`: после исправления reversed List arguments fixture exit0,6m13s. Первоначальный compile exit1 не объявлялся behavioral RED.
- После source freeze: `Test-Training.ps1 -Readiness014`, `-Action Test-Config`: exit0. Полный ant verify/чужая DB/native POST002 не запускались.
- Existing `java ... PhantomTestLauncher post002-support 2002002` с current source root и внешней report directory:9/9 PASS, exit0, DB/GS0. Это contract/static proof, не native effects.

## Ровно два readiness вызова

- Перед заменой exact TEST source/config — внешние byte backups и проверка SHA256. Вне checkout `Setup-Readiness014.ps1` вызывает existing owner/config guards, копирует только утверждённые source paths и меняет только own private campaign/dataset/off flags. Skill/HP/MP/gear/SQL reset отсутствуют.
- `Training.ps1 -Action Prepare -Autonomous -Readiness014Session 1 -Root <own TEST>`: wrapper exit1, worker2, TIMEOUT272357ms, withinCap=true, worker/SQL absent. Cost phases и denominator вPREPARATION014-S1.json. GS/FIT/credit0.
- `Training.ps1 -Action Prepare -Autonomous -Readiness014Session 2 -Root <own TEST>`: второй и последний вызов, immutable720s cap/work510s. Итог отдельно вPREPARATION014-S2.json/RESULT. Никаких ручных продлений deadline.
- Фактический второй итог:wrapper exit1/worker2, TIMEOUT515549ms,withinCap=true,own worker/SQL absent. Сумма272357+515549=787906ms. Cold final Export/read-only5SELECT exit0/183rows exact; final Preflight203Geo/207protected exit0; PS5.1 process/ports and frozen pointer/model/Adam exact assertions exit0.
- Вызовы выполняют DB-free compile-tests/JAR ДО выдачи headless deadline. Это время отдельно от readiness/SQL/worker intervals, не скрывается и не называется native combat time. Startup SQL, worker launch и cleanup включены в session result.
- `Aggregate-Preparation014.ps1`: только разбор существующих coarse JSONL, private versioned cost receipt и sanitized aggregate. Это не тестовый harness или игровой симулятор. Старые013 receipts не меняет.

## Независимая GitHub проверка

GitHub connector: fresh feature HEAD; pinned commit/compare; full files encoding=base64. Декодированные bytes проверяются повторным SHA1(`blob <bytes>\0`+body), remote blob SHA и exact `git ls-tree`. SHA1 self-tests:abc и Git empty blob. Первоначальный oversized Node call отклонён автоматическим review по64KB; безопасные bounded chunks завершили проверку. В итоговой проверке используется тот же независимо проверенный byte-level алгоритм без вывода raw payload.

Финальные encoding проверки выполняются отдельно: полный набор mojibake-маркеров и escaped Cyrillic/XML; результат вFINAL_SCOPE/EVIDENCE. Commit/push и manual gate описываются только по фактическому исходу.

Final external `Verify-Scope014.ps1 -Repo <SOURCE>`: после исправления diagnostic UTF8 BOM/quote/array issues — full mojibake NO_MATCH, escaped Cyrillic/XML NO_MATCH, original manifest11/11, exact6source/34allowed. `git diff --check` обнаружил CR в10новых XML строках: исправлен только этот блок, XML parsePASS; атомарная замена завершена canonical NullString после первого empty-null invocation error. Повторный guard exit0. Java/JAR не менялись, TEST XML синхронизирован после выхода с verified backup; новые readiness не запускались.
