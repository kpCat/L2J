# LIVE-002-40-51-FINAL-CLOSEOUT — точный closeout 40–51

Дата: 24.09.2026. Статус: **BLOCKED — `SCHUTTGART_LOCAL_GEO_BLOCKED`**. Ветка `feature/phantom-world`; обязательный tracked baseline `e28a6c5505374842bf72080dea1d16f06dfc8ffd` совпал с HEAD до работы. Сторонние dirty/untracked файлы не включены в изменения задачи.

## Fresh Plunderous gate

Штатный DB-free `PhantomTravelGeoProbe` проверил ровно четыре заранее заданных направления с текущими 203 GeoEngine regions, `PathFinding=2`, max buffer 500. Все результаты совпали с предыдущими proofs:

| Направление | Статус | Длина | Сегменты | Буфер |
|---|---|---:|---:|---:|
| `_06 7e05f4… → _05 28e320…` | `VALID_DIRECT` | 2376 | 1 | 324/500 |
| `_02 e20a44… → native _02 vertex` | `VALID_DIRECT` | 1482 | 1 | 240/500 |
| native `_02` vertex → native `_30` vertex | `VALID_PATH` | 3590 | 5 | 468/500 |
| native `_30` vertex → `_30 4c20d…` | `VALID_PATH` | 1917 | 4 | 280/500 |

Native vertices `119922,-160430` и `123156,-160164` повторно нормализованы настоящим GeoEngine в Z=−976 и Z=−1192. Обе высоты стабильны и лежат соответственно в native интервалах `−1460..−910` и `−1468..−668`; instance 0. Источник: `data/spawns/Others/PlunderousPlains.xml`.

Независимый directed BFS по активным `high-five-generated-01.xml`/`-02.xml` без локального ledger: 9 достижимых Plunderous anchors, `_02` недостижим. Временное добавление **только** свежедоказанного `_06→_05` дало 13 anchors и достижимый `_02`. Временный deterministic shard с двумя ROUTE anchors и четырьмя направленными edges позволил статически получить путь от принятого `dest.60157…` через `connector.60e086…` и `_06→_05→e906a4…→_02→ROUTE-A→ROUTE-B` к level-40 `_30 4c20d…`. Этот shard **не опубликован**: он удалён после последующего обязательного Schuttgart blocker, а production loader не был успешно проверен. Активный GLOBAL ordinary 40–51 остаётся 0.

## Schuttgart local gate и причина остановки

Существующего активного `ROUTE`/`CITY_CENTER` anchor в 512 единицах от native Schuttgart arrival `87126,-143520,-1288` нет. GeoEngine повторно подтвердил эту точку: Z=−1288, стабильная нормализация. Поэтому предписанный новый ROUTE anchor должен иметь **те же** координаты, что и native destination.

Два точных локальных probe направления:

| Направление | Результат | Длина | Буфер |
|---|---|---:|---:|
| Schuttgart destination → ROUTE anchor в той же native arrival point | `NO_MOVEMENT` | 0 | 64/500 |
| ROUTE anchor → Bilia NPC31964 `87048,-143448,-1293` | `VALID_DIRECT` | 107 | 74/500 |

`PhantomGeoValidationRules.route` возвращает `NO_MOVEMENT` для совпадающих XY до проверки direct path. Принятый D1/D2 connector contract допускает только `VALID_DIRECT`/`VALID_PATH` с положительной длиной. Значит, обязательный `DEST_TO_ANCHOR` при заданной точной позиции нельзя опубликовать как GeoEngine-proven connector. По TASK.md остановка выполнена до D2: targeted supplement, destination castleIds, новые NORMAL legs, two-GK route и GLOBAL acceptance не изменялись. Отступление на другую точку или изменение contract без отдельного решения не выполнено.

## Проверки, ограничения и состояние

- Fresh GeoEngine directional checks: 6 всего (4 Plunderous + 2 Schuttgart); дополнительно два запуска точной Z-нормализации, всего 5 point checks. World/grid searches: 0. Локальные candidate/proof/log TSV: `.phantom-local/logs/LIVE-002-40-51-FINAL-CLOSEOUT/`, вне commit.
- `ant phantom-topology-production-corpus-test` первоначально остановился на sandbox `AccessDeniedException` при чтении `HikariCP-7.0.2.jar`. Повтор с разрешённым доступом скомпилировал production и tests, но корпусный target начал инициализацию Hikari/серверных данных. Он был прерван до завершения; этот запуск **не считается пройденным**. Отдельная попытка production loader также запустила spawn/runtime initialization с повторяющимися ошибками и была остановлена. Временные активный shard и publisher удалены; рабочая ветка не содержит этих непроверенных production artifacts.
- Ограничение `DB=0` **не подтверждено**: корпусный target начал Hikari initialization и мог выполнить read-only обращения. Изменение DB task-кодом не выполнялось; DB mutations не подтверждались. Server runtime отдельно не запускался. `ant jar`=0; full `ant verify`=0.
- Исторический локальный inventory TSV был один раз перегенерирован штатным helper; он не включён в commit. Сторонние отслеживаемые правки сохранены.
- Изменены только этот отчёт и текущий checkpoint LIVE-002 в `docs/phantoms/live-world/STATE.md`. Production Java/Python, D1 TSV, runtime catalog, topology, другие хроники, конфиги и migrations не менялись. Новых tests нет из-за обязательной остановки на local Geo gate.
- Два byte-identical final generation runs, strict loader test, two-GK BFS и GLOBAL 40–51 proof не выполнялись: их предпосылка отсутствует. Goal token/time счётчик средой не предоставлен. Commit SHA и push result сообщаются после exact-path commit; SHA содержащего себя commit нельзя записать заранее.

Следующий Goal/Slice не начат. Для возобновления нужен отдельный контракт на zero-distance destination connector либо разрешённая source-derived точка ROUTE, отличная от native arrival; затем этот же closeout можно повторить с сохранёнными exact Geo proofs.
