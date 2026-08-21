/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyIdentity;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.PartySlot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ReadyReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.StagingCenter;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.StagingSource;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AttemptStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidDecision;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;

public final class PhantomRaidDecisionGoal026Checkpoint5Suite implements PhantomTestSuite
{
	private static final long SEED = 26002653L;

	@Override
	public String id()
	{
		return "raid-decision-goal026cp5";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Raid Decision CP5 used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-prepare-completes-only-on-attempt-victory-and-cancels-in-order", _ -> prepareLifecycle());
		registry.add("02-participate-waits-for-leader-and-follows-terminal", _ -> participationLifecycle());
	}

	private static void prepareLifecycle()
	{
		final List<String> calls = new ArrayList<>();
		final FakeAssembly assembly = new FakeAssembly(calls);
		final FakeAttempt attempt = new FakeAttempt(calls);
		final PhantomStepHandlerRegistry handlers = handlers(assembly, attempt);
		final PhantomGoal goal = goal(1, 10, PhantomRaidAssemblyService.PREPARE_GOAL_TYPE);
		final var waiting = new PhantomRaidAttemptService.AdvanceResult(AttemptStatus.WAITING_FOR_READY, "raid.attempt.waiting_ready_receipt", null);

		attempt.responds(waiting);
		assembly.result = new PhantomRaidAssemblyService.AdvanceResult(AssemblyStatus.ASSEMBLING, "raid.assembly.assembling", null);
		calls.clear();
		PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false).type(), "Typed waiting-for-ready did not advance Assembly.");
		PhantomAssertions.assertEquals(List.of("attempt.advance", "assembly.advance"), calls, "No-attempt Decision did not call Attempt before Assembly.");

		for (AttemptStatus status : List.of(AttemptStatus.FIGHTING, AttemptStatus.RETREAT))
		{
			attempt.responds(new PhantomRaidAttemptService.AdvanceResult(status, "raid.attempt.active", null));
			calls.clear();
			PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false).type(), status + " completed raid.prepare.");
			PhantomAssertions.assertEquals(List.of("attempt.advance"), calls, status + " advanced Assembly despite exact Attempt ownership.");
		}

		attempt.responds(new PhantomRaidAttemptService.AdvanceResult(AttemptStatus.VICTORY, "raid.attempt.victory", null));
		calls.clear();
		PhantomAssertions.assertEquals(PhantomStepResult.Type.COMPLETE_GOAL, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false).type(), "VICTORY did not complete raid.prepare.");
		PhantomAssertions.assertEquals(List.of("attempt.advance"), calls, "Terminal VICTORY advanced Assembly.");
		attempt.responds(new PhantomRaidAttemptService.AdvanceResult(AttemptStatus.ABORTED, "raid.attempt.aborted", null));
		calls.clear();
		PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false).type(), "ABORTED did not fail raid.prepare.");
		PhantomAssertions.assertEquals(List.of("attempt.advance"), calls, "Terminal ABORTED advanced Assembly.");

		assembly.result = new PhantomRaidAssemblyService.AdvanceResult(AssemblyStatus.READY_AT_STAGING, "raid.assembly.ready", ready());
		attempt.responds(waiting, new PhantomRaidAttemptService.AdvanceResult(AttemptStatus.FIGHTING, "raid.attempt.fighting", null));
		calls.clear();
		PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false).type(), "READY did not start Attempt in the same bounded Decision step.");
		PhantomAssertions.assertEquals(List.of("attempt.advance", "assembly.advance", "attempt.advance"), calls, "READY flow did not retry Attempt after Assembly exactly once.");

		calls.clear();
		PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, true).type(), "Cancellation did not cancel raid.prepare.");
		PhantomAssertions.assertEquals(List.of("attempt.cancel", "assembly.cancel"), calls, "Cancellation did not clean Attempt before Assembly.");
	}

	private static void participationLifecycle()
	{
		final List<String> calls = new ArrayList<>();
		final FakeAssembly assembly = new FakeAssembly(calls);
		final FakeAttempt attempt = new FakeAttempt(calls);
		final PhantomStepHandlerRegistry handlers = handlers(assembly, attempt);
		final PhantomGoal goal = goal(2, 20, PhantomRaidAssemblyService.PARTICIPATE_GOAL_TYPE);

		for (ParticipationStatus status : List.of(ParticipationStatus.WAITING_FOR_LEADER, ParticipationStatus.ACTIVE, ParticipationStatus.RETREATING))
		{
			attempt.participation = status;
			PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 2, goal, false).type(), status + " did not keep raid.participate pending.");
		}
		attempt.participation = ParticipationStatus.VICTORY;
		PhantomAssertions.assertEquals(PhantomStepResult.Type.COMPLETE_GOAL, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 2, goal, false).type(), "Leader VICTORY did not complete raid.participate.");
		for (ParticipationStatus status : List.of(ParticipationStatus.FAILED, ParticipationStatus.EXPIRED, ParticipationStatus.CANCELLED))
		{
			attempt.participation = status;
			PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 2, goal, false).type(), status + " did not fail raid.participate.");
		}
		PhantomAssertions.assertEquals(0, assembly.advanceCalls, "raid.participate created or advanced a leader assembly.");
		PhantomAssertions.assertEquals(0, attempt.advanceCalls, "raid.participate created or advanced a leader attempt.");
	}

	private static PhantomStepHandlerRegistry handlers(FakeAssembly assembly, FakeAttempt attempt)
	{
		final PhantomRaidDecision decision = new PhantomRaidDecision(assembly, attempt);
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		decision.registerHandlers(handlers);
		handlers.seal();
		return handlers;
	}

	private static PhantomStepResult execute(PhantomStepHandlerRegistry handlers, String action, String candidate, long profileId, PhantomGoal goal, boolean cancelled)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, action, goal.target(), Map.of(), 60_000, 1, action + ".test");
		final PhantomPlan plan = new PhantomPlan(1, goal.goalId(), candidate, List.of(step), 60_000, 1);
		return handlers.snapshot().get(action).execute(new PhantomStepContext(profileId, goal, plan, step, PhantomActivityState.ACTIVE, 1, 1, cancelled ? () -> true : () -> false));
	}

	private static PhantomGoal goal(long profileId, long goalId, String goalType)
	{
		return new PhantomGoal(goalId, goalType, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("raid.content", "raid.test"), 1, 0, null, List.of(), null, goalType, 500, 0, 0, 20_000, Map.of(), "test.decision", 0);
	}

	private static ReadyReceipt ready()
	{
		final MemberRef leader = MemberRef.phantom(1, 100);
		final StagingCenter centre = new StagingCenter(StagingSource.CONTENT_ANCHOR, new PhantomNavigationPoint(0, 0, 0, 0), "A".repeat(64));
		final PartySlot slot = new PartySlot(leader, centre.point(), new PhantomDomainRef("raid.staging", "test"), "B".repeat(64));
		final RaidReadiness readiness = new RaidReadiness("raid.test", null, null, TargetAvailability.ENTRY_GATED, CurrentForceObservation.unavailable("test.force"), List.of(), ReadinessStatus.GROUP_READY, "raid.group.ready");
		return new ReadyReceipt(new AssemblyIdentity(1, 10, 0, "raid.test"), "C".repeat(64), centre, List.of(slot), readiness, 1);
	}

	private static final class FakeAssembly implements PhantomRaidDecision.AssemblyPort
	{
		private final List<String> _calls;
		private PhantomRaidAssemblyService.AdvanceResult result = new PhantomRaidAssemblyService.AdvanceResult(AssemblyStatus.ASSEMBLING, "raid.assembly.assembling", null);
		private int advanceCalls;

		private FakeAssembly(List<String> calls)
		{
			_calls = calls;
		}

		@Override
		public PhantomRaidAssemblyService.AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision)
		{
			advanceCalls++;
			_calls.add("assembly.advance");
			return result;
		}

		@Override
		public boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey)
		{
			_calls.add("assembly.cancel");
			return true;
		}
	}

	private static final class FakeAttempt implements PhantomRaidDecision.AttemptPort
	{
		private final List<String> _calls;
		private List<PhantomRaidAttemptService.AdvanceResult> _advanceResults = List.of(new PhantomRaidAttemptService.AdvanceResult(AttemptStatus.FIGHTING, "raid.attempt.fighting", null));
		private int _advanceIndex;
		private ParticipationStatus participation = ParticipationStatus.WAITING_FOR_LEADER;
		private int advanceCalls;

		private FakeAttempt(List<String> calls)
		{
			_calls = calls;
		}

		private void responds(PhantomRaidAttemptService.AdvanceResult... results)
		{
			_advanceResults = List.of(results);
			_advanceIndex = 0;
		}

		@Override
		public PhantomRaidAttemptService.AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision)
		{
			advanceCalls++;
			_calls.add("attempt.advance");
			final int index = Math.min(_advanceIndex++, _advanceResults.size() - 1);
			return _advanceResults.get(index);
		}

		@Override
		public boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey)
		{
			_calls.add("attempt.cancel");
			return true;
		}

		@Override
		public ParticipationStatus participation(long profileId, long goalId, long goalRevision)
		{
			return participation;
		}
	}
}
