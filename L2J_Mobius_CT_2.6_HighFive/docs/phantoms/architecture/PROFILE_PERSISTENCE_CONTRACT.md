# Контракт core Phantom profile/persistence envelope

## Статус и граница

Этот контракт принадлежит Goal 005 и определяет только устойчивую идентичность
Phantom и небольшой непрозрачный component envelope. Он не определяет
personality, memory, reputation, goals, schedules, activity states,
population, materialization, AI, navigation, combat, economy или conversation.

Repository не подключён к `GameServer`, `PhantomSystem`, config loading,
scheduler или shutdown. Наличие классов и таблиц само по себе не выполняет DB
query и не создаёт profile.

## Core identity

`phantom_profiles.profile_id` — стабильный `BIGINT UNSIGNED AUTO_INCREMENT`
идентификатор profile. Он не зависит от materialized `Player`.

`character_object_id` — optional unique link на canonical character object ID.
Goal 005 не создаёт foreign key к `characters`, не проверяет существование
character и не выполняет handoff. Эти проверки принадлежат Goal 006.

`schema_version` описывает формат core row. `row_version` — optimistic
concurrency token. Core snapshot неизменяем и содержит timestamps с точностью
до миллисекунд.

## Component envelope

`phantom_profile_components` хранит только небольшие low/medium-frequency
непрозрачные payload:

```text
component type: ^[a-z][a-z0-9_.-]{0,63}$
component schema version: 1..65535
payload: 0..4096 bytes
```

Java API копирует payload на входе и выходе. Core layer не интерпретирует bytes
и не регистрирует реальные component names. Тестовый тип `test.opaque` не
является domain contract.

Большие, relational или high-churn компоненты обязаны получить собственную
normalized table с `profile_id`, component schema version и optimistic row
version. Они не должны дублироваться в inline envelope.

## Schema

Installer `dist/db_installer/sql/game/phantom_profiles.sql` содержит ровно два
idempotent `CREATE TABLE IF NOT EXISTS` statement:

- `phantom_profiles`;
- `phantom_profile_components`.

Обе таблицы используют InnoDB и utf8mb4. Link на character уникален, но
nullable. Component primary key — `(profile_id, component_type)`;
`component_type` использует ASCII binary collation. Единственный foreign key
каскадно удаляет components при удалении profile и никогда не удаляет
canonical character.

`PhantomProfileRepository.open()` проверяет текущую schema: tables, engine,
charset, exact columns и bounds, primary/unique indexes и component foreign
key. Connection после `open()` не удерживается.

## Repository lifecycle

Repository:

- получает обычный `DatabaseFactory` connection на каждую операцию;
- закрывает connection, statements и result sets;
- не содержит singleton, cache, worker, scheduler или background task;
- не выполняет silent retry;
- не предоставляет public transaction callback;
- не вызывается автоматически.

Каждая write operation имеет собственную короткую transaction boundary.
Ошибка constraint или SQL приводит к rollback этой операции.

## Optimistic locking

Core update/delete и component update/delete принимают ожидаемый
`row_version`. Изменение выполняется одним conditional SQL statement:

```sql
... row_version = row_version + 1
WHERE ... AND row_version = ?
```

Ноль изменённых rows означает явный `ConcurrentModificationException`.
Last-write-wins, `SELECT FOR UPDATE`, table locks, global synchronization и
автоматический retry запрещены. Два concurrent writer с одной версией дают
ровно одного победителя.

## Restart и disabled behavior

Новый instance repository читает тот же profile/component state без
in-memory cache. Profile delete каскадно удаляет только envelope components.

При выключенном Phantom World нет автоматического repository open, profile
creation/loading, DB query, thread, World actor или network activity.
