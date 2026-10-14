# DESIGN044 — минимальная наблюдаемость причины, затем исправление

Главная ошибка предыдущих циклов: два нормальных native измерения использованы до получения owner key оставшегося scheduler signal, а ожидание background ограничивалось77с без доказательства соответствия штатной cadence.

## Вариант A (основной)
Из существующих raw `SCHED_SIGNAL_ACCEPTED/COALESCED/WITHDRAW/EXPIRED` для D1 получить key, sequence и actual timing. Если есть полный факт — код наблюдателя не менять. Одновременно offline вывести earliest genuine BG due C1. Реальную причину отличить от законного ожидания по TTL и тому, какой producer должен был сработать.

## Вариант B (условный)
Если raw owner key не сохранён, добавить **только неизменяющий состояние** snapshot источников в существующий `PhantomScheduler` и узкий доступ через старый local operator/debug owner. Тесты на неизменяемость, replacement/withdrawn/expired. Один краткий native run для получения key на действительной away границе, не новый полный native-life run. Ни hooks в combat math, ни пер-актёрных thread, ни глобального LOGGER flood. После подтверждения key определить владельца сигналов по реальному call path.

## Исправление допускается только после причинного RED
Если сигнал stale, производитель обязан перестать его обновлять/штатно withdraw при корректной границе. Исправить именно producer, не scheduler «kill all signals on human away», не удалять valid PvP/party/combat source, не вводить TTL-shortcut. Если сигнал законен и сам истекает, wait/scene precondition и последующий release — не production defect.

## G4 positive
От одного заранее зафиксированного stable profile: native earned→real soft release with finish/drain and owning commit→actual background operation/producer with committed record after eligible due→new native epoch same profile and actual useful action; death означает законный loss/recovery, а не замену actor. Для BG ожидания >525с допускаются последовательные независимые штатные Synthetic-сессии, но не одна бесконечно продлённая.

Никакой второй системы сигналов, маршрутизатора, искусственного движения мертвого персонажа и нового персистентного audit. Release/observer должен быть асимметричным: быстрый prewarm и консервативный отложенный safe release.
