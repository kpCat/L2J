/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

/** DB-free exact native service admission; purchase evidence is a separate native gate. */
public final class PhantomMammon071Contracts
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) { throw new IllegalArgumentException("Game root required."); }
		final Path game = Path.of(args[0]);
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final int[] lists = {311262501,311262502,311262503,311262504,311262505,311262509,311262510,311262511,311262512,311262513,311262516,311262519,311132501};
		for (int id : lists)
		{
			final var document = factory.newDocumentBuilder().parse(game.resolve("data/multisell/" + id + ".xml").toFile());
			final var npcs = document.getElementsByTagName("npc"); boolean pushkin = false, original = false;
			for (int i = 0; i < npcs.getLength(); i++)
			{
				final String value = npcs.item(i).getTextContent().strip();
				pushkin |= value.equals("30300"); original |= value.equals(id == 311132501 ? "31113" : "31126");
			}
			if (!pushkin || !original) { throw new AssertionError("Pushkin and original Mammon eligibility required: " + id); }
		}
		final String html = Files.readString(game.resolve("data/html/trainer/30300.htm"));
		if (!html.contains("seven_signs/mammblack_1.htm") || !html.contains("seven_signs/mammmerch_1.htm") || !html.contains("multisell 91004")) { throw new AssertionError("Pushkin original exchange and native Mammon menus required."); }
		System.out.println("MAMMON071_CONTRACT_PASS lists=13; original NPC/service bindings retained; native purchase NOT_INFERRED");
	}
}
