/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.groups.PartyMessageType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStore;
import org.l2jmobius.commons.database.DatabaseFactory;

/** One stock GameServer scene across two JVMs on an owned TEST012 datadir. */
public final class PhantomResilientActivity012Native
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_012";
	private static final String JDBC = "jdbc:mysql://127.0.0.1:33349/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
	private static final String ATTACKER = "test012attacker";
	private record Actor(long profile, int object, long epoch, Player player) { }

	public static void main(String[] args) throws Exception
	{
		require(args.length == 2 && (args[0].equals("seed") || args[0].equals("resume")), "MODE");
		final Path root = Path.of(args[1]).toRealPath();
		verifyScope(root);
		boolean booted = false;
		try
		{
			GameServer.main(new String[0]);
			booted = true;
			final var profiles = PhantomProfileRepository.open();
			final Path saved = root.resolve("activity012.saved");
			final boolean seed = args[0].equals("seed");
			final var before = seed ? null : Files.readString(saved).strip().split(" ");
			final var actor = await("ORDINARY_NATIVE_ACTION", 60, () -> activeActor(profiles, before == null ? 0 : Long.parseLong(before[0])));
			System.out.printf("NATIVE012_%s profile=%d object=%d epoch=%d pve=%s%n", args[0], actor.profile(), actor.object(), actor.epoch(), pve(actor.player()));
			if (seed)
			{
				Files.writeString(saved, actor.profile() + " " + actor.object() + " " + actor.epoch());
			}
			else
			{
				require(actor.object() == Integer.parseInt(before[1]) && actor.epoch() != Long.parseLong(before[2]), "COLD_LIFETIME");
				final var solo = new PhantomExecutionHandoffStore(profiles).load(actor.profile()).orElseThrow().state();
				require(solo.phase() == Phase.ACTIVE && solo.owner().namespace().equals("execution.solo"), "INITIAL_SOLO");
				preempt(actor, profiles);
				handoff(actor, profiles, solo.generation());
			}
			require(PhantomSystem.shutdownIfStarted() && !PhantomSystem.hasConfiguredInstance(), "PHANTOM_DRAIN");
			System.out.println("NATIVE012_PASS " + args[0]);
		}
		catch (Throwable failure)
		{
			System.out.println("NATIVE012_FAILED " + failure);
			if (PhantomSystem.hasConfiguredInstance())
			{
				System.out.println("NATIVE012_STATUS " + PhantomSystem.operatorStatus());
				for (var profile : PhantomProfileRepository.open().listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 8))
				{
					final long id = profile.profile().profileId();
					final var life = PhantomSystem.operatorLifeProgress(id);
					System.out.println("NATIVE012_ACTOR " + id + " walk=" + life.get("farm.travel.reason")
						+ " preparation=" + life.get("historical.visiblePreparation") + " decision=" + life.get("decision"));
				}
			}
			throw failure;
		}
		finally
		{
			if (booted) { Shutdown.getInstance().startShutdown(null, 0, false); }
		}
	}

	private static void verifyScope(Path root) throws Exception
	{
		require(Path.of(".").toRealPath().equals(root.resolve("game")) && Files.readString(root.resolve(".codex-owned.json")).matches("(?s).*\"task\"\\s*:\\s*\"012\".*"), "OWNED_CWD");
		final var props = new Properties();
		try (var reader = Files.newBufferedReader(Path.of("config/Database.ini"))) { props.load(reader); }
		require(JDBC.equals(props.getProperty("URL")), "JDBC");
		try (var db = DriverManager.getConnection(JDBC, props.getProperty("Login"), props.getProperty("Password"));
			var statement = db.createStatement(); var rows = statement.executeQuery("SELECT DATABASE(), @@port, @@datadir"))
		{
			require(rows.next() && SCHEMA.equals(rows.getString(1)) && rows.getInt(2) == 33349
				&& Files.isSameFile(root.resolve("sql/data"), Path.of(rows.getString(3))), "SQL_OWNER");
		}
	}

	private static Actor activeActor(PhantomProfileRepository profiles, long required)
	{
		for (var managed : profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 8))
		{
			final long id = managed.profile().profileId();
			if (required != 0 && required != id) { continue; }
			final var entry = PhantomSystem.operatorMaterialization(id).orElse(null);
			if (entry == null || !entry.worldPresent() || !entry.actionAdmissionOpen()) { continue; }
			final Player player = World.getInstance().getPlayer(entry.characterObjectId());
			if (player != null && player.hasHeadlessOutboundSession() && !player.isAlikeDead()
				&& (pve(player) || player.isMoving() && "travel.native_walking".equals(PhantomSystem.operatorLifeProgress(id).get("farm.travel.reason"))))
			{
				return new Actor(id, entry.characterObjectId(), entry.materializedAtNanos(), player);
			}
		}
		return null;
	}

	private static boolean pve(Player player)
	{
		return player.getTarget() instanceof Monster && (player.isAttackingNow() || player.isCastingNow());
	}

	private static void preempt(Actor actor, PhantomProfileRepository profiles) throws Exception
	{
		final Player phantom = actor.player();
		await("ATTACK_FIELD", 25, () -> !phantom.isInsideZone(ZoneId.PEACE) && (pve(phantom) || phantom.isMoving()) && phantom.getParty() == null ? phantom : null);
		try (var db = DatabaseFactory.getConnection(); var statement = db.prepareStatement("INSERT INTO accounts (login,password,accessLevel) VALUES (?,'DISABLED_TEST',0)"))
		{
			statement.setString(1, ATTACKER);
			statement.executeUpdate();
		}
		final int x = phantom.getX() + 35, y = phantom.getY(), z = GeoEngine.getInstance().getHeight(x, y, phantom.getZ());
		final var enemy = Player.create(PlayerTemplateData.getInstance().getTemplate(0), ATTACKER, "Attacker012", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		require(enemy != null && !enemy.isGM(), "ATTACKER_CREATED");
		enemy.getStat().setLevel((byte) phantom.getLevel());
		enemy.setXYZ(x, y, z);
		enemy.setCurrentHp(enemy.getMaxHp());
		enemy.setOnlineStatus(true, false);
		enemy.spawnMe();
		final var store = new PhantomPvpStore(profiles);
		try
		{
			final var oldMove = phantom.captureNativeMoveIdentity();
			require(pve(phantom) || oldMove.getAsBoolean(), "PREEMPTION_SOURCE");
			require(!enemy.isInsideZone(ZoneId.PEACE), "ATTACKER_FIELD");
			final long before = phantom.recentPlayerAttackNanos(enemy.getObjectId());
			enemy.setTarget(phantom);
			enemy.getAI().setIntention(Intention.ATTACK, phantom);
			await("ACTUAL_ATTACK", 8, () -> phantom.recentPlayerAttackNanos(enemy.getObjectId()) > before ? phantom : null);
			System.out.println("NATIVE012_ACTUAL_ATTACK object=" + phantom.getObjectId() + " attacker=" + enemy.getObjectId());
			final Stage response = await("NATIVE_PVP_RESPONSE", 20, () -> store.load(actor.profile()).map(row -> row.encounter())
				.filter(row -> row.source() == Source.ACTUAL_ATTACK && row.counterpart().currentObjectId() == enemy.getObjectId())
				.filter(row -> row.stage() == Stage.ENGAGE && phantom.getTarget() == enemy && (phantom.isAttackingNow() || phantom.isCastingNow())
					|| row.stage() == Stage.RETREAT && phantom.isMoving() && !oldMove.getAsBoolean())
				.map(row -> row.stage()).orElse(null));
			require(!oldMove.getAsBoolean() && !pve(phantom), "ROUTE_OR_PVE_AFTER_RESPONSE");
			System.out.println("NATIVE012_RESPONSE " + response);
		}
		finally
		{
			enemy.abortAttack();
			enemy.getAI().setIntention(Intention.IDLE);
			enemy.setOnlineStatus(false, false);
			enemy.deleteMe();
		}
		final Actor resumed = await("ORDINARY_RESUMED", 35, () -> activeActor(profiles, actor.profile()));
		require(resumed.epoch() == actor.epoch(), "PREEMPTION_RECYCLED_LIFETIME");
		System.out.println("NATIVE012_RESUMED profile=" + actor.profile() + " epoch=" + resumed.epoch());
	}

	private static void handoff(Actor actor, PhantomProfileRepository profiles, long originalGeneration) throws Exception
	{
		final var store = new PhantomExecutionHandoffStore(profiles);
		final var party = await("ACTUAL_PARTY_GRANT", 35, () -> store.load(actor.profile())
			.filter(row -> row.state().phase() == Phase.ACTIVE && row.state().owner().namespace().equals("execution.party") && row.state().generation() > originalGeneration
				&& actor.player().getParty() != null && actor.player().getParty().getMembers().stream().allMatch(Player::hasHeadlessOutboundSession))
			.orElse(null));
		require(actor.player().getParty() != null, "NATIVE_PARTY_GONE");
		actor.player().getParty().removePartyMember(actor.player(), PartyMessageType.LEFT);
		final var solo = await("RETURN_TO_SOLO", 35, () -> store.load(actor.profile())
			.filter(row -> row.state().phase() == Phase.ACTIVE && row.state().owner().namespace().equals("execution.solo")
				&& row.state().generation() > party.state().generation() && actor.player().getParty() == null).orElse(null));
		System.out.println("NATIVE012_HANDOFF party=" + party.state().generation() + " solo=" + solo.state().generation());
	}

	private static <T> T await(String reason, int seconds, Supplier<T> observation) throws InterruptedException
	{
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		do
		{
			final T observed = observation.get();
			if (observed != null) { return observed; }
			Thread.sleep(125);
		}
		while (System.nanoTime() < until);
		throw new AssertionError("NATIVE012_" + reason);
	}

	private static void require(boolean valid, String reason)
	{
		if (!valid) { throw new AssertionError("NATIVE012_" + reason); }
	}
}
