# Goal 015 — Background farming reconciliation

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Summary

- Реализована одна capability для ACTIVE `farm.background` с exact NPC/anchor.
- Typed lease и activity identity закрывают ACTIVE/BACKGROUND arbitration.
- `BACKGROUND_MODEL_V1` покрывает farming, travel, competition, death/recovery.
- Canonical MariaDB writer использует `VERIFY_PENDING` reconciliation.

## Changed files

- Production: `phantoms/background/**` (11 файлов).
- Composition/context: `PhantomSystem`, decision contexts/engine, work sink bridge.
- Identity/lifecycle: identity registry, materialization service/player и два ports.
- Разрешённый real-login seam: `GameClient.java`.
- Tests/build: `PhantomBackgroundSuite.java`, launcher и `build.xml`.
- Tools: verifier 015 и bounded compatibility correction verifiers 014/014A.
- Docs: architecture/report/review, roadmap/master-plan status и task package.

## Architecture decisions

- Переиспользованы `Player`, shared identity registry, materialization lifecycle,
  scheduler work item, decision registries, profile components, topology,
  Game Knowledge, progression catalog и commerce hash authority.
- Отдельный Player/fake GameClient, новый persistence schema, config, worker,
  loader или class-name tactics не создавались.
- Baseline transaction повторно блокирует exact persisted goal: service-side
  read не считается достаточным authority против revision race.
- `background.state` schema 1, payload <=4096, states:
  `MATERIALIZED/READY/VERIFY_PENDING/DEAD/INCONSISTENT`.
- Operation key включает profile/character, goal ID/revision,
  generation/tick/action/NPC/anchor/model и четыре authority hash.

## Model/formulas

- Версия: `BACKGROUND_MODEL_V1`; это deterministic approximation, не retail
  combat equivalence.
- `effectiveDamage=max(1,offense*100/(defense+100))`;
  `cycles=max(0.1,speed/500)`; duration — target HP / damage / cycles,
  variance ±10%, clamp 500..20000 ms.
- Incoming attrition использует симметричный offense/defense channel, target
  cycle speed, duration и второй persisted variance roll.
- Batch caps: 32 encounters, 60000 ms, 16 changed objects, 8 новых
  non-stackable objects.
- EXP: current rate cast → current high-level penalty → captured multiplier и
  exact servitor share → `Math.round`.
- SP: current rate cast → penalty/multiplier → integer truncation.
- Drop groups/ungrouped, cumulative/custom occurrence budgets, level-gap/chance/
  inclusive amount rolls повторяют current `NpcTemplate.calculateDrops`.
- Death EXP loss использует current `ExperienceLossData`, level span, captured
  normal-mob reduction и current 10% cap.

## SQL/writer table

| Authority/writer | Goal 015 rule |
|---|---|
| identity registry | per-operation `OwnerKind.BACKGROUND`; blocks PHANTOM/REAL_LOGIN |
| `characters` main | level/exp/expBeforeDeath/sp + HP/MP/CP + x/y/z/heading |
| `character_subclasses` | exact class_index level/exp/sp; base rows untouched |
| `character_skills` | current `SkillTreeData` auto-get-only exact class_index |
| `items` | exact locked objects; IdManager IDs for new rows |
| goal component | exact persisted ACTIVE goal is locked, never mutated |
| `background.state` | only `PhantomBackgroundTransaction` mutates |
| Player/autosave/login | excluded while background lease/transition owns identity |

Lock order:

```text
phantom_profiles link
→ exact goal component
→ background.state
→ characters
→ exact subclass
→ skills ORDER BY skill_id
→ items ORDER BY object_id
→ reserved object IDs
```

Connection uses `autoCommit=false`, 5-second query timeout and exact row-count
guards. Commit writes `VERIFY_PENDING` plus expected-after SHA-256; fresh
connection promotes to READY/DEAD. Mismatch writes INCONSISTENT and fail-stops.

## Transition/receipt matrices

| Transition | Guard/result |
|---|---|
| ACTIVE → BACKGROUND | Player.storeMe → fresh DB capture → READY/DEAD before lease release |
| BACKGROUND → ACTIVE | drain/reconcile → Player.load compare → MATERIALIZED before spawn |
| exact duplicate | fresh proof, IDEMPOTENT, no second mutation |
| stale goal/generation/tick/hash | typed stale/replan, zero mutation |
| pre-commit fault | complete rollback and reserved ID release |
| post-commit unknown | restart resolves VERIFY_PENDING |
| canonical/hash mismatch | INCONSISTENT, no auto-recovery |
| DEAD → WARM | Player revive + current to-town + store/verify → FAIL_GOAL |

Travel persists residual time without moving canonical coordinates mid-edge;
completed edge atomically commits the exact anchor. Competition reservation is
process-local, `(node,npc)`, capacity clamp 1..32, one operation only.

## DB, config and fixtures

- Использована только `l2jmobiush5_phantom_test`; production DB не использовалась.
- Seed всех новых modes: `15001501`.
- Schema, migrations, config, geodata, Player/Item/Inventory/Attackable и loaders
  не изменялись.
- Real fixtures: loaded Player, NPC 22859, current NpcData/drop facts, spawn
  capacity, topology anchors/edges/door state, main/subclass/skill/item rows.
- Fake authority/ports использовались только для deterministic fault injection.

## READ_SET и расширение

- Выполнен task READ_SET 1–18 bounded symbol/range reads: scheduler/activity,
  decision, materialization/identity/login, profile/components, knowledge,
  topology, progression, commerce, combat, Player/stat/EXP loss, Attackable,
  inventory/item/IdManager, integration suites и build/verifier chain.
- Единственное дополнительное exact-file расширение:
  `model/actor/templates/NpcTemplate.java`, `calculateDrops`; причина — доказать
  порядок groups/ungrouped, отдельные occurrence budgets и custom-rate semantics.
- Parent/root `AGENTS.md`, README и relevant project docs проверены; отдельного
  parent AGENTS выше repository contract не найдено.

## Commands and results

- Initial branch/parent/worktree audit: PASS; HEAD был exact
  `9c9412bc4a05a520a83b5187054d6c8a8c12db3c`.
- `ant phantom-background-test`: PASS, model 7/7, transaction 7/7,
  lifecycle 4/4, decision 3/3, server integration 5/5, performance 3/3;
  1 min 55 s.
- После exact baseline-goal-lock/resource fix:
  `ant phantom-background-model-test phantom-background-transaction-test
  phantom-background-lifecycle-test`: PASS, 18/18; 1 min 17 s.
- Affected materialization/scheduler/decision suites: PASS, 105/105; 1 min 42 s.
- Working-tree verifiers 014/014A/015: PASS; joint final run 2 s.
- Final focused aggregate: PASS, 29/29; 1 min 59 s.
- Единственный cumulative `ant verify`: PASS, 7 min 57 s.
- Standalone `ant jar`: PASS, 14 s.
- Post-commit verifier 015 2× byte-identical фиксируется во внешнем handoff.

## Performance

- 100000 pure model evaluations: PASS, bounded state/RNG, no worker.
- 10000 duplicate reconciliations: PASS, idempotent and leak-free.
- Real DB batches, repeated 100 ticks/50 transitions and lifecycle fault/restart
  matrices завершились с нулевыми retained operation/lease counters.

## Scope, encoding and git

- Исторические verifier 014/014A имели parent-only status scope и делали любой
  будущий `ant verify` невозможным. Bounded exception заморозил их accepted
  completion diff и проверяет `9c9412bc...` как ancestor; product assertions
  сохранены.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- `git diff --check` и full exact diff inspection: PASS.
- Git-команды использованы только по прямому task contract для branch/parent,
  scope, commit и push; amend/rebase/squash/merge/force push не выполнялись.

## Limitations, risks and next step

- Existing exact production anchor `giran.farming.22859` имеет immediate-effect
  herb drop. Этот excluded outcome доказан как fail-closed до mutation; loaders/
  data вне scope не менялись.
- Model применим только к exact supported normal-solo facts; party, spoil,
  manor, raid, instance, PvP, buffs/vitality/premium/event остаются fail-closed.
- Goal 013B activation gate остаётся закрытым: `progression.learn_skill` не
  зарегистрирован.
- Goal 015 не self-accepted и требует independent review; Goal 016/017/025 не
  начаты.
- Pre-publication usage: 1806071 tokens, 9625 s. Commit SHA/push/final usage
  передаются во внешнем handoff: SHA нельзя записать внутрь того же commit.
