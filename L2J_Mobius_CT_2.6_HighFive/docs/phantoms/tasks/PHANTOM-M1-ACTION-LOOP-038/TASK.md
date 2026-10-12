# Объём TASK038

Продукт: materialized Player не обязан уже стоять на идеальной точке фарма, чтобы
получить разумное следующее действие. За допустимой целью может существовать обход.
Он должен либо дойти до пригодной позиции и продолжить gameplay, либо отказаться
от конкретной неудачной попытки и выбрать другой допустимый вариант.

Результат НЕ ограничивается диагностикой; feature и связанные causal fixes разрешены.
Не выдавать PATH_FOUND, isMoving, AutoPlay=true или expired timer за полезный результат.
И не требовать, чтобы каждый каст нанёс урон: цель может законно умереть до impact.

Обязательное:
- Общая opportunity/approach модель для farm-start и running-session recovery.
- Fresh target incarnation + actor epoch/goal/request identity перед каждым новым root.
- Не путать NO_DIRECT_PATH, NO_ROUTE, SEARCH_LIMIT, TEMPORARILY_DISABLED и чужой instance.
- Только один исполнитель движения/выбора на actor; stock combat после readiness.
- Exact stale reply rejection и cleanup pending request/Session без потери earned.
- Сценарии переноса на другую геометрию и другое умение без actor/map-specific if.

Не входит: M2 companion/party UI, M3 chat, M4 PvP tactics, M5 raid, M6 instance entry,
M7 profession/progression, M8 gear/M9 economy, перенастройка радиусов/капacity.
Контракты расширения и существующие реализации отражены в MILESTONES.md.
