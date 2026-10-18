/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** One exact new061 canonical-floor reconciliation; never clears an old quarantine or alters earned scalars. */
final class PhantomCareer061AutoGetReconcile
{
	private PhantomCareer061AutoGetReconcile() { }

	static void run() throws Exception
	{
		final Path output = PhantomCampaign058PersistentLane.LANE.resolve("task061");
		final Path plan = output.resolve("career35-autoget-reconcile061.plan.txt");
		PhantomAssertions.assertFalse(Files.exists(plan), "One-use reconciliation already attempted; retain its receipt.");
		PhantomGameKnowledgeService knowledge = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(61006135, PhantomCampaign058PersistentLane.MODULE, output.resolve("career35-reconcile")));
			final var profiles = PhantomProfileRepository.open();
			final var component = profiles.findComponent(35, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			final String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(component.payload()));
			PhantomAssertions.assertTrue(component.rowVersion() == 9 && digest.equals("25a706831f4865c457374d1ebdccf81a0f94cb6279d879879e1776c2379cf820"), "Exact new35 marker changed; no broad repair is allowed.");
			final var previous = new PhantomBackgroundStateCodec().decode(component.payload());
			PhantomAssertions.assertTrue(previous.state() == PhantomBackgroundState.State.INCONSISTENT && previous.identity().characterObjectId() == 268481295 && previous.identity().activeClassId() == 0
				&& previous.progress().level() == 20 && previous.progress().experience() == 837945 && previous.progress().skillPoints() == 92, "Pinned canonical earned state differs.");
			PhantomAssertions.assertTrue(World.getInstance().findObject(268481295) == null && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(268481295), "New35 still has a runtime owner.");
			final String retainedBefore = PhantomCareer061PreviewSetup.retainedDigest(34);
			final String canonicalBefore = canonicalDigest();
			final var skillsBefore = skills();
			PhantomAssertions.assertEquals(Map.of(194, 1, 1320, 2, 1322, 1), skillsBefore, "Only missing free Expertise239:1 is within this exact migration.");
			final var topologyBackend = new L2jTopologyValidationBackend();
			final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var policy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
			knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), topology, policy));
			PhantomAssertions.assertTrue(knowledge.start(), "Native knowledge is unavailable.");
			final var query = knowledge.query(); final var progressionPolicy = PhantomProgressionPolicy.productionDefaults();
			final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
			final var commerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
			final var authority = new L2jPhantomBackgroundAuthority(() -> query, () -> topology, () -> progression, () -> commerce, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology));
			PhantomAssertions.assertEquals(previous.hashes(), authority.hashes(), "Native data authority differs from the retained35 witness.");
			Files.writeString(plan, "EXACT_NEW35_ONLY; oldRow=9; oldPayload=" + digest + "; canonical=" + canonicalBefore + "; committed level20/EXP837945/SP92 unchanged; only native free239:1 restored at its current canonical level; no EXP/SP/HP/MP/XYZ/items/quest grants; no old34 mutation", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			try (var identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(268481295, PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND); var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(268481295))
			{
				PhantomAssertions.assertTrue(identity != null, "Pinned identity is already owned.");
				final Player player = Player.load(268481295);
				PhantomAssertions.assertTrue(player != null, "Pinned native character cannot load.");
				try
				{
					player.stopAllTasks();
					PhantomAssertions.assertTrue(player.getName().equals("T061Career") && player.getActiveClass() == 0 && player.getClassIndex() == 0 && player.getLevel() == 20 && player.getExp() == 837945 && player.getSp() == 92, "Native loaded identity/scalars differ.");
					player.rewardSkills(); // Stock free auto-get only; before/after assertions reject any paid or foreign addition.
					final var expected = new LinkedHashMap<>(skillsBefore); expected.put(239, 1);
					PhantomAssertions.assertEquals(expected, skills(), "Native free-floor reconciliation changed another skill.");
					PhantomAssertions.assertEquals(canonicalBefore, canonicalDigest(), "Native character/items changed; no earned scalar rewrite is allowed.");
					final var goal = new PhantomGoalStateStore(profiles).load(35).orElseThrow().goal();
					final var capture = authority.captureOwnedNative(35, player, goal, previous);
					PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, new PhantomBackgroundTransaction().captureLifecycleBaseline(capture.state(), goal, capture.context()).status(), "Exact canonical revalidation did not commit.");
				}
				finally { player.stopAllTasks(); player.deleteMe(); }
			}
			PhantomAssertions.assertEquals(canonicalBefore, canonicalDigest(), "Native cleanup changed canonical character/items.");
			PhantomAssertions.assertEquals(retainedBefore, PhantomCareer061PreviewSetup.retainedDigest(34), "Old34 changed during exact35 reconciliation.");
			Files.writeString(output.resolve("career35-autoget-reconcile061.complete.txt"), "CANONICAL_UNCHANGED=" + canonicalBefore + "; OLD34_UNCHANGED=" + retainedBefore + "; native free239:1 restored; baseline revalidated; no earned/quest/input grants", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("EXACT35_NATIVE_AUTOGET_RECONCILED; canonical/old34 unchanged; no EXP/SP/items/HP/XYZ/quest value changes");
		}
		finally { if (knowledge != null) { knowledge.beginStop(); knowledge.finishStop(); } ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	private static Map<Integer, Integer> skills() throws Exception
	{
		final Map<Integer, Integer> result = new LinkedHashMap<>();
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT skill_id,skill_level FROM character_skills WHERE charId=268481295 AND class_index=0 ORDER BY skill_id"))
		{ while (rows.next()) { result.put(rows.getInt(1), rows.getInt(2)); } }
		return Map.copyOf(result);
	}

	private static String canonicalDigest() throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var connection = DatabaseFactory.getConnection())
		{
			for (String query : java.util.List.of("SELECT * FROM characters WHERE charId=268481295", "SELECT * FROM items WHERE owner_id=268481295 ORDER BY object_id"))
			{ try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) { while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = String.valueOf(rows.getObject(column)); digest.update((value.length() + ":" + value + "|").getBytes(StandardCharsets.UTF_8)); } } } }
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
