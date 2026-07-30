/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.holders.player.Shortcut;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.item.holders.InitialEquipment;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.ClassEntry;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.ScheduleTemplate;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.CreationStage;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Durable population shell and canonical level-one creation saga. It never
 * creates a client, dispatches a client event or places a character in World.
 */
public final class PhantomPopulationStore
{
	private static final int MANAGED_PAGE_SIZE = 256;
	private static final int DISABLED_ACCOUNT_ACCESS_LEVEL = -1;
	private static final int MAX_NAME_ATTEMPTS = 8;

	private final PhantomProfileRepository _profiles;
	private final PhantomPopulationCatalog _catalog;
	private final PhantomPopulationStateCodec _codec;
	private final SecureRandom _secureRandom;

	public PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog)
	{
		this(profiles, catalog, new PhantomPopulationStateCodec(), new SecureRandom());
	}

	PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog, PhantomPopulationStateCodec codec, SecureRandom secureRandom)
	{
		_profiles = Objects.requireNonNull(profiles, "Profile repository must not be null.");
		_catalog = Objects.requireNonNull(catalog, "Population catalog must not be null.");
		_codec = Objects.requireNonNull(codec, "Population codec must not be null.");
		_secureRandom = Objects.requireNonNull(secureRandom, "Secure random must not be null.");
	}

	public List<ManagedSnapshot> loadManagedAfter(long exclusiveProfileId, int pageSize)
	{
		if ((exclusiveProfileId < 0) || (pageSize < 1) || (pageSize > MANAGED_PAGE_SIZE))
		{
			throw new IllegalArgumentException("Managed population page request is outside bounded limits.");
		}
		return _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, exclusiveProfileId, pageSize).stream().map(this::decode).toList();
	}

	public ManagedSnapshot createShell(long generation, long creationOrdinal, long deterministicSeed)
	{
		return decode(_profiles.createWithComponent(PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, profileId ->
		{
			final long identitySeed = mix(deterministicSeed, profileId);
			final ClassEntry classEntry = _catalog.chooseClass(identitySeed);
			final boolean female = classEntry.sex().female(identitySeed >>> 7);
			final ScheduleTemplate schedule = _catalog.chooseSchedule(identitySeed >>> 13);
			final PlayerTemplate template = requireTemplate(classEntry.classId());
			final Location creationLocation = PlayerCreationInitializer.resolveCreationLocation(template);
			final int maximumPhase = schedule.maximumPhaseMinutes();
			final int phase = maximumPhase == 0 ? 0 : (int) Math.floorMod(identitySeed >>> 23, (maximumPhase * 2L) + 1L) - maximumPhase;
			final byte[] tokenBytes = new byte[32];
			_secureRandom.nextBytes(tokenBytes);
			final PhantomPopulationState state = new PhantomPopulationState(
				State.SHELL,
				generation,
				creationOrdinal,
				_catalog.hash(),
				deterministicSeed,
				0,
				reservedAccount(profileId),
				Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes),
				_catalog.name(identitySeed, 0),
				classEntry.classId(),
				female,
				(int) Math.floorMod(identitySeed >>> 31, 3),
				(int) Math.floorMod(identitySeed >>> 37, 4),
				(int) Math.floorMod(identitySeed >>> 43, female ? 7 : 5),
				schedule.id(),
				phase,
				MapRegionData.getInstance().getMapRegionLocId(creationLocation.getX(), creationLocation.getY()),
				creationLocation.getX(),
				creationLocation.getY(),
				creationLocation.getZ(),
				null,
				null,
				CreationStage.SHELL_DURABLE,
				"",
				"");
			return _codec.encode(state);
		}));
	}

	public ManagedSnapshot reload(long profileId)
	{
		final PhantomProfile profile = _profiles.find(profileId).orElseThrow(() -> new IllegalStateException("Managed profile disappeared."));
		final PhantomProfileComponent component = _profiles.findComponent(profileId, PhantomPopulationState.COMPONENT_TYPE).orElseThrow(() -> new IllegalStateException("Managed population state disappeared."));
		return decode(new ManagedProfile(profile, component));
	}

	public CreationResult advanceCreation(ManagedSnapshot current)
	{
		try
		{
			return switch (current.state().state())
			{
				case SHELL -> prepareAccount(current);
				case ACCOUNT_PREPARED -> prepareCharacter(current);
				case CHARACTER_PRESENT -> initializeCharacter(current);
				case INITIALIZING -> verifyAndLink(current);
				case READY -> new CreationResult(CreationOutcome.READY, current);
				case RETIRE_REQUESTED, RETIRED -> new CreationResult(CreationOutcome.NOT_PENDING, current);
				case INCONSISTENT -> new CreationResult(CreationOutcome.INCONSISTENT, current);
			};
		}
		catch (RuntimeException e)
		{
			return inconsistentSafely(current, typedFailure(e));
		}
	}

	public ManagedSnapshot updateState(ManagedSnapshot current, PhantomPopulationState next)
	{
		final PhantomProfileComponent component = _profiles.updateComponent(current.profile().profileId(), PhantomPopulationState.COMPONENT_TYPE, current.component().rowVersion(), PhantomPopulationState.SCHEMA_VERSION, _codec.encode(next));
		return new ManagedSnapshot(current.profile(), component, next);
	}

	private CreationResult prepareAccount(ManagedSnapshot current)
	{
		ManagedSnapshot snapshot = current;
		if (snapshot.state().creationStage() == CreationStage.SHELL_DURABLE)
		{
			snapshot = updateState(snapshot, snapshot.state().advance(State.ACCOUNT_PREPARED, CreationStage.ACCOUNT_INTENT));
		}
		final Optional<AccountRow> account = findAccount(snapshot.state().reservedAccount());
		if (account.isPresent())
		{
			if (!account.get().password().equals(snapshot.state().ownershipToken()) || (account.get().accessLevel() != DISABLED_ACCOUNT_ACCESS_LEVEL))
			{
				return inconsistent(snapshot, "account.ownership_mismatch");
			}
		}
		else
		{
			insertAccount(snapshot.state().reservedAccount(), snapshot.state().ownershipToken());
		}
		snapshot = updateState(snapshot, snapshot.state().advance(State.ACCOUNT_PREPARED, CreationStage.ACCOUNT_VERIFIED));
		return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
	}

	private CreationResult prepareCharacter(ManagedSnapshot current)
	{
		ManagedSnapshot snapshot = current;
		if (snapshot.state().creationStage() == CreationStage.ACCOUNT_INTENT)
		{
			return prepareAccount(snapshot);
		}
		if (snapshot.state().creationStage() == CreationStage.ACCOUNT_VERIFIED)
		{
			final CharacterLookup existing = findCharacter(snapshot.state());
			if (existing.conflict())
			{
				if (existing.accountCharacter() != null)
				{
					return inconsistent(snapshot, "character.account_conflict");
				}
				int attempt = snapshot.state().nameAttempt();
				do
				{
					attempt++;
					final String nextName = _catalog.name(mix(snapshot.state().deterministicSeed(), snapshot.profile().profileId()), attempt);
					if (!characterNameExists(nextName))
					{
						snapshot = updateState(snapshot, snapshot.state().withName(attempt, nextName));
						break;
					}
				}
				while (attempt < MAX_NAME_ATTEMPTS);
				if (characterNameExists(snapshot.state().characterName()))
				{
					return inconsistent(snapshot, "character.name_attempts_exhausted");
				}
			}
			snapshot = updateState(snapshot, snapshot.state().advance(State.CHARACTER_PRESENT, CreationStage.CHARACTER_INTENT));
		}

		CharacterLookup lookup = findCharacter(snapshot.state());
		if (lookup.conflict())
		{
			return inconsistent(snapshot, "character.identity_mismatch");
		}
		CharacterRow row = lookup.exact();
		if (row == null)
		{
			final Player created;
			synchronized (CharInfoTable.getInstance())
			{
				lookup = findCharacter(snapshot.state());
				if (lookup.conflict())
				{
					return inconsistent(snapshot, "character.identity_race");
				}
				row = lookup.exact();
				if (row == null)
				{
					final PlayerTemplate template = requireTemplate(snapshot.state().classId());
					created = Player.create(template, snapshot.state().reservedAccount(), snapshot.state().characterName(), new PlayerAppearance((byte) snapshot.state().face(), (byte) snapshot.state().hairColor(), (byte) snapshot.state().hairStyle(), snapshot.state().female()));
					if (created == null)
					{
						throw new IllegalStateException("character.create_failed");
					}
					row = requireExactCharacter(snapshot.state());
				}
				else
				{
					created = null;
				}
			}
			if (created != null)
			{
				created.stopAllTasks();
			}
		}
		snapshot = updateState(snapshot, snapshot.state().withExpectedCharacter(row.objectId()).advance(State.CHARACTER_PRESENT, CreationStage.CHARACTER_CREATED));
		return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
	}

	private CreationResult initializeCharacter(ManagedSnapshot current)
	{
		ManagedSnapshot snapshot = current;
		final CharacterRow row = requireExactCharacter(snapshot.state());
		if ((snapshot.state().expectedCharacterObjectId() != null) && (snapshot.state().expectedCharacterObjectId() != row.objectId()))
		{
			return inconsistent(snapshot, "character.object_id_mismatch");
		}
		if (snapshot.state().creationStage() == CreationStage.CHARACTER_INTENT)
		{
			snapshot = updateState(snapshot, snapshot.state().withExpectedCharacter(row.objectId()).advance(State.CHARACTER_PRESENT, CreationStage.CHARACTER_CREATED));
		}
		snapshot = updateState(snapshot, snapshot.state().advance(State.INITIALIZING, CreationStage.INITIALIZATION_INTENT));
		if (isPristine(row.objectId()))
		{
			final Player player = Player.load(row.objectId());
			if (player == null)
			{
				throw new IllegalStateException("character.load_for_initialization_failed");
			}
			PlayerAutoSaveTaskManager.getInstance().remove(player);
			String expectedHash = "";
			try
			{
				if (!matchesIdentity(player, snapshot.state()))
				{
					return inconsistent(snapshot, "character.runtime_identity_mismatch");
				}
				PlayerCreationInitializer.initialize(player, Mode.POPULATION, new Location(snapshot.state().creationX(), snapshot.state().creationY(), snapshot.state().creationZ()));
				player.stopAllTasks();
				player.storeMe();
				expectedHash = initializationHash(player);
			}
			finally
			{
				cleanupLoaded(player);
			}
			final Player verified = Player.load(row.objectId());
			if (verified == null)
			{
				throw new IllegalStateException("character.fresh_load_failed");
			}
			PlayerAutoSaveTaskManager.getInstance().remove(verified);
			try
			{
				final String verificationFailure = initializationFailure(verified, snapshot.state());
				if (verificationFailure != null)
				{
					return inconsistent(snapshot, "character.fresh_" + verificationFailure);
				}
				if (!expectedHash.equals(initializationHash(verified)))
				{
					return inconsistent(snapshot, "character.fresh_hash_mismatch");
				}
			}
			finally
			{
				cleanupLoaded(verified);
			}
			snapshot = updateState(snapshot, snapshot.state().initialized(row.objectId(), expectedHash));
			return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
		}

		final Player existing = Player.load(row.objectId());
		if (existing == null)
		{
			throw new IllegalStateException("character.restart_load_failed");
		}
		PlayerAutoSaveTaskManager.getInstance().remove(existing);
		try
		{
			final String verificationFailure = initializationFailure(existing, snapshot.state());
			if (verificationFailure != null)
			{
				return inconsistent(snapshot, "character.partial_" + verificationFailure);
			}
			snapshot = updateState(snapshot, snapshot.state().initialized(row.objectId(), initializationHash(existing)));
			return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
		}
		finally
		{
			cleanupLoaded(existing);
		}
	}

	private CreationResult verifyAndLink(ManagedSnapshot current)
	{
		ManagedSnapshot snapshot = current;
		final int objectId = Objects.requireNonNull(snapshot.state().actualCharacterObjectId(), "Verified character object ID is absent.");
		final Player player = Player.load(objectId);
		if (player == null)
		{
			throw new IllegalStateException("character.final_load_failed");
		}
		PlayerAutoSaveTaskManager.getInstance().remove(player);
		try
		{
			final String verificationFailure = initializationFailure(player, snapshot.state());
			if (verificationFailure != null)
			{
				return inconsistent(snapshot, "character.final_" + verificationFailure);
			}
			if (!snapshot.state().initializationHash().equals(initializationHash(player)))
			{
				return inconsistent(snapshot, "character.final_hash_mismatch");
			}
		}
		finally
		{
			cleanupLoaded(player);
		}

		PhantomProfile profile = snapshot.profile();
		if (profile.characterObjectId() == null)
		{
			profile = _profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), objectId);
			snapshot = new ManagedSnapshot(profile, snapshot.component(), snapshot.state());
		}
		else if (profile.characterObjectId() != objectId)
		{
			return inconsistent(snapshot, "profile.link_mismatch");
		}
		snapshot = updateState(snapshot, snapshot.state().ready());
		return new CreationResult(CreationOutcome.READY, snapshot);
	}

	private String initializationFailure(Player player, PhantomPopulationState state)
	{
		if (!matchesIdentity(player, state))
		{
			return "identity_mismatch";
		}
		if (player.getLevel() != 1)
		{
			return "level_mismatch";
		}
		if (player.getSp() != 0)
		{
			return "sp_mismatch";
		}
		if (Math.abs(player.getCurrentHp() - player.getMaxHp()) > 0.01)
		{
			return "hp_mismatch";
		}
		if (Math.abs(player.getCurrentMp() - player.getMaxMp()) > 0.01)
		{
			return "mp_mismatch";
		}
		if (Math.abs(player.getCurrentCp()) > 0.01)
		{
			return "cp_mismatch";
		}
		if ((player.getX() != state.creationX()) || (player.getY() != state.creationY()))
		{
			return "position_xy_mismatch";
		}
		if (player.getZ() != state.creationZ())
		{
			return "position_z_mismatch";
		}
		if (player.getAdena() != PlayerConfig.STARTING_ADENA)
		{
			return "adena_mismatch";
		}
		final List<InitialEquipment> equipment = Optional.ofNullable(InitialEquipmentData.getInstance().getClassEquipment(player.getPlayerClass())).map(ArrayList::new).orElseGet(ArrayList::new);
		for (InitialEquipment expectedItem : equipment)
		{
			long count = 0;
			for (Item item : player.getInventory().getAllItemsByItemId(expectedItem.getId()))
			{
				count += item.getCount();
			}
			if (count != expectedItem.getCount())
			{
				return "equipment_mismatch";
			}
		}
		if (!SkillTreeData.getInstance().getAvailableSkills(player, player.getPlayerClass(), false, true).isEmpty())
		{
			return "skills_mismatch";
		}
		if (player.getAllShortcuts().isEmpty())
		{
			return "shortcuts_missing";
		}
		return null;
	}

	private static boolean matchesIdentity(Player player, PhantomPopulationState state)
	{
		return player.getAccountName().equals(state.reservedAccount()) //
			&& player.getName().equals(state.characterName()) //
			&& (player.getPlayerClass().getId() == state.classId()) //
			&& (player.getAppearance().isFemale() == state.female()) //
			&& (player.getAppearance().getFace() == state.face()) //
			&& (player.getAppearance().getHairColor() == state.hairColor()) //
			&& (player.getAppearance().getHairStyle() == state.hairStyle());
	}

	private static String initializationHash(Player player)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(2048);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(player.getLevel());
				output.writeLong(player.getSp());
				output.writeInt(player.getPlayerClass().getId());
				output.writeInt(player.getX());
				output.writeInt(player.getY());
				output.writeInt(player.getZ());
				output.writeLong(Double.doubleToLongBits(player.getCurrentHp()));
				output.writeLong(Double.doubleToLongBits(player.getCurrentMp()));
				output.writeLong(Double.doubleToLongBits(player.getCurrentCp()));
				output.writeLong(player.getAdena());
				final List<Item> items = new ArrayList<>(player.getInventory().getItems());
				items.sort(Comparator.comparingInt(Item::getId).thenComparingInt(Item::getObjectId));
				output.writeInt(items.size());
				for (Item item : items)
				{
					output.writeInt(item.getObjectId());
					output.writeInt(item.getId());
					output.writeLong(item.getCount());
					output.writeBoolean(item.isEquipped());
				}
				final List<Skill> skills = new ArrayList<>(player.getSkills().values());
				skills.sort(Comparator.comparingInt(Skill::getId).thenComparingInt(Skill::getLevel));
				output.writeInt(skills.size());
				for (Skill skill : skills)
				{
					output.writeInt(skill.getId());
					output.writeInt(skill.getLevel());
				}
				final List<Shortcut> shortcuts = new ArrayList<>(player.getAllShortcuts());
				shortcuts.sort(Comparator.comparingInt(Shortcut::getPage).thenComparingInt(Shortcut::getSlot));
				output.writeInt(shortcuts.size());
				for (Shortcut shortcut : shortcuts)
				{
					output.writeInt(shortcut.getPage());
					output.writeInt(shortcut.getSlot());
					output.writeInt(shortcut.getType().ordinal());
					output.writeInt(shortcut.getId());
					output.writeInt(shortcut.getLevel());
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Could not compute canonical population initialization hash.", e);
		}
	}

	private static void cleanupLoaded(Player player)
	{
		PlayerAutoSaveTaskManager.getInstance().remove(player);
		player.stopAllTasks();
		player.storeMe();
		player.deleteMe();
	}

	private static boolean isPristine(int objectId)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM items WHERE owner_id = ?) + (SELECT COUNT(*) FROM character_skills WHERE charId = ?) + (SELECT COUNT(*) FROM character_shortcuts WHERE charId = ?)"))
		{
			statement.setInt(1, objectId);
			statement.setInt(2, objectId);
			statement.setInt(3, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() && (result.getLong(1) == 0);
			}
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not inspect pristine population character.", e);
		}
	}

	private static Optional<AccountRow> findAccount(String account)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT password, accessLevel FROM accounts WHERE login = ?"))
		{
			statement.setString(1, account);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return Optional.empty();
				}
				final AccountRow row = new AccountRow(result.getString("password"), result.getInt("accessLevel"));
				if (result.next())
				{
					throw new IllegalStateException("Reserved population account lookup returned duplicate rows.");
				}
				return Optional.of(row);
			}
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not inspect reserved population account.", e);
		}
	}

	private static void insertAccount(String account, String token)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (login, password, accessLevel) VALUES (?, ?, ?)"))
		{
			statement.setString(1, account);
			statement.setString(2, token);
			statement.setInt(3, DISABLED_ACCOUNT_ACCESS_LEVEL);
			if (statement.executeUpdate() != 1)
			{
				throw new IllegalStateException("Reserved population account insert did not affect one row.");
			}
		}
		catch (SQLException e)
		{
			final Optional<AccountRow> concurrent = findAccount(account);
			if (concurrent.isEmpty() || !concurrent.get().password().equals(token) || (concurrent.get().accessLevel() != DISABLED_ACCOUNT_ACCESS_LEVEL))
			{
				throw new IllegalStateException("Could not create owned reserved population account.", e);
			}
		}
	}

	private static CharacterLookup findCharacter(PhantomPopulationState state)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT charId, char_name, account_name, base_class, sex, face, hairColor, hairStyle, online FROM characters WHERE account_name = ? OR char_name = ? ORDER BY charId"))
		{
			statement.setString(1, state.reservedAccount());
			statement.setString(2, state.characterName());
			CharacterRow exact = null;
			CharacterRow accountCharacter = null;
			boolean conflict = false;
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final CharacterRow row = new CharacterRow(result.getInt("charId"), result.getString("char_name"), result.getString("account_name"), result.getInt("base_class"), result.getInt("sex") != 0, result.getInt("face"), result.getInt("hairColor"), result.getInt("hairStyle"), result.getInt("online"));
					if (row.account().equals(state.reservedAccount()))
					{
						accountCharacter = row;
					}
					if (row.matches(state))
					{
						if (exact != null)
						{
							conflict = true;
						}
						exact = row;
					}
					else
					{
						conflict = true;
					}
				}
			}
			return new CharacterLookup(exact, accountCharacter, conflict);
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not inspect reserved population character.", e);
		}
	}

	private static CharacterRow requireExactCharacter(PhantomPopulationState state)
	{
		final CharacterLookup lookup = findCharacter(state);
		if (lookup.conflict() || (lookup.exact() == null))
		{
			throw new IllegalStateException("character.exact_identity_absent");
		}
		return lookup.exact();
	}

	private static boolean characterNameExists(String name)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM characters WHERE char_name = ? LIMIT 1"))
		{
			statement.setString(1, name);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next();
			}
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not inspect population character name.", e);
		}
	}

	private CreationResult inconsistent(ManagedSnapshot current, String failure)
	{
		final ManagedSnapshot snapshot = updateState(current, current.state().fail(failure));
		return new CreationResult(CreationOutcome.INCONSISTENT, snapshot);
	}

	private CreationResult inconsistentSafely(ManagedSnapshot current, String failure)
	{
		try
		{
			return inconsistent(current, failure);
		}
		catch (RuntimeException persistenceFailure)
		{
			return new CreationResult(CreationOutcome.RETRY, current);
		}
	}

	private ManagedSnapshot decode(ManagedProfile managed)
	{
		if (managed.component().componentSchemaVersion() != PhantomPopulationState.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown population.state component schema version.");
		}
		return new ManagedSnapshot(managed.profile(), managed.component(), _codec.decode(managed.component().payload()));
	}

	private static PlayerTemplate requireTemplate(int classId)
	{
		final PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(classId);
		if (template == null)
		{
			throw new IllegalArgumentException("Population starting class has no Player template.");
		}
		return template;
	}

	private static String reservedAccount(long profileId)
	{
		final String account = "p" + Long.toString(profileId, 36);
		if (account.length() > 14)
		{
			throw new IllegalArgumentException("Managed profile ID cannot fit reserved account namespace.");
		}
		return account;
	}

	private static long mix(long seed, long value)
	{
		long mixed = seed ^ (value + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	private static String typedFailure(RuntimeException exception)
	{
		final String message = exception.getMessage();
		if ((message != null) && message.matches("[a-z0-9_.-]{1,96}"))
		{
			return message;
		}
		return "population.persistence_or_runtime_failure";
	}

	public enum CreationOutcome
	{
		PROGRESSED,
		READY,
		INCONSISTENT,
		RETRY,
		NOT_PENDING
	}

	public record ManagedSnapshot(PhantomProfile profile, PhantomProfileComponent component, PhantomPopulationState state)
	{
	}

	public record CreationResult(CreationOutcome outcome, ManagedSnapshot snapshot)
	{
	}

	private record AccountRow(String password, int accessLevel)
	{
	}

	private record CharacterLookup(CharacterRow exact, CharacterRow accountCharacter, boolean conflict)
	{
	}

	private record CharacterRow(int objectId, String name, String account, int baseClass, boolean female, int face, int hairColor, int hairStyle, int online)
	{
		boolean matches(PhantomPopulationState state)
		{
			return name.equals(state.characterName()) //
				&& account.equals(state.reservedAccount()) //
				&& (baseClass == state.classId()) //
				&& (female == state.female()) //
				&& (face == state.face()) //
				&& (hairColor == state.hairColor()) //
				&& (hairStyle == state.hairStyle()) //
				&& (online == 0);
		}
	}
}
