# Регрессии и продуктовая проверка

## R01–R08: холодный pending и durable границы
R01 AFTER_NATIVE: canonical AFTER exact, owned receipt+VERIFY_PENDING, отсутствует Player.
    Обычный production startup/pulse достигает resolver; pending исчезает; values exact.
R02 AFTER_PREPARE: BEFORE exact; stock before-resolution, без выдуманного after progress.
R03 AFTER_FINALIZE: pending отсутствует; второй процессrestart идемпотентен.
R04 canonical NEITHER/foreign profile/item/skill mismatch: fail closed, receipt retained.
R05 live/retained owner или World/autosave conflict: cold recovery не крадёт identity.
R06 owned target MATERIALIZED (arrival), no owner: correct finalization→abandoned lifecycle.
R07 catchup PENDING и legacy FAILED baseline.conflict: exact resolver до eligibility;
    другой FAILED/request/window mismatch не принимается.
R08 compatibility: POS1/POZ1/schema/enum ordinals unchanged; original owned27 cases.

## R09–R20: live checkpoint
R09 actual arrival capture failure до writes: first class/message/phase известны;
    eligible temporary actor не остаётся бесхозно SEALED.
R10 phase witness absent: conservative failure, не automatic reopen.
R11 inventory flush был/исход неизвестен, pendingfalse: никогда no-write bypass.
R12 exception послеPREPARE: same receipt exact control resume, no second award.
R13 exception после native beforeFINALIZE: ordinary roots blocked, finalize once.
R14 exception послеFINALIZE в index: no repeat native writes; publication+permit recovery.
R15 safe preflight stale goal: bounded replan; captured old callback не двигает actor.
R16 owner/epoch заменился: control stale cancels without touching replacement.
R17 death и permanent cleanup во время temp checkpoint: cleanup wins, no resurrection
    by generic reopen; earned progress preserved.
R18 repeated requests: one control per profile/epoch/key, no unbounded pending map.
R19 success: actual AutoPlay/AutoUse ticks resume, >=5 native farm cycles in sameepoch.
R20 raw nativeXYZ/EXP/SP/items exact; no counter replay or anchor coordinate replacement.

## R21–R28: callbacks, timing, observation, end-to-end
R21 stock ON_ATTACKABLE_KILL delayed earned callback starts/finishes while drain waits.
R22 две simultaneous control operations не занимают все нужные earned workers.
R23 handler ещё не due: expectation bounded waiting, not cancellation or false corruption.
R24 genuine stuck/body throws: reported retained, no silent PASS.
R25 collector hook doesn't acquire other actor locks/scan World/perform fsync under locks.
R26 repeated healthy stop and full sameDB restart; pending0/retained0 exactly, not empty run.
R27 synthetic heartbeat watcher survives one long request; TTL/session bound не увеличивать.
    Разбить полное leave/return в заранее измеримый session budget, не патчить guard вслепую.
R28 native death→lawful recovery→farm и soft leave→background→return→farm на same commit;
    если independent boundary, NOT_PASS и evidence, не замена unrelated code.

## Сохраняемые обязательные проверки
Native original-entry/retaliation OPEN+SEALED+stale controls TASK024, lawful participant
boundary/dynamic suites TASK023, current-intent+local-recovery TASK021/024,
TASK018 handoff15, TASK020 admission18, ecology30/handoff6, scope closure/timer suites,
owned-store27, server-shutdown suite. Использовать актуальные suite routes Run-Checks024;
количество может увеличиться новыми cases. Новый relevant regression → не GREEN.

## Natural scenes
Две сцены по360–420с на одном frozenSHA/config. ≥4 естественных eligible actor;
первые2 primaries до baseline. Вся группа учитывается, class coverage melee+mage.
Каждый primary>=5 sameepoch farm cycles с actual EXP/SP; остальные повторяемый useful
progress по прежним evaluator rules. Последние120с — продолжение работы, а не ранние
награды+пять минут простоя. Cohort sampling≤5с, bounded pagination не теряет actor.

Missing/changedepoch в steady scene не превращать в PASS. Для death/remat отдельные
records с lawful transitions; не смешивать их with sameepoch farm proof.
Инцидент/overflow остаётся FAIL того эпизода, новый исправленныйSHA получает новый episode.
No grants, no passive mobs, no select/attack Phantom, no injected AI.success.

## Whole-group save proof
Expected данные от sealed checkpoint hook ВСЕХ выбранных identities/epochs. SQL reading
после stop и до дальнейшего background work. Затем два sameDB restarts, receipts resolved,
без дублирования items/EXP. Произвольные active live vs SQL различия — INCONCLUSIVE,
не доказательство loss и не повод чинить цифры из лога.
