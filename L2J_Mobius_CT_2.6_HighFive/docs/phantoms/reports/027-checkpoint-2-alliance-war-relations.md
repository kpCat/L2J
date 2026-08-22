# Goal 027 Checkpoint 2 — alliances, relations and war lifecycle (resumed)

## Status

`BLOCKED_NATIVE_SOCIAL_SEAM_REQUIRED`

Checkpoint 2 не реализован. Accepted Goal 027C/027D seam достаточен для exact alliance/war identity, membership ABA, war-id replay и retirement fencing, но не предоставляет Phantom caller безопасное доказательство полного current alliance member set для autonomous dissolve.

## Review state

`BLOCKED`

Delivery state:

- Goal 027C: `ACCEPT after Goal 027D` (frozen accepted seam, не изменялся);
- Goal 027D: `ACCEPT` (frozen accepted seam, не изменялся);
- Goal 027 Checkpoint 2: `BLOCKED_NATIVE_SOCIAL_SEAM_REQUIRED`;
- Goal 027 overall: `IN_PROGRESS_BLOCKED_NATIVE_SOCIAL_SEAM_REQUIRED`.

SUCCESS-only статусы `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` и `IN_PROGRESS_PENDING_CP2_INDEPENDENT_REVIEW` не выставлялись.

## occurred_context_compaction

`yes`

## Summary

Read-first audit и частичная pre-test реализация выявили safety gap. Phantom-side causal managed peer refs не являются доказательством полного canonical alliance membership. Возможен exact контрпример: Phantom leader A создаёт G1, managed Phantom B вступает через CP2, затем REAL clan C вручную вступает в G1 через штатный REAL path. Phantom metadata знает только A/B, но native `ClanAllianceService.dissolve(A, G1)` корректно и намеренно отсоединит A/B/C.

Ослабленный predicate, проверявший только actor и causal Phantom peers, удалён. Вся незавершённая CP2 production/schema/Decision/social/chat реализация полностью откатана до required parent; новый bridge и незавершённый test suite удалены. Frozen native 027C/027D seam не менялся. Existing CP1 schema version 1, bounds 64/256 и runtime behavior сохранены.

Leave собственного non-leader Phantom clan этим blocker не затронут, однако TASK требует единый coherent vertical slice, включающий safe dissolve; частичный delivery не выдавался за SUCCESS.

## Baseline

- repository root: `C:/Users/endim/L2J_Mobius`;
- module: `C:/Users/endim/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive`;
- branch: `feature/phantom-world`;
- upstream: `origin/feature/phantom-world`;
- required parent / initial HEAD: exact `9b8126860bc4f59dc03c316a75e9d2ce6f79ec79`;
- user-owned untracked task packages: read-only, не изменялись и не staged;
- production DB `l2jmobiush5`: не использовалась.

## Read-first evidence

Прочитаны `Agents.md`, root `README.md`, master plan, roadmap, workflow/task standards, полный resumed CP2 package, отчёты CP1/027A/027B/027C/027D, current Phantom clan/store/backend/Decision/system/social/chat/PvP code, accepted `ClanAllianceService`, `ClanWarService`, `ClanSocialRepository`, `ClanSocialMutationFence`, `ClanTable` и focused test/build patterns. Parent `AGENTS.md` и module `README.md` не найдены. Manifest SHA-256 resumed package до реализации совпал 6/6.

Локальные принятые паттерны: exact `AllianceIdentity`, `MembershipEpoch`, exact `WarIdentity.warId`, native retirement fence, Goal 018 idempotent social events, Goal 020 generated chat observation, Goal 025 durable policy cooldown и CP1 bounded caller-driven lifecycle.

## Exact native safety audit

Public `ClanAllianceService` предоставляет:

- `currentIdentity(Clan)`;
- `create(Player, String)`;
- `join(Player, Player, AllianceIdentity, MembershipEpoch)`;
- `leave(Player, AllianceIdentity)`;
- `expel(Player, Clan, AllianceIdentity)`;
- `dissolve(Player, AllianceIdentity)`.

Public bounded exact membership observation отсутствует. Внутренний `StateAccess.allies(int)` package-private. Production `LiveStateAccess.allies(int)` вызывает `ClanTable.getClanAllies(int)`. Реализация `ClanTable.getClanAllies(int)` проходит по всей `_clans.values()`, то есть это запрещённый global registry scan, а не O(alliance-size) source.

`ClanSocialRepository` внутри native service атомарно валидирует exact durable membership/epochs при dissolve, но не является public observation/command contract для Phantom caller; прямой SQL или вызов internal repository из Phantom нарушил бы canonical ownership.

Даже отдельный read-only snapshot без compare-on-mutate недостаточен: REAL C может вступить между Phantom proof и `dissolve`. Native dissolve заново захватит уже расширенный A/B/C set и безопасно для native semantics, но небезопасно для запрета autonomous REAL mutation. Поэтому proof должен быть exact-incarnation и revalidated внутри той же native mutation fence.

## Smallest missing seam

Нужен bounded native contract уровня `ClanAllianceService`, не Phantom-owned registry:

1. получить для captured `AllianceIdentity` exact current member proof в O(alliance-size), содержащий полный sorted clan-id set и per-member membership epoch/generation;
2. передать этот proof в autonomous dissolve command либо получить одноразовый proof token;
3. внутри native retirement/mutation fence сравнить identity и exact member epochs/set перед mutation;
4. при любом неизвестном member, REAL-only C, membership ABA или concurrent join вернуть typed `STALE`/`INELIGIBLE` с zero mutation;
5. позволить Phantom caller сравнить exact canonical set с bounded causal managed clan set без global ClanTable/profile scan.

Минимальная форма — `AllianceMembershipProof` плюс exact expected-proof overload для dissolve. Добавление только count или отдельного unfenced snapshot не закрывает TOCTOU.

## Frozen seam decision

Goal 027C/027D не изменялись «для удобства». Packet/request emulation, client handler calls, `ClanTable.getClans()`/`getClanAllies()` scan, persisted Phantom metadata trust, direct SQL/social setters и второй clan engine не использовались.

Это ровно предусмотренный TASK STOP: `BLOCKED_NATIVE_SOCIAL_SEAM_REQUIRED`.

## Changed files

Финальный delivery изменяет ровно один tracked file:

- `docs/phantoms/reports/027-checkpoint-2-alliance-war-relations.md` — resumed blocker audit, smallest missing seam, rollback и verification evidence.

Production Java, tests, `build.xml`, schema/SQL, master plan, roadmap, frozen 027C/027D files и task packages в финальный diff не входят.

## Goal/action keys

CP2 goal/action keys не доставлены из-за STOP. Планировавшиеся `clan.alliance.create`, `clan.alliance.join`, `clan.alliance.leave`, `clan.alliance.dissolve`, `clan.war.declare`, `clan.war.peace`, `clan.alliance.chat` и соответствующие Decision actions отсутствуют в финальном production code. CP1 keys остаются без изменений.

## Identity and replay rules

Не доставлены как Phantom behavior. Accepted native identities остаются frozen truth:

- alliance incarnation: exact `AllianceIdentity(leaderClanId, generation)`;
- join target ABA fence: exact `MembershipEpoch(clanId, allianceId, generation, counter)`;
- war incarnation: exact `WarIdentity` и `warId`;
- retirement: accepted Goal 027D fence.

G1/W1 replay logic не добавлялась, потому что safe G1 dissolve precondition нельзя доказать через public seam. Existing 027C/027D replay guarantees не изменены.

## Consent protocol

Bilateral later-pulse Phantom join/peace protocol не доставлен. Частичная реализация source offer + target later accept была удалена вместе со всем CP2 vertical slice. REAL-only clans не auto-mutating. Smallest missing dissolve proof должен быть закрыт отдельной accepted native corrective task до повторного CP2 resume.

## Relation and anti-oscillation semantics

Goal 018-backed events, durable hysteresis и Goal 020 alliance chat не доставлены. Частично подготовленные mappings были удалены, чтобы canonical failure/blocker не стал shadow/fake success. Native reputation не заменён Phantom score, CP1 `relationReferences` и schema v1 сохранены без изменений.

## Persistence / DB / migrations

- production DB не подключалась и не изменялась;
- test DB не требовалась и не изменялась;
- migrations отсутствуют;
- `clan.organization` остаётся schema v1;
- existing role/contribution/relation refs не переписывались.

## Commands and results

Baseline/read-only:

- `git status --short --branch` — branch/upstream correct; initial tracked diff empty; только user-owned untracked task packages;
- `git rev-parse HEAD` — exact `9b8126860bc4f59dc03c316a75e9d2ce6f79ec79`;
- `git branch --show-current` — `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world`;
- resumed task manifest — SHA-256 6/6 matched.

Native seam audit:

- `rg` по `ClanAllianceService`, `ClanTable`, `Clan`, `ClanSocialRepository` — public exact member observation не найден;
- `ClanAllianceService.StateAccess.allies` — package-private;
- `ClanAllianceService.LiveStateAccess.allies` — delegates to `ClanTable.getClanAllies`;
- `ClanTable.getClanAllies` — full `_clans.values()` scan;
- public `ClanAllianceService` observation — только `currentIdentity(Clan)`.

Rollback verification:

- clean-filtered `git hash-object --path` пяти ранее изменённых production files exact совпал с index blob SHA для каждого;
- `ant compile` — exit 0, `BUILD SUCCESSFUL`, 2203 production sources, 14 seconds;
- CP2 production bridge/test-temp удалены;
- frozen 027C/027D files имеют zero diff.

SUCCESS-only final order не запускался: CP2 focused target не создавался, 027D/027C/027B/027A/CP1 и Goal018/020/025 regressions не повторялись, final `jar` не запускался. Причина — обязательный STOP до допустимой source implementation. Broad aggregates, performance, soak, stress и plain `ant verify` также не запускались.

## Performance / concurrency / lifecycle

Финальный production runtime равен required parent: новых scans, DB calls, locks, queues, timers, workers, threads, schedulers, schemas или lifecycle state нет. Existing CP1 64 active / 256 terminal bounds сохранены.

## Deviations

- Реализация и mandatory CP2 tests не завершены из-за concrete missing native safety contract.
- Первая попытка exact global membership proof через `ClanTable.getClanAllies()` была отклонена как запрещённый global scan и не попала в source.
- Временный causal-peer-only safety predicate был признан недостаточным по контрпримеру A/B + REAL C и полностью удалён.
- `apply_patch` не смог писать из-за Windows ACL; bounded temporary-file fallback использован только для отчёта. Production rollback выполнен exact reverse diff, затем подтверждён index blob hashes.
- Git modified stat-flags пяти rollback files на Windows могли отображаться до index refresh, однако `git diff`/`git diff --cached` пусты и clean-filtered hashes exact совпадают; эти paths не входят в commit.

## Scope and encoding audit

`PASS`

- final tracked diff allowlist: только этот report;
- frozen 027C/027D diff: empty;
- other chronicles: zero;
- task packages: unchanged/untracked/not staged; resumed manifest SHA-256 6/6 PASS;
- CRLF-safe `git -c core.whitespace=cr-at-eol diff --check`: PASS;
- strict UTF-8 decode изменённого файла: PASS;
- mojibake-маркеры в изменённом файле проверены: совпадений нет;
- escaped Cyrillic в изменённом файле проверены: совпадений нет.

## Risks

До native corrective task autonomous alliance dissolve для Phantom запрещён. Реализация create/join/leave/war отдельно не должна обходить coherent CP2 gate или превращать partial slice в SUCCESS.

## Git delivery

- branch: `feature/phantom-world`;
- required parent: `9b8126860bc4f59dc03c316a75e9d2ce6f79ec79`;
- intended subject: `docs(phantoms): record Goal 027 dissolve safety blocker`;
- ordinary commit/push only;
- no amend/rebase/reset/squash/merge/force push;
- exact commit SHA и push result возвращаются в финальном сообщении.

## Next step

Создать отдельную corrective task для bounded exact `AllianceMembershipProof` + compare-on-dissolve contract внутри native `ClanAllianceService` fence. После independent acceptance повторно resume CP2 и добавить обязательный deterministic case: managed A/B + canonical unexpected REAL C => autonomous dissolve returns non-success and leaves exact G1/A/B/C unchanged.
