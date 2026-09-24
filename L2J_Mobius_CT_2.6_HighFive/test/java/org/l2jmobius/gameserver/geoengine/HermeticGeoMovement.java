package org.l2jmobius.gameserver.geoengine;

/** Test-tooling bridge to the package-local shared movement core. */
public final class HermeticGeoMovement
{
	public static boolean canMove(GeoEngine geo, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId, StaticXmlCollisionOracle oracle)
	{
		return geo.canMoveToTarget(fromX, fromY, fromZ, toX, toY, toZ, instanceId, oracle);
	}
}
