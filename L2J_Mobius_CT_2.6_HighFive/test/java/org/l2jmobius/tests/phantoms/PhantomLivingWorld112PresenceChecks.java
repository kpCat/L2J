/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.population.*;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** Source calendar census and production demand gates; delegate counts are not World evidence. */
public final class PhantomLivingWorld112PresenceChecks
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK112_" + reason); } }
	private static void census(Path module, Path output, Instant now) throws Exception
	{
		final var config = new Properties();
		try (var reader = Files.newBufferedReader(module.resolve("dist/game/config/Custom/PhantomPlayers.ini"))) { config.load(reader); }
		final var zone = ZoneId.of(config.getProperty("PhantomPopulationTimeZone").trim());
		final var preset = PhantomPopulationEcologyState.Preset.valueOf(config.getProperty("PhantomEcologyPreset").trim());
		final int age = Integer.parseInt(config.getProperty("PhantomEcologyWorldAgeDays").trim());
		final var data = module.resolve("dist/game/data/phantoms");
		final var population = PhantomPopulationCatalog.load(data.resolve("population/high-five-population-v3.xml"), zone);
		final var ecology = PhantomPopulationEcologyCatalog.load(data.resolve("population/high-five-ecology-v1.xml"), population, PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml")));
		final var rows = new ArrayList<String>(); rows.add("profile\tschedule\tphase\tcalendar\tnextBoundary\tpredicate");
		int active = 0; int online = 0; int selected = 0;
		for (int id = 1; id <= 5000; id++)
		{
			final var assignment = ecology.assign(preset, 1, id, PhantomPopulationManager.DETERMINISTIC_SEED, now.getEpochSecond() / 60, age, 0, null);
			final var shell = PhantomPopulationStore.scheduleForShell(population, id, PhantomPopulationManager.DETERMINISTIC_SEED, assignment.scheduleTemplate());
			final var evaluation = population.evaluate(shell.template().id(), now, zone, shell.phaseMinutes());
			if (evaluation.state() == PhantomActivityState.ACTIVE) { active++; }
			if (evaluation.state() != PhantomActivityState.SLEEPING) { online++; }
			rows.add(id + "\t" + shell.template().id() + "\t" + shell.phaseMinutes() + "\t" + evaluation.state() + "\t" + evaluation.nextBoundary() + "\t" + (evaluation.state() == PhantomActivityState.ACTIVE ? "calendar.active_only_ecology_runtime_unknown" : "schedule_not_active"));
			if (List.of(12, 64, 128, 256, 5000).contains(id))
			{
				System.out.println("CENSUS utc=" + now + " zone=" + zone + " cohort=" + id + " calendarActive=" + active + " calendarOnline=" + online + " ecologyPermitted=UNKNOWN admitted=UNKNOWN World=NOT_RUN");
				if (selected == 0 && active > 0 && id <= 256) { selected = id; }
			}
		}
		Files.createDirectories(output);
		Files.write(output.resolve("calendar.tsv"), rows);
		Files.writeString(output.resolve("census.properties"), "utc=" + now + "\ncohort=" + selected + "\ncalendarActive5000=" + active + "\nsourceScheduleUnchanged=true\n");
		check(active > 0, "NO_NATURAL_ACTIVE_COHORT");
	}
	private static void matrix()
	{
		final var now = new AtomicLong(1000);
		final var humans = new AtomicReference<List<PhantomTopologyPoint>>(List.of());
		final var calls = new AtomicInteger();
		final var signals = new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var backend = new PhantomTopologyCoreSuite.TestBackend();
		final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signals);
		check(topology.start(), "TOPOLOGY_START");
		try
		{
			topology.registerProfile(1); topology.updateProfile(1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			final var presence = new PhantomPresenceRegistry(1);
			final var admitted = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var locality = new PhantomHumanLocalityControl(topology, signals, humans::get, now::get, presence::isOnline, Map::of);
			final var delegate = new PhantomActivityMaterializationPort()
			{
				@Override public TransitionOutcome materialize(long id) { calls.incrementAndGet(); return TransitionOutcome.success(); }
				@Override public TransitionOutcome dematerialize(long id) { return TransitionOutcome.success(); }
				@Override public TransitionOutcome retryCleanup(long id) { return TransitionOutcome.deferred(); }
				@Override public boolean isMaterialized(long id) { return false; }
				@Override public boolean hasLifecycleOwnership(long id) { return false; }
			};
			final var bridge = new PhantomReconcileFirstActivityPort(delegate);
			bridge.installPopulationReadiness(presence, locality, null, _ -> false, _ -> admitted.get());
			for (var calendar : List.of(PhantomActivityState.SLEEPING, PhantomActivityState.ACTIVE))
			{
				for (boolean human : List.of(false, true))
				{
					presence.schedule(1, calendar); humans.set(human ? List.of(PhantomTopologyCoreSuite.LEFT_POINT) : List.of()); now.addAndGet(1000); locality.onPulse();
					final int before = calls.get(); final var result = bridge.materialize(1);
					final boolean eligible = calendar == PhantomActivityState.ACTIVE && human;
					check((calls.get() == before + 1) == eligible, "CALENDAR_DEMAND_MATRIX");
					System.out.println("MATRIX calendar=" + calendar + " human=" + human + " outcome=" + result.outcome() + " reason=" + result.reason() + " delegateAttempt=" + (calls.get() - before) + " World=NOT_INITIALIZED");
				}
			}
			admitted.set(false); final int before = calls.get();
			check(bridge.materialize(1).outcome() == PhantomActivityMaterializationPort.Outcome.DEFERRED && calls.get() == before, "CAPACITY_ADMISSION_BYPASSED");
			admitted.set(true); now.addAndGet(10001);
			check(bridge.materialize(1).outcome() == PhantomActivityMaterializationPort.Outcome.DEFERRED && calls.get() == before, "EXPIRED_LOCALITY_REUSED");
			locality.onPulse(); humans.set(List.of());
			check(bridge.materialize(1).outcome() == PhantomActivityMaterializationPort.Outcome.DEFERRED && calls.get() == before, "DISAPPEARED_HUMAN_REUSED");
		}
		finally { topology.beginStop(); check(topology.finishStop(), "TOPOLOGY_STOP"); }
		check(!DatabaseFactory.isInitialized(), "DB_INITIALIZED");
		System.out.println("PASS demand/capacity/TTL/leave; real population/ecology owners separately covered by affected readiness102/ecology suites");
	}
	public static void main(String[] args) throws Exception
	{
		census(Path.of(args[0]), Path.of(args[1]), args.length > 2 ? Instant.parse(args[2]) : Instant.now());
		matrix();
	}
}
