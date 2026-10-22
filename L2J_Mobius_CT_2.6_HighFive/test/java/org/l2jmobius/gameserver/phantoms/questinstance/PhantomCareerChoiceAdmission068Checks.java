/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Proposal admission only: immutable choices and strict ensure remain owned by progression. */
public final class PhantomCareerChoiceAdmission068Checks implements PhantomTestSuite
{
	@Override public String id() { return "career-choice-admission068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-conflicting-future-choice-is-unavailable-without-repair", _ -> admission());
	}
	public static void main(String[] args)
	{
		admission();
		System.out.println("OWN068_CAREER_CHOICE_ADMISSION_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void admission()
	{
		final var maestro = PhantomCareerChoice.decode("53:118:false:ECONOMY".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		final var calls = new AtomicInteger();
		final var unavailable = PhantomAutonomousCareer.proposalChoice(maestro, 54, false, () ->
		{
			calls.incrementAndGet();
			throw new IllegalArgumentException("Career choice conflicts with current identity.");
		});
		PhantomAssertions.assertTrue(unavailable.isEmpty(), "An unavailable future branch must not throw through the ordinary farm preflight.");
		PhantomAssertions.assertEquals(0, calls.get(), "Incompatible proposal must not invoke strict ensure or create/replace an immutable choice.");
		PhantomAssertions.assertEquals(118, maestro.terminalClass(), "Rejected proposal cannot rewrite its stored future branch.");
		for (int current : List.of(53, 56, 57, 118))
		{
			final var admitted = PhantomAutonomousCareer.proposalChoice(maestro, current, false, () -> { calls.incrementAndGet(); return maestro; });
			PhantomAssertions.assertEquals(maestro, admitted.orElseThrow(), "Every compatible ancestor/current/terminal identity retains strict ensure.");
		}
		PhantomAssertions.assertEquals(4, calls.get(), "Compatible choices must still pass through their original authority.");
		PhantomAssertions.assertTrue(PhantomAutonomousCareer.proposalChoice(maestro, 56, true, () -> { throw new AssertionError("Identity conflict must not ensure."); }).isEmpty(), "Sex conflict remains unavailable without mutation.");
		PhantomAssertions.assertEquals(maestro, PhantomAutonomousCareer.proposalChoice(null, 53, false, () -> maestro).orElseThrow(), "Absent preference still uses the original deterministic ensure.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class,
			() -> PhantomAutonomousCareer.proposalChoice(maestro, 56, false, () -> { throw new IllegalArgumentException("Unknown career choice version."); }),
			"Unrelated strict-store/version failures cannot be swallowed as unavailable identity.");
	}
}
