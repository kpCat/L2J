# Контракт server shutdown handoff

## Граница Goal 006B

Goal 006B координирует только реальный `GameServer` shutdown с уже принятой
materialization lifecycle Goal 006/006A. Она не меняет schema, config, identity
recovery, canonical Player cleanup или будущий Goal 007.

Порядок в `Shutdown.startShutdownActions()`:

```text
первый PhantomSystem.shutdownIfStarted()
→ disconnectAllCharacters() только для non-managed Players
→ остальные штатные shutdown-действия
→ второй PhantomSystem.shutdownIfStarted()
→ ThreadPool.shutdown()
```

Обе попытки bounded существующим service contract. Вторая попытка наблюдает
terminal late completion, переиспользует текущий `DrainAttempt` или запускает
один explicit retry после завершившейся ошибки. Новые executor, raw thread и
per-profile future не создаются.

## Managed Player

`PhantomSystem.isMaterializationManaged(Player)` возвращает `true` только при
одновременном выполнении всех условий:

```text
Player не null
Player имеет headless outbound session
identity registry owner objectId == PHANTOM
configured PhantomSystem существует
configured PhantomMaterializationService владеет тем же character object ID
```

Service проверяет только exact `_activeByCharacter` map через read-only
`ownsCharacterObjectId`. Query не запускает task, не обращается к DB/World и не
раскрывает Entry, Player, profile ID или mutable collection.

Fail-closed классификация сохраняет штатный `Disconnection` для обычных,
detached/offline real и unowned headless Players. Пока service cleanup retained
или выполняется, его exact map/lease/headless ownership не позволяет generic
loop параллельно вызвать `Disconnection` для actor.

## Диагностика и persistent failure

`ConfiguredShutdownSnapshot` содержит только:

```text
configured
systemState
serviceState
retainedEntries
```

Snapshot создаётся только по явному shutdown/test запросу, не содержит IDs и не
делает DB access.

Если второй attempt не достиг terminal `STOPPED`, configured instance, exact
maps, permit и PHANTOM identity сохраняются. Перед `ThreadPool.shutdown()`
пишется один aggregate `SEVERE` diagnostic с retained count. Persistent failure
не получает success wording; force delete, direct `characters.online=0` и
generic cleanup managed actor запрещены.
