/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.geodata.Cell;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.instance.Door;
import org.l2jmobius.gameserver.model.zone.type.ArenaZone;
import org.l2jmobius.gameserver.model.zone.type.BossZone;
import org.l2jmobius.gameserver.model.zone.type.DamageZone;
import org.l2jmobius.gameserver.model.zone.type.EffectZone;
import org.l2jmobius.gameserver.model.zone.type.JailZone;
import org.l2jmobius.gameserver.model.zone.type.NoLandingZone;
import org.l2jmobius.gameserver.model.zone.type.NoRestartZone;
import org.l2jmobius.gameserver.model.zone.type.OlympiadStadiumZone;
import org.l2jmobius.gameserver.model.zone.type.ResidenceZone;
import org.l2jmobius.gameserver.model.zone.type.ScriptZone;
import org.l2jmobius.gameserver.model.zone.type.SiegableHallZone;
import org.l2jmobius.gameserver.model.zone.type.SiegeZone;
import org.l2jmobius.gameserver.model.zone.type.SwampZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;

/** The scout pulse owns tile leases. The trainer writer checkpoints bounded selection state. */
final class PhantomDedicatedScoutRegions
{
	private static final Logger LOGGER = Logger.getLogger(PhantomDedicatedScoutRegions.class.getName());
	private static final String CHECKPOINT = "dedicated-scout-regions.tsv";
	private static final String HEADER = "phantom-dedicated-scout-regions-v1\t";
	private static final int MAX_CHECKPOINT_BYTES = 64 * 1024;
	private static final int PROBES_PER_PULSE = 8;
	private static final int REJECTS_BEFORE_BACKOFF = 4;
	private static final int MAX_FAILURES = 6;
	private static final long MAX_BACKOFF = TimeUnit.MINUTES.toMillis(10);
	private static final Pattern RELEASE_REASON = Pattern.compile("[A-Za-z0-9_.-]{1,48}");
	private static final int[] SAMPLE_HEIGHTS = {0, -3500, -8000, 2500};
	private static final int[] SAMPLE_OFFSETS = {1032, 360, 1704};
	private static final int SAMPLES_PER_LAYER = 256;
	private static final int SAMPLE_COUNT = SAMPLES_PER_LAYER * SAMPLE_HEIGHTS.length * SAMPLE_OFFSETS.length;

	record Tile(int x, int y)
	{
		int worldX() { return (x - World.TILE_ZERO_COORD_X) * World.TILE_SIZE; }
		int worldY() { return (y - World.TILE_ZERO_COORD_Y) * World.TILE_SIZE; }

		boolean contains(PhantomNavigationPoint point)
		{
			return point != null && point.instanceId() == 0 && point.x() >= worldX() && point.x() < worldX() + World.TILE_SIZE
				&& point.y() >= worldY() && point.y() < worldY() + World.TILE_SIZE;
		}
	}

	record Assignment(long actorId, Tile tile, PhantomNavigationPoint seed)
	{
		int tileX() { return tile.x(); }
		int tileY() { return tile.y(); }
	}

	enum Rejection { NO_LAYER, UNSAFE_GROUND, WATER, ZONES_UNAVAILABLE, RESTRICTED_ZONE, AGGRESSIVE_NPC, OCCUPIED, WORLD_UNAVAILABLE, WORLD_SCAN_LIMIT }

	interface SeedOracle
	{
		long generation();
		boolean loaded(Tile tile);
		PhantomNavigationPoint project(int x, int y, int hintZ);
		Rejection reject(PhantomNavigationPoint point);
	}

	private static final class TileState
	{
		private final Tile tile;
		private int sample;
		private long probedPulse;
		private long assignments;
		private long seedings;
		private long arrivals;
		private int seedRejects;
		private int failures;
		private long retryAfter;
		private String reason = "UNTESTED";
		private Assignment lease;
		private boolean leaseSeeded;
		private boolean leaseArrived;

		TileState(Tile tile) { this.tile = tile; }
	}

	private final SeedOracle _oracle;
	private final long _geoGeneration;
	private final List<TileState> _tiles = new ArrayList<>();
	private final Map<Tile, TileState> _byTile = new HashMap<>();
	private final Map<Long, Assignment> _byActor = new HashMap<>();
	private final EnumMap<Rejection, Long> _rejections = new EnumMap<>(Rejection.class);
	private int _cursor;
	private long _pulse;
	private int _budget;
	private long _revision;
	private long _savedRevision;
	private String _checkpointState = "EMPTY";
	private String _reason = "WAITING_FOR_PULSE";

	static PhantomDedicatedScoutRegions open(PhantomRouteLearning memory)
	{
		return open(memory, new WorldSeedOracle());
	}

	static PhantomDedicatedScoutRegions open(PhantomRouteLearning memory, SeedOracle oracle)
	{
		final var regions = new PhantomDedicatedScoutRegions(oracle);
		try
		{
			final String payload = memory.readChecked(CHECKPOINT, HEADER, MAX_CHECKPOINT_BYTES);
			if (payload != null)
			{
				regions.restore(payload);
				regions._checkpointState = "RESTORED";
			}
		}
		catch (IOException | IllegalArgumentException ex)
		{
			LOGGER.warning("Phantom dedicated scout region checkpoint reset: " + ex.getMessage());
			regions._checkpointState = "RESET";
		}
		return regions;
	}

	private PhantomDedicatedScoutRegions(SeedOracle oracle)
	{
		_oracle = oracle;
		_geoGeneration = oracle.generation();
		for (int x = World.TILE_X_MIN; x <= World.TILE_X_MAX; x++)
		{
			for (int y = World.TILE_Y_MIN; y <= World.TILE_Y_MAX; y++)
			{
				final var tile = new Tile(x, y);
				if (!oracle.loaded(tile)) { continue; }
				final var state = new TileState(tile);
				_tiles.add(state);
				_byTile.put(tile, state);
			}
		}
	}

	synchronized void beginPulse()
	{
		_pulse++;
		_budget = PROBES_PER_PULSE;
	}

	synchronized Assignment claim(long actorId, long nowMillis)
	{
		if (actorId <= 0 || !currentGeneration()) { return null; }
		final Assignment owned = _byActor.get(actorId);
		if (owned != null) { return owned; }
		while (_budget > 0)
		{
			final int index = nextTile(nowMillis);
			if (index < 0)
			{
				_reason = _tiles.isEmpty() ? "NO_LOADED_TILES" : "NO_TILE_AVAILABLE_THIS_PULSE";
				return null;
			}
			final var state = _tiles.get(index);
			_cursor = (index + 1) % _tiles.size();
			state.probedPulse = _pulse;
			final var seed = sample(state);
			state.sample = (state.sample + 1) % SAMPLE_COUNT;
			_budget--;
			_revision++;
			final Rejection rejection = state.tile.contains(seed) ? _oracle.reject(seed) : Rejection.NO_LAYER;
			if (!currentGeneration()) { return null; }
			if (rejection != null)
			{
				_rejections.merge(rejection, 1L, Long::sum);
				state.reason = rejection.name();
				if (++state.seedRejects >= REJECTS_BEFORE_BACKOFF) { defer(state, nowMillis); }
				continue;
			}
			state.lease = new Assignment(actorId, state.tile, seed);
			state.leaseSeeded = false;
			state.leaseArrived = false;
			state.seedRejects = 0;
			state.assignments++;
			state.reason = "LEASED";
			_byActor.put(actorId, state.lease);
			_reason = "ASSIGNED";
			return state.lease;
		}
		_reason = "SEED_PROBE_BUDGET";
		return null;
	}

	private int nextTile(long nowMillis)
	{
		int best = -1;
		for (int offset = 0; offset < _tiles.size(); offset++)
		{
			final int index = (_cursor + offset) % _tiles.size();
			final var candidate = _tiles.get(index);
			if (candidate.lease != null || candidate.probedPulse == _pulse || candidate.retryAfter > nowMillis) { continue; }
			final TileState current = best < 0 ? null : _tiles.get(best);
			if (current == null || candidate.arrivals < current.arrivals || candidate.arrivals == current.arrivals && candidate.assignments < current.assignments)
			{
				best = index;
			}
		}
		return best;
	}

	private PhantomNavigationPoint sample(TileState state)
	{
		// An odd stride permutes every 16 by 16 subgrid once before trying a new height/offset.
		final int cell = (136 + state.sample * 149) % SAMPLES_PER_LAYER;
		final int layer = state.sample / SAMPLES_PER_LAYER;
		final int offset = SAMPLE_OFFSETS[layer / SAMPLE_HEIGHTS.length];
		final int x = state.tile.worldX() + ((cell / 16) << World.SHIFT_BY) + offset;
		final int y = state.tile.worldY() + ((cell % 16) << World.SHIFT_BY) + offset;
		return _oracle.project(x, y, SAMPLE_HEIGHTS[layer % SAMPLE_HEIGHTS.length]);
	}

	private boolean currentGeneration()
	{
		if (_oracle.generation() == _geoGeneration) { return true; }
		_reason = "GEODATA_RELOAD_REQUIRES_RESTART";
		return false;
	}

	private static void defer(TileState state, long nowMillis)
	{
		state.seedRejects = 0;
		state.failures = Math.min(state.failures + 1, MAX_FAILURES);
		state.retryAfter = nowMillis + Math.min(MAX_BACKOFF, TimeUnit.SECONDS.toMillis(30) << (state.failures - 1));
	}

	synchronized boolean validateSeed(Assignment assignment)
	{
		if (!owns(assignment) || !currentGeneration()) { return false; }
		final Rejection rejection = _oracle.reject(assignment.seed());
		if (rejection != null)
		{
			_rejections.merge(rejection, 1L, Long::sum);
			_reason = "SEED_RECHECK_" + rejection.name();
			return false;
		}
		return currentGeneration();
	}

	synchronized boolean tracks(Assignment assignment, PhantomNavigationPoint point)
	{
		return owns(assignment) && assignment.tile().contains(point) && currentGeneration();
	}

	synchronized void seeded(Assignment assignment)
	{
		if (!owns(assignment) || !currentGeneration()) { return; }
		final var state = _byTile.get(assignment.tile());
		if (!state.leaseSeeded)
		{
			state.seedings++;
			state.leaseSeeded = true;
			state.reason = "NATIVE_SEEDED";
			_revision++;
		}
	}

	synchronized void arrived(Assignment assignment)
	{
		if (!owns(assignment) || !currentGeneration()) { return; }
		final var state = _byTile.get(assignment.tile());
		if (state.leaseSeeded && !state.leaseArrived)
		{
			state.arrivals++;
			state.leaseArrived = true;
			state.failures = 0;
			state.retryAfter = 0;
			state.reason = "NATIVE_ARRIVED";
			_revision++;
		}
	}

	synchronized void release(Assignment assignment, long nowMillis, String reason, boolean backoff)
	{
		if (!owns(assignment)) { return; }
		final var state = _byTile.get(assignment.tile());
		_byActor.remove(assignment.actorId());
		state.lease = null;
		state.reason = reason != null && RELEASE_REASON.matcher(reason).matches() ? reason : "RELEASED";
		if (backoff && currentGeneration()) { defer(state, nowMillis); }
		_revision++;
	}

	private boolean owns(Assignment assignment)
	{
		return assignment != null && _byActor.get(assignment.actorId()) == assignment;
	}

	private record TileProgress(int sample, long assignments, long seedings, long arrivals, int seedRejects, int failures, long retryAfter, String reason) { }

	private void restore(String payload) throws IOException
	{
		final var lines = payload.lines().toList();
		if (lines.size() != _tiles.size() + 1) { throw new IOException("Loaded scout tiles changed."); }
		final int cursor = Integer.parseInt(lines.getFirst());
		if (cursor < 0 || cursor >= Math.max(1, _tiles.size())) { throw new IOException("Invalid scout tile cursor."); }
		final var progress = new ArrayList<TileProgress>(_tiles.size());
		final long maximumRetry = System.currentTimeMillis() + MAX_BACKOFF;
		for (int index = 0; index < _tiles.size(); index++)
		{
			final var fields = lines.get(index + 1).split("\t", -1);
			final var tile = _tiles.get(index).tile;
			if (fields.length != 10 || Integer.parseInt(fields[0]) != tile.x() || Integer.parseInt(fields[1]) != tile.y())
			{
				throw new IOException("Invalid or mismatched scout tile checkpoint row.");
			}
			final int sample = Integer.parseInt(fields[2]);
			final long assignments = Long.parseLong(fields[3]);
			final long seedings = Long.parseLong(fields[4]);
			final long arrivals = Long.parseLong(fields[5]);
			final int seedRejects = Integer.parseInt(fields[6]);
			final int failures = Integer.parseInt(fields[7]);
			final long retryAfter = Long.parseLong(fields[8]);
			if (sample < 0 || sample >= SAMPLE_COUNT || arrivals < 0 || arrivals > seedings || seedings > assignments || seedRejects < 0 || seedRejects >= REJECTS_BEFORE_BACKOFF
				|| failures < 0 || failures > MAX_FAILURES || retryAfter < 0 || !RELEASE_REASON.matcher(fields[9]).matches())
			{
				throw new IOException("Invalid scout tile progress.");
			}
			progress.add(new TileProgress(sample, assignments, seedings, arrivals, seedRejects, failures, Math.min(retryAfter, maximumRetry), fields[9]));
		}
		_cursor = cursor;
		for (int index = 0; index < progress.size(); index++)
		{
			final var saved = progress.get(index);
			final var state = _tiles.get(index);
			state.sample = saved.sample();
			state.assignments = saved.assignments();
			state.seedings = saved.seedings();
			state.arrivals = saved.arrivals();
			state.seedRejects = saved.seedRejects();
			state.failures = saved.failures();
			state.retryAfter = saved.retryAfter();
			state.reason = "LEASED".equals(saved.reason()) ? "PREVIOUS_LEASE" : saved.reason();
		}
	}

	void save(PhantomRouteLearning memory) throws IOException
	{
		final long revision;
		final String payload;
		synchronized (this)
		{
			if (_savedRevision == _revision) { return; }
			revision = _revision;
			final var text = new StringBuilder().append(_cursor).append('\n');
			for (var state : _tiles)
			{
				text.append(state.tile.x()).append('\t').append(state.tile.y()).append('\t').append(state.sample).append('\t')
					.append(state.assignments).append('\t').append(state.seedings).append('\t').append(state.arrivals).append('\t').append(state.seedRejects).append('\t')
					.append(state.failures).append('\t').append(state.retryAfter).append('\t').append(state.reason).append('\n');
			}
			payload = text.toString();
		}
		try
		{
			memory.flushPhysicalWitness();
			memory.writeFile(CHECKPOINT, memory.checkedContent(HEADER, payload));
		}
		catch (IOException ex)
		{
			synchronized (this) { _checkpointState = "WRITE_FAILED"; }
			throw ex;
		}
		synchronized (this)
		{
			_savedRevision = revision;
			_checkpointState = "SAVED";
		}
	}

	synchronized String status()
	{
		final long now = System.currentTimeMillis();
		int assigned = 0, seeded = 0, visited = 0, backingOff = 0;
		for (var state : _tiles)
		{
			if (state.assignments > 0) { assigned++; }
			if (state.seedings > 0) { seeded++; }
			if (state.arrivals > 0) { visited++; }
			if (state.retryAfter > now) { backingOff++; }
		}
		return "dedicated_tile_candidates=" + _tiles.size() + "\ndedicated_tile_leases=" + _byActor.size()
			+ "\ndedicated_tiles_assigned=" + assigned + "\ndedicated_tiles_seeded=" + seeded + "\ndedicated_tiles_native_arrived=" + visited
			+ "\ndedicated_tiles_backoff=" + backingOff + "\ndedicated_tile_cursor=" + _cursor
			+ "\ndedicated_tile_checkpoint=" + _checkpointState + "\ndedicated_tile_reason=" + _reason
			+ "\ndedicated_seed_rejections=" + _rejections + "\n";
	}

	private static final class WorldSeedOracle implements SeedOracle
	{
		private static final int MAX_WORLD_OBJECTS = 512;
		private static final int CHECK_STEP = 16;
		private static final int CLEARANCE = 48;
		private static final int[][] CLEARANCE_DIRECTIONS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		private final GeoEngine _geo = GeoEngine.getInstance();

		@Override public long generation() { return _geo.generation(); }
		@Override public boolean loaded(Tile tile) { return _geo.hasGeo(tile.worldX() + World.TILE_SIZE / 2, tile.worldY() + World.TILE_SIZE / 2); }

		@Override
		public PhantomNavigationPoint project(int x, int y, int hintZ)
		{
			return _geo.hasGeo(x, y) ? new PhantomNavigationPoint(x, y, _geo.getHeight(x, y, hintZ), 0) : null;
		}

		@Override
		public Rejection reject(PhantomNavigationPoint seed)
		{
			final int x = seed.x(), y = seed.y(), z = seed.z();
			if (seed.instanceId() != 0 || !layerHasClearance(x, y, z)) { return Rejection.NO_LAYER; }
			if (!_geo.checkNearestNswe(GeoEngine.getGeoX(x), GeoEngine.getGeoY(y), z, Cell.NSWE_ALL)) { return Rejection.UNSAFE_GROUND; }
			final var zones = ZoneManager.getInstance();
			final long zoneGeneration = zones.loadedGeneration();
			if (zoneGeneration < 0) { return Rejection.ZONES_UNAVAILABLE; }
			final Rejection seedZone = zoneRejection(zones, x, y, z);
			if (seedZone != null) { return seedZone; }
			for (int[] direction : CLEARANCE_DIRECTIONS)
			{
				int endX = x, endY = y, endZ = z;
				for (int step = CHECK_STEP; step <= CLEARANCE; step += CHECK_STEP)
				{
					endX = x + direction[0] * step;
					endY = y + direction[1] * step;
					final int height = _geo.getHeight(endX, endY, endZ);
					if (Math.abs(height - endZ) > 32 || !layerHasClearance(endX, endY, height)) { return Rejection.UNSAFE_GROUND; }
					endZ = height;
					final Rejection zone = zoneRejection(zones, endX, endY, endZ);
					if (zone != null) { return zone; }
				}
				// The native bidirectional check covers cliffs, NSWE, closed doors and fences.
				if (!_geo.canMoveToTarget(x, y, z, endX, endY, endZ, 0) || !_geo.canMoveToTarget(endX, endY, endZ, x, y, z, 0))
				{
					return Rejection.UNSAFE_GROUND;
				}
			}
			final Rejection objects = nearbyObjects(seed);
			return zoneGeneration == zones.loadedGeneration() ? objects : Rejection.ZONES_UNAVAILABLE;
		}

		private boolean layerHasClearance(int x, int y, int z)
		{
			final int geoX = GeoEngine.getGeoX(x), geoY = GeoEngine.getGeoY(y);
			if (z <= World.WORLD_Z_MIN + CLEARANCE || z >= World.WORLD_Z_MAX - CLEARANCE || !_geo.hasGeoPos(geoX, geoY) || _geo.getNearestZ(geoX, geoY, z) != z) { return false; }
			final int higher = _geo.getNextHigherZ(geoX, geoY, z + 1);
			return higher == z + 1 || higher - z >= CLEARANCE;
		}

		private static Rejection zoneRejection(ZoneManager zones, int x, int y, int z)
		{
			final var loaded = zones.getLoadedZones(x, y, z);
			if (loaded.isEmpty()) { return Rejection.ZONES_UNAVAILABLE; }
			for (var zone : loaded.get())
			{
				if (zone instanceof WaterZone) { return Rejection.WATER; }
				if (zone instanceof ArenaZone || zone instanceof BossZone || zone instanceof DamageZone || zone instanceof EffectZone
					|| zone instanceof JailZone || zone instanceof NoLandingZone || zone instanceof NoRestartZone || zone instanceof OlympiadStadiumZone
					|| zone instanceof ResidenceZone || zone instanceof ScriptZone || zone instanceof SiegableHallZone || zone instanceof SiegeZone || zone instanceof SwampZone)
				{
					return Rejection.RESTRICTED_ZONE;
				}
			}
			// Seed unknown terrain conservatively. A sea-floor cell can be below the authored WaterZone's lower bound.
			for (var zone : zones.getZones(x, y))
			{
				if (zone instanceof WaterZone water && z <= water.getWaterZ()) { return Rejection.WATER; }
			}
			return null;
		}

		private static Rejection nearbyObjects(PhantomNavigationPoint seed)
		{
			final var region = World.getInstance().getRegion(seed.x(), seed.y(), seed.z());
			if (region == null) { return Rejection.WORLD_UNAVAILABLE; }
			for (var door : region.getDoors())
			{
				if (overlapsDoor(door, seed)) { return Rejection.OCCUPIED; }
			}
			int inspected = 0;
			for (var neighbor : region.getSurroundingRegions())
			{
				for (var object : neighbor.getVisibleObjects())
				{
					if (++inspected > MAX_WORLD_OBJECTS) { return Rejection.WORLD_SCAN_LIMIT; }
					if (!(object instanceof Creature creature) || creature.getInstanceId() != 0 || creature.isAlikeDead()) { continue; }
					final long dz = Math.abs((long) creature.getZ() - seed.z());
					final double collision = CLEARANCE + creature.getTemplate().getCollisionRadius();
					final long dx = (long) creature.getX() - seed.x(), dy = (long) creature.getY() - seed.y();
					final long distanceSquared = dx * dx + dy * dy;
					if (distanceSquared <= collision * collision && dz <= CLEARANCE + creature.getTemplate().getCollisionHeight()) { return Rejection.OCCUPIED; }
					if (creature instanceof Npc npc && npc.isAggressive())
					{
						final long aggro = Math.max(300L, (long) npc.getAggroRange() + 100);
						if (distanceSquared + dz * dz <= aggro * aggro) { return Rejection.AGGRESSIVE_NPC; }
					}
				}
			}
			return null;
		}

		private static boolean overlapsDoor(Door door, PhantomNavigationPoint seed)
		{
			if (door.getInstanceId() != 0 || door.isDead() || !door.checkCollision() || door.getX(0) == 0
				|| seed.z() + CLEARANCE < door.getZMin() || seed.z() > door.getZMax() + CLEARANCE) { return false; }
			int minX = door.getX(0), maxX = minX, minY = door.getY(0), maxY = minY;
			for (int corner = 1; corner < 4; corner++)
			{
				minX = Math.min(minX, door.getX(corner));
				maxX = Math.max(maxX, door.getX(corner));
				minY = Math.min(minY, door.getY(corner));
				maxY = Math.max(maxY, door.getY(corner));
			}
			return seed.x() >= minX - CLEARANCE && seed.x() <= maxX + CLEARANCE && seed.y() >= minY - CLEARANCE && seed.y() <= maxY + CLEARANCE;
		}
	}
}
