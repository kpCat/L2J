/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import static org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.MAX_ITEM_DEBITS;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ItemDebit;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ResourceObservation;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.NativeHealReceipt;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeCastTrace;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeTerminal;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.NativeTiming;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.HealBinding;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.ScheduledArm;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.SoloDamageBinding;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Stratum;

/** Joins paid stock cast originals and native writes for owned AUDIT episodes. */
final class PhantomLearning131AuditDamage implements AutoCloseable
{
	private static final int MAX_CASTS = 64;
	private static final int MAX_HEAL_WRITES = 512;
	private static final AtomicReference<PhantomLearning131AuditDamage> ACTIVE = new AtomicReference<>();
	private static final AtomicReference<PvpCapture> ACTIVE_PVP = new AtomicReference<>();
	private static final AtomicReference<GroupHealCapture> ACTIVE_GROUP = new AtomicReference<>();

	record DuelDeath(int killer, int victim, long victimEpoch, long corpseGeneration, long observedNanos) { }
	record DuelDamageTotals(int paidDealt, int paidReceived, BigDecimal hpDealt, BigDecimal cpDealt, BigDecimal hpReceived, BigDecimal cpReceived) { }
	/** Captured paid damage totals, stock deaths and any death rows with exact action attribution. */
	record DuelEvidence(List<PaidPvpDamage> paidDamage, DuelDamageTotals damageTotals, List<DuelDeath> nativeDeaths,
		Optional<Outcome> nativeDeathOutcome, boolean allDeathsLinked, List<String> eventRows, List<String> originalRows) { }
	private record DuelEvent(long nanos, String row) { }

	private static final class ItemCapture implements AutoCloseable
	{
		private final ConcurrentLinkedQueue<ItemDebit> _items = new ConcurrentLinkedQueue<>();
		private final AtomicInteger _queued = new AtomicInteger();
		private ResourceObservation _observer;
		private boolean _closed;

		void open(BooleanSupplier current)
		{
			_observer = PlayerNativeEvidence.observeResourceWrites(write ->
			{
				if (write instanceof ItemDebit item && current.getAsBoolean()
					&& _queued.getAndUpdate(count -> Math.min(count + 1, MAX_ITEM_DEBITS)) < MAX_ITEM_DEBITS)
				{
					_items.add(item);
				}
			});
		}

		@Override public synchronized void close()
		{
			if (_observer != null && !_closed)
			{
				_observer.close();
				_closed = true;
			}
		}

		void addRows(String episodeId, List<String> events, List<String> originals)
		{
			require(_closed && _observer.failure() == null, "NATIVE_ITEM_OBSERVER_FAILED");
			// Publish the actual writer count, including writes the bounded callback couldn't queue.
			originals.add(PhantomLearning131AuditOriginals.itemWindow(_observer.openedNanos(), _observer.closedNanos(), _observer.itemWrites()));
			_items.stream().sorted(Comparator.comparingLong(ItemDebit::sequence)).forEach(item ->
			{
				originals.add(PhantomLearning131AuditOriginals.itemRow(episodeId, item));
				events.add(PhantomLearning131AuditOriginals.paymentRow(episodeId, item));
			});
		}
	}

	/** The original preserves separate nullable pools, exact MP and the native cast stages. */
	static final class PaidPvpDamage
	{
		private final String _episodeId;
		private final String _bindingSha;
		private final PvpReceipt _original;
		private final NativeCastTrace _trace;
		private final String _role;
		private final NativeTerminal _terminal;
		private final DuelDeath _stock;

		private PaidPvpDamage(String episodeId, String bindingSha, PvpReceipt original, NativeCastTrace trace, String role,
			NativeTerminal terminal, DuelDeath stock)
		{
			_episodeId = episodeId;
			_bindingSha = bindingSha;
			_original = original;
			_trace = trace;
			_role = role;
			_terminal = terminal;
			_stock = stock;
		}

		String episodeId() { return _episodeId; }
		String bindingSha() { return _bindingSha; }
		PvpReceipt original() { return _original; }
		String operationId() { return PhantomLearning131AuditDamage.operationId(_episodeId, _original.owner()); }
		String originalRow() { return PhantomLearning131AuditOriginals.pvpRow(_original); }

		List<String> originalRows()
		{
			final String cast = PhantomLearning131AuditOriginals.castRow(_trace), paid = originalRow();
			return _terminal == null ? List.of(cast, paid) : List.of(cast, paid,
				PhantomLearning131AuditOriginals.terminalRow(_terminal), PhantomLearning131AuditOriginals.stockRow(_original.eventKey(), _stock));
		}

		List<String> eventRows() { return timedEvents().stream().map(DuelEvent::row).toList(); }

		private List<DuelEvent> timedEvents()
		{
			final var rows = new ArrayList<>(castTimedEvents(_trace, operationId(), _role));
			rows.add(new DuelEvent(damageNanos(), eventRow()));
			if (_terminal != null)
			{
				rows.add(new DuelEvent(_terminal.deathNanos(), deathEvent(_terminal, operationId(), _terminal.corpseGeneration())));
			}
			return rows;
		}

		long damageNanos() { return Math.max(lastWrite(_original.hp()), lastWrite(_original.cp())); }

		String eventRow()
		{
			final var action = _original.attempt().selected();
			return String.join("\t", "event", _original.eventKey(), Long.toString(damageNanos()), "DAMAGE",
				Integer.toString(action.actorObjectId()), Integer.toString(action.targetObjectId()), operationId(),
				poolDamage(_original.hp()), poolDamage(_original.cp()));
		}

		private static String poolDamage(PvpPoolWrite write)
		{
			return write == null ? "0" : PhantomLearning131AuditOriginals.wireDamage(write.delta()).toPlainString();
		}

		private static long lastWrite(PvpPoolWrite write) { return write == null ? 0 : write.lastWrite(); }
	}

	private record PvpParticipant(long profile, Player player, long epoch, PlayerNativeWork.Owner owner, Party party)
	{
		PvpParticipant(long profile, Player player, long epoch)
		{
			this(profile, player, epoch, player.getNativeWorkOwner(), player.getParty());
		}

		boolean current()
		{
			return profile > 0 && currentOwner(player, epoch, owner);
		}

		boolean drained()
		{
			return drainedOwner(player, owner);
		}
	}

	private static boolean currentOwner(Player player, long epoch, PlayerNativeWork.Owner owner)
	{
		return owner != null && player.getNativeWorkOwner() == owner && owner.player() == player && owner.epoch() == epoch && owner.isCurrent()
			&& owner.evidence() != null && owner.evidence() == PlayerNativeWork.observationEvidence(player)
			&& World.getInstance().findObject(player.getObjectId()) == player;
	}

	private static boolean drainedOwner(Player player, PlayerNativeWork.Owner owner)
	{
		return !owner.isCurrent() && PlayerNativeWork.observationEvidence(player) == null && World.getInstance().findObject(player.getObjectId()) == null;
	}

	static boolean opposingOwners(Map<Integer, Long> epochs, Set<Integer> scored, int actor, long actorEpoch, int target, long targetEpoch)
	{
		return actorEpoch > 0 && targetEpoch > 0 && scored.contains(actor) != scored.contains(target)
			&& epochs.getOrDefault(actor, 0L) == actorEpoch && epochs.getOrDefault(target, 0L) == targetEpoch;
	}

	private record HealParticipant(Player player, long epoch, PlayerNativeWork.Owner owner)
	{
		boolean current(int instance, long observedEpoch)
		{
			return observedEpoch == epoch && player.getInstanceId() == instance && currentOwner(player, epoch, owner);
		}

		boolean drained() { return drainedOwner(player, owner); }
	}

	/** Passive HEAL and item writer capture for the exact bound group. Combat and leases stay with their native owners. */
	static final class GroupHealCapture implements AutoCloseable
	{
		private final HealBinding _binding;
		private final Path _bindingFile;
		private final int _instance;
		private final Map<Integer, HealParticipant> _players;
		private final PvpCapture _pvpDamage;
		private final ItemCapture _items = new ItemCapture();
		private final ConcurrentLinkedQueue<NativeHealReceipt> _heals = new ConcurrentLinkedQueue<>();
		private final AtomicInteger _queued = new AtomicInteger();
		private final AtomicReference<String> _failure = new AtomicReference<>();
		private AutoCloseable _observer;
		private boolean _draining;
		private volatile boolean _closed;

		private GroupHealCapture(HealBinding binding, Path bindingFile, Map<Integer, Player> players, PvpCapture pvpDamage)
		{
			_binding = binding;
			_bindingFile = bindingFile;
			_pvpDamage = pvpDamage;
			final var roster = Map.copyOf(players);
			require(roster.size() > 1 && roster.keySet().equals(binding.playerEpochs().keySet())
				&& binding.started() <= System.nanoTime(), "NATIVE_HEAL_ROSTER");
			_instance = roster.values().iterator().next().getInstanceId();
			final var participants = new HashMap<Integer, HealParticipant>();
			roster.forEach((id, player) ->
			{
				final long epoch = binding.playerEpochs().get(id);
				final var participant = new HealParticipant(player, epoch, player.getNativeWorkOwner());
				require(player.getObjectId() == id && !player.isAlikeDead() && participant.current(_instance, epoch), "NATIVE_HEAL_OWNER");
				participants.put(id, participant);
			});
			_players = Map.copyOf(participants);
		}

		private void install()
		{
			_items.open(() -> ACTIVE_GROUP.get() == this);
			_observer = HealEvidenceV3.observeNativeAudit(this::nativeHeal, reason -> _failure.compareAndSet(null, reason));
			if (_pvpDamage != null) { _pvpDamage.claim(); }
		}

		private void nativeHeal(NativeHealReceipt receipt)
		{
			if (_closed || ACTIVE_GROUP.get() != this || _failure.get() != null) { return; }
			try { recordHeal(receipt); }
			catch (RuntimeException unavailable) { _failure.compareAndSet(null, "NATIVE_HEAL_OBSERVER_FAILED"); }
		}

		private void recordHeal(NativeHealReceipt receipt)
		{
			final var cast = receipt.cast();
			final var actor = _players.get(cast.actorObjectId());
			final var target = _players.get(cast.targetObjectId());
			if (actor == null || target == null || cast.instanceId() != _instance || receipt.observation().capturedNanos() < _binding.started()
				|| !actor.current(_instance, cast.actorEpoch()) || !target.current(_instance, cast.targetEpoch())
				|| _binding.scoredPlayers().contains(cast.actorObjectId()) != _binding.scoredPlayers().contains(cast.targetObjectId()))
			{
				_failure.compareAndSet(null, "FOREIGN_NATIVE_HEAL");
				return;
			}
			if (_queued.getAndUpdate(count -> Math.min(count + 1, MAX_HEAL_WRITES)) >= MAX_HEAL_WRITES)
			{
				_failure.compareAndSet(null, "NATIVE_HEAL_RECEIPT_CAP");
				return;
			}
			_heals.add(receipt);
		}

		String sealAfterDrain(TacticalEpisodeV3.NativeDrain drain, long combatEnd, List<String> otherEvents, List<String> otherOriginals) throws Exception
		{
			synchronized (this)
			{
				require(!_closed && !_draining && ACTIVE_GROUP.get() == this, "NATIVE_HEAL_CAPTURE_NOT_OPEN");
				_draining = true;
			}
			try
			{
				drain.finish();
				require(_players.values().stream().allMatch(HealParticipant::drained), "NATIVE_HEAL_GROUP_NOT_DRAINED");
				return sealDrained(combatEnd, otherEvents, otherOriginals);
			}
			finally { close(); }
		}

		private synchronized String sealDrained(long combatEnd, List<String> otherEvents, List<String> otherOriginals) throws IOException
		{
			require(!_closed && ACTIVE_GROUP.get() == this, "NATIVE_HEAL_CAPTURE_NOT_OPEN");
			stopObservers();
			final long settled = System.nanoTime();
			final String failure = _failure.get();
			require(failure == null, failure);
			final var events = new ArrayList<>(otherEvents);
			final var originals = new ArrayList<>(otherOriginals);
			if (_pvpDamage != null)
			{
				final var damage = _pvpDamage.finished(settled);
				require(damage.allDeathsLinked() && !damage.paidDamage().isEmpty(), "INVALID_OR_EMPTY_NATIVE_GROUP_PVP");
				events.addAll(damage.eventRows());
				originals.addAll(damage.originalRows());
			}
			_heals.stream().sorted(Comparator.comparingLong((NativeHealReceipt heal) -> heal.cast().decisionNanos())
				.thenComparing(heal -> heal.cast().eventKey())).forEach(heal ->
				{
					events.addAll(PhantomLearning131AuditOriginals.healEvents(_binding.episodeId(), heal));
					originals.add(PhantomLearning131AuditOriginals.healRow(heal));
				});
			_items.addRows(_binding.episodeId(), events, originals);
			return sealClosedDamage(_bindingFile, _binding.episodeId(), _binding.sha(), _pvpDamage != null, combatEnd, settled, events, originals);
		}

		private void stopObservers()
		{
			final var observer = _observer;
			_observer = null;
			try { _items.close(); }
			finally { closeAuditObserver(observer); }
		}

		@Override public synchronized void close()
		{
			_closed = true;
			if (ACTIVE_GROUP.get() == this)
			{
				try { stopObservers(); }
				finally
				{
					try { if (_pvpDamage != null) { _pvpDamage.close(); } }
					finally { ACTIVE_GROUP.compareAndSet(this, null); }
				}
			}
		}
	}

	static final class PvpCapture implements AutoCloseable
	{
		private final Path _bindingFile;
		private final String _episodeId;
		private final String _bindingSha;
		private final long _started;
		private final Map<Integer, PvpParticipant> _roster;
		private final Map<Integer, Long> _epochs;
		private final Set<Integer> _scored;
		private final int _instance;
		private final int _maxCasts;
		private final Map<LearningPolicy, LearningPolicy.Observer> _delegates;
		private final Map<TacticalReceiptV3.Attempt, Long> _issued = new IdentityHashMap<>();
		private final Map<String, PvpReceipt> _paid = new HashMap<>();
		private final Map<String, NativeCastTrace> _traces = new HashMap<>();
		private final Map<Integer, DuelDeath> _stockDeaths = new HashMap<>();
		private final Map<String, NativeTerminal> _terminals = new HashMap<>();
		private final HashSet<String> _operations = new HashSet<>();
		private final Map<Integer, ConsumerEventListener> _deathListeners;
		private final List<Integer> _listening = new ArrayList<>();
		private final List<LearningPolicy> _wrapped = new ArrayList<>();
		private final ItemCapture _items;
		private AutoCloseable _auditObserver;
		private boolean _draining;
		private boolean _closed;
		private boolean _invalid;

		private PvpCapture(Path bindingFile, String episodeId, String bindingSha, long started, Map<Integer, PvpParticipant> roster,
			Set<Integer> scored, Map<LearningPolicy, LearningPolicy.Observer> delegates, ItemCapture items)
		{
			_bindingFile = bindingFile;
			_episodeId = episodeId;
			_bindingSha = bindingSha;
			_started = started;
			_roster = Map.copyOf(roster);
			_scored = Set.copyOf(scored);
			require(!_scored.isEmpty() && _scored.size() <= 9 && _roster.keySet().containsAll(_scored)
				&& _roster.size() > _scored.size() && _roster.size() - _scored.size() <= 9, "NATIVE_PVP_ROSTER");
			_instance = _roster.values().iterator().next().player().getInstanceId();
			_maxCasts = MAX_CASTS * Math.max(_scored.size(), _roster.size() - _scored.size());
			_delegates = Map.copyOf(delegates);
			_items = items;
			require(_roster.values().stream().map(PvpParticipant::profile).distinct().count() == _roster.size()
				&& started <= System.nanoTime(), "NATIVE_PVP_OWNERS");
			require(!_delegates.isEmpty() && _delegates.size() <= 2 && _delegates.keySet().stream().allMatch(LearningPolicy::ownedPvpEpisodes),
				"NATIVE_PVP_POLICY");
			final var epochs = new HashMap<Integer, Long>();
			final var listeners = new HashMap<Integer, ConsumerEventListener>();
			_roster.forEach((objectId, member) ->
			{
				require(member.player().getObjectId() == objectId && current(member) && !member.player().isAlikeDead(), "NATIVE_PVP_OWNER");
				epochs.put(objectId, member.epoch());
				listeners.put(objectId, deathListener(member));
			});
			_epochs = Map.copyOf(epochs);
			_deathListeners = Map.copyOf(listeners);
			if (items == null)
			{
				verifyGroupTeam(true);
				verifyGroupTeam(false);
			}
		}

		private void verifyGroupTeam(boolean scored)
		{
			final var team = _roster.entrySet().stream().filter(entry -> _scored.contains(entry.getKey()) == scored).map(Map.Entry::getValue).toList();
			final Party party = team.getFirst().party();
			require(team.size() == 1 ? party == null : party != null && team.stream().allMatch(member -> member.party() == party)
				&& Set.copyOf(party.getMembers()).equals(Set.copyOf(team.stream().map(PvpParticipant::player).toList())), "NATIVE_GROUP_PVP_PARTY");
		}

		private ConsumerEventListener deathListener(PvpParticipant victim)
		{
			return new ConsumerEventListener(victim.player(), EventType.ON_PLAYER_PVP_KILL,
				(OnPlayerPvPKill event) -> stockDeath(event, victim), this);
		}

		private void stockDeath(OnPlayerPvPKill event, PvpParticipant victim)
		{
			final long corpse = victim.player().nativeCorpseGeneration();
			final long now = System.nanoTime();
			synchronized (this)
			{
				if (_closed || _invalid) { return; }
				final int victimId = victim.player().getObjectId();
				final int killerId = event.getPlayer().getObjectId();
				final var killer = _roster.get(killerId);
				if (event.getTarget() != victim.player() || killer == null || event.getPlayer() != killer.player()
					|| !matchesPair(killerId, killer.epoch(), victimId, victim.epoch(), _instance)
					|| !earned(victim.owner()) || !earned(killer.owner())
					|| corpse <= 0 || now < _started || _stockDeaths.putIfAbsent(victimId,
						new DuelDeath(killerId, victimId, victim.epoch(), corpse, now)) != null)
				{
					_invalid = true;
				}
			}
		}

		private static boolean earned(PlayerNativeWork.Owner owner)
		{
			final var work = PlayerNativeWork.current(owner);
			return work != null && work.semantics() == PlayerNativeWork.Semantics.EARNED;
		}

		private boolean current(PvpParticipant participant)
		{
			return participant.current() && participant.player().getInstanceId() == _instance
				&& (_items != null || participant.player().getParty() == participant.party());
		}

		private void claim()
		{
			require(ACTIVE_PVP.compareAndSet(null, this), "NATIVE_PVP_OBSERVER_BUSY");
			try { install(); }
			catch (RuntimeException failure) { close(); throw failure; }
		}

		private void install()
		{
			if (_items != null) { _items.open(() -> ACTIVE_PVP.get() == this); }
			_auditObserver = LearningCastEvidence.observeNativeAudit(this::nativeTrace, this::nativeTerminal);
			_deathListeners.forEach((objectId, listener) ->
			{
				_roster.get(objectId).player().addListener(listener);
				_listening.add(objectId);
			});
			_delegates.forEach((policy, delegate) ->
			{
				policy.observer(new LearningPolicy.Observer()
				{
					@Override public void issued(LearningPolicy.Attempt attempt, Player actor, Monster target) { delegate.issued(attempt, actor, target); }
					@Override public void recorded(LearningPolicy.Attempt attempt, LearningStore.Evidence evidence) { delegate.recorded(attempt, evidence); }
					@Override public void issuedPvp(LearningPolicy.Attempt attempt, Player actor, Player target)
					{
						PvpCapture.this.issuedPvp(policy, attempt, actor, target);
						delegate.issuedPvp(attempt, actor, target);
					}
					@Override public void paidPvp(PvpReceipt receipt)
					{
						PvpCapture.this.paidPvp(receipt);
						delegate.paidPvp(receipt);
					}
				});
				_wrapped.add(policy);
			});
		}

		private synchronized void issuedPvp(LearningPolicy policy, LearningPolicy.Attempt attempt, Player actor, Player target)
		{
			if (_closed || _invalid) { return; }
			final var source = _roster.get(actor.getObjectId());
			final var victim = _roster.get(target.getObjectId());
			final var tactical = attempt.tactical();
			if (source == null || victim == null || actor != source.player() || target != victim.player() || tactical == null
				|| attempt.policy() != policy || attempt.request().profileId() != source.profile() || attempt.generation() <= 0 || _issued.size() >= _maxCasts
				|| !matchesAction(tactical, source, victim) || _issued.putIfAbsent(tactical, attempt.generation()) != null)
			{
				_invalid = true;
			}
		}

		private boolean matchesAction(TacticalReceiptV3.Attempt attempt, PvpParticipant actor, PvpParticipant victim)
		{
			final var observation = attempt.observation();
			final var action = attempt.selected();
			return observation.profileId() == actor.profile() && observation.actorObjectId() == actor.player().getObjectId()
				&& observation.capturedNanos() >= _started
				&& action.family() == LegalActionV3.Family.DAMAGE && action.targetObjectId() == victim.player().getObjectId()
				&& matchesPair(observation.actorObjectId(), observation.actorEpoch(), action.targetObjectId(), action.targetEpoch(), _instance);
		}

		private synchronized void paidPvp(PvpReceipt receipt)
		{
			if (_closed || _invalid) { return; }
			final Long generation = _issued.remove(receipt.attempt());
			final var action = receipt.attempt().selected();
			if (generation == null || generation.longValue() != receipt.owner().generation()
				|| !matchesPair(action.actorObjectId(), action.actorEpoch(), action.targetObjectId(), action.targetEpoch(), receipt.instanceId())
				|| _paid.size() >= _maxCasts || !_operations.add(operationId(_episodeId, receipt.owner()))
				|| _paid.putIfAbsent(receipt.eventKey(), receipt) != null)
			{
				_invalid = true;
			}
		}

		private synchronized void nativeTrace(NativeCastTrace trace)
		{
			if (_closed || _invalid) { return; }
			if (!matchesPair(trace.actorObjectId(), trace.actorEpoch(), trace.targetObjectId(), trace.targetEpoch(), trace.instanceId())
				|| trace.decisionNanos() < _started || _traces.size() >= _maxCasts || _traces.putIfAbsent(trace.eventKey(), trace) != null)
			{
				_invalid = true;
			}
		}

		private synchronized void nativeTerminal(NativeTerminal terminal)
		{
			if (_closed || _invalid) { return; }
			if (!matchesPair(terminal.actorObjectId(), terminal.actorEpoch(), terminal.targetObjectId(), terminal.targetEpoch(), terminal.instanceId())
				|| terminal.corpseGeneration() <= 0 || terminal.killCreditNanos() != 0
				|| terminal.killSequence() != 0 || terminal.damageNanos() < _started
				|| _terminals.size() >= _maxCasts || _terminals.putIfAbsent(terminal.eventKey(), terminal) != null)
			{
				_invalid = true;
			}
		}

		private boolean matchesPair(int actor, long actorEpoch, int target, long targetEpoch, int instance)
		{
			return instance == _instance && opposingOwners(_epochs, _scored, actor, actorEpoch, target, targetEpoch)
				&& current(_roster.get(actor)) && current(_roster.get(target));
		}

		String sealAfterDrain(TacticalEpisodeV3.NativeDrain drain, long combatEnd) throws Exception
		{
			synchronized (this)
			{
				require(!_closed && !_draining && _items != null && ACTIVE_PVP.get() == this, "NATIVE_DUEL_NOT_OPEN");
				_draining = true;
			}
			try
			{
				drain.finish();
				require(_roster.values().stream().allMatch(PvpParticipant::drained), "NATIVE_DUEL_NOT_DRAINED");
				_items.close();
				final long settled = System.nanoTime();
				final DuelEvidence evidence = finished(settled);
				require(evidence.allDeathsLinked() && !evidence.paidDamage().isEmpty(), "INVALID_OR_EMPTY_NATIVE_DUEL");
				final var events = new ArrayList<>(evidence.eventRows());
				final var originals = new ArrayList<>(evidence.originalRows());
				_items.addRows(_episodeId, events, originals);
				return sealClosedDamage(_bindingFile, _episodeId, _bindingSha, true, combatEnd, settled,
					events, originals);
			}
			finally { close(); }
		}

		private synchronized DuelEvidence finished(long settled)
		{
			require(!_closed && !_invalid && ACTIVE_PVP.get() == this && _traces.keySet().equals(_paid.keySet()), "INVALID_OR_UNPAIRED_NATIVE_PVP");
			final var paid = _paid.values().stream().map(receipt -> joinPaid(receipt, settled)).sorted(Comparator.comparingLong(PaidPvpDamage::damageNanos)
				.thenComparing(damage -> damage.original().eventKey())).toList();
			final var deaths = _stockDeaths.values().stream().sorted(Comparator.comparingLong(DuelDeath::observedNanos)
				.thenComparingInt(DuelDeath::victim)).toList();
			final var events = new ArrayList<DuelEvent>();
			final var originalRows = new ArrayList<String>();
			for (var original : paid)
			{
				events.addAll(original.timedEvents());
				originalRows.addAll(original.originalRows());
			}
			final long linkedDeaths = paid.stream().filter(damage -> damage.original().lethal()).count();
			require(linkedDeaths == _terminals.size(), "NATIVE_PVP_ORPHAN_TERMINAL");
			final var dealt = paid.stream().filter(damage -> _scored.contains(damage.original().attempt().selected().actorObjectId())).toList();
			final var received = paid.stream().filter(damage -> _scored.contains(damage.original().attempt().selected().targetObjectId())).toList();
			final var totals = new DuelDamageTotals(dealt.size(), received.size(), total(dealt, PvpReceipt::hp), total(dealt, PvpReceipt::cp),
				total(received, PvpReceipt::hp), total(received, PvpReceipt::cp));
			final long scoredDeaths = _stockDeaths.keySet().stream().filter(_scored::contains).count();
			final boolean scoredOut = scoredDeaths == _scored.size(), opponentOut = deaths.size() - scoredDeaths == _roster.size() - _scored.size();
			final Optional<Outcome> outcome = !scoredOut && !opponentOut ? Optional.empty()
				: Optional.of(scoredOut && opponentOut ? Outcome.DRAW : scoredOut ? Outcome.LOSS : Outcome.WIN);
			return new DuelEvidence(paid, totals, deaths, outcome, linkedDeaths == deaths.size(),
				events.stream().sorted(Comparator.comparingLong(DuelEvent::nanos)).map(DuelEvent::row).toList(), List.copyOf(originalRows));
		}

		private PaidPvpDamage joinPaid(PvpReceipt receipt, long settled)
		{
			final String key = receipt.eventKey();
			final var action = receipt.attempt().selected();
			final NativeCastTrace trace = _traces.get(key);
			final NativeTerminal terminal = _terminals.get(key);
			final DuelDeath stock = terminal == null ? null : _stockDeaths.get(action.targetObjectId());
			final String role = _scored.contains(action.actorObjectId()) ? "DAMAGE" : "OPPONENT";
			final var original = new PaidPvpDamage(_episodeId, _bindingSha, receipt, trace, role, terminal, stock);
			require(PhantomLearning131AuditOriginals.matchesCast(trace, key, receipt.instanceId(), receipt.attempt(),
				PhantomLearning131AuditOriginals.first(receipt.hp(), receipt.cp())), "NATIVE_DUEL_CAST_NOT_JOINED");
			require(original.damageNanos() <= settled && receipt.mpLastWrite() <= settled, "NATIVE_WRITE_OUTSIDE_DUEL_WINDOW");
			require(receipt.lethal() == (terminal != null), "NATIVE_DUEL_TERMINAL_NOT_JOINED");
			if (terminal != null)
			{
				require(terminal.actorObjectId() == action.actorObjectId() && terminal.targetObjectId() == action.targetObjectId()
					&& terminal.damageNanos() == original.damageNanos() && terminal.deathNanos() >= terminal.damageNanos()
					&& PhantomLearning131AuditOriginals.matchesStock(terminal, stock) && stock.observedNanos() <= settled,
					"NATIVE_DUEL_TERMINAL_NOT_JOINED");
			}
			return original;
		}

		private static BigDecimal total(List<PaidPvpDamage> damage, Function<PvpReceipt, PvpPoolWrite> pool)
		{
			return damage.stream().map(PaidPvpDamage::original).map(pool).filter(Objects::nonNull)
				.map(write -> BigDecimal.valueOf(write.delta())).reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		@Override public synchronized void close()
		{
			_closed = true;
			if (ACTIVE_PVP.get() == this)
			{
				try
				{
					if (_items != null) { _items.close(); }
					_listening.forEach(objectId -> _roster.get(objectId).player().removeListener(_deathListeners.get(objectId)));
					_wrapped.forEach(policy -> policy.observer(_delegates.get(policy)));
				}
				finally
				{
					try { closeAuditObserver(_auditObserver); }
					finally { ACTIVE_PVP.compareAndSet(this, null); }
				}
			}
		}
	}

	static final class PaidDamage
	{
		private final String _episodeId;
		private final String _bindingSha;
		private final TimedAction _original;
		private final NativeCastTrace _trace;
		private final NativeTerminal _terminal;

		private PaidDamage(String episodeId, String bindingSha, TimedAction original, NativeCastTrace trace, NativeTerminal terminal)
		{
			_episodeId = episodeId;
			_bindingSha = bindingSha;
			_original = original;
			_trace = trace;
			_terminal = terminal;
		}

		String episodeId() { return _episodeId; }
		String bindingSha() { return _bindingSha; }
		TimedAction original() { return _original; }
		String operationId() { return PhantomLearning131AuditDamage.operationId(_episodeId, _original.row().receipt().owner()); }
		String originalRow() { return PhantomLearning131AuditOriginals.pveRow(_original); }
		long damageNanos() { return _terminal == null ? _original.hpFinishedNanos() : _terminal.damageNanos(); }

		List<String> originalRows()
		{
			final String cast = PhantomLearning131AuditOriginals.castRow(_trace);
			return _terminal == null ? List.of(cast, originalRow()) : List.of(cast, originalRow(), PhantomLearning131AuditOriginals.terminalRow(_terminal));
		}

		List<String> eventRows()
		{
			final var rows = new ArrayList<>(PhantomLearning131AuditOriginals.castEvents(_trace, operationId(), "DAMAGE"));
			rows.add(eventRow());
			if (_terminal != null)
			{
				rows.add(deathEvent(_terminal, operationId(), _terminal.targetEpoch()));
				if (_terminal.killSequence() != 0)
				{
					rows.add(terminalEvent(_terminal, operationId(), "KILL_CREDIT", _terminal.killCreditNanos(),
						PhantomLearning131AuditOriginals.killCreditKey(_terminal.eventKey(), _terminal.killSequence())));
				}
			}
			return List.copyOf(rows);
		}

		/** The DAMAGE projection of the paid original. */
		String eventRow()
		{
			final var receipt = _original.row().receipt();
			final var action = receipt.attempt().selected();
			final String hp = PhantomLearning131AuditOriginals.wireDamage(receipt.actualHp()).toPlainString();
			// The native target in this slice is a Monster. CP damage is inapplicable.
			return String.join("\t", "event", receipt.eventKey(), Long.toString(damageNanos()), "DAMAGE",
				Integer.toString(action.actorObjectId()), Integer.toString(action.targetObjectId()), operationId(), hp, "0");
		}
	}

	private final SoloDamageBinding _binding;
	private final Path _bindingFile;
	private final long _profileId;
	private final Player _actor;
	private final Monster _target;
	private final PlayerNativeWork.Owner _owner;
	private final PlayerNativeEvidence.Target _spawn;
	private final TacticalPolicyV3 _policy;
	private final Consumer<TacticalReceiptV3> _existingPaidObserver;
	private final Map<String, NativeTiming> _timings = new HashMap<>();
	private final Map<String, TacticalReceiptV3> _receipts = new HashMap<>();
	private final Map<String, NativeCastTrace> _traces = new HashMap<>();
	private final Map<String, NativeTerminal> _terminals = new HashMap<>();
	private final ItemCapture _items = new ItemCapture();
	private AutoCloseable _auditObserver;
	private boolean _draining;
	private boolean _closed;
	private boolean _invalid;

	private PhantomLearning131AuditDamage(SoloDamageBinding binding, Path bindingFile, long profileId, Player actor, Monster target,
		TacticalPolicyV3 ownedPolicy, Consumer<TacticalReceiptV3> existingPaidObserver)
	{
		_binding = binding;
		_bindingFile = bindingFile;
		_profileId = profileId;
		_actor = actor;
		_target = target;
		_owner = actor.getNativeWorkOwner();
		_spawn = target.getNativeEvidenceTarget();
		_policy = Objects.requireNonNull(ownedPolicy);
		_existingPaidObserver = Objects.requireNonNull(existingPaidObserver);
		require(profileId > 0 && _owner != null && _owner.player() == actor && _owner.isCurrent() && _owner.epoch() == binding.actorEpoch()
			&& _policy.mode() != TacticalPolicyV3.Mode.OFF
			&& _owner.evidence() != null && _owner.evidence() == PlayerNativeWork.observationEvidence(actor)
			&& actor.getParty() == null && !actor.isAlikeDead() && World.getInstance().findObject(actor.getObjectId()) == actor
			&& World.getInstance().findObject(target.getObjectId()) == target && target.isSpawned() && !target.isAlikeDead()
			&& _spawn.spawnGeneration() == binding.targetEpoch() && _spawn.instanceId() == actor.getInstanceId()
			&& binding.started() <= System.nanoTime(), "NATIVE_OWNER_OR_SPAWN");
	}

	/** Bind every scheduled Party PvE player before HEAL and item observation starts. */
	static GroupHealCapture openGroupHeal(ScheduledArm planned, Path binding, String bindingSha, String nonce, Map<Integer, Player> players) throws IOException
	{
		requireOwnedBoot(nonce);
		require(planned.block().stratum() == Stratum.PARTY_PVE, "NATIVE_PARTY_PVE_STRATUM");
		final var sealed = PhantomLearning131Evaluation.healBinding(planned, binding, bindingSha);
		requireUnusedEpisode(binding, sealed.episodeId());
		return openGroup(new GroupHealCapture(sealed, binding, players, null));
	}

	/** One stock damage/death, heal and item observation scope for the scheduled Group PvP episode. */
	static GroupHealCapture openGroupPvp(ScheduledArm planned, Path binding, String bindingSha, String nonce, Map<Integer, Player> players,
		Map<Integer, Long> profiles, Map<LearningPolicy, LearningPolicy.Observer> ownedObservers) throws IOException
	{
		requireOwnedBoot(nonce);
		require(planned.block().stratum() == Stratum.GROUP_PVP, "NATIVE_GROUP_PVP_STRATUM");
		final var sealed = PhantomLearning131Evaluation.healBinding(planned, binding, bindingSha);
		requireUnusedEpisode(binding, sealed.episodeId());
		final var members = Map.copyOf(players);
		final var profileIds = Map.copyOf(profiles);
		require(members.keySet().equals(sealed.playerEpochs().keySet()) && members.keySet().equals(profileIds.keySet()), "NATIVE_GROUP_PVP_OWNERS");
		final var roster = new HashMap<Integer, PvpParticipant>();
		members.forEach((id, player) -> roster.put(id, new PvpParticipant(profileIds.get(id), player, sealed.playerEpochs().get(id))));
		final var damage = new PvpCapture(binding, sealed.episodeId(), sealed.sha(), sealed.started(), roster, sealed.scoredPlayers(), ownedObservers, null);
		return openGroup(new GroupHealCapture(sealed, binding, members, damage));
	}

	private static GroupHealCapture openGroup(GroupHealCapture capture)
	{
		require(ACTIVE_GROUP.compareAndSet(null, capture), "NATIVE_HEAL_OBSERVER_BUSY");
		try
		{
			capture.install();
			return capture;
		}
		catch (RuntimeException failure)
		{
			capture.close();
			throw failure;
		}
	}

	/** Capture paid native DAMAGE in either direction of the exact planned duel. */
	static PvpCapture openPvp(ScheduledArm planned, Path binding, String bindingSha, String nonce, long scoredProfile, Player scored,
		long opponentProfile, Player opponent, Map<LearningPolicy, LearningPolicy.Observer> ownedObservers) throws IOException
	{
		requireOwnedBoot(nonce);
		final var sealed = PhantomLearning131Evaluation.duelDamageBinding(planned, binding, bindingSha, scored.getObjectId(), opponent.getObjectId());
		requireUnusedEpisode(binding, sealed.episodeId());
		final var roster = Map.of(scored.getObjectId(), new PvpParticipant(scoredProfile, scored, sealed.scoredEpoch()),
			opponent.getObjectId(), new PvpParticipant(opponentProfile, opponent, sealed.opponentEpoch()));
		final var capture = new PvpCapture(binding, sealed.episodeId(), sealed.sha(), sealed.started(), roster,
			Set.of(scored.getObjectId()), ownedObservers, new ItemCapture());
		capture.claim();
		return capture;
	}

	/** Reserve the timing hook in an isolated TEST boot and tee the owned, episode-local paid observer. */
	static PhantomLearning131AuditDamage open(ScheduledArm planned, Path binding, String bindingSha, String nonce, long profileId, Player actor, Monster target,
		TacticalPolicyV3 ownedPolicy, Consumer<TacticalReceiptV3> existingPaidObserver) throws IOException
	{
		requireOwnedBoot(nonce);
		final var sealed = PhantomLearning131Evaluation.soloDamageBinding(planned, binding, bindingSha, actor.getObjectId(), target.getObjectId());
		requireUnusedEpisode(binding, sealed.episodeId());
		final var capture = new PhantomLearning131AuditDamage(sealed, binding, profileId, actor, target, ownedPolicy, existingPaidObserver);
		require(ACTIVE.compareAndSet(null, capture), "NATIVE_TIMING_OBSERVER_BUSY");
		try
		{
			capture._items.open(() -> ACTIVE.get() == capture);
			capture._auditObserver = LearningCastEvidence.observeNativeAudit(capture::nativeTrace, capture::nativeTerminal);
			TerminalRewardReceiptV1.nativeTimingObserver(capture::nativeTiming);
			ownedPolicy.damageObserver(receipt ->
			{
				capture.paidDamage(receipt);
				existingPaidObserver.accept(receipt);
			});
			return capture;
		}
		catch (RuntimeException failure)
		{
			capture.close();
			throw failure;
		}
	}

	private static void requireOwnedBoot(String nonce)
	{
		require(nonce != null && nonce.matches("[a-f0-9]{32}") && nonce.equals(System.getProperty("phantom.learning131.nonce"))
			&& Boolean.getBoolean("phantom.learning.train"), "OWNED_AUDIT_BOOT_REQUIRED");
	}

	private synchronized void nativeTiming(NativeTiming timing)
	{
		if (_closed || _invalid) { return; }
		if (_timings.size() >= MAX_CASTS || _timings.putIfAbsent(timing.eventKey(), timing) != null) { _invalid = true; }
	}

	private synchronized void nativeTerminal(NativeTerminal terminal)
	{
		if (_closed || _invalid) { return; }
		if (_actor.getNativeWorkOwner() != _owner || !_owner.isCurrent() || terminal.actorObjectId() != _actor.getObjectId()
			|| terminal.actorEpoch() != _binding.actorEpoch() || terminal.targetObjectId() != _target.getObjectId()
			|| terminal.targetEpoch() != _spawn.spawnGeneration() || terminal.instanceId() != _spawn.instanceId() || terminal.corpseGeneration() != 0
			|| terminal.damageNanos() < _binding.started() || _terminals.size() >= MAX_CASTS
			|| _terminals.putIfAbsent(terminal.eventKey(), terminal) != null)
		{
			_invalid = true;
		}
	}

	private synchronized void nativeTrace(NativeCastTrace trace)
	{
		if (_closed || _invalid) { return; }
		if (_actor.getNativeWorkOwner() != _owner || !_owner.isCurrent() || trace.actorObjectId() != _actor.getObjectId()
			|| trace.actorEpoch() != _binding.actorEpoch() || trace.targetObjectId() != _target.getObjectId()
			|| trace.targetEpoch() != _spawn.spawnGeneration() || trace.instanceId() != _spawn.instanceId()
			|| trace.decisionNanos() < _binding.started() || _traces.size() >= MAX_CASTS || _traces.putIfAbsent(trace.eventKey(), trace) != null)
		{
			_invalid = true;
		}
	}

	private synchronized void paidDamage(TacticalReceiptV3 receipt)
	{
		if (_closed || _invalid) { return; }
		final var observation = receipt.attempt().observation();
		final var action = receipt.attempt().selected();
		if (observation.profileId() != _profileId || observation.actorObjectId() != _actor.getObjectId() || observation.actorEpoch() != _binding.actorEpoch()
			|| observation.capturedNanos() < _binding.started() || action.family() != LegalActionV3.Family.DAMAGE
			|| action.targetObjectId() != _target.getObjectId() || action.targetEpoch() != _spawn.spawnGeneration()
			|| _actor.getNativeWorkOwner() != _owner || !_owner.isCurrent() || !_spawn.equals(_target.getNativeEvidenceTarget())
			|| _receipts.size() >= MAX_CASTS || _receipts.putIfAbsent(receipt.eventKey(), receipt) != null)
		{
			_invalid = true;
		}
	}

	String sealAfterDrain(TacticalEpisodeV3.NativeDrain drain, long combatEnd) throws Exception
	{
		synchronized (this)
		{
			require(!_closed && !_draining && ACTIVE.get() == this, "NATIVE_CAPTURE_NOT_OPEN");
			_draining = true;
		}
		try
		{
			drain.finish();
			require(!_owner.isCurrent() && PlayerNativeWork.observationEvidence(_actor) == null
				&& World.getInstance().findObject(_actor.getObjectId()) == null, "NATIVE_ACTOR_NOT_DRAINED");
			_items.close();
			final long settled = System.nanoTime();
			final var events = new ArrayList<String>();
			final var originals = new ArrayList<String>();
			for (var damage : finished(settled))
			{
				events.addAll(damage.eventRows());
				originals.addAll(damage.originalRows());
			}
			_items.addRows(_binding.episodeId(), events, originals);
			return sealClosedDamage(_bindingFile, _binding.episodeId(), _binding.sha(), false, combatEnd, settled, events, originals);
		}
		finally { close(); }
	}

	private synchronized List<PaidDamage> finished(long settledNanos)
	{
		require(!_closed && !_invalid && !_receipts.isEmpty() && _timings.keySet().equals(_receipts.keySet())
			&& _traces.keySet().equals(_receipts.keySet()), "UNPAIRED_OR_INCOMPLETE_NATIVE_CAST");
		final var originals = new ArrayList<PaidDamage>();
		final var operations = new HashSet<String>();
		final var credits = new HashSet<Long>();
		int joined = 0;
		for (var receipt : _receipts.values())
		{
			final NativeTiming timing = _timings.get(receipt.eventKey());
			final NativeCastTrace trace = _traces.get(receipt.eventKey());
			final var row = new TacticalStoreV3.Row("AUDIT", "task131|AUDIT|" + _binding.episodeId(), receipt);
			final TimedAction actual = timing.bind(row);
			require(timing.instanceId() == _spawn.instanceId() && timing.hpStart() >= receipt.attempt().observation().capturedNanos()
				&& timing.mpStart() >= receipt.attempt().observation().capturedNanos() && timing.hpEnd() <= settledNanos && timing.mpEnd() <= settledNanos,
				"NATIVE_WRITE_OUTSIDE_ACTION_WINDOW");
			require(PhantomLearning131AuditOriginals.matchesCast(trace, receipt.eventKey(), timing.instanceId(), receipt.attempt(), timing.hpStart()),
				"NATIVE_SOLO_CAST_NOT_JOINED");
			final NativeTerminal terminal = _terminals.get(receipt.eventKey());
			require(!actual.linkedLethalWrite() || terminal != null, "NATIVE_SOLO_TERMINAL_NOT_JOINED");
			if (terminal != null)
			{
				require(receipt.afterHp() < 0.5 && terminal.damageNanos() >= timing.hpStart() && terminal.damageNanos() <= terminal.deathNanos()
					&& terminal.deathNanos() <= timing.hpEnd() && terminal.killCreditNanos() <= settledNanos
					&& (terminal.killSequence() == 0 ? terminal.killCreditNanos() == 0 : terminal.killCreditNanos() >= terminal.deathNanos()
						&& credits.add(terminal.killSequence())), "NATIVE_SOLO_TERMINAL_CLOCK_OR_CREDIT");
				joined++;
			}
			final var captured = new PaidDamage(_binding.episodeId(), _binding.sha(), actual, trace, terminal);
			require(operations.add(captured.operationId()), "NATIVE_OPERATION_REUSED");
			originals.add(captured);
		}
		require(joined == _terminals.size(), "NATIVE_SOLO_ORPHAN_TERMINAL");
		return originals.stream().sorted(Comparator.comparingLong(PaidDamage::damageNanos)
			.thenComparing(damage -> damage.original().row().eventKey())).toList();
	}

	static String operationId(String episodeId, SourceOwner owner)
	{
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_OPERATION_V1\n" + episodeId + "\n" + owner.generation() + "\n" + owner.operationKey())
			.getBytes(StandardCharsets.UTF_8));
	}

	private static Path requireUnusedEpisode(Path binding, String episodeId) throws IOException
	{
		PhantomLearning131Native.taskRoot(binding);
		PhantomLearning131Native.physical(binding, true);
		final Path directory = binding.getParent();
		require(binding.getFileName().toString().equals("binding131.tsv") && directory.getFileName().toString().equals(episodeId)
			&& directory.getParent().getFileName().toString().equals("episodes"), "NATIVE_AUDIT_EPISODE_PATH");
		for (String output : List.of("events131.tsv", "paid131.tsv", "complete131.pending", "complete131.tsv"))
		{
			final Path path = directory.resolve(output);
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { throw new FileAlreadyExistsException(path.toString()); }
		}
		return directory;
	}

	/** Write the completed paid projection once, with its seal published after both originals. */
	static String sealClosedDamage(Path binding, String episodeId, String bindingSha, boolean pvp, long combatEnd, long settled,
		List<String> eventRows, List<String> originalRows) throws IOException
	{
		final Path directory = requireUnusedEpisode(binding, episodeId);
		final var events = eventRows.stream().map(row -> row.split("\t", -1))
			.sorted(Comparator.comparingLong((String[] row) -> Long.parseLong(row[2]))
				.thenComparingInt(row -> paidEventOrder(row[3])).thenComparing(row -> row[1])).toList();
		final var paid = originalRows.stream().map(row -> row.split("\t", -1)).toList();
		final var window = PhantomLearning131Evaluation.verifyClosedDamage(binding, episodeId, bindingSha, pvp, combatEnd, settled, events, paid);
		final String identity = meta("episodeId", episodeId) + meta("bindingSha", bindingSha);
		final byte[] evidence = (meta("schema", "NATIVE_EVENTS131_V1") + identity + rows(events)).getBytes(StandardCharsets.UTF_8);
		final byte[] originals = (meta("schema", "NATIVE_PAID131_V5") + identity + rows(paid)).getBytes(StandardCharsets.UTF_8);
		require(evidence.length <= PhantomLearning131Evaluation.DOCUMENT_MAX_BYTES && originals.length <= PhantomLearning131Evaluation.PAID_MAX_BYTES,
			"EPISODE_ORIGINAL_SIZE");
		final String eventsSha = TacticalObservationV3.hash(evidence), paidSha = TacticalObservationV3.hash(originals);
		final String seal = meta("schema", "NATIVE_EPISODE131_V3") + meta("bindingSha", bindingSha) + meta("eventsSha", eventsSha)
			+ meta("paidOriginalsSha", paidSha) + meta("status", "DRAINED")
			+ meta("combatElapsedNanos", window.combatElapsed()) + meta("settledElapsedNanos", window.settledElapsed());
		Files.write(directory.resolve("events131.tsv"), evidence, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
		Files.write(directory.resolve("paid131.tsv"), originals, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
		PhantomLearning131Evaluation.verifyPersistedDamage(binding, episodeId, bindingSha, pvp, window.combatElapsed(), window.settledElapsed(), eventsSha, paidSha);
		final Path pending = directory.resolve("complete131.pending");
		writeNew(pending, seal);
		Files.move(pending, directory.resolve("complete131.tsv"));
		final String sha = contentSha(seal);
		PhantomLearning131Evaluation.verifySealedDamage(binding, episodeId, sha, pvp);
		return sha;
	}

	private static int paidEventOrder(String kind)
	{
		return switch (kind)
		{
			case "DECISION" -> 0;
			case "ADMITTED" -> 1;
			case "EXECUTED" -> 2;
			case "PAY" -> 3;
			case "DAMAGE", "HEAL" -> 4;
			case "DEATH" -> 5;
			case "KILL_CREDIT" -> 6;
			default -> throw new IllegalArgumentException("TASK131_NATIVE_AUDIT_DAMAGE_KIND");
		};
	}

	private static String rows(List<String[]> rows)
	{
		final var text = new StringBuilder();
		for (String[] row : rows) { text.append(String.join("\t", row)).append('\n'); }
		return text.toString();
	}

	private static String meta(String name, Object value) { return "meta\t" + name + '\t' + value + '\n'; }
	private static String contentSha(String text) { return TacticalObservationV3.hash(text.getBytes(StandardCharsets.UTF_8)); }
	private static void writeNew(Path path, String text) throws IOException
	{
		Files.writeString(path, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
	}

	private static String deathEvent(NativeTerminal terminal, String operationId, long generation)
	{
		return terminalEvent(terminal, operationId, "DEATH", terminal.deathNanos(),
			PhantomLearning131AuditOriginals.deathKey(terminal.eventKey(), generation));
	}

	private static String terminalEvent(NativeTerminal terminal, String operationId, String kind, long nanos, String eventKey)
	{
		return String.join("\t", "event", eventKey, Long.toString(nanos), kind, Integer.toString(terminal.actorObjectId()),
			Integer.toString(terminal.targetObjectId()), operationId);
	}

	private static List<DuelEvent> castTimedEvents(NativeCastTrace trace, String operationId, String role)
	{
		final var rows = PhantomLearning131AuditOriginals.castEvents(trace, operationId, role);
		return List.of(new DuelEvent(trace.decisionNanos(), rows.get(0)), new DuelEvent(trace.admittedNanos(), rows.get(1)),
			new DuelEvent(trace.executedNanos(), rows.get(2)));
	}

	@Override public synchronized void close()
	{
		_closed = true;
		if (ACTIVE.get() == this)
		{
			_items.close();
			_policy.damageObserver(_existingPaidObserver);
			TerminalRewardReceiptV1.nativeTimingObserver(_ -> { });
			try { closeAuditObserver(_auditObserver); }
			finally { ACTIVE.compareAndSet(this, null); }
		}
	}

	private static void closeAuditObserver(AutoCloseable observer)
	{
		if (observer == null) { return; }
		try { observer.close(); }
		catch (Exception failure) { throw new IllegalStateException("AUDIT_DAMAGE131_CAST_OBSERVER_CLOSE", failure); }
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw new IllegalStateException("AUDIT_DAMAGE131_" + reason); }
	}
}
