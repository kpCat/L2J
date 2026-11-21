/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPolicy;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingStore;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomPvpRetreatCoordinator;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpContext;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPolicy;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpService;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStore;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.tests.phantoms.PhantomBackgroundSuite.ProductionAuthorityFixture;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelFixture;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelSlot;

/** Bounded headless N06 route. Production PvP derives its own authority from a stock incoming hit. */
final class PhantomCombat131DuelRuntime
{
	private static final int DRAIN_SECONDS = 30;
	private static final int OPENING_SECONDS = 15;
	private static final int PULSE_MILLIS = 75;
	private PhantomCombat131DuelRuntime() { }

	static boolean bothArmsFit(Instant now, Instant deadline, int seconds)
	{
		return seconds >= 20 && seconds <= 120 && now.plusSeconds(2L * (seconds + DRAIN_SECONDS)).isBefore(deadline);
	}

	static void run(DuelFixture fixture, ProductionAuthorityFixture source, Path game, Path evidence, String manifestSha, String boot,
		Instant deadline, int seconds) throws Exception
	{
		require(bothArmsFit(Instant.now(), deadline, seconds), "TIME_BUDGET");
		require(World.getInstance().getPlayers().isEmpty(), "FOREIGN_PLAYERS");
		PhantomCombat131Fixture.verifyDuelArena(fixture);
		final var seal = new StringBuilder("schema\tTASK131_N06_NATIVE_RUN_V1\nmanifestSha\t").append(manifestSha)
			.append("\nfixtureSha\t").append(fixture.sha256()).append("\nboot\t").append(boot).append('\n');
		for (int arm = 1; arm <= 2; arm++)
		{
			require(Instant.now().plusSeconds(seconds + DRAIN_SECONDS).isBefore(deadline), "ARM_TIME_BUDGET");
			final var directory = Files.createDirectory(evidence.resolve("arm-" + arm));
			final var path = runArm(fixture, source, game, directory, manifestSha, boot, deadline, seconds, arm);
			seal.append("arm\t").append(arm).append('\t').append(sha(path)).append('\n');
		}
		require(World.getInstance().getPlayers().isEmpty(), "WORLD_NOT_DRAINED");
		writeNew(evidence.resolve("n06-run-seal.tsv"), seal);
	}

	private static Path runArm(DuelFixture fixture, ProductionAuthorityFixture source, Path game, Path directory, String manifestSha,
		String boot, Instant deadline, int seconds, int arm) throws Exception
	{
		final var roster = fixture.arm(arm);
		final Instant setupDeadline = earlier(deadline.minusSeconds(DRAIN_SECONDS), Instant.now().plusSeconds(seconds + 60L));
		final var duel = new PhantomCombat131Duel(fixture, arm, setupDeadline);
		final var profiles = PhantomProfileRepository.open();
		final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics), 2, duel);
		final Path cut = directory.resolve("n06-cut.tsv");
		PhantomCombat131DuelObservation observation = null;
		long fightEndNanos;
		try (duel)
		{
			try (var owners = new Owners(deadline))
			{
				owners.add(() -> require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED
					&& materialization.list().isEmpty() && materialization.occupiedPhysicalSlots() == 0, "MATERIALIZATION_DRAIN"));
				materialization.requireOffensiveShots();
				require(materialization.start(), "MATERIALIZATION_START");
				final var combat = new PhantomCombatService(new L2jCombatBackend(materialization, source::knowledge, source::progression),
					PhantomCombatCapabilityResolver.fromGameKnowledge(source::knowledge), PhantomCombatPolicy.productionDefaults(2));
				owners.track(combat::beginStop, combat::finishStop);
				combat.start();

				final var control = startPvp(source, game, profiles, metrics, materialization, combat, owners);
				for (var slot : roster)
				{
					final var identity = slot.identity();
					require(materialization.materialize(identity.profile()).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "ACTOR_MATERIALIZATION");
					control.topology().registerProfile(identity.profile());
					control.pvp().materializeSucceeded(identity.profile(), identity.object());
				}
				final Player opener = player(roster.getFirst());
				final Player receiver = player(roster.getLast());
				require(Instant.now().plusSeconds(seconds + DRAIN_SECONDS).isBefore(deadline), "ARM_SETUP_OVERRUN");
				observation = new PhantomCombat131DuelObservation(opener, receiver);
				duel.sealAtStart(cut, manifestSha, boot, "N06_ARM_" + arm);
				final Instant fightEnd = earlier(setupDeadline, Instant.now().plusSeconds(seconds));
				fightEndNanos = monotonicDeadline(fightEnd);
				require(duel.submitOpening(materialization), "OPENING_REFUSED");
				awaitOpening(duel, observation, earlier(fightEnd, Instant.now().plusSeconds(OPENING_SECONDS)));
				require(combat.observePvp(roster.getLast().identity().profile(), List.of(opener.getObjectId()), 16, 8).orElseThrow()
					.targets().stream().anyMatch(target -> target.target().objectId() == opener.getObjectId() && target.actualAttacker()
						&& target.canonicalContextAllowed()), "NO_CANONICAL_REACTIVE_SOURCE");
				fight(duel, observation, control, roster, fightEnd);
				duel.close();
			}
			require(Instant.now().isBefore(deadline) && World.getInstance().getPlayers().isEmpty(), "ARM_WORLD_NOT_DRAINED");
			final var result = observation.closeAfterDrain();
			final var stored = verifyStored(duel, roster, result.nativeDeaths());
			final long admittedDeadline = fightEndNanos;
			final boolean lateDeath = result.nativeDeaths().stream().anyMatch(death -> death.observedNanos() - admittedDeadline > 0);
			final Path receipt = directory.resolve("n06-result.tsv");
			writeNew(receipt, new StringBuilder("schema\tTASK131_N06_NATIVE_ARM_V1\ncutSha\t").append(sha(cut))
				.append("\narm\t").append(arm).append("\nstatus\tOBSERVED\nsamples\t").append(result.samples())
				.append("\nfirstSampleNanos\t").append(result.firstSampleNanos()).append("\nlastSampleNanos\t").append(result.lastSampleNanos())
				.append("\nfightDeadlineNanos\t").append(admittedDeadline).append("\ndeathWitnessAfterDeadline\t").append(lateDeath)
				.append("\nstockOutcome\t").append(result.outcome().map(Enum::name).orElse("UNKNOWN"))
				.append("\ninWindowOutcome\t").append(lateDeath ? "UNKNOWN" : result.outcome().map(Enum::name).orElse("UNKNOWN"))
				.append("\nleft\t").append(result.left()).append("\nright\t").append(result.right())
				.append("\ndeaths\t").append(result.nativeDeaths()).append("\nstore\t").append(stored.getFirst())
				.append("\nstore\t").append(stored.getLast()).append('\n'));
			return receipt;
		}
		finally { if (observation != null) { observation.close(); } }
	}

	private record ReactiveOwners(PhantomTopologyService topology, PhantomPvpService pvp, PhantomConversationExecutionService conversation,
		PhantomPvpStore encounters) { }

	private static ReactiveOwners startPvp(ProductionAuthorityFixture source, Path game, PhantomProfileRepository profiles, PhantomMetrics metrics,
		PhantomMaterializationService materialization, PhantomCombatService combat, Owners owners)
	{
		final var goals = new PhantomGoalStateStore(profiles);
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, game, source::knowledge), PhantomProgressionPolicy.productionDefaults());
		owners.track(progression::beginStop, progression::finishStop);
		progression.start();
		final var navigation = new PhantomNavigationService(metrics);
		owners.track(navigation::beginStop, navigation::finishStop);
		require(navigation.start(), "NAVIGATION_START");
		final var topology = PhantomTopologyService.fromSnapshotForTesting(source.topology().snapshot(), source.topologyBackend(), PhantomTopologyPolicy.productionDefaults(), new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String key, long sequence) { return SignalDelivery.ACCEPTED; }
		});
		owners.track(topology::beginStop, topology::finishStop);
		require(topology.start(), "TOPOLOGY_START");
		final var socialCatalog = PhantomSocialCatalog.load(game.resolve("data/phantoms/social/high-five-social-v1.xml"));
		final var social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(profiles, socialCatalog), 13106L, 16, PhantomCombat131DuelRuntime::epochMinute);
		owners.track(social::beginStop, social::finishStop);
		require(social.start(), "SOCIAL_START");
		final var partyBackend = new L2jPhantomPartyBackend(profiles, materialization, progression);
		final var party = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, partyBackend,
			PhantomPartyRoleCatalog.load(game.resolve("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(navigation, combat),
			new PhantomPartyTactics(combat, partyBackend), () -> source.topology().snapshot().canonicalHash(), System::nanoTime, 16, social, PhantomCombat131DuelRuntime::epochMinute);
		owners.track(party::beginStop, party::finishStop);
		require(party.start(), "PARTY_START");
		final var farming = new PhantomFarmingService(PhantomFarmingPolicy.load(game.resolve("data/phantoms/farming/high-five-farming-conflict-v1.xml")),
			new PhantomFarmingStore(profiles), _ -> Optional.empty(), topology, (first, second) -> false, new PhantomFarmingService.SocialFacts()
			{
				@Override public PhantomFarmingService.SocialEvidence evidence(long owner, long counterpart, long minute) { throw failure("UNEXPECTED_FARMING_AUTHORITY"); }
				@Override public boolean record(long owner, long counterpart, String key, String event, String hash, long minute) { throw failure("UNEXPECTED_FARMING_WRITE"); }
			}, 16, PhantomCombat131DuelRuntime::epochMinute, PhantomFarmingService.FaultInjector.NONE);
		owners.track(farming::beginStop, farming::finishStop);
		require(farming.start(), "FARMING_START");
		final var catalog = PhantomConversationExecutionCatalog.load(game.resolve("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
		final var conversation = new PhantomConversationExecutionService(catalog, new PhantomConversationExecutionStore(profiles, catalog), goals,
			new L2jPhantomConversationExecutionPort(catalog, source.knowledgeService(), source.topology(), party, materialization, ChatObservationService.getInstance()));
		owners.track(conversation::beginStop, conversation::finishStop);
		require(conversation.start(), "CONVERSATION_START");
		final var policy = PhantomPvpPolicy.load(game.resolve("data/phantoms/pvp/pvp-policy-v1.xml"));
		final var pvpSocial = new PhantomPvpSocialBridge(social);
		final var encounters = new PhantomPvpStore(profiles);
		final var pvp = new PhantomPvpService(policy, encounters, new PhantomPvpContext(policy, combat, party, farming, pvpSocial, materialization),
			combat, new PhantomPvpConversationBridge(conversation), pvpSocial, new PhantomPvpRetreatCoordinator(navigation, topology, combat));
		owners.track(pvp::beginStop, pvp::finishStop);
		require(pvp.start(), "PVP_START");
		return new ReactiveOwners(topology, pvp, conversation, encounters);
	}

	private static void awaitOpening(PhantomCombat131Duel duel, PhantomCombat131DuelObservation observation, Instant expiry) throws Exception
	{
		final long fixedDeadline = monotonicDeadline(expiry);
		while (!duel.openingLandedBefore(fixedDeadline))
		{
			require(fixedDeadline - System.nanoTime() > 0 && Instant.now().isBefore(expiry)
				&& duel.currentInArena() && !observation.hasNativeDeath(), "NO_BOUND_OPENING_EFFECT");
			observation.sample();
			Thread.sleep(PULSE_MILLIS);
		}
	}

	private static void fight(PhantomCombat131Duel duel, PhantomCombat131DuelObservation observation, ReactiveOwners control,
		List<DuelSlot> roster, Instant expiry) throws Exception
	{
		long sequence = 0;
		while (Instant.now().isBefore(expiry) && !observation.hasNativeDeath())
		{
			require(duel.currentInArena(), "SCENE_SCOPE_LOST");
			observation.sample();
			if (roster.stream().map(PhantomCombat131DuelRuntime::player).anyMatch(Player::isDead)) { break; }
			for (var slot : roster)
			{
				final Player actor = player(slot);
				control.topology().updateProfile(slot.identity().profile(), new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()), ++sequence);
			}
			control.pvp().onPulse();
			control.conversation().onPulse();
			require(control.pvp().snapshot().failures() == 0, "PVP_OWNER_FAILURE");
			Thread.sleep(PULSE_MILLIS);
		}
		require(duel.currentInArena(), "TERMINAL_SCOPE_LOST");
		observation.sample();
		require(control.pvp().snapshot().engagements() > 0, "NO_SHARED_OWNER_ENGAGEMENT");
		final var receiver = roster.getLast().identity();
		final var engaged = control.encounters().load(receiver.profile()).orElseThrow(() -> failure("NO_REACTIVE_ENCOUNTER")).encounter();
		require(engaged.source() == Source.ACTUAL_ATTACK && engaged.counterpart().currentObjectId() == roster.getFirst().identity().object(),
			"REACTIVE_ENCOUNTER_DRIFT");
	}

	private static Player player(DuelSlot slot)
	{
		final Player actor = World.getInstance().getPlayer(slot.identity().object());
		if (actor == null) { throw failure("OWNED_PLAYER_ABSENT"); }
		return actor;
	}

	private static List<PhantomCombat131Duel.Store> verifyStored(PhantomCombat131Duel duel, List<DuelSlot> roster,
		List<PhantomCombat131DuelObservation.NativeDeath> deaths) throws Exception
	{
		final var settled = new ArrayList<PhantomCombat131Duel.Store>(2);
		try (var connection = DatabaseFactory.getConnection();
			var query = connection.prepareStatement("SELECT c.online,c.level,c.classid,c.x,c.y,c.z,"
				+ "(SELECT COALESCE(SUM(i.count),0) FROM items i WHERE i.owner_id=c.charId AND i.item_id=?),"
				+ "(SELECT COALESCE(SUM(i.count),0) FROM items i WHERE i.owner_id=c.charId AND i.item_id=57) "
				+ "FROM characters c WHERE c.charId=? AND c.account_name=?"))
		{
			connection.setReadOnly(true);
			for (var slot : roster)
			{
				final var stored = duel.store(slot.identity().profile());
				require(stored != null && stored.object() == slot.identity().object() && stored.shots() <= slot.shots()
					&& stored.adena() <= slot.adena(), "POST_STORE_RECEIPT");
				final var death = deaths.stream().filter(row -> row.victimObjectId() == stored.object() && row.victimEpoch() == stored.epoch()).findFirst();
				require(stored.dead() == death.isPresent()
					&& death.map(PhantomCombat131DuelObservation.NativeDeath::corpseGeneration).orElse(0L) == stored.corpse(), "UNATTRIBUTED_TERMINAL_DEATH");
				query.setInt(1, slot.shotItem());
				query.setInt(2, slot.identity().object());
				query.setString(3, slot.identity().account());
				try (var row = query.executeQuery())
				{
					require(row.next() && row.getInt(1) == 0 && row.getInt(2) == slot.level() && row.getInt(3) == slot.identity().profession()
						&& row.getInt(4) == stored.x() && row.getInt(5) == stored.y() && row.getInt(6) == stored.z()
						&& row.getLong(7) == stored.shots() && row.getLong(8) == stored.adena() && !row.next(), "DURABLE_ACTOR_STORE");
				}
				settled.add(stored);
			}
		}
		return List.copyOf(settled);
	}

	private static final class Owners implements AutoCloseable
	{
		private final List<AutoCloseable> _stop = new ArrayList<>();
		private final Instant _deadline;
		private long _drainEnd;

		private Owners(Instant deadline) { _deadline = deadline; }
		private void add(AutoCloseable stop) { _stop.add(stop); }
		private void track(Runnable begin, BooleanSupplier finish)
		{
			add(() ->
			{
				begin.run();
				while (!finish.getAsBoolean())
				{
					require(_drainEnd - System.nanoTime() > 0 && Instant.now().isBefore(_deadline), "OWNER_DRAIN_DEADLINE");
					Thread.sleep(25);
				}
			});
		}

		@Override public void close() throws Exception
		{
			_drainEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(DRAIN_SECONDS);
			PhantomPvp072Native.closeAll(_stop);
		}
	}

	private static long epochMinute() { return System.currentTimeMillis() / 60_000L; }
	private static Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
	private static long monotonicDeadline(Instant end) { return System.nanoTime() + Duration.between(Instant.now(), end).toNanos(); }
	private static String sha(Path file) throws IOException { return TacticalObservationV3.hash(Files.readAllBytes(file)); }
	private static void writeNew(Path path, CharSequence value) throws IOException
	{
		Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
	}
	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw failure(reason); }
	}
	private static IllegalStateException failure(String reason) { return new IllegalStateException("TASK131_N06_" + reason); }
}
