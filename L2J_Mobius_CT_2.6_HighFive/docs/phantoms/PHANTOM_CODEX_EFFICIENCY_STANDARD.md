# Phantom World — Codex efficiency standard

Обязателен для всех следующих Goal.

## Task size

- ordinary Goal task package: не более 450 строк без manifest;
- suffix/correction: не более 320 строк;
- одно требование описывается один раз;
- предыдущая история передаётся compact handoff, а не перечитывается полностью.

## READ_SET

Каждая Goal задаёт закрытый initial `READ_SET`.

```text
initial exact files <= 12
repository searches before first patch <= 6
full reads of files >1000 lines = 0
additional exact files <= 5
other chronicles = 0
```

Для больших файлов читать только symbols/bounded ranges через `rg -n` и
точечный вывод. Запрещено без blocker:

- читать весь master plan или roadmap;
- читать все предыдущие task packages/reports;
- читать все production classes/tests;
- печатать полный repository diff.

Каждое расширение READ_SET фиксируется в report: path, symbol, причина.

## Writer audit

Перед любой mutating Goal перечислить все известные runtime/durable writers,
background/autosave/reward writers, locks и swallowed exceptions.

` synchronized(Player)` или `synchronized(Item)` не считается общей
serialization boundary без доказательства, что остальные writers используют тот
же monitor. При недоказанном writer set операция обязана detect ambiguity и
fail closed/fail stop, а не заявлять atomicity.

## Fixtures

Task author задаёт exact IDs либо deterministic selection predicate и fallback.
Не менять historical global seed; новая suite получает отдельный property.

## Commands and logs

Полный output направлять в:

```text
.phantom-local/logs/<goal>/
```

В agent context оставлять exit code, summary и максимум 80 последних строк
ошибки. Использовать targeted diff.

## Test cadence

Во время реализации:

1. compile affected code;
2. affected focused target;
3. после исправления — только failed target.

Финал:

```text
all focused targets текущей Goal: один green run
ant verify: один run
допустим максимум один повтор после конкретного failed-target fix
ant jar: один run после green verify
static verifier: два финальных byte-identical run
```

Unrelated historical flake: один targeted retry и evidence; не повторять из-за
него полный cumulative suite многократно.

## Report and telemetry

Report:

- suffix <=140 строк;
- ordinary Goal <=180 строк;
- не перечисляет каждую read-only команду.

Обязательно сообщает:

```text
initial files opened
additional files opened
repository searches
focused target runs
full ant verify runs
standalone jar runs
verifier runs
intermediate failures and causes
Goal usage
elapsed time
```

Ориентиры:

```text
suffix <=250k tokens
bounded ordinary Goal <=400k tokens
>500k requires explicit justification
```

## Latent handlers

Handler не равен разрешённой автономной политике. Перед первым production
candidate/plan route повторно проверить all-writer concurrency, cancellation и
restart semantics.
