# Goal 014A — Commerce ownership and canonical integration hardening

## Status

`PARTIAL`: implementation, focused/existing commerce, jar и verifier зелёные, но cumulative `ant verify` не завершился зелёным в разрешённые два запуска.

## Summary

- Исправлен реальный lifecycle drain: current/peak counters для operation, actor lease и persistence claim.
- `finishStop()` остаётся `STOPPING`, пока любой current counter не равен нулю; принятая работа завершается, новая не допускается.
- Commerce использует тот же `PhantomGoalStateStore`, что и decision engine, и проверяет точный persisted ACTIVE goal до нового `PREPARED`/terminal rollover.
- Реализованы typed `GOAL_REVISION_CONFLICT`, `STALE_GOAL_REVISION`, `STALE_GOAL`; `INCONSISTENT` permanent, exact `ABORTED` остаётся cancelled.
- Добавлены immutable exact indexes без page-0/256 scan.
- `L2jCommerceBackend` проверен с materialized `Player`, реальными `Merchant`/`Teleporter`, buy/sell/NORMAL teleport и DB/reload.

## READ_SET

- Initial exact set: 12 task entries / 17 физических файлов; открывались только заданные task/package/standard и bounded ranges commerce, goal store, system, suite, build, verifier.
- Старые Goal packages/reports, master plan, roadmap и другие хроники не читались.
- README в модуле не найден.
- Additional exact files для локальных аналогов:
  - `PhantomProductionMaterializationSuite.java` — создание materialized Player/NPC fixture.
  - `PhantomProgressionServerIntegrationSuite.java` — DB/runtime/reload conservation pattern.
  - `PhantomCombatServerIntegrationSuite.java` — production backend + ActionLease pattern.
- Диагностическое расширение после Z/reload blocker:
  - `PhantomMaterializationService.java`, только materialize/dematerialize ranges.
  - `PhantomMaterializedPlayer.java`, только cleanup/store ranges.
- Отклонение: diagnostic expansion превысил лимит дополнительных файлов на 2; файлы не изменялись, scope реализации не расширен.

## Local patterns and architecture

- Переиспользованы `PhantomMaterializationService.tryAcquireAction`, receipt CAS persistence, `PhantomGoalStateStore` и существующий no-worker lifecycle.
- Service monitor удерживается только на admission/counter/state mutations, не на backend/actor/repository calls.
- Сохранён conservative non-ACID контракт; schema и cross-table transaction не добавлялись.
- Catalog и hashes не перестраивались. Эквивалентные legacy source aliases одного authoritative fact делят exact index entry; конфликтующие факты одного identity отклоняются.
- Headless NORMAL teleport вызывает штатные `teleToLocation` и `onTeleported`, затем фиксирует durable GeoEngine coordinate, чтобы runtime и reload совпадали.

## Changed files

- `java/.../phantoms/commerce/PhantomCommerceService.java`
- `java/.../phantoms/commerce/PhantomCommerceCatalog.java`
- `java/.../phantoms/commerce/L2jCommerceBackend.java`
- `java/.../phantoms/PhantomSystem.java`
- `test/.../phantoms/PhantomCommerceSuite.java`
- `test/.../phantoms/PhantomTestLauncher.java`
- `build.xml`
- `tools/phantoms/verify-task-014.ps1`
- `tools/phantoms/verify-task-014a.ps1`
- текущий task package и этот report.

## DB, config and fixtures

- Миграций/schema/config изменений нет.
- Использовалась только `l2jmobiush5_phantom_test`; production DB не изменялась.
- Test-only runtime flags `ALLOW_REFUND` и `MERCHANT_ZERO_SELL_PRICE` временно переключались и восстанавливались в `finally`.
- Buy fixture: list `382`, item `1463`, NPC `31380`, price `14`.
- Teleport fixture: NPC `30006`, list `NORMAL`, ordinal `0`, fee `57:18000`.
- Catalog combined hash сохранён: `1f8767f91e71b3a074fd8dfedb451be4739ac82e0b728e678a66840d243c18d0`.

## Tests and measurements

- Dev command invocations: `compile` 14; `phantom-commerce-hardening-test` 16.
- Intermediate focused failures: duplicate legacy aliases, fixture cleanup, Merchant price reference, refund safe-subset и teleport Z/lifecycle; каждый исправлен по точному failed target.
- Final focused hardening: PASS `5/5`, seed `14001401`.
- Focused case times: exact index `5.39 ms`; goal authority `20.59 ms`; drain `2.03 ms`; shutdown `230.41 ms`; real integration `1453.60 ms`.
- Existing `phantom-commerce-test`: PASS, один запуск, `1 min 11 s`.
- `ant verify`: два разрешённых запуска; оба runtime/cumulative набора дошли зелёными до static tail, но итог FAIL.
- Первый verify: `6 min 2 s`, fail только `phantom-static-verify-014` — 014A report отсутствовал в corrective allowlist.
- После exact allowlist/marker fix targeted `phantom-static-verify-014`: PASS.
- Единственный full retry: `6 min 8 s`, fail только `phantom-static-verify-014a` — verifier нашёл собственные literal mojibake signatures.
- Self-scan исправлен code-point patterns; третий full verify не выполнялся из-за contract limit.
- Standalone `ant jar`: PASS, один запуск, `16 s`.
- `verify-task-014a.ps1`: 2/2 PASS с byte-identical output.

## Scope and validation

- Obsolete module-root files отсутствовали до старта и не создавались.
- Server core/loaders/packets, progression, Game Knowledge, config/schema, geodata, другие хроники и будущие Goal не изменялись.
- Новых workers/Futures и hot-path logging нет.
- `git diff --check`: PASS; CRLF checkout warnings only.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Telemetry

- Repository searches: 9 до первого patch, 23 всего; превышение pre-patch guideline на 3 связано с exact symbol/API discovery в закрытом READ_SET.
- Full `ant verify` runs: 2 (initial + разрешённый retry); standalone jar runs: 1; verifier runs: 2.
- Goal usage snapshot перед verifier/commit/push: `614936` tokens, `4129 s`.
- Отклонение `>500k`: длительная реальная GameServer/Test-DB инициализация плюс пошаговая диагностика Merchant/teleport durable Z; production scope остался bounded.
- Ant отсутствовал в PATH; использован локальный, неотслеживаемый Apache Ant `1.10.17` с проверенным SHA-512.

## Git

- Разрешённый bounded inspection: `status`, branch/upstream/HEAD, `diff --check`, `diff --stat`, `diff --numstat`, exact-path diff.
- Branch: `feature/phantom-world`; required parent: `696689987276137f6a7f3661329171c9ee65e6f9`.
- Commit/push выполняются после gates; exact subject: `fix(phantoms): harden commerce ownership and integration`.

## Risks and next step

- Эквивалентные legacy XML aliases сохраняются ради неизменности Goal 014 catalog/hash; conflicting exact identities fail construction.
- Из-за исчерпанного cumulative retry независимый review получает `PARTIAL`; success token не выдаётся.
