# G0–G9 — независимая приёмка Goal022

**Полный статус:** `FULL022_V4_AUTONOMOUS_SOLO_DAMAGE_TEST_READY` только если все G0–G9 доказаны в собственном isolated TEST. Любой неполный пункт = честный `PARTIAL022_<CAUSE>`/`BLOCKED022_<CAUSE>`; не подменять disabled production и статистику зеленым статусом. До ручной проверки владельца `manualGate=REQUIRED`.

| Gate | Что должно быть доказано |
|---|---|
| G0 SOURCE | Fresh branch/HEAD/AGENTS, accepted 021 evidence, physical parent **188** exact SHA, private TEST/sql/Geo/protected safe, no concurrent writers/dirty changes. |
| G1 CONTROL | Реальные Start/Status/Pause/Resume/Stop/Report, second Start idempotent, detached controller survives CLI exit, no cross-talk с V3, 020, 021 и TEST OFF. |
| G2 AUTONOMY | На основании actual native source появляется 2 different immutable attempt/episode IDs с новым cursor, frozen policy/split/model before CUT; после первого commit выбирается второй, без ручного `/goal` и без hardcode parent179. |
| G3 NATIVE | ≥2 **genuine** законно исполненных, завершённых и independently sealed native Solo/DAMAGE эпизода, distinct eventKeys; ≥1 paid experimental action != stock и ≥1 paid stock control; у обоих есть actual owner/cast/native receipts и изоляция. Хотя бы один native process exit=0 и normal complete; `exit=1` без exact complete не заменяется реконструкцией. Actor death разрешена как outcome, не как доказанная вина действия. |
| G4 FIT | ≥2 *последовательных* authenticated TRAIN updates (за два rounds), parent188→candidate1→candidate2 exact SHA, separate-JVM cold each, replay0 per round and globally, heldout/unknown credit0; старые checkpoints ни на байт не изменились. |
| G5 LEDGER | Atomic reservation/result/HEAD, readback, recovery after injected death at pre-native, after paid-before-complete, after checkpoint-before-HEAD; duplicate/reordered/tampered source/split/event BLOCKED, no second credit. Double-START no second writer. |
| G6 ROLLOVER | Versioned full global lineage from parent188; test boundary 4095→4096→next segment with exact 128 weights and global key retention, safe interrupted rollover; no V4/1 codec rewrite or silent key eviction; physical cold/anti-replay on both sides. Synthetic near-cap fixtures **не являются native training evidence**. |
| G7 QUALITY | Отчёт отдельно показывает число lawful attempts, refusals, real episodes, TRAIN/TUNING/AUDIT, stock/model/experiment, paid HP/MP/time/kills/deaths, context, n/dispersion/unknown, model/checkpoint/global updates, baseline/provisional/NOT_EVALUATED; не приписывает наблюдению каузальность. |
| G8 BUDGET | ≤2 GS boots, каждый ≤8 мин и cumulative native≤16 мин; offline focused before boot; fault testing without GS; stop confirms OFF/process0/portsFree/private DB, historical pins exactly; 480мин NOT_RUN. |
| G9 DELIVERY | Focused TEST green, relevant build/compile and one safe `ant verify` only after G0 approved DB lane (иначе `NOT_RUN_UNSAFE` + DB-free alternative), no leaked private files; exact scoped commit, ordinary feature push, **independent GitHub** diff/bodies/HEAD and русский operator runbook. |

**Для оценки алгоритма:** `MODEL_REORDERED` отличать от `EXPERIMENT_REORDERED`. Success G0–G9 означает доказанную работоспособность тренера, не победу в боях. `SUPERIORITY=NOT_EVALUATED` корректна при недостатке независимых пар; `PRODUCTION_V4=OFF`, `PARTY/HEAL/PVP=NOT_READY`. `nightReadiness=TEST_ONLY_OWNER_TRIAL` лишь после FULL; немедленный 8-часовой запуск не выполняется и не разрешён этим Goal.

**Отказ и публикация:** при отсутствии второго lawful scene не гонять GS 3–10 раз; `PARTIAL022_NATIVE_SCENE_SHORTAGE`. Если новая цепочка не сохраняет event ownership — `BLOCKED022_UNVERIFIED_NATIVE_BINDING`. Если unsafe rollover — `BLOCKED022_GLOBAL_LEDGER`. Публикуются только безопасные изменения/доказательства, incomplete production-код откатить узко, не уничтожая чужие изменения. Новое разрешение на второй сверхлимитный прогон не подразумевается.
