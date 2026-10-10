# Scroll, /unstuck и аварийная микрокоррекция

## Реально найденное в base
`dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java`:
command52; время берётся из PlayerConfig.UNSTUCK_INTERVAL; GM branch1000ms.
При default300 используется skill2099; GM skill2100. При другом интервале
создаётся `EscapeFinalizer(player)` через ThreadPool.schedule. Finalizer хранит
Player, снимает casting/skill lock, ставит instance0 и teleToLocation(TOWN).
Само наличие script handler не доказывает безопасность для managed headless lifetime.
В `dist/game/config/Player.ini` base: `UnstuckInterval = 300`, не30.
Настройка локального PLAY могла быть30: читать её как конфигурацию, не предполагать.
PersonalCharacterQoL.ini здесь не управляет временем и исключает Phantom.

## Обязательные правила интеграции
- Scroll — через настоящий item handler/skill, actual charge/reuse/consume; не
  decrement вручную и не выдача свитка для runtime PASS.
- Unstuck — через native правила и корректный delayed owner contract. До включения
  headless ветки доказать death/cancel/disconnect/demat/new epoch. Нельзя держать
  action lease/monitor во время sleep30/300s; нельзя старым callback телепортировать
  другой lifetime. Использовать существующие native work mechanisms, не новый scheduler.
- GM branch нельзя использовать для Phantom. В clone допускается30 только если это
  реальная экспортированная настройка пользователя; не менять Player.ini repository
  и не обещать30 по картинке. При300 selector учитывает300 честно.
- Транспортный этап имеет собственный native cast timeout; его нельзя маскировать
  под WALK и сбрасывать120s journey каждый tick. Полный itinerary хранит absolute budget.
- Jail, observer mode, events, Olympiad, instance/party pins, casting/death, flags,
  gatekeeper fees/доступ и текущий список teleport проверяются native-вызовом повторно.
  В M1 execution ограничить instance0 и отсутствие REAL-party/raid/event pins.
- Перед RETURN подтвердить, что от возможного native town результата существует
  дальнейший допустимый GK/WALK план. Не телепортироваться в town просто для сброса state.
- Нельзя скопировать EscapeFinalizer в Phantom и забыть его native проверки.
  Разрешён узкий managed-only callback adapter в перечисленных файлах; REAL поведение
  должно остаться byte/semantic-compatible в соответствующих тестах.
- Если safe headless возврат требует перестройки shared native-work engine, mode
  явно UNAVAILABLE, другие безопасные ветки продолжить; итог PARTIAL_TRAVEL_MODES.

## Emergency correction (ОПЦИОНАЛЬНО, OFF в основной приёмке)
Необходимость ближайшего TP из пользовательского сообщения признана, но безопасный
destination сам по себе не означает безопасную коррекцию. Через стену/закрытую дверь/
другой этаж нельзя прыгать, даже если target-point сухой и существует в GeoEngine.

Для единственного bounded nudging требуются ВСЕ условия:
1) только текущий Phantom Player/object/epoch, OPEN, без pending earned/store;
2) подтверждённый stuck после обычного replanning, не ожидание combat/cast/REST;
3) тот же instance0 и та же проходимая поверхность, полная прямая swept Geo/door/water
   проверка от ACTUAL позиции до точки; не только связность обходным путём;
4) маленький шаг<=2×existing arrivalRadius (default100world), точка на свежем доказанном
   corridor, не teleToFarm/не следующий waypoint в километре;
5) не бой/PvP/raid/REALparty/trade/event и не in-view REAL; вне наблюдения предпочтительнее
   штатное soft-dematerialization вместо невидимого магического перемещения;
6) максимум1 correction на journey, audit BEFORE/AFTER, без новой epoch или сброса debt;
7) actual teleToLocation под существующей owned операцией, затем повторная проверка
   фактического landing и пути; неверное приземление — failure, не переписывание snapshot.

Нет достаточных native facts → SAFE_CORRECTION_UNAVAILABLE. В этой задаче допустимо
оставить capability неисполняемой (явный флаг), не делать новую платформу ради неё.
Основные WALK/transport route→farm gates должны проходить при emergencyEnabled=false.
