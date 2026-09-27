# Краткая передача

Branch `feature/phantom-world`, initial HEAD `d2ae5704031ca85e417c3cb959de47cd72618821`.

Принято ранее: функциональная сохранённая population 10000 managed/linked/READY, отдельный post-target soak; уникальные имена/аккаунты, без fatal/OOM. Runtime report `77672c071c1c69669dec6527e7f888bd9eb4c2c0`; GameServer.jar SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`. Это ожидаемый baseline для сверки, не доказательство нынешнего локального процесса.

Последний сообщённый scale closeout: LocalPlay STOPPED, target 10000, background.state/catchup 9986, ecology10000. 14 background positions НЕ объявлены завершёнными. LIVE-003D 45-minute performance threshold остаётся NOT MET. Ничего из этого повторно не прогонять ради пилота.

PHANTOM-VISION-AUDIT-001: шесть audit outputs, 146 пожеланий, четыре независимых evidence axes. Есть реальные компоненты, но не доказаны ordinary visible farming producer, spontaneous chat initiator, полноценный REAL-led party/farm, Phantom offline lifecycle. Пилот не должен «успешно пройти» эти gaps, принудительно меняя ботов.

Пользователь устал от 4 дней внутренних задач и ручных логинов/координат. Он предложил отдельного персонажа для серверного управления, хочет оставлять клиент открытым, избежать предполагаемого AFK disconnect и ограничения от частого /loc. Причины этих двух симптомов пока НЕ подтверждены логами. Цель этой задачи — один reusable инструмент и пакет проверок, не ещё один аудит всей системы.

Открытые опасения: пилот не видит реальную картинку и не умеет логиниться после restart; SERVER подтверждение не равно CLIENT_OBSERVED. Живой smoke потребует максимум одну плановую привязку после deployment. Новый логин при реальном сетевом разрыве нельзя отменить обещанием.

Текущие protected budgets: active64/materialized128/scheduled10000/creation2/pulse100ms/boundaries64. Не настраивать. Все runtime реквизиты читать локально, не брать старые default credentials из исторических docs.
