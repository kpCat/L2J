# Проверки действительного поведения

RED должен быть assertion failure на реальном wrong ordering/effect. Ошибка bootstrap,
недоступная geodata или compilation error — не RED продукта. Latches/barriers/faults
только в guarded TEST. Все wait имеют timeout и finally освобождение fixture/barriers.

## Q — native work, snapshot, recovery

| ID | Сценарий и обязательный результат |
|---|---|
| Q01 | Native kill → delayed ON_ATTACKABLE_KILL → Quest writer. PREPARE запрещён как в queued, так и в running окне. После release один reward, успешный cleanup и равенство после Player.load |
| Q02 | Actual Q00266 native onKill с QuestState, не только test Consumer: native item и quest cond сохраняются. Для детерминированности существующий 20537 branch (chance10), stock fixture; никаких изменений RNG/balance в PLAY |
| Q03 | Hit/cast running, drain начался, producer затем fork delayed child: child принят как continuation; parent exit не даёт false zero |
| Q04 | Future.cancel(false), затем isDone=true, callback ещё за барьером: ownership удержан до finally; вторые cancel/close не дают decrement дважды |
| Q05 | Одновременно callback CAS-start / cancellation-before-start: либо body не исполняется, либо drain ждёт body; earned cancellation запрещена |
| Q06 | QuestTimer one-shot removed-before-body; repeating; TimerHolder post/on/cancel callback: executing counted; long pending ambient не удерживает drain на весь delay |
| Q07 | Native scheduler submit rejects/null/throws/inline/shutdown silent path: ни утечки accounting, ни ложного accepted/SUCCESS, ни второй выдачи; ordinary scheduler positive control |
| Q08 | Identity reuse: objectId тот же, новый Player/epoch/REAL owner; old queued callback не пишет ни старому ни новому и не захватывает new owner |
| Q09 | Active ARRIVAL_CAPTURE/OTHER store с queued/running callback: no PREPARE before drain, no self-deadlock в ActionLease/Player monitor, truthful deferred outcome |
| Q10 | Drain timeout before PREPARE, late legitimate completion, queued retry: сохраняется один reward и завершается native cleanup; UNKNOWN/finalize failure не auto-reopen |
| Q11 | Callback RuntimeException/Error + secondary cleanup failure; accounting closes, outcome unsuccessful, first/primary correct, no blind callback replay |
| Q12 | Actual native hit, cast launch/hit/finalizer, pickup, death/EXP change, status/zone task around snapshot: каждый writer покрыт native scope либо доказан existing exclusion; нельзя просто возвращать early и терять awarded effect |
| Q13 | Callback creates grandchild async event/timer; обе notifyEventAsync overload; zero-delay run-before-assignment: transitive drain и корректная регистрация |
| Q14 | Existing owned C/D, PREPARE/native/FINALIZE process crash и restart, idempotent retry, mismatch negative control. Полный inventory hash, EXP/SP и lifecycle state согласованы |
| Q15 | Early load/abort и native delete/stopAllTasks могут породить callbacks: owner создан до reachable publication; new teardown writers либо synchronously authorized до final capture, либо blocked/retained с evidence |
| Q16 | Общий STOP с несколькими профилями, один зависший producer: finite result, safe ownership, остальные профили завершены; executor не выключен раньше обязательного drain |

## P/E/T/A — завершение старых обязательств

| ID | Сценарий |
|---|---|
| P01 | Real manager iterator captured Player, missing policy after stop: managed не попадает в no-op stock lease; fixture имеет валидную target ветку |
| P02 | После detach outbound/boundary старый managed object остаётся fail-closed без глобальной Player tombstone leak |
| P03 | REAL/offline/synthetic non-Phantom positive controls: прежний native AutoPlay/AutoUse, identity и сохранение |
| P04 | P1.acquire rejected после P2 install: registrations P2 сохранены |
| P05 | P1 body throws после P2 install: catch обоих pools не снимает P2 |
| P06 | Смена owner между stop compare и membership/effect; ordinary offline SQL/follow не касается нового owner |
| P07 | Concurrent session publish/rollback/stop с actual managers: no replacement loss, no lock inversion |
| P08 | AutoPlay=true/AutoUse missing: health=false, next owner step repairs на том же Player/epoch |
| P09 | Native mage после repair casts/kills/EXP/SP; old-policy/new-epoch контроль, воин не сломан |
| E01 | first=A/latest=B на retry, exact lifetime |
| E02 | Store primary A, stopAllTasks B: caller A, B suppressed |
| E03 | Native SQL/resume primary A + boundary.finally B; completed flag корректен |
| E04 | Materialize Error + abort-cleanup Error/RuntimeException: original primary сохранён |
| E05 | Service entry removed, incident доступен detached; cleanupPhase/hook точны в ACTION_DRAIN/PRE_STORE |
| E06 | Unicode, огромные/hostile Throwable, bounded archive/pagination: formatting не ломает recovery, truncation явно |
| T01 | journey_deadline доходит до actual production feedback/replan |
| T02 | Journey remove/recreate не обнуляет same-revision budget |
| T03 | Backend overload/missing native gatekeeper: bounded retry/cooldown, no permanent wrong geo ban |
| T04 | Stale terminal/new goal и navigation protocol failure: не повреждают новый owner |
| T05 | Actual production resolver → новый goal → native route → AutoPlay → полезный farm без ручного attach в TEST |
| T06 | Target exists, autoPlay=true, damage=0/selfheal loop: watchdog видит stall; native reasoned repair/replan вместо endless idle |
| T07 | Dry-land anchor/segment в WaterZone или missing geo/wrong height: unsafe route не исполняется вслепую; lawful water exit не заблокирован |
| T08 | Несколько natural profiles идут в одинаковый anchor: reachable standpoints и расходящиеся native цели, не одна permanent invalid pile |
| A01 | Frozen cohort содержит FAILED/admissionclosed без travel reason: cannot PASS |
| A02 | Self heal + monster target, без attributed effect: cannot PASS |
| A03 | Чужой REAL/Phantom damage/cast/kill не подтверждает выбранного actor |
| A04 | First sample содержит старый cumulative damage: baseline only |
| A05 | Потерянная страница/row/epoch или переполнение evidence: UNPROVEN/RED, не subset GREEN |
| A06 | Native kill очищает aggro: exact actor kill/reward остаётся; нет double attribution |
| A07 | Legitimate death/recovery progress допускается, вечный corpse/retained unknown не исключается из cohort |
| A08 | >=4 natural eligible; selected два последовательных farm cycles, минимум ещё два actor с полезным native progress; остальные имеют ограниченную объяснимую фазу |
| A09 | Sensor проверен через actual successful native writer; runner не «создаёт» damage/loot/EXP evidence |
| A10 | Missing structured field, NaN/overflow, stale timestamps/sequence, synthetic actor под видом REAL: fail closed |

## L — реальный loot

| ID | Сценарий |
|---|---|
| L01 | Spawned законный native Item: stock AutoPlay Pickup → native Player pickup → item исчезает с земли → inventory/canonical after reload совпадают |
| L02 | Native AUTO_LOOT mode: reward проходит штатным способом без искусственного MOVE_TO к несуществующему drop |
| L03 | Protected чужой item, ignored item, full/overweight inventory: отказ понятен, нет theft/dupe/вечной блокировки farm |
| L04 | Reachable item >70 и <200 distance: native movement+pickup; unreachable/другой instance не захватывается; protection не вызывает endless chase |
| L05 | Concurrent pickup/cleanup и quest item, не включённый в background mutableItemIds: полный canonical inventory hash сохранён; предмет не теряется после rematerialization |

## W — продуктовая интеграция (не заменяется unit matrix)

| ID | Сценарий |
|---|---|
| W01 | Production scheduler/readiness/locality/materialization/Decision/travel/AutoPlay wiring на обычном native TEST actor; никаких test-only manual starts вместо production wiring |
| W02 | Melee и mage полные target→approach→damage→kill→EXP/SP→next target cycles; resource pause/regen не превращаются в permanent idle |
| W03 | Natural cohort >=4: полезная жизнь нескольких, не fixed chosen winner; raw census сохранён до и после |
| W04 | Native death → existing recovery → следующий полезный цикл; gear/inventory/level не подменены |
| W05 | Human approach/prewarm before know-region; контакт same object/epoch; no forced dematerialize near human; native movement continuity |
| W06 | Human leaves → soft return after pins release → background → reentry/rematerialization: state сохранено, законная смена epoch отражена, no pop-in workaround |
| W07 | Elven Village/Gremlin и nearby land/water paths + один spot на каждый реально населённый level range current1280; нет forced route/rate/config ради зелёного |
| W08 | TEST save/restart/reconcile + bounded resource soak; REAL-attacker path диагностируется без нового PvP milestone |

Группировать связанные asserts в существующие suites, не создавать по процессу на строку.
Для нужных deterministic fault windows допустимы TEST-only seams, но положительный
native/production-control путь должен исполнять именно production implementation.
