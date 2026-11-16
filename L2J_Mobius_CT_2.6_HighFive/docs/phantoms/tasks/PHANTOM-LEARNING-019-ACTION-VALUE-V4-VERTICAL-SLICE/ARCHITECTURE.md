# V4: проект сквозной системы минимального риска

## Что сохраняется

Программистская stock-тактика, GameServer, lawful action resolver, `TacticalPolicyV3`, `TacticalReceiptV3`, native cast/dispatch и `TacticalObservationV3` остаются истиной и executors. Нейросеть/оценщик **не создаёт** навыки и цели; только сортирует уже законные предложения, если есть доказанные контекст и confidence. Во всех иных случаях stock.

## Минимальные логические компоненты

1. **Native action importer**: отдаёт проверенный `TacticalStoreV3.Row` / `TacticalReceiptV3` из уже существующих native events и episode binding; read-only, строго bounded, без мутации SQL. Исторические row SHA и provenance проверяются. Если episode boundary неизвестен, не выдавать `COMPLETED`, но подтверждённый cast может стать *локальным* sample, если его собственная authority полноценна.
2. **`ActionValueV4`**: маленькая ограниченная числовая модель (`<=256` коэффициентов, линейная оценка, не новый transformer/ML framework). Features образуются стабильным versioned encoder на основании `TacticalObservationV3` + `LegalActionV3`, c явной маской known/unknown и контекстными взаимодействиями (не только state-only bias, который не способен менять порядок действий). Без жёсткого списка ID профессий или boss-specific handcoding. DAMAGE начальная family; интерфейс готов принять HEAL позже, без заявления что оценка HEAL готова.
3. **`ActionFeedbackV4`**: ровно один наблюдавшийся исход для исполненного action, label из verified `TacticalReceiptV3.reward` с расходом HP/MP/времени. Нет counterfactual «второй навык нанёс бы N». Учёт того, что выбор stock смещает выборку; не объявлять expected best по одиночному каста.
4. **Trainer/registry V4**: bounded update (SGD/Huber или эквивалентная стандартная числовая процедура с чёткими лимитами), отдельная immutable candidate snapshot, SHA, version, данные об exact eventKeys/episode IDs, no-double-credit, атомарная запись temp→rename и cold replay. Не трогать старые Adam/модель и V3 таблицы.
5. **TEST-only Policy hook**: явный V4 ON/SHADOW/OFF, не меняет API native dispatcher и stock rescue. Право выбора — у существующего владельца Combat/Party, модель лишь score внутри уже validated noncritical candidates. Каждый переход фиксирует версию модели, offered actions, stock, chosen, scores, reason; native execution остаётся обычным.

## Предлагаемый порядок реализации

- Первая работающая цепочка: read-only native receipt016 → bounded V4 sample → small gradient update → cold model readback → fixed-lawful-action rescoring.
- Вторая: один короткий native TEST execution, где ranker вызван в `TacticalPolicyV3` и есть настоящий paid effect или законный отказ, с раздельным результатом теста. Для FULL требуется именно успешное исполнение с проверяемым влиянием model на decision path.
- Третий этап (следующие задачи, не здесь): фоновая накопительная кампания, остановка/продолжение, отдельные reward objectives для Party, дебаффов, танка, PvP. Не притворяться, что они уже реализованы.

## Почему маленькая модель

Её можно обновлять и проверить за секунды. Отдельный числовой оценщик не обязателен для server survival. Если V4 повреждён/не загружается/score NaN/тайм-аут/недостаточно валидных данных/малый margin: **stock immediately**, диагностическая метка; отсутствие V4 в обычной конфигурации полностью сохраняет старое поведение.

**Не перетаскивать V3 1+ млн весов в новую архитектуру и не ломать старую модель.** Для первого native edge важнее исправная цепочка, чем максимально выразительная сеть.

## Важное инженерное различие

Один реальный native cast — доказательство, что сбор/градиент основан на игре. Изменённые веса — доказательство обучения. Изменение score/rank — доказательство использования модели. **Ни один из этих фактов по отдельности не доказывает, что тактика стала сильнее в PvP или фарме**. Независимая оценка потом.
