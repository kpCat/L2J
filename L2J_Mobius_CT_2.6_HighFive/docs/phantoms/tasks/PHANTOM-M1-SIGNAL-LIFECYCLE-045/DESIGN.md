# DESIGN045 — календарная жизнь ≠ физическая близость

## Инварианты продукта

1. Расписание/online presence отвечает «персонаж сейчас живёт и может выполнять работу»; proximity/native relevance отвечает «надо держать настоящий `Player` в мире». Эти требования нельзя автоматически приравнивать.
2. Если расписание допускает обычный фарм, а реальных наблюдателей нет, физическое присутствие должно в итоге законно завершиться, **после** сохранения всех заработанных действий и hard holds. Тогда допустима настоящая фоновая работа того же профиля по исходным правилам и календарю.
3. При появлении реального наблюдателя существующий `human.local`/topology сигнал должен заблаговременно материализовать персонажа; если есть legitimate combat/real party/targetability — удержание сохраняется. Не требовать мгновенного исчезновения.
4. Schedule SLEEPING/непригодный BG сохраняет запрет генерации наград. Ни один статус или отсчёт времени не заменяет actual committed producer. BG scheduler first due 5–15 минут является реальным контрактом; ecology/historical producer имеет собственную реальную причинную цепочку.
5. Не вводить второй scheduler, отдельный supervisor/owner, game-session proxy или новую DB таблицу. Все существующие native owner/epoch/earned/CAS/reward proofs сохраняются.

## Предпочтительный путь, только после RED

Сначала создать детерминированное тестовое воспроизведение: логическое `population.schedule=ACTIVE` при наличии `human.local=NEARBY` → native, затем физическая locality исчезает/`human.local` истекает, но schedule остаётся логически активным. При отсутствии hard reasons scheduler должен получить законное нематериализующее требование (`BACKGROUND`/другой существующий режим), выдержать retention и закончить dematerialization. На BASE должен воспроизводиться реальный нежелательный hold; тест различает requested state и native retention.

Первым кандидатом является **существующий producer логического расписания `PhantomPopulationManager`**, а не взломанный cleanup/dematerialize. Проекция календарного `ACTIVE` в физическое требование должна быть обоснована контрактами и всеми consumers, особенно `PhantomPresenceRegistry`/ecology, `activeTarget`/admission capacity и topology signals. Возможный минимальный вариант — публиковать для логического расписания безусловно нематериализующее требование при сохранении календарной online/eligible семантики, а физическую материализацию получать через уже существующий current-locality signal. **Не считать именно эту реализацию заранее одобренной**, если исходные tests/native usage опровергают её; выбрать другой единый existing owner с причинным объяснением.

При выборе изменения нужно пройти negatives: current observer prewarm, valid REAL_PARTY, earned outgoing+incoming combat hard hold, topology.combat/targetability, stale generation/sequence, schedule SLEEPING, admitted vs non-admitted, capacity/per-profile actor count, fallback после ухода, cold restart. Не обнулять сигнал schedule или подделывать его expiry просто ради дематериализации.

Если причинный RED показывает, что `population.schedule` не удерживает physical ACTIVE, исследовать **конкретный другой source и актуальную публикацию** с помощью уже добавленного `signalSources`, прежде чем менять продукт. Это не разрешение нового цикла двухчасовых traces; сначала deterministic tests/существующие code paths.

## Наблюдаемость и тесты

Reuse `signalSources` из044. Не добавлять новый логирующий поток, глобальный poller, exporter schema, сотни `*.json` или публикацию секретов. Один bounded per-profile факт до/после ключевой границы достаточен. Если тест опровергает гипотезу, сохранить failure и прекратить product patch, а не маскировать отсутствие причинной информации.
