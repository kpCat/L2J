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
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.model.World;

/**
 * Immutable bounded geometry used only for topology membership.
 */
public final class PhantomTopologyArea
{
	public enum Form
	{
		POINT_RADIUS,
		CUBOID,
		POLYGON
	}

	public record Vertex(int x, int y)
	{
	}

	private final Form _form;
	private final int _instanceId;
	private final int _minX;
	private final int _maxX;
	private final int _minY;
	private final int _maxY;
	private final int _minZ;
	private final int _maxZ;
	private final PhantomTopologyPoint _center;
	private final int _radius;
	private final List<Vertex> _vertices;
	private final double _measure;

	private PhantomTopologyArea(Form form, int instanceId, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, PhantomTopologyPoint center, int radius, List<Vertex> vertices, double measure)
	{
		_form = Objects.requireNonNull(form, "form");
		_instanceId = instanceId;
		_minX = minX;
		_maxX = maxX;
		_minY = minY;
		_maxY = maxY;
		_minZ = minZ;
		_maxZ = maxZ;
		_center = center;
		_radius = radius;
		_vertices = List.copyOf(vertices);
		_measure = measure;
	}

	public static PhantomTopologyArea pointRadius(PhantomTopologyPoint center, int radius)
	{
		Objects.requireNonNull(center, "center");
		if ((radius < 0) || (radius > 100_000))
		{
			throw new IllegalArgumentException("Topology point radius must be between 0 and 100000.");
		}
		if ((((long) center.x() - radius) < World.WORLD_X_MIN) || (((long) center.x() + radius) > World.WORLD_X_MAX) || (((long) center.y() - radius) < World.WORLD_Y_MIN) || (((long) center.y() + radius) > World.WORLD_Y_MAX) || (((long) center.z() - radius) < World.WORLD_Z_MIN) || (((long) center.z() + radius) > World.WORLD_Z_MAX))
		{
			throw new IllegalArgumentException("Topology point-radius area exceeds world bounds.");
		}
		return new PhantomTopologyArea(Form.POINT_RADIUS, center.instanceId(), center.x() - radius, center.x() + radius, center.y() - radius, center.y() + radius, center.z() - radius, center.z() + radius, center, radius, List.of(), Math.PI * radius * radius);
	}

	public static PhantomTopologyArea cuboid(int instanceId, int minX, int maxX, int minY, int maxY, int minZ, int maxZ)
	{
		validateBounds(instanceId, minX, maxX, minY, maxY, minZ, maxZ);
		new PhantomTopologyPoint(minX, minY, minZ, instanceId);
		new PhantomTopologyPoint(maxX, maxY, maxZ, instanceId);
		return new PhantomTopologyArea(Form.CUBOID, instanceId, minX, maxX, minY, maxY, minZ, maxZ, null, 0, List.of(), Math.max(1L, ((long) maxX - minX) * ((long) maxY - minY)));
	}

	public static PhantomTopologyArea polygon(int instanceId, int minZ, int maxZ, List<Vertex> vertices)
	{
		Objects.requireNonNull(vertices, "vertices");
		if ((vertices.size() < 3) || (vertices.size() > 32))
		{
			throw new IllegalArgumentException("Topology polygon must have between 3 and 32 vertices.");
		}
		final ArrayList<Vertex> copy = new ArrayList<>(vertices);
		if (copy.stream().distinct().count() != copy.size())
		{
			throw new IllegalArgumentException("Topology polygon contains duplicate vertices.");
		}
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		long twiceArea = 0;
		for (int index = 0; index < copy.size(); index++)
		{
			final Vertex current = copy.get(index);
			final Vertex next = copy.get((index + 1) % copy.size());
			minX = Math.min(minX, current.x());
			maxX = Math.max(maxX, current.x());
			minY = Math.min(minY, current.y());
			maxY = Math.max(maxY, current.y());
			twiceArea += ((long) current.x() * next.y()) - ((long) next.x() * current.y());
		}
		validateBounds(instanceId, minX, maxX, minY, maxY, minZ, maxZ);
		new PhantomTopologyPoint(minX, minY, minZ, instanceId);
		new PhantomTopologyPoint(maxX, maxY, maxZ, instanceId);
		if (twiceArea == 0)
		{
			throw new IllegalArgumentException("Topology polygon has zero area.");
		}
		for (int first = 0; first < copy.size(); first++)
		{
			final int firstNext = (first + 1) % copy.size();
			for (int second = first + 1; second < copy.size(); second++)
			{
				final int secondNext = (second + 1) % copy.size();
				if ((first == second) || (firstNext == second) || (secondNext == first))
				{
					continue;
				}
				if (segmentsIntersect(copy.get(first), copy.get(firstNext), copy.get(second), copy.get(secondNext)))
				{
					throw new IllegalArgumentException("Topology polygon is self-intersecting.");
				}
			}
		}
		return new PhantomTopologyArea(Form.POLYGON, instanceId, minX, maxX, minY, maxY, minZ, maxZ, null, 0, copy, Math.abs(twiceArea) / 2.0);
	}

	private static void validateBounds(int instanceId, int minX, int maxX, int minY, int maxY, int minZ, int maxZ)
	{
		if ((instanceId < 0) || (minX > maxX) || (minY > maxY) || (minZ > maxZ))
		{
			throw new IllegalArgumentException("Invalid topology area bounds.");
		}
	}

	private static boolean segmentsIntersect(Vertex a, Vertex b, Vertex c, Vertex d)
	{
		final long abC = cross(a, b, c);
		final long abD = cross(a, b, d);
		final long cdA = cross(c, d, a);
		final long cdB = cross(c, d, b);
		return (((abC > 0) && (abD < 0)) || ((abC < 0) && (abD > 0))) && (((cdA > 0) && (cdB < 0)) || ((cdA < 0) && (cdB > 0)));
	}

	private static long cross(Vertex a, Vertex b, Vertex c)
	{
		return (((long) b.x() - a.x()) * ((long) c.y() - a.y())) - (((long) b.y() - a.y()) * ((long) c.x() - a.x()));
	}

	public boolean contains(PhantomTopologyPoint point)
	{
		if ((point == null) || (point.instanceId() != _instanceId) || (point.x() < _minX) || (point.x() > _maxX) || (point.y() < _minY) || (point.y() > _maxY) || (point.z() < _minZ) || (point.z() > _maxZ))
		{
			return false;
		}
		if (_form == Form.POINT_RADIUS)
		{
			return _center.distance3D(point) <= _radius;
		}
		if (_form == Form.CUBOID)
		{
			return true;
		}
		boolean inside = false;
		for (int current = 0, previous = _vertices.size() - 1; current < _vertices.size(); previous = current++)
		{
			final Vertex a = _vertices.get(current);
			final Vertex b = _vertices.get(previous);
			if ((((a.y() > point.y()) != (b.y() > point.y()))) && (point.x() < (((long) (b.x() - a.x()) * (point.y() - a.y())) / (double) (b.y() - a.y())) + a.x()))
			{
				inside = !inside;
			}
		}
		return inside || onBoundary(point.x(), point.y());
	}

	private boolean onBoundary(int x, int y)
	{
		final Vertex point = new Vertex(x, y);
		for (int index = 0; index < _vertices.size(); index++)
		{
			final Vertex first = _vertices.get(index);
			final Vertex second = _vertices.get((index + 1) % _vertices.size());
			if ((cross(first, second, point) == 0) && (x >= Math.min(first.x(), second.x())) && (x <= Math.max(first.x(), second.x())) && (y >= Math.min(first.y(), second.y())) && (y <= Math.max(first.y(), second.y())))
			{
				return true;
			}
		}
		return false;
	}

	public PhantomTopologyPoint representativePoint()
	{
		if (_form == Form.POINT_RADIUS)
		{
			return _center;
		}
		if (_form == Form.CUBOID)
		{
			return new PhantomTopologyPoint(_minX + ((_maxX - _minX) / 2), _minY + ((_maxY - _minY) / 2), _minZ + ((_maxZ - _minZ) / 2), _instanceId);
		}
		long x = 0;
		long y = 0;
		for (Vertex vertex : _vertices)
		{
			x += vertex.x();
			y += vertex.y();
		}
		final PhantomTopologyPoint centroid = new PhantomTopologyPoint((int) (x / _vertices.size()), (int) (y / _vertices.size()), _minZ + ((_maxZ - _minZ) / 2), _instanceId);
		if (contains(centroid))
		{
			return centroid;
		}
		final Vertex first = _vertices.getFirst();
		return new PhantomTopologyPoint(first.x(), first.y(), _minZ + ((_maxZ - _minZ) / 2), _instanceId);
	}

	public Form form()
	{
		return _form;
	}

	public int instanceId()
	{
		return _instanceId;
	}

	public int minX()
	{
		return _minX;
	}

	public int maxX()
	{
		return _maxX;
	}

	public int minY()
	{
		return _minY;
	}

	public int maxY()
	{
		return _maxY;
	}

	public int minZ()
	{
		return _minZ;
	}

	public int maxZ()
	{
		return _maxZ;
	}

	public PhantomTopologyPoint center()
	{
		return _center;
	}

	public int radius()
	{
		return _radius;
	}

	public List<Vertex> vertices()
	{
		return _vertices;
	}

	public double measure()
	{
		return _measure;
	}
}
