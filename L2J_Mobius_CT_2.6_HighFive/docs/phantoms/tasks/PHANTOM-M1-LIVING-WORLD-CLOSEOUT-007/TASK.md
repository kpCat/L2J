# PHANTOM-M1-LIVING-WORLD-CLOSEOUT-007

## Цель

Закрыть оставшуюся инженерную основу M1 так, чтобы приёмка доказывала живой мир:
естественные native Player добираются до места фарма, наносят урон, убивают, получают
EXP/SP и законный loot/reward, выбирают следующую цель, восстанавливаются после смерти,
корректно возвращаются в background и переживают сохранение/перезапуск без split-state.
Вечное стояние, массовый ошибочный уход в воду, бесконечный replan и ложный PASS не
переносятся в M10. M2 и новые PvP/social/progression/economy features не входят в scope.

## Исходная точка

Base/report SHA: `d924a7d2c2f1e06af1cd54bedf1bee861560044f`.
Production baseline: `461a4abe32be4aa08532b8417a6147684a8889c6`.
006 candidate: 13 непринятых source/test/build/runner путей + полный audit patch.
Все прежние PASS — evidence старого candidate, не результат текущей реализации.
Исторические исключения 110/142/175 остаются UNAVAILABLE; не объявлять их раскрытыми.

## Разрешение и приоритет

Этот пакет заменяет ограничения task006 D5, PLAN workstream4 и старый file allowlist
для продолжения того же native lifecycle класса. Сохраняются запреты на потерю earned
reward, weakened mismatch guard, новый combat engine, изменённый баланс и ручной PLAY reset.

SOURCE_MAP.tsv — начальная карта, НЕ исчерпывающая тюрьма для правок. Разрешено расширить
scope на конкретные existing/native helper/test/data пути внутри High Five, когда:
1. указан точный call chain к M1 дефекту или обязательному regression;
2. файл прочитан, записаны before blob/hash и причина;
3. есть RED реального поведения либо прозрачная необходимость интеграции доказанного fix;
4. записаны proposed change, test и reviewer risk в SCOPE_EXTENSIONS.tsv ДО правки.

Нужный следующий файл того же класса не требует нового пользовательского разрешения.
Новые компактные helper-классы для owner/ticket/checkpoint/диагностики разрешены; их имена
и consumers записать в manifest. Нельзя переписывать все quests, разносить Phantom
проверки по каждой gameplay-ветке или заменять ThreadPool/GeoEngine/AutoPlay своим движком.

## Обязательная поставка

Закрыть весь связанный набор Q/P/E/T/L/A/W из SCENARIOS.md, а не один delayed event.
Доказать безопасность producer closure и активных checkpoint, liveness после timeout,
полноту на реальных stock producers, native farm/loot и сохранение на том же lifecycle.
Сделать готовыми инструменты runtime-фазы и truthful evidence, а не только counters.

## Среда и safety

Windows 11, JDK25, Ant, MariaDB 127.0.0.1:3308.
PLAY `l2jmobiush5_localplay3`: SELECT/read/decode; только диагностика без мутаций.
TEST `l2jmobiush5_phantom_test`: existing guarded gate, проверенный schema manifest,
штатные fixture creation/cleanup. Не ослаблять guard и не использовать PLAY как TEST.
Текущие PLAY JVM/JAR/config/клиент не трогать до отдельного runtime-разрешения.
Population 1280 READY / 8720 RETIRED сохранить; MaxScheduled не снижать ради результата.

У пользователя root C:\Users\ZBook\L2J_Mobius\. Старый checkout b80cdf7 с чужим diff
сохранить целиком. Проверить фактическое состояние, не принимать старые PID за актуальные.
Создание нового отдельного worktree от base заранее разрешено, если нет бесспорно
подходящего clean workspace. Рабочий candidate006 не очищать и не переключать.

Git: read-only status/diff/log/show/ls-tree/rev-parse/ls-remote/fetch и git archive
разрешены. Никаких add ., reset/clean/stash/rebase/force/broad restore.
Exact-path add/commit/normal push origin HEAD:refs/heads/feature/phantom-world разрешены
для этого scope; перед push проверить remote ancestry. При чужом новом HEAD не переписывать
историю. Команды и changed-path ledger фиксировать компактно, без секретов и бинарников.

## Когда разрешён BLOCKED

Неизвестный файл/новый доказанный native writer/неполный старый тест/сложность задачи/
отсутствие исторического exception сами по себе НЕ причины остановки.
Продолжать связанное расследование и исправление, даже если понадобится несколько
coherent source commits и новая регрессия в соседнем файле.

Допустима остановка при реальном конфликте чужих изменений, недоступном guarded TEST,
необходимости изменения production DB schema или выхода за M1/High Five, либо доказанном
противоречии, для которого после исследования нет безопасной реализации в согласованном
контракте. Нужны reproducer, рассмотренные решения, точная минимальная дополнительная
необходимость и безопасно сохранённый candidate. Не повторять один BLOCKED три раза.

## Boundary

В этой инженерной фазе deploy/старый synthetic №5/real arm запрещены. После обязательных
native gates — publish source и RESULT/HANDOFF, clean exact-SHA build, STOP на
READY_FOR_CONTROLLED_DEPLOY. Это M1_OPEN, не полное закрытие.
Координатор независимо читает remote diff; следующий новый Codex-чат запускает уже
подготовленную runtime-фазу. M1_CLOSED возможен только по полному ACCEPTANCE.md.
