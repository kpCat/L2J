# AUTHORITY060 — автономная работа, границы и сохранность

## 1. Решение владельца от18.10.2026
Владелец разрешил новый execution-пакет для продвижения к М7: автономное развитие, разные локации/штатные TP, покупка доступных сосок у NPC, покупка и крафт у offline торговцев/гномов, party role tactics и полезные питомцы. Отправленный /goal является разрешением исполнения; повторные вопросы о перечисленных действиях не нужны.

Сохраняются `M1_TECHNICAL_STATUS=OPEN`, `M1_DEVELOPMENT_BASE=ACCEPTED_WITH_DEFERRED_GATES`, `G4=DEFERRED_BY_OWNER_NOT_PROVEN`, `G6=BLOCKED_PLATFORM_DEFERRED`, `FULL_OWNER_ACCEPTANCE=DEFERRED_BY_OWNER`, `FORWARD_WORK_M2_M7=AUTHORIZED`. М2/М3 — ранее SERVER_VERIFIED в указанном scope, не owner acceptance и не иммунитет от регрессий.

В текущий RELEASE_CONTRACT добавить короткую поправку: связанные travel/supply/offline manufacture/Pet функции разрешены в060 вопреки прежнему blanket out-of-scope М8–М10. Не объявлять этим полные М8–М10 готовыми. Никаких новых обязательных критериев после результата сверх утверждённых.

## 2. Неприкосновенное
- `D:\PhantomWorld\M1-057-candidate`, `M2-M7-058-candidate`, `M3-M7-059-candidate`; их release_057a/058a/059a, персонажи, конфиги, логи и процессы. Не обновлять/запускать/останавливать даже при STOPPED.
- MAIN `C:\Users\ZBook\L2J_Mobius`, другие worktrees, PLAY/040a/041final/051a/053a/057x72/057x73, старые backups/SQL/raw — без мутаций. Общий Git store меняется лишь разрешёнными обычными Git-операциями из OWN.
- Клиент, NWindow.dll, l2.ini, hosts, реальные аккаунты владельца вне TEST-сред; OS security/network settings, общая MariaDB service/datadir/users/grants.
- Восемь orphan058t profiles698–705, отказанные удаления accounts/profiles и исторические FAIL сохраняются. Не повторять отказанную операцию через другой инструмент.
- Read-only проверка конкретного manifest/hash/process допустима. Не обходить миллионы старых файлов и не устраивать ещё одну миграционную проверку.

## 3. Разрешённые среды
Пути относительны High Five OWN, кроме нового кандидата.

| Назначение | Root | Exact schema | Право060 |
|---|---|---|---|
| Mutable fixtures | `.phantom-local/contract058t` | `l2jmobiush5_phantom_test_058t` | Новые case IDs и конечный набор TEST actors, предметов/питомцев/магазинов до измерения; повторные штатные tests |
| Накопительная интеграция | `.phantom-local/contract058a/runtime` | `l2jmobiush5_phantom_test_058a` | Обычная жизнь/save/stop/restart, новые scenarios, неразрушающие versioned migrations, без reset/reimport |
| Новый candidate | `D:\PhantomWorld\Living-060-candidate` | `l2jmobiush5_phantom_release_060a` | Новый root/schema; один consistent stopped export/import058a, затем обычный smoke и сохранение |

Не создавать060t/060a и не представлять058 среду другой схемой. Добавить continuation060 authority при необходимости; прежние `phantom.contract058.*` свойства допустимы только для реальной058 среды. Bootstrap058 не повторять. На resume проверить собственный checkpoint, не возвращаться на BASE.

Перед первыми writes: exact root/schema/role/owner, отсутствие постороннего writer, один stopped baseline058a; fixture060 IDs/account/name/начальное состояние фиксируются до теста. Новый case может создать ограниченный набор собственных native identities, вместо столкновения со старыми orphan. Существующие персонажи не обнуляются.

Разрешена подготовка TEST уровней, learned skills, gear, наличных средств, recipe/materials, pet control item и соответствующего pet state, законного offline store ДО сцены. Это FIXTURE_PRECONDITION, не заработанная карьера/экономика/эволюция. В положительной autonomous-сцене нельзя вручную выдавать по очереди learning/travel/supply goals. Во время измерения никакого доведения HP/MP/SP/Аден до нужного значения или довыдачи сосок.

Штатный адресный cleanup разрешён лишь для заново созданных в060 mutable fixture rows по собственному receipt/preimage, после завершения native owners и точной проверки IDs/схемы. Никаких удалений accounts, старых orphans, schema-wide sweep/drop/reset. При отказе cleanup — сохранить записи, зафиксировать quarantine и перейти к новому заранее ограниченному namespace TEST actors; не скрывать остатки.

Предпринимать сделки, тратить игровые деньги/материалы, покупать питомца/корм/соски, использовать штатного Gatekeeper и вступать в тестовые пати можно только на своих058 TEST actors/средах. Реальные offline торговцы в защищённых release-БД не тестовая площадка. Fixture offline merchants создаются штатным механизмом и имеют конечные запасы.

## 4. Инструменты и код
Разрешены точная адаптация И сборка существующих tests/launchers/native fixtures/builders/exporters и runtime guards к новым060 cases/JAR/root/schema. Сохранить прежние mappings058/059; добавить только проверенный explicit060 mapping с wrong-root/schema/role/JAR/layout negatives. Не удалять проверки и не вводить wildcard-допуск всех test-схем.

Новые небольшие product classes допустимы, когда существующего owner действительно нет. Сначала reuse текущих Decision/Goal/Navigation/Commerce/Economy/Party/Pet/Progression services. Число связанных файлов не является запретом: при разумном расширении указать причину в checkpoint и продолжить без вопроса. Переписывать весь движок/создавать параллельный AI или WAL не разрешено.

Разрешены native services, штатные logs/typed events и read-only in-process diagnostic snapshots собственных сред. Предпочитать их новому recorder. Ранее принятый normal-only premain recorder допускается лишь на своей058a/060 candidate с exact guards, если необходим, без fault режима; в ordinary release startup его не должно быть.

**Нельзя diagnostic Attach, включая jcmd/jstack/jmap, late debugger/injector, process kill, crash/fault G6 или клиентский transport hack.** Единственное lifecycle-исключение — существующие проверенные STOP_MONITOR/Runtime Stop и штатный shutdown-helper ровно для своей058a/060 candidate после incarnation/root/JAR/schema guards. Это не право диагностического Attach. Ошибки SQLException/rollback/конкуренции в своей test-JVM допустимы без аварий ОС/БД.

При platform refusal не маскировать запрещённое другим именем. Уменьшить/сменить разрешённый шаг можно, но не выполнять тот же отказанный эффект обходом. Никаких обещаний, что платформа обязана позволить операцию.

## 5. Конфиги и порты
058a сохраняет Login2138/Game7838/control9138. По локальной таблице ОС сначала убедиться, что endpoints свободны и не принадлежат защищённой058-копии. Не закрывать чужого владельца порта.

Новый060 candidate: предпочтительно `Login2106/Game7840/control9140`, если2106 свободен и защищённые057/058/059 не работают; иначе `Login2140/Game7840/control9140`, если свободны. Выбранный exact layout закрепить в одном manifest/BUILD_INFO/config/guard и перепроверить перед START. Никаких port-forward/сетевых проб/изменений клиента. При fallback честно указать ограничение клиентского подключения.

Исходный development-профиль остаётся population32/ACTIVE8/materialized16. Небольшие TEST offline sellers и питомцы должны учитываться в реальных ресурсах. Если сцена с9 членами/торговцами не помещается, сначала проверить состав/полезные owners; допустим заранее объявленный отдельный TEST профиль materialized24/ACTIVE8 при валидаторе и измеренных ресурсах. Это не починка G4, не default для старых сборок и не нагрузочный М10. Production не должен заставлять покупку зависеть от повышения cap.

Новые policy defaults — ограниченные, документированные и только в новых средах/ordinary datapack. Native рейты, цены/рецепты, geodata, время, skill costs, боссы/minions, instance reuse не подгоняются под PASS. Ленивая дематериализация сохраняется; нельзя насильно выкинуть боевого персонажа ради слота. Подтверждённый связанный lifecycle defect можно исправлять адресно без нового М1-soak.

## 6. Питомцы и согласие людей
Действия с Pet/BabyPet разрешены для принадлежащих TEST/Phantom pet-control items. Только текущие поддержанные вид/уровень/навыки/корм/состояние. Не выдавать петам чужие роли или навыки и не лечить всю Party через pet, если native лечит только владельца. Обычных human pets не ломать.

Rift/телепорт/покупки, списывающие ресурсы настоящего игрока, требуют явного scoped consent: instance/цель, стоимость или верхний предел, срок и текущий roster/generation. Уже стоящий в Party человек не считается автоматически согласившимся тратить предметы. Пользовательская фраза согласия может идти через существующий M3 ingress; TEST партнер подаёт своё явно записанное согласие. Не выдумывать согласие владельца во время его сна.

## 7. Git, время и финал
Разрешены status/diff/log/rev-parse/merge-base/ls-remote, exact `git add -- <paths>`, содержательные commits, `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` без force и clean `git archive`. Detached HEAD допустим. BASE должен совпасть при старте; при resume — быть предком своих060 commits. Чужие изменения не затирать; никакого reset/clean/rebase/amend/force/новой ветки/worktree.

Публиковать по законченным проверенным owner-функциям, не по каждому тесту и не после всех14ч одним коммитом. Незавершённое сохранить patch+preimage; адресно снять свой неопубликованный фрагмент можно только с проверкой отсутствия чужой правки. Сломанный код не публиковать ради tracked-clean. Не добавлять чужие untracked.

Бюджет14ч с резервом max90мин/измеренный остаток проверки и поставки. Порог10–12ч — ожидание возвращения владельца, не основание остановить полезную работу. Уже законченный delivery step повторяется лишь при изменившемся input. В конце свои058 и новый060 штатно STOPPED; один RESULT, один checkpoint, одна coverage-матрица; старые057/058/059 нетронуты.
