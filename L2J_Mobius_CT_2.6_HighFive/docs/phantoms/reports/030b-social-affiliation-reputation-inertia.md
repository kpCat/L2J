# Goal 030B — social affiliation context and reputation inertia

## Статус

- Delivery status: `SUCCESS`.
- Goal 029 overall: `ACCEPT`.
- Goal 030 Checkpoint 1: `ACCEPT`.
- Goal 030A: `ACCEPT`.
- Goal 030B: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 030 overall: `IN_PROGRESS`.
- Goal 030 Checkpoint 2: `NOT_STARTED_AFTER_HUMANIZATION`.
- Required parent: exact `8f75ccbba5c82fdf0e6b0db55a2412aca47f2fe0`.
- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- `occurred_context_compaction`: `no`.
- Goal usage at pre-report snapshot: `267260` tokens, `1345` seconds.

## Summary

`SocialEvent` обратно совместимо расширен immutable transient-контекстом `SocialEventContext` с `AffiliationKind.NONE/SAME_CLAN/SAME_ALLIANCE/CLAN_WAR`. Старый семипараметрический конструктор сохранён и даёт `NONE`. Контекст не входит в `SocialState`, codec или persistence и не содержит `Player`/Clan references.

Authoritative social catalog теперь требует для каждого из 23 событий явные `socialClass` и `reputationShockBp`. `PhantomSocialService` масштабирует только dimension deltas и memory salience; agreement counters остаются exact. После affiliation scaling и существующего age decay opposite-sign deltas четырёх REPUTATION dimensions получают deterministic integer reversal inertia. Same-sign reputation и все relationship dimensions не подавляются.

Текущая репутация документирована как личная воспринимаемая репутация субъекта конкретным фантомом. Public/global fame или notoriety не добавлялась. Canonical clan/alliance/war resolution, expulsion/leadership/directive events и PK/karma recovery принадлежат Goal030C.

## Exact affiliation multipliers, basis points

| Affiliation | SUPPORTIVE | ROUTINE_NEGATIVE | BETRAYAL | HOSTILE_COMBAT | NEUTRAL |
|---|---:|---:|---:|---:|---:|
| NONE | 10000 | 10000 | 10000 | 10000 | 10000 |
| SAME_CLAN | 12000 | 7000 | 13000 | 8500 | 10000 |
| SAME_ALLIANCE | 11000 | 8500 | 11500 | 9250 | 10000 |
| CLAN_WAR | 10000 | 10000 | 10000 | 7000 | 10000 |

## Event class and shock authority

Catalog SHA-256 после содержательной правки: `B8813B037C658EFB5EC0BC65717A901D720B3FA11C43BDC57CC966695449C9D3`. Отдельных старых direct hash pins не найдено; новый suite фиксирует текущий hash в measurement report.

| Code | Event | Social class | reputationShockBp |
|---:|---|---|---:|
| 1001 | party.invite.accepted.outbound | SUPPORTIVE | 500 |
| 1002 | party.invite.accepted.inbound | SUPPORTIVE | 500 |
| 1003 | party.invite.refused.outbound | ROUTINE_NEGATIVE | 500 |
| 1004 | party.invite.refused.inbound | ROUTINE_NEGATIVE | 500 |
| 1005 | party.invite.expired.outbound | ROUTINE_NEGATIVE | 1000 |
| 1006 | party.invite.expired.inbound | ROUTINE_NEGATIVE | 1000 |
| 1007 | party.member.joined | SUPPORTIVE | 500 |
| 1008 | party.member.left | ROUTINE_NEGATIVE | 1000 |
| 1009 | party.member.expelled | BETRAYAL | 8500 |
| 1010 | party.leader.transferred | NEUTRAL | 0 |
| 1011 | party.support.received | SUPPORTIVE | 1000 |
| 1012 | agreement.fulfilled | SUPPORTIVE | 1000 |
| 1013 | agreement.broken | BETRAYAL | 9000 |
| 1014 | debt.incurred | NEUTRAL | 0 |
| 1015 | debt.repaid | SUPPORTIVE | 1000 |
| 1016 | farming.agreement.offered | NEUTRAL | 0 |
| 1017 | farming.agreement.accepted | SUPPORTIVE | 500 |
| 1018 | farming.agreement.refused | ROUTINE_NEGATIVE | 750 |
| 1019 | farming.conflict.escalated | HOSTILE_COMBAT | 3000 |
| 1020 | pvp.attack.received | HOSTILE_COMBAT | 3500 |
| 1021 | pvp.kill.caused | HOSTILE_COMBAT | 3500 |
| 1022 | pvp.death.suffered | HOSTILE_COMBAT | 4500 |
| 1023 | pvp.help.received | SUPPORTIVE | 1000 |
## Comparative numeric evidence

Seed `30003020`, event minute `1000`, in-memory `PersistencePort`:

| Проверка | NONE | SAME_ALLIANCE | SAME_CLAN / CLAN_WAR |
|---|---:|---:|---:|
| Supportive helpfulness, `party.support.received` | 200 | 220 | SAME_CLAN 240 |
| Supportive memory salience | 800 | 880 | SAME_CLAN 960 |
| Routine-negative reliability damage, `party.member.left` | 50 | 42 | SAME_CLAN 35 |
| Betrayal trust damage, `party.member.expelled` | 300 | 345 | SAME_CLAN 390 |
| Betrayal memory salience | 1000 | 1150 | SAME_CLAN 1300 |
| Hostile-combat anger, `pvp.death.suffered` | 420 | — | CLAN_WAR 294 |
| Hostile-combat hostility reputation | 220 | — | CLAN_WAR 154 |
| CLAN_WAR retained fear/rivalry | — | — | 126 / 210 |

Таким образом, supportive exact ordering: clan > alliance > neutral; routine-negative damage: clan < alliance < neutral; betrayal magnitude: clan > alliance > neutral. Clan-war combat снижает личные anger/hostility, но fear/rivalry остаются ненулевыми.

Agreement counters не масштабируются: `party.invite.accepted.outbound` дал exact `offered=1, accepted=1` при `NONE` и `SAME_CLAN`.

## Reputation reversal inertia

Формула реализована только для opposite-sign `DimensionGroup.REPUTATION` с long intermediates:

- `baseResistance = min(7000, abs(current) * 7000 / 10000)`;
- `effectiveResistance = baseResistance * (10000 - reputationShockBp) / 10000`;
- `effectiveDelta = delta * (10000 - effectiveResistance) / 10000`.

Same-sign reliability trace: `0 -> 3000 -> 6000 -> 9000`; подавления нет. Четвёртое такое событие даёт штатный clamp `10000`.

При established reliability `9000`:

- weak routine event `party.invite.expired.outbound`, raw delta `-400`, shock `1000`: результат `8827`, то есть один слабый факт знак не меняет;
- high-shock betrayal `agreement.broken`, raw delta `-5000`, shock `9000`: результат `4315`, движение существенно больше;
- near-neutral trace: `0 -> 60 -> 21`, weak opposite delta почти полный (`-39` вместо `-40`).

Repeated weak-opposite trace пересёк ноль на event `34`:

`9000, 8827, 8650, 8468, 8282, 8091, 7895, 7694, 7488, 7277, 7061, 6839, 6612, 6379, 6140, 5895, 5644, 5387, 5123, 4853, 4576, 4292, 4001, 3702, 3396, 3082, 2760, 2430, 2092, 1745, 1389, 1024, 650, 267, -126`.

Trace строго монотонный и удовлетворяет bound `<=64`.

## Non-reputation compatibility and idempotency

Relationship exact results после opposite-sign либо обычных событий:

| Dimension | Result |
|---|---:|
| trust | -2500 |
| friendship | 200 |
| fear | 1000 |
| debt | 0 |
| anger | 300 |
| rivalry | 800 |

Это полные catalog deltas без reputation inertia. Старый constructor и explicit `SocialEventContext(NONE)` дали одинаковые relationship/reputation/agreement maps и memory salience. Повтор старого event вернул `IDEMPOTENT`, write count не вырос, receipt cardinality осталась exact `1`.

## New DB-free suite

Добавлен `PhantomSocialHumanizationGoal030BSuite`, forked Ant target `phantom-social-humanization-goal030b-test`, seed `30003020`, timeout exact `120000`. Suite использует только `PhantomSocialTestDoubles.MemoryStore` как `PhantomSocialService.PersistencePort`.

Cases:

1. `01-context-policy-strict-catalog`;
2. `02-clan-alliance-positive-and-minor-negative-scaling`;
3. `03-clan-betrayal-and-war-combat-semantics`;
4. `04-established-reputation-reversal-inertia`;
5. `05-repeated-evidence-can-reverse-reputation`;
6. `06-non-reputation-dimensions-unchanged`;
7. `07-context-default-backward-compatibility`.

## Exact final gates

После последнего source/test/build изменения выполнена точная последовательность:

| # | Command | Result | Cases | Ant total | Wall |
|---:|---|---|---:|---:|---:|
| 1 | `ant phantom-social-humanization-goal030b-test` | PASS | 7/7 | 18 s | 18.473 s |
| 2 | `ant phantom-social-events-test` | PASS | 4/4 | 18 s | 18.525 s |
| 3 | `ant phantom-social-modifiers-test` | PASS | 3/3 | 18 s | 18.616 s |
| 4 | `ant phantom-conversation-social-style-test` | PASS | 1/1 | 18 s | 18.622 s |
| 5 | ровно один финальный `ant jar` | BUILD SUCCESSFUL | — | 16 s | 16.939 s |

`jar` собрал `LoginServer.jar`, `GameServer.jar`, `DatabaseInstaller.jar`; LoginServer/GameServer jars скопированы в рабочий `dist/libs`. На compile-tests оставались только две historical JDK removal warnings для `System.runFinalization()` в Goal029 CP2/CP3 suites; новых warnings нет.

Не запускались: provisioning, DatabaseFactory/ProfileRepository DB tests, social aggregate, DB activation/integration, materialization/headless, Clan/PvP/Party integration, Goal029 soak, CP1, CP2, `verify`, geodata.

## Production and persistence boundary

- `SocialState`, `PhantomSocialStateCodec`, schema и migrations не изменены.
- ProfileRepository/DatabaseFactory не изменялись и не инициализировались.
- ClanTable/ClanWar/Player/PvP/Party/Conversation owners не изменены.
- Scheduler/Decision, configs и release coverage matrix не изменены.
- Нет global notoriety, worker/thread/timer, I/O или нового hot-path logging.
- Persistence migration и production clan integration не потребовались; blocker codes не возникли.
## Exact changed files

Bounded exception к обычному ориентиру 8–10 файлов: task требует одну coherent social artifact family плюс focused wiring, operator/release docs и отчёт.

1. `build.xml` — seed/DB-free target и social-events timeout 120000.
2. `java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialModel.java` — transient affiliation context и backward-compatible event constructor.
3. `java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialCatalog.java` — event class/shock model, strict parser и exact multipliers.
4. `java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java` — dimension/salience scaling и reputation reversal inertia.
5. `dist/game/data/phantoms/social/high-five-social-v1.xml` — 23 explicit class/shock declarations.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomSocialHumanizationGoal030BSuite.java` — новый 7-case in-memory suite.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — launcher mode.
8. `dist/game/data/phantoms/README.ru.md` — русский social humanization guide и fast gates.
9. `docs/PHANTOM_BOTS_ROADMAP.md` — CP1/030A accepted, 030B pending review, CP2 after humanization.
10. `docs/phantoms/PHANTOM_RELEASE_GATE.md` — sequence CP1 -> 030A -> 030B -> 030C -> CP2 -> CP3 и 030C ownership.
11. `docs/phantoms/reports/030b-social-affiliation-reputation-inertia.md` — этот отчёт.

Другие хроники и user-owned untracked task packages не изменялись и не staging.

## Writes, static checks and process deviation

Все успешные writes выполнены bounded exact-anchor заменами либо bounded new-file chunks через UTF-8 без BOM temp + atomic `Move-Item`.

`apply_patch` не внёс ни одного изменения. Однако exact invocation count `0` не достигнут: был `1` неуспешный вызов, завершившийся до write из-за Windows `apply deny-read ACLs`. После подтверждённого ACL failure использован предусмотренный AGENTS Windows fallback. Applied-patch change count — exact `0`; invocation attempts — exact `1`. Это честно зафиксированное process deviation, не product/code deviation.

Final static results:

- strict UTF-8 decode и no BOM по exact 11-file allowlist: PASS;
- mojibake-маркеры в изменённых файлах проверены: `rg` вернул expected no-match exit `1`, совпадений `0`;
- escaped Cyrillic в изменённых файлах проверены: `rg` вернул expected no-match exit `1`, совпадений `0`;
- Goal030B temp files: `0`;
- social events/classes/shocks: exact `23/23/23`;
- release coverage matrix diff: exact `0`;
- forbidden SocialState codec/schema/DB/Clan/PvP/Party/Conversation-owner diff: exact `0`;
- `git diff --check`: PASS; staged allowlist/check повторены перед commit.

## Git and delivery

Task/Agents.md разрешают обязательные Git baseline/status/branch/upstream, bounded exact diff/scope inspection, ordinary stage/commit/push. Baseline проверен: exact parent и upstream совпали, user packages остались untracked/read-only.

Использованы read-only `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`, bounded `git diff`/name/stat/check inspections. Далее используются только exact-path `git add`, ordinary `git commit -m "feat(phantoms): add social reputation inertia"`, `git rev-parse HEAD` и `git push origin feature/phantom-world`.

Commit SHA и push result указываются в финальном сообщении: report-bearing commit не может содержать собственный SHA без запрещённого amend/history rewrite.

## Limitations, risks and next step

- Affiliation остаётся event-time truth; canonical wiring отсутствует намеренно.
- Catalog hash изменён содержательно, поэтому durable state с другим authority hash продолжает fail closed по существующему контракту; persistence format не менялся.
- Goal030B не доказывает cross-domain CP2/CP3 release behavior.
- Следующий шаг — независимый review Goal030B. После ACCEPT Goal030C получает canonical clan/alliance/war wiring, expulsion/leadership/directive events и PK/karma recovery; только затем возможен новый CP2 package.
