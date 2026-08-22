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

import java.nio.file.Files;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.SelectionStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.MutationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.EvaluationStatus;

public final class PhantomOperatorObservabilitySuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "operator-observability-selected-trace";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-disabled-status-remains-readable", _ -> testDisabledStatus());
		registry.add("02-diagnostics-disabled-has-zero-storage", _ -> testDisabledTrace());
		registry.add("03-explicit-attached-selection-and-current-reason", _ -> testSelectionAndCurrent());
		registry.add("04-single-profile-capacity-switch-and-clear", _ -> testCapacitySwitchAndClear());
		registry.add("05-observer-prefilter-and-exception-isolation", this::testObserverSemantics);
		registry.add("06-admin-family-access-and-privacy-contract", this::testAdminContract);
	}

	private static void testDisabledStatus()
	{
		final var status = PhantomSystem.operatorStatus();
		PhantomAssertions.assertFalse(status.runtimeConfigured(), "Disabled baseline unexpectedly exposed a runtime instance.");
		PhantomAssertions.assertEquals(PhantomDecisionEngine.State.STOPPED, status.decisionState(), "Disabled status did not expose inactive Decision state.");
		PhantomAssertions.assertEquals(5, status.activityStateCounts().size(), "Status did not expose all five activity states.");
		PhantomAssertions.assertFalse(status.selectedTrace().enabled(), "Disabled status exposed selected trace storage.");
		PhantomAssertions.assertEquals(0, status.selectedTrace().capacity(), "Disabled status allocated selected trace capacity.");
	}

	private static void testDisabledTrace()
	{
		final PhantomSelectedDecisionTrace trace = new PhantomSelectedDecisionTrace(false, 64);
		PhantomAssertions.assertEquals(SelectionStatus.DISABLED, trace.select(1, snapshot(1, 1)), "Disabled trace accepted a selection.");
		trace.observe(PhantomActivityState.ACTIVE, snapshot(1, 2));
		final var disabled = trace.snapshot();
		PhantomAssertions.assertEquals(0L, disabled.selectedProfileId(), "Disabled trace retained a selected profile.");
		PhantomAssertions.assertEquals(0L, disabled.recorded(), "Disabled trace counted an observation.");
		PhantomAssertions.assertEquals(List.of(), disabled.history(), "Disabled trace retained history.");
	}

	private static void testSelectionAndCurrent()
	{
		final PhantomSelectedDecisionTrace trace = new PhantomSelectedDecisionTrace(true, 64);
		PhantomAssertions.assertFalse(trace.interested(1), "Trace prefilter accepted a profile before explicit selection.");
		PhantomAssertions.assertEquals(SelectionStatus.NOT_ATTACHED, trace.select(1, snapshot(2, 1)), "Mismatched attached snapshot was accepted.");
		PhantomAssertions.assertEquals(SelectionStatus.SELECTED, trace.select(1, snapshot(1, 7)), "Attached profile was not selected.");
		PhantomAssertions.assertTrue(trace.interested(1), "Trace prefilter rejected the selected profile.");
		PhantomAssertions.assertFalse(trace.interested(2), "Trace prefilter accepted an unselected profile.");
		final var selected = trace.snapshot();
		PhantomAssertions.assertEquals(1L, selected.selectedProfileId(), "Selected profile id mismatch.");
		PhantomAssertions.assertEquals(7L, selected.current().decisionSequence(), "Current reason view did not come from RuntimeSnapshot.");
		PhantomAssertions.assertEquals("candidate.test", selected.current().candidateKey(), "Current candidate was not preserved.");
		PhantomAssertions.assertEquals("reason.test", selected.current().reasonKey(), "Current reason key was not preserved.");
		PhantomAssertions.assertEquals(1, selected.current().topCandidates().size(), "Top candidate explanations were not preserved.");
		trace.observe(PhantomActivityState.WARM, snapshot(2, 8));
		PhantomAssertions.assertEquals(0, trace.snapshot().history().size(), "Unselected profile entered history.");
		trace.observe(PhantomActivityState.BACKGROUND, snapshot(1, 8));
		PhantomAssertions.assertEquals(PhantomActivityState.BACKGROUND, trace.snapshot().history().get(0).activityState(), "Work-item activity state was not retained.");
	}

	private static void testCapacitySwitchAndClear()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomSelectedDecisionTrace(true, 65), "Selected trace accepted capacity above 64.");
		final PhantomSelectedDecisionTrace trace = new PhantomSelectedDecisionTrace(true, 64);
		trace.select(1, snapshot(1, 0));
		for (int sequence = 1; sequence <= 65; sequence++)
		{
			trace.observe(PhantomActivityState.ACTIVE, snapshot(1, sequence));
		}
		final var bounded = trace.snapshot();
		PhantomAssertions.assertEquals(64, bounded.history().size(), "Selected history exceeded or missed capacity 64.");
		PhantomAssertions.assertEquals(1L, bounded.dropped(), "Selected history overwrite count mismatch.");
		PhantomAssertions.assertEquals(2L, bounded.history().get(0).decisionSequence(), "Selected history did not retain newest entries.");
		PhantomAssertions.assertEquals(SelectionStatus.SELECTED, trace.select(2, snapshot(2, 1)), "Second attached profile was not selected.");
		PhantomAssertions.assertEquals(0, trace.snapshot().history().size(), "Changing the one selected profile retained old history.");
		trace.clear();
		PhantomAssertions.assertEquals(0L, trace.snapshot().selectedProfileId(), "Clear retained selected profile.");
		PhantomAssertions.assertEquals(0L, trace.snapshot().recorded(), "Clear retained counters.");
	}

	private void testObserverSemantics(PhantomTestContext context) throws Exception
	{
		final EngineFixture legacy = fixture(null);
		legacy.pulse();
		PhantomAssertions.assertEquals(RuntimeState.NO_CANDIDATE, legacy.engine.find(1).orElseThrow().runtimeState(), "Legacy constructor path changed canonical decision.");
		legacy.stop();

		final AtomicInteger interestedCalls = new AtomicInteger();
		final AtomicInteger filteredDecisionCalls = new AtomicInteger();
		final EngineFixture filtered = fixture(new PhantomDecisionEngine.DecisionObserver()
		{
			@Override
			public boolean interested(long profileId)
			{
				interestedCalls.incrementAndGet();
				return false;
			}

			@Override
			public void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot)
			{
				filteredDecisionCalls.incrementAndGet();
			}
		});
		filtered.pulse();
		PhantomAssertions.assertEquals(1, interestedCalls.get(), "Observer prefilter was not called exactly once.");
		PhantomAssertions.assertEquals(0, filteredDecisionCalls.get(), "Uninterested observer received an allocated RuntimeSnapshot callback.");
		PhantomAssertions.assertEquals(1L, filtered.engine.find(1).orElseThrow().decisionSequence(), "Observer prefilter changed canonical decision sequence.");
		filtered.stop();

		final AtomicInteger rejectedDecisionCalls = new AtomicInteger();
		final EngineFixture rejected = fixture(new PhantomDecisionEngine.DecisionObserver()
		{
			@Override
			public boolean interested(long profileId)
			{
				throw new IllegalStateException("prefilter failure");
			}

			@Override
			public void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot)
			{
				rejectedDecisionCalls.incrementAndGet();
			}
		});
		rejected.pulse();
		PhantomAssertions.assertEquals(0, rejectedDecisionCalls.get(), "Observer callback ran after prefilter exception.");
		PhantomAssertions.assertEquals(RuntimeState.NO_CANDIDATE, rejected.engine.find(1).orElseThrow().runtimeState(), "Observer prefilter exception changed canonical decision state.");
		rejected.stop();

		final AtomicInteger observerCalls = new AtomicInteger();
		final EngineFixture observed = fixture((activityState, snapshot) ->
		{
			observerCalls.incrementAndGet();
			throw new IllegalStateException("observer failure");
		});
		observed.pulse();
		final RuntimeSnapshot canonical = observed.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(1, observerCalls.get(), "Source-compatible observer was not called exactly once for a meaningful pulse.");
		PhantomAssertions.assertEquals(RuntimeState.NO_CANDIDATE, canonical.runtimeState(), "Observer exception changed canonical decision state.");
		PhantomAssertions.assertEquals(1L, canonical.decisionSequence(), "Observer exception changed canonical decision sequence.");
		observed.stop();

		final String engineSource = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java"));
		final int notifyStart = engineSource.indexOf("private void notifyObserver");
		final int notifyEnd = engineSource.indexOf("public Optional<RuntimeSnapshot> find", notifyStart);
		final String notifySource = engineSource.substring(notifyStart, notifyEnd);
		final int prefilter = notifySource.indexOf("_observer.interested(workItem.profileId())");
		final int observerLock = notifySource.indexOf("synchronized (_monitor)");
		final int snapshotBuild = notifySource.indexOf("snapshotLocked(slot)");
		PhantomAssertions.assertTrue((prefilter >= 0) && (prefilter < observerLock) && (prefilter < snapshotBuild), "Observer prefilter no longer precedes the observer snapshot lock/build path.");
	}

	private void testAdminContract(PhantomTestContext context) throws Exception
	{
		final String handler = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java"));
		final String master = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/MasterHandler.java"));
		final var accessPath = context.moduleRoot().resolve("dist/game/config/AdminCommands.xml");
		final var schemaPath = context.moduleRoot().resolve("dist/game/data/xsd/AdminCommands.xsd");
		final String access = Files.readString(accessPath);
		SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(schemaPath.toFile()).newValidator().validate(new StreamSource(accessPath.toFile()));
		final String trace = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java"));
		PhantomAssertions.assertTrue(handler.contains("\"admin_phantom\""), "Admin handler did not expose one native family.");
		PhantomAssertions.assertTrue(handler.contains("//phantom status | //phantom trace <profileId> | //phantom trace clear"), "Admin usage contract drifted.");
		PhantomAssertions.assertTrue(master.contains("AdminPhantom.class"), "MasterHandler did not register AdminPhantom.");
		PhantomAssertions.assertTrue(access.contains("command=\"phantom\"") && access.contains("accessLevel=\"100\"") && access.contains("confirmDlg=\"false\"") && !access.contains("requireConfirm="), "Phantom admin access contract is incomplete.");
		PhantomAssertions.assertFalse(trace.contains("PhantomDomainRef") || trace.contains("Player") || trace.contains("Chat"), "Selected trace source admitted domain, player or chat payload types.");
		PhantomAssertions.assertFalse(trace.contains("Thread") || trace.contains("Timer") || trace.contains("Scheduled") || trace.contains("poll("), "Selected trace introduced active polling or scheduling.");
	}

	private static EngineFixture fixture(PhantomDecisionEngine.DecisionObserver observer)
	{
		final InMemoryStore store = new InMemoryStore();
		store.profiles.add(1L);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.seal();
		final PhantomDecisionEngine engine = observer == null ? new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4) : new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4, observer);
		engine.start();
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, engine.attach(1), "Observer fixture profile did not attach.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.insertGoal(1, goal()), "Observer fixture goal did not insert.");
		return new EngineFixture(engine);
	}

	private static PhantomGoal goal()
	{
		return new PhantomGoal(1, "goal.test", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("subject", "A"), new PhantomDomainRef("target", "B"), 10, 2, "method.test", List.of(new PhantomDomainRef("source", "A")), null, "purpose.test", 500, 20, 30, 0, Map.of(), "reason.test", 0);
	}

	private static RuntimeSnapshot snapshot(long profileId, long decisionSequence)
	{
		final CandidateEvaluation explanation = new CandidateEvaluation("candidate.test", 700, EvaluationStatus.ELIGIBLE, "candidate.ready");
		return new RuntimeSnapshot(profileId, 11, "goal.test", 3, PhantomGoalStatus.ACTIVE, RuntimeState.EXECUTING, decisionSequence, "candidate.test", 700, 21, 1, 2, null, "reason.test", List.of(explanation), false, false, 0, null, 4, 5, 6);
	}

	private record EngineFixture(PhantomDecisionEngine engine)
	{
		private void pulse()
		{
			engine.accept(new PhantomActivityWorkItem(1, PhantomActivityState.WARM, 1, 1, 0, PhantomActivityOverloadLevel.NORMAL));
		}

		private void stop()
		{
			engine.beginStop();
			PhantomAssertions.assertTrue(engine.finishStop(), "Observer fixture did not stop.");
		}
	}

	private static final class InMemoryStore implements PhantomGoalStore
	{
		private final Set<Long> profiles = new HashSet<>();
		private final Map<Long, StoredGoal> goals = new HashMap<>();

		@Override
		public boolean profileExists(long profileId)
		{
			return profiles.contains(profileId);
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(goals.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (goals.containsKey(profileId))
			{
				throw new ConcurrentModificationException("duplicate");
			}
			final StoredGoal stored = new StoredGoal(goal, 0);
			goals.put(profileId, stored);
			return stored;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal stored = new StoredGoal(goal, expectedRowVersion + 1);
			goals.put(profileId, stored);
			return stored;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			goals.remove(profileId);
		}
	}
}
