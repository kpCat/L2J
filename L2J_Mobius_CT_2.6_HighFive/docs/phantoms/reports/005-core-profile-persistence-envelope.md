# Task 005 — core profile persistence envelope

## Статус и принятый baseline

Статус: `ACCEPT`.

Независимое ревью приняло Goal 005 в commit
`9d0465eb62f9913644fab9f1d60feb2f4fd9a674` поверх parent
`f5b66c4edf1ddf18e044ef8c692d70ecea616485`. Push и remote ref подтверждены
как exact. Goal 006: `ALLOWED`.

## Закрытие Task 004B

Перед началом production-изменений подтверждены immutable provenance facts:

- commit `f5b66c4edf1ddf18e044ef8c692d70ecea616485`;
- parent `d36e10e24787edce3fe4f4d933fca4d0ac884d50`;
- remote branch указывала на тот же commit;
- два финальных verifier run дали `66/66`;
- byte-identical SHA-256 verifier output:
  `39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9`;
- независимый verdict: `Task 004B: ACCEPT`.

Эти факты записаны в отчёт Task 004B и отдельный independent review.
До изменений Goal 005 повторно прошёл исторический
`tools/phantoms/verify-task-004b.ps1`: `66/66`, exit code 0.

## Принятие ADR 0001

ADR 0001 переведён из `Proposed` в `Accepted` только после независимого
принятия Task 004B. Accepted seam сохраняет обычный `Player`, использует
явный outbound session contract, выполняет side effects пакета ровно один раз
и не создаёт fake/null-network `GameClient`.

Retained `REAL_LOGIN` recovery orchestration и автоматический retry не
приписаны ADR задним числом: это ограничение остаётся за Goal 006.

## Обновление roadmap progress

Roadmap изменён только в progress, DAG и dependency facts:

- Task 004, 004A и 004B отмечены принятыми;
- ADR 0001 отмечен принятым;
- Goal 005 отмечен `PENDING_INDEPENDENT_REVIEW`;
- Goal 006 остаётся `NOT_STARTED` и зависит от принятия Goal 005.

Final roadmap SHA-256, зафиксированный verifier:
`22460C190A496FD8FCEF375F6E232390725AF78D41AA79AB2B42BA505BED38E9`.

## Стабилизация test-only ThreadPool baseline

Изменён только test environment
`PhantomHeadlessPlayerTestEnvironment`. После штатной инициализации он
ограниченно прогревает instant/scheduled workers и не более 64
high-priority workers, затем требует четыре одинаковых снимка
`L2jMobius`/Hikari thread identity. Общий stabilization budget — две секунды.

Production `ThreadPool` и production lifecycle не менялись. Это устраняет
ложный late-worker diff в тестовом thread baseline, не скрывая реально новый
retained thread.

До финального варианта один development run обнаружил восемь поздно
созданных shared workers. После bounded warm-up получены три последовательных
независимых PASS:

```text
ant phantom-headless-player-test
ant phantom-headless-player-test
ant phantom-headless-player-test
```

| Запуск | Результат | Cases | Время Ant |
|---|---:|---:|---:|
| 1 | PASS | 18/18 | 29 s |
| 2 | PASS | 18/18 | 28 s |
| 3 | PASS | 18/18 | 28 s |

Отдельный targeted run после этой серии также прошёл: `18/18`, exit code 0,
29 секунд.

## Схема и фактический fingerprint

Добавлен один installer
`dist/db_installer/sql/game/phantom_profiles.sql` с ровно двумя idempotent
`CREATE TABLE IF NOT EXISTS` statements. Обе таблицы — InnoDB/utf8mb4.

После включения installer test schema manifest имеет:

```text
scriptCount=118
statementCount=207
aggregateSha256=20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E
loginScripts=4
gameScripts=112
migrationScripts=2
```

### Таблицы, indexes и foreign key

`phantom_profiles`:

- `profile_id BIGINT UNSIGNED AUTO_INCREMENT` — stable primary key;
- nullable `character_object_id INT` с unique index
  `uq_phantom_profiles_character_object_id`;
- `schema_version SMALLINT UNSIGNED DEFAULT 1`;
- `row_version BIGINT UNSIGNED DEFAULT 0`;
- millisecond `created_at` и `updated_at`;
- foreign key к `characters` отсутствует.

`phantom_profile_components`:

- composite primary key `(profile_id, component_type)`;
- `component_type VARCHAR(64)` с `ascii_bin`;
- positive `component_schema_version`;
- `row_version BIGINT UNSIGNED DEFAULT 0`;
- `payload VARBINARY(4096)`;
- FK `fk_phantom_profile_components_profile` с `ON DELETE CASCADE` только на
  `phantom_profiles(profile_id)`.

`PhantomProfileRepository.open()` сверяет actual
`information_schema`: tables, engine/charset, exact columns/defaults/bounds,
indexes и единственный component FK. Profile suite подтвердила этот
fingerprint на реально provisioned test DB.

## Production classes и API

Добавлены только:

- immutable record `PhantomProfile`;
- immutable record `PhantomProfileComponent` с defensive payload copies;
- categorized `PhantomProfilePersistenceException`;
- stateless `PhantomProfileRepository`.

Repository API покрывает create/find/find-by-character, optimistic
link/unlink/delete и component insert/find/list/update/delete. `open()` не
удерживает connection. Каждый вызов получает новый connection через
существующий `DatabaseFactory`.

Repository не содержит singleton, cache, worker, scheduler, background task
или public transaction callback.

## Optimistic locking

Core update/delete и component update/delete используют expected
`row_version` в conditional SQL. Update увеличивает версию непосредственно в
БД:

```sql
row_version = row_version + 1
WHERE ... AND row_version = ?
```

Ноль affected rows приводит к `ConcurrentModificationException`. Silent
retry, last-write-wins, `SELECT FOR UPDATE`, table locks и global Java lock
не используются.

Concurrent test запустил два writer с одной expected core version: ровно один
успешен, ровно один получил stale rejection; сохранённый link совпал с
победителем.

## Границы component envelope

Envelope остаётся непрозрачным:

```text
component type: ^[a-z][a-z0-9_.-]{0,63}$
component schema version: 1..65535
payload: 0..4096 bytes
```

Payload 0 и 4096 bytes приняты, 4097 отклонён до JDBC. Входной массив
копируется, accessor возвращает новую копию. Список components имеет
детерминированный `ORDER BY component_type`, возвращается immutable.

Personality, memory, reputation, goals, schedules, activity, population,
materialization, navigation, economy и conversation не определены.

## DB transaction behavior

Каждая write operation открывает короткую локальную transaction boundary,
делает commit при успехе и rollback при SQLException/runtime failure.
Connections, statements и result sets закрываются через try-with-resources.
Constraint, schema и общие DB failures имеют стабильные категории.

Profile delete полагается на проверенный InnoDB cascade и удаляет только
opaque components этого profile. Canonical character/account/item rows не
изменяются.

## Миграция и provisioning x2

Test DB была явно re-provisioned дважды environment-only admin credentials.
Оба запуска завершились exit code 0 и дали идентичный fingerprint:

| Provision | Scripts | Statements | Aggregate SHA-256 |
|---|---:|---:|---|
| 1 | 118 | 207 | `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E` |
| 2 | 118 | 207 | `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E` |

Дополнительно profile suite повторно выполнила только
`phantom_profiles.sql` два раза в существующей test schema до data cases.
Оба replay успешны.

## Round-trip, restart, concurrency и cleanup

Focused suite подтвердила:

- stable generated profile ID и unlinked round-trip;
- optional link/unlink и unique-link conflict без изменения существующих rows;
- stale core/component update и delete rejection;
- два concurrent core writer дают одного победителя;
- insert/read/update и deterministic immutable component list;
- новый repository instance читает то же состояние без cache;
- profile delete каскадно удаляет components;
- финальный owned-row residue: profiles `0`, components `0`.

## Disabled behavior и production DB safety

Repository не подключён к `GameServer`, `PhantomSystem`, config loading,
startup, shutdown или materialization. Нет automatic open/create/load, и при
выключенном Phantom World новые production DB queries равны нулю.

Рабочая production DB `l2jmobiush5` не подключалась, не читалась и не
изменялась. Все DB tests работали только с allowlisted
`l2jmobiush5_phantom_test`. Локальные admin credentials передавались только
через environment и удалялись после provisioning; в artifacts они не
записаны.

## Тесты и gate

| Команда | Результат | Evidence |
|---|---:|---|
| pre-change `ant verify` | PASS, exit 0 | BUILD SUCCESSFUL, 42 s |
| pre-change `verify-task-004b.ps1` | PASS, exit 0 | 66/66 |
| provisioning run 1 | PASS, exit 0 | 118 scripts / 207 statements |
| provisioning run 2 | PASS, exit 0 | identical fingerprint |
| `ant compile-tests` | PASS, exit 0 | 1910 production + 27 test sources |
| `ant phantom-profile-persistence-test` | PASS, exit 0 | 18/18 |
| `ant phantom-db-test` | PASS, exit 0 | 9/9 |
| `ant test` | PASS, exit 0 | harness 66/66; negative control 2 expected failures |
| `ant phantom-skeleton-test` | PASS, exit 0 | 12/12 |
| `ant phantom-headless-player-test` | PASS, exit 0 | 18/18 |
| `ant phantom-headless-player-performance-smoke` | PASS, exit 0 | 2/2 |
| pre-commit `ant verify` | PASS, exit 0 | all cumulative routes, 42 s |
| pre-commit `ant jar` | PASS, exit 0 | GameServer/LoginServer/installer, 11 s |
| pre-commit `verify-task-005.ps1` | PASS, exit 0 | 69/69 |

Performance smoke сохранил 6 effects для одного fixture и 60 effects для
десяти последовательных fixtures, dropped records `0`; suite `2/2`.

Production `GameServer.jar` содержит 11 directory/class entries core profile
package и `0` entries из `org/l2jmobius/tests/`.

## Static verifier

`tools/phantoms/verify-task-005.ps1` read-only и deterministic. Он проверяет
base/ordinary-child shape, exact scope, замороженные Task 004 production
artifacts/config, exact schema, profile/component contracts, optimistic SQL,
отсутствие wiring/future domain models, tests, provenance, documentation,
UTF-8, mojibake, escaped Cyrillic, credentials и binaries.

Финальные pre-commit и два byte-identical post-commit результата будут
зафиксированы в immutable handoff.

## Scope, отклонения и ограничения

Изменения ограничены exact allowlist Task 005. Другие хроники, production
config, Task 004 seam, `Player`, `GameClient`, protocol, dependencies и CI не
изменялись.

Bounded exception к обычному лимиту 8–10 файлов составляет 24 файла: восемь
из них — поставленный task package, остальные образуют одну неделимую artifact
family schema/model/repository/tests/verifier и обязательный provenance/report
набор, прямо перечисленный exact allowlist задачи. Независимых подсистем или
следующего Goal в commit нет.

Во время development два ранних profile suite run выявили несовместимые
предположения schema verifier о JDBC numeric metadata и nullable default
MariaDB. Они исправлены локально нормализацией `Number` и `NULL`; финальная
suite `18/18`. Один ранний headless run выявил late shared workers и привёл к
описанной bounded test-only стабилизации. Production ThreadPool не менялся.

Ограничения:

- envelope не интерпретирует component bytes;
- optional character link не подтверждает существование character;
- repository не выполняет automatic recovery/retry;
- нет runtime wiring и materialization;
- Goal 006 не начат.

## Branch, parent, subject и manual gate

```text
Branch: feature/phantom-world
Parent: f5b66c4edf1ddf18e044ef8c692d70ecea616485
Subject: feat(phantoms): add profile persistence envelope
Commit: 9d0465eb62f9913644fab9f1d60feb2f4fd9a674
Push/remote: exact
Manual gate: ACCEPT
Goal 006: ALLOWED
```

## Независимое закрытие Goal 005

Зафиксированы следующие факты независимого приёмочного прохода:

- profile persistence suite: `18/18`;
- три последовательных headless regression run: `18/18`, `18/18`, `18/18`;
- финальный verifier: `69/69` дважды, outputs byte-identical;
- production DB `l2jmobiush5`: без доступа и изменений;
- provisioning aggregate SHA-256:
  `20ECFDBD9BAEE625126CF53062B6E72433C7BE5604B0844FEEDD28F581BE067E`;
- verifier SHA-256 Goal 005, восстановленный из локального сохранённого
  final handoff с двумя byte-identical run `69/69`:
  `483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97`;
- independent review: `ACCEPT`;
- Goal 006: `ALLOWED`.

SHA-256
`39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9`
относится только к двум verifier run `66/66` Task 004B, как указано выше, и
не является provenance Goal 005.

Два ограниченных follow-up перенесены в Goal 006: ownership-scoped очистка
profile test rows с foreign sentinel и value equality/hash для component
payload. Они не блокируют принятый baseline Goal 005.
