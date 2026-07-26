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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.MutationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.PersistenceOperationKind;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.ReloadResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandler;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

public final class PhantomDecisionPersistenceSuite implements PhantomTestSuite
{
	private final List<Long> _ownedProfileIds = new ArrayList<>();
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();
	private PhantomProfileRepository _repository;

	@Override
	public String id()
	{
		return "decision-persistence";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		_repository = PhantomProfileRepository.open();
		context.record("decisionPersistence.componentType", PhantomGoalStateStore.COMPONENT_TYPE);
		context.record("decisionPersistence.componentSchema", PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				try (Connection connection = DatabaseFactory.getConnection();
					PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
				{
					for (long profileId : _ownedProfileIds)
					{
						statement.setLong(1, profileId);
						statement.executeUpdate();
					}
				}
			}
		}
		finally
		{
			DatabaseFactory.close();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-codec-deterministic-round-trip", _ -> testDeterministicRoundTrip());
		registry.add("02-codec-rejects-magic", _ -> testUnknownMagic());
		registry.add("03-codec-rejects-format-version", _ -> testUnknownFormatVersion());
		registry.add("04-codec-rejects-goal-version", _ -> testUnknownGoalVersion());
		registry.add("05-codec-rejects-truncation", _ -> testTruncation());
		registry.add("06-codec-rejects-trailing-bytes", _ -> testTrailingBytes());
		registry.add("07-codec-rejects-length-before-allocation", _ -> testOversizedLength());
		registry.add("08-codec-rejects-unknown-status", _ -> testUnknownStatus());
		registry.add("09-store-insert-load-envelope", _ -> testStoreInsertLoad());
		registry.add("10-store-optimistic-replace", _ -> testStoreReplace());
		registry.add("11-store-optimistic-delete", _ -> testStoreDelete());
		registry.add("12-store-rejects-component-schema", _ -> testComponentSchema());
		registry.add("13-restart-active-goal-needs-replan", _ -> testRestartNeedsReplan());
		registry.add("14-payload-contains-goal-only", _ -> testGoalOnlyPayload());
		registry.add("15-all-store-methods-outside-engine-monitor", _ -> testStoreCallsOutsideMonitor());
		registry.add("16-blocked-attach-stop-no-late-publish", _ -> testBlockedAttachStop());
		registry.add("17-pending-attach-capacity-bounded", _ -> testPendingAttachCapacity());
		registry.add("18-blocked-mutation-keeps-other-profile-responsive", _ -> testBlockedMutationResponsiveness());
		registry.add("19-terminal-persistence-busy-detach-retention", _ -> testTerminalPersistenceRetention());
		registry.add("20-conflict-failure-distinct-and-reloadable", _ -> testConflictFailureReload());
		registry.add("21-reload-busy-and-work-excluded", _ -> testReloadBusyAndWorkExcluded());
		registry.add("22-attach-failure-does-not-publish", _ -> testAttachFailure());
		registry.add("23-terminal-conflict-and-failure-distinct", _ -> testTerminalFailureClassification());
	}

	private void testDeterministicRoundTrip()
	{
		final PhantomGoal goal = goal(0);
		final byte[] first = _codec.encode(goal);
		final byte[] second = _codec.encode(goal);
		PhantomAssertions.assertTrue(Arrays.equals(first, second), "Identical goals produced different binary payloads.");
		PhantomAssertions.assertEquals(goal, _codec.decode(first), "Goal binary round-trip changed immutable state.");
		PhantomAssertions.assertTrue(first.length <= PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Goal payload exceeded component envelope.");
	}

	private void testUnknownMagic()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[0] ^= 1;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown goal magic was accepted.");
	}

	private void testUnknownFormatVersion()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[5] = 2;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown binary format version was accepted.");
	}

	private void testUnknownGoalVersion()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[7] = 2;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown goal schema version was accepted.");
	}

	private void testTruncation()
	{
		final byte[] payload = _codec.encode(goal(0));
		for (int length : List.of(0, 4, 24, payload.length - 1))
		{
			final byte[] truncated = Arrays.copyOf(payload, length);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(truncated), "Truncated goal payload was accepted at length " + length + ".");
		}
	}

	private void testTrailingBytes()
	{
		final byte[] payload = Arrays.copyOf(_codec.encode(goal(0)), _codec.encode(goal(0)).length + 1);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Trailing goal payload bytes were accepted.");
	}

	private void testOversizedLength()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[25] = 0x7f;
		payload[26] = (byte) 0xff;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Oversized string length reached allocation.");
	}

	private void testUnknownStatus()
	{
		final byte[] payload = _codec.encode(goal(0));
		payload[24] = 99;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _codec.decode(payload), "Unknown persisted goal status was accepted.");
	}

	private void testStoreInsertLoad()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal inserted = store.insert(profile.profileId(), goal(0));
		final StoredGoal loaded = store.load(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(inserted, loaded, "goal.runtime load changed inserted state.");
		final PhantomProfileComponent component = _repository.findComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(1, component.componentSchemaVersion(), "goal.runtime used a non-v1 component envelope.");
		PhantomAssertions.assertTrue(component.payload().length <= 4096, "goal.runtime exceeded 4096 bytes.");
	}

	private void testStoreReplace()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal initial = store.insert(profile.profileId(), goal(0));
		final StoredGoal replaced = store.replace(profile.profileId(), initial.rowVersion(), goal(1));
		PhantomAssertions.assertEquals(1L, replaced.goal().revision(), "Optimistic goal replacement lost revision.");
		PhantomAssertions.assertEquals(initial.rowVersion() + 1, replaced.rowVersion(), "Goal component row version did not advance.");
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> store.replace(profile.profileId(), initial.rowVersion(), goal(2)), "Stale goal replacement was accepted.");
	}

	private void testStoreDelete()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		final StoredGoal inserted = store.insert(profile.profileId(), goal(0));
		PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> store.delete(profile.profileId(), inserted.rowVersion() + 1), "Stale goal delete was accepted.");
		store.delete(profile.profileId(), inserted.rowVersion());
		PhantomAssertions.assertTrue(store.load(profile.profileId()).isEmpty(), "Optimistic goal delete retained component.");
	}

	private void testComponentSchema()
	{
		final PhantomProfile profile = createProfile();
		_repository.insertComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE, 2, _codec.encode(goal(0)));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomGoalStateStore(_repository).load(profile.profileId()), "Unknown goal.runtime component schema was accepted.");
	}

	private void testRestartNeedsReplan()
	{
		final PhantomProfile profile = createProfile();
		final PhantomGoalStateStore store = new PhantomGoalStateStore(_repository);
		store.insert(profile.profileId(), goal(0));
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4);
		engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "Restart engine did not attach persisted goal.");
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = engine.find(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "Restart restored execution state instead of NEEDS_REPLAN.");
		PhantomAssertions.assertEquals(0L, snapshot.planId(), "Restart restored a persisted plan.");
		engine.beginStop();
		PhantomAssertions.assertTrue(engine.finishStop(), "Restart engine did not stop.");
	}

	private void testGoalOnlyPayload()
	{
		final byte[] payload = _codec.encode(goal(0));
		final String ascii = new String(payload, java.nio.charset.StandardCharsets.ISO_8859_1);
		PhantomAssertions.assertFalse(ascii.contains("action.test"), "Goal payload serialized a plan action.");
		PhantomAssertions.assertFalse(ascii.contains("candidate.test"), "Goal payload serialized candidate evaluation.");
		PhantomAssertions.assertFalse(ascii.contains("handler"), "Goal payload serialized handler state.");
	}

	private void testStoreCallsOutsideMonitor() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "step.complete"), 2);
		store.guardMonitor(engineMonitor(engine));
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, engine.attach(1), "Guarded attach failed.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.insertGoal(1, goal(0)), "Guarded insert failed.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.setGoal(1, goal(1)), "Guarded replace failed.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, engine.reload(1), "Guarded reload failed.");
		engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, engine.find(1).orElseThrow().goalStatus(), "Guarded terminal persistence failed.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.clearGoal(1), "Guarded delete failed.");
		stop(engine);
	}

	private void testBlockedAttachStop() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		store.block(StoreMethod.PROFILE_EXISTS, 1);
		final AtomicReference<AttachResult> result = new AtomicReference<>();
		final Thread attach = new Thread(() -> result.set(engine.attach(1)), "t008a-blocked-attach-stop");
		attach.start();
		store.awaitBlocked();
		final long started = System.nanoTime();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.BeginStopResult.STARTED, engine.beginStop(), "Blocked attach did not allow beginStop.");
		assertResponsive(started, "beginStop waited for blocked attach persistence.");
		PhantomAssertions.assertEquals(1, engine.snapshot().pendingAttaches(), "Blocked attach reservation was not retained.");
		PhantomAssertions.assertFalse(engine.finishStop(), "finishStop cleared a pending attach.");
		store.release();
		join(attach);
		PhantomAssertions.assertEquals(AttachResult.CANCELLED_BY_STOP, result.get(), "Blocked attach published after STOPPING.");
		PhantomAssertions.assertTrue(engine.find(1).isEmpty(), "Cancelled attach published a late runtime slot.");
		PhantomAssertions.assertTrue(engine.finishStop(), "Engine did not stop after pending attach quiesced.");
	}

	private void testPendingAttachCapacity() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1, 2);
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		store.block(StoreMethod.PROFILE_EXISTS, 1);
		final AtomicReference<AttachResult> firstResult = new AtomicReference<>();
		final Thread first = new Thread(() -> firstResult.set(engine.attach(1)), "t008a-pending-capacity");
		first.start();
		store.awaitBlocked();
		PhantomAssertions.assertEquals(AttachResult.CAPACITY_REJECTED, engine.attach(2), "Pending attach did not consume bounded capacity.");
		PhantomAssertions.assertEquals(1, engine.snapshot().pendingAttaches(), "Pending attach reservation count was not bounded.");
		store.release();
		join(first);
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, firstResult.get(), "Reserved attach did not publish after persistence completed.");
		stop(engine);
	}

	private void testBlockedMutationResponsiveness() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1, 2);
		final TokenCapturingHandler handler = new TokenCapturingHandler();
		final PhantomDecisionEngine engine = engine(store, handler, 2);
		attachWithGoal(engine, 1);
		attachWithGoal(engine, 2);

		final Thread handlerWorker = new Thread(() -> engine.accept(work(2, 1, 1, 0)), "t008a-token-profile-b");
		handlerWorker.start();
		handler.awaitEntered();
		store.block(StoreMethod.REPLACE, 1);
		final AtomicReference<MutationResult> mutationResult = new AtomicReference<>();
		final Thread mutation = new Thread(() -> mutationResult.set(engine.setGoal(1, goal(1))), "t008a-blocked-mutation-a");
		mutation.start();
		store.awaitBlocked();

		long started = System.nanoTime();
		PhantomAssertions.assertTrue(engine.find(2).isPresent(), "Blocked store prevented find for another profile.");
		PhantomAssertions.assertEquals(2, engine.list().size(), "Blocked store prevented list for another profile.");
		PhantomAssertions.assertEquals(1L, engine.snapshot().persistenceInFlight(), "Blocked mutation claim was not visible.");
		assertResponsive(started, "Snapshot reads waited for another profile's blocked store.");
		started = System.nanoTime();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.BeginStopResult.STARTED, engine.beginStop(), "Blocked mutation did not allow beginStop.");
		assertResponsive(started, "beginStop waited for blocked mutation persistence.");
		PhantomAssertions.assertTrue(handler.cancellationToken().isCancelled(), "Blocked mutation prevented cancellation-token observation for another profile.");
		PhantomAssertions.assertFalse(engine.finishStop(), "finishStop cleared blocked handler/persistence ownership.");

		handler.release();
		join(handlerWorker);
		PhantomAssertions.assertFalse(engine.finishStop(), "finishStop cleared unresolved persistence after handler quiesced.");
		store.release();
		join(mutation);
		PhantomAssertions.assertEquals(MutationResult.APPLIED, mutationResult.get(), "Committed blocked mutation was not reconciled.");
		PhantomAssertions.assertTrue(engine.finishStop(), "Engine did not stop after blocked mutation quiesced.");
	}

	private void testTerminalPersistenceRetention() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "step.complete"), 1);
		attachWithGoal(engine, 1);
		store.block(StoreMethod.REPLACE, 1);
		final Thread worker = new Thread(() -> engine.accept(work(1, 1, 1, 0)), "t008a-terminal-persistence");
		worker.start();
		store.awaitBlocked();
		final PhantomDecisionEngine.RuntimeSnapshot blocked = engine.find(1).orElseThrow();
		PhantomAssertions.assertTrue(blocked.inFlight(), "Terminal handler ownership ended before persistence.");
		PhantomAssertions.assertTrue(blocked.persistenceInFlight(), "Terminal persistence claim was not retained.");
		PhantomAssertions.assertEquals(PersistenceOperationKind.TERMINAL_COMPLETE, blocked.persistenceOperationKind(), "Terminal persistence kind was not explicit.");
		PhantomAssertions.assertEquals(MutationResult.BUSY, engine.setGoal(1, goal(1)), "Mutation raced blocked terminal persistence.");
		PhantomAssertions.assertEquals(DetachResult.PENDING, engine.detach(1), "Detach did not retain blocked terminal persistence.");
		final PhantomDecisionEngine.RuntimeSnapshot detached = engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(null, detached.selectedCandidateKey(), "Detach retained terminal candidate evidence.");
		PhantomAssertions.assertEquals(null, detached.lastResult(), "Detach retained terminal step evidence.");
		engine.beginStop();
		PhantomAssertions.assertFalse(engine.finishStop(), "finishStop cleared blocked terminal persistence.");
		store.release();
		join(worker);
		PhantomAssertions.assertTrue(engine.find(1).isEmpty(), "Pending detach retained runtime after terminal persistence quiesced.");
		PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, store.storedGoal(1).goal().status(), "Terminal persistence did not commit the completed goal.");
		PhantomAssertions.assertTrue(engine.finishStop(), "Engine did not stop after terminal persistence quiesced.");
	}

	private void testConflictFailureReload()
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		attachWithGoal(engine, 1);
		engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals("candidate.persistence", engine.find(1).orElseThrow().selectedCandidateKey(), "Conflict fixture did not publish evidence.");
		store.conflictNextReplace();
		PhantomAssertions.assertEquals(MutationResult.PERSISTENCE_CONFLICT, engine.setGoal(1, goal(1)), "Optimistic conflict was not explicit.");
		PhantomDecisionEngine.RuntimeSnapshot failed = engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD, failed.runtimeState(), "Conflict did not enter its reload-required state.");
		PhantomAssertions.assertEquals(null, failed.selectedCandidateKey(), "Conflict retained previous decision evidence.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, engine.reload(1), "Reload did not clear conflict state.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, engine.find(1).orElseThrow().runtimeState(), "Reload did not restore active goal state after conflict.");

		engine.accept(work(1, 1, 2, 1));
		PhantomAssertions.assertEquals("candidate.persistence", engine.find(1).orElseThrow().selectedCandidateKey(), "Failure fixture did not publish fresh evidence.");
		store.failNextReplace();
		PhantomAssertions.assertEquals(MutationResult.PERSISTENCE_FAILED, engine.setGoal(1, goal(1)), "Generic persistence failure was not explicit.");
		failed = engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD, failed.runtimeState(), "Generic failure was mislabeled as conflict.");
		PhantomAssertions.assertEquals(null, failed.selectedCandidateKey(), "Generic failure retained previous decision evidence.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, engine.reload(1), "Reload did not clear generic failure state.");
		store.failNextLoad();
		PhantomAssertions.assertEquals(ReloadResult.PERSISTENCE_FAILED, engine.reload(1), "Reload failure was not explicit.");
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD, engine.find(1).orElseThrow().runtimeState(), "Reload failure did not retain explicit recovery state.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, engine.reload(1), "A later explicit reload did not recover reload failure.");
		stop(engine);
	}

	private void testTerminalFailureClassification()
	{
		final ControlledGoalStore failureStore = new ControlledGoalStore(1);
		final PhantomDecisionEngine failureEngine = engine(failureStore, _ -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "step.complete"), 1);
		attachWithGoal(failureEngine, 1);
		failureStore.failNextReplace();
		failureEngine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD, failureEngine.find(1).orElseThrow().runtimeState(), "Terminal database failure was mislabeled as conflict.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, failureEngine.reload(1), "Terminal failure was not explicitly reloadable.");
		stop(failureEngine);

		final ControlledGoalStore conflictStore = new ControlledGoalStore(1);
		final PhantomDecisionEngine conflictEngine = engine(conflictStore, _ -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "step.complete"), 1);
		attachWithGoal(conflictEngine, 1);
		conflictStore.conflictNextReplace();
		conflictEngine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD, conflictEngine.find(1).orElseThrow().runtimeState(), "Terminal optimistic conflict lost its distinct state.");
		PhantomAssertions.assertEquals(ReloadResult.RELOADED, conflictEngine.reload(1), "Terminal conflict was not explicitly reloadable.");
		stop(conflictEngine);
	}

	private void testReloadBusyAndWorkExcluded() throws Exception
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		final AtomicInteger handlerCalls = new AtomicInteger();
		final PhantomDecisionEngine engine = engine(store, _ ->
		{
			handlerCalls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		}, 1);
		attachWithGoal(engine, 1);
		store.block(StoreMethod.REPLACE, 1);
		final AtomicReference<MutationResult> result = new AtomicReference<>();
		final Thread mutation = new Thread(() -> result.set(engine.setGoal(1, goal(1))), "t008a-busy-reload");
		mutation.start();
		store.awaitBlocked();
		PhantomAssertions.assertEquals(ReloadResult.BUSY, engine.reload(1), "Reload raced an in-flight persistence operation.");
		engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(0, handlerCalls.get(), "Ordinary work ran during persistence.");
		store.release();
		join(mutation);
		PhantomAssertions.assertEquals(MutationResult.APPLIED, result.get(), "Blocked mutation did not reconcile.");
		stop(engine);
	}

	private void testAttachFailure()
	{
		final ControlledGoalStore store = new ControlledGoalStore(1);
		store.failNextLoad();
		final PhantomDecisionEngine engine = engine(store, _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		PhantomAssertions.assertEquals(AttachResult.PERSISTENCE_FAILED, engine.attach(1), "Attach store failure was not explicit.");
		PhantomAssertions.assertEquals(0, engine.snapshot().attached(), "Failed attach published a runtime.");
		PhantomAssertions.assertEquals(0, engine.snapshot().pendingAttaches(), "Failed attach retained its reservation.");
		PhantomAssertions.assertTrue(engine.find(1).isEmpty(), "Failed attach retained a runtime slot.");
		stop(engine);
	}

	private static PhantomDecisionEngine engine(PhantomGoalStore store, PhantomStepHandler handler, int capacity)
	{
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.register(new PhantomDecisionCandidate(
			"candidate.persistence",
			Set.of("goal.test"),
			Set.of(PhantomActivityState.WARM),
			List.of(),
			List.of(new PhantomWeightedConsideration("score.persistence", 1, _ -> new PhantomConsideration.Evaluation(1000, "score.persistence"))),
			0,
			context -> new PhantomPlan(
				context.decisionSequence(),
				context.goal().goalId(),
				"candidate.persistence",
				List.of(new PhantomPlanStep(0, "action.persistence", null, Map.of(), 1000, 2, "reason.persistence")),
				1000,
				context.logicalNowNanos())));
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register("action.persistence", handler);
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), capacity);
		engine.start();
		return engine;
	}

	private static void attachWithGoal(PhantomDecisionEngine engine, long profileId)
	{
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, engine.attach(profileId), "Fixture profile did not attach.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.insertGoal(profileId, goal(0)), "Fixture goal did not insert.");
	}

	private static PhantomActivityWorkItem work(long profileId, long activityGeneration, long tickSequence, long logicalNowNanos)
	{
		return new PhantomActivityWorkItem(profileId, PhantomActivityState.WARM, activityGeneration, tickSequence, logicalNowNanos, PhantomActivityOverloadLevel.NORMAL);
	}

	private static Object engineMonitor(PhantomDecisionEngine engine) throws Exception
	{
		final Field field = PhantomDecisionEngine.class.getDeclaredField("_monitor");
		field.setAccessible(true);
		return field.get(engine);
	}

	private static void assertResponsive(long startedNanos, String message)
	{
		PhantomAssertions.assertTrue((System.nanoTime() - startedNanos) < TimeUnit.SECONDS.toNanos(1), message);
	}

	private static void join(Thread thread) throws InterruptedException
	{
		thread.join(TimeUnit.SECONDS.toMillis(2));
		PhantomAssertions.assertFalse(thread.isAlive(), "Persistence test worker did not quiesce.");
	}

	private static void stop(PhantomDecisionEngine engine)
	{
		engine.beginStop();
		PhantomAssertions.assertTrue(engine.finishStop(), "Persistence fixture did not stop.");
	}

	private PhantomProfile createProfile()
	{
		final PhantomProfile profile = _repository.create(null);
		_ownedProfileIds.add(profile.profileId());
		return profile;
	}

	private static PhantomGoal goal(long revision)
	{
		return new PhantomGoal(7, "goal.test", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("subject", "A"), new PhantomDomainRef("target", "B"), 100, 25, "method.test", List.of(new PhantomDomainRef("source", "A"), new PhantomDomainRef("source", "B")), new PhantomDomainRef("anchor", "C"), "purpose.test", 700, 50, 75, 123456789, Map.of("constraint.a", 1L, "constraint.b", -2L), "reason.test", revision);
	}

	private enum StoreMethod
	{
		PROFILE_EXISTS,
		LOAD,
		INSERT,
		REPLACE,
		DELETE
	}

	private static final class TokenCapturingHandler implements PhantomStepHandler
	{
		private final CountDownLatch _entered = new CountDownLatch(1);
		private final CountDownLatch _release = new CountDownLatch(1);
		private final AtomicReference<org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken> _token = new AtomicReference<>();

		@Override
		public PhantomStepResult execute(org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext context)
		{
			_token.set(context.cancellationToken());
			_entered.countDown();
			try
			{
				if (!_release.await(2, TimeUnit.SECONDS))
				{
					throw new AssertionError("Timed out waiting to release token-capturing handler.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		}

		private void awaitEntered() throws InterruptedException
		{
			PhantomAssertions.assertTrue(_entered.await(2, TimeUnit.SECONDS), "Token-capturing handler did not start.");
		}

		private org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken cancellationToken()
		{
			return _token.get();
		}

		private void release()
		{
			_release.countDown();
		}
	}

	private static final class ControlledGoalStore implements PhantomGoalStore
	{
		private final Set<Long> _profiles = ConcurrentHashMap.newKeySet();
		private final Map<Long, StoredGoal> _goals = new ConcurrentHashMap<>();
		private volatile Object _guardedMonitor;
		private volatile StoreMethod _blockedMethod;
		private volatile long _blockedProfileId;
		private volatile CountDownLatch _blocked = new CountDownLatch(0);
		private volatile CountDownLatch _release = new CountDownLatch(0);
		private volatile boolean _conflictNextReplace;
		private volatile boolean _failNextReplace;
		private volatile boolean _failNextLoad;

		private ControlledGoalStore(long... profileIds)
		{
			for (long profileId : profileIds)
			{
				_profiles.add(profileId);
			}
		}

		private void guardMonitor(Object monitor)
		{
			_guardedMonitor = monitor;
		}

		private void block(StoreMethod method, long profileId)
		{
			_blockedMethod = method;
			_blockedProfileId = profileId;
			_blocked = new CountDownLatch(1);
			_release = new CountDownLatch(1);
		}

		private void awaitBlocked() throws InterruptedException
		{
			PhantomAssertions.assertTrue(_blocked.await(2, TimeUnit.SECONDS), "Controlled goal store did not block.");
		}

		private void release()
		{
			_release.countDown();
		}

		private void conflictNextReplace()
		{
			_conflictNextReplace = true;
		}

		private void failNextReplace()
		{
			_failNextReplace = true;
		}

		private void failNextLoad()
		{
			_failNextLoad = true;
		}

		private StoredGoal storedGoal(long profileId)
		{
			return _goals.get(profileId);
		}

		@Override
		public boolean profileExists(long profileId)
		{
			guard();
			maybeBlock(StoreMethod.PROFILE_EXISTS, profileId);
			return _profiles.contains(profileId);
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			guard();
			maybeBlock(StoreMethod.LOAD, profileId);
			if (_failNextLoad)
			{
				_failNextLoad = false;
				throw new IllegalStateException("Injected load failure.");
			}
			return Optional.ofNullable(_goals.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			guard();
			maybeBlock(StoreMethod.INSERT, profileId);
			final StoredGoal stored = new StoredGoal(goal, 0);
			if (_goals.putIfAbsent(profileId, stored) != null)
			{
				throw new ConcurrentModificationException("Duplicate controlled goal.");
			}
			return stored;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			guard();
			maybeBlock(StoreMethod.REPLACE, profileId);
			if (_conflictNextReplace)
			{
				_conflictNextReplace = false;
				throw new ConcurrentModificationException("Injected optimistic conflict.");
			}
			if (_failNextReplace)
			{
				_failNextReplace = false;
				throw new IllegalStateException("Injected persistence failure.");
			}
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new ConcurrentModificationException("Stale controlled goal.");
			}
			final StoredGoal replacement = new StoredGoal(goal, expectedRowVersion + 1);
			if (!_goals.replace(profileId, current, replacement))
			{
				throw new ConcurrentModificationException("Controlled goal changed concurrently.");
			}
			return replacement;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			guard();
			maybeBlock(StoreMethod.DELETE, profileId);
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion) || !_goals.remove(profileId, current))
			{
				throw new ConcurrentModificationException("Stale controlled goal delete.");
			}
		}

		private void guard()
		{
			final Object monitor = _guardedMonitor;
			if ((monitor != null) && Thread.holdsLock(monitor))
			{
				throw new AssertionError("Goal store method executed under the decision-engine monitor.");
			}
		}

		private void maybeBlock(StoreMethod method, long profileId)
		{
			if ((_blockedMethod != method) || (_blockedProfileId != profileId))
			{
				return;
			}
			_blocked.countDown();
			try
			{
				if (!_release.await(2, TimeUnit.SECONDS))
				{
					throw new AssertionError("Timed out waiting to release controlled goal store.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
			_blockedMethod = null;
		}
	}
}
