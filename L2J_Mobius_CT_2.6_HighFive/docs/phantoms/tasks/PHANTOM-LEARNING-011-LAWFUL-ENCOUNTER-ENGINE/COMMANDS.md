# Команды продолжения011 и наблюдённые результаты

Продолжался тот жеGoal, два явно разрешённых scope: local contract и Spawn sentinel. Текущие факты имеют приоритет над первоначальным журналом ниже. `$Source=D:/Codex/repos/L2J-learning-004`, `$Test=D:/Codex/repos/L2J-learning-006`, `$Root=$Test/.phantom-local/learning002/train`, `$Artifacts=<external visualization session>/goal011`. Это операторские aliases; product code новых абсолютных привязок не получил.

## Git, exact source scope и commits

В существующем source004 выполнялись `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse origin/feature/phantom-world`, `git status --porcelain`, `git diff --cached --name-only`, `git fetch origin feature/phantom-world`. MAIN проверялся только `git rev-parse HEAD` и `git status --porcelain -uno`; он остался7e997636…, tracked dirt не менялся, полный historical inventory не пересчитывался.

Source publication через bounded Publish-Local-Source.ps1: `$Paths` — фактические modified paths из exact LOCAL-SOURCE-HASHES.json allowlist; index изначально пуст, branch feature и HEAD==origin обязательны. Выполнялись `git add -- $Paths`, `git diff --cached --name-only`, `git diff --cached --check`, затем конкретные `git commit -m <message>` и `git push origin feature/phantom-world` (обычный, exit0):

| Commit | Точный message |
|---|---|
|eea088afaa8014171154b678e9dca8510c8a9c0b|phantom(learning011): bind local encounters to canonical spawn geometry|
|c458637db24d4f7bd04565d679774ac927b34b18|phantom(learning011): keep local encounter bootstrap outside global farm planning|
|bbe66f2bfd4b86b0d7f56b164e752f9310e5f969|phantom(learning011): preserve native spawn metadata and record realization facts|
|e98c94bcbce19247584143b6aec423cf2004b9c4|fix(spawn): preserve random heading sentinel for native encounter parity|
|8ef41d17a10c664f0d09bf5d1072b0b5f20f01a8|phantom(learning011): seal only paired native-admitted headless cells|
|0c331f738d082c9476754c6561f9e8ae51d4b464|phantom(learning011): balance evaluation orders per direction and preserve source caches|

TEST после tracked-clean guard: `git fetch origin feature/phantom-world`, `git merge --ff-only origin/feature/phantom-world`, `git rev-parse HEAD`. Private `.phantom-local/` сохранён. Для final artifact guard разрешены exact `git diff --check -- $Allowlist`, `git diff --name-only <baseline> HEAD -- $Allowlist`, `git ls-tree -r -l HEAD -- $Allowlist`; final docs staging использует только actual modified allowlisted paths. Ни branch creation, force/reset/clean/stash mutation, rebase, broad restore или StageAll не выполнялись.

GitHub connector независимо получил `git/ref/heads/feature/phantom-world`, comparef153…0c331f73 (57paths,13source) и полные13source bodies вbase64. Git blob SHA1 вычислен по всем bytes, сравнен с API. Ни один base64 payload не публиковался в tool output. Для больших bodies использовались chunks≤59000chars. Полный final docs readback идёт после их push, receipt не содержит собственный hash.

## Дешёвые RED→GREEN и canonical Prepare

JDK25/Ant1.10.17. `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml phantom-learning011-test phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar` после Spawn fix — exit0/185s, raw SPAWN-FULL-REGRESSION.log. Local integration011/autonomous/JAR exit0/103s; отдельные007/008 exit0/102s. Последний source-contract order/cache GREEN — exit0. Canonical Prepare final заново выполнил существующие compile-tests/JAR и создал exact build receipt; никаких `verify`/DB-wide suites вместо focused gate не запускалось.

Behavioral RED: canonical SilentValley local rejection; actual Spawn.setHeading(-1)=0; per-direction order confound; config-only cache collision. Затем focused GREEN. Compile-only ошибки интеграции и первоначальный cache typo не выдаются за behavioral proof. Raw logs byte-preserved вне checkout, safe exit/hash index в evidence/continuation/tests.json и RAW-LOGS-INDEX.json.

Все Prepare использовали штатную команду:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Prepare -Autonomous -Root $Root
```

Ранние continuation sessions4e4c348…/4fdda1d…/7948c3a…/dcbd237… отказали до CUT/GS: global roster binding, native sentinel offset, штатный Party DPS refusal. Session7d02ee… source8ef41d… дала первый полный headlessPASS; после order/cache correction выполнена свежая final session2e8ca933…. Wrapper/worker exit0,4scenes/8paired arms,4/12candidates; native spawn8casesPASS. Worker38708/nonce88c1751b…:116207ms kernel, CPU222.390625s, peak4648873984B. Это headless, не GS-budget.

## Единственный native запуск и shutdown

После final full headlessPASS выполнена ровно одна Start:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Start -Autonomous -Root $Root
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Status -Autonomous -Json -Root $Root
```

Start exit0, Director86628, GS60792, attemptce5d1ef6826742b2a5786d1c5bd58caa. EVAL_ONLY frozen14, explorationOFF.8CUT/47paid/8terminal; Party2complete/Solo0; strict failureTRAIN010_EVAL_MATCHED_PAIR_COVERAGE. GS kernel exit0, wall328635ms; budget-charge OWN_HELD_HANDLE_EXIT_TIME,1/3starts. Native CPU265.203125s/peak4696219648B. GS2/3 NOT_STARTED.

Обычные read-only post-EVAL команды (каждая exit0, без GS/fit): `Training.ps1 -Action Export -Autonomous -Root $Root`, затем `-Action Report`. В controlled Export ownedSQL window byte-identical Read-Lineage.ps1 выполнил только5SELECT сTagafter;183rows/79660bytes/SHA9a9c2d0e…4da70e7d совпал сTagbefore и010. Fresh ZIP model-2-c048637fd35f4d7298e96e7ebefa7f38.zip SHAa2327086…da0f2, CRC12entriesPASS,11payloads exact; только manifest.createdUtc отличается. Устаревший GUI export-result.json не использован как текущий receipt.

Final-Off.ps1 сохранил active config побайтово/SHA90ff8147…303ff, canonical Read/Write-TrainConfig выключил autonomous009Enabled/outcome006Enabled, phaseOFF; бюджет/journal/model не менял. OFF SHA1e61a46f…77d6d4. Затем:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop -Autonomous -Root $Root
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -Autonomous -Root $Root
```

Обе exit0:STOPPED/OFF/owned0/33357,18001,19301free/Geo203/protected207PASS. Foreign8692/3308 identity от28.10 preserved. Read-only Final-Safety.ps1 сохранил exact stop receipt, foreign creation, pointer SHAacc21bed…c9b010 и SHA всех прежних budget journals;00910/5803240,0104/715447,0111/328635. Полный historical asset inventory не пересчитывался.

## Delivery scope и проверки

До обновления44прежних docs files побайтово скопированы в external delivery-before сSHA. Exact current allowlist74paths/13source вSCOPE-GUARD;16новых safe evidence files и1readback receipt относятся только кGoal011. Private SQL/model/Geo не публикуются. Original TASK/ARCHITECTURE/PLAN/TEST_MATRIX/GIT_AND_PRESERVATION и history009/010 не меняются.

`python Check-Global-Contract.py` и `python Check-Spawn-Scope.py` — exit0: после удаления только additive local блоков /согласованного Spawn override все прежние bytes совпадают с backupSHA. `python Collect-Continuation.py` читает actual receipts без игровой мутации:20unique actors,8targets,47events, full timing rule;3linked-lethal HP callbacks раскрыты. Внешний reader один раз исправил имя native-resources.json; никаких повторных игровых runs. Native-admission legacy sessions raw неvalidJSON, сохранён verbatim; форматный предел указан в reports.

`python Check-Encoding.py FINAL-ALLOWLIST.json --root $Source` отдельно проверяет27mojibake markers и6escaped Cyrillic regex. Финальный ENCODING-CHECK и exact status/index guard записываются до docs commit. Current G8 становитсяPASS только после полного independent final docs/evidence byte readback. Manual gate всегдаREQUIRED.

# Первоначальный журнал команд011 до согласованного продолжения

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

## Завершённый GitHub readback

Docs commit `b12d943d72970692387da05f43849b5f653b5c38`: `git add -- $Docs` (41exact paths), `git diff --cached --name-only`, `git diff --cached --check`, `git commit -m "docs(learning011): record bounded coverage refusal and frozen-state evidence"`, `git push origin feature/phantom-world` — exit0. GitHub GET `git/ref/heads/feature/phantom-world`, `git/commits/b12d943d72970692387da05f43849b5f653b5c38` и comparef153..b12 подтвердили HEAD/parentd138/tree696d061c11169c1051b5230fb8127c3180f49489/49exact paths.

`git ls-tree -l HEAD -- $ExistingAllowlist` дал49blob SHA/size.8полных source bodies проверены отдельно;41полный docs body проверен `node:crypto` с Git `blob <bytes> NUL` SHA1. Коннектор UTF-16 логи транскодирует; raw blob endpoints для них отказали по UTF-8 capability. Base64 content восстановлен в исходные UTF-16LE bytes только при необходимости, затем точные Git SHA1/size обоих build logs совпали. Никакого изменения evidence ради сравнения нет. GITHUB-FINAL-READBACK.json содержит полный manifest41+8.

Последний commit — только9existing docs/receipt updates и1новый readback receipt в прежнем exact allowlist; `git add -- <фактические modified allowlisted paths>`, `git diff --cached --check`, `git commit -m "docs(learning011): confirm published readback and manual gate"` и ordinary `git push origin feature/phantom-world` выполняются перед final GET. Final ref/parent/diff и полные изменённые RESULT/EVIDENCE/receipt bodies проверяются снова; их self SHA не встраивается в сам receipt. TEST завершается `git fetch origin feature/phantom-world`, `git merge --ff-only origin/feature/phantom-world` после tracked-clean guard; это docs-only FF, без rebuild/SQL/GS. Итоговый HEAD сообщается в чате.
