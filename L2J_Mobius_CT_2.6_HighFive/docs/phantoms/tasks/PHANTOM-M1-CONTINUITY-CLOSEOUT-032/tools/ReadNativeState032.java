import com.sun.jdi.*;
import java.nio.file.*;
import java.util.*;
/** Exact selected retained owners, read-only fields; no suspension, invocation or mutation. */
class ReadNativeState032 {
 static Value field(Value v,String name) {
  if (!(v instanceof ObjectReference o)) return null;
  Field f=o.referenceType().fieldByName(name); return f==null?null:o.getValue(f);
 }
 static String scalar(Value v) {
  if(v==null)return "null"; if(v instanceof StringReference s)return s.value();
  if(v instanceof PrimitiveValue)return v.toString();
  Value e=field(v,"name"); return e instanceof StringReference s?s.value():v.type().name();
 }
 static List<ObjectReference> entries(Value map) {
  List<ObjectReference> result=new ArrayList<>();
  Value table=field(map,"table");
  if(table instanceof ArrayReference a)for(Value first:a.getValues()) {
   Value node=first; for(int n=0;node instanceof ObjectReference o&&n<128;n++) {
    result.add(o); node=field(o,"next");
   }
  }
  return result;
 }
 public static void main(String[] args)throws Exception {
  Path output=Path.of(args[1]); if(Files.exists(output))throw new IllegalStateException("Immutable output");
  var connector=Bootstrap.virtualMachineManager().attachingConnectors().stream().filter(c->c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
  var options=connector.defaultArguments(); options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]); options.get("timeout").setValue("5000");
  VirtualMachine vm=connector.attach(options); List<String> lines=new ArrayList<>();
  Set<String> profiles=Set.of(args[2].split(","));
  try {
   ReferenceType type=vm.classesByName(args[3]).getFirst();
   Value owners=type.getValue(type.fieldByName("RECEIPT_OWNERS"));
   for(String metric:List.of("HOOK_CALLS","HOOK_NANOS","HOOK_MAX_NANOS"))lines.add("observer."+metric+"="+scalar(field(type.getValue(type.fieldByName(metric)),"value")));
   lines.add("UTC="+java.time.Instant.now()+" READ=NONATOMIC_NO_SUSPEND");
   for(ObjectReference entry:entries(owners)) {
    Value key=field(entry,"key"); String profile=scalar(field(key,"profileId"));
    if(!profiles.contains(profile))continue;
    Value owner=field(entry,"val"), player=field(owner,"_player"), sensor=field(owner,"_evidence");
    lines.add("PROFILE="+profile+" epoch="+scalar(field(key,"epoch"))+" object="+scalar(field(key,"objectId")));
    for(String f:List.of("_waitTypeSitting","_isDead","_isRunning","_isCastingNow","_isAttackingNow"))lines.add("player."+f+"="+scalar(field(player,f)));
    Value status=field(player,"_status"),regen=field(status,"_regTask");lines.add("status._flagsRegenActive="+scalar(field(status,"_flagsRegenActive")));lines.add("status._regTask="+scalar(regen));
    if(regen instanceof ObjectReference future)for(Field f:future.referenceType().allFields())if(!f.isStatic()&&List.of("state","period","time","runner","callable").contains(f.name()))lines.add("regen."+f.name()+"="+scalar(future.getValue(f)));
    for(String f:List.of("_currentHp","_currentMp","_currentCp"))lines.add("status."+f+"="+scalar(field(field(player,"_status"),f)));
    for(String f:List.of("_overflow","_firstUnprovenReason","_firstUnprovenNanos","_damageSequence","_killSequence","_rewardSequence","_farmCycleSequence"))lines.add(f+"="+scalar(field(sensor,f)));
    Value map=field(sensor,"_targets"), node=field(map,"head");
    for(int n=0;node!=null&&n<17;n++) {
     Value target=field(node,"key"), state=field(node,"value"); StringBuilder s=new StringBuilder("target ");
     for(String f:List.of("objectId","instanceId","spawnGeneration"))s.append(f).append('=').append(scalar(field(target,f))).append(' ');
     for(String f:List.of("_damaged","_killed","_exp","_sp","_nextSelected","_counted"))s.append(f).append('=').append(scalar(field(state,f))).append(' ');
     lines.add(s.toString()); node=field(node,"after");
    }
    for(ObjectReference skill:entries(field(player,"_skills"))) {
     Value value=field(skill,"val"); lines.add("skill="+scalar(field(value,"_id"))+":"+scalar(field(value,"_level"))+":"+scalar(field(value,"_name"))+":"+scalar(field(value,"_operateType")));     if("1177".equals(scalar(field(value,"_id")))) {
      Value effects=field(value,"_effectLists"),values=field(effects,"vals");lines.add("skill1177.effectMap="+scalar(effects));
      if(values instanceof ArrayReference a)for(int i=0;i<a.length();i++){Value list=a.getValue(i);if(list==null)continue;lines.add("skill1177.effectScopeIndex="+i+" size="+scalar(field(list,"size")));Value data=field(list,"elementData");if(data instanceof ArrayReference items)for(Value effect:items.getValues())if(effect instanceof ObjectReference object)lines.add("effect="+scalar(effect)+" loader="+(object.referenceType().classLoader()==null?0:object.referenceType().classLoader().uniqueID()));}
     }
    }
   }
   var holder=vm.classesByName("org.l2jmobius.gameserver.data.xml.SkillData$SingletonHolder").getFirst();Value skillData=holder.getValue(holder.fieldByName("INSTANCE"));
   Value table=field(field(skillData,"_skillsByHash"),"table");
   if(table instanceof ArrayReference buckets)for(int level=1;level<=5;level++) {
    int hash=1177*1021+level,index=(hash^(hash>>>16))&(buckets.length()-1);Value node=buckets.getValue(index);
    for(int n=0;node instanceof ObjectReference&&n<64;n++,node=field(node,"next")) {
     if(!Integer.toString(hash).equals(scalar(field(field(node,"key"),"value"))))continue;Value skill=field(node,"val");lines.add("GLOBAL_SKILL1177_LEVEL="+level+" id="+scalar(field(skill,"_id")));
     Value values=field(field(skill,"_effectLists"),"vals");if(values instanceof ArrayReference a)for(int i=0;i<a.length();i++){Value list=a.getValue(i);if(list==null)continue;lines.add("global.effectScopeIndex="+i+" size="+scalar(field(list,"size")));Value data=field(list,"elementData");if(data instanceof ArrayReference items)for(Value effect:items.getValues())if(effect instanceof ObjectReference object)lines.add("global.effect="+scalar(effect)+" loader="+(object.referenceType().classLoader()==null?0:object.referenceType().classLoader().uniqueID()));}
    }
   }
   lines.add("MagicalDamage.loadedTypes="+vm.classesByName("handlers.skill.effects.MagicalDamage").size());
   for(var effectType:vm.classesByName("handlers.skill.effects.MagicalDamage"))for(var method:effectType.methodsByName("onStart"))lines.add("MagicalDamage.onStart.lines="+method.allLineLocations().stream().map(Location::lineNumber).toList());
   ReferenceType systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();
   Value system=systemType.getValue(systemType.fieldByName("_configuredInstance")),travel=field(system,"_visibleFarmTravel");
   Value materialization=field(system,"_materializationService");
   Value travelMaterialization=field(travel,"_materialization"); lines.add("SERVICES system="+(materialization instanceof ObjectReference o?o.uniqueID():0)+" travel="+(travelMaterialization instanceof ObjectReference o?o.uniqueID():0)+" systemState="+scalar(field(materialization,"_state"))+" travelState="+scalar(field(travelMaterialization,"_state")));
   for(ObjectReference entry:entries(field(materialization,"_activeByProfile"))) {
    String profile=scalar(field(field(entry,"key"),"value"));Value owned=field(field(entry,"val"),"_materializedPlayer");lines.add("CAPACITY_OCCUPANT profile="+profile+" state="+scalar(field(owned,"_state"))+" outstanding="+scalar(field(field(field(owned,"_nativeWork"),"_outstanding"),"size"))+" playerDead="+scalar(field(field(owned,"_player"),"_isDead")));if(!profiles.contains(profile))continue;
    Value actor=field(field(entry,"val"),"_materializedPlayer"),p=field(actor,"_player"),scope=field(actor,"_nativeWork"),playerOwner=field(p,"_nativeWorkOwner");
    lines.add("MATERIALIZED PROFILE="+profile+" actor="+((ObjectReference)actor).uniqueID()+" serviceScope="+((ObjectReference)scope).uniqueID()+" playerOwner="+(playerOwner instanceof ObjectReference o?o.uniqueID():0));
    for(String f:List.of("_state","_actionAdmissionOpen","_materializedAtNanos","_actionGeneration"))lines.add("materialized."+f+"="+scalar(field(actor,f)));
    for(String f:List.of("_state","_permanentSeal","_failure","_epoch"))lines.add("serviceScope."+f+"="+scalar(field(scope,f)));
    Value identity=field(scope,"_identity");lines.add("scopeIdentity.closed="+scalar(field(identity,"_closed")));
   }
   for(ObjectReference entry:entries(field(field(system,"_visibleAutoPlay"),"_sessions"))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;Value value=field(entry,"val");if(value==null)value=field(entry,"value");lines.add("AUTOPLAY PROFILE="+profile);
    if(value instanceof ObjectReference session)for(Field f:session.referenceType().allFields())if(!f.isStatic()){Value v=session.getValue(f);if(v instanceof PrimitiveValue||v instanceof StringReference)lines.add("session."+f.name()+"="+scalar(v));}
   }
   for(String mapName:List.of("_journeys","_attempts","_pendingStores","_terminalReasons"))for(ObjectReference entry:entries(field(travel,mapName))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;
    Value value=field(entry,"val");if(value==null)value=field(entry,"value");lines.add("TRAVEL "+mapName+" PROFILE="+profile);
    if(value instanceof ObjectReference object)for(Field f:object.referenceType().allFields())if(!f.isStatic()) {
     Value v=object.getValue(f);lines.add("travel."+f.name()+"="+scalar(v));
     if(f.name().equals("attempt")&&v instanceof ObjectReference attempt)for(Field a:attempt.referenceType().allFields())if(!a.isStatic())lines.add("travel.attempt."+a.name()+"="+scalar(attempt.getValue(a)));
    }
   }
   Value knowledge=field(field(system,"_gameKnowledgeService"),"_snapshot");
   for(String f:List.of("_datasetId","_datasetVersion","_topologyHash","_combinedHash","_itemsHash","_npcDropSpoilHash","_spawnHash","_recipeHash","_manorHash","_classCapabilityHash","_contentRequirementHash"))lines.add("knowledge."+f+"="+scalar(field(knowledge,f)));
   var playerConfig=vm.classesByName("org.l2jmobius.gameserver.config.PlayerConfig").getFirst();
   for(String f:List.of("AUTO_LOOT","AUTO_LOOT_HERBS","AUTO_LOOT_SLOT_LIMIT"))lines.add("nativeLoot."+f+"="+scalar(playerConfig.getValue(playerConfig.fieldByName(f))));
   Value lootItems=playerConfig.getValue(playerConfig.fieldByName("AUTO_LOOT_ITEM_IDS"));List<String> lootIds=new ArrayList<>();for(ObjectReference item:entries(field(lootItems,"map")))lootIds.add(scalar(field(field(item,"key"),"value")));Collections.sort(lootIds);lines.add("nativeLoot.AUTO_LOOT_ITEM_IDS="+lootIds);
   Value ecology=field(system,"_populationEcology");
   for(String f:List.of("_workerInFlight","_workerStarted","_activeProfile","_wakeFailure","_stopping","_preparationSlots","_focusId","_focusBatches","_lastBatchElapsedMillis","_pulses","_lastFailure","_metadataDraining","_inventoryReady","_populationPlanApplied"))lines.add("ecology."+f+"="+scalar(field(ecology,f)));
   for(ObjectReference entry:entries(field(ecology,"_entries"))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;
    Value value=field(entry,"value");if(value==null)value=field(entry,"val");
    lines.add("ECOLOGY PROFILE="+profile);
    if(value instanceof ObjectReference object)for(Field f:object.referenceType().allFields()) {
     if(f.isStatic())continue;Value item=object.getValue(f);lines.add(f.name()+"="+scalar(item));
     if(List.of("_stored","_historicalSnapshot").contains(f.name())&&item instanceof ObjectReference nested)for(Field nf:nested.referenceType().allFields())if(!nf.isStatic()) {
      Value n=nested.getValue(nf);lines.add(f.name()+"."+nf.name()+"="+scalar(n));
      if(nf.name().equals("state")&&n instanceof ObjectReference state)for(Field sf:state.referenceType().allFields())if(!sf.isStatic()){Value sv=state.getValue(sf);if(sv instanceof PrimitiveValue||sv instanceof StringReference||List.of("status","tier").contains(sf.name()))lines.add(f.name()+".state."+sf.name()+"="+scalar(sv));}
     }
    }
   }
   for(ObjectReference entry:entries(field(ecology,"_demandFacts"))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;
    Value value=field(entry,"value");if(value==null)value=field(entry,"val");lines.add("DEMAND PROFILE="+profile);
    if(value instanceof ObjectReference object)for(Field f:object.referenceType().allFields())if(!f.isStatic())lines.add(f.name()+"="+scalar(object.getValue(f)));
   }
   for(String q:List.of("_due","_materializationDue","_nativeReleaseDue")) {
    Value deque=field(ecology,q),array=field(deque,"elements"); if(array instanceof ArrayReference a){int head=((IntegerValue)field(deque,"head")).value(),tail=((IntegerValue)field(deque,"tail")).value(),rank=0;List<String> ids=new ArrayList<>();for(int i=head;i!=tail;i=(i+1)%a.length(),rank++){Value v=a.getValue(i);String id=scalar(field(v,"value"));if(profiles.contains(id))ids.add(id+"@rank="+rank);}lines.add("ecology."+q+".selected="+ids);lines.add("ecology."+q+".size="+rank);}
   }
   for(String mapName:List.of("_journeys","_attempts","_pendingStores","_terminalReasons"))for(ObjectReference entry:entries(field(travel,mapName))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;
    Value value=field(entry,"val");lines.add("TRAVEL PROFILE="+profile+" map="+mapName);
    if(value instanceof ObjectReference object)for(Field f:object.referenceType().allFields()) {
     if(f.isStatic())continue;Value item=object.getValue(f);lines.add(f.name()+"="+scalar(item));
     if(f.name().equals("attempt")||f.name().equals("goal"))if(item instanceof ObjectReference nested)for(Field nf:nested.referenceType().allFields()) {
      if(!nf.isStatic()){Value n=nested.getValue(nf);if(n instanceof PrimitiveValue||n instanceof StringReference)lines.add(f.name()+"."+nf.name()+"="+scalar(n));}
     }
    }
   }
  }finally{vm.dispose();}
  Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  System.out.println("READ_ONLY_CAPTURE="+output+" lines="+lines.size());
 }
}
