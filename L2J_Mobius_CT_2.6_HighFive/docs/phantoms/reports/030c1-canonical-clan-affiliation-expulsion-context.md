# Goal030C1 — canonical clan affiliation, expulsion context и native leader truth

## Status

`BLOCKED_030C1_FORBIDDEN_APPLY_PATCH_ATTEMPT`

Техническая реализация завершена, production/test/status diff компилируется, новый suite и все пять required regressions прошли, ровно один финальный `jar` собран. Формальный `SUCCESS` невозможен: `apply_patch` был вызван один раз из-за более приоритетного правила среды, sandbox отклонил вызов до применения изменений (`apply deny-read ACLs`), поэтому applied change count равен 0, но invocation count равен 1 вместо требуемого 0. Это отклонение нельзя честно исправить постфактум.

## Summary

- Required parent `1c69647c2edd3f9822b4f1f4dd3deb2882467712`, branch `feature/phantom-world` и upstream `origin/feature/phantom-world` подтверждены до изменений.
- Добавлен единый `PhantomSocialAffiliationContextPort` с neutral noop и O(1) production resolver через exact materialization lookup, exact `World.getPlayer`, native `Player.getClan`, `ClanAllianceService` и `ClanWarService`.
- Один resolver переиспользуется `PhantomPartyCoordinator`, `PhantomPvpSocialBridge` и `L2jPhantomClanBackend`; старые конструкторы сохраняют source-compatible `NONE` behavior.
- `OnPlayerClanLeft` расширен совместимо: `UNKNOWN`, `VOLUNTARY`, `EXPELLED`, `CLAN_DISSOLVED`, initiator object ID и deterministic-testable epoch minute.
- Canonical packet/caller metadata подключена для withdrawal, oust и clan destruction.
- Event-driven observer создаёт для managed expelled Phantom ровно один durable `clan.member.expelled` к точному инициатору с явным `SAME_CLAN`; initiator 0, voluntary и dissolve не создают betrayal.
- Native `Clan.setNewLeader` и `ClanSnapshot.leaderObjectId` остались источником истины; obedience/directive scoring не заявляется.
- Goal030C2 scope не начат: leader directives/obedience scoring и PK/karma recovery отложены.

## Read-first и локальные аналоги

Прочитаны objective, `Agents.md`, master plan, workflow contract, task package standard, Goal030C1 `TASK.md`/`ARCHITECTURE.md`/`ACCEPTANCE.md`/`TEST_CASES.md`, roadmap/release gate, отчёты 030B/030B1/027C, SocialModel/Catalog/XML, Party/PvP/Clan producers, PhantomSystem lifecycle, native Clan departure/leader callers, alliance/war services, Goal027C и headless Party integration suites, build/launcher.

Переиспользованы локальные паттерны:

- exact materialization + World identity lookup без fake `GameClient`;
- native Clan/Alliance/War services как единственный canonical truth;
- backward-compatible constructor delegation к noop port;
- global event listener с явным install/close lifecycle;
- deterministic SHA-256 event/evidence identity;
- guarded headless DB suite с test-owned cleanup;
- exact Ant target/launcher conventions.

Непроверенными до изменений оставались runtime clan creation preconditions и Java boxing типа PvP flag; оба расхождения были выявлены диагностическими запусками и исправлены только в fixture/assertion.

## Resolver precedence и context matrix

| Owner / subject state | Result | Evidence owner |
|---|---|---|
| exact same positive clan ID | `SAME_CLAN` | `Player.getClan()` |
| distinct clans, active directed war в любой стороне | `CLAN_WAR` | `ClanWarService.currentWar` |
| distinct non-war clans, equal current alliance identity | `SAME_ALLIANCE` | `ClanAllianceService.currentIdentity` |
| distinct unallied clans | `NONE` | native state |
| unresolved owner/subject, non-materialized profile, no clan или self | `NONE` | fail-closed |
| subject `PHANTOM_PROFILE` | exact materialization lookup, затем `World.getPlayer` | no DB |
| subject `CHARACTER` | exact `World.getPlayer(objectId)` | no scan |

Precedence подтверждён как `SAME_CLAN > CLAN_WAR > SAME_ALLIANCE > NONE`. Resolver не содержит `PhantomProfileRepository`, `findByCharacterObjectId`, `getClans()`, `getPlayers()`, `ThreadPool` или `ScheduledFuture`; cache/refresh/worker не добавлены.

## Party, Clan и PvP evidence

Party terminal events с одинаковой семантикой записали context в точной последовательности:

`[NONE, SAME_CLAN, SAME_ALLIANCE]`

Behavior Party invitation/terminal не менялся. Clan relation producer записал `agreement.fulfilled` с `SAME_CLAN`; итоговый trust для focused fixture равен `+300`.

Для реально созданных двух native clans и reciprocal active war:

- resolver вернул `CLAN_WAR` для exact character subject;
- `pvp.death_suffered` был durable;
- social deltas после XML/context scaling: trust `-154`, fear `+126`, anger `+294`, rivalry `+210`, hostility `+154`;
- native `isAutoAttackable` не изменился до/после social record;
- native PvP flag сохранился `0 -> 0`;
- native karma сохранилась `0 -> 0`.

Social record не вызывает атаку, не меняет flag/karma и не реализует PK recovery.

## Departure call-site ledger

| Caller | Departure kind | Initiator |
|---|---|---|
| legacy two-argument `Clan.removeClanMember` | `UNKNOWN` | `0` |
| `RequestWithdrawalPledge` | `VOLUNTARY` | self object ID |
| `RequestOustPledgeMember` | `EXPELLED` | requester object ID |
| `ClanTable.destroyClan` member loop | `CLAN_DISSOLVED` | safely captured leader object ID, иначе `0` |
| academy graduation / deleted-character internal cleanup | `UNKNOWN` через compatible overload | `0` |

Иные bounded callers были прочитаны; guessing human intent для internal automatic removals не добавлялся.

## Expulsion, non-expulsion и leader truth

Canonical expulsion fixture:

- target: managed observer Phantom;
- actor: managed owner Phantom, сохранён как exact `SubjectRef.phantom(ownerProfileId)`;
- event: `clan.member.expelled`, magnitude `1000`, explicit `SAME_CLAN`;
- XML: code `1024`, class `BETRAYAL`, TTL `525600`, salience `1500`, shock `9000`;
- authoritative base deltas: trust `-400`, respect `-150`, anger `+350`, rivalry `+120`, reliability `-200`, hostility `+250`;
- applied SAME_CLAN deltas: trust `-520`, respect `-195`, anger `+455`, rivalry `+156`, reliability `-260`, hostility `+325`;
- memory salience: `1950`;
- duplicate observer delivery: durable event count не вырос, idempotent count вырос на 1.

`VOLUNTARY` self-withdrawal и `CLAN_DISSOLVED` для обоих exact members доставили metadata, но не создали betrayal events. Initiator `0` также fail-closed.

Native leader transfer evidence: clan `C30C111`, `PhT004A03031 -> PhT004B03031`. До transfer `ClanSnapshot.leaderObjectId` совпадал с owner, после transfer — с target; `Clan.getLeaderId` совпал, прежний leader остался ordinary member и потерял native leader flag.
## Cleanup, DB, schema и performance

Focused suite использовал только guarded allowlisted `l2jmobiush5_phantom_test`, forked JVM, cwd `dist/game`, seed `30003031`, timeout `180000` ms. Provisioning не запускался.

Suite завершился с cleanup assertions:

- tracked test clans: `0`;
- alliance и обе directed war identity удалены canonical services/clan retirement;
- global observer закрыт и listener снят;
- materialization service остановлен, leases и World players удалены;
- test profiles/components и headless accounts/characters очищены штатным environment lifecycle;
- config owner не оставлен;
- broad `DELETE` не добавлялся.

DB schema, migrations, installer SQL и production database не менялись. Новых thread/timer/queue/cache owners нет. Resolver выполняет bounded O(1) exact lookups; event-driven expulsion observer не участвует в AI hot path и не имеет worker. Нового per-AI-event DB lookup нет: profile resolution observer-а выполняется только на редком canonical clan-left event.

## Exact final gates

После последнего production/test/status-doc изменения выполнена точная финальная последовательность через Apache Ant 1.10.17:

| # | Command | Result | Cases | Wall |
|---:|---|---|---:|---:|
| 1 | `phantom-clan-affiliation-humanization-goal030c1-test` | PASS | 7/7 | 28.876 s |
| 2 | `phantom-clan-social-domain-goal027c-test` | PASS | 6/6 | 18.815 s |
| 3 | `phantom-clan-checkpoint2-goal027-test` | PASS | 8/8 | 16.028 s |
| 4 | `phantom-pvp-warning-social-test` | PASS | 1/1 | 15.715 s |
| 5 | `phantom-social-humanization-goal030b-test` | PASS | 8/8 | 15.454 s |
| 6 | ровно один final `jar` | BUILD SUCCESSFUL | — | 14.363 s |

`jar` вызван ровно один раз. Не запускались Goal027 aggregate, Goal025 aggregate, social aggregate, CP1, CP2, soak, `verify`, geodata, provisioning или schema targets.

Дополнительная диагностика до финальной последовательности:

- production `compile`: первый запуск выявил единственный method-reference type mismatch observer-а; после точечной правки PASS;
- `compile-tests`: первый запуск выявил static method-reference mismatch test clock; после точечной правки PASS;
- новый target: первый запуск выявил недостающий native clan level precondition fixture-а, второй — boxing mismatch при равных PvP flag `0/0`, третий PASS 7/7;
- production behavior failures не обнаружены;
- historical warnings только два JDK removal warning для `System.runFinalization()` в Goal029 CP2/CP3 suites.

## Exact changed files

1. `build.xml` — seed и guarded forked Goal030C1 target.
2. `dist/game/data/phantoms/README.ru.md` — canonical context/operator gate documentation.
3. `dist/game/data/phantoms/social/high-five-social-v1.xml` — authoritative expulsion betrayal event.
4. `docs/PHANTOM_BOTS_ROADMAP.md` — 030B/B1 accepted truth и 030C1 pending-review truth.
5. `docs/phantoms/PHANTOM_RELEASE_GATE.md` — split 030C1/030C2 и release sequence.
6. `java/org/l2jmobius/gameserver/data/sql/ClanTable.java` — dissolution metadata.
7. `java/org/l2jmobius/gameserver/model/clan/Clan.java` — compatible departure overload/event wiring.
8. `java/org/l2jmobius/gameserver/model/events/holders/actor/player/clan/OnPlayerClanLeft.java` — departure metadata contract.
9. `java/org/l2jmobius/gameserver/network/clientpackets/RequestOustPledgeMember.java` — expelled/requester classification.
10. `java/org/l2jmobius/gameserver/network/clientpackets/RequestWithdrawalPledge.java` — voluntary/self classification.
11. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — one shared resolver и observer lifecycle.
12. `java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java` — relation context.
13. `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanSocialLifecycleObserver.java` — exact expulsion memory observer.
14. `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java` — Party context port.
15. `java/org/l2jmobius/gameserver/phantoms/social/L2jPhantomSocialAffiliationContextResolver.java` — canonical O(1) resolver.
16. `java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java` — PvP context port.
17. `java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialAffiliationContextPort.java` — port/noop contract.
18. `test/java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanAffiliationHumanizationGoal030C1Suite.java` — seven focused cases.
19. `test/java/org/l2jmobius/tests/phantoms/PhantomSocialHumanizationGoal030BSuite.java` — authoritative event count 24.
20. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — suite mode.
21. `docs/phantoms/reports/030c1-canonical-clan-affiliation-expulsion-context.md` — этот отчёт.

Другие хроники и соседние модули не затронуты. User-owned untracked task packages не изменялись и не будут staged.

## Static, encoding и scope checks

- `git diff --check`: PASS, ошибок нет; сообщения `LF will be replaced by CRLF` являются локальным `core.autocrlf=true` warning.
- Полный tracked diff и новые файлы просмотрены; production/test/status scope соответствует TASK bounded exception на несколько обязательных artifact families.
- Resolver forbidden patterns: `0`.
- Observer worker/queue patterns: `0`.
- Goal030C1 temp remnants: `0`.
- Mojibake-маркеры в изменённых файлах проверены: совпадений `0`.
- Escaped Cyrillic в изменённых файлах проверены: совпадений `0`.
- UTF-8 без BOM использован для новых/атомарно переписанных файлов; кириллица сохранена напрямую.
- Schema diff: `0`.

## Process deviation, Git и compaction

`apply_patch` invocation count: `1`.

`apply_patch` applied change count: `0`.

Первый обязательный edit attempt через `apply_patch` был отклонён sandbox ACL до применения patch. После подтверждённого отказа использован предусмотренный Windows fallback: уникальные counted anchors, bounded incremental writes, UTF-8 without BOM temp в той же директории и atomic `Move-Item`. Повторных `apply_patch` вызовов не было. Требуемое TASK значение `0` не заявляется; именно поэтому формальный статус отчёта `BLOCKED_030C1_FORBIDDEN_APPLY_PATCH_ATTEMPT`.

Разрешённые TASK Git-команды использовались только для parent/branch/upstream/scope/diff verification и последующих ordinary add/commit/push: `status`, `branch --show-current`, `rev-parse`, `diff --check`, `diff --name-only`, `diff --stat`, targeted/full `diff`, `config --get core.autocrlf`, `check-attr`, затем staged allowlist review, commit и push. History rewrite, restore, reset, rebase, force push и изменение user packages не выполнялись.

Planned ordinary commit subject: `feat(phantoms): wire canonical clan social context`.

Фактические commit SHA и push result приводятся в финальном сообщении после фиксации отчёта.

Codex goal snapshot перед отчётом: `tokensUsed=582220`, `timeUsedSeconds=3182`. Автоматическая compaction наблюдалась как минимум один раз; точный счётчик compaction инструментом не предоставляется. Goal token budget не был задан.

## Limitations, risks и next step

Технических blocker-ов реализации или runtime gates нет. Единственный blocker — необратимое process-gate отклонение invocation count. Status docs отражают фактически реализованный Goal030C1 как `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, но этот execution report не заявляет `SUCCESS`.

Оставшиеся deliberate limitations:

- нематериализованный Phantom subject возвращает `NONE` без DB fallback;
- automatic/internal departure callers остаются `UNKNOWN`;
- observer не создаёт память при неизвестном initiator `0`;
- directive/obedience events и PK/karma recovery принадлежат Goal030C2;
- CP2 остаётся `NOT_STARTED_AFTER_HUMANIZATION`.

Следующий шаг: независимый review Goal030C1 с явным решением по process-gate конфликту; затем отдельный Goal030C2, после него новый CP2 package.