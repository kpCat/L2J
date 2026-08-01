# Goal 021 Checkpoint 1 — review package

- Статус реализации: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Required parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`.
- Ветка: `feature/phantom-world`.
- Seed: `21002101`.
- Checkpoint: первый из двух заранее запланированных checkpoint Goal 021.

## Проверяемое решение

Реализация ограничена `acquire.item` kernel: strict policy/codec/store,
authoritative source planner, bounded recipe ingredient DAG, deterministic
switching, active spoil через существующий Combat и background parity через
существующую atomic Background transaction.

Критические отрицательные границы закреплены тестами: нет прямого item grant из
acquisition package, нет второго combat loop, неизвестная capability/source/
target/instance/distance/ownership отклоняется, uncertain dispatch не
повторяется вслепую, а capacity/failure не создаёт background progress.

## Findings

- Блокирующих findings на момент формирования review package нет.
- `Player.java`, `Party.java`, skill/quest handlers и schema не изменены.
- Manor/quest остаются `DEFERRED_CHECKPOINT_2`.
- Recipe path только планирует; craft/trade/private store/enchant execution
  отсутствует и остаётся Goal 022.
- Phantom World остаётся выключенным по умолчанию.
- Независимое принятие этого checkpoint этим документом не выполняется.

## Независимый gate

Ревьюер должен повторно проверить exact commit graph/subject/scope verifier-ом
`021c1`, восемь focused modes, affected regressions, единственный final
aggregate, `ant verify`, `ant jar` и byte-identical post-commit verifier output.
Только отдельное решение ревьюера может изменить статус checkpoint на `ACCEPT`.
