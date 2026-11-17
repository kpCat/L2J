# Goal022 — пользовательский запуск, после успешного FULL и ручной приёмки

**Это контракт интерфейса, который должен реализовать Codex.** До завершения Goal022 команды не гарантированы. Parent V4 TEST, а НЕ игровая production модель. Никакого автоматического запуска ночью после commit.

Из корня модуля в PowerShell:

```powershell
$tr = '.\tools\phantom-runtime\training\Training.ps1'
$history = '<АБСОЛЮТНЫЙ_ПУТЬ_К_ПРОВЕРЕННОМУ_PRIVATE_TEST_train>'
# Сначала Status / preflight без GameServer:
& $tr -Action ActionValue022 -ActionValue022Command Status -Root $history -Json
# Запустить ровно 480 минут (позднее, после отдельного решения владельца):
& $tr -Action ActionValue022 -ActionValue022Command Start -ActionValue022Minutes 480 -Root $history
& $tr -Action ActionValue022 -ActionValue022Command Pause -Root $history
& $tr -Action ActionValue022 -ActionValue022Command Resume -Root $history
& $tr -Action ActionValue022 -ActionValue022Command Report -Root $history
& $tr -Action ActionValue022 -ActionValue022Command Stop -Root $history
```

`Start` подключается к уже живому owned Director, не создавая второго. `Resume` продолжает тот же campaign/deadline и last committed checkpoint, без сброса лимитов. `Pause` и `Stop` могут сначала вернуть `*_REQUESTED`, а потом `PAUSED`/`STOPPED` после native drain; Status никогда не запускает GS. Если PowerShell/GUI закрыть — отдельный owned controller продолжает до дедлайна/остановки, если компьютер включён и Windows не уснул.

**Status/report в private TEST:** `status022.json`, `report022.json`, `report022.md`; показывают elapsed/deadline, scene/episode, последние seal и checkpoints, `globalUpdates`, segment/localUpdates, тренировочные и контрольные paid actions, эксперимент vs stock vs MODEL, средние DAMAGE/MP/time, kill/death, отказ/UNKNOWN, confidence/sample-size, остановку и проверенный OFF. Пути и команды хранить в отчёте Goal022, без секретов.

Если `PAUSED_NO_DATA`, `RECOVERY_REQUIRED`, `BLOCKED_*`, `STOP_UNCONFIRMED` — не удалять каталог/не повторять Start многократно. Сначала `Status`/`Report` и причина. Не называть дополнительными обновлениями модель188 или старый 021.

После FULL можно **отдельно** согласовать восьмичасовой владелецкий TEST-прогон. `nightReadiness=TEST_ONLY_OWNER_TRIAL` означает подтверждённый короткий цикл и safety, но **не** уже проведённые восемь часов и **не** статистически подтверждённую победу над stock.
