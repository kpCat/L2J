/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.CastleSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Target;

public interface PhantomSiegeAuthority
{
	Optional<CastleSnapshot> observeCastle(int castleId);

	Optional<ActorSnapshot> observeActor(long profileId, int castleId);

	RegistrationResult registerAttacker(long profileId, int castleId);

	List<MemberSnapshot> managedClanMembers(int clanId, int limit);

	List<Target> opposingPlayers(long profileId, int castleId, int limit, int maximumDistance);

	List<Target> attackableDoors(long profileId, int castleId, int limit, int maximumDistance);
}
