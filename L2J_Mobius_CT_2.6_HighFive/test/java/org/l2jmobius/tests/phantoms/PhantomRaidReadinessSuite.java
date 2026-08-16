/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAuthority;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidReadinessService;

public final class PhantomRaidReadinessSuite implements PhantomTestSuite
{
	public enum Mode
	{
		AUTHORITY,
		FORCE,
		READINESS
	}

	private static final long SEED = 26002601L;
	private static final long NOW = 1_000_000L;
	private static final String HASH = "0".repeat(64);
	private final Mode _mode;
	private Path _temporaryRoot;
	private PhantomGameKnowledgeService _knowledgeService;
	private PhantomGameKnowledgeQuery _knowledge;
	private StubPartyBackend _party;
	private StubRaidAuthority _authority;
	private PhantomRaidReadinessService _service;

	public PhantomRaidReadinessSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "raid-readiness-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 026 CP1 used the wrong deterministic seed.");
		if (_mode == Mode.READINESS)
		{
			_temporaryRoot = context.reportsDirectory().resolve("raid-readiness-" + ProcessHandle.current().pid());
			Files.createDirectories(_temporaryRoot.resolve("curated"));
			Files.writeString(_temporaryRoot.resolve("Seeds.xml"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<list>
					<castle id="1">
						<crop id="2" seedId="1" mature_Id="3" reward1="4" reward2="5" alternative="false" level="10" limit_seed="100" limit_crops="200" />
					</castle>
				</list>
				""", StandardCharsets.UTF_8);
			Files.writeString(_temporaryRoot.resolve("curated/knowledge.xml"), curatedXml(), StandardCharsets.UTF_8);
			final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
			final PhantomGameKnowledgeCoreSuite.SyntheticBackend backend = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 25d, false, 0);
			_knowledgeService = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(_temporaryRoot.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(_temporaryRoot.resolve("curated"), backend, policy), PhantomGameKnowledgeCoreSuite.topology(), policy));
			PhantomAssertions.assertTrue(_knowledgeService.start(), "Raid readiness knowledge fixture did not start.");
			_knowledge = _knowledgeService.query();
			_party = new StubPartyBackend();
			_authority = new StubRaidAuthority();
			_service = new PhantomRaidReadinessService(_knowledge, _party, _authority);
		}
		context.record("raid.cp1.mode", _mode.name());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_knowledgeService != null)
		{
			_knowledgeService.beginStop();
			_knowledgeService.finishStop();
		}
		if ((_temporaryRoot != null) && Files.exists(_temporaryRoot))
		{
			try (var stream = Files.walk(_temporaryRoot))
			{
				for (Path path : stream.sorted(Collections.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case AUTHORITY -> authority(registry);
			case FORCE -> force(registry);
			case READINESS -> readiness(registry);
		}
	}

	private static void authority(PhantomTestRegistry registry)
	{
		registry.add("01-standard-raid-exact-status-policy", _ ->
		{
			PhantomAssertions.assertEquals(TargetAvailability.AVAILABLE, observation(ContentKind.RAID, 100, true, "ALIVE", true, true, false, 0L).availability(), "Exact live ALIVE raid was not available.");
			PhantomAssertions.assertEquals(TargetAvailability.UNAVAILABLE, observation(ContentKind.RAID, 100, true, "DEAD", false, false, false, NOW + 1000).availability(), "Scheduled DEAD raid was not unavailable.");
			PhantomAssertions.assertEquals(TargetAvailability.UNKNOWN, observation(ContentKind.RAID, 100, false, "UNDEFINED", false, false, false, null).availability(), "Undefined raid did not fail closed.");
			PhantomAssertions.assertEquals(TargetAvailability.UNKNOWN, observation(ContentKind.RAID, 100, true, "ALIVE", true, false, false, 0L).availability(), "Mismatched live raid identity became available.");
		});
		registry.add("02-epic-live-respawn-and-raw-status-policy", _ ->
		{
			PhantomAssertions.assertEquals(TargetAvailability.AVAILABLE, observation(ContentKind.EPIC, 101, true, "37", true, true, false, 0L).availability(), "Exact live GrandBoss was not available.");
			PhantomAssertions.assertEquals(TargetAvailability.UNAVAILABLE, observation(ContentKind.EPIC, 101, true, "91", false, false, false, NOW + 1000).availability(), "Future respawn without a live GrandBoss was not unavailable.");
			PhantomAssertions.assertEquals(TargetAvailability.UNKNOWN, observation(ContentKind.EPIC, 101, true, "0", false, false, false, 0L).availability(), "Raw epic status 0 was interpreted globally.");
			PhantomAssertions.assertEquals(TargetAvailability.UNKNOWN, observation(ContentKind.EPIC, 101, true, "91", false, false, false, 0L).availability(), "Raw epic status 91 was interpreted globally.");
		});
		registry.add("03-production-adapter-is-read-only", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAuthority.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(source.contains("getRaidBossStatusId") && source.contains("getBosses().get") && source.contains("isDefined") && source.contains("getStoredInfo") && source.contains("getBoss(npcId)") && source.contains("getStatus(npcId)") && source.contains("getStatSet(npcId)"), "Production raid authority does not use exact manager read truth.");
			for (String forbidden : List.of(".updateStatus(", ".deleteSpawn(", ".addNewSpawn(", ".setStatus(", ".addBoss(", ".setStatSet(", "ThreadPool", "ScheduledFuture"))
			{
				PhantomAssertions.assertFalse(source.contains(forbidden), "Production raid authority contains a mutation/scheduling seam: " + forbidden);
			}
		});
	}

	private static void force(PhantomTestRegistry registry)
	{
		registry.add("01-bounded-party-and-command-channel-model", _ ->
		{
			final MemberSnapshot leader = member(MemberRef.phantom(1, 100), capability("combat.tank", 900));
			final MemberSnapshot healer = member(MemberRef.real(200), capability("combat.heal", 900));
			final MemberSnapshot support = member(MemberRef.real(300), capability("combat.buff", 900));
			final PartySnapshot second = new PartySnapshot(support.ref(), List.of(support.ref()), PartyDistributionType.FINDERS_KEEPERS);
			final PartySnapshot first = new PartySnapshot(leader.ref(), List.of(leader.ref(), healer.ref()), PartyDistributionType.FINDERS_KEEPERS);
			final CurrentForceSnapshot snapshot = new CurrentForceSnapshot(leader.ref(), leader.ref(), "command-channel:100", leader.ref(), 80, 3, List.of(second, first), List.of(support, healer, leader));
			PhantomAssertions.assertTrue(snapshot.commandChannelPresent(), "CommandChannel presence was lost.");
			PhantomAssertions.assertEquals(3, snapshot.totalMemberCount(), "Current force member count changed.");
			PhantomAssertions.assertEquals(List.of(leader.ref(), healer.ref(), support.ref()), snapshot.members().stream().map(MemberSnapshot::ref).toList(), "Current force members are not deterministic.");
		});
		registry.add("02-unsupported-and-over-bound-observations-fail-closed", _ ->
		{
			final PhantomPartyBackend backend = new StubPartyBackend();
			PhantomAssertions.assertEquals(CurrentForceStatus.UNAVAILABLE, backend.currentForce(MemberRef.phantom(1, 100)).status(), "Unsupported current-force backend fabricated a group.");
			PhantomAssertions.assertEquals(CurrentForceStatus.BOUNDS_EXCEEDED, CurrentForceObservation.boundsExceeded().status(), "Over-bound current force did not fail closed.");
		});
		registry.add("03-production-snapshot-starts-from-exact-actor", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(source.contains("actorPlayer.getParty()") && source.contains("actorParty.getCommandChannel()") && source.contains("channel.getParties()") && source.contains("channel.getLeader()") && source.contains("channel.getLevel()"), "Goal017 current-force snapshot is missing exact Party/CommandChannel reads.");
			PhantomAssertions.assertFalse(source.contains("getPlayers()"), "Goal017 current-force snapshot scans unrelated world players.");
		});
	}

	private void readiness(PhantomTestRegistry registry)
	{
		registry.add("01-content-kind-pagination-and-mismatch", _ ->
		{
			final KnowledgePage<ContentRequirementFact> first = _service.contents(ContentKind.RAID, PageRequest.first(1));
			PhantomAssertions.assertEquals(List.of("raid.mismatch"), first.values().stream().map(ContentRequirementFact::contentId).toList(), "RAID first page order changed.");
			PhantomAssertions.assertTrue(first.hasMore(), "RAID first page lost its continuation.");
			final KnowledgePage<ContentRequirementFact> second = _service.contents(ContentKind.RAID, new PageRequest(1, first.nextCursor()));
			PhantomAssertions.assertEquals(List.of("raid.synthetic"), second.values().stream().map(ContentRequirementFact::contentId).toList(), "RAID cursor page is unstable.");
			PhantomAssertions.assertEquals(_knowledge.content("raid.synthetic").orElseThrow(), second.values().getFirst(), "Exact content truth changed during pagination.");
			_party.observation = readyForce(true, true, false);
			final int calls = _authority.calls;
			PhantomAssertions.assertEquals(ReadinessStatus.TARGET_UNKNOWN, _service.assess(actor(), "raid.mismatch").status(), "RAID content/NPC kind mismatch did not fail closed.");
			PhantomAssertions.assertEquals(calls, _authority.calls, "Content mismatch reached live boss authority.");
		});
		registry.add("02-target-availability-precedes-group-readiness", _ ->
		{
			_party.observation = readyForce(true, true, false);
			_authority.raid = observation(ContentKind.RAID, 100, true, "UNDEFINED", false, false, false, null);
			PhantomAssertions.assertEquals(ReadinessStatus.TARGET_UNKNOWN, _service.assess(actor(), "raid.synthetic").status(), "Unknown target allowed readiness.");
			_authority.raid = observation(ContentKind.RAID, 100, true, "DEAD", false, false, false, NOW + 1000);
			PhantomAssertions.assertEquals(ReadinessStatus.TARGET_UNAVAILABLE, _service.assess(actor(), "raid.synthetic").status(), "Unavailable target allowed readiness.");
		});
		registry.add("03-absent-incomplete-and-over-bound-groups", _ ->
		{
			_authority.raid = availableRaid();
			_party.observation = CurrentForceObservation.partyAbsent();
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_ABSENT, _service.assess(actor(), "raid.synthetic").status(), "Actor without Party did not report GROUP_ABSENT.");
			_party.observation = force(member(actor(), capability("combat.tank", 900), capability("combat.heal", 900)));
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCOMPLETE, _service.assess(actor(), "raid.synthetic").status(), "Below-minimum Party became ready.");
			_party.observation = CurrentForceObservation.boundsExceeded();
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCOMPLETE, _service.assess(actor(), "raid.synthetic").status(), "Over-bound force fabricated readiness.");
		});
		registry.add("04-required-tank-and-healer-gate-readiness", _ ->
		{
			_authority.raid = availableRaid();
			_party.observation = readyForce(false, true, false);
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCAPABLE, _service.assess(actor(), "raid.synthetic").status(), "Missing required tank became ready.");
			_party.observation = readyForce(true, false, false);
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCAPABLE, _service.assess(actor(), "raid.synthetic").status(), "Missing required healer became ready.");
		});
		registry.add("05-epic-resurrection-and-optional-capability-policy", _ ->
		{
			_authority.epic = observation(ContentKind.EPIC, 101, true, "arbitrary", true, true, false, 0L);
			_party.observation = readyForce(true, true, false);
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_INCAPABLE, _service.assess(actor(), "epic.synthetic").status(), "EPIC group without resurrection became ready.");
			_party.observation = readyForce(true, true, true);
			final var ready = _service.assess(actor(), "epic.synthetic");
			PhantomAssertions.assertEquals(ReadinessStatus.GROUP_READY, ready.status(), "All required EPIC capabilities did not become GROUP_READY.");
			PhantomAssertions.assertTrue(ready.groupReady(), "GROUP_READY flag changed.");
			PhantomAssertions.assertTrue(ready.capabilities().stream().filter(value -> value.requirement().required()).allMatch(value -> value.satisfied()), "Required capability evidence is incomplete.");
			PhantomAssertions.assertTrue(ready.capabilities().stream().anyMatch(value -> !value.requirement().required() && !value.satisfied()), "Optional absence was not preserved as evidence.");
		});
		registry.add("06-no-orchestration-or-victory-simulation", context ->
		{
			final Path root = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid");
			final StringBuilder source = new StringBuilder();
			try (var stream = Files.list(root))
			{
				for (Path file : stream.filter(path -> path.toString().endsWith(".java")).sorted().toList())
				{
					source.append(Files.readString(file, StandardCharsets.UTF_8));
				}
			}
			for (String forbidden : List.of("new CommandChannel", ".addParty(", ".removeParty(", ".disbandChannel(", ".invite(", "Navigation", "Combat", "ThreadPool", "ScheduledFuture", "DPS", "damage", "victory"))
			{
				PhantomAssertions.assertFalse(source.toString().contains(forbidden), "Raid CP1 crossed an orchestration boundary: " + forbidden);
			}
			final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(system.contains("new PhantomRaidReadinessService") && system.contains("new L2jPhantomRaidAuthority"), "Passive production raid readiness seam is not constructed.");
		});
	}

	private static BossObservation availableRaid()
	{
		return observation(ContentKind.RAID, 100, true, "ALIVE", true, true, false, 0L);
	}

	private static BossObservation observation(ContentKind kind, int npcId, boolean defined, String rawStatus, boolean live, boolean exact, boolean dead, Long respawn)
	{
		return new BossObservation(kind, npcId, defined, rawStatus, live, exact, dead, respawn, NOW, "test.authority");
	}

	private static MemberRef actor()
	{
		return MemberRef.phantom(1, 100);
	}

	private static CurrentForceObservation readyForce(boolean tank, boolean healer, boolean resurrection)
	{
		final List<MemberCapability> leaderCapabilities = tank ? List.of(capability("combat.tank", 1000)) : List.of();
		final List<MemberCapability> healerCapabilities = healer ? List.of(capability("combat.heal", 1000)) : List.of();
		final List<MemberCapability> supportCapabilities = resurrection ? List.of(capability("combat.resurrection", 1000)) : List.of();
		return force(member(actor(), leaderCapabilities.toArray(MemberCapability[]::new)), member(MemberRef.real(200), healerCapabilities.toArray(MemberCapability[]::new)), member(MemberRef.real(300), supportCapabilities.toArray(MemberCapability[]::new)));
	}

	private static CurrentForceObservation force(MemberSnapshot... members)
	{
		final List<MemberSnapshot> snapshots = List.of(members);
		final List<MemberRef> references = snapshots.stream().map(MemberSnapshot::ref).toList();
		final PartySnapshot party = new PartySnapshot(references.getFirst(), references, PartyDistributionType.FINDERS_KEEPERS);
		return CurrentForceObservation.available(new CurrentForceSnapshot(references.getFirst(), references.getFirst(), "", null, 0, references.size(), List.of(party), snapshots));
	}

	private static MemberSnapshot member(MemberRef reference, MemberCapability... capabilities)
	{
		return new MemberSnapshot(reference, 1, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(capabilities), HASH);
	}

	private static MemberCapability capability(String key, int rank)
	{
		return new MemberCapability(key, "test", rank, 500, 1, "SELF", true, true, true, "ready", rank, "test.fixture");
	}

	private static String curatedXml()
	{
		final StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<knowledge schemaVersion=\"1\" datasetId=\"raid-readiness\" datasetVersion=\"1\">\n");
		for (String capability : PhantomGameKnowledgeBuilder.REQUIRED_CAPABILITIES.stream().sorted().toList())
		{
			result.append("\t<classCapability classId=\"1\" capabilityKey=\"").append(capability).append("\" rank=\"1000\">\n\t\t<skill id=\"500\" level=\"1\" />\n\t\t<source path=\"data/source.xml\" />\n\t</classCapability>\n");
		}
		result.append("""
				<contentRequirement contentId="rift.synthetic" contentKind="RIFT" recommendedMinParty="1" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="raid.mismatch" contentKind="RAID" npcId="101" recommendedMinParty="1" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="raid.synthetic" contentKind="RAID" npcId="100" recommendedMinParty="3" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="850" required="true" />
					<requirement capabilityKey="combat.buff" minimumCount="1" minimumRank="800" required="false" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="epic.synthetic" contentKind="EPIC" npcId="101" recommendedMinParty="3" recommendedMaxParty="45">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="850" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="900" required="true" />
					<requirement capabilityKey="combat.resurrection" minimumCount="1" minimumRank="900" required="true" />
					<requirement capabilityKey="combat.buff" minimumCount="1" minimumRank="850" required="false" />
					<source path="data/source.xml" />
				</contentRequirement>
			</knowledge>
			""");
		return result.toString();
	}

	private static final class StubRaidAuthority implements PhantomRaidAuthority
	{
		private BossObservation raid = availableRaid();
		private BossObservation epic = observation(ContentKind.EPIC, 101, true, "test", true, true, false, 0L);
		private int calls;

		@Override
		public BossObservation observe(ContentKind contentKind, int npcId)
		{
			calls++;
			return contentKind == ContentKind.RAID ? raid : epic;
		}
	}

	private static class StubPartyBackend implements PhantomPartyBackend
	{
		private CurrentForceObservation observation = CurrentForceObservation.unavailable("test.unavailable");

		@Override public OptionalLong managedProfileId(int characterObjectId) { return OptionalLong.empty(); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.empty(); }
		@Override public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { throw new UnsupportedOperationException(); }
		@Override public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome leave(MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome expel(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return Optional.empty(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return Optional.empty(); }
		@Override public CurrentForceObservation currentForce(MemberRef actor) { return observation; }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return List.of(); }
		@Override public boolean materialize(long profileId) { return false; }
	}
}
