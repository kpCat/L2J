# Проверенные источники coordinator
Все пути под `L2J_Mobius_CT_2.6_HighFive/`, ref `07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6`.
- docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/RESULT.md — failure flags, crash452,
  AFTER_FINALIZE187, whole cohort limitations, first retained stop.
- тот же каталог: COHORT.md, FIX_LEDGER.md, HANDOFF.md.
- evidence/FINAL_R4_SCENE_A/cohort-result.json —110 ACTIVE/SEALED/noPending/noIncident,
  exact native counters and expiredREGEN. Это не доказательство first capture exception.
- player/PhantomNativeWorkScope.checkpoint, drain, onQuiescent (под gameserver/phantoms).
- background/PhantomBackgroundService.captureVisibleArrival, recoverAbandonedMaterialization.
- background/PhantomHistoricalBackgroundService.ensureBaseline/begin/advance.
- background/PhantomBackgroundTransaction.reconcileVerifyPending — owned resolver first.
- player/PhantomMaterializationService.registerDrainRetry — reuse callback publish pattern.
- background/L2jPhantomBackgroundAuthority.requireSupportedPlayer — nativePersistence still
  checks combat before the nativePersistence early return; only a candidate producer.
- task024/Contract024Agent.java — existing observer hook uses global nativePlayers scan;
  do not silently attribute runtime defects to it, but avoid repeating this lock coupling.

По данным отчёта отсутствует exact first producer некоторых live stalls; такие места
помечены UNKNOWN в ROOT_CAUSES. Никакой «полной гарантии» из статического чтения нет.
При запуске verify base; если удалённый commit другой, не выдумывать совпадение.
