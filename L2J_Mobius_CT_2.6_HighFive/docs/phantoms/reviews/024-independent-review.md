# Goal 024 — independent review handoff

## Disposition

```text
Goal 023C: ACCEPT
R023C-01: CLOSED
Goal 023 overall: ACCEPT
accepted baseline: e67298697eaecc629a03b215a78ffa947233efd3
Goal 024: CHANGES_REQUIRED
R024A-01: OPEN
R024A-02: OPEN
R024A-03: OPEN
Goal 025+: NOT_STARTED
seed: 24002401
```

Независимый review baseline `2603776c6996007b147f93e4c7e79f145ceb8a89` подтвердил Goal024 kernel, но открыл corrective Goal024A. Принятые части ниже сохраняются; Goal024 не принят до отдельного review результата Goal024A.

## Scope для независимого review

Проверить direct child baseline и commit subject `feat(phantoms): add farming resource negotiation`, затем подтвердить:

- ROOM key равен exact current topology room node; MOB_GROUP key равен node + Source anchor + NPC;
- claim использует только current Goal021 Source/required/progress/remaining и не создаёт второй planner;
- bounded Goal010 `perceptibleProfiles` не вызывает `listProfiles`/`World.getPlayers` и не создаёт новый signal source;
- gate расположен только перед новым TRAVEL_REQUIRED/TARGET_REQUIRED work, а уже dispatched action не прерывается;
- ALLOW/SHARE продолжают acquisition; NEGOTIATE/WAIT блокируют новое work; MOVE проходит через existing Goal021 SWITCH/switchSource;
- exact same Party даёт SHARE без bilateral rounds;
- Phantom↔Phantom FINAL существует с обеих сторон до эффекта, lower-id write order и fault recovery сохраняют stable agreement id;
- arbitration опирается на обе real Goal021 стороны, Goal priority, alternatives, claim age, Goal018 modifiers и current perceptibility;
- набор semantic acts ровно SHARE/WAIT/MOVE/REFUSE/ESCALATE; ESCALATE не вызывает combat/PvP;
- Goal018 владеет history/events, Goal020 — typed query/language; human Player не получает fabricated Phantom state;
- policy strict/content-addressed/bounded, component schema versioned, startup scan и worker primitives отсутствуют;
- production DB, другие хроники, `.l2j`, direct chat/combat/navigation/Party mutation не затронуты.

## Evidence

Архитектурный контракт: `docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md`.

Отчёт и команды: `docs/phantoms/reports/024-farming-resource-negotiation.md`.

Pinned verifier: `tools/phantoms/verify-task-024.ps1` для PowerShell 5.1/7. Goal 025+ не начинать до отдельного решения по этому gate.

## Corrective Goal 024A

- `R024A-01`: разделить mutable pre-final arbitration evidence и stable post-final binding; обычный monotonic Goal021 progress не инвалидирует SHARE/WAIT/MOVE.
- `R024A-02`: сохранить bounded causal Goal010 receipt и exact counterpart-by-ID restart recovery без scans и без scheduler pulse counterpart.
- `R024A-03`: заменить manual boolean outcome production reconciliation по фактическому Goal021 lifecycle, bilateral terminal truth и durable social retry.

Нормативные findings: `docs/phantoms/tasks/024a-farming-agreement-lifecycle-corrections/REVIEW_FINDINGS.md`. Corrective handoff после реализации: `docs/phantoms/reviews/024a-independent-review.md`.
