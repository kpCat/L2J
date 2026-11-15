# COMMANDS013 — журнал команд и оснований

Работа выполняется в существующих source004 и TEST006, без новых веток или checkout. Запись и публикация только feature/phantom-world явно разрешены пользователем и GIT_POLICY.md. Основной dirty checkout сохранён. Ниже параметры путей обозначены SOURCE, TEST и EVIDENCE; это фактически проверенные директории текущего сеанса, не переносимый hardcoded runtime contract.

## Git

- Read-only аудит: `git remote -v`, `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git worktree list`, `git for-each-ref`, `git merge-base`, `git ls-tree HEAD -- <exact10>`, `git diff -- <exact allowlist>`, `git diff --check`. Основание: прямое указание начать с Git-аудита и bounded exception AGENTS/GIT_POLICY.
- `git fetch origin feature/phantom-world` — актуальность единственной разрешённой ветки, без переключения рабочих файлов MAIN.
- Source публикация: `git add -- <exact paths>`, `git diff --cached --check -- <exact paths>`, `git diff --cached --stat -- <exact paths>`, `git commit -m <message>`, `git push origin feature/phantom-world`. Stage All/add-dot/force не применялись. Exact source10 перечислены в IMPLEMENTATION_PLAN.md; добавленные документы только текущего task directory.
- TEST синхронизация: `git fetch origin feature/phantom-world`, затем `git merge --ff-only origin/feature/phantom-world`. Это fast-forward существующего TEST до опубликованного source SHA, без merge commit/переписывания истории. Единственный прежний untracked TEST путь `.phantom-local/` сохранён.
- Source commits: `a954c16b652be9ca67426d3ed2cfa9a0d1b26b16`, `4e349e25f80771457cbf6db382fde7085241dd1f`, `d12a506a520b958e2bac82c247c4da95c43d5b16`. Все commit/push exit0; исходный HEAD `52aac9e0274a63906a9fe5053611b8daf1aac778`.
- Независимо: GitHub connector `fetch_commit`, `compare_commits`, `fetch_file(encoding=base64)` полных изменённых исходников; blob SHA сверяется с exact `git ls-tree`. Итоговая проверка delivery фиксируется отдельно.

## Offline / DB-free

- `ant -f <module>/build.xml phantom-learning013-test phantom-learning012-test phantom-learning011-test phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar` — последовательно, exit0, 4m35 после review fixes. Лог FIX-REGRESSION013.log вне checkout; предыдущий GREEN-REGRESSION013.log тоже exit0.
- `Test-Training.ps1 -Action Test-Cycle013` и `-Action Test-Config` — exit0, включая повтор в Windows PowerShell5.1. Synthetic state-machine receipts не являются игровым evidence.
- Existing routes Test-Arguments, Test-Atomic, Test-ExportAdmission, Test-CimExitRace, Test-StartupHandshake, Test-Broker — exit0, SQL/GS0.
- Focused JDK25 `javac -encoding UTF-8 -cp <existing classes/libs> -d <existing test output> <Native.java> <OutcomeV2Checks.java>` и `java -cp <existing classpath> org.l2jmobius.tests.phantoms.PhantomEvaluationOutcomeV2Checks history013`: behavioral RED→GREEN для physical fence, safeReason, HEAL availability и contextual MP refusal. Ошибки scaffolding/компиляции не объявлены behavioral RED.
- После двух preflight fixes whole existing `PhantomEvaluationOutcomeV2Checks` без аргументов exit0, включая outcomes/negative/codec/statistics/reader/curriculum/legacy guards.
- Pure read-only `PhantomAutonomousTrainingNative verify-eval013 <own installation> <saved CONFIG012-BEFORE-OFF.ini>` — exit0,4pairs/43paid/creditfalse. OFFLINE-REPLAY012.log вне checkout. Первая write-capable версия команды была отклонена автоматическим approval review и не выполнялась; writer удалён из verifier, безопасная версия прошла. Исторические012 receipts не переписывались.
- Parser::ParseFile для трёх изменённых PowerShell-файлов: errors0. Strict UTF8 и два отдельных rg-поиска полного набора mojibake-маркеров и escaped Cyrillic: no matches. Синтаксическая ошибка первого inline encoding-check из-за typographic quote исправлена; этот ошибочный вызов не считается проверкой.

## Own TEST — без GS до legal admission

- Canonical `Training.ps1 -Action Preflight -Autonomous -Root <own installation>` — exit0: JDK25/MariaDB11.4/Geo203/protected207/ports. Вызов из source004 с чужим TEST root был корректно отклонён EXACT_OWN_ROOT; далее использовался собственный TEST006 script.
- Canonical `Training.ps1 -Action Export -Autonomous -Root <own installation>` — exit0; собственный SQL стартовал/остановился штатно. Cold baseline2/14/903/223/apps48 сохранён.
- Byte-identical existing Read-Lineage.ps1: пять SELECT только own33357,183rows SHA9a9c2d0ee9a2b8f37da5dd6bcf625334b76be96fe9b76e22490ed20b4da70e7d. Before/after headless equality exit0. Raw TSV, SQL/defaults и ZIP остаются приватными вне Git.
- `TrainingGui.ps1 -CompileOnly -Root <own installation>` через проектный Windows PowerShell5.1 — exit0. Первоначальный PS7 вызов не поддержал WindowsApplication и не собрал GUI; это invocation error, а не продуктовый RED. Designer round-trip не выполнялся.
- Canonical `Training.ps1 -Action Prepare -Autonomous -Root <own installation>`: первая попытка exit1/worker2 HEAL offer unavailable до CUT; R1 exit1/worker2 pure Party MP3060>2806 до CUT. Исходные failed artifacts/config сохранены, reward/guards не менялись. После двух исправлений final fixed-seed R2 readiness выполняется отдельно; результат в RESULT/EVIDENCE.

## Итоговые действия

- R2 canonical Prepare wrapper exit1: `TRAIN002_WORKER_TIMEOUT_NO_FORCE` после600s. На626s existing `Write-TrainText <exact-own-R2-cohort>/stop.request HEADLESS013_WORKER_DEADLINE_COOPERATIVE_DRAIN`; worker terminal diagnostic REQUESTED_DRAIN, identity absent после завершения. Force kill не применялся; OS exit code R2 не записан held receipt и обозначен UNKNOWN.
- Canonical `Training.ps1 -Action Stop -Autonomous -Root <own installation>` exit0. `-Action Status` exit0: STOPPED/own director absent,2/14/903 restored. Выведенные generation60/batches26 относятся к историческому status, не к новым013 циклам.
- Final canonical Export exit0; byte-identical Read-Lineage.ps1 в новом external final directory с теми же5SELECT/Tag after exit0:183rows exact SHA9a9c2d0… equalBefore=true. Own SQL штатно закрыт.
- Standalone Stop не меняет config после Prepare без живого Director. После SHA-verified external backup existing Write-TrainConfig явно выставил только own autonomousEnabled=false/phaseOFF/outcomeEnabled=false. Затем Test-TrainProcessAbsent для owner/native/worker/ownSQL, Assert-TrainPortsFree33357/18001/19301, Assert-TrainHash pointer12 exit0.
- Final canonical Preflight exit0, Geo203/protected207 actual inventory PASS. Exact built-JAR/staged-JAR SHA equality PASS. Main `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`: старый7e997636…,feature,0tracked/111untracked; рабочих записей MAIN не было.
- Дополнительные короткие диагностические ошибки PowerShell (`foreach` как левый pipeline operand, reserved Error variable) исправлены; их не считают проверками или продуктовым RED. Повторные factual checks выше завершились успешно.

Native acceptance/GUI lifecycle013 NOT_RUN; real native starts/CUT/FIT/new EVAL0. Final evidence/docs exact scoped commit и ordinary feature push с независимым readback фиксируются в READBACK_DELIVERY.json. Никакого FULL по offline/GUI compile.
