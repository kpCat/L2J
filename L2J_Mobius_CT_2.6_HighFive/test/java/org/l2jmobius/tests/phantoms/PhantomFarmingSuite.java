/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictAlternative;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictSnapshot;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.ResultStatus;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Gate;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Outcome;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementStatus;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceKey;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.SemanticAct;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPersistencePort;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPersistencePort.StoredState;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPolicy;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.FaultPoint;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.SocialEvidence;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingStateCodec;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomFarmingSuite implements PhantomTestSuite
{
	public enum Mode
	{
		RESOURCE_POLICY,
		PERCEPTION_CLAIMS,
		PARTY_SHARE,
		BILATERAL,
		CONVERGENCE,
		FACTS,
		RESTART_FAULT,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 24002401L;
	private final Mode _mode;

	public PhantomFarmingSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "farming-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 024 mode used the wrong deterministic seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case RESOURCE_POLICY -> resourcePolicy(registry);
			case PERCEPTION_CLAIMS -> perceptionClaims(registry);
			case PARTY_SHARE -> partyShare(registry);
			case BILATERAL -> bilateral(registry);
			case CONVERGENCE -> convergence(registry);
			case FACTS -> facts(registry);
			case RESTART_FAULT -> restartFault(registry);
			case LIFECYCLE_PERFORMANCE -> lifecyclePerformance(registry);
		}
	}

	private void resourcePolicy(PhantomTestRegistry registry)
	{
		registry.add("01-strict-policy-and-exact-resource-identities", context ->
		{
			final PhantomFarmingPolicy policy = policy(context);
			PhantomAssertions.assertEquals(3, policy.limits().maximumRounds(), "Farming round bound changed.");
			PhantomAssertions.assertEquals(ResourceKey.room("dungeon.left"), ResourceKey.room("dungeon.left"), "ROOM identity is not node-only.");
			PhantomAssertions.assertEquals(ResourceKey.mobGroup("dungeon.right", "door.right", 100), ResourceKey.mobGroup("dungeon.right", "door.right", 100), "Outdoor identity changed.");
			PhantomAssertions.assertFalse(ResourceKey.mobGroup("dungeon.right", "door.right", 100).equals(ResourceKey.mobGroup("dungeon.right", "camp.other", 100)), "Different outdoor anchors collided.");
			PhantomAssertions.assertEquals(EnumSet.allOf(SemanticAct.class), EnumSet.of(SemanticAct.SHARE, SemanticAct.WAIT, SemanticAct.MOVE, SemanticAct.REFUSE, SemanticAct.ESCALATE), "Typed farming act vocabulary changed.");

			final Path malformed = context.reportsDirectory().resolve("farming-policy-invalid.xml");
			Files.createDirectories(malformed.getParent());
			Files.writeString(malformed, """
				<farmingConflictPolicy id="bad" version="1"><limits/></farmingConflictPolicy>
				""", StandardCharsets.UTF_8);
			try
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomFarmingPolicy.load(malformed), "Incomplete farming policy was accepted.");
			}
			finally
			{
				Files.deleteIfExists(malformed);
			}
		});

		registry.add("02-room-outdoor-and-planning-only-derive-dynamically", context ->
		{
			try (Fixture room = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				room.put(snapshot(room, 1, source(room, 1, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57), 57, 10, 2, 500, false));
				room.put(snapshot(room, 2, source(room, 2, Method.QUEST_COLLECTION, "dungeon.left", "door.left", 200, 9000), 9000, 20, 3, 400, false));
				room.service.advance(1);
				room.service.advance(2);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(room, 1).outcome(), "Different ROOM NPC/item/method evidence did not conflict by exact room node.");
			}
			try (Fixture outdoor = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				outdoor.put(snapshot(outdoor, 1, source(outdoor, 1, Method.DEATH_DROP, "dungeon.right", "door.right", 100, 57), 57, 10, 1, 500, false));
				outdoor.put(snapshot(outdoor, 2, source(outdoor, 2, Method.QUEST_COLLECTION, "dungeon.right", "door.right", 100, 9000), 9000, 10, 1, 500, false));
				outdoor.service.advance(1);
				outdoor.service.advance(2);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(outdoor, 1).outcome(), "Outdoor node+anchor+npc identity did not conflict across item/method evidence.");
			}
			try (Fixture recipe = fixture(context, 1, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				recipe.put(snapshot(recipe, 1, source(recipe, 1, Method.RECIPE_PREPARATION, "dungeon.left", "door.left", 0, 57), 57, 1, 0, 500, false));
				PhantomAssertions.assertEquals(PhantomFarmingService.AdvanceStatus.STALE, recipe.service.advance(1).status(), "Planning-only recipe created a farming claim.");
				PhantomAssertions.assertEquals(0, recipe.service.snapshot().activeClaims(), "Recipe source entered the runtime claim index.");
			}
		});
	}

	private void perceptionClaims(PhantomTestRegistry registry)
	{
		registry.add("01-real-goal010-query-is-stable-bounded-and-perceptible", context ->
		{
			try (Fixture fixture = fixture(context, 3, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				fixture.topology.updateProfile(1, PhantomTopologyCoreSuite.LEFT_POINT, 2);
				fixture.topology.updateProfile(2, PhantomTopologyCoreSuite.RIGHT_POINT, 2);
				fixture.topology.updateProfile(3, PhantomTopologyCoreSuite.RIGHT_POINT, 2);
				final List<Long> first = fixture.topology.perceptibleProfiles(1, PhantomPerceptionChannel.LOCAL_CHAT, 2).stream().map(value -> value.profileId()).toList();
				final List<Long> second = fixture.topology.perceptibleProfiles(1, PhantomPerceptionChannel.LOCAL_CHAT, 2).stream().map(value -> value.profileId()).toList();
				PhantomAssertions.assertEquals(List.of(2L, 3L), first, "One-hop LOCAL_CHAT result was not stable and profile-sorted.");
				PhantomAssertions.assertEquals(first, second, "Bounded perceptibility query changed without generation change.");
				PhantomAssertions.assertTrue(first.size() <= 2, "Goal010 perceptibility result exceeded the requested bound.");
				fixture.backend._doorStates.put(500, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState.CLOSED);
				PhantomAssertions.assertEquals(List.of(), fixture.topology.perceptibleProfiles(1, PhantomPerceptionChannel.LOCAL_CHAT, 2), "Closed topology edge remained perceptible.");
			}
		});

		registry.add("02-claim-refresh-revision-expiry-release-and-restart", context ->
		{
			final MemoryStore store = new MemoryStore();
			try (Fixture fixture = fixture(context, 1, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE, store))
			{
				final Source first = source(fixture, 1, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57);
				fixture.put(snapshot(fixture, 1, first, 57, 10, 4, 500, false));
				PhantomAssertions.assertEquals(PhantomFarmingService.AdvanceStatus.PROGRESSED, fixture.service.advance(1).status(), "First exact claim was not persisted.");
				PhantomAssertions.assertEquals(Outcome.ALLOW, gate(fixture, 1).outcome(), "First exact claimant was not allowed.");
				final long unchangedRow = fixture.store.load(1).orElseThrow().rowVersion();
				fixture.service.advance(1);
				PhantomAssertions.assertEquals(unchangedRow, fixture.store.load(1).orElseThrow().rowVersion(), "Exact claim refresh performed a second durable write.");
				fixture.put(snapshot(fixture, 1, first, 57, 10, 5, 500, false, 2, 1));
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(fixture, 1).outcome(), "Changed Goal revision retained the old runtime claim.");
				fixture.service.advance(1);
				fixture.minute.addAndGet(4);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(fixture, 1).outcome(), "Expired claim lease still authorized resource work.");
				fixture.service.advance(1);
				fixture.facts.remove(1L);
				fixture.service.advance(1);
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeClaims(), "Completed/missing acquisition did not release its runtime claim.");
			}
			try (Fixture restarted = fixture(context, 1, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE, store))
			{
				final Source source = source(restarted, 1, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57);
				restarted.put(snapshot(restarted, 1, source, 57, 10, 5, 500, false, 2, 2));
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(restarted, 1).outcome(), "Restart trusted a persisted claim before live revalidation.");
				restarted.service.advance(1);
				PhantomAssertions.assertEquals(Outcome.ALLOW, gate(restarted, 1).outcome(), "Restart did not revalidate and reactivate the exact current claim.");
			}
		});
	}

	private void partyShare(PhantomTestRegistry registry)
	{
		registry.add("01-same-exact-party-shares-without-rounds", context ->
		{
			try (Fixture fixture = fixture(context, 2, (a, b) -> true, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(fixture, false, false, 500, 500);
				fixture.service.advance(1);
				final String agreementId = fixture.service.advance(2).agreementId();
				PhantomAssertions.assertFalse(agreementId.isEmpty(), "Same-Party resolution did not persist an agreement.");
				PhantomAssertions.assertEquals(Outcome.SHARE, gate(fixture, 1).outcome(), "Same-Party lower profile did not SHARE.");
				PhantomAssertions.assertEquals(Outcome.SHARE, gate(fixture, 2).outcome(), "Same-Party higher profile did not SHARE.");
				PhantomAssertions.assertEquals(0L, fixture.service.snapshot().negotiationsStarted(), "Same-Party SHARE opened negotiation rounds.");
				PhantomAssertions.assertEquals("farming.conflict.same_party", fixture.store.state(1).latest().reasonKey(), "Same-Party reason lost canonical ownership.");
			}
		});

		registry.add("02-stale-party-evidence-is-fail-neutral", context ->
		{
			try (Fixture fixture = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(fixture, false, false, 500, 500);
				fixture.service.advance(1);
				fixture.service.advance(2);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(fixture, 1).outcome(), "Missing/stale Party evidence auto-shared a resource.");
				PhantomAssertions.assertEquals(1L, fixture.service.snapshot().negotiationsStarted(), "Non-Party conflict skipped bilateral negotiation.");
			}
		});
	}

	private void bilateral(PhantomTestRegistry registry)
	{
		registry.add("01-friendly-share-needs-two-identical-finals", context ->
		{
			final MemorySocial social = social(0, 0, 1200);
			try (Fixture fixture = fixture(context, 2, (a, b) -> false, social, PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(fixture, false, false, 500, 500);
				fixture.service.advance(1);
				final String offered = fixture.service.advance(2).agreementId();
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(fixture, 1).outcome(), "One-sided OFFER authorized SHARE.");
				fixture.service.advance(1);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(fixture, 2).outcome(), "OFFER+RESPONSE authorized SHARE without FINAL receipts.");
				fixture.service.advance(2);
				final AgreementReceipt lower = fixture.store.state(1).latest();
				final AgreementReceipt higher = fixture.store.state(2).latest();
				PhantomAssertions.assertEquals(offered, lower.agreementId(), "Bilateral protocol invented a second agreement ID.");
				PhantomAssertions.assertTrue(lower.exactPair(higher), "Bilateral final receipts differ.");
				PhantomAssertions.assertEquals(8L, lower.lowerRemaining(), "Lower final did not persist real remaining evidence.");
				PhantomAssertions.assertEquals(8L, lower.higherRemaining(), "Higher final did not persist real remaining evidence.");
				PhantomAssertions.assertEquals(Outcome.SHARE, gate(fixture, 1).outcome(), "Two-sided cooperative FINAL did not authorize SHARE.");
				fixture.service.advance(1);
				PhantomAssertions.assertEquals(1, social.durableCount("farming.agreement.offered", offered), "Replay duplicated the offered social event.");
				PhantomAssertions.assertEquals(1, social.durableCount("farming.agreement.accepted", offered), "Replay duplicated the accepted social event.");
				final byte[] encoded = new PhantomFarmingStateCodec().encode(fixture.store.state(1));
				PhantomAssertions.assertTrue(encoded.length <= 4096, "Farming state exceeded profile component payload.");
				PhantomAssertions.assertEquals(fixture.store.state(1), new PhantomFarmingStateCodec().decode(encoded), "Farming state codec lost bilateral evidence.");
			}
		});
	}

	private void convergence(PhantomTestRegistry registry)
	{
		registry.add("01-move-and-wait-preserve-one-holder", context ->
		{
			try (Fixture moving = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(moving, false, true, 900, 100);
				driveFinal(moving);
				PhantomAssertions.assertEquals(Outcome.ALLOW, gate(moving, 1).outcome(), "Stable higher-priority holder was not allowed.");
				PhantomAssertions.assertEquals(Outcome.MOVE, gate(moving, 2).outcome(), "Loser with a current ranked alternative did not MOVE.");
				PhantomAssertions.assertEquals(AgreementStatus.MOVING, moving.store.state(1).latest().status(), "MOVE terminal status changed.");
			}
			try (Fixture waiting = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(waiting, false, false, 900, 100);
				driveFinal(waiting);
				PhantomAssertions.assertEquals(Outcome.ALLOW, gate(waiting, 1).outcome(), "WAIT conflict lost its unique holder.");
				PhantomAssertions.assertEquals(Outcome.WAIT, gate(waiting, 2).outcome(), "Loser without alternative did not WAIT.");
				PhantomAssertions.assertFalse(waiting.service.hasWork(2), "WAIT created a tight farming retry loop.");
				waiting.minute.addAndGet(6);
				PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(waiting, 2).outcome(), "WAIT expiry did not return to bounded re-evaluation.");
			}
		});

		registry.add("02-refuse-and-escalate-remain-semantic-only", context ->
		{
			try (Fixture refusing = fixture(context, 2, (a, b) -> false, social(0, 0, -1200), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(refusing, false, true, 900, 100);
				driveFinal(refusing);
				final AgreementReceipt receipt = refusing.store.state(1).latest();
				PhantomAssertions.assertEquals(AgreementStatus.REFUSED, receipt.status(), "Low cooperation did not persist REFUSE.");
				PhantomAssertions.assertEquals(List.of(SemanticAct.REFUSE, SemanticAct.MOVE), receipt.acts(), "REFUSE did not converge through the typed loser outcome.");
			}
			try (Fixture escalating = fixture(context, 2, (a, b) -> false, social(0, 1200, 0), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(escalating, false, false, 900, 100);
				driveFinal(escalating);
				final AgreementReceipt receipt = escalating.store.state(1).latest();
				PhantomAssertions.assertEquals(AgreementStatus.ESCALATED, receipt.status(), "High conflict evidence did not persist ESCALATE.");
				PhantomAssertions.assertEquals(List.of(SemanticAct.ESCALATE, SemanticAct.WAIT), receipt.acts(), "ESCALATE did not remain semantic plus WAIT.");
				PhantomAssertions.assertEquals(0L, escalating.service.snapshot().moveActs(), "ESCALATE without alternative invented combat/navigation work.");
			}
		});

		registry.add("03-three-claimants-converge-in-stable-pairs", context ->
		{
			try (Fixture fixture = fixture(context, 3, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				for (int profile = 1; profile <= 3; profile++)
				{
					fixture.put(snapshot(fixture, profile, source(fixture, profile, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57), 57, 10, 2, 1000 - (profile * 100), profile > 1));
					fixture.service.advance(profile);
				}
				for (int step = 0; step < 16; step++)
				{
					fixture.service.advance((step % 3) + 1);
				}
				final long holders = java.util.stream.LongStream.rangeClosed(1, 3).filter(profile -> gate(fixture, profile).outcome() == Outcome.ALLOW).count();
				PhantomAssertions.assertTrue(holders <= 1, "Three claimants produced two exclusive ALLOW holders.");
				PhantomAssertions.assertTrue(fixture.service.snapshot().maximumBucketSize() <= 3, "Three-claimant bucket exceeded its exact bound.");
				PhantomAssertions.assertTrue(fixture.service.snapshot().maximumActiveNegotiations() <= 2, "A profile accumulated unbounded simultaneous negotiations.");
			}
		});
	}

	private void facts(PhantomTestRegistry registry)
	{
		registry.add("01-real-goal020-adapter-maps-current-typed-facts", context ->
		{
			try (Fixture fixture = fixture(context, 2, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(fixture, false, true, 900, 100);
				driveFinal(fixture);
				final var result = L2jPhantomConversationExecutionPort.farmingConflict(2, fixture.service);
				PhantomAssertions.assertEquals(ResultStatus.COMPLETED, result.status(), "Goal020 adapter rejected current farming facts.");
				final Set<String> keys = result.facts().stream().map(value -> value.key()).collect(java.util.stream.Collectors.toSet());
				PhantomAssertions.assertTrue(keys.containsAll(Set.of("farming.claim_status", "farming.resource", "farming.counterpart", "farming.remaining", "farming.counterpart_remaining", "farming.negotiation_act", "farming.agreement")), "Goal020 adapter omitted exact farming facts.");
				PhantomAssertions.assertEquals(ResultStatus.NOT_FOUND, L2jPhantomConversationExecutionPort.farmingConflict(9999, fixture.service).status(), "Ordinary/non-Phantom profile received fabricated farming facts.");
				PhantomAssertions.assertTrue(fixture.store.load(9999).isEmpty(), "Human/unknown query fabricated a Phantom component.");
				fixture.minute.addAndGet(11);
				PhantomAssertions.assertEquals(ResultStatus.NOT_FOUND, L2jPhantomConversationExecutionPort.farmingConflict(2, fixture.service).status(), "Stale farming receipt remained queryable.");
			}
		});
	}

	private void restartFault(PhantomTestRegistry registry)
	{
		registry.add("01-bilateral-fault-matrix-reconciles-one-stable-id", context ->
		{
			for (FaultPoint point : FaultPoint.values())
			{
				final MemoryStore store = new MemoryStore();
				final MemorySocial social = neutral();
				final FaultOnce fault = new FaultOnce(point);
				Fixture first = fixture(context, 2, (a, b) -> false, social, fault, store);
				putPair(first, false, false, 900, 100);
				first.service.advance(1);
				for (long profileId : List.of(2L, 1L, 2L))
				{
					first.service.advance(profileId);
					if (fault.triggered.get())
					{
						break;
					}
				}
				PhantomAssertions.assertTrue(fault.triggered.get(), "Fault point was not reached: " + point);
				final String originalId = persistedAgreementId(store);
				if (point != FaultPoint.BEFORE_SOCIAL)
				{
					PhantomAssertions.assertEquals(Outcome.NEGOTIATE, gate(first, 1).outcome(), "One-sided/intermediate persistence authorized an effect at " + point);
				}
				first.close();

				try (Fixture restarted = fixture(context, 2, (a, b) -> false, social, PhantomFarmingService.FaultInjector.NONE, store))
				{
					putPair(restarted, false, false, 900, 100);
					for (int step = 0; step < 12; step++)
					{
						restarted.service.advance((step % 2) + 1);
					}
					final AgreementReceipt lower = store.state(1).latest();
					final AgreementReceipt higher = store.state(2).latest();
					PhantomAssertions.assertTrue(lower.exactPair(higher), "Restart did not reconcile exact bilateral finals at " + point);
					PhantomAssertions.assertEquals(originalId, lower.agreementId(), "Restart invented a second agreement ID at " + point);
					PhantomAssertions.assertTrue(gate(restarted, 1).outcome() != Outcome.NEGOTIATE, "Reconciled agreement remained blocked at " + point);
					PhantomAssertions.assertTrue(restarted.service.snapshot().maximumActiveNegotiations() <= 1, "Fault replay duplicated active negotiations.");
				}
			}
		});

		registry.add("02-final-outcome-history-is-exact-and-idempotent", context ->
		{
			final MemorySocial social = neutral();
			try (Fixture fixture = fixture(context, 2, (a, b) -> false, social, PhantomFarmingService.FaultInjector.NONE))
			{
				putPair(fixture, false, true, 900, 100);
				driveFinal(fixture);
				final String agreementId = fixture.store.state(1).latest().agreementId();
				PhantomAssertions.assertEquals(PhantomFarmingService.AdvanceStatus.PROGRESSED, fixture.service.observeAgreementOutcome(1, agreementId, false).status(), "Exact fulfilled outcome was not persisted.");
				PhantomAssertions.assertEquals(AgreementStatus.FULFILLED, fixture.store.state(1).latest().status(), "Fulfilled status did not reach both histories.");
				PhantomAssertions.assertEquals(PhantomFarmingService.AdvanceStatus.IDEMPOTENT, fixture.service.observeAgreementOutcome(1, agreementId, false).status(), "Fulfilled replay was not idempotent.");
				PhantomAssertions.assertEquals(2, social.durableCount("agreement.fulfilled", agreementId), "Fulfilled social history was not exactly once per bilateral owner.");
			}
		});
	}

	private void lifecyclePerformance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-bounded-gates-and-fixed-cardinality-metrics", context ->
		{
			try (Fixture fixture = fixture(context, 1, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE))
			{
				fixture.put(snapshot(fixture, 1, source(fixture, 1, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57), 57, 10, 2, 500, false));
				fixture.service.advance(1);
				final long started = System.nanoTime();
				for (int operation = 0; operation < 100_000; operation++)
				{
					PhantomAssertions.assertEquals(Outcome.ALLOW, gate(fixture, 1).outcome(), "Uncontested bounded gate changed during volume run.");
				}
				final long elapsed = System.nanoTime() - started;
				context.record("farming.gateOperations", 100_000);
				context.record("farming.gateElapsedNanos", elapsed);
				PhantomAssertions.assertEquals(1, fixture.service.snapshot().maximumBucketSize(), "Uncontested volume expanded the resource bucket.");
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().operationClaims(), "Volume run leaked operation claims.");
			}
		});

		registry.add("02-no-worker-global-scan-or-runtime-lease-after-stop", context ->
		{
			for (java.lang.reflect.Field field : PhantomFarmingService.class.getDeclaredFields())
			{
				final String type = field.getType().getName();
				PhantomAssertions.assertFalse(type.contains("Thread") || type.contains("Executor") || type.contains("Future") || type.contains("Timer") || type.contains("Scheduled"), "Farming service owns a worker primitive: " + type);
			}
			final Fixture fixture = fixture(context, 1, (a, b) -> false, neutral(), PhantomFarmingService.FaultInjector.NONE);
			fixture.put(snapshot(fixture, 1, source(fixture, 1, Method.DEATH_DROP, "dungeon.left", "door.left", 100, 57), 57, 10, 2, 500, false));
			fixture.service.advance(1);
			PhantomAssertions.assertTrue(fixture.service.beginStop(), "Farming beginStop failed.");
			PhantomAssertions.assertTrue(fixture.service.finishStop(), "Farming finishStop retained operations.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeClaims(), "Shutdown retained runtime farming leases.");
			PhantomAssertions.assertEquals(PhantomFarmingService.State.STOPPED, fixture.service.snapshot().state(), "Farming service did not stop.");
			fixture.closeTopology();
		});
	}

	private static PhantomFarmingPolicy policy(PhantomTestContext context)
	{
		return PhantomFarmingPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml"));
	}

	private static Fixture fixture(PhantomTestContext context, int profiles, PhantomFarmingService.PartyFacts party, MemorySocial social, PhantomFarmingService.FaultInjector faults)
	{
		return fixture(context, profiles, party, social, faults, new MemoryStore());
	}

	private static Fixture fixture(PhantomTestContext context, int profiles, PhantomFarmingService.PartyFacts party, MemorySocial social, PhantomFarmingService.FaultInjector faults, MemoryStore store)
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final PhantomTopologySnapshot snapshot = PhantomTopologyCoreSuite.snapshot(backend);
		final PhantomTopologyService topology = PhantomTopologyService.fromSnapshotForTesting(snapshot, backend, PhantomTopologyCoreSuite.POLICY, NO_SIGNALS);
		PhantomAssertions.assertTrue(topology.start(), "Goal024 topology fixture did not start.");
		for (int profile = 1; profile <= profiles; profile++)
		{
			topology.registerProfile(profile);
			topology.updateProfile(profile, PhantomTopologyCoreSuite.LEFT_POINT, 1);
		}
		final Map<Long, ConflictSnapshot> facts = new ConcurrentHashMap<>();
		final AtomicLong minute = new AtomicLong(100);
		final PhantomFarmingService service = new PhantomFarmingService(policy(context), store, profileId -> Optional.ofNullable(facts.get(profileId)), topology, party, social, Math.max(1, profiles), minute::get, faults);
		PhantomAssertions.assertTrue(service.start(), "Goal024 farming fixture did not start.");
		return new Fixture(backend, topology, service, store, facts, minute);
	}

	private static void putPair(Fixture fixture, boolean room, boolean secondAlternative, int firstPriority, int secondPriority)
	{
		final String node = room ? "dungeon.left" : "dungeon.right";
		final String anchor = room ? "door.left" : "door.right";
		fixture.put(snapshot(fixture, 1, source(fixture, 1, Method.DEATH_DROP, node, anchor, 100, 57), 57, 10, 2, firstPriority, false));
		fixture.put(snapshot(fixture, 2, source(fixture, 2, Method.DEATH_DROP, node, anchor, 100, 57), 57, 10, 2, secondPriority, secondAlternative));
	}

	private static void driveFinal(Fixture fixture)
	{
		fixture.service.advance(1);
		fixture.service.advance(2);
		for (int step = 0; step < 8; step++)
		{
			fixture.service.advance((step % 2) + 1);
			final FarmingState lower = fixture.store.state(1);
			final FarmingState higher = fixture.store.state(2);
			if ((lower.latest() != null) && lower.latest().exactPair(higher.latest()))
			{
				return;
			}
		}
		throw new AssertionError("Bilateral farming agreement did not converge.");
	}

	private static Gate gate(Fixture fixture, long profileId)
	{
		return fixture.service.evaluate(profileId, fixture.facts.get(profileId));
	}

	private static ConflictSnapshot snapshot(Fixture fixture, long profileId, Source source, int itemId, long required, long progress, int priority, boolean alternative)
	{
		return snapshot(fixture, profileId, source, itemId, required, progress, priority, alternative, 1, 1);
	}

	private static ConflictSnapshot snapshot(Fixture fixture, long profileId, Source source, int itemId, long required, long progress, int priority, boolean alternative, long revision, long rowVersion)
	{
		final List<ConflictAlternative> alternatives = alternative ? List.of(new ConflictAlternative(hash("alternative", profileId, revision), Method.DEATH_DROP, 100)) : List.of();
		final String topologyHash = fixture.topology.query().snapshot().canonicalHash();
		final Hashes hashes = new Hashes(hash("catalog"), hash("knowledge"), topologyHash, hash("progression"), hash("background"));
		final String evidence = PhantomFarmingModel.sha256(profileId, 1000 + profileId, revision, source.sourceId(), required, progress, rowVersion, topologyHash);
		return new ConflictSnapshot(profileId, 1000 + profileId, revision, itemId, required, progress, required - progress, priority, source, Status.ACTIVE, Phase.TARGET_REQUIRED, rowVersion, alternatives, alternative, hashes, true, evidence);
	}

	private static Source source(Fixture fixture, long profileId, Method method, String nodeId, String anchorId, int npcId, int itemId)
	{
		final String factKey = method == Method.RECIPE_PREPARATION ? "recipe:test" : method == Method.QUEST_COLLECTION ? "quest:test" : "drop:test";
		return new Source(hash("source", profileId, method, nodeId, anchorId, npcId, itemId), method, npcId, itemId, factKey, nodeId, anchorId, 0, 0, 0, 0, 0);
	}

	private static String hash(Object... values)
	{
		return PhantomFarmingModel.sha256(values).toLowerCase(java.util.Locale.ROOT);
	}

	private static MemorySocial neutral()
	{
		return social(0, 0, 0);
	}

	private static MemorySocial social(int persistence, int escalation, int cooperation)
	{
		return new MemorySocial(new SocialEvidence(persistence, escalation, cooperation, PhantomFarmingModel.sha256("social", persistence, escalation, cooperation)));
	}

	private static String persistedAgreementId(MemoryStore store)
	{
		for (long profileId : List.of(1L, 2L))
		{
			final FarmingState state = store.state(profileId);
			if (state.latest() != null)
			{
				return state.latest().agreementId();
			}
			if (state.active() != null)
			{
				return state.active().agreementId();
			}
		}
		throw new AssertionError("Fault matrix persisted no agreement identity.");
	}

	private static final PhantomRelevanceSignalPort NO_SIGNALS = new PhantomRelevanceSignalPort()
	{
		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			return SignalDelivery.ACCEPTED;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			return SignalDelivery.ACCEPTED;
		}
	};

	private static final class Fixture implements AutoCloseable
	{
		private final PhantomTopologyCoreSuite.TestBackend backend;
		private final PhantomTopologyService topology;
		private final PhantomFarmingService service;
		private final MemoryStore store;
		private final Map<Long, ConflictSnapshot> facts;
		private final AtomicLong minute;

		private Fixture(PhantomTopologyCoreSuite.TestBackend backend, PhantomTopologyService topology, PhantomFarmingService service, MemoryStore store, Map<Long, ConflictSnapshot> facts, AtomicLong minute)
		{
			this.backend = backend;
			this.topology = topology;
			this.service = service;
			this.store = store;
			this.facts = facts;
			this.minute = minute;
		}

		private void put(ConflictSnapshot snapshot)
		{
			facts.put(snapshot.profileId(), snapshot);
		}

		@Override
		public void close()
		{
			if (service.snapshot().state() != PhantomFarmingService.State.STOPPED)
			{
				service.beginStop();
				PhantomAssertions.assertTrue(service.finishStop(), "Farming fixture retained operations.");
			}
			closeTopology();
		}

		private void closeTopology()
		{
			if (topology.snapshot().state() != PhantomTopologyService.State.STOPPED)
			{
				topology.beginStop();
				PhantomAssertions.assertTrue(topology.finishStop(), "Goal024 topology fixture did not stop.");
			}
		}
	}

	private static final class MemoryStore implements PhantomFarmingPersistencePort
	{
		private final Map<Long, StoredState> states = new HashMap<>();

		@Override
		public synchronized Optional<StoredState> load(long profileId)
		{
			return Optional.ofNullable(states.get(profileId));
		}

		@Override
		public synchronized StoredState save(long profileId, long expectedRowVersion, FarmingState state)
		{
			final StoredState current = states.get(profileId);
			if (((current == null) && (expectedRowVersion != -1)) || ((current != null) && (current.rowVersion() != expectedRowVersion)))
			{
				throw new ConcurrentModificationException("Stale farming row version.");
			}
			final StoredState saved = new StoredState(profileId, current == null ? 0 : current.rowVersion() + 1, state);
			states.put(profileId, saved);
			return saved;
		}

		private synchronized FarmingState state(long profileId)
		{
			return Optional.ofNullable(states.get(profileId)).orElseThrow().state();
		}
	}

	private static final class MemorySocial implements PhantomFarmingService.SocialFacts
	{
		private final SocialEvidence evidence;
		private final Set<String> durable = new HashSet<>();

		private MemorySocial(SocialEvidence evidence)
		{
			this.evidence = evidence;
		}

		@Override
		public SocialEvidence evidence(long ownerProfileId, long counterpartProfileId, long minute)
		{
			return evidence;
		}

		@Override
		public synchronized boolean record(long ownerProfileId, long counterpartProfileId, String eventKey, String eventId, String evidenceHash, long minute)
		{
			durable.add(ownerProfileId + "|" + eventKey + "|" + eventId);
			return true;
		}

		private synchronized int durableCount(String eventKey, String agreementId)
		{
			return (int) durable.stream().filter(value -> value.contains("|" + eventKey + "|")).count();
		}
	}

	private static final class FaultOnce implements PhantomFarmingService.FaultInjector
	{
		private final FaultPoint point;
		private final AtomicBoolean triggered = new AtomicBoolean();

		private FaultOnce(FaultPoint point)
		{
			this.point = point;
		}

		@Override
		public void at(FaultPoint current)
		{
			if ((current == point) && triggered.compareAndSet(false, true))
			{
				throw new IllegalStateException("Injected farming fault at " + current);
			}
		}
	}
}
