# PHANTOM-LEARNING-005 — шаблон отчёта (заполняет Codex результатами, не заранее)

## Фактический статус
`FULL005_EVALUATOR` / `PARTIAL` / `BLOCKED`: ...

## Игровой вывод отдельно
SOLO_DAMAGE_V1: `ADVANTAGE_SUPPORTED` / `NO_ADVANTAGE_OBSERVED` / `REGRESSION_OBSERVED` / `INCONCLUSIVE` / `NO_POLICY_DIVERGENCE`.
PARTY_HEAL_V3: ...

## Таблица per-mode
| Mode | fair pairs n | A won/finished | B won/finished | median Δtime | median ΔEXP/min | ΔHP/MP | neural changed+cast | INCOMPLETE/UNMATCHED | Confidence |
|---|---:|---:|---:|---:|---:|---|---:|---|---|
| SOLO_DAMAGE_V1 | | | | | | | | | |
| PARTY_HEAL_V3 | | | | | | | | | |

Только фактические исходы, no fictitious n. Дать path на отдельные arm receipts/event keys. Дать хотя бы один пример реально изменённого выбора и его native result. Предложить, сколько ночей нужно для n=20+ по измеренному throughput, **без обещаний точного времени**.

## Обязательные разделы
- Pinned base, new branch, final independently verified GitHub HEAD and protected refs.
- Exact allowed models and SHA, DRAFT8 rejection and no MAIN/auto-ON.
- Fairness check: scenario, prior state, order balance, stochastic limitations, source hashes.
- Native env: own SQL/ports, Geo203/protected207, JAR SHA, actor/party/mob ids, scopes.
- Real action intervention (what exactly changed) and qualified on/off. Non-divergent cases disclosed.
- COMPLETE/FAILED/INCOMPLETE/SKIP/UNMATCHED counts and reasons.
- Distinct EXP/SP and kill receipts; unknown costs/adёna/loot not silently converted to 0.
- Statistical uncertainty, no win-rate superiority from small n.
- Tests commands/exit, native performance, STOP/Resume, check no duplicate.
- Changed files scoped list, sensitive exclusions, encoding checks, git commands/push.
- Limitations and next step: user night runs / future PvP 1×1/1×2/N×M only after eval trusted.
