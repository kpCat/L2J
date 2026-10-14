# TASK044 — разрыв G4-петли: владелец сигнала и реальное расписание фоновой работы

## 1. Идентификатор
`PHANTOM-M1-G4-CAUSAL-044`, только L2J Mobius High Five. Короткая отдельная задача, не 4-я повторная слепая LIFE-кампания.

## 2. Цель
Закрыть два причинных пробела из043: `D1 sourceKey/sequence/expiry` оставшегося scheduler ACTIVE и `C1 actual BG due` до ожидания награды. При доказанном дефекте исправить его в исходном owner, затем один полный real G4. Если код исправлять не надо, подтвердить легальный signal/producer schedule, и при достаточном бюджете провести один корректный LIFE по реальному clock.

## 3. Зависимости
BASE044 и последние042/043 `RESULT/REVIEW/CHECKPOINT`, `PhantomScheduler/Policy`, `PhantomSystem`, `PhantomRelevanceSignal`, `PhantomHumanLocalityControl`, `PhantomPerceptionProvider`, `PhantomMaterializationRetentionPolicy`, `PhantomHistoricalBackgroundService`. Также Master Plan и рабочие стандарты. Старые БД/raw не изменять.

## 4. Контекст
C1 lawful release, background77.23s no producer, return DEAD. D1 settled native, pins=[], activeSources=1, `ACTIVE/STABLE` after129.84s, key/expiry absent. Product043 unchanged. Scheduler BACKGROUND cadence5–15 min, signals come from multiple producers. G2 A5/C24 historical PASS for old semantic JAR.

## 5. Предварительный аудит
Проверить HEAD/remote, exact own module, external live040a, same JAR/evaluator, existing TEST and source analogs. Read old043 raw and signal flow first. До input closure ни source edits, ни server start.

## 6. Архитектура
Ровно один `PhantomScheduler` owner signals, один existing native Player→owning-store→background protocol. Если needed — immutable read-only one-profile view внутри Scheduler, без влияния на expiry/status/decision, затем error fix только реального producer. Не создавать новый AI/scheduler/store/export.

## 7. Scope
Offline causal analysis, один minimal read-only signal snapshot и focused tests при необходимости, ≤1 короткий ordinary current native trace, условный минимальный fix, ≤1 полный G4 native round и затронутые регрессии. No other milestones/gates inferred.

## 8. Out of scope
Crash/recovery72/73 и force termination, client network intercept, backend replacement, торговля, профессии, тактики M2–M7, Zariche; protected live/old DB/MAIN/PLAY. Фоновую периодичность ради теста менять запрещено.

## 9. Требуемые изменения
(а) Source/TTL D1, (б) actual BG due C1, (в) если stale owner causal RED — minimal repair with negative, (г) actual productive G4 under real time. Следовать PLAN, не переписывать чужие FAIL.

## 10. Конфиги
Original 45/90/120, Synthetic525/cap5, population1280/active8/materialized8, inventory250, rates/radii/catalog/evaluator unchanged. Own exact schema/runtime/root only. No SSL/login/network tooling needed.

## 11. Производительность
No broad captures/full DB copies beyond required own stopped proof, no additional per-signal Java thread/SQL/fsync. Scheduler read-only getter O(number of existing profile sources ≤16) under existing lock, no mutation; not installed as polling every tick.

## 12. Конкурентность / lifecycle
Read sources with same `_monitor` that guards submit/withdraw/expire; snapshot immutable with consistent monotonic clock. Existing `ACTIVE_ACTION` / REAL_PARTY / earned-work pins preserved. Exactly one owner profile and legal handoff, no reentry duplication.

## 13. БД
Only new own044a from previously verified immutable preboot export if native necessary; no input reimport after start, SQL-heal/clock cheat and prod DB access. Stopped final snapshot consistent read-only after all writers; actual successful commit witness and operation IDs.

## 14. Автоматические тесты
Source snapshot deterministic tests for replacement/withdraw/expiry (включая overdue до штатного pulse, не скрывать)/non-existent/side-effect-free; current jar-first93/93, lifecycle11/11, soft-return3/3 and any affected. Только неизменённый exact GameServer.jar/config/evaluator может reuse G2 old candidate; при новом JAR соблюсти строгий acceptance обеих групп или пометить G2_CURRENT=NOT_VERIFIED.

## 15. Команды проверки
Определить реальные targets/commands из build.xml и существующих Verify041–043, не выдумывать Ant targets. Журналы exit, JAR provenance и exact candidate. No marathon new sweep.

## 16. Приёмка
См. ACCEPTANCE.md. Не приписывать scheduler activeSignals cause до key/TTL и producer path. Не требовать positive producer в первые77s, если scheduler не мог выполнить work. Без реальной полезной фоновой операции полного G4 PASS нет.

## 17. Отчёт
`CAUSE044.md`, `RESULT044.md`, `GATES044.tsv`, `REVIEW044.md`, `CHECKPOINT044.md`, compact ZIP and actual publication receipt. При partial объяснить один конкретный следующий исправимый owner и отделить game bug от ошибочной измерительной предпосылки.

## 18. Commit/push
Exact OWN allowlist, ordinary commit + normal non-force push, verify remote SHA/tracked clean, no forced operations, preserve all historical FAIL/raw. Тестовые/private снимки не публиковать.

## 19. При блокировке
Blocked platform action не обходить; остановить только собственное штатно. Если необходимый owner нельзя установить на старой версии и short view, объяснить конкретный недостаток интерфейса, не выполнять бессмысленные повторные часы. Сохранить полезный код/test evidence/report, M1 OPEN.
