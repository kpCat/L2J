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
package org.l2jmobius.gameserver.phantoms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.CreationStage;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;

/**
 * Explicit two-phase, fail-closed deletion owner for durable Phantom identities.
 */
public final class PhantomPopulationResetService
{
	public static final long CONFIRMATION_TTL_MILLIS = 120_000L;
	public static final int MAX_PREVIEW_IDENTITIES = 10_000;
	private static final int SQL_BATCH_SIZE = 250;
	private static final int DISABLED_ACCOUNT_ACCESS_LEVEL = -1;

	private final LongSupplier _clock;
	private final SecureRandom _random;
	private final Lifecycle _lifecycle;
	private final FailureInjector _failureInjector;
	private final JdbcRepository _repository;
	private ArmedReset _armed;

	public PhantomPopulationResetService(Lifecycle lifecycle)
	{
		this(System::currentTimeMillis, new SecureRandom(), lifecycle, FailureInjector.none());
	}

	PhantomPopulationResetService(LongSupplier clock, SecureRandom random, Lifecycle lifecycle, FailureInjector failureInjector)
	{
		_clock = Objects.requireNonNull(clock);
		_random = Objects.requireNonNull(random);
		_lifecycle = Objects.requireNonNull(lifecycle);
		_failureInjector = Objects.requireNonNull(failureInjector);
		_repository = new JdbcRepository();
	}

	public synchronized ResetPreview preview()
	{
		_armed = null;
		final long generatedAt = _clock.getAsLong();
		final Inspection inspection;
		try
		{
			inspection = _repository.inspect();
		}
		catch (RuntimeException exception)
		{
			return ResetPreview.blocked(generatedAt, "inspection.failed." + exception.getClass().getSimpleName());
		}
		if (!inspection.blockers().isEmpty())
		{
			return ResetPreview.from(inspection, generatedAt, 0, null);
		}

		final long expiresAt = Math.addExact(generatedAt, CONFIRMATION_TTL_MILLIS);
		final byte[] tokenBytes = new byte[18];
		_random.nextBytes(tokenBytes);
		final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		_armed = new ArmedReset(token, inspection.snapshotHash(), expiresAt);
		return ResetPreview.from(inspection, generatedAt, expiresAt, token);
	}

	public synchronized ResetResult confirm(String token, boolean reseed)
	{
		final ArmedReset armed = _armed;
		if (armed == null)
		{
			return ResetResult.rejected(ResetCode.NOT_ARMED, "Run reset preview first.");
		}
		if (!constantEquals(armed.token(), token))
		{
			return ResetResult.rejected(ResetCode.INVALID_TOKEN, "Confirmation token does not match.");
		}
		_armed = null;
		if (_clock.getAsLong() > armed.expiresAt())
		{
			return ResetResult.rejected(ResetCode.EXPIRED_TOKEN, "Confirmation token expired.");
		}

		final Inspection beforeDrain;
		try
		{
			beforeDrain = _repository.inspect();
		}
		catch (RuntimeException exception)
		{
			return ResetResult.rejected(ResetCode.INSPECTION_FAILED, "Ownership inspection failed.");
		}
		if (!beforeDrain.blockers().isEmpty())
		{
			return ResetResult.rejected(ResetCode.BLOCKED, String.join(",", beforeDrain.blockers()));
		}
		if (!armed.snapshotHash().equals(beforeDrain.snapshotHash()))
		{
			return ResetResult.rejected(ResetCode.SNAPSHOT_CHANGED, "Durable reset snapshot changed.");
		}

		final OperatorControlResult drain = _lifecycle.drain();
		if ((drain.code() != OperatorControlCode.DRAINED) && (drain.code() != OperatorControlCode.ALREADY_DRAINED))
		{
			return ResetResult.rejected(ResetCode.DRAIN_FAILED, "Runtime drain did not reach a terminal state: " + drain.code());
		}

		final Inspection afterDrain;
		try
		{
			afterDrain = _repository.inspect();
		}
		catch (RuntimeException exception)
		{
			return ResetResult.rejected(ResetCode.INSPECTION_FAILED, "Post-drain ownership inspection failed.");
		}
		if (!afterDrain.blockers().isEmpty())
		{
			return ResetResult.rejected(ResetCode.BLOCKED, String.join(",", afterDrain.blockers()));
		}
		if (!armed.snapshotHash().equals(afterDrain.snapshotHash()))
		{
			return ResetResult.rejected(ResetCode.SNAPSHOT_CHANGED, "Durable reset snapshot changed during drain.");
		}

		final Mutation mutation;
		try
		{
			mutation = _repository.reset(afterDrain, _failureInjector);
		}
		catch (RuntimeException exception)
		{
			return ResetResult.rejected(ResetCode.RESET_FAILED, "Transactional reset rolled back.");
		}
		for (int objectId : mutation.characterObjectIds())
		{
			CharInfoTable.getInstance().removeName(objectId);
		}
		if (!reseed)
		{
			return new ResetResult(mutation.identities(), mutation.identities() == 0 ? ResetCode.RESET_NOOP : ResetCode.RESET_COMPLETE, true, false, "Reset committed.");
		}

		final OperatorControlResult enable = _lifecycle.reseed();
		return switch (enable.code())
		{
			case STARTED, ALREADY_RUNNING -> new ResetResult(mutation.identities(), ResetCode.RESET_RESEEDED, true, true, "Reset committed and population reseed started.");
			case CONFIG_DISABLED -> new ResetResult(mutation.identities(), ResetCode.RESET_CONFIG_DISABLED, true, false, "Reset committed; reseed is disabled by configuration.");
			default -> new ResetResult(mutation.identities(), ResetCode.RESET_RESEED_FAILED, true, false, "Reset committed; reseed failed: " + enable.code());
		};
	}

	public synchronized boolean cancel()
	{
		final boolean existed = _armed != null;
		_armed = null;
		return existed;
	}

	private static boolean constantEquals(String expected, String actual)
	{
		if (actual == null)
		{
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	public interface Lifecycle
	{
		OperatorControlResult drain();

		OperatorControlResult reseed();
	}

	public enum ResetCode
	{
		NOT_ARMED,
		INVALID_TOKEN,
		EXPIRED_TOKEN,
		INSPECTION_FAILED,
		BLOCKED,
		SNAPSHOT_CHANGED,
		DRAIN_FAILED,
		RESET_FAILED,
		RESET_NOOP,
		RESET_COMPLETE,
		RESET_RESEEDED,
		RESET_CONFIG_DISABLED,
		RESET_RESEED_FAILED
	}

	public record ResetPreview(
		boolean safe,
		int identities,
		int characters,
		int accounts,
		Map<String, Long> deleteCounts,
		Map<String, Long> preserveCounts,
		List<String> blockers,
		String snapshotHash,
		long generatedAt,
		long expiresAt,
		String confirmationToken)
	{
		public ResetPreview
		{
			deleteCounts = Map.copyOf(deleteCounts);
			preserveCounts = Map.copyOf(preserveCounts);
			blockers = List.copyOf(blockers);
		}

		private static ResetPreview from(Inspection inspection, long generatedAt, long expiresAt, String token)
		{
			return new ResetPreview(inspection.blockers().isEmpty(), inspection.identities().size(), inspection.characterObjectIds().size(), inspection.accountNames().size(), inspection.deleteCounts(), inspection.preserveCounts(), inspection.blockers(), inspection.snapshotHash(), generatedAt, expiresAt, token);
		}

		private static ResetPreview blocked(long generatedAt, String blocker)
		{
			return new ResetPreview(false, 0, 0, 0, Map.of(), Map.of(), List.of(blocker), sha256(blocker), generatedAt, 0, null);
		}
	}

	public record ResetResult(int identities, ResetCode code, boolean resetCommitted, boolean reseeded, String detail)
	{
		private static ResetResult rejected(ResetCode code, String detail)
		{
			return new ResetResult(0, code, false, false, detail);
		}
	}

	public enum FaultPoint
	{
		CLEANUP_BOUNDARY,
		BEFORE_COMMIT
	}

	@FunctionalInterface
	public interface FailureInjector
	{
		void after(FaultPoint point);

		static FailureInjector none()
		{
			return _ -> { };
		}
	}

	private record ArmedReset(String token, String snapshotHash, long expiresAt)
	{
	}

	private record OwnedIdentity(long profileId, long profileRowVersion, long componentRowVersion, byte[] populationPayload, String accountName, String ownershipToken, String characterName, Integer characterObjectId)
	{
		private OwnedIdentity
		{
			populationPayload = populationPayload.clone();
		}
	}

	private record Inspection(
		List<OwnedIdentity> identities,
		List<Integer> characterObjectIds,
		List<String> accountNames,
		Map<String, Long> deleteCounts,
		Map<String, Long> preserveCounts,
		List<String> blockers,
		String snapshotHash)
	{
		private Inspection
		{
			identities = List.copyOf(identities);
			characterObjectIds = List.copyOf(characterObjectIds);
			accountNames = List.copyOf(accountNames);
			deleteCounts = Map.copyOf(deleteCounts);
			preserveCounts = Map.copyOf(preserveCounts);
			blockers = List.copyOf(blockers);
		}
	}

	private record Mutation(int identities, List<Integer> characterObjectIds)
	{
		private Mutation
		{
			characterObjectIds = List.copyOf(characterObjectIds);
		}
	}

	private static final class JdbcRepository
	{
		private static final List<IdRule> PRIVATE_ID_RULES = List.of(
			new IdRule("auction_watch", "charObjId"),
			new IdRule("bbs_favorites", "playerId"),
			new IdRule("buffer_schemes", "object_id"),
			new IdRule("character_hennas", "charId"),
			new IdRule("character_instance_time", "charId"),
			new IdRule("character_item_reuse_save", "charId"),
			new IdRule("character_macroses", "charId"),
			new IdRule("character_offline_play", "charId"),
			new IdRule("character_offline_trade", "charId"),
			new IdRule("character_offline_trade_items", "charId"),
			new IdRule("character_premium_items", "charId"),
			new IdRule("character_quests", "charId"),
			new IdRule("character_raid_points", "charId"),
			new IdRule("character_recipebook", "charId"),
			new IdRule("character_recipeshoplist", "charId"),
			new IdRule("character_reco_bonus", "charId"),
			new IdRule("character_shortcuts", "charId"),
			new IdRule("character_skills", "charId"),
			new IdRule("character_skills_save", "charId"),
			new IdRule("character_subclasses", "charId"),
			new IdRule("character_summon_skills_save", "ownerId"),
			new IdRule("character_summons", "ownerId"),
			new IdRule("character_tpbookmark", "charId"),
			new IdRule("character_variables", "charId"),
			new IdRule("heroes", "charId"),
			new IdRule("merchant_lease", "player_id"),
			new IdRule("olympiad_nobles", "charId"),
			new IdRule("olympiad_nobles_eom", "charId"),
			new IdRule("seven_signs", "charId"));
		private static final List<SharedRule> SHARED_BLOCKERS = List.of(
			new SharedRule("airship", "airships", "owner_id", null),
			new SharedRule("clan_leader", "clan_data", "leader_id", "new_leader_id"),
			new SharedRule("clan_subpledge_leader", "clan_subpledges", "leader_id", null),
			new SharedRule("cursed_weapon", "cursed_weapons", "charId", null),
			new SharedRule("grandboss_zone", "grandboss_list", "player_id", null),
			new SharedRule("item_auction_bid", "item_auction_bid", "playerObjId", null),
			new SharedRule("siegable_hall_roster", "siegable_hall_flagwar_attackers_members", "object_id", null),
			new SharedRule("wedding", "mods_wedding", "player1Id", "player2Id"),
			new SharedRule("pending_custom_mail", "custom_mail", "receiver", null));

		private final PhantomPopulationStateCodec _codec = new PhantomPopulationStateCodec();

		private Inspection inspect()
		{
			try (Connection connection = DatabaseFactory.getConnection())
			{
				return inspect(connection);
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not inspect Phantom reset ownership.", e);
			}
		}

		private Inspection inspect(Connection connection) throws SQLException
		{
			final List<OwnedIdentity> identities = new ArrayList<>();
			final Set<Integer> characterObjectIds = new LinkedHashSet<>();
			final Set<String> accountNames = new LinkedHashSet<>();
			final Set<String> seenAccounts = new LinkedHashSet<>();
			final Set<String> seenCharacterNames = new LinkedHashSet<>();
			final Set<Integer> seenCharacterIds = new LinkedHashSet<>();
			final List<String> blockers = new ArrayList<>();

			try (PreparedStatement statement = connection.prepareStatement(
				"SELECT p.profile_id,p.character_object_id,p.row_version AS profile_row_version,c.component_schema_version,c.row_version AS component_row_version,c.payload " +
					"FROM phantom_profiles p LEFT JOIN phantom_profile_components c ON c.profile_id=p.profile_id AND c.component_type=? ORDER BY p.profile_id"))
			{
				statement.setString(1, PhantomPopulationState.COMPONENT_TYPE);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						if (identities.size() >= MAX_PREVIEW_IDENTITIES)
						{
							blockers.add("ownership.preview_identity_limit");
							break;
						}
						final long profileId = result.getLong("profile_id");
						final byte[] payload = result.getBytes("payload");
						if (payload == null)
						{
							blockers.add("ownership.population_state_missing:" + profileId);
							continue;
						}
						if (result.getInt("component_schema_version") != PhantomPopulationState.SCHEMA_VERSION)
						{
							blockers.add("ownership.population_schema_unknown:" + profileId);
							continue;
						}
						final PhantomPopulationState state;
						try
						{
							state = _codec.decode(payload);
						}
						catch (RuntimeException exception)
						{
							blockers.add("ownership.population_state_invalid:" + profileId);
							continue;
						}
						if (!seenAccounts.add(state.reservedAccount()))
						{
							blockers.add("ownership.account_duplicate:" + state.reservedAccount());
						}
						final String expectedAccount = "p" + Long.toString(profileId, 36);
						if (!expectedAccount.equals(state.reservedAccount()))
						{
							blockers.add("ownership.account_name_mismatch:" + profileId);
						}
						if (!seenCharacterNames.add(state.characterName()))
						{
							blockers.add("ownership.character_name_duplicate:" + state.characterName());
						}

						final AccountRow account = findAccount(connection, state.reservedAccount());
						if (account == null)
						{
							if (state.creationStage().ordinal() >= CreationStage.ACCOUNT_VERIFIED.ordinal())
							{
								blockers.add("ownership.account_missing:" + profileId);
							}
						}
						else if (!account.password().equals(state.ownershipToken()) || (account.accessLevel() != DISABLED_ACCOUNT_ACCESS_LEVEL))
						{
							blockers.add("ownership.account_mismatch:" + profileId);
						}
						else
						{
							accountNames.add(state.reservedAccount());
						}

						final Integer profileCharacterId = nullableInt(result, "character_object_id");
						final CharacterLookup characters = findCharacters(connection, state, profileCharacterId);
						final CharacterRow exactCharacter = characters.exact();
						if (characters.conflict())
						{
							blockers.add("ownership.character_conflict:" + profileId);
						}
						if (exactCharacter == null)
						{
							if ((profileCharacterId != null) || (state.actualCharacterObjectId() != null) || (state.creationStage().ordinal() >= CreationStage.CHARACTER_CREATED.ordinal()))
							{
								blockers.add("ownership.character_missing:" + profileId);
							}
						}
						else
						{
							if (((profileCharacterId != null) && !profileCharacterId.equals(exactCharacter.objectId())) ||
								((state.expectedCharacterObjectId() != null) && !state.expectedCharacterObjectId().equals(exactCharacter.objectId())) ||
								((state.actualCharacterObjectId() != null) && !state.actualCharacterObjectId().equals(exactCharacter.objectId())))
							{
								blockers.add("ownership.character_link_mismatch:" + profileId);
							}
							if (!seenCharacterIds.add(exactCharacter.objectId()))
							{
								blockers.add("ownership.character_duplicate:" + exactCharacter.objectId());
							}
							characterObjectIds.add(exactCharacter.objectId());
						}
						identities.add(new OwnedIdentity(profileId, result.getLong("profile_row_version"), result.getLong("component_row_version"), payload, state.reservedAccount(), state.ownershipToken(), state.characterName(), exactCharacter == null ? null : exactCharacter.objectId()));
					}
				}
			}

			final List<Integer> ids = characterObjectIds.stream().sorted().toList();
			final List<String> accounts = accountNames.stream().sorted().toList();
			final Map<String, Long> deleteCounts = deleteCounts(connection, ids, accounts, identities.stream().map(OwnedIdentity::characterName).sorted().toList());
			final Map<String, Long> preserveCounts = preserveCounts(connection, ids);
			sharedBlockers(connection, ids, blockers);
			final long clanComponents = scalar(connection, "SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='clan.organization'");
			if (clanComponents > 0)
			{
				blockers.add("shared.clan_organization_component:" + clanComponents);
			}
			blockers.sort(String::compareTo);
			final String snapshotHash = snapshotHash(identities, deleteCounts, preserveCounts, blockers);
			return new Inspection(identities, ids, accounts, deleteCounts, preserveCounts, blockers, snapshotHash);
		}

		private static AccountRow findAccount(Connection connection, String accountName) throws SQLException
		{
			try (PreparedStatement statement = connection.prepareStatement("SELECT password,accessLevel FROM accounts WHERE login=?"))
			{
				statement.setString(1, accountName);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						return null;
					}
					final AccountRow row = new AccountRow(result.getString(1), result.getInt(2));
					if (result.next())
					{
						throw new IllegalStateException("Reserved account lookup returned duplicate rows.");
					}
					return row;
				}
			}
		}

		private static CharacterLookup findCharacters(Connection connection, PhantomPopulationState state, Integer profileCharacterId) throws SQLException
		{
			try (PreparedStatement statement = connection.prepareStatement("SELECT charId,char_name,account_name FROM characters WHERE account_name=? OR char_name=? OR charId=? OR charId=? ORDER BY charId"))
			{
				statement.setString(1, state.reservedAccount());
				statement.setString(2, state.characterName());
				statement.setInt(3, state.expectedCharacterObjectId() == null ? 0 : state.expectedCharacterObjectId());
				statement.setInt(4, profileCharacterId == null ? 0 : profileCharacterId);
				CharacterRow exact = null;
				boolean conflict = false;
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						final CharacterRow row = new CharacterRow(result.getInt(1), result.getString(2), result.getString(3));
						if (row.characterName().equals(state.characterName()) && row.accountName().equals(state.reservedAccount()))
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
				return new CharacterLookup(exact, conflict);
			}
		}

		private static Map<String, Long> deleteCounts(Connection connection, List<Integer> ids, List<String> accounts, List<String> characterNames) throws SQLException
		{
			final Map<String, Long> counts = new LinkedHashMap<>();
			counts.put("phantom_profiles", scalar(connection, "SELECT COUNT(*) FROM phantom_profiles"));
			counts.put("phantom_profile_components", scalar(connection, "SELECT COUNT(*) FROM phantom_profile_components"));
			counts.put("phantom_economy_operations", scalar(connection, "SELECT COUNT(*) FROM phantom_economy_operations"));
			counts.put("phantom_economy_reservations", scalar(connection, "SELECT COUNT(*) FROM phantom_economy_reservations"));
			counts.put("phantom_economy_audit", scalar(connection, "SELECT COUNT(*) FROM phantom_economy_audit"));
			counts.put("phantom_economy_offers", scalar(connection, "SELECT COUNT(*) FROM phantom_economy_offers"));
			for (IdRule rule : PRIVATE_ID_RULES)
			{
				counts.put(rule.table(), countByIds(connection, "SELECT COUNT(*) FROM " + rule.table() + " WHERE " + rule.column() + " IN (%s)", ids, false));
			}
			counts.put("character_contacts.safe_detach", countByIds(connection, "SELECT COUNT(*) FROM character_contacts WHERE charId IN (%s) OR contactId IN (%s)", ids, true));
			counts.put("character_friends.safe_detach", countByIds(connection, "SELECT COUNT(*) FROM character_friends WHERE charId IN (%s) OR friendId IN (%s)", ids, true));
			counts.put("character_offline_play_group.safe_detach", countByIds(connection, "SELECT COUNT(*) FROM character_offline_play_group WHERE leaderId IN (%s) OR charId IN (%s)", ids, true));
			counts.put("character_pet_skills_save", countByIds(connection, "SELECT COUNT(*) FROM character_pet_skills_save WHERE petObjItemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false));
			counts.put("pets", countByIds(connection, "SELECT COUNT(*) FROM pets WHERE ownerId IN (%s) OR item_obj_id IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, true));
			counts.put("item_attributes", countByIds(connection, "SELECT COUNT(*) FROM item_attributes WHERE itemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false));
			counts.put("item_elementals", countByIds(connection, "SELECT COUNT(*) FROM item_elementals WHERE itemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false));
			counts.put("item_variables", countByIds(connection, "SELECT COUNT(*) FROM item_variables WHERE id IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false));
			counts.put("items", countByIds(connection, "SELECT COUNT(*) FROM items WHERE owner_id IN (%s)", ids, false));
			counts.put("character_transmogs", countByStrings(connection, "SELECT COUNT(*) FROM character_transmogs WHERE owner IN (%s)", characterNames));
			counts.put("characters", countByIds(connection, "SELECT COUNT(*) FROM characters WHERE charId IN (%s)", ids, false));
			counts.put("account_gsdata", countByStrings(connection, "SELECT COUNT(*) FROM account_gsdata WHERE account_name IN (%s)", accounts));
			counts.put("account_premium", countByStrings(connection, "SELECT COUNT(*) FROM account_premium WHERE account_name IN (%s)", accounts));
			counts.put("accounts", countByStrings(connection, "SELECT COUNT(*) FROM accounts WHERE login IN (%s)", accounts));
			return counts;
		}

		private static Map<String, Long> preserveCounts(Connection connection, List<Integer> ids) throws SQLException
		{
			final Map<String, Long> counts = new LinkedHashMap<>();
			counts.put("messages.world_effect", countByIds(connection, "SELECT COUNT(*) FROM messages WHERE senderId IN (%s) OR receiverId IN (%s)", ids, true));
			counts.put("forums.world_history", countByIds(connection, "SELECT COUNT(*) FROM forums WHERE forum_owner_id IN (%s)", ids, false));
			counts.put("posts.world_history", countByIds(connection, "SELECT COUNT(*) FROM posts WHERE post_ownerid IN (%s)", ids, false));
			counts.put("topics.world_history", countByIds(connection, "SELECT COUNT(*) FROM topic WHERE topic_ownerid IN (%s)", ids, false));
			counts.put("heroes_diary.world_history", countByIds(connection, "SELECT COUNT(*) FROM heroes_diary WHERE charId IN (%s)", ids, false));
			counts.put("olympiad_fights.world_history", countByIds(connection, "SELECT COUNT(*) FROM olympiad_fights WHERE charOneId IN (%s) OR charTwoId IN (%s)", ids, true));
			counts.put("prime_shop_transactions.audit_history", countByIds(connection, "SELECT COUNT(*) FROM prime_shop_transactions WHERE charId IN (%s)", ids, false));
			return counts;
		}

		private static void sharedBlockers(Connection connection, List<Integer> ids, List<String> blockers) throws SQLException
		{
			final long clanMembership = countByIds(connection, "SELECT COUNT(*) FROM characters WHERE charId IN (%s) AND clanid<>0", ids, false);
			if (clanMembership > 0)
			{
				blockers.add("shared.clan_membership:" + clanMembership);
			}
			for (SharedRule rule : SHARED_BLOCKERS)
			{
				final String sql = rule.secondColumn() == null ?
					"SELECT COUNT(*) FROM " + rule.table() + " WHERE " + rule.firstColumn() + " IN (%s)" :
					"SELECT COUNT(*) FROM " + rule.table() + " WHERE " + rule.firstColumn() + " IN (%s) OR " + rule.secondColumn() + " IN (%s)";
				final long count = countByIds(connection, sql, ids, rule.secondColumn() != null);
				if (count > 0)
				{
					blockers.add("shared." + rule.name() + ":" + count);
				}
			}
			final long incomingAttachments = countIncomingHumanAttachments(connection, ids);
			if (incomingAttachments > 0)
			{
				blockers.add("shared.incoming_human_mail_attachment:" + incomingAttachments);
			}
		}

		private static long countIncomingHumanAttachments(Connection connection, List<Integer> ids) throws SQLException
		{
			if (ids.isEmpty())
			{
				return 0;
			}
			final String placeholders = placeholders(ids.size());
			final String sql = "SELECT COUNT(*) FROM messages WHERE receiverId IN (" + placeholders + ") AND senderId<>0 AND senderId NOT IN (" + placeholders + ") AND hasAttachments='true'";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				int index = bind(statement, ids, 1);
				bind(statement, ids, index);
				try (ResultSet result = statement.executeQuery())
				{
					result.next();
					return result.getLong(1);
				}
			}
		}

		private static String snapshotHash(List<OwnedIdentity> identities, Map<String, Long> deleteCounts, Map<String, Long> preserveCounts, List<String> blockers)
		{
			final StringBuilder builder = new StringBuilder();
			identities.stream().sorted(Comparator.comparingLong(OwnedIdentity::profileId)).forEach(identity ->
				builder.append(identity.profileId()).append('|').append(identity.profileRowVersion()).append('|').append(identity.componentRowVersion()).append('|')
					.append(sha256(Base64.getEncoder().encodeToString(identity.populationPayload()))).append('|').append(identity.accountName()).append('|')
					.append(identity.characterObjectId()).append(';'));
			deleteCounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> builder.append("D:").append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
			preserveCounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> builder.append("P:").append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
			blockers.forEach(blocker -> builder.append("B:").append(blocker).append(';'));
			return sha256(builder.toString());
		}

		private Mutation reset(Inspection expected, FailureInjector failureInjector)
		{
			try (Connection connection = DatabaseFactory.getConnection())
			{
				final boolean originalAutoCommit = connection.getAutoCommit();
				connection.setAutoCommit(false);
				try
				{
					lockOwnership(connection, expected);
					final Inspection locked = inspect(connection);
					if (!locked.blockers().isEmpty() || !expected.snapshotHash().equals(locked.snapshotHash()))
					{
						throw new IllegalStateException("Reset snapshot changed before the destructive boundary.");
					}

					final List<Integer> ids = locked.characterObjectIds();
					final List<String> accounts = locked.accountNames();
					final List<String> names = locked.identities().stream().map(OwnedIdentity::characterName).sorted().toList();
					deleteByIds(connection, "DELETE FROM character_contacts WHERE charId IN (%s) OR contactId IN (%s)", ids, true);
					deleteByIds(connection, "DELETE FROM character_friends WHERE charId IN (%s) OR friendId IN (%s)", ids, true);
					deleteByIds(connection, "DELETE FROM character_offline_play_group WHERE leaderId IN (%s) OR charId IN (%s)", ids, true);
					failureInjector.after(FaultPoint.CLEANUP_BOUNDARY);

					for (IdRule rule : PRIVATE_ID_RULES)
					{
						deleteByIds(connection, "DELETE FROM " + rule.table() + " WHERE " + rule.column() + " IN (%s)", ids, false);
					}
					deleteByStrings(connection, "DELETE FROM character_transmogs WHERE owner IN (%s)", names);
					deleteByIds(connection, "DELETE FROM character_pet_skills_save WHERE petObjItemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false);
					deleteByIds(connection, "DELETE FROM pets WHERE ownerId IN (%s) OR item_obj_id IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, true);
					deleteByIds(connection, "DELETE FROM item_attributes WHERE itemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false);
					deleteByIds(connection, "DELETE FROM item_elementals WHERE itemId IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false);
					deleteByIds(connection, "DELETE FROM item_variables WHERE id IN (SELECT object_id FROM items WHERE owner_id IN (%s))", ids, false);
					deleteByIds(connection, "DELETE FROM items WHERE owner_id IN (%s)", ids, false);
					deleteByIds(connection, "DELETE FROM characters WHERE charId IN (%s)", ids, false);
					deleteByStrings(connection, "DELETE FROM account_gsdata WHERE account_name IN (%s)", accounts);
					deleteByStrings(connection, "DELETE FROM account_premium WHERE account_name IN (%s)", accounts);
					deleteByStrings(connection, "DELETE FROM accounts WHERE login IN (%s)", accounts);
					deleteByLongs(connection, "DELETE FROM phantom_profiles WHERE profile_id IN (%s)", locked.identities().stream().map(OwnedIdentity::profileId).toList());

					final Map<String, Long> residue = deleteCounts(connection, ids, accounts, names);
					for (Map.Entry<String, Long> entry : residue.entrySet())
					{
						if (entry.getValue() != 0)
						{
							throw new IllegalStateException("Reset residue remains in " + entry.getKey() + ".");
						}
					}
					failureInjector.after(FaultPoint.BEFORE_COMMIT);
					connection.commit();
					return new Mutation(locked.identities().size(), ids);
				}
				catch (Throwable throwable)
				{
					try
					{
						connection.rollback();
					}
					catch (SQLException rollbackFailure)
					{
						throwable.addSuppressed(rollbackFailure);
					}
					if (throwable instanceof RuntimeException runtimeException)
					{
						throw runtimeException;
					}
					throw new IllegalStateException("Transactional Phantom reset failed.", throwable);
				}
				finally
				{
					connection.setAutoCommit(originalAutoCommit);
				}
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not execute transactional Phantom reset.", e);
			}
		}

		private static void lockOwnership(Connection connection, Inspection expected) throws SQLException
		{
			try (PreparedStatement statement = connection.prepareStatement("SELECT profile_id FROM phantom_profiles ORDER BY profile_id FOR UPDATE");
				ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					// Consume the exact locked profile set.
				}
			}
			lockByIds(connection, "SELECT charId FROM characters WHERE charId IN (%s) FOR UPDATE", expected.characterObjectIds());
			lockByStrings(connection, "SELECT login FROM accounts WHERE login IN (%s) FOR UPDATE", expected.accountNames());
		}

		private static void lockByIds(Connection connection, String template, List<Integer> values) throws SQLException
		{
			if (values.isEmpty())
			{
				return;
			}
			try (PreparedStatement statement = connection.prepareStatement(template.formatted(placeholders(values.size()))))
			{
				bind(statement, values, 1);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						// Consume all locked rows.
					}
				}
			}
		}

		private static void lockByStrings(Connection connection, String template, List<String> values) throws SQLException
		{
			if (values.isEmpty())
			{
				return;
			}
			try (PreparedStatement statement = connection.prepareStatement(template.formatted(placeholders(values.size()))))
			{
				bindStrings(statement, values, 1);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						// Consume all locked rows.
					}
				}
			}
		}

		private static long countByIds(Connection connection, String template, List<Integer> ids, boolean bindTwice) throws SQLException
		{
			if (ids.isEmpty())
			{
				return 0;
			}
			final String placeholders = placeholders(ids.size());
			final String sql = bindTwice ? template.formatted(placeholders, placeholders) : template.formatted(placeholders);
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				final int next = bind(statement, ids, 1);
				if (bindTwice)
				{
					bind(statement, ids, next);
				}
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new IllegalStateException("Reset count query returned no row.");
					}
					return result.getLong(1);
				}
			}
		}

		private static long countByStrings(Connection connection, String template, List<String> values) throws SQLException
		{
			if (values.isEmpty())
			{
				return 0;
			}
			try (PreparedStatement statement = connection.prepareStatement(template.formatted(placeholders(values.size()))))
			{
				bindStrings(statement, values, 1);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new IllegalStateException("Reset string count query returned no row.");
					}
					return result.getLong(1);
				}
			}
		}

		private static void deleteByIds(Connection connection, String template, List<Integer> ids, boolean bindTwice) throws SQLException
		{
			for (int offset = 0; offset < ids.size(); offset += SQL_BATCH_SIZE)
			{
				final List<Integer> batch = ids.subList(offset, Math.min(offset + SQL_BATCH_SIZE, ids.size()));
				final String placeholders = placeholders(batch.size());
				final String sql = bindTwice ? template.formatted(placeholders, placeholders) : template.formatted(placeholders);
				try (PreparedStatement statement = connection.prepareStatement(sql))
				{
					final int next = bind(statement, batch, 1);
					if (bindTwice)
					{
						bind(statement, batch, next);
					}
					statement.executeUpdate();
				}
			}
		}

		private static void deleteByStrings(Connection connection, String template, List<String> values) throws SQLException
		{
			for (int offset = 0; offset < values.size(); offset += SQL_BATCH_SIZE)
			{
				final List<String> batch = values.subList(offset, Math.min(offset + SQL_BATCH_SIZE, values.size()));
				try (PreparedStatement statement = connection.prepareStatement(template.formatted(placeholders(batch.size()))))
				{
					bindStrings(statement, batch, 1);
					statement.executeUpdate();
				}
			}
		}

		private static void deleteByLongs(Connection connection, String template, List<Long> values) throws SQLException
		{
			for (int offset = 0; offset < values.size(); offset += SQL_BATCH_SIZE)
			{
				final List<Long> batch = values.subList(offset, Math.min(offset + SQL_BATCH_SIZE, values.size()));
				try (PreparedStatement statement = connection.prepareStatement(template.formatted(placeholders(batch.size()))))
				{
					for (int index = 0; index < batch.size(); index++)
					{
						statement.setLong(index + 1, batch.get(index));
					}
					statement.executeUpdate();
				}
			}
		}

		private static long scalar(Connection connection, String sql) throws SQLException
		{
			try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new IllegalStateException("Reset scalar query returned no row.");
				}
				return result.getLong(1);
			}
		}

		private static Integer nullableInt(ResultSet result, String column) throws SQLException
		{
			final int value = result.getInt(column);
			return result.wasNull() ? null : value;
		}

		private static int bind(PreparedStatement statement, List<Integer> values, int firstIndex) throws SQLException
		{
			int index = firstIndex;
			for (int value : values)
			{
				statement.setInt(index++, value);
			}
			return index;
		}

		private static int bindStrings(PreparedStatement statement, List<String> values, int firstIndex) throws SQLException
		{
			int index = firstIndex;
			for (String value : values)
			{
				statement.setString(index++, value);
			}
			return index;
		}

		private static String placeholders(int count)
		{
			if (count < 1)
			{
				throw new IllegalArgumentException("At least one SQL placeholder is required.");
			}
			final StringBuilder builder = new StringBuilder((count * 2) - 1);
			for (int index = 0; index < count; index++)
			{
				if (index > 0)
				{
					builder.append(',');
				}
				builder.append('?');
			}
			return builder.toString();
		}

		private record IdRule(String table, String column)
		{
		}

		private record SharedRule(String name, String table, String firstColumn, String secondColumn)
		{
		}

		private record AccountRow(String password, int accessLevel)
		{
		}

		private record CharacterRow(int objectId, String characterName, String accountName)
		{
		}

		private record CharacterLookup(CharacterRow exact, boolean conflict)
		{
		}
	}

	private static String sha256(String value)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}
}
