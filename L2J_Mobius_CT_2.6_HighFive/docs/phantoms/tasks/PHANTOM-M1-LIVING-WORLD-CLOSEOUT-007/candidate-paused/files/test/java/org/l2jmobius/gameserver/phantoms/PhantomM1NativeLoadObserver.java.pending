/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Passive TEST load witnesses; positive evidence comes only from the native lifecycle hook. */
public final class PhantomM1NativeLoadObserver implements PhantomMaterializationLifecyclePort
{
	private final Map<Long, Integer> _readyIdentities;
	private final Map<Long, Witness> _witnesses = new ConcurrentHashMap<>();
	private final AtomicReference<String> _incomplete = new AtomicReference<>();

	public PhantomM1NativeLoadObserver(Map<Long, Integer> readyIdentities)
	{
		_readyIdentities = Map.copyOf(readyIdentities);
		if (_readyIdentities.isEmpty() || _readyIdentities.size() > 1280 || new HashSet<>(_readyIdentities.values()).size() != _readyIdentities.size())
		{
			throw new IllegalArgumentException("Native-load observer requires distinct bounded frozen READY identities.");
		}
	}

	@Override public void beforeMaterialize(long profileId, int characterObjectId) { }
	@Override public void materializeSucceeded(long profileId, int characterObjectId) { }
	@Override public void materializeAborted(long profileId, int characterObjectId) { }
	@Override public void beforeStore(long profileId, Player player) { }
	@Override public void afterStore(long profileId, Player player) { }

	@Override public void afterPlayerLoad(long profileId, Player player)
	{
		final Integer expected = _readyIdentities.get(profileId);
		if (expected == null || expected != player.getObjectId())
		{
			_incomplete.compareAndSet(null, "Unexpected native-load identity: " + profileId + "/" + player.getObjectId());
			return;
		}
		final var owner = player.getNativeWorkOwner();
		Witness observed;
		try
		{
			// Do not hold Player/status/collector/System monitors or an ActionLease during this drain.
			observed = PlayerNativeWork.checkpoint(player, () ->
			{
				try { return capture(profileId, player, owner); }
				catch (SQLException | RuntimeException | Error failure) { return failed(profileId, player, owner, failure); }
			});
		}
		catch (RuntimeException | Error failure) { observed = failed(profileId, player, owner, failure); }
		// One latest immutable witness per approved profile; no append-only actor/epoch retention.
		_witnesses.put(profileId, observed);
	}

	public Progress assertRestored(PhantomTestContext context, String name, long profileId, Player player, PlayerNativeEvidence.Snapshot evidence)
	{
		final Witness captured = _witnesses.get(profileId);
		context.record("w." + name + ".nativeLoadFacts", captured == null ? "ABSENT profile=" + profileId : facts(captured));
		final Witness observed = requireCurrent(profileId, player, evidence);
		context.record("w." + name + ".nativeLoad", "profile=" + profileId + " object=" + player.getObjectId() + " epoch=" + observed.epoch() + " observedNanos=" + observed.observedNanos() + " progress=" + observed.loadedProgress() + " pose=" + observed.loadedPosition() + " fullHash=" + hash(observed.loadedRows()) + " rows=" + observed.loadedRows().size() + " exactWeakPlayerOwner=true slotAndEnchantParity=true beforeQuestWorldActiveAutoPlay=true; currentDirtyInventoryIsNotLoadParity");
		return observed.loadedProgress();
	}

	void clearAfterStopped(PhantomMaterializationService service)
	{
		final var stopped = service.snapshot();
		if (stopped.state() != PhantomMaterializationService.ServiceState.STOPPED || stopped.retainedEntries() != 0 || stopped.cleanupEvidenceIncomplete())
		{
			throw new IllegalStateException("Native-load witnesses retained until successful exact service drain.");
		}
		_witnesses.clear(); _incomplete.set(null);
	}

	private Witness requireCurrent(long profileId, Player player, PlayerNativeEvidence.Snapshot evidence)
	{
		if (_incomplete.get() != null) { throw new IllegalStateException(_incomplete.get()); }
		final Integer expected = _readyIdentities.get(profileId);
		if (expected == null || expected != player.getObjectId()) { throw new IllegalStateException("Frozen native-load identity is missing/wrong."); }
		return requireWitness(_witnesses.get(profileId), profileId, player, evidence);
	}

	private static Witness requireWitness(Witness witness, long profileId, Player player, PlayerNativeEvidence.Snapshot evidence)
	{
		final var owner = player.getNativeWorkOwner();
		if (witness == null || witness.error() != null || witness.profileId() != profileId || witness.player().get() != player || witness.owner().get() != owner || owner == null || owner.player() != player || !owner.isCurrent() || owner.epoch() != witness.epoch() || evidence.epoch() != witness.epoch() || evidence.objectId() != player.getObjectId() || World.getInstance().getPlayer(player.getObjectId()) != player)
		{
			throw new IllegalStateException("Missing/incomplete/stale exact native-load witness: " + profileId + "; observation=" + (witness == null ? "ABSENT" : witness.error()));
		}
		if (!witness.beforePlay() || !witness.loadedIdentity().equals(witness.savedIdentity()) || !witness.loadedProgress().equals(witness.savedProgress()) || !witness.loadedPosition().equals(witness.savedPosition()) || !witness.loadedRows().equals(witness.nativeRows()) || !hash(witness.loadedRows()).equals(witness.savedInventoryHash()))
		{
			throw new IllegalStateException("Native load differs from canonical full items/slots/enchant/identity/progress/pose: " + profileId);
		}
		return witness;
	}

	private static Witness capture(long profileId, Player player, PlayerNativeWork.Owner owner) throws SQLException
	{
		final var saved = new PhantomBackgroundTransaction().load(profileId);
		if (!saved.successful() || saved.state() == null) { throw new IllegalStateException("Native-load canonical projection is unavailable."); }
		final var state = saved.state();
		final var identity = new Identity(profileId, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
		final var progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
		final var position = new Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), state.position().committedAnchorId());
		final List<NativeInventoryRow> nativeRows = nativeRows(player.getObjectId());
		final var loadedRows = player.getInventory().getItems().stream().filter(item -> item.getCount() > 0 && Set.of("INVENTORY", "PAPERDOLL").contains(item.getItemLocation().name()))
			.map(item -> new NativeInventoryRow(new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name())), item.getLocationSlot(), item.getEnchantLevel()))
			.sorted(Comparator.comparingInt(row -> row.item().objectId())).toList();
		final boolean beforePlay = owner != null && owner.player() == player && owner.isCurrent() && player.getNativeWorkOwner() == owner && !player.isAutoPlaying() && World.getInstance().getPlayer(player.getObjectId()) == null && World.getInstance().findObject(player.getObjectId()) == null;
		return new Witness(profileId, new WeakReference<>(player), new WeakReference<>(owner), owner == null ? -1 : owner.epoch(), System.nanoTime(), identity, state.identity(), progress, state.progress(), position, state.position(), loadedRows, nativeRows, state.inventory().canonicalHash(), beforePlay, null);
	}

	private static List<NativeInventoryRow> nativeRows(int objectId) throws SQLException
	{
		final List<NativeInventoryRow> items = new ArrayList<>();
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT object_id, item_id, count, loc, loc_data, enchant_level FROM items WHERE owner_id=? AND count>0 AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
		{
			query.setInt(1, objectId); query.setQueryTimeout(30);
			try (var rows = query.executeQuery())
			{
				while (rows.next()) { items.add(new NativeInventoryRow(new CanonicalItem(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("count"), ItemLocation.valueOf(rows.getString("loc"))), rows.getInt("loc_data"), rows.getInt("enchant_level"))); }
			}
		}
		return List.copyOf(items);
	}

	private static String hash(List<NativeInventoryRow> rows) { return PhantomBackgroundInventoryHash.compute(rows.stream().map(NativeInventoryRow::item).toList()); }
	private static String facts(Witness witness)
	{
		return "profile=" + witness.profileId() + " epoch=" + witness.epoch() + " observedNanos=" + witness.observedNanos() + " error=" + witness.error() + " beforePlay=" + witness.beforePlay()
			+ " savedIdentity=" + witness.savedIdentity() + " loadedIdentity=" + witness.loadedIdentity() + " savedProgress=" + witness.savedProgress() + " loadedProgress=" + witness.loadedProgress()
			+ " savedPose=" + witness.savedPosition() + " loadedPose=" + witness.loadedPosition() + " savedFullHash=" + witness.savedInventoryHash() + " nativeFullHash=" + hash(witness.nativeRows()) + " loadedFullHash=" + hash(witness.loadedRows())
			+ " nativeRows=" + witness.nativeRows() + " loadedRows=" + witness.loadedRows();
	}
	private static Witness failed(long profileId, Player player, PlayerNativeWork.Owner owner, Throwable failure)
	{
		final String detail = failure.getClass().getName() + ": " + failure.getMessage();
		return new Witness(profileId, new WeakReference<>(player), new WeakReference<>(owner), owner == null ? -1 : owner.epoch(), System.nanoTime(), null, null, null, null, null, null, List.of(), List.of(), "", false, detail.substring(0, Math.min(512, detail.length())));
	}

	private record NativeInventoryRow(CanonicalItem item, int locationSlot, int enchantLevel) { }
	private record Witness(long profileId, WeakReference<Player> player, WeakReference<PlayerNativeWork.Owner> owner, long epoch, long observedNanos,
		Identity loadedIdentity, Identity savedIdentity, Progress loadedProgress, Progress savedProgress, Position loadedPosition, Position savedPosition,
		List<NativeInventoryRow> loadedRows, List<NativeInventoryRow> nativeRows, String savedInventoryHash, boolean beforePlay, String error)
	{
		private Witness withEpoch(long changed) { return new Witness(profileId, player, owner, changed, observedNanos, loadedIdentity, savedIdentity, loadedProgress, savedProgress, loadedPosition, savedPosition, loadedRows, nativeRows, savedInventoryHash, beforePlay, error); }
		private Witness withLoadedRows(List<NativeInventoryRow> changed) { return new Witness(profileId, player, owner, epoch, observedNanos, loadedIdentity, savedIdentity, loadedProgress, savedProgress, loadedPosition, savedPosition, changed, nativeRows, savedInventoryHash, beforePlay, error); }
	}

	/** These contract controls reject absent/stale evidence and slot corruption; they are not native RED evidence. */
	public void assertContractControls(PhantomTestContext context, long profileId, Player player, PlayerNativeEvidence.Snapshot evidence)
	{
		final Witness observed = requireCurrent(profileId, player, evidence);
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> requireWitness(null, profileId, player, evidence), "Missing native-load witness was accepted.");
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> requireWitness(observed.withEpoch(observed.epoch() - 1), profileId, player, evidence), "Stale native-load epoch was accepted.");
		PhantomAssertions.assertFalse(observed.loadedRows().isEmpty(), "Native slot mismatch control requires a genuine loaded item row.");
		final var changed = new java.util.ArrayList<>(observed.loadedRows());
		final var first = changed.getFirst();
		changed.set(0, new NativeInventoryRow(first.item(), first.locationSlot() + 1, first.enchantLevel()));
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> requireWitness(observed.withLoadedRows(List.copyOf(changed)), profileId, player, evidence), "Native-load equipment slot mismatch was accepted.");
		context.record("w.nativeLoad.contractControls." + profileId, "missing/stale/slotMismatch rejected using actual native-load witness; contract only, not native RED");
	}
}
