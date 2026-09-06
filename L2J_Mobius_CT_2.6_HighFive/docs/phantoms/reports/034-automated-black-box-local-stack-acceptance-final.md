# Goal034 closure 2 — final report

## Статус

`BLOCKED` — Goal034 не переведён в `SUCCESS`. Goal035 не начат.

Branch: `feature/phantom-world`
Required parent: `6aa12bd0e8f3ce834972860b823ad9b0d343449d`

## Выполненные исправления

1. `tools/phantoms/verify-task-014a.ps1`: stale production-composition assertion приведён к текущему shared `PhantomCommerceReceiptStore` и `productionGoals`; stale roadmap token Goal025 обновлён после доказанного focused failure.
2. `PhantomBlackBoxLocalStackGoal034.java`: harness принимает оба canonical transport (`jdbc:mysql://` и `jdbc:mariadb://`) и сохраняет guarded source URL без переписывания локального config.
3. Phase-B blocker: `verify-task-016.ps1` больше не считает слово `thread` в комментарии worker infrastructure; запрет остался на `new Thread`, executors, futures и `ThreadPool`.
4. Phase-C blocker 1: сохранён fail-closed на ненулевые `phantom_profiles`, но разрешён безопасный baseline обычных test-only `characters/accounts`; cleanup остаётся exact по run-owned identities.
5. Phase-C blocker 2: GameServer hexid приведён к canonical 16-byte формату из `GameServerRegister` вместо schema-incompatible 32 bytes.

## Проверки

- `ant phantom-static-verify-014a` — PASS.
- `ant phantom-black-box-local-stack-goal034-contract-test` — PASS, 5/5; подтверждены mysql+mariadb и rejection production DB before spawn.
- `ant phantom-db-guard-negative-control` — PASS; driverLoads=0, connectionAttempts=0.
- Fresh `ant verify` run 1 — FAIL на ложном Goal016 comment match.
- Focused `ant phantom-static-verify-016` — PASS.
- Fresh `ant verify` run 2 — PASS, 20:21.
- Standalone `ant jar` — PASS; LoginServer.jar и GameServer.jar созданы.
- Первый real-stack запуск — fails closed на pre-existing ordinary test characters, до spawn.
- Goal034 contract после fix — PASS, 5/5.
- Post-fix `ant verify` run 3 (последний разрешённый) — PASS, 22:46.
- Post-fix standalone `ant jar` — PASS.
- Второй real-stack запуск — schema rejected 64-char hexid до spawn.
- Focused Goal034 contract после canonical hexid fix — PASS, 5/5.
- Final standalone `ant jar` — PASS.
- Финальный real-stack запуск — LoginServer ready; GameServer gen1 запущен, но завершился при загрузке datapack/scripts.

## Финальный blocker

Run: `.phantom-local/blackbox/goal034/20260906-140429-bf608947`.

GameServer sandbox содержит только `config` и несколько root cfg. Реальный процесс ищет часть datapack относительно sandbox-root: отсутствуют `data/mapregion` и `CategoryData.xml`. Затем dynamic compilation `dist/game/data/scripts/handlers/EffectMasterHandler.java` завершается ошибками `package handlers.skill.effects does not exist` / `cannot find symbol`, после чего возникает:

`java.lang.Error: Problems while running EffectMansterHandler`

Это третий независимый Phase-C blocker, поэтому по TASK новые исправления и повторы запрещены.

Минимальное следующее действие в отдельной closure-задаче: исправить sandbox/datapack/script-root composition по реальному GameServer loading contract и добавить focused assertion, что required data/script trees доступны из sandbox process before spawn. Schema/API redesign не нужен.

## Cleanup и safety evidence

- `database=127.0.0.1:3308/l2jmobiush5_phantom_test`.
- `database.production.used=false`; production `l2jmobiush5` не probe/read/cleanup.
- `ant prepare-phantom-test-db` не выполнялся.
- `login.ready=true`; PID LoginServer `37796`, PID GameServer gen1 `34088`.
- Ports: login client `53910`, login-game `53911`, game client `53912`.
- `cleanup.registration=true`, `cleanup.population=0`, `cleanup.forced=false`.
- `orphans.none=true`, `working.integrity=true`.
- Generation 1/2, LIVING 10/5 и native restart не достигнуты; заявлять PASS нельзя.
- Wildcard/global Java kill не использовался.

## Scope и ограничения

Public schema, production runtime API, scheduler/thread ownership, rates, gameplay и Goal035 не менялись. `.phantom-local/Database.test.ini` не переписывался. Pre-existing пользовательские изменения и historical reports не затрагивались.

`apply_patch` был недоступен из-за Windows sandbox `CryptUnprotectData`; точечные исходники изменялись атомарной exact-match заменой с сохранением UTF-8. Bounded Git inspection, exact-path staging, один BLOCKED commit и non-force push явно разрешены пользователем; immutable commit SHA и push result фиксируются в итоговом handoff.
