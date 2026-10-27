/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;

/** Source inventory and production contracts only; never opens JDBC, Geo, Player or World. */
public final class PhantomCityJourneys097Test
{
	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw new AssertionError("TASK097_" + reason); }
	}

	private static Element xml(Path file) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
	}

	private static List<Element> elements(Element root, String name)
	{
		final var result = new ArrayList<Element>();
		final var nodes = root.getElementsByTagName(name);
		for (int index = 0; index < nodes.getLength(); index++) { result.add((Element) nodes.item(index)); }
		return List.copyOf(result);
	}

	private static Element anchor(Path file, String id) throws Exception
	{
		return elements(xml(file), "anchor").stream().filter(value -> id.equals(value.getAttribute("id"))).findFirst().orElseThrow();
	}

	private static int number(Element value, String key) { return Integer.parseInt(value.getAttribute(key)); }
	private static Properties settings(Path file) throws Exception
	{
		final var properties = new Properties();
		try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
		return properties;
	}

	private static PhantomNavigationPoint point(Element value, String prefix)
	{
		return new PhantomNavigationPoint(number(value, prefix + "X"), number(value, prefix + "Y"), number(value, prefix + "Z"), 0);
	}

	private static Leg sourceAudit(Path game) throws Exception
	{
		final var giran = anchor(game.resolve("data/phantoms/topology/high-five-core.xml"), "giran.city.center");
		final var gludin = anchor(game.resolve("data/phantoms/topology/high-five-career-recovery.xml"), "gludin.recovery.anchor");
		check("CITY_CENTER".equals(giran.getAttribute("role")), "GIRAN_CITY_ROLE");
		check("RESPAWN".equals(gludin.getAttribute("role")), "GLUDIN_IS_RECOVERY_ONLY");
		final var all = elements(xml(game.resolve("data/phantoms/travel/high-five-normal-gk.xml")), "leg");
		final var incoming = all.stream().filter(value -> "giran.gatekeeper.30080".equals(value.getAttribute("toAnchorId"))).toList();
		check(!incoming.isEmpty(), "NO_SOURCE_BACKED_CITY_LEG");
		Leg gludio = null;
		for (var entry : incoming)
		{
			final int npc = number(entry, "teleporterNpcId"), index = number(entry, "destinationIndex");
			final var stock = elements(xml(game.resolve("data/teleporters/town/" + npc + ".xml")), "npc").stream().filter(value -> number(value, "id") == npc).findFirst().orElseThrow();
			final var normal = elements(stock, "teleport").stream().filter(value -> "NORMAL".equals(value.getAttribute("type"))).findFirst().orElseThrow();
			final var nativeChoice = elements(normal, "location").get(index);
			check(point(entry, "destination").equals(new PhantomNavigationPoint(number(nativeChoice, "x"), number(nativeChoice, "y"), number(nativeChoice, "z"), 0)), "NATIVE_DESTINATION_DRIFT");
			final int feeId = nativeChoice.hasAttribute("feeId") ? number(nativeChoice, "feeId") : 57;
			check(number(entry, "feeId") == feeId && feeId == 57 && number(entry, "feeCount") == number(nativeChoice, "feeCount"), "NATIVE_PRICE_DRIFT");
			final var castles = PhantomNormalGatekeeperTravel.parseDestinationCastleIds(entry.getAttribute("destinationCastleIds"));
			check(castles.equals(List.of(number(nativeChoice, "castleId"))), "NATIVE_CASTLE_DRIFT");
			System.out.println("TASK097_ROUTE source=DB_FREE origin=" + entry.getAttribute("fromAnchorId") + " npc=" + npc + " index=" + index + " baseAdena=" + entry.getAttribute("feeCount") + " nativeIngress=UNPROVEN");
			if ("generated.route.live002.gludio.arrival.anchor".equals(entry.getAttribute("fromAnchorId")))
			{
				gludio = new Leg(entry.getAttribute("id"), entry.getAttribute("fromAnchorId"), entry.getAttribute("toAnchorId"), entry.getAttribute("sourceConnectorId"), entry.getAttribute("transitionId"), entry.getAttribute("destinationConnectorId"), npc, "NORMAL", index,
					number(entry, "destinationX"), number(entry, "destinationY"), number(entry, "destinationZ"), number(entry, "sourceX"), number(entry, "sourceY"), number(entry, "sourceZ"), feeId, number(entry, "feeCount"), number(entry, "travelMillis"), entry.getAttribute("sourceRefs"), castles);
			}
		}
		check(gludio != null, "GLUDIO_GIRAN_SOURCE_LEG_REQUIRED");
		creationGuards();
		auditOrigins(game, giran, incoming);
		System.out.println("PASS city097.source-audit catalogLegs=" + all.size() + " giranIncoming=" + incoming.size() + " secondCity=RESPAWN_ONLY");
		return gludio;
	}

	private static void auditOrigins(Path game, Element giran, List<Element> incoming) throws Exception
	{
		stockCreation(settings(game.resolve("config/Custom/StartingLocation.ini")), settings(game.resolve("config/Custom/FactionSystem.ini")));
		final var classes = elements(elements(xml(game.resolve("data/phantoms/population/high-five-population-v3.xml")), "classes").getFirst(), "class").stream().map(value -> number(value, "id")).toList();
		final int local = PhantomNavigationPolicy.productionDefaults().maximumLocalStraightDistance();
		int checked = 0, possible = 0;
		try (var files = Files.list(game.resolve("data/stats/players/templates/StartingClass")))
		{
			for (var file : files.filter(value -> value.toString().endsWith(".xml")).sorted().toList())
			{
				final var template = xml(file);
				final int classId = Integer.parseInt(elements(template, "classId").getFirst().getTextContent().strip());
				if (!classes.contains(classId)) { continue; }
				final var births = elements(elements(template, "creationPoints").getFirst(), "node");
				check(!births.isEmpty(), "MISSING_CREATION_POINTS");
				double nearest = Double.POSITIVE_INFINITY, direct = Double.POSITIVE_INFINITY;
				for (var birth : births)
				{
					final int x = number(birth, "x"), y = number(birth, "y");
					direct = Math.min(direct, Math.hypot((long) x - number(giran, "x"), (long) y - number(giran, "y")));
					for (var leg : incoming) { nearest = Math.min(nearest, Math.hypot((long) x - number(leg, "sourceX"), (long) y - number(leg, "sourceY"))); }
				}
				if (nearest <= local || direct <= local) { possible++; }
				checked++;
				System.out.println("TASK097_BIRTH source=DB_FREE class=" + classId + " minimumGiranIngressXY=" + (long) nearest + " minimumDirectXY=" + (long) direct + " localLimit=" + local + " actualActor=NOT_CREATED");
			}
		}
		check(checked == classes.size(), "INCOMPLETE_STARTING_CLASS_AUDIT");
		System.out.println("TASK097_G0 classes=" + checked + " possibleFirstMiles=" + possible + " assessment=" + (possible == 0 ? "NO_COMPLETE_NATIVE_GIRAN_ROUTE_FROM_STOCK_BIRTHS" : "NATIVE_ROUTE_PROOF_REQUIRED") + " scope=STOCK_BIRTHS_ONLY ordinaryLivePopulation=NOT_AUDITED");
	}

	private static void stockCreation(Properties custom, Properties faction)
	{
		check("False".equalsIgnoreCase(custom.getProperty("CustomStartingLocation", "True").strip()), "NONSTOCK_CREATION_LOCATION_REQUIRES_AUDIT");
		check("False".equalsIgnoreCase(faction.getProperty("EnableFactionSystem", "True").strip()), "FACTION_CREATION_LOCATION_REQUIRES_AUDIT");
	}

	private static void creationGuards()
	{
		final var custom = new Properties(); custom.setProperty("CustomStartingLocation", "False");
		final var faction = new Properties(); faction.setProperty("EnableFactionSystem", "False");
		stockCreation(custom, faction);
		for (var entry : List.of(custom, faction))
		{
			final String key = entry.stringPropertyNames().iterator().next();
			entry.setProperty(key, "True");
			try { stockCreation(custom, faction); }
			catch (AssertionError expected) { entry.setProperty(key, "False"); continue; }
			throw new AssertionError("TASK097_CREATION_OVERRIDE_ACCEPTED");
		}
	}

	private static void fees(Path game, Leg leg) throws Exception
	{
		final int previous = PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			PlayerConfig.MAX_FREE_TELEPORT_LEVEL = Integer.parseInt(settings(game.resolve("config/Player.ini")).getProperty("MaxFreeTeleportLevel").strip());
			final long ordinaryTime = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, ZoneId.systemDefault()).toEpochSecond() / 60;
			final long discounted = ZonedDateTime.of(2026, 10, 27, 21, 0, 0, 0, ZoneId.systemDefault()).toEpochSecond() / 60;
			check(PhantomNormalGatekeeperTravel.fee(leg, 0, 1, ordinaryTime) == 0, "STOCK_NEWBORN_FREE_LEVEL");
			check(PhantomNormalGatekeeperTravel.fee(leg, 0, PlayerConfig.MAX_FREE_TELEPORT_LEVEL + 1, ordinaryTime) == leg.feeCount(), "STOCK_PAID_LEVEL");
			check(PhantomNormalGatekeeperTravel.fee(leg, 1, 1, ordinaryTime) == leg.feeCount(), "SUBCLASS_NOT_FREE");
			check(PhantomNormalGatekeeperTravel.fee(leg, 0, PlayerConfig.MAX_FREE_TELEPORT_LEVEL + 1, discounted) == leg.feeCount() / 2, "STOCK_DISCOUNT");
		}
		finally { PlayerConfig.MAX_FREE_TELEPORT_LEVEL = previous; }
		System.out.println("PASS city097.stock-fees source=DB_FREE nativePayment=NOT_RUN");
	}

	private static void routeContracts(Leg leg)
	{
		// Structural points deliberately carry no Geo/native movement attestation.
		final var from = new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0);
		final var landing = new PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), leg.destinationZ(), 0);
		final var city = new PhantomNavigationPoint(82480, 149087, -3350, 0);
		final var gk = new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, from, landing, List.of(), leg, 0, leg.feeCount(), 0);
		final var walk = new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, landing, city, List.of(city), null, 15000, 0, 0);
		final var complete = new PhantomTravelStrategy.Candidate("city097.structural", List.of(gk, walk));
		final var buy = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "City contract", List.of(new PhantomStorePlan.Line(1865, 1865, 10, 100)), 1);
		final long funds = Math.addExact(leg.feeCount(), 1000);
		check(PhantomAutonomousMarketProducer.completeRestRoute(complete, from, city, PhantomAutonomousMarketProducer.restTravelBudget(buy, funds), 15000), "COMPLETE_BUDGETED_ROUTE");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(complete, from, city, PhantomAutonomousMarketProducer.restTravelBudget(buy, funds - 1), 15000), "BUY_RESERVE_MUST_NOT_PAY_GK");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(complete, from, city, funds, 14999), "DEADLINE_REJECTED");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(null, from, city, funds, 15000), "NO_ROUTE_REJECTED");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(new PhantomTravelStrategy.Candidate("partial", List.of(gk)), from, city, funds, 15000), "GK_LANDING_IS_NOT_CITY_ARRIVAL");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(complete, landing, city, funds, 15000), "RETARGETED_ORIGIN_REJECTED");
		final var returning = new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK, from, city, List.of(), null, 1, 0, 0);
		check(!PhantomAutonomousMarketProducer.completeRestRoute(new PhantomTravelStrategy.Candidate("return", List.of(returning)), from, city, funds, 15000), "REST_RETURN_NOT_ALLOWED");
		check(PhantomAutonomousMarketProducer.acceptedRestGatekeeper(PhantomNativeTravelActions.Status.ARRIVED), "ARRIVED_RECEIPT");
		for (var status : PhantomNativeTravelActions.Status.values())
		{
			if (status != PhantomNativeTravelActions.Status.ARRIVED) { check(!PhantomAutonomousMarketProducer.acceptedRestGatekeeper(status), "UNCERTAIN_PAYMENT_NOT_REPLAYABLE"); }
		}
		System.out.println("PASS city097.route-resource-receipt-contracts source=DB_FREE geometry=SYNTHETIC World=NOT_STARTED STORE=NOT_RUN restart=NOT_RUN");
	}

	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final var leg = sourceAudit(module.resolve("dist/game"));
		fees(module.resolve("dist/game"), leg);
		routeContracts(leg);
		System.out.println("TASK097_DB_FREE_PASS tests=3 ordinaryVisitor=UNPROVEN nativeArrival=NOT_RUN");
	}
}
