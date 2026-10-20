# PSS-006 — fresh-context review and author rulings

2026-10-08, отдельный read-only reviewer основной ветки, context: required base f92431aa5594a62210916e94bc6b617ec51f4bdc, окончание A/B/C перед closure. Приватный архив/индексы/тексты reviewer не читал; build/GUI/LM/Java не запускал. Git только scoped diff/stat в Studio. Critical: 0; Important: 2; Minor: 2. Это ревью исходников, не независимый acceptance PASS.

| Finding | Решение автора / доказательство |
| --- | --- |
| Important: SqliteCommand.Cancel — no-op в pinned 10.0.12 | Принято. Native sqlite3_progress_handler на каждой worker-owned connection, 1000 VM instructions; callback только проверяет token. Import/Query получают тот же token, SQLITE_INTERRUPT при отмене переводится в OperationCanceledException, partial не публикуется. Test отменяет на 50-м вызове SQLite scalar function внутри recursive SQL, а не на чтении ZIP. RED SQL закончил 100000 вызовов; GREEN interrupt code 9, 52 вызова <1000. |
| Important: Entries.Count материализует metadata до max500 | Принято. Bounded classic central-directory preflight до ZipArchive/Entries: tail<=65557 bytes, <=500 фактических/объявленных entries, directory<=512 KiB, names<=256 bytes, extra/comment<=4096 bytes, exact spans/offsets/count, cancellation. ZIP64/multidisk отклоняются. RED 20000 empty entries превышает allocation ceiling; GREEN отказ с 103456 allocated bytes <4 MiB. Ложный EOCD count также отклоняется. |
| Minor: UI скрывал безопасные категории ошибки | Принято. Только whitelist CorpusException.Code отображается с рекомендацией разделить лог/получить корректный ZIP/новый индекс. Raw exception/path/entry/message не используются. Existing controls и final build повторены после изменения. |
| Minor: план заявлял temp_store FILE | Принято. План исправлен на MEMORY: не создавать private SQLite temporary bytes в системном TEMP. Fingerprint/time indexes + dedup без NOT IN; cache 8 MiB. Память измерена на 100k и реальном импорте отдельно; не заявляется абсолютный RAM bound для всех возможных запросов/архивов. |

Executable evidence: [review RED](PSS-006-review-red.txt) 7/2 → [review GREEN](PSS-006-review-green.txt) 9/0, exit 1 → 0; [final Release](PSS-006-build-final.txt) 98/0. InternalsVisibleTo предоставлен только существующему тестовому assembly для проверки реального connection hook без reflection/private shell harness и нового test project. API подтверждены XML закреплённого Microsoft.Data.Sqlite и исходниками SQLitePCLRaw 2.1.12; [SQLite progress callback](https://www.sqlite.org/c3ref/progress_handler.html), [PKWARE classic ZIP](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) §§4.3.12/4.3.16.

Declined-to-judge пункты получили отдельные решения:

- Physical UI/DPI/VS: NOT_TESTED, capture timeout, manual gate REQUIRED; не выводятся из static/STA.
- Build/STA/restore/encoding: автор запускает на окончательном коде; результаты в final/UI/evidence, не приписаны reviewer.
- Actual private corpus/privacy/source immutability: авторский локальный разрешённый маршрут; только aggregate/hash, synthetic physical-byte privacy test. Нет заявления о privacy-perfect anonymity публичных оригиналов.
- LIVE_LM: BLOCKED_LM, loaded list пуст, POST=0; Java NOT_RUN.
- External filesystem mutation между окончанием read и repaint: freshness относится к полному worker read/hash и проверенной UI identity. Непрерывный filesystem watcher/атомарность с внешним редактором не обещаются; прежний synchronous путь тоже не владел чужим деревом. Existing source/peer drift gates сохранены.
- Deletion/migration/crash recovery: вне scope. Immutable version=1, failed own partial удаляется; orphan partial после аварийного kill не виден List, автоматической уборки чужих directories нет.
- Publication/inventory: отдельный closing gate с exact allowlist, nonforce push и remote SHA proof. Не объявлен PASS ревьюером до выполнения.

Повторное review не запрашивалось; автор оценил замечания, подтвердил дефекты и проверил исправления. PSS-007 не начат.
