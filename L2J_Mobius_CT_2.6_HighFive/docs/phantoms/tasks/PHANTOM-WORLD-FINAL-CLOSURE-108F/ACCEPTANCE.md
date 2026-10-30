# ACCEPTANCE — бинарные native gates, без «почти PASS»

Обозначения: `CODE_VERIFIED`, `DB_FREE_VERIFIED`, `NATIVE_VERIFIED`, `BLOCKED`, `UNKNOWN`. Fixture не замещает native; ненаблюдаемый private evidence нельзя назвать проверенным через GitHub. Каждый gate имеет точный файл + SHA, expected/actual + outcome.

| Gate | Условие PASS | При FAIL |
|---|---|---|
| G0 source & safety | fresh HEAD/branch parity, exact scope, original TEST authority 5000 identities, 250 merchants, foreign JVM/SQL/TCP=0, stable full-visibility preimages, old immutable pins exact | No JVM/SQL write; STOP |
| H1 observer | `addObservers` mark-after-success; negative tests: first failed lookup does **not** block second same-region candidate, denied admission/lease does not reserve region; 2 current labelled human observers measured before A and B | No World acceptance |
| H2 admission reason | For shortfall show fresh/stale ecology revision, `pendingRebalance`, calendar UTC, per-profile `permitted`, topology, local physical demand/prep and phase; don't treat `eligible0` as conclusive | UNKNOWN/block on causality |
| S1 SP/skills | READ-only paired before/after `characters.sp` + sidecar `character_skills` for owned IDs; all negative SP explained by exact canonical skill/economy SUCCESS receipts and class/epoch/identity; duplicate/ambiguous/debit without skill rejected. Positive credits independently earned. | `negativeRows` never disabled; BLOCKED |
| A World | Actual current owned native World `16/16`, **stable16** same `Player` reference/object/lease/epoch for >=30s; 2 observers, no fake placement | No AcceptA/B |
| A economy | 250 native offline `OPEN` in first/second/prestop/stopped: `BUY=63`, `SELL=162`, `MANUFACTURE=25`; no duplicate roles/identities, ledger finite, funding replay0, stock source explained | No clean |
| A stop/exit | Actual ON_PLAYER_LOGOUT `EARNED=1`, outstanding0, safe drain, postexit owned/foreign JVM0/SQL0/TCP0, held Handle `Int32 exit0`, exact checkpoint+postflight hashes+independent review | No clean and NO B |
| B continuity | same original TEST DB, same staged JAR/classpath/config/secret provenance; valid accepted A hash chain, byte-exact A postexit→B before; no extra profiles/grants/reseed | No B launch |
| B World + economy | Same independent A World/observer/250 shops and SP/skill/EXP/economy checks in B; no silent artifact of 16 test cap | No FULL108 |
| B stop/exit | Natural current-owned EARNED logout, no retained processes/sessions, exit0, verified postflight/independent review/clean hash chain | No FULL108 |
| DELIVERY | `git diff --check`, exact files only, tests/build, protected unchanged; scoped commit and normal push; report with discrepancies | BLOCKED/NOT_RUN |

## SP causality requirements

- Every pair is for the **same** `characterObjectId`, `classIndex`, `classId`, skill and immutable identity; SELECT-only snapshot must be tied to exact sealed JVM/DB campaign pre/post cut.
- Formula for a specific actor: `SP_after = SP_before + sum(proven positive native SP credits) − sum(proven uniquely paid CLASS skill costs) − other proven native costs`. No grouping-only excuse: aggregate `SPdelta>0` cannot compensate unexplained individual `−310`.
- For skill `141:1` and class53 canonical cost310 is **necessary but insufficient**; require absent/older before→exactly1 after, correct status `SUCCESS`, native trainer and no doubled application, persisted after restart. If a transaction returns `DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED`, cannot call it SUCCESS; preserve DB fact and investigate.
- Sidecar must not leak secrets, must reject foreign owners/new identity, duplicate rows, inconsistent classIndex and missing before snapshot. Pin query, sorted keys, row count/size, SHA, session, timestamp.
- Historic B108R3D is not retroactively upgraded by a new `character_skills` SELECT. New baseline may start after it, explicitly marked.

## Closure decision

Only all A/B relevant `NATIVE_VERIFIED` + trusted offline source checks → `FULL108=SUCCESS`. Otherwise `FULL108=BLOCKED` or `PARTIAL` with exact failed/unknown gate. Do not soften World16 stable gate to World5 or mark exit0 as full PASS. This closure does **not** mean 128 concurrent World in owner PLAY or complete Phantom World gameplay; those remain independent owner-product goals.
