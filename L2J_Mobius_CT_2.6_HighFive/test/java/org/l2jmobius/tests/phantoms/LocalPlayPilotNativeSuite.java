package org.l2jmobius.tests.phantoms;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.l2jmobius.gameserver.localplay.LocalPlayPilotActions;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotProtocol;
import org.l2jmobius.gameserver.data.xml.SkillData;
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
		registry.add("native-pose-and-read-only-snapshot", this::poseAndSnapshot);
		registry.add("chat-party-target-and-skill-refusals", this::refusals);
		registry.add("learned-skill-native-path", this::learnedSkill);
		registry.add("envelope-proof-requires-natural-target", this::envelopeProofRequiresNaturalTarget);
		registry.add("origin-remains-returnable-after-actor-location-changes", this::originRemainsReturnable);
		registry.add("m1-invalid-token-never-falls-back-to-origin", this::invalidM1TokenNeverFallsBack);
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

	private LocalPlayPilotActions.Outcome execute(LocalPlayPilotProtocol.Operation operation, Map<String, String> args)
	{
		return _actions.execute(_actor, new LocalPlayPilotProtocol.Request(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, Instant.now().plusSeconds(30), operation, args));
	}
}
