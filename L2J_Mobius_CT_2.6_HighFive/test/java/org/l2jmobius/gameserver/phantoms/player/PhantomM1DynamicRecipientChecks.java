/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Changes native recipients after stock publication; failed TEST lifetimes never store or reopen. */
public final class PhantomM1DynamicRecipientChecks
{
	private PhantomM1DynamicRecipientChecks() { }

	/** Fault is injected after the stock NPC HP writer, under a real scheduled MagicUseTask. */
	public static void failedCastBody(PhantomTestContext context, Player first, Monster npc, Skill magic) throws Exception
	{
		try (var one = new NativeLifetime(first))
		{
			try
			{
				final double hp = npc.getCurrentHp();
				setup(one, null, () -> cast(first, npc, magic));
				await(12_000, () -> one.scope.firstNativeIncident() != null || !first.isCastingNow(), "N04 failed native cast has eternal CAST.");
				context.record("N04.actualCastBody", "hp=" + hp + "->" + npc.getCurrentHp() + ";cast=" + first.isCastingNow() + ";incident=" + one.scope.firstNativeIncident());
				PhantomAssertions.assertTrue(npc.getCurrentHp() < hp, "N04 fault happened after a real native HP writer.");
				PhantomAssertions.assertTrue(one.scope.firstNativeIncident() != null && one.scope.firstNativeIncident().detail().contains("TEST023_AFTER_NATIVE_HP_WRITER"), "RED N04: real owned cast body exception must retain its exact primary incident.");
				await(3000, () -> !first.isCastingNow() && one.scope.outstanding() == 0, "N04 exact failed cast must finalize without successful phase3 continuation.");
				PhantomAssertions.assertFalse(one.scope.open(), "N04 failed native owner must remain fenced.");
				PhantomAssertions.assertThrows(IllegalStateException.class, first::storeMe, "N04 failed native body cannot become canonical store success.");
			}
			finally { npc.abortAttack(); npc.abortCast(); npc.deleteMe(); first.abortAttack(); first.abortCast(); }
		}
	}

	/** Both callbacks are stock published tasks; an aborted older task must not cancel a newer cast. */
	public static void staleCast(PhantomTestContext context, Player first, Monster removed, Skill magic) throws Exception
	{
		final var learn = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(first.getPlayerClass()).values().stream().filter(entry -> entry.getSkillId() == 1184 && entry.getSkillLevel() == 1 && entry.getGetLevel() <= first.getLevel()).findFirst().orElseThrow();
		final Skill nextSkill = SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel());
		first.addSkill(nextSkill, true); // Legal stock Ice Bolt, before managed lifetime/baseline.
		final Monster next = new Monster(removed.getTemplate());
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(next.getTemplate()); spawn.setXYZ(removed.getX() + 15, removed.getY(), removed.getZ()); next.setSpawn(spawn);
		next.setCurrentHpMp(next.getMaxHp(), next.getMaxMp()); next.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
		try (var one = new NativeLifetime(first))
		{
			try
			{
				final double hp = next.getCurrentHp();
				try (var gate = new WorkerGate())
				{
					gate.acquire(); setup(one, null, () -> cast(first, removed, magic));
					PhantomAssertions.assertTrue(first.isCastingNow() && one.scope.outstanding() > 0, "N06 older native callback published.");
					final var futureField = Creature.class.getDeclaredField("_skillCast"); futureField.setAccessible(true);
					final var oldFuture = (ScheduledFuture<?>) futureField.get(first);
					final long oldDelay = oldFuture.getDelay(TimeUnit.MILLISECONDS);
					PhantomAssertions.assertTrue(oldDelay <= 8000, "INVALID N06: original launch exceeds bounded scheduler hold.");
					Thread.sleep(Math.max(0, oldDelay) + 50);
					PhantomAssertions.assertTrue(oldFuture.getDelay(TimeUnit.MILLISECONDS) <= 0 && first.isCastingNow(), "N06 old callback due but held before new cast.");
					setup(one, null, first::abortCast); removed.abortAttack(); removed.abortCast(); removed.deleteMe();
					context.record("N06.newCastEligibility", "oldReuse=" + first.isSkillDisabled(magic) + ";newAllowed=" + first.checkDoCastConditions(nextSkill) + ";mp=" + first.getCurrentMp());
					setup(one, null, () -> cast(first, next, nextSkill));
					PhantomAssertions.assertTrue(first.isCastingNow(), "N06 newer exact native cast started before old callback released.");
					gate.release();
				}
				await(12_000, () -> !first.isCastingNow() || one.scope.firstNativeIncident() != null, "N06 stale callback left eternal CAST.");
				context.record("N06.stale", "nextHp=" + hp + "->" + next.getCurrentHp() + ";cast=" + first.isCastingNow() + ";scope=" + one.scope.snapshot() + ";incident=" + one.scope.firstNativeIncident());
				PhantomAssertions.assertEquals(null, one.scope.firstNativeIncident(), "N06 exact cancellation does not poison lifetime.");
				PhantomAssertions.assertTrue(next.getCurrentHp() < hp && !first.isCastingNow(), "RED N06: old stock callback must not abort or strand newer cast on the next target.");
			}
			finally { next.abortAttack(); next.abortCast(); next.deleteMe(); first.abortAttack(); first.abortCast(); }
		}
	}

	/** Paired pre-write rollback, exact delayed owner fence and real body failure. */
	public static void admissionContract(PhantomTestContext context, Player first, Player second, String variant) throws Exception
	{
		try (var one = new NativeLifetime(first); var two = new NativeLifetime(second))
		{
			final double hp = second.getCurrentHp(); final java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
			if (variant.equals("sealed")) { two.scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); }
			final var origin = one.scope.reserve(null, "TEST023_PUBLISHED_EARNED", Semantics.EARNED);
			PhantomAssertions.assertTrue(origin != null && origin.tryStart(), "N04 exact earned origin running.");
			try (var original = PlayerNativeWork.enter(origin))
			{
				final Runnable writer = () -> { writes.incrementAndGet(); second.reduceCurrentHp(1, null, true, true, null); };
				if (variant.equals("strict-delayed"))
				{
					PhantomAssertions.assertThrows(IllegalStateException.class, () -> PlayerNativeWork.run(first, List.of(second), "TEST023_DELAYED_BODY", writer), "N03 generic delayed body must not freshly borrow newcomer.");
					PhantomAssertions.assertEquals(0, writes.get(), "N03 strict denial before any writer.");
					PhantomAssertions.assertTrue(one.scope.firstNativeIncident() != null && two.scope.firstNativeIncident() != null, "N03 delayed fence records its genuine lineage failure.");
				}
				else if (variant.equals("replaced-epoch"))
				{
					final var recipient = two.scope.reserve(null, "TEST023_CAPTURED_RECIPIENT", Semantics.EARNED);
					PhantomAssertions.assertTrue(recipient != null && recipient.tryStart(), "N03 original recipient exact running.");
					final var replacement = new PhantomNativeWorkScope(new Object(), second, two.identity, two.scope.epoch() + 1);
					try (var captured = PlayerNativeWork.enter(recipient))
					{
						second.detachNativeWorkOwner(two.scope); second.attachNativeWorkOwner(replacement);
						try { PhantomAssertions.assertEquals(PlayerNativeWork.NativeBoundaryOutcome.REJECTED_STALE, PlayerNativeWork.runAtNativeWriteBoundary(first, List.of(second), "TEST023_FRESH_WRITE", writer), "N03 stale frame never resolves replacement epoch."); }
						finally { second.detachNativeWorkOwner(replacement); second.attachNativeWorkOwner(two.scope); }
					}
					finally { recipient.complete(null); }
					PhantomAssertions.assertEquals(0, writes.get(), "N03 replaced epoch has no writer.");
					PhantomAssertions.assertEquals(0, replacement.outstanding(), "N03 replacement has no borrowed reservation.");
				}
				else if (variant.equals("earned-failure-drain"))
				{
					final double firstHp = first.getCurrentHp(); one.scope.recordFailure(new IllegalStateException("TEST023_PRIOR_NATIVE_FAILURE"));
					final var incident = one.scope.firstNativeIncident();
					final var outcome = PlayerNativeWork.runAtNativeWriteBoundary(first, List.of(first), "TEST023_ALREADY_CAPTURED_EARNED", () -> { writes.incrementAndGet(); first.reduceCurrentHp(1, null, true, true, null); });
					PhantomAssertions.assertEquals(PlayerNativeWork.NativeBoundaryOutcome.EXECUTED, outcome, "RED N04: already captured published earned native work must finish during failed-owner drain.");
					PhantomAssertions.assertTrue(writes.get() == 1 && first.getCurrentHp() < firstHp && one.scope.firstNativeIncident() == incident && !one.scope.open(), "N04 earned drain writes once without reopening or replacing failure.");
				}
				else if (variant.equals("body-failure"))
				{
					final var sentinel = new IllegalStateException("TEST023_NATIVE_BODY_FAILURE");
					try { PlayerNativeWork.runAtNativeWriteBoundary(first, List.of(second), "TEST023_FRESH_WRITE", () -> { writer.run(); throw sentinel; }); throw new AssertionError("N04 real native exception swallowed."); }
					catch (IllegalStateException actual) { PhantomAssertions.assertTrue(actual == sentinel, "N04 exact primary exception preserved."); }
					PhantomAssertions.assertEquals(1, writes.get(), "N04 real writer executed once without retry.");
					PhantomAssertions.assertTrue(second.getCurrentHp() < hp && one.scope.firstNativeIncident() != null && two.scope.firstNativeIncident() != null, "N04 started writer failure retained on all exact participants.");
				}
				else
				{
					final var outcome = PlayerNativeWork.runAtNativeWriteBoundary(first, List.of(second), "TEST023_FRESH_WRITE", writer);
					PhantomAssertions.assertEquals(variant.equals("sealed") ? PlayerNativeWork.NativeBoundaryOutcome.DEFERRED_BEFORE_WRITE : PlayerNativeWork.NativeBoundaryOutcome.EXECUTED, outcome, "N04 positive and sealed operation outcomes differ.");
					PhantomAssertions.assertEquals(variant.equals("sealed") ? 0 : 1, writes.get(), "N04 writer count is exact.");
					PhantomAssertions.assertTrue(variant.equals("sealed") ? second.getCurrentHp() == hp && two.scope.sealed() : second.getCurrentHp() < hp && two.scope.open(), "N04 stock HP delta or zero writes under sealed owner.");
					PhantomAssertions.assertEquals(null, one.scope.firstNativeIncident(), "N04 admission denial does not poison origin.");
				}
				PhantomAssertions.assertTrue(origin.isRunning() && one.scope.outstanding() == 1 && two.scope.outstanding() == 0, "N04 partial admission retired without cancelling published earned origin.");
			}
			finally { origin.complete(null); }
			context.record("N03/N04." + variant, "writes=" + writes.get() + ";first=" + one.scope.snapshot() + ";second=" + two.scope.snapshot());
		}
	}

	/** A second stock skill writes real HP after the first stock MagicUseTask was published. */
	public static void lateNativeDamage(PhantomTestContext context, Player first, Player second, Monster npc, Skill magic) throws Exception
	{
		lateNativeDamage(context, first, second, null, npc, magic);
	}

	public static void lateNativeDamage(PhantomTestContext context, Player first, Player second, Player ordinary, Monster npc, Skill magic) throws Exception
	{
		try (var one = new NativeLifetime(first); var two = new NativeLifetime(second))
		{
			final long firstExp = first.getExp(), secondExp = second.getExp(), ordinaryExp = ordinary == null ? 0 : ordinary.getExp();
			try
			{
			if (ordinary != null)
			{
				ordinary.setTarget(npc); ordinary.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, npc);
				await(12_000, () -> npc.getAggroList().get(ordinary) != null && npc.getAggroList().get(ordinary).getDamage() > 1, "INVALID N02: ordinary stock attack has not yet dealt reward-eligible damage.");
				ordinary.abortAttack(); ordinary.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ACTIVE);
			}
			try (var gate = new WorkerGate())
			{
				gate.acquire();
				setup(one, null, () -> cast(first, npc, magic));
				PhantomAssertions.assertTrue(first.isCastingNow() && one.scope.outstanding() > 0, "N02 original actual cast queued.");
				final double hp = npc.getCurrentHp();
				setup(two, null, () -> second.callSkill(magic, List.of(npc)));
				PhantomAssertions.assertTrue(npc.getCurrentHp() < hp && !npc.isDead() && npc.getAggroList().get(second) != null
					&& npc.getAggroList().get(second).getDamage() > 1, "N02 late participant must deal real stock damage without killing the original target.");
				context.record("N02.lateDamage", hp + "->" + npc.getCurrentHp() + ";damage=" + npc.getAggroList().get(second).getDamage());
				gate.release();
			}
			await(12_000, () -> !first.isCastingNow() || one.scope.firstNativeIncident() != null, "N02 native first cast did not finish.");
			context.record("N02.original", "casting=" + first.isCastingNow() + ";scope=" + one.scope.snapshot() + ";incident=" + one.scope.firstNativeIncident());
			PhantomAssertions.assertEquals(null, one.scope.firstNativeIncident(), "RED N02: lawful late native damaging OPEN participant must not poison an earned cast.");
			PhantomAssertions.assertFalse(first.isCastingNow(), "N02 actual native cast finalizer must complete.");
			PhantomAssertions.assertTrue(one.scope.open() && two.scope.open(), "N02 both exact lifetimes continue admitting native work.");
			PhantomAssertions.assertTrue(npc.isDead() && first.getExp() > firstExp && second.getExp() > secondExp, "N02 genuine damaging participants receive native death reward.");
			if (ordinary != null) { PhantomAssertions.assertTrue(ordinary.getExp() > ordinaryExp && npc.getAggroList().get(ordinary) != null && npc.getAggroList().get(ordinary).getDamage() > 1, "N02 ordinary third native attack and reward remain stock."); }
			context.record("N02.nativeRewards", "first=" + (first.getExp() - firstExp) + ";second=" + (second.getExp() - secondExp) + ";ordinary=" + (ordinary == null ? "NONE" : ordinary.getExp() - ordinaryExp));
			}
			finally
			{
				npc.abortAttack(); npc.abortCast(); npc.stopMove(null); npc.deleteMe();
				first.abortAttack(); first.abortCast(); second.abortAttack(); second.abortCast();
			}
		}
	}

	/** Caller owns three fresh ordinary native Players and the NPC; managed variants consume the first two Players. */
	public static void partyChange(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc20534, Skill windStrike, boolean managed, boolean sealNewcomer) throws Exception
	{
		run(context, origin, newcomer, ordinary, npc20534, windStrike, false, managed, sealNewcomer, false);
	}

	public static void transferChange(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc20534, Skill hurricane, boolean managed, boolean sealNewcomer) throws Exception
	{
		run(context, origin, newcomer, ordinary, npc20534, hurricane, true, managed, sealNewcomer, false);
	}

	public static void physicalTransfer(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc, boolean managed, boolean sealed) throws Exception
	{
		run(context, origin, newcomer, ordinary, npc, SkillData.getInstance().getSkill(1239, 1), true, managed, sealed, true);
	}

	private static void run(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc, Skill magic, boolean transfer, boolean managed, boolean sealNewcomer, boolean physical) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Dynamic recipient fixture requires guarded native TEST.");
		PhantomAssertions.assertTrue(managed || !sealNewcomer, "INVALID Q12: an ordinary Player has no managed seal.");
		final String key = "Q12.dynamic." + (physical ? "physical-transfer" : transfer ? "transfer" : "party") + "." + (managed ? sealNewcomer ? "sealed" : "open" : "ordinary");
		context.record(key + ".fixture", "INVALID_UNTIL_ACTUAL_RECIPIENT_CHANGE");
		final List<Player> players = List.of(origin, newcomer, ordinary);
		PhantomAssertions.assertEquals(3L, players.stream().map(Player::getObjectId).distinct().count(), "INVALID Q12: three distinct native identities required.");
		for (Player player : players)
		{
			PhantomAssertions.assertTrue(!player.isNativeWorkManaged() && World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline() && !player.isDead()
				&& !player.isInParty() && !player.hasSummon() && player.getInstanceId() == origin.getInstanceId() && player.isInSurroundingRegion(origin)
				&& player.calculateDistance3D(origin) < 900 && player.getLevel() == origin.getLevel(), "INVALID Q12: fresh same-level nearby native ordinary Players required.");
		}
		PhantomAssertions.assertTrue(npc != null && npc.getId() == (physical ? 20667 : 20534) && !npc.isDead() && !npc.isInvul() && npc.getInstanceId() == origin.getInstanceId()
			&& origin.isInSurroundingRegion(npc), "INVALID Q12: original native NPC20534 required.");
		PhantomAssertions.assertTrue(magic != null && SkillData.getInstance().getSkill(magic.getId(), magic.getLevel()) == magic
			&& magic.getId() == (transfer ? 1239 : 1177) && magic.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.MagicalDamage")), "INVALID Q12: original stock MagicalDamage skill required.");
		PhantomAssertions.assertTrue(transfer || origin.getKnownSkill(1177) == magic, "INVALID Q12: origin must know stock Wind Strike.");
		final Skill shield = transfer ? SkillData.getInstance().getSkill(528, 1) : null;
		PhantomAssertions.assertTrue(!transfer || shield != null && shield.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.TransferDamage"))
			&& origin.getEffectList().getBuffInfoBySkillId(528) == null && origin.getTransferingDamageTo() == null, "INVALID Q12: fresh original stock528 required.");
		final Creature actor = transfer ? npc : origin, target = transfer ? origin : npc;
		final var completion = new Completion(actor, target, physical ? null : magic);
		NativeLifetime first = null, second = null;
		Throwable primary = null;
		try
		{
			// Same read-confirmed raw-scope fixture as NativeScopeChecks: no ordinary pending producer crosses attachment.
			for (Player player : players) { player.stopAllTasks(); }
			if (managed) { first = new NativeLifetime(origin); second = new NativeLifetime(newcomer); }
			final NativeLifetime originLifetime = first, newcomerLifetime = second;
			setup(first, second, () ->
			{
				invite(origin, ordinary, 2);
				for (Player player : players) { player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.getStatus().stopHpMpRegeneration(); }
				npc.setCurrentMp(npc.getMaxMp()); npc.getStatus().stopHpMpRegeneration();
				if (transfer) { shield.applyEffects(ordinary, origin); }
				else { npc.setCurrentHp(1); }
			});
			PhantomAssertions.assertTrue(!newcomer.isInParty(), "INVALID Q12: newcomer joined before native publication.");
			if (transfer) { requirePointer(origin, ordinary); }
			await(3000, () -> quiescent(originLifetime) && quiescent(newcomerLifetime), "INVALID Q12: setup callbacks must finish before scheduled-pool hold.");
			final long expBefore = newcomer.getExp(), spBefore = newcomer.getSp(), originExpBefore = origin.getExp(), originSpBefore = origin.getSp();
			final double hpBefore = newcomer.getCurrentHp(), cpBefore = newcomer.getCurrentCp(), originHpBefore = origin.getCurrentHp(), originCpBefore = origin.getCurrentCp();
			actor.addListener(completion.listener);
			try (var gate = new WorkerGate())
			{
				gate.acquire();
				PhantomAssertions.assertTrue(PlayerNativeWork.inheritedPlayers().isEmpty(), "INVALID Q12: setup context contaminated native publication.");
				if (physical) { actor.setTarget(target); actor.doAttack(target); }
				else if (transfer) { cast(actor, target, magic); }
				else { setup(first, null, () -> cast(actor, target, magic)); }
				PhantomAssertions.assertTrue((physical ? actor.isAttackingNow() : actor.isCastingNow()) && completion.finished.getCount() == 1, "INVALID Q12: stock offense must be queued before native entry.");
				if (managed)
				{
					PhantomAssertions.assertTrue(first.scope.outstanding() > 0 && second.scope.outstanding() == 0 && PlayerNativeWork.current(first.scope) == null && PlayerNativeWork.current(second.scope) == null, "INVALID Q12: only the original managed recipient may be captured before change.");
					context.record(key + ".originQueued", first.scope.snapshot());
				}
				setup(first, second, () ->
				{
					invite(origin, newcomer, 3);
					if (transfer) { origin.stopSkillEffects(SkillFinishType.REMOVED, 528); shield.applyEffects(newcomer, origin); }
				});
				PhantomAssertions.assertTrue(origin.getParty() == newcomer.getParty() && origin.getParty() == ordinary.getParty() && origin.getParty().getMembers().size() == 3, "INVALID Q12: actual native party change failed.");
				if (transfer) { requirePointer(origin, newcomer); }
				// Membership publishes its own status callback. Release the stock scheduler before
				// awaiting it; the original cast still has its native launch/hit delay.
				gate.release();
				await(3000, () -> quiescent(newcomerLifetime), "INVALID Q12: newcomer membership callbacks did not finish.");
				PhantomAssertions.assertTrue(completion.finished.getCount() == 1 && newcomer.getExp() == expBefore && newcomer.getSp() == spBefore && newcomer.getCurrentHp() == hpBefore && newcomer.getCurrentCp() == cpBefore, "INVALID Q12: recipient writer entered before release.");
				if (managed && sealNewcomer) { second.scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); PhantomAssertions.assertTrue(second.scope.sealed(), "INVALID Q12: newcomer failed to seal before native entry."); }
				if (transfer) { requirePointer(origin, newcomer); }
				context.record(key + ".fixture", "VALID_ACTUAL_STOCK_PUBLICATION_AND_DYNAMIC_RECIPIENT");
				context.record(key + ".workersHeld", gate.workerCount());
				context.record(key + ".exactPlayers", origin.getObjectId() + ":" + System.identityHashCode(origin) + "," + newcomer.getObjectId() + ":" + System.identityHashCode(newcomer) + "," + ordinary.getObjectId() + ":" + System.identityHashCode(ordinary));
				if (managed) { context.record(key + ".newcomerBeforeWrite", second.scope.snapshot()); }
			}
			if (managed)
			{
				await(12_000, () -> !(physical ? actor.isAttackingNow() : actor.isCastingNow()) || originLifetime.scope.firstNativeIncident() != null, "Q12 exact native offense did not finish.");
				await(3000, () -> quiescent(originLifetime) && quiescent(newcomerLifetime), "Q12 rejected dynamic task retained accounting.");
				final var incident = first.scope.firstNativeIncident();
				context.record(key + ".originIncident", incident); context.record(key + ".originLatestIncident", first.scope.latestNativeIncident());
				PhantomAssertions.assertEquals(null, incident, "RED N03/N04: fresh native admission or before-write denial must preserve the captured origin lifetime.");
				PhantomAssertions.assertFalse(physical ? actor.isAttackingNow() : actor.isCastingNow(), "Q12 stock offense state must finalize after execution or safe denial.");
				if (sealNewcomer)
				{
				PhantomAssertions.assertTrue(newcomer.getExp() == expBefore && newcomer.getSp() == spBefore && newcomer.getCurrentHp() == hpBefore && newcomer.getCurrentCp() == cpBefore, "Q12 unregistered recipient received native EXP/SP or HP/CP.");
				PhantomAssertions.assertTrue(transfer ? origin.getCurrentHp() == originHpBefore && origin.getCurrentCp() == originCpBefore : origin.getExp() == originExpBefore && origin.getSp() == originSpBefore, "Q12 dynamic group failed after a partial original-recipient writer.");
				PhantomAssertions.assertTrue(second.scope.sealed(), "Q12 denied recipient must remain sealed.");
				}
				else
				{
				PhantomAssertions.assertTrue(transfer ? newcomer.getCurrentHp() < hpBefore : newcomer.getExp() > expBefore && newcomer.getSp() > spBefore, "Q12 lawful OPEN recipient must receive the stock native writer.");
				PhantomAssertions.assertTrue(second.scope.open(), "Q12 fresh admitted recipient must remain healthy.");
				}
				PhantomAssertions.assertTrue(first.scope.open(), "Q12 healthy original scope remains open after its exact cast.");
			}
			else
			{
				PhantomAssertions.assertTrue(completion.finished.await(10, TimeUnit.SECONDS), "INVALID ordinary dynamic control: native task did not complete.");
				PhantomAssertions.assertEquals(null, completion.failure.get(), "INVALID ordinary dynamic control: callback lineage failed.");
				PhantomAssertions.assertTrue(transfer ? newcomer.getCurrentHp() < hpBefore : npc.isDead() && newcomer.getExp() > expBefore && newcomer.getSp() > spBefore, "INVALID ordinary dynamic control: changed recipient received no stock writer.");
				context.record(key + ".nativeCompletion", completion.stack.get());
				context.record(key + ".nativeDelta", transfer ? hpBefore - newcomer.getCurrentHp() : newcomer.getExp() - expBefore);
			}
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			Throwable cleanup = null;
			try { actor.removeListener(completion.listener); actor.abortCast(); actor.abortAttack(); actor.stopMove(null); npc.abortAttack(); npc.abortCast(); npc.deleteMe(); }
			catch (Exception | Error failure) { cleanup = failure; }
			for (NativeLifetime lifetime : Arrays.asList(second, first))
			{
				if (lifetime == null) { continue; }
				try { lifetime.close(); }
				catch (Exception | Error failure) { if (cleanup == null) { cleanup = failure; } else { cleanup.addSuppressed(failure); } }
			}
			if (!managed)
			{
				try { if (transfer) { origin.stopSkillEffects(SkillFinishType.REMOVED, 528); } PartyInvitationService.getInstance().leave(newcomer); PartyInvitationService.getInstance().leave(ordinary); PartyInvitationService.getInstance().leave(origin); }
				catch (Exception | Error failure) { if (cleanup == null) { cleanup = failure; } else { cleanup.addSuppressed(failure); } }
			}
			if (cleanup != null) { if (primary != null) { primary.addSuppressed(cleanup); } else if (cleanup instanceof Exception failure) { throw failure; } else { throw (Error) cleanup; } }
		}
	}

	private static boolean quiescent(NativeLifetime lifetime) { return lifetime == null || lifetime.scope.outstanding() == 0; }
	private static void requirePointer(Player origin, Player caster) { PhantomAssertions.assertTrue(origin.getTransferingDamageTo() == caster && origin.getEffectList().getBuffInfoBySkillId(528) != null && origin.calcStat(Stat.TRANSFER_DAMAGE_TO_PLAYER, 0, null, null) > 0, "INVALID Q12: original stock528 did not bind the actual caster/native transfer stat."); }
	private static void cast(Creature actor, Creature target, Skill skill) { actor.setTarget(target); PhantomAssertions.assertTrue(skill.getTargetList(actor).contains(target) && actor.getCurrentMp() >= actor.getStat().getMpConsume(skill) + actor.getStat().getMpInitialConsume(skill), "INVALID Q12: original stock target/MP eligibility failed."); actor.doCast(skill); }
	private static void invite(Player origin, Player recipient, int count)
	{
		final var service = PartyInvitationService.getInstance(); final var invitation = service.invite(origin, recipient, PartyDistributionType.FINDERS_KEEPERS.getId());
		try { PhantomAssertions.assertTrue(invitation.delivered(), "INVALID Q12: native invitation rejected: " + invitation.outcome()); final var response = service.respond(recipient, Response.ACCEPT, invitation.identity()); PhantomAssertions.assertTrue(response.accepted() && response.party() == origin.getParty() && response.party() == recipient.getParty() && response.party().getMembers().size() == count, "INVALID Q12: native membership rejected: " + response.outcome()); }
		finally { if (invitation.identity() != null) { service.cancel(invitation.identity()); } }
	}
	private static void setup(NativeLifetime first, NativeLifetime second, Runnable action)
	{
		if (first == null) { action.run(); return; }
		final var ticket = first.scope.reserve(null, "TEST_DYNAMIC_RECIPIENT_SETUP", Semantics.CANCELLABLE);
		PhantomAssertions.assertTrue(ticket != null && ticket.tryStart(), "INVALID Q12: exact native setup admission failed.");
		Throwable failure = null;
		try (var context = PlayerNativeWork.enter(ticket)) { if (second == null) { action.run(); } else { setup(second, null, action); } }
		catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
		finally { ticket.complete(failure); }
	}
	private static void await(long millis, BooleanSupplier condition, String message) throws Exception { final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10L); } PhantomAssertions.assertTrue(condition.getAsBoolean(), message); }

	private static final class NativeLifetime implements AutoCloseable
	{
		final Player player; final PhantomIdentityLeaseRegistry.Lease identity; final PhantomNativeWorkScope scope;
		NativeLifetime(Player value)
		{
			player = value; identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(player.getObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(identity != null, "INVALID Q12: exact native identity lease unavailable.");
			scope = new PhantomNativeWorkScope(new Object(), player, identity, System.nanoTime());
			try { player.attachNativeWorkOwner(scope); }
			catch (RuntimeException | Error failure) { identity.close(); throw failure; }
		}
		@Override public void close() throws Exception
		{
			await(12_000, () -> scope.outstanding() == 0, "Q12 failed TEST lifetime still has counted native work.");
			player.stopAllTasks();
			if (scope.pendingTimers() > 0)
			{
				try { scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); }
				catch (IllegalStateException failure) { PhantomAssertions.assertTrue(scope.firstNativeIncident() != null && scope.outstanding() == 0 && scope.pendingTimers() == 0, "Q12 TEST disposal could not stop native producers: " + failure); }
			}
			PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.pendingTimers() == 0, "Q12 failed TEST disposal lacks quiescence.");
			player.deleteMe(); player.detachNativeWorkOwner(scope); identity.close();
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(player.getObjectId()), "Q12 consumed TEST Player remains in World.");
		}
	}

	private static final class Completion
	{
		final CountDownLatch finished = new CountDownLatch(1); final AtomicReference<String> failure = new AtomicReference<>(), stack = new AtomicReference<>(); final ConsumerEventListener listener;
		Completion(Creature actor, Creature target, Skill magic)
		{
			listener = new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
			{
				if (event.getAttacker() != actor || event.getTarget() != target || event.getSkill() != magic || event.isDamageOverTime()) { return; }
				final var frames = Thread.currentThread().getStackTrace();
				if (!Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature." + (magic == null ? "HitTask" : "MagicUseTask")) && frame.getMethodName().equals("run"))) { failure.set("Expected original stock offense task."); }
				stack.set(Arrays.stream(frames).limit(24).map(frame -> frame.getClassName() + "." + frame.getMethodName()).reduce((left, right) -> left + ">" + right).orElse("")); finished.countDown();
			}, finished);
		}
	}

	/** Bounded actual pool gate from SharedRecipients/QueuedWork; no pool replacement. */
	private static final class WorkerGate implements AutoCloseable
	{
		private final int _count = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		private final CountDownLatch _started, _release = new CountDownLatch(1);
		private final List<ScheduledFuture<?>> _workers = new ArrayList<>();
		private final AtomicReference<String> _failure = new AtomicReference<>();
		WorkerGate() { PhantomAssertions.assertTrue(_count > 0 && _count <= 128, "INVALID Q12: pool outside existing1..128 gate bound."); _started = new CountDownLatch(_count); }
		int workerCount() { return _count; }
		void acquire() throws Exception
		{
			for (int i = 0; i < _count; i++)
			{
				final var task = ThreadPool.schedule(() ->
				{
					_started.countDown();
					try { if (!_release.await(10, TimeUnit.SECONDS)) { _failure.compareAndSet(null, "Worker hold expired."); } }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); _failure.compareAndSet(null, "Worker interrupted."); }
				}, 0);
				PhantomAssertions.assertTrue(task != null, "INVALID Q12: native TEST worker rejected."); _workers.add(task);
			}
			PhantomAssertions.assertTrue(_started.await(5, TimeUnit.SECONDS), "INVALID Q12: scheduled workers did not all enter hold.");
			PhantomAssertions.assertEquals(null, _failure.get(), "INVALID Q12: native scheduled hold failed.");
		}
		void release() { _release.countDown(); }
		@Override public void close() throws Exception
		{
			release(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			try { for (var worker : _workers) { worker.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); } PhantomAssertions.assertEquals(null, _failure.get(), "INVALID Q12: scheduled hold expired/failed."); }
			finally { _workers.forEach(worker -> worker.cancel(false)); }
		}
	}
}
