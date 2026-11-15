# Команды016 и полномочия

Git использован. Основание: прямое разрешение владельца на bounded inspection/актуализацию чистого SOURCE, scoped commits и обычный non-force push только feature/phantom-world; root AGENTS допускает scope guard. Новые ветки/worktrees/clones, reset/clean/stash/restore, force push и изменение истории не выполнялись.

Рабочие каталоги: SOURCE=`D:\Codex\repos\L2J-learning-004`, TEST=`D:\Codex\repos\L2J-learning-006`, MAIN=`D:\Codex\repos\L2J-phantom-world`. Ниже команды из соответствующего checkout; module=`L2J_Mobius_CT_2.6_HighFive`. Полный префикс вместо краткого module в реальных командах сохранён в transcript. Логи/helper scripts вне Git: текущий artifacts directory, каталог `01a1e0df-49e5-7330-87b6-ba5edf88bb9c`.

## Git: фактические формы команд

```text
# MAIN: baseline и финальное сохранение чужого dirt
git rev-parse --show-toplevel HEAD
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short
git status --short --untracked-files=no
git status --short --untracked-files=normal -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT
# SOURCE: fresh remote, ancestry, неизвестные изменения, scope
git rev-parse --show-toplevel HEAD
git symbolic-ref --short HEAD
git status --short
git status --short --untracked-files=no
git status --short --untracked-files=normal
git status --porcelain=v1 --untracked-files=all
git worktree list --porcelain
git fetch --no-tags origin refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world
git rev-parse HEAD origin/feature/phantom-world
git merge-base HEAD origin/feature/phantom-world
git merge-base --is-ancestor HEAD origin/feature/phantom-world
git merge-base --is-ancestor eafcee66c18212ee9a2cdc08225ba4d5ecb0e302 origin/feature/phantom-world
git diff --name-status HEAD origin/feature/phantom-world -- AGENTS.md L2J_Mobius_CT_2.6_HighFive
git diff --name-status HEAD origin/feature/phantom-world -- PhantomSemanticStudio
git diff --name-status HEAD origin/feature/phantom-world -- AGENTS.md L2J_Mobius_CT_2.6_HighFive PhantomSemanticStudio
git remote get-url origin
git for-each-ref --format='%(refname) %(objectname) %(upstream)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world
git merge --ff-only origin/feature/phantom-world
git diff --numstat 42d6b675a9cf57faa4df8a74cc52a8b4adda97fa HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test/java L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/dist/db_installer/sql L2J_Mobius_CT_2.6_HighFive/test/resources/phantoms/db/migrations
git diff 42d6b675a9cf57faa4df8a74cc52a8b4adda97fa HEAD -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TacticalReceiptV3.java
# TEST: только inventory/guard, commit/push здесь не выполнялся
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short --untracked-files=no
```

Exact-path формы `git diff --check -- <7 code paths>` / `git diff --stat -- <exact allowlist>` / `git diff --name-status --cached -- <exact allowlist>` / `git add -- <exact allowlist>` разворачиваются по списку в `EVIDENCE.json.implementation.files` (только mutable=true) плюс12inputs/9outputs текущего task и2report/roadmap paths; список опубликованных30путей фиксируется в READBACK. Ни `add .`, ни Stage All не применялись. Развёрнутый список source paths:

```text
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/TrainingOwner.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Training.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/Test-Training.ps1
L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java
L2J_Mobius_CT_2.6_HighFive/build.xml
```

Публикация — scoped `git commit` и `git push origin HEAD:refs/heads/feature/phantom-world`; точные сообщения/HEAD/parents и полные тела независимо проверяются GitHub connector. Итоговый receipt дополняет этот раздел после фактического push. Readback Git-object inventory выполняется `git ls-tree -r HEAD -- <exact allowlist>`; это разрешённая проверка публикуемых артефактов, не broad inventory.

## Проверки и runtime

Последовательно из SOURCE:

```powershell
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning016-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning015-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-autonomous-training-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning008-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning007-test
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml jar
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-humanized-v3-content-validate
```

Все7 exit0 (`final016-*.log`). До них были focused RED/GREEN и первый полный green прогон. После реального epoch failure: `phantom-learning016-test` RED2→RED2→GREEN0, затем legacy `phantom-autonomous-training-test` и `jar` exit0. Компиляционный/fixture failure отдельно, не behavioral RED. Test runner — существующий Ant/Java/PowerShell, reflection harness не создавался. Общий `ant verify` не запускался.

PowerShell5.1 из SOURCE: `powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1` по одному аргументу `-Readiness016`, `-Readiness015`, `-Readiness014`, `-Headless013`, `-Action Test-Cycle013`, `-Action Test-Config`, `-Action Test-Atomic`; final/corrected exit0. Headless013 первый раз получил CommandNotFound в AST fixture, импорт helper исправлен; это не live headless. Часть PS шла одновременно с source Ant, Ant targets между собой последовательно. GUI не изменялся и не проверялся.

External helpers: `Setup016.ps1` (20byte backups/exact10); `Copy-Docs016.ps1` (12inputs+plan); `Run-Offline016.ps1` (первый Ant green7/7, PS external wrapper exit2 с пустым log); `Verify-Review016.ps1` (финальные Ant7/7); `Inspect-SourceTree016.ps1` (3069files,36CRLF-only,0semantic); `Sync-Test016.ps1` (exact7 и24artifacts); `Configure016.ps1`; `Freeze-Run016.ps1` (private59file copy); `Write-Evidence016.ps1`. Первые Sync guard failures остановились до TEST writes; JSON-array orchestration исправлен. Private backup child сначала не имел protected ACL, копирование не началось; для новой пустой child ACL выставлена current SID+SYSTEM, после чего59bytes-copy hashes подтверждены. Старые private directory/receipts не переписывались.

TEST commands:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Preflight -Autonomous -Root 'D:\Codex\repos\L2J-learning-006\.phantom-local\learning002\train'
powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Training.ps1 -Action Prepare -Autonomous -Readiness016Session 1 -Root 'D:\Codex\repos\L2J-learning-006\.phantom-local\learning002\train'
```

Preflight до/после exit0. Prepare exit1 wrapper /Java held exit2, штатный SQL held exit0, G3 отсутствует; повторные Prepare/Start/GS/FIT не запускались. Prepare вызвал штатные TEST compile-tests/jar из-за честного несовпадения byte fingerprints, сформировал фактический TEST stamp/JAR до выдачи readiness window. Не подменять его source JAR задним числом.

`g0/Read-Safety.ps1 -TestRepo <TEST> -OutputPath <external receipt>` до/после exit0. `g0/Invoke-ColdLineage.ps1 -TestRepo <TEST> -Tag before` и `-Tag after` запускают штатный `Training.ps1 -Action Export -Autonomous -Root <runtime>` и один read-only client с пятью SELECT. Exit0,183rows SHA до=после; raw SQL только private. G0 scripts скопированы побайтово из015, оригиналы не менялись. Export — preservation, не readiness/GS/FIT.

## Scope и текст

Final exact allowlist guard и входной MANIFEST11 проверяются перед commit. Две отдельные `rg -n` проверки по всему изменённому scope: полный набор mojibake markers и regex escaped Cyrillic/XML. Exit1 без matches — PASS, не ошибка продукта. Результаты хранятся во внешнем scope receipt и в EVIDENCE/READBACK. Final report не приравнивает ни source GREEN, ни pre-CUT к настоящему бою.

## Фактическая публикация и independent readback

`git commit -m "feat(phantoms): add guarded incremental TRAIN016 protocol"` → `a69953c76cb75389b6666fbd867902bc2671d5ef`, parent `591c5261ed52f9b3f62d9a146513935fda503afb`;30paths,2596insertions/54deletions. `git push origin HEAD:refs/heads/feature/phantom-world` exit0, ordinary fast-forward. `git rev-parse HEAD` и `git status --short --untracked-files=all` подтвердили HEAD/чистоту SOURCE. `git ls-tree -r HEAD -- <exact30>` дал30blob identities; external `Build-Readback016.ps1` сверил их SHA1 с полными локальными bytes (raw или штатно нормализованный LF), затем SHA256.

GitHub connector независимо прочитал branch ref и Git commit parents/tree, `compare_commits(591c5261,a69953c7)` подтвердил ровно30paths. `fetch_file(encoding=base64,ref=a69953c7,path=...)` прочитал полные тела всех30файлов,1 228 453bytes. Декодированные bytes/SHA256 и remote blob SHA совпали с локальными Git blobs. Это не проверка только metadata. Начальный generic fetch contents вернул raw XML вместо API JSON; parser failure не стал PASS, чтение выполнено purpose-built fetch_file.

Receipt commit: `git add --` только три точных пути READBACK.json/EVIDENCE.json/COMMANDS.md текущего task; `git diff --check --cached -- <exact3>`; `git commit -m "docs(phantoms): record Goal016 GitHub readback and partial result"`; тот же ordinary `git push origin HEAD:refs/heads/feature/phantom-world`. Финальный HEAD/parent, compare и полные изменённые receipt bodies повторно читаются connector; внешний FINAL_GITHUB_READBACK016.json фиксирует фактический final SHA без self-reference. SOURCE status/HEAD и final frozen59/protected3 guards также проверяются. Никакого code/runtime изменения в receipt commit нет.

External scope script вначале получил parser error на типографском символе в literal regex, затем неверный count из-за PowerShell array concatenation; оба отказа до staging. Regex перенесён в here-string, exact30 count и обе проверки реально прошли. Это ошибки оркестрации, не product regression и не behavioral RED.
