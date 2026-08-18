# Goal 026 Checkpoint 5 — raid encounter orchestration

## Status

`PARTIAL`

CP1–CP4 и correctives 026A/026B/026C независимо приняты на baseline `f6402b512d5b22982e44f256506d7383a6b3d7c1`.

Выполнен один безопасный coherent implementation block: additive exact RAID/GRAND_BOSS path в существующем Combat owner. После первого автоматического сжатия контекста TASK потребовал прекратить новое discovery/delivery, поэтому остальные CP5 blocks намеренно не начинались. Goal 026 overall остаётся `IN_PROGRESS`; Goal 027 — `NOT_STARTED`.

## Summary

Добавлен отдельный `PhantomRaidCombatRequest` с exact runtime `objectId`, `npcId`, RAID/EPIC kind, ожидаемым GameKnowledge NPC kind, attempt authority hash, maximum actor level и существующим plan cancellation token. `PhantomCombatService` использует прежние session ownership, capability/loadout, shots, bounded shared worker, loot observation и cleanup.

L2J adapter выдаёт raid snapshot только для canonical `RaidBoss`/`GrandBoss`, подтверждённого GameKnowledge, и перед каждой attack/cast повторно проверяет exact request, instance, region, distance, peace, targetable/attackable/invulnerable и level ceiling. Ordinary `TargetSnapshot.validFor` и обычные `attack`/`cast` не изменялись.

Combat-level `VICTORY` возникает только при наблюдаемой `dead/alikeDead` exact target. `null`/identity/kind/instance/force drift завершаются как `TARGET_LOST`. Общая loot phase доступна только когда caller явно передаёт `lootAfterVictory=true`; создание или назначение drop не добавлено.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomRaidCombatRequest.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSession.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java`

Focused verification:

- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidCombatGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`

Canonical status documentation:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- этот отчёт.

Task package:

- `docs/phantoms/tasks/026-checkpoint-5-raid-encounter-orchestration/CODEX_LAUNCHER.txt`
- `CONTEXT.md`
- `PACKAGE_MANIFEST.json`
- `PRIOR_INDEPENDENT_REVIEW.md`
- `TASK.md`
- `TEST_CASES.md`

Несвязанные user-owned untracked `docs/phantoms/tasks/026-checkpoint-2-command-channel-lifecycle/CODEX_LAUNCHER.txt` и `PACKAGE_MANIFEST.json` не изменялись и не включаются в commit.

## Architecture decisions

- Raid combat является additive branch общего `PhantomCombatService`; второй combat engine и отдельный worker не создавались.
- Default methods в `PhantomCombatActorLease` сохраняют совместимость unrelated adapters и fail closed.
- Ordinary Monster path остаётся изолирован: он по-прежнему требует `normalMonster && knowledgeMonster`.
- Exact raid snapshot отделён от ordinary snapshot и требует согласованного `ContentKind`/`NpcKind` плюс canonical runtime class.
- Authority hash входит в idempotent operation identity; другой request не может присоединиться к живой session того же profile.
- Level ceiling проверяется при admission, на каждом pulse и перед каждым L2J attack/cast.
- Общий loot path наблюдает только существующие world items; custom drop/reward logic отсутствует.

## Required CP5 areas

- raid-combat safety: выполнено в пределах coherent блока;
- ENTRY_GATED CP1/CP3/CP4: не начато после compaction-stop;
- Queen Ant executable profile и canonical authority confirmation: не начато; generic exact combat request способен принять `29001` и level ceiling, но profile/wiring отсутствуют;
- Zaken83 adapter/entry/candles/reveal: не начато;
- attempt/support/retreat/victory orchestration: не начато; combat death является только combat-level evidence и не заменяет authority/script confirmation;
- stable collector selection: не начато; существующий opt-in loot phase сохранён;
- PhantomRaidDecision end-to-end: не начато;
- production raid-attempt lifecycle: не начато.

## DB, migrations and configs

DB, migrations, schema, production DB и config keys не менялись и не использовались.

## Commands and test results

Git/read-only scope commands:

- `git status --short --branch`
- `git rev-parse --show-toplevel`
- `git rev-parse HEAD`
- `git branch --show-current`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- bounded `git status --short`, `git diff`, `git diff --stat`, `git diff --check`

Verification:

- `ant phantom-raid-encounter-goal026cp5-test`: не стартовал, потому что `ant` отсутствовал в `PATH`; production/test compilation не запускалась этой командой.
- `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-raid-encounter-goal026cp5-test`: `BUILD SUCCESSFUL`; 2185 production sources и 94 test sources скомпилированы; 4/4 tests passed, seed `26002651`.
- единственный `.phantom-local/apache-ant-1.10.17/bin/ant.bat jar`: `BUILD SUCCESSFUL`; собраны `LoginServer.jar`, `GameServer.jar`, `DatabaseInstaller.jar`, server jars скопированы в рабочий `dist/libs`.
- strict UTF-8: 18/18 exact allowlist files decoded successfully.
- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Не запускались plain `ant verify`, Goal025, broad Goal017, all-Combat, all-Phantom, economy/social/PvP/Rift, stress loops, CP1/CP3/CP4 gates для не реализованных blocks.

## Performance and lifecycle

Новый thread, scheduler, DB query, global scan и queue не добавлены. Raid session использует существующий bounded shared Combat worker, session capacity, pulse budget, cleanup и cancellation. Отдельный performance smoke запрещён verification budget и не запускался.

## Deviations and diagnostics

Встроенный `apply_patch` периодически не мог читать existing workspace files из-за Windows sandbox ACL (`apply deny-read ACLs`). Новые файлы добавлялись через `apply_patch`; existing files изменялись bounded PowerShell exact replacements с проверкой единственного ожидаемого occurrence. Полный diff и compilation проверены после изменений.

`occurred_context_compaction: yes`

## Limitations and risks

Этот commit не является полным Checkpoint 5 и не получает target verdict `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Без ENTRY_GATED, Queen/Zaken profiles, AttemptService, support/retreat/authority confirmation и Decision E2E additive Combat API не вызывается production raid orchestration. Combat death нельзя трактовать как Goal026 canonical victory без будущего authority/script confirmation.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `f6402b512d5b22982e44f256506d7383a6b3d7c1`
- commit subject: `feat(phantoms): complete raid encounter orchestration`
- commit SHA: commit, содержащий этот отчёт; exact SHA фиксируется в final handoff
- push target: `origin feature/phantom-world`
- push result / remote HEAD: фиксируется в final handoff после ordinary push
- amend/rebase/squash/reset/force-push не выполнялись

## Next step

В новом непрерывном implementation context завершить оставшиеся CP5 coherent blocks из того же TASK: ENTRY_GATED, Queen Ant, Zaken83 script adapter, bounded AttemptService, per-Party support/offense ownership, retreat, authority-confirmed victory/native settlement и Decision E2E. После полного focused aggregate возможен verdict `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
