import com.sun.jdi.*;
import java.nio.file.*;
import java.util.*;
/** Exact selected retained owners, read-only fields; no suspension, invocation or mutation. */
class ReadNativeState031 {
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
   lines.add("UTC="+java.time.Instant.now()+" READ=NONATOMIC_NO_SUSPEND");
   for(ObjectReference entry:entries(owners)) {
    Value key=field(entry,"key"); String profile=scalar(field(key,"profileId"));
    if(!profiles.contains(profile))continue;
    Value owner=field(entry,"val"), player=field(owner,"_player"), sensor=field(owner,"_evidence");
    lines.add("PROFILE="+profile+" epoch="+scalar(field(key,"epoch"))+" object="+scalar(field(key,"objectId")));
    for(String f:List.of("_overflow","_firstUnprovenReason","_firstUnprovenNanos","_damageSequence","_killSequence","_rewardSequence","_farmCycleSequence"))lines.add(f+"="+scalar(field(sensor,f)));
    Value map=field(sensor,"_targets"), node=field(map,"head");
    for(int n=0;node!=null&&n<17;n++) {
     Value target=field(node,"key"), state=field(node,"value"); StringBuilder s=new StringBuilder("target ");
     for(String f:List.of("objectId","instanceId","spawnGeneration"))s.append(f).append('=').append(scalar(field(target,f))).append(' ');
     for(String f:List.of("_damaged","_killed","_exp","_sp","_nextSelected","_counted"))s.append(f).append('=').append(scalar(field(state,f))).append(' ');
     lines.add(s.toString()); node=field(node,"after");
    }
    for(ObjectReference skill:entries(field(player,"_skills"))) {
     Value value=field(skill,"val"); lines.add("skill="+scalar(field(value,"_id"))+":"+scalar(field(value,"_level"))+":"+scalar(field(value,"_name"))+":"+scalar(field(value,"_operateType")));
    }
   }
   ReferenceType systemType=vm.classesByName("org.l2jmobius.gameserver.phantoms.PhantomSystem").getFirst();
   Value system=systemType.getValue(systemType.fieldByName("_configuredInstance")),travel=field(system,"_visibleFarmTravel");
   Value materialization=field(system,"_materializationService");
   Value travelMaterialization=field(travel,"_materialization"); lines.add("SERVICES system="+(materialization instanceof ObjectReference o?o.uniqueID():0)+" travel="+(travelMaterialization instanceof ObjectReference o?o.uniqueID():0)+" systemState="+scalar(field(materialization,"_state"))+" travelState="+scalar(field(travelMaterialization,"_state")));
   for(ObjectReference entry:entries(field(materialization,"_activeByProfile"))) {
    String profile=scalar(field(field(entry,"key"),"value"));if(!profiles.contains(profile))continue;
    Value actor=field(field(entry,"val"),"_materializedPlayer"),p=field(actor,"_player"),scope=field(actor,"_nativeWork"),playerOwner=field(p,"_nativeWorkOwner");
    lines.add("MATERIALIZED PROFILE="+profile+" actor="+((ObjectReference)actor).uniqueID()+" serviceScope="+((ObjectReference)scope).uniqueID()+" playerOwner="+(playerOwner instanceof ObjectReference o?o.uniqueID():0));
    for(String f:List.of("_state","_actionAdmissionOpen","_materializedAtNanos","_actionGeneration"))lines.add("materialized."+f+"="+scalar(field(actor,f)));
    for(String f:List.of("_state","_permanentSeal","_failure","_epoch"))lines.add("serviceScope."+f+"="+scalar(field(scope,f)));
    Value identity=field(scope,"_identity");lines.add("scopeIdentity.closed="+scalar(field(identity,"_closed")));
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
