/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.model.zone.type.NoPvPZone;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.combat.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;

/** Own071 fixture only. Actual production combat owner; no direct damage/HP grant during measurement. */
public final class PhantomCombat071Native
{
	private record Role(String name, int rootClass, int profession, WeaponType weapon, PhantomCombatMode mode) { }
	private PhantomCombat071Native() { }

	/** Caller owns infrastructure, handlers, report persistence and DatabaseFactory shutdown. */
	public static void run(StringBuilder report) throws Exception
	{
		run(report,false,false,false);
	}
	public static void resume(StringBuilder report) throws Exception
	{
		run(report,true,false,false);
	}
	public static void roles(StringBuilder report) throws Exception
	{
		run(report,true,true,false);
	}
	public static void remainingRoles(StringBuilder report) throws Exception
	{
		run(report,true,true,true);
	}
	public static void combatTail(StringBuilder report) throws Exception
	{
		run(report,true,true,true,true);
	}
	public static void kamaelRetained(StringBuilder report) throws Exception
	{
		run(report,true,true,true,true,true);
	}
	private static void run(StringBuilder report,boolean resume,boolean rolesOnly,boolean remainingRoles) throws Exception
	{
		run(report,resume,rolesOnly,remainingRoles,false);
	}
	private static void run(StringBuilder report,boolean resume,boolean rolesOnly,boolean remainingRoles,boolean tail) throws Exception
	{
		run(report,resume,rolesOnly,remainingRoles,tail,false);
	}
	private static void run(StringBuilder report,boolean resume,boolean rolesOnly,boolean remainingRoles,boolean tail,boolean kamaelOnly) throws Exception
	{
		check(PhantomTask071Lane.enabled(), "Explicit own071 native infrastructure required.");
		if(rolesOnly)
		{
			try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login='task071combat' AND accessLevel=-1");var rows=query.executeQuery()){check(rows.next()&&rows.getInt(1)==1,"Existing disabled own071 combat account required; no account insert.");}
			if(kamaelOnly)
			{
				check(retainedCharacter("Combat071Kamael")>0&&retainedCharacter("Enemy071Kamael")>0,"Exact retained Kamael and enemy required; no new actors.");
				report.append("combat071.kamaelRetained\tOnly existing Kamael/enemy; actual stock learned offensive subset restricted after materialization;no skill adds;no HP/MP/CP/gear/shot refill;independent one-use native10410 Soul-source fixture explicitly authorized because Souls reset on logout;previous native two bottles and damage with Souls10->10 remain PARTIAL evidence\n");
			}
			else if(tail)
			{
				for(String name:List.of("Combat071Dagger","Enemy071Dagger","Combat071Mage","Enemy071Mage")){check(retainedCharacter(name)>0,"Exact retained tail actor required: "+name);}
				for(String name:List.of("Combat071Tank","Enemy071Tank","Combat071Kamael","Enemy071Kamael")){check(retainedCharacter(name)==0,"Tail new role already exists; no replay/refill: "+name);}
				report.append("combat071.tail\tRetained Dagger/Mage and exact enemies; no HP/MP/CP/gear/shot/Soul refill; prior Mage single-hit timeout and prior Dagger cleanup failure remain separate evidence; Archer/support/grades untouched; only absent Tank/Kamael created\n");
			}
			else if(remainingRoles)
			{
				try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE account_name='task071combat' AND accesslevel=0 AND char_name IN ('Combat071Dagger','Enemy071Dagger','Support071','Support071Ally')");var rows=query.executeQuery()){check(rows.next()&&rows.getInt(1)==4,"Exact retained support and Dagger actors required; no replay/refill.");}
				try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE account_name='task071combat' AND (char_name LIKE 'Combat071%' OR char_name LIKE 'Enemy071%' OR char_name LIKE 'Support071%') AND char_name NOT IN ('Combat071Dagger','Enemy071Dagger','Support071','Support071Ally')");var rows=query.executeQuery()){check(rows.next()&&rows.getInt(1)==0,"A remaining role already exists; no replay/refill permitted.");}
				report.append("combat071.remainingRoles\tExact retained support/Dagger actors verified; Archer/Mage/Tank/Kamael absent; grade/support/Dagger untouched; prior Dagger measurement remains incomplete separate evidence; no five-role PASS asserted\n");
			}
			else
			{
				try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE account_name='task071combat' AND (char_name LIKE 'Combat071%' OR char_name LIKE 'Enemy071%' OR char_name LIKE 'Support071%')");var rows=query.executeQuery()){check(rows.next()&&rows.getInt(1)==0,"Role/support fixtures already exist; no replay, refill or reseed permitted.");}
				report.append("combat071.rolesOnly\tExisting own071 account; zero priorCombat071/Enemy071/Support071 actors verified; grade fixture untouched; only previously unreached role/support scenes start\n");
			}
		}
		if(!resume) { try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task071combat','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); } }
		final var repository = PhantomProfileRepository.open();
		final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		check(materialization.start(), "Native materialization did not start.");
		final var backend = new L2jCombatBackend(materialization, () -> null);
		final var combat = new PhantomCombatService(backend, new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(2));
		combat.start();
		final List<Player> ordinary = new ArrayList<>();
		try
		{
			if(resume&&!rolesOnly)
			{
				try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task071combat' AND char_name='Supply071Grade' AND accesslevel=0"))
				{
					try(var rows=query.executeQuery()){check(rows.next(),"Retained grade fixture missing.");final Player grade=Player.load(rows.getInt(1));check(!rows.next(),"Ambiguous retained grade fixture.");ordinary.add(grade);PhantomSupply071Native.resumeGrades(grade,report);}
				}
			}
			else if(!rolesOnly) { final Player grade = fixture("Supply071Grade", 0, 0, WeaponType.SWORD, ordinary); PhantomSupply071Native.verifyGrades(grade, report); }
			if(!remainingRoles){support(materialization, repository, ordinary, report);}
			int tailFailures=0;
			for (Role role : List.of(new Role("Dagger", 0, 93, WeaponType.DAGGER, PhantomCombatMode.MELEE_PHYSICAL), new Role("Archer", 0, 92, WeaponType.BOW, PhantomCombatMode.RANGED_PHYSICAL), new Role("Mage", 10, 94, WeaponType.BLUNT, PhantomCombatMode.RANGED_MAGIC), new Role("Tank", 0, 90, WeaponType.SWORD, PhantomCombatMode.MELEE_PHYSICAL), new Role("Kamael", 123, 132, WeaponType.RAPIER, PhantomCombatMode.MELEE_PHYSICAL)))
			{
				if(kamaelOnly?!role.name().equals("Kamael"):tail?role.name().equals("Archer"):remainingRoles&&role.name().equals("Dagger")){continue;}
				long roleProfile=0;
				try
				{
				final boolean retained=kamaelOnly||tail&&(role.name().equals("Dagger")||role.name().equals("Mage"));
				final int character;
				final long profile;
				if(retained){character=retainedCharacter("Combat071"+role.name());profile=repository.findByCharacterObjectId(character).orElseThrow().profileId();}
				else
				{
					final Player prepared = fixture("Combat071" + role.name(), role.rootClass(), role.profession(), role.weapon(), ordinary);
					if(role.name().equals("Tank")){prepared.setCurrentCp(1);for(int id:new int[]{5591,5592}){prepared.addItem(ItemProcessType.REWARD,id,2,prepared,false);}report.append("combat071.autoCpFixture\tTank lowCP1 and finite5591x2/5592x2 declared before materialization/measurement;not purchased or earned\n");}
					prepared.setXYZ(point());prepared.stopAllTasks();prepared.storeMe();prepared.deleteMe();ordinary.remove(prepared);character=prepared.getObjectId();profile=repository.create(character).profileId();
				}
				roleProfile=profile;
				check(materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Own071 role failed materialization: " + role.name());
				final Player actor = World.getInstance().getPlayer(character);
				if(role.name().equals("Kamael")&&!kamaelOnly){soulBottles(materialization,profile,actor,report);}
				final Player defender = retained?Player.load(retainedCharacter("Enemy071"+role.name())):fixture("Enemy071" + role.name(), 0, 90, WeaponType.SWORD, ordinary);
				check(defender!=null&&!defender.isDead()&&!actor.isDead(),"Native tail participants must remain alive; no pool reset.");if(retained){ordinary.add(defender);}
				defender.setOnlineStatus(true, false); defender.spawnMe(actor.getX() + 80, actor.getY(), actor.getZ());
				actor.setTarget(defender); defender.setTarget(actor);
				if(kamaelOnly){soulBottles(materialization,profile,actor,report,1,true);actor.setTarget(defender);restrictNativeSoulSkill(actor,defender,report);}
				final AtomicInteger skillEvents = new AtomicInteger();
				final AtomicInteger positionalEvents = new AtomicInteger();
				final var actualSkillIds=new java.util.concurrent.ConcurrentSkipListSet<Integer>();
				final AtomicBoolean reuseObserved = new AtomicBoolean();
				final AtomicBoolean autoCpReuse=new AtomicBoolean();final double[] autoCpPeak={actor.getCurrentCp()};
				final long cpSmallBefore=actor.getInventory().getInventoryItemCount(5591,-1),cpLargeBefore=actor.getInventory().getInventoryItemCount(5592,-1);
				final int cpSmallObject=role.name().equals("Tank")?actor.getInventory().getItemByItemId(5591).getObjectId():0,cpLargeObject=role.name().equals("Tank")?actor.getInventory().getItemByItemId(5592).getObjectId():0;
				final int initialX=actor.getX(),initialY=actor.getY();
				final var listener = new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == actor && event.getTarget() == defender && event.getSkill() != null && event.getDamage() > 0) { skillEvents.incrementAndGet();actualSkillIds.add(event.getSkill().getId());if(PhantomPvpSkillSafety.requiresRear(event.getSkill())){positionalEvents.incrementAndGet();} } }, actor);
				actor.addListener(listener);
				final var selected = L2jCombatBackend.nativePvpSkills(actor, role.mode(), 16);
				check(!selected.isEmpty(), "UNCOVERED_ROLE native learned skill selection: " + role.name());
				final long shotsBefore = shotCount(actor, role.mode()); final double mpBefore = actor.getCurrentMp(); final int soulsBefore = actor.getChargedSouls();
				final double targetPool = defender.getCurrentHp() + defender.getCurrentCp();
				report.append("combat071.fixture\trole=").append(role.name()).append(";profile=").append(profile).append(";nativeClass=").append(actor.getActiveClass()).append(";weapon=").append(actor.getActiveWeaponItem().getId()).append(";known=").append(selected).append(";finiteStockShots=").append(shotsBefore).append(";SoulsFixture=").append(soulsBefore).append(";not earned gear/skills/currency\n");
				// Opponent starts a real ordinary native attack. Reactive reason has a factual source.
				final double poolBeforeEnemy=actor.getCurrentHp()+actor.getCurrentCp();
				if(tail){nativeFirstAttack(actor,defender,report);}
				else{defender.doAttack(actor);await(() -> actor.getCurrentHp() + actor.getCurrentCp() < poolBeforeEnemy, 6000, "Native enemy first attack produced no damage.");}
				defender.abortAttack(); defender.getAI().setIntention(Intention.IDLE);
				final var observation=combat.observePvp(profile,List.of(defender.getObjectId()),16,16).orElseThrow();
				check(observation.supportedModes().getFirst()==role.mode(),"Native role preference selected a secondary magic/melee capability instead of its actual archetype: "+role.name());
				check(observation.targets().stream().anyMatch(value->value.target().objectId()==defender.getObjectId()&&value.actualAttacker()&&value.canonicalContextAllowed()),"Production causal observation did not confirm the real native attacker.");
				final String authority = org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.sha256("own071.native.attack", actor.getObjectId(), defender.getObjectId());
				final var request = new PhantomPvpCombatRequest(profile, defender.getObjectId(), Source.ACTUAL_ATTACK, authority, role.mode(), false, true, 70, 20_000, () -> false);
				check(combat.startPvpSession(request).accepted(), "Production PvP owner refused " + role.name());
				final double cpBeforeAutomatic=actor.getCurrentCp();autoCpPeak[0]=cpBeforeAutomatic;
				boolean effect = false;
				try
				{
					await(() -> { if(selected.stream().anyMatch(skill -> actor.isSkillDisabled(actor.getKnownSkill(skill.skillId())))){reuseObserved.set(true);} if(role.name().equals("Tank")){autoCpPeak[0]=Math.max(autoCpPeak[0],actor.getCurrentCp());if(actor.getItemRemainingReuseTime(cpSmallObject)>0||actor.getItemRemainingReuseTime(cpLargeObject)>0){autoCpReuse.set(true);}} return kamaelOnly?skillEvents.get()>0&&actor.getChargedSouls()<soulsBefore:tail&&role.name().equals("Dagger")?positionalEvents.get()>0:skillEvents.get() > 0 && defender.getCurrentHp() + defender.getCurrentCp() < targetPool || selected.stream().anyMatch(skill -> defender.isAffectedBySkill(skill.skillId())); }, 20_000, "UNCOVERED_ROLE no native skill damage/debuff: " + role.name());
					if(role.name().equals("Tank"))
					{
						await(()->{autoCpPeak[0]=Math.max(autoCpPeak[0],actor.getCurrentCp());if(actor.getItemRemainingReuseTime(cpSmallObject)>0||actor.getItemRemainingReuseTime(cpLargeObject)>0){autoCpReuse.set(true);}return actor.getInventory().getInventoryItemCount(5591,-1)+actor.getInventory().getInventoryItemCount(5592,-1)<cpSmallBefore+cpLargeBefore&&autoCpPeak[0]>=cpBeforeAutomatic+40&&autoCpReuse.get();},1500,"Actual CombatService PvP did not auto-consume/restore/throttle native CP.");
						report.append("combat071.autoCp\tactual PvP session5591=").append(cpSmallBefore).append("->").append(actor.getInventory().getInventoryItemCount(5591,-1)).append(";5592=").append(cpLargeBefore).append("->").append(actor.getInventory().getInventoryItemCount(5592,-1)).append(";CP=").append(cpBeforeAutomatic).append("->peak").append(autoCpPeak[0]).append(";nativeReuseObserved=").append(autoCpReuse.get()).append(";finite declared inventory;no directCP write in episode\n");
					}
					effect = true;
				}
				finally
				{
					if(tail){report.append("combat071.tailMeasured\trole=").append(role.name()).append(";events=").append(skillEvents.get()).append(";actualSkillIds=").append(actualSkillIds).append(";positionalEvents=").append(positionalEvents.get()).append(";targetPool=").append(targetPool).append("->").append(defender.getCurrentHp()+defender.getCurrentCp()).append(";MP=").append(mpBefore).append("->").append(actor.getCurrentMp()).append(";shots=").append(shotsBefore).append("->").append(shotCount(actor,role.mode())).append(";souls=").append(soulsBefore).append("->").append(actor.getChargedSouls()).append(";reuse=").append(reuseObserved.get()).append(";moved=").append(Math.hypot((long)actor.getX()-initialX,(long)actor.getY()-initialY)).append(";rearOrSide=").append(!actor.isInFrontOf(defender)).append('\n');}
					combat.cancel(profile);
					await(() -> {combat.consumeTerminal(profile);return !combat.hasClaim(profile);}, 6000, "PvP native action cleanup retained claim after terminal consumption.");
					actor.removeListener(listener);
				}
				check(effect && reuseObserved.get() && actor.getCurrentMp() < mpBefore && shotCount(actor, role.mode()) < shotsBefore, "Native skill lacks observed reuse, MP or shot debit.");
				final double moved=Math.hypot((long)actor.getX()-initialX,(long)actor.getY()-initialY);
				if(role.name().equals("Dagger")){check(moved>20&&!actor.isInFrontOf(defender),"Dagger positive case has no actual rear/side movement.");}
				if(role.name().equals("Archer")){check(moved>20&&actor.calculateDistance3D(defender)>=200,"Archer positive case did not actually keep distance.");}
				report.append("combat071.native\trole=").append(role.name()).append(";skillDamageEvents=").append(skillEvents.get()).append(";targetPool=").append(targetPool).append("->").append(defender.getCurrentHp()+defender.getCurrentCp()).append(";MP=").append(mpBefore).append("->").append(actor.getCurrentMp()).append(";shots=").append(shotsBefore).append("->").append(shotCount(actor, role.mode())).append(";souls=").append(soulsBefore).append("->").append(actor.getChargedSouls()).append(";native reuse=").append(reuseObserved.get()).append(";actualMoved=").append(moved).append(";targetDistance=").append(actor.calculateDistance3D(defender)).append(";rearOrSide=").append(!actor.isInFrontOf(defender)).append('\n');
				if (!tail&&role.name().equals("Tank")) { await(()->actor.getItemRemainingReuseTime(cpSmallObject)<=0&&actor.getItemRemainingReuseTime(cpLargeObject)<=0,1500,"Automatic CP reuse did not retire before independent handler control.");cp(backend, profile, actor, report); }
				if (role.name().equals("Kamael")) { check(actor.getChargedSouls() < soulsBefore, "Kamael cast did not debit its actual native Souls."); }
				if(!tail){noSupply(combat, profile, actor, defender, request, report);}
				check(materialization.dematerialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Native role did not store cleanly.");
				}
				catch(Exception|AssertionError failure)
				{
					if(!tail){throw failure;}
					tailFailures++;report.append("combat071.tailPartial\trole=").append(role.name()).append(";failure=").append(failure).append(";no retry/refill;next previously unrun role continues\n");
					if(roleProfile>0){final long failedProfile=roleProfile;combat.cancel(failedProfile);await(()->{combat.consumeTerminal(failedProfile);return !combat.hasClaim(failedProfile);},6000,"Tail failed action did not drain.");materialization.dematerialize(failedProfile);}
				}
			}
			check(tailFailures==0,"Tail has "+tailFailures+" partial roles; inspect individual native evidence.");
		}
		finally
		{
			combat.beginStop(); await(combat::finishStop, 6000, "Combat native STOP failed.");
			check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "Materialization native STOP failed.");
			for (Player player : ordinary) { player.abortAttack(); player.abortCast(); player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
		}
	}

	private static void restrictNativeSoulSkill(Player actor,Player target,StringBuilder report)
	{
		check(!actor.isFakePlayer()&&actor.getActiveWeaponItem()!=null,"Real retained native actor and equipped weapon required.");
		final var chosen=actor.getAllSkills().stream().filter(skill->PhantomPvpSkillSafety.supports(skill,PhantomCombatMode.MELEE_PHYSICAL,actor.isTransformed())&&skill.isDamage()&&skill.getMaxSoulConsumeCount()>0&&skill.getMaxSoulConsumeCount()<=5&&!actor.isSkillDisabled(skill)&&actor.getCurrentMp()>=actor.getStat().getMpConsume(skill)+actor.getStat().getMpInitialConsume(skill)&&actor.getCurrentHp()>skill.getHpConsume()&&skill.checkCondition(actor,target,false)).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.skill.Skill::getMaxSoulConsumeCount).thenComparingInt(org.l2jmobius.gameserver.model.skill.Skill::getId)).orElseThrow(()->new AssertionError("No native currently legal learned soul-consuming physical damage skill <=5; PARTIAL."));
		report.append("combat071.nativeSoulChoice\tid=").append(chosen.getId()).append(";level=").append(chosen.getLevel()).append(";soulMaxConsumeCount=").append(chosen.getMaxSoulConsumeCount()).append(";native minimum required=").append(chosen.getMaxSoulConsumeCount()).append(";separate minimum API absent;currentSouls=").append(actor.getChargedSouls()).append(";native condition=true;existing learned only\n");
		check(actor.getChargedSouls()>=chosen.getMaxSoulConsumeCount(),"Single native bottle produced insufficient Souls; no second bottle authorized, retained debit remains PARTIAL.");
		final var removed=new ArrayList<Integer>();
		for(var skill:List.copyOf(actor.getAllSkills())){if(skill.getId()!=chosen.getId()&&PhantomPvpSkillSafety.supports(skill,PhantomCombatMode.MELEE_PHYSICAL,actor.isTransformed())){removed.add(skill.getId());actor.removeSkill(skill.getId());}}
		report.append("combat071.nativeSoulSubset\tchosen=").append(chosen.getId()).append(':').append(chosen.getLevel()).append(";removed existing offensive IDs=").append(removed).append(";no skill added;no resource/gear/inventory writes\n");
	}
	private static int retainedCharacter(String name) throws Exception
	{
		try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task071combat' AND char_name=? AND accesslevel=0"))
		{
			query.setString(1,name);try(var rows=query.executeQuery()){if(!rows.next()){return 0;}final int id=rows.getInt(1);check(!rows.next(),"Ambiguous retained native actor: "+name);return id;}
		}
	}
	private static void nativeFirstAttack(Player actor,Player defender,StringBuilder report) throws Exception
	{
		final double before=actor.getCurrentHp()+actor.getCurrentCp();final long until=System.nanoTime()+6_000_000_000L;int attempts=0;
		while(actor.getCurrentHp()+actor.getCurrentCp()>=before&&System.nanoTime()<until)
		{
			if(attempts<3&&!defender.isAttackingNow()){defender.doAttack(actor);attempts++;}
			Thread.sleep(25);
		}
		defender.abortAttack();defender.getAI().setIntention(Intention.IDLE);
		report.append("combat071.tailFirstAttack\tactor=").append(actor.getName()).append(";actual native attempts=").append(attempts).append(";max3 after attack end/max6s;pool=").append(before).append("->").append(actor.getCurrentHp()+actor.getCurrentCp()).append(";enemyHeading=").append(defender.getHeading()).append(";actorFront=").append(actor.isInFrontOf(defender)).append('\n');
		check(actor.getCurrentHp()+actor.getCurrentCp()<before,"Native bounded first attacks produced no damage; no fabricated causal reason.");
	}
	private static void soulBottles(PhantomMaterializationService materialization,long profile,Player actor,StringBuilder report) throws Exception
	{
		soulBottles(materialization,profile,actor,report,2,false);
	}
	private static void soulBottles(PhantomMaterializationService materialization,long profile,Player actor,StringBuilder report,int bottles,boolean once) throws Exception
	{
		check(actor.getChargedSouls()==0&&actor.getInventory().getInventoryItemCount(10410,-1)==0,"Fresh Kamael must have no prior soul/bottle fixture.");
		if(once)
		{
			final var marker=java.nio.file.Path.of(System.getProperty("phantom.module.root")).toRealPath().getParent().resolve(".phantom-local/task071/kamael-soul-source071.once");
			java.nio.file.Files.writeString(marker,"character="+actor.getObjectId()+";profile="+profile+";one finite native10410 fixture;no second grant even on failure\n",java.nio.file.StandardOpenOption.CREATE_NEW);
			report.append("combat071.soulSourceOnce\tCREATE_NEW marker before any bottle grant=").append(marker).append(";stock0/Souls0 verified;exact one independent finite fixture;not earned or purchased\n");
		}
		final var bottle=actor.addItem(ItemProcessType.REWARD,10410,bottles,actor,false);
		check("ItemSkillsTemplate".equals(bottle.getEtcItem().getHandlerName()),"Stock full soul bottle handler unavailable; Kamael remains PARTIAL.");
		final var handler=org.l2jmobius.gameserver.handler.ItemHandler.getInstance().getHandler(bottle.getEtcItem());check(handler!=null,"Stock soul bottle handler not registered.");
		try(var lease=materialization.tryAcquireAction(profile).orElseThrow())
		{
			for(int i=0;i<bottles;i++)
			{
				final int souls=actor.getChargedSouls();final long count=actor.getInventory().getInventoryItemCount(10410,-1);actor.setTarget(actor);
				check(bottle.getTemplate().checkCondition(actor,actor,true)&&handler.onItemUse(actor,bottle,false),"Native full soul bottle refused; Kamael remains PARTIAL.");
				await(()->actor.getChargedSouls()>souls,1500,"Native bottle2499 did not produce Souls.");
				check(actor.getChargedSouls()==souls+5&&actor.getInventory().getInventoryItemCount(10410,-1)==count-1,"Native soul bottle lacked exact item debit/+5 Souls.");
				report.append("combat071.soulBottle\tfinite declared item10410;native ItemSkillsTemplate/2499/FocusSouls;count=").append(count).append("->").append(count-1).append(";souls=").append(souls).append("->").append(actor.getChargedSouls()).append(";after materialization/before measurement;no direct soul counter grant;not earned procurement\n");
				await(()->!actor.isCastingSimultaneouslyNow(),1500,"Native bottle cast did not finish.");
			}
		}
	}
	private static Player fixture(String name, int root, int profession, WeaponType weaponType, List<Player> ordinary)
	{
		final Player player = Player.create(PlayerTemplateData.getInstance().getTemplate(root), "task071combat", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(player != null && !player.isGM() && player.getClient() == null, "Ordinary fixture actor required."); ordinary.add(player);
		player.setOnlineStatus(true,false);
		player.setPlayerClass(profession); player.setBaseClass(profession); player.getStat().setExp(player.getStat().getExpForLevel(80)); player.getStat().setLevel((byte) 80);
		for (var skill : SkillTreeData.getInstance().getAllAvailableSkills(player, player.getPlayerClass(), true, true, true)) { player.addSkill(skill, true); }
		final var weapon = ItemData.getInstance().getAllWeapons().stream().filter(item -> item.getItemType() == weaponType && item.isMagicWeapon() == player.getPlayerClass().isMage() && item.getCrystalTypePlus() == CrystalType.S && item.getReferencePrice() > 0 && !item.isHeroItem() && !item.isPvpItem()).min(Comparator.comparingInt(item -> item.getId())).orElseThrow();
		player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, weapon.getId(), 1, player, false));
		final int[] armor = player.getPlayerClass().isMage() ? new int[] {6383,6384,6385,6386} : profession == 93 || profession == 92 || root == 123 ? new int[] {6379,6380,6381,6382} : new int[] {6373,6374,6375,6376,6378};
		for (int id : armor) { player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, id, 1, player, false)); }
		if (profession == 90) { player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD,6377,1,player,false)); }
		for (var shot : ItemData.getInstance().getAllEtcItems()) { if (shot.getCrystalType() == weapon.getCrystalTypePlus() && (shot.getDefaultAction() == ActionType.SOULSHOT || shot.getDefaultAction() == ActionType.SPIRITSHOT) && !shot.isBlessed()) { player.addItem(ItemProcessType.REWARD, shot.getId(), 200, player, false); } }
		if (weaponType == WeaponType.BOW) { player.addItem(ItemProcessType.REWARD, 1345, 200, player, false); }
		if (root == 123)
		{
			final var soulSkill = player.getAllSkills().stream().filter(skill -> skill.getMaxSoulConsumeCount() > 0 && skill.getMaxSoulConsumeCount() <= 10 && PhantomPvpSkillSafety.supports(skill,PhantomCombatMode.MELEE_PHYSICAL,false)).min(Comparator.comparingInt(skill -> skill.getId())).orElseThrow(() -> new AssertionError("UNCOVERED_ROLE no stock learned physical soul-cost action."));
			// A declared bounded learned subset guarantees a native soul-cost positive case.
			for (var skill : List.copyOf(player.getAllSkills())) { if (skill.getId()!=soulSkill.getId() && PhantomPvpSkillSafety.supports(skill,PhantomCombatMode.MELEE_PHYSICAL,false)) { player.removeSkill(skill.getId()); } }
		}
		if(profession==93)
		{
			final var positional=player.getAllSkills().stream().filter(PhantomPvpSkillSafety::requiresRear).findFirst().orElseThrow(()->new AssertionError("UNCOVERED_ROLE no stock learned positional blow."));
			for(var skill:List.copyOf(player.getAllSkills())) { if(skill.getId()!=positional.getId()&&PhantomPvpSkillSafety.supports(skill,PhantomCombatMode.MELEE_PHYSICAL,false)){player.removeSkill(skill.getId());} }
		}
		player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp());
		return player;
	}
	private static Location point()
	{
		final var geo = GeoEngine.getInstance();
		for (Location candidate : List.of(new Location(-71432,254000,-3104),new Location(-76000,254000,-3104),new Location(-78000,254000,-3104),new Location(145214,-1914,-4592)))
		{
			final int x=candidate.getX(), y=candidate.getY(), z=geo.getHeight(x,y,candidate.getZ());
			if (geo.hasGeo(x,y) && ZoneManager.getInstance().getZone(x,y,z,PeaceZone.class)==null && ZoneManager.getInstance().getZone(x,y,z,NoPvPZone.class)==null && ZoneManager.getInstance().getZone(x,y,z,WaterZone.class)==null && geo.canMoveToTarget(x,y,z,x+80,y,z,0) && geo.canSeeTarget(x,y,z,0,x+80,y,z,0)) { return new Location(x,y,z); }
		}
		throw new AssertionError("No stock native Geo/zone point for own071 combat fixture.");
	}
	private static long shotCount(Player player, PhantomCombatMode mode) { return player.getInventory().getItems().stream().filter(item -> item.getTemplate().getDefaultAction() == (mode.magic() ? ActionType.SPIRITSHOT : ActionType.SOULSHOT)).mapToLong(item -> item.getCount()).sum(); }
	private static void noSupply(PhantomCombatService combat,long profile,Player actor,Player target,PhantomPvpCombatRequest request,StringBuilder report)
	{
		// Separate exhausted-inventory negative fixture after the positive episode, never earned consumption.
		for(var item:List.copyOf(actor.getInventory().getItems())) { if(item.getTemplate().getDefaultAction()==ActionType.SOULSHOT||item.getTemplate().getDefaultAction()==ActionType.SPIRITSHOT) { actor.destroyItem(ItemProcessType.DESTROY,item,item.getCount(),actor,false); } }
		for(var type:List.of(ShotType.SOULSHOTS,ShotType.SPIRITSHOTS,ShotType.BLESSED_SPIRITSHOTS)) { actor.setChargedShot(type,false); }
		actor.setTarget(target);
		check(combat.startPvpSession(request).status()==PhantomCombatService.StartStatus.SUPPLY_REQUIRED,"Zero-stock native PvP admission did not fail closed.");
		check(!actor.getNativeWorkOwner().prepareOffensiveShot(request.mode().magic()),"Final native playable-action boundary allowed depleted offense.");
		report.append("combat071.zeroStock\trole=").append(actor.getActiveClass()).append(";explicit independent depleted fixture;native service=SUPPLY_REQUIRED;native work final charge=false;no attack issued\n");
	}
	private static void support(PhantomMaterializationService materialization,PhantomProfileRepository repository,List<Player> ordinary,StringBuilder report) throws Exception
	{
		final var input=point(); final var prepared=fixture("Support071",25,105,WeaponType.BLUNT,ordinary); prepared.setXYZ(input); prepared.stopAllTasks(); prepared.storeMe(); prepared.deleteMe(); ordinary.remove(prepared);
		final long profile=repository.create(prepared.getObjectId()).profileId(); check(materialization.materialize(profile).status()==PhantomMaterializationService.ResultStatus.SUCCESS,"Support fixture materialization failed.");
		final var healer=World.getInstance().getPlayer(prepared.getObjectId()); final var target=fixture("Support071Ally",10,94,WeaponType.BLUNT,ordinary);
		target.setCurrentHp(target.getMaxHp()/2); target.setCurrentMp(1); target.setOnlineStatus(true,false); target.spawnMe(input.getX()+100,input.getY(),input.getZ());
		final var party=new Party(healer,PartyDistributionType.FINDERS_KEEPERS); healer.setParty(party); target.joinParty(party);
		try(var lease=materialization.tryAcquireAction(profile).orElseThrow())
		{
			for(var type:List.of(EffectType.HEAL,EffectType.MANAHEAL_BY_LEVEL))
			{
				final var skill=healer.getAllSkills().stream().filter(value->value.isActive()&&!value.isPassive()&&!value.isToggle()&&!value.hasNegativeEffect()&&value.getTargetType()==org.l2jmobius.gameserver.model.skill.targets.TargetType.ONE&&value.hasEffectType(type)).max(Comparator.comparingInt(value->value.getLevel())).orElseThrow();
				final double before=type==EffectType.HEAL?target.getCurrentHp():target.getCurrentMp(), mp=healer.getCurrentMp(); healer.setTarget(target);
				check(healer.useMagic(skill,false,false),"Native party support refused: "+type);
				await(()->(type==EffectType.HEAL?target.getCurrentHp():target.getCurrentMp())>before+40,12000,"Native party support produced no actual restoration: "+type);
				await(()->!healer.isCastingNow(),4000,"Native support cast did not finish.");
				check(healer.getCurrentMp()<mp,"Native party support did not debit MP.");
				report.append("combat071.support\ttype=").append(type).append(";stockLearned=").append(skill.getId()).append(':').append(skill.getLevel()).append(";target=").append(before).append("->").append(type==EffectType.HEAL?target.getCurrentHp():target.getCurrentMp()).append(";actorMP=").append(mp).append("->").append(healer.getCurrentMp()).append(";actual Party and Phantom ActionLease;native useMagic;low pools declared fixture;PartySupportAction orchestration not re-proved\n");
			}
		}
		finally { party.disbandParty(); }
		check(materialization.dematerialize(profile).status()==PhantomMaterializationService.ResultStatus.SUCCESS,"Support fixture native cleanup failed.");
	}
	public static void inspect(StringBuilder report) throws Exception
	{
		check(PhantomTask071Lane.enabled(),"Explicit own071 inspect required.");
		try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT c.charId,c.char_name,c.classid,c.level,c.curHp,c.curMp,c.curCp,p.profile_id FROM characters c LEFT JOIN phantom_profiles p ON p.character_object_id=c.charId WHERE c.account_name='task071combat' ORDER BY c.charId");var rows=query.executeQuery())
		{ while(rows.next()){report.append("combat071.retained\t").append(rows.getInt(1)).append(';').append(rows.getString(2)).append(";class=").append(rows.getInt(3)).append(";level=").append(rows.getInt(4)).append(";HP=").append(rows.getDouble(5)).append(";MP=").append(rows.getDouble(6)).append(";CP=").append(rows.getDouble(7)).append(";profile=").append(rows.getLong(8)).append('\n');} }
	}
	public static void mixedReserve(StringBuilder report) throws Exception
	{
		check(PhantomTask071Lane.enabled(),"Explicit own071 mixed-reserve fixture required.");
		try(var connection=DatabaseFactory.getConnection();var insert=connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task071reserve','DISABLED_NATIVE_TEST_ONLY',-1,1)")){insert.executeUpdate();}
		final var player=Player.create(PlayerTemplateData.getInstance().getTemplate(10),"task071reserve","Mixed071Reserve",new PlayerAppearance((byte)0,(byte)0,(byte)0,false));
		check(player!=null&&!player.isGM()&&player.getClient()==null,"Ordinary own071 mixed-reserve actor required.");player.setOnlineStatus(true,false);
		try
		{
			final var weapon=ItemData.getInstance().getAllWeapons().stream().filter(item->item.isMagicWeapon()&&item.getCrystalTypePlus()==CrystalType.S&&item.getSpiritShotCount()>0&&item.getReferencePrice()>0&&!item.isHeroItem()&&!item.isPvpItem()).min(Comparator.comparingInt(item->item.getId())).orElseThrow();
			player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD,weapon.getId(),1,player,false));final int perHit=weapon.getSpiritShotCount();
			final var regular=ItemData.getInstance().getAllEtcItems().stream().filter(item->item.getDefaultAction()==ActionType.SPIRITSHOT&&item.getCrystalType()==weapon.getCrystalTypePlus()&&!item.isBlessed()).min(Comparator.comparingInt(item->item.getId())).orElseThrow();
			final var blessed=ItemData.getInstance().getAllEtcItems().stream().filter(item->item.getDefaultAction()==ActionType.SPIRITSHOT&&item.getCrystalType()==weapon.getCrystalTypePlus()&&item.isBlessed()).min(Comparator.comparingInt(item->item.getId())).orElseThrow();
			final var wrong=ItemData.getInstance().getAllEtcItems().stream().filter(item->item.getDefaultAction()==ActionType.SPIRITSHOT&&item.getCrystalType()!=weapon.getCrystalTypePlus()&&!item.isBlessed()).min(Comparator.comparingInt(item->item.getId())).orElseThrow();
			final var bss=player.addItem(ItemProcessType.REWARD,blessed.getId(),perHit,player,false);final var ss=player.addItem(ItemProcessType.REWARD,regular.getId(),2L*perHit,player,false);player.addItem(ItemProcessType.REWARD,wrong.getId(),1000,player,false);
			for(var type:List.of(ShotType.SOULSHOTS,ShotType.SPIRITSHOTS,ShotType.BLESSED_SPIRITSHOTS)){player.setChargedShot(type,false);}
			check(PhantomNativeShotControl.hasOffensiveReserve(player,true,3),"One blessed hit plus two regular hits did not aggregate to three.");
			check(!PhantomNativeShotControl.hasOffensiveReserve(player,true,4),"Wrong-grade stock was included in the offensive reserve.");
			check(!PhantomNativeShotControl.hasOffensiveReserve(player,true,0)&&!PhantomNativeShotControl.hasOffensiveReserve(player,true,5),"Invalid reserve bound accepted.");
			check(PhantomNativeShotControl.chargeOffensive(player,player,true)==PhantomCombatBackend.ShotOutcome.ACTIVATED&&player.getInventory().getInventoryItemCount(blessed.getId(),-1)==0,"Native preferred blessed handler did not consume its one-hit stack.");
			check(PhantomNativeShotControl.hasOffensiveReserve(player,true,3)&&!PhantomNativeShotControl.hasOffensiveReserve(player,true,4),"Current native charge was lost or counted more than once.");
			player.destroyItem(ItemProcessType.DESTROY,ss,perHit,player,false);
			check(!PhantomNativeShotControl.hasOffensiveReserve(player,true,3)&&PhantomNativeShotControl.hasOffensiveReserve(player,true,2),"Below-bound reserve or current charge arithmetic failed.");
			report.append("combat071.mixedReserve\tFIXTURE magical weapon=").append(weapon.getId()).append(";perHit=").append(perHit).append(";blessed1hit+regular2hits=3;wrongGrade1000excluded;nativeBlessedChargeDebit=true;currentChargeCountedOnce;belowBoundsRejected;no earned supply claim\n");
		}
		finally{player.stopAllTasks();player.storeMe();player.deleteMe();}
	}
	public static void cpRetained(StringBuilder report) throws Exception
	{
		check(PhantomTask071Lane.enabled(),"Explicit own071 retained CP fixture required.");
		final int character;
		try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT c.charId FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.account_name='task071combat' AND c.char_name='Combat071Archer' AND c.accesslevel=0 AND a.accessLevel=-1");var rows=query.executeQuery())
		{
			check(rows.next(),"Exact retained ordinary Archer on disabled own071 account required.");character=rows.getInt(1);check(!rows.next(),"Ambiguous retained Archer.");
		}
		final var repository=PhantomProfileRepository.open();
		final long profile=repository.findByCharacterObjectId(character).orElseThrow(()->new AssertionError("Retained Archer profile missing; never create or replay.")).profileId();
		final var metrics=new PhantomMetrics();
		final var materialization=new PhantomMaterializationService(repository,PhantomIdentityLeaseRegistry.getInstance(),metrics,new PhantomDiagnosticTrace(false,0,0,metrics),1);
		check(materialization.start(),"Retained CP materialization did not start.");
		try
		{
			check(materialization.materialize(profile).status()==PhantomMaterializationService.ResultStatus.SUCCESS,"Retained Archer materialization failed.");
			final Player actor=World.getInstance().getPlayer(character);
			check(actor!=null&&!actor.isGM()&&actor.getClient()==null&&!actor.isDead()&&!actor.isInOlympiadMode(),"Ordinary retained Archer required.");
			check(actor.getInventory().getInventoryItemCount(5591,-1)==0&&actor.getInventory().getInventoryItemCount(5592,-1)==0,"CP fixture already exists; refuse replay/refill across JVMs.");
			final var gear=actor.getInventory().getItems().stream().filter(item->item.isEquipped()).map(item->item.getObjectId()+":"+item.getId()+":"+item.getEnchantLevel()).sorted().toList();
			final double hp=actor.getCurrentHp(),mp=actor.getCurrentMp();
			report.append("combat071.cpRetainedFixture\tcharacter=").append(character).append(";profile=").append(profile).append(";starting5591=0;starting5592=0;retained HP/MP/gear;CP1 and finite5591x2/5592x2 independent handler fixture;no battle replay;not earned/purchased;ordinary30081 offer is static source evidence only\n");
			final var backend=new L2jCombatBackend(materialization,()->null);
			cp(backend,profile,actor,report);
			actor.setInOlympiadMode(true);
			try(var lease=backend.tryAcquireActor(profile))
			{
				check(lease!=null,"Olympiad refusal native lease missing.");
				for(int id:new int[]{5591,5592})
				{
					final var item=actor.getInventory().getItemByItemId(id);check(item!=null&&item.getCount()==1,"Native CP debit did not leave exact retained fixture remainder.");
					final var refused=lease.useCpPotion(item.getObjectId(),id);
					check(refused.outcome()==PhantomCombatBackend.CpPotionOutcome.REJECTED&&refused.countBefore()==1&&refused.countAfter()==1&&actor.getInventory().getInventoryItemCount(id,-1)==1,"Native Olympiad CP refusal changed stock.");
					report.append("combat071.cpOlympiad\tid=").append(id).append(";native setInOlympiadMode=true;outcome=").append(refused.outcome()).append(";count=1->1;restriction fixture restored afterwards\n");
				}
			}
			finally{actor.setInOlympiadMode(false);}
			check(gear.equals(actor.getInventory().getItems().stream().filter(item->item.isEquipped()).map(item->item.getObjectId()+":"+item.getId()+":"+item.getEnchantLevel()).sorted().toList()),"Retained CP control changed equipment.");
			report.append("combat071.cpRetained\tHP=").append(hp).append("->").append(actor.getCurrentHp()).append(";MP=").append(mp).append("->").append(actor.getCurrentMp()).append(";no HP/MP writes;native regeneration allowed;gear unchanged;real2166 handler debit/reuse/restoration for both;automatic PvP and paid procurement not claimed\n");
		}
		finally{check(materialization.shutdown().state()==PhantomMaterializationService.ServiceState.STOPPED,"Retained CP materialization STOP failed.");}
	}
	private static void cp(L2jCombatBackend backend, long profile, Player actor, StringBuilder report) throws Exception
	{
		// Explicit independent low-CP handler fixture; never credited as native damage or acquisition.
		actor.setCurrentCp(1); for (int id : new int[] {5591,5592}) { actor.addItem(ItemProcessType.REWARD,id,2,actor,false); }
		for (int id : new int[] {5591,5592})
		{
			try (var lease = backend.tryAcquireActor(profile))
			{
				check(lease != null, "CP native actor lease missing.");
				final var potion = lease.cpPotions().stream().filter(item -> item.itemId() == id).findFirst().orElseThrow();
				final var used = lease.useCpPotion(potion.itemObjectId(),id);
				await(() -> actor.getCurrentCp() > used.cpBefore(), 1000, "CP native restoration missing.");
				final var reuse = lease.useCpPotion(potion.itemObjectId(),id);
				check(used.countAfter() == used.countBefore()-1 && reuse.countAfter() == used.countAfter() && reuse.outcome() == PhantomCombatBackend.CpPotionOutcome.REUSE, "CP debit or immediate native reuse failed.");
				report.append("combat071.cp\tid=").append(id).append(";count=").append(used.countBefore()).append("->").append(used.countAfter()).append(";cp=").append(used.cpBefore()).append("->").append(actor.getCurrentCp()).append(";reuseRejected=true;source=normal Dawn/Dusk multisell500 for240/600AA (acquisition not claimed)\n");
			}
			Thread.sleep(600);
		}
	}
	private static void await(java.util.function.BooleanSupplier condition, long millis, String failure) throws Exception { final long until=System.nanoTime()+millis*1_000_000L; while(!condition.getAsBoolean()&&System.nanoTime()<until){Thread.sleep(25);} check(condition.getAsBoolean(),failure); }
	private static void check(boolean condition,String failure) { if(!condition){throw new AssertionError(failure);} }
}
