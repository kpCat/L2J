# Проверенные на GitHub точки кода

Проверенный commit: `f7638db7bbb5553a2d4f36ab065f0ae8d3a10b0d` (`feature/phantom-world`); перед работой fetch и убедиться, что источники не изменились.

Относительно `L2J_Mobius_CT_2.6_HighFive/`:

- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalReceiptV3.java` — verified DAMAGE/HEAL outcome; `reward` вычисляется из actual HP/MP/time, constructor подтверждает provenance. **Нет зависимости от EXP/SP.**
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java` — `Row` с validators, `encode/decode`, записи native events, связь `phantom_tactical_v3_event_episodes`, `settleEpisode`; здесь уже есть база для read-only import. Не использовать пересчитанный `TerminalRewardReceiptV1` для V4.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java` — законные DAMAGE alternatives (`damage`, ~стр.74–146), HEAL в текущей Party тактике. Имеется `Mode.OFF/SHADOW/ON`, 5ms safety window, fallback, критический heal. V4 — отдельный opt-in, не подменять существующий V3 mode/weights.
- `java/org/l2jmobius/gameserver/phantoms/learning/LegalActionV3.java` — enum содержит DAMAGE/HEAL/RECHARGE/CONTROL/и др., **но `trainable()` пока разрешает только DAMAGE/HEAL**, `receiptKind` прочим UNKNOWN. Не расширять бесконтрольно на другие families в019.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalObservationV3.java` — versioned field facts + known mask, 128 state dims.
- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalModelV3.java` — старый optimizer и weights, не изменять. Его `optimizeBatch` технически есть, но цель019 — компактный независимый V4 scorer.
- `java/org/l2jmobius/gameserver/phantoms/learning/ActionAdvantageV2.java` — требует `TRAIN>=16/TUNING>=8/AUDIT>=8` плюс Solo+Party, иных ограничений. Не смешивать с новым локальным action-value objective.
- `java/org/l2jmobius/gameserver/phantoms/learning/TerminalRewardReceiptV1.java` — отвергает Party при UNKNOWN EXP/SP в любом живом actor; не меняем V1.
- `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — TEST collector, имеющий native `TacticalReceiptV3`, `selection` и `reward017` evidence; подготовка016 привязана к 3 сценам/51 slots. Использовать его уже существующие type codecs, но НЕ запускать old Prepare/FIT.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — есть isolated owner `Runtime`, legal Solo start/stop, short native mechanics; legacy `main` имеет собственные восемь аргументов и lane; не запускать его большой historical routine как 019 smoke.
- `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `Test-Training.ps1` — существующие owned process/ports / OFF guards. Использовать/переиспользовать, не создавать второй сервер менеджер.
- `build.xml` — `phantom-learning016-test`, `phantom-autonomous-training-test`, `jar`. Добавить один focused `phantom-learning019-test`, не полный suite.

**Исходные факты:** в 016 подтверждены 51 CUT / 399 native paid effects / 29 kills, но только 27 terminal receipts. В 017 доказано, что отсутствие EXP/SP у class94 делает все Party terminal V1 непригодными; **V4 может использовать индивидуальный authenticated cast без того terminal, если cast самостоятельно полон и законен**. Не объявлять данные 016 достаточными до реального readback каждого source row.

**Сценарий read-only API:** authenticated `phantom_tactical_v3_events e JOIN phantom_tactical_v3_event_episodes l` с проверенным `receipt_sha`, `receipt_blob`, `split`, `actor_profile`, episode ownership; выбирает ограниченную порцию ≤256 записей, точные decoded native bytes. Названия SQL столбцов Codex обязан сверить по фактическому CREATE TABLE / имеющимся SELECT; никаких догадок и SQL write. Если нет реального эпизодного связывания или каст не прошёл verifier — отбрасывать с reason, не ковать sample.
