# TASK045 — разделить календарное расписание и физическую материализацию

## 1. Идентификатор
`PHANTOM-M1-SIGNAL-LIFECYCLE-045`, High Five; новая ограниченная задача после044.

## 2. Цель
Устранить доказанную причинную помеху G4 на уровне **штатного владельца сигнала/перехода**, а не ещё раз ждать 120 секунд на произвольном споте. Выполнить при возможности один полный native→productive background→useful same-profile native.

## 3. Зависимости
Published a6fde1df1b1785b93e2248a20786c61878e05428, 043/044 historical FAIL, current scheduler signalSources API, older035–042 conservation, original byte-exact CONTINUITY_V2 и release contract М1–М7.

## 4. Контекст
D1: ACTIVE/source count1, no native pins; sourceKey исторически отсутствует. 044: profiler73 `human.local/NEARBY`, no away; C1 SLEEPING/nextDue0. Конкретная гипотеза population.schedule ACTIVE при отсутствии real observer, см. CONTEXT.

## 5. Предварительный аудит
HEAD/remote/tracked/foreign, private DB provenance, protected040a PID/runtime, JDK25/Ant build, точный исходный owner у population/scheduler/locality и existing tests. Изменения лишь после исходного snapshot и анализа контрактов.

## 6. Архитектурное решение
Не переписывать Scheduler/GeoEngine/Player/AI. Сохранить логическую активность расписания независимо от физического присутствия. Физическая материализация только по реальной locality или иным допустимым hard/native требованиям. Design.md — гипотеза, требующая RED.

## 7. Scope
Java signal producer/consumer **одного реального владельца**, связанные tests, isolated ordinary045a, одно native functional испытание по реальному расписанию и полная scoped preservation check.

## 8. Out of scope
G5/G6 restart/crash, client route, Зарич, торговля/разговоры/профессии, PvP/raid/кайтинг, новый planner/AI, изменение радиусов/caps/рейтов/слотов/порогов.

## 9. Правки
RED на conflicting logical schedule/locality без hard hold → minimum code fix if proved → GREEN/negative → actual own native. Если RED не получается, описать границу гипотезы и не делать speculative fix.

## 10. Конфиги
Оригинал WinPS5.1, JDK25/Ant, free private own045a ports. No config/rate/time/calendar edits. Protected040a read-only.

## 11. Производительность
Не увеличивать ring512/512MiB/export budgets. Никаких новых фоновых потоков/глобальных collectors и JDBC/FS I/O в combat callback. Обязательно учитывать true background cadence/due.

## 12. Конкурентность/lifecycle
Single-writer/epoch, 60sec recent human, earned native work, existing hard holds, no duplicate Player/phantom/claim. Все own JVM штатно остановить; не применять force.

## 13. БД
Только own045a из проверенного immutable export, без SQL heal/reimport после start. Committed producer и full stopped canonical cut, actual owner facts, no duplicate rewards.

## 14. Тесты
Самая важная проверка — детерминированное RED/negative по настоящим methods producer/scheduler. Затем existing93/93,11/11,3/3,28/28, affected population tests и короткий native (полный G4 условно по времени). Старые successful G2 только history при JAR edit.

## 15. Команды
Определить actual available Ant targets, не выдумывать `ant test` цель. Для каждого запуска указать exit/sha/test family; latest committed own build jar-first. Exact stock shutdown/consistent SQL.

## 16. Приёмка
Только ACCEPTANCE.md. G4 PASS исключительно после настоящей фоновой работы и полезного возврата одной личности; маркировать хронологию. Ни confidence, ни signal count не PASS.

## 17. Отчёт
Минимальный RESULT045/GATES045/REVIEW045/CHECKPOINT045 и проверенный небольшой review ZIP, вход/выход, по одной causality, явные gaps. Не отчёт по отчётам.

## 18. Commit/push
Scoped exact changed source/tests/task docs, normal commits/non-force push, проверка HEAD=remote и tracked clean. Не включать старые untracked/.phantom-local/raw/DB/JAR/secrets; detached HEAD сохранить.

## 19. При блокировке
Нет обхода системных блокировок; source-first безопасный анализ и stop. Если один native precondition FAIL, не устраивать выбор выигрышной DB/identity. Старые raw/FAIL и current040a сохранить. Следующий task/М2 не запускать.
