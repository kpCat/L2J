# EVIDENCE — что сохранить, без внешней демонстрации секретов

**Private root**: переиспользуй принятый механизм `.phantom-local/logs/<TASK-ID>/index.private.json` из D (или физически проверенный аналог). Никакие сырые БД/accounts/SQL credentials/UUID/private TSV/logs нельзя отправлять в Git. В публичном отчёте — paths, числа, timestamps, SHA-256, redacted decisions, gate statuses. Не переписывать ничего в `...108R3D/`.

## E0. Read-only анализ сохранённого R3D

- Private D index + `cohort108R3-4-start.private.tsv`, `cohort108R3-4-plateau.private.tsv`, `cohort108R3-4-final.private.tsv`, `samples4.tsv`, `world4-first.tsv`, `world4-second.tsv`, postexit `delta105.log`, beforeB/afterB full snapshots and seal.
- Каждое заключение связано с checkpoint/SHA; отсутствие приватного файла = `UNAVAILABLE`, не `PASS`. Old B failed World/negativeRows остаются неизменными.
- Сводная таблица по A vs B: точные observer profile/region/candidate lookup failure reasons; `physical` and `preparation`; `STATUS.admission` (`pendingRebalance`); `dueSnapshot.reason`; scheduler/calendar/TZ; actual native owners. Не объявлять observer bug единственным root без причинной связи.

## E1. Fixed harness and focused unit negative controls

- RED test `same home region: first topology target null, second valid => second not skipped`; successful observer count exactly2; failed admission/identity lease not counted; observer Java `World.getPlayer(objectId)==same instance`, ownership `LOCALPLAY_TEST_HUMAN`, `humanObserver` accepted, native locality producer nonempty, both positions have valid instance0 and real topology.
- Captured per-candidate bounded trace: profileId, homeRegion, lookup result with reason (not registered/stale/point-null), observer attempt, lease outcome, actual `World` presence, selected non-overlapping regions, timestamp and topo generation. Limit trace to candidates required for 2 observers and <=64 detail rows/cut, aggregate overflow counters; no 5000-line per-tick log.
- Per-profile representative trace for World shortage: own live admission (`desired/eligible/admitted/reason`), clock/calendar, `_admissionDirty`, ecology revision current/stale, scheduling permission, native catchup status, demand/preparation selected, failure, retention/lease, materialization status. Interpret `eligible=0` with `pendingRebalance`, not as POLICY_LIMITED alone.
- Keep exact before/after code test results, red/green commands and independent code review, no expensive broad soak.

## E2. SP skills / expense evidence

- Pre/post `characters` SP rows (existing 10-slice), separate stable SQL **read-only** sidecar for `character_skills` filtered exact OWN102 cohort and class indexes (not legacy slice rewrite). Sorted SHA, row count bounded, class identity, exact stamp of preA/afterA/preB/afterB.
- For every negative SP: `charId, classIndex, classId, level, EXP, skillId, beforeLvl, afterLvl, canonical price, unique success/commit receipt ID (or UNKNOWN), credits, debit, formula result`, without user account secrets. Actual result from canonical `PhantomProgressionService.learnClassSkill` / `PhantomClassSkillLearningTransaction` if safely observable; only opt-in/bounded TEST recording if current native receipts insufficient, not unconditional production logging. See `SOURCE_MAP`.
- Negative controls: dropped skill row, wrong classIndex, same skill twice, debit without skill, cost mismatch, credit invented from aggregate, success result without DB commit, commit without runtime reconcile, stale class identity; must FAIL. If no negative SP in this run, retain negatives; do not force a purchase.

## E3. Native A→B

- Original TEST authority identity, source+staged input hashes, JDK/Ant/JAR SHA, classpath/config/Geo203/owner config, OS process PID+creation+SID+command SHA, observed exit from held handle.
- World first/second/stable cohort with actual native player identity/ref/epoch/lease, two observers, plateau and per-candidate admission traces, 250 true open stores by type and owner, original 5000 exact IDs, natively earned XP/SP/stock transactions, canonical market ledger/funding and no replay.
- Actual OWNED native logout callback EARNED, current scope, outstanding0, shutdown and pools, two quiescent SQL/lease cuts, JDBC foreign0, JVM/TCP0, stable checkpoint compared to SELECT-only postexit. Explicit reports of what is **not** proven (e.g. 128 PLAY World).
- File hashes: `world6.pass`, `world4.pass`, `stop6.natural`, `stop4.natural`, `postflight6/4.pass.json`, `exit6/4.clean`, signed independent review6/4, immutable `index/authority`, before/after skill sidecars, `delta105.log`, `market`, no old D modifications. **Names are examples from D; retain existing contract semantics under private 108F namespace.**

## E4. Final publication

Report `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-WORLD-FINAL-CLOSURE-108F.md` with exact commits/diff, two production fixes max (if any), testing/negative cases, gates, WIP preservation, provenance SHA, native results or STOP reason and local private index. Any unavailable source/receipt or inconsistent pair → `UNKNOWN`/`BLOCKED`, never inferred success. At end confirm `local HEAD == origin tracking == live GitHub` only after actual normal push; don't claim local status from remote-only data.
