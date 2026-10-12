# ACCEPTANCE040 — неизменная продуктовая граница M1

## Исходные условия

Ни средний процент успешности, ни количество тестов/часов/коммитов не являются M1. Общий PASS требует всех применимых обязательных строк одного финального кандидата. Используемая версия CONTINUITY_V2 остаётся byte-exact исходной; старые FAIL сохраняются.

| Gate | Конкретное условие PASS040 | Не является PASS |
|---|---|---|
| G0 INPUT | Exact BASE/OWN/remote, isolation, protected snapshots, исходный input package, точные current runtime identities и собственные040 schema paths; чужие данные не изменены | Вывод039 о Java0 вместо нового census; работа вMAIN/чужом worktree |
| G1 ACTION | Реальный обход→native damage→≥5cycles отдельно melee и caster; применимый native loot; stale/instance/busy/no-route negatives; нет actor/zone hardcode | Только autoPlay=true; короткий удачный caster вместо обеих ролей; выданный fixture loot за natural |
| G2 GROUP | Две независимые естественные группы6–8, все enrolled проходят исходный CONTINUITY_V2; primary≥5cycles, остальные≥2 плюс все исходные критерии rest/debt/tail/counters/epochs | Удаление failed actor; замена epoch; новый cohort вместо выяснения причины;6/7 как «почтиPASS» |
| G3 SAVE | Все births соответствующего stopped run учтены, exact PREPARE/native/FINALIZE, full items/skills/state/PNC на своей commit boundary; последующие версии связаны реальными producer/hash/version; global COMPLETE/retained0 до pools stop | Process exit0; позднийSQL без lineage; потерянный ring sample; искусственный native seal |
| G4 LIFE | Native death→legal recovery→полезная native работа; отдельно soft-away→настоящий productive background→тот же profile→полезный native return; нет lost epochs/двойных rewards | READY без действий; фон без заработка в productive case; healing/setters; жизнь другого заменённого profile |
| G5 RESTART | Два actual normal sameDB restart текущего кандидата без reset/import; state/context/items/skills/goal и reward producer-chain сохранены; есть useful resume и нет повторного начисления | Два запуска на разных freshly importedDB; roundtrip codec вместо actual boot; отсутствие логов как «значитнеизменно» |
| G6 CRASH | Оба существующих72/73 окна на final candidate, actual recovery и дополнительный normal restart на каждом для idempotence; canonical nativeXYZ/earned/store согласованы; отдельное разрешение на новые crash получено | СтарыйOS72, unit model, fault-OFF replay одного окна, незапущенный73, молчаливое снятие запрета |
| G7 REGRESS | Исходные93/93 и11/11 и все затронутые защищённые/новые tests проходят jar-first на final product classes; native action/lifecycle реально проверены | ДругойJAR в test classpath; исключённые старые asserts; зелёный compile без runtime |
| G8 DELIVERY | Полный self-review diff, exact commit+normal push и проверкаlocal=remote, читаемый компактный архив; только allowlisted здоровый final/preview runtime оставлен живым; DB/worktree/raw сохранены; user handoff | OwnJVM0 как универсальная цель040; runtime выключится cleanup/TTL; неподтверждённыйpush; гигантская бессмысленная копия039 |

G8 изменён явно по последнему требованию пользователя: окончившиеся тестовые JVM должны быть остановлены, но финальный сервер должен продолжать работать. Это не отмена G3 stopped-cut — он выполняется в завершённом episode перед final live boot.

## O040_SERVER — обязательная готовность существующего observer

Это дополнительное продуктовое требование пользователя, не новое семейство «метадоказательств». По возможности проверяется внутри тех же episodes.

| Пункт | Критерий |
|---|---|
| O1 Autonomous | Без REAL пассивный observer продолжает существовать и показывает truthful mode; Synthetic не учитывается как Phantom и не выдаётся за REAL |
| O2 Binding contract | Existing owner корректно обрабатывает arrival/logout/reconnect и игнорирует old connection event; проверено отдельными корректно маркированнымиfixtures |
| O3 One focus | При switching не остаётся ложный второй human anchor; нет дублирования materialization/earned/background owners; реальные другие люди продолжают учитываться |
| O4 Lifetime | Finite Synthetic action-session/heartbeat не управляют временем жизни acceptance server; после завершения Codex/контролирующегоwrapper пассивная готовность сохраняется |
| O5 Native guards | Не расширены525s/cap5/consent/actionTTL и другие защиты ради обхода; используются существующие observation/lifecycle seams |

`O040_SERVER=PASS` не означает фактический вход REAL. `ACTUAL_REAL_BINDING=NOT_RUN` до клиентского финала. В REAL_FINAL нужно повторно подтвердить реальные O2/O3 на настоящей сети, а не только fixture-модель перехода.

## REAL_FINAL — последняя фактическая ступень

Все пять условий обязательны:
1. Реальный клиент прошёл actual Login/EnterWorld в том же frozen product candidate; сервер это подтверждает без synthetic/fake flags.
2. Observer автоматически привязан именно к текущей actual REAL session/instance; не управляет игроком и не держит двойную ложную locality.
3. Пользователь лично видит осмысленное native движение/бой/смену целей/нормальные восстановления, без устойчивых зависаний и неприемлемого pop-in/flicker. Native server events согласуются с наблюдением.
4. Одна ограниченная проверка ухода/возврата или согласованный эквивалент не ломает same-profile continuity и наблюдение; пользователь не подменяет собой всю QA-кампанию.
5. Пользователь явно подтвердил приёмку. Codex не пишет это подтверждение от его имени.

Успешный actual login без пользовательской оценки видимого поведения — не полная приёмка. Непроведённый финал всегда NOT_RUN. Известный defect не переводится в «minor accepted», если пользователь этого не подтвердил и исходный gate ему противоречит.

## Обязательные регрессии

Сохранить все исходные asserts, а не только суммарное количество:
- Jar-first matrix93/93 и lifecycle11/11;
- native0369, Q15, inventory0356, action0385, debt0372;
- projection/capacity, A02, boundary2, snapshot1, death-useful1;
- source/helper23/23 и native verifier positive+3negatives4/4 — если соответствующий helper/verifier используется в final evidence;
- новые причинные negative tests040 и дополнительные callers/suites изменённых компонентов.

Числа означают зафиксированные039 baseline suites, не требование искусственно оставить число tests неизменным. Новые тесты увеличивают totals; старые нельзя выключать. Реальные классы/runner commands определить по опубликованным suites/локальным receipts, не придумывать имена по этой сокращённой записи.

## Конечные статусы без лазеек

`SERVER_ALL_PASS = G0..G8==PASS AND O040_SERVER==PASS`.

- `SERVER_ALL_PASS && REAL_FINAL==NOT_RUN`: `TASK_RESULT=M1_SERVER_READY; M1=WAITING_REAL_FINAL`.
- `SERVER_ALL_PASS && REAL_FINAL==PASS`: `TASK_RESULT=M1_CLOSED; M1=CLOSED`.
- Любой `FAIL/NOT_VERIFIED/BLOCKED_AUTHORITY`: `M1=OPEN`; полезные части сохраняют свои отдельные статусы.
- G6 недоступен из-за действующего запрета: `G6=BLOCKED_AUTHORITY`, не SKIPPED_PASS/WAIVED/N/A. Прочие recovery результаты — отдельные ограниченные факты этой строки.
- Runtime здоров, но server gates неполны: `RUNTIME_MODE=READY_FOR_PREVIEW`; это не M1_SERVER_READY.

Нельзя изменить эти формулы одним отчётным commit или объединить successes разных кандидатов, чтобы получить общий GREEN. Связанные production/config changes требуют соответствующей повторной проверки. Незатронутые immutable данные не надо перепроверять повторно только из-за doc-only commit.
