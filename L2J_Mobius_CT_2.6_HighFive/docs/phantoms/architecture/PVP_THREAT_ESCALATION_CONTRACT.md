# PvP, threat и bounded escalation contract

Статус: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal: 025-pvp-threat-escalation
Accepted parent: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb
Seed: 25002501

## Зафиксированные dependency gates

- Goal 024A: ACCEPT.
- R024A-01, R024A-02, R024A-03: CLOSED.
- Goal 024 overall: ACCEPT.
- Accepted baseline: 922f72c0d422904dcbdc6215a5cc1167a1bb84fb.
- Goal 025: AUTHORIZED; после реализации — IMPLEMENTED_PENDING_INDEPENDENT_REVIEW.
- Goal 026+: NOT_STARTED.

## Владельцы

Goal 012/012A остаётся единственным владельцем combat session, actor lease, capability resolution и worker budget. Goal 025 добавляет отдельный explicit Player PvP path внутри тех же PhantomCombatService, lease и worker; legacy Monster attack/cast path не изменён и не ослаблен.

Смежные владельцы не дублируются:

- Goal 017 владеет party/help evidence;
- Goal 018 владеет revenge и social memory;
- Goal 020 владеет language/chat, durable outbound submission и receipt;
- Goal 024 владеет farming escalation authority;
- navigation владеет retreat route и movement;
- canonical Player, PlayerStatus, item handler и death/reputation paths владеют игровыми последствиями.

## Допустимые источники агрессии

| Source | Тип | Немедленное действие | Дополнительные gates |
|---|---|---:|---|
| ACTUAL_ATTACK | reactive | да | exact current attacker evidence |
| PARTY_DEFENSE | reactive | да | exact Goal 017 protection evidence |
| FARMING_ESCALATION | proactive | нет | exact bilateral Goal 024 ESCALATED, warning receipt, delay, risk, per-pair budget |
| REVENGE | proactive | нет | exact Goal 018 persisted revenge evidence, warning receipt, delay, risk, per-pair budget |

Visibility, PvP flag, karma, low HP, selected target и локальные игроки являются только контекстом. Они не создают aggression source и не могут породить victim candidate.

## Canonical execution

Physical Player PvP делегируется canonical target.onForcedAttack(actor) и считается выданным только после observed PlayerAI intention ATTACK с тем же exact target.

Skill Player PvP делегируется canonical Player.useMagic(skill, forceUse, false). Skill допускается только при exact current authority, известном exact skill/level и безопасном one-target hostile contract. ClientPacket не создаётся.

Legacy Monster path использует прежние attack/cast; PvP request хранится отдельно, а PvP branch никогда не читает Monster targetSnapshot.

## CP и последствия

Phantom-код не мутирует HP, CP, PvP flag, PvP kills, PK kills, karma и death-drop inventory.

CP следует canonical CP→HP damage и natural regen. Допустим реальный stock item 5591/5592; skill 2166 level 1/2 выводится из item source. Reuse, Olympiad checks и consumption выполняются только registered ItemSkills handler. Успех фиксируется только по observed inventory/CP/reuse truth.

PvP/PK/karma/drop risk читается из canonical Player, PvpConfig и RatesConfig; actual outcome создаётся только штатными Player/Playable death и reputation paths.

## Bounded threat и local risk

Нельзя использовать World.getPlayers, глобальный profile scan или новый engine. Exact candidate IDs приходят только из четырёх causal owners.

Для уже выбранной exact causal пары L2jCombatBackend делает bounded scan только surrounding regions actor-а. Учитываются живые видимые Player в том же instance и в локальной дистанции actor-а или exact target. Детерминированный TreeMap ограничивается localRiskPlayerLimit в диапазоне 1..32.

Из scan наружу выходят только observedPlayers, actorSupport, targetSupport и применённый limit. Идентификаторы локальных Player не возвращаются, поэтому local risk не может создать новую цель. Эти агрегаты используются только в RiskSnapshot.

## Proactive force-PK

Proactive neutral-target force use разрешён только при одновременно выполненных условиях:

1. exact stronger current authority;
2. persisted owner evidence Goal 024 или Goal 018;
3. durable Goal 020 warning submission и observed delivered receipt;
4. истёк configured warning delay;
5. target остаётся exact/resolvable/visible и authority hash не изменился;
6. strength/risk policy разрешает действие;
7. per-pair proactive budget не исчерпан.

Reactive actual attack и party defense не требуют warning delay. Отказ, expiry, terminal combat или retreat переводят encounter в bounded cooldown.

## Persistence, lifecycle и budgets

Encounter хранит exact counterpart, source, authority hash, stage, warning/help receipts, proactive engagement count, logical timestamps и optimistic row version. Restart восстанавливает только materialized profile через lifecycle; startup global scan отсутствует.

Один bounded queue обслуживается существующим shared scheduler pulse. Лимиты: 2048 tracked profiles, 16 profiles per pulse, 32 observed attackers/local players, один active encounter и один Goal 012 combat claim на profile. Нет thread/future/timer на фантома.

Disable gate выполняется до policy load и service construction. Stop сначала закрывает admissions, затем отменяет только owned PvP/combat/retreat work и освобождает shared leases.

## Out of scope

Нет второго combat/social/chat/navigation engine, изменений других хроник, production DB, .l2j, client protocol, dependency/build-system замены, clan/raid Goal 026+ и unrelated refactor.