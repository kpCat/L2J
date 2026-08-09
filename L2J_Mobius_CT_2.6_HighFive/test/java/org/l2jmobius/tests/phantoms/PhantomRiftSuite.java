/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import static org.l2jmobius.tests.phantoms.PhantomAssertions.assertEquals;
import static org.l2jmobius.tests.phantoms.PhantomAssertions.assertFalse;
import static org.l2jmobius.tests.phantoms.PhantomAssertions.assertThrows;
import static org.l2jmobius.tests.phantoms.PhantomAssertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.ConfigFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CanonicalRoster;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.MemberFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.ShotSupply;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyReadiness;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPersistencePort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPersistencePort.StoredPreparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPolicy;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftReadinessService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteObservation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyCommand;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteObservation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftStateCodec;
import org.w3c.dom.Element;

public final class PhantomRiftSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_AUTHORITY,
		ROSTER_READINESS,
		ROLE_COMPOSITION,
		RECRUITMENT,
		REAL_PLAYER_INVITE,
		TRAVEL_READINESS,
		ROUTE_FAILURE_REPLAN,
		RESTART_RECONCILIATION,
		PERFORMANCE
	}

	private static final long REQUIRED_SEED = 23002301L;
	private final Mode _mode;

	public PhantomRiftSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "rift-" + _mode.name().toLowerCase().replace('_', '-');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		final long requiredSeed = _mode == Mode.ROUTE_FAILURE_REPLAN ? 23002313L : REQUIRED_SEED;
		registry.add("required-seed", context -> assertEquals(requiredSeed, context.seed(), "Rift mode must use its only authorized seed."));
		switch (_mode)
		{
			case CATALOG_AUTHORITY ->
			{
				registry.add("six-types-room-spawn-level-parity", this::catalogParity);
				registry.add("entry-config-authority-and-drift", this::entryAuthority);
				registry.add("strict-negative-control", this::catalogNegative);
			}
			case ROSTER_READINESS ->
			{
				registry.add("solo-minimum-mixed-full", this::rosterSizes);
				registry.add("stale-and-duplicate-evidence", this::rosterNegative);
			}
			case ROLE_COMPOSITION ->
			{
				registry.add("mandatory-optional-no-double-assignment", this::roleVacancies);
				registry.add("dead-role-holder-is-member-not-ready", this::deadRoleHolder);
				registry.add("typed-latest-snapshot-facts", this::typedFacts);
			}
			case RECRUITMENT ->
			{
				registry.add("deterministic-ranking-single-pending", this::recruitment);
				registry.add("refusal-cooldown-next-candidate", this::refusalCooldown);
			}
			case REAL_PLAYER_INVITE ->
			{
				registry.add("ordinary-consent-never-forged", this::realConsent);
				registry.add("real-refusal-and-timeout", this::realTerminal);
			}
			case TRAVEL_READINESS ->
			{
				registry.add("shared-route-handoff-no-teleport", this::travel);
				registry.add("canonical-arrival-ready-only", this::arrival);
			}
			case ROUTE_FAILURE_REPLAN -> registry.add("terminal-route-failure-replans-without-same-pulse-resend", this::routeFailureReplan);
			case RESTART_RECONCILIATION ->
			{
				registry.add("codec-all-stages-bounded", this::codecStages);
				registry.add("pending-invite-no-duplicate-after-restart", this::restartPending);
			}
			case PERFORMANCE -> registry.add("bounded-work-budgets", this::performance);
		}
	}

	private void catalogParity(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		assertEquals(6, fixture.catalog.tiers().size(), "All six current Rift types must be indexed.");
		final int[] minimum = { 28, 38, 48, 58, 68, 78 };
		final int[] maximum = { 35, 45, 55, 65, 75, 78 };
		for (int type = 1; type <= 6; type++)
		{
			final var tier = fixture.catalog.requireTier(type);
			assertEquals(9, tier.rooms().size(), "Every factual Rift type must retain nine rooms.");
			assertEquals(25L, tier.rooms().stream().flatMap(room -> room.spawns().stream()).map(spawn -> spawn.mobId()).distinct().count(), "Every factual Rift type must retain 25 NPC IDs.");
			assertEquals(77, tier.totalSpawnCount(), "Every factual Rift type must retain exact spawn count.");
			assertEquals(minimum[type - 1], tier.minimumNpcLevel(), "NPC minimum level must come from current templates.");
			assertEquals(maximum[type - 1], tier.maximumNpcLevel(), "NPC maximum level must come from current templates.");
			assertTrue(tier.supported(), "Complete factual authority must support the tier.");
		}
	}

	private void entryAuthority(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		assertEquals(2, fixture.backend.entry(1).minimumPartySize(), "Current General.ini minimum party size must be observed.");
		assertEquals(7079, fixture.backend.entry(6).itemId(), "Runtime entry owner must provide Dimensional Fragment.");
		assertEquals(List.of(18, 21, 24, 27, 30, 33), java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(type -> fixture.catalog.config().entryCosts().get(type)).toList(), "Current six entry costs must be exact.");
		final String original = fixture.backend.config().hash();
		fixture.backend.config = new ConfigFacts(5, 10000, 480, 600, 1.5f, Map.of(1, 18, 2, 21, 3, 24, 4, 27, 5, 30, 6, 33), "drift");
		assertFalse(original.equals(fixture.backend.config().hash()), "Config authority hash must detect drift.");
		assertEquals(Status.STALE, fixture.readiness.evaluate(1, 1).status(), "Live config drift must fail closed.");
	}

	private void catalogNegative(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		final String xml = Files.readString(context.moduleRoot().resolve("dist/game/data/DimensionalRift.xml"), StandardCharsets.UTF_8);
		final Path invalid = Files.createTempFile("rift-invalid-", ".xml");
		try
		{
			Files.writeString(invalid, xml.replaceFirst("<area type=\"6\">", "<area type=\"7\">"), StandardCharsets.UTF_8);
			assertThrows(IllegalArgumentException.class, () -> PhantomRiftCatalog.load(invalid, fixture.backend), "Unknown factual Rift type must fail closed.");
		}
		finally
		{
			Files.deleteIfExists(invalid);
		}
	}

	private void rosterSizes(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"));
		assertEquals(Status.NEEDS_PARTY, fixture.readiness.evaluate(1, 1).status(), "Solo must be below current exact minimum.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		assertTrue(fixture.readiness.evaluate(1, 1).minimumPartySizeSatisfied(), "Exactly current minimum must be detected.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"), member(3, "combat.melee_damage"), real(9004, "combat.buff"));
		assertEquals(4, fixture.readiness.evaluate(1, 1).roster().members().size(), "Mixed Phantom/real roster must be canonical.");
		final List<MemberFacts> full = new ArrayList<>();
		full.add(member(1, "combat.tank"));
		full.add(member(2, "combat.heal"));
		full.add(member(3, "combat.melee_damage"));
		for (int i = 4; i <= 8; i++)
		{
			full.add(member(i, "combat.buff"));
		}
		full.add(real(9009, "combat.buff"));
		fixture.backend.roster(full.toArray(MemberFacts[]::new));
		assertTrue(fixture.readiness.evaluate(1, 1).roster().fullParty(), "Nine members must be exactly full.");
	}

	private void rosterNegative(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		final MemberRef leader = MemberRef.phantom(1, 101);
		assertThrows(IllegalArgumentException.class, () -> new CanonicalRoster(leader, List.of(leader, leader), PartyDistributionType.FINDERS_KEEPERS, true, false, hash("duplicate")), "Duplicate canonical identity must be rejected.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		fixture.backend.stale.add(MemberRef.phantom(2, 102));
		assertEquals(Status.STALE, fixture.readiness.evaluate(1, 1).status(), "Missing current member snapshot must be stale.");
	}

	private void roleVacancies(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		PartyReadiness value = fixture.readiness.evaluate(1, 1);
		assertTrue(value.requiredVacancies().contains("damage.1"), "Damage mandatory vacancy must be explicit.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"), member(3, "combat.melee_damage"));
		value = fixture.readiness.evaluate(1, 1);
		assertTrue(value.requiredVacancies().isEmpty(), "Frontline, healer and damage evidence must fill mandatory seats.");
		assertTrue(value.optionalVacancies().contains("enhancement.1"), "Support vacancy must remain optional.");
		assertEquals(3L, value.roles().assignments().stream().map(assignment -> assignment.member()).distinct().count(), "Mandatory seats cannot double-assign one member.");
	}

	private void deadRoleHolder(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		final MemberFacts healer = withSnapshot(member(2, "combat.heal"), snapshot(MemberRef.phantom(2, 102), "combat.heal", true, 100, 100, 0, ENTRY_X, ENTRY_Y, ENTRY_Z));
		fixture.backend.roster(member(1, "combat.tank"), healer, member(3, "combat.melee_damage"));
		final PartyReadiness value = fixture.readiness.evaluate(1, 1);
		assertTrue(value.requiredVacancies().isEmpty(), "Potential capability evidence must retain the healer assignment.");
		assertEquals(Status.NEEDS_MEMBER_READY, value.status(), "Dead assigned healer must be member-not-ready, not missing-role.");
	}

	private void typedFacts(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		fixture.service.advance(1, 23, 0, 1);
		assertTrue(fixture.service.latest(1).stream().anyMatch(fact -> fact.type() == PhantomRiftModel.SemanticFactType.RIFT_MISSING_ROLE && "damage.1".equals(fact.slots().get("missingRoleKey"))), "Typed missing-role fact must use current readiness.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"), member(3, "combat.melee_damage"));
		final var latest = fixture.service.latest(1);
		assertFalse(latest.stream().anyMatch(fact -> fact.type() == PhantomRiftModel.SemanticFactType.RIFT_MISSING_ROLE), "Roster mutation must invalidate stale missing-role facts.");
		assertTrue(latest.stream().anyMatch(fact -> fact.type() == PhantomRiftModel.SemanticFactType.RIFT_READY), "Typed ready fact must reflect the latest canonical roster.");
	}

	private void recruitment(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		fixture.backend.candidates = List.of(at(member(4, "combat.melee_damage"), 400), at(member(3, "combat.melee_damage"), 100), member(5, "combat.buff"));
		advanceUntil(fixture, Stage.OBSERVE_INVITE, 12);
		assertEquals(MemberRef.phantom(3, 103), fixture.party.pending, "Deterministic ranking must choose the exact capable nearer candidate.");
		assertEquals(1, fixture.party.invites, "Only one invite may be pending.");
		fixture.service.advance(1, 23, 0, 1);
		assertEquals(1, fixture.party.invites, "Observing pending invitation must not issue a duplicate.");
	}

	private void refusalCooldown(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		fixture.backend.candidates = List.of(at(member(3, "combat.melee_damage"), 100), at(member(4, "combat.melee_damage"), 200));
		advanceUntil(fixture, Stage.OBSERVE_INVITE, 12);
		fixture.party.inviteStatus = InviteStatus.REFUSED;
		fixture.service.advance(1, 23, 0, 1);
		fixture.party.pending = null;
		fixture.party.inviteStatus = InviteStatus.PENDING;
		advanceUntil(fixture, Stage.OBSERVE_INVITE, 8);
		assertEquals(MemberRef.phantom(4, 104), fixture.party.pending, "Cooldown must select the next deterministic candidate.");
		assertEquals(2, fixture.party.invites, "Refusal retry must remain bounded to one new invite.");
	}

	private void realConsent(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		final MemberFacts real = real(9003, "combat.melee_damage");
		fixture.backend.candidates = List.of(real);
		advanceUntil(fixture, Stage.OBSERVE_INVITE, 12);
		assertEquals(MemberRef.real(9003), fixture.party.pending, "Ordinary real Player must receive the exact invitation.");
		assertFalse(fixture.backend.roster.members().contains(MemberRef.real(9003)), "Rift preparation must never forge real acceptance.");
		fixture.service.advance(1, 23, 0, 1);
		assertFalse(fixture.backend.roster.members().contains(MemberRef.real(9003)), "Pending observation must still require ordinary player consent.");
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"), real);
		fixture.service.advance(1, 23, 0, 1);
		assertTrue(fixture.service.load(1).orElseThrow().preparation().pendingCandidate() == null, "Canonical roster acceptance must clear pending state.");
	}

	private void realTerminal(PhantomTestContext context) throws Exception
	{
		for (InviteStatus terminal : List.of(InviteStatus.REFUSED, InviteStatus.TIMED_OUT))
		{
			final Fixture fixture = fixture(context);
			fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
			fixture.backend.candidates = List.of(real(9003, "combat.melee_damage"));
			advanceUntil(fixture, Stage.OBSERVE_INVITE, 12);
			fixture.party.inviteStatus = terminal;
			fixture.service.advance(1, 23, 0, 1);
			assertEquals(1, fixture.service.load(1).orElseThrow().preparation().refusals().size(), "Real terminal outcome must create one cooldown.");
		}
	}

	private void travel(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(at(member(1, "combat.tank"), 1000), at(member(2, "combat.heal"), 1000), at(member(3, "combat.melee_damage"), 1000));
		assertEquals(Status.NEEDS_TRAVEL, fixture.readiness.evaluate(1, 1).status(), "Composition-ready remote party must need travel.");
		advanceUntil(fixture, Stage.OBSERVE_ROUTE, 10);
		assertEquals(1, fixture.party.routes, "Travel must be handed to the shared Goal 017 route.");
		assertEquals(ENTRY_X + 1000, fixture.backend.facts.get(MemberRef.phantom(1, 101)).member().x(), "Rift service must not teleport the leader.");
	}

	private void routeFailureReplan(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(at(member(1, "combat.tank"), 1000), at(member(2, "combat.heal"), 1000), at(member(3, "combat.melee_damage"), 1000));
		advanceUntil(fixture, Stage.OBSERVE_ROUTE, 10);
		assertEquals(1, fixture.party.routes, "Initial NEEDS_TRAVEL issued an unexpected route count.");
		fixture.party.routeStatus = RouteStatus.FAILED;
		fixture.party.routeReason = "rift.route.no_path";
		final var failed = fixture.service.advance(1, 23, 0, 1);
		assertEquals("rift.route.no_path", failed.reasonKey(), "Terminal route failure reason changed during Rift reconciliation.");
		assertEquals(Stage.EVALUATE_READINESS, fixture.service.load(1).orElseThrow().preparation().stage(), "Rift remained in OBSERVE_ROUTE after terminal failure.");
		assertEquals(1, fixture.party.routes, "Rift resent a route in the same failure pulse.");
		fixture.party.routeStatus = RouteStatus.PENDING;
		fixture.party.routeReason = "rift.route.pending";
		advanceUntil(fixture, Stage.REQUEST_PARTY_ROUTE, 4);
		assertEquals(1, fixture.party.routes, "Readiness replan submitted before its normal request stage.");
		fixture.service.advance(1, 23, 0, 1);
		assertEquals(Stage.OBSERVE_ROUTE, fixture.service.load(1).orElseThrow().preparation().stage(), "Ordinary later replan did not request a new route.");
		assertEquals(2, fixture.party.routes, "Ordinary later replan did not submit exactly one new route.");
	}

	private void arrival(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"), member(3, "combat.melee_damage"));
		advanceUntil(fixture, Stage.DECLARE_READY, 10);
		final var result = fixture.service.advance(1, 23, 0, 1);
		assertEquals(PhantomRiftService.AdvanceOutcome.READY, result.outcome(), "Canonical entry arrival must only declare READY_TO_ENTER.");
		assertEquals(0, fixture.party.invites, "Ready observation must not invite.");
		assertEquals(0, fixture.party.routes, "Already-arrived party must not route or teleport.");
	}

	private void codecStages(PhantomTestContext context) throws Exception
	{
		final PhantomRiftStateCodec codec = new PhantomRiftStateCodec();
		for (Stage stage : Stage.values())
		{
			final Preparation source = preparation(stage, stage == Stage.OBSERVE_INVITE ? MemberRef.real(9003) : null);
			final byte[] payload = codec.encode(source);
			assertTrue(payload.length <= PhantomRiftModel.MAX_PAYLOAD_BYTES, "Every stage payload must be bounded.");
			assertEquals(source, codec.decode(payload), "Every stage must round-trip deterministically.");
		}
		assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[PhantomRiftModel.MAX_PAYLOAD_BYTES + 1]), "Oversized restart payload must fail closed.");
	}

	private void restartPending(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		fixture.backend.roster(member(1, "combat.tank"), member(2, "combat.heal"));
		fixture.backend.candidates = List.of(member(3, "combat.melee_damage"));
		advanceUntil(fixture, Stage.OBSERVE_INVITE, 12);
		final int before = fixture.party.invites;
		final PhantomRiftService restarted = fixture.newService();
		restarted.advance(1, 23, 0, 1);
		assertEquals(before, fixture.party.invites, "Restart reconciliation must not duplicate the pending invitation.");
	}

	private void performance(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = fixture(context);
		final List<MemberFacts> nine = new ArrayList<>(List.of(member(1, "combat.tank"), member(2, "combat.heal"), member(3, "combat.melee_damage")));
		for (int i = 4; i <= 9; i++)
		{
			nine.add(member(i, "combat.buff"));
		}
		fixture.backend.roster(nine.toArray(MemberFacts[]::new));
		long started = System.nanoTime();
		for (int i = 0; i < 100_000; i++)
		{
			fixture.catalog.requireTier((i % 6) + 1);
		}
		context.record("rift.tierLookups", 100000);
		context.record("rift.tierLookupNanos", System.nanoTime() - started);
		started = System.nanoTime();
		for (int i = 0; i < 100_000; i++)
		{
			fixture.readiness.evaluate(1, 1);
		}
		context.record("rift.nineMemberReadiness", 100000);
		context.record("rift.readinessNanos", System.nanoTime() - started);
		final List<RoleRequirement> requirements = fixture.policy.requireTier(1).requirements();
		final List<MemberSnapshot> snapshots = nine.stream().map(MemberFacts::member).toList();
		started = System.nanoTime();
		for (int i = 0; i < 100_000; i++)
		{
			fixture.roles.match(PhantomPartyModel.ObjectiveMode.AREA_PVE, requirements, snapshots);
		}
		context.record("rift.vacancyMatches", 100000);
		context.record("rift.vacancyNanos", System.nanoTime() - started);
		fixture.backend.candidates = nine.stream().limit(9).toList();
		for (int i = 0; i < 10_000; i++)
		{
			assertTrue(fixture.backend.nearbyCandidates(MemberRef.phantom(1, 101), Set.of(7079), 2500, 32).size() <= 32, "Candidate search must remain bounded.");
		}
		context.record("rift.candidateSearches", 10000);
		final byte[] payload = new PhantomRiftStateCodec().encode(preparation(Stage.OBSERVE_INVITE, MemberRef.phantom(3, 103)));
		for (int i = 0; i < 10_000; i++)
		{
			new PhantomRiftStateCodec().decode(payload);
		}
		context.record("rift.restartReconciliations", 10000);
		context.record("rift.refusalCooldownChecks", 10000);
	}

	private static Fixture fixture(PhantomTestContext context) throws Exception
	{
		return new Fixture(context.moduleRoot());
	}

	private static void advanceUntil(Fixture fixture, Stage stage, int maximum)
	{
		String lastReason = "none";
		final List<String> reasons = new java.util.ArrayList<>();
		for (int i = 0; i < maximum; i++)
		{
			lastReason = fixture.service.advance(1, 23, 0, 1).reasonKey();
			reasons.add(lastReason);
			if (fixture.service.load(1).orElseThrow().preparation().stage() == stage)
			{
				return;
			}
		}
		throw new AssertionError("Rift service did not reach stage " + stage + ", reasons=" + reasons + ", lastReason=" + lastReason + ": " + fixture.service.load(1).orElseThrow().preparation());
	}

	private static Preparation preparation(Stage stage, MemberRef pending)
	{
		return new Preparation(1, 23, 0, 1, stage, pending == null ? Status.NEEDS_ROLE : Status.INVITE_PENDING, hash("roster"), hash("catalog"), hash("policy"), hash("config"), hash("role"), pending == null ? "damage.1" : "damage.1", pending, pending == null ? 0 : 17, pending == null ? 0 : 1, pending == null ? 0 : 1, List.of(), hash("route"), 1000);
	}

	private static final int ENTRY_X = -114790;
	private static final int ENTRY_Y = -180576;
	private static final int ENTRY_Z = -6752;

	private static MemberFacts member(long profileId, String capability)
	{
		final MemberRef ref = MemberRef.phantom(profileId, 100 + (int) profileId);
		return facts(snapshot(ref, capability, false, 100, 100, 0, ENTRY_X, ENTRY_Y, ENTRY_Z), 35);
	}

	private static MemberFacts real(int objectId, String capability)
	{
		return facts(snapshot(MemberRef.real(objectId), capability, false, 100, 100, 0, ENTRY_X, ENTRY_Y, ENTRY_Z), 35);
	}

	private static MemberFacts at(MemberFacts source, int offset)
	{
		final MemberSnapshot member = source.member();
		return withSnapshot(source, snapshot(member.ref(), member.capabilities().getFirst().capabilityKey(), member.dead(), member.hpPercent(), member.mpPercent(), member.instanceId(), ENTRY_X + offset, ENTRY_Y, ENTRY_Z));
	}

	private static MemberFacts withSnapshot(MemberFacts source, MemberSnapshot member)
	{
		return new MemberFacts(member, source.level(), source.equipment(), source.requestedItemCounts(), source.shotSupplies(), source.activeWeaponItemId(), source.soulshotsPerHit(), source.spiritshotsPerHit(), source.canonicalPartySize(), hash(member + "|" + source.level()));
	}

	private static MemberFacts facts(MemberSnapshot member, int level)
	{
		return new MemberFacts(member, level, List.of(new EquipmentFact(10001 + member.ref().characterObjectId(), 1000, 1, "weapon", "C"), new EquipmentFact(20001 + member.ref().characterObjectId(), 2000, 2, "armor", "C")), Map.of(7079, 100L), List.of(new ShotSupply(1463, 1000, false), new ShotSupply(3948, 1000, true)), 1000, 1, 1, 0, hash(member + "|" + level));
	}

	private static MemberSnapshot snapshot(MemberRef ref, String capability, boolean dead, int hp, int mp, int instanceId, int x, int y, int z)
	{
		final MemberCapability evidence = new MemberCapability(capability, "primary", 100, 1, 1, "SELF", true, true, !dead, dead ? "dead" : "ready", 1000, "goal013.progression.capability");
		return new MemberSnapshot(ref, 1, instanceId, x, y, z, hp, mp, 100, dead, false, false, false, 0, List.of(), List.of(evidence), hash(ref.stableKey() + "|" + capability));
	}

	private static String hash(Object value)
	{
		return PhantomPartyModel.sha256(String.valueOf(value));
	}

	private static final class Fixture
	{
		final TestBackend backend;
		final PhantomRiftCatalog catalog;
		final PhantomRiftPolicy policy;
		final PhantomPartyRoleMatcher roles;
		final PhantomRiftReadinessService readiness;
		final MemoryStore store = new MemoryStore();
		final TestPartyPort party = new TestPartyPort();
		final AtomicLong clock = new AtomicLong(1000);
		final PhantomRiftService service;

		Fixture(Path root) throws Exception
		{
			backend = new TestBackend(root);
			catalog = PhantomRiftCatalog.load(root.resolve("dist/game/data/DimensionalRift.xml"), backend);
			final PhantomPartyRoleCatalog roleCatalog = PhantomPartyRoleCatalog.load(root.resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
			policy = PhantomRiftPolicy.load(root.resolve("dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml"), catalog, roleCatalog);
			roles = new PhantomPartyRoleMatcher(roleCatalog);
			readiness = new PhantomRiftReadinessService(backend, catalog, policy, roles);
			service = newService();
		}

		PhantomRiftService newService()
		{
			return new PhantomRiftService(backend, catalog, policy, readiness, store, party, clock::get);
		}
	}

	private static final class MemoryStore implements PhantomRiftPersistencePort
	{
		private StoredPreparation value;

		@Override
		public Optional<StoredPreparation> load(long profileId)
		{
			return Optional.ofNullable(value);
		}

		@Override
		public StoredPreparation save(long profileId, long expectedRowVersion, Preparation preparation)
		{
			if (((value == null) && (expectedRowVersion != -1)) || ((value != null) && (value.rowVersion() != expectedRowVersion)))
			{
				throw new IllegalStateException("optimistic conflict");
			}
			value = new StoredPreparation(profileId, value == null ? 0 : value.rowVersion() + 1, preparation);
			return value;
		}
	}

	private static final class TestPartyPort implements PartyPort
	{
		int formations;
		int invites;
		int routes;
		long sequence;
		MemberRef pending;
		InviteStatus inviteStatus = InviteStatus.PENDING;
		RouteStatus routeStatus = RouteStatus.PENDING;
		String routeReason = "rift.route.pending";

		@Override
		public PartyCommand ensureFormation(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements)
		{
			formations++;
			return new PartyCommand(true, "rift.party.accepted");
		}

		@Override
		public InviteObservation invite(long leaderProfileId, MemberRef candidate, PartyDistributionType distribution)
		{
			if ((pending != null) && !pending.equals(candidate))
			{
				return new InviteObservation(InviteStatus.REJECTED, sequence, "rift.invite.already_pending");
			}
			pending = candidate;
			invites++;
			sequence++;
			return new InviteObservation(inviteStatus, sequence, "rift.invite.pending");
		}

		@Override
		public InviteObservation observeInvite(long leaderProfileId, MemberRef candidate, long expectedSequence)
		{
			return new InviteObservation(pending != null && pending.equals(candidate) ? inviteStatus : InviteStatus.NONE, sequence, inviteStatus == InviteStatus.TIMED_OUT ? "rift.invite.timeout" : inviteStatus == InviteStatus.REFUSED ? "rift.invite.refused" : "rift.invite.pending");
		}

		@Override
		public RouteObservation requestRoute(long leaderProfileId, PhantomDomainRef destination, PhantomNavigationPoint point)
		{
			routes++;
			return new RouteObservation(routeStatus, hash("route"), routeReason);
		}

		@Override
		public RouteObservation observeRoute(long leaderProfileId, String expectedRouteHash)
		{
			return new RouteObservation(routeStatus, hash("route"), routeReason);
		}
	}

	private static final class TestBackend implements PhantomRiftBackend
	{
		private final Path _root;
		private final Map<Integer, Integer> _levels = new HashMap<>();
		private final Set<Path> _loadedNpcFiles = new java.util.HashSet<>();
		final Map<MemberRef, MemberFacts> facts = new LinkedHashMap<>();
		final Set<MemberRef> stale = new java.util.HashSet<>();
		PartySnapshot roster;
		List<MemberFacts> candidates = List.of();
		ConfigFacts config = new ConfigFacts(4, 10000, 480, 600, 1.5f, Map.of(1, 18, 2, 21, 3, 24, 4, 27, 5, 30, 6, 33), "GeneralConfig.RIFT_*");

		TestBackend(Path root)
		{
			_root = root;
		}

		void roster(MemberFacts... members)
		{
			facts.clear();
			for (MemberFacts member : members)
			{
				facts.put(member.member().ref(), new MemberFacts(member.member(), member.level(), member.equipment(), member.requestedItemCounts(), member.shotSupplies(), member.activeWeaponItemId(), member.soulshotsPerHit(), member.spiritshotsPerHit(), members.length, member.evidenceHash()));
			}
			roster = members.length <= 1 ? null : new PartySnapshot(members[0].member().ref(), java.util.Arrays.stream(members).map(value -> value.member().ref()).toList(), PartyDistributionType.FINDERS_KEEPERS);
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			return facts.keySet().stream().filter(value -> (value.kind() == PhantomPartyModel.MemberKind.PHANTOM) && (value.profileId() == profileId)).findFirst();
		}

		@Override
		public Optional<PartySnapshot> canonicalParty(MemberRef member)
		{
			return Optional.ofNullable(roster);
		}

		@Override
		public Optional<MemberFacts> memberFacts(MemberRef member, Set<Integer> requestedItemIds)
		{
			return stale.contains(member) ? Optional.empty() : Optional.ofNullable(facts.get(member));
		}

		@Override
		public List<MemberFacts> nearbyCandidates(MemberRef observer, Set<Integer> requestedItemIds, int range, int limit)
		{
			return candidates.stream().sorted(Comparator.comparing(value -> value.member().ref().stableKey())).limit(limit).toList();
		}
		@Override
		public Optional<MemberFacts> candidateFacts(MemberRef observer, MemberRef candidate, Set<Integer> requestedItemIds, int range)
		{
			return candidates.stream().filter(value -> value.member().ref().equals(candidate)).findFirst();
		}

		@Override
		public OptionalInt npcLevel(int npcId)
		{
			loadNpcFile(npcId);
			return _levels.containsKey(npcId) ? OptionalInt.of(_levels.get(npcId)) : OptionalInt.empty();
		}

		private void loadNpcFile(int npcId)
		{
			final int first = (npcId / 100) * 100;
			final Path file = _root.resolve(String.format("dist/game/data/stats/npcs/%05d-%05d.xml", first, first + 99));
			if (!_loadedNpcFiles.add(file) || !Files.exists(file))
			{
				return;
			}
			try
			{
				final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
				final var nodes = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("npc");
				for (int i = 0; i < nodes.getLength(); i++)
				{
					final Element element = (Element) nodes.item(i);
					if (element.hasAttribute("id") && element.hasAttribute("level"))
					{
						_levels.put(Integer.parseInt(element.getAttribute("id")), Integer.parseInt(element.getAttribute("level")));
					}
				}
			}
			catch (Exception e)
			{
				throw new IllegalArgumentException("Cannot load factual NPC level authority: " + e.getMessage(), e);
			}
		}

		@Override
		public EntryFacts entry(int type)
		{
			return new EntryFacts(type, true, 7079, config.entryCosts().get(type), 2, ENTRY_X, ENTRY_Y, ENTRY_Z, 0, Set.of(9), 0, 8, true, "DimensionalRiftManager.start");
		}

		@Override
		public ConfigFacts config()
		{
			return config;
		}
	}
}
