# Машина продолжения локального действия (не вторая машина персонажа)

Состояния хранятся внутри текущего native Session. Схема намеренно не меняет persistent goal.

| Текущее состояние | Событие | Продолжение |
|---|---|---|
| DISCOVER | fresh допустимое действие уже в range/LOS | READY_NATIVE |
| DISCOVER | domain-valid цель за препятствием | PLAN_APPROACH |
| PLAN_APPROACH | queue/backpressure | WAIT_NAV, исходный deadline |
| WAIT_NAV | exact route reply | APPROACH, не success |
| WAIT_NAV/APPROACH | target incarnation/goal/owner сменился | отбрасываем reply, cancel только own request |
| APPROACH | реальная новая позиция по path | следующий waypoint, счётчик фарма не меняется |
| APPROACH | fresh native action predicate true | READY_NATIVE, own MOVE прекращён |
| READY_NATIVE | next root разрешён и цель актуальна | YIELD_TO_STOCK |
| YIELD_TO_STOCK | native damage/reward/next target | новое наблюдение; сохраняется original useful epoch |
| любое | цель погибла во время принятого каста | дать native завершиться; затем reselect/SWEEP по policy |
| любое | temporary checkpoint/resources | bounded PAUSED, не перезапуск Session |
| path | no-progress/blocked | local alternate/replan, не новый deadline на каждом tick |
| любое | смерть/retirement/store/stop/party takeover | REVOKED для новых roots; штатный earned drain |

Action token не является лицензией на native write. Любая команда снова требует current
owner/lease/goal/state и native legality. Результат PathFound не означает ARRIVED,
а ARRIVED не означает kill/reward. Awaiting delayed earned не означает stuck.
Default branch любого неизвестного состояния — safe yield/typed failure, не fake success.
