# Goal 027 Checkpoint 1 — persistent clan organizations

## Status

`SUCCESS` — implementation завершена и ожидает независимого review.

- required parent: `ff631e5f71a43da6e771c3541ee59ee15ea916b3`
- Goal 026 overall: `ACCEPT`
- Goal 027 Checkpoint 1: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
- Goal 027 overall: `IN_PROGRESS`
- Goal 027 Checkpoint 2: `NOT_STARTED`
- occurred_context_compaction: `yes`

## Summary

Реализован один substantive vertical slice persistent clan organizations: explicit `clan.build` через canonical `ClanTable.createClan`; bounded bilateral recruitment через общий H5 invite/join mutation; restart-safe canonical membership; роли `LEADER/OFFICER/RECRUITER/TREASURER/MEMBER` и exact `Clan.setNewLeader`; opt-in contribution в `ClanWarehouse` через canonical inventory transfer; bounded clan chat через существующий conversation/chat safety; `PhantomClanDecision` и lifecycle integration.

Второй clan engine не создавался. `ClanTable`, `Clan`, `Player` и `ClanWarehouse` остаются source of truth. Прямые SQL-записи в `clan_data`, `characters` и warehouse tables отсутствуют.

## Changed files

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `build.xml`
- `java/org/l2jmobius/gameserver/model/clan/ClanInvitationService.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinPledge.java`
- `java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinPledge.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanStore.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomClanGoal027Checkpoint1Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `docs/phantoms/reports/027-checkpoint-1-persistent-clan-organizations.md`

Bounded exception на количество файлов принят как необходимый для одного неделимого CP1 slice: canonical packet mutation, Phantom domain/service/backend/store/Decision, system lifecycle, focused tests и обязательная delivery-документация.
## Architecture decisions

1. `ClanInvitationService` стал transport-neutral владельцем exact H5 invite/join mutation. Оба real packet handler делегируют ему, но REAL-персонаж по-прежнему отвечает только своим manual client packet. Phantom принимает приглашение только на более позднем pulse при matching ACTIVE `clan.join`.
2. `PhantomClanService` ограничен 64 live и 256 terminal operations, одной active operation на profile и не более чем 16 explicit `validSources`. Глобальные clan/player scans отсутствуют.
3. Canonical membership наблюдается по `ClanTable/Clan/Player`, поэтому survives restart. Phantom metadata — только bounded organization intent/evidence; при внешнем изменении canonical clan/leader evidence stale metadata приводит к replan.
4. Leadership передаётся только через exact `Clan.setNewLeader`. Остальные CP1 roles — typed Phantom organization metadata без выдуманной privilege mapping.
5. Warehouse contribution использует durable `PREPARED -> COMPLETED` receipt с inventory/warehouse baselines и exact `Inventory.transferItem(..., ClanWarehouse, ...)`. Restart reconciliation не повторяет уже состоявшийся transfer.
6. Safe canonical withdrawal seam в narrow H5 scope не доказан, поэтому withdrawal возвращает typed `UNSUPPORTED`.
7. Clan chat идёт через существующий `ChatType.CLAN` handler внутри `ChatObservationService.openGeneratedDispatch`; receipt делает повтор idempotent.
8. `PhantomClanDecision` регистрирует build/join/role/contribute candidates и handlers до seal registry; `PhantomSystem` управляет start/stop без отдельного worker/thread.

## DB and migrations

Новых таблиц и migrations нет. Durable organization metadata хранится в существующем `PhantomProfileRepository` component `clan.organization` schema version 1. Автоматические проверки использовали только test DB; рабочая `l2jmobiush5` не изменялась.

## Configs

Новых config keys и feature flags нет. Существующее глобальное включение Phantom World не менялось.

## Commands and test results

- `git status --short --branch` и bounded branch/parent/upstream checks — branch `feature/phantom-world`, required parent подтверждён, пользовательские untracked task packages сохранены.
- Локальный `ant` не найден в `PATH`; использован существующий Ant 1.9.2 из CUDA Eclipse plugin.
- Первый `ant compile` обнаружил одну неверно названную cancellation API call; вызов исправлен на подтверждённый `isCancelled()`.
- Повторный `ant compile` — `BUILD SUCCESSFUL`, 2199 source files, 12 seconds.
- Финальный CP1 aggregate пришлось повторить после трёх fixture-only corrections: build/chat fixtures сначала задавали запрещённый `clan.id` вместо explicit `clan.name`, role fixture использовал uppercase acquisition method вместо lowercase Goal key. Production contracts не ослаблялись.
- `ant phantom-clan-checkpoint1-goal027-test` — `BUILD SUCCESSFUL`, Goal027 aggregate 7/7; creation/restart, recruitment, roles/leadership, treasury и clan-chat/Decision modes прошли.
- Разрешённый dependency aggregate `phantom-profile-persistence-test` — 18/18, test DB cleanup residue zero.
- Разрешённый dependency aggregate `phantom-chat-observation-test` — 2/2.
- Финальный CP1 aggregate — 21 seconds.
- Единственный `ant jar` — `BUILD SUCCESSFUL`, 2199 source files, 14 seconds; `GameServer.jar` и `LoginServer.jar` скопированы в рабочий `dist/libs`.
- `git -c core.whitespace=cr-at-eol diff --cached --check` — clean; настройка применена только к invocation, потому что два изменённых legacy handler blobs хранят CRLF непосредственно в Git.
- Plain `ant verify` не запускался.
- Goal025/Goal026 aggregates, broad Party/Combat/PvP, war/alliance, all-Phantom и stress loops не запускались.

## Performance measurements

Stress/soak намеренно не запускались по TASK. Статически подтверждены bounded limits: 64 live operations, 256 terminal receipts, одна pending invitation на profile, explicit source set не более 16, bounded canonical invitation ledger. Отдельных потоков и per-Phantom scheduled workers не добавлено.

## Deviations

- Первое автоматическое сжатие контекста произошло после завершения implementation и focused aggregate. Согласно TASK новое исследование было остановлено; выполнена только safe delivery.
- `apply_patch` оказался недоступен из-за Windows sandbox ACL. Правки документации и EOL-aware минимизация legacy handler diff выполнены bounded атомарными PowerShell replacements с временными файлами.
- Финальный aggregate был запущен более одного раза исключительно для correction тестовых fixtures; все попытки отражены выше.
- Системный `ant` отсутствовал в `PATH`, поэтому использован уже установленный локальный binary без скачивания зависимостей.

## Limitations and risks

- Warehouse withdrawal не реализован и честно возвращает `UNSUPPORTED` до отдельного доказательства exact canonical privilege/transfer seam.
- Alliances, clan wars/peace, war aggression, alliance chat, sieges, clan halls и global clan discovery относятся к Checkpoint 2 и не начаты.
- REAL recruitment остаётся manual. Phantom auto-accept возможен только при exact matching ACTIVE join goal.
- Live GameServer/client manual gate не запускался: checkpoint покрыт focused deterministic suites, source guards, test DB persistence и полной компиляцией/jar.
- Independent review должен отдельно проверить shared invitation mutation, restart reconciliation contribution receipt и lifecycle ordering.

## Git delivery

- branch: `feature/phantom-world`
- required parent: `ff631e5f71a43da6e771c3541ee59ee15ea916b3`
- commit subject: `feat(phantoms): establish persistent clan organizations`
- commit SHA: этот delivery commit; exact SHA приводится в финальном сообщении
- push result: выполняется после включения отчёта в commit; exact remote SHA приводится в финальном сообщении
- git commands использовались только потому, что TASK и project workflow явно требуют branch/parent/diff/commit/push delivery.

## Next step

Независимое review Goal 027 Checkpoint 1. Checkpoint 2 не начинать до решения gate.
