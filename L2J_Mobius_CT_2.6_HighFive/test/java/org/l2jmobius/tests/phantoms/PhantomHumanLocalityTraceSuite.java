/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/** DB-free production locality hooks; run in a private working directory. */
public final class PhantomHumanLocalityTraceSuite implements PhantomTestSuite
{
	private final Path _config = Path.of(PhantomPlayersConfig.PHANTOM_PLAYERS_CONFIG_FILE);
	private String _canonical;

	public static void main(String[] args)
	{
		final var context = new PhantomTestContext(17001701, Path.of(args[0]), Path.of(args[1]));
		System.exit(PhantomTestLauncher.runSuite("human-locality-trace", new PhantomHumanLocalityTraceSuite(), context));
	}

	@Override
	public String id() { return "phantom-human-locality-trace"; }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertFalse(Files.exists(_config), "Private test config must be absent.");
		Files.createDirectories(_config.getParent());
		_canonical = Files.readString(context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini"))
			.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Files.deleteIfExists(_config);
		PhantomPlayersConfig.load();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("offline-probes-do-not-consume-slots", _ -> run(0, true));
		registry.add("first-three-online-local-candidates-watched", _ -> run(3, true));
		registry.add("eight-slot-bound-and-diagnostics-off-semantics", _ ->
		{
			final Outcome disabled = run(9, false);
			final Outcome enabled = run(9, true);
			PhantomAssertions.assertEquals(disabled, enabled, "Diagnostics changed calls, membership, physical demand or signals.");
		});
	}

	private Outcome run(int onlineCount, boolean diagnostics) throws Exception
	{
		Files.writeString(_config, _canonical.replace("EnablePhantomDiagnostics = False", "EnablePhantomDiagnostics = " + diagnostics));
		PhantomPlayersConfig.load();
		PhantomAssertions.assertTrue(PhantomPlayersConfig.isEnabled(), "Fixture configuration must enable Phantom system.");
		PhantomAssertions.assertEquals(diagnostics, PhantomPlayersConfig.settings().diagnosticsEnabled(), "Fixture diagnostic flag.");
		final var recorder = PhantomRuntimeFlightRecorder.getInstance();
		final String consent = "locality-" + onlineCount + "-" + diagnostics;
		PhantomAssertions.assertEquals(diagnostics, recorder.begin(consent), "Recording admission.");
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var port = new RecordingPort();
		final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, port);
		PhantomAssertions.assertTrue(topology.start(), "Topology fixture start.");
		try
		{
			for (long id = 1; id <= 12 + onlineCount; id++)
			{
				PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, topology.registerProfile(id), "Profile registration.");
				PhantomAssertions.assertEquals(UpdateResult.UPDATED, topology.updateProfile(id, PhantomTopologyCoreSuite.LEFT_POINT, 1), "Committed local position.");
			}
			final LongPredicate online = id -> id > 12;
			final List<Long> baselineCalls = new ArrayList<>();
			final var baseline = topology.nativeProfilesAt(PhantomTopologyCoreSuite.LEFT_POINT, 1024, id -> { baselineCalls.add(id); return online.test(id); });
			final List<Long> calls = new ArrayList<>();
			final List<Long> physical = new ArrayList<>();
			final var locality = new PhantomHumanLocalityControl(topology, port, () -> List.of(PhantomTopologyCoreSuite.LEFT_POINT), () -> 1000,
				id -> { calls.add(id); return online.test(id); }, Map::of);
			locality.installPhysicalDemand(physical::add);
			locality.onPulse();
			PhantomAssertions.assertEquals(baselineCalls, calls.subList(0, baselineCalls.size()), "Topology eligibility invocation sequence changed.");
			final List<Long> expected = baseline.candidates().stream().map(profile -> profile.profileId()).sorted().toList();
			PhantomAssertions.assertEquals(expected, new TreeSet<>(locality.physicalSnapshot().positionRevisions().keySet()).stream().toList(), "Candidate membership changed.");
			PhantomAssertions.assertEquals(expected, physical, "Physical demand membership changed.");
			PhantomAssertions.assertEquals(expected, port._signals.stream().map(Submitted::profileId).toList(), "Signal membership changed.");
			PhantomAssertions.assertEquals(onlineCount, locality.localCount(), "Accepted local count.");
			for (long id = 1; id <= 12 + onlineCount; id++) { PhantomAssertions.assertEquals(online.test(id), locality.isLocal(id), "Online/local membership."); }
			final var snapshot = recorder.end(consent);
			final List<Long> firstOnline = baselineCalls.stream().filter(online::test).limit(8).toList();
			PhantomAssertions.assertEquals(diagnostics ? firstOnline : List.of(), snapshot.watched(), "OFFLINE probes consumed ONLINE watch slots.");
			PhantomAssertions.assertTrue(snapshot.events().stream().noneMatch(event -> event.profileId() > 0 && event.profileId() <= 12), "OFFLINE probe retained.");
			if (diagnostics)
			{
				for (long id : firstOnline)
				{
					PhantomAssertions.assertTrue(snapshot.events().stream().anyMatch(event -> event.profileId() == id && event.event().equals("LOCAL_CANDIDATE") && event.stateA().equals("ONLINE")), "Watched ONLINE predicate event missing.");
					PhantomAssertions.assertTrue(snapshot.events().stream().anyMatch(event -> event.profileId() == id && event.event().equals("LOCAL_CANDIDATE") && event.stateA().equals("LOCAL") && event.reason().equals("physical.demand")), "Watched accepted physical demand missing.");
					PhantomAssertions.assertTrue(snapshot.events().stream().anyMatch(event -> event.profileId() == id && event.event().equals("LOCAL_SIGNAL_RESULT") && event.reason().equals("human.local")), "Watched human.local signal result missing.");
				}
			}
			else { PhantomAssertions.assertEquals(0, snapshot.events().size(), "Diagnostics OFF retained events."); }
			return new Outcome(List.copyOf(calls), expected, List.copyOf(physical), List.copyOf(port._signals), locality.deliverySnapshot(), locality.physicalSnapshot().overflow());
		}
		finally
		{
			recorder.stop(consent);
			topology.beginStop();
			PhantomAssertions.assertTrue(topology.finishStop(), "Topology fixture stop.");
		}
	}

	private record Submitted(long profileId, PhantomRelevanceSignal signal) { }
	private record Outcome(List<Long> calls, List<Long> candidates, List<Long> physical, List<Submitted> signals, Map<Long, PhantomRelevanceSignalPort.SignalDelivery> delivery, boolean overflow) { }
	private static final class RecordingPort implements PhantomRelevanceSignalPort
	{
		private final List<Submitted> _signals = new ArrayList<>();
		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal) { _signals.add(new Submitted(profileId, signal)); return SignalDelivery.ACCEPTED; }
		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence) { throw new AssertionError("Unexpected signal withdrawal."); }
	}
}
