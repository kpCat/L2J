# Goal 027D — recyclable clan social identity + retirement fence

## Status

`SUCCESS`

Review state: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Следующий Goal/CP2 не начинался.

`occurred_context_compaction: yes`

## Summary

Исправлены три native-core gap независимого review 027C: alliance incarnation теперь выдаётся durable глобальным non-reusing high-water, destruction публикует retirement под тем же canonical social fence до cleanup и удерживает его до удаления/release id, а direct war declaration проверяет exact target id повторного name lookup внутри выбранного lock set.

`ally_generation_counter` сохранён как независимый per-clan membership ABA epoch. Phantom behavior, diplomacy/reputation/chat/sieges, generic framework и другие chronicles не затронуты.

## Baseline и read-first

- branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`;
- required parent и исходный HEAD: `24755325faee9ab9e4432546420b05c5318b29d2`;
- прочитаны `Agents.md`, master plan, workflow/task standards, полный package 027D, отчёт и targeted diff 027C;
- прочитаны `ClanTable`, `IdManager`/`DatabaseIdManager`, clan create/destroy, social services/repository/fence, schema/migration, installer/test DB infrastructure и focused аналоги 027A–027C;
- higher `AGENTS.md`, отдельные применимые `README.md`/`docs` вне обязательного набора и project files для dependency change не найдены/не требовались;
- user-owned untracked task packages обнаружены до изменений и не менялись/не staging-овались.

Локальные паттерны: 027C typed Result/Reason, exact affected-row CAS, JDBC transaction + row lock, fixed striped fence, guarded MariaDB rehearsal и deterministic latch tests. Ограничения: High Five only, JDK 25/Ant, no new library, no network-client seam, no production DB.

Bounded exception для 12 файлов обоснован единым corrective: native fence/repository/services/destroy path, fresh/manual schema, focused route/tests и обязательный отчёт нельзя безопасно разделить на независимые artifact families.

## Allocator semantics

- новая `clan_social_identity` содержит одну строку `alliance_incarnation -> high_water`; её lifetime не зависит от `clan_data` и recyclable `IdManager`;
- create transaction сначала `SELECT ... FOR UPDATE` revalidate exact detached leader row, затем блокирует allocator row, увеличивает high-water, увеличивает `ally_generation_counter` этого clan отдельно и CAS-записывает оба значения;
- commit предшествует memory transition/notifications; rollback allocator/create атомарен, поэтому unpublished failed token не считается incarnation;
- каждый committed alliance получает глобально отличный положительный `ally_generation`; identity остаётся `(leaderClanId, ally_generation)`;
- `ally_generation_counter` меняется только как per-clan membership ABA epoch и у fresh/reused clan снова начинается с нуля независимо от глобального high-water;
- missing row/exhaustion/SQL failure возвращает `PERSISTENCE_FAILURE`, не меняя durable clan row или memory.
## Retirement protocol и reachability

Audit подтвердил destroy-while-allied reachability: initial dissolve request запрещает уже allied clan, но в отложенном окне до фактического destroy target clan мог войти в alliance; old destroy path не выполнял alliance cleanup.

Protocol:

1. `beginRetirement(clanId)` берёт canonical clan stripe и публикует identity-token в bounded map только текущих retirements.
2. Любая normal alliance/war mutation под тем же lock set отклоняет участвующий retiring clan как `CLAN_RETIRING` до durable write.
3. Canonical cleanup допускается только с exact current token: allied member безопасно detach-ится с ABA advance; retiring alliance leader canonical dissolve-ит весь exact member set и advances epochs всех строк.
4. War cleanup берёт clan stripe даже при zero wars, поэтому между empty observation и destroy не возникает unfenced declare.
5. После social cleanup выполняется durable clan delete; затем exact live registry remove, `completeRetirement`, и только после этого `IdManager.releaseId`.
6. Failure до завершения вызывает identity-checked abort и не release-ит id. Старый token не может снять новый retirement reused id; успешный abort не отравляет still-live clan.

Таким образом dangling alliance member/leader state после reachable allied destroy не остаётся. Операция, начатая до publication, завершается перед `beginRetirement`; операция после publication видит token и не пишет.

## Direct declare race proof

Direct declaration захватывает candidate target id до формирования source+target lock set. Повторный lookup имени внутри lock обязан вернуть тот же id; rebind возвращает typed `STALE`, не создавая durable war, exact registry entry или legacy war view ни для старого, ни для нового target.

## Fresh schema и manual migration

Fresh installer создаёт `clan_social_identity` и `INSERT IGNORE` initial high-water `0`.

Поскольку 027C не принят и production schema не мигрировалась, исправлен тот же unreleased one-shot `V027C__canonical_clan_social_domain.sql`: после preservation active alliances создаётся allocator table, а high-water инициализируется `MAX(ally_generation)` (`1` для migrated active identities). Artifact требует остановки серверов, verified backup и применения ровно один раз только к pre-027C schema; повторное применение запрещено presence convention.

Rehearsal выполнялся только на `l2jmobiush5_phantom_test`: exact old tables + alliances/wars/peace flags, exact migration, non-colliding allocation, затем `finally` восстановил fresh tables и исходный high-water. Production `l2jmobiush5` не открывалась и не менялась.

Provisioning manifest: login scripts 4, game 115, migrations 2, total 121; statements 214; aggregate SHA-256 `615504C4DD0C46F8D66E5D69967C93D56C82A6895936F910315B2B0336A2B7A9`; credentials не записаны.
## Deterministic tests и gates

Final order соблюдён:

1. `phantom-clan-social-retirement-goal027d-test`: PASS 6/6, seed `27002740`.
   - real DB G1 → dissolve/delete → exact id reuse → restore → G2 > G1; stale G1 harmless;
   - allocator failure без fake success;
   - allied member/leader retirement cleanup и ABA;
   - latch-only zero-war destroy/declare, alliance reject, exact-token abort/reuse;
   - target-name rebind;
   - migration high-water/repository/source contract.
2. `phantom-clan-social-domain-goal027c-test`: PASS 6/6, включая join/leave/expel/multi-member dissolve ABA и W1/W2.
3. `phantom-clan-expired-replay-goal027b-test`: PASS 4/4.
4. `phantom-clan-consent-chat-goal027a-test`: PASS 2/2.
5. `phantom-clan-checkpoint1-goal027-test`: PASS 26/26 (CP1 6 + profile 18 + chat observation 2).
6. Ровно один final `jar`: PASS; штатные LoginServer/GameServer JAR собраны и скопированы, DatabaseInstaller JAR создан существующей target semantics.

До final sequence: `compile` PASS 2203 sources; `compile-tests` PASS 2203 + 102 sources. Race tests используют `CountDownLatch`/bounded join, sleeps отсутствуют.

## Exact changed files

- `build.xml`
- `dist/db_installer/sql/game/clan_social_identity.sql`
- `docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql`
- `java/org/l2jmobius/gameserver/data/sql/ClanTable.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanSocialMutationFence.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanWarService.java`
- `test/java/org/l2jmobius/gameserver/model/clan/ClanSocialDomainGoal027CSuite.java`
- `test/java/org/l2jmobius/gameserver/model/clan/ClanSocialRetirementGoal027DSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `docs/phantoms/reports/027d-recyclable-clan-social-identity-retirement-fence.md`
## Commands, checks и deviations

- Initial `prepare-phantom-test-db` без admin URL и с `jdbc:mariadb:` был отвергнут guard до destructive phase; финальный explicit `jdbc:mysql://127.0.0.1:3308/` provisioning — PASS только на allowlisted DB.
- Один regression launcher без exact Ant path не стартовал (`ant not recognized`) и gate не засчитывался; повтор выполнен через `.phantom-local/tools/apache-ant-1.10.17/bin/ant.bat`.
- `rg` repo-wide High Five writer audit: alliance/war runtime SQL находится в `ClanSocialRepository`; package-private alliance sinks — в `ClanAllianceService`; единственное внешнее `clan_wars DELETE` — existing startup orphan sanitation `DatabaseIdManager` до restore.
- CRLF-safe `git -c core.whitespace=cr-at-eol diff --check`: PASS.
- strict UTF-8 decode всех changed text files: PASS.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
- scope: High Five 027D allowlist only; user task packages, other chronicles, binaries/IDE files и production DB не затронуты.
- Performance proof структурный: fixed 256 stripes, bounded current-retirement map, один short row-lock transaction только при rare alliance create; AI hot path не затронут.
- Запрещённые amend/rebase/reset/squash/merge/force-push не выполнялись.

Git inspection использовала только требуемые baseline/status/rev/upstream, targeted `git show`/`git diff` 027C, final name/stat/check/staged review. Editing/history Git-команды до delivery не использовались.

## Git delivery

- branch: `feature/phantom-world`
- commit: один ordinary atomic commit `fix(clan): fence recyclable social identities`; exact SHA возвращён в final response, поскольку commit не может self-reference
- push: exact result возвращён в final response
- force push: не использовался

## Next step

Независимый review/acceptance corrective Goal 027D; до acceptance не начинать Phantom CP2 или следующий Goal/Slice.