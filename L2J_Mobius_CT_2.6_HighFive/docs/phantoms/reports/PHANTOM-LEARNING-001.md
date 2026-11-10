# PHANTOM-LEARNING-001 — PARTIAL

10 ноября 2026 года. Исходный SHA `c5b81f89ce02030eff8441395b0411137a110b2f`, коммит реализации `518318c89c6937c80818b4035df6b0fc9523f9ce`. Отчёт и передача контекста идут следующим коммитом в `wip/phantom-learning`.

## Реализация

- Общий MLP 128→24 ReLU→1 с float32, воспроизводимым SGD, ограничением градиента и весов, L2 и выбором эпохи по отдельному tuning split. V1 кодирует 64 пары «значение и маска». Неизвестная проходимость остаётся замаскирована. Навыки со сложными предварительными условиями консервативно исключены из кандидатов.
- `PhantomCombatService.issueAction` использует тот же owner для обычного Solo и автономного Party PvE. Модель переупорядочивает 2–4 кандидата после штатного positioning. Штатный dispatch повторно проверяет навык, MP, цели и lease. OFF, один доступный навык, неподтверждённая версия, stale снимок и малый разрыв оценок сохраняют round-robin. Агрессия, Heal, loot, spoil/sweep, PvP, raid и siege сохраняют свои штатные пути.
- `LearningCastEvidence` привязывает опыт к штатному `MagicUseTask`, точному актёру, epoch, Combat generation, skill и spawn цели. Штатный HP writer отдаёт наблюдение только для прямого положительного урона того же эффекта. Перекрытие HP writes, отказ, завершение lease и повторный callback исключают credit. Party использует пассивную проверку поколения и ревизии состава. Длительный подход или отложенный старт без привязки пропускается. Reward учитывает долю фактически снятого HP и расходы MP, времени и предметов. Отдельная оценка выживаемости отсутствует.
- Две таблицы InnoDB, очередь на 512 примеров и один writer на сервер. SQL selection не вызывается из Combat. Offline trainer разделяет актёров на train, tuning и audit и исключает из audit уже встречавшийся контекст. Принятие версии выполняется явным SQL CAS после проверки весов, native provenance и метрик. Параметры по умолчанию OFF. Для эксперимента требуются JVM opt-in и отдельная loopback MariaDB. При reload SkillData learning отключается до перезапуска.

Изменения находятся в `phantoms/learning`, Combat owner и Party admission, точках наблюдения `MagicUseTask`, `Creature` и `CreatureStatus`, регистрации PhantomSystem и config, штатном SQL installer и Ant registry. В коммите реализации 25 файлов кода, конфигурации, SQL и тестов плюс 12 исходных файлов пакета. SHA-256 ZIP `fa8e7b2522f3c8bff9478495de162a699f8a41ec83c3424cc9ffd503d64a3290`. `SHA256SUMS.txt` подтвердил 11/11 файлов исходного пакета.

## Измерения

Финальная синтетическая выборка содержит 144 train, 48 tuning и 48 audit примеров. Audit охватывает 24 ранее не виденных сочетания. Тест отдельно проверяет отсутствие одинаковых закодированных строк в audit и обучении. MSE в audit снизилась с **0,446780** до **0,000432**. Средняя отдача выбора составила **0,8000** против **0,2500** у round-robin.

Тест запускает production `PhantomCombatService` с управляемым backend. Один набор весов изменил первый навык в двух разных контекстах Solo и Party. Контроли SHADOW, OFF, одного готового навыка и устаревшей идентичности сохранили первый штатный навык. Реальные PlayerAI, Geo и применение эффекта в этом тесте отсутствуют.

JDK 25.0.4.1, 128 чередующихся потоков фактов, 4 кандидата, 30 000 warmup и 30 000 замеров. Для выбора по готовым векторам p50 **6,6 мкс**, p95 **7,3 мкс**, p99 **10,4 мкс**, **24,1 байта на выбор**, **203,125 мс CPU**. Веса 12 484 байта, payload векторов 262 144 байта, heap snapshot до и после 17 979 616 байт. Замер охватывает scoring и выбор, без построения снапшота мира и native cast.

## SQL readback и остановка

MariaDB 11.4.13, loopback `33306`, отдельная схема `phantom_learning001_proof`. G0 подтвердил собственный PID **47728**, FILETIME UTC `2026-11-10T18:11:06.8823807Z`, executable и datadir. Записан checkpoint **2**, алгоритм **1**, эпоха **160**, `SYNTHETIC / DRAFT`, blob **12 484** байта. SQL вернул `digest_match=1`. Отдельный JVM загрузил тот же SHA и повторил выбор через CombatService fixture.

- SHA-256 модели `5e3b0857f8523a8ac6ad345d5784768d6aff210bcb42dc136f777ab23be48314`.
- Fingerprint V1 `53a0b97cd4c771a2d2a93b46829273f312e0dfd77626989e92b9b52ae2dc1d34`.
- Fingerprint эффективного каталога `e46709db95ce8e92f4b4f392b5c468d147c6ddb11ab6c773cc48f3702bc6474a`.
- Повторная вставка оставила одну строку `SYNTHETIC / FIXTURE`. Native trainer её отклонил. Повреждённый checksum и устаревшая схема отклонены, rollback восстановил исходную строку. Число ACCEPTED — 0, число NATIVE — 0.
- Штатный SQL STOP, exit 0. Скрипт проверил, что PID и слушатель порта исчезли. GameServer bootstrap — 0/2. Один clean JAR из двух разрешённых.

## Проверки

`ant -f build.xml compile-tests` и `ant -f build.xml jar` из модуля завершились с exit 0. В финальном compile-tests пять существующих предупреждений JDK в исторических тестах. SHA-256 `dist/libs/GameServer.jar` — `eec58870528bd2b77de154472892154891bea9780d2f2e22d8ca620c12f097e0`. Все пять основных learning-классов присутствуют в JAR.

Из `dist/game` запущен штатный `org.l2jmobius.tests.phantoms.PhantomTestLauncher <suite> <seed>` с `phantom.module.root` и `phantom.test.reports`, classpath содержит `build/bin`, `build/phantom-test/bin`, `build/phantom-test/resources` и `dist/libs/*`. Финальные запуски дали exit 0.

| Suite | Seed | PASS |
|---|---:|---:|
| learning001 | 20260725001 | 2/2 |
| combat-action-ownership | 20260725001 | 37/37 |
| party-tactics | 17001701 | 13/13 |
| world-coordination130 | 130 | 77/77 |
| pvp-combat | 25002501 | 14/14 |
| post002-support | 2002002 | 9/9 |
| supply071 | 71007101 | 11/11 |
| commerce-receipt | 140014 | 15/15 |

Первый запуск `party-tactics` через Ant из модуля завершился exit 1 из-за относительного пути к `statBonus.xml`. Повтор из `dist/game` прошёл. Черновой выбор имени PvP suite дал exit 2, а запуск `commerce-supply` с неподходящим seed — exit 1. Финальные проверки используют точное имя `pvp-combat` и DB-free supply и receipt маршруты. SQL fixture запускался командами `LearningSql001Check write <gameRoot>` и `LearningSql001Check read <gameRoot> 2 <sha256>` в отдельных JVM. Оба exit 0. Пробный SQL digest SELECT исправлен на бинарное сравнение после ошибки collations.

Ревью выявило побочные эффекты активной Party-проверки и пробных preconditions, совпадения синтетических векторов между split, окно конкурентной записи HP и неполную идентичность каталога. Все эти находки обработаны одним исправительным проходом, затем повторены перечисленные проверки. P1 по review не выявлены. Native наблюдение остаётся самостоятельным открытым gate.

- Mojibake-маркеры в изменённых файлах проверены полным списком, совпадений нет.
- Escaped Cyrillic в изменённых файлах проверен по Unicode и XML шаблонам, совпадений нет.

## Открытые gates и Git

**NATIVE_UNPROVEN.** Запись фактически атрибутированного урона из PlayerAI, обучение по этим записям, положительный переход NATIVE модели в ACCEPTED, реальное применение в Solo и Party и его повтор после загрузки остаются открыты. Общий headless guard требует порт 3308 и отдельный закреплённый fixture, а имеющийся TASK130 native маршрут требует свою привязку к полному сценарию. Для этого результата использован только собственный SQL-only контур. Полные F7/F8, native Geo203 и protected207 gates не запускались. TASK130 остаётся PARTIAL.

Scoped исключение владельца позволяет отдельный clone и обычный push только в `wip/phantom-learning`. Пользовательский checkout был read-only. MAIN, PLAY, TEST129 и старые SQL/Geo контуры не участвовали. Подготовка и runtime-артефакты находятся в независимом clone. Каталог `.phantom-local/learning001` содержит приватные данные и логи, он исключён из коммитов. В Git вошли только разрешённые пути High Five. Точные команды и следующий шаг приведены в [HANDOFF](../tasks/PHANTOM-LEARNING-001/HANDOFF.md).
