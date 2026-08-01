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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCodec;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionReceipt;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.GoalPreparation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.OutboundResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.PendingInvitation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.ResultStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.HandoffStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSubject;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
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
			default -> "conversation-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
		};
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Checkpoint 2 execution suite used the wrong seed.");
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
				port.query = new QueryResult(ResultStatus.COMPLETED, "источник=текущие данные");
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
				port.query = new QueryResult(status, status == ResultStatus.AMBIGUOUS ? "вариант=1;вариант=2" : "");
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
			final String decision = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java"));
			PhantomAssertions.assertTrue(decision.contains("PhantomPartyCoordinator.FORM_GOAL") && decision.contains("_coordinator.form"), "party.invite no longer reaches the current Decision/Party path.");
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
		final String observation = PhantomConversationModel.sha256("observation|" + profileId + '|' + dispatchId);
		final String semantic = PhantomConversationModel.sha256("semantic|" + profileId + '|' + dispatchId + '|' + proposalKey);
		final ConversationActionProposal proposal = proposalKey == null ? null : new ConversationActionProposal(proposalKey, new PhantomDomainRef("profile", Long.toString(profileId)), target, slots, semantic, observation, 9000, 100, 220, Authorization.CHECKPOINT_2_REQUIRED);
		return new ConversationResponsePlan(profileId, dispatchId, observation, ChatType.WHISPER, new ConversationSubject(new PhantomDomainRef("character.object", "777")), semantic, proposalKey == null ? "ack.accepted" : proposalKey.endsWith("query") || proposalKey.contains("source") || proposalKey.contains("acquire") || proposalKey.contains("requirements") || proposalKey.contains("locate") ? "ack.query_proposed" : "ack.action_proposed", "neutral", "Ответ подготовлен.", proposal, 101, List.of());
	}

	private static PhantomGoal unrelatedGoal(long goalId)
	{
		return new PhantomGoal(goalId, "progression.level", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), null, 1, 0, null, List.of(), null, "progression", 500, 0, 0, 0, Map.of(), "progression.level", 0);
	}

	private record QueryFixture(String key, PhantomDomainRef target, List<SlotValue> slots)
	{
	}

	private static final class MemoryPort implements PhantomConversationExecutionPort
	{
		private final AtomicInteger queryCalls = new AtomicInteger();
		private final AtomicInteger goalPreparations = new AtomicInteger();
		private final AtomicInteger partyResponses = new AtomicInteger();
		private final AtomicInteger dispatchCalls = new AtomicInteger();
		private QueryResult query = new QueryResult(ResultStatus.COMPLETED, "факт=подтверждён");
		private PendingInvitation pending;
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
			final Map<String, Long> constraints = new java.util.TreeMap<>();
			for (int index = 0; index < 4; index++)
			{
				constraints.put("conversation.plan." + index, Long.parseUnsignedLong(entry.planId().substring(index * 16, (index + 1) * 16), 16));
			}
			final String type = entry.proposalKey().equals("party.accept") ? "party.join" : entry.proposalKey().equals("party.invite") ? "party.form" : entry.proposalKey();
			final PhantomGoal goal = new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("party", "general"), entry.target(), 1, 0, null, List.of(), null, "conversation.action", 700, 0, 0, (nowMinute + 120) * 60000L, constraints, "conversation." + entry.proposalKey(), 0);
			return new GoalPreparation(ResultStatus.COMPLETED, goal);
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
			accepted = accept;
			pending = null;
			return ResultStatus.COMPLETED;
		}

		@Override
		public OutboundResult dispatch(long profileId, ExecutionEntry entry)
		{
			dispatchCalls.incrementAndGet();
			return new OutboundResult(ResultStatus.COMPLETED, 1);
		}
	}
}
