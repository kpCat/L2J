# Goal 015 — Production loot disposition unblock

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

- Принятый Goal 015 reconciliation code сохранён.
- Immediate/time-limited drops, которые current AutoLoot policy не приобретает,
  имеют `LEAVE_ON_GROUND`.
- Они остаются в полном ordered drop corpus и участвуют в тех же grouped/
  ungrouped RNG, count rolls и occurrence budgets.
- Они не входят в Player inventory, weight/slot checks, item mutation,
  effect/timer/variable state, deferred grant или object-ID reservation.
- `BatchResult.groundLosses` даёт bounded immutable test/metrics evidence.
- Auto-loot через `AutoLootHerbs`, `AutoLoot` или `AutoLootItemIds` отклоняет
  target до mutation.
- `LOOT_POLICY_V1` fingerprint включён в composite knowledge authority hash;
  drift любого relevant config value делает старый READY state stale.

## Changed files

- Production:
  - `PhantomBackgroundModel.java`;
  - `L2jPhantomBackgroundAuthority.java`.
- Tests/build:
  - `PhantomBackgroundSuite.java`;
  - `PhantomTestLauncher.java`;
  - `build.xml`.
- Verification/docs:
  - `tools/phantoms/verify-task-015.ps1`;
  - architecture contract, roadmap, этот report;
  - review evidence и current task package.
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` уже содержал требуемый status line и не
  изменялся.

## Architecture decisions

- `DropDisposition` отделяет canonical RNG result от способа приобретения.
- Ground loss агрегируется только после успешного encounter, но не становится
  canonical Player property и не передаётся transaction command.
- Inventory capacity и object-ID accounting видят только `ACQUIRE`.
- Authority hash использует локальный deterministic SHA-256 pattern и включает
  versioned loot-policy fingerprint.
- Supported Player дополнительно должен быть non-flying/non-mounted; raid и
  остальные прежние fail-closed контексты сохранены.

## DB, configs and fixtures

- DB: только `l2jmobiush5_phantom_test`.
- Новый focused seed: `15001502`.
- Historical Goal 015 seed сохранён: `15001501`.
- Exact pair: `22859@giran.farming.22859`.
- Shipped policy: `AutoLootHerbs=False`, `AutoLoot=False`,
  `AutoLootSlotLimit=True`, `AutoLootItemIds=0`.
- Ground-loss IDs: `8600–8614`, `10655–10657`, `13028`.
- Supported production pair count: `1`.
- Schema/migration/config/topology/datapack/geodata/loader changes: нет.
- Fixture полностью восстанавливает character, inventory и skills.
- Установленная geodata нормализует Z при `Player.load`; test-only lifecycle
  placement повторно ставит real Player в exact task anchor до неизменённого
  production lifecycle.

## Product evidence

- Real Player и current loaders/catalogs формируют production authority input.
- Seed `15001502` даёт ровно один surviving production encounter.
- `PhantomBackgroundService` передаёт результат в real
  `PhantomBackgroundTransaction` с `autoCommit=false`.
- EXP/SP, HP/MP, RNG, receipt/hash и exact acquired deltas совпадают с model.
- Ground-loss item rows отсутствуют; reservation count равен только added slots.
- Exact duplicate возвращает `IDEMPOTENT`, не reroll/regrant и не резервирует ID.
- Real materialize/dematerialize и reload сохраняют committed bytes.
- Config drift и все три auto-loot acquisition paths имеют negative controls.

## Commands and results

- Initial `ant compile`: INFRA FAIL, Ant отсутствовал в `PATH`; bundled
  `.phantom-local/tools/apache-ant-1.10.17/bin/ant.bat` найден и использован.
- Bundled Ant `compile`: PASS.
- Bundled Ant `compile-tests`: PASS.
- Новый focused mode: PASS, 3/3.
- Все 13 historical Goal 015 modes: PASS после одного focused assertion fix.
- Historical server-integration первая попытка: FAIL из-за ошибочного требования
  отдельного time-limited drop у exact pair; повтор после удаления этого
  fabricated assertion: PASS, 5/5.
- Static verifier 015: PASS после verifier-only исправлений quoting и имени
  существующего test method.
- Единственный явный final focused aggregate: PASS, новый mode 3/3 и 13
  historical modes 40/40, всего 43/43; `BUILD SUCCESSFUL`, 4:20.
- Единственный final `ant verify`: PASS; `BUILD SUCCESSFUL`, 10:56.
- Standalone `ant jar`: PASS; `GameServer.jar` и `LoginServer.jar` собраны и
  скопированы в `dist/libs`, 0:14.
- Два post-commit byte-identical verifier runs выполняются после immutable
  commit/push и фиксируются в terminal handoff.

Полные логи: `.phantom-local/logs/goal-015-production-loot-unblock/`.

## Performance and safety

- Historical 100,000 model evaluations и 10,000 duplicate reconciliations: PASS.
- Новых worker/thread/Future/task и per-tick logging нет.
- Runtime writer остаётся только `PhantomBackgroundTransaction`.
- Ground-loss evidence ограничена 96 distinct item IDs.
- Mojibake-маркеры в 14 изменённых файлах проверены: PASS.
- Escaped Cyrillic в 14 изменённых файлах проверены отдельно: PASS.

## Deviations, limitations and risks

- Production activation не выполнялась; требуется независимое ревью.
- Goal 015 остаётся одной capability; Goal 015A/015B не создавались.
- Party, spoil, manor, quest, craft, raid, instance, PvP и Goal 016/017/025 не
  входят в scope.
- Test fixture exact-anchor placement изолирует внешний geodata Z drift без
  изменения topology/geodata/loader или production reconciliation.

## Git and handoff

- Branch: `feature/phantom-world`.
- Required parent: `32be3bbc320bc3a054aab8c5d39001910f35e4b8`.
- Git inspection использован по прямому разрешению task/user:
  `git status --short --branch`, `git rev-parse`, `git branch`,
  `git diff --name-only --`, `git diff --check`, `git diff --stat`,
  `git diff --numstat`, `git diff --no-ext-diff`.
- Publication commands после freeze отчёта: exact-allowlist `git add --`,
  staged `git diff --cached`, `git commit -m`, `git push origin
  feature/phantom-world`; amend/rebase/squash/merge/force push не используются.
- Commit subject: `fix(phantoms): support ground-loss production drops`.
- Commit SHA: передаётся во внешнем handoff после commit.
- Push: PENDING.
- Next step: independent review Goal 015; Goal 016/017/025 не начинать.
