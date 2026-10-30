# HANDOFF — текущая точка и обязательные решения

## Проверенный источник истины

- GitHub: `https://github.com/kpCat/L2J`, `feature/phantom-world`; checked commit: `58c76fc1e7f02bfe8e019c72b521284f56946c6c` (30.10.2026). Актуализировать перед началом и перед публикацией; нельзя предполагать локальный WIP удалённо.
- Последний коммит D `58c76fc1e7f0` после `0795d510467e...`; ровно 6 tracked files: `PhantomPopulation102Native.java`, `PhantomTask102Lane.java`, `PhantomWorldCohort108R3Checks.java`, новый `Task108R3DRun.ps1`, `Test-Task108R3Process.ps1`, новый report D. **Production Java/config changes0**.
- Исходный report `docs/phantoms/reports/PHANTOM-WORLD-RESTART-CLOSURE-108R3D.md`. Предыдущие reports C/R3/R2 только при фактической необходимости.
- Отчёт D публикует A `World16/16 stable16≈36.7s`, 2 observers, BUY63/SELL162/MFG25, EARNED1, exit0 and accepted clean; B `World5/16 stable4`, 1 observer, physical5, desired3252/eligible0/admitted0, `SP312→2` and `negativeRows={sp=1}`, natural JVM exit0, but **no B clean**. Private proofs НЕ представлены самим публичным GitHub; требовать replay/checksums локально.

## Сверено в живом source

1. `PhantomPopulation102Native.java` около `addObservers` (строки ≈677–706): `regions.add(row.state().homeMapRegionId())` до `operatorLocalityTarget`; known harness bug. `onlineHumanPoints` проверяет хотя бы одного, **не** двух.
2. `PhantomSystem.operatorLocalityTarget(profileId)` получает registered `TopologyProfileSnapshot` через `PhantomTopologyService.findProfile`, без гарантии готовности target. `operatorSetupLocalityTarget` дополнительно проверяет durable READY/calendar/native absence.
3. `PhantomWorldCohort108R3Checks.capture` пишет `cohort108R3-4-start/plateau/final.private.tsv`: строки SUMMARY/STATUS/PREPARATION/HUMANS/OBSERVER/PROFILE/WORLD, выбранные до 64 профилей. **Начинать с существующих приватных D файлов**, затем при необходимости дополнять.
4. `PhantomPopulationManager.admissionSnapshot()` возвращает eligible/admitted=0, когда `ecologyCurrent(_admissionEcology)` false, с `pendingRebalance`; `SchedulingSnapshot` экологии имеет revision/epoch/permitted. Не делать вывод «все 3252 не eligible» по одному нулю.
5. `DwarvenFighter.xml`: `classId53`, skill141/1 `levelUpSp310`. `PhantomClassSkillLearningTransaction`: SQL `FOR UPDATE` SP/skill + commit, после commit runtime reconciliation и `OperationResult(SUCCESS,spBefore,spAfter,skillLevel)`. `L2jPhantomLearningJourney` получает результат `learnClassSkill`, `PhantomProgressionService` агрегирует метрики. **На B конкретный SUCCESS receipt не сохранён.** Возможность расхождения после SQL commit/до runtime reconciliation учитывать.
6. `tools/phantom-runtime/Task105Census.java` делает 10-slice snapshots, `characters`/items/components/recipebook, но НЕ включает `character_skills`; `PhantomWorldCohort108R3Checks.CharacterFrame` принимает legacy slices. Нельзя бесконтрольно менять старые sealed contracts; отдельный hashed skill sidecar предпочтительнее.
7. D wrapper `Task108R3DRun.ps1` поддерживает A/AcceptA/B/AcceptB и held process handle, сохраняет `negativeRows` hard fail. Не подменять старые markers. `Test-Task108R3Process.ps1 -WrapperPath` позволяет проверять новый wrapper без изменения старого.

## Важные продуктовые инварианты

5000 persisted profile identities, 128+ target (16 только частный тестовый cap), 250 offline shops, реальный native `Player`, paid learning/travel/craft, earned-only resources, coexistence с OwnerConfig/Premium/Alt+B/QoL/GeoData/Semantic v4. Запрет second game engine, forced materialization ради теста, free SP/Adena/EXP/item, silent reset и фиктивного World/market PASS. `TASK109=NOT_STARTED`, номер не занимать.

## Разделение фактов и гипотез

- CONFIRMED CODE: маркер региона до lookup, стоимость 310, отсутствие skill slice, нулевой admission snapshot при stale ecology.
- REPORTED NATIVE: A succeeded, B failure, 1 observer, SP−310, 250 shops, clean A, exit0 обеих JVM; private receipts требуют локальной проверки.
- NOT PROVEN: именно observer дефект вызвал World5, именно learning SUCCESS вызвал SP−310, политика ecology запрещала всех профилей, причинность market/World для B.

Работа — **финальный closure**, а не проектирование следующего suffix. Если чего-то нельзя доказать одним bounded прогоном, запиши один точный остаточный blocker и STOP.
