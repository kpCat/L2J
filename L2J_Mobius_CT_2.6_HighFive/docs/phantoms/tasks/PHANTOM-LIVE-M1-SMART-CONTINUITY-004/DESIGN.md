# DESIGN — intended invariants, not mandatory class names

Codex must read actual source first and may refine implementation details, but the following invariants are mandatory.

## 1. Policy deferral is not a failure

Do **not** solve the 30-second prewarm RED by merely reducing `transitionRetryMaximumMillis`, changing TTLs, or increasing budgets.

Introduce an explicit non-failure transition outcome/state for cases such as:

- materialization admission/reconcile is not ready yet;
- dematerialization is deliberately retained by policy/grace/pin.

Suggested semantic shape:

- `SUCCESS`
- `DEFERRED` (policy wait, no exponential failure backoff)
- `TRANSIENT_FAILURE` (real retryable failure, exponential backoff allowed)
- `RETAINED_FAILURE` (cleanup/lifecycle ownership failure requiring explicit handling)

Exact naming is up to Codex after read-first.

A newly accepted higher-detail signal such as `human.local` must wake/preempt a prior policy defer immediately. It must not inherit a stale 30-second failure deadline.

## 2. Smart dematerialization / continuity

Do not use `TRANSIENT_BLOCK` as the mechanism for ordinary retention.

Create one bounded retention policy with explicit reasons.

Minimum active reasons in this task:

- `NATIVE_VISIBLE` — hard while a REAL client's native region can know the live Phantom Player.
- `REAL_PARTY` — hard while the materialized Phantom is in a native Party that contains at least one non-headless REAL Player.
- `ACTIVE_ACTION` or equivalent — do not tear down an admitted native action in progress; reuse existing action/lifecycle truth rather than duplicate ownership.
- `RECENT_HUMAN` — soft hold for ~60 seconds after losing ordinary human locality/native visibility.

The implementation may include `COMBAT` if the existing native state makes this cheap and unambiguous. Do not grow this task into full future presence-reason implementation.

Soft recent-human hold requirements:

- starts from the last meaningful human/native-visible contact, not from an arbitrary scheduler retry;
- return during the hold cancels dematerialization and preserves the same Player identity/object;
- after expiry, if there is no hard reason and no renewed locality, normal dematerialization may proceed;
- soft-only retained entries must be reclaimable if `MaxMaterialized` pressure blocks a new human-nearby promotion. Prefer deterministic oldest-soft-first reclamation or an equally bounded policy. Hard pins are not reclaimed by ordinary churn.

Keep `MaxMaterialized=128` bounded. Do not increase it to hide churn.

## 3. Travel failure must cause adaptation, not self-retention

`PhantomVisibleFarmTravel` must distinguish:

- navigation pending / actively walking;
- successful arrival;
- retryable infrastructure pressure;
- terminal or repeated route failure (`NO_PATH`, unproven route, route budget, disabled/partial geo where no validated path is possible, etc.).

A travel presence signal may exist only while travel is genuinely pending/progressing. Terminal failure must withdraw it immediately.

On terminal/repeated route failure:

1. stop/cancel only the owned navigation/move;
2. release travel retention;
3. record a bounded transient reachability failure for that route/destination;
4. replan the **same high-level farm intention** to another suitable reachable candidate/anchor;
5. do not choose the same failed route forever during the failure TTL;
6. do not persist a permanent blacklist for a dynamic obstacle.

Do not add hard-coded race/level/anchor/NPC fallbacks.

Prefer extending the existing `PhantomHistoricalBackgroundPlanner` / visible replan path with bounded candidate exclusions/reachability feedback rather than inventing a parallel planner.

## 4. Farming target fallback

The exact persisted farm NPC may remain the macro target, but lack of that exact NPC in the actual reachable farm scene must not create indefinite IDLE.

Choose one source-backed generic solution after read-first:

- allow a bounded set of level/area-compatible suitable NPC IDs for the same farm area in the stock AutoPlay policy; or
- after a bounded no-target interval, replan to another suitable target/anchor.

Do not make AutoPlay attack arbitrary monsters and do not special-case Gremlins.

## 5. Party continuity boundary

This task does **not** implement M2 party gameplay.

It only ensures the lifecycle invariant:

> once a Phantom is canonically in a native Party with at least one REAL player, ordinary locality/demotion cannot silently dematerialize it.

Reuse native Party truth and existing phantom identity/materialization ownership. Do not invent a second party state.
