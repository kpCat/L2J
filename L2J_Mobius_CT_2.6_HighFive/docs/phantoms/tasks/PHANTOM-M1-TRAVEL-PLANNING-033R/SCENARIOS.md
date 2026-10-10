# Проверки: от рисунка к настоящему серверу

Для каждого semantic fix: genuine RED на неизменённом base/последнем clean Jar,
GREEN на фактически исполняемом новом Jar. Failure bootstrap/fixture — INVALID.
Reference tests не являются Mobius tests.

| ID | Сценарий | Обязательный результат |
|---|---|---|
| T01 | Прямой путь через препятствие, доказанный обход существует | найден полный обход, разрешён первый шаг от цели |
| T02 | Путь упирается в воду после первого короткого leg | не WALK в воду; альтернативный доказанный corridor или другой mode |
| T03 | U-образный тупик / дверь не по прямой | не жадное уменьшение EuclideanDistance на каждом шаге |
| T04 | Один XY, разные этажи | не склеивать поверхности/не перескочить этаж |
| T05 | Направленный склон | forward и reverse не выводятся друг из друга |
| T06 | Raw native route vs compressed route | first bad segment; нельзя убирать нужный поворот при smoothing |
| T07 | Path budget/cancel | typed SEARCH_LIMIT/CANCELLED, без MOVE/наград/записиXYZ |
| T08 | Ребро стало недоступно после planning | revalidation перед MOVE, ограниченный replan без обхода двери |
| T09 | Смена epoch/goal/instance до async ответа | stale reply не исполняется |
| T10 | Intermediate waypoint достигнут | не final arrival и не fake farm snapshot |
| T11 | Два допустимых плана WALK и return+GK | выбор по полной цене времени/ресурсов, не минимальному числу рёбер |
| T12 | Дорогой GK/нет scroll/запрещён RETURN | legal WALK/replan или typed unavailable; не выдавать ресурсы |
| T13 | Unstuck delayed callback после death/demat/cancel/new epoch | никакой телепортации нового/чужого lifetime |
| T14 | GK actual arrival/fee/final WALK | native fee once, actual landing verified, дальнейший farm |
| T15 | Реальный stuck при допустимой геометрии | обычный recovery конечен; emergencyOFF не скрывает дефект |
| T16 | Emergency candidate за стеной/дверью/на другом этаже/дальний | строго отказ; сухой endpoint сам по себе недостаточен |
| T17 | Historical authoritative state | route planning не меняет EXP/SP/items/vitality/cursor/nativeXYZ |
| T18 | Shared target / pending earned callback | навигационный fix не запрещает cooperative и не отменяет earned hit |

## Native runtime — обязательное доказательство
A. Reproduce route class TASK032 C50/C53/C55 на НОВОЙ owned clone. Original5/19 —
диагностические IDs, не единственные разрешённые actors. Регистрировать полный
context до исходов. Само включение малого buffer/достижение первого leg НЕ PASS.

B. Маршрут с настоящим препятствием обходится stock MOVE без emergency; после
прибытия actual Player получает5последовательных native kill/reward cycles, EXP/SP,
следующие targets, применимый loot. Если исходная цель действительно недоступна,
законный RETURN/GK или атомарный replan к достижимой подходящей цели приемлемы для
оригинального actor; их объявить отдельно от WALK_DETOUR_PASS.

C. Native transport scene: реальные item/fee/таймер/arrival; нет ручного teleToFarm.
Если нужного legal ресурса у natural actor нет, это не подлог и не drop fixture:
native isolated TEST проверяет контракт, runtime mode остаётся NOT_OBSERVED.
WALK GREEN не превращается в полный TRAVEL_STRATEGY GREEN без B/C evidence.

D. Одна полноценная natural CONTINUITY_V2 scene4..8 на frozen кандидате около380с;
все initial participants/epochs в отчёте, два primaries заранее, тот же evaluator.
Она оценивает последствия route change, но не заменяет A/B/C.

E. Actual graceful stop, exact saved native state для затронутых lifetimes,
retained/pending0. Без общего повторного ночного crash/whole-M1 sweep.

Affected suites: native-return0328, H16, recovery021/026 с прежними known failures
отдельно, cooperative/earned, маршруты stock Geo. Один93+11 финальный sweep,
не после каждой правки. Старый4/7 не объявлять PASS и не автоматически чинить fixture.
