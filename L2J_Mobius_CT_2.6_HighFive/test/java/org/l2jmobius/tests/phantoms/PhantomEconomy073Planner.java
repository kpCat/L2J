/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.stats.MoveType;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.*;

/** Read-only, pre-birth economic shortlist. Creates neither Player nor account, and changes no world input. */
final class PhantomEconomy073Planner
{
	static record Scene(Merchant merchant, Monster farm) { }
	private record Option(Merchant merchant, Monster farm, int list, long base, long price, double distance, long threshold, double meanA, long expectedKills) { }
	static Scene select(PhantomCommerceCatalog catalog, StringBuilder report, PhantomGameKnowledgeQuery query) throws Exception
	{
		final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		final int perHit = ((org.l2jmobius.gameserver.model.item.Weapon) ItemData.getInstance().getTemplate(129)).getSoulShotCount();
		// The native template base run speed is a conservative pre-birth bound; exact live speed is checked before measurement.
		final double speed = PlayerTemplateData.getInstance().getTemplate(PlayerClass.WARRIOR).getBaseMoveSpeed(MoveType.RUN);
		final var objects = World.getInstance().getVisibleObjects();
		final var mobs = objects.stream().filter(value -> value instanceof Monster).map(value -> (Monster) value)
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == 0 && mob.getLevel() >= 18 && mob.getLevel() <= 22 && !mob.isInsideZone(ZoneId.PEACE) && mob.getExpReward(20) > 0 && mob.getSpReward(20) > 0).toList();
		final var options = new ArrayList<Option>();
		for (var offer : catalog.findBuyOffers(1463, 0, 256).values())
		{
			if (offer.limitedStock() || offer.price() <= 0) { continue; }
			for (var merchant : objects.stream().filter(value -> value instanceof Merchant).map(value -> (Merchant) value).filter(npc -> npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0 && offer.npcIds().contains(npc.getId()) && npc.getMpc() != null && npc.getMpc().getCastleTaxRate() == 0).toList())
			{
				final Set<Integer> types = new HashSet<>();
				for (var mob : mobs.stream().filter(value -> value.calculateDistance3D(merchant) >= 250 && value.calculateDistance3D(merchant) <= 5000).sorted(Comparator.comparingDouble(value -> value.calculateDistance3D(merchant))).toList())
				{
					if (types.contains(mob.getId()) || mobs.stream().filter(other -> other.calculateDistance3D(mob) <= 700).limit(3).count() < 3) { continue; }
					types.add(mob.getId());
					final long price = (long) (offer.price() * (1 + merchant.getMpc().getBaseTaxRate()));
					final double distance = mob.calculateDistance3D(merchant);
					final long useful = perHit * Math.min(policy.targetHits(), Math.max(policy.lowWaterHits() + 1, (long) Math.ceil(distance / speed)));
					long balance = 0;
					while (policy.availableBudget(balance, 0, 0, true) < useful * price) { balance++; }
					final double mean = meanAdena(mob);
					options.add(new Option(merchant, mob, offer.listId(), offer.price(), price, distance, balance, mean, mean <= 0 ? Long.MAX_VALUE : (long) Math.ceil(Math.max(0, balance - 100) / mean)));
				}
			}
		}
		final var rows = new StringBuilder("merchant\tmob\tlevel\tbasePrice\tbaseTax\tcastleTax\teffectivePrice\tweapon\tperCharge\tspeedLowerBound\tdirectDistance\toutWalk\tbackWalk\troundTripETA\treserve\tfee\twalletThreshold\tavailableBudget\tusefulCount\tmeanAdenaPerKillX1\texpectedKillsFrom100Fixture\trewardEXP_SP\tstatus\n");
		report.append("planner_inputs\tobjects=").append(objects.size()).append(";rewardedMobs18to22=").append(mobs.size()).append(";offers=").append(catalog.findBuyOffers(1463, 0, 256).values().size()).append(";economicOptions=").append(options.size()).append(";rates=").append(org.l2jmobius.gameserver.config.RatesConfig.RATE_XP).append('/').append(org.l2jmobius.gameserver.config.RatesConfig.RATE_SP).append('\n');
		final var paths = new L2jNavigationBackend(); final var navigationPolicy = PhantomNavigationPolicy.productionDefaults();
		Scene best = null; double bestCost = Double.MAX_VALUE;
		for (var option : options.stream().sorted(Comparator.comparingLong(Option::expectedKills).thenComparingDouble(Option::distance)).limit(5).toList())
		{
			final var mob = option.farm(); final var merchant = option.merchant();
			final var origin = paths.walkSurface(new PhantomNavigationPoint(mob.getX(), mob.getY(), mob.getZ(), 0), 256).orElse(null);
			PhantomWalkCorridorPlanner.Result out = null, back = null;
			String status = option.expectedKills() > 45 ? "EXPECTED_WALLET_THRESHOLD" : "GEO_ROUTE";
			if (origin != null && option.expectedKills() <= 45)
			{
				for (int radius : new int[] {0, 64, 112})
				{
					final double angle = Math.atan2((long) mob.getY() - merchant.getY(), (long) mob.getX() - merchant.getX());
					final var target = paths.walkSurface(new PhantomNavigationPoint(merchant.getX() + (int) Math.round(radius * Math.cos(angle)), merchant.getY() + (int) Math.round(radius * Math.sin(angle)), merchant.getZ(), 0), 256).orElse(null);
					if (target == null || merchant.calculateDistance3D(target.x(), target.y(), target.z()) > 184) { continue; }
					long now = System.nanoTime();
					out = paths.findWalkCorridor(new PhantomNavigationRequest(1, origin, target, now, now + TimeUnit.SECONDS.toNanos(1), 10_000), new PhantomNavigationCancellationToken(), navigationPolicy, System::nanoTime);
					status = out.status().name(); if (out.status() != PhantomWalkCorridorPlanner.Status.FOUND) { continue; }
					now = System.nanoTime();
					back = paths.findWalkCorridor(new PhantomNavigationRequest(1, target, origin, now, now + TimeUnit.SECONDS.toNanos(1), 10_000), new PhantomNavigationCancellationToken(), navigationPolicy, System::nanoTime);
					status = back.status().name(); if (back.status() == PhantomWalkCorridorPlanner.Status.FOUND) { break; }
				}
			}
			final boolean viable = out != null && back != null && out.status() == PhantomWalkCorridorPlanner.Status.FOUND && back.status() == PhantomWalkCorridorPlanner.Status.FOUND;
			final double outward = viable ? length(origin, out.points()) : -1, returning = viable ? length(out.points().getLast(), back.points()) : -1;
			final long budget = policy.availableBudget(option.threshold(), 0, 0, true), count = budget / option.price();
			rows.append(merchant.getId()).append('/').append(merchant.getObjectId()).append('\t').append(mob.getId()).append('\t').append(mob.getLevel()).append('\t').append(option.base()).append('\t').append(merchant.getMpc().getBaseTaxRate()).append('\t').append(merchant.getMpc().getCastleTaxRate()).append('\t').append(option.price()).append("\t129:D\t").append(perHit).append('\t').append(speed).append('\t').append(option.distance()).append('\t').append(outward).append('\t').append(returning).append('\t').append(viable ? (outward + returning) / speed : -1).append('\t').append(option.threshold() - budget).append("\t0\t").append(option.threshold()).append('\t').append(budget).append('\t').append(count).append('\t').append(option.meanA()).append('\t').append(option.expectedKills()).append('\t').append(mob.getExpReward(20)).append('/').append(mob.getSpReward(20)).append('\t').append(viable ? "VIABLE" : status).append('\n');
			final double cost = option.expectedKills() * 20 + (outward + returning) / speed;
			if (viable && cost < bestCost) { best = new Scene(merchant, mob); bestCost = cost; }
		}
		final Path matrix = Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task073/reports/SCENE_ECONOMICS073-" + System.currentTimeMillis() + ".tsv");
		Files.writeString(matrix, rows, java.nio.file.StandardOpenOption.CREATE_NEW);
		report.append("economic_shortlist\t").append(matrix).append(";max5;no Player/account created;X1 projected mean is not a guaranteed drop;15min actual earning bound;fixture100A separately declared\n");
		if (best == null) { throw new AssertionError("SCENE_MISSING_ECONOMIC_VIABILITY: no owned actor created."); }
		report.append("PREFLIGHT_ACCEPTED\tmerchant=").append(best.merchant().getId()).append(';').append(best.merchant().getLocation()).append(";farm=").append(best.farm().getId()).append(';').append(best.farm().getLocation()).append(";native both-way corridor+tax/budget/usefulness;no economic policy edits\n");
		return best;
	}
	private static double meanAdena(Monster mob)
	{
		double mean = 0;
		if (mob.getTemplate().getDropList() != null) { for (var drop : mob.getTemplate().getDropList()) { if (drop.getItemId() == 57) { mean += (drop.getMin() + drop.getMax()) / 2.0 * drop.getChance() / 100; } } }
		if (mob.getTemplate().getDropGroups() != null) { for (var group : mob.getTemplate().getDropGroups()) { for (var drop : group.getDropList()) { if (drop.getItemId() == 57) { mean += (drop.getMin() + drop.getMax()) / 2.0 * drop.getChance() / 100 * group.getChance() / 100; } } } }
		return mean;
	}
	private static double length(PhantomNavigationPoint start, List<PhantomNavigationPoint> points) { double length = 0; for (var next : points) { length += start.distanceTo(next); start = next; } return length; }
}
