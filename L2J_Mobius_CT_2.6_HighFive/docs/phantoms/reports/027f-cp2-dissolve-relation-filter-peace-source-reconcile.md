# Goal 027F — CP2 dissolve relation filtering + peace source reconciliation

## Status

- Goal027F: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- CP2: CHANGES_REQUIRED_PENDING_027F_INDEPENDENT_REVIEW.
- Goal027: IN_PROGRESS.
- Required parent: exact b6e2ffbcd7d373366ce00a8e140a2d1b2ccdd2e6.
- Branch: feature/phantom-world.
- occurred_context_compaction: no.

## Summary

Устранены ровно два Phantom-side дефекта независимого CP2 review. Native Goal027C/027D/027E seam не изменялся.

R027F-01: dissolve теперь использует canonical Goal027E proof как exact clan-id set. Bounded actor, goal sources и persisted relationReferences остаются только кандидатами для доказательства managed Phantom current leader каждого proof member. Кандидаты вне proof игнорируются; отсутствующий, REAL-only, не-current-leader или принадлежащий другой alliance proof member блокирует mutation. Global clan/profile scans не добавлены.

R027F-02: source bilateral peace offer сохраняет exact WAR_PEACE/PREPARED с exact warId. После target consent или внешнего завершения той же войны source pulse сохраняет WAR_PEACE/COMPLETED с cooldown и завершается COMPLETE без Goal018 event. Target acceptance остаётся единственным owner двустороннего relation event. Persisted W1 PREPARED против W2 завершается STALE.

Schema v2 достаточна; schema v3, migrations, таблицы, config keys, workers, caches и native seams не добавлялись.

## Changed files

1. java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java
   - proof-directed matching exact Goal027E membership set;
   - source WAR_PEACE PREPARED persistence и source-only completion reconciliation без relation event;
   - existing withPhase() сохраняет COMPLETED cooldown.
2. test/java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanGoal027Checkpoint2Suite.java
   - scenario 03: unrelated managed relation peer C вне A/B proof не блокирует dissolve; unexpected REAL C внутри proof продолжает блокировать;
   - scenario 05: exact source W1 PREPARED, W1→W2 STALE, target-owned single bilateral Goal018 event, source W2 COMPLETE/cooldown без duplicate;
   - scenario 06: external W1 end даёт source COMPLETE без fake agreement event и suppresses immediate WAR_DECLARE.
3. docs/phantoms/reports/027f-cp2-dissolve-relation-filter-peace-source-reconcile.md
   - этот отчёт.

User task packages остались read-only и не входят в staging.

## Architecture decisions

- Canonical membership proof является единственным authority exact alliance set.
- managedRelations(...) не менялся и остаётся bounded candidate resolver; результат фильтруется по proof clan ids до alliance validation.
- Source PREPARED определяется deterministic evidence hash, вычисляемым из goal identity и exact warId; existing schema v2 fields не переопределялись и не расширялись.
- Source reconciliation выполняет persisted PREPARED→COMPLETED и terminal receipt без вызова recordRelation; target/recovery path сохраняет существующее ownership Goal018 event.

## DB, migrations and configs

- Production DB l2jmobiush5 не использовалась и не изменялась.
- CP1 gate использовал штатную test configuration/schema.
- Миграции, schema v3, таблицы и config keys не добавлялись.

## Commands and test results

Первый вызов ant phantom-clan-checkpoint2-goal027-test не стартовал: ant отсутствовал в PATH. После bounded lookup все gates запускались через C:/Users/endim/.cache/codex-ant/apache-ant-1.10.17/bin/ant.bat.

Финальная последовательность:

1. phantom-clan-checkpoint2-goal027-test — PASS, 8/8, seed 27002702, 21 s.
2. phantom-clan-alliance-membership-proof-goal027e-test — PASS, 6/6, seed 27002750, 19 s.
3. phantom-clan-checkpoint1-goal027-test — PASS: CP1 focused 6/6, profile persistence 18/18, chat observation 2/2, 23 s.
4. phantom-social-events-test — PASS, 4/4, seed 18001801, 21 s.
5. Ровно один jar — PASS, 17 s; штатно созданы и скопированы GameServer.jar и LoginServer.jar.

Broad aggregates, performance, stress, soak и production DB команды не запускались.

## Performance and bounds

Новых global scans, threads, futures, workers, caches или unbounded collections нет. Existing bounds MAX_RELATION_REFERENCES=16, active operations и receipts не менялись. Performance measurement не требовался narrow corrective task и не запускался.

## Static, encoding and scope checks

- git -c core.whitespace=cr-at-eol diff --check — PASS.
- Strict UTF-8 decode production/test changed files — PASS.
- Mojibake-маркеры в изменённых файлах проверены: PASS.
- Escaped Cyrillic в изменённых файлах проверены: PASS.
- Exact pre-report changed scope — только production owner и CP2 focused suite.
- Frozen native Goal027C/D/E files, repository, store/schema и user task packages не изменены.
- Финальный report-inclusive scope и cached diff проверяются перед commit.

## Deviations, limitations and risks

- apply_patch не вызывался согласно Windows contract. Использованы small unique exact-anchor UTF-8 without BOM temp+atomic replacements.
- Первый atomic replace с null backup path не применился из-за runtime API; исходник остался неизменённым. Повтор выполнен через exact local backup path, временные artifacts удалены.
- Независимый Goal027F review ещё не выполнен; CP2 не получает ACCEPT в этой задаче.

## Git and delivery

Git-команды разрешены task package и Agents.md для exact parent/branch/scope/diff/commit/push контроля. Использовались bounded git status --short, git branch --show-current, git rev-parse, exact/relative git diff, git diff --check; delivery добавит exact-path git add, cached inspections, ordinary git commit и git push origin feature/phantom-world.

Planned commit subject: fix(phantoms): reconcile clan diplomacy edges.

Commit SHA и push result фиксируются в финальном сообщении после atomic commit/push; self-referential SHA не может быть записан внутрь того же commit без amend, который запрещён.

## Next step

Независимый review Goal027F. До него CP2 остаётся CHANGES_REQUIRED_PENDING_027F_INDEPENDENT_REVIEW, Goal027 — IN_PROGRESS.
