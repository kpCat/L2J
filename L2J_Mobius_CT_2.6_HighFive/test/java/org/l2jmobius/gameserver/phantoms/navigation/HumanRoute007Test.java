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
		runtimeFarmRefusal();
		zoneScope();
		check(HumanRouteService.farmProfiles("7,14,27,31").size() == 4, "bounded farm observer profile list rejected");
		reject(() -> HumanRouteService.farmProfiles("7,14,27,31,32"));
		if (args.length == 1) { mailbox(Path.of(args[0])); }
		System.out.println("PASS Human Route 007 trace, arrival, authenticated refusal and zone controls");
	}

	private static void runtimeFarmRefusal()
	{
		final var fingerprint = "a".repeat(64);
		final var incarnation = UUID.randomUUID();
		final var source = new HumanRouteScenes.FarmSource(7, 268484815);
		final var start = new HumanRouteTrace.Point(12207, 15907, -4552, 0);
		final var target = new HumanRouteTrace.Point(15147, 15312, -4552, 0);
		final var scene = new HumanRouteScenes.Scene("dark-elf", start, target, 96, 24, "start", "target", "RUNTIME_FARM_REFUSAL", 1, source);
		final var witness = new HumanRouteFarmRefusals(incarnation);
		final var evidence = new HumanRouteFarmRefusals.Evidence(incarnation, fingerprint, 8, 9, 7, 268484815, 42, 11, start, target, 1000,
			PhantomNavigationResult.LimitKind.SEARCH_LIMIT);
		final var signed = witness.sign(evidence);
		witness.observe(evidence);
		check(witness.find(scene, 1001) != null, "a runtime refusal must be available before its scene is loaded");
		check(!witness.matches(null, scene, fingerprint, 8, 9, 42, 1001), "operator scene without runtime evidence admitted");
		check(witness.matches(signed, scene, fingerprint, 8, 9, 42, 1001), "current exact refusal was not admitted");
		check(!witness.matches(signed, scene, "b".repeat(64), 8, 9, 42, 1001), "mismatched GeoData admitted");
		check(!witness.matches(signed, scene, fingerprint, 8, 9, 43, 1001), "stale actor epoch admitted");
		check(!witness.matches(signed, scene, fingerprint, 8, 9, 42, 1000 + HumanRouteFarmRefusals.MAX_AGE_MILLIS), "expired refusal admitted");
		check(!witness.matches(new HumanRouteFarmRefusals.Signed(evidence, "b".repeat(64)), scene, fingerprint, 8, 9, 42, 1001), "tampered signature admitted");
		check(!new HumanRouteFarmRefusals(UUID.randomUUID()).matches(signed, scene, fingerprint, 8, 9, 42, 1001), "foreign server incarnation admitted");
		final var otherScene = new HumanRouteScenes.Scene("other", start, new HumanRouteTrace.Point(15148, 15312, -4552, 0), 96, 24,
			"start", "target", "RUNTIME_FARM_REFUSAL", 1, source);
		check(!witness.matches(signed, otherScene, fingerprint, 8, 9, 42, 1001), "different endpoint admitted");
	}

	private static void zoneScope()
	{
		check(HumanRouteScenes.worldZone(-1, -1), "stock global zone must apply to world zero");
		check(HumanRouteScenes.worldZone(0, -1), "explicit world zero zone rejected");
		check(!HumanRouteScenes.worldZone(1, -1), "instance zone admitted");
		check(!HumanRouteScenes.worldZone(-1, 2), "instance template zone admitted");
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
		check(HumanRouteMailbox.parseRequest(valid.replace("status", "reload"), incarnation, 10000).action() == HumanRouteMailbox.Action.RELOAD, "reload command rejected");
		check(HumanRouteMailbox.parseRequest(valid.replace("status", "renew"), incarnation, 10000).action() == HumanRouteMailbox.Action.RENEW, "renew command rejected");
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
			final long now = System.currentTimeMillis();
			final UUID id = UUID.randomUUID();
			final long deadline = now + 5000;
			final var arm = new HumanRouteMailbox.Request(id, 1, deadline, HumanRouteMailbox.Action.ARM, "-");
			mailbox.write(absolute.resolve("permit-" + id + ".txt"), "EnableHumanRouteDemonstration=True\nincarnation=" + mailbox.incarnation()
				+ "\nid=" + id + "\nsequence=1\nrequestDeadline=" + deadline + "\naccount=testroute\nobjectId=7007\nexpires=" + (now + 60_000) + "\n");
			check(mailbox.permit(arm, now).objectId() == 7007, "isolated Windows permit");
			try
			{
				mailbox.permit(new HumanRouteMailbox.Request(id, 2, deadline, HumanRouteMailbox.Action.ARM, "-"), now);
				throw new AssertionError("Old consent reused with a new sequence");
			}
			catch (IllegalArgumentException expected) { }
			final String envelope = "version=1\nincarnation=" + mailbox.incarnation() + "\nid=" + id + "\nsequence=1\ndeadline=" + (now + 5000) + "\naction=status\nscene=-\n";
			final Path request = absolute.resolve("request.txt");
			mailbox.write(request, envelope);
			mailbox.write(absolute.resolve("stop.txt"), envelope.replace(id.toString(), UUID.randomUUID().toString()).replace("sequence=1", "sequence=0").replace("status", "stop"));
			check(mailbox.receive(now).action() == HumanRouteMailbox.Action.STOP, "stop must preempt a pending command");
			check(mailbox.receive(now) == null, "stop must invalidate the pending command");
			mailbox.write(request, envelope.replace("sequence=1", "sequence=2"));
			final var accepted = mailbox.receive(now);
			check(accepted != null && accepted.id().equals(id) && accepted.sequence() == 2, "real atomic request exchange");
			mailbox.reply(accepted, "result=PASS\n");
			check(Files.readString(absolute.resolve("receipt.txt")).contains("result=PASS"), "missing atomic receipt");
			mailbox.write(request, envelope.replace("sequence=1", "sequence=3"));
			check(mailbox.receive(now) == null, "request ID is single use across sequences");
			final String fingerprint = "a".repeat(64);
			final String failure = "0\t100\t100\t0\t400\t100\t96\tFAILURE_ONLY\tFAILURE_ONLY\t0\t0\t3\t0\t0\n";
			mailbox.write(absolute.resolve("region-0_0.tsv"), "PHANTOM_ROUTE_LEARNING\t1\t" + fingerprint + "\t" + HumanRouteScenes.sha256(failure) + "\n" + failure);
			mailbox.write(absolute.resolve("scenes.tsv"), "first\t100\t100\t0\t400\t100\t96\t32\t24\tУ старой башни\tИди к каменному мосту\tLEARNED_FAILURE\n");
			final var queue = HumanRouteScenes.read(mailbox, fingerprint);
			check(queue.ready().size() == 1 && queue.ready().getFirst().reason().equals("GEOMETRY_FAILURE"), "verified failure did not enter queue");
			mailbox.write(absolute.resolve("scenes.tsv"), "first\t100\t100\t0\t400\t100\t96\t32\t24\t-\t-\tLEARNED_FAILURE\n");
			check(HumanRouteScenes.read(mailbox, fingerprint).needsLandmark() == 1, "private scene without landmarks entered queue");
			mailbox.write(absolute.resolve("scenes.tsv"), "live\t100\t100\t0\t400\t100\t96\t32\t24\tУ старой башни\tИди к каменному мосту\tRUNTIME_FARM_REFUSAL\t7\t268484815\t" + fingerprint + "\n");
			final var live = HumanRouteScenes.read(mailbox, fingerprint).ready().getFirst();
			check(new HumanRouteScenes.FarmSource(7, 268484815).equals(live.farmSource()), "runtime scene did not keep exact source identity");
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
