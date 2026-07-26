# Независимое ревью Goal 006A — materialization boundary hardening

```text
Goal 006A local boundary hardening: ACCEPT
Goal 006 overall: FIX_REQUIRED pending 006B
Revert: NOT_REQUIRED
Goal 006B: REQUIRED
Goal 007: BLOCKED
```

Локальные границы Goal 006A приняты: полный World/autosave identity preflight,
атомарный action admission относительно `STOPPING`, один tracked service-level
`DrainAttempt`, bounded caller wait, fail-closed retention и explicit retry.

Серверная интеграция остаётся отдельным обязательным finding. В реальном
`Shutdown.startShutdownActions()` generic `Disconnection` выполняется до
Phantom drain, а shared `ThreadPool` останавливается без второй явной
возможности завершить или повторить retained cleanup. Goal 006B должна изменить
только этот shutdown handoff. Schema, config, identity recovery и Goal 007
изменять нельзя.
