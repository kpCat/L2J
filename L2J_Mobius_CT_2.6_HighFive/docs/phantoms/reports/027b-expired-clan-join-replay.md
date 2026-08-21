# Goal 027B — expired clan.join replay-safe terminalization

## Status

SUCCESS

## Review state

IMPLEMENTED_PENDING_INDEPENDENT_REVIEW

## occurred_context_compaction

no

## Summary

Закрыт только R027B-01. First-touch expired `clan.join` теперь после единственной canonical cleanup-попытки всегда получает exact terminal `EXPIRED` receipt в существующем bounded ledger. Повтор exact `(profileId, goalId, revision)` возвращает сохранённый terminal result до любого нового observation/response, поэтому более позднее приглашение больше не принадлежит завершённой цели.

Goal 027 Checkpoint 2 не начинался. Другие хроники, production DB, packet handlers, persistence schema, конфиги и пользовательские untracked task packages не изменялись.

## Root cause

В required parent `7ebdcc5fbcd29f1fc0dab19832fd055936435929` метод `PhantomClanService.advance(...)` проверял `_terminal` до expiry cleanup, но first-touch expired path без exact `_active` operation:

1. наблюдал current invitation;
2. вызывал exact `REFUSE`;
3. возвращал новый `AdvanceResult(EXPIRED, ...)` напрямую;
4. не вызывал `terminalize` и не записывал `_terminal`.

Поэтому replay той же старой цели мог заново наблюдать и отклонить более позднее приглашение.

## Read-first / local patterns

Прочитаны:

- `Agents.md`;
- корневой `README.md`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
- `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
- весь пользовательский package `docs/phantoms/tasks/027b-expired-clan-join-replay/`;
- `docs/phantoms/reports/027-checkpoint-1-persistent-clan-organizations.md`;
- `docs/phantoms/reports/027a-clan-consent-chat.md`;
- целевые участки `PhantomClanService`, `ClanInvitationService`, clan suite, launcher и `build.xml`;
- diff required parent Goal 027A в целевых файлах;
- delivery-status участки master plan и roadmap.

Parent `AGENTS.md` и модульный `README.md` отсутствуют. SHA-256 всех шести файлов task package совпали с `MANIFEST.sha256`.

Переиспользованы локальные паттерны:

- existing exact `OperationIdentity` и bounded `_terminal` ledger;
- existing `terminalize(Operation, ...)`, который удаляет только matching active instance и сохраняет exact terminal result;
- `ClanInvitationService.respond(..., expectedIdentity)` compare-before-remove;
- существующее Goal 027A mode/launcher/Ant-target wiring.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomClanGoal027Checkpoint1Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/027b-expired-clan-join-replay.md`

## Architecture decisions

1. First-touch expired join использует transient exact `Operation(identity, goal)`, если exact active operation отсутствует. Transient operation не добавляется в `_active`.
2. Cleanup по-прежнему наблюдает одно current invitation, захватывает exact `InvitationIdentity` и делегирует `REFUSE` canonical backend.
3. Независимо от pending/no-pending/stale результата cleanup выполняется существующий `terminalize`, который помещает `EXPIRED` в bounded `_terminal`.
4. Replay barrier остаётся operation-scoped и проверяется до clock/backend access.
5. `ClanInvitationService`, `L2jPhantomClanBackend`, packet handlers, persistence и real-client semantics не менялись.

## Replay-safety evidence

Focused mode `EXPIRY_REPLAY_027B`, launcher key `clan-expired-replay-goal027b`, seed `27002712` и target `phantom-clan-expired-replay-goal027b-test` доказывают:

- first-touch expired + I1: exact `REFUSE(I1.identity)` ровно один раз, `EXPIRED`, terminal ledger count 1, active count 0;
- I2 после terminal result: exact replay возвращает равный сохранённый result, не делает даже invitation observation, не вызывает response, оставляет I2 pending и membership неизменной;
- first-touch expired без invitation: terminal barrier записывается, появившееся позже I2 не наблюдается и не меняется;
- replacement I1 -> I2 между observation/response: response использует I1 identity, fake canonical compare-before-remove возвращает stale, I2 остаётся, replay его не наблюдает;
- REAL target сохраняет manual pending invitation и получает zero Phantom auto-response.

## Regression evidence

Существующие Goal 027A assertions не ослаблялись. Отдельно зелёные:

- exact mismatch/matching consent;
- expiry cleanup;
- revision replacement;
- explicit cancel;
- service stop;
- stale identity;
- REAL manual path;
- explicit clan chat Decision/idempotency;
- Goal 027 CP1 creation/restart, recruitment, roles, treasury, chat;
- profile persistence 18/18;
- chat observation 2/2.

## DB / migrations / configs

Новых DB calls, migrations, таблиц, schema changes и config keys нет.

Рабочая `l2jmobiush5` не использовалась. Focused clan suites не обращались к DB. Предписанный CP1 aggregate запустил существующий `phantom-profile-persistence-test` через штатный test DB guard; production DB не изменялась.

## Commands and exact results

Baseline:

- `git status --short --branch` — exit 0; `feature/phantom-world...origin/feature/phantom-world`; tracked baseline clean; показаны только пользовательские untracked task packages.
- `git rev-parse --show-toplevel` — exit 0; `C:/Users/endim/L2J_Mobius`.
- `git rev-parse HEAD` — exit 0; exact required parent `7ebdcc5fbcd29f1fc0dab19832fd055936435929`.
- `git branch --show-current` — exit 0; `feature/phantom-world`.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — exit 0; `origin/feature/phantom-world`.

Verification:

- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-expired-replay-goal027b-test` — exit 0; `BUILD SUCCESSFUL`; seed `27002712`; 4/4 PASS; 2199 production + 100 test sources compiled; 20 seconds.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-consent-chat-goal027a-test` — exit 0; `BUILD SUCCESSFUL`; seed `27002711`; 2/2 PASS; 17 seconds.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-checkpoint1-goal027-test` — exit 0; `BUILD SUCCESSFUL`; clan creation/restart 2/2, recruitment 1/1, roles 1/1, treasury 1/1, chat Decision 1/1, profile persistence 18/18, chat observation 2/2; 19 seconds.
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat jar` — единственный финальный jar; exit 0; `BUILD SUCCESSFUL`; 2199 production sources; LoginServer/GameServer/DatabaseInstaller JARs созданы, server JARs скопированы в рабочий `dist/libs`; 16 seconds.
- `git diff --check` — PASS, exit 0; whitespace errors отсутствуют; вывод содержит только informational LF-to-CRLF working-copy warnings.
- exact scope guard — PASS: tracked allowlist 6/6 + новый report 1/1, другие хроники 0, task package SHA-256 6/6 unchanged.
- mojibake-маркеры в изменённых файлах проверены — PASS: `rg` exit 1, совпадений нет.
- escaped Cyrillic в изменённых файлах проверены — PASS: `rg` exit 1, совпадений нет.

Не запускались plain `ant verify`, Goal 026, broad Party/Combat/PvP/all-Phantom, stress/soak и дополнительные jar.

## Performance / lifecycle

Fix остаётся O(1): один lookup active operation, максимум одно invitation observation/response и одна bounded terminal insertion. Full scan, DB I/O, новые коллекции, locks, timers, workers, threads, schedulers и futures не добавлены.

Существующие limits сохранены: максимум 64 active operations и 256 terminal receipts. Transient expired operation не расходует active capacity. Replay возвращается из `_terminal` до clock/backend access.

## Deviations

Функциональных deviations от TASK нет.

Первый sandboxed Git/read запуск был отклонён Windows ACL helper. После разрешения выполнены те же read-only команды. `apply_patch` также не смог прочитать рабочие файлы из-за `apply deny-read ACLs`; согласно локальному Windows fallback использованы небольшие exact-match atomic replacements по одному файлу с single/exact occurrence checks, сохранением UTF-8 BOM/no-BOM и без EOL churn.

occurred_context_compaction: no.

## Limitations / risks

Terminal ledger остаётся существующим bounded in-memory lifecycle ledger; новая persistence не добавлялась и TASK её запрещал. Независимое review должно проверить operation ownership barrier и exact identity race evidence.

Goal 027 CP1 не объявляется ACCEPT. Goal 027 overall остаётся IN_PROGRESS. Checkpoint 2 остаётся NOT_STARTED.

## Git delivery

- branch: `feature/phantom-world`
- required parent: `7ebdcc5fbcd29f1fc0dab19832fd055936435929`
- commit subject: `fix(phantoms): make expired clan join replay safe`
- commit SHA: этот единственный delivery commit включает данный отчёт; exact SHA приводится в финальном сообщении
- push result: exact remote result и equality local HEAD/origin приводятся в финальном сообщении
- пользовательские untracked task packages не добавляются
- git commands разрешены TASK/workflow; amend/rebase/reset/squash/merge/force push не используются

## Next step

Независимое review Goal 027B. До verdict:

- Goal 027 Checkpoint 1 — `CHANGES_REQUIRED pending Goal 027B independent review`;
- Goal 027A — `CHANGES_REQUIRED after independent review; corrective Goal 027B pending`;
- Goal 027B — `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 027 overall — `IN_PROGRESS`;
- Goal 027 Checkpoint 2 — `NOT_STARTED`.