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
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.holders.RecipeStatHolder;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.managers.RecipeCraftObserver;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.network.holders.TradeList;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOffer.CounterpartyKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOffer.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Audit;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Identity;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.StoredOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.DirectTrade;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.Line;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.Manufacture;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.StoreBuy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.StoreSell;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.DirectTradeService;
import org.l2jmobius.gameserver.services.ManufactureService;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Active-only orchestration over the canonical two-owner economy services. */
public final class PhantomMultipartyEconomyService
{
	private static final int MAX_RETAINED_OBSERVERS = 10000;
	private final PhantomEconomyPolicy _policy;
	private final PhantomEconomyReservationService _reservations;
	private final PhantomEconomyOfferService _offers;
	private final PhantomMaterializationService _materialization;
	private final PhantomGoalStateStore _goals;
	private final PhantomProfileRepository _profiles;
	private final FaultInjector _faults;
	private final ConcurrentHashMap<String, AutoCloseable> _observerLeases = new ConcurrentHashMap<>();
	private final LongAdder _activeRequired = new LongAdder();
	private final LongAdder _offersAccepted = new LongAdder();
	private final LongAdder _committed = new LongAdder();
	private final LongAdder _inconsistent = new LongAdder();

	public PhantomMultipartyEconomyService(PhantomEconomyPolicy policy, PhantomEconomyReservationService reservations, PhantomEconomyOfferService offers, PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomProfileRepository profiles)
	{
		this(policy, reservations, offers, materialization, goals, profiles, FaultInjector.none());
	}

	public PhantomMultipartyEconomyService(PhantomEconomyPolicy policy, PhantomEconomyReservationService reservations, PhantomEconomyOfferService offers, PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomProfileRepository profiles, FaultInjector faults)
	{
		_policy = Objects.requireNonNull(policy);
		_reservations = Objects.requireNonNull(reservations);
		_offers = Objects.requireNonNull(offers);
		_materialization = Objects.requireNonNull(materialization);
		_goals = Objects.requireNonNull(goals);
		_profiles = Objects.requireNonNull(profiles);
		_faults = Objects.requireNonNull(faults);
	}

	public boolean supports(long profileId, PhantomGoal goal, PhantomActivityState state)
	{
		try
		{
			final PhantomSocialEconomyGoalSpec spec = PhantomSocialEconomyGoalSpec.parse(goal);
			final PhantomProfile initiator = _profiles.find(profileId).orElse(null);
			return (initiator != null) && (initiator.characterObjectId() != null) && ((state == PhantomActivityState.ACTIVE) || (state == PhantomActivityState.NEARBY_PERCEPTIBLE) || (state == PhantomActivityState.BACKGROUND)) && exactCounterparty(spec);
		}
		catch (IllegalArgumentException exception)
		{
			return false;
		}
	}

	public StepResult discoverOrLoad(long profileId, PhantomGoal goal, long now)
	{
		final PhantomSocialEconomyGoalSpec spec;
		try
		{
			spec = PhantomSocialEconomyGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			return StepResult.replan("economy.social.goal.stale");
		}
		final Optional<PhantomEconomyOffer> active = _offers.findActive(profileId, goal.goalId(), goal.revision());
		if (active.isPresent())
		{
			reconcileOffer(active.get(), now, false);
			final PhantomEconomyOffer reconciled = _offers.findActive(profileId, goal.goalId(), goal.revision()).orElse(null);
			return reconciled == null ? StepResult.replan("economy.social.offer.reconciled") : StepResult.success(reconciled.offerId(), reconciled.operationId(), "economy.social.offer.loaded");
		}
		final PhantomProfile profile = _profiles.find(profileId).orElse(null);
		if ((profile == null) || (profile.characterObjectId() == null) || !exactCounterparty(spec))
		{
			return StepResult.replan("economy.social.identity.stale");
		}
		final long expiry = spec instanceof DirectTrade trade ? trade.expiresEpochMillis() : (goal.deadlineEpochMillis() > now ? goal.deadlineEpochMillis() : Math.addExact(now, _policy.limits().reservationTtlSeconds() * 1000L));
		if (expiry <= now)
		{
			return StepResult.replan("economy.social.offer.expired");
		}
		final Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
		final CounterpartyKind counterpartyKind = spec.counterpartyProfileId() > 0 ? CounterpartyKind.PHANTOM : isStore(spec) && (counterparty != null) && ((counterparty.getClient() == null) || counterparty.getClient().isDetached()) ? CounterpartyKind.OFFLINE_STORE : CounterpartyKind.PLAYER;
		final byte[] payload = goal.target().key().getBytes(StandardCharsets.US_ASCII);
		final int initiatorLines = spec instanceof DirectTrade trade ? trade.offeredLines().size() : spec instanceof StoreSell sell ? sell.lines().size() : 1;
		final int counterpartyLines = spec instanceof DirectTrade trade ? trade.requestedLines().size() : spec instanceof StoreBuy buy ? buy.lines().size() : 1;
		final PhantomEconomyOffer offer = PhantomEconomyOffer.draft(profileId, profile.characterObjectId(), spec.operationKind(), counterpartyKind, spec.counterpartyProfileId(), spec.counterpartyCharacterObjectId(), goal.goalId(), goal.revision(), payload, initiatorLines, counterpartyLines, now, expiry);
		final PhantomEconomyOfferService.Status created = _offers.create(offer);
		if ((created != PhantomEconomyOfferService.Status.TRANSITIONED) && (created != PhantomEconomyOfferService.Status.IDEMPOTENT))
		{
			return StepResult.replan("economy.social.offer.conflict");
		}
		final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElseThrow();
		if (current.state() == State.DRAFT)
		{
			_offers.offer(current.offerId(), current.rowVersion(), now);
		}
		return StepResult.success(offer.offerId(), "", "economy.social.offer.created");
	}

	public StepResult offerOrAccept(long profileId, PhantomGoal goal, PhantomActivityState activityState, long now)
	{
		if (!activityState.requiresMaterialization())
		{
			_activeRequired.increment();
			return StepResult.activeRequired("economy.social.materialization.required");
		}
		final PhantomSocialEconomyGoalSpec spec = parse(goal);
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		if (offer == null)
		{
			return StepResult.replan("economy.social.offer.missing");
		}
		if (offer.state() == State.ACCEPTED)
		{
			return StepResult.success(offer.offerId(), offer.operationId(), "economy.social.offer.accepted");
		}
		if (offer.state() != State.OFFERED)
		{
			return StepResult.replan("economy.social.offer.state");
		}
		try (ParticipantLeases participants = acquireParticipants(profileId, spec))
		{
			if ((participants == null) || !participants.exclusive() || (participants.player(profileId).getObjectId() != offer.initiatingCharacterObjectId()))
			{
				_activeRequired.increment();
				return StepResult.activeRequired("economy.social.initiator.required");
			}
			final Player initiator = participants.player(profileId);
			final Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
			if ((counterparty == null) || !validateStandingOffer(spec, initiator, counterparty))
			{
				return StepResult.retry("economy.social.counterparty.awaiting");
			}
			if (spec instanceof DirectTrade trade)
			{
				if (initiator.calculateDistance3D(counterparty) > trade.maximumDistance())
				{
					return StepResult.replan("economy.social.distance.stale");
				}
				if ((initiator.getActiveTradeList() == null) || (counterparty.getActiveTradeList() == null))
				{
					if (counterparty.getActiveRequester() != initiator)
					{
						DirectTradeService.getInstance().request(initiator, counterparty.getObjectId());
					}
					if (spec.counterpartyProfileId() > 0)
					{
						final Player leasedCounterparty = participants.player(spec.counterpartyProfileId());
						if ((leasedCounterparty == counterparty) && (counterparty.getActiveRequester() == initiator))
						{
							DirectTradeService.getInstance().answer(counterparty, true);
						}
					}
				}
				if ((initiator.getActiveTradeList() == null) || (counterparty.getActiveTradeList() == null))
				{
					return StepResult.retry("economy.social.consent.awaiting");
				}
			}
			final PhantomEconomyOfferService.Status accepted = _offers.accept(offer.offerId(), offer.contentHash(), offer.rowVersion(), now);
			if ((accepted == PhantomEconomyOfferService.Status.TRANSITIONED) || (accepted == PhantomEconomyOfferService.Status.IDEMPOTENT))
			{
				_offersAccepted.increment();
				_faults.inject(FaultPoint.AFTER_OFFER_ACCEPTED);
				return StepResult.success(offer.offerId(), "", "economy.social.accepted");
			}
			return StepResult.replan("economy.social.accept.conflict");
		}
	}

	public StepResult reserve(long profileId, PhantomGoal goal, long generation, long tick, long now)
	{
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		if ((offer == null) || (offer.state() != State.ACCEPTED))
		{
			return StepResult.replan("economy.social.offer.not_accepted");
		}
		if (!offer.operationId().isEmpty())
		{
			return StepResult.success(offer.offerId(), offer.operationId(), "economy.social.reserve.idempotent");
		}
		final StoredOperation existing = _reservations.findActive(profileId).orElse(null);
		if (existing != null)
		{
			if ((existing.goalId() == goal.goalId()) && (existing.goalRevision() == goal.revision()) && (existing.kind() == parse(goal).operationKind()) && existing.intentHash().equals(offer.contentHash()))
			{
				final PhantomEconomyOffer refreshed = _offers.find(offer.offerId()).orElseThrow();
				final PhantomEconomyOfferService.Status bound = _offers.bindOperation(refreshed.offerId(), existing.operationId(), refreshed.rowVersion(), now);
				if ((bound == PhantomEconomyOfferService.Status.TRANSITIONED) || (bound == PhantomEconomyOfferService.Status.IDEMPOTENT))
				{
					return StepResult.success(offer.offerId(), existing.operationId(), "economy.social.reserve.recovered");
				}
			}
			return StepResult.replan("economy.social.operation.busy");
		}
		final PhantomSocialEconomyGoalSpec spec = parse(goal);
		try (ParticipantLeases participants = acquireParticipants(profileId, spec))
		{
			if ((participants == null) || !participants.exclusive())
			{
				return StepResult.activeRequired("economy.social.quote.active_required");
			}
			final Player initiator = participants.player(profileId);
			final Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
			final Quote quote = quote(profileId, initiator, counterparty, spec, offer);
			if (quote == null)
			{
				return StepResult.replan("economy.social.quote.stale");
			}
			final int attempt = _reservations.nextAttempt(profileId, goal.goalId(), spec.operationKind(), 32);
			final Identity identity = new Identity(profileId, initiator.getObjectId(), goal.goalId(), goal.revision(), attempt, "economy.social:" + goal.goalId() + ":" + goal.revision() + ":" + attempt, generation, tick);
			final PhantomEconomyOperation operation = new PhantomEconomyOperation(identity, spec.operationKind(), PhantomEconomyOperation.State.PREPARED, quote.authorityHash(), offer.contentHash(), quote.beforePayload(), offer.payload(), now, now, Math.addExact(now, _policy.limits().reservationTtlSeconds() * 1000L), 0);
			final PhantomEconomyReservationService.ReserveResult reserved = _reservations.reserve(operation, quote.reservations());
			if ((reserved.status() != PhantomEconomyReservationService.Status.RESERVED) && (reserved.status() != PhantomEconomyReservationService.Status.IDEMPOTENT))
			{
				return StepResult.replan("economy.social.reserve.conflict");
			}
			_faults.inject(FaultPoint.AFTER_RESERVATIONS);
			final PhantomEconomyOffer refreshed = _offers.find(offer.offerId()).orElseThrow();
			if (_offers.bindOperation(refreshed.offerId(), operation.operationId(), refreshed.rowVersion(), now) == PhantomEconomyOfferService.Status.CONFLICT)
			{
				_reservations.transition(operation.operationId(), PhantomEconomyOperation.State.RESERVED, PhantomEconomyOperation.State.ABORTED, now, new Audit(Result.CONFLICT, "offer.operation.conflict", new byte[0]));
				return StepResult.replan("economy.social.offer.bind_conflict");
			}
			return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.reserved");
		}
	}

	public StepResult dispatch(long profileId, PhantomGoal goal, long now)
	{
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		final StoredOperation operation = operation(offer);
		if ((operation == null) || (operation.kind() != parse(goal).operationKind()))
		{
			return StepResult.replan("economy.social.dispatch.stale");
		}
		if ((operation.state() == PhantomEconomyOperation.State.DISPATCHING) || (operation.state() == PhantomEconomyOperation.State.OBSERVING))
		{
			return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.dispatch.idempotent");
		}
		final var result = _reservations.transition(operation.operationId(), PhantomEconomyOperation.State.RESERVED, PhantomEconomyOperation.State.DISPATCHING, now, null);
		if ((result.status() == PhantomEconomyReservationService.Status.TRANSITIONED) || (result.status() == PhantomEconomyReservationService.Status.IDEMPOTENT))
		{
			_faults.inject(FaultPoint.AFTER_DISPATCHING);
			return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.dispatched");
		}
		return StepResult.replan("economy.social.dispatch.conflict");
	}

	public StepResult observeReconcile(long profileId, PhantomGoal goal, PhantomActivityState activityState, long now)
	{
		if (!activityState.requiresMaterialization())
		{
			_activeRequired.increment();
			return StepResult.activeRequired("economy.social.execution.active_required");
		}
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		final PhantomSocialEconomyGoalSpec spec = parse(goal);
		final StoredOperation operation = operation(offer);
		if (operation == null)
		{
			return StepResult.replan("economy.social.operation.missing");
		}
		if (operation.state().terminal())
		{
			return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.operation.terminal");
		}
		if (operation.state() == PhantomEconomyOperation.State.OBSERVING)
		{
			final AutoCloseable retained = _observerLeases.get(operation.operationId());
			if (retained instanceof ManufactureObserver observer)
			{
				return observer.recover(now);
			}
			terminal(operation, offer, goal, PhantomEconomyOperation.State.INCONSISTENT, Result.INCONSISTENT, "observation.missing_callback", 0, 0, 0, now);
			return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.observation.fail_stop");
		}
		if (operation.state() != PhantomEconomyOperation.State.DISPATCHING)
		{
			return StepResult.replan("economy.social.operation.state");
		}
		try (ParticipantLeases participants = acquireParticipants(profileId, spec))
		{
			if ((participants == null) || !participants.exclusive())
			{
				return StepResult.activeRequired("economy.social.initiator.active_required");
			}
			final Player initiator = participants.player(profileId);
			final Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
			if (counterparty == null)
			{
				if (spec instanceof DirectTrade)
				{
					final TradeList active = initiator.getActiveTradeList();
					final Player expected = active == null ? initiator.getActiveRequester() : active.getPartner();
					return (expected != null) && (expected.getObjectId() == spec.counterpartyCharacterObjectId()) ? abortDirectBeforeEffect(operation, offer, goal, initiator, expected, now, "trade.counterparty.absent") : StepResult.retry("economy.social.trade.cleanup.retry");
				}
				return abortBeforeEffect(operation, offer, now, "counterparty.absent");
			}
			return switch (spec)
			{
				case DirectTrade trade -> executeDirectTrade(initiator, counterparty, trade, operation, offer, goal, participants, now);
				case StoreBuy buy -> executeStoreBuy(initiator, counterparty, buy, operation, offer, goal, now);
				case StoreSell sell -> executeStoreSell(initiator, counterparty, sell, operation, offer, goal, now);
				case Manufacture manufacture -> executeManufacture(initiator, counterparty, manufacture, operation, offer, goal, participants, now);
			};
		}
	}

	public StepResult close(long profileId, PhantomGoal goal, long now)
	{
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		return offer == null ? StepResult.success("", "", "economy.social.closed") : StepResult.retry("economy.social.close.awaiting");
	}

	public StepResult cancel(long profileId, PhantomGoal goal, long now)
	{
		final PhantomEconomyOffer offer = activeOffer(profileId, goal);
		if (offer == null)
		{
			return StepResult.success("", "", "economy.social.cancel.empty");
		}
		final StoredOperation operation = operation(offer);
		if ((operation != null) && !operation.state().terminal())
		{
			final AutoCloseable retained = _observerLeases.get(operation.operationId());
			if (retained instanceof ManufactureObserver observer)
			{
				final StepResult result = observer.cancel(now);
				final StoredOperation current = _reservations.find(operation.operationId()).orElse(operation);
				if (!current.state().terminal())
				{
					return result;
				}
			}
			else
			{
				final PhantomSocialEconomyGoalSpec spec = parse(goal);
				if (spec instanceof DirectTrade)
				{
					try (ParticipantLeases participants = acquireParticipants(profileId, spec))
					{
						if ((participants == null) || !participants.exclusive())
						{
							return StepResult.retry("economy.social.trade.cleanup.retry");
						}
						final Player initiator = participants.player(profileId);
						Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
						if (counterparty == null)
						{
							final TradeList active = initiator.getActiveTradeList();
							counterparty = active == null ? initiator.getActiveRequester() : active.getPartner();
						}
						if ((counterparty == null) || !DirectTradeService.getInstance().cancel(initiator, counterparty))
						{
							return StepResult.retry("economy.social.trade.cleanup.retry");
						}
						final StoredOperation current = _reservations.find(operation.operationId()).orElse(operation);
						if (!current.state().terminal())
						{
							terminal(operation, offer, goal, PhantomEconomyOperation.State.ABORTED, Result.ERROR, "operation.cancelled", 0, 0, 0, now);
						}
					}
				}
				else
				{
					final PhantomEconomyOperation.State terminalState = operation.state() == PhantomEconomyOperation.State.OBSERVING ? PhantomEconomyOperation.State.INCONSISTENT : PhantomEconomyOperation.State.ABORTED;
					terminal(operation, offer, goal, terminalState, terminalState == PhantomEconomyOperation.State.INCONSISTENT ? Result.INCONSISTENT : Result.ERROR, "operation.cancelled", 0, 0, 0, now);
				}
			}
		}
		final PhantomEconomyOffer refreshed = _offers.find(offer.offerId()).orElse(null);
		if ((refreshed != null) && (refreshed.state() == State.ACCEPTED))
		{
			_offers.cancel(refreshed.offerId(), refreshed.rowVersion(), now, "offer.cancelled");
		}
		return StepResult.success(offer.offerId(), operation == null ? "" : operation.operationId(), "economy.social.cancelled");
	}

	public ShutdownResult shutdown(long now)
	{
		final java.util.TreeSet<String> pending = new java.util.TreeSet<>();
		String cursor = "";
		for (int page = 0; page < 1000; page++)
		{
			final List<PhantomEconomyOffer> active = _offers.findActiveAfter(cursor, 100);
			if (active.isEmpty())
			{
				for (String operationId : List.copyOf(_observerLeases.keySet()))
				{
					final StoredOperation operation = _reservations.find(operationId).orElse(null);
					if ((operation == null) || operation.state().terminal())
					{
						closeObserver(operationId);
					}
					else
					{
						pending.add(operationId);
					}
				}
				return new ShutdownResult(pending.isEmpty(), List.copyOf(pending));
			}
			for (PhantomEconomyOffer offer : active)
			{
				cursor = offer.offerId();
				try
				{
					if (!shutdownOffer(offer, now))
					{
						pending.add(offer.operationId().isEmpty() ? offer.offerId() : offer.operationId());
					}
				}
				catch (RuntimeException exception)
				{
					final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElse(offer);
					if (!reconcileOffer(current, now, true) && !_offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false))
					{
						pending.add(offer.operationId().isEmpty() ? offer.offerId() : offer.operationId());
					}
				}
			}
		}
		pending.add("bounded-offer-scan");
		return new ShutdownResult(false, List.copyOf(pending));
	}

	private boolean shutdownOffer(PhantomEconomyOffer offer, long now)
	{
		if (offer.state() != State.ACCEPTED)
		{
			reconcileOffer(offer, now, true);
			return _offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false);
		}
		StoredOperation operation = operation(offer);
		if (operation == null)
		{
			final StoredOperation candidate = _reservations.findActive(offer.initiatingProfileId()).orElse(null);
			if ((candidate != null) && (candidate.goalId() == offer.goalId()) && (candidate.goalRevision() == offer.goalRevision()) && (candidate.kind() == offer.operationKind()) && candidate.intentHash().equals(offer.contentHash()))
			{
				operation = candidate;
			}
		}
		if ((operation != null) && operation.state().terminal())
		{
			reconcileOffer(offer, now, true);
			return _offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false);
		}
		final PhantomGoalStateStore.StoredGoal storedGoal = _goals.load(offer.initiatingProfileId()).orElse(null);
		if ((storedGoal == null) || (storedGoal.goal().goalId() != offer.goalId()) || (storedGoal.goal().revision() != offer.goalRevision()))
		{
			return false;
		}
		final PhantomGoal goal = storedGoal.goal();
		final PhantomSocialEconomyGoalSpec spec = parse(goal);
		if (operation == null)
		{
			if ((spec instanceof DirectTrade) && !cancelDirectForShutdown(offer, spec))
			{
				return false;
			}
			reconcileOffer(offer, now, true);
			return _offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false);
		}
		final AutoCloseable retained = _observerLeases.get(operation.operationId());
		if (retained instanceof ManufactureObserver observer)
		{
			observer.cancel(now);
			final StoredOperation current = _reservations.find(operation.operationId()).orElse(operation);
			if (!current.state().terminal())
			{
				return false;
			}
			reconcileOffer(_offers.find(offer.offerId()).orElse(offer), now, true);
			return _offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false);
		}
		if (spec instanceof DirectTrade)
		{
			if (!cancelDirectForShutdown(offer, spec))
			{
				return false;
			}
				final StoredOperation current = _reservations.find(operation.operationId()).orElse(operation);
				if (!current.state().terminal())
				{
					terminal(operation, offer, goal, PhantomEconomyOperation.State.ABORTED, Result.ERROR, "operation.shutdown", 0, 0, 0, now);
				}
		}
		else
		{
			final PhantomEconomyOperation.State terminalState = operation.state() == PhantomEconomyOperation.State.OBSERVING ? PhantomEconomyOperation.State.INCONSISTENT : PhantomEconomyOperation.State.ABORTED;
			terminal(operation, offer, goal, terminalState, terminalState == PhantomEconomyOperation.State.INCONSISTENT ? Result.INCONSISTENT : Result.ERROR, "operation.shutdown", 0, 0, 0, now);
		}
		reconcileOffer(_offers.find(offer.offerId()).orElse(offer), now, true);
		return _offers.find(offer.offerId()).map(value -> value.state().terminal()).orElse(false);
	}

	private boolean cancelDirectForShutdown(PhantomEconomyOffer offer, PhantomSocialEconomyGoalSpec spec)
	{
		try (ParticipantLeases participants = acquireParticipants(offer.initiatingProfileId(), spec))
		{
			if ((participants == null) || !participants.exclusive())
			{
				return false;
			}
			final Player initiator = participants.player(offer.initiatingProfileId());
			Player counterparty = World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
			if (counterparty == null)
			{
				final TradeList active = initiator.getActiveTradeList();
				counterparty = active == null ? initiator.getActiveRequester() : active.getPartner();
			}
			return (counterparty != null) && DirectTradeService.getInstance().cancel(initiator, counterparty);
		}
	}

	public int reconcileStartup(long now)
	{
		_offers.expireDue(now, 1000);
		String cursor = "";
		int reconciled = 0;
		for (int page = 0; page < 1000; page++)
		{
			final List<PhantomEconomyOffer> active = _offers.findActiveAfter(cursor, 100);
			if (active.isEmpty())
			{
				return reconciled;
			}
			for (PhantomEconomyOffer offer : active)
			{
				cursor = offer.offerId();
				reconciled += reconcileOffer(offer, now, false) ? 1 : 0;
			}
		}
		throw new IllegalStateException("Multiparty economy reconciliation exceeded its bounded offer scan.");
	}

	private boolean reconcileOffer(PhantomEconomyOffer offer, long now, boolean shuttingDown)
	{
		if ((offer.state() == State.DRAFT) && !shuttingDown && (offer.expiresEpochMillis() > now))
		{
			return _offers.offer(offer.offerId(), offer.rowVersion(), now) == PhantomEconomyOfferService.Status.TRANSITIONED;
		}
		if (offer.state() != State.ACCEPTED)
		{
			if (shuttingDown || (offer.expiresEpochMillis() <= now))
			{
				final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElse(null);
				return (current != null) && (_offers.cancel(current.offerId(), current.rowVersion(), now, shuttingDown ? "offer.shutdown" : "offer.expired") == PhantomEconomyOfferService.Status.TRANSITIONED);
			}
			return false;
		}
		final StoredOperation operation = operation(offer);
		if (operation == null)
		{
			if (offer.operationId().isEmpty() && !shuttingDown)
			{
				return false;
			}
			final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElse(null);
			return (current != null) && (_offers.cancel(current.offerId(), current.rowVersion(), now, offer.operationId().isEmpty() ? "offer.shutdown" : "operation.missing") == PhantomEconomyOfferService.Status.TRANSITIONED);
		}
		PhantomEconomyOperation.State state = operation.state();
		if (!state.terminal() && (shuttingDown || (state == PhantomEconomyOperation.State.OBSERVING)))
		{
			final PhantomEconomyOperation.State terminal = state == PhantomEconomyOperation.State.OBSERVING ? PhantomEconomyOperation.State.INCONSISTENT : PhantomEconomyOperation.State.ABORTED;
			final var transitioned = _reservations.transition(operation.operationId(), state, terminal, now, new Audit(terminal == PhantomEconomyOperation.State.INCONSISTENT ? Result.INCONSISTENT : Result.ERROR, shuttingDown ? "operation.shutdown" : "operation.restart_observing", new byte[0]));
			if ((transitioned.status() != PhantomEconomyReservationService.Status.TRANSITIONED) && (transitioned.status() != PhantomEconomyReservationService.Status.IDEMPOTENT))
			{
				return false;
			}
			state = terminal;
		}
		if (!state.terminal())
		{
			return false;
		}
		final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElse(null);
		if ((current == null) || (current.state() != State.ACCEPTED))
		{
			closeObserver(operation.operationId());
			return false;
		}
		final PhantomEconomyOfferService.Status result = switch (state)
		{
			case COMMITTED -> _offers.consume(current.offerId(), operation.operationId(), current.rowVersion(), now);
			case INCONSISTENT -> _offers.inconsistent(current.offerId(), operation.operationId(), current.rowVersion(), now, "operation.reconciled_inconsistent");
			default -> _offers.cancel(current.offerId(), current.rowVersion(), now, "operation.reconciled_aborted");
		};
		closeObserver(operation.operationId());
		return (result == PhantomEconomyOfferService.Status.TRANSITIONED) || (result == PhantomEconomyOfferService.Status.IDEMPOTENT);
	}

	private StepResult executeDirectTrade(Player initiator, Player counterparty, DirectTrade spec, StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, ParticipantLeases participants, long now)
	{
		final TradeList initiatorList = initiator.getActiveTradeList();
		final TradeList counterpartyList = counterparty.getActiveTradeList();
		if ((now >= offer.expiresEpochMillis()) || !directPairCurrent(initiator, counterparty, spec, initiatorList, counterpartyList))
		{
			return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, now >= offer.expiresEpochMillis() ? "trade.external.expired" : "trade.consent.lost");
		}
		if (!populateExactTradeList(initiator, initiatorList, spec.offeredLines(), spec.offeredAdena()))
		{
			return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, "trade.initiator.lines.stale");
		}
		if (spec.counterpartyProfileId() > 0)
		{
			if (!populateExactTradeList(counterparty, counterpartyList, spec.requestedLines(), spec.requestedAdena()))
			{
				return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, "trade.counterparty.lines.stale");
			}
		}
		else if (!exactTradeList(counterpartyList, spec.requestedLines(), spec.requestedAdena()))
		{
			return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, "trade.external.lines.stale");
		}
		if (!exactTradeList(initiatorList, spec.offeredLines(), spec.offeredAdena()) || !exactTradeList(counterpartyList, spec.requestedLines(), spec.requestedAdena()))
		{
			return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, "trade.accepted.lines.stale");
		}
		if (spec.counterpartyProfileId() == 0)
		{
			if (initiatorList.isConfirmed())
			{
				return abortDirectBeforeEffect(operation, offer, goal, initiator, counterparty, now, "trade.phantom.confirmed_early");
			}
			if (!counterpartyList.isConfirmed())
			{
				return StepResult.retry("economy.social.trade.external_confirmation.awaiting");
			}
		}
		if (!participants.exclusive() || _observerLeases.containsKey(operation.operationId()))
		{
			return StepResult.retry("economy.social.trade.lease_boundary.retry");
		}
		final AutoCloseable registration = DirectTradeService.getInstance().observe(initiator.getObjectId(), counterparty.getObjectId(), new DirectObserver(operation, offer, goal, initiator, counterparty, spec, _reservations.findReservations(operation.operationId()), participants, now));
		participants.retain();
		installObserver(operation.operationId(), () -> { close(registration); participants.release(); });
		DirectTradeService.getInstance().finish(initiator, true);
		if (spec.counterpartyProfileId() > 0)
		{
			DirectTradeService.getInstance().finish(counterparty, true);
		}
		StoredOperation after = _reservations.find(operation.operationId()).orElse(operation);
		if (!after.state().terminal())
		{
			if (!DirectTradeService.getInstance().cancel(initiator, counterparty))
			{
				return StepResult.retry("economy.social.trade.cleanup.retry");
			}
			after = _reservations.find(operation.operationId()).orElse(after);
			if (!after.state().terminal())
			{
				terminal(operation, offer, goal, PhantomEconomyOperation.State.INCONSISTENT, Result.INCONSISTENT, "trade.synchronous_terminal_missing", 0, 0, 0, now);
			}
		}
		return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.trade.complete");
	}

	private boolean directPairCurrent(Player initiator, Player counterparty, DirectTrade spec, TradeList initiatorList, TradeList counterpartyList)
	{
		return (World.getInstance().getPlayer(counterparty.getObjectId()) == counterparty) && counterparty.isOnline() && (initiator.getInstanceId() == counterparty.getInstanceId()) && (initiator.calculateDistance3D(counterparty) <= Math.min(150, spec.maximumDistance())) && (initiatorList != null) && (counterpartyList != null) && (initiatorList.getPartner() == counterparty) && (counterpartyList.getPartner() == initiator);
	}

	private boolean populateExactTradeList(Player owner, TradeList list, List<Line> lines, long adena)
	{
		if (exactTradeList(list, lines, adena))
		{
			return true;
		}
		if (list.isConfirmed() || !list.getItems().isEmpty())
		{
			return false;
		}
		for (Line line : lines)
		{
			if (DirectTradeService.getInstance().addItem(owner, 0, line.objectId(), line.count()) != DirectTradeService.Result.UPDATED)
			{
				return false;
			}
		}
		if ((adena > 0) && (DirectTradeService.getInstance().addItem(owner, 0, owner.getInventory().getAdenaInstance().getObjectId(), adena) != DirectTradeService.Result.UPDATED))
		{
			return false;
		}
		return exactTradeList(list, lines, adena);
	}

	private StepResult abortDirectBeforeEffect(StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, Player initiator, Player counterparty, long now, String reason)
	{
		if (!DirectTradeService.getInstance().cancel(initiator, counterparty))
		{
			return StepResult.retry("economy.social.trade.cleanup.retry");
		}
		final StoredOperation current = _reservations.find(operation.operationId()).orElse(operation);
		if (!current.state().terminal())
		{
			terminal(operation, offer, goal, PhantomEconomyOperation.State.ABORTED, Result.ERROR, reason, 0, 0, 0, now);
		}
		return StepResult.replan("economy.social." + reason);
	}

	private StepResult executeStoreBuy(Player buyer, Player owner, StoreBuy spec, StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, long now)
	{
		if (!PrivateStoreService.listingHash(owner.getSellList()).equals(spec.listingHash()))
		{
			return abortBeforeEffect(operation, offer, now, "store.list.changed");
		}
		final Set<RequestTrade> request = new HashSet<>();
		for (Line line : spec.lines())
		{
			request.add(new RequestTrade(line.objectId(), line.itemId(), line.count(), line.price()));
		}
		final String requestHash = PrivateStoreService.requestHash(request);
		final Conservation before = Conservation.capture(buyer, owner, itemIds(spec.lines()));
		installObserver(operation.operationId(), PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, buyer.getObjectId(), owner.getObjectId(), new StoreObserver(operation, offer, goal, before, itemDeltas(spec.lines(), 1), -totalPrice(spec.lines()), PrivateStoreService.Direction.BUY_FROM_SELL_STORE, spec.listingHash(), requestHash, spec.packageExpected(), _reservations.findReservations(operation.operationId()), now)));
		final PrivateStoreService.Result result = PrivateStoreService.getInstance().buyExact(buyer, owner.getObjectId(), request, spec.listingHash(), requestHash);
		if (result != PrivateStoreService.Result.COMMITTED)
		{
			final boolean unchanged = before.equals(Conservation.capture(buyer, owner, itemIds(spec.lines())));
			terminal(operation, offer, goal, unchanged ? PhantomEconomyOperation.State.ABORTED : PhantomEconomyOperation.State.INCONSISTENT, unchanged ? Result.ERROR : Result.INCONSISTENT, unchanged ? "store.pre_effect_rejected" : "dispatch.ambiguous", 0, 0, 0, now);
		}
		return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.store.buy.complete");
	}

	private StepResult executeStoreSell(Player seller, Player owner, StoreSell spec, StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, long now)
	{
		if (!PrivateStoreService.listingHash(owner.getBuyList()).equals(spec.listingHash()))
		{
			return abortBeforeEffect(operation, offer, now, "store.list.changed");
		}
		final RequestTrade[] request = spec.lines().stream().map(line -> new RequestTrade(line.objectId(), line.itemId(), line.count(), line.price())).toArray(RequestTrade[]::new);
		final String requestHash = PrivateStoreService.requestHash(request);
		final Conservation before = Conservation.capture(seller, owner, itemIds(spec.lines()));
		installObserver(operation.operationId(), PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.SELL_TO_BUY_STORE, seller.getObjectId(), owner.getObjectId(), new StoreObserver(operation, offer, goal, before, itemDeltas(spec.lines(), -1), totalPrice(spec.lines()), PrivateStoreService.Direction.SELL_TO_BUY_STORE, spec.listingHash(), requestHash, false, _reservations.findReservations(operation.operationId()), now)));
		final PrivateStoreService.Result result = PrivateStoreService.getInstance().sellExact(seller, owner.getObjectId(), request, spec.listingHash(), requestHash);
		if (result != PrivateStoreService.Result.COMMITTED)
		{
			final boolean unchanged = before.equals(Conservation.capture(seller, owner, itemIds(spec.lines())));
			terminal(operation, offer, goal, unchanged ? PhantomEconomyOperation.State.ABORTED : PhantomEconomyOperation.State.INCONSISTENT, unchanged ? Result.ERROR : Result.INCONSISTENT, unchanged ? "store.pre_effect_rejected" : "dispatch.ambiguous", 0, 0, 0, now);
		}
		return StepResult.success(offer.offerId(), operation.operationId(), "economy.social.store.sell.complete");
	}

	private StepResult executeManufacture(Player customer, Player manufacturer, Manufacture spec, StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, ParticipantLeases participants, long now)
	{
		final ManufactureItem listing = manufacturer.getManufactureItems().get(spec.recipeListId());
		if ((listing == null) || (listing.getCost() != spec.listingPrice()))
		{
			return abortBeforeEffect(operation, offer, now, "manufacture.list.changed");
		}
		final RecipeList recipe = RecipeData.getInstance().getRecipeList(spec.recipeListId());
		if (recipe == null)
		{
			return abortBeforeEffect(operation, offer, now, "manufacture.recipe.changed");
		}
		final Conservation before = Conservation.capture(customer, manufacturer, manufactureItemIds(recipe));
		final List<Reservation> acceptedReservations = _reservations.findReservations(operation.operationId());
		if (!operation.intentHash().equals(offer.contentHash()) || !operation.authorityHash().equals(manufactureAuthority(offer, spec, customer, manufacturer)) || !reservationsMatchRuntime(acceptedReservations))
		{
			return abortBeforeEffect(operation, offer, now, "manufacture.authority.changed");
		}
		final var observing = _reservations.transition(operation.operationId(), PhantomEconomyOperation.State.DISPATCHING, PhantomEconomyOperation.State.OBSERVING, now, null);
		if ((observing.status() != PhantomEconomyReservationService.Status.TRANSITIONED) && (observing.status() != PhantomEconomyReservationService.Status.IDEMPOTENT))
		{
			return StepResult.replan("economy.social.observe.conflict");
		}
		try
		{
			_faults.inject(FaultPoint.AFTER_OBSERVING);
		}
		catch (RuntimeException exception)
		{
			return abortBeforeEffect(operation, offer, now, "manufacture.fault_after_observing");
		}
		participants.retain();
		final ManufactureObserver observer = new ManufactureObserver(operation, offer, goal, customer, manufacturer, spec, before, acceptedReservations, participants, now);
		installObserver(operation.operationId(), observer);
		if (ManufactureService.getInstance().manufacture(customer, manufacturer.getObjectId(), spec.recipeListId(), observer) != ManufactureService.Result.STARTED)
		{
			terminal(operation, offer, goal, PhantomEconomyOperation.State.ABORTED, Result.ERROR, "manufacture.pre_effect_rejected", 0, 0, 0, now);
		}
		final StoredOperation after = _reservations.find(operation.operationId()).orElse(operation);
		return after.state().terminal() ? StepResult.success(offer.offerId(), operation.operationId(), "economy.social.manufacture.complete") : StepResult.retry("economy.social.manufacture.observing");
	}

	private Quote quote(long profileId, Player initiator, Player counterparty, PhantomSocialEconomyGoalSpec spec, PhantomEconomyOffer offer)
	{
		if ((counterparty == null) || !validateStandingOffer(spec, initiator, counterparty))
		{
			return null;
		}
		final List<Reservation> reservations = new ArrayList<>();
		final StringBuilder authority = new StringBuilder(512).append(offer.offerId()).append('|').append(spec.operationKind()).append('|').append(initiator.getObjectId()).append('|').append(counterparty.getObjectId()).append('|');
		if (spec instanceof DirectTrade trade)
		{
			if ((initiator.getActiveTradeList() == null) || (counterparty.getActiveTradeList() == null))
			{
				return null;
			}
			for (Line line : trade.offeredLines())
			{
				if (!reserveObject(reservations, profileId, initiator, line))
				{
					return null;
				}
			}
			for (Line line : trade.requestedLines())
			{
				if (!reserveObject(reservations, trade.counterpartyProfileId(), counterparty, line))
				{
					return null;
				}
			}
			reserveAdena(reservations, profileId, initiator, trade.offeredAdena());
			reserveAdena(reservations, trade.counterpartyProfileId(), counterparty, trade.requestedAdena());
			reserveCapacity(reservations, profileId, initiator);
			reserveCapacity(reservations, trade.counterpartyProfileId(), counterparty);
		}
		else if (spec instanceof StoreBuy buy)
		{
			long total = 0;
			for (Line line : buy.lines())
			{
				total = Math.addExact(total, Math.multiplyExact(line.count(), line.price()));
				if (!reserveObject(reservations, buy.counterpartyProfileId(), counterparty, line))
				{
					return null;
				}
			}
			if (total > buy.maximumTotalPrice())
			{
				return null;
			}
			reserveAdena(reservations, profileId, initiator, total);
			reserveCapacity(reservations, profileId, initiator);
			authority.append(buy.listingHash());
		}
		else if (spec instanceof StoreSell sell)
		{
			long total = 0;
			for (Line line : sell.lines())
			{
				total = Math.addExact(total, Math.multiplyExact(line.count(), line.price()));
				if (!reserveObject(reservations, profileId, initiator, line))
				{
					return null;
				}
			}
			if (total < sell.minimumTotalProceeds())
			{
				return null;
			}
			reserveAdena(reservations, sell.counterpartyProfileId(), counterparty, total);
			reserveCapacity(reservations, sell.counterpartyProfileId(), counterparty);
			authority.append(sell.listingHash());
		}
		else if (spec instanceof Manufacture manufacture)
		{
			final RecipeList recipe = RecipeData.getInstance().getRecipeList(manufacture.recipeListId());
			final ManufactureItem listing = counterparty.getManufactureItems().get(manufacture.recipeListId());
			if ((recipe == null) || (listing == null) || (listing.getCost() != manufacture.listingPrice()) || (recipe.getItemId() != manufacture.productItemId()) || (recipe.getCount() != manufacture.productCount()) || (manufacture.listingPrice() > manufacture.maximumTotalFee()))
			{
				return null;
			}
			final Map<Integer, Long> ingredientCounts = manufactureIngredientCounts(recipe);
			final Map<Integer, Long> reservedCounts = new TreeMap<>(ingredientCounts);
			reservedCounts.putIfAbsent(manufacture.productItemId(), manufacture.productCount());
			if (recipe.getRareItemId() > 0)
			{
				reservedCounts.putIfAbsent(recipe.getRareItemId(), Math.max(1L, recipe.getRareCount()));
			}
			for (Map.Entry<Integer, Long> entry : reservedCounts.entrySet())
			{
				final long current = initiator.getInventory().getInventoryItemCount(entry.getKey(), -1);
				if (current < ingredientCounts.getOrDefault(entry.getKey(), 0L))
				{
					return null;
				}
				reservations.add(new Reservation(profileId, initiator.getObjectId(), initiator.getClassIndex(), ResourceKind.ITEM_COUNT, 0, entry.getKey(), entry.getValue(), current, 0, "INVENTORY"));
			}
			reserveAdena(reservations, profileId, initiator, manufacture.listingPrice());
			reserveCapacity(reservations, profileId, initiator);
			reservations.add(new Reservation(manufacture.counterpartyProfileId(), counterparty.getObjectId(), counterparty.getClassIndex(), ResourceKind.RECIPE, 0, recipe.getId(), 0, 0, 0, ""));
			reservations.add(new Reservation(manufacture.counterpartyProfileId(), counterparty.getObjectId(), counterparty.getClassIndex(), ResourceKind.SKILL, 0, recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId(), 0, 0, 0, ""));
			authority.append(recipe.getId()).append('|').append(listing.getCost()).append('|').append(recipe.getSuccessRate());
		}
		final String before = initiator.getObjectId() + "|" + initiator.getAdena() + "|" + counterparty.getObjectId() + "|" + counterparty.getAdena();
		final List<Reservation> canonical = PhantomEconomyOperation.canonicalReservations(reservations, _policy.limits().reservationsPerOperation());
		final String authorityHash = spec instanceof DirectTrade trade ? directAuthority(offer, trade, canonical) : spec instanceof Manufacture manufacture ? manufactureAuthority(offer, manufacture, initiator, counterparty) : PhantomEconomyOperation.sha256(authority.toString());
		return new Quote(authorityHash, canonical, before.getBytes(StandardCharsets.US_ASCII));
	}

	private String manufactureAuthority(PhantomEconomyOffer offer, Manufacture spec, Player customer, Player manufacturer)
	{
		final RecipeList recipe = RecipeData.getInstance().getRecipeList(spec.recipeListId());
		final ManufactureItem listing = manufacturer.getManufactureItems().get(spec.recipeListId());
		final var productTemplate = recipe == null ? null : ItemData.getInstance().getTemplate(recipe.getItemId());
		if ((recipe == null) || (listing == null) || (productTemplate == null) || ((recipe.getRareItemId() > 0) && (ItemData.getInstance().getTemplate(recipe.getRareItemId()) == null)))
		{
			return "";
		}
		final int skillId = recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId();
		final var skill = manufacturer.getKnownSkill(skillId);
		final StringBuilder facts = new StringBuilder(1024).append(offer.contentHash()).append('|').append(offer.initiatingCharacterObjectId()).append('|').append(offer.counterpartyCharacterObjectId()).append('|').append(customer.getObjectId()).append('|').append(manufacturer.getObjectId()).append('|').append(spec.recipeListId()).append('|').append(spec.listingPrice()).append('|').append(listing.getCost()).append('|').append(recipe.getId()).append('|').append(recipe.getRecipeId()).append('|').append(recipe.getLevel()).append('|').append(recipe.isDwarvenRecipe()).append('|').append(recipe.getItemId()).append('|').append(recipe.getCount()).append('|').append(recipe.getSuccessRate()).append('|').append(recipe.getRareItemId()).append('|').append(recipe.getRareCount()).append('|').append(recipe.getRarity()).append('|').append(manufacturer.hasRecipeList(recipe.getId())).append('|').append(skillId).append('|').append(manufacturer.getSkillLevel(skillId)).append('|').append(skill == null ? "" : skill.getClass().getName());
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			facts.append("|ingredient:").append(ingredient.getItemId()).append(':').append(ingredient.getQuantity());
		}
		for (RecipeStatHolder statUse : recipe.getStatUse())
		{
			facts.append("|stat-use:").append(statUse.getType().name()).append(':').append(statUse.getValue());
		}
		for (RecipeStatHolder statChange : recipe.getAltStatChange())
		{
			facts.append("|alt-stat-change:").append(statChange.getType().name()).append(':').append(statChange.getValue());
		}
		appendOutputTemplateFacts(facts, recipe.getItemId());
		if (recipe.getRareItemId() > 0)
		{
			appendOutputTemplateFacts(facts, recipe.getRareItemId());
		}
		final long outputWeight = (long) productTemplate.getWeight() * recipe.getCount();
		facts.append("|craft-config:").append(PlayerConfig.IS_CRAFTING_ENABLED).append(':').append(PlayerConfig.CRAFT_MASTERWORK).append(':').append(PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE).append(':').append(PlayerConfig.ALT_GAME_CREATION).append(':').append(PlayerConfig.ALT_GAME_CREATION_SPEED).append(':').append(PlayerConfig.ALT_GAME_CREATION_XP_RATE).append(':').append(PlayerConfig.ALT_GAME_CREATION_SP_RATE).append(':').append(PlayerConfig.ALT_GAME_CREATION_RARE_XPSP_RATE).append(':').append(PlayerConfig.AUTO_LOOT_SLOT_LIMIT);
		facts.append("|customer-capacity:").append(customer.getInventory().getNonQuestSize()).append(':').append(customer.getInventoryLimit()).append(':').append(customer.getInventory().getTotalWeight()).append(':').append(customer.getMaxLoad()).append(':').append(customer.getInventory().validateCapacity(1)).append(':').append(customer.getInventory().validateWeight(outputWeight));
		facts.append("|economy-policy:").append(_policy.hash());
		return PhantomEconomyOperation.sha256(facts.toString());
	}

	private static void appendOutputTemplateFacts(StringBuilder facts, int itemId)
	{
		final var template = ItemData.getInstance().getTemplate(itemId);
		if (template == null)
		{
			facts.append("|output:").append(itemId).append(":missing");
			return;
		}
		facts.append("|output:").append(itemId).append(':').append(template.getClass().getName()).append(':').append(template.isStackable()).append(':').append(template.getWeight());
	}

	private static boolean reserveObject(List<Reservation> reservations, long profileId, Player owner, Line line)
	{
		final Item item = owner.getInventory().getItemByObjectId(line.objectId());
		if ((item == null) || (item.getCount() < line.count()) || ((line.itemId() > 0) && (item.getId() != line.itemId())) || !item.isTradeable())
		{
			return false;
		}
		reservations.add(new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.ITEM_OBJECT, item.getObjectId(), item.getId(), line.count(), item.getCount(), item.getEnchantLevel(), item.getItemLocation().name()));
		return true;
	}

	private static void reserveAdena(List<Reservation> reservations, long profileId, Player owner, long amount)
	{
		if (amount > 0)
		{
			reservations.add(new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.ADENA, 0, Inventory.ADENA_ID, amount, owner.getAdena(), 0, "INVENTORY"));
		}
	}

	private static void reserveCapacity(List<Reservation> reservations, long profileId, Player owner)
	{
		reservations.add(new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 0, 0, 0, ""));
	}

	private static Map<Integer, Long> manufactureIngredientCounts(RecipeList recipe)
	{
		final Map<Integer, Long> result = new TreeMap<>();
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			result.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact);
		}
		return Map.copyOf(result);
	}

	private static Set<Integer> manufactureItemIds(RecipeList recipe)
	{
		final Set<Integer> result = new java.util.TreeSet<>(manufactureIngredientCounts(recipe).keySet());
		result.add(recipe.getItemId());
		if (recipe.getRareItemId() > 0)
		{
			result.add(recipe.getRareItemId());
		}
		return Set.copyOf(result);
	}

	private static Map<Integer, Long> eventItems(RecipeCraftObserver.Event event)
	{
		final Map<Integer, Long> result = new TreeMap<>();
		for (RecipeCraftObserver.ItemDelta item : event.items())
		{
			result.merge(item.itemId(), item.count(), Math::addExact);
		}
		return Map.copyOf(result);
	}

	private ParticipantLeases acquireParticipants(long initiatingProfileId, PhantomSocialEconomyGoalSpec spec)
	{
		final TreeMap<Long, Integer> expected = new TreeMap<>();
		final PhantomProfile initiator = _profiles.find(initiatingProfileId).orElse(null);
		if ((initiator == null) || (initiator.characterObjectId() == null))
		{
			return null;
		}
		expected.put(initiatingProfileId, initiator.characterObjectId());
		if (spec.counterpartyProfileId() > 0)
		{
			final PhantomProfile counterparty = _profiles.find(spec.counterpartyProfileId()).orElse(null);
			if ((counterparty == null) || (counterparty.characterObjectId() == null) || (counterparty.characterObjectId() != spec.counterpartyCharacterObjectId()))
			{
				return null;
			}
			expected.put(spec.counterpartyProfileId(), counterparty.characterObjectId());
		}
		final ParticipantLeases result = new ParticipantLeases();
		for (Map.Entry<Long, Integer> participant : expected.entrySet())
		{
			final var before = _materialization.find(participant.getKey()).orElse(null);
			if ((before == null) || (before.characterObjectId() != participant.getValue()) || (before.admittedActionCount() != 0))
			{
				result.release();
				return null;
			}
			final ActionLease lease = _materialization.tryAcquireAction(participant.getKey()).orElse(null);
			if (lease == null)
			{
				result.release();
				return null;
			}
			result.add(participant.getKey(), lease);
			final var admitted = _materialization.find(participant.getKey()).orElse(null);
			if ((admitted == null) || (admitted.characterObjectId() != participant.getValue()) || (admitted.admittedActionCount() != 1) || (lease.player().getObjectId() != participant.getValue()))
			{
				result.release();
				return null;
			}
		}
		return result;
	}

	private boolean validateStandingOffer(PhantomSocialEconomyGoalSpec spec, Player initiator, Player counterparty)
	{
		if ((initiator.getObjectId() == counterparty.getObjectId()) || (spec.counterpartyProfileId() > 0 && !exactCounterparty(spec)))
		{
			return false;
		}
		return switch (spec)
		{
			case DirectTrade trade -> initiator.calculateDistance3D(counterparty) <= trade.maximumDistance();
			case StoreBuy buy -> ((counterparty.getPrivateStoreType() == PrivateStoreType.SELL) || (counterparty.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL)) && (buy.packageExpected() == (counterparty.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL)) && PrivateStoreService.listingHash(counterparty.getSellList()).equals(buy.listingHash());
			case StoreSell sell -> (counterparty.getPrivateStoreType() == PrivateStoreType.BUY) && PrivateStoreService.listingHash(counterparty.getBuyList()).equals(sell.listingHash());
			case Manufacture manufacture -> (counterparty.getPrivateStoreType() == PrivateStoreType.MANUFACTURE) && (counterparty.getManufactureItems().get(manufacture.recipeListId()) != null) && (counterparty.getManufactureItems().get(manufacture.recipeListId()).getCost() == manufacture.listingPrice());
		};
	}

	private boolean exactCounterparty(PhantomSocialEconomyGoalSpec spec)
	{
		if (spec.counterpartyProfileId() == 0)
		{
			return true;
		}
		final PhantomProfile profile = _profiles.find(spec.counterpartyProfileId()).orElse(null);
		return (profile != null) && Objects.equals(profile.characterObjectId(), spec.counterpartyCharacterObjectId());
	}

	private void terminal(StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, PhantomEconomyOperation.State terminal, Result result, String reason, long consumed, long produced, long adena, long now)
	{
		PhantomEconomyOperation.State finalState = terminal;
		Result finalResult = result;
		String finalReason = reason;
		if (terminal == PhantomEconomyOperation.State.COMMITTED)
		{
			try
			{
				completeGoal(operation.profileId(), goal);
			}
			catch (RuntimeException exception)
			{
				finalState = PhantomEconomyOperation.State.INCONSISTENT;
				finalResult = Result.INCONSISTENT;
				finalReason = "goal.authority.ambiguous";
			}
		}
		final PhantomEconomyOperation.State expected = _reservations.find(operation.operationId()).map(StoredOperation::state).orElse(operation.state());
		final var transitioned = _reservations.transition(operation.operationId(), expected, finalState, now, new Audit(finalResult, finalReason, conservationPayload(operation, consumed, produced, adena), consumed, produced, adena, adena, 0, 0));
		final boolean durableTerminal = (transitioned.status() == PhantomEconomyReservationService.Status.TRANSITIONED) || (transitioned.status() == PhantomEconomyReservationService.Status.IDEMPOTENT);
		try
		{
			if (durableTerminal)
			{
				_faults.inject(FaultPoint.AFTER_OPERATION_AUDIT);
				final PhantomEconomyOffer current = _offers.find(offer.offerId()).orElse(null);
				if ((current != null) && (current.state() == State.ACCEPTED))
				{
					if (finalState == PhantomEconomyOperation.State.COMMITTED)
					{
						_offers.consume(current.offerId(), operation.operationId(), current.rowVersion(), now);
						_committed.increment();
					}
					else if (finalState == PhantomEconomyOperation.State.INCONSISTENT)
					{
						_offers.inconsistent(current.offerId(), operation.operationId(), current.rowVersion(), now, finalReason);
						_inconsistent.increment();
					}
					else
					{
						_offers.cancel(current.offerId(), current.rowVersion(), now, finalReason);
					}
				}
			}
		}
		finally
		{
			if (durableTerminal)
			{
				closeObserver(operation.operationId());
			}
		}
	}

	private StepResult abortBeforeEffect(StoredOperation operation, PhantomEconomyOffer offer, long now, String reason)
	{
		terminal(operation, offer, null, PhantomEconomyOperation.State.ABORTED, Result.ERROR, reason, 0, 0, 0, now);
		return StepResult.replan("economy.social." + reason);
	}

	private void completeGoal(long profileId, PhantomGoal expected)
	{
		if (expected == null)
		{
			return;
		}
		final var stored = _goals.load(profileId).orElse(null);
		if ((stored == null) || (stored.goal().goalId() != expected.goalId()) || (stored.goal().revision() != expected.revision()))
		{
			throw new IllegalStateException("Social economy Goal authority changed after canonical mutation.");
		}
		final PhantomGoal completed = new PhantomGoal(expected.goalId(), expected.goalType(), PhantomGoalStatus.COMPLETED, expected.subject(), expected.target(), expected.requiredAmount(), expected.requiredAmount(), expected.acquisitionMethod(), expected.validSources(), expected.selectedAnchor(), expected.purposeKey(), expected.priority(), expected.riskBudget(), expected.expenseBudget(), expected.deadlineEpochMillis(), expected.constraints(), "economy.social.committed", Math.addExact(expected.revision(), 1));
		_goals.replace(profileId, stored.rowVersion(), completed);
		_faults.inject(FaultPoint.AFTER_GOAL_WRITE);
	}

	private void installObserver(String operationId, AutoCloseable lease)
	{
		if (_observerLeases.size() >= MAX_RETAINED_OBSERVERS)
		{
			close(lease);
			throw new IllegalStateException("Multiparty observer bound exceeded.");
		}
		final AutoCloseable previous = _observerLeases.putIfAbsent(operationId, lease);
		if (previous != null)
		{
			close(lease);
		}
	}

	private void closeObserver(String operationId)
	{
		close(_observerLeases.remove(operationId));
	}

	private static void close(AutoCloseable lease)
	{
		if (lease != null)
		{
			try
			{
				lease.close();
			}
			catch (Exception exception)
			{
				throw new IllegalStateException("Could not close economy observer.", exception);
			}
		}
	}

	private PhantomEconomyOffer activeOffer(long profileId, PhantomGoal goal)
	{
		return _offers.findActive(profileId, goal.goalId(), goal.revision()).orElse(null);
	}

	private StoredOperation operation(PhantomEconomyOffer offer)
	{
		return (offer == null) || offer.operationId().isEmpty() ? null : _reservations.find(offer.operationId()).orElse(null);
	}

	private static PhantomSocialEconomyGoalSpec parse(PhantomGoal goal)
	{
		return PhantomSocialEconomyGoalSpec.parse(goal);
	}

	private static boolean isStore(PhantomSocialEconomyGoalSpec spec)
	{
		return (spec instanceof StoreBuy) || (spec instanceof StoreSell) || (spec instanceof Manufacture);
	}

	private static Set<Integer> itemIds(List<Line> lines)
	{
		final Set<Integer> result = new HashSet<>();
		for (Line line : lines)
		{
			if (line.itemId() > 0)
			{
				result.add(line.itemId());
			}
		}
		return Set.copyOf(result);
	}

	private static Map<Integer, Long> itemDeltas(List<Line> lines, int direction)
	{
		final Map<Integer, Long> result = new TreeMap<>();
		for (Line line : lines)
		{
			result.merge(line.itemId(), Math.multiplyExact(line.count(), direction), Math::addExact);
		}
		return Map.copyOf(result);
	}

	private static long totalPrice(List<Line> lines)
	{
		long result = 0;
		for (Line line : lines)
		{
			result = Math.addExact(result, Math.multiplyExact(line.count(), line.price()));
		}
		return result;
	}

	private static Set<Integer> tradeItemIds(TradeList first, TradeList second)
	{
		final Set<Integer> result = new HashSet<>();
		first.getItems().forEach(item -> result.add(item.getItem().getId()));
		second.getItems().forEach(item -> result.add(item.getItem().getId()));
		return Set.copyOf(result);
	}

	private static String directAuthority(PhantomEconomyOffer offer, DirectTrade spec, List<Reservation> reservations)
	{
		final StringBuilder value = new StringBuilder(1024).append(offer.offerId()).append('|').append(offer.contentHash()).append('|').append(spec.counterpartyProfileId()).append('|').append(spec.counterpartyCharacterObjectId()).append('|').append(spec.offeredAdena()).append('|').append(spec.requestedAdena()).append('|').append(spec.maximumDistance()).append('|').append(spec.expiresEpochMillis()).append('|');
		spec.offeredLines().stream().sorted(java.util.Comparator.comparingInt(Line::objectId)).forEach(line -> value.append('O').append(':').append(line.objectId()).append(':').append(line.itemId()).append(':').append(line.count()).append(':').append(line.price()).append(';'));
		spec.requestedLines().stream().sorted(java.util.Comparator.comparingInt(Line::objectId)).forEach(line -> value.append('R').append(':').append(line.objectId()).append(':').append(line.itemId()).append(':').append(line.count()).append(':').append(line.price()).append(';'));
		for (Reservation reservation : reservations)
		{
			value.append(reservation.canonicalKey()).append(':').append(reservation.profileId()).append(':').append(reservation.ownerObjectId()).append(':').append(reservation.ownerClassIndex()).append(':').append(reservation.objectId()).append(':').append(reservation.itemId()).append(':').append(reservation.count()).append(':').append(reservation.expectedCount()).append(':').append(reservation.expectedEnchantLevel()).append(':').append(reservation.expectedLocation()).append(';');
		}
		return PhantomEconomyOperation.sha256(value.toString());
	}

	private static boolean reservationsMatchRuntime(List<Reservation> reservations)
	{
		for (Reservation reservation : reservations)
		{
			final Player owner = World.getInstance().getPlayer(reservation.ownerObjectId());
			if (owner == null)
			{
				return false;
			}
			if (reservation.kind() == ResourceKind.ITEM_OBJECT)
			{
				final Item item = owner.getInventory().getItemByObjectId(reservation.objectId());
				if ((item == null) || (item.getId() != reservation.itemId()) || (item.getCount() != reservation.expectedCount()) || (item.getCount() < reservation.count()) || (item.getEnchantLevel() != reservation.expectedEnchantLevel()) || !item.getItemLocation().name().equals(reservation.expectedLocation()))
				{
					return false;
				}
			}
			else if ((reservation.kind() == ResourceKind.ADENA) && ((owner.getAdena() != reservation.expectedCount()) || (owner.getAdena() < reservation.count())))
			{
				return false;
			}
		}
		return true;
	}

	private static String transferKey(int ownerObjectId, int receiverObjectId, int objectId, int itemId, long count)
	{
		return ownerObjectId + ":" + receiverObjectId + ":" + objectId + ":" + itemId + ":" + count;
	}

	private static void addExpectedAdenaTransfer(List<String> expected, TradeList first, TradeList second, Player owner, Player receiver, long amount)
	{
		if (amount <= 0)
		{
			return;
		}
		final TradeList ownerList = first.getOwner() == owner ? first : second;
		final int objectId = ownerList.getItems().stream().filter(item -> item.getItem().getId() == Inventory.ADENA_ID).mapToInt(TradeItem::getObjectId).findFirst().orElse(0);
		expected.add(transferKey(owner.getObjectId(), receiver.getObjectId(), objectId, Inventory.ADENA_ID, amount));
	}

	private static boolean exactTradeList(TradeList list, List<Line> lines, long adena)
	{
		final Map<String, Long> expected = new TreeMap<>();
		for (Line line : lines)
		{
			final Item exactItem = list.getOwner().getInventory().getItemByObjectId(line.objectId());
			final int itemId = line.itemId() > 0 ? line.itemId() : exactItem == null ? 0 : exactItem.getId();
			if ((itemId <= 0) || (expected.put(line.objectId() + ":" + itemId, line.count()) != null))
			{
				return false;
			}
		}
		if (adena > 0)
		{
			final Item adenaItem = list.getOwner().getInventory().getAdenaInstance();
			if ((adenaItem == null) || (expected.put(adenaItem.getObjectId() + ":" + Inventory.ADENA_ID, adena) != null))
			{
				return false;
			}
		}
		final Map<String, Long> actual = new TreeMap<>();
		for (TradeItem item : list.getItems())
		{
			if (actual.put(item.getObjectId() + ":" + item.getItem().getId(), item.getCount()) != null)
			{
				return false;
			}
		}
		return expected.equals(actual);
	}

	private static byte[] conservationPayload(StoredOperation operation, long consumed, long produced, long adena)
	{
		return (operation.kind() + "|" + operation.profileId() + "|" + consumed + "|" + produced + "|" + adena).getBytes(StandardCharsets.US_ASCII);
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_activeRequired.sum(), _offersAccepted.sum(), _committed.sum(), _inconsistent.sum(), _observerLeases.size());
	}

	private final class DirectObserver implements DirectTradeService.Observer
	{
		private final StoredOperation _operation;
		private final PhantomEconomyOffer _offer;
		private final PhantomGoal _goal;
		private final Player _first;
		private final Player _second;
		private final DirectTrade _spec;
		private final List<Reservation> _acceptedReservations;
		private final ParticipantLeases _participants;
		private final List<String> _actualTransfers = new ArrayList<>();
		private final long _now;
		private Conservation _before;
		private boolean _firstAdenaObserved;
		private boolean _firstItemObserved;
		private String _preEffectReason = "trade.before_execute_rejected";

		private DirectObserver(StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, Player first, Player second, DirectTrade spec, List<Reservation> acceptedReservations, ParticipantLeases participants, long now)
		{
			_operation = operation;
			_offer = offer;
			_goal = goal;
			_first = first;
			_second = second;
			_spec = spec;
			_acceptedReservations = List.copyOf(acceptedReservations);
			_participants = participants;
			_now = now;
		}

		@Override
		public boolean beforeExecute(TradeList first, TradeList second)
		{
			final TradeList firstList = first.getOwner() == _first ? first : second.getOwner() == _first ? second : null;
			final TradeList secondList = first.getOwner() == _second ? first : second.getOwner() == _second ? second : null;
			final StoredOperation current = _reservations.find(_operation.operationId()).orElse(null);
			final List<Reservation> liveReservations = _reservations.findReservations(_operation.operationId());
			final boolean exact = _participants.exclusive() && (firstList != null) && (secondList != null) && (firstList.getPartner() == _second) && (secondList.getPartner() == _first) && firstList.isConfirmed() && secondList.isConfirmed() && exactTradeList(firstList, _spec.offeredLines(), _spec.offeredAdena()) && exactTradeList(secondList, _spec.requestedLines(), _spec.requestedAdena()) && (current != null) && (current.state() == PhantomEconomyOperation.State.DISPATCHING) && (current.kind() == PhantomEconomyOperation.Kind.DIRECT_TRADE) && current.intentHash().equals(_offer.contentHash()) && current.authorityHash().equals(directAuthority(_offer, _spec, _acceptedReservations)) && liveReservations.equals(_acceptedReservations) && reservationsMatchRuntime(_acceptedReservations);
			if (!exact)
			{
				_preEffectReason = "trade.accepted_spec_mismatch";
				return false;
			}
			final Set<Integer> ids = new HashSet<>();
			_acceptedReservations.stream().filter(reservation -> reservation.kind() == ResourceKind.ITEM_OBJECT).map(Reservation::itemId).forEach(ids::add);
			_before = Conservation.capture(_first, _second, ids);
			final var transitioned = _reservations.transition(_operation.operationId(), PhantomEconomyOperation.State.DISPATCHING, PhantomEconomyOperation.State.OBSERVING, _now, null);
			final boolean ready = (transitioned.status() == PhantomEconomyReservationService.Status.TRANSITIONED) || (transitioned.status() == PhantomEconomyReservationService.Status.IDEMPOTENT);
			if (ready)
			{
				try
				{
					_faults.inject(FaultPoint.AFTER_OBSERVING);
				}
				catch (RuntimeException exception)
				{
					_preEffectReason = "trade.fault_after_observing";
					return false;
				}
			}
			else
			{
				_preEffectReason = "trade.observe.conflict";
			}
			return ready;
		}

		@Override
		public void afterTransfer(int ownerObjectId, int receiverObjectId, int objectId, int itemId, long count)
		{
			_actualTransfers.add(transferKey(ownerObjectId, receiverObjectId, objectId, itemId, count));
			if ((itemId == Inventory.ADENA_ID) && !_firstAdenaObserved)
			{
				_firstAdenaObserved = true;
				_faults.inject(FaultPoint.AFTER_FIRST_ADENA_MUTATION);
			}
			else if ((itemId != Inventory.ADENA_ID) && !_firstItemObserved)
			{
				_firstItemObserved = true;
				_faults.inject(FaultPoint.AFTER_FIRST_ITEM_TRANSFER);
			}
			_faults.inject(FaultPoint.AFTER_EACH_TRANSFER_LINE);
		}

		@Override
		public void afterExecute(TradeList first, TradeList second, boolean successful)
		{
			final Conservation after = Conservation.capture(_first, _second, _before == null ? Set.of() : _before.itemCounts().keySet());
			final Map<Integer, Long> firstItemDeltas = new TreeMap<>();
			_spec.offeredLines().forEach(line -> firstItemDeltas.merge(acceptedItemId(line.objectId()), -line.count(), Math::addExact));
			_spec.requestedLines().forEach(line -> firstItemDeltas.merge(acceptedItemId(line.objectId()), line.count(), Math::addExact));
			final List<String> expectedTransfers = new ArrayList<>();
			_spec.offeredLines().forEach(line -> expectedTransfers.add(transferKey(_first.getObjectId(), _second.getObjectId(), line.objectId(), acceptedItemId(line.objectId()), line.count())));
			_spec.requestedLines().forEach(line -> expectedTransfers.add(transferKey(_second.getObjectId(), _first.getObjectId(), line.objectId(), acceptedItemId(line.objectId()), line.count())));
			addExpectedAdenaTransfer(expectedTransfers, first, second, _first, _second, _spec.offeredAdena());
			addExpectedAdenaTransfer(expectedTransfers, first, second, _second, _first, _spec.requestedAdena());
			expectedTransfers.sort(String::compareTo);
			_actualTransfers.sort(String::compareTo);
			final long firstAdenaDelta = Math.subtractExact(_spec.requestedAdena(), _spec.offeredAdena());
			final boolean exact = (_before != null) && _before.exactTransfer(after, firstAdenaDelta, firstItemDeltas) && expectedTransfers.equals(_actualTransfers) && (_first.getActiveTradeList() == null) && (_second.getActiveTradeList() == null) && !_first.isProcessingTransaction() && !_second.isProcessingTransaction();
			terminal(_operation, _offer, _goal, successful && exact ? PhantomEconomyOperation.State.COMMITTED : PhantomEconomyOperation.State.INCONSISTENT, successful && exact ? Result.SUCCESS : Result.INCONSISTENT, successful && exact ? "result.success" : "dispatch.ambiguous", 0, 0, Math.abs(firstAdenaDelta), _now);
		}

		private int acceptedItemId(int objectId)
		{
			return _acceptedReservations.stream().filter(reservation -> (reservation.kind() == ResourceKind.ITEM_OBJECT) && (reservation.objectId() == objectId)).mapToInt(Reservation::itemId).findFirst().orElse(0);
		}

		@Override
		public void cancel(String reason)
		{
			final StoredOperation current = _reservations.find(_operation.operationId()).orElse(null);
			if ((current != null) && !current.state().terminal())
			{
				final boolean beforeEffect = _before == null;
				terminal(_operation, _offer, _goal, beforeEffect ? PhantomEconomyOperation.State.ABORTED : PhantomEconomyOperation.State.INCONSISTENT, beforeEffect ? Result.ERROR : Result.INCONSISTENT, beforeEffect ? _preEffectReason : reason, 0, 0, 0, _now);
			}
		}
	}

	private final class StoreObserver implements PrivateStoreService.Observer
	{
		private final StoredOperation _operation;
		private final PhantomEconomyOffer _offer;
		private final PhantomGoal _goal;
		private final Conservation _before;
		private final Map<Integer, Long> _firstItemDeltas;
		private final long _firstAdenaDelta;
		private final PrivateStoreService.Direction _direction;
		private final String _listingHash;
		private final String _requestHash;
		private final boolean _packageExpected;
		private final List<Reservation> _acceptedReservations;
		private final long _now;

		private StoreObserver(StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, Conservation before, Map<Integer, Long> firstItemDeltas, long firstAdenaDelta, PrivateStoreService.Direction direction, String listingHash, String requestHash, boolean packageExpected, List<Reservation> acceptedReservations, long now)
		{
			_operation = operation;
			_offer = offer;
			_goal = goal;
			_before = before;
			_firstItemDeltas = Map.copyOf(firstItemDeltas);
			_firstAdenaDelta = firstAdenaDelta;
			_direction = direction;
			_listingHash = listingHash;
			_requestHash = requestHash;
			_packageExpected = packageExpected;
			_acceptedReservations = List.copyOf(acceptedReservations);
			_now = now;
		}

		@Override
		public boolean beforeMutation(PrivateStoreService.Direction direction, Player actor, Player owner, TradeList list, String listingHash)
		{
			return false;
		}

		@Override
		public boolean beforeMutation(PrivateStoreService.Direction direction, Player actor, Player owner, TradeList list, String listingHash, String requestHash, TradeList.MutationMode mode)
		{
			final StoredOperation current = _reservations.find(_operation.operationId()).orElse(null);
			final PrivateStoreType expectedType = direction == PrivateStoreService.Direction.BUY_FROM_SELL_STORE ? _packageExpected ? PrivateStoreType.PACKAGE_SELL : PrivateStoreType.SELL : PrivateStoreType.BUY;
			final List<Reservation> liveReservations = _reservations.findReservations(_operation.operationId());
			final boolean exact = (direction == _direction) && (actor.getObjectId() == _offer.initiatingCharacterObjectId()) && (owner.getObjectId() == _offer.counterpartyCharacterObjectId()) && (list.getOwner() == owner) && (owner.getPrivateStoreType() == expectedType) && (list.isPackaged() == _packageExpected) && _listingHash.equals(listingHash) && _requestHash.equals(requestHash) && (mode == TradeList.MutationMode.STRICT_EXACT_OBJECT) && (current != null) && (current.state() == PhantomEconomyOperation.State.DISPATCHING) && current.intentHash().equals(_offer.contentHash()) && current.authorityHash().equals(_operation.authorityHash()) && liveReservations.equals(_acceptedReservations) && reservationsMatchRuntime(_acceptedReservations);
			if (!exact)
			{
				terminal(_operation, _offer, _goal, PhantomEconomyOperation.State.ABORTED, Result.CONFLICT, "store.accepted_spec_mismatch", 0, 0, 0, _now);
				return false;
			}
			final var transitioned = _reservations.transition(_operation.operationId(), PhantomEconomyOperation.State.DISPATCHING, PhantomEconomyOperation.State.OBSERVING, _now, null);
			final boolean ready = (transitioned.status() == PhantomEconomyReservationService.Status.TRANSITIONED) || (transitioned.status() == PhantomEconomyReservationService.Status.IDEMPOTENT);
			if (ready)
			{
				_faults.inject(FaultPoint.AFTER_OBSERVING);
			}
			return ready;
		}

		@Override
		public void afterMutation(PrivateStoreService.Direction direction, Player actor, Player owner, TradeList list, String beforeListingHash, String afterListingHash, boolean successful)
		{
			final Conservation after = Conservation.capture(actor, owner, _before.itemCounts().keySet());
			try
			{
				if (_before.firstAdena() != after.firstAdena())
				{
					_faults.inject(FaultPoint.AFTER_FIRST_ADENA_MUTATION);
				}
				if (!_before.firstItemCounts().equals(after.firstItemCounts()))
				{
					_faults.inject(FaultPoint.AFTER_FIRST_ITEM_TRANSFER);
					_faults.inject(FaultPoint.AFTER_EACH_TRANSFER_LINE);
				}
			}
			catch (RuntimeException exception)
			{
				terminal(_operation, _offer, _goal, PhantomEconomyOperation.State.INCONSISTENT, Result.INCONSISTENT, "dispatch.fault_after_effect", 0, 0, Math.abs(after.firstAdena() - _before.firstAdena()), _now);
				throw exception;
			}
			final boolean exact = _before.exactTransfer(after, _firstAdenaDelta, _firstItemDeltas);
			terminal(_operation, _offer, _goal, successful && exact ? PhantomEconomyOperation.State.COMMITTED : PhantomEconomyOperation.State.INCONSISTENT, successful && exact ? Result.SUCCESS : Result.INCONSISTENT, successful && exact ? "result.success" : "dispatch.ambiguous", 0, 0, Math.abs(after.firstAdena() - _before.firstAdena()), _now);
		}
	}

	private final class ManufactureObserver implements RecipeCraftObserver, AutoCloseable
	{
		private final StoredOperation _operation;
		private final PhantomEconomyOffer _offer;
		private final PhantomGoal _goal;
		private final Player _customer;
		private final Player _manufacturer;
		private final Manufacture _spec;
		private final Conservation _before;
		private final long _now;
		private final List<Reservation> _acceptedReservations;
		private final ParticipantLeases _participants;
		private final long _manufacturerExpBefore;
		private final long _manufacturerSpBefore;
		private final double _manufacturerHpBefore;
		private final double _manufacturerMpBefore;
		private final long _customerExpBefore;
		private final long _customerSpBefore;
		private boolean _accepted;
		private long _fee;
		private long _consumed;
		private Map<Integer, Long> _consumedItems = Map.of();
		private boolean _tainted;
		private String _firstTaintReason = "";
		private boolean _terminal;

		private ManufactureObserver(StoredOperation operation, PhantomEconomyOffer offer, PhantomGoal goal, Player customer, Player manufacturer, Manufacture spec, Conservation before, List<Reservation> acceptedReservations, ParticipantLeases participants, long now)
		{
			_operation = operation;
			_offer = offer;
			_goal = goal;
			_customer = customer;
			_manufacturer = manufacturer;
			_spec = spec;
			_before = before;
			_now = now;
			_acceptedReservations = List.copyOf(acceptedReservations);
			_participants = participants;
			_manufacturerExpBefore = manufacturer.getExp();
			_manufacturerSpBefore = manufacturer.getSp();
			_manufacturerHpBefore = manufacturer.getCurrentHp();
			_manufacturerMpBefore = manufacturer.getCurrentMp();
			_customerExpBefore = customer.getExp();
			_customerSpBefore = customer.getSp();
		}

		@Override
		public synchronized void onEvent(RecipeCraftObserver.Event event)
		{
			if (_terminal)
			{
				return;
			}
			if (!manufactureAuthorityMatches(event))
			{
				taint("manufacture.observer.authority");
				return;
			}
			if ((event.crafterObjectId() != _manufacturer.getObjectId()) || (event.targetObjectId() != _customer.getObjectId()) || (event.recipeListId() != _spec.recipeListId()))
			{
				taint("manufacture.observer.identity");
				return;
			}
			if (!_accepted)
			{
				final StoredOperation current = _reservations.find(_operation.operationId()).orElse(null);
				final boolean exactAcceptance = (event.type() == RecipeCraftObserver.Type.ACCEPTED) && event.items().isEmpty() && (event.feeTransferred() == 0) && (event.expConsequence() == 0) && (event.spConsequence() == 0) && (current != null) && (current.state() == PhantomEconomyOperation.State.OBSERVING) && current.intentHash().equals(_offer.contentHash()) && current.authorityHash().equals(_operation.authorityHash()) && _reservations.findReservations(_operation.operationId()).equals(_acceptedReservations) && reservationsMatchRuntime(_acceptedReservations) && _participants.exclusive();
				if (!exactAcceptance)
				{
					taint("manufacture.accepted_spec_mismatch");
					return;
				}
				_accepted = true;
				return;
			}
			if (event.type() == RecipeCraftObserver.Type.FEE_TRANSFERRED)
			{
				if ((_fee != 0) || (event.feeTransferred() != _spec.listingPrice()) || (event.crafterAdenaDelta() != _spec.listingPrice()) || (event.targetAdenaDelta() != -_spec.listingPrice()) || !event.items().isEmpty())
				{
					taint("manufacture.fee_evidence");
					return;
				}
				_fee = Math.addExact(_fee, event.feeTransferred());
				injectAfterEffect(FaultPoint.AFTER_FIRST_ADENA_MUTATION, "manufacture.fault_after_fee");
			}
			else if (event.type() == RecipeCraftObserver.Type.INGREDIENTS_CONSUMED)
			{
				final RecipeList currentRecipe = RecipeData.getInstance().getRecipeList(_spec.recipeListId());
				if ((currentRecipe == null) || !eventItems(event).equals(manufactureIngredientCounts(currentRecipe)) || (event.feeTransferred() != 0))
				{
					taint("manufacture.ingredient_evidence");
					return;
				}
				_consumedItems = eventItems(event);
				_consumed = _consumedItems.values().stream().mapToLong(Long::longValue).sum();
				injectAfterEffect(FaultPoint.AFTER_RECIPE_INGREDIENTS, "manufacture.fault_after_ingredients");
			}
			else if ((event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT) || (event.type() == RecipeCraftObserver.Type.RARE_PRODUCT) || (event.type() == RecipeCraftObserver.Type.CRAFT_FAILED))
			{
				_terminal = true;
				injectAfterEffect(FaultPoint.AFTER_PRODUCT_OR_FAILURE, "manufacture.fault_after_result");
				final Conservation after = Conservation.capture(_customer, _manufacturer, _before.itemCounts().keySet());
				final long produced = event.items().stream().mapToLong(RecipeCraftObserver.ItemDelta::count).sum();
				final RecipeList recipe = RecipeData.getInstance().getRecipeList(_spec.recipeListId());
				final boolean exactFee = _fee == _spec.listingPrice();
				final boolean conservedAdena = (_before.firstAdena() + _before.secondAdena()) == (after.firstAdena() + after.secondAdena());
				final Map<Integer, Long> expectedIngredients = recipe == null ? Map.of() : manufactureIngredientCounts(recipe);
				final Map<Integer, Long> expectedProducts = new TreeMap<>();
				if ((recipe != null) && (event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT))
				{
					expectedProducts.put(recipe.getItemId(), (long) recipe.getCount());
				}
				else if ((recipe != null) && (event.type() == RecipeCraftObserver.Type.RARE_PRODUCT))
				{
					expectedProducts.put(recipe.getRareItemId(), (long) recipe.getRareCount());
				}
				final Map<Integer, Long> expectedInventoryDeltas = new TreeMap<>();
				expectedIngredients.forEach((itemId, count) -> expectedInventoryDeltas.merge(itemId, -count, Math::addExact));
				expectedProducts.forEach((itemId, count) -> expectedInventoryDeltas.merge(itemId, count, Math::addExact));
				final boolean exactIngredients = _consumedItems.equals(expectedIngredients);
				final boolean exactProduct = eventItems(event).equals(expectedProducts);
				final boolean exactInventory = _before.exactDeltas(after, expectedInventoryDeltas);
				final boolean exactProgress = (event.expConsequence() == (_manufacturer.getExp() - _manufacturerExpBefore)) && (event.spConsequence() == (_manufacturer.getSp() - _manufacturerSpBefore)) && (_customer.getExp() == _customerExpBefore) && (_customer.getSp() == _customerSpBefore);
				final boolean exactVitals = (Double.compare(_manufacturer.getCurrentHp(), _manufacturerHpBefore - event.hpConsumed()) == 0) && (Double.compare(_manufacturer.getCurrentMp(), _manufacturerMpBefore - event.mpConsumed()) == 0);
				final boolean exact = !_tainted && (recipe != null) && exactFee && conservedAdena && exactIngredients && exactProduct && exactInventory && exactProgress && exactVitals;
				final String reason = exact ? event.type() == RecipeCraftObserver.Type.CRAFT_FAILED ? "result.craft_failed" : "result.success" : _tainted ? _firstTaintReason : "dispatch.ambiguous";
				terminal(_operation, _offer, _goal, exact ? PhantomEconomyOperation.State.COMMITTED : PhantomEconomyOperation.State.INCONSISTENT, exact ? event.type() == RecipeCraftObserver.Type.CRAFT_FAILED ? Result.CRAFT_FAILED : Result.SUCCESS : Result.INCONSISTENT, reason, _consumed, produced, _fee, _now);
			}
			else if (event.type() == RecipeCraftObserver.Type.ABORTED)
			{
				_terminal = true;
				final Conservation after = Conservation.capture(_customer, _manufacturer, _before.itemCounts().keySet());
				final boolean exactAbort = !_tainted && unchangedSinceBefore(after);
				terminal(_operation, _offer, _goal, exactAbort ? PhantomEconomyOperation.State.ABORTED : PhantomEconomyOperation.State.INCONSISTENT, exactAbort ? Result.ERROR : Result.INCONSISTENT, exactAbort ? "manufacture.pre_effect_aborted" : _tainted ? _firstTaintReason : "dispatch.ambiguous", _consumed, 0, _fee, _now);
			}
			else
			{
				taint("manufacture.observer.sequence");
			}
		}

		private boolean manufactureAuthorityMatches(RecipeCraftObserver.Event event)
		{
			final RecipeList recipe = RecipeData.getInstance().getRecipeList(_spec.recipeListId());
			final ManufactureItem listing = _manufacturer.getManufactureItems().get(_spec.recipeListId());
			if ((recipe == null) || (listing == null))
			{
				return false;
			}
			final RecipeCraftObserver.Authority authority = event.authority();
			final Map<Integer, Long> ingredients = new TreeMap<>();
			authority.requiredIngredients().forEach(item -> ingredients.merge(item.itemId(), item.count(), Math::addExact));
			final int skillId = recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId();
			return (authority.recipeListId() == recipe.getId()) && (authority.recipeItemId() == recipe.getRecipeId()) && (authority.productItemId() == _spec.productItemId()) && (authority.productCount() == _spec.productCount()) && (authority.rareProductItemId() == recipe.getRareItemId()) && (authority.rareProductCount() == (recipe.getRareItemId() > 0 ? recipe.getRareCount() : 0)) && (authority.rarity() == recipe.getRarity()) && (authority.craftLevel() == recipe.getLevel()) && (authority.successRate() == recipe.getSuccessRate()) && (authority.dwarven() == recipe.isDwarvenRecipe()) && (authority.skillId() == skillId) && (authority.skillLevel() == _manufacturer.getSkillLevel(skillId)) && (authority.listingPrice() == _spec.listingPrice()) && (listing.getCost() == _spec.listingPrice()) && ingredients.equals(manufactureIngredientCounts(recipe));
		}

		private synchronized StepResult recover(long now)
		{
			if (_terminal)
			{
				return StepResult.success(_offer.offerId(), _operation.operationId(), "economy.social.manufacture.terminal");
			}
			if (RecipeManager.getInstance().isManufactureActive(_manufacturer.getObjectId()))
			{
				if (now <= _goal.deadlineEpochMillis())
				{
					return StepResult.retry("economy.social.manufacture.observing");
				}
				RecipeManager.getInstance().requestMakeItemAbort(_manufacturer);
				if (_terminal)
				{
					return StepResult.success(_offer.offerId(), _operation.operationId(), "economy.social.manufacture.timeout");
				}
				if (RecipeManager.getInstance().isManufactureActive(_manufacturer.getObjectId()))
				{
					return StepResult.retry("economy.social.manufacture.abort.retry");
				}
			}
			final Conservation after = Conservation.capture(_customer, _manufacturer, _before.itemCounts().keySet());
			final boolean unchanged = unchangedSinceBefore(after);
			final boolean exactAbort = !_tainted && unchanged;
			_terminal = true;
			terminal(_operation, _offer, _goal, exactAbort ? PhantomEconomyOperation.State.ABORTED : PhantomEconomyOperation.State.INCONSISTENT, exactAbort ? Result.ERROR : Result.INCONSISTENT, exactAbort ? "manufacture.callback_missing_before_effect" : _tainted ? _firstTaintReason : "manufacture.callback_missing_after_effect", _consumed, 0, _fee, now);
			return StepResult.success(_offer.offerId(), _operation.operationId(), "economy.social.manufacture.reconciled");
		}

		private synchronized StepResult cancel(long now)
		{
			if (_terminal)
			{
				return StepResult.success(_offer.offerId(), _operation.operationId(), "economy.social.manufacture.terminal");
			}
			if (RecipeManager.getInstance().isManufactureActive(_manufacturer.getObjectId()))
			{
				RecipeManager.getInstance().requestMakeItemAbort(_manufacturer);
			}
			if (_terminal)
			{
				return StepResult.success(_offer.offerId(), _operation.operationId(), "economy.social.manufacture.cancelled");
			}
			if (RecipeManager.getInstance().isManufactureActive(_manufacturer.getObjectId()))
			{
				return StepResult.retry("economy.social.manufacture.abort.retry");
			}
			return recover(now);
		}

		private boolean unchangedSinceBefore(Conservation after)
		{
			return _before.equals(after) && (_manufacturer.getExp() == _manufacturerExpBefore) && (_manufacturer.getSp() == _manufacturerSpBefore) && (Double.compare(_manufacturer.getCurrentHp(), _manufacturerHpBefore) == 0) && (Double.compare(_manufacturer.getCurrentMp(), _manufacturerMpBefore) == 0) && (_customer.getExp() == _customerExpBefore) && (_customer.getSp() == _customerSpBefore);
		}

		private void taint(String reason)
		{
			if (!_tainted)
			{
				_tainted = true;
				_firstTaintReason = reason;
			}
		}

		@Override
		public void close()
		{
			_participants.release();
		}

		private void injectAfterEffect(FaultPoint point, String reason)
		{
			try
			{
				_faults.inject(point);
			}
			catch (RuntimeException exception)
			{
				taint(reason);
			}
		}
	}

	private final class ParticipantLeases implements AutoCloseable
	{
		private final TreeMap<Long, ActionLease> _leases = new TreeMap<>();
		private boolean _retained;
		private boolean _released;

		private void add(long profileId, ActionLease lease)
		{
			_leases.put(profileId, lease);
		}

		private Player player(long profileId)
		{
			final ActionLease lease = _leases.get(profileId);
			return lease == null ? null : lease.player();
		}

		private boolean exclusive()
		{
			for (Map.Entry<Long, ActionLease> entry : _leases.entrySet())
			{
				final var snapshot = _materialization.find(entry.getKey()).orElse(null);
				if ((snapshot == null) || (snapshot.admittedActionCount() != 1) || (snapshot.characterObjectId() != entry.getValue().player().getObjectId()))
				{
					return false;
				}
			}
			return !_leases.isEmpty();
		}

		private void retain()
		{
			_retained = true;
		}

		private synchronized void release()
		{
			if (_released)
			{
				return;
			}
			_released = true;
			final List<ActionLease> reverse = new ArrayList<>(_leases.descendingMap().values());
			_leases.clear();
			for (ActionLease lease : reverse)
			{
				lease.close();
			}
		}

		@Override
		public void close()
		{
			if (!_retained)
			{
				release();
			}
		}
	}

	private record Quote(String authorityHash, List<Reservation> reservations, byte[] beforePayload)
	{
		private Quote
		{
			reservations = List.copyOf(reservations);
			beforePayload = beforePayload.clone();
		}

		@Override
		public byte[] beforePayload()
		{
			return beforePayload.clone();
		}
	}

	private record Conservation(long firstAdena, long secondAdena, Map<Integer, Long> itemCounts, Map<Integer, Long> firstItemCounts)
	{
		private Conservation
		{
			itemCounts = Map.copyOf(itemCounts);
			firstItemCounts = Map.copyOf(firstItemCounts);
		}

		private static Conservation capture(Player first, Player second, Set<Integer> itemIds)
		{
			final Map<Integer, Long> counts = new TreeMap<>();
			final Map<Integer, Long> firstCounts = new TreeMap<>();
			for (int itemId : itemIds)
			{
				final long firstCount = first.getInventory().getInventoryItemCount(itemId, -1);
				firstCounts.put(itemId, firstCount);
				counts.put(itemId, Math.addExact(firstCount, second.getInventory().getInventoryItemCount(itemId, -1)));
			}
			return new Conservation(first.getAdena(), second.getAdena(), counts, firstCounts);
		}

		private boolean globallyConserved(Conservation after)
		{
			return (firstAdena + secondAdena) == (after.firstAdena + after.secondAdena) && itemCounts.equals(after.itemCounts);
		}

		private boolean exactTransfer(Conservation after, long firstAdenaDelta, Map<Integer, Long> firstItemDeltas)
		{
			if (!globallyConserved(after) || ((after.firstAdena - firstAdena) != firstAdenaDelta) || ((after.secondAdena - secondAdena) != -firstAdenaDelta))
			{
				return false;
			}
			for (Map.Entry<Integer, Long> entry : firstItemDeltas.entrySet())
			{
				if ((after.firstItemCounts.getOrDefault(entry.getKey(), 0L) - firstItemCounts.getOrDefault(entry.getKey(), 0L)) != entry.getValue())
				{
					return false;
				}
			}
			return true;
		}

		private boolean exactDeltas(Conservation after, Map<Integer, Long> expectedFirstItemDeltas)
		{
			final Set<Integer> itemIds = new HashSet<>(itemCounts.keySet());
			itemIds.addAll(after.itemCounts.keySet());
			itemIds.addAll(expectedFirstItemDeltas.keySet());
			for (int itemId : itemIds)
			{
				final long expected = expectedFirstItemDeltas.getOrDefault(itemId, 0L);
				if (((after.itemCounts.getOrDefault(itemId, 0L) - itemCounts.getOrDefault(itemId, 0L)) != expected) || ((after.firstItemCounts.getOrDefault(itemId, 0L) - firstItemCounts.getOrDefault(itemId, 0L)) != expected))
				{
					return false;
				}
			}
			return true;
		}
	}

	public enum StepStatus
	{
		SUCCESS,
		RETRY,
		REPLAN,
		ACTIVE_REQUIRED
	}

	public record StepResult(StepStatus status, String offerId, String operationId, String reason)
	{
		private static StepResult success(String offerId, String operationId, String reason)
		{
			return new StepResult(StepStatus.SUCCESS, offerId, operationId, reason);
		}

		private static StepResult retry(String reason)
		{
			return new StepResult(StepStatus.RETRY, "", "", reason);
		}

		private static StepResult replan(String reason)
		{
			return new StepResult(StepStatus.REPLAN, "", "", reason);
		}

		private static StepResult activeRequired(String reason)
		{
			return new StepResult(StepStatus.ACTIVE_REQUIRED, "", "", reason);
		}
	}

	public enum FaultPoint
	{
		AFTER_OFFER_ACCEPTED,
		AFTER_RESERVATIONS,
		AFTER_DISPATCHING,
		AFTER_OBSERVING,
		AFTER_FIRST_ADENA_MUTATION,
		AFTER_FIRST_ITEM_TRANSFER,
		AFTER_EACH_TRANSFER_LINE,
		AFTER_RECIPE_INGREDIENTS,
		AFTER_PRODUCT_OR_FAILURE,
		AFTER_GOAL_WRITE,
		AFTER_OPERATION_AUDIT
	}

	@FunctionalInterface
	public interface FaultInjector
	{
		void inject(FaultPoint point);

		static FaultInjector none()
		{
			return point ->
			{
			};
		}
	}

	public record ShutdownResult(boolean successful, List<String> pendingOperationIds)
	{
		public ShutdownResult
		{
			pendingOperationIds = List.copyOf(pendingOperationIds);
		}
	}

	public record Snapshot(long activeRequired, long offersAccepted, long committed, long inconsistent, int retainedObservers)
	{
	}
}
