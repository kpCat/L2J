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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.enums.StatType;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.managers.RecipeCraftObserver;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOffer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOffer.CounterpartyKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyMaterializationLifecycle;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Identity;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Kind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.DirectTradeService;
import org.l2jmobius.gameserver.services.ManufactureService;
import org.l2jmobius.gameserver.services.PrivateStoreService;

public final class PhantomMultipartyEconomySuite implements PhantomTestSuite
{
	public enum Mode
	{
		PARTICIPANT_INDEX(false),
		OFFER_LIFECYCLE(false),
		DIRECT_TRADE(true),
		PRIVATE_STORE_BUY(true),
		PRIVATE_STORE_SELL(true),
		MANUFACTURE(true),
		RESTART_FAULT(false),
		PERFORMANCE(false);

		private final boolean _players;

		Mode(boolean players)
		{
			_players = players;
		}
	}

	private static final long SEED = 22002202L;
	private static final String TEST_DATABASE = "l2jmobiush5_phantom_test";
	private final Mode _mode;
	private PhantomEconomyPolicy _policy;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _profiles;
	private PhantomProfile _firstProfile;
	private PhantomProfile _secondProfile;
	private PhantomMaterializationService _materialization;
	private Player _first;
	private Player _second;
	private int _tradeItemId;
	private long _firstTradeBaseline;
	private long _secondTradeBaseline;
	private long _firstAdenaBaseline;
	private long _secondAdenaBaseline;

	public PhantomMultipartyEconomySuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "economy-c2-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 022 Checkpoint 2 mode used the wrong seed.");
		_policy = PhantomEconomyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/economy/high-five-economy-v1.xml"));
		if (!_mode._players)
		{
			final String config = System.getProperty("phantom.test.config");
			PhantomAssertions.assertTrue((config != null) && !config.isBlank(), "Economy C2 test DB config is missing.");
			PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(config));
			return;
		}
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		_firstProfile = _profiles.create(_environment.primary().objectId());
		_secondProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "C2 materialization did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_firstProfile.profileId()).status(), "First C2 fixture did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_secondProfile.profileId()).status(), "Second C2 fixture did not materialize.");
		_first = World.getInstance().getPlayer(_environment.primary().objectId());
		_second = World.getInstance().getPlayer(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_first != null) && (_second != null), "C2 materialized Players are absent.");
		_first.setXYZ(_second.getX(), _second.getY(), _second.getZ());
		_tradeItemId = selectTradeItemId();
		_firstTradeBaseline = itemCount(_first, _tradeItemId);
		_secondTradeBaseline = itemCount(_second, _tradeItemId);
		_firstAdenaBaseline = _environment.primary().fixtureItemBaseline();
		_secondAdenaBaseline = _environment.observer().fixtureItemBaseline();
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment == null)
		{
			DatabaseFactory.close();
			return;
		}
		DirectTradeService.getInstance().cancel(_first);
		DirectTradeService.getInstance().cancel(_second);
		resetStore(_first);
		resetStore(_second);
		_first.setCrafting(false);
		_second.setCrafting(false);
		restoreItemCount(_first, _tradeItemId, _firstTradeBaseline);
		restoreItemCount(_second, _tradeItemId, _secondTradeBaseline);
		restoreItemCount(_first, Inventory.ADENA_ID, _firstAdenaBaseline);
		restoreItemCount(_second, Inventory.ADENA_ID, _secondAdenaBaseline);
		PhantomAssertions.assertEquals(_firstAdenaBaseline, _first.getAdena(), "First C2 fixture Adena cleanup drifted before store.");
		PhantomAssertions.assertEquals(_secondAdenaBaseline, _second.getAdena(), "Second C2 fixture Adena cleanup drifted before store.");
		_materialization.shutdown();
		_first.stopAllTasks();
		_second.stopAllTasks();
		restorePersistedItemCount(_environment.primary().objectId(), Inventory.ADENA_ID, _firstAdenaBaseline);
		restorePersistedItemCount(_environment.observer().objectId(), Inventory.ADENA_ID, _secondAdenaBaseline);
		deleteProfile(_firstProfile.profileId());
		deleteProfile(_secondProfile.profileId());
		_environment.assertClean(_environment.primary(), _first);
		_environment.assertClean(_environment.observer(), _second);
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case PARTICIPANT_INDEX ->
			{
				registry.add("indexed-external-participant", this::testParticipantIndex);
				registry.add("indexed-link-drift-and-idempotent-revalidation", this::testParticipantLinkDrift);
				registry.add("packet-independent-source-boundary", this::testSourceBoundary);
			}
			case OFFER_LIFECYCLE ->
			{
				registry.add("immutable-content-authority", this::testOfferAuthority);
				registry.add("accept-reject-expire-cancel", this::testOfferLifecycle);
			}
			case DIRECT_TRADE ->
			{
				registry.add("phantom-phantom-exact-conservation", this::testDirectTrade);
				registry.add("six-step-durable-orchestration", this::testDirectTradeOrchestration);
				registry.add("external-confirmation-timeout-cleanup", this::testExternalDirectTradeLifetime);
				registry.add("full-direct-fault-cleanup-matrix", this::testDirectFaultCleanupMatrix);
			}
			case PRIVATE_STORE_BUY ->
			{
				registry.add("sell-and-package-exact-mutation", this::testPrivateStoreBuy);
				registry.add("store-visible-lifecycle", this::testStoreLifecycle);
			}
			case PRIVATE_STORE_SELL -> registry.add("buy-store-exact-mutation", this::testPrivateStoreSell);
			case MANUFACTURE -> registry.add("canonical-fee-ingredient-product-events", this::testManufacture);
			case RESTART_FAULT -> registry.add("observing-and-audit-reconciliation", this::testRestartReconciliation);
			case PERFORMANCE -> registry.add("bounded-offer-quote-cleanup-volume", this::testPerformance);
		}
	}

	private void testParticipantIndex(PhantomTestContext context) throws Exception
	{
		final String migration = Files.readString(context.moduleRoot().resolve("dist/db_installer/sql/game/phantom_reservations_checkpoint2.sql"));
		PhantomAssertions.assertTrue(migration.contains("idx_phantom_economy_reservations_profile_operation"), "C2 participant index migration is absent.");
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("EXPLAIN SELECT operation_id FROM phantom_economy_reservations FORCE INDEX (idx_phantom_economy_reservations_profile_operation) WHERE profile_id=? ORDER BY operation_id LIMIT 33"))
		{
			statement.setLong(1, 1);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Participant EXPLAIN returned no row.");
				PhantomAssertions.assertEquals("idx_phantom_economy_reservations_profile_operation", row.getString("key"), "Participant lookup did not use the C2 index.");
			}
		}
		final PhantomProfile profile = createProfile(982001);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		try
		{
			reservations.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 982001, 2200220201L, Kind.DIRECT_TRADE, "participant-index", System.currentTimeMillis());
			final Reservation external = new Reservation(0, 982002, 0, ResourceKind.ITEM_OBJECT, 982102, 57, 1, 1, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, List.of(itemReservation(profile.profileId(), 982001, 982101), external)).status(), "External participant reservation was rejected.");
			PhantomAssertions.assertEquals(List.of(982001, 982002), reservations.findReservations(operation.operationId()).stream().map(Reservation::ownerObjectId).sorted().distinct().toList(), "Participant-neutral character set drifted.");
		}
		finally
		{
			reservations.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testSourceBoundary(PhantomTestContext context) throws Exception
	{
		final String root = context.moduleRoot().toString();
		final String orchestrator = Files.readString(Path.of(root, "java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.java"));
		PhantomAssertions.assertFalse(orchestrator.contains("ClientPacket"), "Multiparty service references ClientPacket.");
		PhantomAssertions.assertFalse(orchestrator.contains("GameClient"), "Multiparty service references GameClient.");
		PhantomAssertions.assertFalse(orchestrator.contains("ThreadPool"), "Multiparty service creates asynchronous work.");
		for (String packet : List.of("TradeRequest.java", "AnswerTradeRequest.java", "AddTradeItem.java", "TradeDone.java", "RequestPrivateStoreBuy.java", "RequestPrivateStoreSell.java", "RequestRecipeShopMakeItem.java"))
		{
			final String source = Files.readString(Path.of(root, "java/org/l2jmobius/gameserver/network/clientpackets", packet));
			PhantomAssertions.assertTrue(source.contains("Service.getInstance()"), "Ordinary packet is not a thin canonical-service adapter: " + packet);
		}
	}

	private void testParticipantLinkDrift(PhantomTestContext context) throws Exception
	{
		final int initiatorCharacterId = 982011;
		final int participantCharacterId = 982012;
		final PhantomProfile initiator = createProfile(initiatorCharacterId);
		final PhantomProfile participant = createProfile(participantCharacterId);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyMaterializationLifecycle lifecycle = new PhantomEconomyMaterializationLifecycle(reservations, Clock.systemUTC());
		final long now = System.currentTimeMillis();
		try
		{
			reservations.start();
			final PhantomEconomyOperation stable = operation(initiator.profileId(), initiatorCharacterId, 2200220211L, Kind.DIRECT_TRADE, "participant-stable", now);
			reserveParticipants(reservations, stable, initiator, initiatorCharacterId, participant, participantCharacterId, 982111);
			lifecycle.beforeMaterialize(participant.profileId(), participantCharacterId);
			assertTerminalWithoutClaims(reservations, stable.operationId(), State.ABORTED, "Stable reservation-only participant was not found by profile index.");

			final PhantomEconomyOperation changed = operation(initiator.profileId(), initiatorCharacterId, 2200220212L, Kind.DIRECT_TRADE, "participant-changed", now + 10);
			reserveParticipants(reservations, changed, initiator, initiatorCharacterId, participant, participantCharacterId, 982112);
			updateProfileCharacter(participant.profileId(), participantCharacterId + 100);
			lifecycle.beforeMaterialize(participant.profileId(), participantCharacterId);
			assertTerminalWithoutClaims(reservations, changed.operationId(), State.ABORTED, "Changed participant link hid the indexed operation.");

			updateProfileCharacter(participant.profileId(), participantCharacterId);
			final PhantomEconomyOperation unlinked = operation(initiator.profileId(), initiatorCharacterId, 2200220213L, Kind.DIRECT_TRADE, "participant-null", now + 20);
			reserveParticipants(reservations, unlinked, initiator, initiatorCharacterId, participant, participantCharacterId, 982113);
			updateProfileCharacter(participant.profileId(), null);
			reservations.beforeBoundary(participant.profileId(), now + 21);
			assertTerminalWithoutClaims(reservations, unlinked.operationId(), State.ABORTED, "NULL participant link hid the indexed operation.");

			updateProfileCharacter(participant.profileId(), participantCharacterId);
			final PhantomEconomyOperation idempotentReserved = operation(initiator.profileId(), initiatorCharacterId, 2200220214L, Kind.DIRECT_TRADE, "participant-idempotent-reserved", now + 30);
			reserveParticipants(reservations, idempotentReserved, initiator, initiatorCharacterId, participant, participantCharacterId, 982114);
			updateProfileCharacter(participant.profileId(), participantCharacterId + 100);
			final PhantomEconomyReservationService.TransitionResult reservedReplay = reservations.transition(idempotentReserved.operationId(), State.PREPARED, State.RESERVED, now + 31, null);
			PhantomAssertions.assertEquals(State.ABORTED, reservedReplay.state(), "RESERVED idempotent replay hid participant drift.");
			assertTerminalWithoutClaims(reservations, idempotentReserved.operationId(), State.ABORTED, "RESERVED replay retained drifted claims.");

			updateProfileCharacter(participant.profileId(), participantCharacterId);
			final PhantomEconomyOperation idempotentObserving = operation(initiator.profileId(), initiatorCharacterId, 2200220215L, Kind.DIRECT_TRADE, "participant-idempotent-observing", now + 40);
			reserveParticipants(reservations, idempotentObserving, initiator, initiatorCharacterId, participant, participantCharacterId, 982115);
			reservations.transition(idempotentObserving.operationId(), State.RESERVED, State.DISPATCHING, now + 41, null);
			reservations.transition(idempotentObserving.operationId(), State.DISPATCHING, State.OBSERVING, now + 42, null);
			updateProfileCharacter(participant.profileId(), participantCharacterId + 100);
			final PhantomEconomyReservationService.TransitionResult observingReplay = reservations.transition(idempotentObserving.operationId(), State.DISPATCHING, State.OBSERVING, now + 43, null);
			PhantomAssertions.assertEquals(State.INCONSISTENT, observingReplay.state(), "OBSERVING idempotent replay hid participant drift.");
			assertTerminalWithoutClaims(reservations, idempotentObserving.operationId(), State.INCONSISTENT, "OBSERVING replay retained drifted claims.");

			updateProfileCharacter(participant.profileId(), participantCharacterId);
			final PhantomEconomyOperation deleted = operation(initiator.profileId(), initiatorCharacterId, 2200220216L, Kind.DIRECT_TRADE, "participant-deleted", now + 50);
			reserveParticipants(reservations, deleted, initiator, initiatorCharacterId, participant, participantCharacterId, 982116);
			deleteProfile(participant.profileId());
			reservations.beforeBoundary(participant.profileId(), now + 51);
			assertTerminalWithoutClaims(reservations, deleted.operationId(), State.ABORTED, "Deleted participant row hid the indexed operation.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentOperations(), "Participant link-drift fixtures retained active operations.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Participant link-drift fixtures retained reservations.");
			context.record("economy.c2.participantLinkDrift", List.of("stable", "changed", "null", "idempotent-reserved", "idempotent-observing", "deleted"));
		}
		finally
		{
			reservations.shutdown(now + 60);
			deleteProfile(initiator.profileId());
			deleteProfile(participant.profileId());
		}
	}

	private void testOfferAuthority(PhantomTestContext context)
	{
		final long now = System.currentTimeMillis();
		final PhantomEconomyOffer first = offer(983001, 983101, Kind.DIRECT_TRADE, "one", 1, now);
		final PhantomEconomyOffer replay = offer(983001, 983101, Kind.DIRECT_TRADE, "one", 1, now);
		final PhantomEconomyOffer changed = offer(983001, 983101, Kind.DIRECT_TRADE, "two", 1, now);
		PhantomAssertions.assertEquals(first.offerId(), replay.offerId(), "Offer identity is not deterministic.");
		PhantomAssertions.assertEquals(first.contentHash(), replay.contentHash(), "Offer content hash is not deterministic.");
		PhantomAssertions.assertFalse(first.contentHash().equals(changed.contentHash()), "Offer mutation did not change authority hash.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> offer(983001, 983101, Kind.DIRECT_TRADE, "x".repeat(4097), 1, now), "Oversized offer payload was accepted.");
	}

	private void testOfferLifecycle(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(984001);
		final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
		final long now = System.currentTimeMillis();
		try
		{
			final PhantomEconomyOffer accepted = offer(profile.profileId(), 984002, Kind.DIRECT_TRADE, "accept", 11, now);
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.TRANSITIONED, offers.create(accepted), "Offer draft failed.");
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.IDEMPOTENT, offers.create(accepted), "Offer draft replay was not idempotent.");
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.TRANSITIONED, offers.offer(accepted.offerId(), 0, now + 1), "Offer publication failed.");
			final PhantomEconomyOffer offered = offers.find(accepted.offerId()).orElseThrow();
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.OFFERED, offered.state(), "Published offer state drifted.");
			PhantomAssertions.assertEquals(1L, offered.rowVersion(), "Published offer row version drifted.");
			PhantomAssertions.assertTrue(offered.expiresEpochMillis() > (now + 2), "Published offer expiry drifted: " + offered.expiresEpochMillis() + " <= " + (now + 2));
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.CONFLICT, offers.accept(offered.offerId(), offered.contentHash(), 0, now + 2), "Stale offer row version was accepted.");
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.TRANSITIONED, offers.accept(offered.offerId(), offered.contentHash(), offered.rowVersion(), now + 2), "Offer acceptance failed.");
			final PhantomEconomyOffer rejected = offer(profile.profileId(), 984005, Kind.PLAYER_MANUFACTURE, "reject", 14, now);
			offers.create(rejected);
			offers.offer(rejected.offerId(), 0, now + 1);
			final PhantomEconomyOffer rejectable = offers.find(rejected.offerId()).orElseThrow();
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.TRANSITIONED, offers.reject(rejectable.offerId(), rejectable.rowVersion(), now + 2, "test.rejected"), "Offer rejection failed.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.REJECTED, offers.find(rejected.offerId()).orElseThrow().state(), "Rejected offer state drifted.");
			final PhantomEconomyOffer cancelled = offer(profile.profileId(), 984003, Kind.PRIVATE_STORE_BUY, "cancel", 12, now);
			offers.create(cancelled);
			offers.offer(cancelled.offerId(), 0, now + 1);
			final PhantomEconomyOffer current = offers.find(cancelled.offerId()).orElseThrow();
			PhantomAssertions.assertEquals(PhantomEconomyOfferService.Status.TRANSITIONED, offers.cancel(current.offerId(), current.rowVersion(), now + 2, "test.cancel"), "Offer cancellation failed.");
			final PhantomEconomyOffer expiring = offer(profile.profileId(), 984004, Kind.PRIVATE_STORE_SELL, "expire", 13, now, now + 5);
			offers.create(expiring);
			offers.offer(expiring.offerId(), 0, now + 1);
			PhantomAssertions.assertEquals(1, offers.expireDue(now + 6, 10), "Offered expiry did not terminalize exactly one row.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.EXPIRED, offers.find(expiring.offerId()).orElseThrow().state(), "Expired offer state drifted.");
		}
		finally
		{
			deleteProfile(profile.profileId());
		}
	}

	private void testDirectTrade(PhantomTestContext context) throws Exception
	{
		final long boundaryNow = System.currentTimeMillis();
		final PhantomEconomyOfferService boundaryOffers = new PhantomEconomyOfferService();
		final PhantomEconomyOffer boundaryOffer = offer(_firstProfile.profileId(), _second.getObjectId(), Kind.DIRECT_TRADE, "materialization-boundary", 2200220288L, boundaryNow);
		boundaryOffers.create(boundaryOffer);
		boundaryOffers.offer(boundaryOffer.offerId(), 0, boundaryNow + 1);
		final PhantomEconomyOffer offeredBoundary = boundaryOffers.find(boundaryOffer.offerId()).orElseThrow();
		boundaryOffers.accept(offeredBoundary.offerId(), offeredBoundary.contentHash(), offeredBoundary.rowVersion(), boundaryNow + 2);
		final PhantomEconomyMaterializationLifecycle boundary = new PhantomEconomyMaterializationLifecycle(new PhantomEconomyReservationService(_policy), boundaryOffers, Clock.systemUTC());
		boundary.beforeMaterialize(_firstProfile.profileId(), _first.getObjectId());
		PhantomAssertions.assertThrows(PhantomEconomyReservationService.EconomyConflictException.class, () -> boundary.beforeStore(_firstProfile.profileId(), _first), "Accepted offer did not block dematerialization.");
		final PhantomEconomyOffer acceptedBoundary = boundaryOffers.find(boundaryOffer.offerId()).orElseThrow();
		boundaryOffers.cancel(acceptedBoundary.offerId(), acceptedBoundary.rowVersion(), boundaryNow + 3, "test.complete");
		fund(_first, _tradeItemId, 5);
		fund(_first, Inventory.ADENA_ID, 20);
		final long globalItems = itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId);
		final long globalAdena = _first.getAdena() + _second.getAdena();
		PhantomAssertions.assertEquals(DirectTradeService.Result.REQUESTED, DirectTradeService.getInstance().request(_first, _second.getObjectId()), "Canonical trade request failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.ACCEPTED, DirectTradeService.getInstance().answer(_second, true), "Canonical trade consent failed.");
		final Item item = _first.getInventory().getItemByItemId(_tradeItemId);
		final Item adena = _first.getInventory().getAdenaInstance();
		PhantomAssertions.assertEquals(DirectTradeService.Result.UPDATED, DirectTradeService.getInstance().addItem(_first, 0, item.getObjectId(), 2), "Trade item line failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.UPDATED, DirectTradeService.getInstance().addItem(_first, 0, adena.getObjectId(), 7), "Trade Adena line failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.CONFIRMED, DirectTradeService.getInstance().finish(_first, true), "First confirmation failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.CONFIRMED, DirectTradeService.getInstance().finish(_second, true), "Second confirmation did not commit ordinary canonical trade.");
		PhantomAssertions.assertEquals(globalItems, itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId), "Direct trade duplicated or lost items.");
		PhantomAssertions.assertEquals(globalAdena, _first.getAdena() + _second.getAdena(), "Direct trade duplicated or lost Adena.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.REQUESTED, DirectTradeService.getInstance().request(_first, _second.getObjectId()), "Refusal fixture request failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.REJECTED, DirectTradeService.getInstance().answer(_second, false), "Explicit direct-trade refusal was not canonical.");
		PhantomAssertions.assertTrue((_first.getActiveTradeList() == null) && (_second.getActiveTradeList() == null) && (_second.getActiveRequester() == null), "Refusal retained direct-trade state.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.REQUESTED, DirectTradeService.getInstance().request(_first, _second.getObjectId()), "Confirmation fixture request failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.ACCEPTED, DirectTradeService.getInstance().answer(_second, true), "Confirmation fixture consent failed.");
		final Item remaining = _first.getInventory().getItemByItemId(_tradeItemId);
		PhantomAssertions.assertEquals(DirectTradeService.Result.UPDATED, DirectTradeService.getInstance().addItem(_first, 0, remaining.getObjectId(), 1), "Confirmation fixture line failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.CONFIRMED, DirectTradeService.getInstance().finish(_first, true), "Confirmation fixture first confirmation failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.REJECTED, DirectTradeService.getInstance().addItem(_first, 0, remaining.getObjectId(), 2), "Confirmed direct-trade offer remained mutable.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.CANCELLED, DirectTradeService.getInstance().finish(_second, false), "Confirmed direct-trade cancellation failed.");
		PhantomAssertions.assertTrue((_first.getActiveTradeList() == null) && (_second.getActiveTradeList() == null), "Cancellation retained direct-trade lists.");
		context.record("economy.c2.directTradeConserved", true);
	}

	private void testDirectTradeOrchestration(PhantomTestContext context)
	{
		fund(_first, _tradeItemId, 2);
		fund(_second, _tradeItemId, 2);
		final Item offered = _first.getInventory().getItemByItemId(_tradeItemId);
		final Item requested = _second.getInventory().getItemByItemId(_tradeItemId);
		final long now = System.currentTimeMillis();
		final long goalId = 2200220291L;
		final String key = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + (now + 60000) + ";150;0;0;c2;O:" + offered.getObjectId() + ":1;R:" + requested.getObjectId() + ":1";
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 0, now + 60000, Map.of(), "economy.c2.test", 0);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		goals.insert(_firstProfile.profileId(), goal);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		try
		{
			reservations.start();
			final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
			final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.discoverOrLoad(_firstProfile.profileId(), goal, now).status(), "Six-step discovery failed.");
			try (ActionLease busyCounterparty = _materialization.tryAcquireAction(_secondProfile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.ACTIVE_REQUIRED, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "Counterparty ActionLease conflict was not fail closed.");
				PhantomAssertions.assertTrue((_first.getActiveTradeList() == null) && (_second.getActiveTradeList() == null), "Lease-unavailable offer mutated direct-trade state.");
			}
			PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Lease conflict leaked initiator admission.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "Six-step consent failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.reserve(_firstProfile.profileId(), goal, 1, 1, now + 2).status(), "Six-step reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 3).status(), "Six-step dispatch failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.ACTIVE_REQUIRED, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.BACKGROUND, now + 4).status(), "Background direct-trade execution was admitted.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 4).status(), "Six-step observation/reconciliation failed.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Six-step canonical mutation did not complete the exact Goal.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Six-step completion retained reservations.");
			PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Six-step completion retained observers.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.close(_firstProfile.profileId(), goal, now + 5).status(), "Six-step close failed.");
			final Item storeItem = _second.getInventory().getItemByItemId(_tradeItemId);
			_second.getSellList().clear();
			_second.getSellList().setPackaged(false);
			_second.getSellList().addItem(storeItem.getObjectId(), 1, 4);
			_second.setPrivateStoreType(PrivateStoreType.SELL);
			final PhantomStorePlan storePlan = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, _second.getStoreName(), List.of(new PhantomStorePlan.Line(storeItem.getObjectId(), storeItem.getId(), 1, 4)), now + 60000);
			final PhantomStoreService stores = new PhantomStoreService(_profiles, _materialization);
			PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.open(_secondProfile.profileId(), PhantomActivityState.ACTIVE, storePlan, now + 5), "Visible SELL store lifecycle did not open.");
			PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, stores.close(_secondProfile.profileId()), "Visible SELL store lifecycle did not close.");
			context.record("economy.c2.sixStepCommitted", service.snapshot().committed());
		}
		finally
		{
			DirectTradeService.getInstance().cancel(_first);
			DirectTradeService.getInstance().cancel(_second);
			reservations.shutdown(now + 6);
		}
	}

	private void testExternalDirectTradeLifetime(PhantomTestContext context) throws Exception
	{
		fund(_first, _tradeItemId, 2);
		fund(_second, _tradeItemId, 2);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
		final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles);
		final long now = System.currentTimeMillis();
		try
		{
			reservations.start();
			final Item offered = _first.getInventory().getItemByItemId(_tradeItemId);
			final Item requested = _second.getInventory().getItemByItemId(_tradeItemId);
			final long expiry = now + 60000;
			final long goalId = 2200220295L;
			final String key = _second.getObjectId() + ";0;" + expiry + ";150;0;0;c2-external;O:" + offered.getObjectId() + ":1;R:" + requested.getObjectId() + ":1";
			final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 0, expiry, Map.of(), "economy.c2.external", 0);
			final PhantomGoalStateStore.StoredGoal previous = goals.load(_firstProfile.profileId()).orElse(null);
			if (previous == null)
			{
				goals.insert(_firstProfile.profileId(), goal);
			}
			else
			{
				goals.replace(_firstProfile.profileId(), previous.rowVersion(), goal);
			}
			final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "External direct offer discovery failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.RETRY, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "External consent was synthesized by the Phantom path.");
			PhantomAssertions.assertEquals(_first, _second.getActiveRequester(), "Canonical external trade request was not retained for ordinary consent.");
			PhantomAssertions.assertEquals(DirectTradeService.Result.ACCEPTED, DirectTradeService.getInstance().answer(_second, true), "Ordinary external consent failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 2).status(), "Accepted external consent was not observed.");
			final PhantomMultipartyEconomyService.StepResult reserved = service.reserve(_firstProfile.profileId(), goal, 1, 1, now + 3);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, reserved.status(), "External direct reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 4).status(), "External direct dispatch failed.");
			PhantomAssertions.assertEquals(DirectTradeService.Result.UPDATED, DirectTradeService.getInstance().addItem(_second, 0, requested.getObjectId(), 1), "Ordinary external requested line failed.");
			try (ActionLease externalBusy = _materialization.tryAcquireAction(_secondProfile.profileId()).orElseThrow())
			{
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.RETRY, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 5).status(), "Phantom confirmed before ordinary external confirmation.");
				PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "External confirmation wait retained a direct observer.");
				PhantomAssertions.assertEquals(1, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "External Player was incorrectly acquired as a Phantom participant.");
				PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "External confirmation wait leaked initiator admission.");
			}
			PhantomAssertions.assertEquals(DirectTradeService.Result.CONFIRMED, DirectTradeService.getInstance().finish(_second, true), "Ordinary external confirmation did not remain pending.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 6).status(), "Confirmed external direct trade did not commit.");
			PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(reserved.operationId()).orElseThrow().state(), "External direct operation did not commit.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "External direct operation did not complete exact Goal authority.");
			PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "External direct terminal audit retained an observer.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "External direct terminal audit retained reservations.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", reserved.operationId()), "External direct terminal audit was not exactly once.");

			final Item timeoutOffered = _first.getInventory().getItemByItemId(_tradeItemId);
			final Item timeoutRequested = _second.getInventory().getItemByItemId(_tradeItemId);
			final long timeoutNow = now + 100;
			final long timeoutExpiry = timeoutNow + 1000;
			final long timeoutGoalId = 2200220296L;
			final String timeoutKey = _second.getObjectId() + ";0;" + timeoutExpiry + ";150;0;0;c2-timeout;O:" + timeoutOffered.getObjectId() + ":1;R:" + timeoutRequested.getObjectId() + ":1";
			final PhantomGoal timeoutGoal = new PhantomGoal(timeoutGoalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, timeoutKey), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 0, timeoutExpiry, Map.of(), "economy.c2.external.timeout", 0);
			final PhantomGoalStateStore.StoredGoal completed = goals.load(_firstProfile.profileId()).orElseThrow();
			goals.replace(_firstProfile.profileId(), completed.rowVersion(), timeoutGoal);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.discoverOrLoad(_firstProfile.profileId(), timeoutGoal, timeoutNow).status(), "Timeout offer discovery failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.RETRY, service.offerOrAccept(_firstProfile.profileId(), timeoutGoal, PhantomActivityState.ACTIVE, timeoutNow + 1).status(), "Timeout fixture synthesized external consent.");
			PhantomAssertions.assertEquals(DirectTradeService.Result.ACCEPTED, DirectTradeService.getInstance().answer(_second, true), "Timeout fixture ordinary consent failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), timeoutGoal, PhantomActivityState.ACTIVE, timeoutNow + 2).status(), "Timeout fixture consent observation failed.");
			final PhantomMultipartyEconomyService.StepResult timeoutReserved = service.reserve(_firstProfile.profileId(), timeoutGoal, 2, 2, timeoutNow + 3);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, timeoutReserved.status(), "Timeout fixture reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), timeoutGoal, timeoutNow + 4).status(), "Timeout fixture dispatch failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.REPLAN, service.observeReconcile(_firstProfile.profileId(), timeoutGoal, PhantomActivityState.ACTIVE, timeoutExpiry + 1).status(), "Expired external direct operation was not cancelled before effect.");
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(timeoutReserved.operationId()).orElseThrow().state(), "Expired external direct operation did not abort.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Expired external direct operation completed its Goal.");
			PhantomAssertions.assertTrue(DirectTradeService.getInstance().canonicalPairCleared(_first, _second), "Expired external direct operation retained canonical request/list state.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Expired external direct operation retained reservations.");
			PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Expired external direct operation retained observers.");
			final long refusalNow = timeoutExpiry + 100;
			final ExternalDirectFixture refusal = stageExternalDirect(service, goals, refusalNow, 2200220321L, 11, "refusal");
			PhantomAssertions.assertEquals(DirectTradeService.Result.CANCELLED, DirectTradeService.getInstance().finish(_second, false), "External ordinary confirmation refusal was not canonical.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.REPLAN, service.observeReconcile(_firstProfile.profileId(), refusal.goal(), PhantomActivityState.ACTIVE, refusalNow + 6).status(), "External confirmation refusal was not aborted before effect.");
			assertExternalAbort(refusal, reservations, offers, goals, service, "refusal");

			final long disconnectNow = refusalNow + 100;
			final ExternalDirectFixture disconnect = stageExternalDirect(service, goals, disconnectNow, 2200220322L, 12, "disconnect");
			_second.setOnlineStatus(false, false);
			try
			{
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.REPLAN, service.observeReconcile(_firstProfile.profileId(), disconnect.goal(), PhantomActivityState.ACTIVE, disconnectNow + 6).status(), "Disconnected external counterparty was not aborted before effect.");
			}
			finally
			{
				_second.setOnlineStatus(true, false);
			}
			assertExternalAbort(disconnect, reservations, offers, goals, service, "disconnect");

			final long cancellationNow = disconnectNow + 100;
			final ExternalDirectFixture cancellation = stageExternalDirect(service, goals, cancellationNow, 2200220323L, 13, "cancellation");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.cancel(_firstProfile.profileId(), cancellation.goal(), cancellationNow + 6).status(), "External direct cancellation did not complete.");
			assertExternalAbort(cancellation, reservations, offers, goals, service, "cancellation");

			final long shutdownNow = cancellationNow + 100;
			final ExternalDirectFixture shutdown = stageExternalDirect(service, goals, shutdownNow, 2200220324L, 14, "shutdown");
			final PhantomMultipartyEconomyService.ShutdownResult externalShutdown = service.shutdown(shutdownNow + 6);
			PhantomAssertions.assertTrue(externalShutdown.successful() && externalShutdown.pendingOperationIds().isEmpty(), "External direct shutdown retained protected work: " + externalShutdown.pendingOperationIds());
			assertExternalAbort(shutdown, reservations, offers, goals, service, "shutdown");

			final long staleNow = shutdownNow + 100;
			final ExternalDirectFixture stale = stageExternalDirect(service, goals, staleNow, 2200220325L, 15, "stale-line");
			final var staleLine = _second.getActiveTradeList().getItems().stream().filter(item -> item.getObjectId() == stale.requestedObjectId()).findFirst().orElseThrow();
			staleLine.setCount(2);
			PhantomAssertions.assertEquals(DirectTradeService.Result.CONFIRMED, DirectTradeService.getInstance().finish(_second, true), "External stale-line ordinary confirmation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.REPLAN, service.observeReconcile(_firstProfile.profileId(), stale.goal(), PhantomActivityState.ACTIVE, staleNow + 6).status(), "Stale external line after ordinary confirmation was not aborted before effect.");
			assertExternalAbort(stale, reservations, offers, goals, service, "stale line");
			final Item auditOffered = _first.getInventory().getItemByItemId(_tradeItemId);
			final Item auditRequested = _second.getInventory().getItemByItemId(_tradeItemId);
			final long auditNow = timeoutExpiry + 100;
			final long auditExpiry = auditNow + 60000;
			final long auditGoalId = 2200220298L;
			final String auditKey = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + auditExpiry + ";150;0;0;c2-audit;O:" + auditOffered.getObjectId() + ":1;R:" + auditRequested.getObjectId() + ":1";
			final PhantomGoal auditGoal = new PhantomGoal(auditGoalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, auditKey), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 0, auditExpiry, Map.of(), "economy.c2.audit.finally", 0);
			final PhantomGoalStateStore.StoredGoal timedOut = goals.load(_firstProfile.profileId()).orElseThrow();
			goals.replace(_firstProfile.profileId(), timedOut.rowVersion(), auditGoal);
			final AtomicBoolean auditFault = new AtomicBoolean();
			final PhantomMultipartyEconomyService faultService = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
			{
				if ((point == PhantomMultipartyEconomyService.FaultPoint.AFTER_OPERATION_AUDIT) && auditFault.compareAndSet(false, true))
				{
					throw new IllegalStateException("after-operation-audit-fixture");
				}
			});
			final PhantomMultipartyEconomyService.StepResult auditDiscovered = faultService.discoverOrLoad(_firstProfile.profileId(), auditGoal, auditNow);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, auditDiscovered.status(), "Audit-fault offer discovery failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, faultService.offerOrAccept(_firstProfile.profileId(), auditGoal, PhantomActivityState.ACTIVE, auditNow + 1).status(), "Audit-fault direct consent failed.");
			final PhantomMultipartyEconomyService.StepResult auditReserved = faultService.reserve(_firstProfile.profileId(), auditGoal, 3, 3, auditNow + 2);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, auditReserved.status(), "Audit-fault reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, faultService.dispatch(_firstProfile.profileId(), auditGoal, auditNow + 3).status(), "Audit-fault dispatch failed.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> faultService.observeReconcile(_firstProfile.profileId(), auditGoal, PhantomActivityState.ACTIVE, auditNow + 4), "AFTER_OPERATION_AUDIT fault was not propagated.");
			PhantomAssertions.assertTrue(auditFault.get(), "AFTER_OPERATION_AUDIT fault was not injected.");
			PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(auditReserved.operationId()).orElseThrow().state(), "Audit-fault durable operation was not committed.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Audit-fault exact Goal write was lost.");
			PhantomAssertions.assertTrue(DirectTradeService.getInstance().canonicalPairCleared(_first, _second), "Audit-fault direct operation retained canonical trade state.");
			PhantomAssertions.assertEquals(0, faultService.snapshot().retainedObservers(), "AFTER_OPERATION_AUDIT finally leaked observer registration.");
			PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "AFTER_OPERATION_AUDIT finally leaked initiator lease.");
			PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "AFTER_OPERATION_AUDIT finally leaked counterparty lease.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Audit-fault durable terminal retained reservations.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", auditReserved.operationId()), "Audit-fault terminal audit was not exactly once.");
			PhantomAssertions.assertTrue(faultService.reconcileStartup(auditNow + 5) > 0, "Audit-fault accepted offer was not reconciled from durable terminal state.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.CONSUMED, offers.find(auditDiscovered.offerId()).orElseThrow().state(), "Audit-fault offer did not reconcile to consumed.");
			PhantomAssertions.assertTrue(faultService.shutdown(auditNow + 6).successful(), "Audit-fault cleanup blocked shutdown.");
			PhantomAssertions.assertTrue(service.shutdown(timeoutExpiry + 2).successful(), "Clean external direct shutdown reported a false failure.");
			context.record("economy.c2.externalLifetime", true);
		}
		finally
		{
			DirectTradeService.getInstance().cancel(_first, _second);
			goals.load(_firstProfile.profileId()).ifPresent(stored -> goals.delete(_firstProfile.profileId(), stored.rowVersion()));
			service.shutdown(now + 70000);
			reservations.shutdown(now + 70001);
		}
	}

	private ExternalDirectFixture stageExternalDirect(PhantomMultipartyEconomyService service, PhantomGoalStateStore goals, long now, long goalId, long generation, String suffix) throws Exception
	{
		DirectTradeService.getInstance().cancel(_first, _second);
		fund(_first, _tradeItemId, 2);
		fund(_second, _tradeItemId, 2);
		final Item offered = _first.getInventory().getItemByItemId(_tradeItemId);
		final Item requested = _second.getInventory().getItemByItemId(_tradeItemId);
		final long globalItems = itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId);
		final long globalAdena = _first.getAdena() + _second.getAdena();
		final long expiry = now + 60000;
		final String key = _second.getObjectId() + ";0;" + expiry + ";150;0;0;c2-external-" + suffix + ";O:" + offered.getObjectId() + ":1;R:" + requested.getObjectId() + ":1";
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 0, expiry, Map.of(), "economy.c2.external." + suffix, 0);
		final PhantomGoalStateStore.StoredGoal previous = goals.load(_firstProfile.profileId()).orElse(null);
		if (previous == null)
		{
			goals.insert(_firstProfile.profileId(), goal);
		}
		else
		{
			goals.replace(_firstProfile.profileId(), previous.rowVersion(), goal);
		}
		final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "External " + suffix + " offer discovery failed.");
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.RETRY, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "External " + suffix + " consent was synthesized.");
		PhantomAssertions.assertEquals(_first, _second.getActiveRequester(), "External " + suffix + " canonical request was not retained.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.ACCEPTED, DirectTradeService.getInstance().answer(_second, true), "External " + suffix + " ordinary acceptance failed.");
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 2).status(), "External " + suffix + " accepted consent was not observed.");
		final PhantomMultipartyEconomyService.StepResult reserved = service.reserve(_firstProfile.profileId(), goal, generation, generation, now + 3);
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, reserved.status(), "External " + suffix + " reservation failed.");
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 4).status(), "External " + suffix + " dispatch failed.");
		PhantomAssertions.assertEquals(DirectTradeService.Result.UPDATED, DirectTradeService.getInstance().addItem(_second, 0, requested.getObjectId(), 1), "External " + suffix + " ordinary requested line failed.");
		PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.RETRY, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 5).status(), "External " + suffix + " did not wait for ordinary confirmation.");
		PhantomAssertions.assertTrue(!_first.getActiveTradeList().isConfirmed() && !_second.getActiveTradeList().isConfirmed(), "External " + suffix + " forged confirmation while waiting.");
		PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "External " + suffix + " wait retained an observer.");
		PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "External " + suffix + " wait retained a Phantom lease.");
		return new ExternalDirectFixture(goal, discovered.offerId(), reserved.operationId(), requested.getObjectId(), globalItems, globalAdena);
	}

	private void assertExternalAbort(ExternalDirectFixture fixture, PhantomEconomyReservationService reservations, PhantomEconomyOfferService offers, PhantomGoalStateStore goals, PhantomMultipartyEconomyService service, String label) throws Exception
	{
		PhantomAssertions.assertEquals(State.ABORTED, reservations.find(fixture.operationId()).orElseThrow().state(), "External " + label + " operation did not abort.");
		PhantomAssertions.assertEquals(PhantomEconomyOffer.State.CANCELLED, offers.find(fixture.offerId()).orElseThrow().state(), "External " + label + " offer did not cancel.");
		PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "External " + label + " completed its Goal.");
		PhantomAssertions.assertTrue(DirectTradeService.getInstance().canonicalPairCleared(_first, _second), "External " + label + " retained canonical trade state.");
		PhantomAssertions.assertEquals(fixture.globalItems(), itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId), "External " + label + " violated item conservation.");
		PhantomAssertions.assertEquals(fixture.globalAdena(), _first.getAdena() + _second.getAdena(), "External " + label + " violated Adena conservation.");
		PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "External " + label + " retained reservations.");
		PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "External " + label + " retained an observer.");
		PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "External " + label + " retained the Phantom lease.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", fixture.operationId()), "External " + label + " audit was not exactly once.");
	}

	private record ExternalDirectFixture(PhantomGoal goal, String offerId, String operationId, int requestedObjectId, long globalItems, long globalAdena)
	{
	}
	private void testDirectFaultCleanupMatrix(PhantomTestContext context) throws Exception
	{
		final List<PhantomMultipartyEconomyService.FaultPoint> matrix = List.of(PhantomMultipartyEconomyService.FaultPoint.AFTER_OFFER_ACCEPTED, PhantomMultipartyEconomyService.FaultPoint.AFTER_RESERVATIONS, PhantomMultipartyEconomyService.FaultPoint.AFTER_DISPATCHING, PhantomMultipartyEconomyService.FaultPoint.AFTER_OBSERVING, PhantomMultipartyEconomyService.FaultPoint.AFTER_FIRST_ADENA_MUTATION, PhantomMultipartyEconomyService.FaultPoint.AFTER_FIRST_ITEM_TRANSFER, PhantomMultipartyEconomyService.FaultPoint.AFTER_EACH_TRANSFER_LINE, PhantomMultipartyEconomyService.FaultPoint.AFTER_GOAL_WRITE, PhantomMultipartyEconomyService.FaultPoint.AFTER_OPERATION_AUDIT);
		int index = 0;
		for (PhantomMultipartyEconomyService.FaultPoint faultPoint : matrix)
		{
			DirectTradeService.getInstance().cancel(_first, _second);
			fund(_first, _tradeItemId, 2);
			fund(_second, _tradeItemId, 2);
			fund(_first, Inventory.ADENA_ID, 2);
			final Item offered = _first.getInventory().getItemByItemId(_tradeItemId);
			final Item requested = _second.getInventory().getItemByItemId(_tradeItemId);
			final long globalItems = itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId);
			final long globalAdena = _first.getAdena() + _second.getAdena();
			final long now = System.currentTimeMillis() + (++index * 100L);
			final long expiry = now + 60000;
			final long goalId = 2200220300L + index;
			final String key = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + expiry + ";150;1;0;c2-fault-" + faultPoint.name() + ";O:" + offered.getObjectId() + ":1;R:" + requested.getObjectId() + ":1";
			final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.DIRECT_TRADE_GOAL, 500, 0, 1, expiry, Map.of(), "economy.c2.fault.matrix", 0);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
			final AtomicBoolean injected = new AtomicBoolean();
			final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
			{
				if ((point == faultPoint) && injected.compareAndSet(false, true))
				{
					throw new IllegalStateException("direct-fault-matrix-" + faultPoint);
				}
			});
			try
			{
				reservations.start();
				goals.insert(_firstProfile.profileId(), goal);
				final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "Fault-matrix discovery failed: " + faultPoint);
				try
				{
					service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1);
					service.reserve(_firstProfile.profileId(), goal, index, index, now + 2);
					service.dispatch(_firstProfile.profileId(), goal, now + 3);
					service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 4);
				}
				catch (IllegalStateException expected)
				{
				}
				PhantomAssertions.assertTrue(injected.get(), "Direct fault point was not reached: " + faultPoint);
				final PhantomMultipartyEconomyService.ShutdownResult shutdown = service.shutdown(now + 5);
				PhantomAssertions.assertTrue(shutdown.successful() && shutdown.pendingOperationIds().isEmpty(), "Fault cleanup shutdown failed: " + faultPoint + " pending=" + shutdown.pendingOperationIds());
				PhantomAssertions.assertTrue(offers.find(discovered.offerId()).orElseThrow().state().terminal(), "Fault cleanup retained active offer: " + faultPoint);
				PhantomAssertions.assertTrue(DirectTradeService.getInstance().canonicalPairCleared(_first, _second), "Fault cleanup retained canonical trade pair: " + faultPoint);
				PhantomAssertions.assertEquals(globalItems, itemCount(_first, _tradeItemId) + itemCount(_second, _tradeItemId), "Fault cleanup violated item conservation: " + faultPoint);
				PhantomAssertions.assertEquals(globalAdena, _first.getAdena() + _second.getAdena(), "Fault cleanup violated Adena conservation: " + faultPoint);
				PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Fault cleanup retained reservations: " + faultPoint);
				PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Fault cleanup retained observer registration: " + faultPoint);
				PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Fault cleanup retained initiator lease: " + faultPoint);
				PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "Fault cleanup retained counterparty lease: " + faultPoint);
			}
			finally
			{
				DirectTradeService.getInstance().cancel(_first, _second);
				service.shutdown(now + 6);
				reservations.shutdown(now + 7);
				goals.load(_firstProfile.profileId()).ifPresent(stored -> goals.delete(_firstProfile.profileId(), stored.rowVersion()));
			}
		}
		PhantomAssertions.assertEquals(9, matrix.size(), "Direct multiparty fault matrix drifted.");
		context.record("economy.c2.directFaultMatrix", matrix.size());
	}

	private void testPrivateStoreBuy(PhantomTestContext context) throws Exception
	{
		fund(_second, _tradeItemId, 5);
		fund(_first, Inventory.ADENA_ID, 100);
		final Item listed = _second.getInventory().getItemByItemId(_tradeItemId);
		_second.getSellList().clear();
		_second.getSellList().addItem(listed.getObjectId(), 3, 5);
		_second.setPrivateStoreType(PrivateStoreType.SELL);
		final long buyerBefore = itemCount(_first, listed.getId());
		PhantomAssertions.assertEquals(_second, World.getInstance().getPlayer(_second.getObjectId()), "SELL store owner left World.");
		PhantomAssertions.assertFalse(_first.isCursedWeaponEquipped(), "SELL store buyer unexpectedly carries a cursed weapon.");
		PhantomAssertions.assertTrue(_first.isInsideRadius3D(_second, 150), "SELL store fixtures are outside canonical range.");
		PhantomAssertions.assertEquals(_first.getInstanceId(), _second.getInstanceId(), "SELL store fixtures changed instance.");
		PhantomAssertions.assertEquals(PrivateStoreType.SELL, _second.getPrivateStoreType(), "SELL store type changed before mutation.");
		PhantomAssertions.assertTrue(_first.getAccessLevel().allowTransaction(), "SELL store buyer access forbids transactions.");
		PhantomAssertions.assertTrue(_first.hasHeadlessOutboundSession() && _second.hasHeadlessOutboundSession(), "SELL store fixtures lost headless sessions.");
		final TrackingStoreObserver observer = new TrackingStoreObserver();
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, _first.getObjectId(), _second.getObjectId(), observer))
		{
			final Set<RequestTrade> request = Set.of(new RequestTrade(listed.getObjectId(), listed.getId(), 2, 5));
			final PrivateStoreService.Result result = PrivateStoreService.getInstance().buyExact(_first, _second.getObjectId(), request, PrivateStoreService.listingHash(_second.getSellList()), PrivateStoreService.requestHash(request));
			PhantomAssertions.assertTrue(observer.before, "SELL store mutation was rejected before observer dispatch: " + result);
			PhantomAssertions.assertTrue(observer.after, "SELL store mutation did not reach the canonical completion callback: " + result);
			PhantomAssertions.assertTrue(observer.successful, "SELL store canonical holder mutation failed: " + result);
			PhantomAssertions.assertEquals(PrivateStoreService.Result.COMMITTED, result, "Exact SELL store purchase failed.");
		}
		PhantomAssertions.assertEquals(buyerBefore + 2, itemCount(_first, listed.getId()), "SELL store purchase applied the wrong quantity.");
		_second.getSellList().clear();
		final Item orchestrationItem = _second.getInventory().getItemByItemId(listed.getId());
		_second.getSellList().setPackaged(false);
		_second.getSellList().addItem(orchestrationItem.getObjectId(), 2, 5);
		_second.setPrivateStoreType(PrivateStoreType.SELL);
		final long now = System.currentTimeMillis();
		final String listingHash = PrivateStoreService.listingHash(_second.getSellList());
		final long goalId = 2200220293L;
		final String key = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + listingHash + ";0;5;B:" + orchestrationItem.getObjectId() + ":1:5:" + orchestrationItem.getId();
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, 500, 0, 5, now + 60000, Map.of(), "economy.c2.test", 0);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		goals.insert(_firstProfile.profileId(), goal);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		try
		{
			reservations.start();
			final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
			final AtomicBoolean auditFault = new AtomicBoolean();
			final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
			{
				if ((point == PhantomMultipartyEconomyService.FaultPoint.AFTER_OPERATION_AUDIT) && auditFault.compareAndSet(false, true))
				{
					throw new IllegalStateException("store-buy-audit-fault");
				}
			});
			final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "Private-store offer discovery failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "Private-store standing-offer acceptance failed.");
			final PhantomMultipartyEconomyService.StepResult reserved = service.reserve(_firstProfile.profileId(), goal, 1, 1, now + 2);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, reserved.status(), "Private-store reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 3).status(), "Private-store dispatch failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.ACTIVE_REQUIRED, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.BACKGROUND, now + 4).status(), "Background private-store BUY execution was admitted.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 4), "Private-store BUY audit fault was not propagated.");
			PhantomAssertions.assertTrue(auditFault.get(), "Private-store BUY audit fault was not injected.");
			PhantomAssertions.assertEquals(PhantomEconomyOperation.State.COMMITTED, reservations.find(reserved.operationId()).orElseThrow().state(), "Private-store operation did not commit.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Private-store BUY audit fault lost the Goal write.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.ACCEPTED, offers.find(discovered.offerId()).orElseThrow().state(), "Private-store BUY audit fault did not preserve the accepted offer for reconciliation.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", reserved.operationId()), "Private-store BUY audit was not exactly once.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Private-store BUY audit terminal retained reservations.");
			PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Private-store completion retained an observer.");
			PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Private-store BUY audit terminal retained initiator lease.");
			PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "Private-store BUY audit terminal retained owner lease.");
			PhantomAssertions.assertTrue(service.reconcileStartup(now + 5) > 0, "Private-store BUY accepted offer was not reconciled from terminal operation.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.CONSUMED, offers.find(discovered.offerId()).orElseThrow().state(), "Private-store BUY offer was not consumed after reconciliation.");
		}
		finally
		{
			reservations.shutdown(now + 5);
		}
		_second.getSellList().clear();
		final Item remainder = _second.getInventory().getItemByItemId(listed.getId());
		_second.getSellList().setPackaged(false);
		_second.getSellList().addItem(remainder.getObjectId(), 2, 5);
		_second.setPrivateStoreType(PrivateStoreType.SELL);
		final long aggregateBuyerItems = itemCount(_first, remainder.getId());
		final long aggregateBuyerAdena = _first.getAdena();
		final String aggregateListing = PrivateStoreService.listingHash(_second.getSellList());
		final Set<RequestTrade> aggregate = new HashSet<>();
		aggregate.add(new RequestTrade(remainder.getObjectId(), remainder.getId(), 2, 5));
		aggregate.add(new RequestTrade(remainder.getObjectId(), remainder.getId(), 1, 5));
		PhantomAssertions.assertEquals(2, aggregate.size(), "Duplicate exact-object aggregate fixture collapsed.");
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, _first.getObjectId(), _second.getObjectId(), new AcceptingStoreObserver()))
		{
			PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().buyExact(_first, _second.getObjectId(), aggregate, aggregateListing, PrivateStoreService.requestHash(aggregate)), "Strict SELL-store aggregate overdraw mutated before full preflight.");
		}
		PhantomAssertions.assertEquals(aggregateBuyerItems, itemCount(_first, remainder.getId()), "Rejected strict SELL-store aggregate changed buyer items.");
		PhantomAssertions.assertEquals(aggregateBuyerAdena, _first.getAdena(), "Rejected strict SELL-store aggregate changed buyer Adena.");
		PhantomAssertions.assertEquals(aggregateListing, PrivateStoreService.listingHash(_second.getSellList()), "Rejected strict SELL-store aggregate changed listing.");
		_second.getSellList().clear();
		_second.getSellList().addItem(remainder.getObjectId(), 1, 5);
		_second.getSellList().setPackaged(true);
		_second.setPrivateStoreType(PrivateStoreType.PACKAGE_SELL);
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, _first.getObjectId(), _second.getObjectId(), new AcceptingStoreObserver()))
		{
			final Set<RequestTrade> stale = Set.of(new RequestTrade(remainder.getObjectId(), remainder.getId(), 2, 5));
			PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().buyExact(_first, _second.getObjectId(), stale, PrivateStoreService.listingHash(_second.getSellList()), PrivateStoreService.requestHash(stale)), "Stale package quantity was clamped instead of rejected.");
		}
	}

	private void testStoreLifecycle(PhantomTestContext context) throws Exception
	{
		final Item item = _second.getInventory().getItemByItemId(_tradeItemId);
		PhantomAssertions.assertTrue((item != null) && (_second.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL), "Package store fixture was not retained for lifecycle closure.");
		final PhantomStorePlan plan = new PhantomStorePlan(PhantomStorePlan.Type.PACKAGE_SELL, PhantomStorePlan.State.REQUESTED, _second.getStoreName(), List.of(new PhantomStorePlan.Line(item.getObjectId(), item.getId(), 1, 5)), System.currentTimeMillis() + 60000);
		final PhantomStoreService stores = new PhantomStoreService(_profiles, _materialization);
		PhantomAssertions.assertEquals(PhantomStoreService.Result.ACTIVE_REQUIRED, stores.open(_secondProfile.profileId(), PhantomActivityState.BACKGROUND, plan, System.currentTimeMillis()), "Background store opening was accepted.");
		PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.open(_secondProfile.profileId(), PhantomActivityState.ACTIVE, plan, System.currentTimeMillis()), "Visible Phantom store did not open.");
		PhantomAssertions.assertEquals(PrivateStoreType.PACKAGE_SELL, _second.getPrivateStoreType(), "Visible Phantom store type drifted.");
		PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.restore(_secondProfile.profileId(), PhantomActivityState.ACTIVE, System.currentTimeMillis()), "Exact store restore was not idempotent.");
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, _first.getObjectId(), _second.getObjectId(), new AcceptingStoreObserver()))
		{
			final Set<RequestTrade> request = Set.of(new RequestTrade(item.getObjectId(), item.getId(), 1, 5));
			PhantomAssertions.assertEquals(PrivateStoreService.Result.COMMITTED, PrivateStoreService.getInstance().buy(_first, _second.getObjectId(), request), "Visible package store did not sell its exact final listing.");
		}
		PhantomAssertions.assertEquals(PrivateStoreType.NONE, _second.getPrivateStoreType(), "Empty visible package store did not close.");
		PhantomAssertions.assertEquals(0, stores.snapshot().retainedOwnerObservers(), "Store lifecycle retained an owner observer.");
		fund(_second, _tradeItemId, 1);
		final Item retryItem = _second.getInventory().getItemByItemId(_tradeItemId);
		final long retryNow = System.currentTimeMillis();
		final PhantomStorePlan retryPlan = new PhantomStorePlan(PhantomStorePlan.Type.PACKAGE_SELL, PhantomStorePlan.State.REQUESTED, _second.getStoreName(), List.of(new PhantomStorePlan.Line(retryItem.getObjectId(), retryItem.getId(), 1, 5)), retryNow + 60000);
		PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.open(_secondProfile.profileId(), PhantomActivityState.ACTIVE, retryPlan, retryNow), "Retry store fixture did not open.");
		try (ActionLease busy = _materialization.tryAcquireAction(_secondProfile.profileId()).orElseThrow())
		{
			PhantomAssertions.assertEquals(PhantomStoreService.Result.RETRY, stores.close(_secondProfile.profileId()), "Lease-unavailable store close did not request retry.");
			PhantomAssertions.assertEquals(PrivateStoreType.PACKAGE_SELL, _second.getPrivateStoreType(), "Retry close mutated the visible store.");
		}
		PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.restore(_secondProfile.profileId(), PhantomActivityState.ACTIVE, retryNow + 1), "Retry close discarded the durable store plan.");
		final PhantomStoreService.ShutdownResult shutdown = stores.shutdown();
		PhantomAssertions.assertTrue(shutdown.successful() && shutdown.retryProfileIds().isEmpty() && shutdown.inconsistentProfileIds().isEmpty(), "Bounded store shutdown did not report exact closure.");
		PhantomAssertions.assertEquals(PrivateStoreType.NONE, _second.getPrivateStoreType(), "Successful store shutdown left the visible store open.");
		PhantomAssertions.assertEquals(0, stores.snapshot().retainedOwnerObservers(), "Successful store shutdown retained an owner observer.");
	}

	private void testPrivateStoreSell(PhantomTestContext context) throws Exception
	{
		fund(_first, _tradeItemId, 5);
		fund(_second, Inventory.ADENA_ID, 100);
		final Item sold = _first.getInventory().getItemByItemId(_tradeItemId);
		_second.getBuyList().clear();
		_second.getBuyList().addItemByItemId(sold.getId(), 3, 6);
		_second.setPrivateStoreType(PrivateStoreType.BUY);
		final long sellerBefore = itemCount(_first, sold.getId());
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.SELL_TO_BUY_STORE, _first.getObjectId(), _second.getObjectId(), new AcceptingStoreObserver()))
		{
			final RequestTrade[] request =
			{
				new RequestTrade(sold.getObjectId(), sold.getId(), 2, 6)
			};
			PhantomAssertions.assertEquals(PrivateStoreService.Result.COMMITTED, PrivateStoreService.getInstance().sellExact(_first, _second.getObjectId(), request, PrivateStoreService.listingHash(_second.getBuyList()), PrivateStoreService.requestHash(request)), "Exact BUY store sale failed.");
		}
		PhantomAssertions.assertEquals(sellerBefore - 2, itemCount(_first, sold.getId()), "BUY store sale applied the wrong quantity.");
		_second.getBuyList().clear();
		_second.getBuyList().addItemByItemId(sold.getId(), 3, 6);
		_second.setPrivateStoreType(PrivateStoreType.BUY);
		final Item aggregateSold = _first.getInventory().getItemByItemId(sold.getId());
		final long aggregateSellerItems = itemCount(_first, sold.getId());
		final long aggregateOwnerAdena = _second.getAdena();
		final String aggregateListing = PrivateStoreService.listingHash(_second.getBuyList());
		final RequestTrade[] aggregate =
		{
			new RequestTrade(aggregateSold.getObjectId(), aggregateSold.getId(), 2, 6),
			new RequestTrade(aggregateSold.getObjectId(), aggregateSold.getId(), 2, 6)
		};
		try (AutoCloseable ignored = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.SELL_TO_BUY_STORE, _first.getObjectId(), _second.getObjectId(), new AcceptingStoreObserver()))
		{
			PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().sellExact(_first, _second.getObjectId(), aggregate, aggregateListing, PrivateStoreService.requestHash(aggregate)), "Strict BUY-store aggregate overdraw mutated before full preflight.");
		}
		PhantomAssertions.assertEquals(aggregateSellerItems, itemCount(_first, sold.getId()), "Rejected strict BUY-store aggregate changed seller items.");
		PhantomAssertions.assertEquals(aggregateOwnerAdena, _second.getAdena(), "Rejected strict BUY-store aggregate changed owner Adena.");
		PhantomAssertions.assertEquals(aggregateListing, PrivateStoreService.listingHash(_second.getBuyList()), "Rejected strict BUY-store aggregate changed listing.");
		_second.getBuyList().clear();
		_second.getBuyList().addItemByItemId(sold.getId(), 2, 6);
		_second.setPrivateStoreType(PrivateStoreType.BUY);
		final Item orchestrationItem = _first.getInventory().getItemByItemId(sold.getId());
		final long now = System.currentTimeMillis();
		final String listingHash = PrivateStoreService.listingHash(_second.getBuyList());
		final long goalId = 2200220294L;
		final String key = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + listingHash + ";6;S:" + orchestrationItem.getObjectId() + ":1:6:" + orchestrationItem.getId();
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.STORE_SELL_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.STORE_SELL_GOAL, 500, 0, 0, now + 60000, Map.of(), "economy.c2.test", 0);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		goals.insert(_firstProfile.profileId(), goal);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		try
		{
			reservations.start();
			final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
			final AtomicBoolean auditFault = new AtomicBoolean();
			final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
			{
				if ((point == PhantomMultipartyEconomyService.FaultPoint.AFTER_OPERATION_AUDIT) && auditFault.compareAndSet(false, true))
				{
					throw new IllegalStateException("store-sell-audit-fault");
				}
			});
			final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "Private-store SELL offer discovery failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "Private-store SELL standing-offer acceptance failed.");
			final PhantomMultipartyEconomyService.StepResult reserved = service.reserve(_firstProfile.profileId(), goal, 1, 1, now + 2);
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, reserved.status(), "Private-store SELL reservation failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 3).status(), "Private-store SELL dispatch failed.");
			PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.ACTIVE_REQUIRED, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.BACKGROUND, now + 4).status(), "Background private-store SELL execution was admitted.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 4), "Private-store SELL audit fault was not propagated.");
			PhantomAssertions.assertTrue(auditFault.get(), "Private-store SELL audit fault was not injected.");
			PhantomAssertions.assertEquals(PhantomEconomyOperation.State.COMMITTED, reservations.find(reserved.operationId()).orElseThrow().state(), "Private-store SELL operation did not commit.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Private-store SELL operation did not complete the exact Goal.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.ACCEPTED, offers.find(discovered.offerId()).orElseThrow().state(), "Private-store SELL audit fault did not preserve the accepted offer for reconciliation.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", reserved.operationId()), "Private-store SELL audit was not exactly once.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Private-store SELL completion retained reservations.");
			PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Private-store SELL completion retained an observer.");
			PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Private-store SELL audit terminal retained initiator lease.");
			PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "Private-store SELL audit terminal retained owner lease.");
			PhantomAssertions.assertTrue(service.reconcileStartup(now + 5) > 0, "Private-store SELL accepted offer was not reconciled from terminal operation.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.CONSUMED, offers.find(discovered.offerId()).orElseThrow().state(), "Private-store SELL offer was not consumed after reconciliation.");
		}
		finally
		{
			reservations.shutdown(now + 5);
		}
		final PhantomStorePlan plan = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, _second.getStoreName(), List.of(new PhantomStorePlan.Line(sold.getId(), sold.getId(), 1, 6)), System.currentTimeMillis() + 60000);
		final PhantomStoreService stores = new PhantomStoreService(_profiles, _materialization);
		PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.open(_secondProfile.profileId(), PhantomActivityState.ACTIVE, plan, System.currentTimeMillis()), "Visible BUY store lifecycle did not open.");
		final PhantomEconomyReservationService boundaryReservations = new PhantomEconomyReservationService(_policy);
		PhantomAssertions.assertThrows(PhantomEconomyReservationService.EconomyConflictException.class, () -> new PhantomEconomyMaterializationLifecycle(boundaryReservations, Clock.systemUTC()).beforeStore(_secondProfile.profileId(), _second), "Visible BUY store did not block dematerialization.");
		PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, stores.close(_secondProfile.profileId()), "Visible BUY store lifecycle did not close.");
	}

	private void testManufacture(PhantomTestContext context) throws Exception
	{
		final RecipeList recipe = selectRepeatableRecipe();
		final Skill skill = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getSkill() : CommonSkill.CREATE_COMMON.getSkill();
		PhantomAssertions.assertTrue(skill != null, "Manufacture craft skill is unavailable.");
		final boolean skillAdded = _second.getKnownSkill(skill.getId()) == null;
		final boolean recipeAdded = !_second.hasRecipeList(recipe.getId());
		final Map<Integer, Long> baselines = new java.util.HashMap<>();
		try
		{
			if (skillAdded)
			{
				_second.addSkill(skill, false);
			}
			if (recipeAdded)
			{
				if (recipe.isDwarvenRecipe())
				{
					_second.registerDwarvenRecipeList(recipe, false);
				}
				else
				{
					_second.registerCommonRecipeList(recipe, false);
				}
			}
			for (RecipeHolder ingredient : recipe.getRecipes())
			{
				baselines.put(ingredient.getItemId(), itemCount(_first, ingredient.getItemId()));
				fund(_first, ingredient.getItemId(), ingredient.getQuantity());
			}
			baselines.putIfAbsent(recipe.getItemId(), itemCount(_first, recipe.getItemId()));
			fund(_first, Inventory.ADENA_ID, 100);
			_second.getManufactureItems().put(recipe.getId(), new ManufactureItem(recipe.getId(), 10));
			_second.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
			final CountDownLatch terminal = new CountDownLatch(1);
			final List<RecipeCraftObserver.Event> events = new ArrayList<>();
			final RecipeCraftObserver observer = event ->
			{
				events.add(event);
				if ((event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT) || (event.type() == RecipeCraftObserver.Type.RARE_PRODUCT) || (event.type() == RecipeCraftObserver.Type.CRAFT_FAILED) || (event.type() == RecipeCraftObserver.Type.ABORTED))
				{
					terminal.countDown();
				}
			};
			PhantomAssertions.assertEquals(ManufactureService.Result.STARTED, ManufactureService.getInstance().manufacture(_first, _second.getObjectId(), recipe.getId(), observer), "Canonical manufacture request was rejected.");
			PhantomAssertions.assertTrue(terminal.await(10, TimeUnit.SECONDS), "Canonical manufacture did not emit a terminal event.");
			PhantomAssertions.assertTrue(events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.FEE_TRANSFERRED), "Manufacture observer missed the exact fee transfer.");
			PhantomAssertions.assertTrue(events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.INGREDIENTS_CONSUMED), "Manufacture observer missed exact ingredients.");
			PhantomAssertions.assertTrue(events.stream().allMatch(event -> (event.crafterObjectId() == _second.getObjectId()) && (event.targetObjectId() == _first.getObjectId()) && (event.recipeListId() == recipe.getId())), "Manufacture observer identity drifted.");
			for (RecipeHolder ingredient : recipe.getRecipes())
			{
				fund(_first, ingredient.getItemId(), ingredient.getQuantity());
			}
			fund(_first, Inventory.ADENA_ID, 100);
			final long now = System.currentTimeMillis();
			final long goalId = 2200220292L;
			final String key = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + recipe.getId() + ";10;" + recipe.getItemId() + ";" + recipe.getCount() + ";1;10";
			final PhantomGoal goal = new PhantomGoal(goalId, PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, key), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, 500, 0, 10, now + 60000, Map.of(), "economy.c2.test", 0);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
			goals.insert(_firstProfile.profileId(), goal);
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			try
			{
				reservations.start();
				final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
				final AtomicBoolean auditFault = new AtomicBoolean();
				final PhantomMultipartyEconomyService service = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
				{
					if ((point == PhantomMultipartyEconomyService.FaultPoint.AFTER_OPERATION_AUDIT) && auditFault.compareAndSet(false, true))
					{
						throw new IllegalStateException("manufacture-audit-fault");
					}
				});
				final PhantomMultipartyEconomyService.StepResult discovered = service.discoverOrLoad(_firstProfile.profileId(), goal, now);
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, discovered.status(), "Manufacture offer discovery failed.");
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.offerOrAccept(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 1).status(), "Manufacture standing-offer acceptance failed.");
				final PhantomMultipartyEconomyService.StepResult reserved = service.reserve(_firstProfile.profileId(), goal, 1, 1, now + 2);
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, reserved.status(), "Manufacture reservation failed.");
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, service.dispatch(_firstProfile.profileId(), goal, now + 3).status(), "Manufacture dispatch failed.");
				PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.ACTIVE_REQUIRED, service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.BACKGROUND, now + 4).status(), "Background manufacture execution was admitted.");
				service.observeReconcile(_firstProfile.profileId(), goal, PhantomActivityState.ACTIVE, now + 4);
				final long auditWaitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				while (!reservations.find(reserved.operationId()).orElseThrow().state().terminal() && (System.nanoTime() < auditWaitUntil))
				{
					Thread.sleep(10);
				}
				PhantomAssertions.assertTrue(auditFault.get(), "Manufacture audit fault was not injected.");
				PhantomAssertions.assertEquals(PhantomEconomyOperation.State.COMMITTED, reservations.find(reserved.operationId()).orElseThrow().state(), "Manufacture observer did not commit exact canonical consequences.");
				PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Manufacture observer did not complete the exact Goal.");
				PhantomAssertions.assertEquals(PhantomEconomyOffer.State.ACCEPTED, offers.find(discovered.offerId()).orElseThrow().state(), "Manufacture audit fault did not preserve the accepted offer for reconciliation.");
				PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", reserved.operationId()), "Manufacture audit was not exactly once.");
				PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Manufacture audit terminal retained reservations.");
				PhantomAssertions.assertEquals(0, service.snapshot().retainedObservers(), "Manufacture completion retained an observer.");
				PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Manufacture audit terminal retained customer lease.");
				PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "Manufacture audit terminal retained maker lease.");
				PhantomAssertions.assertTrue(service.reconcileStartup(now + 5) > 0, "Manufacture accepted offer was not reconciled from terminal operation.");
				PhantomAssertions.assertEquals(PhantomEconomyOffer.State.CONSUMED, offers.find(discovered.offerId()).orElseThrow().state(), "Manufacture offer was not consumed after reconciliation.");
				final List<PhantomMultipartyEconomyService.FaultPoint> manufactureFaults = List.of(PhantomMultipartyEconomyService.FaultPoint.AFTER_FIRST_ADENA_MUTATION, PhantomMultipartyEconomyService.FaultPoint.AFTER_RECIPE_INGREDIENTS, PhantomMultipartyEconomyService.FaultPoint.AFTER_PRODUCT_OR_FAILURE);
				int manufactureFaultIndex = 0;
				for (PhantomMultipartyEconomyService.FaultPoint faultPoint : manufactureFaults)
				{
					_second.setCurrentHp(_second.getMaxHp());
					_second.setCurrentMp(_second.getMaxMp());
					for (RecipeHolder ingredient : recipe.getRecipes())
					{
						fund(_first, ingredient.getItemId(), ingredient.getQuantity());
					}
					fund(_first, Inventory.ADENA_ID, 100);
					final long taintNow = now + (++manufactureFaultIndex * 100L);
					final long taintGoalId = 2200220296L + manufactureFaultIndex;
					final String taintKey = _second.getObjectId() + ";" + _secondProfile.profileId() + ";" + recipe.getId() + ";10;" + recipe.getItemId() + ";" + recipe.getCount() + ";1;10";
					final PhantomGoal taintGoal = new PhantomGoal(taintGoalId, PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, taintKey), 1, 0, null, List.of(), null, PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, 500, 0, 10, taintNow + 60000, Map.of(), "economy.c2.manufacture.taint", 0);
					final PhantomGoalStateStore.StoredGoal completed = goals.load(_firstProfile.profileId()).orElseThrow();
					goals.replace(_firstProfile.profileId(), completed.rowVersion(), taintGoal);
					final AtomicBoolean faultInjected = new AtomicBoolean();
					final PhantomMultipartyEconomyService taintedService = new PhantomMultipartyEconomyService(_policy, reservations, offers, _materialization, goals, _profiles, point ->
					{
						if ((point == faultPoint) && faultInjected.compareAndSet(false, true))
						{
							throw new IllegalStateException("manufacture-taint-fixture-" + faultPoint);
						}
					});
					final PhantomMultipartyEconomyService.StepResult taintDiscovered = taintedService.discoverOrLoad(_firstProfile.profileId(), taintGoal, taintNow);
					PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, taintDiscovered.status(), "Tainted manufacture offer discovery failed.");
					PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, taintedService.offerOrAccept(_firstProfile.profileId(), taintGoal, PhantomActivityState.ACTIVE, taintNow + 1).status(), "Tainted manufacture acceptance failed.");
					final PhantomMultipartyEconomyService.StepResult taintReserved = taintedService.reserve(_firstProfile.profileId(), taintGoal, 2, 2, taintNow + 2);
					PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, taintReserved.status(), "Tainted manufacture reservation failed.");
					PhantomAssertions.assertEquals(PhantomMultipartyEconomyService.StepStatus.SUCCESS, taintedService.dispatch(_firstProfile.profileId(), taintGoal, taintNow + 3).status(), "Tainted manufacture dispatch failed.");
					taintedService.observeReconcile(_firstProfile.profileId(), taintGoal, PhantomActivityState.ACTIVE, taintNow + 4);
					final long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
					while (!reservations.find(taintReserved.operationId()).orElseThrow().state().terminal() && (System.nanoTime() < waitUntil))
					{
						taintedService.observeReconcile(_firstProfile.profileId(), taintGoal, PhantomActivityState.ACTIVE, taintNow + 5);
						Thread.sleep(10);
					}
					PhantomAssertions.assertTrue(faultInjected.get(), "Manufacture fault was not injected: " + faultPoint);
					PhantomAssertions.assertEquals(State.INCONSISTENT, reservations.find(taintReserved.operationId()).orElseThrow().state(), "Tainted manufacture terminal callback did not fail stop.");
					PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goals.load(_firstProfile.profileId()).orElseThrow().goal().status(), "Tainted manufacture completed its Goal.");
					PhantomAssertions.assertEquals(PhantomEconomyOffer.State.INCONSISTENT, offers.find(taintDiscovered.offerId()).orElseThrow().state(), "Tainted manufacture offer was not terminal inconsistent.");
					PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Tainted manufacture terminal callback retained reservations.");
					PhantomAssertions.assertEquals(0, taintedService.snapshot().retainedObservers(), "Tainted manufacture terminal callback retained observer protection.");
					PhantomAssertions.assertEquals(0, _materialization.find(_firstProfile.profileId()).orElseThrow().admittedActionCount(), "Tainted manufacture retained customer participant lease.");
					PhantomAssertions.assertEquals(0, _materialization.find(_secondProfile.profileId()).orElseThrow().admittedActionCount(), "Tainted manufacture retained manufacturer participant lease.");
					PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", taintReserved.operationId()), "Tainted manufacture audit was not exactly once.");
					PhantomAssertions.assertTrue(taintedService.shutdown(taintNow + 60001).successful(), "Terminal tainted manufacture blocked clean shutdown.");
				}
				PhantomAssertions.assertEquals(3, manufactureFaults.size(), "Manufacture fault matrix drifted.");
				context.record("economy.c2.manufactureFaultMatrix", manufactureFaults.size());
			}
			finally
			{
				reservations.shutdown(now + 5);
			}
			final PhantomStorePlan plan = new PhantomStorePlan(PhantomStorePlan.Type.MANUFACTURE, PhantomStorePlan.State.REQUESTED, _second.getStoreName(), List.of(new PhantomStorePlan.Line(recipe.getId(), recipe.getItemId(), 1, 10)), System.currentTimeMillis() + 60000);
			final PhantomStoreService stores = new PhantomStoreService(_profiles, _materialization);
			PhantomAssertions.assertEquals(PhantomStoreService.Result.OPENED, stores.open(_secondProfile.profileId(), PhantomActivityState.ACTIVE, plan, System.currentTimeMillis()), "Visible MANUFACTURE store lifecycle did not open.");
			PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, stores.close(_secondProfile.profileId()), "Visible MANUFACTURE store lifecycle did not close.");
			context.record("economy.c2.manufactureEvents", events.stream().map(event -> event.type().name()).toList());
		}
		finally
		{
			_second.setCrafting(false);
			_second.getManufactureItems().clear();
			_second.setPrivateStoreType(PrivateStoreType.NONE);
			for (Map.Entry<Integer, Long> entry : baselines.entrySet())
			{
				restoreItemCount(_first, entry.getKey(), entry.getValue());
			}
			if (recipeAdded && _second.hasRecipeList(recipe.getId()))
			{
				_second.unregisterRecipeList(recipe.getId());
			}
			if (skillAdded)
			{
				_second.removeSkill(skill, false, true);
			}
		}
	}

	private void testRestartReconciliation(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(989001);
		final PhantomEconomyOfferService offers = new PhantomEconomyOfferService();
		final PhantomEconomyReservationService firstReservations = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
		final long now = System.currentTimeMillis();
		try
		{
			firstReservations.start();
			final PhantomEconomyOffer offer = offer(profile.profileId(), 989002, Kind.DIRECT_TRADE, "restart", 41, now);
			offers.create(offer);
			offers.offer(offer.offerId(), 0, now + 1);
			PhantomEconomyOffer current = offers.find(offer.offerId()).orElseThrow();
			offers.accept(current.offerId(), current.contentHash(), current.rowVersion(), now + 2);
			current = offers.find(offer.offerId()).orElseThrow();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 989001, current.goalId(), Kind.DIRECT_TRADE, current.contentHash(), now + 3);
			firstReservations.reserve(operation, List.of(itemReservation(profile.profileId(), 989001, 989101), new Reservation(0, 989002, 0, ResourceKind.ITEM_OBJECT, 989102, 57, 1, 1, 0, "INVENTORY")));
			offers.bindOperation(current.offerId(), operation.operationId(), current.rowVersion(), now + 4);
			firstReservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, now + 5, null);
			firstReservations.transition(operation.operationId(), State.DISPATCHING, State.OBSERVING, now + 6, null);
			PhantomAssertions.assertTrue(restarted.start(), "Restarted reservation service did not start.");
			final PhantomProfileRepository repository = PhantomProfileRepository.open();
			final PhantomMetrics metrics = new PhantomMetrics();
			final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			final PhantomMultipartyEconomyService multiparty = new PhantomMultipartyEconomyService(_policy, restarted, offers, materialization, new PhantomGoalStateStore(repository), repository);
			PhantomAssertions.assertTrue(multiparty.reconcileStartup(now + 7) > 0, "Restart reconciliation did not terminalize the accepted offer.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, restarted.find(operation.operationId()).orElseThrow().state(), "OBSERVING restart did not fail stop.");
			PhantomAssertions.assertEquals(PhantomEconomyOffer.State.INCONSISTENT, offers.find(offer.offerId()).orElseThrow().state(), "Restart did not reconcile the durable offer.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", operation.operationId()), "Restart reconciliation duplicated terminal audit.");
		}
		finally
		{
			firstReservations.shutdown(now + 8);
			restarted.shutdown(now + 8);
			deleteProfile(profile.profileId());
		}
	}

	private void testPerformance(PhantomTestContext context)
	{
		long checksum = 0;
		final long started = System.nanoTime();
		final long now = System.currentTimeMillis();
		final java.util.HashMap<String, PhantomEconomyOffer> lookup = new java.util.HashMap<>(1024);
		final List<String> lookupKeys = new ArrayList<>(1000);
		for (int i = 0; i < 1000; i++)
		{
			final PhantomEconomyOffer value = offer(990001, 990002, Kind.DIRECT_TRADE, "lookup-" + i, i + 1L, now);
			lookup.put(value.offerId(), value);
			lookupKeys.add(value.offerId());
		}
		for (int i = 0; i < 100000; i++)
		{
			final PhantomEconomyOffer value = offer(990001, 990002, Kind.DIRECT_TRADE, "offer-" + i, i + 1L, now);
			checksum += value.offerId().charAt(0);
			checksum += lookup.get(lookupKeys.get(i % lookupKeys.size())).contentHash().charAt(0);
		}
		final Reservation conflictLeft = new Reservation(990001, 990101, 0, ResourceKind.ITEM_COUNT, 0, 57, 1, 1, 0, "INVENTORY");
		final Reservation conflictRight = new Reservation(0, 990101, 0, ResourceKind.ITEM_OBJECT, 990201, 57, 1, 1, 0, "INVENTORY");
		int conflictChecks = 0;
		for (int i = 0; i < 100000; i++)
		{
			checksum += conflictLeft.overlaps(conflictRight) ? 1 : 0;
			conflictChecks++;
		}
		int directQuotes = 0;
		int storeQuotes = 0;
		int manufactureQuotes = 0;
		int cleanupChecks = 0;
		for (int i = 0; i < 10000; i++)
		{
			checksum += PhantomEconomyOperation.sha256(Kind.DIRECT_TRADE + ":quote:" + i).charAt(2);
			directQuotes++;
			final PhantomStorePlan plan = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "p", List.of(new PhantomStorePlan.Line(1, 57, 1, i)), now + 60000);
			checksum += PhantomStorePlan.decode(plan.encode()).lines().size();
			storeQuotes++;
			checksum += PhantomEconomyOperation.sha256(Kind.PLAYER_MANUFACTURE + ":quote:" + i).charAt(3);
			manufactureQuotes++;
			checksum += plan.expiresEpochMillis() > (now + i) ? 1 : 0;
			cleanupChecks++;
		}
		final long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		PhantomAssertions.assertTrue(checksum != 0, "C2 performance checksum was optimized away.");
		PhantomAssertions.assertEquals(100000, conflictChecks, "Participant/resource conflict volume drifted.");
		PhantomAssertions.assertEquals(10000, directQuotes, "Direct-trade quote/reconcile volume drifted.");
		PhantomAssertions.assertEquals(10000, storeQuotes, "Private-store quote/reconcile volume drifted.");
		PhantomAssertions.assertEquals(10000, manufactureQuotes, "Manufacture quote/reconcile volume drifted.");
		PhantomAssertions.assertEquals(10000, cleanupChecks, "Expiration/cleanup volume drifted.");
		PhantomAssertions.assertTrue(elapsed < 30000, "C2 bounded authority performance exceeded 30 seconds: " + elapsed);
		context.record("economy.c2.performanceMillis", elapsed);
	}

	private static PhantomEconomyOffer offer(long initiatingProfileId, int counterpartyCharacterId, Kind kind, String payload, long goalId, long now)
	{
		return offer(initiatingProfileId, counterpartyCharacterId, kind, payload, goalId, now, now + 60000);
	}

	private static PhantomEconomyOffer offer(long initiatingProfileId, int counterpartyCharacterId, Kind kind, String payload, long goalId, long now, long expiry)
	{
		return PhantomEconomyOffer.draft(initiatingProfileId, 980001 + (int) (goalId % 1000), kind, CounterpartyKind.PLAYER, 0, counterpartyCharacterId, goalId, 0, payload.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 1, 1, now, expiry);
	}

	private static PhantomEconomyOperation operation(long profileId, int characterObjectId, long goalId, Kind kind, String intentHash, long now)
	{
		final String normalizedIntent = intentHash.matches("[0-9a-f]{64}") ? intentHash : PhantomEconomyOperation.sha256(intentHash);
		return new PhantomEconomyOperation(new Identity(profileId, characterObjectId, goalId, 0, 1, "economy.c2:" + goalId, 1, 1), kind, State.PREPARED, PhantomEconomyOperation.sha256("authority:" + goalId), normalizedIntent, PhantomEconomyOperation.utf8Payload("before"), PhantomEconomyOperation.utf8Payload("intent"), now, now, now + 120000, 0);
	}

	private static Reservation itemReservation(long profileId, int ownerObjectId, int objectId)
	{
		return new Reservation(profileId, ownerObjectId, 0, ResourceKind.ITEM_OBJECT, objectId, 57, 1, 1, 0, "INVENTORY");
	}

	private static void reserveParticipants(PhantomEconomyReservationService reservations, PhantomEconomyOperation operation, PhantomProfile initiator, int initiatorCharacterId, PhantomProfile participant, int participantCharacterId, int objectId)
	{
		final List<Reservation> resources = List.of(itemReservation(initiator.profileId(), initiatorCharacterId, objectId), itemReservation(participant.profileId(), participantCharacterId, objectId + 1000));
		PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, resources).status(), "Participant link-drift fixture was not reserved.");
	}

	private static void assertTerminalWithoutClaims(PhantomEconomyReservationService reservations, String operationId, State expected, String message) throws Exception
	{
		PhantomAssertions.assertEquals(expected, reservations.find(operationId).orElseThrow().state(), message);
		PhantomAssertions.assertEquals(0, reservations.findReservations(operationId).size(), "Terminal participant operation retained reservations.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", operationId), "Terminal participant operation did not retain exactly one audit row.");
	}

	private static PhantomProfile createProfile(int characterObjectId)
	{
		return PhantomProfileRepository.open().create(characterObjectId);
	}

	private static void deleteProfile(long profileId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id=?"))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "C2 cleanup touched a non-test database.");
			statement.setLong(1, profileId);
			statement.executeUpdate();
		}
	}

	private static void updateProfileCharacter(long profileId, Integer characterObjectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE phantom_profiles SET character_object_id=? WHERE profile_id=?"))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "C2 profile-link fixture touched a non-test database.");
			if (characterObjectId == null)
			{
				statement.setNull(1, java.sql.Types.INTEGER);
			}
			else
			{
				statement.setInt(1, characterObjectId);
			}
			statement.setLong(2, profileId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "C2 profile-link fixture is absent.");
		}
	}

	private static long scalar(String sql, String value) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, value);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "C2 scalar query returned no row.");
				return row.getLong(1);
			}
		}
	}

	private static void restorePersistedItemCount(int ownerObjectId, int itemId, long expected) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "C2 fixture normalization touched a non-test database.");
			connection.setAutoCommit(false);
			try
			{
				final List<Integer> objects = new ArrayList<>();
				try (PreparedStatement select = connection.prepareStatement("SELECT object_id FROM items WHERE owner_id=? AND item_id=? ORDER BY object_id FOR UPDATE"))
				{
					select.setInt(1, ownerObjectId);
					select.setInt(2, itemId);
					try (ResultSet rows = select.executeQuery())
					{
						while (rows.next())
						{
							objects.add(rows.getInt(1));
						}
					}
				}
				PhantomAssertions.assertFalse(objects.isEmpty(), "C2 fixture normalization found no canonical stack.");
				try (PreparedStatement update = connection.prepareStatement("UPDATE items SET count=? WHERE object_id=? AND owner_id=? AND item_id=?"))
				{
					update.setLong(1, expected);
					update.setInt(2, objects.get(0));
					update.setInt(3, ownerObjectId);
					update.setInt(4, itemId);
					PhantomAssertions.assertEquals(1, update.executeUpdate(), "C2 fixture normalization lost its canonical stack.");
				}
				for (int index = 1; index < objects.size(); index++)
				{
					try (PreparedStatement delete = connection.prepareStatement("DELETE FROM items WHERE object_id=? AND owner_id=? AND item_id=?"))
					{
						delete.setInt(1, objects.get(index));
						delete.setInt(2, ownerObjectId);
						delete.setInt(3, itemId);
						PhantomAssertions.assertEquals(1, delete.executeUpdate(), "C2 fixture normalization lost a duplicate owned stack.");
					}
				}
				connection.commit();
			}
			catch (Throwable failure)
			{
				connection.rollback();
				throw failure;
			}
		}
	}

	private static long itemCount(Player player, int itemId)
	{
		return player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private static void fund(Player player, int itemId, long count)
	{
		if (count > 0)
		{
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, itemId, count, player, PhantomMultipartyEconomySuite.class) != null, "Could not fund C2 fixture.");
		}
	}

	private static void restoreItemCount(Player player, int itemId, long expected)
	{
		final long current = itemCount(player, itemId);
		if (current < expected)
		{
			fund(player, itemId, expected - current);
		}
		else if (current > expected)
		{
			final Item item = player.getInventory().getItemByItemId(itemId);
			PhantomAssertions.assertTrue((item != null) && (player.getInventory().destroyItem(ItemProcessType.DESTROY, item, current - expected, player, PhantomMultipartyEconomySuite.class) != null), "Could not restore C2 item baseline.");
		}
		final Item restored = player.getInventory().getItemByItemId(itemId);
		if (restored != null)
		{
			restored.updateDatabase(true);
		}
	}

	private static void resetStore(Player player)
	{
		player.getSellList().clear();
		player.getBuyList().clear();
		player.getManufactureItems().clear();
		player.setPrivateStoreType(PrivateStoreType.NONE);
		player.standUp();
	}

	private static RecipeList selectRepeatableRecipe()
	{
		return Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(RecipeData.getInstance()::getRecipeByItemId).filter(recipe -> (recipe != null) && (recipe.getLevel() == 1) && (recipe.getSuccessRate() == 100) && (recipe.getCount() == 1) && (recipe.getRareItemId() <= 0) && (recipe.getRecipes().length > 0) && (recipe.getRecipes().length <= 8) && Arrays.stream(recipe.getRecipes()).allMatch(ingredient -> (ingredient.getQuantity() > 0) && (ingredient.getQuantity() <= 1000) && (ingredient.getItemId() != recipe.getItemId())) && (Arrays.stream(recipe.getRecipes()).map(RecipeHolder::getItemId).distinct().count() == recipe.getRecipes().length) && Arrays.stream(recipe.getStatUse()).allMatch(stat -> (((stat.getType() == StatType.HP) || (stat.getType() == StatType.MP)) && (stat.getValue() <= 10))) && (ItemData.getInstance().getTemplate(recipe.getItemId()) != null)).sorted(Comparator.comparingInt(RecipeList::getId)).findFirst().orElseThrow(() -> new AssertionError("No repeatable manufacture recipe is available."));
	}

	private static int selectTradeItemId()
	{
		final org.l2jmobius.gameserver.model.item.ItemTemplate stem = ItemData.getInstance().getTemplate(1864);
		if ((stem != null) && stem.isTradeable() && stem.isStackable() && !stem.isQuestItem())
		{
			return stem.getId();
		}
		return Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).filter(item -> (item.getId() != Inventory.ADENA_ID) && item.isTradeable() && item.isStackable() && !item.isQuestItem() && (item.getTime() == -1)).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).mapToInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId).findFirst().orElseThrow(() -> new AssertionError("No bounded tradeable stack item is available."));
	}

	private static class AcceptingStoreObserver implements PrivateStoreService.Observer
	{
		@Override
		public boolean beforeMutation(PrivateStoreService.Direction direction, Player actor, Player owner, org.l2jmobius.gameserver.network.holders.TradeList list, String listingHash)
		{
			return true;
		}

		@Override
		public void afterMutation(PrivateStoreService.Direction direction, Player actor, Player owner, org.l2jmobius.gameserver.network.holders.TradeList list, String beforeListingHash, String afterListingHash, boolean successful)
		{
		}
	}

	private static final class TrackingStoreObserver extends AcceptingStoreObserver
	{
		private boolean before;
		private boolean after;
		private boolean successful;

		@Override
		public boolean beforeMutation(PrivateStoreService.Direction direction, Player actor, Player owner, org.l2jmobius.gameserver.network.holders.TradeList list, String listingHash)
		{
			before = true;
			return true;
		}

		@Override
		public void afterMutation(PrivateStoreService.Direction direction, Player actor, Player owner, org.l2jmobius.gameserver.network.holders.TradeList list, String beforeListingHash, String afterListingHash, boolean success)
		{
			after = true;
			successful = success;
		}
	}
}
