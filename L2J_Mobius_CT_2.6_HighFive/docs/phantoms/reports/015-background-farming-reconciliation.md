# Goal 015 — каноническая позиция background anchor

## Status

`BLOCKED`

## Summary

- Последний bounded anchor-tolerance completion от required parent
  `d4a4557cb2447be501fe8f339cc68b482e8561e0` заблокирован противоречием между
  обязательной raw-to-normalized Z проверкой и запрещённым topology scope.
- Production-код, тесты и verifier не изменялись: заведомо ломающая оба shipped
  production anchor правка не оставлена.
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

- Fresh pre-change
  `phantom-background-position-canonicalization-test`: PASS, 2/2, seed
  `15001502`, test DB `l2jmobiush5_phantom_test`.
- Fresh runtime evidence: `giran.route.north` raw Z `-3400` нормализуется в
  `-4072` (delta `672`, tolerance `0`); `giran.farming.22859` raw Z `-3061`
  нормализуется в `-3056` (delta `5`, tolerance `0`).
- Compile, новый helper/tolerance target, transition negative, production loot
  3/3, historical modes, verifier, aggregate, `ant verify` и `ant jar` для
  anchor-tolerance child не запускались: обязательная production precondition
  доказанно невыполнима в разрешённом scope.
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

- Требуемое условие
  `Math.abs((long) normalizedZ - point.z()) > anchor.validationTolerance()`
  при shipped topology отклоняет оба production anchor до ARRIVED mutation.
- Разрешённый production scope содержит только
  `L2jPhantomBackgroundAuthority.java`, а topology XML прямо запрещён. Для
  совместимости нужны точечные canonical Z правки `-3400 → -4072` и
  `-3061 → -3056`; без отдельного разрешения они не выполнены.
- `validationTolerance` нельзя просто увеличить для departure anchor: topology
  contract ограничивает его значением `500`, а подтверждённая delta равна
  `672`.
- Production activation не выполнялась; требуется независимое ревью.
- Goal 015A/015B/015C не создавались.
- Goal 016/017/025 не начаты.
- Party, spoil, manor, quest, craft, raid, instance и PvP вне scope.

## Git and handoff

- Branch: `feature/phantom-world`.
- Required parent: `d4a4557cb2447be501fe8f339cc68b482e8561e0`.
- Его parent: `b800f125bddedadd4f181e9a5f398283e73c4c13`.
- Expected subject: `fix(phantoms): enforce anchor normalization tolerance`.
- Expected graph: один ordinary direct child commit, без
  amend/rebase/squash/merge/force push.
- Commit SHA и push result передаются в final handoff после publication.
- Next step: отдельно разрешить canonical Z correction двух shipped topology
  anchors либо снять требование сохранить их production support.
