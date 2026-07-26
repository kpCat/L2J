# Независимое review Goal 006B — server shutdown handoff

## Verdict

```text
Reviewed baseline: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Goal 006B: ACCEPT
Goal 006 overall: ACCEPT
Stage I: COMPLETE
```

Review выполнено до production-изменений Goal 007. Verdict относится к
неизменному commit `82a03342...` и не зависит от последующей реализации
scheduler.

## Проверенные инварианты

- `PhantomSystem.isMaterializationManaged(Player)` классифицирует actor только
  при одновременном наличии headless session, process-local owner
  `OwnerKind.PHANTOM` и exact ownership object ID configured materialization
  service.
- Первый `PhantomSystem.shutdownIfStarted()` расположен до generic
  `disconnectAllCharacters()`, второй bounded вызов — непосредственно до
  `ThreadPool.shutdown()`.
- Generic disconnect не запускает конкурентный cleanup доказанно managed
  Phantom actor; ordinary, detached/offline real и unowned headless Players
  сохраняют штатный путь.
- In-flight drain переиспользуется, повторный server-level вызов не создаёт
  duplicate cleanup.
- При terminal failure configured instance, entry, permit и identity остаются
  retained; результат не маскируется success-сообщением и сопровождается
  aggregate `SEVERE`.
- `ConfiguredShutdownSnapshot` ограничен агрегированными состояниями и числом
  retained entries, не раскрывает профили и не выполняет DB/World access.

## Доказательства

На baseline ранее выполнены shutdown-handoff suite `4/4 ×3`, production
materialization suite `19/19 ×3`, cumulative `ant verify`, `ant jar` и
`verify-task-006b.ps1`. В начале Goal 007 повторный pre-change `ant verify`
подтвердил все runtime regressions; исторический verifier 006B ожидаемо
отверг только новый untracked task package 007 как выход за собственный
immutable allowlist. Production-код Goal 006B до review не изменялся.

## Решение gate

Goal 006B закрыта с `ACCEPT`. Тем самым Goal 006 overall принят и Stage I
завершён. Разрешён только scope Goal 007; Goal 008 и Goal 009 остаются
`NOT_STARTED / BLOCKED` до независимого принятия Goal 007.
