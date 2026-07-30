# Goal 015 position canonicalization — review evidence

## Review status

`PENDING_INDEPENDENT_REVIEW`

Этот файл передаёт evidence внешнему reviewer и не принимает Goal 015
самостоятельно.

## Graph and scope

- Branch: `feature/phantom-world`.
- Required parent: `b800f125bddedadd4f181e9a5f398283e73c4c13`.
- Required parent subject:
  `fix(phantoms): support ground-loss production drops`.
- Required parent parent: `32be3bbc320bc3a054aab8c5d39001910f35e4b8`.
- Expected child subject:
  `fix(phantoms): canonicalize background anchor positions`.
- Expected graph: один ordinary direct child commit.
- Goal 015A/015B не создавались; Goal 016/017/025 не начаты.
- Player, GeoEngine, GameClient, materialization production code, Player.ini,
  topology/datapack/geodata, loaders, schema и другие хроники не менялись.

## Production contract

- `canonicalCommittedAnchorPosition` — единственный новый production helper.
- X/Y берутся из topology anchor, instance равен `0`, heading и anchor ID
  сохраняются.
- Z вычисляется как `GeoEngine.getHeight(x, y, rawZ)`.
- Повторный вызов с raw Z обязан дать тот же результат; вызов с canonical Z
  обязан быть fixed point.
- Неподдерживаемая канонизация возвращает empty, а travel — typed
  `ANCHOR_MISMATCH` без mutation.
- Partial travel сохраняет committed position и меняет только residual time.
- ARRIVED атомарно передаёт canonical position в
  `PhantomBackgroundTransaction`.
- Baseline capture сохраняет фактические runtime coordinates без snap.
- `farmInput` и `matchesRuntime` требуют естественно восстановленную canonical
  position.

## Real Player evidence

- Test DB: только `l2jmobiush5_phantom_test`.
- Seed: `15001502`.
- Current production route:
  `giran.route.north → giran.farming.22859`.
- Fixture записывает canonical Z в test DB через production helper до
  `Player.load`; после загрузки координаты не меняются.
- Lifecycle capture/dematerialize создаёт READY state с exact departure
  position.
- Partial travel сохраняет state/DB position и не создаёт runtime Player.
- ARRIVED сохраняет exact canonical X/Y/Z/heading; raw topology Z отличается и
  не становится durable.
- Ordinary materialization создаёт exact real Player; direct background
  lifecycle выполняет store/dematerialize без wrapper.
- Новый transaction/service/materialization после restart повторяет exact
  materialize/dematerialize.
- Оба цикла сохраняют byte-identical state.

## Negative controls

- raw topology Z отклоняется как farm position;
- position вне anchor tolerance возвращает `ANCHOR_MISMATCH` без mutation;
- stale topology/authority hash возвращает `NO_ROUTE` без mutation;
- partial travel не меняет committed position;
- restart после ARRIVED сохраняет canonical coordinates.
- `exactAnchorLifecycle` и post-load `setXYZInvisible` отсутствуют в suite.

## Preserved loot proof

- Exact pair: `22859@giran.farming.22859`.
- Supported production pair count: `1`.
- `LOOT_POLICY_V1`, `LEAVE_ON_GROUND`, grouped/ungrouped RNG и occurrence
  budgets сохранены.
- Production loot mode использует natural Player и direct lifecycle, выполняет
  successful atomic batch, exact duplicate и materialization/reload
  conservation.

## Verification evidence

- Production compile и test compile: PASS.
- Position canonicalization mode: PASS, 2/2.
- Production loot mode: PASS, 3/3.
- Historical server integration: PASS, 5/5.
- Остальные 12 historical Goal 015 modes: PASS.
- Static verifier, final aggregate, final `ant verify`, `ant jar`, commit/push и
  два post-commit byte-identical verifier run фиксируются в terminal handoff.

## Reviewer decision

Reviewer должен независимо проверить graph/scope, fixed-point GeoEngine
canonicalization, fail-closed semantics, отсутствие coordinate masking,
transaction durability и restart conservation. До внешнего решения статус
остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
