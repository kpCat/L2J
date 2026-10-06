# Конечные критерии

ENGINEERING_PASS:
- R01–R15, affected regressions, clean committed build;
- current goal синхронизирован до исполнения, stale actions не выполняются;
- terminal outcome не становится endless retry;
- allowed local plan publication атомарен и совместим с exact foreground owner;
- water protection не ослаблена, исторические rewards/cursors не фальсифицируются;
- guarded TEST восстановлен, никакой записи в PLAY.

RUNTIME_FARM_PASS:
- один natural same-epoch native Player выполняет>=5 полных farm cycles с EXP/SP;
- не управляется вручную, AutoPlay/AutoUse/PlayerAI остаются штатными;
- actual sample duration/timestamps опубликованы, overflow=false;
- persistent/runtime revisions совпадают при исполняемых действиях;
- видно отсутствие endless terminal retry и запрещённого water entry в этом episode;
- cohort до4 отражён честно, даже если соседний actor заблокирован;
- logout/save и graceful shutdown подтверждены.

TASK_RESULT=GREEN только при ENGINEERING_PASS и RUNTIME_FARM_PASS.
BLOCKED — необходим выход за scope, неизвестная ownership/geometry boundary либо runtime
не достиг критериев. FAILED — нарушен контракт/проверка не прошла. No auto continuation.

M1=OPEN при любом результате TASK021. М1 ещё требует полного death/recovery,
soft-dematerialization/background/rematerialization, cohort/повторяемости и финальной
приёмки. Не скрывать прежний NATIVE_WORK_SELF_DRAIN22/23 в общем списке M1 gaps.
