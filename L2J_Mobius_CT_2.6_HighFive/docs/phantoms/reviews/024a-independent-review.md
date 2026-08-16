# Goal 024A — independent review handoff

## Disposition

```text
required parent: 2603776c6996007b147f93e4c7e79f145ceb8a89
Goal 024: CHANGES_REQUIRED
Goal 024A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
R024A-01: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
R024A-02: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
R024A-03: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 025+: NOT_STARTED
seed: 24002402
```

Этот handoff не является self-accept Goal024 или Goal024A. Принятый Goal024 kernel сохранён;
review должен проверить только corrective R024A-01/02/03.

## R024A-01

- pre-final remaining/progress/acquisition/social drift инвалидирует старый draft и вычисляет новый;
- exact bilateral FINAL хранит remaining/progress только как historical arbitration evidence;
- live binding проверяет pair, goal/revision, source, ResourceKey, stable authority,
  `exactPair`, causal TTL, но не equality current remaining;
- SHARE, WAIT и MOVE переживают обычный monotonic Goal021 progress;
- Goal020 query после progress показывает current remaining.

## R024A-02

- новая negotiation требует fresh bounded Goal010 LOCAL_CHAT perceptibility;
- active/final сохраняют typed `CausalPerceptionReceipt` с pair, generation/hash,
  node/sequence, channel, observed/expiry и evidence hash;
- после OFFER/FINAL исчезновение one-hop не стирает exact pair до TTL;
- loser-first restart exact-load/revalidates только persisted counterpart ID и не ждёт его pulse;
- v1 decode legacy-untrusted, fresh exact pair может безопасно мигрировать в schema v2;
- profile/listProfiles/World scans отсутствуют.

## R024A-03

- manual `observeAgreementOutcome(..., boolean)` удалён;
- real Goal021 MOVE идёт через existing SWITCH/`switchSource` один раз, затем source change
  автоматически освобождает old claim и создаёт bilateral FULFILLED;
- WAIT переживает holder progress и завершается при completion/release/move; SHARE переживает progress;
- TTL даёт EXPIRED, authority drift — STALE; BROKEN без objective exact breach не создаётся;
- FULFILLED/BROKEN social delivery использует deterministic IDs и persisted per-owner retry bits.

## Scope и safety

Новые layers, SQL, workers/Futures, direct chat, PvP/combat, navigation ownership и изменение
Goal021 Source со стороны farming не добавлены. Изменения ограничены farming lifecycle, узким
read-only Goal021 observation seam, focused tests, Ant/verifier и обязательными process artifacts.
Другие хроники, production DB и `.l2j` не затронуты.

## Evidence

- contract: `docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md`;
- report: `docs/phantoms/reports/024a-farming-agreement-lifecycle-corrections.md`;
- verifier: `tools/phantoms/verify-task-024a.ps1`, PowerShell 5.1/7;
- required seed: `24002402`;
- required subject: `fix(phantoms): harden farming agreement lifecycle`.

Goal025+ не начинать до отдельного independent review решения.
