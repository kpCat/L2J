# COMMANDS017

Команды выполнялись после read-first аудита и явного согласования IMPLEMENTATION_PLAN017.md. Обозначения: `$Source` — существующий SOURCE004, `$TestRepo` — существующий TEST006, `$Module = Join-Path $Source 'L2J_Mobius_CT_2.6_HighFive'`, `$Train = Join-Path $TestRepo '.phantom-local/learning002/train'`, `$Evidence017` — сохранённый внешний каталог evidence017. В публичных командах личный путь оператора заменён ролью; код не привязан к этому пути. Полные retained logs с SHA перечислены в EVIDENCE.json. Повтор G0 Export сейчас не требуется: он изменит operational SQL files и сделает прежний stoppedSQL proof устаревшим.

## Read-only аудит

`Get-Content -LiteralPath ... -Raw`, `rg --files`, bounded `rg -n` применялись к AGENTS/README/workflow/status, всем13 task inputs, RESULT/NEGATIVE/EVIDENCE/COMMANDS016 и целевым Java/PS/build.xml. Прочитанные файлы и3 локальных аналога перечислены в approved plan. Backup выполнен до чтения private raw: все source/copy SHA до/после совпали. Ни .native.json record text, ни SQL before_json не содержат нужных native write timings84.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/Backup-ReadOnly017.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/Backup-Extra017.ps1"
python "$Evidence017/Audit-Raw017.py"
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/g0/Invoke-ColdLineage.ps1" -TestRepo $TestRepo -Tag before
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/g0/Invoke-ColdLineage.ps1" -TestRepo $TestRepo -Tag after
python "$Evidence017/Verify-SqlCensus017.py"
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/Capture-ColdProof017.ps1" -Tag after -Name cold-proof-after017.json
```

Private wrappers — сохранённые bounded orchestration scripts, не новая инфраструктура проекта. G0 использует frozen TEST Training.ps1 `-Action Export -Autonomous -Root $Train`, собственную короткую SQL-сессию, read-only SELECT и held exit. Exit0/0, SQL183 exact before/after. Нового GS не было. Дополнительная private native census SELECT проверила51/399/27/247 и exact cells, без UPDATE/INSERT/DDL.

## Focused RED → GREEN и regression

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Closure017
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Action Test-Cycle013
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Readiness016
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Headless013
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Action Test-Config
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Test-Training.ps1" -Action Test-Atomic
ant -f "$Module/build.xml" phantom-learning016-test
ant -f "$Module/build.xml" phantom-autonomous-training-test
ant -f "$Module/build.xml" phantom-learning008-test
ant -f "$Module/build.xml" phantom-learning007-test
ant -f "$Module/build.xml" jar
ant -f "$Module/build.xml" phantom-humanized-v3-content-validate
```

Ant только последовательно и в SOURCE, TEST JAR не заменялся. Последний `ant -f build.xml phantom-learning016-test jar` exit0/42сек после последней source правки. Шесть PS-групп exit0 после review fixes. READINESS first synthetic ONE_FIT coverage сохранён; composed real ONE_FIT запрещён и NOT_RUN.

DB-free Java CLI, из `$Module` (это важно для fixture ini path):

```powershell
$Cp = "$Source/build/bin;$Source/build/phantom-test/bin;$Source/build/phantom-test/resources;$Module/dist/libs/*"
java -Xmx256m '-Dphantom.module.root=.' -cp $Cp org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative party-reward-contract017
java -Xmx256m '-Dphantom.module.root=.' -cp $Cp org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative diagnose-cohort $Train
```

Оба финальных запуска exit0. CLI принимает installation root `$Train`, не cohort directory. Party fixture сообщает `NOT_NATIVE SQL=0 GS=0 rewardsNotFabricated=1`; это не новый native receipt. Census7/6/4, sufficient=false,51 old summaries UNOBSERVED.

RED логи: red017-phase/cold/party и review-red017-readonly/alias/publication/marker/counters/input-lease. GREEN — соответствующие green/review-green и boundary017-final. Behavioral RED отдельно от orchestration ошибок (RESULT.md). Concurrent publication проверена двумя реальными издателями и held named mutex; readonly process helper — реальным held child и genuine exit receipt при simulated API31. Полный boundary proof использовал actual frozen TEST inputs, writer STUBBED; реальные записи проверены отдельно настоящим CLI ниже.

## Реальный cold repair (дважды, exit0)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$Module/tools/phantom-runtime/training/Training.ps1" -Action RepairClosure017 -Root $Train -RepairEvidenceRoot "$Evidence017/repair-closure017" -ColdLineageProof "$Evidence017/cold-proof-after017.json"
```

Первый вызов создал external immutable closure/marker, второй прочитал те же bytes. В обоих `COMPLETED_NO_FIT JAVA=0 SQL=0 GS=0 FIT=0`. Исторический journal/checkpoint не записывались; ordinary Start/Resume не запускался.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$Evidence017/Final-Preservation017.ps1"
python "$Evidence017/Check-Encoding017.py" mojibake <exact-file>
python "$Evidence017/Check-Encoding017.py" escaped <exact-file>
```

Final preservation exit0:1822 immutable originals,1823 backup copies, единственный permitted operational worker-process.json. Кодировки проверяются двумя отдельными полными проходами exact25; unchanged legacy matches вне изменённых строк отдельно отражаются в evidence. Результат не подменяется одной проверкой.

## Git: точные формы и разрешение

Git разрешён пользовательским017, корневым bounded scope guard и approved plan, включая scoped add/commit/обычный push. Команды audit до gate полностью приведены в IMPLEMENTATION_PLAN017.md. Для SOURCE/TEST использован только одноразовый `-c safe.directory=<exact checkout>` (глобальный config не менялся). Для повторяющихся SOURCE команд ниже общий префикс `git -c safe.directory=D:/Codex/repos/L2J-learning-004 -C D:\Codex\repos\L2J-learning-004`; для TEST аналогичный точный006. `MAIN` использовал обычный `git` из cwd.

```text
git rev-parse --show-toplevel
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short
git status --short -uno
git status --short --untracked-files=all
git worktree list
git fetch origin feature/phantom-world
git rev-parse origin/feature/phantom-world
git merge-base HEAD origin/feature/phantom-world
git merge --ff-only origin/feature/phantom-world
git ls-tree HEAD -- <exact TerminalRewardReceiptV1.java path>
git diff --stat -- <exact5 code paths>
git diff -- <exact code/delivery paths>
git diff --check -- <exact25 paths>
git diff --cached --name-only -- <exact25 paths>
git diff --cached --check -- <exact25 paths>
git -c core.autocrlf=false add -- <exact13 unchanged input paths>
git -c core.autocrlf=true add -- <exact12 code/delivery paths>
git commit -m "Fix frozen NO_FIT closure and retain Party validation evidence (017)"
git push origin HEAD:feature/phantom-world
git -c core.autocrlf=true add -- <exact RESULT.md EVIDENCE.json READBACK.json paths>
git commit -m "Record independent GitHub readback for learning017"
git push origin HEAD:feature/phantom-world
```

`<exact25>` перечислен без wildcard в READBACK.json; exact13 — неизменённые package inputs; exact12 —5 code +7 delivery docs. ff-only SOURCE e8→eed подтверждён до исходников, только10 чужих Studio files в этом переходе. `ls-tree` и локальный Git blob SHA1 проверили прежний CRLF→LF contract: physical TerminalReward blob707abe52… нормализуется в baseline96346456…, код не менялся. Для13 input docs нормализация отключена, чтобы сохранить MANIFEST bytes. Работа scoped, add . /Stage All/clean/reset/stash/restore/rebase/force/новые branches/worktrees не применялись.

Первый commit содержит exact25; второй — только3 публикационных receipts. Перед push проверяется fresh origin; при non-FF запрещены force/rebase. Публикация считается завершённой лишь по независимому свежему GitHub HEAD/parent/diff/full body, а не по локальному origin ref.

## Независимый GitHub readback

Использован подключённый GitHub plugin GET `branches/feature/phantom-world`, compare base→delivery и delivery→receipt. При final checksum readback полных25 тел/commit tree дополнительно читаются публичные HTTPS GitHub API/raw responses: connector преобразует body в текст, а для побайтового SHA256 нужны исходные bytes. Все ответы сохраняются в private evidence. Проверяются fresh HEAD parents, exact changed paths, SHA1 каждого Git blob и полный SHA256 каждого публичного source/evidence, MANIFEST unchanged13. Две стадии позволяют избежать самоссылки SHA самого READBACK: публичный receipt подтверждает delivery commit, финальный receipt-only HEAD повторно проверяется снаружи; его SHA сообщается владельцу.

Результаты publication/readback, реальные commit SHA и две отдельные encoding checks находятся в READBACK.json/EVIDENCE.json; окончательный HEAD сообщается после последней независимой проверки.
