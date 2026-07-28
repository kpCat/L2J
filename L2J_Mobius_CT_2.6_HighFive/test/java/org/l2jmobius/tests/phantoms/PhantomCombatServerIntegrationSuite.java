/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomCombatServerIntegrationSuite implements PhantomTestSuite
{
	private static final int MELEE_CLASS_ID = 88;
	private static final int MAGIC_CLASS_ID = 94;
	private static final int MAGIC_SKILL_ID = 1339;
	private static final int WEAPON_ITEM_ID = 6;
	private static final int SOULSHOT_ITEM_ID = 1835;
	private static final int ADENA_ITEM_ID = 57;
	private static final long WAIT_MILLIS = 5000;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Monster> _worldFixtures = new ArrayList<>();
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private PhantomMaterializationService _materialization;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomGameKnowledgeQuery _query;
	private PhantomCombatService _combat;
	private L2jCombatBackend _backend;
	private Player _player;
	private Player _observer;
	private SpawnFact _combatPoint;
	private Path _moduleRoot;

	@Override
	public String id()
	{
		return "combat-server-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_moduleRoot = context.moduleRoot();
		_environment.initialize(context);
		try
		{
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();

			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final PhantomTopologySnapshot topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final PhantomGameKnowledgeBuilder builder = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), knowledgePolicy), topologyQuery, knowledgePolicy);
			_knowledge = new PhantomGameKnowledgeService(builder);
			PhantomAssertions.assertTrue(_knowledge.start(), "Game Knowledge service did not start.");
			_query = _knowledge.query();
			_combatPoint = selectCombatPoint();

			_repository = PhantomProfileRepository.open();
			_profile = _repository.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(_materialization.start(), "Materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Test actor did not materialize.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(_player != null, "Materialized World Player is absent.");
			relocateToCombatPoint();

			_backend = new L2jCombatBackend(_materialization, () -> _query);
			_combat = new PhantomCombatService(_backend, PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query), PhantomCombatPolicy.productionDefaults(1));
			_combat.start();

			context.record("combatIntegration.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
			context.record("combatIntegration.profileId", _profile.profileId());
			context.record("combatIntegration.actorObjectId", _player.getObjectId());
			context.record("combatIntegration.normalNpcId", _combatPoint.npcId());
		}
		catch (Throwable throwable)
		{
			cleanup();
			throw throwable;
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-exact-world-player-action-lease", _ -> testExactActorLease());
		registry.add("02-canonical-player-ai-attack-and-death", _ -> testCanonicalAttack());
		registry.add("03-canonical-selected-skill-cast", _ -> testCanonicalCast());
		registry.add("04-canonical-shot-conservation-and-discharge", _ -> testCanonicalShot());
		registry.add("05-missing-shot-does-not-fabricate", _ -> testMissingShot());
		registry.add("06-canonical-ground-item-pickup", _ -> testCanonicalLoot());
		registry.add("07-player-raid-grandboss-rejected", _ -> testForbiddenTargets());
		registry.add("08-cancel-only-owned-action", _ -> testOwnedCancellation());
		registry.add("09-other-player-pickup-is-not-acquisition", _ -> testOtherPlayerPickupEvidence());
		registry.add("10-despawn-is-not-acquisition", _ -> testDespawnEvidence());
		registry.add("11-range-loss-is-not-acquisition", _ -> testRangeLossEvidence());
		registry.add("12-cancel-during-exact-pickup", _ -> testPickupCleanup(false));
		registry.add("13-stop-during-exact-pickup", _ -> testPickupCleanup(true));
		registry.add("14-foreign-cast-and-pickup-survive", _ -> testForeignCastAndPickup());
		registry.add("15-positive-one-target-skill-rejected", _ -> testPositiveSkillRejected());
		registry.add("16-player-death-releases-ownership", _ -> testPlayerDeath());
		registry.add("17-restricted-normal-town-respawn", _ -> testNormalTownRespawn());
		registry.add("18-production-combat-has-no-packet-route", _ -> testNoPacketRoute());
		registry.add("19-dematerialization-waits-for-combat-lease", _ -> testDematerializationDrain());
	}

	private void testExactActorLease()
	{
		try (ActionLease lease = _materialization.tryAcquireAction(_profile.profileId()).orElseThrow())
		{
			PhantomAssertions.assertEquals(_player, lease.player(), "Materialization lease did not retain the exact actor.");
			PhantomAssertions.assertEquals(_player, World.getInstance().getPlayer(_player.getObjectId()), "Materialization actor is not the exact World Player.");
		}
	}

	private void testCanonicalAttack() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Normal monster combat was not accepted.");
		await(() -> target.isDead() || target.isAlikeDead(), "Canonical PlayerAI attack did not kill the deterministic target.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Canonical target death did not produce victory.");
		PhantomAssertions.assertTrue(_player.getTarget() == null, "Victory cleanup retained the exact dead combat target.");
		consumeTerminal();
	}

	private void testCanonicalCast() throws Exception
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Deterministic offensive skill is unavailable.");
		_player.addSkill(skill, false);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final double initialHp = target.getCurrentHp();
		final double initialMp = _player.getCurrentMp();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID, 1), PhantomCombatMode.RANGED_MAGIC), "Real hostile one-target skill was rejected.");
		}
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.RANGED_MAGIC, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Supported magic loadout was not accepted.");
		await(() -> _player.isCastingNow() || (_player.getCurrentSkill() != null) || (target.getCurrentHp() < initialHp) || (_player.getCurrentMp() < initialMp), "Canonical CAST produced no observable cast state or effect.");
		PhantomAssertions.assertTrue((_player.getCurrentMp() <= initialMp) && (target.getCurrentHp() <= initialHp), "Canonical cast fabricated HP or MP.");
		_combat.cancel(_profile.profileId());
		consumeTerminal();
	}

	private void testCanonicalShot() throws Exception
	{
		resetActor(true);
		final Item weapon = ensureWeapon();
		final Item shots = _player.getInventory().addItem(ItemProcessType.REWARD, SOULSHOT_ITEM_ID, 5, _player, this);
		PhantomAssertions.assertTrue(shots != null, "Could not create test-owned soulshot fixture.");
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.ACTIVATED, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Canonical soulshot activation failed.");
			PhantomAssertions.assertEquals(before - 1, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Canonical soulshot activation consumed an unexpected count.");
			PhantomAssertions.assertTrue(_player.isChargedShot(ShotType.SOULSHOTS), "Canonical soulshot handler did not charge the weapon.");
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.attack(target.getObjectId()), "Canonical shot-backed attack was not issued.");
			await(() -> !_player.isChargedShot(ShotType.SOULSHOTS), "Canonical attack did not discharge the soulshot.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
		}
		destroyInventoryItem(shots);
		PhantomAssertions.assertTrue(weapon.isEquipped(), "Shot test unexpectedly unequipped its weapon.");
	}

	private void testMissingShot()
	{
		resetActor(true);
		ensureWeapon();
		final Item lingeringShots = _player.getInventory().getItemByItemId(SOULSHOT_ITEM_ID);
		if (lingeringShots != null)
		{
			destroyInventoryItem(lingeringShots);
		}
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.UNAVAILABLE, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Missing shot was not reported as unavailable.");
		}
		PhantomAssertions.assertEquals(before, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Missing shot path fabricated inventory.");
		PhantomAssertions.assertFalse(_player.isChargedShot(ShotType.SOULSHOTS), "Missing shot path fabricated charge.");
		destroyInventoryItem(_player.getInventory().getItemByItemId(WEAPON_ITEM_ID));
	}

	private void testCanonicalLoot() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final long before = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, true));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Loot combat was not accepted.");
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill the test-owned loot target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Canonical monster drop did not create a ground item.");
		try
		{
			await(() -> World.getInstance().findObject(dropped.getObjectId()) == null, "Canonical PlayerAI pickup did not remove the ground item.");
			final PhantomCombatSessionSnapshot terminal = awaitTerminal();
			PhantomAssertions.assertTrue((terminal.result() == PhantomCombatResult.VICTORY_LOOTED) || (terminal.result() == PhantomCombatResult.VICTORY_LOOT_PARTIAL), "Canonical loot produced unexpected result " + terminal.result() + ".");
			PhantomAssertions.assertEquals(before + 1, _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Ground item and inventory conservation failed.");
		}
		finally
		{
			consumeTerminal();
			final long added = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - before;
			if (added > 0)
			{
				final Item adena = _player.getInventory().getItemByItemId(ADENA_ITEM_ID);
				PhantomAssertions.assertTrue(_player.getInventory().destroyItem(ItemProcessType.DESTROY, adena, added, _player, this) != null, "Could not restore fixture adena baseline.");
			}
		}
	}

	private void testForbiddenTargets()
	{
		resetActor(true);
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned Player target.");
		_observer.setXYZInvisible(_player.getX() + 20, _player.getY(), _player.getZ());
		_observer.spawnMe();
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(_observer.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "Player target was accepted.");

		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		final RaidBoss raid = spawn(new RaidBoss(template), 30);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(raid.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "RaidBoss target was accepted.");
		final GrandBoss grand = spawn(new GrandBoss(template), 40);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(grand.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "GrandBoss target was accepted.");

		final Monster observed = spawnNormalMonster(targetMaximumHp());
		observed.setTarget(_player);
		_player.getAttackByList().clear();
		_player.getAttackByList().add(observed);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.observedAttackers(16).stream().anyMatch(entry -> entry.targetObjectId() == observed.getObjectId()), "Valid normal-monster threat was not observed.");
			observed.setInvul(true);
			PhantomAssertions.assertTrue(lease.observedAttackers(16).isEmpty(), "Forbidden invulnerable threat entered the table.");
		}
		finally
		{
			observed.setInvul(false);
			_player.getAttackByList().clear();
		}
	}

	private void testOwnedCancellation() throws Exception
	{
		resetActor(true);
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		final Monster foreign = spawnNormalMonster(targetMaximumHp());
		foreign.setXYZ(_player.getX() + 60, _player.getY(), _player.getZ());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(owned, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Owned-action combat did not start.");
		await(() -> _player.hasAI() && (_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() == owned), "Combat did not establish the owned attack.");
		_player.setTarget(foreign);
		_player.getAI().setIntention(Intention.ATTACK, foreign);
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Combat cancellation was not accepted.");
		PhantomAssertions.assertEquals(Intention.ATTACK, _player.getAI().getIntention(), "Combat cancellation stopped a foreign action.");
		PhantomAssertions.assertEquals(foreign, _player.getAI().getAttackTarget(), "Combat cancellation replaced a foreign target.");
		consumeTerminal();
	}

	private void testPlayerDeath() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Death-observation combat did not start.");
		PhantomAssertions.assertTrue(_player.doDie(target), "Could not kill the test-owned actor.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.PLAYER_DEAD, terminal.result(), "Actor death did not produce PLAYER_DEAD.");
		await(() -> (_combat.snapshot().actorLeases() == 0) && (_combat.snapshot().currentWorkers() == 0), "Player death retained combat lease or worker ownership.");
	}

	private void testNormalTownRespawn() throws Exception
	{
		PhantomAssertions.assertTrue(_player.isDead(), "Respawn case did not inherit the canonical dead actor.");
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, _combat.respawnTown(new PhantomRespawnRequest(_profile.profileId(), () -> false)), "Restricted normal-town respawn was not accepted.");
		await(() -> !_player.isDead() && !_player.isPendingRevive(), "Canonical normal-town teleport did not revive the actor.");
		PhantomAssertions.assertEquals(0, _player.getInstanceId(), "Normal-town respawn retained an instance.");
		PhantomAssertions.assertTrue(_player.isSpawned() && !_player.isTeleporting(), "Headless normal-town respawn did not complete canonical teleport lifecycle.");
		consumeTerminal();
	}

	private void testDematerializationDrain() throws Exception
	{
		resetActor(true);
		relocateToCombatPoint();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Lease-drain combat did not start.");
		final AtomicReference<PhantomMaterializationService.DematerializeResult> result = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread dematerialize = new Thread(() ->
		{
			try
			{
				result.set(_materialization.dematerialize(_profile.profileId()));
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		}, "Task012-dematerialization-drain");
		dematerialize.start();
		Thread.sleep(200);
		PhantomAssertions.assertTrue(dematerialize.isAlive(), "Dematerialization passed an active combat ActionLease.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Could not cancel combat for materialization drain.");
		dematerialize.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(dematerialize.isAlive(), "Dematerialization did not complete after combat lease release.");
		if (failure.get() != null)
		{
			throw new AssertionError("Dematerialization failed.", failure.get());
		}
		PhantomAssertions.assertTrue(result.get() != null, "Dematerialization returned no result.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), "Dematerialization failed after combat cancellation.");
		consumeTerminal();
	}

	private void testNoPacketRoute() throws Exception
	{
		final Path combatRoot = _moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/combat");
		final String source;
		try (var paths = Files.walk(combatRoot))
		{
			source = paths.filter(path -> path.toString().endsWith(".java")).sorted().map(path ->
			{
				try
				{
					return Files.readString(path, StandardCharsets.UTF_8);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
			}).reduce("", String::concat);
		}
		PhantomAssertions.assertFalse(source.contains("network.clientpackets"), "Combat production code imports a client packet.");
		PhantomAssertions.assertFalse(source.contains("network.serverpackets"), "Combat production code imports a server packet.");
		PhantomAssertions.assertFalse(source.contains("sendPacket("), "Combat production code sends a packet directly.");
		PhantomAssertions.assertFalse(source.contains("RequestRestartPoint"), "Combat production code simulates restart packet handling.");
	}

	private void testOtherPlayerPickupEvidence()
	{
		resetActor(true);
		final Player observer = ensureObserver();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill other-player pickup fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create other-player ground item.");
		final long observerBefore = observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.getDropProtection().unprotect();
			dropped.setOwnerId(0);
			observer.doPickupItem(dropped);
			PhantomAssertions.assertEquals(observerBefore + 1, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Other test player did not acquire the item.");
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Other-player pickup was attributed to the phantom.");
		}
		finally
		{
			destroyInventoryCount(observer, ADENA_ITEM_ID, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - observerBefore);
			destroyGroundItem(dropped);
		}
	}

	private void testDespawnEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill despawn fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create despawn ground item.");
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.decayMe();
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Despawn was attributed to the phantom.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testRangeLossEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill range-loss fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create range-loss ground item.");
		final int x = _player.getX();
		final int y = _player.getY();
		final int z = _player.getZ();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			_player.setXYZ(x + 1000, y, z);
			PhantomAssertions.assertEquals(LootObservation.INELIGIBLE, lease.observeLoot(candidate), "Out-of-radius item was attributed to the phantom.");
		}
		finally
		{
			_player.setXYZ(x, y, z);
			destroyGroundItem(dropped);
		}
	}

	private void testPickupCleanup(boolean stop)
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill exact PICK_UP target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create exact PICK_UP item.");
		dropped.setXYZ(_player.getX() + 200, _player.getY(), _player.getZ());
		try
		{
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				exactCandidate(lease, dropped);
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(dropped.getObjectId()), "Canonical PICK_UP was not issued.");
				PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Canonical PICK_UP intention was not established.");
				PhantomAssertions.assertEquals(dropped, _player.getTarget(), "Canonical PICK_UP did not own the exact item.");
				lease.cancelOwnedAction(new PhantomOwnedAction(stop ? 2 : 1, target.getObjectId(), null, dropped.getObjectId()));
			}
			PhantomAssertions.assertEquals(Intention.IDLE, _player.getAI().getIntention(), "Exact PICK_UP intention survived cleanup.");
			PhantomAssertions.assertTrue(_player.getTarget() == null, "Exact PICK_UP target survived cleanup.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testForeignCastAndPickup()
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Foreign CAST skill is unavailable.");
		_player.addSkill(skill, false);
		_player.enableSkill(skill);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill staleSelected = new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID + 1, 1);
			_player.abortCast();
			_player.setTarget(owned);
			_player.getAI().setIntention(Intention.CAST, skill, owned);
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Foreign CAST intention was not established.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, owned.getObjectId(), staleSelected, 0));
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Stale cleanup stopped a foreign CAST.");
			PhantomAssertions.assertEquals(owned, _player.getAI().getCastTarget(), "Stale cleanup replaced a foreign CAST target.");
			PhantomAssertions.assertEquals(owned, _player.getTarget(), "Stale cleanup cleared the selected target of a foreign CAST.");
		}
		finally
		{
			_player.abortCast();
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
		}

		final Monster dropTarget = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(dropTarget.doDie(_player), "Could not kill foreign PICK_UP fixture.");
		final Item ownedItem = dropTarget.dropItem(_player, ADENA_ITEM_ID, 1);
		final Item foreignItem = ItemManager.createItem(ItemProcessType.LOOT, ADENA_ITEM_ID, 1, _player, this);
		PhantomAssertions.assertTrue((ownedItem != null) && (foreignItem != null), "Could not create foreign PICK_UP items.");
		foreignItem.dropMe(_player, _player.getX() + 100, _player.getY(), _player.getZ());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(ownedItem.getObjectId()), "Owned PICK_UP was not issued.");
			_player.setTarget(foreignItem);
			_player.getAI().setIntention(Intention.PICK_UP, foreignItem);
			lease.cancelOwnedAction(new PhantomOwnedAction(2, dropTarget.getObjectId(), null, ownedItem.getObjectId()));
			PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Stale cleanup stopped a foreign PICK_UP.");
			PhantomAssertions.assertEquals(foreignItem, _player.getTarget(), "Stale cleanup replaced a foreign PICK_UP target.");
		}
		finally
		{
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
			destroyGroundItem(ownedItem);
			destroyGroundItem(foreignItem);
		}
	}

	private void testPositiveSkillRejected()
	{
		resetActor(true);
		final Skill positive = SkillData.getInstance().getSkill(1040, 1);
		PhantomAssertions.assertTrue(positive != null, "Known positive one-target skill is unavailable.");
		_player.addSkill(positive, false);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertFalse(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(1040, 1), PhantomCombatMode.RANGED_MAGIC), "Known positive one-target skill was accepted.");
		}
	}

	private PhantomCombatRequest request(Monster target, PhantomCombatMode mode, boolean shots, boolean loot)
	{
		return new PhantomCombatRequest(_profile.profileId(), target.getObjectId(), mode, shots, loot, 30_000, () -> false);
	}

	private PhantomCombatRequest request(int targetObjectId, PhantomCombatMode mode)
	{
		return new PhantomCombatRequest(_profile.profileId(), targetObjectId, mode, false, false, 30_000, () -> false);
	}

	private PhantomCombatSessionSnapshot awaitTerminal() throws Exception
	{
		await(() -> _combat.find(_profile.profileId()).map(snapshot -> snapshot.result().terminal()).orElse(false), "Combat session did not become terminal.");
		return _combat.find(_profile.profileId()).orElseThrow();
	}

	private void consumeTerminal()
	{
		_combat.consumeTerminal(_profile.profileId());
	}

	private Player ensureObserver()
	{
		if (_observer == null)
		{
			_observer = Player.load(_environment.observer().objectId());
			PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned observer.");
			_observer.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
			_observer.spawnMe();
		}
		return _observer;
	}

	private static LootCandidate exactCandidate(PhantomCombatActorLease lease, Item item)
	{
		return lease.lootCandidates(32, 300).stream().filter(candidate -> candidate.worldObjectId() == item.getObjectId()).findFirst().orElseThrow(() -> new AssertionError("Exact real ground item was not observed."));
	}

	private static void destroyInventoryCount(Player owner, int itemId, long count)
	{
		if (count <= 0)
		{
			return;
		}
		final Item item = owner.getInventory().getItemByItemId(itemId);
		PhantomAssertions.assertTrue((item != null) && (owner.getInventory().destroyItem(ItemProcessType.DESTROY, item, count, owner, PhantomCombatServerIntegrationSuite.class) != null), "Could not restore test-owned inventory baseline.");
	}

	private static void destroyGroundItem(Item item)
	{
		if ((item != null) && (World.getInstance().findObject(item.getObjectId()) != null))
		{
			item.resetOwnerTimer();
			if (item.isSpawned())
			{
				item.decayMe();
			}
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomCombatServerIntegrationSuite.class);
		}
	}

	private void resetActor(boolean revive)
	{
		if (_combat != null)
		{
			_combat.cancel(_profile.profileId());
			consumeTerminal();
		}
		if (_player == null)
		{
			return;
		}
		if (revive && _player.isDead())
		{
			_player.doRevive();
		}
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		_player.abortAttack();
		_player.abortCast();
		_player.setTarget(null);
		_player.getAI().setIntention(Intention.IDLE);
		_player.setPlayerClass(MELEE_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
	}

	private Item ensureWeapon()
	{
		Item weapon = _player.getInventory().getItemByItemId(WEAPON_ITEM_ID);
		if (weapon == null)
		{
			weapon = _player.getInventory().addItem(ItemProcessType.REWARD, WEAPON_ITEM_ID, 1, _player, this);
		}
		PhantomAssertions.assertTrue(weapon != null, "Could not create the test-owned weapon.");
		if (!weapon.isEquipped())
		{
			_player.getInventory().equipItem(weapon);
		}
		return weapon;
	}

	private void destroyInventoryItem(Item item)
	{
		if (item != null)
		{
			_player.getInventory().destroyItem(ItemProcessType.DESTROY, item, _player, this);
		}
	}

	private Monster spawnNormalMonster(double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		final Monster monster = spawn(new Monster(template), 20 + (_worldFixtures.size() * 5));
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		return monster;
	}

	private <T extends Monster> T spawn(T monster, int xOffset)
	{
		monster.setInstanceId(0);
		monster.spawnMe(_player.getX() + xOffset, _player.getY(), _player.getZ());
		_worldFixtures.add(monster);
		return monster;
	}

	private double targetMaximumHp()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		return Math.max(1, template.getBaseHpMax());
	}

	private SpawnFact selectCombatPoint()
	{
		final List<NpcFact> candidates = _knowledge.query().snapshot().npcById().values().stream().filter(fact -> (fact.kind() == NpcKind.MONSTER) && fact.attackable() && fact.targetable()).sorted(Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId)).toList();
		for (NpcFact candidate : candidates)
		{
			final Optional<SpawnFact> point = _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(candidate.npcId(), List.of()).stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)).findFirst();
			if (point.isPresent())
			{
				return point.orElseThrow();
			}
		}
		throw new AssertionError("No deterministic normal-monster spawn fact is available.");
	}

	private void relocateToCombatPoint()
	{
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(_combatPoint.x(), _combatPoint.y(), _combatPoint.z());
		_player.spawnMe();
		_player.revalidateZone(true);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private void cleanup() throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_combat != null)
			{
				_combat.beginStop();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (!_combat.finishStop() && (System.nanoTime() < deadline))
				{
					Thread.sleep(10);
				}
				PhantomAssertions.assertTrue(_combat.finishStop(), "Combat service did not stop cleanly.");
			}
			for (Monster fixture : List.copyOf(_worldFixtures))
			{
				if (fixture.isSpawned())
				{
					fixture.deleteMe();
				}
			}
			_worldFixtures.clear();
			if (_observer != null)
			{
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, _materialization.shutdown().state(), "Materialization service did not stop.");
			}
			if ((_repository != null) && (_profile != null))
			{
				_repository.find(_profile.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Game Knowledge service did not stop.");
			}
			if (_player != null)
			{
				_environment.assertClean(_environment.primary(), _player);
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

}
