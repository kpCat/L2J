/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionUse;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPvpCombatRequest;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Candidate;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Counterpart;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.CounterpartKind;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.RiskSnapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPolicy;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStateCodec;

public final class PhantomPvpSuite implements PhantomTestSuite
{
	public enum Mode
	{
		POLICY,
		ADMISSION,
		COMBAT,
		CP,
		PARTY_HELP,
		WARNING_SOCIAL,
		RESTART,
		PERFORMANCE
	}

	private static final long SEED = 25002501L;
	private static final long SECOND = 1_000_000_000L;
	private static final String HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private final Mode _mode;

	public PhantomPvpSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "pvp-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 025 mode used the wrong deterministic seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case POLICY -> policy(registry);
			case ADMISSION -> admission(registry);
			case COMBAT -> combat(registry);
			case CP -> cp(registry);
			case PARTY_HELP -> partyHelp(registry);
			case WARNING_SOCIAL -> warningSocial(registry);
			case RESTART -> restart(registry);
			case PERFORMANCE -> performance(registry);
		}
	}

	private static void policy(PhantomTestRegistry registry)
	{
		registry.add("01-strict-policy-load-and-bounds", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			PhantomAssertions.assertEquals(16, policy.limits().observedAttackerLimit(), "Attacker observation bound changed.");
			PhantomAssertions.assertEquals(16, policy.limits().profilesPerPulse(), "Shared pulse bound changed.");
			PhantomAssertions.assertEquals(1, policy.limits().maxProactiveEngagementsPerPair(), "Per-pair budget changed.");
			PhantomAssertions.assertEquals(30, policy.limits().cpPotionThresholdPercent(), "CP potion policy changed.");
			final Path malformed = context.reportsDirectory().resolve("pvp-policy-invalid.xml");
			Files.createDirectories(malformed.getParent());
			Files.writeString(malformed, "<pvpThreatPolicy id=\"bad\" version=\"1\"><limits/></pvpThreatPolicy>", StandardCharsets.UTF_8);
			try
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomPvpPolicy.load(malformed), "Incomplete PvP policy was accepted.");
			}
			finally
			{
				Files.deleteIfExists(malformed);
			}
		});
		registry.add("02-warning-reactive-retreat-and-budget-decisions", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			final long now = 20 * SECOND;
			final Candidate proactive = candidate(Source.FARMING_ESCALATION, now);
			final Encounter observe = encounter(proactive, Stage.OBSERVE, "", 0, 0, 0);
			PhantomAssertions.assertEquals(Decision.WARN, policy.decide(proactive, risk(100, 100, false), observe, now).decision(), "Proactive source skipped warning.");
			final Encounter warned = encounter(proactive, Stage.WARN, "receipt", now - (6 * SECOND), 0, 0);
			final var engage = policy.decide(proactive, risk(100, 100, false), warned, now);
			PhantomAssertions.assertEquals(Decision.ENGAGE, engage.decision(), "Persisted warning and delay did not authorize engagement.");
			PhantomAssertions.assertTrue(engage.forceUse(), "Exact proactive neutral target did not retain force authority.");
			final Candidate reactive = candidate(Source.ACTUAL_ATTACK, now);
			PhantomAssertions.assertEquals(Decision.ENGAGE, policy.decide(reactive, risk(100, 100, false), encounter(reactive, Stage.OBSERVE, "", 0, 0, 0), now).decision(), "Reactive attack did not bypass warning.");
			PhantomAssertions.assertEquals(Decision.RETREAT, policy.decide(reactive, risk(10, 20, true), encounter(reactive, Stage.OBSERVE, "", 0, 0, 0), now).decision(), "Low effective pool did not retreat.");
			PhantomAssertions.assertEquals(Decision.COOLDOWN, policy.decide(proactive, risk(100, 100, false), encounter(proactive, Stage.WARN, "receipt", now - (6 * SECOND), 1, 0), now).decision(), "Per-pair proactive budget was exceeded.");
		});
	}

	private static void admission(PhantomTestRegistry registry)
	{
		registry.add("01-only-causal-source-vocabulary-and-current-authority", _ ->
		{
			PhantomAssertions.assertEquals(Set.of(Source.ACTUAL_ATTACK, Source.FARMING_ESCALATION, Source.PARTY_DEFENSE, Source.REVENGE), Set.of(Source.values()), "Aggression source vocabulary widened.");
			final long now = 10 * SECOND;
			PhantomAssertions.assertTrue(candidate(Source.ACTUAL_ATTACK, now).currentAt(now), "Exact current attacker was rejected.");
			final Candidate stale = new Candidate(1, counterpart(), Source.REVENGE, HASH, now - SECOND, now + SECOND, false, true, true);
			PhantomAssertions.assertFalse(stale.currentAt(now), "Stale revenge authority remained current.");
			final Candidate hidden = new Candidate(1, counterpart(), Source.ACTUAL_ATTACK, HASH, now - SECOND, now + SECOND, true, true, false);
			PhantomAssertions.assertFalse(hidden.currentAt(now), "Invisible target remained current.");
		});
		registry.add("02-canonical-friendly-and-context-gates", _ ->
		{
			final ActorSnapshot actor = actor(100, 100);
			PhantomAssertions.assertTrue(target(false, false, false).validFor(actor, 2000), "Exact neutral Player target was not structurally valid.");
			PhantomAssertions.assertFalse(target(true, false, false).validFor(actor, 2000), "Same Party Player passed canonical context gate.");
			PhantomAssertions.assertFalse(target(false, true, false).validFor(actor, 2000), "Peace-restricted Player passed canonical context gate.");
			PhantomAssertions.assertFalse(target(false, false, true).validFor(actor, 2000), "Dead Player passed canonical context gate.");
		});
	}

	private static void combat(PhantomTestRegistry registry)
	{
		registry.add("01-explicit-physical-path-isolated-from-monster-path", _ ->
		{
			try (CombatFixture fixture = new CombatFixture(PhantomCombatMode.MELEE_PHYSICAL, 100))
			{
				final var start = fixture.start(Source.ACTUAL_ATTACK, false);
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, start.status(), "Explicit physical PvP request was rejected.");
				fixture.dispatcher.runNext();
				PhantomAssertions.assertEquals(1, fixture.lease.pvpAttacks, "Physical PvP did not reach explicit backend path.");
				PhantomAssertions.assertEquals(0, fixture.lease.legacyAttacks, "Physical PvP weakened or reused legacy monster attack.");
				PhantomAssertions.assertEquals(0, fixture.lease.legacyTargetReads, "PvP pulse entered legacy monster target lookup.");
				PhantomAssertions.assertTrue(fixture.service.matchesPvpSession(1, 100, HASH), "Exact PvP session ownership was not observable.");
			}
		});
		registry.add("02-explicit-skill-path-and-force-authority", _ ->
		{
			try (CombatFixture fixture = new CombatFixture(PhantomCombatMode.RANGED_MAGIC, 100))
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> fixture.request(Source.ACTUAL_ATTACK, true), "Reactive source gained forceUse authority.");
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.service.startPvpSession(fixture.request(Source.REVENGE, true)).status(), "Exact proactive skill PvP request was rejected.");
				fixture.dispatcher.runNext();
				PhantomAssertions.assertEquals(1, fixture.lease.pvpCasts, "Skill PvP did not reach explicit backend path.");
				PhantomAssertions.assertTrue(fixture.lease.lastForceUse, "Exact proactive forceUse was lost.");
				PhantomAssertions.assertEquals(0, fixture.lease.legacyCasts, "Skill PvP weakened or reused legacy monster cast.");
			}
		});
		registry.add("03-target-safety-rejection-propagates", _ ->
		{
			try (CombatFixture fixture = new CombatFixture(PhantomCombatMode.MELEE_PHYSICAL, 100))
			{
				fixture.lease.pvpTarget = target(true, false, false);
				PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.start(Source.ACTUAL_ATTACK, false).status(), "Same Party target was accepted.");
				PhantomAssertions.assertEquals(0, fixture.lease.pvpAttacks, "Rejected target produced an action.");
			}
		});
	}

	private static void cp(PhantomTestRegistry registry)
	{
		registry.add("01-real-stock-and-observed-success-contract", _ ->
		{
			final CpPotionSnapshot small = new CpPotionSnapshot(5001, 5591, 2, 2166, 1, 0, 0);
			final CpPotionSnapshot large = new CpPotionSnapshot(5002, 5592, 1, 2166, 2, 500, 0);
			PhantomAssertions.assertTrue(small.ready(), "Ready stock 5591 was not ready.");
			PhantomAssertions.assertFalse(large.ready(), "Canonical item reuse for 5592 was ignored.");
			new CpPotionUse(CpPotionOutcome.OBSERVED_SUCCESS, 5591, 2, 1, 10, 60, 500);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new CpPotionUse(CpPotionOutcome.OBSERVED_SUCCESS, 5591, 2, 2, 10, 10, 0), "Synthetic CP success without observed truth was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new CpPotionSnapshot(5003, 5592, 1, 2166, 1, 0, 0), "Wrong source-derived skill level for 5592 was accepted.");
		});
		registry.add("02-session-uses-handler-result-only-below-threshold", _ ->
		{
			try (CombatFixture fixture = new CombatFixture(PhantomCombatMode.MELEE_PHYSICAL, 10))
			{
				fixture.lease.potions = List.of(new CpPotionSnapshot(5001, 5591, 2, 2166, 1, 0, 0));
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(Source.ACTUAL_ATTACK, false).status(), "CP fixture PvP request was rejected.");
				fixture.dispatcher.runNext();
				PhantomAssertions.assertEquals(1, fixture.lease.potionUses, "Ready real CP stock was not delegated once.");
			}
			try (CombatFixture fixture = new CombatFixture(PhantomCombatMode.MELEE_PHYSICAL, 80))
			{
				fixture.lease.potions = List.of(new CpPotionSnapshot(5001, 5591, 2, 2166, 1, 0, 0));
				fixture.start(Source.ACTUAL_ATTACK, false);
				fixture.dispatcher.runNext();
				PhantomAssertions.assertEquals(0, fixture.lease.potionUses, "CP potion was used above policy threshold.");
			}
		});
	}

	private static void partyHelp(PhantomTestRegistry registry)
	{
		registry.add("01-party-defense-is-distinct-immediate-and-bounded", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			final long now = 20 * SECOND;
			final Candidate candidate = candidate(Source.PARTY_DEFENSE, now);
			final var outcome = policy.decide(candidate, risk(100, 100, true), encounter(candidate, Stage.OBSERVE, "", 0, 0, 0), now);
			PhantomAssertions.assertEquals(Decision.HELP, outcome.decision(), "Party defense did not use distinct immediate help decision.");
			PhantomAssertions.assertTrue(policy.limits().helpFanout() <= 8, "Party helper fanout is not bounded.");
		});
	}

	private static void warningSocial(PhantomTestRegistry registry)
	{
		registry.add("01-warning-receipt-persists-before-authority", _ ->
		{
			final long now = 20 * SECOND;
			final Candidate candidate = candidate(Source.REVENGE, now);
			final Encounter warned = encounter(candidate, Stage.WARN, "plan-warning-1", now, 0, 0);
			final Encounter restored = new PhantomPvpStateCodec().decode(new PhantomPvpStateCodec().encode(warned));
			PhantomAssertions.assertEquals(Stage.WARN, restored.stage(), "Warning stage did not persist.");
			PhantomAssertions.assertEquals("plan-warning-1", restored.warningReceiptId(), "Goal020 receipt did not persist.");
			PhantomAssertions.assertEquals(PhantomPvpModel.sha256("social", HASH), PhantomPvpModel.sha256("social", HASH), "Typed social idempotency hash is unstable.");
		});
	}

	private static void restart(PhantomTestRegistry registry)
	{
		registry.add("01-cooldown-and-counterpart-survive-reload", _ ->
		{
			final long now = 20 * SECOND;
			final Candidate candidate = candidate(Source.FARMING_ESCALATION, now);
			final Encounter cooling = encounter(candidate, Stage.COOLDOWN, "receipt", now - SECOND, 1, now + (300 * SECOND));
			final PhantomPvpStateCodec codec = new PhantomPvpStateCodec();
			final Encounter restored = codec.decode(codec.encode(cooling));
			PhantomAssertions.assertEquals(cooling, restored, "Bounded encounter state changed across reload.");
			PhantomAssertions.assertTrue(restored.cooldownUntilLogicalNanos() > now, "Pair cooldown did not survive reload.");
			final Candidate stale = new Candidate(1, restored.counterpart(), restored.source(), HASH, now, now + SECOND, false, true, true);
			PhantomAssertions.assertFalse(stale.currentAt(now), "Restart resumed stale authority.");
		});
	}

	private static void performance(PhantomTestRegistry registry)
	{
		registry.add("01-two-hundred-thousand-pure-policy-evaluations", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			final long now = 20 * SECOND;
			final Candidate candidate = candidate(Source.ACTUAL_ATTACK, now);
			final Encounter encounter = encounter(candidate, Stage.OBSERVE, "", 0, 0, 0);
			final RiskSnapshot risk = risk(100, 100, true);
			final long started = System.nanoTime();
			int engagements = 0;
			for (int i = 0; i < 200_000; i++)
			{
				if (policy.decide(candidate, risk, encounter, now).decision() == Decision.ENGAGE)
				{
					engagements++;
				}
			}
			final long elapsed = System.nanoTime() - started;
			PhantomAssertions.assertEquals(200_000, engagements, "Pure policy evaluation changed under load.");
			PhantomAssertions.assertTrue(elapsed < 5_000_000_000L, "Pure policy performance smoke exceeded five seconds.");
			context.record("pvpPolicyEvaluations", engagements);
			context.record("pvpPolicyElapsedNanos", elapsed);
		});
	}

	private static PhantomPvpPolicy policy(PhantomTestContext context)
	{
		return PhantomPvpPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/pvp/pvp-policy-v1.xml"));
	}

	private static Candidate candidate(Source source, long now)
	{
		return new Candidate(1, counterpart(), source, HASH, now - SECOND, now + (120 * SECOND), true, true, true);
	}

	private static Counterpart counterpart()
	{
		return new Counterpart(CounterpartKind.HUMAN_OBJECT, 100, 100);
	}

	private static Encounter encounter(Candidate candidate, Stage stage, String warningReceipt, long warningAt, int engagements, long cooldown)
	{
		return new Encounter(candidate.profileId(), candidate.counterpart(), candidate.source(), candidate.authorityHash(), stage, warningReceipt, "", engagements, candidate.createdLogicalNanos(), Math.max(candidate.expiresLogicalNanos(), cooldown + 1), warningAt, cooldown, "test");
	}

	private static RiskSnapshot risk(int hp, int effective, boolean autoAttackable)
	{
		return new RiskSnapshot(hp, effective, 100, 4, 4, 10000, autoAttackable ? 0 : 1000, 0, 0, false, false, false, true, autoAttackable);
	}

	private static ActorSnapshot actor(double hp, double cp)
	{
		return new ActorSnapshot(10, 88, 0, hp, 100, 100, 100, cp, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
	}

	private static PvpTargetSnapshot target(boolean sameParty, boolean peaceRestricted, boolean dead)
	{
		return new PvpTargetSnapshot(100, 89, 0, 80, 4, 4, 10, true, true, true, false, dead, dead, true, peaceRestricted, sameParty, false, false, false, false, false, false, false, false, false);
	}

	private static final class CombatFixture implements AutoCloseable
	{
		private final AtomicLong clock = new AtomicLong(1);
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final ManualDispatcher dispatcher = new ManualDispatcher();
		private final FakeLease lease;
		private final PhantomCombatService service;
		private final PhantomCombatMode mode;

		private CombatFixture(PhantomCombatMode mode, double cp)
		{
			this.mode = mode;
			lease = new FakeLease(cp);
			final List<SelectedSkill> skills = mode == PhantomCombatMode.RANGED_MAGIC ? List.of(new SelectedSkill(123, 1)) : List.of();
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(mode.capabilityKey(), 900, skills)));
			service = new PhantomCombatService(profileId -> profileId == 1 ? lease : null, resolver, PhantomCombatPolicy.productionDefaults(4), new PhantomCombatMetrics(), clock::get, dispatcher);
			service.start();
			PhantomAssertions.assertEquals(PhantomCombatService.ServiceState.RUNNING, service.snapshot().state(), "PvP combat fixture did not start.");
		}

		private PhantomPvpCombatRequest request(Source source, boolean forceUse)
		{
			return new PhantomPvpCombatRequest(1, 100, source, HASH, mode, forceUse, false, 30, 30_000, cancelled::get);
		}

		private PhantomCombatService.StartResult start(Source source, boolean forceUse)
		{
			return service.startPvpSession(request(source, forceUse));
		}

		@Override
		public void close()
		{
			service.beginStop();
			if (dispatcher.next != null)
			{
				dispatcher.cancelNext();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "PvP combat fixture did not stop.");
		}
	}

	private static final class FakeLease implements PhantomCombatActorLease
	{
		private final ActorSnapshot actor;
		private PvpTargetSnapshot pvpTarget = target(false, false, false);
		private List<CpPotionSnapshot> potions = List.of();
		private int legacyTargetReads;
		private int legacyAttacks;
		private int legacyCasts;
		private int pvpAttacks;
		private int pvpCasts;
		private int potionUses;
		private boolean lastForceUse;

		private FakeLease(double cp)
		{
			actor = actor(100, cp);
		}

		@Override
		public ActorSnapshot actorSnapshot()
		{
			return actor;
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			legacyTargetReads++;
			return null;
		}

		@Override
		public PvpTargetSnapshot pvpTargetSnapshot(int targetObjectId)
		{
			return targetObjectId == 100 ? pvpTarget : null;
		}

		@Override
		public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return false;
		}

		@Override
		public boolean supportsPvpSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return skill.skillId() == 123;
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return List.of();
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return List.of();
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return LootObservation.INELIGIBLE;
		}

		@Override
		public List<CpPotionSnapshot> cpPotions()
		{
			return potions;
		}

		@Override
		public CpPotionUse useCpPotion(int itemObjectId, int itemId)
		{
			potionUses++;
			return new CpPotionUse(CpPotionOutcome.OBSERVED_SUCCESS, itemId, 2, 1, actor.currentCp(), actor.currentCp() + 50, 500);
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			legacyAttacks++;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome attackPvp(int targetObjectId, String authorityHash)
		{
			pvpAttacks++;
			return HASH.equals(authorityHash) ? ActionOutcome.ISSUED : ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			legacyCasts++;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome castPvp(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode, boolean forceUse, String authorityHash)
		{
			pvpCasts++;
			lastForceUse = forceUse;
			return HASH.equals(authorityHash) ? ActionOutcome.ISSUED : ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			return RespawnOutcome.REJECTED;
		}

		@Override
		public void close()
		{
		}
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable next;
		private ManualHandle handle;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			PhantomAssertions.assertTrue(next == null, "PvP fixture scheduled more than one shared worker.");
			next = runnable;
			handle = new ManualHandle(this);
			return DispatchResult.accepted(handle);
		}

		private void runNext()
		{
			final Runnable runnable = next;
			PhantomAssertions.assertTrue(runnable != null, "No PvP combat pulse was scheduled.");
			next = null;
			final ManualHandle exact = handle;
			handle = null;
			exact.run(runnable);
		}

		private void cancelNext()
		{
			PhantomAssertions.assertTrue(handle.cancelIfNotStarted(), "Scheduled PvP worker could not be cancelled.");
		}
	}

	private static final class ManualHandle implements DispatchHandle
	{
		private final ManualDispatcher owner;
		private DispatchState state = DispatchState.SCHEDULED;

		private ManualHandle(ManualDispatcher owner)
		{
			this.owner = owner;
		}

		private void run(Runnable runnable)
		{
			state = DispatchState.RUNNING;
			try
			{
				runnable.run();
			}
			finally
			{
				state = DispatchState.FINISHED;
			}
		}

		@Override
		public boolean cancelIfNotStarted()
		{
			if ((state != DispatchState.SCHEDULED) || (owner.handle != this))
			{
				return false;
			}
			owner.next = null;
			owner.handle = null;
			state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public DispatchState state()
		{
			return state;
		}
	}
}
