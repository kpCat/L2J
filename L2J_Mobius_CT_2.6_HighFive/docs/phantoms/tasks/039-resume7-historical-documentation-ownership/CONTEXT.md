# Goal039 Resume 7 — historical documentation ownership correction

## Baseline

Required:

- branch: `feature/phantom-world`
- `HEAD == origin/feature/phantom-world`
- exact parent:
  `49a33254f2b5645ac8f9966fb60c0b3fa3474d47`

Parent subject:

`phantom(goal-039): record resume 6 blocker`

Goal039 remains BLOCKED.
Goal040 does not exist.

## Resume 6 accepted family closure

Resume 6 closed the complete known
`LEGACY_HEADLESS_FULL_PHANTOMSYSTEM_MISSING_SUPPORTED_OWNER_BOOTSTRAP`
test-composition family.

Fresh accepted evidence before the new blocker:

- Goal032 reset/reseed: 2/2 PASS
- Goal032 ownership: 3/3 PASS
- Goal031 readiness: 3/3 PASS
- Goal030 restart/failure: 3/3 PASS
- Goal030 rollback/release: 3/3 PASS
- Goal030 cross-domain: 6/6 PASS
- Goal033 production: 2/2 PASS
- Goal036: 8/8 PASS
- Goal037 native: 8/8 PASS
- Goal039 static/safety: 26/26 PASS
- final blocked structure: 7/7 PASS
- production changes: 0

Do not reopen that family without a fresh regression.

## Exact current blocker

`GOAL039_RESUME6_GOAL032_DOCUMENTATION_CONFIG_KEY_INVENTORY_STALE`

Target:

`phantom-population-reset-documentation-goal032-test`

Current result:

`0/1 FAIL`

First diagnostic:

`Shipped Phantom config key inventory changed.`

Current shipped config has 23 keys while the historical Goal032 documentation
suite hardcodes 17.

## Why literal 17 -> 23 is wrong

The same suite has additional forward-state assertions that would fail immediately
after a naive count update:

- shipped and local-play preset must have identical key sets;
- current canonical roadmap must be exactly version 3;
- current status must say `Следующий Goal034`;
- current handoff must point to Goal034;
- it owns the ordered future tail Goal032..Goal037 in mutable current docs.

Those assertions are not Goal032's durable contract.

## Historical Goal032 source of truth

`docs/phantoms/reports/032-phantom-reset-operator-control.md`

records the accepted Goal032 contract:

- Goal032 SUCCESS;
- safe Phantom-only reset/reseed;
- preview / confirm / confirm-reseed / cancel;
- no startup auto-reset;
- shared-state fail-closed ownership;
- production DB not used by tests;
- at Goal032 time, exactly **13** shipped config keys were documented;
- ecology tuning was explicitly deferred to Goal033;
- Roadmap v3 and its then-next Goal033 were historical delivery facts.

Therefore Goal032 should permanently own its 13 checkpoint keys and reset UX/safety,
not mutable future config cardinality or "current next Goal".

## Current config ownership

Current shipped config has exactly 23 keys in declared Goal039 scope.

### Goal032 base — 13

1. `EnablePhantomSystem`
2. `EnablePhantomDiagnostics`
3. `MaxMaterializedPhantoms`
4. `MaxScheduledPhantomProfiles`
5. `PhantomSchedulerPulseMillis`
6. `PhantomSchedulerProfilesPerPulse`
7. `PhantomPopulationTarget`
8. `PhantomPopulationActiveTarget`
9. `PhantomPopulationCreationInFlight`
10. `PhantomPopulationBoundariesPerPulse`
11. `PhantomPartyOperationsPerPulse`
12. `PhantomSocialCacheProfiles`
13. `PhantomPopulationTimeZone`

### Goal033 additive ecology — 4

14. `EnablePhantomEcology`
15. `PhantomEcologyPreset`
16. `PhantomEcologyWorldAgeDays`
17. `PhantomEcologyArchiveLimit`

### Goal038 additive Humanized/custom conversation — 6

18. `EnablePhantomHumanizedConversation`
19. `EnablePhantomCustomConversationPack`
20. `PhantomConversationRegister`
21. `PhantomConversationProfanity`
22. `PhantomConversationVariation`
23. `EnablePhantomMatureConversation`

Goal038 report explicitly records the six conversation shipped values.

## Current local-play preset

Current `docs/phantoms/examples/PhantomPlayers.local-play.ini` contains 17 keys:

- Goal032 base 13
- Goal033 ecology 4

It intentionally omits Goal038's six conversation keys.

This is valid current parser behavior because those six settings are optional and
have explicit defaults in `PhantomPlayersConfig`:

- Humanized: true
- custom pack: true
- register: CASUAL
- profanity: CONTEXTUAL
- variation: HIGH
- mature: false

Therefore Goal032 MUST NOT require shipped/preset exact key-set equality.

## Correct ownership split

### Historical Goal032 suite owns

- its exact 13 base keys are present in shipped and preset;
- parser references those 13 keys;
- operator tuning guide documents those 13;
- shipped False/0/0 safety;
- reset commands and AdminPhantom routes;
- no automatic reset in GameServer;
- historical Goal032 report still records SUCCESS and the accepted reset safety
  contract.

It may record additive keys but must not fail merely because later accepted Goals
added them.

### Goal039 final/current suite owns

At the declared final scope, verify current config composition:

`13 Goal032 + 4 Goal033 + 6 Goal038 = 23`

and distinguish:
- shipped: 23 current keys;
- local-play preset: 17 explicit keys;
- the six omitted Goal038 keys resolve to their exact parser defaults.

Goal039 may freeze this declared-scope inventory because future explicit
post-freeze scope is expected to update the final release contract.

### Mutable current docs

Current:
- roadmap
- status
- handoff
- final completion marker

belong to current Goal039 final documentation/freeze validation.

Historical Goal032 must not assert a current roadmap version or next Goal.

## Scope

Expected production changes: ZERO.

Expected main test changes:

- `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationResetDocumentationGoal032Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomFullVisionGoal039Suite.java`

Additional historical TEST-only assertion corrections are allowed only if the
documentation census proves the same exact ownership anti-pattern.

No production config/data behavior changes are needed.

## DB safety

Production database `l2jmobiush5` is forbidden even for read/probe.

Only guarded test DB:
- localhost / 127.0.0.1
- port 3308
- `l2jmobiush5_phantom_test`
- user `l2j_phantom_test`

`prepare-phantom-test-db` is forbidden.
