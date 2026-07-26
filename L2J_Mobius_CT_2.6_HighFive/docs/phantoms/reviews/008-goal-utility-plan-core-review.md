# Independent review — Goal 008 goal/Utility AI/plan core

## Verdict

```text
Reviewed commit: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Parent commit: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Goal 008 architecture direction: ACCEPT
Goal 008 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 008A: REQUIRED
Goal 009: BLOCKED
```

Архитектурное направление Goal 008 принято: immutable domain model,
deterministic Utility AI, typed plan, bounded executor и переиспользование
profile component envelope соответствуют roadmap. Commit не принимается как
финальный baseline до bounded Goal 008A, потому что review обнаружил
concurrency/liveness findings внутри decision-engine persistence boundary.

## Reproduced evidence

На reviewed commit независимо воспроизведены:

| Gate | Result |
|---|---:|
| Decision core | PASS `30/30 ×3` |
| Decision persistence | PASS `14/14` |
| Decision performance | PASS `2/2 ×2`, canonical summaries byte-identical |
| Scheduler regressions/integration | PASS `20/20 ×3` |
| Goal 008 final verifier | PASS `68/68 ×2`, output byte-identical |
| External final verifier SHA-256 | `B2968457F0F59C0CEFDCF4566F4CA1C9FF456CB05FC886E1C111915BF67689C0` |

Reviewed commit, branch and remote matched:

```text
feature/phantom-world
b6c58c37f1ba77e92b61e9499a30d17d09c82086
origin/feature/phantom-world = b6c58c37f1ba77e92b61e9499a30d17d09c82086
```

## Required findings

1. `GoalStore`/JDBC calls could execute while holding the global
   decision-engine monitor. A blocked store could therefore block unrelated
   profiles, cancellation-token reads and `beginStop()`.
2. Attach did not have bounded pending ownership distinct from published
   runtime slots.
3. Runtime persistence did not expose an exact one-claim ownership protocol
   across mutation, reload and terminal writes.
4. Conflict, store failure and concurrent BUSY conditions were not all
   represented as distinct explicit results/runtime states.
5. Detach/stop needed retained ownership until an already claimed external
   store call returned, preventing a late result from reaching a replacement
   runtime.
6. Step timeout used logical time `0` as an unset value, although `0` is a valid
   monotonic start time.
7. Goal/activity/stop boundaries could retain stale candidate, explanation or
   last-result evidence in snapshots.

## Scope decision

Исправления ограничиваются Goal 008A: persistence ownership, liveness,
timeout sentinel и stale snapshot cleanup. Immutable model, codec, scoring
semantics, schema/config, Goal 006 lifecycle и будущие Goal 009 concerns
остаются frozen.

Итог independent review: `FIX_REQUIRED`, bounded successor Goal 008A обязателен;
Goal 009 остаётся `BLOCKED` до отдельного принятия hardening gate.
