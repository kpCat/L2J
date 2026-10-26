/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** One owned continuation; only production owners issue movement, combat, rewards and store. */
public final class PhantomArrivalFarmContinuity092CChecks
{
	private static final long PROFILE = 1;
	private static final int OBJECT = 268480990;
	private static Path proof() { return Path.of(System.getProperty("phantom.task092c.proof")); }
	private static void require(boolean value, String reason) { PhantomAssertions.assertTrue(value, "092C_" + reason); }

	static PhantomMaterializationLifecyclePort lifecycle(PhantomBackgroundService background, PhantomHistoricalBackgroundService history)
	{
		return new PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long id, int object) { beforeMaterialize(id, object, PhantomMaterializationService.MaterializationPurpose.NORMAL, ""); }
			@Override public void beforeMaterialize(long id, int object, PhantomMaterializationService.MaterializationPurpose purpose, String claim)
			{ history.beforeMaterialize(id, object, purpose, claim); background.beforeMaterialize(id, object, purpose, claim); }
			@Override public void afterPlayerLoad(long id, Player player) { background.afterPlayerLoad(id, player); history.afterPlayerLoad(id, player); }
			@Override public void beforeWorldSpawn(long id, Player player) { background.beforeWorldSpawn(id, player); }
			@Override public void materializeSucceeded(long id, int object) { background.materializeSucceeded(id, object); history.materializeSucceeded(id, object); }
			@Override public void materializeAborted(long id, int object) { try { background.materializeAborted(id, object); } finally { history.materializeAborted(id, object); } }
			@Override public void beforeStore(long id, Player player) { history.beforeStore(id, player); background.beforeStore(id, player); }
			@Override public void afterStore(long id, Player player) { background.afterStore(id, player); history.afterStore(id, player); }
		};
	}

	static void run(boolean restart, boolean acceptance, PhantomBackgroundSuite.ProductionAuthorityFixture fixture, PhantomProfileRepository profiles,
		PhantomGoalStateStore goals, PhantomBackgroundTransaction transactions, PhantomBackgroundService background, PhantomMaterializationService materialization,
		PhantomHistoricalBackgroundService history, PhantomTopologyService topology, PhantomRelevanceSignalPort signals) throws Exception
	{
		final var codec = new PhantomBackgroundStateCodec();
		final var initial = background.acquisitionSnapshot(PROFILE).orElseThrow();
		final var initialGoal = goals.load(PROFILE).orElseThrow().goal();
		require(initial.identity().profileId() == PROFILE && initial.identity().characterObjectId() == OBJECT, "EXACT_IDENTITY");
		for (Class<?> type : java.util.List.of(PhantomMaterializationService.class, PhantomVisibleAutoPlay.class, PhantomHistoricalBackgroundService.class))
		{
			final var location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
			require(location.equals(Path.of(System.getProperty("phantom.task092b.expectedProduction"))), "PRODUCTION_CODESOURCE");
			System.out.println("092C_CODESOURCE " + type.getName() + "=" + location);
		}
		if (restart)
		{
			final var saved = codec.decode(Files.readAllBytes(proof().resolve("earned-state.bin")));
			require(initial.equals(saved), "RESTART_EXACT_STATE");
			final byte[] goal = profiles.findComponent(PROFILE, PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow().payload();
			require(java.util.Arrays.equals(goal, Files.readAllBytes(proof().resolve("earned-goal.bin"))), "RESTART_EXACT_GOAL");
			final var replay = transactions.reconcileVerifyPending(PROFILE, OBJECT);
			require(replay.successful() && initial.equals(replay.state()) && initial.equals(background.acquisitionSnapshot(PROFILE).orElseThrow()), "RESTART_NO_DUPLICATE");
			require(World.getInstance().findObject(OBJECT) == null && materialization.find(PROFILE).isEmpty(), "RESTART_WORLD_EMPTY");
			System.out.println("092C_RESTART_PASS state=" + initial.progress() + " inventory=" + initial.inventory().canonicalHash());
			require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "RESTART_STOP");
			topology.beginStop(); require(topology.finishStop(), "RESTART_TOPOLOGY_STOP");
			return;
		}
		if (acceptance)
		{
			require(initial.equals(codec.decode(Files.readAllBytes(proof().resolve("baseline-state.bin"))))
				&& java.util.Arrays.equals(profiles.findComponent(PROFILE, PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow().payload(), Files.readAllBytes(proof().resolve("baseline-goal.bin"))), "POST_DEATH_BASELINE");
		}
		else
		{
			require(initial.equals(codec.decode(Files.readAllBytes(PhantomTask091Lane.root().resolve("arrival.bin"))))
				&& initialGoal.goalId() == 3065608245847424986L && initialGoal.revision() == 2, "COMMITTED_ARRIVAL_GOAL");
		}
		System.out.println("092C_INPUT_PASS " + initial.position() + " progress=" + initial.progress());
		// This is the existing stock world loader; no fixture NPC, spawn point or balance edits.
		org.l2jmobius.gameserver.data.xml.SpawnData.getInstance();
		final long grace = System.nanoTime() + 60_100_000_000L;
		while (System.nanoTime() < grace) { Thread.sleep(1000); }
		final var catchup = history.status(PROFILE).orElseThrow();
		System.out.println("092C_HANDOFF_INPUT " + catchup);
		final var loaded = catchup.state().blocksNormalOperation()
			? materialization.materialize(PROFILE, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, catchup.state().requestId())
			: materialization.materialize(PROFILE);
		System.out.println("092C_MATERIALIZE " + loaded);
		if (loaded.status() != PhantomMaterializationService.ResultStatus.SUCCESS)
		{
			require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "FAILED_HANDOFF_STOP");
			topology.beginStop(); require(topology.finishStop(), "FAILED_HANDOFF_TOPOLOGY_STOP");
			require(false, "HANDOFF_UNAVAILABLE_" + loaded.status());
		}
		final Player player = World.getInstance().getPlayer(OBJECT);
		final var ref = new AtomicReference<PhantomDecisionEngine>();
		final var auto = new PhantomVisibleAutoPlay(materialization, ref::get, history::permitsDecision);
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new PhantomMetrics());
		final var travel = new PhantomVisibleFarmTravel(materialization, background, fixture.authority().travelQuery(fixture.topology()), navigation, history::permitsDecision, signals, history::recordVisibleTravelFailure, System::nanoTime);
		final var adapter = PhantomBackgroundDecision.bindVisibleLife(background, travel, auto, history, ref::get);
		final var candidates = new PhantomCandidateRegistry(); adapter.registerCandidates(candidates); candidates.seal();
		final var handlers = new PhantomStepHandlerRegistry(); adapter.registerHandlers(handlers); handlers.seal();
		final var engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1, null, history::permitsDecision);
		ref.set(engine);
		final Set<Integer> targetObjects = ConcurrentHashMap.newKeySet(), templates = ConcurrentHashMap.newKeySet();
		final Map<Integer, Double> initialHp = new ConcurrentHashMap<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final var dealt = new java.util.concurrent.atomic.AtomicInteger();
		final var hpReduced = new java.util.concurrent.atomic.AtomicBoolean();
		final var targets = new ConcurrentHashMap<Integer, Creature>();
		final var listener = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if (event.getAttacker().getObjectId() != OBJECT || event.getDamage() <= 0 || !event.getTarget().isMonster()) { return; }
			final var target = event.getTarget();
			targetObjects.add(target.getObjectId()); templates.add(target.asNpc().getId()); targets.put(target.getObjectId(), target);
			if (dealt.incrementAndGet() <= 48) { System.out.println("092C_DAMAGE target=" + target.getObjectId() + " npc=" + target.asNpc().getId() + " skill=" + (event.getSkill() == null ? 0 : event.getSkill().getId()) + " amount=" + event.getDamage() + " hp=" + target.getCurrentHp() + " actorHp=" + event.getAttacker().getCurrentHp() + " mp=" + event.getAttacker().getCurrentMp()); }
		}, PhantomArrivalFarmContinuity092CChecks.class);
		final var incoming = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_RECEIVED,
			(org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived event) ->
			System.out.println("092C_INCOMING source=" + event.getAttacker().getObjectId() + " amount=" + event.getDamage() + " hp=" + event.getTarget().getCurrentHp() + " mp=" + event.getTarget().getCurrentMp()), PhantomArrivalFarmContinuity092CChecks.class);
		final var casts = new ConsumerEventListener(player, EventType.ON_CREATURE_SKILL_USE,
			(org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillUse event) ->
			System.out.println("092C_CAST_ISSUED skill=" + event.getSkill().getId() + " target=" + event.getTarget().getObjectId() + " hp=" + event.getCaster().getCurrentHp() + " mp=" + event.getCaster().getCurrentMp()), PhantomArrivalFarmContinuity092CChecks.class);
		final Set<Player> observedPlayers = ConcurrentHashMap.newKeySet();
		Player observed = player;
		boolean sawLiving = !player.isDead();
		java.util.concurrent.ScheduledFuture<?> driver = null;
		final long exp = player.getExp(), sp = player.getSp();
		boolean earned = false;
		long expectedExp = exp, expectedSp = sp;
		try
		{
			require(player == World.getInstance().findObject(OBJECT) && player.getX() == initial.position().x() && player.getY() == initial.position().y()
				&& player.getZ() == initial.position().z() && player.getInstanceId() == 0 && materialization.find(PROFILE).orElseThrow().actionAdmissionOpen(), "PLAYER_WORLD_XYZ_LEASE");
			System.out.println("092C_PLAYER_PASS hp=" + player.getCurrentHp() + "/" + player.getMaxHp() + " mp=" + player.getCurrentMp() + "/" + player.getMaxMp() + " equipment=" + player.getInventory().getPaperdollItems().stream().map(item -> item.getObjectId() + ":" + item.getId()).toList());
			for (var target : World.getInstance().getVisibleObjectsInRange(player, Creature.class, 5000))
			{
				if (!target.isMonster() || target.isRaid()) { continue; }
				initialHp.put(target.getObjectId(), target.getCurrentHp());
				System.out.println("092C_STOCK target=" + target.getObjectId() + " npc=" + target.asNpc().getId() + " hp=" + target.getCurrentHp() + "/" + target.getMaxHp() + " distance=" + player.calculateDistance2D(target) + " viable=" + org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.viable(player, target));
			}
			player.addListener(listener); player.addListener(incoming); player.addListener(casts); observedPlayers.add(player);
			require(navigation.start(), "NAVIGATION_START"); engine.start(); engine.attach(PROFILE);
			final var tick = new java.util.concurrent.atomic.AtomicLong();
			driver = ThreadPool.schedulePriorityTaskAtFixedRate(() ->
			{
				try
				{
					if (history.prepareVisibleDecision(PROFILE, engine))
					{
						final int npc = PhantomBackgroundGoalSpec.parse(goals.load(PROFILE).orElseThrow().goal()).npcId();
						if (!templates.contains(npc) && templates.size() >= 3 || targetObjects.size() >= 6) { return; }
						templates.add(npc);
						engine.accept(new PhantomActivityWorkItem(PROFILE, PhantomActivityState.ACTIVE, 1, tick.incrementAndGet(), System.nanoTime(), PhantomActivityOverloadLevel.NORMAL));
					}
				}
				catch (Throwable error) { failure.compareAndSet(null, error); }
			}, 0, 250);
			final long start = System.nanoTime(), deadline = start + 180_000_000_000L;
			long next = start;
			while (System.nanoTime() < deadline && (acceptance || !observed.isDead()) && failure.get() == null && targetObjects.size() < 6 && templates.size() <= 3)
			{
				final Player current = World.getInstance().getPlayer(OBJECT);
				if (current != null)
				{
					observed = current;
					if (observedPlayers.add(current)) { current.addListener(listener); current.addListener(incoming); current.addListener(casts); }
				}
				if (current == null || observed.getNativeWorkOwner() == null) { Thread.sleep(100); continue; }
				if (sawLiving && observed.isDead()) { System.out.println("092C_SAFETY_STOP native_death_after_recovery"); break; }
				sawLiving |= !observed.isDead();
				final var sample = observed.getNativeWorkOwner().evidence().snapshot();
				for (var target : targets.values()) { if (target.getCurrentHp() < initialHp.getOrDefault(target.getObjectId(), (double) target.getMaxHp())) { hpReduced.set(true); } }
				earned = sample.killSequence() > 0 && ((observed.getExp() > exp && sample.expGained() > 0) || sample.lootSequence() > 0);
				if (earned) { break; }
				if (System.nanoTime() >= next)
				{
					next += 10_000_000_000L;
					System.out.println("092C_OBSERVE evidence=" + sample + " reason=" + history.visibleRecoveryReason(PROFILE) + " goal=" + goals.load(PROFILE).orElseThrow().goal() + " runtime=" + engine.find(PROFILE));
					if (acceptance)
					{
						final var primary = org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.preparationSkill(observed, observed.getTarget() instanceof Creature target ? target : null);
						System.out.println("092C_NATIVE hp=" + observed.getCurrentHp() + "/" + observed.getMaxHp() + " mp=" + observed.getCurrentMp() + "/" + observed.getMaxMp() + " dead=" + observed.isDead() + " xyz=" + observed.getX() + "," + observed.getY() + "," + observed.getZ() + " primary=" + (primary == null ? 0 : primary.getId()) + " magic=" + (primary != null && primary.isMagic()) + " action2=" + observed.getAutoUseSettings().getAutoActions().contains(2) + " attack=" + observed.isAttackingNow() + " cast=" + observed.isCastingNow() + " moving=" + observed.isMoving() + " intention=" + (observed.hasAI() ? observed.getAI().getIntention() : "NONE") + " threat=" + org.l2jmobius.gameserver.phantoms.combat.PhantomNativeLootControl.threat(observed) + " owner=" + observed.getNativeWorkOwner().epoch() + " policy=" + auto.snapshotContinuation(PROFILE).scalarMap() + " inventory=" + observed.getInventory().getItems().stream().map(item -> item.getId() + ":" + item.getCount()).toList() + " buffs=" + observed.getEffectList().getEffects().size());
					}
				}
				if (!acceptance && dealt.get() == 0 && System.nanoTime() - start > 60_000_000_000L) { break; }
				Thread.sleep(100);
			}
			expectedExp = observed.getExp(); expectedSp = observed.getSp();
			System.out.println("092C_EPISODE damage=" + dealt + " hpReduced=" + hpReduced + " earned=" + earned + " expDelta=" + (expectedExp - exp) + " spDelta=" + (expectedSp - sp) + " hp=" + observed.getCurrentHp() + " mp=" + observed.getCurrentMp() + " dead=" + observed.isDead() + " templates=" + templates + " targets=" + targetObjects + " failure=" + failure.get() + " reason=" + history.visibleRecoveryReason(PROFILE));
		}
		finally
		{
			if (driver != null) { driver.cancel(false); }
			auto.stop(PROFILE); travel.beforeMaterialize(PROFILE, OBJECT);
			PhantomVisibleIntentRecoverySuite.stop(engine); navigation.beginStop(); navigation.finishStop();
			for (Player attached : observedPlayers) { attached.removeListener(listener); attached.removeListener(incoming); attached.removeListener(casts); }
			final var stored = materialization.dematerialize(PROFILE);
			System.out.println("092C_STORE " + stored);
			require(stored.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "STORE_UNAVAILABLE");
			require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED && materialization.snapshot().retainedEntries() == 0 && World.getInstance().findObject(OBJECT) == null, "STOP_WORLD_LEASES");
			topology.beginStop(); require(topology.finishStop(), "TOPOLOGY_STOP");
		}
		final var after = background.acquisitionSnapshot(PROFILE).orElseThrow();
		require(after.identity().equals(initial.identity()) && after.progress().experience() == expectedExp && after.progress().skillPoints() == expectedSp, "STORE_NATIVE_RESULT");
		Files.write(proof().resolve("stored-state.bin"), codec.encode(after), StandardOpenOption.CREATE_NEW);
		System.out.println("092C_STORE_PASS position=" + after.position() + " progress=" + after.progress() + " inventory=" + after.inventory().canonicalHash());
		require(failure.get() == null && targetObjects.size() <= 6 && templates.size() <= 3, "EPISODE_FAILURE_OR_BUDGET");
		require(dealt.get() > 0 && hpReduced.get(), "NO_NATIVE_DAMAGE");
		require(earned, "NO_EARNED_FARM");
		Files.write(proof().resolve("earned-state.bin"), codec.encode(after), StandardOpenOption.CREATE_NEW);
		Files.write(proof().resolve("earned-goal.bin"), profiles.findComponent(PROFILE, PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow().payload(), StandardOpenOption.CREATE_NEW);
		final var facts = new Properties(); facts.setProperty("goal", goals.load(PROFILE).orElseThrow().goal().toString()); facts.setProperty("exp", Long.toString(expectedExp)); facts.setProperty("sp", Long.toString(expectedSp));
		try (var writer = Files.newBufferedWriter(proof().resolve("earned.properties"), StandardOpenOption.CREATE_NEW)) { facts.store(writer, "TASK092C earned canonical result"); }
		System.out.println("092C_CAMPAIGN_PASS native=true earned=true store=true conditionalRestart=ALLOWED");
	}
}
