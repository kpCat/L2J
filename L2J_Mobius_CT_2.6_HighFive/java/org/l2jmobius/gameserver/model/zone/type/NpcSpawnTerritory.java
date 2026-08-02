/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.model.zone.type;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.zone.ZoneForm;
import org.l2jmobius.gameserver.model.zone.form.ZoneNPoly;

/**
 * Just dummy zone, needs only for geometry calculations
 * @author GKR, Mobius
 */
public class NpcSpawnTerritory
{
	private final String _name;
	private final ZoneForm _territory;
	private final String _sourcePath;
	private final PolygonGeometry _mainGeometry;
	private List<ZoneForm> _bannedTerritories;
	private List<PolygonGeometry> _bannedGeometry = List.of();
	private boolean _unsupportedBannedGeometry;
	private GeometrySnapshot _geometrySnapshot;

	public NpcSpawnTerritory(String name, ZoneForm territory)
	{
		this(name, territory, null);
	}

	public NpcSpawnTerritory(String name, ZoneForm territory, String sourcePath)
	{
		_name = name;
		_territory = territory;
		_sourcePath = sourcePath == null ? null : canonicalSourcePath(sourcePath);
		_mainGeometry = copyPolygon(territory);
	}

	public synchronized void addBannedTerritory(ZoneForm territory)
	{
		if (_bannedTerritories == null)
		{
			_bannedTerritories = new ArrayList<>(1);
		}

		_bannedTerritories.add(territory);
		final PolygonGeometry geometry = copyPolygon(territory);
		if (geometry == null)
		{
			_unsupportedBannedGeometry = true;
		}
		else
		{
			final ArrayList<PolygonGeometry> copy = new ArrayList<>(_bannedGeometry);
			copy.add(geometry);
			_bannedGeometry = List.copyOf(copy);
		}
		_geometrySnapshot = null;
	}
	
	public String getName()
	{
		return _name;
	}

	/**
	 * Returns a detached immutable geometry only when the loader supplied a
	 * canonical source identity and every involved shape is supported.
	 * @return authoritative loader geometry, or empty for legacy/unsupported forms
	 */
	public synchronized Optional<GeometrySnapshot> geometrySnapshot()
	{
		if ((_sourcePath == null) || (_mainGeometry == null) || _unsupportedBannedGeometry)
		{
			return Optional.empty();
		}
		if (_geometrySnapshot == null)
		{
			final String hash = geometryHash(_name, _sourcePath, _mainGeometry, _bannedGeometry);
			_geometrySnapshot = new GeometrySnapshot(_name, _sourcePath, Shape.POLYGON, _mainGeometry, _bannedGeometry, hash);
		}
		return Optional.of(_geometrySnapshot);
	}
	
	public Location getRandomPoint()
	{
		int count = 0; // Prevent infinite loop.
		Location location;
		
		final Location centerPoint = _territory.getCenterPoint();
		final int centerX = centerPoint.getX();
		final int centerY = centerPoint.getY();
		final int centerZ = centerPoint.getZ();
		int randomX;
		int randomY;
		int randomZ;
		
		if (_bannedTerritories != null)
		{
			SEARCH: while (count++ < 100)
			{
				location = _territory.getRandomPoint();
				randomX = location.getX();
				randomY = location.getY();
				randomZ = location.getZ();
				
				for (ZoneForm territory : _bannedTerritories)
				{
					if (territory.isInsideZone(randomX, randomY, randomZ))
					{
						continue SEARCH;
					}
				}
				
				if (GeoEngine.getInstance().getHeight(randomX, randomY, randomZ) > _territory.getHighZ())
				{
					continue;
				}
				
				if (!GeoEngine.getInstance().canSeeTarget(randomX, randomY, randomZ, centerX, centerY, centerZ, 0))
				{
					continue;
				}
				
				return location;
			}
			
			count = 0;
			SEARCH_NO_GEO: while (count++ < 100)
			{
				location = _territory.getRandomPoint();
				randomX = location.getX();
				randomY = location.getY();
				randomZ = location.getZ();
				
				for (ZoneForm territory : _bannedTerritories)
				{
					if (territory.isInsideZone(randomX, randomY, randomZ))
					{
						continue SEARCH_NO_GEO;
					}
				}
				
				return location;
			}
		}
		
		count = 0;
		while (count++ < 100)
		{
			location = _territory.getRandomPoint();
			randomX = location.getX();
			randomY = location.getY();
			randomZ = location.getZ();
			
			if (GeoEngine.getInstance().getHeight(randomX, randomY, randomZ) > _territory.getHighZ())
			{
				continue;
			}
			
			if (!GeoEngine.getInstance().canSeeTarget(randomX, randomY, randomZ, centerX, centerY, centerZ, 0))
			{
				continue;
			}
			
			return location;
		}
		
		return _territory.getRandomPoint();
	}
	
	public boolean isInsideZone(int x, int y, int z)
	{
		return _territory.isInsideZone(x, y, z);
	}
	
	public int getHighZ()
	{
		return _territory.getHighZ();
	}
	
	public void visualizeZone(int z)
	{
		_territory.visualizeZone(z);
	}

	private static PolygonGeometry copyPolygon(ZoneForm territory)
	{
		if (!(territory instanceof ZoneNPoly polygon))
		{
			return null;
		}
		final int[] x = polygon.getX();
		final int[] y = polygon.getY();
		if (x.length != y.length)
		{
			throw new IllegalArgumentException("Polygon coordinate arrays differ in length.");
		}
		final ArrayList<Vertex> vertices = new ArrayList<>(x.length);
		for (int index = 0; index < x.length; index++)
		{
			vertices.add(new Vertex(x[index], y[index]));
		}
		try
		{
			return new PolygonGeometry(vertices, polygon.getLowZ(), polygon.getHighZ());
		}
		catch (IllegalArgumentException exception)
		{
			return null;
		}
	}

	private static String canonicalSourcePath(String sourcePath)
	{
		if (sourcePath.isBlank() || !sourcePath.equals(sourcePath.trim()) || sourcePath.contains("\\") || sourcePath.startsWith("/") || sourcePath.endsWith("/") || sourcePath.contains("//"))
		{
			throw new IllegalArgumentException("Invalid spawn territory source path.");
		}
		final Path path = Path.of(sourcePath);
		if (path.isAbsolute())
		{
			throw new IllegalArgumentException("Spawn territory source path must be relative.");
		}
		for (Path part : path)
		{
			if (part.toString().equals(".") || part.toString().equals(".."))
			{
				throw new IllegalArgumentException("Spawn territory source path must be normalized.");
			}
		}
		return sourcePath;
	}

	private static String geometryHash(String name, String sourcePath, PolygonGeometry main, List<PolygonGeometry> banned)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, name);
			update(digest, sourcePath);
			update(digest, Shape.POLYGON.name());
			update(digest, main);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(banned.size()).array());
			for (PolygonGeometry polygon : banned)
			{
				update(digest, polygon);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException(exception);
		}
	}

	private static void update(MessageDigest digest, String value)
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}

	private static void update(MessageDigest digest, PolygonGeometry polygon)
	{
		digest.update(ByteBuffer.allocate(Integer.BYTES * 3).putInt(polygon.lowZ()).putInt(polygon.highZ()).putInt(polygon.vertices().size()).array());
		for (Vertex vertex : polygon.vertices())
		{
			digest.update(ByteBuffer.allocate(Integer.BYTES * 2).putInt(vertex.x()).putInt(vertex.y()).array());
		}
	}

	public enum Shape
	{
		POLYGON
	}

	public record Vertex(int x, int y)
	{
	}

	public record PolygonGeometry(List<Vertex> vertices, int lowZ, int highZ)
	{
		public PolygonGeometry
		{
			vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
			if ((vertices.size() < 3) || (vertices.size() > 32) || (new HashSet<>(vertices).size() != vertices.size()) || (lowZ > highZ))
			{
				throw new IllegalArgumentException("Invalid spawn territory polygon geometry.");
			}
		}
	}

	public record GeometrySnapshot(String territoryName, String sourcePath, Shape shape, PolygonGeometry main, List<PolygonGeometry> banned, String hash)
	{
		public GeometrySnapshot
		{
			if ((territoryName == null) || territoryName.isBlank() || (sourcePath == null) || sourcePath.isBlank() || (hash == null) || (hash.length() != 64))
			{
				throw new IllegalArgumentException("Invalid spawn territory geometry snapshot.");
			}
			Objects.requireNonNull(shape, "shape");
			Objects.requireNonNull(main, "main");
			banned = List.copyOf(Objects.requireNonNull(banned, "banned"));
		}
	}
}
