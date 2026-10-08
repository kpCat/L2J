# MORNING026

По умолчанию все собственные JVM STOPPED. TASK026 завершён FAILED, M1=OPEN. Preserved same DB и runtime предназначены для воспроизведения фактов [RESULT](RESULT.md), а не для запуска неизвестного patch. Ничего автоматически не стартовать и не reseed.

Own detached worktree:
`C:\Users\ZBook\.codex\worktrees\m1-sustained-026\L2J_Mobius`.

Frozen product/source SHA:
`cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63`.
Final artifact publication является потомком R6 без product/test изменений. Не выполнять Update ради замены runtime manifest SHA на docs commit.

GameServer.jar SHA256:
`9035F814805E0C44203BF9FA997CF5E630DCF6D63709D80A03A988D2F9F79C69`.
LoginServer.jar SHA256:
`6D7B1C5EC3E82EE8704B0E50EF73D12041EB257350396E1204C27C12649F1B5E`.
JDK25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin`.
MariaDB11.4:127.0.0.1:3308. Config/stock assets/geodata hashes сохранены в [FINAL_R6_RUNTIME_HASHES](evidence/FINAL_R6_RUNTIME_HASHES.json). Credential files остаются только в private runtime.

Preserved clones: contract026a TEST, b earlier native attempts, c finalB+two actual restarts, d finalA+two actual restarts, e R4 crash/recovery, h R6 cold452+two actual restarts. f/g не созданы. Source PLAY snapshot не импортировать повторно; old024/025 только read/export.

Следующая команда фактически использовалась для final d restart1/2 с этим exact R6 manifest/JAR и same DB. Она стартует собственный LoginServer и полный GameServer без Synthetic/REAL client; existing Start wrapper проверяет own DB/source/JAR hashes и штатный ownership guard. Выполнять только по новой задаче и после проверки свободных7777/2106/9014.

```powershell
$task026 = 'C:\Users\ZBook\.codex\worktrees\m1-sustained-026\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-SUSTAINED-FARM-026'
& (Join-Path $task026 'Control026.ps1') -Action Start -Episode d -ExpectedSha 'cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63'
```

Для воспроизведения finalB используется та же проверенная форма `-Episode c`; same DB contract026c сохраняет reconciled состояние после restart2, первый failed shutdown raw log неизменен. Повторный запуск не восстанавливает отсутствующий final SEALED proof.

Проверенная штатная остановка собственных JVM:

```powershell
& (Join-Path $task026 'Control026.ps1') -Action Stop -Episode d
```

Episode должен совпадать со стартом. Нельзя останавливать чужие PID или запускать два episode на общих портах. Exit helper/физический STOPPED и healthy Phantom drain проверять отдельно: finalB показал их различие. Сохранённые STALE_RECORD — отсутствие живого marked PID, не разрешение на kill случайного PID.

Original026 cohort:110,142,175,260,275,278,404,447. В d275 DEAD до finalA baseline; в c110/175 DEAD,275/447 absent but alive SQL. Не заменять missing actors, не переключать lifetime и не запускать manual Phantom controls. R6 full-native observer использует snapshot() с временем под evidence monitor; старый R4 batch snapshot(now) probe INVALID и не является verdict.

Первый полезный read-only разбор: B native shutdown java0.log и final-owner-state-197707677899300.json, EVENT24354 на404. Producer опубликован; delayed execution reason UNKNOWN. Далее требуются собственный точный RED и новый bounded task до каких-либо изменений. WAITING_FINAL_CLIENT запрещён до всех server gates. Следующая задача не начата.
