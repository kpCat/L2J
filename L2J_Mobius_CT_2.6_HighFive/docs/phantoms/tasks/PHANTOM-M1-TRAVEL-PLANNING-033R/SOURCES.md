# Read-first audit координатора

Required remote HEAD проверен `df71140e659670e2e3d225605f64689a6d32d281`; не новый ответ исполнения сервера.
Реально прочитаны pinned source (ссылки ниже). Локальную Windows geodata/DB текущего
пользователя координатор не запускал. Доступного в контейнере native runtime нет.
Drawing пользователя — концептуальный граф; никаких world-coordinates из него нет.

| Source | Проверенный факт | Git blob |
|---|---|---|
|[PathFinding](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java)|buffer64+2maxDelta; raw bypass only postFilter; compression still present|3b8a7c17fd578745dd1ed2daf2abc01c951066a8|
|[NodeBuffer](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/geoengine/pathfinding/NodeBuffer.java)|A*, priority queue, native NSWE neighbors, MAX_ITERATIONS7000|2955c641a7a68ea77d3bcea7f06c0f64b9871471|
|[L2jNavigationBackend](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java)|calls stock PathFinding, copies XYZ|085b129553e95a64ead98581ce386c882e4f856d|
|[NavigationService](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java)|bounded existing workers + NativeRouteContract.normalize|409bca03fcf42018ff0e2b816915d970bcad84e3|
|[VisibleFarmTravel](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java)|walk357–428 uses straight-ray2000; later native geometry may reject|d72041d5753a9fbc516715d69824165cc2da02c0|
|[NormalGatekeeperTravel](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java)|macro topology+GK graph; search BFS; native destinations/fees validated|see actual source|
|[Unstuck](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java)|52; interval; delayed EscapeFinalizer; GM and normal branches|faa0789ac8af39bc5c6be9dfa1e2960c901b2a67|
|[Player.ini](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/dist/game/config/Player.ini)|shipped UnstuckInterval300|fb8db50edf85e42c358b863890deb15704423fb9|
|[PersonalCharacterQoL.ini](https://github.com/kpCat/L2J/blob/df71140e659670e2e3d225605f64689a6d32d281/L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/PersonalCharacterQoL.ini)|REAL-only allowlist, not unstuck timer|6b8ba45075204f208bac9b811195d655eef2c61c|

Внешний авторский reference по графам/A*: https://www.redblobgames.com/pathfinding/a-star/implementation.html
Использован для терминологии, не для утверждений о L2J runtime.

Не доказано координатором: что конкретный полный5/19 маршрут существует; что raw
stock path всегда безопасен; что 30сек реально настроено в текущем local runtime;
что существующий Unstuck уже безопасен для Phantom. Это обязательные проверки,
а не факты, которые можно переносить в RESULT без собственного исполнения.
