# Реализационный blueprint: точные исправляемые цепочки

Это source-backed проект внедрения, не готовый вставляемый патч. Все сигнатуры ниже — целевая схема; подстрой только к фактическим существующим API, не выдумывай отсутствующие методы/данные.

## 1. Неизменяемый контракт уровня
Предлагается один `record PartyNpcLevelBand(int minimum, int maximum)` внутри маленького `PhantomPartyFarmEligibility` в party package (или аналогичная минимальная pure policy без дополнительного двигателя).

```java
// PSEUDOCODE: use existing class-level max=85 for players, no max=85 for NPC.
static Optional<PartyNpcLevelBand> forRoster(List<Integer> members) {
    if (members.size() < 2 || members.size() > 9 || invalidPlayerLevel(members)) return Optional.empty();
    int minNpc = Math.max(1, members.stream().mapToInt(x -> x).max().orElseThrow() - 4);
    int maxNpc = members.stream().mapToInt(x -> x).min().orElseThrow() + 4;
    return minNpc <= maxNpc ? Optional.of(new PartyNpcLevelBand(minNpc, maxNpc)) : Optional.empty();
}
static PartyNpcLevelBand forSingle(int level) { return new PartyNpcLevelBand(Math.max(1, level-4), level+4); }
```

Не менять Solo constants и не дублировать диапазон в Native helper. Все caller'ы используют одно правило; уровни перепроверяются после level-up и перед target acceptance.

## 2. Explicit PARTY source-purpose
`PhantomHistoricalBackgroundPlanner.rendezvousSources()` сейчас до `anchor` вызывает `_authority.viableTarget(PlanningCombat.from(state), npcId)` для non-support. Удалить SOLO требование ИМЕННО из Party source flow, но не из Solo `plan/replan`.

Рекомендуемое разделение (предпочтительнее перегрузка с явным enum/flag PARTY_RENDEZVOUS vs SOLO, а не неочевидное использование `supportTravelOnly`):

```java
// PSEUDOCODE
partySources = queryServerTargets(level-4, level+4)
  .filter(stockNormalMonster && attackable && targetable)
  .flatMap(realSpawnAreasAndFarmingAnchors)
  .filter(structuralRouteCertified)
  .filter(notExcluded)
  .boundedFairPages(); // NO soloViableTarget here
```

Перевести на PARTY-purpose и остальные связанные entrypoints:
- `replanLogicalRendezvous` (нет прямого Solo combat requirements);
- `proposeLogicalActivity` (общий target сохраняется, без имитации source reward);
- `rendezvousGraphPlan` (сейчас передаёт PlanningCombat.from(state) в `plan`);
- `rendezvousIngressPlan` (сейчас Solo viability и Solo range);
- `replanVisibleRendezvous` (accepted source, legal travel only);
- `previewLogicalRendezvous/previewVisibleRendezvous` через идентичный policy.

Не менять `viableTarget` и `remainsSuitable` глобально, потому что Solo и offscreen FARM используют их как настоящую safety-защиту. Если `plan` общий, добавить explicit mode/overload и покрыть old Solo regression; вовсе не делать global `combat=null` в обычном Background.

## 3. Roles: consistent durable vs verified
`PhantomHistoricalBackgroundPlanner.logicalRole(state)` сейчас support/damage, `HistoricalBackgroundService.planLogicalActivity` требует exact match, `LogicalPartyCoordinator.discover` выбирает лидера только damage, `AutonomousFarmParty.requirements` всегда первый slot damage.
Заменить единым source-backed potential role mapping (class lineage + durable skill/equipment/progression evidence). Перед чтением SQL убедиться в текущем индексе и bounded max 16 per page; persist primary role string using existing Member.role. Unknown native readiness — отдельный `PENDING_NATIVE_ROLE`, не успешное действие. Для persisted legacy `damage`/`support` договорить explicit migration/compatibility без silent rewrite и с restart test.

`frontline.guardian` (learned aggro), `support.healer/...` (learned heal/support), `utility.spoiler` (learned Spoil/Sweeper), `damage.melee/ranged` — role options, не классовые запреты. Информация о learned skills и equipped weapon берётся из настоящих фактов. `PhantomPartyRoleMatcher` должен продолжать выдавать один официальный assignment на slot без double counting, но DPS вклад разных участников суммируется независимо от назначенного primary slot.

`selectRendezvousTeam` убрать `damageDealer(leader)` как обязательное условие административного лидера. Проверять фактическую role compatibility и общие capability readiness после materialization. Убрать `team.size()<3` из native rendezvousPulse; вместо этого `2..9` плюс RoleMatcher и groupViable. Проверить старые `selectTeam` перегрузки, требующие обязательного support+третьего DD.

Дополнительно: если добавляется `utility.spoiler`, добавить роль в существующий XML `dist/game/data/phantoms/party/high-five-party-roles-v1.xml` с capability weights `profession.spoil/sweep` (точные настоящие capability keys проверить); `PhantomPartyRoleMatcher.match` должен признавать честное наличие; сохранённые role-catalog-hash и restart-fence — регрессировать. Пока нет реального Player нельзя говорить `readyNow=true`.

## 4. Group readiness в native backend
В `L2jPhantomPartyBackend.autonomousFarmTarget` после stock NPC/Geo/reward gates вызвать существующий `groupViable` с действительным `party.members` и `MemberSnapshot` каждого. Не проверять Solo feasibility каждого.

```text
for each player (actual native):
  if real offensive weapon or learned skill and resources: add ONE actual damage contribution
  if guardian: aggro skill potential noted, actual NPC aggression only after stock cast/observation
  if healer: learned cast/MP/target proof, no predicted HP credit as actual reward
  if spoiler: record utility and legal spoil candidates, NO made-up Spoil/loot
compute group DPS vs target current HP+regen; if not enough => WAIT_GROUP_DAMAGE
validate native target/stock attack/reward/Geo/party member level ±4
validate each member's actual situation under native observed attacker targets;
    if unknown risk/aggro distribution => WAIT_TANK_AGGRO or WAIT_THREAT; not automatically PASS
execute only via existing PartyCoordinator/CombatBackend with current leases
```

Важно: существующий groupViable суммирует только `damage(members)`, исключая support, но затем даёт каждому участнику полный mob incoming damage; этот конфликт нужно исправить на основе штатного NPC aggro target и actual Player. Никакого обещания бесплатного танкования или полного HP regen. Если нет надёжной информации о направлении атаки до engage, разрешено безопасно дождаться реального tank aggro/effect или отклонить потенциально опасную сцену; НЕ записывать synthetic AI target.

## 5. Shared objective and durable revision
При новом roster и при изменении уровня запросить `PartyNpcLevelBand.forRoster`. Во время `PhantomLogicalPartyCoordinator.discover` candidate.source intersection только конкретного NPC@anchor, у каждого в его level-band. При смене источника `planLogicalActivity` и `ActivityRevision` должны использовать тот же invariant; не создавать разные цели на одного и того же моба для членов Party. Сохранить native-independent SQL groupId/generation/CAS claims, goal publish and compensation.

## 6. Self-check after coding (before any JAR)
Выполнить source-level grep на все вызовы `viableTarget/PlanningCombat.from`, все вызовы `logicalRole`, `team.size()<3`, `role()==damage`, все `replan*Rendezvous`, `groupViable`, old `LEVEL_RADIUS` uses. Составить табличку PURPOSE|CALLER|SOLO/PARTY|NEW GUARD|COVERING TEST; это обязательное доказательство, что не осталась скрытая двойная SOLO admission.
Внести таблицу в RESULT.md пакета, не заводить отдельные 100 скриптов.
