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
   if(player==null)throw new IllegalStateException("EXACT_ACTOR_NOT_CURRENT");
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
