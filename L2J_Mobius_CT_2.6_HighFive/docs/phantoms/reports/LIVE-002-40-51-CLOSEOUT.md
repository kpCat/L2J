# LIVE-002-40-51-CLOSEOUT — точный Plunderous territory route

Дата: 24.09.2026. Статус: **BLOCKED — `BLOCKED_PLUNDEROUS_02_NOT_REACHABLE`**. Ветка `feature/phantom-world`, обязательный tracked baseline `fb96617aa959b722392e59bf63a53b1b4f38100a` совпал с HEAD до работы. Чужие dirty/untracked файлы сохранены.

## Обязательный вход в маршрут

Три принятых `DEST_TO_ANCHOR` connectors от `dest.60157b30f93203373e08dd6a` ведут к опубликованным Plunderous starts `f90942…`, `05aa0d…`, `7e05f4…`. Независимый directed BFS существующего `plunderous_internal.py` **без локального checks ledger** по активным `high-five-generated-01.xml` и `-02.xml` показал: 20 Plunderous farming anchors, 34 directed BACKGROUND edges, 9 достижимых anchors, `generated.farm.e20a44e2413cc5ec51f0b15c.anchor` (`_02`) — `reachable=False`. Команда: `python tools/phantom-world-data/plunderous_internal.py inspect .phantom-local/logs/LIVE-002-40-51-CLOSEOUT/no-ledger.tsv`.

Первичная проверка с историческим `.phantom-local/logs/LIVE-002-PLUNDEROUS-INTERNAL/checks.tsv` ошибочно показала `_02` достижимым. Этот локальный ledger временно учитывает доказанное, но **неопубликованное** `_06 → _05` ребро: с ним 35 edges и 13 достижимых anchors. Ledger не является активной topology. После проверки без ledger вывод исправлен; публикация остановлена. Условие TASK.md «`_02` уже directed-reachable от принятого start set до patch» не выполнено.

## Локальная GeoEngine проверка

До выявления ошибки во входной достижимости были выполнены ровно 3/12 новых directed checks реальным DB-free GeoEngine с `PathFinding=2`, actual `PathFindBuffers` max 500, native door/fence checks и штатным `PhantomGeoValidationRules.route`. Native точки взяты только из `data/spawns/Others/PlunderousPlains.xml`; initial Z — середина native интервала, итоговый Z повторно нормализован и остался внутри него:

| Точка | Native Z | Stable GeoEngine Z |
|---|---:|---:|
| `_02` vertex `119922,-160430` | −1460…−910 | −976 |
| `_30` vertex `123156,-160164` | −1468…−668 | −1192 |

| Направление | Результат | Path length | Segments | Required/max buffer |
|---|---|---:|---:|---:|
| `e20a44…anchor → _02 vertex` | `VALID_DIRECT` | 1482 | 1 | 240/500 |
| `_02 vertex → _30 vertex` | `VALID_PATH` | 3590 | 5 | 468/500 |
| `_30 vertex → 4c20d…anchor` | `VALID_PATH` | 1917 | 4 | 280/500 |

`canMoveToTarget` проверялся первым; PathFinding запускался только при допустимом буфере. Возвращённые сегменты повторно проверены существующими правилами. Локальный proof: `.phantom-local/logs/LIVE-002-40-51-CLOSEOUT/phase1-proof.tsv`. Fallback vertices не проверялись. Эти три hop доказывают локальный фрагмент, но **не** вход от опубликованного destination start set.

## Stop и изменения

По обязательному precondition остановлены публикация ROUTE anchors/edges, Schuttgart/Bilia, destination `castleId=9`, D2 catalog и GLOBAL proof. `PLUNDEROUS_INTERNAL_LEVEL40=GREEN` и `GLOBAL ordinary 40–51 >=1` не заявлены; прежний опубликованный GLOBAL 40–51 остаётся 0. Новый shard и witness TSV не созданы. В production Java/Python, runtime catalog, topology, config и DB изменений нет.

Итоговые изменённые файлы: только этот отчёт и LIVE-002 checkpoint в `docs/phantoms/live-world/STATE.md`. Временный узкий Geo probe и черновик publisher удалены до отчёта. `ant compile-tests` дважды остановился в sandbox на `AccessDeniedException` для локального `HikariCP-7.0.2.jar`; тот же штатный target с разрешённым доступом завершился `BUILD SUCCESSFUL` (2 старых deprecation warnings). Geo probe завершился exit 0. Дальнейшие focused regressions и два generation runs не запускались по stop condition. Runtime/DB connections/mutations=0, `ant jar`=0, full `ant verify`=0, Schuttgart Geo checks=0.

Goal token/time счётчик средой не предоставлен. Commit SHA и push result сообщаются после exact-path commit. Следующий Goal/Slice не начат.
