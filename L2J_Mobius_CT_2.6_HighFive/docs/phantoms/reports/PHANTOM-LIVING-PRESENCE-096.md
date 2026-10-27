# PHANTOM-LIVING-PRESENCE-096 — PARTIAL

27.10.2026. **PARTIAL / NATIVE_WORLD_NOT_PROVEN / OWNER_CLIENT_QA_DEFERRED**.
Проверенный код опубликован по разрешённому fallback TASK096; SUCCESS и видимое население обычного мира не заявлены.
Baseline и повторно fetched origin: `fc69c712ef5850e635cc90272e72e1d586c94f64`, `feature/phantom-world`.
Commit — публикационный commit, содержащий этот отчёт; его полный SHA и push receipt записаны после commit в `.phantom-local/task096/publication.json` и финальном ответе. История owner-коммитов не переписывалась.

## Причина и изменение

Population выбирал ACTIVE по региональной квоте/суточному cursor, без физического demand. Однако `human.local` самостоятельно повышал WARM до NEARBY_PERCEPTIBLE: квота сама по себе не доказывала причину пустого города, а логический ACTIVE не доказывал World.
Теперь existing Population выделяет среди **eligible scheduled ACTIVE** до восьми demand-приоритетов в пределах прежнего cap; обычный остаток сохраняет региональное распределение. При нехватке региональной квоты приоритет временно занимает обычный слот. Календарь, eligibility и число permits не обходятся.
Стабильное окно 30s не зависит от небольшого изменения расстояния; после него ожидавшие eligible profiles получают очередь по last admission, demand age и ID. CouldKnow сохраняет преимущество перед prewarm. Положительный тест — две логические regional groups, не два native города.
Locality отбрасывает off-calendar/ecology-fenced profiles **до** ограничения query1024. Переполнение самого query остаётся UNKNOWN: recovery/полная fairness за пределом1024 в этой задаче не доказаны.
Reconcile требует текущего admission для нового human-request; существующий accepted native journey сохраняет прежний owner. Population demand обновляется и при ecology OFF; Ecology использует существующие preparation/focus/cursor.
Недопущенный requester не выбирает soft victim и повторно отвергается перед reclaim, если admission изменился. Это исправление получено независимым review и отдельным RED→GREEN.
Recent-human60s теперь исключает soft victim. Для всех cap modes, включая burst0, установлен existing native idle-seal callback: свежий census, overflow, TTL, текущая геометрия и recent observation CLEAR. Sentinel257 сохраняет факт human overflow; UNKNOWN/overflow не дают разрешение cleanup.
Не изменены MaterializationService/MaterializedPlayer, epoch fencing, World identity, native action ownership, Party/store/trade/combat/move/cast guards и normal drain/STORE. Нет manual World.remove, нового engine, DB writer или per-profile timer.

## Приёмка A–F

| Gate | Статус / фактическое доказательство |
|---|---|
| A | PARTIAL, DB_FREE_PRODUCTION: 257→1 census recovery; zero-resident fixture. Обычные eligible residents Giran/Gludin, committed positions/revisions — NOT_MEASURED. NO_ELIGIBLE_RESIDENT подтверждён только для пустой тестовой topology. |
| B | PARTIAL, DB_FREE_PRODUCTION: cap1, minority region, два demand ID из разных logical regions, стабильность1s, очередь31s, no pulse writes. Native time-to-World и two-town присутствие NOT_PROVEN. |
| C | PARTIAL: retention/grace и admission-at-selection/boundary focused PASS; scheduler36/36. Реальные known-list/Party/STORE races в новой native сцене NOT_RUN; существующие native guards сохранены, новые guarantees не приписаны старому065. |
| D | PASS для проверенной совместимости: calendar0653/3, population-schedule3/3, v1/v2/v3 bytes unchanged, OwnerConfig Verify0writes. Это регрессия существующего065, не повторная реализация календаря. |
| E | BLOCKED: один bounded preflight existing native065 entrypoint, private cwd, без DB authority. Guard отказал до ConfigLoader/DB/native Player: `Headless integration JVM must run from dist/game`, exit1. TEST096 lane/ownership/config не подготовлены; historical lanes и БД не использованы. Native World сцена NOT_RUN. |
| F | PARTIAL: final whole-source Ant compile/tests PASS, read-only config/hash checks и independent review. STORE/persistence/sameDB restart, scale500/1000/5000, CPU/heap/backlog measurement NOT_RUN. |

Нельзя объявить обычный город пустым из-за отсутствия residents: обычный DB inventory не выполнялся. `READY/ACTIVE/admitted/materialized/World/visible/known-list` не взаимозаменяемы. Реальные значения всех этих счётчиков, epoch и position origin обычного мира — NOT_MEASURED. Focused model: READY10, eligible ACTIVE10, admitted1, physical demand2; это не native population.
Soft model: recent hold retained до60s; admitted requester reaches selection; lost admission gives DEFERRED и release0. Actual attempted/succeeded native evictions, hard-hold распределение, min/max World wait — NOT_MEASURED. Human overflow257 и recovery1 проверены; profile-overflow recovery не доказан.

## Проверки и commands

Логи: `.phantom-local/logs/PHANTOM-LIVING-PRESENCE-096/`; scratch ledger/build wrapper: `.phantom-local/task096/`.
JDK Temurin25.0.4.1; Ant1.10.17. Source build: **2372 production +416 test files**, exit0, пять прежних warnings.
Команда: `ant -f .phantom-local/task096/verify.xml -Dbuild=<absolute-own-ant-output> compile-tests`.
Wrapper импортирует штатный build.xml; только mutating owner-config-apply заменён no-op. Штатный OwnerProfileVerifier:38rules PASS, READ_ONLY=true. Source INI/OwnerConfig Apply не запускался.
`powershell -NoProfile -ExecutionPolicy Bypass -File L2J_Mobius_CT_2.6_HighFive/tools/OwnerConfig.ps1 -Action Verify`: exit0,68files/287overrides/0conflicts/0writes.
Final suites запущены `java -Dphantom.module.root=<module> -Dphantom.test.reports=<own-report-dir> -cp <own-test-bin;resources;own-production-bin;dist/libs/*> org.l2jmobius.tests.phantoms.PhantomTestLauncher <mode> <seed>`:

| Mode / seed | Exit | Результат |
|---|---:|---|
| living-presence096 /96009601 | 0 | 4/4 |
| activity-scheduler /20260725001 | 0 | 36/36 |
| living-presence-calendar065 /65006500 | 0 | 3/3 |
| population-schedule /16001601 | 0 | 3/3 |
| population-ecology-goal033 /33003300 | 1 | 35/36; единственный FAIL — старое требование shipped system OFF, protected owner config включает систему. Остальные35 поведения PASS. |
| PhantomHumanLocalityTraceSuite `<module> <own-reports>` /17001701 | 0 | 3/3, private cwd |

Final aggregate:84PASS/1FAIL, **не all-green**. Native preflight отдельно exit1 до World/DB; shutdown сообщил null uninitialized thread pool, JVM штатно завершилась. Повтор native не запускался.
Промежуточно: RED0960/3→GREEN3/3; review regression RED3/4→GREEN4/4. Scheduler сначала33/36 из-за старых recent-reclaim ожиданий, затем36/36 после изменения controlled clock/ожиданий под096. Первый javac launcher-only не учитывал тестовые файлы с нестандартным package/path; исправлено полным test source inventory, без изменения проекта. Это compile failure, не behavioral RED.
Final whole Ant compile:1; ant verify:0; jar:0; native campaigns:0, preflight:1. Никакого широкого повторного soak или Party PvE.

## Independent review, scope и сохранность

Один независимый read-only reviewer; найденные ecology-OFF wiring и unadmitted-reclaim замечания исправлены. Final production recheck: открытых P1/P2 нет; разрешена только PARTIAL-публикация. Его review не доказывает native World; scheduler assertions проверены основным исполнителем.
Exact allowlist — **9 файлов**; девятый existing scheduler suite необходим для обновления трёх ожиданий recent grace, bounded exception зафиксирован до изменения:

1. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — wiring/filter/fresh observation.
2. `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java` — bounded lawful admission/fairness.
3. `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java` — sentinel overflow.
4. `java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java` — full recent grace.
5. `java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java` — admitted load/reclaim.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomLivingPresence096Suite.java` —4 focused cases.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — один mode.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java` — grace assertions/clocks.
9. `docs/phantoms/reports/PHANTOM-LIVING-PRESENCE-096.md` — этот отчёт.

Пути списка относительно High Five. Task package/old untracked WIP не staged. Ни Studio, Semantic Pack, config, authored topology, экономика/Party, DB schema, release или зависимости не изменены.
Before/after SHA256: **712 protected files,0changes**, включая source config, Semantic Pack/authoring и Studio source (bin/obj исключены).
Population v1:`23B12FC523DE83D1BDEA54A75677823B2E8129978792A9C9FEC684ACCFAA4748`; v2:`D555AB8DDA0783D0736D3AADB0BD3D1B76481C73F0A34656CDF5C70FB88FBBC6`; v3:`884C2959726251BC1E6E0AD2A6692721A23199B6F6636C46D64E03DA55754CE0`.
DB/preflight: schema не создавалась, DatabaseFactory не инициализирована; DB/SQL/credentials commands отсутствуют. Все запущенные JVM завершились с записанными exit codes. System-wide `Get-CimInstance Win32_Process` отказал в доступе; общий JVM/connection postflight не объявлен PASS. Собственные runtime sessions/leases не создавались. Защищённые поставки/сервисы/клиент не запускались.

Mojibake-маркеры в изменённых файлах проверены отдельно: PASS.
Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены отдельно: PASS.
Exact-file whitespace guard PASS: `git -c core.whitespace=cr-at-eol diff --check -- <9 exact paths>`. Без per-command CRLF setting Git ошибочно считает CR в двух существующих mixed-ending файлах trailing whitespace; исходные line endings сохранены. Первый inline PowerShell encoding scan имел parser error на smart quote; отдельный UTF-8 Python scanner успешно проверил оба полных набора. Publication receipt фиксирует scope/exit codes commit/push/remote comparison.

Read-first: root AGENTS/README, bounded CURRENT_STATUS/WORKFLOW/EFFICIENCY, task package и initial11 READ_SET; дополнительные PresenceRegistry/TopologyService/Reconcile/MaterializedPlayer/PopulationTestDoubles и bounded Scheduler для проверки quota-гипотезы. Chosen test/build/OwnerConfig/native guard entrypoints прочитаны точечно; других хроник нет. Вложенных AGENTS/code-map/pattern файлов не найдено. Initial search budget превышен при проверке scheduler arbitration/native guards; расширение ограничено указанными seams, без обхода всего проекта.
Git использован по TASK096/SAFETY и root scope permission: `symbolic-ref --short HEAD`, `rev-parse HEAD [origin/feature/phantom-world]`, `status --short [--untracked-files=no]`, `worktree list --porcelain`, `for-each-ref --format=... refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`, `fetch origin feature/phantom-world`, `diff [--numstat|--check] -- <exact paths>`. OwnerConfig Verify не выполнял Git mutation. Запись: `add -- <9 exact paths>`, `commit -m <096 message>`, `push origin HEAD:feature/phantom-world`; никаких force/rebase/reset/clean/stash/branch.
Usage на pre-delivery snapshot:275353tokens/966s, без token STOP; final usage — goal tool/final receipt. Следующая задача не начата. **STOP после публикации.**
