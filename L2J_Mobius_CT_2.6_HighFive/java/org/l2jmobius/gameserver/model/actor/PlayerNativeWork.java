package org.l2jmobius.gameserver.model.actor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;

/** Optional native lifetime ownership. Ordinary players keep their stock execution path. */
public final class PlayerNativeWork
{
	public enum Semantics { EARNED, CANCELLABLE }
	public interface Owner
	{
		Player player();
		Ticket reserve(Ticket parent, String kind, Semantics semantics);
		boolean isCurrent();
		void recordFailure(Throwable failure);
		<T> T checkpoint(Supplier<T> action);
		<T> T pendingStoreCheckpoint(Object ownerKey, Supplier<T> action);
		boolean sealed();
		default PlayerNativeEvidence evidence() { return null; }
		default long epoch() { return -1; }
		default boolean nativeObservationHealthy() { return true; }
		TimerRegistration registerTimer(Ticket parent, String kind, boolean repeating, Runnable stop);
	}
	public interface Ticket
	{
		Owner owner();
		Semantics semantics();
		boolean tryStart();
		boolean isRunning();
		boolean cancelBeforeStart();
		void complete(Throwable failure);
		void rejected(Throwable failure);
		default PlayerNativeEvidence.CombatEpisode combatEpisode() { return null; }
	}
	public interface Context extends AutoCloseable
	{
		@Override void close();
	}
	/** Pending producers and running invocations share the owner's admission monitor. */
	public interface TimerRegistration
	{
		Ticket start();
		boolean stopped();
		Cancellation cancel(boolean callback);
		void completed(Ticket ticket, Throwable failure);
		void rejected(Throwable failure);
	}
	public record Cancellation(boolean accepted, Ticket ticket, boolean invokeCallback) { }
	private record Frame(Ticket ticket, Frame previous) { }
	private static final ThreadLocal<Frame> CONTEXT = new ThreadLocal<>();
	private record CombatFrame(Owner owner, PlayerNativeEvidence.CombatEpisode episode, CombatFrame previous) { }
	private static final ThreadLocal<CombatFrame> COMBAT_CONTEXT = new ThreadLocal<>();
	private PlayerNativeWork() { }

	/** Exact native lifetime only; a delayed old context never resolves a replacement sensor. */
	public static PlayerNativeEvidence observationEvidence(Player player)
	{
		if ((player == null) || !player.isNativeWorkManaged()) { return null; }
		final Owner owner = ownerFor(player);
		if ((owner == null) || (player.getNativeWorkOwner() != owner) || (owner.player() != player) || !owner.isCurrent() || !owner.nativeObservationHealthy()
			|| (World.getInstance().getPlayer(player.getObjectId()) != player) || (World.getInstance().findObject(player.getObjectId()) != player)) { return null; }
		final PlayerNativeEvidence evidence = owner.evidence();
		return (evidence != null) && evidence.matches(player.getObjectId(), owner.epoch()) ? evidence : null;
	}
	private static PlayerNativeEvidence.CombatEpisode inheritedCombat(Owner owner)
	{
		for (CombatFrame frame = COMBAT_CONTEXT.get(); frame != null; frame = frame.previous()) { if (frame.owner() == owner) { return frame.episode(); } }
		return null;
	}
	/** Scope captures only this scalar token at actual ticket reservation, before any native publication. */
	public static PlayerNativeEvidence.CombatEpisode captureCombatEpisode(Owner owner)
	{
		final var inherited = inheritedCombat(owner);
		if (inherited == null) { return null; }
		final var evidence = observationEvidence(owner.player());
		return (evidence != null) && (evidence == owner.evidence()) ? evidence.reserveCombat(inherited) : null;
	}
	public static void combatDamage(Player player, Owner capturedOwner, PlayerNativeEvidence capturedEvidence, double actualHpDelta)
	{
		if ((capturedOwner != null) && (player.getNativeWorkOwner() == capturedOwner) && (observationEvidence(player) == capturedEvidence))
		{
			capturedEvidence.combatDamage(inheritedCombat(capturedOwner), actualHpDelta);
		}
	}
	private static boolean nativeCombatPassive(Player player)
	{
		if (player.isDead()) { return true; }
		if (player.isAttackingOrCastingNow()) { return false; }
		final WorldObject target = player.getTarget();
		if ((target == null) || (target.isCreature() && target.asCreature().isDead())) { return true; }
		return !player.hasAI() || ((player.getAI().getIntention() != Intention.ATTACK) && (player.getAI().getIntention() != Intention.CAST));
	}
	/** Original passive processing has completed; reserved/running continuation counts still veto early retirement. */
	public static void probeCombat(Player player)
	{
		final PlayerNativeEvidence evidence = observationEvidence(player);
		if (evidence == null) { return; }
		final var episode = evidence.currentCombatEpisode();
		if (nativeCombatPassive(player) && (observationEvidence(player) == evidence)) { evidence.passiveCombat(episode); }
	}
	private static final class CombatBinding
	{
		private final Player _player; private final Owner _owner; private final long _epoch;
		private final PlayerNativeEvidence _evidence; private final PlayerNativeEvidence.CombatEpisode _episode;
		private boolean _completed;
		private CombatBinding(Participant participant, PlayerNativeEvidence evidence, PlayerNativeEvidence.CombatEpisode episode)
		{
			_player = participant.player(); _owner = participant.owner(); _epoch = participant.epoch(); _evidence = evidence; _episode = episode;
		}
		private Context enterObservation()
		{
			final CombatFrame previous = COMBAT_CONTEXT.get(); final var frame = new CombatFrame(_owner, _episode, previous); COMBAT_CONTEXT.set(frame);
			return () ->
			{
				if (COMBAT_CONTEXT.get() != frame) { throw new IllegalStateException("NATIVE_COMBAT_CONTEXT_ORDER"); }
				if (previous == null) { COMBAT_CONTEXT.remove(); } else { COMBAT_CONTEXT.set(previous); }
			};
		}
		private void complete()
		{
			synchronized (this) { if (_completed) { return; } _completed = true; }
			final boolean exact = (_player.getNativeWorkOwner() == _owner) && (_owner.epoch() == _epoch) && (observationEvidence(_player) == _evidence);
			_evidence.completeCombat(_episode, exact && nativeCombatPassive(_player));
		}
	}

	/** Acquire quiescence before any Player/status/DB monitor is entered. */
	public static <T> T checkpoint(Player player, Supplier<T> action)
	{
		if (!player.isNativeWorkManaged()) { return action.get(); }
		final Owner owner = player.getNativeWorkOwner();
		if ((owner == null) || !owner.isCurrent()) { throw new IllegalStateException("NATIVE_CHECKPOINT_OWNER_MISSING"); }
		return owner.checkpoint(action);
	}

	/** Explicit recovery of the attached immutable receipt, without ordinary root admission. */
	public static <T> T pendingStoreCheckpoint(Player player, Object ownerKey, Supplier<T> action)
	{
		if (!player.isNativeWorkManaged()) { return action.get(); }
		final Owner owner = player.getNativeWorkOwner();
		if ((owner == null) || !owner.isCurrent()) { throw new IllegalStateException("NATIVE_CHECKPOINT_OWNER_MISSING"); }
		return owner.pendingStoreCheckpoint(Objects.requireNonNull(ownerKey), action);
	}

	public static Player inheritedPlayer()
	{
		final Frame frame = CONTEXT.get();
		return frame == null ? null : frame.ticket().owner().player();
	}

	/** Exact executing participants, including every owner of a native multi-target callback. */
	public static List<Player> inheritedPlayers()
	{
		final List<Player> players = new ArrayList<>();
		final var seen = Collections.newSetFromMap(new IdentityHashMap<Player, Boolean>());
		for (Frame frame = CONTEXT.get(); frame != null; frame = frame.previous())
		{
			final Ticket ticket = frame.ticket();
			if (ticket.isRunning() && seen.add(ticket.owner().player())) { players.add(ticket.owner().player()); }
		}
		return players;
	}

	public static Ticket current(Owner owner)
	{
		for (Frame frame = CONTEXT.get(); frame != null; frame = frame.previous())
		{
			if ((frame.ticket().owner() == owner) && frame.ticket().isRunning()) { return frame.ticket(); }
		}
		return null;
	}

	public static Context enter(Ticket ticket)
	{
		if (!ticket.isRunning()) { throw new IllegalStateException("NATIVE_WORK_CONTEXT_NOT_RUNNING"); }
		final Frame previous = CONTEXT.get();
		final Frame frame = new Frame(ticket, previous);
		CONTEXT.set(frame);
		final CombatFrame previousCombat = COMBAT_CONTEXT.get();
		final CombatFrame combat = ticket.combatEpisode() == null ? null : new CombatFrame(ticket.owner(), ticket.combatEpisode(), previousCombat);
		if (combat != null) { COMBAT_CONTEXT.set(combat); }
		return () ->
		{
			if (CONTEXT.get() != frame) { throw new IllegalStateException("NATIVE_WORK_CONTEXT_ORDER"); }
			if (combat != null)
			{
				if (COMBAT_CONTEXT.get() != combat) { throw new IllegalStateException("NATIVE_COMBAT_CONTEXT_ORDER"); }
				if (previousCombat == null) { COMBAT_CONTEXT.remove(); } else { COMBAT_CONTEXT.set(previousCombat); }
			}
			if (previous == null) { CONTEXT.remove(); } else { CONTEXT.set(previous); }
		};
	}

	public static void recordFailure(Throwable failure)
	{
		final var seen = Collections.newSetFromMap(new IdentityHashMap<Owner, Boolean>());
		for (Frame frame = CONTEXT.get(); frame != null; frame = frame.previous())
		{
			if (seen.add(frame.ticket().owner())) { frame.ticket().owner().recordFailure(failure); }
		}
	}

	/** Explicit ordinary participants never borrow an unrelated ambient owner. */
	public static void run(Creature actor, Collection<? extends WorldObject> targets, String kind, Runnable action)
	{
		final List<Participant> participants = capture(actor, targets, kind);
		Semantics semantics = Semantics.CANCELLABLE;
		for (Participant participant : participants)
		{
			final Ticket parent = participant.owner() == null ? null : current(participant.owner());
			if ((parent != null) && (parent.semantics() == Semantics.EARNED)) { semantics = Semantics.EARNED; break; }
		}
		requireCapturedEarnedParticipants(participants);
		final ParticipantWork work = reserve(participants, kind, semantics);
		if (work == null) { return; }
		if (work.ordinary()) { action.run(); } else { work.run(action); }
	}
	/** Explicit original attack/cast entry. No phase is published until its own positive HP writer. */
	public static void runCombat(Creature actor, Collection<? extends WorldObject> targets, String kind, Runnable action)
	{
		final Player player = actor instanceof Player nativePlayer ? nativePlayer : null;
		final PlayerNativeEvidence evidence = observationEvidence(player);
		if (evidence == null) { run(actor, targets, kind, action); return; }
		final Owner owner = ownerFor(player);
		final var episode = evidence.reserveCombat(inheritedCombat(owner));
		if (episode == null) { run(actor, targets, kind, action); return; }
		// This short root-operation reservation prevents an inline/passive clear before its first ticket exists.
		final var binding = new CombatBinding(new Participant(player, owner, owner.epoch(), null), evidence, episode);
		try (var observation = binding.enterObservation()) { run(actor, targets, kind, action); }
		finally { binding.complete(); }
	}

	/** Capture and reserve all affected lifetimes before a single native task is published. */
	public static ScheduledFuture<?> schedule(Creature actor, Collection<? extends WorldObject> targets, String kind, Semantics semantics, Runnable action, long delay)
	{
		final ParticipantWork work = reserve(actor, targets, kind, semantics);
		if (work == null) { return null; }
		if (work.ordinary()) { return ThreadPool.schedule(action, delay); }
		try { return new ParticipantFuture(ThreadPool.scheduleOrThrow(() -> work.run(action), delay), work); }
		catch (RuntimeException | Error failure) { work.rejected(failure); throw failure; }
	}

	public static void execute(Creature actor, Collection<? extends WorldObject> targets, String kind, Semantics semantics, Runnable action)
	{
		final ParticipantWork work = reserve(actor, targets, kind, semantics);
		if (work == null) { return; }
		if (work.ordinary()) { ThreadPool.execute(action); return; }
		try { ThreadPool.executeOrThrow(() -> work.run(action)); }
		catch (RuntimeException | Error failure) { work.rejected(failure); throw failure; }
	}

	private record Participant(Player player, Owner owner, long epoch, Ticket ticket)
	{
		boolean current() { return (owner != null) && (player.getNativeWorkOwner() == owner) && (owner.player() == player) && (owner.epoch() == epoch) && owner.isCurrent(); }
	}

	private static ParticipantWork reserve(Creature actor, Collection<? extends WorldObject> targets, String kind, Semantics semantics)
	{
		final List<Participant> participants = capture(actor, targets, kind);
		requireCapturedEarnedParticipants(participants);
		return reserve(participants, kind, semantics);
	}

	private static List<Participant> capture(Creature actor, Collection<? extends WorldObject> targets, String kind)
	{
		final List<Participant> participants = new ArrayList<>();
		final var seen = Collections.newSetFromMap(new IdentityHashMap<Player, Boolean>());
		capture(actor, seen, participants);
		if (targets != null) { for (WorldObject target : targets) { capture(target, seen, participants); } }
		if (kind.startsWith("attack-") || kind.startsWith("cast-"))
		{
			captureCombatRecipients(actor, seen, participants);
			if (targets != null) { for (WorldObject target : targets) { captureCombatRecipients(target, seen, participants); } }
		}
		return participants;
	}

	private static void captureCombatRecipients(WorldObject object, Set<Player> seen, List<Participant> participants)
	{
		if (object == null) { return; }
		final Player player = object.isSummon() ? object.asSummon().getOwner() : object.asPlayer();
		if (player != null)
		{
			capture(player.getTransferingDamageTo(), seen, participants);
			final var party = player.getParty();
			if (party != null)
			{
				final var channel = party.getCommandChannel();
				for (Player member : channel == null ? party.getMembers() : channel.getMembers()) { capture(member, seen, participants); }
			}
		}
		if (object instanceof Attackable attackable)
		{
			for (Creature attacker : attackable.getAggroList().keySet())
			{
				capture(attacker, seen, participants);
				if (attacker.isPlayable()) { captureCombatRecipients(attacker, seen, participants); }
			}
		}
	}

	/** A delayed earned body cannot discover and silently borrow a different live lifetime. */
	private static void requireCapturedEarnedParticipants(List<Participant> participants)
	{
		boolean earned = false;
		for (Frame frame = CONTEXT.get(); frame != null; frame = frame.previous())
		{
			if (frame.ticket().isRunning() && frame.ticket().semantics() == Semantics.EARNED) { earned = true; break; }
		}
		if (!earned) { return; }
		for (Participant participant : participants)
		{
			if (!participant.current() || current(participant.owner()) == null)
			{
				final var failure = new IllegalStateException("NATIVE_EARNED_RECIPIENT_NOT_CAPTURED");
				recordFailure(failure);
				if (participant.owner() != null) { participant.owner().recordFailure(failure); }
				throw failure;
			}
		}
	}

	private static ParticipantWork reserve(List<Participant> participants, String kind, Semantics semantics)
	{
		final ParticipantWork work = new ParticipantWork(participants, semantics);
		try
		{
			for (int i = 0; i < participants.size(); i++)
			{
				final Participant participant = participants.get(i);
				final Ticket ticket = participant.current() ? participant.owner().reserve(current(participant.owner()), kind, semantics) : null;
				if (ticket == null)
				{
					work.refused(new IllegalStateException("NATIVE_PARTICIPANT_ADMISSION_REFUSED"));
					return null;
				}
				participants.set(i, new Participant(participant.player(), participant.owner(), participant.epoch(), ticket));
			}
			return work;
		}
		catch (RuntimeException | Error failure) { work.rejected(failure); throw failure; }
	}

	private static void capture(WorldObject object, Set<Player> seen, List<Participant> participants)
	{
		if (object == null) { return; }
		final Player player = object.isSummon() ? object.asSummon().getOwner() : object.asPlayer();
		if ((player == null) || !player.isNativeWorkManaged() || !seen.add(player)) { return; }
		final Owner owner = ownerFor(player);
		participants.add(new Participant(player, owner, owner == null ? -1 : owner.epoch(), null));
	}

	private static Owner ownerFor(Player player)
	{
		for (Frame frame = CONTEXT.get(); frame != null; frame = frame.previous())
		{
			if ((frame.ticket().owner().player() == player) && frame.ticket().isRunning()) { return frame.ticket().owner(); }
		}
		return player.getNativeWorkOwner();
	}

	private static final class ParticipantWork
	{
		private enum State { RESERVED, RUNNING, FINISHED }
		private final List<Participant> _participants;
		private final Semantics _semantics;
		private State _state = State.RESERVED;
		ParticipantWork(List<Participant> participants, Semantics semantics) { _participants = participants; _semantics = semantics; }
		boolean ordinary() { return _participants.isEmpty(); }

		private synchronized boolean start()
		{
			if (_state != State.RESERVED) { return false; }
			try
			{
				for (Participant participant : _participants)
				{
					if (!participant.current() || !participant.ticket().tryStart())
					{
						refused(new IllegalStateException("NATIVE_PARTICIPANT_START_REFUSED"));
						return false;
					}
				}
			}
			catch (RuntimeException | Error failure) { rejected(failure); throw failure; }
			_state = State.RUNNING;
			return true;
		}

		void run(Runnable action)
		{
			if (!start()) { return; }
			Throwable failure = null;
			final List<Context> contexts = new ArrayList<>();
			try
			{
				try
				{
					for (Participant participant : _participants) { contexts.add(enter(participant.ticket())); }
					action.run();
				}
				finally { for (int i = contexts.size() - 1; i >= 0; i--) { contexts.get(i).close(); } }
			}
			catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
			finally
			{
				Throwable completionFailure = null;
				try
				{
					for (Participant participant : _participants)
					{
						try { participant.ticket().complete(failure); }
						catch (RuntimeException | Error thrown)
						{
							participant.owner().recordFailure(thrown);
							if (failure != null) { if (failure != thrown) { failure.addSuppressed(thrown); } }
							else if (completionFailure == null) { completionFailure = thrown; }
							else if (completionFailure != thrown) { completionFailure.addSuppressed(thrown); }
						}
					}
				}
				finally { synchronized (this) { _state = State.FINISHED; _participants.clear(); } }
				if (completionFailure instanceof RuntimeException runtime) { throw runtime; }
				if (completionFailure instanceof Error error) { throw error; }
			}
		}

		synchronized boolean cancel()
		{
			if (_state == State.RUNNING) { return true; }
			if ((_state != State.RESERVED) || (_semantics == Semantics.EARNED)) { return false; }
			finishUnpublished(null);
			return true;
		}

		synchronized void refused(Throwable failure)
		{
			finishUnpublished(_semantics == Semantics.EARNED ? failure : null);
		}

		synchronized void rejected(Throwable failure) { finishUnpublished(failure); }

		private void finishUnpublished(Throwable failure)
		{
			if (_state != State.RESERVED) { return; }
			Throwable retirementFailure = null;
			try
			{
				for (Participant participant : _participants)
				{
					try
					{
						final Ticket ticket = participant.ticket();
						// A scope may already have retired a stale ticket; retain the captured owner's incident anyway.
						if ((failure != null) && (participant.owner() != null)) { participant.owner().recordFailure(failure); }
						if (ticket == null) { continue; }
						if (ticket.isRunning()) { ticket.complete(failure); }
						else if (failure != null) { ticket.rejected(failure); }
						else { ticket.cancelBeforeStart(); }
					}
					catch (RuntimeException | Error thrown)
					{
						if (participant.owner() != null) { participant.owner().recordFailure(thrown); }
						if (failure != null) { if (failure != thrown) { failure.addSuppressed(thrown); } }
						else if (retirementFailure == null) { retirementFailure = thrown; }
						else if (retirementFailure != thrown) { retirementFailure.addSuppressed(thrown); }
					}
				}
			}
			finally { _state = State.FINISHED; _participants.clear(); }
			if (retirementFailure instanceof RuntimeException runtime) { throw runtime; }
			if (retirementFailure instanceof Error error) { throw error; }
		}
	}

	private record ParticipantFuture(ScheduledFuture<?> future, ParticipantWork work) implements ScheduledFuture<Object>
	{
		private ParticipantFuture { Objects.requireNonNull(future); }
		@Override public boolean cancel(boolean interrupt) { return work.cancel() && future.cancel(interrupt); }
		@Override public boolean isCancelled() { return future.isCancelled(); }
		@Override public boolean isDone() { return future.isDone(); }
		@Override public Object get() throws InterruptedException, ExecutionException { return future.get(); }
		@Override public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return future.get(timeout, unit); }
		@Override public long getDelay(TimeUnit unit) { return future.getDelay(unit); }
		@Override public int compareTo(Delayed other) { return future.compareTo(other); }
	}

	public static void run(Player player, String kind, Runnable action)
	{
		if (!player.isNativeWorkManaged()) { action.run(); return; }
		final Owner owner = ownerFor(player);
		if ((owner == null) || !owner.isCurrent()) { return; }
		if (current(owner) != null) { action.run(); return; }
		final Ticket ticket = owner.reserve(null, kind, Semantics.CANCELLABLE);
		if (ticket == null) { return; }
		run(ticket, action);
	}

	private static void run(Ticket ticket, Runnable action)
	{
		if (!ticket.tryStart()) { return; }
		Throwable failure = null;
		try (var context = enter(ticket)) { action.run(); }
		catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
		finally { ticket.complete(failure); }
	}

	/** Reserve before publishing to the executor, including submissions that run inline. */
	public static ScheduledFuture<?> schedule(Player player, String kind, Semantics semantics, Runnable action, long delay)
	{
		if ((player == null) || !player.isNativeWorkManaged()) { return ThreadPool.schedule(action, delay); }
		final Ticket ticket = reserve(player, kind, semantics);
		if (ticket == null) { return null; }
		try
		{
			final ScheduledFuture<?> future = ThreadPool.scheduleOrThrow(() -> run(ticket, action), delay);
			return new OwnedFuture(future, ticket);
		}
		catch (RuntimeException | Error failure) { ticket.rejected(failure); throw failure; }
	}

	public static void execute(Player player, String kind, Semantics semantics, Runnable action)
	{
		if ((player == null) || !player.isNativeWorkManaged()) { ThreadPool.execute(action); return; }
		final Ticket ticket = reserve(player, kind, semantics);
		if (ticket == null) { return; }
		try { ThreadPool.executeOrThrow(() -> run(ticket, action)); }
		catch (RuntimeException | Error failure) { ticket.rejected(failure); throw failure; }
	}

	/** Ambient periodic work accounts only each executing invocation, not the idle delay. */
	public static ScheduledFuture<?> scheduleAtFixedRate(Player player, String kind, Runnable action, long initialDelay, long period)
	{
		if ((player == null) || !player.isNativeWorkManaged()) { return ThreadPool.scheduleAtFixedRate(action, initialDelay, period); }
		final var timer = new PlayerNativeTimer(player, kind, true, () -> { });
		if (!timer.accepted()) { return null; }
		timer.submit(action, initialDelay, period);
		return timer.future();
	}

	private static Ticket reserve(Player player, String kind, Semantics semantics)
	{
		final Owner owner = ownerFor(player);
		if ((owner == null) || !owner.isCurrent()) { return null; }
		return owner.reserve(current(owner), kind, semantics);
	}

	private record OwnedFuture(ScheduledFuture<?> future, Ticket ticket) implements ScheduledFuture<Object>
	{
		private OwnedFuture { Objects.requireNonNull(future); }
		@Override public boolean cancel(boolean interrupt)
		{
			// A cancelled Future may be done while its RUNNING body still owns the lifetime.
			if (ticket.cancelBeforeStart() || ticket.isRunning()) { return future.cancel(interrupt); }
			return false;
		}
		@Override public boolean isCancelled() { return future.isCancelled(); }
		@Override public boolean isDone() { return future.isDone(); }
		@Override public Object get() throws InterruptedException, ExecutionException { return future.get(); }
		@Override public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return future.get(timeout, unit); }
		@Override public long getDelay(TimeUnit unit) { return future.getDelay(unit); }
		@Override public int compareTo(Delayed other) { return future.compareTo(other); }
	}
}
