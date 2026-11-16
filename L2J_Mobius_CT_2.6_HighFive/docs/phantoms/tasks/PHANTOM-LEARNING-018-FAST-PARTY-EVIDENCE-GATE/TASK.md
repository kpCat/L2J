# TASK018 — FAST PARTY EARNINGS CAUSE / GO-NO-GO

## 1. Идентификатор
`PHANTOM-LEARNING-018-FAST-PARTY-EVIDENCE-GATE`. Задача №12 из 13, после выдачи останется 1 до обязательного пересмотра бюджета.

## 2. Цель
В пределах короткой задачи установить **первую доказанную причину** исчезновения подтверждённых EXP/SP у одного Party-участника класса 94 и, только если причина узкая и безопасно исправима, показать натуральную проверку на малом количестве боёв. Если доказательств не хватает — `STOP_DIAGNOSIS`, вынести владельцу 2–3 обоснованных способа двигаться дальше. Не требовать `FULL` искусственно.

## 3. Зависимости
JDK25, существующий Ant и PowerShell 5.1, текущие canon native player/owner/reward observers. Никаких новых внешних библиотек, БД/schema, самостоятельных worktrees, таймеров/фоновых агентов.

## 4. Контекст
Goal016: 51 CUT, 399 paid effects, 17 Party `NATIVE_KILL`, 71 исключённое действие, 27 Solo terminal receipts. Goal017: 7 TRAIN gradients / 6 TUNING / 4 AUDIT, существующие пороги 16/8/8; all-party recovered best ≤13 gradients/≤7 AUDIT, FIT невозможен. Один участник каждого Party эпизода имеет EXP/SP=`null`. В `TerminalRewardReceiptV1.java` это неизбежный отказ; **какая проверка сработала первой и почему native evidence стала UNKNOWN, не доказано**. Итог017 — `ACCEPTED_PARTIAL`, не переписывать.

## 5. Обязательный аудит
Только существующие reader/receipts/cold evidence017 и точечный просмотр `Runtime.outcomeActors`, `PlayerNativeEvidence.snapshot`, `terminalEvidenceFacts017`, `TerminalRewardReceiptV1` и соответствующего диагностического collector. Выяснить, можно ли без игрового запуска отличить `current=null`, mismatch epoch, `overflow`, `firstUnprovenReason`, `PHASE_DEADLINE` и другие реальные исходы; не объявлять гипотезу фактом. Быстро оценить, можно ли существующим **законным** TEST entrypoint воспроизвести один Party-случай без запуска 51-эпизодного цикла.

## 6. Архитектурное решение
Использовать имеющийся `terminalEvidenceObserver017` и неизменённую native authority. Не создавать параллельный reward, не заполнять EXP/SP нулями по умолчанию, не выдумывать timestamps, не снимать owner/cast/timing/kill/earnings checks. `unknown` должен оставаться `unknown`. Один причинный диагноз на каждую попытку и короткий `GO / NO_GO`.

## 7. Scope изменений
Максимум **два** согласованных исходника: `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` и `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`. Только диагностика, existing observer/TEST instrumentation или точечный TEST-only bug fix, подтверждённый RED. Если достаточно read-only данных — source diff=0. Документы этой задачи и одна roadmap status append по необходимости; не изменять старые отчёты016/017. Любой третий исходник или изменение `PlayerNativeEvidence.java`, `TerminalRewardReceiptV1.java`, Party/Combat, reward rules требует отдельного owner approval — **STOP**.

## 8. Out of scope
Новая TRAIN кампания, 51/72 slot, FIT, EVAL, optimizer, production promotion, новая policy, переделка journal, архитектура классов/Party, новые пет/соски/баффы, изменение EXP/SP или вознаграждения в игре.

## 9. Изменения
При доказанной причине сначала focused behavioral RED→GREEN на фактическом predicate, затем самый маленький фикс в разрешённом TEST коде. Исправление observer не должно менять то, когда/как оригинальные evidence snapshots считываются для игрового результата. Если корень в production или missing information без lawful entrypoint — не кодировать обход, сформулировать точный блокер.

## 10. Конфигурация
Штатная `feature/phantom-world`, существующие source/test. Ни старый 016 dataset, ни флаги frozen config менять нельзя. New temporary TEST owner/scene только при существующем официальном bounded lifecycle; никаких скрытых `free resources`, remap/model repin и runtime replacement поверх protected bytes.

## 11. Производительность
Не создавать высокочастотные логи/скан огромного каталога. Не читать 1823 архива повторно, если достаточно проверенных SHA из017. Ограничения времени и единственного native опыта — `GATES_AND_BUDGET.md`.

## 12. Конкурентность/lifecycle
Перед любым native шагом удостоверить TEST owner, OFF, process/ports, независимый sandbox и точный source/JAR/protected hashes. Рабочий START/STOP только штатным существующим runner с verified exit. При невозможности этого в бюджете — `NO_GO_NATIVE`, не обходить guards.

## 13. БД и транзакции
Только read-only источники. Сохранять SQL183, TEST2, DRAFT14, Adam903/223, pointer12, Geo203/protected207; historic 016/017 journal/receipts полностью read-only. No source of truth mutation in SQL и никакого задним числом созданного receipt.

## 14. Тесты
См. `TESTS.md`. Поведенческие RED только на фактическом ошибочном пути, синтетика не равна native proof.

## 15. Команды
Использовать только focused existing tests (например, `ant -f .\\L2J_Mobius_CT_2.6_HighFive\\build.xml phantom-learning016-test`) и `jar` только при реальном кодовом изменении; весь Ant последовательно. Не запускать общий `verify`, full suite и десятки смежных release gates. Log exit codes.

## 16. Приёмка
`ROOT_CAUSE_PROVEN` только если факты показывают **какой именно** native predicate теряется и при каких условиях. `NATIVE_PARTY_REWARD_PROVEN` — только если есть настоящий новый оплаченный Party action с законным terminal reward и оригинальными EXP/SP. Допустим статус `STOP_DIAGNOSIS_NO_GO` с конкретными вариантами для владельца. Качественная STOP-приёмка лучше искусственного FULL.

## 17. Отчёт
Одно краткое `RESULT.md` (до 100 строк), при необходимости `EVIDENCE.json` без секретов, `COMMANDS.md` с точными командами и wall time. Обязательные поля: elapsed, reason by predicate, target class/actor, unknown/known, native attempted Y/N, fixes/test result, source diff, budget, preservation, next options, 12/13 и осталось1.

## 18. Commit/push
Только если есть осмысленный code/docs diff: scoped commit, обычный non-force push в `feature/phantom-world`, независимый GitHub HEAD/parent/diff и изменённые тела, ничего из protected/secret. Отдельная WIP/force/reset/clean запрещены. Если задача остановлена без изменений, достаточно записать отчёт и не симулировать commit.

## 19. BLOCKED
На границе времени, невозможной native подготовке или требуемом изменении reward semantics — прекратить новые проверки, надёжно завершить OWNED процессы и передать owner decision. Не переходить к Goal019 самостоятельно.
