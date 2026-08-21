# Goal 027C — canonical clan social domain seam

## Status

`SUCCESS`

Review state: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Goal 027 и Phantom CP2 не переводились в accepted/implemented state.

`occurred_context_compaction: yes`

## Summary

В native High Five clan domain создан единый transport-neutral owner для alliance и clan-war mutations: `ClanAllianceService`, `ClanWarService`, узкий `ClanSocialRepository` и общий bounded `ClanSocialMutationFence`. REAL packet/NPC paths делегируют этим сервисам; Phantom CP2 behavior не реализован.

Durable write выполняется до canonical memory transition и notifications. SQL failure возвращает typed `PERSISTENCE_FAILURE` и не создаёт fake in-memory success. Post-commit notification exception изолируется как WARNING и не превращает committed mutation в false failure.

## Baseline

- branch: `feature/phantom-world`
- upstream: `origin/feature/phantom-world`
- required parent / implementation baseline: `6a59379ef014c06842f454af2c279e6ec0703582`
- tracked baseline: clean
- user-owned untracked task packages обнаружены до изменений и не менялись/не staging-овались
- High Five module only; другие chronicles не сканировались и не менялись

## Read-first и exhaustive call-site audit

Прочитаны обязательные `Agents.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, полный Goal 027C package, предыдущие отчёты CP1/027A/027B/CP2, native `Clan`/`ClanTable`, alliance/war packets, `VillageMaster`, `CrestTable`, installer schema, build/launcher/test DB infrastructure и локальные тестовые аналоги.

До 027C ownership был разделён:

- alliance rules/mutation/persistence находились в `Clan`, packets, `VillageMaster` и `CrestTable`;
- `Clan.updateClanInDB()` мог перезаписать social fields и скрывал SQL failure;
- clan wars записывались public `ClanTable.storeClanWars/deleteClanWars`, меняли legacy memory/events до SQL и скрывали SQL failure;
- start/stop/surrender/reply paths повторяли либо обходили rules;
- `ClanTable.checkSurrender` был public direct writer, но exact-parent `git grep` доказал отсутствие callers;
- `wantspeace1/wantspeace2` существовали в DDL и всегда создавались как `0`; exact-parent audit не нашёл runtime readers/writers этих двух columns. Они сохранены и migration rehearsal доказал сохранение значений.

После 027C ownership:

- `ClanAllianceService` владеет create/invite permit/join/leave/expel/dissolve/crest/orphan-repair rules и transitions;
- `ClanWarService` владеет declare/direct stop/surrender/accepted replies/destroy cleanup/restore и exact current-war registry;
- `ClanSocialRepository` — единственный runtime durable writer затронутых alliance/war rows;
- package-private setters в `Clan` — только canonical compatibility sinks для legacy views;
- REAL packets/NPC остаются transport/message adapters;
- startup `DatabaseIdManager` orphan-row DELETE остаётся pre-restore referential sanitation для отсутствующих clans, не является runtime social operation и не обходит active registry. Расширять bootstrap-wide cleanup в transaction framework запрещено scope-ом.

Repo-wide High Five audit выполнялся по `java`, `dist`, scripts/datapack, tests, docs/text с исключением `.git`, local logs и jars. Runtime matches старых `createAlly`, `dissolveAlly`, `changeAllyCrest`, `storeClanWars`, `deleteClanWars` отсутствуют. Legacy war-view add/remove calls находятся только в `ClanWarService`; alliance setters — только в `ClanAllianceService`.

## Alliance identity и ABA epoch

Exact semantics:

- `ally_generation` — identity только текущего alliance incarnation; detached row имеет `ally_id=0`, `ally_generation=0`;
- `ally_generation_counter` — durable per-clan monotonic high-water / ABA epoch;
- create использует `counter + 1` как new alliance generation и epoch;
- join копирует current leader alliance generation, но независимо увеличивает target epoch;
- leave/expel/dissolve/orphan repair обнуляют current generation и увеличивают epoch каждой строки, возвращаемой в detached state;
- crest-only mutation epoch не меняет.

REAL invitation хранит typed `MembershipEpoch(clanId, allyId, generation, counter)` вместе с `AllianceIdentity`; answer/replay должен предъявить оба. Repository CAS проверяет old epoch, а committed transition записывает следующий. Focused tests закрывают join→leave→stale old join, create→dissolve→restart→create, direct stale detached create CAS, replacement target epoch и epoch advance каждой строки multi-member dissolve.

## War identity и REAL parity

`war_id BIGINT UNSIGNED AUTO_INCREMENT` — persistent non-reusing incarnation identity; directed pair остаётся UNIQUE. Canonical registry содержит `warId/sourceClanId/targetClanId` и exact delete использует `WHERE war_id=? AND clan1=? AND clan2=?` с affected-row check.

REAL parity сохранена раздельно:

- direct declare сохраняет authority, source/target level/member, exact target, self, allied, dissolving и already-active rules;
- direct stop сохраняет WAR_DECLARATION authority и clan-member attack-stance gate;
- surrender не наследует stop-only authority/attack-stance gates;
- accepted start reply использует отдельный canonical `declareAcceptedReply` и не наследует direct declaration gates, которых не было в legacy reply path;
- accepted stop/surrender replies используют exact `endAcceptedReply` и не наследуют direct stop gates;
- active requester/request response cleanup, refusal/surrender messages и все шесть client opcode registrations сохранены;
- directed mutual A→B/B→A coexist; exact stop одной стороны не удаляет reverse side;
- zero-caller unsafe `checkSurrender` bypass удалён; отдельный character `Player.wantsPeace` и mutual-war PvP condition не менялись.
## Persistence и post-commit consistency

Repository использует narrow JDBC transactions, `commit()` только после exact affected-row checks и rollback при SQL/stale/runtime failure. Memory/registry изменяются только после durable success.

Canonical memory transitions отделены от notifications:

- alliance `StateAccess.apply` — deterministic primitive assignment в уже существующий `Clan` compatibility view;
- war registry transition и package-private legacy set transition не выполняют messages/events;
- broadcasts/events/crest cleanup находятся в `notifySafely` после memory apply;
- controlled notification exceptions для alliance join, war start и war end дали WARNING, но operation вернула typed SUCCESS, durable row, registry и legacy view остались согласованы.

Новый generic transaction/event framework не добавлялся.

## Schema и manual upgrade semantics

Fresh install:

- `clan_data`: добавлены `ally_generation` и `ally_generation_counter`, оба `BIGINT UNSIGNED NOT NULL DEFAULT 0`;
- `clan_wars`: добавлен `war_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`; directed pair `(clan1,clan2)` сохранён как UNIQUE; `wantspeace1/2` сохранены.

Upgrade artifact: `docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql`.

Artifact явно manual, versioned, data-safe и one-shot/non-idempotent:

1. серверы остановить и сделать verified backup;
2. применить файл ровно один раз только к pre-027C schema;
3. active pre-027C alliance rows получают generation `1`; active leaders получают own high-water `1`, members — `0`; inactive clans остаются `0/0`;
4. существующим directed wars MariaDB назначает unique nonzero `war_id`; pair uniqueness сохраняется;
5. повторное применение запрещено presence guard convention в комментарии artifact, автоматический migration runner не изобретался.

MariaDB `RENAME`/`ALTER` DDL имеет implicit commit и не назывался transactional rollback. Production DB `l2jmobiush5` не открывалась и не менялась.

### Guarded manual upgrade rehearsal

Rehearsal выполнялся только на allowlisted `l2jmobiush5_phantom_test` через существующие `PhantomTestDatabaseGuard`, schema manifest, `DatabaseFactory` и `StrictSqlScriptRunner`.

Fixture использует полные exact pre-027C DDL обеих таблиц, а не simplified mock. Exact-parent schema diff доказывает, что 027C fresh DDL отличается только двумя alliance columns и `war_id` + PK→UNIQUE conversion; test deterministically обращает именно этот diff.

Порядок rehearsal:

- fresh test tables атомарно переименованы в bounded backup names;
- созданы exact old `clan_data`/`clan_wars`;
- добавлены 4 clan rows (две alliances, member, detached clan) и 3 directed war rows с ненулевыми peace flags;
- применён exact text one-shot artifact;
- доказаны row counts 4/3 без loss, exact generations/counters, preserved peace flags, nonzero unique war IDs, `clan_data` PK, `clan_wars` war_id PK и directed-pair UNIQUE;
- canonical alliance/war restore восстановил exact identities;
- real MariaDB repository/service path выполнил W1 create → exact delete → new service restore boundary → W2 same pair; W2 ID отличается от W1 и migrated IDs; stale W1 stop/peace вернул STALE и не изменил W2;
- `finally` удалил только fixture tables и вернул исходные fresh test tables; исходные row counts совпали.

## Concurrency и performance

`ClanSocialMutationFence` содержит фиксированные 256 fair-free `ReentrantLock` stripes. Clan/name keys сортируются/дедуплицируются, lock order deterministic. Нет global server mutex, unbounded registry/queue, thread-per-clan или DB access в AI hot path. Services native и не импортируют Phantom packages.

## Focused и regression results

Final verification order соблюдён:

1. `phantom-clan-social-domain-goal027c-test`: PASS 6/6.
   - alliance incarnation/restart/persistence failure;
   - join/leave/expel/multi-member dissolve ABA fences и notification failure;
   - hard W1/W2 restart/persistence/stale fences;
   - REAL direct/accepted reply/directed mutual/surrender parity;
   - adapter/schema/source ownership contract;
   - exact old-schema migration + MariaDB war non-reuse rehearsal.
2. `phantom-clan-expired-replay-goal027b-test`: PASS 4/4.
3. `phantom-clan-consent-chat-goal027a-test`: PASS 2/2.
4. `phantom-clan-checkpoint1-goal027-test`: PASS 26/26 total:
   - CP1 clan suites 6/6;
   - profile persistence 18/18;
   - chat observation 2/2.
5. Ровно один final invocation `ant jar`: PASS; собраны и скопированы штатные `LoginServer.jar`, `GameServer.jar`, также build создал `DatabaseInstaller.jar` по существующей target semantics.

До final sequence также выполнялись targeted compile/focused iterations; последняя compilation: 2203 production sources и 101 test sources, PASS.

Guarded provisioning:

- database: `l2jmobiush5_phantom_test`;
- user: `l2j_phantom_test`;
- scripts: login 4, game 114, migrations 2, total 120;
- statements: 212;
- aggregate schema SHA-256: `4BDCD900CF85FE074D85A13360820FE6ACCD1551998A3E78AF7AADDDE9F77F00`;
- credentials recorded: no.
## Exact changed files

Production/domain/schema:

- `dist/db_installer/sql/game/clan_data.sql`
- `dist/db_installer/sql/game/clan_wars.sql`
- `docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql`
- `java/org/l2jmobius/gameserver/model/clan/ClanSocialMutationFence.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java`
- `java/org/l2jmobius/gameserver/model/clan/ClanWarService.java`
- `java/org/l2jmobius/gameserver/model/clan/Clan.java`
- `java/org/l2jmobius/gameserver/data/sql/ClanTable.java`
- `java/org/l2jmobius/gameserver/data/sql/CrestTable.java`
- `java/org/l2jmobius/gameserver/model/actor/instance/VillageMaster.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/AllyDismiss.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/AllyLeave.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinAlly.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestDismissAlly.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinAlly.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestSetAllyCrest.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestStartPledgeWar.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestReplyStartPledgeWar.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestStopPledgeWar.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestReplyStopPledgeWar.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestSurrenderPledgeWar.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestReplySurrenderPledgeWar.java`

Tests/build/docs:

- `build.xml`
- `test/java/org/l2jmobius/gameserver/model/clan/ClanSocialDomainGoal027CSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/027c-canonical-clan-social-domain-seam.md`

Bounded exception к обычному 8–10 file guideline обоснован самим TASK: единый owner потребовал заменить все 12 REAL adapters, два legacy owners, schema/migration и focused route атомарно. Независимые подсистемы не затронуты.

## Commands и результаты

Build/test commands (portable project Ant):

- `.phantom-local\apache-ant-1.10.17\bin\ant.bat compile-tests` — PASS, 2203 + 101 sources.
- environment-only admin credentials + `.phantom-local\apache-ant-1.10.17\bin\ant.bat prepare-phantom-test-db` — PASS, только allowlisted test DB; manifest указан выше.
- `.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-social-domain-goal027c-test` — final PASS 6/6.
- `.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-expired-replay-goal027b-test` — final PASS 4/4.
- `.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-consent-chat-goal027a-test` — final PASS 2/2.
- `.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-checkpoint1-goal027-test` — final PASS 26/26.
- `.phantom-local\apache-ant-1.10.17\bin\ant.bat jar` — единственный final jar invocation, PASS.

Read-only/search audit commands использовали `rg -n -uuu` только внутри High Five tree для old APIs, direct setters/SQL, war compatibility views, `wantspeace*`, `checkSurrender` и canonical delegation. Result: runtime domain bypass отсутствует; единственный direct `clan_wars` DELETE вне canonical repository — startup orphan sanitation до restore.

TASK-разрешённые bounded git inspection commands:

- `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, upstream inspection — baseline/scope guard;
- `git show 6a59379ef014c06842f454af2c279e6ec0703582:<exact High Five path>` — exact-parent comparison и восстановление случайно затронутого `Clan.checkClanJoinCondition` method без broad restore;
- `git diff 6a59379ef014c06842f454af2c279e6ec0703582 -- <six REAL war adapters>` — parity review;
- `git diff 6a59379ef014c06842f454af2c279e6ec0703582 -- <clan_data.sql> <clan_wars.sql>` — exact old-DDL proof;
- `git grep -n checkSurrender 6a59379ef014c06842f454af2c279e6ec0703582 -- L2J_Mobius_CT_2.6_HighFive` — zero-caller proof;
- `git grep -n -e wantspeace1 -e wantspeace2 6a59379ef014c06842f454af2c279e6ec0703582 -- L2J_Mobius_CT_2.6_HighFive` — durable-column usage proof;
- `git diff --stat`, `git diff --name-only`, final status/diff/check/staged review — artifact and scope verification.

Запрещённые amend/rebase/reset/squash/merge/force-push не выполнялись.

## Encoding, diff и scope checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет;
- UTF-8 decoding изменённых text files: PASS;
- `git -c core.whitespace=cr-at-eol diff --check`: PASS;
- staged allowlist/scope review: High Five 027C files only; user untracked task packages исключены;
- другие chronicles, binary/IDE artifacts и production DB не затронуты.

## Deviations, limitations и risks

- Artifact one-shot, не idempotent; повторное применение намеренно запрещено. Это соответствует отсутствию repo migration runner/convention.
- MariaDB DDL не даёт transactional rollback; безопасность обеспечивается documented backup/stop convention, а rehearsal — test-only backup rename + deterministic finally restore.
- Startup orphan sanitation остаётся вне runtime canonical service, потому что выполняется до `ClanTable`/registry restore и удаляет только rows с отсутствующим clan FK-equivalent. Это явно классифицированный bounded non-domain path.
- Notification failure создаёт WARNING stack trace по controlled test design; durable success не меняется.
- Performance gate структурный: fixed 256 stripes и bounded registries; отдельный load benchmark TASK не требовал.
- Phantom CP2 alliance/war actions, diplomacy/reputation/alliance chat, sieges/clan halls и future chronicles не реализованы.

## Git delivery

- branch: `feature/phantom-world`
- commit: один ordinary atomic commit; exact SHA возвращён в final response (commit не может self-reference собственный SHA)
- push: exact result возвращён в final response
- force push: не использовался

## Next step

Независимый review/acceptance Goal 027C. До acceptance Goal 027 overall остаётся `IN_PROGRESS`, CP2 — `BLOCKED_PENDING_027C_INDEPENDENT_REVIEW`; новый goal/slice не начинать.