package org.l2jmobius.tests.phantoms;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.l2jmobius.gameserver.localplay.LocalPlayPilotActions;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotProtocol;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Guarded TEST DB check of the stock Player action boundary, without a fabricated GameClient. */
public final class LocalPlayPilotNativeSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Player _actor;
	private LocalPlayPilotActions _actions;

	@Override
	public String id()
	{
		return "localplay-pilot-native";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_actor = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue((_actor != null) && (_actor.getClient() == null), "Native fixture unexpectedly has a GameClient.");
		_actions = new LocalPlayPilotActions(_actor);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.cleanupLoadedPlayer(_actor);
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if ("initial-position031".equals(System.getProperty("phantom.m1.native.focus"))) { registry.add("synthetic-first-world-publication-at-checked-setup", this::initialPosition031); return; }
		registry.add("native-pose-and-read-only-snapshot", this::poseAndSnapshot);
		registry.add("setup-addressing-requires-synthetic-owner", context ->
        {
            final var point = _actor.getLocation().clone();
            final var result = execute(LocalPlayPilotProtocol.Operation.SNAPSHOT_PHANTOMS, Map.of("setupProfileId", "18"));
            PhantomAssertions.assertEquals("REJECTED", result.status(), "Addressed setup must not silently use ordinary admitted selection.");
            PhantomAssertions.assertEquals("SETUP_SYNTHETIC_REQUIRED", result.reason(), "Ordinary Player cannot acquire a setup teleport target.");
            PhantomAssertions.assertEquals(point.getX(), _actor.getX(), "Readonly setup moved the Player.");
            PhantomAssertions.assertEquals(point.getY(), _actor.getY(), "Readonly setup moved the Player.");
        });
		registry.add("chat-party-target-and-skill-refusals", this::refusals);
		registry.add("learned-skill-native-path", this::learnedSkill);
		registry.add("envelope-proof-requires-natural-target", this::envelopeProofRequiresNaturalTarget);
		registry.add("origin-remains-returnable-after-actor-location-changes", this::originRemainsReturnable);
		registry.add("m1-invalid-token-never-falls-back-to-origin", this::invalidM1TokenNeverFallsBack);
		registry.add("m1-forward-move-uses-native-guard", this::forwardMoveUsesNativeGuard);
		registry.add("synthetic-identity-is-separate-from-real-login", context ->
		{
			final var kind = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.valueOf("LOCALPLAY_TEST_HUMAN");
			PhantomAssertions.assertTrue(kind != org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN, "Synthetic identity must not impersonate real login.");
		});
		registry.add("synthetic-native-lifecycle-locality-and-cleanup", this::syntheticLifecycle);
		registry.add("known-legacy-quarantine-is-exact-and-fail-closed", context ->
		{
			final var witness = new org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Witness(30, 1234, 19, "payload", "canonical");
			final var known = org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Decision.KNOWN_PREFIX_FAIL_CLOSED;
			final var parser = org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.class.getDeclaredMethod("parseWitnesses", java.util.List.class);
			parser.setAccessible(true);
			final String first = "29\t1233\t18\t" + "a".repeat(64) + "\t" + "b".repeat(64);
			final String second = "30\t1234\t19\t" + "a".repeat(64) + "\t" + "b".repeat(64);
			final var parsed = (Map<?, ?>) parser.invoke(null, java.util.List.of("M1_KNOWN_PREFIX_FAIL_CLOSED_V2", first, second));
			PhantomAssertions.assertEquals(2, parsed.size(), "V2 witnesses were not loaded.");
			for (var invalid : java.util.List.of(java.util.List.of("M1_KNOWN_MIXED_LEGACY_V1", first), java.util.List.of("M1_KNOWN_PREFIX_FAIL_CLOSED_V2", second, first), java.util.List.of("M1_KNOWN_PREFIX_FAIL_CLOSED_V2", first, first)))
			{
				PhantomAssertions.assertThrows(java.lang.reflect.InvocationTargetException.class, () -> parser.invoke(null, invalid), "Old/unsorted/duplicate witness manifest was accepted.");
			}
			PhantomAssertions.assertEquals(known, org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(witness, 30, 1234, 19, "payload", "canonical"), "Exact known witness was not classified.");
			PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(witness, 30, 1234, 20, "payload", "canonical"), "New rowVersion was silently skipped.");
			PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(witness, 30, 1234, 19, "new", "canonical"), "New background was silently skipped.");
			PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(witness, 30, 1234, 19, "payload", "new"), "New canonical corruption was silently skipped.");
			PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(null, 30, 1234, 19, "payload", "canonical"), "Unknown corruption was silently skipped.");
			final var owned = new org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Witness(31, _actor.getObjectId(), 19, "payload", "canonical");
			final var autosave = org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance();
			PhantomAssertions.assertTrue(autosave.contains(_actor), "Native fixture has no autosave owner.");
			PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(owned, 31, _actor.getObjectId(), 19, "payload", "canonical"), "Autosave owner was silently quarantined.");
			autosave.remove(_actor);
			try
			{
				try (var lease = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(_actor.getObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN))
				{
					PhantomAssertions.assertTrue(lease != null, "Fixture lease unavailable.");
					PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(owned, 31, _actor.getObjectId(), 19, "payload", "canonical"), "Identity owner was silently quarantined.");
				}
				_actor.spawnMe();
				try { PhantomAssertions.assertTrue(known != org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.attested(owned, 31, _actor.getObjectId(), 19, "payload", "canonical"), "World owner was silently quarantined."); }
				finally { _actor.decayMe(); }
			}
			finally { autosave.add(_actor); }
			final var selector = LocalPlayPilotActions.class.getDeclaredMethod("quarantineCandidate", long.class, org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Decision.class, java.util.List.class);
			selector.setAccessible(true);
			final var skips = new java.util.ArrayList<String>();
			final var skipped = (LocalPlayPilotActions.Outcome) selector.invoke(null, 71L, known, skips);
			PhantomAssertions.assertEquals("SKIPPED", skipped.status(), "Known prefix did not continue natural order.");
			PhantomAssertions.assertEquals(java.util.List.of("71:KNOWN_PREFIX_FAIL_CLOSED"), skips, "Typed prefix evidence absent.");
			PhantomAssertions.assertEquals(null, selector.invoke(null, 9L, org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Decision.ELIGIBLE, skips), "Next natural eligible candidate was skipped.");
			final var unknown = (LocalPlayPilotActions.Outcome) selector.invoke(null, 5L, org.l2jmobius.gameserver.localplay.LocalPlayM1LegacyQuarantine.Decision.UNKNOWN_INCONSISTENT, skips);
			PhantomAssertions.assertEquals("REJECTED", unknown.status(), "Unknown corruption continued selection.");
			PhantomAssertions.assertEquals("UNKNOWN_INCONSISTENT:5", unknown.reason(), "Unknown boundary lost identity.");
			PhantomAssertions.assertEquals(1, skips.size(), "Unknown corruption was recorded as known.");
			for (long id = 100; id < 107; id++) { selector.invoke(null, id, known, skips); }
			final var capped = (LocalPlayPilotActions.Outcome) selector.invoke(null, 107L, known, skips);
			PhantomAssertions.assertEquals("KNOWN_PREFIX_SKIP_CAP", capped.reason(), "Existing eight-skip cap changed.");
			PhantomAssertions.assertEquals(8, skips.size(), "Skip cap expanded silently.");
		});
	}

	private void initialPosition031(PhantomTestContext context) throws Exception
	{
		final var fixture = _environment.observer();
		final Player original = Player.load(fixture.objectId());
		final Location origin = original.getLocation().clone();
		_environment.cleanupLoadedPlayer(original);
		final int x = -90072, y = 248328;
		final var geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(geo.hasGeo(x, y), "INVALID native setup TEST geometry is absent.");
		final var setup = new Location(x, y, geo.getHeight(x, y, -3568), origin.getHeading(), 0);
		final var session = new org.l2jmobius.gameserver.localplay.LocalPlaySyntheticHumanSession(fixture.objectId(), fixture.characterName());
		Player actor = null;
		java.lang.reflect.Method method = null;
		try
		{
			try { method = session.getClass().getMethod("start", Location.class); } catch (NoSuchMethodException oldApi) { context.record("initialPosition031.oldApi", "original native start first publishes its stored origin"); }
			actor = method == null ? session.start() : (Player) method.invoke(session, setup);
			context.record("initialPosition031.firstWorld", "x=" + actor.getX() + ";y=" + actor.getY() + ";z=" + actor.getZ() + ";requested=" + setup);
			PhantomAssertions.assertTrue(actor.getX() == setup.getX() && actor.getY() == setup.getY() && actor.getZ() == setup.getZ(), "INITIAL_WORLD_SETUP_RED: original native START published a foreign origin before setup.");
			PhantomAssertions.assertTrue(session.valid() && actor.getClient() == null && !actor.hasHeadlessOutboundSession(), "Checked setup changed ordinary native Synthetic identity.");
			final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), 0);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().contains(point), "First native human supplier did not expose the setup point.");
			PhantomAssertions.assertEquals(null, org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(actor.getObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN), "Initial position bypassed identity ownership.");
		}
		finally { session.close(); }
		_environment.assertClean(fixture, actor);
		final Player reloaded = Player.load(fixture.objectId());
		try { PhantomAssertions.assertEquals(origin, reloaded.getLocation(), "Checked setup overwrote immutable canonical origin."); }
		finally { _environment.cleanupLoadedPlayer(reloaded); }
		final var start = method;
		PhantomAssertions.assertTrue(start != null, "Checked native start API absent.");
		PhantomAssertions.assertThrows(java.lang.reflect.InvocationTargetException.class, () -> start.invoke(session, new Location(x, y, setup.getZ(), 0, 1)), "Unsupported initial instance must reject before native publication.");
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.model.World.getInstance().findObject(fixture.objectId()) == null, "Rejected setup leaked World identity.");
	}
	private void syntheticLifecycle(PhantomTestContext context) throws Exception
	{
		final var fixture = _environment.observer();
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET curHp=50,curMp=25,curCp=10 WHERE charId=?"))
		{
			statement.setInt(1, fixture.objectId());
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Guarded TEST submax fixture was not configured.");
		}
		final var session = new org.l2jmobius.gameserver.localplay.LocalPlaySyntheticHumanSession(fixture.objectId(), fixture.characterName());
		Player actor = null;
		Location origin = null;
		try
		{
			actor = session.start(); origin = actor.getLocation().clone();
			PhantomAssertions.assertTrue(session.valid() && (actor.getClient() == null) && !actor.hasHeadlessOutboundSession(), "Synthetic actor did not use ordinary native Player lifecycle.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().contains(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId())), "Production human supplier omitted the native synthetic Player.");
			final var realClient = org.l2jmobius.gameserver.localplay.LocalPlayPilotService.class.getDeclaredMethod("realClient", Player.class);
			realClient.setAccessible(true);
			PhantomAssertions.assertEquals(false, realClient.invoke(null, actor), "Synthetic actor passed realClient guard.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.localplay.LocalPlayPilotService.getInstance().arm(actor, "ABCD2345").contains("недоступен"), "Synthetic actor was armed.");
			PhantomAssertions.assertEquals(null, org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(actor.getObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN), "Real login stole synthetic lease.");
			PhantomAssertions.assertTrue(session.valid(), "Login contention deleted the synthetic Player.");
			final var loginBusy = org.l2jmobius.gameserver.network.GameClient.class.getDeclaredMethod("localPlayIdentityBusy", int.class);
			loginBusy.setAccessible(true);
			PhantomAssertions.assertEquals(true, loginBusy.invoke(null, actor.getObjectId()), "GameClient pre-load arbitration did not reject the synthetic owner.");
			final var backend = new PhantomTopologyCoreSuite.TestBackend();
			final var port = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
			{
				public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var topology = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, port);
			PhantomAssertions.assertTrue(topology.start(), "Native human locality TEST topology failed to start.");
			final var clock = new java.util.concurrent.atomic.AtomicLong(1000);
			final var target = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
			final var locality = new org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl(topology, port, org.l2jmobius.gameserver.phantoms.PhantomSystem::onlineHumanPoints, clock::get, id -> id == 1, () -> Map.of(1L, target));
			locality.onPulse();
			PhantomAssertions.assertTrue(locality.isLocal(1), "Actual World human did not produce native locality demand.");
			final var before = org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints();
			actor.teleToLocation(origin.getX() + 20000, origin.getY(), origin.getZ()); actor.onTeleported();
			PhantomAssertions.assertTrue(!before.equals(org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints()), "Native relocation did not change the production human supplier.");
			clock.addAndGet(1000); locality.onPulse();
			PhantomAssertions.assertTrue(!locality.isLocal(1), "Native relocation left the same production human demand active.");
			topology.beginStop(); PhantomAssertions.assertTrue(topology.finishStop(), "Native locality topology did not clean up.");
			PhantomAssertions.assertTrue(!actor.isTeleporting(), "Native null-client teleport remained pending.");
		}
		finally { session.close(); session.close(); }
		_environment.assertClean(fixture, actor);
		final var loginBusyAfter = org.l2jmobius.gameserver.network.GameClient.class.getDeclaredMethod("localPlayIdentityBusy", int.class);
		loginBusyAfter.setAccessible(true);
		PhantomAssertions.assertEquals(false, loginBusyAfter.invoke(null, fixture.objectId()), "GameClient login remained busy after synthetic cleanup.");
		PhantomAssertions.assertEquals(origin, actor.getLocation(), "Synthetic cleanup lost immutable origin/heading/instance.");
		try (var lease = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(fixture.objectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN)) { PhantomAssertions.assertTrue(lease != null, "Cleanup did not release login arbitration."); }
		try
		{
			final Player again = session.start();
			PhantomAssertions.assertEquals(origin, again.getLocation(), "Repeat start loaded a changed origin.");
			PhantomAssertions.assertEquals(50.0, again.getCurrentHp(), "Cleanup did not persist exact submax HP.");
			PhantomAssertions.assertEquals(25.0, again.getCurrentMp(), "Cleanup did not persist exact submax MP.");
			PhantomAssertions.assertEquals(10.0, again.getCurrentCp(), "Cleanup did not persist exact submax CP.");
		}
		finally { session.close(); }
		_environment.assertClean(fixture, actor);
	}

	private void poseAndSnapshot(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals("SUCCEEDED", execute(LocalPlayPilotProtocol.Operation.STATUS, Map.of()).status(), "Read-only STATUS failed.");
		PhantomAssertions.assertEquals("SUCCEEDED", execute(LocalPlayPilotProtocol.Operation.SIT, Map.of()).status(), "Native sit did not change pose.");
		PhantomAssertions.assertTrue(_actor.isSitting(), "Native sit pose missing.");
		Thread.sleep(2700);
		PhantomAssertions.assertTrue(execute(LocalPlayPilotProtocol.Operation.STAND, Map.of()).status().matches("SUCCEEDED|ACCEPTED"), "Native stand rejected.");
		Thread.sleep(2700);
		PhantomAssertions.assertTrue(!_actor.isSitting(), "Native stand pose missing.");
		PhantomAssertions.assertEquals("SUCCEEDED", execute(LocalPlayPilotProtocol.Operation.STOP_MOVE, Map.of()).status(), "Native stop movement failed.");
	}

	private void refusals(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.SAY, Map.of("channel", "GENERAL", "text", ".admin")).status(), "Pilot chat accepted a voiced/admin prefix.");
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.PARTY_INVITE, Map.of("targetObjectId", Integer.toString(_environment.observer().objectId()), "distributionTypeId", "0")).status(), "Unrostered party invite escaped candidate guard.");
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.PARTY_RESPOND, Map.of("sequence", "1", "requesterObjectId", Integer.toString(_environment.observer().objectId()), "response", "ACCEPT")).status(), "Stale party identity was accepted.");
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.SELECT_TARGET, Map.of("targetObjectId", Integer.toString(_environment.observer().objectId()))).status(), "Unrostered target selected.");
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.CAST_LEARNED_SKILL, Map.of("skillId", "999999", "targetObjectId", Integer.toString(_actor.getObjectId()))).status(), "Unknown skill accepted.");
	}

	private void learnedSkill(PhantomTestContext context)
	{
		final int skillId = _environment.primary().skillId();
		PhantomAssertions.assertTrue(_actor.getKnownSkill(skillId) != null, "TEST fixture lost its learned skill.");
		final LocalPlayPilotActions.Outcome outcome = execute(LocalPlayPilotProtocol.Operation.CAST_LEARNED_SKILL, Map.of("skillId", Integer.toString(skillId), "targetObjectId", Integer.toString(_actor.getObjectId())));
		PhantomAssertions.assertEquals("NATIVE_USE_MAGIC", outcome.reason(), "Learned skill did not reach native useMagic path.");
		final Skill areaSkill = SkillData.getInstance().getSkill(7, 1);
		PhantomAssertions.assertTrue((areaSkill != null) && areaSkill.isAOE(), "Expected stock area skill is unavailable.");
		_actor.addSkill(areaSkill, false);
		PhantomAssertions.assertEquals("SKILL_TARGET_TYPE_NOT_PERMITTED", execute(LocalPlayPilotProtocol.Operation.CAST_LEARNED_SKILL, Map.of("skillId", "7", "targetObjectId", Integer.toString(_actor.getObjectId()))).reason(), "Learned area skill passed pilot target guard.");
	}

	private void envelopeProofRequiresNaturalTarget(PhantomTestContext context)
	{
		final var origin = _actor.getLocation();
		final LocalPlayPilotActions.Outcome preparation = execute(LocalPlayPilotProtocol.Operation.valueOf("PREPARE_M1_ENVELOPE"), Map.of());
		PhantomAssertions.assertEquals("REJECTED", preparation.status(), "Envelope preparation accepted without a natural target.");
		PhantomAssertions.assertEquals(origin, _actor.getLocation(), "Rejected envelope preparation moved the actor.");
		PhantomAssertions.assertEquals("REJECTED", execute(LocalPlayPilotProtocol.Operation.valueOf("SNAPSHOT_M1_ENVELOPE"), Map.of()).status(), "Unprepared envelope snapshot was accepted.");
	}

	private void originRemainsReturnable(PhantomTestContext context)
	{
		final Location original = _actor.getLocation().clone();
		try
		{
			_actor.getLocation().setLocation(new Location(original.getX() + 3000, original.getY(), original.getZ(), original.getHeading(), original.getInstanceId()));
			final var snapshotOrigin = execute(LocalPlayPilotProtocol.Operation.STATUS, Map.of()).candidate();
			PhantomAssertions.assertEquals(Integer.toString(original.getX()), snapshotOrigin.get("originX"), "Pilot STATUS returned the moved actor as its origin.");
			PhantomAssertions.assertEquals(Integer.toString(original.getZ()), snapshotOrigin.get("originZ"), "Pilot STATUS lost the exact captured origin Z after native ground normalization.");
			final LocalPlayPilotActions.Outcome result = execute(LocalPlayPilotProtocol.Operation.TELEPORT_SELF, Map.of("x", Integer.toString(original.getX()), "y", Integer.toString(original.getY()), "z", Integer.toString(original.getZ()), "instanceId", Integer.toString(original.getInstanceId())));
			PhantomAssertions.assertEquals("ACCEPTED", result.status(), "Pilot lost its original return point when the actor's mutable Location changed.");
		}
		finally
		{
			_actor.getLocation().setLocation(original);
		}
	}

	private void invalidM1TokenNeverFallsBack(PhantomTestContext context)
	{
		final Location origin = _actor.getLocation().clone();
		final LocalPlayPilotActions.Outcome result = execute(LocalPlayPilotProtocol.Operation.TELEPORT_SELF, Map.of("x", Integer.toString(origin.getX()), "y", Integer.toString(origin.getY()), "z", Integer.toString(origin.getZ()), "instanceId", Integer.toString(origin.getInstanceId()), "m1Token", "wrong"));
		PhantomAssertions.assertEquals("REJECTED", result.status(), "Invalid M1 token fell through to the ordinary origin teleport allowance.");
		PhantomAssertions.assertEquals("M1_TICKET_INVALID", result.reason(), "Invalid M1 token used an unrelated refusal path.");
		PhantomAssertions.assertEquals(origin, _actor.getLocation(), "Invalid M1 transport moved the actor.");
	}

	private void forwardMoveUsesNativeGuard(PhantomTestContext context)
	{
		final Location origin = _actor.getLocation().clone();
		Location destination = null;
		for (int distance : new int[] {32, 64, 128})
		{
			for (int[] offset : new int[][] {{distance, 0}, {0, distance}, {-distance, 0}, {0, -distance}})
			{
				final int x = origin.getX() + offset[0];
				final int y = origin.getY() + offset[1];
				if (!GeoEngine.getInstance().hasGeo(x, y)) { continue; }
				final int z = GeoEngine.getInstance().getHeight(x, y, origin.getZ());
				if ((Math.abs(z - origin.getZ()) <= 150) && GeoEngine.getInstance().canMoveToTarget(origin.getX(), origin.getY(), origin.getZ(), x, y, z, origin.getInstanceId()))
				{
					destination = new Location(x, y, z, origin.getHeading(), origin.getInstanceId());
					break;
				}
			}
			if (destination != null) { break; }
		}
		PhantomAssertions.assertTrue(destination != null, "Native TEST actor has no short forward geodata segment.");
		try
		{
			final LocalPlayPilotActions.Outcome moved = execute(LocalPlayPilotProtocol.Operation.MOVE_SELF, Map.of("x", Integer.toString(destination.getX()), "y", Integer.toString(destination.getY()), "z", Integer.toString(destination.getZ())));
			PhantomAssertions.assertEquals("ACCEPTED", moved.status(), "Pilot rejected a forward-valid native MOVE_SELF step.");
		}
		finally
		{
			execute(LocalPlayPilotProtocol.Operation.STOP_MOVE, Map.of());
			_actor.getLocation().setLocation(origin);
		}
	}

	private LocalPlayPilotActions.Outcome execute(LocalPlayPilotProtocol.Operation operation, Map<String, String> args)
	{
		return _actions.execute(_actor, new LocalPlayPilotProtocol.Request(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, Instant.now().plusSeconds(30), operation, args));
	}
}
