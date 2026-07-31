# Контракт social memory и relationships

## Статус и граница

Goal 018 добавляет числовое субъективное состояние фантома. Оно хранит
детерминированную personality, важные события, асимметричные отношения,
субъективную reputation, счётчики договорённостей и debt.

Компонент не понимает и не генерирует текст, не выбирает и не исполняет action,
не меняет `Player`, `Party`, combat, navigation, economy, clan или PvP.
Party остаётся владельцем канонического факта; social service — downstream
observer, ошибка которого не откатывает Party.

## Authority

Единственный data-driven authority:

`dist/game/data/phantoms/social/high-five-social-v1.xml`

Loader читает исходные bytes, ограничивает размер, запрещает DTD и внешние
entities, требует точный порядок секций, атрибуты, уникальные ключи и глобальные
numeric codes. SHA-256 считается по исходным bytes и сохраняется в state.

Catalog drift возвращает `AUTHORITY_STALE` до mutation. Автоматической
интерпретации старого state под новым catalog нет.

Значения catalog — tuning policy проекта, а не утверждение о retail High Five.

## Identity и persistence

Component:

```text
componentType = social.state
schemaVersion = 1
maximum payload = 4096 bytes
```

`SubjectRef` допускает только:

```text
PHANTOM_PROFILE + positive profileId
CHARACTER_OBJECT + positive canonical character objectId
```

Names, `Player`, `Party`, packets и session state не сохраняются.

Compact binary payload имеет magic/version, authority hash, personality seed,
monotonic epoch-minute, sorted traits, не более 24 relationships и не более
24 memories. Полные event/evidence SHA-256 сохраняются как 32 bytes.
Unknown version, truncation, trailing bytes, duplicates, invalid ordering и
out-of-range values отклоняются. Worst-case payload проверяется тестом.

Store использует существующие profile components и optimistic row version.
Новой schema или migration нет.

## Personality

Trait вычисляется при первом social access:

```text
SHA-256(catalog hash | seed | profileId | trait code)
```

Seed Goal 018: `18001801`. Wall clock, global random state, name, class и race
в расчёт не входят. Один profile/catalog/seed даёт byte-identical state после
restart; разные profile IDs дают детерминированное разнообразие.

## Relationship и memory

Каждая relationship принадлежит owner profile и одному subject. Обратное
направление хранится независимо.

Relationship содержит семь relationship dimensions, четыре subjective
reputation dimensions, пять bounded agreement counters и две временные границы.
Signed debt читается с позиции owner:

```text
positive — subject должен owner
negative — owner должен subject
```

Memory содержит полный event ID, catalog event code, subject, happened/expiry
minute, salience, magnitude и evidence hash. Event ID обеспечивает idempotency
повторной доставки, пока событие входит в bounded important-memory horizon.

## Время, decay и capacity

Service использует:

```text
effectiveNow = max(requestedNow, storedMonotonicMinute)
reduction = floor(elapsedMinutes * unitsPerDay / 1440)
```

Decay выполняется integer/long arithmetic к нулю с сохранением знака. Query
строит projection без DB write. Mutation сначала материализует decay/expiry,
затем применяет event ровно один раз.

Memory eviction:

1. expired удаляются projection;
2. минимальная effective salience;
3. самый старый happened minute;
4. lexical event hash.

Relationship можно вытеснить только при neutral dimensions, отсутствии
unresolved agreement и live memory. Иначе возвращается `CAPACITY_REACHED`.

## Ownership и lifecycle

`PhantomSocialService` — единственный writer `social.state`. Он использует
64 striped locks, bounded access-order cache и не создаёт worker, thread,
executor, future или scheduled task.

Optimistic mutation делает не более трёх попыток с reload exact DB truth.
First-access insert collision перечитывает winner.

Lifecycle:

```text
NEW -> RUNNING -> STOPPING -> STOPPED
```

После `beginStop` новые operations не принимаются. `finishStop` успешен только
при нулевых operation/write claims.

Production order:

```text
profile repository -> social -> party -> decision/scheduler
party drain -> social beginStop/finishStop -> remaining shutdown
```

Disabled Phantom World не загружает social XML и не обращается к social DB.

## Party sink

`PhantomSocialEventSink` имеет no-op implementation и внедряется в coordinator
через backward-compatible constructor. Core invitation service social classes
не импортирует.

Coordinator публикует accepted/refused/expired для каждой managed perspective,
joined, left, expelled и leader transferred только после канонического или
durable party-факта. Event ID выводится из канонического invitation/operation
identity, owner, perspective и counterpart. Retry даёт `IDEMPOTENT`.

`RECORDED`, `IDEMPOTENT` и failure учитываются bounded aggregate counters.
Social exception перехватывается после канонического Party результата.

## Modifiers

Шесть modifier definitions полностью задаются catalog weights. Generic evaluator
возвращает immutable snapshot: bounded delta basis points, trait/relationship/
agreement contributions, до восьми evidence keys и catalog hash.

Modifier query не меняет social state, goal, Party или action state.

## Явные future contracts

- Semantic Pack и conversation engine могут читать typed snapshots, но не
  становятся writer `social.state`.
- Новые event producers обязаны иметь каноническое completion evidence и
  стабильный event ID.
- Изменение catalog требует явного version/migration Goal; hash drift не
  обходится fallback.
- Расширение idempotency horizon сверх bounded important memory требует
  отдельного bounded persistence contract.
