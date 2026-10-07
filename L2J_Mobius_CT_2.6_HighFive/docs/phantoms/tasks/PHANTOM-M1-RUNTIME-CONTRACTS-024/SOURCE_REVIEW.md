# Final source review024

Реализованы B/C → A → D и четыре дополнительных B-перехода с собственными RED. Код и relevant targeted checks проходят, но реальный whole-cohort product outcome не достигнут. Engineering results не повышают M1 до WAITING_FINAL_CLIENT.

B: capture сохраняет exact native XYZ вне anchor; POSITION_REQUIRES_NATIVE отделён от background simulation eligibility, прежние ordinal/schema сохранены. B09/B10 возобновляют только exact pending owner и повторно проверяют factual arrival. B11 использует existing stale-PENDING recovery. B12 возвращает только проверенный stock pre-World GeoEngine Z transform к committed native SQL Z, с guards identity/progress/owner/epoch/transition; anchor не подставляется.

C: topology/index прекращаются после native store/POST_STORE/lifetime release; blocking shutdown вынесен из configured/instance monitor под узкий shutdown claim. A: original attack/cast получает fresh root admission и не наследует чужую EARNED obligation; stale owner/epoch и delayed guards сохранены. D: локальная геометрия и capped alternatives, useful arrival до старого nonterminal deadline, bounded exact route exclusion вместо whole-area exclusion.

Новые API проверены исходниками; stock ThreadPool/EventDispatcher/combat engine/SQL schema и PlayerNativeEvidence thresholds не переписаны. Production11/18, новых production helpers0. Изменены8 test paths; дополнительных существующих test paths2: HeadlessPlayerTestEnvironment и NativeContextHandoffSuite; Contracts024DatabaseLane — новый test-only helper. Предыдущая формулировка ledger «third additional existing» считала новый helper, эта классификация уточнена здесь.

RED/GREEN и точные routes: FIX_LEDGER.md и raw RED_*/GREEN_*/REG_*.log. B3/3; C3/3 и shutdown8/8; Aactual HitTask2/2, stale3/3, dynamic6/6; Dlocality1/1 иarrival/route2/2, current-intent10/10; B09/B10/B11/B12 отдельные RED/GREEN, owned matrix27 PASS.

Сохранены неуспехи: GREEN_C01 некорректный repeat-stop fixture; REG_A_LIVING_FULL N02 совпадает с unchanged required base; случайный unsupported focus запустил full position suite6/12, это не full PASS. Старые a43 final/probe failures и pre-baseline ENVIRONMENT_GAP не удалены. Package self-check не считается server gate.

Оставшиеся blockers имеют fresh runtime evidence: stalled/missing natural cohort; RESERVED kill-event drain timeout; persisted AFTER_NATIVE VERIFY_PENDING452; death-return farm не появился; Synthetic return uncertainty. C dependency fix устраняет прежний NOT_RUNNING index, но не доказывает отсутствие прочих lifecycle defects. B exact native capture устранён локально, end-to-end recovery ещё открыт. Их нельзя закрыть снять guards/reset receipts/увеличить thresholds. Frozen code сохранён; full8 episodes и planned2 crash caps исчерпаны. Это остановка по TASK024_CONTRACT, не старому4repair limit. Не запускается следующая задача.


Reviewed paths:

- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/Creature.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomNativeContext.java`
- `L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/player/PhantomM1DynamicRecipientChecks.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomContracts024DatabaseLane.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLivingWorld023Suite.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomNativeFarmContinuation022Suite.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomRuntimeContracts024Suite.java`
- `L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java`

Source unchanged from frozen ca3, confirmed bounded exact diff. Targeted RED/GREEN were run against actual required-base/candidate builds. This is a self-review, no subagents. Concurrent owner/epoch/store guards and restore pre-World fencing remain. Code review is not end-to-end acceptance.
