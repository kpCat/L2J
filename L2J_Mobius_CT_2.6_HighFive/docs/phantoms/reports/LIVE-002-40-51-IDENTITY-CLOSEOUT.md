# LIVE-002-40-51-IDENTITY-CLOSEOUT — safety closeout

Дата: 24.09.2026. Статус: **BLOCKED — `BLOCKED_CONNECTOR_PROVENANCE`**. Ветка `feature/phantom-world`; исходный tracked HEAD `50e37e84a7ee269c62371c3e9a0f03a69d7b5755` совпал с обязательным baseline. Сторонние dirty/untracked файлы сохранены.

## Что сделано

В DB-free `PhantomGeoValidationRules` добавлен только connector-specific `connectorRoute`. Exact `DEST_TO_ANCHOR` instance 0 при наличии обоих source identities, GeoEngine geodata и стабильной нормализации factual Z в точный canonical anchor XYZ даёт `VALID_IDENTITY`, length=0, segments=0. Generic `route()` сохранён: same-point остаётся `NO_MOVEMENT`; другой kind, неверный Z, cross-instance и отсутствие source evidence не получают identity. Focused RED был зафиксирован ошибкой компиляции отсутствующего метода; после реализации `ant phantom-geodata-rules-test` завершился `BUILD SUCCESSFUL` и `PHANTOM GEO RULES: PASS`.

## Причина остановки

Первый запуск `ant phantom-geodata-rules-test` остановился на sandbox ACL для `HikariCP-7.0.2.jar` до теста. Разрешённый повтор дошёл до ожидаемого RED, затем GREEN. Это было чтение JAR компилятором; Hikari не инициализировался.

Для fresh Plunderous proof был вызван существующий `PhantomTravelGeoProbe` с ровно четырьмя заданными направлениями. Его локальный TSV содержит совпавшие строки: `_06→_05` `VALID_DIRECT` 2376/1/324; `_02→ROUTE-A` `VALID_DIRECT` 1482/1/240; `ROUTE-A→ROUTE-B` `VALID_PATH` 3590/5/468; `ROUTE-B→_30` `VALID_PATH` 1917/4/280, max buffer 500. **Этот запуск не принят как safe proof:** probe вызвал `DoorData`/`FenceData`, и логи показали инициализацию серверных менеджеров и попытки DB-запросов при неинициализированном pool. Hikari pool не был поднят; подтверждённых DB connections и mutations нет. Но требуемый строгий `runtime/DB=0` не может быть заявлен, поэтому результат помечен `SKIPPED_UNSAFE`.

Статическая проверка показала, что `GeoEngine.canMoveToTarget()` безусловно вызывает `DoorData.getInstance()` и `FenceData.getInstance()`. `DoorData` создаёт серверные door objects; поэтому текущий native probe нельзя считать DB-free только за счёт удаления его явной предзагрузки. Обход этой зависимости потребовал бы отдельного изменения production GeoEngine/door path или нового доказанного test seam; оба действия выходят за scope. Недостоверные строки TSV не опубликованы.

## Scope и gates

- Активная topology, D1 connector TSV, targeted supplement, D2 catalog, Java runtime travel owner, castle conditions, генерация legs и final GLOBAL 40–51 не менялись. Активное `GLOBAL ordinary 40–51` остаётся на прежнем 0, GREEN не заявлен.
- Не выполнены final topology/supplement hashes, catalog before/after, two-GK route, two byte-identical generation runs и independent final graph proof: safe fresh GeoEngine precondition отсутствует.
- `ant compile-tests` выполнен как зависимость Geo rules; `ant phantom-geodata-rules-test` — RED и GREEN. `ant phantom-topology-core-test` не запускался, поскольку topology не изменялась.
- Запрещённые `phantom-normal-gatekeeper-travel-test`, `phantom-normal-gatekeeper-d2-focused-test`, `phantom-topology-production-corpus-test`, generated historical/canonical ingress и другие guarded DB targets: **NOT RUN**.
- `ant jar`=0, full `ant verify`=0, GameServer/LoginServer не запускались. Никаких 52+/Ruins/Cat/Necro работ не начато.
- Схема, миграции, конфиги, fee/replay/transaction/cursor, внешний стек и другие хроники не менялись. Performance/runtime measurements не выполнялись.
- Goal token/time счётчик средой не предоставлен.

Для возобновления нужен отдельно подтверждённый DB-free native GeoEngine movement probe, который сохраняет door/fence semantics без инициализации серверных менеджеров. Затем заново выполнить точные четыре proof и только после этого переходить к публикации. Commit SHA и push result указываются в финальном сообщении после exact-path commit/push.
