# Goal 026 Checkpoint 3 — raid force composition и bounded outbound recruitment

## Status

`SUCCESS — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Goal 026 overall: `IN_PROGRESS`.

`occurred_context_compaction: yes`

## Summary

Реализован один coherent production block для raid force composition и bounded outbound recruitment. Планирование принимает exact current Party/CommandChannel leader, exact `RAID`/`EPIC` contentId и не более 16 уже известных caller-supplied exact candidate Party leaders. Дефициты всегда строятся из fresh Checkpoint 1 assessment; hard capability требует exact key/rank и одновременно `intrinsic && learned && readyNow`.

Candidate facts берутся только через canonical Goal 017 current-force seam. Допускается только exact standalone Party leader; кандидат рассматривается целой Party и должен помещаться в `recommendedMaxParty` и force bounds. Выбор детерминирован: hard deficit reduction, затем useful member deficit, меньший excess и stable key.

`recruitNext` заново строит fresh plan и отправляет не более одного invitation через принятый Checkpoint 2 `inviteCommandChannel`. При drift/reject второй кандидат в том же вызове не пробуется. Invitation не считается membership: production Checkpoint 3 не вызывает `respondCommandChannel`; присоединение подтверждается только отдельным exact target-side `ACCEPT` и новым Checkpoint 1 assessment.

## Read-first pass

Прочитаны обязательные `Agents.md`, master plan, workflow contract, task package standard, весь пакет Checkpoint 3, отчёты Checkpoint 1/026A/Checkpoint 2 и релевантные Goal 017/Checkpoint 1/Checkpoint 2 raid/party classes и focused fixtures. Parent `../AGENTS.md` и корневой `README.md` в обязательном read budget не найдены. Локальные аналоги: Goal 017 canonical current-force facts, Checkpoint 2 lifecycle outcomes и Goal 023 deterministic hash/sort. Переиспользованы существующие records/seams, naming и stateless service pattern; новых библиотек, DB, конфигов, scheduler/worker или архитектурного слоя нет.

## Changed files

- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidModel.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidReadinessService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidRecruitmentService.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidRecruitmentSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/026-checkpoint-2-command-channel-lifecycle.md`
- `docs/phantoms/reports/026-checkpoint-3-raid-recruitment.md`
- task package `docs/phantoms/tasks/026-checkpoint-3-raid-recruitment/`

Файлов больше обычного soft-limit 8–10, но это bounded exception для одной artifact family: production contract/service, один focused suite/route, Ant routes, обязательный task package и truth/report closure. Независимые подсистемы не затрагивались.

## Architecture decisions

- Checkpoint 1 остаётся единственным владельцем raid readiness truth; Checkpoint 3 переиспользует тот же `satisfies` helper без изменения его логики.
- Goal 017 остаётся единственным источником canonical Party/CommandChannel facts; scans World/profile отсутствуют.
- Checkpoint 2 остаётся единственным outbound membership seam; Checkpoint 3 вызывает только `inviteCommandChannel` и не принимает приглашение за target.
- Candidate Party не дробится; bounds проверяются до ранжирования.
- Evidence hash и порядок результатов не зависят от caller order.

## DB, migrations, configs

Отсутствуют. Runtime persistence, raid saga и новые feature/config keys не добавлялись.

## Commands and results

- `ant compile` — JDK завершился native `EXCEPTION_ACCESS_VIOLATION` до диагностик javac; это не компиляционная ошибка исходников. Сгенерированный crash log удалён из рабочего scope после фиксации результата.
- `ant phantom-raid-recruitment-test` — первая подготовительная попытка выявила неполный synthetic RIFT fixture; следующая — регрессию регистра SHA-256. Обе причины исправлены в focused fixture/model validation.
- `ant phantom-raid-recruitment-test` — `BUILD SUCCESSFUL`, 9/9, seed `26002631`.
- `ant phantom-raid-current-capability-readiness-test` — `BUILD SUCCESSFUL`, 5/5, seed `26002611`; запущен отдельно, потому что visibility readiness helper изменена для точного переиспользования.
- `ant phantom-raid-recruitment-checkpoint3-test` — final aggregate `BUILD SUCCESSFUL`, Checkpoint 3 9/9 + affected Checkpoint 1 readiness 5/5.
- `ant jar` — `BUILD SUCCESSFUL`; выполнен ровно один раз.

Не запускались запрещённые plain `verify`, Goal 025, Checkpoint 1 aggregate, Checkpoint 2 15-test aggregate, broad Goal 017/all-Phantom и stress routes. Documentation-only исправление отчёта Checkpoint 2 не было причиной повторного запуска его product gates.

- git diff --cached --check — PASS; staged allowlist содержит только High Five, другие хроники не затронуты.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Performance and bounds

Планировщик ограничен 16 кандидатами; выполняет один fresh readiness assessment, bounded current-force reads и максимум один outbound invite на вызов. Не добавлены потоки, scheduler, worker, DB I/O, World/profile scans, navigation/pathfinding, chat или hot-path logging.

## Deviations and limitations

- Системный `apply_patch` был заблокирован Windows sandbox ACL; использован точечный project-local fallback с exact replacements/patches и строгой UTF-8 проверкой.
- Первый automatic context compaction произошёл после завершения coherent production block и обязательных focused gates. Согласно task STOP rule новое исследование прекращено; завершены только документация, проверки scope/encoding, commit/push и handoff.
- Checkpoint 3 не реализует auto-accept, discovery, gathering/navigation, entry/combat/retreat/loot, raid DB saga или scheduling. Фактическое membership появляется только после отдельного Checkpoint 2 target response и нового Checkpoint 1 assessment.

## Documentation closure

Mojibake в отчёте Checkpoint 2 исправлен. Master plan и roadmap фиксируют: Checkpoint 1 + 026A `ACCEPT`; Checkpoint 2 `ACCEPT` на `bbd29495a19a322c0629509c85c31fe508ae8d07`; Checkpoint 3 `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 026 overall `IN_PROGRESS`; Checkpoint 4+ `NOT_STARTED`.

## Git

- branch: `feature/phantom-world`
- required parent: `bbd29495a19a322c0629509c85c31fe508ae8d07`
- commit subject: `feat(phantoms): add raid recruitment planning`
- commit SHA: тот же commit; exact SHA указан во внешнем final handoff
- push: ordinary push в `origin feature/phantom-world`; результат указан во внешнем final handoff

## Risks and next step

Риск ограничен контрактом stale external candidate state между plan и invite: при reject/drift метод намеренно не выполняет fallback в том же вызове. Следующий шаг — независимый review Checkpoint 3; Checkpoint 4 не начинать до принятия gate.

`GOAL_026_CHECKPOINT_3_RAID_RECRUITMENT_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
