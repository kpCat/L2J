package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.*;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningCombat;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** TASK086 bounded native proof and pre-CREATE guard controls; never creates Players. */
public final class PhantomNativeFarmReachability086
{
	private static final int[] LEVELS = {1,10,20,40,52,61,76,80,85};
	private static final List<String> STARTS = List.of("giran.city.center", "population.ingress.human-fighter.01", "population.ingress.elf.01", "population.ingress.orc.01");
	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			final Path module = Path.of(System.getProperty("phantom.module.root")), config = Path.of(System.getProperty("phantom.test.config"));
			if (args[0].equals("guards")) { PhantomTask086Lane.controls(); }
			else if (args[0].equals("native")) { measure(module); }
			else { PhantomTask086Lane.environment(module, config, args[0]); }
			exit = 0;
		}
		catch (Throwable failure)
		{
			// Never print JDBC exceptions, nested causes, private config or credentials.
			String reason = failure.getMessage();
			System.out.println("TASK086_FAIL type=" + failure.getClass().getSimpleName() + "; key=" + (reason != null && reason.matches("TASK086_[A-Z_]+") ? reason : "WITHHELD_CHECK_PRIVATE_SANITIZED_LOG"));
		}
		finally { if (DatabaseFactory.isInitialized()) { DatabaseFactory.close(); } }
		System.exit(exit);
	}
	private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception
	{
		final Method result = type.getDeclaredMethod(name, parameters); result.setAccessible(true); return result;
	}
	private static Object field(Object candidate, String name) throws Exception { return method(candidate.getClass(), name).invoke(candidate); }
	private static TargetFact target(Object candidate) throws Exception { return (TargetFact) field(candidate, "target"); }
	private static PhantomTopologyAnchor anchor(Object candidate) throws Exception { return (PhantomTopologyAnchor) field(candidate, "anchor"); }
	private static long number(Object candidate, String name) throws Exception { return ((Number)field(candidate, name)).longValue(); }
	private static int legs(Object candidate) throws Exception { return ((List<?>)field(candidate,"routeEdgeIds")).size(); }
	private static String key(Object candidate) throws Exception { return target(candidate).npc().npcId() + "@" + anchor(candidate).id(); }
	private static int referenceCompare(Object a, Object b, int level)
	{
		try
		{
			int result = Integer.compare(Math.abs(target(a).npc().level()-level), Math.abs(target(b).npc().level()-level));
			if(result == 0) result = Integer.compare(legs(a), legs(b));
			if(result == 0) result = Long.compare(number(a,"travelMillis"), number(b,"travelMillis"));
			if(result == 0) result = Long.compare(number(a,"feeAdena"), number(b,"feeAdena"));
			if(result == 0) result = Integer.compare(target(a).npc().npcId(), target(b).npc().npcId());
			return result == 0 ? anchor(a).id().compareTo(anchor(b).id()) : result;
		}
		catch(Exception failure) { throw new IllegalStateException(failure); }
	}
	private static boolean comparable(Object value, Object reference, int level) throws Exception
	{
		return Math.abs(target(value).npc().level()-level) <= Math.abs(target(reference).npc().level()-level)+1 && legs(value) <= legs(reference)+1
			&& number(value,"travelMillis") <= number(reference,"travelMillis") + Math.clamp(number(reference,"travelMillis")/2,30000L,120000L)
			&& number(value,"feeAdena") <= number(reference,"feeAdena") && target(value).npc().exp() >= target(reference).npc().exp()*0.5 && target(value).npc().sp() >= target(reference).npc().sp()*0.5;
	}
	private static PhantomBackgroundState state(int level, String start, PhantomTopologyQuery topology, L2jPhantomBackgroundAuthority authority)
	{
		// Explicit TEMPLATE_ONLY scenario. No equipped Player, buffs, items, wallet or learned skills are fabricated.
		final var template = PlayerTemplateData.getInstance().getTemplate(0);
		final var point = topology.findAnchor(start).orElseThrow().point();
		final double hp=template.getBaseHpMax(level), mp=template.getBaseMpMax(level), cp=template.getBaseCpMax(level);
		final var combat = new CombatFacts(ModelKind.MELEE, template.getBasePAtk(), template.getBaseMAtk(), template.getBasePDef(), template.getBaseMDef(), template.getBasePAtkSpd(), template.getBaseMAtkSpd(), template.getBaseHpRegen(level)/3, template.getBaseMpRegen(level)/3,1,1,0,1,1,1,1);
		return new PhantomBackgroundState(State.READY,new Identity(86,86,0,0,0),new Progress(level,0,0,0),new Vitals(hp,hp,mp,mp,cp,cp),new Position(0,point.x(),point.y(),point.z(),0,start),combat,Loadout.none(),new InventoryFacts(List.of(),List.of(),"0".repeat(64),0,0,0,0),List.of(),new Clock(860086,0,0),Receipt.empty(),authority.hashes());
	}
	private static void emptyPlayerTables() throws Exception
	{
		try(var connection=DatabaseFactory.getConnection();var statement=connection.createStatement())
		{
			for(String table:List.of("accounts","characters","items","phantom_profiles"))
			{
				try(var rows=statement.executeQuery("SELECT COUNT(*) FROM `"+table+"`")) { rows.next(); PhantomTask086Lane.require(rows.getLong(1)==0,"UNEXPECTED_PLAYER_ROWS"); }
			}
		}
	}
	private static void measure(Path module) throws Exception
	{
		PhantomTask086Lane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		Files.writeString(PhantomTask086Lane.root().resolve("native.started"),"one bounded episode",java.nio.file.StandardOpenOption.CREATE_NEW);
		final long started=System.nanoTime();
		System.out.println("TASK086_STAGE nativeInfrastructure");
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(860086,module,PhantomTask086Lane.root().resolve("reports")));
		try(var fixture=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var topology=fixture.topology(); final var knowledge=fixture.knowledge();
			final var travel=PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"),topology);
			final var backgroundTravel=PhantomNormalGatekeeperTravel.empty(topology);
			final var authority=new L2jPhantomBackgroundAuthority(fixture::knowledge,fixture::topology,fixture::progression,fixture::commerce,travel);
			final var planner=new PhantomHistoricalBackgroundPlanner(knowledge,topology,authority);
			for(var type:List.of(PhantomHistoricalBackgroundPlanner.class,PhantomNormalGatekeeperTravel.class,PhantomTask086Lane.class,org.l2jmobius.gameserver.data.xml.NpcData.class))
			{
				final Path classes=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
				PhantomTask086Lane.require(classes.startsWith(PhantomTask086Lane.root().resolve("build")),"CLASSPATH_DRIFT");
				System.out.println("TASK086_CLASS_SOURCE type="+type.getSimpleName()+"; path="+classes);
			}
			final var add=method(PhantomHistoricalBackgroundPlanner.class,"addCandidates",List.class,Set.class,String.class,List.class);
			final var select=method(PhantomHistoricalBackgroundPlanner.class,"selectGlobal",List.class,long.class,long.class,long.class,int.class);
			System.out.println("TASK086_NATIVE_LOADED elapsedMs="+(System.nanoTime()-started)/1_000_000+"; knowledge="+knowledge.snapshot().combinedHash()+"; topology="+topology.snapshot().canonicalHash()+"; authority="+authority.hashes()+"; npc="+knowledge.snapshot().npcs().size()+"; spawns="+knowledge.snapshot().spawnFacts().size()+"; areas="+knowledge.snapshot().spawnAreas().size()+"; anchors="+topology.snapshot().anchors().size());
			emptyPlayerTables();
			final var matrix=new ArrayList<String>(); matrix.add("level\tstart\tnativeNpcs\tnativeAreas\tfarmPairs\tnativeRouteHint\tbackgroundRoutes\tnormalRoutes\tlegalBounded\tviableBounded\tcomparable\tcomparableAnchors\tchosen\thistogram\twallMs\tcpuMs\theapBytes\tphysicalLastMile\twalletAdmission");
			final var candidates=new ArrayList<String>(); candidates.add("level\tstart\tnpcId\tnpcLevel\tanchor\tnode\tregion\texp\tsp\tlegs\ttravelMs\tfeeAdena\tcomparable");
			for(int level:LEVELS) for(String start:STARTS)
			{
				final long clock=System.nanoTime(),cpu=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
				final var current=state(level,start,topology,authority); final var combat=PlanningCombat.from(current);
				final var viable=new HashMap<Integer,Boolean>();
				int nativeNpcs=0,nativeAreas=0,farmPairs=0,hints=0,background=0,normal=0;
				final var allPairs=new TreeSet<String>();
				for(var npc:knowledge.snapshot().npcs())
				{
					if(npc.level()<Math.max(1,level-10)||npc.level()>level+2||npc.kind()!=NpcKind.MONSTER||!npc.attackable()||!npc.targetable()) continue;
					nativeNpcs++;
					for(var area:knowledge.snapshot().spawnAreasByNpc().getOrDefault(npc.npcId(),List.of()))
					{
						if(area.instanceId()!=0||area.totalConfiguredAmount()<=0)continue; nativeAreas++;
						if(area.topologyNodeId()==null)continue;
						for(var point:topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(),List.of()))
						{
							if(point.role()!=PhantomTopologyAnchorRole.FARMING||point.point().instanceId()!=0||(point.npcId()!=null&&point.npcId()!=npc.npcId())||!allPairs.add(npc.npcId()+"@"+point.id()))continue;
							farmPairs++;
							if(topology.routeHint(start,point.id()).isPresent())hints++;
							if(backgroundTravel.route(start,point.id()).isPresent())background++;
							if(travel.route(start,point.id()).isPresent())normal++;
						}
					}
				}
				final var pool=new ArrayList<Object>(); final var seen=new HashSet<String>(); int legal=0;
				for(int radius:new int[]{2,5,10})
				{
					String cursor=null;
					for(int page=0;page<8;page++)
					{
						final var targets=knowledge.suitableTargets(new TargetQuery(Math.max(1,level-radius),level+2,level,null,null,Set.of(NpcKind.MONSTER),true,true,null,null,null,cursor==null?PageRequest.first(64):new PageRequest(256,cursor)));
						final int old=pool.size(); add.invoke(planner,pool,seen,start,targets.values()); legal+=pool.size()-old;
						for(var it=pool.iterator();it.hasNext();){final var value=it.next();int npc=target(value).npc().npcId();if(!viable.computeIfAbsent(npc,id->authority.viableTarget(combat,id)))it.remove();}
						cursor=targets.nextCursor();if(cursor==null)break;
					}
					if(!pool.isEmpty())break;
				}
				final var comparable=new ArrayList<Object>();
				if(!pool.isEmpty()){final var reference=pool.stream().min((a,b)->referenceCompare(a,b,level)).orElseThrow();for(var candidate:pool){if(comparable(candidate,reference,level))comparable.add(candidate);}}
				final var owner=new PhantomDomainRef("profile","86");
				final var previous=new PhantomGoal(86,PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE,PhantomGoalStatus.ACTIVE,owner,owner,1,0,"background.idle",List.of(),new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE,start),"background.history",500,0,0,0,Map.of(),"planner.target_or_route.absent",0);
				final var publicResult=planner.replaceFromState(86,current,previous,860086,0);
				PhantomTask086Lane.require(publicResult.ready()==!pool.isEmpty(),"POOL_PUBLIC_DISAGREES");
				if(publicResult.ready())PhantomTask086Lane.require(key(select.invoke(null,pool,86L,860086L,0L,level)).equals(publicResult.spec().npcId()+"@"+publicResult.spec().anchorId()),"RANK_PUBLIC_DISAGREES");
				final var anchors=new TreeSet<String>();for(var candidate:comparable)anchors.add(anchor(candidate).id());
				for(var candidate:pool)candidates.add(level+"\t"+start+"\t"+target(candidate).npc().npcId()+"\t"+target(candidate).npc().level()+"\t"+anchor(candidate).id()+"\t"+anchor(candidate).nodeId()+"\t"+anchor(candidate).mapRegionLocId()+"\t"+target(candidate).npc().exp()+"\t"+target(candidate).npc().sp()+"\t"+legs(candidate)+"\t"+number(candidate,"travelMillis")+"\t"+number(candidate,"feeAdena")+"\t"+comparable.contains(candidate));
				final var histogram=new TreeMap<String,Integer>();
				if(anchors.size()>=2)for(long profile=1;profile<=100;profile++)
				{
					final var chosen=planner.replaceFromState(profile,current,previous,860086,0);
					PhantomTask086Lane.require(chosen.ready() && key(select.invoke(null,pool,profile,860086L,0L,level)).equals(chosen.spec().npcId()+"@"+chosen.spec().anchorId()),"SAMPLE_PUBLIC_DISAGREES");
					histogram.merge(chosen.spec().anchorId(),1,Integer::sum);
				}
				final String chosen=publicResult.ready()?publicResult.spec().npcId()+"@"+publicResult.spec().anchorId():publicResult.reasonKey();
				final String row=level+"\t"+start+"\t"+nativeNpcs+"\t"+nativeAreas+"\t"+farmPairs+"\t"+hints+"\t"+background+"\t"+normal+"\t"+legal+"\t"+pool.size()+"\t"+comparable.size()+"\t"+anchors.size()+"\t"+chosen+"\t"+histogram+"\t"+(System.nanoTime()-clock)/1_000_000+"\t"+(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpu)/1_000_000+"\t"+ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()+"\tUNVERIFIED\tUNVERIFIED_ZERO_WALLET_TEMPLATE_ONLY";
				matrix.add(row);System.out.println("TASK086_MATRIX\t"+row);
			}
			Files.write(PhantomTask086Lane.root().resolve("native-matrix.tsv"),matrix,StandardCharsets.UTF_8);
			Files.write(PhantomTask086Lane.root().resolve("native-candidates.tsv"),candidates,StandardCharsets.UTF_8);
			emptyPlayerTables(); System.out.println("TASK086_NATIVE_PROOF_PASS cohorts=36; Players=0; durableProfiles=0; templateOnly=true");
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
}
