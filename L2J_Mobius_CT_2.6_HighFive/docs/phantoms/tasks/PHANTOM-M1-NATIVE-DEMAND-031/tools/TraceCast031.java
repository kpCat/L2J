import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.util.*;
/** One exact actor instance, original paid cast boundary; no target method invocation. */
class TraceCast031 {
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
