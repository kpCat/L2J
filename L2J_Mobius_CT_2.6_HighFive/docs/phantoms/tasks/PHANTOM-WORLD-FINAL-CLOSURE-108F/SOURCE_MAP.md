# SOURCE_MAP — точные исходники, приоритетное чтение

Пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`, кроме `AGENTS.md` в Git-root. Проверены на commit `58c76fc1e7f02bfe8e019c72b521284f56946c6c`. Переоткрыть при свежем HEAD; строки приблизительные.

| Path | Зачем |
|---|---|
| `docs/phantoms/reports/PHANTOM-WORLD-RESTART-CLOSURE-108R3D.md` | Последняя A success/B blocked, private index, хеши |
| `test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java` | `addObservers` ~677, World loop ~165, cohort checks, native logout+STOP |
| `test/java/org/l2jmobius/tests/phantoms/PhantomWorldCohort108R3Checks.java` | `capture` ~85, `evaluate` ~20, legacy `CharacterFrame` 10 slices |
| `test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java` | Authority namespace validation before JDBC; отдельный 108F от 108R3D |
| `tools/phantom-runtime/Task108R3DRun.ps1` | Existing private stage/build/G0/native/AcceptA/B/exit0/negativeRows guard; **read-only шаблон**, не изменять |
| `tools/phantom-runtime/Test-Task108R3Process.ps1` | `-WrapperPath`, stub exit0/7 negative test; переиспользовать |
| `tools/phantom-runtime/Task104Census.java`, `Task105Census.java` | Stable 10-slice SELECT-only pre/post, `negativeRows`, legacy protected formats |
| `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` | `operatorAdmissionProfile` ~2238, `operatorLocalityTarget` ~2257, `operatorSetupLocalityTarget` ~2270, `onlineHumanPoints` ~3483 |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java` | `findProfile` ~339, topology registration authority |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java` | Physical human demand/prewarm, locality refreshing, preparation input |
| `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java` | `rebalanceAdmissionLocked` ~870, `admissionSnapshot` ~1762 (stale→zero), `admissionProfile` ~1785 |
| `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java` | schedulingSnapshot/current ~1367–1395, `permitted` ~1428, demand/preparation ~270–325 |
| `java/org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.java` | SQL `FOR UPDATE`, skill+SP transactional commit and runtime reconcile, `OperationResult.SUCCESS` ~157 |
| `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java` | canonical `learnClassSkill` ~218, operation result/metrics ~255–350 |
| `java/org/l2jmobius/gameserver/phantoms/progression/L2jPhantomLearningJourney.java` | native trainer interaction and learning result ~270–292 |
| `dist/game/data/stats/players/skillTrees/StartingClass/DwarvenFighter.xml` | class53, skill141/1, `levelUpSp=310` |
| `dist/game/config/Custom/PhantomPlayers.ini`, `dist/game/config/OwnerConfig.json` | protected owner values; **не менять**. Test overlay только в stage |

Не превращать table в разрешение менять перечисленные production-файлы: исходно **только чтение**, правка максимум одного причинного owner файла по доказательствам и бюджету `TASK.md`.
