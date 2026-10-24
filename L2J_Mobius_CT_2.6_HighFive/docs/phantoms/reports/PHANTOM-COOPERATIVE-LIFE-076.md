# PHANTOM-COOPERATIVE-LIFE-076

Итог: **TOTAL=PARTIAL; две production-причины исправлены, FOCUSED_PASS, IMPLEMENTED_PENDING_OWNER_QA**.
Единственный проход 076; native/client OWNER_QA отложена владельцем. Полный игровой PASS не заявлен.
Baseline 24.10.2026: local и live origin `feature/phantom-world` = `dbd6ba00b33bd219ea8fa391afef3ac586fff793`; tracked clean.
Root фактически определён через rev-parse. Старые worktrees/candidates не использованы; новый worktree не создавался.
Исходные untracked `.phantom-local/`, packages068V/073/076 не staged и не заменяются.
Прочитаны все 8 файлов пакета076, root AGENTS/README, workflow contract, current status, финал075 и bounded roadmap D.
075: по прежнему финальному отчёту OWN075 STOPPED, JVM0/listeners0/DB0; нового native запуска здесь нет.

Read-first: invitation policy/processManagedInvitation, native backend/currentForce/capabilities, reconcileRealLed,
advanceRealFollow/dispatchTactics, role matcher usage, route FOLLOW, support effect authority/lease,
raid readiness/recruitment/assembly/attempt/runtime и native Kamaloka entrant guards.
Ближайшие аналоги: tactical combat cancellation token, raid pending support lease, existing Party lifecycle/route fixtures.
Дополнительных локальных AGENTS/code-map/pattern-файлов в выбранном scope не найдено.
Ограничения: JDK25/Ant, один существующий Combat owner, native Party/skills/rewards, максимум две причины; без новых executors/schema.

1. Причина FOLLOW: PROFILE_BUSY/QUEUE_BACKPRESSURE/COOLDOWN дают retry, но истечение его deadline сохраняло permanent blocked.
   При неизменных координатах/leader/topology больше не было перехода к полезному FOLLOW.
   Теперь истёкший известный transient wait освобождает попытку; следующий pulse получает новый ограниченный бюджет.
   NO_PATH/прочий geometric reject по-прежнему требует изменённого evidence. Телепорта, обхода Geo или бесконечного lease нет.
2. Причина support: dispatchTactics передавал cast token только на STOP. Pending native CAST/approach после leave,
   смены leader/roster/session/death/instance сохранял actor claim до конца действия/deadline.
   Теперь support token проверяет captured group/generation, committed claim, current actor, native leader/exact roster,
   доступность actor/target, смерть actor и совпадение instance; прежний releaseFinishedAction закрывает только свой lease.
   Нормальный незавершённый cast остаётся owned; HOLD сохраняет stationary support и прежнее ожидание окончания cast.
После этих двух причин новые production-направления не открывались.

Production paths относительно модуля:
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java`
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`
Existing tests: `test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java`; два regression case в прежних route/tactics modes.
Дублирующие Party/Combat/Raid engine, production test hooks, новые mode, конфиги и allowlists не добавлены.

| Gate ACCEPTANCE.tsv | Статус | Доказательство / граница |
|---|---|---|
| P0_MIXED_INVITE | SOURCE_PASS, FOCUSED_PASS | Real invitation policy/social preference/native respond; canonical invitation6/6, lifecycle refusal/timeout/inactive goals; CLIENT_QA_PENDING |
| P0_FOLLOW_HOLD | IMPLEMENTED_PENDING_OWNER_QA, FOCUSED_PASS | route6/6: transient deadline recovery, NO_PATH stays blocked, foreign generation cannot cancel, own cancel releases movement; клиентский FOLLOW/HOLD pending |
| P0_SUPPORT | IMPLEMENTED_PENDING_OWNER_QA, FOCUSED_PASS | tactics3/3 + post002-support9/9: actual capability plan, human target, pending CAST retained once, seven stale-authority cancellations; native MP/LOS/range/cast/effect pending |
| P1_RAID_ASSIST | SOURCE_PASS, FOCUSED_PASS | unchanged runtime15/15: exact Party-local snapshots, REAL provider without control, tank/support reservations, heal/res, minions/collector and cancellation; native mixed encounter pending |
| P2_INSTANCE_GUARD | SOURCE_PASS | Kamaloka Entrants/current roster, leader/size/death/instance/level/range/reuse checks and all entrants teleport retained; native execution NOT_RUN; Rift unchanged |
| LIFECYCLE | IMPLEMENTED_PENDING_OWNER_QA, FOCUSED_PASS | lifecycle12/12, state-recovery9/9; stale session/leader/roster/death/leave/STOP; no real consent replay after restart |
| PERFORMANCE | SOURCE_PASS | Same shared control pulse/Combat owner, no executor/global scan/log added; token reads only actor/target/current Party, roster≤9; existing pulse budget tests GREEN; soak NOT_RUN |
| DELIVERY | compiled source/JAR; scoped publication | One production compilation/JAR; test-only compiler repair below. Final commit/live remote receipt and tracked status are in the final execution response. |

Already present, preserved: consent/refusal, native membership, learned capability evaluation, effect-aware maintenance,
assist/protect, heal/recharge/resurrection, HOLD stationary range policy, earned native rewards and restart consent stripping.
L2jCombatBackend.castSupport still validates catalog/known skill/exact target scope/party/instance/death state,
rebuff effect/range/stationary rules/reuse/MP/native conditions. No native skill, item or result was fabricated.
RaidReadiness observes current Party/CC; recruitment requires actual invitation authority; assembly selects a Phantom
route actor from the exact Party and keeps human members in the same roster. Runtime controls only PHANTOM offense/support.
Unsupported level/CC/profile/target/provider/native admission remains a reject, never an override. Boss25199 victory not required.
No third independent cause was addressed after the two permitted fixes.

Checks: `$R` = actual Git root; `$M=$R/L2J_Mobius_CT_2.6_HighFive`; `$O=$R/.phantom-local/task076`.
Logs are only in `$R/.phantom-local/logs/PHANTOM-COOPERATIVE-LIFE-076/`; no logs/binaries/packages staged.

1. `git rev-parse --show-toplevel`; `git symbolic-ref --short HEAD`; `git rev-parse HEAD`; `git status --short`:
   baseline above, tracked clean. `git ls-remote --heads origin feature/phantom-world`: sandbox network exit1,
   authorized network repeat exit0, same baseline. Task MANIFEST SHA256:7/7 unchanged, exit0.
2. `ant -q -f L2J_Mobius_CT_2.6_HighFive/build.xml "-Dbuild=$O/build" "-Dlibs=$O/libs" jar compile-tests`:
   **exit1/49s**, after successful production compilation, OwnerConfig/OwnerProfile validation and JAR creation;
   new TEST fixture incorrectly treated void `combat.start()` as boolean. Corrected to ordinary `combat.start()`.
   No second Ant run, production compilation or JAR. Dependencies copied to fresh owned libs; source dist/libs untouched.
3. Test-only `javac -encoding UTF-8 -source 25 -target 25 -cp <owned bin/test/libs> -sourcepath $M/test/java
   -d $O/build/phantom-test/bin <PhantomPartySuite.java>`:exit0. First launcher attempt:exit1/ClassNotFound,
   because failed Ant had not emitted PhantomTestLauncher. Full existing test sources compiled with `javac @$O/tests.javac.args`:
   first exit1/Windows argfile backslash escaping before compile; normalized paths then exit1/Hikari wildcard classpath;
   exact four dependency JAR paths then **exit0**,395 existing test sources,5 pre-existing warnings. No production edits/rebuild.
4. From `$M/dist/game`: `java -Dphantom.module.root=$M -Dphantom.test.reports=<076 logs>
   -cp "$O/build/bin;$O/build/phantom-test/bin;$O/build/phantom-test/resources;$O/libs/*"
   org.l2jmobius.tests.phantoms.PhantomTestLauncher <mode> 760076`:
   party-route6/6, party-tactics3/3, party-canonical-invitation6/6, party-lifecycle12/12,
   party-state-recovery9/9, post002-support9/9 — each exit0. Six final modes45/45.
5. Same launcher `raid-attempt-runtime-goal026d 760076`:exit1/beforeAll, expected pinned26002653.
   Repeat `raid-attempt-runtime-goal026d 26002653`:**exit0,15/15**. Guard unchanged; final seven modes **60/60**.
6. `Get-FileHash -Algorithm SHA256` compared with pre-build inventory:89/89 unchanged, exit0
   (public config/OwnerProfile files, OwnerConfig.ps1, original source GameServer/LoginServer JARs).
   Both production source hashes still match pre-build inputs; packed two changed classes equal compiled classes byte-for-byte.
7. Independent read-only review of exact three-file diff: no remaining Critical/Important after correcting moving snapshot
   in TEST fixture. Review did not run builds or native tests. Later void TEST compiler correction is recorded above.
8. Final exact-path diff/check, four-file scope guard, two Cyrillic scans and publication commands below; no unrelated staging.

Source SHA256: Coordinator=`90751A570775AB36CF0D78244C14D1ECC31B338FFC6F212E29C4A70E47C02F3E`;
RouteCoordinator=`CB1927F4FF98668F0BA5540BC8817B72E8500A9EE0B9FA5BF2A9F7AE52A17BC5`.
JARs in `$O/build/dist/libs`: GameServer=`4192267E99E8BC9A3AABE4D8EC6A2C326CC4E4CBCE9FD4FB19500DFAA2516B84`;
LoginServer=`FF0CA31F5AAB1247AF0AB167339B03A501D9E233E5E80AAE9B6B4D13C052BEAC`.

Final allowlist = the two production paths above, existing PhantomPartySuite.java and this report only (4 files).
Git authorized by TASK076/DELIVERY and the explicit execution request: bounded preflight commands in check1;
`git diff -- <exact 3 code/test paths>`; `git diff --stat/--check -- <exact 4 paths>`;
`git diff --cached -- <exact 4 paths>`; `git diff --cached --name-only`; `git diff --cached --check -- <exact 4 paths>`;
`git status --short --untracked-files=no`; exact `git add -- <4 paths>`;
`git commit -m "phantom(task-076): renew transient follow and scope party support ownership"`;
`git push origin HEAD:refs/heads/feature/phantom-world`; final `git rev-parse HEAD`,
`git ls-remote --heads origin feature/phantom-world`, tracked status. No force/branch/worktree/merge/rebase/reset/clean/restore.
The report cannot contain its own commit hash; final local=live remote SHA and actual push exit are recorded after publication in the final response.

- mojibake-маркеры в изменённых файлах проверены: весь заданный набор,4 files,0 matches.
- escaped Cyrillic в изменённых файлах проверены: все6regex,4 files,0 matches.

Native Main/DB setup/soak/restart/client/raid victory/instance execution = NOT_RUN, DEFERRED_BY_OWNER.
STOP/DB touched=NO; защищённые candidates, код074/075, OwnerConfig, Kamaloka/Rift не изменяются.
Отдельная RED-компиляция не выполняется: приоритет имеет лимит владельца на одну compilation; native effect не выдумывается.
Next OWNER_QA: одна обычная human-led Party с native FOLLOW/HOLD/ролями/leave/STOP и допустимый mixed raid;
native MP/cooldown/LOS/effects/resurrection, клиентская сцена и настоящая instance admission остаются OWNER_QA_PENDING.
Финальная игровая QA не запускается автоматически; R2/077/новый roadmap не создаются.
