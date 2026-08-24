# Goal 029B — guarded test DB reprovision + Goal029A closeout

## Status

- Delivery status: `SUCCESS`.
- Goal029B: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal029A: `BLOCKER_CLOSED_PENDING_029B_INDEPENDENT_REVIEW`.
- Goal029 CP1: `CHANGES_REQUIRED_PENDING_029B_INDEPENDENT_REVIEW`.
- Goal029 overall: `IN_PROGRESS`.
- Required parent/current pre-delivery HEAD: exact `6abcb0e2233eb296aa0a8a5c04242e3e55030864`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Canonical guarded test DB provisioner выполнен ровно один раз и успешно восстановил allowlisted локальную Phantom test DB. Ручной SQL, ручное редактирование local manifest, DB metadata или `Database.test.ini`, обход guard и повтор provisioning не применялись. Production database не была целью и `dist/game/config/Database.ini` не изменялся.

После provisioning authoritative production materialization target прошёл `20/20`, включая обязательные cases 05/12/13. Focused Goal029 CP1 прошёл `6/6`. После всех test gates выполнен ровно один final `jar`, результат PASS.

## Exact tracked changed files

1. `docs/phantoms/reports/029b-guarded-test-db-reprovision-029a-closeout.md` — этот SUCCESS-отчёт.

Java, build, config, schema, roadmap и user task packages не изменялись. На SUCCESS других tracked файлов нет.

## Guarded DB identity and schema manifest

Exact canonical DB identity:

- host: `127.0.0.1`;
- port: `3308`;
- database: `l2jmobiush5_phantom_test`;
- dedicated login: `l2j_phantom_test`;
- local config: `.phantom-local/Database.test.ini`;
- local manifest: `.phantom-local/schema-manifest.properties`.

Non-secret current schema manifest metadata:

- schema version: `1`;
- login scripts: `4`;
- game scripts: `115`;
- test migrations: `2`;
- total scripts: `121`;
- statements: `214`;
- aggregate SHA-256: `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.

Provisioner записал и сверил DB metadata с тем же current inventory. Последующий `phantom-production-materialization-test` прошёл bootstrap freshness/DB metadata guard и все cases, что подтверждает exact соответствие local manifest, DB metadata и repository SQL inventory.

Admin credential values не выводились и не записывались в tracked report/config/source. Provisioner подтвердил, что credentials переданы только через environment и не записаны.

## Commands and results

Read-only preflight/safety:

- `git rev-parse --show-toplevel` — Git root подтверждён;
- `git status --short --branch` — branch/upstream и существующие user-owned untracked task packages подтверждены;
- `git rev-parse HEAD` — exact required parent PASS;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`;
- bounded `git diff -- ...` для build/guard/provisioner/manifest/config — safety sources и production config без tracked diff;
- read-only audit подтвердил destructive constants: exact test DB, dedicated user, port 3308 и explicit production-schema prohibition.

Required execution order:

1. В одном PowerShell process заданы три nonblank `PHANTOM_DB_ADMIN_*` environment variables и выполнен `.phantom-local/apache-ant-1.10.17/bin/ant.bat prepare-phantom-test-db` — PASS с первой и единственной попытки, total time 24 s.
2. `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-production-materialization-test` — PASS, seed `20260725001`, total `20`, passed `20`, failed `0`, total time 35 s.
   - case 05 `cap-release-and-readmission` — PASS;
   - case 12 `action-timeout-retains-cap-and-retries` — PASS;
   - case 13 `operation-failure-retains-and-retries` — PASS.
3. `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-scale-envelope-goal029cp1-test` — PASS, seed `29002901`, total `6`, passed `6`, failed `0`, total time 17 s.
4. `.phantom-local/apache-ant-1.10.17/bin/ant.bat jar` — единственный final jar PASS, total time 14 s; LoginServer/GameServer/DatabaseInstaller jars собраны, server jars скопированы в `dist/libs`.

Broad DB suites, separate scheduler/navigation aggregates, Goal028 aggregates, performance/stress/soak, real geodata и full-world gates не запускались.

## Local artifacts, DB, config and architecture

- `.phantom-local/Database.test.ini` существует локально и ignored/untracked.
- `.phantom-local/schema-manifest.properties` существует локально и ignored/untracked.
- Оба пути покрыты existing `/.phantom-local/` ignore rule.
- Production DB `l2jmobiush5` не была целью.
- `dist/game/config/Database.ini` не изменялся.
- DB migration, schema design, production architecture и config changes отсутствуют.

## Performance measurements

Performance/soak measurements не требовались и не выполнялись. Зафиксированы только времена обязательных bounded Ant targets; они не заявляются как performance baseline.

## Static, encoding and scope

- Windows `apply_patch` не вызывался.
- Report создан как UTF-8 text.
- До report tracked diff отсутствовал.
- Final allowlist: только этот report.
- User-owned untracked task packages оставались read-only.
- Mojibake-маркеры в изменённом файле проверяются отдельным final gate.
- Escaped Cyrillic в изменённом файле проверяется отдельным final gate.
- `git diff --check`, exact diff/scope и staging allowlist выполняются перед commit.

## Deviations, limitations and risks

Отклонений от required execution sequence нет. Independent review остаётся обязательным; Goal029 overall остаётся `IN_PROGRESS`. Long-running scale/soak и release-candidate доказательства относятся к последующим checkpoints и здесь не заявляются.

## Git and delivery

TASK разрешает required baseline Git inspection, bounded safety/scope/diff verification, exact allowlist staging, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не используются.

Preferred commit subject: `test(phantoms): refresh guarded scale database`.

Commit SHA и push result указываются в финальном сообщении после ordinary commit/push; report-bearing commit не может самоссылочно содержать собственный SHA.

## Next step

Независимое review Goal029B должно проверить report-bearing commit, exact single-file tracked scope, canonical DB identity/manifest aggregate и результаты materialization cases 05/12/13, CP1 6/6 и final jar. До review Goal029 overall остаётся `IN_PROGRESS`.
