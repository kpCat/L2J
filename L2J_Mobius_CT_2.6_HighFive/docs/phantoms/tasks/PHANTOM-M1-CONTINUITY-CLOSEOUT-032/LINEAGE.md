# LINEAGE — не поздний SQL вместо ожидаемого native snapshot

## Закрыть старый вопрос без нового runtime, если raw достаточно

031 оригинальные receipts группы3/43/95/117/121/155/195/204 → restart34 → restart35.
Для117/121/155/195/204 metadata найдено. Для95 последняя причина UNKNOWN.
Прочитать только нужные old streams, проверить source/Jar, operation kind, profile/object,
before/after rowVersion, exact payload hashes, commit success. Табличка version+1 не proof.

IMPORTANT: metadata-only `OrdinaryProjectionCommit` сам не имеет права менять XYZ,
EXP/SP/vitals/items. Он может подтверждать последний шаг цепочки, но не доказывает
предшествующую телепортацию/фоновый TRAVEL. Найти настоящий position writer и его
допустимые conditions, не принять Town location просто потому, что версия увеличилась.

## Новая proof-chain

Для каждого enrolled lifetime:
REGISTER(source/run/object/epoch)
→ immutable native PREPARED/FINALIZED с exact payload/receipt digest
→ terminal либо текущий checkpoint
→ 0..N committed background/metadata transitions
→ quiet SQL на следующем startup
→ native rematerialization (если произошла).

Для каждого перехода подтвердить predecessor payload/version и successor payload/version,
владение и отсутствие параллельного world writer; restart integrity/EXPSP conservation.
Projection: native-поля неизменны. TRAVEL: допустим XYZ/time transition без награды
неизвестного происхождения. FARM: штатно смоделированные rewards/resources/death с
операционным receipt, не ручной UPDATE.
Если receipt отсутствует: OPEN с exact gap. Нельзя синтезировать его из конечной SQL.
Первоначальные FAIL и missing IDs остаются в отчёте. Старые доказательства не становятся
доказательством нового source SHA; их используют для диагностики и выборочных регрессий.

## Observation safety

Обычный hook — immutable capture/enqueue only. No SQL/FS/wait inside native callback.
Exporter — ограниченный поток/очередь, read-only SELECT с deadline, без попытки
получить синхронный снимок блокированием gameplay. Его поздний view маркировать ASYNC,
не сравнивать как exact commit. Не строить отдельную платформу event sourcing.

Вне игрового времени можно quiet startup boundary с существующим031 helper:
event-thread only, hard8s resume, остальные потоки живы; проверить race и actual
phase/entries0 перед SQL. Он не делает времени breakpoint частью gameplay PASS.

## Crash

Два актуальных окна: AFTER_OWNED_NATIVE_STORE (72) и AFTER_OWNED_FINALIZE_COMMIT (73).
Использовать существующие typed fault points на своих clones, REALcount0, exact receipt,
после естественного earned progress. Recovery auto-release диагностического barrier,
никакого забываемого external job45s. Старый Jar87 не заменяет final candidate032.
После каждого planned halt: restart, exact resolution, повторный restart идемпотентен,
потом healthy drain. Planned halt не выдавать за failed graceful и наоборот.
