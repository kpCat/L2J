# Полностью автономный runbook

## Никакого окна клиента ночью
Выбрать synthetic КОПИЮ TestAdmin в owned clone (SELECT точного objectId/name, online0,
не связан с phantom_profiles, без party/store/instance). Не использовать kpCat.
Если copied TestAdmin непригоден, разрешено создать отдельного обычного NightObserver23
через existing native fixture/PlayerCreationInitializer в собственном clone. Не создавать
fake GameClient/REAL_LOGIN и не выдавать прав Phantom-персонажам. SQL exception по observer
разрешена только для existing exact clone setup, не для игрового прогресса Phantom.

## Effective config
PhantomPopulationTarget=1280; PhantomPopulationActiveTarget=8;
MaxMaterializedPhantoms=8; MaxScheduledPhantomProfiles=10000; EnablePhantomSystem=True.
EnablePhantomDiagnostics=True; EnableLocalPlayPilot=True;
EnableLocalPlaySyntheticHuman=True;
LocalPlaySyntheticCharacterObjectId=<exact clone ordinary objectId>
LocalPlaySyntheticCharacterName=<exact clone name>
Pilot autoattach TestAdmin оставить для последующей REAL-проверки, но ночью использовать
исключительно ActorMode=Synthetic. GM invisibility/invulnerability/silence/hide False.
PersonalCharacterQoL/balance/rates/catalog/geodata не менять.

Начальные spawn/позиция observer берутся из clone/актуальной topology. Межсценовый перенос
ТОЛЬКО synthetic observer допускается до baseline через штатный TELEPORT_SELF (если нужны
другие районы); это setup, не доказательство soft return. Тест soft leave/return — MOVE_SELF
по реальной сухой local path, а не teleport и не произвольный refcount toggle.

## Уже существующие команды (в copied runtime tools, как прежние task022)
```powershell
$runId = [guid]::NewGuid().ToString('D')
.\Start-LocalPlaySynthetic.ps1 -RunId $runId
.\Invoke-LocalPlayPilot.ps1 -ActorMode Synthetic -RunId $runId -Operation STATUS
.\Invoke-LocalPlayPilot.ps1 -ActorMode Synthetic -RunId $runId -Operation SNAPSHOT_PHANTOMS -Arguments @{includeCensus='true'}
```
Скопировать existing tools рядом с уже созданным valid LocalPlay manifest; не запускать
их из main runtime: Get-PilotContext определяет ownership от своего реального пути.
Heartbeat во время сессии каждые10s, request count<=400, scene<=420s, session service cap525s.
START fresh UUID, STOP через existing Stop-LocalPlayPilot -ActorMode Synthetic.
Не использовать PREPARE_M1_ENVELOPE для read-only baseline. В случае неподдерживаемой
diagnostic операции расширить task-local wrapper, не переключать ActorMode на RealClient.

## Решения без вопросов
| Условие | Действие |
|---|---|
| Branch занят | detached worktree от exact base, normal push explicit refspec |
| Target path занят | первый свободный suffix, ничего не удалять |
| Порты заняты чужим процессом | отдельный loopback port set в owned clone или безопасные TEST; чужой PID не трогать |
| TEST занят чужим процессом/journal | не использовать общую TEST; выполнить clone/in-memory checks; не чистить journal |
| Нет нужного NPC рядом | выбрать следующий natural eligible anchor по catalog, записать отсутствие; не спавнить в final scene |
| Нет loot drop по RNG | не заставлять drop; separate stock guarded pickup control; честное NOT_OBSERVED в natural scene |
| Synthetic expired | выяснить heartbeat, корректно STOP; максимум новый UUID в общем budget; не обнулять bot epoch для PASS |
| Startup/fixture error | bounded tooling repair до30мин, proof на fixed input; затем safe alternate work, не вопрос |
| Новый relevant farm/lifecycle defect | собственный RED → минимальный fix внутри SOURCE_MAP, продолжать тот же task |
| Повтор2раз неизменённого root | пересмотреть причинную гипотезу по DESIGN, не бесконечный rerun |
| Выход за native writer/scope safety | собрать доказательство, не обходить boundary; независимые gates и финальный BLOCKED |
| Наш JVM завис | dumps+receipts,2graceful bounded attempts; exact-owned force только по TASK.md, cleanup FAIL |
| Уже вошёл REAL клиент | не управлять им, остановить synthetic safely; не делать force/teleport/arm; отдельный clone |
| Нет Git credentials/remote изменён | local commit+patch+PUBLISH_BLOCKED, не просить ночью новый токен |

## Завершение
Серверы default STOPPED; runtime/clone сохранены. MORNING.md содержит:
reviewed SHA, paths, config fingerprint, что реально PASS/не PASS, verified script
Start-Reviewed023.ps1 для retained clone, один ручной TestAdmin login для будущей приёмки.
Не превращать это утреннее действие в ночной gate.
