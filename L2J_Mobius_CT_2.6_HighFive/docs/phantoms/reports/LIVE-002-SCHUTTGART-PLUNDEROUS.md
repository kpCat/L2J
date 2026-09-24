# LIVE-002-SCHUTTGART-PLUNDEROUS — ограниченная проверка цепочки 40–51

Дата: 24.09.2026. Статус: **BLOCKED — `NO_LEVEL40_PLUNDEROUS_DEST_CONNECTOR`**. Ветка `feature/phantom-world`; обязательный tracked baseline `992a732c62481672b97e817cab471a99fe8518fb` совпал с HEAD до работы. Независимые пользовательские dirty/untracked файлы сохранены.

## Диагностика A–F

- A. Для native NPC 30540 опубликован `ANCHOR_TO_GK` `connector.b0c14ba43d9b0dcc750959ab`: `generated.farm.ea72d768f3c8ec3d79c1a530.anchor → spawn.cd48f4a126ad9fb6d9a60ca2`, `VALID_PATH`, 3345. Этот anchor уже служит источником пяти действующих D2 legs. Фактическая доступность от каждого ingress здесь заново не доказана.
- B. NPC 30540 имеет FACTUAL_NORMAL `transition.da2bfda52a944f78cd61ab30` в Schuttgart `87126,-143520,-1288`, fee 4400, `castleId=9`; native источник `data/teleporters/town/30540.xml`.
- C. Bilia, NPC 31964, находится в `87048,-143448,-1293` (`data/spawns/Others/22_13.xml`), около 106 единиц от arrival. Локальный GeoEngine connector не проверялся: ранний отрицательный результат E включил stop condition.
- D. `fact.42780b2b649ae553eda73091` и `transition.bb1ab636c13e8cbfaee92385` подтверждают NORMAL Bilia→Plunderous Plains `111965,-154172,-1528`, fee 1600, destination `dest.60157b30f93203373e08dd6a`; native источник `data/teleporters/town/31964.xml`.
- E. Machine join трёх готовых `DEST_TO_ANCHOR` с `TOPOLOGY_CANDIDATES.tsv` и `WORLD_COVERAGE.tsv` дал только ordinary instance-0 фермы `PlunderousPlains_15` 32–34, `_07` 30–32 и `_06` 30–32. Из семи coverage-групп Plunderous с level-40 overlap только два anchors опубликованы в активной топологии: `generated.farm.4c20d26f8b57611bdafd0d33.anchor` (36–40) и `generated.farm.6cc54f41492a8ebff56a5815.anchor` (38–40). Для них нет готового connector от нужного destination. Два новых направленных DB-free GeoEngine checks дали соответственно `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE`, required buffer 1680 и 1322 при native max 500; valid connector не появился.
- F. Текущий D2 generator отбрасывает `fact["castle_ids"]`, а `PhantomNormalGatekeeperTravel.matchesNative` требует пустой `location.getCastleId()`. Native `TeleportHolder` при выключенном `TELEPORT_WHILE_SIEGE_IN_PROGRESS` проверяет siege каждого destination castle. Из-за E castle support не изменялся.

## Результат и границы

GLOBAL ordinary 40–51 остаётся **0 → 0**: готовые Plunderous connectors ведут только к уровню 30–34, а оба опубликованных level-40 anchors не получили GeoEngine-proven destination edge. Независимый GREEN witness, двух-GK route и byte-identical generation runs не создавались: их предпосылка отсутствует. `SCHUTTGART_PLUNDEROUS_40_51_PROOF.tsv` не публиковался, поскольку он предусмотрен только для GREEN. `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` означает ограничение текущего native pathfinder buffer, не доказанную физическую непроходимость.

Изменены только этот отчёт и checkpoint LIVE-002 в `docs/phantoms/live-world/STATE.md`. Production Java/Python, D1/D2 catalogs, topology, configs, DB schema и другие хроники не менялись. Новых GeoEngine checks: 2/8; world scans/grid/pathchain: 0. Runtime/GameServer/LoginServer: 0; DB connection/mutation: 0; `ant jar`: 0; full `ant verify`: 0. Focused build/test не запускался, так как код не менялся. Проверка GeoEngine выполнена существующим `PhantomTravelGeoProbe` с двумя exact candidate rows; первый запуск завершился ошибкой формата заголовка до проверки кандидатов, исправленный запуск завершился exit 0 и записал оба отрицательных результата. Логи и TSV остались во временном каталоге, в commit не включены.

Goal token/time счётчик средой не предоставлен; проверка заняла один ограниченный проход без новых поисковых harness. Commit SHA и push result сообщаются после commit; SHA нельзя достоверно записать в содержащий его commit. Следующий Goal/Slice не начат.
