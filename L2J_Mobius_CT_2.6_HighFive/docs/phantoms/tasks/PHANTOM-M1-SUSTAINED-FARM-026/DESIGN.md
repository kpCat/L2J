# DESIGN026 — непрерывная farm-миссия

## F1. Разделить farm-миссию, локальную возможность и неудачный маршрут
Долговременная цель остаётся существующей farm.background. npcId/anchor текущего
плана не становятся новым независимым combat engine. Изменение плана по-прежнему
проходит exact goal/catchup replacePlan и runtime/permit rebind.

В начале каждой новой попытки существующий Phantom-слой вычисляет одно immutable
решение на текущем exact profile/object/epoch/goal revision. Порядок:
1. Identity/epoch/admission/receipt/control. Нет обхода запретов.
2. Смерть/действующий checkpoint: передать существующему lifecycle; не трогать AutoPlay.
3. Идёт полезное native действие: не отзывать его из-за расстояния до центра anchor.
4. Не хватает ресурса: bounded RECOVER, затем возврат в тот же FARM без reset прогресса.
5. Есть **проверенная** местная native-возможность: выполнять stock gameplay,
   а не обязательно сначала walk к точной stand-point.
6. Реально нужен локальный путь: выполнить существующую navigation с bounded attempt.
7. Путь отказал: исключить конкретный witness/goal/epoch, запросить другой локальный
   план. Исчерпание всех возможностей — typed LOCAL_UNAVAILABLE + следующий bounded
   выбор/безопасный return, не permanent IDLE при CLEAR guards.

Проверенная возможность — factual spawned normal monster нужного плана, same instance,
уровень/способности допустимы, targetability/legality/LoS/native path/сухой маршрут
подтверждены. Old failed segment не может стать разрешённым под новым именем.
**Существование NPC в census не равно этой проверке.**

Отклонение nativeZ от GeoEngineZ само по себе не доказывает ни корректность, ни ошибку.
Разрешена узкая замена exact-equality gate на существующую native legality/path проверку,
если RED доказывает проходимое исходное положение и невозможность перехода между
неверными этажами/водой. Ни setXYZ, ни широкая z-tolerance без witness недопустимы.
Перс сохраняется в actual XYZ по TASK025 независимо от геометрии фонового плана.

## F2. Ресурсы и восстановление — поведение, не таймер-обход
Для mage используем реальные learned offensive skills и их native MP requirements.
Не предполагать, что MP450 — установленная причина. Сначала trace affordability,
намерения, фактический cast outcome и регенерацию, затем native RED.

При доказанной нехватке:
- Не прерывать уже оплаченный/published earned cast.
- При отсутствии атаки/угрозы позволить штатный rest/sit и регенерацию; выйти из rest
  по достижению достаточного MP для bounded малого набора casts, не ждать maxMP.
- При угрозе не сидеть бездумно: существующая безопасная локальная смена позиции или
  native допустимое действие, не массовый teleport и не forced heal/MP.
- Hysteresis и один coalesced state на epoch/revision; после выхода нужны реальные
  действия. Недостаточная регенерация получает terminal reason и recovery, не вечный wait.
- Ни REGEN evidence reset, ни расширение120s, ни fake damage/kill. При недоказанной
  возможности восстановиться в текущем плане выбрать дешевле допустимый план.
- Состояние ресурса может объяснить bounded паузу, но не освобождает от tail120 и
  sustained whole-cohort acceptance.

Новая логика по возможности внутри VisibleAutoPlay/BackgroundDecision. Не копировать
AutoUse/AutoPlay. Conditional tick hook только для конкретной доказанной интеграционной
ошибки, обычный REAL gameplay идентичен base.

## E1. N02/S12: genuine native effect отдельно от evidence
Задать deterministic input/seed, exact participants и barriers вместо случайного sleep.
Записать цепочку: HP delta → original sensor damage hook → death target generation →
reward roster → killedIfDamaged → actual EXP/SP writer. Найти первый divergence.
Проверить оба пути: полноценный original doCast с отложенным launch и legal synchronous
callSkill из N02. Не объявлять прямой skill невалидным только чтобы тест стал GREEN.

Если сломан producer native-observation: исправить hook после реального writer с exact
owner/epoch/target generation и once-only учётом. Если flawed fixture: отдельно доказать,
сохранить исходный RED и заменить только неверную предпосылку, не продуктовый invariant.
Для признания REGRESSION_PASS нужен понятный paired result, не многократные rerun до удачи.

## E2. RESERVED callback
Сначала ограниченный trace точной EVENT-цепочки: reserve, submit ACK/REJECT, due,
start, listener begin/end, complete/reject; owner/epoch/ticket и parent IDs.
Достаточно bounded diagnostic ring; в hooks нет I/O/DB/waits/чужих Player locks.
Сохранить thread dump первого timeout, не только поздний healthy stop.

Разрешённые исправления по доказанной ветке:
- неуспешная публикация: retire только точно НЕ опубликованную reservation;
- callback опубликован и ожидает: убрать конкретную starvation/lock ordering причину
  в Phantom control bridge, а не вручную исполнять его второй раз;
- body началось/упало: exact finally completion, первичное исключение не терять;
- listener законно порождает child: учитывать children до seal, не завершать parent
  как способ уничтожить долг.
Нет увеличения drain timeout и отмены EARNED «потому что долго».
Нет global pool rewiring; новые dispatch semantics для всех game events запрещены.

## R. Recovery observation
Заработанные поля нельзя сравнивать до checkpoint с SQL после минут обычной игры и
выдавать это за атомарную проверку. Установить test-only exact recovery barrier:
после commit существующего resolver, но до publish/ordinary admission выбранного
профиля. Захватить immutable after snapshot + matching completed state/context,
сравнить receipt-after native поля/skills/full inventory. Только затем отпустить
обычную работу и отдельно трассировать первый legitimate XYZ writer.

Barrier короткий, bounded, только private clone, не держит DB/Player locks во время
внешнего ожидания и не меняет заработанное. Task observer берёт данные из exact
аргумента выполняющегося пути, не сканирует других Player из locked hook.
Два actual sameDB process restart нужны; repeated SELECT не считается restart.
Продуктовая правка resolver/XYZ допускается только после fresh earliest divergence.
