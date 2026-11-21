/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.Offense;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.OffenseContext;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelFixture;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelIdentity;

public final class PhantomCombat131Checks
{
	public static void main(String[] args) throws IOException
	{
		PhantomCombatCoreSuite.testReadySkillBeyondLoadoutBound();
		System.out.println("TASK131_D1_READY_SKILL_BOUND_PASS");
		PhantomCombatCoreSuite.testUnlistedAcquiredPveSkill();
		System.out.println("TASK131_D1_ACQUIRED_PVE_PASS");
		PhantomCombatCoreSuite.testPartySupportYield();
		System.out.println("TASK131_D2_PARTY_SUPPORT_YIELD_PASS");
		PhantomCombatCoreSuite.testRetreatDrainsNativeCast();
		System.out.println("TASK131_D9_RETREAT_DRAIN_PASS");
		testSharedOffensePreference();
		System.out.println("TASK131_D1_CONTEXTUAL_OFFENSE_PASS");
		PhantomCombatCoreSuite.testPartyAreaOwner();
		System.out.println("TASK131_D3_PARTY_AREA_OWNER_PASS");
		testDuelSetupRoster();
		System.out.println("TASK131_N06_SETUP_ROSTER_CONTRACT_PASS");
		testDuelReceipt();
		System.out.println("TASK131_N06_SETUP_RECEIPT_CONTRACT_PASS");
		testDuelRunManifest();
		System.out.println("TASK131_N06_RUN_MANIFEST_CONTRACT_PASS");
	}

	private static void testDuelReceipt() throws IOException
	{
		final Path scratch = Files.createTempDirectory(Path.of("."), "task131-n06-receipt-");
		final Path receipt = scratch.resolve("n06-duel-fixture.tsv");
		final Path cut = scratch.resolve("n06-cut.tsv");
		final String header = "status\tarm\tside\topeningSide\tprofileId\tobjectId\taccount\trole\tclass\tlevel\tweapon\tshotItem\tshots\tadena\tarena\tinstanceId\tstartX\tstartY\tstartZ\tstoredX\tstoredY\tstoredZ\tlearnedPvpRanks\tcontrolRanks\n";
		final String contents = header
			+ "SETUP_DUEL_STORED\t1\tA\tA\t1\t110\towned_a\tMAGE\t94\t76\t6366\t2514\t2000\t50000\tgludin_pvp\t0\t-88000\t142220\t-3500\t10000\t10000\t-3000\t10:12,11:18\t11:18\n"
			+ "SETUP_DUEL_STORED\t1\tB\tA\t2\t120\towned_b\tPHYSICAL\t88\t76\t6580\t1467\t2000\t50000\tgludin_pvp\t0\t-87840\t142220\t-3500\t10001\t10000\t-3000\t20:9,21:14\t\n"
			+ "SETUP_DUEL_STORED\t2\tA\tA\t3\t130\towned_c\tPHYSICAL\t88\t76\t6580\t1467\t2000\t50000\tgludin_pvp\t0\t-88000\t142220\t-3500\t10002\t10000\t-3000\t20:9,21:14\t\n"
			+ "SETUP_DUEL_STORED\t2\tB\tA\t4\t140\towned_d\tMAGE\t94\t76\t6366\t2514\t2000\t50000\tgludin_pvp\t0\t-87840\t142220\t-3500\t10003\t10000\t-3000\t10:12,11:18\t11:18\n";
		try
		{
			final String sha = writeReceipt(receipt, contents);
			final var fixture = PhantomCombat131Fixture.readDuelFixture(receipt, sha);
			PhantomAssertions.assertEquals(sha, fixture.sha256(), "Receipt bytes were not pinned.");
			PhantomAssertions.assertEquals(140, fixture.arm(2).getLast().identity().object(), "The mirrored arm did not retain its independent actor.");
			PhantomAssertions.assertEquals(-87840, fixture.arm(2).getLast().start().x(), "Planned stock positions were not retained.");
			testUnboundDuel(fixture, cut, sha);
			final String drifted = contents.replace("\towned_d\t", "\towned_e\t");
			writeReceipt(receipt, drifted);
			rejectDuelReceipt(receipt, sha);
			PhantomAssertions.assertEquals("owned_d", fixture.arm(2).getLast().identity().account(), "A file edit mutated the admitted receipt data.");
			for (String malformed : List.of(contents.replace("\t-87840\t142220\t", "\t-87839\t142220\t"),
				contents.replace("\t6366\t2514\t", "\t6366\t2515\t"),
				contents.replace("10:12,11:18\t11:18", "10:12,10:18\t10:18")))
			{
				rejectDuelReceipt(receipt, writeReceipt(receipt, malformed));
			}
		}
		finally
		{
			Files.deleteIfExists(cut);
			Files.deleteIfExists(receipt);
			Files.delete(scratch);
		}
	}

	private static void testDuelRunManifest() throws IOException
	{
		final Path scratch = Files.createTempDirectory(Path.of("."), "task131-n06-manifest-");
		final Path manifest = scratch.resolve("n06-run.properties");
		final String zeroSha = "0".repeat(64);
		final Instant now = Instant.parse("2026-11-21T12:00:00Z");
		final String input = "schema=TASK131_N06_RUN_V1\nphase=N06\nsqlLane=n06-setup-r1\nruntimeId=native-runtime-r7\n"
			+ "gameJarSha=" + zeroSha + "\njdbcSha=" + zeroSha + "\nsqlAttestationSha=" + zeroSha
			+ "\nsqlPid=100\nsqlStartedUtc=2026-11-21T11:55:00Z\nfixtureSha=" + zeroSha + "\nbootId=n06r1\n"
			+ "issuedUtc=2026-11-21T11:59:00Z\ndeadlineUtc=2026-11-21T12:07:00Z\narms=2\nsecondsPerArm=20\n";
		try
		{
			final String sha = writeReceipt(manifest, input);
			final var run = PhantomCombat131Fixture.readDuelRun(manifest, sha, now);
			PhantomAssertions.assertEquals(20, run.seconds(), "The two-arm run did not preserve per-arm time.");
			PhantomAssertions.assertThrows(RuntimeException.class, () -> PhantomCombat131Fixture.readDuelRun(manifest, sha, now.plusSeconds(335)),
				"A run was admitted without both native drain windows.");
			final String duplicateSha = writeReceipt(manifest, input + "phase=N06\n");
			PhantomAssertions.assertThrows(RuntimeException.class, () -> PhantomCombat131Fixture.readDuelRun(manifest, sha, now),
				"A run admitted mutated manifest bytes.");
			PhantomAssertions.assertThrows(RuntimeException.class, () -> PhantomCombat131Fixture.readDuelRun(manifest, duplicateSha, now),
				"A run admitted duplicate scope keys.");
		}
		finally
		{
			Files.deleteIfExists(manifest);
			Files.delete(scratch);
		}
	}

	private static void testUnboundDuel(DuelFixture fixture, Path cut, String sha)
	{
		final var duel = new PhantomCombat131Duel(fixture, 1, Instant.now().plusSeconds(30));
		PhantomAssertions.assertTrue(!duel.openingLandedBefore(System.nanoTime()), "An unbound arm cannot have a native hit.");
		final var otherArm = fixture.arm(2).getFirst().identity();
		final var chosen = fixture.arm(1).getFirst().identity();
		try
		{
			PhantomAssertions.assertThrows(RuntimeException.class, () -> duel.beforeMaterialize(otherArm.profile(), otherArm.object()),
				"A mirrored arm participant entered the currently selected duel.");
			PhantomAssertions.assertThrows(RuntimeException.class, () -> duel.sealAtStart(cut, sha, "boot1", "N06A"),
				"A duel cut succeeded without both materialized owners.");
			PhantomAssertions.assertFalse(Files.exists(cut), "A rejected duel cut wrote an artifact.");
		}
		finally { duel.close(); }
		PhantomAssertions.assertThrows(RuntimeException.class, () -> duel.beforeMaterialize(chosen.profile(), chosen.object()),
			"The closed duel admitted a participant again.");
	}

	private static String writeReceipt(Path path, String content) throws IOException
	{
		final byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
		Files.write(path, bytes);
		return TacticalObservationV3.hash(bytes);
	}

	private static void rejectDuelReceipt(Path receipt, String sha) throws IOException
	{
		try { PhantomCombat131Fixture.readDuelFixture(receipt, sha); }
		catch (IllegalArgumentException | IllegalStateException expected) { return; }
		throw new AssertionError("Inconsistent N06 setup receipt was accepted.");
	}

	private static void testDuelSetupRoster()
	{
		final var mage = List.of(new SelectedSkill(10, 12), new SelectedSkill(11, 18));
		final var physical = List.of(new SelectedSkill(20, 9), new SelectedSkill(21, 14));
		final var first = new DuelIdentity(1, "A", 1, 110, "owned_a", "MAGE", 10, mage);
		final var second = new DuelIdentity(1, "B", 2, 120, "owned_b", "PHYSICAL", 88, physical);
		final var third = new DuelIdentity(2, "A", 3, 130, "owned_c", "PHYSICAL", 88, physical);
		final var fourth = new DuelIdentity(2, "B", 4, 140, "owned_d", "MAGE", 10, mage);
		PhantomCombat131Fixture.validateDuelRoster(List.of(first, second, third, fourth));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 110, "owned_d", "MAGE", 10, mage)));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 140, "owned_a", "MAGE", 10, mage)));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 140, "owned_d", "MAGE", 10,
			List.of(new SelectedSkill(10, 11), mage.getLast()))));
	}

	private static void rejectDuelRoster(List<DuelIdentity> roster)
	{
		try { PhantomCombat131Fixture.validateDuelRoster(roster); }
		catch (IllegalStateException expected) { return; }
		throw new AssertionError("Inconsistent N06 setup roster was accepted.");
	}

	private static void testSharedOffensePreference()
	{
		final var strike = new Offense(new SelectedSkill(31, 4), true, false, false, 200, 500, 900, 70, 100, 21);
		final var interrupt = new Offense(new SelectedSkill(67, 7), false, true, false, 300, 500, 400, 70, 0, 12);
		final var activeControl = new Offense(interrupt.skill(), false, true, true, 300, 500, 400, 70, 0, 12);
		final var ordinary = new OffenseContext(false, false, true, 0.8, 0.8, 300);
		final var hostileCast = new OffenseContext(false, true, true, 0.8, 0.8, 300);
		final var distantCast = new OffenseContext(false, true, true, 0.8, 0.8, 550);
		final var occludedCast = new OffenseContext(false, true, false, 0.8, 0.8, 300);
		final var pvpFinisher = new OffenseContext(true, false, true, 0.12, 0.8, 300);
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(ordinary, List.of(interrupt, strike)).getFirst(), "PvE without a cast to interrupt should prefer damage.");
		PhantomAssertions.assertEquals(interrupt, CombatDecisionPolicy131.rankOffense(hostileCast, List.of(strike, interrupt)).getFirst(), "An in-range interruption should protect both combat paths.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(distantCast, List.of(interrupt, strike)).getFirst(), "An out-of-range control is not an available interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(occludedCast, List.of(interrupt, strike)).getFirst(), "A hidden target is not an available interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(hostileCast, List.of(activeControl, strike)).getFirst(), "Already active control must not be treated as a new interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(pvpFinisher, List.of(strike, interrupt)).getFirst(), "A low-health opponent should be finished rather than controlled again.");
		final var area = new Offense(new SelectedSkill(68, 2), true, false, false, 200, 500, 1100, 70, 80, 40, 3);
		PhantomAssertions.assertEquals(area, CombatDecisionPolicy131.rankOffense(ordinary, List.of(strike, area)).getFirst(), "Coverage of the admitted group of enemies did not affect the common chooser.");
		final var lowMp = new OffenseContext(false, false, true, 0.8, 0.30, 300);
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(lowMp, List.of(strike, area)).getFirst(), "Area coverage permanently displaced resource conservation.");
	}
}
