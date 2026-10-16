import java.nio.file.Path;

import com.sun.tools.attach.VirtualMachine;

/** Operator tooling: requests the existing stock shutdown on one marked incarnation. */
public final class RequestShutdown
{
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 6) || !args[0].matches("[1-9][0-9]*") || !args[1].matches("[0-9a-f]{64}") || !args[2].matches("GameServer|LoginServer"))
		{
			throw new IllegalArgumentException("Exact owned PID/runtime/role/root/database/agent required.");
		}
		RuntimeCampaign058Guard.validate(Path.of(args[3]), args[4]);
		final VirtualMachine vm = VirtualMachine.attach(args[0]);
		try
		{
			final var properties = vm.getSystemProperties();
			if (!args[1].equals(properties.getProperty("phantom.localplay.runtime")) || !args[2].equals(properties.getProperty("phantom.localplay.role")))
			{
				throw new IllegalStateException("Runtime identity mismatch; shutdown not requested.");
			}
			final String root = Path.of(args[3]).toRealPath().toString();
			vm.loadAgent(Path.of(args[5]).toRealPath().toString(), args[1] + "\n" + args[2] + "\n" + root + "\n" + args[4]);
		}
		finally
		{
			vm.detach();
		}
	}
}
