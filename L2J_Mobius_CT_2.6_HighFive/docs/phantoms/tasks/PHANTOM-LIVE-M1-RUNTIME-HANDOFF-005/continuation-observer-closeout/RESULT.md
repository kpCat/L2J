# PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005 — observer closeout

Status: **M1 OPEN; CONNECTED RED; PRE-ARM BLOCKED by preserved profile13 INCONSISTENT**. Новый arm и connected-run после producer fix не выполнялись; M2 и новые task/proof не начаты.

## Последний connected boundary

- Единственный connected-run после предыдущего recovery: `314658b3-a72c-4a7c-b9fd-ae7d11de09ce`, profile13, `STORED_START`, `APPROACH_DEADLINE_EXPIRED`. Все 87 snapshots были `COMMITTED/STORED`, objectId0, World=false; TestAdmin дошёл до 9 единиц от committed point. `readinessReason=catchup.renewal.background_state_invalid`; NEW_MATERIALIZATION/CONTACT/NATIVE_LIFE/COHORT/SOFT_RETURN не наблюдались, RESTORE/STOP PASS. Приватное evidence сохранено в `.phantom-local/m1-005-connected-314658b3-a72c-4a7c-b9fd-ae7d11de09ce/`.
- Этот run не установил причину перехода в INCONSISTENT. Последующее исследование ниже выполнено read-only на PLAY; TestAdmin не перемещался.

## Phase 1 — exact read-only PLAY

- Profile13 связан с characterObjectId268484332. `background.state=INCONSISTENT`, rowVersion9791, updated_at UTC `2026-09-30 10:03:55.784`. Предыдущий coherent PLAY dump: `MATERIALIZED`, rowVersion9790, updated_at UTC `2026-09-29 17:58:27.327`; canonical `characters` уже имел расхождение. Connected-run начался в UTC10:27, значит INCONSISTENT существовал **до** него. Какой прежний вызов записал canonical drift — **UNKNOWN**: точного аудита writer в логах нет.
- Typed invariant: `BACKGROUND_CANONICAL_MISMATCH:CURRENT_HP_CURRENT_CP_POSITION_X_Y_HEADING`. Background expected HP128/180, MP67/67, CP8/72, x44126 y42751 z-3488 heading25847 instance0. Canonical actual HP180/180, MP67/67, CP72/72, x45975 y47879 z-3488 heading12772 instance0.
- Identity/profile link/objectId, active class/classIndex/race, level/EXP/SP/expBeforeDeath, max vitals, z/instance совпали. Tracked mutable item objects/count/location, inventory hash `1c6a9aecf3a692c6fe90430cd5bda245eb9e5e0b239e2a93a18e31db718cd0fc`, load9691/8832000, slots15/250, auto-get skills 194/1,1320/1,1322/1 совпали. Receipt expectedAfterHash есть, но его guard действует только для VERIFY_PENDING; здесь сохранён MATERIALIZED.
- Snapshot census перед deploy среди **1280 READY**: ABSENT0, READY419, DEAD626, MATERIALIZED30, VERIFY_PENDING4, INCONSISTENT201. Предыдущий coherent dump: MATERIALIZED244, READY510, DEAD522, VERIFY_PENDING4, INCONSISTENT0. Для 201 случаев `MATERIALIZED→INCONSISTENT` rowVersion вырос на один после dump; это системная restart/recovery boundary, а не единичный profile13.
- Точное разделение MATERIALIZED на `World Player present/absent` — **UNKNOWN**. DB `characters.online` не доказывает World. Read-only `.phantomstatus` у TestAdmin вернул `personal access denied`; production access guard не менялся.

## Phase 2 — producer и guarded TEST

- Доказанный воспроизводимый producer **этой формы drift**: `PlayerAutoSaveTaskManager` вызывает `Player.autoSave()` для headless native Player; прежний `autoSave→storeMe` записывал HP/CP/XY/heading canonical, пока background projection оставался MATERIALIZED. Native TEST до фикса показал именно этот RED, включая profile13-shaped координаты и витальные дельты. Исторический writer именно profile13 остаётся UNKNOWN.
- Минимальный fix в `Player.java`: periodic `autoSave()` пропускает headless outbound session; owned dematerialize/store/capture сохраняет canonical через прежний guarded lifecycle. Durable matching, INCONSISTENT guard, NORMAL admission, LEAVE/RETURN tickets и production navigation policy не ослаблены; DB position UPDATE на каждом шаге не добавлены.
- `PhantomBackgroundSuite.java`: native Player autoSave не меняет canonical до owned capture; matching MATERIALIZED после interruption/restart возвращается READY; точный profile13-shaped HP/CP/XY/heading mismatch при abort остаётся INCONSISTENT. Никакого ручного PLAY UPDATE/DELETE/recovery profile13.
- Focused TEST: background position canonicalization **7/7 PASS**; transaction, native Pilot, navigation core, production materialization и headless Player targets — **BUILD SUCCESSFUL**; linked historical-background-goal033a **31/31 PASS**. Aggregate205 не запускался. Actual `Test-M1ObserverOffline.ps1`: **M1_OBSERVER_OFFLINE_PASS** с movement, COMMITTED→LIVE epoch, CONTACT/OBSERVE/LEAVE/15s ABSENT/RETURN/RESTORE/STOP.

## Phase 3 — linked native TEST

- Existing SOURCE_MAP содержит точную таблицу 01–10 для profile70733: human demand → ecology due → historical renewal/recovery → readiness complete → NORMAL Player object268482073 → DecisionEngine goal `3942388938638833675/0/ACTIVE/farm.background` → matching `current()` guards → `start/running` true → native attack damage14 → guarded cleanup READY/no Player. Reasons сохранены по каждому переходу. `current()` — private guard; TEST проверил те же prerequisites и успешный start/running, а не вызывал private method через reflection.
- Valid farm target не оставил native fixture в бесконечном ACTIVE_IDLE: первая атака снизила HP NPC. Существующий native travel проверил typed `travel.navigation_pathfinding_disabled`, navigation core — STUCK/replan. Точный водный маршрут Elven Village TEST fixture не воспроизвёл; по нему нет нового PASS/RED и не изменена gameplay navigation policy.

## Clean build, deploy, state

- Source `f9562c8002f4d2487baae6840c99001b743d0a83` опубликован обычным fast-forward в `feature/phantom-world`. Exact stage: `Player.java`, `PhantomBackgroundSuite.java`, existing `SOURCE_MAP.tsv`; шесть TEST SQL EOL-only worktree файлов не staged/published.
- Clean `ant -q -Dbuild=.phantom-local/m1-005-profile13-clean jar`: BUILD SUCCESSFUL. Deployed GameServer.jar SHA-256 `EF10CA499A82174199D8C13D85346D10D86C4038AA5DDAC9E6361E662E8D6D44`, LoginServer.jar unchanged `3B79A86276AA544E6886881F54814BE159FAC1412241852408781E86EF64A9E8`; manifest source/hash совпали.
- Existing controlled deploy сделал owned stop, coherent native PLAY dump 66 344 905 bytes (completion marker, stderr0, SHA-256 `8CDAA9DB0A16F92C0302BC9A50B2D0E20596687C36CE66C225CD043858076478`), backup `artifacts/local-play/m1-005-backup-f9562c8-20260930-184320`, один deploy/restart. `Check-LocalPlay.ps1`: CONFIG PASS, Login PID25456/Game PID20600 owned/RUNNING, ports healthy; Pilot OFF/runActive=false. Caps 1280/64/128/100 ms, profilesPerPulse256, maxScheduled10000 сохранены.
- Read-only immediately after restart: population READY1280/RETIRED8720; background MATERIALIZED25/READY850/VERIFY_PENDING4/DEAD195/INCONSISTENT206. Последующий адресный export во время обычной работы: MATERIALIZED21/READY842/VERIFY_PENDING4/DEAD203/INCONSISTENT210. Из pre-deploy snapshot девять MATERIALIZED стали INCONSISTENT с rowVersion +1; это совместимо с recovery сохранённого drift, но момент mismatch и writer этих девяти — UNKNOWN. Profile13 по-прежнему INCONSISTENT/rowVersion9791.

## Gate

- **Не WAITING_ARM**: выбранный profile13 остаётся fail-closed, а authoritative side для его старого canonical/background drift не доказана однозначно. Не сбрасывать profile13, не подменять его другим профилем и не выдавать arm для заведомо заблокированного connected path. Только настоящее клиентское наблюдение ещё может подтвердить NEW_MATERIALIZATION/CONTACT/NATIVE_LIFE/COHORT/SOFT_RETURN, но до него нужен доказуемый recovery path для profile13 либо отдельное решение пользователя по сохраняемому blocker. Второго connected-run не запускать без свежего consent.
