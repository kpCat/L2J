/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 */
package org.l2jmobius.gameserver.phantoms;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.CurrentStatus;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.EconomyReader;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.ReceiptReader;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.EconomicAuditCode;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationRequest;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore.VersionedReceipt;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.AuditRecord;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.StoredOperation;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomEconomicAuditGoal028Checkpoint4Suite implements PhantomTestSuite
{
	private static final String OPERATION_A = "a".repeat(64);
	private static final String OPERATION_B = "b".repeat(64);

	private final Mode _mode;

	public PhantomEconomicAuditGoal028Checkpoint4Suite()
	{
		this(Mode.FULL);
	}

	public PhantomEconomicAuditGoal028Checkpoint4Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "economic-audit-goal028-checkpoint4";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-select-query-limit-newest-first-and-payload-omission", this::testSelectContract);
		registry.add("02-current-race-and-saturating-retained-summary", _ -> testCurrentRaceAndSummary());
		if (_mode == Mode.GOAL022)
		{
			return;
		}
		registry.add("03-latest-receipt-safe-deltas-and-privacy", _ -> testReceiptView());
		registry.add("04-typed-facade-and-no-auto-enable", _ -> testFacadeStatuses());
		registry.add("05-same-store-owner-and-read-only-contract", this::testStaticOwnershipAndReadOnly);
		registry.add("06-admin-bound-rendering-and-existing-surfaces", this::testAdminContract);
	}
	private void testSelectContract(PhantomTestContext context) throws Exception
	{
		final AtomicReference<String> sql = new AtomicReference<>();
		final List<Object> bindings = new ArrayList<>();
		final List<Object[]> rows = List.of(
			new Object[] { 9L, OPERATION_A, "SELF_CRAFT", "COMMITTED", "SUCCESS", "craft.success", 2L, 3L, 4L, 5L, 6L, 7L },
			new Object[] { 8L, OPERATION_B, "ITEM_ENCHANT", "ABORTED", "ERROR", "enchant.abort", 1L, 0L, 0L, 2L, 0L, 0L });
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(() -> connection(sql, bindings, rows), PhantomEconomyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/economy/high-five-economy-v1.xml")));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> service.findAudit(0, 1), "Nonpositive profile ID was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> service.findAudit(1, 0), "Zero audit limit was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> service.findAudit(1, 257), "Audit limit above retained bound was accepted.");
		final List<AuditRecord> result = service.findAudit(77, 2);
		PhantomAssertions.assertEquals("SELECT audit_id,operation_id,operation_kind,terminal_state,result_code,reason_key,items_consumed,items_produced,adena_source,adena_sink,crystals_produced,target_items_destroyed FROM phantom_economy_audit WHERE profile_id=? ORDER BY audit_id DESC LIMIT ?", sql.get(), "Audit SELECT shape drifted.");
		PhantomAssertions.assertEquals(List.of(77L, 2), bindings, "Audit SELECT bindings drifted.");
		PhantomAssertions.assertEquals(List.of(9L, 8L), result.stream().map(AuditRecord::auditId).toList(), "Audit rows were not preserved newest-first.");
		PhantomAssertions.assertFalse(sql.get().contains("consequence_payload"), "Audit SELECT exposed consequence payload.");
	}

	private static void testCurrentRaceAndSummary()
	{
		final StoredOperation active = stored(OPERATION_A);
		final AtomicInteger reads = new AtomicInteger();
		final EconomyReader economy = new EconomyReader()
		{
			@Override
			public Optional<StoredOperation> findActive(long profileId)
			{
				return reads.getAndIncrement() == 0 ? Optional.of(active) : Optional.empty();
			}

			@Override
			public List<Reservation> findReservations(String operationId)
			{
				return List.of();
			}

			@Override
			public List<AuditRecord> findAudit(long profileId, int limit)
			{
				return List.of(
					audit(2, OPERATION_A, State.COMMITTED, Long.MAX_VALUE, 4, 5, 6, 7, 8),
					audit(1, OPERATION_B, State.ABORTED, 1, 10, 20, 30, 40, 50));
			}
		};
		final var snapshot = new PhantomEconomicAuditView(economy, _ -> Optional.empty()).read(77);
		PhantomAssertions.assertEquals(CurrentStatus.CHANGED, snapshot.current().status(), "Disappearing current operation leaked stale details.");
		PhantomAssertions.assertEquals(2, reads.get(), "Current operation race was not bounded to confirmation reads.");
		PhantomAssertions.assertEquals(2, snapshot.retainedSummary().retainedRows(), "Retained row count drifted.");
		PhantomAssertions.assertEquals(1, snapshot.retainedSummary().stateCounts().get(State.COMMITTED), "Committed retained state count drifted.");
		PhantomAssertions.assertEquals(1, snapshot.retainedSummary().stateCounts().get(State.ABORTED), "Aborted retained state count drifted.");
		PhantomAssertions.assertEquals(Long.MAX_VALUE, snapshot.retainedSummary().itemsConsumed(), "Overflow did not saturate.");
		PhantomAssertions.assertEquals(14L, snapshot.retainedSummary().itemsProduced(), "Nonoverflow total drifted.");
		PhantomAssertions.assertTrue(snapshot.retainedSummary().totalsSaturated(), "Saturation flag was not raised.");
	}
	private static void testReceiptView()
	{
		final OperationRequest request = new OperationRequest(OperationKind.BUY, 100, 200, 300, 400, 0, 2, 20, 0, 0, 0, "", 700, 800, 900);
		PhantomCommerceReceipt receipt = PhantomCommerceReceipt.prepared(77, 88, 3, request, new ConservationFacts(100, 5, 2, 1, 10, 20, 30), new ConservationFacts(80, 7, 2, 1, 11, 20, 30));
		receipt = receipt.withState(PhantomCommerceReceipt.State.COMMITTING).resumed();
		final PhantomCommerceReceipt durable = receipt;
		final var snapshot = new PhantomEconomicAuditView(emptyEconomy(), _ -> Optional.of(new VersionedReceipt(4, durable))).read(77);
		final var view = snapshot.latestReceipt();
		PhantomAssertions.assertEquals(OperationKind.BUY, view.kind(), "Receipt kind drifted.");
		PhantomAssertions.assertEquals(-20L, view.primary().delta(), "Signed primary delta drifted.");
		PhantomAssertions.assertEquals(2L, view.secondary().delta(), "Signed secondary delta drifted.");
		PhantomAssertions.assertEquals(0L, view.object().delta(), "Signed object delta drifted.");
		PhantomAssertions.assertTrue(view.positionChanged(), "Position change was not reduced to a boolean.");
		final List<String> fields = List.of(view.getClass().getRecordComponents()).stream().map(component -> component.getName()).toList();
		for (String forbidden : List.of("profileId", "request", "before", "expectedAfter", "inventory", "x", "y", "z", "instanceId"))
		{
			PhantomAssertions.assertFalse(fields.contains(forbidden), "Receipt view exposed forbidden field " + forbidden);
		}
	}

	private static void testFacadeStatuses()
	{
		PhantomSystem.resetEconomicAuditForTesting();
		try
		{
			PhantomAssertions.assertEquals(EconomicAuditCode.INVALID, PhantomSystem.operatorEconomicAudit(0).code(), "Invalid profile status drifted.");
			PhantomAssertions.assertEquals(EconomicAuditCode.RUNTIME_NOT_CONFIGURED, PhantomSystem.operatorEconomicAudit(77).code(), "Unconfigured runtime status drifted.");
			PhantomAssertions.assertEquals(PhantomSystem.OperatorMode.DRAINED, PhantomSystem.operatorDrain().desiredMode(), "Drain fixture did not hold desired mode.");
			PhantomAssertions.assertEquals(EconomicAuditCode.RUNTIME_NOT_CONFIGURED, PhantomSystem.operatorEconomicAudit(77).code(), "Drained runtime auto-enabled through audit.");
			PhantomSystem.resetEconomicAuditForTesting();

			PhantomSystem.configureEconomicAuditForTesting(new PhantomEconomicAuditView(emptyEconomy(), _ -> Optional.empty()), PhantomSystem.State.RUNNING);
			PhantomAssertions.assertEquals(EconomicAuditCode.EMPTY, PhantomSystem.operatorEconomicAudit(77).code(), "Empty audit status drifted.");
			PhantomSystem.resetEconomicAuditForTesting();

			final EconomyReader available = fixedEconomy(List.of(audit(1, OPERATION_A, State.COMMITTED, 1, 2, 3, 4, 5, 6)));
			PhantomSystem.configureEconomicAuditForTesting(new PhantomEconomicAuditView(available, _ -> Optional.empty()), PhantomSystem.State.RUNNING);
			PhantomAssertions.assertEquals(EconomicAuditCode.AVAILABLE, PhantomSystem.operatorEconomicAudit(77).code(), "Available audit status drifted.");
			PhantomSystem.resetEconomicAuditForTesting();

			PhantomSystem.configureEconomicAuditForTesting(new PhantomEconomicAuditView(failingEconomy(), _ -> Optional.empty()), PhantomSystem.State.RUNNING);
			PhantomAssertions.assertEquals(EconomicAuditCode.READ_FAILED, PhantomSystem.operatorEconomicAudit(77).code(), "Read failure status drifted.");
			PhantomSystem.resetEconomicAuditForTesting();

			PhantomSystem.configureEconomicAuditForTesting(null, PhantomSystem.State.STOPPED);
			PhantomAssertions.assertEquals(EconomicAuditCode.ECONOMY_UNAVAILABLE, PhantomSystem.operatorEconomicAudit(77).code(), "Stopped economy status drifted.");
		}
		finally
		{
			PhantomSystem.resetEconomicAuditForTesting();
		}
	}
	private void testStaticOwnershipAndReadOnly(PhantomTestContext context) throws Exception
	{
		final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
		final String view = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomEconomicAuditView.java"));
		final String reservations = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java"));
		PhantomAssertions.assertEquals(1, occurrences(system, "new PhantomCommerceReceiptStore(productionProfiles)"), "PhantomSystem owns duplicate production receipt stores.");
		PhantomAssertions.assertTrue(system.contains("new PhantomCommerceService(commerceCatalog, _commerceReceiptStore") && system.contains("new PhantomEconomicAuditView(_economyReservations, _commerceReceiptStore)"), "Commerce and audit do not share the same receipt store instance.");
		for (String forbidden : List.of(".save(", ".reserve(", ".transition(", ".reconcile(", ".renew(", ".setGoal(", ".clearGoal(", ".operatorEnable(", ".operatorDrain(", ".operatorDisable(", "new Thread", "Timer", "Scheduled", "poll(", "consequencePayload"))
		{
			PhantomAssertions.assertFalse(view.contains(forbidden), "Audit view contains forbidden mutating/active/raw call " + forbidden);
		}
		final int methodStart = reservations.indexOf("public List<AuditRecord> findAudit");
		final int methodEnd = reservations.indexOf("public int nextAttempt", methodStart);
		final String findAudit = reservations.substring(methodStart, methodEnd);
		PhantomAssertions.assertTrue(findAudit.contains("WHERE profile_id=? ORDER BY audit_id DESC LIMIT ?"), "Goal022 audit query lost exact profile/order/limit shape.");
		PhantomAssertions.assertFalse(findAudit.contains("INSERT ") || findAudit.contains("UPDATE ") || findAudit.contains("DELETE ") || findAudit.contains("consequence_payload"), "Goal022 audit read contains mutation or raw payload.");
	}

	private void testAdminContract(PhantomTestContext context) throws Exception
	{
		final String admin = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java"));
		PhantomAssertions.assertTrue(admin.contains("arguments.startsWith(\"economy \")") && admin.contains("PhantomSystem.operatorEconomicAudit(profileId)"), "Admin economy syntax does not delegate to the facade.");
		PhantomAssertions.assertTrue(admin.contains("Math.min(PhantomEconomicAuditView.RENDER_LIMIT") && admin.contains("RENDER_LIMIT = 8") == false, "Admin did not use the bounded eight-row view constant.");
		PhantomAssertions.assertTrue(admin.contains("retained-window (max 256, not lifetime)") && admin.contains("totalsSaturated="), "Admin summary is not explicitly retained-window/saturation-aware.");
		PhantomAssertions.assertTrue(admin.contains("positionChanged=") && admin.contains("primary=") && admin.contains("secondary=") && admin.contains("object="), "Safe receipt delta section is incomplete.");
		final int renderStart = admin.indexOf("private static void sendEconomicAudit");
		final int renderEnd = admin.indexOf("private static void sendTrace", renderStart);
		final String render = admin.substring(renderStart, renderEnd);
		for (String forbidden : List.of("destinationX", "destinationY", "destinationZ", "instanceId", "npcObjectId", "itemObjectId", "inventory", "consequence"))
		{
			PhantomAssertions.assertFalse(render.contains(forbidden), "Admin economic rendering exposed forbidden detail " + forbidden);
		}
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"enable\")") && admin.contains("arguments.equals(\"drain\")") && admin.contains("arguments.equals(\"disable\")") && admin.contains("arguments.equals(\"status\")") && admin.contains("arguments.startsWith(\"trace \")"), "Existing Goal028 operator surfaces drifted.");
	}
	private static EconomyReader emptyEconomy()
	{
		return fixedEconomy(List.of());
	}

	private static EconomyReader fixedEconomy(List<AuditRecord> audit)
	{
		return new EconomyReader()
		{
			@Override
			public Optional<StoredOperation> findActive(long profileId)
			{
				return Optional.empty();
			}

			@Override
			public List<Reservation> findReservations(String operationId)
			{
				return List.of();
			}

			@Override
			public List<AuditRecord> findAudit(long profileId, int limit)
			{
				return audit;
			}
		};
	}

	private static EconomyReader failingEconomy()
	{
		return new EconomyReader()
		{
			@Override
			public Optional<StoredOperation> findActive(long profileId)
			{
				throw new IllegalStateException("expected read failure");
			}

			@Override
			public List<Reservation> findReservations(String operationId)
			{
				throw new AssertionError("unreachable");
			}

			@Override
			public List<AuditRecord> findAudit(long profileId, int limit)
			{
				throw new AssertionError("unreachable");
			}
		};
	}

	private static StoredOperation stored(String operationId)
	{
		return new StoredOperation(operationId, 77, 101, 88, 3, PhantomEconomyOperation.Kind.SELF_CRAFT, State.RESERVED, 1, "intent", "c".repeat(64), "d".repeat(64), 4, 5, 6);
	}

	private static AuditRecord audit(long auditId, String operationId, State state, long itemsConsumed, long itemsProduced, long adenaSource, long adenaSink, long crystalsProduced, long targetItemsDestroyed)
	{
		return new AuditRecord(auditId, operationId, PhantomEconomyOperation.Kind.SELF_CRAFT, state, state == State.COMMITTED ? Result.SUCCESS : Result.ERROR, "audit.test", itemsConsumed, itemsProduced, adenaSource, adenaSink, crystalsProduced, targetItemsDestroyed);
	}

	private static int occurrences(String source, String needle)
	{
		return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
	}
	public enum Mode
	{
		FULL,
		GOAL022
	}

	private static Connection connection(AtomicReference<String> sql, List<Object> bindings, List<Object[]> rows)
	{
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (proxy, method, arguments) ->
		{
			if (method.getName().equals("prepareStatement"))
			{
				sql.set((String) arguments[0]);
				return preparedStatement(bindings, rows);
			}
			if (method.getName().equals("close"))
			{
				return null;
			}
			if (method.getName().equals("isClosed"))
			{
				return false;
			}
			throw new UnsupportedOperationException("Unexpected Connection call " + method.getName());
		});
	}

	private static PreparedStatement preparedStatement(List<Object> bindings, List<Object[]> rows)
	{
		return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class }, (proxy, method, arguments) ->
		{
			if (method.getName().equals("setLong") || method.getName().equals("setInt"))
			{
				final int index = (Integer) arguments[0];
				while (bindings.size() < index)
				{
					bindings.add(null);
				}
				bindings.set(index - 1, arguments[1]);
				return null;
			}
			if (method.getName().equals("executeQuery"))
			{
				return resultSet(rows);
			}
			if (method.getName().equals("close"))
			{
				return null;
			}
			throw new UnsupportedOperationException("Unexpected PreparedStatement call " + method.getName());
		});
	}

	private static ResultSet resultSet(List<Object[]> rows)
	{
		final AtomicInteger index = new AtomicInteger(-1);
		return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class }, (proxy, method, arguments) ->
		{
			if (method.getName().equals("next"))
			{
				return index.incrementAndGet() < rows.size();
			}
			if (method.getName().equals("getLong"))
			{
				return ((Number) rows.get(index.get())[(Integer) arguments[0] - 1]).longValue();
			}
			if (method.getName().equals("getString"))
			{
				return (String) rows.get(index.get())[(Integer) arguments[0] - 1];
			}
			if (method.getName().equals("close"))
			{
				return null;
			}
			throw new UnsupportedOperationException("Unexpected ResultSet call " + method.getName());
		});
	}
}