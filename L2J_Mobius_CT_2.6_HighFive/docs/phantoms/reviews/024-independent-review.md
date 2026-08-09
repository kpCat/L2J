# Goal 024 — independent review handoff

## Disposition

```text
Goal 023C: ACCEPT
R023C-01: CLOSED
Goal 023 overall: ACCEPT
accepted baseline: e67298697eaecc629a03b215a78ffa947233efd3
Goal 024: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 025+: NOT_STARTED
seed: 24002401
```

Исторические `CHANGES_REQUIRED` в `023-independent-review.md` и `023a-independent-review.md` относятся к прежним exact baselines и сохранены. Этот handoff не является self-accept Goal 024.

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
