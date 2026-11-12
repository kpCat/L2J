# PHANTOM-LEARNING-006-PVE-OUTCOME — команды и результаты

Разрешение Git: сообщение 006 прямо требует remote refs, ancestry, HEAD/status, independent clone, branch wip006 и ordinary scoped publication/readback. Root AGENTS отдельно разрешает bounded source/scope inspection. Все Git writes выполняются только в новом clone006; root пользователя только читается. Reset, clean, stash, restore, merge, rebase, force push и запись в feature/master не выполнялись.

## Фактически выполненные Git-команды

В основном checkout:

```powershell
git remote -v
git symbolic-ref --short HEAD
git rev-parse HEAD
git status --short
git ls-remote origin refs/heads/feature/phantom-world refs/heads/wip/phantom-learning-005 refs/heads/wip/phantom-learning-006
```

Четыре локальные команды: exit0. Первый ls-remote в sandbox: exit1, GitHub443 unreachable. Тот же read-only ls-remote с network escalation: exit0, обе ветки dfcba154…, 006 absent. Это ограничение сети; automatic approval review не отклонял операцию.

Новый clone:

```powershell
git clone --branch feature/phantom-world --single-branch --no-tags https://github.com/kpCat/L2J.git D:\Codex\repos\L2J-learning-006
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short
git merge-base --is-ancestor 4a910adaea9dfb0ec5341439ffebd83d11c7cac8 HEAD
git merge-base --is-ancestor dff800f4b95232c132b374f8ec97c932eda4a6b1 HEAD
git switch -c wip/phantom-learning-006
git diff dff800f4b95232c132b374f8ec97c932eda4a6b1 dfcba154fa1f459be6082a559744d2b4a5c9b47a -- L2J_Mobius_CT_2.6_HighFive/build.xml
git diff faec72161f9e4f6231764e17d6f2637a4db080af dfcba154fa1f459be6082a559744d2b4a5c9b47a -- L2J_Mobius_CT_2.6_HighFive/build.xml
git ls-remote origin refs/heads/master refs/heads/feature/phantom-world refs/heads/wip/phantom-learning-004 refs/heads/wip/phantom-learning-005 refs/heads/wip/phantom-learning-006
git status --short --untracked-files=no
git symbolic-ref --short HEAD
```

Все exit0. Clone содержит 80077 files, исходный HEAD dfcba154…, до 006 artifacts был clean. Обе ancestry проверки успешны. Первый exact diff пуст: build.xml004 уже совпадает с текущим. Второй diff объясняет 17 добавленных Ant строк. Protected refs: master16d61833b3983a3976583d0e4813e0de9457a52f, feature/005 dfcba154…, 004 dff800f4…; 006 ещё не публиковалась на момент снимка.

Независимый GitHub connector: `search_branches(owner=kpCat,repo_name=L2J,query=feature/phantom-world)` и `fetch_commit(repo_full_name=kpCat/L2J,commit_sha=feature/phantom-world)` подтвердили ветку и HEAD dfcba154…. Данные diff не применялись.

## Read-only диагностика и baseline

Get-Content, rg, rg --files, Get-Item и Get-FileHash использовались для указанных в PLAN документов и ближайших исходников. Вышестоящие AGENTS до D:\, module-specific AGENTS и learning code-map/pattern не найдены. Повторных поисков нет.

Первое чтение private old TEST в sandbox дало AccessDenied (exit1). Повторные bounded read checks с escalation: exit0. ACL и bytes старых private директорий не менялись.

Обычный orchestration script `audit006.py` выполнен из временного artifact каталога с явными параметрами repo006, previous-root005, asset-root cycle19. Он проверил SHA207 protected assets,203 Geo и 005 receipts/timings/filtered log facts; записал только новые private baseline copies и redacted006 evidence; exit0. Script не является test harness, не вызывает Reflection, JVM, private API или DB и не запускает серверы. Exact SHA и audit counts указаны в evidence/baseline.json.

Полные raw logs не публикуются. Exact005 cast rejections указаны в diagnostics005.json с log SHA и line. Для Solo3 прочитаны pre-CUT facts и existing opening filter: distance42.591 м при minimum100 м.

`java -version`: exit0, JDK25.0.4.1+1. `ant -version`: exit0,1.10.17. MariaDB binary ProductVersion11.4.13.0. Read-only CIM inventory java.exe/mariadbd.exe:0 processes. На этой подготовительной точке final preflight был NOT_RUN; выполненные проверки перечислены далее.

Python zipfile/hashlib read checkpoint8: ZIP SHA F689100E…,11 entries, Adam blob14224664 bytes, selected4741580 bytes; optimizer769 updates/124 epochs, parent7 metadata. Архив только читался.

## Выполненная реализация и TEST006

План подтверждён пользователем: «Подтверждаю план; продолжай автономно». Runtime preflight006 exit0: exact baseline207/Geo203/JDK25/MariaDB11.4.13/own ports. Только собственные33357/18001/19301, shadow33358. Read-only inventory дополнительно обнаружил общую MariaDB mysqld PID8692/3308; к ней не подключались и не останавливали.

Временные ordinary orchestration scripts `Copy-006ColdLineage.ps1`, `Stop-006ShadowRecovery.ps1`, `Import-006Lineage.ps1`, `Set-006Pins.ps1`, `Set-006Phase.ps1` работали только на explicit own/source read-only roots. Старый004 datadir скопирован после held-exit/lock proof:505files/391415602bytes exact SHA; SQL dump10tables/231686389bytes SHA dd47fe664842c023c486a323099e10831ecbd85fad6051380498b16193fb9cce. Ошибки initial guard/PS5 wrapping/nonce config и shadow cleanup-layout сохранены в PROGRESS/private logs; каждый собственный shadow exit отдельно подтверждён, старый004 не запускался. Own006 import атомарный, full existing parent traversal8 PASS; guard не ослаблен.

Основные команды из собственного clone006:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml compile-tests
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -ConfigProfile OUTCOME006
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Resume -ConfigProfile OUTCOME006 -EnableOutcome006
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Status
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Stop
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Resume
```

Полные source compilations сначала обнаруживали missing new APIs, неверный package Spawn, package-private quote access, missing import и compound var declaration; каждый дефект исправлен, failed exits сохранены. Final canonical compile-tests exit0/36s/5existingwarnings. Canonical jar exit0 через штатный Ensure-TrainBuild, SHA в checks006.json. Последний production JAR содержит тот же product source; final Native metadata/marker fix проверен compile-tests, native budget не переигран.

Focused JDK runner classpath: `build/phantom-test/bin;build/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*` (Windows separators `;`). Выполнены:

```text
org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks contracts
org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks lineage <own-root>
org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks terminal-sql <own-root>
org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks audit <own-root>
org.l2jmobius.tests.phantoms.PhantomLearning006Checks baseline <module>
org.l2jmobius.tests.phantoms.PhantomLearning006Checks schema <module>
org.l2jmobius.tests.phantoms.PhantomLearning006Checks boundaries
org.l2jmobius.tests.phantoms.PhantomLearning004Schema <module>
```

Baseline RED→GREEN, receipt/codec RED→GREEN, schema RED→GREEN, empty-terminal trainer objective RED→GREEN. Последние contracts/boundaries/schema/baseline exit0. Lineage/empty-terminal проверялись до native509; `audit` после cold gen7:9receipts,TRAIN3,TUNING13,AUDIT11,applications0,model8,769updates/124epochs/seed183,native536,exit0. Это не actual006 fit PASS.

`Verify-006Targeted.ps1` — временная оркестрация existing runners, не новый test harness: `PhantomTestLauncher learning001|combat-core|combat-action-ownership|party-tactics|training-contracts|post002-support 183` из module/dist/game с absolute module/reports properties. Все143/143,exit0. Existing004 model/replay/damage/episode/failure checks exit0; old00528 assertions exit0; F7 `PhantomVisibleItinerary068Checks` exit0/DB_CONNECTIONS0; F8 `PhantomNativeEvidenceContinuation022Suite <module> <reports>` 14/14exit0. Первая PS5 orchestration ошибочно трактовала Java stderr при exit0 как NativeCommandError; исправлен только wrapper, повторный full targeted набор exit0. Общий Ant verify/DB gates не запускался.

Native gen4/5/6 сохраняли full denominator; gen6 worker закончил9actualkills, но Director завершился FAILED/TRAIN002_NATIVE_BATCH_INCOMPLETE из-за missing batch.complete. Marker/source metadata исправлены после кампании, failed receipt не переписан. Gen7 fresh resume пропустил completed native budget и выполнил existing trainer: COLLECTING/insufficient TRAIN,fit0. Final Stop exit0, свои процессы остановлены/порты свободны; никакого automatic budget extension.

Artifacts подготовлены ordinary Python stdlib scripts `Export-006Evidence.py`/`Finalize-006Documents.py`: читают own006 metadata/receipts/logs, записывают только новые task docs/компактный ZIP, не запускают SQL/native/tests/Git. Архив сохраняет16slots, в том числе7неуспешных preparations/interrupts. Root source owner/baseline copies и private SQL не публикуются.

## Дополнительный bounded Git inspection

```powershell
git status --short --untracked-files=normal
```

Exit0 с warning AccessDenied на private .phantom-local/train при sandbox inventory; это не полный inventory приватной папки. `.phantom-local` всегда исключён из exact stage allowlist. Reviewer выполнял только `git diff dfcba154fa1f459be6082a559744d2b4a5c9b47a -- <exact reviewed source path>` для17перечисленных source paths, без Git writes.

## Exact scope / encoding / публикация

Final allowlist:17 source/config/test +13initial task/report/config/fixture +4compact evidence +1native ZIP =35 paths. Исторические003–005 artifacts, build.xml, Player/engine files отсутствуют в allowlist. Проверка UTF-8/JSON/links/reward SHA, полный набор27mojibake markers и отдельно6escaped-Cyrillic regex выполняются `verify006_documents.py --repo <own-clone>`; вариант `--staged` сверяет точное равенство Git index allowlist. Exit codes и exact final commit/readback во внешней delivery receipt; repo не содержит ссылки на собственный ещё не созданный commit.

Разрешённая пользователем ordinary scoped delivery, только wip006:

```powershell
git symbolic-ref --short HEAD
git rev-parse HEAD
git --literal-pathspecs add --pathspec-from-file=L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/SCOPE_ALLOWLIST.txt
git diff --cached --check
git diff --cached --name-only
git commit -m "feat(learning006): stage native terminal outcomes with guarded replay"
git push -u origin HEAD:refs/heads/wip/phantom-learning-006
git rev-parse HEAD
git status --short --untracked-files=no
git ls-remote origin refs/heads/wip/phantom-learning-006 refs/heads/feature/phantom-world refs/heads/master refs/heads/wip/phantom-learning-004 refs/heads/wip/phantom-learning-005
```

Независимый GitHub connector fetch_commit с exact final SHA должен подтвердить branch HEAD и изменённые paths. Commit/push не означают FULL006 или manual acceptance. Reset/clean/stash/restore/merge/rebase/force и запись feature/master не выполняются. Git-команды разрешены прямым заданием006 и bounded scope policy AGENTS.

Byte-pinned reward JSON и новая SQL-before fixture имеют task-local `.gitattributes -text`, чтобы новый checkout не изменял exact SHA через CRLF normalization. Это необходимая metadata file того же task, не изменение исторических inputs. Выполняется только exact pair reindex:

```powershell
git add --renormalize -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/reward-config.v1.json L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/fixtures/schema-before-terminal.sql
git rev-parse :L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/reward-config.v1.json
git rev-parse :L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/fixtures/schema-before-terminal.sql
```

`Pin-006Artifacts.py --verify` сверяет Git blob SHA1 (header+raw file bytes) с каждым index rev-parse, сохраняя raw SHA256 reward3d1ae88c… и fixture79b176a2…. Обе проверки PASS. No broad renormalize.

После сохранения raw CRLF byte-exact fixture обычный `git diff --cached --check` дал exit2 на CR в конце её127строк. Содержимое fixture не исправлялось ради style: оно должно совпадать со старым SQL по SHA. Повторная проверка сохраняет все три стандартных whitespace rules и явно признаёт CRLF концом строки, только в этой команде:

```powershell
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
```

Exit0. Это не отключение whitespace проверки или изменение Git config; raw fixture SHA/index proof и остальные scope/encoding guards остаются обязательными.
