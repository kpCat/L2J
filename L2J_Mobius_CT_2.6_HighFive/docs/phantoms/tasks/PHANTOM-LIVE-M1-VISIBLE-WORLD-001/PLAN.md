# M1 Visible World Implementation Plan

> Execute task-by-task in this fresh Codex chat. Use TDD for behavior changes. Do not ask the user to choose another execution mode.

**Goal:** Nearby phantoms materialize without obvious pop-in/flicker and perform proven normal autonomous native gameplay instead of unexplained idle.

**Architecture:** Reuse topology, scheduler, materialization, selected-decision trace, farming/combat/navigation and LocalPlay Pilot. First measure/prove failure, then add only bounded spatial materialization policy and minimal visible-life repair required by trace.

## Global constraints
- initial HEAD `7872e18fbf37177cf08ab23f342a25e3742bb1d0`;
- population 10000; ActiveTarget 64; MaxMaterialized 128;
- no full 10k scan per human;
- no per-profile timer/thread;
- no test-only production goal/action;
- human interaction target 1, hard max 2;
- full verify forbidden;
- TestAdmin REAL_LOGIN/non-GM/no PersonalQoL.

## Task 1 — Read-first/current failure
- confirm exact repo state/docs;
- find native visibility/known/broadcast source and record exact semantics;
- map locality->scheduler->materialization;
- map ordinary visible Goal production;
- verify selected trace seam;
- inspect pilot;
- write explicit hypotheses before behavior edit.

## Task 2 — Materialization RED
- approaching-human prewarm test;
- exit/re-entry hysteresis test;
- instance mismatch test;
- cap/backpressure test if needed;
- run RED.

## Task 3 — Bounded materialization fix
- source-backed entry/prewarm threshold;
- coherent exit hysteresis;
- topology/instance authority;
- bounded candidate work;
- GREEN topology/activity/materialization regressions.

## Task 4 — Visible idle RED via existing trace
- choose naturally present ordinary idle profile;
- capture Goal/candidate/step/reason;
- write `IDLE_ROOT_CAUSE.md`;
- add exact regression;
- run RED.

## Task 5 — Minimal visible-life repair
- change only proven producer/decision/service seam;
- exclude critical/external-busy profiles;
- reuse native target/move/combat/recovery;
- GREEN regression + touched suites.

## Task 6 — Focused verification/code checkpoint
- relevant focused Ant targets;
- diff/encoding/scope;
- exact code commit/push;
- clean detached `ant jar`;
- record SHA.

## Task 7 — One deployment/connected scenario
- exact backups;
- one restart/deploy;
- verify 10000/64/128/100;
- reuse binding or request one 8-char arm;
- pilot drives approach/return;
- record distances/times/states;
- selected trace and autonomous-life observation.

## Task 8 — One-defect allowance OR final evidence
If connected GREEN, stop changing code and report. If one small concrete defect requires code change, fix/rebuild and use second/final user action if needed. Third action => BLOCKED.

## Task 9 — Final
GREEN: `GREEN — M1 VISIBLE WORLD PROVEN`
Otherwise precise blocker. STOP; no M2.
