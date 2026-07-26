# TEST CASES — Goal 006

## Config/disabled

- false/false/32 canonical defaults;
- disabled effective cap 0;
- enabled 1 and 10000 accepted;
- missing/blank/signed/zero/10001/malformed enabled cap disables settings;
- disabled start does not invoke repository factory/DB sentinel.

## Goal 005 follow-ups

- foreign sentinel profile survives every owned cleanup;
- final explicit sentinel cleanup;
- separately loaded equal component snapshots compare equal/hash equal;
- differing payload compares unequal; defensive copies remain.

## Shared lifecycle

- spike delegates lifecycle to production core;
- production core/service contain no fixture item ID;
- old headless packet/effect/failure matrix stays 18/18.

## Service

- start active zero/no auto materialization;
- missing and unlinked profile results;
- linked canonical Player materialization;
- exact World/output/online state;
- concurrent same profile exactly one winner;
- profile and character uniqueness;
- cap one reject/release/readmit;
- immutable profile-ID-ordered snapshots.

## Identity recovery

- PHANTOM owner blocks;
- RESERVED REAL_LOGIN blocks and is never recovered;
- clean RETAINED REAL_LOGIN recovers/materializes;
- World player/object residue rejects;
- autosave residue rejects;
- DB online 1/2 rejects;
- missing character row rejects;
- token replacement race cannot be removed.

## Action/cleanup

- token only ACTIVE and double close safe;
- cleanup closes admission first and waits for held token;
- new token rejected during cleanup;
- timeout retains actor/permit;
- operation failure retains maps/identity/permit;
- explicit retry reaches STORED.

## Shutdown/restart

- shutdown drains two actors in stable order;
- one-time failure succeeds on one immediate retry;
- persistent failure returns exact IDs and FAILED;
- resources remain retained on failure;
- second shutdown after fault removal succeeds;
- new service/repository after stop has active zero, profiles intact, no runtime
  component writes, and explicit materialization works.

## Metrics/performance

- fixed counters and current/peak exact;
- bounded trace;
- one and ten sequential cycles;
- no World/autosave/lease/thread/item residue;
- no per-phantom executor/future.
