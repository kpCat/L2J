# Команды011 и наблюдённые результаты

Git разрешён текущим поручением владельца, TASK011§1 и GIT_AND_PRESERVATION. Все записи ограничены feature/phantom-world и exact scope. `$Source=D:/Codex/repos/L2J-learning-004`, `$Test=D:/Codex/repos/L2J-learning-006`, `$Module=<checkout>/L2J_Mobius_CT_2.6_HighFive`, `$Root=$Test/.phantom-local/learning002/train`, `$Task=$Source/<module>/docs/phantoms/tasks/PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE`, `$Artifacts=<внешний visualization root текущей сессии>/goal011`. Это фактические операторские aliases, не новые привязки product code.

## Read-only pass и Git

В source004: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git rev-parse origin/feature/phantom-world`, `git worktree list`, `git stash list` — exit0; HEADf153d137, feature, initial clean, stash empty. В main: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short` — exit0; HEAD7e997636 и protected untracked dirt сохранены. В TEST: те же branch/HEAD reads; initial `git status --short` предупредил о sandbox private ACL, затем `git status --short --untracked-files=no` exit0 и tracked clean.

`git fetch origin feature/phantom-world` в source — exit0, HEAD/originf153 совпали. GitHub connector `search_branches`, `fetch_commit(feature/phantom-world)` независимо подтвердили исходныйf153.

Read-first: `Get-Content -LiteralPath <files>` и `rg --files`/`rg -n` по task/README/AGENTS, workflow/status/package, RESULT/EVAL-REPAIR010 и перечисленным в IMPLEMENTATION_PLAN аналогам. Указанный владельцем module task path отсутствовал; пакет один раз найден в корневом docs и9файлов побайтово скопированы в source module task. SHA-256 каждого source/destination совпал. Parent AGENTS выше repository не найдены; ближайшие module instructions прочитаны.

Read-only safety: `Get-Acl`, `Get-FileHash`, bounded `.NET Directory/ZipFile.OpenRead`, `Get-CimInstance Win32_Process`, `Get-NetTCPConnection -State Listen`. Первый sandbox private/CIM read отказал, повтор с разрешённым sandbox elevation прошёл; Windows ACL/owner не менялись. При чтении hidden runtime использованы `-Force`/`.NET Directory`. Первая Unicode inline marker команда parse-failed до исполнения, заменена UTF-8 Python diagnostic file.

## Source tests/build

JDK25 из существующего JAVA_HOME, Ant1.10.17. Рабочий каталог `$Source`, обычная команда Ant — `C:/Tools/apache-ant-1.10.17/bin/ant.bat -f <module>/build.xml <targets>`.

| Targets / runner | Exit | Evidence |
|---|---:|---|
|`phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test`|0|BASELINE-TESTS.log;201s|
|`compile-tests`, gate type ещё отсутствует|1|RED-DISPATCH.log;50s;compile RED отдельно|
|`javac -encoding UTF-8 -cp build/bin;build/phantom-test/bin;<module>/dist/libs/* -d build/phantom-test/bin <AutonomousTrainingChecks.java>`|1|RED-PROTOCOL.log;missing protocol type|
|`javac <gate+checks>` → `java -cp <same> org.l2jmobius.tests.phantoms.PhantomEncounterDispatchGateChecks`|0→1, затем0→0|RED-LIFECYCLE.log/GREEN-LIFECYCLE.log; настоящий owner cleanup RED→GREEN,4checks|
|`phantom-learning011-test phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test`, первый run|1|GREEN-TESTS.log — имя историческое, фактически RED started-worker/STOP;51s|
|Та же suite после исправления gate|0|GREEN-REGRESSION.log;231s|
|`javac <PhantomAutonomousTrainingNative.java>` → `java -cp <same> org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative source-contract`|0→1|RED-REASONS.log; реальная потеря safe reason|
|Focused new catalog/freshness checks до implementation|1|RED-CATALOG.log;compile RED отдельно|
|Тот же existing source-contract после review fixes|0|GREEN-REVIEW.log; риск/свежесть/причины|
|`phantom-learning011-test phantom-autonomous-training-test jar`|0|FINAL-BUILD.log;162s;JAR/source compile|
|Final exact prepared/live seal separation, тот же source-contract|0|GREEN-REVIEW-FINAL.log;после JAR, изменён только TEST fixture|

Прямой javac использовался только для существующего runner/новых двух fixture classes, без Reflection, private method invocation или самодельной runtime-модели. Последние unit/config/lifecycle проверки не названы native gameplay proof. Пять legacy javac warnings о internal API/runFinalization сохранены, не исправлялись вне scope. Общий DB-dependent `ant verify`, C#/GUI и broad product smoke не запускались.

## Scoped source publication

`$Paths=@((Get-Content $Task/SOURCE-HASHES.json -Raw | ConvertFrom-Json).path)` — ровно8 source paths.

1. `git diff --stat -- <пять изменённых existing source paths>`, `git diff --numstat -- <module>/build.xml`, `git diff --check -- $Paths` — bounded review. Первый check exit2:7new target CRLF-only lines. Только эти7EOL исправлены bounded atomic byte patch; повтор exit0.
2. `git diff --cached --name-only` — пустой index до stage.
3. `git add -- $Paths`; `git diff --cached --name-only` — exact8allowlist; `git diff --cached --check` — exit0.
4. `git commit -m "phantom(learning011): unify lawful encounter preparation and native admission"` — exit0, `d138611901cb3ef98dbcafa8c046a2143dfbd75c`.
5. `git push origin feature/phantom-world` — exit0, ordinary non-force f153→d138.
6. `git ls-tree HEAD -- $Paths` — exit0,8blob hashes. GitHub `fetch_file` прочитал все8полных bodies atd138; SHA совпали. GitHub GET `git/commits/d138` подтвердил parentf153/treedac30e6309da2c1911e27666ec1d860eeb08dd8d и source diff8files.
7. В TEST после branch/clean guard: `git fetch origin feature/phantom-world`, `git merge --ff-only origin/feature/phantom-world`, `git rev-parse HEAD`, `git status --short --untracked-files=no` — exit0; TESTd138, private runtime не перемещён.

## Canonical TEST011 и frozen state

Перед изменением config010 — побайтовая копия вне checkout, SHA `8fae8c57…cff500` совпал. Через существующие `Read-TrainConfig`/`Write-TrainConfig` создан **новый** EVAL-only campaign011, frozen14, exploration=false, max3/45, prior0/0; исторические009/010 journal не изменены. Active config SHA `ea764419…5a31ae`. Families DAMAGE,HEAL; skill/class lists не дописывались вручную.

Из `$Test`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -Autonomous -Root $Root
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Prepare -Autonomous -Root $Root
```

Preflight exit0, actual config/dependencies/Geo203/protected207/ports, без build/SQL/GS. Prepare выполнял штатные build/stage/ownedSQL/Java headless; TEST `compile-tests` exit0/51s, `jar` exit0/31s. Existing pinned build contract не обходился. Actual stageJAR SHA `186b71fd5ec4db132fb7b6522dd9566839cca39d658a583dc8828325e8ecf0ec`, sourceFP receipt `a362e7cf726169ad5f86e4195e07711ee6eabf240183c132b5da2a698b71267a`.

Prepare wrapper exit1, worker86644 exit2, exact kernel `2026-11-14T17:51:10.8036561Z`: required SoloDAMAGE missing,4PartyHEAL preliminary, paired admission0, GS0. Raw resources и safe reason прочитаны из immutable worker receipts. Повторного Prepare, расширения поиска/политик и GameServer не было.

Existing `Read-Lineage.ps1` найден во внешнем архиве010, SHA `c8a2daa0394bfa936fb4003b159db030c36a5a1d40a38b48647e1ae3c8c8cc07`; побайтовая копия в новом `$Artifacts` с `preserved/`. Оригинал010 не исполнялся поверх исторических receipts. Он ждёт уже-owned33357 и делает только5SELECT, фиксируя UTF-8/LF rows без изменения БД.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File $Artifacts\Read-Lineage.ps1 -TestRepo $Test -Tag before
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Export -Autonomous -Root $Root
powershell -NoProfile -ExecutionPolicy Bypass -File $Artifacts\Read-Lineage.ps1 -TestRepo $Test -Tag after
```

Before reader выполнялся на owned SQL Prepare; after reader ожидал owned SQL Export, сам SQL не запускал. Оба exit0/183rows/79660bytes/SHA9a9c2d0e…4da70e7d, after==before==010. Export exit0, worker94936 exit0; ZIP CRC12entries PASS, frozen working/optimizer/accepted/application/metric bytes unchanged. Полные SQL/model artifacts остаются private, в Git только safe summary.

Active011 config побайтово сохранён вне checkout и через canonical config writer переведён OFF (SHA `abe72bbc…365ea9a`). Затем:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop -Autonomous -Root $Root
```

Stop exit0, свежий STOPPED receipt/owned0/portsfree; flagsOFF. Foreign8692/3308 не тронут. No GS journal011 создан; budget0/3 и0/2700000ms. Historical009/010 не пересчитаны в пользу011.

## Encoding, scope и финальная доставка

`python <UTF-8 Check-Encoding.py> $Task/SOURCE-HASHES.json --root $Source` — exit0:27mojibake markers и6escaped Cyrillic regex,8source files,0matches. Финальный all-artifact scan и exact scope inventory — ENCODING-CHECK.json/SCOPE-GUARD.json. Новые logs/TSV/JSON проверяются до docs commit; arbitrary private files не staging.

Final docs add/commit/push ограничиваются exact generated task/report/roadmap allowlist. Независимый GitHub GET branch/commit/tree/compare и полные RESULT/EVIDENCE/receipt bodies завершают G8. Receipt-only commit/readback фиксируется отдельно; source8blobs остаютсяd138.

Финальный scan распознал UTF-16 BOM у некоторых PowerShell captures.36mojibake matches обнаружены только в трёх generated Ant raw logs. Они побайтово сохранены вне checkout, SHA проверены; в Git публикуется RAW-LOGS-INDEX.json с exit/time/hash receipts. Исходные runtime captures не перекодировались и не выдаются за чистые исходники. В публикуемых source/docs и остальных evidence markers0; escaped Cyrillic0. Это ограничение транспорта старого console output, не новый escaped/user-facing source text.

Не выполнялись `git add .`, StageAll, новые branch/worktree, reset/clean/restore/stash mutation, merge с чужой веткой, rebase/squash/force push. Единственный merge — обычный TEST fast-forward опубликованного feature. Git используется только по явно разрешённому Goal011 scope.
