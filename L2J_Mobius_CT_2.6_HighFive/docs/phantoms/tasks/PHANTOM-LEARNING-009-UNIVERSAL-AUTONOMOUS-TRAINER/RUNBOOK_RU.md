# Запуск и управление TRAIN009


/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER

Статус: **PARTIAL / INSUFFICIENT_NATIVE_EVIDENCE**. Final manual gate **REQUIRED**. FULL009 не подтверждён.
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

**Ограничение фактической приёмки:** реальные нажатия кнопок не проверены. Пользователь попросил прекратить Computer Use; дальнейшие проверки выполнялись через CLI, исходники и журналы. Компиляция WinForms и чтение настоящего окна не заменяют GUI/manual gate.

На acceptance profile действует суммарный предел6GS/90native-минут, который не сбрасывался между кампаний. Не удаляйте journals/config, чтобы обойти бюджет, pinned JAR или owner guards. Это не разрешение на дополнительные acceptance starts.
Обычный профиль рассчитан на `autonomous009DurationMinutes=1440`, `autonomous009CycleLimit=0`, classes/levels=auto, с checkpoint/resource/no-data/stagnation limits, но суточная работа не проверена и готовность ночного обучения не заявлена.
Рабочий config находится в `.phantom-local/learning002/train/TRAIN_CONFIG.ini`; изменять только после подтверждённой остановки с сохранением byte backup.
Fault injection — только acceptance; оставлять её в обычном ночном профиле запрещено.
Фактическая передача: private config OFF, обе fault flags=false, campaign D и счётчик6/2231818ms сохранены. Новый ночной campaign не настроен и не запущен; nightReady=false. Исчерпанный acceptance profile нельзя выдавать за готовый ночной запуск.

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
