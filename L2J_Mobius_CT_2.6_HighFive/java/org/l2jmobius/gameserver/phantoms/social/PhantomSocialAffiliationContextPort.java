/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/** Resolves transient social affiliation from current canonical game state. */
@FunctionalInterface
public interface PhantomSocialAffiliationContextPort
{
	SocialEventContext resolve(long ownerProfileId, SubjectRef subject);

	static PhantomSocialAffiliationContextPort noop()
	{
		return (_, _) -> SocialEventContext.NONE;
	}
}