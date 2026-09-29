# STATE

Status: CONNECTED_RED_M1_OPEN; final manual gate REQUIRED. M1 GREEN не заявлен, M2 не начат.
Продолжение: continuation-history-self-heal/RESULT.md; TASK/DESIGN/PLAN реализованы, TEST aggregate 205/205 PASS.
Code/deployed source: 9e3576311af6ef8c53e662955e2330b7cee00143, origin feature/phantom-world опубликован.
Clean detached `ant jar` PASS; GameServer SHA256 FA609D7E5E3B8A269500F54D03C339C65C40A560569C7D5927FB0639EC7CD3EE.
Controlled deployment backup: artifacts/local-play/m1-005-backup-9e35763-20260929-203534; native PLAY dump 63502509 bytes.
CONFIG/ownership PASS: Login PID7860, Game PID16908; 1280/64/128/100, maxScheduled10000.
PLAY read-only 2026-09-29 17:36:52 UTC: READY1280/RETIRED8720, retired pending6641, reserve digest0659577b3b92f7708bebb3e4688a0d8ac9065714654e6736064b8343dffd1a68.
Pre-arm history COMPLETE501/RUNNING229/FAILED_REPLAN_REQUIRED550; known recoverable failures were not a global M1 gate.
Selected connected profile545 had `historicalStatus=COMPLETE`; no unknown history failure on selected candidate.
One TestAdmin arm was confirmed; one existing connected runner executed and stopped RED: `NORMAL_MATERIALIZATION_DURING_APPROACH_NOT_OBSERVED`.
Same Player object268489445 remained worldPresent, but regionCanKnow/clientVisible stayed false; committed/live points diverged while runner approached. Evidence in `.phantom-local/m1-005-connected-1ae855b5-db2e-47d0-9eb2-bd1e4fe0cf2b/`.
Runner confirmed TestAdmin origin restored. TestAdmin `.playtest off` confirmed by read-only Pilot state OFF.
After run PLAY: READY1280/RETIRED8720, FAILED_REPLAN_REQUIRED13 (stale1, item conflict canonical12), reserve digest unchanged; CONFIG/ownership PASS.
Stop at connected RED boundary. No second run, location-specific fix, new proof task or M2.
Source scope: 10 production Java + 3 focused test Java + task docs/STATE; unrelated original checkout hunks preserved.
SQL differences in managed TEST worktree are only EOL and unstaged; no schema/world data mutation.
mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.

## Previous continuation

Status: PLAYABLE1280_RUNTIME_PREPARATION_RED_M1_OPEN; final manual gate REQUIRED.
Последнее продолжение: continuation-playable-1280/RESULT.md; TASK/DESIGN/PLAN реализованы в bounded scope.
Code/deployed source: ca2dbc753106d165dc73a1b03ba89aadc69f917e; ordinary push origin feature/phantom-world выполнен.
Game SHA256:7A6332A28FFBB6B864C0D7BDDF783958D84A2E7E008DF9757AC6618580E90706.
Clean managed detached ant jar PASS; чужие MaterializationService/ClanDirective/Multiparty hunks сохранены и исключены.
TEST aggregate204/204 PASS; после последнего owner guard targeted core14/14 + ecology6/6 PASS.
Resize10000→1280→restart1280→3000 сохраняет identities/pending; restart retirement writes0.
Один ecology worker: focus12/ordinary4, общий4/16/100ms, admission≤8 с реальным headroom/soft reclaim.
Physical geometry сохранена; retired paused; periodic horizon без human/effective WARM gate; native owner не simulated.
Native TEST4320 calendar minutes:75 productive windows1108 intervals +381 competing ordinary intervals.
Ready15042ms/firstClaim10ms; тот же Player, native displacement4691.17, farm arrival, stock AutoPlay damage4.
Один общий review; READY pause и outer-save-after-inner-commit budget defect исправлены, focused regressions PASS.
Один controlled owned restart после coherent native PLAY dump и backup JAR/INI/manifest.
Backup: artifacts/local-play/m1-005-backup-ca2dbc7-20260929-020030; PLAY.sql63397691 bytes; private, не в Git.
INI/manifest1280/64/128/100, maxScheduled10000; owned Login24428/Game32512 RUNNING; CONFIG PASS.
PLAY23:04:03Z: READY1280/RETIRED8720; retirementFailures пуст; retired pending6641 сохранены.
Reserve ecology/history hash одинаков23:02:51Z/23:04:03Z:0659577b3b92f7708bebb3e4688a0d8ac9065714654e6736064b8343dffd1a68.
Preparation RED: activePending977, initialIncomplete165, FAILED_REPLAN_REQUIRED590, calendarGap17189101.
Причины: model.object_cap_indivisible354, catchup.authority.unsupported121, planner.target_or_route.absent114, stale1.
Canonical content/model failures не заменены READY. Полная готовность рабочего населения не подтверждена.
Новый arm/CONNECTED run не запускались; TestAdmin не перемещался; native life/census/short return PLAY NOT_OBSERVED.
Saved Pilot ARMED_IDLE старого Game29824; current Game32512 без нового consent run. Stop guard отверг stale-session.
На предписанном TASK runtime RED остановились до arm; M1 не GREEN, required gate не принят автоматически.
Owned runtime остаётся RUNNING с автоматической bounded preparation; новый gameplay run не разрешён этим результатом.
Raw logs/probes/backup только ignored local storage. Schema/world data/providers/навигационные workers не менялись.
Exact publication scope:8 code/runner paths + RESULT/STATE, перечислены в RESULT; Git разрешён TASK.
mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
M2 не начат.

<!-- Previous state retained in historical continuation-after-stall/RESULT.md. -->
