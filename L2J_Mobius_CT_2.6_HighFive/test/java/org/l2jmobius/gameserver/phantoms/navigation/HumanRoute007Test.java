package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** DB-free controls for the human route evidence and operator request contract. */
public final class HumanRoute007Test
{
	public static void main(String[] args) throws Exception
	{
		trace();
		protocol();
		if (args.length == 1) { mailbox(Path.of(args[0])); }
		System.out.println("PASS Human Route 007 trace, arrival and mailbox controls");
	}

	private static void trace()
	{
		final var start = new HumanRouteTrace.Point(100, 100, 0, 0);
		final var destination = new HumanRouteTrace.Point(400, 100, 96, 0);
		final var trace = new HumanRouteTrace(start, destination, 32, 24, 0);
		check(!trace.observe(0, 1000, start, 0, false, false), "landing is separate from samples");
		check(trace.observe(250, 1250, start, 0, false, false), "first sample");
		check(!trace.observe(499, 1499, destination, 96, true, true), "sample rate exceeded");
		check(trace.observe(500, 1500, destination, 96, true, true), "actual movement");
		check(!trace.arrived(destination), "a sample after direct relocation cannot prove arrival");
		trace.nativeMove(destination);
		trace.water(1510, 37, true);
		trace.water(1520, 37, false);
		check(!trace.arrived(new HumanRouteTrace.Point(400, 100, 0, 0)), "different floor");
		check(!trace.arrived(new HumanRouteTrace.Point(400, 100, 96, 1)), "different instance");
		check(trace.arrived(destination), "native arrival inside target");
		trace.breakTrace("UNEXPECTED_TELEPORT");
		check(!trace.arrived(destination), "teleport cannot yield arrival");
		final var report = trace.finish(destination, "INTERRUPTED", "UNEXPECTED_TELEPORT");
		check(report.samples() == 2 && report.breaks() == 1 && report.water(), "partial evidence was lost");
		check(report.body().contains("WATER_ENTER") && report.body().contains("WATER_EXIT"), "water callbacks lost");
		final var raced = new HumanRouteTrace(start, destination, 32, 24, 0);
		raced.observe(250, 1250, destination, 96, false, true);
		raced.nativeMove(destination);
		check(raced.arrived(destination), "positive control before interrupt");
		raced.breakTrace("DEATH");
		check(raced.finish(destination, "ARRIVED", "TARGET_REACHED").outcome().equals("INTERRUPTED"), "interrupt lost in seal race");
	}

	private static void protocol()
	{
		final UUID incarnation = UUID.randomUUID();
		final UUID requestId = UUID.randomUUID();
		final String valid = "version=1\nincarnation=" + incarnation + "\nid=" + requestId + "\nsequence=1\ndeadline=10500\naction=status\nscene=-\n";
		check(HumanRouteMailbox.parseRequest(valid, incarnation, 10000).action() == HumanRouteMailbox.Action.STATUS, "valid request rejected");
		reject(() -> HumanRouteMailbox.parseRequest(valid, UUID.randomUUID(), 10000));
		reject(() -> HumanRouteMailbox.parseRequest(valid, incarnation, 10500));
		reject(() -> HumanRouteMailbox.parseRequest(valid.replace("status", "move"), incarnation, 10000));
		reject(() -> HumanRouteMailbox.parseRequest(valid + "sequence=1\n", incarnation, 10000));
		reject(() -> HumanRouteMailbox.parseRequest(valid.replace("status", "stop"), incarnation, 10000));
		check(HumanRouteMailbox.parseRequest(valid.replace("sequence=1", "sequence=0").replace("status", "stop"), incarnation, 10000).action() == HumanRouteMailbox.Action.STOP, "priority stop format");
	}

	private static void mailbox(Path root) throws Exception
	{
		final Path absolute = root.toAbsolutePath().normalize();
		try (var mailbox = new HumanRouteMailbox(absolute))
		{
			mailbox.validate();
			check(mailbox.permit(System.currentTimeMillis()).objectId() == 7007, "isolated Windows permit");
			final long now = System.currentTimeMillis();
			final UUID id = UUID.randomUUID();
			final String envelope = "version=1\nincarnation=" + mailbox.incarnation() + "\nid=" + id + "\nsequence=1\ndeadline=" + (now + 5000) + "\naction=status\nscene=-\n";
			final Path request = absolute.resolve("request.txt");
			mailbox.write(request, envelope);
			mailbox.write(absolute.resolve("stop.txt"), envelope.replace(id.toString(), UUID.randomUUID().toString()).replace("sequence=1", "sequence=0").replace("status", "stop"));
			check(mailbox.receive(now).action() == HumanRouteMailbox.Action.STOP, "stop must preempt a pending command");
			final var accepted = mailbox.receive(now);
			check(accepted != null && accepted.id().equals(id) && accepted.sequence() == 1, "real atomic request exchange");
			mailbox.reply(accepted, "result=PASS\n");
			check(Files.readString(absolute.resolve("receipt.txt")).contains("result=PASS"), "missing atomic receipt");
			mailbox.write(request, envelope.replace("sequence=1", "sequence=2"));
			check(mailbox.receive(now) == null, "request ID is single use across sequences");
			final String fingerprint = "a".repeat(64);
			final String failure = "0\t100\t100\t0\t400\t100\t96\tFAILURE_ONLY\tFAILURE_ONLY\t0\t0\t3\t0\t0\n";
			mailbox.write(absolute.resolve("region-0_0.tsv"), "PHANTOM_ROUTE_LEARNING\t1\t" + fingerprint + "\t" + HumanRouteScenes.sha256(failure) + "\n" + failure);
			mailbox.write(absolute.resolve("scenes.tsv"), "first\t100\t100\t0\t400\t100\t96\t32\t24\tУ старой башни\tИди к каменному мосту\tLEARNED_FAILURE\n");
			final var queue = HumanRouteScenes.read(mailbox, fingerprint);
			check(queue.ready().size() == 1 && queue.ready().getFirst().reason().equals("GEOMETRY_FAILURE"), "verified failure did not enter queue");
			mailbox.write(absolute.resolve("scenes.tsv"), "first\t100\t100\t0\t400\t100\t96\t32\t24\t-\t-\tLEARNED_FAILURE\n");
			check(HumanRouteScenes.read(mailbox, fingerprint).needsLandmark() == 1, "private scene without landmarks entered queue");
		}
		System.out.println("PASS isolated Windows mailbox, priority STOP, ACL, replay and failure selection controls");
	}

	private static void reject(Runnable operation)
	{
		try
		{
			operation.run();
		}
		catch (IllegalArgumentException expected)
		{
			return;
		}
		throw new AssertionError("Invalid request accepted");
	}

	private static void check(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
