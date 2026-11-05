/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.w3c.dom.Element;

/** Production fast-path selection with stock identities; geometry remains a structural fixture. */
public final class PhantomLivingRoutes110Test
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK110_" + reason); } }
	private static PhantomTravelStrategy.Options options(List<Leg> legs, long funds, int index, int level)
	{
		return new PhantomTravelStrategy.Options(legs, 100, funds, 0, index, level, 29885040, true, "market.no_return", null, 0, 3_600_000, Set.of());
	}
	private static List<Leg> stock(Path module) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final var nodes = factory.newDocumentBuilder().parse(module.resolve("dist/game/data/phantoms/travel/high-five-normal-gk.xml").toFile()).getElementsByTagName("leg");
		final var result = new ArrayList<Leg>();
		for (int i = 0; i < nodes.getLength(); i++)
		{
			final var node = (Element) nodes.item(i);
			// Two actual independent sources: Oren and Gludio; stock097 verifies their NORMAL XML.
			final int npc = Integer.parseInt(node.getAttribute("teleporterNpcId"));
			if ((npc != 30177 && npc != 30256) || !"giran.gatekeeper.30080".equals(node.getAttribute("toAnchorId"))) { continue; }
			result.add(new Leg(node.getAttribute("id"), node.getAttribute("fromAnchorId"), node.getAttribute("toAnchorId"), node.getAttribute("sourceConnectorId"), node.getAttribute("transitionId"), node.getAttribute("destinationConnectorId"), npc,
				node.getAttribute("teleportListName"), Integer.parseInt(node.getAttribute("destinationIndex")), Integer.parseInt(node.getAttribute("destinationX")), Integer.parseInt(node.getAttribute("destinationY")), Integer.parseInt(node.getAttribute("destinationZ")),
				Integer.parseInt(node.getAttribute("sourceX")), Integer.parseInt(node.getAttribute("sourceY")), Integer.parseInt(node.getAttribute("sourceZ")), Integer.parseInt(node.getAttribute("feeId")), Long.parseLong(node.getAttribute("feeCount")), Long.parseLong(node.getAttribute("travelMillis")), node.getAttribute("sourceRefs"), java.util.Arrays.stream(node.getAttribute("destinationCastleIds").split(",")).filter(value -> !value.isBlank()).map(Integer::valueOf).toList()));
		}
		check(result.size() == 2, "STOCK_TWO_FAMILIES_REQUIRED");
		return result;
	}
	private static PhantomTravelStrategy.Candidate discounted(Leg leg)
	{
		final var from = new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0);
		final var to = new PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), leg.destinationZ(), 0);
		return new PhantomTravelStrategy.Candidate("stock." + leg.id(), List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, from, to, List.of(), leg, 0, leg.feeCount() / 2, 0)));
	}
	public static void main(String[] args) throws Exception
	{
		final int old = PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			PlayerConfig.MAX_FREE_TELEPORT_LEVEL = 40;
			final var legs = stock(Path.of(System.getProperty("phantom.module.root")));
			for (var leg : legs)
			{
				final var quoted = discounted(leg);
				check(L2jNavigationBackend.selectTravel(List.of(quoted), options(legs, 0, 1, 80)).candidate() != null, "EMPTY_PHANTOM_WALLET_BLOCKS_NATIVE_ROUTE npc=" + leg.teleporterNpcId());
				final var full = L2jNavigationBackend.selectTravel(List.of(quoted), options(legs, leg.feeCount(), 1, 80)).candidate();
				check(full != null && full.adena() == 0, "PHANTOM_TRAVEL_RESERVE_NOT_ZERO");
				final var buy = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(1865, 1865, 10, 100)), 120001);
				check(PhantomAutonomousMarketProducer.restTravelBudget(buy, 1000) == 0 && L2jNavigationBackend.selectTravel(List.of(quoted), options(legs, 0, 1, 80)).candidate().adena() == 0, "FREE_TRAVEL_CONSUMED_BUY_RESERVE");
				check(L2jNavigationBackend.selectTravel(List.of(quoted), options(legs, 0, 0, 40)).candidate().adena() == 0, "LAWFUL_FREE_LEVEL_LOST");
				check(L2jNavigationBackend.selectTravel(List.of(quoted), options(legs, 0, 1, 1)).candidate().adena() == 0, "PHANTOM_SUBCLASS_CHARGED");
				check(PhantomNormalGatekeeperTravel.maximumFee(leg, 1, 80) == leg.feeCount(), "CANONICAL_PRICE_CHANGED");
				System.out.println("PASS living110.fast-path-full-reserve npc=" + leg.teleporterNpcId() + " maxFee=" + full.adena() + " nativePayment=NOT_RUN geometry=STRUCTURAL_FIXTURE");
			}
		}
		finally { PlayerConfig.MAX_FREE_TELEPORT_LEVEL = old; }
	}
}
