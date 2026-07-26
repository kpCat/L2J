# SERVER SHUTDOWN HANDOFF — Goal 006B

```text
Phase A: PhantomSystem.shutdownIfStarted()
Phase B: disconnectAllCharacters(), skipping only proven managed Phantom Players
Phase C: second PhantomSystem.shutdownIfStarted()
Phase D: ThreadPool.shutdown()
```

A managed Player requires configured service ownership, PHANTOM identity and a
headless outbound session.

Persistent failure remains fail-closed and receives one aggregate severe
diagnostic. No force release or direct DB online update is permitted.
