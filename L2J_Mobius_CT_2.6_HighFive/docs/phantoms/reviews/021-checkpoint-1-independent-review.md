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

## Safety completion к foundation bf0cc37

Для independent review foundation дополнен одним bounded direct child с subject
`fix(phantoms): complete acquisition eligibility and recovery`.

- Capability truth покрывает exact Dwarf active-class lineage и canonical skills
  254/42/172; eligibility основана на actual known level, а не на минимуме rule.
- Background learned-skill evidence читается bounded из `character_skills` и
  повторно проверяется внутри существующей atomic transaction; `autoGetSkills`
  сохранил прежнюю семантику.
- Dispatch и Combat получили persisted prepared/verification boundaries, exact
  restart observation и bounded terminal release без второго combat loop.
- Acquisition operation identity versioned по source ID и acquisition row version;
  ordinary Goal 015 digest и поведение сохранены.
- Ambiguity теперь едина для разных methods; все ненулевые policy weights получают
  bounded evidence либо conservative penalty. Quest evidence без authority не
  выдумывается, stale source очищается из Goal.

Completion scope: 22 файла, 15 production/data, 0 новых production-файлов. Focused,
affected, atomic, parity, Goal 015, lifecycle gates и working verifiers прошли на
test DB с seed `21002101`. Финальные frozen aggregate/full verify/jar и два
post-commit verifier runs должны быть подтверждены из финального handoff и exact
completion commit.
