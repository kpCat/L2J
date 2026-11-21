/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.ArenaZone;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPvpSkillSafety;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelFixture;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelSlot;

/** Binds one owned N06 arm before allowing one ordinary arena opening cast. */
final class PhantomCombat131Duel implements PhantomMaterializationLifecyclePort, AutoCloseable
{
	private final DuelFixture _fixture;
	private final List<DuelSlot> _pair;
	private final Instant _deadline;
	private final HashMap<Long, Bound> _bound = new HashMap<>();
	private final HashMap<Long, Store> _stored = new HashMap<>();
	private Cut _cut;
	private boolean _openingAttempted;
	private Long _openingNanos;
	private boolean _closed;

	private record Bound(DuelSlot slot, Player player, PlayerNativeWork.Owner owner)
	{
		long profile() { return slot.identity().profile(); }
		boolean current()
		{
			return player.getNativeWorkOwner() == owner && PlayerNativeWork.observationEvidence(player) != null;
		}
	}

	private record Cut(Bound opener, Bound other, Skill skill, int arm) { }
	record Store(long profile, int object, long epoch, int x, int y, int z, long shots, long adena, boolean dead, long corpse) { }

	PhantomCombat131Duel(DuelFixture fixture, int arm, Instant deadline)
	{
		final Instant now = Instant.now();
		require(now.isBefore(deadline) && !deadline.isAfter(now.plusSeconds(900)), "ARM_DEADLINE");
		_fixture = fixture;
		_pair = fixture.arm(arm);
		_deadline = deadline;
	}

	@Override
	public synchronized void beforeMaterialize(long profile, int object)
	{
		slot(profile, object);
		require(!_closed && _cut == null && !_bound.containsKey(profile) && Instant.now().isBefore(_deadline), "MATERIALIZATION_CLOSED");
	}

	@Override public void afterPlayerLoad(long profile, Player player) { beforeMaterialize(profile, player.getObjectId()); }

	@Override
	public synchronized void beforeWorldSpawn(long profile, Player player)
	{
		final var slot = slot(profile, player.getObjectId());
		beforeMaterialize(profile, player.getObjectId());
		final var stored = slot.stored();
		final int nativeZ = GeoEngine.getInstance().getHeight(stored.x(), stored.y(), stored.z());
		require(!player.isSpawned() && player.getAccountName().equals(slot.identity().account()) && player.getClient() == null && !player.isGM()
			&& player.getX() == stored.x() && player.getY() == stored.y() && player.getZ() == nativeZ && player.getInstanceId() == stored.instanceId(),
			"SETUP_NATIVE_ORIGIN");
		PhantomCombat131Fixture.verifyDuelArena(_fixture);
		final var start = slot.start();
		player.setXYZInvisible(start.x(), start.y(), start.z());
	}

	@Override
	public synchronized void materializeSucceeded(long profile, int object)
	{
		beforeMaterialize(profile, object);
		final var slot = slot(profile, object);
		final var player = World.getInstance().getPlayer(object);
		require(player != null, "WORLD_IDENTITY");
		PhantomCombat131Fixture.verifyDuelAtStart(slot, profile, player);
		final var owner = player.getNativeWorkOwner();
		require(owner.epoch() > 0, "OWNER_EPOCH");
		_bound.put(profile, new Bound(slot, player, owner));
	}

	@Override public synchronized void materializeAborted(long profile, int object) { close(); }
	@Override public void beforeStore(long profile, Player player) { }

	@Override
	public synchronized void afterStore(long profile, Player player)
	{
		final var bound = _bound.get(profile);
		if (bound == null || bound.player() != player) { return; }
		final var slot = bound.slot();
		final boolean dead = player.isDead();
		_stored.put(profile, new Store(profile, player.getObjectId(), bound.owner().epoch(), player.getX(), player.getY(), player.getZ(),
			player.getInventory().getInventoryItemCount(slot.shotItem(), -1), player.getAdena(), dead, dead ? player.nativeCorpseGeneration() : 0));
	}

	synchronized Store store(long profile) { return _stored.get(profile); }

	/** The caller supplies the approved, physical episode directory after launch admission. */
	synchronized void sealAtStart(Path receipt, String manifestSha, String boot, String episode) throws IOException
	{
		require(!_closed && _cut == null && _bound.size() == 2 && Instant.now().isBefore(_deadline), "ARM_NOT_BOUND");
		require(manifestSha.matches("[0-9a-f]{64}") && boot.matches("[A-Za-z0-9_-]{1,80}") && episode.matches("[A-Za-z0-9_-]{1,80}"), "CUT_IDENTITY");
		final var opener = _bound.get(_pair.getFirst().identity().profile());
		final var other = _bound.get(_pair.getLast().identity().profile());
		require(_pair.getFirst().identity().side().equals(_pair.getFirst().openingSide()) && _pair.getFirst().openingSide().equals(_pair.getLast().openingSide()),
			"OPENER_SIDE");
		verifyStart(opener, other);
		final var planned = new Cut(opener, other, openingSkill(opener, other), opener.slot().identity().arm());
		final var content = new StringBuilder("schema\tN06_NATIVE_CUT131_V1\n")
			.append("manifestSha\t").append(manifestSha).append("\nfixtureSha\t").append(_fixture.sha256())
			.append("\nboot\t").append(boot).append("\nepisode\t").append(episode).append("\narm\t").append(planned.arm())
			.append("\ncutUtc\t").append(Instant.now()).append("\nopeningSkill\t").append(planned.skill().getId())
			.append(':').append(planned.skill().getLevel()).append('\n');
		for (var bound : List.of(opener, other))
		{
			final var player = bound.player();
			final var start = bound.slot().start();
			content.append("actor\t").append(bound.slot().identity().side()).append('\t').append(bound.profile()).append('\t')
				.append(player.getObjectId()).append('\t').append(bound.owner().epoch()).append('\t')
				.append(start.instanceId()).append('\t').append(start.x()).append('\t').append(start.y()).append('\t').append(start.z()).append('\t')
				.append(player.getCurrentHp()).append('\t').append(player.getCurrentCp()).append('\t').append(player.getCurrentMp()).append('\t')
				.append(bound.slot().shotItem()).append('\t').append(bound.slot().shots()).append('\t').append(bound.slot().adena()).append('\n');
		}
		final var requested = receipt.toAbsolutePath().normalize();
		require("n06-cut.tsv".equals(requested.getFileName().toString()), "CUT_PATH");
		final var directory = PhantomCombat131Fixture.physical(requested.getParent(), false);
		Files.writeString(directory.resolve(requested.getFileName()), content, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
			StandardOpenOption.SYNC);
		_cut = planned;
	}

	/** Reports native request admission; the caller must wait for an exact incoming-damage witness. */
	synchronized boolean submitOpening(PhantomMaterializationService materialization)
	{
		require(!_closed && _cut != null && !_openingAttempted && Instant.now().isBefore(_deadline), "OPENER_NOT_ADMITTED");
		// An issued or refused opening consumes the one attempt for this cut.
		_openingAttempted = true;
		final var cut = _cut;
		final var player = cut.opener().player();
		final var other = cut.other().player();
		try (var lease = materialization.tryAcquireAction(cut.opener().profile()).orElseThrow(() -> failure("OPENER_ACTION_LEASE")))
		{
			require(lease.player() == player && lease.executionValid(), "OPENER_STALE_LEASE");
			verifyStart(cut.opener(), cut.other());
			require(openingSkill(cut.opener(), cut.other()) == cut.skill(), "OPENER_SKILL_CHANGED");
			require(Instant.now().isBefore(_deadline) && lease.executionValid(), "OPENER_DEADLINE_OR_STALE_LEASE");
			final long submitted = System.nanoTime();
			player.setTarget(other);
			if (!player.useMagic(cut.skill(), false, false)) { return false; }
			_openingNanos = submitted;
			return true;
		}
	}

	synchronized boolean openingLandedBefore(long elapsedDeadline)
	{
		if (_closed || _openingNanos == null || !Instant.now().isBefore(_deadline) || !_cut.opener().current() || !_cut.other().current()
			|| World.getInstance().getPlayers().size() != 2) { return false; }
		final long hit = _cut.other().player().recentPlayerAttackNanos(_cut.opener().player().getObjectId());
		return hit != 0 && hit - _openingNanos >= 0 && elapsedDeadline - hit >= 0;
	}

	synchronized boolean currentInArena()
	{
		return !_closed && _cut != null && World.getInstance().getPlayers().size() == 2
			&& currentInArena(_cut.opener()) && currentInArena(_cut.other());
	}

	private static boolean currentInArena(Bound bound)
	{
		final var player = bound.player();
		if (!bound.current() || player.getInstanceId() != bound.slot().start().instanceId() || !player.isInsideZone(ZoneId.PVP)
			|| player.isInsideZone(ZoneId.PEACE) || player.isInsideZone(ZoneId.NO_PVP) || player.isInParty()) { return false; }
		final var zone = ZoneManager.getInstance().getZone(player.getX(), player.getY(), player.getZ(), ArenaZone.class);
		return zone != null && "gludin_pvp".equals(zone.getName());
	}

	private void verifyStart(Bound source, Bound target)
	{
		require(source.current() && target.current() && World.getInstance().getPlayers().size() == 2, "UNBOUND_WORLD_PARTICIPANT");
		for (var bound : List.of(source, target))
		{
			final var player = bound.player();
			PhantomCombat131Fixture.verifyDuelAtStart(bound.slot(), bound.profile(), player);
			require(player.getCurrentHp() == player.getMaxHp() && player.getCurrentCp() == player.getMaxCp()
				&& player.getCurrentMp() == player.getMaxMp(), "ARM_VITALS_NOT_FULL");
			require(player.getTarget() == null && !player.isMoving() && !player.isAttackingNow() && !player.isCastingNow()
				&& !player.isCastingSimultaneouslyNow() && player.getQueuedSkill() == null && !player.hasSummon() && !player.isInCombat()
				&& player.recentPlayerAttackers(16).isEmpty(), "ARM_NOT_FRESH");
		}
		require(target.player().isAutoAttackable(source.player()) && source.player().isAutoAttackable(target.player())
			&& GeoEngine.getInstance().canSeeTarget(source.player(), target.player()), "OPENER_ARENA_LEGALITY");
	}

	private static Skill openingSkill(Bound opener, Bound other)
	{
		final var player = opener.player();
		final var target = other.player();
		final var mode = player.getPlayerClass().isMage() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL;
		for (var rank : opener.slot().identity().skills())
		{
			final var skill = player.getKnownSkill(rank.skillId());
			if (skill != null && skill.getLevel() == rank.skillLevel() && skill.isDamage() && skill.getAffectRange() == 0
				&& skill.getFlyType() == null && !PhantomPvpSkillSafety.requiresRear(skill)
				&& PhantomPvpSkillSafety.supports(skill, mode, player.isTransformed()) && !player.isSkillDisabled(skill)
				&& player.checkDoCastConditions(skill) && skill.checkCondition(player, target, false) && skill.checkCondition(player, target, true)
				&& player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill) > 0)
			{
				return skill;
			}
		}
		throw failure("NO_ORDINARY_OPENING_SKILL");
	}

	private DuelSlot slot(long profile, int object)
	{
		return _pair.stream().filter(slot -> slot.identity().profile() == profile && slot.identity().object() == object)
			.findFirst().orElseThrow(() -> failure("ACTOR_NOT_IN_ARM"));
	}

	@Override public synchronized void close() { _closed = true; }

	private static void require(boolean predicate, String reason)
	{
		if (!predicate) { throw failure(reason); }
	}

	private static AdmissionRejectedException failure(String reason) { return new AdmissionRejectedException("TASK131_N06_" + reason); }
}
