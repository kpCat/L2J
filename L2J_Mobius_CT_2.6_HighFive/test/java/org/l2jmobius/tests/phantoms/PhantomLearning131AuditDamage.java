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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.NativeTiming;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1.TimedAction;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.ScheduledArm;
import org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation.SoloDamageBinding;

/** Joins exact native HP writes to paid stock cast originals for one owned Solo PvE AUDIT target. */
final class PhantomLearning131AuditDamage implements AutoCloseable
{
	private static final int MAX_CASTS = 64;
	private static final AtomicReference<PhantomLearning131AuditDamage> ACTIVE = new AtomicReference<>();

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

		/** A fragment only. A complete AUDIT needs separately observed decision, admission and execution rows. */
		String eventRow()
		{
			final var receipt = _original.row().receipt();
			final var action = receipt.attempt().selected();
			final String hp = BigDecimal.valueOf(receipt.actualHp()).stripTrailingZeros().toPlainString();
			require(hp.matches("[0-9]{1,18}(\\.[0-9]{1,9})?"), "HP_OUTSIDE_WIRE_PRECISION");
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

	/** Reserve the timing hook in an isolated TEST boot and tee the owned, episode-local paid observer. */
	static PhantomLearning131AuditDamage open(ScheduledArm planned, Path binding, String bindingSha, String nonce, long profileId, Player actor, Monster target,
		TacticalPolicyV3 ownedPolicy, Consumer<TacticalReceiptV3> existingPaidObserver) throws IOException
	{
		require(nonce != null && nonce.matches("[a-f0-9]{32}") && nonce.equals(System.getProperty("phantom.learning131.nonce"))
			&& Boolean.getBoolean("phantom.learning.train"), "OWNED_AUDIT_BOOT_REQUIRED");
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
