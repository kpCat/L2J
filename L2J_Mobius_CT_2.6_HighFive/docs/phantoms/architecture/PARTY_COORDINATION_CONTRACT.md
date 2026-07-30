# Контракт координации группы Phantom World

Статус: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Граница canonical Party

`PartyInvitationService` находится в `model/groups` и не зависит от Phantom.
Обычные `RequestJoinParty` и `RequestAnswerJoinParty`, а также Phantom-команды
используют один сервис и одинаковые проверки. Сервис владеет:

- точной парой requester/invitee и монотонным invitation sequence;
- request-полями игроков и pending invitation существующей `Party`;
- revalidation перед принятием;
- очисткой при refuse, disabled, timeout, cancel и terminal failure;
- выбором client delivery или зарегистрированной managed delivery.

Managed delivery не создаёт `GameClient` и не вызывает packet handlers.
`PartyInvitationDelivery` передаёт immutable invitation в coordinator.
Все изменения состава выполняются только публичными методами canonical `Party`
через общий сервис.

## Durable manifest и member claims

Компонент:

```text
type = party.state
schema = 1
payload <= 4096 bytes
roster <= 9
```

Leader manifest и каждый Phantom member claim содержат один `groupId`,
`groupGeneration`, `membershipRevision`, точного лидера, Phantom/real roster,
objective, requirements, assignments, route, operation и evidence hashes.
Manifest hash вычисляется из канонически отсортированных фактов.

Операция проходит фазы:

```text
PREPARED
CANONICAL_PENDING
CANONICAL_OBSERVED
COMMITTED
ABORTED
```

Сохранение использует существующий optimistic row version. Cross-profile ACID
не заявляется: конфликт поколения, ревизии или manifest переводится в typed
conflict/inconsistent path. Текущая несвязанная goal никогда не заменяется.

## Consent и restart

Phantom принимает новый invite только при точной текущей `party.join` goal,
которая ссылается на requester. После commit точные party goals переходят в
`party.lead` или `party.member`.

При restart:

- committed Phantom-only claims переходят в `RECOVERING`;
- материализуются только точные Phantom members;
- canonical Party пересобирается обычными invite/respond операциями;
- отсутствие лидера приводит к детерминированному выбору минимального profile ID
  и увеличению generation;
- real member refs удаляются из recovery roster;
- группа с real leader становится `SOLO`;
- согласие реального игрока никогда не воспроизводится автоматически;
- незавершённая операция до `CANONICAL_OBSERVED` безопасно abort-ится.

## Contextual roles и vacancies

`high-five-party-roles-v1.xml` — строгий, XXE-safe, content-addressed каталог.
Роли сопоставляются только со string capability keys progression catalog.
Нет таблицы class ID и единственной роли класса.

Matcher учитывает intrinsic/learned/READY_NOW, rank, resources, runtime state и
objective context. Один actor сохраняет все capability facts, но одна vacancy
получает одного primary assignee. Результат различает:

```text
FILLED
MISSING
OPTIONAL
UNSUPPORTED
```

Каждая assignment/vacancy содержит provenance и общий evidence hash.

## Typed semantic acts

`PhantomSemanticAct` содержит string `actKey`, actor/target refs, group identity,
reason, confidence, bounded typed slots и provenance. Он не содержит фразу,
prompt, LLM output или parser state и сам ничего не изменяет. Dispatch разрешён
только при совпадении текущих `groupId` и `groupGeneration`.

## Shared route

Лидер создаёт ровно один navigation request и один `RouteManifest`.
Followers используют его текущую shared waypoint либо текущую позицию лидера
при regroup. Нет per-member pathfinding, snap, teleport или background travel.

Movement каждого Phantom выдаётся через `PhantomCombatService` как
`PARTY_ROUTE`, поэтому оно разделяет per-profile exclusion с combat, respawn и
support. Route хранит progress, regroup, arrived/failed и topology/navigation
hashes; real leader остаётся observation-only.

## Tactics и ownership

Priority planner строит typed directives:

```text
ASSIST_TARGET
PROTECT_MEMBER
HEAL_MEMBER
RECHARGE_MEMBER
RESURRECT_MEMBER
PARTY_SUPPORT
```

Assist/protect повторно подтверждают normal-monster target. Support выбирает
ровно один exact progression action skill/variant. Heal, recharge,
resurrection, buff, song и dance повторно проверяются production backend:
known skill, target scope, Party identity, instance, death predicate, range,
reuse и skill conditions.

Любое действие захватывает общий combat external-action lease
(`PARTY_TACTIC`, `PARTY_SUPPORT` или `PARTY_ROUTE`). Combat session, respawn и
другая external action того же profile взаимно исключаются.

## Scheduler, lifecycle и bounds

Population и Party образуют один `PhantomCompositeSchedulerControlPort`, который
содержит не более восьми stages и считает isolated failures. Существует только
одна scheduler task.

`PhantomPartyOperationsPerPulse` имеет default `64` и диапазон `1..10000`.
Legacy config без ключа получает `64`. Pulse не сканирует World и не создаёт
thread, executor, timer или Future на группу/profile.

`beginStop` закрывает managed admission, cancels invitations/routes и releases
external actions. `finishStop` возвращает `false`, пока остаётся persistence,
invite, navigation, movement или tactical claim. Disabled Phantom World не
загружает каталог, компоненты и coordinator.

## Явно вне контракта

Global matchmaking, Party Match Room, Rift composition, Command Channel,
background party rewards/travel/farming, text/LLM/personality, clans,
reputation, PvP/PK, economy и будущие Goals 018/019/020/023/025 не реализованы.
