# ARCHITECTURE — Goal 009

```text
PhantomSystem
  -> PhantomNavigationService
       -> bounded request registry
       -> ArrayBlockingQueue
       -> <=2 transient shared drain workers on existing ThreadPool
       -> bounded LRU route cache
       -> per-profile cooldown/cancellation state
       -> PhantomNavigationBackend
            -> lazy GeoEngine
            -> lazy PathFinding
       -> PhantomNavigationProgressTracker
```

Request path:

```text
direct door/fence-aware validation
→ direct validated/unverified result
OR
capability/budget/cooldown/cache
→ bounded queued legacy A*
→ cancellation/deadline check
→ bounded route validation/cache/result
```

Legacy A* is not preemptively cancellable. The async caller remains unblocked;
late output is discarded and worker ownership is retained until return.
