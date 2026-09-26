# EVIDENCE — accepted handoff facts

Baseline HEAD for LIVE-003C:
`71dfef4e6c2f1e870e78a5566d4a59499d8ad330`

Accepted previous gates:
- progression routes: 15/15 GREEN
- production human locality: GREEN
- live 0D: GREEN (`worldMaterialized=7`, profile 201 `worldPresent=true`)
- runtime 1280: GREEN

LIVE-003B exact result:
- lost ecology inventoryReady edge RED 16/17 -> fixed GREEN 17/17
- code commit: `3f3fa07c5f242f1e2a9423a930e68ae085e36a7b`
- report commit / current HEAD: `71dfef4e6c2f1e870e78a5566d4a59499d8ad330`
- profile creation resumed; first new shell profile 1281
- runtime sample growth: 1282 -> 2656 managed in 44.33 minutes = 1374 new = 30.99/min
- final post-stop PLAY snapshot: 2668 managed / 2666 linked, 2666 unique names/accounts, duplicate counts zero, 2632 committed positions
- two profiles remained in ordinary unfinished creation lifecycle
- heap peak 93.99%, never >90% for three consecutive minute samples
- threads 160-163
- DB connections 13-15 / 151
- fatal markers 0
- LocalPlay stopped, ports closed
- private target remains 5000; budgets unchanged

Measured code-path evidence at current HEAD:
- `PhantomPopulationDecision` handles `CreationOutcome.PROGRESSED` as `RETRY(25ms)`.
- WARM cadence is 1000ms.
- `PhantomPopulationStore.advanceCreation()` durably advances exactly one major state per call on the normal path:
  SHELL -> ACCOUNT_PREPARED -> CHARACTER_PRESENT -> INITIALIZING -> READY.
- `PhantomPopulationTestDoubles.MemoryStore` models the same four-call normal path.
- CreationInFlight stayed at 2 and managed-linked delta stayed at 2 during the measured run.

Do not repeat old live 0D/progression audits. This task begins at the creation-rate blocker.

## 2026-09-26 — RED, GREEN и доставка code SHA

- Исходный HEAD: `71dfef4e6c2f1e870e78a5566d4a59499d8ad330`, ветка `feature/phantom-world`. Несвязанные dirty/untracked файлы не включались в commit.
- Новый DB-free target `ant phantom-population-throughput-test` исполняет зарегистрированный `population.create_character` handler в WARM context с `MemoryStore` и реальным `PhantomPopulationManager`. На baseline до production-изменения: exit 1, `0/1`, `Expected <SUCCESS> but was <RETRY>`, seed `16001601`. Первый `PROGRESSED` прерывал одну WARM delivery до READY.
- После bounded continuation: target exit 0, `3/3`. Проверены четыре durable записи SHELL→READY в одном handler, RETRY и cancellation без дополнительного advance, READY и idempotent READY, INCONSISTENT, NOT_PENDING, hard bound в четыре вызова и no-progress PROGRESSED с REPLAN.
- `ant phantom-population-creation-test phantom-population-reconciliation-test phantom-population-lifecycle-test phantom-population-ecology-goal033-test phantom-historical-background-goal033a-test`: общий exit 0; Goal033 `17/17`, Goal033A `13/13`. Компиляция Ant выполнена зависимостями. Неизменённые deprecation warnings в двух старых тестах остались.
- Первый sandbox запуск RED не дошёл до тестов: JDK получил `AccessDeniedException` на существующем `HikariCP-7.0.2.jar`. Разрешённый запуск дал настоящий RED, затем GREEN.
- `git diff --check` по пяти task-owned code/test/build paths и staged diff: exit 0. Read-only review не обнаружил actionable correctness issues.
- Code commit/push: `a4bb55270d33bfdd0ce96e0367dd509b5c3358b2` → `origin/feature/phantom-world`. Первый запрос push отклонён auto-review из-за неподтверждённого назначения; read-only проверка `AGENTS.md:23,329,346` и `git remote get-url origin` подтвердила `https://github.com/kpCat/L2J`, после чего push прошёл.
- Чистый detached worktree на exact code SHA, один `ant jar`: exit 0. Доставлен только private `GameServer.jar`, SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`. Предыдущий JAR `C9947AC12C181705F9804ADE62E2C740E76FBF38B687EED605CB2D244CEF953D` сохранён в `.phantom-local/backups/LIVE-003C-SCALE-5000-THROUGHPUT/GameServer.previous.jar`.
- Protected parity до/после доставки: `local-play.json` `00D6601BDAB42B8AD35550A8BBEEFA80C7EF0B03E640F44457572D868DFDEFF8`; LoginServer.jar `B289D316C9D35503E34E6F2994EB9F3B810E6F87DD3D4686ACD18E18C6A7058F`; PhantomPlayers.ini `334746ADEA772056FE76AB94EA31CE3A285FCAD3537EFE34369FF891F163D390`; Game Database.ini `15B4C577498BC0F9DA697364C049B35C9E8D3F29B40744E794DD40FCF41A7CE4`; Login Database.ini `E2D59E1C4FD6AE30494E415ECE0EE300CEB95A20FFC4C6C6F697649C46D90BF6`; Login Server.ini `BF84376BF977828B3AF90598A765E3F0D752A7411A6EA92AA2AED3EDCECD0EEE`.

## PLAY baseline и runtime start

- До старта read-only SELECT/SHOW: 2668 managed / 2666 linked, 2666 distinct names/accounts, duplicate names/accounts 0/0, committed background.state 2632. Population states: READY 2666, CHARACTER_PRESENT 2. Direct PLAY DML/DDL отсутствовали.
- `Check-LocalPlay.ps1`: CONFIG PASS, owned Login/Game STOPPED, порты 2106/9014/7777 закрыты. Private target=5000; ActiveTarget=64, MaxMaterialized=128, MaxScheduled=10000, CreationInFlight=2, pulse=100 ms, boundaries=64.
- `Start-LocalPlay.ps1 -Background`: начало окна `2026-09-26T20:05:53.6531610+03:00`; owned Login PID 22380 и Game PID 17964. Ramp deadline `20:50:53.6531610+03:00`.
- Первый sample `20:06:58`: 2668 managed / 2668 linked (два прежних профиля завершились), unique 2668/2668, duplicates 0, heap 3606/4096 MiB (88.04%), threads 166, DB connections 13, fatal 0. Следующий sample `20:07:55`: 2727/2725, unique 2725/2725, duplicates 0, heap 87.99%, threads 164, connections 14, fatal 0.
- Read-only подробные samples и snapshots: `.phantom-local/logs/LIVE-003C-SCALE-5000-THROUGHPUT/`. Компактный ряд будет записан в `LIVE003C_RUNTIME_5000.tsv`.

## PLAY 5000 ramp gate

- Target достигнут `2026-09-26T20:17:23.5151358+03:00`, до deadline `20:50:53.6531610+03:00`: 5000 managed / 5000 linked, distinct names/accounts 5000/5000, duplicate names/accounts 0/0. Read-only population states: READY 5000. С момента старта `20:05:53.6531610` прошло 11.4977 минуты; 2332 новых managed = 202.82 профиля/мин по полному окну, включая startup.
- Target sample `20:17:21`: heap 3680/4096 MiB (89.84%), Game threads 162, DB connections 13/151, fatal markers 0. За ramp максимум heap 93.85%; подряд выше 90% был не более одного sample. Threads 160–166, DB connections 13–15; identity duplicates 0 на каждом sample.
- Committed `background.state` и `background.catchup` выросли с 2632 на baseline до 4645 к target; `population.ecology` охватила 5000 профилей. Soak стартовал только после linked=5000: `20:17:23.5151358+03:00`, deadline `20:32:23.5151358+03:00`.

## 15-минутный soak и exact owned stop

- Soak завершён `2026-09-26T20:32:26.4548589+03:00`, то есть более чем через 15 минут после target gate. Все soak samples сохраняли 5000 managed / 5000 linked, distinct names/accounts 5000/5000, duplicates 0, fatal 0. Общий ряд ramp+soak: 30 samples, heap 80.71–93.85% от 4096 MiB, максимум два последовательных samples >90%; Game threads 160–166, DB connections 13–15 из 151.
- Финальный soak sample `20:32:24`: heap 3314/4096 MiB (80.91%), threads 160, connections 13, fatal 0. После soak SELECT: 5000/5000, READY 5000, unique names/accounts 5000/5000, duplicates 0. `background.state` и `background.catchup` — по 4978, `population.ecology` — 5000; оставшиеся 22 профиля не представлены как committed background coverage.
- Read-only запрос по `updated_at >= 2026-09-26 20:17:23` после soak: обновлялись 3002 `background.state`, 3058 `background.catchup` и 2709 `population.ecology` rows; последние timestamps `20:32:48`. Это подтверждает продолжение фоновой и ecology работы в окне soak.
- Канонический `Stop-LocalPlay.ps1` остановил только Game PID 17964 и Login PID 22380. `Check-LocalPlay.ps1`: STOPPED, staleRecord=False, ports 2106/9014/7777 closed, CONFIG PASS. Post-stop read-only PLAY SELECT: 5000 managed / 5000 linked, unique names/accounts 5000/5000, duplicates 0, background committed positions 4978. Все профили сохранены; target остался 5000.
- После stop hashes manifest, LoginServer.jar, PhantomPlayers.ini, Game/Login Database.ini и Login Server.ini совпали с baseline. GameServer.jar сохранил доставленный hash `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`. Private budgets: ActiveTarget 64, materialized 128, MaxScheduled 10000, CreationInFlight 2, pulse 100 ms, boundaries 64 — без изменений.
- PLAY использовалась только через штатные start/stop tooling и read-only SELECT/SHOW; прямых PLAY DML/DDL, reset/reseed/delete не было. Runtime 10000 и LIVE-004/005 не начинались. Блокера для LIVE-003C нет.

## Scope, text checks и Git process

- Изменены production только `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java`; test/build — `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java`, `PhantomPopulationTestDoubles.java`, `PhantomTestLauncher.java`, `build.xml`; task outputs — `EVIDENCE.md`, `STATE.md`, `LIVE-003C-SCALE-5000-THROUGHPUT.md`, `LIVE003C_RUNTIME_5000.tsv`. Несвязанные изменения не staged/committed.
- README.md в корне модуля, `docs/`, `docs/phantoms/` и текущем task-каталоге отсутствуют; локальные code-map/pattern-файлы в затронутых population/test/task путях не найдены. Повторный поиск не выполнялся.
- mojibake-маркеры в изменённых файлах проверены: 0 совпадений по полному списку из инструкции.
- escaped Cyrillic в изменённых файлах проверены отдельно: 0 совпадений по всем шести заданным regex-паттернам.
- Git-команды использовались только для требуемых branch/HEAD/scope/diff checks, exact-path staging, commit/push и проверки detached checkout. Управляемый detached worktree создан и архивирован средствами Codex app; shell `git worktree`, reset/clean/stash/rebase/force-push не применялись.

Точные Git-команды (повторные read-only checks с теми же аргументами приведены один раз):

```text
git rev-parse HEAD
git branch --show-current
git status --short --branch
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
git diff --check -- L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationTestDoubles.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff --stat -- L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationTestDoubles.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java
git diff -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationTestDoubles.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java L2J_Mobius_CT_2.6_HighFive/build.xml
git add -- L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationTestDoubles.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff --cached --check
git diff --cached --stat
git diff --cached --name-only
git commit -m "phantom(live-003c): coalesce durable creation stages"
git remote get-url origin
git push origin feature/phantom-world
git symbolic-ref --short HEAD
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003C-SCALE-5000-THROUGHPUT/EVIDENCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003C-SCALE-5000-THROUGHPUT/STATE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003C-SCALE-5000-THROUGHPUT/LIVE-003C-SCALE-5000-THROUGHPUT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003C-SCALE-5000-THROUGHPUT/LIVE003C_RUNTIME_5000.tsv
git commit -m "phantom(live-003c): record 5000 throughput gate"
```
