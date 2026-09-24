package org.l2jmobius.gameserver.geoengine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Conservative, read-only collision proof for instance-zero static XML geometry. */
public final class StaticXmlCollisionOracle implements GeoEngine.MovementCollisionOracle
{
	private record Door(int id, int[] x, int[] y, int minZ, int maxZ)
	{
	}

	private record Fence(String name, int minX, int minY, int maxX, int maxY, int z)
	{
	}

	public record SegmentCollision(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, List<Integer> doorIds, List<String> fenceNames)
	{
	}

	private final List<Door> _doors;
	private final List<Fence> _fences;
	private final Set<Integer> _doorIntersections = new TreeSet<>();
	private final Set<String> _fenceIntersections = new TreeSet<>();
	private final List<SegmentCollision> _segmentRecords = new ArrayList<>();

	private StaticXmlCollisionOracle(List<Door> doors, List<Fence> fences)
	{
		_doors = List.copyOf(doors);
		_fences = List.copyOf(fences);
	}

	public static StaticXmlCollisionOracle load(Path doorsXml, Path fencesXml)
	{
		try
		{
			final List<Door> doors = new ArrayList<>();
			for (Element element : children(parse(doorsXml), "door"))
			{
				final String collision = element.getAttribute("check_collision");
				if (!collision.isEmpty() && !"true".equalsIgnoreCase(collision) && !"false".equalsIgnoreCase(collision))
				{
					throw new IllegalArgumentException("Invalid door collision flag.");
				}
				if ("false".equalsIgnoreCase(collision))
				{
					continue;
				}
				final int[] x = new int[4];
				final int[] y = new int[4];
				for (int i = 0; i < 4; i++)
				{
					final String[] point = required(element, "node" + (i + 1)).split(",", -1);
					if (point.length != 2)
					{
						throw new IllegalArgumentException("Invalid door node.");
					}
					x[i] = Integer.parseInt(point[0]);
					y[i] = Integer.parseInt(point[1]);
				}
				final int minZ = Integer.parseInt(required(element, "nodeZ"));
				final int height = Integer.parseInt(required(element, "height"));
				if (height <= 0)
				{
					throw new IllegalArgumentException("Invalid door height.");
				}
				doors.add(new Door(Integer.parseInt(required(element, "id")), x, y, minZ, Math.addExact(minZ, height)));
			}
			final List<Fence> fences = new ArrayList<>();
			for (Element element : children(parse(fencesXml), "fence"))
			{
				required(element, "state");
				final int x = Integer.parseInt(required(element, "x"));
				final int y = Integer.parseInt(required(element, "y"));
				final int width = Integer.parseInt(required(element, "width"));
				final int length = Integer.parseInt(required(element, "length"));
				if ((width <= 0) || (length <= 0) || (Integer.parseInt(required(element, "height")) <= 0))
				{
					throw new IllegalArgumentException("Invalid fence size.");
				}
				fences.add(new Fence(required(element, "name"), x - (width / 2), y - (length / 2), x + (width / 2), y + (length / 2), Integer.parseInt(required(element, "z"))));
			}
			return new StaticXmlCollisionOracle(doors, fences);
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Invalid static collision XML.", exception);
		}
	}

	private static Element parse(Path file) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		final Element root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
		if (!"list".equals(root.getTagName()))
		{
			throw new IllegalArgumentException("Expected collision XML list.");
		}
		return root;
	}

	private static List<Element> children(Element root, String name)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node instanceof Element element)
			{
				if (!name.equals(element.getTagName()))
				{
					throw new IllegalArgumentException("Unexpected collision XML element.");
				}
				result.add(element);
			}
		}
		return result;
	}

	private static String required(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (value.isEmpty())
		{
			throw new IllegalArgumentException("Missing collision geometry: " + name);
		}
		return value;
	}

	@Override
	public boolean doorBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
	{
		if (instanceId != 0)
		{
			throw new IllegalArgumentException("Static collision proof requires instance zero.");
		}
		boolean blocked = false;
		for (Door door : _doors)
		{
			if (matchesDoor(door, fromX, fromY, fromZ, toX, toY, toZ))
			{
				_doorIntersections.add(door.id());
				blocked = true;
			}
		}
		return blocked;
	}

	@Override
	public boolean fenceBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
	{
		if (instanceId != 0)
		{
			throw new IllegalArgumentException("Static collision proof requires instance zero.");
		}
		boolean blocked = false;
		for (Fence fence : _fences)
		{
			if (matchesFence(fence, fromX, fromY, fromZ, toX, toY, toZ))
			{
				_fenceIntersections.add(fence.name());
				blocked = true;
			}
		}
		return blocked;
	}

	private static boolean matchesDoor(Door door, int fromX, int fromY, int fromZ, int toX, int toY, int toZ)
	{
		return (Math.min(fromZ, toZ) <= door.maxZ()) && (Math.max(fromZ, toZ) >= door.minZ()) && intersectsPolygon(fromX, fromY, toX, toY, door.x(), door.y());
	}

	private static boolean matchesFence(Fence fence, int fromX, int fromY, int fromZ, int toX, int toY, int toZ)
	{
		return (Math.min(fromZ, toZ) <= (fence.z() + 100)) && (Math.max(fromZ, toZ) >= (fence.z() - 100)) && intersectsPolygon(fromX, fromY, toX, toY, new int[]
		{
			fence.minX(), fence.maxX(), fence.maxX(), fence.minX()
		}, new int[]
		{
			fence.minY(), fence.minY(), fence.maxY(), fence.maxY()
		});
	}

	private static boolean intersectsPolygon(int ax, int ay, int bx, int by, int[] x, int[] y)
	{
		if (inside(ax, ay, x, y) || inside(bx, by, x, y))
		{
			return true;
		}
		for (int i = 0; i < x.length; i++)
		{
			final int next = (i + 1) % x.length;
			if (intersects(ax, ay, bx, by, x[i], y[i], x[next], y[next]))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean inside(int px, int py, int[] x, int[] y)
	{
		boolean inside = false;
		for (int i = 0, j = x.length - 1; i < x.length; j = i++)
		{
			if (onSegment(x[j], y[j], x[i], y[i], px, py))
			{
				return true;
			}
			if (((y[i] > py) != (y[j] > py)) && (px < ((double) (x[j] - x[i]) * (py - y[i]) / (y[j] - y[i])) + x[i]))
			{
				inside = !inside;
			}
		}
		return inside;
	}

	private static boolean intersects(int ax, int ay, int bx, int by, int cx, int cy, int dx, int dy)
	{
		final long abC = cross(ax, ay, bx, by, cx, cy);
		final long abD = cross(ax, ay, bx, by, dx, dy);
		final long cdA = cross(cx, cy, dx, dy, ax, ay);
		final long cdB = cross(cx, cy, dx, dy, bx, by);
		return (((abC <= 0) && (abD >= 0)) || ((abC >= 0) && (abD <= 0))) && (((cdA <= 0) && (cdB >= 0)) || ((cdA >= 0) && (cdB <= 0))) && (Math.max(Math.min(ax, bx), Math.min(cx, dx)) <= Math.min(Math.max(ax, bx), Math.max(cx, dx))) && (Math.max(Math.min(ay, by), Math.min(cy, dy)) <= Math.min(Math.max(ay, by), Math.max(cy, dy)));
	}

	private static boolean onSegment(int ax, int ay, int bx, int by, int px, int py)
	{
		return (cross(ax, ay, bx, by, px, py) == 0) && (px >= Math.min(ax, bx)) && (px <= Math.max(ax, bx)) && (py >= Math.min(ay, by)) && (py <= Math.max(ay, by));
	}

	private static long cross(int ax, int ay, int bx, int by, int px, int py)
	{
		return ((long) (bx - ax) * (py - ay)) - ((long) (by - ay) * (px - ax));
	}

	public void resetIntersections()
	{
		_doorIntersections.clear();
		_fenceIntersections.clear();
		_segmentRecords.clear();
	}

	public void inspect(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
	{
		if (instanceId != 0)
		{
			throw new IllegalArgumentException("Static collision proof requires instance zero.");
		}
		final Set<Integer> doors = new TreeSet<>();
		final Set<String> fences = new TreeSet<>();
		for (Door door : _doors)
		{
			if (matchesDoor(door, fromX, fromY, fromZ, toX, toY, toZ))
			{
				doors.add(door.id());
			}
		}
		for (Fence fence : _fences)
		{
			if (matchesFence(fence, fromX, fromY, fromZ, toX, toY, toZ))
			{
				fences.add(fence.name());
			}
		}
		_doorIntersections.addAll(doors);
		_fenceIntersections.addAll(fences);
		_segmentRecords.add(new SegmentCollision(fromX, fromY, fromZ, toX, toY, toZ, List.copyOf(doors), List.copyOf(fences)));
	}

	public List<SegmentCollision> segmentRecords()
	{
		return List.copyOf(_segmentRecords);
	}

	public int doorIntersections()
	{
		return _doorIntersections.size();
	}

	public int fenceIntersections()
	{
		return _fenceIntersections.size();
	}

	public List<Integer> doorIds()
	{
		return List.copyOf(_doorIntersections);
	}

	public List<String> fenceNames()
	{
		return List.copyOf(_fenceIntersections);
	}
}
