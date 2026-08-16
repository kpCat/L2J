# Goal 025 — independent review handoff

Status: PENDING_INDEPENDENT_REVIEW
Implementation verdict: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Branch: feature/phantom-world
Required parent: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Commit subject: feat(phantoms): add pvp threat escalation
Seed: 25002501
Review ZIP: не требуется и не создаётся.

## Accepted dependency truth

- Goal024A=ACCEPT.
- R024A-01/02/03=CLOSED.
- Goal024 overall=ACCEPT.
- Accepted baseline=922f72c0d422904dcbdc6215a5cc1167a1bb84fb.
- Goal025=AUTHORIZED.
- Goal026+=NOT_STARTED.

## Review scope

Reviewer должен проверить atomic commit относительно required parent и подтвердить:

1. Goal 012/012A остаётся единственным combat owner; legacy Monster attack/cast не ослаблен.
2. Physical Player PvP использует exact target.onForcedAttack(actor), skill PvP — Player.useMagic с exact current authority; ClientPacket отсутствует.
3. Phantom не мутирует HP/CP/PvP flag/PvP kills/PK/karma/drop inventory.
4. CP stock 5591/5592 и skill 2166/1-2 source-derived; registered ItemSkills владеет reuse/Olympiad/consumption; success основан на observed truth.
5. Aggression sources ограничены ACTUAL_ATTACK/FARMING_ESCALATION/PARTY_DEFENSE/REVENGE.
6. Visibility/PvP flag/karma/low HP/selected target/local players не создают source или victim candidate.
7. Proactive force-PK требует persisted owner authority, delivered warning receipt, delay, strength/risk и per-pair budget; reactive/party defense не задерживается.
8. Goal 017/018/020/024 и navigation сохраняют свои owner boundaries.
9. Нет World.getPlayers/global profile scan, второго engine, per-phantom thread/future/timer, другой хроники, production DB, .l2j или unrelated refactor.
10. Disable/start/stop/restart/cancellation и bounded budgets соответствуют contract.

## Required evidence

- docs/phantoms/architecture/PVP_THREAT_ESCALATION_CONTRACT.md
- docs/phantoms/reports/025-pvp-threat-escalation.md
- tools/phantoms/verify-task-025.ps1
- phantom-pvp-goal025-test с seed 25002501
- phantom-pvp-combat-integration-test real Player cases
- plain verify и explicit jar results
- exact changed-file allowlist и task-package SHA-256 guard

## Open finding

- `G025-F01` — process/status: historical `tools/phantoms/verify-task-014a.ps1:145` requires obsolete `Goal 025: NOT_STARTED`; the authoritative roadmap correctly records `Goal 025: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, so the one permitted plain `ant verify` stops at `build.xml:2226`. Production behavior is not implicated; no production fix or verifier rerun is allowed under the Goal025 feature freeze.

Reviewer фиксирует ACCEPT либо findings отдельным review result. Этот файл не объявляет Goal 025 принятым самостоятельно.