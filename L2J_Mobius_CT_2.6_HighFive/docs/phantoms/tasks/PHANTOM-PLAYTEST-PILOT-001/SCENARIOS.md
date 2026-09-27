# Пакет проверок без ручной проверки каждой фичи

## 1. pilot-contract (быстро, без клиента)

Использовать fake clock и small in-memory fixtures для consent, process/session incarnation, queue limits, replay, cancellation, stop/off, expiry, parse/path controls. Затем адресные native TEST adapters без fake GameClient: в норме и при deny native API. Один Ant target выводит subcases, не 30 отдельных задач.

Тестовая установка состояния перед условием допускается только в TEST и отмечается fixture. Пример: available skill fixture проверяет cast bridge, но не доказывает, что обычный бот сам прокачал навык.

## 2. pilot-smoke (настоящий подключённый TestAdmin)

Runner исполняет одной командой, без диалога с пользователем между шагами:

1. Проверить текущий клиент/session, исходное состояние, безопасную позицию, отсутствие чужой party/store. Снять origin snapshot.
2. Серия bounded STATUS (например 20 чтений за 5 секунд), не через /loc. Убедиться, что игрок остался online и action/chat counters не тронуты.
3. Native SIT → наблюдаемая поза → STAND. Короткий MOVE на доступную точку по геодате → arrival → native MOVE назад. Недоступный маршрут даёт отказ/timeout, не teleport fallback.
4. Test TELEPORT_SELF к разрешённой безопасной точке того же instance и обратно. Дождаться native teleport lifecycle/ответов живого клиента. Никакого force XYZ. Это проверка пилота, не pop-in UX.
5. Найти bounded текущего READY/admitted кандидата через существующие readonly snapshots. При наличии — перенести только пилота к его сохранённой позиции, дождаться обычного runtime. При отсутствии — записать ENVIRONMENT/NO_CANDIDATE и не спавнить бота искусственно.
6. При доступном живом фантоме — одно адресованное сообщение, затем native приглашение. Записать dispatch/invitation identity; отдельно дать ограниченное окно ответа. Нет ответа/принятия → GAMEPLAY_GAP. Не читать source всех conversation/party компонентов заново и не чинить их здесь.
7. CAST/ATTACK: positive native cases обязательно в guarded TEST; в connected PLAY только если уже есть законные навыки и явно допустимая безопасная цель. Иначе `NOT_APPLICABLE` с причиной, без выдачи навыков/ресурсов и без похода на raid.
8. Простой пилота в безопасной зоне около 60–120 секунд, без поддельных keepalive и без /loc spam. Снять connectivity/safety. Такой замер доказывает только наблюдавшийся интервал; длинный idle threshold проверен fake clock/кодом либо остаётся UNCONFIRMED.
9. Runner завершает сегмент, сохраняет report. В той же session выполнить второй короткий сегмент STATUS → SIT/STAND → STATUS, без relogin/arm. Это обязательное доказательство reusable управления.
10. Освободить execution lease, сделать безопасный cleanup origin/позы/сценарной party по фактическому состоянию. Оставить RUNNING клиент и сервер при отсутствии safety blocker. Отчёт фиксирует отсутствие выполняющихся действий и срок consent lease.

Для manual preemption/expiry не заставлять владельца прерывать каждый run: основное доказательство deterministic/TEST. Ручной аварийный `.playtest off` описан в USAGE и всегда доступен.

## 3. Что не принимать за успех

- `invitation sent` ≠ `bot accepted`.
- `chat accepted` ≠ `meaningful bot reply`.
- `skill request issued` ≠ `heal/buff completed`.
- `packet queued` ≠ `client rendered it`.
- `teleport succeeded` ≠ `navigation works`.
- `manually supplied Goal ran` ≠ `autonomous bot decided`.
- TEST actor с MemberKind.REAL ≠ настоящий пользовательский сетевой клиент.

Нужны две независимые колонки: исправность пилота и состояние проверяемого gameplay. Не поднимать прежнюю VISION_MATRIX до LIVE_PROVEN на основании только пилота.

## 4. Будущие игровые пакеты

Следующие задачи будут пользоваться тем же pilot runner: appearance/follow/party/farm/support, затем progression/equipment/economy и группы/instances. Пакеты дополняются новыми штатными action adapters только когда нужен конкретный сценарий. Не создавать сейчас инструменты для всех 146 пожеланий.

Человеку остаётся одна объединённая UX-проверка после существенного набора изменений. Это целевой рабочий процесс, а не обещание полностью автоматизировать зрительное восприятие или избавиться от логинов после restart.
