# Codex report — Task 004A real-login lease and cleanup hardening

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Technical recommendation:

```text
FEASIBLE_WITH_SEAM_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Manual gate: `PENDING_INDEPENDENT_REVIEW`

Task 005: `NOT_STARTED`

ADR: `Proposed`

## Summary

Закрыты только четыре findings независимого ревью Task 004:

- disabled ordinary real login сохраняет legacy load path без нового lease;
- `CharacterSelect` load/bind и `GameClient.onDisconnection()` сериализованы
  общим `playerLock`;
- REAL_LOGIN lease освобождается fail-closed только после World/offline/
  autosave/client postconditions;
- Phantom cleanup удерживает owner/output/Player при operation failure,
  повторяется безопасно и заканчивается `STORED` только после полного успеха.

Fake `GameClient`/`Connection` не создавались. Task 005 не начинался.

Effective baseline:
`441877e75feed482b58c2b0647137739b5b07748`.
Это единственный ordinary child reviewed Task 004 commit
`5b22b1ee9bab556cd5a14c2212dfa3f4119c4566`, меняющий только roadmap.

`docs/PHANTOM_BOTS_ROADMAP.md` сохранён byte-for-byte. Зафиксированный SHA-256:
`52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346`.

## Changed files

Production и build:

- `build.xml`;
- `java/org/l2jmobius/gameserver/network/GameClient.java`;
- `java/org/l2jmobius/gameserver/network/Disconnection.java`;
- `java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java`;
- `java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java`.

Tests и verifier:

- `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java`;
- `tools/phantoms/verify-task-004a.ps1`.

Documentation:

- восемь файлов task package
  `docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/`;
- `docs/phantoms/reports/004-headless-player-feasibility-spike.md`;
- этот report;
- `docs/phantoms/reviews/004-headless-player-feasibility-spike-review.md`;
- `docs/phantoms/adr/0001-headless-player-integration-seam.md`.

Другие хроники, `Player.java`, configs, SQL/data, Goal 005 artifacts и roadmap
не менялись.

## Architecture decisions

`PhantomIdentityLeaseRegistry.requiresRealLoginArbitration(...)` реализует
явную truth table. При disabled+none/REAL_LOGIN используется отдельный exact
legacy `GameClient.load` path без claim/release. Disabled+PHANTOM и весь enabled
режим проходят существующую registry arbitration.

`GameClient.onDisconnection()` удерживает тот же `playerLock`, что
`CharacterSelect`, при переводе client в `DISCONNECTED` и immediate cleanup.
`CharacterSelect` после получения lock и до load требует состояние
`AUTHENTICATED`. Delayed combat wait выполняется вне lock.

Новый `PhantomPlayerCleanupPolicy` — узкая read-only проверка. Cleanup считается
полным только когда Player offline, exact Player отсутствует в `World`
player/object maps, отсутствует в autosave и имеет null client. Для этого в
autosave manager добавлен только `contains(Player)`.

`Disconnection` освобождает REAL_LOGIN lease после Player cleanup только при
успехе общей политики. Исключение или residue удерживает owner и выдаёт одно
bounded warning для lease; автоматический retry loop не добавлен.

Materializer различает test-only after-step evidence и реальные operation/
postcondition failures. `BEFORE_STORE_OPERATION` и
`BEFORE_DELETE_OPERATION` оставляют state `FAILED` и сохраняют Player,
outbound и lease. Повторный cleanup использует сохранённые ссылки, достигает
полных postconditions, освобождает owner последним и устанавливает `STORED`.

## DB and migrations

Production DB `l2jmobiush5` не использовалась и не изменялась. Выполнялись только
existing isolated fixtures в `l2jmobiush5_phantom_test` с seed
`20260725001`. Новых migrations, SQL, schema или data artifacts нет.

## Configs

Конфиги и их defaults не менялись. `PhantomPlayersConfig` остаётся canonical
source; обе Phantom feature flags по умолчанию выключены. Test/system-property
switch не добавлялся.

## Commands and test results

Использован Apache Ant `1.10.15` из проверенного локального task runtime, потому
что `ant` отсутствует в PATH.

- `ant compile-tests` — PASS; 1906 production и 26 test sources.
- `ant test` — PASS; harness unit `66/66`.
- `ant phantom-headless-player-test` — PASS; `17/17`, включая исходную
  failure matrix `11/11` и новые arbitration/cleanup regression tests.
- `ant phantom-headless-player-performance-smoke` — PASS; `2/2`.
- `ant phantom-skeleton-test` — PASS; `12/12`.
- `ant phantom-db-test` — PASS; `9/9`.
- предварительный `verify-task-004a.ps1` — `81/87`; шесть ожидаемых FAIL были
  только отсутствовавшими на тот момент review/report/ADR closure artifacts.
- pre-commit `verify-task-004a.ps1` после documentation closure — PASS,
  `87/87`.
- pre-commit `ant verify` — PASS, `BUILD SUCCESSFUL`, 43 секунды; cumulative
  Task 002/002A/003/004/004A gates и negative controls прошли.
- pre-commit `ant jar` — PASS, `BUILD SUCCESSFUL`, 13 секунд.
- production `GameServer.jar` inspection — PASS: 2479 entries, требуемые
  hardening classes присутствуют, test entries `0`.
- post-commit `ant verify`, `ant jar` и два deterministic verifier запуска
  выполняются после создания immutable commit; их evidence передаётся во
  внешнем handoff.

## Performance measurements

Dedicated performance smoke:

```text
oneFixtureEffects=6
oneFixtureNanos=18726800
tenSequentialEffects=60
tenSequentialNanos=137774000
tenSequentialDroppedRecords=0
```

Одна fixture заняла около 18,7 мс, десять последовательных — около 137,8 мс.
Это bounded integration smoke, не production capacity benchmark.

## Deviations

Функциональных отклонений от Task 004A нет. Новый production файл только один:
общая cleanup policy, прямо разрешённая task envelope.

Первый объединённый запуск нескольких Ant targets дважды был остановлен
ограничением времени shell; каждый обязательный target затем запущен отдельно
и завершился успешно. Ранний focused test обнаружил неверный test counter
expectation после retry; assertion исправлен до финальных зелёных прогонов,
production logic из-за этого не менялась.

## Limitations and risks

- Retained REAL_LOGIN lease не получает автоматический retry; это намеренный
  fail-closed результат до recovery orchestration Task 006 или restart.
- Реальный network client не конструировался: lock/order contract проверяется
  static verifier, а policy/ownership — executable tests.
- Геодата отсутствует; navigation не входит в scope.
- ADR и Task 004A не приняты самим Codex и требуют независимого review.

## Branch, commit and push

```text
Branch: feature/phantom-world
Parent: 441877e75feed482b58c2b0647137739b5b07748
Subject: fix(phantoms): harden identity lease cleanup
Commit shape: one ordinary child commit
```

Amend, rebase, merge и force push не используются.

Exact immutable commit SHA, push result and post-commit verifier outputs are
external final-handoff evidence generated after this report is committed.

## Next step

Провести независимое ревью Task 004A. До его принятия ADR остаётся `Proposed`,
manual gate — `PENDING_INDEPENDENT_REVIEW`, Task 005 — `NOT_STARTED`.
