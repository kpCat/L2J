# Факты, проверенные по GitHub на старте018

Репозиторий: `kpCat/L2J`, ветка `feature/phantom-world`. HEAD при выпуске `048ed764b9e09dfe617f569f4bfa705bed297a44` — принятие Goal017 `ACCEPTED_PARTIAL`, кодовых изменений этим коммитом нет.

1. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`: `Runtime.outcomeActors` (~999–1010) читает `before` и `current = PlayerNativeWork.observationEvidence(player).snapshot()`. `known` требует не-null current, совпадающий native epoch, отсутствие overflow в обоих snapshots и `firstUnprovenReason == NONE`. При `!known` EXP/SP оба становятся null.
2. Там же уже создан `terminalEvidenceObserver017`, который получает существующие previous/current snapshots; он **не должен делать дополнительные снимки**. Проверить его payload и точку включения в `PhantomAutonomousTrainingNative.collectEpisode` (~1989).
3. `java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java`: `UnprovenReason` имеет OWNER_FAILURE, INVALID_VALUE, INVALID_TARGET, TARGET_CAP, COUNTER_OVERFLOW, TIME_REGRESSION, INVALID_PHASE, PHASE_DEADLINE, COMBAT_ACCOUNTING. `snapshot()` может обнаружить истёкшую фазу и отметить PHASE_DEADLINE. Не объявлять её причиной для class94 без native evidence.
4. `java/org/l2jmobius/gameserver/phantoms/learning/TerminalRewardReceiptV1.java` (~140–142): если живы актёры и нет смертей, неизвестные EXP или SP всего Party блокируют terminal. Это действующий канон; не превращать UNKNOWN в 0.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` (~2157–2173): после017 для *будущих* эпизодов сохраняются `terminal-validation017.json`, категоризированные per-event refusals и snapshot facts. Старые данные016 не дополнены задним числом.
6. Отчёт017: все17 Party native kills, у одного участника class94 в каждом EXP/SP null; 71 исключённое действие. Первый thrown validator и первопричина неизвестны из исторических данных. Оставшиеся13 исключений Solo не входят в018.
7. V2 пригодность уже вычислена: TRAIN7/TUNING6/AUDIT4 при16/8/8. Даже полная законная реставрация Party старых данных даёт не более13 TRAIN и7 AUDIT; **FIT не является целью018**.

Путь к canonical report: `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-017-PARTY-REWARD-AND-CYCLE-CLOSURE/RESULT.md`.

**Ловушка:** `source` код может быть уже правильным; проблема может быть в реальные gameplay/owner/phase constraints. В таком случае НЕ менять production алгоритмы без выбора владельца.
