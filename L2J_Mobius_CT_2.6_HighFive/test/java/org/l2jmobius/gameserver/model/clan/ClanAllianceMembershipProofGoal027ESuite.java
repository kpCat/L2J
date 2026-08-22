/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.model.clan.ClanAllianceService.Actor;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceMembershipProof;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanSocialDomainGoal027CSuite.AllianceStateAccess;
import org.l2jmobius.gameserver.model.clan.ClanSocialDomainGoal027CSuite.FakePersistence;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class ClanAllianceMembershipProofGoal027ESuite implements PhantomTestSuite
{
	private static final long SEED = 27002750L;
	private static final long NOW = 1_000_000L;

	@Override
	public String id()
	{
		return "clan-alliance-membership-proof-goal027e";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 027E suite used the wrong deterministic seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-complete-sorted-immutable-proof-capture", this::proofCapture);
		registry.add("02-unexpected-member-exact-set-cas", this::unexpectedMember);
		registry.add("03-membership-aba-rejected", this::membershipAba);
		registry.add("04-alliance-generation-replay-rejected", this::generationReplay);
		registry.add("05-happy-persistence-and-retirement-outcomes", this::dissolveOutcomes);
		registry.add("06-bounded-source-contract", this::sourceBoundedness);
	}

	private void proofCapture(PhantomTestContext context)
	{
		final Fixture fixture = fixture(2);
		final AllianceMembershipProof proof = proof(fixture);
		PhantomAssertions.assertEquals(fixture.identity(), proof.identity(), "Proof changed the captured alliance identity.");
		PhantomAssertions.assertEquals(List.of(1, 2), proof.memberEpochs().stream().map(MembershipEpoch::clanId).toList(), "Proof did not expose the complete sorted clan-id set.");
		for (MembershipEpoch member : proof.memberEpochs())
		{
			PhantomAssertions.assertEquals(fixture.state().clan(member.clanId()).membershipEpoch(), member, "Proof did not expose the exact member epoch.");
		}
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> proof.memberEpochs().add(proof.memberEpochs().get(0)), "Proof member list is mutable.");
	}

	private void unexpectedMember(PhantomTestContext context)
	{
		final Fixture fixture = fixture(2);
		final AllianceMembershipProof captured = proof(fixture);
		PhantomAssertions.assertTrue(join(fixture, 3).successful(), "Unexpected canonical member C could not join the fixture alliance.");
		final ClanAllianceService.Result result = fixture.service().dissolveWithProof(fixture.leader(), captured);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, result.status(), "A/B proof dissolved an alliance after unexpected C joined.");
		PhantomAssertions.assertEquals(List.of(1, 2, 3), proof(fixture).memberEpochs().stream().map(MembershipEpoch::clanId).toList(), "Stale A/B dissolve mutated the durable A/B/C set.");
		assertAllied(fixture, 1, 2, 3);
	}
	private void membershipAba(PhantomTestContext context)
	{
		final Fixture fixture = fixture(2);
		final AllianceMembershipProof captured = proof(fixture);
		final Actor member = actor(2);
		PhantomAssertions.assertTrue(fixture.service().leave(member, fixture.identity()).successful(), "Member B could not leave before ABA replay.");
		fixture.clock().addAndGet(TimeUnit.DAYS.toMillis(30));
		PhantomAssertions.assertTrue(join(fixture, 2).successful(), "Member B could not rejoin after its leave penalty expired.");
		final MembershipEpoch currentMember = fixture.state().clan(2).membershipEpoch();
		PhantomAssertions.assertTrue(currentMember.counter() > captured.memberEpochs().get(1).counter(), "B leave/rejoin did not advance its membership epoch.");
		final ClanAllianceService.Result result = fixture.service().dissolveWithProof(fixture.leader(), captured);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, result.status(), "Pre-ABA proof dissolved the rejoined alliance.");
		PhantomAssertions.assertEquals(currentMember, proof(fixture).memberEpochs().get(1), "Rejected ABA proof mutated durable membership.");
		assertAllied(fixture, 1, 2);
	}

	private void generationReplay(PhantomTestContext context)
	{
		final Fixture fixture = fixture();
		final AllianceMembershipProof generationOneProof = proof(fixture);
		PhantomAssertions.assertTrue(fixture.service().dissolve(fixture.leader(), fixture.identity()).successful(), "Fixture G1 could not dissolve through the existing REAL API.");
		fixture.clock().addAndGet(TimeUnit.DAYS.toMillis(30));
		final ClanAllianceService.Result generationTwo = fixture.service().create(fixture.leader(), "AlphaTwo");
		PhantomAssertions.assertTrue(generationTwo.successful(), "Fixture G2 could not be created.");
		PhantomAssertions.assertTrue(generationTwo.identity().generation() > fixture.identity().generation(), "Alliance generation did not advance from G1 to G2.");
		final ClanAllianceService.Result replay = fixture.service().dissolveWithProof(fixture.leader(), generationOneProof);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, replay.status(), "G1 proof mutated G2.");
		PhantomAssertions.assertEquals(generationTwo.identity(), fixture.state().clan(1).identity(), "Rejected G1 proof changed live G2.");
		PhantomAssertions.assertEquals(generationTwo.identity(), fixture.service().captureMembershipProof(generationTwo.identity()).proof().identity(), "Rejected G1 proof changed durable G2.");
	}
	private void dissolveOutcomes(PhantomTestContext context)
	{
		final Fixture happy = fixture(2);
		final AllianceMembershipProof happyProof = proof(happy);
		PhantomAssertions.assertTrue(happy.service().dissolveWithProof(happy.leader(), happyProof).successful(), "Current exact proof did not dissolve its alliance.");
		PhantomAssertions.assertEquals(0, happy.state().clan(1).allianceId(), "Happy proof dissolve left leader allied.");
		PhantomAssertions.assertEquals(0, happy.state().clan(2).allianceId(), "Happy proof dissolve left member allied.");

		final Fixture persistenceFailure = fixture(2);
		final AllianceMembershipProof persistenceProof = proof(persistenceFailure);
		persistenceFailure.persistence().failAllianceWrites = true;
		final ClanAllianceService.Result failedWrite = persistenceFailure.service().dissolveWithProof(persistenceFailure.leader(), persistenceProof);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.PERSISTENCE_FAILURE, failedWrite.status(), "Dissolve SQL failure was not typed.");
		persistenceFailure.persistence().failAllianceWrites = false;
		PhantomAssertions.assertEquals(persistenceProof, proof(persistenceFailure), "Dissolve SQL failure mutated durable membership.");
		assertAllied(persistenceFailure, 1, 2);

		final Fixture retirement = fixture(2);
		final AllianceMembershipProof retirementProof = proof(retirement);
		final ClanSocialMutationFence.Retirement token = retirement.fence().beginRetirement(2);
		final ClanAllianceService.Result retired = retirement.service().dissolveWithProof(retirement.leader(), retirementProof);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.INELIGIBLE, retired.status(), "Retirement race was not typed non-success.");
		PhantomAssertions.assertEquals(ClanAllianceService.Reason.CLAN_RETIRING, retired.reason(), "Retirement race returned the wrong reason.");
		PhantomAssertions.assertTrue(retirement.fence().abortRetirement(token), "Retirement fixture token did not abort.");
		PhantomAssertions.assertEquals(retirementProof, proof(retirement), "Retirement rejection mutated durable membership.");
		assertAllied(retirement, 1, 2);

		final Fixture readFailure = fixture();
		readFailure.persistence().failAllianceReads = true;
		PhantomAssertions.assertEquals(ClanAllianceService.Status.PERSISTENCE_FAILURE, readFailure.service().captureMembershipProof(readFailure.identity()).status(), "Proof SQL failure was not typed.");
	}

	private void sourceBoundedness(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String repository = Files.readString(root.resolve("java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java"));
		final String service = Files.readString(root.resolve("java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java"));
		final String schema = Files.readString(root.resolve("dist/db_installer/sql/game/clan_data.sql"));
		final String exactQuery = "SELECT clan_id, ally_id, ally_generation, ally_generation_counter FROM clan_data WHERE ally_id=? AND ally_generation=? ORDER BY clan_id";
		PhantomAssertions.assertTrue(repository.contains(exactQuery), "Membership proof is not sourced by the bounded exact-incarnation query.");
		final int readStart = repository.indexOf("public List<ClanAllianceService.MembershipEpoch> loadAllianceMembership");
		final int readEnd = repository.indexOf("\n\t@Override", readStart + 1);
		final String readMethod = repository.substring(readStart, readEnd);
		assertNoRegistryOrPhantomSource(readMethod, "Repository membership read");
		final int proofStart = service.indexOf("public ProofResult captureMembershipProof");
		final int proofEnd = service.indexOf("public Result dissolve(Player player, AllianceIdentity expectedIdentity)");
		final String proofPath = service.substring(proofStart, proofEnd);
		assertNoRegistryOrPhantomSource(proofPath, "Public proof path");
		PhantomAssertions.assertTrue(proofPath.contains("_fence.execute") && proofPath.contains("_persistence.dissolveAlliance"), "Proof dissolve is not revalidated and mutated under the canonical fence.");
		PhantomAssertions.assertTrue(repository.contains("requireAllianceMembers(lockAllianceRows(connection, allianceId), memberGenerationCounters, generation)"), "Proof dissolve does not reach the durable exact-set CAS.");
		PhantomAssertions.assertTrue(schema.contains("KEY `ally_id` (`ally_id`)"), "Fresh clan_data lacks the existing bounded lookup index.");
		PhantomAssertions.assertTrue(service.contains("public record AllianceMembershipProof") && service.contains("public Result dissolveWithProof(Player player, AllianceMembershipProof proof)"), "Public transport-neutral proof API is missing.");
	}
	private static void assertNoRegistryOrPhantomSource(String source, String label)
	{
		PhantomAssertions.assertFalse(source.contains("ClanTable.getClans") || source.contains("ClanTable.getClanAllies") || source.contains("_clans.values()"), label + " performs a forbidden clan-registry scan.");
		PhantomAssertions.assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("phantom"), label + " depends on Phantom metadata.");
	}

	private static Fixture fixture(int... joinedClanIds)
	{
		final FakePersistence persistence = new FakePersistence();
		for (int clanId : List.of(1, 2, 3))
		{
			persistence.addClan(clanId);
		}
		final AllianceStateAccess state = AllianceStateAccess.standard(1, 2, 3);
		final ClanSocialMutationFence fence = new ClanSocialMutationFence(16);
		final AtomicLong clock = new AtomicLong(NOW);
		final ClanAllianceService service = new ClanAllianceService(persistence, state, fence, clock::get, true);
		final Actor leader = actor(1);
		final ClanAllianceService.Result created = service.create(leader, "Alpha");
		PhantomAssertions.assertTrue(created.successful(), "Fixture alliance creation failed.");
		final Fixture fixture = new Fixture(persistence, state, fence, clock, service, leader, created.identity());
		for (int clanId : joinedClanIds)
		{
			PhantomAssertions.assertTrue(join(fixture, clanId).successful(), "Fixture member join failed: " + clanId);
		}
		return fixture;
	}

	private static ClanAllianceService.Result join(Fixture fixture, int clanId)
	{
		return ClanSocialDomainGoal027CSuite.joinWithCurrentEpoch(fixture.service(), fixture.leader(), actor(clanId), fixture.identity());
	}

	private static AllianceMembershipProof proof(Fixture fixture)
	{
		final ClanAllianceService.ProofResult result = fixture.service().captureMembershipProof(fixture.identity());
		PhantomAssertions.assertTrue(result.successful(), "Exact alliance membership proof capture failed: " + result.status() + '/' + result.reason());
		return result.proof();
	}

	private static void assertAllied(Fixture fixture, int... clanIds)
	{
		for (int clanId : clanIds)
		{
			PhantomAssertions.assertEquals(fixture.identity(), fixture.state().clan(clanId).identity(), "Rejected proof dissolve changed live member " + clanId + '.');
		}
	}

	private static Actor actor(int clanId)
	{
		return new Actor(100 + clanId, clanId, true, false);
	}

	private record Fixture(FakePersistence persistence, AllianceStateAccess state, ClanSocialMutationFence fence, AtomicLong clock, ClanAllianceService service, Actor leader, AllianceIdentity identity)
	{
	}
}