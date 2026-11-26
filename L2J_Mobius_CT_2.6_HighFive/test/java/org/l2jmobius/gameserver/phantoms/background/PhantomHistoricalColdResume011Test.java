/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Real JDBC history, with the owned 092 geometry fixture refusing the offscreen transition. */
public final class PhantomHistoricalColdResume011Test
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_011";
	private static final String JDBC = "jdbc:mysql://127.0.0.1:33349/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
	private static final long PROFILE = 92;
	private static final int OBJECT_ID = 920092;

	public static void main(String[] args) throws Exception
	{
		if (args.length != 3 || !List.of("seed", "resume", "seed-idle018", "resume-idle018").contains(args[0]))
		{
			throw new IllegalArgumentException("Usage: seed|resume|seed-idle018|resume-idle018 <module> <owned-fixture-directory>");
		}
		final Path module = Path.of(args[1]).toRealPath();
		final Path fixture = Path.of(args[2]).toRealPath();
		final Path config = fixture.resolve("Database.fixture.ini");
		final var settings = new Properties();
		try (var input = Files.newInputStream(config)) { settings.load(input); }
		PhantomAssertions.assertEquals(JDBC, settings.getProperty("URL"), "Fixture must use the dedicated 011 endpoint.");
		ServerConfig.DATAPACK_ROOT = module.resolve("dist/game").toFile();
		PlayerConfig.load();
		DatabaseFactory.initFromConfig(config.toString());
		try
		{
			try (var db = DatabaseFactory.getConnection(); var query = db.createStatement();
				var rows = query.executeQuery("SELECT DATABASE(), @@port, @@datadir"))
			{
				PhantomAssertions.assertTrue(rows.next() && SCHEMA.equals(rows.getString(1)) && rows.getInt(2) == 33349
					&& Files.isSameFile(fixture.resolve("data"), Path.of(rows.getString(3))), "Fixture SQL ownership mismatch.");
			}
			final var witness = PhantomOffscreen092Checks.witness(module);
			final var planner = PhantomOffscreen092Checks.planner(witness, false, true);
			final var profiles = PhantomProfileRepository.open();
			final var goals = new PhantomGoalStateStore(profiles);
			final var store = new PhantomBackgroundCatchupStore(profiles, goals);
			final boolean idleRange = args[0].endsWith("-idle018");
			if (args[0].startsWith("seed"))
			{
				seed(profiles, goals, store, planner, witness, idleRange);
				return;
			}
			ThreadPool.init();
			try { resume(profiles, goals, store, planner, idleRange); }
			finally { ThreadPool.shutdown(); }
		}
		finally { DatabaseFactory.close(); }
	}

	private static void seed(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomBackgroundCatchupStore store,
		PhantomHistoricalBackgroundPlanner planner, PhantomOffscreen092Checks.Witness witness, boolean idleRange) throws Exception
	{
		final var base = PhantomOffscreen092Checks.state(witness.origin(), List.of());
		final var inventory = new PhantomBackgroundState.InventoryFacts(List.of(), List.of(),
			PhantomBackgroundInventoryHash.compute(List.of()), 0, 1000, 0, 250);
		final var progress = new PhantomBackgroundState.Progress(80, ExperienceData.getInstance().getExpForLevel(80), 27, 0);
		final var nativeFacts = new L2jPhantomBackgroundAuthority(() -> null, () -> null, () -> null, () -> null);
		final var skills = nativeFacts.autoGetSkills(base.identity(), progress.level());
		PhantomAssertions.assertTrue(progress.experience() > 0 && !skills.isEmpty(), "Fixture must load the stock experience and class skill tables.");
		final var state = base.after(progress, base.vitals(), base.position(), inventory, skills,
			new PhantomBackgroundState.Clock(920092, 0, 0), base.receipt());
		final var offscreen = planner.replaceFromState(PROFILE, state, PhantomOffscreen092Checks.goal(witness, 92, 1), 920092, 3);
		PhantomAssertions.assertTrue(offscreen.ready() && PhantomOffscreenOutdoorTravel.pending(offscreen.goal()), "Fixture must persist an eligible offscreen proposal.");
		final var plan = idleRange ? planner.idleFromState(PROFILE, state, offscreen.goal(), 920092, 3) : offscreen;
		final var goal = plan.goal();
		try (var db = DatabaseFactory.getConnection(); var stmt = db.prepareStatement(
			"INSERT INTO characters (charId,char_name,level,exp,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points) VALUES (?,'ColdResume011',80,?,27,100,100,100,100,100,100,?,?,?,0,1,0,1)");
			var skill = db.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,0)"))
		{
			stmt.setInt(1, OBJECT_ID);
			stmt.setLong(2, progress.experience());
			stmt.setInt(3, state.position().x());
			stmt.setInt(4, state.position().y());
			stmt.setInt(5, state.position().z());
			PhantomAssertions.assertEquals(1, stmt.executeUpdate(), "Fixture character was not inserted.");
			for (var rank : skills)
			{
				skill.setInt(1, OBJECT_ID);
				skill.setInt(2, rank.skillId());
				skill.setInt(3, rank.skillLevel());
				skill.addBatch();
			}
			skill.executeBatch();
		}
		PhantomAssertions.assertEquals(PROFILE, profiles.create(OBJECT_ID).profileId(), "Expected a fresh fixture profile sequence.");
		goals.insert(PROFILE, goal);
		final byte[] payload = new PhantomBackgroundStateCodec().encode(state);
		final var stored = profiles.insertComponent(PROFILE, PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, payload);
		final var policy = new PhantomBackgroundSimulationPolicy(2, 80, 1, 1, true, true, true, false, false,
			1, 1, 1, 1, 1, 1, 1, 0, 0, 100, 100, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint(), 120, 60000, 0);
		final var context = PhantomNativeContext.completed(state.identity(), new PhantomNativeContext.Capture(1,
			PhantomNativeContext.Eligibility.SUPPORTED, policy), stored.rowVersion(), payload);
		profiles.insertComponent(PROFILE, PhantomNativeContext.COMPONENT_TYPE, context.schemaVersion(), context.encode());
		final var generation = plan.generation();
		final long target = idleRange ? 1008 : 1003;
		store.claim(PROFILE, new PhantomBackgroundCatchupState(Status.RUNNING, "a".repeat(64), 920092, 1000, target, 1002, 3, 2, 1,
			generation.knowledgeGeneration(), generation.topologyGeneration(), goal.goalId(), goal.revision(), plan.planIdentity(),
			PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), ""));
		System.out.println("SEEDED RUNNING cursor=1002 target=" + target + " ordinal=2");
	}

	private static void resume(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomBackgroundCatchupStore store,
		PhantomHistoricalBackgroundPlanner planner, boolean idleRange)
	{
		final var before = store.load(PROFILE).orElseThrow();
		PhantomAssertions.assertEquals(Status.RUNNING, before.state().status(), "The fixture must resume from persisted RUNNING.");
		final var identities = PhantomIdentityLeaseRegistry.getInstance();
		final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, identities, metrics,
			new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false, 64, 16, metrics), 1);
		final var transaction = new PhantomBackgroundTransaction();
		final var authority = PhantomOffscreen092Checks.generationAuthority(new AtomicLong(1));
		final var signals = new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var background = new PhantomBackgroundService(profiles, goals, identities, transaction, authority,
			new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
		final var history = new PhantomHistoricalBackgroundService(profiles, goals, planner, background, materialization);
		PhantomAssertions.assertTrue(background.start(), "The historical owner did not start.");
		try
		{
			final var canonicalBefore = transaction.load(PROFILE).state();
			PhantomAssertions.assertThrows(AdmissionRejectedException.class, () -> history.beforeMaterialize(PROFILE, OBJECT_ID),
				"NORMAL admission must fence the unfinished claim.");
			try (var foreign = identities.tryAcquire(OBJECT_ID, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
			{
				PhantomAssertions.assertTrue(foreign != null, "Fixture could not hold a competing identity lease.");
				final var busy = history.advance(PROFILE, 1, 1);
				PhantomAssertions.assertEquals("identity.busy", busy.reason(), "A foreign owner must cause a genuine retry.");
				PhantomAssertions.assertEquals(before, store.load(PROFILE).orElseThrow(), "Busy retry changed the durable claim.");
			}
			final var goalBefore = goals.load(PROFILE).orElseThrow().goal();
			if (idleRange)
			{
				final var activeIntent = profiles.insertComponent(PROFILE, "supply.intent", 1,
					new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(goalBefore));
				try
				{
					final var blocked = background.advanceHistorical(PROFILE, goalBefore, before, before.state().advanceIdleTo(1008));
					PhantomAssertions.assertEquals("transaction.native_intent_pending", blocked.reason(), "Accepted intent must keep the idle range uncommitted.");
					PhantomAssertions.assertEquals(before, store.load(PROFILE).orElseThrow(), "Blocked range changed the durable claim.");
					PhantomAssertions.assertEquals(canonicalBefore, transaction.load(PROFILE).state(), "Blocked range changed the durable state.");
				}
				finally { profiles.deleteComponent(PROFILE, "supply.intent", activeIntent.rowVersion()); }
			}
			final int count = idleRange ? 6 : 1;
			final var completed = history.advance(PROFILE, count, count);
			PhantomAssertions.assertTrue(completed.successful() && completed.advancedIntervals() == count, "Cold resume failed: " + completed.reason());
			final var after = store.load(PROFILE).orElseThrow();
			PhantomAssertions.assertEquals(before.state().requestId(), after.state().requestId(), "Recovery replaced the historical owner.");
			PhantomAssertions.assertEquals(before.state().goalRevision() + (idleRange ? 0 : 1), after.state().goalRevision(), "Unexpected historical plan revision.");
			PhantomAssertions.assertEquals(before.state().intervalOrdinal() + count, after.state().intervalOrdinal(), "Unexpected historical interval count.");
			PhantomAssertions.assertEquals(Status.COMPLETE, after.state().status(), "The last interval was not committed.");
			PhantomAssertions.assertTrue(PhantomBackgroundGoalSpec.canonicalHistoricalIdle(PROFILE, goals.load(PROFILE).orElseThrow().goal()),
				"No certified itinerary must produce the existing no-reward idle goal.");
			final var earned = transaction.load(PROFILE).state();
			PhantomAssertions.assertEquals(canonicalBefore.progress(), earned.progress(), "Historical recovery changed EXP/SP.");
			PhantomAssertions.assertEquals(canonicalBefore.inventory(), earned.inventory(), "Historical recovery changed inventory.");
			PhantomAssertions.assertEquals(canonicalBefore.position(), earned.position(), "Historical recovery moved the saved character.");
			PhantomAssertions.assertTrue(!earned.receipt().operationKey().isEmpty(), "No committed interval receipt was written.");
			if (idleRange)
			{
				PhantomAssertions.assertEquals(before.rowVersion() + 1, after.rowVersion(), "Idle minutes must share one catch-up write.");
				final var exactReplay = background.advanceHistorical(PROFILE, goalBefore, before, after.state());
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.IDEMPOTENT, exactReplay.status(), "Idle range replay lost the exact final receipt.");
				System.out.println("TASK018_IDLE_SQL_PASS intervals=6 cursorWrites=1 acceptedIntent=blocked exactReplay=idempotent");
			}
			history.beforeMaterialize(PROFILE, OBJECT_ID);
			history.materializeAborted(PROFILE, OBJECT_ID);
			final var replay = history.advance(PROFILE, 1, 1);
			PhantomAssertions.assertTrue(replay.successful() && replay.advancedIntervals() == 0, "Complete request attempted to process another interval.");
			PhantomAssertions.assertEquals(after, store.load(PROFILE).orElseThrow(), "Completed request changed on replay.");
			PhantomAssertions.assertEquals(earned, transaction.load(PROFILE).state(), "Replay changed the canonical state or receipt.");
			System.out.println("PASS persisted catch-up cold resume, foreign-owner retry, exactly-once commit and NORMAL admission");
		}
		finally
		{
			background.beginStop();
			PhantomAssertions.assertTrue(background.finishStop(), "The owned Background service did not stop.");
		}
	}
}
