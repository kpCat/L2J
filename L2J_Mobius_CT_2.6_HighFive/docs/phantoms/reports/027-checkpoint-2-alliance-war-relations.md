# Goal 027 Checkpoint 2 — alliances, relations and war lifecycle

## Status

`BLOCKED_CANONICAL_DOMAIN_SEAM_REQUIRED`

Checkpoint 2 не реализован: native High Five не предоставляет разрешённый transport-neutral domain seam, который одновременно сохраняет canonical eligibility, persistence result, exact alliance/war identity и stale/replay safety.

## Review state

`BLOCKED`

Delivery state:

- Goal 027 Checkpoint 1: `ACCEPTED`;
- Goal 027A: `ACCEPTED`;
- Goal 027B: `ACCEPTED`;
- Goal 027 Checkpoint 2: `BLOCKED_CANONICAL_DOMAIN_SEAM_REQUIRED`;
- Goal 027 overall: `IN_PROGRESS_BLOCKED_ON_CANONICAL_DOMAIN_SEAM`.

## occurred_context_compaction

`no`

## Summary

Выполнен полный read-first и pre-implementation audit current High Five alliance/war domain. Production/test implementation остановлена до изменений canonical behavior: alliance join/leave/expel mutation остаётся внутри client packet handlers, а clan-war eligibility остаётся внутри packet handlers отдельно от непроверяющих `ClanTable` mutators. Копирование checks/mutations в Phantom package создало бы запрещённую parallel canonical logic; вызов handlers или использование `Player` request state означали бы packet emulation.

Mock/static-only CP2 suite не добавлялась: она не могла бы доказать canonical H5 state и потому не разрешала бы `SUCCESS`. Production Java, packet handlers, native `Clan`/`ClanTable`, schema, real chat core и существующие tests не изменялись.

## Baseline

- repository root: `C:/Users/endim/L2J_Mobius`;
- module: `C:/Users/endim/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive`;
- branch: `feature/phantom-world`;
- upstream: `origin/feature/phantom-world`;
- required parent / initial HEAD: exact `87da216ad4c4a17d26d46e79e3d59098c0e47c8a`;
- tracked diff and index relative to required parent: empty;
- initial worktree contained only user-owned untracked packages 026/026d/027/027A/027B; they remained read-only and are excluded from delivery.

## Read-first evidence

Прочитаны `AGENTS.md`, root `README.md`, master plan, authoritative roadmap, workflow/task standards, все файлы CP2 package, отчёты CP1/027A/027B, четыре current Phantom clan files, Goal 027 suite/launcher/Ant wiring, `ClanInvitationService`, native alliance/war handlers, релевантные методы `Clan`, `ClanTable`, `VillageMaster`, alliance/clan chat handlers, `ChatObservationService`, `CreatureSay` path и `clan_wars.sql`. Parent `AGENTS.md` и module `README.md` отсутствуют. SHA-256 шести manifest entries task package совпал.

Локальные аналоги: exact immutable identity + compare-before-mutate + shared lock в `ClanInvitationService`; terminal receipt barrier в `PhantomClanService`; real delivered-chat capture в `ChatObservationService`.

## Canonical alliance audit

Canonical truth хранится непосредственно в `Clan`: `ally_id`/`ally_name` загружаются из `clan_data`, доступны через `getAllyId()`/`getAllyName()` и сохраняются `updateClanInDB()`. Отдельного alliance aggregate/version/generation нет; native identity — leader clan ID плюс имя.

- Create: `Clan.createAlly(Player,String)` проверяет clan leader, отсутствие alliance, clan level 5, penalties/dissolution, name format/length/uniqueness; затем устанавливает `allyId = clanId`, имя и вызывает `updateClanInDB()`. Метод `void`, DB failure логируется и не возвращается caller.
- Join eligibility: reusable check только `Clan.checkAllyJoinCondition(Player,Player)` — leader/penalties/target leader/membership/siege/war/capacity.
- Join mutation: только `RequestAnswerJoinAlly` — `setAllyId`, `setAllyName`, penalty/crest, `updateClanInDB()`. Consent identity принадлежит `Player.getRequest()`/`RequestJoinAlly`, то есть real-client state.
- Leave: `AllyLeave` напрямую очищает alliance fields/crest, выставляет penalty и вызывает `updateClanInDB()`.
- Expel: `AllyDismiss` напрямую меняет leader/target penalties и target alliance fields.
- Dissolve: `Clan.dissolveAlly(Player)` содержит checks/mutation, но использует `ClanTable.getClanAllies()` scan, меняет все member clans и имеет `void` persistence outcome.

Exhaustive call-site search не нашёл transport-neutral join/leave/expel service. Вызов handlers, заполнение `Player.getRequest()` или перенос mutation block в `L2jPhantomClanBackend` нарушил бы TASK. Полный create/join/leave/dissolve lifecycle недостижим в разрешённом Phantom-only scope.

## Canonical war audit

Native owner active war — directed sets `Clan._atWarWith` и `_atWarAttackers`; persistence owner — `clan_wars` и `ClanTable.restoreClanWars()`.

- Declare conditions существуют в `RequestStartPledgeWar`: source level/member count, `ClanAccess.WAR_DECLARATION`, exact target, non-allied target, target level/member count, no current directed war.
- Stop conditions существуют в `RequestStopPledgeWar`: exact current war, access и отсутствие online source members в attack stance.
- Mutation выполняют `ClanTable.storeClanWars(int,int)` / `deleteClanWars(int,int)`, но они не проверяют actor authority, eligibility, self/alliance constraints, expected prior state или operation identity.
- Оба mutator — `void`: сначала меняют in-memory sets/events/status, затем делают DB statement; exception подавляется логированием. Caller не может доказать durable canonical success и безопасно записать relation memory только после persistence success.
- `clan_wars` имеет только `(clan1,clan2)` primary key и peace flags; war ID, generation, revision/start timestamp отсутствуют. После external `W1 end -> W2 start` та же пара неотличима для старой peace-operation. Exact W1 stale fence невозможно реализовать в Phantom package.

Exhaustive call-site search нашёл только packet reply/start/stop/surrender paths и `ClanTable.checkSurrender`; общего validation/mutation service нет. Прямой вызов low-level mutators после Phantom-копии packet conditions запрещён и не решает persistence/error/identity race.

## Changed files

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` — только current Goal 027 blocker status;
- `docs/PHANTOM_BOTS_ROADMAP.md` — только current Goal 027 blocker status/evidence;
- `docs/phantoms/reports/027-checkpoint-2-alliance-war-relations.md` — audit/delivery report.

## Architecture decisions

1. Не создавать Phantom-owned authoritative alliance/war state.
2. Не вызывать client packets/handlers и не эмулировать request/answer state.
3. Не копировать alliance/war validation и mutation blocks в backend.
4. Не автоматизировать REAL clan leaders.
5. Не добавлять relation/diplomacy mock state до canonical success seam.
6. Не добавлять migration и не менять canonical core в CP2 scope.

## Alliance lifecycle evidence

Не реализован. Canonical create/dissolve частичны, но join/leave/expel не имеют transport-neutral mutation owner. Required vertical lifecycle нельзя честно принять частичным или mock-only результатом.

## War / peace lifecycle evidence

Не реализован. `ClanTable` methods — low-level persistence/memory mutators, а не safe domain commands. Отсутствуют shared eligibility, durable result и exact war generation для stale W1/W2 safety.

## Relation / reputation memory

`PhantomClanStore.relationReferences` bounded (до 16) и restart-safe в profile component, но это список строк без typed event/cooldown semantics. Его можно versioned-расширить без DB migration, однако делать это до canonical success seam означало бы сохранять недоказанные outcomes. Schema/payload не менялись.

## Anti-oscillation evidence

Native alliance penalties дают часть leave/dissolve cooldown, но нет shared mutation/result seam. War peace/redeclare generation/cooldown также не имеет safe owner. Phantom anti-oscillation policy не добавлялась поверх неполного lifecycle.

## Alliance chat evidence

`ChatAlliance` и `ChatObservationService` предоставляют подходящий real path (`ChatType.ALLIANCE`, `openGeneratedDispatch`, `CreatureSay` delivery capture). Его можно расширить по existing clan-chat pattern, но chat-only partial не закрывает blocker и потому не добавлялся как fake success.

## Persistence / DB / migrations

- production DB `l2jmobiush5` не использовалась;
- CP1 aggregate использовал штатный test DB guard/profile persistence suite и завершил cleanup;
- migrations, SQL schema и profile component schema не менялись;
- native `clan_wars`/`clan_data` читались только как source evidence.

## Commands and exact results

Baseline:

- `git status --short --branch` — exit 0; branch/upstream correct, tracked changes absent, only user untracked packages shown;
- `git rev-parse --show-toplevel` — exit 0; `C:/Users/endim/L2J_Mobius`;
- `git rev-parse HEAD` — exit 0; exact `87da216ad4c4a17d26d46e79e3d59098c0e47c8a`;
- `git branch --show-current` — exit 0; `feature/phantom-world`;
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — exit 0; `origin/feature/phantom-world`;
- `git diff --name-only 87da216ad4c4a17d26d46e79e3d59098c0e47c8a -- L2J_Mobius_CT_2.6_HighFive` и `git diff --cached --name-only` — exit 0, empty;
- task package SHA-256 — 6/6 manifest entries matched.

Verification available under BLOCKED:

- `phantom-clan-checkpoint2-goal027-test` — not created/not run: canonical seam отсутствует, mock/static-only CP2 запрещён;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-expired-replay-goal027b-test` — exit 0, `BUILD SUCCESSFUL`, seed `27002712`, 4/4 PASS, 2199 production + 100 test sources, 17 seconds;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-consent-chat-goal027a-test` — exit 0, `BUILD SUCCESSFUL`, seed `27002711`, 2/2 PASS, 19 seconds;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-checkpoint1-goal027-test` — exit 0, `BUILD SUCCESSFUL`, clan modes 6/6, profile persistence 18/18, chat observation 2/2, 19 seconds;
- `.\.phantom-local\apache-ant-1.10.17\bin\ant.bat jar` — ровно один final jar, exit 0, `BUILD SUCCESSFUL`, 2199 production sources; server/installer JARs built, GameServer/LoginServer copied to `dist/libs`, 16 seconds;
- `git diff --check` — exit 0, PASS; exact scope guard — PASS; strict UTF-8 decode — 3/3 PASS; task package SHA-256 — 6/6 PASS.

Plain `ant verify`, Goal 026, broad all-Phantom/Party/Combat/PvP, stress/soak и дополнительные `jar` не запускались.

## Performance / concurrency / lifecycle

Production runtime не изменён: новых scans, DB calls, locks, queues, timers, workers, threads, schedulers или futures нет. Existing CP1 bounds/terminal receipts сохранены.

Missing seam должен атомарно владеть compare-before-mutate для real-client и Phantom callers. Phantom-local locking не защищает race с real packet paths.

## Scope audit

PASS

- delivery allowlist: ровно три documentation files;
- production/test/native core/schema files: 0;
- other chronicles: 0;
- user task packages: SHA-256 6/6 unchanged, untracked и не включены;
- valid UTF-8 в изменённых files: 3/3;
- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Deviations

- Required implementation и CP2 suite не выполнены из-за обязательного STOP rule, а не заменены mock behavior.
- Первый sandboxed Git/read запуск был отклонён Windows ACL helper; те же read-only команды выполнены с разрешением.
- `apply_patch` не смог прочитать workspace из-за того же ACL helper; применён bounded Windows fallback: exact atomic replacements для двух tracked status documents и sequential small UTF-8 chunks для нового report.
- Parent `AGENTS.md` и module `README.md` не найдены; повторный поиск не выполнялся.

## Limitations / risks

Goal 027 overall остаётся незавершённым. До follow-up запрещено считать alliance/war actions доступными Phantom runtime.

Минимальный follow-up production scope:

1. canonical `model.clan` transport-neutral `AllianceService`, владеющий create/invite/accept/leave/expel/dissolve checks и mutation под shared compare-before-mutate lock; existing VillageMaster/client handlers делегируют ему;
2. transport-neutral `ClanWarService`, владеющий declare/stop eligibility, actor authority, atomic persistence result/events; existing war handlers делегируют ему;
3. canonical persistent alliance/war generation (или identity stronger than pair/state) с versioned migration для restart-safe W1/W2 stale fence;
4. typed result/durable-success evidence; затем Phantom relation memory, anti-oscillation, diplomacy, alliance chat и canonical-state focused tests.

Это изменение canonical core/packet delegation/schema требует отдельной явно разрешённой corrective task.

## Git delivery

- branch: `feature/phantom-world`;
- required parent: `87da216ad4c4a17d26d46e79e3d59098c0e47c8a`;
- commit subject: `docs(phantoms): record Goal 027 canonical seam blocker`;
- commit SHA: этот единственный delivery commit; exact SHA приводится в финальном сообщении;
- push result: выполняется после commit; exact remote result приводится в финальном сообщении;
- amend/rebase/reset/squash/merge/force push не используются.

## Next step

Подготовить corrective task с явно разрешёнными canonical `AllianceService`/`ClanWarService`, packet/VillageMaster delegation и versioned identity migration. После independent acceptance повторно запустить Goal 027 Checkpoint 2 с bounded Phantom scope.
