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
package org.l2jmobius.gameserver.config.custom;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Loads the disabled-by-default Phantom World skeleton configuration.
 */
public final class PhantomPlayersConfig
{
	public static final String PHANTOM_PLAYERS_CONFIG_FILE = "./config/Custom/PhantomPlayers.ini";
	public static final int DEFAULT_MAX_MATERIALIZED_PHANTOMS = 32;
	public static final int DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES = 10000;
	public static final int DEFAULT_SCHEDULER_PULSE_MILLIS = 100;
	public static final int DEFAULT_SCHEDULER_PROFILES_PER_PULSE = 128;
	public static final int DEFAULT_POPULATION_TARGET = 0;
	public static final int DEFAULT_POPULATION_ACTIVE_TARGET = 0;
	public static final int DEFAULT_POPULATION_CREATION_IN_FLIGHT = 2;
	public static final int DEFAULT_POPULATION_BOUNDARIES_PER_PULSE = 64;
	public static final int DEFAULT_PARTY_OPERATIONS_PER_PULSE = 64;
	public static final int DEFAULT_SOCIAL_CACHE_PROFILES = 1024;
	public static final boolean DEFAULT_HUMANIZED_CONVERSATION = true;
	public static final boolean DEFAULT_CUSTOM_CONVERSATION_PACK = true;
	public static final boolean DEFAULT_MATURE_CONVERSATION = false;
	public static final String DEFAULT_ECOLOGY_PRESET = "LIVING";
	public static final int DEFAULT_ECOLOGY_WORLD_AGE_DAYS = -1;
	public static final int DEFAULT_ECOLOGY_ARCHIVE_LIMIT = 1000;
	public static final ZoneId DEFAULT_POPULATION_TIME_ZONE = ZoneId.of("UTC");

	private static volatile Settings _settings = Settings.disabled();
	private static volatile LearningSettings _learning = LearningSettings.off();

	private PhantomPlayersConfig()
	{
	}

	public static void load()
	{
		_settings = read(Path.of(PHANTOM_PLAYERS_CONFIG_FILE));
		_learning = _settings.enabled() && Boolean.getBoolean("phantom.learning.train") ? readLearning() : LearningSettings.off();
	}

	public record LearningSettings(String mode, long version, int maxCandidates, boolean evidence, int maxExamplesPerBatch, long seed)
	{
		private static LearningSettings off() { return new LearningSettings("OFF", 0, 4, false, 4096, 0); }
	}

	public static LearningSettings learningSettings()
	{
		return _learning;
	}

	private static LearningSettings readLearning()
	{
		try
		{
			final var config = new ConfigReader(PHANTOM_PLAYERS_CONFIG_FILE);
			final String mode = config.getString("LearningMode", "OFF");
			final long version = config.getLong("LearningModelVersion", 0);
			final int candidates = config.getInt("LearningMaxCandidates", 4);
			final int examples = config.getInt("LearningMaxExamplesPerBatch", 4096);
			if (!Set.of("OFF", "SHADOW", "ON").contains(mode) || version < 0 || candidates < 2 || candidates > 4 || examples < 32 || examples > 4096)
			{
				return LearningSettings.off();
			}
			return new LearningSettings(mode, version, candidates, config.getBoolean("LearningEvidenceEnabled", false), examples, config.getLong("LearningDeterministicSeed", 0));
		}
		catch (RuntimeException invalid)
		{
			return LearningSettings.off();
		}
	}

	public static Settings read(Path path)
	{
		try
		{
			if ((path == null) || !Files.isRegularFile(path))
			{
				return Settings.disabled();
			}

			final ConfigReader config = new ConfigReader(path.toString());
			final boolean enabled = strictBoolean(config.getValue("EnablePhantomSystem"));
			if (!enabled)
			{
				return Settings.disabled();
			}
			final Integer maximumMaterialized = strictCap(config.getValue("MaxMaterializedPhantoms"));
			final Integer burstSlots = strictInteger(config.getValue("PhantomMaterializationBurstSlots"), 0, 9999, 0);
			final Integer maximumScheduled = strictInteger(config.getValue("MaxScheduledPhantomProfiles"), 1, 1_000_000);
			final Integer pulseMillis = strictInteger(config.getValue("PhantomSchedulerPulseMillis"), 10, 1000);
			final Integer profilesPerPulse = strictInteger(config.getValue("PhantomSchedulerProfilesPerPulse"), 1, 10000);
			if ((maximumMaterialized == null) || (burstSlots == null) || (maximumScheduled == null) || (pulseMillis == null) || (profilesPerPulse == null) || (maximumMaterialized + burstSlots > 10000) || (maximumScheduled < maximumMaterialized + burstSlots))
			{
				return Settings.disabled();
			}
			final Integer populationTarget = strictInteger(config.getValue("PhantomPopulationTarget"), 0, maximumScheduled, DEFAULT_POPULATION_TARGET);
			final Integer populationActiveTarget = strictInteger(config.getValue("PhantomPopulationActiveTarget"), 0, Math.min(populationTarget != null ? populationTarget : 0, maximumMaterialized + burstSlots), DEFAULT_POPULATION_ACTIVE_TARGET);
			final Integer populationCreationInFlight = strictInteger(config.getValue("PhantomPopulationCreationInFlight"), 1, 64, DEFAULT_POPULATION_CREATION_IN_FLIGHT);
			final Integer populationBoundariesPerPulse = strictInteger(config.getValue("PhantomPopulationBoundariesPerPulse"), 1, 10000, DEFAULT_POPULATION_BOUNDARIES_PER_PULSE);
			final Integer partyOperationsPerPulse = strictInteger(config.getValue("PhantomPartyOperationsPerPulse"), 10, 10000, DEFAULT_PARTY_OPERATIONS_PER_PULSE);
			final Integer socialCacheProfiles = strictInteger(config.getValue("PhantomSocialCacheProfiles"), 16, 10000, DEFAULT_SOCIAL_CACHE_PROFILES);
			final ZoneId populationTimeZone = strictZoneId(config.getValue("PhantomPopulationTimeZone"));
			final Boolean ecologyEnabled = strictOptionalBoolean(config.getValue("EnablePhantomEcology"), true);
			final String ecologyPreset = strictEcologyPreset(config.getValue("PhantomEcologyPreset"));
			final Integer ecologyWorldAgeDays = strictWorldAgeDays(config.getValue("PhantomEcologyWorldAgeDays"));
			final Integer ecologyArchiveLimit = strictInteger(config.getValue("PhantomEcologyArchiveLimit"), 1, 1_000_000, DEFAULT_ECOLOGY_ARCHIVE_LIMIT);
			final Boolean humanizedConversation = strictOptionalBoolean(config.getValue("EnablePhantomHumanizedConversation"), DEFAULT_HUMANIZED_CONVERSATION);
			final Boolean customConversationPack = strictOptionalBoolean(config.getValue("EnablePhantomCustomConversationPack"), DEFAULT_CUSTOM_CONVERSATION_PACK);
			final ConversationRegister conversationRegister = strictOptionalEnum(config.getValue("PhantomConversationRegister"), ConversationRegister.CASUAL, ConversationRegister.class);
			final ConversationProfanity conversationProfanity = strictOptionalEnum(config.getValue("PhantomConversationProfanity"), ConversationProfanity.CONTEXTUAL, ConversationProfanity.class);
			final ConversationVariation conversationVariation = strictOptionalEnum(config.getValue("PhantomConversationVariation"), ConversationVariation.HIGH, ConversationVariation.class);
			final Boolean matureConversation = strictOptionalBoolean(config.getValue("EnablePhantomMatureConversation"), DEFAULT_MATURE_CONVERSATION);
			// Startup-only opt-in; old owner profiles without this key retain the exact v3 path.
			final Boolean semanticV4 = strictOptionalBoolean(config.getValue("EnablePhantomSemanticV4"), false);
			final Integer starterShotLifetimeLimit = strictInteger(config.getValue("PhantomStarterShotLifetimeLimit"), 0, 5000, 0);
			if (starterShotLifetimeLimit == null) { return Settings.disabled(); }
			final PartySeekingSettings partySeeking = readPartySeeking(path);
			if (partySeeking == null)
			{
				return Settings.disabled();
			}
			if ((populationTarget == null) || (populationActiveTarget == null) || (populationCreationInFlight == null) || (populationBoundariesPerPulse == null) || (partyOperationsPerPulse == null) || (socialCacheProfiles == null) || (populationTimeZone == null) || (ecologyEnabled == null) || (ecologyPreset == null) || (ecologyWorldAgeDays == null) || (ecologyArchiveLimit == null) || (humanizedConversation == null) || (customConversationPack == null) || (conversationRegister == null) || (conversationProfanity == null) || (conversationVariation == null) || (matureConversation == null))
			{
				return Settings.disabled();
			}
			final boolean diagnosticsEnabled = enabled && strictBoolean(config.getValue("EnablePhantomDiagnostics"));
			final ConversationSettings conversation = new ConversationSettings(humanizedConversation, customConversationPack, conversationRegister, conversationProfanity, conversationVariation, matureConversation, Boolean.TRUE.equals(semanticV4));
			return new Settings(true, diagnosticsEnabled, maximumMaterialized, maximumScheduled, pulseMillis, profilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, ecologyEnabled, ecologyPreset, ecologyWorldAgeDays, ecologyArchiveLimit, conversation, burstSlots, starterShotLifetimeLimit, partySeeking);
		}
		catch (IOException | RuntimeException e)
		{
			return Settings.disabled();
		}
	}

	public static Settings settings()
	{
		return _settings;
	}

	public static boolean isEnabled()
	{
		return _settings.enabled();
	}

	public enum ConversationRegister
	{
		NEUTRAL,
		CASUAL
	}

	public enum ConversationProfanity
	{
		NONE,
		MILD,
		CONTEXTUAL
	}

	public enum ConversationVariation
	{
		LOW,
		MEDIUM,
		HIGH
	}

	public record ConversationSettings(boolean humanizedEnabled, boolean customPackEnabled, ConversationRegister register, ConversationProfanity profanity, ConversationVariation variation, boolean matureEnabled, boolean semanticV4Enabled)
	{
		public ConversationSettings
		{
			Objects.requireNonNull(register);
			Objects.requireNonNull(profanity);
			Objects.requireNonNull(variation);
			matureEnabled = humanizedEnabled && matureEnabled;
			semanticV4Enabled = humanizedEnabled && semanticV4Enabled;
		}

		public ConversationSettings(boolean humanizedEnabled, boolean customPackEnabled, ConversationRegister register, ConversationProfanity profanity, ConversationVariation variation, boolean matureEnabled)
		{
			this(humanizedEnabled, customPackEnabled, register, profanity, variation, matureEnabled, false);
		}

		public static ConversationSettings disabled()
		{
			return new ConversationSettings(false, false, ConversationRegister.CASUAL, ConversationProfanity.CONTEXTUAL, ConversationVariation.HIGH, false);
		}
	}

	/** Stable interest in future autonomous searches; never an instruction to leave an existing native Party. */
	public record PartySeekingSettings(boolean enabled, int chance1To39, int chance40To51, int chance52To60, int chance61To75, int chance76To85, boolean preferHumanFollow)
	{
		public PartySeekingSettings
		{
			if ((chance1To39 < 0) || (chance1To39 > 100) || (chance40To51 < 0) || (chance40To51 > 100) || (chance52To60 < 0) || (chance52To60 > 100) || (chance61To75 < 0) || (chance61To75 > 100) || (chance76To85 < 0) || (chance76To85 > 100))
			{
				throw new IllegalArgumentException("Party seeking percentages must be between 0 and 100.");
			}
		}

		public int percentage(int level)
		{
			if (level <= 0) { return 0; }
			if (level <= 39) { return chance1To39; }
			if (level <= 51) { return chance40To51; }
			if (level <= 60) { return chance52To60; }
			if (level <= 75) { return chance61To75; }
			return chance76To85;
		}

		public int percentile(long profileId)
		{
			// Immutable durable identity, not the current materialized character object ID or pulse time.
			long mixed = profileId ^ 0x9e3779b97f4a7c15L;
			mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
			mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
			mixed ^= mixed >>> 31;
			return Math.floorMod(mixed, 100);
		}

		public boolean seeks(long profileId, int level)
		{
			return enabled && (profileId > 0) && (level > 0) && (percentile(profileId) < percentage(level));
		}

		public static PartySeekingSettings defaults()
		{
			return new PartySeekingSettings(true, 0, 20, 35, 50, 80, true);
		}

		public static PartySeekingSettings disabled()
		{
			return new PartySeekingSettings(false, 0, 20, 35, 50, 80, false);
		}
	}

	private static PartySeekingSettings readPartySeeking(Path path) throws IOException
	{
		final Set<String> keys = Set.of("EnablePhantomAutonomousPartySeeking", "PhantomPartySeekChance1To39", "PhantomPartySeekChance40To51", "PhantomPartySeekChance52To60", "PhantomPartySeekChance61To75", "PhantomPartySeekChance76To85", "PhantomPartyPreferHumanFollow");
		// Same Properties escaping/continuation semantics and charset as ConfigReader, with local duplicate admission.
		final Properties properties = new Properties()
		{
			@Override
			public synchronized Object put(Object key, Object value)
			{
				if (!keys.contains(key)) { return null; }
				if (containsKey(key)) { throw new IllegalArgumentException("Duplicate Party seeking property: " + key); }
				return super.put(key, value);
			}
		};
		try (var reader = Files.newBufferedReader(path, Charset.defaultCharset()))
		{
			properties.load(reader);
		}
		final Boolean enabled = strictOptionalBoolean(properties.getProperty("EnablePhantomAutonomousPartySeeking"), true);
		final Integer low = strictInteger(properties.getProperty("PhantomPartySeekChance1To39"), 0, 100, 0);
		final Integer first = strictInteger(properties.getProperty("PhantomPartySeekChance40To51"), 0, 100, 20);
		final Integer second = strictInteger(properties.getProperty("PhantomPartySeekChance52To60"), 0, 100, 35);
		final Integer third = strictInteger(properties.getProperty("PhantomPartySeekChance61To75"), 0, 100, 50);
		final Integer fourth = strictInteger(properties.getProperty("PhantomPartySeekChance76To85"), 0, 100, 80);
		final Boolean preferHumanFollow = strictOptionalBoolean(properties.getProperty("PhantomPartyPreferHumanFollow"), true);
		if ((enabled == null) || (low == null) || (first == null) || (second == null) || (third == null) || (fourth == null) || (preferHumanFollow == null))
		{
			return null;
		}
		return new PartySeekingSettings(enabled, low, first, second, third, fourth, preferHumanFollow);
	}

	private static boolean strictBoolean(String value)
	{
		if (value == null)
		{
			return false;
		}

		final String normalized = value.trim();
		if (normalized.equalsIgnoreCase("true"))
		{
			return true;
		}
		if (normalized.equalsIgnoreCase("false"))
		{
			return false;
		}
		return false;
	}

	private static Boolean strictOptionalBoolean(String value, boolean defaultValue)
	{
		if (value == null)
		{
			return defaultValue;
		}
		final String normalized = value.trim();
		if (normalized.equalsIgnoreCase("true"))
		{
			return true;
		}
		if (normalized.equalsIgnoreCase("false"))
		{
			return false;
		}
		return null;
	}

	private static <E extends Enum<E>> E strictOptionalEnum(String value, E defaultValue, Class<E> type)
	{
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Enum.valueOf(type, value.trim());
		}
		catch (IllegalArgumentException exception)
		{
			return null;
		}
	}

	private static String strictEcologyPreset(String value)
	{
		final String normalized = value == null ? DEFAULT_ECOLOGY_PRESET : value.trim();
		return normalized.equals("FRESH") || normalized.equals("LIVING") || normalized.equals("MATURE") ? normalized : null;
	}

	private static Integer strictWorldAgeDays(String value)
	{
		if (value == null)
		{
			return DEFAULT_ECOLOGY_WORLD_AGE_DAYS;
		}
		final String normalized = value.trim();
		return "-1".equals(normalized) ? -1 : strictInteger(normalized, 0, 3650);
	}

	private static Integer strictCap(String value)
	{
		return strictInteger(value, 1, 10000);
	}

	private static Integer strictInteger(String value, int minimum, int maximum)
	{
		if (value == null)
		{
			return null;
		}
		final String normalized = value.trim();
		if (!normalized.matches("[0-9]+"))
		{
			return null;
		}
		try
		{
			final int parsed = Integer.parseInt(normalized, 10);
			return ((parsed >= minimum) && (parsed <= maximum)) ? parsed : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Integer strictInteger(String value, int minimum, int maximum, int defaultValue)
	{
		return value == null ? defaultValue : strictInteger(value, minimum, maximum);
	}

	private static ZoneId strictZoneId(String value)
	{
		if (value == null)
		{
			return DEFAULT_POPULATION_TIME_ZONE;
		}
		final String normalized = value.trim();
		if (normalized.isEmpty() || (normalized.length() > 64))
		{
			return null;
		}
		try
		{
			return ZoneId.of(normalized);
		}
		catch (DateTimeException e)
		{
			return null;
		}
	}

	public record Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone, boolean ecologyEnabled, String ecologyPreset, int ecologyWorldAgeDays, int ecologyArchiveLimit, ConversationSettings conversation, int materializationBurstSlots, int starterShotLifetimeLimit, PartySeekingSettings partySeeking)
	{
		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone, boolean ecologyEnabled, String ecologyPreset, int ecologyWorldAgeDays, int ecologyArchiveLimit, ConversationSettings conversation, int materializationBurstSlots, int starterShotLifetimeLimit)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, ecologyEnabled, ecologyPreset, ecologyWorldAgeDays, ecologyArchiveLimit, conversation, materializationBurstSlots, starterShotLifetimeLimit, enabled ? PartySeekingSettings.defaults() : PartySeekingSettings.disabled());
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone, boolean ecologyEnabled, String ecologyPreset, int ecologyWorldAgeDays, int ecologyArchiveLimit, ConversationSettings conversation, int materializationBurstSlots)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, ecologyEnabled, ecologyPreset, ecologyWorldAgeDays, ecologyArchiveLimit, conversation, materializationBurstSlots, 0);
		}

		public Settings
		{
			if (starterShotLifetimeLimit < 0 || starterShotLifetimeLimit > 5000) { throw new IllegalArgumentException("Starter shot lifetime limit must be between0 and5000."); }
			starterShotLifetimeLimit = enabled ? starterShotLifetimeLimit : 0;
			diagnosticsEnabled = enabled && diagnosticsEnabled;
			maxMaterializedPhantoms = enabled ? maxMaterializedPhantoms : 0;
			materializationBurstSlots = enabled ? materializationBurstSlots : 0;
			maxScheduledPhantomProfiles = enabled ? maxScheduledPhantomProfiles : 0;
			schedulerPulseMillis = enabled ? schedulerPulseMillis : 0;
			schedulerProfilesPerPulse = enabled ? schedulerProfilesPerPulse : 0;
			populationTarget = enabled ? populationTarget : 0;
			populationActiveTarget = enabled ? populationActiveTarget : 0;
			populationCreationInFlight = enabled ? populationCreationInFlight : 0;
			populationBoundariesPerPulse = enabled ? populationBoundariesPerPulse : 0;
			partyOperationsPerPulse = enabled ? partyOperationsPerPulse : 0;
			socialCacheProfiles = enabled ? socialCacheProfiles : 0;
			populationTimeZone = enabled ? populationTimeZone : DEFAULT_POPULATION_TIME_ZONE;
			ecologyEnabled = enabled && ecologyEnabled;
			ecologyPreset = enabled ? ecologyPreset : DEFAULT_ECOLOGY_PRESET;
			ecologyWorldAgeDays = enabled ? ecologyWorldAgeDays : DEFAULT_ECOLOGY_WORLD_AGE_DAYS;
			ecologyArchiveLimit = enabled ? ecologyArchiveLimit : 0;
			conversation = enabled ? Objects.requireNonNull(conversation) : ConversationSettings.disabled();
			partySeeking = enabled ? Objects.requireNonNull(partySeeking) : PartySeekingSettings.disabled();
			if (enabled && ((maxMaterializedPhantoms < 1) || (maxMaterializedPhantoms > 10000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a materialization cap between 1 and 10000.");
			}
			if (enabled && (materializationBurstSlots < 0 || materializationBurstSlots > 9999 || maxMaterializedPhantoms + materializationBurstSlots > 10000))
			{
				throw new IllegalArgumentException("Materialization soft target plus burst must fit10000 physical admissions.");
			}
			if (enabled && ((maxScheduledPhantomProfiles < 1) || (maxScheduledPhantomProfiles > 1_000_000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduled profile cap between 1 and 1000000.");
			}
			if (enabled && ((schedulerPulseMillis < 10) || (schedulerPulseMillis > 1000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduler pulse between 10 and 1000 milliseconds.");
			}
			if (enabled && ((schedulerProfilesPerPulse < 1) || (schedulerProfilesPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduler profile budget between 1 and 10000.");
			}
			if (enabled && (maxScheduledPhantomProfiles < maxMaterializedPhantoms + materializationBurstSlots))
			{
				throw new IllegalArgumentException("Scheduled Phantom profile capacity must cover materialization capacity.");
			}
			if (enabled && ((populationTarget < 0) || (populationTarget > maxScheduledPhantomProfiles)))
			{
				throw new IllegalArgumentException("Population target must be between zero and scheduled profile capacity.");
			}
			if (enabled && ((populationActiveTarget < 0) || (populationActiveTarget > Math.min(populationTarget, maxMaterializedPhantoms + materializationBurstSlots))))
			{
				throw new IllegalArgumentException("Population ACTIVE target must fit both population and materialization capacity.");
			}
			if (enabled && ((populationCreationInFlight < 1) || (populationCreationInFlight > 64)))
			{
				throw new IllegalArgumentException("Population creation in-flight limit must be between 1 and 64.");
			}
			if (enabled && ((populationBoundariesPerPulse < 1) || (populationBoundariesPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Population boundary budget must be between 1 and 10000.");
			}
			if (enabled && ((partyOperationsPerPulse < 10) || (partyOperationsPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Party operation budget must be between 10 and 10000.");
			}
			if (enabled && ((socialCacheProfiles < 16) || (socialCacheProfiles > 10000)))
			{
				throw new IllegalArgumentException("Social cache capacity must be between 16 and 10000 profiles.");
			}
			Objects.requireNonNull(populationTimeZone, "Population time zone must not be null.");
			if (enabled && (!ecologyPreset.equals("FRESH") && !ecologyPreset.equals("LIVING") && !ecologyPreset.equals("MATURE")))
			{
				throw new IllegalArgumentException("Ecology preset must be FRESH, LIVING or MATURE.");
			}
			if (enabled && ((ecologyWorldAgeDays < -1) || (ecologyWorldAgeDays > 3650)))
			{
				throw new IllegalArgumentException("Ecology world age must be -1 or between 0 and 3650 days.");
			}
			if (enabled && ((ecologyArchiveLimit < 1) || (ecologyArchiveLimit > 1_000_000)))
			{
				throw new IllegalArgumentException("Ecology archive limit must be between 1 and 1000000.");
			}
		}

		public int physicalMaterializationCeiling() { return maxMaterializedPhantoms + materializationBurstSlots; }

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone, boolean ecologyEnabled, String ecologyPreset, int ecologyWorldAgeDays, int ecologyArchiveLimit, ConversationSettings conversation)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, ecologyEnabled, ecologyPreset, ecologyWorldAgeDays, ecologyArchiveLimit, conversation, 0);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone, boolean ecologyEnabled, String ecologyPreset, int ecologyWorldAgeDays, int ecologyArchiveLimit)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, ecologyEnabled, ecologyPreset, ecologyWorldAgeDays, ecologyArchiveLimit, enabled ? new ConversationSettings(DEFAULT_HUMANIZED_CONVERSATION, DEFAULT_CUSTOM_CONVERSATION_PACK, ConversationRegister.CASUAL, ConversationProfanity.CONTEXTUAL, ConversationVariation.HIGH, DEFAULT_MATURE_CONVERSATION) : ConversationSettings.disabled());
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, int socialCacheProfiles, ZoneId populationTimeZone)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, socialCacheProfiles, populationTimeZone, enabled, DEFAULT_ECOLOGY_PRESET, DEFAULT_ECOLOGY_WORLD_AGE_DAYS, enabled ? DEFAULT_ECOLOGY_ARCHIVE_LIMIT : 0);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled)
		{
			this(enabled, diagnosticsEnabled, enabled ? DEFAULT_MAX_MATERIALIZED_PHANTOMS : 0, enabled ? DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES : 0, enabled ? DEFAULT_SCHEDULER_PULSE_MILLIS : 0, enabled ? DEFAULT_SCHEDULER_PROFILES_PER_PULSE : 0, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, enabled ? DEFAULT_SOCIAL_CACHE_PROFILES : 0, DEFAULT_POPULATION_TIME_ZONE, enabled, DEFAULT_ECOLOGY_PRESET, DEFAULT_ECOLOGY_WORLD_AGE_DAYS, enabled ? DEFAULT_ECOLOGY_ARCHIVE_LIMIT : 0);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, enabled ? DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES : 0, enabled ? DEFAULT_SCHEDULER_PULSE_MILLIS : 0, enabled ? DEFAULT_SCHEDULER_PROFILES_PER_PULSE : 0, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, enabled ? DEFAULT_SOCIAL_CACHE_PROFILES : 0, DEFAULT_POPULATION_TIME_ZONE);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int burstSlots)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, enabled ? DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES : 0, enabled ? DEFAULT_SCHEDULER_PULSE_MILLIS : 0, enabled ? DEFAULT_SCHEDULER_PROFILES_PER_PULSE : 0, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, enabled ? DEFAULT_SOCIAL_CACHE_PROFILES : 0, DEFAULT_POPULATION_TIME_ZONE, enabled, DEFAULT_ECOLOGY_PRESET, DEFAULT_ECOLOGY_WORLD_AGE_DAYS, enabled ? DEFAULT_ECOLOGY_ARCHIVE_LIMIT : 0, enabled ? new ConversationSettings(DEFAULT_HUMANIZED_CONVERSATION, DEFAULT_CUSTOM_CONVERSATION_PACK, ConversationRegister.CASUAL, ConversationProfanity.CONTEXTUAL, ConversationVariation.HIGH, DEFAULT_MATURE_CONVERSATION) : ConversationSettings.disabled(), burstSlots);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, enabled ? DEFAULT_SOCIAL_CACHE_PROFILES : 0, DEFAULT_POPULATION_TIME_ZONE);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, ZoneId populationTimeZone)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, enabled ? DEFAULT_SOCIAL_CACHE_PROFILES : 0, populationTimeZone);
		}

		public static Settings disabled()
		{
			return new Settings(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, DEFAULT_POPULATION_TIME_ZONE, false, DEFAULT_ECOLOGY_PRESET, DEFAULT_ECOLOGY_WORLD_AGE_DAYS, 0, ConversationSettings.disabled());
		}
	}
}
