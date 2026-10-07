import com.sun.tools.attach.VirtualMachine;
/** Exact-owned wrapper preflight supplies PID, reviewed agent JAR and immutable spec path. */
public final class AttachContract024
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3) { throw new IllegalArgumentException("pid agentJar specPath required"); }
        final var vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }
}
