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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.InitialPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.MacroPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.ShortcutKey;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.ShortcutPlan;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.CreationPlan;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.InitialItemPlan;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.PopulationInitializationObserver;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.holders.player.Shortcut;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.item.holders.InitialEquipment;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
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
public final class PhantomPopulationStore implements PhantomPopulationPersistencePort
{
	private static final int MANAGED_PAGE_SIZE = 256;
	private static final int DISABLED_ACCOUNT_ACCESS_LEVEL = -1;
	private static final int MAX_NAME_ATTEMPTS = 8;

	private final PhantomProfileRepository _profiles;
	private final PhantomPopulationCatalog _catalog;
	private final PhantomPopulationStateCodec _codec;
	private final SecureRandom _secureRandom;
	private final ZoneId _zoneId;
	private final FailureInjector _failureInjector;

	public PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog)
	{
		this(profiles, catalog, ZoneOffset.UTC);
	}

	public PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog, ZoneId zoneId)
	{
		this(profiles, catalog, zoneId, new PhantomPopulationStateCodec(), new SecureRandom(), FailureInjector.none());
	}

	public PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog, ZoneId zoneId, FailureInjector failureInjector)
	{
		this(profiles, catalog, zoneId, new PhantomPopulationStateCodec(), new SecureRandom(), failureInjector);
	}

	PhantomPopulationStore(PhantomProfileRepository profiles, PhantomPopulationCatalog catalog, ZoneId zoneId, PhantomPopulationStateCodec codec, SecureRandom secureRandom, FailureInjector failureInjector)
	{
		_profiles = Objects.requireNonNull(profiles, "Profile repository must not be null.");
		_catalog = Objects.requireNonNull(catalog, "Population catalog must not be null.");
		_zoneId = Objects.requireNonNull(zoneId, "Population time zone must not be null.");
		_codec = Objects.requireNonNull(codec, "Population codec must not be null.");
		_secureRandom = Objects.requireNonNull(secureRandom, "Secure random must not be null.");
		_failureInjector = Objects.requireNonNull(failureInjector, "Population failure injector must not be null.");
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
			final PopulationInitializationContract authority = PopulationInitializationContract.resolve(_catalog.hash(), _zoneId, classEntry.classId(), creationLocation);
			final int maximumPhase = schedule.maximumPhaseMinutes();
			final int phase = maximumPhase == 0 ? 0 : (int) Math.floorMod(identitySeed >>> 23, (maximumPhase * 2L) + 1L) - maximumPhase;
			final byte[] tokenBytes = new byte[32];
			_secureRandom.nextBytes(tokenBytes);
			final PhantomPopulationState state = new PhantomPopulationState(
				State.SHELL,
				generation,
				creationOrdinal,
				_catalog.hash(),
				authority.hash(),
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
				case INITIALIZING -> current.state().creationStage() == CreationStage.VERIFIED ? verifyAndLink(current) : initializeCharacter(current);
				case READY -> new CreationResult(CreationOutcome.READY, current);
				case RETIRE_REQUESTED, RETIRED -> new CreationResult(CreationOutcome.NOT_PENDING, current);
				case INCONSISTENT -> new CreationResult(CreationOutcome.INCONSISTENT, current);
			};
		}
		catch (FaultInjectedException e)
		{
			final ManagedSnapshot reloaded = reload(current.profile().profileId());
			return new CreationResult(reloaded.state().state() == State.READY ? CreationOutcome.READY : CreationOutcome.RETRY, reloaded);
		}
		catch (RuntimeException e)
		{
			return inconsistentSafely(reload(current.profile().profileId()), typedFailure(e));
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
		final boolean initializationStored = !snapshot.state().initializationHash().isEmpty();
		final boolean resumeStoredInitialization = snapshot.state().creationStage() == CreationStage.INITIALIZATION_STORED;
		final CharacterRow row = requireExactCharacter(snapshot.state());
		if ((snapshot.state().expectedCharacterObjectId() != null) && (snapshot.state().expectedCharacterObjectId() != row.objectId()))
		{
			return inconsistent(snapshot, "character.object_id_mismatch");
		}
		if (snapshot.state().creationStage() == CreationStage.CHARACTER_INTENT)
		{
			snapshot = updateState(snapshot, snapshot.state().withExpectedCharacter(row.objectId()).advance(State.CHARACTER_PRESENT, CreationStage.CHARACTER_CREATED));
		}
		if (!resumeStoredInitialization)
		{
			snapshot = updateState(snapshot, snapshot.state().advance(State.INITIALIZING, CreationStage.INITIALIZATION_INTENT));
		}
		final PopulationInitializationContract authority = validateAuthority(snapshot);
		ProjectionInspection inspection = inspectProjection(row.objectId(), snapshot.state(), authority);
		if (inspection.status() == ProjectionStatus.INCONSISTENT)
		{
			return inconsistent(snapshot, inspection.failure());
		}
		if (resumeStoredInitialization)
		{
			if ((snapshot.state().actualCharacterObjectId() == null) || (snapshot.state().actualCharacterObjectId() != row.objectId()) || (inspection.status() != ProjectionStatus.CANONICAL) || !snapshot.state().initializationHash().equals(inspection.projection().hash()))
			{
				return inconsistent(snapshot, "character.stored_projection_mismatch");
			}
			final String resumedVerificationFailure = verifyReadOnly(snapshot.profile().profileId(), row.objectId(), snapshot.state(), authority, inspection.projection(), "fresh");
			if (resumedVerificationFailure != null)
			{
				return inconsistent(snapshot, resumedVerificationFailure);
			}
			snapshot = updateState(snapshot, snapshot.state().initialized(row.objectId(), inspection.projection().hash()));
			return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
		}
		if (!inspection.missingSkills().isEmpty())
		{
			insertMissingSkills(row.objectId(), inspection.missingSkills());
			_failureInjector.after(FaultPoint.SKILLS, snapshot.profile().profileId(), inspection.missingSkills().size());
			inspection = inspectProjection(row.objectId(), snapshot.state(), authority);
			if (inspection.status() == ProjectionStatus.INCONSISTENT)
			{
				return inconsistent(snapshot, inspection.failure());
			}
		}
		if (inspection.status() != ProjectionStatus.CANONICAL)
		{
			PlayerCreationInitializer.preparePopulationCharacterRow(row.objectId(), repairPlan(authority, inspection));
			inspection = inspectProjection(row.objectId(), snapshot.state(), authority);
			if (inspection.status() == ProjectionStatus.INCONSISTENT)
			{
				return inconsistent(snapshot, inspection.failure());
			}
			final byte[] beforeLoad = inspection.projection().canonicalBytes();
			try (PlayerAutoSaveTaskManager.PopulationLoadSuppression ignored = PlayerAutoSaveTaskManager.suppressPopulationLoad(row.objectId()))
			{
				final Player repairPlayer = Player.load(row.objectId());
				if (repairPlayer == null)
				{
					throw new IllegalStateException("character.load_for_initialization_failed");
				}
				assertAutosaveSuppressed(row.objectId());
				try
				{
					if (!java.util.Arrays.equals(beforeLoad, inspectProjection(row.objectId(), snapshot.state(), authority).projection().canonicalBytes()))
					{
						return inconsistent(snapshot, "character.load_mutated_projection");
					}
					if (!matchesIdentity(repairPlayer, snapshot.state()))
					{
						return inconsistent(snapshot, "character.runtime_identity_mismatch");
					}
					final CreationPlan repairPlan = repairPlan(authority, inspection);
					PlayerCreationInitializer.initializePopulation(repairPlayer, repairPlan, inspection.existingMacroIds(), new StoreInitializationObserver(snapshot.profile().profileId()));
				}
				finally
				{
					cleanupLoadedReadOnly(repairPlayer);
				}
			}
		}
		if (!initializationStored)
		{
			try (PlayerAutoSaveTaskManager.PopulationLoadSuppression ignored = PlayerAutoSaveTaskManager.suppressPopulationLoad(row.objectId()))
			{
				final Player storePlayer = Player.load(row.objectId());
				if (storePlayer == null)
				{
					throw new IllegalStateException("character.reload_for_initialization_store_failed");
				}
				assertAutosaveSuppressed(row.objectId());
				try
				{
					final CreationPlan finalizationPlan = new CreationPlan(
						Mode.POPULATION,
						authority.level(),
						authority.sp(),
						0,
						new Location(authority.creationX(), authority.creationY(), authority.creationZ()),
						authority.title(),
						authority.vitalityEnabled(),
						authority.vitalityPoints(),
						authority.configuredStartingLevel(),
						authority.configuredStartingSp(),
						List.of(),
						List.of(),
						new InitialPlan(List.of(), List.of()));
					PlayerCreationInitializer.initializePopulation(storePlayer, finalizationPlan, Set.of(), PopulationInitializationObserver.noop());
					storePlayer.stopAllTasks();
					storePlayer.storeMe();
				}
				finally
				{
					cleanupLoadedReadOnly(storePlayer);
				}
			}
		}
		final ProjectionInspection canonical = inspectProjection(row.objectId(), snapshot.state(), authority);
		if (canonical.status() != ProjectionStatus.CANONICAL)
		{
			return inconsistent(snapshot, canonical.failure());
		}
		if (!initializationStored)
		{
			snapshot = updateState(snapshot, snapshot.state().initializationStored(row.objectId(), canonical.projection().hash()));
			_failureInjector.after(FaultPoint.CHARACTER_STORE, snapshot.profile().profileId(), 0);
		}
		final String verificationFailure = verifyReadOnly(snapshot.profile().profileId(), row.objectId(), snapshot.state(), authority, canonical.projection(), "fresh");
		if (verificationFailure != null)
		{
			return inconsistent(snapshot, verificationFailure);
		}
		snapshot = updateState(snapshot, snapshot.state().initialized(row.objectId(), canonical.projection().hash()));
		return new CreationResult(CreationOutcome.PROGRESSED, snapshot);
	}

	private CreationResult verifyAndLink(ManagedSnapshot current)
	{
		ManagedSnapshot snapshot = current;
		final int objectId = Objects.requireNonNull(snapshot.state().actualCharacterObjectId(), "Verified character object ID is absent.");
		final PopulationInitializationContract authority = validateAuthority(snapshot);
		final ProjectionInspection canonical = inspectProjection(objectId, snapshot.state(), authority);
		if (canonical.status() == ProjectionStatus.INCONSISTENT)
		{
			return inconsistent(snapshot, canonical.failure());
		}
		if (canonical.status() != ProjectionStatus.CANONICAL)
		{
			return initializeCharacter(snapshot);
		}
		final String verificationFailure = verifyReadOnly(snapshot.profile().profileId(), objectId, snapshot.state(), authority, canonical.projection(), "final");
		if (verificationFailure != null)
		{
			return inconsistent(snapshot, verificationFailure);
		}
		if (!snapshot.state().initializationHash().equals(canonical.projection().hash()))
		{
			return inconsistent(snapshot, "character.final_hash_mismatch");
		}

		PhantomProfile profile = snapshot.profile();
		if (profile.characterObjectId() == null)
		{
			profile = _profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), objectId);
			snapshot = new ManagedSnapshot(profile, snapshot.component(), snapshot.state());
			_failureInjector.after(FaultPoint.PROFILE_LINK, snapshot.profile().profileId(), 0);
		}
		else if (profile.characterObjectId() != objectId)
		{
			return inconsistent(snapshot, "profile.link_mismatch");
		}
		snapshot = updateState(snapshot, snapshot.state().ready());
		_failureInjector.after(FaultPoint.READY_COMPONENT_UPDATE, snapshot.profile().profileId(), 0);
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

	private String verifyReadOnly(long profileId, int objectId, PhantomPopulationState state, PopulationInitializationContract authority, DurableProjection expected, String phase)
	{
		final byte[] before = expected.canonicalBytes();
		try (PlayerAutoSaveTaskManager.PopulationLoadSuppression ignored = PlayerAutoSaveTaskManager.suppressPopulationLoad(objectId))
		{
			final Player player = Player.load(objectId);
			if (player == null)
			{
				return "character." + phase + "_load_failed";
			}
			assertAutosaveSuppressed(objectId);
			try
			{
				final String failure = initializationFailure(player, state);
				if (failure != null)
				{
					return "character." + phase + "_" + failure;
				}
				if ("fresh".equals(phase))
				{
					_failureInjector.after(FaultPoint.FRESH_VERIFICATION, profileId, 0);
				}
			}
			finally
			{
				cleanupLoadedReadOnly(player);
			}
		}
		final ProjectionInspection after = inspectProjection(objectId, state, authority);
		if ((after.status() != ProjectionStatus.CANONICAL) || !java.util.Arrays.equals(before, after.projection().canonicalBytes()))
		{
			return "character." + phase + "_verification_wrote_state";
		}
		return null;
	}

	private static void assertAutosaveSuppressed(int objectId)
	{
		if (!PlayerAutoSaveTaskManager.isPopulationLoadSuppressed(objectId) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId))
		{
			throw new IllegalStateException("character.autosave_suppression_failed");
		}
	}

	private static void cleanupLoadedReadOnly(Player player)
	{
		PlayerAutoSaveTaskManager.getInstance().remove(player);
		player.stopAllTasks();
		player.deleteMe();
	}

	private CreationPlan repairPlan(PopulationInitializationContract authority, ProjectionInspection inspection)
	{
		final List<InitialItemPlan> missingEquipment = new ArrayList<>();
		long missingAdena = 0;
		for (PopulationInitializationContract.ItemFact expected : authority.items())
		{
			final long missing = inspection.missingItems().getOrDefault(new ExpectedItemKey(expected.itemId(), expected.equipped()), 0L);
			if (expected.itemId() == Inventory.ADENA_ID)
			{
				missingAdena = Math.addExact(missingAdena, missing);
			}
			else if (missing > 0)
			{
				missingEquipment.add(new InitialItemPlan(expected.itemId(), missing, expected.equipped()));
			}
		}
		final Set<Integer> requiredMacroPlans = new HashSet<>(inspection.missingMacroIds());
		for (ShortcutPlan shortcut : authority.initialPlan().shortcuts())
		{
			if (inspection.missingShortcutKeys().contains(shortcut.key()) && (shortcut.type() == org.l2jmobius.gameserver.model.actor.enums.player.ShortcutType.MACRO))
			{
				requiredMacroPlans.add(shortcut.logicalId());
			}
		}
		return new CreationPlan(
			Mode.POPULATION,
			authority.level(),
			authority.sp(),
			missingAdena,
			new Location(authority.creationX(), authority.creationY(), authority.creationZ()),
			authority.title(),
			authority.vitalityEnabled(),
			authority.vitalityPoints(),
			authority.configuredStartingLevel(),
			authority.configuredStartingSp(),
			missingEquipment,
			List.of(),
			authority.initialPlan().subset(inspection.missingShortcutKeys(), requiredMacroPlans));
	}

	private static void insertMissingSkills(int objectId, Set<PopulationInitializationContract.SkillFact> missing)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,0)"))
		{
			for (PopulationInitializationContract.SkillFact skill : missing)
			{
				statement.setInt(1, objectId);
				statement.setInt(2, skill.skillId());
				statement.setInt(3, skill.skillLevel());
				if (statement.executeUpdate() != 1)
				{
					throw new IllegalStateException("Population initial skill insert did not affect one row.");
				}
			}
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not insert missing canonical population skills.", e);
		}
	}

	private ProjectionInspection inspectProjection(int objectId, PhantomPopulationState state, PopulationInitializationContract authority)
	{
		final DurableProjection projection = loadProjection(objectId);
		final CharacterProjection character = projection.character();
		if ((character == null) || !character.matchesIdentity(state) || (character.online() != 0) || (character.level() != authority.level()) || (character.sp() != authority.sp()) || (character.exp() != 0))
		{
			return ProjectionInspection.inconsistent(projection, "character.projection_identity_or_level");
		}
		final boolean pristineCharacter = (character.curHp() == 0) && (character.curMp() == 0) && (character.curCp() == 0) && character.title().isEmpty();
		final boolean canonicalCharacter = (character.maxHp() > 0) && (character.curHp() == character.maxHp()) && (character.maxMp() > 0) && (character.curMp() == character.maxMp()) && (character.curCp() == 0) && (character.x() == authority.creationX()) && (character.y() == authority.creationY()) && (character.z() == authority.creationZ()) && character.title().equals(authority.title()) && (character.vitalityPoints() == authority.vitalityPoints());
		if (!pristineCharacter && !canonicalCharacter)
		{
			return ProjectionInspection.inconsistent(projection, "character.projection_properties");
		}

		final Map<ExpectedItemKey, Long> expectedItems = new LinkedHashMap<>();
		for (PopulationInitializationContract.ItemFact item : authority.items())
		{
			expectedItems.put(new ExpectedItemKey(item.itemId(), item.equipped()), item.count());
		}
		final Map<ExpectedItemKey, Long> actualItems = new LinkedHashMap<>();
		final Map<Integer, ItemProjection> itemsByObjectId = new HashMap<>();
		for (ItemProjection item : projection.items())
		{
			if ((item.objectId() <= 0) || (item.count() <= 0) || (itemsByObjectId.put(item.objectId(), item) != null))
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_item_identity");
			}
			final boolean equipped;
			if ("PAPERDOLL".equals(item.location()))
			{
				equipped = true;
			}
			else if ("INVENTORY".equals(item.location()))
			{
				equipped = false;
			}
			else
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_item_location");
			}
			final ExpectedItemKey key = new ExpectedItemKey(item.itemId(), equipped);
			final Long maximum = expectedItems.get(key);
			if (maximum == null)
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_extra_item");
			}
			final long accumulated = actualItems.merge(key, item.count(), Math::addExact);
			if (accumulated > maximum)
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_item_excess");
			}
		}
		final Map<ExpectedItemKey, Long> missingItems = new LinkedHashMap<>();
		for (Map.Entry<ExpectedItemKey, Long> expected : expectedItems.entrySet())
		{
			final long missing = expected.getValue() - actualItems.getOrDefault(expected.getKey(), 0L);
			if (missing > 0)
			{
				missingItems.put(expected.getKey(), missing);
			}
		}

		final Set<PopulationInitializationContract.SkillFact> expectedSkills = Set.copyOf(authority.skills());
		final Set<PopulationInitializationContract.SkillFact> actualSkills = new HashSet<>();
		for (SkillProjection skill : projection.skills())
		{
			final PopulationInitializationContract.SkillFact fact = new PopulationInitializationContract.SkillFact(skill.skillId(), skill.skillLevel());
			if ((skill.classIndex() != 0) || !expectedSkills.contains(fact) || !actualSkills.add(fact))
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_extra_skill");
			}
		}
		final Set<PopulationInitializationContract.SkillFact> missingSkills = new HashSet<>(expectedSkills);
		missingSkills.removeAll(actualSkills);

		final Map<ShortcutKey, ShortcutPlan> expectedShortcuts = new LinkedHashMap<>();
		for (ShortcutPlan shortcut : authority.initialPlan().shortcuts())
		{
			if (expectedShortcuts.put(shortcut.key(), shortcut) != null)
			{
				return ProjectionInspection.inconsistent(projection, "character.authority_duplicate_shortcut");
			}
		}
		final Set<ShortcutKey> actualShortcutKeys = new HashSet<>();
		for (ShortcutProjection shortcut : projection.shortcuts())
		{
			final ShortcutKey key = new ShortcutKey(shortcut.page(), shortcut.slot());
			final ShortcutPlan expected = expectedShortcuts.get(key);
			if ((expected == null) || !actualShortcutKeys.add(key) || (shortcut.classIndex() != 0) || (shortcut.type() != expected.type().ordinal()) || (shortcut.level() != expected.level()))
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_extra_shortcut");
			}
			if (expected.type() == org.l2jmobius.gameserver.model.actor.enums.player.ShortcutType.ITEM)
			{
				final ItemProjection owned = itemsByObjectId.get(shortcut.shortcutId());
				if ((owned == null) || (owned.itemId() != expected.logicalId()))
				{
					return ProjectionInspection.inconsistent(projection, "character.projection_item_shortcut_owner");
				}
			}
			else if (shortcut.shortcutId() != expected.logicalId())
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_shortcut_identity");
			}
		}
		final Set<ShortcutKey> missingShortcutKeys = new HashSet<>(expectedShortcuts.keySet());
		missingShortcutKeys.removeAll(actualShortcutKeys);

		final Map<Integer, MacroPlan> expectedMacros = new LinkedHashMap<>();
		authority.initialPlan().macros().forEach(macro -> expectedMacros.put(macro.id(), macro));
		final Set<Integer> existingMacroIds = new HashSet<>();
		for (MacroProjection macro : projection.macros())
		{
			final MacroPlan expected = expectedMacros.get(macro.id());
			if ((expected == null) || !existingMacroIds.add(macro.id()) || (macro.icon() != expected.icon()) || !macro.name().equals(expected.name()) || !macro.description().equals(expected.description()) || !macro.acronym().equals(expected.acronym()) || !macro.commands().equals(expected.serializedCommands()))
			{
				return ProjectionInspection.inconsistent(projection, "character.projection_extra_macro");
			}
		}
		final Set<Integer> missingMacroIds = new HashSet<>(expectedMacros.keySet());
		missingMacroIds.removeAll(existingMacroIds);

		final boolean complete = missingItems.isEmpty() && missingSkills.isEmpty() && missingShortcutKeys.isEmpty() && missingMacroIds.isEmpty();
		final ProjectionStatus status = canonicalCharacter && complete ? ProjectionStatus.CANONICAL : (pristineCharacter && projection.items().isEmpty() && projection.skills().isEmpty() && projection.shortcuts().isEmpty() && projection.macros().isEmpty() ? ProjectionStatus.PRISTINE : ProjectionStatus.STRICT_SUBSET);
		final String pending = status == ProjectionStatus.CANONICAL ? "" : (status == ProjectionStatus.PRISTINE ? "character.projection_pristine" : "character.projection_incomplete");
		return new ProjectionInspection(status, projection, pending, Map.copyOf(missingItems), Set.copyOf(missingSkills), Set.copyOf(missingShortcutKeys), Set.copyOf(missingMacroIds), Set.copyOf(existingMacroIds));
	}

	private static DurableProjection loadProjection(int objectId)
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			CharacterProjection character = null;
			try (PreparedStatement statement = connection.prepareStatement("SELECT account_name,charId,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,x,y,z,exp,sp,classid,base_class,title,online,vitality_points FROM characters WHERE charId=?"))
			{
				statement.setInt(1, objectId);
				try (ResultSet result = statement.executeQuery())
				{
					if (result.next())
					{
						character = new CharacterProjection(result.getString("account_name"), result.getInt("charId"), result.getString("char_name"), result.getInt("level"), result.getLong("maxHp"), result.getLong("curHp"), result.getLong("maxCp"), result.getLong("curCp"), result.getLong("maxMp"), result.getLong("curMp"), result.getInt("face"), result.getInt("hairStyle"), result.getInt("hairColor"), result.getInt("sex") != 0, result.getInt("x"), result.getInt("y"), result.getInt("z"), result.getLong("exp"), result.getLong("sp"), result.getInt("classid"), result.getInt("base_class"), Objects.requireNonNullElse(result.getString("title"), ""), result.getInt("online"), result.getInt("vitality_points"));
					}
					if ((character != null) && result.next())
					{
						throw new IllegalStateException("Population character projection returned duplicate rows.");
					}
				}
			}
			final List<ItemProjection> items = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? ORDER BY object_id"))
			{
				statement.setInt(1, objectId);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						items.add(new ItemProjection(result.getInt("object_id"), result.getInt("item_id"), result.getLong("count"), result.getString("loc")));
					}
				}
			}
			final List<SkillProjection> skills = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? ORDER BY skill_id,skill_level,class_index"))
			{
				statement.setInt(1, objectId);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						skills.add(new SkillProjection(result.getInt("skill_id"), result.getInt("skill_level"), result.getInt("class_index")));
					}
				}
			}
			final List<ShortcutProjection> shortcuts = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT slot,page,type,shortcut_id,level,class_index FROM character_shortcuts WHERE charId=? ORDER BY page,slot,class_index"))
			{
				statement.setInt(1, objectId);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						final String level = result.getString("level");
						if ((level == null) || !level.matches("-?[0-9]+"))
						{
							throw new IllegalStateException("character.projection_shortcut_level");
						}
						shortcuts.add(new ShortcutProjection(result.getInt("slot"), result.getInt("page"), result.getInt("type"), result.getInt("shortcut_id"), Integer.parseInt(level), result.getInt("class_index")));
					}
				}
			}
			final List<MacroProjection> macros = new ArrayList<>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT id,icon,name,descr,acronym,commands FROM character_macroses WHERE charId=? ORDER BY id"))
			{
				statement.setInt(1, objectId);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						macros.add(new MacroProjection(result.getInt("id"), result.getInt("icon"), result.getString("name"), result.getString("descr"), result.getString("acronym"), result.getString("commands")));
					}
				}
			}
			return new DurableProjection(character, List.copyOf(items), List.copyOf(skills), List.copyOf(shortcuts), List.copyOf(macros));
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not load exact population character projection.", e);
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
		if (managed.component().componentSchemaVersion() == 1)
		{
			_codec.decodeV1(managed.component().payload());
			throw new PopulationAuthorityException(AuthorityFailure.LEGACY_AUTHORITY_V1, "authority.legacy_v1");
		}
		if (managed.component().componentSchemaVersion() != PhantomPopulationState.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown population.state component schema version.");
		}
		final ManagedSnapshot snapshot = new ManagedSnapshot(managed.profile(), managed.component(), _codec.decode(managed.component().payload()));
		validateAuthority(snapshot);
		return snapshot;
	}

	public PopulationInitializationContract validateAuthority(ManagedSnapshot snapshot)
	{
		Objects.requireNonNull(snapshot, "Managed population snapshot must not be null.");
		final PhantomPopulationState state = snapshot.state();
		if (state.initializationAuthorityHash().isEmpty())
		{
			throw new PopulationAuthorityException(AuthorityFailure.LEGACY_AUTHORITY_V1, "authority.legacy_v1");
		}
		if (!state.catalogHash().equals(_catalog.hash()))
		{
			throw new PopulationAuthorityException(AuthorityFailure.CATALOG_DRIFT, "authority.catalog_drift");
		}
		final PopulationInitializationContract authority = PopulationInitializationContract.resolve(
			_catalog.hash(),
			_zoneId,
			state.classId(),
			new Location(state.creationX(), state.creationY(), state.creationZ()));
		if (!state.initializationAuthorityHash().equals(authority.hash()))
		{
			throw new PopulationAuthorityException(AuthorityFailure.CONTRACT_DRIFT, "authority.contract_drift");
		}
		return authority;
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

	public enum AuthorityFailure
	{
		LEGACY_AUTHORITY_V1,
		CATALOG_DRIFT,
		CONTRACT_DRIFT
	}

	public static final class PopulationAuthorityException extends IllegalStateException
	{
		private static final long serialVersionUID = 1L;
		private final AuthorityFailure _failure;

		private PopulationAuthorityException(AuthorityFailure failure, String key)
		{
			super(key);
			_failure = failure;
		}

		public AuthorityFailure failure()
		{
			return _failure;
		}
	}

	public enum FaultPoint
	{
		ADENA,
		INITIAL_ITEM,
		SKILLS,
		SHORTCUTS,
		MACROS,
		CHARACTER_STORE,
		FRESH_VERIFICATION,
		PROFILE_LINK,
		READY_COMPONENT_UPDATE
	}

	@FunctionalInterface
	public interface FailureInjector
	{
		void after(FaultPoint point, long profileId, int ordinal);

		static FailureInjector none()
		{
			return (point, profileId, ordinal) ->
			{
			};
		}
	}

	public static final class FaultInjectedException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		public FaultInjectedException(String message)
		{
			super(message);
		}
	}

	private final class StoreInitializationObserver implements PopulationInitializationObserver
	{
		private final long _profileId;

		private StoreInitializationObserver(long profileId)
		{
			_profileId = profileId;
		}

		@Override
		public void afterAdena()
		{
			_failureInjector.after(FaultPoint.ADENA, _profileId, 0);
		}

		@Override
		public void afterItem(int ordinal)
		{
			_failureInjector.after(FaultPoint.INITIAL_ITEM, _profileId, ordinal);
		}

		@Override
		public void afterSkills()
		{
			_failureInjector.after(FaultPoint.SKILLS, _profileId, 0);
		}

		@Override
		public void afterShortcut(int ordinal)
		{
			_failureInjector.after(FaultPoint.SHORTCUTS, _profileId, ordinal);
		}

		@Override
		public void afterMacro(int ordinal)
		{
			_failureInjector.after(FaultPoint.MACROS, _profileId, ordinal);
		}
	}

	private enum ProjectionStatus
	{
		PRISTINE,
		STRICT_SUBSET,
		CANONICAL,
		INCONSISTENT
	}

	private record ProjectionInspection(ProjectionStatus status, DurableProjection projection, String failure, Map<ExpectedItemKey, Long> missingItems, Set<PopulationInitializationContract.SkillFact> missingSkills, Set<ShortcutKey> missingShortcutKeys, Set<Integer> missingMacroIds, Set<Integer> existingMacroIds)
	{
		private static ProjectionInspection inconsistent(DurableProjection projection, String failure)
		{
			return new ProjectionInspection(ProjectionStatus.INCONSISTENT, projection, failure, Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
		}
	}

	private record ExpectedItemKey(int itemId, boolean equipped)
	{
	}

	private record DurableProjection(CharacterProjection character, List<ItemProjection> items, List<SkillProjection> skills, List<ShortcutProjection> shortcuts, List<MacroProjection> macros)
	{
		private byte[] canonicalBytes()
		{
			try
			{
				final ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
				try (DataOutputStream output = new DataOutputStream(bytes))
				{
					output.writeBoolean(character != null);
					if (character != null)
					{
						character.write(output);
					}
					output.writeInt(items.size());
					for (ItemProjection item : items)
					{
						output.writeInt(item.objectId());
						output.writeInt(item.itemId());
						output.writeLong(item.count());
						output.writeUTF(item.location());
					}
					output.writeInt(skills.size());
					for (SkillProjection skill : skills)
					{
						output.writeInt(skill.skillId());
						output.writeInt(skill.skillLevel());
						output.writeInt(skill.classIndex());
					}
					output.writeInt(shortcuts.size());
					for (ShortcutProjection shortcut : shortcuts)
					{
						output.writeInt(shortcut.slot());
						output.writeInt(shortcut.page());
						output.writeInt(shortcut.type());
						output.writeInt(shortcut.shortcutId());
						output.writeInt(shortcut.level());
						output.writeInt(shortcut.classIndex());
					}
					output.writeInt(macros.size());
					for (MacroProjection macro : macros)
					{
						output.writeInt(macro.id());
						output.writeInt(macro.icon());
						output.writeUTF(macro.name());
						output.writeUTF(macro.description());
						output.writeUTF(macro.acronym());
						output.writeUTF(macro.commands());
					}
				}
				return bytes.toByteArray();
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Could not encode durable population projection.", e);
			}
		}

		private String hash()
		{
			try
			{
				return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes()));
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Could not hash durable population projection.", e);
			}
		}
	}

	private record CharacterProjection(String account, int objectId, String name, int level, long maxHp, long curHp, long maxCp, long curCp, long maxMp, long curMp, int face, int hairStyle, int hairColor, boolean female, int x, int y, int z, long exp, long sp, int classId, int baseClass, String title, int online, int vitalityPoints)
	{
		private boolean matchesIdentity(PhantomPopulationState state)
		{
			return (objectId == Objects.requireNonNullElse(state.expectedCharacterObjectId(), objectId)) && account.equals(state.reservedAccount()) && name.equals(state.characterName()) && (classId == state.classId()) && (baseClass == state.classId()) && (female == state.female()) && (face == state.face()) && (hairColor == state.hairColor()) && (hairStyle == state.hairStyle());
		}

		private void write(DataOutputStream output) throws Exception
		{
			output.writeUTF(account);
			output.writeInt(objectId);
			output.writeUTF(name);
			output.writeInt(level);
			output.writeLong(maxHp);
			output.writeLong(curHp);
			output.writeLong(maxCp);
			output.writeLong(curCp);
			output.writeLong(maxMp);
			output.writeLong(curMp);
			output.writeInt(face);
			output.writeInt(hairStyle);
			output.writeInt(hairColor);
			output.writeBoolean(female);
			output.writeInt(x);
			output.writeInt(y);
			output.writeInt(z);
			output.writeLong(exp);
			output.writeLong(sp);
			output.writeInt(classId);
			output.writeInt(baseClass);
			output.writeUTF(title);
			output.writeInt(online);
			output.writeInt(vitalityPoints);
		}
	}

	private record ItemProjection(int objectId, int itemId, long count, String location)
	{
	}

	private record SkillProjection(int skillId, int skillLevel, int classIndex)
	{
	}

	private record ShortcutProjection(int slot, int page, int type, int shortcutId, int level, int classIndex)
	{
	}

	private record MacroProjection(int id, int icon, String name, String description, String acronym, String commands)
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
