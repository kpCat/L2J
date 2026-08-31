/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCodec;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionReceipt;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationBinding;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationResponse;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.GoalPreparation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.OutboundResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.PendingInvitation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryFact;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.ResultStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.HandoffStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationGoalRuntimePort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationGoalRuntimePort.SyncStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSubject;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveryPolicy;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;

public final class PhantomConversationExecutionSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_CODEC,
		HANDOFF_DURABILITY,
		QUERY_EXECUTION,
		PARTY_ACTIONS,
		RESTART_IDEMPOTENCY,
		RUNTIME_SYNC_GOAL030_CP2,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 20002002L;
	private static final String HASH = "A".repeat(64);
	private final Mode _mode;
	private final List<Long> _profiles = new ArrayList<>();
	private PhantomConversationExecutionCatalog _catalog;
	private PhantomProfileRepository _repository;
	private PhantomGoalStateStore _goals;

	public PhantomConversationExecutionSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return switch (_mode)
		{
			case CATALOG_CODEC -> "conversation-execution-catalog-codec";
			case LIFECYCLE_PERFORMANCE -> "conversation-execution-lifecycle-performance";
			case RUNTIME_SYNC_GOAL030_CP2 -> "conversation-decision-runtime-sync-goal030cp2";
			default -> "conversation-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
		};
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final long expectedSeed = _mode == Mode.RUNTIME_SYNC_GOAL030_CP2 ? 30003024L : SEED;
		PhantomAssertions.assertEquals(expectedSeed, context.seed(), "Checkpoint 2 execution suite used the wrong seed.");
		_catalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml"));
		final String config = System.getProperty("phantom.test.config");
		if ((config == null) || config.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(config));
		_repository = PhantomProfileRepository.open();
		_goals = new PhantomGoalStateStore(_repository);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
				{
					for (long profileId : _profiles)
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
		switch (_mode)
		{
			case CATALOG_CODEC -> catalogCodec(registry);
			case HANDOFF_DURABILITY -> handoff(registry);
			case QUERY_EXECUTION -> queries(registry);
			case PARTY_ACTIONS -> actions(registry);
			case RESTART_IDEMPOTENCY -> restart(registry);
			case RUNTIME_SYNC_GOAL030_CP2 -> runtimeSyncGoal030Cp2(registry);
			case LIFECYCLE_PERFORMANCE -> lifecycle(registry);
		}
	}

	private void catalogCodec(PhantomTestRegistry registry)
	{
		registry.add("01-strict-policy-complete-and-content-addressed", context ->
		{
			PhantomAssertions.assertEquals(Kind.QUERY, _catalog.proposal("item.source").kind(), "Item source policy kind changed.");
			PhantomAssertions.assertEquals(Kind.GOAL, _catalog.proposal("party.travel").kind(), "Party travel policy kind changed.");
			PhantomAssertions.assertEquals(Kind.PARTY_RESPONSE, _catalog.proposal("party.accept").kind(), "Party accept policy kind changed.");
			for (String proposal : List.of("party.support", "party.assist", "party.regroup"))
			{
				PhantomAssertions.assertEquals(Kind.DEFERRED, _catalog.proposal(proposal).kind(), proposal + " is not typed DEFERRED.");
			}
			PhantomAssertions.assertEquals(64, _catalog.hash().length(), "Execution catalog hash is not full SHA-256.");
			PhantomAssertions.assertTrue(_catalog.proposal("party.invite").requiredSlots().contains("target.player"), "Invite authorization lost its exact target slot.");
		});

		registry.add("02-codec-roundtrip-bound-and-fail-closed-controls", context ->
		{
			final PhantomConversationExecutionCodec codec = new PhantomConversationExecutionCodec(_catalog);
			final ExecutionEntry entry = ExecutionEntry.prepared(plan(1, 1, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1))));
			final ExecutionState state = ExecutionState.empty(_catalog.hash(), 100).add(entry);
			final byte[] encoded = codec.encode(state);
			PhantomAssertions.assertEquals(state, codec.decode(encoded), "conversation.execution roundtrip changed immutable state.");
			PhantomAssertions.assertTrue((encoded.length <= 4096) && (PhantomConversationExecutionCodec.DECLARED_WORST_CASE_BYTES <= 4096), "Execution codec exceeds the component envelope.");
			final byte[] badMagic = encoded.clone();
			badMagic[0] ^= 1;
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(badMagic), "Unknown execution magic was accepted.");
			final byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(trailing), "Trailing execution bytes were accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> entry.withOutbound(OutboundState.SENT, "query.ok", 101), "PREPARED skipped the durable DISPATCHING boundary.");
			final ExecutionEntry bound = ExecutionEntry.prepared(plan(1, 2, "party.accept", null, List.of())).withInvitation(new InvitationBinding(9, 777, 888, InvitationResponse.ACCEPT));
			PhantomAssertions.assertEquals(bound, codec.decode(codec.encode(ExecutionState.empty(_catalog.hash(), 100).add(bound))).entries().getFirst(), "Typed invitation binding did not roundtrip.");
			final List<SlotValue> four = executionSlots(4);
			PhantomAssertions.assertEquals(4, ExecutionEntry.prepared(plan(1, 3, "item.source", null, four)).arguments().size(), "Four execution arguments did not roundtrip exactly.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> ExecutionEntry.prepared(plan(1, 4, "item.source", null, executionSlots(5))), "A fifth semantic slot vanished instead of failing before handoff.");
			context.record("conversation.execution.payloadBytes", encoded.length);
		});

		registry.add("03-replay-horizon-never-evicts-a-live-terminal-plan", context ->
		{
			final List<ExecutionReceipt> receipts = new ArrayList<>();
			for (int index = 0; index < PhantomConversationExecutionModel.MAX_RECEIPTS; index++)
			{
				final ExecutionEntry prepared = ExecutionEntry.prepared(plan(1, 2_000 + index, "party.support", null, List.of()));
				final ExecutionEntry terminal = prepared.withResult(_catalog.render("action.deferred", "neutral", null), "action.deferred").withAction(ActionState.DEFERRED, 0, 0, "action.deferred", 100 + index).withOutbound(OutboundState.DISPATCHING, "action.deferred", 100 + index).withOutbound(OutboundState.SENT, "action.deferred", 100 + index);
				receipts.add(ExecutionReceipt.from(terminal));
			}
			receipts.sort(java.util.Comparator.naturalOrder());
			final ExecutionEntry pendingTerminal = ExecutionEntry.prepared(plan(1, 3_000, "party.support", null, List.of())).withResult(_catalog.render("action.deferred", "neutral", null), "action.deferred").withAction(ActionState.DEFERRED, 0, 0, "action.deferred", 120).withOutbound(OutboundState.DISPATCHING, "action.deferred", 120).withOutbound(OutboundState.SENT, "action.deferred", 120);
			final ExecutionState saturated = new ExecutionState(_catalog.hash(), 120, List.of(pendingTerminal), receipts);
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> saturated.compact(pendingTerminal.planId()), "A live replay receipt was evicted to compact a seventeenth plan.");
			PhantomAssertions.assertEquals(PhantomConversationExecutionModel.MAX_RECEIPTS, saturated.pruneReceipts(100).receipts().size(), "Replay horizon pruned a live receipt.");
			PhantomAssertions.assertEquals(0, saturated.pruneReceipts(116).receipts().size(), "Expired replay receipts were not pruned deterministically.");
		});

		registry.add("04-structured-query-facts-are-bounded-unique-and-rendered-by-catalog", context ->
		{
			final QueryResult structured = factResult(ResultStatus.COMPLETED);
			final String rendered = _catalog.renderQuery("query.ok", "neutral", structured);
			PhantomAssertions.assertTrue(rendered.contains("предмет") && rendered.contains("источник"), "Russian fact labels did not remain catalog-owned.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new QueryResult(ResultStatus.COMPLETED, List.of(new QueryFact("item.source", null, null, "drop", "game.knowledge.item"), new QueryFact("item.source", null, null, "spoil", "game.knowledge.item"))), "Duplicate structured fact keys were accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new QueryFact("item.reference", new PhantomDomainRef("item", "57"), 57L, null, "game.knowledge.item"), "A structured fact with two value representations was accepted.");
			final List<QueryFact> oversized = new ArrayList<>();
			for (int index = 0; index < 9; index++)
			{
				oversized.add(new QueryFact("item.source." + index, null, null, "drop", "game.knowledge.item"));
			}
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new QueryResult(ResultStatus.COMPLETED, oversized), "A ninth structured fact was accepted.");
		});
	}

	private void handoff(PhantomTestRegistry registry)
	{
		registry.add("01-state-and-execution-commit-atomically-with-duplicate-and-capacity-types", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan firstPlan = plan(profile.profileId(), 10, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1)));
			final ExecutionEntry first = ExecutionEntry.prepared(firstPlan);
			final var saved = store.handoff(profile.profileId(), -1, conversationState(100), first);
			PhantomAssertions.assertEquals(HandoffStatus.SAVED, saved.status(), "Atomic handoff did not save.");
			PhantomAssertions.assertTrue(_repository.findComponent(profile.profileId(), PhantomConversationModel.COMPONENT_TYPE).isPresent(), "Atomic handoff omitted conversation.state.");
			PhantomAssertions.assertTrue(_repository.findComponent(profile.profileId(), PhantomConversationExecutionModel.COMPONENT_TYPE).isPresent(), "Atomic handoff omitted conversation.execution.");
			PhantomAssertions.assertEquals(HandoffStatus.DUPLICATE, store.handoff(profile.profileId(), saved.conversation().rowVersion(), conversationState(101), first).status(), "Duplicate plan was not typed DUPLICATE.");

			final long stateVersion = saved.conversation().rowVersion();
			final long executionVersion = saved.execution().rowVersion();
			final ExecutionEntry second = ExecutionEntry.prepared(plan(profile.profileId(), 11, "party.support", null, List.of()));
			PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> store.handoff(profile.profileId(), stateVersion + 99, conversationState(102), second), "Atomic handoff accepted a stale conversation version.");
			PhantomAssertions.assertEquals(stateVersion, _repository.findComponent(profile.profileId(), PhantomConversationModel.COMPONENT_TYPE).orElseThrow().rowVersion(), "Failed atomic handoff changed conversation.state.");
			PhantomAssertions.assertEquals(executionVersion, _repository.findComponent(profile.profileId(), PhantomConversationExecutionModel.COMPONENT_TYPE).orElseThrow().rowVersion(), "Failed atomic handoff changed conversation.execution.");
		});

		registry.add("02-commit-before-signal-recovers-by-component-page", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ExecutionEntry entry = ExecutionEntry.prepared(plan(profile.profileId(), 20, "party.support", null, List.of()));
			store.handoff(profile.profileId(), -1, conversationState(100), entry);
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			drive(service, 64);
			final var stored = store.load(profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(stored.state().receipts().stream().anyMatch(receipt -> receipt.planId().equals(entry.planId())), "Restart page did not recover a committed unsignalled plan.");
			PhantomAssertions.assertTrue(service.snapshot().pages() > 0, "Execution recovery did not use component paging.");
			stop(service);
		});

		registry.add("03-receipt-reservation-rejects-before-conversation-mutation-and-expiry-reopens", context ->
		{
			for (int[] shape : List.of(new int[]
			{
				15,
				0,
				1
			}, new int[]
			{
				15,
				1,
				0
			}, new int[]
			{
				16,
				0,
				0
			}))
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final ExecutionEntry bootstrap = ExecutionEntry.prepared(plan(profile.profileId(), 40, "party.support", null, List.of()));
				final var first = store.handoff(profile.profileId(), -1, conversationState(100), bootstrap);
				final List<ExecutionEntry> entries = shape[1] == 0 ? List.of() : List.of(ExecutionEntry.prepared(plan(profile.profileId(), 41, "party.support", null, List.of())));
				final var configured = store.save(profile.profileId(), first.execution().rowVersion(), new ExecutionState(_catalog.hash(), 100, entries, receipts(profile.profileId(), shape[0], 100)));
				final var beforeConversation = _repository.findComponent(profile.profileId(), PhantomConversationModel.COMPONENT_TYPE).orElseThrow();
				final var beforeExecution = _repository.findComponent(profile.profileId(), PhantomConversationExecutionModel.COMPONENT_TYPE).orElseThrow();
				final ExecutionEntry candidate = ExecutionEntry.prepared(plan(profile.profileId(), 42, "party.support", null, List.of()));
				final var result = store.handoff(profile.profileId(), beforeConversation.rowVersion(), conversationState(101), candidate);
				PhantomAssertions.assertEquals(shape[2] == 1 ? HandoffStatus.SAVED : HandoffStatus.CAPACITY_REACHED, result.status(), "Receipt reservation matrix changed for " + shape[0] + "+" + shape[1]);
				if (shape[2] == 0)
				{
					final var afterConversation = _repository.findComponent(profile.profileId(), PhantomConversationModel.COMPONENT_TYPE).orElseThrow();
					final var afterExecution = _repository.findComponent(profile.profileId(), PhantomConversationExecutionModel.COMPONENT_TYPE).orElseThrow();
					PhantomAssertions.assertEquals(beforeConversation.rowVersion(), afterConversation.rowVersion(), "Rejected reservation changed conversation version.");
					PhantomAssertions.assertEquals(beforeExecution.rowVersion(), afterExecution.rowVersion(), "Rejected reservation changed execution version.");
					PhantomAssertions.assertTrue(Arrays.equals(beforeConversation.payload(), afterConversation.payload()) && Arrays.equals(beforeExecution.payload(), afterExecution.payload()), "Rejected reservation changed durable bytes.");
				}
				PhantomAssertions.assertTrue(configured.rowVersion() >= 0, "Capacity fixture was not durable.");
			}

			final PhantomProfile expiredProfile = profile();
			final PhantomConversationExecutionStore expiredStore = store();
			final var bootstrap = expiredStore.handoff(expiredProfile.profileId(), -1, conversationState(1), ExecutionEntry.prepared(plan(expiredProfile.profileId(), 50, "party.support", null, List.of())));
			expiredStore.save(expiredProfile.profileId(), bootstrap.execution().rowVersion(), new ExecutionState(_catalog.hash(), 1, List.of(), receipts(expiredProfile.profileId(), 16, 1)));
			final ConversationResponsePlan late = planAt(expiredProfile.profileId(), 51, "party.support", null, List.of(), 2_000);
			PhantomAssertions.assertEquals(HandoffStatus.SAVED, expiredStore.handoff(expiredProfile.profileId(), bootstrap.conversation().rowVersion(), conversationState(2_000), ExecutionEntry.prepared(late)).status(), "Expired receipt did not reopen handoff capacity.");
		});
	}

	private void queries(PhantomTestRegistry registry)
	{
		registry.add("01-all-query-kinds-produce-one-factual-outbound-and-no-goal", context ->
		{
			final List<QueryFixture> fixtures = List.of(
				new QueryFixture("party.role.query", null, List.of()),
				new QueryFixture("entity.locate", null, List.of(SlotValue.domain(SlotType.NPC, new PhantomDomainRef("npc", "30001"), -1, -1))),
				new QueryFixture("item.acquire", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1))),
				new QueryFixture("item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1))),
				new QueryFixture("content.requirements", null, List.of(SlotValue.domain(SlotType.CONTENT, new PhantomDomainRef("content", "raid.queen_ant"), -1, -1))));
			for (int index = 0; index < fixtures.size(); index++)
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final QueryFixture fixture = fixtures.get(index);
				final ConversationResponsePlan plan = plan(profile.profileId(), 100 + index, fixture.key(), fixture.target(), fixture.slots());
				final ExecutionEntry entry = ExecutionEntry.prepared(plan);
				store.handoff(profile.profileId(), -1, conversationState(100), entry);
				final MemoryPort port = new MemoryPort();
				port.query = factResult(ResultStatus.COMPLETED);
				final PhantomConversationExecutionService service = service(store, port, 101);
				service.publish(plan);
				drive(service, 64);
				final var terminal = store.load(profile.profileId()).orElseThrow().state();
				PhantomAssertions.assertTrue(terminal.receipts().stream().anyMatch(receipt -> (receipt.actionState() == ActionState.COMPLETED) && (receipt.outboundState() == OutboundState.SENT)), "Query did not reach one terminal SENT receipt: " + fixture.key());
				PhantomAssertions.assertEquals(1, port.queryCalls.get(), "Query boundary call count changed: " + fixture.key());
				PhantomAssertions.assertEquals(1, port.dispatchCalls.get(), "Query produced more or fewer than one outbound: " + fixture.key());
				PhantomAssertions.assertTrue(_goals.load(profile.profileId()).isEmpty(), "Read-only query created a gameplay goal: " + fixture.key());
				stop(service);
			}
		});

		registry.add("02-not-found-and-ambiguous-remain-typed-and-write-free", context ->
		{
			for (ResultStatus status : List.of(ResultStatus.NOT_FOUND, ResultStatus.AMBIGUOUS))
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final ConversationResponsePlan plan = plan(profile.profileId(), 200 + status.ordinal(), "entity.locate", null, List.of(SlotValue.domain(SlotType.NPC, new PhantomDomainRef("npc", "999999"), -1, -1)));
				store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
				final MemoryPort port = new MemoryPort();
				port.query = status == ResultStatus.AMBIGUOUS ? new QueryResult(status, List.of(new QueryFact("item.source.0", null, null, "drop", "game.knowledge.item"), new QueryFact("item.source.1", null, null, "spoil", "game.knowledge.item"))) : new QueryResult(status, List.of());
				final PhantomConversationExecutionService service = service(store, port, 101);
				service.publish(plan);
				drive(service, 64);
				PhantomAssertions.assertEquals(1, port.dispatchCalls.get(), "Typed query result did not send exactly one response.");
				PhantomAssertions.assertTrue(_goals.load(profile.profileId()).isEmpty(), "Typed negative query result mutated goal.runtime.");
				stop(service);
			}
		});

		registry.add("03-production-adapter-uses-current-read-only-authorities", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java"));
			for (String proposal : List.of("party.role.query", "entity.locate", "item.acquire", "item.source", "content.requirements"))
			{
				PhantomAssertions.assertTrue(source.contains("\"" + proposal + "\""), "Production query adapter omitted " + proposal);
			}
			PhantomAssertions.assertTrue(source.contains("_knowledge.query()") && source.contains("_topology.findNode") && source.contains("_party.claim"), "Production query adapter bypasses a current canonical authority.");
			PhantomAssertions.assertTrue(source.contains("new QueryFact") && source.contains("game.knowledge.item") && source.contains("topology.snapshot") && source.contains("party.claim"), "Production query adapter omitted structured authority evidence.");
			PhantomAssertions.assertFalse(source.codePoints().anyMatch(value -> ((value >= 'А') && (value <= 'я')) || (value == 'Ё') || (value == 'ё')), "Production query adapter contains Russian presentation labels or sentences.");
			for (String mutation : List.of("addItem(", "destroyItem(", "teleToLocation(", "setParty(", "doCast(", "doAttack("))
			{
				PhantomAssertions.assertFalse(source.contains(mutation), "Conversation query/dispatch adapter contains direct gameplay mutation: " + mutation);
			}
		});
	}

	private void actions(PhantomTestRegistry registry)
	{
		registry.add("01-unrelated-active-goal-is-never-overwritten", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomGoal unrelated = unrelatedGoal(7001);
			_goals.insert(profile.profileId(), unrelated);
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 300, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			service.publish(plan);
			drive(service, 64);
			PhantomAssertions.assertEquals(unrelated, _goals.load(profile.profileId()).orElseThrow().goal(), "Conversation overwrote an unrelated ACTIVE goal.");
			PhantomAssertions.assertEquals(0, port.goalPreparations.get(), "Busy arbitration crossed the goal preparation boundary.");
			stop(service);
		});

		registry.add("02-invite-goal-is-atomic-owned-and-idempotent", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 310, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			final ExecutionEntry entry = ExecutionEntry.prepared(plan);
			store.handoff(profile.profileId(), -1, conversationState(100), entry);
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			service.publish(plan);
			drive(service, 8);
			final PhantomGoal goal = _goals.load(profile.profileId()).orElseThrow().goal();
			PhantomAssertions.assertEquals("party.form", goal.goalType(), "Invite did not submit the canonical party.form Goal.");
			PhantomAssertions.assertEquals("conversation.action", goal.purposeKey(), "Invite Goal lost conversation ownership.");
			PhantomAssertions.assertEquals("conversation.party.invite", goal.reasonKey(), "Invite Goal lost exact proposal ownership.");
			final long goalId = goal.goalId();
			drive(service, 16);
			PhantomAssertions.assertEquals(goalId, _goals.load(profile.profileId()).orElseThrow().goal().goalId(), "Duplicate signal replaced the exact conversation Goal.");
			PhantomAssertions.assertEquals(1, port.goalPreparations.get(), "Exact plan prepared its Goal more than once.");
			stop(service);
		});

		registry.add("02a-exact-membership-goal-supersession-is-atomic-and-revisioned", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomGoal previous = unrelatedGoal(7101);
			_goals.insert(profile.profileId(), previous);
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 315, "party.leave", null, List.of());
			final ExecutionEntry entry = ExecutionEntry.prepared(plan);
			store.handoff(profile.profileId(), -1, conversationState(100), entry);
			final MemoryPort port = new MemoryPort();
			port.allowSupersession = true;
			final PhantomConversationExecutionService service = service(store, port, 101);
			service.publish(plan);
			drive(service, 8);

			final PhantomGoal replacement = _goals.load(profile.profileId()).orElseThrow().goal();
			final ExecutionEntry submitted = store.load(profile.profileId()).orElseThrow().state().entry(entry.planId());
			PhantomAssertions.assertEquals("party.leave", replacement.goalType(), "Allowed membership supersession installed the wrong Goal type.");
			PhantomAssertions.assertEquals(previous.revision() + 1, replacement.revision(), "Allowed membership supersession lost the previous Goal revision.");
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, submitted.actionState(), "Execution was not submitted with the replacement Goal.");
			PhantomAssertions.assertEquals(replacement.goalId(), submitted.goalId(), "Execution and replacement Goal were not committed as one ownership handoff.");
			PhantomAssertions.assertEquals(replacement.revision(), submitted.goalRevision(), "Execution lost the replacement Goal revision.");
			stop(service);
		});

		registry.add("03-accept-refuse-stale-and-deferred-are-exact-and-bounded", context ->
		{
			for (String key : List.of("party.accept", "party.refuse"))
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final ConversationResponsePlan plan = plan(profile.profileId(), 320 + key.length(), key, null, List.of());
				store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
				final MemoryPort port = new MemoryPort();
				port.pending = new PendingInvitation(9, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
				final PhantomConversationExecutionService service = service(store, port, 101);
				service.publish(plan);
				drive(service, 64);
				PhantomAssertions.assertEquals(1, port.partyResponses.get(), "Exact pending invitation did not receive one canonical response: " + key);
				PhantomAssertions.assertEquals(key.equals("party.accept"), port.accepted, "Pending invitation response kind changed: " + key);
				PhantomAssertions.assertEquals(key.equals("party.accept"), _goals.load(profile.profileId()).isPresent(), "Only ACCEPT may create the exact party.join Goal.");
				PhantomAssertions.assertEquals(9L, port.lastResponseInvitation.sequence(), "Party response did not use the durable invitation sequence.");
				if (key.equals("party.accept"))
				{
					PhantomAssertions.assertEquals(new InvitationBinding(9, 777, 888, InvitationResponse.ACCEPT), port.lastPreparedEntry.invitationBinding(), "Accept Goal was not prepared from the durable invitation binding.");
					final PhantomGoal goal = _goals.load(profile.profileId()).orElseThrow().goal();
					PhantomAssertions.assertEquals(9L, goal.constraints().get("party.invitation"), "Accept Goal lost invitation sequence evidence.");
					PhantomAssertions.assertEquals(777L, goal.constraints().get("party.requester"), "Accept Goal lost requester evidence.");
					PhantomAssertions.assertEquals(888L, goal.constraints().get("party.invitee"), "Accept Goal lost invitee evidence.");
				}
				stop(service);
			}
			for (String key : List.of("party.support", "party.assist", "party.regroup"))
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final ConversationResponsePlan plan = plan(profile.profileId(), 400 + key.length(), key, null, List.of());
				store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
				final MemoryPort port = new MemoryPort();
				final PhantomConversationExecutionService service = service(store, port, 101);
				service.publish(plan);
				drive(service, 64);
				final var receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
				PhantomAssertions.assertEquals(ActionState.DEFERRED, receipt.actionState(), key + " did not remain typed DEFERRED until Goal 024.");
				PhantomAssertions.assertTrue(_goals.load(profile.profileId()).isEmpty(), key + " mutated gameplay state.");
				stop(service);
			}
		});

		registry.add("04-goal-mappings-carry-current-party-and-topology-evidence", context ->
		{
			PhantomAssertions.assertEquals("party.form", _catalog.proposal("party.invite").goalType(), "Invite Goal mapping left strict policy data.");
			PhantomAssertions.assertEquals("party.leave", _catalog.proposal("party.leave").goalType(), "Leave Goal mapping left strict policy data.");
			PhantomAssertions.assertEquals("party.travel", _catalog.proposal("party.travel").goalType(), "Travel Goal mapping left strict policy data.");
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java"));
			for (String evidence : List.of("party.generation", "party.x", "party.y", "party.z", "party.instance", "_party.claim(profileId)", "_topology.findNode"))
			{
				PhantomAssertions.assertTrue(source.contains(evidence), "Production Goal adapter omitted exact current evidence: " + evidence);
			}
			PhantomAssertions.assertTrue(source.contains("claim.state().status() != StateStatus.LEADER") && source.contains("Set.of(StateStatus.LEADER, StateStatus.MEMBER)"), "Production Goal adapter does not reject member travel or non-membership leave before Goal submission.");
			final String decision = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java"));
			PhantomAssertions.assertTrue(decision.contains("PhantomPartyCoordinator.FORM_GOAL") && decision.contains("_coordinator.form"), "party.invite no longer reaches the current Decision/Party path.");
		});

		registry.add("05-ack-suppression-never-suppresses-actions-or-factual-results", context ->
		{
			for (String key : List.of("party.support", "party.refuse", "party.invite"))
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final PhantomDomainRef target = key.equals("party.invite") ? new PhantomDomainRef("character.object", "777") : null;
				final List<SlotValue> slots = target == null ? List.of() : List.of(SlotValue.domain(SlotType.TARGET_PLAYER, target, -1, -1));
				final ConversationResponsePlan plan = withDelivery(plan(profile.profileId(), 600 + key.length(), key, target, slots), DeliveryPolicy.SUPPRESS_ACK);
				store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
				final MemoryPort port = new MemoryPort();
				if (key.equals("party.refuse"))
				{
					port.pending = new PendingInvitation(19, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
				}
				final PhantomConversationExecutionService service = service(store, port, 101);
				service.publish(plan);
				drive(service, 32);
				final ExecutionState state = store.load(profile.profileId()).orElseThrow().state();
				PhantomAssertions.assertEquals(0, port.dispatchCalls.get(), "SUPPRESS_ACK crossed a chat handler boundary: " + key);
				PhantomAssertions.assertTrue(state.receipts().stream().anyMatch(receipt -> receipt.outboundState() == OutboundState.SUPPRESSED) || state.entries().stream().anyMatch(item -> item.outboundState() == OutboundState.SUPPRESSED), "Suppressed action lost its durable terminal outbound state: " + key);
				PhantomAssertions.assertTrue(key.equals("party.support") ? state.receipts().stream().anyMatch(receipt -> receipt.actionState() == ActionState.DEFERRED) : key.equals("party.refuse") ? port.partyResponses.get() == 1 : _goals.load(profile.profileId()).isPresent(), "Suppression changed action execution: " + key);
				stop(service);
			}

			final PhantomProfile queryProfile = profile();
			final PhantomConversationExecutionStore queryStore = store();
			final ConversationResponsePlan queryPlan = withDelivery(plan(queryProfile.profileId(), 690, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1))), DeliveryPolicy.SUPPRESS_ACK);
			final ExecutionEntry queryEntry = ExecutionEntry.prepared(queryPlan);
			PhantomAssertions.assertEquals(OutboundState.PREPARED, queryEntry.outboundState(), "A factual query was incorrectly terse-suppressed.");
			queryStore.handoff(queryProfile.profileId(), -1, conversationState(100), queryEntry);
			final MemoryPort queryPort = new MemoryPort();
			final PhantomConversationExecutionService queryService = service(queryStore, queryPort, 101);
			queryService.publish(queryPlan);
			drive(queryService, 32);
			PhantomAssertions.assertEquals(1, queryPort.dispatchCalls.get(), "Factual query result did not reach outbound dispatch.");
			stop(queryService);
		});

		registry.add("06-invitation-replacement-after-binding-is-stale", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 700, "party.refuse", null, List.of());
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort port = new MemoryPort();
			port.pending = new PendingInvitation(21, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
			final PhantomConversationExecutionService service = service(store, port, 101);
			service.publish(plan);
			service.onPulse();
			final ExecutionEntry bound = store.load(profile.profileId()).orElseThrow().state().entries().getFirst();
			PhantomAssertions.assertEquals(new InvitationBinding(21, 777, 888, InvitationResponse.REFUSE), bound.invitationBinding(), "Invitation binding was not durable before response.");
			port.pending = new PendingInvitation(22, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
			drive(service, 32);
			final ExecutionReceipt receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(ActionState.REJECTED, receipt.actionState(), "Replacement invitation was not rejected as stale.");
			PhantomAssertions.assertEquals(0, port.partyResponses.get(), "Replacement invitation crossed the response boundary.");
			stop(service);
		});
	}

	private void restart(PhantomTestRegistry registry)
	{
		registry.add("01-dispatching-recovers-uncertain-and-never-resends", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ExecutionEntry prepared = ExecutionEntry.prepared(plan(profile.profileId(), 500, "party.support", null, List.of()));
			final ExecutionEntry dispatching = prepared.withAction(ActionState.DEFERRED, 0, 0, "action.deferred", 101).withResult(_catalog.render("action.deferred", "neutral", null), "action.deferred").withOutbound(OutboundState.DISPATCHING, "action.deferred", 101);
			store.save(profile.profileId(), -1, ExecutionState.empty(_catalog.hash(), 100).add(dispatching));
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			drive(service, 32);
			final var receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(OutboundState.UNCERTAIN, receipt.outboundState(), "Recovered DISPATCHING state was not terminal UNCERTAIN.");
			PhantomAssertions.assertEquals(0, port.dispatchCalls.get(), "Recovered DISPATCHING state was blindly resent.");
			stop(service);
		});

		registry.add("02-expiry-cancels-only-exact-owned-active-goal-atomically", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 510, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService submitter = service(store, port, 101);
			submitter.publish(plan);
			drive(submitter, 4);
			stop(submitter);
			final PhantomGoal owned = _goals.load(profile.profileId()).orElseThrow().goal();
			final PhantomConversationExecutionService expirer = service(store, port, 500);
			drive(expirer, 32);
			PhantomAssertions.assertEquals(PhantomGoalStatus.ABANDONED, _goals.load(profile.profileId()).orElseThrow().goal().status(), "Expired exact conversation Goal remained ACTIVE.");
			PhantomAssertions.assertEquals(owned.goalId(), _goals.load(profile.profileId()).orElseThrow().goal().goalId(), "Expiry replaced the owned Goal identity.");
			stop(expirer);
		});

		registry.add("03-refusal-crash-without-durable-proof-recovers-uncertain", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 520, "party.refuse", null, List.of());
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort crashing = new MemoryPort();
			crashing.pending = new PendingInvitation(31, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
			crashing.throwAfterResponse = true;
			final PhantomConversationExecutionService first = service(store, crashing, 101);
			first.publish(plan);
			drive(first, 8);
			stop(first);
			final ExecutionEntry stranded = store.load(profile.profileId()).orElseThrow().state().entries().getFirst();
			PhantomAssertions.assertEquals(ActionState.PREPARED, stranded.actionState(), "Injected refusal crash incorrectly claimed a terminal response.");
			final MemoryPort restarted = new MemoryPort();
			restarted.reconciliation = ResultStatus.UNCERTAIN;
			final PhantomConversationExecutionService second = service(store, restarted, 101);
			drive(second, 32);
			final ExecutionReceipt receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(ActionState.UNCERTAIN, receipt.actionState(), "Unprovable refusal restart was reported as success.");
			PhantomAssertions.assertEquals(1, restarted.dispatchCalls.get(), "Uncertain refusal did not send its factual failure result exactly once.");
			stop(second);
		});

		registry.add("04-accept-crash-reconciles-from-exact-membership-proof", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 530, "party.accept", null, List.of());
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort crashing = new MemoryPort();
			crashing.pending = new PendingInvitation(32, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
			crashing.throwAfterResponse = true;
			final PhantomConversationExecutionService first = service(store, crashing, 101);
			first.publish(plan);
			drive(first, 10);
			stop(first);
			final ExecutionEntry stranded = store.load(profile.profileId()).orElseThrow().state().entries().getFirst();
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, stranded.actionState(), "Injected accept crash lost the durable submitted Goal state.");
			final MemoryPort restarted = new MemoryPort();
			restarted.reconciliation = ResultStatus.COMPLETED;
			final PhantomConversationExecutionService second = service(store, restarted, 101);
			drive(second, 32);
			final ExecutionReceipt receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(ActionState.COMPLETED, receipt.actionState(), "Exact accept membership proof did not reconcile to completed.");
			PhantomAssertions.assertEquals(0, restarted.partyResponses.get(), "Reconciled accept was sent a second time.");
			stop(second);
		});

		registry.add("05-exact-rejected-replay-cannot-inherit-completed-goal", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 540, "party.accept", null, List.of());
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort firstPort = new MemoryPort();
			firstPort.pending = new PendingInvitation(33, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
			final PhantomConversationExecutionService first = service(store, firstPort, 101);
			first.publish(plan);
			first.onPulse();
			first.onPulse();
			stop(first);
			final ExecutionEntry submitted = store.load(profile.profileId()).orElseThrow().state().entries().getFirst();
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, submitted.actionState(), "Rejected replay fixture did not reach durable SUBMITTED state.");
			final var owned = _goals.load(profile.profileId()).orElseThrow();
			_goals.replace(profile.profileId(), owned.rowVersion(), owned.goal().withStatus(PhantomGoalStatus.COMPLETED));
			final MemoryPort rejectedPort = new MemoryPort();
			rejectedPort.reconciliation = ResultStatus.REJECTED;
			final PhantomConversationExecutionService restarted = service(store, rejectedPort, 101);
			drive(restarted, 32);
			final ExecutionReceipt receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(ActionState.REJECTED, receipt.actionState(), "Exact REJECTED replay inherited an unrelated completed Goal outcome.");
			PhantomAssertions.assertEquals("party.stale", receipt.reasonKey(), "Exact REJECTED replay produced a success reason.");
			PhantomAssertions.assertEquals(0, rejectedPort.partyResponses.get(), "Rejected replay invoked the backend response path.");
			stop(restarted);
		});
	}

	private void runtimeSyncGoal030Cp2(PhantomTestRegistry registry)
	{
		registry.add("04-terminal-busy-unavailable-restart-exact", context ->
		{
			for (String route : List.of("missing", "reject", "expire"))
			{
				terminalRuntimeSync(context, route, false);
			}
		});
		registry.add("05-terminal-sync-failure-is-uncertain", context ->
		{
			for (String route : List.of("missing", "reject", "expire"))
			{
				terminalRuntimeSync(context, route, true);
			}
		});
		registry.add("06-goal-payload-profile-reopen", this::payloadProfileReopen);
		registry.add("01-external-goal-mutation-syncs-attached-runtime", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 90_001, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final AtomicInteger handlerCalls = new AtomicInteger();
			final PhantomDecisionEngine engine = runtimeSyncEngine(_goals, handlerCalls);
			PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "Decision runtime did not attach the external-goal profile.");
			final MemoryPort executionPort = new MemoryPort();
			final PhantomConversationExecutionService service = new PhantomConversationExecutionService(_catalog, store, _goals, executionPort, PhantomConversationGoalRuntimePort.decisionEngine(engine), () -> 101, PhantomConversationExecutionService.PhaseObserver.NONE);
			PhantomAssertions.assertTrue(service.start(), "Runtime-sync execution service did not start.");
			service.publish(plan);
			drive(service, 8);

			final StoredGoal durable = _goals.load(profile.profileId()).orElseThrow();
			final ExecutionEntry submitted = store.load(profile.profileId()).orElseThrow().state().entry(ExecutionEntry.prepared(plan).planId());
			final PhantomDecisionEngine.RuntimeSnapshot runtime = engine.find(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, submitted.actionState(), "Conversation execution was not atomically retained as SUBMITTED.");
			PhantomAssertions.assertEquals("party.form", durable.goal().goalType(), "Conversation installed the wrong durable Goal type.");
			PhantomAssertions.assertEquals(durable.goal().goalId(), runtime.goalId(), "Decision runtime did not synchronize the exact durable goalId.");
			PhantomAssertions.assertEquals(durable.goal().revision(), runtime.goalRevision(), "Decision runtime did not synchronize the exact durable revision.");
			engine.accept(runtimeSyncWork(profile.profileId(), 1));
			PhantomAssertions.assertEquals(1, handlerCalls.get(), "Synchronized runtime did not process the relevant Decision candidate.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, engine.find(profile.profileId()).orElseThrow().goalStatus(), "Processed Decision candidate did not terminalize the runtime Goal.");
			context.record("goal030cp2.sync.goalId", durable.goal().goalId());
			context.record("goal030cp2.sync.revision", durable.goal().revision());
			stop(service);
			stop(engine);
		});

		registry.add("02-busy-reload-retries-without-duplicate-goal", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 90_002, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			final ExecutionEntry prepared = ExecutionEntry.prepared(plan);
			store.handoff(profile.profileId(), -1, conversationState(100), prepared);
			final BlockingLoadGoalStore blockingGoals = new BlockingLoadGoalStore(_goals);
			final PhantomDecisionEngine engine = runtimeSyncEngine(blockingGoals, new AtomicInteger());
			PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "BUSY profile did not attach.");
			blockingGoals.blockNextLoad(profile.profileId());
			final AtomicReference<PhantomDecisionEngine.ReloadResult> blockingReload = new AtomicReference<>();
			final Thread reloadThread = new Thread(() -> blockingReload.set(engine.reload(profile.profileId())), "goal030cp2-runtime-reload");
			reloadThread.start();
			blockingGoals.awaitBlocked();

			final AtomicInteger syncCalls = new AtomicInteger();
			final AtomicReference<SyncStatus> firstStatus = new AtomicReference<>();
			final PhantomConversationGoalRuntimePort canonical = PhantomConversationGoalRuntimePort.decisionEngine(engine);
			final PhantomConversationGoalRuntimePort observed = (observedProfileId, goalId, minimumRevision) ->
			{
				if (observedProfileId != profile.profileId())
				{
					return canonical.synchronize(observedProfileId, goalId, minimumRevision);
				}
				final SyncStatus status = canonical.synchronize(observedProfileId, goalId, minimumRevision);
				if (syncCalls.getAndIncrement() == 0)
				{
					firstStatus.set(status);
				}
				return status;
			};
			final MemoryPort executionPort = new MemoryPort();
			final PhantomConversationExecutionService service = new PhantomConversationExecutionService(_catalog, store, _goals, executionPort, observed, () -> 101, PhantomConversationExecutionService.PhaseObserver.NONE);
			PhantomAssertions.assertTrue(service.start(), "BUSY retry execution service did not start.");
			service.publish(plan);
			for (int pulse = 0; (pulse < 8) && (executionPort.goalPreparations.get() == 0); pulse++)
			{
				service.onPulse();
			}

			final StoredGoal firstDurable = _goals.load(profile.profileId()).orElseThrow();
			final var firstExecution = store.load(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(SyncStatus.BUSY, firstStatus.get(), "Legitimate Decision reload BUSY was not surfaced by the runtime port.");
			PhantomAssertions.assertEquals(1, syncCalls.get(), "BUSY synchronization spun inside one execution pulse.");
			PhantomAssertions.assertEquals(1, firstExecution.state().entries().size(), "BUSY synchronization duplicated the durable execution entry.");
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, firstExecution.state().entry(prepared.planId()).actionState(), "BUSY synchronization did not retain the same SUBMITTED entry.");
			PhantomAssertions.assertEquals(1, executionPort.goalPreparations.get(), "BUSY synchronization prepared a second Goal.");

			blockingGoals.release();
			reloadThread.join(TimeUnit.SECONDS.toMillis(2));
			PhantomAssertions.assertFalse(reloadThread.isAlive(), "Blocking Decision reload did not quiesce.");
			PhantomAssertions.assertEquals(PhantomDecisionEngine.ReloadResult.RELOADED, blockingReload.get(), "Legitimate in-flight reload did not complete.");
			drive(service, 16);
			final StoredGoal retriedDurable = _goals.load(profile.profileId()).orElseThrow();
			final var retriedExecution = store.load(profile.profileId()).orElseThrow();
			final PhantomDecisionEngine.RuntimeSnapshot runtime = engine.find(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(firstDurable.goal().goalId(), retriedDurable.goal().goalId(), "BUSY retry replaced the durable goalId.");
			PhantomAssertions.assertEquals(firstDurable.goal().revision(), retriedDurable.goal().revision(), "BUSY retry changed the durable Goal revision.");
			PhantomAssertions.assertEquals(1, retriedExecution.state().entries().size(), "BUSY retry duplicated the execution entry.");
			PhantomAssertions.assertEquals(1, executionPort.goalPreparations.get(), "BUSY retry prepared the Goal twice.");
			PhantomAssertions.assertEquals(retriedDurable.goal().goalId(), runtime.goalId(), "Later retry did not synchronize the exact runtime goalId.");
			PhantomAssertions.assertEquals(retriedDurable.goal().revision(), runtime.goalRevision(), "Later retry did not synchronize the exact runtime revision.");
			context.record("goal030cp2.busy.syncCalls", syncCalls.get());
			stop(service);
			stop(engine);
		});

		registry.add("03-submitted-goal-does-not-starve-newer-query", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan goalPlan = plan(profile.profileId(), 90_003, "party.invite", new PhantomDomainRef("character.object", "777"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1)));
			final ExecutionEntry goalEntry = ExecutionEntry.prepared(goalPlan);
			store.handoff(profile.profileId(), -1, conversationState(100), goalEntry);
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			service.publish(goalPlan);
			service.onPulse();
			final var submittedStored = store.load(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, submittedStored.state().entry(goalEntry.planId()).actionState(), "Fixture Goal did not reach SUBMITTED.");

			final ConversationResponsePlan queryPlan = planAt(profile.profileId(), 90_004, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1)), 101);
			final ExecutionEntry queryEntry = ExecutionEntry.prepared(queryPlan);
			store.save(profile.profileId(), submittedStored.rowVersion(), submittedStored.state().add(queryEntry));
			service.publish(queryPlan);
			drive(service, 16);
			final var completed = store.load(profile.profileId()).orElseThrow().state();
			PhantomAssertions.assertEquals(1, port.queryCalls.get(), "Newer real ITEM57 query was starved by the SUBMITTED Goal.");
			PhantomAssertions.assertEquals(1L, port.dispatchedPlanIds.stream().filter(queryEntry.planId()::equals).count(), "Newer query did not complete one exact outbound dispatch.");
			PhantomAssertions.assertEquals(ActionState.SUBMITTED, completed.entry(goalEntry.planId()).actionState(), "QUERY processing duplicated or stole the owned SUBMITTED Goal.");
			PhantomAssertions.assertTrue(completed.receipts().stream().anyMatch(receipt -> receipt.planId().equals(queryEntry.planId()) && (receipt.actionState() == ActionState.COMPLETED) && (receipt.outboundState() == OutboundState.SENT)), "QUERY did not terminalize independently with SENT outbound evidence.");
			context.record("goal030cp2.query.itemId", 57);
			context.record("goal030cp2.query.outbound", port.dispatchCalls.get());
			stop(service);
		});
	}

	private void payloadProfileReopen(PhantomTestContext context)
	{
		final PhantomProfile profile = profile();
		_repository.insertComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE, 1, java.util.HexFormat.of().parseHex(PhantomClanGoal027Checkpoint1Suite.LEGACY_CHAT_HEX));
		final PhantomGoalStateStore reopened = new PhantomGoalStateStore(PhantomProfileRepository.open());
		final StoredGoal legacy = reopened.load(profile.profileId()).orElseThrow();
		PhantomAssertions.assertTrue(legacy.goal().payloadText() == null, "Stored legacy v1 did not reopen.");
		final String text = "Собираемся у склада, через пять минут идём на рейд. Проверьте припасы и ждите приглашения!";
		final PhantomGoal goal = new PhantomGoal(70, "clan.chat", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile.profileId())), new PhantomDomainRef("clan.id", "42"), 1, 0, null, List.of(), null, "clan.organization", 700, 0, 0, 9_000_000, Map.of("text", (long) text.codePointCount(0, text.length())), "clan.test", 1, text);
		reopened.replace(profile.profileId(), legacy.rowVersion(), goal);
		final PhantomGoalStateStore restarted = new PhantomGoalStateStore(PhantomProfileRepository.open());
		PhantomAssertions.assertEquals(goal, restarted.load(profile.profileId()).orElseThrow().goal(), "Profile store reopen lost exact v2 payload.");
		final var component = _repository.findComponent(profile.profileId(), PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(1, component.componentSchemaVersion(), "Outer component schema changed.");
		PhantomAssertions.assertTrue(component.payload().length <= 4096, "Component payload exceeded storage cap.");
		final PhantomConversationExecutionStore executionStore = store();
		final PhantomDomainRef invitee = new PhantomDomainRef("character.object", "777");
		final ConversationResponsePlan plan = plan(profile.profileId(), 92_001, "party.invite", invitee, List.of(SlotValue.domain(SlotType.TARGET_PLAYER, invitee, -1, -1)));
		executionStore.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
		final MemoryPort port = new MemoryPort();
		port.allowSupersession = true;
		port.goalPayloadText = text;
		final PhantomConversationExecutionService service = service(executionStore, port, 101);
		service.publish(plan);
		for (int pulse = 0; (pulse < 128) && (_goals.load(profile.profileId()).orElseThrow().goal().revision() < 2); pulse++)
		{
			service.onPulse();
		}
		PhantomAssertions.assertEquals(text, _goals.load(profile.profileId()).orElseThrow().goal().payloadText(), "Conversation withRevision copy lost generic payload.");
		PhantomAssertions.assertEquals(2L, _goals.load(profile.profileId()).orElseThrow().goal().revision(), "Payload copy did not exercise supersession revision helper.");
		stop(service);
		context.record("goal030cp2.chat.profileReopen", "legacy v1 -> exact Russian v2; outer schema 1; <=4096 bytes");
	}

	private void terminalRuntimeSync(PhantomTestContext context, String route, boolean failed)
	{
		final PhantomProfile profile = profile();
		final PhantomConversationExecutionStore store = store();
		final boolean expiry = route.equals("expire");
		final PhantomDomainRef invitee = expiry ? new PhantomDomainRef("character.object", "777") : null;
		final List<SlotValue> slots = expiry ? List.of(SlotValue.domain(SlotType.TARGET_PLAYER, invitee, -1, -1)) : List.of();
		final ConversationResponsePlan plan = plan(profile.profileId(), 91_001, expiry ? "party.invite" : "party.accept", invitee, slots);
		final ExecutionEntry prepared = ExecutionEntry.prepared(plan);
		store.handoff(profile.profileId(), -1, conversationState(100), prepared);
		final MemoryPort port = new MemoryPort();
		port.pending = new PendingInvitation(41, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
		final PhantomConversationExecutionService submitter = service(store, port, 101);
		submitter.publish(plan);
		for (int pulse = 0; (pulse < 8) && (port.goalPreparations.get() == 0); pulse++)
		{
			submitter.onPulse();
		}
		stop(submitter);
		final StoredGoal active = _goals.load(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(ActionState.SUBMITTED, store.load(profile.profileId()).orElseThrow().state().entry(prepared.planId()).actionState(), "Terminal fixture did not submit.");
		port.pending = route.equals("reject") ? port.pending : null;
		port.response = ResultStatus.REJECTED;
		port.reconciliation = ResultStatus.REJECTED;
		final PhantomDecisionEngine engine = runtimeSyncEngine(_goals, new AtomicInteger());
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profile.profileId()), "Terminal fixture runtime did not attach.");
		final AtomicReference<SyncStatus> result = new AtomicReference<>(failed ? SyncStatus.FAILED : SyncStatus.BUSY);
		final AtomicInteger syncCalls = new AtomicInteger();
		final PhantomConversationGoalRuntimePort blocked = (synchronizedProfileId, goalId, revision) ->
		{
			if (synchronizedProfileId != profile.profileId())
			{
				return SyncStatus.UNAVAILABLE;
			}
			syncCalls.incrementAndGet();
			PhantomAssertions.assertEquals(active.goal().goalId(), goalId, "Terminal sync changed identity.");
			PhantomAssertions.assertEquals(active.goal().revision() + 1, revision, "Terminal sync used the old revision.");
			return result.get();
		};
		final long minute = expiry ? 500 : 101;
		final PhantomConversationExecutionService terminal = new PhantomConversationExecutionService(_catalog, store, _goals, port, blocked, () -> minute, PhantomConversationExecutionService.PhaseObserver.NONE);
		PhantomAssertions.assertTrue(terminal.start(), "Terminal service did not start.");
		terminal.publish(plan);
		drive(terminal, failed ? 16 : 8);
		final StoredGoal abandoned = _goals.load(profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(PhantomGoalStatus.ABANDONED, abandoned.goal().status(), "Terminal route did not abandon its owned goal.");
		PhantomAssertions.assertTrue(syncCalls.get() > 0, "Terminal route never called runtime sync.");
		if (failed)
		{
			final ExecutionReceipt receipt = store.load(profile.profileId()).orElseThrow().state().receipts().getFirst();
			PhantomAssertions.assertEquals(ActionState.UNCERTAIN, receipt.actionState(), "FAILED sync was reported as a safe terminal result.");
		}
		else
		{
			final var pending = store.load(profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(pending.state().receipts().isEmpty(), "BUSY terminal was compacted before runtime sync.");
			PhantomAssertions.assertEquals(abandoned.goal().revision(), pending.state().entry(prepared.planId()).goalRevision(), "Durable terminal entry lost exact revision.");
			final int responses = port.partyResponses.get();
			result.set(SyncStatus.UNAVAILABLE);
			drive(terminal, 12);
			PhantomAssertions.assertEquals(pending.rowVersion(), store.load(profile.profileId()).orElseThrow().rowVersion(), "Retry rewrote the same durable execution.");
			PhantomAssertions.assertEquals(abandoned.rowVersion(), _goals.load(profile.profileId()).orElseThrow().rowVersion(), "Retry mutated the goal twice.");
			PhantomAssertions.assertEquals(responses, port.partyResponses.get(), "Retry repeated the Party mutation.");
			if (route.equals("missing"))
			{
				final ConversationResponsePlan queryPlan = planAt(profile.profileId(), 91_002, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1)), minute);
				final var beforeQuery = store.load(profile.profileId()).orElseThrow();
				store.save(profile.profileId(), beforeQuery.rowVersion(), beforeQuery.state().add(ExecutionEntry.prepared(queryPlan)));
				terminal.publish(queryPlan);
				drive(terminal, 16);
				PhantomAssertions.assertEquals(1, port.queryCalls.get(), "Terminal sync retry starved a newer QUERY.");
				PhantomAssertions.assertTrue(store.load(profile.profileId()).orElseThrow().state().entry(prepared.planId()) != null, "QUERY compacted unsynchronized terminal goal.");
			}
			stop(terminal);
			final PhantomConversationExecutionService restarted = new PhantomConversationExecutionService(_catalog, store(), _goals, port, PhantomConversationGoalRuntimePort.decisionEngine(engine), () -> minute, PhantomConversationExecutionService.PhaseObserver.NONE);
			PhantomAssertions.assertTrue(restarted.start(), "Terminal restart failed.");
			restarted.publish(plan);
			drive(restarted, 32);
			final var runtime = engine.find(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(abandoned.goal().revision(), runtime.goalRevision(), "Restart did not synchronize the exact terminal revision.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.ABANDONED, runtime.goalStatus(), "Runtime remained active after terminal sync.");
			final var completed = store.load(profile.profileId()).orElseThrow().state();
			PhantomAssertions.assertTrue(completed.entries().isEmpty(), "Synchronized terminal was not compacted.");
			PhantomAssertions.assertEquals(1L, completed.receipts().stream().filter(receipt -> receipt.planId().equals(prepared.planId())).count(), "Terminal replay duplicated its receipt.");
			restarted.publish(plan);
			drive(restarted, 4);
			PhantomAssertions.assertEquals(abandoned.rowVersion(), _goals.load(profile.profileId()).orElseThrow().rowVersion(), "Terminal replay changed durable Goal.");
			PhantomAssertions.assertEquals(expiry ? 0 : route.equals("missing") ? 2 : 1, port.dispatchCalls.get(), "Terminal replay spammed outbound text.");
			stop(restarted);
		}
		context.record("goal030cp2.terminal." + route + (failed ? ".failed" : ".restart"), "exact revision; no duplicate mutation; " + (failed ? "UNCERTAIN" : "BUSY/UNAVAILABLE retained; synchronized before compact"));
		stop(terminal);
		stop(engine);
	}

	private static PhantomDecisionEngine runtimeSyncEngine(PhantomGoalStore goals, AtomicInteger handlerCalls)
	{
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.register(new PhantomDecisionCandidate(
			"candidate.goal030cp2.runtime-sync",
			Set.of("party.form"),
			Set.of(PhantomActivityState.WARM),
			List.of(),
			List.of(new PhantomWeightedConsideration("score.goal030cp2.runtime-sync", 1, ignored -> new PhantomConsideration.Evaluation(1000, "score.goal030cp2.runtime-sync"))),
			0,
			planning -> new PhantomPlan(planning.decisionSequence(), planning.goal().goalId(), "candidate.goal030cp2.runtime-sync", List.of(new PhantomPlanStep(0, "action.goal030cp2.runtime-sync", null, Map.of(), 1000, 1, "reason.goal030cp2.runtime-sync")), 1000, planning.logicalNowNanos())));
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register("action.goal030cp2.runtime-sync", ignored ->
		{
			handlerCalls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "goal030cp2.runtime-sync.complete");
		});
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1);
		engine.start();
		return engine;
	}

	private static PhantomActivityWorkItem runtimeSyncWork(long profileId, long tickSequence)
	{
		return new PhantomActivityWorkItem(profileId, PhantomActivityState.WARM, 1, tickSequence, tickSequence, PhantomActivityOverloadLevel.NORMAL);
	}

	private static void stop(PhantomDecisionEngine engine)
	{
		engine.beginStop();
		PhantomAssertions.assertTrue(engine.finishStop(), "Runtime-sync Decision engine did not finish stop.");
	}

	private void lifecycle(PhantomTestRegistry registry)
	{
		registry.add("01-10k-durable-envelope-records-and-bounded-shared-pulses", context ->
		{
			final PhantomConversationExecutionCodec codec = new PhantomConversationExecutionCodec(_catalog);
			long bytes = 0;
			for (int index = 0; index < 10_000; index++)
			{
				final ExecutionEntry entry = ExecutionEntry.prepared(plan(1, 10_000L + index, "party.support", null, List.of()));
				final byte[] payload = codec.encode(ExecutionState.empty(_catalog.hash(), 100).add(entry));
				bytes += payload.length;
				PhantomAssertions.assertEquals(entry.planId(), codec.decode(payload).entries().getFirst().planId(), "Durable execution envelope changed at record " + index);
			}
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ConversationResponsePlan plan = plan(profile.profileId(), 20_000, "party.support", null, List.of());
			store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
			final MemoryPort port = new MemoryPort();
			final PhantomConversationExecutionService service = service(store, port, 101);
			for (int index = 0; index < 10_000; index++)
			{
				service.publish(plan);
			}
			drive(service, 128);
			final var snapshot = service.snapshot();
			PhantomAssertions.assertTrue(snapshot.maximumOperationsPerPulse() <= _catalog.limits().operationsPerPulse(), "Execution pulse exceeded its configured operation budget.");
			PhantomAssertions.assertTrue(snapshot.ready() <= _catalog.limits().executionQueue(), "Execution ready queue exceeded its hard bound.");
			PhantomAssertions.assertTrue(snapshot.pages() > 0, "Execution startup did not use bounded component paging.");
			context.record("conversation.execution.records", 10_000);
			context.record("conversation.execution.encodedBytes", bytes);
			stop(service);
		});

		registry.add("02-shutdown-at-every-boundary-drains-claims-without-worker", context ->
		{
			for (PhantomConversationExecutionService.Phase phase : PhantomConversationExecutionService.Phase.values())
			{
				final PhantomProfile profile = profile();
				final PhantomConversationExecutionStore store = store();
				final String proposal = switch (phase)
				{
					case QUERY -> "item.source";
					case GOAL_SUBMIT, GOAL_OBSERVE, DELAY_PROMOTE -> "party.invite";
					case PARTY_RESPONSE -> "party.refuse";
					default -> "party.support";
				};
				final PhantomDomainRef target = proposal.equals("party.invite") ? new PhantomDomainRef("character.object", "777") : null;
				final List<SlotValue> slots = proposal.equals("item.source") ? List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1)) : proposal.equals("party.invite") ? List.of(SlotValue.domain(SlotType.TARGET_PLAYER, target, -1, -1)) : List.of();
				final ConversationResponsePlan plan = plan(profile.profileId(), 30_000L + phase.ordinal(), proposal, target, slots);
				store.handoff(profile.profileId(), -1, conversationState(100), ExecutionEntry.prepared(plan));
				final PhantomConversationExecutionService[] holder = new PhantomConversationExecutionService[1];
				final AtomicBoolean reached = new AtomicBoolean();
				final MemoryPort port = new MemoryPort();
				if (proposal.equals("party.refuse"))
				{
					port.pending = new PendingInvitation(9, 777, 888, "Speaker", new PhantomDomainRef("character.object", "777"));
				}
				final PhantomConversationExecutionService service = new PhantomConversationExecutionService(_catalog, store, _goals, port, () -> 101, (current, ignored) ->
				{
					if (current == phase)
					{
						reached.set(true);
						holder[0].beginStop();
					}
				});
				holder[0] = service;
				PhantomAssertions.assertTrue(service.start(), "Boundary shutdown service did not start.");
				service.publish(plan);
				for (int pulse = 0; (pulse < 32) && !reached.get() && (service.snapshot().state() == PhantomConversationExecutionService.State.RUNNING); pulse++)
				{
					service.onPulse();
				}
				PhantomAssertions.assertTrue(reached.get(), "Boundary shutdown fixture did not reach " + phase);
				PhantomAssertions.assertTrue(service.finishStop(), "Boundary shutdown did not drain at " + phase);
				PhantomAssertions.assertEquals(0, service.snapshot().claims(), "Boundary shutdown retained a claim at " + phase);
			}
		});

		registry.add("03-deterministic-priority-keeps-work-fair-and-capacity-waits-for-expiry", context ->
		{
			final PhantomProfile profile = profile();
			final PhantomConversationExecutionStore store = store();
			final ExecutionEntry blocked = terminal(ExecutionEntry.prepared(plan(profile.profileId(), 40_000, "party.support", null, List.of())), 101);
			final ExecutionEntry query = ExecutionEntry.prepared(plan(profile.profileId(), 40_001, "item.source", null, List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1))));
			final List<ExecutionEntry> entries = new ArrayList<>(List.of(blocked, query));
			entries.sort(java.util.Comparator.comparing(ExecutionEntry::planId));
			store.save(profile.profileId(), -1, new ExecutionState(_catalog.hash(), 101, entries, receipts(profile.profileId(), 16, 100)));
			final MemoryPort port = new MemoryPort();
			final AtomicInteger targetLoads = new AtomicInteger();
			final PhantomConversationExecutionService service = new PhantomConversationExecutionService(_catalog, store, _goals, port, () -> 101, (phase, profileId) ->
			{
				if ((phase == PhantomConversationExecutionService.Phase.LOAD) && (profileId == profile.profileId()))
				{
					targetLoads.incrementAndGet();
				}
			});
			PhantomAssertions.assertTrue(service.start(), "Fairness execution service did not start.");
			drive(service, 16);
			PhantomAssertions.assertEquals(1, port.queryCalls.get(), "Capacity-blocked terminal entry starved a PREPARED query.");
			PhantomAssertions.assertTrue(port.dispatchCalls.get() >= 1, "Capacity-blocked terminal entry starved query outbound.");
			final int before = targetLoads.get();
			final int dispatchesBefore = port.dispatchCalls.get();
			drive(service, 100);
			PhantomAssertions.assertEquals(before, targetLoads.get(), "Capacity-blocked terminal entry spun once per pulse before receipt expiry.");
			PhantomAssertions.assertEquals(dispatchesBefore, port.dispatchCalls.get(), "Capacity wait repeated an outbound dispatch.");
			stop(service);

			final PhantomProfile recoveredProfile = profile();
			final PhantomConversationExecutionStore recoveredStore = store();
			final ExecutionEntry dispatching = ExecutionEntry.prepared(plan(recoveredProfile.profileId(), 41_000, "party.support", null, List.of())).withAction(ActionState.DEFERRED, 0, 0, "action.deferred", 101).withOutbound(OutboundState.DISPATCHING, "action.deferred", 101);
			final ConversationResponsePlan recoveredPlan = plan(recoveredProfile.profileId(), 41_001, "party.support", null, List.of());
			final ExecutionEntry prepared = ExecutionEntry.prepared(recoveredPlan);
			final List<ExecutionEntry> recoveredEntries = new ArrayList<>(List.of(prepared, dispatching));
			recoveredEntries.sort(java.util.Comparator.comparing(ExecutionEntry::planId));
			recoveredStore.save(recoveredProfile.profileId(), -1, new ExecutionState(_catalog.hash(), 101, recoveredEntries, List.of()));
			final MemoryPort recoveredPort = new MemoryPort();
			final PhantomConversationExecutionService recoveredService = service(recoveredStore, recoveredPort, 101);
			recoveredService.publish(recoveredPlan);
			recoveredService.onPulse();
			PhantomAssertions.assertTrue(recoveredStore.load(recoveredProfile.profileId()).orElseThrow().state().receipts().stream().anyMatch(receipt -> receipt.outboundState() == OutboundState.UNCERTAIN), "Recovered DISPATCHING was not selected before lexicographically ordered work.");
			PhantomAssertions.assertEquals(0, recoveredPort.dispatchCalls.get(), "Recovered DISPATCHING was resent.");
			stop(recoveredService);
		});
	}

	private PhantomConversationExecutionStore store()
	{
		return new PhantomConversationExecutionStore(_repository, _catalog);
	}

	private PhantomProfile profile()
	{
		final PhantomProfile profile = _repository.create(null);
		_profiles.add(profile.profileId());
		return profile;
	}

	private PhantomConversationExecutionService service(PhantomConversationExecutionStore store, MemoryPort port, long now)
	{
		final PhantomConversationExecutionService service = new PhantomConversationExecutionService(_catalog, store, _goals, port, () -> now, PhantomConversationExecutionService.PhaseObserver.NONE);
		PhantomAssertions.assertTrue(service.start(), "Conversation execution service did not start.");
		return service;
	}

	private static void drive(PhantomConversationExecutionService service, int pulses)
	{
		for (int pulse = 0; pulse < pulses; pulse++)
		{
			service.onPulse();
		}
	}

	private static void stop(PhantomConversationExecutionService service)
	{
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Conversation execution service did not finish stop.");
	}

	private static ConversationState conversationState(long minute)
	{
		return new ConversationState(HASH, HASH, HASH, HASH, HASH, HASH, HASH, minute, List.of(), List.of());
	}

	private static ConversationResponsePlan plan(long profileId, long dispatchId, String proposalKey, PhantomDomainRef target, List<SlotValue> slots)
	{
		return planAt(profileId, dispatchId, proposalKey, target, slots, 100);
	}

	private static ConversationResponsePlan planAt(long profileId, long dispatchId, String proposalKey, PhantomDomainRef target, List<SlotValue> slots, long createdMinute)
	{
		final String observation = PhantomConversationModel.sha256("observation|" + profileId + '|' + dispatchId);
		final String semantic = PhantomConversationModel.sha256("semantic|" + profileId + '|' + dispatchId + '|' + proposalKey);
		final ConversationActionProposal proposal = proposalKey == null ? null : new ConversationActionProposal(proposalKey, new PhantomDomainRef("profile", Long.toString(profileId)), target, slots, semantic, observation, 9000, createdMinute, createdMinute + 120, Authorization.CHECKPOINT_2_REQUIRED);
		return new ConversationResponsePlan(profileId, dispatchId, observation, ChatType.WHISPER, new ConversationSubject(new PhantomDomainRef("character.object", "777")), semantic, proposalKey == null ? "ack.accepted" : proposalKey.endsWith("query") || proposalKey.contains("source") || proposalKey.contains("acquire") || proposalKey.contains("requirements") || proposalKey.contains("locate") ? "ack.query_proposed" : "ack.action_proposed", "neutral", "Ответ подготовлен.", proposal, createdMinute + 1, List.of());
	}

	private static ConversationResponsePlan withDelivery(ConversationResponsePlan plan, DeliveryPolicy deliveryPolicy)
	{
		return new ConversationResponsePlan(plan.ownerProfileId(), plan.dispatchId(), plan.observationHash(), plan.channel(), plan.counterpart(), plan.semanticResultHash(), plan.responseAct(), plan.style(), plan.renderedText(), plan.proposal(), deliveryPolicy, plan.cooldownUntilMinute(), plan.evidence());
	}

	private List<ExecutionReceipt> receipts(long profileId, int count, long terminalMinute)
	{
		final List<ExecutionReceipt> result = new ArrayList<>();
		for (int index = 0; index < count; index++)
		{
			final ExecutionEntry prepared = ExecutionEntry.prepared(planAt(profileId, 80_000L + index, "party.support", null, List.of(), Math.max(0, terminalMinute - 1)));
			final ExecutionEntry terminal = prepared.withAction(ActionState.DEFERRED, 0, 0, "action.deferred", terminalMinute).withOutbound(OutboundState.DISPATCHING, "action.deferred", terminalMinute).withOutbound(OutboundState.SENT, "action.deferred", terminalMinute);
			result.add(ExecutionReceipt.from(terminal));
		}
		result.sort(java.util.Comparator.naturalOrder());
		return List.copyOf(result);
	}

	private ExecutionEntry terminal(ExecutionEntry prepared, long terminalMinute)
	{
		return prepared.withResult(_catalog.render("action.deferred", "neutral", null), "action.deferred").withAction(ActionState.DEFERRED, 0, 0, "action.deferred", terminalMinute).withOutbound(OutboundState.DISPATCHING, "action.deferred", terminalMinute).withOutbound(OutboundState.SENT, "action.deferred", terminalMinute);
	}

	private static List<SlotValue> executionSlots(int count)
	{
		return List.of( //
			SlotValue.domain(SlotType.CONTENT, new PhantomDomainRef("content", "raid.queen_ant"), -1, -1), //
			SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1), //
			SlotValue.domain(SlotType.NPC, new PhantomDomainRef("npc", "30001"), -1, -1), //
			SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "777"), -1, -1), //
			SlotValue.domain(SlotType.TOPOLOGY_NODE, new PhantomDomainRef("topology.node", "giran"), -1, -1)).subList(0, count);
	}

	private static PhantomGoal unrelatedGoal(long goalId)
	{
		return new PhantomGoal(goalId, "progression.level", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), null, 1, 0, null, List.of(), null, "progression", 500, 0, 0, 0, Map.of(), "progression.level", 0);
	}

	private static QueryResult factResult(ResultStatus status)
	{
		return new QueryResult(status, List.of(new QueryFact("item.reference", new PhantomDomainRef("item", "57"), null, null, "game.knowledge.item"), new QueryFact("item.source.0", null, null, "drop", "game.knowledge.item")));
	}

	private record QueryFixture(String key, PhantomDomainRef target, List<SlotValue> slots)
	{
	}

	private static final class BlockingLoadGoalStore implements PhantomGoalStore
	{
		private final PhantomGoalStore _delegate;
		private volatile long _blockedProfileId;
		private volatile CountDownLatch _blocked = new CountDownLatch(0);
		private volatile CountDownLatch _release = new CountDownLatch(0);

		private BlockingLoadGoalStore(PhantomGoalStore delegate)
		{
			_delegate = delegate;
		}

		private void blockNextLoad(long profileId)
		{
			_blockedProfileId = profileId;
			_blocked = new CountDownLatch(1);
			_release = new CountDownLatch(1);
		}

		private void awaitBlocked() throws InterruptedException
		{
			PhantomAssertions.assertTrue(_blocked.await(2, TimeUnit.SECONDS), "Decision reload did not enter the controlled GoalStore load.");
		}

		private void release()
		{
			_release.countDown();
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return _delegate.profileExists(profileId);
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			if (_blockedProfileId == profileId)
			{
				_blocked.countDown();
				try
				{
					if (!_release.await(2, TimeUnit.SECONDS))
					{
						throw new AssertionError("Timed out waiting to release controlled Decision reload.");
					}
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
				_blockedProfileId = 0;
			}
			return _delegate.load(profileId);
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			return _delegate.insert(profileId, goal);
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			return _delegate.replace(profileId, expectedRowVersion, goal);
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			_delegate.delete(profileId, expectedRowVersion);
		}
	}

	private static final class MemoryPort implements PhantomConversationExecutionPort
	{
		private final AtomicInteger queryCalls = new AtomicInteger();
		private final AtomicInteger goalPreparations = new AtomicInteger();
		private final AtomicInteger partyResponses = new AtomicInteger();
		private final AtomicInteger dispatchCalls = new AtomicInteger();
		private final List<String> dispatchedPlanIds = new ArrayList<>();
		private QueryResult query = factResult(ResultStatus.COMPLETED);
		private PendingInvitation pending;
		private ExecutionEntry lastPreparedEntry;
		private PendingInvitation lastResponseInvitation;
		private ResultStatus reconciliation = ResultStatus.UNCERTAIN;
		private ResultStatus response = ResultStatus.COMPLETED;
		private boolean allowSupersession;
		private String goalPayloadText;
		private boolean throwAfterResponse;
		private boolean accepted;

		@Override
		public QueryResult query(long profileId, ExecutionEntry entry)
		{
			queryCalls.incrementAndGet();
			return query;
		}

		@Override
		public GoalPreparation prepareGoal(long profileId, ExecutionEntry entry, long goalId, long nowMinute)
		{
			goalPreparations.incrementAndGet();
			lastPreparedEntry = entry;
			final Map<String, Long> constraints = new java.util.TreeMap<>();
			for (int index = 0; index < 4; index++)
			{
				constraints.put("conversation.plan." + index, Long.parseUnsignedLong(entry.planId().substring(index * 16, (index + 1) * 16), 16));
			}
			if (entry.invitationBinding() != null)
			{
				constraints.put("party.invitation", entry.invitationBinding().sequence());
				constraints.put("party.requester", (long) entry.invitationBinding().requesterObjectId());
				constraints.put("party.invitee", (long) entry.invitationBinding().inviteeObjectId());
			}
			final String type = entry.proposalKey().equals("party.accept") ? "party.join" : entry.proposalKey().equals("party.invite") ? "party.form" : entry.proposalKey();
			final PhantomGoal goal = new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("party", "general"), entry.target(), 1, 0, null, List.of(), null, "conversation.action", 700, 0, 0, (nowMinute + 120) * 60000L, constraints, "conversation." + entry.proposalKey(), 0, goalPayloadText);
			return new GoalPreparation(ResultStatus.COMPLETED, goal);
		}

		@Override
		public boolean allowsGoalSupersession(long profileId, ExecutionEntry entry, PhantomGoal previousGoal)
		{
			return allowSupersession;
		}

		@Override
		public Optional<PendingInvitation> pendingInvitation(long profileId)
		{
			return Optional.ofNullable(pending);
		}

		@Override
		public ResultStatus respondToPending(long profileId, PendingInvitation invitation, boolean accept, String planId)
		{
			partyResponses.incrementAndGet();
			lastResponseInvitation = invitation;
			accepted = accept;
			pending = null;
			if (throwAfterResponse)
			{
				throw new IllegalStateException("Injected post-response failure.");
			}
			return response;
		}

		@Override
		public ResultStatus reconcileInvitation(long profileId, ExecutionEntry entry)
		{
			if ((pending != null) && (entry.invitationBinding() != null))
			{
				final InvitationBinding binding = entry.invitationBinding();
				return (pending.sequence() == binding.sequence()) && (pending.requesterObjectId() == binding.requesterObjectId()) && (pending.inviteeObjectId() == binding.inviteeObjectId()) ? ResultStatus.REJECTED : ResultStatus.STALE;
			}
			return reconciliation;
		}

		@Override
		public OutboundResult dispatch(long profileId, ExecutionEntry entry)
		{
			dispatchCalls.incrementAndGet();
			dispatchedPlanIds.add(entry.planId());
			return new OutboundResult(ResultStatus.COMPLETED, 1);
		}
	}
}
