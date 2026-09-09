# Goal036 — bounded whitelist quests / class transfer / Kamaloka / Pailaka

Status: SUCCESS

## Read-first audit and scope

- Initial files opened: `Agents.md`, the complete Goal036 task package, the
  Roadmap v5 sections of the master plan/roadmap/current status/handoff, workflow
  and efficiency standards, and the Goal035 report.
- Additional files opened: the accepted acquisition Q102/Q152 catalog/parser/
  service/tests; native Q102/Q152/Q401/Q128 scripts; `Quest`, `QuestState`,
  ScriptManager/ScriptEngine; the normal first-profession owner; InstanceManager,
  Instance/InstanceWorld/InstanceScript; Kamaloka/Pailaka controllers and XML;
  existing Materialization, Combat, Party, Navigation, Progression and Decision
  seams used by the implementation.
- Repository searches were bounded symbol/path searches around those owners and
  later failed-test diagnostics. No full `quests/**` inventory was performed;
  that remains Goal037.
- The required vertical crosses more than ten files. The bounded exception is
  limited to one catalog/service/backend/Decision family, two additive shared
  composition seams, one focused suite/build route, this report and four current
  status documents. No schema, native quest, profession or instance script changed.

## Native source-fact audit

| Content | Native owner and precondition | Canonical truth / safe seam | Terminal, restart and cleanup |
|---|---|---|---|
| Q102 | `Q00102_SeaOfSporesFever`; Elf, level 12; Alberius 30284 | `QuestState`; exact pinned event/talk calls; existing source-hashed acquisition rule owns the cond-2 kill subset | Native COMPLETED and item 1060 reward; resume derives from QuestState; fixture removes owned quest/items/character |
| Q152 | `Q00152_ShardsOfGolem`; level 10; Harris 30035 | `QuestState`; pinned Harris/Altran operations; existing source-hashed Stone Golem acquisition rule | Native COMPLETED and item 23 reward; retry observes completion; exact fixture cleanup |
| Q401 / Warrior | `Q00401_PathOfTheWarrior`; Human Fighter level 18+, then `village_master.ElfHumanFighterChange1` at level 20 with item 1145 | Q401 QuestState/inventory, followed only by normal-player owner event `1` at Ramos 30373 | Native owner consumes the mark, sets class/base class 1 and grants 15 item 8869; retry observes class truth |
| Kamaloka 57 | `instances.Kamaloka.Kamaloka`; native XML level 23, party max 6, boss 18554 | Native entry event `0`, Instance/InstanceWorld, shared Combat | Boss callback sets native reuse and five-minute exit window; restart observes instance/reuse; fixture destroys its instance |
| Pailaka 128 | Q128 level 36–42 plus `PailakaSongOfIceAndFire`, template 43 | Native Quest/Instance events, native boss callbacks and exact quest-weapon handoffs 13034→13035→13036 | Conds 1–9, Adler 32510 terminal, rewards 13294/13293/736 and native exit duration; retry observes COMPLETED |

Kamaloka 57 was selected because it is the lowest/simple audited controller row:
native level 23, maximum party 6, one XML boss 18554, 30-minute template duration,
and a controller-owned terminal reuse/exit callback. Controller and XML hashes are
pinned by the catalog.

## Delivered architecture

- `high-five-supported-content-v1.xml` enumerates exactly five sorted identities:
  `class.warrior-q401`, `instance.kamaloka-57`, `instance.pailaka-128`,
  `quest.102`, `quest.152`. Script classes, quest/template/NPC/item/step IDs and
  source hashes are pinned; malformed, duplicate, stale and unsupported content
  fails closed.
- `PhantomQuestInstanceService` is caller-driven with at most 128 active owned
  operations. It re-observes native truth on every advance, delegates travel,
  equip and combat to existing owners, settles exact owned Combat receipts, and
  introduces no scheduler, worker, Future, schema or shadow quest/instance state.
- `L2jPhantomQuestInstanceBackend` uses a Materialization action lease, resolves
  the exact loaded native script identity, invokes only catalog-pinned event/talk
  seams, issues native AI movement and observes QuestState/class/inventory/
  Instance/reuse/party/weapon truth. It never teleports or edits DB state.
- `PhantomCombatService.startContentSession` is an additive owner-tagged branch.
  Its basic physical fallback is available only for this content session; all
  native target/instance/peace/lease checks remain, and acquisition/PvP/raid/
  siege entry points are unchanged.
- `PhantomSystem` composes/drains the service and registers its Decision handler.
  Goals contain only a supported content ID; no raw script, HTML or bypass input.
- ACTIVE performs native conversation, class, entry and combat operations.
  BACKGROUND only publishes the existing ACTIVE relevance signal and returns
  retry; the accepted Q102/Q152 acquisition subsystem remains the sole safe
  background collection owner.

## Acceptance evidence

- Final focused `ant -q phantom-quest-instance-goal036-test`: `8/8` PASS, seed
  `36003601`, 4 minutes 16 seconds.
- Q102: native COMPLETED, item 1060 delta `100`; Q152: native COMPLETED, item 23
  delta `1`.
- Q401: native COMPLETED/Medallion, then exact normal-player owner
  `village_master.ElfHumanFighterChange1`, event `1`, NPC 30373; class/base class
  became `1`, mark consumed once and item 8869 delta `15`.
- Kamaloka: template `57`, level `23`, party max `6`, boss `18554`, native reuse
  and exit duration observed; duplicate entry after service restart did not occur.
- Pailaka: Q128/template 43, native conds 1–9, all five bosses and native weapon
  upgrades, Adler completion, exact rewards 13294/13293/736 once, native exit
  duration and restart idempotency.
- Production-composed cleanup: `characters=0`, `quests=0`, `items=0`,
  `instanceReuse=0`, `instances=0`, `leases=0`; service ownership ended with
  `active=0`, `combat=0`, `travel=0`.
- Affected gate was one green 11-target Ant run (8 minutes 31 seconds): acquisition
  checkpoint2, production materialization, Combat server integration, Party server
  integration, Progression server/durability, Background lifecycle, Decision core,
  skeleton/shutdown composition and Goal035. Representative reports: acquisition
  quest catalog `3/3`, active `4/4`, background `3/3`; materialization `21/21`;
  Combat `20/20`; Party `10/10`; Progression `28/28` + durability `15/15`;
  Background `4/4`; Decision `36/36`; shutdown `7/7`; Goal035 `8/8`.
- One fresh `ant verify`: BUILD SUCCESSFUL in 27 minutes 7 seconds; its Goal036
  route was `8/8` with zero cleanup residue.
- One standalone final `ant -q jar` after verify: BUILD SUCCESSFUL in 18 seconds.
  Java 25 printed its known zipfs close-time AccessDenied diagnostic for the
  already copied LoginServer.jar, while Ant returned exit 0.
- Goal036 defines no new static verifier. Two final direct runs of the historical
  descendant-compatible Goal022C2 verifier and their byte identity/hash are
  returned in the immutable handoff.

## Intermediate failures and corrections

- Development used 20 logged focused executions plus one final green execution
  and three narrow diagnostic modes. Early failures exposed, in order: the
  content-only no-skill Combat gap; terminal Combat ownership after native cond
  callbacks; collision with a native Q401 spawn in the fixture; combat-stance
  timing; Pailaka native weapon replacements; catalog ordering; and inconsistent
  test level/EXP. Each received a bounded owner or fixture correction and a focused
  rerun; no native quest/instance/class backdoor was added.
- Preliminary JAR synchronization was required because this checkout's
  `compile-tests` classpath reads `dist/libs/GameServer.jar`; only one standalone
  final JAR was run after the green verify.

## Safety, process and delivery

- All DB-backed evidence used only `127.0.0.1:3308/l2jmobiush5_phantom_test`
  as user `l2j_phantom_test`. Production `l2jmobiush5` was unused and
  `prepare-phantom-test-db` was not executed.
- Shipped Phantom defaults remain disabled/safe. No secret or test password is
  included in production/data/docs.
- Goal usage at report drafting: 1,391,234 tokens and 12,998 seconds. This exceeds
  the ordinary-Goal guide because native chance/combat/stance debugging required
  repeated real-script focused runs, followed by the mandated 27-minute cumulative
  verify; no Goal037 discovery or implementation was consumed.
- Full logs are under `.phantom-local/logs/goal036/` and are not committed.
- Mojibake, escaped-Cyrillic, diff/scope and exact staging results are recorded in
  the final handoff. One task-owned commit uses subject
  `phantom(goal-036): add bounded quest and instance gameplay`; exact SHA and push
  result are returned after commit.
- Goal037 remains `NOT_STARTED`; its complete quest-script/rates audit is unchanged
  and is the next Goal only after this Goal036 SUCCESS delivery.
