# Операторский интерфейс022

Статус приёмки и реальные ограничения находятся в RESULT.json и отчёте PHANTOM-LEARNING-022.md. Наличие команд не означает FULL или разрешение на восьмичасовую кампанию. Production V4 остаётся OFF. Перед первым native обязательны physical parent188, source SQL, Geo203 и весь protected207; любое несовпадение останавливает GS.

Из корня High Five, в обычном PowerShell:

```powershell
$tr = '.\tools\phantom-runtime\training\Training.ps1'
$history = '<APPROVED_PRIVATE_TRAIN_ABSOLUTE>'
& $tr -Action ActionValue022 -ActionValue022Command Status -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Start -ActionValue022Minutes 15 -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Pause -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Resume -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Report -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Stop -Root $history -Json
```

Status/Report только читают состояние и identity; они не запускают Java, SQL или GS. Другие команды не направляются в старые V3/020/021 маршруты. Повторный Start живого controller возвращает его состояние. Detached controller использует WMI/broker admission, lifetime lock и проверяемые pid/create-time/image/nonce, продолжает после закрытия CLI, пока компьютер включён.

Pause/Stop сначала могут вернуть REQUESTED. Завершение — только после drain, OFF, отсутствия собственных процессов и свободных портов. Если controller умер, Stop использует verified identity и сохранённый process handle для native drain, затем штатный shutdown только собственной SQL. Неизвестная identity даёт STOP_UNCONFIRMED, чужой PID не останавливается. Обрыв до native complete не получает credit.

Resume продолжает тот же campaignId, deadline и immutable reservation. Complete с подготовленным checkpoint проходит повторную аутентификацию raw/row/Intent/Effect/source, отдельную cold JVM и atomic committed HEAD без второго зачёта. При повреждении lineage или source — BLOCKED. Не удалять каталоги и не повторять Start для обхода отказа.

После STOPPED/AUTO_STOPPED новый явный Start создаёт отдельный owned каталог `train022/campaigns/<id>`, копирует и проверяет полный предыдущий ledger и models, оставляет старую кампанию неизменной. `train022/active022.json` указывает на текущую кампанию. Bootstrap, оборвавшийся до первого раунда, сохраняется; новая кампания допускается только при доказанном отсутствии native/SQL.

В текущем campaign-каталоге: `campaign022.json`, `status022.json`, `report022.json`, русский `report022.md`, `reservations/`, `rounds/`, `ledger/HEAD`. Config/deadline и входные hashes запечатаны до CUT. Метрики раздельно показывают HP/MP/time, paid stock/experiment/model, n/variance, kills/death lower bound, UNKNOWN, источник, parent/candidate и split. Исторический021 и малые выборки не доказывают causal superiority.

TRAIN тактики идут по сохранённому committed step: COVERAGE, BASELINE, MODEL, EPSILON. Фактически изученные навыки выбирает AutonomousTrainingV1; native owner заново проверяет MP/cooldown/target/Geo/LOS. Каждый восьмой блок содержит предзаданные TUNING/AUDIT с BASELINE без exploration и SGD. Недостаток lawful source не компенсируется выдачей ресурсов.

Короткий smoke ограничен двумя GS boots; можно явно задать `-ActionValue022BootLimit 1` для последнего оставшегося boot. Native deadline6 минут с отдельным drain/cleanup и общим acceptance ceiling ≤8 минут на boot. CPU2.1 cores проверяется как10s delta после GS-ready; cold startup имеет отдельные ActiveProcessorCount2, memory/disk/time guards. Ночной профиль в коде ограничен96 boots и абсолютным deadline480; один GS сейчас обслуживает один episode. Повторные копирования и boot overhead могут досрочно исчерпать диск/время. Throughput за восемь часов не измерен.

После FULL и отдельного решения владельца, в будущем:

```powershell
& $tr -Action ActionValue022 -ActionValue022Command Start -ActionValue022Minutes 480 -Root $history -Json
```

Эта команда в Goal022 не запускается. CPU/memory/disk/no-data/stagnation и deadline ограничивают сеанс; Resume не продлевает срок. Windows sleep не изменяется. Порты TEST: loopback33364/18024/19324; никакого подключения к MAIN/PLAY или общей MariaDB.

Historical protected207 не перезаписывается. Отдельный `approved-protected022.json` допускается только по прямому решению владельца и содержит ровно два разрешённых текущих source pin — корневой AGENTS.md и build.xml — с привязкой к исходному manifest и старым SHA. Start сам его не создаёт. Остальные205 pins и Geo203 проверяются без ослабления; изменение нового snapshot после reservation блокирует запуск.
