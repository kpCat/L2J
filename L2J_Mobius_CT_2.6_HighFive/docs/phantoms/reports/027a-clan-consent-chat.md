# Goal 027A — complete clan consent and chat flow

## Status

SUCCESS

Review state: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.

occurred_context_compaction: no

## Summary

Закрыты ровно два blocker независимого review Goal 027 CP1.

- R027A-01: PHANTOM clan.join отвечает exact REFUSE текущему ClanInvitationService invite при mismatch, перед expiry, revision/goal replacement, explicit cancel и service stop. Matching invite использует exact ACCEPT, а успех подтверждается canonical membership. REAL остаётся manual. Stale identity не удаляет более новое приглашение.
- R027A-02: добавлены explicit clan.chat goal, candidate.clan.chat и clan.chat.advance. Goal требует ACTIVE/future deadline, exact subject при наличии, exact current clan target, пустой validSources, explicit text length constraint и bounded payload. advanceChat делегирует idempotent postClanChat.

Creation/restart, roles/leadership, treasury/withdrawal, profile persistence schema, ClanTable/Clan/warehouse, alliances/wars, Goal026 и broad chat/Party/Combat/PvP не менялись.

## Read-first и local patterns

Прочитаны Agents.md, master plan, workflow/task standards, весь package 027A, целевые участки PhantomClanService, PhantomClanDecision, ClanInvitationService.respond, focused recruitment/chat tests и generic Goal/Decision contracts. Корневой README.md отсутствует.

Переиспользованы:

- exact identity response и stale protection из ClanInvitationService.respond / PhantomPartyCoordinator;
- cleanup-before-terminalization из bounded raid/party lifecycle;
- generic candidate/action adapter из PhantomClanDecision.

## Changed files

- java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java
- java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDecision.java
- test/java/org/l2jmobius/tests/phantoms/PhantomClanGoal027Checkpoint1Suite.java
- test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
- build.xml
- PHANTOM_DEVELOPMENT_MASTER_PLAN.md
- docs/PHANTOM_BOTS_ROADMAP.md
- docs/phantoms/reports/027a-clan-consent-chat.md

## Architecture decisions

1. Incoming cleanup наблюдает current invitation и отвечает REFUSE с exact InvitationIdentity. Canonical service выполняет compare-before-remove, поэтому stale identity не очищает newer invite.
2. REAL members не входят в PHANTOM response helper. Matching PHANTOM ACCEPT завершает goal только после canonical membership match.
3. PhantomGoal.constraints хранит только numeric values. Explicit chat payload использует существующий bounded string slot acquisitionMethod; exact constraint text хранит и проверяет заявленную длину. Persistence schema не менялась.
4. No autonomous phrase generation: chat достижим только из explicit ACTIVE goal и idempotent по exact profile/goal/revision/text.

## Consent evidence

Focused regression доказывает:

- mismatch: exact REFUSE, no ACCEPT, REPLAN;
- expiry: exact REFUSE до EXPIRED;
- revision replacement: old active join refused before terminalization;
- explicit cancel и service stop: exact REFUSE;
- stale identity: newer invite остаётся;
- REAL candidate: manual pending invite, zero automatic responses;
- matching invitation: exact ACCEPT и canonical clan ID.

## Clan chat evidence

Focused regression доказывает:

- Decision registry содержит candidate.clan.chat и clan.chat.advance;
- normal Decision execution достигает backend clan chat;
- repeated exact execution даёт один backend delivery;
- wrong clan, blank, oversize, non-empty validSources и expired goal не dispatch;
- random phrases/timer/worker/broadcast path не добавлены.

## DB, migrations, configs

Новых таблиц, migrations и config keys нет. Рабочая БД не использовалась. Profile persistence schema не менялась.

## Commands and results

- ant phantom-clan-consent-chat-goal027a-test — shell-level failure до Ant: ant отсутствовал в PATH; target не исполнялся.
- .\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-consent-chat-goal027a-test — BUILD SUCCESSFUL, seed 27002711, 2/2 PASS.
- .\.phantom-local\apache-ant-1.10.17\bin\ant.bat phantom-clan-chat-decision-goal027cp1-test — после exact text constraint BUILD SUCCESSFUL, seed 27002701, 1/1 PASS; production/test sources compiled.
- .\.phantom-local\apache-ant-1.10.17\bin\ant.bat jar — единственный jar, BUILD SUCCESSFUL; server JAR copied в dist/libs.
- git diff --check — PASS после добавления отчёта.

Forbidden ant verify, all-Phantom, broad Party/Combat/PvP, Goal026, stress/soak и production DB tests не запускались.

## Performance и lifecycle

Новых потоков, scheduler/future, DB I/O и scan нет. Cleanup caller-driven и bounded. Aggregate занял 19 секунд, focused chat — 18 секунд, jar — 17 секунд. Отдельный performance smoke не требовался.

## Deviations, limitations, risks

- Прямой ant из PATH не стартовал; использован project-local Ant 1.10.17.
- String payload ограничен generic PhantomDecisionKey slot; broad Goal/persistence redesign был запрещён. postClanChat сохраняет MAX_CHAT_TEXT guard.
- Live GameServer/client manual gate не запускался: scope покрыт deterministic focused tests и полной compile/jar.
- Следующий шаг — independent review 027A; CP2 не начинать.

## Git delivery

- branch: feature/phantom-world
- required parent: b3803a011c063359b166c12000f3281a90ea5d1c
- commit subject: fix(phantoms): complete clan consent and chat flow
- commit SHA: этот delivery commit; exact SHA приводится в финальном сообщении
- push result: после включения отчёта в commit; exact result приводится в финальном сообщении
- Git inspection/diff/commit/push разрешены TASK и workflow; amend/rebase/reset/force-push не используются.

## Next step

Independent review Goal 027A по R027A-01/R027A-02. До verdict Goal 027 CP1 остаётся CHANGES_REQUIRED, Goal 027A — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW, Goal 027 overall — IN_PROGRESS, Checkpoint 2 — NOT_STARTED.
