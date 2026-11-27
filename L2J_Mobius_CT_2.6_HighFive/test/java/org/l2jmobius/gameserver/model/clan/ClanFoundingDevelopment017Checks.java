/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.FeatureConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.DurableOutcome;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.Payment;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.itemcontainer.ItemContainer;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Exact receipt and stock fee staging against isolated, connection-local native row fixtures. */
public final class ClanFoundingDevelopment017Checks
{
	private static final Pattern ISOLATED_URL = Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(\\d{1,5})/" + PhantomTestDatabaseGuard.TARGET_DATABASE);
	private static final long BEFORE_ADENA = 1_500_000;
	private static final long BEFORE_SP = 50_000;
	private static final Payment PAYMENT = new Payment(7, 11, 29, 88, 37, BEFORE_ADENA, BEFORE_SP, new UUID(17, 1));

	private ClanFoundingDevelopment017Checks()
	{
	}

	public static void main(String[] args) throws Exception
	{
		final Payment other = new Payment(7, 11, 29, 88, 37, BEFORE_ADENA, BEFORE_SP, new UUID(17, 2));
		require(PAYMENT.itemAfter() == 850_000 && PAYMENT.spAfter() == 30_000, "Stock costs changed.");
		require(PAYMENT.evidence().length == 56 && Arrays.equals(PAYMENT.evidence(), PAYMENT.evidence()) && !Arrays.equals(PAYMENT.evidence(), other.evidence()), "Receipt does not identify the exact operation.");
		System.out.println("PASS C4 stock fees and exact payment identity");
		final Payment secondLevel = new Payment(7, 11, 29, 88, 37, 3_000_000, 120_000, new UUID(21, 21002102), 1);
		require(secondLevel.itemAfter() == 500_000 && secondLevel.spAfter() == 20_000 && secondLevel.evidence().length != PAYMENT.evidence().length, "L2 costs and receipt must identify the exact transition.");
		verifyRequirements();
		if (args.length == 0) { return; }
		final var target = ISOLATED_URL.matcher(args[0]);
		require((args.length == 1 || args.length == 3) && target.matches(), "Only the isolated loopback test schema may run SQL proof.");
		final int port = Integer.parseInt(target.group(1));
		require(port >= 33332 && port <= 65535, "Isolated test port required.");
		final String user = System.getenv("PHANTOM_C4_TEST_USER");
		final String password = System.getenv("PHANTOM_C4_TEST_PASSWORD");
		require(user != null && !user.isBlank() && password != null, "Explicit isolated test credentials required.");
		try (var connection = DriverManager.getConnection(args[0], user, password))
		{
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT DATABASE(), @@port"))
			{
				require(rows.next() && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(rows.getString(1)) && rows.getInt(2) == port, "Connected server differs from the isolated target.");
			}
			verifyPaidStaging(connection, other);
			verifySecondLevel(connection, secondLevel);
		}
		System.out.println("PASS C4 isolated MariaDB stock fees, rollback, readback and replay prevention");
		if (args.length == 3) { verifyNativeDebit(args[0], user, password, Path.of(args[1]), Path.of(args[2])); }
	}

	private static void verifyRequirements()
	{
		for (int current = 0; current < 11; current++)
		{
			final var requirement = Clan.levelRequirement(current);
			require(requirement.currentLevel() == current && requirement.nextLevel() == current + 1 && requirement.territory() == (current == 10), "Broken stock clan level boundary.");
		}
		require(Clan.levelRequirement(-1) == null && Clan.levelRequirement(11) == null, "Nonexistent level transition.");
		require(Clan.levelRequirement(2).itemId() == 1419 && Clan.levelRequirement(3).itemId() == 3874 && Clan.levelRequirement(4).itemId() == 3870, "Stock quest or loot item changed.");
		require(Clan.levelRequirement(8).itemCount() == 150 && Clan.levelRequirement(9).itemCount() == 5, "Stock high-level item requirements changed.");
		System.out.println("PASS 021 stock requirement boundaries for levels 0-11");
	}

	private static void verifyNativeDebit(String url, String user, String password, Path config, Path datapack) throws Exception
	{
		final Path scratch = config.toRealPath().getParent();
		require(Files.isRegularFile(scratch.resolve(".codex-owned.json")), "The native fixture requires marked scratch.");
		try (var conn = DriverManager.getConnection(url, user, password); var sql = conn.createStatement(); var rows = sql.executeQuery("SELECT @@datadir"))
		{
			require(rows.next() && Path.of(rows.getString(1)).toRealPath().equals(scratch.resolve("data").toRealPath()), "The native fixture must use the scratch datadir.");
		}
		DatabaseConfig.load(config.toString());
		require(url.equals(DatabaseConfig.DATABASE_URL) && user.equals(DatabaseConfig.DATABASE_LOGIN) && password.equals(DatabaseConfig.DATABASE_PASSWORD),
			"Native pool must use the already validated isolated credentials.");
		ServerConfig.DATAPACK_ROOT = datapack.toRealPath().toFile();
		PlayerConfig.load();
		FeatureConfig.load();
		DatabaseFactory.initFromConfig(config.toString());
		final Payment payment = new Payment(21002102, 21002103, 21002104, 88, 21002105, 3_000_000, 120_000, new UUID(21, 21002102), 1);
		try
		{
			try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
			{
				sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002102,21002103)");
				sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002102,'clan.organization',2,X'01')");
				sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002104,'ClanProof021',1,21002103)");
				sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002103,'ClanProof021',120000,88,88,21002104)");
			}
			final Item adena = new Item(payment.itemObjectId(), Inventory.ADENA_ID);
			adena.setOwnerId(payment.actorId());
			adena.setItemLocation(ItemLocation.INVENTORY, 0, false);
			adena.setCount(payment.itemBefore());
			final long reserve = payment.itemAfter();
			for (boolean mayCommit : new boolean[] { true, false })
			{
				final long protectedAdena = mayCommit ? reserve + 1 : reserve;
				try
				{
					adena.transactRecoverableAdenaDebit(payment.requirement().adena(), connection ->
						ClanFoundingDevelopment.stage(connection, payment, credit -> false, protectedAdena, () -> mayCommit));
					throw new AssertionError("An unaffordable or stale clan payment was committed.");
				}
				catch (SQLException expected)
				{
					final String reason = mayCommit ? "Native clan payment would spend the personal Adena reserve."
						: "Native clan payment authority changed before commit.";
					require(reason.equals(expected.getMessage()), "Unexpected refusal: " + expected.getMessage());
				}
				require(adena.getCount() == payment.itemBefore() && !adena.hasPendingAdenaDebit(), "Failed native debit left a memory effect.");
				try (var conn = DatabaseFactory.getConnection())
				{
					assertRows(conn, payment.itemBefore(), payment.spBefore(), 1);
				}
			}
			adena.transactRecoverableInventoryDebit(payment.actorId(), payment.itemBefore(), payment.feeCount(),
				conn -> ClanFoundingDevelopment.stage(conn, payment, credit -> false, reserve, () -> true));
			require(adena.getCount() == payment.itemAfter() && !adena.hasPendingAdenaDebit(), "Committed native debit was not published.");
			try (var conn = DriverManager.getConnection(url, user, password))
			{
				conn.setAutoCommit(false);
				assertRows(conn, payment.itemAfter(), payment.spAfter(), 2);
				require(ClanFoundingDevelopment.readback(conn, payment, payment.itemAfter(), payment.spBefore()) == DurableOutcome.APPLIED,
					"Reconnect lost the exact receipt, actor, level, Adena or SP.");
				conn.rollback();
			}
			verifyNativeItemFees();
			verifyNativeShopPurchase();
			verifyNativeReputationLevels();
			verifyNativePledgeSkill();
			ClanWarehouse021Checks.verify();
		}
		finally
		{
			DatabaseFactory.close();
		}
		System.out.println("PASS 021 native Item debit with stock DDL, Adena reserve, stale authority rollback and cold SQL readback");
	}

	private static void verifyNativeReputationLevels() throws Exception
	{
		final long profile = 21002132;
		final int actor = 21002133;
		final int clan = 21002134;
		final int durableReputation = 100_000;
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002132,21002133)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002132,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id,reputation_score) VALUES(21002134,'ClanRepProof021',5,21002133,100000)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002133,'ClanRepProof021',450,88,88,21002134)");
		}
		final var requirement = Clan.levelRequirement(5);
		require(requirement.reputation() > 0 && requirement.members() <= 1, "Expected stock Bartz level-6 requirements for the isolated fixture.");
		final var first = new Payment(profile, actor, clan, 88, 0, 0, 0, new UUID(21, 21002102), requirement, durableReputation + 12);
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			require(ClanFoundingDevelopment.stageReputation(connection, first, durableReputation) == durableReputation, "Unsaved native reputation credit was not witnessed.");
			connection.rollback();
		}
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			require(ClanFoundingDevelopment.readReputation(connection, first, 0, durableReputation) == DurableOutcome.ROLLED_BACK, "Reputation rollback left an effect.");
			ClanFoundingDevelopment.stageReputation(connection, first, durableReputation);
			connection.commit();
		}
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			require(ClanFoundingDevelopment.readReputation(connection, first, 0, durableReputation) == DurableOutcome.APPLIED, "Exact level-6 receipt did not survive reconnect.");
			connection.rollback();
		}
		final var settlement = new ClanFoundingDevelopment.PendingPayment(new Clan(clan, "ClanRepProof021"), null, first, 0, first.reputationAfter());
		final int afterLateCredit = first.reputationAfter() + 19;
		require(ClanFoundingDevelopment.saveLateReputation(settlement, afterLateCredit, true) && ClanFoundingDevelopment.saveLateReputation(settlement, afterLateCredit, true),
			"Late reputation could not be saved exactly once after deciding the payment outcome.");
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement();
			var row = query.executeQuery("SELECT reputation_score,clan_level FROM clan_data WHERE clan_id=21002134"))
		{
			require(row.next() && row.getInt(1) == afterLateCredit && row.getInt(2) == 6, "Late reputation was not durable before publishing the resolved native level.");
		}
		for (int level = 6; level <= 9; level++)
		{
			final var fee = Clan.levelRequirement(level);
			require(fee.reputation() > 0 && fee.members() <= 1, "Expected stock mixed-fee requirements for the isolated fixture.");
			try (var connection = DatabaseFactory.getConnection(); var reset = connection.prepareStatement("UPDATE clan_data SET clan_level=?, reputation_score=? WHERE clan_id=?"))
			{
				reset.setInt(1, level);
				reset.setInt(2, durableReputation);
				reset.setInt(3, clan);
				require(reset.executeUpdate() == 1, "Could not prepare the next isolated reputation fixture.");
			}
			final long before = fee.itemCount() + (level == 8 ? 1 : 0);
			final int object = fee.itemId() == 0 ? 0 : 21002135 + level;
			final var item = object == 0 ? null : plainInventoryItem(object, fee.itemId(), actor, before);
			final var payment = new Payment(profile, actor, clan, 88, object, before, 0, new UUID(21, level), fee, durableReputation);
			if (item == null)
			{
				try (var connection = DatabaseFactory.getConnection())
				{
					connection.setAutoCommit(false);
					ClanFoundingDevelopment.stageReputation(connection, payment, durableReputation);
					connection.commit();
				}
			}
			else
			{
				item.updateDatabase(true);
				item.transactRecoverableInventoryDebit(actor, before, fee.itemCount(), connection ->
				{
					ClanFoundingDevelopment.stageReputation(connection, payment, durableReputation);
					return null;
				});
			}
			try (var connection = DatabaseFactory.getConnection())
			{
				connection.setAutoCommit(false);
				require(ClanFoundingDevelopment.readReputation(connection, payment, item == null ? 0 : item.getCount(), durableReputation)
					== DurableOutcome.APPLIED, "The high-level fee and reputation did not settle together.");
				if (item != null)
				{
					try (var query = connection.prepareStatement("SELECT count,owner_id,item_id,loc FROM items WHERE object_id=?"))
					{
						query.setInt(1, object);
						try (var row = query.executeQuery())
						{
							require(payment.itemAfter() == 0 ? !row.next() : row.next() && row.getLong(1) == payment.itemAfter()
								&& row.getInt(2) == actor && row.getInt(3) == fee.itemId() && "INVENTORY".equals(row.getString(4)), "The exact mixed fee did not persist.");
						}
					}
				}
				connection.rollback();
			}
		}
		final var territorial = new Payment(profile, actor, clan, 88, 0, 0, 0, new UUID(21, 11), Clan.levelRequirement(10), durableReputation, 1);
		final int levelTenReputation = durableReputation - Clan.levelRequirement(9).reputation();
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				ClanFoundingDevelopment.stageReputation(connection, territorial, levelTenReputation);
				throw new AssertionError("A stale territory cache authorized the final level.");
			}
			catch (SQLException stale)
			{
				require("Native territory ownership changed.".equals(stale.getMessage()), "Final level rejection did not use the durable territory witness.");
			}
			connection.rollback();
			try (var setup = connection.createStatement())
			{
				require(setup.executeUpdate("UPDATE clan_data SET hasCastle=1 WHERE clan_id=21002134") == 1, "Could not prepare the isolated territory witness.");
			}
			ClanFoundingDevelopment.stageReputation(connection, territorial, levelTenReputation);
			connection.commit();
			require(ClanFoundingDevelopment.readReputation(connection, territorial, 0, levelTenReputation) == DurableOutcome.APPLIED,
				"Final level did not commit with the exact isolated SQL territory witness.");
			connection.rollback();
		}
		final var nativeClan = new Clan(21002139, "ClanRepNative021");
		nativeClan.addReputationScore(90_000_000);
		synchronized (nativeClan)
		{
			nativeClan.beginReputationResolution(90_000_000, 89_990_000);
		}
		nativeClan.addReputationScore(10_005_000);
		nativeClan.takeReputationScore(100);
		synchronized (nativeClan)
		{
			nativeClan.resolveReputationPayment(false, 90_000_000);
		}
		require(nativeClan.getReputationScore() == 99_999_900, "Rollback lost late native reputation at the stock cap.");
		System.out.println("PASS 021 reputation-only and mixed native fees, exact rollback and late credit");
	}

	private static void verifyNativePledgeSkill() throws Exception
	{
		final int actor = 21002143;
		final int clan = 21002144;
		final int object = 21002145;
		final var offer = ClanFoundingDevelopment.pledgeTerms(5, SkillTreeData.getInstance().getPledgeSkill(370, 1));
		require(offer != null && offer.itemId() == 9816 && offer.itemCount() == 10 && offer.reputation() == 1_500,
			"Expected a stock Clan Body rank with the active Life Crystal fee.");
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002142,21002143)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002142,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id,reputation_score) VALUES(21002144,'ClanSkillProof021',5,21002143,5000)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002143,'ClanSkillProof021',450,88,88,21002144)");
		}
		final var payment = new Payment(21002142, actor, clan, 88, object, 11, new UUID(21, 370), offer, 5_000);
		final var item = plainInventoryItem(object, offer.itemId(), actor, payment.itemBefore());
		item.updateDatabase(true);
		final var insufficientReputation = new Payment(21002142, actor, clan, 88, object, 11, new UUID(21, 372), offer, 1_499);
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				ClanFoundingDevelopment.stageReputation(connection, insufficientReputation, 5_000);
				throw new AssertionError("A pledge payment was staged with insufficient reputation.");
			}
			catch (SQLException expected)
			{
				require("Invalid native reputation payment.".equals(expected.getMessage()), "Insufficient reputation was rejected for the wrong reason.");
			}
			connection.rollback();
		}
		try
		{
			item.transactRecoverableInventoryDebit(actor, payment.itemBefore(), payment.feeCount(), connection ->
			{
				ClanFoundingDevelopment.stageReputation(connection, payment, 5_000);
				throw new SQLException("TEST pledge abort");
			});
			throw new AssertionError("Pledge rollback fixture committed.");
		}
		catch (SQLException expected)
		{
			require("TEST pledge abort".equals(expected.getMessage()), "Pledge fixture did not reach its deliberate abort.");
		}
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			require(item.getCount() == 11 && ClanFoundingDevelopment.readReputation(connection, payment, 11, 5_000) == DurableOutcome.ROLLED_BACK,
				"Rolled back skill left a rank, debit or receipt.");
			connection.rollback();
		}
		item.transactRecoverableInventoryDebit(actor, payment.itemBefore(), payment.feeCount(), connection ->
		{
			ClanFoundingDevelopment.stageReputation(connection, payment, 5_000);
			return null;
		});
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try (var query = connection.prepareStatement("SELECT count,owner_id,item_id,loc FROM items WHERE object_id=?"))
			{
				query.setInt(1, object);
				try (var row = query.executeQuery())
				{
					require(row.next() && row.getLong(1) == 1 && row.getInt(2) == actor && row.getInt(3) == offer.itemId() && "INVENTORY".equals(row.getString(4))
						&& ClanFoundingDevelopment.readReputation(connection, payment, row.getLong(1), 5_000) == DurableOutcome.APPLIED,
						"Pledge transaction did not leave the exact rank, reputation, item and receipt.");
				}
			}
			connection.rollback();
			final var duplicate = new Payment(21002142, actor, clan, 88, object, 11, new UUID(21, 371), offer, 3_500);
			try
			{
				ClanFoundingDevelopment.stageReputation(connection, duplicate, 3_500);
				throw new AssertionError("An existing clan rank was charged again.");
			}
			catch (SQLException expected)
			{
				require("Native clan skill rank changed.".equals(expected.getMessage()), "Duplicate rank was rejected for the wrong reason.");
			}
			connection.rollback();
		}
		final var restored = new Clan(clan);
		final var skill = restored.getSkills().get(offer.skillId());
		require(restored.getLevel() == 5 && restored.getLeaderId() == actor && restored.getReputationScore() == 3_500
			&& skill != null && skill.getLevel() == offer.rank() && Clan.minimumOrdinarySkillPledgeClass(skill) > 0
			&& SkillTreeData.getInstance().getAvailablePledgeSkills(restored).stream().noneMatch(next -> next.getSkillId() == offer.skillId()),
			"Native cold restore lost the skill, requirements, rank restriction or clan.");
		final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
		constructor.setAccessible(true);
		final var member = constructor.newInstance(actor, PlayerTemplateData.getInstance().getTemplate(0), "test021",
			new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		try
		{
			restored.getClanMember(actor).setPlayer(member);
			member.setClan(restored);
			final int minimumClass = Clan.minimumOrdinarySkillPledgeClass(skill);
			member.setPledgeClass(minimumClass - 1);
			restored.addSkillEffects(member);
			require(member.getSkillLevel(offer.skillId()) < 1, "Native login applied the skill below its pledge class.");
			member.setPledgeClass(minimumClass);
			restored.addSkillEffects(member);
			require(member.getSkillLevel(offer.skillId()) == offer.rank(), "Native login did not apply the paid clan skill.");
			restored.removeSkillEffects(member);
			require(member.getSkillLevel(offer.skillId()) < 1, "Native membership loss did not revoke the skill.");
		}
		finally
		{
			member.stopAllTasks();
		}
		System.out.println("PASS 021 native ordinary PLEDGE, rollback, cold clan restore, member eligibility and revocation");
	}

	private static void verifyNativeShopPurchase() throws Exception
	{
		final var stock = MultisellData.getInstance();
		require(stock.matchesPersonalProgressionOffer(1419, 10_000_000) && stock.matchesPersonalProgressionOffer(3874, 25_000_000)
			&& stock.matchesPersonalProgressionOffer(3870, 50_000_000) && stock.matchesPersonalProgressionOffer(9910, 150, 75_000_000)
			&& stock.matchesPersonalProgressionOffer(9911, 5, 100_000_000) && !stock.matchesPersonalProgressionOffer(3874, 24_999_999)
			&& !stock.matchesPersonalProgressionOffer(9910, 149, 75_000_000),
			"The active native 91002 list does not match the curated stock quotes.");
		final int actor = 21002123;
		final int adenaId = 21002125;
		final int productId = 21002126;
		final var purchase = new ClanDevelopmentShop.Purchase(21002122, actor, 21002124, 3, adenaId, 30_000_000, productId, 3874, 25_000_000, new UUID(21, 21002102));
		try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002122,21002123)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002122,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002124,'ClanShopProof021',3,21002123)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002123,'ClanShopProof021',1000000,88,88,21002124)");
		}
		final var adena = plainInventoryItem(adenaId, Inventory.ADENA_ID, actor, purchase.adenaBefore());
		final var product = plainInventoryItem(productId, purchase.itemId(), actor, 1);
		final Item.CountTransaction<Void> shopAuthority = conn -> ClanDevelopmentShop.lockAuthority(conn, purchase, List.of());
		adena.updateDatabase(true);
		try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
		{
			sql.executeUpdate("UPDATE items SET count=29999999 WHERE object_id=21002125 AND owner_id=21002123");
		}
		adena.setCount(purchase.adenaBefore() - 1);
		adena.setCount(purchase.adenaBefore());
		try
		{
			adena.transactExactRecoverableAdenaDebit(actor, purchase.adenaBefore(), purchase.price(), shopAuthority,
				connection -> { throw new AssertionError("Conflicting Adena reached purchase staging."); });
			throw new AssertionError("Conflicting Adena was accepted.");
		}
		catch (IllegalStateException expected)
		{
			require("Native currency preimage differs.".equals(expected.getMessage()), "The exact Adena preimage check did not reject the dirty stack.");
		}
		try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
		{
			require(shopAdena(conn) == purchase.adenaBefore() - 1, "The payment overwrote the foreign durable Adena preimage.");
			sql.executeUpdate("UPDATE items SET count=30000000 WHERE object_id=21002125 AND owner_id=21002123");
		}
		try
		{
			adena.transactExactRecoverableAdenaDebit(actor, purchase.adenaBefore(), purchase.price(), shopAuthority, connection ->
			{
				ClanDevelopmentShop.stage(connection, purchase, product);
				throw new SQLException("021 shop failure before commit");
			});
			throw new AssertionError("Shop rollback control did not fail.");
		}
		catch (SQLException expected)
		{
			require("021 shop failure before commit".equals(expected.getMessage()), "Shop mutation never reached its staged effects.");
		}
		try (var conn = DatabaseFactory.getConnection())
		{
			conn.setAutoCommit(false);
			final long durableAdena = shopAdena(conn);
			require(durableAdena == purchase.adenaBefore() && ClanDevelopmentShop.readback(conn, purchase, durableAdena) == DurableOutcome.ROLLED_BACK, "Uncommitted purchase changed Adena or left a product or receipt.");
			conn.rollback();
		}
		try
		{
			adena.transactExactRecoverableAdenaDebit(actor, purchase.adenaBefore(), purchase.price(), shopAuthority, connection ->
			{
				ClanDevelopmentShop.stage(connection, purchase, product);
				// The server commits, then the writer's commit call encounters a closed connection.
				connection.commit();
				connection.close();
				return null;
			});
			throw new AssertionError("The missing acknowledgement control did not fail.");
		}
		catch (SQLException expected)
		{
			require(adena.hasPendingAdenaDebit(), "A durable commit without acknowledgement did not retain its native guard.");
		}
		adena.changeCount(null, 17, null, null);
		final var recovered = adena.reconcileCountTransaction(actor, purchase.adenaBefore(), purchase.adenaAfter(),
			(conn, durable) -> ClanDevelopmentShop.readback(conn, purchase, durable) == DurableOutcome.APPLIED ? DurableOutcome.APPLIED : null);
		require(recovered == DurableOutcome.APPLIED && !adena.hasPendingAdenaDebit() && adena.getCount() == purchase.adenaAfter() + 17,
			"Native readback did not reconcile the committed purchase and retain later earned Adena.");
		try (var conn = DatabaseFactory.getConnection())
		{
			conn.setAutoCommit(false);
			final long durableAdena = shopAdena(conn);
			require(durableAdena == purchase.adenaAfter() && ClanDevelopmentShop.readback(conn, purchase, durableAdena) == DurableOutcome.APPLIED, "Committed Adena debit, product or exact receipt missing.");
			final var foreign = new ClanDevelopmentShop.Purchase(21002122, actor, 21002124, 3, adenaId, purchase.adenaBefore(), productId, 3874, 25_000_000, new UUID(21, 21002103));
			require(ClanDevelopmentShop.readback(conn, foreign, purchase.adenaAfter()) == DurableOutcome.CONFLICT, "Another purchase accepted a foreign receipt.");
			conn.rollback();
		}
		System.out.println("PASS 021 native clan shop quotes, exact Adena, rollback, missing acknowledgement, late credit and cold readback");
	}

	private static long shopAdena(Connection conn) throws SQLException
	{
		try (var query = conn.createStatement(); var rows = query.executeQuery("SELECT count FROM items WHERE object_id=21002125 AND owner_id=21002123 AND item_id=57 AND loc='INVENTORY'"))
		{
			require(rows.next(), "The exact TEST Adena row is absent.");
			return rows.getLong(1);
		}
	}

	private static Item plainInventoryItem(int objectId, int itemId, int ownerId, long count)
	{
		final var item = new Item(objectId, itemId);
		item.setOwnerId(ownerId);
		item.setItemLocation(ItemLocation.INVENTORY, 0, false);
		item.setCount(count);
		return item;
	}

	private static void verifyNativeItemFees() throws Exception
	{
		final long profileId = 21002112;
		final int actorId = 21002113;
		final int clanId = 21002114;
		try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002112,21002113)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002112,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002114,'ClanItemProof021',2,21002113)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002113,'ClanItemProof021',0,88,88,21002114)");
		}
		for (int level = 2; level <= 4; level++)
		{
			final var fee = Clan.levelRequirement(level);
			final long before = level == 2 ? 2 : 1;
			final long spBefore = fee.sp() + 15;
			final int objectId = 21002115 + level;
			final var payment = new Payment(profileId, actorId, clanId, 88, objectId, before, spBefore, new UUID(21002102, level), level);
			final Item item = new Item(objectId, fee.itemId());
			item.setOwnerId(actorId);
			item.setItemLocation(ItemLocation.INVENTORY, 0, false);
			item.setCount(before);
			item.updateDatabase(true);
			try (var conn = DatabaseFactory.getConnection(); var sql = conn.prepareStatement("UPDATE characters SET sp=? WHERE charId=?"))
			{
				sql.setLong(1, spBefore);
				sql.setInt(2, actorId);
				require(sql.executeUpdate() == 1, "Item fee fixture lacks an SP owner.");
			}
			if (level == 3)
			{
				checkForeignFeeRow(item, before, actorId);
				try
				{
					item.transactRecoverableInventoryDebit(actorId, before, fee.itemCount(), conn ->
					{
						ClanFoundingDevelopment.stage(conn, payment, credit -> false);
						throw new SQLException("021 controlled item fee rollback");
					});
					throw new AssertionError("Item fee did not roll back.");
				}
				catch (SQLException failure)
				{
					require("021 controlled item fee rollback".equals(failure.getMessage()), "Item fee never reached native staging.");
				}
				require(item.getCount() == before && !item.isCommittedInventoryRemoval(), "Rollback retired the item from inventory.");
				assertItemFeeRows(payment, before, spBefore, level, DurableOutcome.ROLLED_BACK);
			}
			item.transactRecoverableInventoryDebit(actorId, before, fee.itemCount(), conn -> ClanFoundingDevelopment.stage(conn, payment, credit -> false));
			final long left = before - fee.itemCount();
			require(item.getCount() == left && item.isCommittedInventoryRemoval() == (left == 0), "Committed item count or removal did not publish.");
			if (left == 0)
			{
				require(!item.tryCreditContainerStack(null, 1, null, null, () -> true) && item.getCount() == 0,
					"A stale container credit resurrected the fully consumed object.");
				verifySurvivingNativeStacks(item);
			}
			assertItemFeeRows(payment, left, 15, level + 1, DurableOutcome.APPLIED);
		}
		System.out.println("PASS 021 native L2-to-L5 items, partial stack, full removal, rollback and exact receipts");
	}

	private static void verifySurvivingNativeStacks(Item retired)
	{
		final int itemId = retired.getId();
		require(ClanFoundingDevelopment.isItemFee(itemId) && !ClanFoundingDevelopment.isItemFee(Inventory.ADENA_ID),
			"The inventory restore rule must retain the exact fee stacks.");
		final var original = new Item(retired.getObjectId() + 200, itemId);
		final var earned = new Item(retired.getObjectId() + 201, itemId);
		earned.setCount(4);
		final var container = new FeeItemContainer(retired, original, earned);
		require(container.getItemByItemId(itemId) != retired && container.getInventoryItemCount(itemId, -1) == 5,
			"Stock lookups exposed a retired object or undercounted surviving stacks.");
		final var credited = container.addItem(null, itemId, 2, null, null);
		require((credited == original || credited == earned) && container.getSize() == 3
			&& container.getInventoryItemCount(itemId, -1) == 7, "Credit must reuse an available owned stack.");
	}

	private static final class FeeItemContainer extends ItemContainer
	{
		private FeeItemContainer(Item... items)
		{
			for (Item item : items) { addItem(item); }
		}

		@Override
		protected Creature getOwner() { return null; }

		@Override
		protected ItemLocation getBaseLocation() { return ItemLocation.INVENTORY; }
	}

	private static void checkForeignFeeRow(Item item, long expectedCount, int actorId) throws Exception
	{
		final int foreignOwner = actorId + 1;
		item.setCount(expectedCount + 1);
		item.setCount(expectedCount);
		try (var conn = DatabaseFactory.getConnection(); var row = conn.prepareStatement("UPDATE items SET owner_id=? WHERE object_id=?"))
		{
			row.setInt(1, foreignOwner);
			row.setInt(2, item.getObjectId());
			require(row.executeUpdate() == 1, "Fixture item was not durable.");
			try
			{
				item.transactRecoverableInventoryDebit(actorId, expectedCount, expectedCount, tx ->
				{
					throw new AssertionError("Foreign fee row was overwritten before exact validation.");
				});
				throw new AssertionError("The native item guard did not refuse a foreign row.");
			}
			catch (IllegalStateException expected)
			{
				require("Native currency preimage differs.".equals(expected.getMessage()), "The fee failed outside the exact row guard.");
			}
			try (var query = conn.prepareStatement("SELECT owner_id,count FROM items WHERE object_id=?"))
			{
				query.setInt(1, item.getObjectId());
				try (var rows = query.executeQuery())
				{
					require(rows.next() && rows.getInt(1) == foreignOwner && rows.getLong(2) == expectedCount,
						"Rejected fee changed the durable preimage.");
				}
			}
			row.setInt(1, actorId);
			require(row.executeUpdate() == 1, "Could not restore the TEST fee fixture preimage.");
		}
	}

	private static void assertItemFeeRows(Payment payment, long count, long sp, int level, DurableOutcome outcome) throws SQLException
	{
		try (var conn = DatabaseFactory.getConnection();
			var query = conn.prepareStatement("SELECT i.count,i.owner_id,i.item_id,i.loc,c.sp,d.clan_level FROM characters c JOIN clan_data d ON d.clan_id=c.clanid LEFT JOIN items i ON i.object_id=? WHERE c.charId=?"))
		{
			conn.setAutoCommit(false);
			query.setInt(1, payment.itemObjectId());
			query.setInt(2, payment.actorId());
			try (var rows = query.executeQuery())
			{
				require(rows.next() && rows.getLong(5) == sp && rows.getInt(6) == level, "Clan level or SP diverged from the item debit.");
				final long durableCount = rows.getLong(1);
				require(count == 0 ? rows.wasNull() : durableCount == count && rows.getInt(2) == payment.actorId()
					&& rows.getInt(3) == payment.requirement().itemId() && "INVENTORY".equals(rows.getString(4)), "Exact item fee row diverged.");
			}
			require(ClanFoundingDevelopment.readback(conn, payment, count, payment.spBefore()) == outcome, "Item fee receipt did not reconcile.");
			conn.rollback();
		}
	}

	private static void verifySecondLevel(Connection connection, Payment payment) throws SQLException
	{
		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE items SET count=3000000 WHERE object_id=37");
			sql.executeUpdate("UPDATE characters SET sp=120000 WHERE charId=11");
			connection.commit();
		}
		final Payment insufficient = new Payment(7, 11, 29, 88, 37, 3_000_000, 99_999, new UUID(21, 1), 1);
		requireRefused(connection, insufficient, "Native clan payment preimage changed.");

		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE clan_data SET dissolving_expiry_time=50 WHERE clan_id=29");
			connection.commit();
		}
		requireRefused(connection, payment, "Native clan leadership or level changed.");
		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE clan_data SET dissolving_expiry_time=0 WHERE clan_id=29");
			connection.commit();
		}

		debitFixtureCoins(connection, payment);
		ClanFoundingDevelopment.stage(connection, payment, credit -> false);
		connection.rollback();
		assertRows(connection, payment.itemBefore(), payment.spBefore(), 1);
		require(ClanFoundingDevelopment.readback(connection, payment, payment.itemBefore(), payment.spBefore()) == DurableOutcome.ROLLED_BACK, "L2 rollback was not recognized over the previous-version receipt.");

		debitFixtureCoins(connection, payment);
		ClanFoundingDevelopment.stage(connection, payment, credit -> false);
		connection.commit();
		assertRows(connection, payment.itemAfter(), payment.spAfter(), 2);
		require(ClanFoundingDevelopment.readback(connection, payment, payment.itemAfter(), payment.spBefore()) == DurableOutcome.APPLIED, "L2 lost-ACK recovery did not confirm the exact effect.");
		requireRefused(connection, payment, "Native clan payment preimage changed.");
		assertRows(connection, payment.itemAfter(), payment.spAfter(), 2);
		System.out.println("PASS 021 L1-to-L2 stock fees, legacy receipt, refusal, rollback and exact recovery");
	}

	private static void requireRefused(Connection connection, Payment payment, String reason) throws SQLException
	{
		try
		{
			ClanFoundingDevelopment.stage(connection, payment, credit -> false);
			throw new AssertionError("Invalid payment was accepted.");
		}
		catch (SQLException expected)
		{
			require(reason.equals(expected.getMessage()), "Unexpected refusal: " + expected.getMessage());
		}
		finally
		{
			connection.rollback();
		}
	}

	private static void verifyPaidStaging(Connection connection, Payment other) throws SQLException
	{
		try (var sql = connection.createStatement())
		{
			sql.execute("CREATE TEMPORARY TABLE clan_data (clan_id INT PRIMARY KEY, clan_level INT, leader_id INT, dissolving_expiry_time BIGINT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE characters (charId INT PRIMARY KEY, sp BIGINT, classid INT, base_class INT, clanid INT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE items (object_id INT PRIMARY KEY, count BIGINT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE phantom_profiles (profile_id BIGINT PRIMARY KEY, character_object_id INT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE phantom_profile_components (profile_id BIGINT, component_type VARCHAR(64), component_schema_version SMALLINT, payload VARBINARY(4096), row_version BIGINT DEFAULT 0, PRIMARY KEY (profile_id,component_type)) ENGINE=InnoDB");
			sql.executeUpdate("INSERT INTO clan_data VALUES (29,0,11,0)");
			sql.executeUpdate("INSERT INTO characters VALUES (11,50000,88,88,29)");
			sql.executeUpdate("INSERT INTO items VALUES (37,1500000)");
			sql.executeUpdate("INSERT INTO phantom_profiles VALUES (7,11)");
			sql.executeUpdate("INSERT INTO phantom_profile_components VALUES (7,'clan.organization',1,X'01',0)");
		}
		connection.setAutoCommit(false);
		debitFixtureCoins(connection, PAYMENT);
		require(ClanFoundingDevelopment.stage(connection, PAYMENT, credit -> false) == BEFORE_SP, "Native SP preimage was lost.");
		connection.rollback();
		assertRows(connection, BEFORE_ADENA, BEFORE_SP, 0);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, BEFORE_ADENA, BEFORE_SP) == DurableOutcome.ROLLED_BACK, "Exact rolled-back preimage was not recognized.");

		debitFixtureCoins(connection, PAYMENT);
		ClanFoundingDevelopment.stage(connection, PAYMENT, credit -> false);
		connection.commit();
		assertRows(connection, PAYMENT.itemAfter(), PAYMENT.spAfter(), 1);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, PAYMENT.itemAfter(), BEFORE_SP) == DurableOutcome.APPLIED, "Committed receipt did not recover exact fees.");
		require(ClanFoundingDevelopment.readback(connection, other, PAYMENT.itemAfter(), BEFORE_SP) == DurableOutcome.CONFLICT, "Readback accepted a different operation's receipt.");
		final Payment replay = new Payment(7, 11, 29, 88, 37, PAYMENT.itemAfter(), PAYMENT.spAfter(), new UUID(17, 3));
		debitFixtureCoins(connection, replay);
		try
		{
			ClanFoundingDevelopment.stage(connection, replay, credit -> false);
			throw new AssertionError("Paid level accepted a second payment.");
		}
		catch (SQLException expected)
		{
			require("Native clan leadership or level changed.".equals(expected.getMessage()), "Replay did not reach the exact native clan level check.");
		}
		finally
		{
			connection.rollback();
		}
		assertRows(connection, PAYMENT.itemAfter(), PAYMENT.spAfter(), 1);
	}

	private static void debitFixtureCoins(Connection connection, Payment payment) throws SQLException
	{
		try (var sql = connection.prepareStatement("UPDATE items SET count=? WHERE object_id=? AND count=?"))
		{
			sql.setLong(1, payment.itemAfter());
			sql.setInt(2, payment.itemObjectId());
			sql.setLong(3, payment.itemBefore());
			require(sql.executeUpdate() == 1, "Native currency preimage was lost.");
		}
	}

	private static void assertRows(Connection connection, long adena, long sp, int level) throws SQLException
	{
		try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT i.count,c.sp,cl.clan_level FROM items i CROSS JOIN characters c CROSS JOIN clan_data cl"))
		{
			require(rows.next() && rows.getLong(1) == adena && rows.getLong(2) == sp && rows.getInt(3) == level, "Stock clan level and fees diverged.");
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
