# READ_SET064 — короткая карта исходников, не повторять всю историю

Один ранний read-first ≤12 exact files, до первого patch ≤6 targeted repository searches, большие Java читать bounded `rg -n`/точечные диапазоны. Исторические полные отчёты 001–060 не перечитывать. Новые exact files — только при установленной причине, фиксируй path+symbol.

1. `AGENTS.md` в Git root; этот `TASK.md`, `AUTHORITY.md`, `RUNTIME_INPUTS.md`, `ACCEPTANCE.md` — исполняемый scope.
2. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md` — история/принятые ограничения.
3. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-WORLD-CAREER-061.md` — последний native ordinary/профессии.
4. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-M3-M7-CAMPAIGN-059.md` — M4–M6 blockers, особенно raid25199/Rift.
5. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M2-M7-CAMPAIGN-058/ACCEPTANCE.md` — S2–S7.
6. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/architecture/{MATERIALIZATION_LIFECYCLE_CONTRACT,DECISION_GOAL_PLAN_CONTRACT,COMBAT_KERNEL_CONTRACT,PARTY_COORDINATION_CONTRACT}.md` — только нужные bounded paragraphs.
7. `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/{Runtime.ps1,Build-Package.ps1,Campaign058Ownership.ps1}` — только entrypoint/schema guards (старые профили exact OWN!).
8. `L2J_Mobius_CT_2.6_HighFive/dist/{game/config/GeoEngine.ini,game/config/Custom/PhantomPlayers.ini}` — фактические default vs новый candidate.

Символы дальнейшего поиска по потребности: `PhantomSystem`, `PhantomPopulationManager`, `PhantomMaterializationService`, `PhantomHistoricalBackgroundService`, `L2jNavigationBackend`, `PhantomCombatService`, `PhantomPartyCoordinator`, `PhantomRaidDecision`, `PhantomRift*`, `PhantomQuestInstanceService`, `PhantomAutonomousCareer`, `L2jPhantomSupplyJourney`, `PhantomBackgroundTransaction`. Не создавать новые аналоги, пока не установлена проблема в этих owners. Публичные статусы read-only; private `.phantom-local`/release receipts читать только exact known path, не обходить миллионы файлов.

Проверки: существующие `Invoke-Campaign059Test.ps1`, `Invoke-Campaign059Regression.ps1`, `PhantomTestLauncher`, Ant target names брать **из актуального** `build.xml`; `ant verify` содержит DB targets и не выполняется без own explicit safe schema. Вызов BUILD JAR из чистого HEAD, без затягивания приватного product class из старого release.
