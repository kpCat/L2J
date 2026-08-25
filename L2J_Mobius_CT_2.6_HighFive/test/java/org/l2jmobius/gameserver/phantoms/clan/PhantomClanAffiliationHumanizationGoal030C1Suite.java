/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft.DepartureKind;
import org.l2jmobius.gameserver.model.events.listeners.AbstractEventListener;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.MemberRef;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleOutcome;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.L2jPhantomSocialAffiliationContextResolver;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge.EventKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Goal030C1 canonical clan affiliation and expulsion integration. */
public final class PhantomClanAffiliationHumanizationGoal030C1Suite implements PhantomTestSuite
{
	private static final long SEED = 30003031L;
	private static final String ZERO = "0".repeat(64);
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Integer> _clanIds = new ArrayList<>();
	private PhantomProfileRepository _profiles;
	private PhantomProfile _ownerProfile;
	private PhantomProfile _targetProfile;
	private PhantomMaterializationService _materialization;
	private PhantomSocialService _social;
	private L2jPhantomSocialAffiliationContextResolver _resolver;
	private PhantomClanSocialLifecycleObserver _observer;
	private Player _owner;
	private Player _target;
	private Path _moduleRoot;
	private int _nameSequence;

	@Override
	public String id()
	{
		return "clan-affiliation-humanization-goal030c1";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030C1 used the wrong deterministic seed.");
		_environment.initialize(context);
		_moduleRoot = context.moduleRoot();
		_profiles = PhantomProfileRepository.open();
		_ownerProfile = _profiles.create(_environment.primary().objectId());
		_targetProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "Goal030C1 materialization did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_ownerProfile.profileId()).status(), "Owner Phantom did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_targetProfile.profileId()).status(), "Target Phantom did not materialize.");
		_owner = World.getInstance().getPlayer(_environment.primary().objectId());
		_target = World.getInstance().getPlayer(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_owner != null) && (_target != null), "Goal030C1 Players are absent from World.");
		_owner.getStat().setLevel((byte) 20);
		_target.getStat().setLevel((byte) 20);
		final PhantomSocialCatalog catalog = PhantomSocialCatalog.load(_moduleRoot.resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		_social = new PhantomSocialService(catalog, new PhantomSocialStore(_profiles, catalog), SEED, 64, () -> System.currentTimeMillis() / 60000L);
		PhantomAssertions.assertTrue(_social.start(), "Goal030C1 social service did not start.");
		_resolver = new L2jPhantomSocialAffiliationContextResolver(_materialization);
		_observer = new PhantomClanSocialLifecycleObserver(_profiles, _social);
		PhantomAssertions.assertTrue(_observer.install(), "Goal030C1 clan observer did not install.");
		context.record("goal030c1.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("goal030c1.ownerProfileId", _ownerProfile.profileId());
		context.record("goal030c1.targetProfileId", _targetProfile.profileId());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_observer.close();
		cleanupClans();
		stopSocial();
		_materialization.shutdown();
		deleteProfile(_ownerProfile.profileId());
		deleteProfile(_targetProfile.profileId());
		_environment.assertClean(_environment.primary(), _owner);
		_environment.assertClean(_environment.observer(), _target);
		context.record("goal030c1.cleanup.clans", _clanIds.size());
		context.record("goal030c1.cleanup.observerInstalled", _observer.installed());
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-canonical-affiliation-real-and-phantom-identities", this::canonicalAffiliation);
		registry.add("02-party-context-clan-alliance-neutral", this::partyContexts);
		registry.add("03-pvp-clan-war-context-and-native-pk-risk", this::pvpWarContext);
		registry.add("04-expelled-phantom-remembers-exact-initiator", this::expulsionBetrayal);
		registry.add("05-voluntary-and-dissolve-are-not-betrayal", this::nonExpulsionDepartures);
		registry.add("06-native-leader-transfer-truth", this::leaderTransfer);
		registry.add("07-idempotency-lifecycle-source-bounds-and-cleanup", this::lifecycleAndSourceBounds);
	}

	private void canonicalAffiliation(PhantomTestContext context) throws Exception
	{
		final Clan clan = sameClan();
		try
		{
			assertAffiliation(AffiliationKind.SAME_CLAN, SubjectRef.phantom(_targetProfile.profileId()));
			assertAffiliation(AffiliationKind.SAME_CLAN, SubjectRef.character(_target.getObjectId()));
			final L2jPhantomClanBackend backend = new L2jPhantomClanBackend(_profiles, _materialization, _social, null, _resolver);
			PhantomAssertions.assertTrue(backend.recordRelation(_ownerProfile.profileId(), MemberRef.phantom(_targetProfile.profileId(), _target.getObjectId()), "agreement.fulfilled", "goal030c1-same-clan", PhantomSocialModel.sha256("goal030c1.same-clan"), nowMinute()), "Clan backend did not durably record canonical SAME_CLAN context.");
			final var sameClan = _social.snapshot(_ownerProfile.profileId(), SubjectRef.phantom(_targetProfile.profileId()), 24, nowMinute()).value().relationship();
			PhantomAssertions.assertEquals(300, sameClan.relationship().get("trust"), "Clan backend did not apply SAME_CLAN supportive scaling.");

			clan.removeClanMember(_target.getObjectId(), 0, DepartureKind.UNKNOWN, 0);
			PhantomAssertions.assertEquals(AffiliationKind.NONE, _resolver.resolve(_ownerProfile.profileId(), SubjectRef.phantom(_targetProfile.profileId())).affiliation(), "Distinct unallied clans were guessed before target clan creation.");
			resetPenalties(_target);
			final Clan targetClan = createClan(_target);
			clan.changeLevel(5);
			targetClan.changeLevel(5);
			final AllianceIdentity identity = createAlliance(clan, targetClan);
			assertAffiliation(AffiliationKind.SAME_ALLIANCE, SubjectRef.phantom(_targetProfile.profileId()));
			dissolveAlliance(identity);
			context.record("goal030c1.resolverMatrix", "sameClan=PHANTOM+CHARACTER,sameAlliance=PHANTOM,neutral=NONE");
		}
		finally
		{
			cleanupClans();
		}
	}

	private void partyContexts(PhantomTestContext context) throws Exception
	{
		final CapturingSink sink = new CapturingSink();
		final PhantomPartyCoordinator coordinator = partyCoordinator(sink);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Party context coordinator did not start.");
			emitPartyTerminal(coordinator, 301);
			final Clan clan = sameClan();
			emitPartyTerminal(coordinator, 302);
			clan.removeClanMember(_target.getObjectId(), 0, DepartureKind.UNKNOWN, 0);
			resetPenalties(_target);
			final Clan targetClan = createClan(_target);
			clan.changeLevel(5);
			targetClan.changeLevel(5);
			final AllianceIdentity identity = createAlliance(clan, targetClan);
			emitPartyTerminal(coordinator, 303);
			dissolveAlliance(identity);
			final List<AffiliationKind> contexts = sink.events().stream().filter(event -> event.ownerProfileId() == _ownerProfile.profileId()).map(event -> event.context().affiliation()).toList();
			PhantomAssertions.assertEquals(List.of(AffiliationKind.NONE, AffiliationKind.SAME_CLAN, AffiliationKind.SAME_ALLIANCE), contexts, "Equivalent Party terminals did not carry neutral/clan/alliance context.");
			context.record("goal030c1.partyContexts", contexts);
		}
		finally
		{
			stopParty(coordinator);
			cleanupClans();
		}
	}
	private void pvpWarContext(PhantomTestContext context) throws Exception
	{
		final int memberThreshold = PlayerConfig.ALT_CLAN_MEMBERS_FOR_WAR;
		Clan ownerClan = null;
		Clan targetClan = null;
		try
		{
			final Clan[] clans = separateClans(3);
			ownerClan = clans[0];
			targetClan = clans[1];
			PlayerConfig.ALT_CLAN_MEMBERS_FOR_WAR = 1;
			final ClanWarService wars = ClanWarService.getInstance();
			final var declared = wars.declare(_owner, targetClan);
			PhantomAssertions.assertTrue(declared.successful(), "Canonical source clan war declaration failed: " + declared.reason());
			final var reply = wars.declareAcceptedReply(targetClan, ownerClan);
			PhantomAssertions.assertTrue(reply.successful(), "Canonical reciprocal clan war reply failed: " + reply.reason());
			assertAffiliation(AffiliationKind.CLAN_WAR, SubjectRef.character(_target.getObjectId()));

			final boolean autoAttackableBefore = _target.isAutoAttackable(_owner);
			final int ownerFlagBefore = _owner.getPvpFlag();
			final int ownerKarmaBefore = _owner.getKarma();
			final PhantomPvpSocialBridge bridge = new PhantomPvpSocialBridge(_social, _resolver);
			final SubjectRef subject = SubjectRef.character(_target.getObjectId());
			final long minute = nowMinute();
			final var delivery = bridge.record(_ownerProfile.profileId(), subject, EventKind.DEATH_SUFFERED, "goal030c1-war-death", PhantomSocialModel.sha256("goal030c1-war-death"), minute, 1000);
			PhantomAssertions.assertTrue(delivery.durable(), "Clan-war PvP social event was not durable.");
			final var snapshot = _social.snapshot(_ownerProfile.profileId(), subject, 24, minute).value().relationship();
			PhantomAssertions.assertEquals(-154, snapshot.relationship().get("trust"), "CLAN_WAR trust scaling changed.");
			PhantomAssertions.assertEquals(126, snapshot.relationship().get("fear"), "CLAN_WAR fear scaling changed.");
			PhantomAssertions.assertEquals(294, snapshot.relationship().get("anger"), "CLAN_WAR anger scaling changed.");
			PhantomAssertions.assertEquals(210, snapshot.relationship().get("rivalry"), "CLAN_WAR rivalry scaling changed.");
			PhantomAssertions.assertEquals(154, snapshot.reputation().get("hostility"), "CLAN_WAR hostility scaling changed.");
			PhantomAssertions.assertEquals(autoAttackableBefore, _target.isAutoAttackable(_owner), "Social recording changed native autoAttackable semantics.");
			PhantomAssertions.assertEquals(ownerFlagBefore, (int) _owner.getPvpFlag(), "Social recording changed native PvP flag.");
			PhantomAssertions.assertEquals(ownerKarmaBefore, _owner.getKarma(), "Social recording changed native karma.");
			context.record("goal030c1.pvp.context", AffiliationKind.CLAN_WAR);
			context.record("goal030c1.pvp.autoAttackable", autoAttackableBefore);
			context.record("goal030c1.pvp.angerHostility", "294/154");
		}
		finally
		{
			PlayerConfig.ALT_CLAN_MEMBERS_FOR_WAR = memberThreshold;
			endWars(ownerClan, targetClan);
			cleanupClans();
		}
	}

	private void expulsionBetrayal(PhantomTestContext context) throws Exception
	{
		final Clan clan = sameClan();
		final AtomicReference<OnPlayerClanLeft> captured = new AtomicReference<>();
		final AbstractEventListener captureListener = Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_CLAN_LEFT, (java.util.function.Consumer<OnPlayerClanLeft>) event ->
		{
			if (event.getClanMember().getObjectId() == _target.getObjectId())
			{
				captured.compareAndSet(null, event);
			}
		}, this));
		try
		{
			final long recordedBefore = _social.snapshot().recordedEvents();
			final long idempotentBefore = _social.snapshot().idempotentEvents();
			clan.removeClanMember(_target.getObjectId(), 0, DepartureKind.EXPELLED, _owner.getObjectId());
			await(() -> captured.get() != null, "Canonical expulsion event was not delivered.");
			final OnPlayerClanLeft event = captured.get();
			PhantomAssertions.assertEquals(DepartureKind.EXPELLED, event.getDepartureKind(), "Expulsion kind was not preserved.");
			PhantomAssertions.assertEquals(_owner.getObjectId(), event.getInitiatorObjectId(), "Expulsion initiator was not preserved.");
			final SubjectRef actor = SubjectRef.phantom(_ownerProfile.profileId());
			await(() -> hasMemory(_targetProfile.profileId(), actor, "clan.member.expelled"), "Expelled Phantom did not receive durable betrayal memory.");
			final var relationship = _social.snapshot(_targetProfile.profileId(), actor, 24, nowMinute()).value().relationship();
			PhantomAssertions.assertEquals(-520, relationship.relationship().get("trust"), "Expulsion trust delta or SAME_CLAN multiplier changed.");
			PhantomAssertions.assertEquals(-195, relationship.relationship().get("respect"), "Expulsion respect delta changed.");
			PhantomAssertions.assertEquals(455, relationship.relationship().get("anger"), "Expulsion anger delta changed.");
			PhantomAssertions.assertEquals(156, relationship.relationship().get("rivalry"), "Expulsion rivalry delta changed.");
			PhantomAssertions.assertEquals(-260, relationship.reputation().get("reliability"), "Expulsion reliability delta changed.");
			PhantomAssertions.assertEquals(325, relationship.reputation().get("hostility"), "Expulsion hostility delta changed.");
			PhantomAssertions.assertEquals(recordedBefore + 1, _social.snapshot().recordedEvents(), "Expulsion did not record exactly one event.");
			_observer.onClanLeft(event);
			PhantomAssertions.assertEquals(recordedBefore + 1, _social.snapshot().recordedEvents(), "Duplicate expulsion delivery created a second durable event.");
			PhantomAssertions.assertEquals(idempotentBefore + 1, _social.snapshot().idempotentEvents(), "Duplicate expulsion delivery was not idempotent.");
			final var memory = _social.snapshot(_targetProfile.profileId(), actor, 24, nowMinute()).value().memories().stream().filter(item -> item.eventKey().equals("clan.member.expelled")).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(1950, memory.salience(), "Expulsion memory did not apply XML SAME_CLAN betrayal salience.");
			context.record("goal030c1.expulsion.actor", actor.stableKey());
			context.record("goal030c1.expulsion.targetProfileId", _targetProfile.profileId());
			context.record("goal030c1.expulsion.deltas", "-520,-195,+455,+156,-260,+325");
		}
		finally
		{
			captureListener.unregisterMe();
			cleanupClans();
		}
	}

	private void nonExpulsionDepartures(PhantomTestContext context) throws Exception
	{
		final Clan clan = sameClan();
		final List<OnPlayerClanLeft> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		final AbstractEventListener captureListener = Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_PLAYER_CLAN_LEFT, (java.util.function.Consumer<OnPlayerClanLeft>) events::add, this));
		try
		{
			final long recordedBefore = _social.snapshot().recordedEvents();
			clan.removeClanMember(_target.getObjectId(), 0, DepartureKind.VOLUNTARY, _target.getObjectId());
			await(() -> events.stream().anyMatch(event -> event.getDepartureKind() == DepartureKind.VOLUNTARY), "Voluntary departure metadata was not delivered.");
			PhantomAssertions.assertEquals(recordedBefore, _social.snapshot().recordedEvents(), "Voluntary departure became betrayal.");
			resetPenalties(_target);
			clan.addClanMember(_target);
			destroyClan(clan);
			await(() -> events.stream().filter(event -> event.getDepartureKind() == DepartureKind.CLAN_DISSOLVED).count() >= 2, "Clan dissolution metadata was not delivered for exact members.");
			PhantomAssertions.assertTrue(events.stream().filter(event -> event.getDepartureKind() == DepartureKind.CLAN_DISSOLVED).allMatch(event -> event.getInitiatorObjectId() == _owner.getObjectId()), "Clan dissolution did not preserve the safely known leader.");
			PhantomAssertions.assertEquals(recordedBefore, _social.snapshot().recordedEvents(), "Clan dissolution became betrayal.");
			context.record("goal030c1.departureKinds", "VOLUNTARY,CLAN_DISSOLVED");
			context.record("goal030c1.nonExpulsionBetrayals", 0);
		}
		finally
		{
			captureListener.unregisterMe();
			cleanupClans();
		}
	}

	private void leaderTransfer(PhantomTestContext context) throws Exception
	{
		final Clan clan = sameClan();
		try
		{
			final L2jPhantomClanBackend backend = new L2jPhantomClanBackend(_profiles, _materialization);
			final MemberRef owner = MemberRef.phantom(_ownerProfile.profileId(), _owner.getObjectId());
			final MemberRef target = MemberRef.phantom(_targetProfile.profileId(), _target.getObjectId());
			final var before = backend.observe(owner).orElseThrow();
			PhantomAssertions.assertEquals(_owner.getObjectId(), before.leaderObjectId(), "ClanSnapshot did not expose the native leader before transfer.");
			final var transfer = backend.transferLeader(owner, target, clan.getId());
			PhantomAssertions.assertEquals(RoleOutcome.COMPLETED, transfer.outcome(), "Native leader transfer did not complete.");
			final var after = backend.observe(owner).orElseThrow();
			PhantomAssertions.assertEquals(_target.getObjectId(), after.leaderObjectId(), "ClanSnapshot did not change after native setNewLeader.");
			PhantomAssertions.assertEquals(_target.getObjectId(), clan.getLeaderId(), "Native Clan leader ID differs from snapshot.");
			PhantomAssertions.assertTrue(clan.getClanMember(_owner.getObjectId()) != null, "Former leader is no longer an ordinary member.");
			PhantomAssertions.assertFalse(_owner.isClanLeader(), "Former leader retained native leader truth.");
			context.record("goal030c1.leader.beforeAfter", before.leaderObjectId() + "->" + after.leaderObjectId());
		}
		finally
		{
			cleanupClans();
		}
	}

	private void lifecycleAndSourceBounds(PhantomTestContext context) throws Exception
	{
		cleanupClans();
		final CapturingSink sink = new CapturingSink();
		final PhantomClanSocialLifecycleObserver local = new PhantomClanSocialLifecycleObserver(_profiles, sink);
		PhantomAssertions.assertTrue(local.install(), "Local observer did not install.");
		PhantomAssertions.assertFalse(local.install(), "Local observer installed twice.");
		local.close();
		PhantomAssertions.assertFalse(local.installed(), "Local observer remained installed after close.");
		local.close();

		final String resolver = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/social/L2jPhantomSocialAffiliationContextResolver.java"));
		final String observer = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanSocialLifecycleObserver.java"));
		final String system = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
		final String party = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"));
		final String pvp = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java"));
		final String backend = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java"));
		final String withdrawal = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestWithdrawalPledge.java"));
		final String oust = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestOustPledgeMember.java"));
		final String clanTable = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/data/sql/ClanTable.java"));
		for (String forbidden : List.of("PhantomProfileRepository", "findByCharacterObjectId", "getClans()", "getPlayers()", "ThreadPool", "ScheduledFuture"))
		{
			PhantomAssertions.assertFalse(resolver.contains(forbidden), "Resolver contains forbidden unbounded/DB/background owner: " + forbidden);
		}
		for (String forbidden : List.of("ThreadPool", "ScheduledFuture", "ArrayBlockingQueue", "ConcurrentLinkedQueue"))
		{
			PhantomAssertions.assertFalse(observer.contains(forbidden), "Observer contains forbidden worker/queue owner: " + forbidden);
		}
		PhantomAssertions.assertTrue(system.contains("final PhantomSocialAffiliationContextPort socialAffiliations") && system.contains("new PhantomPvpSocialBridge(_socialService, socialAffiliations)") && system.contains("pvpPolicy, socialAffiliations)") && system.contains("new PhantomClanSocialLifecycleObserver"), "PhantomSystem does not share one production resolver/observer.");
		PhantomAssertions.assertTrue(party.contains("PhantomSocialAffiliationContextPort.noop()") && pvp.contains("PhantomSocialAffiliationContextPort.noop()") && backend.contains("PhantomSocialAffiliationContextPort.noop()"), "Backward-compatible producer constructors lost neutral context.");
		PhantomAssertions.assertTrue(withdrawal.contains("DepartureKind.VOLUNTARY, player.getObjectId()") && oust.contains("DepartureKind.EXPELLED, player.getObjectId()") && clanTable.contains("DepartureKind.CLAN_DISSOLVED, leaderObjectId"), "Canonical departure call-site metadata is incomplete.");
		PhantomAssertions.assertTrue(_clanIds.isEmpty(), "Focused suite retained tracked clans.");
		PhantomAssertions.assertTrue(_observer.installed(), "Production-shaped suite observer was unexpectedly uninstalled.");
		context.record("goal030c1.sourceBounds", "no-db-scan-worker-queue");
	}
	private PhantomPartyCoordinator partyCoordinator(PhantomSocialEventSink sink)
	{
		final PhantomPartyBackend backend = new InertPartyBackend();
		final PhantomPartyRoleCatalog roles = PhantomPartyRoleCatalog.load(_moduleRoot.resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
		return new PhantomPartyCoordinator(new PhantomPartyStore(_profiles), new PhantomGoalStateStore(_profiles), backend, roles, new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> ZERO, System::nanoTime, 64, sink, PhantomClanAffiliationHumanizationGoal030C1Suite::nowMinute, _resolver);
	}

	private void emitPartyTerminal(PhantomPartyCoordinator coordinator, long sequence)
	{
		final PartyInvitation invitation = new PartyInvitation(new InvitationIdentity(sequence, _owner.getObjectId(), _target.getObjectId()), _owner.getObjectId(), _owner.getName(), _target.getObjectId(), _target.getName(), PartyDistributionType.FINDERS_KEEPERS, _owner.getObjectId(), Long.MAX_VALUE);
		coordinator.terminal(invitation, OptionalLong.of(_ownerProfile.profileId()), OptionalLong.empty(), TerminalOutcome.REFUSED, "party.invite.refused");
		for (int pulse = 0; pulse < 4; pulse++)
		{
			coordinator.onPulse();
		}
	}

	private Clan sameClan()
	{
		cleanupClans();
		resetPenalties(_owner);
		resetPenalties(_target);
		final Clan clan = createClan(_owner);
		clan.addClanMember(_target);
		return clan;
	}

	private Clan[] separateClans(int level)
	{
		cleanupClans();
		resetPenalties(_owner);
		resetPenalties(_target);
		final Clan ownerClan = createClan(_owner);
		final Clan targetClan = createClan(_target);
		ownerClan.changeLevel(level);
		targetClan.changeLevel(level);
		return new Clan[]
		{
			ownerClan,
			targetClan
		};
	}

	private Clan createClan(Player leader)
	{
		final Clan clan = ClanTable.getInstance().createClan(leader, nextName("C"));
		PhantomAssertions.assertTrue(clan != null, "Canonical clan creation failed for " + leader.getName() + ".");
		_clanIds.add(clan.getId());
		return clan;
	}

	private AllianceIdentity createAlliance(Clan leaderClan, Clan targetClan)
	{
		final ClanAllianceService service = ClanAllianceService.getInstance();
		final var created = service.create(_owner, nextName("A"));
		PhantomAssertions.assertTrue(created.successful(), "Canonical alliance creation failed: " + created.reason());
		final var permit = service.checkInvite(_owner, _target);
		PhantomAssertions.assertTrue(permit.successful(), "Canonical alliance invite check failed: " + permit.reason());
		final var joined = service.join(_owner, _target, created.identity(), permit.targetEpoch());
		PhantomAssertions.assertTrue(joined.successful(), "Canonical alliance join failed: " + joined.reason());
		PhantomAssertions.assertEquals(leaderClan.getId(), targetClan.getAllyId(), "Target clan did not join the exact leader alliance.");
		return created.identity();
	}

	private void dissolveAlliance(AllianceIdentity identity)
	{
		if ((identity != null) && (_owner.getClan() != null))
		{
			final var result = ClanAllianceService.getInstance().dissolve(_owner, identity);
			PhantomAssertions.assertTrue(result.successful(), "Canonical alliance dissolution failed: " + result.reason());
		}
	}

	private static void endWars(Clan ownerClan, Clan targetClan)
	{
		if ((ownerClan == null) || (targetClan == null))
		{
			return;
		}
		final ClanWarService service = ClanWarService.getInstance();
		service.currentWar(ownerClan, targetClan).ifPresent(identity -> service.endAcceptedReply(ownerClan, targetClan, identity.warId()));
		service.currentWar(targetClan, ownerClan).ifPresent(identity -> service.endAcceptedReply(targetClan, ownerClan, identity.warId()));
	}

	private void cleanupClans()
	{
		for (int index = _clanIds.size() - 1; index >= 0; index--)
		{
			destroyClan(ClanTable.getInstance().getClan(_clanIds.get(index)));
		}
		_clanIds.clear();
		resetPenalties(_owner);
		resetPenalties(_target);
	}

	private void destroyClan(Clan clan)
	{
		if ((clan != null) && (ClanTable.getInstance().getClan(clan.getId()) != null))
		{
			ClanTable.getInstance().destroyClan(clan.getId());
		}
		if (clan != null)
		{
			_clanIds.remove(Integer.valueOf(clan.getId()));
		}
	}
	private void assertAffiliation(AffiliationKind expected, SubjectRef subject)
	{
		PhantomAssertions.assertEquals(expected, _resolver.resolve(_ownerProfile.profileId(), subject).affiliation(), "Unexpected canonical affiliation for " + subject.stableKey() + ".");
	}

	private boolean hasMemory(long profileId, SubjectRef subject, String eventKey)
	{
		final var snapshot = _social.snapshot(profileId, subject, 24, nowMinute());
		return snapshot.available() && snapshot.value().memories().stream().anyMatch(memory -> memory.eventKey().equals(eventKey));
	}

	private static void await(BooleanSupplier condition, String failure) throws InterruptedException
	{
		final long deadline = System.nanoTime() + 5_000_000_000L;
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private void stopSocial()
	{
		if ((_social != null) && (_social.snapshot().state() == PhantomSocialService.ServiceState.RUNNING))
		{
			_social.beginStop();
			PhantomAssertions.assertTrue(_social.finishStop(), "Goal030C1 social service did not drain.");
		}
	}

	private static void stopParty(PhantomPartyCoordinator coordinator)
	{
		if (coordinator != null)
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Goal030C1 Party coordinator did not drain.");
		}
	}

	private void deleteProfile(long profileId)
	{
		_profiles.find(profileId).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
	}

	private String nextName(String prefix)
	{
		return prefix + "30C1" + (++_nameSequence);
	}

	private static void resetPenalties(Player player)
	{
		if (player != null)
		{
			player.setClanJoinExpiryTime(0);
			player.setClanCreateExpiryTime(0);
		}
	}

	private static long nowMinute()
	{
		return System.currentTimeMillis() / 60000L;
	}

	private static final class CapturingSink implements PhantomSocialEventSink
	{
		private final List<SocialEvent> _events = new ArrayList<>();

		@Override
		public synchronized Result record(SocialEvent event)
		{
			_events.add(event);
			return new Result(Status.RECORDED, "captured");
		}

		private synchronized List<SocialEvent> events()
		{
			return List.copyOf(_events);
		}
	}

	private static final class InertPartyBackend implements PhantomPartyBackend
	{
		@Override
		public OptionalLong managedProfileId(int characterObjectId)
		{
			return OptionalLong.empty();
		}

		@Override
		public Optional<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef> currentMember(long profileId)
		{
			return Optional.empty();
		}

		@Override
		public InviteResult invite(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef requester, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef target, PartyDistributionType distribution)
		{
			throw new UnsupportedOperationException("Goal030C1 terminal fixture does not invite.");
		}

		@Override
		public RespondResult respond(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef invitee, Response response, InvitationIdentity identity)
		{
			throw new UnsupportedOperationException("Goal030C1 terminal fixture does not respond.");
		}

		@Override
		public MembershipOutcome leave(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef member)
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}

		@Override
		public MembershipOutcome expel(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef requester, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef member)
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}

		@Override
		public MembershipOutcome transferLeader(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef requester, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef member)
		{
			return MembershipOutcome.NOT_IN_PARTY;
		}

		@Override
		public Optional<PartySnapshot> observe(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef member)
		{
			return Optional.empty();
		}

		@Override
		public Optional<MemberSnapshot> memberSnapshot(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef member)
		{
			return Optional.empty();
		}

		@Override
		public List<MemberCapability> capabilities(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef actor, int exactTargetObjectId)
		{
			return List.of();
		}

		@Override
		public boolean materialize(long profileId)
		{
			return false;
		}
	}
}