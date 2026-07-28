# Goal 013A — Progression capability extensibility hardening

## 0. Назначение

Это **bounded corrective Goal 013A** после независимого ревью Goal 013.

Goal 013 создала полезный immutable factual catalog и корректную общую dependency
direction, но оставила несколько структурных сужений, которые нельзя переносить
в Goal 014/015/017:

1. один `(classId, capabilityKey)` не допускает несколько вариантов одной
   capability;
2. `READY_NOW` не учитывает item consumption самого skill;
3. summon/cubic facts недостаточны для будущей multi-actor тактики, а cubic
   ошибочно получает body-command flags;
4. equipment candidates отсекаются одной глобальной grade/enchant формулой до
   появления contextual doctrine;
5. focused tests не строят тот catalog composition, который реально создаёт
   production `PhantomSystem` с обычным Game Knowledge;
6. skill-learning path не доказывает отсутствие частичной мутации при сбое
   после начала списания нескольких required items;
7. main/subclass isolation и extension seams не доказаны структурными tests.

Goal 013A исправляет **только эти доказанные seams**. Она не реализует PvE,
PvP/PK/Olympiad, party, Rift, raid, class matchup, equipment doctrine или
master–summon tactical controller.

## 1. Git contract

- Repository root:
  `C:\Users\endim\L2J_Mobius\`
- Work module and Codex launch directory:
  `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Branch: `feature/phantom-world`
- Required parent for Goal 013A:
  `ca50ea28f233e41343035977c55c98129e5d113a`
- Last independently accepted baseline before Goal 013:
  `8dba87e9c1d5828376b80c1ea16c4578726d4947`
- Exact commit subject:
  `fix(phantoms): harden progression capability extensibility`
- Success token:
  `GOAL_013A_PROGRESSION_CAPABILITY_EXTENSIBILITY_HARDENED`

Required history:

```text
8dba87e9c1d5828376b80c1ea16c4578726d4947
  └─ ca50ea28f233e41343035977c55c98129e5d113a  Goal 013
       └─ <one ordinary Goal 013A commit>
```

Mandatory:

- no amend;
- no rebase;
- no squash;
- no merge commit;
- no force push;
- ordinary commit;
- push exact commit to `origin/feature/phantom-world`;
- commit and push even for honest `BLOCKED` or `FAILED`, after reverting unsafe
  production changes and preserving safe audit/tests/evidence.

Before any edit verify:

```text
git branch --show-current == feature/phantom-world
git rev-parse HEAD == ca50ea28f233e41343035977c55c98129e5d113a
git rev-parse origin/feature/phantom-world == ca50ea28f233e41343035977c55c98129e5d113a
git status --short
```

A dirty worktree is a blocker unless every pre-existing file is positively
identified and preserved outside Goal 013A scope.

## 2. Mandatory read-first audit

Read completely before implementation:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
2. `docs/PHANTOM_BOTS_ROADMAP.md`
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`
4. `docs/phantoms/TASK_PACKAGE_STANDARD.md`
5. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`
6. all files in
   `docs/phantoms/tasks/013-class-progression-capability-catalog/`
7. `docs/phantoms/reports/013-class-progression-capability-catalog.md`
8. `docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md`
9. all normalized DR-01…DR-05 files under
   `docs/phantoms/research/high-five-behavior/`
10. this Goal 013A package;
11. all production code under
    `java/org/l2jmobius/gameserver/phantoms/progression/`;
12. `PhantomCombatCapabilityResolver`;
13. the accepted Game Knowledge query/model/data seam used by progression;
14. canonical server code for `Player`, `Skill`, `SkillTreeData`, `ItemData`,
    inventory, `Summon`, `Servitor`, `Pet`, `BabyPet`, `Cubic`,
    `VillageMaster`, subclass switching and skill acquisition;
15. all Goal 013 focused suites and verifier.

Do not redesign accepted subsystems from memory. Reuse canonical server APIs and
existing Phantom lifecycle/ownership contracts.

## 3. Required outcome

After Goal 013A the composition must be:

```text
authoritative High Five facts
→ capability groups
→ independently addressable capability variants
→ immutable actor / controlled-actor / equipment context facts
→ future tactical doctrine (NOT implemented here)
→ Utility AI / planner
→ semantic action
→ canonical server action
```

The factual layer must answer:

- capability exists for the active class;
- exact variant and provenance;
- exact evidence/action skill is learned;
- current equipment/resource/state requirements pass;
- exact variant is ready now.

The factual layer must **not** answer whether using it is tactically desirable.

## 4. Required implementation contracts

### 4.1. Capability group and variant identity

Replace the one-row-per-`(classId, capabilityKey)` restriction with a stable
variant contract.

Minimum invariants:

- `capabilityKey` remains the coarse capability group;
- every executable/evaluable alternative has a nonblank deterministic
  `variantKey`;
- uniqueness is
  `(classId, capabilityKey, variantKey)`;
- multiple variants of one capability for one class are legal and preserved;
- every variant has exact provenance and exact action/evidence skill identity;
- runtime evaluation returns every variant, not one arbitrary first match;
- no `findFirst()` may silently collapse multiple learned evidence skills;
- the combat resolver must not make static catalog `rank` the winner, return on
  the first same-group entry, or stop before trying another supported variant;
- where the current Goal 012 loadout still needs one bounded generic result,
  it must preserve/consider all same-group variants through the generic port
  and use deterministic factual fallback rather than class-specific policy;
- imported accepted Game Knowledge facts receive deterministic progression-side
  variant identities without changing the accepted Game Knowledge subsystem;
- curated progression XML can add a second variant without Java planner/combat
  class branching;
- static `rank` is catalog evidence metadata only, never final tactical
  suitability.

A valid implementation may introduce an explicit evidence mode, but it must be
unambiguous. Prefer one exact action skill per action variant and separate
supporting evidence rather than an unordered list whose first learned member
becomes the action.

No class names, localized names, archetype names or display text may be used to
infer behavior.

### 4.2. Real production composition

Goal 013 tests used inert Game Knowledge and therefore did not prove the catalog
that normal `PhantomSystem` builds.

Add an independent production-composition suite that:

- starts/loads the ordinary accepted Game Knowledge data and query path;
- builds progression through the same dependency order as production;
- independently enumerates all Game Knowledge class capability facts;
- independently parses/counts progression capability seeds;
- proves that every source fact appears exactly once as a progression variant;
- proves deterministic component and combined hashes across repeated builds;
- records exact production-composition counts and hashes;
- does not hardcode a guessed total as the source of truth;
- does not use `PhantomGameKnowledgeService.inertForTesting(...)` for this suite.

The existing inert fixture may remain for narrow synthetic tests, but its
17-rule count/hash must be labelled fixture-only and must not be reported as the
production catalog hash.

### 4.3. READY_NOW resource truth

For every capability variant, effective current resource requirements must
include all applicable authoritative facts:

- explicit curated required items;
- action skill `itemConsumeId/itemConsumeCount`;
- other publicly exposed canonical skill resource requirements that are already
  part of current High Five server truth, including charge/soul consumption
  where a reliable server API exists;
- summon creation/upkeep resources remain distinct summon facts and must not be
  silently converted into owner combat policy.

Mandatory:

- every positive item reference validates against `ItemData`, not against the
  equippable-item subset;
- remove the dead required-item validation condition found in Goal 013;
- referenced resource item IDs must be included in the immutable actor snapshot;
- missing exact item count makes that variant not `READY_NOW`;
- MP/HP, reuse, target, condition and equipment checks remain separate;
- no loader/file/DB lookup occurs in the evaluation hot path;
- no double counting when a curated requirement and skill consumption refer to
  the same item.

### 4.4. Summon/pet/cubic factual seam

Preserve separate identities for `SERVITOR`, `PET`, `BABY_PET`, `CUBIC`,
`SIEGE_SUMMON` and `QUEST_SUMMON`.

Correct and extend the factual model so that:

- cubic is not a `Playable` body;
- cubic does not advertise follow/hold/move/attack body commands;
- each summon variant remains separately addressable by summon skill, level,
  actor/NPC identity and actor kind;
- different summons of one class are not merged into one owner-DPS fact;
- servitor/pet own skill references and mechanical capabilities are represented
  from current server/datapack facts rather than inferred from the owner class;
- support/heal/recharge/buff/damage/control evidence is represented as factual
  skill/mechanic evidence, not as a tactical role;
- lifetime, EXP multiplier, summon item, upkeep, shot consumption, control item,
  food, inventory and pickup facts are preserved;
- a runtime controlled-body snapshot is immutable and sufficient for a future
  coordinated controller to know, where applicable:
  object identity, actor kind, instance, position, current/max HP and MP,
  target object identity, alive/dead state and reference summon skill;
- fields that do not exist for a cubic are explicitly absent/not-applicable,
  never fabricated as zero-valued body truth;
- no summon commands, combat execution, resummon policy, owner/summon planner or
  background reconciliation is implemented in Goal 013A.

Adding another summon effect/NPC variant must pass the generic parser/catalog
contract without modifying a class switch or generic combat lifecycle contract.

### 4.5. Equipment facts and candidate access

Remove the current universal “best item” truncation contract.

Mandatory result:

- no global grade/enchant/P.Atk/M.Atk score decides which owned items remain
  visible to future doctrine;
- no `top 64 from whole inventory` loss before context is known;
- owned equipment is available through a deterministic bounded paged and/or
  filtered query;
- query filters may include exact body part, equipment family and canonical
  compatibility, but must remain factual;
- page size is bounded to at most 64, while every matching item remains
  reachable through paging;
- exact object ID, item ID, equipped state, grade, enchant, body part, family,
  compatibility and source template facts are preserved;
- deterministic ordering is a stable identity order, not tactical preference;
- contextual scoring by skill requirements, weapon type, shield/dual need,
  armor mastery, attack range, speeds, PvE/PvP mode, party role, summon
  dependency, shot cost, survivability, weight or budget remains a future
  doctrine layer;
- exact owned-item equip operation remains canonical and unchanged in meaning.

No item purchase, creation, enchant, augmentation or direct paperdoll insertion.

### 4.6. Main/subclass/certification truth

Add real structural proof that:

- base class and active class remain distinct;
- active `Player.getSkills()` truth is used for current capability readiness;
- switching to a subclass changes the active class capability/evidence view;
- switching back restores main-class truth;
- ordinary main/subclass skills do not leak across class indices;
- legitimately persistent/certification skills are represented separately and
  are not treated as class-tree evidence for the wrong active class;
- `PlayerConfig.MAX_SUBCLASS`, level bounds and `VillageMaster` restrictions
  remain canonical read-only facts;
- no production subclass, Noble, Hero or certification grant is added.

Test-only canonical setup/mutation is allowed only inside the test source tree
and must be unreachable from production code.

### 4.7. Skill-learning failure atomicity

Keep exact real trainer, exact `SkillLearn`, previous level, level, SP,
prerequisites, required items, ownership and cancellation checks.

Additionally:

- aggregate repeated required-item IDs before preflight and mutation;
- no side effect occurs before the final cancellation/ownership check;
- the implementation must not return a normal failure after consuming only a
  prefix of multiple required items;
- do not compensate by fabricating/rewarding replacement items;
- use an existing canonical atomic/bounded server seam if one can be proven;
- if no safe atomic multi-item seam exists, fail closed **before side effects**
  for the unsupported multi-distinct-item case and document the live gate;
- `OnPlayerSkillLearn` is emitted only after successful conservation and skill
  reconciliation;
- SP/items/skill are conserved exactly on success;
- cancellation/failure leaves all three unchanged;
- repeated request remains idempotent.

Do not invoke `RequestAcquireSkill`, packets or NPC bypass.

### 4.8. Facts versus policy and dependency direction

The following must remain true:

- progression/catalog imports no tactical doctrine;
- catalog does not depend on planner/combat;
- planner/combat consume generic capability/query ports;
- no class-specific central switch is added;
- no one-script-per-class architecture;
- persistence, scheduler, materialization, identity and shutdown contracts do
  not change;
- adding a doctrine in a future Goal requires a new doctrine/provider layer,
  not migration of class facts;
- unsupported doctrine/mode returns a bounded empty/fallback result;
- disabled Phantom behavior remains inert;
- no new executor, raw thread, task, future or per-profile scheduler.

## 5. Exact allowed scope

Allowed production/data files:

```text
java/org/l2jmobius/gameserver/phantoms/progression/**
dist/game/data/phantoms/progression/high-five-capabilities-v1.xml
java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java
```

`PhantomCombatCapabilityResolver.java` may change only as required to preserve
variant identity through the existing generic port and safe fallback. No new
combat policy or class branch.

Allowed build/test/tool files:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomProgression*.java
test/java/org/l2jmobius/tests/phantoms/PhantomCapability*.java
test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-013a.ps1
```

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md
docs/phantoms/reports/013a-progression-capability-extensibility-hardening.md
docs/phantoms/research/high-five-behavior/*.md
docs/phantoms/tasks/013a-progression-capability-extensibility-hardening/**
```

Normalized research files may change only to add stable claim IDs, correct the
production-composition provenance, or align factual wording with the corrected
contracts. Do not copy raw research.

Any other file requires a proven blocker and explicit user authorization.
Do not silently broaden the allowlist.

## 6. Hard forbidden scope

Forbidden:

- any other chronicle;
- root `.gitignore`;
- any `.l2j` geodata file;
- `java/org/l2jmobius/gameserver/phantoms/knowledge/**`;
- `dist/game/data/phantoms/knowledge/**`;
- server core/model/data loaders;
- `Shutdown.java`;
- `PhantomSystem.java`;
- scheduler, profile persistence, materialization or identity code;
- config files or config schema;
- SQL/schema/migrations;
- production DB `l2jmobiush5`;
- Goal 014, 015, 017 or later production work;
- NPC commerce, purchase/sell/travel loop;
- zone selection/farming doctrine;
- party solver/lifecycle;
- PvP/PK/Olympiad doctrine;
- raid/Rift/epic coordination;
- semantic pack;
- summon command execution or tactical controller;
- item purchase/create/enchant/augmentation;
- direct profession/subclass/Noble/certification mutation;
- request-packet or bypass simulation;
- per-phantom executor/thread/task/Future;
- high-frequency logging.

If a required correction genuinely cannot be made without a forbidden accepted
subsystem change, stop, remove unsafe production edits, preserve the audit and
tests that prove the blocker, report `BLOCKED`, commit and push.

## 7. Test requirements

Use deterministic seed `130013`.

Create focused suites with real unique assertions. Repeating a small switch
matrix through modulo arithmetic may remain as stress repetition, but must not
be counted as independent semantic coverage.

Required focused proof:

1. **Production composition**
   - ordinary Game Knowledge + progression data;
   - independent source-set parity;
   - deterministic merged hashes;
   - no inert fixture.

2. **Capability variants**
   - one class has several different capability groups;
   - one class has at least two variants of the same capability;
   - both survive build/query/hash;
   - readiness is evaluated independently per exact action skill;
   - adding a data-only variant requires no planner/combat source change;
   - same coarse archetype does not make two classes factually identical.

3. **READY_NOW**
   - active class;
   - learned/unlearned;
   - target;
   - equipment;
   - exact item consumption;
   - MP/HP;
   - reuse/disabled;
   - dynamic conditions;
   - summon/servitor presence;
   - variant A may be ready while variant B is not;
   - resolver does not stop at a blocked/unsupported first variant and does
     not use static rank as final suitability.

4. **Main/subclass**
   - real materialized test Player;
   - main → subclass → main;
   - class index and active class exact;
   - no ordinary skill contamination;
   - certification/persistent skill distinction.

5. **Summon/pet/cubic**
   - several summon variants for one owner class remain separate;
   - real summon/NPC/item/skill references;
   - cubic has no body commands/body snapshot;
   - servitor/pet own evidence is preserved;
   - BabyPet support evidence;
   - pet inventory/pickup versus servitor distinction;
   - new synthetic/data fixture variant passes generic contract.

6. **Equipment**
   - more than 64 mixed owned items;
   - a lower-grade or lower-enchant matching family remains reachable;
   - paging reaches all matching items exactly once;
   - no global tactical score;
   - foreign, incompatible and exact owned-item equip paths.

7. **Skill learning**
   - exact real trainer success;
   - previous level, level, SP, prerequisite and item rejection;
   - cancellation before side effects;
   - duplicate required-item aggregation;
   - injected failure around multi-item mutation proves no partial prefix loss;
   - event only after success;
   - idempotency and exact conservation.

8. **Safe fallback/dependency**
   - unsupported capability/doctrine/mode returns empty/fallback;
   - no class branch;
   - no persistence/scheduler/materialization changes required.

9. **Lifecycle/performance**
   - zero workers/tasks/Futures;
   - no hot-path file/loader/DB scans;
   - page limit <= 64 for owned equipment;
   - 100,000 capability-variant evaluations;
   - 100,000 indexed catalog queries;
   - 50,000 equipment page/filter queries;
   - fixed smoke timeout <= 120 seconds on the existing test environment;
   - operation and actor lease counts return to zero.

10. **Cumulative**
    - all Goal 013 focused suites;
    - all previous Phantom verification targets;
    - `ant verify`;
    - `ant jar`;
    - read-only Goal 013A verifier twice with byte-identical output.

The verifier must inspect semantics, not merely constants/method-name presence.
It must fail on:

- duplicate `(classId, capabilityKey, variantKey)`;
- reintroduction of one `(classId, capabilityKey)` uniqueness;
- arbitrary `findFirst()` evidence collapse;
- missing skill item resource propagation;
- cubic body command flags;
- global equipment preference score/top-N truncation;
- inert-only production composition;
- forbidden files;
- new workers/threads/tasks/Futures;
- direct class/profession mutation;
- packet/bypass simulation;
- production DB credentials/use;
- `.l2j` or other chronicle changes.

## 8. Test database and environment safety

- Use only `l2jmobiush5_phantom_test`.
- Reuse the existing fail-closed Goal 002/002A guard and
  `.phantom-local/Database.test.ini`.
- Never connect to or mutate `l2jmobiush5`.
- No schema changes are permitted.
- Do not commit local credentials.
- Do not add or commit geodata.

## 9. Documentation and report

Update `PROGRESSION_CAPABILITY_CONTRACT.md` with the accepted variant, resource,
summon/cubic and equipment-query contracts.

Normalized DR documents must use stable claim IDs, for example:

```text
DR01-CLASS-001
DR02-PVE-CAP-001
DR03-PVP-MECH-001
DR04-PARTY-CAP-001
DR05-SUMMON-001
```

Each nontrivial claim must retain authority, confidence and source path(s).
Recommendations and disputed retail claims stay separate from current-server
facts.

Create:

```text
docs/phantoms/reports/013a-progression-capability-extensibility-hardening.md
```

The report must include:

- honest status;
- full commit and parent;
- branch and push result;
- exact diff/allowlist;
- old Goal 013 findings and their dispositions;
- production-composition counts and component/combined hashes;
- distinction between fixture-only and production hashes;
- focused test commands/results with unique semantic case counts;
- cumulative `ant verify` and `ant jar`;
- verifier run 1/run 2 output hashes and byte comparison;
- performance measurements and bounds;
- DB guard evidence;
- disabled/lifecycle/concurrency evidence;
- geodata/other chronicle/root `.gitignore` evidence;
- unresolved blockers;
- explicit statement that Goal 014/015/017 were not started.

Update roadmap only as progress truth:

```text
Goal 013: FIX_REQUIRED, bounded correction Goal 013A
Goal 013A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 014: NOT_STARTED / blocked by independent acceptance of 013A
Goal 015: NOT_STARTED
Goal 017: NOT_STARTED
```

Do not mark Goal 013 accepted yourself.

## 10. Completion and blocking policy

Success requires all mandatory contracts and tests.

On success print exactly:

```text
GOAL_013A_PROGRESSION_CAPABILITY_EXTENSIBILITY_HARDENED
```

On blocker:

- do not weaken a contract;
- do not change accepted Game Knowledge/server core to make tests pass;
- remove/revert unsafe production code;
- preserve safe audit/tests/evidence;
- create an honest report;
- ordinary commit and push;
- print an explicit `BLOCKED_...` token.

Do not ask the user or assistant to reconfirm architecture already fixed by this
task package.
