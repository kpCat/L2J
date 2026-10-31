/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Ticket;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.item.instance.Item;

/** Bounded TEST-only observations of actual context tickets. It neither reserves work nor grants admission. */
public final class PhantomEarnedRecipient109Trace implements AutoCloseable
{
	private static final int LIMIT = 64, ROW_LIMIT = 8192;
	private final ArrayDeque<String> rows = new ArrayDeque<>();
	private final Field observerField, failureField;
	private final Field pickupField;
	private final BiConsumer<String, long[]> pickupObserver = this::pickup;
	private static volatile PhantomEarnedRecipient109Trace active;
	private final AtomicReference<Throwable> unsafe = new AtomicReference<>();
	private final Set<PlayerNativeWork.Owner> observedOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final ThreadLocal<ArrayDeque<PickupCheck>> pending = ThreadLocal.withInitial(ArrayDeque::new);
	private final Set<Integer> consumedSources = new HashSet<>();
	private final ArrayDeque<String> pickupRows = new ArrayDeque<>();
	private long inventoryProofs, measuredProofs;
	private volatile long measurement;
	private Map<Integer, PlayerNativeWork.Owner> measuredOwners = Map.of();
	private Map<Integer, Long> measuredEpochs = Map.of();
	private final BiConsumer<String, List<Ticket>> observer = this::sample;
	private final String previousFailure;
	private long dropped;
	private String observerFailure = "";
	private boolean closed;

	public PhantomEarnedRecipient109Trace() throws Exception
	{
		observerField = PlayerNativeWork.class.getDeclaredField("_participantObserver"); observerField.setAccessible(true);
		failureField = PlayerNativeWork.class.getDeclaredField("_participantObserverFailure"); failureField.setAccessible(true);
		pickupField = PlayerNativeEvidence.class.getDeclaredField("_pickupObserver"); pickupField.setAccessible(true);
		synchronized (PlayerNativeWork.class)
		{
			if (observerField.get(null) != null || pickupField.get(null) != null || active != null) { throw new IllegalStateException("TASK109_RECIPIENT_OBSERVER_ALREADY_OWNED"); }
			previousFailure = (String) failureField.get(null); failureField.set(null, ""); observerField.set(null, observer);
			pickupField.set(null, pickupObserver); active = this;
		}
	}

	private static final class PickupCheck
	{
		final int actor, source, item;
		final long maximum, measurement;
		final Item ground;
		final boolean spawned;
		final Map<Integer, Player> recipients = new LinkedHashMap<>();
		final Map<Integer, PlayerNativeWork.Owner> owners = new LinkedHashMap<>();
		final Map<Integer, Long> epochs = new LinkedHashMap<>(), before = new LinkedHashMap<>(), gains = new LinkedHashMap<>();
		long total;
		PickupCheck(long[] value, long measurement)
		{
			this.measurement = measurement;
			actor = (int) value[0]; source = (int) value[1]; item = (int) value[2]; maximum = value[3];
			final var object = World.getInstance().findObject(source); ground = object instanceof Item target ? target : null;
			spawned = ground != null && ground.isSpawned();
			for (Player player : PlayerNativeWork.inheritedPlayers())
			{
				recipients.put(player.getObjectId(), player); final var owner = player.getNativeWorkOwner(); owners.put(player.getObjectId(), owner);
				epochs.put(player.getObjectId(), owner.epoch()); before.put(player.getObjectId(), player.getInventory().getInventoryItemCount(item, -1));
			}
		}
	}

	/** Native callbacks report successful writers. Any genuine recipient/count violation fences this run. */
	private void pickup(String stage, long[] value)
	{
		try
		{
			if (stage.equals("START")) { final var proof = new PickupCheck(value, measurement); observedOwners.addAll(proof.owners.values()); pending.get().push(proof); return; }
			final var stack = pending.get(); final var proof = stack.peek();
			if (proof == null || proof.source != value[1]) { throw new IllegalStateException("TASK109_PICKUP_SOURCE_CONTEXT"); }
			if (stage.equals("GAIN"))
			{
				final int id = (int) value[4]; final var recipient = proof.recipients.get(id); final var owner = proof.owners.get(id);
				if (recipient == null || owner == null || recipient.getNativeWorkOwner() != owner || !owner.isCurrent() || owner.epoch() != proof.epochs.get(id)
					|| World.getInstance().getPlayer(id) != recipient || PlayerNativeWork.current(owner) == null || PlayerNativeWork.current(owner).semantics() != PlayerNativeWork.Semantics.EARNED)
				{ throw new IllegalStateException("TASK109_PICKUP_UNKNOWN_OR_STALE_RECIPIENT"); }
				proof.total = Math.addExact(proof.total, value[5]); requireGain(proof.maximum, proof.total);
				proof.gains.merge(id, value[5], Math::addExact); return;
			}
			if (!stage.equals("END")) { throw new IllegalStateException("TASK109_PICKUP_UNKNOWN_STAGE"); }
			stack.pop(); if (stack.isEmpty()) { pending.remove(); }
			final var after = new LinkedHashMap<Integer, Long>(); boolean exactInventory = true;
			for (var entry : proof.recipients.entrySet())
			{
				final long count = entry.getValue().getInventory().getInventoryItemCount(proof.item, -1); after.put(entry.getKey(), count);
				exactInventory &= count - proof.before.get(entry.getKey()) == proof.gains.getOrDefault(entry.getKey(), 0L);
			}
			if (!exactInventory) { throw new IllegalStateException("TASK109_PICKUP_INVENTORY_MISMATCH"); }
			if (proof.total > 0 && (proof.total != proof.maximum || proof.ground == null || proof.ground.isSpawned())) { throw new IllegalStateException("TASK109_PICKUP_SOURCE_NOT_CONSERVED"); }
			synchronized (this)
			{
				if (proof.total > 0 || value[7] == 1)
				{
					if (consumedSources.size() == 256 || !consumedSources.add(proof.source)) { throw new IllegalStateException("TASK109_PICKUP_DUPLICATE_OR_OVERFLOW"); }
				}
				final boolean pass = proof.total == proof.maximum && proof.spawned && exactInventory;
				if (pass) { recordInventoryProof(proof); }
				if (pickupRows.size() == 128) { pickupRows.removeFirst(); }
				pickupRows.add("PICKUP source=" + proof.source + " item=" + proof.item + " count=" + proof.maximum + " actor=" + proof.actor + " thread=" + Thread.currentThread().getName()
					+ " epochs=" + proof.epochs + " before=" + proof.before + " gains=" + proof.gains + " after=" + after + " sourceSpawnedBefore=" + proof.spawned
					+ " sourceSpawnedAfter=" + (proof.ground != null && proof.ground.isSpawned()) + " consumedNative=" + value[7] + " inventoryProof=" + pass);
			}
		}
		catch (Throwable failure)
		{
			unsafe.compareAndSet(null, failure);
		}
	}

	public static void requireGain(long maximum, long total) { if (maximum <= 0 || total <= 0 || total > maximum) { throw new IllegalStateException("TASK109_PICKUP_DUPLICATE_COUNT"); } }
	public static void assertSafe()
	{
		final var trace = active;
		if (trace != null && trace.unsafe.get() != null)
		{
			// Main acceptance boundary only: never acquire owner monitors inside an inventory callback.
			for (var owner : trace.observedOwners) { owner.recordFailure(trace.unsafe.get()); }
			throw new IllegalStateException("TASK109_PICKUP_UNSAFE", trace.unsafe.get());
		}
	}
	public static void beginInventoryWindow(List<Player> players)
	{
		final var trace = active;
		if (trace == null) { throw new IllegalStateException("TASK109_PICKUP_TRACE_ABSENT"); }
		final var owners = new LinkedHashMap<Integer, PlayerNativeWork.Owner>();
		final var epochs = new LinkedHashMap<Integer, Long>();
		for (Player player : players)
		{
			final var owner = player.getNativeWorkOwner();
			if (owner == null || owner.player() != player || !owner.isCurrent() || owners.put(player.getObjectId(), owner) != null)
			{ throw new IllegalStateException("TASK109_PICKUP_MEASUREMENT_OWNER"); }
			epochs.put(player.getObjectId(), owner.epoch());
		}
		if (owners.isEmpty()) { throw new IllegalStateException("TASK109_PICKUP_MEASUREMENT_EMPTY"); }
		synchronized (trace)
		{
			trace.measuredOwners = Map.copyOf(owners); trace.measuredEpochs = Map.copyOf(epochs);
			trace.measurement = Math.incrementExact(trace.measurement); trace.measuredProofs = 0;
		}
	}

	private synchronized void recordInventoryProof(PickupCheck proof)
	{
		inventoryProofs++;
		if (measurement != 0 && proof.measurement == measurement && measuredOwners.containsKey(proof.actor)
			&& measuredOwners.size() == proof.owners.size() && measuredEpochs.equals(proof.epochs)
			&& measuredOwners.entrySet().stream().allMatch(entry -> proof.owners.get(entry.getKey()) == entry.getValue()))
		{ measuredProofs++; }
	}

	public static long inventoryProofCount() { final var trace = active; if (trace == null) { return 0; } synchronized (trace) { return trace.measuredProofs; } }

	private synchronized void sample(String facts, List<Ticket> tickets)
	{
		if (closed) { return; }
		final var row = new StringBuilder(facts);
		for (Ticket ticket : tickets)
		{
			row.append(" frame=ticket@").append(System.identityHashCode(ticket)).append(":player=").append(ticket.owner().player().getObjectId())
				.append(":owner@").append(System.identityHashCode(ticket.owner())).append(":epoch=").append(ticket.owner().epoch())
				.append(":semantics=").append(ticket.semantics()).append(":running=").append(ticket.isRunning())
				.append(":id=").append(scalar(ticket, "_id")).append(":parentId=").append(scalar(ticket, "_parentId")).append(":kind=").append(scalar(ticket, "_kind"));
		}
		if (rows.size() == LIMIT) { rows.removeFirst(); dropped++; }
		rows.addLast(row.substring(0, Math.min(row.length(), ROW_LIMIT)).replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
	}

	private static Object scalar(Ticket ticket, String name)
	{
		try { final var field = ticket.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(ticket); }
		catch (ReflectiveOperationException | RuntimeException unavailable) { return "UNKNOWN"; }
	}

	public synchronized String snapshot()
	{
		return "boundedTestOnly=true admissionChanged=false maxRows=" + LIMIT + " dropped=" + dropped + " observerFailure=" + observerFailure + " inventoryProofs=" + inventoryProofs + " unsafe=" + (unsafe.get() == null ? "NONE" : unsafe.get().getClass().getName()) + "\n" + String.join("\n", rows) + "\n" + String.join("\n", pickupRows) + "\n";
	}

	@Override public void close() throws Exception
	{
		synchronized (PlayerNativeWork.class)
		{
			if (observerField.get(null) == observer) { observerField.set(null, null); }
			if (pickupField.get(null) == pickupObserver) { pickupField.set(null, null); }
			if (active == this) { active = null; }
			observerFailure = (String) failureField.get(null); failureField.set(null, previousFailure);
			synchronized (this) { closed = true; }
		}
	}
}
