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
import java.util.Objects;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSinkBridge;
import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
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
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationPlanSink;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyDecision;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyParticipationPort;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ShutdownResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationDecision;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionStepHandlers;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/**
 * Lifecycle owner for the disabled-by-default Phantom World subsystem.
 */
public final class PhantomSystem
{
	public static final int TRACE_CAPACITY = 64;
	public static final int TRACE_SAMPLE_EVERY = 16;
	public static final long SOCIAL_PERSONALITY_SEED = 18001801L;

	private static PhantomSystem _configuredInstance;

	private final PhantomPlayersConfig.Settings _settings;
	private final PhantomMetrics _metrics;
	private PhantomScheduler _scheduler;
	private final PhantomDiagnosticTrace _trace;
	private final boolean _productionMaterialization;
	private PhantomMaterializationService _materializationService;
	private PhantomDecisionEngine _decisionEngine;
	private PhantomNavigationService _navigationService;
	private PhantomTopologyService _topologyService;
	private PhantomGameKnowledgeService _gameKnowledgeService;
	private PhantomSemanticUnderstandingService _semanticUnderstandingService;
	private PhantomProgressionService _progressionService;
	private PhantomCombatService _combatService;
	private PhantomCommerceService _commerceService;
	private PhantomBackgroundService _backgroundService;
	private PhantomPopulationManager _populationManager;
	private PhantomPartyCoordinator _partyCoordinator;
	private PhantomSocialService _socialService;
	private PhantomConversationService _conversationService;
	private PhantomConversationExecutionService _conversationExecutionService;
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
			if (_productionMaterialization)
			{
				profileRepository = PhantomProfileRepository.open();
				final File socialCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/social/high-five-social-v1.xml");
				final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(socialCatalogFile.toPath());
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
				_scheduler = createScheduler(new PhantomMaterializationServiceActivityPort(_materializationService), workSinkBridge);
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
				final PhantomMaterializationLifecycleBridge productionLifecycle = Objects.requireNonNull(lifecycleBridge);
				final PhantomActivityWorkSinkBridge productionWorkSink = Objects.requireNonNull(workSinkBridge);
				_progressionService = new PhantomProgressionService(new L2jProgressionBackend(_materializationService, ServerConfig.DATAPACK_ROOT.toPath(), _gameKnowledgeService::query), PhantomProgressionPolicy.productionDefaults());
				_progressionService.start();
				final L2jCombatBackend combatBackend = new L2jCombatBackend(_materializationService, _gameKnowledgeService::query, () -> _progressionService.findCatalog().orElse(null));
				_combatService = new PhantomCombatService(combatBackend, PhantomCombatCapabilityResolver.fromProgression(() -> _progressionService.findCatalog().orElse(null)), combatPolicy);
				_combatService.start();
				final PhantomCommerceCatalogLoader.LoadResult commerceCatalog = new PhantomCommerceCatalogLoader(ServerConfig.DATAPACK_ROOT.toPath()).load();
				_commerceService = new PhantomCommerceService(commerceCatalog, new PhantomCommerceReceiptStore(productionProfiles), productionGoals, new L2jCommerceBackend(_materializationService, commerceCatalog.catalog(), Clock.systemDefaultZone()));
				if (!_commerceService.start())
				{
					throw new IllegalStateException("Phantom commerce service could not enter the running state.");
				}
				final PhantomPartyParticipationPort.Bridge partyParticipation = PhantomPartyParticipationPort.bridge();
				_backgroundService = new PhantomBackgroundService(
					productionProfiles,
					productionGoals,
					PhantomIdentityLeaseRegistry.getInstance(),
					new PhantomBackgroundTransaction(),
					new L2jPhantomBackgroundAuthority(_gameKnowledgeService::query, _topologyService::query, _progressionService::catalog, _commerceService::catalog),
					new PhantomBackgroundCompetitionRegistry(),
					new PhantomSchedulerRelevanceSignalPort(_scheduler),
					() -> _materializationService,
					partyParticipation);
				if (!_backgroundService.start())
				{
					throw new IllegalStateException("Phantom background service could not enter the running state.");
				}
				productionLifecycle.install(_backgroundService);
				final PhantomCommerceDecision commerceDecision = new PhantomCommerceDecision(_commerceService);
				final PhantomBackgroundDecision backgroundDecision = new PhantomBackgroundDecision(_backgroundService);
				final File populationCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/population/high-five-population-v1.xml");
				final PhantomPopulationCatalog populationCatalog = PhantomPopulationCatalog.load(populationCatalogFile.toPath(), _settings.populationTimeZone());
				_populationManager = new PhantomPopulationManager(
					new PhantomPopulationStore(productionProfiles, populationCatalog, _settings.populationTimeZone()),
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
				_partyCoordinator = new PhantomPartyCoordinator(
					new PhantomPartyStore(productionProfiles),
					productionGoals,
					partyBackend,
					partyRoleCatalog,
					new PhantomPartyRouteCoordinator(_navigationService, _combatService),
					new PhantomPartyTactics(_combatService, partyBackend),
					() -> _topologyService.query().snapshot().canonicalHash(),
					System::nanoTime,
					_settings.partyOperationsPerPulse(),
					_socialService,
					() -> System.currentTimeMillis() / 60000L);
				if (!_partyCoordinator.start())
				{
					throw new IllegalStateException("Phantom party coordinator could not enter the running state.");
				}
				partyParticipation.install(_partyCoordinator);
				final File conversationCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-v1.xml");
				final File conversationCorpusFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv");
				final PhantomConversationCatalog conversationCatalog = PhantomConversationCatalog.load(conversationCatalogFile.toPath(), conversationCorpusFile.toPath());
				final File conversationExecutionCatalogFile = new File(ServerConfig.DATAPACK_ROOT, "data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml");
				final PhantomConversationExecutionCatalog conversationExecutionCatalog = PhantomConversationExecutionCatalog.load(conversationExecutionCatalogFile.toPath());
				final PhantomConversationExecutionStore conversationExecutionStore = new PhantomConversationExecutionStore(productionProfiles, conversationExecutionCatalog);
				final PhantomConversationPlanSink.Bridge conversationExecutionSignal = PhantomConversationPlanSink.bridge();
				_conversationService = new PhantomConversationService(conversationCatalog, new PhantomConversationStore(productionProfiles, conversationExecutionStore), new L2jPhantomConversationContextPort(_materializationService, _topologyService.query()), _semanticUnderstandingService, _socialService, conversationExecutionSignal, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance());
				_conversationExecutionService = new PhantomConversationExecutionService(conversationExecutionCatalog, conversationExecutionStore, productionGoals, new L2jPhantomConversationExecutionPort(conversationExecutionCatalog, _gameKnowledgeService, _topologyService.query(), _partyCoordinator, _materializationService, ChatObservationService.getInstance()));
				conversationExecutionSignal.install(_conversationExecutionService);
				if (!_conversationExecutionService.start())
				{
					throw new IllegalStateException("Phantom conversation execution service could not enter the running state.");
				}
				if (!_conversationService.start())
				{
					throw new IllegalStateException("Phantom conversation service could not enter the running state.");
				}
				final PhantomPopulationDecision populationDecision = new PhantomPopulationDecision(_populationManager);
				final PhantomPartyDecision partyDecision = new PhantomPartyDecision(_partyCoordinator);
				final PhantomCandidateRegistry candidateRegistry = new PhantomCandidateRegistry();
				commerceDecision.registerCandidates(candidateRegistry);
				backgroundDecision.registerCandidates(candidateRegistry);
				populationDecision.registerCandidates(candidateRegistry);
				partyDecision.registerCandidates(candidateRegistry);
				candidateRegistry.seal();
				final PhantomStepHandlerRegistry handlerRegistry = new PhantomStepHandlerRegistry();
				new PhantomProgressionStepHandlers(_progressionService).register(handlerRegistry);
				new PhantomCombatStepHandlers(_combatService, combatPolicy).register(handlerRegistry);
				commerceDecision.registerHandlers(handlerRegistry);
				backgroundDecision.registerHandlers(handlerRegistry);
				populationDecision.registerHandlers(handlerRegistry);
				partyDecision.registerHandlers(handlerRegistry);
				handlerRegistry.seal();
				_decisionEngine = new PhantomDecisionEngine(productionGoals, candidateRegistry, handlerRegistry, _metrics, _settings.maxScheduledPhantomProfiles());
				_decisionEngine.start();
				_populationManager.installDecisionEngine(_decisionEngine);
				if (!_scheduler.installControlPort(new PhantomCompositeSchedulerControlPort(java.util.List.of(_populationManager, _partyCoordinator, _conversationService, _conversationExecutionService))))
				{
					throw new IllegalStateException("Population control port could not be installed before scheduler start.");
				}
				productionWorkSink.install(_decisionEngine);
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
			if (_conversationExecutionService != null)
			{
				_conversationExecutionService.beginStop();
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
			final boolean conversationStopped = (_conversationService == null) || _conversationService.finishStop();
			final boolean conversationExecutionStopped = conversationStopped && ((_conversationExecutionService == null) || _conversationExecutionService.finishStop());
			final boolean partyStopped = conversationExecutionStopped && ((_partyCoordinator == null) || _partyCoordinator.finishStop());
			boolean socialStopped = _socialService == null;
			if (partyStopped && (_socialService != null))
			{
				_socialService.beginStop();
				socialStopped = _socialService.finishStop();
			}
			final boolean combatStopped = partyStopped && socialStopped && ((_combatService == null) || _combatService.finishStop());
			final boolean progressionStopped = (_progressionService == null) || _progressionService.finishStop();
			final boolean commerceStopped = (_commerceService == null) || _commerceService.finishStop();
			final boolean populationStopped = (_populationManager == null) || _populationManager.finishStop();
			boolean materializationStopped = _materializationService == null;
			if (combatStopped && progressionStopped && commerceStopped && populationStopped && backgroundReadyForMaterializationShutdown() && (_materializationService != null))
			{
				materializationStopped = _materializationService.shutdown().state() == ServiceState.STOPPED;
			}
			final boolean backgroundStopped = populationStopped && ((_backgroundService == null) || (materializationStopped && _backgroundService.finishStop()));
			if (backgroundStopped && (_scheduler != null))
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
			_state = backgroundStopped && semanticStopped ? State.STOPPED : State.FAILED;
			throw e;
		}
		_metrics.recordLifecycleStart();
		_state = State.RUNNING;
		return true;
	}

	public synchronized boolean shutdown()
	{
		if (_state == State.STOPPED)
		{
			return false;
		}

		if (_state == State.RUNNING)
		{
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
			if ((_combatService != null) && !_combatService.finishStop())
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
			_state = State.STOPPED;
			return true;
		}
		if (_state == State.FAILED)
		{
			if ((_conversationService != null) && (_conversationService.snapshot().state() != PhantomConversationService.ServiceState.STOPPED))
			{
				_conversationService.beginStop();
				if (!_conversationService.finishStop())
				{
					_metrics.recordShutdownFailure();
					return false;
				}
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
			if (_combatService != null)
			{
				_combatService.retryFailedCleanup();
			}
			if ((_combatService != null) && (_combatService.snapshot().state() != PhantomCombatService.ServiceState.STOPPED) && !_combatService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
			}
			if ((_progressionService != null) && (_progressionService.snapshot().state() != PhantomProgressionService.State.STOPPED) && !_progressionService.finishStop())
			{
				_metrics.recordShutdownFailure();
				return false;
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
			_state = State.STOPPED;
			return true;
		}

		_state = State.STOPPED;
		return false;
	}

	private boolean backgroundReadyForMaterializationShutdown()
	{
		return (_backgroundService == null) || permitsMaterializationShutdown(_backgroundService.materializationQuiescence());
	}

	public static boolean permitsMaterializationShutdown(PhantomBackgroundService.QuiescenceSnapshot snapshot)
	{
		return (snapshot != null) && snapshot.ready();
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_state, _settings, _scheduler != null ? _scheduler.snapshot() : PhantomScheduler.SchedulerSnapshot.inactive(), _decisionEngine != null ? _decisionEngine.snapshot() : PhantomDecisionEngine.EngineSnapshot.inactive(), _navigationService != null ? _navigationService.snapshot() : PhantomNavigationService.ServiceSnapshot.inactive(), _topologyService != null ? _topologyService.snapshot() : PhantomTopologyService.ServiceSnapshot.inactive(), _gameKnowledgeService != null ? _gameKnowledgeService.snapshot() : PhantomGameKnowledgeService.ServiceSnapshot.inactive(), _semanticUnderstandingService != null ? _semanticUnderstandingService.snapshot() : PhantomSemanticUnderstandingService.Snapshot.inactive(), _progressionService != null ? _progressionService.snapshot() : PhantomProgressionService.ServiceSnapshot.inactive(), _combatService != null ? _combatService.snapshot() : PhantomCombatService.ServiceSnapshot.inactive(), _backgroundService != null ? _backgroundService.snapshot() : null, _populationManager != null ? _populationManager.snapshot() : PhantomPopulationManager.Snapshot.inactive(), _socialService != null ? _socialService.snapshot() : PhantomSocialService.Snapshot.inactive(), _conversationService != null ? _conversationService.snapshot() : PhantomConversationService.Snapshot.inactive(), _conversationExecutionService != null ? _conversationExecutionService.snapshot() : PhantomConversationExecutionService.Snapshot.inactive(), ChatObservationService.getInstance().snapshot(), _metrics.snapshot(), _trace != null ? _trace.snapshot() : PhantomDiagnosticTrace.Snapshot.disabled());
	}

	public synchronized PhantomPartyCoordinator.Snapshot partySnapshot()
	{
		return _partyCoordinator == null ? PhantomPartyCoordinator.Snapshot.inactive() : _partyCoordinator.snapshot();
	}

	public static synchronized boolean startConfigured()
	{
		if (!PhantomPlayersConfig.isEnabled() || (_configuredInstance != null))
		{
			return false;
		}

		final PhantomSystem candidate = new PhantomSystem(PhantomPlayersConfig.settings(), true);
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

	public static synchronized boolean shutdownIfStarted()
	{
		if (_configuredInstance == null)
		{
			return false;
		}

		final PhantomSystem configured = _configuredInstance;
		final boolean stopped = configured.shutdown();
		if (configured.snapshot().state() == State.STOPPED)
		{
			_configuredInstance = null;
		}
		return stopped;
	}

	public static synchronized boolean hasConfiguredInstance()
	{
		return _configuredInstance != null;
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

	public record Snapshot(State state, PhantomPlayersConfig.Settings settings, PhantomScheduler.SchedulerSnapshot scheduler, PhantomDecisionEngine.EngineSnapshot decision, PhantomNavigationService.ServiceSnapshot navigation, PhantomTopologyService.ServiceSnapshot topology, PhantomGameKnowledgeService.ServiceSnapshot gameKnowledge, PhantomSemanticUnderstandingService.Snapshot semanticUnderstanding, PhantomProgressionService.ServiceSnapshot progression, PhantomCombatService.ServiceSnapshot combat, PhantomBackgroundService.Snapshot background, PhantomPopulationManager.Snapshot population, PhantomSocialService.Snapshot social, PhantomConversationService.Snapshot conversation, PhantomConversationExecutionService.Snapshot conversationExecution, ChatObservationService.Snapshot chatObservation, PhantomMetrics.Snapshot metrics, PhantomDiagnosticTrace.Snapshot trace)
	{
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
