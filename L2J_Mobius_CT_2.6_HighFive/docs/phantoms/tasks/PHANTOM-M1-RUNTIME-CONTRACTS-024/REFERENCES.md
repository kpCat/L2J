# Источники координатора

Read-only source review: точная ветка `experiment/m1-candidate007-observe008`, HEAD `819e3cea5baa64e6c429e450c8fc296874e37d1c`.
Это результаты предыдущего TASK023 и исходник, не запуск исправленного TASK024.
User report: «Вставленный Markdown(5).md», строки1/3/8/103–105/122–131; его содержание
подтверждено remote RESULT/COHORT/LIFECYCLE_EVIDENCE. Утверждённого M1PASS там нет.

Repository paths при BASE:
- docs/phantoms/tasks/PHANTOM-M1-UNATTENDED-LIVING-WORLD-023/RESULT.md
- .../COHORT.md; LIFECYCLE_EVIDENCE.md; SOURCE_REVIEW.md; FIX_LEDGER.md
- .../evidence/FINAL_SCENE_A/cohort-result.json
- background authority capture/exactAnchor: nativePersistence ещё требует geometry.
- PhantomBackgroundService.java: captureVisibleArrival/prepareStore/afterStore (около924–1120).
- PhantomNativeContext.java: Eligibility/simulationEligible/encode/decode.
- PhantomOwnedStoreIntent.java: allowedtargetstates/точные before/after bytes.
- Creature.java: original doAttack≈1036 и earned HitTask publication≈1690.
- PlayerNativeWork.java: run/runAtNativeWriteBoundary/runCombat/captured-parent fence.
- PhantomTopologyService.java: register/update≈272–287 требуют runningView.
- PhantomSystem.java: beginStop topology до materialization shutdown в RUNNING/FAILED.
- PhantomVisibleFarmTravel.java: journeydeadline/arrival/standpoint≈190–310.
- PhantomHistoricalBackgroundPlanner.java: isVisibleLocal/addLocalCandidates.

Номера source line — навигация, не идентичность. Использовать точные methods + SHA.
SOURCE_MAP содержит Git blob ids для непосредственно просмотренных файлов.
Не использовать default branch search для проверки experiment реализации.

Remote base:
https://github.com/kpCat/L2J/tree/819e3cea5baa64e6c429e450c8fc296874e37d1c
