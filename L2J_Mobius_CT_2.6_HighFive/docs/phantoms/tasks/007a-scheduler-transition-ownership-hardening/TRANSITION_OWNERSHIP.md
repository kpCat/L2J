# TRANSITION OWNERSHIP — Goal 007A

```text
processing/boundary-in-flight
→ slot cannot be removed

retained failure
→ only explicit retryCleanup may clear ownership

retryCleanup success
→ truthful non-materialized effective state
→ fresh materialize required for ACTIVE/NEARBY

service owns profile
→ RETAINED_FAILURE regardless of status name

beginStop
→ no new boundary/work
→ finishStop false until pulse and slots quiesce
```

No global scheduler lock is held during lifecycle or work calls. No new
executor, raw thread or per-profile future is introduced.
