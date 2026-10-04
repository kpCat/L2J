import java.util.Map;

import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;

/** Pure TEST contract. This does not bootstrap a server or prove native writer integration. */
public final class M1NativeEvidenceContract
{
	private static final long EPOCH = 100;
	private static final long SECOND = 1_000_000_000L;
	private static final Target A = new Target(701, 0, 1);
	private static final Target B = new Target(702, 0, 1);
	private static final Target C = new Target(703, 0, 1);

	public static void main(String[] args)
	{
		testCyclesAndRewardContext();
		testPhaseDebtAndLoot();
		testBoundAndOverflow();
		System.out.println("M1_NATIVE_EVIDENCE_CONTRACT_PASS groups=3 nativeWriter=REQUIRED");
	}

	private static void testCyclesAndRewardContext()
	{
		final var evidence = new PlayerNativeEvidence(600, EPOCH);
		evidence.selected(A, 101);
		evidence.damage(A, 20, 102);
		evidence.reward(A, 8, 3, 103); // Stock calculateRewards runs before Attackable.doDie returns.
		equal(0, evidence.snapshot(104).farmCycleSequence(), "Reward alone completed a cycle.");
		evidence.killed(A, 105);
		evidence.killed(A, 106);
		evidence.selected(B, 107);
		equal(1, evidence.snapshot(108).farmCycleSequence(), "Reward-before-kill lost a cycle.");
		evidence.damage(B, 10, 109);
		evidence.killed(B, 110);
		evidence.reward(B, 9, 4, 111);
		final Target respawned = new Target(702, 0, 2);
		evidence.selected(respawned, 112);
		final var done = evidence.snapshot(113);
		equal(2, done.farmCycleSequence(), "A distinct spawn lifetime did not complete the second cycle.");
		equal(2, done.killSequence(), "A duplicate kill inflated the counter.");
		equal(17, done.expGained(), "Actual EXP gain was lost.");
		equal(7, done.spGained(), "Actual SP gain was lost.");
		check(evidence.matches(600, EPOCH) && !evidence.matches(601, EPOCH) && !evidence.matches(600, EPOCH + 1), "Actor/epoch match was weakened.");
		final Map<String, String> fields = done.scalarMap();
		equal(19, fields.size(), "Observer field contract drifted.");
		check("1".equals(fields.get("nativeEvidenceVersion")) && "PHANTOM".equals(fields.get("nativeEvidenceOwner")), "Version or observed owner drifted.");
		check("600".equals(fields.get("nativeEvidenceObjectId")) && "100".equals(fields.get("nativeEvidenceEpoch")), "Snapshot attribution drifted.");

		final var foreign = new PlayerNativeEvidence(600, EPOCH);
		foreign.selected(A, 101);
		foreign.damage(A, 5, 102);
		foreign.reward(B, 5, 2, 103);
		foreign.killed(A, 104);
		foreign.selected(B, 105);
		equal(0, foreign.snapshot(106).farmCycleSequence(), "A different target supplied the reward.");

		final var area = new PlayerNativeEvidence(600, EPOCH);
		area.selected(A, 101);
		long now = 102;
		for (Target target : new Target[] { A, B })
		{
			area.damage(target, 5, now++);
			area.reward(target, 5, 2, now++);
			area.killed(target, now++);
		}
		area.selected(C, now++);
		final var areaDone = area.snapshot(now);
		check(!areaDone.overflow(), "Area fixture attribution was invalid.");
		equal(1, areaDone.farmCycleSequence(), "One next target inflated multiple area kills into cycles.");

		check(PlayerNativeEvidence.currentRewardTarget() == null, "Reward context leaked before entry.");
		try (var first = PlayerNativeEvidence.enterReward(A))
		{
			check(A.equals(PlayerNativeEvidence.currentRewardTarget()), "Reward target missing.");
			try (var second = PlayerNativeEvidence.enterReward(B))
			{
				check(B.equals(PlayerNativeEvidence.currentRewardTarget()), "Nested reward target missing.");
			}
			check(A.equals(PlayerNativeEvidence.currentRewardTarget()), "Previous reward target was not restored.");
		}
		check(PlayerNativeEvidence.currentRewardTarget() == null, "Reward context survived close.");
	}

	private static void testPhaseDebtAndLoot()
	{
		final var evidence = new PlayerNativeEvidence(600, EPOCH);
		evidence.damage(A, 5, 101);
		evidence.phase(Phase.ROUTE, 102, 100 * SECOND);
		evidence.selected(B, 103);
		evidence.phase(Phase.ROUTE, 104, 110 * SECOND);
		final var waiting = evidence.snapshot(90 * SECOND);
		equal(101, waiting.usefulProgressNanos(), "Selection or phase erased useful progress debt.");
		equal(103, waiting.lastProgressNanos(), "Real next-target event did not receive a native event timestamp.");
		equal(102, waiting.phaseSinceNanos(), "Same phase renewed its start.");
		equal(100 * SECOND, waiting.phaseDeadlineNanos(), "Same phase extended its deadline.");
		evidence.phase(Phase.NONE, 0, 0);
		evidence.loot(900, 57, 0, 90 * SECOND + 1);
		equal(0, evidence.snapshot(90 * SECOND + 2).lootSequence(), "An empty pickup supplied loot evidence.");
		evidence.loot(900, 57, 3, 90 * SECOND + 3);
		final var loot = evidence.snapshot(90 * SECOND + 4);
		equal(1, loot.lootSequence(), "Successful native inventory receipt was lost.");
		equal(3, loot.lootCount(), "Actual retained loot count was lost.");
		equal(90 * SECOND + 3, loot.usefulProgressNanos(), "Legal native loot failed to advance useful progress.");
		equal(0, loot.phaseSinceNanos(), "NONE retained phase stamps.");

		final var invalid = new PlayerNativeEvidence(600, EPOCH);
		invalid.phase(Phase.REGEN, EPOCH, EPOCH + 121 * SECOND);
		check(invalid.snapshot(EPOCH + 1).overflow(), "Unbounded phase remained proven.");
		final var attempts = new PlayerNativeEvidence(600, EPOCH);
		attempts.damage(A, 0, 101);
		attempts.reward(A, 0, 0, 102);
		equal(0, attempts.snapshot(103).sequence(), "Attempted cast or zero reward manufactured progress.");
	}

	private static void testBoundAndOverflow()
	{
		final var bounded = new PlayerNativeEvidence(600, EPOCH);
		for (int index = 0; index <= PlayerNativeEvidence.MAX_TARGETS; index++)
		{
			bounded.damage(new Target(1000 + index, 0, 1), 1, EPOCH + index + 1);
		}
		final var limit = bounded.snapshot(EPOCH + 30);
		check(limit.overflow(), "Target ledger cap silently discarded attribution.");
		equal(PlayerNativeEvidence.MAX_TARGETS, limit.trackedTargets(), "Target ledger exceeded its bound.");
		final var saturated = new PlayerNativeEvidence(600, EPOCH);
		saturated.reward(A, Long.MAX_VALUE, 1, 101);
		saturated.reward(A, 1, 1, 102);
		final var overflow = saturated.snapshot(103);
		check(overflow.overflow(), "Counter overflow remained proven.");
		equal(Long.MAX_VALUE, overflow.expGained(), "Counter wrapped instead of saturating.");
		final var stale = new PlayerNativeEvidence(600, EPOCH);
		stale.snapshot(110);
		stale.damage(A, 1, 109);
		check(stale.snapshot(111).overflow(), "A pre-sample event received fresh evidence.");
	}

	private static void equal(long expected, long actual, String reason)
	{
		check(expected == actual, reason + " expected=" + expected + " actual=" + actual);
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw new AssertionError(reason); }
	}
}
