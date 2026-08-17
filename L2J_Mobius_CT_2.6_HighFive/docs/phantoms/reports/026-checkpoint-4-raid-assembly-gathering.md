# Goal 026 Checkpoint 4 — raid assembly, preparation and gathering

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 026 overall остаётся `IN_PROGRESS`. Checkpoint 5 остаётся `NOT_STARTED`.

## Summary

Реализован bounded process-local coordinator для явного ACTIVE `raid.prepare` goal:

- валидирует не более 16 exact `profile` / `character.object` sources;
- последовательно использует существующий CP3 recruitment API без изменения его semantics;
- отделяет invitation от consent и не отвечает на том же advance;
- принимает Phantom только при matching ACTIVE `raid.participate`, standalone target и fresh useful CP3 candidate;
- никогда не отвечает за REAL target, оставляя client prompt authoritative;
- замораживает canonical force и structural hash перед physical gathering;
- создаёт отдельный existing `PhantomPartyRouteCoordinator` route для каждой Party;
- публикует READY только после физического нахождения участников в slots и fresh CP1 `GROUP_READY`.

Добавлен `PhantomRaidDecision` по локальному Rift pattern. Production lifecycle освобождает assembly invitations/routes до Party, Combat и Navigation shutdown.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/model/groups/CommandChannelInvitationService.java`
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidModel.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`

Focused verification:

- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidAssemblySuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomCommandChannelLifecycleSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidReadinessSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`

Documentation/package:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/phantoms/tasks/026-checkpoint-4-raid-assembly-gathering/TASK.md`
- `docs/phantoms/tasks/026-checkpoint-4-raid-assembly-gathering/PRIOR_INDEPENDENT_REVIEW.md`
- `docs/phantoms/tasks/026-checkpoint-4-raid-assembly-gathering/PACKAGE_MANIFEST.json`
- `docs/phantoms/tasks/026-checkpoint-4-raid-assembly-gathering/CODEX_LAUNCHER.txt`
- этот отчёт.

## Architecture decisions

- `CommandChannel` остаётся canonical mutation authority; добавлен только exact stale-safe cleanup `cancel(InvitationIdentity)`.
- `CommandChannel` не помещён в `PartyState`.
- Движение не дублируется: CP4 владеет raid process, а каждая Party использует существующий `PARTY_ROUTE`.
- Mixed Party двигает только Phantom members; all-REAL Party наблюдается без synthetic movement.
- Structural force hash исключает transient readiness flags; membership/leader/Party/CommandChannel drift возвращает assembly к reassembly.
- Staging выбирается в порядке content topology anchor, goal selected anchor, exact live boss.
- Live fallback использует deterministic ring 1800; Party anchors разделены радиусом 300.
- Hardcoded boss coordinates и teleport отсутствуют. GeoEngine используется только для Z normalization, Navigation остаётся feasibility authority.
- Raid authority location adapter является read-only и не меняет spawn/status.

## Goal contract, recruitment and consent

Leader обязан иметь ACTIVE `raid.prepare`, exact `raid.content`, future deadline и валидные bounded sources. Participation goal сам по себе assembly не создаёт.

Recruitment выполняется по одному CP3 `recruitNext` attempt на advance. Phantom consent возможен только на последующем advance при exact pending invitation, standalone candidate, matching ACTIVE `raid.participate` для того же content и fresh CP3 useful assessment. Missing, stale или mismatched willingness приводит к exact refuse. REAL consent остаётся только ручным client action.

## Staging and READY_AT_STAGING evidence

Final ready receipt содержит assembly identity, immutable structural hash, authoritative staging centre, per-Party slots, fresh readiness и completion time. Focused suite подтверждает три независимых Party route claims, физическое размещение участников в slots и повторную CP1 readiness проверку непосредственно перед READY.

## Lifecycle and cleanup

Cancel, deadline, goal replacement, force/topology/live-centre drift и shutdown отменяют только exact owned invitation и owned route groups. Stale invitation identity не может удалить более новый pending invite. Cleanup вызывается в `PhantomSystem` до Party, Combat и Navigation lifecycle stop.

## DB, migrations and configs

Миграции, таблицы и config keys не добавлялись. Runtime assembly state bounded и process-local. Рабочая production schema автоматическими CP4 тестами не изменялась; affected CP2 lifecycle target использовал существующий test configuration. Phantom World сохраняет существующий feature-flag lifecycle.

## Commands and test results

- plain `ant compile`: `ant` отсутствует в PATH; команда не запустилась.
- `.phantom-local/apache-ant-1.10.17/bin/ant.bat compile`: `BUILD SUCCESSFUL`.
- `phantom-raid-assembly-consent-test`: 3/3 passed, seed `26002641`.
- `phantom-raid-gathering-staging-test`: 4/4 passed, seed `26002641`.
- `phantom-raid-decision-lifecycle-test`: 2/2 passed, seed `26002641`.
- `phantom-command-channel-lifecycle-test`: 7/7 passed, seed `26002621`.
- `phantom-raid-readiness-authority-test`: 4/4 passed, seed `26002601`.
- единственный `phantom-raid-assembly-checkpoint4-test`: `BUILD SUCCESSFUL`.
- единственный `ant.bat jar`: `BUILD SUCCESSFUL`; jars скопированы в рабочий `dist/libs`.
- strict UTF-8: 20/20 files decoded successfully.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Запрещённые plain `ant verify`, Goal025, CP1/CP2/CP3/026B aggregates, broad Goal017, all-Phantom и stress loops не запускались.

## Performance

Отдельный benchmark не запускался по contract. Coordinator не создаёт worker/thread на assembly, ограничен 64 assembly states и 256 ready receipts, выполняет один recruitment advance за вызов и переиспользует общие Navigation/Combat services.

## Deviations and diagnostics

После первого автоматического context compaction новое исследование и новые delivery элементы не выполнялись. Завершены уже начатый coherent block, обязательные focused gates, factual handoff, commit и push tail.

Focused fixture потребовала локальных исправлений Java string escaping, обязательного Rift/Raid/Epic knowledge coverage и deterministic registry expected order. Gathering gate выявил и исправил lowercase-to-uppercase topology SHA-256 adapter на границе existing PARTY_ROUTE.

`occurred_context_compaction: yes`

## Limitations, risks and out of scope

Не реализованы entry, combat, retreat, loot, Queen Ant/Zaken execution, global discovery и raid DB saga. Они остаются CP5 или отдельным scope. Runtime integration с реальными многопартийными рейдами требует independent review/manual product gate. Process-local receipts не являются durable raid saga.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `88b7c031847c71abd4077423336caaa6bd179712`
- commit subject: `feat(phantoms): assemble and gather raid forces`
- commit SHA: текущий commit, содержащий этот отчёт; exact SHA фиксируется в final handoff
- push target: `origin feature/phantom-world`
- remote HEAD: фиксируется после push в final handoff

Несвязанные untracked CP2 package files не включаются в commit.

## Next step

Независимый review Checkpoint 4. До принятия gate Checkpoint 5 не начинается.
