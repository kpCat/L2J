# PHANTOM-WORLD-COHORT-RECOVERY-108R3

Статус: **FULL108R3=BLOCKED_G0_UNATTRIBUTED_CHARACTER_DRIFT**. HIST108R3=HISTORICAL_INSUFFICIENT; harness PATCH=DB_FREE_VERIFIED; WORLD_A/STOP_A/RESTART_B/STOP_B=NOT_RUN. Native acceptance отсутствует. TASK109=NOT_STARTED.

## Authority и G0

- Fresh local/tracking/live `feature/phantom-world` совпали: `75f4f957b6d16d0609cf4b7e888da607ee2a6ea9`; known ancestor подтверждён. Чужие untracked файлы сохранены.
- `Task108R3Run.ps1 -Action Audit`: exit1, `OWN108R3_G0_DRIFT_REQUIRES_AUDIT_NO_NATIVE`. До отказа проверены original OWN102 installation/source/DB hash/SID, полный SHOW GRANTS visibility, OS JVM0, SQL0, TCP0, foreign0 и два стабильных SELECT-only canonical SQL preimage (9 named frames проверяемых таблиц, не все таблицы схемы). SQL mutation0.
- Снимки 29.10.2026 14:33:31Z/14:33:32Z byte-identical. Retained108R2 cut был10:09:42Z; approved force12:05:26Z и clearance12:06:21Z остаются **FORCED_UNCLEAN**. Post-force canonical SQL preimage в историческом force receipt отсутствует. Closing read-only cut14:50:56Z: JVM0/SQL0/TCP0/foreign0; G0 всё ещё BLOCKED, baseline не принят.
- Штатный DB-free `Task105Census diff`: exit0; original5000 READY IDs/init EXACT, profiles5001/characters5256,250 merchant roles/quotas/births, accounts и recipebook150 сохранены. Inventory54731, components34910, offliners250/offlineItems2368 без изменений.
- Изменены только252 character rows: `onlinetime`2 (+7299s/+6505s) и `vitality_points`250 (+505…585, кратно5). EXP/SPdelta0, negativeRows0. Не откатывались и не объявлялись принятым baseline.
- Existing market checker: exit0, **82→82**, fundingDelta0/stockDelta0, stock254851704, funding3150000000; прежние63 funding+19 stock receipts и physical inventory сохранены. Это market PASS, но не полный G0 PASS.
- Bounded `Player.java` store/checkpoint/storeNative/storeCharBase и vitality/autosave paths показывают совместимость изменений со штатным сохранением. Они не доказывают конкретного writer в интервале retained→force→fresh cut. Attribution=UNKNOWN; независимый reviewer подтвердил G0 BLOCKED.
- Не создавались `audit.complete`, shadow108R3, seal, launch/clean/B markers. Prepare/Ant/OwnerConfig stage/nested census/native A/B остановлены на G0. Source OwnerConfig287 True/5000/128/128, Player.ini20, merchant policy и semantic не менялись; protected hashes проверяются финальным verifier.

## Historical causal matrix

| Измерение | 108R | 108R2 | Вывод |
|---|---|---|---|
| Native start UTC |08:43:20Z|10:01:23Z|Одна дата; local JVM log UTC+02 по корреляции, explicit ZoneId не записан|
| World loop |16 достигнут примерно за71s|9 за примерно303s; late10 после second cut|R2 соответствует300s ceiling; не доказан120s stall|
| Stable30s |16/16,16 exact tuples|9/9,9 exact tuples|Player reference/epoch/token churn0 внутри каждой сохранённой пары|
| Controlled humans |2 list entries|2 list entries|addObservers byte-identical; реальные resulting coordinates/instances/visibility не записаны|
| Desired |2500 final|1886 peak→1712 final|Оба≫16; calendar aggregate отличается, причина World shortfall не следует|
| Eligible/admitted final |0/0|0/0|Instantaneous snapshot не доказывает POLICY_LIMITED|
| Local signaled |480|821|Не заменяет physical demand/preparation readiness|
| Prep/retry/native failure/calendar boundary |Не записаны|Не записаны|HISTORICAL_INSUFFICIENT; production correction не обоснована|

- Signed causal audit `/root/review108r3`: причина16→9=UNKNOWN. Не изменялись время/TZ/calendar/population policy, observer placement, spawn/cap или production admission. Cap16 остаётся потолком; World16 не ослаблен.
- Подтверждён отдельный harness defect: strict stable-cohort assertion был до logout observer. В108R2 shortfall пропустил регистрацию; finally получил drained=true, затем proveStop105 отказал из-за отсутствующего EARNED, до закрытия pools/DB.

## Изменения и writers

- Exact deliverables: `PhantomPopulation102Native.java`, `PhantomTask102Lane.java`, новый `PhantomWorldCohort108R3Checks.java`, новый `tools/phantom-runtime/Task108R3Run.ps1`, этот отчёт. Production edits0.
- Режим108R3 использует прежний current-owned `PlayerNativeWork.run` listener; World shortfall не бросает assertion до witness attempt. Отказ witness сохраняет private type/reason, затем canonical finally по-прежнему требует настоящий EARNED1/outstanding0 и lease/SQL proof.
- Раздельные `world6.pass|fail`, `stop6.natural`, `postflight6.pass.json`, `exit6.clean`; shortfall пишет diagnostic phase и wrapper возвращает WORLD_FAIL после доказанного STOP, без clean/B. Реальные108R3 receipts не созданы: runtime NOT_RUN.
- B pre-JDBC требует full16 stable A, natural STOP, прежний sameJAR/checkpoint contract и независимую hash chain. Namespace108R3 отклоняет смешанные103–108R2 markers.
- Read-only diagnostics: start/plateau/final,≤64 selected candidates/cut; actual World identities/leases/native headless, observer coordinates/instances/visibility, JVM/population TZ, admission/calendar boundary, ecology prep/retry/failure. Existing10s sampling/120s no-progress/300s ceiling сохранены; новых threads/timers нет. Телеметрия скомпилирована, native не исполнялась.
- Writers/locks переиспользованы: finite grant CAS, native inventory/Player store, ordinary reward/background/autosave, lifecycle/logout scope, merchant service/list locks, OfflineTraderTable, canonical ThreadPool/Database close. Новый writer/DDL/manual grant/refill не добавлен; Player monitor не объявляется общей atomic boundary.

## Проверки и ограничения

- JDK25 paths фактически обнаружены: Eclipse Adoptium25.0.4.101, Ant1.10.17. Ant executions0, standalone jar0, generic verify/DB suites0; source build/dist JAR не перезаписывались.
- Focused RED: javac нового checker exit0; legacy ordering model run exit1 `shortfall skipped genuine witness attempt` через настоящий finishBoundary105/PhantomLease105Checks.finish. Это DB-free модель порядка, не повторный native108R2.
- GREEN: affected `javac -encoding UTF-8 -cp <unchanged108R2CP> -d <newPrivateScratch> <Checker,Native,Lane>` и `java -cp <scratch;108R2CP> org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks`: exit0. Stable16,9/9+late10, replaced Player/epoch/token, witness refusal, resource ordering, namespace/census negatives.
- Existing focused `PhantomStop108Checks`, `PhantomStop108RChecks`, `PhantomLease105Checks`, `PhantomMarketDelta108R2Checks guards`: exit0 каждый. `PhantomShutdown103Checks.java` по точному task path отсутствует; этот target NOT_RUN, имя не подменялось. Lock104/native market suites NOT_RUN после G0 blocker.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <module>/tools/phantom-runtime/Task108R3Run.ps1 -Action Historical`: exit0. Старый exact market checker ожидаемо RED exit1; production decoder/finite ledger checker на реальных71→78 и78→82: exit0, negatives PASS, funding replay0, JDBC0/native0. Исторические файлы не изменялись.
- Новые stage `compile-tests jar`, separate javac/javap104+105, nested10-slice SQL snapshots, Apply→Verify→Apply0, seal и все native acceptance gates: **NOT_RUN**, запрещены непринятым G0. Старый classpath использован только для offline compile/checks; новая поставка им не подменена.
- Independent source review `/root/review108r3`: P1=0/P2=0 после двух corrections: actual start cohort вместо пустого snapshot и durable witness-refusal reason. Final evidence review и final static outputs сохраняются private; runtime acceptance не выдаётся.
- Mojibake-маркеры в изменённых файлах проверены отдельным финальным scan.
- Escaped Cyrillic в изменённых файлах проверены отдельным финальным scan всех6 patterns.
- Final static verifier дважды проверяет byte-identical5 deliverables, UTF-8/PowerShell AST, exact tracked scope/diff check, protected source/OwnerConfig/Geo203, historical seal/JAR pins и отсутствие нового stage/native markers. Полные commands/exits/SHA и review receipts находятся в `.phantom-local/logs/PHANTOM-WORLD-COHORT-RECOVERY-108R3/` через private index.
- Два промежуточных private static attempts exit1: PowerShell трактовал smart quote в literal marker как delimiter; затем Windows PowerShell5 не перечислил JSON array в pipeline. Исправлен только private verifier; следующий pass exit0,184 protected/203 Geo EXACT. Финальная пара выполняется после фиксации report bytes.

## Процесс и delivery

- Initial READ_SET12 прочитан bounded; все TASK companions, root/module instructions, README/current status/workflow прочитаны. Ancestor AGENTS выше Git root и вложенные docs/task AGENTS не найдены; повторно не искались. Отдельные code-map/pattern files в bounded inventory не найдены.
- Additional causal reads: MarketDelta108R2Checks, Merchant107Native, MaterializationService, Task105Census. Обоснованное расширение: Task102Authority/Task102.ps1/build.xml — exact stage/build contract; Stop108/108RChecks и Lease105/Lock104 main ranges — focused API; reviewer Player.java — G0 save writer attribution. Strict additional≤5 compliance не заявляется.
- Repository searches до patch4, всего6; reviewer3. Full reads source>1000 lines0; other chronicle reads0. Первый inventory/несколько batch outputs обрезаны; необходимые symbols затем прочитаны bounded. Native retry0/force0, G0 retry0.
- Goal telemetry на1054s:308544 tokens; suffix250k ориентир превышен обязательным causal/authority/read-first аудитом, private access/network fallback и независимым review. Explicit token budget не задавался; final usage/elapsed фиксируются private.
- Git использован по TASK17/DELIVERY: `rev-parse --show-toplevel`, `symbolic-ref --short HEAD`, `rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `status --short`, `merge-base --is-ancestor 75f4f957b6d16d0609cf4b7e888da607ee2a6ea9 HEAD`, `ls-remote origin refs/heads/feature/phantom-world`; exact Native/Lane `diff --numstat --` и `diff --`.
- До публикации: `git -c core.whitespace=cr-at-eol diff --check -- <exact5>`; только `git add -- <exact5>`, cached diff/check/name-only, `git commit -m "phantom(task-108R3): separate World failure from diagnostic stop"`, обычный `git push origin HEAD:feature/phantom-world`. Exact5 — пять deliverables выше, не wildcard/add-all. Полные аргументы и результаты в private publication receipt; SHA/push в final без самоссылочного переписывания report.
- Для native продолжения требуется отдельное разрешение на принятие конкретного изменённого baseline либо достаточное доказательство writer; текущий TASK не обходит G0 и не запускает ещё одну кампанию. Никаких rollback/reseed, исправления sealed108R/108R2 или переноса старого force approval.

**STOP после delivery. FULL108R3 не достигнут; A/B=NOT_RUN; TASK109=NOT_STARTED.**
