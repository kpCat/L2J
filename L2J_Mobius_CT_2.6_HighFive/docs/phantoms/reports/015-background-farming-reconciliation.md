# Goal 015 — anchor normalization tolerance

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 016/017/025: `NOT_STARTED`.

## Summary

- Documentation-only BLOCKED parent
  `7037fe92ad930425a600d070bbaf6c2d0234ada0` сохранён в истории.
- Production helper теперь сравнивает geodata-normalized Z с raw topology Z:
  `Math.abs((long) normalizedZ - point.z())`.
- Бессмысленная tolerance-проверка restored Z против normalized Z удалена;
  отдельная fixed-point equality сохранена.
- `giran.route.north` исправлен на factual canonical Z `-4072`, tolerance `0`.
- `giran.farming.22859` сохраняет factual spawn Z `-3061`, tolerance изменён
  только на `5`; canonical Z остаётся `-3056`.
- Принятые reconciliation, production loot disposition и natural Player
  lifecycle сохранены.

## Changed files

- Production:
  `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`.
- Topology:
  `dist/game/data/phantoms/topology/high-five-core.xml`.
- Tests:
  `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`.
- Verification:
  `tools/phantoms/verify-task-015.ps1`.
- Docs: roadmap, architecture contract, этот report и
  `015-background-anchor-tolerance-review.md`.
- `PhantomTestLauncher.java` и `build.xml` не менялись: existing focused mode
  остался ровно `2/2`.

## Architecture decisions

- Public production path использует `GeoEngine.getInstance()`; новый runtime
  mutable dependency не добавлен.
- Private static `IntUnaryOperator` overload является узким deterministic
  test seam.
- Проверки helper выполняются fail closed в порядке: instance `0`, два
  одинаковых raw-height result, raw-to-normalized long delta, normalized fixed
  point.
- X/Y берутся из exact anchor; heading и committed anchor ID сохраняются.
- Arrival с невозможной канонизацией возвращает `ANCHOR_MISMATCH` до mutation.

## Topology evidence

- Production `PhantomTopologyLoader` принимает исправленный
  `high-five-core.xml`.
- Factual NPC 22859 spawn `(87439,121072,-3061)` остаётся в tolerance `5`.
- Farming node center, X/Y, npcId, sources, node bounds и background edge не
  менялись.
- Edge `giran.city.farming.background` сохраняет endpoint IDs и
  `baseTravelMillis=900000`.
- Parent topology hash:
  `f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f`.
- Current deterministic topology hash:
  `7277419d2ff5c6a4f7066182d01e32aeb9708814e54707e7a91a85cb550a3580`.

## Focused negative evidence

- Delta ровно tolerance допускается; tolerance + 1 отклоняется.
- Разные first/second raw-height results отклоняются.
- Non-fixed-point normalized Z и instance не `0` отклоняются.
- Current route/farming anchors поддерживаются.
- Synthetic farming arrival с factual raw Z и tolerance `0` возвращает
  `ANCHOR_MISMATCH`.
- Direct result и service attempt сохраняют position/clock; mutation
  transaction fault points не вызываются; background bytes и canonical DB
  position не меняются.

## Production conservation

- Seed `15001502`, test DB `l2jmobiush5_phantom_test`.
- Natural `Player.load` без post-load coordinate masking.
- Partial travel сохраняет departure position и residual time.
- ARRIVED атомарно сохраняет `(87439,121072,-3056)`.
- Ordinary `PhantomMaterializationService` использует
  `PhantomBackgroundService` как lifecycle port.
- Exact DB/runtime/background X/Y/Z, dematerialization, restart, повторная
  materialization и byte-identical state подтверждены.

## Production loot

- Supported production pair count: `1`.
- Exact pair: `22859@giran.farming.22859`.
- `LOOT_POLICY_V1`, `LEAVE_ON_GROUND`, grouped/ungrouped RNG и occurrence
  budgets сохранены.
- Successful atomic real-Player batch, duplicate idempotency и
  materialization/reload conservation сохранены.
- Ground-loss inventory rows и object IDs не создаются.

## Commands and results

- `compile-tests`: PASS.
- `phantom-background-position-canonicalization-test`: PASS, `2/2`.
- `phantom-background-production-loot-unblock-test`: PASS, `3/3`.
- `phantom-background-server-integration-test`: PASS, `5/5`.
- Все 13 historical Goal 015 modes: PASS.
- Static verifier: PASS, `TASK015_VERIFIER_OK`, working graph, scope `8`.
- Единственный explicit final `phantom-background-test`: PASS; все 15 report
  files имеют `failed=0`.
- Первый `ant verify` остановился на transient historical
  `combat-server-integration.02`; exact target сразу после этого прошёл `20/20`,
  out-of-scope правок не было.
- Повторный и единственный green `ant verify`: PASS, `10:28`.
- Standalone `ant jar`: PASS, `0:16`.
- Commit/push и два post-commit verifier run: ожидают выполнения.

## Deviations and safety

- Первый historical batch выявил test-only door-anchor assumption после нового
  fail-closed contract. Exact server-integration test исправлен и повторён
  зелёным `5/5`; production code/data дополнительно не расширялись.
- Использовалась только `l2jmobiush5_phantom_test`.
- Player, GeoEngine, spawn XML, NPC data, loaders, geodata, config, schema,
  materialization, GameClient, commerce, progression и другие хроники не
  менялись.
- Goal 015A/015B/015C не создавались.

## Git and handoff

- Branch: `feature/phantom-world`.
- Required parent: `7037fe92ad930425a600d070bbaf6c2d0234ada0`.
- Его parent: `d4a4557cb2447be501fe8f339cc68b482e8561e0`.
- Required subject: `fix(phantoms): resolve anchor tolerance data`.
- Expected graph: ровно один ordinary direct child, без
  amend/rebase/squash/merge/force push.
- Commit SHA и push result передаются в final handoff.
- Next step: независимое ревью Goal 015.
