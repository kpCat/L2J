/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationIntent;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime;

/** Scalar bounded child in the existing Goal codec; no separate inventory, scheduler or persistence engine. */
public final class PhantomInventoryMaintenanceIntent
{
	public static final String TYPE = "inventory.maintenance";
	public static final int MAXIMUM_OPERATIONS = 4;
	private static final String PURPOSE = "supplies.autonomous";
	private static final String SOURCE = "inventory.native";
	private static final Set<String> OWN_KEYS = Set.of("source.index", "maintenance.failed", "maintenance.buy_checked", "maintenance.pressure");
	private PhantomInventoryMaintenanceIntent() { }

	public record Operation(OperationKind kind, int npcId, int npcObjectId, int listId, int itemId, int itemObjectId, long count)
	{
		public Operation
		{
			if (kind == null || count < 1 || count > 600 || itemId <= 0 || kind == OperationKind.TELEPORT) { throw new IllegalArgumentException("Invalid bounded maintenance operation."); }
			if (kind == OperationKind.CRYSTALLIZE ? npcId != 0 || npcObjectId != 0 || listId != 0 || itemObjectId <= 0 || count != 1
				: npcId <= 0 || npcObjectId <= 0 || (kind == OperationKind.BUY || kind == OperationKind.SELL ? listId <= 0 : listId != 0)
					|| (kind == OperationKind.BUY ? itemObjectId != 0 : itemObjectId <= 0)) { throw new IllegalArgumentException("Invalid maintenance native identity."); }
		}
		public OperationIntent intent(long budget) { return new OperationIntent(kind, npcId, npcObjectId, listId, itemId, itemObjectId, count, 0, "", budget); }
		private PhantomDomainRef source(int index) { return new PhantomDomainRef(SOURCE, index + ":" + kind + ':' + npcId + ':' + npcObjectId + ':' + listId + ':' + itemId + ':' + itemObjectId + ':' + count); }
		public boolean matches(PhantomCommerceReceipt.OperationRequest request)
		{
			return kind == request.kind() && npcId == request.npcTemplateId() && npcObjectId == request.npcObjectId() && listId == request.listId() && itemId == request.itemId() && itemObjectId == request.itemObjectId() && count == request.count();
		}
	}
	public record Spec(List<Operation> operations, int index, boolean failed, boolean buyChecked)
	{
		public Spec
		{
			operations = List.copyOf(operations);
			if (operations.isEmpty() || operations.size() > MAXIMUM_OPERATIONS || index < 0 || index > operations.size()) { throw new IllegalArgumentException("Maintenance operations/index exceed the existing bounded child contract."); }
		}
		public boolean returning() { return index == operations.size(); }
		public Operation current() { if (returning()) { throw new IllegalStateException("Maintenance is returning."); } return operations.get(index); }
	}

	public static boolean isMaintenance(PhantomGoal goal) { return goal != null && TYPE.equals(goal.goalType()); }
	public static Spec parse(PhantomGoal goal)
	{
		if (!isMaintenance(goal) || !PURPOSE.equals(goal.purposeKey()) || goal.target() == null || !"item".equals(goal.target().namespace()) || !PhantomJourneyLifetime.valid(goal) || !goal.constraints().keySet().containsAll(PhantomJourneyLifetime.KEYS)) { throw new IllegalArgumentException("Invalid maintenance goal/lifetime."); }
		final var keys = new HashSet<>(OWN_KEYS); keys.addAll(PhantomJourneyLifetime.KEYS);
		if (!goal.constraints().keySet().equals(keys)) { throw new IllegalArgumentException("Unknown maintenance constraints."); }
		final long failed = goal.constraints().get("maintenance.failed"), checked = goal.constraints().get("maintenance.buy_checked"), pressure = goal.constraints().get("maintenance.pressure");
		if ((failed != 0 && failed != 1) || (checked != 0 && checked != 1) || (pressure != 0 && pressure != 1)) { throw new IllegalArgumentException("Invalid maintenance flags."); }
		final var operations = new ArrayList<Operation>();
		for (var source : goal.validSources())
		{
			final var fields = source.key().split(":", -1);
			if (!SOURCE.equals(source.namespace()) || fields.length != 8 || Integer.parseInt(fields[0]) != operations.size()) { throw new IllegalArgumentException("Invalid maintenance source order/version."); }
			operations.add(new Operation(OperationKind.valueOf(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), Long.parseLong(fields[7])));
		}
		final var spec = new Spec(operations, Math.toIntExact(goal.constraints().get("source.index")), failed == 1, checked == 1);
		if (!goal.target().key().equals(Integer.toString(operations.getFirst().itemId()))) { throw new IllegalArgumentException("Maintenance first item changed."); }
		PhantomSupplyIntentStore.Binding.parse(goal);
		return spec;
	}

	public static PhantomGoal create(PhantomGoal root, PhantomSupplyIntentStore.Binding binding, List<Operation> operations, long revision, long budget, long deadline, long now, boolean pressure)
	{
		final var spec = new Spec(operations, 0, false, false);
		final var values = new LinkedHashMap<String, Long>(); values.put("source.index", 0L); values.put("maintenance.failed", 0L); values.put("maintenance.buy_checked", 0L);
		values.put("maintenance.pressure", pressure ? 1L : 0L);
		final var goal = new PhantomGoal(root.goalId(), TYPE, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("item", Integer.toString(spec.operations().getFirst().itemId())), operations.size(), 0, "inventory.native", sources(operations), root.selectedAnchor(), PURPOSE, 750, 0, budget, deadline, values, "maintenance.native.requested", revision, binding.encode());
		final var started = PhantomJourneyLifetime.start(goal, now); parse(started); return started;
	}

	public static PhantomGoal advance(PhantomGoal goal, Spec replacement, long budget, String reason)
	{
		parse(goal);
		final var values = new LinkedHashMap<>(goal.constraints()); values.put("source.index", (long) replacement.index()); values.put("maintenance.failed", replacement.failed() ? 1L : 0L); values.put("maintenance.buy_checked", replacement.buyChecked() ? 1L : 0L);
		final var changed = new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), replacement.operations().size(), replacement.index(), goal.acquisitionMethod(), sources(replacement.operations()), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), budget, goal.deadlineEpochMillis(), values, reason, Math.addExact(goal.revision(), 1), goal.payloadText());
		parse(changed); return changed;
	}

	/** Only an unprepared counter reference can change after a JVM restart; receipt identities are immutable. */
	public static PhantomGoal rebindCounter(PhantomGoal goal, int npcObjectId, PhantomCommerceReceipt receipt)
	{
		final var spec = parse(goal);
		if (goal.status() != PhantomGoalStatus.ACTIVE || spec.returning() || spec.current().kind() == OperationKind.CRYSTALLIZE || npcObjectId <= 0) { throw new IllegalArgumentException("Only an active unprepared native counter may be rebound."); }
		if (receipt != null && (!receipt.state().terminal() || receipt.state() == PhantomCommerceReceipt.State.INCONSISTENT || receipt.goalId() == goal.goalId() && receipt.goalRevision() == goal.revision())) { return goal; }
		final var old = spec.current();
		if (npcObjectId == old.npcObjectId()) { return goal; }
		final var operations = new ArrayList<>(spec.operations());
		operations.set(spec.index(), new Operation(old.kind(), old.npcId(), npcObjectId, old.listId(), old.itemId(), old.itemObjectId(), old.count()));
		return advance(goal, new Spec(operations, spec.index(), spec.failed(), spec.buyChecked()), goal.expenseBudget(), "maintenance.counter.rebound_after_restart");
	}
	private static List<PhantomDomainRef> sources(List<Operation> operations)
	{
		final var result = new ArrayList<PhantomDomainRef>(); for (int index = 0; index < operations.size(); index++) { result.add(operations.get(index).source(index)); } return List.copyOf(result);
	}
}
