/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager.EntryReadinessSnapshot;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyMessageType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.sevensigns.DimensionalRift;
import org.l2jmobius.gameserver.model.sevensigns.DimensionalRiftRoom;
import org.l2jmobius.gameserver.model.sevensigns.SevenSigns;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-005 bounded personal Seven Signs access coverage. */
public final class QoLSevenSignsAccessSuite implements PhantomTestSuite
{
	private static final long SEED = 1005001L;
	private static final byte RIFT_TYPE = 1;
	private static final int RIFT_NPC_ID = 31494;
	private static final int DIMENSIONAL_FRAGMENT_ID = 7079;
	private static final Pattern NPC_ELEMENT = Pattern.compile("<npc\\b([^>]*)>");
	private static final Pattern NPC_ID = Pattern.compile("\\bid=\"([0-9]+)\"");
	private static final Pattern RESPAWN_DELAY = Pattern.compile("\\brespawnDelay=\"([0-9]+)\"");

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Settings _previousSettings;
	private Player _player;
	private Player _observer;
	private Npc _riftNpc;
	private Party _party;
	private Path _fixtures;
	private int _previousRiftSpawnDelay;
	private boolean _riftSpawnDelayChanged;

	@Override
	public String id()
	{
		return "qol-seven-signs-access";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-005 suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-005 fixture Players did not load.");
		PhantomAssertions.assertTrue(NpcData.getInstance().getTemplate(RIFT_NPC_ID) != null, "Native Rift teleporter template is absent.");
		_riftNpc = new Npc(NpcData.getInstance().getTemplate(RIFT_NPC_ID));
		_previousSettings = install(allowlisted());
		_fixtures = context.reportsDirectory().resolve("qol-seven-signs-access-fixtures");
		Files.createDirectories(_fixtures);
		context.record("qol005.database", "l2jmobiush5_phantom_test");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-shipped-off-backward-compatible-and-real-player-only", this::configAndIdentity);
		registry.add("02-central-cabal-winner-seal-policy-and-no-registration-mutation", this::policyAndMutationBoundary);
		registry.add("03-all-catacomb-necropolis-combat-spawns-all-seven-signs-periods", this::catacombPopulationCensus);
		registry.add("04-native-rift-prerequisites-populated-rooms-jump-and-cleanup", this::nativeRiftLifecycle);
		registry.add("05-complete-gate-wiring-rift-continuation-and-mammon-world-state", this::sourceCensusContract);
	}

	private void configAndIdentity(PhantomTestContext context) throws Exception
	{
		final Settings shipped = PersonalCharacterQoLConfig.read(Path.of("config/Custom/PersonalCharacterQoL.ini"));
		PhantomAssertions.assertTrue(shipped.valid() && !shipped.enabled() && !shipped.sevenSignsAccessEnabled(), "Personal Seven Signs access is not shipped OFF.");

		final Settings legacy = PersonalCharacterQoLConfig.read(write("legacy-qol004.ini", config("True", null, Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertTrue(legacy.valid() && legacy.enabled() && !legacy.sevenSignsAccessEnabled(), "An older Personal QoL config did not retain an OFF Seven Signs default.");
		final Settings malformed = PersonalCharacterQoLConfig.read(write("invalid-seven-signs-switch.ini", config("True", "sometimes", Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertFalse(malformed.valid() || malformed.enabled(), "Malformed personal Seven Signs switch did not fail closed.");
		final Settings masterOff = PersonalCharacterQoLConfig.read(write("master-off.ini", config("False", "True", Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertFalse(masterOff.enabled() || masterOff.sevenSignsAccessEnabled(), "Disabled Personal QoL master admitted Seven Signs access.");

		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		install(allowlisted());
		PhantomAssertions.assertTrue(service.isSevenSignsAccessEnabled(_player), "Allowlisted real Player was denied personal Seven Signs access.");
		PhantomAssertions.assertFalse(service.isSevenSignsAccessEnabled(_observer), "Non-allowlisted real Player received personal Seven Signs access.");
		install(new Settings(true, false, false, true, Set.of(), Set.of(_player.getAccountName().toLowerCase(Locale.ROOT)), true, "Account allowlist test."));
		PhantomAssertions.assertTrue(service.isSevenSignsAccessEnabled(_player), "Case-normalized account allowlist was denied.");
		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertFalse(service.isSevenSignsAccessEnabled(_player), "Headless Phantom received personal Seven Signs access.");
		}
		install(Settings.disabled("Feature OFF test."));
		PhantomAssertions.assertFalse(service.isSevenSignsAccessEnabled(_player), "Feature OFF admitted an allowlisted Player.");
		install(allowlisted());
		context.record("qol005.identity", "shippedOff=true;legacyDefault=false;realAllowlisted=true;ordinary/headless=false");
	}

	private void policyAndMutationBoundary(PhantomTestContext context)
	{
		install(allowlisted());
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final SevenSigns sevenSigns = SevenSigns.getInstance();
		final int cabalBefore = sevenSigns.getPlayerCabal(_player.getObjectId());
		final int sealBefore = sevenSigns.getPlayerSeal(_player.getObjectId());
		final int stonesBefore = sevenSigns.getPlayerStoneContrib(_player.getObjectId());
		final int scoreBefore = sevenSigns.getPlayerContribScore(_player.getObjectId());

		PhantomAssertions.assertTrue(service.isSevenSignsRegistered(_player, SevenSigns.CABAL_NULL), "Personal registration exception was denied.");
		PhantomAssertions.assertFalse(service.isSevenSignsRegistered(_observer, SevenSigns.CABAL_NULL), "Ordinary unregistered Player was admitted.");
		PhantomAssertions.assertTrue(service.isSevenSignsCabalEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN), "Personal winner exception was denied.");
		PhantomAssertions.assertFalse(service.isSevenSignsCabalEligible(_observer, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN), "Ordinary losing/unregistered Player was admitted.");
		PhantomAssertions.assertTrue(service.isSevenSignsWinningSealEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN, SevenSigns.CABAL_DAWN), "Personal winner access was denied with matching global seal state.");
		PhantomAssertions.assertFalse(service.isSevenSignsWinningSealEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN, SevenSigns.CABAL_DUSK), "Personal access bypassed the global seal owner.");
		PhantomAssertions.assertFalse(service.isSevenSignsWinningSealEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_NULL, SevenSigns.CABAL_NULL), "Personal access invented a competition winner.");
		PhantomAssertions.assertFalse(service.isSevenSignsCabalEligible(_observer, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN), "One party member's eligibility leaked to another Player.");

		install(Settings.disabled("Exact stock policy test."));
		PhantomAssertions.assertFalse(service.isSevenSignsRegistered(_player, SevenSigns.CABAL_NULL), "Feature OFF changed stock registration denial.");
		PhantomAssertions.assertTrue(service.isSevenSignsRegistered(_player, SevenSigns.CABAL_DUSK), "Feature OFF changed stock registration admission.");
		PhantomAssertions.assertFalse(service.isSevenSignsCabalEligible(_player, SevenSigns.CABAL_DUSK, SevenSigns.CABAL_DAWN), "Feature OFF changed stock winner denial.");
		PhantomAssertions.assertTrue(service.isSevenSignsCabalEligible(_player, SevenSigns.CABAL_DAWN, SevenSigns.CABAL_DAWN), "Feature OFF changed stock winner admission.");
		install(allowlisted());

		PhantomAssertions.assertEquals(cabalBefore, sevenSigns.getPlayerCabal(_player.getObjectId()), "Personal policy mutated Seven Signs cabal registration.");
		PhantomAssertions.assertEquals(sealBefore, sevenSigns.getPlayerSeal(_player.getObjectId()), "Personal policy mutated the selected seal.");
		PhantomAssertions.assertEquals(stonesBefore, sevenSigns.getPlayerStoneContrib(_player.getObjectId()), "Personal policy mutated stone contribution.");
		PhantomAssertions.assertEquals(scoreBefore, sevenSigns.getPlayerContribScore(_player.getObjectId()), "Personal policy mutated contribution score.");
		context.record("qol005.policy", "registration/cabal=personal-only;winner+seal=global;sevenSignsMutation=false");
	}

	private void catacombPopulationCensus(PhantomTestContext context) throws Exception
	{
		final Path spawnDirectory = context.moduleRoot().resolve("dist/game/data/spawns/Catacombs");
		final List<Path> spawnFiles;
		try (var files = Files.list(spawnDirectory))
		{
			spawnFiles = files.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList();
		}
		PhantomAssertions.assertEquals(14, spawnFiles.size(), "Catacomb/Necropolis combat spawn-list census changed.");

		int npcDeclarations = 0;
		int minimumRespawn = Integer.MAX_VALUE;
		int maximumRespawn = 0;
		int representativeNpcId = 0;
		for (Path spawnFile : spawnFiles)
		{
			final String xml = Files.readString(spawnFile, StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(xml.contains("<list enabled=\"true\""), "Dungeon combat spawn list is disabled: " + spawnFile.getFileName());
			PhantomAssertions.assertFalse(xml.contains("periodOfDay") || xml.contains("SevenSigns") || xml.contains("seven_signs"), "Dungeon combat spawn list acquired a phase condition: " + spawnFile.getFileName());
			final Matcher npcs = NPC_ELEMENT.matcher(xml);
			int fileNpcDeclarations = 0;
			while (npcs.find())
			{
				final String attributes = npcs.group(1);
				final Matcher id = NPC_ID.matcher(attributes);
				final Matcher respawn = RESPAWN_DELAY.matcher(attributes);
				PhantomAssertions.assertTrue(id.find() && respawn.find(), "Dungeon combat spawn lacks an NPC id or native respawn delay: " + spawnFile.getFileName());
				final int npcId = Integer.parseInt(id.group(1));
				final int respawnDelay = Integer.parseInt(respawn.group(1));
				if (representativeNpcId == 0)
				{
					representativeNpcId = npcId;
				}
				PhantomAssertions.assertTrue((NpcData.getInstance().getTemplate(npcId) != null) && NpcData.getInstance().getTemplate(npcId).isAttackable(), "Dungeon spawn is not a normal combat NPC: " + npcId);
				PhantomAssertions.assertTrue(respawnDelay > 0, "Dungeon combat NPC has disabled respawn: " + npcId);
				minimumRespawn = Math.min(minimumRespawn, respawnDelay);
				maximumRespawn = Math.max(maximumRespawn, respawnDelay);
				fileNpcDeclarations++;
				npcDeclarations++;
			}
			PhantomAssertions.assertTrue(fileNpcDeclarations > 0, "Dungeon combat spawn list is empty: " + spawnFile.getFileName());
		}

		final int[] periods =
		{
			SevenSigns.PERIOD_COMP_RECRUITING,
			SevenSigns.PERIOD_COMPETITION,
			SevenSigns.PERIOD_COMP_RESULTS,
			SevenSigns.PERIOD_SEAL_VALIDATION
		};
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		for (int period : periods)
		{
			final boolean sealValidation = period == SevenSigns.PERIOD_SEAL_VALIDATION;
			final boolean resultOrValidation = (period == SevenSigns.PERIOD_COMP_RESULTS) || sealValidation;
			final boolean personalEntry = sealValidation ? service.isSevenSignsWinningSealEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN, SevenSigns.CABAL_DAWN) : service.isSevenSignsRegistered(_player, SevenSigns.CABAL_NULL);
			final boolean personalContinuedAccess = resultOrValidation ? service.isSevenSignsCabalEligible(_player, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN) : service.isSevenSignsRegistered(_player, SevenSigns.CABAL_NULL);
			final boolean ordinaryEntry = sealValidation ? service.isSevenSignsWinningSealEligible(_observer, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN, SevenSigns.CABAL_DAWN) : service.isSevenSignsRegistered(_observer, SevenSigns.CABAL_NULL);
			final boolean ordinaryContinuedAccess = resultOrValidation ? service.isSevenSignsCabalEligible(_observer, SevenSigns.CABAL_NULL, SevenSigns.CABAL_DAWN) : service.isSevenSignsRegistered(_observer, SevenSigns.CABAL_NULL);
			PhantomAssertions.assertTrue(personalEntry && personalContinuedAccess && service.isSevenSignsAccessEnabled(_player), "Personal Catacomb/Necropolis entry, relog or continued access failed for period " + period + ".");
			PhantomAssertions.assertFalse(ordinaryEntry || ordinaryContinuedAccess, "Ordinary unregistered Player bypassed stock Catacomb/Necropolis policy for period " + period + ".");
			PhantomAssertions.assertEquals(14, spawnFiles.size(), "A Seven Signs period changed the dungeon combat file set: " + period);
			PhantomAssertions.assertTrue(npcDeclarations > 0, "A Seven Signs period produced an empty dungeon combat census: " + period);
			PhantomAssertions.assertTrue((minimumRespawn > 0) && (maximumRespawn >= minimumRespawn), "A Seven Signs period lost normal dungeon respawn: " + period);
		}
		assertNormalCombatRespawn(representativeNpcId);

		final String spawnData = source(context, "java/org/l2jmobius/gameserver/data/xml/SpawnData.java");
		final String spawn = source(context, "java/org/l2jmobius/gameserver/model/spawns/Spawn.java");
		final String sevenSigns = source(context, "java/org/l2jmobius/gameserver/model/sevensigns/SevenSigns.java");
		assertContains(spawnData, "parseDatapackDirectory(\"data/spawns\", true)", "Generic startup loader no longer loads dungeon spawn lists.");
		assertContains(spawnData, "ret += spawnDat.init()", "Generic dungeon spawns are not initialized.");
		PhantomAssertions.assertFalse(spawnData.contains("SevenSigns"), "Generic spawn loading acquired a Seven Signs phase dependency.");
		assertContains(spawn, "if (_doRespawn && ((_scheduledCount + _currentCount) < _maximumCount))", "Normal combat respawn scheduling changed.");
		assertContains(spawn, "RespawnTaskManager.getInstance().add", "Normal combat respawn task handoff is absent.");
		PhantomAssertions.assertFalse(sevenSigns.contains("data/spawns/Catacombs") || sevenSigns.contains("SpawnData.getInstance"), "Seven Signs period transitions acquired normal dungeon combat-spawn ownership.");
		context.record("qol005.catacombPopulation", "periods=0,1,2,3;personalEntry/relog/continued=true;files=14;npcDeclarations=" + npcDeclarations + ";respawnSeconds=" + minimumRespawn + ".." + maximumRespawn + ";actualRespawn=true;phaseDependency=false");
	}

	private void assertNormalCombatRespawn(int npcId) throws Exception
	{
		final Spawn spawn = new Spawn(npcId);
		spawn.setAmount(1);
		spawn.setXYZ(_player.getX() + 96, _player.getY() + 96, _player.getZ());
		spawn.setHeading(0);
		spawn.setRespawnDelay(1);
		try
		{
			PhantomAssertions.assertEquals(1, spawn.init(), "Representative dungeon combat NPC did not spawn.");
			final Npc initial = spawn.getLastSpawn();
			PhantomAssertions.assertTrue((initial != null) && initial.isSpawned() && initial.isAttackable() && spawn.isRespawnEnabled(), "Representative dungeon combat population is not playable or respawn-enabled.");
			initial.deleteMe();
			final long deadline = System.nanoTime() + 4_000_000_000L;
			while ((System.nanoTime() < deadline) && ((spawn.getLastSpawn() == null) || spawn.getLastSpawn().isDecayed() || !spawn.getLastSpawn().isSpawned()))
			{
				Thread.sleep(10);
			}
			final Npc respawned = spawn.getLastSpawn();
			PhantomAssertions.assertTrue((respawned != null) && respawned.isSpawned() && !respawned.isDecayed() && respawned.isAttackable(), "Representative dungeon combat NPC did not complete normal respawn.");
		}
		finally
		{
			spawn.stopRespawn();
			if (spawn.getLastSpawn() != null)
			{
				spawn.getLastSpawn().deleteMe();
			}
		}
	}

	private void nativeRiftLifecycle(PhantomTestContext context) throws Exception
	{
		final DimensionalRiftManager manager = DimensionalRiftManager.getInstance();
		final EntryReadinessSnapshot entry = manager.entryReadiness(RIFT_TYPE);
		PhantomAssertions.assertTrue(entry.supported() && entry.entryCapacityAvailable(), "Recruit Rift entry snapshot is unavailable.");
		PhantomAssertions.assertEquals(DIMENSIONAL_FRAGMENT_ID, entry.entryItemId(), "Native Rift entry item changed.");
		PhantomAssertions.assertEquals(GeneralConfig.RIFT_MIN_PARTY_SIZE, entry.minimumPartySize(), "Rift entry snapshot differs from the configured minimum party size.");
		PhantomAssertions.assertEquals(2, GeneralConfig.RIFT_MIN_PARTY_SIZE, "Focused two-Player fixture no longer matches the shipped Rift minimum.");

		manager.start(_player, RIFT_TYPE, _riftNpc);
		PhantomAssertions.assertFalse(_player.isInParty(), "Rift created a party for a solo Player.");
		_party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS);
		_player.setParty(_party);
		manager.teleportToWaitingRoom(_player);
		manager.start(_player, RIFT_TYPE, _riftNpc);
		PhantomAssertions.assertFalse(_party.isInDimensionalRift(), "Rift ignored RIFT_MIN_PARTY_SIZE.");

		_party.addPartyMember(_observer);
		_observer.setParty(_party);
		manager.teleportToWaitingRoom(_observer);
		PhantomAssertions.assertTrue(manager.checkIfInPeaceZone(_player.getX(), _player.getY(), _player.getZ()) && manager.checkIfInPeaceZone(_observer.getX(), _observer.getY(), _observer.getZ()), "Native waiting-room teleport did not place every member in the peace zone.");
		manager.start(_observer, RIFT_TYPE, _riftNpc);
		PhantomAssertions.assertFalse(_party.isInDimensionalRift(), "A non-leader started the Rift.");

		final List<Byte> freeRooms = List.copyOf(manager.getFreeRooms(RIFT_TYPE));
		PhantomAssertions.assertTrue(freeRooms.size() > 1, "Recruit Rift has insufficient rooms for the capacity/jump fixture.");
		try
		{
			for (byte room : freeRooms)
			{
				manager.getRoom(RIFT_TYPE, room).setPartyInside(true);
			}
			manager.start(_player, RIFT_TYPE, _riftNpc);
			PhantomAssertions.assertFalse(_party.isInDimensionalRift(), "Full Rift capacity was bypassed.");
		}
		finally
		{
			for (byte room : freeRooms)
			{
				manager.getRoom(RIFT_TYPE, room).setPartyInside(false);
			}
		}

		manager.start(_player, RIFT_TYPE, _riftNpc);
		PhantomAssertions.assertFalse(_party.isInDimensionalRift(), "Rift admitted a party without Dimensional Fragments.");
		final int cost = entry.entryItemCount();
		_player.getInventory().addItem(ItemProcessType.REWARD, DIMENSIONAL_FRAGMENT_ID, cost, _player, this);
		_observer.getInventory().addItem(ItemProcessType.REWARD, DIMENSIONAL_FRAGMENT_ID, cost, _observer, this);
		final long playerFragments = _player.getInventory().getInventoryItemCount(DIMENSIONAL_FRAGMENT_ID, -1);
		final long observerFragments = _observer.getInventory().getInventoryItemCount(DIMENSIONAL_FRAGMENT_ID, -1);
		_previousRiftSpawnDelay = GeneralConfig.RIFT_SPAWN_DELAY;
		_riftSpawnDelayChanged = true;
		GeneralConfig.RIFT_SPAWN_DELAY = 0;
		manager.start(_player, RIFT_TYPE, _riftNpc);

		PhantomAssertions.assertTrue(_party.isInDimensionalRift(), "Native prerequisites did not start a Rift session.");
		final DimensionalRift rift = _party.getDimensionalRift();
		final byte firstRoom = rift.getCurrentRoom();
		final DimensionalRiftRoom firstRoomState = manager.getRoom(RIFT_TYPE, firstRoom);
		final Location firstRoomLocation = firstRoomState.getTeleportCoorinates();
		PhantomAssertions.assertTrue((firstRoom > 0) && manager.checkIfInRiftZone(firstRoomLocation.getX(), firstRoomLocation.getY(), firstRoomLocation.getZ(), true), "Rift session did not select a first monster room.");
		awaitPopulation(firstRoomState, "first");
		PhantomAssertions.assertEquals(playerFragments - cost, _player.getInventory().getInventoryItemCount(DIMENSIONAL_FRAGMENT_ID, -1), "Leader fragment payment changed.");
		PhantomAssertions.assertEquals(observerFragments - cost, _observer.getInventory().getInventoryItemCount(DIMENSIONAL_FRAGMENT_ID, -1), "Member fragment payment changed.");

		rift.manualTeleport(_observer, _riftNpc);
		PhantomAssertions.assertEquals(firstRoom, rift.getCurrentRoom(), "Non-leader performed a manual Rift jump.");
		PhantomAssertions.assertEquals((byte) GeneralConfig.RIFT_MAX_JUMPS, rift.getMaxJumps(), "Native Rift jump limit changed.");
		rift.manualTeleport(_player, _riftNpc);
		PhantomAssertions.assertTrue(rift.getCurrentRoom() != firstRoom, "Leader manual jump did not select the next room.");
		awaitPopulation(manager.getRoom(RIFT_TYPE, rift.getCurrentRoom()), "subsequent");

		_party.removePartyMember(_observer, PartyMessageType.LEFT);
		PhantomAssertions.assertFalse(_party.isInDimensionalRift(), "Low member count did not clean the Rift session.");
		PhantomAssertions.assertTrue(manager.checkIfInPeaceZone(_observer.getX(), _observer.getY(), _observer.getZ()), "Native two-member disband did not return the Rift counterpart to the waiting room.");
		GeneralConfig.RIFT_SPAWN_DELAY = _previousRiftSpawnDelay;
		_riftSpawnDelayChanged = false;
		context.record("qol005.rift", "solo/nonLeader/minSize/capacity/fragments=retained;first/subsequentPopulation=true;nativeRespawn=true;manualLeader=true;lowMemberCleanup=true");
	}

	private void sourceCensusContract(PhantomTestContext context) throws Exception
	{
		final String dungeonGate = source(context, "java/org/l2jmobius/gameserver/model/actor/instance/DungeonGatekeeper.java");
		final String huntingTeleport = source(context, "dist/game/data/scripts/ai/others/HuntingGroundsTeleport/HuntingGroundsTeleport.java");
		final String gatekeeperSpirit = source(context, "dist/game/data/scripts/ai/others/GatekeeperSpirit/GatekeeperSpirit.java");
		final String callPc = source(context, "dist/game/data/scripts/handlers/skill/effects/CallPc.java");
		final String wedding = source(context, "dist/game/data/scripts/handlers/chat/commands/voiced/Wedding.java");
		final String dawnPriest = source(context, "java/org/l2jmobius/gameserver/model/actor/instance/DawnPriest.java");
		final String duskPriest = source(context, "java/org/l2jmobius/gameserver/model/actor/instance/DuskPriest.java");
		final String signsPriest = source(context, "java/org/l2jmobius/gameserver/model/actor/instance/SignsPriest.java");
		final String player = source(context, "java/org/l2jmobius/gameserver/model/actor/Player.java");
		final String sevenSigns = source(context, "java/org/l2jmobius/gameserver/model/sevensigns/SevenSigns.java");
		final String npc = source(context, "java/org/l2jmobius/gameserver/model/actor/Npc.java");
		final String service = source(context, "java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java");
		assertContains(dungeonGate, "isSevenSignsWinningSealEligible", "Catacomb/Necropolis winner gate is not wired.");
		assertContains(dungeonGate, "isSevenSignsRegistered", "Catacomb/Necropolis registration gate is not wired.");
		assertContains(huntingTeleport, "isSevenSignsRegistered", "Hunting-ground destination route is not wired.");
		assertContains(gatekeeperSpirit, "isSevenSignsWinningSealEligible", "Avarice boss-room gate is not wired.");
		assertContains(callPc, "isSevenSignsCabalEligible(target", "Summon Friend does not evaluate the destination Player independently.");
		assertContains(wedding, "isSevenSignsCabalEligible(activeChar", "Wedding teleport does not evaluate the teleporting Player independently.");
		assertContains(dawnPriest, "addPersonalDungeonAccess(player, html)", "Dawn Priest does not expose the personal hunting-ground route in every period.");
		assertContains(duskPriest, "addPersonalDungeonAccess(player, html)", "Dusk Priest does not expose the personal hunting-ground route in every period.");
		assertContains(signsPriest, "isSevenSignsAccessEnabled(player)", "Priest destination route is not personal-only.");
		assertContains(signsPriest, "Script HuntingGroundsTeleport", "Priest destination route does not reuse the native hunting-ground owner.");
		assertContains(player, "isSevenSignsCabalEligible(this", "Relog validation ejection is not wired.");
		assertContains(player, "isSevenSignsRegistered(this", "Relog registration ejection is not wired.");
		assertContains(sevenSigns, "isSevenSignsAccessEnabled(player)", "Delayed period ejection is not wired.");
		assertContains(npc, "case 31113", "Merchant of Mammon family is absent.");
		assertContains(npc, "case 31126", "Blacksmith of Mammon family is absent.");
		PhantomAssertions.assertTrue(occurrences(npc, "isSevenSignsWinningSealEligible") == 4, "Mammon strict player gates are not bounded to both Dawn/Dusk branches for both families.");
		PhantomAssertions.assertFalse(service.contains("setPlayerInfo") || service.contains("setPlayerSeal") || service.contains("addPlayerStoneContrib") || service.contains("setPlayer"), "Personal policy contains a Seven Signs mutation API.");

		final String manager = source(context, "java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java");
		final String rift = source(context, "java/org/l2jmobius/gameserver/model/sevensigns/DimensionalRift.java");
		final String riftRoom = source(context, "java/org/l2jmobius/gameserver/model/sevensigns/DimensionalRiftRoom.java");
		final String riftData = source(context, "dist/game/data/DimensionalRift.xml");
		final String oracle = source(context, "dist/game/data/scripts/ai/others/OracleTeleport/OracleTeleport.java");
		final String riftBypass = source(context, "dist/game/data/scripts/handlers/bypass/npc/Rift.java");
		final String enterWorld = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java");
		PhantomAssertions.assertFalse(manager.contains("PersonalCharacterQoLService") || manager.contains("getPlayerCabal"), "Rift start acquired a personal/cabal bypass.");
		assertContains(manager, "if (!player.isInParty())", "Rift party-required gate is absent.");
		assertContains(manager, "party.getLeaderObjectId() != player.getObjectId()", "Rift start leader gate is absent.");
		assertContains(manager, "party.getMemberCount() < GeneralConfig.RIFT_MIN_PARTY_SIZE", "Rift minimum-size gate is absent.");
		assertContains(manager, "if (!isAllowedEnter(type))", "Rift capacity gate is absent.");
		assertContains(manager, "checkIfInPeaceZone", "Rift waiting-room presence gate is absent.");
		assertContains(manager, "DIMENSIONAL_FRAGMENT_ITEM_ID = 7079", "Rift fragment item changed.");
		assertContains(manager, "destroyItem(ItemProcessType.FEE", "Rift fragment debit is absent.");
		assertContains(manager, "new DimensionalRift(party, type, room)", "Rift first-room session creation is absent.");
		PhantomAssertions.assertFalse(rift.contains("PersonalCharacterQoLService") || rift.contains("getPlayerCabal"), "Rift continuation acquired a personal/cabal bypass.");
		assertContains(rift, "currentJump < getMaxJumps()", "Timed jump limit is absent.");
		assertContains(rift, "createSpawnTimer(_choosenRoom)", "First/timed Rift rooms no longer schedule combat population.");
		assertContains(rift, "createSpawnTimer(_choosenRoom);", "Manual Rift jump no longer schedules combat population.");
		assertContains(rift, "public void manualTeleport", "Manual room transition is absent.");
		assertContains(rift, "player.getObjectId() != party.getLeaderObjectId()", "Manual leader rule is absent.");
		assertContains(rift, "partyMemberExited", "Low-member exit handling is absent.");
		assertContains(rift, "teleportToWaitingRoom", "Rift waiting-room ejection is absent.");
		assertContains(rift, "qs.exitQuest(true, true)", "Rift quest cleanup is absent.");
		assertContains(riftRoom, "spawn.doSpawn(false)", "Rift room combat spawn is absent.");
		assertContains(riftRoom, "spawn.startRespawn()", "Rift room normal respawn is absent.");
		PhantomAssertions.assertFalse(riftRoom.contains("SevenSigns.getInstance") || riftData.contains("period"), "Rift room population acquired a Seven Signs period dependency.");
		assertContains(riftBypass, "DimensionalRiftManager.getInstance().start", "Rift NPC bypass no longer delegates to the native manager.");
		assertContains(oracle, "event.equalsIgnoreCase(\"Dimensional\")", "Oracle waiting-room route is absent.");
		assertContains(oracle, "event.equalsIgnoreCase(\"zigurratDimensional\")", "Ziggurat waiting-room route is absent.");
		PhantomAssertions.assertFalse(oracle.contains("getPlayerCabal") || oracle.contains("PersonalCharacterQoLService"), "Oracle waiting-room route acquired a speculative cabal edit.");
		assertContains(enterWorld, "checkIfInRiftZone", "Rift relog-zone check is absent.");
		assertContains(enterWorld, "teleportToWaitingRoom(player)", "Rift relog safety return is absent.");

		assertContains(sevenSigns, "public void spawnSevenSignsNPC()", "Global Mammon spawn owner is absent.");
		assertContains(sevenSigns, "getSealOwner(SEAL_GNOSIS) == getCabalHighestScore()", "Blacksmith global spawn predicate changed.");
		assertContains(sevenSigns, "getSealOwner(SEAL_AVARICE) == getCabalHighestScore()", "Merchant global spawn predicate changed.");
		PhantomAssertions.assertFalse(service.contains("spawnSevenSignsNPC") || service.contains("AutoSpawnHandler") || service.contains("MultisellData"), "Personal policy can mutate Mammon spawn or economy.");
		context.record("qol005.census", "changed=12 player-facing gates;rift continuation=NO_CHANGE_REQUIRED_ALREADY_COMPATIBLE;mammon spawn=OUT_OF_SCOPE_GLOBAL_WORLD_STATE");
	}

	private static void awaitPopulation(DimensionalRiftRoom room, String label) throws InterruptedException
	{
		final long deadline = System.nanoTime() + 2_000_000_000L;
		while ((System.nanoTime() < deadline) && (room.getSpawns().isEmpty() || room.getSpawns().stream().anyMatch(spawn -> spawn.getLastSpawn() == null)))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(!room.getSpawns().isEmpty() && room.getSpawns().stream().allMatch(spawn -> (spawn.getLastSpawn() != null) && spawn.isRespawnEnabled()), "Rift " + label + " combat room did not receive its full normal population with respawn enabled.");
	}

	private Settings allowlisted()
	{
		return new Settings(true, false, false, true, Set.of(_player.getObjectId()), Set.of(), true, "L2-QOL-005 test configuration.");
	}

	private Settings install(Settings settings)
	{
		return PersonalCharacterQoLService.getInstance().installForTests(settings);
	}

	private Path write(String name, String content) throws Exception
	{
		final Path path = _fixtures.resolve(name);
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private static String config(String master, String sevenSigns, String ids, String accounts)
	{
		return "EnablePersonalCharacterQoL=" + master + "\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\n" + (sevenSigns == null ? "" : "EnablePersonalSevenSignsAccess=" + sevenSigns + "\n") + "AllowedCharacterIds=" + ids + "\nAllowedAccounts=" + accounts + "\n";
	}

	private static String source(PhantomTestContext context, String relativePath) throws Exception
	{
		return Files.readString(context.moduleRoot().resolve(relativePath), StandardCharsets.UTF_8);
	}

	private static void assertContains(String source, String token, String message)
	{
		PhantomAssertions.assertTrue(source.contains(token), message);
	}

	private static int occurrences(String source, String token)
	{
		int result = 0;
		int offset = 0;
		while ((offset = source.indexOf(token, offset)) >= 0)
		{
			result++;
			offset += token.length();
		}
		return result;
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if ((_party != null) && _party.isInDimensionalRift())
			{
				final Player leader = _party.getLeader();
				_party.getDimensionalRift().manualExitRift(leader, _riftNpc);
			}
			if ((_player != null) && _player.isInParty())
			{
				_player.getParty().disbandParty();
			}
			if ((_observer != null) && _observer.isInParty())
			{
				_observer.getParty().disbandParty();
			}
			if (_player != null)
			{
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
			if (_observer != null)
			{
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
			if (_riftNpc != null)
			{
				_riftNpc.deleteMe();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			if (_riftSpawnDelayChanged)
			{
				GeneralConfig.RIFT_SPAWN_DELAY = _previousRiftSpawnDelay;
				_riftSpawnDelayChanged = false;
			}
			install(_previousSettings);
			_environment.shutdown();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}
}
