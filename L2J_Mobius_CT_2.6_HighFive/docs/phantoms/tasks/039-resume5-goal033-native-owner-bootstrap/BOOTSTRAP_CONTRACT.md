# Test-only native-owner bootstrap contract

## Preferred helper

Create a test-only helper such as:

`test/java/org/l2jmobius/tests/phantoms/PhantomSupportedContentScriptBootstrap.java`

Equivalent narrow naming is acceptable.

It MUST NOT live under production `java/**`.

## Exact responsibility

Reproduce the already accepted Goal036 script prerequisite sequence.

Preferred method:
`loadGoal036Owners(PhantomTestContext context)`

The helper may assume:
- JVM working directory is `dist/game`;
- `PhantomHeadlessPlayerTestEnvironment.initialize()` already completed.

## Exact loading order

Preserve:

1. `ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE)`
2. `ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"))`
3. `ScriptEngine.getInstance().executeScript(Path.of("village_master/ElfHumanFighterChange1/ElfHumanFighterChange1.java"))`
4. `ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"))`
5. `ScriptEngine.getInstance().executeScript(Path.of("instances/PailakaSongOfIceAndFire/PailakaSongOfIceAndFire.java"))`

Do NOT replace this with full `executeScriptList()`.

Do NOT invent a second direct quest loader unless this exact accepted sequence is
proven unusable.

## Exact owner proof

After loading, verify all current supported owner identities, not only Q401:

- quest 102 / `Q00102_SeaOfSporesFever`
- quest 152 / `Q00152_ShardsOfGolem`
- quest 401 / `Q00401_PathOfTheWarrior`
- quest 128 / `Q00128_PailakaSongOfIceAndFire`
- script `ElfHumanFighterChange1`
- script `Kamaloka`
- script `PailakaSongOfIceAndFire`

Prefer checking ScriptManager identities/classes and then allowing the existing
`PhantomQuestInstanceCatalog.validateRuntime()` to remain the final authority.

No mock/fake owners.

## Call sites

### Goal036
Replace only the duplicated current script setup with this helper.
Everything else stays behaviorally unchanged.

### Goal033 production-composed
Call helper once:
- after `_environment.initialize(...)`;
- before the first `PhantomSystem.startConfiguredForTesting(...)`.

Do not call helper from `startRuntime()` because that method is used on every
PhantomSystem restart.

The native ScriptManager owners should survive Phantom subsystem restart, matching
production process semantics.

## Generic headless

Do not change the generic EffectMaster-only bootstrap.

## Forbidden

- PhantomSystem loading scripts itself;
- weakening `PhantomQuestInstanceCatalog.validateRuntime()`;
- ignoring absent owners;
- special Q401 fallback;
- direct class-transfer mutation;
- changing Q401/Q102/Q152/Q128 scripts;
- changing instance scripts;
- changing catalogs/pins;
- changing production GameServer startup.
