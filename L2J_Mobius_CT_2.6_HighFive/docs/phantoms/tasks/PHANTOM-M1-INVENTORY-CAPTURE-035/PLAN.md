# TASK035 execution plan (one bounded vertical)

**Goal:** native full inventory 252/250 safely saved and released without extending bot loot capacity.
**Architecture:** keep stock Player + codec/DB proof; resolve scalar inventory representation vs stock nonQuest count; conservative model drops; no lifecycle bypass.
**Stack:** Java25/Ant, L2J Mobius HighFive, MariaDB own clones, PowerShell existing 032/034 runner, JFR/JDWP if necessary.
**Spec:** DESIGN.md + SCENARIOS.md.

## 0 — Read-first + exact source, ≤40 minutes
- [ ] Read AGENTS/project and entire package, verify package SHA and exact base/remote/worktree.
- [ ] Validate TASK034 evidence archive (hash, source/Jar) and current own 034t/032a state; original SQL sources read-only.
- [ ] Make a one-page consistency matrix: source of `usedSlots`, stock effective nonquest count, equipped/quest, codec/transaction/model consumers, applicable invariants.
- [ ] State one narrow hypothesis and the expected negative result. If it contradicts stock capacity, revise BEFORE editing.

## 1 — Reproduce RED, ≤80 min from task start
- [ ] Write an actual causal test using `PhantomInventoryCapacity031Suite`/native owned fixture or smallest new035 suite. Old unmodified Jar or source must FAIL on 252/250 at same boundary (not admission/setup).
- [ ] Run negative partial tests: no items changed, no releases while native store failed, at-limit new item rejected.

## 2 — Implement minimal root fix + GREEN
- [ ] Implement ONLY required conditional-write source changes. Each source file gets a short comment about representation vs permission.
- [ ] Test C02–C06: old and new capacity, negative stock case, quest/equipment and canonical item parity. No changing the global cap or shortening test timeouts.
- [ ] Keep exact v2 codec and DB schema when possible; prove existing healthy payload binary parity, and compressed250+25 roundtrip.
- [ ] Verify `PhantomBackgroundModel` ordinary item awards use stock-equivalent capacity; no overfull loot.

## 3 — Runtime proof
- [ ] Build and record source SHA/Jar SHA. Start one bounded GameServer on own 035 clone and exact scoped observer; observe pre-Synthetic 36/45 and first positive owned store.
- [ ] Verify immutable FINALIZED/native → SQL exact hashes → loaded Player; original bytes and all item objects; World/AutoSave/lease clean only on successful finalized receipt.
- [ ] Gracefully stop. If first rejection is now another typed guard, preserve evidence and diagnose only directly connected cause; do not loop multi-hour whole scene.

## 4 — Freeze + mandatory regression + publish
- [ ] After causal runtime GREEN, commit candidate source/test and build from exact committed source. Run affected suites and full93/93+11/11 on SAME final Jar, record all negative outcomes.
- [ ] Archive immutable raw (private), hash, inspect exact allowed git diff, normal push. OS JVMs healthy stopped and retained clone data protected. Worktree stays reusable.
- [ ] Report status `GREEN_INVENTORY_SAFE` only when all acceptance gates achieved. Otherwise `PARTIAL_VERIFIED`/`BLOCKED_SAFE_SEMANTICS`, no fabricated result.

Budgets: 2–4h target; hard6h; ≤3 production builds, ≤4 GameServer starts. 4h45m semantic freeze, final30m cleanup/push. Read-first, targeted tests and one good runtime before expensive acceptance.
