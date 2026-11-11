/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Fresh native timing proof: stock calm Major Heal versus a faster lawful Heal under actual NPC threat. */
public final class PhantomLearning003TemporalNative
{
	private static final String ANCHOR = "generated.farm.72007cc387f17e963e4fcf2c.anchor";
	public static void run(Path root, Path attempt, TrainingConfig config, LearningPolicy legacy) throws Exception
	{
		PhantomTrainingActors.physicalRoot(root); PhantomTrainingActors.settings(root);
		check(attempt.toRealPath().equals(attempt) && attempt.getParent().equals(root.resolve("attempts")), "TEMPORAL_OWN_ATTEMPT");
		check(!Files.exists(attempt.resolve("temporal.complete")) && !Files.exists(attempt.resolve("temporal.failed")), "TEMPORAL_FRESH_ATTEMPT");
		Files.writeString(attempt.resolve("temporal.started"), "freshActors=true\npostCutAssignments=false\n", StandardOpenOption.CREATE_NEW);
		try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
			var runtime = new PhantomLearning001ShadowNative.Runtime(source, root.resolve("game"), legacy, attempt.resolve("stop.request"), false, 5))
		{
			for (boolean urgent : new boolean[] {false, true})
			{
				check(!requested(attempt), "TEMPORAL_STOP_REQUESTED");
				final Monster threat = urgent ? nativeThreat(source) : null;
				final Location point = urgent ? new Location(threat.getX(), threat.getY(), threat.getZ()) : new Location(82480, 149087, -3350);
				final var roster = prepare(source, point, attempt, config, urgent);
				prove(runtime, roster, threat, attempt, urgent);
			}
			Files.writeString(attempt.resolve("temporal.complete"), "calm=EXACT_NATIVE_HEAL\nurgent=EXACT_NATIVE_HEAL\nlethalHorizon=UNKNOWN\n", StandardOpenOption.CREATE_NEW);
			System.out.println("V3_TEMPORAL_NATIVE_PASS scenes=2 actualThreat=true trainedModelClaim=false lethalHorizon=UNKNOWN");
		}
		catch (Exception failure)
		{
			Files.writeString(attempt.resolve("temporal.failed"), failure.getClass().getSimpleName() + "\n", StandardOpenOption.CREATE_NEW);
			throw failure;
		}
	}
	private static List<String[]> prepare(PhantomBackgroundSuite.ProductionAuthorityFixture source, Location point, Path attempt, TrainingConfig config, boolean urgent) throws Exception
	{
		final var profiles = PhantomProfileRepository.open(); int next;
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement(); var rows = sql.executeQuery(
			"SELECT COALESCE(MAX(profile_id),0)+1,(SELECT COUNT(*) FROM characters WHERE account_name IS NULL OR account_name<>'learn001fixture'),(SELECT COUNT(*) FROM characters) FROM phantom_profiles"))
		{
			check(rows.next() && rows.getLong(2) == 0 && rows.getLong(3) + 3 <= config.integer("maxStoredActors", 4096, 32, 32768)
				&& rows.getLong(1) + 2 <= config.integer("maxStoredProfiles", 4096, 64, 32768), "TEMPORAL_ACTOR_CAPACITY");
			next = Math.toIntExact(rows.getLong(1));
		}
		final var resolver = PhantomCombatCapabilityResolver.fromProgression(source::progression);
		final var rows = new ArrayList<String[]>(); final var goals = new PhantomGoalStateStore(profiles);
		final Path manifest = attempt.resolve("temporal-" + (urgent ? "urgent" : "calm") + "-actors.tsv");
		Files.writeString(manifest, "split\trole\tprofile\tactor\tclass\tlevel\tweapon\tskills\tx\ty\tz\n", StandardOpenOption.CREATE_NEW);
		for (int actor = 0; actor < 3; actor++)
		{
			final String role = actor == 0 ? "HEAL_HELPER" : actor == 1 ? "HELPER" : "SOLO";
			final int type = actor == 0 ? 97 : actor == 1 ? 90 : 94;
			final String row = PhantomLearning001ActorFixture.createTraining(profiles, resolver, next++, "TEMPORAL", role,
				new Location(point.getX() + actor * 35, point.getY(), point.getZ()), 77, CrystalType.S, type, Set.of(1230, 1339), 4,
				"T3" + Long.toUnsignedString(System.nanoTime(), 36) + actor);
			Files.writeString(manifest, row + "\n", StandardOpenOption.APPEND); rows.add(row.split("\t", -1));
		}
		for (int actor = 0; actor < rows.size(); actor++)
		{
			final long profile = Long.parseLong(rows.get(actor)[2]);
			goals.insert(profile, PhantomLearning003Native.partyGoal(profile, profile * 10,
				actor == 0 ? PhantomPartyCoordinator.FORM_GOAL : PhantomPartyCoordinator.JOIN_GOAL,
				actor == 0 ? null : new PhantomDomainRef("character.object", rows.getFirst()[3])));
		}
		return List.copyOf(rows);
	}
	private static void prove(PhantomLearning001ShadowNative.Runtime runtime, List<String[]> roster, Monster threat, Path attempt, boolean urgent) throws Exception
	{
		final String scene = urgent ? "urgent" : "calm";
		final var players = runtime.prepareHeal(roster); final Player healer = players.getFirst(), tank = players.get(1);
		final long healerProfile = Long.parseLong(roster.getFirst()[2]), tankProfile = Long.parseLong(roster.get(1)[2]);
		final var cancelThreat = new AtomicBoolean(); ExternalActionLease healLease = null;
		try
		{
			// Declared TEST input before CUT. No HP/MP assignments occur after this point.
			tank.setCurrentHp(tank.getMaxHp() * .45);
			check(healer.getCurrentHp() > healer.getMaxHp() * .55 && players.get(2).getCurrentHp() > players.get(2).getMaxHp() * .55, "TEMPORAL_OTHER_ALLIES_HEALTHY");
			if (urgent)
			{
				check(runtime.startTemporalThreat(tankProfile, threat.getObjectId(), cancelThreat::get), "TEMPORAL_NATIVE_TANK_SESSION");
				await(() -> runtime.temporalMemberSnapshot(tankProfile).attackerObjectIds().contains(threat.getObjectId()), 45, attempt, "TEMPORAL_REAL_ATTACKER");
			}
			final var targetSnapshot = runtime.temporalMemberSnapshot(tankProfile);
			check(!targetSnapshot.dead() && targetSnapshot.hpPercent() <= (urgent ? 75 : 55) && (urgent == !targetSnapshot.attackerObjectIds().isEmpty()), "TEMPORAL_NATIVE_PRESSURE_FACTS");
			final var choices = runtime.inspectHeal().stream().filter(d -> d.actor().profileId() == healerProfile && d.kind() == DirectiveKind.HEAL_MEMBER).toList();
			check(choices.size() == 1 && choices.getFirst().targetObjectId() == tank.getObjectId(), "TEMPORAL_ONE_STOCK_HEAL");
			final var directive = choices.getFirst(); final var major = healer.getKnownSkill(1401); final var selectedSkill = healer.getKnownSkill(directive.actionSkillId());
			check(major != null && selectedSkill != null, "TEMPORAL_NATIVE_KNOWN_SKILLS");
			final int majorMillis = healer.calculateSkillCastTime(major), selectedMillis = healer.calculateSkillCastTime(selectedSkill);
			check(urgent ? selectedMillis < majorMillis : selectedSkill.getId() == 1401, "TEMPORAL_FAST_URGENT_OR_CALM_MAJOR");
			final var selectedTiming = TacticalTimingV3.observe(healer, selectedSkill); final var majorTiming = TacticalTimingV3.observe(healer, major);
			check(selectedTiming.kind() == TacticalTimingV3.Kind.NORMAL_STANDARD && selectedTiming.predictedEffectAfterMillis().known()
				&& majorTiming.predictedEffectAfterMillis().known(), "TEMPORAL_SUPPORTED_NATIVE_TIMING");
			final var geo = GeoEngine.getInstance();
			check(geo.hasGeo(healer.getX(), healer.getY()) && geo.hasGeo(tank.getX(), tank.getY()) && geo.canSeeTarget(healer, tank), "TEMPORAL_NATIVE_LOS");
			final var observation = TacticalSnapshotV3.collect(healer, healerProfile, urgent ? 2 : 1, 20);
			final var offered = new ArrayList<LegalActionV3>();
			for (var capability : runtime.temporalHealCapabilities(healerProfile, tank.getObjectId()))
			{
				final var action = action(healer, tank, capability, observation, urgent);
				if (action != null && !offered.contains(action)) { offered.add(action); }
			}
			check(offered.size() >= 2 && offered.size() <= 16, "TEMPORAL_REAL_ALTERNATIVES");
			final String offeredSkills = offered.stream().map(a -> a.skillId() + ":" + a.skillLevel() + "@base=" + a.castMillis()
				+ "@native=" + healer.calculateSkillCastTime(healer.getKnownSkill(a.skillId()))).toList().toString();
			final var selected = offered.stream().filter(a -> a.skillId() == selectedSkill.getId() && a.skillLevel() == selectedSkill.getLevel()).findFirst().orElseThrow();
			final var baseline = offered.stream().filter(a -> a.skillId() == 1401).findFirst().orElseThrow();
			final var receipts = new ConcurrentLinkedQueue<HealEvidenceV3.Receipt>();
			final var evidence = new HealEvidenceV3.Attempt(observation, offered, selected, baseline, 0, receipts::add);
			Files.write(attempt.resolve("temporal-" + scene + ".precut.bin"), observation.bytes(), StandardOpenOption.CREATE_NEW);
			Files.writeString(attempt.resolve("temporal-" + scene + ".cut"), "profile=" + healerProfile + "\ntarget=" + tank.getObjectId()
				+ "\nselected=" + selectedSkill.getId() + "\nknownAttackers=" + targetSnapshot.attackerObjectIds() + "\nlethalHorizon=UNKNOWN\n", StandardOpenOption.CREATE_NEW);
			healLease = HealEvidenceV3.withAttempt(evidence, () -> runtime.dispatchTemporalHeal(directive, "temporal." + scene + "." + healerProfile,
				System.nanoTime() + Duration.ofSeconds(20).toNanos())).orElseThrow(() -> new IllegalStateException("TEMPORAL_NATIVE_DISPATCH"));
			await(() -> !receipts.isEmpty() && !healer.isCastingNow(), 20, attempt, "TEMPORAL_EXACT_HEAL_RECEIPT");
			check(receipts.size() == 1, "TEMPORAL_NO_DOUBLE_CREDIT");
			final var receipt = receipts.remove(); check(receipt.attempt() == evidence && receipt.actualHp() > 0 && receipt.actualMp() > 0, "TEMPORAL_EXACT_HP_MP");
			healLease.complete(); healLease = null;
			cancelThreat.set(true); await(() -> runtime.temporalThreatDrained(tankProfile), 30, attempt, "TEMPORAL_TANK_DRAIN");
			final String line = "V3_TEMPORAL_NATIVE_HEAL scene=" + scene + " profile=" + healerProfile + " selected=" + selectedSkill.getId() + ":" + selectedSkill.getLevel()
				+ " majorBaseMillis=" + major.getHitTime() + " majorNativeMillis=" + majorMillis + " selectedBaseMillis=" + selectedSkill.getHitTime()
				+ " selectedNativeMillis=" + selectedMillis + " effectElapsedNanos=" + receipt.elapsedNanos() + " beforeHp=" + receipt.beforeHp()
				+ " selectedPredictedEffectMillis=" + selectedTiming.predictedEffectAfterMillis().value() + " majorPredictedEffectMillis=" + majorTiming.predictedEffectAfterMillis().value()
				+ " afterHp=" + receipt.afterHp() + " usefulHp=" + receipt.actualHp() + " mpDebit=" + receipt.actualMp() + " knownAttackers=" + targetSnapshot.attackerObjectIds()
				+ " offeredSkills=" + offeredSkills
				+ " eventKey=" + receipt.eventKey() + " lethalHorizon=UNKNOWN unsupportedChannelTiming=UNKNOWN modelVersion=0";
			Files.writeString(attempt.resolve("temporal-" + scene + ".receipt.txt"), line + "\n", StandardOpenOption.CREATE_NEW); System.out.println(line);
			runtime.finishHeal();
		}
		finally { cancelThreat.set(true); if (healLease != null) { healLease.close(); } }
	}
	private static LegalActionV3 action(Player healer, Player target, MemberCapability capability, TacticalObservationV3 observation, boolean urgent)
	{
		if (!"combat.heal".equals(capability.capabilityKey()) || !capability.readyNow()) { return null; }
		final var skill = healer.getKnownSkill(capability.actionSkillId());
		if (skill == null || skill.getLevel() != capability.actionSkillLevel() || skill.isChanneling() || skill.isToggle() || skill.isSimultaneousCast()
			|| skill.getFlyType() != null || skill.getAffectRange() > 0 || !PhantomSupportEffectAuthority.supportsCapability(skill, "combat.heal")
			|| healer.isSkillDisabled(skill) || !healer.checkDoCastConditions(skill) || !skill.checkCondition(healer, target, false)
			|| healer.calculateDistance3D(target) > healer.getMagicalAttackRange(skill)) { return null; }
		final double mp = healer.getStat().getMpConsume(skill) + healer.getStat().getMpInitialConsume(skill);
		if (mp <= 0 || healer.getCurrentMp() < mp) { return null; }
		return new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", observation.profileId(), healer.getObjectId(), observation.actorEpoch(), observation.decisionId(),
			target.getObjectId(), TacticalSnapshotV3.epoch(target), observation.partyIdentity(), skill.getId(), skill.getLevel(), mp,
			Math.max(0, skill.getHitTime()), Math.max(0, healer.getMagicalAttackRange(skill)), urgent, true);
	}
	private static Monster nativeThreat(PhantomBackgroundSuite.ProductionAuthorityFixture source)
	{
		final var point = source.topology().findAnchor(ANCHOR).orElseThrow().point(); final var geo = GeoEngine.getInstance();
		check(point.x() == 180488 && point.y() == -75315 && point.z() == -2728 && point.instanceId() == 0 && geo.hasGeo(point.x(), point.y()), "TEMPORAL_CANONICAL_SOURCE");
		final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
		return World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(n -> Set.of(21798, 21799).contains(n.getId()) && n.isSpawned() && !n.isAlikeDead() && n.getInstanceId() == 0 && !n.isAggressive()
				&& !n.isRaid() && !n.isChampion() && n.isTargetable() && !n.isInvul() && Math.hypot((long) n.getX() - point.x(), (long) n.getY() - point.y()) < 1600
				&& planner.partyNativeTarget(new PhantomDomainRef("background.farm", n.getId() + "@" + ANCHOR), n.getId(), new PhantomTopologyPoint(n.getX(), n.getY(), n.getZ(), 0))
				&& geo.hasGeo(n.getX(), n.getY()) && geo.canSeeTarget(point.x(), point.y(), point.z(), 0, n.getX(), n.getY(), n.getZ(), 0)
				&& geo.canMoveToTarget(point.x(), point.y(), point.z(), n.getX(), n.getY(), n.getZ(), 0))
			.sorted(Comparator.comparingDouble((Monster n) -> Math.hypot((long) n.getX() - point.x(), (long) n.getY() - point.y())).thenComparingInt(Monster::getObjectId))
			.findFirst().orElseThrow(() -> new IllegalStateException("TEMPORAL_NATIVE_THREAT_SOURCE"));
	}
	private static void await(BooleanSupplier condition, int seconds, Path attempt, String reason) throws Exception
	{
		final long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
		while (!condition.getAsBoolean()) { check(!requested(attempt) && System.nanoTime() < deadline, reason); Thread.sleep(50); }
	}
	private static boolean requested(Path attempt) { return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	private static void check(boolean value, String reason) { if (!value) { throw new IllegalStateException(reason); } }
	private PhantomLearning003TemporalNative() { }
}
