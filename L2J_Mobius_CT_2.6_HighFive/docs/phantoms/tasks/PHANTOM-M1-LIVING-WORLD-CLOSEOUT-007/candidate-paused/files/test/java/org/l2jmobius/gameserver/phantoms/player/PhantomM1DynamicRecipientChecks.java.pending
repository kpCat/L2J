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

	/** Caller owns three fresh ordinary native Players and the NPC; managed variants consume the first two Players. */
	public static void partyChange(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc20534, Skill windStrike, boolean managed, boolean sealNewcomer) throws Exception
	{
		run(context, origin, newcomer, ordinary, npc20534, windStrike, false, managed, sealNewcomer);
	}

	public static void transferChange(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc20534, Skill hurricane, boolean managed, boolean sealNewcomer) throws Exception
	{
		run(context, origin, newcomer, ordinary, npc20534, hurricane, true, managed, sealNewcomer);
	}

	private static void run(PhantomTestContext context, Player origin, Player newcomer, Player ordinary, Monster npc, Skill magic, boolean transfer, boolean managed, boolean sealNewcomer) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Dynamic recipient fixture requires guarded native TEST.");
		PhantomAssertions.assertTrue(managed || !sealNewcomer, "INVALID Q12: an ordinary Player has no managed seal.");
		final String key = "Q12.dynamic." + (transfer ? "transfer" : "party") + "." + (managed ? sealNewcomer ? "sealed" : "open" : "ordinary");
		context.record(key + ".fixture", "INVALID_UNTIL_ACTUAL_RECIPIENT_CHANGE");
		final List<Player> players = List.of(origin, newcomer, ordinary);
		PhantomAssertions.assertEquals(3L, players.stream().map(Player::getObjectId).distinct().count(), "INVALID Q12: three distinct native identities required.");
		for (Player player : players)
		{
			PhantomAssertions.assertTrue(!player.isNativeWorkManaged() && World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline() && !player.isDead()
				&& !player.isInParty() && !player.hasSummon() && player.getInstanceId() == origin.getInstanceId() && player.isInSurroundingRegion(origin)
				&& player.calculateDistance3D(origin) < 900 && player.getLevel() == origin.getLevel(), "INVALID Q12: fresh same-level nearby native ordinary Players required.");
		}
		PhantomAssertions.assertTrue(npc != null && npc.getId() == 20534 && !npc.isDead() && !npc.isInvul() && npc.getInstanceId() == origin.getInstanceId()
			&& origin.isInSurroundingRegion(npc), "INVALID Q12: original native NPC20534 required.");
		PhantomAssertions.assertTrue(magic != null && SkillData.getInstance().getSkill(magic.getId(), magic.getLevel()) == magic
			&& magic.getId() == (transfer ? 1239 : 1177) && magic.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.MagicalDamage")), "INVALID Q12: original stock MagicalDamage skill required.");
		PhantomAssertions.assertTrue(transfer || origin.getKnownSkill(1177) == magic, "INVALID Q12: origin must know stock Wind Strike.");
		final Skill shield = transfer ? SkillData.getInstance().getSkill(528, 1) : null;
		PhantomAssertions.assertTrue(!transfer || shield != null && shield.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.TransferDamage"))
			&& origin.getEffectList().getBuffInfoBySkillId(528) == null && origin.getTransferingDamageTo() == null, "INVALID Q12: fresh original stock528 required.");
		final Creature actor = transfer ? npc : origin, target = transfer ? origin : npc;
		final var completion = new Completion(actor, target, magic);
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
				npc.disableCoreAI(true); npc.setCurrentMp(npc.getMaxMp()); npc.getStatus().stopHpMpRegeneration();
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
				if (transfer) { cast(actor, target, magic); }
				else { setup(first, null, () -> cast(actor, target, magic)); }
				PhantomAssertions.assertTrue(actor.isCastingNow() && completion.finished.getCount() == 1, "INVALID Q12: stock cast must be queued before native entry.");
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
				await(3000, () -> quiescent(newcomerLifetime), "INVALID Q12: newcomer setup retained counted work under the hold.");
				PhantomAssertions.assertTrue(completion.finished.getCount() == 1 && newcomer.getExp() == expBefore && newcomer.getSp() == spBefore && newcomer.getCurrentHp() == hpBefore && newcomer.getCurrentCp() == cpBefore, "INVALID Q12: recipient writer entered before release.");
				if (managed && sealNewcomer) { second.scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); PhantomAssertions.assertTrue(second.scope.sealed(), "INVALID Q12: newcomer failed to seal before native entry."); }
				if (transfer) { requirePointer(origin, newcomer); }
				context.record(key + ".fixture", "VALID_ACTUAL_STOCK_PUBLICATION_AND_DYNAMIC_RECIPIENT");
				context.record(key + ".workersHeld", gate.workerCount());
				context.record(key + ".exactPlayers", origin.getObjectId() + ":" + System.identityHashCode(origin) + "," + newcomer.getObjectId() + ":" + System.identityHashCode(newcomer) + "," + ordinary.getObjectId() + ":" + System.identityHashCode(ordinary));
				if (managed)
				{
					context.record(key + ".newcomerBeforeRelease", second.scope.snapshot());
					// A correct before-writer boundary fails without entering either recipient monitor.
					synchronized (newcomer)
					{
						synchronized (newcomer.getStatus())
						{
							gate.release();
							await(12_000, () -> originLifetime.scope.firstNativeIncident() != null && originLifetime.scope.outstanding() == 0, "Q12 dynamic failure was silent or waited for a recipient writer monitor.");
							context.record(key + ".failureBeforeRecipientMonitors", true);
						}
					}
				}
			}
			if (managed)
			{
				await(3000, () -> quiescent(originLifetime) && quiescent(newcomerLifetime), "Q12 rejected dynamic task retained accounting.");
				final var incident = first.scope.firstNativeIncident();
				PhantomAssertions.assertTrue(incident != null && incident.objectId() == origin.getObjectId() && incident.epoch() == first.scope.epoch() && incident.detail().contains("MagicUseTask") && incident.detail().contains("PlayerNativeWork"), "Q12 origin incident lost actual stock queued native lineage.");
				context.record(key + ".originIncident", incident); context.record(key + ".originLatestIncident", first.scope.latestNativeIncident());
				PhantomAssertions.assertTrue(newcomer.getExp() == expBefore && newcomer.getSp() == spBefore && newcomer.getCurrentHp() == hpBefore && newcomer.getCurrentCp() == cpBefore, "Q12 unregistered recipient received native EXP/SP or HP/CP.");
				PhantomAssertions.assertTrue(transfer ? origin.getCurrentHp() == originHpBefore && origin.getCurrentCp() == originCpBefore : origin.getExp() == originExpBefore && origin.getSp() == originSpBefore, "Q12 dynamic group failed after a partial original-recipient writer.");
				PhantomAssertions.assertFalse(first.scope.open(), "Q12 failed original lifetime reopened.");
				PhantomAssertions.assertThrows(IllegalStateException.class, origin::storeMe, "Q12 dynamic failure became successful canonical store.");
				PhantomAssertions.assertTrue(first.scope.firstNativeIncident() == incident, "Q12 store rejection replaced origin incident.");
				context.record(key + ".storeSuccess", false);
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
			try { actor.removeListener(completion.listener); actor.abortCast(); actor.abortAttack(); actor.stopMove(null); }
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
			await(3000, () -> scope.outstanding() == 0, "Q12 failed TEST lifetime still has counted native work.");
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
				if (!Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask") && frame.getMethodName().equals("run"))) { failure.set("Expected original MagicUseTask.run."); }
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
