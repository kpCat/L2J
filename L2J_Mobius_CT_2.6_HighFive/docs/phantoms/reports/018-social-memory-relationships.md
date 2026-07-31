# Goal 018 — social memory and relationships

Status: `SUCCESS`

## Summary

Реализована одна bounded social-state capability: строгий hashed catalog,
детерминированная personality, compact `social.state`, асимметричные
relationships/reputation/debt/agreement counters, important memory, lazy
integer decay/expiry, idempotent ingestion, explainable modifiers и downstream
Party event sink.

Goal 017 зафиксирован как `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`; verifier 017
читает pinned accepted blobs и допускает descendant HEAD. Consent на
prepare/accept/member/leader transition требует exact ACTIVE goal.

Не реализованы text, Semantic Pack, chat, social actions, PvP/PK, clans,
economy, matchmaking или новые support/death producers.

## Git

- Branch: `feature/phantom-world`.
- Required parent: `6cf261370e3cb98158805828e995cfe6e8b14651`.
- Upstream до commit: `origin/feature/phantom-world` на required parent.
- Required subject: `feat(phantoms): add social memory and relationships`.
- Commit SHA: self-referential commit containing this report; exact SHA is
  reported by the post-commit verifier and final response.
- Push result: verified after the ordinary commit; exact remote result is
  reported by the post-commit verifier and final response.
- Scope до отчёта: 29 файлов; с отчётом: 30, limit 32.
- New production/data files: 7, limit 10.
- Changed production/data/config files: 11, limit 15.
- Git-команды разрешены явным запросом Goal и workflow contract.
- Read-only контроль выполнялся командами `git status --short --branch`,
  `git branch --show-current`, `git rev-parse HEAD`,
  `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`,
  `git rev-parse origin/feature/phantom-world`, `git diff --check`,
  `git diff --name-status`, `git diff --stat` и verifier-internal
  `git show`, `git log`, `git merge-base --is-ancestor`, `git ls-files`.
- Финальные mutation-команды: `git add -- <exact 30-file allowlist>`,
  `git commit -m "feat(phantoms): add social memory and relationships"` и
  `git push origin feature/phantom-world`.
- Amend, force push, rebase, squash и merge не используются.

## Initial read set

- `Agents.md`, workflow contract и task package standard.
- Полный пакет `docs/phantoms/tasks/018-social-memory-relationships`.
- Goal 018 section master plan и текущие Goal 017 report/review.
- Component API `PhantomProfileRepository`/`PhantomProfileComponent`.
- `PhantomGoal`, planning context, candidate/consideration contracts.
- Party coordinator injection/terminal/membership ranges и Party decision.
- PhantomSystem production construction/snapshot/shutdown ranges.
- Historical verifier 017.
- Profile/Party in-memory и real test-DB fixtures.

`README.md`, общий code-map и pattern-файлы для Goal 018 не найдены;
повторный поиск не выполнялся.

## Six additional exact files/symbols

1. `PhantomPartyRoleCatalog` — переиспользован strict hashed XML loader pattern.
2. `PhantomPartyStore` — переиспользован optimistic profile-component adapter.
3. `PhantomPlayersConfig` — сохранён fail-closed parser и legacy default pattern.
4. `PhantomPlayers.ini` — сохранены local grouping и config comment style.
5. `PhantomTestLauncher` — переиспользована exact mode dispatch convention.
6. `build.xml` — переиспользованы focused/DB/aggregate Ant target patterns.

## Changed files

Production/data/config:

- `phantoms/social/PhantomSocialModel.java`
- `phantoms/social/PhantomSocialCatalog.java`
- `phantoms/social/PhantomSocialStateCodec.java`
- `phantoms/social/PhantomSocialStore.java`
- `phantoms/social/PhantomSocialService.java`
- `phantoms/social/PhantomSocialEventSink.java`
- `phantoms/party/PhantomPartyCoordinator.java`
- `phantoms/PhantomSystem.java`
- `config/custom/PhantomPlayersConfig.java`
- `dist/game/config/Custom/PhantomPlayers.ini`
- `dist/game/data/phantoms/social/high-five-social-v1.xml`

Tests/build/tools:

- `PhantomSocialSuite.java`, `PhantomSocialTestDoubles.java`
- `PhantomSocialPartyIntegrationSuite.java`
- targeted `PhantomPartySuite.java`, `PhantomSkeletonSuite.java`
- `PhantomTestLauncher.java`, `build.xml`
- `verify-task-017.ps1`, `verify-task-018.ps1`

Docs:

- Goal 017 final review, social architecture contract, this report.
- Goal 018 task package включён без изменения payload.
- Master-plan status: Goal 017 accepted with contracts; Goal 018 pending review.

## Architecture decisions

- XML — byte-content authority; runtime hash:
  `C3F6ECF67B66C82B92ADA4C4A92C57DDCBC370A3C3BEFA1B06C961BFA44210ED`.
- Component: `social.state`, schema 1, existing profile component table.
- Worst-case 16 traits + 24 relationships + 24 memories: 3768 bytes.
- Personality: SHA-256(catalog hash, seed 18001801, profile, trait code).
- Subjects: only `PHANTOM_PROFILE` или `CHARACTER_OBJECT`.
- State ownership: one service, 64 stripes, maximum three optimistic attempts,
  bounded access-order cache, no worker/thread/executor/Future/task.
- Query decay/expiry is a read-only projection; mutation materializes once.
- Party emits only after canonical/durable fact; social failure is counted and
  never rolls back canonical Party.
- Two-member canonical expel accepts absent Party observation only with exact
  `MembershipOutcome.COMPLETED`.

## DB, migrations and configs

- Использовалась только `l2jmobiush5_phantom_test`.
- Production DB не изменялась и не называлась production code.
- Schema/migrations/SQL repository не изменялись.
- Добавлен один key: `PhantomSocialCacheProfiles`, default 1024, range 16..10000.
- Phantom World остаётся disabled by default.

## Development commands and results

- `phantom-party-lifecycle-test` preflight: 11/11 PASS.
- Initial seven non-DB social modes: 23/23 PASS.
- Initial DB social Party mode: 2/3 PASS; найден двухучастниковый expel edge.
- Exact DB retry после bounded fix: 3/3 PASS.
- Post-hardening catalog: 3/3 PASS.
- Post-hardening codec: 3/3 PASS.
- Exact affected:
  - Party lifecycle 11/11 PASS;
  - profile persistence 18/18 PASS;
  - skeleton/config 14/14 PASS;
  - shutdown handoff 7/7 PASS.
- Historical verifier 017: `TASK017_VERIFIER_OK`.
- Working verifier 018: `TASK018_VERIFIER_OK`, scope 30, production 11,
  new production 7.
- Final `phantom-social-test`: 26/26 PASS, `BUILD SUCCESSFUL`, 32 s.
- Единственный full `ant verify`: `BUILD SUCCESSFUL`, 11 min 57 s.
- Standalone `ant jar`: `BUILD SUCCESSFUL`, 15 s.

Ant был вызван через
`.\.phantom-local\apache-ant-1.10.17\bin\ant.bat`; bare `ant` отсутствует в PATH.

## Performance

- 100000 pure modifier/decay evaluations: 609288300 ns, DB writes 0.
- 10000 deterministic profiles: cache remained <=16 in the smoke fixture.
- Worst-case payload: 3768/4096 bytes.
- Stopped service: operation claims 0, write claims 0.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Deviations, limitations and risks

- Один exact targeted retry выполнен после реального DB integration defect;
  broad historical affected aggregate не запускался.
- Important-memory и idempotency horizon bounded 24 retained event IDs; более
  длинный ledger требует отдельного persistence contract.
- Catalog drift возвращает `AUTHORITY_STALE`; migration сознательно отсутствует.
- Support event объявлен для будущего producer, но Goal 018 его не публикует.
- Commit/push и два byte-identical post-commit verifier выполняются после
  фиксации этого отчёта; их exact evidence остаётся в final response.

## Next step

Независимый review Goal 018. Goal 019/020/021/025 не начаты.
