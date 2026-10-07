# Автономное исполнение без вопросов

## Среда
JDK25/Ant/MariaDB3308, owned runtime в `.phantom-local/contract024*/runtime`.
Re-use Start-Reviewed023 / Update-OwnedRuntime023 / Freeze-Runtime023, копируя в TASK024
и заменяя только own paths/manifest. Старые runtime023 immutable read-only.
Synthetic: existing LocalPlaySyntheticHumanSession, ordinary clone TestAdmin, owner
LOCALPLAY_TEST_HUMAN, GameClient=null. Не fake REAL_LOGIN, не headless Phantom observer.

Effective config1280/8/8/10000; диагностика ON; real TestAdmin autoattach сохранён на утро,
но ночью ActorMode=Synthetic. QoL/rates/GM invis/invul/hide/silence не усиливать. kpCat untouched.
Synthetic session525s/request≤400/five-starts-per-JVM сохраняются. Finalscene360–420s,
heartbeat10s, max5s между sample requests. Не обходить cap перезапуском без cause.

Existing commands из copied runtime tools:
```powershell
$runId=[guid]::NewGuid().ToString('D')
.\Start-LocalPlaySynthetic.ps1 -RunId $runId
.\Invoke-LocalPlayPilot.ps1 -ActorMode Synthetic -RunId $runId -Operation STATUS
.\Invoke-LocalPlayPilot.ps1 -ActorMode Synthetic -RunId $runId -Operation SNAPSHOT_PHANTOMS -Arguments @{includeCensus='true'}
```
PREPARE_M1_ENVELOPE запрещён как read-only sampling. В setup разрешён native TELEPORT_SELF
ТОЛЬКО synthetic observer до baseline. Soft leave/return доказывается MOVE_SELF сухим
путём вне native local envelope и назад; setup teleport не считается soft movement.

## Решения без оператора
- Worktree/path занят: detached+free suffix. Не трогать existing.
- Порты заняты чужим PID: own loopback alternate port set, зафиксировать manifest.
- Чужой TEST/journal: пропустить shared run; isolated clone/ConnectionProvider validation.
- Schema guard: никаких checksumUPDATE. Своя freshly provisioned DB либо gate BLOCKED_SCHEMA.
- Synthetic TestAdmin непригоден: own ordinary ContractObserver24 через существующий native
  initializer/fixture. Никаких ручных Phantom progress изменений.
- Недостаточная natural cohort: до baseline bounded census≤3 candidate areas из реального
  catalog/состояния; записать все rejected setup locations. Зафиксировать A/B раньше
  измерения. После failed measurement не менять primary/profile для удачи.
- Появился REAL: никакого контроля/force; уйти в другой свой runtime или безопасные tests.
- Новый in-scope defect: RED/fix/verify до270мин, не спрашивать о новом пакете.
- Unsafe outside scope: evidence+lane BLOCKED; безопасные независимые work items продолжить.
- Платформенный approval prompt/auth: не обходить, barrier зафиксировать. Нельзя гарантировать
  автономность против platform limitations; пользовательские input gates не создавать.

## Утро
Default все JVM STOPPED, сохранён own clone/runtime и проверенный Start-Reviewed024.ps1.
MORNING.md: exactSHA/JAR/config+datahash/DB/script/чтоPASS/чтоFAIL/одна команда запуска.
Его future manual TestAdmin login не превращать в ночной gate. SERVER_M1_PASS без REAL
возможен только по всем server gates, M1 максимум WAITING_FINAL_CLIENT.
