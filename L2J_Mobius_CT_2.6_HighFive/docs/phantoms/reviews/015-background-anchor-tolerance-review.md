# Goal 015 — review anchor normalization tolerance

## Статус

`BLOCKED`

Goal 015 production loot disposition, natural `Player.load`, ARRIVED
transaction, materialization и restart conservation из required parent
`d4a4557cb2447be501fe8f339cc68b482e8561e0` сохранены без изменения.
Goal 016/017/025 имеют статус `NOT_STARTED`.

## Требуемая коррекция

`canonicalCommittedAnchorPosition` должен fail closed при условии:

```java
Math.abs((long) normalizedZ - point.z()) > anchor.validationTolerance()
```

Проверка обязана использовать raw topology anchor Z и long arithmetic. Она
должна дополнять сохранённые invariants: instance `0`, два одинаковых
`getHeight(x, y, rawZ)`, fixed point `getHeight(x, y, normalizedZ)`, exact X/Y,
heading и committed anchor ID.

## Воспроизводимое противоречие

Fresh запуск существующего
`phantom-background-position-canonicalization-test` на неизменённом required
parent, seed `15001502`, DB `l2jmobiush5_phantom_test`, завершился PASS `2/2` и
зафиксировал:

| Anchor | Raw Z | GeoEngine normalized Z | Delta | Tolerance |
|---|---:|---:|---:|---:|
| `giran.route.north` | -3400 | -4072 | 672 | 0 |
| `giran.farming.22859` | -3061 | -3056 | 5 | 0 |

Следовательно, обязательная проверка отклоняет текущие production departure и
arrival anchors. Это делает невозможными одновременно:

- требуемый fail-closed raw-to-normalized tolerance contract;
- сохранение текущих production departure/arrival anchors;
- successful partial → ARRIVED → materialize → dematerialize → restart;
- запрет изменения topology XML.

Увеличение tolerance не решает departure case: topology anchor contract
ограничивает tolerance максимумом `500`, а delta равна `672`. Минимальная
совместимая коррекция — canonical raw Z `-4072` и `-3056` в двух shipped
topology anchors, но такой файл прямо исключён из allowlist.

## Безопасный результат

- Production Java, tests, build routing и verifier не изменены.
- Нестабильная или заведомо ломающая реализация не оставлена.
- Принятые `LOOT_POLICY_V1`, `LEAVE_ON_GROUND`, RNG, occurrence budgets,
  duplicate semantics и production loot `3/3` не затронуты.
- `Player`, `GeoEngine`, topology XML, datapack, geodata, loaders, config,
  schema, materialization и другие хроники не изменены.
- Goal 015A/015B/015C не создавались.

Новый helper/tolerance target, transition negative, cumulative verifier,
historical aggregate, final `ant verify` и `ant jar` не запускались: обязательная
production precondition доказанно несовместима с разрешённым scope.

## Необходимое решение

Для продолжения нужен отдельный явный scope exception на две canonical Z правки
shipped topology anchors либо отмена требования сохранить поддержку этих
anchors. До такого решения Goal 015 остаётся `BLOCKED`, activation закрыта.

## Git

- Branch: `feature/phantom-world`.
- Required parent: `d4a4557cb2447be501fe8f339cc68b482e8561e0`.
- Required parent parent: `b800f125bddedadd4f181e9a5f398283e73c4c13`.
- Child subject: `fix(phantoms): enforce anchor normalization tolerance`.
- Commit SHA и push result передаются в final handoff.
