# MORNING028

Итог BLOCKED, M1 OPEN, REAL_FINAL NOT_RUN. Все собственные JVM STOPPED.
Ни follow-up automation, ни следующая задача не запущены.

Tested production SHA: `07c2c1cc4036b47487c98e96943a39ce295a3920`.
Required base: `9aeb4ac6c52970372f97637d26a5eb54c760ed1a`.
Normal publication branch: `experiment/m1-candidate007-observe008`.
Финальный task-only report/archive commit не меняет tested production JAR.

```text
WORKTREE=C:\Users\ZBook\.codex\worktrees\m1-continuity-028\L2J_Mobius
MODULE=L2J_Mobius_CT_2.6_HighFive
RUNTIME=.phantom-local/contract028f/runtime
DATABASE=l2jmobiush5_localplay_contract028f
GAME_JAR_SHA256=31CBEF01BB38C78B22DF775F665568EC0242FBD5F7F7F00936E3B0DAAE53CA8F
LOGIN_JAR_SHA256=A4888C552D310F30FF3183DEB74CACAD7B70574AAEE54C0FFD227160887C3767
OBSERVER_SOURCE_SHA256=EE651236553F59B6DE5D682C6387A3836A5B1134259628DF978332F93C4866DA
```

Только для нового явно разрешённого запуска, из указанного MODULE:

```powershell
pwsh -NoProfile -File docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/Control028.ps1 -Action Start -Episode f -ExpectedSha 07c2c1cc4036b47487c98e96943a39ce295a3920
```

Команда запускает full GameServer/LoginServer собственной clone, не Synthetic
session и не acceptance сама по себе. Last exit code startup helper может быть
ненулевым при фактически RUNNING обоих PID: проверять ownership/record/start-time,
не запускать дубликат. Существующая clone содержит earned state; не перезаписывать.
Source/runtime hashes перепроверить до нового synthetic enrollment.

Достигнуто: две полные V2 сцены8/8 и4/4 на одном SHA; native away/absence/return,
новые epochs primaries и11/7 post cycles; lifecycle02711/11; два real restarts
с exact canonical preservation всех original groups.

Незакрытые server gates:

- Lawful background step primaries запрещён сохранёнными native eligibility facts.
- Whole post-return3/5:994/1159 route_absent0 cycles.
- Missing original872/1272 receipts и UNPROVEN полный terminal/receipt join20 lifetimes; пять исходных away scopes имеют DETACHED/permanent seal proof.
- Full intermediate lineage424/876/1176 между early seal и late canonical state.
- Retained e goal/catchup authority mismatch.
- Required cooperative regression route11 RED; exact cause UNKNOWN.
- Restart1 shutdown stopped=false; first predicate UNKNOWN, dumps сохранены.
- Sticky telemetry latest-file AccessDenied; Windows sharing cause UNKNOWN.

Один следующий observable product endpoint: настоящий background state commit
между natural FINALIZE/absence и return с actual vitality/XYZ, затем new native
owner и продолжение фарма. Не заменять unsupported state на anchor, не расширять
native simulation eligibility без нового разрешённого scope.

Отчёт: `RESULT.md`. Raw evidence archive: `archives/RAW_EVIDENCE_028.zip`;
SHA/inventory/privacy derivatives: `archives/ARCHIVE_MANIFEST.json`. Весь baseline
и negative outcomes сохранены; личное поле account runtime manifests публикуется
только в удалённом виде с парой original/published hashes.
