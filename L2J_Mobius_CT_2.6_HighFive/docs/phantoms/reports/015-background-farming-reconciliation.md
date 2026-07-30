# Goal 015 — каноническая позиция background anchor

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

- Принятый reconciliation и production loot disposition из commit
  `b800f125bddedadd4f181e9a5f398283e73c4c13` сохранены.
- Единственная production-правка добавляет
  `canonicalCommittedAnchorPosition`: X/Y берутся из topology anchor, Z вычисляется
  через `GeoEngine.getHeight(x, y, rawZ)`, instance равен `0`, heading и anchor ID
  сохраняются.
- Полное завершение edge атомарно записывает каноническую позицию; partial travel
  сохраняет последнюю committed position и только уменьшает residual time.
- Нестабильная/неподдерживаемая канонизация, position вне tolerance, stale authority
  hash и raw topology Z отклоняются до мутации.
- Baseline capture по-прежнему сохраняет фактическую позицию runtime Player; snap
  при capture не добавлен.
- Test-only `exactAnchorLifecycle` и все post-`Player.load` вызовы
  `setXYZInvisible` удалены.

## Changed files

- Production:
  `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`.
- Tests/build:
  `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`,
  `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`, `build.xml`.
- Verification/docs:
  `tools/phantoms/verify-task-015.ps1`, architecture contract, roadmap, этот report
  и position-canonicalization review.

## Architecture decisions

- Единственный источник durable committed-anchor position — production helper.
- Helper использует текущий production `GeoEngine`, дважды проверяет одинаковый
  результат нормализации и fixed-point повторного `getHeight`.
- Exact X/Y и geodata-normalized Z проверяются с bounded anchor tolerance.
- `advanceTravel` сначала проверяет текущую committed position и будущую
  каноническую arrival position; при отказе возвращает typed
  `ANCHOR_MISMATCH` без изменения position/clock.
- `farmInput` принимает только каноническую committed position.
- `matchesRuntime` остаётся exact: materialization должна естественно загрузить
  ровно сохранённые X/Y/Z/heading.
- Новых слоёв, worker/thread/Future, логирования или runtime writer нет.

## DB, configs and fixtures

- Использовалась только `l2jmobiush5_phantom_test`.
- Focused seed: `15001502`; historical seed: `15001501`.
- Production pair сохранена: `22859@giran.farming.22859`.
- Supported production pair count: `1`.
- `LOOT_POLICY_V1`, `LEAVE_ON_GROUND`, canonical grouped/ungrouped RNG,
  occurrence budgets и `groundLosses` сохранены.
- Production loot batch остаётся успешным: immediate/time-limited ground loss не
  создаёт Player inventory/effect/timer/object ID; auto-loot drift fail-closed.
- Shipped `Player.ini`, topology/datapack/geodata, loaders, schema и migrations не
  менялись.
- Test fixture записывает в test DB каноническую Z через тот же production helper
  до `Player.load`; после загрузки координаты не меняются.

## Product evidence

- Текущий direct route `giran.route.north → giran.farming.22859` используется
  production transition test.
- Natural real Player загружается в departure anchor, проходит lifecycle capture
  и dematerialize, partial и ARRIVED через `PhantomBackgroundService`.
- Partial transaction сохраняет DB/state position и не создаёт runtime Player.
- ARRIVED transaction сохраняет exact canonical X/Y/Z/heading; raw topology Z не
  становится durable.
- Ordinary materialization загружает exact runtime Player без координатной
  подмены; dematerialization сохраняет byte-identical state.
- Новый transaction/service/materialization после restart повторяет exact reload
  и byte conservation.
- Negative controls покрывают raw topology Z, position вне tolerance, stale hash,
  partial non-mutation и restart после ARRIVED.
- Production loot mode подтверждает real Player atomic batch, duplicate и
  materialization/reload conservation на seed `15001502`.

## Commands and results

- Bundled Ant `compile`: PASS.
- Bundled Ant `compile-tests`: PASS.
- Новый `phantom-background-position-canonicalization-test`: две диагностические
  попытки выявили отсутствующую fixture initialization и несохранённые runtime
  vitals; после двух точечных test-only исправлений — PASS, 2/2.
- `phantom-background-production-loot-unblock-test`: PASS, 3/3.
- `phantom-background-server-integration-test`: PASS, 5/5.
- Остальные 12 historical Goal 015 modes: PASS.
- Static verifier, final focused aggregate, единственный final `ant verify`,
  standalone `ant jar`, commit/push и два post-commit byte-identical verifier run
  фиксируются после выполнения соответствующих gate.

## Performance and safety

- Historical 100,000 model evaluations и 10,000 duplicate reconciliations: PASS.
- Изменения ограничены одной production authority и focused evidence.
- Player, Attackable, Item, Inventory, GameClient и materialization production
  code не менялись.
- Mojibake-маркеры в изменённых файлах проверяются static verifier.
- Escaped Cyrillic в изменённых файлах проверяется static verifier отдельно.

## Deviations, limitations and risks

- Production activation не выполнялась; требуется независимое ревью.
- Goal 015A/015B не создавались.
- Goal 016/017/025 не начаты.
- Party, spoil, manor, quest, craft, raid, instance и PvP вне scope.

## Git and handoff

- Branch: `feature/phantom-world`.
- Required parent: `b800f125bddedadd4f181e9a5f398283e73c4c13`.
- Его parent: `32be3bbc320bc3a054aab8c5d39001910f35e4b8`.
- Expected subject: `fix(phantoms): canonicalize background anchor positions`.
- Expected graph: один ordinary direct child commit, без
  amend/rebase/squash/merge/force push.
- Commit SHA и push result передаются в final handoff после publication.
- Next step: независимое ревью Goal 015.
