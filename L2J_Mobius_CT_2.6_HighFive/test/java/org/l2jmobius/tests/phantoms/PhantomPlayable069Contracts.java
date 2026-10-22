/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Cheap source/data safety contracts; native evidence is a separate gate. */
public final class PhantomPlayable069Contracts implements PhantomTestSuite
{
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("playable069-contracts", new PhantomPlayable069Contracts(), new PhantomTestContext(690069L, Path.of(args[0]), Path.of(args[1]))));
	}

	@Override
	public String id() { return "playable069-contracts"; }

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-mana-pvp-preserves-olympiad-and-native-skills", this::mana);
		registry.add("02-no-global-raid-curse-generation-or-config-switch", this::curse);
		registry.add("03-frintezza-retains-nonlevel-admission", this::frintezza);
		registry.add("04-mana-price-and-seal-ratios", this::store);
		registry.add("05-visible-merchant-does-not-offer-ready-gear", this::merchant);
		registry.add("06-wind-walk-is-an-allowed-real-skill", this::windWalk);
	}

	private void mana(PhantomTestContext context) throws Exception
	{
		final Path root = Path.of(System.getProperty("phantom.module.root"));
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		final var document = factory.newDocumentBuilder().parse(root.resolve("dist/game/data/stats/items/00700-00799.xml").toFile());
		for (int id : new int[] {726, 728})
		{
			Element item = null;
			final var items = document.getElementsByTagName("item");
			for (int index = 0; index < items.getLength(); index++)
			{
				final Element candidate = (Element) items.item(index);
				if (candidate.getAttribute("id").equals(Integer.toString(id))) { item = candidate; break; }
			}
			PhantomAssertions.assertTrue(item != null, "Missing mana item " + id);
			boolean olympiad = false;
			final var settings = item.getElementsByTagName("set");
			for (int index = 0; index < settings.getLength(); index++)
			{
				final Element set = (Element) settings.item(index);
				olympiad |= set.getAttribute("name").equals("is_oly_restricted") && set.getAttribute("val").equals("true");
			}
			PhantomAssertions.assertTrue(olympiad, "Olympiad restriction changed for " + id);
			final var conditions = item.getElementsByTagName("player");
			for (int index = 0; index < conditions.getLength(); index++)
			{
				PhantomAssertions.assertFalse(((Element) conditions.item(index)).hasAttribute("isPvpFlagged"), "Mana still refuses PvP: " + id);
			}
			PhantomAssertions.assertEquals(id == 728 ? "10001" : "10000", ((Element) item.getElementsByTagName("skill").item(0)).getAttribute("id"), "Mana skill changed.");
		}
	}

	private void curse(PhantomTestContext context) throws Exception
	{
		final Path root = Path.of(System.getProperty("phantom.module.root"));
		for (String relative : List.of("java/org/l2jmobius/gameserver/model/actor/Creature.java", "dist/game/data/scripts/ai/bosses/QueenAnt/QueenAnt.java"))
		{
			PhantomAssertions.assertFalse(Files.readString(root.resolve(relative)).contains("CommonSkill.RAID_CURSE"), "Raid curse generation remains: " + relative);
		}
		final String config = Files.readString(root.resolve("java/org/l2jmobius/gameserver/config/NpcConfig.java"));
		PhantomAssertions.assertFalse(config.contains("config.getBoolean(\"DisableRaidCurse\""), "INI can restore the removed business rule.");
	}

	private void frintezza(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(Path.of(System.getProperty("phantom.module.root"), "dist/game/data/scripts/ai/bosses/Frintezza/LastImperialTomb.java"));
		PhantomAssertions.assertFalse(source.contains("channelMember.getLevel() < 80"), "Frintezza still refuses solely on level.");
		for (String guard : List.of("getCommandChannel()", "FRINTEZZAS_SCROLL", "MIN_PLAYERS", "MAX_PLAYERS", "getInstanceTime(", "LocationUtil.checkIfInRange"))
		{
			PhantomAssertions.assertTrue(source.contains(guard), "Native nonlevel admission guard missing: " + guard);
		}
	}

	private void store(PhantomTestContext context) throws Exception
	{
		final Path root = Path.of(System.getProperty("phantom.module.root"));
		final var factory = DocumentBuilderFactory.newInstance();
		for (int list : new int[] {91003, 91004})
		{
			final Path file = root.resolve("dist/game/data/multisell/" + list + ".xml");
			PhantomAssertions.assertTrue(Files.exists(file), "Missing native consumables/exchange list " + list);
			final var document = factory.newDocumentBuilder().parse(file.toFile());
			final var items = document.getElementsByTagName("item");
			PhantomAssertions.assertEquals(list == 91003 ? 1 : 3, items.getLength(), "Unexpected offer count.");
			for (int index = 0; index < items.getLength(); index++)
			{
				final Element item = (Element) items.item(index);
				final var costs = item.getElementsByTagName("ingredient");
				final var products = item.getElementsByTagName("production");
				PhantomAssertions.assertEquals(1, costs.getLength(), "Single native cost required for atomic debit.");
				PhantomAssertions.assertEquals(1, products.getLength(), "Single product required.");
				final Element cost = (Element) costs.item(0), product = (Element) products.item(0);
				PhantomAssertions.assertEquals(Integer.toString(list == 91003 ? 57 : 6360 + index), cost.getAttribute("id"), "Wrong native currency.");
				PhantomAssertions.assertEquals(list == 91003 ? "1000" : "1", cost.getAttribute("count"), "Wrong native debit.");
				PhantomAssertions.assertEquals(list == 91003 ? "728" : "5575", product.getAttribute("id"), "Wrong native product.");
				PhantomAssertions.assertEquals(list == 91003 ? "1" : Integer.toString(new int[] {3, 5, 10}[index]), product.getAttribute("count"), "Wrong native ratio.");
			}
		}
	}

	private void merchant(PhantomTestContext context) throws Exception
	{
		final String html = Files.readString(Path.of(System.getProperty("phantom.module.root"), "dist/game/data/html/CommunityBoard/Custom/merchant/main.html"));
		for (int list : new int[] {600010, 600014, 600016})
		{
			PhantomAssertions.assertFalse(html.contains(";" + list + ","), "Visible ready-gear offer " + list);
		}
		PhantomAssertions.assertTrue(html.contains(";91003,"), "Mana shop is unreachable.");
		PhantomAssertions.assertTrue(html.contains(";91004,"), "Seal exchange is unreachable.");
	}

	private void windWalk(PhantomTestContext context) throws Exception
	{
		final Path root = Path.of(System.getProperty("phantom.module.root"));
		PhantomAssertions.assertTrue(Files.readString(root.resolve("dist/game/data/html/CommunityBoard/Custom/buffer/main.html")).contains("_bbsbuff;1204,2;buffer/main"), "Wind Walk2 missing in menu.");
		final String config = Files.readString(root.resolve("dist/game/config/Custom/CommunityBoard.ini"));
		PhantomAssertions.assertTrue(config.lines().filter(line -> line.startsWith("CommunityAvailableBuffs =")).anyMatch(line -> java.util.Arrays.asList(line.substring(line.indexOf('=') + 1).trim().split(",")).contains("1204")), "Wind Walk is not permitted.");
		PhantomAssertions.assertTrue(Files.readString(root.resolve("dist/game/data/stats/skills/01200-01299.xml")).contains("<skill id=\"1204\" levels=\"2\""), "Unsupported Wind Walk rank.");
	}
}
