# Контракт production materialization lifecycle

## Граница Goal 006

Goal 006 вводит только явный production lifecycle для преобразования
сохранённого Phantom profile в canonical `Player` и обратно. Он не выбирает
профили автоматически и не содержит scheduler activity states, population,
Utility AI, navigation, combat, economy или conversation. Goal 007 не начат.

Единственный per-actor lifecycle реализует `PhantomMaterializedPlayer`:

```text
STORED → CLAIMED → LOADING → MATERIALIZING → ACTIVE
ACTIVE/FAILED → DEMATERIALIZING → STORED
```

`PhantomPlayerMaterializationSpike` остаётся compatibility/test wrapper. Он
делегирует lifecycle production core и владеет только Task 004 fixture action и
восстановлением fixture baseline. Production core и service не знают item ID.

## Production service

`PhantomMaterializationService` не является singleton. Единственный configured
production instance принадлежит `PhantomSystem`; новый process начинает с
пустыми service maps и active count 0.

Service имеет состояния `NEW`, `RUNNING`, `STOPPING`, `STOPPED`, `FAILED` и
явные операции:

- `start`;
- `materialize(profileId)`;
- `dematerialize(profileId)`;
- `retryCleanup(profileId)`;
- `find(profileId)` и стабильный profile-ID-ordered `list`;
- explicit retained-identity recovery;
- `shutdown` и immutable service snapshot.

Profile разрешается через `PhantomProfileRepository`; требуется существующий
положительный character link. Service не создаёт profile или character.
Concurrent ownership фиксируют две conditional maps: profile ID и captured
character object ID. Link, изменённый пока actor активен, не retarget-ит actor.

Fair `Semaphore` задаёт hard cap `MaxMaterializedPhantoms`. Permit освобождается
только после terminal `STORED`. Общий state monitor используется только для
быстрых state/reservation операций; `Player.load`, World, store и delete не
выполняются под ним. На actor нет thread, future или executor.

## Canonical Player и action admission

Материализация выполняется в следующем порядке:

```text
profile lookup
→ profile/character reservation
→ permit
→ однократная on-demand retained recovery при необходимости
→ PHANTOM identity claim
→ Player.load с exact object ID
→ headless outbound attachment
→ domain initialization
→ online status
→ World spawn
→ открытие action admission
```

`ActionLease` выдаётся только в `ACTIVE`. Его close уменьшает admitted count
ровно один раз; double/stale close безопасен. Cleanup сначала закрывает admission,
затем ограниченно ждёт уже выданные tokens. Произвольный callback executor
наружу не предоставляется.

## Cleanup, retry и shutdown

Cleanup сохраняет принятый порядок:

```text
close admission
→ drain admitted ActionLease
→ stop Player tasks
→ store
→ delete
→ object-ID cleanup postconditions
→ detach headless output
→ release PHANTOM identity
→ remove exact service maps
→ release permit
→ STORED
```

Ошибка до подтверждения postconditions оставляет actor, identity, maps и permit
для explicit `retryCleanup`. Успешный повтор достигает `STORED`; повторный
cleanup после успеха является no-op.

`shutdown` запрещает новые materialization/action admissions, обходит entries в
стабильном порядке profile ID, делает один основной cleanup pass и не более
одного немедленного retry pass. Общий budget не превышает 10 секунд.
Persistent failure возвращает exact failed profile IDs, сохраняет ресурсы и
оставляет service/System в `FAILED`. Второй явный shutdown может повторить
cleanup; background retry отсутствует. Configured instance очищается только
после terminal `STOPPED`.

## REAL_LOGIN ownership и recovery

Identity lease имеет состояния `RESERVED` и `RETAINED`. Только matching
`REAL_LOGIN` lease после failed/incomplete `Disconnection` cleanup может стать
`RETAINED`. Живой `RESERVED` owner никогда не освобождается recovery path.

Explicit или однократная on-demand recovery допускает conditional removal
только того же retained token, если одновременно доказано:

```text
owner == REAL_LOGIN
state == RETAINED
World.getPlayer(objectId) == null
World.findObject(objectId) == null
autosave.containsObjectId(objectId) == false
prepared SELECT online FROM characters WHERE charId=? вернул ровно одну строку
online == 0
```

DB error, отсутствующая/лишняя строка, ненулевой `online`, World/autosave residue
или token replacement отклоняют recovery. Periodic scan, timeout/age release и
startup release-all отсутствуют.

## Restart, persistence и disabled mode

Runtime ACTIVE state не записывается в profile components. После process
restart identity registry и service maps пусты, profile rows сохранены, и
материализация возможна только новым явным запросом.

Configured enabled startup имеет порядок:

```text
scheduler start
→ repository open/schema validation
→ materialization service start
→ RUNNING
```

Disabled settings имеют effective cap 0; `startConfigured` возвращается до
создания repository/service и не выполняет profile/materialization DB query.
Default config остаётся `False / False / 32`. В enabled mode cap обязан быть
unsigned base-10 `1..10000`, иначе вся Phantom subsystem отключается fail-closed.

## Наблюдаемость

`PhantomMetrics` содержит только фиксированные counters: requested/succeeded/
rejected materialization, retained failures, successful dematerialization,
cleanup failures, retained recovery, shutdown failures и active current/peak.
Динамической per-profile metric map нет. `PhantomDiagnosticTrace` остаётся
опциональным bounded sampled ring и принимает только короткое внутреннее событие
с profile/character ID.
