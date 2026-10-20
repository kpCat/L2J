# Мир и карьера061

Каталог `D:\PhantomWorld\World-Career-061-candidate`, база `l2jmobiush5_phantom_release_061a`. Login2106, Game7841, control9141. Нужны существующие Java25 и локальная MariaDB3308.

1. Запустите `START.cmd`. Он запускает Login и обычный Main/GameServer, затем возвращает управление.
2. `STATUS.cmd` показывает процессы, ownership/incarnation, JAR и свежесть observer. Профили: `playtest-pilot/observer.properties`, поля `populationProfile.<id>.admission/life`.
3. Данные аккаунта находятся только в локальном `OWNER_LOGIN061.txt`. Серверные проверки не требуют входа владельца, видео или GM-команд.
4. Для сохранения используйте `STOP.cmd`, затем `STATUS.cmd`. После штатного завершения PID0 и освобождения портов может остаться `STALE_RECORD`: это audit-запись, её не удаляйте. Следующий START продолжает ту же БД.

Повторный START идемпотентен. STOP проверяет принадлежность процессов, force отсутствует. Защищённые057–060 и накопительная058a самостоятельны. Клиентские и системные настройки не менялись.

## Фактически наблюдавшиеся партнёры

- Последняя whole25: `Thor66`,6/268436825, Elf Fighter5, при passive21 прошёл реальную цепь:2 native боя → сам выбрал Power Strike3:2 → saved goal/STOP/sameDB START → учитель/50SP → возврат →2 новых native боя; между ними купил3+1Soulshot. Предшествующий3:1 также куплен за50SP. Сохранено EXP3678/SP11/skill3:2/Adena6/SS0; дальнейший дефицит средств остаётся видимым ограничением, ресурсов не добавляли.
- Текущий passive18 — `Thorbat`. Рядом реально появился `Steelpo`,22/268436954, Human Mystic6:20октября06:13–06:19UTC World EXP9514→10501/SP426→454; расход8Blessed3947, затем покупка9 у native NPC30004 за360A. Штатный STOP сохранил11Blessed/A160. Его положение менялось в районе Talking Island(-87385,251014)→(-85118,243234); это маршрут, не постоянное место. Сам Thorbat18 в этом эпизоде столкнулся с travel.no_route_within_budget; его исход не объявлен успешным.

- `Kentdobr`,12/268436873, Kamael Soldier: ordinary20октября03:47–04:03UTC сам купил Soulshot, вернулся, убил6 native монстров, заработал174EXP/12SP. После STOP сохранены EXP2253/SP442/43Soulshot/Adena1793. Следующий поход за ShortSword отказал на navigation.route_budget_exceeded; полного обучения в этом эпизоде нет.
- `Thorbat`,18/268436924, Human Mystic: ordinary фармил, покупал Blessed3947, готовил экипировку. Последний путь к магазину1121 отказал на native navigation; условия для PASS не подменялись.
- `Dobriyneo`,21/268436947, Elf Mystic, `Thor66`,6/268436825, Elf Fighter, и `DuskForge`,26/268436984, Elf Mystic: в прежних ordinary061 incarnation реально появлялись и получали награды в эльфийской области около(48940,51917). Это факты наблюдений, а не обещание постоянного присутствия на saved XYZ.
- `T061LifeM`,34/268481283: сохранённый ordinary эпизод farm→trainer→160SP/1011:2→возврат, затем sameDB restart и ещё4 награды. Позднейший1011:3 тоже оплачен; полная дальнейшая цепочка на последнем продукте отдельно не объявлена.
- `T061Career`/`T061CareerD20`,35/36: настоящие незавершённые Q401 states/items сохранены; квест не объявлен завершённым. Исходный evening-календарь действует.

Появление зависит от календаря, catch-up/readiness, locality, capacity, смерти и путешествия. Пассивный AUTONOMOUS viewpoint без Player не управляет персонажами; Synthetic OFF. Точный input записан в `BUILD_INFO.json.independentOrdinaryInput061`. Маленькая World-выборка не полный census.

## Правила продукта

Phantom сохраняет совместимую конечную профессию и автоматически проходит native edges20/40/76. Классовые квесты необязательны. Phantom-only политика: `game/data/phantoms/life/high-five-automatic-professions-v1.xml`; AutoNoblesse default main80. Глобальные ClassMaster/AutoNoblesse и правила людей не менялись.

Soulshot/Blessed Spiritshot выбираются по действию и точному грейду оружия; физическому классу с изученной атакующей магией нужен отдельный магический запас. Native H5 расходует unpacked beast6645/6647 из инвентаря владельца как для pet, так и servitor. По разрешению владельца Phantom-компаньону пополняется конечный запас на60 атак с native проверкой веса/вместимости; предметы действительно расходуются штатным handler. Обычные оружейные соски бесплатно не выдаются.

Единственные RESULT и матрица: `docs/phantoms/reports/PHANTOM-WORLD-CAREER-061.md` и `docs/phantoms/tasks/PHANTOM-WORLD-CAREER-061/ACCEPTANCE061.tsv`. Полный М7 не объявлен; точные ограничения — `KNOWN_ISSUES.md` и финальные статусы/provenance.
