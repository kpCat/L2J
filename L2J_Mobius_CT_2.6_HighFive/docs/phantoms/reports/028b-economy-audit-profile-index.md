# Goal 028B — economy audit profile/audit index

## Status

- Delivery status: `SUCCESS`.
- Goal 028B: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 Checkpoint 4: `BLOCKED_PENDING_028B_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `991a8a8e7446d76ebba360f722e3baff226700d6`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Добавлен ровно отсутствовавший secondary index `idx_phantom_economy_audit_profile_audit(profile_id, audit_id)` для fresh install и existing DB. CP4 не возобновлялся: Java/Admin/System/roadmap не читались для реализации и не изменялись.

Fresh DDL сохраняет `PRIMARY (audit_id)`, unique operation key, существующий `idx_phantom_economy_audit_profile_created(profile_id, created_at, audit_id)` и FK на `phantom_profiles` без изменений.

## Read-first и upgrade mechanism choice

Прочитаны обязательные project/task документы, принятый CP4 blocker-report, целевой fresh DDL, bounded структура/config/scripts/archive `dist/db_installer`, exact Goal022 test DB guard и единственный существующий standalone migration-аналог.

В `dist/db_installer` обнаружены только fresh SQL-каталоги `sql/game` и `sql/login`; upgrade/update/migration/version каталогов, version table convention или installer archive entries для incremental migrations нет. Файлы с `doorupgrade` в имени являются fresh table DDL и не образуют upgrade mechanism. Поэтому новый auto-discovered installer directory не создавался; выбран прямо разрешённый TASK standalone artifact:

`docs/phantoms/migrations/028b-add-economy-audit-profile-audit-index.sql`.

## Changed files

1. `dist/db_installer/sql/game/phantom_reservations.sql` — в `phantom_economy_audit` добавлена одна строка fresh DDL:
   `KEY idx_phantom_economy_audit_profile_audit (profile_id, audit_id)`.
2. `docs/phantoms/migrations/028b-add-economy-audit-profile-audit-index.sql` — standalone idempotent existing-DB migration.
3. `docs/phantoms/reports/028b-economy-audit-profile-index.md` — этот отчёт.

User-owned untracked task packages оставлены read-only и не входят в commit.

## Fresh DDL proof

Bounded diff относительно required parent содержит ровно одну строку в fresh DDL:

```sql
KEY `idx_phantom_economy_audit_profile_audit` (`profile_id`, `audit_id`),
```

Соседние строки `PRIMARY`, `uq_phantom_economy_audit_operation`, `idx_phantom_economy_audit_profile_created` и `fk_phantom_economy_audit_profile` совпадают с required parent. Fresh DDL index встречается ровно один раз.

## Existing-DB migration semantics

Migration читает `information_schema.statistics` только для current `DATABASE()`, таблицы `phantom_economy_audit` и exact index name.

- Index отсутствует: dynamic SQL выполняет `ALTER TABLE ... ADD KEY (profile_id, audit_id)`.
- Exact named non-unique full-column BTREE `(profile_id, audit_id)` с seq 1/2: no-op success.
- Same-name index с другим количеством, columns/order, direction, prefix, uniqueness или index type: выбирается тот же `ALTER ... ADD KEY`; MariaDB намеренно отказывает с duplicate key name. Migration не содержит `DROP`, replace или data rewrite.
- Runtime Java auto-DDL не добавлялся.

## Allowlisted DB verification

Использован только existing `.phantom-local/Database.test.ini`, проверенный по exact Goal022 guard metadata: local host, port `3308`, database `l2jmobiush5_phantom_test`, user `l2j_phantom_test`. Credentials не выводились и не выдумывались. Production `l2jmobiush5` не открывалась.

Временный JDBC verifier создавался в Windows temp, не добавлялся в repository и был удалён после запуска. Результаты:

```text
DB_GUARD=PASS catalog=l2jmobiush5_phantom_test user=l2j_phantom_test
INITIAL_TARGET_INDEX=rows=0 columns=
APPLY_ONCE=rows=2 columns=profile_id,audit_id
SHOW_INDEX seq=1 column=profile_id non_unique=1 type=BTREE
SHOW_INDEX seq=2 column=audit_id non_unique=1 type=BTREE
APPLY_TWICE_IDEMPOTENT=rows=2 columns=profile_id,audit_id
PRESERVED_INDEX=idx_phantom_economy_audit_profile_created(profile_id,created_at,audit_id)
PRESERVED_INDEX=PRIMARY(audit_id)
PRESERVED_INDEX=uq_phantom_economy_audit_operation(operation_id)
PRESERVED_FK_COUNT=1
INTERLEAVED_QUERY=PASS profile=71001 rows=8 descending=true lastAuditId=9
EXPLAIN_SUPPORTING key=idx_phantom_economy_audit_profile_audit rows=12 Extra=Using where; Using index
EXPECTED_WRONG_SHAPE_ERROR state=42000 code=1061 message=Duplicate key name 'idx_phantom_economy_audit_profile_audit'
WRONG_SHAPE_FAIL_SAFE=true preserved=rows=2 columns=audit_id,profile_id
FINAL_RESTORED_INDEX=rows=2 columns=profile_id,audit_id
```

Wrong-shape negative control временно создавал same-name `(audit_id, profile_id)` только в allowlisted test DB. Migration отказала без изменения wrong shape. В `finally` target index восстановлен exact `(profile_id, audit_id)`; существующие PK/unique/profile-created/FK повторно подтверждены.

Interleaved query выполнялась над temporary InnoDB probe table с тем же exact index и 24 чередующимися строками двух profiles:

```sql
WHERE profile_id=?
ORDER BY audit_id DESC
LIMIT 8
```

Она вернула ровно 8 строк одного profile в строгом descending audit order. `EXPLAIN` приведён только как supporting evidence и выбрал target index без filesort.

## Static verification

- Оба SQL artifact сохранены как UTF-8 without BOM.
- Fresh DDL line endings сохранены в исходном LF-стиле.
- Migration содержит exact `information_schema.statistics` checks и не содержит `DROP INDEX`, `REPLACE` или data rewrite.
- mojibake-маркеры в изменённых файлах проверены.
- escaped Cyrillic в изменённых файлах проверены.

## Commands and test results

Выполнены:

- `git status --short --branch`, `git branch --show-current`, `git rev-parse HEAD`, `git rev-parse --show-toplevel`, upstream check — branch/parent/upstream PASS; обнаружены только user-owned untracked task packages.
- bounded `rg`/directory/archive inspection в `dist/db_installer` — supported incremental mechanism отсутствует.
- exact-anchor UTF-8-no-BOM temp+atomic edits — PASS; `apply_patch` не вызывался.
- bounded static SQL/key/FK/BOM/line-ending checks — PASS.
- temporary JDBC DB gate — PASS, evidence выше.
- `git diff --check` и final exact diff/scope verification — PASS.

`jar` не запускался: committed Java verifier или production Java changes отсутствуют. CP4, broad/domain/performance/stress/soak gates не запускались согласно TASK.

## Performance measurements

Отдельные performance/stress/soak измерения запрещены scope и не запускались. Bounded query вернула `LIMIT 8`; supporting `EXPLAIN` выбрал `idx_phantom_economy_audit_profile_audit` и сообщил `Using where; Using index`.

## Configs, DB and transactions

Config files не изменялись. Production DB не использовалась. Existing test DB после negative control оставлена с exact новым index; остальные audit keys и FK сохранены. Migration выполняет только schema DDL и не переписывает данные.

## Deviations, limitations and risks

- Штатный incremental installer mechanism отсутствует, поэтому использован явно разрешённый standalone migration.
- Первый Windows `File.Replace` не принял пустой backup path; исходный DDL остался неизменён, после exact temp/path validation выполнен same-directory atomic overwrite move.
- Первый temporary verifier некорректно split диагностический SQL literal с внутренней точкой с запятой; до ALTER он не дошёл. Literal был упрощён без изменения semantics, повторный полный DB gate прошёл.
- Migration намеренно оставляет same-name wrong-shape index нетронутым и требует manual resolution после deliberate failure.
- Independent review остаётся обязательным; CP4 нельзя возобновлять до принятия Goal 028B.

## Git and delivery

Разрешённые Git-команды использованы только для required parent/branch/upstream, bounded exact diff/scope verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не использовались.

Preferred commit subject: `fix(phantoms): index economy audit by profile`.

Commit SHA и push result фиксируются в финальном сообщении после ordinary commit/push.

## Next step

Независимый review Goal 028B. До его результата Goal 028 Checkpoint 4 остаётся `BLOCKED_PENDING_028B_INDEPENDENT_REVIEW`, а Goal 028 — `IN_PROGRESS`.
