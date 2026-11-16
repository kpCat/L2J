# COMMANDS018

Wall start: 2026-11-16 17:28:21 UTC. T+4min: preliminary UNKNOWN_MISSING_EVIDENCE; T+7min: STOP018_NO_CAUSAL_PROOF/NO_GO_NATIVE. Полный elapsed с publication/readback сообщён владельцу в финальном ответе; ниже нет заявлений о native PASS.

Обозначения команд: `$Source` — существующий SOURCE004, `$TestRepo` — существующий TEST006, `$Main` — предоставленный MAIN, `$Module = Join-Path $Source 'L2J_Mobius_CT_2.6_HighFive'`, `$Train = Join-Path $TestRepo '.phantom-local/learning002/train'`, `$Task = 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-018-FAST-PARTY-EVIDENCE-GATE'`. `$Delivery` — временные два документа018 вне checkout. Никаких новых worktrees/branches.

## Read-first и bounded audit

Get-Content, rg --files, bounded rg -n, Get-ChildItem использованы только для AGENTS/README/workflow/status,8 inputs018, нужных RESULT/EVIDENCE/COMMANDS017, Runtime/collector, PlayerNativeEvidence, TerminalRewardReceiptV1, build.xml, Training.ps1 и одного settlement slot17. Nested AGENTS High Five не найдены. Архивы1823 не перечитывались.

Git-команды до изменений (разрешены пользователем и AGENTS для baseline/scope):

```powershell
git -C $Source rev-parse --show-toplevel HEAD
git -C $Source symbolic-ref --short HEAD
git -C $Source status --short
git -C $TestRepo rev-parse --show-toplevel HEAD
git -C $TestRepo symbolic-ref --short HEAD
git -C $TestRepo status --short
git -C $Main rev-parse --show-toplevel HEAD
git -C $Main symbolic-ref --short HEAD
git -C $Main status --short
```

SOURCE/TEST первые sandbox-вызовы: dubious ownership; это не clean status. Успешно повторены теми же командами с одноразовым `-c safe.directory=<exact checkout>` перед `-C`; global config не менялся. SOURCE clean, expected048ed764; TEST42d6b675 с10 прежними modified paths и.phantom-local; MAIN7e997636 с169 entries, не изменялся. TEST status сообщил недоступность protected train из sandbox; read-only native inventory затем выполнен с разрешённым escalated exec. Первый Get-CimInstance в sandbox: Access denied; успешная read-only проверка вне sandbox дала Java0. Ошибочные отсутствующие RESULT017 в MAIN не создавались: прочитан правильный SOURCE017.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'"
Get-NetTCPConnection -State Listen | Where-Object LocalPort -In @(18001,19301,33357)
rg -n 'sqlPort|gamePort|loginPort|autonomous009Phase|episodesPerBatch|autonomous009Campaign|Smoke' "$Train/TRAIN_CONFIG.ini"
Get-Content -LiteralPath "$Train/director.json" -Raw | ConvertFrom-Json | Select-Object phase,reason
Get-FileHash -LiteralPath "$Train/TRAIN_CONFIG.ini","$Train/geo203.json","$Train/protected207.json","$Train/game/libs/GameServer.jar"
```

Exit0: STOPPED/OFF, Java0, TEST listeners0. Frozen51/75min/3starts сохранены, не являются разрешением018. Actual JAR2d43df5c249cda247a7b33370007744d68bdad1c6e579acaf80b7f66cd04170b совпал с build.json.jarSha. Config b687881e82bfdb94b739d9c2fd5633f2e0f8f4538b1c7afe4c710582c1ba5a0e; geo manifest a8a4b73bc7b53bed28f545cd889f489492b585b369f8e84988f8db8a91c973d5; protected manifest246ac12cd718e698b18e6ab2b338622b24d505f75ba492eb1cd348bca2e335cd. Полный contents audit203/207/183 NOT_RUN.

Exact sample: `$Train/autonomous/v1/campaigns/autonomous009-learning016-g2-v1/cohorts/autonomous009-learning016-g2-v1-c000001/0c661d4a55ad5e02cff2f335871c08fa45532933bc4d74afd2c5641df6ff19c5`. Прочитан settlement.json; Test-Path actor-evidence017.json=False. Hash settlement/precut см.RESULT. Raw owner nonce не переносится в публичные документы.

## Targeted проверки

```powershell
# cwd=$Source; exit0,37s. Один Ant invocation, no jar.
ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning016-test
# cwd=$Module; exit0,~1s; existing contract, NOT_NATIVE.
java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp '../build/bin;../build/phantom-test/bin;../build/phantom-test/resources;dist/libs/*' org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative party-reward-contract017
```

Первый direct Java invocation из SOURCE с некавыченными -Dstdout.encoding/-Dstderr.encoding дал exit1/ClassNotFoundException `.encoding=UTF-8` из-за PowerShell argument parsing. Следующий с quoted -D и classpath `build/bin;build/phantom-test/bin;build/phantom-test/resources;L2J_Mobius_CT_2.6_HighFive/dist/libs/*` из SOURCE дал exit1/NoSuchFileException в censusContract017:3063: fixture ожидает относительный `tools/phantom-runtime/training/AutonomousTraining.ini`. После чтения строки3063 исправлен только cwd=$Module и соответствующие classpath prefixes; код/тест не менялись. Итог `TRAIN017_PARTY_CONTRACT_GREEN NOT_NATIVE SQL=0 GS=0 rewardsNotFabricated=1`.

## Доставка и проверки scope

В SOURCE ранее не было пакета018. Восемь его input-файлов из MAIN копируются byte-exact;7 записей MANIFEST проверяются SHA256 до и после. В SOURCE добавляются только RESULT.md/COMMANDS.md. Применяется exact allowlist10: ACCEPTANCE.md, GATES_AND_BUDGET.md, HANDOFF.md, MANIFEST.sha256, SOURCE_FACTS.md, START_HERE.md, TASK.md, TESTS.md, RESULT.md, COMMANDS.md внутри `$Task`. Ничего вне этих10 paths не stage/commit.

```powershell
git -c safe.directory=$Source -C $Source diff -- $RuntimePath $CollectorPath
git -c safe.directory=$Source -C $Source diff --check -- $Exact10
git -c safe.directory=$Source -C $Source status --short
git -c safe.directory=$Source -C $Source -c core.autocrlf=false add -- $Exact10
git -c safe.directory=$Source -C $Source diff --cached --name-only
git -c safe.directory=$Source -C $Source diff --cached --check -- $Exact10
git -c safe.directory=$Source -C $Source commit -m 'Record bounded learning018 Party evidence NO_GO'
git -c safe.directory=$Source -C $Source push origin HEAD:refs/heads/feature/phantom-world
git -c safe.directory=$Source -C $Source rev-parse HEAD
git -c safe.directory=$Source -C $Source status --short
```

`$RuntimePath`/`$CollectorPath` — exact два разрешённых test/java/org/l2jmobius/tests/phantoms исходника. SOURCE diff перед отчётом пустой. `git diff --cached --name-only` используется для проверки, что index не содержит чужих путей; commit/push явно разрешены018. Обычный non-force push только feature/phantom-world. Reset/clean/stash/branch/force/rebase не выполняются.

GitHub plugin: search_branches(owner=kpCat,repo_name=L2J,query=feature/phantom-world), fetch_commit(repo_full_name=kpCat/L2J,commit_sha=feature/phantom-world). Первые попытки с неподходящими именами schema-параметров вернули INVALID_ARGUMENT без действий; после исправления branch HEAD048ed764 подтверждён. Публикационный fresh HEAD/parent/exact changed paths/full bodies проверяются GitHub connector; outcome и commit SHA сообщаются финальным ответом без самопереписывания receipt.

Две отдельные проверки всех10 файлов: полный список mojibake-маркеров из AGENTS и regex escaped Cyrillic (`\\u04[0-9A-Fa-f]{2}`, `\\u05[0-9A-Fa-f]{2}`, XML &#x/&#X04/05). Для нулевых совпадений rg exit1 означает no matches. Inline и первый script вызовы завершились ParserError до исполнения: PowerShell трактует smart apostrophe внутри списка маркеров как закрывающую single quote. Исправлены кавычки только во внешнем скрипте. Bounded encoding/manifest/staging код сохранён во внешнем UTF-8 BOM Verify-Delivery018.ps1 и выполнен через `powershell -NoProfile -ExecutionPolicy Bypass -File "$Delivery/Verify-Delivery018.ps1"`. Это static delivery check, без Reflection/runtime/test harness. Также повторно сверяются четыре TEST safety hash и два sample hash, Java0/TEST listeners0. Новых native/SQL процессов не было; cleanup runtime не требовался. Следующий Goal не начинался.
