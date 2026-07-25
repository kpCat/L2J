# Codex report — Task 004B retained identity ownership fix

## Status

`SUCCESS`

Technical recommendation:

```text
FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Manual gate: `PENDING_INDEPENDENT_REVIEW`

Goal 005: `NOT_STARTED`

ADR: `Proposed`

## Summary

Исправлены только три retained-identity findings Task 004A:

- disabled legacy path используется только при `currentOwner == null`;
- существующий `REAL_LOGIN` или `PHANTOM` owner всегда проходит arbitration;
- client lease освобождается только для exact object ID очищаемого Player;
- no-player и wrong-character paths удерживают unverifiable lease;
- cleanup считается полным только при отсутствии любого World player/object и
  autosave entry по object ID.

Retryable `FAILED → STORED` cleanup Task 004A сохранён byte-for-byte.
`CharacterSelect`, `Player`, outbound/packet seam, config, DB schema и roadmap
не менялись. Goal 005 не начинался.

Starting baseline:
`d36e10e24787edce3fe4f4d933fca4d0ac884d50`.

Roadmap SHA-256:
`52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346`.

## Changed files

Production и build:

- `build.xml`;
- `java/org/l2jmobius/gameserver/network/GameClient.java`;
- `java/org/l2jmobius/gameserver/network/Disconnection.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java`;
- `java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java`.

Tests и verifier:

- `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java`;
- `tools/phantoms/verify-task-004b.ps1`.

Documentation:

- восемь файлов Task 004B package;
- `docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md`;
- этот report;
- `docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md`;
- `docs/phantoms/adr/0001-headless-player-integration-seam.md`.

`PhantomPlayerMaterializationSpike.java`, `CharacterSelect.java`, `Player.java`,
packet seam, старый verifier Task 004A, configs, schema/SQL/data, другие хроники
и roadmap не менялись.

## Architecture decisions

`requiresRealLoginArbitration(...)` теперь реализует фиксированную policy:

```text
phantom enabled OR current owner exists → arbitration
phantom disabled AND current owner absent → exact legacy path
```

`Lease.matchesObjectId(int)` предоставляет immutable boolean query без выдачи
lease наружу. `GameClient.hasPlayerIdentityLeaseFor(int)` проверяет exact owner,
а `releasePlayerIdentityLeaseFor(int)` повторяет guard внутри synchronized
release. Unscoped release API удалён.

`Disconnection` требует exact matching lease, отсутствие operation failure и
полные cleanup postconditions перед release. Mismatch/no-player удерживает
lease; существующий one-shot retention marker ограничивает warning. Новый retry
scheduler не добавлен.

`PhantomPlayerCleanupPolicy` использует один captured object ID и требует:

```text
!player.isOnline()
world.getPlayer(objectId) == null
world.findObject(objectId) == null
!autosave.containsObjectId(objectId)
player.getClient() == null
```

`PlayerAutoSaveTaskManager.containsObjectId(int)` — узкий read-only scan
cleanup-path. Existing `contains(Player)` сохранён.

## DB and migrations

Production DB `l2jmobiush5` не читалась и не изменялась. Targeted lifecycle и DB
regression suites использовали только `l2jmobiush5_phantom_test` с seed
`20260725001`. Новых migrations, SQL, schema или data artifacts нет.

## Configs

Конфиги не менялись. Обе Phantom flags остаются выключенными по умолчанию.
Hidden test/system-property switch не добавлялся.

## Commands and test results

Использован внешний Apache Ant `1.10.15`, проверенный по official SHA-512 и
распакованный вне репозитория, потому что `ant` отсутствует в `PATH`.

- baseline `verify-task-004a.ps1` — PASS, `87/87`;
- `ant compile-tests` — PASS, 1906 production + 26 test sources;
- `ant test` — PASS, harness unit `66/66`;
- `ant phantom-headless-player-test` — final PASS, `18/18`;
- Task 004 failure matrix — `11/11`;
- Task 004A before-store/before-delete retry — PASS;
- `ant phantom-headless-player-performance-smoke` — PASS, `2/2`;
- `ant phantom-skeleton-test` — PASS, `12/12`;
- `ant phantom-db-test` — PASS, `9/9`;
- initial `verify-task-004b.ps1` before docs closure — expected `60/66`; все
  шесть FAIL относились только к ещё отсутствовавшим report/review/ADR facts.
- final pre-commit `verify-task-004b.ps1` — PASS, `66/66`;
- pre-commit `ant verify` — PASS, `BUILD SUCCESSFUL`, 43 секунды; все Task
  002/002A/003/004/004A/004B suites и negative controls прошли;
- pre-commit `ant jar` — PASS, `BUILD SUCCESSFUL`, 14 секунд;
- production `GameServer.jar` inspection — test entries `0`.

Post-commit evidence выполняется полным contract sequence.
Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.

## Performance measurements

Dedicated final focused smoke:

```text
oneFixtureEffects=6
oneFixtureNanos=15942300
tenSequentialEffects=60
tenSequentialNanos=123068100
tenSequentialDroppedRecords=0
recordingCapacity=16
```

Одна fixture заняла около 15,9 мс, десять последовательных — около 123,1 мс.
World/lease/autosave/task residue отсутствует; это bounded integration smoke,
не production capacity benchmark.

## Deviations

Функциональных отклонений от Task 004B нет.

Первый ранний headless запуск после компиляции завершился `10/18` из-за
известного test-environment race: lazy-created shared ThreadPool workers
появились после baseline thread snapshot и были классифицированы как retained
non-daemon threads. Без изменения production/test logic немедленный повторный и
последующие focused запуски прошли `18/18`; World/autosave/lease residue в
падении не сообщался.

Task 004B package добавляет восемь документов, поэтому общий file count больше
обычного soft limit. Это bounded exception из exact task allowlist, а не
несколько независимых artifact families.

## Limitations and risks

- Реальный `GameClient`/`Connection` тестом не создаётся по прямому запрету;
  exact release source contract защищён verifier, а lease semantics —
  executable pure-policy tests.
- Retained lease не получает automatic retry; recovery orchestration остаётся
  будущей ответственностью Task 006.
- Independent review ещё не выполнен; ADR не принят.
- Геодата отсутствует и не относится к scope.

## Branch, commit and push

```text
Branch: feature/phantom-world
Parent: d36e10e24787edce3fe4f4d933fca4d0ac884d50
Subject: fix(phantoms): preserve retained identity ownership
Commit shape: one ordinary child commit
```

Amend, rebase, merge, reset history и force push не используются.

## Next step

Провести независимое ревью Task 004B. До него ADR остаётся `Proposed`, manual
gate — `PENDING_INDEPENDENT_REVIEW`, Goal 005 — `NOT_STARTED`.
