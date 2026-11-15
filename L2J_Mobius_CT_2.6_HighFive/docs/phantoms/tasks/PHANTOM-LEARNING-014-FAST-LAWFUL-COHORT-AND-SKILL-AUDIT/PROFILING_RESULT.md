# Стоимость preparation014

Монотонные nanos + UTC coarse records собирались существующим adapter/Runtime. Raw остаются в private TEST; опубликованы sanitized aggregates и SHA. Никаких high-rate snapshots или второго supervisor.

| Фаза | Baseline count / total ms | Post-fix count / total ms | Вывод |
|---|---:|---:|---|
| Natural recovery |2 /199668.479|6 /413013.777|Главный расход:73.31%/80.11% полной wall. Разное число arms; это не доказательство снижения общей latency.|
| Roster reproduction |2 /926.879|6 /2204.567|Создание durable incarnations дешевле ожидания ресурсов.|
| Materialization |10 /419.699|22 /668.845|Не dominant bottleneck.|
| Anchor travel |10 /2.662|22 /2.893|На этих сценах фактически пренебрежимо.|
| Party formation |1 /413.031|3 /1039.707|Не dominant bottleneck.|
| Native admission |0 /NOT_REACHED|3 /29.574|Сам вызов дешёвый, но идёт после recovery.|
| Party admission |0 /NOT_REACHED|3 /23.875|2refused/1ready.|
| Owner drain |UNKNOWN_BASELINE|6 /20.919|В baseline отдельного timer ещё не было. Общий exit/cleanup в session wall.|
| Source discovery |1 /1208.265|1 /1007.232|Не dominant bottleneck; отдельная initial ingress/infrastructure разбивка UNKNOWN.|
| Immutable HEAL deny |NOT_IMPLEMENTED|1 /0.1742|Ранее134458.889ms полного recovery до неподходящего HEAL; теперь до roster и recovery.|

Для каждой фазы min/median/p95/max и denominator есть вPREPARATION014-S1/S2. Baseline median для двух значений — lower empirical median; данных мало, статистическая устойчивость не заявлена. Baseline НЕ изолирует каждый SQL request/первую ingress reproduction и drain; SQL/infrastructure residual не выдан за measured SQL time.

S1:272357ms total, worker249658ms, CPU114.1875s, peak4650033152B. S2:515549ms total, worker473427ms, CPU120.484375s, peak4656578560B. Worker CPU не атрибутирован отдельным cell;120.484375s на единственный полностью paired cell — только cohort-level denominator, а не его собственное CPU-время. Startup+cleanup в total, Ant/JAR отдельно.

До первого полного paired cell:S1 NOT_REACHED за272357ms; S2 74153.531ms. До второго/третьего:S1/S2 NOT_REACHED. Никакой first-arm proof не засчитан парой. Sealed TRAIN cohort0.

Post-fix непригодные MP-кандидаты всё ещё потребили269003.720ms recovery, затем отказали по unchanged native budget3060>2244.8. Корректный future fast deny должен получать necessary immutable projection от canonical Party authority и никогда не кэшировать текущий MP/HP/owner/cooldown/Geo. В014 этого API не добавляли и policy не копировали.

Варианты для отдельного решения владельца:

1. Минимальный read-only necessary-viability seam в каноническом Party backend с pinned learned/loadout/definition facts и консервативным max-resource bound. Live native admission непосредственно перед CUT сохранить. Это выходит за exact6/production-party запрет014 и сейчас не реализовано.
2. Для HEAL спроектировать отдельный lawful wounded-recipient admission на фактических native последствиях. Совместимость текущих full-recovery и pre-CUT HEAL offers нарушена конструктивно; нельзя исправлять её выдачей HP/MP, искусственной раной или ослаблением owner/MP/Geo. Это иной contract, не фиктивный adapter014.

Оба варианта — предложения для review, без начала следующего Goal и без изменения buffs/production.
