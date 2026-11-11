# Performance and budgets

Laptop baseline: HP ZBook Fury17 G8, i7‑11850H, RTX A5000 16 GiB, RAM128GiB. Existing V3 HEAL native total p95=2.59ms on n=8 (4 actual scored); synthetic saved-array 128 logical actors p95 ~1.03–1.06ms at 4/8/16 actions; avoid treating as 128 concurrent real GameServer decisions. Existing process peak ~4.7GB including world. Model ~4.52MiB and one shared CPU instance. No per-actor 4.52MiB copy.

Keep standard: <3–5ms useful native action decision p95 in real verified sample where feasible, else fail-safe stock and report. 128+ visible bots are *population target*, not mandate to run scorer on every tick; 250 offline merchants excluded from materialized cap, 5000 persisted profiles need not hold model copies. Bound maxActions16/maxEntities20 until measured extension; do not invent action candidates.

Benchmark separate: legal candidate extraction, rich native snapshot, encoder, scorer, native dispatch (not part of model inference), end-to-end decision, p50/p95/p99 (sample counts, no fake percentiles from n=1), allocations, GC. Throughput: GameServer boot, useful committed episodes/h and events/h vs wasted incomplete, max total PROCESS working-set and CPU, SQL worker and training separately. GPU is not necessary in this CPU-sized task; compare GPU option only after profiling, no dependency on it in runtime.

Bench deterministic saved observation arrays = mathematical smoke; native elapsed game operations = true gameplay. Both reported, never interchangeable. Don't busy-loop, no per-profile threads, no SQL/training in Combat tick. Preserve AdaptiveIdle/disk quotas and reuse prior Windows launcher.
