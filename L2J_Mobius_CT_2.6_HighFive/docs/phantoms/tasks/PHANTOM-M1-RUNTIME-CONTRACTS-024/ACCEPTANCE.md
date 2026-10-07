# Итоговые флаги — без бумажного GREEN

ENGINEERING_PASS: собственные B01–B08/C01–C05/A01–A08/D01–D07 и затронутые native regressions
прошли, каждый неизменённый relevant FAIL объяснён сравнимой базой или исправлен. Build
сам по себе не engineering. Expected-negative safety cases отдельно, не gameplay PASS.

RETALIATION_PASS: actual active-NPC nested action positive + stale/sealed negatives.
POSITION_STORE_PASS: saved exact off-area native snapshot + retained native-only proof.
EARNED_PERSISTENCE_PASS: для ВСЕЙ finalcohort quiescent expected snapshot == postFINALIZE SQL
(EXP/SP/expBeforeDeath/vitals/class/XYZ/heading/inventory/skills), no pendingstore/retained.
SHUTDOWN_PASS: healthy earned shutdown complete, correct dependency order, no force.
LOCAL_RECOVERY_PASS: после фактического безопасного replanning игра продолжается.
FARM_A_PASS/FARM_B_PASS/COHORT_PASS: правила SCENARIOS полностью, не одна удачная цель.

TASK_RESULT=GREEN только если ENGINEERING+RETALIATION+POSITION_STORE+EARNED_PERSISTENCE+
SHUTDOWN+LOCAL_RECOVERY+двеFARM+COHORT PASS и sameDB restart verification успешен.
Иначе BLOCKED с точной причиной/FAILED при собственном нарушении safety, без self-GREEN.

SERVER_M1_PASS дополнительно требует LOOT/DEATH/SOFT_RETURN/RESTART/CRASH_MATRIX PASS.
Не выполненный gate = NOT_RUN/UNKNOWN, не defaultTrue. При SERVER_M1_PASS=true и REALnotrun:
M1=WAITING_FINAL_CLIENT. Иначе M1=OPEN. REAL_FINAL_PASS=NOT_RUN ночью всегда.
M1_CLOSED требует отдельной реальной клиентской приёмки на проверенном SHA.

RETAINED0 после пустого restart не исправляет retained5 на первом stop с earnedactors.
Допустимо здоровое сохранение с POSITION_REQUIRES_NATIVE: это POSITION_STORE PASS,
но постоянный отказ возвращаться к игре запрещает FARM/SERVER pass.
Data/context/phase/time budgets или sampling thresholds не ослаблять для получения успеха.
