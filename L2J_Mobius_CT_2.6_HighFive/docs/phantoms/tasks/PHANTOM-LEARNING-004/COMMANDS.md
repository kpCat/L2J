# PHANTOM-LEARNING-004 — команды и проверяемые результаты

Все writes/build/native выполняются в независимом clone `L2J-learning-004`, не в пользовательском checkout. Команды ниже используют фактически найденный clone/module, JDK25 и Ant1.10.17; новые инструкции не привязаны к диску или имени пользователя. Native операции — PowerShell5 и существующий Training Director.

## Сборка и targeted contracts

Из корня независимого clone:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
```

Ant запускается последовательно. Director повторно собирает только изменённый source fingerprint. Последние canonical результаты и JAR SHA находятся в PROGRESS/evidence. Пять прежних warnings не скрыты. Один compile test-helper отказал на неверном вызове API; исправлен на существующий `optimizationParent`, focused javac GREEN, последующий canonical compile-tests PASS.

Classpath из корня clone:

```powershell
$cp='build\phantom-test\bin;build\bin;L2J_Mobius_CT_2.6_HighFive\dist\libs\*'
java -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning004ModelChecks
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004ReplayChecks
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DamageChecks
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004EpisodeChecks
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004FailureChecks
java -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning004Schema <absolute-module>
```

DB-free checks exit0. Они проверяют математику/codec/guards и не являются native обучением. RED для HP1600→1100 с ложным DAMAGE1500, отсутствующих core groups и lineage/drain contracts зафиксированы в PROGRESS.

Existing `PhantomTestLauncher` routes (seed183): learning0014, combat-core72, combat-action-ownership38, party-tactics14, training-contracts6, post002-support9 —143/143 final PASS. CWD=`<module>/dist/game`, absolute module/reports в `-Dphantom.module.root`/`-Dphantom.test.reports`, quoted аргументы. F7 и F8 standalone exit0, F8 14/14. Общий `ant verify` с DB gates не запускался.

## Собственная TEST и безопасная остановка

Из корня clone:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Start -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Resume -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Pause
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop
```

Director сам выполняет SQL prepare/probe, actors/native/evaluate/report через существующие isolated worker processes. SQL33337, game17981/login19281, только own root. Ручные TRAIN_*.cmd от владельца не требуются. `nativeFailures`/`batchesCompleted` не заменяют фактические завершённые004 learning segments.

Actual SQL cold/fault/transport routes, после штатной native остановки:

```powershell
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks inspect <absolute-own-root>
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks transport <absolute-own-root>
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks verify <absolute-own-root>
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks faults <absolute-own-root>
java -cp $cp org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks export <absolute-own-root>
```

`faults` дополнительно требует PAUSED Director. Все fault mutations — transaction-local с rollback, commits0; новые synthetic native events запрещены. `transport` проверяет14.2MB optimizer в собственной temporary table, origin=MATH_ONLY/nativeCredit0. Команда в этом списке не означает PASS: фактические exit/results в PROGRESS/evidence.

## Git и scope

Пользователь явно разрешил independent clone исходной ветки, новую004 WIP, scoped commits и non-force push только туда. Использованы `git clone`, `git switch -c wip/phantom-learning-004`, `git rev-parse`, `git symbolic-ref`, `git status`, `git for-each-ref`, `git ls-remote --heads` и exact-path `git diff` для source/branch/scope/remote inventory. Base=`06d42410fbfe1ca9b7f462b0816e0da1784630d6`. Пользовательский checkout читался только bounded проверкой branch/HEAD и source docs.

Проверки текущего scope:

```powershell
git status --short --untracked-files=all
git status --porcelain --untracked-files=all
git symbolic-ref --short HEAD
git rev-parse HEAD
git -c core.whitespace=cr-at-eol diff --check -- <exact-paths>
```

CRLF существующих файлов сохранён по local003 analog. Перед изменением local `.git/info/exclude` сделана внешняя побайтная копия с SHA; исключён только own `/.phantom-local/`. Private SQL/config/process logs не staged. Окончательные exact allowlist/stage/commit/push/remote readback будут внесены после native gates; `git add .`, force, reset/clean/stash и изменения чужих refs не используются.

Первый полный cached whitespace-check exit2: неизменённый входной `ARCHITECTURE.md:3` содержит исходный trailing space. Payload SHA совпадает с manifest; исправлять аутентифицированный owner input нельзя. Повтор cached diff-check исключает только этот exact path, остальные113 paths проходят; bounded input-preservation exception записан в SCOPE_GUARD. Первое unstaged check exit0 не проверяло новые untracked input files и не было доказательством их whitespace-чистоты.

## Final commands / exit codes

Все перечисленные focused/durability mains выполнены exit0 на final canonical source. Дополнительно:

```powershell
java -Dphantom.module.root=<module> -Dphantom.test.reports=<reports> -cp $cp org.l2jmobius.tests.phantoms.PhantomTestLauncher <route> 183
java -Dphantom.module.root=<module> -Dphantom.test.reports=<reports> -cp $cp org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks
java -cp $cp org.l2jmobius.tests.phantoms.PhantomNativeEvidenceContinuation022Suite <module> <reports>
java -Xlog:gc=info:file=<gc-log> -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning003Performance <own-root> <output-json>
powershell -NoProfile -ExecutionPolicy Bypass -File <task>/Interrupt-OwnFrozenWorker.ps1 -TimeoutSeconds 1800
```

Interrupt фактически99509 exit0, held ownworkerexit−1; ожидаемый Director worker failure затем cleanup. Resume/Pause/Stop exit0. Durability inspect/verify/faults/export exit0; SQL faults commits0. MathOnly resource profile повторил существующий ModelChecks для недостающего CPU/RAM sampling100ms, exit0; это не native credit. Full route stderr с locale dates не включён в concise evidence; сохранены настоящие assertions/summary. Own logs не удалялись.

Публикационные команды, разрешённые прямой инструкцией текущей задачи:

```powershell
$paths=Get-Content <task>/SCOPE_ALLOWLIST.txt
git add -- $paths
git -c core.whitespace=cr-at-eol diff --cached --check -- $paths
git diff --cached --name-only -- $paths
git commit -m "Complete incremental shared V3 combat learning core"
git push -u origin HEAD:refs/heads/wip/phantom-learning-004
git rev-parse HEAD
git ls-remote --heads origin wip/phantom-learning-004 feature/phantom-world master wip/coordination-130-preserved wip/phantom-learning-002 wip/phantom-learning-003
git status --short --untracked-files=all
```

Только exact allowlist, никаких StageAll/force/merge/history edits. Final report status после первого remote verification оформляется отдельным scoped docs commit и тем же non-force push; итоговый HEAD во внешней delivery receipt. `git -C <original> rev-parse HEAD` и `git -C <original> symbolic-ref --short HEAD` — только read-only подтверждение прежнего HEAD/branch. Source/TEST overlay ZIP создаётся .NET ZipArchive из exact allowlist с исключением самого ZIP/scope receipts, не включает private runtime и требует базовый003 checkout. Нативный checkpoint export хранится отдельно. SHA и каждый вложенный entry проверяются при упаковке.

## Кодировка

`rg -n -F -f <UTF8-full27-mojibake-patterns> -- <exact-text-paths>` и отдельно `rg -n -f <UTF8-six-escaped-Cyrillic-regex> -- <exact-text-paths>`. rg exit1 означает0matches, wrapper exit0; каждый прогон/число путей сохраняется. Итоговый отчёт должен содержать обе отдельные строки, не объединённое обещание.
