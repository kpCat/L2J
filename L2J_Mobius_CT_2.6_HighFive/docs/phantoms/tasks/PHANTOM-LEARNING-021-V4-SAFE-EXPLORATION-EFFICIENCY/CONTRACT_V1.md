# Граница021 для будущих022–024

Production V4 OFF. NIGHT_TRAINER NOT_READY. Этот документ фиксирует совместимость; следующий Goal не запускается.

## Decision → Intent → Effect → Feedback

`ActionExplorationV4.Plan`, schemaVersion1, фиксируется atomic create-only до CUT в `policy021.bin`; SHA входит в durable CUT. В плане policyId, seed, cap, epsilon, split TRAIN, episode, source image SHA, model SHA и полный упорядоченный acquired skillId:level набор. COVERAGE_TEST_V1: cap1, deterministic alternative, p=1. EPSILON_V1: p(stock)=1−epsilon, p(each alternative)=epsilon/(k−1), draw определяется seed+cursor; после cap фактический BASELINE p=1. Разные режимы не переименовываются в MODEL_REORDERED.

`Decision` (magic EX21, план версии1) содержит immutable observation с actor/profile/owner epoch, target/epoch, generation, offered legal alternatives, stock/selected skill+level, mode, propensity, draw/cursor, capturedNanos/deadline и reason. Сбор до возврата из chooser, без disk I/O. Bounded64; overflow лишает весь буфер credit. Весь лог проверяется по непрерывным cursors, единому плану, уникальным generations и cap.

`ActionExperimentReceiptV4.Intent` (magic IN21) добавляет exact native SourceOwner generation/operationKey и время. Его захватывает существующий `LearningPolicy.Observer.issued` внутри `LearningCastEvidence.dispatch` до `nativeDispatch.run()`. Owner не восстанавливается из позднего receipt. Нативный executor остаётся владельцем legality/MP/cooldown/LOS/Geo/lease; отказ или потеря owner не становится paid action.

После original settle/drain публикуются create-only atomic decision/intent файлы и `Effect` (magic NE21, schemaVersion1, family DAMAGE). Effect связывает SHA decision, raw native writer, original V3 row, original episode, eventKey и ActionFeedbackV4 fingerprint; фиксирует measured HP fraction, actual paid HP/MP и elapsed. Exact binding проверяет observation/offered/stock/selected, owner, split, episode/time; label остаётся исходным `ActionFeedbackV4.label()`. EXP/SP не входит в DAMAGE label. Неизвестный family отвергается, caller сохраняет штатный fallback. Никакой регистрации универсального plugin framework в021 нет.

`manifest021.json` фиксирует полный набор decision/intent файлов, SHA и количества paid records; `native021.complete.json` атомарно связывает manifest и весь native proof. Неполный или подменённый набор не получает fit. Summary mode/propensity/stock/chosen сверяется с immutable bytes, количества experiment/control/model пересчитываются. Native raw writer дополнительно проходит original020 raw/action/owner/effect проверку.

Старые outer filenames `preflight020/cut020/episode020/native020/fit020/cold020/safety020` намеренно переиспользуются только внутри нового `train021`; исторические roots не изменяются. Новый checkpoint называется `candidate021-<SHA>.bin`, использует неизменённый V4 codec. Future consumers должны проверять наличие021 seal и версии, а не выводить experiment из имени старого файла. Старый020 без opt-in сохраняет собственный codec/parent161/порты/маршрут.

## Метрики и независимая оценка

Измеренные DAMAGE HP/MP/time/fraction приводятся отдельно по mode и skill. Actor after-state, interruption, other costs — UNKNOWN без отдельного native измерения. Kill/death — отдельные episode facts, не причинная награда одного cast. COVERAGE не randomized sample, не подходит для IPS epsilon. Невыполненная альтернатива — UNKNOWN. Один эпизод не доказывает superiority; для сравнения нужны независимые сопоставимые contexts, достаточная выборка и variability. TUNING/AUDIT не получают exploration или gradient credit.

Новые HEAL/CONTROL/TANK/Party/PvP adapters потребуют самостоятельной versioned схемы известных/неизвестных outcomes и отдельного safety/label контракта. Они добавляются на этой границе без переписывания опубликованных V3/V4 bytes. В021 реализован только DAMAGE.

## Capacity и будущий ночной trainer

Текущие `ActionValueV4.MAX_EVENTS=4096`, `ActionLearningV4.fit` batch≤256 сохраняются. Достижение cap означает STOP, а не удаление старых event keys или reset модели. Ночной trainer сейчас запускать нельзя.

Для022 требуется append-only глобальный ledger сегментов: immutable input snapshot/event fingerprints/split, parent/new model SHA, atomic reservation/result и idempotent cursor; один event получает credit только один раз во всех сегментах. Партия≤256, preflight capacity до fit, interrupted reservation возобновляется по тем же bytes. До4096 нужен явный rollover в новый версионированный continuation contract с сохранением полного внешнего anti-replay ledger и lineage. Старый179 checkpoint не очищается, не конвертируется молча и не обучается повторно. Сами rollover/новый codec/scheduler в021 не реализованы.

CLI status021 публикует stepResultVersion1, actual updates/additionalUpdates, parentSha, checkpointSha и sourceSnapshotSha; automaticNextStep=false. Для production позже отдельно потребуются allowlisted loader, immutable atomic model swap, version pin, SHADOW/canary, fallback/rollback и concurrency/p99 guards с разрешением владельца.
