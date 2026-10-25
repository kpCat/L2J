# DELIVERY — порядок и ручная приёмка

1. READ-FIRST, package digest, actual HEAD/branch/status, published reports087/089/090 и production source.
2. Таблица причинного RED, один технически обоснованный plan-of-record (source→plan→native move→paid→persist). Если неясен атомарный платёжный owner — STOP с архитектурной проблемой, не писать provisional paid commit.
3. Общие minimum fixes в существующих owners; первая focused GREEN с защитными отрицательными тестами.
4. Один guarded TEST091 native e2e: H/E/O, два real paid GK, farm rewards и контролируемый paid restart. Не запускать вторую параллельную кампанию/переименование harness ради PASS.
5. Отчёт `docs/phantoms/reports/PHANTOM-INTERREGIONAL-TRAVEL-CLOSURE-091.md`, P0–P7 + provenance; exact scoped changed files и case-by-case NOT_RUN; original 089 остаётся `PARTIAL`, 090 остается `PARTIAL/NATIVE_NOT_RUN` без переписывания их reports.
6. Точный `git diff --check`, проверка UTF-8 и изменений Rates/OwnerConfig=0, exact-path stage. Commit subject: `phantom(task-091): close native interregional travel` **только при полном SERVER_VERIFIED**; при честном PARTIAL — `phantom(task-091): advance native travel with open gameplay gates`; при BLOCKED — `phantom(task-091): document native travel blockers`. Normal non-force push и verify local/upstream/live SHA, tracked clean, unchanged other untracked.
7. Завершить сообщение Codex на русском: `TOTAL091`, что реально делает **в игре**, H/E/O/P0–P7, сколько native GK debits/rewards/restart, список открытых blockers, commit/branch/push/clean, ссылка на report. **Следующую задачу не запускать.**

После ответа Codex владелец направляет итог на независимый GitHub diff-review. OWNER_QA_PENDING сохраняется до реального входа через клиент.
