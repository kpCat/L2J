# Профилирование и быстрый, но законный pipeline

## Измерить до исправления

Для одной fixed-seed preparation и последующих candidates собирать bounded records (всегда UTC+monotonic elapsed) по этапам:
- source SQL read/identity; source filtering/historical exclusion; reproduce actor; actual skill inventory;
- nextCandidate / shortlist / roster selection; static canonical NPC/source capability/level/grade / reward envelope;
- candidate scene and target discovery; Geo/faction preliminary checks;
- per-arm materialization, party join and travel to anchor, native natural recovery (HP/MP), target spawn, legal skill readiness, Party admission, snapshot/physical parity, drain;
- SAVE of candidate receipts, old/new model/index fingerprints, SQL/CPU/RAM metrics, actual failure reasons.

Получить total, count, min/median/p95/max, total wall per stage, per source/roster/cell и denominator отказов. Не использовать high-frequency INFO или dump всех actor snapshots в Git. Все raw в private TEST; публично redacted aggregate.

Стадия `WAIT_RECOVERY_MP` дорогая не потому, что долгий таймер, а потому что персонаж реально недостаточно восстановился. Проверять read-only возможность раннего отклонения (если канонически невозможно выполнить заданное время/MP) и отказаться, а не выдавать MP. Если HP/MP могут восстановиться естественно — легальная подготовка может ожидать, но её стоимость фиксируется.

## Улучшение, если доказано

- Сортировать native admission после дешёвых отрицательных отсечений, при этом кандидаты сохраняют frozen ordering и независимость от outcome.
- Не делать полный double-repro, если для отказа достаточно канонических стабильных read-only skill/target/scope фактов. Но если доступность меняется, все mutable facts пересчитывать живым GameServer до CUT.
- Можно кэшировать только immutable fingerprint-checked source/catalog/skill-tree facts. Нельзя кэшировать HP/MP/LOS/cooldown, текущую позицию NPC, spawn generation, active owner, заменять проверку на copied old admission.
- Если отказаться после одной руки A/B, сохранить denominator и не считать cell доказанной. Один и тот же reserved ID не исполнять дважды. Прерывание/восстановление со строгим immutable ownership.
- Не создавать новые GameServer при невалидном headless. Никаких новых постоянных workers или независимо работающих supervisors.
- Не получать больше скорости за счёт снижения правил игры, увеличения batch/physical-candidate cap, расширения stock score margins или скрытой отмены ошибок.

## Результаты

1. Публичная причинная таблица `before`, `after`, `change`, `evidence`, `unknown` по ТОП-3 дорогим этапам.
2. Короткое доказательство CPU/elapsed за cell и доля расходов на непригодные кандидаты. Если после правки нет фактического улучшения — прямо сказать.
3. Версионированный `preparation-cost014.json` только в приватном TEST с hash; sanitized metrics в docs.
4. Одна bounded baseline и одна post-fix readiness с общим headless лимитом20min. Если невозможно зафиксировать baseline без full retry — использовать уже F3 как верхний baseline с явным UNKNOWN stage breakdown, без выдуманных цифр.
