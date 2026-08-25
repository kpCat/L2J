# Goal 030B1 — data-driven affiliation policy and process closeout

## Статус

- Delivery status: SUCCESS.
- Required parent: exact 9ad72ecc8700e726cb0ef1e41194e4d05d51a218.
- Branch/upstream: feature/phantom-world / origin/feature/phantom-world.
- Goal 030 Checkpoint 1: ACCEPT.
- Goal 030A: ACCEPT.
- Goal 030B: CHANGES_REQUIRED_CLOSED_BY_030B1_PENDING_INDEPENDENT_REVIEW.
- Goal 030B1: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- Goal 030 overall: IN_PROGRESS.
- Goal 030 Checkpoint 2: NOT_STARTED_AFTER_HUMANIZATION.
- occurred_context_compaction: no.
- Goal usage snapshot before report: 182610 tokens, 874 seconds.

## Причины corrective

Independent review Goal030B потребовал ровно два исправления: affiliation multiplier policy была hardcoded Java switch в PhantomSocialCatalog, а отчёт Goal030B зафиксировал один запрещённый вызов apply_patch. Остальная social math/inertia/context семантика была принята и в Goal030B1 не изменялась.

Goal030B1 перенёс политику в authoritative dist/game/data/phantoms/social/high-five-social-v1.xml и выполнил чистый процесс с apply_patch invocation count = 0. История Goal030B не переписывалась.

## Authoritative XML table

| Affiliation | SUPPORTIVE | ROUTINE_NEGATIVE | BETRAYAL | HOSTILE_COMBAT | NEUTRAL |
|---|---:|---:|---:|---:|---:|
| NONE | 10000 | 10000 | 10000 | 10000 | 10000 |
| SAME_CLAN | 12000 | 7000 | 13000 | 8500 | 10000 |
| SAME_ALLIANCE | 11000 | 8500 | 11500 | 9250 | 10000 |
| CLAN_WAR | 10000 | 10000 | 10000 | 7000 | 10000 |

XML содержит canonical affiliationMultipliers сразу после limits и до traits: exact 4 affiliation rows и 20 explicit multiplier cells. Итоговый catalog SHA-256: 59F993AABA6E78EEE89CA4DC00393B8DE0FE00DF019F736436C19BE51D41C4D7.

## Parser и production lookup

PhantomSocialCatalog требует exact section order limits -> affiliationMultipliers -> traits -> relationships -> reputation -> events -> modifiers. Строки и cells следуют canonical enum order, все четыре AffiliationKind и пять EventSocialClass обязательны ровно один раз. Неизвестные, отсутствующие, дублированные или лишние элементы/атрибуты отклоняются; basisPoints допускается только в диапазоне 0..20000.

Parsed table хранится как immutable outer/inner maps. affiliationMultiplierBp() выполняет только null checks и lookup в parsed table. Production Java не содержит hardcoded affiliation policy switch, fallback или копию 4x5 значений; единственное числовое значение policy-related в Java — generic upper bound 20000.

## DB-free negative parser evidence

Case 08 focused suite создаёт temporary XML в reports directory, ожидает IllegalArgumentException и удаляет файл в finally. PASS для всех семи controls:

1. missing SAME_CLAN row;
2. duplicate CLAN_WAR row;
3. missing basisPoints multiplier attribute;
4. unknown affiliation key;
5. multiplier 20001 вне range;
6. extra affiliation attribute;
7. affiliationMultipliers после traits, то есть bad section order.

Valid authoritative catalog одновременно доказал exact 4x5 lookup values, 23 event definitions и неизменные socialClass/reputationShockBp validations. Existing required-event/shock validation не ослаблена.

## Unchanged Goal030B numeric semantics

| Evidence | Exact result |
|---|---|
| helpfulness NONE / SAME_ALLIANCE / SAME_CLAN | 200 / 220 / 240 |
| routine-negative reliability damage | 50 / 42 / 35 |
| betrayal trust damage | 300 / 345 / 390 |
| ordinary -> CLAN_WAR anger | 420 -> 294 |
| ordinary -> CLAN_WAR hostility | 220 -> 154 |
| CLAN_WAR fear / rivalry | 126 / 210 |
| weak opposite reliability from 9000 | 8827 |
| betrayal reliability from 9000 | 4315 |
| repeated weak opposite crossing | event 34 |
| old constructor | AffiliationKind.NONE |
| duplicate old event | IDEMPOTENT, receipt cardinality 1 |

Suite сохранил agreement counter equality, memory salience scaling, same-sign reputation behavior и relationship dimensions. Ни одна формула или порядок применения SocialService не менялись.

## Exact final gates

После последнего source/test/status-doc изменения выполнена финальная последовательность через Apache Ant 1.10.17:

| # | Command | Result | Cases | Ant total | Wall |
|---:|---|---|---:|---:|---:|
| 1 | phantom-social-humanization-goal030b-test | PASS | 8/8 | 24 s | 24.964 s |
| 2 | phantom-social-events-test | PASS | 4/4 | 19 s | 19.523 s |
| 3 | phantom-social-modifiers-test | PASS | 3/3 | 19 s | 19.871 s |
| 4 | phantom-conversation-social-style-test | PASS | 1/1 | 18 s | 18.697 s |
| 5 | ровно один final jar | BUILD SUCCESSFUL | — | 16 s | 16.998 s |

Первичная команда с коротким именем ant не запустила Ant, потому что ant отсутствовал в PATH; exit 1 был launcher lookup failure до compile/test. После обнаружения штатного C:\Users\endim\AppData\Local\CodexTools\apache-ant-1.10.17\bin\ant.bat финальная последовательность выполнена целиком в exact order. Цель jar была вызвана ровно один раз.

Compile-tests повторил только две historical JDK removal warnings для System.runFinalization() в Goal029 CP2/CP3 suites. Новых warnings нет. Не запускались aggregate, DB, integration, CP1, CP2, soak, verify или geodata gates.

## Exact changed files

1. java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialCatalog.java — strict affiliation section parser, immutable parsed table, pure lookup.
2. dist/game/data/phantoms/social/high-five-social-v1.xml — authoritative exact 4x5 policy.
3. test/java/org/l2jmobius/tests/phantoms/PhantomSocialHumanizationGoal030BSuite.java — valid-table evidence и семь temp-XML negative controls.
4. docs/PHANTOM_BOTS_ROADMAP.md — Goal030B/030B1 corrective statuses.
5. docs/phantoms/PHANTOM_RELEASE_GATE.md — corrective status и sequence 030B/030B1.
6. docs/phantoms/reports/030b1-data-driven-affiliation-process-closeout.md — этот отчёт.

Production diff состоит только из PhantomSocialCatalog и authoritative social XML. PhantomSocialService, PhantomSocialModel, SocialEvent/SocialEventContext, SocialState, schema/codec, DB/ProfileRepository, Clan, PvP, Party и Conversation owners не изменены. Config, migrations, build.xml, launcher и README.ru.md не менялись. User-owned untracked task packages оставались read-only и не staging.

## Process, checks and Git

- apply_patch invocation count = 0.
- Все содержательные правки: exact/counted anchors, UTF-8 without BOM temporary file в той же директории и final Move-Item.
- Один ранний PowerShell alias collision не применил replacements и не изменил содержимое; повтор выполнен с уникальным helper name.
- UTF-8 decode/BOM, temporary remnants, exact 4/20 XML count, production lookup и scoped diff проверены.
- Mojibake-маркеры в изменённых файлах проверены: rg expected no-match exit 1, совпадений 0.
- Escaped Cyrillic в изменённых файлах проверены: rg expected no-match exit 1, совпадений 0.
- git diff --check, полный diff, staged allowlist и forbidden-owner scope выполняются перед commit.
- Использованные Git-команды разрешены TASK/пользователем: status --short --branch, rev-parse HEAD/upstream, branch --show-current, targeted diff/name-only, последующие diff --check/status/add/diff --cached/commit/push.
- Required parent и branch подтверждены до правок; history rewrite/force push не выполнялись.

Commit subject: fix(phantoms): externalize social affiliation policy.
Фактические commit SHA и push result приводятся в финальном сообщении после фиксации этого отчёта.

## DB, performance, limitations and next step

DB не инициализировалась и не изменялась; задача полностью DB-free. Новых worker/thread/timer, I/O в hot path, logging или lifecycle owners нет. Runtime lookup остаётся O(1), XML разбирается один раз при load.

Catalog hash изменился содержательно, поэтому существующий authority-drift fail-closed contract сохраняется. Canonical clan/alliance/war wiring по-прежнему принадлежит Goal030C. Goal030B не отмечается ACCEPT самостоятельно.

Следующий шаг: independent review Goal030B1; после него Goal030C, затем новый CP2 package.