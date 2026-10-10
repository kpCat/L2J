import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Exact real-evaluation trace; baseline offsets fail closed, current result uses actual arguments. */
class TraceReadiness032 {
    static Value field(Value value, String name) {
        if (!(value instanceof ObjectReference object)) return null;
        Field member = object.referenceType().fieldByName(name);
        return member == null ? null : object.getValue(member);
    }
    static String scalar(Value value) {
        if (value == null) return "null";
        if (value instanceof StringReference text) return text.value();
        if (value instanceof PrimitiveValue) return value.toString();
        return value.type().name() + "#" + ((ObjectReference)value).uniqueID();
    }
    static Value local(StackFrame frame, String name) throws Exception {
        LocalVariable variable = frame.visibleVariableByName(name);
        return variable == null ? null : frame.getValue(variable);
    }
    static String facts(Value object, String... names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) result.append(name).append('=').append(scalar(field(object, name))).append(' ');
        return result.toString();
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalStateException("Immutable trace exists");
        Set<String> targets = new HashSet<>(Arrays.asList(args[2].split(",")));
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(value -> value.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        options.get("timeout").setValue("5000");
        VirtualMachine vm = connector.attach(options);
        List<String> lines = new ArrayList<>();
        List<EventRequest> requests = new ArrayList<>();
        Map<Long, String> branch = new HashMap<>();
        Map<String, String> last = new HashMap<>();
        Map<Long, MethodExitRequest> returns = new HashMap<>();
        long deadline = System.nanoTime() + 180_000_000_000L;
        int eventsCount = 0;
        try {
            ReferenceType type = vm.classesByName("org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService").getFirst();
            Method method = type.methodsByName("visibleFarmReady").getFirst();
            byte[] bytes = method.bytecodes();
            boolean current = !type.methodsByName("visibleFarmResult").isEmpty();
            // Fail closed on stale baseline source/Jar; offsets are from saved javap.
            if (!current && (bytes.length != 182 || bytes[97] != 3 || bytes[155] != 3 || bytes[181] != (byte)172))
                throw new IllegalStateException("BASELINE_BYTECODE_CHANGED");
            Method evaluated = current ? type.methodsByName("visibleFarmResult").getFirst() : method;
            for (long offset : current ? new long[]{0} : new long[]{97,112,123,152,155,157,181}) {
                var request = vm.eventRequestManager().createBreakpointRequest(evaluated.locationOfCodeIndex(offset));
                request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable(); requests.add(request);
            }
            Method prepare = type.methodsByName("prepareVisibleDecision").getFirst();
            boolean inPrepare = false;
            for (String line : Files.readAllLines(Path.of(args[3]))) {
                if (line.startsWith("  public boolean prepareVisibleDecision(")) { inPrepare = true; continue; }
                if (inPrepare && line.startsWith("  ") && !line.startsWith("    ") && line.contains("(")) break;
                var matcher = java.util.regex.Pattern.compile("^\\s*(\\d+): ireturn$").matcher(line);
                if (inPrepare && matcher.matches()) {
                    var request = vm.eventRequestManager().createBreakpointRequest(prepare.locationOfCodeIndex(Long.parseLong(matcher.group(1))));
                    request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable(); requests.add(request);
                }
            }
            while (System.nanoTime() < deadline && eventsCount < 4096) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                List<String> captured = new ArrayList<>();
                try {
                    for (Event event : events) {
                        if (!(event instanceof LocatableEvent hit)) continue;
                        String methodName = hit.location().method().name();
                        if (!Set.of("visibleFarmReady", "visibleFarmResult", "prepareVisibleDecision").contains(methodName)) continue;
                        long start = System.nanoTime();
                        StackFrame frame = hit.thread().frame(0);
                        String profile = scalar(local(frame, "profileId"));
                        if (!targets.contains(profile)) continue;
                        eventsCount++;
                        long thread = hit.thread().uniqueID();
                        long offset = hit.location().codeIndex();
                        if (methodName.equals("visibleFarmResult")) {
                            List<Value> actual = frame.getArgumentValues();
                            Value goal = actual.get(1);
                            String tuple = scalar(actual.get(4)) + " " + scalar(actual.get(2)) + " " + facts(goal,"goalId","revision");
                            if (tuple.equals(last.put(profile, tuple))) continue;
                            captured.add("UTC="+Instant.now()+" profile="+profile+" firstReason="+scalar(actual.get(4))
                                +" ready="+scalar(actual.get(3))+" epoch="+scalar(actual.get(2))+" goal="+facts(goal,"goalId","revision","target"));
                            for (StackFrame caller : hit.thread().frames().stream().limit(5).toList())
                                captured.add("caller="+caller.location().declaringType().name()+"."+caller.location().method().name()+":"+caller.location().lineNumber());
                            captured.add("PAUSE_NANOS="+(System.nanoTime()-start)+" GAMEPLAY_PROOF=false");
                            continue;
                        }
                        if (event instanceof BreakpointEvent && (methodName.equals("prepareVisibleDecision") || offset == 181)) {
                            if (!returns.containsKey(thread)) {
                                var returned = vm.eventRequestManager().createMethodExitRequest();
                                returned.addClassFilter(type); returned.addThreadFilter(hit.thread());
                                returned.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); returned.enable();
                                returns.put(thread, returned);
                            }
                            continue;
                        }
                        if (event instanceof MethodExitEvent) {
                            var request = returns.remove(thread);
                            if (request != null) vm.eventRequestManager().deleteEventRequest(request);
                        }
                        if (methodName.equals("prepareVisibleDecision")) {
                            if (!(event instanceof MethodExitEvent returned) || !(returned.returnValue() instanceof BooleanValue value)) continue;
                            String outcome = value.value() ? "PREPARE_READY" : "PREPARE_FALSE@" + hit.location().lineNumber();
                            Value stored = local(frame,"stored"), goal = field(stored,"goal"), replacement = local(frame,"replacement");
                            String tuple = outcome + " " + facts(goal,"goalId","revision");
                            if (tuple.equals(last.put("prepare:"+profile, tuple))) continue;
                            captured.add("UTC="+Instant.now()+" profile="+profile+" method=prepareVisibleDecision offset="+offset+" outcome="+outcome
                                +" goal="+facts(goal,"goalId","revision","target")
                                +" replacement="+facts(replacement,"reasonKey","goal")
                                +" runtime="+facts(local(frame,"runtime"),"goalId","goalRevision","inFlight","persistenceInFlight","reasonKey")
                                +" exception="+facts(local(frame,"exception"),"detailMessage")
                                +" PAUSE_NANOS="+(System.nanoTime()-start)+" GAMEPLAY_PROOF=false");
                            continue;
                        }
                        if (offset == 97) branch.put(thread, "COOLDOWN");
                        else if (offset == 112) branch.put(thread, "PROTOCOL_BLOCK");
                        else if (offset == 123) branch.put(thread, "ROUTE_BLOCK");
                        else if (offset == 152) branch.put(thread, "TARGET_EXCLUDED");
                        else if (offset == 157) branch.put(thread, "STORED_GOAL_MISMATCH");
                        String reason = null;
                        if (event instanceof BreakpointEvent && offset == 97) reason = "COOLDOWN";
                        if (event instanceof BreakpointEvent && offset == 155) reason = branch.get(thread);
                        if (event instanceof MethodExitEvent returned && returned.returnValue() instanceof BooleanValue value) {
                            reason = value.value() ? "READY" : branch.getOrDefault(thread, "UNKNOWN");
                        }
                        if (reason == null) continue;
                        Value goal = local(frame, "goal"), episode = local(frame, "episode"), failures = local(frame, "failures");
                        String tuple = reason + " " + scalar(local(frame,"epoch")) + " " + facts(goal,"goalId","revision");
                        if (tuple.equals(last.put(profile, tuple))) continue;
                        captured.add("UTC=" + Instant.now() + " profile=" + profile + " offset=" + offset + " firstReason=" + reason
                            + " epoch=" + scalar(local(frame,"epoch")) + " goal=" + facts(goal,"goalId","revision","target")
                            + " episode=" + facts(episode,"epoch","started","cooldownUntil","reason")
                            + " route=" + facts(field(failures,"_route"),"goalId","revision","epoch","witness")
                            + " protocol=" + facts(field(failures,"_protocol"),"goalId","revision","epoch","reason"));
                        for (StackFrame caller : hit.thread().frames().stream().limit(5).toList())
                            captured.add("caller=" + caller.location().declaringType().name() + "." + caller.location().method().name() + ":" + caller.location().lineNumber());
                        captured.add("PAUSE_NANOS=" + (System.nanoTime()-start) + " GAMEPLAY_PROOF=false");
                    }
                } finally { events.resume(); }
                lines.addAll(captured);
                if (!captured.isEmpty()) Files.write(output, lines, java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            for (EventRequest request : requests) vm.eventRequestManager().deleteEventRequest(request);
            for (EventRequest request : returns.values()) vm.eventRequestManager().deleteEventRequest(request);
            vm.dispose();
            lines.add("TRACE_END_UTC=" + Instant.now() + " events=" + eventsCount + " TARGET_RESUMED=true");
            Files.write(output, lines, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
