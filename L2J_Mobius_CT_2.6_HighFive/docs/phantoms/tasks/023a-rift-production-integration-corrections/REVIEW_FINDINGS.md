# Independent review findings — Goal 023 baseline 840e159a

## Verdict

```text
Commit: 840e159a989f6372da9c471c915413f1e4470daf
Parent: 1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb
Subject: feat(phantoms): add rift readiness and advanced party recruitment
Review result: CHANGES_REQUIRED
Goal 023 accepted baseline: NONE
Corrective action: Goal 023A
Goal 024+: NOT_STARTED
```

Commit shape, branch head, parent, subject и 37-file scope подтверждены. Ни один finding ниже не основан только на Codex report.

## R023A-01 — Existing canonical party cannot be rebound to `rift.prepare`

Severity: `P1 / acceptance blocker`.

Evidence:

- `PhantomRiftService.requestInvite(...)` сначала вызывает `PartyPort.ensureFormation(...)`, затем `invite(...)`.
- `L2jPhantomRiftPartyPort.ensureFormation(...)` делегирует `PhantomPartyCoordinator.formForGoal(...)`.
- `formForGoal(...)` возвращает `CLAIM_EXISTS`, когда у leader уже есть non-SOLO committed Goal 017 claim, если claim не был создан тем же exact Rift goal.
- `PhantomPartyCoordinator.requestRoute(...)` принимает route только при exact persisted `StateStatus.LEADER` claim.

Consequence:

- обычная уже сформированная Phantom/mixed party не может переключиться на Rift recruitment;
- composition-ready live party без подходящего Goal 017 claim не может передать shared route;
- service может повторять replan/claim rejection, хотя canonical Party roster валиден.

Required closure:

- один Goal 017-owned content binding/adoption seam;
- exact reconciliation live canonical Party ↔ durable party claims;
- поддержка no claim, SOLO claim, matching committed LEADER/MEMBER claim;
- conflicting claim fail-closed;
- сохранение canonical membership, group identity и real members;
- binding выполняется отдельным Decision stage до invite/route;
- нельзя маскировать проблему простым `CLAIM_EXISTS -> IDEMPOTENT` без обновления exact content binding.

## R023A-02 — Managed Phantom candidate normally cannot accept a Rift invite

Severity: `P1 / acceptance blocker`.

Evidence:

- Goal 017 `processManagedInvitation(...)` auto-responds ACCEPT only при собственном exact active `party.join` goal invitee, targeting requester.
- При отсутствии такого goal метод оставляет invitation pending; при несовместимом `party.join` — REFUSE.
- `PhantomPartyDecision` обслуживает только explicit `party.join`; он не выполняет candidate discovery и не создаёт consent goal.
- Goal 023 выбирает Phantom candidate и вызывает invite, но не создаёт target-side proposal/policy decision.
- Goal 023 tests используют `TestPartyPort`, где `inviteStatus` вручную переключается на ACCEPTED/REFUSED; production consent path не исполняется.

Consequence:

Rift recruitment Phantom-кандидата обычно заканчивается expiry, даже когда кандидат eligible и должен согласиться по policy.

Required closure:

- target-side managed invitation policy seam, owned by Goal 017 lifecycle;
- deterministic `ACCEPT | REFUSE | DEFER` from invitee-side exact facts/policy;
- leader не имеет права безусловно подменять goal другого Phantom;
- explicit `party.join` и conversation consent сохраняют приоритет/совместимость;
- ordinary real Player никогда не проходит auto-consent;
- exact offer/invitation identity и restart behavior обязательны.

## R023A-03 — Candidate is not revalidated immediately before canonical mutation

Severity: `P1 / acceptance blocker`.

Evidence:

`PhantomRiftService.requestInvite(...)` повторно читает party readiness, но перед invite проверяет только pending candidate, full-party, roster hash и отсутствие candidate в roster. Не перечитываются в полном объёме:

- current candidate MemberFacts/evidence;
- alive/vitals/instance/location/perceptibility;
- current party membership;
- exact missing vacancy/RoleMatcher eligibility;
- recent refusal/cooldown;
- incompatible Goal 017 claim/operation;
- current catalog/policy/config/role hashes;
- current goal ownership beyond previously supplied values.

Consequence:

Кандидат может умереть, сменить instance/class/capabilities, вступить в другую party или получить conflicting operation между SELECT_CANDIDATE и REQUEST_INVITE, но stale invite всё равно будет delegated.

Required closure:

В том же REQUEST_INVITE pulse, непосредственно до единственного Goal 017 invite, выполнить exact refresh и все восемь pre-mutation checks из Goal 023 TASK. При drift — zero canonical invite, zero attempt increment, typed stale reason и re-evaluation.

## R023A-04 — Persisted state lacks exact party/invitation identity and does not fail closed on source drift

Severity: `P1 / acceptance blocker`.

Evidence:

Current `rift.preparation` schema stores roster/source hashes, pending candidate и invitation sequence, но не хранит:

- party group ID/generation/membership revision;
- exact requester/invitee object IDs alongside sequence;
- selected vacancy/candidate evidence receipt sufficient for pre-invite replay;
- explicit migration marker for untrusted v1 pending state.

`sameGoal(...)` compares goal/tier only. A persisted REQUEST_INVITE can survive catalog/policy/config/role drift without forced reset when roster hash happens to remain equal.

Consequence:

Restart/reload cannot prove which party operation and invitation are being observed; a stale selected candidate may be reused after authority drift.

Required closure:

- `rift.preparation` schema v2 or an equivalent backward-compatible encoding;
- bounded typed party binding and full invitation identity;
- v1 decode must be safe but v1 operational receipts are untrusted and force replan before mutation;
- source/policy/config/role/binding drift clears or quarantines pending action and never duplicates invite;
- payload remains <=4096 bytes, refusal history <=32.

## R023A-05 — Invite timeout policy is parsed but not authoritative; expiry is misclassified

Severity: `P1`.

Evidence:

- Rift policy contains `inviteTimeoutMillis="30000"`.
- `PhantomRiftService` does not use/pass this value for Goal 017 invitation deadline.
- `PhantomPartyCoordinator.deadline()` hardcodes 30 seconds for multiple operation kinds.
- canonical `Player.REQUEST_TIMEOUT` is 15 seconds and `PartyInvitationService` creates the actual expiry.
- adapter maps ABORTED to TIMED_OUT only when failure text contains `timeout`; canonical terminal reason uses `party.invite.expired`.

Consequence:

Policy claims authority it does not have, and canonical expiry can be recorded as refusal instead of expired/timed-out evidence.

Required closure:

- default policy value 15000 ms;
- effective invite expiry derived from exact canonical invitation expiration and bounded by policy, never longer than canonical consent window;
- no change to `Player.REQUEST_TIMEOUT`;
- typed terminal mapping: ACCEPTED, REFUSED, EXPIRED/TIMED_OUT, CANCELLED/REJECTED remain distinct;
- no string-substring classification.

## R023A-06 — Pending/conflicting operations and required semantic facts are not represented

Severity: `P1/P2`.

Evidence:

- readiness has no read of Goal 017 pending/conflicting operation and may report `READY_TO_ENTER` while party coordination is not stable;
- `latest(...)` reevaluates readiness but does not merge exact persisted pending/refusal state;
- `semanticFacts(...)` never emits `RIFT_INVITE_REQUEST` or `RIFT_INVITE_REFUSED`;
- Goal 020 adapter does not map these two fact types;
- exact pending invite can therefore be verbalized as `NEEDS_ROLE`, not `INVITE_PENDING`.

Required closure:

- party binding snapshot exposes bounded operation stability;
- `READY_TO_ENTER` requires no pending/conflicting operation;
- latest facts merge canonical readiness with exact non-stale persisted receipt;
- emit/map `RIFT_INVITE_REQUEST` and `RIFT_INVITE_REFUSED`;
- stale candidate/roster/goal/source identity suppresses old fact.

## R023A-07 — Candidate source ordering/ranking contract is incomplete

Severity: `P2`.

Evidence:

`L2jPhantomRiftBackend.nearbyCandidates(...)` sorts all visible Players by object ID and applies limit before converting to Phantom/real identity. This does not implement required source order “known nearby Phantom first, real visible second”. A Phantom beyond the first 32 object IDs is skipped behind real Players. Relationship/reputation modifier is absent from `CandidateScore` despite Goal 018 service being available in production composition.

Required closure:

- bounded local/perceptible query only;
- partition/order managed Phantom first, ordinary real second, then stable identity;
- <=32 actual candidate facts evaluated;
- relationship modifier from accepted Goal 018 when exact query is available; fail-neutral 0 only for explicit unavailable/not-running result, never invented data;
- test 32+ reals plus a higher-object-ID managed Phantom.

## R023A-08 — Acceptance proof, metrics and roadmap are incomplete

Severity: `P2`, but blocks final acceptance in aggregate.

Evidence:

- all eight Goal 023 modes use `TestBackend`/`TestPartyPort` for recruitment lifecycle;
- no acceptance mode instantiates production `L2jPhantomRiftPartyPort` with real `PhantomPartyCoordinator` for the failing seams;
- verifier is primarily structural/token-based and cannot prove runtime consent/binding;
- metrics omit several required status/terminal/rejection families;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` was updated, but `docs/PHANTOM_BOTS_ROADMAP.md` still has stale Goal 022/023 status.

Required closure:

- production-seam integration tests, including canonical `PartyInvitationService` path and no fake GameClient;
- bounded required metrics without IDs in labels;
- exact review/roadmap/master-plan status;
- original Goal 023 verifier remains historical/descendant-compatible; new verifier 023A pins exact corrective child.

## Review acceptance rule

Goal 023 cannot become ACCEPT merely because Goal 023A tests pass. Successful Goal 023A status is:

```text
CORRECTIVE_023A_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

A later independent review decides Goal 023 overall acceptance. Goal 024 must remain untouched.
