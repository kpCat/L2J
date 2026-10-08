# SCENARIOS028

## Transport / orchestration
T01 absolute expiry immutable при repeated writeState;525s остаётся пределом.
T02 FullObserve после enrollment снимает60+samples без нового IPC на sample;
    oversized XML/invalid fields не меняют64KiB limit и не обрезают актёров молча.
T03 ответ опубликован после sender timeout: resolver читает exact result, dispatch count1.
T04 MOVE ACCEPTED но actor ещё moving — ARRIVAL не подтверждён; wrong run/epoch→reject.
T05 missing/stale heartbeat или native identity change закрывает session; guard не снят.
T06 reconnect/new Synthetic ID/новый epoch не продолжает прерванный baseline; gap>5с FAIL.
T07 delayed result другого request/run/sequence не принимается; no action replay при claim.
T08 advertised window+request count доstart хватает на episode; при нехватке новый setup
    доbaseline, не повышение TTL400/cap5. Независимая heartbeat жизнь подтверждена логом.

## Behavior, использовать реальные native callbacks
B01 healthy same goal+epoch AutoPlay не теряется из-за wrapper retry/replan.
B02 dead/lost target→следующий допустимый native target;5cycles+EXP/SP без manual attacks.
B03 два кандидата, один имеет доказанно unsafe/unreachable route, второй доступен:
    меняется exact action/plan, guards первого остаются; отсутствие обеих целей typed.
B04 настоящая нехватка MP→stock sit/regen→stand/offensive action; resources не добавлять.
B05 второй REST/REPLAN tick не обнуляет old useful-progress debt и phase deadline.
B06 late navigation/cast callback старой revision/epoch не действует на replacement.
B07 population/area has no target: bounded retry/alternate lawful area; не infinite idle.
B08 поле ACTIVE_ACTION не удерживается бесконечно самими newly-issued auto roots offscreen.
B09 мёртвый или background actor остаётся в отчёте; только native lineage доказывает
    корректный state, отсутствие в census не считается автоматически offline/sleep.
B10 native incident/pending ambiguity не снимаются state classifier/collector.

## Soft return
L01 observer физически уходит по проверенному пути: live native envelope false для
    заранее2 выбранных actors, чужих REAL/local observers0. Не просто растёт distance.
L02 после native action+grace actor дематериализуется, queued callbacks завершаются,
    immutable store receipt finalized и canonical фон продолжает жизнь.
L03 observer возвращается физически;>=1 заранее выбранный подходящий по текущей зоне
    actor rematerialize новым epoch и даёт>=5cycles, второй>=2 либо полный lineage
    его законного перехода в другую зону. Нельзя выбрать победителя задним числом;
    если ни один не eligible current area, gate UNPROVEN, не false failure навигации.
L04 early observer return до завершения demat: no duplicated owner, no lost rewards,
    exact existing retention/cancellation решает, не новый retry timer.
L05 путь имеет сухие концы, но водный промежуточный сегмент: запрещён без MOVE,
    выбрать другой разрешённый observer path доepisode или сохранить BLOCKED_GEOMETRY.

## Persistence & regression
P01 последний SEALED каждой incarnation→SQL exact inventory/skills/EXP/SP/XYZ.
P02 sameDB restart1+2: контрольная точка доordinary release, затем законные фоновые
    операции отдельно. Background travel832 из027 не объявлять corruption без evidence.
P03 first healthy stop COMPLETE доpools, pending0/retained0, без force.
P04 reused027 native death/cold/pending-store/crash provenance указано с hashes.

Для B03/B04/L04 допускается controlled native TEST fixture; finalA/B только естественные
NPC/персонажи и штатные характеристики. Любой injected failure/story fixture маркируется
и не выдаётся за natural final scene. No counters spoofing, no forced target on Phantom.
