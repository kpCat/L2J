# Контракт координации группы Phantom World

Статус: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

## Каноническая Party

`PartyInvitationService` расположен в `model/groups`, не зависит от Phantom и
является единственным сервисом приглашений для packet handlers и внутренних
команд. Он владеет точной парой requester/invitee, монотонным
`InvitationIdentity`, request-полями игроков и pending-флагом существующей
`Party`.

Одна запись приглашения публикуется только после:

1. резервирования обоих индексов;
2. определения managed requester и invitee;
3. успешного `prepare` с сохранением точной identity;
4. установки request-полей.

Accept, refuse, disable, expire, cancel, delivery failure, revalidation failure
и недоступный requester атомарно отсоединяют оба индекса. Только победитель
посылает один terminal callback. Expiry проверяется по requester и invitee.
Закрытие managed registration сначала прекращает admission, затем отменяет все
принадлежащие регистрации приглашения вне общего state-lock.

Сервис не создаёт `GameClient` и не вызывает packet handlers. Managed boundary
переносит только immutable значения. Все изменения состава выполняются
публичными операциями канонической `Party`.

## Durable saga и retry

Компонент `party.state` сохраняет schema 1, roster не более девяти участников и
payload не более 4096 байт. Leader и каждый managed member хранят одинаковые:

- `groupId`, generation и membership revision;
- точного leader и Phantom/real roster;
- objective, role requirements и assignments;
- route и operation;
- manifest, progression и topology hashes.

Фазы операции:

```text
PREPARED -> CANONICAL_PENDING -> CANONICAL_OBSERVED -> COMMITTED
                                                    \-> ABORTED
```

`prepare` приглашения сохраняет sequence и точные requester/invitee object IDs
на обеих managed сторонах до публикации prompt. Optimistic conflict отклоняет
публикацию и выполняет rollback либо переводит спорную запись в
`INCONSISTENT`. Stale terminal identity не изменяет более новую операцию.

Повтор `form` или `inviteTarget` с тем же goal ID/revision и участниками
возвращает idempotent outcome. Другая цель, revision или target конфликтует.
Deadline завершает именно принадлежащее операции core-приглашение.

## Команды состава

Поддержаны явные goal/action пары:

```text
party.leave
party.expel_member
party.transfer_leader
party.travel
```

Команда проверяет goal ID/revision, generation, exact target и текущую
каноническую роль. Сначала выполняется каноническая операция, затем наблюдается
реальная `Party`, после чего обновляются все затронутые claims. Ушедший или
исключённый Phantom становится `SOLO`; disband переводит в `SOLO` всех managed
участников. Реальная смена лидера увеличивает generation ровно один раз.
Повтор уже committed операции idempotent, stale generation не мутирует state.

## Restart и consent

Committed Phantom-only claims восстанавливаются как `RECOVERING`. Реальные
участники не восстанавливаются автоматически. Группа с real leader становится
`SOLO`; незавершённая операция до `CANONICAL_OBSERVED` abort-ится. Новый invite
принимается Phantom только по точной текущей `party.join` goal либо точному
recovery claim.

## Роли и capabilities

Роли определяются string capability keys из строгого content-addressed
High Five каталога. Matcher решает bounded maximum matching для максимум девяти
участников в порядке:

1. число заполненных required vacancies;
2. суммарный contextual score;
3. число заполненных optional vacancies;
4. лексический tie-break.

Один участник занимает не более одной vacancy, но все capability evidence
сохраняются. Результат различает `FILLED`, `MISSING`, `OPTIONAL` и
`UNSUPPORTED`.

Backend выдаёт обычный snapshot без выдуманной target-readiness и отдельный
`capabilities(actor, exactTargetObjectId)`. Tactics запрашивает exact target
только для реально low-HP, low-MP или dead участника. SELF применяется только к
себе, PARTY/PARTY_MEMBER/ALLY — к совместимому участнику.

Support проходит через typed `PhantomPartySupportAction`: capability key,
variant, target scope, exact target object ID и skill ID/level. L2J backend
повторно проверяет каталог, известный skill, Party/instance/range, состояние
смерти, reuse, ресурсы и skill conditions. Capability key не может переименовать
посторонний positive skill; use-all buff/song/dance отсутствует.

## Route authority

На группу существует один navigation request и один `RouteManifest`. Перед
advance проверяются route ownership, deadline, cancellation и topology hash.
Каждый канонический участник обязан иметь snapshot в одном instance.

Missing/cross-instance участник даёт `REGROUPING` или typed failure и не
позволяет сдвинуть waypoint либо получить `ARRIVED`. Dead, casting или attacking
участник не перемещается. Expired movement lease отменяется. Cancel остаётся
group-scoped; snap, teleport и background travel отсутствуют.

## Background gate

Узкий `PhantomPartyParticipationPort` блокирует background directive, farm,
travel, acquire и commit для `LEADER`, `MEMBER`, `RECOVERING` и точных
`CANONICAL_PENDING/CANONICAL_OBSERVED` операций. Причина:
`party.materialized_only`. Bridge создаётся до BackgroundService и получает
coordinator после его startup.

## Pulse и lifecycle

Coordinator использует:

- индекс `groupId -> sorted claims`;
- bounded round-robin queue групп;
- bounded terminal/inbound/tactical-release queues;
- operation и persistence claims.

Каждый просмотренный group/profile/action учитывается в
`PhantomPartyOperationsPerPulse`; pulse не превышает budget и не делает полного
сканирования claims, groups или tactical actions. Startup может один раз читать
DB страницами по 256.

`beginStop` закрывает admission и managed registration, terminal-обработку,
routes и tactical leases. `finishStop` ждёт нулевые operation/persistence,
invitation, queue, navigation, movement и tactical claims. Callback в гонке
либо завершается под claim, либо отклоняется до мутации.

## Вне контракта

Global matchmaking, Rift policy, Command Channel, background party rewards,
текст/LLM/personality, clans, PvP/PK, economy и будущие Goals
018/019/020/023/025 не реализуются в Goal 017.
