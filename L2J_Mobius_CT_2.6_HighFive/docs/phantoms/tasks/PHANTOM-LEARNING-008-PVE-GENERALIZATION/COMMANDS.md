# COMMANDS008

`/goal PHANTOM-LEARNING-008-PVE-GENERALIZATION`

Все команды выполняются в существующем TEST006, если явно не указано иное. Ant последовательно. Прямое разрешение Git — разделы2/12/13 задания и подтверждённый PLAN; только ветка008.

|Команда|Результат|
|---|---|
|`git rev-parse HEAD`|exit0;062862eca8e0bcedd4107d7b91720627307f736c|
|`git symbolic-ref --quiet --short HEAD`|exit0;исходная wip/phantom-learning-007|
|`git status --short --branch`|exit0;только untracked .phantom-local/|
|`git rev-parse --git-dir` / `git rev-parse --git-common-dir`|exit0;.git/.git;отдельный clone, не linked worktree|
|`git switch -c wip/phantom-learning-008 062862eca8e0bcedd4107d7b91720627307f736c`|exit0;создана только разрешённая008|
|`git symbolic-ref --quiet --short HEAD` / `git status --short --branch` после switch|exit0;wip/phantom-learning-008;private данные сохранены|
|`ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning007-test`|exit0;4groups PASS;44s;5pre-existing warnings;DB/native NOT_RUN|

Исходные read-first Git команды и GitHub comparisons также сохранены в PLAN. Source backups сделаны Copy-Item в собственный scratch вне checkout; Get-FileHash SHA-256 проверил побайтовое совпадение11файлов. Больших копий репозитория нет.

Focused008 RED→GREEN: admission1→0; baseline1→0; targets1→0; native learned-skills1→0; authority1→0; codec1→0; EVAL008 namespace1→0; paired TRAIN credit1→0. Подробные stdout/exit codes находятся в компактном evidence archive; controlled observations не выдаются за native receipts.

`ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning008-test phantom-learning007-test`: exit0,1m33s. `ant ... phantom-learning008-test` после native adapters: exit0,29s. PowerShell Parser.ParseFile Training.ps1:0errors.

Штатный `Training.ps1 -Action Preflight -Root <owned-TEST>`: exit0. Штатный `Training.ps1 -Action Export -Root <owned-TEST>`: exit0, fresh cold SQL export с точными Adam11/DRAFT11/applications. Это единственный SQL запуск до008 PREPARE; GameServer0.

## Native и финальная проверка

Точные постоянные аргументы: `<repo>` = `D:\Codex\repos\L2J-learning-006`; `<module>` = `<repo>\L2J_Mobius_CT_2.6_HighFive`; `<owned-TEST>` = `<repo>\.phantom-local\learning002\train`. Shell cwd для owner — `<module>`, для Ant — `<repo>`. Реальные пути являются фактами этой машины; product scripts не привязаны к ним.

Owner CLI: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action <action> -Root <owned-TEST>`. Start/Resume008 добавляли `-ConfigProfile OUTCOME006 -EnableOutcome006 -EnableExploration008`. Использованы только существующие owner/broker. Private phase/config менялись при STOPPED; не через SQL repair.

|Запуск/команда|Результат|
|---|---|
|PREPARE26 / Resume|worker отказ доGS: stopped guard после pool; защита перенесена до pool|
|PREPARE27/28/30/32/34|exit0; canonical native classes/skills, sealed catalog, Geo56slots, build receipts|
|COLLECT Director29/31/33/35|GS1/2/3/4; первые три имеют сохранённые failed/incomplete slots; последний completed all remaining35slots|
|Owner Stop после каждого segment|exit0; held own exit/ports check; никакого kill чужих PID|
|GeoProbe на exact inputs slots2/4|DB-free;180/160 valid,240blocked; source positions зафиксированы; TSV/logs в archive|
|`ant ... phantom-learning008-test` cold range RED→GREEN|1→0; MysticMuse range0 regression, затем все4groupsPASS|
|Owner Export доfit|exit0; модели11/Adam11/old007 payload exact initial|
|FIT Director36|worker exit2 после COMMIT: cold reader loss units mismatch; SQL commit не отменялся|
|PREPARE37|отказ cold candidate admission, точная причина reader mismatch; GS0|
|`ant ... phantom-learning008-test phantom-learning007-test` cold units RED→GREEN|1→0; GREEN1m31s|
|FIT restore Director38|exit0; persisted12 восстановлен, безrefit/duplicatecredit|
|EVAL Director39|GS5;16arms/8matchedcompletedpairs; training state unchanged|
|CONTROL Director40|GS6;4arms/2matchedstockpairs;0divergences; training state unchanged|
|`Training.ps1 -Action Stop -Root <owned-TEST>` послеcontrol|exit0;STOPPED/portsfree|
|`Training.ps1 -Action Export -Root <owned-TEST>` послеEVAL|exit0; fresh export d2b3039e…;6payload entries byte-identical to a85a9a76…; manifest export metadata differs|
|`ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning008-test phantom-learning007-test jar`|exit0;2m03s;5existingwarnings;последовательно,толькоTESTbuild/distlibs|
|`FinalRegressions008.ps1 -TestRepo <repo>`|exit0;10existingDB-freeCLI routes listed below|
|`Training.ps1 -Action Preflight -Root <owned-TEST>` final|exit0;config/JDK25/MariaDB11.4/Geo203/protected207/freeports;noGS|

Regression Java executable — JDK25 `java.exe`; classpath — `<repo>\build\bin;<repo>\build\phantom-test\bin;<repo>\build\phantom-test\resources;<module>\dist\libs\*`. Каждая отдельная команда exit0:

```text
org.l2jmobius.tests.phantoms.PhantomLearning004Schema <module>
org.l2jmobius.tests.phantoms.PhantomLearning004ModelChecks
org.l2jmobius.tests.phantoms.PhantomLearning005Checks
org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks contracts
org.l2jmobius.tests.phantoms.PhantomLearning006Checks clock-r1
org.l2jmobius.tests.phantoms.PhantomLearning006Checks heal-totals-r1
org.l2jmobius.tests.phantoms.PhantomLearning006Checks authority-r1
org.l2jmobius.tests.phantoms.PhantomLearning006Checks campaign-r1
org.l2jmobius.tests.phantoms.PhantomLearning006Checks boundaries
org.l2jmobius.tests.phantoms.PhantomLearning006Checks routing-r1 <module>
```

`CollectEvidence008.py <owned-TEST> <evidence>`: exit0, read-only file aggregation/SHA comparison, безSQL/fit. `AnalyzeFrozen008.java` — public codec frozen observations/OOD, exit0; первый compile отказал по package-private quote, заменён formatter только в artifact helper. `InspectCampaign008.java` — canonical file-only42samples/56targets, exit0; первый запуск с nonhex run identity отказал, helper identity исправлена, данные не менялись. Неиспользованный DiagnoseFit008 не запускался и в delivery не включён.

Диагностические чтения двух предполагаемых путей007 COMMANDS и campaign budget дали file-not-found; фактические пути найдены одним bounded `rg --files`. Эти ошибки не были test failures и не вызвали native retry. Все значимые stdout и negative RED logs сохранены в evidence ZIP. Старые accepted007 tests не выдаются за новые native008 результаты. `ant verify`, fault injection008, full-system soak и GUI NOT_RUN.

## Дополнительный Git для scope/review

Прямое разрешение текущего задания включает scoped branch/commit/push008; root Agents разрешает bounded status/diff. Skill requesting-code-review явно разрешил независимый read-only review. Все перечисленные inspection команды exit0, кроме local merge-base с отсутствующим feature object (exit128, безfetch); GitHub connector подтвердил ancestry отдельно. Никаких merge/reset/restore/stash/clean/force/StageAll.

Review cwd `<repo>`: `git status --short`. Exact `git diff --` paths использовались группами из16-path allowlist: три learning core paths; Training.ps1; пять Native/Ledger/Evaluation paths; ActorFixture/ShadowNative/PhantomTrainingNative/build.xml. Новые файлы прочитаны непосредственно; untracked private train не проверялся черезGit из-за protected ACL, использован owner-controlled inventory.

Дополнительные read-first argv и полный exact allowlist находятся в PLAN. Final scope/diff-check, exact add/commit/push argv и независимый GitHub receipt будут дописаны после выполнения. Только27allowlisted paths; private dataset/optimizer/history вGit не добавляются.

`FinalScope008.ps1 -TestRepo <repo> -Scratch <owned-scratch>`: final exit0. В helper последовательно выполнены `git status --short --untracked-files=normal`, `git status --short --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive`, `git diff --check -- <27exactpaths>`, `git diff -- <27exactpaths>`. Полные развёрнутые argv — archive `analysis/final-scope-encoding.json`; root status допускает только прежний untracked private root. Final scope27/27, никаких дополнительных изменённых paths.

Две отдельные encoding команды `rg -n -- <full-mojibake-pattern> <26textpaths>` и `rg -n -- <escaped-Cyrillic-and-XML-pattern> <26textpaths>` вернули1/no matches, что означает PASS. Полные patterns/paths в archive helper FinalScope008.ps1. Первые helper parser/short inline quoting failures и diff-check CRLF rejection сохранены в PROGRESS; исправлен только helper quoting и21новых Ant line endings, baseline bytes вокруг сохранены. После этого exact diff-check exit0.
