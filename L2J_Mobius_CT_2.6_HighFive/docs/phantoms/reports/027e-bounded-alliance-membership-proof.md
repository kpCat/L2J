# Goal 027E — bounded exact alliance membership proof

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Связанные статусы:

- Goal 027E: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
- Goal 027 Checkpoint 2: `BLOCKED_PENDING_027E_INDEPENDENT_REVIEW`;
- Goal 027: `IN_PROGRESS`.

## occurred_context_compaction

`no`

## Summary

Добавлен минимальный native seam для безопасного автономного dissolve: публичный immutable `AllianceMembershipProof` фиксирует exact `AllianceIdentity`, полный отсортированный current clan-id set и exact `MembershipEpoch` каждого участника. Proof читается только из durable `clan_data` bounded-запросом exact incarnation и передаётся в отдельную команду `dissolveWithProof`.

Existing REAL/native `dissolve(Player, AllianceIdentity)` не изменён. CP2 не возобновлялся. Allocator, retirement protocol, war identity/contracts, Phantom metadata/code и schema не изменялись.

## Baseline и read-first

- branch: `feature/phantom-world`;
- upstream: `origin/feature/phantom-world`;
- required parent / initial HEAD: exact `fcc5213e6550fcc27447e77f5824628b8043961e`;
- user-owned untracked task packages оставались read-only и не staged;
- прочитаны только `Agents.md`, текущий `TASK.md`, CP2 blocker report, `ClanAllianceService`, `ClanSocialRepository`/`ClanSocialPersistence`, `ClanSocialMutationFence`, exact alliance sections 027C/027D suites и `clan_data.sql`; дополнительно точечно прочитаны только необходимые registration fragments `build.xml`/`PhantomTestLauncher`;
- локальные аналоги: existing immutable `AllianceIdentity`/`MembershipEpoch`, fence-key ordering, repository `dissolveAlliance` exact-set CAS и focused suite/Ant target pattern.

## Public proof API

`ClanAllianceService` теперь предоставляет:

- `public record AllianceMembershipProof(AllianceIdentity identity, List<MembershipEpoch> memberEpochs)` — defensive `List.copyOf`, non-empty, strictly sorted unique clan ids, exact identity match каждого epoch и обязательное присутствие leader clan;
- `public record ProofResult(Status status, Reason reason, AllianceMembershipProof proof)`;
- `public ProofResult captureMembershipProof(AllianceIdentity expectedIdentity)`;
- `public Result dissolveWithProof(Player player, AllianceMembershipProof proof)`.

Existing public native dissolve signature сохранён без изменения и без overload ambiguity.

## Query и boundedness evidence

Durable source — единственный новый repository query:

```sql
SELECT clan_id, ally_id, ally_generation, ally_generation_counter
FROM clan_data
WHERE ally_id=? AND ally_generation=?
ORDER BY clan_id
```

Fresh schema уже содержит `KEY ally_id (ally_id)`, поэтому migration/schema change не добавлялись. Query возвращает только строки exact alliance incarnation и формирует immutable sorted `MembershipEpoch` list. В proof capture/dissolve отсутствуют `ClanTable.getClans()`, `ClanTable.getClanAllies()`, `_clans.values()`, новый registry/cache и Phantom metadata.

Capture делает bounded read, захватывает canonical fence keys всех observed members (включая leader), затем повторяет тот же durable exact-incarnation read под fence и принимает proof только при exact equality. Concurrent join разделяет leader key; leave/expel captured member разделяет member key.

## TOCTOU proof

`dissolveWithProof` под `ClanSocialMutationFence`:

1. блокирует все clan keys из proof;
2. отвергает retirement любого captured member;
3. повторно проверяет live leader identity и actor eligibility;
4. повторно сверяет каждый exact captured `MembershipEpoch` с canonical live member state;
5. передаёт exact ordered `clanId -> counter` set в existing repository `dissolveAlliance`;
6. repository transaction выполняет `SELECT ... WHERE ally_id=? ORDER BY clan_id FOR UPDATE`, сравнивает полный durable set, generation и каждый counter, и только затем обновляет строки;
7. live mutation/notifications происходят только после успешного durable commit.

Поэтому unexpected C/missing member достигает exact-set CAS и возвращает `STALE`; B leave/rejoin ABA или G1→G2 отсекаются identity/epoch checks; retirement возвращает `INELIGIBLE/CLAN_RETIRING`; SQL failure возвращает `PERSISTENCE_FAILURE`. Все non-success пути завершаются до live mutation, а repository stale/error transaction rollback сохраняет durable state.
## Focused suite

Новый deterministic suite `clan-alliance-membership-proof-goal027e`, seed `27002750`, содержит ровно 6 сценариев:

1. complete sorted immutable proof capture + exact member epochs;
2. A/B proof, затем unexpected canonical C — `STALE`, zero dissolve mutation;
3. B leave/rejoin ABA — old proof `STALE`, current membership сохранён;
4. G1 proof против recreated G2 — `STALE`, G2 сохранён;
5. happy exact dissolve, typed persistence failure, retirement rejection и proof-read persistence failure;
6. source boundedness: exact SQL/index/public API/fence/CAS и запрет registry/Phantom sources.

## Commands and results

Baseline/read-only:

- `git status --short --branch` — correct branch/upstream, tracked diff initially empty, только user-owned untracked task packages;
- `git rev-parse HEAD` — exact required parent;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`.

Первый `ant phantom-clan-alliance-membership-proof-goal027e-test` не стартовал: `ant` отсутствовал в `PATH`. После bounded поиска использован project-local `.phantom-local/apache-ant-1.10.17/bin/ant.bat`; это не test failure и код при первой попытке не компилировался.

Финальные gates выполнены ровно в требуемом порядке:

- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-alliance-membership-proof-goal027e-test` — `BUILD SUCCESSFUL`, 6/6;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-social-retirement-goal027d-test` — `BUILD SUCCESSFUL`, 6/6;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-social-domain-goal027c-test` — `BUILD SUCCESSFUL`, 6/6; ожидаемые controlled post-commit notification warnings не превратились в failures;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat jar` — единственный jar gate, `BUILD SUCCESSFUL`, 2203 production sources.

Не запускались CP1/027A/027B/Goal018/020/025, broad verify, performance, stress, soak и другие запрещённые gates. DB не reprovisioned. Production DB `l2jmobiush5` не использовалась; 027C/027D применили существующий allowlisted test config/database.

## Changed files

- `java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java` — public immutable proof/result, bounded capture и proof-consuming dissolve command;
- `java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java` — package-private persistence read contract и exact durable query; existing exact-set CAS не изменён;
- `test/java/org/l2jmobius/gameserver/model/clan/ClanAllianceMembershipProofGoal027ESuite.java` — 6 focused scenarios;
- `test/java/org/l2jmobius/gameserver/model/clan/ClanSocialDomainGoal027CSuite.java` — accepted fake persistence поддерживает новый read-only contract/failure injection;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — регистрация 027E suite;
- `build.xml` — seed и один focused 027E target;
- `docs/phantoms/reports/027e-bounded-alliance-membership-proof.md` — этот отчёт.

`dist/db_installer/sql/game/clan_data.sql`, migrations, Phantom production code и другие chronicles не изменялись.

## Architecture / DB / config / performance

- Новый слой, cache, registry, thread, scheduler, provider и dependency не добавлялись.
- Capture выполняет два bounded exact-incarnation reads для fenced stable proof; dissolve — O(proof members) live checks плюс existing O(current alliance rows) durable exact-set lock/CAS.
- Global clan registry scans отсутствуют в новом path.
- Schema/migration/config changes отсутствуют.
- Existing allocator/retirement/war contracts и REAL request behavior сохранены.
## Scope and encoding audit

Финальный allowlist: только 7 файлов из раздела Changed files. User-owned untracked task packages остаются untracked/read-only/not staged. Other chronicles: zero. Schema/migrations: zero. Phantom code: zero.

- `git diff --check`: PASS;
- strict UTF-8 decode всех изменённых файлов: PASS;
- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Deviations and limitations

- `apply_patch` был выбран первым, но Windows sandbox ACL вернул `apply deny-read ACLs`. После этого использован разрешённый локальными инструкциями bounded exact-anchor/incremental UTF-8 fallback; каждый existing-file anchor проверялся на уникальность, новый suite/report записаны небольшими chunks.
- CP2 намеренно остаётся blocked до independent review/accept 027E. Phantom caller integration отсутствует по scope.
- Report является частью того же ordinary atomic commit, поэтому exact commit SHA и фактический push result сообщаются в final handoff без amend.

## Risks

Остаточный риск ограничен independent review нового public seam и его будущей CP2 интеграцией. Proof является immutable value object, но correctness mutation path не доверяет caller: exact identity/set/epochs повторно проверяются canonical live state и durable CAS.

## Git delivery

- branch: `feature/phantom-world`;
- required parent: `fcc5213e6550fcc27447e77f5824628b8043961e`;
- subject: `feat(clan): add exact alliance membership proof`;
- один ordinary atomic commit + push;
- amend/rebase/reset/squash/merge/force push не использовались;
- exact commit SHA: same atomic commit containing this report; returned in final handoff;
- push result: returned in final handoff.

## Next step

Independent review Goal 027E. До accept статус CP2 остаётся `BLOCKED_PENDING_027E_INDEPENDENT_REVIEW`; CP2 в этой задаче не возобновлять.