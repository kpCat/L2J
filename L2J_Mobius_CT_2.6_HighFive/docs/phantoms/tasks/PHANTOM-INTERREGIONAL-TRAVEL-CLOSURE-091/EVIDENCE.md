# EVIDENCE — что именно должен увидеть независимый reviewer

Единый report в Git и подробные sanitized receipts в приватном TEST091 stage. **Одна строка на family + leg + episode**, не один красивый общий PASS.

| Колонка | Смысл |
|---|---|
| `actor` | family, level, classId, actual native Player/profile/objectId в собственной DB |
| `source` | исходная spawn/GK/native XML ссылка, topology current anchor; proof tier `BACKGROUND_CERTIFIED` или `FOREGROUND_NATIVE_PENDING` |
| `goal` | goalId/revision, target farm region/NPC spawn, selection reasons (affordability, distance, risk) |
| `walk` | from XYZ/instance → native PathFinding/Geo evidence → issued `MOVE_TO` → observed landing XYZ/time, each episode count/counters |
| `gk` | NPC ID, NORMAL list/index, canInteract, before wallet, fee effective, after wallet, exact destination, native status, unique debit token |
| `farm` | original actor, Monster HP before/after/kill, EXP/SP/Adena/items before/after; separate fixture-derived vs earned |
| `restart` | saved journey intent/checkpoint/paid receipt, previous/new JVM, sameDB/goal/objectId, no-second-fee, resumed action and reward |
| `guards` | negative exact refusal, no move/charge, no double callback, stale marker/state and ownership rejection |

Раздельные tiers: `SOURCE_FACT`, `GEO_ONLY`, `PLAYER_PREPARED`, `PLAYER_MOVE_ISSUED`, `PLAYER_ARRIVED`, `GK_NATIVE_ACTION`, `GK_PAID`, `COMBAT_REAL`, `REWARD_EARNED`, `RESTART_CONTINUED`. Нельзя повышать tier по quote, staged inventory, generated route или fixture. В одном компактном summary rows H/E/O: planned, physical, fee, farm, restart, root cause for FAIL.

Report привязывает текущий local/upstream/live commit SHA, production source hashes, inputs, exact DB schema name (без credential), Geo region count, JDK/Ant, staged JAR hash, focused/native exit codes, previous first failing evidence и новое доказательство, own JVM/DB cleanup, protected078/config090 unchanged. Не включать names/passwords/allowlists/host secret fields в Git.
