# TASK053 — ночная доводка М1, 2026-10-15

Статус текущего checkpoint: **IN_PROGRESS; M1=OPEN; M1_SERVER_READY=false; READY_FOR_M2=false**. Реальный клиент/USER_ACCEPTANCE=WAITING_USER. Один общий deadline12:44UTC15октября, включая остаток052; резерв30min. Без субагентов, новых worktrees, изменения истории и review ZIP.

`OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius`; `M=OWN/L2J_Mobius_CT_2.6_HighFive`; `P=M/.phantom-local/contract051a`; `T=M/.phantom-local/contract053a`.
TASK052 закончена со своим PARTIAL/G4=NOT_VERIFIED, штатным strict stop, SQL и публикацией `123176c56d0043c2a871682364d8d7788c31d38f`. Это BASE053, проверенный HEAD=remote до записи. Её результат не переписан последующим053.
Прочитаны GOAL/PLAN/ACCEPTANCE/SKILL_POLICY053 из MAIN read-only, Agents/workflow/release contract/SCENARIOS041, actual ecology/native/observer owners. В053 пакете ровно4 указанных файла, других TASK/CONTEXT/SOURCE_MAP нет. Локальные аналоги: exact045/049 headless lane, Verify049/Prepare-Test049 и неизменённый051 recorder. JDK25/Ant1.10.17/WinPS5.1, только High Five.

Первый gap052: native runner не знал новой разрешённой test identity. Минимальная test-only правка `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`: один exact053a binding по049-паттерну, взаимное исключение lanes; прежние exact real root/CWD/manifest/config/owner/JDBC/port3308/exportSHA/pool4 guards сохранены. Product API/политики/лимиты не менялись.
Для fixture-тестов создана только новая `l2jmobiush5_phantom_test_053a` из неизменённого stopped052 SHA256=`5d59312815333160eabd5a855f04608e4b3b4467dde493b7b4d67041cd4fb211`: read-before-create, один import053, reimport051=0. Game/Login boots053=0, новых agents0. Накопительная051a и старые DB этим runner не используются.
RED текущего runner: `T/ops/evidence/RED_LANE053.log`, exit1, exact root rejected до DatabaseFactory. GREEN: current JAR-first **95/95**,18 routes, exit0 (`CURRENT053-matrix.tsv`), исходные94 + regression052; lifecycle acceptance **11/11**, exit0 (`ACCEPTANCE053.log`). Ant compile-tests PASS,2 прежних runFinalization warnings.
Все проверки на Game JAR `P/build052/dist/libs/GameServer.jar`, SHA256=`c7ff546c47f2b00177fc149d53e741b2f40cac4c66165d1039612e15dc91009f`, classpath JAR-first без development bin. Семь negatives **7/7** до DB: mixed owner, wrong manifest/config/CWD/owner/schema/export; `T/ops/GUARDS053.json`. Temporary053 input bytes восстановлены.
Observer memory fixtures **3/3**: autonomous без heartbeat, REAL-affinity/stale disconnect, instance; Pilot security fixtures **15/15**. Это server fixtures, не actual REAL login или клиентская приёмка. Логи `T/ops/OBSERVER053.log`, `PILOT053.log`.
SKILL_POLICY: существующий `PhantomEngagementPlanner.offensiveSkill` получает текущий `Player.getKnownSkill`, проверяет active/passive/MP/cooldown/items/conditions; новые профессии/AI/экономика и фиктивное изучение навыков не добавлялись.

Prospective05303:16:54UTC: прежний stable target **10129/object268485600**, initialComplete=true, pending=false, READY/FULL-PNC/FARM eligible, cursor29867202/horizon29867236, debt34, ACTIVE до06:03UTC, EXP59042/SP7104, x45195/41189/-3504. `T/PROSPECTIVE053.tsv` содержит deterministic max8 и16 exclusions; LIFE target не заменяется после исхода. Старта runtime с новым JAR в этом checkpoint ещё нет.

| Gate | Текущий честный статус |
|---|---|
| G0 | BASE/remote/OWN подтверждены;051a stopped cut сохранён; новые fixture writes только053a. |
| G1/G2_CURRENT | NOT_VERIFIED; две natural6–8 группы380s на current JAR ещё не выполнены. |
| G3/G4 | NOT_VERIFIED на новом JAR; strict052900/900 относится к исходному runtime, не перенесён. |
| G5_CURRENT | Historical050 сохранён; applicability/current scoped restart ещё не подтверждены. |
| G6 | OPEN: existing041x72/73 manifest JAR5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c/source92400bebb9126ec4364ac57b05f8c453fe42690d не равен текущему. Новых crash/fault/kill/аварийных JVM нет. |
| G7/P07 | PARTIAL:95+11+observer3+Pilot15 PASS; P07 ещё отдельно не проверен. |
| G8/R1 | NOT_VERIFIED; новый clean tracked package/operator smoke ещё не выполнен. |
| O_SERVER | Fixtures3/3+Pilot15/15 PASS; autonomy >525s actual process ещё не доказана053. |
| O_REAL/REAL_FINAL/USER_ACCEPTANCE | WAITING_USER; М1 не закрывается, М2–М7 не запускаются. |

После подтверждения каждого исправления/gate выполняются exact-path ordinary commit и non-force push в `experiment/m1-candidate007-observe008`; публикационные SHA/private receipts вне отчёта во избежание самоссылки. Git разрешён прямой ночной инструкцией и GOAL053. Prefix `G=git -c safe.directory=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius -C C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius`; использованы `rev-parse --show-toplevel`, `rev-parse HEAD`, `status --porcelain=v1 --untracked-files=no`, `ls-remote origin refs/heads/experiment/m1-candidate007-observe008`, `diff --check`, `diff --stat -- E`, `diff -- E`, exact `add -- E R`, `diff --cached --name-only`, `diff --cached --check`, `diff --cached --stat`, `commit -m "phantom(task-053): bind isolated native regression lane"`, `push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`, final HEAD/remote/clean checks. E=`L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`; R=`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M1-OVERNIGHT-053.md`. Никаких force/reset/restore/clean/rebase/branch и broad add.

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
