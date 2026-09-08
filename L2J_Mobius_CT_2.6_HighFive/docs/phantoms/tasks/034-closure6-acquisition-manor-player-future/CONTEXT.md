# Goal034 closure 6 context

Required parent: `d2096a37d1458977c2dd5e81e329c6732e11aa59`.

Closure 5 is correctly `BLOCKED`, not failed architecture:
- restart ecology regression failed `8/9` before its bounded fix and passed `9/9` after it;
- Goal034 contract passed `18/18`;
- DB negative guard passed `1/1`, driver loads/connections `0`;
- latest real runs reached gen1/gen2 desired/expected/online `5/5/5`, same IDs, no missing/unexpected IDs and stable identity/ecology continuity;
- terminal lifecycle background capture regressions passed;
- first post-fix full verify hit a transient combat cleanup predecessor failure, whose immediate focused repeat passed `20/20`;
- the single allowed full verify repeat then failed independently at `acquisition-manor-active.after-all` with live Player futures `[Player._skillListTask]`.

Confirmed source facts for this resume:
- `ant phantom-acquisition-manor-active-test` runs `PhantomCombatServerIntegrationSuite(Mode.MANOR)`;
- suite `afterAll()` calls its cleanup path, materialization shutdown, then retained Player cleanliness checks;
- test environment rejects any reflected `Future` that is neither done nor cancelled;
- `Player.sendSkillList()` arms `_skillListTask` with a short scheduled delay;
- canonical `Player.stopAllTasks()` cancels and clears `_skillListTask`;
- `PhantomMaterializedPlayer.cleanup()` currently calls `Player.stopAllTasks()` before lifecycle `beforeStore -> storeMe -> afterStore -> deleteMe`.

Therefore the primary hypothesis is lifecycle re-arming after the first canonical task stop. It is a hypothesis, not permission to patch blindly: closure 6 must prove the exact creation/order edge first.

Current repository documentation is still Roadmap v4. The separately agreed Roadmap v5 changes (strengthened 100% quest-script/rates audit, Humanized Russian Semantic Pack/custom overrides/contextual profanity/optional mature conversation, final gate moved to Goal039) are intentionally NOT part of this code closure. After Goal034 SUCCESS, stop; the next user-issued task will be documentation-only Roadmap v5 synchronization before Goal035.
