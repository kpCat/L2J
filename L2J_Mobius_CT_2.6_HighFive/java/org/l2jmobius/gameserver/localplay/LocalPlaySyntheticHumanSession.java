package org.l2jmobius.gameserver.localplay;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** Native lifecycle shared by the private LocalPlay service and guarded TEST. No client/sink. */
public final class LocalPlaySyntheticHumanSession implements AutoCloseable
{
	private final int _objectId;
	private final String _name;
	private Lease _lease;
	private Player _actor;
	private LocalPlayPilotActions _actions;
	private Location _origin;
	private double _hp;
	private double _mp;
	private double _cp;
	private boolean _dead;
	private boolean _stored;

	public LocalPlaySyntheticHumanSession(int objectId, String name)
	{
		if ((objectId <= 0) || (name == null) || name.isBlank()) { throw new IllegalArgumentException("SYNTHETIC_CONFIG_INVALID"); }
		_objectId = objectId;
		_name = name;
	}

	private void requireFree()
	{
		if ((World.getInstance().getPlayer(_objectId) != null) || (World.getInstance().findObject(_objectId) != null)) { throw new IllegalStateException("SYNTHETIC_WORLD_IDENTITY_BUSY"); }
		if (PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId)) { throw new IllegalStateException("SYNTHETIC_AUTOSAVE_IDENTITY_BUSY"); }
		// A configured ordinary character cannot be a Phantom, even if it is currently stored.
		if (PhantomProfileRepository.open().findByCharacterObjectId(_objectId).isPresent()) { throw new IllegalStateException("SYNTHETIC_PHANTOM_IDENTITY_REJECTED"); }
	}

	public synchronized Player start() { return start(null); }

	/** LocalPlay setup precedes first World publication; canonical origin remains the cleanup boundary. */
	public synchronized Player start(Location initialPosition)
	{
		final Location setup = initialPosition == null ? null : initialPosition.clone();
		if (setup != null)
		{
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			if ((setup.getInstanceId() != 0) || !geo.hasGeo(setup.getX(), setup.getY())
				|| (Math.abs((long) geo.getHeight(setup.getX(), setup.getY(), setup.getZ()) - setup.getZ()) > 100)
				|| (org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(setup.getX(), setup.getY(), setup.getZ(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null))
			{ throw new IllegalArgumentException("SYNTHETIC_INITIAL_POSITION_INVALID"); }
		}
		if ((_lease != null) || (_actor != null)) { throw new IllegalStateException("SYNTHETIC_ALREADY_ACTIVE"); }
		requireFree();
		_lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(_objectId, OwnerKind.LOCALPLAY_TEST_HUMAN);
		if (_lease == null) { throw new IllegalStateException("SYNTHETIC_IDENTITY_BUSY"); }
		_stored = false;
		try
		{
			requireFree();
			try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(_objectId)) { _actor = Player.load(_objectId); }
			if (_actor == null) { throw new IllegalStateException("SYNTHETIC_LOAD_FAILED"); }
			_origin = _actor.getLocation().clone();
			_hp = _actor.getCurrentHp(); _mp = _actor.getCurrentMp(); _cp = _actor.getCurrentCp(); _dead = _actor.isDead();
			if ((_actor.getObjectId() != _objectId) || !_name.equals(_actor.getName()) || (_actor.getClient() != null) || _actor.hasHeadlessOutboundSession()) { throw new IllegalStateException("SYNTHETIC_LOADED_IDENTITY_INVALID"); }
			if (_dead || _actor.isInStoreMode() || _actor.isInParty() || (_actor.getInstanceId() != 0)) { throw new IllegalStateException("SYNTHETIC_ACTOR_UNSAFE"); }
			if (PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId)) { throw new IllegalStateException("SYNTHETIC_AUTOSAVE_SUPPRESSION_FAILED"); }
			_actor.setRunning(); _actor.standUp(); _actor.refreshOverloaded(); _actor.refreshExpertisePenalty();
			if (setup != null) { _actor.setXYZInvisible(setup.getX(), setup.getY(), setup.getZ()); _actor.setHeading(setup.getHeading()); }
			_actor.setOnlineStatus(true, false);
			_actor.spawnMe();
			if (!valid()) { throw new IllegalStateException("SYNTHETIC_SPAWN_IDENTITY_INVALID"); }
			_actions = new LocalPlayPilotActions(_actor);
			return _actor;
		}
		catch (RuntimeException | Error failure)
		{
			try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
	}

	public synchronized boolean valid()
	{
		final var owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(_objectId);
		return (_actor != null) && (_lease != null) && !_lease.isClosed() && _actor.isOnline() && (_actor.getClient() == null) && !_actor.hasHeadlessOutboundSession() && (World.getInstance().getPlayer(_objectId) == _actor) && (World.getInstance().findObject(_objectId) == _actor) && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId) && (owner != null) && (owner.token() == _lease.token());
	}

	public synchronized LocalPlayPilotActions.Outcome execute(LocalPlayPilotProtocol.Request request)
	{
		if (!valid()) { throw new IllegalStateException("SYNTHETIC_SESSION_INVALID"); }
		final var outcome = _actions.execute(_actor, request);
		// Ordinary Player teleport waits for a client acknowledgement. This owned local actor
		// has no client; complete the native server lifecycle explicitly, without a packet/sink.
		if (_actor.isTeleporting()) { _actor.onTeleported(); }
		return outcome;
	}

	public synchronized Player actor() { return _actor; }
	public synchronized LocalPlayPilotActions actions() { return _actions; }

	@Override
	public synchronized void close()
	{
		if (_actor != null)
		{
			final Player actor = _actor;
			if (((World.getInstance().getPlayer(_objectId) != null) && (World.getInstance().getPlayer(_objectId) != actor)) || ((World.getInstance().findObject(_objectId) != null) && (World.getInstance().findObject(_objectId) != actor))) { throw new IllegalStateException("SYNTHETIC_CLEANUP_FOREIGN_IDENTITY"); }
			if (_actions != null) { _actions.cancelPendingInvitation(); }
			actor.abortAttack(); actor.abortCast(); actor.stopMove(null); actor.setTarget(null);
			actor.getAI().setIntention(Intention.IDLE); actor.stopAllTasks(); actor.stopAllTimers();
			actor.decayMe();
			actor.setInstanceId(_origin.getInstanceId());
			actor.getLocation().setLocation(_origin);
			// Native status setters and regeneration share this monitor. Restore/store is one
			// boundary; setters restart regeneration for submax vitals, so cancel it again.
			synchronized (actor)
			{
				synchronized (actor.getStatus())
				{
					actor.setDead(_dead); actor.setCurrentHp(_hp); actor.setCurrentMp(_mp); actor.setCurrentCp(_cp);
					actor.getStatus().stopHpMpRegeneration();
					try { if (!_stored) { actor.storeMe(); _stored = true; } }
					finally
					{
						// An already executing regeneration callback can be waiting for the monitor.
						// Tombstone only this removed runtime instance: persisted vitals stay intact.
						actor.setDead(true); actor.deleteMe(); actor.stopAllTasks(); PlayerAutoSaveTaskManager.getInstance().remove(actor);
					}
				}
			}
			if ((World.getInstance().getPlayer(_objectId) != null) || (World.getInstance().findObject(_objectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId)) { throw new IllegalStateException("SYNTHETIC_CLEANUP_INCOMPLETE"); }
			_actor = null; _actions = null;
		}
		if (_lease != null) { _lease.close(); _lease = null; }
	}
}
