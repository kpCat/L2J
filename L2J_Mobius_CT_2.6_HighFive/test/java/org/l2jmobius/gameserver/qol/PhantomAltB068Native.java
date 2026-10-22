/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.network.ReadableBuffer;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAltBPriceCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyXml;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Actual registered HomeBoard and socket-backed native packet after an externally approved own068 CLI Apply. */
public final class PhantomAltB068Native
{
	private record Selected(String path, int ordinal, long before, long after) { }
	public static void main(String[] args) throws Exception
	{
		if (args.length != 4 || !Boolean.getBoolean("phantom.task068.native") || !List.of("purchase", "rollback-check").contains(args[0])) { throw new IllegalArgumentException("Mode purchase|rollback-check, plan.json, Apply receipt and exact plan SHA256 required in own068."); }
		final boolean purchase = args[0].equals("purchase");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task068").toRealPath();
		final Path game = Path.of("").toRealPath();
		final Path planFile = PhantomEconomyXml.physical(task, Path.of(args[1])), receiptFile = PhantomEconomyXml.physical(task, Path.of(args[2]));
		final String hash = args[3].toLowerCase(java.util.Locale.ROOT);
		check(hash.matches("[0-9a-f]{64}") && PhantomEconomyXml.hash(planFile).equals(hash), "Approved Plan hash differs.");
		final String plan = Files.readString(planFile), apply = Files.readString(receiptFile);
		for (String document : List.of(plan, apply)) { check(property(document, "owner", "TASK068_LIVING_ECONOMY") && property(document, "gameRoot", game.toString()), "CLI owner/root differs from this exact native game."); }
		check(property(apply, "planHash", hash), "Apply receipt does not belong to this exact Plan.");
		for (String scale : List.of("adenaScale", "lootScale")) { check(Pattern.compile("\"" + scale + "\"\\s*:\\s*1(?:\\.0+)?(?=\\s*[,}])").matcher(plan).find(), "Illustrative rates cannot prove a native Apply."); }
		final Selected selected = select(PhantomEconomyXml.physical(task, planFile.getParent().resolve("facts/offers.tsv")), plan);
		final Path xml = PhantomEconomyXml.physical(game, game.resolve(selected.path()));
		final var receiptEntry = Pattern.compile("\\{\\s*\"path\"\\s*:\\s*\"" + Pattern.quote(selected.path()) + "\"\\s*,\\s*\"preHash\"\\s*:\\s*\"([0-9a-f]{64})\"\\s*,\\s*\"postHash\"\\s*:\\s*\"([0-9a-f]{64})\"").matcher(apply);
		check(receiptEntry.find() && PhantomEconomyXml.hash(xml).equals(receiptEntry.group(purchase ? 2 : 1)), "Selected native XML differs from the completed CLI Apply/Rollback image.");
		final long expected = purchase ? selected.after() : selected.before();
		final int listId = Integer.parseInt(xml.getFileName().toString().replace(".xml", ""));
		final StringBuilder report = new StringBuilder("stage\tvalue\n");
		final Path output = task.resolve("reports/altb068-" + args[0] + ".tsv");
		if (Files.exists(output)) { throw new IllegalArgumentException("Existing native proof is preserved; inspect it before another purchase attempt."); }
		Player player = null; QoLTestClient network = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(68006805L, module, output.getParent()));
			check(GeneralConfig.ENABLE_COMMUNITY_BOARD && CommunityBoardConfig.CUSTOM_CB_ENABLED && CommunityBoardConfig.COMMUNITYBOARD_ENABLE_MULTISELLS, "Enable the actual own068 Custom HomeBoard config before Plan; this helper does not override runtime admission.");
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); MultisellData.getInstance();
			final var catalog = PhantomAltBPriceCatalog.load(game);
			final var offer = catalog.offers().stream().filter(row -> row.path().equals(selected.path()) && row.ordinal() == selected.ordinal()).findFirst().orElseThrow();
			check(offer.supported() && offer.oldPrice() == expected, "Actual applied reachable offer price differs.");
			final String bypass = bypass(game, catalog, listId);
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			final var handler = CommunityBoardHandler.getInstance().getHandler(bypass);
			check(handler != null && handler.getClass().getSimpleName().equals("HomeBoard"), "Actual HomeBoard bypass is not registered.");
			player = player(expected, purchase); network = QoLTestClient.attach(player);
			check(player.getAccessLevel().getLevel() == 0 && !player.isGM() && !player.hasHeadlessOutboundSession() && player.getNativeWorkOwner() == null, "Ordinary access0 native packet owner required.");
			CommunityBoardHandler.getInstance().handleParseCommand("_bbstop", player);
			player.setMultiSell(null); CommunityBoardHandler.getInstance().handleParseCommand(bypass, player);
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (player.getMultiSell() == null && System.nanoTime() < deadline) { Thread.sleep(10); }
			final var prepared = player.getMultiSell(); check(prepared != null && prepared.getListId() == listId && !prepared.isPersonalPremiumQoL() && prepared.isNpcAllowed(-1), "Registered HomeBoard did not prepare the actual native CB multisell.");
			final var entry = prepared.getEntries().get(selected.ordinal() - 1);
			check(entry.getIngredients().size() == 1 && entry.getIngredients().getFirst().getItemId() == 57 && entry.getIngredients().getFirst().getItemCount() == expected, "Native PreparedEntry has the wrong applied Adena price.");
			final Map<Integer, Long> products = new java.util.TreeMap<>(); entry.getProducts().forEach(product -> products.merge(product.getItemId(), product.getItemCount(), Math::addExact));
			check(products.equals(offer.products()), "Native prepared products differ from the actual XML offer.");
			final var before = new HashMap<Integer, Long>(); for (int item : products.keySet()) { before.put(item, player.getInventory().getInventoryItemCount(item, -1)); }
			final long adena = player.getAdena();
			report.append("input\tplanSha=").append(hash).append(";xml=").append(selected.path()).append(";ordinal=").append(selected.ordinal()).append(";old=").append(selected.before()).append(";new=").append(selected.after()).append(";bypass=").append(bypass).append(";handler=").append(handler.getClass().getName()).append('\n');
			report.append("prepared\tlist=").append(listId).append(";entry=").append(entry.getEntryId()).append(";price=").append(expected).append(";products=").append(products).append(";transport=real-local-async-socket/GameClient/MultiSellChoose\n");
			if (purchase)
			{
				final var bytes = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN); bytes.putInt(listId); bytes.putInt(entry.getEntryId()); bytes.putLong(1); bytes.putShort((short) 0); bytes.putInt(0); bytes.putInt(0); for (int index = 0; index < 8; index++) { bytes.putShort((short) 0); } bytes.flip();
				final var packet = new MultiSellChoose(); packet.init(network.client(), ReadableBuffer.of(bytes)); check(packet.read(), "Actual MultiSellChoose did not parse."); packet.run();
				check(player.getAdena() == adena - expected, "Native packet did not debit the exact applied price.");
				for (var product : products.entrySet()) { check(player.getInventory().getInventoryItemCount(product.getKey(), -1) == before.get(product.getKey()) + product.getValue(), "Native packet did not credit exact products."); }
				player.getInventory().updateDatabase(); player.storeMe();
				report.append("purchase\tadena=").append(adena).append("->").append(player.getAdena()).append(";productDelta=").append(products).append(";source=finite TEST initial funds;native debit/credit conserved\n");
			}
			else { check(player.getAdena() == adena, "Rollback price inspection mutated funds."); report.append("rollback_check\toriginal native prepared price restored; no transaction/grant\n"); }
			report.append("result\tPASS\n");
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally
		{
			Files.createDirectories(output.getParent()); Files.writeString(output, report);
			if (network != null) { network.close(); }
			if (player != null) { player.stopAllTasks(); player.storeMe(); if (World.getInstance().getPlayer(player.getObjectId()) == player) { player.deleteMe(); } }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}
	private static Selected select(Path offers, String plan) throws Exception
	{
		for (String row : Files.readAllLines(offers).stream().skip(1).toList())
		{
			final String[] value = row.split("\t", -1);
			if (value.length != 9 || !value[1].equals("multisell") || !value[5].equals("PRICED") || value[3].equals(value[4])) { continue; }
			check(value[0].matches("data/multisell/(custom/)?[0-9]+\\.xml"), "Unsupported price path.");
			final var flat = Pattern.compile("\\{[^{}]*\\}").matcher(plan); boolean approved = false;
			while (flat.find()) { final String record = flat.group(); if (property(record, "path", value[0]) && property(record, "kind", value[1]) && property(record, "ordinal", value[2]) && property(record, "old_price", value[3]) && property(record, "new_price", value[4]) && property(record, "status", "PRICED")) { approved = true; break; } }
			check(approved, "Selected TSV offer is absent from the exact approved Plan.");
			final var selected = new Selected(value[0], Integer.parseInt(value[2]), Long.parseLong(value[3]), Long.parseLong(value[4])); check(selected.ordinal() > 0 && selected.before() > 0 && selected.after() > 0, "Positive actual price row required."); return selected;
		}
		throw new IllegalArgumentException("Plan has no supported changed reachable multisell offer.");
	}
	private static String bypass(Path game, PhantomAltBPriceCatalog catalog, int listId) throws Exception
	{
		final Pattern pattern = Pattern.compile("_bbsmultisell;" + listId + ",[A-Za-z0-9_/-]+");
		for (String path : catalog.hashes().keySet().stream().filter(value -> value.startsWith("data/html/CommunityBoard/Custom/")).sorted().toList()) { final var found = pattern.matcher(Files.readString(PhantomEconomyXml.physical(game, game.resolve(path)))); if (found.find()) { return found.group(); } }
		throw new IllegalArgumentException("Applied list has no actual reachable HomeBoard bypass.");
	}
	private static boolean property(String json, String name, String value)
	{
		final String quoted = '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
		return Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*" + Pattern.quote(quoted)).matcher(json).find();
	}
	private static Player player(long price, boolean purchase) throws Exception
	{
		Integer existing = null;
		try (var connection = DatabaseFactory.getConnection())
		{
			try (var insert = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task068altb','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
			try (var query = connection.prepareStatement("SELECT accessLevel FROM accounts WHERE login='task068altb'")) { try (var rows = query.executeQuery()) { check(rows.next() && rows.getInt(1) == -1 && !rows.next(), "Native Alt+B fixture account is not explicitly disabled."); } }
			try (var query = connection.prepareStatement("SELECT c.charId,c.accesslevel,a.accessLevel FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.account_name='task068altb' AND c.char_name='AltB068Buyer'")) { try (var rows = query.executeQuery()) { if (rows.next()) { existing = rows.getInt(1); check(rows.getInt(2) == 0 && rows.getInt(3) == -1 && !rows.next(), "Existing native Alt+B fixture authority differs."); } } }
		}
		check(existing != null || purchase, "Rollback inspection cannot create/fund a new fixture.");
		final var type = java.util.Arrays.stream(PlayerClass.values()).filter(value -> value.level() == 0 && PlayerTemplateData.getInstance().getTemplate(value) != null).min(Comparator.comparingInt(PlayerClass::getId)).orElseThrow();
		final var player = existing == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(type), "task068altb", "AltB068Buyer", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(existing);
		check(player != null && player.getClient() == null && player.getNativeWorkOwner() == null && !player.hasHeadlessOutboundSession(), "Native fixture has another session/owner.");
		player.stopAllTasks();
		// Native fixture preparation precedes measurement; a persisted zero-HP character loads dead.
		if (player.isDead()) { player.doRevive(); }
		player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		if (existing == null) { check(price <= PlayerConfig.MAX_ADENA, "Finite TEST funding exceeds MaxAdena."); player.addAdena(ItemProcessType.REWARD, price, player, false); player.getInventory().updateDatabase(); player.storeMe(); }
		check(!purchase || player.getAdena() >= price, "Retained TEST funds are insufficient; no automatic topup/replay.");
		player.setOnlineStatus(true, false); player.spawnMe(81700, 148500, -3470); return player;
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
