# Native summon TEST completion fence correction

This supersedes the exceptional-exit cleanup and API portion of the earlier summon stage. Independent rereview and actual ordinary positive/managed writer-first RED remain REQUIRED. No product edit or native test execution is credited.

Read-first: NATIVE_SUMMON_TEST_INDEPENDENT_REVIEW.md SHA256 DBEF7634A81632661CA7EE9AC1E99FFF8E9DCFED7629575647A37C58289E4806; exact helper168BBD/private Suite701FCC; original task/event/disposal paths and original fixture close; existing Q14 direct-root retained journal/force(true) analogue; PhantomTestRegistry.orderedTests identity sorting. Important1 was confirmed: the unused gate executor cannot drain a Runnable submitted to the original executor.

Frozen inputs/results:

| Artifact | Before SHA256 | Final SHA256 |
|---|---|---|
| test/java/org/l2jmobius/tests/phantoms/PhantomM1NativePhaseChecks.java.pending | 168BBD56F7F94B5A921BE0C0ED3D57380BB1A3A41F55188024E62E3DCE583D10 | 8F052B86D8885630260CF67BCC35401162B3A09AE9A6EC2E1FE52F65E105ADDF |
| private source/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java | 701FCC17F4C00B45ED7659DAB5AA8F39D6B337410B0699605F0A6CF6C492FAD5 | BBCB92AB6661BBDB83366C77398D27EBD90CCF5252DE8A688E6278BF68767A57 |
| exact private helper compiler copy | 168BBD56F7F94B5A921BE0C0ED3D57380BB1A3A41F55188024E62E3DCE583D10 | 8F052B86D8885630260CF67BCC35401162B3A09AE9A6EC2E1FE52F65E105ADDF |

Private root: C:/Users/ZBook/L2J_Mobius/.phantom-author-check-007/native-summon-phase-007. Central SCOPE_EXTENSIONS rows preceded each mutation. Published PhaseChecks70165, root Suite450FF, root Suite.pending and all production source remained unchanged.

New TEST-only API (compiled; not a production cleanup/recovery API):

```java
CompletionFence(PhantomTestContext context, Player owner, String ownedBeforeImage);
void summon(context, owner, service, profileId, summon, target, CompletionFence);
void ordinarySummon(context, owner, summon, target, CompletionFence);
boolean CompletionFence.cleanupSafe();
boolean CompletionFence.retained();
String CompletionFence.diagnostic();
void CompletionFence.requireSafeCleanup();
void CompletionFence.cleanupFinished();
void requireNoRetainedCompletion(PhantomTestContext context);
```

createSummon(Player) is unchanged stock1225/1 effect setup. Existing regen body/helpers are unchanged. Caller creates the local fence after stock bootstrap and before original doAttack; it records scalar exact owner/summon objectIds and native epoch, not permanent Player/Scope/Actor references. At most16 captured body receipts/latches can be registered. Caller diagnostics include its original Canonical character record, base class, inventory digest and managed profileId where present.

Original publication/completion evidence:

- SummonPool.schedule registers NativeBody before original.schedule. Future return records accepted submission, not completion. Exact standard ScheduledThreadPoolExecutor with original AbortPolicy rejection can record rejected-before-start; that receipt never increments nativeFinally. Other thrown/null/unknown submission results require actual body completion or retain the fence.
- The wrapper still delegates original Runnable, delay and backend executor. Actual native body completion is recorded only after task.run exits through the wrapper finally, including native RuntimeException/Error. gate.shutdownNow remains disposal of the unused TEST wrapper executor and supplies no completion evidence.
- Every probe finally releases both pre-entry and post-HP TEST latches, restores the exact original pool slot (refuses a foreign slot), then awaits all captured receipts for a single bounded5s cleanup horizon, even if the normal try path threw or was interrupted. Interrupts cannot skip this wait; their flag is restored after it, including diagnostic failure. Listener removal occurs only after a nonretained completion result.
- Primary Throwable is rethrown unchanged. Exact-pool restoration/drain/listener secondary failures are suppressed rather than replacing it. Captured, accepted, rejected, actual nativeFinally and unresolved counts are separately recorded.

Caller restore boundary:

Both prospective managed/ordinary callers now close manually. Before any unSummon/deleteMe, outbound close or fixture.close they branch on CompletionFence.cleanupSafe, which checks real receipt completion independently of a retained flag. Unresolved or sticky-retained completion bypasses the whole disposal/canonical restore chain. The caller stores a scalar per-Suite retained diagnostic and preserves the original failure; diagnostic failures are suppressed. No native scope is cleared/reopened and no failed store is called successful.

If all original captured work is resolved, original disposal and original strict fixture.close proceed. Managed fixture retains its existing native shutdown/failed-profile gate; ordinary fixture retains its original canonical close. Secondary cleanup errors preserve primary failure and keep the journal. Only after all those operations succeed may cleanupFinished verify the exact journal bytes/nonlinked file and delete that owned marker.

Private journal/admission fence:

CompletionFence creates an exact direct module .phantom-local/m1-007-summon-<ownedObjectId>-<nativeEpoch>.journal through CREATE_NEW/WRITE/FileChannel.force(true), before any captured hit publication. This is a durable pending/admission marker plus bounded owned character/digest diagnostics, not a full database backup or automatic restore authority. Unknown completion or cleanup failure leaves it unchanged. No test has run, so this authoring pass created no runtime journal or DB rows.

The read-only namespace guard rejects linked/unproven directories and any matching retained journal. The private Suite runs it before beforeAll fixture/bootstrap, before every registered case and before afterAll native/canonical cleanup. Its per-Suite retained scalar also blocks subsequent cases. Existing registry types/identities are reused: no Launcher/registry source is edited and no new test framework is added. A fresh process cannot silently treat the retained marker as a new baseline. No automatic recovery, foreign-file deletion, process killing or schema mutation is provided; unresolved ownership requires an explicit exact-owner recovery path.

Because the original registry sorts identities, the focused control IDs are now A08-0-ordinary-original-native-Servitor-damage-control and A08-1-managed-original-native-Servitor-COMBAT. Original ordinary positive setup runs first by actual runtime order, not registration order. Focus/environment sets remain native-summon-phase and old native-phase remains unchanged.

Exceptional-exit audit:

| Exit | Original-body premise | Cleanup permission |
|---|---|---|
| Prepublication/premise failure with no captured body | zero captured original submissions | Original disposal/fixture cleanup allowed; only successful close deletes journal |
| Frontend/pre-entry assertion, interruption or timeout after capture | release both gates; bounded wait for actual original body finally | Allowed only if all captured bodies resolved; primary preserved |
| Actual original native body throws RuntimeException/Error | actual wrapper finally counts body completion | Native owner failure/strict fixture gate remains authoritative; no recovery/reset |
| Known original standard AbortPolicy rejection | rejected-before-start, nativeFinally remains0 | No false native writer/phase proof; original cleanup may proceed if all other captures resolved |
| Ambiguous submission or still-live original body at cleanup horizon | unresolved receipt or sticky retained status | No disposal/outbound/canonical restore; retained journal/diagnostic blocks later admission |
| Secondary disposal/native shutdown/canonical close/journal-delete failure | original-body completion is known but cleanup failed | Preserve primary + suppressed failure; leave journal and block new fixture |

Positive writer assertions are unchanged: actual original HitTask stack and positive target HP loss, exact fresh Servitor/owner/native epoch, frontend NONE, queued/running obligations, COMBAT/damageSequence at true writer, original completion and terminal passive evidence. No HP/stat/template/rate/phase writer is injected. Three real misses remain INVALID rather than product RED. Live engagement/reassignment/old epoch controls and full W remain separate REQUIRED gates.

Verification: final coherent JDK25 javac --release25/UTF-8/-proc:none of the exact two private sources exited0 against immutable task007-frozen-phase09-classpath/{production,tests} and original dist/libs. No Ant, DB/native fixture, JVM server or Git command was used. Both source hashes and private helper-copy equality were verified; published before hashes matched.

- Mojibake markers in changed helper/private sources/report/central ledger: separate scan, no matches.
- Escaped Cyrillic/XML escaped Cyrillic in those files: separate scan, no matches.

Native ordinary/managed proof and nonauthor every-exit rereview: REQUIRED, not claimed passed.
