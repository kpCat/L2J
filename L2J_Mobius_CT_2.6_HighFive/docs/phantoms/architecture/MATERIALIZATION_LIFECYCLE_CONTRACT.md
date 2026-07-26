# Контракт production materialization lifecycle

## Граница Goal 006

Goal 006 вводит только явный production lifecycle для преобразования
сохранённого Phantom profile в canonical `Player` и обратно. Он не выбирает
профили автоматически и не содержит scheduler activity states, population,
Utility AI, navigation, combat, economy или conversation. Goal 007 не начат.

Goal 006A не расширяет этот scope. Она закрывает только identity boundary,
action/`STOPPING` atomicity, wall-clock budget caller `shutdown` и provenance.
Goal 006 остаётся `FIX_REQUIRED`, а Goal 006A —
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

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
→ World.getPlayer / World.findObject / autosave preflight
→ PHANTOM identity claim
→ повторный World/autosave preflight
→ Player.load с exact object ID
→ обе World maps пусты, exact Player — единственный autosave owner
→ headless outbound attachment
→ domain initialization
→ online status
→ повторная проверка обеих World maps
→ World spawn
→ обе World maps указывают на exact Player
→ открытие action admission
```

Preflight отклоняет любой существующий `World.getPlayer(objectId)`, любой
`World.findObject(objectId)` и любой `containsObjectId(objectId)` в autosave.
После `Player.load` проверяются exact object ID, пустые World maps,
`contains(player)` и отсутствие другого Player с тем же ID через узкий
read-only `containsOtherObjectId`. После spawn обе World maps обязаны указывать
на тот же exact `Player`; различимые ошибки остаются retryable и fail-closed.

`ActionLease` выдаётся только в `ACTIVE`. Его close уменьшает admitted count
ровно один раз; double/stale close безопасен. Cleanup сначала закрывает admission,
затем ограниченно ждёт уже выданные tokens. Произвольный callback executor
наружу не предоставляется.

Service выполняет проверку `RUNNING`, поиск entry и actor admission внутри
одного `_stateMonitor`. Поэтому action либо принят до `STOPPING` и будет drained,
либо переход в `STOPPING` выигрывает и новый action отклоняется. Под этим
monitor нет DB, Player или World work.

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

`shutdown` под state monitor запрещает новые materialization/action admissions,
создаёт или переиспользует один transient service-level `DrainAttempt` и
отправляет один drain command в существующий `ThreadPool`. Caller ждёт latch
не дольше своего wall-clock budget. Command обходит entries в стабильном
порядке profile ID, делает один основной cleanup pass и не более одного
немедленного retry pass.

Caller timeout возвращает `FAILED` с exact retained profile IDs, но не отменяет
`storeMe`/`deleteMe` и не освобождает maps, permit или identity. Пока tracked
attempt выполняется, concurrent/second `shutdown` переиспользует его и не
запускает duplicate cleanup. Успешное позднее завершение может перевести
service в `STOPPED`; после завершившейся ошибки новый explicit `shutdown` может
создать retry attempt. Новый executor, raw thread и per-profile future
отсутствуют. Configured instance очищается только после terminal `STOPPED`.

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

## Server shutdown handoff — Goal 006B

Реальный `Shutdown.startShutdownActions()` сначала вызывает bounded
`PhantomSystem.shutdownIfStarted()`, затем generic disconnect пропускает только
доказанно managed Phantom Player, после чего непосредственно перед
`ThreadPool.shutdown()` выполняется вторая bounded shutdown/observation/retry
попытка.

Managed actor одновременно имеет headless outbound session, `PHANTOM` identity
owner и exact character ownership в configured materialization service. Exact
service map сохраняется до terminal `STORED`, поэтому generic `Disconnection` и
service cleanup не выполняются параллельно.

Persistent failure оставляет configured instance, map, permit и identity
retained. Финальная диагностика имеет aggregate `SEVERE` status и не сообщает
успех. Goal 006B не меняет schema, config или retained REAL_LOGIN recovery.
