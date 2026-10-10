import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.util.*;
/** One exact actor instance, original paid cast boundary; no target method invocation. */
class TraceCast031 {
 static void traceTravel(VirtualMachine vm,List<String> lines,String profile,boolean actionTrace)throws Exception {
  var systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();
  var system=systemType.getValue(systemType.fieldByName("_configuredInstance"));
  var travel=(ObjectReference)ReadNativeState031.field(system,"_visibleFarmTravel");
  if(actionTrace){
   Value materialization=ReadNativeState031.field(system,"_materializationService");ObjectReference exact=null;
   for(ObjectReference e:ReadNativeState031.entries(ReadNativeState031.field(materialization,"_activeByProfile")))if(profile.equals(ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(e,"key"),"value"))))exact=(ObjectReference)ReadNativeState031.field(ReadNativeState031.field(e,"val"),"_materializedPlayer");
   if(exact==null)throw new IllegalStateException("EXACT_MATERIALIZATION_ABSENT");travel=exact;
  }
  var type=travel.referenceType();var requests=vm.eventRequestManager();
  var bp=requests.createBreakpointRequest(type.locationsOfLine(actionTrace?328:159).getFirst());bp.addInstanceFilter(travel);bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);bp.enable();
  StepRequest step=null;long deadline=System.nanoTime()+30_000_000_000L;int foreign=0,steps=0;boolean done=false,deep=false;
  try {
   while(!done&&System.nanoTime()<deadline&&foreign<8&&steps<48) {
    EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
    try {
     for(Event event:events)if(event instanceof LocatableEvent hit) {
      long start=System.nanoTime();StackFrame frame=hit.thread().frame(0);
      if(event instanceof BreakpointEvent) {
       var id=actionTrace?null:frame.getValue(frame.visibleVariableByName("profileId"));if(!actionTrace&&!profile.equals(ReadNativeState031.scalar(id))){foreign++;continue;}
       bp.disable();step=requests.createStepRequest(hit.thread(),StepRequest.STEP_LINE,actionTrace?StepRequest.STEP_INTO:StepRequest.STEP_OVER);step.addClassFilter(actionTrace?"org.l2jmobius.gameserver.phantoms.player.*":type.name());step.addClassExclusionFilter("org.l2jmobius.gameserver.phantoms.player.PhantomCleanupIncident");step.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);step.enable();
       for(StackFrame f:hit.thread().frames().stream().limit(12).toList())lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());
      }
      if(!actionTrace&&!deep&&!frame.location().method().name().equals("advance")){done=true;break;}
      if(!actionTrace&&!deep&&frame.location().method().name().equals("advance")&&frame.location().lineNumber()==165){step.disable();requests.deleteEventRequest(step);step=requests.createStepRequest(hit.thread(),StepRequest.STEP_LINE,StepRequest.STEP_INTO);step.addClassFilter("org.l2jmobius.gameserver.phantoms.player.*");step.addClassExclusionFilter("org.l2jmobius.gameserver.phantoms.player.PhantomCleanupIncident");step.addClassExclusionFilter("org.l2jmobius.gameserver.phantoms.player.PhantomCleanupIncident");step.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);step.enable();deep=true;}
      steps++;if((actionTrace||deep)&&frame.thisObject()!=null){ObjectReference self=frame.thisObject();lines.add("this="+self.referenceType().name()+":"+self.uniqueID());for(String f:List.of("_state","_actionAdmissionOpen","_epoch","_permanentSeal","_nativeWork","_identity","_player","_closed"))lines.add("this."+f+"="+ReadNativeState031.scalar(ReadNativeState031.field(self,f)));}lines.add("UTC="+java.time.Instant.now()+" profile="+profile+" source="+frame.location().sourceName()+":"+frame.location().lineNumber());
      for(var local:frame.visibleVariables()) {
       Value value=frame.getValue(local);lines.add("local "+local.name()+"="+ReadNativeState031.scalar(value));
      if(local.name().equals("outcome")&&value instanceof ArrayReference a&&a.length()>0)lines.add("outcome.first="+ReadNativeState031.scalar(a.getValue(0)));
      if(local.name().equals("participant")) {Value owner=ReadNativeState031.field(value,"owner");for(String f:List.of("_state","_permanentSeal","_failure"))lines.add("participant.owner."+f+"="+ReadNativeState031.scalar(ReadNativeState031.field(owner,f)));lines.add("participant.owner.overflow="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(owner,"_evidence"),"_overflow")));}
      if(local.name().equals("targets")){lines.add("targets.size="+ReadNativeState031.scalar(ReadNativeState031.field(value,"size")));Value target=ReadNativeState031.field(ReadNativeState031.field(value,"first"),"item");lines.add("targets.first="+ReadNativeState031.scalar(target));lines.add("targets.dead="+ReadNativeState031.scalar(ReadNativeState031.field(target,"_isDead")));lines.add("targets.hp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(target,"_status"),"_currentHp")));}
       if(List.of("player","action","state","targetAnchor","journey","existingAttempt","pending").contains(local.name())&&value instanceof ObjectReference o)for(Field f:o.referenceType().allFields()) {
        if(f.isStatic())continue;Value v=o.getValue(f);if(v instanceof PrimitiveValue||v instanceof StringReference||List.of("_state","_party","state","identity","position").contains(f.name()))lines.add(local.name()+"."+f.name()+"="+ReadNativeState031.scalar(v));
       }
      }
      lines.add("captureNanos="+(System.nanoTime()-start));
     }
    } finally {events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
   }
  } finally {if(step!=null){step.disable();requests.deleteEventRequest(step);}bp.disable();requests.deleteEventRequest(bp);}
  lines.add("guardSteps="+steps+" otherNativeActorsResumed="+foreign+" NO_VM_SUSPEND=true");
 }
 static void traceBackground(VirtualMachine vm,String profile,List<String> lines)throws Exception {
  var requests=vm.eventRequestManager();var type=vm.classesByName("org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority").getFirst();
  List<BreakpointRequest> points=new ArrayList<>();ThreadReference exact=null;int foreign=0,count=0;boolean done=false;long deadline=System.nanoTime()+40_000_000_000L;
  try {
   var entry=requests.createBreakpointRequest(type.locationsOfLine(470).getFirst());entry.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);entry.enable();points.add(entry);
   while(!done&&System.nanoTime()<deadline&&foreign<8){EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
    try {for(Event event:events)if(event instanceof BreakpointEvent hit){long began=System.nanoTime();var frame=hit.thread().frame(0);int line=frame.location().lineNumber();
     if(line==470){Value state=frame.getValue(frame.visibleVariableByName("state"));if(!profile.equals(ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(state,"identity"),"profileId")))){foreign++;continue;}
      exact=hit.thread();entry.disable();for(int n:List.of(473,478,483,484,487,497)){var bp=requests.createBreakpointRequest(type.locationsOfLine(n).getFirst());bp.addThreadFilter(exact);bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);bp.enable();points.add(bp);}
     }
     count++;lines.add("UTC="+java.time.Instant.now()+" profile="+profile+" source=L2jPhantomBackgroundAuthority.java:"+line);
     for(var local:frame.visibleVariables()){Value v=frame.getValue(local);lines.add("local "+local.name()+"="+ReadNativeState031.scalar(v));if(v instanceof ObjectReference o){for(Field f:o.referenceType().allFields())if(!f.isStatic()){Value value=o.getValue(f);if(value instanceof PrimitiveValue||value instanceof StringReference)lines.add(local.name()+"."+f.name()+"="+ReadNativeState031.scalar(value));if(f.name().equals("position")&&value instanceof ObjectReference position)for(Field pf:position.referenceType().allFields())if(!pf.isStatic())lines.add("state.position."+pf.name()+"="+ReadNativeState031.scalar(position.getValue(pf)));}}}
     int n=0;for(var f:hit.thread().frames()){if(n++==8)break;lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());}
     lines.add("captureNanos="+(System.nanoTime()-began));if(List.of(473,478,484,487).contains(line))done=true;
    }}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
   }
  }finally{for(var bp:points){bp.disable();requests.deleteEventRequest(bp);}}
  lines.add("BACKGROUND_POINTS="+count+" foreignResumed="+foreign+" NO_VM_SUSPEND=true");
 }
 static void traceGeneration(VirtualMachine vm,String profile,List<String> lines)throws Exception {
  var systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();Value system=systemType.getValue(systemType.fieldByName("_configuredInstance"));
  var history=(ObjectReference)ReadNativeState031.field(system,"_historicalBackgroundService");var requests=vm.eventRequestManager();
  var point=requests.createBreakpointRequest(history.referenceType().locationsOfLine(1193).getFirst());point.addInstanceFilter(history);point.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);point.enable();
  boolean done=false;int foreign=0;long deadline=System.nanoTime()+40_000_000_000L;
  try {while(!done&&foreign<8&&System.nanoTime()<deadline){EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
   try {for(Event event:events)if(event instanceof BreakpointEvent hit){long began=System.nanoTime();var frame=hit.thread().frame(0);var values=frame.getValues(frame.visibleVariables());
    Value id=frame.getValue(frame.visibleVariableByName("profileId"));if(!profile.equals(ReadNativeState031.scalar(id))){foreign++;continue;}
    lines.add("UTC="+java.time.Instant.now()+" profile="+profile+" source=PhantomHistoricalBackgroundService.java:1193");
    for(var item:values.entrySet()){String name=item.getKey().name();Value value=item.getValue();if(!List.of("replacement","catchup","projected","live","stored").contains(name))continue;
     if(name.equals("replacement"))value=ReadNativeState031.field(value,"generation");if(name.equals("catchup"))value=ReadNativeState031.field(value,"state");
     for(String field:List.of("knowledgeGeneration","topologyGeneration","authorityHashes","hashes","x","y","z","instanceId","rowVersion","goal")){Value v=ReadNativeState031.field(value,field);if(v==null)continue;lines.add(name+"."+field+"="+ReadNativeState031.scalar(v));
      if((field.equals("authorityHashes")||field.equals("hashes"))&&v instanceof ObjectReference hash)for(String h:List.of("knowledge","topology","progression","commerce"))lines.add(name+"."+field+"."+h+"="+ReadNativeState031.scalar(ReadNativeState031.field(hash,h)));
     }
    }
    for(var f:hit.thread().frames().stream().limit(8).toList())lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());lines.add("captureNanos="+(System.nanoTime()-began));done=true;
   }}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
  }}finally{point.disable();requests.deleteEventRequest(point);}lines.add("GENERATION_HIT="+done+" foreignResumed="+foreign+" NO_VM_SUSPEND=true");
 }
 static void traceHistory(VirtualMachine vm,String profile,List<String> lines)throws Exception {
  var systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();Value system=systemType.getValue(systemType.fieldByName("_configuredInstance"));
  var history=(ObjectReference)ReadNativeState031.field(system,"_historicalBackgroundService");var requests=vm.eventRequestManager();
  var entry=requests.createBreakpointRequest(history.referenceType().locationsOfLine(1122).getFirst());entry.addInstanceFilter(history);entry.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);entry.enable();
  StepRequest step=null;MethodExitRequest exit=null;int foreign=0,count=0;boolean done=false;long deadline=System.nanoTime()+40_000_000_000L;
  try {while(!done&&System.nanoTime()<deadline&&foreign<8&&count<64){EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
   try {for(Event event:events)if(event instanceof LocatableEvent hit){long began=System.nanoTime();
    if(event instanceof MethodExitEvent returned){if(returned.method().name().equals("prepareVisibleDecision")){lines.add("RETURN="+(vm.canGetMethodReturnValues()?ReadNativeState031.scalar(returned.returnValue()):"UNAVAILABLE")+" source="+returned.location().sourceName()+":"+returned.location().lineNumber());done=true;}continue;}
    var frame=hit.thread().frame(0);if(event instanceof BreakpointEvent){Value id=frame.getValue(frame.visibleVariableByName("profileId"));if(!profile.equals(ReadNativeState031.scalar(id))){foreign++;continue;}entry.disable();
     step=requests.createStepRequest(hit.thread(),StepRequest.STEP_LINE,StepRequest.STEP_OVER);step.addClassFilter(history.referenceType().name());step.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);step.enable();
     exit=requests.createMethodExitRequest();exit.addInstanceFilter(history);exit.addClassFilter(history.referenceType());exit.addThreadFilter(hit.thread());exit.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);exit.enable();
     for(var f:hit.thread().frames().stream().limit(8).toList())lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());
    }
    if(!frame.location().method().name().equals("prepareVisibleDecision"))continue;count++;lines.add("UTC="+java.time.Instant.now()+" profile="+profile+" source="+frame.location().sourceName()+":"+frame.location().lineNumber());
    var values=frame.getValues(frame.visibleVariables());for(var item:values.entrySet()){String name=item.getKey().name();Value value=item.getValue();lines.add("local "+name+"="+ReadNativeState031.scalar(value));
     if(List.of("runtime","stored","admitted","baseline","checkpoint","catchup").contains(name)&&value instanceof ObjectReference object){for(String field:List.of("goalId","goalRevision","goalType","goalStatus","inFlight","persistenceInFlight","worldPresent","actionAdmissionOpen","characterObjectId","materializedAtNanos","state","outcome","reason","rowVersion","goal")){Value v=ReadNativeState031.field(object,field);if(v!=null)lines.add(name+"."+field+"="+ReadNativeState031.scalar(v));}}
    }lines.add("captureNanos="+(System.nanoTime()-began));
   }}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
  }}finally{entry.disable();requests.deleteEventRequest(entry);if(step!=null){step.disable();requests.deleteEventRequest(step);}if(exit!=null){exit.disable();requests.deleteEventRequest(exit);}}
  lines.add("HISTORY_POINTS="+count+" foreignResumed="+foreign+" NO_VM_SUSPEND=true");
 }
 static void tracePolicy(VirtualMachine vm,String profile,List<String> lines)throws Exception {
  var systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();Value system=systemType.getValue(systemType.fieldByName("_configuredInstance"));ObjectReference policy=null;
  for(ObjectReference e:ReadNativeState031.entries(ReadNativeState031.field(ReadNativeState031.field(system,"_visibleAutoPlay"),"_sessions")))if(profile.equals(ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(e,"key"),"value"))))policy=(ObjectReference)ReadNativeState031.field(ReadNativeState031.field(e,"val"),"_policy");
  if(policy==null)throw new IllegalStateException("EXACT_POLICY_ABSENT");var requests=vm.eventRequestManager();var bp=requests.createBreakpointRequest(policy.referenceType().locationsOfLine(544).getFirst());bp.addInstanceFilter(policy);bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);bp.enable();long deadline=System.nanoTime()+40_000_000_000L;boolean done=false;
  try{while(!done&&System.nanoTime()<deadline){EventSet events=vm.eventQueue().remove(500);if(events==null)continue;try{for(Event event:events)if(event instanceof BreakpointEvent hit){long began=System.nanoTime();var frame=hit.thread().frame(0);lines.add("UTC="+java.time.Instant.now()+" profile="+profile+" FALSE_GUARD=PhantomVisibleAutoPlay.java:"+frame.location().lineNumber());for(var local:frame.visibleVariables()){Value v=frame.getValue(local);lines.add("local "+local.name()+"="+ReadNativeState031.scalar(v));if(v instanceof ObjectReference o)for(String f:List.of("_profileId","_objectId","_epoch","_state","_isDead","_waitTypeSitting","_permanentSeal","_failure","_instanceId"))lines.add(local.name()+"."+f+"="+ReadNativeState031.scalar(ReadNativeState031.field(o,f)));}int n=0;for(var f:hit.thread().frames()){if(n++==8)break;lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());}lines.add("captureNanos="+(System.nanoTime()-began));done=true;}}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}}}finally{bp.disable();requests.deleteEventRequest(bp);}lines.add("POLICY_FALSE_HIT="+done+" NO_VM_SUSPEND=true");
 }
 static void traceLaunch(VirtualMachine vm,ObjectReference player,List<String> lines)throws Exception {
  var requests=vm.eventRequestManager();var type=vm.classesByName("org.l2jmobius.gameserver.model.actor.Creature").getFirst();List<BreakpointRequest> points=new ArrayList<>();long cast=0;int count=0;boolean done=false;long deadline=System.nanoTime()+40_000_000_000L;
  try {
   for(int line:List.of(5861,5945,6094)){var locations=type.locationsOfLine(line);if(locations.isEmpty()){lines.add("UNAVAILABLE_LINE="+line);continue;}var bp=requests.createBreakpointRequest(locations.getFirst());bp.addInstanceFilter(player);bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);bp.enable();points.add(bp);}
   while(!done&&System.nanoTime()<deadline&&count<9){EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
    try {for(Event event:events)if(event instanceof BreakpointEvent hit){long began=System.nanoTime();var frame=hit.thread().frame(0);Value mut=frame.getValue(frame.visibleVariableByName("mut"));Value skill=ReadNativeState031.field(mut,"_skill");if(!"1177".equals(ReadNativeState031.scalar(ReadNativeState031.field(skill,"_id")))||!(mut instanceof ObjectReference exact))continue;if(cast==0)cast=exact.uniqueID();if(cast!=exact.uniqueID())continue;
     Value targets=ReadNativeState031.field(mut,"_targets"),target=ReadNativeState031.field(ReadNativeState031.field(targets,"first"),"item");if(target==null&&ReadNativeState031.field(targets,"elementData") instanceof ArrayReference data&&data.length()>0)target=data.getValue(0);
     var localTarget=frame.visibleVariableByName("target");if(localTarget!=null)target=frame.getValue(localTarget);lines.add("targetsType="+ReadNativeState031.scalar(targets));
     lines.add("UTC="+java.time.Instant.now()+" source=Creature.java:"+frame.location().lineNumber()+" cast="+cast+" phase="+ReadNativeState031.scalar(ReadNativeState031.field(mut,"_phase"))+" npc="+ReadNativeState031.scalar(ReadNativeState031.field(target,"_objectId"))+" dead="+ReadNativeState031.scalar(ReadNativeState031.field(target,"_isDead"))+" hp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(target,"_status"),"_currentHp"))+" mp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(player,"_status"),"_currentMp")));
     for(var local:frame.visibleVariables())if(List.of("mpConsume","skipRange","skipLOS","skipPeaceZone").contains(local.name()))lines.add("local "+local.name()+"="+ReadNativeState031.scalar(frame.getValue(local)));int n=0;for(var f:hit.thread().frames()){if(n++==8)break;lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());}count++;lines.add("captureNanos="+(System.nanoTime()-began));if(frame.location().lineNumber()==6094){done="true".equals(ReadNativeState031.scalar(ReadNativeState031.field(target,"_isDead")));cast=0;}
    }}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
   }
  }finally{for(var bp:points){bp.disable();requests.deleteEventRequest(bp);}}
  lines.add("LAUNCH_POINTS="+count+" NO_VM_SUSPEND=true");
 }
 static void traceEffect(VirtualMachine vm,ObjectReference player,List<String> lines)throws Exception {
  var requests=vm.eventRequestManager();var creature=vm.classesByName("org.l2jmobius.gameserver.model.actor.Creature").getFirst();
  var first=requests.createBreakpointRequest(creature.locationsOfLine(6094).getFirst());first.addInstanceFilter(player);first.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);first.enable();
  List<BreakpointRequest> downstream=new ArrayList<>();int count=0;long deadline=System.nanoTime()+40_000_000_000L;boolean done=false;
  try {
   while(!done&&System.nanoTime()<deadline&&count<20) {
    EventSet events=vm.eventQueue().remove(500);if(events==null)continue;
    try {for(Event event:events)if(event instanceof BreakpointEvent hit) {
     long began=System.nanoTime();var frame=hit.thread().frame(0);
     if(hit.request()==first) {
      Value mut=frame.getValue(frame.visibleVariableByName("mut"));if(!"1177".equals(ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(mut,"_skill"),"_id"))))continue;
      Value firstTarget=ReadNativeState031.field(ReadNativeState031.field(ReadNativeState031.field(mut,"_targets"),"first"),"item");
      lines.add("FIRST_BEFORE_BREAKPOINT_SETUP UTC="+java.time.Instant.now()+" target="+ReadNativeState031.scalar(firstTarget)+" dead="+ReadNativeState031.scalar(ReadNativeState031.field(firstTarget,"_isDead"))+" hp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(firstTarget,"_status"),"_currentHp")));
      first.disable();
      for(var target:List.of(new String[]{"org.l2jmobius.gameserver.model.actor.Creature","6250"},new String[]{"handlers.skill.effects.MagicalDamage","77"},new String[]{"org.l2jmobius.gameserver.model.actor.status.CreatureStatus","148"},new String[]{"org.l2jmobius.gameserver.model.actor.status.CreatureStatus","345"},new String[]{"org.l2jmobius.gameserver.model.actor.Creature","6107"})) {       var types=vm.classesByName(target[0]);if(types.isEmpty()){lines.add("UNAVAILABLE_CLASS="+target[0]);continue;}
       var locations=types.getFirst().locationsOfLine(Integer.parseInt(target[1]));if(locations.isEmpty()){lines.add("UNAVAILABLE_LINE="+Arrays.toString(target));continue;}
       var bp=requests.createBreakpointRequest(locations.getFirst());bp.addThreadFilter(hit.thread());bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);bp.enable();downstream.add(bp);
      }
     }
     count++;lines.add("UTC="+java.time.Instant.now()+" source="+frame.location().declaringType().name()+":"+frame.location().lineNumber());
     if(frame.thisObject()!=null){Value victim=ReadNativeState031.field(frame.thisObject(),"_creature");if(victim!=null)lines.add("status.creatureDead="+ReadNativeState031.scalar(ReadNativeState031.field(victim,"_isDead")));}
     try {for(var local:frame.visibleVariables()) {
      Value value=frame.getValue(local);lines.add("local "+local.name()+"="+ReadNativeState031.scalar(value));
      if(local.name().equals("outcome")&&value instanceof ArrayReference a&&a.length()>0)lines.add("outcome.first="+ReadNativeState031.scalar(a.getValue(0)));
      if(local.name().equals("participant")) {Value owner=ReadNativeState031.field(value,"owner");for(String f:List.of("_state","_permanentSeal","_failure"))lines.add("participant.owner."+f+"="+ReadNativeState031.scalar(ReadNativeState031.field(owner,f)));lines.add("participant.owner.overflow="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(owner,"_evidence"),"_overflow")));}
      if(local.name().equals("targets")){lines.add("targets.size="+ReadNativeState031.scalar(ReadNativeState031.field(value,"size")));Value target=ReadNativeState031.field(ReadNativeState031.field(value,"first"),"item");lines.add("targets.first="+ReadNativeState031.scalar(target));lines.add("targets.dead="+ReadNativeState031.scalar(ReadNativeState031.field(target,"_isDead")));lines.add("targets.hp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(target,"_status"),"_currentHp")));}
      if(value instanceof ObjectReference o&&List.of("target","effected","effector","skill","mut","damageAttacker").contains(local.name())) {
       for(String f:List.of("_objectId","_isDead","_id","_phase","_nativeWorkOwner"))lines.add(local.name()+"."+f+"="+ReadNativeState031.scalar(ReadNativeState031.field(o,f)));
       lines.add(local.name()+".hp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(o,"_status"),"_currentHp")));
      }
     }}catch(AbsentInformationException absent){lines.add("LOCALS_UNAVAILABLE="+frame.location().declaringType().name());}
     for(StackFrame f:hit.thread().frames().stream().limit(10).toList())lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());
     lines.add("captureNanos="+(System.nanoTime()-began));if(frame.location().declaringType().name().equals(creature.name())&&frame.location().lineNumber()==6107)done=true;
    }}finally{events.resume();lines.add("EVENT_THREAD_RESUMED=true");}
   }
  }finally {first.disable();requests.deleteEventRequest(first);for(var bp:downstream){bp.disable();requests.deleteEventRequest(bp);}}
  lines.add("effectBoundaryEvents="+count+" NO_VM_SUSPEND=true");
 }
 public static void main(String[] args)throws Exception {
  Path output=Path.of(args[1]); if(Files.exists(output))throw new IllegalStateException("Immutable output");
  var connector=Bootstrap.virtualMachineManager().attachingConnectors().stream().filter(c->c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
  var options=connector.defaultArguments(); options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]); options.get("timeout").setValue("5000");
  VirtualMachine vm=connector.attach(options); List<String> lines=new ArrayList<>();
  try {
   ReferenceType observer=vm.classesByName(args[3]).getFirst();
   ObjectReference player=null;
   Value owners=observer.getValue(observer.fieldByName("RECEIPT_OWNERS"));
   for(ObjectReference entry:ReadNativeState031.entries(owners)) {
    Value key=ReadNativeState031.field(entry,"key");
    if(args[2].equals(ReadNativeState031.scalar(ReadNativeState031.field(key,"profileId"))))player=(ObjectReference)ReadNativeState031.field(ReadNativeState031.field(entry,"val"),"_player");
   }
   if(args.length==6&&args[5].equals("GENERATION")){traceGeneration(vm,args[2],lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   if(args.length==6&&args[5].equals("HISTORY")){traceHistory(vm,args[2],lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   if(args.length==6&&args[5].equals("BACKGROUND")){traceBackground(vm,args[2],lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   if(args.length==6&&args[5].equals("POLICY")){tracePolicy(vm,args[2],lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   var systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();
   Value system=systemType.getValue(systemType.fieldByName("_configuredInstance"));
   player=null;
   for(ObjectReference entry:ReadNativeState031.entries(ReadNativeState031.field(ReadNativeState031.field(system,"_materializationService"),"_activeByProfile"))) {
    if(args[2].equals(ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(entry,"key"),"value"))))player=(ObjectReference)ReadNativeState031.field(ReadNativeState031.field(ReadNativeState031.field(entry,"val"),"_materializedPlayer"),"_player");
   }
   if(player==null)throw new IllegalStateException("EXACT_ACTOR_NOT_CURRENT");
   if(args.length==6&&args[5].equals("LAUNCH")){traceLaunch(vm,player,lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   if(args.length==6&&args[5].equals("EFFECT")){traceEffect(vm,player,lines);Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   if(args.length==6 && List.of("TRAVEL","ACTION").contains(args[5])){traceTravel(vm,lines,args[2],args[5].equals("ACTION"));Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(String.join("\n",lines));return;}
   ReferenceType creature=vm.classesByName("org.l2jmobius.gameserver.model.actor.Creature").getFirst();
   var request=vm.eventRequestManager().createBreakpointRequest(creature.locationsOfLine(Integer.parseInt(args[4])).getFirst());
   request.addInstanceFilter(player); request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
   long deadline=System.nanoTime()+40_000_000_000L; int hits=0;
   while(System.nanoTime()<deadline&&hits<8) {
    EventSet events=vm.eventQueue().remove(1000); if(events==null)continue; boolean dead=false;
    try {
     for(Event event:events)if(event instanceof BreakpointEvent hit) {
      long start=System.nanoTime(); hits++; StackFrame frame=hit.thread().frame(0);
      Value mut=frame.getValue(frame.visibleVariableByName("mut"));
      Value skill=ReadNativeState031.field(mut,"_skill"), targets=ReadNativeState031.field(mut,"_targets");
      Value first=ReadNativeState031.field(targets,"first"), target=ReadNativeState031.field(first,"item");
      if(target==null) {
       Value data=ReadNativeState031.field(targets,"elementData"); if(data instanceof ArrayReference a&&a.length()>0)target=a.getValue(0);
      }
      dead="true".equals(ReadNativeState031.scalar(ReadNativeState031.field(target,"_isDead")));
      lines.add("UTC="+java.time.Instant.now()+" actor="+ReadNativeState031.scalar(ReadNativeState031.field(player,"_objectId"))+" skill="+ReadNativeState031.scalar(ReadNativeState031.field(skill,"_id"))+" phase="+ReadNativeState031.scalar(ReadNativeState031.field(mut,"_phase"))+" target="+ReadNativeState031.scalar(ReadNativeState031.field(target,"_objectId"))+" targetDead="+dead+" targetHp="+ReadNativeState031.scalar(ReadNativeState031.field(ReadNativeState031.field(target,"_status"),"_currentHp")));
      for(var local:frame.visibleVariables())if(List.of("mpConsume","isSendStatus").contains(local.name()))lines.add("local "+local.name()+"="+ReadNativeState031.scalar(frame.getValue(local)));
      int count=0;for(StackFrame f:hit.thread().frames()){if(count++==12)break;lines.add("stack="+f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());}
      lines.add("captureNanos="+(System.nanoTime()-start));
     }
    }finally{events.resume(); lines.add("EVENT_THREAD_RESUMED=true");}
    if(dead)break;
   }
   request.disable();vm.eventRequestManager().deleteEventRequest(request);
   if(hits==0)lines.add("NO_TARGET_HIT");
  }finally{vm.dispose();}
  Files.write(output,lines,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  System.out.println(String.join("\n",lines));
 }
}
