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

/** Exact087 exploration. Planning/Geo evidence never claims payment or physical arrival. */
public final class PhantomUniversalRegionTravel087
{
	private static final int[] LEVELS = {40,76,80};
	private static final List<String> STARTS = List.of("giran.city.center", "population.ingress.human-fighter.01", "population.ingress.elf.01", "population.ingress.orc.01");
	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			final Path module = Path.of(System.getProperty("phantom.module.root")), config = Path.of(System.getProperty("phantom.test.config"));
			if (args[0].equals("guards")) { PhantomTask087Lane.controls(); }
			else if (args[0].equals("native")) { measure(module); }
			else if (args[0].equals("refine")) { refine(module); }
			else { PhantomTask087Lane.environment(module, config, args[0]); }
			exit = 0;
		}
		catch (Throwable failure)
		{
			// Never print JDBC exceptions, nested causes, private config or credentials.
			String reason = failure.getMessage();
			System.out.println("TASK087_FAIL type=" + failure.getClass().getSimpleName() + "; key=" + (reason != null && reason.matches("TASK087_[A-Z_]+") ? reason : "WITHHELD_CHECK_PRIVATE_SANITIZED_LOG"));
		}
		finally { if (args.length > 0 && List.of("native","refine").contains(args[0])) { ThreadPool.shutdown(); } if (DatabaseFactory.isInitialized()) { DatabaseFactory.close(); } }
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
		return new PhantomBackgroundState(State.READY,new Identity(86,86,0,0,0),new Progress(level,0,0,0),new Vitals(hp,hp,mp,mp,cp,cp),new Position(0,point.x(),point.y(),point.z(),0,start),combat,Loadout.none(),new InventoryFacts(List.of(),List.of(),"0".repeat(64),0,0,0,0),List.of(),new Clock(860087,0,0),Receipt.empty(),authority.hashes());
	}
	private static void emptyPlayerTables() throws Exception
	{
		try(var connection=DatabaseFactory.getConnection();var statement=connection.createStatement())
		{
			for(String table:List.of("accounts","characters","items","phantom_profiles"))
			{
				try(var rows=statement.executeQuery("SELECT COUNT(*) FROM `"+table+"`")) { rows.next(); PhantomTask087Lane.require(rows.getLong(1)==0,"UNEXPECTED_PLAYER_ROWS"); }
			}
		}
	}
	private static void measure(Path module) throws Exception
	{
		PhantomTask087Lane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		Files.writeString(PhantomTask087Lane.root().resolve("native.started"),"one bounded episode",java.nio.file.StandardOpenOption.CREATE_NEW);
		final long started=System.nanoTime();
		System.out.println("TASK087_STAGE nativeInfrastructure");
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(860087,module,PhantomTask087Lane.root().resolve("reports")));
		try(var fixture=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var topology=fixture.topology(); final var knowledge=fixture.knowledge();
			final var travel=PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"),topology);
			final var backgroundTravel=PhantomNormalGatekeeperTravel.empty(topology);
			final var authority=new L2jPhantomBackgroundAuthority(fixture::knowledge,fixture::topology,fixture::progression,fixture::commerce,travel);
			final var planner=new PhantomHistoricalBackgroundPlanner(knowledge,topology,authority);
			for(var type:List.of(PhantomHistoricalBackgroundPlanner.class,PhantomNormalGatekeeperTravel.class,PhantomTask087Lane.class,org.l2jmobius.gameserver.data.xml.NpcData.class))
			{
				final Path classes=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
				PhantomTask087Lane.require(classes.startsWith(PhantomTask087Lane.root().resolve("build")),"CLASSPATH_DRIFT");
				System.out.println("TASK087_CLASS_SOURCE type="+type.getSimpleName()+"; path="+classes);
			}
			final var add=method(PhantomHistoricalBackgroundPlanner.class,"addCandidates",List.class,Set.class,String.class,List.class);
			final var select=method(PhantomHistoricalBackgroundPlanner.class,"selectGlobal",List.class,long.class,long.class,long.class,int.class);
			System.out.println("TASK087_NATIVE_LOADED elapsedMs="+(System.nanoTime()-started)/1_000_000+"; knowledge="+knowledge.snapshot().combinedHash()+"; topology="+topology.snapshot().canonicalHash()+"; authority="+authority.hashes()+"; npc="+knowledge.snapshot().npcs().size()+"; spawns="+knowledge.snapshot().spawnFacts().size()+"; areas="+knowledge.snapshot().spawnAreas().size()+"; anchors="+topology.snapshot().anchors().size());
			emptyPlayerTables();
			PhantomTravelGeoProbe.main(new String[] {PhantomTask087Lane.root().resolve("source-audit087/ingress-gk-candidates.tsv").toString(), PhantomTask087Lane.root().resolve("ingress-gk-geo.tsv").toString(), "--waypoints"});
			qualifiedContexts(fixture, travel, authority);
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
				final var publicResult=planner.replaceFromState(86,current,previous,860087,0);
				PhantomTask087Lane.require(publicResult.ready()==!pool.isEmpty(),"POOL_PUBLIC_DISAGREES");
				if(publicResult.ready())PhantomTask087Lane.require(key(select.invoke(null,pool,86L,860087L,0L,level)).equals(publicResult.spec().npcId()+"@"+publicResult.spec().anchorId()),"RANK_PUBLIC_DISAGREES");
				final var anchors=new TreeSet<String>();for(var candidate:comparable)anchors.add(anchor(candidate).id());
				for(var candidate:pool)candidates.add(level+"\t"+start+"\t"+target(candidate).npc().npcId()+"\t"+target(candidate).npc().level()+"\t"+anchor(candidate).id()+"\t"+anchor(candidate).nodeId()+"\t"+anchor(candidate).mapRegionLocId()+"\t"+target(candidate).npc().exp()+"\t"+target(candidate).npc().sp()+"\t"+legs(candidate)+"\t"+number(candidate,"travelMillis")+"\t"+number(candidate,"feeAdena")+"\t"+comparable.contains(candidate));
				final var histogram=new TreeMap<String,Integer>();
				if(anchors.size()>=2)for(long profile=1;profile<=3;profile++)
				{
					final var chosen=planner.replaceFromState(profile,current,previous,860087,0);
					PhantomTask087Lane.require(chosen.ready() && key(select.invoke(null,pool,profile,860087L,0L,level)).equals(chosen.spec().npcId()+"@"+chosen.spec().anchorId()),"SAMPLE_PUBLIC_DISAGREES");
					histogram.merge(chosen.spec().anchorId(),1,Integer::sum);
				}
				final String chosen=publicResult.ready()?publicResult.spec().npcId()+"@"+publicResult.spec().anchorId():publicResult.reasonKey();
				final String row=level+"\t"+start+"\t"+nativeNpcs+"\t"+nativeAreas+"\t"+farmPairs+"\t"+hints+"\t"+background+"\t"+normal+"\t"+legal+"\t"+pool.size()+"\t"+comparable.size()+"\t"+anchors.size()+"\t"+chosen+"\t"+histogram+"\t"+(System.nanoTime()-clock)/1_000_000+"\t"+(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpu)/1_000_000+"\t"+ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()+"\tUNVERIFIED\tUNVERIFIED_ZERO_WALLET_TEMPLATE_ONLY";
				matrix.add(row);System.out.println("TASK087_MATRIX\t"+row);
			}
			Files.write(PhantomTask087Lane.root().resolve("native-matrix.tsv"),matrix,StandardCharsets.UTF_8);
			Files.write(PhantomTask087Lane.root().resolve("native-candidates.tsv"),candidates,StandardCharsets.UTF_8);
			System.out.println("TASK087_EXPLORATION_COMPLETE cohorts=12; nativeContexts=3; payment=NOT_RUN; physicalArrival=NOT_RUN; restart=NOT_RUN");
		}
		finally { DatabaseFactory.close(); }
	}

	private record Scenario(String family, int classId, int level, int[] gear) { }
	private static void refine(Path module) throws Exception
	{
		PhantomTask087Lane.validate(module,Path.of(System.getProperty("phantom.test.config")));
		Files.writeString(PhantomTask087Lane.root().resolve("refine.started"),"one bounded corridor/continuity refinement; no fixture repair",java.nio.file.StandardOpenOption.CREATE_NEW);
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(870087,module,PhantomTask087Lane.root().resolve("refined-reports")));
		try (var fixture=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var topology=fixture.topology();
			final var travel=PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"),topology);
			final var authority=new L2jPhantomBackgroundAuthority(fixture::knowledge,fixture::topology,fixture::progression,fixture::commerce,travel);
			final var backend=new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
			final var policy=org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
			final var candidates=new HashMap<String,String[]>();
			final Path candidateFile=PhantomTask087Lane.root().resolve("source-audit087/ingress-gk-candidates.tsv");
			final var candidateHash=java.util.regex.Pattern.compile("\"candidates_sha256\": \"([0-9a-f]{64})\"").matcher(Files.readString(PhantomTask087Lane.root().resolve("source-audit087/source-provenance.json")));
			PhantomTask087Lane.require(candidateHash.find(),"CANDIDATE_MANIFEST"); PhantomTask087Lane.inputFile(candidateFile,candidateHash.group(1));
			for (String line:Files.readAllLines(candidateFile).subList(1,5)) { final var row=line.split("\t"); candidates.put(row[0],row); }
			final var profiles=org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
			final var metrics=new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
			final var materialization=new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService(profiles,org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(),metrics,new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false,64,16,metrics),3);
			final var admitted=new ArrayList<Long>();
			final var rows=new ArrayList<String>(); rows.add("profile\tactor\tepoch\tclass\tlevel\tposition\twallet\titemsEqual\tautoSkillsBefore\tautoSkillsAfter\tcombat\tviableNativeNpcs\tgiranViablePairs\tleg0\tcanInteractBefore\tcorridor\tnodes\tedges\tqueries\tlabels\twallMs\tactionContext");
			final var points=new ArrayList<String>(); points.add("profile\twaypoint\tx\ty\tz\tinstance");
			PhantomTask087Lane.require(materialization.start(),"MATERIALIZATION_START");
			try
			{
				for (String line:Files.readAllLines(PhantomTask087Lane.root().resolve("fixture-before-measurement.tsv")).subList(1,4))
				{
					final var previous=line.split("\t",-1); final long id=Long.parseLong(previous[1]);
					PhantomTask087Lane.require(materialization.materialize(id).status()==org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS,"RELOAD_MATERIALIZATION"); admitted.add(id);
					try (var action=materialization.tryAcquireAction(id).orElseThrow())
					{
						final var player=action.player(); final var from=org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player);
						PhantomTask087Lane.require(player.getObjectId()==Integer.parseInt(previous[2]) && player.getActiveClass()==Integer.parseInt(previous[3]) && player.getLevel()==Integer.parseInt(previous[4]) && player.getAdena()==Long.parseLong(previous[8]),"RESTORED_IDENTITY_WALLET");
						PhantomTask087Lane.require(from.x()==Integer.parseInt(previous[5]) && from.y()==Integer.parseInt(previous[6]) && from.z()==Integer.parseInt(previous[7]),"RESTORED_POSITION");
						final var actualItems=player.getInventory().getItems().stream().map(item->item.getObjectId()+":"+item.getId()+":"+item.getCount()+":"+item.isEquipped()).sorted().toList();
						PhantomTask087Lane.require(actualItems.toString().equals(previous[12]),"RESTORED_ITEMS");
						final var combat=authority.planningCombat(player).orElseThrow(); final var viability=new HashMap<Integer,Boolean>(); int giranPairs=0;
						final boolean sameActionContext=previous[11].equals(player.getAutoUseSettings().getAutoSkills().toString());
						final var seen=new HashSet<String>();
						for (var npc:fixture.knowledge().snapshot().npcs())
						{
							if (!sameActionContext) { continue; }
							if (npc.level()<player.getLevel()-10 || npc.level()>player.getLevel()+2 || npc.kind()!=NpcKind.MONSTER || !npc.attackable() || !npc.targetable()) { continue; }
							final boolean viable=authority.viableTarget(combat,npc.npcId()); viability.put(npc.npcId(),viable);
							if (!viable) { continue; }
							for (var area:fixture.knowledge().snapshot().spawnAreasByNpc().getOrDefault(npc.npcId(),List.of()))
							{
								if (area.instanceId()!=0 || area.totalConfiguredAmount()<=0 || area.topologyNodeId()==null) { continue; }
								for (var anchor:topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(),List.of()))
								{ if (anchor.role()==PhantomTopologyAnchorRole.FARMING && (anchor.npcId()==null || anchor.npcId()==npc.npcId()) && seen.add(npc.npcId()+"@"+anchor.id()) && travel.route("giran.city.center",anchor.id()).isPresent()) { giranPairs++; } }
							}
						}
						final String start=player.getActiveClass()==2?"population.ingress.human-fighter.01":player.getActiveClass()==102?"population.ingress.elf.01":"population.ingress.orc.01";
						final var source=candidates.get(start);
						final var authored=topology.findAnchor(start).orElseThrow().point();
						PhantomTask087Lane.require(authored.x()==Integer.parseInt(source[1]) && authored.y()==Integer.parseInt(source[2]) && authored.z()==Integer.parseInt(source[3]),"CANDIDATE_ANCHOR_DRIFT");
						final var to=new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(Integer.parseInt(source[5]),Integer.parseInt(source[6]),org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(Integer.parseInt(source[5]),Integer.parseInt(source[6]),Integer.parseInt(source[7])),0);
						final int sourceNpc=nearestSourceNpc(module,source);
						final var leg=PhantomNormalGatekeeperTravel.observeNativeLeg(sourceNpc,0,Integer.parseInt(source[5]),Integer.parseInt(source[6]),Integer.parseInt(source[7])).orElse(null);
						final var nativeNpc=leg==null?null:org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeperAt(leg);
						final long now=System.nanoTime();
						final var result=backend.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id,from,to,now,now+policy.defaultRequestDeadlineNanos(),policy.maximumRouteDistance()),new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(),policy,System::nanoTime);
						int waypoint=0; var point=from;
						for (var next:result.points()) { PhantomTask087Lane.require(backend.canWalkDirect(point,next),"CORRIDOR_EDGE"); point=next; points.add(id+"\t"+(waypoint++)+"\t"+next.x()+"\t"+next.y()+"\t"+next.z()+"\t"+next.instanceId()); }
						final String row=id+"\t"+player.getObjectId()+"\t"+player.getNativeWorkOwner().epoch()+"\t"+player.getActiveClass()+"\t"+player.getLevel()+"\t"+from+"\t"+player.getAdena()+"\ttrue\t"+previous[11]+"\t"+player.getAutoUseSettings().getAutoSkills()+"\t"+combat+"\t"+(sameActionContext?Long.toString(viability.values().stream().filter(Boolean::booleanValue).count()):"NOT_COMPARABLE")+"\t"+(sameActionContext?Integer.toString(giranPairs):"NOT_COMPARABLE")+"\t"+(leg==null?"ABSENT":leg.teleporterNpcId()+":"+leg.destinationIndex()+":available="+org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.available(leg)+":quote="+PhantomNormalGatekeeperTravel.fee(leg,player.getClassIndex(),player.getLevel(),java.time.Instant.now().toEpochMilli()/60000))+"\t"+(nativeNpc!=null && nativeNpc.canInteract(player))+"\t"+result.status()+"\t"+result.nodes()+"\t"+result.edges()+"\t"+result.queries()+"\t"+result.labels()+"\t"+(System.nanoTime()-now)/1_000_000+"\t"+(sameActionContext?"MATCHED_ACTION_CONTEXT":"AUTO_SKILLS_LOST_RESTART");
						rows.add(row); System.out.println("TASK087_REFINED\t"+row);
					}
				}
				Files.write(PhantomTask087Lane.root().resolve("refined-corridor.tsv"),rows,StandardCharsets.UTF_8);
				Files.write(PhantomTask087Lane.root().resolve("refined-waypoints.tsv"),points,StandardCharsets.UTF_8);
			}
			finally { for (long id:admitted) { PhantomTask087Lane.require(materialization.dematerialize(id).status()==org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS,"REFINED_DEMATERIALIZE"); } PhantomTask087Lane.require(materialization.shutdown().state()==org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED,"REFINED_STOP"); }
		}
	}
	private static int nearestSourceNpc(Path module, String[] candidate) throws Exception
	{
		final var facts=Files.readAllLines(module.resolve("docs/phantoms/live-world/GATEKEEPER_FACTS.tsv"));
		final var columns=List.of(facts.getFirst().split("\t",-1));
		final int type=columns.indexOf("teleport_type"), availability=columns.indexOf("availability_class"), x=columns.indexOf("teleporter_spawn_x"), y=columns.indexOf("teleporter_spawn_y"), z=columns.indexOf("teleporter_spawn_z"), npc=columns.indexOf("teleporter_npc_id"), key=columns.indexOf("teleporter_spawn_key");
		final var normal=facts.subList(1,facts.size()).stream().map(line->line.split("\t",-1)).filter(row->row[type].equals("NORMAL") && row[availability].equals("NORMAL"));
		final var nearest=normal.min(Comparator.<String[]>comparingDouble(row->Math.hypot((long)Integer.parseInt(row[x])-Integer.parseInt(candidate[1]),(long)Integer.parseInt(row[y])-Integer.parseInt(candidate[2]))).thenComparing(row->row[key])).orElseThrow();
		PhantomTask087Lane.require(nearest[x].equals(candidate[5]) && nearest[y].equals(candidate[6]) && nearest[z].equals(candidate[7]),"CANDIDATE_GK_DRIFT");
		return Integer.parseInt(nearest[npc]);
	}
	private static void qualifiedContexts(PhantomBackgroundSuite.ProductionAuthorityFixture fixture,
		PhantomNormalGatekeeperTravel travel, L2jPhantomBackgroundAuthority authority) throws Exception
	{
		// Fixed before any viability measurement; all funds/gear/levels below are TEST_FIXTURE.
		final var scenarios = List.of(new Scenario("human-fighter", 2, 40, new int[] {2561,60,64,517}),
			new Scenario("elf", 102, 76, new int[] {7575,6379,6380,6381,6382}),
			new Scenario("orc", 115, 76, new int[] {6579,6383,6384,6385,6386}));
		final Path receipt = PhantomTask087Lane.root().resolve("fixture-before-measurement.tsv");
		PhantomTask087Lane.require(!Files.exists(receipt), "FIXTURE_ALREADY_EXISTS");
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task087native',NULL,0,1)"))
		{ PhantomTask087Lane.require(insert.executeUpdate() == 1, "ACCOUNT_CREATE"); }
		final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
		final var metrics = new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
		final var materialization = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService(profiles,
			org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(), metrics,
			new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false,64,16,metrics),3);
		final var identities = new LinkedHashMap<Long,Scenario>();
		final var receipts = new ArrayList<String>(); receipts.add("origin\tprofile\tobjectId\tclass\tlevel\tx\ty\tz\twallet\tgear\tskills\tautoSkills\tactualItems");
		PhantomTask087Lane.require(materialization.start(), "MATERIALIZATION_START");
		try
		{
			for (var scenario : scenarios)
			{
				final var point = fixture.topology().findAnchor("population.ingress."+scenario.family()+".01").orElseThrow().point();
				final var setup = org.l2jmobius.gameserver.model.actor.Player.create(PlayerTemplateData.getInstance().getTemplate(scenario.classId()), "task087native", "Trip087"+scenario.classId(),
					new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte)0,(byte)0,(byte)0,false));
				PhantomTask087Lane.require(setup != null && !setup.isGM(), "PLAYER_CREATE");
				final long profile;
				try
				{
					setup.stopAllTasks(); setup.setXYZInvisible(point.x(),point.y(),org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(point.x(),point.y(),point.z()));
					setup.setPlayerClass(scenario.classId()); setup.setBaseClass(scenario.classId());
					setup.getStat().setExp(setup.getStat().getExpForLevel(scenario.level())); setup.getStat().setLevel((byte)scenario.level());
					setup.giveAvailableAutoGetSkills(); setup.giveAvailableSkills(false,true,false); setup.rewardSkills();
					for (int item : scenario.gear()) { setup.getInventory().equipItem(setup.addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD,item,1,setup,false)); }
					if (scenario.classId() == 102) { setup.addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD,1345,1000,setup,false); }
					setup.addAdena(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD,200000,setup,false);
					setup.refreshExpertisePenalty(); setup.refreshOverloaded(); setup.setRunning();
					setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp());
					setup.getInventory().updateDatabase(); setup.storeMe(); profile=profiles.create(setup.getObjectId()).profileId();
				}
				finally { setup.stopAllTasks(); setup.deleteMe(); }
				PhantomTask087Lane.require(materialization.materialize(profile).status() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, "MATERIALIZATION");
				identities.put(profile,scenario);
				try (var action = materialization.tryAcquireAction(profile).orElseThrow())
				{
					final var player = action.player();
					for (int item : scenario.gear())
					{
						final var equipped = player.getInventory().getItemByItemId(item);
						PhantomTask087Lane.require(equipped != null && equipped.isEquipped() && equipped.getTemplate().getCrystalType().getLevel() <= player.getExpertiseLevel(),"QUALIFIED_GEAR");
					}
					if (scenario.classId() == 115)
					{
						final var offensive = player.getAllSkills().stream().filter(s -> s.isMagic() && s.isDamage() && s.isActive() && !s.isToggle() && s.hasNegativeEffect() && s.getItemConsumeCount()==0 && s.getChargeConsumeCount()==0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.skill.Skill::getId)).findFirst().orElseThrow();
						player.getAutoUseSettings().getAutoSkills().add(offensive.getId()); player.getAutoUseSettings().resetSkillOrder();
						PhantomTask087Lane.require(org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.offensiveSkill(player,null) != null,"MAGIC_ACTION");
					}
					final var actualItems = player.getInventory().getItems().stream().map(item -> item.getObjectId()+":"+item.getId()+":"+item.getCount()+":"+item.isEquipped()).sorted().toList();
					receipts.add("TEST_FIXTURE_STOCK_TREE_NO_FS_NO_ITEM_SKILLS\t"+profile+"\t"+player.getObjectId()+"\t"+player.getActiveClass()+"\t"+player.getLevel()+"\t"+player.getX()+"\t"+player.getY()+"\t"+player.getZ()+"\t"+player.getAdena()+"\t"+Arrays.toString(scenario.gear())+"\t"+player.getAllSkills().size()+"\t"+player.getAutoUseSettings().getAutoSkills()+"\t"+actualItems);
				}
			}
			Files.write(receipt,receipts,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
			final var rows = new ArrayList<String>(); rows.add("family\tobjectId\tclass\tlevel\tstart\tnativeCombat\tfarmPairs\troutes\tviableRoutes\tavailableCatalogLegs\tplanner\twalletBefore\twalletAfter");
			for (var entry : identities.entrySet()) try (var action = materialization.tryAcquireAction(entry.getKey()).orElseThrow())
			{
				final var player=action.player(); final var scenario=entry.getValue(); final long wallet=player.getAdena();
				final String start="population.ingress."+scenario.family()+".01";
				final var combat=authority.planningCombat(player).orElseThrow(); int pairs=0,routes=0,viable=0;
				final var seen = new HashSet<String>();
				for (var npc : fixture.knowledge().snapshot().npcs())
				{
					if (npc.level()<scenario.level()-10 || npc.level()>scenario.level()+2 || npc.kind()!=NpcKind.MONSTER || !npc.attackable() || !npc.targetable()) { continue; }
					for (var area : fixture.knowledge().snapshot().spawnAreasByNpc().getOrDefault(npc.npcId(),List.of()))
					{
						if (area.instanceId()!=0 || area.totalConfiguredAmount()<=0 || area.topologyNodeId()==null) { continue; }
						for (var anchor : fixture.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(),List.of()))
						{
							if (anchor.role()!=PhantomTopologyAnchorRole.FARMING || anchor.point().instanceId()!=0 || anchor.npcId()!=null && anchor.npcId()!=npc.npcId() || !seen.add(npc.npcId()+"@"+anchor.id())) { continue; }
							pairs++;
							if (travel.route(start,anchor.id()).isPresent()) { routes++; if (authority.viableTarget(combat,npc.npcId())) { viable++; } }
						}
					}
				}
				String planning;
				try { final var plan=new PhantomHistoricalBackgroundPlanner(fixture.knowledge(),fixture.topology(),authority).planInitial(entry.getKey(),player,870087,1); planning=plan.ready()?plan.spec().toString():plan.reasonKey(); }
				catch (IllegalArgumentException refused)
				{
					final String reason=refused.getMessage();
					if ("Canonical Player position does not identify exactly one topology anchor.".equals(reason)) { planning="NATIVE_ANCHOR_NON_UNIQUE_OR_ABSENT"; }
					else if (reason != null && reason.matches("Canonical Player is in an unsupported background context[.] firstPredicate=(instance|flying|flyingMounted|mounted|party|combat|combatFlag|gm|premium|event|festival|karma|nevit=[0-9.]+|vitality=[0-9.]+)")) { planning=reason.substring(reason.indexOf("firstPredicate=")); }
					else { throw refused; }
				}
				final String row=scenario.family()+"\t"+player.getObjectId()+"\t"+scenario.classId()+"\t"+player.getLevel()+"\t"+start+"\t"+combat+"\t"+pairs+"\t"+routes+"\t"+viable+"\t"+travel.legs().stream().filter(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions::available).count()+"\t"+planning+"\t"+wallet+"\t"+player.getAdena();
				rows.add(row); System.out.println("TASK087_ACTOR\t"+row);
				PhantomTask087Lane.require(player.getAdena()==wallet,"UNEXPECTED_PAYMENT");
			}
			Files.write(PhantomTask087Lane.root().resolve("native-actors.tsv"),rows,StandardCharsets.UTF_8);
		}
		finally
		{
			for (long profile : identities.keySet()) { PhantomTask087Lane.require(materialization.dematerialize(profile).status() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS,"DEMATERIALIZATION"); }
			PhantomTask087Lane.require(materialization.shutdown().state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED,"MATERIALIZATION_STOP");
		}
	}
}
