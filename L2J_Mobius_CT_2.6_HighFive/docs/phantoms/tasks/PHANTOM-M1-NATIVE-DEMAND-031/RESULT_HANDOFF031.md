# RESULT/HANDOFF031 — серверная приёмка не достигнута

Финальный цикл 10.10.2026, предел 13:31 Europe/Chisinau (10:31Z). RESULT=FAILED_ACCEPTANCE / PARTIAL_VERIFIED. M1=OPEN; REAL_FINAL=NOT_RUN; WAITING_REAL_FINAL не выставлен. STOP_AUTHORITY=TASK031_CONTRACT. Следующая задача не создана. Продления пользователя разрешили связанные production-исправления, дополнительные запуски и 14 часов; остальные ограничения сохранены.

## Кандидат, изоляция и scope

Base `e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0`; runtime source `7e765ca426e089e5cd9837bde84d749236657420`; Java tree `df3732e6a5c2849f1ce83166a8735e2a5b2ad07e`. GameServer.jar SHA256 `98C1A4D9C7379C8C0B2F7A04C886513F6461A2630D4498E6A197D2F7E262D5B7`. Итоговый report commit — commit, содержащий этот документ; точный HEAD/remote/reportSHA сохраняется после push в локальном `evidence/FINAL031_GIT_VERIFICATION.json`. Документ и task-only PS исправление не меняют замороженный Java/Jar.

Только own worktree `C:\Users\ZBook\.codex\worktrees\m1-native-demand-031\L2J_Mobius`, БД `l2jmobiush5_localplay_contract031a/b/t`. Main, PLAY, kpCat и чужие worktree не изменялись. Нет SQL-heal, фиктивного REAL_LOGIN, подмены наград или ослабления ownership/PNC/materialization/SQL guards. Субагенты не использовались. До правок прочитаны DEBUG_PLAYBOOK, GOAL/пакет031, HighFive AGENTS/master/workflow, README и связанные native/ecology/background источники; аналоги Prepare030/Control030/Observe030, штатные native fixture и typed shutdown переиспользованы. Отсутствующие артефакты не заменялись выдуманными API.

Immutable preboot export SHA256 (проверены до последнего runtime): a `1400D245B1950D65D6D221470298B581BC007D022028D72E711FE730DCF8BD7C`; b `455087312CF1014B8C6643B434162CB17F3A6B9AFAA5EBB3FCC43B3913DCBCB9`; t `D709DD9FA2DCB590DF8035938DECC6B894B986ED2A17E5C4729EA5F342258675`.

Итог: 14 Jar builds, 20 полных production compiles, 36 own GameServer starts, applied HotSwap=0. Диагностические/тестовые компиляции отдельно от production. Накопленный bounded exception: 14 production Java и 13 test Java paths относительно base, одна связанная причинная цепь native M1; перечень проверен exact diff. C#/стек/новый слой архитектуры не менялись. История отдельных RED→GREEN и source identity в CHECKPOINT_C*.md / PROGRESS031; ранние показатели не перенесены на новый Jar.

## Текущий продуктовый результат

C26_FROZEN_CONTINUITY38: own33 Game PID36300, startTicks639272208170501345, Login14000; 380.8903016s, 372 samples, maxgap1.0465063s, 3 команды, mailbox telemetry=0. Все естественно enrolled ID/epochs сохранены; primaries3/43 выбраны заранее. CONTINUITY_V2 **6/8, FAIL**. Native evidence firstUnprovenReason=NONE у всей группы; доказанный native фарм не равен выполнению temporal acceptance.

| ID | original native epoch | cycles | EXP/SP | continuity |
|---|---:|---:|---:|---|
|3|352625083588300|12|976/105|PASS|
|43|352625110465400|2|174/20|FAIL: same-segment minimum, tail120, useful debt|
|95|352625134999000|41|1423/150|PASS|
|117|352625168495000|18|1438/160|FAIL: useful debt|
|121|352625188621100|18|1262/137|PASS|
|155|352625209855900|18|1526/170|PASS|
|195|352625228012500|25|2106/235|PASS|
|204|352625245070200|21|1636/179|PASS|

Текущий frozen matrix18 routes **93/93 PASS**, lifecycle-completion027 acceptance **11/11 PASS**, resourceEpisodes031 **1/1**, releaseOwner031 **2/2**, orphanRest031 **1/1**. Evidence: C26_FROZEN_MATRIX-matrix.tsv, C26_FROZEN_LIFECYCLE.log, C26_RESOURCE_EPISODES.log, C26_RELEASE_HANDOFF_GREEN.log и C26 focused RED/GREEN. Эти результаты не закрывают runtime continuity/away/crash gates.

## Первые guards и проверенные исправления

Task-only setup исправлен отдельно от production: READY/current calendar/physical native demand не требует PNC background FARM. Старый смешанный predicate защищён focused RED/GREEN11/11. Inherited invalid canonical state и cached aggregate ошибки сохранены как отдельные наблюдения, а не объявлены current exception.

Последний доказанный production-дефект: C25 actor238, epoch351030531545300, own32 PID29560, non-suspending JDI 09:06:58.363606600Z. Реальный Player sitting=true, MP180/HP208, regen task=null; policy _recovering=false/resource.not_required. Старый PhantomVisibleAutoPlay.java:287 возвращал false при !recovering && MP>=cost; AutoPlayTaskManager.java:125 пропускал сидящего. Минимальная C26 правка переводит фактически сидящего affordable actor в существующий stock standUp branch под текущим session/action lease. Native deadline и 45s resource /90s debt /120s phase не сброшены. Focus orphanRest031: old shipped Jar RED1/0/1 → ordinary frozen Jar GREEN1/1, same epoch, EXP/SP не создаются. EOL исправление отдельным normal commit; повторного semantic rebuild после него не было.

**Текущая нерешённая причина actor43:** non-suspending/no-invocation JDI 09:31:52.633658700Z, own33 PID36300, epoch352625110465400: sitting=false, жив, MP104/HP302, owner==serviceScope ACTIVE/open, liveOwner/participating/materializationDemand/schedulingPermission=true. Current result background.visible.replan_required; AutoPlay session уже отсутствует. Его producer — `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java:243` (await вариант:258), FALSE `_visibleSuitable`. Binding вызывает `PhantomHistoricalBackgroundService.java:1107`; возможные первые FALSE:1112 same-epoch cooldown,1113 protocol/route/target exclusions,1114 stored-goal equality. **Какое из них сработало, не снято; это UNKNOWN**, не доказанный новый knowledge mismatch. Позже current reason travel.journey_deadline, отдельный более поздний факт. Cached global catchup.recovery.materialize_capacity_reached не является текущим exception43. Stack trace/SQLState для логического FALSE не выдуман. Причина полезного debt117 также OPEN. Новый широкий rewrite/обход guards не выполнен.

C27 own36 PID26628/startTicks639272224786237169/Login30792: runtime source/Jar те же. R3 natural enrolled3/43/269/295/329/330/929/955, все сохранены; DEPART43.372s и первый настоящий native MOVE/arrival произошли. Затем task guard `Accept031.ps1:97` (до правки) взял прежний 1Hz кадр вместо actual 0.1s native arrival: следующий шаг старый кадр371.2479 >300.01, фактическая позиция224 <=300.01. Записанный counterexample с immutable arrival hash в C27_STALE_POSE_RECORDED_COUNTEREXAMPLE.json; runtimeSceneGreenClaimed=false.

Task-only ReadArrivalFrame031 переиспользует существующий exact run/session/object/dead/proofFailure guard для WaitArrival и Walk. Пороги300.01/height200, arrival32/z48/12s и budgets320/480/525 не ослаблены. PS AST проверен. **Runtime GREEN этой правки НЕ получен:** R4 остановлен до enrollment на initial known-envelope guard:196, R5 до Synthetic на READY calendar:118 (profile5 schedule_not_active/coalesced). R2 старый prefix отвергнут точным source/Jar guard:200; новый prefix прошёл stock dry proof. Ошибки parameter binding/старый Start-wrapper относятся к orchestration/NOT_RUN, не native дефектам. Original C27 root содержит immutable отказ дублирующего pending-parent запуска, Synthetic у завершённых попыток STOPPED. Full away →75s actual background with progress →return →further native farm **OPEN/NOT_PASSED**, на current Jar не доказан.

## Persistence, два restart и crash gates

C26 endpoint: actual ORIGINAL-epoch native FINALIZED receipts всей группы, nativeOwnerSealed, no forced flush, pendingOwnedStores0/retained0; exact canonical fields/inventory/skills strict **8/8 PASS**. C26_ALL8_NATIVE_FINALIZED_PERSISTENCE_VALIDATED.json pass=true/errors=[]. Adapter024 заменяет только contract/owner и ожидаемый native-sealed kind на реально captured native-finalized kind; actual stage FINALIZED проверен отдельно, equality/versions/health не ослаблены.

SameDB34 PID23252/startTicks639272220936538119: настоящий startup boundary PhantomSystem.java:808, managerNEW/entries0, только event thread, остальные потоки выполняются; quiet external SQL, hard8s resume. C26_RESTART34_ALL8_PROOF.json **8/8 PASS** относительно original native33. SameDB35 PID15884/startTicks639272222897552751: actual quiet export0.984s/normal resume, strict comparison **2/8 PASS,6/8 OPEN** (95/117/121/155/195/204), differences только x/y/z. У95 prepared18006 → stateVersion18016,1178417→8428; новый Town position. Последние actual native34 receipts ещё содержат original coordinates. Более поздний projection/background lineage не прослежен полностью; нельзя произвольно заменить expected snapshot либо назвать mismatch потерей данных. Оба stock shutdown healthy. Debugger-paused startup не включён в gameplay timing.

Старый Jar87 C21: continuity8/8 + storage8/8 + два actual sameDB8/8; это прошлый кандидат. Старый C22 crash72 AFTER_OWNED_NATIVE_STORE, profile1082: planned halt72 и actual recovery storage1/1, но diagnostic recovery barrier45s timeout — clean recovery gate OPEN. Старый C23 crash73 AFTER_OWNED_FINALIZE_COMMIT, profile634: planned halt73, actual recovery1/1, restart31 stock healthy. C24 auto-release диагностического barrier изменён, но **crash72/73 на нынешнем Jar98C1 NOT_RUN**. Planned halt не объявлен здоровым shutdown; ранние proof не перенесены на нынешний кандидат.

## DEBUG evidence, C08 и cleanup

Source build.xml уже содержит lines/vars/source. Own loopback JDWP5031, suspend=n; jcmd VM.command_line и Thread.print -l сохранены privately для actual JVM incarnation. Узкие JDI чтения не вызывали gameplay methods; startup breakpoint только event-thread с hard8s resume, никаких mass catch1280. Gameplay C26 JFR60s записан после enrollment без debugger pause: private `.phantom-local/ops031/C26_A33-gameplay-60s.jfr`, SHA256 `17BDC72FAD14B8C2490F2FD2F7C94DE1E9FDE54EC2ED2B2BB8704530BAFDA486`,81 GCPhasePause,0 JavaMonitorEnter events. Pre-scene C25 JFR не выдан за gameplay.

C08 inline ordinary FINALIZED SQL снят. `Contract031Observer.java:782–808` собирает immutable native intent/SEALED Player receipt и enqueue, без SQL/I/O/wait. Exporter отдельный TASK031-witness-export thread:476; SQL:879 read-only/RR, каждый Statement query timeout2s:876. Метрики C26:4777 hooks, total834208700ns/max4848500ns, captured/exported3895/3895, pending0, overflow0, exporterFailure empty, activeRefs0/buffer0; nativeBirths924. Наблюдаемое окно и maxgap1.0465s не показывают blocking ordinary hook. Однако pool acquisition DatabaseFactory.java:133 имеет60s timeout; общий pool/queue создаёт возможное косвенное влияние. Read-only само по себе не доказательство безопасности, универсальная невмешиваемость НЕ заявлена. Специальные exact crash/recovery SQL/barrier относятся к untimed fault scenes, не к ordinary gameplay proof.

Own36 финально остановлен штатным `Control031.ps1 -Action Stop -Episode a -DumpDuringStop`: STOP_EXIT0; Game26628/Login30792 stock graceful confirmed. Typed phaseDONE/outcomeCOMPLETE, materializationSTOPPED retained0, backgroundSTOPPED/currentOperations0/currentIdentityLeases0/currentTransactions0/retainedIdentityLeases0, activeReferences0, proofFailure empty. Typed proof снят до остановки pools (Shutdown=false в receipt), затем процессы отсутствуют. FINAL031_PUBLIC_SCALARS.json содержит свежую cleanup проверку и хеши, без raw. Собственные test/Synthetic/helper JVM завершены; foreign MariaDB/чужие процессы не остановлены. Final typed-stop SHA256 `46FB1C804DCCB176600521D76C2820A6B9D6F6F3F1363C811BBD65EED1AC13A9`.

Privacy review: публикуются только числовые actor ID/epochs/deltas, verdicts, own PID/time и hashes/path references. JFR, jcmd/JDI dumps, SQL dumps, canonical Base64/inventory, account/credentials и raw gameplay не staged/не pushed. Локальные immutable evidence сохранены; старые NOT_RUN/failure не удалены.

## Handoff, git и кодировка

Точный следующий анализ, автоматически НЕ запущен: снять первое реально FALSE из historical visibleFarmReady1112–1114 у43, отдельно bounded useful-debt117; связать committed projection/background versions между native34 и quiet SQL35; затем короткий причинный runtime тест, стабильный whole-group continuity/away и оба crash gates на одном frozen Jar. Фактов для масштабной перестройки GameServer нет; выборы camps/warm-up без новой гипотезы прекращены. REAL_FINAL инструкция и WAITING_REAL_FINAL требуют сначала всех server PASS.

Final exact-path allowlist: Accept031.ps1, RESULT_HANDOFF031.md, PROGRESS031.md, evidence/FINAL031_PUBLIC_SCALARS.json. Git явно разрешён пользователем (exact commit/normal push) и TASK031 source/scope guards. Использованы bounded `git status --short` (inventory), `git diff --stat`, `git diff -- <exact path>`, `git diff --name-only/--numstat <base> -- <java/test>`, `git rev-parse HEAD/HEAD:<java>`, затем exact `git add -- <эти 4 paths>`, `git diff --cached --name-only`, `git -c core.whitespace=cr-at-eol diff --cached --check`, `git diff --cached --stat`, `git commit`, `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`, `git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`. Локальный HEAD detached, requested branch принадлежит другому worktree; normal refspec push не меняет его checkout. Без force/reset/clean/amend. После push HEAD=remote проверен, exact staged scope без private raw.
Точные команды финального checkpoint (переменные имеют эти значения):

```powershell
$r='C:\Users\ZBook\.codex\worktrees\m1-native-demand-031\L2J_Mobius'
$p='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-DEMAND-031'
$allow=@("$p/Accept031.ps1","$p/RESULT_HANDOFF031.md","$p/PROGRESS031.md","$p/evidence/FINAL031_PUBLIC_SCALARS.json")
git -C $r add -- $allow
git -C $r diff --cached --name-only
git -C $r -c core.whitespace=cr-at-eol diff --cached --check
git -C $r diff --cached --stat
git -C $r commit -m 'TASK031: record incomplete frozen acceptance and healthy cleanup'
git -C $r push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C $r rev-parse HEAD
git -C $r ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

- Mojibake-маркеры в изменённых файлах проверены отдельным полным набором пользователя; совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно (\\u04/05 и XML x/X04/05); совпадений нет.

STOP_AUTHORITY=TASK031_CONTRACT; все own JVM STOPPED штатно в финальном cleanup. Серверная приёмка НЕ GREEN; M1=OPEN, REAL_FINAL=NOT_RUN.


### C28 — заключительный read-only audit до предельного срока

10:29:55Z: actual successful OrdinaryProjectionCommit receipts own34 для117/121/155/195/204 имеют beforeVersion8427/15330/13206/14236/13798; quietSQL35 stateVersion ровно +1 у всех пяти. Это новые committed промежуточные записи после исходных native receipts. Для95 такой projection receipt в этом каталоге не найден. Evidence C28_POST_NATIVE_PROJECTION_METADATA.json содержит только числовые версии, sourceSHA и hashes. Полная последовательность переходов и equality afterPayload/SQL не проверены; restart35 остаётся OPEN, semanticLineagePassClaimed=false. Никаких JVM/SQL изменений. Предыдущий goal turn — progress (code/report/evidence exact commit); этот — progress (новое authoritative lineage evidence). Новый runtime за оставшийся cleanup интервал не запускался.

Exact follow-up команды: `git -C $r add -- "$p/RESULT_HANDOFF031.md" "$p/evidence/C28_POST_NATIVE_PROJECTION_METADATA.json"`; `git -C $r diff --cached --name-only`; `git -C $r -c core.whitespace=cr-at-eol diff --cached --check`; `git -C $r commit -m 'TASK031: preserve intervening projection metadata without closing lineage'`; `git -C $r push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`; `git -C $r rev-parse HEAD`; `git -C $r ls-remote origin refs/heads/experiment/m1-candidate007-observe008`. Exact allowlist2 paths; raw payload не публикуется.
