/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.DoubleAdder;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.managers.GrandBossManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** One bounded actual Queen Ant episode: overlevel melee, magic, heal/buff and owned summon. */
public final class PhantomPlayable069CurseNative
{
	private static boolean _resume;
	private static Creature _queen;
	public static void main(String[] args) throws Exception
	{
		if ((args.length > 1 || args.length == 1 && !java.util.List.of("inspect", "resume").contains(args[0])) || !PhantomTask069Lane.enabled()) { throw new IllegalArgumentException("Own069 native curse check, inspect or explicit resume required."); }
		_resume = args.length == 1 && args[0].equals("resume");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path report = module.getParent().resolve(".phantom-local/task069/reports/curse-native-" + System.currentTimeMillis() + ".tsv");
		final var players = new ArrayList<Player>(); Servitor summon = null;
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer phantom = null;
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease lease = null;
		final StringBuilder evidence = new StringBuilder("stage\tvalue\n");
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006904L, module, report.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("ai/bosses/QueenAnt/QueenAnt.java"));
			final var queen = GrandBossManager.getInstance().getBoss(29001);
			_queen = queen;
			check(queen != null && !queen.isDead() && queen.isRaid(), "Own TEST Queen Ant is not alive; do not reset/respawn it for a result.");
			if (args.length == 1 && args[0].equals("inspect"))
			{
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task069curse' ORDER BY charId"); var rows = query.executeQuery())
				{
					while (rows.next())
					{
						final var actor = Player.load(rows.getInt(1)); players.add(actor);
						final String state = actor.getName() + ";hp=" + actor.getCurrentHp() + '/' + actor.getMaxHp() + ";mp=" + actor.getCurrentMp() + '/' + actor.getMaxMp() + ";dead=" + actor.isDead() + ";xyz=" + actor.getLocation() + ";mAtkSpd=" + actor.getMAtkSpd() + ";queenLoS=" + org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canSeeTarget(actor, queen) + ";curse4215=" + actor.isAffectedBySkill(4215) + ";curse4515=" + actor.isAffectedBySkill(4515);
						evidence.append("retained\t").append(state).append('\n'); System.out.println(state);
					}
				}
				return;
			}
			if (!_resume) { try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task069curse','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); } }
			final var preparedTank = actor("Curse069Tank", 0, 88, 6580, queen.getX() + 100, queen.getY(), queen.getZ());
			preparedTank.stopAllTasks(); preparedTank.storeMe(); preparedTank.deleteMe();
			phantom = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer(preparedTank.getObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(), new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(16, 128, 32));
			phantom.materialize(); final var tank = phantom.getPlayer(); players.add(tank); lease = phantom.tryAcquireAction();
			check(lease != null && tank.getNativeWorkOwner() != null && tank.hasHeadlessOutboundSession() && tank.getClient() == null, "Actual native Phantom owner required; no fake GameClient.");
			final var mage = actor("Curse069Mage", 10, 94, 6579, queen.getX() + 300, queen.getY(), queen.getZ()); players.add(mage);
			final var healer = actor("Curse069Heal", 10, 97, 6579, queen.getX() + 250, queen.getY() + 80, queen.getZ()); players.add(healer);
			final var owner = actor("Curse069Pet", 10, 96, 6579, queen.getX() + 250, queen.getY() - 80, queen.getZ()); players.add(owner);
			for (var skill : new int[][] {{1339, 1}, {1217, 1}, {1068, 1}}) { (skill[0] == 1339 ? mage : healer).addSkill(SkillData.getInstance().getSkill(skill[0], skill[1]), true); }
			summon = new Servitor(NpcData.getInstance().getTemplate(14176), owner); summon.setLifeTime(3600000); summon.setLifeTimeRemaining(3600000); owner.setPet(summon);
			summon.setCurrentHp(summon.getMaxHp()); summon.setCurrentMp(summon.getMaxMp()); summon.spawnMe(queen.getX() + 110, queen.getY() + 40, queen.getZ());
			evidence.append("fixture\tclasses88/94/97/96 level80; actual Phantom tank; learned1339:1/1217:1/1068:1; stock S gear/expertise; native Mew14176; resume=").append(_resume).append(";retained tankHP=").append(tank.getCurrentHp()).append("; no HP/MP refill during resume or measurement\n");
			final DoubleAdder melee = new DoubleAdder(), magic = new DoubleAdder(), petDamage = new DoubleAdder();
			listen(tank, queen, melee); listen(mage, queen, magic); listen(summon, queen, petDamage);
			final double meleeHp = queen.getCurrentHp();
			tank.setTarget(queen); tank.doAttack(queen);
			await(() -> melee.sum() > 0 && queen.getCurrentHp() < meleeHp, 6000, "Overlevel melee has no event plus applied HP reduction.");
			tank.abortAttack(); tank.getAI().setIntention(Intention.IDLE);
			final var ai = ScriptManager.getInstance().getScript("QueenAnt");
			for (var player : players) { ai.onAggroRangeEnter(queen, player, false); uncurssed(player); }
			ai.onAggroRangeEnter(queen, owner, true); uncurssed(summon);
			mage.setTarget(queen); final double mageMp = mage.getCurrentMp();
			final double magicHp = queen.getCurrentHp();
			check(mage.useMagic(SkillData.getInstance().getSkill(1339, 1), true, false), "Overlevel native magic refused.");
			evidence.append("castAccepted\t").append(state(mage)).append(";queenHP=").append(magicHp).append('\n');
			// Fire Vortex6000ms at native casting speed166 needs about12s; the old8s fixture deadline was invalid.
			await(() -> magic.sum() > 0 && queen.getCurrentHp() < magicHp, 18000, "Overlevel magic has no event plus applied HP reduction.");
			check(mage.getCurrentMp() < mageMp, "Magic did not spend native MP.");
			healer.setTarget(tank); final double healMp = healer.getCurrentMp();
			check(tank.getMaxHp() - tank.getCurrentHp() > 200, "No native battle damage to heal; no artificial HP debit permitted.");
			check(healer.useMagic(SkillData.getInstance().getSkill(1217, 1), true, false), "Overlevel native heal refused.");
			// A finished/aborted casting flag and initial MP debit cannot prove healing.
			// Require a sampled positive HP writer larger than the ordinary regen tick.
			awaitHeal(tank, 18000);
			check(healer.getCurrentMp() < healMp, "Heal did not spend native MP.");
			healer.setTarget(tank); check(healer.useMagic(SkillData.getInstance().getSkill(1068, 1), true, false), "Overlevel native buff refused.");
			await(() -> tank.isAffectedBySkill(1068), 18000, "Native support buff did not apply.");
			final double petHp = queen.getCurrentHp(); summon.setTarget(queen); summon.doAttack(queen);
			await(() -> petDamage.sum() > 0 && queen.getCurrentHp() < petHp, 6000, "Overlevel owned summon has no event plus applied HP reduction.");
			for (var player : players) { uncurssed(player); } uncurssed(summon);
			evidence.append("native\tmelee=").append(melee.sum()).append(";magic=").append(magic.sum()).append(";summon=").append(petDamage.sum()).append(";mageMP=").append(mageMp).append("->").append(mage.getCurrentMp()).append(";healMP=").append(healMp).append("->").append(healer.getCurrentMp()).append(";MightApplied=true;curse4215/4515=false\n");
			System.out.println("OWN069_CURSE_NATIVE_PASS " + report);
		}
		catch (Throwable failure)
		{
			evidence.append("failure\t").append(failure).append('\n');
			if (_queen != null) { evidence.append("queen\t").append(state(_queen)).append('\n'); }
			for (var actor : players) { evidence.append("actor\t").append(state(actor)).append('\n'); }
			if (summon != null) { evidence.append("summon\t").append(state(summon)).append('\n'); }
			throw failure;
		}
		finally
		{
			Files.writeString(report, evidence);
			if (summon != null) { summon.abortAttack(); summon.abortCast(); summon.deleteMe(); }
			if (phantom != null && phantom.getPlayer() != null) { phantom.getPlayer().getAI().setIntention(Intention.IDLE); phantom.getPlayer().abortAttack(); phantom.getPlayer().abortCast(); }
			if (lease != null) { lease.close(); }
			if (phantom != null) { phantom.cleanup(); }
			for (var player : players) { if (phantom != null && player == players.getFirst()) { continue; } player.abortAttack(); player.abortCast(); player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static Player actor(String name, int root, int profession, int weapon, int x, int y, int z) throws Exception
	{
		Player player;
		if (_resume)
		{
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task069curse' AND char_name=? AND accesslevel=0"))
			{
				query.setString(1, name); try (var rows = query.executeQuery()) { check(rows.next(), "Retained actor missing."); player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous retained actor."); }
			}
		}
		else { player = Player.create(PlayerTemplateData.getInstance().getTemplate(root), "task069curse", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); }
		check(player != null && player.getClient() == null && !player.hasHeadlessOutboundSession() && !player.isGM(), "Ordinary Player required.");
		if (!_resume) { player.setPlayerClass(profession); player.setBaseClass(profession); player.getStat().setExp(player.getStat().getExpForLevel(80)); player.getStat().setLevel((byte) 80); }
		check(!player.isDead() && player.getLevel() == 80 && player.getActiveClass() == profession, "Retained native class/life differs; do not revive/refill for the test.");
		player.addSkill(SkillData.getInstance().getSkill(239, 6), true);
		if (!_resume) { player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, weapon, 1, player, false)); }
		for (int armor : profession == 88 ? new int[] {6373, 6374, 6375, 6376, 6378} : new int[] {6383, 6384, 6385, 6386})
		{
			var item = player.getInventory().getItemByItemId(armor); if (item == null) { item = player.addItem(ItemProcessType.REWARD, armor, 1, player, false); } player.getInventory().equipItem(item);
		}
		if (!_resume) { player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp()); }
		player.setOnlineStatus(true, false); player.spawnMe(x, y, z); return player;
	}

	private static void listen(Creature actor, Creature target, DoubleAdder damage)
	{
		actor.addListener(new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getTarget() == target && event.getDamage() > 0) { damage.add(event.getDamage()); } }, PhantomPlayable069CurseNative.class));
	}
	private static void uncurssed(Creature actor) { check(!actor.isAffectedBySkill(4215) && !actor.isAffectedBySkill(4515) && !actor.isParalyzed() && !actor.isMuted(), "Native raid curse/effect remained on " + actor.getName()); }
	private static String state(Creature actor) { return actor.getName() + ";hp=" + actor.getCurrentHp() + '/' + actor.getMaxHp() + ";mp=" + actor.getCurrentMp() + '/' + actor.getMaxMp() + ";dead=" + actor.isDead() + ";casting=" + actor.isCastingNow() + ";intention=" + actor.getAI().getIntention() + ";target=" + actor.getTarget() + ";xyz=" + actor.getLocation() + ";curse4215=" + actor.isAffectedBySkill(4215) + ";curse4515=" + actor.isAffectedBySkill(4515); }
	private static void awaitHeal(Creature target, long millis) throws Exception
	{
		final double ordinaryTick = org.l2jmobius.gameserver.model.stats.Formulas.calcHpRegen(target);
		final long regenNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(org.l2jmobius.gameserver.model.stats.Formulas.getRegeneratePeriod(target));
		long sampledAt = System.nanoTime(); final long end = sampledAt + millis * 1000000L; double previous = target.getCurrentHp();
		while (System.nanoTime() < end)
		{
			Thread.sleep(10); final long now = System.nanoTime(); final double current = target.getCurrentHp();
			if (now >= end) { break; }
			if (now - sampledAt < regenNanos && current - previous > Math.max(50, ordinaryTick * 2)) { return; }
			previous = current; sampledAt = now;
		}
		throw new AssertionError("No applied native healing HP increase above ordinary regen.");
	}
	private static void await(java.util.function.BooleanSupplier condition, long millis, String message) throws Exception
	{
		final long end = System.nanoTime() + millis * 1000000L;
		while (!condition.getAsBoolean() && System.nanoTime() < end) { Thread.sleep(20); }
		check(condition.getAsBoolean(), message);
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
