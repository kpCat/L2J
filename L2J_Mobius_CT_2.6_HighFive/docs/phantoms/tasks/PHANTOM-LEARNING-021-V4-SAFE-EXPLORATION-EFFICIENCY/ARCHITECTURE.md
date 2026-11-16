# Архитектура V4 Experiment021 — минимальный additive seam

## Исходные классы

- `java/org/l2jmobius/gameserver/phantoms/learning/TacticalPolicyV3.java`: существующий `damage(...)`, V4 TEST-only `actionValueTest(...)`, `actionValueChoice(...)`, stock fallback; старый TrainExplorationV1. **Главная точка арбитража**.
- `ActionValueV4.java`, `ActionLearningV4.java`, `ActionFeedbackV4.java`: уже работают, parent checkpoint179, 128 коэффициентов, event ledger4096, batch256, immutable codec. **Не переписывать.**
- `LegalActionV3.java`: определены DAMAGE/HEAL/...; trainable только DAMAGE/HEAL, V4 эксперимент021 — DAMAGE без расширения family.
- `PhantomLearning020V4Native.java`: owner lifecycle, fresh class94 reconstructed from sealed016, natural recovery180, placement/Geo, CUT, original settlement, native+fit+cold. Переиспользовать с минимальным opt-in extension, а не копировать третий GameServer runner.
- `PhantomAutonomousTrainingNative.java`: `soloSource020`, `reproduceSolo020`, `positionAcquired016`; методы старых016/020 сохраняют байтовое поведение.
- `TrainingOwner.ps1`, `Training.ps1`: one-step own root/worker/process proof; reuse bounded 020 wrapper, **новую ночь и отдельный scheduler НЕ писать**.

## Разрешённый scope (только необходимые изменения)

**Новые production Java:** до двух классов в `java/.../phantoms/learning/` — `ActionExplorationV4.java` (pure chooser with immutable decision record) и `ActionExperimentReceiptV4.java` (versioned metadata/exact bindings), либо эквиваленты. Никакой дополнительной модели.

**Точечно изменяемые:** `TacticalPolicyV3.java` — только TEST-only opt-in/arbitration at DAMAGE, V3/legacy preserved, production remains OFF. `PhantomLearning020V4Native.java` — переиспользуемый native TEST adapter; `PhantomLearning001ShadowNative.java` и `PhantomAutonomousTrainingNative.java` — только если нужны доказанные недостающие тестовые швы. До одного нового `PhantomLearning021V4Exploration.java` focused test entrypoint. `TrainingOwner.ps1`, `Training.ps1`, `Test-Training.ps1`, `build.xml` — маленькие изменения только для21 script and targeted test. Task docs + `docs/phantoms/reports/PHANTOM-LEARNING-021.md` +1 roadmap. Никаких иных файлов без owner agreement.

## Фиксированный контракт для будущих 022/023/024

Выделить **одну узкую, версиионную внешнюю границу** на которой 022 сможет собирать V4 decisions/receipts, а 023/024 — добавлять семейства действий *без изменения формата уже опубликованной истории*: 

`DecisionV4V1` → `ExecutedNativeEffectV4V1` → `TrainingFeedbackV4V1` (точные имена Codex может выбрать в рамках max2 новых Java классов). Обязательно поля: `schemaVersion`, `family`, `policyId`, `actor identity/epoch`, `target identity/epoch`, `episode/event`, `knownActionSet`, stock/selected, `selectionMode`, `propensity`, source/model SHA, native receipt SHA, outcome known/unknown, split, bounds/deadline. До CUT фиксировать policy/seed; dynamic decision формировать до кастa после CUT, buffered, атомарная публикация после drain и точная привязка receipt/event. Не делать disk fsync в native hot path. Метаданные/encoding versioned, immutable; **старый V4 codec не менять**.

Новые family-specific adapters впоследствии регистрируются через проверяемую версию и реализуют собственные эффект/label contracts. Если новый family отсутствует → штатный fallback, не ошибка тренера. В021 достаточно доказать family=DAMAGE и тест отказа unknown family. Не строить сейчас универсальный плагин-фреймворк.

Ограничение `ActionValueV4.MAX_EVENTS=4096` и `ActionLearningV4.fit<=256` не исчезает от одной ночи! Для022 зафиксировать требуемую *стратегию поэтапной append-only сегментации/rollover* с idempotent cursors и без переприсвоения credit; не размыкать caps и не отбрасывать события задним числом в021. `StepResultV1`/текущий one-step proof должен содержать next-compatible source/snapshot SHA/actual updates and model parent. 021 не запускает автоматически новый step.

## Production integration — честная граница

Текущий `actionValueTest()` работает **только** при Java `-Dphantom.learning.train` в отдельной TEST; обычный сервер не умеет просто «включить V4». Production rollout в дальнейшем требует allowlisted safe loader, atomic immutable weight swap, version pin, SHADOW/canary ON, fallback/rollback, concurrency and p99 latency guards, dedicated permission. В021 production остаётся **default OFF**, никакого hot reload/prod flag. Совместимость future family adapters и replay tests — обязательны.
