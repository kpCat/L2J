# Goal 014A — Commerce ownership and canonical integration hardening

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

- Lifecycle drain учитывает current/peak operation, actor lease и persistence
  claim; `finishStop()` не завершает service до полного освобождения ownership.
- Commerce использует общий persisted `PhantomGoalStateStore`, строго различает
  stale goal/revision и terminal rollover conflict.
- Persisted ACTIVE `profileId/goalId/revision` повторно проверяется после первой
  authority read и непосредственно перед durable `PREPARED`; при смене revision
  receipt не создаётся, `applyFirst`/`applySecond` не вызываются.
- Exact buy/teleport catalog identity не ограничена page-0/256.
- Настоящий `L2jCommerceBackend` проверен на materialized `Player` с реальными
  `Merchant`/`Teleporter`: buy, sell, NORMAL teleport и DB/runtime/reload
  conservation.
- Historical Goal 014 verifier фиксирует exact commit `696689987276137f6a7f3661329171c9ee65e6f9`,
  его parent/subject и ancestor relation вместо требований к текущему HEAD.
- Goal 014A verifier фиксирует implementation commit `cb4fa6486dd705f5ba46d92bd8576424cbd188ee`
  и допускает только exact working-completion либо один ordinary completion child.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomCommerceSuite.java`
- `tools/phantoms/verify-task-014.ps1`
- `tools/phantoms/verify-task-014a.ps1`
- `docs/phantoms/reviews/014-npc-commerce-supply-travel-loop-review.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/014a-commerce-ownership-integration-hardening.md`

## Architecture decisions

- Сохранены текущие CAS receipt/store architecture и conservative non-ACID
  contract; cross-table transaction или новый abstraction layer не добавлялись.
- Вторая authority read выполняется без commerce side effects перед сохранением
  `PREPARED`; остаётся честная консервативная граница без заявления cross-server
  ACID.
- Deterministic test меняет persisted revision ровно на второй `load()` и
  доказывает `STALE_GOAL_REVISION`, отсутствие receipt и нулевые apply-calls.
- Server core/loaders/packets, progression, Game Knowledge, catalog loaders,
  config/schema и Goal 015/017/025 не изменялись.

## DB, config and fixtures

- Использована только `l2jmobiush5_phantom_test`.
- Миграций, schema/config изменений и production DB writes нет.
- Seed: `14001401`.
- Catalog combined hash сохранён:
  `1f8767f91e71b3a074fd8dfedb451be4739ac82e0b728e678a66840d243c18d0`.

## Commands and results

- `ant compile`: PASS, 2034 production sources, 16 s.
- Final focused `phantom-commerce-hardening-test`: PASS `5/5`.
  Shell capture отсоединился по локальному 5-second timeout, но те же Ant/test
  PID были дожданы без повторного запуска; deterministic report завершён зелёным.
- Focused cases:
  - exact catalog beyond 256: PASS, `6.10 ms`;
  - goal authority/terminal rollover/current-goal race: PASS, `30.19 ms`;
  - lifecycle drain/counters: PASS, `3.37 ms`;
  - shutdown claim: PASS, `427.91 ms`;
  - real Player buy/sell/teleport/reload: PASS, `2453.59 ms`.
- `phantom-static-verify-014`: PASS, historical cumulative graph.
- `phantom-static-verify-014a`: PASS, `working-completion` graph.
- Единственный полный `ant verify`: PASS, `6 min 54 s`; static tail и commerce
  hardening внутри cumulative run зелёные.
- Единственный standalone `ant jar`: PASS, 19 s.
- Negative-control FAIL/exit codes внутри `verify` были ожидаемыми и их guard
  targets прошли.

## Scope and validation

- Required completion parent:
  `cb4fa6486dd705f5ba46d92bd8576424cbd188ee`.
- Obsolete root files отсутствуют:
  `CODEX_EXECUTION_BUDGET_BLOCK.md`, `MANIFEST.json`,
  `PHANTOM_CODEX_EFFICIENCY_STANDARD.md`.
- Production/tests/build/verifiers после полного `verify` не менялись.
- `git diff --check`: PASS.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Git and publication

- Branch: `feature/phantom-world`.
- Implementation commit: `cb4fa6486dd705f5ba46d92bd8576424cbd188ee`.
- Completion subject: `fix(phantoms): complete commerce hardening gate`.
- Completion SHA, push result и два byte-identical post-push verifier run
  фиксируются во внешнем handoff: собственный SHA нельзя записать внутрь того же
  ordinary commit, а второй documentation commit запрещён.
- Amend, rebase, merge, squash и force push не выполнялись.

## Limitations, risks and next step

- Между отдельной persisted authority read и receipt CAS нет cross-table ACID;
  контракт намеренно остаётся conservative и не заявляет обратного.
- Goal 014 остаётся `FIX_REQUIRED after first review`; Goal 014A ожидает
  независимого review.
- Goal 015/017/025 остаются `NOT_STARTED`.
