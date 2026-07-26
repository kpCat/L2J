# TEST CASES — Goal 007A

1. Latch-blocked materialize plus concurrent unregister; late success is cleaned
   before slot removal.
2. Same scenario with retained materialization and explicit retry.
3. Retained dematerialization plus newer ACTIVE signal; retained state survives,
   cleanup produces non-materialized truth, fresh materialize restores ACTIVE.
4. Retained materialization plus withdrawal/expiry to SLEEPING; explicit cleanup
   remains mandatory.
5. Guarded real adapter pre-spawn World collision with retained service entry.
6. STOPPING during blocked boundary; no later boundary/work.
7. STOPPING during blocked work sink; finishStop false until release.
8. All prior scheduler, scale, production materialization, shutdown, headless,
   profile, DB, harness, skeleton and performance routes.
