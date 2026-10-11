# Read-first координатора — source at c0e86fa2950012ae2ee898b841524fa160869a77

Все пути относительны HighFive; номера строк — ориентир на base, не разрешение править весь файл.
Source URL template: `https://github.com/kpCat/L2J/blob/c0e86fa2950012ae2ee898b841524fa160869a77/L2J_Mobius_CT_2.6_HighFive/<path>`.
Чтение через GitHub выполнено; локальная JVM/privateraw пользователем здесь не предоставлены.

| Source / range | Установленный факт | Что из него НЕ следует |
|---|---|---|
| Creature.java:1940–2225 | beginCast создаёт MagicUseTask и `schedule(this, targets, "cast-launch", EARNED, ..., max(0, skillTime-400))` | RESERVED спустя3.4с сам по себе не leak: нужен actual skillTime/due |
| Creature.java:5925–6235 | launch→cast-hit(400ms)→cast-finalizer(0); queued/finalizer children; stock guards остаются | EARNED не обещает успешный урон/EXP: target мог умереть/каст прерваться |
| MagicUseTask.java:37–104 | exact current-task check; stale task возвращается без native body; failure aborts current cast | Возврат из body не должен оставлять ticket: wrapper обязан complete |
| PlayerNativeWork.java:376–399,505–619 | reserve before publish; ParticipantWork.run/finish; ParticipantFuture.cancel не отменяет RESERVED EARNED | future.isDone не доказывает ticket complete и обратное |
| PhantomNativeWorkScope.java:207–229 | transitive child RUNNING parent допускается в DRAINING, новый root — нет | нельзя открыть root admission ради следующего скилла |
| PhantomNativeWorkScope.java:445–563 | `_traceDispatch` включается только для EVENT:; общий cached work содержит не весь dispatch timeline | cast-launch может присутствовать без submit/dequeue/start/cancel evidence |
| PhantomNativeWorkScope.java:348–425,570–619 | beginTerminalDrain не паркует shared worker; drain waits; EARNED cancelBeforeStart=false | насильный cancel/clear для retained0 не исправление |
| PhantomMaterializationService.java:421–630 | prepare drain→control cleanup; сохранённый control result, retry on quiescence, release only STORED | sticky result/failed readiness — гипотезы, не установленные причины27 |
| PhantomSystem.java:980–1075 | single10s deadline, FAILED remains FAILED; закрываются producers, затем accepted control/players | запрещено перезапускать deadline на каждом poll |
| Shutdown.java:391–456 | initial/final Phantom drain вызываются до shared ThreadPool.shutdown; при failure всё равно идёт stock exit | collateral cutoff возможен, но именно27 им не доказан |

Проверенные Git blobs (полный source):
- PlayerNativeWork.java `3cbd0e5ec1e96c83dc64dd9735b233ac16149d7a`
- PhantomNativeWorkScope.java `07a87b6b5e326c558c5fa4f44a84ea9d480272e2`
- Creature.java `2f13b5a35dc3f30c71a79655609c894354d7b866`
- MagicUseTask.java `09bb596b00bda15922963cd0f2c19c5553fadd0b`
- PhantomMaterializationService.java `50973670f8b809252aa18db782a69b21d5edd707`
- PhantomSystem.java `74c638f13ebc2d33636962924a2eed9b8d764353`
- Shutdown.java `101b3a3dc62fce791fd57020b81f331704fe21e7`

## Повторно использовать, не изобретать fixtures
`PhantomLifecycleCompletion027Suite`: S02 delayed kill child; E01 executor-before-owner;
S05 original callback timeout/single deadline; real death/recovery. `acceptance` — ровно11 случаев.
`PhantomCallbackContinuation026Checks`: actual native kill/QuestTimer, WorkerGate,
OriginalCastMonster с проверкой настоящего MagicUseTask в стеке и real HP write.
`PhantomNativeContextHandoffSuite.Fixture`: реальный owned Player/background/lifecycle.
`PhantomHeadlessPlayerTestEnvironment`: exact manifests; добавить ТОЛЬКО036t guard по аналогу035.
`Verify035.ps1`, private ops035 runner/observer — самый свежий источник proven Jar-first classpath.
Старый `Verify032.ps1`/Contract032Observer — fallback; не исполнять hardcoded пути без адаптации.

Сохраняемые source035: L2jPhantomBackgroundAuthority, PhantomBackgroundState,
PhantomBackgroundModel. Их нельзя модифицировать в036; regression6/4/2/9/11/2 остаются на исходных критериях.
