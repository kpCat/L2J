# Goal034 closure 5 context

Required parent: `79184f2ed8ed4d3d787ac70e9e306b45c6893e7e`.

Closure4 reached the full real stack:
- gen1 5/5/5;
- native restart + Phantom drain PASS;
- gen2 READY/registered but 5/5/4.

Source audit found a restart-specific ecology fence gap. Restored READY profiles can be temporarily published SLEEPING while ecology inventory loads. Each ecology `publish()` can make inventory ready inside the processing loop, so the end-of-pulse false→true detection can be missed. Existing fence callbacks are operation-specific rather than a bounded permission-edge owner.

First reproduce this with existing durable rows. Then fix scheduling permission propagation without full scans/new workers. Black-box must preserve missing gen2 profile IDs before cleanup.
