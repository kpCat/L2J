/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.*;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.managers.*;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.economy.*;
import org.l2jmobius.gameserver.phantoms.knowledge.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomClassSkillLearningTransaction;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** One finite, premeasurement profession preparation; never an ordinary runtime entry point. */
public final class PhantomRelease068ManufacturePreparation
{
	private static final String OWNER = "TASK068_LIVING_ECONOMY", SCHEMA = "l2jmobiush5_phantom_release_068";
	private static final String COMPONENT = "merchant.profession.prepare.v1", ACCOUNT = "phm068profession", NAME = "Craft068Maker";
	private static final int CLASS = 53, LEVEL = 5, SKILL = 172, SP = 310, RECIPE = 1, SCROLL = 1666;
	private PhantomRelease068ManufacturePreparation() { }
	public static void main(String[] args) throws Exception
	{
		final boolean resume = args.length == 7 && "resume-unlearned-skill".equals(args[3]);
		check(args.length == 3 || resume, "Require root currentCodeSHA currentJarSHA [resume-unlearned-skill actorId originalPendingCodeSHA originalPendingJarSHA].");
		final int resumeId = resume ? Integer.parseInt(args[4]) : 0;
		check(!resume || resumeId > 0 && args[5].matches("[0-9a-f]{40}") && args[6].matches("[0-9a-fA-F]{64}"), "Exact addressed actor and original PENDING hashes required.");
		final Path runtime = validateRelease(args[0], args[1], args[2]);
		Player player = null;
		final Path lockPath = runtime.resolve("local-play/operator.lock");
		plain(lockPath.getParent()); if (Files.exists(lockPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { plain(lockPath); }
		try (var channel = java.nio.channels.FileChannel.open(lockPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE); var lease = channel.tryLock())
		{
			check(lease != null, "Another own operator/preparation holds this runtime.");
			bootstrap(runtime);
			check(OfflineTradeConfig.OFFLINE_TRADE_ENABLE && OfflineTradeConfig.OFFLINE_CRAFT_ENABLE && OfflineTradeConfig.RESTORE_OFFLINERS, "Own profile must enable stock offline manufacture and restore.");
			final var policy = PhantomMerchantPolicy.load(Path.of(PhantomMerchantPolicy.FILE));
			check(policy != null, "Explicit own merchant policy required.");
			final var profiles = PhantomProfileRepository.open();
			final var servers = profiles.listManagedAfter(PhantomMerchantBootstrap.COMPONENT, 0, 2);
			check(servers.size() <= 1, "Ambiguous merchant ledger.");
			PhantomProfileComponent previous = null;
			if (!servers.isEmpty())
			{
				previous = profiles.findComponent(servers.getFirst().profile().profileId(), COMPONENT).orElse(null);
				if (previous != null)
				{
					final var receipt = properties(previous.payload());
					if ("READY".equals(receipt.getProperty("stage")))
					{
						check(args[1].equals(receipt.getProperty("continuationCodeSHA", receipt.getProperty("codeSHA"))) && args[2].equalsIgnoreCase(receipt.getProperty("continuationGameJarSHA256", receipt.getProperty("gameJarSHA256"))), "Existing preparation provenance differs.");
						check(!resume || resumeId == Integer.parseInt(receipt.getProperty("characterId")) && args[5].equals(receipt.getProperty("codeSHA")) && args[6].equalsIgnoreCase(receipt.getProperty("gameJarSHA256")), "Addressed READY provenance differs.");
						verifyDurable(Integer.parseInt(receipt.getProperty("characterId")), Long.parseLong(receipt.getProperty("fee")));
						System.out.println("MFG_PREP_ALREADY_READY characterId=" + receipt.getProperty("characterId") + "; no mutation/grant"); return;
					}
					check(resume && "PENDING".equals(receipt.getProperty("stage")) && args[5].equals(receipt.getProperty("codeSHA")) && args[6].equalsIgnoreCase(receipt.getProperty("gameJarSHA256")), "PENDING requires its exact addressed unlearned-stage continuation; no replay or replacement actor.");
				}
			}
			check(!resume || previous != null, "Addressed continuation requires the original durable PENDING receipt.");
			check(!Files.exists(runtime.resolve("MFG_PREP068.properties"), java.nio.file.LinkOption.NOFOLLOW_LINKS), "Unexpected profession receipt file; no uncertain-stage replay.");
			check(resume || count("SELECT COUNT(*) FROM accounts WHERE login=?", ACCOUNT) == 0 && count("SELECT COUNT(*) FROM characters WHERE char_name=?", NAME) == 0, "Stable preparation identity already exists without its exact receipt.");
			final var learn = SkillTreeData.getInstance().getClassSkill(SKILL, 1, org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(CLASS));
			check(learn != null && learn.getGetLevel() == LEVEL && learn.getLevelUpSp() == SP && learn.getRequiredItems().isEmpty() && learn.getPreReqSkills().isEmpty(), "Pinned minimal profession facts changed; review before grant.");
			final var recipe = RecipeData.getInstance().getRecipeList(RECIPE);
			check(recipe != null && recipe.isDwarvenRecipe() && recipe.getRecipeId() == SCROLL && recipe.getLevel() == 1 && recipe.getSuccessRate() == 100, "Pinned native recipe changed.");
			final long fee = pricing().manufactureFee(recipe.getId(), recipe.getItemId()); check(fee > 0, "Positive source-backed manufacture fee required.");
			final String initialFacts = "owner=" + OWNER + "\ncodeSHA=" + (resume ? args[5] : args[1]) + "\ngameJarSHA256=" + (resume ? args[6] : args[2]) + "\nsource=PREMEASUREMENT_FINITE_PROFESSION_GIFT\nclassId=53\nlevel=5\nskill=172:1\nspCredit=310\nscroll=1666:1\ncapital=0\ngear=0\nmaterials=0\nfee=" + fee + '\n';
			final byte[] pending = (initialFacts + "stage=PENDING\n").getBytes(StandardCharsets.UTF_8);
			check(!resume || java.util.Arrays.equals(pending, previous.payload()), "Original PENDING payload/facts/fee differ; no partial-stage repair.");
			final PhantomProfileComponent receipt;
			if (resume) { receipt = previous; verifyUnlearned(resumeId, profiles); }
			else if (servers.isEmpty())
			{
				final var ledger = PhantomMerchantBootstrap.Ledger.empty(policy.lifetimeIdentities(), policy.globalCapital(), policy.globalStockValue(), policy.perItemQuota());
				receipt = profiles.findComponent(profiles.createWithComponent(PhantomMerchantBootstrap.COMPONENT, 1, _ -> PhantomMerchantBootstrap.encode(ledger), Map.of(COMPONENT, pending)).profile().profileId(), COMPONENT).orElseThrow();
			}
			else { receipt = profiles.updateComponent(servers.getFirst().profile().profileId(), COMPONENT, -1, 1, pending); }
			if (!resume)
			{
				try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,?,-1,1)"))
				{
					insert.setString(1, ACCOUNT); insert.setString(2, "DISABLED_FINITE_PROFESSION068"); check(insert.executeUpdate() == 1, "Preparation account creation failed.");
				}
				player = Player.create(PlayerTemplateData.getInstance().getTemplate(CLASS), ACCOUNT, NAME, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				check(player != null && player.getAccessLevel().getLevel() == 0 && player.getInventory().getItems().isEmpty() && player.getAdena() == 0, "Pristine native actor required.");
				player.stopAllTasks();
				player.getStat().setExp(player.getStat().getExpForLevel(LEVEL)); player.getStat().setLevel((byte) LEVEL); player.setSp(SP); player.giveAvailableAutoGetSkills();
				if (player.isDead()) { player.doRevive(); } player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.storeMe();
				final int id = player.getObjectId();
				check(player.deleteMe(), "Fresh native actor reload cleanup failed.");
				check(World.getInstance().findObject(id) == null, "Fresh native identity still published before reload.");
				player = null; verifyUnlearned(id, profiles); player = Player.load(id);
			}
			else { player = Player.load(resumeId); }
			check(player != null && player.getActiveClass() == CLASS && player.getClassIndex() == 0 && player.getPlayerClass().getId() == CLASS && player.getBaseClass() == CLASS && player.getSp() == SP && player.getLevel() == LEVEL && player.getExp() == ExperienceData.getInstance().getExpForLevel(LEVEL), "Canonical loaded profession class/level/EXP/SP differ.");
			player.stopAllTasks();
			check(player.getAccessLevel().getLevel() == 0 && player.getClient() == null && player.getNativeWorkOwner() == null && !player.hasHeadlessOutboundSession() && !player.isAlikeDead() && player.getParty() == null && player.getInventory().getItems().isEmpty() && player.getDwarvenRecipeBook().isEmpty() && player.getCommonRecipeBook().isEmpty() && player.getSkillLevel(SKILL) <= 0 && player.getCurrentMp() >= 30, "Exact alive ordinary unlearned native actor required; no continuation vitals or resource grant.");
			final var result = new PhantomClassSkillLearningTransaction().execute(player, SkillData.getInstance().getSkill(SKILL, 1), null, player.getSkillLevel(SKILL), SP, null, null);
			check(result.status() == OperationStatus.SUCCESS && player.getSp() == 0 && player.getSkillLevel(SKILL) == 1, "Native durable skill learn must consume exactly310 SP; status=" + result.status());
			final var scroll = player.getInventory().addItem(ItemProcessType.REWARD, SCROLL, 1, player, "FINITE_PROFESSION068_RECEIPT");
			final var handler = ItemHandler.getInstance().getHandler(scroll.getEtcItem());
			check(handler != null && handler.getClass().getSimpleName().equals("Recipes") && handler.onItemUse(player, scroll, false), "Registered native Recipes handler failed.");
			check(player.hasRecipeList(RECIPE) && player.getInventory().getItems().isEmpty() && player.getAdena() == 0, "Scroll must be consumed; no capital/gear/material stock.");
			final var site = policy.sites().getFirst(); final int z = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(site.x(), site.y(), site.z());
			player.setOnlineStatus(true, false); player.spawnMe(site.x(), site.y(), z);
			check(player.isInsideZone(ZoneId.PEACE) && !player.isInsideZone(ZoneId.NO_STORE), "Configured native preparation slot is not a legal peaceful store.");
			player.getManufactureItems().put(RECIPE, new ManufactureItem(RECIPE, fee)); player.setStoreName("Craft service"); player.setPrivateStoreType(PrivateStoreType.MANUFACTURE); player.sitDown();
			player.setOfflineStartTime(System.currentTimeMillis()); player.setOnlineStatus(true, true); player.getInventory().updateDatabase(); player.storeMe();
			OfflineTraderTable.getInstance().onTransaction(player, false, true);
			verifyDurable(player.getObjectId(), fee);
			check(profiles.findByCharacterObjectId(player.getObjectId()).isEmpty(), "Preparation must not link a Phantom character profile.");
			final String continuation = resume ? "continuationSource=ADDRESSED_UNLEARNED_SKILL_NATIVE_LOAD\ncontinuationCodeSHA=" + args[1] + "\ncontinuationGameJarSHA256=" + args[2] + '\n' : "";
			final var ready = profiles.updateComponent(receipt.profileId(), COMPONENT, receipt.rowVersion(), 1, (initialFacts + continuation + "initialPendingPayloadBase64=" + java.util.Base64.getEncoder().encodeToString(pending) + "\nstage=READY\ncharacterId=" + player.getObjectId() + "\nexpCredit=" + player.getStat().getExpForLevel(LEVEL) + "\nspConsumed=310\nscrollConsumed=1\n").getBytes(StandardCharsets.UTF_8));
			Files.write(runtime.resolve("MFG_PREP068.properties"), ready.payload(), java.nio.file.StandardOpenOption.CREATE_NEW);
			System.out.println("MFG_PREP_READY characterId=" + player.getObjectId() + "; nativeRecipe=1; fee=" + fee + "; finite profession gift; not earned; configure prepared allowlist then ordinary START");
		}
		finally
		{
			if (player != null) { player.stopAllTasks(); if (World.getInstance().getPlayer(player.getObjectId()) == player) { player.deleteMe(); } }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	/** All checks precede ConfigLoader, DatabaseFactory or a native actor. No TEST guard is called. */
	static Path validateRelease(String rootArgument, String codeSHA, String jarSHA) throws Exception
	{
		check(Boolean.getBoolean("phantom.task068.release.prepare") && codeSHA.matches("[0-9a-f]{40}") && jarSHA.matches("[0-9a-fA-F]{64}"), "Explicit release preparation authority and exact hashes required.");
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.task") && !key.equals("phantom.task068.release.prepare")) { throw new IllegalArgumentException("Mixed historical/native/control authority forbidden."); }
		}
		final Path root = plain(Path.of(rootArgument));
		final var ownership = properties(Files.readAllBytes(plain(root.resolve("ownership068.properties"))));
		check(OWNER.equals(ownership.getProperty("owner")) && "1".equals(ownership.getProperty("version")) && "RELEASE_CANDIDATE".equals(ownership.getProperty("role")) && SCHEMA.equals(ownership.getProperty("schema")), "Exact RELEASE068 owner/schema/role required.");
		final Path source = Path.of(ownership.getProperty("sourceModuleRoot")).toAbsolutePath().normalize();
		check(source.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive") && root.equals(source.getRoot().resolve("PhantomWorld/Living-Economy-068-candidate")) && root.equals(Path.of(ownership.getProperty("runtimeRoot")).toAbsolutePath().normalize()) && plain(Path.of("")).equals(root.resolve("game")), "Exact candidate root and game working directory required.");
		final String build = read(root.resolve("BUILD_INFO068.json")), original = read(root.resolve("SOURCE_BUILD_INFO068.json"));
		for (String document : List.of(build, original))
		{
			check(OWNER.equals(json(document, "owner")) && "COMMITTED".equals(json(document, "sourceState")) && codeSHA.equals(json(document, "codeCommit")) && jarSHA.equalsIgnoreCase(json(document, "gameJarSha256")), "Committed source/JAR receipt differs.");
		}
		check(SCHEMA.equals(json(build, "databaseName")) && hash(root.resolve("SOURCE_BUILD_INFO068.json")).equalsIgnoreCase(json(build, "buildReceiptSha256")), "Candidate receipt binding differs.");
		final Path jar = plain(root.resolve("libs/GameServer.jar"));
		check(jarSHA.equalsIgnoreCase(hash(jar)) && jarSHA.equalsIgnoreCase(ownership.getProperty("gameJarSha256")) && jar.equals(plain(Path.of(Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()))), "Only the exact own committed production JAR may be loaded.");
		final Path helper = plain(Path.of(PhantomRelease068ManufacturePreparation.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		check(helper.equals(plain(Path.of(json(original, "buildRoot")).resolve("phantom-test/bin"))), "Helper must come from the matching committed test build; no isolated production shadow.");
		final var database = properties(Files.readAllBytes(plain(root.resolve("game/config/Database.ini"))));
		check(database.getProperty("URL", "").matches("jdbc:(?:mysql|mariadb)://127\\.0\\.0\\.1:3308/" + SCHEMA + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") && "4".equals(database.getProperty("MaximumDatabaseConnections")), "Exact own release JDBC/pool required.");
		check(ProcessHandle.allProcesses().filter(process -> process.pid() != ProcessHandle.current().pid()).noneMatch(process -> process.info().commandLine().orElse("").matches(".*-Dphantom\\.task068\\.(?:control|native)=true.*")), "Stop all own068 runtime/native JVMs before preparation.");
		return root;
	}

	private static void bootstrap(Path root) throws Exception
	{
		ConfigLoader.init();
		DatabaseConfig.load(root.resolve("game/config/Database.ini").toString());
		try (var connection = DriverManager.getConnection(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD); var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
		{
			query.setString(1, SCHEMA); try (var rows = query.executeQuery()) { check(rows.next() && rows.getInt(1) == 0, "Release schema is not quiescent."); }
		}
		DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		ThreadPool.init(); IdManager.getInstance(); World.getInstance(); CategoryData.getInstance(); ExperienceData.getInstance();
		org.l2jmobius.gameserver.handler.EffectHandler.getInstance().executeScript();
		EnchantSkillGroupsData.getInstance(); SkillTreeData.getInstance(); SkillData.getInstance(); ItemData.getInstance(); EnchantItemOptionsData.getInstance(); OptionData.getInstance(); RecipeData.getInstance(); ClassListData.getInstance(); PlayerTemplateData.getInstance(); AdminData.getInstance();
		org.l2jmobius.gameserver.data.sql.CharInfoTable.getInstance(); org.l2jmobius.gameserver.data.sql.ClanTable.getInstance(); CHSiegeManager.getInstance(); org.l2jmobius.gameserver.data.sql.ClanHallTable.getInstance(); ClanHallAuctionManager.getInstance();
		org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance(); SkillLearnData.getInstance(); NpcData.getInstance(); CastleManager.getInstance().loadInstances(); InstanceManager.getInstance(); ZoneManager.getInstance(); GrandBossManager.getInstance().initZones(); TerritoryWarManager.getInstance();
		org.l2jmobius.gameserver.model.olympiad.Hero.getInstance(); org.l2jmobius.gameserver.model.sevensigns.SevenSigns.getInstance(); org.l2jmobius.gameserver.model.groups.matching.PartyMatchWaitingList.getInstance(); org.l2jmobius.gameserver.model.groups.matching.PartyMatchRoomList.getInstance(); CursedWeaponsManager.getInstance(); RecipeManager.getInstance(); org.l2jmobius.gameserver.model.olympiad.OlympiadManager.getInstance();
		InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); DoorData.getInstance();
		org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().loadInstances(); org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().updateReferences(); SpawnData.getInstance();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
	}

	private static PhantomPrivateMarketPricingAuthority pricing() throws Exception
	{
		final var validation = new L2jTopologyValidationBackend();
		final var topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), validation, PhantomTopologyPolicy.productionDefaults()).load(1);
		final var policy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
		final var knowledge = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), new PhantomTopologyQuery(topology, validation, new PhantomTopologyMetrics()), policy).build();
		return new PhantomPrivateMarketPricingAuthority(knowledge, new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog(), PhantomRateAwareLootFairValue.Rates.capture(), PhantomMarketConfig.policy().orElseThrow(), () -> World.getInstance().getVisibleObjects());
	}

	private static void verifyDurable(int id, long fee) throws Exception
	{
		check(count("SELECT COUNT(*) FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.charId=? AND c.char_name='Craft068Maker' AND c.account_name='phm068profession' AND c.accesslevel=0 AND c.level=5 AND c.classid=53 AND c.base_class=53 AND c.sp=0 AND c.curHp>=0.5 AND a.accessLevel=-1", id) == 1, "Native profession identity/vitals/SP differ.");
		check(count("SELECT COUNT(*) FROM items WHERE owner_id=?", id) == 0, "Maker must retain zero Adena/gear/materials/scroll.");
		check(count("SELECT COUNT(*) FROM character_skills WHERE charId=? AND skill_id=172 AND skill_level=1 AND class_index=0", id) == 1 && count("SELECT COUNT(*) FROM character_recipebook WHERE charId=? AND id=1 AND classIndex=0 AND type=1", id) == 1, "Native skill/book rows absent.");
		check(count("SELECT COUNT(*) FROM character_offline_trade WHERE charId=? AND type=5", id) == 1, "Native offline manufacture row absent.");
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT item,count,price FROM character_offline_trade_items WHERE charId=?"))
		{
			query.setInt(1, id); try (var rows = query.executeQuery()) { check(rows.next() && rows.getInt(1) == RECIPE && rows.getLong(2) == 0 && rows.getLong(3) == fee && !rows.next(), "Native offline recipe listing differs."); }
		}
	}
	/** Read-only, exact economic baseline before loading the one addressed native actor. */
	private static void verifyUnlearned(int id, PhantomProfileRepository profiles) throws Exception
	{
		check(World.getInstance().findObject(id) == null && profiles.findByCharacterObjectId(id).isEmpty(), "Prepared actor is already published or linked to a Phantom profile.");
		check(count("SELECT COUNT(*) FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.charId=? AND c.char_name='Craft068Maker' AND c.account_name='phm068profession' AND c.accesslevel=0 AND c.level=5 AND c.classid=53 AND c.base_class=53 AND c.exp=" + ExperienceData.getInstance().getExpForLevel(LEVEL) + " AND c.sp=310 AND c.curHp>=0.5 AND c.curMp>=30 AND c.online=0 AND a.accessLevel=-1", id) == 1, "Exact unlearned native identity/EXP/SP/vitals/offline baseline differs.");
		check(count("SELECT COUNT(*) FROM characters WHERE account_name=?", ACCOUNT) == 1 && count("SELECT COUNT(*) FROM characters WHERE char_name=?", NAME) == 1, "Stable profession identity is ambiguous.");
		for (String table : List.of("items", "character_subclasses", "character_recipebook", "character_recipeshoplist", "character_offline_trade", "character_offline_trade_items"))
		{
			check(count("SELECT COUNT(*) FROM " + table + " WHERE " + (table.equals("items") ? "owner_id" : "charId") + "=?", id) == 0, "Unlearned continuation has unexpected durable rows: " + table);
		}
		check(count("SELECT COUNT(*) FROM character_skills WHERE charId=? AND skill_id=172", id) == 0, "Skill learning may already have executed; refuse partial-stage continuation.");
	}
	private static long count(String sql, Object parameter) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(sql)) { query.setObject(1, parameter); try (var rows = query.executeQuery()) { check(rows.next(), "Missing native verification row."); return rows.getLong(1); } }
	}
	private static Properties properties(byte[] bytes) throws Exception { final var result = new Properties(); result.load(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8).replace("\ufeff", ""))); return result; }
	private static String read(Path path) throws Exception { final Path file = plain(path); check(Files.size(file) <= 32768, "Bounded own manifest required."); return Files.readString(file); }
	private static String json(String document, String key)
	{
		final var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(document);
		check(matcher.find(), "Missing exact metadata key: " + key); final String value = matcher.group(1).replace("\\\\", "\\").replace("\\/", "/"); check(!matcher.find(), "Duplicate metadata key: " + key); return value;
	}
	private static Path plain(Path path) throws Exception
	{
		final Path absolute = path.toAbsolutePath().normalize(); check(absolute.equals(absolute.toRealPath()), "Physical own paths only.");
		for (Path current = absolute; current != null; current = current.getParent()) { check(!Files.isSymbolicLink(current) && !Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS).isOther(), "No aliases/reparse targets."); } return absolute;
	}
	private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(plain(path)))); }
	private static void check(boolean condition, String message) { if (!condition) { throw new IllegalArgumentException(message); } }
}
