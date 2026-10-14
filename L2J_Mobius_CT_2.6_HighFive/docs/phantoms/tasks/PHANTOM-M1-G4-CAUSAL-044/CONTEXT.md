# CONTEXT044 — проверенные факты и две разные причины

Прочитай опубликованные task043/042 как источник истины, не подменяй их этой сводкой.

BASE044: `df295c79e0aa36428e88933352aeff629816ee83` (проверен публичный remote при подготовке). Последний Game semantic/JAR в043: `92400bebb9126ec4364ac57b05f8c453fe42690d`, SHA256 `5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c`; 043 **не менял product Java/config/data**. Точный hash после нового изменённого JAR вычислить заново, а не перенести из этой сводки.

G2 принят на этом JAR: A5=8/8/382.374с и независимая C24=8/8/381.682с, исходный CONTINUITY_V2 неизменён. Текущая jar-first матрица93/93; lifecycle11/11 и soft-return3/3 PASS.

**B2 из042**: profile367, чужие native attack-hit продолжались, HP180→20.058; ACTIVE_ACTION и hard удержание законны. Это НЕ дефект. Force release запрещён.

**C1 из043**: profile776, actual native earned + reward, legal soft release, terminal/owning commit, empty outstanding/timers, physical native отсутствует, new legal epoch upon return, но background77.229855с без полезного committed producer; вернулся DEAD/без useful work. G4=FAIL. Реальная плановая дата producer/decision/cadence не исследована полно; 77с нельзя считать proof неисправного фонового планировщика без вычисления фактического due.

**D1 из043**: profile1189, на последнем RUNNING-away снимке age129.842с, world-present old epoch, native outstanding0, AI=IDLE, no move/attack/cast/combat, HP200, physical demand/nativeVisible=false, retentionPins=[], входящих целей в ограниченном scope нет; одновременно scheduler ACTIVE/STABLE и `activeSources=1`. Это не называет `sourceKey` и не доказывает дефект: активный signal может быть законным до собственных `expiresAtNanos`. Nonatomic native facts не делать атомарными post-hoc. D1 G4=FAIL, источник signal UNKNOWN.

**43 conservation**: A1/A2/B1/C1/D1 owned native FULL_SAVE/GLOBAL_DRAIN и RUN_END/dropped0 PASS для собственных043 runs. Старые failures042 и G3 всей кампании не закрываются автоматически. Сырые данные сохранены локально в OWN/043, не перезаписывать.

**Публичный Java-код при подготовке**:
- `PhantomScheduler`: `Slot._sources` — Map key→`SourceEntry(_sequence,_signal,_expiresAtNanos)`. `find(profileId)` возвращает только `PhantomActivitySnapshot.activeSources` число. `submitSignal`, `withdrawSignal`, `expireSignalsLocked` и `find` защищены `_monitor`. Flight recorder пишет `SCHED_SIGNAL_ACCEPTED`, `SCHED_SIGNAL_COALESCED` с key/sequence.
- `PhantomRelevanceSignal`: sourceKey/sequence/requiredState/ttlMillis.
- `PhantomSchedulerPolicy.productionDefaults()`: backgroundCadenceMillis=300000, при обычном BACKGROUND используется jitter до900000. `PhantomScheduler.firstWorkDueLocked()` откладывает первую BACKGROUND работу; `PhantomSystem.productionWorkSink` вызывает readiness и `_decisionEngine.accept()`.
- `PhantomHumanLocalityControl`: LOCAL signal имеет TTL10000 и requested NEARBY_PERCEPTIBLE. `PhantomPerceptionProvider`: topology.local_chat / topology.combat / topology.targetability. Есть и иные возможные senders; ни один не назначать виновным без реального key и источника.
- `PhantomMaterializationRetentionPolicy` смотрит humanLocal/nativeVisible/REAL_PARTY/ACTIVE_ACTION/RECENT_HUMAN (60s), и это **не то же самое**, что `_scheduler` signal map. Разделить эти два разных механизма.

Пользователь требует быстрый приход в материальный мир и ленивый безопасный уход. Ленивый ≠ вечное удержание; нельзя ослабить невыполненные earned native действия ради доказательства. Другие пожелания пользователя (PvP, кайтинг, торговля, SemanticPack) — будущие milestones, не scope044.
