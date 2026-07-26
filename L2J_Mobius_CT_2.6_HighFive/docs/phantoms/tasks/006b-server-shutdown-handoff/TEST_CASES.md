# TEST CASES — Goal 006B

- active configured Phantom classifier true;
- ordinary/detached/unowned Player classifier false;
- first drain before generic disconnect;
- managed actor excluded, ordinary actor included;
- second drain before ThreadPool phase;
- blocked first drain reused and completed by second phase;
- persistent failure stays configured and logs incomplete state;
- no concurrent generic/service cleanup;
- shutdown-handoff suite ×3;
- production materialization ×3;
- all cumulative regressions.
