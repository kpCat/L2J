# MORNING030

TASK_RESULT=BLOCKED; M1=OPEN; REAL_FINAL=NOT_RUN.
STOP_AUTHORITY=TASK030_CONTRACT. Own Game/Login JVM a/b/c physically STOPPED; force не применялся.
Worktree: C:\Users\ZBook\.codex\worktrees\m1-plan-inventory-030\L2J_Mobius.
Module: L2J_Mobius_CT_2.6_HighFive.
Frozen code:5375198db48575666623ac79b875373d1750cdc6.
Owned DB: l2jmobiush5_localplay_contract030c, MariaDB127.0.0.1:3308.
Runtime: .phantom-local/contract030c/runtime; manifest local-play.json.
JAR и observer hashes: evidence/FINAL_FROZEN_ENVELOPE030.json.

Проверенная команда запуска (успешна дважды на том же frozen SHA/DB; сейчас не запускать):
```powershell
& 'docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/Control030.ps1' -Action Start -Episode c -ExpectedSha '5375198db48575666623ac79b875373d1750cdc6'
```
Она использует существующий frozen runtime и planned startup collector до первых births.
Report-only HEAD может отличаться от codeSha runtime; не Update/reimport для смены label.
Перед отдельной разрешённой сессией проверить ports/ownership/hashes штатными guards.
Stock Stop exact-owned command:
```powershell
& 'docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/Control030.ps1' -Action Stop -Episode c
```

Natural admission не восстановлен:1280 census,0 READY+ONLINE+farmAllowed+complete.
Полные сцены/away-return/whole-save/crash gates не пройдены. Exact old finalC7 source binding
UNKNOWN; capacity не доказанная причина cohort0. Old clones/PLAY только READ/export,
никакого SQL-heal или late reconstruction missing receipts.

Следующую задачу не запускать автоматически. RESULT.md и HANDOFF.md — единый итог этой сессии.