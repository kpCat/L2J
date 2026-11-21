/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
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
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.DuelDamageBinding;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.ScheduledArm;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.SoloDamageBinding;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;

/** Joins paid stock cast originals and native writes for an owned Solo PvE or 1x1 PvP AUDIT fragment. */
final class PhantomLearning131AuditDamage implements AutoCloseable
{
	private static final int MAX_CASTS = 64;
	private static final AtomicReference<PhantomLearning131AuditDamage> ACTIVE = new AtomicReference<>();
	private static final AtomicReference<PvpCapture> ACTIVE_DUEL = new AtomicReference<>();

	record DuelDeath(int killer, int victim, long victimEpoch, long corpseGeneration, long observedNanos) { }
	record DuelDamageTotals(int paidDealt, int paidReceived, BigDecimal hpDealt, BigDecimal cpDealt, BigDecimal hpReceived, BigDecimal cpReceived) { }
	/** Captured paid damage totals, stock deaths and any death rows with exact action attribution. */
	record DuelEvidence(List<PaidPvpDamage> paidDamage, DuelDamageTotals damageTotals, List<DuelDeath> nativeDeaths,
		Optional<Outcome> nativeDeathOutcome, boolean allDeathsLinked, List<String> eventRows, List<String> originalRows) { }
	private record DuelEvent(long observedNanos, String row) { }

	static long joinedDeathNanos(DuelDeath stock, DuelDeath paid, long damageNanos)
	{
		require(stock.killer() == paid.killer() && stock.victim() == paid.victim() && stock.victimEpoch() == paid.victimEpoch()
			&& stock.corpseGeneration() > 0 && stock.corpseGeneration() == paid.corpseGeneration()
			&& stock.observedNanos() >= damageNanos && paid.observedNanos() >= damageNanos, "UNBOUND_NATIVE_DEATH");
		return Math.max(stock.observedNanos(), paid.observedNanos());
	}

	/** The original preserves separate nullable pools and exact MP. The row projects only DAMAGE. */
	static final class PaidPvpDamage
	{
		private final String _episodeId;
		private final String _bindingSha;
		private final PvpReceipt _original;

		private PaidPvpDamage(String episodeId, String bindingSha, PvpReceipt original)
		{
			_episodeId = episodeId;
			_bindingSha = bindingSha;
			_original = original;
		}

		String episodeId() { return _episodeId; }
		String bindingSha() { return _bindingSha; }
		PvpReceipt original() { return _original; }
		String operationId() { return PhantomLearning131AuditDamage.operationId(_episodeId, _original.owner()); }
		String originalRow() { return PhantomLearning131AuditOriginals.pvpRow(_original); }

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

	private record DuelParticipant(long profile, Player player, long epoch, PlayerNativeWork.Owner owner)
	{
		boolean current()
		{
			return profile > 0 && owner != null && player.getNativeWorkOwner() == owner && owner.player() == player && owner.epoch() == epoch && owner.isCurrent()
				&& owner.evidence() != null && owner.evidence() == PlayerNativeWork.observationEvidence(player)
				&& World.getInstance().findObject(player.getObjectId()) == player;
		}

		boolean drained()
		{
			return !owner.isCurrent() && PlayerNativeWork.observationEvidence(player) == null && World.getInstance().findObject(player.getObjectId()) == null;
		}
	}

	static final class PvpCapture implements AutoCloseable
	{
		private final DuelDamageBinding _binding;
		private final DuelParticipant _scored;
		private final DuelParticipant _opponent;
		private final int _instance;
		private final Map<LearningPolicy, LearningPolicy.Observer> _delegates;
		private final Map<TacticalReceiptV3.Attempt, Long> _issued = new IdentityHashMap<>();
		private final Map<String, PaidPvpDamage> _paid = new HashMap<>();
		private final Map<Integer, DuelDeath> _stockDeaths = new HashMap<>();
		private final Map<Integer, DuelDeath> _paidDeaths = new HashMap<>();
		private final HashSet<String> _operations = new HashSet<>();
		private final ConsumerEventListener _scoredDeathListener;
		private final ConsumerEventListener _opponentDeathListener;
		private boolean _draining;
		private boolean _closed;
		private boolean _invalid;

		private PvpCapture(DuelDamageBinding binding, DuelParticipant scored, DuelParticipant opponent,
			Map<LearningPolicy, LearningPolicy.Observer> delegates)
		{
			_binding = binding;
			_scored = scored;
			_opponent = opponent;
			_instance = scored.player().getInstanceId();
			_delegates = Map.copyOf(delegates);
			require(scored.profile() != opponent.profile() && bothCurrent() && !scored.player().isAlikeDead() && !opponent.player().isAlikeDead()
				&& binding.started() <= System.nanoTime(), "NATIVE_DUEL_OWNERS");
			require(!_delegates.isEmpty() && _delegates.size() <= 2 && _delegates.keySet().stream().allMatch(LearningPolicy::ownedPvpEpisodes),
				"NATIVE_DUEL_POLICY");
			_scoredDeathListener = deathListener(scored, opponent);
			_opponentDeathListener = deathListener(opponent, scored);
		}

		private ConsumerEventListener deathListener(DuelParticipant victim, DuelParticipant killer)
		{
			return new ConsumerEventListener(victim.player(), EventType.ON_PLAYER_PVP_KILL,
				(OnPlayerPvPKill event) -> stockDeath(event, victim, killer), this);
		}

		private void stockDeath(OnPlayerPvPKill event, DuelParticipant victim, DuelParticipant killer)
		{
			final long corpse = victim.player().nativeCorpseGeneration();
			final long now = System.nanoTime();
			synchronized (this)
			{
				if (_closed || _invalid) { return; }
				final int victimId = victim.player().getObjectId();
				if (event.getTarget() != victim.player() || event.getPlayer() != killer.player() || !bothCurrent()
					|| !earned(victim.owner()) || !earned(killer.owner())
					|| corpse <= 0 || now < _binding.started() || _stockDeaths.putIfAbsent(victimId,
						new DuelDeath(killer.player().getObjectId(), victimId, victim.epoch(), corpse, now)) != null)
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

		private boolean bothCurrent()
		{
			return _scored.current() && _opponent.current() && _scored.player().getInstanceId() == _instance
				&& _opponent.player().getInstanceId() == _instance;
		}

		private void install()
		{
			_scored.player().addListener(_scoredDeathListener);
			_opponent.player().addListener(_opponentDeathListener);
			_delegates.forEach((policy, delegate) -> policy.observer(new LearningPolicy.Observer()
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
			}));
		}

		private synchronized void issuedPvp(LearningPolicy policy, LearningPolicy.Attempt attempt, Player actor, Player target)
		{
			if (_closed || _invalid) { return; }
			final boolean scored = actor == _scored.player();
			final DuelParticipant source = scored ? _scored : _opponent, victim = scored ? _opponent : _scored;
			final var tactical = attempt.tactical();
			if (tactical == null || attempt.policy() != policy || attempt.request().profileId() != source.profile() || attempt.generation() <= 0
				|| actor != source.player() || target != victim.player() || !bothCurrent() || _issued.size() >= MAX_CASTS
				|| !matchesDuelAction(tactical, source, victim) || _issued.putIfAbsent(tactical, attempt.generation()) != null)
			{
				_invalid = true;
			}
		}

		private boolean matchesDuelAction(TacticalReceiptV3.Attempt attempt, DuelParticipant actor, DuelParticipant victim)
		{
			final var observation = attempt.observation();
			final var action = attempt.selected();
			return observation.profileId() == actor.profile() && observation.actorObjectId() == actor.player().getObjectId()
				&& observation.actorEpoch() == actor.epoch() && observation.capturedNanos() >= _binding.started()
				&& action.family() == LegalActionV3.Family.DAMAGE && action.targetObjectId() == victim.player().getObjectId()
				&& action.targetEpoch() == victim.epoch();
		}

		private void paidPvp(PvpReceipt receipt)
		{
			final var action = receipt.attempt().selected();
			final DuelParticipant victim = action.targetObjectId() == _scored.player().getObjectId() ? _scored : _opponent;
			final long corpse = receipt.lethal() ? victim.player().nativeCorpseGeneration() : 0;
			final long now = System.nanoTime();
			synchronized (this)
			{
				if (_closed || _invalid) { return; }
				final Long generation = _issued.remove(receipt.attempt());
				final var original = new PaidPvpDamage(_binding.episodeId(), _binding.sha(), receipt);
				if (generation == null || generation.longValue() != receipt.owner().generation() || !bothCurrent() || receipt.instanceId() != _instance
					|| _paid.size() >= MAX_CASTS || !_operations.add(original.operationId()) || _paid.putIfAbsent(receipt.eventKey(), original) != null
					|| (receipt.lethal() && (corpse <= 0 || _paidDeaths.putIfAbsent(action.targetObjectId(),
						new DuelDeath(action.actorObjectId(), action.targetObjectId(), victim.epoch(), corpse, now)) != null)))
				{
					_invalid = true;
				}
			}
		}

		List<PaidPvpDamage> afterDrain(TacticalEpisodeV3.NativeDrain drain) throws Exception
		{
			final var evidence = afterDrainEvidence(drain);
			require(!evidence.paidDamage().isEmpty(), "INVALID_OR_EMPTY_NATIVE_DUEL");
			return evidence.paidDamage();
		}

		DuelEvidence afterDrainEvidence(TacticalEpisodeV3.NativeDrain drain) throws Exception
		{
			synchronized (this)
			{
				require(!_closed && !_draining && ACTIVE_DUEL.get() == this, "NATIVE_DUEL_NOT_OPEN");
				_draining = true;
			}
			try
			{
				drain.finish();
				require(_scored.drained() && _opponent.drained(), "NATIVE_DUEL_NOT_DRAINED");
				return finished(System.nanoTime());
			}
			finally { close(); }
		}

		private synchronized DuelEvidence finished(long settled)
		{
			require(!_closed && !_invalid, "INVALID_OR_EMPTY_NATIVE_DUEL");
			final var paid = _paid.values().stream().sorted(Comparator.comparingLong(PaidPvpDamage::damageNanos)
				.thenComparing(damage -> damage.original().eventKey())).toList();
			final var deaths = _stockDeaths.values().stream().sorted(Comparator.comparingLong(DuelDeath::observedNanos)
				.thenComparingInt(DuelDeath::victim)).toList();
			final var events = new ArrayList<DuelEvent>();
			final var originalRows = new ArrayList<String>();
			int linkedDeaths = 0;
			for (var original : paid)
			{
				require(original.damageNanos() <= settled && original.original().mpLastWrite() <= settled, "NATIVE_WRITE_OUTSIDE_DUEL_WINDOW");
				events.add(new DuelEvent(original.damageNanos(), original.eventRow()));
				originalRows.add(original.originalRow());
				if (original.original().lethal())
				{
					final int victim = original.original().attempt().selected().targetObjectId();
					final DuelDeath stock = _stockDeaths.get(victim);
					if (stock != null)
					{
						final DuelDeath paidDeath = _paidDeaths.get(victim);
						final long stamp = joinedDeathNanos(stock, paidDeath, original.damageNanos());
						require(stamp <= settled, "NATIVE_WRITE_OUTSIDE_DUEL_WINDOW");
						final String eventKey = PhantomLearning131AuditOriginals.deathKey(original.original().eventKey(), stock.corpseGeneration());
						events.add(new DuelEvent(stamp, String.join("\t", "event", eventKey, Long.toString(stamp), "DEATH",
							Integer.toString(stock.killer()), Integer.toString(stock.victim()), original.operationId())));
						originalRows.add(PhantomLearning131AuditOriginals.deathRow(original.original().eventKey(), stock, paidDeath));
						linkedDeaths++;
					}
				}
			}
			final int scoredId = _scored.player().getObjectId();
			final var dealt = paid.stream().filter(damage -> damage.original().attempt().selected().actorObjectId() == scoredId).toList();
			final var received = paid.stream().filter(damage -> damage.original().attempt().selected().targetObjectId() == scoredId).toList();
			final var totals = new DuelDamageTotals(dealt.size(), received.size(), total(dealt, PvpReceipt::hp), total(dealt, PvpReceipt::cp),
				total(received, PvpReceipt::hp), total(received, PvpReceipt::cp));
			final Optional<Outcome> outcome = deaths.isEmpty() ? Optional.empty()
				: Optional.of(deaths.size() == 2 ? Outcome.DRAW : _stockDeaths.containsKey(scoredId) ? Outcome.LOSS : Outcome.WIN);
			return new DuelEvidence(paid, totals, deaths, outcome, linkedDeaths == deaths.size() && linkedDeaths == _paidDeaths.size(),
				events.stream().sorted(Comparator.comparingLong(DuelEvent::observedNanos)).map(DuelEvent::row).toList(), List.copyOf(originalRows));
		}

		private static BigDecimal total(List<PaidPvpDamage> damage, Function<PvpReceipt, PvpPoolWrite> pool)
		{
			return damage.stream().map(PaidPvpDamage::original).map(pool).filter(Objects::nonNull)
				.map(write -> BigDecimal.valueOf(write.delta())).reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		@Override public synchronized void close()
		{
			_closed = true;
			if (ACTIVE_DUEL.get() == this)
			{
				_scored.player().removeListener(_scoredDeathListener);
				_opponent.player().removeListener(_opponentDeathListener);
				_delegates.forEach(LearningPolicy::observer);
				ACTIVE_DUEL.compareAndSet(this, null);
			}
		}
	}

	static final class PaidDamage
	{
		private final String _episodeId;
		private final String _bindingSha;
		private final TimedAction _original;

		private PaidDamage(String episodeId, String bindingSha, TimedAction original)
		{
			_episodeId = episodeId;
			_bindingSha = bindingSha;
			_original = original;
		}

		String episodeId() { return _episodeId; }
		String bindingSha() { return _bindingSha; }
		TimedAction original() { return _original; }
		String operationId() { return PhantomLearning131AuditDamage.operationId(_episodeId, _original.row().receipt().owner()); }
		String originalRow() { return PhantomLearning131AuditOriginals.pveRow(_original); }

		/** A fragment only. A complete AUDIT needs separately observed decision, admission and execution rows. */
		String eventRow()
		{
			final var receipt = _original.row().receipt();
			final var action = receipt.attempt().selected();
			final String hp = PhantomLearning131AuditOriginals.wireDamage(receipt.actualHp()).toPlainString();
			// The native target in this slice is a Monster. CP damage is inapplicable.
			return String.join("\t", "event", receipt.eventKey(), Long.toString(_original.hpFinishedNanos()), "DAMAGE",
				Integer.toString(action.actorObjectId()), Integer.toString(action.targetObjectId()), operationId(), hp, "0");
		}
	}

	private final SoloDamageBinding _binding;
	private final long _profileId;
	private final Player _actor;
	private final Monster _target;
	private final PlayerNativeWork.Owner _owner;
	private final PlayerNativeEvidence.Target _spawn;
	private final TacticalPolicyV3 _policy;
	private final Consumer<TacticalReceiptV3> _existingPaidObserver;
	private final Map<String, NativeTiming> _timings = new HashMap<>();
	private final Map<String, TacticalReceiptV3> _receipts = new HashMap<>();
	private boolean _draining;
	private boolean _closed;
	private boolean _invalid;

	private PhantomLearning131AuditDamage(SoloDamageBinding binding, long profileId, Player actor, Monster target,
		TacticalPolicyV3 ownedPolicy, Consumer<TacticalReceiptV3> existingPaidObserver)
	{
		_binding = binding;
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

	/** Capture paid native DAMAGE in either direction of the exact planned duel. */
	static PvpCapture openPvp(ScheduledArm planned, Path binding, String bindingSha, String nonce, long scoredProfile, Player scored,
		long opponentProfile, Player opponent, Map<LearningPolicy, LearningPolicy.Observer> ownedObservers) throws IOException
	{
		requireOwnedBoot(nonce);
		final var sealed = PhantomLearning131Evaluation.duelDamageBinding(planned, binding, bindingSha, scored.getObjectId(), opponent.getObjectId());
		final var capture = new PvpCapture(sealed,
			new DuelParticipant(scoredProfile, scored, sealed.scoredEpoch(), scored.getNativeWorkOwner()),
			new DuelParticipant(opponentProfile, opponent, sealed.opponentEpoch(), opponent.getNativeWorkOwner()), ownedObservers);
		require(ACTIVE_DUEL.compareAndSet(null, capture), "NATIVE_DUEL_OBSERVER_BUSY");
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

	/** Reserve the timing hook in an isolated TEST boot and tee the owned, episode-local paid observer. */
	static PhantomLearning131AuditDamage open(ScheduledArm planned, Path binding, String bindingSha, String nonce, long profileId, Player actor, Monster target,
		TacticalPolicyV3 ownedPolicy, Consumer<TacticalReceiptV3> existingPaidObserver) throws IOException
	{
		requireOwnedBoot(nonce);
		final var sealed = PhantomLearning131Evaluation.soloDamageBinding(planned, binding, bindingSha, actor.getObjectId(), target.getObjectId());
		final var capture = new PhantomLearning131AuditDamage(sealed, profileId, actor, target, ownedPolicy, existingPaidObserver);
		require(ACTIVE.compareAndSet(null, capture), "NATIVE_TIMING_OBSERVER_BUSY");
		TerminalRewardReceiptV1.nativeTimingObserver(capture::nativeTiming);
		ownedPolicy.damageObserver(receipt ->
		{
			capture.paidDamage(receipt);
			existingPaidObserver.accept(receipt);
		});
		return capture;
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

	List<PaidDamage> afterDrain(TacticalEpisodeV3.NativeDrain drain) throws Exception
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
			return finished(System.nanoTime());
		}
		finally { close(); }
	}

	private synchronized List<PaidDamage> finished(long settledNanos)
	{
		require(!_closed && !_invalid && !_receipts.isEmpty() && _timings.keySet().equals(_receipts.keySet()), "UNPAIRED_OR_INCOMPLETE_NATIVE_CAST");
		final var originals = new ArrayList<PaidDamage>();
		final var operations = new HashSet<String>();
		for (var receipt : _receipts.values())
		{
			final NativeTiming timing = _timings.get(receipt.eventKey());
			final var row = new TacticalStoreV3.Row("AUDIT", "task131|AUDIT|" + _binding.episodeId(), receipt);
			final TimedAction actual = timing.bind(row);
			require(timing.instanceId() == _spawn.instanceId() && timing.hpStart() >= receipt.attempt().observation().capturedNanos()
				&& timing.mpStart() >= receipt.attempt().observation().capturedNanos() && timing.hpEnd() <= settledNanos && timing.mpEnd() <= settledNanos,
				"NATIVE_WRITE_OUTSIDE_ACTION_WINDOW");
			final var captured = new PaidDamage(_binding.episodeId(), _binding.sha(), actual);
			require(operations.add(captured.operationId()), "NATIVE_OPERATION_REUSED");
			originals.add(captured);
		}
		return originals.stream().sorted(Comparator.comparingLong((PaidDamage damage) -> damage.original().hpFinishedNanos())
			.thenComparing(damage -> damage.original().row().eventKey())).toList();
	}

	static String operationId(String episodeId, SourceOwner owner)
	{
		return TacticalObservationV3.hash(("TASK131_AUDIT_NATIVE_OPERATION_V1\n" + episodeId + "\n" + owner.generation() + "\n" + owner.operationKey())
			.getBytes(StandardCharsets.UTF_8));
	}

	@Override public synchronized void close()
	{
		_closed = true;
		if (ACTIVE.get() == this)
		{
			_policy.damageObserver(_existingPaidObserver);
			TerminalRewardReceiptV1.nativeTimingObserver(_ -> { });
			ACTIVE.compareAndSet(this, null);
		}
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw new IllegalStateException("AUDIT_DAMAGE131_" + reason); }
	}
}
