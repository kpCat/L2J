# RESULT_HANDOFF035

Status: `PARTIAL_VERIFIED` pending publication only. G0-G7 PASS; G8 REQUIRED normal push and local=remote receipt. Inventory technical result is GREEN. M1=`OPEN`; REAL_FINAL=`NOT_RUN`. No following task started.

Base: `d6d8c4b9d32276aff5563c239c5f822cf8ec83fa`. Frozen production commit: `e156e566062a3de5c26c26b088c021c8948f0469`. Focused test refinement commit: `e1bfe8c004617899cc26d60b0b35bc57c54c747b`. Later changes are TASK035 tools/reports only. No production difference after the frozen commit.

J0 original GameServer SHA256: `EBC6918A38D634964028A171AFF62CB24926B5E7432040EB50B3A5E5CD620D46`.

J1 developmental GameServer SHA256: `2BCBDCFD0D40E5659F7CA07D11C3CBD0DC6D48CAD0C9056C8008946BFAA705D9`.

J2 accepted committed GameServer SHA256: `D149B4411DAC664F380BEC0616B781D063628458A396D822C0BCCACC35F9A8F8`. LoginServer SHA256: `8EC24397E98A6B7EA9E17DFD12691554E0D10B9EC0D170D95B9906E471BE2254`. Runtime035b and final regression classpath used this exact Jar, not build/bin production classes. No earlier Jar PASS carried forward.

The native authority used total item count, while bounded compact state rejected usedSlots252 above max250. Existing immutable witness/SQL projection already normalizes the durable scalar to249 INVENTORY rows. The fix allows bounded representation of inherited native occupancy through275, captures stock nonquest count, and makes background pickup count durable INVENTORY plus PAPERDOLL. Representation grants no acquisition permission. Only three conditional production files changed; service, transaction, codec, schema, lifecycle and stock configuration remain unchanged. See CONSISTENCY035.md for the boundary meanings.

Full multiset hashes, including every object ID, item ID, count and location:

- H36 before = after FINALIZED = SQL after = sameDB native reload: `e64e08cbfe2e93d5496212ae08d2efb8694c0ad4ed1fcda14c0b0e0959c9d18c`.
- H45 before = after FINALIZED = SQL after = sameDB native reload: `c0205ddbdeec5acb027ff34f2d9a47de94bc4eb046ea07111d9d68c13f7c16b4`.

Counts below are `(INVENTORY,PAPERDOLL,QUEST,nonQuest,all)`. J0/J1/J2 and H36/H45 refer to the exact SHA256 values above, not truncated hashes. Distinct epochs are distinct rows.

| Lane/profile | objectId | epoch | Initial counts / cap | First incident | After store count/hash; SQL parity | FINALIZED key / PREPARED row revision | World/AutoSave/lease | Global drain / Jar |
|---|---:|---:|---|---|---|---|---|---|
| protected034/36 | 268484533 | 413992015958800 | 249,3,0,252,252 / 250 | Invalid bounded background inventory, NATIVE_STORE | failure retained; no successful store asserted | absent | retained; World/AutoSave present | FAILED retained2 / J0 |
| protected034/45 | 268484616 | 413993041020700 | 249,3,0,252,252 / 250 | Invalid bounded background inventory, NATIVE_STORE | failure retained; no successful store asserted | absent | retained; World/AutoSave present | FAILED retained2 / J0 |
| own035a/36 | 268484533 | 444705076249000 | 249,3,0,252,252 / 250 | none | SQL252/H36; exact rows | 36-444705076249000-15922 / 15922 | DETACHED, work0/timers0; triad not individually captured by R1 observer | DONE/COMPLETE retained0 / J1 |
| own035a/45 | 268484616 | 444707095913800 | 249,3,0,252,252 / 250 | none | SQL252/H45; exact rows | 45-444707095913800-13391 / 13391 | DETACHED, work0/timers0; triad not individually captured by R1 observer | DONE/COMPLETE retained0 / J1 |
| own035b/36 | 268484533 | 445022441285000 | 249,3,0,252,252 / 250 | none | native252/H36; SQL252/H36; exact rows | 36-445022441285000-15922 / 15922 | all absent AFTER FINALIZED; DETACHED, work0/timers0 | DONE/COMPLETE retained0 / J2 |
| own035b/45 | 268484616 | 445024561494700 | 249,3,0,252,252 / 250 | none | native252/H45; SQL252/H45; exact rows | 45-445024561494700-13391 / 13391 | all absent AFTER FINALIZED; DETACHED, work0/timers0 | DONE/COMPLETE retained0 / J2 |

J2 receipts:

- 36: `cd0c7246d22396be476a29a2cf44edb73653d575f8c1a27a9f6379467f23c3d4`.
- 45: `121d0425d1c4d2adc35ef5bcbbd22e5c540b51b6ad1b28848f0145444579baa9`.

Full items were captured at REGISTER, PREPARED and FINALIZED for both accepted epochs, and compared against SQL before/after including loc_data/enchant. Skills remained exact. Every character field remained exact except the explicit stock online2->0 logout transition. FINALIZED SQL EXP/SP/vitality/position matched native values; subsequent sameDB reload preserved EXP26844, SP2414, vitality1 and full252 objects. Native context was COMPLETED, with post-run state revisions15955/13424. Earned-state receipts and goal/payload bindings were verified, not synthesized.

Terminal FULL native item count0 is preserved in raw evidence: ordinary disposal cleared the Player container AFTER FINALIZED. It is not hidden or treated as item conservation proof. Conservation is established by earlier full252 native witnesses, exact durable252 rows and independent sameDB Player reload252.

R2 typed stop reached DONE/COMPLETE with retained0 while all three thread pools remained live. At that instant observer activeReferences was1; final observer status reached0, pending0/overflow0/buffer0, captured=exported1210. All292 native births had terminal witnesses. All1212 ledger frames have contiguous sequence/run identity and verified raw SHA; RUN_END reports exporter_drained=true, dropped0 and empty failure. Observer SQL exports ran asynchronously, not in native callbacks. Synthetic was never started.

| Gate | Result / current proof |
|---|---|
| G0 | PASS: initial own HEAD and remote exactly base; registration and isolation checked; 12 manifest entries SHA verified before edits; protected034 archive all10001 files and protected032 runtime304 files verified again |
| G1 | PASS: OLD_RED035 actual36/45 both failed on J0 at the same bounded native capture, with all252 original objects; original034 native store failures separately preserved |
| G2 | PASS: full canonical items and authoritative nonquest252; phantom max250; equipment never treated as free slots |
| G3 | PASS: both J2 original owners/epochs native FINALIZED, sealed/exact argument and native/SQL payload bindings; no item or EXP/SP delta |
| G4 | PASS: focused overfull/new-slot/stack/quest/equipment negatives, duplicate/>275/4097/8193 codec rejection; original SQL/hash/version/owner/lost-reply guards unchanged and passing |
| G5 | PASS: exact terminal triad absent AFTER FINALIZED; owner DETACHED/work0/timers0; typed healthy drain and final observer drain0 |
| G6 | PASS: v2/schema unchanged; 10000 original032a payloads decoded and10000 byte-identical re-encodes, including314 compact; compact275 bounds; existing native/historical031 capacity routes and sameDB reload2/2 |
| G7 | PASS: complete final93/93 and lifecycle11/11 on J2; focused suites below; no acceptance edits |
| G8 | REQUIRED: selected external archive SHA verified and own stock JVM STOPPED; final normal publication and local=remote receipt still required |

| Current J2 regression | Result | Immutable raw label |
|---|---:|---|
| 18-route full matrix | 93/93 | FINAL_R2_CLEAN035 |
| lifecycle027 acceptance | 11/11 | FINAL_R2_LIFECYCLE035 |
| actual inherited capture + capacity/quest/codec | 6/6 | FINAL_R2_CAPTURE035 |
| native/historical SQL projection + negatives/lost replies | 4/4 | FINAL_R2_PROJECTION035 |
| unchanged031 compact native/historical capacity | 2/2 | FINAL_R2_CAPACITY031_035 |
| unchanged background model | 9/9 | FINAL_R2_MODEL_SEEDED035 |
| unchanged SQL atomic/version/hash/restart contracts | 11/11 | FINAL_R2_SQL035 |
| full inventory hash/50-transition conservation | 2/2 | FINAL_R2_COMPACT035 |
| original codec corpus / sameDB reload | 10000/10000; 2/2 | R2_LEGACY_CODEC035; R2_SAMEDB_RELOAD035 |

All earlier negative outcomes remain in the archive. Initial matrix was92/93: unchanged cooperative reward case failed when the second participant died before death reward. Its isolated repeat passed1/1 and a complete sequential repeat passed93/93 on unchanged J2. No thresholds or gameplay/test source were altered.

The mistakenly selected extra `FINAL_R2_NATIVE11_035` is actually the general24-case suite:19 passed, four queued tests rejected the own035t DB because they require shared phantom_test, and Q15 logout reward expected1 but received20. This is neither lifecycle acceptance nor accepted GREEN evidence; no shared/old DB write or guard bypass was attempted. That Q15 failure remains unresolved outside TASK035. `FINAL_R2_MODEL035` was an INVALID seed33003300 setup; the original required seed15001501 run passed9/9. These failures are disclosed, not relabeled PASS.

The old private actor1149/157-object031 fixture and hardcoded031 preboot file were not available; that exact legacy suite is NOT_RUN. Its unchanged contract is covered here by current original031 native/historical routes, C06 compact275 negatives and10000 actual original032a byte-parity payloads. No historical fixture was fabricated. General quest-capacity policy and AutoLootSlotLimit=False are not introduced: durable legacy INVENTORY includes quest rows and retains conservative background accounting. Actual035 targets have zero quests; C05 verifies native quest exclusion and full-hash retention on a separate ordinary owned TEST fixture.

Exactly2 production Jar builds and2 own GameServer starts used. No380s scene, crash72/73, personal1000 changes or profile27 cast-launch fix. Player.java, Player.ini, PersonalCharacterQoL.ini and client remain untouched. Main, PLAY, foreign runtimes, old databases and034 evidence remain read-only. Disk audit saved separately and paused; no directory/database deletion. See CLEANUP035.md and EVIDENCE_INDEX035.md.
