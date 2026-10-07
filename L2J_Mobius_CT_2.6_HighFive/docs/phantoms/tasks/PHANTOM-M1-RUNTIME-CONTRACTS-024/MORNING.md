# MORNING024

M1=OPEN. TASK_RESULT=FAILED; all-server acceptance не пройден.

Reviewed GameServer SHA256: E2278F7E782A374CDC9932A43CE9F2F194359B48CD0814070CC97F5555C41D2A.
Reviewed LoginServer SHA256: D19D55B7AD0926A8BF706866A3C7B9EDD9062B68791B08937888AE84DA5C6059.
Config/data fingerprint: BC61EC50FCA9C1E266F86DE3C059771AA6CB1EE2E72AC7582661A9C90D876D26 (25767 files).
Preserved runtime: `C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract024c\runtime`; DB `l2jmobiush5_localplay_contract024c`, loopback MariaDB3308. a isolated TEST and b probe также сохранены; d..h не использовались. PLAY/kpCat/night023 не менялись. Runtime и private credentials не входят в Git.

Проверенная команда запуска, реально использована для episodes6/7/8 и ValidateOnly после остановки:

```powershell
& 'C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\Start-Reviewed024.ps1' -Background
```

Команда проверяет frozen SHA/JAR/config/data, использует тот же preserved clone и stock Start-LocalPlay. Она не обновляет runtime до documentation publication SHA. Сейчас default own JVM/listeners0; последние Game27704/Login19516 остановлены stock graceful, force0. При будущем human TestAdmin login используется существующий native REAL autoattach; такой login в этой задаче не выполнен.

Перед ручной client проверкой учитывать natural cohort, native drain и pending452 blockers из RESULT.md. Pending receipt сохранён специально; команда запуска не удаляет его и не восстанавливает rewards из diagnostics. Highest truthful status остаётся OPEN.
