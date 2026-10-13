# SCENARIOS041 — конечные native проверки

## 0. Один final acceptance set

В ops041/RUNTIME041.json записать candidate CODE_COMMIT/JAR/config+catalog hashes/evaluator hash, exact DB/root/PID/startTime/ports, episode, initial identities, UTC+monotonic границы. Это переиспользование существующего manifest, не отдельная доказательная система.

Обязательный набор: GROUP_A, GROUP_B, LIFE, NORMAL_RESTART_1/2, CRASH72+RECOVERY1+IDEMPOTENCE2, CRASH73+RECOVERY1+IDEMPOTENCE2, release runtime smoke, реальный финальный вход. Совмещать GROUP_A/LIFE/RESTART и их cuts при сохранении отдельных начал/концов, не дублировать одинаковые запуски.

Два G2 cohorts допустимо иметь с пересечением IDs, но независимость должна быть настоящей по locality/history/schema, не переименование одного удачного run. До исхода фиксировать живые фактические предусловия; после enrollment никого не подменять. Baseline population/rates/radius/45–90–120 не меняются.

Final test-only faults/negative fixtures не смешиваются с natural claims. Старые invalid records сохраняются, но полнота041 измеряется внутри заранее объявленных final episodes, а не за всю историю проекта. Любой unresolved real product FAIL на этом же candidate блокирует финал.

## S_ACTION и S_GROUP_A/B

Сохранить CONTINUITY_V2 byte-exact и исходную команду с~380s окном. В каждой группе6–8: primary melee/caster≥5 законченных native cycles, остальные≥2; старые требования к damage/rewards/rest/debt/хвосту/epochs действуют. Actual native обход препятствия→damage/cycles у обеих ролей; исходный blocked-route fixture допустим как отдельно помеченная geo-проверка. Применимый natural loot подтверждается actual acquired item/штатным autoloot, не видимой анимацией.

Short negatives: stale/dead target, instance/floor mismatch, busy genuine cast, inaccessible target, outdated route generation. Не отменять earned callbacks ради нового маршрута. Shared targets и cooperative farm не запрещать искусственно.

Отдельный diagnostic срез idle: target candidates и причины отказа, native intention/skill/resources/progress, current level/EXP/SP/items/consumables. Слишком дальняя цель или no-route сами по себе не баг; необоснованный вечный idle при законной альтернативе — bug. Не вводить карьерный AI/магазины для прохождения этого шага.

## S_LIFE — один полный productive away и возврат

До away выбрать живой natural profile с настоящим native progress и пригодным background goal. Зафиксировать profile/object/epoch/instance, актуальное state/goal/context/FULL inventory/skills и owning pending work. Выбор исхода не допускается; предварительная проверка допустимости разрешена.

1. Отвести native Synthetic/locality anchor штатным доказанным маршрутом, не teleport-heal. Пассивный autonomous fallback не должен незаметно оставлять второй anchor в исходной зоне. Не подделывать REAL presence.
2. Дождаться мягкого release по existing pins/hysteresis, earned completion, native FINALIZE/commit; подтвердить отсутствие одновременных writers. Не вынимать hard pins руками.
3. По настоящему прошедшему времени получить реальное полезное background изменение с producer/operation identity, а не только sleep90/statusBACKGROUND. Если due time/eligibility законно не позволяют работу, выявить причину и скорректировать детерминированные предусловия ДО нового measurement, не повторять lottery.
4. При death сохранить заработанное и законный loss, довести этого же profile через штатную recovery. Простой replacement следующей READY личностью запрещён.
5. Вернуть locality по штатному маршруту; тот же stable profile, legal новая epoch, отсутствие копии/двойной награды; возобновление полезного native damage/cycles. Проверить state/items/skills/goal continuity на соответствующих boundaries.

Один здоровый полный positive S_LIFE + focused negative hard-pin/stale ownership достаточны; не плодить десятки копий одной истории. Отдельно actual45s native death→stock recovery→полезное действие по существующему fixture. При naturally случившейся death необходимые факты допускается переиспользовать без ложного объявления fixture natural.

## S_RESTART — G3 и G5 совместно

Одна041a DB, два consecutive normal restart без reimport/reset. Первый original cohort заранее подписан; в cut включаются ВСЕ births за проверяемый run, даже не enrolled в G2. При переходе между boot scope не теряет профили, которые не материализовались заново.

Каждая граница: штатный COMPLETE/retained0 до остановки пулов → actual process exit → consistent canonical stopped SQL без writers → boot той же DB → native restore FULL payload/items/skills/context/goal/XYZ → полезное продолжение → следующий normal stop. До допуска фоновой новой работы известные pending recovery согласованы существующим protocol.

Поздний version/hash с real producer chain допустим, одинаковая version с иным payload — conflict. Изменившийся goal объяснить actual committed writer; не требовать необоснованно неизменного ephemeral runtime stamp, но не исключать durable семантику. Каждая уже учтённая reward operation не повторяется после restart; accounted pending/receipt не висит бесконечно.

Проверить полные fields/fulls items/skills и правила reconciliation, не только общий XP balance. Смерть/consumption/loot/новый goal учитываются причинно. Snapshot SQL на работающем другом boot не заменяет старый committed cut.

Recorder cap512MiB не повышать. Исправить задержку wrapper/удержание лишних listeners, завершать capture/stock stop в рассчитанном времени. Closed batch stream с read-back и одним footer вместо тысяч файлов. Пропущенный RUN_END обычного stop — NOT_VERIFIED и починка capture; не дописывать его задним числом.

## S_CRASH72 / S_CRASH73

Точные existing window definitions/fixtures/hook/controller читать по индексам032/039 и local ops; в read-first записать конкретные method/boundary, до и после какой durable операции находится crash. Не предполагать, что72/73 — произвольные точки с одинаковыми гарантиями.

На каждой exact041x schema:
1. Final production JAR, согласованный исходный input, real native earned work и pre-enrolled identity. Положительный guard и negative wrong-root/PID/schema до arm.
2. Existing разрешённый hook → реально достигнуто нужное окно → точечный abrupt GameServer exit. Не использовать сборку с отличной игровой семантикой ради crash.
3. Зафиксировать доступные durable preimage/boundary/exit факты; не выдумывать успешное сохранение RAM после смерти процесса. Выполнить обещанные исходным protocol гарантии earned work и явно разграничить committed/pending/uncommitted.
4. Fault OFF первый boot той же schema; восстановление до normal admission. Canonical state/context/goal/full items/skills/XYZ/operation lineage; отсутствие двойного writer/reward и корректное разрешение pending.
5. Второй sameDB boot с fault OFF: idempotence и реальное useful native продолжение. Stock stop/drain; файлы/DB сохранены, injector disarmed.

Оба окна обязательны. Старый039 actual crash или unit имитация не заменяют041. Нет normal RUN_END в аварийном run — ожидаемо; нет доказательства reached window/recovery — НЕ ожидаемо и не PASS. Normal recovery stops обязаны завершаться корректно.

## S_RELEASE / S_OBSERVER_SERVER

Tracked-input build, новая копия поставки не в .codex, явные local configs, exact private DB/ports, нет test-only agents/fault mechanisms. START→STATUS→STOP→START в новом041final, все действия scoped. Сохранение перед STOP штатное. Во время теста040a не останавливается и не переписывается.

Проверить статус при завершении управляющего shell и за пределом525s Synthetic lease: native server/пассивный observer продолжают жить без heartbeat агента. Таймеры активного Pilot/Synthetic не продлевать. Не запускать тестовые collectors на всё время ожидания человека.

Для server fixtures: один focus, REAL candidate current-session affinity, stale disconnect не выключает новую connection, instance switch, normal-controls non-interference; existing original Pilot15/15 сохраняется. Это не подмена actual client gate.

## S_REAL_FINAL — последний этап человека

Только после остальных PASS. Человек входит в новую041final через отдельный проверенный профиль. Нет просьбы перед этим остановить040a. Показать обычные melee/caster farm, подход/смену целей/rest, отсутствие выраженного stuck/pop-in. Пользователь свободно двигается, общается с NPC и использует обычные действия. Без Zariche.

За10–15минут: коротко наблюдает спот, отходит/возвращается, один раз выходит из клиента/сессии на~1минуту и входит. Из собственного bounded transition output/status подтвердить actual REAL→AUTONOMOUS→REAL на новой connection, правильные XYZ/instance, отсутствие duplicate focus и interference. Captures организовать заранее без коротких JDI окон и бесконечных просьб «ещё2клика».

Явное подтверждение пользователя необходимо; сам вход/фраза «вижу ботов» не равна принятому М1. Если человек ещё не ответил, остановить агентский polling и завершить turn WAITING_REAL_FINAL, оставив041final живым. Подтверждение и документирование продолжаются в041. Сервер не выключается по таймеру goal.

## Граница G3 и неинструментированной поставки

All-birth G3 scope охватывает заранее объявленные native GROUP/LIFE/RESTART и normal recovery/idempotence boots. Каждый их normal stop закрывается полным stream/canonical cut. R1 operator smoke и последний живой acceptance boot — отдельная проверка обычной поставки без test-agent: нужны actual штатный drain/stop и успешное восстановление/запуск, но не новая полная forensic-кампания для всех её будущих births. Это разделение записывается ДО запусков, не после неизвестного результата. Реальный выявленный save/gameplay defect в поставке всё равно блокирует закрытие.
