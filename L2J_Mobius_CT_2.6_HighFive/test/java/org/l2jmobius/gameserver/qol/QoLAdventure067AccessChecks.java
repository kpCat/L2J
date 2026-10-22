/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.data.holders.AccessLevel;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Real access profiles are policy inputs only: no Player receives elevated access. */
public final class QoLAdventure067AccessChecks
{
	public static void main(String[] args) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final var profiles = factory.newDocumentBuilder().parse(Path.of("config/AccessLevels.xml").toFile()).getElementsByTagName("access");
		int checked = 0;
		for (int i = 0; i < profiles.getLength(); i++)
		{
			final var element = (org.w3c.dom.Element) profiles.item(i);
			final var values = new StatSet();
			values.set("level", element.getAttribute("level")); values.set("name", element.getAttribute("name")); values.set("isGM", element.getAttribute("isGM"));
			final var access = new AccessLevel(values);
			PhantomAssertions.assertEquals(access.getLevel() == 0, PersonalCharacterQoLService.isOrdinaryAdventureAccess(access), "Personal adventure accepted a nonzero stock access profile: " + access.getLevel());
			checked++;
		}
		PhantomAssertions.assertTrue(checked >= 8, "Stock access profile matrix is unexpectedly empty.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLService.isOrdinaryAdventureAccess(null), "Missing access profile was admitted.");
		System.out.println("OWN067_ACCESS_PROFILE_PASS profiles=" + checked + "; onlyAccess0=true; PlayerRightsChanged=0; DB_WRITES=0");
	}
}
