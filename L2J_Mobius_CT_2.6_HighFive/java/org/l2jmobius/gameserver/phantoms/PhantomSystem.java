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

import java.io.File;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.localplay.LocalPlayM1Observation;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSinkBridge;
import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionDecision;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomLegacyHeadlessRecovery;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatStepHandlers;
import org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceDecision;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.clan.L2jPhantomClanBackend;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveCatalog;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveService;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanSocialLifecycleObserver;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDecision;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanStore;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationGoalRuntimePort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationPlanSink;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyDecision;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyMaterializationLifecycle;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyDecision;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingDecision;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPolicy;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingStore;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomPvpRetreatCoordinator;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyDecision;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyParticipationPort;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ShutdownResult;
import org.l2jmobius.gameserver.phantoms.raid.L2jPhantomRaidAttemptRuntime;
import org.l2jmobius.gameserver.phantoms.raid.L2jPhantomRaidAuthority;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidDecision;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterCatalog;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidReadinessService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidRecruitmentService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry;
import org.l2jmobius.gameserver.phantoms.rift.L2jPhantomRiftBackend;
import org.l2jmobius.gameserver.phantoms.rift.L2jPhantomRiftPartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftDecision;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPolicy;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftReadinessService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftStore;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpContext;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPolicy;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpService;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStore;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationDecision;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionStepHandlers;
import org.l2jmobius.gameserver.phantoms.questinstance.L2jPhantomQuestInstanceBackend;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceDecision;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.L2jPhantomSocialAffiliationContextResolver;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialAffiliationContextPort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.siege.L2jPhantomSiegeAuthority;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeCatalog;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeDecision;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeMovementCoordinator;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeService;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPositionPublisher;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/**
 * Lifecycle owner for the disabled-by-default Phantom World subsystem.
 */
public final class PhantomSystem
{
	public static final int TRACE_CAPACITY = 64;
	public static final int TRACE_SAMPLE_EVERY = 16;
	public static final long SOCIAL_PERSONALITY_SEED = 18001801L;

	private static PhantomSystem _configuredInstance;
	private static OperatorMode _operatorMode = OperatorMode.AUTO;
	private static PhantomDecisionReplay.Bundle _operatorReplayBundle;
	private static PhantomPopulationResetService _populationResetService;

	private final PhantomPlayersConfig.Settings _settings;
	private final PhantomMetrics _metrics;
	private PhantomScheduler _scheduler;
	private final PhantomDiagnosticTrace _trace;
	private final PhantomSelectedDecisionTrace _selectedDecisionTrace;
	private final boolean _productionMaterialization;
	private boolean _shutdownFailureForTesting;
	private PhantomMaterializationService _materializationService;
	private PhantomMaterializationServiceActivityPort _materializationActivity;
	private PhantomReconcileFirstActivityPort _reconcileMaterializationActivity;
	private PhantomDecisionEngine _decisionEngine;
	private PhantomNavigationService _navigationService;
	private PhantomTopologyService _topologyService;
	private PhantomHumanLocalityControl _humanLocality;
	private PhantomMaterializationRetentionPolicy _materializationRetention;
	private long _nextRetentionRefreshNanos;
	private PhantomGameKnowledgeService _gameKnowledgeService;
	private PhantomSemanticUnderstandingService _semanticUnderstandingService;
	private PhantomProgressionService _progressionService;
	private PhantomCombatService _combatService;
	private PhantomCommerceService _commerceService;
	private PhantomCommerceReceiptStore _commerceReceiptStore;
	private PhantomEconomicAuditView _economicAuditView;
	private PhantomEconomyReservationService _economyReservations;
	private PhantomEconomyService _economyService;
	private PhantomEconomyOfferService _economyOffers;
	private PhantomMultipartyEconomyService _multipartyEconomyService;
	private PhantomStoreService _phantomStoreService;
	private PhantomAutonomousMarketProducer _autonomousMarketProducer;
	private PhantomBackgroundService _backgroundService;
	private PhantomVisibleAutoPlay _visibleAutoPlay;
	private PhantomVisibleFarmTravel _visibleFarmTravel;
	private PhantomHistoricalBackgroundService _historicalBackgroundService;
	private PhantomAcquisitionService _acquisitionService;
	private PhantomFarmingService _farmingService;
	private PhantomPopulationManager _populationManager;
	private PhantomPopulationEcologyService _populationEcology;
	private PhantomPartyCoordinator _partyCoordinator;
	private PhantomClanService _clanService;
	private PhantomClanSocialLifecycleObserver _clanSocialLifecycleObserver;
	private PhantomOrdinaryDeathRecovery _ordinaryDeathRecovery;
	private PhantomClanDirectiveService _clanDirectiveService;
	private PhantomRaidReadinessService _raidReadinessService;
	private PhantomRaidRecruitmentService _raidRecruitmentService;
	private PhantomRaidAssemblyService _raidAssemblyService;
	private PhantomRaidAttemptService _raidAttemptService;
	private PhantomSiegeService _siegeService;
	private PhantomQuestInstanceService _questInstanceService;
	private PhantomSocialService _socialService;
	private PhantomConversationService _conversationService;
	private PhantomConversationExecutionService _conversationExecutionService;
	private PhantomPvpService _pvpService;
	private State _state = State.NEW;

	public PhantomSystem(PhantomPlayersConfig.Settings settings)
	{
		this(settings, false);
	}

	private PhantomSystem(PhantomPlayersConfig.Settings settings, boolean productionMaterialization)
	{
		_settings = Objects.requireNonNull(settings);
		_productionMaterialization = productionMaterialization;
		_metrics = new PhantomMetrics();
		_selectedDecisionTrace = new PhantomSelectedDecisionTrace(settings.diagnosticsEnabled(), TRACE_CAPACITY);
		if (settings.enabled())
		{
			_trace = new PhantomDiagnosticTrace(settings.diagnosticsEnabled(), TRACE_CAPACITY, TRACE_SAMPLE_EVERY, _metrics);
		}
		else
		{
			_scheduler = null;
			_trace = null;
		}
	}

	public synchronized boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}

		if (!_settings.enabled())
		{
			_state = State.DISABLED;
			return false;
		}

		try
		{
			PhantomProfileRepository profileRepository = null;
			PhantomGoalStateStore goalStateStore = null;
			PhantomCombatPolicy combatPolicy;
			PhantomActivityWorkSinkBridge workSinkBridge = null;
			PhantomMaterializationLifecycleBridge lifecycleBridge = null;
			PhantomMaterializationLifecycleBridge pvpLifecycleBridge = null;
			PhantomSocialCatalog socialCatalog = null;
			if (_productionMaterialization)
			{
				profileRepository = PhantomProfileRepository.open();
				final File socialCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/social/high-five-social-v1.xml");
				socialCatalog = PhantomSocialCatalog.load(socialCatalogFile.toPath());
				_socialService = new PhantomSocialService(socialCatalog, new PhantomSocialStore(profileRepository, socialCatalog), SOCIAL_PERSONALITY_SEED, _settings.socialCacheProfiles(), () -> System.currentTimeMillis() / 60000L);
				if (!_socialService.start())
				{
					throw new IllegalStateException("Phantom social service could not enter the running state.");
				}
				goalStateStore = new PhantomGoalStateStore(profileRepository);
				lifecycleBridge = new PhantomMaterializationLifecycleBridge();
				_materializationService = new PhantomMaterializationService(profileRepository, PhantomIdentityLeaseRegistry.getInstance(), _metrics, _trace, _settings.maxMaterializedPhantoms(), lifecycleBridge);
				if (!_materializationService.start())
				{
					throw new IllegalStateException("Phantom materialization service could not enter the running state.");
				}
				workSinkBridge = new PhantomActivityWorkSinkBridge();
				_materializationActivity = new PhantomMaterializationServiceActivityPort(_materializationService, _settings.diagnosticsEnabled());
				_reconcileMaterializationActivity = new PhantomReconcileFirstActivityPort(_materializationActivity);
				_scheduler = createScheduler(_reconcileMaterializationActivity, workSinkBridge);
				combatPolicy = PhantomCombatPolicy.productionDefaults(_settings.maxScheduledPhantomProfiles());
			}
			else
			{
				_scheduler = createScheduler(PhantomActivityMaterializationPort.noop());
				combatPolicy = PhantomCombatPolicy.productionDefaults(_settings.maxScheduledPhantomProfiles());
				_combatService = new PhantomCombatService(PhantomCombatBackend.inert(), new PhantomCombatCapabilityResolver(_ -> java.util.List.of()), combatPolicy);
				_combatService.start();
			}
			_navigationService = new PhantomNavigationService(_metrics);
			if (!_navigationService.start())
			{
				throw new IllegalStateException("Phantom navigation service could not enter the running state.");
			}
			if (_productionMaterialization)
			{
				final PhantomTopologyPolicy topologyPolicy = PhantomTopologyPolicy.productionDefaults().withMaximumRegisteredProfiles(_settings.maxScheduledPhantomProfiles());
				final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
				final File topologyDirectory = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/topology");
				_topologyService = new PhantomTopologyService(new PhantomTopologyLoader(topologyDirectory.toPath(), topologyBackend, topologyPolicy), topologyBackend, topologyPolicy, new PhantomSchedulerRelevanceSignalPort(_scheduler));
			}
			else
			{
				_topologyService = PhantomTopologyService.inertForTesting(new PhantomSchedulerRelevanceSignalPort(_scheduler), _settings.maxScheduledPhantomProfiles());
			}
			if (!_topologyService.start())
			{
				throw new IllegalStateException("Phantom topology service could not enter the running state.");
			}
			if (_productionMaterialization)
			{
				final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
				final L2jGameKnowledgeBackend knowledgeBackend = new L2jGameKnowledgeBackend();
				final File knowledgeDirectory = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/knowledge");
				final File seedsFile = new File(ServerConfig.DATAPACK_ROOT, "data/Seeds.xml");
				_gameKnowledgeService = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(seedsFile.toPath(), knowledgePolicy), new PhantomCuratedKnowledgeParser(knowledgeDirectory.toPath(), knowledgeBackend, knowledgePolicy), _topologyService.query(), knowledgePolicy));
			}
			else
			{
				_gameKnowledgeService = PhantomGameKnowledgeService.inertForTesting(_topologyService.query().snapshot().canonicalHash());
			}
			if (!_gameKnowledgeService.start())
			{
				throw new IllegalStateException("Phantom Game Knowledge service could not enter the running state.");
			}
			if (_productionMaterialization)
			{
				final PhantomProfileRepository productionProfiles = Objects.requireNonNull(profileRepository);
				final PhantomGoalStateStore productionGoals = Objects.requireNonNull(goalStateStore);
				final PhantomAcquisitionStore acquisitionStore = new PhantomAcquisitionStore(productionProfiles, productionGoals);
				final PhantomMaterializationLifecycleBridge productionLifecycle = Objects.requireNonNull(lifecycleBridge);
				final PhantomActivityWorkSinkBridge productionWorkSink = Objects.requireNonNull(workSinkBridge);
				_progressionService = new PhantomProgressionService(new L2jProgressionBackend(_materializationService, ServerConfig.DATAPACK_ROOT.toPath(), _gameKnowledgeService::query), PhantomProgressionPolicy.productionDefaults());
				_progressionService.start();
				final L2jCombatBackend combatBackend = new L2jCombatBackend(_materializationService, _gameKnowledgeService::query, () -> _progressionService.findCatalog().orElse(null));
				_combatService = new PhantomCombatService(combatBackend, PhantomCombatCapabilityResolver.fromProgression(() -> _progressionService.findCatalog().orElse(null)), combatPolicy);
				_combatService.start();
				final File economyPolicyFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/economy/high-five-economy-v1.xml");
				final PhantomEconomyPolicy economyPolicy = PhantomEconomyPolicy.load(economyPolicyFile.toPath());
				_economyReservations = new PhantomEconomyReservationService(economyPolicy);
				if (!_economyReservations.start())
				{
					throw new IllegalStateException("Phantom economy reservation service could not enter the running state.");
				}
				PhantomEconomyConflictPort.install(_economyReservations);
				_economyOffers = new PhantomEconomyOfferService();
				_economyService = new PhantomEconomyService(economyPolicy, _economyReservations, new PhantomEconomyBackgroundTransaction(_economyReservations, economyPolicy), _materializationService, acquisitionStore, productionGoals, productionProfiles);
				_multipartyEconomyService = new PhantomMultipartyEconomyService(economyPolicy, _economyReservations, _economyOffers, _materializationService, productionGoals, productionProfiles);
				_multipartyEconomyService.reconcileStartup(System.currentTimeMillis());
				_phantomStoreService = new PhantomStoreService(productionProfiles, _materializationService, () -> new PhantomPrivateMarketPricingAuthority(_gameKnowledgeService.query().snapshot(), _commerceService.catalog(), PhantomRateAwareLootFairValue.Rates.capture(), PhantomMarketConfig.policy().orElseThrow(), World.getInstance().getVisibleObjects()));
				PhantomMarketConfig.autonomousPolicy().ifPresent(policy -> _autonomousMarketProducer = new PhantomAutonomousMarketProducer(_materializationService, _scheduler, _phantomStoreService, productionGoals, acquisitionStore, _economyReservations, () -> _decisionEngine, policy));
				final PhantomCommerceCatalogLoader.LoadResult commerceCatalog = new PhantomCommerceCatalogLoader(ServerConfig.DATAPACK_ROOT.toPath()).load();
				_commerceReceiptStore = new PhantomCommerceReceiptStore(productionProfiles);
				_commerceService = new PhantomCommerceService(commerceCatalog, _commerceReceiptStore, productionGoals, new L2jCommerceBackend(_materializationService, commerceCatalog.catalog(), Clock.systemDefaultZone()));
				_economicAuditView = new PhantomEconomicAuditView(_economyReservations, _commerceReceiptStore);
				if (!_commerceService.start())
				{
					throw new IllegalStateException("Phantom commerce service could not enter the running state.");
				}
				final PhantomPartyParticipationPort.Bridge partyParticipation = PhantomPartyParticipationPort.bridge();
				final PhantomNormalGatekeeperTravel travel = PhantomNormalGatekeeperTravel.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/travel/high-five-normal-gk.xml").toPath(), _topologyService.query());
				final L2jPhantomBackgroundAuthority backgroundAuthority = new L2jPhantomBackgroundAuthority(_gameKnowledgeService::query, _topologyService::query, _progressionService::catalog, _commerceService::catalog, travel);
				_backgroundService = new PhantomBackgroundService(
					productionProfiles,
					productionGoals,
					PhantomIdentityLeaseRegistry.getInstance(),
					new PhantomBackgroundTransaction(),
					backgroundAuthority,
					new PhantomBackgroundCompetitionRegistry(),
					new PhantomSchedulerRelevanceSignalPort(_scheduler),
					() -> _materializationService,
					partyParticipation);
				if (!_backgroundService.start())
				{
					throw new IllegalStateException("Phantom background service could not enter the running state.");
				}
				try
				{
					PhantomLegacyHeadlessRecovery.apply(new File(ServerConfig.DATAPACK_ROOT, PhantomLegacyHeadlessRecovery.RELATIVE_PATH).toPath(), _backgroundService);
					PhantomLegacyHeadlessRecovery.applyMaterialized(new File(ServerConfig.DATAPACK_ROOT, PhantomLegacyHeadlessRecovery.MATERIALIZED_RELATIVE_PATH).toPath(), _backgroundService);
				}
				catch (java.io.IOException failure)
				{
					throw new IllegalStateException("M1 legacy recovery witness file could not be read.", failure);
				}
				final PhantomTopologyPositionPublisher positionPublisher = new PhantomTopologyPositionPublisher(_topologyService, _backgroundService::acquisitionSnapshot);
				_backgroundService.installCommittedPositionPublisher(positionPublisher::committed);
				_humanLocality = new PhantomHumanLocalityControl(_topologyService, new PhantomSchedulerRelevanceSignalPort(_scheduler), PhantomSystem::onlineHumanPoints, System::currentTimeMillis, profileId -> (_populationManager != null) && _populationManager.presence().isOnline(profileId), this::liveMaterializedPoints);
				_historicalBackgroundService = new PhantomHistoricalBackgroundService(productionProfiles, productionGoals, new PhantomHistoricalBackgroundPlanner(_gameKnowledgeService.query(), _topologyService.query(), backgroundAuthority), _backgroundService, _materializationService);
				_visibleAutoPlay = new PhantomVisibleAutoPlay(_materializationService, () -> _decisionEngine, profileId -> ((_partyCoordinator == null) || !_partyCoordinator.blocksBackground(profileId)) && ((_phantomStoreService == null) || !_phantomStoreService.blocksDecision(profileId)));
				_visibleFarmTravel = new PhantomVisibleFarmTravel(_materializationService, _backgroundService, backgroundAuthority.travelQuery(_topologyService.query()), _navigationService, profileId -> ((_partyCoordinator == null) || !_partyCoordinator.blocksBackground(profileId)) && ((_phantomStoreService == null) || !_phantomStoreService.blocksDecision(profileId)), new PhantomSchedulerRelevanceSignalPort(_scheduler), (profileId, failure) ->
				{
					if (failure.routeFailure()) { _historicalBackgroundService.recordVisibleFailure(profileId, failure.goal(), failure.stepId()); }
				}, System::nanoTime);
				pvpLifecycleBridge = new PhantomMaterializationLifecycleBridge();
				productionLifecycle.install(PhantomMaterializationLifecyclePort.chain(_visibleFarmTravel, PhantomMaterializationLifecyclePort.chain(_visibleAutoPlay, PhantomMaterializationLifecyclePort.chain(positionPublisher, PhantomMaterializationLifecyclePort.chain(_historicalBackgroundService, PhantomMaterializationLifecyclePort.chain(PhantomMaterializationLifecyclePort.chain(new PhantomEconomyMaterializationLifecycle(_economyReservations, _economyOffers, Clock.systemUTC()), _backgroundService), pvpLifecycleBridge))))));
				final File acquisitionCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/acquisition/high-five-acquisition-v1.xml");
				final PhantomAcquisitionCatalog acquisitionCatalog = PhantomAcquisitionCatalog.load(acquisitionCatalogFile.toPath());
				final File questCollectionCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/acquisition/high-five-quest-collection-v1.xml");
				final PhantomAcquisitionQuestCatalog questCollectionCatalog = PhantomAcquisitionQuestCatalog.load(questCollectionCatalogFile.toPath(), new File(ServerConfig.DATAPACK_ROOT, "data/scripts").toPath());
				final PhantomAcquisitionManorAuthority manorAuthority = new PhantomAcquisitionManorAuthority(_gameKnowledgeService.query(), _topologyService.query(), new File(ServerConfig.DATAPACK_ROOT, "data/mapregion").toPath());
				if (!_backgroundService.installAcquisitionAuthorities(manorAuthority, questCollectionCatalog, acquisitionCatalog.limits()))
				{
					throw new IllegalStateException("Phantom background acquisition authorities could not be installed.");
				}
				_acquisitionService = new PhantomAcquisitionService(acquisitionCatalog, acquisitionStore, productionGoals, new PhantomAcquisitionSourcePlanner(acquisitionCatalog, _gameKnowledgeService.query(), _topologyService.query(), _progressionService.catalog(), manorAuthority, questCollectionCatalog), _gameKnowledgeService.query(), _topologyService.query(), _progressionService.catalog(), _combatService, _backgroundService, _navigationService, manorAuthority, questCollectionCatalog);
				if (!_acquisitionService.start())
				{
					throw new IllegalStateException("Phantom acquisition service could not enter the running state.");
				}
				final PhantomCommerceDecision commerceDecision = new PhantomCommerceDecision(_commerceService);
				final PhantomBackgroundDecision backgroundDecision = PhantomBackgroundDecision.bindVisibleLife(_backgroundService, _visibleFarmTravel, _visibleAutoPlay, _historicalBackgroundService, () -> _decisionEngine);
				_ordinaryDeathRecovery = new PhantomOrdinaryDeathRecovery(_materializationService, productionGoals, _backgroundService);
				if (!_ordinaryDeathRecovery.install())
				{
					throw new IllegalStateException("Ordinary Phantom death recovery could not be installed.");
				}
				final PhantomAcquisitionDecision acquisitionDecision = new PhantomAcquisitionDecision(_acquisitionService);
				final PhantomEconomyDecision economyDecision = new PhantomEconomyDecision(_economyService);
				final PhantomMultipartyEconomyDecision multipartyEconomyDecision = new PhantomMultipartyEconomyDecision(_multipartyEconomyService);
				final File populationCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/population/high-five-population-v2.xml");
				final File populationPredecessorFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/population/high-five-population-v1.xml");
				final PhantomPopulationCatalog populationCatalog = PhantomPopulationCatalog.load(populationCatalogFile.toPath(), _settings.populationTimeZone());
				final PhantomPopulationCatalog populationPredecessor = PhantomPopulationCatalog.load(populationPredecessorFile.toPath(), _settings.populationTimeZone());
				_populationManager = new PhantomPopulationManager(
					new PhantomPopulationStore(productionProfiles, populationCatalog, populationPredecessor, _settings.populationTimeZone()),
					populationCatalog,
					productionGoals,
					_scheduler,
					profileId -> _materializationService.find(profileId).isPresent(),
					Clock.systemUTC(),
					_settings.populationTimeZone(),
					_settings.populationTarget(),
					_settings.populationActiveTarget(),
					_settings.maxScheduledPhantomProfiles(),
					_settings.maxMaterializedPhantoms(),
					_settings.populationCreationInFlight(),
					_settings.populationBoundariesPerPulse());
				_populationManager.installTopologyMembership(profileId ->
				{
					positionPublisher.ready(profileId);
					resumeRecoveredBackgroundGoal(profileId, productionGoals);
				}, positionPublisher::retired);
				_backgroundService.installPresencePolicy(_populationManager.presence()::permitsOrdinaryFarm);
				final File partyRoleCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/party/high-five-party-roles-v1.xml");
				final PhantomPartyRoleCatalog partyRoleCatalog = PhantomPartyRoleCatalog.load(partyRoleCatalogFile.toPath());
				final File semanticPackFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/semantic/high-five-ru-semantic-v1.xml");
				final File semanticCorpusFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/semantic/high-five-ru-corpus-v1.tsv");
				_semanticUnderstandingService = PhantomSemanticUnderstandingService.production(semanticPackFile.toPath(), semanticCorpusFile.toPath(), PhantomSemanticGrounding.production(_gameKnowledgeService.query(), _topologyService.query(), partyRoleCatalog));
				if (!_semanticUnderstandingService.start())
				{
					throw new IllegalStateException("Phantom semantic understanding service could not enter the running state.");
				}
				final L2jPhantomPartyBackend partyBackend = new L2jPhantomPartyBackend(productionProfiles, _materializationService, _progressionService);
				final PhantomPartySupportPolicy partySupportPolicy = PhantomPartySupportPolicy.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/party/high-five-party-support-v1.xml").toPath());
				final L2jPhantomRaidAuthority raidAuthority = new L2jPhantomRaidAuthority();
				final PhantomRaidEncounterCatalog raidCatalog = new PhantomRaidEncounterCatalog();
				final PhantomRaidScriptRegistry raidScripts = PhantomRaidScriptRegistry.getInstance();
				_raidReadinessService = new PhantomRaidReadinessService(_gameKnowledgeService.query(), partyBackend, raidAuthority, raidCatalog, raidScripts);
				_raidRecruitmentService = new PhantomRaidRecruitmentService(_raidReadinessService, partyBackend);
				final PhantomPartyRouteCoordinator raidRoutes = new PhantomPartyRouteCoordinator(_navigationService, _combatService);
				_raidAssemblyService = new PhantomRaidAssemblyService(productionGoals, _raidReadinessService, _raidRecruitmentService, partyBackend, raidAuthority, _topologyService::query, raidRoutes, System::currentTimeMillis, System::nanoTime);
				final PhantomPartyTactics raidTactics = new PhantomPartyTactics(_combatService, partyBackend, partySupportPolicy);
				final L2jPhantomRaidAttemptRuntime raidRuntime = new L2jPhantomRaidAttemptRuntime(_combatService, raidTactics, raidRoutes, () -> _topologyService.query().snapshot().canonicalHash(), System::nanoTime);
				_raidAttemptService = new PhantomRaidAttemptService(productionGoals, _raidAssemblyService, _raidReadinessService, partyBackend, raidAuthority, raidCatalog, raidScripts, raidRuntime, System::currentTimeMillis, System::nanoTime, () -> NpcConfig.RAID_DISABLE_CURSE);
				final PhantomRaidDecision raidDecision = new PhantomRaidDecision(_raidAssemblyService, _raidAttemptService);
				final PhantomSocialAffiliationContextPort socialAffiliations = new L2jPhantomSocialAffiliationContextResolver(_materializationService);
				_partyCoordinator = new PhantomPartyCoordinator(
					new PhantomPartyStore(productionProfiles),
					productionGoals,
					partyBackend,
					partyRoleCatalog,
					new PhantomPartyRouteCoordinator(_navigationService, _combatService),
					new PhantomPartyTactics(_combatService, partyBackend, partySupportPolicy),
					() -> _topologyService.query().snapshot().canonicalHash(),
					System::nanoTime,
					_settings.partyOperationsPerPulse(),
					_socialService,
					() -> System.currentTimeMillis() / 60000L,
					socialAffiliations);
				if (!_partyCoordinator.start())
				{
					throw new IllegalStateException("Phantom party coordinator could not enter the running state.");
				}
				partyParticipation.install(_partyCoordinator);
				_populationManager.presence().installExternalBusySource("party", _partyCoordinator::blocksBackground);
				_populationManager.presence().installExternalBusySource("store", _phantomStoreService::blocksDecision);
				_populationManager.presence().installExternalBusySource("action", profileId -> _materializationService.find(profileId).map(materialization -> materialization.admittedActionCount() > 0).orElse(false));
				if (_settings.ecologyEnabled())
				{
					final File ecologyCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/population/high-five-ecology-v1.xml");
					final PhantomPopulationEcologyCatalog ecologyCatalog = PhantomPopulationEcologyCatalog.load(ecologyCatalogFile.toPath(), populationCatalog, Objects.requireNonNull(socialCatalog));
					_populationEcology = new PhantomPopulationEcologyService(
						ecologyCatalog,
						populationCatalog,
						new PhantomPopulationEcologyStore(productionProfiles),
						_historicalBackgroundService,
						profileId -> _materializationService.find(profileId).isPresent(),
						profileId -> ecologySafetyBlock(profileId, productionGoals),
						Clock.systemUTC(),
						_settings.populationTimeZone(),
						Preset.valueOf(_settings.ecologyPreset()),
						_settings.ecologyWorldAgeDays(),
						_settings.ecologyArchiveLimit());
					_populationEcology.enablePeriodicDueMode();
					_populationEcology.installDemandPump(_settings.schedulerPulseMillis(), () -> System.nanoTime() / 1_000_000L, (wake, delay) ->
					{
						final var future = org.l2jmobius.commons.threads.ThreadPool.schedule(wake, delay);
						if (future == null) { throw new java.util.concurrent.RejectedExecutionException("ecology.wake_rejected"); }
						return () -> future.cancel(false);
					});
					_populationManager.installEcology(_populationEcology);
					_socialService.installPersonalityInitializer(_populationEcology::initialPersonalityTraits);
				}
				final PhantomPopulationEcologyService periodicEcology = _populationEcology;
				if (periodicEcology != null)
				{
					periodicEcology.installMaterializationDemand(profileId -> _populationManager.presence().isOnline(profileId) && _humanLocality.isCurrentLocal(profileId));
					_humanLocality.installPreparationDemand(periodicEcology::updateMaterializationDemand, () ->
					{
						final var capacity = _materializationService.snapshot();
						final long soft = capacity.materializations().stream().filter(entry -> entry.worldPresent() && (_materializationRetention != null) && _materializationRetention.observe(entry.profileId()).reclaimable()).count();
						return Math.min(8, capacity.availablePermits() + Math.toIntExact(soft));
					});
				}
				_reconcileMaterializationActivity.installPopulationReadiness(_populationManager.presence(), _humanLocality, periodicEcology);
				_materializationRetention = new PhantomMaterializationRetentionPolicy(this::retentionFacts, System::nanoTime, 60_000);
				_populationManager.installRetirementProtection(profileId -> _materializationRetention.observe(profileId).hard() || ((_partyCoordinator != null) && (_partyCoordinator.committed(profileId) || _partyCoordinator.blocksBackground(profileId))) || ((_phantomStoreService != null) && _phantomStoreService.blocksDecision(profileId)) || ((_economyReservations != null) && _economyReservations.findActive(profileId).isPresent()));
				_reconcileMaterializationActivity.installRetention(profileId -> (_scheduler.snapshot().state() == PhantomScheduler.SchedulerState.RUNNING) && _materializationRetention.observe(profileId).retained());
				_reconcileMaterializationActivity.installSoftReclamation(requestingProfileId ->
				{
					if (!_humanLocality.isLocal(requestingProfileId))
					{
						return 0;
					}
					final var snapshot = _materializationService.snapshot();
					return snapshot.availablePermits() == 0 ? _materializationRetention.oldestSoft(snapshot.materializations().stream().filter(entry -> entry.worldPresent()).map(entry -> entry.profileId()).toList()) : 0;
				}, profileId -> _materializationRetention.observe(profileId).reclaimable());
				_backgroundService.installPeriodicFarm(profileId ->
				{
					if (periodicEcology == null)
					{
						return PhantomBackgroundService.OperationResult.retry("ecology.disabled");
					}
					final var due = periodicEcology.requestBackgroundDue(profileId);
					if (!due.complete())
					{
						return PhantomBackgroundService.OperationResult.retry(due.reason());
					}
					return due.advancedIntervals() > 0 ? new PhantomBackgroundService.OperationResult(PhantomBackgroundService.OperationStatus.SUCCESS, due.reason(), 0, due.advancedIntervals() * 60_000L, false) : PhantomBackgroundService.OperationResult.idempotent(due.reason());
				});
				final PhantomPvpPolicy pvpPolicy = PhantomPvpPolicy.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/pvp/pvp-policy-v1.xml").toPath());
				_clanService = new PhantomClanService(productionGoals, new PhantomClanStore(productionProfiles), new L2jPhantomClanBackend(productionProfiles, _materializationService, _socialService, pvpPolicy, socialAffiliations), System::currentTimeMillis);
				if (!_clanService.start())
				{
					throw new IllegalStateException("Phantom clan organization service could not enter the running state.");
				}
				_clanSocialLifecycleObserver = new PhantomClanSocialLifecycleObserver(productionProfiles, _socialService);
				if (!_clanSocialLifecycleObserver.install())
				{
					throw new IllegalStateException("Phantom clan social lifecycle observer could not be installed.");
				}
				final PhantomClanDecision clanDecision = new PhantomClanDecision(_clanService);
				final PhantomSiegeCatalog siegeCatalog = PhantomSiegeCatalog.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/siege/high-five-siege-v1.xml").toPath());
				siegeCatalog.validateTopologyAnchors(anchorId -> _topologyService.query().findAnchor(anchorId).isPresent());
				_siegeService = new PhantomSiegeService(productionGoals, new L2jPhantomSiegeAuthority(productionProfiles, _materializationService, () -> _progressionService.findCatalog().orElse(null)), siegeCatalog, new PhantomSiegeMovementCoordinator(_navigationService, _topologyService, _combatService), _combatService, new PhantomSchedulerRelevanceSignalPort(_scheduler));
				final PhantomSiegeDecision siegeDecision = new PhantomSiegeDecision(_siegeService);
				final PhantomQuestInstanceCatalog questInstanceCatalog = PhantomQuestInstanceCatalog.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/quests/high-five-supported-content-v1.xml").toPath(), ServerConfig.DATAPACK_ROOT.toPath());
				_questInstanceService = new PhantomQuestInstanceService(productionGoals, questInstanceCatalog, questCollectionCatalog, new L2jPhantomQuestInstanceBackend(_materializationService, questInstanceCatalog, _gameKnowledgeService::query), _combatService, _navigationService, _progressionService, new PhantomSchedulerRelevanceSignalPort(_scheduler));
				if (!_questInstanceService.start())
				{
					throw new IllegalStateException("Phantom quest and instance service could not enter the running state.");
				}
				final PhantomQuestInstanceDecision questInstanceDecision = new PhantomQuestInstanceDecision(_questInstanceService);
				final PhantomFarmingPolicy farmingPolicy = PhantomFarmingPolicy.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/farming/high-five-farming-conflict-v1.xml").toPath());
				_farmingService = new PhantomFarmingService(farmingPolicy, new PhantomFarmingStore(productionProfiles), _acquisitionService, _topologyService, _partyCoordinator, _socialService, _settings.maxScheduledPhantomProfiles());
				if (!_farmingService.start())
				{
					throw new IllegalStateException("Phantom farming conflict service could not enter the running state.");
				}
				PhantomFarmingConflictPort.install(_farmingService);
				final PhantomFarmingDecision farmingDecision = new PhantomFarmingDecision(_farmingService);
				final L2jPhantomRiftBackend riftBackend = new L2jPhantomRiftBackend(partyBackend, productionProfiles, _materializationService, _progressionService, commerceCatalog.catalog(), _socialService);
				final PhantomRiftCatalog riftCatalog = PhantomRiftCatalog.load(new File(ServerConfig.DATAPACK_ROOT, "data/DimensionalRift.xml").toPath(), riftBackend);
				final PhantomRiftPolicy riftPolicy = PhantomRiftPolicy.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/rift/high-five-rift-policy-v1.xml").toPath(), riftCatalog, partyRoleCatalog);
				final PhantomPartyRoleMatcher riftRoles = new PhantomPartyRoleMatcher(partyRoleCatalog);
				final PhantomRiftReadinessService riftReadiness = new PhantomRiftReadinessService(riftBackend, riftCatalog, riftPolicy, riftRoles);
				final PhantomRiftService riftService = new PhantomRiftService(riftBackend, riftCatalog, riftPolicy, riftReadiness, new PhantomRiftStore(productionProfiles), new L2jPhantomRiftPartyPort(_partyCoordinator), System::currentTimeMillis);
				_partyCoordinator.installManagedInvitationPolicy(PhantomRiftService.GOAL_TYPE, riftService::evaluateManagedInvitation);
				final PhantomRiftDecision riftDecision = new PhantomRiftDecision(riftService);

				final File conversationCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-v1.xml");
				final File conversationCorpusFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv");
				final PhantomConversationCatalog conversationCatalog = PhantomConversationCatalog.load(conversationCatalogFile.toPath(), conversationCorpusFile.toPath());
				final File conversationExecutionCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-execution-v2.xml");
				final PhantomConversationExecutionCatalog conversationExecutionCatalog = PhantomConversationExecutionCatalog.load(conversationExecutionCatalogFile.toPath());
				final PhantomConversationExecutionStore conversationExecutionStore = new PhantomConversationExecutionStore(productionProfiles, conversationExecutionCatalog);
				final PhantomConversationPlanSink.Bridge conversationExecutionSignal = PhantomConversationPlanSink.bridge();
				final PhantomConversationGoalRuntimePort.Bridge conversationGoalRuntime = PhantomConversationGoalRuntimePort.bridge();
				final PhantomHumanizedCatalog humanizedCatalog = PhantomHumanizedCatalog.loadV3(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms").toPath(), _settings.conversation().customPackEnabled());
				final PhantomHumanizedConversationService.Settings humanizedSettings = new PhantomHumanizedConversationService.Settings(
					_settings.conversation().humanizedEnabled(),
					PhantomHumanizedCatalog.Register.valueOf(_settings.conversation().register().name()),
					PhantomHumanizedCatalog.ProfanityMode.valueOf(_settings.conversation().profanity().name()),
					PhantomHumanizedCatalog.Variation.valueOf(_settings.conversation().variation().name()),
					_settings.conversation().matureEnabled());
				final PhantomHumanizedConversationService humanizedConversation = new PhantomHumanizedConversationService(humanizedCatalog, PhantomPersonalConversationStore.production(productionProfiles), _socialService, humanizedSettings);
				final File clanDirectiveCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/clan/high-five-clan-directives-v1.xml");
				final PhantomClanDirectiveCatalog clanDirectiveCatalog = PhantomClanDirectiveCatalog.load(clanDirectiveCatalogFile.toPath());
				_clanDirectiveService = new PhantomClanDirectiveService(clanDirectiveCatalog, _materializationService, _socialService, new PhantomSchedulerRelevanceSignalPort(_scheduler));
				if (!_clanDirectiveService.start())
				{
					throw new IllegalStateException("Phantom clan directive service could not enter the running state.");
				}
				_conversationService = new PhantomConversationService(conversationCatalog, new PhantomConversationStore(productionProfiles, conversationExecutionStore), new L2jPhantomConversationContextPort(_materializationService, _topologyService.query()), _semanticUnderstandingService, _socialService, conversationExecutionSignal, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance(), _clanDirectiveService, humanizedConversation);
				_conversationExecutionService = new PhantomConversationExecutionService(conversationExecutionCatalog, conversationExecutionStore, productionGoals, new L2jPhantomConversationExecutionPort(conversationExecutionCatalog, _gameKnowledgeService, _topologyService.query(), _partyCoordinator, _materializationService, ChatObservationService.getInstance(), riftService, _farmingService, _combatService, _socialService, partySupportPolicy), conversationGoalRuntime);
				conversationExecutionSignal.install(_conversationExecutionService);
				if (!_conversationExecutionService.start())
				{
					throw new IllegalStateException("Phantom conversation execution service could not enter the running state.");
				}
				if (!_conversationService.start())
				{
					throw new IllegalStateException("Phantom conversation service could not enter the running state.");
				}
				final PhantomPvpSocialBridge pvpSocial = new PhantomPvpSocialBridge(_socialService, socialAffiliations);
				_pvpService = new PhantomPvpService(
					pvpPolicy,
					new PhantomPvpStore(productionProfiles),
					new PhantomPvpContext(pvpPolicy, _combatService, _partyCoordinator, _farmingService, pvpSocial, _materializationService),
					_combatService,
					new PhantomPvpConversationBridge(_conversationExecutionService),
					pvpSocial,
					new PhantomPvpRetreatCoordinator(_navigationService, _topologyService, _combatService),
					org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.load(new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/pvp/high-five-karma-recovery-v1.xml").toPath()),
					new org.l2jmobius.gameserver.phantoms.pvp.L2jPhantomKarmaRecoveryContext(_materializationService));
				if (!_pvpService.start())
				{
					throw new IllegalStateException("Phantom PvP service could not enter the running state.");
				}
				Objects.requireNonNull(pvpLifecycleBridge).install(_pvpService);
				final PhantomPopulationDecision populationDecision = new PhantomPopulationDecision(_populationManager);
				final PhantomPartyDecision partyDecision = new PhantomPartyDecision(_partyCoordinator);
				final PhantomCandidateRegistry candidateRegistry = new PhantomCandidateRegistry();
				farmingDecision.registerCandidates(candidateRegistry);
				acquisitionDecision.registerCandidates(candidateRegistry);
				economyDecision.registerCandidates(candidateRegistry);
				multipartyEconomyDecision.registerCandidates(candidateRegistry);
				commerceDecision.registerCandidates(candidateRegistry);
				backgroundDecision.registerCandidates(candidateRegistry);
				populationDecision.registerCandidates(candidateRegistry);
				partyDecision.registerCandidates(candidateRegistry);
				clanDecision.registerCandidates(candidateRegistry);
				riftDecision.registerCandidates(candidateRegistry);
				raidDecision.registerCandidates(candidateRegistry);
				siegeDecision.registerCandidates(candidateRegistry);
				questInstanceDecision.registerCandidates(candidateRegistry);
				candidateRegistry.seal();
				final PhantomStepHandlerRegistry handlerRegistry = new PhantomStepHandlerRegistry();
				new PhantomProgressionStepHandlers(_progressionService).register(handlerRegistry);
				new PhantomCombatStepHandlers(_combatService, combatPolicy).register(handlerRegistry);
				farmingDecision.registerHandlers(handlerRegistry);
				acquisitionDecision.registerHandlers(handlerRegistry);
				economyDecision.registerHandlers(handlerRegistry);
				multipartyEconomyDecision.registerHandlers(handlerRegistry);
				commerceDecision.registerHandlers(handlerRegistry);
				backgroundDecision.registerHandlers(handlerRegistry);
				populationDecision.registerHandlers(handlerRegistry);
				partyDecision.registerHandlers(handlerRegistry);
				clanDecision.registerHandlers(handlerRegistry);
				riftDecision.registerHandlers(handlerRegistry);
				raidDecision.registerHandlers(handlerRegistry);
				siegeDecision.registerHandlers(handlerRegistry);
				questInstanceDecision.registerHandlers(handlerRegistry);
				handlerRegistry.seal();
				_decisionEngine = new PhantomDecisionEngine(productionGoals, candidateRegistry, handlerRegistry, _metrics, _settings.maxScheduledPhantomProfiles(), _settings.diagnosticsEnabled() ? _selectedDecisionTrace : null, profileId -> _historicalBackgroundService.permitsNormalOperation(profileId) && ((_phantomStoreService == null) || !_phantomStoreService.blocksDecision(profileId)));
				_decisionEngine.start();
				conversationGoalRuntime.install(PhantomConversationGoalRuntimePort.decisionEngine(_decisionEngine));
				_populationManager.installDecisionEngine(_decisionEngine);
				final var controlPorts = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort>(java.util.List.of(_populationManager, _partyCoordinator, _conversationService, _conversationExecutionService, _pvpService));
				if (_autonomousMarketProducer != null)
				{
					controlPorts.add(_autonomousMarketProducer);
				}
				if (!_scheduler.installControlPort(new PhantomCompositeSchedulerControlPort(controlPorts)))
				{
					throw new IllegalStateException("Population control port could not be installed before scheduler start.");
				}
				if (!_scheduler.installLocalControlPort(() ->
				{
					_humanLocality.onPulse();
					final long now = System.nanoTime();
					if (now >= _nextRetentionRefreshNanos)
					{
						_nextRetentionRefreshNanos = now + 1_000_000_000L;
						_materializationRetention.refresh(_materializationService.list().stream().filter(entry -> entry.worldPresent()).map(entry -> entry.profileId()).toList());
					}
				}))
				{
					throw new IllegalStateException("Human-local control port could not be installed before scheduler start.");
				}
				productionWorkSink.install(item ->
				{
					if ((item.effectiveState() == PhantomActivityState.BACKGROUND) && (_populationEcology != null) && _populationManager.presence().permitsOrdinaryFarm(item.profileId()))
					{
						if (!_populationEcology.requestBackgroundReadiness(item.profileId()).complete())
						{
							return;
						}
					}
					_decisionEngine.accept(item);
				});
			}
			if (!_scheduler.start())
			{
				throw new IllegalStateException("Phantom scheduler could not enter the running state.");
			}
			if ((_populationManager != null) && !_populationManager.start())
			{
				throw new IllegalStateException("Phantom population manager could not enter the running state.");
			}
		}
		catch (RuntimeException e)
		{
			if (_questInstanceService != null)
			{
				_questInstanceService.beginStop();
			}
			if (_siegeService != null)
			{
				_siegeService.beginStop();
			}
			if (_raidAttemptService != null)
			{
				_raidAttemptService.beginStop();
			}
			if (_raidAssemblyService != null)
			{
				_raidAssemblyService.beginStop();
			}
			if (_pvpService != null)
			{
				_pvpService.beginStop();
			}
			if (_farmingService != null)
			{
				_farmingService.beginStop();
				PhantomFarmingConflictPort.uninstall(_farmingService);
			}
			if (_conversationService != null)
			{
				_conversationService.beginStop();
				if (!_conversationService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					throw e;
				}
			}
			if (_clanDirectiveService != null)
			{
				_clanDirectiveService.close();
			}
			if (_conversationExecutionService != null)
			{
				_conversationExecutionService.beginStop();
			}
			if (_clanSocialLifecycleObserver != null)
			{
				_clanSocialLifecycleObserver.close();
			}
			if (_ordinaryDeathRecovery != null)
			{
				_ordinaryDeathRecovery.close();
			}
			if (_clanService != null)
			{
				_clanService.beginStop();
				_clanService.finishStop();
			}
			if (_partyCoordinator != null)
			{
				_partyCoordinator.beginStop();
			}
			if (_populationManager != null)
			{
				_populationManager.beginStop();
			}
			if (_scheduler != null)
			{
				_scheduler.beginStop();
			}
			if (_decisionEngine != null)
			{
				_decisionEngine.beginStop();
			}
			if (_acquisitionService != null)
			{
				_acquisitionService.beginStop();
			}
			if (_combatService != null)
			{
				_combatService.beginStop();
			}
			if (_progressionService != null)
			{
				_progressionService.beginStop();
			}
			if (_commerceService != null)
			{
				_commerceService.beginStop();
			}
			if (_backgroundService != null)
			{
				_backgroundService.beginStop();
			}
			if (_semanticUnderstandingService != null)
			{
				_semanticUnderstandingService.beginStop();
			}
			if (_topologyService != null)
			{
				_topologyService.beginStop();
			}
			if (_navigationService != null)
			{
				_navigationService.beginStop();
			}
			final boolean pvpStopped = (_pvpService == null) || _pvpService.finishStop();
			final boolean conversationStopped = pvpStopped && ((_conversationService == null) || _conversationService.finishStop());
			final boolean conversationExecutionStopped = conversationStopped && ((_conversationExecutionService == null) || _conversationExecutionService.finishStop());
			final boolean farmingStopped = conversationExecutionStopped && ((_farmingService == null) || _farmingService.finishStop());
			if (farmingStopped && (_farmingService != null))
			{
				PhantomFarmingConflictPort.uninstall(_farmingService);
			}
			final boolean partyStopped = farmingStopped && ((_partyCoordinator == null) || _partyCoordinator.finishStop());
			boolean socialStopped = _socialService == null;
			if (partyStopped && (_socialService != null))
			{
				_socialService.beginStop();
				socialStopped = _socialService.finishStop();
			}
			final boolean acquisitionStopped = partyStopped && socialStopped && ((_acquisitionService == null) || _acquisitionService.finishStop());
			final boolean questInstanceStopped = acquisitionStopped && ((_questInstanceService == null) || _questInstanceService.finishStop());
			final boolean siegeStopped = questInstanceStopped && ((_siegeService == null) || _siegeService.finishStop());
			final boolean combatStopped = siegeStopped && ((_combatService == null) || _combatService.finishStop());
			final boolean commerceStopped = (_commerceService == null) || _commerceService.finishStop();
			final boolean populationStopped = (_populationManager == null) || _populationManager.finishStop();
			boolean materializationStopped = _materializationService == null;
			if (combatStopped && commerceStopped && populationStopped && backgroundReadyForMaterializationShutdown() && (_materializationService != null))
			{
				materializationStopped = _materializationService.shutdown().state() == ServiceState.STOPPED;
			}
			final boolean backgroundStopped = populationStopped && ((_backgroundService == null) || (materializationStopped && _backgroundService.finishStop()));
			final boolean progressionStopped = backgroundStopped && ((_progressionService == null) || _progressionService.finishStop());
			if (backgroundStopped && (_gameKnowledgeService != null))
			{
				_gameKnowledgeService.beginStop();
			}
			if (backgroundStopped && progressionStopped && (_scheduler != null))
			{
				_scheduler.finishStop();
			}
			final boolean semanticStopped = (_semanticUnderstandingService == null) || _semanticUnderstandingService.finishStop();
			if (backgroundStopped && semanticStopped && (_gameKnowledgeService != null))
			{
				_gameKnowledgeService.finishStop();
			}
			if (backgroundStopped && semanticStopped && (_topologyService != null))
			{
				_topologyService.finishStop();
			}
			if (backgroundStopped && (_decisionEngine != null))
			{
				_decisionEngine.finishStop();
			}
			if (backgroundStopped && (_navigationService != null))
			{
				_navigationService.finishStop();
			}
			_state = backgroundStopped && progressionStopped && semanticStopped ? State.STOPPED : State.FAILED;
			throw e;
		}
		_metrics.recordLifecycleStart();
		_state = State.RUNNING;
		return true;
	}

	public synchronized boolean shutdown()
	{
		if (_autonomousMarketProducer != null)
		{
			_autonomousMarketProducer.beginStop();
		}
		if (_shutdownFailureForTesting && (_state == State.RUNNING))
		{
			_scheduler.beginStop();
			_metrics.recordShutdownFailure();
			_state = State.FAILED;
			return false;
		}

		if (_state == State.STOPPED)
		{
			return false;
		}
		if (_populationEcology != null)
		{
			_populationEcology.beginStop();
			if (!_populationEcology.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
		}

		if (_state == State.RUNNING)
		{
			if (_raidAttemptService != null)
			{
				_raidAttemptService.beginStop();
			}
			if (_raidAssemblyService != null)
			{
				_raidAssemblyService.beginStop();
			}
			if (_multipartyEconomyService != null)
			{
				if (!_multipartyEconomyService.shutdown(System.currentTimeMillis()).successful())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_phantomStoreService != null)
			{
				if (!_phantomStoreService.shutdown().successful())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_economyReservations != null)
			{
				_economyReservations.shutdown(System.currentTimeMillis());
			}
			if (_pvpService != null)
			{
				_pvpService.beginStop();
				if (!_pvpService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_farmingService != null)
			{
				_farmingService.beginStop();
				PhantomFarmingConflictPort.uninstall(_farmingService);
			}
			if (_conversationService != null)
			{
				_conversationService.beginStop();
				if (!_conversationService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_clanDirectiveService != null)
			{
				_clanDirectiveService.close();
			}
			if (_conversationExecutionService != null)
			{
				_conversationExecutionService.beginStop();
				if (!_conversationExecutionService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_farmingService != null)
			{
				if (!_farmingService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if (_clanSocialLifecycleObserver != null)
			{
				_clanSocialLifecycleObserver.close();
			}
			if (_ordinaryDeathRecovery != null)
			{
				_ordinaryDeathRecovery.close();
			}
			if (_clanService != null)
			{
				_clanService.beginStop();
				_clanService.finishStop();
			}
			if (_partyCoordinator != null)
			{
				_partyCoordinator.beginStop();
			}
			if (_populationManager != null)
			{
				_populationManager.beginStop();
			}
			_scheduler.beginStop();
			if (_decisionEngine != null)
			{
				_decisionEngine.beginStop();
			}
			if (_acquisitionService != null)
			{
				_acquisitionService.beginStop();
			}
			if (_questInstanceService != null)
			{
				_questInstanceService.beginStop();
			}
			if (_siegeService != null)
			{
				_siegeService.beginStop();
			}
			if (_combatService != null)
			{
				_combatService.beginStop();
			}
			if (_progressionService != null)
			{
				_progressionService.beginStop();
			}
			if (_commerceService != null)
			{
				_commerceService.beginStop();
			}
			if (_backgroundService != null)
			{
				_backgroundService.beginStop();
			}
			if (_semanticUnderstandingService != null)
			{
				_semanticUnderstandingService.beginStop();
			}
			if (_topologyService != null)
			{
				_topologyService.beginStop();
			}
			if (_navigationService != null)
			{
				_navigationService.beginStop();
			}
			if ((_partyCoordinator != null) && !_partyCoordinator.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if (_socialService != null)
			{
				_socialService.beginStop();
				if (!_socialService.finishStop())
				{
					_metrics.recordShutdownFailure();
					_state = State.FAILED;
					return false;
				}
			}
			if ((_acquisitionService != null) && !_acquisitionService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_questInstanceService != null) && !_questInstanceService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_siegeService != null) && !_siegeService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_combatService != null) && !_combatService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_commerceService != null) && !_commerceService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_populationManager != null) && !_populationManager.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if (!backgroundReadyForMaterializationShutdown())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if (_materializationService != null)
			{
				final ShutdownResult result = _materializationService.shutdown();
				if (result.state() != ServiceState.STOPPED)
				{
					_state = State.FAILED;
					return false;
				}
			}
			if ((_backgroundService != null) && !_backgroundService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_progressionService != null) && !_progressionService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if (_gameKnowledgeService != null)
			{
				_gameKnowledgeService.beginStop();
			}
			if (!_scheduler.finishStop())
			{
				_state = State.FAILED;
				return false;
			}
			if ((_semanticUnderstandingService != null) && !_semanticUnderstandingService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_gameKnowledgeService != null) && !_gameKnowledgeService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_topologyService != null) && !_topologyService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			if ((_decisionEngine != null) && !_decisionEngine.finishStop())
			{
				_state = State.FAILED;
				return false;
			}
			if ((_navigationService != null) && !_navigationService.finishStop())
			{
				_metrics.recordShutdownFailure();
				_state = State.FAILED;
				return false;
			}
			_metrics.recordLifecycleStop();
			PhantomEconomyConflictPort.uninstall(_economyReservations);
			_state = State.STOPPED;
			return true;
		}
		if (_state == State.FAILED)
		{
			if (_questInstanceService != null)
			{
				_questInstanceService.beginStop();
			}
			if (_siegeService != null)
			{
				_siegeService.beginStop();
			}
			if (_raidAttemptService != null)
			{
				_raidAttemptService.beginStop();
			}
			if (_raidAssemblyService != null)
			{
				_raidAssemblyService.beginStop();
			}
			if (_multipartyEconomyService != null)
			{
				if (!_multipartyEconomyService.shutdown(System.currentTimeMillis()).successful())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_phantomStoreService != null)
			{
				if (!_phantomStoreService.shutdown().successful())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_economyReservations != null)
			{
				_economyReservations.shutdown(System.currentTimeMillis());
			}
			if ((_pvpService != null) && (_pvpService.snapshot().state() != PhantomPvpService.State.STOPPED))
			{
				_pvpService.beginStop();
				if (!_pvpService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_farmingService != null)
			{
				PhantomFarmingConflictPort.uninstall(_farmingService);
			}
			if ((_conversationService != null) && (_conversationService.snapshot().state() != PhantomConversationService.ServiceState.STOPPED))
			{
				_conversationService.beginStop();
				if (!_conversationService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_clanDirectiveService != null)
			{
				_clanDirectiveService.close();
			}
			if ((_conversationExecutionService != null) && (_conversationExecutionService.snapshot().state() != PhantomConversationExecutionService.State.STOPPED))
			{
				_conversationExecutionService.beginStop();
				if (!_conversationExecutionService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if ((_farmingService != null) && (_farmingService.snapshot().state() != PhantomFarmingService.State.STOPPED))
			{
				_farmingService.beginStop();
				if (!_farmingService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_clanSocialLifecycleObserver != null)
			{
				_clanSocialLifecycleObserver.close();
			}
			if (_ordinaryDeathRecovery != null)
			{
				_ordinaryDeathRecovery.close();
			}
			if (_clanService != null)
			{
				_clanService.beginStop();
				_clanService.finishStop();
			}
			if ((_partyCoordinator != null) && (_partyCoordinator.snapshot().state() != PhantomPartyCoordinator.State.STOPPED))
			{
				_partyCoordinator.beginStop();
				if (!_partyCoordinator.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if ((_socialService != null) && (_socialService.snapshot().state() != PhantomSocialService.ServiceState.STOPPED))
			{
				_socialService.beginStop();
				if (!_socialService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if ((_populationManager != null) && (_populationManager.snapshot().state() != PhantomPopulationManager.LifecycleState.STOPPED))
			{
				_populationManager.beginStop();
				if (!_populationManager.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			_scheduler.beginStop();
			if (_decisionEngine != null)
			{
				_decisionEngine.beginStop();
			}
			if ((_acquisitionService != null) && (_acquisitionService.snapshot().state() == PhantomAcquisitionService.ServiceState.RUNNING))
			{
				_acquisitionService.beginStop();
			}
			if ((_acquisitionService != null) && (_acquisitionService.snapshot().state() != PhantomAcquisitionService.ServiceState.STOPPED) && !_acquisitionService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_questInstanceService != null) && (_questInstanceService.snapshot().state() != PhantomQuestInstanceService.State.STOPPED) && !_questInstanceService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_siegeService != null) && !_siegeService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if (_combatService != null)
			{
				_combatService.beginStop();
				_combatService.retryFailedCleanup();
			}
			if ((_combatService != null) && (_combatService.snapshot().state() != PhantomCombatService.ServiceState.STOPPED) && !_combatService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if (_commerceService != null)
			{
				_commerceService.beginStop();
			}
			if ((_commerceService != null) && (_commerceService.snapshot().state() != PhantomCommerceService.StateSnapshot.STOPPED) && !_commerceService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_backgroundService != null) && (_backgroundService.snapshot().state() == PhantomBackgroundService.ServiceState.RUNNING))
			{
				_backgroundService.beginStop();
			}
			if (!backgroundReadyForMaterializationShutdown())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if (_materializationService != null)
			{
				final ShutdownResult result = _materializationService.shutdown();
				if (result.state() != ServiceState.STOPPED)
				{
					return false;
				}
			}
			if ((_backgroundService != null) && (_backgroundService.snapshot().state() != PhantomBackgroundService.ServiceState.STOPPED) && !_backgroundService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if (_progressionService != null)
			{
				_progressionService.beginStop();
			}
			if ((_progressionService != null) && (_progressionService.snapshot().state() != PhantomProgressionService.State.STOPPED) && !_progressionService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_scheduler.snapshot().state() != PhantomScheduler.SchedulerState.STOPPED) && !_scheduler.finishStop())
			{
				return false;
			}
			if ((_semanticUnderstandingService != null) && (_semanticUnderstandingService.snapshot().state() != PhantomSemanticUnderstandingService.State.STOPPED))
			{
				_semanticUnderstandingService.beginStop();
				if (!_semanticUnderstandingService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
			}
			if (_gameKnowledgeService != null)
			{
				_gameKnowledgeService.beginStop();
			}
			if (_topologyService != null)
			{
				_topologyService.beginStop();
			}
			if (_navigationService != null)
			{
				_navigationService.beginStop();
			}
			if ((_gameKnowledgeService != null) && (_gameKnowledgeService.snapshot().state() != PhantomGameKnowledgeService.State.STOPPED) && !_gameKnowledgeService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_topologyService != null) && (_topologyService.snapshot().state() != PhantomTopologyService.State.STOPPED) && !_topologyService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_decisionEngine != null) && (_decisionEngine.snapshot().state() != PhantomDecisionEngine.State.STOPPED) && !_decisionEngine.finishStop())
			{
				return false;
			}
			if ((_navigationService != null) && (_navigationService.snapshot().state() != PhantomNavigationService.ServiceState.STOPPED) && !_navigationService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			_metrics.recordLifecycleStop();
			PhantomEconomyConflictPort.uninstall(_economyReservations);
			_state = State.STOPPED;
			return true;
		}

		_state = State.STOPPED;
		return false;
	}

	private boolean backgroundReadyForMaterializationShutdown()
	{
		return ((_ordinaryDeathRecovery == null) || _ordinaryDeathRecovery.drained()) && ((_backgroundService == null) || permitsMaterializationShutdown(_backgroundService.materializationQuiescence()));
	}

	public static boolean permitsMaterializationShutdown(PhantomBackgroundService.QuiescenceSnapshot snapshot)
	{
		return (snapshot != null) && snapshot.ready();
	}

	public synchronized Snapshot snapshot()
	{
		final long selectedProfileId = _selectedDecisionTrace.selectedProfileId();
		final boolean selectedAttached = (selectedProfileId > 0) && (_decisionEngine != null) && _decisionEngine.find(selectedProfileId).isPresent();
		return new Snapshot(_state, _settings, _scheduler != null ? _scheduler.snapshot() : PhantomScheduler.SchedulerSnapshot.inactive(), _decisionEngine != null ? _decisionEngine.snapshot() : PhantomDecisionEngine.EngineSnapshot.inactive(), _navigationService != null ? _navigationService.snapshot() : PhantomNavigationService.ServiceSnapshot.inactive(), _topologyService != null ? _topologyService.snapshot() : PhantomTopologyService.ServiceSnapshot.inactive(), _gameKnowledgeService != null ? _gameKnowledgeService.snapshot() : PhantomGameKnowledgeService.ServiceSnapshot.inactive(), _semanticUnderstandingService != null ? _semanticUnderstandingService.snapshot() : PhantomSemanticUnderstandingService.Snapshot.inactive(), _progressionService != null ? _progressionService.snapshot() : PhantomProgressionService.ServiceSnapshot.inactive(), _combatService != null ? _combatService.snapshot() : PhantomCombatService.ServiceSnapshot.inactive(), _backgroundService != null ? _backgroundService.snapshot() : null, _populationManager != null ? _populationManager.snapshot() : PhantomPopulationManager.Snapshot.inactive(), _socialService != null ? _socialService.snapshot() : PhantomSocialService.Snapshot.inactive(), _conversationService != null ? _conversationService.snapshot() : PhantomConversationService.Snapshot.inactive(), _conversationExecutionService != null ? _conversationExecutionService.snapshot() : PhantomConversationExecutionService.Snapshot.inactive(), ChatObservationService.getInstance().snapshot(), _metrics.snapshot(), _trace != null ? _trace.snapshot() : PhantomDiagnosticTrace.Snapshot.disabled(), _selectedDecisionTrace.snapshot(selectedAttached));
	}

	public synchronized PhantomPartyCoordinator.Snapshot partySnapshot()
	{
		return _partyCoordinator == null ? PhantomPartyCoordinator.Snapshot.inactive() : _partyCoordinator.snapshot();
	}

	public synchronized PhantomRaidReadinessService raidReadiness()
	{
		return _raidReadinessService;
	}

	public synchronized PhantomRaidRecruitmentService raidRecruitment()
	{
		return _raidRecruitmentService;
	}

	public synchronized PhantomSiegeService siegeService()
	{
		return _siegeService;
	}

	public synchronized PhantomQuestInstanceService questInstanceService()
	{
		return _questInstanceService;
	}

	public synchronized PhantomRaidAssemblyService raidAssembly()
	{
		return _raidAssemblyService;
	}

	public synchronized PhantomRaidAttemptService raidAttempt()
	{
		return _raidAttemptService;
	}

	public synchronized PhantomAcquisitionService.Snapshot acquisitionSnapshot()
	{
		return _acquisitionService == null ? null : _acquisitionService.snapshot();
	}

	public synchronized PhantomFarmingService.Snapshot farmingSnapshot()
	{
		return _farmingService == null ? null : _farmingService.snapshot();
	}

	public static synchronized boolean startConfigured()
	{
		if ((_operatorMode == OperatorMode.DRAINED) || (_operatorMode == OperatorMode.DISABLED))
		{
			return false;
		}
		return startConfiguredInternal();
	}

	private static boolean startConfiguredInternal()
	{
		if (!PhantomPlayersConfig.isEnabled() || (_configuredInstance != null))
		{
			return false;
		}

		return startConfiguredCandidate(new PhantomSystem(PhantomPlayersConfig.settings(), true));
	}

	static synchronized boolean startConfiguredForTesting(PhantomPlayersConfig.Settings settings)
	{
		if (_configuredInstance != null)
		{
			return false;
		}
		return startConfiguredCandidate(new PhantomSystem(Objects.requireNonNull(settings), true));
	}

	private static boolean startConfiguredCandidate(PhantomSystem candidate)
	{
		try
		{
			if (!candidate.start())
			{
				throw new IllegalStateException("Configured Phantom World skeleton did not start.");
			}
			_configuredInstance = candidate;
			return true;
		}
		catch (RuntimeException e)
		{
			candidate.shutdown();
			throw e;
		}
	}

	public static synchronized OperatorControlResult operatorEnable()
	{
		if (_configuredInstance != null)
		{
			final State actualState = _configuredInstance.snapshot().state();
			if (actualState == State.RUNNING)
			{
				_operatorMode = OperatorMode.ENABLED;
				return operatorControlResult(OperatorControlCode.ALREADY_RUNNING);
			}
			if (actualState == State.STOPPED)
			{
				_configuredInstance = null;
			}
			else
			{
				return operatorControlResult(OperatorControlCode.OWNER_BUSY);
			}
		}

		_operatorMode = OperatorMode.ENABLED;
		if (!PhantomPlayersConfig.isEnabled())
		{
			return operatorControlResult(OperatorControlCode.CONFIG_DISABLED);
		}
		try
		{
			return operatorControlResult(startConfiguredInternal() ? OperatorControlCode.STARTED : OperatorControlCode.OWNER_BUSY);
		}
		catch (RuntimeException e)
		{
			return operatorControlResult(OperatorControlCode.START_FAILED);
		}
	}

	public static synchronized OperatorControlResult operatorDrain()
	{
		return requestOperatorStop(OperatorMode.DRAINED, OperatorControlCode.DRAINED, OperatorControlCode.ALREADY_DRAINED);
	}

	public static synchronized OperatorControlResult operatorDisable()
	{
		return requestOperatorStop(OperatorMode.DISABLED, OperatorControlCode.DISABLED, OperatorControlCode.ALREADY_DISABLED);
	}

	public static synchronized PhantomPopulationResetService.ResetPreview operatorResetPreview()
	{
		return populationResetService().preview();
	}

	public static synchronized PhantomPopulationResetService.ResetResult operatorResetConfirm(String token, boolean reseed)
	{
		return populationResetService().confirm(token, reseed);
	}

	public static synchronized boolean operatorResetCancel()
	{
		return (_populationResetService != null) && _populationResetService.cancel();
	}

	private static PhantomPopulationResetService populationResetService()
	{
		if (_populationResetService == null)
		{
			_populationResetService = new PhantomPopulationResetService(new PhantomPopulationResetService.Lifecycle()
			{
				@Override
				public OperatorControlResult drain()
				{
					return operatorDrain();
				}

				@Override
				public OperatorControlResult reseed()
				{
					return operatorEnable();
				}
			});
		}
		return _populationResetService;
	}

	private static OperatorControlResult requestOperatorStop(OperatorMode requestedMode, OperatorControlCode stoppedCode, OperatorControlCode alreadyCode)
	{
		final boolean alreadyRequested = _operatorMode == requestedMode;
		_operatorMode = requestedMode;
		if (_configuredInstance == null)
		{
			return operatorControlResult(alreadyRequested ? alreadyCode : stoppedCode);
		}

		shutdownConfiguredInstance();
		return operatorControlResult(_configuredInstance == null ? stoppedCode : OperatorControlCode.SHUTDOWN_FAILED);
	}

	public static synchronized boolean shutdownIfStarted()
	{
		return (_configuredInstance != null) && shutdownConfiguredInstance();
	}

	private static boolean shutdownConfiguredInstance()
	{
		final PhantomSystem configured = _configuredInstance;
		final boolean stopped = configured.shutdown();
		if (configured.snapshot().state() == State.STOPPED)
		{
			_configuredInstance = null;
		}
		return stopped;
	}

	private static OperatorControlResult operatorControlResult(OperatorControlCode code)
	{
		final OperatorStatus status = operatorStatus();
		return new OperatorControlResult(code, status.operatorMode(), status.desiredRuntimeEnabled(), status.runtimeConfigured(), status.runtimeState());
	}

	public static synchronized boolean hasConfiguredInstance()
	{
		return _configuredInstance != null;
	}

	public static synchronized OperatorStatus operatorStatus()
	{
		final PhantomPlayersConfig.Settings settings = PhantomPlayersConfig.settings();
		final PhantomSystem configured = _configuredInstance;
		if (configured == null)
		{
			return OperatorStatus.notRunning(settings.enabled(), settings.diagnosticsEnabled(), _operatorMode);
		}
		final Snapshot snapshot = configured.snapshot();
		final PhantomMetrics.Snapshot metrics = snapshot.metrics();
		final PhantomPopulationEcologyService.Snapshot ecology = configured._populationManager == null ? PhantomPopulationEcologyService.Snapshot.disabled() : configured._populationManager.ecologySnapshot();
		final PhantomPopulationManager.AdmissionSnapshot admission = configured._populationManager == null ? PhantomPopulationManager.AdmissionSnapshot.inactive() : configured._populationManager.admissionSnapshot();
		final PhantomPresenceRegistry.Snapshot presence = configured._populationManager == null ? new PhantomPresenceRegistry.Snapshot(0, 0, 0) : configured._populationManager.presence().snapshot();
		final var registry = configured._topologyService == null ? new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrySnapshot(0, 0, 0, 0, 0) : configured._topologyService.registrySnapshot();
		final String periodicOwner = configured._populationEcology == null ? "DISABLED_BY_CONFIG" : "ECOLOGY";
		final int localSignaled = configured._humanLocality == null ? 0 : configured._humanLocality.localCount();
		final long worldMaterialized = configured._materializationService == null ? 0 : configured._materializationService.snapshot().materializations().stream().filter(entry -> (entry.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE) && entry.worldPresent()).count();
		final java.util.Map<String, Integer> levelHistogram;
		try
		{
			levelHistogram = ecology.enabled() ? configured._populationManager.currentLevelHistogram() : java.util.Map.of();
		}
		catch (RuntimeException exception)
		{
			return OperatorStatus.readFailure(settings.enabled(), settings.diagnosticsEnabled(), _operatorMode, snapshot, metrics, ecology, admission, worldMaterialized, presence, registry, periodicOwner, localSignaled);
		}
		return new OperatorStatus(
			settings.enabled(),
			settings.diagnosticsEnabled(),
			_operatorMode,
			desiredRuntimeEnabled(settings.enabled(), _operatorMode),
			true,
			snapshot.state(),
			snapshot.scheduler().state(),
			snapshot.decision().state(),
			metrics.activeCurrent(),
			metrics.activePeak(),
			metrics.activity().stateCounts(),
			snapshot.scheduler().overloadLevel(),
			snapshot.scheduler().peakOverloadLevel(),
			snapshot.scheduler().ready(),
			snapshot.scheduler().due(),
			snapshot.scheduler().capacity(),
			metrics.queueAccepted(),
			metrics.queueRejected(),
			metrics.shutdownFailures(),
			ecology,
			levelHistogram,
			snapshot.selectedTrace(),
			admission,
			worldMaterialized,
			presence,
			registry,
			periodicOwner,
			localSignaled);
	}

	public static synchronized java.util.Optional<OperatorAdmissionProfile> operatorAdmissionProfile(long profileId)
	{
		final PhantomSystem configured = _configuredInstance;
		if ((profileId <= 0) || (configured == null) || (configured._populationManager == null))
		{
			return java.util.Optional.empty();
		}
		return configured._populationManager.admissionProfile(profileId).map(admission -> new OperatorAdmissionProfile(admission,
			configured._scheduler == null ? null : configured._scheduler.find(profileId).orElse(null),
			configured._materializationService == null ? null : configured._materializationService.find(profileId).orElse(null),
			configured._materializationActivity == null ? null : configured._materializationActivity.diagnosticFailures().get(profileId),
			configured._populationManager.presence().busyReason(profileId),
			configured._populationEcology == null ? null : configured._populationEcology.dueSnapshot(profileId),
			(configured._humanLocality != null) && configured._humanLocality.isLocal(profileId),
			(configured._humanLocality != null) && configured._humanLocality.isNativeVisible(profileId),
			configured._materializationRetention == null ? java.util.Set.of() : configured._materializationRetention.observe(profileId).reasons()));
	}

	/** Read-only topology and locality state for one operator-selected ordinary profile. */
	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorLocalityTarget(long profileId)
	{
		final PhantomSystem configured = _configuredInstance;
		if ((profileId <= 0) || (configured == null) || (configured._state != State.RUNNING) || (configured._topologyService == null))
		{
			return java.util.Optional.empty();
		}
		return configured._topologyService.findProfile(profileId)
			.filter(profile -> profile.point() != null)
			.map(profile -> new OperatorLocalityTarget(profile.profileId(), profile.point(), profile.nodeId(), profile.topologyGeneration()));
	}

	/** Addressable M1 read: a live Player wins over the durable anchor only with a stable lifecycle epoch. */
	public static synchronized java.util.Optional<OperatorM1TargetSnapshot> operatorM1TargetSnapshot(long profileId)
	{
		return operatorM1TargetSnapshot(profileId, null);
	}

	/** Capture client eligibility with the same verified Player and lifecycle epoch as its live position. */
	public static synchronized java.util.Optional<OperatorM1TargetSnapshot> operatorM1TargetSnapshot(long profileId, Player human)
	{
		final PhantomSystem configured = _configuredInstance;
		if ((profileId <= 0) || (configured == null) || (configured._state != State.RUNNING) || (configured._topologyService == null) || (configured._materializationService == null) || (configured._populationManager == null))
		{
			return java.util.Optional.empty();
		}
		return configured._topologyService.findProfile(profileId).filter(profile -> profile.point() != null).map(profile -> m1TargetSnapshot(configured, profile.profileId(), profile.point(), profile.topologyGeneration(), human));
	}

	/** One bounded INITIAL scan; later M1 phases use operatorM1TargetSnapshot for one selected id. */
	public static synchronized java.util.List<OperatorM1TargetSnapshot> operatorM1CandidateSnapshots(PhantomTopologyPoint human, long maxDistanceSquared2D)
	{
		Objects.requireNonNull(human);
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._state != State.RUNNING) || (configured._topologyService == null) || (configured._materializationService == null) || (configured._populationManager == null) || (maxDistanceSquared2D < 0))
		{
			return java.util.List.of();
		}
		final java.util.List<OperatorM1TargetSnapshot> candidates = new java.util.ArrayList<>();
		for (var profile : configured._topologyService.listProfiles())
		{
			if ((profile.point() == null) || configured._populationManager.admissionProfile(profile.profileId()).filter(admission -> admission.populationState() == org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State.READY).isEmpty())
			{
				continue;
			}
			final OperatorM1TargetSnapshot candidate = m1TargetSnapshot(configured, profile.profileId(), profile.point(), profile.topologyGeneration(), null);
			if ((candidate.observedPosition() != null) && (candidate.observedPosition().instanceId() == human.instanceId()) && (human.distanceSquared2D(candidate.observedPosition()) <= maxDistanceSquared2D))
			{
				candidates.add(candidate);
			}
		}
		candidates.sort(java.util.Comparator.comparingLong((OperatorM1TargetSnapshot candidate) -> human.distanceSquared2D(candidate.observedPosition())).thenComparingLong(OperatorM1TargetSnapshot::profileId));
		return java.util.List.copyOf(candidates.subList(0, Math.min(128, candidates.size())));
	}

	private static OperatorM1TargetSnapshot m1TargetSnapshot(PhantomSystem configured, long profileId, PhantomTopologyPoint committed, long committedSequence, Player human)
	{
		final var first = configured._materializationService.find(profileId).orElse(null);
		final int objectId = first == null ? 0 : first.characterObjectId();
		final WorldObject worldObject = objectId <= 0 ? null : World.getInstance().findObject(objectId);
		final Player player = worldObject instanceof Player live ? live : null;
		final boolean verified = (first != null) && (first.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE) && first.worldPresent() && first.identityLeaseRetained() && first.outboundAttached() && (player != null) && (player.getClient() == null) && player.isOnline() && (World.getInstance().getPlayer(objectId) == player) && (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
		final Location location = verified ? player.getLocation().clone() : null;
		final var second = configured._materializationService.find(profileId).orElse(null);
		final boolean stable = verified && (second != null) && (second.state() == first.state()) && (second.characterObjectId() == objectId) && (second.materializedAtNanos() == first.materializedAtNanos()) && second.worldPresent() && (World.getInstance().findObject(objectId) == player);
		final boolean safelyStored = first == null ? configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE : (first.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.STORED) && !first.worldPresent() && !first.identityLeaseRetained() && (worldObject == null) && (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null);
		final var state = first == null ? safelyStored ? org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.STORED : null : first.state();
		final PhantomTopologyPoint livePoint = stable ? new PhantomTopologyPoint(location.getX(), location.getY(), location.getZ(), location.getInstanceId()) : null;
		final var choice = LocalPlayM1Observation.select(committed, state, stable, livePoint, safelyStored);
		final WorldObject nativeTarget = stable ? player.getTarget() : null;
		return new OperatorM1TargetSnapshot(profileId, objectId, first == null ? 0 : first.materializedAtNanos(), System.nanoTime(), committed, committedSequence, choice.observed(), choice.source(), stable, first != null && first.worldPresent(), first == null ? (safelyStored ? "STORED" : "UNAVAILABLE") : first.state().name(), stable && player.isOnline(), stable && (human != null) && player.isVisibleFor(human), stable && player.isMoving(), stable && player.isAttackingNow(), stable && player.isCastingNow(), stable && player.isAutoPlaying(), nativeTarget == null ? 0 : nativeTarget.getObjectId(), (nativeTarget instanceof org.l2jmobius.gameserver.model.actor.Attackable attackable) && attackable.isMonster() && !attackable.isDead());
	}

	public static synchronized boolean operatorHumanLocality(long profileId)
	{
		final PhantomSystem configured = _configuredInstance;
		return (profileId > 0) && (configured != null) && (configured._state == State.RUNNING) && (configured._humanLocality != null) && configured._humanLocality.isLocal(profileId);
	}

	public static synchronized Map<String, String> operatorReadinessProgress(long profileId)
	{
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._populationEcology == null)) { return Map.of(); }
		final var progress = configured._populationEcology.progressSnapshot(profileId);
		final Map<String, String> result = new java.util.LinkedHashMap<>();
		result.put("ordinaryQueued", Integer.toString(progress.ordinaryQueued()));
		result.put("urgentQueued", Integer.toString(progress.urgentQueued()));
		result.put("workerState", progress.workerState());
		result.put("activeProfile", Long.toString(progress.activeProfile()));
		result.put("currentStage", progress.currentStage());
		result.put("enqueueAgeMillis", Long.toString(progress.enqueueAgeMillis()));
		result.put("lastProgressAgeMillis", Long.toString(progress.lastProgressAgeMillis()));
		result.put("nextWakeMillis", Long.toString(progress.nextWakeMillis()));
		result.put("nextRetryMillis", Long.toString(progress.nextRetryMillis()));
		result.put("historicalStatus", progress.historicalStatus());
		result.put("historicalRequestId", progress.requestId());
		result.put("innerCursorMinute", Long.toString(progress.innerCursorMinute()));
		result.put("innerTargetMinute", Long.toString(progress.targetMinute()));
		result.put("innerRevision", Long.toString(progress.innerRevision()));
		final var preparation = configured._populationEcology.preparationSnapshot();
		result.put("physicalCount", Integer.toString(preparation.physicalCount()));
		result.put("admittedPreparationCount", Integer.toString(preparation.admittedPreparationCount()));
		result.put("waitingPreparationCount", Integer.toString(preparation.waitingPreparationCount()));
		result.put("focusId", Long.toString(preparation.focusId()));
		result.put("focusAgeMillis", Long.toString(preparation.focusAgeMillis()));
		result.put("oldestWaitMillis", Long.toString(preparation.oldestWaitMillis()));
		result.put("runnableOrdinary", Integer.toString(preparation.runnableOrdinary()));
		result.put("reservedPaused", Integer.toString(preparation.reservedPaused()));
		result.put("committedIntervals", Integer.toString(preparation.committedIntervals()));
		result.put("elapsedBatchMillis", Long.toString(preparation.elapsedBatchMillis()));
		final var resize = configured._populationManager.resizeSnapshot();
		result.put("participants", Integer.toString(resize.participants()));
		result.put("retiredReserve", Integer.toString(resize.retiredReserve()));
		result.put("resizePending", Integer.toString(resize.pendingRetirements()));
		result.put("resizePhase", resize.phase());
		if (configured._humanLocality != null)
		{
			result.put("signalDelivery", String.valueOf(configured._humanLocality.deliverySnapshot().get(profileId)));
			result.put("localityOverflow", Boolean.toString(configured._humanLocality.physicalSnapshot().overflow()));
		}
		return Map.copyOf(result);
	}

	/** Read-only test of the production prewarm gates for a consented human point. */
	public static synchronized boolean operatorCanPrewarmAt(long profileId, PhantomTopologyPoint human)
	{
		final PhantomSystem configured = _configuredInstance;
		return (profileId > 0) && (configured != null) && (configured._state == State.RUNNING) && (configured._humanLocality != null) && configured._humanLocality.canPrewarmAt(profileId, human);
	}

	/** Bounded read-only census of naturally visible ordinary Players for the same Pilot lease. */
	public static synchronized Map<String, String> operatorVisibleLifeCensus(Player human)
	{
		return operatorVisibleLifeCensus(human, 0);
	}

	/** Small pages preserve the existing 64 KiB Pilot result contract. */
	public static synchronized Map<String, String> operatorVisibleLifeCensus(Player human, long afterProfileId)
	{
		final PhantomSystem configured = _configuredInstance;
		final Map<String, String> result = new java.util.LinkedHashMap<>();
		if ((configured == null) || (configured._state != State.RUNNING) || (configured._backgroundService == null) || (configured._materializationService == null))
		{
			return Map.of("censusCount", "0");
		}
		int count = 0;
		int eligible = 0;
		long nextProfileId = 0;
		long lastIncludedProfileId = afterProfileId;
		final var backgroundStatus = configured._backgroundService.snapshot().state();
		result.put("censusBackgroundService", backgroundStatus.name());
		for (var entry : configured._materializationService.snapshot().materializations().stream().filter(value -> value.worldPresent()).sorted(java.util.Comparator.comparingLong(value -> value.profileId())).toList())
		{
			if (entry.profileId() <= afterProfileId) { continue; }
			final var object = World.getInstance().findObject(entry.characterObjectId());
			if (!(object instanceof Player player) || !player.isOnline() || !player.isVisibleFor(human) || (player.getInstanceId() != human.getInstanceId()) || !World.getInstance().getRegion(human).isSurroundingRegion(World.getInstance().getRegion(player)))
			{
				continue;
			}
			PhantomGoal goal;
			try
			{
				goal = configured._backgroundService.ordinaryGoal(entry.profileId()).orElse(null);
			}
			catch (RuntimeException exception)
			{
				goal = null;
			}
			final var spec = goal == null ? null : org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal.status() == PhantomGoalStatus.ACTIVE ? goal : goal.withStatus(PhantomGoalStatus.ACTIVE));
			final var runtime = configured._decisionEngine.find(entry.profileId()).orElse(null);
			final String prefix = "census" + (++count) + ".";
			result.put(prefix + "profileId", Long.toString(entry.profileId()));
			result.put(prefix + "objectId", Integer.toString(player.getObjectId()));
			result.put(prefix + "materializedAtNanos", Long.toString(entry.materializedAtNanos()));
			result.put(prefix + "materializationState", entry.state().name());
			result.put(prefix + "actionAdmissionOpen", Boolean.toString(entry.actionAdmissionOpen()));
			result.put(prefix + "admittedActionCount", Integer.toString(entry.admittedActionCount()));
			result.put(prefix + "cleanupPhase", entry.cleanupPhase().name());
			result.put(prefix + "cleanupFailurePhase", entry.cleanupFailurePhase().name());
			result.put(prefix + "cleanupFailureClass", entry.cleanupFailureClass());
			result.put(prefix + "cleanupFailureMessage", entry.cleanupFailureMessage());
			result.put(prefix + "cleanupFailureSequence", Long.toString(entry.cleanupFailureSequence()));
			result.put(prefix + "cleanupFailureAdmittedActionCount", Integer.toString(entry.cleanupFailureAdmittedActionCount()));
			result.put(prefix + "playerRetained", Boolean.toString(entry.playerRetained()));
			result.put(prefix + "identityLeaseRetained", Boolean.toString(entry.identityLeaseRetained()));
			result.put(prefix + "outboundAttached", Boolean.toString(entry.outboundAttached()));
			result.put(prefix + "worldPresent", Boolean.toString(entry.worldPresent()));
			result.put(prefix + "pendingOwnedStore", Boolean.toString(player.hasPendingOwnedStore()));
			result.put(prefix + "goalId", goal == null ? "0" : Long.toString(goal.goalId()));
			result.put(prefix + "goalRevision", goal == null ? "0" : Long.toString(goal.revision()));
			result.put(prefix + "runtimeGoalRevision", runtime == null ? "0" : Long.toString(runtime.goalRevision()));
			result.put(prefix + "currentActionGuard", backgroundStatus != PhantomBackgroundService.ServiceState.RUNNING ? "BACKGROUND_SERVICE_" + backgroundStatus : !entry.actionAdmissionOpen() ? "ACTION_ADMISSION_CLOSED" : !player.hasHeadlessOutboundSession() ? "OUTBOUND_MISSING" : player.isDead() ? "DEAD" : player.hasPendingOwnedStore() ? "OWNED_STORE_PENDING" : goal == null ? "GOAL_ABSENT" : goal.status() != PhantomGoalStatus.ACTIVE ? "GOAL_NOT_ACTIVE" : runtime == null ? "DECISION_ABSENT" : ((runtime.goalId() != goal.goalId()) || (runtime.goalRevision() != goal.revision()) || (runtime.goalStatus() != PhantomGoalStatus.ACTIVE)) ? "DECISION_GOAL_MISMATCH" : "COMMON_GUARDS_CLEAR");
			result.put(prefix + "hp", Double.toString(player.getCurrentHp()));
			result.put(prefix + "maxHp", Double.toString(player.getMaxHp()));
			result.put(prefix + "nativeAttackBy", player.getAttackByList().stream().map(value -> Integer.toString(value.getObjectId())).sorted().limit(8).collect(java.util.stream.Collectors.joining(",")));
			result.put(prefix + "level", Integer.toString(player.getLevel()));
			result.put(prefix + "npcId", spec == null ? "0" : Integer.toString(spec.npcId()));
			result.put(prefix + "anchor", spec == null ? "" : spec.anchorId());
			result.put(prefix + "goalStatus", goal == null ? "NONE" : goal.status().name());
			result.put(prefix + "runtimeReason", runtime == null ? "runtime.absent" : runtime.reasonKey());
			result.put(prefix + "travelReason", configured._visibleFarmTravel == null ? "" : configured._visibleFarmTravel.reason(entry.profileId()));
			final var travelFailure = configured._visibleFarmTravel == null ? null : configured._visibleFarmTravel.lastFailure(entry.profileId());
			result.put(prefix + "travelFailureReason", travelFailure == null ? "" : travelFailure.reason());
			result.put(prefix + "travelFailureSequence", travelFailure == null ? "0" : Long.toString(travelFailure.sequence()));
			result.put(prefix + "dead", Boolean.toString(player.isDead()));
			result.put(prefix + "moving", Boolean.toString(player.isMoving()));
			result.put(prefix + "attacking", Boolean.toString(player.isAttackingNow()));
			result.put(prefix + "casting", Boolean.toString(player.isCastingNow()));
			result.put(prefix + "autoPlay", Boolean.toString(player.isAutoPlaying()));
			result.put(prefix + "party", Boolean.toString(player.isInParty()));
			result.put(prefix + "store", Boolean.toString(player.isInStoreMode()));
			result.put(prefix + "intention", player.getAI().getIntention().name());
			result.put(prefix + "x", Integer.toString(player.getX()));
			result.put(prefix + "y", Integer.toString(player.getY()));
			result.put(prefix + "z", Integer.toString(player.getZ()));
			result.put(prefix + "targetObjectId", Integer.toString(player.getTarget() == null ? 0 : player.getTarget().getObjectId()));
			result.put(prefix + "targetMonsterAlive", Boolean.toString((player.getTarget() instanceof org.l2jmobius.gameserver.model.actor.Attackable attackable) && attackable.isMonster() && !attackable.isDead()));
			final boolean ordinaryEligible = (goal != null) && (goal.status() == PhantomGoalStatus.ACTIVE) && !player.isDead() && !player.isInParty() && !player.isInStoreMode();
			if (ordinaryEligible) { eligible++; }
			result.put(prefix + "eligible", Boolean.toString(ordinaryEligible));
			final int[] targets = new int[10];
			World.getInstance().forEachVisibleObjectInRange(player, org.l2jmobius.gameserver.model.actor.Npc.class, org.l2jmobius.gameserver.config.custom.AutoPlayConfig.AUTO_PLAY_LONG_RANGE, npc ->
			{
				if ((spec == null) || (npc.getId() != spec.npcId())) { return; }
				targets[2]++;
				if (npc.isAlikeDead()) { targets[3]++; return; }
				if (npc.isInvul()) { targets[4]++; return; }
				if (!npc.isTargetable() || !npc.isShowName()) { targets[5]++; return; }
				if (!npc.isMonster() || npc.isRaid() || !npc.isAutoAttackable(player) || (npc.getInstanceId() != player.getInstanceId()) || (Math.abs((long) player.getZ() - npc.getZ()) >= 800)) { targets[6]++; return; }
				if (player.getAutoPlaySettings().isRespectfulHunting() && (npc.getTarget() != null) && (npc.getTarget() != player) && !(player.hasSummon() && (player.getSummon().getObjectId() == npc.getTarget().getObjectId()))) { targets[9]++; return; }
				if (!org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canSeeTarget(player, npc)) { targets[7]++; return; }
				if (!org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), npc.getX(), npc.getY(), npc.getZ(), player.getInstanceId())) { targets[8]++; return; }
				{
					targets[1]++;
					if (Math.hypot((long) npc.getX() - player.getX(), (long) npc.getY() - player.getY()) <= org.l2jmobius.gameserver.config.custom.AutoPlayConfig.AUTO_PLAY_SHORT_RANGE)
					{
						targets[0]++;
					}
				}
			});
			result.put(prefix + "shortTargets", Integer.toString(targets[0]));
			result.put(prefix + "longTargets", Integer.toString(targets[1]));
			result.put(prefix + "targetRejections", "exact=" + targets[2] + ",dead=" + targets[3] + ",invulnerable=" + targets[4] + ",untargetable=" + targets[5] + ",notAttackable=" + targets[6] + ",noLos=" + targets[7] + ",noForwardPath=" + targets[8] + ",respectful=" + targets[9]);
			final var pvp = configured._combatService == null ? null : configured._combatService.observePvp(entry.profileId(), java.util.List.of(human.getObjectId()), 8, 4).orElse(null);
			final var observedHuman = pvp == null ? null : pvp.targets().stream().filter(value -> value.target().objectId() == human.getObjectId()).findFirst().orElse(null);
			result.put(prefix + "pvpHumanContext", observedHuman == null ? "UNAVAILABLE" : "actualAttacker=" + observedHuman.actualAttacker() + ",canonicalAllowed=" + observedHuman.canonicalContextAllowed() + ",invulnerable=" + observedHuman.target().invulnerable() + ",peace=" + observedHuman.target().peaceRestricted() + ",autoAttackable=" + observedHuman.target().autoAttackable() + ",reachable=" + observedHuman.target().reachable());
			result.put(prefix + "idleReason", player.isDead() ? "DEATH_RECOVERY" : player.isInParty() || player.isInStoreMode() ? "NATIVE_OWNER" : !ordinaryEligible ? "NO_ACTIVE_FARM" : player.isMoving() ? "NATIVE_MOVEMENT" : player.isAttackingNow() || player.isCastingNow() ? "NATIVE_ACTION" : targets[1] == 0 && player.isAutoPlaying() ? "TARGET_DEFICIT" : "ACTIVE_IDLE");
			if (censusPageBudgetExceeded(result))
			{
				result.keySet().removeIf(name -> name.startsWith(prefix));
				count--;
				if (ordinaryEligible) { eligible--; }
				nextProfileId = lastIncludedProfileId;
				break;
			}
			lastIncludedProfileId = entry.profileId();
			if (count >= Math.min(24, configured._settings.maxMaterializedPhantoms()))
			{
				nextProfileId = entry.profileId();
				break;
			}
		}
		result.put("censusCount", Integer.toString(count));
		result.put("censusEligible", Integer.toString(eligible));
		result.put("censusNextProfileId", Long.toString(nextProfileId));
		return Map.copyOf(result);
	}

	/** Reserve 16 KiB for the existing Pilot envelope, with page metadata and XML escaping counted. */
	private static boolean censusPageBudgetExceeded(Map<String, String> fields)
	{
		int bytes = 256;
		for (var field : fields.entrySet())
		{
			bytes += field.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 4;
			final String value = field.getValue();
			bytes += value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
			for (int index = 0; index < value.length(); index++)
			{
				bytes += switch (value.charAt(index))
				{
					case '&' -> 4;
					case '"', '\'' -> 5;
					case '<', '>' -> 3;
					default -> 0;
				};
			}
			if (bytes > 48 * 1024) { return true; }
		}
		return false;
	}

	/** Read-only naturally materialized target already visible to the Pilot actor. */
	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestVisibleMaterializedTarget(Player human)
	{
		Objects.requireNonNull(human, "Human actor must not be null.");
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._state != State.RUNNING) || (configured._populationManager == null) || (configured._topologyService == null) || (configured._materializationService == null))
		{
			return java.util.Optional.empty();
		}
		final java.util.Set<Integer> visible = new java.util.HashSet<>();
		World.getInstance().forEachVisibleObject(human, Player.class, target ->
		{
			if (target.isOnline() && target.isVisibleFor(human))
			{
				visible.add(target.getObjectId());
			}
		});
		if (visible.isEmpty())
		{
			return java.util.Optional.empty();
		}
		final PhantomTopologyPoint point = new PhantomTopologyPoint(human.getX(), human.getY(), human.getZ(), human.getInstanceId());
		return configured._materializationService.snapshot().materializations().stream()
			.filter(entry -> entry.worldPresent() && (entry.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE) && visible.contains(entry.characterObjectId()))
			.map(entry -> configured._topologyService.findProfile(entry.profileId()))
			.flatMap(java.util.Optional::stream)
			.filter(profile -> profile.resolved() && (profile.point() != null) && (profile.point().instanceId() == point.instanceId()))
			.min(java.util.Comparator.comparingLong((org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot profile) -> point.distanceSquared2D(profile.point())).thenComparingLong(org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot::profileId))
			.map(profile -> new OperatorLocalityTarget(profile.profileId(), profile.point(), profile.nodeId(), profile.topologyGeneration()));
	}

	/** Read-only canonical target for the one-shot LocalPlay human relocation proof. */
	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestLocalityTarget(PhantomTopologyPoint human)
	{
		Objects.requireNonNull(human, "Human point must not be null.");
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._state != State.RUNNING) || (configured._populationManager == null) || (configured._topologyService == null))
		{
			return java.util.Optional.empty();
		}
		return PhantomLocalProofSelector.nearest(human, configured._topologyService.listProfiles(), configured._populationManager::admissionProfile, profileId -> configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE)
			.map(profile -> new OperatorLocalityTarget(profile.profileId(), profile.point(), profile.nodeId(), profile.topologyGeneration()));
	}

	/** Read-only READY target selection for a bounded human-locality envelope proof. */
	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestReadyLocalityTarget(PhantomTopologyPoint human, long maxDistanceSquared2D)
	{
		return operatorNearestReadyLocalityTarget(human, maxDistanceSquared2D, 0);
	}

	public static synchronized java.util.Optional<OperatorLocalityTarget> operatorNearestReadyLocalityTarget(PhantomTopologyPoint human, long maxDistanceSquared2D, long afterProfileId)
	{
		Objects.requireNonNull(human, "Human point must not be null.");
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._state != State.RUNNING) || (configured._populationManager == null) || (configured._topologyService == null))
		{
			return java.util.Optional.empty();
		}
		return PhantomLocalProofSelector.nearestReadyWithin(human, configured._topologyService.listProfiles(), configured._populationManager::admissionProfile, profileId -> configured._populationManager.presence().state(profileId) == PhantomPresenceRegistry.Presence.AVAILABLE, maxDistanceSquared2D, afterProfileId)
			.map(profile -> new OperatorLocalityTarget(profile.profileId(), profile.point(), profile.nodeId(), profile.topologyGeneration()));
	}

	/** Calendar-based preparation count; technical readiness is deliberately not a selection filter. */
	public static synchronized int operatorNaturalCohortSize(PhantomTopologyPoint human, java.time.Instant until)
	{
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._populationManager == null) || (configured._topologyService == null)) { return 0; }
		return (int) configured._topologyService.listProfiles().stream()
			.filter(profile -> (profile.point() != null) && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(human, profile.point()))
			.filter(profile -> configured._populationManager.admissionProfile(profile.profileId()).filter(state -> (state.populationState() == org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State.READY) && state.calendarOnline() && state.nextBoundary().isAfter(until)).isPresent())
			.filter(profile -> configured._populationManager.presence().state(profile.profileId()) == PhantomPresenceRegistry.Presence.AVAILABLE)
			.limit(configured._settings.maxMaterializedPhantoms()).count();
	}

	public static synchronized OperatorEconomicAudit operatorEconomicAudit(long profileId)
	{
		if (profileId <= 0)
		{
			return new OperatorEconomicAudit(EconomicAuditCode.INVALID, profileId, null);
		}
		final PhantomSystem configured = _configuredInstance;
		if (configured == null)
		{
			return new OperatorEconomicAudit(EconomicAuditCode.RUNTIME_NOT_CONFIGURED, profileId, null);
		}
		if ((configured._state != State.RUNNING) || (configured._economicAuditView == null))
		{
			return new OperatorEconomicAudit(EconomicAuditCode.ECONOMY_UNAVAILABLE, profileId, null);
		}
		try
		{
			final PhantomEconomicAuditView.Snapshot snapshot = configured._economicAuditView.read(profileId);
			return new OperatorEconomicAudit(snapshot.empty() ? EconomicAuditCode.EMPTY : EconomicAuditCode.AVAILABLE, profileId, snapshot);
		}
		catch (RuntimeException exception)
		{
			return new OperatorEconomicAudit(EconomicAuditCode.READ_FAILED, profileId, null);
		}
	}

	public static synchronized OperatorReplayResult operatorReplayCapture()
	{
		final PhantomSystem configured = _configuredInstance;
		if ((configured == null) || (configured._state != State.RUNNING))
		{
			return OperatorReplayResult.empty(OperatorReplayCode.RUNTIME_NOT_CONFIGURED);
		}
		final PhantomSelectedDecisionTrace.CaptureResult capture = configured._selectedDecisionTrace.captureReplay();
		if (capture.status() != PhantomSelectedDecisionTrace.CaptureStatus.CAPTURED)
		{
			return OperatorReplayResult.empty(switch (capture.status())
			{
				case TRACE_DISABLED -> OperatorReplayCode.TRACE_DISABLED;
				case NO_SELECTION -> OperatorReplayCode.NO_SELECTION;
				case NO_HISTORY -> OperatorReplayCode.NO_HISTORY;
				case CAPTURED -> throw new IllegalStateException("Captured replay bundle is missing.");
			});
		}
		try
		{
			final PhantomDecisionReplay.Bundle candidate = capture.bundle();
			final String digest = PhantomDecisionReplay.digest(candidate);
			_operatorReplayBundle = candidate;
			return new OperatorReplayResult(OperatorReplayCode.CAPTURED, candidate.profileId(), candidate.frames().size(), digest, null);
		}
		catch (RuntimeException exception)
		{
			return OperatorReplayResult.empty(OperatorReplayCode.CAPTURE_FAILED);
		}
	}

	public static synchronized OperatorReplayResult operatorReplayRun()
	{
		final PhantomDecisionReplay.Bundle frozen = _operatorReplayBundle;
		if (frozen == null)
		{
			return OperatorReplayResult.empty(OperatorReplayCode.NO_CAPTURE);
		}
		final PhantomDecisionReplay.ReplayResult replay = PhantomDecisionReplay.replay(frozen);
		return new OperatorReplayResult(replay.status() == PhantomDecisionReplay.ReplayStatus.PASS ? OperatorReplayCode.REPLAY_PASS : OperatorReplayCode.REPLAY_FAIL, replay.profileId(), replay.frameCount(), replay.digest(), replay);
	}

	public static synchronized OperatorReplayResult operatorReplayClear()
	{
		if (_operatorReplayBundle == null)
		{
			return OperatorReplayResult.empty(OperatorReplayCode.NO_CAPTURE);
		}
		_operatorReplayBundle = null;
		return OperatorReplayResult.empty(OperatorReplayCode.CLEARED);
	}

	public static synchronized PhantomSelectedDecisionTrace.SelectionStatus selectOperatorTrace(long profileId)
	{
		final PhantomSystem configured = _configuredInstance;
		if (configured == null)
		{
			return PhantomPlayersConfig.settings().diagnosticsEnabled() ? PhantomSelectedDecisionTrace.SelectionStatus.NOT_ATTACHED : PhantomSelectedDecisionTrace.SelectionStatus.DISABLED;
		}
		if (!configured._selectedDecisionTrace.snapshot().enabled())
		{
			return PhantomSelectedDecisionTrace.SelectionStatus.DISABLED;
		}
		final PhantomDecisionEngine engine = configured._decisionEngine;
		return engine == null ? PhantomSelectedDecisionTrace.SelectionStatus.NOT_ATTACHED : configured._selectedDecisionTrace.select(profileId, engine.find(profileId).orElse(null));
	}

	public static synchronized PhantomSelectedDecisionTrace.Snapshot clearOperatorTrace()
	{
		if (_configuredInstance == null)
		{
			return PhantomSelectedDecisionTrace.Snapshot.disabled();
		}
		_configuredInstance._selectedDecisionTrace.clear();
		return _configuredInstance._selectedDecisionTrace.snapshot();
	}

	public static synchronized boolean isMaterializationManaged(Player player)
	{
		if ((player == null) || !player.hasHeadlessOutboundSession())
		{
			return false;
		}
		if (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) != OwnerKind.PHANTOM)
		{
			return false;
		}
		final PhantomMaterializationService service = configuredMaterializationService();
		return (service != null) && service.ownsCharacterObjectId(player.getObjectId());
	}

	public static synchronized ConfiguredShutdownSnapshot configuredShutdownSnapshot()
	{
		final PhantomSystem configured = _configuredInstance;
		if (configured == null)
		{
			return ConfiguredShutdownSnapshot.notConfigured();
		}
		ServiceState materializationServiceState = null;
		int retainedMaterializationEntries = 0;
		if (configured._materializationService != null)
		{
			final PhantomMaterializationService.ShutdownSnapshot materializationSnapshot = configured._materializationService.shutdownSnapshot();
			materializationServiceState = materializationSnapshot.state();
			retainedMaterializationEntries = materializationSnapshot.retainedEntries();
		}
		PhantomNavigationService.ServiceState navigationState = null;
		int navigationActiveRequests = 0;
		int navigationQueuedRequests = 0;
		int navigationWorkers = 0;
		if (configured._navigationService != null)
		{
			final PhantomNavigationService.ServiceSnapshot navigationSnapshot = configured._navigationService.snapshot();
			navigationState = navigationSnapshot.state();
			navigationActiveRequests = navigationSnapshot.activeRequests();
			navigationQueuedRequests = navigationSnapshot.queuedRequests();
			navigationWorkers = navigationSnapshot.currentWorkers();
		}
		PhantomTopologyService.State topologyState = null;
		int topologyRegisteredProfiles = 0;
		int topologyEventsInFlight = 0;
		long topologyGeneration = 0;
		if (configured._topologyService != null)
		{
			final PhantomTopologyService.ServiceSnapshot topologySnapshot = configured._topologyService.snapshot();
			topologyState = topologySnapshot.state();
			topologyRegisteredProfiles = topologySnapshot.registeredProfiles();
			topologyEventsInFlight = topologySnapshot.eventsInFlight();
			topologyGeneration = topologySnapshot.generation();
		}
		final PhantomGameKnowledgeService.State knowledgeState = configured._gameKnowledgeService == null ? null : configured._gameKnowledgeService.snapshot().state();
		PhantomCombatService.ServiceState combatState = null;
		int combatActiveSessions = 0;
		int combatTerminalSessions = 0;
		int combatQueuedSessions = 0;
		int combatWorkers = 0;
		int combatActorLeases = 0;
		if (configured._combatService != null)
		{
			final PhantomCombatService.ServiceSnapshot combatSnapshot = configured._combatService.snapshot();
			combatState = combatSnapshot.state();
			combatActiveSessions = combatSnapshot.activeSessions();
			combatTerminalSessions = combatSnapshot.terminalSessions();
			combatQueuedSessions = combatSnapshot.queuedSessions();
			combatWorkers = combatSnapshot.currentWorkers();
			combatActorLeases = combatSnapshot.actorLeases();
		}
		PhantomProgressionService.State progressionState = null;
		String progressionCatalogHash = "none";
		int progressionOperations = 0;
		int progressionActorLeases = 0;
		if (configured._progressionService != null)
		{
			final PhantomProgressionService.ServiceSnapshot progressionSnapshot = configured._progressionService.snapshot();
			progressionState = progressionSnapshot.state();
			progressionCatalogHash = progressionSnapshot.combinedHash();
			progressionOperations = progressionSnapshot.currentOperations();
			progressionActorLeases = progressionSnapshot.currentActorLeases();
		}
		final PhantomPopulationManager.Snapshot populationSnapshot = configured._populationManager == null ? PhantomPopulationManager.Snapshot.inactive() : configured._populationManager.snapshot();
		final PhantomSocialService.Snapshot socialSnapshot = configured._socialService == null ? PhantomSocialService.Snapshot.inactive() : configured._socialService.snapshot();
		final PhantomConversationService.Snapshot conversationSnapshot = configured._conversationService == null ? PhantomConversationService.Snapshot.inactive() : configured._conversationService.snapshot();
		final ChatObservationService.Snapshot chatSnapshot = ChatObservationService.getInstance().snapshot();
		return new ConfiguredShutdownSnapshot(true, configured._state, materializationServiceState, retainedMaterializationEntries, navigationState, navigationActiveRequests, navigationQueuedRequests, navigationWorkers, topologyState, topologyRegisteredProfiles, topologyEventsInFlight, topologyGeneration, knowledgeState, progressionState, progressionCatalogHash, progressionOperations, progressionActorLeases, combatState, combatActiveSessions, combatTerminalSessions, combatQueuedSessions, combatWorkers, combatActorLeases, populationSnapshot, socialSnapshot.state(), socialSnapshot.catalogHash(), socialSnapshot.cacheEntries(), socialSnapshot.operationClaims(), socialSnapshot.writeClaims(), conversationSnapshot.state(), conversationSnapshot.ingressSize(), conversationSnapshot.openBatches(), conversationSnapshot.operationClaims(), conversationSnapshot.persistenceClaims(), chatSnapshot.observerRegistered());
	}

	static synchronized PhantomMaterializationService configuredMaterializationService()
	{
		return _configuredInstance == null ? null : _configuredInstance._materializationService;
	}

	static synchronized PhantomScheduler configuredScheduler()
	{
		return _configuredInstance == null ? null : _configuredInstance._scheduler;
	}

	static synchronized PhantomSelectedDecisionTrace configuredSelectedTraceForTesting()
	{
		return _configuredInstance == null ? null : _configuredInstance._selectedDecisionTrace;
	}

	static synchronized void configureEconomicAuditForTesting(PhantomEconomicAuditView view, State state)
	{
		if (_configuredInstance != null)
		{
			throw new IllegalStateException("A configured PhantomSystem instance already exists.");
		}
		final PhantomSystem configured = new PhantomSystem(new PhantomPlayersConfig.Settings(true, false, 1), false);
		configured._economicAuditView = view;
		configured._state = Objects.requireNonNull(state);
		_configuredInstance = configured;
	}

	static synchronized void resetEconomicAuditForTesting()
	{
		_configuredInstance = null;
		_operatorMode = OperatorMode.AUTO;
	}

	static synchronized void configureOperatorRuntimeForTesting(boolean failShutdown)
	{
		configureOperatorRuntimeForTesting(failShutdown, false);
	}

	static synchronized void configureOperatorReplayForTesting()
	{
		configureOperatorRuntimeForTesting(false, true);
	}

	private static void configureOperatorRuntimeForTesting(boolean failShutdown, boolean diagnosticsEnabled)
	{
		if (_configuredInstance != null)
		{
			throw new IllegalStateException("A configured PhantomSystem instance already exists.");
		}
		_operatorMode = OperatorMode.AUTO;
		final PhantomSystem configured = new PhantomSystem(new PhantomPlayersConfig.Settings(true, diagnosticsEnabled, 1), false);
		configured._scheduler = new PhantomScheduler(
			1,
			configured._settings.schedulerPulseMillis(),
			configured._settings.schedulerProfilesPerPulse(),
			org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy.productionDefaults(configured._settings.schedulerPulseMillis()),
			System::nanoTime,
			(pulse, period) -> null,
			false,
			configured._metrics,
			configured._trace,
			PhantomActivityMaterializationPort.noop(),
			PhantomActivityWorkSink.noop());
		if (!configured._scheduler.start())
		{
			throw new IllegalStateException("The operator test Phantom scheduler could not start.");
		}
		configured._shutdownFailureForTesting = failShutdown;
		configured._metrics.recordLifecycleStart();
		configured._state = State.RUNNING;
		_configuredInstance = configured;
	}

	static synchronized void releaseOperatorShutdownFailureForTesting()
	{
		if (_configuredInstance != null)
		{
			_configuredInstance._shutdownFailureForTesting = false;
		}
	}

	static synchronized void injectConfiguredShutdownFailureForTesting()
	{
		if ((_configuredInstance == null) || (_configuredInstance._state != State.RUNNING))
		{
			throw new IllegalStateException("A running configured PhantomSystem instance is required.");
		}
		_configuredInstance._shutdownFailureForTesting = true;
	}

	static synchronized void resetOperatorModeForTesting()
	{
		if (_configuredInstance != null)
		{
			throw new IllegalStateException("Cannot reset operator mode while a configured instance exists.");
		}
		_operatorMode = OperatorMode.AUTO;
		_populationResetService = null;
	}

	static synchronized void configureForTesting(PhantomMaterializationService materializationService)
	{
		Objects.requireNonNull(materializationService, "materializationService");
		if (_configuredInstance != null)
		{
			throw new IllegalStateException("A configured PhantomSystem instance already exists.");
		}
		final PhantomMaterializationService.ServiceSnapshot serviceSnapshot = materializationService.snapshot();
		if (serviceSnapshot.state() != ServiceState.RUNNING)
		{
			throw new IllegalArgumentException("The test materialization service must be running.");
		}

		final PhantomPlayersConfig.Settings settings = new PhantomPlayersConfig.Settings(true, false, serviceSnapshot.maximumMaterialized());
		final PhantomSystem configured = new PhantomSystem(settings, false);
		configured._scheduler = configured.createScheduler(PhantomActivityMaterializationPort.noop());
		configured.startNavigationForTesting();
		configured.startTopologyForTesting();
		configured.startKnowledgeForTesting();
		configured.startCombatForTesting();
		if (!configured._scheduler.start())
		{
			throw new IllegalStateException("The test Phantom scheduler could not start.");
		}
		configured._materializationService = materializationService;
		configured._metrics.recordLifecycleStart();
		configured._state = State.RUNNING;
		_configuredInstance = configured;
	}

	static synchronized void configureForTesting(PhantomMaterializationService materializationService, PhantomScheduler scheduler)
	{
		configureForTesting(materializationService, scheduler, null);
	}

	static synchronized void configureForTesting(PhantomMaterializationService materializationService, PhantomScheduler scheduler, PhantomNavigationService navigationService)
	{
		Objects.requireNonNull(materializationService, "materializationService");
		Objects.requireNonNull(scheduler, "scheduler");
		if (_configuredInstance != null)
		{
			throw new IllegalStateException("A configured PhantomSystem instance already exists.");
		}
		final PhantomMaterializationService.ServiceSnapshot serviceSnapshot = materializationService.snapshot();
		if (serviceSnapshot.state() != ServiceState.RUNNING)
		{
			throw new IllegalArgumentException("The test materialization service must be running.");
		}
		if (scheduler.snapshot().state() != PhantomScheduler.SchedulerState.RUNNING)
		{
			throw new IllegalArgumentException("The test Phantom scheduler must be running.");
		}

		final PhantomPlayersConfig.Settings settings = new PhantomPlayersConfig.Settings(true, false, serviceSnapshot.maximumMaterialized());
		final PhantomSystem configured = new PhantomSystem(settings, false);
		configured._scheduler = scheduler;
		if (navigationService == null)
		{
			configured.startNavigationForTesting();
		}
		else
		{
			if (navigationService.snapshot().state() != PhantomNavigationService.ServiceState.RUNNING)
			{
				throw new IllegalArgumentException("The test Phantom navigation service must be running.");
			}
			configured._navigationService = navigationService;
		}
		configured.startTopologyForTesting();
		configured.startKnowledgeForTesting();
		configured.startCombatForTesting();
		configured._materializationService = materializationService;
		configured._metrics.recordLifecycleStart();
		configured._state = State.RUNNING;
		_configuredInstance = configured;
	}

	public enum OperatorMode
	{
		AUTO,
		ENABLED,
		DRAINED,
		DISABLED
	}

	public enum OperatorControlCode
	{
		STARTED,
		ALREADY_RUNNING,
		CONFIG_DISABLED,
		OWNER_BUSY,
		START_FAILED,
		DRAINED,
		ALREADY_DRAINED,
		DISABLED,
		ALREADY_DISABLED,
		SHUTDOWN_FAILED
	}

	public enum State
	{
		NEW,
		DISABLED,
		RUNNING,
		FAILED,
		STOPPED
	}

	private PhantomScheduler createScheduler(PhantomActivityMaterializationPort materializationPort)
	{
		return createScheduler(materializationPort, PhantomActivityWorkSink.noop());
	}

	private PhantomMaterializationRetentionPolicy.Facts retentionFacts(long profileId)
	{
		final var snapshot = _materializationService.find(profileId).orElse(null);
		final var object = snapshot == null ? null : World.getInstance().findObject(snapshot.characterObjectId());
		if ((snapshot == null) || !snapshot.worldPresent() || !(object instanceof Player player))
		{
			return new PhantomMaterializationRetentionPolicy.Facts(false, false, false, false, false);
		}
		final var region = World.getInstance().getRegion(player);
		final boolean visible = World.getInstance().getPlayers().stream().anyMatch(human -> human.isOnline() && !human.hasHeadlessOutboundSession() && (human.getInstanceId() == player.getInstanceId()) && (World.getInstance().getRegion(human) != null) && World.getInstance().getRegion(human).isSurroundingRegion(region));
		return PhantomMaterializationRetentionPolicy.Facts.nativeFacts(player, _humanLocality.isLocal(profileId), visible, snapshot.admittedActionCount());
	}

	private Map<Long, PhantomTopologyPoint> liveMaterializedPoints()
	{
		if (_materializationService == null)
		{
			return Map.of();
		}
		final Map<Long, PhantomTopologyPoint> points = new HashMap<>();
		for (var snapshot : _materializationService.snapshot().materializations())
		{
			if ((snapshot.state() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE) || !snapshot.worldPresent())
			{
				continue;
			}
			final Player player = World.getInstance().getPlayer(snapshot.characterObjectId());
			if ((player != null) && player.isOnline() && player.hasHeadlessOutboundSession())
			{
				points.put(snapshot.profileId(), new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()));
			}
		}
		return Map.copyOf(points);
	}

	private PhantomScheduler createScheduler(PhantomActivityMaterializationPort materializationPort, PhantomActivityWorkSink workSink)
	{
		return new PhantomScheduler(
			_settings.maxScheduledPhantomProfiles(),
			_settings.schedulerPulseMillis(),
			_settings.schedulerProfilesPerPulse(),
			_metrics,
			_trace,
			materializationPort,
			workSink);
	}

	private void startNavigationForTesting()
	{
		_navigationService = new PhantomNavigationService(_metrics);
		if (!_navigationService.start())
		{
			throw new IllegalStateException("The test Phantom navigation service could not start.");
		}
	}

	private void startTopologyForTesting()
	{
		_topologyService = PhantomTopologyService.inertForTesting(new PhantomSchedulerRelevanceSignalPort(_scheduler), _settings.maxScheduledPhantomProfiles());
		if (!_topologyService.start())
		{
			throw new IllegalStateException("The test Phantom topology service could not start.");
		}
	}

	private void startKnowledgeForTesting()
	{
		_gameKnowledgeService = PhantomGameKnowledgeService.inertForTesting(_topologyService.query().snapshot().canonicalHash());
		if (!_gameKnowledgeService.start())
		{
			throw new IllegalStateException("The test Phantom Game Knowledge service could not start.");
		}
	}

	private void startCombatForTesting()
	{
		_combatService = new PhantomCombatService(PhantomCombatBackend.inert(), new PhantomCombatCapabilityResolver(_ -> java.util.List.of()), PhantomCombatPolicy.productionDefaults(_settings.maxScheduledPhantomProfiles()));
		_combatService.start();
	}

	public record Snapshot(State state, PhantomPlayersConfig.Settings settings, PhantomScheduler.SchedulerSnapshot scheduler, PhantomDecisionEngine.EngineSnapshot decision, PhantomNavigationService.ServiceSnapshot navigation, PhantomTopologyService.ServiceSnapshot topology, PhantomGameKnowledgeService.ServiceSnapshot gameKnowledge, PhantomSemanticUnderstandingService.Snapshot semanticUnderstanding, PhantomProgressionService.ServiceSnapshot progression, PhantomCombatService.ServiceSnapshot combat, PhantomBackgroundService.Snapshot background, PhantomPopulationManager.Snapshot population, PhantomSocialService.Snapshot social, PhantomConversationService.Snapshot conversation, PhantomConversationExecutionService.Snapshot conversationExecution, ChatObservationService.Snapshot chatObservation, PhantomMetrics.Snapshot metrics, PhantomDiagnosticTrace.Snapshot trace, PhantomSelectedDecisionTrace.Snapshot selectedTrace)
	{
	}

	public record OperatorControlResult(OperatorControlCode code, OperatorMode desiredMode, boolean desiredRuntimeEnabled, boolean runtimeConfigured, State runtimeState)
	{
	}

	public enum OperatorReplayCode
	{
		CAPTURED,
		REPLAY_PASS,
		REPLAY_FAIL,
		CLEARED,
		NO_CAPTURE,
		RUNTIME_NOT_CONFIGURED,
		TRACE_DISABLED,
		NO_SELECTION,
		NO_HISTORY,
		CAPTURE_FAILED
	}

	public record OperatorReplayResult(OperatorReplayCode code, long profileId, int frameCount, String digest, PhantomDecisionReplay.ReplayResult replay)
	{
		public OperatorReplayResult
		{
			Objects.requireNonNull(code);
		}

		private static OperatorReplayResult empty(OperatorReplayCode code)
		{
			return new OperatorReplayResult(code, 0, 0, null, null);
		}
	}

	public enum EconomicAuditCode
	{
		AVAILABLE,
		EMPTY,
		INVALID,
		RUNTIME_NOT_CONFIGURED,
		ECONOMY_UNAVAILABLE,
		READ_FAILED
	}

	public record OperatorEconomicAudit(EconomicAuditCode code, long profileId, PhantomEconomicAuditView.Snapshot snapshot)
	{
		public OperatorEconomicAudit
		{
			Objects.requireNonNull(code);
			final boolean available = (code == EconomicAuditCode.AVAILABLE) || (code == EconomicAuditCode.EMPTY);
			if (available != (snapshot != null))
			{
				throw new IllegalArgumentException("Economic audit status and snapshot do not match.");
			}
		}
	}

	public record OperatorAdmissionProfile(PhantomPopulationManager.AdmissionProfileSnapshot admission, org.l2jmobius.gameserver.phantoms.activity.PhantomActivitySnapshot scheduler, PhantomMaterializationService.MaterializationSnapshot materialization, PhantomMaterializationService.ResultStatus lastMaterializationFailure, String busyReason, PhantomPopulationEcologyService.DueSnapshot readiness, boolean humanLocality, boolean nativeVisible, java.util.Set<PhantomMaterializationRetentionPolicy.Reason> retentionPins)
	{
	}

	public record OperatorLocalityTarget(long profileId, PhantomTopologyPoint committedPosition, String topologyNodeId, long topologyGeneration)
	{
	}

	/** The production locality supplier; bounded and independent of transport/identity kind. */
	public static java.util.List<PhantomTopologyPoint> onlineHumanPoints()
	{
		return World.getInstance().getPlayers().stream().filter(player -> player.isOnline() && !player.hasHeadlessOutboundSession()).limit(256).map(player -> new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId())).toList();
	}

	public record OperatorM1TargetSnapshot(long profileId, int objectId, long materializedAtNanos, long sampledAtNanos, PhantomTopologyPoint committedPosition, long committedSequence, PhantomTopologyPoint observedPosition, LocalPlayM1Observation.PositionSource positionSource, boolean worldPresent, boolean snapshotWorldPresent, String materializationState, boolean online, boolean visibleForHuman, boolean moving, boolean attacking, boolean casting, boolean autoPlay, int targetObjectId, boolean targetMonsterAlive)
	{
	}

	public record OperatorStatus(boolean configuredEnabled, boolean diagnosticsEnabled, OperatorMode operatorMode, boolean desiredRuntimeEnabled, boolean runtimeConfigured, State runtimeState, PhantomScheduler.SchedulerState schedulerState, PhantomDecisionEngine.State decisionState, long activeCurrent, long activePeak, java.util.List<Long> activityStateCounts, PhantomActivityOverloadLevel overloadLevel, PhantomActivityOverloadLevel peakOverloadLevel, int queueReady, int queueDue, int queueCapacity, long queueAccepted, long queueRejected, long shutdownFailures, PhantomPopulationEcologyService.Snapshot ecology, java.util.Map<String, Integer> levelHistogram, PhantomSelectedDecisionTrace.Snapshot selectedTrace, PhantomPopulationManager.AdmissionSnapshot admission, long worldMaterialized, PhantomPresenceRegistry.Snapshot presence, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrySnapshot registry, String periodicOwner, int localSignaled)
	{
		public OperatorStatus
		{
			activityStateCounts = java.util.List.copyOf(activityStateCounts);
			levelHistogram = java.util.Map.copyOf(levelHistogram);
		}

		private static OperatorStatus notRunning(boolean configuredEnabled, boolean diagnosticsEnabled, OperatorMode operatorMode)
		{
			return new OperatorStatus(configuredEnabled, diagnosticsEnabled, operatorMode, PhantomSystem.desiredRuntimeEnabled(configuredEnabled, operatorMode), false, null, PhantomScheduler.SchedulerState.STOPPED, PhantomDecisionEngine.State.STOPPED, 0, 0, java.util.List.of(0L, 0L, 0L, 0L, 0L), PhantomActivityOverloadLevel.NORMAL, PhantomActivityOverloadLevel.NORMAL, 0, 0, 0, 0, 0, 0, PhantomPopulationEcologyService.Snapshot.disabled(), java.util.Map.of(), PhantomSelectedDecisionTrace.Snapshot.disabled(), PhantomPopulationManager.AdmissionSnapshot.inactive(), 0, new PhantomPresenceRegistry.Snapshot(0, 0, 0), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrySnapshot(0, 0, 0, 0, 0), "DISABLED_BY_CONFIG", 0);
		}

		private static OperatorStatus readFailure(boolean configuredEnabled, boolean diagnosticsEnabled, OperatorMode operatorMode, Snapshot snapshot, PhantomMetrics.Snapshot metrics, PhantomPopulationEcologyService.Snapshot ecology, PhantomPopulationManager.AdmissionSnapshot admission, long worldMaterialized, PhantomPresenceRegistry.Snapshot presence, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrySnapshot registry, String periodicOwner, int localSignaled)
		{
			return new OperatorStatus(configuredEnabled, diagnosticsEnabled, operatorMode, PhantomSystem.desiredRuntimeEnabled(configuredEnabled, operatorMode), true, snapshot.state(), snapshot.scheduler().state(), snapshot.decision().state(), metrics.activeCurrent(), metrics.activePeak(), metrics.activity().stateCounts(), snapshot.scheduler().overloadLevel(), snapshot.scheduler().peakOverloadLevel(), snapshot.scheduler().ready(), snapshot.scheduler().due(), snapshot.scheduler().capacity(), metrics.queueAccepted(), metrics.queueRejected(), metrics.shutdownFailures(), ecology, java.util.Map.of("UNAVAILABLE", Math.max(0, ecology.managed())), snapshot.selectedTrace(), admission, worldMaterialized, presence, registry, periodicOwner, localSignaled);
		}
	}

	private static boolean desiredRuntimeEnabled(boolean configuredEnabled, OperatorMode operatorMode)
	{
		return configuredEnabled && ((operatorMode == OperatorMode.AUTO) || (operatorMode == OperatorMode.ENABLED));
	}

	private void resumeRecoveredBackgroundGoal(long profileId, PhantomGoalStateStore goals)
	{
		final var runtime = _decisionEngine.find(profileId).orElse(null);
		if ((runtime == null) || (runtime.goalStatus() != org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.FAILED) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(runtime.goalType()))
		{
			return;
		}
		final var background = _backgroundService.acquisitionSnapshot(profileId).orElse(null);
		if ((background == null) || ((background.state() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY) && ((background.state() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.MATERIALIZED) || (background.vitals().currentHp() <= 0))))
		{
			return;
		}
		final var stored = goals.load(profileId).orElse(null);
		if ((stored == null) || (stored.goal().status() != org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.FAILED) || (stored.goal().goalId() != runtime.goalId()) || (stored.goal().revision() != runtime.goalRevision()))
		{
			return;
		}
		final PhantomGoal resumed = stored.goal().withStatus(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE);
		try
		{
			PhantomBackgroundGoalSpec.parse(resumed);
		}
		catch (IllegalArgumentException exception)
		{
			return;
		}
		_decisionEngine.setGoal(profileId, resumed);
	}

	private String ecologySafetyBlock(long profileId, PhantomGoalStateStore goals)
	{
		if ((_partyCoordinator != null) && (_partyCoordinator.committed(profileId) || _partyCoordinator.blocksBackground(profileId)))
		{
			return "party";
		}
		if ((_economyReservations != null) && _economyReservations.findActive(profileId).isPresent())
		{
			return "economy";
		}
		final String goalType = goals.load(profileId)
			.filter(stored -> stored.goal().status() == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE)
			.map(stored -> stored.goal().goalType())
			.orElse("");
		if (goalType.startsWith("party.") || goalType.startsWith("raid.") || goalType.startsWith("clan.") || goalType.startsWith("economy.") || goalType.startsWith("rift.") || goalType.startsWith("pvp."))
		{
			return "critical_goal";
		}
		return "";
	}

	public record ConfiguredShutdownSnapshot(boolean configured, State systemState, ServiceState materializationServiceState, int retainedMaterializationEntries, PhantomNavigationService.ServiceState navigationState, int navigationActiveRequests, int navigationQueuedRequests, int navigationWorkers, PhantomTopologyService.State topologyState, int topologyRegisteredProfiles, int topologyEventsInFlight, long topologyGeneration, PhantomGameKnowledgeService.State knowledgeState, PhantomProgressionService.State progressionState, String progressionCatalogHash, int progressionOperations, int progressionActorLeases, PhantomCombatService.ServiceState combatState, int combatActiveSessions, int combatTerminalSessions, int combatQueuedSessions, int combatWorkers, int combatActorLeases, PhantomPopulationManager.Snapshot population, PhantomSocialService.ServiceState socialState, String socialCatalogHash, int socialCacheEntries, int socialOperations, int socialWrites, PhantomConversationService.ServiceState conversationState, int conversationIngress, int conversationBatches, int conversationOperations, int conversationPersistence, boolean chatObserverRegistered)
	{
		public ConfiguredShutdownSnapshot(boolean configured, State systemState, ServiceState materializationServiceState, int retainedMaterializationEntries, PhantomNavigationService.ServiceState navigationState, int navigationActiveRequests, int navigationQueuedRequests, int navigationWorkers, PhantomTopologyService.State topologyState, int topologyRegisteredProfiles, int topologyEventsInFlight, long topologyGeneration, PhantomGameKnowledgeService.State knowledgeState, PhantomCombatService.ServiceState combatState, int combatActiveSessions, int combatTerminalSessions, int combatQueuedSessions, int combatWorkers, int combatActorLeases)
		{
			this(configured, systemState, materializationServiceState, retainedMaterializationEntries, navigationState, navigationActiveRequests, navigationQueuedRequests, navigationWorkers, topologyState, topologyRegisteredProfiles, topologyEventsInFlight, topologyGeneration, knowledgeState, null, "none", 0, 0, combatState, combatActiveSessions, combatTerminalSessions, combatQueuedSessions, combatWorkers, combatActorLeases, PhantomPopulationManager.Snapshot.inactive(), PhantomSocialService.ServiceState.STOPPED, "none", 0, 0, 0, PhantomConversationService.ServiceState.STOPPED, 0, 0, 0, 0, false);
		}

		private static ConfiguredShutdownSnapshot notConfigured()
		{
			return new ConfiguredShutdownSnapshot(false, null, null, 0, null, 0, 0, 0, null, 0, 0, 0, null, null, "none", 0, 0, null, 0, 0, 0, 0, 0, PhantomPopulationManager.Snapshot.inactive(), PhantomSocialService.ServiceState.STOPPED, "none", 0, 0, 0, PhantomConversationService.ServiceState.STOPPED, 0, 0, 0, 0, false);
		}
	}
}
