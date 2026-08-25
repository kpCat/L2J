/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.EventSocialClass;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.tests.phantoms.PhantomSocialTestDoubles.MemoryStore;

public final class PhantomSocialHumanizationGoal030BSuite implements PhantomTestSuite
{
	private static final long SEED = 30003020L;
	private static final long MINUTE = 1000L;
	private static final SubjectRef SUBJECT = SubjectRef.character(77);
	private PhantomSocialCatalog _catalog;

	@Override
	public String id()
	{
		return "social-humanization-goal030b";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 030B requires the exact deterministic seed.");
		_catalog = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-context-policy-strict-catalog", this::testStrictCatalog);
		registry.add("02-clan-alliance-positive-and-minor-negative-scaling", this::testPositiveAndRoutineScaling);
		registry.add("03-clan-betrayal-and-war-combat-semantics", this::testBetrayalAndWar);
		registry.add("04-established-reputation-reversal-inertia", this::testEstablishedReputation);
		registry.add("05-repeated-evidence-can-reverse-reputation", this::testRepeatedReversal);
		registry.add("06-non-reputation-dimensions-unchanged", this::testRelationshipCompatibility);
		registry.add("07-context-default-backward-compatibility", this::testBackwardCompatibility);
		registry.add("08-affiliation-policy-invalid-catalogs-fail-closed", this::testInvalidAffiliationCatalogs);
	}

	private void testStrictCatalog(PhantomTestContext context)
	{
		assertMultiplier(AffiliationKind.NONE, 10000, 10000, 10000, 10000, 10000);
		assertMultiplier(AffiliationKind.SAME_CLAN, 12000, 7000, 13000, 8500, 10000);
		assertMultiplier(AffiliationKind.SAME_ALLIANCE, 11000, 8500, 11500, 9250, 10000);
		assertMultiplier(AffiliationKind.CLAN_WAR, 10000, 10000, 10000, 7000, 10000);
		PhantomAssertions.assertEquals(24, _catalog.events().size(), "Authoritative social event count changed.");
		for (var event : _catalog.events())
		{
			PhantomAssertions.assertTrue((event.reputationShockBp() >= 0) && (event.reputationShockBp() <= 10000), "Reputation shock is outside basis-point bounds.");
			switch (event.socialClass())
			{
				case SUPPORTIVE, ROUTINE_NEGATIVE -> PhantomAssertions.assertTrue(event.reputationShockBp() <= 1500, "Routine/supportive shock exceeds guidance.");
				case BETRAYAL -> PhantomAssertions.assertTrue(event.reputationShockBp() >= 7000, "Betrayal shock is below guidance.");
				case HOSTILE_COMBAT -> PhantomAssertions.assertTrue((event.reputationShockBp() >= 2500) && (event.reputationShockBp() <= 5000), "Hostile-combat shock is outside guidance.");
				case NEUTRAL -> PhantomAssertions.assertEquals(0, event.reputationShockBp(), "Neutral bookkeeping has non-zero reputation shock.");
			}
		}
		context.record("goal030b.catalogHash", _catalog.hash());
		context.record("goal030b.eventCount", _catalog.events().size());
	}

	private void testInvalidAffiliationCatalogs(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"), StandardCharsets.UTF_8);
		final String sameClan = affiliationRow(source, "SAME_CLAN");
		final String clanWar = affiliationRow(source, "CLAN_WAR");
		final String policySection = affiliationPolicySection(source);

		rejectCatalog(context, "missing-same-clan", replaceExact(source, sameClan, ""));
		rejectCatalog(context, "duplicate-clan-war", replaceExact(source, "	</affiliationMultipliers>", clanWar + System.lineSeparator() + "	</affiliationMultipliers>"));
		rejectCatalog(context, "missing-multiplier", replaceExact(source, "			<multiplier socialClass=\"BETRAYAL\" basisPoints=\"13000\"/>", "			<multiplier socialClass=\"BETRAYAL\"/>"));
		rejectCatalog(context, "unknown-affiliation", replaceExact(source, "<affiliation kind=\"SAME_ALLIANCE\">", "<affiliation kind=\"UNKNOWN\">"));
		rejectCatalog(context, "out-of-range", replaceExact(source, "basisPoints=\"13000\"", "basisPoints=\"20001\""));
		rejectCatalog(context, "extra-attribute", replaceExact(source, "<affiliation kind=\"NONE\">", "<affiliation kind=\"NONE\" extra=\"forbidden\">"));
		final String withoutPolicy = replaceExact(source, policySection, "");
		rejectCatalog(context, "bad-section-order", replaceExact(withoutPolicy, "	</traits>", "	</traits>" + System.lineSeparator() + policySection));

		context.record("goal030b.affiliationParserNegativeCases", 7);
	}

	private void assertMultiplier(AffiliationKind affiliation, int supportive, int routineNegative, int betrayal, int hostileCombat, int neutral)
	{
		PhantomAssertions.assertEquals(supportive, _catalog.affiliationMultiplierBp(affiliation, EventSocialClass.SUPPORTIVE), "Supportive affiliation multiplier changed.");
		PhantomAssertions.assertEquals(routineNegative, _catalog.affiliationMultiplierBp(affiliation, EventSocialClass.ROUTINE_NEGATIVE), "Routine-negative affiliation multiplier changed.");
		PhantomAssertions.assertEquals(betrayal, _catalog.affiliationMultiplierBp(affiliation, EventSocialClass.BETRAYAL), "Betrayal affiliation multiplier changed.");
		PhantomAssertions.assertEquals(hostileCombat, _catalog.affiliationMultiplierBp(affiliation, EventSocialClass.HOSTILE_COMBAT), "Hostile-combat affiliation multiplier changed.");
		PhantomAssertions.assertEquals(neutral, _catalog.affiliationMultiplierBp(affiliation, EventSocialClass.NEUTRAL), "Neutral affiliation multiplier changed.");
	}

	private void testPositiveAndRoutineScaling(PhantomTestContext context)
	{
		final Fixture fixture = fixture(9);
		record(fixture.service(), event(1, "support-none", "party.support.received", AffiliationKind.NONE, 1000));
		record(fixture.service(), event(2, "support-alliance", "party.support.received", AffiliationKind.SAME_ALLIANCE, 1000));
		record(fixture.service(), event(3, "support-clan", "party.support.received", AffiliationKind.SAME_CLAN, 1000));
		final int supportNone = reputation(fixture.service(), 1, "helpfulness");
		final int supportAlliance = reputation(fixture.service(), 2, "helpfulness");
		final int supportClan = reputation(fixture.service(), 3, "helpfulness");
		PhantomAssertions.assertEquals(List.of(200, 220, 240), List.of(supportNone, supportAlliance, supportClan), "Supportive reputation scaling is not exact.");
		PhantomAssertions.assertTrue((supportClan > supportAlliance) && (supportAlliance > supportNone), "Clan/alliance supportive ordering changed.");
		PhantomAssertions.assertEquals(List.of(800, 880, 960), List.of(memorySalience(fixture.service(), 1), memorySalience(fixture.service(), 2), memorySalience(fixture.service(), 3)), "Supportive memory salience scaling is not exact.");

		record(fixture.service(), event(4, "left-none", "party.member.left", AffiliationKind.NONE, 1000));
		record(fixture.service(), event(5, "left-alliance", "party.member.left", AffiliationKind.SAME_ALLIANCE, 1000));
		record(fixture.service(), event(6, "left-clan", "party.member.left", AffiliationKind.SAME_CLAN, 1000));
		final int routineNone = Math.abs(reputation(fixture.service(), 4, "reliability"));
		final int routineAlliance = Math.abs(reputation(fixture.service(), 5, "reliability"));
		final int routineClan = Math.abs(reputation(fixture.service(), 6, "reliability"));
		PhantomAssertions.assertEquals(List.of(50, 42, 35), List.of(routineNone, routineAlliance, routineClan), "Routine-negative reputation scaling is not exact.");
		PhantomAssertions.assertTrue((routineClan < routineAlliance) && (routineAlliance < routineNone), "Clan/alliance routine-negative ordering changed.");

		record(fixture.service(), event(7, "accepted-none", "party.invite.accepted.outbound", AffiliationKind.NONE, 1000));
		record(fixture.service(), event(8, "accepted-clan", "party.invite.accepted.outbound", AffiliationKind.SAME_CLAN, 1000));
		PhantomAssertions.assertEquals(agreements(fixture.service(), 7), agreements(fixture.service(), 8), "Affiliation scaled agreement counters.");
		PhantomAssertions.assertEquals(Map.of("offered", 1, "accepted", 1, "fulfilled", 0, "broken", 0, "refused", 0), agreements(fixture.service(), 7), "Agreement counters are not exact.");

		context.record("goal030b.support.helpfulness.none-alliance-clan", supportNone + "," + supportAlliance + "," + supportClan);
		context.record("goal030b.routine.reliability-damage.none-alliance-clan", routineNone + "," + routineAlliance + "," + routineClan);
		stop(fixture.service());
	}
	private void testBetrayalAndWar(PhantomTestContext context)
	{
		final Fixture fixture = fixture(5);
		record(fixture.service(), event(1, "betrayal-none", "party.member.expelled", AffiliationKind.NONE, 1000));
		record(fixture.service(), event(2, "betrayal-alliance", "party.member.expelled", AffiliationKind.SAME_ALLIANCE, 1000));
		record(fixture.service(), event(3, "betrayal-clan", "party.member.expelled", AffiliationKind.SAME_CLAN, 1000));
		final int betrayalNone = Math.abs(relationship(fixture.service(), 1, "trust"));
		final int betrayalAlliance = Math.abs(relationship(fixture.service(), 2, "trust"));
		final int betrayalClan = Math.abs(relationship(fixture.service(), 3, "trust"));
		PhantomAssertions.assertEquals(List.of(300, 345, 390), List.of(betrayalNone, betrayalAlliance, betrayalClan), "Betrayal relationship scaling is not exact.");
		PhantomAssertions.assertTrue((betrayalClan > betrayalAlliance) && (betrayalAlliance > betrayalNone), "Clan/alliance betrayal ordering changed.");
		PhantomAssertions.assertEquals(List.of(1000, 1150, 1300), List.of(memorySalience(fixture.service(), 1), memorySalience(fixture.service(), 2), memorySalience(fixture.service(), 3)), "Betrayal memory salience scaling is not exact.");

		record(fixture.service(), event(4, "combat-none", "pvp.death.suffered", AffiliationKind.NONE, 1000));
		record(fixture.service(), event(5, "combat-war", "pvp.death.suffered", AffiliationKind.CLAN_WAR, 1000));
		final int angerNone = relationship(fixture.service(), 4, "anger");
		final int angerWar = relationship(fixture.service(), 5, "anger");
		final int hostilityNone = reputation(fixture.service(), 4, "hostility");
		final int hostilityWar = reputation(fixture.service(), 5, "hostility");
		final int fearWar = relationship(fixture.service(), 5, "fear");
		final int rivalryWar = relationship(fixture.service(), 5, "rivalry");
		PhantomAssertions.assertEquals(List.of(420, 294), List.of(angerNone, angerWar), "Clan-war anger scaling is not exact.");
		PhantomAssertions.assertEquals(List.of(220, 154), List.of(hostilityNone, hostilityWar), "Clan-war hostility scaling is not exact.");
		PhantomAssertions.assertTrue((angerWar < angerNone) && (hostilityWar < hostilityNone), "Clan-war combat did not reduce personal hostility.");
		PhantomAssertions.assertEquals(List.of(126, 210), List.of(fearWar, rivalryWar), "Clan-war fear/rivalry evidence was erased or scaled incorrectly.");
		PhantomAssertions.assertTrue((fearWar > 0) && (rivalryWar > 0), "Clan-war fear/rivalry evidence must remain non-zero.");

		context.record("goal030b.betrayal.trust-damage.none-alliance-clan", betrayalNone + "," + betrayalAlliance + "," + betrayalClan);
		context.record("goal030b.war.anger.none-war", angerNone + "," + angerWar);
		context.record("goal030b.war.hostility.none-war", hostilityNone + "," + hostilityWar);
		context.record("goal030b.war.fear-rivalry", fearWar + "," + rivalryWar);
		stop(fixture.service());
	}

	private void testEstablishedReputation(PhantomTestContext context)
	{
		final Fixture fixture = fixture(4);
		final List<Integer> sameSignTrace = establishReliability(fixture.service(), 1, "routine");
		PhantomAssertions.assertEquals(List.of(3000, 6000, 9000), sameSignTrace, "Same-sign reputation evidence was damped.");
		record(fixture.service(), event(1, "weak-opposite", "party.invite.expired.outbound", AffiliationKind.NONE, 10000));
		final int weakAfter = reputation(fixture.service(), 1, "reliability");
		PhantomAssertions.assertEquals(8827, weakAfter, "Established reputation weak-opposite inertia changed.");
		PhantomAssertions.assertTrue(weakAfter > 0, "One weak contradictory event flipped established reputation.");

		establishReliability(fixture.service(), 2, "betrayal");
		record(fixture.service(), event(2, "high-shock", "agreement.broken", AffiliationKind.NONE, 10000));
		final int betrayalAfter = reputation(fixture.service(), 2, "reliability");
		PhantomAssertions.assertEquals(4315, betrayalAfter, "High-shock betrayal inertia result changed.");
		PhantomAssertions.assertTrue((9000 - betrayalAfter) > (9000 - weakAfter), "High-shock betrayal did not move reputation more than routine evidence.");

		final List<Integer> clampTrace = establishReliability(fixture.service(), 3, "same-sign");
		record(fixture.service(), event(3, "same-sign-4", "agreement.fulfilled", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(List.of(3000, 6000, 9000), clampTrace, "Positive same-sign evidence was damped before clamp.");
		PhantomAssertions.assertEquals(10000, reputation(fixture.service(), 3, "reliability"), "Same-sign reputation did not reach the normal clamp.");

		record(fixture.service(), event(4, "near-positive", "party.invite.accepted.outbound", AffiliationKind.NONE, 1000));
		PhantomAssertions.assertEquals(60, reputation(fixture.service(), 4, "reliability"), "Near-neutral reputation fixture changed.");
		record(fixture.service(), event(4, "near-opposite", "party.invite.expired.outbound", AffiliationKind.NONE, 1000));
		final int nearAfter = reputation(fixture.service(), 4, "reliability");
		PhantomAssertions.assertEquals(21, nearAfter, "Near-neutral reputation did not reverse almost normally.");

		context.record("goal030b.reputation.same-sign-trace", join(sameSignTrace));
		context.record("goal030b.reputation.weak-after", weakAfter);
		context.record("goal030b.reputation.betrayal-after", betrayalAfter);
		context.record("goal030b.reputation.near-neutral-trace", "0,60,21");
		stop(fixture.service());
	}

	private void testRepeatedReversal(PhantomTestContext context)
	{
		final Fixture fixture = fixture(1);
		establishReliability(fixture.service(), 1, "repeat");
		final StringBuilder trace = new StringBuilder("9000");
		int previous = 9000;
		int count = 0;
		while ((previous > 0) && (count < 64))
		{
			count++;
			record(fixture.service(), event(1, "weak-repeat-" + count, "party.invite.expired.outbound", AffiliationKind.NONE, 10000));
			final int current = reputation(fixture.service(), 1, "reliability");
			PhantomAssertions.assertTrue(current < previous, "Repeated weak opposite evidence is not strictly monotonic.");
			trace.append(',').append(current);
			previous = current;
		}
		PhantomAssertions.assertTrue(previous <= 0, "Repeated weak opposite evidence did not cross zero within 64 events.");
		context.record("goal030b.reputation.reversalEventCount", count);
		context.record("goal030b.reputation.reversalTrace", trace.toString());
		stop(fixture.service());
	}
	private void testRelationshipCompatibility(PhantomTestContext context)
	{
		final Fixture fixture = fixture(6);
		record(fixture.service(), event(1, "trust-positive", "agreement.fulfilled", AffiliationKind.NONE, 10000));
		record(fixture.service(), event(1, "trust-opposite", "agreement.broken", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(-2500, relationship(fixture.service(), 1, "trust"), "Relationship trust was damped by reputation inertia.");

		record(fixture.service(), event(2, "friendship-positive", "party.member.joined", AffiliationKind.NONE, 10000));
		record(fixture.service(), event(2, "friendship-opposite", "party.member.left", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(200, relationship(fixture.service(), 2, "friendship"), "Relationship friendship was damped by reputation inertia.");

		record(fixture.service(), event(3, "fear-positive", "pvp.death.suffered", AffiliationKind.NONE, 10000));
		record(fixture.service(), event(3, "fear-opposite", "pvp.kill.caused", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(1000, relationship(fixture.service(), 3, "fear"), "Relationship fear was damped by reputation inertia.");

		record(fixture.service(), event(4, "debt-positive", "debt.incurred", AffiliationKind.NONE, 10000));
		record(fixture.service(), event(4, "debt-opposite", "debt.repaid", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(0, relationship(fixture.service(), 4, "debt"), "Relationship debt was damped by reputation inertia.");

		record(fixture.service(), event(5, "anger", "party.invite.refused.inbound", AffiliationKind.NONE, 10000));
		record(fixture.service(), event(6, "rivalry", "farming.conflict.escalated", AffiliationKind.NONE, 10000));
		PhantomAssertions.assertEquals(300, relationship(fixture.service(), 5, "anger"), "Relationship anger compatibility changed.");
		PhantomAssertions.assertEquals(800, relationship(fixture.service(), 6, "rivalry"), "Relationship rivalry compatibility changed.");

		context.record("goal030b.relationship.exact-trust-friendship-fear-debt-anger-rivalry", "-2500,200,1000,0,300,800");
		stop(fixture.service());
	}

	private void testBackwardCompatibility(PhantomTestContext context)
	{
		final Fixture fixture = fixture(2);
		final SocialEvent oldEvent = oldEvent(1, "old-neutral", "party.support.received", 1000);
		final SocialEvent explicitNone = event(2, "explicit-neutral", "party.support.received", AffiliationKind.NONE, 1000);
		PhantomAssertions.assertEquals(AffiliationKind.NONE, oldEvent.context().affiliation(), "Old SocialEvent constructor did not default to NONE.");
		record(fixture.service(), oldEvent);
		record(fixture.service(), explicitNone);
		final var oldSnapshot = snapshot(fixture.service(), 1);
		final var explicitSnapshot = snapshot(fixture.service(), 2);
		PhantomAssertions.assertEquals(oldSnapshot.relationship().relationship(), explicitSnapshot.relationship().relationship(), "Old constructor relationship behavior differs from explicit NONE.");
		PhantomAssertions.assertEquals(oldSnapshot.relationship().reputation(), explicitSnapshot.relationship().reputation(), "Old constructor reputation behavior differs from explicit NONE.");
		PhantomAssertions.assertEquals(oldSnapshot.relationship().agreements(), explicitSnapshot.relationship().agreements(), "Old constructor agreement behavior differs from explicit NONE.");
		PhantomAssertions.assertEquals(oldSnapshot.memories().get(0).salience(), explicitSnapshot.memories().get(0).salience(), "Old constructor memory behavior differs from explicit NONE.");

		final int writesBefore = fixture.store().writes();
		PhantomAssertions.assertEquals(Status.IDEMPOTENT, fixture.service().record(oldEvent).status(), "Duplicate old-constructor event was not idempotent.");
		PhantomAssertions.assertEquals(writesBefore, fixture.store().writes(), "Idempotent event wrote in-memory persistence.");
		PhantomAssertions.assertEquals(1, fixture.store().require(1).receipts().receipts().size(), "Receipt ledger cardinality changed after idempotent replay.");
		context.record("goal030b.backward.old-context", oldEvent.context().affiliation());
		context.record("goal030b.backward.receipts", fixture.store().require(1).receipts().receipts().size());
		stop(fixture.service());
	}

	private Fixture fixture(int profiles)
	{
		final MemoryStore store = new MemoryStore();
		store.addProfiles(1, profiles);
		final PhantomSocialService service = new PhantomSocialService(_catalog, store, SEED, 16);
		PhantomAssertions.assertTrue(service.start(), "Goal 030B social service did not start.");
		return new Fixture(service, store);
	}

	private static List<Integer> establishReliability(PhantomSocialService service, long owner, String prefix)
	{
		final java.util.ArrayList<Integer> trace = new java.util.ArrayList<>();
		for (int index = 1; index <= 3; index++)
		{
			record(service, event(owner, prefix + "-positive-" + index, "agreement.fulfilled", AffiliationKind.NONE, 10000));
			trace.add(reputation(service, owner, "reliability"));
		}
		PhantomAssertions.assertTrue(trace.get(2) >= 7000, "Established reliability fixture is below +7000.");
		return List.copyOf(trace);
	}

	private static SocialEvent event(long owner, String identity, String key, AffiliationKind affiliation, int magnitude)
	{
		return new SocialEvent(owner, PhantomSocialModel.sha256("goal030b.event|" + owner + '|' + identity), key, SUBJECT, MINUTE, magnitude, PhantomSocialModel.sha256("goal030b.evidence|" + identity), new SocialEventContext(affiliation));
	}

	private static SocialEvent oldEvent(long owner, String identity, String key, int magnitude)
	{
		return new SocialEvent(owner, PhantomSocialModel.sha256("goal030b.event|" + owner + '|' + identity), key, SUBJECT, MINUTE, magnitude, PhantomSocialModel.sha256("goal030b.evidence|" + identity));
	}

	private static void record(PhantomSocialService service, SocialEvent event)
	{
		PhantomAssertions.assertEquals(Status.RECORDED, service.record(event).status(), "Goal 030B social event was not recorded.");
	}
	private static int relationship(PhantomSocialService service, long owner, String key)
	{
		return snapshot(service, owner).relationship().relationship().get(key);
	}

	private static int reputation(PhantomSocialService service, long owner, String key)
	{
		return snapshot(service, owner).relationship().reputation().get(key);
	}

	private static int memorySalience(PhantomSocialService service, long owner)
	{
		return snapshot(service, owner).memories().get(0).salience();
	}

	private static Map<String, Integer> agreements(PhantomSocialService service, long owner)
	{
		return snapshot(service, owner).relationship().agreements();
	}

	private static PhantomSocialModel.SocialSnapshot snapshot(PhantomSocialService service, long owner)
	{
		final var result = service.snapshot(owner, SUBJECT, 24, MINUTE);
		PhantomAssertions.assertTrue(result.available(), "Goal 030B social snapshot is unavailable.");
		return result.value();
	}

	private static String join(List<Integer> values)
	{
		return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
	}

	private static String affiliationRow(String source, String kind)
	{
		final String start = "		<affiliation kind=\"" + kind + "\">";
		final int startIndex = uniqueIndex(source, start);
		final String end = "		</affiliation>";
		final int endIndex = source.indexOf(end, startIndex);
		if (endIndex < 0)
		{
			throw new IllegalArgumentException("Affiliation row anchor is malformed.");
		}
		return source.substring(startIndex, endIndex + end.length());
	}

	private static String affiliationPolicySection(String source)
	{
		final String start = "	<affiliationMultipliers>";
		final int startIndex = uniqueIndex(source, start);
		final String end = "	</affiliationMultipliers>";
		final int endIndex = uniqueIndex(source, end);
		if (endIndex < startIndex)
		{
			throw new IllegalArgumentException("Affiliation policy section anchor is malformed.");
		}
		return source.substring(startIndex, endIndex + end.length());
	}

	private static String replaceExact(String source, String anchor, String replacement)
	{
		final int index = uniqueIndex(source, anchor);
		return source.substring(0, index) + replacement + source.substring(index + anchor.length());
	}

	private static int uniqueIndex(String source, String anchor)
	{
		final int index = source.indexOf(anchor);
		if ((index < 0) || (source.indexOf(anchor, index + anchor.length()) >= 0))
		{
			throw new IllegalArgumentException("Test mutation anchor is missing or duplicated.");
		}
		return index;
	}

	private static void rejectCatalog(PhantomTestContext context, String name, String content) throws Exception
	{
		final Path path = Files.createTempFile(context.reportsDirectory(), "social-030b1-invalid-" + name + '-', ".xml");
		try
		{
			Files.writeString(path, content, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSocialCatalog.load(path), "Invalid Goal 030B1 affiliation catalog was accepted: " + name);
		}
		finally
		{
			Files.deleteIfExists(path);
		}
	}

	private static void stop(PhantomSocialService service)
	{
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Goal 030B social service did not stop.");
	}

	private record Fixture(PhantomSocialService service, MemoryStore store)
	{
	}
}