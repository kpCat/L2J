# READ_SET065 — адресное чтение

Не загружай полностью несколько тысяч строк `PhantomSystem`, `PhantomScheduler`, `PhantomPopulationManager` или старые 058–064 отчёты. Прочти только нужные symbols, bounded ranges, и relevant acceptance. База — текущий HEAD основной ветки; root `AGENTS.md` и `L2J_Mobius_CT_2.6_HighFive/Agents.md` обязательны.

**Initial exact files / bounded sections:**

1. `docs/phantoms/reports/PHANTOM-M1-M7-CLOSURE-064.md` — только M1, preservation и final fact.
2. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md` — authority и G4/G6.
3. `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — `PhantomHumanLocalityControl`, `installPreparationDemand`, `installSoftReclamation`, `retentionFacts`, `onlineHumanPoints`.
4. `.../topology/PhantomHumanLocalityControl.java` — `onPulse`, `isCurrentLocal` и demand facts.
5. `.../topology/PhantomNativeLocalityEnvelope.java` — native WorldRegion geometry.
6. `.../activity/PhantomMaterializationRetentionPolicy.java` — `Hold`, `reclaimable`, `oldestSoft`.
7. `.../activity/PhantomReconcileFirstActivityPort.java` — readiness, reclaimSoft.
8. `.../PhantomScheduler.java` — `executeBoundary`, `requestSoftReclamation`, retry.
9. `.../population/PhantomPopulationCatalog.java` — `evaluate`, `parseSchedules`, `markOccupied`.
10. `L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v2.xml` — schedules only.

Дополнительные файлы только по доказанному blocker: `PhantomPopulationEcologyService`, `PhantomPresenceRegistry`, `PhantomMaterializationService`/native lifecycle, nearby M1 affected suites. Искать не более6 адресных symbols до первого патча. Использовать существующую `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md` и TASK065 acceptance. Логи больших проверок — в `.phantom-local/task065/`, в чат возвращать только exit/status и причинные выдержки.
