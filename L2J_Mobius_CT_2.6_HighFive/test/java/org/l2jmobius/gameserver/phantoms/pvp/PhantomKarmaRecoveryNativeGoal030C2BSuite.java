/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.nio.file.Files;

import org.l2jmobius.gameserver.config.PvpConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomPlayerMaterializationSpike;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerFixture;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomKarmaRecoveryNativeGoal030C2BSuite implements PhantomTestSuite
{
	private static final long SEED = 30003035L;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomPlayerMaterializationSpike _primarySpike;
	private PhantomPlayerMaterializationSpike _counterpartSpike;
	private Player _primary;
	private Player _counterpart;
	private L2jPhantomKarmaRecoveryContext _context;
	private PhantomKarmaRecoveryPolicy _policy;

	@Override
	public String id()
	{
		return "karma-recovery-native-goal030c2b";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030C2B native seed changed.");
		_environment.initialize(context);
		_primarySpike = spike(_environment.primary());
		_counterpartSpike = spike(_environment.observer());
		_primarySpike.materialize();
		_counterpartSpike.materialize();
		_primary = _primarySpike.getPlayer();
		_counterpart = _counterpartSpike.getPlayer();
		final long levelTwentyExp = ExperienceData.getInstance().getExpForLevel(20);
		_primary.addExpAndSp(Math.max(0, levelTwentyExp - _primary.getExp()), 0);
		_counterpart.addExpAndSp(Math.max(0, levelTwentyExp - _counterpart.getExp()), 0);
		_context = new L2jPhantomKarmaRecoveryContext(profileId -> profileId == 1 ? _primary.getObjectId() : profileId == 2 ? _counterpart.getObjectId() : 0, ClanWarService.getInstance());
		_policy = PhantomKarmaRecoveryPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/pvp/high-five-karma-recovery-v1.xml"));
		context.record("karmaDropLimit", RatesConfig.KARMA_DROP_LIMIT);
		context.record("karmaRateDrop", RatesConfig.KARMA_RATE_DROP);
		context.record("karmaRateDropEquip", RatesConfig.KARMA_RATE_DROP_EQUIP);
		context.record("karmaRateDropEquipWeapon", RatesConfig.KARMA_RATE_DROP_EQUIP_WEAPON);
		context.record("karmaRateDropItem", RatesConfig.KARMA_RATE_DROP_ITEM);
		context.record("karmaPkLimit", PvpConfig.KARMA_PK_LIMIT);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_primarySpike != null)
		{
			_primarySpike.cleanup();
		}
		if (_counterpartSpike != null)
		{
			_counterpartSpike.cleanup();
		}
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-live-context-safe-yield", _ ->
		{
			_primary.setKarma(800);
			final var snapshot = _context.observe(1, _counterpart.getObjectId());
			PhantomAssertions.assertTrue(snapshot.available(), "Live recovery context was unavailable.");
			PhantomAssertions.assertEquals(800, snapshot.karma(), "Live karma was not canonical.");
			PhantomAssertions.assertEquals(_primary.getExp(), snapshot.currentExp(), "Live EXP was not canonical.");
			PhantomAssertions.assertEquals(0L, snapshot.expDebt(), "Fresh fixture unexpectedly had XP debt.");
			PhantomAssertions.assertEquals(600, snapshot.predictedKarmaAfterNativeDeath(), "Live death prediction changed.");
			PhantomAssertions.assertEquals(Decision.YIELD, _policy.evaluate(Source.ACTUAL_ATTACK, true, snapshot).decision(), "Safe live actual attack did not yield.");
		});
		registry.add("02-native-death-800-to-600-and-debt", context ->
		{
			final long before = _primary.getExp();
			PhantomAssertions.assertTrue(_primary.doDie(_counterpart), "Native external death was rejected.");
			PhantomAssertions.assertEquals(600, _primary.getKarma(), "Native 800 karma death did not produce 600.");
			PhantomAssertions.assertEquals(before, _primary.getExpBeforeDeath(), "Native death did not preserve the pre-death EXP watermark.");
			final var snapshot = _context.observe(1, _counterpart.getObjectId());
			PhantomAssertions.assertTrue(snapshot.expDebt() > 0, "Native death did not expose positive XP debt.");
			PhantomAssertions.assertEquals(Decision.NORMAL, _policy.evaluate(Source.ACTUAL_ATTACK, true, snapshot).decision(), "XP debt did not block another intentional yield.");
			context.record("nativeKarma800After", _primary.getKarma());
			context.record("nativeExpBeforeDeath", _primary.getExpBeforeDeath());
			context.record("nativeCurrentExpAfterDeath", _primary.getExp());
			context.record("nativeExpDebt", snapshot.expDebt());
		});
		registry.add("03-native-low-karma-clears", context ->
		{
			_counterpart.setKarma(100);
			PhantomAssertions.assertTrue(_counterpart.doDie(_primary), "Native low-karma external death was rejected.");
			PhantomAssertions.assertEquals(0, _counterpart.getKarma(), "Native 100 karma death did not clear karma.");
			final var snapshot = _context.observe(2, _primary.getObjectId());
			PhantomAssertions.assertEquals(Decision.NORMAL, _policy.evaluate(Source.ACTUAL_ATTACK, true, snapshot).decision(), "Clean recovery overlay remained active.");
			context.record("nativeKarma100After", _counterpart.getKarma());
		});
		registry.add("04-configured-drop-exposure-blocks", context ->
		{
			final int risk = L2jPhantomKarmaRecoveryContext.deathDropRiskBasisPoints(800, PvpConfig.KARMA_PK_LIMIT);
			final int maximumRate = Math.max(Math.max(RatesConfig.KARMA_RATE_DROP, RatesConfig.KARMA_RATE_DROP_EQUIP), Math.max(RatesConfig.KARMA_RATE_DROP_EQUIP_WEAPON, RatesConfig.KARMA_RATE_DROP_ITEM));
			final int expected = (RatesConfig.KARMA_DROP_LIMIT > 0) && (maximumRate > 0) ? Math.min(10000, maximumRate * 100) : 0;
			PhantomAssertions.assertEquals(expected, risk, "Configured native drop exposure calculation changed.");
			final var exposed = new PhantomKarmaRecoveryPolicy.Snapshot(true, 800, PvpConfig.KARMA_PK_LIMIT, 1000, 1000, 0, Math.max(1, risk), 600, false, false);
			PhantomAssertions.assertEquals(Decision.NORMAL, _policy.evaluate(Source.ACTUAL_ATTACK, true, exposed).decision(), "Configured drop exposure allowed voluntary death.");
			context.record("nativeDropRiskBasisPoints", risk);
		});
		registry.add("05-active-combat-is-not-cancelled", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java"));
			final int engageGuard = source.indexOf("stored.encounter().stage() == Stage.ENGAGE");
			final int recovery = source.indexOf("_karmaRecoveryContext.observe");
			PhantomAssertions.assertTrue((engageGuard >= 0) && (recovery > engageGuard), "Recovery overlay moved before active combat ownership.");
			final String overlay = source.substring(recovery, source.indexOf("final Outcome outcome", recovery));
			PhantomAssertions.assertFalse(overlay.contains("_combat.cancel"), "Recovery overlay cancels active combat synchronously.");
		});
	}

	private static PhantomPlayerMaterializationSpike spike(PhantomHeadlessPlayerFixture fixture)
	{
		return new PhantomPlayerMaterializationSpike(fixture.objectId(), PhantomIdentityLeaseRegistry.getInstance(), new HeadlessPlayerOutboundSession(16, 128, 32), new PhantomActionFacade(), PhantomPlayerMaterializationSpike.FailureInjector.none());
	}
}
