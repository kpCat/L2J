# ARCHITECTURE — Task 003

```text
ConfigLoader
  -> PhantomPlayersConfig.Settings

GameServer
  -> guarded PhantomSystem.startConfigured()
       -> PhantomSystem
          -> PhantomScheduler (bounded queue, no worker)
          -> PhantomMetrics (fixed counters)
          -> PhantomDiagnosticTrace (optional bounded ring)

Shutdown
  -> PhantomSystem.shutdownIfStarted()
  -> ThreadPool.shutdown()
```

Disabled graph:

```text
EnablePhantomSystem=false
  -> no PhantomSystem instance
  -> no scheduler/queue/trace
  -> no metric mutation
  -> no DB/network/task/thread
```

Enabled graph:

```text
EnablePhantomSystem=true
  -> one RUNNING PhantomSystem
  -> shared queue running, size 0
  -> scheduled task count 0
  -> no worker
```

Lifecycle:

```text
NEW -> DISABLED -> STOPPED
NEW -> RUNNING  -> STOPPED
STOPPED is terminal
```

Memory is fixed: bounded queue, fixed counters, fixed trace ring. No profile-keyed
collections. Task 004 may reuse the lifecycle owner but must separately prove
canonical Player, session/output seam, identity and cleanup.
