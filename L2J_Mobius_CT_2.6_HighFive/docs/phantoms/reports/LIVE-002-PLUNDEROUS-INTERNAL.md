# LIVE-002-PLUNDEROUS-INTERNAL — внутренний Plunderous chain

Дата: 24.09.2026. Статус: **BLOCKED — `NO_PUBLISHED_PLUNDEROUS_INTERNAL_CHAIN`**. Ветка `feature/phantom-world`, обязательный HEAD до работы `c8e090cd64fa40ef185d43af65308caa14d520c2`. Чужие dirty/untracked файлы сохранены.

## Факты и решение

Машинный граф взят только из активных `high-five-generated-01.xml` и `-02.xml`: 20 опубликованных `FARMING` anchors instance 0 с точным authoritative source `data/spawns/Others/PlunderousPlains.xml`, 34 существующих directed `BACKGROUND` edges, 9 weak components. `WORLD_COVERAGE.tsv` и `TOPOLOGY_CANDIDATES.tsv` связаны по coverage key; три start anchors подтверждены существующими `VALID_PATH`/`VALID_DIRECT` destination connectors `2789ec…`, `5c5450…`, `60e086…`. Два exact goals пересекают уровень 40. Начальный directed BFS достигает 9 nodes, goals не достигает.

В пределах nearest fanout 6 и прямой дистанции 7000 выполнены **2/120** новых направленных GeoEngine checks:

| Направление | Результат | Path length | Required/max buffer |
|---|---|---:|---:|
| `PlunderousPlains_06 → _05` (`7e05f4… → 28e320…`) | `VALID_DIRECT` | 2376 | 324/500 |
| `PlunderousPlains_02 → _30` (`e20a44… → 4c20d…`) | `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` | 0 | 732/500 |

После временного добавления только доказанного `_06 → _05` BFS достигает 13 nodes, но не goal. Для `_30` единственный разрешённый входящий кандидат — `_02 → _30` (прямая дистанция 5786); direct заблокирован и требуемый PathFinding buffer превышает actual max 500. Для `_32` единственный входящий кандидат — `_25 → _32` (6071), но `_25` сам недостижим: в пределах fanout/distance у него нет входа из остальных Plunderous anchors. Оптимистический BFS, где все остальные допустимые непроверенные направления считаются проходимыми, достигает 17 nodes, но ни одного goal. Дальнейшие checks не могут дать witness в заданном scope, поэтому probing остановлен до лимита 120. `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` не трактуется как физический `NO_PATH`.

Доказанное `_06 → _05` направление не опубликовано: полного witness до level 40 нет. `PLUNDEROUS_LEVEL40_CHAIN.tsv` и GREEN validator не создавались, потому что их предмет — опубликованная полная цепочка — отсутствует. `PLUNDEROUS_INTERNAL_LEVEL40=REACHABLE` не заявлен. GLOBAL 40–51 по этой задаче не проверялся.

## Изменения и проверки

Добавлены узкий read-only graph/probe accounting helper `tools/phantom-world-data/plunderous_internal.py` и три focused tests `test_plunderous_internal.py`; обновлён только checkpoint LIVE-002 в `STATE.md` и этот отчёт. Источник/топология/Java/config/D2/Schuttgart/Bilia/другие families не менялись. Новых DB-соединений, runtime запусков, `ant jar` и full `ant verify`: 0. Java Geo probe использовал существующие скомпилированные классы и штатный `PhantomGeoValidationRules`: direct-first, PathFinding только при required buffer ≤ actual max 500. Во время DB-free инициализации probe вывел предупреждения о неинициализированном DB pool; probe завершился с кодом 0 и записал обе строки доказательств.

Команды: `python -m unittest -v test_plunderous_internal` — 3/3 OK; `python plunderous_internal.py inventory <work>/phase0-nodes.tsv` — 20 nodes, 9 components; `python plunderous_internal.py inspect <work>/checks.tsv` — cut выше. Два свежих audit runs побайтно совпали, SHA-256 обоих `C5EB1819E444F6AEEAD1B9062B85EF450849D2A2D1CADBDB7956457FE3163620`. Mojibake-маркеры в изменённых файлах проверены: 0; escaped Cyrillic в изменённых файлах проверены отдельно: 0. Локальные probe TSV/logs лежат в `.phantom-local/logs/LIVE-002-PLUNDEROUS-INTERNAL/` и не включаются в commit.

Отклонения и риск: проверено 2 направления вместо полного бюджета 120, потому что математический cut исключил успех остальных разрешённых checks. Отказ `_02 → _30` вызван текущим лимитом PathFinding, а не доказанной физической непроходимостью. Производительность: 2 Geo checks, no grid/world scan; иных измерений не требовалось. Миграций и новых конфигов нет. Commit SHA и push result сообщаются после commit; SHA содержащего себя commit нельзя записать заранее. Следующий Goal/Slice не начат.
