# SCENARIOS039 — классы ошибок, а не тысячи отдельных персонажей

|ID|Сценарий|Обязательный факт|
|---|---|---|
|A01|Current437 без useful progress;515/576 с длинным перерывом|Первый потерянный переход, actual targets/capabilities/current epoch; прежний report не является причиной.|
|A02|Native progress перед ранним resource/busy return|Настоящий damage не пропущен observer; без progress долг не обновляется.|
|A03|Цель умерла после честного launch|Earned заканчивается штатно; новые offensive roots не назначаются трупу; следующая цель выбирается без вечной очереди.|
|A04|ObjectID цели переиспользован новым spawn|Старый маршрут или выбор каста не применяется к новому рождению.|
|A05|Owner/goal/epoch изменился при pending navigation|Устаревший reply отвергается; чужое движение не отменяется.|
|A06|Конкурируют decision, AutoPlay и AutoUse|Один writer; busy даёт PAUSED; нет дублирования move/cast.|
|A07|Умение в cooldown или недостаточно MP|Допустимая альтернатива либо ограниченный native rest; нет fake MP и принудительного melee для всех магов.|
|A08|Прямой путь, угол, другой слой|Один controller и stock geo; вторая геометрия без прохода сквозь препятствие.|
|A09|Совместная цель и доступная альтернатива|Shared attack разрешена; нет универсального запрета «цель занята».|
|P01|FINALIZE v; background commit v+1; поздний SQL|Два разных commit доказаны, исходный FAIL сохранён.|
|P02|То же, но промежуточного witness нет|GAP, не автоматическое разрешение любой v+1.|
|P03|Одинаковая version, другие payload/native fields|CONFLICT/NOT_VERIFIED; такое нельзя объяснить только фоном.|
|P04|Rollback, commit unknown, повтор callback|Нет ложного committed receipt и повторной награды.|
|P05|Exporter переполнен, отключён или читает изменяемый объект|Gameplay не блокируется, proof GAP явный, Player/Connection не удерживаются.|
|P06|VERIFY_PENDING → READY/DEAD|Обе версии и PNC связаны; одна операция не обязательно равна одному increment.|
|P07|Overfull252/250 и sameDB reload|Все items/count/location/hash сохранены, новый slot не разрешён.|
|L01|Dead-at-baseline в stable-farm setup|Вся подготовка невалидна; не удалять один ID и не лечить/сбрасывать SQL.|
|L02|Настоящая native смерть|Exact death/earned settlement/save; stock resurrection → reentry → полезное действие.|
|L03|Native human away|Подтверждён выход из locality; мягкий переход не исчезает под носом; hard interactions сохранены.|
|L04|Background и возвращение|Настоящий продуктивный commit; тот же profile, корректный native epoch; фарм возобновлён.|
|R01|Два restart той же БД|Состояния не изменились либо изменения полностью объяснены; нет удвоения наград.|
|R02|Crash72/73|Контрольный exit на own clone; reconciliation и второй restart подтверждают idempotence.|

Переиспользовать native fixtures038/036/035. Existing DynamicRecipient WorkerGate —
только для контролируемого interleaving. TEST-position/death задаются разрешённым setup,
не setXYZInvisible после World publication и не setter счётчиков.

После каждого semantic block проверять: обычный REAL не изменился; новые PvP/party roots
не возникли; cast/root/cancellation различаются; native death/stop race безопасна;
объекты immutable на export boundary; ограничения resource/debt/evidence не подняты.

Ошибку helper чинить в существующем adapter. Не собирать заново все wrappers033–038.
Команды native cases взять из проверенных ops038/Verify038; новый focus — в suite039.
