/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms;

import java.util.Arrays;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Capture;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Eligibility;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Focused scalar contracts; root invokes these through the existing TEST suite. */
public final class PhantomM1NativeContextChecks
{
	private PhantomM1NativeContextChecks() {}

	public static void unknownAndExactBinding()
	{
		final var identity = new Identity(7, 100007, 0, 0, 0);
		final byte[] state = { 1, 2, 3 };
		final var unknown = PhantomNativeContext.completed(identity, 1, Eligibility.UNKNOWN, 4, state);
		PhantomAssertions.assertFalse(unknown.simulationEligible(), "Missing legacy vitality evidence must require native work.");
		final var supported = PhantomNativeContext.completed(identity, 1, Eligibility.SUPPORTED, 4, state);
		PhantomAssertions.assertTrue(supported.simulationEligible() && supported.binds(identity, 4, state), "Explicit fresh minimum-points proof should bind.");
		PhantomAssertions.assertFalse(supported.binds(identity, 5, state), "Stale B4 version must not bind.");
		PhantomAssertions.assertFalse(supported.binds(new Identity(8, 100007, 0, 0, 0), 4, state), "Wrong profile must not bind.");
		PhantomAssertions.assertFalse(supported.binds(identity, 4, new byte[] { 1, 2, 4 }), "Wrong B4 payload must not bind.");
		PhantomAssertions.assertEquals(supported, PhantomNativeContext.decode(supported.encode()), "Bounded scalar codec must preserve exact proof.");
		final byte[] trailing = Arrays.copyOf(supported.encode(), supported.encode().length + 1);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomNativeContext.decode(trailing), "Trailing scalar bytes must fail closed.");
	}

	public static void pendingZeroAndNativePoints()
	{
		final var identity = new Identity(7, 100007, 0, 0, 0);
		final byte[] state = { 1, 2, 3 };
		final byte[] receipt = { 4, 5, 6 };
		final var pending = PhantomNativeContext.pending(identity, 0, Eligibility.UNKNOWN, new Capture(1, Eligibility.SUPPORTED), 4, state, 99, receipt);
		PhantomAssertions.assertFalse(pending.simulationEligible(), "PENDING must never admit simulation.");
		PhantomAssertions.assertTrue(pending.matchesReceipt(4, 99, receipt), "Pending exact receipt/epoch must bind.");
		PhantomAssertions.assertFalse(pending.matchesReceipt(4, 100, receipt), "Stale owner epoch must fail.");
		PhantomAssertions.assertFalse(pending.matchesReceipt(5, 99, receipt), "Wrong prepared B4 version must fail.");
		PhantomAssertions.assertFalse(pending.matchesReceipt(4, 99, new byte[] { 4, 5, 7 }), "Different original receipt bytes must fail.");
		PhantomAssertions.assertEquals(pending, PhantomNativeContext.decode(pending.encode()), "Pending codec must retain both exact witnesses and receipt binding.");
		PhantomAssertions.assertTrue(pending.matchesBefore(0) && !pending.matchesBefore(1), "Legacy canonical zero must remain an exact BEFORE witness.");
		PhantomAssertions.assertTrue(pending.matchesAfter(1) && !pending.matchesAfter(0), "Stock native normalization one must remain the separate AFTER witness.");
		PhantomAssertions.assertFalse(pending.complete(false, 5, state).simulationEligible(), "Choosing legacy BEFORE does not invent supported evidence.");
		PhantomAssertions.assertTrue(pending.complete(true, 5, state).simulationEligible(), "Choosing attested AFTER can complete its own proof.");
		final var boosted = PhantomNativeContext.pending(identity, 1, Eligibility.SUPPORTED, new Capture(1000, Eligibility.VITALITY_REQUIRES_NATIVE), 4, state, 99, receipt);
		PhantomAssertions.assertFalse(boosted.complete(true, 5, state).simulationEligible(), "Saved native bonus context must require original native evolution.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new Capture(0, Eligibility.SUPPORTED), "Native capture cannot claim canonical legacy zero.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new Capture(1000, Eligibility.SUPPORTED), "Boosted points cannot be labelled simulation supported.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomNativeContext.completed(identity, -1, Eligibility.UNKNOWN, 4, state), "Negative canonical points must fail closed.");
	}

	public static void loadedPointsRequireExactWitness(PhantomBackgroundState state)
	{
		final var current = state.withState(PhantomBackgroundState.State.READY);
		final var legacy = new PhantomBackgroundTransaction.NativeContextResult(Status.NATIVE_CONTEXT_REQUIRED, current, null, 0);
		PhantomAssertions.assertTrue(legacy.matchesNativeLoad(1), "Stock clamp is permitted explicitly for canonical legacy zero.");
		PhantomAssertions.assertFalse(legacy.simulationEligible(), "Normalization does not attest policy or load parity.");
		PhantomAssertions.assertFalse(legacy.matchesNativeLoad(0), "Native getter cannot return canonical zero.");
		final var proof = PhantomNativeContext.completed(current.identity(), 1, Eligibility.SUPPORTED, 4, new byte[] { 1 });
		final var exact = new PhantomBackgroundTransaction.NativeContextResult(Status.SUCCESS, current, proof, 1);
		PhantomAssertions.assertTrue(exact.matchesNativeLoad(1), "Fresh exact native scalar must be accepted.");
		PhantomAssertions.assertFalse(exact.matchesNativeLoad(2), "A vitality-only load mismatch must fence.");
		PhantomAssertions.assertFalse(new PhantomBackgroundTransaction.NativeContextResult(Status.CANONICAL_MISMATCH, current, proof, 1).matchesNativeLoad(1), "A failed canonical guard cannot be bypassed by runtime points.");
	}

	/** Root invokes these against its genuine PREPARE/native STORE/finalize/fault fixtures. */
	public static void assertPending(PhantomBackgroundTransaction transaction, PhantomOwnedStoreIntent intent, int beforePoints, int afterPoints)
	{
		final var proof = transaction.nativeContext(intent.after().identity().profileId(), intent.after().identity().characterObjectId());
		PhantomAssertions.assertEquals(Status.SUCCESS, proof.status(), "Pending scalar read must pass original canonical guards.");
		PhantomAssertions.assertTrue(proof.context() != null && proof.context().phase() == PhantomNativeContext.Phase.PENDING, "No missing/subset pending proof.");
		PhantomAssertions.assertTrue(proof.context().matchesReceipt(intent.preparedRowVersion(), intent.materializedAtNanos(), intent.encode()), "Exact old receipt bytes and owner epoch must bind.");
		PhantomAssertions.assertEquals(beforePoints, proof.context().beforePoints(), "Exact BEFORE points.");
		PhantomAssertions.assertEquals(afterPoints, proof.context().afterPoints(), "Exact AFTER points.");
		PhantomAssertions.assertFalse(proof.simulationEligible(), "A native save in flight cannot admit simulation.");
	}

	public static void assertCompleted(PhantomBackgroundTransaction transaction, PhantomBackgroundState expected, int points, boolean eligible)
	{
		final var proof = transaction.nativeContext(expected.identity().profileId(), expected.identity().characterObjectId());
		PhantomAssertions.assertTrue(proof.status() == Status.SUCCESS || proof.status() == Status.NATIVE_CONTEXT_REQUIRED, "Completed scalar read must retain canonical guards.");
		PhantomAssertions.assertEquals(expected, proof.state(), "Exact completed B4 state binding.");
		PhantomAssertions.assertEquals(points, proof.canonicalPoints(), "Exact durable native points.");
		PhantomAssertions.assertTrue(proof.context() != null && proof.context().phase() != PhantomNativeContext.Phase.PENDING, "Completed scalar must be retained.");
		PhantomAssertions.assertEquals(points, proof.context().afterPoints(), "Saved native scalar and canonical row must match.");
		PhantomAssertions.assertEquals(eligible, proof.context().simulationEligible(), "Native-only/UNKNOWN must never be labelled supported.");
	}
}
