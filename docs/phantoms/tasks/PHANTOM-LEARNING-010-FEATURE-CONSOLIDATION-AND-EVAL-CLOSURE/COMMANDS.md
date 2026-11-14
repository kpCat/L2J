# COMMANDS010

Все Git-команды разрешены явным TASK010/GIT-POLICY и текущим поручением владельца: audit→preserve→merge→scoped commits/push→independent readback. За рамками этой задачи общего разрешения на произвольный Git нет. Пути ниже — фактические операторские каталоги этой сессии, не новые привязки в product code.

`$Main=D:/Codex/repos/L2J-phantom-world`, `$Source=D:/Codex/repos/L2J-learning-004`, `$Test=D:/Codex/repos/L2J-learning-006`, `$Module=<checkout>/L2J_Mobius_CT_2.6_HighFive`, `$Root=$Test/.phantom-local/learning002/train`, `$Artifacts=<внешний visualization root этой сессии>/goal010`.

## Git audit и сохранение

- `git -c safe.directory=<root> -C <root> <args>`: точные root/args/exit из первоначального inventory приведены ниже. Все65 вызовов exit0. Для repeat status дополнительно `-c core.fsmonitor=false -c core.untrackedCache=false`; owner-проверка подтвердила исходные dirty counts.
- `git -C <each independent root> fetch origin` и затем `git -C <root> fetch origin refs/heads/*:refs/remotes/origin/*`: exit0 в main/004/005/006; без prune/force. Single-branch refspec требовал второго явного fetch.
- `git merge-base <feature> <009>`, `git merge-base --is-ancestor <head> <009>`, `git rev-list --left-right --count <feature>...<009>`, `git ls-tree <tree>`, `git rev-parse <ref>`, `git for-each-ref`: exit0, exact ancestry/scope audit.
- `git ls-files --others --ignored --exclude-standard -z`: exit0 для тех же5roots; отдельный ignored inventory2716584paths. Это bounded artifact inventory, не index mutation.
- `python audit.py`, `python preserve.py`, tail-resume preservation, `python preserve_git.py`, `python inspect_ignored.py`, `python preserve_ignored_source.py`: успешные итоговые receipts; SHA/readback всех архивных объектов. Первый ZIP writer отказал на timestamp<1980; исходный архив не заменялся, хвост сохранён отдельно. Числа/hashes в LOCAL-AUDIT/EVIDENCE.
- `git bundle create <external local-stash.bundle> <stash/index refs>`; `git bundle verify <external local-stash.bundle>`: exit0. `git pack-objects` для двух snapshot trees с исключением опубликованных объектов и `git index-pack --strict <external pack>`: exit0. Exact object IDs и prerequisites указаны в LOCAL-AUDIT. Ни branch, ни stash не удалён/восстановлен поверх canonical code.

## Консолидация и публикации

В004: fresh `git fetch origin refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world refs/heads/wip/phantom-learning-009:refs/remotes/origin/wip/phantom-learning-009` exit0.

Первый `git switch --create feature/phantom-world --track origin/feature/phantom-world` отказал из-за refspec (exit128); index оказался равен опубликованному feature tree. Сохранены index/config/SHA. `git config --local --add remote.origin.fetch refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world`, затем `git switch --no-track --create feature/phantom-world origin/feature/phantom-world` и `git branch --set-upstream-to=origin/feature/phantom-world feature/phantom-world`: exit0. Force/reset/restore не использовались.

`git merge --no-ff --no-commit origin/wip/phantom-learning-009`: exit0. `git write-tree`, `git diff --cached --stat`, `git diff --cached --name-only`: exit0. Initial `git diff --cached --check` exit2 на CRLF build.xml; `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check` exit0, исторические bytes сохранены. Merge commit8752 содержит оба parent. Обычный `git push origin feature/phantom-world` exit0 **доB**.

Последующие product commits6c97(8paths),ccd93(2из них),add152(4из них): перед каждым fresh fetch/rev-parse/symbolic-ref, exact `git diff --check -- <allowlisted paths>`, `git add -- <точные пути>` и `git diff --cached --name-only`; scoped `git commit -m <subject>` и обычный `git push origin feature/phantom-world`: exit0. Все subject/SHA записаны в EVIDENCE. Ни `git add .`, ни Stage All не применялись.

В TEST006 source обновлён `git fetch origin refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world` и `git merge --ff-only origin/feature/phantom-world`, только при STOPPED и отсутствии tracked dirt: exit0. На feature006 был обычный switch без force; private untracked сохранены. Основной dirty checkout не FF и не переключался.

Source patches: `git apply --check <exact external patch>` и `git apply <exact external patch>` только allowlisted paths: exit0. Это способ bounded writes в существующем004, когда Windows ACL/sandbox не даёт обычной записи туда. Первый auto-review отказ счёл004 внеfeature; read-only branch/upstream/HEAD доказали feature и TASK§A разрешение clean004, после чего та же bounded операция одобрена. Один Node payload>64KB отклонён, receipt сохранён меньшими блоками; обхода approval не было.

## Build и focused contracts

Из source004, JDK25.0.4.1/Ant1.10.17, `-Dbuild=$Artifacts/build-A` либо `build-B`:

```text
ant -f <module>/build.xml -Dbuild=<external build-A> phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test phantom-learning001-test phantom-combat-action-ownership-test phantom-humanized-v3-content-validate jar
ant -f <module>/build.xml -Dbuild=<external build-B> phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar
ant -f <module>/build.xml -Dbuild=<external build-B> phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar
ant -f <module>/build.xml -Dbuild=<external build-B> phantom-autonomous-training-test jar
```

A197с/B145с/C166с/D80с, все exit0. Ant последовательно; stageA/B/C/D-ant.log. Старые JAR004 до замены сохранены с SHA; внешний build изолирован от MAIN/release. `ant verify` NOT_RUN.

`powershell -NoProfile -ExecutionPolicy Bypass -File $Artifacts/Run-Regressions.ps1 -TestRepo $Source -BuildRoot $Artifacts/build-A`: exit0,10existing004/005/006 DB-free routes (точные class/args/exit в EVIDENCE или stage-A-regression-results receipt). SQL0/GS0.

Existing Java `AutonomousTrainingChecks` inventory/shortlist/eligibility/curriculum/objective/config/catalog и `PhantomAutonomousTrainingNative source-contract`: GREEN exit0. Focused RED/GREEN: lawful-before-cap, config3/45, native risk, arm codec compile-contract, bounded placement,180с config rejection. Runtime RED exit1, GREEN exit0; compile-only RED отдельно обозначен в EVAL-REPAIR. Первые javac sandbox dependency lookup failures не засчитаны behavioral RED.

`powershell ... Test-Training.ps1 -Action Test-Config` и `-Action Test-CimExitRace`: RED exit1→GREEN exit0, включая malformed exact-exit timing и cadence. После нового180с case final Test-Config exit0, evalOnlyInvalid=6. PS parser0errors. Source config guard add152 после native budget: native NOT_RUN.

Две отдельные проверки всех итоговых изменённых текстовых файлов:

```text
rg -n -F -f <mojibake-patterns.txt, полный набор27> -- <exact changed files>
rg -n -f <escaped-cyrillic-patterns.txt, все6regex> -- <exact changed files>
```

Обе rg exit1 означают0matches; wrapper exit0. Не путать с runtime JSON serializer escaping. CRLF-aware final `git diff --check -- <exact paths>` exit0.

## Изолированный TEST и native010

Все действия через Windows PowerShell5.1 `-NoProfile -ExecutionPolicy Bypass`, owner/identity/protected guards обязательны:

```text
Training.ps1 -Action Preflight -Root <ownedRoot>
Training.ps1 -Action Export -Root <ownedRoot>
Configure-Goal010.ps1 -TestRepo <TEST006>
Training.ps1 -Action Prepare -Autonomous -Root <ownedRoot>
Training.ps1 -Action Resume -Autonomous -Root <ownedRoot>
Training.ps1 -Action Stop -Root <ownedRoot>
Configure-Goal010B.ps1 -TestRepo <TEST006>
Training.ps1 -Action Prepare -Autonomous -Root <ownedRoot>
Training.ps1 -Action Resume -Autonomous -Root <ownedRoot>
Training.ps1 -Action Stop -Root <ownedRoot>
Configure-Goal010C.ps1 -TestRepo <TEST006>
Training.ps1 -Action Prepare -Autonomous -Root <ownedRoot>
Training.ps1 -Action Resume -Autonomous -Root <ownedRoot>
Training.ps1 -Action Stop -Root <ownedRoot>
Read-Lineage.ps1 -TestRepo <TEST006> -Tag after
Training.ps1 -Action Export -Root <ownedRoot>
Inspect-AutonomousColdExport.py <after.zip> <external after/cold.json> --minimum-version 14
Postflight-Goal010.ps1 -TestRepo <TEST006>
```

Control/Preflight/Export/Prepare/Resume/Stop итоговые exit0. Detached director/native outcomes **не равны** control exit0: A=TRAIN010_EVAL_MATCHED_PAIR_COVERAGE; B=TRAIN002_NATIVE_BATCH_INCOMPLETE/NO_LAWFUL_SCENE; C=TRAIN002_NATIVE_BATCH_INCOMPLETE/Invalid combat request180>120. JVM exit0 при shutdown не засчитан как успешная EVAL. Native charge265981+221044+113990=601015мс, starts3/3. Обычный TRAIN/FIT не вызывался.

Read-Lineage before и after выполняют только SELECT на identity-проверенном собственном33357, exit0,183строки с одним SHA. Helpers не запускают/останавливают SQL; lifecycle принадлежит штатному Export/Prepare. Cold inspector CRC/raw bytecopy exit0 before и after. Первая after-age проверка ошиблась из-за сравнения Local/UTC DateTime и отказала до копирования; повтор с DateTimeOffset.UtcDateTime exit0, повторного Export не было. Внешний Configure010B сначала parse-failed без BOM до записей; UTF-8 BOM добавлен, повтор exit0. Один диагностический Read-Host вызов был пустым и не дал runtime mutation.

`collect010.py` — read-only агрегация существующих receipts и hash comparison3716старых файлов, exit0; это анализ файлов, не самодельный shell runtime/test harness. Postflight: own0, portsfree, активные flagsOFF, cumulative counters preserved, source/privateSQL/models/Geo/protected сохранены. Ни foreign PID, ни MAIN/PLAY не останавливались.

## Independent GitHub readback

Exact commit subjects использованных `git commit -m`:

```text
Merge published learning006-009 into feature/phantom-world
Fix lawful evaluation admission and owned native exit accounting
Expand bounded lawful target placement coverage
Reject evaluation windows beyond native combat request bound
Document Goal010 consolidation and bounded partial evaluation
Record independent Goal010 GitHub verification
```

Заключительный scope/readback использует `git show <verifiedCommit>:<exact allowlisted path>` (24blobs), `git show -s --format=%H %s <8752> <6c97> <ccd93> <add152>`, `git diff --name-only 8752d88a8a963b5e283e0ecd0f69c6c319ac5e62`, `git diff --cached --name-only`, `git ls-files --others --exclude-standard`, `git add -- <24exactpaths>`; exit0. Receipt commit содержит только запрошенные docs; его фактический HEAD/parent/tree/diff дополнительно читается с GitHub после ordinary push. Source8blob SHA в финальном HEAD сверяются с независимо прочитанными полными телами в родителе. Последующий final status/check читает только004/006/основнойroot и свои процессы/порты, без запуска GS/SQL.

GitHub connector GET commit/ref, `/git/commits/<sha>` parents/tree, fetch_file полных восьми critical sources и final docs, локальный Git blob SHA-1 + SHA-256: exact comparison, без записи через connector. После каждого обычного push branch HEAD проверяется самостоятельно. StageA receipt отдельно, финальный publication receipt содержит HEAD/parent/tree/source hashes/diff scope. Локальный `git` push/status сам по себе не засчитан независимым readback.

## Первоначальные точные audit команды

| Root | git arguments | Exit |
|---|---|---:|
| `D:\Codex\repos\L2J-phantom-world` | `rev-parse --show-toplevel` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `rev-parse HEAD` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `remote -v` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `branch -avv` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `worktree list --porcelain` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `for-each-ref --format=%(refname) %(objectname) %(upstream)` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `stash list --format=%gd %H %gs` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `reflog --all --format=%H %gD %gs` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `tag --list` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `log --all --oneline` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `rev-list --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `rev-list --reflog --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-phantom-world` | `-c core.quotepath=false status --porcelain=v2 -uall -z` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `rev-parse --show-toplevel` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `rev-parse HEAD` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `remote -v` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `branch -avv` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `worktree list --porcelain` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `for-each-ref --format=%(refname) %(objectname) %(upstream)` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `stash list --format=%gd %H %gs` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `reflog --all --format=%H %gD %gs` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `tag --list` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `log --all --oneline` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `rev-list --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `rev-list --reflog --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-004` | `-c core.quotepath=false status --porcelain=v2 -uall -z` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `rev-parse --show-toplevel` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `rev-parse HEAD` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `remote -v` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `branch -avv` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `worktree list --porcelain` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `for-each-ref --format=%(refname) %(objectname) %(upstream)` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `stash list --format=%gd %H %gs` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `reflog --all --format=%H %gD %gs` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `tag --list` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `log --all --oneline` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `rev-list --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `rev-list --reflog --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-005` | `-c core.quotepath=false status --porcelain=v2 -uall -z` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `rev-parse --show-toplevel` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `rev-parse HEAD` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `remote -v` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `branch -avv` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `worktree list --porcelain` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `for-each-ref --format=%(refname) %(objectname) %(upstream)` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `stash list --format=%gd %H %gs` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `reflog --all --format=%H %gD %gs` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `tag --list` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `log --all --oneline` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `rev-list --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `rev-list --reflog --all --not --remotes=origin` | 0 |
| `D:\Codex\repos\L2J-learning-006` | `-c core.quotepath=false status --porcelain=v2 -uall -z` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `rev-parse --show-toplevel` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `rev-parse HEAD` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `remote -v` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `branch -avv` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `worktree list --porcelain` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `for-each-ref --format=%(refname) %(objectname) %(upstream)` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `stash list --format=%gd %H %gs` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `reflog --all --format=%H %gD %gs` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `tag --list` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `log --all --oneline` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `rev-list --all --not --remotes=origin` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `rev-list --reflog --all --not --remotes=origin` | 0 |
| `D:\Codex\worktrees\phantom-109-controlled-delivery\L2J-phantom-world` | `-c core.quotepath=false status --porcelain=v2 -uall -z` | 0 |
