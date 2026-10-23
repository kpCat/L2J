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
public final class PhantomEconomy070CurseNative
{
	private static boolean _resume;
	private static Creature _queen;
	private static final StringBuffer _events = new StringBuffer();
	public static void main(String[] args) throws Exception
	{
		if ((args.length > 1 || args.length == 1 && !java.util.List.of("inspect", "resume").contains(args[0])) || !PhantomTask070Lane.enabled()) { throw new IllegalArgumentException("Own070 native curse check, inspect or explicit resume required."); }
		_resume = args.length == 1 && args[0].equals("resume");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path report = module.getParent().resolve(".phantom-local/task070/reports/curse-native-" + System.currentTimeMillis() + ".tsv");
		final var players = new ArrayList<Player>(); Servitor summon = null;
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer phantom = null;
		org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease lease = null;
		final StringBuilder evidence = new StringBuilder("stage\tvalue\n");
		try
		{
			evidence.append("loadedCreature\t").append(Creature.class.getProtectionDomain().getCodeSource().getLocation()).append('\n');
			evidence.append("loadedPlayer\t").append(Player.class.getProtectionDomain().getCodeSource().getLocation()).append('\n');
			evidence.append("classpath\t").append(System.getProperty("java.class.path")).append('\n');
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69007004L, module, report.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("ai/bosses/QueenAnt/QueenAnt.java"));
			final var queen = GrandBossManager.getInstance().getBoss(29001);
			_queen = queen;
			check(queen != null && !queen.isDead() && queen.isRaid(), "Own TEST Queen Ant is not alive; do not reset/respawn it for a result.");
			if (args.length == 1 && args[0].equals("inspect"))
			{
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task070curse' ORDER BY charId"); var rows = query.executeQuery())
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
			if (!_resume) { try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task070curse','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); } }
			final var preparedTank = actor("Curse070Tank", 0, 88, 6580, queen.getX() + 100, queen.getY(), queen.getZ());
			preparedTank.stopAllTasks(); preparedTank.storeMe(); preparedTank.deleteMe();
			phantom = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer(preparedTank.getObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(), new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(16, 128, 32));
			phantom.materialize(); final var tank = phantom.getPlayer(); players.add(tank); lease = phantom.tryAcquireAction();
			check(lease != null && tank.getNativeWorkOwner() != null && tank.hasHeadlessOutboundSession() && tank.getClient() == null, "Actual native Phantom owner required; no fake GameClient.");
			final var mage = actor("Curse070Mage", 10, 94, 6579, queen.getX() + 300, queen.getY(), queen.getZ()); players.add(mage);
			final var healer = actor("Curse070Heal", 10, 97, 6579, queen.getX() + 250, queen.getY() + 80, queen.getZ()); players.add(healer);
			final var owner = actor("Curse070Pet", 10, 96, 6579, queen.getX() + 250, queen.getY() - 80, queen.getZ()); players.add(owner);
			for (var skill : new int[][] {{1230, 28}, {1339, 1}, {1217, 33}, {1068, 1}}) { (skill[0] == 1339 || skill[0] == 1230 ? mage : healer).addSkill(SkillData.getInstance().getSkill(skill[0], skill[1]), true); }
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
			final double mageMp = mage.getCurrentMp();
			cast(mage, queen, 1230, 28, magic);
			cast(mage, queen, 1339, 1, magic);
			check(mage.getCurrentMp() < mageMp, "Magic did not spend native MP.");
			healer.setTarget(tank); final double healMp = healer.getCurrentMp();
			check(tank.getMaxHp() - tank.getCurrentHp() > 200, "No native battle damage to heal; no artificial HP debit permitted.");
			check(healer.useMagic(SkillData.getInstance().getSkill(1217, 33), true, false), "Overlevel native heal refused.");
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
			System.out.println("OWN070_CURSE_NATIVE_PASS " + report);
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
			evidence.append(_events);
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
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task070curse' AND char_name=? AND accesslevel=0"))
			{
				query.setString(1, name); try (var rows = query.executeQuery()) { check(rows.next(), "Retained actor missing."); player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous retained actor."); }
			}
		}
		else { player = Player.create(PlayerTemplateData.getInstance().getTemplate(root), "task070curse", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); }
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
		actor.addListener(new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getTarget() == target && event.getDamage() > 0) { damage.add(event.getDamage()); _events.append("damage\tactor=" + actor.getName() + ";skill=" + (event.getSkill() == null ? 0 : event.getSkill().getId()) + ";damage=" + event.getDamage() + ";dot=" + event.isDamageOverTime() + ";hpAtEvent=" + target.getCurrentHp() + '\n'); } }, PhantomEconomy070CurseNative.class));
		actor.addListener(new ConsumerEventListener(actor, EventType.ON_CREATURE_SKILL_USE, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillUse event) -> { _events.append("castStart\tactor=" + actor.getName() + ";skill=" + event.getSkill().getId() + ";target=" + event.getTarget().getName() + ";hp=" + event.getTarget().getCurrentHp() + '\n'); }, PhantomEconomy070CurseNative.class));
	}
	private static void cast(Player caster, Creature target, int id, int level, DoubleAdder damage) throws Exception
	{
		final var skill = SkillData.getInstance().getSkill(id, level);
		final long started = System.nanoTime(); final double before = damage.sum();
		caster.setTarget(target);
		_events.append("castRequest\tskill=" + id + ";hitTime=" + skill.getHitTime() + ";nativeTime=" + org.l2jmobius.gameserver.model.stats.Formulas.calcAtkSpd(caster, skill, skill.getHitTime() + skill.getCoolTime()) + ";magic=" + skill.isMagic() + ";element=" + skill.getElement() + ";mAtkSpd=" + caster.getMAtkSpd() + ";distance=" + caster.calculateDistance2D(target) + ";los=" + org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canSeeTarget(caster, target) + ";mp=" + caster.getCurrentMp() + '\n');
		check(caster.useMagic(skill, true, false), "Native cast refused skill=" + id);
		final long end = started + java.util.concurrent.TimeUnit.SECONDS.toNanos(22);
		double previousHp = target.getCurrentHp(); boolean hpWrite = false; long nextSample = 0;
		while (System.nanoTime() < end)
		{
			Thread.sleep(5); final double hp = target.getCurrentHp();
			if (hp < previousHp && damage.sum() > before) { hpWrite = true; }
			previousHp = hp;
			if (System.nanoTime() >= nextSample) { _events.append("castSample\tskill=" + id + ";elapsedMs=" + (System.nanoTime() - started) / 1000000 + ";bossHP=" + hp + ";damage=" + (damage.sum() - before) + ";" + state(caster) + '\n'); nextSample = System.nanoTime() + 250000000L; }
			if (hpWrite && damage.sum() > before && !caster.isCastingNow()) { break; }
		}
		check(damage.sum() > before && hpWrite, "No native damage event and sampled HP writer skill=" + id + "; see cast events; boss nurses/regen remain native.");
		_events.append("castEnd\tskill=" + id + ";elapsedMs=" + (System.nanoTime() - started) / 1000000 + ";damage=" + (damage.sum() - before) + ";bossHP=" + target.getCurrentHp() + ";effect=" + target.isAffectedBySkill(id) + '\n');
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
