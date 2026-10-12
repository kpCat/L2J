# COMMIT_BOUNDARY039 — конечная проверка вместо бесконечного lineage-расследования

## Зафиксированное наблюдение
verify_lifetimes038.py берёт .sql.tsv, снятый после FINALIZED, и требует всё ещё видеть
preparedRowVersion+1. Background может законно успеть изменить state после detach.
Это гипотеза для конкретного mismatch, не автоматическое объяснение всех записей.
B382/C348 поздних границ и B71/C66 native parity разбирать как два разных класса.

## Порядок решения
1. В raw038 найти existing producer/end receipts и связать один представитель каждого
   класса, включая enrolled actors. Проверять namespace/run/profile/object/epoch,
   inventory FULL, skill/class, native values, PNC и returned completed state.
2. Если receipts достаточны: исправить только normalizer/verifier039 и протестировать
   missing predecessor, tampered payload, wrongepoch, delayed writer и duplicate reward.
3. Если существенных фактов нет: один optional immutable witness на existing owning
   transaction boundary. Составить SOURCE→READ→COMMIT→PUBLISH карту прежде правки.
   Изменение039 не делает отсутствующий historical038 факт задним числом существующим.
4. Проверить current полный run, а не бесконечно восстанавливать весь historical архив.
   Исторические UNKNOWN оставить UNKNOWN, когда в текущем коде причина устранена/проверена.

## Минимальный witness (использовать существующие типы, не новую БД)
run/boot identity;sourceJarSha;profileId/objectId/classIndex;nativeEpoch для native операции;
componentName;operation kind+exact receipt/key;beforeRowVersion/hash;afterRowVersion/hash;
полный immutable payload;native-context version/hash/phase;полный inventory hash/count и
immutable comparison facts или digest, рассчитанный одним documented canonical encoder;
canonical checked=true только после реального сравнения под owning transaction;
commit outcome отдельный от runtime callback outcome;producer;sequence/sample time.

Нативная FINALIZED-граница требует exact expected native fields ↔ actual locked canonical
с учётом существующего source rounding и classIndex; нельзя придумывать tolerance.
Только поля, которые source действительно canonicalizes, могут нормализоваться тем же способом.
Имена полей/offsets брать из текущих codec/API, не копировать magic offset из старого raw verifier.

## Контракт публикации
- Факты собрать из уже имеющихся locked reads/result; immutable копия до выхода из ownership.
- Известный успешный connection.commit → неблокирующая передача в существующий collector.
- Rollback/ошибка commit/COMMIT_OUTCOME_UNKNOWN не выпускают вымышленный committed proof.
  Ambiguous outcome проверяется штатным recovery, never replay reward by observer.
- Observer disabled ничего не меняет в продукте. Full queue/drop → proof GAP, не блокировка
  gameplay и не «добавим limit». Не хранить ссылки Player/Connection в exporter.
- Никаких новых запросов БД/fsync/ожиданий в native locks, FINALIZED или pool tick.
  Если нужен дополнительный read, только existing JDBC control transaction и согласованный
  порядок locks, вне gameplay monitor; предпочтение уже имеющимся фактам.
- До COMMIT witness не отправлять; callback publication failure не отменяет состоявшийся commit.
  Сохранить статус proof gap отдельно от бизнес-результата.
- Не удлинять время удержания livePlayer лишь ради более позднего SQL dump.

## Полнота цепочки
background.state переходы включают PREPARE, FINALIZE, background mutation, VERIFY promotion,
recovery/native refresh. Один logical operation может иметь несколько реальных row increments.
Для каждого increment нужен свой record identity. SAME key не означает duplicate award,
а повторный native award нельзя оправдать несколькими фазами receipt.

NAMESPACE=(DB,profileId,objectId,component). Version/hash переходы внутри него непрерывны.
Epoch меняется только через native birth/terminal lineage, не через склеивание счётчиков.
ПозднийSQL: EXACT | LINKED_COMMITTED_SUCCESSOR | GAP | CONFLICT. Первые два относятся
только к цепочке component; native fields/context/full inventory доказываются отдельно.
По одному SNAPSHOT нельзя восстановить утраченный predecessor — не пытаться угадать.

## Пример в tools
commit_chain039.py — только STRUCTURAL_CHAIN_ONLY, всегда fullSaveProven=false.
Не внедрять его в сервер. Сначала normalizer связывает raw+SHA+verified transaction witnesses,
потом checker помогает отвергнуть gaps/forks. Успешный checker не завершает G3 самостоятельно.

## Текущий завершающий SQL-read
Для stopped snapshot разрешён обычный readonly consistent transaction на own DB при
отсутствующих own writers. Не выполнять отдельные SELECT без общей границы, когда другой
writer может менять items между characters/component reads. Это не остановка живого мира
для каждого store — это только финальная проверка уже штатно остановленного clone.
