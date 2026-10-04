/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.l2jmobius.commons.util.IXmlReader;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureKilled;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** TEST observations of configured production owners; no copied scheduler, recovery or farm implementation. */
public final class PhantomM1WorldContinuationChecks
{
	private PhantomM1WorldContinuationChecks() { }

	public static void prewarmContact(PhantomTestContext context, long profileId, Player human, List<PhantomTopologyPoint> approach, long deadlineNanos) throws Exception
	{
		PhantomAssertions.assertTrue(approach.size() >= 2, "INVALID W05: actual prewarm/contact route is missing.");
		final var destination = approach.getLast();
		PhantomAssertions.assertTrue(PhantomSystem.operatorCanPrewarmAt(profileId, point(human)) && !PhantomNativeLocalityEnvelope.couldKnow(point(human), destination), "INVALID W05: human did not start in prewarm before know-region.");
		await(limit(deadlineNanos, 60_000), () -> present(profileId), "W05 production prewarm did not materialize the actual natural profile.");
		final var first = target(profileId, human);
		final long materialized = System.nanoTime();
		context.record("w.W05.prewarm", first);
		PhantomAssertions.assertFalse(PhantomNativeLocalityEnvelope.couldKnow(point(human), first.observedPosition()), "W05 native materialization occurred only after know-region.");
		move(context, "W05.contact", human, approach, deadlineNanos);
		final var contact = target(profileId, human);
		PhantomAssertions.assertTrue(same(first, contact) && contact.visibleForHuman() && PhantomNativeLocalityEnvelope.couldKnow(point(human), contact.observedPosition()), "W05 contact changed native owner or is not server-visible.");
		context.record("w.W05.materializedBeforeContactNanos", materialized);
		context.record("w.W05.contact", contact);
		context.record("w.W05.clientRender", "UNAVAILABLE_NATIVE_TEST_HAS_NO_GAMECLIENT");
	}

	public static void softReturnBackgroundReentry(PhantomTestContext context, long profileId, Player human, List<PhantomTopologyPoint> leave, List<PhantomTopologyPoint> returnRoute, PhantomM1NativeLoadObserver loadObserver, long deadlineNanos) throws Exception
	{
		final var first = target(profileId, human);
		final Player terminal = World.getInstance().getPlayer(first.objectId());
		final var service = PhantomSystem.configuredMaterializationService();
		PhantomAssertions.assertTrue(service != null, "INVALID W06: configured native owner absent.");
		move(context, "W06.shortLeave", human, leave, deadlineNanos);
		PhantomAssertions.assertFalse(PhantomSystem.operatorHumanLocality(profileId) || PhantomNativeLocalityEnvelope.prewarm(point(human), target(profileId, human).observedPosition()), "INVALID W06: native human did not leave the live envelope.");
		final long shortEnd = limit(deadlineNanos, 15_000);
		while (System.nanoTime() < shortEnd)
		{
			PhantomAssertions.assertTrue(same(first, target(profileId, human)), "W06 short absence churned the native Player/epoch.");
			Thread.sleep(100);
		}
		move(context, "W06.softReturn", human, returnRoute, deadlineNanos);
		PhantomAssertions.assertTrue(same(first, target(profileId, human)), "W06 soft return changed the native Player/epoch.");
		context.record("w.W06.softReturn", "SAME_PLAYER_NO_CHURN object=" + first.objectId() + " epoch=" + first.materializedAtNanos());
		move(context, "W06.longLeave", human, leave, deadlineNanos);
		final long withdrawal = limit(deadlineNanos, 150_000);
		int ordinal = 0;
		while (present(profileId) && System.nanoTime() < withdrawal)
		{
			final var admission = PhantomSystem.operatorAdmissionProfile(profileId).orElseThrow();
			context.record("w.W06.pins." + ordinal++, admission.retentionPins());
			Thread.sleep(1000);
		}
		PhantomAssertions.assertFalse(present(profileId), "W06 native hard pins never released or production did not enter background; no forced dematerialization attempted.");
		PhantomAssertions.assertTrue(service.find(profileId).isEmpty(), "W06 absent actor retained a materialization entry.");
		final var stored = new PhantomBackgroundTransaction().load(profileId);
		PhantomAssertions.assertTrue(stored.successful() && stored.state() != null && "READY".equals(stored.state().state().name()), "W06 background state is not safely stored READY.");
		assertStoredParity(context, "W06.background", stored.state(), terminal);
		context.record("w.W06.background", stored.state());
		move(context, "W06.reentry", human, returnRoute, deadlineNanos);
		await(limit(deadlineNanos, 60_000), () -> present(profileId), "W06 production reentry did not rematerialize the same natural profile.");
		final var reentry = target(profileId, human);
		PhantomAssertions.assertTrue(reentry.objectId() == first.objectId() && reentry.materializedAtNanos() != first.materializedAtNanos(), "W06 reentry lacks the same identity and lawful new epoch.");
		assertCanonicalOwner(context, "W06.reentry", profileId, loadObserver);
		context.record("w.W06.reentry", reentry);
	}

	public static void nativeDeathRecoveryCycle(PhantomTestContext context, long profileId, Monster attacker, PhantomM1NativeLoadObserver loadObserver, long deadlineNanos) throws Exception
	{
		final var before = target(profileId, null);
		final Player victim = World.getInstance().getPlayer(before.objectId());
		PhantomAssertions.assertTrue(victim != null && !victim.isDead() && victim.getParty() == null && attacker != null && attacker.isSpawned() && !attacker.isDead() && !attacker.isRaid() && attacker.getInstanceId() == victim.getInstanceId(), "INVALID W04: live solo native victim/ordinary source attacker is missing.");
		final var corpseOwner = victim.getNativeWorkOwner();
		PhantomAssertions.assertTrue(corpseOwner != null && corpseOwner.player() == victim && corpseOwner.isCurrent() && corpseOwner.epoch() == before.materializedAtNanos(), "INVALID W04: exact native pre-death owner/epoch missing.");
		context.record("w.W04.fixture", "victim=" + victim.getObjectId() + " epoch=" + before.materializedAtNanos() + " level=" + victim.getLevel() + " attacker=" + attacker.getObjectId() + " npc=" + attacker.getId() + " level=" + attacker.getLevel() + " positions=" + point(victim) + "/" + point(attacker));
		final var deathAt = new AtomicLong();
		final var hits = new AtomicInteger();
		final var writer = new AtomicReference<String>();
		final var damage = new ConsumerEventListener(attacker, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if (event.getAttacker() == attacker && event.getTarget() == victim && event.getDamage() > 0 && Double.isFinite(event.getDamage()))
			{
				if (java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.HitTask") && frame.getMethodName().equals("run"))) { hits.incrementAndGet(); writer.set("ACTUAL_NATIVE_HIT_TASK"); }
			}
		}, attacker);
		final var death = new ConsumerEventListener(victim, EventType.ON_CREATURE_DEATH, (OnCreatureDeath event) -> { if (event.getTarget() == victim && event.getAttacker() == attacker) { deathAt.compareAndSet(0, System.nanoTime()); } }, victim);
		attacker.addListener(damage); victim.addListener(death);
		try
		{
			attacker.setTarget(victim);
			attacker.getAI().setIntention(Intention.ATTACK, victim);
			await(limit(deadlineNanos, 120_000), () -> victim.isDead(), "INVALID W04: unchanged native source attacker did not produce actual death within120s; no HP/death/stat override.");
			context.record("w.W04.nativeHits", hits.get());
			context.record("w.W04.nativeWriter", writer.get());
			PhantomAssertions.assertTrue(deathAt.get() > 0 && hits.get() > 0, "INVALID W04: death lacks the exact NPC/native HitTask provenance.");
			final long earliest = deathAt.get() + TimeUnit.SECONDS.toNanos(44);
			PhantomAssertions.assertTrue(earliest <= deadlineNanos, "INVALID W04: scene budget cannot observe the unchanged44s corpse window.");
			while (System.nanoTime() < earliest && System.nanoTime() < deadlineNanos)
			{
				PhantomAssertions.assertTrue(victim.isDead() && World.getInstance().getPlayer(victim.getObjectId()) == victim && victim.getNativeWorkOwner() == corpseOwner && corpseOwner.player() == victim && corpseOwner.isCurrent() && corpseOwner.epoch() == before.materializedAtNanos() && !recovered(profileId, before.materializedAtNanos()), "W04 exact native corpse World/owner/epoch changed or recovered before its ordinary45s window.");
				Thread.sleep(100);
			}
			context.record("w.W04.corpseWindow", "exactWorldPlayerAndOwner=true epoch=" + before.materializedAtNanos() + " observed44s=true earlyNewLifetime=false");
			await(limit(deadlineNanos, 65_000), () -> recovered(profileId, before.materializedAtNanos()), "W04 configured native death owner did not recover/reconcile/rematerialize its actual corpse.");
			assertCanonicalOwner(context, "W04.recovered", profileId, loadObserver);
			final var recovered = target(profileId, null);
			context.record("w.W04.recovered", recovered);
			PhantomAssertions.assertTrue(recovered.objectId() == before.objectId(), "W04 recovery replaced the canonical identity.");
			final var baseline = nativeSample(profileId);
			await(limit(deadlineNanos, 180_000), () -> completeCycle(baseline, nativeSample(profileId)), "W04 recovered native actor never completed its next attributed damage/kill/EXP/SP/distinct-target cycle.");
			context.record("w.W04.nextCycle", nativeSample(profileId));
		}
		finally { attacker.abortAttack(); attacker.getAI().setIntention(Intention.IDLE); attacker.setTarget(null); attacker.removeListener(damage); victim.removeListener(death); }
	}

	public static void restartAndSoak(PhantomTestContext context, PhantomPlayersConfig.Settings settings, Player human, List<Long> frozenCohort, PhantomM1NativeLoadObserver loadObserver, long deadlineNanos) throws Exception
	{
		PhantomAssertions.assertTrue(!frozenCohort.isEmpty(), "INVALID W08: frozen natural cohort missing.");
		final var oldService = PhantomSystem.configuredMaterializationService();
		final var oldScheduler = PhantomSystem.configuredScheduler();
		final Map<Long, Player> terminals = new java.util.HashMap<>();
		for (long id : frozenCohort) { terminals.put(id, World.getInstance().getPlayer(target(id, human).objectId())); }
		final long stopStarted = System.nanoTime();
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "W08 configured save/drain did not reach STOPPED.");
		context.record("w.W08.shutdownNanos", System.nanoTime() - stopStarted);
		PhantomAssertions.assertTrue(oldService.snapshot().retainedEntries() == 0 && oldScheduler.snapshot().registered() == 0, "W08 shutdown retained native owners or scheduler slots.");
		final var saved = new java.util.HashMap<Long, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState>();
		for (long id : frozenCohort)
		{
			final var state = new PhantomBackgroundTransaction().load(id);
			PhantomAssertions.assertTrue(state.successful() && state.state() != null && "READY".equals(state.state().state().name()), "W08 shutdown left unknown/partial background state: " + id);
			saved.put(id, state.state());
			PhantomAssertions.assertTrue(World.getInstance().getPlayer(state.state().identity().characterObjectId()) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(state.state().identity().characterObjectId()) == null, "W08 stopped identity retained a native World/lease owner.");
			context.record("w.W08.saved." + id, state.state());
			assertStoredParity(context, "W08.saved." + id, state.state(), terminals.get(id));
		}
		terminals.clear();
		loadObserver.clearAfterStopped(oldService);
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(settings, loadObserver), "W08 same configured production factory did not restart.");
		await(limit(deadlineNanos, 60_000), () -> frozenCohort.stream().allMatch(PhantomM1WorldContinuationChecks::present), "W08 restarted natural cohort did not reenter; frozen denominator retained.");
		for (long id : frozenCohort)
		{
			PhantomAssertions.assertTrue(target(id, human).objectId() == saved.get(id).identity().characterObjectId(), "W08 restart changed canonical identity.");
			assertCanonicalOwner(context, "W08.restarted." + id, id, loadObserver);
		}
		final long end = limit(deadlineNanos, 60_000);
		PhantomAssertions.assertTrue(end - System.nanoTime() >= TimeUnit.SECONDS.toNanos(55), "INVALID W08: insufficient remaining scene budget for bounded60s resource observation.");
		int ordinal = 0;
		while (System.nanoTime() < end)
		{
			final var service = PhantomSystem.configuredMaterializationService().snapshot();
			final var scheduler = PhantomSystem.configuredScheduler().snapshot();
			context.record("w.W08.resource." + ordinal++, "jvm=" + jvm() + " retained=" + service.retainedEntries() + " permits=" + service.availablePermits() + " queue=" + scheduler.ready() + "/" + scheduler.due() + " registered=" + scheduler.registered());
			PhantomAssertions.assertTrue(service.retainedEntries() <= settings.maxMaterializedPhantoms() && service.availablePermits() >= 0 && scheduler.registered() <= settings.maxScheduledPhantomProfiles() && !service.cleanupEvidenceIncomplete(), "W08 bounded production resources exceeded approved envelope or lost evidence.");
			for (long id : frozenCohort) { PhantomAssertions.assertTrue(PhantomSystem.operatorAdmissionProfile(id).orElseThrow().lastMaterializationFailure() == null, "W08 retained cohort failed after restart: " + id); }
			Thread.sleep(1000);
		}
		context.record("w.W08.cpu", "UNAVAILABLE_IN_JVM_USE_EXACT_LAUNCHER_PID_GET_PROCESS_TOTAL_PROCESSOR_TIME");
		context.record("w.W08.allocations", "UNAVAILABLE_NO_ALLOCATION_AGENT_OR_RUNTIME_FLAG_CHANGE");
	}

	public static void move(PhantomTestContext context, String name, Player human, List<PhantomTopologyPoint> route, long deadlineNanos) throws Exception
	{
		PhantomAssertions.assertTrue(human.isOnline() && !human.hasHeadlessOutboundSession() && !route.isEmpty(), "INVALID W native human/route.");
		for (var destination : route)
		{
			final var start = point(human);
			PhantomAssertions.assertTrue(drySegment(start, destination), "INVALID W native route has missing geo/water/wrong-instance segment: " + start + ">" + destination);
			human.getAI().setIntention(Intention.MOVE_TO, new Location(destination.x(), destination.y(), destination.z()));
			await(limit(deadlineNanos, 60_000), () -> point(human).distanceSquared2D(destination) <= 40_000L, "W native human movement did not reach a validated waypoint: " + destination);
			PhantomAssertions.assertFalse(human.isTeleporting(), "W native movement was replaced by teleport.");
			context.record("w." + name + "." + destination.x() + "." + destination.y(), start + ">" + point(human));
		}
	}

	/** Observes the configured natural actors; it never attaches a new goal, travel owner or AutoPlay session. */
	public static void geography(PhantomTestContext context, Player human, List<Long> cohort, StockScene stock, long deadlineNanos) throws Exception
	{
		final var gremlins = stock.monsters().stream().filter(npc -> npc.getId() == 18342 && npc.getSpawn().getSpawnTerritory() != null && npc.getSpawn().getSpawnTerritory().geometrySnapshot().map(geometry -> geometry.sourcePath().endsWith("Others/Gremlins.xml")).orElse(false)).toList();
		PhantomAssertions.assertFalse(gremlins.isEmpty(), "INVALID W07: natural Elven scene lacks original native Gremlin Spawn owners/provenance.");
		final String gremlinSourceSha256 = sha256(Path.of("data/spawns/Others/Gremlins.xml")); final String waterSourceSha256 = sha256(Path.of("data/zones/water.xml"));
		context.record("w.W07.gremlinSource", "sha256=" + gremlinSourceSha256 + " nativeNpcIds=" + gremlins.stream().map(npc -> npc.getObjectId() + ":" + npc.getNativeEvidenceTarget()).toList());
		final var shoreline = shoreline(point(human));
		PhantomAssertions.assertTrue(shoreline != null, "INVALID W07: no nearby loaded original land/WaterZone boundary with a lawful dry approach.");
		context.record("w.W07.shoreline", "dry=" + shoreline.getFirst() + " water=" + shoreline.getLast() + " waterSourceSha256=" + waterSourceSha256 + " geodataAndNativeWaterZone=true; phantom route unchanged; sourceBoundaryIsNotMovementCredit=true");
		await(limit(deadlineNanos, 60_000), () -> cohort.stream().allMatch(PhantomM1WorldContinuationChecks::present), "W07 frozen natural cohort did not materialize; denominator retained.");
		final Map<Long, PlayerNativeEvidence.Snapshot> baseline = new java.util.HashMap<>();
		final Map<Integer, Long> profiles = new java.util.HashMap<>();
		final Map<Long, PhantomTopologyPoint> positions = new java.util.HashMap<>(); final Map<Long, Long> positionTimes = new java.util.HashMap<>();
		for (long id : cohort) { final var evidence = nativeSample(id); baseline.put(id, evidence); profiles.put(evidence.objectId(), id); positions.put(id, point(World.getInstance().getPlayer(evidence.objectId()))); positionTimes.put(id, System.nanoTime()); }
		final Map<PlayerNativeEvidence.Target, GremlinKill> deaths = new java.util.concurrent.ConcurrentHashMap<>();
		final Map<Monster, ConsumerEventListener> listeners = new java.util.HashMap<>();
		for (Monster gremlin : gremlins)
		{
			final var listener = new ConsumerEventListener(gremlin, EventType.ON_CREATURE_KILLED, (OnCreatureKilled event) ->
			{
				if (event.getTarget() != gremlin || !(event.getAttacker() instanceof Player player)) { return; }
				final Long profile = profiles.get(player.getObjectId()); final var owner = player.getNativeWorkOwner();
				if (profile != null && owner != null && owner.player() == player && owner.isCurrent() && owner.epoch() == baseline.get(profile).epoch() && owner.evidence() != null && deaths.size() < 512)
				{
					final var evidence = owner.evidence().snapshot(); deaths.put(gremlin.getNativeEvidenceTarget(), new GremlinKill(profile, gremlin, evidence.killSequence(), evidence.epoch()));
				}
			}, gremlin);
			gremlin.addListener(listener); listeners.put(gremlin, listener);
		}
		final Set<String> walking = new HashSet<>(); final Set<Long> acceptedGremlin = new HashSet<>(); final Set<Long> completed = new HashSet<>(); final Set<Long> shorelineLinked = new HashSet<>();
		final long started = System.nanoTime(); final long end = limit(deadlineNanos, 180_000); int ordinal = 0;
		try
		{
			while (System.nanoTime() < end)
			{
				for (long id : cohort)
				{
					final var evidence = nativeSample(id); final var initial = baseline.get(id);
					PhantomAssertions.assertTrue(evidence.objectId() == initial.objectId() && evidence.epoch() == initial.epoch(), "W07 frozen participant churned before native geography observation completed: " + id);
					final long lastUseful = Math.max(started, evidence.usefulProgressNanos());
					PhantomAssertions.assertTrue(evidence.sampleNanos() - lastUseful < TimeUnit.SECONDS.toNanos(90), "W07 natural member retained90s useful farm debt: " + id);
					final Player actor = World.getInstance().getPlayer(evidence.objectId());
					PhantomAssertions.assertFalse(actor.isTeleporting(), "W07 native local walking continuity used teleport.");
					final var observed = point(actor); final var previousPosition = positions.put(id, observed); final long observedAt = System.nanoTime(); final long previousAt = positionTimes.put(id, observedAt);
					if (previousPosition.distanceSquared2D(observed) >= 256L)
					{
						final double distance = Math.sqrt(previousPosition.distanceSquared2D(observed)); final double allowed = actor.getMoveSpeed() * ((observedAt - previousAt) / 1_000_000_000.0) + 64;
						PhantomAssertions.assertTrue(previousPosition.instanceId() == observed.instanceId() && distance <= allowed, "W07 actual pose changed beyond native movement speed/continuity; no teleport credit: " + id);
						final var actual = corridor(previousPosition, observed);
						PhantomAssertions.assertTrue(actual.loaded() && !actual.waterReentry() && (actual.startsWet() || actual.wetCells() == 0), "W07 actual native displacement crossed missing geo or unsafe water: " + id);
						if (walking.size() < 512 && walking.add(id + ":" + evidence.epoch() + ":" + previousPosition + ">" + observed)) { context.record("w.W07.actualNativeWalking." + walking.size(), "profile=" + id + " object=" + evidence.objectId() + " epoch=" + evidence.epoch() + " nativeSpeed=" + actor.getMoveSpeed() + " observed=" + previousPosition + ">" + observed + " corridor=" + actual); }
						if (!shorelineLinked.contains(id))
						{
							final var owner = actor.getNativeWorkOwner(); final var witness = actualShoreline(previousPosition, observed, actual);
							if (witness != null)
							{
								PhantomAssertions.assertTrue(observedAt > previousAt && World.getInstance().getPlayer(evidence.objectId()) == actor && actor.getNativeWorkOwner() == owner && owner != null && owner.player() == actor && owner.isCurrent() && owner.epoch() == evidence.epoch(), "W07 shoreline witness lost exact Player/owner/epoch or native sample order: " + id);
								shorelineLinked.add(id);
								context.record("w.W07.actualShorelineWitness." + id, "profile=" + id + " object=" + evidence.objectId() + " epoch=" + evidence.epoch() + " observedNanos=" + previousAt + ">" + observedAt + " observed=" + previousPosition + ">" + observed + " witness=" + witness + " corridor=" + actual + " gremlinSourceSha256=" + gremlinSourceSha256 + " waterSourceSha256=" + waterSourceSha256 + " nativeHeightLayerAndBidirectionalPath=true; issuedDestinationIsNotCredit=true");
							}
						}
					}
					if (actor.isMoving())
					{
						final var origin = point(actor); final var destination = new PhantomTopologyPoint(actor.getXdestination(), actor.getYdestination(), actor.getZdestination(), actor.getInstanceId());
						final var verify = new PhantomTopologyPoint(actor.getXdestination(), actor.getYdestination(), actor.getZdestination(), actor.getInstanceId());
						if (actor.isMoving() && destination.equals(verify) && origin.distanceSquared2D(destination) > 0)
						{
							final var corridor = corridor(origin, destination);
							PhantomAssertions.assertTrue(corridor.loaded() && !corridor.waterReentry() && (corridor.startsWet() || corridor.wetCells() == 0), "W07 actual configured native MOVE_TO has missing geo or illegal dry-to-water/reentry: " + id + " " + origin + ">" + destination + " " + corridor);
							if (ordinal % 100 == 0) { context.record("w.W07.issuedSegment." + ordinal + "." + id, "profile=" + id + " object=" + evidence.objectId() + " epoch=" + evidence.epoch() + " issued=" + origin + ">" + destination + " corridor=" + corridor + " issuedFlagIsNotMovementCredit=true"); }
						}
					}
					for (var death : deaths.entrySet())
					{
						final var receipt = death.getValue();
						if (receipt.profileId() == id && receipt.epoch() == evidence.epoch() && receipt.npc().isDead() && receipt.npc().getNativeEvidenceTarget().equals(death.getKey()) && evidence.killSequence() > receipt.beforeAcceptedKill()) { acceptedGremlin.add(id); context.record("w.W07.acceptedGremlin." + id, "exactNpcLifetime=" + death.getKey() + " beforeAcceptedKill=" + receipt.beforeAcceptedKill() + " acceptedNative=" + evidence); }
					}
					if (completeCycle(initial, evidence)) { completed.add(id); }
					if (ordinal % 100 == 0) { context.record("w.W07.sample." + ordinal + "." + id, PhantomSystem.operatorM1TargetSnapshot(id, human).orElseThrow()); }
				}
				final Set<Long> usefulGremlins = new HashSet<>(completed); usefulGremlins.retainAll(acceptedGremlin); usefulGremlins.retainAll(shorelineLinked);
				if (!walking.isEmpty() && !usefulGremlins.isEmpty() && System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(45)) { context.record("w.W07.fullNativeGremlinCycles", "sameMemberShorelineAndGremlinAward=" + usefulGremlins + " frozenCohort=" + cohort); return; }
				ordinal++; Thread.sleep(100);
			}
			throw new AssertionError("W07 original Elven Gremlin/native land-water scene lacks same-member factual shoreline displacement and accepted same-lifetime kill/reward/next-cycle; shorelineLinked=" + shorelineLinked + " acceptedGremlin=" + acceptedGremlin + " completed=" + completed + "; no source/goal/pose overrides.");
		}
		finally { listeners.forEach((npc, listener) -> npc.removeListener(listener)); deaths.clear(); }
	}

	private static List<PhantomTopologyPoint> shoreline(PhantomTopologyPoint origin)
	{
		final var geo = GeoEngine.getInstance();
		for (int[] direction : List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}, new int[] {1, 1}, new int[] {-1, -1}, new int[] {1, -1}, new int[] {-1, 1}))
		{
			PhantomTopologyPoint previous = origin;
			for (int distance = 128; distance <= 4096; distance += 128)
			{
				final int x = origin.x() + direction[0] * distance; final int y = origin.y() + direction[1] * distance;
				if (!geo.hasGeo(x, y)) { break; }
				final var current = new PhantomTopologyPoint(x, y, geo.getHeight(x, y, previous.z()), origin.instanceId());
				if (ZoneManager.getInstance().getZone(x, y, current.z(), WaterZone.class) != null)
				{
					if (drySegment(origin, previous) && geo.canMoveToTarget(previous.x(), previous.y(), previous.z(), current.x(), current.y(), current.z(), origin.instanceId())) { return List.of(previous, current); }
					break;
				}
				previous = current;
			}
		}
		return null;
	}

	private static ShorelineWitness actualShoreline(PhantomTopologyPoint from, PhantomTopologyPoint to, Corridor actual)
	{
		if (!actual.loaded() || actual.waterReentry() || from.instanceId() != to.instanceId() || from.distanceSquared2D(to) > 144_000_000L) { return null; }
		final var geo = GeoEngine.getInstance(); final var zones = ZoneManager.getInstance();
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		PhantomTopologyPoint previousWet = null; int z = from.z(); int scanned = 0;
		while (cells.next())
		{
			if (++scanned > 1024 || !geo.hasGeoPos(cells.x(), cells.y())) { return null; }
			final int x = GeoEngine.getWorldX(cells.x()); final int y = GeoEngine.getWorldY(cells.y()); z = geo.getHeight(x, y, z);
			final var current = new PhantomTopologyPoint(x, y, z, from.instanceId());
			if (zones.getZone(x, y, z, WaterZone.class) != null) { previousWet = current; continue; }
			if (actual.startsWet() && previousWet != null && adjacentLayerPath(current, previousWet)) { return new ShorelineWitness("WET_PREFIX_EXIT", current, previousWet, scanned); }
			for (int[] offset : List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}))
			{
				final int waterGeoX = cells.x() + offset[0]; final int waterGeoY = cells.y() + offset[1];
				if (!geo.hasGeoPos(waterGeoX, waterGeoY)) { continue; }
				final int waterX = GeoEngine.getWorldX(waterGeoX); final int waterY = GeoEngine.getWorldY(waterGeoY); final int waterZ = geo.getHeight(waterX, waterY, z);
				final var water = new PhantomTopologyPoint(waterX, waterY, waterZ, from.instanceId());
				if (zones.getZone(waterX, waterY, waterZ, WaterZone.class) != null && adjacentLayerPath(current, water)) { return new ShorelineWitness("DRY_CELL_ADJACENT_WATER", current, water, scanned); }
			}
		}
		return null;
	}

	private static boolean adjacentLayerPath(PhantomTopologyPoint dry, PhantomTopologyPoint water)
	{
		final var geo = GeoEngine.getInstance();
		return dry.instanceId() == water.instanceId() && Math.max(Math.abs(GeoEngine.getGeoX(dry.x()) - GeoEngine.getGeoX(water.x())), Math.abs(GeoEngine.getGeoY(dry.y()) - GeoEngine.getGeoY(water.y()))) == 1 && geo.getHeight(dry.x(), dry.y(), water.z()) == dry.z() && geo.getHeight(water.x(), water.y(), dry.z()) == water.z() && geo.canMoveToTarget(dry.x(), dry.y(), dry.z(), water.x(), water.y(), water.z(), dry.instanceId()) && geo.canMoveToTarget(water.x(), water.y(), water.z(), dry.x(), dry.y(), dry.z(), dry.instanceId());
	}

	private static Corridor corridor(PhantomTopologyPoint from, PhantomTopologyPoint to)
	{
		final var geo = GeoEngine.getInstance();
		if (from.instanceId() != to.instanceId() || from.distanceSquared2D(to) > 144_000_000L) { return new Corridor(false, false, 0, true); }
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		final boolean startsWet = ZoneManager.getInstance().getZone(from.x(), from.y(), from.z(), WaterZone.class) != null; boolean becameDry = !startsWet; boolean reentry = false; int wet = 0; int z = from.z();
		while (cells.next())
		{
			if (!geo.hasGeoPos(cells.x(), cells.y())) { return new Corridor(false, startsWet, wet, reentry); }
			final int x = GeoEngine.getWorldX(cells.x()); final int y = GeoEngine.getWorldY(cells.y()); z = geo.getHeight(x, y, z);
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { wet++; if (becameDry) { reentry = true; } } else { becameDry = true; }
		}
		return new Corridor(true, startsWet, wet, reentry);
	}

	private record GremlinKill(long profileId, Monster npc, long beforeAcceptedKill, long epoch) { }
	private record Corridor(boolean loaded, boolean startsWet, int wetCells, boolean waterReentry) { }
	private record ShorelineWitness(String kind, PhantomTopologyPoint dryCell, PhantomTopologyPoint waterCell, int observedCells) { }

	public static boolean drySegment(PhantomTopologyPoint from, PhantomTopologyPoint to)
	{
		final var geo = GeoEngine.getInstance();
		if (from.instanceId() != to.instanceId() || !geo.hasGeo(from.x(), from.y()) || !geo.hasGeo(to.x(), to.y()) || !geo.canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId())) { return false; }
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		int z = from.z();
		while (cells.next()) { if (!geo.hasGeoPos(cells.x(), cells.y())) { return false; } z = geo.getHeight(GeoEngine.getWorldX(cells.x()), GeoEngine.getWorldY(cells.y()), z); if (ZoneManager.getInstance().getZone(GeoEngine.getWorldX(cells.x()), GeoEngine.getWorldY(cells.y()), z, WaterZone.class) != null) { return false; } }
		return true;
	}

	public static PlayerNativeEvidence.Snapshot nativeSample(long profileId)
	{
		final var snapshot = target(profileId, null);
		final Player player = World.getInstance().getPlayer(snapshot.objectId());
		final var owner = player == null ? null : player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(owner != null && owner.player() == player && owner.isCurrent() && owner.epoch() == snapshot.materializedAtNanos() && owner.evidence() != null, "W exact native evidence owner missing.");
		final var result = owner.evidence().snapshot();
		PhantomAssertions.assertTrue(!result.overflow() && result.objectId() == player.getObjectId() && result.epoch() == owner.epoch(), "W native scalar evidence overflow/stale identity.");
		return result;
	}

	private static boolean completeCycle(PlayerNativeEvidence.Snapshot before, PlayerNativeEvidence.Snapshot after)
	{
		return before.objectId() == after.objectId() && before.epoch() == after.epoch() && after.farmCycleSequence() > before.farmCycleSequence() && after.damageSequence() > before.damageSequence() && after.killSequence() > before.killSequence() && after.rewardSequence() > before.rewardSequence() && after.expGained() > before.expGained() && after.spGained() > before.spGained();
	}

	private static void assertCanonicalOwner(PhantomTestContext context, String name, long id, PhantomM1NativeLoadObserver loadObserver)
	{
		final var live = target(id, null); final Player player = World.getInstance().getPlayer(live.objectId());
		final var restored = loadObserver.assertRestored(context, name + ".beforeProgress", id, player, nativeSample(id));
		final var observedEvidence = PlayerNativeWork.checkpoint(player, () ->
		{
			final var stored = new PhantomBackgroundTransaction().load(id);
			PhantomAssertions.assertTrue(stored.successful() && stored.state() != null && stored.state().identity().characterObjectId() == player.getObjectId(), "W restored canonical projection is unavailable/wrong identity.");
			final var evidence = nativeSample(id);
			PhantomAssertions.assertTrue(player.getExp() == Math.addExact(restored.experience(), evidence.expGained()) && player.getSp() == Math.addExact(restored.skillPoints(), evidence.spGained()), "W native progress differs from original loaded progress plus actual earned rewards.");
			context.record("w." + name + ".canonical", "stored=" + stored.state().progress() + " liveExp=" + player.getExp() + " liveSp=" + player.getSp() + " currentLiveFullHash=" + fullHash(player) + " originalLoadParityUsesImmutableWitness=true evidence=" + evidence);
			return evidence;
		});
		// Immutable witness rejection happens on the TEST caller, after native admission reopened.
		loadObserver.assertRestored(context, name, id, player, observedEvidence);
	}

	private static void assertStoredParity(PhantomTestContext context, String name, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState state, Player terminal)
	{
		PhantomAssertions.assertTrue(terminal != null && World.getInstance().getPlayer(terminal.getObjectId()) == null && terminal.getNativeWorkOwner() == null, "W terminal parity read preceded native owner/World release.");
		PhantomAssertions.assertEquals(state.identity().characterObjectId(), terminal.getObjectId(), "W terminal saved identity changed.");
		PhantomAssertions.assertEquals(state.progress().level(), terminal.getLevel(), "W terminal level was not preserved.");
		PhantomAssertions.assertEquals(state.progress().experience(), terminal.getExp(), "W terminal native EXP was not saved.");
		PhantomAssertions.assertEquals(state.progress().skillPoints(), terminal.getSp(), "W terminal native SP was not saved.");
		final String actualHash = storedNativeInventoryHash(terminal.getObjectId());
		PhantomAssertions.assertEquals(state.inventory().canonicalHash(), actualHash, "W full native stored inventory/equipment disagrees with the durable projection.");
		context.record("w." + name + ".terminalParity", "object=" + terminal.getObjectId() + " level=" + terminal.getLevel() + " exp=" + terminal.getExp() + " sp=" + terminal.getSp() + " nativeStoredFullInventoryHash=" + actualHash + " exactClosedNativeOwner=true; postDeleteInMemoryInventoryUnavailable=ItemContainerClearsItems");
	}

	private static String storedNativeInventoryHash(int objectId)
	{
		return storedNativeInventoryHash(storedNativeInventoryRows(objectId));
	}

	private static String storedNativeInventoryHash(List<NativeInventoryRow> rows)
	{
		return PhantomBackgroundInventoryHash.compute(rows.stream().map(NativeInventoryRow::item).toList());
	}

	private record NativeInventoryRow(CanonicalItem item, int locationSlot, int enchantLevel) { }

	private static List<NativeInventoryRow> storedNativeInventoryRows(int objectId)
	{
		final List<NativeInventoryRow> items = new ArrayList<>();
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT object_id, item_id, count, loc, loc_data, enchant_level FROM items WHERE owner_id=? AND count>0 AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
		{
			query.setInt(1, objectId); query.setQueryTimeout(30);
			try (var rows = query.executeQuery()) { while (rows.next()) { items.add(new NativeInventoryRow(new CanonicalItem(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("count"), ItemLocation.valueOf(rows.getString("loc"))), rows.getInt("loc_data"), rows.getInt("enchant_level"))); } }
			return List.copyOf(items);
		}
		catch (java.sql.SQLException failure) { throw new IllegalStateException("W guarded TEST native inventory rows unavailable.", failure); }
	}

	public static void realAttackerDiagnostic(PhantomTestContext context, long profileId, Player human, long deadlineNanos) throws Exception
	{
		final var observed = target(profileId, human); final Player victim = World.getInstance().getPlayer(observed.objectId());
		PhantomAssertions.assertTrue(human.isOnline() && !human.hasHeadlessOutboundSession() && human != victim && human.getInstanceId() == victim.getInstanceId(), "INVALID W08 ordinary REAL native attacker.");
		final double hp = victim.getCurrentHp(); final double cp = victim.getCurrentCp(); final var hits = new AtomicInteger();
		final var listener = new ConsumerEventListener(human, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == human && event.getTarget() == victim && event.getDamage() > 0 && java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.HitTask") && frame.getMethodName().equals("run"))) { hits.incrementAndGet(); } }, human);
		human.addListener(listener);
		try
		{
			human.setTarget(victim); human.getAI().setIntention(Intention.ATTACK, victim);
			final long end = limit(deadlineNanos, 5_000); while (System.nanoTime() < end && !victim.isDead()) { Thread.sleep(100); }
			context.record("w.W08.realAttackerDiagnostic", "ordinaryObject=" + human.getObjectId() + " target=" + victim.getObjectId() + " epoch=" + observed.materializedAtNanos() + " nativeHitTaskEvents=" + hits.get() + " hpDelta=" + (hp - victim.getCurrentHp()) + " cpDelta=" + (cp - victim.getCurrentCp()) + " dead=" + victim.isDead() + " pvpFlags=" + human.getPvpFlag() + "/" + victim.getPvpFlag() + " nativeEligibilityRulesPreserved=true; no new PvP milestone");
		}
		finally { human.abortAttack(); human.getAI().setIntention(Intention.IDLE); human.setTarget(null); human.removeListener(listener); }
	}

	private static String fullHash(Player player) { return PhantomBackgroundInventoryHash.compute(player.getInventory().getItems().stream().filter(item -> item.getCount() > 0 && Set.of("INVENTORY", "PAPERDOLL").contains(item.getItemLocation().name())).map(item -> new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList()); }
	private static PhantomSystem.OperatorM1TargetSnapshot target(long id, Player human) { final var result = PhantomSystem.operatorM1TargetSnapshot(id, human).orElseThrow(); PhantomAssertions.assertTrue(result.worldPresent() && result.materializedAtNanos() > 0, "W exact native actor missing: " + id); return result; }
	private static boolean present(long id) { return PhantomSystem.operatorM1TargetSnapshot(id).map(PhantomSystem.OperatorM1TargetSnapshot::worldPresent).orElse(false); }
	private static boolean recovered(long id, long oldEpoch) { final var snapshot = PhantomSystem.operatorM1TargetSnapshot(id).orElse(null); if (snapshot == null || !snapshot.worldPresent() || snapshot.materializedAtNanos() == oldEpoch) { return false; } final Player player = World.getInstance().getPlayer(snapshot.objectId()); return player != null && !player.isDead() && !player.isTeleporting() && "ACTIVE".equals(snapshot.materializationState()); }
	private static boolean same(PhantomSystem.OperatorM1TargetSnapshot first, PhantomSystem.OperatorM1TargetSnapshot second) { return first.objectId() == second.objectId() && first.materializedAtNanos() == second.materializedAtNanos() && second.worldPresent(); }
	public static PhantomTopologyPoint point(Player player) { return new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()); }
	private static long limit(long sceneDeadline, long milliseconds) { return Math.min(sceneDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds)); }
	private static void await(long deadline, BooleanSupplier predicate, String failure) throws Exception { while (!predicate.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(100); } PhantomAssertions.assertTrue(predicate.getAsBoolean(), failure); }
	private static String jvm() { final var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(); long count = 0; long millis = 0; for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) { count += Math.max(0, gc.getCollectionCount()); millis += Math.max(0, gc.getCollectionTime()); } return "heap=" + heap.getUsed() + "/" + heap.getCommitted() + "/" + heap.getMax() + " gc=" + count + "/" + millis + " threads=" + ManagementFactory.getThreadMXBean().getThreadCount(); }

	/** Native SpawnData parser receives unchanged source-defined sections; all source counts/respawn/AI remain stock. */
	public static StockScene stockScene(PhantomTestContext context, PhantomTopologyPoint contact) throws Exception
	{
		final var before = new HashSet<Spawn>(); SpawnTable.getInstance().getSpawnTable().values().forEach(before::addAll);
		context.record("w.bootstrap.nativeWorldNpcCount", World.getInstance().getVisibleObjects().stream().filter(Npc.class::isInstance).count());
		context.record("w.bootstrap.nativeSpawnOwnerCount", before.size());
		if (!before.isEmpty())
		{
			final Set<Spawn> borrowed = new HashSet<>();
			for (Spawn spawn : before)
			{
				if (spawn.getSpawnedNpcs().stream().anyMatch(npc -> npc.getInstanceId() == contact.instanceId() && Math.abs((long) npc.getZ() - contact.z()) <= 300 && point(npc).distanceSquared2D(contact) <= 16_000_000L)) { borrowed.add(spawn); }
			}
			PhantomAssertions.assertFalse(borrowed.isEmpty(), "INVALID W: full native stock bootstrap has no original Spawn owner near the natural scene; additional NPCs are not manufactured.");
			final Set<String> recorded = new HashSet<>();
			for (Spawn spawn : borrowed)
			{
				context.record("w.bootstrap.nativeOwner." + spawn.getId() + "." + spawn.getName(), "count=" + spawn.getAmount() + " respawnMinMillis=" + spawn.getRespawnMinDelay() + " respawnMaxMillis=" + spawn.getRespawnMaxDelay() + " live=" + spawn.getSpawnedNpcs().size());
				final var territory = spawn.getSpawnTerritory();
				if (territory != null)
				{
					final var geometry = territory.geometrySnapshot().orElse(null);
					if (geometry != null && recorded.add(geometry.sourcePath())) { final Path source = Path.of(geometry.sourcePath()); context.record("w.bootstrap.source." + geometry.sourcePath(), "sha256=" + sha256(source) + " geometry=" + geometry.hash()); }
				}
			}
			context.record("w.bootstrap.provenance", "REUSED_ACTUAL_MASTER_HANDLER_SPAWN_DATA_INIT; additionalNativeSpawns=0; nativeBootstrapOwnsCleanup");
			return new StockScene(Set.of(), borrowed);
		}
		final var nativeParser = new SpawnData() { @Override public void load() { } };
		final var sources = new ArrayList<Path>();
		try (var paths = Files.walk(Path.of("data/spawns"))) { sources.addAll(paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList()); }
		PhantomAssertions.assertTrue(sources.size() <= 2048, "INVALID W stock spawn source inventory exceeded bounded parser scope.");
		final var failure = new AtomicReference<Throwable>();
		try
		{
		for (Path source : sources)
		{
			final IXmlReader selector = new IXmlReader()
			{
				@Override public void load() { }
				@Override public void parseDocument(Document document, File file)
				{
					try
					{
						final var selected = (Document) document.cloneNode(true); final var root = selected.getDocumentElement(); int sections = 0; int count = 0;
						for (Node node = root.getFirstChild(); node != null;)
						{
							final Node next = node.getNextSibling();
							if (node instanceof Element spawn && spawn.getTagName().equals("spawn"))
							{
								final var territory = spawn.getElementsByTagName("territory"); boolean near = false;
								if (territory.getLength() > 0)
								{
									final var nodes = ((Element) territory.item(0)).getElementsByTagName("node"); int minX = Integer.MAX_VALUE; int minY = Integer.MAX_VALUE; int maxX = Integer.MIN_VALUE; int maxY = Integer.MIN_VALUE;
									for (int n = 0; n < nodes.getLength(); n++) { final var vertex = (Element) nodes.item(n); final int x = Integer.parseInt(vertex.getAttribute("x")); final int y = Integer.parseInt(vertex.getAttribute("y")); minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
									final var altitude = (Element) territory.item(0); final int minZ = Integer.parseInt(altitude.getAttribute("minZ")); final int maxZ = Integer.parseInt(altitude.getAttribute("maxZ"));
									near = contact.x() >= minX - 2500L && contact.x() <= maxX + 2500L && contact.y() >= minY - 2500L && contact.y() <= maxY + 2500L && contact.z() >= minZ - 300L && contact.z() <= maxZ + 300L;
								}
								if (!near) { root.removeChild(node); }
								else { sections++; final var npcs = spawn.getElementsByTagName("npc"); for (int n = 0; n < npcs.getLength(); n++) { final var npc = (Element) npcs.item(n); final int delay = npc.hasAttribute("respawnDelay") ? Integer.parseInt(npc.getAttribute("respawnDelay")) : 0; PhantomAssertions.assertTrue(delay <= 30, "INVALID W selected stock respawn exceeds cleanup45: " + source); count += npc.hasAttribute("count") ? Integer.parseInt(npc.getAttribute("count")) : 1; } }
							}
							else { root.removeChild(node); }
							node = next;
						}
						if (sections > 0) { PhantomAssertions.assertTrue(count <= 512, "INVALID W original stock scene exceeds bounded512 NPC fixture envelope."); context.record("w.bootstrap." + source, "sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))) + " nativeSections=" + sections + " stockCount=" + count); nativeParser.parseDocument(selected, file); }
					}
					catch (Throwable error) { failure.compareAndSet(null, error); }
				}
			};
			selector.parseFile(source.toFile());
			if (failure.get() != null) { throw new IllegalStateException("INVALID W native stock spawn bootstrap: " + source, failure.get()); }
		}
		final var owned = addedSpawns(before);
		PhantomAssertions.assertFalse(owned.isEmpty(), "INVALID W stock source sections did not establish native Spawn owners.");
		PhantomAssertions.assertTrue(owned.stream().mapToInt(spawn -> spawn.getSpawnedNpcs().size()).sum() <= 512, "INVALID W cumulative stock scene exceeds bounded512 native NPC envelope.");
		context.record("w.bootstrap.nativeSpawnOwners", owned.size());
		return new StockScene(owned, owned);
		}
		catch (Exception | Error primary) { try { final var added = addedSpawns(before); new StockScene(added, added).close(); } catch (Throwable cleanup) { primary.addSuppressed(cleanup); } throw primary; }
	}

	private static Set<Spawn> addedSpawns(Set<Spawn> before) { final var result = new HashSet<Spawn>(); SpawnTable.getInstance().getSpawnTable().values().forEach(result::addAll); result.removeAll(before); return result; }
	private static String sha256(Path source) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))); }
	private static PhantomTopologyPoint point(Npc npc) { return new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()); }

	public static final class StockScene implements AutoCloseable
	{
		private final Set<Spawn> _owned;
		private final Set<Spawn> _scene;
		private StockScene(Set<Spawn> owned, Set<Spawn> scene) { _owned = Set.copyOf(owned); _scene = Set.copyOf(scene); }
		public List<Monster> monsters() { return _scene.stream().flatMap(spawn -> spawn.getSpawnedNpcs().stream()).filter(Monster.class::isInstance).map(Monster.class::cast).filter(npc -> npc.isSpawned() && !npc.isDead() && !npc.isRaid()).toList(); }
		@Override public void close() throws Exception
		{
			if (_owned.isEmpty()) { return; }
			_owned.forEach(Spawn::stopRespawn);
			final var npcs = new HashSet<Npc>(); _owned.forEach(spawn -> npcs.addAll(spawn.getSpawnedNpcs())); World.getInstance().getVisibleObjects().stream().filter(Npc.class::isInstance).map(Npc.class::cast).filter(npc -> _owned.contains(npc.getSpawn())).forEach(npcs::add);
			npcs.forEach(Npc::deleteMe);
			await(System.nanoTime() + TimeUnit.SECONDS.toNanos(32), () -> _owned.stream().allMatch(spawn -> spawn._scheduledCount == 0), "W native stock respawn ownership did not drain before cleanup45.");
			_owned.forEach(spawn -> SpawnTable.getInstance().removeSpawn(spawn));
			PhantomAssertions.assertTrue(npcs.stream().allMatch(npc -> World.getInstance().findObject(npc.getObjectId()) != npc), "W stock native fixture retained a World NPC.");
		}
	}
}
