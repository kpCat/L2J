# Goal 021 Checkpoint 2 — independent review package

- Статус реализации: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Ветка: `feature/phantom-world`.
- Seed: `21002102`.
- Accepted Checkpoint 1: `0045f60417f4605f46e3058b9a694278283b1456`.
- C2 foundation: `365c014a48c7998eb880352b00503a28b2f27a2c`.
- Loaded-boundary audit: `130a08a90c729dd94c13d782416bc0f1f727e6c7`.
- Anchor audit: `83b22f2338c297151a9b0881fdf566963ee5d571`.
- Near-final foundation:
  `81e4d2a7044f8c1bafc7db6b5d3c66ce4df050aa`.
- Terminal foundation:
  `906b8a043320deb955da02276cf27797e0c5fadd`.
- Terminal foundation subject:
  `fix(phantoms): close manor attribution and quest service recovery`.
- Exact-delta child:
  `0c41280632617f50d4bd133b59b81326e3b6d3f6`.
- Exact-delta child subject:
  `fix(phantoms): enforce exact quest callback item delta`.
- Final cap-boundary child subject:
  `fix(phantoms): close quest collection cap boundary`.

Это второй и последний заранее запланированный checkpoint Goal 021, не 021A/021B.
Только независимый reviewer может заменить pending-статус на `ACCEPT`.

## Что проверить

- Exact ordinary ancestry и subjects всей цепочки C1/C2; cap-boundary completion
  должен быть ровно одним прямым child `0c412806…`, verifier —
  descendant-compatible.
- Active `8→9`/`3→4`: completion/partial и legacy `VERIFYING` сохраняют exact
  receipt, valid historical binding и schema-3 round-trip; partial блокирует source.
- Background real model/transaction `8→9`/`3→4`: exact rows неизменны, replay и
  post-commit reconstruction byte-identical, partial blocked, `after > cap` rollback.
- Cumulative C2 scope считается от accepted C1, final micro scope — отдельно:
  не более 9 файлов, не более 3 production/data/config, 0 новых production/data.
- Immutable loaded `NpcSpawnTerritory` geometry, canonical SpawnData identity,
  topology dataset 2 и точные `35/15/20` territory facts не изменены.
- `MANOR_CROP`: внешний delta создаёт только `VERIFY`, обновляет overall progress и
  binding baseline одной mutation; handler receipt начинается с refreshed baseline;
  pre-dispatch drift не вызывает Harvester; decrease и no-op fail closed без ложного
  manor receipt; restart сохраняет обе истины.
- `QUEST_COLLECTION`: absolute injected epoch deadline переживает restart; deadline,
  rollback, forward jump и legacy values ограничены; уже наблюдаемый item имеет
  приоритет над timeout.
- Callback receipt допускается только для exact current rule/state/cond/vars и
  delta `minimumCount..maximumCount` от persisted `itemCountBeforeKill`; shipped
  Q00102/Q00152 принимают только `+1`. `+2`, decrease и cap violation не создают
  receipt/progress и не завершают Goal.
- Valid callback одной state/Goal mutation сразу завершает Goal либо переходит в
  `TARGET_REQUIRED`; generic `VERIFYING` reread отсутствует. Persisted legacy
  `QUEST_COLLECTION/VERIFYING` проходит тот же exact validator.
- Перед `startAcquisitionSession` item count обязан совпасть с binding baseline и
  быть ниже cap; drift не запускает Combat и остаётся typed/bounded.
- Для NPC `20013`, `20019`, `20016` full service идёт через real planner,
  `activeAdvance`, acquisition-owned existing Combat, real death и delayed
  `OnAttackableKill`; foreign Combat не наследуется.
- `STARTED/state/cond/vars` quest rows не меняются; background остаётся одной atomic
  item/background/Goal/acquisition transaction с exact read-only quest validation.
- Нет direct `setSeeded`, `takeHarvest`, `addItem`, `destroyItem`, `Quest.onKill`,
  crop procurement/reward exchange, нового interpreter или worker.

## Terminal evidence

Проверить один final `phantom-acquisition-checkpoint2-test`, один plain `ant verify`,
standalone `ant jar`, ordinary commit/push и два byte-identical accepted запуска
`verify-task-021c2.ps1` в PowerShell 5.1 и 7.x. Exact commit SHA и verifier output
передаются terminal handoff без self-referential amend отчёта.

Goal 022–027 не начинались.
