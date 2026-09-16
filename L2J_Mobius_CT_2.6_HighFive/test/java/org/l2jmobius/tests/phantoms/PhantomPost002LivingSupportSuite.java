/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.Argument;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;

/** DB-free focused acceptance for POST-002 support, conversation and population contracts. */
public final class PhantomPost002LivingSupportSuite implements PhantomTestSuite
{
	public enum Mode
	{
		SUPPORT,
		CONVERSATION,
		POPULATION
	}

	private static final String ZERO = "0".repeat(64);
	private final Mode _mode;

	public PhantomPost002LivingSupportSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "post002-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case SUPPORT -> support(registry);
			case CONVERSATION -> conversation(registry);
			case POPULATION -> population(registry);
		}
	}

	private static void support(PhantomTestRegistry registry)
	{
		registry.add("01-policy-is-data-owned-and-bounded", context ->
		{
			final PhantomPartySupportPolicy policy = policy(context);
			PhantomAssertions.assertEquals(45, policy.rebuffRemainingSeconds(), "Rebuff window changed.");
			PhantomAssertions.assertEquals(2, policy.maximumMaintenanceDirectives(), "Maintenance bound changed.");
		});
		registry.add("02-human-member-and-priority-use-canonical-capabilities", context ->
		{
			final MemberRef actor = MemberRef.phantom(1, 101);
			final MemberRef human = MemberRef.real(202);
			final FakeBackend backend = new FakeBackend();
			backend.capabilities.put(202, List.of(capability("combat.heal", 1001), capability("combat.buff", 1002)));
			final List<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective> directives = new PhantomPartyTactics(null, backend, policy(context)).plan(actor, List.of(actor, human), Map.of(actor, snapshot(actor, 100, 100, false), human, snapshot(human, 20, 100, false)));
			PhantomAssertions.assertTrue(directives.stream().anyMatch(value -> (value.kind() == DirectiveKind.PARTY_SUPPORT) && value.targetMember().equals(human) && value.capabilityKey().equals("combat.buff")), "Real human member was not a support target.");
			PhantomAssertions.assertEquals(DirectiveKind.HEAL_MEMBER, directives.getFirst().kind(), "Critical heal did not outrank ordinary buff maintenance.");
		});
		registry.add("03-healthy-suppressed-near-expiry-admitted", context ->
		{
			final MemberRef actor = MemberRef.phantom(1, 101);
			final MemberRef human = MemberRef.real(202);
			final FakeBackend backend = new FakeBackend();
			backend.capabilities.put(202, List.of(capability("combat.buff", 1002)));
			backend.effectStatus = Status.HEALTHY;
			final PhantomPartyTactics tactics = new PhantomPartyTactics(null, backend, policy(context));
			PhantomAssertions.assertFalse(tactics.plan(actor, List.of(actor, human), Map.of(actor, snapshot(actor, 100, 100, false), human, snapshot(human, 100, 100, false))).stream().anyMatch(value -> value.kind() == DirectiveKind.PARTY_SUPPORT), "Healthy equal/stronger effect caused rebuff spam.");
			backend.effectStatus = Status.NEAR_EXPIRY;
			PhantomAssertions.assertTrue(tactics.plan(actor, List.of(actor, human), Map.of(actor, snapshot(actor, 100, 100, false), human, snapshot(human, 100, 100, false))).stream().anyMatch(value -> value.kind() == DirectiveKind.PARTY_SUPPORT), "Near-expiry effect did not admit bounded rebuff.");
		});
		registry.add("04-song-dance-maintenance-is-bounded", context ->
		{
			final MemberRef actor = MemberRef.phantom(1, 101);
			final MemberRef human = MemberRef.real(202);
			final FakeBackend backend = new FakeBackend();
			backend.capabilities.put(202, List.of(capability("combat.buff", 1001), capability("combat.song", 1002), capability("combat.dance", 1003)));
			final List<String> support = new PhantomPartyTactics(null, backend, policy(context)).plan(actor, List.of(actor, human), Map.of(actor, snapshot(actor, 100, 100, false), human, snapshot(human, 100, 100, false))).stream().filter(value -> value.kind() == DirectiveKind.PARTY_SUPPORT).map(value -> value.capabilityKey()).toList();
			PhantomAssertions.assertTrue((support.size() <= 2) && support.stream().allMatch(value -> List.of("combat.buff", "combat.song", "combat.dance").contains(value)), "Song/dance maintenance exceeded the data-owned cycle bound.");
		});
		registry.add("05-native-cast-and-raid-reuse-remain-owned", context ->
		{
			final String combat = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"), StandardCharsets.UTF_8);
			final String effects = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/combat/PhantomSupportEffectAuthority.java"), StandardCharsets.UTF_8);
			final String raid = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAttemptRuntime.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(combat.contains("checkDoCastConditions(skill)") && combat.contains("setIntention(Intention.CAST, skill, target)"), "Support bypassed native cast authority.");
			PhantomAssertions.assertTrue(effects.contains("getBuffInfoByAbnormalType") && effects.contains("getTime()") && effects.contains("getAbnormalLevel()"), "Effect-aware suppression is not based on native abnormal state.");
			PhantomAssertions.assertTrue(raid.contains("combat.song") && raid.contains("combat.dance") && raid.contains("_tactics.plan"), "Raid runtime did not reuse bounded party support authority.");
			PhantomAssertions.assertFalse(combat.contains("addSkillEffect") || effects.contains("addSkillEffect"), "Support introduced direct effect injection.");
		});
	}

	private static void conversation(PhantomTestRegistry registry)
	{
		registry.add("01-execution-v1-frozen-v2-support-executable", context ->
		{
			final var v1 = PhantomConversationExecutionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml"));
			final var v2 = PhantomConversationExecutionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v2.xml"));
			PhantomAssertions.assertEquals(Kind.DEFERRED, v1.proposal("party.support").kind(), "Frozen execution v1 changed.");
			PhantomAssertions.assertEquals(Kind.SUPPORT, v2.proposal("party.support").kind(), "Execution v2 did not make support bounded and executable.");
		});
		registry.add("02-party-and-real-relationship-policy", context ->
		{
			final PhantomPartySupportPolicy policy = policy(context);
			PhantomAssertions.assertTrue(L2jPhantomConversationExecutionPort.allowsRequestedSupport(true, RelationshipBand.HOSTILE, Map.of(), policy), "Relationship blocked normal same-party role support.");
			PhantomAssertions.assertTrue(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.TRUSTED, Map.of(), policy), "Trusted external requester was not substantially willing.");
			PhantomAssertions.assertTrue(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.FAMILIAR, Map.of(), policy), "Familiar external requester was not substantially willing.");
			PhantomAssertions.assertFalse(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.TENSE, Map.of("empathy", 10000), policy), "Tense requester received casual external support.");
			PhantomAssertions.assertFalse(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.HOSTILE, Map.of("empathy", 10000), policy), "Hostile requester received casual external support.");
			PhantomAssertions.assertFalse(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.UNKNOWN, Map.of("empathy", 10000), policy), "Missing relationship was replaced with invented mood.");
			PhantomAssertions.assertTrue(L2jPhantomConversationExecutionPort.allowsRequestedSupport(false, RelationshipBand.NEUTRAL, Map.of("empathy", policy.neutralEmpathyMinimum()), policy), "Neutral request did not use actual personality authority.");
		});
		registry.add("03-terminal-receipt-blocks-replay", context ->
		{
			final String hash = "A".repeat(64);
			final ExecutionEntry terminal = new ExecutionEntry(hash, hash, ChatType.WHISPER, new PhantomDomainRef("character.object", "200"), "ack.action_proposed", "neutral", "Усиление запущено.", "party.support", new PhantomDomainRef("character.object", "200"), List.of(new Argument("capability", "capability:combat.buff")), 10, 15, OutboundState.SENT, ActionState.COMPLETED, 0, 0, "support.issued", 1, 1, 11);
			final ExecutionState compacted = ExecutionState.empty(hash, 10).add(terminal).compact(hash);
			PhantomAssertions.assertTrue(compacted.contains(hash), "Terminal support receipt lost replay identity.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> compacted.add(terminal), "Support replay was admitted after durable receipt.");
		});
		registry.add("04-truthful-native-outcomes-are-rendered", context ->
		{
			final var catalog = PhantomConversationExecutionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v2.xml"));
			for (String reason : List.of("support.issued", "support.healthy", "support.refused", "support.unavailable"))
			{
				PhantomAssertions.assertFalse(catalog.render(reason, "neutral", null).isBlank(), "Support result is not truthfully renderable: " + reason);
			}
		});
	}

	private static void population(PhantomTestRegistry registry)
	{
		registry.add("01-career-weights-remain-exact-and-sum-100", context ->
		{
			final PhantomPopulationCatalog catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneId.of("UTC"));
			final Map<CareerArchetype, Integer> expected = Map.of(CareerArchetype.DAMAGE, 55, CareerArchetype.TANK, 8, CareerArchetype.HEALER, 8, CareerArchetype.ENHANCEMENT, 12, CareerArchetype.CONTROL, 7, CareerArchetype.ECONOMY, 10);
			final Map<CareerArchetype, Integer> actual = new HashMap<>();
			catalog.archetypes().forEach((key, value) -> actual.put(key, value.weight()));
			PhantomAssertions.assertEquals(expected, actual, "Population archetype weights changed.");
			PhantomAssertions.assertEquals(100, actual.values().stream().mapToInt(Integer::intValue).sum(), "Population archetype weights do not sum to 100.");
			final String docs = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/README.ru.md"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(docs.contains("ENHANCEMENT=12") && docs.contains("не означает") && docs.contains("третью профессию"), "Russian operator meaning for population weights is incomplete.");
		});
	}

	private static PhantomPartySupportPolicy policy(PhantomTestContext context)
	{
		return PhantomPartySupportPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-support-v1.xml"));
	}

	private static MemberCapability capability(String key, int skillId)
	{
		return new MemberCapability(key, key.substring("combat.".length()), 100, skillId, 1, "SINGLE_TARGET", true, true, true, "ready", 100, "post002.fixture");
	}

	private static MemberSnapshot snapshot(MemberRef ref, int hp, int mp, boolean dead)
	{
		return new MemberSnapshot(ref, 1, 0, 0, 0, 0, hp, mp, 100, dead, false, false, false, 0, List.of(), List.of(), ZERO);
	}

	private static final class FakeBackend implements PhantomPartyBackend
	{
		private final Map<Integer, List<MemberCapability>> capabilities = new HashMap<>();
		private Status effectStatus = Status.MISSING;

		@Override public OptionalLong managedProfileId(int characterObjectId) { return OptionalLong.empty(); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.empty(); }
		@Override public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { return null; }
		@Override public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity) { return null; }
		@Override public MembershipOutcome leave(MemberRef member) { return null; }
		@Override public MembershipOutcome expel(MemberRef requester, MemberRef member) { return null; }
		@Override public MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { return null; }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return Optional.empty(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return Optional.empty(); }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return capabilities.getOrDefault(exactTargetObjectId, List.of()); }
		@Override public Status supportEffectStatus(MemberRef actor, int exactTargetObjectId, MemberCapability capability, int rebuffRemainingSeconds) { return effectStatus; }
		@Override public boolean materialize(long profileId) { return false; }
	}
}
