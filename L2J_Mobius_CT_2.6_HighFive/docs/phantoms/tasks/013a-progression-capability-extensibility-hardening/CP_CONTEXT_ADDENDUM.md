# Goal 013A — обязательный CP context contract

Это bounded дополнение к Goal 013A. Оно не запускает Goal заново и не расширяет его до PvP, commerce или reconciliation.

## Current server truth

- Canonical `Player` имеет отдельные current CP и maximum CP.
- При уроне от `Playable`/fake player canonical path расходует CP до HP, если не использован `ignoreCP`.
- CP восстанавливается отдельной `Formulas.calcCpRegen` в общем HP/MP/CP regeneration lifecycle.
- Item 5591 восстанавливает 50 CP, item 5592 — 200 CP; оба используют skill 2166 `CP Gauge Potion`.
- Current item facts задают reuse delay 500 ms и Olympiad restriction.
- NPC, buylist/multisell, валюта и цена не выводятся из памяти и не определяются Goal 013A.

## Goal 013A contract

- `PhantomCombatBackend.ActorSnapshot` представляет `currentCp` и `maximumCp` как отдельные Player resources рядом с HP/MP.
- `L2jCombatBackend.actorSnapshot()` копирует их только из exact canonical `Player` под существующим `ActionLease`.
- Snapshot immutable, не владеет CP и не меняет `Player`.
- CP не добавляется в `PhantomProfile` и не получает отдельную persistence.
- Body-bearing servitor/pet snapshots не получают fabricated CP; cubic по-прежнему не имеет body.
- Disabled path остаётся inert.

## Required proof

- current/max CP совпадают с canonical getters;
- следующий snapshot отражает изменение canonical CP, а предыдущий остаётся неизменным;
- snapshot не мутирует `Player`;
- HP, MP и CP не перепутаны;
- servitor/pet/cubic не получают Player-like CP;
- disabled backend не получает actor lease и не создаёт snapshot.

## Future boundaries

- Goal 014 должна получить CP potion supplies, vendors, restrictions, currency и cost из authoritative item/NPC/buylist/multisell data.
- Goal 015 и последующая reconciliation не должны бесплатно сбрасывать или восстанавливать CP при materialization/background transition.
- Goal 025 должна учитывать current/max CP, canonical порядок CP → HP для PvP damage, natural CP regeneration, stock/reuse CP potions, economic consumption и Olympiad restrictions.
- CP tactical desirability остаётся future doctrine.

Goal 013A не добавляет PvP attack policy, CP-based target scoring, auto-use/purchase CP potions, Ancient Adena assumptions, Olympiad doctrine или background PvP simulation.
