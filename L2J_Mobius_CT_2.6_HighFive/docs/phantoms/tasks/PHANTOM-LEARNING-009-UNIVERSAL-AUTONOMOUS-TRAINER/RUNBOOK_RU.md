# Запуск и управление TRAIN009


/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER

Статус: **PARTIAL009_REAL_FIT_NO_TUNING_IMPROVEMENT_EVAL_AND_NATIVE_LIMIT_FAILED**. Final manual gate **REQUIRED**. FULL009 не подтверждён.
008 остаётся PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT с отдельным непринятым gate.
Исходный HEAD85b1c3558e35c1df0a7625fcf5a9e5c119c18b96; только wip/phantom-learning-009.
Exact scope39:25source/config/test и14delivery. PLAN38 и39-й Party path явно утверждены владельцем.


В существующем TEST-клоне откройте `L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/TRAIN_GUI.cmd` двойным щелчком.
Codex для работы Director не требуется. Launcher создаёт отдельный WinForms EXE, а GUI обращается к постоянному CLI.

1. «Состояние» запрашивает текущие owner/PID/heartbeat, фазу, данные и checkpoints.
2. «Учиться» делает явный opt-in и запускает отдельный Director под installation lock. Два окна используют одного владельца.
3. «Пауза» сохраняет безопасную границу; «Стоп» штатно завершает только свои workers/GameServer/SQL. Дождитесь PAUSED/STOPPED и подтверждения свободных портов.
4. «Продолжить» запускает новый Director и восстанавливает ту же durable campaign/terminal Adam. Старые finished/reserved episodes не разыгрываются повторно.
5. Окно GUI можно закрыть: его закрытие не посылает Stop. Повторный запуск получает состояние через CLI.
6. «Экспорт» при работающем Director ставится в очередь до остановленной границы; готовность проверяется по export-result.json.

**Фактическая приёмка GUI:** PASS:6реальных handlers,2окна/1Director,фактический GUI exit и продолжившийся GS game.ready/socket; WINFORMS_PERFORMCLICK_NOT_DESKTOP_INPUT. Physical human click и Visual Studio Designer round-trip NOT_RUN. Computer Use прекращён по предпочтению владельца; GUI integration использует собственный opt-in канал настоящего WinForms EXE и Button.PerformClick, без desktop input.

На acceptance profile действует явно расширенный владельцем предел10GS/90native-минут. Ранее использованные6starts/2231818ms сохранены. Не удаляйте journals/config, чтобы обойти бюджет, pinned JAR или owner guards.
Обычный профиль рассчитан на `autonomous009DurationMinutes=1440`, `autonomous009CycleLimit=0`, classes/levels=auto, с checkpoint/resource/no-data/stagnation limits, но суточная работа не проверена и готовность ночного обучения не заявлена.
Рабочий config находится в `.phantom-local/learning002/train/TRAIN_CONFIG.ini`; изменять только после подтверждённой остановки с сохранением byte backup.
Fault injection — только acceptance; оставлять её в обычном ночном профиле запрещено.
Фактическое состояние: Director STOPPED, campaign autonomous009-goal009-20261114-f, cumulative 10starts/5803240ms, pending=False. Итоговые OFF/fault/ports guards см. postflight в EVIDENCE. Ночные24h не проверены; nightReady=false.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Status -Json
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Start -Autonomous
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Pause
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Stop
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Resume -Autonomous
powershell -NoProfile -ExecutionPolicy Bypass -File .\Training.ps1 -Action Export
```

Collection/heartbeat/progress checkpoint не означают обновление весов. Для нового fit нужны16TRAIN_B+8TUNING+8AUDIT, независимые matched TRAIN_A controls,≥2classes/2scopes и фактически оплаченная альтернатива. Недостаток данных сохраняется как COLLECTING/INSUFFICIENT_EVIDENCE.
Selected weights и terminal optimizer weights различаются. Продолжение использует terminal Adam, не склейку selected policy с чужими moments. DRAFT не применяется автоматически в accepted TEST или MAIN/PLAY.

При WAIT_GROUP_DAMAGE прочитайте party-admission.json и planning-refusals.json: законный cast не гарантирует поддерживаемый planner envelope.
При STALE/UNKNOWN heartbeat не считайте один живой PID доказательством прогресса. При owner/PID/image/port/hash refusal не останавливайте чужой процесс и не удаляйте evidence.

## Финальный итог по указанию владельца

**PARTIAL. Задача закрывается без новых GS, опыта, обучения и увеличения бюджета. FULL009 не достигнут; final manual gate REQUIRED.**

Получен реальный autonomous SQL checkpoint DRAFT14: Adam887/207→903/223,48applications/16gradient episodes,3classes. Холодный rollback до COMMIT и recovery после COMMIT проверены; после EVAL raw weights/Adam/apps совпадают, повторного credit нет. TUNING не улучшился, best selected epoch0; selected14 — прежние terminal12 weights, новый terminal Adam14 другой. AUDIT MSE14=0.014208027 против collection teacher12=0.005066568; улучшение поведения не доказано. Завершённых полных F cycles0: EVAL и graceful exit сорвали переход к cycle CHECKPOINT. Долговечность нового SQL checkpoint подтверждена cold export.

Независимый EVAL GS10:0lawful cells/222planning refusals/0episodes/0pairs; evaluation.complete отсутствует. batch.complete подтверждает trainingStateUnchanged=true, cold export подтверждает отсутствие градиента. После budget stop получен STOP_UNCONFIRMED; JVM позднее вышел сам, identity guard отказал до Kill. Финально STOPPED/ports0/OFF. Списано консервативно5803240ms=96.720667min при разрешённых90; эта верхняя оценка включает задержку recovery после выхода. Точное время native exit не доказано; строгий90min gate FAILED. Новых GS/сбора/обучения после указания владельца не выполнялось.

Итог безопасности: own Director/native/worker/SQL отсутствуют; порты33357/18001/19301 свободны; Geo203/protected207 exact hashes PASS. Config OFF, обе fault flags и GUI hold выключены; shutdown wait возвращён180s. Accepted TEST2 и MAIN/PLAY не менялись. Аварийный Kill не выполнялся: перед действием exact identity guard вернул PROCESS_UNAVAILABLE, JVM уже завершился; затем штатный Stop закрыл свой SQL.

Консервативный счётчик5803240ms включает время до pending recovery, в том числе задержку после фактического выхода JVM. Это не точный замер96.721мин активного JVM. Точное время выхода не установлено; соблюдение90мин не доказано, budget gate FAILED. Счётчик сохранён, не обнулён и не увеличен разрешённый ceiling. Starts10/10, pending=false. Нативных повторов не было.

Cold SQL working14 подтверждён двумя независимыми экспортами до и после recovery/EVAL: raw selected/Adam/targets/apps/labels совпали;48applications,16gradient episodes,3classes. Исторические117строк моделей/Adam/apps1–12 дали прежнийSHA E0BD464544A67AC4D14292DED4BF32EFBF82F6DDF86E2D4937264929063F2309. Отрицательные raw outcomes и legacy settlement flags не переписаны; фактический training credit определяется SQL applications.

`cold-checkpoint.json` содержит последний завершённый cycle pointer model12; после срыва EVAL фаза cycle CHECKPOINT не достигнута. Это отдельный незакрытый аспект статуса; долговечный SQL checkpoint14 и его terminal Adam903/223 проверены холодным экспортом, pointer вручную не переименовывался.

Незакрыты G2/G3/G4/G6/G7: неполная lawful coverage, нет двух завершённых обученных cycles, STOP_UNCONFIRMED,0EVAL pairs и провал native90-limit proof. G0/G1/G5 подтверждены в указанном bounded scope. G8 завершается только обычной публикацией009 и независимым GitHub readback. Низкий MSE и факт Adam updates не считаются улучшением поведения. VisualStudio Designer round-trip и24h soak NOT_RUN; nightReady=false.
