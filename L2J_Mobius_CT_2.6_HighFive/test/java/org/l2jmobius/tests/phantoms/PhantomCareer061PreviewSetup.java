/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** One pre-enrollment continuation: two NEW canonical identities; existing058a rows are read-only. */
final class PhantomCareer061PreviewSetup
{
	private static final String ANCHOR = "generated.farm.bfb0335de417bc81a7e9b231.anchor";
	private record Role(String name, int classId, int level, int weapon, int shots) { }
	private static final List<Role> ROLES = List.of(new Role("T061LifeF", 0, 4, 68, 1835), new Role("T061LifeM", 10, 6, 8, 2509));
	private PhantomCareer061PreviewSetup() { }

	static void run() throws Exception
	{
		run(false, false);
	}

	static void runCareer() throws Exception
	{
		run(true, false);
	}

	static void finishCareerInput() throws Exception
	{
		run(true, true);
	}

	private static void run(boolean career, boolean finishInput) throws Exception
	{
		final var roles = career ? List.of(new Role("T061Career", 0, 19, 68, 1835)) : ROLES;
		final int retainedProfiles = career ? 34 : 32;
		final String phase = career ? "career" : "preview";
		final int addedAdena = career ? 50_000 : 10_000;
		final int addedCharges = career ? 3000 : 300;
		final Path output = PhantomCampaign058PersistentLane.LANE.resolve("task061");
		final Path planPath = output.resolve(phase + (finishInput ? "-revised-input061.plan.tsv" : "-input061.plan.tsv"));
		if (finishInput) { PhantomAssertions.assertTrue(Files.isRegularFile(output.resolve("career-input061.plan.tsv")), "Retained unfinished preparation is absent."); }
		PhantomAssertions.assertTrue(Files.isRegularFile(output.resolve("initial-stopped058a.complete.json")), "Required initial stopped backup is absent.");
		PhantomAssertions.assertFalse(Files.exists(planPath) || Files.exists(output.resolve(phase + "-input061.complete.tsv")), "Setup is one-use; inspect retained state instead of replay.");
		PhantomGameKnowledgeService knowledge = null;
		try
		{
			final var context = new PhantomTestContext(61006101, PhantomCampaign058PersistentLane.MODULE, output.resolve(phase + "-preparation"));
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(context);
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
			final var zone = ZoneId.of("UTC");
			final var population = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v2.xml"), zone);
			final var previous = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v1.xml"), zone);
			final var profiles = PhantomProfileRepository.open();
			final String retainedBefore = retainedDigest(retainedProfiles);
			final var store = new PhantomPopulationStore(profiles, population, previous, zone);
			final var existing = store.loadManagedAfter(0, 128);
			PhantomAssertions.assertEquals(retainedProfiles + (finishInput ? 1 : 0), existing.size(), "Retained identities differ; no existing profile may be replaced.");
			long nextProfile;
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'"))
			{ PhantomAssertions.assertTrue(rows.next(), "Profile sequence absent."); nextProfile = rows.getLong(1); }
			final long generation = existing.stream().mapToLong(value -> value.state().populationGeneration()).max().orElseThrow();
			final long ordinal = existing.stream().mapToLong(value -> value.state().creationOrdinal()).max().orElseThrow() + (finishInput ? 0 : 1);
			PhantomAssertions.assertTrue(nextProfile > existing.stream().mapToLong(value -> value.profile().profileId()).max().orElseThrow(), "Profile sequence is not beyond the retained cohort.");
			if (finishInput) { nextProfile = 35; PhantomAssertions.assertTrue(goalsAbsent(profiles, 35), "Unfinished preparation already has an enrolled goal/history/ecology."); }
			final List<Long> seeds = new ArrayList<>();
			for (int index = 0; index < roles.size(); index++)
			{
				final var role = roles.get(index); long selected = 0;
				for (long candidate = 61006101; candidate < 61026101; candidate++)
				{
					final var archetype = population.chooseArchetype(candidate, ordinal + index);
					if (archetype == CareerArchetype.DAMAGE && population.chooseClass(PhantomCampaign058SceneSetup.mix(candidate, nextProfile + index), archetype).classId() == role.classId()) { selected = candidate; break; }
				}
				PhantomAssertions.assertTrue(selected > 0, "No bounded canonical birth input for declared class."); seeds.add(selected);
			}
			final var topologyBackend = new L2jTopologyValidationBackend();
			final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var policy = PhantomGameKnowledgePolicy.productionDefaults();
			final var backend = new L2jGameKnowledgeBackend();
			knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), topology, policy));
			PhantomAssertions.assertTrue(knowledge.start(), "Native knowledge unavailable before preparation.");
			final var query = knowledge.query();
			final var progressionPolicy = PhantomProgressionPolicy.productionDefaults();
			final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
			final var commerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
			final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
			final var authority = new L2jPhantomBackgroundAuthority(() -> query, () -> topology, () -> progression, () -> commerce, routes);
			final var planner = new PhantomHistoricalBackgroundPlanner(query, topology, authority);
			final var ecology = PhantomPopulationEcologyCatalog.load(Path.of("data/phantoms/population/high-five-ecology-v1.xml"), population,
				org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml")));
			final var anchor = finishInput ? query.suitableTargets(new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery(17, 21, 19, null, null,
				Set.of(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER), true, true, null, null, null, org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest.first(256))).values().stream()
				.flatMap(target -> target.representativeAreas().stream()).filter(area -> area.instanceId() == 0 && area.totalConfiguredAmount() > 0 && area.topologyNodeId() != null)
				.flatMap(area -> topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream())
				.filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && GeoEngine.getInstance().hasGeo(value.point().x(), value.point().y()))
				.min(java.util.Comparator.comparingDouble(value -> Math.hypot(value.point().x() + 81861, value.point().y() - 149197))).orElseThrow() : career ? topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream)
				.filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0)
				.min(java.util.Comparator.comparingDouble(value -> Math.hypot(value.point().x() + 81861, value.point().y() - 149197))).orElseThrow() : topology.findAnchor(ANCHOR).orElseThrow();
			final var geo = GeoEngine.getInstance();
			PhantomAssertions.assertTrue(geo.hasGeo(anchor.point().x(), anchor.point().y()), "Declared farm lacks native geometry.");
			final List<String> plan = new ArrayList<>(List.of("PREMEASUREMENT_ONLY; NEW canonical profiles=" + roles.size() + "; retained=" + retainedProfiles + "; current join/cursor; evening calendar; no historical reward/reset; ACTIVE8/materialized16 unchanged; SyntheticOFF; native birth stock retained; finite added charges=" + addedCharges + "/Adena=" + addedAdena + "; " + (career ? "prepared Fighter19 near20 is not earned1-19; Q401 ABSENT/no1138-1145; native full quest/master; episode90minutes per durable intent" : "maximum active episode30minutes"), "profile\tordinal\tseed\tname\tclass\tlevel\tSP\tAdenaAdded\tchargesAdded\tanchor"));
			for (int index = 0; index < roles.size(); index++) { final var role = roles.get(index); plan.add((nextProfile + index) + "\t" + (ordinal + index) + "\t" + seeds.get(index) + "\t" + role.name() + "\t" + role.classId() + "\t" + role.level() + "\t0\t" + addedAdena + "\t" + addedCharges + ":" + role.shots() + "\t" + anchor.id()); }
			if (finishInput) { plan.add("PRE_ENROLLMENT_INPUT_REVISION: original nearest Gludin farm had no level19 target/route; retain failed receipt; reuse exact profile35/seed/EXP/SP/items without new grants; only declared initial field/baseline is completed before any ordinary episode."); }
			Files.write(planPath, plan, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			final var goals = new PhantomGoalStateStore(profiles);
			final List<String> completed = new ArrayList<>(List.of("profileId\tobjectId\tname\tclassId\tlevel\tx\ty\tz\texp\tsp\tadena\tshots\tgoal"));
			for (int index = 0; index < roles.size(); index++)
			{
				final var role = roles.get(index); final long seed = seeds.get(index);
				var born = finishInput ? store.reload(35) : store.createShell(generation, ordinal + index, seed, "evening");
				PhantomAssertions.assertEquals(nextProfile + index, born.profile().profileId(), "Another writer changed the frozen identity sequence.");
				PhantomAssertions.assertEquals(role.classId(), born.state().classId(), "Canonical declared class differs.");
				if (!finishInput) { born = store.updateState(born, born.state().withName(0, role.name())); }
				else { PhantomAssertions.assertEquals(seed, born.state().deterministicSeed(), "Retained canonical seed differs."); PhantomAssertions.assertEquals(ordinal, born.state().creationOrdinal(), "Retained ordinal differs."); }
				for (int stage = 0; stage < 12 && born.state().state() != PhantomPopulationState.State.READY; stage++) { store.advanceCreation(born); born = store.reload(born.profile().profileId()); }
				PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, born.state().state(), "New canonical birth did not finish; retain receipts.");
				final long id = born.profile().profileId(); final int objectId = born.state().actualCharacterObjectId();
				try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(objectId))
				{
					final Player body = Player.load(objectId);
					PhantomAssertions.assertTrue(body != null && !body.isGM() && body.getActiveClass() == role.classId() && body.getClassIndex() == 0, "New native identity/class differs.");
					try
					{
						if (!finishInput)
						{
						body.getStat().setExp(body.getStat().getExpForLevel(role.classId() == 0 ? role.level() + 1 : role.level()) - (role.classId() == 0 ? 1 : 0)); body.getStat().setLevel((byte) role.level());
						body.setLearningClass(body.getPlayerClass()); body.setSp(0); body.rewardSkills();
						for (int itemId : new int[] {role.weapon(), 24, 31}) { body.getInventory().equipItem(body.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, PhantomCareer061PreviewSetup.class)); }
						body.getInventory().addItem(ItemProcessType.REWARD, role.shots(), addedCharges, null, PhantomCareer061PreviewSetup.class); body.getInventory().addItem(ItemProcessType.REWARD, 57, addedAdena, null, PhantomCareer061PreviewSetup.class);
						}
						else
						{
							PhantomAssertions.assertTrue(body.getName().equals("T061Career") && body.getLevel() == 19 && body.getExp() == body.getStat().getExpForLevel(20) - 1 && body.getSp() == 0 && body.getAdena() == 50_000
								&& body.getInventory().getAllItemsByItemId(1835, false).stream().mapToLong(item -> item.getCount()).sum() == 3000, "Unenrolled prepared input changed; no resource replay is allowed.");
						}
						if (career)
						{
							PhantomAssertions.assertTrue(body.getQuestState("Q00401_PathOfTheWarrior") == null && body.getInventory().getItems().stream().noneMatch(item -> item.getId() >= 1138 && item.getId() <= 1145), "Career must begin without a quest stage/mark/item.");
						}
						body.setRunning(); body.standUp(); body.setInvul(false); body.refreshExpertisePenalty(); body.refreshOverloaded();
						body.setXYZInvisible(anchor.point().x(), anchor.point().y(), geo.getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z())); body.revalidateZone(true);
						PhantomAssertions.assertFalse(body.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || body.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.WATER), "Declared field is not legal dry combat input.");
						final var initial = planner.planInitial(id, body, seed, 0);
						PhantomAssertions.assertTrue(initial.ready(), "Initial native career input unavailable: " + initial.reasonKey());
						final var observed = authority.captureOwnedNative(id, body, initial.goal(), null).state();
						final var local = planner.replanVisibleLocal(id, observed, initial.goal(), seed, 1, new PhantomTopologyPoint(body.getX(), body.getY(), body.getZ(), 0), Set.of(), Set.of());
						PhantomAssertions.assertTrue(local.ready(), "Local native farm unavailable: " + local.reasonKey());
						body.setCurrentHp(body.getMaxHp()); body.setCurrentMp(body.getMaxMp()); body.setCurrentCp(body.getMaxCp()); body.storeMe(); goals.insert(id, local.goal());
						final var captured = authority.captureOwnedNative(id, body, local.goal(), null);
						PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, new PhantomBackgroundTransaction().captureBaseline(captured.state(), local.goal(), captured.context()).status(), "New native baseline not committed.");
						final long minute = System.currentTimeMillis() / 60_000;
						new PhantomBackgroundCatchupStore(profiles, goals).claim(id, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "preview061-" + id, seed, minute - 1, minute, minute, 1, 0, 1,
							local.generation().knowledgeGeneration(), local.generation().topologyGeneration(), local.goal().goalId(), local.goal().revision(), local.planIdentity(), 1, authority.hashes(), ""));
						final var assigned = ecology.assign(PhantomPopulationEcologyState.Preset.LIVING, generation, ordinal + index, seed, minute, -1, 0, "evening");
						new PhantomPopulationEcologyStore(profiles).insert(id, new PhantomPopulationEcologyState(assigned.catalogHash(), assigned.preset(), assigned.ecologyGeneration(), assigned.assignmentOrdinal(), minute, minute, minute, minute,
							assigned.pace(), assigned.productiveShareBasisPoints(), assigned.productiveBlockMinutes(), assigned.personality(), assigned.initialSocialTraits(), assigned.scheduleTemplate(), assigned.disposition(), assigned.turnoverEligibleEpochMinute(), 0, "", 0, 0, 0, ""));
						completed.add(id + "\t" + objectId + "\t" + body.getName() + "\t" + body.getActiveClass() + "\t" + body.getLevel() + "\t" + body.getX() + "\t" + body.getY() + "\t" + body.getZ() + "\t" + body.getExp() + "\t" + body.getSp() + "\t" + body.getAdena() + "\t" + body.getInventory().getAllItemsByItemId(role.shots(), false).stream().mapToLong(item -> item.getCount()).sum() + "\t" + local.goal());
					}
					finally { body.stopAllTasks(); body.storeMe(); body.deleteMe(); }
				}
			}
			final String retainedAfter = retainedDigest(retainedProfiles);
			PhantomAssertions.assertEquals(retainedBefore, retainedAfter, "Retained character/skills/items/profiles/components changed; preserve evidence, no reset.");
			Files.writeString(output.resolve(phase + "-retained-preserved061.txt"), retainedBefore + "\n" + retainedAfter, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			Files.write(output.resolve(phase + "-input061.complete.tsv"), completed, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK061_" + phase + "_PREPARED_NEW_CANONICAL_PROFILES=" + roles.size() + "; retained=" + retainedProfiles + "; no test controller after boot");
		}
		finally { if (knowledge != null) { knowledge.beginStop(); knowledge.finishStop(); } ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	private static boolean goalsAbsent(PhantomProfileRepository profiles, long profileId)
	{
		return new PhantomGoalStateStore(profiles).load(profileId).isEmpty() && profiles.findComponent(profileId, PhantomPopulationEcologyState.COMPONENT_TYPE).isEmpty() && profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).isEmpty();
	}

	static String retainedDigest(int retainedProfiles) throws Exception
	{
		PhantomAssertions.assertTrue(retainedProfiles == 32 || retainedProfiles == 34, "Digest scope must remain explicitly bounded.");
		final var digest = java.security.MessageDigest.getInstance("SHA-256");
		final List<String> queries = List.of(
			"SELECT c.* FROM characters c JOIN phantom_profiles p ON p.character_object_id=c.charId WHERE p.profile_id<=32 ORDER BY c.charId",
			"SELECT s.* FROM character_skills s JOIN phantom_profiles p ON p.character_object_id=s.charId WHERE p.profile_id<=32 ORDER BY s.charId,s.class_index,s.skill_id",
			"SELECT i.* FROM items i JOIN phantom_profiles p ON p.character_object_id=i.owner_id WHERE p.profile_id<=32 ORDER BY i.object_id",
			"SELECT * FROM phantom_profiles WHERE profile_id<=32 ORDER BY profile_id",
			"SELECT profile_id,component_type,component_schema_version,row_version,HEX(payload),created_at,updated_at FROM phantom_profile_components WHERE profile_id<=32 ORDER BY profile_id,component_type");
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true);
			for (String original : queries)
			{
				final String query = original.replace("<=32", "<=" + retainedProfiles);
				try (var statement = connection.createStatement(); var rows = statement.executeQuery(query))
				{
					while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = String.valueOf(rows.getObject(column)); digest.update((value.length() + ":" + value + "|").getBytes(StandardCharsets.UTF_8)); } digest.update((byte) '\n'); }
				}
			}
		}
		return java.util.HexFormat.of().formatHex(digest.digest());
	}
}
