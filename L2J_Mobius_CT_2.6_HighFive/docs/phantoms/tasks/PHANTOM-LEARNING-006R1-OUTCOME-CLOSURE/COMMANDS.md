# PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE — команды

Начальные разделы — исторический снимок ДО approval. Итог выполнения после подтверждения приведён в последнем разделе; ранние NOT_RUN не являются финальным статусом. Основной checkout только читался.

## Выполненный bounded Git inspection

Прямое задание разрешает создание новой ветки от exact WIP006 и ordinary publication только в неё. Root AGENTS дополнительно разрешает bounded state/scope inspection.

```powershell
git rev-parse --show-toplevel --git-dir --git-common-dir HEAD
git symbolic-ref --short HEAD
git status --short --untracked-files=no
git for-each-ref --format='%(objectname) %(refname)' refs/heads refs/remotes
git ls-remote --heads origin feature/phantom-world master wip/phantom-learning-006 wip/phantom-learning-006r1
git rev-parse HEAD
git switch -c wip/phantom-learning-006r1 5a38d35194c914c5dda90f8d57763a916201e977
git -C <own-clone> symbolic-ref --short HEAD
git -C <own-clone> rev-parse HEAD
git status --porcelain=v1 --untracked-files=all
git status --short --untracked-files=all
git status --porcelain=v1 --untracked-files=all -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE.md
```

Успешные команды exit0. Branch creation выполнена только после повторных exact HEAD и clean tracked guards; затем повторены `git rev-parse HEAD` и `git symbolic-ref --short HEAD`, exit0.

Первый ls-remote без sandbox escalation отказал в network access (git error); последующее чтение .git/config в той же shell-команде привело к общему shell exit0. Это НЕ успешный remote check. Повторный standalone ls-remote с разрешённой escalation exit0 подтвердил точные remote SHA и отсутствие новой ветки.

GitHub connector read-only search_branches независимо подтвердил существование feature/wip006. Он возвращал имена, не SHA; SHA доказаны standalone ls-remote. Fetch/push/commit/add/merge/restore/reset/clean/stash не выполнялись.

## Read-first и состояние TEST

`Get-Content`, bounded `rg --files`/`rg -n`, `Get-ChildItem`, `Get-FileHash`, чтение director/recovery/report/config, `Get-Process` и `Get-NetTCPConnection` использовались только read-only. До повышения sandbox private train inventory вернул AccessDenied; это не считалось полным inventory. Повторная bounded escalation дала доступ.

Составной первоначальный resource read завершился shell exit1 при отсутствии процессов/слушателей; повторная структурированная проверка с подсчётом отсутствующих matches exit0: STOPPED generation7, java0/mariadbd0/listeners0. Старая collection —96files,9terminal JSON; inventory SHA в EVIDENCE.

Во время первичного поиска ошибочно указан отсутствующий каталог tests (в проекте test); rg вернул exit2, затем поиск ограничен существующими путями. Ранний ошибочный read несуществующего skill path завершился exit1, затем прочитан настоящий SKILL.md. Эти неуспехи не являются product test failures.

## Подготовленные документы

Новые восемь task/report/allowlist файлов сформированы apply_patch в разрешённом компактном staging. Копирование в clone — exact filename allowlist, только новые destinations, SHA source/destination; никаких wholesale source copies.

`Verify-006R1Documents.py --stage <compact-staging>` exit0:8files, strict UTF-8/JSON PASS. Первоначальная inline PowerShell-проверка не выполнилась из-за ParserError при передаче кириллических markers через shell; вместо повторов использован маленький UTF-8 Python stdlib validator, без тестового harness или Reflection.

- mojibake-маркеры в изменённых файлах проверены:27markers, PASS;
- escaped Cyrillic в изменённых файлах проверены:6regex patterns, PASS.

После копирования тот же validator с `--repo <own-clone>` exit0 для8фактических файлов: UTF-8/JSON/27mojibake/6escaped patterns/relative links PASS. Эти document gates не являются build/native gates.

Первый full untracked scope guard завершился exit1 EXACT_SCOPE_MISMATCH. Read-only диагностика exit0 показала прежние private `.phantom-local` SQL/shadow/runtime files, существовавшие до R1; это не новые исходники. Никакой cleanup/ignore mutation не выполнялась. Правильный guard сравнивает8exact new document paths, а глобальный tracked status отдельно должен быть пустым. Private runtime никогда не включается в stage allowlist.

## Следующие команды после approval

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -ConfigProfile OUTCOME006
```

Expected: exit0 при exact baseline207/Geo203 и собственных ports. Ant последовательны. Focused/red/green, 143affected/28old005/F7/F8 — существующие runners из006 COMMANDS с актуальными counts и текущим classpath, без нового тест-проекта. Конкретные новые runner args фиксируются вместе с implementation и фактическими exits; не выдавать этот раздел за выполненные проверки.

Own Start/Resume/Pause/Stop и offline terminal fit/export выполняются через existing Director после pin новой campaign/config. Публикация — только явный exact allowlist add, scoped commit и ordinary `git push -u origin HEAD:refs/heads/wip/phantom-learning-006r1` после проверки. Пока NOT_RUN.

## Выполнено после подтверждения PLAN

Пользователь подтвердил: «Подтверждаю план; продолжай автономно». Использован existing independent clone006 и единственный Training.ps1 Director. Scope и точные paths в SCOPE_ALLOWLIST.txt, private .phantom-local не stage.

Canonical `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests` и `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar` выполнялись последовательно, финальные exit0,5старыхwarnings. Director повторяет эти же команды при изменённом source fingerprint; build.json вручную не подменялся. Preflight exit0 baseline207/Geo203/own ports. Ant verify/DB-wide gate не запускался.

Полные actual Java argv с JDK25/classpath/cwd/props, wall seconds, exit codes и JUnit attributes: [checks-r1.json](evidence/checks-r1.json). Это существующие mains, не новый shell/Reflection harness:

- PhantomTestLauncher шесть routes learning001/combat-core/combat-action-ownership/party-tactics/training-contracts/post002-support, seed183:143checks PASS; после последней pre-CUT правки повторены exit0.
- PhantomLearning005Checks:28old005 PASS, после последней правки повторены exit0.
-004 Model/Replay/Damage/Episode/Failure/Schema;006 contracts/quota-r1/campaign-r1/authority-r1/routing-r1/clock-r1/heal-totals-r1/boundaries/baseline/schema:exit0. Quota/campaign/authority/routing/clock/heal-totals имели наблюдаемый RED до реализации.
- Learning006OutcomeChecks before-r1: SQL536events/9oldreceipts/Adam8/769updates/124epochs до collection, exit0. Старый509-only SQL assertion не запускался как current gate.
- Learning006OutcomeChecks commit-r1: cold exact model9/Adam817/172/13markers/7TRAINused, duplicate reward/application1062ROLLBACK, secondFreeze NONE, historical006used=false, exit0. Controlled pre-COMMIT crash NOT_RUN.
- F7 PhantomVisibleItinerary068Checks exit0, F8 PhantomNativeEvidenceContinuation022Suite exit0/14checks. Первые F7 wrappers ошиблись package и incomplete classpath после compile failure; исправленный canonical run PASS. Это orchestration failures.

Own Director actions:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Resume -ConfigProfile OUTCOME006 -EnableOutcome006
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Pause -ConfigProfile OUTCOME006 -EnableOutcome006
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop -ConfigProfile OUTCOME006 -EnableOutcome006
```

Actual generations/attempts/exit codes в PROGRESS и [resources-r1.json](evidence/resources-r1.json). Между segments использован Stop→Resume: Resume на live PAUSED не исполняет новый OUTCOME segment. Export request867b42c7b58248448a46811177b0aa21 штатной queue COMPLETED. TRAIN_CONFIG заменялся atomic exact copy после byte/SHA backups внутри own archives и вне checkout. Phase PROOF/EVALUATE/CONTROL менялись только при STOPPED, pins из verified export; A/B smoke/main один config/JAR/protocol, без fit.

Gen9 IOException до native/CUT получил один обычный retry; deeper transient cause не доказана. Первый proof отказал до actors/CUT из-за missing historical V1 observer. Existing005 importer и новый safe namespace proof-r2 сохранили отказ и позволили продолжить; DRAFT promotion отсутствует. Proof-r2 выявил PRE_CUT_FACTS_DIFFER, исправлено перед A/B по реальным facts без ослабления comparator.

Source edits apply_patch → Sync-006R1.py --apply с exact13name allowlist/previousSHA/atomic replace/SHA. Менялись11из13allowed sources. Export-006R1Evidence.py проверяет31unique eligible native actions и temporal bounds, historical96file SHA. Package-006R1Native.py проверяет ZIP и каждый archived SHA. Finalize-006R1Files.py --copy --stage проверяет global tracked scope11, exact task inventory, whitespace,27mojibake markers и6escaped patterns. Verify-006R1Documents.py --repo проверяет8docs UTF-8/JSON/relative links. Helpers — stdlib orchestration/file validators вне checkout, не новый supervisor/test project. Exact receipts/scripts сохранены в compact operations archive после публикации.

## Финальные Git scope / publication команды

Git разрешён прямым заданием для новой ветки/scoped commit/non-force push. Дополнительные bounded команды: `git status --porcelain=v1 --untracked-files=no`, `git diff --stat -- <exact11source paths>`, `git diff -- <exact11source paths>`, `git diff --check -- <exact11source paths>`. Reviewer также читал только exact diff/stat. Полный literal path list приведён в SCOPE_ALLOWLIST.txt; shell expansion не использовался.

Перед commit выполняется `git add --` с ровно28literal paths allowlist, argv создаётся stdlib subprocess. Это не add . и не Stage All. Actual exits/HEAD/readback записываются отдельной delivery receipt и финальным сообщением после выполнения, чтобы не создавать self-referential commit SHA в его содержимом.

```powershell
git commit -m "Close bounded terminal outcome learning campaign 006R1"
git push -u origin HEAD:refs/heads/wip/phantom-learning-006r1
git rev-parse HEAD
git ls-remote --heads origin feature/phantom-world master wip/phantom-learning-006 wip/phantom-learning-006r1
```

GitHub connector fetch branch/commit/files at exact SHA — независимый readback, только GET. Feature/master refs до/после сравниваются. Force/merge/reset/restore/stash/clean не выполняются. Final manual gate остаётся REQUIRED.

После source push/readback выполнен отдельный bounded docs commit: `git commit -m "Record verified 006R1 publication evidence"`, затем тот же ordinary push. Stage только шести изменённых task/report docs: EVIDENCE,RESULT,REPORT,HANDOFF,PROGRESS,COMMANDS. Нативные данные и код повторно не менялись. Итоговый SHA проверен отдельно после receipt commit.

Exact argv scope checks/stage (полные literal paths; не shell wildcard):

```powershell
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/COMMANDS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/EVIDENCE.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/SCOPE_ALLOWLIST.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/artifacts/native-evidence-r1.zip L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/ab-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/baseline-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/checks-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/cold-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/collection-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/fit-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/proof-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/resources-r1.json L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TerminalReplayV1.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/learning/Learning006OutcomeChecks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning006Checks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Outcome006.ini L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1
git symbolic-ref --short HEAD
git rev-parse HEAD
git status --porcelain=v1 --untracked-files=no
git diff --cached --check -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/COMMANDS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/EVIDENCE.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/SCOPE_ALLOWLIST.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/artifacts/native-evidence-r1.zip L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/ab-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/baseline-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/checks-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/cold-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/collection-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/fit-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/proof-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/resources-r1.json L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TerminalReplayV1.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/learning/Learning006OutcomeChecks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning006Checks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Outcome006.ini L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1
git symbolic-ref --short HEAD
git rev-parse HEAD
git status --porcelain=v1 --untracked-files=no
git ls-tree -r HEAD -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/COMMANDS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/EVIDENCE.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PLAN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/PROGRESS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/SCOPE_ALLOWLIST.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/artifacts/native-evidence-r1.zip L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/ab-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/baseline-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/checks-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/cold-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/collection-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/fit-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/proof-r1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006R1-OUTCOME-CLOSURE/evidence/resources-r1.json L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TerminalReplayV1.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/learning/Learning006OutcomeChecks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning004Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLearning006Checks.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Outcome006.ini L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1
```
