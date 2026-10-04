/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

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
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Native secondary recipients are sampled before task entry, then proved by actual stock writers. */
public final class PhantomM1SharedRecipientsChecks
{
	private PhantomM1SharedRecipientsChecks() { }

	public record Managed(Player player, PhantomMaterializationService materialization, long profileId) { }

	public static void partyExp(PhantomTestContext context, Managed attacker, Managed recipient, Monster target20534, Skill offensiveSkill) throws Exception
	{
		run(context, attacker.player(), recipient.player(), attacker, recipient, target20534, offensiveSkill, false);
	}

	public static void transferDamage(PhantomTestContext context, Managed receiver, Managed transferCaster, Monster npc20534, Skill incomingMagic) throws Exception
	{
		run(context, receiver.player(), transferCaster.player(), receiver, transferCaster, npc20534, incomingMagic, true);
	}

	public static void ordinaryPartyExp(PhantomTestContext context, Player attacker, Player recipient, Monster target20534, Skill offensiveSkill) throws Exception
	{
		run(context, attacker, recipient, null, null, target20534, offensiveSkill, false);
	}

	public static void ordinaryTransferDamage(PhantomTestContext context, Player receiver, Player caster, Monster npc20534, Skill incomingMagic) throws Exception
	{
		run(context, receiver, caster, null, null, npc20534, incomingMagic, true);
	}

	private static void run(PhantomTestContext context, Player first, Player second, Managed managedFirst, Managed managedSecond, Monster npc, Skill magic, boolean transfer) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Shared recipients require guarded headless TEST.");
		PhantomAssertions.assertTrue(first != second && first.getObjectId() != second.getObjectId(), "INVALID Q12: two distinct actual Player identities required.");
		final PhantomNativeWorkScope firstScope = scope(first, managedFirst), secondScope = scope(second, managedSecond);
		final String key = "Q12." + (transfer ? "thirdTransfer" : "sharedExp") + (managedSecond == null ? ".ordinary" : ".managed");
		context.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		PhantomAssertions.assertTrue(npc != null && npc.getId() == 20534 && !npc.isDead() && !npc.isInvul(), "INVALID Q12: fresh actual stock NPC20534 required.");
		PhantomAssertions.assertTrue(!first.isInParty() && !second.isInParty() && !first.hasSummon() && !second.hasSummon(), "INVALID Q12: fresh two-member party without additional summon recipients required.");
		PhantomAssertions.assertTrue(first.getInstanceId() == second.getInstanceId() && first.getInstanceId() == npc.getInstanceId() && first.calculateDistance3D(second) < 900
			&& first.isInSurroundingRegion(second) && first.isInSurroundingRegion(npc), "INVALID Q12: actual participants must be near each other in the same native instance/region.");
		PhantomAssertions.assertTrue(magic != null && SkillData.getInstance().getSkill(magic.getId(), magic.getLevel()) == magic && magic.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.MagicalDamage")),
			"INVALID Q12: supplied stock skill must use the original MagicalDamage handler.");
		if (!transfer) { PhantomAssertions.assertTrue(magic.getId() == 1177 && first.getKnownSkill(1177) == magic, "INVALID Q12: party attacker must know the exact stock Wind Strike."); }
		final Skill shield = transfer ? SkillData.getInstance().getSkill(528, 1) : null;
		if (transfer)
		{
			// A controlled stock1239 cast tests the native task pipeline; it does not claim NPC20534's ordinary AI uses that skill.
			PhantomAssertions.assertTrue(magic.getId() == 1239, "INVALID Q12: stock Hurricane required; weak Wind Strike can truncate to zero transfer behind Shield of Faith's native M.Def bonus.");
			PhantomAssertions.assertTrue(shield != null && first.getEffectList().getBuffInfoBySkillId(528) == null && first.getTransferingDamageTo() == null && second.getTransferingDamageTo() == null,
				"INVALID Q12: fresh original stock Shield of Faith transfer state required.");
			PhantomAssertions.assertTrue(shield.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.TransferDamage")), "INVALID Q12: original stock TransferDamage handler unavailable.");
		}
		final Creature actor = transfer ? npc : first, target = transfer ? first : npc;
		final var completion = new NativeCompletion(actor, target, magic, second);
		Throwable primary = null;
		try
		{
			withMembers(managedFirst, managedSecond, () ->
			{
				formParty(first, second);
				first.setCurrentHp(first.getMaxHp()); first.setCurrentMp(first.getMaxMp()); first.getStatus().stopHpMpRegeneration();
				second.setCurrentHp(second.getMaxHp()); second.setCurrentMp(second.getMaxMp()); second.getStatus().stopHpMpRegeneration();
				npc.disableCoreAI(true); npc.setCurrentMp(npc.getMaxMp()); npc.getStatus().stopHpMpRegeneration();
				if (!transfer) { npc.setCurrentHp(1); }
			});
			final long expBefore = second.getExp(), spBefore = second.getSp();
			final double secondHpBefore = second.getCurrentHp();
			actor.addListener(completion.listener);
			final int queued;
			try (var gate = new ScheduledWorkerGate())
			{
				gate.acquire();
				if (transfer)
				{
					withMembers(managedFirst, managedSecond, () -> shield.applyEffects(second, first));
					PhantomAssertions.assertTrue(first.getTransferingDamageTo() == second && first.calcStat(Stat.TRANSFER_DAMAGE_TO_PLAYER, 0, null, null) > 0,
						"INVALID Q12: original stock528 did not bind the exact third caster and transfer stat.");
					PhantomAssertions.assertTrue(actor.getCurrentMp() >= actor.getStat().getMpConsume(magic) + actor.getStat().getMpInitialConsume(magic), "INVALID Q12: actual NPC stock cast lacks its native MP cost.");
				}
				await(() -> (firstScope == null || firstScope.outstanding() == 0) && (secondScope == null || secondScope.outstanding() == 0), "INVALID Q12: pre-existing native tickets hide queued accounting.");
				PhantomAssertions.assertFalse(actor.isCastingNow() || actor.isAttackingNow(), "INVALID Q12: native publisher already busy.");
				if (transfer)
				{
					PhantomAssertions.assertTrue(PlayerNativeWork.inheritedPlayers().isEmpty(), "INVALID Q12: NPC must not borrow either managed ActionLease.");
					cast(actor, target, magic);
				}
				else if (managedFirst != null)
				{
					try (var action = managedFirst.materialization().tryAcquireAction(managedFirst.profileId()).orElseThrow())
					{
						PhantomAssertions.assertTrue(action.player() == first, "INVALID Q12: actor ActionLease changed identity."); cast(actor, target, magic);
					}
				}
				else { cast(actor, target, magic); }
				PhantomAssertions.assertTrue(actor.isCastingNow(), "INVALID Q12: actual native cast frontend rejected publication.");
				queued = secondScope == null ? 0 : secondScope.outstanding();
				context.record(key + ".scheduledWorkersHeld", gate.workerCount());
				context.record(key + ".baselineOutstanding", 0); context.record(key + ".secondaryOutstandingAfterLeaseBeforeEntry", queued);
				if (firstScope != null)
				{
					context.record(key + ".primaryQueuedSnapshot", firstScope.snapshot());
					PhantomAssertions.assertTrue(firstScope.outstanding() > 0 && PlayerNativeWork.current(firstScope) == null, "INVALID Q12: primary queued native work missing or publication lease still open.");
				}
				if (secondScope != null) { context.record(key + ".secondaryExactEpoch", secondScope.epoch()); context.record(key + ".secondaryQueuedSnapshot", secondScope.snapshot()); PhantomAssertions.assertEquals(null, PlayerNativeWork.current(secondScope), "INVALID Q12: secondary setup lease remained open."); }
				PhantomAssertions.assertEquals(1L, completion.finished.getCount(), "INVALID Q12: native damage entered before worker release.");
				PhantomAssertions.assertTrue(transfer ? second.getCurrentHp() == secondHpBefore : second.getExp() == expBefore && second.getSp() == spBefore, "INVALID Q12: authoritative recipient changed before actual queued callback entry.");
			}
			PhantomAssertions.assertTrue(completion.finished.await(10, TimeUnit.SECONDS), "INVALID Q12: actual stock native task did not complete.");
			PhantomAssertions.assertEquals(null, completion.failure.get(), "INVALID Q12: stock task observation failed.");
			context.record(key + ".nativeTaskStack", completion.stack.get()); context.record(key + ".nativeDamage", completion.damage.get());
			if (transfer)
			{
				PhantomAssertions.assertTrue(first.getTransferingDamageTo() == second && first.getEffectList().getBuffInfoBySkillId(528) != null && completion.secondHp.get() < secondHpBefore,
					"INVALID Q12: no actual stock528 third-caster HP transfer after native queued cast.");
				context.record(key + ".nativeThirdCasterHpLoss", secondHpBefore - completion.secondHp.get());
			}
			else
			{
				PhantomAssertions.assertTrue(npc.isDead() && second.getExp() > expBefore && second.getSp() > spBefore, "INVALID Q12: actual native party kill did not award shared EXP and SP.");
				context.record(key + ".nativeSharedExp", second.getExp() - expBefore); context.record(key + ".nativeSharedSp", second.getSp() - spBefore);
			}
			context.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
			context.record(key + ".exactPlayerIdentities", first.getObjectId() + ":" + System.identityHashCode(first) + "," + second.getObjectId() + ":" + System.identityHashCode(second));
			if (secondScope != null) { PhantomAssertions.assertTrue(queued > 0, transfer ? "Q12 third transfer caster was not reserved before actual NPC MagicUseTask entry." : "Q12 shared party EXP recipient was not reserved before actual lethal MagicUseTask entry."); }
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				actor.removeListener(completion.listener); actor.abortCast(); actor.abortAttack(); actor.stopMove(null);
				withMembers(managedFirst, managedSecond, () ->
				{
					if (transfer) { first.stopSkillEffects(SkillFinishType.REMOVED, 528); }
					PartyInvitationService.getInstance().leave(second); PartyInvitationService.getInstance().leave(first);
				});
			}
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
	}

	private static PhantomNativeWorkScope scope(Player player, Managed managed)
	{
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline() && !player.isDead(), "INVALID Q12: exact live native World Player required.");
		if (managed == null) { PhantomAssertions.assertTrue(!player.isNativeWorkManaged(), "INVALID ordinary control: keep actual REAL Player identity without managed flag replacement."); return null; }
		PhantomAssertions.assertTrue(managed.player() == player && player.isNativeWorkManaged() && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID Q12: exact managed scope required.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(scope.isCurrent() && scope.player() == player, "INVALID Q12: stale managed lifetime.");
		return scope;
	}

	private static void withMembers(Managed first, Managed second, Runnable action)
	{
		if (first == null) { action.run(); return; }
		try (var one = first.materialization().tryAcquireAction(first.profileId()).orElseThrow(); var two = second.materialization().tryAcquireAction(second.profileId()).orElseThrow())
		{
			PhantomAssertions.assertTrue(one.player() == first.player() && two.player() == second.player(), "INVALID Q12: member setup leases changed exact identities."); action.run();
		}
	}

	private static void formParty(Player first, Player second)
	{
		final var service = PartyInvitationService.getInstance();
		final var invite = service.invite(first, second, PartyDistributionType.FINDERS_KEEPERS.getId());
		try
		{
			PhantomAssertions.assertTrue(invite.delivered(), "INVALID Q12: actual native party invitation failed: " + invite.outcome());
			final var accepted = service.respond(second, Response.ACCEPT, invite.identity());
			PhantomAssertions.assertTrue(accepted.accepted() && first.getParty() == accepted.party() && second.getParty() == accepted.party() && accepted.party().getMembers().size() == 2,
				"INVALID Q12: exact two-member native party acceptance failed: " + accepted.outcome());
		}
		finally { if (invite.identity() != null) { service.cancel(invite.identity()); } }
	}

	private static void cast(Creature actor, Creature target, Skill magic)
	{
		actor.setTarget(target);
		PhantomAssertions.assertTrue(magic.getTargetList(actor).contains(target), "INVALID Q12: actual stock target handler rejected its explicit native target."); actor.doCast(magic);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static final class NativeCompletion
	{
		final CountDownLatch finished = new CountDownLatch(1);
		final AtomicReference<String> failure = new AtomicReference<>(), stack = new AtomicReference<>();
		final AtomicReference<Double> damage = new AtomicReference<>(), secondHp = new AtomicReference<>();
		final ConsumerEventListener listener;
		NativeCompletion(Creature actor, Creature target, Skill magic, Player second)
		{
			listener = new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
			{
				if (event.getAttacker() != actor || event.getTarget() != target || event.getSkill() != magic || event.isDamageOverTime()) { return; }
				final var frames = Thread.currentThread().getStackTrace();
				if (!Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask") && frame.getMethodName().equals("run"))) { failure.compareAndSet(null, "Expected actual native MagicUseTask.run stack."); }
				stack.set(Arrays.stream(frames).limit(24).map(frame -> frame.getClassName() + "." + frame.getMethodName()).reduce((left, right) -> left + ">" + right).orElse(""));
				damage.set(event.getDamage()); secondHp.set(second.getCurrentHp()); finished.countDown();
			}, finished);
		}
	}

	/** Same bounded native scheduled-pool gate as PhantomM1QueuedWorkChecks; no scheduler setting replacement. */
	private static final class ScheduledWorkerGate implements AutoCloseable
	{
		private final int _workerCount = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		private final CountDownLatch _started, _release = new CountDownLatch(1);
		private final List<ScheduledFuture<?>> _workers = new ArrayList<>();
		private final AtomicReference<String> _failure = new AtomicReference<>();
		ScheduledWorkerGate()
		{
			PhantomAssertions.assertTrue(_workerCount > 0 && _workerCount <= 128, "INVALID queued native fixture: scheduled pool must be within 1..128 workers."); _started = new CountDownLatch(_workerCount);
		}
		int workerCount() { return _workerCount; }
		void acquire() throws Exception
		{
			for (int index = 0; index < _workerCount; index++)
			{
				final var future = ThreadPool.schedule(() ->
				{
					_started.countDown();
					try { if (!_release.await(10, TimeUnit.SECONDS)) { _failure.compareAndSet(null, "Scheduled TEST worker release timed out."); } }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); _failure.compareAndSet(null, "Scheduled TEST worker interrupted."); }
				}, 0);
				PhantomAssertions.assertTrue(future != null, "INVALID queued native fixture: scheduled worker was rejected."); _workers.add(future);
			}
			PhantomAssertions.assertTrue(_started.await(5, TimeUnit.SECONDS), "INVALID queued native fixture: all actual scheduled workers must enter the hold.");
			PhantomAssertions.assertEquals(null, _failure.get(), "INVALID queued native fixture: hold expired before native publication.");
		}
		@Override public void close() throws Exception
		{
			_release.countDown(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			try
			{
				for (var worker : _workers) { worker.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
				PhantomAssertions.assertEquals(null, _failure.get(), "INVALID queued native fixture: scheduled worker failed.");
			}
			finally { _workers.forEach(worker -> worker.cancel(false)); }
		}
	}
}
