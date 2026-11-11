# Критерии FULL003

| Gate | Обязательный проверяемый результат |
|---|---|
| A0 — Git | Исходный 002 SHA, независимый clone, `wip/phantom-learning-003`; защищённые refs и оригинальные datadir не тронуты |
| A1 — V3 контракт | Typed entity list/action list, schema+hash/masks, variable allies/enemies/summon; safe unknown; native class/level/weapon/jewels/effects/geometry меняют факты |
| A2 — Реальная нейромодель | Отдельная обучаемая V3 4–8 МиБ без пустых padding, immutable sha/version/checkpoint, deterministic inference, сериализация и холодное восстановление |
| A3 — Совместимость | V1/002 model weight/version/readback, существующие damage Solo/Party и TRAIN_*.cmd продолжают работать; отсутствие V3 = legacy |
| A4 — Native legality | Каждый кандидат из законного owner, hard gates, проверка epoch/lease/cooldown/target, нет free actions или вмешательства в человеческого игрока |
| A5 — HEAL evidence | Реальный штатный Heal получил exact полезный delta HP и расход MP; durable label с provenance NATIVE, одна запись, отказ при ошибке/повторе/изменении owner |
| A6 — Изменение поведения | После V3 обучения + cold reload хотя бы один разрешённый HEAL выбор target/skill изменён относительно штатной сортировки и исполнен со штатным HP; emergency safety не нарушена |
| A7 — Обобщение и честность | HEAL: как минимум два независимых actor/scene contexts между train и audit, baseline vs candidate, no data leakage; DAMAGE V1/002 — проверенная регрессия, не новое обучение V3, не выдавать одну запись за общий боевой интеллект |
| A8 — Скорость | 128-actor warmed microbench + фактическое snapshot/encode/score; p50/p95/p99, actions=4/8/16, CPU/RAM/alloc; bounded throttle/fallback. Если не хватает производительности — не включать ON на production |
| A9 — Регрессия | Изменённые model/Party/Combat suites и F7/F8, обычный Player heal, no double reward, Geo/protected safe; process STOP и 002-old checks не пострадали |
| A10 — Доставка | Отчёт RU, exact commands and SHA, scope/perf/limitations, scoped push, HANDOFF for 004, все созданные owned TEST процессы остановлены |

Для успеха не требуется огромная multi-class training campaign и полное превосходство в PvP или Олимпиаде. **Без реального обучаемого HEAL-выбора нельзя заявлять FULL003**, даже если V3 весит ровно 8 МиБ. И наоборот, успех не требует ручной проверки cmd-вызовов пользователем сейчас: owner отложил её.

Не засчитывать synthetic/SIM/teacher as native. Никаких невалидных «обходов сложности» подменой самого Gameplay. Не использовать hardcoded правило «Cardinal always target A», random action override или наполнение файла нулями для достижения объёма.
