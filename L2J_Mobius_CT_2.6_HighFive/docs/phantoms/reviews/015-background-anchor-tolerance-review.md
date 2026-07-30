# Goal 015 — review anchor normalization tolerance

## Статус

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent:
`7037fe92ad930425a600d070bbaf6c2d0234ada0`.

Required subject:
`fix(phantoms): resolve anchor tolerance data`.

Goal 016/017/025: `NOT_STARTED`.

## Production correction

`canonicalCommittedAnchorPosition` fail closed проверяет:

```java
Math.abs((long) normalizedZ - point.z()) > anchor.validationTolerance()
```

Сохранены instance `0`, два одинаковых raw-height вызова, normalized fixed
point, exact X/Y, heading и committed anchor ID. Production public path
использует `GeoEngine.getInstance()`; resolver overload private/static и
доступен только как deterministic test seam.

Старая бессмысленная tolerance-проверка только между `restoredZ` и
`normalizedZ` удалена. Fixed-point equality `restoredZ != normalizedZ`
сохранена отдельно.

## Topology data

| Anchor | Raw Z | Canonical Z | Tolerance | Result |
|---|---:|---:|---:|---|
| `giran.route.north` | -4072 | -4072 | 0 | supported |
| `giran.farming.22859` | -3061 | -3056 | 5 | supported |

Farming raw Z сохраняет factual NPC 22859 spawn. Production loader подтверждает
spawn distance, node geometry и edge endpoints. Farming node center, X/Y,
npcId, sources, edge и `baseTravelMillis=900000` не изменены.

Parent canonical topology hash:
`f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f`.

Current canonical topology hash:
`7277419d2ff5c6a4f7066182d01e32aeb9708814e54707e7a91a85cb550a3580`.

Повторный production loader даёт тот же current hash.

## Deterministic negative proof

- Delta ровно tolerance допускается.
- Delta tolerance + 1 отклоняется.
- Разные first/second raw normalization result отклоняются.
- Non-fixed-point normalized Z отклоняется.
- Instance не `0` отклоняется до height resolution.
- Current route/farming anchors остаются supported.

Synthetic malformed arrival сохраняет factual farming raw Z, но использует
tolerance `0`. Direct `advanceTravel` возвращает `ANCHOR_MISMATCH`, position и
clock остаются byte-identical. Service attempt возвращает typed
`travel.anchor_mismatch`; mutation transaction не вызывается, canonical
background state и DB position не меняются.

## Preserved production evidence

- Natural `Player.load` без `setXYZInvisible`.
- Partial → ARRIVED через production authority и atomic MariaDB transaction.
- Exact DB/runtime/background X/Y/Z.
- Ordinary materialization lifecycle, dematerialization и restart.
- Повторная materialization и byte-identical conservation.
- Production loot `3/3` для `22859@giran.farming.22859`.
- `LOOT_POLICY_V1`, `LEAVE_ON_GROUND`, RNG, occurrence budgets, duplicate
  semantics и отсутствие ground-loss inventory rows.

## Scope

Разрешённые production/data изменения ограничены authority helper и двумя
attributes в `high-five-core.xml`. Player, GeoEngine, spawn/NPC data, другие
topology entities, loaders, geodata, config, schema, materialization,
GameClient, commerce, progression и другие хроники не изменены.

Goal 015A/015B/015C не создавались. Activation закрыта до независимого review.

## Verification

- Compile: PASS.
- Position/helper/transition mode: PASS `2/2`, seed `15001502`.
- Production loot: PASS `3/3`, seed `15001502`.
- Production server integration: PASS `5/5`.
- Все 13 historical Goal 015 modes: PASS.
- Static verifier: PASS.
- Единственный explicit final aggregate: PASS, 15/15 suite reports имеют
  `failed=0`.
- Первый full verify встретил transient historical combat cleanup failure;
  exact target прошёл `20/20` без изменений, повторный full verify —
  `BUILD SUCCESSFUL`.
- Standalone `ant jar`: PASS.
- Post-commit verifier reproducibility фиксируется после publication.

## Git

- Branch: `feature/phantom-world`.
- Expected graph: один ordinary child required parent.
- Amend/rebase/squash/merge/force push запрещены.
- Commit SHA и push result передаются в final handoff.
