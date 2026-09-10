# Goal039 Resume 5 — Goal033 production fixture native-owner bootstrap

## Baseline

Required:
- branch: `feature/phantom-world`
- `HEAD == origin/feature/phantom-world`
- exact parent:
  `8e77b4b94e0a58eafac29504a2845a96b02a3e51`

Parent subject:
`phantom(goal-039): record resume 4 blocker`

Goal039 remains BLOCKED. Goal040 does not exist.

## Accepted Resume closures

Resume 1 closed Goal033A1 topology/no-geodata ingress.
Resume 2 closed Goal015 Background geodata-present test assumption.
Resume 4 closed Goal021/036 source-hash portability:
- strict UTF-8/EOL-only source hash committed;
- 13/13 active pins migrated;
- 10/10 unique paths accounted;
- permanent validator 6/6 PASS;
- static/safety 25/25 PASS;
- Goal036 focused PASS.

Do not reopen these families without a fresh regression.

## Exact current blocker

`GOAL039_RESUME4_GOAL033_HEADLESS_QUEST_OWNER_NOT_LOADED`

Goal033 production-composed fails during PhantomSystem startup:

`Supported content owner is not loaded with its exact identity:
 class.warrior-q401/profession`

The later 0-vs-10 reseed failure is cascading from startup failure.

Resume 4 reproduced the same primary diagnostic twice and stopped.

## Proven root cause

### Production GameServer

Production startup executes:
1. `ScriptEngine.MASTER_HANDLER_FILE`
2. normal server script list

and only later starts Phantom World.

Therefore `PhantomSystem` is correct to expect native ScriptManager owners to
already exist.

Do NOT make PhantomSystem load scripts.

### Generic headless environment

`PhantomHeadlessPlayerTestEnvironment.initialize()` intentionally exposes only
`ScriptEngine(effect-master-only)` through EffectHandler initialization.

It is shared minimal test infrastructure.

Do NOT globally change it to load quests/instances.

### Goal036 focused environment

`PhantomQuestInstanceGoal036Suite` already performs the accepted bounded script
bootstrap after generic headless initialization:

1. `ScriptEngine.MASTER_HANDLER_FILE`
2. `quests/QuestMasterHandler.java`
3. `village_master/ElfHumanFighterChange1/ElfHumanFighterChange1.java`
4. `instances/Kamaloka/Kamaloka.java`
5. `instances/PailakaSongOfIceAndFire/PailakaSongOfIceAndFire.java`

This is the proven prerequisite sequence for current Goal036 supported owners.

### Goal033 production-composed environment

Goal033 initializes the generic headless environment and then directly starts the
full modern `PhantomSystem` with `startConfiguredForTesting()`.

It does not load the Goal036 native owners first.

After Goal036 became part of PhantomSystem composition, this fixture became stale.

## Correct fix owner

Test composition only.

Expected:
- one reusable TEST-ONLY supported-content script bootstrap;
- Goal036 delegates its existing five calls to it;
- Goal033 production-composed invokes it once after headless initialization and
  before first PhantomSystem startup.

Production Java/data/config/build changes are not expected.

## Safety

Production DB `l2jmobiush5` is forbidden even for read/probe.

Only guarded:
- localhost / 127.0.0.1
- port 3308
- database `l2jmobiush5_phantom_test`
- user `l2j_phantom_test`

`prepare-phantom-test-db` is forbidden.
