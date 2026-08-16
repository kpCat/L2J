# Goal 025 — PvP/PK, threat и bounded escalation

Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Verdict: PARTIAL — implementation complete, final plain verify blocked by historical status assertion
Branch: feature/phantom-world
Required parent: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Accepted baseline: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Commit subject: feat(phantoms): add pvp threat escalation
Commit SHA: фиксируется внешним final handoff, поскольку commit не может содержать собственный SHA
Remote HEAD: проверяется после ordinary push и фиксируется внешним final handoff
Seed: 25002501

## Prior independent review truth

- Goal024A=ACCEPT.
- R024A-01=CLOSED.
- R024A-02=CLOSED.
- R024A-03=CLOSED.
- Goal024 overall=ACCEPT.
- Goal025=AUTHORIZED.
- Goal026+=NOT_STARTED.

## Summary

Реализован bounded PvP/PK threat/escalation поверх существующих owners. Goal 012/012A остаётся единственным combat owner: Player PvP получил отдельный explicit request/path внутри того же CombatService, actor lease и worker budget, а legacy Monster attack/cast сохранён изолированным.

Physical PvP вызывает canonical target.onForcedAttack(_player), skill PvP — canonical _player.useMagic(skill, forceUse, false). ClientPacket не создаётся. Phantom-код не изменяет напрямую HP/CP/PvP flag/PvP kills/PK/karma/drop inventory.

Aggression допускается только от ACTUAL_ATTACK, PARTY_DEFENSE, FARMING_ESCALATION или REVENGE. Visibility, PvP flag, karma, low HP, selected target и local support остаются контекстом. Proactive neutral-target force-PK проходит persisted owner authority, durable warning receipt, delay, strength/risk и per-pair budget; reactive attack/party defense может действовать сразу.

## Read-first и source-backed evidence

Обязательные документы прочитаны полностью:

- Agents.md;
- PHANTOM_DEVELOPMENT_MASTER_PLAN.md;
- docs/PHANTOM_BOTS_ROADMAP.md;
- docs/phantoms/CODEX_WORKFLOW_CONTRACT.md;
- docs/phantoms/TASK_PACKAGE_STANDARD.md;
- весь docs/phantoms/tasks/025-pvp-threat-escalation package;
- prior Goal 024/024A reports и review artifacts.

README.md, DATA.md и отдельные code-map/pattern files для Goal 025 не найдены; корневой readme.txt прочитан.

Подтверждённые source paths:

- AttackRequest делегирует target.onForcedAttack(player);
- RequestMagicSkillUse делегирует player.useMagic(skill, ctrlPressed, shiftPressed);
- Player/PlayerStatus и Playable death/reputation владеют CP→HP, PvP flag, PvP/PK kills, karma и death drop;
- item XML задаёт 5591/5592 и skill 2166 levels 1/2;
- registered ItemSkills handler владеет Olympiad/reuse/consumption;
- WorldRegion и существующий Goal 012 acquisition scan дают локальный bounded TreeMap pattern;
- AntiFeedManager и canonical Player/Playable death path подтверждают PvP/PK consequence ownership.

## Локальные аналоги и переиспользованный паттерн

- Goal 012/012A: PhantomCombatService, actor lease, single worker budget, capability resolver, observed terminal truth.
- Goal 017: typed party directive и exact member protection evidence.
- Goal 018: persisted social/revenge authority и idempotent event recording.
- Goal 020: typed outbound plan, durable submission/receipt и existing ChatHandler execution ownership.
- Goal 024: exact bilateral persisted ESCALATED evidence.
- Navigation: existing plan/movement и external combat-action lease.
- PhantomSystem: disabled gate, composite scheduler, materialization lifecycle и ordered stop.
- Existing Ant launcher/suites: deterministic seed, DB guard, focused route и one aggregate target.

## Изменения и bounded expansions

Production:

- новый package phantoms/pvp: model, validated policy, context join, optimistic store/codec, lifecycle/scheduler service;
- Goal 012 combat files: explicit PvP request/session branch, exact Player target observation, physical/magic execution, CP handler path, consequence snapshot и bounded local-support aggregate;
- Goal 017 party files: distinct PROTECT_MEMBER_PVP directive/evidence без ослабления Monster tactics;
- Goal 018 bridge и social event codes: ATTACK_RECEIVED, KILL_CAUSED, DEATH_SUFFERED, HELP_RECEIVED;
- Goal 020 execution store/service и bridge: durable WARNING/HELP_REQUEST/DISENGAGE submit/receipt;
- Goal 024 service: read-only exact current bilateral pvpEscalation evidence;
- navigation service/coordinator: retreat через existing plan/movement owner и Goal 012 external lease;
- PhantomSystem: disabled-first wiring, shared scheduler/lifecycle и stop ordering;
- versioned dist/game/data/phantoms/pvp/pvp-policy-v1.xml.

Test/process:

- PhantomPvpSuite и launcher/build targets;
- existing PhantomCombatServerIntegrationSuite expanded only for real Player PvP/CP/consequence paths;
- tools/phantoms/verify-task-025.ps1 contains task-package hash guard, forbidden mutation/scan checks and exact changed-file allowlist;
- architecture contract, this report, pending independent review handoff and status-only master/roadmap updates;
- unchanged Goal 025 task package included per manifest.

Дополнительные read/change expansions сверх исходного short set были необходимы только по exact call path: PhantomCombatSession для branch ownership; conversation ExecutionStore/Service для durable receipt; party model/backend/coordinator/tactics для distinct PvP protection; navigation service для existing movement lease; social XML/bridge для typed events; server integration suite для real Player fixtures; build/launcher/verifier/docs для acceptance gates.

## Architecture decisions

1. Второй combat engine не создан; PvP admission и execution принадлежат Goal 012.
2. Exact authority hash связывает source evidence, current Player identity, instance и policy hash.
3. Local risk scan не возвращает Player IDs: только observedPlayers/actorSupport/targetSupport с cap 1..32, поэтому scan не способен создать victim candidate.
4. Proactive budget сохраняется консервативно до combat admission; capacity rejection не разрешает повторную неконтролируемую атаку.
5. CP potion success существует только при observed count decrease и CP increase либо reuse truth.
6. Restart не делает global recovery scan; state поднимается только для materialized lifecycle profile.
7. Один bounded queue и shared pulse заменяют per-phantom thread/future/timer.

## DB, config и runtime scope

- Production DB не использовалась.
- Server integration выполнялась только через PhantomTestDatabaseGuard для l2jmobiush5_phantom_test.
- Новых SQL/migrations нет.
- Production config dist/game/config не изменялся.
- .l2j не изменялся.
- Другие хроники не изменялись.
- Phantom World остаётся выключенным существующим global feature flag.
- Policy version 1 безопасно валидируется до запуска.

## Verification

Pre-freeze focused evidence:

- compile-tests: BUILD SUCCESSFUL; 2176 production + 88 test sources.
- phantom-pvp-combat-integration-test: pure pvp-combat 3/3 PASS.
- phantom-pvp-combat-integration-test: guarded real server integration 4/4 PASS.
- Real server cases: physical forced attack, Player.useMagic, CP ItemSkills plus consequence snapshot, canonical PvP/PK/karma outcomes.
- Ранее выполненные affected suites: combat core 50/50, action ownership 33/33, baseline server integration 20/20, party state 6/6, party tactics 2/2, social 4/4 + 3/3, conversation 2/2 + 5/5, farming lifecycle 5/5 + restart 3/3, navigation 50/50, skeleton 14/14.
- Ранее выполненные focused Goal025 modes: policy 2/2, admission 2/2, combat 3/3, CP 2/2, party help 1/1, warning/social 1/1, restart 1/1, performance 1/1; 200000 decisions менее 5 секунд.

FINAL_STATIC_VERIFY: PASS — PHANTOM_STATIC_VERIFY_025_OK
FINAL_GOAL025_AGGREGATE: PASS — 22 reports, 214/214, failures=0, errors=0, skipped=0
FINAL_ANT_VERIFY: FAILED — historical verify-task-014a.ps1 requires obsolete `Goal 025: NOT_STARTED`; no rerun
FINAL_ANT_JAR: PASS — 2176 production sources, BUILD SUCCESSFUL in 16 seconds
FINAL_DIFF_CHECK: PASS — executed after this process-only update
FINAL_MOJIBAKE_SCAN: PASS — 0 matches in all 48 staged UTF-8 text files and added lines
FINAL_ESCAPED_CYRILLIC_SCAN: PASS — 0 matches in all 48 staged UTF-8 text files and added lines

Verification discipline: unrelated flake не возникал; targeted rerun allowance не использован. После production/test freeze выполняется один final Goal025 aggregate, один plain Ant verify и один explicit Ant jar. Docs/process-only updates после них не требуют ceremonial rerun.

## Commands and process

Git inspection был разрешён task package и Agents.md как scope/parent guard. Использованы bounded команды:

- git status --short --branch;
- git rev-parse HEAD;
- git branch --show-current;
- git rev-parse --abbrev-ref --symbolic-full-name @{upstream};
- git diff --name-only 922f72c0d422904dcbdc6215a5cc1167a1bb84fb --;
- git ls-files --others --exclude-standard --;
- git diff --check;
- final git add/commit/push и git ls-remote --heads origin feature/phantom-world.

Нативный apply_patch был недоступен из-за Windows sandbox helper error apply deny-read ACLs. Точечные edits выполнены через git apply либо exact checked old→new UTF-8 replacement с отказом при missing/non-unique anchor. Массовое форматирование не выполнялось.

ant отсутствовал в системном PATH. Использован source-confirmed bundled Apache Ant 1.10.14 из установленной IntelliJ через JDK 25.0.4; исполнялись существующие build.xml targets, без изменения build system.

## Deviations, limitations и risks

- Незавершённые gates: независимый review Goal 025 и plain `ant verify`.
- Independent-review finding G025-F01 (process/status): historical `tools/phantoms/verify-task-014a.ps1:145` жёстко требует `Goal 025: NOT_STARTED`, поэтому корректный текущий roadmap status `Goal 025: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` приводит к `BUILD FAILED` на `build.xml:2226`. Все suites до этого gate завершились; повторный verify запрещён verification discipline. Production defect не обнаружен и production-код по этому finding не изменялся.
- Commit SHA и remote HEAD нельзя записать внутрь того же atomic commit без второго commit; они фиксируются в final handoff.
- Полноценное поведение pathfinding без geodata остаётся ранее известным внешним ограничением; retreat сохраняет navigation terminal truth и не обходит владельца.
- Combat capacity rejection консервативно расходует уже persisted proactive attempt; это безопаснее автоматического повторного force-PK.

- Review ZIP не создаётся по task package.

## Next step

Независимый reviewer проверяет docs/phantoms/reviews/025-independent-review.md и либо принимает Goal 025, либо открывает findings. Goal 026+ не начинается до принятия текущего gate.