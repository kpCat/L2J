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
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.PvpConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager;
import org.l2jmobius.gameserver.handler.BypassHandler;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.managers.RaidBossSpawnManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.NoPvPZone;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.script.State;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.QuestEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.RankedSource;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionSourcePlanner.ResourceEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.MethodBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionSkillKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.AcquisitionTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionUse;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpConsequenceSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpLinkedServitorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPvpCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.Argument;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.zone.type.NpcSpawnTerritory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFollowPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomRealPartyInvitationPolicy;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftConversationFacts;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConversationFacts;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomCombatServerIntegrationSuite implements PhantomTestSuite
{
	public enum Mode
	{
		BASELINE,
		ACQUISITION,
		MANOR,
		QUEST,
		PVP,
		PVP_CURRENT_058,
		RAID_TACTICS_058,
		INSTANCES_058,
		RIFT_NATIVE_058,
		NATURAL_STOP_058,
		EARNED_LEARNING_058,
		TELEPORT_CLEANUP_058,
		COMPANION_GREATER_HEAL_058,
		POST002_SUPPORT,
		COMPANION_REAL_SUPPORT_058,
		COMPANION_ROLE_MATRIX_058,
		COMPANION_FOLLOW_058,
		COMPANION_MOVING_FOLLOW_058,
		COMPANION_DEPARTURE_058,
		COMPANION_ASSIST_058,
		COMPANION_NPC_AGGRO_058,
		LEARNED_SKILLS_058,
		COMPANION_RANGED_SPACING_058,
		COMPANION_FULL_COMPOSITION_058,
		COMPANION_FARM_RESUME_058,
		QOL009_SERVITOR
	}

	private static final long ACQUISITION_SEED = 21002101L;
	private static final long CHECKPOINT_2_SEED = 21002102L;
	private static final long PVP_SEED = 25002501L;
	private static final long QOL009_SEED = 1009001L;
	private static final long POST002_SEED = 2002002L;
	private static final String PVP_AUTHORITY_HASH = "A".repeat(64);
	private static final int MELEE_CLASS_ID = 88;
	private static final int MAGIC_CLASS_ID = 94;
	private static final int MAGIC_SKILL_ID = 1339;
	private static final int WEAPON_ITEM_ID = 6;
	private static final int SOULSHOT_ITEM_ID = 1835;
	private static final int ADENA_ITEM_ID = 57;
	private static final int SPIRIT_ORE_ITEM_ID = 3031;
	private static final int WARLOCK_CLASS_ID = 14;
	private static final int ARCANA_LORD_CLASS_ID = 96;
	private static final int SUMMON_MEW_SKILL_ID = 1225;
	private static final int SUMMON_MEW_SKILL_LEVEL = 18;
	private static final int SUMMON_FELINE_KING_SKILL_ID = 1406;
	private static final int SUMMON_FELINE_KING_SKILL_LEVEL = 1;
	private static final long WAIT_MILLIS = 10000;
	private static final int SPOIL_CLASS_ID = 117;
	private static final int SPOIL_SKILL_ID = 254;
	private static final int SWEEP_SKILL_ID = 42;

	private final Mode _mode;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Monster> _worldFixtures = new ArrayList<>();
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private final List<PhantomProfile> _additionalProfiles058 = new ArrayList<>();
	private PhantomMaterializationService _materialization;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomGameKnowledgeQuery _query;
	private PhantomCombatService _combat;
	private L2jCombatBackend _backend;
	private Player _player;
	private Player _observer;
	private SpawnFact _combatPoint;
	private Source _acquisitionSource;
	private PhantomProgressionCatalog _progression;
	private Path _moduleRoot;
	private float _spoilChanceRateBaseline;
	private double _acquisitionRawItemChance;
	private PhantomAcquisitionManorAuthority _manorAuthority;
	private Candidate _manorCandidate;
	private int _manorPlayerLevel;
	private PhantomAcquisitionQuestCatalog _questCatalog;
	private List<QuestSource> _questSources = List.of();
	private Rule _questRule;
	private PhantomTopologyQuery _topology;
	private NpcSpawnTerritory _sourceTerritory;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _backgroundProduction;
	private PhantomAcquisitionCatalog _acquisitionCatalog;
	private PhantomAcquisitionSourcePlanner _acquisitionPlanner;
	private PhantomGoalStateStore _acquisitionGoals;
	private PhantomAcquisitionStore _acquisitionStore;
	private PhantomBackgroundService _background;
	private PhantomNavigationService _navigation;
	private PhantomAcquisitionService _acquisition;
	private final AtomicLong _epochMillis = new AtomicLong(1_800_000_000_000L);
	private long _acquisitionRevision;

	public PhantomCombatServerIntegrationSuite()
	{
		this(Mode.BASELINE);
	}

	public PhantomCombatServerIntegrationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return switch (_mode)
		{
			case POST002_SUPPORT -> "post002-native-support";
			case COMPANION_REAL_SUPPORT_058 -> "companion-real-support058";
			case COMPANION_ROLE_MATRIX_058 -> "companion-role-matrix058";
			case COMPANION_FOLLOW_058 -> "companion-follow058";
			case COMPANION_MOVING_FOLLOW_058 -> "companion-moving-follow058";
			case COMPANION_DEPARTURE_058 -> "companion-departure058";
			case COMPANION_ASSIST_058 -> "companion-assist058";
			case COMPANION_NPC_AGGRO_058 -> "companion-npc-aggro058";
			case LEARNED_SKILLS_058 -> "learned-skills058";
			case COMPANION_RANGED_SPACING_058 -> "companion-ranged-spacing058";
			case COMPANION_FULL_COMPOSITION_058 -> "companion-full-composition058";
			case COMPANION_FARM_RESUME_058 -> "companion-farm-resume058";
			case QOL009_SERVITOR -> "qol-summoner-servitor-combat";
			case PVP -> "pvp-combat-server-integration";
			case PVP_CURRENT_058 -> "pvp-current-native058";
			case RAID_TACTICS_058 -> "raid-tactics-native058";
			case INSTANCES_058 -> "instances-native058";
			case RIFT_NATIVE_058 -> "rift-native058";
			case NATURAL_STOP_058 -> "natural-infrastructure-stop058";
			case EARNED_LEARNING_058 -> "earned-learning-native058";
			case TELEPORT_CLEANUP_058 -> "teleport-cleanup-native058";
			case COMPANION_GREATER_HEAL_058 -> "companion-current-greater-heal058";
			case ACQUISITION -> "acquisition-active-spoil";
			case MANOR -> "acquisition-manor-active";
			case QUEST -> "acquisition-quest-active";
			default -> "combat-server-integration";
		};
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		if (_mode == Mode.COMPANION_GREATER_HEAL_058)
		{
			PhantomAssertions.assertEquals(58005839L, context.seed(), "Current Greater Heal used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.TELEPORT_CLEANUP_058)
		{
			PhantomAssertions.assertEquals(58005837L, context.seed(), "Normal teleport cleanup used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.EARNED_LEARNING_058)
		{
			PhantomAssertions.assertEquals(58005833L, context.seed(), "Earned learning used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.NATURAL_STOP_058)
		{
			PhantomAssertions.assertEquals(58005835L, context.seed(), "Natural infrastructure stop used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.RIFT_NATIVE_058)
		{
			PhantomAssertions.assertEquals(58005832L, context.seed(), "Native Rift used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.INSTANCES_058)
		{
			PhantomAssertions.assertEquals(58005831L, context.seed(), "Native instances used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.RAID_TACTICS_058)
		{
			PhantomAssertions.assertEquals(58005830L, context.seed(), "Native raid tactics used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		if (_mode == Mode.PVP_CURRENT_058)
		{
			PhantomAssertions.assertEquals(58005829L, context.seed(), "Current native PvP used the wrong058 seed.");
			assertCampaign058Jar(context);
		}
		else if (_mode == Mode.COMPANION_FARM_RESUME_058)
		{
			PhantomAssertions.assertEquals(58005825L, context.seed(), "Companion farm resume used the wrong seed.");
			assertCampaign058Jar(context);
		}
		else if (_mode == Mode.COMPANION_FULL_COMPOSITION_058)
		{
			PhantomAssertions.assertEquals(58005824L, context.seed(), "Full companion composition used the wrong seed.");
			assertCampaign058Jar(context);
		}
		else if (_mode == Mode.COMPANION_RANGED_SPACING_058)
		{
			PhantomAssertions.assertEquals(58005819L, context.seed(), "Ranged spacing used the wrong deterministic seed.");
		}
		else if (_mode == Mode.LEARNED_SKILLS_058)
		{
			PhantomAssertions.assertEquals(58005818L, context.seed(), "Learned skills used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_NPC_AGGRO_058)
		{
			PhantomAssertions.assertEquals(58005817L, context.seed(), "Companion NPC aggro used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_ASSIST_058)
		{
			PhantomAssertions.assertEquals(58005815L, context.seed(), "Companion assist used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_DEPARTURE_058)
		{
			PhantomAssertions.assertEquals(58005814L, context.seed(), "Companion departure used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_MOVING_FOLLOW_058)
		{
			PhantomAssertions.assertEquals(58005813L, context.seed(), "Moving companion follow used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_FOLLOW_058)
		{
			PhantomAssertions.assertEquals(58005812L, context.seed(), "Companion native follow used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_ROLE_MATRIX_058)
		{
			PhantomAssertions.assertEquals(58005807L, context.seed(), "Companion role matrix used the wrong deterministic seed.");
		}
		else if (_mode == Mode.COMPANION_REAL_SUPPORT_058)
		{
			PhantomAssertions.assertEquals(58005806L, context.seed(), "Companion native support used the wrong deterministic seed.");
		}
		else if (_mode == Mode.QOL009_SERVITOR)
		{
			PhantomAssertions.assertEquals(QOL009_SEED, context.seed(), "L2-QOL-009 used the wrong deterministic seed.");
		}
		else if (_mode == Mode.POST002_SUPPORT)
		{
			PhantomAssertions.assertEquals(POST002_SEED, context.seed(), "POST-002 native support used the wrong deterministic seed.");
		}
		else if (_mode == Mode.ACQUISITION)
		{
			PhantomAssertions.assertEquals(ACQUISITION_SEED, context.seed(), "Active acquisition mode used the wrong seed.");
		}
		else if ((_mode == Mode.MANOR) || (_mode == Mode.QUEST))
		{
			PhantomAssertions.assertEquals(CHECKPOINT_2_SEED, context.seed(), "Goal 021 Checkpoint 2 active mode used the wrong seed.");
		}
		else if (_mode == Mode.PVP)
		{
			PhantomAssertions.assertEquals(PVP_SEED, context.seed(), "Goal 025 PvP mode used the wrong seed.");
		}
		_moduleRoot = context.moduleRoot();
		_environment.initialize(context);
		try
		{
			if (_mode == Mode.INSTANCES_058) { PhantomSupportedContentScriptBootstrap.loadGoal036Owners(context); }
			else { ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE); }
			final boolean supplyNative060 = _mode == Mode.EARNED_LEARNING_058 && (Boolean.getBoolean("phantom.test.living060") || Boolean.getBoolean("phantom.test.supplyGuards060"));
			if (supplyNative060) { org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().loadInstances(); }
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();
			if (supplyNative060) { org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().updateReferences(); }

			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final PhantomTopologySnapshot topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
			_topology = topologyQuery;
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final PhantomGameKnowledgeBuilder builder = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), knowledgePolicy), topologyQuery, knowledgePolicy);
			_knowledge = new PhantomGameKnowledgeService(builder);
			PhantomAssertions.assertTrue(_knowledge.start(), "Game Knowledge service did not start.");
			_query = _knowledge.query();
			final PhantomProgressionPolicy progressionPolicy = PhantomProgressionPolicy.productionDefaults();
			_progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> _query).load(progressionPolicy), progressionPolicy);
			if (_mode == Mode.ACQUISITION)
			{
				_acquisitionSource = selectAcquisitionSource(topologyQuery, context);
				_combatPoint = _query.snapshot().spawnFactsByNpc().getOrDefault(_acquisitionSource.npcId(), List.of()).stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)).findFirst().orElseThrow(() -> new AssertionError("Acquisition source has no exact normal-world spawn."));
				_spoilChanceRateBaseline = RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;
				RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = Math.max(_spoilChanceRateBaseline, (float) Math.ceil(100 / _acquisitionRawItemChance));
			}
			else if (_mode == Mode.MANOR)
			{
				_manorAuthority = new PhantomAcquisitionManorAuthority(_query, topologyQuery, Path.of("data/mapregion"));
				_manorCandidate = selectManorCandidate();
				_combatPoint = sourceCombatPoint(_manorCandidate.npcId(), _manorCandidate.topologyNodeId(), _manorCandidate.anchorId());
				_sourceTerritory = sourceTerritory(_manorCandidate.npcId(), _manorCandidate.topologyNodeId());
			}
			else if (_mode == Mode.QUEST)
			{
				_questCatalog = PhantomAcquisitionQuestCatalog.load(Path.of("data/phantoms/acquisition/high-five-quest-collection-v1.xml"), Path.of("data/scripts"));
				ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
				_questCatalog.validateRuntime();
				_questSources = questSources(topologyQuery);
				selectQuestSource(_questSources.getFirst());
			}
			else
			{
				_combatPoint = selectCombatPoint();
			}

			_repository = PhantomProfileRepository.open();
			_profile = _repository.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			final int actorCapacity = (_mode == Mode.RAID_TACTICS_058) && Boolean.getBoolean("phantom.test.raidFlow059") ? 8 : (_mode == Mode.INSTANCES_058) && Boolean.getBoolean("phantom.test.kamalokaFlow059") ? 5 : (_mode == Mode.RIFT_NATIVE_058) && Boolean.getBoolean("phantom.test.riftFlow059") ? 3 : Set.of(Mode.INSTANCES_058, Mode.RAID_TACTICS_058, Mode.COMPANION_MOVING_FOLLOW_058, Mode.COMPANION_DEPARTURE_058, Mode.COMPANION_ASSIST_058, Mode.COMPANION_NPC_AGGRO_058, Mode.COMPANION_RANGED_SPACING_058, Mode.COMPANION_FULL_COMPOSITION_058).contains(_mode) ? 3 : 1;
			_materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), actorCapacity);
			if (Boolean.getBoolean("phantom.test.loadoutGuards061")) { _materialization.requireOffensiveShots(); }
			PhantomAssertions.assertTrue(_materialization.start(), "Materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Test actor did not materialize.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(_player != null, "Materialized World Player is absent.");
			if (_mode == Mode.ACQUISITION)
			{
				prepareAcquisitionActor();
			}
			if (Boolean.getBoolean("phantom.test.loadoutGuards061"))
			{
				// Declare the whole independent loadout input before its first case. The
				// generic lowest-level NPC may be a Scarecrow inside a level83 stock area.
				_player.setPlayerClass(0); _player.getStat().setLevel((byte) 19);
				_player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp());
				prepareLearningField059(context);
			}
			else { relocateToCombatPoint(); }
			if (_mode == Mode.MANOR)
			{
				prepareManorActor();
			}
			else if (_mode == Mode.QUEST)
			{
				prepareQuestActor();
			}

			_backend = new L2jCombatBackend(_materialization, () -> _query, () -> _progression);
			_combat = new PhantomCombatService(_backend, ((_mode == Mode.RAID_TACTICS_058) || (_mode == Mode.COMPANION_NPC_AGGRO_058)) ? PhantomCombatCapabilityResolver.fromProgression(() -> _progression) : PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query), PhantomCombatPolicy.productionDefaults(actorCapacity));
			_combat.start();
			if ((_mode == Mode.MANOR) || (_mode == Mode.QUEST))
			{
				_backgroundProduction = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
				_acquisitionCatalog = PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml"));
				_acquisitionPlanner = new PhantomAcquisitionSourcePlanner(_acquisitionCatalog, _query, _topology, _progression, _manorAuthority, _questCatalog);
				_acquisitionGoals = new PhantomGoalStateStore(_repository);
				_acquisitionStore = new PhantomAcquisitionStore(_repository, _acquisitionGoals);
				_background = new PhantomBackgroundService(_repository, _acquisitionGoals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), _backgroundProduction.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), () -> _materialization);
				_navigation = new PhantomNavigationService(new PhantomMetrics());
				_acquisition = new PhantomAcquisitionService(_acquisitionCatalog, _acquisitionStore, _acquisitionGoals, _acquisitionPlanner, _query, _topology, _progression, _combat, _background, _navigation, _manorAuthority, _questCatalog, _epochMillis::get);
				PhantomAssertions.assertTrue(_acquisition.start(), "Full acquisition integration service did not start.");
			}

			context.record("combatIntegration.database", System.getProperty("phantom.contract058.manifest") == null ? PhantomTestDatabaseGuard.TARGET_DATABASE : PhantomCampaign058DatabaseLane.DATABASE);
			context.record("combatIntegration.profileId", _profile.profileId());
			context.record("combatIntegration.actorObjectId", _player.getObjectId());
			context.record("combatIntegration.normalNpcId", _combatPoint.npcId());
			if (_mode == Mode.ACQUISITION)
			{
				context.record("acquisition.activeItemId", _acquisitionSource.itemId());
				context.record("acquisition.activeSourceId", _acquisitionSource.sourceId());
			}
			else if (_mode == Mode.MANOR)
			{
				context.record("acquisition.manorSourceId", _manorCandidate.sourceId());
				context.record("acquisition.manorSeedItemId", _manorCandidate.fact().seedItemId());
			}
			else if (_mode == Mode.QUEST)
			{
				context.record("acquisition.questRule", _questRule.id());
				context.record("acquisition.questScriptHash", _questRule.scriptHash());
			}
		}
		catch (Throwable throwable)
		{
			cleanup();
			throw throwable;
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.COMPANION_GREATER_HEAL_058)
		{
			registry.add("01-real-led-current-greater-heal-with-native-effect-and-cost", context -> testCompanionNativeRole058(context, "combat.heal"));
			return;
		}
		if (_mode == Mode.TELEPORT_CLEANUP_058)
		{
			registry.add("01-native-pending-teleport-normal-dematerialize", context ->
			{
				PhantomCampaign058DatabaseLane.assertActiveFixture(context);
				_player.setOnlineStatus(true, false);
				_player.teleToLocation(new Location(-71350, 258050, -3104));
				PhantomAssertions.assertTrue(_player.isTeleporting(), "Native TEST teleport did not enter its pending ACK state.");
				context.record("teleportCleanup058.input", "ordinary native teleport awaiting normal client ACK; no injected failure/kill/crash; next operation is ordinary materialization dematerialize");
				final var result = _materialization.dematerialize(_profile.profileId());
				context.record("teleportCleanup058.facts", "result=" + result.status() + "; teleporting=" + _player.isTeleporting() + "; worldPlayerSame=" + (World.getInstance().getPlayer(_player.getObjectId()) == _player) + "; worldObjectSame=" + (World.getInstance().findObject(_player.getObjectId()) == _player) + "; spawned=" + _player.isSpawned());
				PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.status(), "Normal dematerialize retained a Player awaiting teleport ACK.");
				final Player ordinary = ensureObserver(); ordinary.setOnlineStatus(true, false);
				ordinary.teleToLocation(new Location(-71350, 258070, -3104));
				PhantomAssertions.assertTrue(ordinary.isTeleporting(), "Ordinary human control never entered native pending teleport.");
				ordinary.deleteMe();
				PhantomAssertions.assertTrue(!ordinary.isTeleporting() && World.getInstance().getPlayer(ordinary.getObjectId()) == null && World.getInstance().findObject(ordinary.getObjectId()) == null, "Ordinary human pending-teleport logout regressed.");
				_observer = null;
			});
			return;
		}
		if (_mode == Mode.EARNED_LEARNING_058)
		{
			if (Boolean.getBoolean("phantom.test.areaAggro060")) { registry.add("36-native-area-aggro-recipient-refusals", context -> testNativeAreaAggro060(context, true)); registry.add("37-native-area-aggro-real-party-threat", context -> testNativeAreaAggro060(context, false)); return; }
			if (Boolean.getBoolean("phantom.test.petSupport060")) { registry.add("30-native-owned-buffalo-heal-and-control-lifecycle", context -> { prepareLearningField059(context); PhantomPet060NativeSupport.directHeal(context, _player); }); registry.add("32-production-native-kookaburra-recharge-and-food", context -> { _player.setCurrentHp(_player.getMaxHp()); prepareLearningField059(context); PhantomPet060NativeSupport.automaticMana(context, _player, _backend, _profile.profileId()); }); return; }
			if (Boolean.getBoolean("phantom.test.paidAutoGetGuards060"))
			{
				if (Boolean.getBoolean("phantom.test.learningLong061")) { registry.add("15-native-observed-elf-learning-local-walk061", this::testLearningLongWalk061); }
				registry.add("12-paid-native-rank-covers-autoget-floor", context -> testPaidAutoGetFloor060(context, true));
				registry.add("13-missing-or-unattainable-rank-does-not-cover-floor", context -> testPaidAutoGetFloor060(context, false));
				registry.add("14-native-return-normalizes-captured-feet", this::testLearningReturnFloor060);
				if (Boolean.getBoolean("phantom.test.retainedAutoGet061")) { registry.add("061-native-owned-capture-retains-earned-autoget-after-level-loss", this::testRetainedAutoGet061); registry.add("061-native-gludin-town-is-canonical", this::testGludinTownRecovery061); }
				return;
			}
			if (Boolean.getBoolean("phantom.test.supplyGuards060"))
			{
				if (Boolean.getBoolean("phantom.test.loadoutGuards061"))
				{
					registry.add("04-canonical-shot-conservation-and-discharge", _ -> testCanonicalShot());
					registry.add("05-missing-shot-does-not-fabricate", _ -> testMissingShot());
					registry.add("061-native-mandatory-shots-refuse-unpaid-attack-and-cast", this::testMandatoryShots061);
					registry.add("061-native-continuous-physical-shots-and-owned-cleanup", this::testContinuousShots061);
					registry.add("061-native-blessed-magic-shots-prefer-exact-weapon-grade", this::testBlessedShots061);
					registry.add("061-native-ordinary-autoplay-shares-shot-type-grade-and-quality", this::testOrdinaryShots061);
					registry.add("061-native-equipment-aspiration-respects-expertise-and-style", this::testEquipmentEligibility061);
					registry.add("061-native-equipment-owned-role-and-armor-through-progression", context -> { prepareLearningField059(context); PhantomSupply060OwnerGuards.equipment(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId()); });
					registry.add("061-native-unarmed-preparation-precedes-shot-stock", context -> { prepareLearningField059(context); PhantomSupply060OwnerGuards.unarmed(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId()); });
					registry.add("061-native-supply-long-walk-admission-with-finite-budget", context -> { prepareLearningField059(context); PhantomSupply060OwnerGuards.longApproach(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId()); });
					registry.add("061-native-equipment-paid-npc-receipt-and-equip", context -> { prepareLearningField059(context); PhantomSupply060OwnerGuards.equipmentPaid(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId()); });
					registry.add("061-native-supply-prefers-blessed-despite-regular-stock", context -> { PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat(), 20_000), "Previous native shot control did not leave combat stance."); prepareLearningField059(context); PhantomSupply060OwnerGuards.blessed(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId()); });
					return;
				}
				if (Boolean.getBoolean("phantom.test.supplyAmmunitionGuards060")) { registry.add("33-native-ammunition-grade-owner-and-stock-guards", context -> { prepareLearningField059(context); PhantomSupply060OwnerGuards.run(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId(), true); }); registry.add("34-unmodeled-ammunition-requires-native-farm", context -> PhantomSupply060OwnerGuards.ammunitionPolicy(context, _player, _query, _topology, _progression, new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog())); return; }
				if (Boolean.getBoolean("phantom.test.supplyResumeGuards060")) { registry.add("27-two-jvm-resume-receipt-authority", PhantomLiving060ResumeReceipt::models); return; }
				if (Boolean.getBoolean("phantom.test.supplyGatekeeperGuards060"))
				{
					registry.add("25-native-gk-corridor-plan-and-resources", context -> PhantomGatekeeper060CorridorGuards.run(context, _topology));
					registry.add("26-shared-gk-walk-search-budget", PhantomGatekeeper060CorridorGuards::sharedBudget); return;
				}
				if (Boolean.getBoolean("phantom.test.supplyManufactureGuards060")) { registry.add("22-native-offline-manufacture-source-guards", this::testManufactureSupplyGuards060); return; }
				if (Boolean.getBoolean("phantom.test.supplyReturnGuards060")) { registry.add("21-native-paid-supply-return-corridor", this::testSupplyReturn060); return; }
				registry.add("16-supply-bound-child-model", PhantomSupply060Observation::bindingModels);
				registry.add("17-supply-policy-bounds", PhantomSupply060Observation::policyModels);
				registry.add("18-native-npc-supply-source-readiness", context -> PhantomSupply060Observation.nativeSources(context, _query));
				registry.add("19-supply-native-owner-combat-deadline-resume-guards", context ->
				{
					prepareLearningField059(context);
					PhantomSupply060OwnerGuards.run(context, _player, _profile.profileId(), _repository, _materialization, _query, _topology, _combatPoint.npcId());
				});
				return;
			}
			if (Boolean.getBoolean("phantom.test.living060"))
			{
				final int classId = Integer.getInteger("phantom.test.livingClass060", Boolean.getBoolean("phantom.test.livingMage060") ? 25 : Boolean.getBoolean("phantom.test.livingDwarf060") ? 53 : 0);
				registry.add(Boolean.getBoolean("phantom.test.deathCpGuards060") ? "31-native-death-cp-owned-store-bound" : Boolean.getBoolean("phantom.test.livingResumeSave060") ? "28-paid-life-save-for-next-jvm" : Boolean.getBoolean("phantom.test.livingResumeContinue060") ? "29-loaded-paid-intent-return-and-new-earned-farm" : Boolean.getBoolean("phantom.test.livingGatekeeper060") ? "24-production-farm-native-gatekeeper-supply-and-farm" : Boolean.getBoolean("phantom.test.livingBow060") ? "35-production-bow-paid-arrows-return-and-new-farm" : Boolean.getBoolean("phantom.test.livingManufacture060") ? "23-production-farm-offline-manufacture-and-farm" : Boolean.getBoolean("phantom.test.livingOfflineStore060") ? "20-production-farm-paid-offline-store-and-farm" : Boolean.getBoolean("phantom.test.livingSupply060") ? "15-production-farm-paid-npc-supply-and-farm" : Boolean.getBoolean("phantom.test.livingRelocate060") ? "11-production-earned-threshold-farm-relocation" : classId == 25 ? "07-production-mystic-farm-learning-and-farm" : classId == 53 ? "08-production-dwarf-farm-learning-and-farm" : classId == 10 ? "09-production-human-mystic-farm-learning-and-farm" : classId == 44 ? "10-production-orc-farm-learning-and-farm" : "06-production-fighter-farm-learning-and-farm", context ->
					PhantomLiving060Scenario.run(context, _environment, _repository, _query, _topology, _progression, new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load(), classId));
				return;
			}
			if (Boolean.getBoolean("phantom.test.autonomousLearning060"))
			{
				if (Boolean.getBoolean("phantom.test.journeyLifecycle061"))
				{
					registry.add("061-accepted-journey-active-clock-and-native-distance-budget", this::testJourneyLifetime061);
					registry.add("061-native-town-preparation-without-false-farm-arrival", this::testNativePreparationTown061);
					registry.add("061-failed-native-journey-returns-without-erasing-failure", this::testFailedLearningReturn061);
					registry.add("061-career-native-field-return-without-learning-or-class-grant", context -> testNativeReturn061(context, true));
					registry.add("061-career-native-town-return-without-class-grant", context -> testNativeReturn061(context, true, true));
					registry.add("061-native-owned-walk-lifecycle-handoff-preserves-foreign-movement", context -> testNativeReturn061(context, true, false, true));
					registry.add("061-native-town-learning-producer-retains-owner-and-sp-guards", context -> testAutonomousLearning060(context, true));
					registry.add("061-learning-producer-rejects-native-weapon-mismatch", context -> testAutonomousLearning060(context, true, true));
				}
				registry.add("03-autonomous-earned-learning-through-farm-decision", this::testAutonomousLearning060);
				if (!Boolean.getBoolean("phantom.test.autonomousLearningGuards060")) { registry.add("04-native-earned-autonomous-paid-walk-return-and-resume", context ->
				{
					final String previous = System.getProperty("phantom.test.learningJourney059");
					System.setProperty("phantom.test.learningJourney059", "true");
					try { testEarnedLearning058(context); }
					finally { if (previous == null) { System.clearProperty("phantom.test.learningJourney059"); } else { System.setProperty("phantom.test.learningJourney059", previous); } }
				}); }
				registry.add("05-autonomous-learning-policy-rejects-unknown-input", this::testAutonomousLearningPolicy060);
				return;
			}
			registry.add(Boolean.getBoolean("phantom.test.learningJourney059") ? "02-earned-field-trainer-paid-learning-native-return-and-effect" : "01-earned-level-paid-skills-and-separate-field-effect-persistence", this::testEarnedLearning058);
			return;
		}
		if (_mode == Mode.NATURAL_STOP_058)
		{
			registry.add("01-native-festival-waits-before-ordinary-infrastructure-stop", context ->
			{
				org.l2jmobius.gameserver.model.sevensigns.SevenSignsFestival.getInstance();
				final List<String> lines = Files.readAllLines(_moduleRoot.resolve("java/org/l2jmobius/gameserver/model/sevensigns/SevenSignsFestival.java"));
				final int exactWaitLine = java.util.stream.IntStream.range(0, lines.size()).filter(index -> lines.get(index).contains("wait(FESTIVAL_SIGNUP_TIME);")).findFirst().orElseThrow() + 1;
				final long budget = org.l2jmobius.gameserver.config.FeatureConfig.ALT_FESTIVAL_MANAGER_START + 30_000;
				PhantomAssertions.assertTrue((budget > 0) && (budget <= 300_000), "Native Festival initial delay exceeds the declared bounded episode.");
				System.out.println("NATIVE058 natural signup wait: sourceLine=" + exactWaitLine + "; budgetMs=" + budget + "; signupMs=" + org.l2jmobius.gameserver.model.sevensigns.SevenSignsFestival.FESTIVAL_SIGNUP_TIME);
				PhantomAssertions.assertTrue(waitFor(() -> Thread.getAllStackTraces().entrySet().stream().anyMatch(entry -> entry.getKey().isAlive() && (entry.getKey().getState() == Thread.State.TIMED_WAITING) && java.util.Arrays.stream(entry.getValue()).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.sevensigns.SevenSignsFestival$FestivalManager") && (frame.getLineNumber() == exactWaitLine))), budget), "Native Festival worker did not reach its unchanged signup wait.");
				context.record("normalStop058.input", "natural native FestivalManager signup TIMED_WAITING observed at exact source line" + exactWaitLine + "; unchanged timers/no participants/no injected interruption; ordinary environment teardown must leave no infrastructure threads");
			});
			return;
		}
		if (_mode == Mode.RIFT_NATIVE_058)
		{
			registry.add(Boolean.getBoolean("phantom.test.riftFlow059") ? "02-legal-four-member-native-rift-reward-and-leave-control" : "01-mixed-native-rift-fee-room-combat-and-last-human-leave", this::testRift058);
			return;
		}
		if (_mode == Mode.INSTANCES_058)
		{
			if (Boolean.getBoolean("phantom.test.kamalokaFlow059")) { registry.add("02-product-owned-six-member-kamaloka-full-result-and-natural-closing", context -> testKamaloka058(context, true)); }
			else { registry.add("01-mixed-stock-kamaloka-entry-combat-reuse-natural-closing", this::testKamaloka058); }
			return;
		}
		if (_mode == Mode.RAID_TACTICS_058)
		{
			if (Boolean.getBoolean("phantom.test.raidTriggerGuard059")) { registry.add("06-native-trigger-expands-exact-earned-recipients-with-human-control", this::testRaidTrigger059); }
			else if (Boolean.getBoolean("phantom.test.raidMinionGuard059")) { registry.add("05-native-minion-parent-authority-effect-and-cost", this::testRaidFull059); }
			else if (Boolean.getBoolean("phantom.test.raidLevelGuard059")) { registry.add("04-native-minion-ceiling-refuses-before-combat", this::testRaidFull059); }
			else if (Boolean.getBoolean("phantom.test.raidStagingOnly059")) { registry.add("03-native-staging-geometry-preflight", this::testRaidFull059); }
			else if (Boolean.getBoolean("phantom.test.raidFlow059")) { registry.add("02-current-supported-raid-readiness-to-full-native-result", this::testRaidFull059); }
			else { registry.add("01-stock-live-raid-current-magic-tank-aggro-and-spacing", this::testRaidTactics058); }
			return;
		}
		if (_mode == Mode.COMPANION_FARM_RESUME_058)
		{
			registry.add("01-earned-native-farm-to-party-and-same-goal-resume", this::testCompanionFarmResume058);
			return;
		}
		if (_mode == Mode.COMPANION_FULL_COMPOSITION_058)
		{
			registry.add("01-addressed-speech-to-native-party-walk-assist-and-partychat", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_RANGED_SPACING_058)
		{
			registry.add("01-ranged-native-spacing-damage-and-rewards", this::testCompanionFollow058);
			registry.add("02-current-bow-gear-revalidates-without-cached-capability", this::testRangedGear058);
			registry.add("03-native-spacing-cancel-preserves-foreign-movement", this::testSpacingOwnership058);
			return;
		}
		if (_mode == Mode.LEARNED_SKILLS_058)
		{
			registry.add("01-current-enchanted-offense-and-class-context", this::testLearnedOffense058);
			registry.add("02-current-enchanted-recharge-native-effect", context -> testCompanionNativeRole058(context, "combat.recharge"));
			registry.add("03-current-bow-gear-revalidates-without-cached-capability", this::testRangedGear058);
			registry.add("04-canonical-subclass-switch-and-native-reload-refresh-skills", this::testSubclassSkills058);
			return;
		}
		if (_mode == Mode.COMPANION_NPC_AGGRO_058)
		{
			registry.add(Boolean.getBoolean("phantom.test.aggroReadiness060") ? "01-native-pre-enrollment-dd-readiness060" : "01-tank-native-npc-aggro-and-live-party-heal", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_ASSIST_058)
		{
			registry.add("01-three-roles-walk-and-assist-two-stock-reward-targets", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_DEPARTURE_058)
		{
			registry.add("01-last-human-leave-logout-and-same-character-rejoin", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_MOVING_FOLLOW_058)
		{
			registry.add("01-three-companions-follow-native-walking-human", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_FOLLOW_058)
		{
			registry.add("01-real-led-native-follow-around-stock-geometry", this::testCompanionFollow058);
			return;
		}
		if (_mode == Mode.COMPANION_ROLE_MATRIX_058)
		{
			registry.add("01-real-led-heal-hp-and-native-cost", context -> testCompanionNativeRole058(context, "combat.heal"));
			registry.add("02-real-led-recharge-mp-and-native-cost", context -> testCompanionNativeRole058(context, "combat.recharge"));
			registry.add("03-real-led-resurrection-request-test-accept-and-stand-up", context -> testCompanionNativeRole058(context, "combat.resurrection"));
			return;
		}
		if (_mode == Mode.COMPANION_REAL_SUPPORT_058)
		{
			if (Boolean.getBoolean("phantom.test.cardinalBuff059")) { registry.add("04-native-cardinal-current-avatar-party-hp-and-mp-cost", context -> testCompanionNativeRole058(context, "combat.buff", 97)); return; }
			if (Boolean.getBoolean("phantom.test.supportKinds059")) { registry.add("02-native-heal-label-cannot-cast-prophecy-buff", this::testNativeSupportKind059); registry.add("03-eva-current-learned-heal-addressed-effect-and-cost", context -> testCompanionNativeRole058(context, "combat.heal", 105)); return; }
			registry.add("01-real-led-automatic-buff-effect-cost-and-leave", this::testCompanionRealSupport058);
			return;
		}
		if (_mode == Mode.POST002_SUPPORT)
		{
			registry.add("01-human-party-native-buff-effect-and-guards", this::testPost002NativeSupport);
			return;
		}
		if (_mode == Mode.QOL009_SERVITOR)
		{
			registry.add("01-stock-human-servitor-path-and-canonical-census", this::testQol009SourceCensus);
			registry.add("02-warlock-native-resummon-sync-retarget-follow", _ -> testQol009WarlockControl());
			registry.add("03-human-owner-loses-los-summon-path-remains-authoritative", this::testQol009OwnerLosesLos);
			registry.add("04-native-resummon-resource-reuse-and-state-guards", _ -> testQol009ResummonGuards());
			registry.add("05-arcana-lord-feline-king-native-active-skill", _ -> testQol009ArcanaActiveSkill());
			registry.add("06-pet-babypet-stale-dead-and-instance-exclusion", this::testQol009Exclusions);
			registry.add("07-pvp-owner-and-linked-servitor-policy", _ -> testQol009PvpPolicy());
			registry.add("08-live-linked-servitor-owner-context-and-cleanup", _ -> testQol009LivePvpAndCleanup());
			return;
		}
		if (_mode == Mode.PVP_CURRENT_058)
		{
			if (Boolean.getBoolean("phantom.test.pvpGuardsOnly059"))
			{
				registry.add("05-native-player-kills-flags-karma-and-ordinary-reload", this::testPvpConsequences059);
				registry.add("06-native-clan-war-eligibility-and-instance-guards", this::testPvpAffiliations059);
				return;
			}
			if (Boolean.getBoolean("phantom.test.pvpFlowOnly059")) { registry.add("04-native-defense-resource-retreat-regroup-and-durable-end", this::testPvpFlow059); return; }
			registry.add("01-native-no-pvp-zone-refuses-enemy-ownership", this::testNoPvpZone058);
			registry.add("02-current-enhanced-native-retaliation-and-party-negative", this::testCurrentPvp058);
			registry.add("03-native-real-party-defense-and-stale-membership", this::testPartyDefense058);
			if (Boolean.getBoolean("phantom.test.pvpFlow059"))
			{
				registry.add("04-native-defense-resource-retreat-regroup-and-durable-end", this::testPvpFlow059);
				registry.add("05-native-player-kills-flags-karma-and-ordinary-reload", this::testPvpConsequences059);
				registry.add("06-native-clan-war-eligibility-and-instance-guards", this::testPvpAffiliations059);
			}
			return;
		}
		if (_mode == Mode.PVP)
		{
			registry.add("01-canonical-player-forced-physical-path", _ -> testPvpPhysical());
			registry.add("02-canonical-player-use-magic-path", _ -> testPvpMagic());
			registry.add("03-canonical-cp-handler-and-consequence-truth", _ -> testPvpCpAndConsequences());
			registry.add("04-canonical-pvp-pk-karma-outcomes", _ -> testPvpReputationOutcomes());
			return;
		}
		if (_mode == Mode.ACQUISITION)
		{
			registry.add("01-exact-target-skill-distance-instance-and-ownership", _ -> testAcquisitionControls());
			registry.add("02-canonical-spoil-existing-combat-sweep-inventory", _ -> testCanonicalAcquisitionChain());
			registry.add("03-dispatch-crash-recovery-never-blind-repeats", _ -> testAcquisitionDispatchRecovery());
			return;
		}
		if (_mode == Mode.MANOR)
		{
			registry.add("01-real-seed-combat-harvester-chain", _ -> testCanonicalManorChain());
			registry.add("02-manor-controls-and-dispatch-recovery", _ -> testManorControls());
			return;
		}
		if (_mode == Mode.QUEST)
		{
			registry.add("01-real-delayed-on-attackable-kill", _ -> testCanonicalQuestChain());
			registry.add("02-exact-state-cond-target-and-cap-controls", _ -> testQuestControls());
			registry.add("03-full-service-owned-combat-and-epoch-recovery", _ -> testQuestServiceLifecycle());
			registry.add("04-full-service-exact-delta-and-legacy-verifying", _ -> testQuestExactDeltaBoundaries());
			return;
		}
		registry.add("01-exact-world-player-action-lease", _ -> testExactActorLease());
		registry.add("02-canonical-player-ai-attack-and-death", _ -> testCanonicalAttack());
		registry.add("03-canonical-selected-skill-cast", _ -> testCanonicalCast());
		registry.add("04-canonical-shot-conservation-and-discharge", _ -> testCanonicalShot());
		registry.add("05-missing-shot-does-not-fabricate", _ -> testMissingShot());
		registry.add("06-canonical-ground-item-pickup", _ -> testCanonicalLoot());
		registry.add("07-player-raid-grandboss-rejected", _ -> testForbiddenTargets());
		registry.add("08-cancel-only-owned-action", _ -> testOwnedCancellation());
		registry.add("09-other-player-pickup-is-not-acquisition", _ -> testOtherPlayerPickupEvidence());
		registry.add("10-despawn-is-not-acquisition", _ -> testDespawnEvidence());
		registry.add("11-range-loss-is-not-acquisition", _ -> testRangeLossEvidence());
		registry.add("12-cancel-during-exact-pickup", _ -> testPickupCleanup(false));
		registry.add("13-stop-during-exact-pickup", _ -> testPickupCleanup(true));
		registry.add("14-foreign-cast-and-pickup-survive", _ -> testForeignCastAndPickup());
		registry.add("15-positive-one-target-skill-rejected", _ -> testPositiveSkillRejected());
		registry.add("16-player-death-releases-ownership", _ -> testPlayerDeath());
		registry.add("17-restricted-normal-town-respawn", _ -> testNormalTownRespawn());
		registry.add("18-production-combat-has-no-packet-route", _ -> testNoPacketRoute());
		registry.add("19-canonical-player-cp-snapshot", _ -> testCanonicalCpSnapshot());
		registry.add("20-dematerialization-waits-for-combat-lease", _ -> testDematerializationDrain());
	}

	static void assertCampaign058Jar(PhantomTestContext context) throws Exception
	{
		final Path supplied = Path.of(System.getProperty("phantom.contract058.semantic.jar", PhantomCampaign058PersistentLane.RUNTIME.resolve("libs/GameServer.jar").toString())).toRealPath();
		PhantomAssertions.assertTrue(campaign058JarAllowed(supplied), "TASK058 ordinary semantic JAR path is outside its exact allowlist.");
		for (Class<?> type : List.of(Player.class, PhantomCombatService.class, PhantomPartyCoordinator.class, org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService.class, org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney.class))
		{
			PhantomAssertions.assertEquals(supplied, Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath(), "TASK058 composition mixes private product classes or different JARs: " + type.getName());
		}
		context.record("semanticJar.path", supplied.toString());
		context.record("semanticJar.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(supplied))));
	}

	static boolean campaign058JarAllowed(Path supplied)
	{
		if (PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/task061/build/dist/libs/GameServer.jar").equals(supplied)) { return true; }
		if (Path.of("D:/PhantomWorld/World-Career-061-candidate/libs/GameServer.jar").equals(supplied)) { return true; }
		return List.of(PhantomCampaign058PersistentLane.RUNTIME.resolve("libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/native-current058/dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/task059/build/dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/release059/compiled/dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/task060/build/dist/libs/GameServer.jar"), PhantomCampaign058DatabaseLane.MODULE.resolve(".phantom-local/contract058a/build/release060/compiled/dist/libs/GameServer.jar"), Path.of("D:/PhantomWorld/M2-M7-058-candidate/libs/GameServer.jar")).contains(supplied);
	}

	private void testCompanionFarmResume058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final SpawnFact source = _query.snapshot().npcById().values().stream()
			.filter(npc -> (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable() && (npc.level() >= 70) && (npc.level() <= 72) && (npc.exp() > 0))
			.sorted(Comparator.comparingInt(NpcFact::npcId))
			.flatMap(npc -> _query.snapshot().spawnFactsByNpc().getOrDefault(npc.npcId(), List.of()).stream())
			.filter(point -> (point.instanceId() == 0) && (point.topologyNodeId() != null) && !_topology.snapshot().anchorsByNode().getOrDefault(point.topologyNodeId(), List.of()).isEmpty())
			.filter(point -> point.territoryGeometry() != null)
			.map(point -> sourceCombatPoint(point.npcId(), point.topologyNodeId(), _topology.snapshot().anchorsByNode().get(point.topologyNodeId()).getFirst().id()))
			.filter(point -> GeoEngine.getInstance().hasGeo(point.x(), point.y()) && (Math.abs(GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()) - point.z()) <= 100)
				&& GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), point.x() + 64, point.y(), point.z(), 0))
			.findFirst().orElseThrow(() -> new AssertionError("No declared stock70..72 normal spawn with current topology/geometry."));
		final var anchor = _topology.snapshot().anchorsByNode().get(source.topologyNodeId()).getFirst();
		final int z = GeoEngine.getInstance().getHeight(source.x(), source.y(), source.z());
		final Player human = ensureObserver();
		for (Player actor : List.of(_player, human))
		{
			actor.decayMe();
			actor.setXYZInvisible(source.x() + 64, source.y(), z);
			actor.spawnMe();
			actor.revalidateZone(true);
		}
		prepareCompanionCombat058(_player, 88, 6580, 345, 6373, 6374, 6375, 6376, 6378);
		prepareCompanionCombat058(human, 5, 0, 0, 6373, 6374, 6375, 6376, 6378);
		final List<Monster> targets = new ArrayList<>();
		for (int index = 0; index < 4; index++)
		{
			final Monster monster = new Monster(NpcData.getInstance().getTemplate(source.npcId()));
			targets.add(monster);
			_worldFixtures.add(monster);
		}
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_repository);
		final PhantomGoal farm = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(source.npcId())), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, source.npcId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture058.native_farm", 1);
		goals.insert(_profile.profileId(), farm);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		candidates.seal();
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1);
		engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(_profile.profileId()), "Native FARM did not attach.");
		final PhantomNavigationService navigation = new PhantomNavigationService(new PhantomMetrics());
		navigation.start();
		final PhantomProgressionService progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final PhantomSocialService social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), context.seed(), 16);
		social.start();
		final L2jPhantomPartyBackend backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final PhantomPartyCoordinator party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), goals, backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(navigation, _combat, PhantomPartyFollowPolicy.load(Path.of("data/phantoms/party/high-five-party-follow-v1.xml"))), new PhantomPartyTactics(_combat, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 32);
		party.start();
		party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
		final PhantomVisibleAutoPlay autoPlay = new PhantomVisibleAutoPlay(_materialization, () -> engine, id -> !party.blocksBackground(id));
		final long actorAdenaBefore = _player.getAdena();
		final long humanAdenaBefore = human.getAdena();
		context.record("farm058.input", "TEST sourceNpc=" + source.npcId() + "; actual stock source=" + source + "; anchor=" + anchor.id() + "; class88/80/Sgear/345:1;4 full stock targets; first earned native FARM kill, invite during next attack, native party assist, leave/same FARM resume;120s each; no actor/item/stat/reward mutation during episode");
		try (var client = PhantomConversationIntegrationSuite.NetworkBackedClient.attach(human))
		{
			final long initialExp = _player.getExp();
			targets.get(0).spawnMe(source.x() + 32, source.y(), z);
			PhantomAssertions.assertTrue(autoPlay.start(_profile.profileId(), farm), "Native FARM owner did not start.");
			awaitFarmKill058(targets.get(0), party, false, initialExp);
			context.record("farm058.beforeParty", "npcDead=true; exp=" + initialExp + "->" + _player.getExp() + "; " + autoPlay.snapshotContinuation(_profile.profileId()).scalarMap());
			targets.get(1).spawnMe(source.x() + 32, source.y(), z);
			final long attackDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			while (!_player.isAttackingNow() && (System.nanoTime() < attackDeadline)) { Thread.sleep(20); }
			PhantomAssertions.assertTrue(_player.isAttackingNow(), "Declared takeover did not encounter an actual native attack.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().invite(human, _player, PartyDistributionType.FINDERS_KEEPERS.getId()).delivered(), "Native incoming invitation was not delivered.");
			for (int pulse = 0; (pulse < 64) && !party.committed(_profile.profileId()); pulse++) { party.onPulse(); Thread.sleep(20); }
			PhantomAssertions.assertTrue(party.committed(_profile.profileId()) && party.blocksBackground(_profile.profileId()), "Active FARM prevented lawful native companion commitment.");
			final long revokedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (_player.isAutoPlaying() && (System.nanoTime() < revokedDeadline)) { party.onPulse(); Thread.sleep(20); }
			PhantomAssertions.assertFalse(autoPlay.running(_profile.profileId(), farm) || _player.isAutoPlaying(), "Native FARM root still competes with committed party.");
			PhantomAssertions.assertEquals(farm, goals.load(_profile.profileId()).orElseThrow().goal(), "Incoming companion membership replaced the personal FARM.");
			human.setTarget(targets.get(1));
			awaitFarmKill058(targets.get(1), party, true, _player.getExp());
			targets.get(2).spawnMe(source.x() + 32, source.y(), z);
			human.setTarget(targets.get(2));
			awaitFarmKill058(targets.get(2), party, true, _player.getExp());
			context.record("farm058.inParty", "actual native second-target assist/reward; exp=" + _player.getExp() + "; ordinary=" + autoPlay.snapshotContinuation(_profile.profileId()).scalarMap());
			final var registrations = autoPlay.snapshotContinuation(_profile.profileId()).scalarMap();
			PhantomAssertions.assertEquals("false", registrations.get("liveAutoPlayRegistered"), "Old FARM AutoPlay registration competed with native party.");
			PhantomAssertions.assertEquals("false", registrations.get("liveAutoUseRegistered"), "Old FARM AutoUse registration competed with native party.");
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
			for (int pulse = 0; (pulse < 64) && party.blocksBackground(_profile.profileId()); pulse++) { party.onPulse(); Thread.sleep(20); }
			PhantomAssertions.assertFalse(party.blocksBackground(_profile.profileId()), "Leave retained the FARM fence.");
			PhantomAssertions.assertEquals(farm, goals.load(_profile.profileId()).orElseThrow().goal(), "Leave changed the exact personal FARM.");
			final long resumeExp = _player.getExp();
			targets.get(3).spawnMe(source.x() + 32, source.y(), z);
			PhantomAssertions.assertTrue(autoPlay.start(_profile.profileId(), farm), "Same personal FARM could not resume after leave.");
			awaitFarmKill058(targets.get(3), party, false, resumeExp);
			context.record("farm058.resumed", "sameGoal=" + farm.goalId() + ":" + farm.revision() + "; actual fourth stock kill/native EXP=" + resumeExp + "->" + _player.getExp() + "; " + autoPlay.snapshotContinuation(_profile.profileId()).scalarMap());
		}
		finally
		{
			autoPlay.stop(_profile.profileId());
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
			for (int pulse = 0; pulse < 8; pulse++) { party.onPulse(); }
			party.beginStop();
			party.finishStop();
			navigation.beginStop();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (navigation.snapshot().state() != PhantomNavigationService.ServiceState.STOPPED && System.nanoTime() < deadline) { navigation.finishStop(); Thread.sleep(10); }
			progression.beginStop(); progression.finishStop();
			social.beginStop(); social.finishStop();
			engine.beginStop(); engine.finishStop();
			// Measurement has ended. Restore only the native TEST fixtures' conserved adena baseline.
			destroyInventoryCount(_player, ADENA_ITEM_ID, _player.getAdena() - actorAdenaBefore);
			destroyInventoryCount(human, ADENA_ITEM_ID, human.getAdena() - humanAdenaBefore);
		}
	}

	private void awaitFarmKill058(Monster target, PhantomPartyCoordinator party, boolean companion, long expBefore) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
		while ((!target.isDead() || (_player.getExp() <= expBefore)) && (System.nanoTime() < deadline))
		{
			if (companion) { party.onPulse(); }
			PhantomAssertions.assertFalse(_player.isDead(), "Declared FARM/party actor died.");
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(target.isDead() && (_player.getExp() > expBefore), "No actual native stock kill/reward: npc=" + target.getId() + "; hp=" + target.getCurrentHp() + "; exp=" + expBefore + "->" + _player.getExp() + "; xyz=" + _player.getLocation() + "; target=" + _player.getTarget() + "; autoPlay=" + _player.isAutoPlaying());
	}

	private void testCompanionFollow058(PhantomTestContext context) throws Exception
	{
		final boolean movingLeader = Set.of(Mode.COMPANION_MOVING_FOLLOW_058, Mode.COMPANION_DEPARTURE_058, Mode.COMPANION_ASSIST_058, Mode.COMPANION_NPC_AGGRO_058, Mode.COMPANION_RANGED_SPACING_058, Mode.COMPANION_FULL_COMPOSITION_058).contains(_mode);
		// All placement is a declared precondition. During measurement only native movement is used.
		final int fromX = -71350;
		final int fromY = 258050;
		final int targetY = 258750;
		final int z = -3104;
		PhantomAssertions.assertFalse(GeoEngine.getInstance().canMoveToTarget(fromX, fromY, z, fromX, targetY, z, 0), "Follow input lacks the previously measured stock obstacle.");
		_player.decayMe();
		_player.setXYZInvisible(fromX, fromY, z);
		_player.spawnMe();
		_player.revalidateZone(true);
		_player.getStat().setExp(_player.getStat().getExpForLevel(30));
		_player.getStat().setLevel((byte) 30);
		_player.setCurrentHp(_player.getMaxHp());
		final Player human = ensureObserver();
		human.decayMe();
		human.setXYZInvisible(movingLeader ? fromX : fromX + 30, fromY, z);
		human.spawnMe();
		human.revalidateZone(true);
		PhantomAssertions.assertTrue(human.isSpawned() && !human.isMovementDisabled() && (human.getStat().getMoveSpeed() > 0), "Native human movement precondition is invalid.");
		final List<Player> actors = new ArrayList<>(List.of(_player));
		final List<Long> profileIds = new ArrayList<>(List.of(_profile.profileId()));
		if (movingLeader)
		{
			for (int slot = 1; slot <= 2; slot++)
			{
				final PhantomHeadlessPlayerFixture fixture = _environment.createCampaign058Companion(context, slot);
				final PhantomProfile profile = _repository.create(fixture.objectId());
				_additionalProfiles058.add(profile);
				PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(profile.profileId()).status(), "Additional058 companion did not materialize.");
				final Player actor = World.getInstance().getPlayer(fixture.objectId());
				PhantomAssertions.assertTrue(actor != null && !actor.isGM(), "Additional058 actor is absent or GM.");
				actor.decayMe();
				actor.setXYZInvisible(fromX, fromY, z);
				actor.spawnMe();
				actor.revalidateZone(true);
				actor.getStat().setExp(actor.getStat().getExpForLevel(30));
				actor.getStat().setLevel((byte) 30);
				actor.setCurrentHp(actor.getMaxHp());
				actors.add(actor);
				profileIds.add(profile.profileId());
			}
		}
		PhantomAssertions.assertTrue(!_player.isGM() && !human.isGM(), "Native follow requires ordinary TEST actors.");
		if (_mode == Mode.COMPANION_NPC_AGGRO_058)
		{
			prepareCompanionCombat058(actors.get(0), 90, 2500, 368, 6373, 6374, 6375, 6376, 6377, 6378);
			final int aggroLevel = _progression.classSkillLearns(90).stream().filter(learn -> (learn.skillId() == 28) && (learn.minimumCharacterLevel() <= 80)).mapToInt(learn -> learn.skillLevel()).max().orElseThrow();
			actors.get(0).addSkill(SkillData.getInstance().getSkill(28, aggroLevel), true);
			prepareCompanionCombat058(actors.get(1), 94, 6579, 1339, 6383, 6384, 6385, 6386);
			final int basicLevel = _progression.classSkillLearns(94).stream().filter(learn -> learn.skillId() == 1230 && learn.minimumCharacterLevel() <= 80).mapToInt(learn -> learn.skillLevel()).max().orElseThrow(() -> new AssertionError("Declared native Prominence input is absent from Mage94's current class tree."));
			actors.get(1).addSkill(SkillData.getInstance().getSkill(1230, basicLevel), true);

			prepareCompanionCombat058(actors.get(2), 97, 6579, 1335, 6383, 6384, 6385, 6386);
			prepareCompanionCombat058(human, 5, 13, 0, 6373, 6374, 6375, 6376, 6378);
			PhantomAssertions.assertTrue(human.getInventory().addItem(ItemProcessType.REWARD, 17, 100, null, this) != null, "TEST human native bow input lacks arrows.");
			human.setOnlineStatus(true, false);
			human.setRunning();
			human.standUp();
			context.record("companion058.aggro.preconditions", "TEST classes90/94/97 level80; actual learned28:" + aggroLevel + "; tank368:1/mage1339:1+native basic1230:" + basicLevel + "/healer1335:1; stock weapons2500/6579/6579 and ImperialCrusader/MajorArcana armor; human online/standing/running with native bow13+arrows17x100; no grants inside combat");
		}
		if ((_mode == Mode.COMPANION_NPC_AGGRO_058) && Boolean.getBoolean("phantom.test.aggroReadiness060"))
		{
			try (var lease = _backend.tryAcquireActor(profileIds.get(1)))
			{
				PhantomAssertions.assertTrue(lease != null, "Native DD readiness lacks its own actor lease.");
				final var loadout = org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.fromProgression(() -> _progression).resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4);
				context.record("companion058.aggro.readiness", "PRE-ENROLLMENT native prepared Mage94/80; loadout=" + loadout + "; learned1339=" + actors.get(1).getKnownSkill(1339) + "; native1339 cast/reuse=" + SkillData.getInstance().getSkill(1339,1).getHitTime() + '/' + SkillData.getInstance().getSkill(1339,1).getReuseDelay() + "; learned1230=" + actors.get(1).getKnownSkill(1230) + "; no fight/HP/MP/hate writes in this focused check");
				PhantomAssertions.assertTrue(loadout.isPresent() && loadout.orElseThrow().selectedSkills().stream().anyMatch(selected -> { final Skill skill = actors.get(1).getKnownSkill(selected.skillId()); return skill != null && skill.getReuseDelay() <= 10_000 && skill.getPower() > 0 && skill.getMagicLevel() >= 70; }), "Declared native35s level70 aggro scene lacks a learned supported repeatable damage spell of an appropriate native magic level.");
			}
			return;
		}
		if (Set.of(Mode.COMPANION_ASSIST_058, Mode.COMPANION_RANGED_SPACING_058, Mode.COMPANION_FULL_COMPOSITION_058).contains(_mode))
		{
			prepareCompanionCombat058(actors.get(0), 88, 6580, 345, 6373, 6374, 6375, 6376, 6378);
			prepareCompanionCombat058(actors.get(1), 94, 6579, 1339, 6383, 6384, 6385, 6386);
			prepareCompanionCombat058(actors.get(2), 92, 7575, 343, 6379, 6380, 6381, 6382);
			prepareCompanionCombat058(human, 5, 0, 0, 6373, 6374, 6375, 6376, 6378);
			PhantomAssertions.assertTrue(actors.get(2).getInventory().addItem(ItemProcessType.REWARD, 1345, 200, null, this) != null, "TEST archer arrows unavailable.");
			context.record("companion058.assist.preconditions", "TEST only; classes88/94/92; level80/XP/base/current class; learned345:1/1339:1/343:1+expertise239:6; stockS weapons6580/6579/7575; ImperialCrusader/MajorArcana/Draconic armor; arrows1345x200; humanKnight5/80+ImperialCrusader; no grants during measurement");
		}
		final PhantomMetrics navigationMetrics = new PhantomMetrics();
		final PhantomNavigationService navigation = new PhantomNavigationService(navigationMetrics);
		PhantomAssertions.assertTrue(navigation.start(), "Native companion navigation did not start.");
		final PhantomProgressionService progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final PhantomSocialService social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), context.seed(), 16);
		progression.start();
		PhantomAssertions.assertTrue(social.start(), "Native follow social authority did not start.");
		final L2jPhantomPartyBackend backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final PhantomPartyRouteCoordinator routes = new PhantomPartyRouteCoordinator(navigation, _combat, PhantomPartyFollowPolicy.load(Path.of("data/phantoms/party/high-five-party-follow-v1.xml")));
		final PhantomPartyCoordinator party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), routes, new PhantomPartyTactics(_combat, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 32);
		PhantomAssertions.assertTrue(party.start(), "Native follow party owner did not start.");
		party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
		Throwable scenarioFailure = null;
		PhantomCompanionComposition058 composition = null;
		try
		{
			if (_mode == Mode.COMPANION_FULL_COMPOSITION_058)
			{
				context.record("companion058.composition.input", "TEST seed58005824; actual ordinary GameServer.jar; speech/native consent; 3 roles; obstacle walk; 2 full stock Karik18008; native reward; 2 canonical leader transfers during an actual second-target native action; elected truthful party-role reply/repeat negative; no grants during measurement");
				composition = new PhantomCompanionComposition058(_repository, _materialization, _topology, _knowledge, social, party, human);
				composition.invite(context, human, actors.getFirst(), profileIds.getFirst(), party);
			}
			for (int actorIndex = composition == null ? 0 : 1; actorIndex < actors.size(); actorIndex++)
			{
				PhantomAssertions.assertTrue(org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().invite(human, actors.get(actorIndex), PartyDistributionType.FINDERS_KEEPERS.getId()).delivered(), "Native follow invitation was not delivered for actor" + actorIndex);
				final long profileId = profileIds.get(actorIndex);
				for (int pulse = 0; (pulse < 32) && !party.committed(profileId); pulse++)
				{
					party.onPulse();
				}
				PhantomAssertions.assertTrue(party.committed(profileId), "Native follow input never became a committed mixed Party for" + profileId);
			}
			final var humanPath = movingLeader ? PathFinding.getInstance().findPath(human.getX(), human.getY(), human.getZ(), fromX, targetY, z, 0, true) : null;
			if (movingLeader)
			{
				PhantomAssertions.assertTrue((humanPath != null) && !humanPath.isEmpty(), "Declared native human walking path is unavailable.");
			}
			else
			{
				human.decayMe();
				human.setXYZInvisible(fromX, targetY, z);
				human.spawnMe();
				human.revalidateZone(true);
			}
			context.record("companion058.follow.input", "TEST preconditions; movingHuman=" + movingLeader + "; managed=" + actors.stream().map(Player::getObjectId).toList() + "; profiles=" + profileIds + "; human=" + human.getObjectId() + "; from=" + fromX + ',' + fromY + ',' + z + "; to=" + fromX + ',' + targetY + ',' + z + "; native path around stock obstacle; deadline30s; maxSampleStep128");
			final int[][] previous = actors.stream().map(actor -> new int[] {actor.getX(), actor.getY(), actor.getZ()}).toArray(int[][]::new);
			double maximumStep = 0;
			int humanWaypoint = 0;
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while ((System.nanoTime() < deadline) && ((movingLeader && ((humanWaypoint < humanPath.size()) || human.isMoving())) || actors.stream().anyMatch(actor -> (Math.hypot(actor.getX() - human.getX(), actor.getY() - human.getY()) > 350) || actor.isMoving())))
			{
				if (movingLeader && (humanWaypoint < humanPath.size()))
				{
					var waypoint = humanPath.get(humanWaypoint);
					if (Math.hypot(human.getX() - waypoint.getX(), human.getY() - waypoint.getY()) <= 64)
					{
						humanWaypoint++;
						if (humanWaypoint < humanPath.size())
						{
							waypoint = humanPath.get(humanWaypoint);
							human.moveToLocation(waypoint.getX(), waypoint.getY(), waypoint.getZ(), 0);
						}
					}
					else if (!human.isMoving())
					{
						human.moveToLocation(waypoint.getX(), waypoint.getY(), waypoint.getZ(), 0);
					}
				}
				party.onPulse();
				for (int index = 0; index < actors.size(); index++)
				{
					final Player actor = actors.get(index);
					maximumStep = Math.max(maximumStep, Math.sqrt(Math.pow(actor.getX() - previous[index][0], 2) + Math.pow(actor.getY() - previous[index][1], 2) + Math.pow(actor.getZ() - previous[index][2], 2)));
					previous[index] = new int[] {actor.getX(), actor.getY(), actor.getZ()};
					PhantomAssertions.assertTrue(actor.isInParty() && (actor.getParty() == human.getParty()) && party.committed(profileIds.get(index)), "Follow lost a native mixed member during movement.");
				}
				Thread.sleep(20);
			}
			context.record("companion058.follow.human", "actual=" + human.getX() + ',' + human.getY() + ',' + human.getZ() + "; visited=" + humanWaypoint + '/' + (humanPath == null ? 0 : humanPath.size()) + "; spawned=" + human.isSpawned());
			for (Player actor : actors)
			{
				final double remaining = Math.hypot(actor.getX() - human.getX(), actor.getY() - human.getY());
				context.record("companion058.follow.result." + actor.getObjectId(), "xyz=" + actor.getX() + ',' + actor.getY() + ',' + actor.getZ() + "; remaining=" + remaining + "; maximumStep=" + maximumStep + "; navigation=" + navigationMetrics.snapshot().navigation());
				PhantomAssertions.assertTrue(remaining <= 350, "Real-led companion did not reach its leader: " + remaining + "; routes=" + routes.snapshot());
				PhantomAssertions.assertTrue(Math.hypot(actor.getX() - fromX, actor.getY() - fromY) >= 300, "Follow has no actual World displacement.");
			}
			if (movingLeader)
			{
				PhantomAssertions.assertTrue((humanWaypoint == humanPath.size()) && (Math.hypot(human.getX() - fromX, human.getY() - fromY) >= 600), "TEST human did not actually walk the declared route.");
			}
			PhantomAssertions.assertTrue(maximumStep <= 128, "Follow snapped instead of using native movement: " + maximumStep);
			PhantomAssertions.assertTrue(navigationMetrics.snapshot().navigation().pathSucceeded() > 0, "Follow did not use a computed native path around the obstacle.");
			PhantomAssertions.assertTrue(navigationMetrics.snapshot().navigation().submissionsAccepted() <= 120, "Friendly leader selection caused unbounded follow replanning.");
			if ((composition != null) && Boolean.getBoolean("phantom.test.commands059"))
			{
				composition.holdAndResume059(context, human, actors, party, fromX, fromY, z);
			}
			if (Set.of(Mode.COMPANION_ASSIST_058, Mode.COMPANION_RANGED_SPACING_058, Mode.COMPANION_FULL_COMPOSITION_058).contains(_mode))
			{
				testCompanionAssistTargets058(context, human, actors, profileIds, party);
			}
			if (composition != null)
			{
				composition.partyChat(context, human);
				if (Boolean.getBoolean("phantom.test.commandAuthority059"))
				{
					composition.authority059(context, human, actors, profileIds, party, fromX, fromY, z);
					testHeldOffensiveTarget059(context, composition, human, actors, profileIds, party);
					composition.lifecycle059(context, human, actors, profileIds, party);
				}
			}
			if (_mode == Mode.COMPANION_NPC_AGGRO_058)
			{
				testCompanionNpcAggro058(context, human, actors, profileIds, party);
			}
			if ((_mode == Mode.COMPANION_DEPARTURE_058) || ((composition != null) && Boolean.getBoolean("phantom.test.commandAuthority059")))
			{
				if (composition != null) { PhantomAssertions.assertTrue(composition.command059(context, human, actors.getFirst(), org.l2jmobius.gameserver.network.enums.ChatType.PARTY, "всем стоять", party), "Pre-leave HOLD was not delivered."); }
				org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
				for (int pulse = 0; (pulse < 32) && profileIds.stream().anyMatch(party::blocksBackground); pulse++)
				{
					party.onPulse();
				}
				context.record("companion058.departure", "humanParty=" + human.isInParty() + "; actorParties=" + actors.stream().map(Player::isInParty).toList() + "; pins=" + profileIds.stream().map(party::blocksBackground).toList() + "; routes=" + routes.snapshot());
				PhantomAssertions.assertFalse(profileIds.stream().anyMatch(party::blocksBackground), "Last human leave retained mixed companion pins.");
				PhantomAssertions.assertFalse(actors.stream().anyMatch(Player::isInParty), "Departed companion group retained a native party after release.");
				PhantomAssertions.assertEquals(0, routes.snapshot().movementClaims(), "Last human leave retained owned follow movement.");
				if (composition != null) { PhantomAssertions.assertTrue(profileIds.stream().allMatch(profile -> party.claim(profile).orElseThrow().state().command() == null), "Last human leave retained commands."); }
				joinCompanions058(human, actors, profileIds, party);
				if (composition != null) { PhantomAssertions.assertTrue(composition.command059(context, human, actors.getFirst(), org.l2jmobius.gameserver.network.enums.ChatType.PARTY, "всем стоять", party), "Pre-logout HOLD was not delivered."); }
				final int humanObjectId = human.getObjectId();
				human.storeMe();
				human.deleteMe(); // Ordinary native logout, not a crash or a forced process termination.
				_observer = null;
				for (int pulse = 0; (pulse < 32) && profileIds.stream().anyMatch(party::blocksBackground); pulse++)
				{
					party.onPulse();
				}
				context.record("companion058.logout", "humanWorld=" + (World.getInstance().getPlayer(humanObjectId) != null) + "; actorParties=" + actors.stream().map(Player::isInParty).toList() + "; pins=" + profileIds.stream().map(party::blocksBackground).toList() + "; routes=" + routes.snapshot());
				PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(humanObjectId), "Native TEST logout did not remove the exact World identity.");
				PhantomAssertions.assertFalse(profileIds.stream().anyMatch(party::blocksBackground), "Last human logout retained mixed companion pins.");
				PhantomAssertions.assertFalse(actors.stream().anyMatch(Player::isInParty), "Last human logout retained an abandoned companion native party.");
				if (composition != null) { PhantomAssertions.assertTrue(profileIds.stream().allMatch(profile -> party.claim(profile).orElseThrow().state().command() == null), "Native logout retained commands."); }
				_observer = Player.load(humanObjectId);
				PhantomAssertions.assertTrue(_observer != null && !_observer.isGM(), "Same TEST character could not reload after ordinary logout.");
				_observer.spawnMe();
				_observer.revalidateZone(true);
				joinCompanions058(_observer, actors, profileIds, party);
				if (composition != null) { PhantomAssertions.assertTrue(profileIds.stream().allMatch(profile -> party.claim(profile).orElseThrow().state().command() == null), "Same-character reconnect revived old commands."); }
				context.record("companion058.rejoin", "sameHuman=" + _observer.getObjectId() + "; members=" + _observer.getParty().getMembers().stream().map(Player::getObjectId).sorted().toList() + "; pins=" + profileIds.stream().map(party::blocksBackground).toList());
			}
		}
		catch (Exception | AssertionError failure)
		{
			scenarioFailure = failure;
			throw failure;
		}
		finally
		{
			if (composition != null)
			{
				composition.close();
			}
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
			for (int pulse = 0; pulse < 8; pulse++)
			{
				party.onPulse();
			}
			party.beginStop();
			final boolean partyStopped = party.finishStop();
			navigation.beginStop();
			boolean navigationStopped = navigation.snapshot().state() == PhantomNavigationService.ServiceState.STOPPED;
			final long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!navigationStopped && (System.nanoTime() < stopDeadline))
			{
				navigation.finishStop();
				navigationStopped = navigation.snapshot().state() == PhantomNavigationService.ServiceState.STOPPED;
				if (!navigationStopped)
				{
					Thread.sleep(10);
				}
			}
			progression.beginStop();
			final boolean progressionStopped = progression.finishStop();
			social.beginStop();
			final boolean socialStopped = social.finishStop();
			if (!partyStopped || !navigationStopped || !progressionStopped || !socialStopped)
			{
				final AssertionError cleanup = new AssertionError("Follow owners did not drain: party=" + partyStopped + ':' + party.snapshot() + "; navigation=" + navigationStopped + ':' + navigation.snapshot() + "; progression=" + progressionStopped + "; social=" + socialStopped);
				if (scenarioFailure != null)
				{
					scenarioFailure.addSuppressed(cleanup);
				}
				else
				{
					throw cleanup;
				}
			}
		}
	}

	private static void joinCompanions058(Player human, List<Player> actors, List<Long> profileIds, PhantomPartyCoordinator party)
	{
		for (int index = 0; index < actors.size(); index++)
		{
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().invite(human, actors.get(index), PartyDistributionType.FINDERS_KEEPERS.getId()).delivered(), "Native rejoin invitation was not delivered.");
			final long profileId = profileIds.get(index);
			for (int pulse = 0; (pulse < 32) && !party.committed(profileId); pulse++)
			{
				party.onPulse();
			}
			if (!party.committed(profileId) || (actors.get(index).getParty() != human.getParty()))
			{
				throw new AssertionError("Native companion did not rejoin its exact human: profile=" + profileId + "; claim=" + party.claim(profileId) + "; pending=" + party.pendingInvitation(profileId) + "; party=" + party.snapshot() + "; goal=" + new PhantomGoalStateStore(PhantomProfileRepository.open()).load(profileId) + "; dead=" + actors.get(index).isDead() + "; distance=" + actors.get(index).calculateDistance3D(human));
			}
		}
	}


	private void testNativeAreaAggro060(PhantomTestContext context, boolean negative) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var field = prepareLearningField059(context);
		final Player human = ensureObserver();
		prepareCompanionCombatAtLevel058(_player, 90, 2500, 0, 79, 6373, 6374, 6375, 6376, 6377, 6378);
		final Skill aura = SkillData.getInstance().getSkill(18, 37);
		PhantomAssertions.assertTrue(aura != null && _progression.classSkillLearns(90).stream().anyMatch(learn -> learn.skillId() == 18 && learn.skillLevel() == 37 && learn.minimumCharacterLevel() <= 79), "Declared Aura rank is absent from the native tank tree.");
		_player.addSkill(aura, false); _player.removeSkill(28, false);
		human.getStat().setExp(human.getStat().getExpForLevel(5)); human.setBaseClass(0); human.setPlayerClass(0); human.getStat().setLevel((byte) 5);
		human.setInvul(false); human.setCurrentHp(human.getMaxHp()); human.setCurrentMp(human.getMaxMp()); human.setCurrentCp(human.getMaxCp());
		placeInstanceInput058(human, field.x() + 30, field.y(), field.z());
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(party); human.joinParty(party);
		final List<Monster> enemies = new ArrayList<>();
		Player foreign = null;
		try
		{
			for (int offset : new int[] {-65,65})
			{
				final Monster enemy = new Monster(NpcData.getInstance().getTemplate(20120));
				PhantomAssertions.assertTrue(enemy.getTemplate().getClans() == null || enemy.getTemplate().getClans().isEmpty(), "Declared stock Wolf has native clan assist.");
				enemy.setCurrentHp(enemy.getMaxHp()); enemy.setCurrentMp(enemy.getMaxMp()); _worldFixtures.add(enemy); enemies.add(enemy);
				enemy.spawnMe(field.x() + 65, field.y() + offset, field.z());
			}
			context.record("group060.area.input", "Independent " + (negative ? "negative controls" : "positive episode") + "; prepared tank90/79/native18:37/finiteMP; human0/5/native initial gear; fixed native party; two full stock Wolf20120/4/noClans; human ATTACK creates actual hate before enrollment; area range=" + aura.getAffectRange() + "; reuse=" + aura.getReuseDelay() + "; no HP/MP/hate/XYZ writes after boundary; ordinary NPC only, no raid claim");
			for (Monster enemy : enemies)
			{
				human.setTarget(enemy); human.getAI().setIntention(Intention.ATTACK, enemy);
				PhantomAssertions.assertTrue(waitFor(() -> enemy.getHating(human) > 0 || enemy.isDead(), 8000) && !enemy.isDead(), "BLOCKED_INPUT: native human first hit did not establish a living stock recipient.");
				human.abortAttack(); human.getAI().setIntention(Intention.IDLE);
				PhantomAssertions.assertTrue(waitFor(() -> !human.isAttackingNow(), 3000), "Human native first-hit action did not retire.");
			}
			PhantomAssertions.assertTrue(enemies.stream().allMatch(enemy -> enemy.getMostHated() == human && !enemy.isDead()), "BLOCKED_INPUT: initial recipients are not attacking the declared native party member.");
			if (negative)
			{
				final Monster idle = new Monster(NpcData.getInstance().getTemplate(20120)); idle.setCurrentHp(idle.getMaxHp()); idle.setCurrentMp(idle.getMaxMp()); _worldFixtures.add(idle); idle.spawnMe(field.x() - 65, field.y(), field.z());
				try (var lease = _backend.tryAcquireActor(_profile.profileId()))
				{
					PhantomAssertions.assertTrue(lease != null, "Area negative lacks the existing actor lease.");
					final double mp = _player.getCurrentMp();
					PhantomAssertions.assertTrue(aura.getTargetList(_player, false, enemies.getFirst()).contains(idle), "Idle native recipient was absent from the actual area list.");
					PhantomAssertions.assertTrue(lease.npcAggroSkill(enemies.getFirst().getObjectId()).isEmpty(), "Area aggression admitted a fresh unrelated pull.");
					PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castNpcAggro(enemies.getFirst().getObjectId(), new SelectedSkill(18,37)), "Area aggression cast onto an idle recipient.");
					PhantomAssertions.assertTrue(_player.getCurrentMp() == mp && idle.getMostHated() == null, "Rejected idle-recipient control changed native MP or hate.");
				}
				idle.deleteMe();
				final var fixture = _environment.createCampaign058Companion(context, 1); foreign = Player.load(fixture.objectId());
				PhantomAssertions.assertTrue(foreign != null, "Owned foreign TEST player did not load.");
				foreign.setInvul(false); foreign.setPvpFlag(1); foreign.setPvpFlagLasts(System.currentTimeMillis() + PvpConfig.PVP_PVP_TIME); placeInstanceInput058(foreign, field.x() - 50, field.y(), field.z());
				PhantomAssertions.assertTrue(aura.getTargetList(_player, false, enemies.getFirst()).contains(foreign), "BLOCKED_INPUT: native Aura did not include the flagged foreign TEST player.");
				try (var lease = _backend.tryAcquireActor(_profile.profileId()))
				{
					final double mp = _player.getCurrentMp(); final var humanTarget = foreign.getTarget();
					PhantomAssertions.assertTrue(lease != null && lease.npcAggroSkill(enemies.getFirst().getObjectId()).isEmpty(), "Native area selected a list containing a foreign player.");
					PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castNpcAggro(enemies.getFirst().getObjectId(), new SelectedSkill(18,37)), "Area aggression forced a foreign player's target.");
					PhantomAssertions.assertTrue(foreign.getTarget() == humanTarget && _player.getCurrentMp() == mp && !_player.isSkillDisabled(aura), "Rejected foreign-recipient control changed target, MP or reuse.");
				}
				_environment.cleanupLoadedPlayer(foreign); foreign = null;
				final Location walk = java.util.Arrays.stream(new int[][] {{180,0},{-180,0},{0,180},{0,-180}}).map(offset -> new Location(field.x()+offset[0], field.y()+offset[1], GeoEngine.getInstance().getHeight(field.x()+offset[0],field.y()+offset[1],field.z()))).filter(point -> Math.abs(point.getZ()-field.z()) <= 8 && GeoEngine.getInstance().canMoveToTarget(field.x(),field.y(),field.z(),point.getX(),point.getY(),point.getZ(),0)).findFirst().orElseThrow(() -> new AssertionError("BLOCKED_INPUT: native walk frontend control lacks a direct legal corridor."));
				try (var lease = _backend.tryAcquireActor(_profile.profileId()))
				{
					PhantomAssertions.assertTrue(lease != null && aura.getTargetList(_player, false, enemies.getFirst()).size() == 2, "Moving control lacks the same exact two native recipients.");
					_player.getAI().setIntention(Intention.MOVE_TO, walk);
					PhantomAssertions.assertTrue(waitFor(_player::isMoving, 1000), "BLOCKED_INPUT: declared native MOVE_TO never started.");
					final double mp = _player.getCurrentMp();
					context.record("group060.area.frontend", "nativeMoving=" + _player.isMoving() + "; currentBowReuseUntil=" + _player.getBowAttackEndTime() + "; nativeRecipients=" + aura.getTargetList(_player, false, enemies.getFirst()).size());
					PhantomAssertions.assertTrue(lease.npcAggroSkill(enemies.getFirst().getObjectId()).isEmpty(), "Frozen area frontend selected a long cast while native movement was active.");
					PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castNpcAggro(enemies.getFirst().getObjectId(), new SelectedSkill(18,37)), "Frozen area frontend bypassed native movement admission.");
					PhantomAssertions.assertTrue(_player.getCurrentMp() == mp && !_player.isSkillDisabled(aura), "Rejected moving cast spent native MP or reuse.");
				}
				finally { _player.stopMove(null); }
				context.record("group060.area.negative", "actual native idle Monster, flagged foreign Player, and active native movement each refused; no MP/reuse/hate/forced-target effect");
				return;
			}
			final var nativeTargets = aura.getTargetList(_player, false, enemies.getFirst());
			PhantomAssertions.assertTrue(nativeTargets.size() == 2 && nativeTargets.containsAll(enemies), "BLOCKED_INPUT: native positive recipients differ from the declared two stock monsters: " + nativeTargets);
			final double mp = _player.getCurrentMp(); final var humanTarget = human.getTarget();
			final long beforeHate = enemies.stream().mapToLong(enemy -> enemy.getHating(_player)).sum();
			try (var lease = _backend.tryAcquireActor(_profile.profileId()))
			{
				PhantomAssertions.assertTrue(lease != null, "Area positive lacks the existing actor lease.");
				final var selected = lease.npcAggroSkill(enemies.getFirst().getObjectId());
				context.record("group060.area.selection", "nativeRecipients=" + nativeTargets.size() + "; selected=" + selected + "; beforeHate=" + beforeHate + "; beforeMP=" + mp);
				PhantomAssertions.assertEquals(java.util.Optional.of(new SelectedSkill(18,37)), selected, "Existing NPC tactics did not select the lawful learned native area aggression.");
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.castNpcAggro(enemies.getFirst().getObjectId(), selected.orElseThrow()), "Lawful native area cast was not issued.");
				long heldSince = 0; boolean redirected = false;
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
				while (System.nanoTime() < deadline)
				{
					final boolean controls = !_player.isCastingNow() && enemies.stream().allMatch(enemy -> enemy.getMostHated() == _player && (enemy.getTarget() == _player || (enemy.hasAI() && enemy.getAI().getAttackTarget() == _player)));
					if (controls) { if (heldSince == 0) { heldSince = System.nanoTime(); } if (System.nanoTime() - heldSince >= TimeUnit.SECONDS.toNanos(2)) { redirected = true; break; } } else { heldSince = 0; }
					Thread.sleep(20);
				}
				final long afterHate = enemies.stream().mapToLong(enemy -> enemy.getHating(_player)).sum();
				context.record("group060.area.effect", "actualAIRecipients=" + enemies.stream().map(enemy -> enemy.getObjectId() + "/" + (enemy.hasAI() && enemy.getAI().getAttackTarget() == _player)).toList() + "; redirected=" + redirected + "; hate=" + beforeHate + "->" + afterHate + "; MP=" + mp + "->" + _player.getCurrentMp() + "; nativeReuse=" + _player.isSkillDisabled(aura) + "; humanTargetUnchanged=" + (human.getTarget() == humanTarget));
				PhantomAssertions.assertTrue(redirected && _player.getCurrentMp() < mp && _player.isSkillDisabled(aura) && human.getTarget() == humanTarget && enemies.stream().noneMatch(Monster::isDead), "Area CAST lacks both native NPC retarget effects/cost/reuse or changed the human target.");
			}
		}
		finally
		{
			for (Monster enemy : enemies) { enemy.abortAttack(); enemy.getAI().setIntention(Intention.IDLE); enemy.deleteMe(); }
			human.abortAttack(); human.getAI().setIntention(Intention.IDLE); human.setTarget(null);
			if (foreign != null) { foreign.setPvpFlag(0); foreign.setPvpFlagLasts(0); _environment.cleanupLoadedPlayer(foreign); }
			if (human.isInParty()) { PartyInvitationService.getInstance().leave(human); } if (_player.isInParty()) { PartyInvitationService.getInstance().leave(_player); }
		}
	}

	private void testCompanionNpcAggro058(PhantomTestContext context, Player human, List<Player> actors, List<Long> profileIds, PhantomPartyCoordinator party) throws Exception
	{
		final Player tank = actors.get(0);
		final Player healer = actors.get(2);
		final Skill aggression = tank.getKnownSkill(28);
		PhantomAssertions.assertTrue(aggression != null && (aggression.getLevel() > 1), "TEST tank lacks its actual learned higher Aggression rank.");
		try (var negative = _backend.tryAcquireActor(profileIds.get(0)))
		{
			PhantomAssertions.assertTrue(negative != null, "NPC aggro negative control lacks its own actor lease.");
			final var humanTarget = human.getTarget();
			final var tankTarget = tank.getTarget();
			PhantomAssertions.assertTrue(negative.npcAggroSkill(human.getObjectId()).isEmpty(), "NPC aggression selected a real player target.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, negative.castNpcAggro(human.getObjectId(), new SelectedSkill(aggression.getId(), aggression.getLevel())), "NPC aggression forced a real player's target.");
			PhantomAssertions.assertEquals(humanTarget, human.getTarget(), "Rejected aggression changed the human target.");
			PhantomAssertions.assertEquals(tankTarget, tank.getTarget(), "Rejected aggression changed the tank target.");
		}
		// Explicit preparatory boundary after the walk and before this independent combat episode.
		tank.setCurrentHp(tank.getMaxHp() * 0.75);
		final double preparedHp = tank.getCurrentHp();
		final double initialHealerMp = healer.getCurrentMp();
		final Monster target = new Monster(NpcData.getInstance().getTemplate(18008));
		target.setCurrentHp(target.getMaxHp());
		target.setCurrentMp(target.getMaxMp());
		_worldFixtures.add(target);
		final double initialMageMp = actors.get(1).getCurrentMp();
		final var basicHits = new java.util.concurrent.atomic.AtomicInteger();
		final var basicDamage = new java.util.concurrent.atomic.AtomicReference<Double>(0.0);
		final var damageListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(target, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DAMAGE_RECEIVED, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived event) ->
		{
			if (event.getTarget() == target && event.getAttacker() == actors.get(1) && event.getSkill() != null && event.getSkill().getId() == 1230 && event.getDamage() > 1) { basicHits.incrementAndGet(); basicDamage.set(event.getDamage()); }
		}, this);
		target.addListener(damageListener);
		try
		{
			context.record("companion058.aggro.input", "DECLARED TEST combat boundary; tank=" + tank.getObjectId() + "; hp75percent=" + preparedHp + "; healer=" + healer.getObjectId() + "; skill=" + aggression.getId() + ':' + aggression.getLevel() + "; nativeMpCost=" + (aggression.getMpInitialConsume() + aggression.getMpConsume()) + "; stockNpc18008/70/fullHP=" + target.getMaxHp() + "; object=" + target.getObjectId() + "; xyz=-71480,258616,-3104; human native ATTACK establishes threat, then party pulse; deadline35s; first heal must begin above55percent under actual NPC pressure; no hate/HP/MP/reward writes inside measurement");
			target.spawnMe(-71480, 258616, -3104);
			human.setTarget(target);
			human.getAI().setIntention(Intention.ATTACK, target);
			final boolean humanThreat = waitFor(() -> target.getHating(human) > 0, 8000);
			context.record("companion058.aggro.humanInput", "threat=" + humanThreat + "; online=" + human.isOnline() + "; spawned=" + human.isSpawned() + "; disabled=" + human.isAllSkillsDisabled() + "; sitting=" + human.isSitting() + "; moving=" + human.isMoving() + "; movementDisabled=" + human.isMovementDisabled() + "; attackDisabled=" + human.isAttackDisabled() + "; casting=" + human.isCastingNow() + "; intention=" + human.getAI().getIntention() + "; range=" + human.getPhysicalAttackRange() + "; distance=" + Math.hypot(human.getX() - target.getX(), human.getY() - target.getY()) + "; los=" + GeoEngine.getInstance().canSeeTarget(human, target) + "; npcHp=" + target.getCurrentHp() + "; arrows=" + human.getInventory().getInventoryItemCount(17, -1));
			PhantomAssertions.assertTrue(humanThreat, "TEST human did not establish actual native attack threat.");
			PhantomAssertions.assertEquals(human, target.getMostHated(), "Native initial threat does not belong to the declared TEST human.");
			final long initialTankHate = target.getHating(tank);
			final long initialExp = actors.stream().mapToLong(Player::getExp).sum();
			boolean castObserved = false;
			boolean reuseObserved = false;
			boolean healObserved = false;
			double firstHealHpPercent = Double.NaN;
			boolean firstHealNpcPressure = false;
			long heldSince = 0;
			long maximumHeld = 0;
			long maximumTankHate = initialTankHate;
			double previousHp = tank.getCurrentHp();
			double minimumHealerMp = initialHealerMp;
			double minimumMageMp = initialMageMp;
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
			while (!target.isDead() && (System.nanoTime() < deadline))
			{
				party.onPulse();
				if (Double.isNaN(firstHealHpPercent) && healer.isCastingNow() && (healer.getLastSkillCast() != null) && (healer.getLastSkillCast().getId() == 1335))
				{
					firstHealHpPercent = 100 * tank.getCurrentHp() / tank.getMaxHp();
					firstHealNpcPressure = target.hasAI() && (target.getAI().getIntention() == Intention.ATTACK) && (target.getAI().getAttackTarget() == tank);
				}
				castObserved |= (tank.getLastSkillCast() != null) && (tank.getLastSkillCast().getId() == aggression.getId()) && (tank.getLastSkillCast().getLevel() == aggression.getLevel());
				reuseObserved |= tank.isSkillDisabled(aggression);
				maximumTankHate = Math.max(maximumTankHate, target.getHating(tank));
				final boolean controls = (target.getMostHated() == tank) && ((target.getTarget() == tank) || (target.hasAI() && (target.getAI().getAttackTarget() == tank)));
				if (controls)
				{
					if (heldSince == 0)
					{
						heldSince = System.nanoTime();
					}
					maximumHeld = Math.max(maximumHeld, System.nanoTime() - heldSince);
				}
				else
				{
					heldSince = 0;
				}
				minimumHealerMp = Math.min(minimumHealerMp, healer.getCurrentMp());
				minimumMageMp = Math.min(minimumMageMp, actors.get(1).getCurrentMp());
				healObserved |= (tank.getCurrentHp() - previousHp > 100) && (healer.getLastSkillCast() != null) && (healer.getLastSkillCast().getId() == 1335);
				previousHp = tank.getCurrentHp();
				for (int index = 0; index < actors.size(); index++)
				{
					PhantomAssertions.assertTrue(actors.get(index).isInParty() && (actors.get(index).getParty() == human.getParty()) && party.committed(profileIds.get(index)), "NPC aggro episode lost a declared mixed member.");
				}
				Thread.sleep(20);
			}
			final long rewardDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (target.isDead() && (actors.stream().mapToLong(Player::getExp).sum() <= initialExp) && (System.nanoTime() < rewardDeadline))
			{
				party.onPulse();
				Thread.sleep(20);
			}
			context.record("companion058.aggro.basicEffect", "productionProgressionResolver=true; actual1230usefulHits=" + basicHits.get() + "; lastNativeDamage=" + basicDamage.get() + "; mageMP=" + initialMageMp + "->" + minimumMageMp);
			context.record("companion058.aggro.damage", "npcHP=" + target.getCurrentHp() + '/' + target.getMaxHp() + "; nativeActors=" + actors.stream().map(actor -> actor.getObjectId() + "/class" + actor.getActiveClass() + "/last" + actor.getLastSkillCast() + "/mp" + actor.getCurrentMp() + "/cast" + actor.isCastingNow() + "/attack" + actor.isAttackingNow() + "/move" + actor.isMoving() + "/target" + (actor.getTarget() == null ? 0 : actor.getTarget().getObjectId())).toList());
			context.record("companion058.aggro.result", "nativeCast=" + castObserved + "; nativeReuse=" + reuseObserved + "; tankHate=" + initialTankHate + "->" + maximumTankHate + "; heldNanos=" + maximumHeld + "; actualHeal=" + healObserved + "; healerMp=" + initialHealerMp + "->" + minimumHealerMp + "; tankHp=" + preparedHp + "->" + tank.getCurrentHp() + "; deadNpc=" + target.isDead() + "; nativePartyExp=" + initialExp + "->" + actors.stream().mapToLong(Player::getExp).sum());
			context.record("companion058.aggro.earlyHeal", "firstCastHpPercent=" + firstHealHpPercent + "; actualNpcPressure=" + firstHealNpcPressure);
			PhantomAssertions.assertTrue(firstHealNpcPressure && (firstHealHpPercent > 55), "Threatened native party member did not start healing before the old55percent boundary.");
			PhantomAssertions.assertTrue(castObserved && reuseObserved, "Tank never used its current learned native NPC aggression skill/reuse.");
			PhantomAssertions.assertTrue((maximumTankHate > initialTankHate) && (maximumHeld >= TimeUnit.SECONDS.toNanos(2)), "Tank did not actually recover and hold native NPC aggression.");
			PhantomAssertions.assertTrue(healObserved && (minimumHealerMp < initialHealerMp), "Party healer produced no actual heal/cost during the live NPC fight.");
			PhantomAssertions.assertTrue(target.isDead() && (actors.stream().mapToLong(Player::getExp).sum() > initialExp), "Native aggro fight produced no actual stock kill/reward.");
			PhantomAssertions.assertFalse(actors.stream().anyMatch(Player::isDead), "A declared companion died during the native NPC aggro episode.");
			PhantomAssertions.assertTrue(basicHits.get() > 0 && basicDamage.get() > 1 && minimumMageMp < initialMageMp, "Production-derived current learned Prominence has no useful native damage above the resist floor or MP cost.");
			human.setTarget(null);
		}
		finally { target.removeListener(damageListener); }
	}

	private void prepareCompanionCombat058(Player actor, int classId, int weaponId, int skillId, int... armorIds)
	{
		prepareCompanionCombatAtLevel058(actor, classId, weaponId, skillId, 80, armorIds);
	}

	private void prepareCompanionCombatAtLevel058(Player actor, int classId, int weaponId, int skillId, int level, int... armorIds)
	{
		PhantomAssertions.assertTrue(level == 79 || level == 80, "Only the declared native campaign79/80 inputs are supported.");
		actor.getStat().setExp(actor.getStat().getExpForLevel(level));
		actor.setBaseClass(classId);
		actor.setPlayerClass(classId);
		actor.getStat().setLevel((byte) level);
		actor.addSkill(SkillData.getInstance().getSkill(239, level >= 80 ? 6 : 5), true);
		if (skillId > 0)
		{
			PhantomAssertions.assertTrue(_progression.classSkillLearns(classId).stream().anyMatch(learn -> (learn.skillId() == skillId) && (learn.skillLevel() == 1) && (learn.minimumCharacterLevel() <= level)), "TEST offensive skill is not in the current class tree.");
			actor.addSkill(SkillData.getInstance().getSkill(skillId, 1), true);
		}
		if (weaponId > 0)
		{
			final Item weapon = actor.getInventory().addItem(ItemProcessType.REWARD, weaponId, 1, null, this);
			PhantomAssertions.assertTrue(weapon != null, "TEST stock weapon is unavailable.");
			actor.getInventory().equipItem(weapon);
		}
		for (int itemId : armorIds)
		{
			final Item armor = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this);
			PhantomAssertions.assertTrue(armor != null, "Declared TEST stock armor is unavailable.");
			actor.getInventory().equipItem(armor);
		}
		actor.refreshExpertisePenalty();
		actor.refreshOverloaded();
		actor.setCurrentHp(actor.getMaxHp());
		actor.setCurrentMp(actor.getMaxMp());
		actor.setCurrentCp(actor.getMaxCp());
	}

	private void testHeldOffensiveTarget059(PhantomTestContext context, PhantomCompanionComposition058 composition, Player human, List<Player> actors, List<Long> profiles, PhantomPartyCoordinator party) throws Exception
	{
		final NpcFact fact = _query.snapshot().npcById().values().stream().filter(npc -> npc.kind() == NpcKind.MONSTER && npc.attackable() && npc.targetable() && npc.level() >= 70 && npc.level() <= 72 && NpcData.getInstance().getTemplate(npc.npcId()).getAggroRange() == 0).sorted(Comparator.comparingInt(NpcFact::npcId)).findFirst().orElseThrow(() -> new AssertionError("INVALID_INPUT: no stock non-aggressive70..72 HOLD target."));
		Location point = null;
		for (int[] offset : new int[][] {{900,0},{0,900},{-900,0},{0,-900},{700,700},{-700,700},{700,-700},{-700,-700}})
		{
			final int x = human.getX() + offset[0], y = human.getY() + offset[1];
			final int z = GeoEngine.getInstance().getHeight(x, y, human.getZ());
			if (GeoEngine.getInstance().hasGeo(x, y) && actors.stream().allMatch(actor -> Math.hypot(actor.getX() - x, actor.getY() - y) > 500 && GeoEngine.getInstance().canSeeTarget(actor.getX(), actor.getY(), actor.getZ(), 0, x, y, z, 0))) { point = new Location(x,y,z); break; }
		}
		PhantomAssertions.assertTrue(point != null, "INVALID_INPUT: no native LOS point for independent distant HOLD target.");
		final Monster target = new Monster(NpcData.getInstance().getTemplate(fact.npcId()));
		target.setCurrentHp(target.getMaxHp()); target.setCurrentMp(target.getMaxMp());
		_worldFixtures.add(target); target.spawnMe(point.getX(), point.getY(), point.getZ());
		context.record("command059.offense.input", "INDEPENDENT negative after two full kills; stock non-aggressive target=" + target.getId() + "; object=" + target.getObjectId() + "; fullHP=" + target.getMaxHp() + "; XYZ=" + point + "; all3 HOLD; native leader selects target farther than500;5s no pursuit/damage; no existing episode or resource modification");
		PhantomAssertions.assertTrue(composition.command059(context, human, actors.getFirst(), org.l2jmobius.gameserver.network.enums.ChatType.PARTY, "всем стоять", party), "HOLD target negative had no delivered admission.");
		composition.awaitHolding059(context, party, profiles);
		final int[][] anchors = actors.stream().map(actor -> new int[] {actor.getX(),actor.getY(),actor.getZ()}).toArray(int[][]::new);
		human.setTarget(target);
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline)
		{
			party.onPulse();
			PhantomAssertions.assertEquals((double) target.getMaxHp(), target.getCurrentHp(), "HOLD started native offense against the leader's distant target.");
			for (int index=0; index<actors.size(); index++) { final Player actor=actors.get(index); PhantomAssertions.assertTrue(!actor.isAttackingNow() && Math.hypot(actor.getX()-anchors[index][0],actor.getY()-anchors[index][1]) <=128, "HOLD pursued the selected enemy."); }
			Thread.sleep(20);
		}
		human.setTarget(null);
		PhantomAssertions.assertTrue(composition.command059(context, human, actors.getFirst(), org.l2jmobius.gameserver.network.enums.ChatType.PARTY, "всем за мной", party), "HOLD target negative RESUME was not delivered.");
		composition.assertCommandReason059("command.following");
		context.record("command059.offense.result", "all3 current native roles retained anchors; no pursuit/attack/damage for5s despite leader selection; full stock HP unchanged; lawful RESUME delivered");
	}

	private void testCompanionAssistTargets058(PhantomTestContext context, Player human, List<Player> actors, List<Long> profileIds, PhantomPartyCoordinator party) throws Exception
	{
		final NpcFact fact = _query.snapshot().npcById().values().stream().filter(npc -> (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable() && (npc.level() >= 70) && (npc.level() <= 72) && (npc.exp() > 0)).sorted(Comparator.comparingInt(NpcFact::npcId)).findFirst().orElseThrow(() -> new AssertionError("No stock70..72 reward monster for companion input."));
		final List<Monster> targets = new ArrayList<>();
		for (int index = 0; index < 2; index++)
		{
			final Monster target = new Monster(NpcData.getInstance().getTemplate(fact.npcId()));
			target.setCurrentHp(target.getMaxHp());
			target.setCurrentMp(target.getMaxMp());
			targets.add(target);
			_worldFixtures.add(target);
		}
		Location point = null;
		// First candidate is the verified native route's last corner (-71480,258616), not an assumed open field.
		for (int[] offset : new int[][] {{-128, -128}, {0, 300}, {-200, 300}, {-300, 0}, {300, 0}, {0, -300}, {-300, -200}})
		{
			final int x = human.getX() + offset[0];
			final int y = human.getY() + offset[1];
			final int height = GeoEngine.getInstance().getHeight(x, y, human.getZ());
			if (GeoEngine.getInstance().hasGeo(x, y) && actors.stream().allMatch(actor -> (Math.hypot(actor.getX() - x, actor.getY() - y) >= 80) && GeoEngine.getInstance().canSeeTarget(actor.getX(), actor.getY(), actor.getZ(), 0, x, y, height, 0) && GeoEngine.getInstance().canMoveToTarget(actor.getX(), actor.getY(), actor.getZ(), x, y, height, 0)))
			{
				point = new Location(x, y, height);
				break;
			}
		}
		PhantomAssertions.assertTrue(point != null, "Declared companion combat point lacks native LOS/path.");
		final Player mage = actors.get(1);
		final Player archer = actors.get(2);
		final double initialMageMp = mage.getCurrentMp();
		final long initialArrows = archer.getInventory().getInventoryItemCount(1345, -1);
		final long[] damage = new long[actors.size()];
		final double[] firstRangedActionDistance = { Double.NaN, Double.NaN };
		final double[] maximumRangedDistance = { 0, 0 };
		boolean nativeMageCast = false;
		boolean activeHandoff058 = false;
		context.record("companion058.assist.input", "stockNpc=" + fact.npcId() + "; level=" + fact.level() + "; stockExp=" + fact.exp() + "; objects=" + targets.stream().map(Monster::getObjectId).toList() + "; fullHP=" + targets.stream().map(Monster::getMaxHp).toList() + "; xyz=" + point + "; two consecutive targets;45s each; no stats/rates/reward/actor refill inside measurement");
		for (Monster target : targets)
		{
			final long expBefore = actors.stream().mapToLong(Player::getExp).sum();
			target.spawnMe(point.getX(), point.getY(), point.getZ());
			if ((_mode == Mode.COMPANION_RANGED_SPACING_058) && (target == targets.getFirst()))
			{
				context.record("spacing058.input", "TEST existing3DPS/80/Sgear/nativefollow; firstNpc=" + target.getObjectId() + "; initialMage=" + mage.calculateDistance3D(target) + "; initialArcher=" + archer.calculateDistance3D(target) + "; expected first native ranged actions>=200 and actual retreat>=240; no movement/HP/MP/item writes during measurement");
				PhantomAssertions.assertTrue(mage.calculateDistance3D(target) < 200 && archer.calculateDistance3D(target) < 200, "Declared close-range spacing input is invalid.");
			}
			human.setTarget(target);
			for (int index = 0; index < actors.size(); index++)
			{
				context.record("companion058.assist.readiness." + target.getObjectId() + '.' + profileIds.get(index), party.supportCapabilities(profileIds.get(index), target.getObjectId()).stream().filter(capability -> Set.of("combat.melee_damage", "combat.ranged_magic_damage", "combat.ranged_physical_damage").contains(capability.capabilityKey())).limit(4).toList());
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
			while (!target.isDead() && (System.nanoTime() < deadline))
			{
				party.onPulse();
				if ((_mode == Mode.COMPANION_FULL_COMPOSITION_058) && !activeHandoff058 && (target == targets.getLast()) && (actors.getFirst().isAttackingNow() || mage.isCastingNow()))
				{
					final long generation = party.claim(profileIds.getFirst()).orElseThrow().state().groupGeneration();
					context.record("companion058.handoff.active", "nativeAttack=" + actors.getFirst().isAttackingNow() + "; nativeCast=" + mage.isCastingNow() + "; oldGeneration=" + generation);
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome.COMPLETED, org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().transferLeader(human, actors.getFirst()), "Active native leader handoff failed.");
					for (int pulse = 0; pulse < 8; pulse++) { party.onPulse(); }
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome.NOT_LEADER, org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().transferLeader(human, actors.getFirst()), "Former human leader retained control.");
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome.COMPLETED, org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().transferLeader(actors.getFirst(), human), "Active native leader return failed.");
					for (int pulse = 0; pulse < 8; pulse++) { party.onPulse(); }
					for (long profileId : profileIds)
					{
						final var claim = party.claim(profileId).orElseThrow().state();
						PhantomAssertions.assertTrue((claim.groupGeneration() > generation) && (claim.leader().characterObjectId() == human.getObjectId()), "Active handoff retained stale per-member authority.");
					}
					activeHandoff058 = true;
				}
				for (int index = 0; index < actors.size(); index++)
				{
					final Player actor = actors.get(index);
					final var aggro = target.getAggroList().get(actor);
					damage[index] = Math.max(damage[index], aggro == null ? 0 : aggro.getDamage());
					PhantomAssertions.assertTrue(actor.isInParty() && (actor.getParty() == human.getParty()) && party.committed(profileIds.get(index)), "Native assist lost its exact mixed member.");
				}
				nativeMageCast |= (mage.getLastSkillCast() != null) && (mage.getLastSkillCast().getId() == 1339) && (mage.getCurrentMp() < initialMageMp);
				for (int rangedIndex = 0; rangedIndex < 2; rangedIndex++)
				{
					final Player ranged = actors.get(rangedIndex + 1);
					final double distance = ranged.calculateDistance3D(target);
					maximumRangedDistance[rangedIndex] = Math.max(maximumRangedDistance[rangedIndex], distance);
					if (Double.isNaN(firstRangedActionDistance[rangedIndex]) && (ranged.isCastingNow() || ranged.isAttackingNow()))
					{
						firstRangedActionDistance[rangedIndex] = distance;
					}
				}
				Thread.sleep(20);
			}
			for (int index = 0; index < actors.size(); index++)
			{
				final var contribution = target.getAggroList().get(actors.get(index));
				damage[index] = Math.max(damage[index], contribution == null ? 0 : contribution.getDamage());
			}
			// Dead is published before the native reward boundary has necessarily settled.
			final long rewardDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (target.isDead() && (actors.stream().mapToLong(Player::getExp).sum() <= expBefore) && (System.nanoTime() < rewardDeadline))
			{
				party.onPulse();
				Thread.sleep(20);
			}
			final long expAfter = actors.stream().mapToLong(Player::getExp).sum();
			context.record("companion058.assist.target." + target.getObjectId(), "dead=" + target.isDead() + "; hp=" + target.getCurrentHp() + "; partyExp=" + expBefore + "->" + expAfter + "; mageCast=" + nativeMageCast + "; damage=" + java.util.Arrays.toString(damage) + "; roles=" + actors.stream().map(actor -> actor.getPlayerClass().getId() + ":hp=" + actor.getCurrentHp() + ":mp=" + actor.getCurrentMp() + ":cast=" + (actor.getLastSkillCast() == null ? 0 : actor.getLastSkillCast().getId())).toList() + "; combat=" + profileIds.stream().map(_combat::find).toList());
			PhantomAssertions.assertTrue(target.isDead(), "Native companions did not kill the selected stock target.");
			PhantomAssertions.assertFalse(actors.stream().anyMatch(Player::isDead), "A declared companion died during the native assist episode.");
			PhantomAssertions.assertTrue(expAfter > expBefore, "Native party kill produced no actual earned EXP.");
		}
		human.setTarget(null);
		context.record("companion058.assist.roles", "damage=" + java.util.Arrays.toString(damage) + "; mageMp=" + initialMageMp + "->" + mage.getCurrentMp() + "; arrows=" + initialArrows + "->" + archer.getInventory().getInventoryItemCount(1345, -1) + "; nativeMageCast=" + nativeMageCast);
		context.record("companion058.assist.sessions", _combat.metrics().sessionsAccepted());
		final int maximumSessions = _mode == Mode.COMPANION_FULL_COMPOSITION_058 ? 12 : 6; // Two declared generation changes may each replace three actor sessions.
		PhantomAssertions.assertTrue(_combat.metrics().sessionsAccepted() <= maximumSessions, "Lower-priority directives restarted the same actor/target combat session.");
		if (_mode == Mode.COMPANION_FULL_COMPOSITION_058) { PhantomAssertions.assertTrue(activeHandoff058, "Declared active-action leader handoff never occurred."); }
		PhantomAssertions.assertTrue(nativeMageCast, "Party mage never performed a native offensive cast with MP cost.");
		PhantomAssertions.assertTrue(archer.getInventory().getInventoryItemCount(1345, -1) < initialArrows, "Party archer never consumed a native arrow.");
		PhantomAssertions.assertTrue(java.util.Arrays.stream(damage).allMatch(amount -> amount > 0), "One companion role produced no actual native damage.");
		if (_mode == Mode.COMPANION_RANGED_SPACING_058)
		{
			context.record("spacing058.result", "firstNativeActionDistance=" + java.util.Arrays.toString(firstRangedActionDistance) + "; maximumActualDistance=" + java.util.Arrays.toString(maximumRangedDistance));
			PhantomAssertions.assertTrue(java.util.Arrays.stream(firstRangedActionDistance).allMatch(value -> value >= 200), "Mage/bow issued their first native action in unsafe close range.");
			PhantomAssertions.assertTrue(java.util.Arrays.stream(maximumRangedDistance).allMatch(value -> value >= 240), "Mage/bow did not actually establish native spacing.");
		}
	}

	private void testCompanionRealSupport058(PhantomTestContext context) throws Exception
	{
		testCompanionNativeRole058(context, "combat.buff");
		if (Boolean.getBoolean("phantom.test.socialContexts059")) { testNativeSocialContexts059(context); }
	}

	private void testNativeSocialContexts059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final Player human = ensureObserver();
		final var subject = PhantomSocialModel.SubjectRef.character(human.getObjectId());
		final var catalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final var store = new PhantomSocialStore(_repository, catalog);
		final long minute = System.currentTimeMillis() / 60000L;
		final var initialize = new PhantomSocialService(catalog, store, context.seed(), 16);
		PhantomAssertions.assertTrue(initialize.start() && initialize.snapshot(_profile.profileId(), subject, 1, minute).value() != null, "Social059 fixture initialization failed.");
		initialize.beginStop(); PhantomAssertions.assertTrue(initialize.finishStop(), "Social059 fixture initializer did not stop.");
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final List<String> replies = new ArrayList<>();
		try
		{
			for (boolean friendly : List.of(true, false))
			{
				// Independent declared TEST context before speech measurement; no058a state is altered.
				final var before = store.load(_profile.profileId()).orElseThrow();
				final var values = new ArrayList<>(java.util.Collections.nCopies(PhantomSocialModel.DIMENSION_COUNT, 0));
				for (var dimension : catalog.dimensions())
				{
					if (friendly && dimension.key().equals("trust")) { values.set(dimension.index(), 3000); }
					if (friendly && dimension.key().equals("friendship")) { values.set(dimension.index(), 2000); }
					if (!friendly && dimension.key().equals("anger")) { values.set(dimension.index(), 4000); }
				}
				final var relationships = new ArrayList<>(before.state().relationships());
				relationships.removeIf(value -> value.subject().equals(subject));
				relationships.add(new PhantomSocialModel.RelationshipRecord(subject, values, java.util.Collections.nCopies(PhantomSocialModel.AGREEMENT_COUNT, 0), minute, minute));
				final var prepared = new PhantomSocialModel.SocialState(before.state().authorityHash(), before.state().personalitySeed(), Math.max(minute, before.state().logicalMinute()), before.state().traits(), relationships, before.state().memories());
				store.save(_profile.profileId(), before.stateRowVersion(), before.receiptRowVersion(), prepared, before.receipts());
				context.record("social059.fixture." + friendly, "FIXTURE_PRECONDITION exact058t; same personality seed=" + prepared.personalitySeed() + "; explicit relationship context=" + (friendly ? "TRUSTED" : "HOSTILE") + "; native greeting measurement starts next");
				final var social = new PhantomSocialService(catalog, store, context.seed(), 16);
				PhantomAssertions.assertTrue(social.start(), "Social059 context service did not start.");
				final var backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
				final var party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 16);
				PhantomAssertions.assertTrue(party.start(), "Social059 context party owner did not start.");
				final var humanized = new PhantomHumanizedConversationService(PhantomHumanizedCatalog.loadV3(Path.of("data/phantoms"), true), PhantomPersonalConversationStore.production(_repository), social, new PhantomHumanizedConversationService.Settings(true, PhantomHumanizedCatalog.Register.CASUAL, PhantomHumanizedCatalog.ProfanityMode.NONE, PhantomHumanizedCatalog.Variation.LOW, false));
				try (var composition = new PhantomCompanionComposition058(_repository, _materialization, _topology, _knowledge, social, party, human, humanized))
				{
					replies.add(composition.socialContext059(context, human, _player, friendly ? org.l2jmobius.gameserver.network.enums.ChatType.GENERAL : org.l2jmobius.gameserver.network.enums.ChatType.TRADE, friendly ? "warm" : "cold"));
				}
				finally
				{
					party.beginStop(); PhantomAssertions.assertTrue(party.finishStop(), "Social059 context party owner did not stop.");
					social.beginStop(); PhantomAssertions.assertTrue(social.finishStop(), "Social059 context service did not stop.");
				}
			}
			PhantomAssertions.assertTrue(replies.size() == 2 && !replies.getFirst().equals(replies.getLast()), "Two native relationship contexts did not produce distinct delivered greetings.");
		}
		finally { progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Social059 progression did not stop."); }
	}

	private void testNativeSupportKind059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final Player human = ensureObserver();
		final var creation = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
		for (Player actor : List.of(_player, human)) { actor.decayMe(); actor.setXYZInvisible(creation.getX(), creation.getY(), creation.getZ()); actor.spawnMe(); actor.revalidateZone(true); }
		prepareCompanionCombat058(_player, 105, 6579, 0);
		final Skill prophecy = SkillData.getInstance().getSkill(1355, 1);
		_player.addSkill(prophecy, false); _player.setCurrentMp(_player.getMaxMp() / 2);
		PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, prophecy.getItemConsumeId(), prophecy.getItemConsumeCount() * 2L, null, this) != null, "Declared native Prophecy reagent is unavailable.");
		human.setInvul(false); human.setCurrentHp(human.getMaxHp() / 5); human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, prophecy.getId());
		final Party nativeParty = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(nativeParty); human.joinParty(nativeParty);
		final var rule = _progression.capabilityRules("combat.heal").stream().filter(value -> value.classIds().contains(105) && value.actionSkill().skillId() == 1355).findFirst().orElseThrow();
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final double beforeHp = human.getCurrentHp(), beforeMp = _player.getCurrentMp();
		final long beforeOre = _player.getInventory().getInventoryItemCount(prophecy.getItemConsumeId(), -1);
		final var evaluation = progression.capabilities(_profile.profileId(), human.getObjectId()).stream().filter(value -> value.capabilityKey().equals("combat.heal") && value.actionSkill().skillId() == 1355).findFirst().orElseThrow();
		final var acquired = _combat.acquireExternalAction(new PhantomCombatService.ExternalActionRequest(_profile.profileId(), PhantomCombatService.ExternalActionKind.PARTY_SUPPORT, "task059.support.kind", System.nanoTime() + TimeUnit.SECONDS.toNanos(20), () -> false));
		try
		{
			PhantomAssertions.assertTrue(acquired.lease() != null, "Native support-kind actor lease was not acquired.");
			final var outcome = acquired.lease().castSupport(new PhantomPartySupportAction("combat.heal", rule.variantKey(), rule.targetScope().name(), human.getObjectId(), new SelectedSkill(1355, 1)));
			if (outcome == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome.ISSUED) { waitFor(() -> PhantomSupportEffectAuthority.status(human, prophecy, 0) == PhantomSupportEffectAuthority.Status.HEALTHY && !_player.isCastingNow(), 10_000); }
			context.record("support059.kind", "native class105 skill1355=ProphecyWater; declared heal readiness=" + evaluation.readyNow() + '/' + evaluation.reason() + "; cast=" + outcome + "; actualBuff=" + PhantomSupportEffectAuthority.status(human, prophecy, 0) + "; targetHP=" + beforeHp + "->" + human.getCurrentHp() + "; casterMP=" + beforeMp + "->" + _player.getCurrentMp() + "; ore=" + beforeOre + "->" + _player.getInventory().getInventoryItemCount(prophecy.getItemConsumeId(), -1));
			PhantomAssertions.assertTrue(!evaluation.readyNow() && outcome == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome.REJECTED, "Native heal label admitted a non-healing buff and its native costs.");
		}
		finally
		{
			if (acquired.lease() != null) { acquired.lease().close(); }
			PartyInvitationService.getInstance().leave(human); if (_player.isInParty()) { PartyInvitationService.getInstance().leave(_player); }
			progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Support-kind progression did not stop.");
		}
	}

	private void testCompanionNativeRole058(PhantomTestContext context, String capabilityKey) throws Exception
	{
		testCompanionNativeRole058(context, capabilityKey, 0);
	}

	private void testCompanionNativeRole058(PhantomTestContext context, String capabilityKey, int preferredClass) throws Exception
	{
		final var creationPoint = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
		_player.decayMe();
		_player.setXYZInvisible(creationPoint.getX(), creationPoint.getY(), creationPoint.getZ());
		_player.spawnMe();
		_player.revalidateZone(true);
		final Player human = ensureObserver();
		if (human.isSpawned())
		{
			human.decayMe();
		}
		human.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
		human.spawnMe();
		human.revalidateZone(true);
		PhantomAssertions.assertTrue(!_player.isGM() && !human.isGM(), "Companion support requires ordinary TEST players.");
		final CapabilityRule rule = _progression.capabilityRules(capabilityKey).stream()
			.filter(candidate -> preferredClass <= 0 || candidate.classIds().contains(preferredClass))
			.filter(candidate -> (_mode != Mode.COMPANION_GREATER_HEAL_058) || (candidate.classIds().contains(97) && (candidate.actionSkill().skillId() == 1217)))
			.filter(candidate -> candidate.requiredEquipmentFamilies().isEmpty() && candidate.requiredItems().isEmpty() && (candidate.classIds().size() == 1))
			.filter(candidate -> Set.of("SINGLE_TARGET", "PARTY", "ALLY").contains(candidate.targetScope().name()))
			.filter(candidate -> {
				final Skill skill = SkillData.getInstance().getSkill(candidate.actionSkill().skillId(), candidate.actionSkill().skillLevel());
				return (skill != null) && (skill.getItemConsumeId() <= 0) && (skill.getMpConsume() > 0);
			}).findFirst().orElseThrow();
		final int preparedRank = preferredClass == 97 && capabilityKey.equals("combat.buff") ? 6 : preferredClass == 105 && capabilityKey.equals("combat.heal") ? 33 : Set.of(Mode.LEARNED_SKILLS_058, Mode.COMPANION_GREATER_HEAL_058).contains(_mode) ? 101 : rule.actionSkill().skillLevel();
		final Skill skill = SkillData.getInstance().getSkill(rule.actionSkill().skillId(), preparedRank);
		PhantomAssertions.assertTrue(skill != null && skill.getLevel() == preparedRank, "Declared stock support rank is unavailable.");
		// Explicit TEST preparation before the episode; no natural career or product grant claim.
		_player.getStat().setExp(_player.getStat().getExpForLevel(85));
		_player.getStat().setLevel((byte) 85);
		_player.setBaseClass(rule.classIds().getFirst());
		_player.setPlayerClass(rule.classIds().getFirst());
		_player.addSkill(skill, false);
		if (_mode == Mode.COMPANION_GREATER_HEAL_058) { _player.addSkill(SkillData.getInstance().getSkill(1335, 1), false); }
		_player.enableSkill(skill);
		_player.setCurrentHp(_player.getMaxHp());
		// Leave regeneration headroom; a full-MP clamp can hide a cost at the sampling boundary.
		_player.setCurrentMp(_player.getMaxMp() / 2);
		if (human.isDead())
		{
			human.doRevive();
		}
		human.getStat().setExp(human.getStat().getExpForLevel(30));
		human.getStat().setLevel((byte) 30);
		human.setBaseClass(11);
		human.setPlayerClass(11); // Stock human Wizard, with no Recharge skill.
		human.setInvul(false);
		human.setCurrentHp(human.getMaxHp());
		human.setCurrentMp(human.getMaxMp());
		human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
		if (capabilityKey.equals("combat.heal"))
		{
			human.setCurrentHp(human.getMaxHp() / 5);
		}
		else if (capabilityKey.equals("combat.recharge"))
		{
			human.setCurrentMp(human.getMaxMp() / 20);
		}
		else if (capabilityKey.equals("combat.resurrection"))
		{
			human.doDie(null); // Explicit owned corpse precondition, before measurement.
			PhantomAssertions.assertTrue(human.isDead() && !human.isResurrectionBlocked(), "Resurrection corpse input is invalid.");
		}
		context.record("companion058.input." + capabilityKey, "FIXTURE_PRECONDITION profile=" + _profile.profileId() + "; actor=" + _player.getObjectId() + "; human=" + human.getObjectId() + "; class=" + _player.getPlayerClass().getId() + "; level=85; skill=" + skill.getId() + ':' + skill.getLevel() + "; humanClass=11; humanLevel=" + human.getLevel() + "; hp=" + human.getCurrentHp() + "; mp=" + human.getCurrentMp() + "; xyz=" + human.getX() + ',' + human.getY() + ',' + human.getZ() + "; deadline=20s");
		final PhantomProgressionService progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final PhantomSocialService social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), context.seed(), 16);
		progression.start();
		PhantomAssertions.assertTrue(social.start(), "Companion social authority did not start.");
		final L2jPhantomPartyBackend backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final PhantomPartyCoordinator party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, _combat), new PhantomPartyTactics(_combat, backend, PhantomPartySupportPolicy.load(Path.of("data/phantoms/party/high-five-party-support-v1.xml"))), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 32);
		PhantomAssertions.assertTrue(party.start(), "Companion party owner did not start.");
		party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
		Throwable scenarioFailure = null;
		final boolean busyHold059 = Boolean.getBoolean("phantom.test.busyHold059") && capabilityKey.equals("combat.heal");
		final boolean supportRequests059 = Boolean.getBoolean("phantom.test.supportRequests059") || Boolean.getBoolean("phantom.test.supportKinds059");
		PhantomCompanionComposition058 commands059 = null;
		final java.util.concurrent.atomic.AtomicInteger nativeCasts059 = new java.util.concurrent.atomic.AtomicInteger();
		final var castListener059 = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(_player, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_SKILL_USE, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillUse event) -> { if ((event.getCaster() == _player) && (event.getSkill().getId() == skill.getId()) && (event.getSkill().getLevel() == skill.getLevel())) { nativeCasts059.incrementAndGet(); } }, this);
		try
		{
			if (supportRequests059)
			{
				commands059 = new PhantomCompanionComposition058(_repository, _materialization, _topology, _knowledge, social, party, human, null, _combat);
				_player.addListener(castListener059);
				context.record("support059.input", "Existing TEST native role fixture; request via WHISPER, conversation/execution only; no automatic tactical pulses during effect measurement; capability=" + capabilityKey);
			}
			if (busyHold059)
			{
				commands059 = new PhantomCompanionComposition058(_repository, _materialization, _topology, _knowledge, social, party, human);
				_player.addListener(castListener059);
				context.record("command059.busy.input", "TEST predeclared close-range healer; issue addressed HOLD only after native cast flag; no HP/MP/item writes during measurement; expect one native cast/effect/cost then HOLD and RESUME");
			}
			PhantomAssertions.assertEquals(85, _player.getLevel(), "Prepared companion level is inconsistent.");
			final var readiness = progression.capabilities(_profile.profileId(), human.getObjectId()).stream().filter(capability -> capability.capabilityKey().equals(capabilityKey) && (capability.actionSkill().skillId() == skill.getId()) && (capability.actionSkill().skillLevel() == skill.getLevel())).toList();
			context.record("companion058.readiness." + capabilityKey, readiness);
			PhantomAssertions.assertTrue(readiness.stream().anyMatch(capability -> capability.readyNow()), "Prepared companion has no current learned, legal support capability: " + readiness);
			final double initialMp = _player.getCurrentMp();
			final double targetInitialHp = human.getCurrentHp();
			final double targetInitialMaxHp059 = human.getMaxHp();
			final double targetInitialMp = human.getCurrentMp();
			double minimumMp = initialMp;
			boolean selectedCastSeen = false;
			boolean resurrectionRequested = false;
			boolean effectObserved = false;
			boolean holdSent059 = false;
			final var invitation = org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().invite(human, _player, PartyDistributionType.FINDERS_KEEPERS.getId());
			PhantomAssertions.assertTrue(invitation.delivered(), "Real-led support invitation was not delivered: " + invitation);
			if (supportRequests059)
			{
				final var pending059 = org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().observe(_player).orElseThrow();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondOutcome.ACCEPTED, org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().respond(_player, org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response.ACCEPT, pending059.identity()).outcome(), "Support059 explicit TEST consent failed.");
				final String request059 = switch (capabilityKey) { case "combat.heal" -> "нжно лечение"; case "combat.recharge" -> "дай ману"; case "combat.resurrection" -> "дай воскрешение"; default -> "дай баф"; };
				PhantomAssertions.assertEquals("support.issued", commands059.supportMessage059(context, human, _player, request059), "Native support request did not report admitted cast.");
				if (_player.isCastingNow())
				{
					PhantomAssertions.assertEquals("support.unavailable", commands059.supportMessage059(context, human, _player, request059), "Busy support repeat did not deliver an honest unavailable reply.");
					context.record("support059.busy." + capabilityKey, "repeat while native casting delivered unavailable; no abort or extra cast");
				}
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while ((System.nanoTime() < deadline) && (!effectObserved || _player.isCastingNow()))
			{
				if (!supportRequests059) { party.onPulse(); }
				if (busyHold059 && !holdSent059 && _player.isCastingNow())
				{
					context.record("command059.busy.before", "casting=true; targetHP=" + human.getCurrentHp() + "; casterMP=" + _player.getCurrentMp() + "; nativeCasts=" + nativeCasts059.get());
					PhantomAssertions.assertTrue(commands059.command059(context, human, _player, "стой здесь", party), "Busy HOLD did not deliver a truthful response.");
					commands059.assertCommandReason059("command.pending");
					holdSent059 = true;
				}
				final Skill casting = _player.getLastSkillCast();
				selectedCastSeen |= (casting != null) && (casting.getId() == skill.getId()) && (casting.getLevel() == skill.getLevel());
				if (capabilityKey.equals("combat.resurrection") && human.isReviveRequested())
				{
					resurrectionRequested = true;
					human.reviveAnswer(1); // Explicit TEST response to a native request, never a real-user autoaccept.
				}
				effectObserved = switch (capabilityKey)
				{
					case "combat.heal" -> selectedCastSeen && !human.isDead() && (human.getCurrentHp() >= targetInitialHp + human.getMaxHp() / 4);
					case "combat.recharge" -> selectedCastSeen && !human.isDead() && (human.getCurrentMp() >= targetInitialMp + Math.min(90, human.getMaxMp() / 4));
					case "combat.resurrection" -> selectedCastSeen && resurrectionRequested && !human.isDead() && (human.getCurrentHp() > 0);
					default -> PhantomSupportEffectAuthority.status(human, skill, 45) == PhantomSupportEffectAuthority.Status.HEALTHY;
				};
				minimumMp = Math.min(minimumMp, _player.getCurrentMp());
				Thread.sleep(10);
			}
			PhantomAssertions.assertTrue(human.isInParty() && human.getParty().containsPlayer(_player) && (supportRequests059 || party.committed(_profile.profileId())), "Support scenario did not retain canonical mixed membership.");
			context.record("companion058.support." + capabilityKey, "skill=" + skill.getId() + ':' + skill.getLevel() + "; selectedCast=" + selectedCastSeen + "; resurrectionRequested=" + resurrectionRequested + "; initialMp=" + initialMp + "; minimumMp=" + minimumMp + "; targetHp=" + targetInitialHp + "->" + human.getCurrentHp() + "; targetMp=" + targetInitialMp + "->" + human.getCurrentMp());
			PhantomAssertions.assertTrue(effectObserved, "Real-led native support has no measured effect: " + capabilityKey + "; selectedCast=" + selectedCastSeen + "; resurrectionRequested=" + resurrectionRequested + "; hp=" + human.getCurrentHp() + "; mp=" + human.getCurrentMp());
			PhantomAssertions.assertTrue(minimumMp < initialMp, "Automatic native support did not consume caster MP: initial=" + initialMp + "; minimum=" + minimumMp + "; final=" + _player.getCurrentMp() + "; maximum=" + _player.getMaxMp());
			if (Boolean.getBoolean("phantom.test.cardinalBuff059")) { PhantomAssertions.assertTrue(skill.getId() == 1311 && skill.getLevel() == 6 && human.getMaxHp() > targetInitialMaxHp059 * 1.34, "Current native Avatar rank did not increase party MaxHP by35percent."); context.record("raid059.avatarEffect", "actual current1311:6; native party MaxHP="+targetInitialMaxHp059+"->"+human.getMaxHp()+"; MP="+initialMp+"->"+minimumMp+"; normal mixed-party invitation/coordinator, no direct effect/HP write inside measurement"); }
			if (supportRequests059)
			{
				PhantomAssertions.assertEquals(1, nativeCasts059.get(), "Support059 duplicated a requested native cast.");
				PhantomAssertions.assertEquals("support.refused", commands059.supportMessage059(context, human, _player, "дай контроль"), "Unsupported request did not deliver a truthful refusal.");
				PhantomAssertions.assertEquals(1, nativeCasts059.get(), "Refused support request started another cast.");
			}
			if (busyHold059)
			{
				for (int pulse = 0; pulse < 16; pulse++) { party.onPulse(); Thread.sleep(10); }
				PhantomAssertions.assertTrue(holdSent059, "Busy HOLD input never observed an actual native cast.");
				PhantomAssertions.assertEquals(1, nativeCasts059.get(), "Busy HOLD duplicated or lost the native cast.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, party.claim(_profile.profileId()).orElseThrow().state().command().mode(), "Busy HOLD never reached its safe native boundary.");
				testHeldSupport059(context, human, party, skill, nativeCasts059);
				PhantomAssertions.assertTrue(commands059.command059(context, human, _player, "за мной", party), "Post-cast RESUME was not delivered.");
				commands059.assertCommandReason059("command.following");
				context.record("command059.busy.result", "nativeCasts=" + nativeCasts059.get() + "; actualEffect=true; actualMpCost=true; postCastHold=true; resume=true");
			}
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
			for (int pulse = 0; (pulse < 32) && party.blocksBackground(_profile.profileId()); pulse++)
			{
				party.onPulse();
			}
			PhantomAssertions.assertFalse(party.blocksBackground(_profile.profileId()), "Native leave retained a stale real-party pin.");
		}
		catch (Exception | AssertionError failure)
		{
			scenarioFailure = failure;
			throw failure;
		}
		finally
		{
			if (busyHold059 || supportRequests059) { _player.removeListener(castListener059); }
			if (commands059 != null) { commands059.close(); }
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().observe(_player).ifPresent(pending -> org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().cancel(pending.identity()));
			org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(human);
			for (int pulse = 0; pulse < 4; pulse++)
			{
				party.onPulse();
			}
			party.beginStop();
			final boolean partyStopped = party.finishStop();
			progression.beginStop();
			final boolean progressionStopped = progression.finishStop();
			social.beginStop();
			final boolean socialStopped = social.finishStop();
			if (!partyStopped || !progressionStopped || !socialStopped)
			{
				final AssertionError cleanup = new AssertionError("Companion owners did not drain: party=" + party.snapshot() + "; progression=" + progressionStopped + "; social=" + socialStopped);
				if (scenarioFailure != null)
				{
					scenarioFailure.addSuppressed(cleanup);
				}
				else
				{
					throw cleanup;
				}
			}
		}
	}

	private void testHeldSupport059(PhantomTestContext context, Player human, PhantomPartyCoordinator party, Skill skill, java.util.concurrent.atomic.AtomicInteger nativeCasts) throws Exception
	{
		final var anchor = party.claim(_profile.profileId()).orElseThrow().state().command().anchor();
		final int range = skill.getCastRange();
		PhantomAssertions.assertTrue(range > 200, "Held support needs a declared ranged native skill.");
		// Independent negative episode: only these TEST preconditions precede measurement.
		human.decayMe();
		human.setXYZInvisible(anchor.x() + range + 75, anchor.y(), anchor.z());
		human.spawnMe();
		human.revalidateZone(true);
		human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill.getId()); // Prior episode's legitimate HoT must not masquerade as a new cast.
		human.setCurrentHp(human.getMaxHp() / 5);
		final int castsBefore = nativeCasts.get();
		final double beforeHp = human.getCurrentHp();
		final double beforeMp = _player.getCurrentMp();
		context.record("command059.support.input", "INDEPENDENT TEST preconditions: held healer anchor=" + anchor + "; skill=" + skill.getId() + ':' + skill.getLevel() + "; castRange=" + range + "; targetDistance=" + _player.calculateDistance3D(human) + "; targetHP=" + beforeHp + "; casterMP=" + beforeMp + "; no further fixture mutations; far5s then native approach");
		final long farDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		double displacement = 0;
		boolean moving = false;
		while (System.nanoTime() < farDeadline)
		{
			party.onPulse();
			moving |= _player.isMoving();
			displacement = Math.max(displacement, Math.hypot(_player.getX() - anchor.x(), _player.getY() - anchor.y()));
			Thread.sleep(10);
		}
		context.record("command059.support.far", "moving=" + moving + "; displacement=" + displacement + "; targetHP=" + human.getCurrentHp() + "; casterMP=" + _player.getCurrentMp());
		PhantomAssertions.assertTrue(!moving && displacement <= 5, "HOLD support chased an out-of-range target: moving=" + moving + "; displacement=" + displacement);
		PhantomAssertions.assertEquals(castsBefore, nativeCasts.get(), "Out-of-range HOLD support started a native cast.");
		PhantomAssertions.assertTrue(human.getCurrentHp() < beforeHp + human.getMaxHp() / 4, "Out-of-range HOLD support produced an unexpected heal.");
		human.setRunning();
		human.getAI().setIntention(Intention.MOVE_TO, new Location(anchor.x() + 30, anchor.y(), anchor.z()));
		final long nearDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		boolean healed = false;
		double minimumMp = _player.getCurrentMp();
		while (System.nanoTime() < nearDeadline)
		{
			party.onPulse();
			minimumMp = Math.min(minimumMp, _player.getCurrentMp());
			displacement = Math.max(displacement, Math.hypot(_player.getX() - anchor.x(), _player.getY() - anchor.y()));
			healed |= human.getCurrentHp() >= beforeHp + human.getMaxHp() / 4;
			if (healed && !human.isMoving() && !_player.isCastingNow()) { break; }
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(healed && minimumMp < beforeMp && displacement <= 5, "Held healer did not cast from its anchor after native target approach: healed=" + healed + "; displacement=" + displacement + "; MP=" + beforeMp + "->" + minimumMp);
		context.record("command059.support.near", "actualHeal=" + healed + "; nativeTargetWalk=true; displacement=" + displacement + "; MP=" + beforeMp + "->" + minimumMp);
	}

	private void testPost002NativeSupport(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		final var creation = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
		_player.decayMe();
		_player.setXYZInvisible(creation.getX(), creation.getY(), creation.getZ());
		_player.spawnMe();
		_player.revalidateZone(true);
		final Player human = ensureObserver();
		human.revalidateZone(true);
		human.setCurrentHp(human.getMaxHp());
		human.setCurrentMp(human.getMaxMp());
		final CapabilityRule rule = _progression.capabilityRules("combat.buff").stream()
			.filter(candidate -> (candidate.actionSkill().skillId() == 349) && (candidate.actionSkill().skillLevel() == 1)) // Same verified stock Sword Muse input as companion-real-support058.
			.filter(candidate -> candidate.requiredEquipmentFamilies().isEmpty() && candidate.requiredItems().isEmpty())
			.filter(candidate -> candidate.classIds().size() == 1)
			.filter(candidate -> SkillData.getInstance().getSkill(candidate.actionSkill().skillId(), candidate.actionSkill().skillLevel()) != null)
			.findFirst()
			.orElseThrow(() -> new AssertionError("Canonical High Five progression exposes no usable combat.buff rule."));
		final int classId = rule.classIds().getFirst();
		final Skill skill = SkillData.getInstance().getSkill(rule.actionSkill().skillId(), rule.actionSkill().skillLevel());
		// Canonical XP/base/current class must agree before the native class-refresh callback.
		_player.getStat().setExp(_player.getStat().getExpForLevel(85));
		_player.setBaseClass(classId);
		_player.setPlayerClass(classId);
		_player.getStat().setLevel((byte) 85);
		human.getStat().setExp(human.getStat().getExpForLevel(85));
		human.getStat().setLevel((byte) 85);
		_player.addSkill(skill, false);
		_player.enableSkill(skill);
		_player.setCurrentMp(_player.getMaxMp());
		final int supportItemId = skill.getItemConsumeId();
		final long supportItemBaseline = supportItemId > 0 ? _player.getInventory().getInventoryItemCount(supportItemId, -1) : 0;
		if (supportItemId > 0)
		{
			PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, supportItemId, Math.max(1, skill.getItemConsumeCount()) * 4L, _player, this) != null, "Could not create canonical support reagents.");
		}
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS);
		_player.setParty(party);
		party.addPartyMember(human);
		human.setParty(party);
		human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
		final PhantomPartySupportAction action = new PhantomPartySupportAction("combat.buff", rule.variantKey(), rule.targetScope().name(), human.getObjectId(), new SelectedSkill(skill.getId(), skill.getLevel()), PhantomPartySupportAction.Audience.PARTY, 45);
		try
		{
			final double firstMpBefore = _player.getCurrentMp();
			PhantomAssertions.assertEquals(PhantomConversationExecutionPort.ResultStatus.COMPLETED, executePost002ConversationSupport(human, rule), "Same-party conversation support did not issue a native cast.");
			try { await(() -> human.getEffectList().getBuffInfoBySkillId(skill.getId()) != null, "Same-party conversation support did not produce a native effect on the human requester."); }
			finally { context.record("support058.initial", "stockSkill=" + skill.getId() + ":" + skill.getLevel() + "; class=" + _player.getActiveClass() + "; level=" + _player.getLevel() + "; mp=" + firstMpBefore + "->" + _player.getCurrentMp() + "; nativeCast=" + _player.isCastingNow() + "; intention=" + _player.getAI().getIntention() + "; targetEffect=" + (human.getEffectList().getBuffInfoBySkillId(skill.getId()) != null) + "; geoLOS=" + GeoEngine.getInstance().canSeeTarget(_player.getX(), _player.getY(), _player.getZ(), 0, human.getX(), human.getY(), human.getZ(), 0)); }
			PhantomAssertions.assertEquals(PhantomSupportEffectAuthority.Status.HEALTHY, PhantomSupportEffectAuthority.status(human, skill, 45), "Fresh native buff was not observed as healthy.");
			PhantomAssertions.assertEquals(ActionOutcome.ALREADY_OWNED, castPost002Support(action, "healthy"), "Healthy native effect did not suppress rebuff spam.");

			final BuffInfo effect = human.getEffectList().getBuffInfoBySkillId(skill.getId());
			await(() -> !_player.isCastingNow() && !_player.isSkillDisabled(skill), "Positive native support cast/reuse did not finish normally.");
			// setAbnormalTime is TOTAL duration, so retain elapsed ticks and declare thirty seconds remaining.
			effect.setAbnormalTime(effect.getAbnormalTime() - effect.getTime() + 30);
			_player.getAI().setIntention(Intention.IDLE);
			PhantomAssertions.assertEquals(PhantomSupportEffectAuthority.Status.NEAR_EXPIRY, PhantomSupportEffectAuthority.status(human, skill, 45), "Native remaining-time threshold did not expose near-expiry.");
			final double recastMpBefore = _player.getCurrentMp();
			final long recastItemsBefore = supportItemId > 0 ? _player.getInventory().getInventoryItemCount(supportItemId, -1) : 0;
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, castPost002Support(action, "near-expiry"), "Near-expiry native buff was not admitted for rebuff.");

			try { await(() -> !_player.isCastingNow() && (PhantomSupportEffectAuthority.status(human, skill, 45) == PhantomSupportEffectAuthority.Status.HEALTHY), "Near-expiry native recast did not finish with an actual renewed effect."); }
			finally
			{
				final BuffInfo actual = human.getEffectList().getBuffInfoBySkillId(skill.getId());
				context.record("support058.recast", "skill=" + skill.getId() + ":" + skill.getLevel() + "; mp=" + recastMpBefore + "->" + _player.getCurrentMp() + "; items=" + recastItemsBefore + "->" + (supportItemId > 0 ? _player.getInventory().getInventoryItemCount(supportItemId, -1) : 0) + "; casting=" + _player.isCastingNow() + "; disabled=" + _player.isSkillDisabled(skill) + "; reuseMillis=" + _player.getSkillRemainingReuseTime(skill.getReuseHashCode()) + "; intention=" + _player.getAI().getIntention() + "; effectTotal=" + (actual == null ? -1 : actual.getAbnormalTime()) + "; effectRemaining=" + (actual == null ? -1 : actual.getTime()) + "; hp=" + human.getCurrentHp());
			}
			await(() -> !_player.isSkillDisabled(skill), "Native recast reuse did not finish before independent negative controls.");
			_player.getAI().setIntention(Intention.IDLE);
			human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
			_player.setCurrentMp(0);
			PhantomAssertions.assertEquals(ActionOutcome.UNAVAILABLE, castPost002Support(action, "no-mp"), "Native zero-MP guard was bypassed.");

			_player.setCurrentMp(_player.getMaxMp());
			// Independent invalid-target fixture boundaries; no successful measurement is being rescued.
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, castPost002Support(new PhantomPartySupportAction(action.capabilityKey(), action.variantKey(), action.targetScope(), Integer.MAX_VALUE, action.skill(), action.audience(), action.rebuffRemainingSeconds()), "wrong-target"), "Unknown exact native target was admitted.");
			human.setDead(true);
			try { PhantomAssertions.assertEquals(ActionOutcome.REJECTED, castPost002Support(action, "dead-target"), "Ordinary buff was cast on a dead target."); }
			finally { human.setDead(false); }
			final int wrongInstance = InstanceManager.getInstance().createDynamicInstance(0).getId();
			human.setInstanceId(wrongInstance);
			try { PhantomAssertions.assertEquals(ActionOutcome.REJECTED, castPost002Support(action, "foreign-instance"), "Native support crossed an instance boundary."); }
			finally { human.setInstanceId(0); InstanceManager.getInstance().destroyInstance(wrongInstance); }
			final int targetX = human.getX();
			final int targetY = human.getY();
			final int targetZ = human.getZ();
			human.setXYZInvisible(_player.getX() + 5000, _player.getY(), _player.getZ());
			PhantomAssertions.assertEquals(ActionOutcome.UNAVAILABLE, castPost002Support(action, "out-of-range"), "Native support range guard was bypassed.");
			human.setXYZInvisible(targetX, targetY, targetZ);

			_player.disableSkill(skill, 60_000);
			PhantomAssertions.assertEquals(ActionOutcome.UNAVAILABLE, castPost002Support(action, "reuse"), "Native support reuse guard was bypassed.");
		}
		finally
		{
			_player.enableSkill(skill);
			_player.setCurrentMp(_player.getMaxMp());
			human.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
			if (supportItemId > 0)
			{
				destroyInventoryCount(_player, supportItemId, _player.getInventory().getInventoryItemCount(supportItemId, -1) - supportItemBaseline);
			}
			_player.setParty(null);
			human.setParty(null);
		}
	}

	private PhantomConversationExecutionPort.ResultStatus executePost002ConversationSupport(Player human, CapabilityRule rule)
	{
		final PhantomProgressionService progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final PhantomSocialService social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), POST002_SEED, 16);
		progression.start();
		PhantomAssertions.assertTrue(social.start(), "POST-002 conversation social authority did not start.");
		try
		{
			final PhantomPartySupportPolicy policy = PhantomPartySupportPolicy.load(Path.of("data/phantoms/party/high-five-party-support-v1.xml"));
			final L2jPhantomPartyBackend backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
			final PhantomPartyCoordinator party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, _combat), new PhantomPartyTactics(_combat, backend, policy), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 16);
			final PhantomConversationExecutionCatalog catalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v2.xml"));
			final L2jPhantomConversationExecutionPort port = new L2jPhantomConversationExecutionPort(catalog, _knowledge, _topology, party, _materialization, ChatObservationService.getInstance(), PhantomRiftConversationFacts.NONE, PhantomFarmingConversationFacts.NONE, _combat, social, policy);
			final PhantomDomainRef requester = new PhantomDomainRef("character.object", Integer.toString(human.getObjectId()));
			final ExecutionEntry entry = new ExecutionEntry("C".repeat(64), "D".repeat(64), org.l2jmobius.gameserver.network.enums.ChatType.PARTY, requester, "ack.action_proposed", "neutral", "Проверяю доступное усиление.", "party.support", requester, List.of(new Argument("capability", "capability:combat.buff")), 1, 5, OutboundState.SUPPRESSED, ActionState.PREPARED, 0, 0, "execution.prepared", 0, 0, -1);
			PhantomAssertions.assertEquals(rule.actionSkill().skillId(), party.supportCapabilities(_profile.profileId(), human.getObjectId()).stream().filter(capability -> capability.capabilityKey().equals("combat.buff") && capability.readyNow()).findFirst().orElseThrow().actionSkillId(), "Conversation support did not resolve the canonical learned buff.");
			return port.executeSupport(_profile.profileId(), entry);
		}
		finally
		{
			progression.beginStop();
			PhantomAssertions.assertTrue(progression.finishStop(), "POST-002 conversation progression authority did not stop.");
			social.beginStop();
			PhantomAssertions.assertTrue(social.finishStop(), "POST-002 conversation social authority did not stop.");
		}
	}

	private ActionOutcome castPost002Support(PhantomPartySupportAction action, String suffix)
	{
		final ExternalActionLease lease = _combat.acquireExternalAction(new ExternalActionRequest(_profile.profileId(), ExternalActionKind.PARTY_SUPPORT, "post002.support." + suffix, System.nanoTime() + TimeUnit.SECONDS.toNanos(30), () -> false)).lease();
		PhantomAssertions.assertTrue(lease != null, "POST-002 support lease was unavailable: " + suffix);
		final ActionOutcome outcome = lease.castSupport(action);
		if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
		{
			lease.complete();
		}
		else
		{
			lease.close();
		}
		return outcome;
	}

	private Source selectAcquisitionSource(PhantomTopologyQuery topology, PhantomTestContext context)
	{
		final PhantomAcquisitionCatalog catalog = PhantomAcquisitionCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml"));
		final PhantomAcquisitionSourcePlanner planner = new PhantomAcquisitionSourcePlanner(catalog, _query, topology, _progression);
		Source best = null;
		double bestChance = -1;
		for (int itemId : _query.snapshot().spoilSourcesByItem().keySet().stream().sorted().toList())
		{
			final var request = new PhantomAcquisitionSourcePlanner.Request(1, itemId, 1, PhantomActivityState.ACTIVE, SPOIL_CLASS_ID, 85, Map.of(), Map.of(SPOIL_SKILL_ID, 11, SWEEP_SKILL_ID, 1), Set.of(Method.SPOIL_SWEEP), Method.SPOIL_SWEEP, "", Map.of(), 0);
			final var result = planner.plan(request);
			if (result.selected() == null)
			{
				continue;
			}
			final Source source = result.selected().source();
			if (_query.snapshot().spawnFactsByNpc().getOrDefault(source.npcId(), List.of()).stream().noneMatch(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)))
			{
				continue;
			}
			final double chance = _query.snapshot().spoilSourcesByItem().getOrDefault(itemId, List.of()).stream().filter(fact -> fact.stableKey().equals(source.factKey())).mapToDouble(DropFact::rawItemChance).findFirst().orElse(0);
			if ((chance > bestChance) || ((Double.compare(chance, bestChance) == 0) && ((best == null) || (source.sourceId().compareTo(best.sourceId()) < 0))))
			{
				best = source;
				bestChance = chance;
			}
		}
		if (best == null)
		{
			throw new AssertionError("No production spoil/sweep source has an exact normal-world spawn.");
		}
		context.record("acquisition.activeRawItemChance", bestChance);
		_acquisitionRawItemChance = bestChance;
		return best;
	}

	private Candidate selectManorCandidate()
	{
		for (int cropItemId : _query.snapshot().manorFacts().stream().map(fact -> fact.cropItemId()).distinct().sorted().toList())
		{
			final Map<Integer, Long> inventory = _manorAuthority.probe(cropItemId).requiredItemIds().stream().collect(java.util.stream.Collectors.toMap(itemId -> itemId, _ -> 64L));
			for (int playerLevel = 1; playerLevel <= 85; playerLevel++)
			{
				final var result = _manorAuthority.candidates(cropItemId, playerLevel, inventory);
				final int currentLevel = playerLevel;
				final Candidate levelMatched = result.candidates().stream().filter(candidate -> candidate.npcLevel() == currentLevel).findFirst().orElse(null);
				if (levelMatched != null)
				{
					_manorPlayerLevel = playerLevel;
					return levelMatched;
				}
			}
		}
		throw new AssertionError("No current manor candidate has a real normal-world target.");
	}

	private SpawnFact sourceCombatPoint(int npcId, String nodeId, String anchorId)
	{
		final SpawnFact fact = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(value -> nodeId.equals(value.topologyNodeId()) && (value.territoryGeometry() != null)).findFirst().orElseThrow(() -> new AssertionError("Mapped source has no territory fact: " + npcId));
		final var anchor = _topology.snapshot().anchorById().get(anchorId);
		return new SpawnFact(npcId, fact.spawnOrdinal(), fact.instanceId(), anchor.point().x(), anchor.point().y(), anchor.point().z(), fact.amount(), fact.locationId(), SpawnPointKind.EXACT, nodeId, anchor.mapRegionLocId(), PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT);
	}

	private NpcSpawnTerritory sourceTerritory(int npcId, String nodeId)
	{
		final var geometry = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(fact -> nodeId.equals(fact.topologyNodeId()) && (fact.territoryGeometry() != null)).map(SpawnFact::territoryGeometry).distinct().findFirst().orElseThrow(() -> new AssertionError("Mapped source geometry is missing."));
		return SpawnTable.getInstance().getSpawns(npcId).stream().map(Spawn::getSpawnTerritory).filter(java.util.Objects::nonNull).filter(territory -> territory.geometrySnapshot().map(snapshot -> snapshot.hash().equals(geometry.geometryHash()) && snapshot.sourcePath().equals(geometry.sourcePath()) && snapshot.territoryName().equals(geometry.territoryName())).orElse(false)).findFirst().orElseThrow(() -> new AssertionError("Runtime Spawn territory differs from Game Knowledge source."));
	}

	private List<QuestSource> questSources(PhantomTopologyQuery topology)
	{
		final List<QuestSource> sources = new ArrayList<>();
		for (Rule rule : _questCatalog.rules())
		{
			for (int npcId : rule.targetNpcIds())
			{
				final SpawnFact fact = _query.snapshot().spawnFactsByNpc().getOrDefault(npcId, List.of()).stream().filter(value -> (value.instanceId() == 0) && (value.amount() > 0) && (value.topologyNodeId() != null) && (value.territoryGeometry() != null)).min(Comparator.comparing(SpawnFact::stableKey)).orElseThrow(() -> new AssertionError("Curated quest target has no mapped source territory: " + npcId));
				final var anchor = topology.snapshot().anchorsByNode().getOrDefault(fact.topologyNodeId(), List.of()).stream().filter(value -> value.point().instanceId() == 0).min(Comparator.comparing(value -> value.id())).orElseThrow(() -> new AssertionError("Curated quest source has no normal-world anchor: " + npcId));
				sources.add(new QuestSource(rule, npcId, sourceCombatPoint(npcId, fact.topologyNodeId(), anchor.id()), sourceTerritory(npcId, fact.topologyNodeId())));
			}
		}
		PhantomAssertions.assertEquals(3, sources.size(), "The curated catalog must expose exactly three separately covered quest targets.");
		PhantomAssertions.assertEquals(List.of(20013, 20019, 20016), sources.stream().map(QuestSource::npcId).toList(), "Curated quest target coverage drifted.");
		return List.copyOf(sources);
	}

	private void selectQuestSource(QuestSource source)
	{
		_questRule = source.rule();
		_combatPoint = source.combatPoint();
		_sourceTerritory = source.territory();
	}

	private void prepareManorActor()
	{
		_player.setPlayerClass(MELEE_CLASS_ID);
		_player.getStat().setLevel((byte) _manorPlayerLevel);
		_player.setInvul(true);
		_player.getItemReuseTimeStamps().clear();
		ensureWeapon();
		ensureInventoryItem(_manorCandidate.fact().seedItemId(), 64);
		ensureInventoryItem(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, 1);
		final var anchor = _topology.snapshot().anchorById().get(_manorCandidate.anchorId());
		PhantomAssertions.assertTrue(anchor != null, "Selected manor anchor disappeared.");
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
		_player.spawnMe();
		_player.revalidateZone(true);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
	}

	private void prepareQuestActor()
	{
		if (_player.getPlayerClass().getId() != MELEE_CLASS_ID)
		{
			_player.setPlayerClass(MELEE_CLASS_ID);
		}
		if (_player.getLevel() != 85)
		{
			_player.getStat().setLevel((byte) 85);
		}
		_player.setInvul(true);
		ensureWeapon();
		final var quest = ScriptManager.getInstance().getQuest(_questRule.questId());
		PhantomAssertions.assertTrue(quest != null, "Curated quest was not loaded.");
		final QuestState state = _player.getQuestState(_questRule.questName()) == null ? new QuestState(quest, _player, State.STARTED) : _player.getQuestState(_questRule.questName());
		state.setCond(_questRule.allowedConds().getFirst(), false);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private PhantomGoal installAcquisition(RankedSource selected, List<RankedSource> ranked, MethodBinding binding, long baseline, long required, Phase phase, int targetObjectId)
	{
		final long revision = ++_acquisitionRevision;
		final Source source = selected.source();
		final PhantomGoal goal = new PhantomGoal(21, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(source.itemId())), required, 0, source.method().key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, source.method().key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, source.anchorId()), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "acquisition.checkpoint2.service", revision);
		final List<PhantomAcquisitionState.Candidate> candidates = ranked.stream().map(value -> new PhantomAcquisitionState.Candidate(value.source().sourceId(), value.source().method(), value.score(), 0, 0, "")).toList();
		final int cursor = java.util.stream.IntStream.range(0, ranked.size()).filter(index -> ranked.get(index).source().sourceId().equals(source.sourceId())).findFirst().orElseThrow();
		final PhantomBackgroundState.Hashes background = _background.authorityHashes();
		final Hashes hashes = new Hashes(_acquisitionCatalog.hash(), _query.snapshot().combinedHash(), _topology.snapshot().canonicalHash(), _progression.combinedHash().toLowerCase(java.util.Locale.ROOT), canonicalDigest(background.knowledge(), background.topology(), background.progression(), background.commerce()));
		final boolean targeted = phase != Phase.TARGET_REQUIRED;
		final PhantomAcquisitionState state = new PhantomAcquisitionState(hashes, goal.goalId(), goal.revision(), source.itemId(), required, baseline, baseline, 0, targeted ? Status.ACTIVE : Status.READY, source, candidates, cursor, 0, phase, targeted ? targetObjectId : 0, targeted ? source.npcId() : 0, 0, null, binding, List.of(), 0, revision);
		final var storedGoal = _acquisitionGoals.load(_profile.profileId());
		final var storedState = _acquisitionStore.load(_profile.profileId());
		if (storedGoal.isEmpty() && storedState.isEmpty())
		{
			_acquisitionGoals.insert(_profile.profileId(), goal);
			_acquisitionStore.insert(_profile.profileId(), state);
		}
		else
		{
			PhantomAssertions.assertTrue(storedGoal.isPresent() && storedState.isPresent(), "Acquisition Goal/state setup became partial.");
			_acquisitionStore.mutateWithGoal(_profile.profileId(), storedState.orElseThrow().rowVersion(), state, storedGoal.orElseThrow().rowVersion(), goal);
		}
		return goal;
	}

	private PhantomAcquisitionService.OperationResult advanceAcquisition(PhantomGoal goal, long sequence)
	{
		return _acquisition.activeAdvance(_profile.profileId(), goal, PhantomActivityState.ACTIVE, 1, sequence, 1_000_000 + sequence, sequence, () -> false);
	}

	private void restartAcquisitionService()
	{
		_acquisition.beginStop();
		PhantomAssertions.assertTrue(_acquisition.finishStop(), "Acquisition restart retained an owned claim.");
		_acquisition = new PhantomAcquisitionService(_acquisitionCatalog, _acquisitionStore, _acquisitionGoals, _acquisitionPlanner, _query, _topology, _progression, _combat, _background, _navigation, _manorAuthority, _questCatalog, _epochMillis::get);
		PhantomAssertions.assertTrue(_acquisition.start(), "Acquisition restart did not start.");
	}

	private PhantomAcquisitionState acquisitionState()
	{
		return _acquisitionStore.load(_profile.profileId()).orElseThrow().state();
	}

	private PhantomAcquisitionSourcePlanner.Result planManor(long baseline, long required)
	{
		final Map<Integer, Long> inventory = Map.of(_manorCandidate.fact().seedItemId(), _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1), _manorCandidate.fact().cropItemId(), baseline, PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, _player.getInventory().getInventoryItemCount(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, -1));
		return _acquisitionPlanner.plan(new PhantomAcquisitionSourcePlanner.Request(_profile.profileId(), _manorCandidate.fact().cropItemId(), required, PhantomActivityState.ACTIVE, MELEE_CLASS_ID, _player.getLevel(), inventory, Map.of(), Set.of(Method.MANOR_CROP), Method.MANOR_CROP, _manorCandidate.anchorId(), "", ResourceEvidence.unavailable(), Map.of(), 1));
	}

	private PhantomAcquisitionSourcePlanner.Result planQuest(QuestSource questSource, long baseline)
	{
		final Rule rule = questSource.rule();
		final QuestEvidence evidence = new QuestEvidence("STARTED", rule.allowedConds().getFirst(), Map.of(), baseline);
		return _acquisitionPlanner.plan(new PhantomAcquisitionSourcePlanner.Request(_profile.profileId(), rule.questItemId(), 1, PhantomActivityState.ACTIVE, MELEE_CLASS_ID, _player.getLevel(), Map.of(rule.questItemId(), baseline), Map.of(), Set.of(Method.QUEST_COLLECTION), Method.QUEST_COLLECTION, "", "", ResourceEvidence.unavailable(), Map.of(), 1, Map.of(rule.id(), evidence)));
	}

	private static RankedSource exactRanked(PhantomAcquisitionSourcePlanner.Result result, int npcId, String nodeId)
	{
		return result.ranked().stream().filter(value -> (value.source().npcId() == npcId) && value.source().topologyNodeId().equals(nodeId)).findFirst().or(() -> result.ranked().stream().filter(value -> value.source().npcId() == npcId).findFirst()).orElseThrow(() -> new AssertionError("Planner did not expose the exact production source."));
	}

	private QuestSource plannerQuestSource(Rule rule, RankedSource ranked)
	{
		final Source source = ranked.source();
		return new QuestSource(rule, source.npcId(), sourceCombatPoint(source.npcId(), source.topologyNodeId(), source.anchorId()), sourceTerritory(source.npcId(), source.topologyNodeId()));
	}

	private Item ensureInventoryItem(int itemId, long count)
	{
		Item item = _player.getInventory().getItemByItemId(itemId);
		final long current = item == null ? 0 : item.getCount();
		if (current < count)
		{
			item = _player.getInventory().addItem(ItemProcessType.REWARD, itemId, count - current, _player, this);
		}
		PhantomAssertions.assertTrue(item != null, "Could not create test-owned inventory item: " + itemId);
		return item;
	}

	private void enableExactItemSkill(int itemId, String message)
	{
		final Item item = _player.getInventory().getItemByItemId(itemId);
		final var skills = item == null ? null : item.getTemplate().getSkills();
		PhantomAssertions.assertTrue((skills != null) && (skills.length == 1), message);
		_player.enableSkill(skills[0].getSkill());
		_player.setCurrentMp(_player.getMaxMp());
	}

	private void testCanonicalManorChain() throws Exception
	{
		prepareManorActor();
		Monster seededTarget = null;
		boolean observedFailure = false;
		for (int attempt = 0; (attempt < 32) && (seededTarget == null); attempt++)
		{
			await(() -> !_player.isCastingNow() && !_player.isAttackingNow(), "Canonical sow cleanup did not quiesce before the next bounded attempt.");
			enableExactItemSkill(_manorCandidate.fact().seedItemId(), "Canonical seed item lost its exact Sowing skill.");
			final Monster target = spawnNormalMonster(targetMaximumHp());
			try (ExternalActionLease lease = acquireAcquisition("manor-sow-" + attempt))
			{
				final var inventory = lease.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
				final long before = inventory.seedCount();
				final AcquisitionTargetSnapshot live = lease.acquisitionTargetSnapshot(target.getObjectId());
				PhantomAssertions.assertTrue((live != null) && live.manorLiveValidFor(lease.actorSnapshot(), _manorCandidate.npcId(), 2000), "Exact manor target was not sow-eligible.");
				PhantomAssertions.assertEquals(_sourceTerritory.geometrySnapshot().orElseThrow().hash(), live.territoryGeometryHash(), "Manor target did not retain selected factual territory ownership.");
				boolean consumed = false;
				for (int dispatch = 0; (dispatch < 3) && !consumed; dispatch++)
				{
					final ActionOutcome outcome = lease.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), target.getObjectId());
					PhantomAssertions.assertTrue((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED), "Canonical Seed handler was not issued.");
					consumed = waitFor(() -> _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1) < before, 4000);
				}
				PhantomAssertions.assertTrue(consumed, "Canonical sow attempts did not consume exactly one seed.");
				PhantomAssertions.assertEquals(before - 1, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().seedItemId(), -1), "Canonical sow consumed an unexpected seed count.");
				if (target.isSeeded())
				{
					PhantomAssertions.assertEquals(_player.getObjectId(), target.getSeederId(), "Canonical sow recorded the wrong seeder.");
					PhantomAssertions.assertEquals(_manorCandidate.fact().seedItemId(), target.getSeed().getSeedId(), "Canonical sow recorded the wrong seed.");
					seededTarget = target;
				}
				else
				{
					observedFailure = true;
				}
			}
		}
		PhantomAssertions.assertTrue(seededTarget != null, "Bounded canonical sow attempts produced no successful seeded target.");
		final Monster harvestTarget = seededTarget;
		try (ExternalActionLease recovered = acquireAcquisition("manor-sow-recovery"))
		{
			final var inventory = recovered.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, recovered.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), harvestTarget.getObjectId()), "Observed sow was blindly dispatched twice.");
		}
		runManorServiceAttribution(harvestTarget);
		// A normal seed has a high canonical chance; the explicit formula suite supplies deterministic failed-attempt coverage.
		PhantomAssertions.assertTrue(observedFailure || (_manorCandidate.sowChance() >= 1), "Canonical sow failure evidence became inconsistent.");
	}

	private void testManorControls()
	{
		prepareManorActor();
		final Monster liveTarget = spawnNormalMonster(targetMaximumHp());
		try (ExternalActionLease lease = acquireAcquisition("manor-controls"))
		{
			final var inventory = lease.manorInventory(_manorCandidate.fact().seedItemId(), _manorCandidate.fact().cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			PhantomAssertions.assertTrue((inventory.seedObjectId() > 0) && (inventory.harvesterObjectId() > 0), "Exact seed/harvester ownership was not observed.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.useExactSeed(inventory.seedObjectId() + 1, _manorCandidate.fact().seedItemId(), liveTarget.getObjectId()), "Wrong seed object was admitted.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.useExactSeed(inventory.seedObjectId(), _manorCandidate.fact().seedItemId(), liveTarget.getObjectId() + 1), "Wrong manor target was admitted.");
			final ActorSnapshot actor = lease.actorSnapshot();
			final AcquisitionTargetSnapshot base = lease.acquisitionTargetSnapshot(liveTarget.getObjectId());
			final AcquisitionTargetSnapshot forbidden = new AcquisitionTargetSnapshot(base.objectId(), base.npcId(), base.instanceId(), base.distance(), false, false, true, true, false, true, true, false, true, false, 0, false, false, base.level(), true, true, false, false, 0, 0, base.onKillDelayMillis());
			PhantomAssertions.assertFalse(forbidden.manorLiveValidFor(actor, _manorCandidate.npcId(), 2000), "Raid manor target was admitted.");
		}
	}

	private void runManorServiceAttribution(Monster target) throws Exception
	{
		final int cropItemId = _manorCandidate.fact().cropItemId();
		final long existingCrop = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		destroyInventoryCount(_player, cropItemId, existingCrop);
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		final long matureBefore = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().matureItemId(), -1);
		final long reward1Before = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward1ItemId(), -1);
		final long reward2Before = _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward2ItemId(), -1);

		final var planned = planManor(0, 100);
		final RankedSource selected = planned.ranked().stream().filter(value -> value.source().sourceId().equals(_manorCandidate.sourceId())).findFirst().orElseThrow(() -> new AssertionError("Planner lost the selected current manor source."));
		final ManorBinding plannedBinding = (ManorBinding) selected.methodBinding();
		final ManorBinding exact;
		try (ExternalActionLease lease = acquireAcquisition("manor-service-binding"))
		{
			final var inventory = lease.manorInventory(plannedBinding.seedItemId(), plannedBinding.cropItemId(), PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID);
			exact = new ManorBinding(plannedBinding.castleId(), plannedBinding.seedItemId(), plannedBinding.cropItemId(), plannedBinding.matureItemId(), plannedBinding.reward1ItemId(), plannedBinding.reward2ItemId(), plannedBinding.seedLevel(), plannedBinding.alternative(), plannedBinding.rawSeedLimit(), plannedBinding.rawCropLimit(), inventory.seedObjectId(), inventory.harvesterObjectId(), inventory.seedCount(), 0, plannedBinding.authorityHash());
		}
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), exact, 0, 100, Phase.COMBAT_PREPARED, target.getObjectId());
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 1).status(), "Service manor Combat was not submitted.");
		PhantomAssertions.assertEquals(Phase.COMBAT_SUBMITTED, acquisitionState().phase(), "Service manor did not persist COMBAT_SUBMITTED.");
		final String owner = "acquisition:" + canonicalDigest(acquisitionState().goalId(), acquisitionState().goalRevision(), acquisitionState().selectedSource().sourceId(), target.getObjectId()).substring(0, 48);
		PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Service manor Combat lost exact acquisition ownership.");
		ensureInventoryItem(cropItemId, 5);
		awaitCombatOutcome(target, "Service manor existing Combat");
		awaitTerminal();
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 2).status(), "Service manor terminal Combat was not consumed.");
		PhantomAssertions.assertEquals(Phase.COMBAT_TERMINAL, acquisitionState().phase(), "Service manor terminal phase was not durable.");
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 3).status(), "External crop observation did not prepare harvest.");
		final PhantomAcquisitionState prepared = acquisitionState();
		PhantomAssertions.assertEquals(Phase.HARVEST_PREPARED, prepared.phase(), "External crop observation did not retain the exact corpse.");
		PhantomAssertions.assertEquals(5L, prepared.lastObservedCount(), "External crop observation did not update overall truth.");
		PhantomAssertions.assertEquals(5L, prepared.progress(), "External crop observation did not update baseline-derived progress.");
		PhantomAssertions.assertEquals(ReceiptKind.VERIFY, prepared.receipts().getLast().kind(), "External crop delta was misattributed to Harvester.");
		PhantomAssertions.assertEquals(0L, prepared.receipts().getLast().beforeCount(), "External crop receipt before-count drifted.");
		PhantomAssertions.assertEquals(5L, prepared.receipts().getLast().afterCount(), "External crop receipt after-count drifted.");
		PhantomAssertions.assertEquals(5L, ((ManorBinding) prepared.methodBinding()).cropCountBeforeDispatch(), "Harvester baseline did not rebase to the observed crop count.");
		restartAcquisitionService();
		PhantomAssertions.assertEquals(prepared, acquisitionState(), "Prepared manor restart changed overall or handler-bound truth.");
		await(() -> !_player.isCastingNow() && !_player.isAttackingNow(), "Service manor Combat cleanup did not quiesce before Harvester dispatch.");
		enableExactItemSkill(PhantomAcquisitionManorAuthority.HARVESTER_ITEM_ID, "Canonical Harvester item lost its exact skill.");

		long cropAfter = 5;
		for (int attempt = 0; (attempt < 3) && (cropAfter == 5); attempt++)
		{
			final PhantomAcquisitionService.OperationResult dispatch = advanceAcquisition(goal, 4 + (attempt * 2L));
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, dispatch.status(), "Service manor Harvester dispatch was not accepted: " + dispatch.reasonKey());
			PhantomAssertions.assertEquals(Phase.HARVEST_DISPATCHING, acquisitionState().phase(), "Service manor Harvester dispatch did not persist HARVEST_DISPATCHING.");
			waitFor(() -> _player.getInventory().getInventoryItemCount(cropItemId, -1) > 5, 4000);
			advanceAcquisition(goal, 5 + (attempt * 2L));
			cropAfter = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		}
		cropAfter = _player.getInventory().getInventoryItemCount(cropItemId, -1);
		PhantomAssertions.assertTrue(cropAfter > 5, "Service Harvester produced no bounded crop delta.");
		final PhantomAcquisitionState harvested = acquisitionState();
		PhantomAssertions.assertEquals(cropAfter, harvested.lastObservedCount(), "Successful Harvester did not update overall truth.");
		final var harvestReceipt = harvested.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_MANOR_HARVEST).findFirst().orElseThrow(() -> new AssertionError("Successful Harvester receipt is absent."));
		PhantomAssertions.assertEquals(5L, harvestReceipt.beforeCount(), "Harvester receipt absorbed the external crop delta.");
		PhantomAssertions.assertEquals(cropAfter, harvestReceipt.afterCount(), "Harvester receipt lost its exact crop delta.");
		PhantomAssertions.assertEquals(TerminalResult.OBSERVED, harvestReceipt.result(), "Harvester receipt was not authoritative evidence.");
		PhantomAssertions.assertEquals(1L, harvested.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.VERIFY).count(), "External and Harvester receipts were not kept separate.");
		PhantomAssertions.assertEquals(matureBefore, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().matureItemId(), -1), "Manor acquisition credited mature seed.");
		PhantomAssertions.assertEquals(reward1Before, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward1ItemId(), -1), "Manor acquisition performed reward exchange 1.");
		PhantomAssertions.assertEquals(reward2Before, _player.getInventory().getInventoryItemCount(_manorCandidate.fact().reward2ItemId(), -1), "Manor acquisition performed reward exchange 2.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Terminal manor service retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Terminal manor service retained an external claim.");
	}

	private void testCanonicalQuestChain() throws Exception
	{
		for (QuestSource source : _questSources)
		{
			selectQuestSource(source);
			relocateToCombatPoint();
			prepareQuestActor();
			final QuestState state = _player.getQuestState(_questRule.questName());
			PhantomAssertions.assertTrue((state != null) && state.isStarted() && (state.getCond() == _questRule.allowedConds().getFirst()), "Exact already-started quest setup is absent.");
			boolean granted = false;
			boolean notGranted = false;
			long count = _player.getInventory().getInventoryItemCount(_questRule.questItemId(), -1);
			for (int attempt = 0; (attempt < 32) && !(granted && notGranted) && (count < _questRule.itemCap()); attempt++)
			{
				resetActor(true);
				relocateToCombatPoint();
				_player.setInvul(true);
				ensureWeapon();
				final Monster target = spawnNormalMonster(1);
				target.setOnKillDelay(100);
				final long before = count;
				final var started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Existing Combat did not accept curated quest target " + source.npcId() + ".");
				awaitCombatOutcome(target, "Curated quest existing Combat " + source.npcId());
				PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Existing Combat did not kill curated quest target " + source.npcId() + ".");
				awaitTerminal();
				consumeTerminal();
				Thread.sleep(300);
				count = _player.getInventory().getInventoryItemCount(_questRule.questItemId(), -1);
				granted |= count == (before + 1);
				notGranted |= count == before;
				PhantomAssertions.assertTrue((count == before) || (count == (before + 1)), "Curated delayed callback produced an invalid item delta for " + source.npcId() + ".");
				PhantomAssertions.assertTrue(state.isStarted() && (state.getCond() == _questRule.allowedConds().getFirst()), "Kill collection changed quest state/cond below the audited cap.");
			}
			PhantomAssertions.assertTrue(granted && notGranted, "Bounded real delayed quest kills did not observe both grant and no-grant paths for " + source.npcId() + ".");
			PhantomAssertions.assertTrue(count <= _questRule.itemCap(), "Active quest collection crossed its conservative item cap.");
		}
	}

	private void testQuestControls()
	{
		PhantomAssertions.assertTrue(_questCatalog.current(), "Curated quest authority is not current after exact script loading.");
		boolean mappedTerritoryNegativeObserved = false;
		for (QuestSource source : _questSources)
		{
			selectQuestSource(source);
			relocateToCombatPoint();
			prepareQuestActor();
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst() + 1, 0, source.npcId(), false), "Wrong quest cond was admitted.");
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst(), _questRule.itemCap(), source.npcId(), false), "Quest item cap was admitted.");
			PhantomAssertions.assertFalse(_questRule.supports(_questRule.allowedConds().getFirst(), 0, source.npcId() + 1, false), "Wrong quest target was admitted.");
			try (ExternalActionLease lease = acquireAcquisition("quest-controls-" + source.npcId()))
			{
				final var snapshot = lease.questState(_questRule.questName(), _questRule.expectedVars());
				PhantomAssertions.assertTrue((snapshot != null) && "STARTED".equals(snapshot.state()) && (snapshot.cond() == _questRule.allowedConds().getFirst()), "Exact active quest state was not read through the acquisition seam.");
				final Source selectedSource = questAcquisitionSource(source);
				final Monster owned = spawnNormalMonster(targetMaximumHp());
				final AcquisitionTargetSnapshot target = lease.acquisitionTargetSnapshot(owned.getObjectId());
				PhantomAssertions.assertTrue((target != null) && target.spawnTerritoryPresent() && _sourceTerritory.geometrySnapshot().orElseThrow().hash().equals(target.territoryGeometryHash()), "Quest target did not retain exact mapped territory ownership.");
				PhantomAssertions.assertTrue(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, target), "Selected factual territory target was rejected.");

				final SpawnFact otherMapped = _query.snapshot().spawnFactsByNpc().get(source.npcId()).stream().filter(fact -> (fact.territoryGeometry() != null) && (fact.topologyNodeId() != null) && !fact.topologyNodeId().equals(source.combatPoint().topologyNodeId())).findFirst().orElse(null);
				if (otherMapped != null)
				{
					final Monster mappedOther = spawnWithTerritory(source.npcId(), runtimeTerritory(source.npcId(), otherMapped), targetMaximumHp());
					PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, lease.acquisitionTargetSnapshot(mappedOther.getObjectId())), "Same NPC from another mapped territory was admitted.");
					mappedTerritoryNegativeObserved = true;
				}

				final SpawnFact unmapped = _query.snapshot().spawnFactsByNpc().get(source.npcId()).stream().filter(fact -> (fact.territoryGeometry() != null) && (fact.topologyNodeId() == null)).findFirst().orElseThrow(() -> new AssertionError("Quest target lacks an infeasible negative-control territory."));
				final Monster unmappedTarget = spawnWithTerritory(source.npcId(), runtimeTerritory(source.npcId(), unmapped), targetMaximumHp());
				PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, lease.acquisitionTargetSnapshot(unmappedTarget.getObjectId())), "Same NPC from an unmapped territory was admitted.");

				final Monster exactPoint = spawnExactPointMonster(source.npcId(), targetMaximumHp());
				final AcquisitionTargetSnapshot exactPointSnapshot = lease.acquisitionTargetSnapshot(exactPoint.getObjectId());
				PhantomAssertions.assertTrue((exactPointSnapshot != null) && exactPointSnapshot.exactPointSpawn(), "Exact-point negative-control Spawn was not observed.");
				PhantomAssertions.assertFalse(PhantomAcquisitionService.ownsMappedTarget(_query, selectedSource, exactPointSnapshot), "Same NPC exact-point Spawn inherited polygon source ownership.");
			}
		}
		PhantomAssertions.assertTrue(mappedTerritoryNegativeObserved, "No same-NPC alternate mapped territory negative control was exercised.");
	}

	private void testQuestServiceLifecycle() throws Exception
	{
		testQuestForeignSession();
		boolean observedGrant = false;
		boolean observedNoGrant = false;
		final Set<Integer> coveredTargets = new java.util.HashSet<>();
		for (int attempt = 0; (attempt < 24) && ((coveredTargets.size() < 3) || !observedGrant || !observedNoGrant); attempt++)
		{
			final QuestSource source = _questSources.get(attempt % _questSources.size());
			final boolean granted = runQuestServiceAttempt(source, attempt < _questSources.size());
			coveredTargets.add(source.npcId());
			observedGrant |= granted;
			observedNoGrant |= !granted;
		}
		PhantomAssertions.assertEquals(Set.of(20013, 20019, 20016), coveredTargets, "Full service quest coverage lost Q102/Q152 targets.");
		PhantomAssertions.assertTrue(observedGrant, "Full service delayed callbacks produced no real quest item.");
		PhantomAssertions.assertTrue(observedNoGrant, "Full service delayed callbacks produced no bounded no-grant path.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Terminal quest service retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Terminal quest service retained an external claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().navigationClaims(), "Terminal quest service retained navigation ownership.");
	}

	private void testQuestExactDeltaBoundaries()
	{
		for (Rule rule : _questCatalog.rules())
		{
			final QuestSource source = _questSources.stream().filter(value -> value.rule().id().equals(rule.id())).findFirst().orElseThrow();
			selectQuestSource(source);
			prepareQuestActor();
			final String initialQuestTruth = questTruth(rule);

			QuestDeltaScenario scenario = prepareQuestDeltaScenario(source, 0, 1, 1, Phase.QUEST_CALLBACK_WAIT);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.COMPLETE_GOAL, advanceAcquisition(scenario.goal(), 10 + _acquisitionRevision).status(), "Exact +1 callback did not complete " + rule.id() + ".");
			PhantomAcquisitionState observed = acquisitionState();
			PhantomAssertions.assertEquals(Status.COMPLETED, observed.status(), "Exact +1 callback did not complete acquisition state.");
			PhantomAssertions.assertEquals(Phase.NONE, observed.phase(), "Exact +1 callback retained an intermediate phase.");
			PhantomAssertions.assertEquals(0, observed.targetObjectId(), "Exact +1 callback retained its old target.");
			PhantomAssertions.assertEquals(1L, observed.progress(), "Exact +1 callback did not update baseline-derived progress.");
			PhantomAssertions.assertEquals(1L, observed.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION).count(), "Exact +1 callback did not create exactly one quest receipt.");
			final var exactReceipt = observed.receipts().getLast();
			PhantomAssertions.assertEquals(0L, exactReceipt.beforeCount(), "Exact quest receipt did not use itemCountBeforeKill.");
			PhantomAssertions.assertEquals(1L, exactReceipt.afterCount(), "Exact quest receipt lost the validated current count.");
			PhantomAssertions.assertEquals(TerminalResult.OBSERVED, exactReceipt.result(), "Exact quest receipt is not authoritative observation.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, _acquisitionGoals.load(_profile.profileId()).orElseThrow().goal().status(), "Exact +1 callback did not atomically complete Goal.");
			assertQuestTruth(rule, initialQuestTruth);

			scenario = prepareQuestDeltaScenario(source, 0, 1, 2, Phase.QUEST_CALLBACK_WAIT);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(scenario.goal(), 20 + _acquisitionRevision).status(), "Exact +1 partial callback failed for " + rule.id() + ".");
			observed = acquisitionState();
			PhantomAssertions.assertEquals(Status.READY, observed.status(), "Partial exact callback did not return to READY.");
			PhantomAssertions.assertEquals(Phase.TARGET_REQUIRED, observed.phase(), "Partial exact callback did not require a new target.");
			PhantomAssertions.assertEquals(0, observed.targetObjectId(), "Partial exact callback retained its callback target.");
			PhantomAssertions.assertEquals(1L, observed.progress(), "Partial exact callback did not preserve one unit of progress.");
			setQuestItemCount(rule.questItemId(), 2);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(scenario.goal(), 21 + _acquisitionRevision).status(), "Post-observation target preparation failed.");
			final PhantomAcquisitionState preparedAfterExtra = acquisitionState();
			PhantomAssertions.assertEquals(1L, preparedAfterExtra.progress(), "A later inventory delta was absorbed by a second quest verification read.");
			PhantomAssertions.assertEquals(1L, preparedAfterExtra.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION).count(), "A later inventory delta created a second quest receipt.");
			PhantomAssertions.assertEquals(2L, ((QuestBinding) preparedAfterExtra.methodBinding()).itemCountBeforeKill(), "New quest target did not bind the independent current baseline.");
			assertQuestTruth(rule, initialQuestTruth);

			assertInvalidQuestDelta(prepareQuestDeltaScenario(source, 0, 2, 1, Phase.QUEST_CALLBACK_WAIT), "quest.invalid_delta", "Below-cap +2 callback was accepted for " + rule.id() + ".");
			assertInvalidQuestDelta(prepareQuestDeltaScenario(source, 0, rule.itemCap() + 1L, 1, Phase.QUEST_CALLBACK_WAIT), "quest.item_cap", "Above-cap callback was accepted for " + rule.id() + ".");
			assertInvalidQuestDelta(prepareQuestDeltaScenario(source, 1, 0, 1, Phase.QUEST_CALLBACK_WAIT), "quest.item_count_decreased", "Decreased callback count was accepted for " + rule.id() + ".");
			assertQuestTruth(rule, initialQuestTruth);

			scenario = prepareQuestDeltaScenario(source, 0, 1, 1, Phase.VERIFYING);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.COMPLETE_GOAL, advanceAcquisition(scenario.goal(), 30 + _acquisitionRevision).status(), "Legacy VERIFYING exact +1 did not recover for " + rule.id() + ".");
			PhantomAssertions.assertEquals(1L, acquisitionState().receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION).count(), "Legacy VERIFYING exact +1 lacks its dedicated receipt.");
			assertInvalidQuestDelta(prepareQuestDeltaScenario(source, 0, 2, 1, Phase.VERIFYING), "quest.invalid_delta", "Legacy VERIFYING +2 was accepted for " + rule.id() + ".");

			assertQuestCapBoundary(source, Phase.QUEST_CALLBACK_WAIT, 1, true);
			assertQuestCapBoundary(source, Phase.QUEST_CALLBACK_WAIT, 2, false);
			assertQuestCapBoundary(source, Phase.VERIFYING, 1, true);
			assertQuestCapBoundary(source, Phase.VERIFYING, 2, false);
			assertQuestTruth(rule, initialQuestTruth);

			scenario = prepareQuestDeltaScenario(source, 0, 0, 1, Phase.QUEST_COMBAT_PREPARED);
			final PhantomAcquisitionState beforeSubmission = acquisitionState();
			final String owner = "acquisition:" + canonicalDigest(beforeSubmission.goalId(), beforeSubmission.goalRevision(), beforeSubmission.selectedSource().sourceId(), beforeSubmission.targetObjectId()).substring(0, 48);
			setQuestItemCount(rule.questItemId(), 1);
			final PhantomAcquisitionService.OperationResult drift = advanceAcquisition(scenario.goal(), 40 + _acquisitionRevision);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, drift.status(), "Pre-combat quest item drift was accepted for " + rule.id() + ".");
			PhantomAssertions.assertEquals("quest.item_count_changed", drift.reasonKey(), "Pre-combat quest item drift lost its typed failure.");
			PhantomAssertions.assertFalse(acquisitionState().phase() == Phase.QUEST_COMBAT_SUBMITTED, "Pre-combat quest item drift advanced to submitted Combat.");
			PhantomAssertions.assertEquals(0L, acquisitionState().progress(), "Pre-combat quest item drift was recorded as progress.");
			PhantomAssertions.assertTrue(acquisitionState().receipts().isEmpty(), "Pre-combat quest item drift created a quest receipt.");
			PhantomAssertions.assertFalse(_combat.matchesAcquisitionSession(_profile.profileId(), beforeSubmission.targetObjectId(), owner), "Blocked pre-combat drift acquired a Combat session.");
			PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty(), "Blocked pre-combat drift retained Combat ownership.");
			assertQuestTruth(rule, initialQuestTruth);
		}
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Exact-delta terminal paths retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Exact-delta terminal paths retained an external claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().navigationClaims(), "Exact-delta terminal paths retained navigation ownership.");
	}

	private void assertQuestCapBoundary(QuestSource source, Phase phase, long required, boolean completes)
	{
		final Rule rule = source.rule();
		final long before = rule.itemCap() - 1;
		final QuestDeltaScenario scenario = prepareQuestDeltaScenario(source, before, rule.itemCap(), required, phase);
		final PhantomAcquisitionService.OperationResult result = advanceAcquisition(scenario.goal(), 45 + _acquisitionRevision);
		PhantomAssertions.assertEquals(completes ? PhantomAcquisitionService.OperationStatus.COMPLETE_GOAL : PhantomAcquisitionService.OperationStatus.REPLAN, result.status(), "Exact cap boundary returned the wrong operation status for " + rule.id() + "/" + phase + "/required=" + required + ".");
		final PhantomAcquisitionState observed = acquisitionState();
		PhantomAssertions.assertEquals((long) rule.itemCap(), observed.lastObservedCount(), "Exact cap boundary lost the authoritative cap count.");
		PhantomAssertions.assertEquals(1L, observed.progress(), "Exact cap boundary lost its single-item progress.");
		PhantomAssertions.assertEquals(Phase.NONE, observed.phase(), "Exact cap boundary retained an executable phase.");
		PhantomAssertions.assertEquals(0, observed.targetObjectId(), "Exact cap boundary retained its target object.");
		PhantomAssertions.assertEquals(0, observed.targetNpcId(), "Exact cap boundary retained its target NPC.");
		PhantomAssertions.assertEquals(0, observed.targetInstanceId(), "Exact cap boundary retained its target instance.");
		PhantomAssertions.assertEquals(1L, observed.receipts().stream().filter(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION).count(), "Exact cap boundary did not retain exactly one ACTIVE_QUEST_COLLECTION receipt.");
		final var receipt = observed.receipts().getLast();
		PhantomAssertions.assertEquals(before, receipt.beforeCount(), "Exact cap receipt lost its persisted pre-kill baseline.");
		PhantomAssertions.assertEquals((long) rule.itemCap(), receipt.afterCount(), "Exact cap receipt lost its authoritative after-count.");
		PhantomAssertions.assertEquals(TerminalResult.OBSERVED, receipt.result(), "Exact cap receipt is not an observed active grant.");
		final QuestBinding historical = (QuestBinding) observed.methodBinding();
		PhantomAssertions.assertEquals(before, historical.itemCountBeforeKill(), "Persisted cap binding is not the valid historical pre-kill binding.");
		PhantomAssertions.assertTrue(historical.itemCountBeforeKill() < historical.itemCap(), "Persisted cap binding violates the executable baseline invariant.");
		PhantomAssertions.assertEquals(0L, historical.callbackDeadlineMillis(), "Persisted cap binding retained a callback deadline.");
		final var codec = new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec();
		PhantomAssertions.assertEquals(observed, codec.decode(codec.encode(observed)), "Exact cap schema-3 acquisition state did not round-trip.");
		if (completes)
		{
			PhantomAssertions.assertEquals(Status.COMPLETED, observed.status(), "Completion exactly at cap did not complete acquisition state.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, _acquisitionGoals.load(_profile.profileId()).orElseThrow().goal().status(), "Completion exactly at cap did not atomically complete Goal.");
		}
		else
		{
			PhantomAssertions.assertEquals("quest.item_cap", result.reasonKey(), "Partial exact cap boundary lost its typed source-exhaustion reason.");
			PhantomAssertions.assertEquals(Status.BLOCKED, observed.status(), "Partial completion at cap did not block the exhausted quest source.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, _acquisitionGoals.load(_profile.profileId()).orElseThrow().goal().status(), "Partial completion at cap completed Goal.");
			PhantomAssertions.assertEquals("quest.item_cap", observed.candidates().get(observed.sourceCursor()).lastFailureReason(), "Partial completion at cap did not record the candidate failure.");
			PhantomAssertions.assertEquals(PhantomAcquisitionService.DirectiveKind.SWITCH, _acquisition.directive(_profile.profileId(), scenario.goal(), PhantomActivityState.ACTIVE).kind(), "Partial completion at cap did not direct a source switch.");
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, advanceAcquisition(scenario.goal(), 46 + _acquisitionRevision).status(), "A subsequent active advance executed the exhausted quest source.");
			PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty(), "Blocked cap boundary retained or started Combat.");
		}
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().currentClaims(), "Exact cap boundary retained a state claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().externalClaims(), "Exact cap boundary retained an external claim.");
		PhantomAssertions.assertEquals(0, _acquisition.snapshot().navigationClaims(), "Exact cap boundary retained navigation ownership.");
	}

	private QuestDeltaScenario prepareQuestDeltaScenario(QuestSource source, long before, long after, long required, Phase phase)
	{
		clearWorldFixtures();
		selectQuestSource(source);
		relocateToCombatPoint();
		prepareQuestActor();
		final var planned = planQuest(source, before);
		final RankedSource selected = exactRanked(planned, source.npcId(), source.combatPoint().topologyNodeId());
		selectQuestSource(plannerQuestSource(source.rule(), selected));
		relocateToCombatPoint();
		prepareQuestActor();
		setQuestItemCount(source.rule().questItemId(), after);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final QuestBinding plannedBinding = (QuestBinding) selected.methodBinding();
		final long deadline = phase == Phase.QUEST_CALLBACK_WAIT ? _epochMillis.get() + _acquisitionCatalog.limits().questCallbackWaitMillis() : 0;
		final QuestBinding binding = new QuestBinding(plannedBinding.ruleId(), plannedBinding.ruleHash(), plannedBinding.questId(), plannedBinding.questName(), plannedBinding.scriptHash(), plannedBinding.expectedState(), plannedBinding.expectedCond(), plannedBinding.questItemId(), plannedBinding.itemCap(), plannedBinding.targetNpcId(), before, deadline, plannedBinding.authorityHash());
		return new QuestDeltaScenario(installAcquisition(selected, planned.ranked(), binding, before, required, phase, target.getObjectId()), target);
	}

	private void assertInvalidQuestDelta(QuestDeltaScenario scenario, String reason, String message)
	{
		final PhantomAcquisitionService.OperationResult result = advanceAcquisition(scenario.goal(), 50 + _acquisitionRevision);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, result.status(), message);
		PhantomAssertions.assertEquals(reason, result.reasonKey(), "Invalid quest delta lost its typed failure.");
		PhantomAssertions.assertEquals(0L, acquisitionState().progress(), "Invalid quest delta changed acquisition progress.");
		PhantomAssertions.assertTrue(acquisitionState().receipts().stream().noneMatch(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION), "Invalid quest delta created a quest receipt.");
		PhantomAssertions.assertFalse(acquisitionState().status() == Status.COMPLETED, "Invalid quest delta completed acquisition state.");
		PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, _acquisitionGoals.load(_profile.profileId()).orElseThrow().goal().status(), "Invalid quest delta completed Goal.");
	}

	private void setQuestItemCount(int itemId, long count)
	{
		final long current = _player.getInventory().getInventoryItemCount(itemId, -1);
		destroyInventoryCount(_player, itemId, current);
		if (count > 0)
		{
			ensureInventoryItem(itemId, count);
		}
		PhantomAssertions.assertEquals(count, _player.getInventory().getInventoryItemCount(itemId, -1), "Could not establish exact quest item test count.");
	}

	private String questTruth(Rule rule)
	{
		final QuestState state = _player.getQuestState(rule.questName());
		final List<String> variables = rule.expectedVars().stream().sorted().map(name -> name + "=" + state.get(name)).toList();
		return state.getState() + "|" + state.getCond() + "|" + state.get("cond") + "|" + variables;
	}

	private void assertQuestTruth(Rule rule, String expected)
	{
		PhantomAssertions.assertEquals(expected, questTruth(rule), "Acquisition changed curated quest state/cond/vars for " + rule.id() + ".");
	}

	private record QuestDeltaScenario(PhantomGoal goal, Monster target)
	{
	}

	private boolean runQuestServiceAttempt(QuestSource source, boolean restartEveryPhase) throws Exception
	{
		clearWorldFixtures();
		selectQuestSource(source);
		relocateToCombatPoint();
		prepareQuestActor();
		final Rule rule = source.rule();
		final long existing = _player.getInventory().getInventoryItemCount(rule.questItemId(), -1);
		destroyInventoryCount(_player, rule.questItemId(), existing);
		final QuestState questState = _player.getQuestState(rule.questName());
		final int originalCond = questState.getCond();
		final byte originalState = questState.getState();
		_epochMillis.addAndGet(_acquisitionCatalog.limits().questCallbackWaitMillis() + 10_000L);

		final var planned = planQuest(source, 0);
		final RankedSource selected = exactRanked(planned, source.npcId(), source.combatPoint().topologyNodeId());
		selectQuestSource(plannerQuestSource(rule, selected));
		relocateToCombatPoint();
		prepareQuestActor();
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), selected.methodBinding(), 0, 1, Phase.TARGET_REQUIRED, 0);
		PhantomAssertions.assertEquals(0L, ((QuestBinding) acquisitionState().methodBinding()).callbackDeadlineMillis(), "Quest deadline existed before callback preparation.");
		final Monster fixtureTarget = spawnNormalMonster(1);
		fixtureTarget.setOnKillDelay(100);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 100 + _acquisitionRevision).status(), "Full service quest target selection failed.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_PREPARED, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_PREPARED.");
		final var selectedObject = World.getInstance().findObject(acquisitionState().targetObjectId());
		PhantomAssertions.assertTrue(selectedObject instanceof Monster, "Planner-owned exact quest target is not a real Monster.");
		final Monster target = (Monster) selectedObject;
		// Loaded territories can already contain an older object id than the controlled fixture. Re-spawn that exact
		// selected object locally so its world/known-list and native attack path agree with the fixture position.
		target.decayMe();
		target.spawnMe(_player.getX() + 20, _player.getY(), _player.getZ());
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		target.setOnKillDelay(100);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 200 + _acquisitionRevision).status(), "Full service quest Combat submission failed.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_SUBMITTED, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_SUBMITTED.");
		final String owner = "acquisition:" + canonicalDigest(acquisitionState().goalId(), acquisitionState().goalRevision(), acquisitionState().selectedSource().sourceId(), target.getObjectId()).substring(0, 48);
		PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Full service quest session lost exact acquisition ownership.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_SUBMITTED, acquisitionState().phase(), "Submitted quest restart changed its durable phase.");
			PhantomAssertions.assertTrue(_combat.matchesAcquisitionSession(_profile.profileId(), target.getObjectId(), owner), "Submitted quest restart lost its Combat owner.");
		}
		awaitCombatOutcome(target, "Full service curated quest Combat " + source.npcId());
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Full service quest Combat did not terminate with a real kill.");
		final PhantomAcquisitionService.OperationResult terminalResult = advanceAcquisition(goal, 300 + _acquisitionRevision);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, terminalResult.status(), "Full service quest terminal was not consumed: " + terminalResult.reasonKey());
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_TERMINAL, acquisitionState().phase(), "Full service quest did not persist QUEST_COMBAT_TERMINAL.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_TERMINAL, acquisitionState().phase(), "Terminal quest restart changed its durable phase.");
		}
		Thread.sleep(300);
		final long callbackCount = _player.getInventory().getInventoryItemCount(rule.questItemId(), -1);
		PhantomAssertions.assertTrue((callbackCount == 0) || (callbackCount == 1), "Real delayed quest callback produced an invalid delta.");
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.SUCCESS, advanceAcquisition(goal, 400 + _acquisitionRevision).status(), "Full service quest callback wait was not prepared.");
		final PhantomAcquisitionState waiting = acquisitionState();
		PhantomAssertions.assertEquals(Phase.QUEST_CALLBACK_WAIT, waiting.phase(), "Full service quest did not persist QUEST_CALLBACK_WAIT.");
		final QuestBinding binding = (QuestBinding) waiting.methodBinding();
		final long deadline = binding.callbackDeadlineMillis();
		PhantomAssertions.assertTrue(deadline > _epochMillis.get(), "Quest callback deadline is not an absolute future epoch millisecond.");
		PhantomAssertions.assertEquals(waiting, new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec().decode(new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec().encode(waiting)), "Quest callback deadline did not survive schema-3 restart encoding.");
		if (restartEveryPhase)
		{
			restartAcquisitionService();
			PhantomAssertions.assertEquals(waiting, acquisitionState(), "Callback-wait restart reset its deadline or attempt.");
		}
		if (callbackCount == 1)
		{
			_epochMillis.set(deadline + 1_000_000L);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.COMPLETE_GOAL, advanceAcquisition(goal, 500 + _acquisitionRevision).status(), "Observed quest item was not committed before an expired deadline.");
			PhantomAssertions.assertEquals(Status.COMPLETED, acquisitionState().status(), "Real quest item did not persist completed acquisition truth.");
			PhantomAssertions.assertEquals(Phase.NONE, acquisitionState().phase(), "Successful quest callback retained an intermediate verification phase.");
			PhantomAssertions.assertTrue(acquisitionState().receipts().stream().anyMatch(receipt -> (receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION) && (receipt.beforeCount() == 0) && (receipt.afterCount() == 1)), "Real delayed callback lacks an active quest receipt.");
		}
		else
		{
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 500 + _acquisitionRevision).status(), "No-grant callback did not remain pending before its deadline.");
			PhantomAssertions.assertEquals(0, acquisitionState().phaseAttempt(), "Restart/pending observation reset or consumed a callback attempt.");
			_epochMillis.set(deadline - _acquisitionCatalog.limits().questCallbackWaitMillis() - 1);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 600 + _acquisitionRevision).status(), "Clock rollback beyond the wait window did not expire conservatively.");
			PhantomAssertions.assertEquals(1, acquisitionState().phaseAttempt(), "Clock rollback did not consume one bounded timeout attempt.");
			_epochMillis.set(deadline);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.RETRY, advanceAcquisition(goal, 700 + _acquisitionRevision).status(), "Exact callback deadline did not consume a bounded attempt.");
			PhantomAssertions.assertEquals(2, acquisitionState().phaseAttempt(), "Exact callback deadline attempt was not durable.");
			final var stored = _acquisitionStore.load(_profile.profileId()).orElseThrow();
			final QuestBinding legacy = new QuestBinding(binding.ruleId(), binding.ruleHash(), binding.questId(), binding.questName(), binding.scriptHash(), binding.expectedState(), binding.expectedCond(), binding.questItemId(), binding.itemCap(), binding.targetNpcId(), binding.itemCountBeforeKill(), 1, binding.authorityHash());
			_acquisitionStore.replace(_profile.profileId(), stored.rowVersion(), stored.state().withBinding(legacy, Phase.QUEST_CALLBACK_WAIT, stored.state().targetObjectId(), stored.state().targetNpcId(), stored.state().targetInstanceId(), stored.state().phaseAttempt(), stored.state().logicalMinute() + 1));
			_epochMillis.set(deadline + 10_000_000L);
			PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, advanceAcquisition(goal, 800 + _acquisitionRevision).status(), "Legacy/small deadline did not fail the source after the bounded limit.");
			PhantomAssertions.assertFalse(acquisitionState().phase() == Phase.QUEST_CALLBACK_WAIT, "Legacy/small deadline retained callback ownership.");
			PhantomAssertions.assertTrue(acquisitionState().receipts().stream().noneMatch(receipt -> receipt.kind() == ReceiptKind.ACTIVE_QUEST_COLLECTION), "No-grant timeout fabricated a quest receipt.");
		}
		PhantomAssertions.assertEquals(originalState, questState.getState(), "Acquisition service changed the already-started quest state.");
		PhantomAssertions.assertEquals(originalCond, questState.getCond(), "Acquisition service changed the already-started quest cond.");
		PhantomAssertions.assertTrue(_combat.find(_profile.profileId()).isEmpty(), "Terminal quest attempt retained Combat ownership.");
		return callbackCount == 1;
	}

	private void testQuestForeignSession()
	{
		clearWorldFixtures();
		final QuestSource source = _questSources.getFirst();
		selectQuestSource(source);
		relocateToCombatPoint();
		prepareQuestActor();
		final long existing = _player.getInventory().getInventoryItemCount(source.rule().questItemId(), -1);
		destroyInventoryCount(_player, source.rule().questItemId(), existing);
		final var planned = planQuest(source, 0);
		final RankedSource selected = exactRanked(planned, source.npcId(), source.combatPoint().topologyNodeId());
		selectQuestSource(plannerQuestSource(source.rule(), selected));
		relocateToCombatPoint();
		prepareQuestActor();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final PhantomGoal goal = installAcquisition(selected, planned.ranked(), selected.methodBinding(), 0, 1, Phase.QUEST_COMBAT_PREPARED, target.getObjectId());
		PhantomAssertions.assertTrue(_combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).accepted(), "Foreign quest Combat fixture was not established.");
		final var result = advanceAcquisition(goal, 50);
		PhantomAssertions.assertEquals(PhantomAcquisitionService.OperationStatus.REPLAN, result.status(), "Full service quest inherited a foreign Combat session.");
		PhantomAssertions.assertEquals("acquisition.combat.foreign_session", result.reasonKey(), "Foreign quest Combat lost its typed rejection.");
		PhantomAssertions.assertEquals(Phase.QUEST_COMBAT_PREPARED, acquisitionState().phase(), "Foreign quest Combat advanced acquisition state.");
		_combat.cancel(_profile.profileId());
		consumeTerminal();
	}

	private void clearWorldFixtures()
	{
		for (Monster fixture : List.copyOf(_worldFixtures))
		{
			if (fixture.isSpawned())
			{
				fixture.deleteMe();
			}
		}
		_worldFixtures.clear();
	}

	private Source questAcquisitionSource(QuestSource source)
	{
		final var anchor = _topology.snapshot().anchorsByNode().get(source.combatPoint().topologyNodeId()).getFirst();
		return new Source("a".repeat(64), Method.QUEST_COLLECTION, source.npcId(), source.rule().questItemId(), "quest:" + source.rule().id(), source.combatPoint().topologyNodeId(), anchor.id(), source.combatPoint().instanceId(), 0, 0, 0, 0);
	}

	private NpcSpawnTerritory runtimeTerritory(int npcId, SpawnFact fact)
	{
		final var geometry = fact.territoryGeometry();
		return SpawnTable.getInstance().getSpawns(npcId).stream().map(Spawn::getSpawnTerritory).filter(java.util.Objects::nonNull).filter(territory -> territory.geometrySnapshot().map(snapshot -> snapshot.hash().equals(geometry.geometryHash()) && snapshot.sourcePath().equals(geometry.sourcePath()) && snapshot.territoryName().equals(geometry.territoryName())).orElse(false)).findFirst().orElseThrow(() -> new AssertionError("Runtime negative-control territory differs from Game Knowledge."));
	}

	private record QuestSource(Rule rule, int npcId, SpawnFact combatPoint, NpcSpawnTerritory territory)
	{
	}

	private void prepareAcquisitionActor()
	{
		_player.setPlayerClass(SPOIL_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		_player.setInvul(true);
		ensureWeapon();
		for (int skillId : List.of(SPOIL_SKILL_ID, SWEEP_SKILL_ID))
		{
			final Skill skill = SkillData.getInstance().getSkill(skillId, skillId == SPOIL_SKILL_ID ? 11 : 1);
			PhantomAssertions.assertTrue(skill != null, "Canonical acquisition skill is unavailable: " + skillId);
			_player.addSkill(skill, false);
			_player.enableSkill(skill);
		}
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private void resetAcquisitionActor()
	{
		resetActor(true);
		prepareAcquisitionActor();
		relocateToCombatPoint();
	}

	private DeterministicSpoilMonster spawnDeterministicSpoilMonster()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		// NpcStat#getLevel reads the immutable template level, so a stat override does not remove the unseeded magic-resist roll.
		// Keep the canonical NPC/drop template while bounding only the cast fixture's effective level.
		final DeterministicSpoilMonster target = spawn(new DeterministicSpoilMonster(template), 20 + (_worldFixtures.size() * 5));
		target.setCurrentHp(Math.min(targetMaximumHp(), target.getMaxHp()));
		return target;
	}

	private ExternalActionLease acquireAcquisition(String operation)
	{
		final var result = _combat.acquireExternalAction(new ExternalActionRequest(_profile.profileId(), ExternalActionKind.ACQUISITION, operation, System.nanoTime() + TimeUnit.SECONDS.toNanos(10), () -> false));
		PhantomAssertions.assertEquals(ExternalActionStatus.ACQUIRED, result.status(), "Acquisition action lease was not acquired.");
		return result.lease();
	}

	private void testAcquisitionControls()
	{
		resetAcquisitionActor();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		try (ExternalActionLease lease = acquireAcquisition("acquisition-controls"))
		{
			final long inventoryBefore = _player.getInventory().getInventoryItemCount(57, -1);
			final Map<Integer, Long> inventoryCounts = lease.acquisitionInventoryCounts(List.of(57, 99999));
			PhantomAssertions.assertEquals(inventoryBefore, inventoryCounts.get(57), "Exact active inventory count differed from canonical Player inventory.");
			PhantomAssertions.assertEquals(0L, inventoryCounts.get(99999), "Absent exact active inventory item was not zero.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> lease.acquisitionInventoryCounts(java.util.stream.IntStream.rangeClosed(1, 129).boxed().toList()), "129 active inventory IDs were admitted by the production backend.");
			PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> inventoryCounts.put(57, 1L), "Production active inventory snapshot was mutable.");
			final List<AcquisitionTargetSnapshot> targets = lease.acquisitionTargets(_acquisitionSource.npcId(), 8, 2000);
			PhantomAssertions.assertTrue(targets.stream().anyMatch(value -> value.objectId() == target.getObjectId()), "Exact authoritative acquisition target was not observed.");
			PhantomAssertions.assertTrue(lease.acquisitionTargets(_acquisitionSource.npcId() + 1, 8, 2000).stream().noneMatch(value -> value.objectId() == target.getObjectId()), "Wrong NPC identity was admitted.");
			PhantomAssertions.assertEquals(_acquisitionSource.spoilSkillLevel(), lease.knownSkillLevel(SPOIL_SKILL_ID), "Canonical known spoil skill was not observed.");
			PhantomAssertions.assertEquals(_acquisitionSource.sweepSkillLevel(), lease.knownSkillLevel(SWEEP_SKILL_ID), "Canonical known sweep skill was not observed.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID + 1, 1), AcquisitionSkillKind.SPOIL), "Unknown acquisition skill was admitted.");
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, lease.castAcquisition(target.getObjectId() + 1, new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Wrong exact target was admitted.");
			PhantomAssertions.assertEquals(StartStatus.REJECTED_EXISTING, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Existing Combat admitted a second owner during acquisition.");
			PhantomAssertions.assertEquals(inventoryBefore, _player.getInventory().getInventoryItemCount(57, -1), "Exact active inventory read mutated canonical inventory.");
		}

		final Monster wrongInstance = spawnNormalMonster(targetMaximumHp());
		final int wrongInstanceId = InstanceManager.getInstance().createDynamicInstance(0).getId();
		wrongInstance.setInstanceId(wrongInstanceId);
		final Monster distant = spawnAtDistance(targetMaximumHp(), 2101);
		try (ExternalActionLease lease = acquireAcquisition("acquisition-negative-targets"))
		{
			final AcquisitionTargetSnapshot instanceSnapshot = lease.acquisitionTargetSnapshot(wrongInstance.getObjectId());
			final AcquisitionTargetSnapshot distanceSnapshot = lease.acquisitionTargetSnapshot(distant.getObjectId());
			PhantomAssertions.assertTrue((instanceSnapshot != null) && !instanceSnapshot.liveValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Wrong instance was admitted.");
			PhantomAssertions.assertTrue((distanceSnapshot != null) && !distanceSnapshot.liveValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Out-of-range target was admitted.");
		}
		finally
		{
			wrongInstance.setInstanceId(0);
			InstanceManager.getInstance().destroyInstance(wrongInstanceId);
		}
	}

	private void testCanonicalAcquisitionChain() throws Exception
	{
		resetAcquisitionActor();
		final long before = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
		final DeterministicSpoilMonster target = spawnDeterministicSpoilMonster();
		try
		{
			try (ExternalActionLease lease = acquireAcquisition("acquisition-chain-spoil"))
			{
				final ActionOutcome spoil = lease.castAcquisition(target.getObjectId(), new SelectedSkill(_acquisitionSource.spoilSkillId(), _acquisitionSource.spoilSkillLevel()), AcquisitionSkillKind.SPOIL);
				PhantomAssertions.assertTrue((spoil == ActionOutcome.ISSUED) || (spoil == ActionOutcome.ALREADY_OWNED), "Canonical spoil cast was not issued.");
				await(() -> target.isSpoiled() && (target.getSpoilerObjectId() == _player.getObjectId()), "Canonical spoil was not observed on the exact Monster.");
			}
			resetActor(false);
			relocateToCombatPoint();
			_player.setInvul(true);
			ensureWeapon();
			target.restoreTemplateLevel();
			target.setCurrentHp(1);
			target.getStatus().stopHpMpRegeneration();
			_player.setPlayerClass(MELEE_CLASS_ID);

			final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Existing Combat did not accept the spoiled exact target.");
			awaitCombatOutcome(target, "Existing Combat");
			PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Existing Combat did not kill the spoiled exact target: " + _combat.find(_profile.profileId()).orElse(null));
			PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, awaitTerminal().result(), "Existing Combat did not publish canonical victory.");
			consumeTerminal();
			PhantomAssertions.assertTrue(target.isSweepActive(), "Canonical spoiled corpse did not expose sweep state.");
			prepareAcquisitionActor();

			try (ExternalActionLease lease = acquireAcquisition("acquisition-chain-sweep"))
			{
				final AcquisitionTargetSnapshot corpse = lease.acquisitionTargetSnapshot(target.getObjectId());
				PhantomAssertions.assertTrue((corpse != null) && corpse.sweepValidFor(lease.actorSnapshot(), _acquisitionSource.npcId(), 2000), "Exact spoiled corpse was not sweep-eligible.");
				final ActionOutcome sweep = lease.castAcquisition(target.getObjectId(), new SelectedSkill(_acquisitionSource.sweepSkillId(), _acquisitionSource.sweepSkillLevel()), AcquisitionSkillKind.SWEEP);
				PhantomAssertions.assertTrue((sweep == ActionOutcome.ISSUED) || (sweep == ActionOutcome.ALREADY_OWNED), "Canonical sweep cast was not issued.");
				await(() -> !target.isSweepActive(), "Canonical sweep did not consume the exact corpse loot state.");
			}
			final long after = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
			PhantomAssertions.assertTrue(after > before, "Canonical spoil/sweep produced no authoritative target-item inventory delta.");
		}
		finally
		{
			final long after = _player.getInventory().getInventoryItemCount(_acquisitionSource.itemId(), -1);
			destroyInventoryCount(_player, _acquisitionSource.itemId(), Math.max(0, after - before));
		}
	}

	private void testAcquisitionDispatchRecovery() throws Exception
	{
		resetAcquisitionActor();
		final DeterministicSpoilMonster target = spawnDeterministicSpoilMonster();
		try (ExternalActionLease lease = acquireAcquisition("acquisition-recovery-spoil-1"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Initial spoil dispatch was not issued.");
			await(() -> target.isSpoiled(), "Initial spoil dispatch did not become observable.");
		}
		try (ExternalActionLease recovered = acquireAcquisition("acquisition-recovery-spoil-2"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ALREADY_OWNED, recovered.castAcquisition(target.getObjectId(), new SelectedSkill(SPOIL_SKILL_ID, 11), AcquisitionSkillKind.SPOIL), "Observed spoil was blindly repeated after dispatch recovery.");
		}
		resetActor(false);
		relocateToCombatPoint();
		_player.setInvul(true);
		ensureWeapon();
		target.restoreTemplateLevel();
		target.setCurrentHp(1);
		target.getStatus().stopHpMpRegeneration();
		_player.setPlayerClass(MELEE_CLASS_ID);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Recovery fixture Combat start failed.");
		awaitCombatOutcome(target, "Recovery fixture Combat");
		PhantomAssertions.assertTrue(target.isDead() || target.isAlikeDead(), "Recovery fixture target did not die: " + _combat.find(_profile.profileId()).orElse(null));
		awaitTerminal();
		consumeTerminal();
		prepareAcquisitionActor();
		try (ExternalActionLease lease = acquireAcquisition("acquisition-recovery-sweep-1"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.castAcquisition(target.getObjectId(), new SelectedSkill(SWEEP_SKILL_ID, 1), AcquisitionSkillKind.SWEEP), "Initial sweep dispatch was not issued.");
			await(() -> !target.isSweepActive(), "Initial sweep dispatch did not become observable.");
		}
		try (ExternalActionLease recovered = acquireAcquisition("acquisition-recovery-sweep-2"))
		{
			PhantomAssertions.assertEquals(ActionOutcome.REJECTED, recovered.castAcquisition(target.getObjectId(), new SelectedSkill(SWEEP_SKILL_ID, 1), AcquisitionSkillKind.SWEEP), "Consumed sweep was blindly repeated after dispatch recovery.");
		}
	}

	private void testExactActorLease()
	{
		try (ActionLease lease = _materialization.tryAcquireAction(_profile.profileId()).orElseThrow())
		{
			PhantomAssertions.assertEquals(_player, lease.player(), "Materialization lease did not retain the exact actor.");
			PhantomAssertions.assertEquals(_player, World.getInstance().getPlayer(_player.getObjectId()), "Materialization actor is not the exact World Player.");
		}
	}

	private void testCanonicalCpSnapshot()
	{
		resetActor(true);
		final double firstCp = Math.max(1, Math.floor(_player.getMaxCp() / 3));
		final double secondCp = Math.min(_player.getMaxCp(), firstCp + 7);
		final double hp = _player.getCurrentHp();
		final double mp = _player.getCurrentMp();
		_player.setCurrentCp(firstCp);
		final ActorSnapshot first;
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			first = lease.actorSnapshot();
		}
		PhantomAssertions.assertTrue(Double.compare(first.currentCp(), _player.getCurrentCp()) == 0, "Combat snapshot current CP differs from canonical Player.");
		PhantomAssertions.assertTrue(Double.compare(first.maximumCp(), _player.getMaxCp()) == 0, "Combat snapshot maximum CP differs from canonical Player.");
		PhantomAssertions.assertTrue(Double.compare(first.currentHp(), hp) == 0 && Double.compare(first.currentMp(), mp) == 0, "HP, MP and CP were mixed in the combat snapshot.");
		PhantomAssertions.assertTrue(Double.compare(_player.getCurrentCp(), firstCp) == 0, "Combat snapshot mutated canonical Player CP.");
		_player.setCurrentCp(secondCp);
		final ActorSnapshot second;
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			second = lease.actorSnapshot();
		}
		PhantomAssertions.assertTrue(Double.compare(second.currentCp(), secondCp) == 0, "Next combat snapshot did not observe changed canonical CP.");
		PhantomAssertions.assertTrue(Double.compare(first.currentCp(), firstCp) == 0, "Immutable combat snapshot changed after canonical CP mutation.");
	}

	private void testQol009SourceCensus(PhantomTestContext context) throws Exception
	{
		final String requestAction = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestActionUse.java"), StandardCharsets.UTF_8);
		final String summon = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/model/actor/Summon.java"), StandardCharsets.UTF_8);
		final String summonAi = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/ai/SummonAI.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(requestAction.contains("case 22: // Attack (Servitors)") && requestAction.contains("summon.canAttack(_ctrlPressed)") && requestAction.contains("summon.doSummonAttack(target)"), "Stock human Servitor attack route drifted.");
		PhantomAssertions.assertTrue(summon.contains("GeoEngine.getInstance().canSeeTarget(this, target)") && !summon.contains("GeoEngine.getInstance().canSeeTarget(_owner, target)"), "Stock Servitor command stopped using summon-to-target visibility.");
		PhantomAssertions.assertTrue(summonAi.contains("PathFinding.getInstance().findPath(_actor.getX(), _actor.getY(), _actor.getZ(), target.getX(), target.getY(), target.getZ(), _actor.getInstanceId(), false)"), "SummonAI path authority is no longer summon-to-target.");

		for (int classId : List.of(14, 28, 41, 96, 104, 111))
		{
			PhantomAssertions.assertTrue(_progression.classFact(classId).summoner(), "Canonical summoner class fact is absent: " + classId);
			PhantomAssertions.assertTrue(_progression.summons(classId).stream().anyMatch(fact -> fact.actorKind() == ActorKind.SERVITOR), "Canonical summoner class has no Servitor fact: " + classId);
			PhantomAssertions.assertTrue(_progression.summons(classId).stream().noneMatch(fact -> (fact.actorKind() == ActorKind.PET) || (fact.actorKind() == ActorKind.BABY_PET)), "Summoner class mixed Pet/BabyPet into class-owned actor facts: " + classId);
		}
		PhantomAssertions.assertTrue(_progression.summons(WARLOCK_CLASS_ID).stream().anyMatch(fact -> (fact.skillId() == SUMMON_MEW_SKILL_ID) && (fact.skillLevel() == SUMMON_MEW_SKILL_LEVEL) && (fact.actorKind() == ActorKind.SERVITOR)), "Warlock Mew cat fact is absent.");
		PhantomAssertions.assertTrue(_progression.summons(ARCANA_LORD_CLASS_ID).stream().anyMatch(fact -> (fact.skillId() == SUMMON_FELINE_KING_SKILL_ID) && (fact.skillLevel() == SUMMON_FELINE_KING_SKILL_LEVEL) && (fact.actorKind() == ActorKind.SERVITOR)), "Arcana Lord Feline King fact is absent.");
		context.record("qol009.census", "stock=already_native;summoners=14,28,41,96,104,111;warlock=1225/18;arcana=1406/1");
	}

	private void testQol009WarlockControl() throws Exception
	{
		resetActor(true);
		final long oreBaseline = spiritOreCount(_player);
		try
		{
			final Monster first = spawnNormalMonster(targetMaximumHp());
			final Servitor cat = nativeSummon(_player, WARLOCK_CLASS_ID, SUMMON_MEW_SKILL_ID, SUMMON_MEW_SKILL_LEVEL, first, true);
			cat.setCurrentMp(0);
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				lease.attack(first.getObjectId());
				PhantomAssertions.assertTrue(cat.hasAI() && (cat.getAI().getIntention() == Intention.ATTACK) && (cat.getAI().getAttackTarget() == first) && (cat.getTarget() == first), "Warlock cat did not synchronize target A through stock attack AI.");
				final Object exactAi = cat.getAI();
				lease.attack(first.getObjectId());
				PhantomAssertions.assertTrue((cat.getAI() == exactAi) && (cat.getAI().getAttackTarget() == first), "Repeated target A pulse replaced or disturbed the existing cat attack intention.");

				final Monster second = spawnNormalMonster(targetMaximumHp());
				lease.attack(second.getObjectId());
				PhantomAssertions.assertTrue((cat.getAI().getIntention() == Intention.ATTACK) && (cat.getAI().getAttackTarget() == second) && (cat.getTarget() == second), "Warlock cat did not retarget from A to B.");
				lease.cancelOwnedAction(new PhantomOwnedAction(1, second.getObjectId(), null, 0));
				PhantomAssertions.assertTrue(cat.getFollowStatus() && (cat.getAI().getIntention() == Intention.FOLLOW) && (cat.getAI().getFollowTarget() == _player), "Combat cleanup did not return the cat to native owner follow.");
			}
		}
		finally
		{
			clearSummon(_player);
			restoreSpiritOre(_player, oreBaseline);
		}
	}

	private void testQol009OwnerLosesLos(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		final long oreBaseline = spiritOreCount(_player);
		final int ownerX = _player.getX();
		final int ownerY = _player.getY();
		final int ownerZ = _player.getZ();
		try
		{
			final Monster target = spawnNormalMonster(targetMaximumHp());
			final Servitor cat = nativeSummon(_player, WARLOCK_CLASS_ID, SUMMON_MEW_SKILL_ID, SUMMON_MEW_SKILL_LEVEL, target);
			cat.setCurrentMp(0);
			cat.cancelAction();
			_player.setXYZInvisible(ownerX, ownerY, ownerZ + 2000);
			final boolean ownerStillVisibleInLoadedGeodata = GeoEngine.getInstance().canSeeTarget(_player, target);
			PhantomAssertions.assertTrue(GeoEngine.getInstance().canSeeTarget(cat, target), "Cat-to-target route was not directly reachable in the positive fixture.");
			if (GeoEngineConfig.PATHFINDING > 0)
			{
				PhantomAssertions.assertTrue(PathFinding.getInstance().findPath(cat.getX(), cat.getY(), cat.getZ(), target.getX(), target.getY(), target.getZ(), cat.getInstanceId(), false) != null, "Stock summon-to-target pathfinder rejected the reachable fixture.");
			}
			_player.setTarget(target);
			PhantomAssertions.assertTrue(cat.canAttack(false), "Stock human command rejected a reachable cat target only because owner LoS was lost.");
			cat.doSummonAttack(target);
			PhantomAssertions.assertTrue((cat.getAI().getIntention() == Intention.ATTACK) && (cat.getAI().getAttackTarget() == target), "Owner LoS loss cleared stock cat ATTACK.");
			context.record("qol009.ownerLosPath", "sourceHasNoOwnerLos=true;loadedGeodataOwnerStillVisible=" + ownerStillVisibleInLoadedGeodata + ";servitorLos=true;servitorPath=true");

			final Monster unreachable = spawnNormalMonster(targetMaximumHp());
			unreachable.setXYZInvisible(cat.getX() + 20, cat.getY(), cat.getZ() + 5000);
			cat.cancelAction();
			_player.setTarget(unreachable);
			PhantomAssertions.assertFalse(cat.canAttack(false), "Unreachable cat target bypassed native visibility/geodata checks.");
			PhantomAssertions.assertFalse((cat.getAI().getIntention() == Intention.ATTACK) && (cat.getAI().getAttackTarget() == unreachable), "Unreachable route fabricated a cat ATTACK intention.");
		}
		finally
		{
			_player.setXYZInvisible(ownerX, ownerY, ownerZ);
			clearSummon(_player);
			restoreSpiritOre(_player, oreBaseline);
		}
	}

	private void testQol009ResummonGuards() throws Exception
	{
		resetActor(true);
		final long oreBaseline = spiritOreCount(_player);
		final int teleportProtectionBaseline = PlayerConfig.PLAYER_TELEPORT_PROTECTION;
		final Monster target = spawnNormalMonster(targetMaximumHp());
		String stage = "setup";
		try
		{
			stage = "missing-reagent";
			prepareSummoner(_player, WARLOCK_CLASS_ID, SUMMON_MEW_SKILL_ID, SUMMON_MEW_SKILL_LEVEL);
			restoreSpiritOre(_player, 0);
			final Skill summonSkill = _player.getKnownSkill(SUMMON_MEW_SKILL_ID);
			PhantomAssertions.assertFalse(_player.checkDoCastConditions(summonSkill), "Native missing-reagent guard unexpectedly admitted the summon skill.");
			PhantomAssertions.assertTrue(_player.getSummon() == null, "Missing Spirit Ore produced a free Servitor.");
			PhantomAssertions.assertEquals(0L, spiritOreCount(_player), "Missing-reagent path fabricated Spirit Ore.");

			stage = "zero-mp";
			addSpiritOre(_player, 20);
			_player.setCurrentMp(0);
			PhantomAssertions.assertFalse(_player.checkDoCastConditions(summonSkill), "Native zero-MP guard unexpectedly admitted the summon skill.");
			PhantomAssertions.assertTrue(_player.getSummon() == null, "Zero MP produced a free Servitor.");
			_player.setCurrentMp(_player.getMaxMp());

			stage = "active-reuse";
			_player.disableSkill(summonSkill, 60_000);
			PhantomAssertions.assertFalse(_player.checkDoCastConditions(summonSkill), "Native active-reuse guard unexpectedly admitted the summon skill.");
			PhantomAssertions.assertTrue(_player.getSummon() == null, "Active reuse produced a duplicate Servitor.");
			stage = "active-reuse-enable";
			_player.enableSkill(summonSkill);

			stage = "teleport-protection";
			final long oreBeforeProtection = spiritOreCount(_player);
			_player.abortCast();
			_player.setCurrentSkill(null, false, false);
			_player.getAI().setIntention(Intention.IDLE);
			PlayerConfig.PLAYER_TELEPORT_PROTECTION = Math.max(1, teleportProtectionBaseline);
			_player.setTeleportProtection(true);
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				lease.attack(target.getObjectId());
			}
			PhantomAssertions.assertTrue(_player.getSummon() == null, "Illegal teleport-protected state bypassed summon conditions.");
			PhantomAssertions.assertEquals(oreBeforeProtection, spiritOreCount(_player), "Teleport-protected resummon consumed a reagent.");
			PhantomAssertions.assertFalse(_player.isCastingNow() && (_player.getCurrentSkill() != null) && (_player.getCurrentSkill().getSkillId() == SUMMON_MEW_SKILL_ID), "Teleport-protected resummon entered native CAST ownership.");
			_player.setTeleportProtection(false);

			stage = "legal-resummon";
			final Servitor legal = nativeSummon(_player, WARLOCK_CLASS_ID, SUMMON_MEW_SKILL_ID, SUMMON_MEW_SKILL_LEVEL, target);
			final int exactObjectId = legal.getObjectId();
			final long oreBeforeExisting = spiritOreCount(_player);
			stage = "existing-servitor";
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				lease.attack(target.getObjectId());
			}
			PhantomAssertions.assertTrue((_player.getSummon() == legal) && (legal.getObjectId() == exactObjectId), "Existing live Servitor was replaced or duplicated.");
			PhantomAssertions.assertEquals(oreBeforeExisting, spiritOreCount(_player), "Existing live Servitor triggered another summon debit.");
		}
		catch (Throwable throwable)
		{
			throw new AssertionError("QOL-009 resummon guard stage failed: " + stage + ": " + throwable, throwable);
		}
		finally
		{
			_player.setTeleportProtection(false);
			PlayerConfig.PLAYER_TELEPORT_PROTECTION = teleportProtectionBaseline;
			clearSummon(_player);
			restoreSpiritOre(_player, oreBaseline);
		}
	}

	private void testQol009ArcanaActiveSkill() throws Exception
	{
		resetActor(true);
		final long oreBaseline = spiritOreCount(_player);
		try
		{
			final Monster target = spawnNormalMonster(targetMaximumHp());
			final Servitor king = nativeSummon(_player, ARCANA_LORD_CLASS_ID, SUMMON_FELINE_KING_SKILL_ID, SUMMON_FELINE_KING_SKILL_LEVEL, target);
			positionAtReachableServitorPath(king, target);
			final Skill slash = SkillData.getInstance().getSkill(5135, 1);
			PhantomAssertions.assertTrue((slash != null) && king.getTemplate().getParameters().getSet().values().stream().anyMatch(value -> (value instanceof org.l2jmobius.gameserver.model.skill.holders.SkillHolder holder) && (holder.getSkillId() == 5135)), "Feline King Slash was not discovered from canonical live NPC parameters.");
			_player.setCurrentPetSkill(null, false, false);
			final double mpBeforeSlash = king.getCurrentMp();
			final double hpBeforeSlash = target.getCurrentHp();
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				final ActionOutcome attackOutcome = lease.attack(target.getObjectId());
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, attackOutcome, "Feline King combat target did not enter the canonical actor attack route: actor=" + lease.actorSnapshot() + ", target=" + lease.targetSnapshot(target.getObjectId()) + ", spawned=" + target.isSpawned() + ", worldExact=" + (World.getInstance().findObject(target.getObjectId()) == target));
				final boolean nativeSlashObserved = waitFor(() -> (_player.getCurrentPetSkill() != null) && (_player.getCurrentPetSkill().getSkillId() == 5135) && (((king.getAI().getIntention() == Intention.CAST) && (king.getAI().getCastTarget() == target)) || (king.getCurrentMp() < mpBeforeSlash) || king.isSkillDisabled(slash) || (target.getCurrentHp() < hpBeforeSlash)), WAIT_MILLIS);
				PhantomAssertions.assertTrue(nativeSlashObserved, "Legal Feline King Slash produced no native CAST, resource, reuse, or damage signal: mp=" + king.getCurrentMp() + '/' + king.getMaxMp() + ", required=" + (king.getStat().getMpConsume(slash) + king.getStat().getMpInitialConsume(slash)) + ", distance=" + king.calculateDistance2D(target) + ", target=" + king.getTarget() + ", intention=" + king.getAI().getIntention() + ", castTarget=" + king.getAI().getCastTarget() + ", currentPetSkill=" + _player.getCurrentPetSkill() + ", disabled=" + king.isSkillDisabled(slash) + ", damage=" + slash.isDamage() + ", negative=" + slash.hasNegativeEffect());
				king.abortCast();
				king.getAI().setIntention(Intention.ACTIVE);
				lease.attack(target.getObjectId());
				PhantomAssertions.assertTrue(waitFor(() -> (king.getAI().getIntention() == Intention.ATTACK) && (king.getAI().getAttackTarget() == target), WAIT_MILLIS), "Active-skill anti-spam did not fall back to stock cat attack.");
			}

			king.abortAttack();
			king.getAI().setIntention(Intention.ACTIVE);
			king.setCurrentMp(0);
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				lease.attack(target.getObjectId());
			}
			PhantomAssertions.assertTrue(waitFor(() -> (king.getAI().getIntention() == Intention.ATTACK) && (king.getAI().getAttackTarget() == target) && !king.isCastingNow(), WAIT_MILLIS), "Insufficient Servitor MP bypassed native skill resource checks.");

			king.abortAttack();
			king.getAI().setIntention(Intention.ACTIVE);
			king.setCurrentMp(king.getMaxMp());
			king.disableSkill(slash, 60_000);
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				lease.attack(target.getObjectId());
			}
			PhantomAssertions.assertTrue(waitFor(() -> (king.getAI().getIntention() == Intention.ATTACK) && (king.getAI().getAttackTarget() == target) && !king.isCastingNow(), WAIT_MILLIS), "Servitor skill reuse was bypassed.");
			king.enableSkill(slash);
		}
		finally
		{
			clearSummon(_player);
			restoreSpiritOre(_player, oreBaseline);
		}
	}

	private void testQol009Exclusions(PhantomTestContext context) throws Exception
	{
		final String backend = Files.readString(_moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(backend.contains("controlled instanceof Servitor servitor") && !backend.contains("instanceof BabyPet") && !backend.contains("instanceof Pet pet"), "QOL-009 controller does not remain true-Servitor-only.");
		final ActorSnapshot actor = pvpActor();
		final PvpTargetSnapshot owner = pvpOwner(false, true);
		PhantomAssertions.assertEquals(owner.objectId(), linked(false, false, 0, false, false, true, true, 1).preferredTargetObjectId(actor, owner, 2000), "Pet-shaped candidate became a tactical Servitor target.");
		PhantomAssertions.assertEquals(owner.objectId(), linked(true, true, 0, false, false, true, true, 1).preferredTargetObjectId(actor, owner, 2000), "Dead Servitor became a tactical target.");
		PhantomAssertions.assertEquals(owner.objectId(), linked(true, false, 1, false, false, true, true, 1).preferredTargetObjectId(actor, owner, 2000), "Wrong-instance Servitor became a tactical target.");
		PhantomAssertions.assertEquals(owner.objectId(), linked(true, false, 0, true, false, true, true, 1).preferredTargetObjectId(actor, owner, 2000), "Same-party Servitor became a tactical target.");
		PhantomAssertions.assertEquals(owner.objectId(), linked(true, false, 0, false, true, true, true, 1).preferredTargetObjectId(actor, owner, 2000), "Illegal-context Servitor became a tactical target.");
		PhantomAssertions.assertEquals(owner.objectId(), linked(true, false, 0, false, false, true, true, 1, false).preferredTargetObjectId(actor, owner, 2000), "Non-attackable Servitor became a tactical target.");
		context.record("qol009.exclusions", "pet=false;babyPet=false;dead=false;wrongInstance=false;sameParty=false;illegalContext=false;attackable=false");
	}

	private void testQol009PvpPolicy()
	{
		final ActorSnapshot actor = pvpActor();
		final PvpTargetSnapshot neutralOwner = pvpOwner(false, true);
		PhantomAssertions.assertEquals(neutralOwner.objectId(), linked(true, false, 0, false, false, false, false, 4).preferredTargetObjectId(actor, neutralOwner, 2000), "Neutral owner lost to a non-threatening low-impact Servitor.");
		PhantomAssertions.assertEquals(901, linked(true, false, 0, false, false, true, false, 4).preferredTargetObjectId(actor, neutralOwner, 2000), "Directly threatening Servitor did not win tactical selection.");
		PhantomAssertions.assertEquals(901, linked(true, false, 0, false, false, false, true, 1).preferredTargetObjectId(actor, neutralOwner, 2000), "High-impact easy-removal Servitor did not win tactical selection.");
		PhantomAssertions.assertEquals(901, linked(true, false, 0, false, false, false, false, 4).preferredTargetObjectId(actor, pvpOwner(true, true), 2000), "Invulnerable owner did not allow a legal Servitor preference.");
		PhantomAssertions.assertEquals(901, linked(true, false, 0, false, false, false, false, 4).preferredTargetObjectId(actor, pvpOwner(false, false), 2000), "Unreachable owner did not allow a legal Servitor preference.");
	}

	private void testQol009LivePvpAndCleanup() throws Exception
	{
		resetActor(true);
		clearSummon(_player);
		final Player owner = preparePvpTarget();
		final long oreBaseline = spiritOreCount(owner);
		try
		{
			clearSummon(owner);
			final Monster summonAnchor = spawnNormalMonster(targetMaximumHp());
			final Servitor enemyServitor = nativeSummon(owner, ARCANA_LORD_CLASS_ID, SUMMON_FELINE_KING_SKILL_ID, SUMMON_FELINE_KING_SKILL_LEVEL, summonAnchor);
			owner.setTarget(_player);
			enemyServitor.doSummonAttack(_player);
			PhantomAssertions.assertTrue((enemyServitor.getAI().getIntention() == Intention.ATTACK) && (enemyServitor.getAI().getAttackTarget() == _player), "Stock enemy Feline King command did not establish the direct-threat fixture.");
			final int karmaBefore = _player.getKarma();
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				final PvpTargetSnapshot ownerSnapshot = lease.pvpTargetSnapshot(owner.getObjectId());
				final PvpLinkedServitorSnapshot linked = lease.pvpLinkedServitorSnapshot(owner.getObjectId());
				final PvpConsequenceSnapshot consequences = lease.pvpConsequences(owner.getObjectId());
				PhantomAssertions.assertTrue((ownerSnapshot != null) && (linked != null) && (linked.ownerObjectId() == owner.getObjectId()) && linked.directThreat(), "Live enemy Servitor was not observed as an exact linked owner subtarget.");
				PhantomAssertions.assertEquals(enemyServitor.getObjectId(), lease.pvpTacticalTargetObjectId(owner.getObjectId(), 2000), "Live direct-threat Servitor did not win tactical selection.");
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.attackPvp(owner.getObjectId(), enemyServitor.getObjectId(), PVP_AUTHORITY_HASH), "Canonical PvP route did not admit the linked Servitor target.");
				PhantomAssertions.assertTrue((_player.getAI().getAttackTarget() == enemyServitor) && (_player.getTarget() == enemyServitor), "Player attack did not route to the exact linked Servitor.");
				PhantomAssertions.assertEquals(consequences, lease.pvpConsequences(owner.getObjectId()), "Linked tactical target replaced canonical owner consequence context.");
				lease.cancelOwnedAction(new PhantomOwnedAction(1, owner.getObjectId(), null, 0));
				PhantomAssertions.assertTrue((_player.getAI().getIntention() == Intention.IDLE) && (_player.getTarget() == null), "Linked Servitor PvP action survived owned cleanup.");
			}
			PhantomAssertions.assertEquals(karmaBefore, _player.getKarma(), "QOL-009 manually mutated Player karma.");
		}
		finally
		{
			clearSummon(owner);
			restoreSpiritOre(owner, oreBaseline);
			resetActor(true);
		}
	}

	private Servitor nativeSummon(Player owner, int classId, int skillId, int skillLevel, Monster target) throws Exception
	{
		return nativeSummon(owner, classId, skillId, skillLevel, target, false);
	}

	private Servitor nativeSummon(Player owner, int classId, int skillId, int skillLevel, Monster target, boolean requireNativeDebit) throws Exception
	{
		clearSummon(owner);
		prepareSummoner(owner, classId, skillId, skillLevel);
		addSpiritOre(owner, 20);
		final Skill summonSkill = owner.getKnownSkill(skillId);
		final long oreBeforeAttempt = spiritOreCount(owner);
		final double mpBeforeAttempt = owner.getCurrentMp();
		if (owner == _player)
		{
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				PhantomAssertions.assertEquals(ActionOutcome.UNAVAILABLE, lease.attack(target.getObjectId()), "Combat lease did not pause owner action for a native resummon attempt.");
			}
		}
		else
		{
			PhantomAssertions.assertTrue(owner.useMagic(summonSkill, false, false), "Native summon attempt was rejected.");
		}
		PhantomAssertions.assertTrue(owner.isCastingNow() && (owner.getCurrentSkill() != null) && (owner.getCurrentSkill().getSkillId() == skillId), "Canonical native summon attempt did not own the exact CAST/currentSkill state.");
		if (requireNativeDebit)
		{
			final boolean nativeDebitObserved = waitFor(() -> (spiritOreCount(owner) == (oreBeforeAttempt - summonSkill.getItemConsumeCount())) && (owner.getCurrentMp() < mpBeforeAttempt) && owner.isSkillDisabled(summonSkill), WAIT_MILLIS);
			PhantomAssertions.assertTrue(nativeDebitObserved, "Canonical summon attempt did not establish exact native reagent/MP/reuse ownership: oreBefore=" + oreBeforeAttempt + ", oreAfter=" + spiritOreCount(owner) + ", itemConsume=" + summonSkill.getItemConsumeCount() + ", mpBefore=" + mpBeforeAttempt + ", mpAfter=" + owner.getCurrentMp() + ", disabled=" + owner.isSkillDisabled(summonSkill));
		}
		PhantomAssertions.assertTrue(summonSkill.hasEffects(EffectScope.GENERAL), "Canonical summon skill has no loaded native Summon effect.");
		owner.abortCast();
		owner.getAI().setIntention(Intention.IDLE);
		owner.setCurrentSkill(null, false, false);
		summonSkill.activateSkill(owner, List.of(owner));
		PhantomAssertions.assertTrue(owner.getSummon() instanceof Servitor, "Native Summon effect did not materialize a true Servitor test fixture.");
		final Servitor servitor = (Servitor) owner.getSummon();
		PhantomAssertions.assertTrue((servitor.getOwner() == owner) && servitor.isSpawned() && (servitor.getInstanceId() == owner.getInstanceId()), "Native Servitor ownership/live identity is invalid.");
		return servitor;
	}

	private void prepareSummoner(Player owner, int classId, int skillId, int skillLevel)
	{
		owner.abortAttack();
		owner.abortCast();
		owner.setTarget(null);
		owner.getAI().setIntention(Intention.IDLE);
		owner.removeSkill(SUMMON_MEW_SKILL_ID, false);
		owner.removeSkill(SUMMON_FELINE_KING_SKILL_ID, false);
		owner.setPlayerClass(classId);
		owner.getStat().setLevel((byte) 85);
		owner.setSpawnProtection(false);
		owner.setTeleportProtection(false);
		owner.setCurrentHp(owner.getMaxHp());
		owner.setCurrentMp(owner.getMaxMp());
		final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
		PhantomAssertions.assertTrue(skill != null, "Canonical summon skill is unavailable: " + skillId + '/' + skillLevel);
		owner.addSkill(skill, false);
		owner.enableSkill(skill);
	}

	private static void positionAtReachableServitorPath(Servitor servitor, Monster target)
	{
		if (GeoEngineConfig.PATHFINDING <= 0)
		{
			return;
		}
		for (int offset = 20; offset <= 200; offset += 20)
		{
			if (target.isSpawned())
			{
				target.decayMe();
			}
			target.setXYZInvisible(servitor.getX() + offset, servitor.getY(), servitor.getZ());
			target.spawnMe();
			target.revalidateZone(true);
			if (PathFinding.getInstance().findPath(servitor.getX(), servitor.getY(), servitor.getZ(), target.getX(), target.getY(), target.getZ(), servitor.getInstanceId(), false) != null)
			{
				return;
			}
		}
		throw new AssertionError("No native-reachable Feline King active-skill fixture was found within 200 units.");
	}

	private static void clearSummon(Player owner)
	{
		final Summon summon = owner == null ? null : owner.getSummon();
		if (summon != null)
		{
			summon.unSummon(owner);
		}
	}

	private static long spiritOreCount(Player owner)
	{
		return owner.getInventory().getInventoryItemCount(SPIRIT_ORE_ITEM_ID, -1);
	}

	private static void addSpiritOre(Player owner, long count)
	{
		if (count > 0)
		{
			PhantomAssertions.assertTrue(owner.getInventory().addItem(ItemProcessType.REWARD, SPIRIT_ORE_ITEM_ID, count, owner, PhantomCombatServerIntegrationSuite.class) != null, "Could not add test-owned Spirit Ore.");
		}
	}

	private static void restoreSpiritOre(Player owner, long baseline)
	{
		final long current = spiritOreCount(owner);
		if (current > baseline)
		{
			destroyInventoryCount(owner, SPIRIT_ORE_ITEM_ID, current - baseline);
		}
		else if (current < baseline)
		{
			addSpiritOre(owner, baseline - current);
		}
	}

	private static ActorSnapshot pvpActor()
	{
		return new ActorSnapshot(800, 88, 0, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
	}

	private static PvpTargetSnapshot pvpOwner(boolean invulnerable, boolean reachable)
	{
		return new PvpTargetSnapshot(900, 96, 0, 85, 4, 4, 100, true, true, true, false, false, false, true, false, false, false, false, false, false, false, false, false, false, true, invulnerable, reachable);
	}

	private static PvpLinkedServitorSnapshot linked(boolean trueServitor, boolean dead, int instanceId, boolean sameParty, boolean incompatibleContext, boolean directThreat, boolean highImpact, int hpBand)
	{
		return linked(trueServitor, dead, instanceId, sameParty, incompatibleContext, directThreat, highImpact, hpBand, true);
	}

	private static PvpLinkedServitorSnapshot linked(boolean trueServitor, boolean dead, int instanceId, boolean sameParty, boolean incompatibleContext, boolean directThreat, boolean highImpact, int hpBand, boolean attackable)
	{
		return new PvpLinkedServitorSnapshot(901, 900, instanceId, 80, hpBand, 100, trueServitor, true, false, dead, dead, false, true, true, false, sameParty, false, incompatibleContext, attackable, directThreat, highImpact);
	}

	private void testNoPvpZone058(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		final Player target = ensureObserver();
		int offset = 0;
		for (Player actor : List.of(_player, target)) { actor.decayMe(); actor.setXYZInvisible(-71432 + offset, 258512, -3104); actor.spawnMe(); actor.revalidateZone(true); offset += 30; }
		PhantomAssertions.assertTrue(_player.isInsideZone(ZoneId.NO_PVP) && !_player.isInsideZone(ZoneId.PEACE) && target.isInsidePeaceZone(_player), "Declared stock fighter_start_peace_1 negative input is not native NO_PVP-only.");
		try
		{
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startPvpSession(pvpRequest(target, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.MELEE_PHYSICAL, false)).status(), "NO_PVP-only native restriction acquired enemy combat ownership.");
		}
		finally { _combat.cancel(_profile.profileId()); consumeTerminal(); }
		context.record("pvp058.noPvp", "actual stock fighter_start_peace_1; NO_PVP=true/PEACE=false; canonical isInsidePeaceZone=true; enemy ownership rejected; no zone/config mutation");
	}

	private void preparePvpDefender058(Player actor)
	{
		prepareCompanionCombat058(actor, 5, 2500, 0, 6373, 6374, 6375, 6376, 6378, 920, 858, 858, 889, 889);
		for (var learn : _progression.classSkillLearns(5).stream().filter(value -> value.minimumCharacterLevel() <= 80).sorted(Comparator.comparingInt(value -> value.skillLevel())).toList())
		{
			final Skill skill = SkillData.getInstance().getSkill(learn.skillId(), learn.skillLevel());
			if ((skill != null) && (actor.getSkillLevel(learn.skillId()) < learn.skillLevel())) { actor.addSkill(skill, true); }
		}
		actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
	}

	private void testCurrentPvp058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true);
		final Player opponent = ensureObserver();
		prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		_player.addSkill(SkillData.getInstance().getSkill(1339, 101), true);
		preparePvpDefender058(opponent);
		// Ordered candidates outside the stock starting protection polygons; choose before enrollment by actual native geo/zone authority.
		Location input = null;
		final List<Location> inputs = new ArrayList<>(List.of(new Location(-71432, 254000, -3104), new Location(-76000, 254000, -3104), new Location(-78000, 254000, -3104), new Location(-80000, 254000, -3104)));
		for (int dx : new int[] { -6000, -4000, -2000, 0, 2000, 4000, 6000 }) { for (int dy : new int[] { -6000, -4000, -2000, 0, 2000, 4000, 6000 }) { inputs.add(new Location(145214 + dx, -1914 + dy, -4592)); } }
		for (Location candidate : inputs)
		{
			final int x = candidate.getX(); final int y = candidate.getY(); final int z = GeoEngine.getInstance().getHeight(x, y, candidate.getZ());
			final int targetZ = GeoEngine.getInstance().getHeight(x + 30, y, z);
			if (!GeoEngine.getInstance().hasGeo(x, y) || (ZoneManager.getInstance().getZone(x, y, z, NoPvPZone.class) != null) || (ZoneManager.getInstance().getZone(x, y, z, PeaceZone.class) != null) || (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) || !GeoEngine.getInstance().canMoveToTarget(x, y, z, x + 30, y, targetZ, 0)) { continue; }
			final var region = World.getInstance().getRegion(x, y, z);
			if (region == null) { continue; }
			boolean occupied = false;
			for (var nearby : region.getSurroundingRegions())
			{
				for (var object : nearby.getVisibleObjects())
				{
					if ((object instanceof Monster monster) && !monster.isDead() && (monster.getInstanceId() == 0) && (Math.abs((long) monster.getZ() - z) < 500) && (Math.hypot((long) monster.getX() - x, (long) monster.getY() - y) < 1800)) { occupied = true; break; }
				}
				if (occupied) { break; }
			}
			if (!occupied) { input = new Location(x, y, z); break; }
		}
		PhantomAssertions.assertTrue(input != null, "No declared native legal PvP input point; episode not enrolled.");
		int inputOffset = 0;
		for (Player actor : List.of(_player, opponent))
		{
			actor.setInvul(false); actor.decayMe(); actor.setXYZInvisible(input.getX() + inputOffset, input.getY(), input.getZ()); actor.spawnMe(); actor.revalidateZone(true); inputOffset += 30;
			PhantomAssertions.assertTrue(!actor.isGM() && !actor.isInsideZone(ZoneId.PEACE), "Native PvP input is privileged or in peace.");
		}
		PhantomAssertions.assertTrue(!opponent.isInsidePeaceZone(_player) && !_player.isInsidePeaceZone(opponent), "Native full PvP zone restriction rejects the declared positive input.");
		context.record("pvp058.inputPoint", input);
		final boolean antiFeed = PvpConfig.ANTIFEED_ENABLE;
		final double actorPool = _player.getCurrentHp() + _player.getCurrentCp();
		final double opponentPool = opponent.getCurrentHp() + opponent.getCurrentCp();
		final double mp = _player.getCurrentMp();
		context.record("pvp058.input", "TEST only native Mage94/80 learned1339:101/Sstaff+robe versus Knight5/80/Sweapon+Sheavy/full Tateossian920+858x2+889x2/native class-tree<=80; all declared before measurement; actual opponent attack precedes retaliation; no forceUse, grants, HP/MP refill or AntiFeed change during episode;30s combat +35s natural fact expiry bound; partial M4");
		PhantomAssertions.assertTrue(GeoEngine.getInstance().canSeeTarget(opponent, _player), "Declared native PvP input has no LOS.");
		opponent.setTarget(_player); // Same ordinary selection seam used by the existing canonical combat backend.
		_player.onForcedAttack(opponent);
		try { await(() -> (_player.getCurrentHp() + _player.getCurrentCp()) < actorPool, "Declared opponent did not actually damage the managed Mage."); }
		catch (AssertionError error) { throw new AssertionError("Native attacker input: intention=" + opponent.getAI().getIntention() + "; attacking=" + opponent.isAttackingNow() + "; attackTarget=" + opponent.getAI().getAttackTarget() + "; selected=" + opponent.getTarget() + "; online=" + opponent.isOnline() + "; actorInvul=" + _player.isInvul() + "; actorPool=" + actorPool + "->" + (_player.getCurrentHp() + _player.getCurrentCp()), error); }
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.observedPlayerAttackers(_player.getObjectId(), 16).stream().anyMatch(attacker -> attacker.targetObjectId() == opponent.getObjectId()), "Retaliation has no actual native Player attacker evidence.");
			PhantomAssertions.assertEquals(101, lease.knownSkillLevel(1339), "Native enhanced rank differs from prepared input.");
		}
		final var observed = _combat.observePvp(_profile.profileId(), List.of(opponent.getObjectId()), 16, 16).orElseThrow();
		PhantomAssertions.assertTrue(observed.targets().stream().anyMatch(value -> value.actualAttacker() && value.canonicalContextAllowed() && (value.target().objectId() == opponent.getObjectId())), "Current production PvP observation did not confirm the exact actual attacker.");
		final String authority = org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.sha256("pvp.actual.attack", _player.getObjectId(), opponent.getObjectId(), _player.getInstanceId());
		final var retaliation = new PhantomPvpCombatRequest(_profile.profileId(), opponent.getObjectId(), org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, authority, PhantomCombatMode.RANGED_MAGIC, false, false, 1, 30_000, () -> false);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startPvpSession(retaliation).status(), "Native enhanced retaliation was rejected.");
		final boolean[] observedEnhanced = { false };
		final boolean[] observedWalk = { false };
		final int[] previous = { _player.getX(), _player.getY() }; final double[] maximumStep = { 0 }, maximumDistance = { _player.calculateDistance3D(opponent) };
		PhantomAssertions.assertTrue(waitFor(() ->
		{
			if (_player.isMoving() && (_player.getAI().getIntention() == Intention.MOVE_TO)) { observedWalk[0] = true; }
			maximumStep[0] = Math.max(maximumStep[0], Math.hypot((long) _player.getX() - previous[0], (long) _player.getY() - previous[1])); previous[0] = _player.getX(); previous[1] = _player.getY();
			maximumDistance[0] = Math.max(maximumDistance[0], _player.calculateDistance3D(opponent));
			if ((_player.getCurrentSkill() != null) && (_player.getCurrentSkill().getSkillId() == 1339) && (_player.getCurrentSkill().getSkillLevel() == 101)) { observedEnhanced[0] = true; }
			return observedEnhanced[0] && ((_player.getCurrentMp() + 1) < mp) && ((_player.getCurrentHp() > 0) && ((opponent.getCurrentHp() + opponent.getCurrentCp() + 10) < opponentPool));
		}, 35_000), "Actual native PvP enhanced cast produced no completed damage/cost after bounded spacing.");
		context.record("pvp058.spacing", "native MOVE_TO=" + observedWalk[0] + "; maximumSampledStep=" + maximumStep[0] + "; maximumDistance=" + maximumDistance[0] + "; original30s session/four attempts<=5s each unchanged");
		PhantomAssertions.assertTrue(observedWalk[0] && maximumStep[0] < 100, "Native ranged PvP did not walk before casting or teleported during spacing.");
		context.record("pvp058.actual", "native1339:101=true; opponentHP+CP=" + opponentPool + "->" + (opponent.getCurrentHp() + opponent.getCurrentCp()) + "; MP=" + mp + "->" + _player.getCurrentMp() + "; opponentFlag=" + opponent.getPvpFlag() + "; actorFlag=" + _player.getPvpFlag() + "; actualCause=true; AntiFeedUnchanged=" + (antiFeed == PvpConfig.ANTIFEED_ENABLE));
		PhantomAssertions.assertTrue(opponent.recentPlayerAttackers(16).contains(_player.getObjectId()), "Ordinary PlayerStatus did not retain its actual incoming managed Player damage fact.");
		_combat.cancel(_profile.profileId());
		await(() -> _combat.find(_profile.profileId()).map(value -> value.result().terminal()).orElse(true), "Native PvP stop did not terminate.");
		consumeTerminal();
		opponent.abortAttack(); opponent.getAI().setIntention(Intention.IDLE); // Declared episode ended; quiesce owned TEST opponent.
		PhantomAssertions.assertEquals(antiFeed, PvpConfig.ANTIFEED_ENABLE, "PvP helper changed AntiFeed rules.");
		// Separate native Party fixture precondition, not invitation/positive combat evidence.
		final var party = new org.l2jmobius.gameserver.model.groups.Party(_player, PartyDistributionType.FINDERS_KEEPERS);
		_player.setParty(party); party.addPartyMember(opponent); opponent.setParty(party);
		try
		{
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startPvpSession(pvpRequest(opponent, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.RANGED_MAGIC, false)).status(), "Native same-party Player acquired enemy ownership.");
		}
		finally { PartyInvitationService.getInstance().leave(opponent); PartyInvitationService.getInstance().leave(_player); }
		PhantomAssertions.assertTrue(waitFor(() -> _player.recentPlayerAttackers(32).isEmpty() && opponent.recentPlayerAttackers(32).isEmpty(), 35_000), "Actual Player attacker facts did not expire naturally within the declared35s bound.");
		// Separate negative fixture boundary after the completed fight and natural TTL. No positive episode rescue.
		opponent.setInvul(true);
		final double protectedPool = opponent.getCurrentHp() + opponent.getCurrentCp();
		try
		{
			opponent.reduceCurrentHp(100, _player, null);
			PhantomAssertions.assertTrue((opponent.getCurrentHp() + opponent.getCurrentCp()) >= protectedPool, "Rejected native invulnerable damage reduced HP/CP.");
			PhantomAssertions.assertTrue(opponent.recentPlayerAttackers(32).isEmpty(), "Rejected native damage fabricated an actual-attacker fact.");
		}
		finally { opponent.setInvul(false); }
		PhantomAssertions.assertTrue(!_player.isDead() && !opponent.isDead(), "The declared isolated PvP episode lost a TEST participant; no rescue or silent exclusion is allowed.");
		context.record("pvp058.damageFacts", "both native PlayerStatus directions observed; natural30sTTL expires; rejected invulnerable damage creates no fact; ID-only/cap32; no timer or World scan");
	}

	private void testPartyDefense058(PhantomTestContext context) throws Exception
	{
		final Player human = ensureObserver();
		PhantomAssertions.assertTrue(!_player.isDead() && !human.isDead(), "Previous enrolled native actors are unavailable; no rescue is allowed.");
		final var fixture = _environment.createCampaign058Companion(context, 1);
		final Player enemy = Player.load(fixture.objectId());
		PhantomAssertions.assertTrue(enemy != null, "Owned third TEST actor did not load.");
		preparePvpDefender058(enemy);
		enemy.setInvul(false); enemy.setXYZInvisible(_player.getX() + 30, _player.getY() + 30, _player.getZ()); enemy.spawnMe(); enemy.revalidateZone(true);
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final var socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final var social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), context.seed(), 16);
		PhantomAssertions.assertTrue(social.start(), "Party defense social service did not start.");
		final var backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final var party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, _combat), new PhantomPartyTactics(_combat, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 32, social, () -> System.currentTimeMillis() / 60000L);
		PhantomAssertions.assertTrue(party.start(), "Party defense coordinator did not start.");
		party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
		try (var client = PhantomConversationIntegrationSuite.NetworkBackedClient.attach(human))
		{
			PhantomAssertions.assertTrue(PartyInvitationService.getInstance().invite(human, _player, PartyDistributionType.FINDERS_KEEPERS.getId()).delivered(), "Actual human-to-managed party invitation was not delivered.");
			for (int pulse = 0; pulse < 64; pulse++) { party.onPulse(); Thread.sleep(10); }
			PhantomAssertions.assertTrue(human.isInParty() && (human.getParty() == _player.getParty()) && party.committed(_profile.profileId()), "Native mixed party did not commit.");
			PhantomAssertions.assertTrue(party.pvpProtection(_profile.profileId(), 4).isEmpty(), "Flags/visibility without actual damage created party aggression.");
			PhantomAssertions.assertTrue(!human.isInsidePeaceZone(enemy) && GeoEngine.getInstance().canSeeTarget(enemy, human), "Third TEST actor has no legal native attack context.");
			context.record("pvp058.partyInput", "native human leader=" + human.getObjectId() + "; managedMage=" + _player.getObjectId() + "; owned enemy=" + enemy.getObjectId() + "; actual hit before defense; same existing equipped actors/no refill; stock rules;30s bound; TEST loopback only, no client login claim");
			final double humanPool = human.getCurrentHp() + human.getCurrentCp();
			final double enemyPool = enemy.getCurrentHp() + enemy.getCurrentCp();
			final double mp = _player.getCurrentMp();
			enemy.setTarget(human); human.onForcedAttack(enemy);
			await(() -> (human.getCurrentHp() + human.getCurrentCp()) < humanPool, "Owned enemy did not actually hit the native human leader.");
			final var proof = party.pvpProtection(_profile.profileId(), 4).stream().filter(value -> (value.directive().targetObjectId() == enemy.getObjectId()) && (value.directive().targetMember().characterObjectId() == human.getObjectId())).findFirst().orElseThrow(() -> new AssertionError("Actual human damage did not create exact party-defense evidence."));
			final var request = new PhantomPvpCombatRequest(_profile.profileId(), enemy.getObjectId(), org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.PARTY_DEFENSE, proof.authorityHash(), PhantomCombatMode.RANGED_MAGIC, false, false, 1, 30_000, () -> false);
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startPvpSession(request).status(), "Exact native party defense did not acquire common combat ownership.");
			await(() -> ((_player.getCurrentMp() + 1) < mp) && ((enemy.getCurrentHp() + enemy.getCurrentCp() + 10) < enemyPool), "Native party defense did not complete actual damage/cost.");
			_combat.cancel(_profile.profileId()); consumeTerminal(); enemy.abortAttack(); enemy.getAI().setIntention(Intention.IDLE);
			PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().leave(human), "Native human leave failed.");
			for (int pulse = 0; pulse < 32; pulse++) { party.onPulse(); Thread.sleep(10); }
			PhantomAssertions.assertTrue(party.pvpProtection(_profile.profileId(), 4).isEmpty() && !_player.isInParty(), "Stale human membership retained party-defense authority.");
			PhantomAssertions.assertTrue(!_player.isDead() && !human.isDead() && !enemy.isDead(), "Declared native party-defense episode lost a participant.");
			context.record("pvp058.partyActual", "native PARTY_DEFENSE cause=" + proof.authorityHash() + "; generation=" + proof.partyGeneration() + "; enemyPool=" + enemyPool + "->" + (enemy.getCurrentHp() + enemy.getCurrentCp()) + "; MP=" + mp + "->" + _player.getCurrentMp() + "; after canonical human leave evidence empty; all three alive");
		}
		finally
		{
			_combat.cancel(_profile.profileId()); consumeTerminal(); enemy.abortAttack(); enemy.getAI().setIntention(Intention.IDLE);
			if (human.isInParty()) { PartyInvitationService.getInstance().leave(human); }
			party.beginStop(); party.finishStop(); social.beginStop(); social.finishStop(); progression.beginStop(); progression.finishStop();
			_environment.cleanupLoadedPlayer(enemy);
		}
	}

	private void testPvpFlow059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final Player human = ensureObserver();
		final var enemyFixture = _environment.createCampaign058Companion(context, 2);
		final Player enemy = Player.load(enemyFixture.objectId());
		PhantomAssertions.assertTrue(enemy != null, "Flow TEST attacker did not load.");
		prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		_player.addSkill(SkillData.getInstance().getSkill(1339, 101), true);
		preparePvpDefender058(human);
		prepareCompanionCombat058(enemy, 92, 7575, 343, 6379, 6380, 6381, 6382);
		PhantomAssertions.assertTrue(enemy.getInventory().addItem(ItemProcessType.REWARD, 1345, 200, null, this) != null, "Declared TEST archer has no native S arrows.");
		_player.setRunning(); _player.standUp();
		final var nativeNavigation = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		Location flowInput = null;
		// Retain the blocked full-flow contract. No runtime navigation changes are justified by an unproven route.
		for (int[] input : new int[][] {{78900,150200}})
		{
			final int x = input[0], y = input[1];
			{
				final int z = GeoEngine.getInstance().getHeight(x, y, -3560);
				final boolean forbidden = ZoneManager.getInstance().getZone(x, y, z, PeaceZone.class) != null || ZoneManager.getInstance().getZone(x, y, z, NoPvPZone.class) != null || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null;
				if (!GeoEngine.getInstance().hasGeo(x, y) || forbidden) { System.out.println("PVP059 preflight=" + x + ',' + y + ',' + z + "; geo=" + GeoEngine.getInstance().hasGeo(x, y) + "; forbidden=" + forbidden); continue; }
				if (!GeoEngine.getInstance().canMoveToTarget(x, y, z, x + 64, y, GeoEngine.getInstance().getHeight(x + 64, y, z), 0)) { continue; }
				final var origin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, 0);
				final var respawns = _topology.nearestAnchors(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(x,y,z,0), org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.RESPAWN, 1, 100_000);
				final var landmarks = respawns.isEmpty() ? _topology.nearestAnchors(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(x,y,z,0), org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.CITY_CENTER, 1, 100_000) : respawns;
				if (landmarks.isEmpty()) { continue; }
				final var landmark = landmarks.getFirst().point();
				final var safe = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(landmark.x(), landmark.y(), GeoEngine.getInstance().getHeight(landmark.x(), landmark.y(), landmark.z()), 0);
				final long now = System.nanoTime();
				final var plan = nativeNavigation.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), origin, safe, now, now + TimeUnit.SECONDS.toNanos(1), 100_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
				double length = 0; var previous = origin;
				for (var waypoint : plan.points()) { length += previous.distanceTo(waypoint); previous = waypoint; }
				final double eta = length / _player.getStat().getMoveSpeed();
				System.out.println("PVP059 preflight=" + origin + "; status=" + plan.status() + "; length=" + length + "; ETA=" + eta + "; safe=" + safe);
				if (plan.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND && length >= 450 && eta < 25) { flowInput = new Location(x, y, z); break; }
			}
			if (flowInput != null) { break; }
		}
		PhantomAssertions.assertTrue(flowInput != null, "BLOCKED_NATIVE_ROUTE: declared Giran input has no proven protected native route within the unchanged30s window; full P4 remains PARTIAL.");
		int flowOffset = 0;
		for (Player actor : List.of(_player, human, enemy)) { actor.setInvul(false); actor.setRunning(); actor.standUp(); actor.decayMe(); actor.setXYZInvisible(flowInput.getX() + flowOffset, flowInput.getY(), flowInput.getZ()); actor.spawnMe(); actor.revalidateZone(true); flowOffset += 30; }
		_player.setCurrentCp(0); _player.setCurrentHp(_player.getMaxHp() * .55);
		PhantomAssertions.assertTrue(!human.isInsidePeaceZone(enemy) && !_player.isInsidePeaceZone(enemy) && GeoEngine.getInstance().canSeeTarget(enemy, human), "Flow native legal input is unavailable.");
		final var topologyBackend = new L2jTopologyValidationBackend();
		final var topology = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.fromSnapshotForTesting(_topology.snapshot(), topologyBackend, PhantomTopologyPolicy.productionDefaults(), new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long profileId, String sourceKey, long sequence) { return SignalDelivery.ACCEPTED; }
		});
		PhantomAssertions.assertTrue(topology.start(), "Flow topology did not start.");
		topology.registerProfile(_profile.profileId());
		final PhantomNavigationService navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final var socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final var social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_repository, socialCatalog), context.seed(), 16); social.start();
		final var backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final var party = new PhantomPartyCoordinator(new PhantomPartyStore(_repository), new PhantomGoalStateStore(_repository), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(navigation, _combat), new PhantomPartyTactics(_combat, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 32, social, () -> System.currentTimeMillis() / 60000L);
		party.start();
		party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
		final var farming = new org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService(org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPolicy.load(Path.of("data/phantoms/farming/high-five-farming-conflict-v1.xml")), new org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingStore(_repository), id -> Optional.empty(), topology, (first, second) -> false, new org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.SocialFacts()
		{
			@Override public org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.SocialEvidence evidence(long owner, long counterpart, long minute) { throw new AssertionError("No farming source belongs to this native party-defense scene."); }
			@Override public boolean record(long owner, long counterpart, String eventKey, String eventId, String evidenceHash, long minute) { throw new AssertionError("No farming event belongs to this scene."); }
		}, 16, () -> System.currentTimeMillis() / 60000L, org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingService.FaultInjector.NONE);
		farming.start();
		final var policy = org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPolicy.load(Path.of("data/phantoms/pvp/pvp-policy-v1.xml"));
		final var pvpStore = new org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStore(_repository);
		final var pvpSocial = new org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge(social);
		org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpService pvp = null;
		long positionSequence = 0;
		try (var composition = new PhantomCompanionComposition058(_repository, _materialization, _topology, _knowledge, social, party, human, null, _combat))
		{
			composition.invite(context, human, _player, _profile.profileId(), party);
			final var nativePoint = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
			final var respawns = _topology.nearestAnchors(nativePoint, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.RESPAWN, 1, 100_000);
			final var anchors = respawns.isEmpty() ? _topology.nearestAnchors(nativePoint, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.CITY_CENTER, 1, 100_000) : respawns;
			final var nativeOrigin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(nativePoint.x(), nativePoint.y(), nativePoint.z(), 0);
			final var preferred = anchors.isEmpty() ? nativeOrigin : new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(anchors.getFirst().point().x(), anchors.getFirst().point().y(), anchors.getFirst().point().z(), 0);
			final var safe = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(preferred.x(), preferred.y(), GeoEngine.getInstance().getHeight(preferred.x(), preferred.y(), preferred.z()), 0);
			final long planNow = System.nanoTime();
			final var plan = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend().findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(_profile.profileId(), nativeOrigin, safe, planNow, planNow + TimeUnit.SECONDS.toNanos(1), 100_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
			double routeLength = 0; var previousPoint = nativeOrigin;
			for (var waypoint : plan.points()) { routeLength += previousPoint.distanceTo(waypoint); previousPoint = waypoint; }
			context.record("pvp059.routePreflight", "status=" + plan.status() + "; length=" + routeLength + "; nativeSpeed=" + _player.getStat().getMoveSpeed() + "; ETA=" + routeLength / _player.getStat().getMoveSpeed() + "; safe=" + safe + "; native policy window30s; no movement during plan");
			PhantomAssertions.assertTrue(plan.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND && routeLength / _player.getStat().getMoveSpeed() < 27, "INVALID_INPUT: proven native retreat route does not fit the unchanged30s policy window.");
			pvp = new org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpService(policy, pvpStore, new org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpContext(policy, _combat, party, farming, pvpSocial, _materialization), _combat, composition.pvpBridge059(), pvpSocial, new org.l2jmobius.gameserver.phantoms.navigation.PhantomPvpRetreatCoordinator(navigation, topology, _combat));
			PhantomAssertions.assertTrue(pvp.start(), "Native flow PvP owner did not start.");
			pvp.materializeSucceeded(_profile.profileId(), _player.getObjectId());
			final boolean antiFeed = PvpConfig.ANTIFEED_ENABLE;
			final double humanPool = human.getCurrentHp() + human.getCurrentCp(), enemyPool = enemy.getCurrentHp() + enemy.getCurrentCp(), mp = _player.getCurrentMp();
			final double retreatHp = Math.max(_player.getMaxHp() * policy.limits().retreatHpPercent() / 100.0, (_player.getMaxHp() + _player.getMaxCp()) * policy.limits().retreatEffectivePoolPercent() / 100.0);
			PhantomAssertions.assertTrue(_player.getCurrentHp() > retreatHp, "INVALID_INPUT: defender already below the declared native retreat threshold.");
			context.record("pvp059.flowInput", "INDEPENDENT stock Giran input; Mage94/80/1339:101/Sgear HP55% CP0; human Knight5/80/full; enemy Archer92/80/S7575+343/Draconic armor/Sarrows1345x200/full; native party-defense first, enemy then targets mage until native HP/effective-pool threshold=" + retreatHp + ", voluntarily withdraws; no refill/TP/stat edit during measurement; native policy45s combat/30s retreat; actual input=" + nativeOrigin + "; planned safe=" + safe + "; source Game=" + System.getProperty("phantom.contract058.semantic.jar"));
			enemy.setTarget(human); human.onForcedAttack(enemy);
			await(() -> (human.getCurrentHp() + human.getCurrentCp()) < humanPool, "Flow enemy did not actually damage the protected human.");
			final long engageDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (((enemy.getCurrentHp() + enemy.getCurrentCp()) >= enemyPool - 10 || _player.getCurrentMp() >= mp - 1) && System.nanoTime() < engageDeadline)
			{
				topology.updateProfile(_profile.profileId(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(_player.getX(), _player.getY(), _player.getZ(), 0), ++positionSequence);
				pvp.onPulse(); composition.pulsePvp059(); Thread.sleep(20);
			}
			PhantomAssertions.assertTrue((enemy.getCurrentHp() + enemy.getCurrentCp()) < enemyPool - 10 && _player.getCurrentMp() < mp - 1, "Party-defense owner produced no completed native damage/cost: " + pvp.snapshot());
			final var admitted = pvpStore.load(_profile.profileId()).orElseThrow().encounter();
			PhantomAssertions.assertTrue(composition.pvpBridge059().receipt(_profile.profileId(), admitted.helpReceiptId()).map(value -> value.delivered()).orElse(false), "Party-defense HELP did not reach native SENT.");
			PhantomAssertions.assertTrue(_combat.observePvp(_profile.profileId(), List.of(enemy.getObjectId()), 16, 16).isEmpty(), "Generic PvP observation weakened active-lease exclusivity.");
			PhantomAssertions.assertTrue(_combat.observeOwnedPvp(_profile.profileId(), enemy.getObjectId(), "0".repeat(64), 16, 16).isEmpty(), "Wrong authority read an admitted native session.");
			PhantomAssertions.assertTrue(_combat.observeOwnedPvp(_profile.profileId(), enemy.getObjectId(), admitted.authorityHash(), 16, 16).isPresent(), "Exact native owner cannot observe its current risk.");
			enemy.setTarget(_player); _player.onForcedAttack(enemy);
			final long resourceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			boolean riskWitness = false;
			while (!_player.isDead() && pvp.snapshot().retreats() == 0 && System.nanoTime() < resourceDeadline)
			{
				topology.updateProfile(_profile.profileId(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(_player.getX(), _player.getY(), _player.getZ(), 0), ++positionSequence);
				riskWitness |= Math.round(_player.getCurrentHp() * 100 / _player.getMaxHp()) <= policy.limits().retreatHpPercent() || Math.round((_player.getCurrentHp() + _player.getCurrentCp()) * 100 / (_player.getMaxHp() + _player.getMaxCp())) <= policy.limits().retreatEffectivePoolPercent();
				if (riskWitness) { break; }
				pvp.onPulse(); composition.pulsePvp059(); Thread.sleep(20);
			}
			riskWitness |= Math.round(_player.getCurrentHp() * 100 / _player.getMaxHp()) <= policy.limits().retreatHpPercent() || Math.round((_player.getCurrentHp() + _player.getCurrentCp()) * 100 / (_player.getMaxHp() + _player.getMaxCp())) <= policy.limits().retreatEffectivePoolPercent();
			PhantomAssertions.assertTrue(!_player.isDead() && riskWitness && _player.getCurrentHp() > _player.getMaxHp() * .15, "Declared resource transition did not enter the native rounded risk/combat15% interval; HP=" + _player.getCurrentHp() + '/' + _player.getMaxHp() + "; CP=" + _player.getCurrentCp());
			enemy.getAI().setIntention(Intention.MOVE_TO, new Location(enemy.getX() - 600, enemy.getY(), enemy.getZ()));
			final int retreatX = _player.getX(), retreatY = _player.getY();
			final long retreatAdmission = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (pvp.snapshot().retreats() == 0 && System.nanoTime() < retreatAdmission) { pvp.onPulse(); composition.pulsePvp059(); Thread.sleep(20); }
			context.record("pvp059.retreatAdmission", "service=" + pvp.snapshot() + "; durable=" + pvpStore.load(_profile.profileId()) + "; topology=" + topology.findProfile(_profile.profileId()));
			PhantomAssertions.assertEquals(1L, pvp.snapshot().retreats(), "Native resource retreat was missing or replayed: " + pvp.snapshot() + "; durable=" + pvpStore.load(_profile.profileId()));
			final long retreatDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(32);
			boolean nativeWalk = false;
			int lastX = _player.getX(), lastY = _player.getY(); double maximumStep = 0;
			while (pvpStore.load(_profile.profileId()).orElseThrow().encounter().stage() != org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage.COOLDOWN && System.nanoTime() < retreatDeadline)
			{
				nativeWalk |= _player.isMoving() && _player.getAI().getIntention() == Intention.MOVE_TO;
				maximumStep = Math.max(maximumStep, Math.hypot(_player.getX() - lastX, _player.getY() - lastY)); lastX = _player.getX(); lastY = _player.getY();
				topology.updateProfile(_profile.profileId(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(_player.getX(), _player.getY(), _player.getZ(), 0), ++positionSequence);
				pvp.onPulse(); composition.pulsePvp059(); Thread.sleep(20);
			}
			final var terminal = pvpStore.load(_profile.profileId()).orElseThrow().encounter();
			context.record("pvp059.flowRetreat", "terminal=" + terminal + "; actual=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ() + "; displacement=" + Math.hypot(_player.getX() - retreatX, _player.getY() - retreatY) + "; HP=" + _player.getCurrentHp() + "; service=" + pvp.snapshot());
			PhantomAssertions.assertTrue(terminal.terminalReason().equals("pvp.retreat.arrived") && Math.hypot(_player.getX() - retreatX, _player.getY() - retreatY) > 64 && nativeWalk && maximumStep < 100 && !_combat.hasClaim(_profile.profileId()) && !_player.isDead(), "Native retreat did not really walk/arrive/release ownership; walk=" + nativeWalk + "; maximumStep=" + maximumStep);
			PhantomAssertions.assertTrue(_player.isInsideZone(ZoneId.PEACE) || _player.isInsideZone(ZoneId.NO_PVP), "Retreat arrival is not a factual native protected surface.");
			human.getAI().setIntention(Intention.MOVE_TO, new Location(_player.getX(), _player.getY(), _player.getZ()));
			PhantomAssertions.assertTrue(waitFor(() -> human.calculateDistance3D(_player) < 350, 35_000), "Native human did not walk to the completed retreat regroup.");
			for (int pulse = 0; pulse < 32; pulse++) { party.onPulse(); pvp.onPulse(); composition.pulsePvp059(); Thread.sleep(20); }
			PhantomAssertions.assertTrue(human.getParty() == _player.getParty() && party.committed(_profile.profileId()) && !_combat.hasClaim(_profile.profileId()) && PvpConfig.ANTIFEED_ENABLE == antiFeed, "Regroup revived stale enemy ownership or changed native rules.");
			PhantomAssertions.assertTrue(enemy.getInventory().getInventoryItemCount(1345, -1) < 200, "Native ranged pressure did not debit its declared arrows.");
			Location continuation = null;
			for (int[] offset : new int[][] {{800,0},{0,800},{-800,0},{0,-800},{600,600},{-600,600},{600,-600},{-600,-600}})
			{
				final int x = human.getX() + offset[0], y = human.getY() + offset[1], z = GeoEngine.getInstance().getHeight(x, y, human.getZ());
				if (Math.hypot(_player.getX() - x, _player.getY() - y) <= 500 || (ZoneManager.getInstance().getZone(x, y, z, PeaceZone.class) == null && ZoneManager.getInstance().getZone(x, y, z, NoPvPZone.class) == null) || !GeoEngine.getInstance().checkNearestNswe(GeoEngine.getGeoX(x), GeoEngine.getGeoY(y), z, org.l2jmobius.gameserver.geoengine.geodata.Cell.NSWE_ALL)) { continue; }
				if (nativeNavigation.canWalkDirect(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(human.getX(), human.getY(), human.getZ(), 0), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x,y,z,0)) && nativeNavigation.canWalkDirect(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x,y,z,0))) { continuation = new Location(x,y,z); break; }
			}
			PhantomAssertions.assertTrue(continuation != null, "No native safe walking continuation from the completed regroup.");
			final int followX = _player.getX(), followY = _player.getY();
			final long engagements = pvp.snapshot().engagements();
			PhantomAssertions.assertTrue(composition.command059(context, human, _player, org.l2jmobius.gameserver.network.enums.ChatType.WHISPER, "иди за мной", party), "Post-conflict FOLLOW had no delivered reply.");
			final var resumedPvp = pvp;
			composition.walkAndRegroup059(context, human, List.of(_player), party, continuation.getX(), continuation.getY(), continuation.getZ(), () -> { resumedPvp.onPulse(); composition.pulsePvp059(); });
			PhantomAssertions.assertTrue(Math.hypot(_player.getX() - followX, _player.getY() - followY) > 64 && pvp.snapshot().engagements() == engagements && !_player.isDead() && !human.isDead() && !enemy.isDead(), "Useful native follow did not resume or stale conflict replayed after regroup.");
			context.record("pvp059.followContinuation", "same native Party; delivered FOLLOW; actual helper displacement=" + Math.hypot(_player.getX() - followX, _player.getY() - followY) + "; all3 alive; engagements=" + engagements + "; remaining native arrows=" + enemy.getInventory().getInventoryItemCount(1345, -1) + "; no resource reset");
			_player.storeMe();
			final var reloaded = new org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpStore(_repository).load(_profile.profileId()).orElseThrow().encounter();
			PhantomAssertions.assertEquals(terminal, reloaded, "Ordinary save changed terminal conflict/cooldown identity.");
			context.record("pvp059.flowResult", "actual defense effect+cost; native policy retreat/movement; voluntary enemy withdrawal; native human walking regroup; same Party; cooldown durable/reloaded; no actor rescue; owner UI unverified");
		}
		finally
		{
			if (pvp != null) { pvp.beginStop(); pvp.finishStop(); }
			_combat.cancel(_profile.profileId()); consumeTerminal();
			if (human.isInParty()) { PartyInvitationService.getInstance().leave(human); }
			party.beginStop(); party.finishStop(); farming.beginStop(); farming.finishStop(); social.beginStop(); social.finishStop(); progression.beginStop(); progression.finishStop();
			navigation.beginStop(); final long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); while (!navigation.finishStop() && System.nanoTime() < stopDeadline) { Thread.sleep(10); }
			topology.beginStop(); topology.finishStop(); _environment.cleanupLoadedPlayer(enemy);
		}
	}

	private void testPvpConsequences059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final Player target = ensureObserver();
		final boolean antiFeed = PvpConfig.ANTIFEED_ENABLE;
		for (boolean flagged : new boolean[] { true, false })
		{
			// Two independent TEST episodes; revival/HP are declared input boundaries, never a rescue inside a fight.
			if (target.isDead()) { target.doRevive(); }
			prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
			_player.addSkill(SkillData.getInstance().getSkill(1339, 101), true);
			preparePvpDefender058(target);
			int offset = 0;
			for (Player actor : List.of(_player, target))
			{
				actor.getAI().setIntention(Intention.IDLE); actor.setInvul(false); actor.stopPvpRegTask(); actor.setPvpFlag(0); actor.setPvpFlagLasts(0);
				actor.decayMe(); actor.setXYZInvisible(78900 + offset, 150200, -3560); actor.spawnMe(); actor.revalidateZone(true); offset += 30;
			}
			target.setKarma(0); target.setCurrentCp(0); target.setCurrentHp(100);
			if (flagged) { target.setPvpFlag(1); target.setPvpFlagLasts(System.currentTimeMillis() + PvpConfig.PVP_PVP_TIME); }
			PhantomAssertions.assertTrue(!_player.isInsidePeaceZone(target) && !target.isGM() && !_player.isGM() && !_player.isInParty() && !target.isInParty(), "Native consequence TEST input is illegal.");
			final int pvpBefore = _player.getPvpKills(), pkBefore = _player.getPkKills(), karmaBefore = _player.getKarma();
			final double mp = _player.getCurrentMp();
			try (var client = PhantomConversationIntegrationSuite.NetworkBackedClient.attach(target))
			{
				final boolean countedPvp = flagged && org.l2jmobius.gameserver.managers.AntiFeedManager.getInstance().check(_player, target);
				context.record("pvp059.consequenceInput." + flagged, "INDEPENDENT native Player control; same owned Mage94/80/1339:101 and Knight5/80/Sgear; victim HP100 CP0 fixture BEFORE enrollment; actual forced native useMagic until death; flagged=" + flagged + "; AntiFeed=" + antiFeed + "; native countedPvp=" + countedPvp + ";30s bound; no direct damage/doDie/config change");
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
				while (!target.isDead() && System.nanoTime() < deadline)
				{
					if (!_player.isCastingNow() && !_player.isAttackingNow()) { _player.setTarget(target); _player.useMagic(_player.getKnownSkill(1339), true, false); }
					Thread.sleep(20);
				}
				final boolean finished = waitFor(() -> target.isDead() && !_player.isCastingNow() && _player.getPvpFlag() != 0 && _player.getPkKills() == pkBefore + (flagged ? 0 : 1) && _player.getPvpKills() == pvpBefore + (countedPvp ? 1 : 0), 20_000);
				context.record("pvp059.consequenceBoundary." + flagged, "dead=" + target.isDead() + "; targetHP=" + target.getCurrentHp() + "; casting=" + _player.isCastingNow() + "; MP=" + mp + "->" + _player.getCurrentMp() + "; flag=" + _player.getPvpFlag() + "; PvP=" + _player.getPvpKills() + "; PK=" + _player.getPkKills() + "; karma=" + _player.getKarma());
				PhantomAssertions.assertTrue(finished && _player.getCurrentMp() < mp, "Native death/cost/flag callback did not finish: dead=" + target.isDead() + "; flag=" + _player.getPvpFlag() + "; MP=" + mp + "->" + _player.getCurrentMp());
				PhantomAssertions.assertEquals(pvpBefore + (countedPvp ? 1 : 0), _player.getPvpKills(), "Native anti-feed-aware PvP counter changed incorrectly.");
				PhantomAssertions.assertEquals(pkBefore + (flagged ? 0 : 1), _player.getPkKills(), "Native Player death was classified into the wrong PK counter.");
				PhantomAssertions.assertTrue(flagged ? _player.getKarma() == karmaBefore : _player.getKarma() > karmaBefore, "Native karma does not match the declared victim flag.");
				context.record("pvp059.consequenceActual." + flagged, "actual dead=true; native1339:101; MP=" + mp + "->" + _player.getCurrentMp() + "; PvP=" + pvpBefore + "->" + _player.getPvpKills() + "; PK=" + pkBefore + "->" + _player.getPkKills() + "; karma=" + karmaBefore + "->" + _player.getKarma());
			}
		}
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat() && !_player.isCastingNow(), 30_000), "Consequence actor did not quiesce naturally before save/reload.");
		final int objectId = _player.getObjectId(), pvpKills = _player.getPvpKills(), pkKills = _player.getPkKills(), karma = _player.getKarma();
		final long sp = _player.getSp();
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Consequence ordinary save did not finish.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Consequence ordinary reload did not finish.");
		_player = World.getInstance().getPlayer(objectId);
		PhantomAssertions.assertTrue(_player != null && _player.getPvpKills() == pvpKills && _player.getPkKills() == pkKills && _player.getKarma() == karma && _player.getSp() == sp && _player.getSkillLevel(1339) == 101 && _player.getActiveWeaponItem().getId() == 6579 && PvpConfig.ANTIFEED_ENABLE == antiFeed, "Normal reload lost/replayed native consequences, SP, rank or equipment.");
		context.record("pvp059.consequenceReload", "sameID=" + objectId + "; PvP=" + pvpKills + "; PK=" + pkKills + "; karma=" + karma + "; exact SP/rank/Sweapon retained; AntiFeed unchanged; no callback/reward replay");
	}

	private void testPvpAffiliations059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final Player member = ensureObserver();
		if (member.isDead()) { member.doRevive(); }
		PhantomAssertions.assertTrue(_player.getClanId() == 0 && member.getClanId() == 0 && !_player.isInParty() && !member.isInParty(), "Affiliation TEST actors already belong to another owner.");
		final var clan = ClanTable.getInstance().createClan(_player, "P059" + context.seed());
		PhantomAssertions.assertTrue(clan != null, "Owned TEST clan could not be created by native ClanTable.");
		try
		{
			clan.addClanMember(member); // Declared TEST affiliation input; not a claim of invitation/consent UI.
			final var refused = org.l2jmobius.gameserver.model.clan.ClanWarService.getInstance().declare(_player, "Task059NoClan");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.clan.ClanWarService.Reason.SOURCE_REQUIREMENTS, refused.reason(), "Native clan level/member war gate was bypassed.");
			PhantomAssertions.assertTrue(clan.getWarList().isEmpty() && !_player.canAttackCreature(member), "Native TEST clan did not retain friendship/war eligibility boundaries.");
			try { PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startPvpSession(pvpRequest(member, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.RANGED_MAGIC, false)).status(), "Native same-clan friendship acquired enemy combat ownership."); }
			finally { _combat.cancel(_profile.profileId()); consumeTerminal(); }
			context.record("pvp059.clanGuards", "native ClanTable TEST clan=" + clan.getId() + "; exact same-clan target rejected; native war SOURCE_REQUIREMENTS; no war declared and no native rule changed; positive bilateral-war encounter not claimed");
		}
		finally { ClanTable.getInstance().destroyClan(clan.getId()); }
		PhantomAssertions.assertTrue(_player.getClanId() == 0 && member.getClanId() == 0, "Owned native clan cleanup did not release exact actors.");
		final int instance = InstanceManager.getInstance().createDynamicInstance(0).getId();
		try
		{
			member.setInstanceId(instance);
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startPvpSession(pvpRequest(member, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.RANGED_MAGIC, false)).status(), "Foreign native instance acquired enemy ownership.");
		}
		finally { member.setInstanceId(0); InstanceManager.getInstance().destroyInstance(instance); }
	}

	private void testPvpPhysical() throws Exception
	{
		resetActor(true);
		final Player target = preparePvpTarget();
		final double initialHp = target.getCurrentHp();
		final double initialCp = target.getCurrentCp();
		final PhantomCombatService.StartResult started = _combat.startPvpSession(pvpRequest(target, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.MELEE_PHYSICAL, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Real Player physical PvP was not accepted.");
		await(() -> _player.hasAI() && (_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() == target), "Canonical target.onForcedAttack did not establish the exact Player attack.");
		PhantomAssertions.assertTrue((target.getCurrentHp() <= initialHp) && (target.getCurrentCp() <= initialCp), "Canonical physical PvP fabricated target HP or CP.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Physical PvP session did not release exact combat ownership.");
		consumeTerminal();
	}

	private void testPvpMagic() throws Exception
	{
		resetActor(true);
		final Player target = preparePvpTarget();
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Deterministic PvP offensive skill is unavailable.");
		_player.addSkill(skill, false);
		_player.setCurrentMp(_player.getMaxMp());
		final double initialHp = target.getCurrentHp();
		final double initialCp = target.getCurrentCp();
		final double initialMp = _player.getCurrentMp();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.supportsPvpSkill(new SelectedSkill(MAGIC_SKILL_ID, 1), PhantomCombatMode.RANGED_MAGIC), "Real hostile one-target PvP skill was rejected.");
		}
		final PhantomCombatService.StartResult started = _combat.startPvpSession(pvpRequest(target, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.REVENGE, PhantomCombatMode.RANGED_MAGIC, true));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Real Player skill PvP was not accepted.");
		await(() -> _player.isCastingNow() || (_player.getCurrentSkill() != null) || (target.getCurrentHp() < initialHp) || (target.getCurrentCp() < initialCp) || (_player.getCurrentMp() < initialMp), "Canonical Player.useMagic produced no observable cast state or effect.");
		PhantomAssertions.assertTrue((_player.getCurrentMp() <= initialMp) && (target.getCurrentHp() <= initialHp) && (target.getCurrentCp() <= initialCp), "Canonical PvP cast fabricated HP, CP or MP.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Skill PvP session did not release exact combat ownership.");
		consumeTerminal();
	}

	private void testPvpCpAndConsequences()
	{
		resetActor(true);
		final Player target = preparePvpTarget();
		final double initialHp = target.getMaxHp();
		final double initialCp = target.getMaxCp();
		PhantomAssertions.assertTrue(initialCp > 1, "PvP fixture has no usable canonical CP pool.");
		target.setCurrentHp(initialHp);
		target.setCurrentCp(initialCp);
		final double damage = Math.max(1, Math.min(25, Math.floor(initialCp / 2)));
		target.reduceCurrentHp(damage, _player, null);
		PhantomAssertions.assertTrue(target.getCurrentCp() < initialCp, "Canonical PlayerStatus did not consume CP first.");
		PhantomAssertions.assertTrue(Double.compare(target.getCurrentHp(), initialHp) == 0, "Canonical PlayerStatus bypassed remaining CP and reduced HP.");

		final long baseline5591 = _player.getInventory().getInventoryItemCount(5591, -1);
		final long baseline5592 = _player.getInventory().getInventoryItemCount(5592, -1);
		try
		{
			PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, 5591, 1, _player, this) != null, "Could not create test-owned CP Potion.");
			PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, 5592, 1, _player, this) != null, "Could not create test-owned Greater CP Potion.");
			_player.setTarget(null);
			_player.setCurrentCp(Math.max(0, _player.getMaxCp() - 1000));
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				final List<CpPotionSnapshot> stock = lease.cpPotions();
				final CpPotionSnapshot normal = stock.stream().filter(value -> value.itemId() == 5591).findFirst().orElseThrow(() -> new AssertionError("Real stock 5591 was not source-derived."));
				final CpPotionSnapshot greater = stock.stream().filter(value -> value.itemId() == 5592).findFirst().orElseThrow(() -> new AssertionError("Real stock 5592 was not source-derived."));
				PhantomAssertions.assertTrue((normal.skillId() == 2166) && (normal.skillLevel() == 1), "Item 5591 did not expose source-derived skill 2166/1.");
				PhantomAssertions.assertTrue((greater.skillId() == 2166) && (greater.skillLevel() == 2), "Item 5592 did not expose source-derived skill 2166/2.");
				final CpPotionUse used = lease.useCpPotion(normal.itemObjectId(), normal.itemId());
				PhantomAssertions.assertEquals(CpPotionOutcome.OBSERVED_SUCCESS, used.outcome(), "Registered ItemSkills did not produce observed CP potion success.");
				PhantomAssertions.assertTrue((used.countAfter() < used.countBefore()) && ((used.cpAfter() > used.cpBefore()) || (used.observedReuseMillis() > 0)), "CP potion success lacked observed canonical inventory/CP/reuse truth.");

				final PvpConsequenceSnapshot consequences = lease.pvpConsequences(target.getObjectId());
				PhantomAssertions.assertTrue(consequences != null, "Canonical PvP consequence snapshot is absent.");
				PhantomAssertions.assertTrue((consequences.pvpFlag() == _player.getPvpFlag()) && (consequences.karma() == _player.getKarma()) && (consequences.pvpKills() == _player.getPvpKills()) && (consequences.pkKills() == _player.getPkKills()), "PvP consequence snapshot diverged from canonical Player truth.");
				PhantomAssertions.assertTrue((consequences.normalPvpDurationMillis() == PvpConfig.PVP_NORMAL_TIME) && (consequences.pvpFlagDurationMillis() == PvpConfig.PVP_PVP_TIME) && (consequences.minimumPkForDrop() == PvpConfig.KARMA_PK_LIMIT), "PvP consequence snapshot diverged from canonical PvpConfig.");
				PhantomAssertions.assertTrue((consequences.karmaDropLimit() == RatesConfig.KARMA_DROP_LIMIT) && (Double.compare(consequences.weaponDropChance(), RatesConfig.KARMA_RATE_DROP_EQUIP_WEAPON) == 0) && (Double.compare(consequences.equipmentDropChance(), RatesConfig.KARMA_RATE_DROP_EQUIP) == 0) && (Double.compare(consequences.inventoryDropChance(), RatesConfig.KARMA_RATE_DROP_ITEM) == 0), "Drop risk snapshot diverged from canonical RatesConfig.");
			}
			final var observation = _combat.observePvp(_profile.profileId(), List.of(target.getObjectId()), 16, 1).orElseThrow(() -> new AssertionError("Bounded exact PvP observation is absent."));
			PhantomAssertions.assertEquals(1, observation.targets().size(), "Local risk scan created a victim candidate outside the exact causal target.");
			PhantomAssertions.assertEquals(target.getObjectId(), observation.targets().getFirst().target().objectId(), "Bounded PvP observation changed the exact causal target.");
			PhantomAssertions.assertTrue((observation.targets().getFirst().localSupport().limit() == 1) && (observation.targets().getFirst().localSupport().observedPlayers() <= 1), "Local PvP support observation exceeded its configured cap.");
		}
		finally
		{
			destroyInventoryCount(_player, 5591, _player.getInventory().getInventoryItemCount(5591, -1) - baseline5591);
			destroyInventoryCount(_player, 5592, _player.getInventory().getInventoryItemCount(5592, -1) - baseline5592);
		}
	}

	private void testPvpReputationOutcomes() throws Exception
	{
		resetActor(true);
		final Player target = preparePvpTarget();
		final int actorPvpFlag = _player.getPvpFlag();
		final long actorPvpFlagLasts = _player.getPvpFlagLasts();
		final int actorPvpKills = _player.getPvpKills();
		final int actorPkKills = _player.getPkKills();
		final int actorKarma = _player.getKarma();
		final int targetPvpFlag = target.getPvpFlag();
		final long targetPvpFlagLasts = target.getPvpFlagLasts();
		final int targetKarma = target.getKarma();
		final long targetExp = target.getExp();
		final long targetSp = target.getSp();
		final int targetDeathPenalty = target.getDeathPenaltyBuffLevel();
		final boolean antiFeed = PvpConfig.ANTIFEED_ENABLE;
		try
		{
			PvpConfig.ANTIFEED_ENABLE = false;
			_player.stopPvpRegTask();
			_player.setPvpFlag(0);
			_player.setPvpFlagLasts(0);
			_player.setKarma(0);
			target.setKarma(0);
			target.setPvpFlag(1);
			target.setPvpFlagLasts(System.currentTimeMillis() + PvpConfig.PVP_PVP_TIME);
			final PhantomCombatService.StartResult started = _combat.startPvpSession(pvpRequest(target, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, PhantomCombatMode.MELEE_PHYSICAL, false));
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Canonical flagged-target PvP did not start.");
			await(() -> _player.getPvpFlag() != 0, "Canonical physical Player attack did not update the actor PvP flag.");
			PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Flagged-target PvP ownership did not release.");
			consumeTerminal();

			final int pvpBefore = _player.getPvpKills();
			final int pkBeforePvp = _player.getPkKills();
			final int karmaBeforePvp = _player.getKarma();
			PhantomAssertions.assertTrue(target.doDie(_player), "Could not execute canonical flagged Player death.");
			PhantomAssertions.assertEquals(pvpBefore + 1, _player.getPvpKills(), "Canonical flagged Player death did not increment PvP kills.");
			PhantomAssertions.assertEquals(pkBeforePvp, _player.getPkKills(), "Canonical flagged Player death was misclassified as PK.");
			PhantomAssertions.assertEquals(karmaBeforePvp, _player.getKarma(), "Canonical flagged Player death fabricated karma.");

			target.doRevive();
			target.setCurrentHp(target.getMaxHp());
			target.setCurrentMp(target.getMaxMp());
			target.setCurrentCp(target.getMaxCp());
			target.setPvpFlag(0);
			target.setPvpFlagLasts(0);
			target.setKarma(0);
			_player.stopPvpRegTask();
			_player.setPvpFlag(0);
			_player.setPvpFlagLasts(0);
			_player.setKarma(0);
			final int pkBefore = _player.getPkKills();
			final int pvpBeforePk = _player.getPvpKills();
			PhantomAssertions.assertTrue(target.doDie(_player), "Could not execute canonical neutral Player death.");
			PhantomAssertions.assertEquals(pkBefore + 1, _player.getPkKills(), "Canonical neutral Player death did not increment PK kills.");
			PhantomAssertions.assertEquals(pvpBeforePk, _player.getPvpKills(), "Canonical neutral Player death was misclassified as PvP.");
			PhantomAssertions.assertTrue(_player.getKarma() > 0, "Canonical neutral Player death did not produce karma.");
		}
		finally
		{
			_combat.cancel(_profile.profileId());
			consumeTerminal();
			if (target.isDead())
			{
				target.doRevive();
			}
			_player.stopPvpRegTask();
			_player.setPvpFlag(actorPvpFlag);
			_player.setPvpFlagLasts(actorPvpFlagLasts);
			_player.setPvpKills(actorPvpKills);
			_player.setPkKills(actorPkKills);
			_player.setKarma(actorKarma);
			target.stopPvpRegTask();
			target.setPvpFlag(targetPvpFlag);
			target.setPvpFlagLasts(targetPvpFlagLasts);
			target.setKarma(targetKarma);
			target.getStat().setExp(targetExp);
			target.setSp(targetSp);
			target.setDeathPenaltyBuffLevel(targetDeathPenalty);
			target.setCurrentHp(target.getMaxHp());
			target.setCurrentMp(target.getMaxMp());
			target.setCurrentCp(target.getMaxCp());
			PvpConfig.ANTIFEED_ENABLE = antiFeed;
		}
	}

	private void testCanonicalAttack() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Normal monster combat was not accepted.");
		await(() -> target.isDead() || target.isAlikeDead(), "Canonical PlayerAI attack did not kill the deterministic target.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Canonical target death did not produce victory.");
		await(() -> _player.getTarget() == null, "Victory cleanup retained the exact dead combat target.");
		consumeTerminal();
	}

	private void testSpacingOwnership058(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		_player.decayMe();
		_player.setXYZInvisible(-71432, 258512, -3104);
		_player.spawnMe();
		_player.revalidateZone(true);
		final Monster target = new Monster(NpcData.getInstance().getTemplate(18008));
		_worldFixtures.add(target);
		target.spawnMe(-71480, 258616, -3104);
		context.record("spacing058.ownership.input", "TEST prepared mage94/80/1339:1 + stockNPC18008; no attack/reward; native own MOVE, stale cleanup, exact cleanup and foreign native MOVE preservation");
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.maintainRangedSpacing(target.getObjectId(), new SelectedSkill(1339, 1), PhantomCombatMode.RANGED_MAGIC), "Declared native spacing input did not move.");
			PhantomAssertions.assertTrue(_player.isMoving(), "Owned spacing has no native movement.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId() + 1, null, 0));
			PhantomAssertions.assertTrue(_player.isMoving(), "Stale different-target cleanup stopped owned spacing.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
			PhantomAssertions.assertFalse(_player.isMoving(), "Exact owned spacing cleanup retained movement.");
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.maintainRangedSpacing(target.getObjectId(), new SelectedSkill(1339, 1), PhantomCombatMode.RANGED_MAGIC), "Second independent spacing input did not move.");
			final Location foreign = new Location(-71480, 258376, -3104);
			PhantomAssertions.assertTrue(GeoEngine.getInstance().canMoveToTarget(_player.getX(), _player.getY(), _player.getZ(), foreign.getX(), foreign.getY(), foreign.getZ(), 0), "Declared foreign native movement input is blocked.");
			_player.getAI().setIntention(Intention.MOVE_TO, foreign);
			PhantomAssertions.assertTrue(_player.isMoving() && (_player.getYdestination() == foreign.getY()), "Foreign TEST native MOVE did not replace the destination.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
			PhantomAssertions.assertTrue(_player.isMoving() && (_player.getYdestination() == foreign.getY()), "Old spacing cleanup stopped foreign native movement.");
			_player.getAI().setIntention(Intention.IDLE); // End the separate TEST movement, owned by this fixture.
			context.record("spacing058.ownership.result", "differentTargetPreserved=true; exactOwnMoveStopped=true; foreignDestinationPreserved=true");
		}
		finally
		{
			_player.getAI().setIntention(Intention.IDLE);
		}
	}

	private void testManufactureSupplyGuards060(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final long originalAdena = _player.getAdena();
		_player.setBaseClass(0); _player.setPlayerClass(0); _player.setLearningClass(_player.getPlayerClass()); _player.getStat().setLevel((byte) 20); _player.getStat().setExp(_player.getStat().getExpForLevel(20)); _player.setSp(0); _player.rewardSkills();
		final var weapon = _player.getInventory().addItem(ItemProcessType.REWARD, 129, 1, null, this); _player.getInventory().equipItem(weapon);
		_player.getInventory().addItem(ItemProcessType.REWARD, 57, 10_000, null, this); _player.refreshExpertisePenalty(); _player.refreshOverloaded();
		placeInstanceInput058(_player, 115832, -177335, GeoEngine.getInstance().getHeight(115832, -177335, -880));
		final var shots = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(1463);
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.compatible(_player, shots), "Declared D weapon/charges are incompatible.");
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", "20539"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20539@generated.farm.ea72d768f3c8ec3d79c1a530.anchor")), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, "generated.farm.ea72d768f3c8ec3d79c1a530.anchor"), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.manufacture.discovery", 1);
		final var binding = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding(1, _player.getObjectId(), 0, _player.getClassIndex(), weapon.getObjectId(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player), 0);
		final var policy = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml"));
		final var reservations = new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService(policy); reservations.start();
		final var service = new org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService(policy, reservations, new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService(), _materialization, new PhantomGoalStateStore(_repository), _repository);
		final var helper = new org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply(service);
		try (var fixture = PhantomOfflineSupply060Fixture.create(context, _environment, _player, _query, true))
		{
			final var maker = fixture.actor();
			final java.util.function.LongFunction<java.util.Optional<PhantomGoal>> offer = reserve -> helper.offer(_player, root, binding, 0, List.of(shots), 116, reserve, System.currentTimeMillis() + 60_000, Long.MAX_VALUE, _ -> 0);
			PhantomAssertions.assertTrue(_player.getInventory().getInventoryItemCount(1785, -1) == 0 && _player.getInventory().getInventoryItemCount(1458, -1) == 0 && offer.apply(500).isEmpty(), "Missing-material offline craft was selected.");
			_player.getInventory().addItem(ItemProcessType.REWARD, 1785, 9, null, this); _player.getInventory().addItem(ItemProcessType.REWARD, 1458, 3, null, this);
			PhantomAssertions.assertTrue(offer.apply(500).isPresent(), "BLOCKED_MANUFACTURE_INPUT: finite native recipe/skill/materials/MP/offer are not discoverable.");
			final long adena = _player.getAdena(), makerAdena = maker.getAdena(), exp = _player.getExp(), sp = _player.getSp();
			final double mp = maker.getCurrentMp(); maker.setCurrentMp(0);
			try { PhantomAssertions.assertTrue(offer.apply(500).isEmpty(), "Zero-MP offline maker was selected."); }
			finally { maker.setCurrentMp(mp); }
			final var recipe = org.l2jmobius.gameserver.data.xml.RecipeData.getInstance().getRecipeList(20); maker.unregisterRecipeList(20);
			try { PhantomAssertions.assertTrue(offer.apply(500).isEmpty(), "Missing-recipe offline maker was selected."); }
			finally { maker.registerDwarvenRecipeList(recipe, true); }
			final var published = maker.getManufactureItems().remove(20);
			try { PhantomAssertions.assertTrue(offer.apply(500).isEmpty(), "Withdrawn offline manufacture listing was selected."); }
			finally { maker.getManufactureItems().put(20, published); }
			PhantomAssertions.assertTrue(offer.apply(_player.getAdena()).isEmpty(), "Offline craft spent the protected cash reserve.");
			PhantomAssertions.assertTrue(_player.getAdena() == adena && maker.getAdena() == makerAdena && _player.getExp() == exp && _player.getSp() == sp && reservations.findAudit(_profile.profileId(), 16).isEmpty(), "Readiness guards executed a paid operation/reward.");
			context.record("supply060.manufactureGuards", "SEPARATE source-readiness control; prepared Artisan56/20/172:2/native recipe20/clientNULL restored store; no materials, MP, recipe, published listing, cash reserve; no native payment/travel/full-life claim; native chance/data untouched");
		}
		finally
		{
			service.shutdown(System.currentTimeMillis()); reservations.shutdown(System.currentTimeMillis());
			if (_player.getAdena() == originalAdena + 10_000) { _player.reduceAdena(ItemProcessType.DESTROY, 10_000, _player, false); }
		}
	}

	private void testSupplyReturn060(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_player.setBaseClass(0); _player.setPlayerClass(0); _player.setLearningClass(_player.getPlayerClass()); _player.getStat().setLevel((byte) 5); _player.setSp(0);
		final var weapon = _player.getInventory().addItem(ItemProcessType.REWARD, 68, 1, null, this); _player.getInventory().equipItem(weapon);
		_player.setRunning(); _player.standUp(); _player.setInvul(false); _player.refreshExpertisePenalty(); _player.refreshOverloaded();
		placeInstanceInput058(_player, 115778, -177473, GeoEngine.getInstance().getHeight(115778, -177473, -880));
		final var from = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player);
		final var goals = new PhantomGoalStateStore(_repository);
		PhantomAssertions.assertTrue(goals.load(_profile.profileId()).isEmpty(), "Return control refuses an existing root.");
		final var anchor = _topology.findAnchor("generated.farm.ea72d768f3c8ec3d79c1a530.anchor").orElseThrow();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", "20539"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20539@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.return.corridor", 1);
		goals.insert(_profile.profileId(), root);
		final var store = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore(_repository, goals);
		final var field = new PhantomNavigationPoint(116731, -174686, -967, 0);
		final var binding = new org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding(root.revision(), _player.getObjectId(), _player.getActiveClass(), _player.getClassIndex(), weapon.getObjectId(), field, 0);
		final String key = _environment.observer().objectId() + ";0;" + org.l2jmobius.gameserver.services.PrivateStoreService.listingHash(_player.getSellList()) + ";0;696;B:" + weapon.getObjectId() + ":116:6:1835";
		final var prepared = new PhantomGoal(root.goalId(), "private.store.buy", PhantomGoalStatus.COMPLETED, root.subject(), new PhantomDomainRef("economy.offer", key), 1, 1, null, List.of(), null, "private.store.buy", 700, 0, 696, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8), Map.of(), "fixture.return.only", 1, binding.encode());
		store.saveIntent(_profile.profileId(), root, -1, prepared);
		final var loaded = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load();
		final var receipts = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(_repository);
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService(loaded, receipts, store, new org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend(_materialization, loaded.catalog(), java.time.Clock.systemDefaultZone()));
		final var policy = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml"));
		final var reservations = new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService(policy); reservations.start();
		final var social = new org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService(policy, reservations, new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService(), _materialization, store, _repository);
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var journey = new org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney(store, goals, _materialization, commerce, receipts, navigation, () -> _query, routes, _ -> true, (_, _) -> true, (_, _) -> false, org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
		journey.useOfflineSupply(social); navigation.start(); commerce.start(); journey.start();
		final long exp = _player.getExp(), sp = _player.getSp(), adena = _player.getAdena();
		context.record("supply060.returnInput", "PREMEASUREMENT prepared COMPLETED binding, return-only geometry control; no payment/autonomous choice/full life claim; from=" + from + "; captured field=" + field);
		try
		{
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(125); long sequence = 0;
			while (System.nanoTime() < until && org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.returning(store.intent(_profile.profileId()).orElseThrow().goal()))
			{
				final var planning = new PhantomPlanningContext(_profile.profileId(), root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), ++sequence);
				final var candidate = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				PhantomAssertions.assertTrue(candidate != null, "Prepared return lost its owning candidate.");
				final var plan = candidate.planFactory().create(planning);
				handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, 1, sequence, System.nanoTime(), 1, () -> false));
				Thread.sleep(100);
			}
			final var result = store.intent(_profile.profileId()).orElseThrow().goal();
			context.record("supply060.nativeReturn", "result=" + result + "; reason=" + journey.reason(_profile.profileId()) + "; actual=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ());
			PhantomAssertions.assertTrue(result.status() == PhantomGoalStatus.COMPLETED && org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding.parse(result).retryAfterEpochMillis() > 0 && _player.calculateDistance3D(field.x(), field.y(), field.z()) <= navigation.arrivalRadius() + 1 && _player.calculateDistance3D(from.x(), from.y(), from.z()) > 1000, "Paid-return corridor did not finish ordinary native WALK: " + journey.reason(_profile.profileId()));
			PhantomAssertions.assertTrue(_player.getExp() == exp && _player.getSp() == sp && _player.getAdena() == adena && receipts.find(_profile.profileId()).isEmpty() && reservations.findAudit(_profile.profileId(), 16).isEmpty(), "Return-only control created a reward/payment.");
		}
		finally
		{
			journey.beginStop(); journey.finishStop(); social.shutdown(System.currentTimeMillis()); reservations.shutdown(System.currentTimeMillis()); commerce.beginStop(); commerce.finishStop();
			navigation.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < until) { Thread.sleep(20); }
			final var child = _repository.findComponent(_profile.profileId(), "supply.intent").orElseThrow(); _repository.deleteComponent(_profile.profileId(), "supply.intent", child.rowVersion());
			final var current = goals.load(_profile.profileId()).orElseThrow(); PhantomAssertions.assertEquals(root, current.goal(), "Return control root drift; cleanup refused."); goals.delete(_profile.profileId(), current.rowVersion());
		}
	}

	private void testLearningLongWalk061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context); prepareLearningField059(context); _player.rewardSkills();
		final var known = _player.getSkills().values().stream().filter(skill -> skill.getLevel() > 0).findFirst().orElseThrow();
		_player.decayMe(); _player.setXYZInvisible(48947, 42009, -3488); _player.spawnMe(); _player.revalidateZone(true);
		final var from = new PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
		final var field = new PhantomNavigationPoint(45688, 44386, -3704, 0);
		final var goals = new PhantomGoalStateStore(_repository);
		final var goal = new PhantomGoal(context.seed(), "skill.learn", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("skill", known.getId() + ":" + known.getLevel()), 1, 0, null, List.of(), null, "learning.observed.route061", 600, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8), Map.of("class.id", (long) _player.getActiveClass(), "class.index", (long) _player.getClassIndex(), "field.x", (long) field.x(), "field.y", (long) field.y(), "field.z", (long) field.z(), "field.instance", 0L), "fixture.native.observed.route061", 1);
		goals.insert(_profile.profileId(), goal);
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
		final var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> true); journey.start();
		final long sp = _player.getSp(), exp = _player.getExp(), adena = _player.getAdena(); boolean local = false;
		org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult result = null;
		try
		{
			context.record("learning061.route.input", "Independent native route-only TEST; observed elf field points; already-owned native skill; no lesson/payment/quest grant; original2min trip cap; not ordinary A/B");
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(110);
			do { result = journey.advance(_profile.profileId(), goal, () -> false, System.nanoTime()); local |= result.reasonKey().equals("learning.navigation.local_arrived"); if (result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL || result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.FAIL_GOAL) { break; } Thread.sleep(100); } while (System.nanoTime() < until);
			PhantomAssertions.assertTrue(local && result != null && result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL && _player.calculateDistance3D(field.x(), field.y(), field.z()) <= navigation.arrivalRadius(), "Observed long learning WALK did not complete through proven local segments: " + result);
			PhantomAssertions.assertTrue(_player.getSp() == sp && _player.getExp() == exp && _player.getAdena() == adena && !_player.isInsideZone(ZoneId.PEACE), "Free local learning WALK changed earned resources or destination contract.");
			context.record("learning061.route.green", "actual native movement=" + from.distanceTo(field) + "; local boundary=true; original2min cap; completeGoal; SP/EXP/A unchanged");
		}
		finally { journey.beginStop(); journey.finishStop(); progression.beginStop(); progression.finishStop(); navigation.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < until) { Thread.sleep(20); } _player.stopMove(null); final var saved = goals.load(_profile.profileId()).orElseThrow(); goals.delete(_profile.profileId(), saved.rowVersion()); }
	}

	private void testLearningReturnFloor060(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		prepareLearningField059(context);
		_player.rewardSkills();
		final var known = _player.getSkills().values().stream().filter(skill -> skill.getLevel() > 0).findFirst().orElseThrow();
		final var geo = GeoEngine.getInstance();
		final var from = new PhantomNavigationPoint(_player.getX(), _player.getY(), _player.getZ(), 0);
		final var backend = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		PhantomNavigationPoint grounded = null;
		for (int[] offset : new int[][] {{256,0},{-256,0},{0,256},{0,-256}})
		{
			final int x = from.x() + offset[0], y = from.y() + offset[1];
			if (!geo.hasGeo(x, y)) { continue; }
			final var candidate = new PhantomNavigationPoint(x, y, geo.getHeight(x, y, from.z()), 0);
			if (backend.canWalkDirect(from, candidate)) { grounded = candidate; break; }
		}
		PhantomAssertions.assertTrue(grounded != null, "INVALID_INPUT: no factual short dry native return corridor.");
		final var feet = new PhantomNavigationPoint(grounded.x(), grounded.y(), grounded.z() - 1, 0);
		PhantomAssertions.assertFalse(backend.canWalkDirect(from, feet), "Raw interpolated feet unexpectedly satisfy the exact native endpoint contract.");
		final var goals = new PhantomGoalStateStore(_repository);
		var goal = new PhantomGoal(context.seed(), "skill.learn", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("skill", known.getId() + ":" + known.getLevel()), 1, 0, null, List.of(), null, "learning.return.floor.control", 600, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8), Map.of("class.id", (long) _player.getActiveClass(), "class.index", (long) _player.getClassIndex(), "field.x", (long) feet.x(), "field.y", (long) feet.y(), "field.z", (long) feet.z(), "field.instance", 0L), "fixture.native.return.floor", 1);
		goals.insert(_profile.profileId(), goal);
		final long sp = _player.getSp(), exp = _player.getExp();
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
		final var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> true); journey.start();
		org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult result = null;
		try
		{
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			do
			{
				result = journey.advance(_profile.profileId(), goal, () -> false, System.nanoTime());
				if (result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL || result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.FAIL_GOAL) { break; }
				Thread.sleep(50);
			} while (System.nanoTime() < deadline);
			context.record("career060.nativeReturnFloor", "captured feet=" + feet + "; proven ground=" + grounded + "; actual=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ() + "; result=" + result + "; already native-owned skill; no fee/reward or test XYZ inside measured WALK");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL, result.type(), "Captured native feet could not return through the verified ground corridor: " + result);
			PhantomAssertions.assertTrue(_player.calculateDistance3D(from.x(), from.y(), from.z()) > 50 && waitFor(() -> !_player.isMoving(), 5_000), "Return control did not finish ordinary native WALK.");
			final var wrongFloor = new java.util.LinkedHashMap<>(goal.constraints()); wrongFloor.put("field.z", (long) World.WORLD_Z_MAX);
			final var invalid = new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), wrongFloor, "fixture.native.return.wrong_floor", goal.revision() + 1);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney.request(invalid).isPresent(), "INVALID_INPUT: wrong-floor control exceeded the supported request bounds.");
			goals.replace(_profile.profileId(), goals.load(_profile.profileId()).orElseThrow().rowVersion(), invalid); goal = invalid;
			final var refused = journey.advance(_profile.profileId(), goal, () -> false, System.nanoTime());
			PhantomAssertions.assertEquals("learning.field.floor_unavailable", refused.reasonKey(), "Far floor projection bypassed the existing native arrival tolerance.");
			PhantomAssertions.assertFalse(_player.isMoving(), "Wrong-floor control issued native movement.");
			PhantomAssertions.assertEquals(sp, _player.getSp(), "Return-only native control charged SP.");
			PhantomAssertions.assertEquals(exp, _player.getExp(), "Return-only native control awarded EXP.");
		}
		finally
		{
			journey.beginStop(); journey.finishStop(); progression.beginStop(); progression.finishStop();
			navigation.beginStop(); final long drain = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < drain) { Thread.sleep(20); }
			_player.stopMove(null);
			final var stored = goals.load(_profile.profileId()).orElseThrow(); PhantomAssertions.assertEquals(goal, stored.goal(), "Native return guard root drift; cleanup refused."); goals.delete(_profile.profileId(), stored.rowVersion());
		}
	}

	private void testGludinTownRecovery061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		prepareLearningField059(context);
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var authority = new org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority(() -> _query, () -> _topology, () -> _progression, () -> commerce, routes);
		// Exact observed career field; a living TEST fixture, no death or recovery operation.
		_player.setXYZInvisible(-46177, 210132, -5080);
		final var town = org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance().getTeleToLocation(_player, org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType.TOWN);
		PhantomAssertions.assertTrue(town != null, "Native Gludin town is absent.");
		final var position = authority.canonicalRecoveryPosition(town.getX(), town.getY(), town.getZ(), town.getInstanceId(), _player.getHeading()).orElseThrow(() -> new AssertionError("Observed native Gludin town lacks canonical recovery anchor: " + town));
		PhantomAssertions.assertEquals(911, org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance().getMapRegionLocId(position.x(), position.y()), "Recovery crossed native Gludin region.");
		PhantomAssertions.assertTrue(authority.canonicalRecoveryPosition(town.getX(), town.getY(), town.getZ(), 1, 0).isEmpty(), "Instance recovery escaped its boundary.");
		_player.setXYZInvisible(position.x(), position.y(), position.z());
		final var anchor = _topology.findAnchor("generated.farm.bfb0335de417bc81a7e9b231.anchor").orElseThrow();
		final long id = _profile.profileId();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20120"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20120@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.town061", 0);
		PhantomAssertions.assertEquals(position.committedAnchorId(), authority.capture(id, _player, root, null).position().committedAnchorId(), "Recovery anchor cannot be captured uniquely.");
		context.record("townRecovery061", "Native town=" + town + "; canonical=" + position + "; living TEST only; no death/fault/ordinary grants");
	}

	private void testRetainedAutoGet061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		_player.setBaseClass(0); _player.setPlayerClass(0); _player.setLearningClass(_player.getPlayerClass());
		_player.getStat().setExp(_player.getStat().getExpForLevel(20)); _player.getStat().setLevel((byte) 20); _player.setSp(92); _player.rewardSkills();
		_player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp()); _player.setCurrentCp(_player.getMaxCp());
		prepareLearningField059(context);
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var authority = new org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority(() -> _query, () -> _topology, () -> _progression, () -> commerce, routes);
		final var anchor = _topology.findAnchor("generated.farm.bfb0335de417bc81a7e9b231.anchor").orElseThrow();
		final long id = _profile.profileId();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20120"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20120@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.retained_auto061", 0);
		final var before = authority.captureOwnedNative(id, _player, root, null).state();
		PhantomAssertions.assertEquals(2, _player.getSkillLevel(1320), "Prepared native level20 lacks free1320:2.");
		// Declared lower-level fixture input; no death, damage, fault injection or ordinary actor mutation.
		_player.getStat().setExp(_player.getStat().getExpForLevel(19)); _player.getStat().setLevel((byte) 19); _player.removeSkill(239);
		_player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp()); _player.setCurrentCp(_player.getMaxCp());
		final var after = authority.captureOwnedNative(id, _player, root, before).state();
		PhantomAssertions.assertTrue(after.autoGetSkills().contains(new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill(1320, 2)), "Native owned capture lost the attested retained1320:2 after level20->19; current floor alone rejects its store.");
		PhantomAssertions.assertFalse(after.autoGetSkills().stream().anyMatch(skill -> skill.skillId() == 239), "Removed Expertise was reintroduced by retained-auto capture.");
		final var check = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.class.getDeclaredMethod("skillsMatch", org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.class, Map.class);
		check.setAccessible(true); // Same existing pure canonical validator contract as paid-floor060.
		final var nativeSkills = Map.of(194, _player.getSkillLevel(194), 1320, _player.getSkillLevel(1320), 1322, _player.getSkillLevel(1322));
		PhantomAssertions.assertEquals(true, check.invoke(null, after, nativeSkills), "Persisted retained floor still rejects the canonical native rank.");
		final var unattested = authority.captureOwnedNative(id, _player, root, null).state();
		PhantomAssertions.assertEquals(false, check.invoke(null, unattested, nativeSkills), "Unattested higher rank passed the current-level floor.");
		PhantomAssertions.assertEquals(false, check.invoke(null, after, Map.of(194, 1, 1320, 3, 1322, 1)), "Rank above the retained witness passed the native level guard.");
		final var restored = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().encode(after));
		PhantomAssertions.assertEquals(after.autoGetSkills(), after.retainAutoGetRanks(authority.autoGetSkills(after.identity(), 19)), "Further background work downgraded its retained native floor.");
		PhantomAssertions.assertEquals(after.autoGetSkills(), authority.captureOwnedNative(id, _player, root, restored).state().autoGetSkills(), "Restored native capture lost retained earned ranks.");
		PhantomAssertions.assertEquals(92L, _player.getSp(), "Retained free skill capture changed SP.");
		context.record("retainedAuto061", "native class0 level20->declared19; actual1320:2 retained/239removed; previous=" + before.autoGetSkills() + "; captured=" + after.autoGetSkills() + "; no death/fault/quest/reward/test-controller action");
	}

	private void testPaidAutoGetFloor060(PhantomTestContext context, boolean positive) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var authority = new org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority(() -> _query, () -> _topology, () -> _progression, () -> commerce, routes);
		final var base = PhantomBackgroundSuite.productionState(_topology.findAnchor("population.farming.human-mystic.20481").orElseThrow(), authority.hashes());
		final var state = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState(base.state(), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(base.identity().profileId(), base.identity().characterObjectId(), 0, 10, 0), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress(7, _player.getStat().getExpForLevel(7), 0, 0), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), List.of(new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill(1177, 1)), base.clock(), base.receipt(), base.hashes());
		final var check = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.class.getDeclaredMethod("skillsMatch", org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.class, Map.class);
		check.setAccessible(true); // Existing deterministic contract, no private native action or DB mutation.
		if (positive)
		{
			PhantomAssertions.assertEquals(true, check.invoke(null, state, Map.of(1177, 1)), "Native starter auto-get floor disappeared.");
			PhantomAssertions.assertEquals(true, check.invoke(null, state, Map.of(1177, 2)), "Paid native1177:2 was rejected as a mismatch against auto-get1177:1.");
		}
		else
		{
			for (var skills : List.of(Map.<Integer, Integer>of(), Map.of(1177, 0), Map.of(1177, 4), Map.of(1177, 5000)))
			{
				PhantomAssertions.assertEquals(false, check.invoke(null, state, skills), "Missing/level-ineligible/nonexistent native rank covered auto-get floor: " + skills);
			}
			final var foreign = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState(state.state(), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(state.identity().profileId(), state.identity().characterObjectId(), 0, 0, 0), state.progress(), state.vitals(), state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
			PhantomAssertions.assertEquals(false, check.invoke(null, foreign, Map.of(1177, 2)), "Foreign class tree authorized the higher paid rank.");
		}
		context.record("career060.autoGetFloor." + positive, "PURE validator against actual HumanMystic native skill tree/level7; no Player skill/SP/EXP or canonical writes; real paid retained failure is profile883/1177:2");
	}

	private void testFailedLearningReturn061(PhantomTestContext context) throws Exception
	{
		testNativeReturn061(context, false);
	}

	private void testNativeReturn061(PhantomTestContext context, boolean career) throws Exception
	{
		testNativeReturn061(context, career, false);
	}

	private void testNativeReturn061(PhantomTestContext context, boolean career, boolean townBase) throws Exception
	{
		testNativeReturn061(context, career, townBase, false);
	}

	private void testNativeReturn061(PhantomTestContext context, boolean career, boolean townBase, boolean handoff) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var field = townBase ? new PhantomNavigationPoint(-12787, 122779, GeoEngine.getInstance().getHeight(-12787, 122779, -3112), 0) : prepareLearningField059(context);
		if (townBase)
		{
			placeInstanceInput058(_player, field.x(), field.y(), field.z());
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(_player), "INVALID_INPUT: native town return base unavailable.");
			context.record("journey061.townReturnInput", "SEPARATE premeasurement native Gludio base; no ordinary actor/item/quest/class/SP mutation");
		}
		final var nativeNavigation = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		PhantomNavigationPoint away = null;
		for (int[] offset : new int[][] {{256,0},{-256,0},{0,256},{0,-256}})
		{
			final int x = field.x() + offset[0], y = field.y() + offset[1];
			if (!GeoEngine.getInstance().hasGeo(x, y)) { continue; }
			final var point = new PhantomNavigationPoint(x, y, GeoEngine.getInstance().getHeight(x, y, field.z()), 0);
			if (nativeNavigation.canWalkDirect(field, point)) { away = point; break; }
		}
		PhantomAssertions.assertTrue(away != null, "INVALID_INPUT: failed-return control needs a real local native corridor.");
		final var target = away;
		final String walkInput = "field=" + field + "; target=" + target + "; intention=" + _player.getAI().getIntention() + "; moving=" + _player.isMoving() + "; sitting=" + _player.isSitting() + "; disabled=" + _player.isMovementDisabled() + "; speed=" + _player.getMoveSpeed() + "; teleporting=" + _player.isTeleporting();
		_player.getAI().setIntention(Intention.MOVE_TO, new Location(target.x(), target.y(), target.z()));
		final boolean prepared = waitFor(() -> !_player.isMoving() && _player.calculateDistance3D(target.x(), target.y(), target.z()) < 60, 10_000);
		context.record("journey061.preparationWalk." + townBase + '.' + handoff, walkInput + "; reached=" + prepared + "; actual=" + org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player) + "; intentionAfter=" + _player.getAI().getIntention() + "; movingAfter=" + _player.isMoving());
		PhantomAssertions.assertTrue(prepared, "Native preparation WALK did not reach the declared failed-trip position: " + walkInput + "; actual=" + org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(_player));
		final var anchor = _topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(Comparator.comparingDouble(value -> Math.hypot(value.point().x() - field.x(), value.point().y() - field.y()))).orElseThrow();
		final var goals = new PhantomGoalStateStore(_repository);
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_combatPoint.npcId())), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, _combatPoint.npcId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.failed_return061", 1);
		goals.insert(_profile.profileId(), root);
		final var values = new java.util.LinkedHashMap<String, Long>();
		values.put("owner.revision", root.revision()); values.put("owner.object", (long) _player.getObjectId()); values.put("class.id", (long) _player.getActiveClass()); values.put("class.index", (long) _player.getClassIndex());
		values.put("field.x", (long) field.x()); values.put("field.y", (long) field.y()); values.put("field.z", (long) field.z()); values.put("field.instance", 0L); values.put("retry.after", System.currentTimeMillis() + 120_000);
		if (career) { values.put("class.from", (long) _player.getActiveClass()); values.put("class.to", (long) _player.getActiveClass() + 1); }
		final var failed = new PhantomGoal(root.goalId(), career ? "class.transfer" : "skill.learn", career ? PhantomGoalStatus.ACTIVE : PhantomGoalStatus.FAILED, root.subject(), career ? new PhantomDomainRef("supported.content", "class.warrior-q401") : new PhantomDomainRef("skill", "3:1"), 1, 0, null, List.of(), root.selectedAnchor(), career ? "career.autonomous" : "learning.autonomous", 600, 0, 0, System.currentTimeMillis() + (career ? 120_000 : -1), values, career ? "fixture.career_return_only" : "learning.route.deadline", 1);
		final String componentType = career ? "career.intent" : "learning.intent";
		_repository.insertComponent(_profile.profileId(), componentType, 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(failed));
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
		final var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> true);
		journey.enableAutonomous(_repository, (profile, deadline) -> true, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults()); journey.start();
		final long sp = _player.getSp(), exp = _player.getExp(); final int rank = _player.getSkillLevel(3);
		try
		{
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
			final var selected = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
			PhantomAssertions.assertTrue(career || journey.permitsAutomaticVisibleDecision(_profile.profileId(), root) && selected != null, "A failed native trip stranded its actor outside the farm gate instead of proposing bounded return.");
			final var plan = career ? null : selected.planFactory().create(planning); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult returnResult = null;
			boolean handedOff = false;
			do
			{
				if (career) { returnResult = journey.returnCareerToField(_profile.profileId(), root, failed, () -> false, System.nanoTime()); }
				else
				{
					final var step = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
					handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
				}
				if (handoff && !handedOff && _player.isMoving())
				{
					PhantomAssertions.assertTrue(waitFor(() -> _player.calculateDistance3D(target.x(), target.y(), target.z()) >= 80, 5000), "Owned handoff input never physically moved.");
					journey.beforeStore(_profile.profileId(), _player);
					PhantomAssertions.assertFalse(_player.isMoving(), "Journey cancelled only its path request and left its own native MOVE_TO running.");
					_player.getAI().setIntention(Intention.MOVE_TO, new Location(target.x(), target.y(), target.z()));
					PhantomAssertions.assertTrue(_player.isMoving(), "Foreign movement control was not admitted.");
					journey.beforeStore(_profile.profileId(), _player);
					PhantomAssertions.assertTrue(_player.isMoving() && _player.getXdestination() == target.x() && _player.getYdestination() == target.y(), "Old journey cleanup stopped a foreign native move.");
					handedOff = true;
				}
				if (career && returnResult.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.FAIL_GOAL) { break; }
				if (_player.calculateDistance3D(field.x(), field.y(), field.z()) <= navigation.arrivalRadius() && (career ? returnResult.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL : !journey.ownsAutomaticIntent(_profile.profileId(), root))) { break; }
				Thread.sleep(100);
			} while (System.nanoTime() < until);
			final var terminal = career ? new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(_repository.findComponent(_profile.profileId(), componentType).orElseThrow().payload()) : journey.automaticIntent(_profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(_player.calculateDistance3D(field.x(), field.y(), field.z()) <= navigation.arrivalRadius(), "Failed trip did not physically return through native WALK.");
			PhantomAssertions.assertTrue(!handoff || handedOff, "Native movement handoff contract never exercised a real issued move.");
			PhantomAssertions.assertTrue(!career || returnResult != null && returnResult.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL, "Native career return did not complete its accepted field/town contract: " + returnResult);
			PhantomAssertions.assertTrue((career ? terminal.equals(failed) && _player.getActiveClass() == values.get("class.from") : terminal.status() == PhantomGoalStatus.FAILED && terminal.reasonKey().equals("learning.route.deadline")) && _player.getSp() == sp && _player.getExp() == exp && _player.getSkillLevel(3) == rank, "Return changed its accepted authority, class, FAIL or paid lesson.");
			context.record(career ? "journey061.careerReturn" : "journey061.failedReturn", "native WALK256 then product return; original child retained; no SP/EXP/rank/class changes; one bounded settlement, not Q401/B or a lesson PASS");
		}
		finally
		{
			journey.beginStop(); journey.finishStop(); progression.beginStop(); progression.finishStop(); navigation.beginStop();
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < until) { Thread.sleep(20); }
			_player.stopMove(null); final var component = _repository.findComponent(_profile.profileId(), componentType).orElseThrow(); _repository.deleteComponent(_profile.profileId(), componentType, component.rowVersion());
			final var saved = goals.load(_profile.profileId()).orElseThrow(); goals.delete(_profile.profileId(), saved.rowVersion());
		}
	}

	private void testNativePreparationTown061(PhantomTestContext context) throws Exception
	{
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var routes = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var authority = new org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority(() -> _query, () -> _topology, () -> _progression, () -> commerce, routes);
		final var planner = new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner(_query, _topology, authority);
		final var anchor = _topology.findAnchor("population.farming.human-mystic.20481").orElseThrow();
		final var base = PhantomBackgroundSuite.productionState(anchor, authority.hashes());
		final var state = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState(base.state(), base.identity(), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress(19, 755866, 388, 0), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", "20120"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20120@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.regional061", 1);
		// Read-only planning at the observed ordinary35 position; no Player, quest or resource mutation.
		final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(-12787, 122779, -3112, 0);
		final var local = planner.replanVisibleLocal(_profile.profileId(), state, root, context.seed(), 1, live, java.util.Set.of(), java.util.Set.of());
		PhantomAssertions.assertFalse(local.ready(), "Observed no-local-farm input unexpectedly acquired a local field.");
		final int x = _player.getX(), y = _player.getY(), z = _player.getZ();
		try
		{
			// Separate TEST zone input before measurement; no ordinary actor, quest, level or resource mutation.
			_player.setXYZInvisible(_combatPoint.x(), _combatPoint.y(), _combatPoint.z()); _player.revalidateZone(true);
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(_player), "An ordinary combat field was mislabeled a safe town.");
			_player.setXYZInvisible(live.x(), live.y(), live.z()); _player.revalidateZone(true);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(_player), "Native Gludio peace/geo cannot serve as an autonomous preparation base.");
			PhantomAssertions.assertFalse(planner.isVisibleLocal(root, live), "Town preparation fabricated arrival at the saved farm anchor.");
			context.record("preparationTown061", "native Gludio PEACE/geo accepted; actual field rejected as town; local farm still absent; no farm arrival/quest/SP/level/class grant");
		}
		finally { _player.setXYZInvisible(x, y, z); _player.revalidateZone(true); }
	}

	private void testJourneyLifetime061(PhantomTestContext context)
	{
		final var legacy = new PhantomGoal(61, "skill.learn", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "61"), new PhantomDomainRef("skill", "3:1"), 1, 0, null, List.of(), null, "learning.autonomous", 600, 0, 0, 1_010_000, Map.of(), "fixture.clock061", 1);
		final var active = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(legacy, 1_000_000);
		final var paused = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(active, 1_001_000);
		final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec();
		final var loaded = codec.decode(codec.encode(paused));
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(loaded, 4_601_000), "A stored one-hour wait consumed the remaining active journey budget.");
		final var resumed = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(loaded, 4_601_000);
		PhantomAssertions.assertEquals(4_610_000L, resumed.deadlineEpochMillis(), "Resume did not preserve the nine remaining active seconds.");
		PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(resumed, 4_610_000), "Spent active journey budget was extended again.");
		PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(loaded, 87_400_000), "Repeated suspension bypassed the finite calendar bound.");
		PhantomAssertions.assertEquals(legacy, org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(legacy, 1_020_000), "An expired historical episode was revived during migration.");
		PhantomAssertions.assertEquals(680_000L, org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney.walkingBudgetMillis(30_000, 100), "A thirty-kilometer native WALK still uses a fixed two-minute deadline.");
		context.record("journey061.clock", "compatible Goal codec; one-hour offline wait preserves9000ms; active exhaustion and24h calendar refused; expired legacy unchanged;30000distance/100speed budget680000ms");
	}

	private void testAutonomousLearning060(PhantomTestContext context) throws Exception
	{
		testAutonomousLearning060(context, false);
	}

	private void testAutonomousLearning060(PhantomTestContext context, boolean townBase) throws Exception
	{
		testAutonomousLearning060(context, townBase, false);
	}

	private void testAutonomousLearning060(PhantomTestContext context, boolean townBase, boolean loadoutGuard) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var previousStrike = _player.getKnownSkill(3);
		final int previousWeapon = _player.getActiveWeaponInstance() == null ? 0 : _player.getActiveWeaponInstance().getObjectId();
		final long startingSp = loadoutGuard ? 200 : 100;
		final var goals = new PhantomGoalStateStore(_repository);
		_player.setBaseClass(0); _player.setPlayerClass(0); _player.getStat().setLevel((byte) 20); _player.setSp(startingSp);
		final var sword = _player.getInventory().addItem(ItemProcessType.REWARD, 68, 1, null, this);
		_player.getInventory().equipItem(sword);
		if (loadoutGuard)
		{
			_player.addSkill(SkillData.getInstance().getSkill(3, 3), false);
			PhantomAssertions.assertFalse(SkillData.getInstance().getSkill(16, 1).checkCondition(_player, _player, false), "Native sword input unexpectedly allows a dagger attack.");
			context.record("learning061.loadoutInput", "Independent TEST before measurement: Fighter20/Sword68/PowerStrike3/SP200; cheap dagger/bow attacks unavailable, native passive affordable; no ordinary grants/payment");
		}
		_player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp());
		if (townBase)
		{
			placeInstanceInput058(_player, -12787, 122779, GeoEngine.getInstance().getHeight(-12787, 122779, -3112));
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(_player), "Native learning preparation base is unavailable.");
			context.record("journey061.townLearningInput", "Independent premeasurement native Gludio base, matching ordinary35 stuck condition; reuse existing HOLD/root/deadline/native store/SP conservation guards; no paid lesson or ordinary B claim");
		}
		else { prepareLearningField059(context); }
		final var anchor = _topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(Comparator.comparingDouble(value -> Math.hypot(value.point().x() - _player.getX(), value.point().y() - _player.getY()))).orElseThrow();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_combatPoint.npcId())), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, _combatPoint.npcId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.learning.owner_guards", 1);
		goals.insert(_profile.profileId(), root);
		final var allowed = new java.util.concurrent.atomic.AtomicBoolean(false);
		context.record("career060.ownerGuardInput", "SEPARATE PREMEASUREMENT own060 fixture; class0/20/Sword68/SP" + startingSp + "; one root=" + root + "; no paid effect expected; own learning.intent/goal rows are cleaned only after exact owner drain");
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		PhantomAssertions.assertTrue(navigation.start(), "Autonomous learning navigation did not start.");
		var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> allowed.get());
		journey.enableAutonomous(_repository, (profile, pauseDeadline) -> true, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults());
		if (townBase) { journey.requireField((_, player) -> org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(player)); }
		PhantomAssertions.assertTrue(journey.start(), "Autonomous learning journey did not start.");
		try
		{
			final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry();
			journey.registerCandidates(candidates);
			candidates.seal();
			PhantomAssertions.assertTrue(candidates.snapshot().stream().anyMatch(candidate -> candidate.supportedGoalTypes().contains(PhantomBackgroundGoalSpec.GOAL_TYPE)), "Earned farm actor has no registered autonomous learning candidate; explicit skill.learn is not a producer.");
			final var selector = new org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector();
			final var planning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
			PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() == null, "Foreign owner/HOLD allowed a new learning root.");
			PhantomAssertions.assertFalse(journey.permitsAutomaticVisibleDecision(_profile.profileId(), root), "HOLD opened the upstream learning Decision gate.");
			allowed.set(true);
			PhantomAssertions.assertTrue(journey.permitsAutomaticVisibleDecision(_profile.profileId(), root), "Fresh affordable native lesson cannot reach the upstream visible Decision gate.");
			_player.getAI().clientStartAutoAttack();
			PhantomAssertions.assertTrue(_player.isInCombat(), "Native combat stance control did not start.");
			PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), planning).candidate() != null, "Declared affordable native lesson was not proposed.");
			if (Boolean.getBoolean("phantom.test.nearbyLife061"))
			{
				final var nearby = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.NEARBY_PERCEPTIBLE, System.nanoTime(), 2);
				PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), nearby).candidate() != null, "Ordinary materialized NEARBY farm cannot select its affordable native lesson.");
				for (var state : List.of(PhantomActivityState.SLEEPING, PhantomActivityState.BACKGROUND))
				{
					final var absent = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), root, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), state, System.nanoTime(), 3);
					PhantomAssertions.assertTrue(selector.select(candidates.snapshot(), absent).candidate() == null, "Nonmaterialized state selected native trainer work.");
				}
			}
			final var stored = goals.load(_profile.profileId()).orElseThrow();
			final var replacement = root.withStatus(PhantomGoalStatus.ACTIVE);
			goals.replace(_profile.profileId(), stored.rowVersion(), replacement);
			PhantomAssertions.assertFalse(journey.permitsAutomaticVisibleDecision(_profile.profileId(), root), "Stale farm revision opened the upstream learning Decision gate.");
			PhantomAssertions.assertTrue(journey.permitsAutomaticVisibleDecision(_profile.profileId(), replacement), "Current farm revision lost the upstream learning Decision gate.");
			final var currentPlanning = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(), replacement, org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 2);
			final var selected = selector.select(candidates.snapshot(), currentPlanning).candidate();
			PhantomAssertions.assertTrue(selected != null, "Fresh root lost its eligible lesson.");
			final var plan = selected.planFactory().create(currentPlanning);
			final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final var step = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), replacement, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
			final var outcome = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
			final var intent = journey.automaticIntent(_profile.profileId()).orElseThrow();
			if (loadoutGuard)
			{
				final var selectedSkill = org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney.request(intent).orElseThrow();
				PhantomAssertions.assertTrue(SkillData.getInstance().getSkill(selectedSkill.skillId(), selectedSkill.skillLevel()).checkCondition(_player, _player, false), "Learning producer selected an attack rejected by native equipped-weapon conditions: " + selectedSkill);
			}
			context.record("career060.ownerGuardIntent", "outcome=" + outcome + "; intent=" + intent);
			PhantomAssertions.assertEquals("learning.actor.busy", outcome.reasonKey(), "Selecting a future lesson must preserve the native combat execution guard.");
			PhantomAssertions.assertFalse(_player.isMoving(), "Combat stance control started trainer travel.");
			PhantomAssertions.assertEquals(replacement.revision(), intent.constraints().get("owner.revision").longValue(), "A cached offer from an old root revision created a stale learning intent.");
			PhantomAssertions.assertEquals(startingSp, _player.getSp(), "Owner guard control spent SP before any trainer effect.");
			if (Boolean.getBoolean("phantom.test.journeyLifecycle061"))
			{
				_player.getAI().clientStopAutoAttack(); _player.abortAttack(); _player.stopMove(null); _player.getAI().setIntention(Intention.IDLE);
				journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "Accepted learning owner did not drain before normal save.");
				final int objectId = _player.getObjectId();
				PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Accepted journey did not save through native materialization.");
				try
				{
					journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> allowed.get());
					journey.enableAutonomous(_repository, (profile, pauseDeadline) -> true, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults());
					PhantomAssertions.assertTrue(journey.start(), "Recreated stored-journey owner did not start.");
					PhantomAssertions.assertTrue(journey.ownsAutomaticIntent(_profile.profileId(), replacement), "Accepted durable child lost authority solely because its native Player was stored.");
					allowed.set(false);
					PhantomAssertions.assertFalse(journey.ownsAutomaticIntent(_profile.profileId(), replacement), "HOLD retained native journey authority.");
					PhantomAssertions.assertTrue(journey.protectsAutomaticRoot(_profile.profileId(), replacement), "HOLD allowed a background replan to discard the finite accepted root.");
					allowed.set(true);
					PhantomAssertions.assertFalse(journey.ownsAutomaticIntent(_profile.profileId(), replacement.withStatus(PhantomGoalStatus.ACTIVE)), "Replacement root inherited an old child's authority.");
					PhantomAssertions.assertFalse(journey.protectsAutomaticRoot(_profile.profileId(), replacement.withStatus(PhantomGoalStatus.ACTIVE)), "A replacement root inherited old acceptance protection.");
					context.record("journey061.storedAuthority", "same component/root/class/object; native save; owner reconstructed; stored continuation retained; HOLD/replaced root refused; no SP or quest grants");
				}
				finally
				{
					allowed.set(true);
					PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Stored journey actor did not reload after the authority control.");
					_player = World.getInstance().getPlayer(objectId);
				}
			}
			journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "Old learning owner did not drain before deadline reconstruction.");
			final var component = _repository.findComponent(_profile.profileId(), "learning.intent").orElseThrow();
			final var expired = new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), System.currentTimeMillis() - 1, intent.constraints(), intent.reasonKey(), intent.revision());
			_repository.updateComponent(_profile.profileId(), "learning.intent", component.rowVersion(), 1, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(expired));
			journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> allowed.get());
			journey.enableAutonomous(_repository, (profile, pauseDeadline) -> false, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults());
			PhantomAssertions.assertTrue(journey.start(), "Reconstructed expired-intent owner did not start.");
			final var renewedCandidates = new PhantomCandidateRegistry(); journey.registerCandidates(renewedCandidates); renewedCandidates.seal();
			final var renewedHandlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(renewedHandlers); renewedHandlers.seal();
			final var renewedPlan = selector.select(renewedCandidates.snapshot(), currentPlanning).candidate().planFactory().create(currentPlanning);
			final var renewedStep = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(), replacement, renewedPlan, renewedPlan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
			final var expiredResult = renewedHandlers.snapshot().get(renewedPlan.steps().getFirst().actionKey()).execute(renewedStep);
			PhantomAssertions.assertEquals(PhantomGoalStatus.FAILED, journey.automaticIntent(_profile.profileId()).orElseThrow().status(), "Expired restored intent waited forever for its captured target.");
			PhantomAssertions.assertEquals("learning.native.deadline", expiredResult.reasonKey(), "Expired intent did not retain its terminal cause.");
			PhantomAssertions.assertTrue(selector.select(renewedCandidates.snapshot(), currentPlanning).candidate() == null, "Expired intent ignored its durable backoff.");
			PhantomAssertions.assertEquals(startingSp, _player.getSp(), "Expired intent charged native SP.");
		}
		finally
		{
			journey.beginStop();
			PhantomAssertions.assertTrue(journey.finishStop(), "Autonomous learning journey did not drain.");
			progression.beginStop();
			PhantomAssertions.assertTrue(progression.finishStop(), "Autonomous progression did not drain.");
			navigation.beginStop();
			final long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!navigation.finishStop() && System.nanoTime() < stopDeadline) { Thread.sleep(20); }
			PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPED, navigation.snapshot().state(), "Autonomous navigation did not drain.");
			final var current = goals.load(_profile.profileId()).orElseThrow();
			PhantomAssertions.assertTrue(current.goal().equals(root) || current.goal().equals(root.withStatus(PhantomGoalStatus.ACTIVE)), "Own060 guard root changed unexpectedly; cleanup refused.");
			org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(_player);
			_player.getAI().clientStopAutoAttack();
			final var intentComponent = _repository.findComponent(_profile.profileId(), "learning.intent").orElse(null);
			if (intentComponent != null)
			{
				final var ownedIntent = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(intentComponent.payload());
				PhantomAssertions.assertTrue(ownedIntent.goalId() == root.goalId() && ownedIntent.constraints().getOrDefault("owner.object", -1L) == _player.getObjectId(), "Foreign learning intent; cleanup refused.");
				context.record("career060.ownerGuardCleanup", "fresh060 profile=" + _profile.profileId() + "; component=" + intentComponent.componentType() + "; row=" + intentComponent.rowVersion() + "; native owners stopped; no profile/account deletion");
				_repository.deleteComponent(_profile.profileId(), "learning.intent", intentComponent.rowVersion());
			}
			goals.delete(_profile.profileId(), current.rowVersion());
			if (loadoutGuard)
			{
				if (previousStrike == null) { _player.removeSkill(3, false); } else { _player.addSkill(previousStrike, false); }
				PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), 20_000), "Native loadout-only guard did not release its combat stance.");
			}
			final var oldWeapon = _player.getInventory().getItemByObjectId(previousWeapon);
			if (oldWeapon != null) { _player.getInventory().equipItem(oldWeapon); }
		}
	}

	private void testAutonomousLearningPolicy060(PhantomTestContext context) throws Exception
	{
		final Path source = _moduleRoot.resolve("dist/game/data/phantoms/life/high-five-autonomous-learning-v1.xml");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults(), org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.load(source), "Ordinary autonomous learning defaults differ.");
		final String text = Files.readString(source);
		final Path reports = Path.of(System.getProperty("phantom.test.reports"));
		Files.createDirectories(reports);
		final List<String> invalid = List.of(text.replace("version=\"1\"", "version=\"2\""), text.replace("retrySeconds=\"30\"", "retrySeconds=\"0\""), text.replace("/>", "><unexpected/></autonomousLearningPolicy>"));
		for (int index = 0; index < invalid.size(); index++)
		{
			final Path path = reports.resolve("policy-negative-" + index + ".xml");
			Files.writeString(path, invalid.get(index), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
			boolean rejected = false;
			try { org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.load(path); }
			catch (IllegalArgumentException expected) { rejected = true; }
			PhantomAssertions.assertTrue(rejected, "Unknown or unbounded autonomous learning policy accepted: case=" + index);
		}
	}

	private void testEarnedLearning058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final boolean journey059 = Boolean.getBoolean("phantom.test.learningJourney059");
		_player.setBaseClass(0); _player.setPlayerClass(0);
		_player.getStat().setExp(_player.getStat().getExpForLevel(20) - 500); _player.getStat().setLevel((byte) 19); _player.setSp(0);
		PhantomAssertions.assertTrue(_player.getKnownSkill(3) == null, "New Fighter already has the skill that must be paid for.");
		for (int id : new int[] {68, 24, 31}) { final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, id, 1, null, this); PhantomAssertions.assertTrue(item != null, "Declared no-grade fixture item missing."); _player.getInventory().equipItem(item); }
		final List<Item> level20Equipment = new ArrayList<>();
		for (int id : new int[] {129, 352, 2378}) { final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, id, 1, null, this); PhantomAssertions.assertTrue(item != null, "Declared owned level20 equipment input missing."); level20Equipment.add(item); }
		_player.refreshExpertisePenalty(); _player.refreshOverloaded(); _player.setRunning(); _player.standUp(); _player.setInvul(false); _player.setOnlineStatus(true, false);
		_player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp()); _player.setCurrentCp(_player.getMaxCp());
		final var field059 = journey059 ? prepareLearningField059(context) : new PhantomNavigationPoint(143214, 2086, GeoEngine.getInstance().getHeight(143214, 2086, -600), 0);
		final int x = field059.x(), y = field059.y(), z = field059.z();
		PhantomAssertions.assertTrue(GeoEngine.getInstance().hasGeo(x, y) && (journey059 || Math.abs(z + 600) < 64) && GeoEngine.getInstance().canMoveToTarget(x, y, z, x + 40, y, z, 0) && (ZoneManager.getInstance().getZone(x, y, z, PeaceZone.class) == null), "Declared native learned-career input point is unavailable.");
		placeInstanceInput058(_player, x, y, z);
		PhantomAssertions.assertTrue(World.getInstance().getVisibleObjects(_player, Monster.class).stream().noneMatch(monster -> !monster.isDead() && (_player.calculateDistance3D(monster) < 1800)), "Declared career episode has an unrelated stock monster nearby.");
		if (Boolean.getBoolean("phantom.test.autonomousLearning060"))
		{
			// A prior negative case may have prepared another SP value on this TEST actor.
			// Establish the declared zero-SP baseline by a real native write before the first earned event.
			_player.store(false);
			try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT sp,classid FROM characters WHERE charId=?"))
			{
				statement.setInt(1, _player.getObjectId());
				try (var rows = statement.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && rows.getLong("sp") == 0 && rows.getInt("classid") == _player.getActiveClass() && !rows.next(), "INVALID_INPUT: native fixture SP/class baseline was not stored."); }
			}
			context.record("career060.fixtureBaseline", "PREMEASUREMENT native store class0/SP0 before the first kill; no earned SP saved by the test before learning");
			final var anchor = _topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(Comparator.comparingDouble(value -> Math.hypot(value.point().x() - x, value.point().y() - y))).orElseThrow();
			final var farm = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", "20035"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20035@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.native.earned_farm", 1);
			new PhantomGoalStateStore(_repository).insert(_profile.profileId(), farm);
			context.record("career060.rootInput", "PREMEASUREMENT single farm root only; automatic product selects every lesson/route/return; root=" + farm);
		}
		final long expBefore = _player.getExp();
		context.record("career058.knowledge", _query.snapshot().combinedHash());
		try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query).resolve(lease.actorSnapshot(), PhantomCombatMode.MELEE_PHYSICAL, lease, 4).orElseThrow().selectedSkills().isEmpty(), "Curated Fighter capability granted an unlearned offensive skill.");
		}
		context.record("career058.input", "FIXTURE level19/EXPfor20-500/SP0/Fighter0/no PowerStrike/no-grade68+24+31; already-owned D129+352+2378 stay inventory until natural20/native equip; native stock20035 fullHP; four ordinary common melee kills; no grants/reward/HP/MP/item creation inside measurement; xyz=" + x + ',' + y + ',' + z);
		for (int kill = 0; kill < 4; kill++)
		{
			final Monster target = spawnCareerStock058();
			final long before = _player.getExp(); final long sp = _player.getSp();
			final var started = _combat.startContentSession(new PhantomCombatRequest(_profile.profileId(), target.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, false, false, 90_000, () -> false), "career058.earned." + kill);
			PhantomAssertions.assertTrue(started.accepted(), "Native earned-XP combat rejected: " + started);
			final boolean finished = waitFor(() -> target.isDead() || _player.isDead() || _combat.find(_profile.profileId()).map(value -> value.result().terminal() && !value.result().victory()).orElse(false), 90_000);
			context.record("career058.fight" + kill, "stockHP=" + target.getCurrentHp() + '/' + target.getMaxHp() + "; actorHP=" + _player.getCurrentHp() + "; SP=" + _player.getSp() + "; session=" + _combat.find(_profile.profileId()));
			PhantomAssertions.assertTrue(finished && target.isDead() && !_player.isDead(), "Full stock Skeleton fight failed without rescue.");
			PhantomAssertions.assertTrue(waitFor(() -> _player.getExp() > before && _player.getSp() > sp, 5_000), "Stock kill did not earn native EXP/SP.");
			final java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean();
			PhantomAssertions.assertTrue(waitFor(() -> { if (!consumed.get() && _combat.consumeTerminal(_profile.profileId()).isPresent()) { consumed.set(true); } return consumed.get(); }, 10_000), "Victory cleanup did not release common ownership.");
			context.record("career058.kill" + kill, "stock20035/HP=" + target.getMaxHp() + "; EXP=" + before + "->" + _player.getExp() + "; SP=" + sp + "->" + _player.getSp() + "; level=" + _player.getLevel());
			System.out.println("NATIVE058 career kill=" + (kill + 1) + "; level=" + _player.getLevel() + "; earnedEXP=" + (_player.getExp() - expBefore) + "; SP=" + _player.getSp());
			if (kill == 0)
			{
				PhantomAssertions.assertTrue(_player.getLevel() == 20 && _player.getSkillLevel(239) >= 1, "Natural20 did not learn native D expertise.");
				PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), 30_000), "Native combat did not end before earned-level equipment change.");
				final var equipmentOwner = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); equipmentOwner.start();
				try
				{
					for (Item item : level20Equipment) { final var equipped = equipmentOwner.equipOwnedItem(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest(_profile.profileId(), item.getObjectId(), () -> false)); PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.SUCCESS, equipped.status(), "Native owned-item equip after natural20 failed."); }
				}
				finally { equipmentOwner.beginStop(); PhantomAssertions.assertTrue(equipmentOwner.finishStop(), "Native equipment owner did not drain."); }
				context.record("career058.gear", "natural20/expertise1 -> existing owned D129+352+2378 native equipped; no grant/effect/HP rescue");
			}
		}
		PhantomAssertions.assertTrue(_player.getLevel() >= 20 && _player.getExp() > expBefore && _player.getSp() >= 100, "Four stock kills did not earn the declared level/learning budget.");
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat() && !_player.isAttackingNow() && !_player.isCastingNow(), 30_000), "Native combat state did not end before trainer learning.");
		// Immediate learning must consume genuinely earned, not yet manually saved SP.
		final long earnedSp = _player.getSp(), earnedExp = _player.getExp(); final int earnedObjectId = _player.getObjectId();
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT sp FROM characters WHERE charId = ?"))
		{
			statement.setInt(1, earnedObjectId);
			try (var rows = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(rows.next(), "Immediate SP fixture row is missing.");
				final long durableSp = rows.getLong(1);
				context.record("career059.immediateInput", "same native actor=" + earnedObjectId + "; EXP=" + earnedExp + "; runtimeSP=" + earnedSp + "; durableSP=" + durableSp + "; class=" + _player.getActiveClass() + "; index=" + _player.getClassIndex() + "; no manual save/reload before learning");
				PhantomAssertions.assertTrue(!rows.next() && durableSp < earnedSp, "INVALID_INPUT: the declared immediate-learning episode has no unsaved earned SP.");
				if (Boolean.getBoolean("phantom.test.autonomousLearning060"))
				{
					PhantomAssertions.assertTrue(_player.getStat().matchesNativeSkillPointCredit(durableSp), "INVALID_INPUT: earned SP does not have the native credit witness; do not start the WALK episode.");
					System.out.println("NATIVE060 INPUT_FENCE_GREEN runtimeSP=" + earnedSp + "; durableSP=" + durableSp + "; class=" + _player.getActiveClass());
				}
			}
		}
		if (journey059) { runLearningJourney059(context, field059); }
		else
		{
			final var template = NpcData.getInstance().getTemplates(value -> "Trainer".equals(value.getType()) && value.canTeach(_player.getLearningClass())).stream().min(Comparator.comparingInt(NpcTemplate::getId)).orElseThrow();
			final var trainer = new org.l2jmobius.gameserver.model.actor.instance.Trainer(template); trainer.spawnMe(_player.getX() + 10, _player.getY(), _player.getZ()); _player.setLastFolkNPC(trainer);
			final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
			try
			{
				for (int rank = 1; rank <= 2; rank++)
				{
					final long sp = _player.getSp();
					final var request = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest(_profile.profileId(), trainer.getObjectId(), org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind.CLASS, 3, rank, () -> false);
					final var result = progression.learnClassSkill(request);
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.SUCCESS, result.status(), "Paid native learning failed: " + result);
					PhantomAssertions.assertEquals(sp - 50, _player.getSp(), "Stock PowerStrike learning cost differs.");
					PhantomAssertions.assertEquals(rank, _player.getSkillLevel(3), "Actual learned rank differs.");
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.IDEMPOTENT, progression.learnClassSkill(request).status(), "Learning retry charged twice.");
					PhantomAssertions.assertEquals(sp - 50, _player.getSp(), "Idempotent retry consumed SP.");
					context.record("career058.learn" + rank, "nativeTrainer=" + trainer.getId() + "; skill3:" + rank + "; SP=" + sp + "->" + _player.getSp() + "; exact retry idempotent");
				}
			}
			finally { trainer.deleteMe(); progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Career progression owner did not drain."); }
		}
		// Separate, declared native-effect fixture after the completed earned/paid-learning episode.
		// Normal return may put this actor inside a town; its Peace guard remains unchanged and was verified negative.
		final long effectInputExp = _player.getExp(), effectInputSp = _player.getSp();
		context.record("career058.fieldEffectInput", journey059 ? "SAME measured journey after product native walk return; no fixture positioning since first earned kill; actualXYZ=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ() : "SEPARATE TEST field input; beforeXYZ=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ() + "; prepared field=" + x + ',' + y + ',' + z + "; position only, no HP/MP/EXP/SP/skill/item mutation; autonomous town-to-field travel NOT claimed");
		if (!journey059) { placeInstanceInput058(_player, x, y, z); }
		PhantomAssertions.assertTrue(!_player.isDead() && !_player.isInsideZone(ZoneId.PEACE) && (_player.getExp() == effectInputExp) && (_player.getSp() == effectInputSp) && (_player.getSkillLevel(3) == 2), "Separate effect input changed earned/paid progress or remains in Peace.");
		final var resolver = PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query);
		final Monster effectTarget = spawnCareerStock058(); final double hp = effectTarget.getCurrentHp(), mp = _player.getCurrentMp();
		try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final var resolved = resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.MELEE_PHYSICAL, lease, 4);
			context.record("career058.currentLoadout", resolved);
			PhantomAssertions.assertTrue(resolved.isPresent(), "Paid Fighter skill has no supported curated melee capability.");
			final var loadout = resolved.orElseThrow();
			PhantomAssertions.assertTrue(loadout.selectedSkills().contains(new SelectedSkill(3, 2)), "Resolver did not select the actual paid learned rank.");
			context.record("career058.effectAdmission", "actor=" + lease.actorSnapshot() + "; target=" + lease.targetSnapshot(effectTarget.getObjectId()) + "; supports=" + lease.supportsSkill(new SelectedSkill(3, 2), PhantomCombatMode.MELEE_PHYSICAL) + "; actorXYZ=" + _player.getX() + ',' + _player.getY() + ',' + _player.getZ() + "; targetXYZ=" + effectTarget.getX() + ',' + effectTarget.getY() + ',' + effectTarget.getZ());
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.cast(effectTarget.getObjectId(), new SelectedSkill(3, 2), PhantomCombatMode.MELEE_PHYSICAL), "Current paid rank native cast rejected.");
			PhantomAssertions.assertTrue(waitFor(() -> effectTarget.getCurrentHp() < hp && _player.getCurrentMp() < mp - 1, 10_000), "Paid current rank produced no actual native damage/cost.");
		}
		context.record("career058.effect", "skill3:2/native damage HP=" + hp + "->" + effectTarget.getCurrentHp() + "; MP=" + mp + "->" + _player.getCurrentMp());
		_player.abortAttack(); _player.getAI().setIntention(Intention.IDLE); // Completed effect measurement; quiesce the owned TEST fixture.
		effectTarget.deleteMe();
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isCastingNow(), 10_000), "Completed skill cast did not drain.");
		final long savedSp = _player.getSp(), savedExp = _player.getExp(); final int objectId = _player.getObjectId();
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Earned native progress did not save.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Earned native progress did not reload.");
		_player = World.getInstance().getPlayer(objectId);
		PhantomAssertions.assertTrue(_player != null && (_player.getSkillLevel(3) == 2) && (_player.getSp() == savedSp) && (_player.getExp() == savedExp), "Native identity/earned EXP/SP/paid rank changed on reload.");
		context.record("career058.persisted", "same object=" + objectId + "; skill3:2; EXP=" + savedExp + "; SP=" + savedSp + "; native save/reload; profession20/40/76, subclass quest and noblesse NOT covered");
	}

	private PhantomNavigationPoint prepareLearningField059(PhantomTestContext context)
	{
		final var geo = GeoEngine.getInstance();
		final var origin = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
		final var trainers = _query.snapshot().spawnFactsByNpc().entrySet().stream().filter(entry -> { final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && "Trainer".equals(npc.getType()) && npc.canTeach(_player.getLearningClass()); }).flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.pointKind() == SpawnPointKind.EXACT && spawn.instanceId() == 0).sorted(Comparator.comparingDouble((SpawnFact spawn) -> Math.hypot(spawn.x() - origin.getX(), spawn.y() - origin.getY())).thenComparingInt(SpawnFact::npcId)).limit(8).toList();
		int candidates = 0, noGeo = 0, peace = 0, water = 0, corridor = 0, mobs = 0;
		for (var trainer : trainers)
		{
			for (int radius : new int[] {1200,2400,3600,4800,6000})
			{
				for (int direction = 0; direction < 8; direction++)
				{
					candidates++;
					final double angle = direction * Math.PI / 4; final int x = trainer.x() + (int) Math.round(radius * Math.cos(angle)), y = trainer.y() + (int) Math.round(radius * Math.sin(angle)), z = geo.getHeight(x, y, trainer.z());
					if (!geo.hasGeo(x,y)) { noGeo++; continue; }
					if (ZoneManager.getInstance().getZone(x,y,z,PeaceZone.class) != null) { peace++; continue; }
					boolean dry = true;
					for (int dx : new int[] {-320,0,320}) { for (int dy : new int[] {-320,0,320}) { final int surface = geo.getHeight(x+dx,y+dy,z); if (ZoneManager.getInstance().getZone(x+dx,y+dy,surface,WaterZone.class) != null) { dry = false; } } }
					if (!dry) { water++; continue; }
					if (!geo.canMoveToTarget(x,y,z,x+40,y,z,0)) { corridor++; continue; }
					placeInstanceInput058(_player, x, y, z);
					if (_player.isInsideZone(ZoneId.WATER)) { water++; continue; }
					if (World.getInstance().getVisibleObjects(_player, Monster.class).stream().anyMatch(monster -> !monster.isDead() && _player.calculateDistance3D(monster) < 1800)) { mobs++; continue; }
					PhantomAssertions.assertTrue(!_player.isInCombat() && !_player.isDead() && _player.getCurrentHp() >= _player.getMaxHp() * .95, "INVALID_INPUT: native interference during premeasurement field preparation.");
					context.record("career059.declaredField", "PREMEASUREMENT TEST input only; finite candidates=" + candidates + "/320; stock Trainer=" + trainer.npcId() + '@' + trainer.x() + ',' + trainer.y() + ',' + trainer.z() + "; outsidePeace field=" + x + ',' + y + ',' + z + "; native local Geo corridor; no unrelated Monster1800; actual outbound/return path proof belongs to product Navigation; no fixture position writes after first earned kill");
					return new PhantomNavigationPoint(x,y,z,0);
				}
			}
		}
		context.record("career059.fieldInputRejected","trainers="+trainers.stream().map(value->value.npcId()+"@"+value.x()+','+value.y()+','+value.z()).toList()+"; candidates="+candidates+"; noGeo="+noGeo+"; peace="+peace+"; water="+water+"; corridor="+corridor+"; mobs="+mobs);
		throw new AssertionError("BLOCKED_NATIVE_FIELD_INPUT: no bounded teachable Trainer/field corridor without unrelated stock mobs.");
	}

	private void runLearningJourney059(PhantomTestContext context, PhantomNavigationPoint field) throws Exception
	{
		if (Boolean.getBoolean("phantom.test.autonomousLearning060")) { runAutonomousLearningJourney060(context, field); return; }
		context.record("career059.nativeRouteInput", "actor="+_player.getX()+','+_player.getY()+','+_player.getZ()+"; field="+field+"; water="+_player.isInsideZone(ZoneId.WATER)+"; sourceTrainers="+_query.snapshot().spawnFactsByNpc().entrySet().stream().filter(entry->{final var npc=NpcData.getInstance().getTemplate(entry.getKey());return npc!=null&&"Trainer".equals(npc.getType())&&npc.canTeach(_player.getLearningClass());}).flatMap(entry->entry.getValue().stream()).filter(value->value.pointKind()==SpawnPointKind.EXACT&&value.instanceId()==0).sorted(Comparator.comparingDouble((SpawnFact value)->_player.calculateDistance3D(value.x(),value.y(),value.z()))).limit(3).toList()+"; visibleTrainers="+World.getInstance().getVisibleObjects(_player,Npc.class).stream().filter(npc->"Trainer".equals(npc.getTemplate().getType())&&npc.getTemplate().canTeach(_player.getLearningClass())).sorted(Comparator.comparingDouble((Npc npc)->_player.calculateDistance3D(npc))).limit(3).map(npc->npc.getId()+"@"+npc.getX()+','+npc.getY()+','+npc.getZ()).toList());
		final var goals = new PhantomGoalStateStore(_repository);
		final var goal = new PhantomGoal(58005833059L, "skill.learn", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("skill", "3:2"), 1, 0, null, List.of(), null, "learning.explicit", 500, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8), Map.of("class.id",0L,"class.index",0L,"field.x",(long)field.x(),"field.y",(long)field.y(),"field.z",(long)field.z(),"field.instance",0L), "task059.earned_learning", 0);
		goals.insert(_profile.profileId(), goal);
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics()); PhantomAssertions.assertTrue(navigation.start(), "Learning journey Navigation did not start.");
		var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals,_materialization,progression,navigation,()->_query,profile->true);
		PhantomAssertions.assertTrue(journey.start(), "Learning journey did not start.");
		final long initialSp = _player.getSp(); boolean reconstructed = false; double walked = 0, maximumFieldDistance = 0; int lastX = _player.getX(), lastY = _player.getY(), lastZ = _player.getZ(), reportedRank = 0; long nextReport = 0;
		try
		{
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.CANCELLED, journey.advance(_profile.profileId(),goal,()->true,System.nanoTime()).type(), "Cancelled journey issued native work.");
			final var candidates = new org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
			final var candidate = candidates.snapshot().getFirst();
			PhantomAssertions.assertTrue(candidate.supportedGoalTypes().contains("skill.learn"), "Actual registered candidate lost explicit learning intent.");
			final var plan = candidate.planFactory().create(new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext(_profile.profileId(),goal,org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet.empty(),PhantomActivityState.ACTIVE,System.nanoTime(),1));
			var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
			org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult result = null;
			while (System.nanoTime() < deadline)
			{
				PhantomAssertions.assertTrue(!_player.isDead() && !_player.isTeleporting(), "Measured native learning journey died or teleported.");
				final long now = System.nanoTime();
				result = handlers.snapshot().get("learning.journey").execute(new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(_profile.profileId(),goal,plan,plan.steps().getFirst(),PhantomActivityState.ACTIVE,now,1,()->false));
				walked += Math.sqrt(Math.pow(_player.getX()-lastX,2)+Math.pow(_player.getY()-lastY,2)+Math.pow(_player.getZ()-lastZ,2)); lastX=_player.getX();lastY=_player.getY();lastZ=_player.getZ(); maximumFieldDistance=Math.max(maximumFieldDistance,_player.calculateDistance3D(field.x(),field.y(),field.z()));
				if (now >= nextReport || reportedRank != _player.getSkillLevel(3)) { reportedRank = _player.getSkillLevel(3); System.out.println("LEARN059_PROGRESS phase="+result+"; xyz="+lastX+','+lastY+','+lastZ+"; rank="+reportedRank+"; SP="+_player.getSp()+"; walked="+walked);System.out.flush();nextReport=now+TimeUnit.SECONDS.toNanos(10); }
				if (result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.FAIL_GOAL) { context.record("career059.terminalNativeFacts", "xyz="+lastX+','+lastY+','+lastZ+"; moving="+_player.isMoving()+"; sitting="+_player.isSitting()+"; water="+_player.isInsideZone(ZoneId.WATER)+"; trainerFacts="+World.getInstance().getVisibleObjects(_player,Npc.class).stream().filter(npc->"Trainer".equals(npc.getTemplate().getType())&&npc.getTemplate().canTeach(_player.getLearningClass())).sorted(Comparator.comparingDouble((Npc npc)->_player.calculateDistance3D(npc))).limit(3).map(npc->npc.getId()+"@"+npc.getX()+','+npc.getY()+','+npc.getZ()+"/distance="+_player.calculateDistance3D(npc)+"/interact="+npc.canInteract(_player)).toList()); }
				PhantomAssertions.assertTrue(result.type() != org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.FAIL_GOAL && result.type() != org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.CANCELLED, "Registered learning owner rejected the declared native journey: "+result);
				if (!reconstructed && _player.getSkillLevel(3) == 1)
				{
					PhantomAssertions.assertEquals(initialSp-50,_player.getSp(),"First paid rank spent incorrect SP.");
					journey.beginStop();PhantomAssertions.assertTrue(journey.finishStop(),"Learning adapter did not drain after first committed rank.");
					journey=new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals,_materialization,progression,navigation,()->_query,profile->true);PhantomAssertions.assertTrue(journey.start(),"Reconstructed learning adapter did not start.");
					handlers=new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry();journey.registerHandlers(handlers);handlers.seal();reconstructed=true;
					context.record("career059.adapterResume","same durable explicit goal/native paid rank1; adapter reconstructed without Player save/reload/position or SP writes; next rank must charge once");
				}
				if (result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL) { break; }
				Thread.sleep(100);
			}
			PhantomAssertions.assertTrue(result != null && result.type() == org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL && reconstructed && walked > 500 && maximumFieldDistance > 300 && _player.getSkillLevel(3)==2 && !_player.isInsideZone(ZoneId.PEACE) && _player.calculateDistance3D(field.x(),field.y(),field.z()) <= navigation.arrivalRadius(), "Full native field/trainer/return journey did not complete its declared bounds.");
			PhantomAssertions.assertEquals(initialSp-100,_player.getSp(),"Native two-rank journey cost differs or reconstruction charged twice.");
			for(int repeat=0;repeat<3;repeat++){PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type.COMPLETE_GOAL,journey.advance(_profile.profileId(),goal,()->false,System.nanoTime()).type(),"Terminal journey retry did not preserve native learned truth.");}
			PhantomAssertions.assertEquals(initialSp-100,_player.getSp(),"Terminal journey replay consumed SP.");
			PhantomAssertions.assertTrue(waitFor(()->!_player.isMoving(),10_000),"Native return movement did not settle.");
			context.record("career059.journeyResult","actual registered candidate/handler; native walk distance="+walked+"; max field separation="+maximumFieldDistance+"; current rank3:2; native SP="+initialSp+"->"+_player.getSp()+"; adapter reconstruction/terminal retry no extra charge; returned outsidePeace; no manual positioning inside measured episode; generic auto-career goal generation/professions/Noblesse not covered");
		}
		finally
		{
			journey.beginStop();PhantomAssertions.assertTrue(journey.finishStop(),"Learning journey did not drain.");
			progression.beginStop();PhantomAssertions.assertTrue(progression.finishStop(),"Learning progression did not drain.");
			navigation.beginStop();final long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!navigation.finishStop()&&System.nanoTime()<stop){Thread.sleep(20);}PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPED,navigation.snapshot().state(),"Learning Navigation did not drain.");
		}
	}

	private void runAutonomousLearningJourney060(PhantomTestContext context, PhantomNavigationPoint field) throws Exception
	{
		final var goals = new PhantomGoalStateStore(_repository);
		final var rootBefore = goals.load(_profile.profileId()).orElseThrow();
		PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.GOAL_TYPE, rootBefore.goal().goalType(), "Autonomous input is not an existing farm root.");
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		PhantomAssertions.assertTrue(navigation.start(), "Autonomous native navigation did not start.");
		var journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> true);
		journey.enableAutonomous(_repository, (profile, pauseDeadline) -> true, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults());
		PhantomAssertions.assertTrue(journey.start(), "Autonomous native journey did not start.");
		var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
		var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
		var engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1); engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(_profile.profileId()), "Autonomous farm did not attach to actual Decision.");
		final long initialSp = _player.getSp();
		boolean reconstructed = false;
		PhantomNavigationPoint currentReturn = field;
		long sequence = 0, nextReport = 0;
		double walked = 0;
		int lastX = _player.getX(), lastY = _player.getY(), lastZ = _player.getZ();
		final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
		try
		{
			while (System.nanoTime() < deadline)
			{
				final long now = System.nanoTime();
				PhantomAssertions.assertTrue(!_player.isDead() && !_player.isTeleporting(), "Autonomous paid WALK died or used a fixture teleport.");
				engine.accept(new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem(_profile.profileId(), PhantomActivityState.ACTIVE, 1, ++sequence, now, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel.NORMAL));
				walked += Math.sqrt(Math.pow(_player.getX() - lastX, 2) + Math.pow(_player.getY() - lastY, 2) + Math.pow(_player.getZ() - lastZ, 2));
				lastX = _player.getX(); lastY = _player.getY(); lastZ = _player.getZ();
				final var intent = journey.automaticIntent(_profile.profileId()).orElse(null);
				if (intent != null && intent.status() == PhantomGoalStatus.ACTIVE) { currentReturn = org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney.request(intent).orElseThrow().field(); }
				PhantomAssertions.assertTrue(intent == null || intent.status() != PhantomGoalStatus.FAILED, "Autonomous native intent failed: " + intent);
				if (now >= nextReport)
				{
					System.out.println("LIFE060_LEARNING xyz=" + lastX + ',' + lastY + ',' + lastZ + "; SP=" + _player.getSp() + "; rank=" + _player.getSkillLevel(3) + "; intent=" + intent + "; decision=" + engine.find(_profile.profileId()));
					System.out.flush(); nextReport = now + TimeUnit.SECONDS.toNanos(15);
				}
				if (!reconstructed && _player.getSkillLevel(3) == 1)
				{
					PhantomAssertions.assertEquals(initialSp - 50, _player.getSp(), "Autonomous first native lesson cost differs.");
					engine.beginStop();
					PhantomAssertions.assertTrue(engine.finishStop(), "Autonomous Decision did not stop before native save.");
					journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "Autonomous journey did not stop before save.");
					final int objectId = _player.getObjectId();
					PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Autonomous native save failed.");
					PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Autonomous native reload failed.");
					_player = World.getInstance().getPlayer(objectId);
					PhantomAssertions.assertTrue(_player != null && _player.getSkillLevel(3) == 1 && _player.getSp() == initialSp - 50, "Native save/reload changed the paid lesson.");
					journey = new org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney(goals, _materialization, progression, navigation, () -> _query, profile -> true);
					journey.enableAutonomous(_repository, (profile, pauseDeadline) -> true, org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy.defaults());
					PhantomAssertions.assertTrue(journey.start(), "Reconstructed autonomous journey did not start.");
					PhantomAssertions.assertEquals(intent, journey.automaticIntent(_profile.profileId()).orElseThrow(), "Durable autonomous intent changed on reconstruction.");
					candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
					handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
					engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1); engine.start();
					PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(_profile.profileId()), "Reconstructed autonomous farm did not attach.");
					reconstructed = true;
					lastX = _player.getX(); lastY = _player.getY(); lastZ = _player.getZ();
					context.record("career060.resume", "native save/reload same identity/root/earned SP/paid rank; durable child intent unchanged; no fixture goal or coordinate rewrite");
				}
				if (reconstructed && _player.getSkillLevel(3) == 2 && intent != null && intent.status() == PhantomGoalStatus.COMPLETED && _player.calculateDistance3D(currentReturn.x(), currentReturn.y(), currentReturn.z()) <= navigation.arrivalRadius()) { break; }
				Thread.sleep(100);
			}
			PhantomAssertions.assertTrue(reconstructed && _player.getSkillLevel(3) == 2 && walked > 500 && !_player.isInsideZone(ZoneId.PEACE) && _player.calculateDistance3D(currentReturn.x(), currentReturn.y(), currentReturn.z()) <= navigation.arrivalRadius(), "Autonomous native earned/paid/WALK/return/resume chain did not complete its own product-selected field.");
			PhantomAssertions.assertEquals(initialSp - 100, _player.getSp(), "Autonomous lessons or resume charged incorrect SP.");
			PhantomAssertions.assertEquals(rootBefore, goals.load(_profile.profileId()).orElseThrow(), "Autonomous child overwrote the native farm/catchup root.");
			PhantomAssertions.assertTrue(waitFor(() -> !_player.isMoving(), 10_000), "Autonomous native return did not settle.");
			context.record("career060.autonomousResult", "existing Decision selected both paid native lessons without test-assigned skill.learn; native earned SP=" + initialSp + "->" + _player.getSp() + "; walked=" + walked + "; same farm root retained; saved child resumed without duplicate fee; full ordinary farm relocation/GK and JVM resume not claimed by this focused case");
		}
		finally
		{
			engine.beginStop(); if (engine.snapshot().state() != PhantomDecisionEngine.State.STOPPED) { engine.finishStop(); }
			PhantomAssertions.assertEquals(PhantomDecisionEngine.State.STOPPED, engine.snapshot().state(), "Autonomous Decision did not drain.");
			journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "Autonomous learning owner did not drain.");
			progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Autonomous progression did not drain.");
			navigation.beginStop(); final long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < stop) { Thread.sleep(20); }
			PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPED, navigation.snapshot().state(), "Autonomous Navigation did not drain.");
		}
	}

	private Monster spawnCareerStock058() throws ReflectiveOperationException
	{
		final Spawn spawn = new Spawn(NpcData.getInstance().getTemplate(20035));
		spawn.setXYZ(_player.getX() + 40, _player.getY(), _player.getZ());
		final Monster target = (Monster) spawn.doSpawn(false); spawn.stopRespawn();
		PhantomAssertions.assertTrue(target != null, "Native stock career Spawn failed."); _worldFixtures.add(target);
		PhantomAssertions.assertTrue(target.getCurrentHp() >= target.getMaxHp() * .99 && !target.isRaid(), "Career target is not a full stock normal Monster.");
		return target;
	}

	private void prepareInstanceActor058(Player actor, int classId, int level)
	{
		PhantomAssertions.assertTrue(!actor.isDead() && (actor.getInstanceId() == 0), "New instance fixture boundary requires a living ordinary-world actor.");
		actor.setBaseClass(classId); actor.setPlayerClass(classId);
		actor.getStat().setExp(actor.getStat().getExpForLevel(level)); actor.getStat().setLevel((byte) level);
		for (var learn : _progression.classSkillLearns(classId).stream().filter(learn -> learn.minimumCharacterLevel() <= level).sorted(Comparator.comparingInt(learn -> learn.skillLevel())).toList())
		{
			final Skill skill = SkillData.getInstance().getSkill(learn.skillId(), learn.skillLevel());
			if ((skill != null) && (actor.getSkillLevel(learn.skillId()) < learn.skillLevel())) { actor.addSkill(skill, true); }
		}
		actor.addSkill(SkillData.getInstance().getSkill(239, 1), true);
		for (int itemId : new int[] {129, 352, 2378})
		{
			final Item item = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this);
			PhantomAssertions.assertTrue(item != null, "Declared D-grade fixture item missing."); actor.getInventory().equipItem(item);
		}
		actor.getInventory().addItem(ItemProcessType.REWARD, 1463, 3000, null, this);
		actor.refreshExpertisePenalty(); actor.refreshOverloaded(); actor.setInvul(false); actor.setOnlineStatus(true, false); actor.setRunning(); actor.standUp();
		actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
	}

	static void placeInstanceInput058(Player actor, int x, int y, int z)
	{
		actor.decayMe(); actor.setXYZInvisible(x, y, z); actor.spawnMe(); actor.revalidateZone(true);
	}

	private static void acknowledgeInstance058(List<Player> actors)
	{
		for (Player actor : actors) { if (actor.isTeleporting()) { actor.onTeleported(); } actor.revalidateZone(true); }
	}

	private void testKamaloka058(PhantomTestContext context) throws Exception
	{
		testKamaloka058(context, false);
	}

	private void testKamaloka058(PhantomTestContext context, boolean full059) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final List<Player> actors = new ArrayList<>(List.of(_player));
		final List<Long> profiles = new ArrayList<>(List.of(_profile.profileId()));
		for (int slot = 1; slot <= (full059 ? 4 : 2); slot++)
		{
			final var fixture = full059 ? _environment.createCampaign059InstanceCompanion(context, slot) : _environment.createCampaign058Companion(context, slot);
			final var profile = _repository.create(fixture.objectId()); _additionalProfiles058.add(profile);
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(profile.profileId()).status(), "Native instance companion did not materialize.");
			actors.add(World.getInstance().getPlayer(fixture.objectId())); profiles.add(profile.profileId());
		}
		final Player human = ensureObserver(); actors.add(human);
		for (Player actor : actors) { prepareInstanceActor058(actor, actor == human ? (full059 ? 15 : 11) : actor == _player ? 4 : 1, 28); }
		if (full059)
		{
			for (Player actor : actors)
			{
				for (int itemId : new int[] {2411,913,850,850,881,881}) { final Item item = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this); PhantomAssertions.assertTrue(item != null, "Declared legal D-grade instance equipment missing."); actor.getInventory().equipItem(item); }
				if (actor == human)
				{
					for (int itemId : new int[] {188,437,470,2450}) { final Item item = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this); PhantomAssertions.assertTrue(item != null, "Declared TEST Cleric equipment missing."); actor.getInventory().equipItem(item); }
					actor.getInventory().addItem(ItemProcessType.REWARD, 3948, 1000, null, this); actor.addAutoSoulShot(3948);
				}
				else { actor.getInventory().addItem(ItemProcessType.REWARD, 1463, 3000, null, this); }
				if (actor == _player) { final Item shield = actor.getInventory().addItem(ItemProcessType.REWARD, 2493, 1, null, this); PhantomAssertions.assertTrue(shield != null, "Declared Knight shield missing."); actor.getInventory().equipItem(shield); }
				actor.addAutoSoulShot(1463); actor.refreshExpertisePenalty(); actor.refreshOverloaded();
				PhantomAssertions.assertTrue(!actor.isOverloaded() && !actor.isMovementDisabled(), "INVALID_INPUT: legal Kamaloka inventory prevents native movement.");
				actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
			}
		}
		final var captain = _query.snapshot().spawnFactsByNpc().getOrDefault(30332, List.of()).stream().filter(point -> point.pointKind() == SpawnPointKind.EXACT).findFirst().orElseThrow();
		int offset = 0; for (Player actor : actors) { placeInstanceInput058(actor, captain.x() + offset, captain.y(), captain.z()); offset += 20; }
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(party);
		for (Player actor : actors.subList(1, actors.size())) { actor.joinParty(party); }
		final var catalog = org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.load(Path.of("data/phantoms/quests/high-five-supported-content-v1.xml"), Path.of("."));
		final var backend = new org.l2jmobius.gameserver.phantoms.questinstance.L2jPhantomQuestInstanceBackend(_materialization, catalog, () -> _query);
		final var content = catalog.content("instance.kamaloka-57").orElseThrow(); final var enter = content.step("enter").orElseThrow();
		context.record("instance058.kamaloka.input", full059 ? "NEW independent six-member059: Knight4 +four Warrior1 +TEST Cleric15, all28/native class-tree<=28; legal unenchanted D SwordOfRevolution/Brigandine/Elven jewellery, Knight shield, Cleric GhostStaff/Mithril robe; native SS6000 per Phantom/BSS1000 human; full stock18554; existing product service owns entry and combat; fight<=28min within stock script30min, natural closing5min; human native heal/buff/attack only; no mid-scene fixture writes" : "TEST mixed canonical Party=" + actors.stream().map(Player::getObjectId).toList() + "; classes4/1/1/11 level28; native class-tree<=28/D sword129/body352/2378/1463x3000 each; primary Phantom leader; human-like Cleric uses native1011 when HP<=70; full stock18554; fight<=600s in native sessions<=120s, native closing<=320s; no rescue/HP edit/clock change");
		if (full059)
		{
			PhantomAssertions.assertTrue(actors.size() == 6 && actors.stream().allMatch(actor -> !actor.isGM() && actor.getLevel() == 28 && actor.getInstanceId() == 0), "Declared six-member legal Kamaloka input differs.");
			context.record("instance059.kamaloka.roster", actors.stream().map(actor -> actor.getObjectId() + ":class" + actor.getActiveClass() + ":level" + actor.getLevel() + ":weapon" + actor.getActiveWeaponItem().getId() + ":HP" + actor.getCurrentHp() + ":MP" + actor.getCurrentMp() + ":load" + actor.getCurrentLoad()).toList() + "; prior058 class11 is native WIZARD, not CLERIC15; old FAIL retained; this independent input uses actual Cleric current heal" + human.getSkillLevel(1011));
		}
		int instanceId = 0;
		PhantomQuestInstanceService service059 = null;
		PhantomProgressionService progression059 = null;
		PhantomNavigationService navigation059 = null;
		final PhantomGoalStateStore goals059 = new PhantomGoalStateStore(_repository);
		try
		{
			final var stale = backend.invoke(_profile.profileId(), content, enter, "0".repeat(64));
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionStatus.STALE, stale.status(), "Stale native entry fingerprint mutated membership.");
			final var wrongLeader = backend.invoke(profiles.get(1), content, enter, backend.observe(profiles.get(1), content).fingerprint());
			PhantomAssertions.assertTrue(actors.stream().allMatch(actor -> actor.getInstanceId() == 0), "Non-leader entered native Kamaloka.");
			context.record("instance058.kamaloka.negative", "stale=" + stale.status() + "; nonLeader=" + wrongLeader.status() + "; all instance0");
			if (full059)
			{
				progression059 = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression059.start();
				navigation059 = new PhantomNavigationService(new PhantomMetrics()); PhantomAssertions.assertTrue(navigation059.start(), "Kamaloka navigation owner did not start.");
				final var acquisitions059 = PhantomAcquisitionQuestCatalog.load(Path.of("data/phantoms/acquisition/high-five-quest-collection-v1.xml"), Path.of("data/scripts"));
				service059 = new PhantomQuestInstanceService(goals059, catalog, acquisitions059, backend, _combat, navigation059, progression059, noSignals());
				PhantomAssertions.assertTrue(service059.start(), "Existing product instance service did not start.");
				for (int index = 0; index < profiles.size(); index++) { final long profile = profiles.get(index); goals059.insert(profile, new PhantomGoal(58005831059L + index, PhantomQuestInstanceService.INSTANCE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)), new PhantomDomainRef(PhantomQuestInstanceService.TARGET_NAMESPACE, content.id()), 1, 0, null, List.of(), null, "instance.play", 500, 0, 0, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(35), Map.of(), "task059.native_kamaloka", 0)); }
				final long entryDeadline059 = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
				while (_player.getInstanceId() == 0 && System.nanoTime() < entryDeadline059) { final var goal = goals059.load(_profile.profileId()).orElseThrow().goal(); service059.advance(_profile.profileId(), goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), () -> false); acknowledgeInstance058(actors); Thread.sleep(100); }
			}
			final var entered = full059 ? "product-owner" : backend.invoke(_profile.profileId(), content, enter, backend.observe(_profile.profileId(), content).fingerprint());
			instanceId = _player.getInstanceId(); final int exactInstance = instanceId;
			PhantomAssertions.assertTrue(instanceId > 0 && actors.stream().allMatch(actor -> actor.getInstanceId() == exactInstance), "Native Kamaloka did not admit the complete mixed Party: " + entered);
			acknowledgeInstance058(actors);
			final var instance = InstanceManager.getInstance().getInstance(instanceId);
			PhantomAssertions.assertTrue(instance != null && actors.stream().allMatch(actor -> instance.containsPlayer(actor.getObjectId())), "Native instance lost exact Player identities.");
			final Monster boss = (Monster) instance.getNpcs().stream().filter(npc -> npc.getId() == 18554).findFirst().orElseThrow();
			final double hp = boss.getCurrentHp(); final long exp = actors.stream().mapToLong(Player::getExp).sum();
			final long shots = actors.stream().mapToLong(actor -> actor.getInventory().getInventoryItemCount(1463, -1)).sum();
			PhantomAssertions.assertTrue(hp >= boss.getMaxHp() * .99, "Kamaloka input boss was not full stock HP.");
			if (!full059) { for (int index = 0; index < profiles.size(); index++) { PhantomAssertions.assertTrue(_combat.startContentSession(new PhantomCombatRequest(profiles.get(index), boss.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, false, PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS, () -> false), "instance058.kamaloka." + profiles.get(index)).accepted(), "Kamaloka common combat admission failed."); } }
			final Skill heal = human.getKnownSkill(1011); PhantomAssertions.assertTrue(heal != null, "Declared native TEST Cleric has no learned heal.");
			final long fightStart059 = System.nanoTime(); long nextProgress059 = fightStart059; boolean feasible059 = false;
			final long deadline = fightStart059 + (full059 ? TimeUnit.MINUTES.toNanos(28) : TimeUnit.SECONDS.toNanos(600));
			while (!boss.isDead() && (System.nanoTime() < deadline) && actors.stream().noneMatch(Player::isDead))
			{
				for (long profile : profiles)
				{
					if (full059) { final var goal = goals059.load(profile).orElseThrow().goal(); service059.advance(profile, goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), () -> false); continue; }
					final var session = _combat.find(profile).orElseThrow();
					if (session.result().terminal())
					{
						PhantomAssertions.assertEquals(PhantomCombatResult.TIMEOUT, session.result(), "Native Kamaloka combat stopped for a reason other than its regular bounded session duration.");
						if (_combat.consumeTerminal(profile).isEmpty()) { continue; }
						final var continuation = _combat.startContentSession(new PhantomCombatRequest(profile, boss.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, false, PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS, () -> false), "instance058.kamaloka." + profile);
						PhantomAssertions.assertTrue(continuation.accepted(), "Regular bounded Kamaloka combat continuation failed: " + continuation.status());
					}
				}
				if (!human.isCastingNow() && (full059 || !human.isAttackingNow()))
				{
					final Player injured = actors.stream().filter(actor -> actor.getCurrentHp() < actor.getMaxHp() * .70).min(Comparator.comparingDouble(actor -> actor.getCurrentHp() / actor.getMaxHp())).orElse(null);
					if (injured != null) { if (full059) { human.abortAttack(); } human.setTarget(injured); human.useMagic(heal, false, false); }
					else if (full059)
					{
						Player recipient = null; Skill missing = null;
						for (int skillId : new int[] {1040,1068}) { final Skill buff = human.getKnownSkill(skillId); if (buff == null) { continue; } final Player candidate = actors.stream().filter(actor -> PhantomSupportEffectAuthority.status(actor, buff, 45) != PhantomSupportEffectAuthority.Status.HEALTHY).findFirst().orElse(null); if (candidate != null) { recipient = candidate; missing = buff; break; } }
						if (recipient != null) { human.abortAttack(); human.setTarget(recipient); human.useMagic(missing, false, false); }
						else if (!human.isAttackingNow()) { human.setTarget(boss); human.getAI().setIntention(Intention.ATTACK, boss); }
					}
				}
				if (full059)
				{
					final long now = System.nanoTime(); final double seconds = (now - fightStart059) / 1_000_000_000.0; final double netDps = (hp - boss.getCurrentHp()) / Math.max(1, seconds);
					if (now >= nextProgress059) { System.out.println("KAMA059_PROGRESS seconds=" + (int) seconds + "; bossHP=" + boss.getCurrentHp() + '/' + boss.getMaxHp() + "; netDPS=" + netDps + "; ETA=" + (netDps > 0 ? boss.getCurrentHp() / netDps : -1) + "; members=" + actors.stream().map(actor -> actor.getObjectId() + ":HP" + (int) actor.getCurrentHp() + ":MP" + (int) actor.getCurrentMp()).toList() + "; owner=" + service059.snapshot()); System.out.flush(); nextProgress059 = now + TimeUnit.SECONDS.toNanos(60); }
					if (!feasible059 && seconds >= 90) { feasible059 = true; context.record("instance059.kamaloka.feasibility", "actualDPS=" + netDps + "; bossRemaining=" + boss.getCurrentHp() + "; nativeInstanceRemainingMs=" + (instance.getInstanceEndTime() - System.currentTimeMillis())); PhantomAssertions.assertTrue(netDps > 0 && boss.getCurrentHp() / netDps < TimeUnit.MINUTES.toSeconds(25), "BLOCKED_NATIVE_CAPACITY: full six-member stock Kamaloka cannot finish inside declared28min/native30min window."); }
				}
				Thread.sleep(100);
			}
			context.record("instance058.kamaloka.fight", "instance=" + instanceId + "; bossHP=" + hp + "->" + boss.getCurrentHp() + "; alive=" + actors.stream().map(actor -> !actor.isDead()).toList() + "; MP=" + human.getCurrentMp() + "; damage=" + actors.subList(0, 3).stream().map(actor -> boss.getAggroList().get(actor) == null ? 0 : boss.getAggroList().get(actor).getDamage()).toList());
			PhantomAssertions.assertTrue(boss.isDead() && actors.stream().noneMatch(Player::isDead), "Full stock Kamaloka fight did not succeed with the declared mixed roster.");
			PhantomAssertions.assertTrue(waitFor(() -> actors.stream().allMatch(actor -> InstanceManager.getInstance().getInstanceTime(actor.getObjectId(), 57) > System.currentTimeMillis()), 10_000), "Native Kamaloka did not assign reuse to every exact mixed member.");
			PhantomAssertions.assertTrue(actors.stream().mapToLong(Player::getExp).sum() > exp, "Native Kamaloka stock reward missing.");
			PhantomAssertions.assertTrue(actors.stream().mapToLong(actor -> actor.getInventory().getInventoryItemCount(1463, -1)).sum() < shots, "Native D shots were not consumed.");
			context.record("instance058.kamaloka.kill", "native death/reuse/reward; exp=" + exp + "->" + actors.stream().mapToLong(Player::getExp).sum() + "; naturalClosingRemainingMs=" + (instance.getInstanceEndTime() - System.currentTimeMillis()));
			if (full059)
			{
				final long earned059 = actors.stream().mapToLong(Player::getExp).sum();
				for (int repeat = 0; repeat < 3; repeat++) { for (long profile : profiles) { final var goal = goals059.load(profile).orElseThrow().goal(); service059.advance(profile, goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), () -> false); } }
				PhantomAssertions.assertEquals(earned059, actors.stream().mapToLong(Player::getExp).sum(), "Product terminal retry duplicated native Kamaloka rewards.");
				long nextClosing059 = 0; final long closingDeadline059 = System.nanoTime() + TimeUnit.SECONDS.toNanos(320);
				while (actors.stream().anyMatch(actor -> actor.getInstanceId() != 0) && System.nanoTime() < closingDeadline059) { if (System.nanoTime() >= nextClosing059) { System.out.println("KAMA059_NATURAL_CLOSING remainingMs=" + (instance.getInstanceEndTime() - System.currentTimeMillis()) + "; sameParty=" + party.getMemberCount()); System.out.flush(); nextClosing059 = System.nanoTime() + TimeUnit.SECONDS.toNanos(60); } Thread.sleep(250); }
				PhantomAssertions.assertTrue(actors.stream().allMatch(actor -> actor.getInstanceId() == 0 && actor.getParty() == party) && party.getMemberCount() == 6, "Stock closing lost one of the exact six native identities or its Party.");
			}
			else { PhantomAssertions.assertTrue(waitFor(() -> actors.stream().allMatch(actor -> actor.getInstanceId() == 0), 320_000), "Native Kamaloka closing did not return every mixed member."); }
			acknowledgeInstance058(actors);
			for (Player actor : actors) { actor.storeMe(); }
			if (full059)
			{
				final var goal = goals059.load(_profile.profileId()).orElseThrow().goal();
				final var completedService059 = service059;
				PhantomAssertions.assertTrue(waitFor(() -> completedService059.advance(_profile.profileId(), goal.goalId(), goal.revision(), PhantomActivityState.ACTIVE, System.nanoTime(), () -> false).status() == PhantomQuestInstanceService.Status.COMPLETE, 10_000), "Product owner did not settle its owned combat and preserve native reuse after closing.");
			}
			else { backend.invoke(_profile.profileId(), content, enter, backend.observe(_profile.profileId(), content).fingerprint()); }
			PhantomAssertions.assertTrue(actors.stream().allMatch(actor -> actor.getInstanceId() == 0), "Native cooldown retry opened another Kamaloka.");
			context.record("instance058.kamaloka.result", "natural closing complete; all identities/Party preserved; native saved; cooldown retry no new instance; death/logout/reboot remain untested");
		}
		finally
		{
			if (service059 != null) { service059.beginStop(); PhantomAssertions.assertTrue(service059.finishStop(), "Kamaloka product owner did not drain."); }
			if (progression059 != null) { progression059.beginStop(); PhantomAssertions.assertTrue(progression059.finishStop(), "Kamaloka progression owner did not drain."); }
			if (navigation059 != null) { navigation059.beginStop(); PhantomAssertions.assertTrue(navigation059.finishStop(), "Kamaloka navigation owner did not drain."); }
			final var unfinished = InstanceManager.getInstance().getInstance(instanceId);
			context.record("instance058.kamaloka.endState", "boss=" + (unfinished == null ? "closed" : unfinished.getNpcs().stream().filter(npc -> npc.getId() == 18554).map(npc -> npc.getCurrentHp() + "/" + npc.getMaxHp()).toList()) + "; actors=" + actors.stream().map(actor -> actor.getObjectId() + ":hp=" + actor.getCurrentHp() + ":mp=" + actor.getCurrentMp() + ":exp=" + actor.getExp()).toList() + "; sessions=" + profiles.stream().map(_combat::find).toList());
			for (long profile : profiles) { _combat.cancel(profile); }
			consumeTerminal();
			for (Player actor : actors) { if (actor.isInParty()) { PartyInvitationService.getInstance().leave(actor); } }
			if ((instanceId > 0) && (InstanceManager.getInstance().getInstance(instanceId) != null)) { InstanceManager.getInstance().destroyInstance(instanceId); acknowledgeInstance058(actors); context.record("instance058.kamaloka.cleanup", "owned native instance cleanup and native teleport acknowledgement after incomplete episode; not natural closing proof"); }
		}
	}

	private void testRift058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final boolean legal059 = Boolean.getBoolean("phantom.test.riftFlow059");
		final Player human = ensureObserver(); final List<Player> actors = new ArrayList<>(List.of(_player));
		final List<Long> profiles059 = new ArrayList<>(List.of(_profile.profileId()));
		if (legal059)
		{
			for (int slot = 1; slot <= 2; slot++) { final var fixture = _environment.createCampaign058Companion(context, slot); final var profile = _repository.create(fixture.objectId()); _additionalProfiles058.add(profile); profiles059.add(profile.profileId()); PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(profile.profileId()).status(), "Rift059 companion did not materialize."); actors.add(World.getInstance().getPlayer(fixture.objectId())); }
		}
		actors.add(human);
		if (legal059)
		{
			for (Player actor : actors)
			{
				prepareInstanceActor058(actor, actor == human ? 15 : actor == _player ? 4 : 1, 30);
				for (int itemId : new int[] {2411,913,850,850,881,881}) { final Item item = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this); PhantomAssertions.assertTrue(item != null, "Rift059 legal D equipment unavailable."); actor.getInventory().equipItem(item); }
				if (actor == human) { for (int itemId : new int[] {188,437,470,2450}) { final Item item = actor.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, this); PhantomAssertions.assertTrue(item != null, "Rift059 native Cleric equipment unavailable."); actor.getInventory().equipItem(item); } actor.getInventory().addItem(ItemProcessType.REWARD, 3948, 1000, null, this); actor.addAutoSoulShot(3948); }
				if (actor == _player) { final Item shield = actor.getInventory().addItem(ItemProcessType.REWARD, 2493, 1, null, this); actor.getInventory().equipItem(shield); }
				actor.addAutoSoulShot(1463); actor.refreshExpertisePenalty(); actor.refreshOverloaded();
				PhantomAssertions.assertTrue(!actor.isGM() && !actor.isOverloaded() && !actor.isMovementDisabled(), "INVALID_INPUT: Rift059 requires legal mobile actors.");
				actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
			}
		}
		else { prepareInstanceActor058(_player, 1, 43); prepareInstanceActor058(human, 11, 43); }
		final var manager = DimensionalRiftManager.getInstance(); final var entry = manager.entryReadiness((byte) 1);
		PhantomAssertions.assertTrue(entry.supported() && entry.entryCapacityAvailable() && entry.minimumPartySize() <= 2, "INVALID_INPUT: current native Rift1 entry is unavailable for the declared pair.");
		for (Player actor : actors) { placeInstanceInput058(actor, entry.destinationX(), entry.destinationY(), entry.destinationZ()); }
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(party); for (Player actor : actors.subList(1, actors.size())) { actor.joinParty(party); }
		final Npc npc = World.getInstance().getVisibleObjects(_player, Npc.class).stream().filter(value -> (value.getId() == 31488) && manager.checkIfInPeaceZone(value.getX(), value.getY(), value.getZ())).min(Comparator.comparingDouble(value -> _player.calculateDistance3D(value))).orElseThrow(() -> new AssertionError("INVALID_INPUT: no native Rift Recruit in waiting room."));
		for (Player actor : actors) { placeInstanceInput058(actor, npc.getX() + 20, npc.getY(), npc.getZ()); }
		PhantomAssertions.assertTrue(npc.canInteract(_player) && manager.checkIfInPeaceZone(_player.getX(), _player.getY(), _player.getZ()), "INVALID_INPUT: native Rift Recruit approach is illegal.");
		final var handler = BypassHandler.getInstance().getHandler("enterrift"); PhantomAssertions.assertTrue(handler != null, "Native Rift bypass handler absent.");
		context.record("instance058.rift.input", "TEST roster=" + actors.stream().map(actor -> actor.getObjectId() + ":class" + actor.getActiveClass() + ":level" + actor.getLevel()).toList() + "; native Rift1/minParty=" + entry.minimumPartySize() + "; native class-tree/D gear; normal room instance0; stock fees7079; one native room kill<=90s then canonical leave; no clock/rate changes");
		if (legal059) { context.record("rift059.declaredInput", "Independent four-member native control: Knight4/two Warrior1/TEST Cleric15, all30/current learned class tree; unenchanted D equipment and native grade-matched shots; no overlevel EXP bypass; native handler owns entry/fees, common Combat owns three Phantom sessions, human native heal/buff; this does NOT prove product readiness-to-entry consent or a full timed Rift cycle. Old058 raw retained."); }
		try
		{
			handler.onCommand("enterrift 1", human, npc);
			PhantomAssertions.assertFalse(party.isInDimensionalRift(), "Non-leader entered Rift.");
			handler.onCommand("enterrift 1", _player, npc);
			PhantomAssertions.assertFalse(party.isInDimensionalRift(), "Missing fragments entered Rift.");
			// Preparation for the separate positive entry, before enrollment; not post-outcome rescue.
			for (Player actor : actors) { actor.getInventory().addItem(ItemProcessType.REWARD, 7079, entry.entryItemCount() + 1, null, this); }
			handler.onCommand("enterrift 1", _player, npc); acknowledgeInstance058(actors);
			PhantomAssertions.assertTrue(party.isInDimensionalRift(), "Native guarded Rift entry failed.");
			final var rift = party.getDimensionalRift(); final var room = manager.getRoom(rift.getType(), rift.getCurrentRoom());
			PhantomAssertions.assertTrue(actors.stream().allMatch(actor -> (actor.getInstanceId() == 0) && room.checkIfInZone(actor.getX(), actor.getY(), actor.getZ()) && (actor.getInventory().getInventoryItemCount(7079, -1) == 1)), "Native Rift room/fee/identity mismatch.");
			PhantomAssertions.assertTrue(waitFor(() -> room.getSpawns().stream().anyMatch(spawn -> spawn.getLastSpawn() instanceof Monster monster && !monster.isRaid()), 20_000), "Native Rift spawn delay produced no non-raid stock room monsters; room=" + rift.getCurrentRoom());
			context.record("instance058.rift.roomSpawns", "room=" + rift.getCurrentRoom() + "; spawns=" + room.getSpawns().stream().map(spawn -> spawn.getLastSpawn() == null ? "pending" : spawn.getLastSpawn().getId() + ":" + spawn.getLastSpawn().getClass().getSimpleName() + ":raid=" + spawn.getLastSpawn().isRaid()).toList());
			final Monster target = room.getSpawns().stream().map(Spawn::getLastSpawn).filter(value -> value instanceof Monster monster && !monster.isRaid()).map(value -> (Monster) value).sorted(Comparator.comparingInt(Monster::getId)).findFirst().orElseThrow();
			context.record("instance058.rift.target", "stock=" + target.getId() + "; nativeType=" + target.getClass().getSimpleName() + "; knowledge=" + _query.findNpc(target.getId()));
			PhantomAssertions.assertEquals(NpcKind.MONSTER, _query.findNpc(target.getId()).orElseThrow().kind(), "Native RiftInvader was not classified as its actual Monster base.");
			final long exp = _player.getExp(); final double hp = target.getCurrentHp();
			final Map<Integer, Long> expBefore059 = new java.util.HashMap<>(); for (Player actor : actors) { expBefore059.put(actor.getObjectId(), actor.getExp()); }
			for (long profile : profiles059) { PhantomAssertions.assertTrue(_combat.startContentSession(new PhantomCombatRequest(profile, target.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, false, 90_000, () -> false), "instance058.rift.combat").accepted(), "Native Rift common combat refused its same-room stock target."); }
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90); final Skill heal = human.getKnownSkill(1011);
			while (!target.isDead() && actors.stream().noneMatch(Player::isDead) && (System.nanoTime() < deadline))
			{
				if (legal059 && !human.isCastingNow()) { final Player injured = actors.stream().filter(actor -> actor.getCurrentHp() < actor.getMaxHp() * .75).min(Comparator.comparingDouble(actor -> actor.getCurrentHp() / actor.getMaxHp())).orElse(null); if (injured != null) { human.abortAttack(); human.setTarget(injured); human.useMagic(heal, false, false); } }
				else if ((_player.getCurrentHp() < _player.getMaxHp() * .70) && !human.isCastingNow()) { human.setTarget(_player); human.useMagic(heal, false, false); }
				Thread.sleep(100);
			}
			context.record("instance058.rift.fight", "type=" + rift.getType() + "; room=" + rift.getCurrentRoom() + "; stock=" + target.getId() + "; hp=" + hp + "->" + target.getCurrentHp() + "; exp=" + exp + "->" + _player.getExp() + "; alive=" + !_player.isDead() + '/' + !human.isDead());
			PhantomAssertions.assertTrue(target.isDead() && (_player.getExp() > exp) && !_player.isDead() && !human.isDead(), "Declared native Rift room combat/reward failed.");
			if (legal059) { PhantomAssertions.assertTrue(actors.stream().allMatch(actor -> !actor.isDead() && actor.getExp() > expBefore059.get(actor.getObjectId())), "Rift059 legal roster did not earn native EXP for every exact member."); context.record("rift059.nativeRewards", actors.stream().map(actor -> actor.getObjectId() + ":EXP" + expBefore059.get(actor.getObjectId()) + "->" + actor.getExp()).toList()); }
			for (long profile : profiles059) { _combat.cancel(profile); } consumeTerminal();
			PartyInvitationService.getInstance().leave(human); if (legal059) { for (Player actor : actors.subList(1, actors.size() - 1)) { if (actor.isInParty()) { PartyInvitationService.getInstance().leave(actor); } } } acknowledgeInstance058(actors);
			context.record("rift059.leaveBoundary", "nativePartyRift=" + party.isInDimensionalRift() + "; roomOccupied=" + room.isPartyInside() + "; actors=" + actors.stream().map(actor -> actor.getObjectId() + ":managed=" + actor.isNativeWorkManaged() + ":instance=" + actor.getInstanceId() + ":waiting=" + manager.checkIfInPeaceZone(actor.getX(), actor.getY(), actor.getZ()) + ":xyz=" + actor.getX() + ',' + actor.getY() + ',' + actor.getZ()).toList());
			PhantomAssertions.assertTrue(!party.isInDimensionalRift() && actors.stream().allMatch(actor -> manager.checkIfInPeaceZone(actor.getX(), actor.getY(), actor.getZ())) && !room.isPartyInside(), "Last human leave did not release native Rift room/return every exact actor.");
			context.record("instance058.rift.result", "native room instance0 (not instanced dungeon); exact fee debit; stock kill/reward; canonical leave→native return/room released; auto-readiness-to-entry consent and full timed/death/logout cycle remain open");
		}
		finally
		{
			for (long profile : profiles059) { _combat.cancel(profile); } consumeTerminal();
			for (Player actor : actors) { if (actor.isInParty()) { PartyInvitationService.getInstance().leave(actor); } }
		}
	}

	private void testRaidTrigger059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final var fixture = _environment.createCampaign058Companion(context, 1);
		final var profile = _repository.create(fixture.objectId()); _additionalProfiles058.add(profile);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(profile.profileId()).status(), "Trigger recipient did not materialize.");
		final Player receiver = World.getInstance().getPlayer(fixture.objectId());
		final Player human = ensureObserver();
		final Skill skill = SkillData.getInstance().getSkill(1068, 3);
		for (Player caster : List.of(_player, human))
		{
			caster.setBaseClass(98); caster.setPlayerClass(98);
			caster.getStat().setExp(caster.getStat().getExpForLevel(79)); caster.getStat().setLevel((byte) 79);
			caster.addSkill(skill, true); caster.setInvul(false); caster.setOnlineStatus(true, false);
			caster.setCurrentHp(caster.getMaxHp()); caster.setCurrentMp(caster.getMaxMp());
		}
		final var origin = PlayerTemplateData.getInstance().getTemplate(0).getCreationPoint();
		for (Player actor : List.of(_player, receiver, human)) { placeInstanceInput058(actor, origin.getX() + 20, origin.getY(), origin.getZ()); }
		final Party party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(party); receiver.joinParty(party); human.joinParty(party);
		context.record("raid059.trigger.input", "Independent native effect boundary: managed caster+new managed recipient, existing earned parent captures caster only; learned Might1068:3/native target semantics; ordinary-human reverse control; no direct effect/owner mutation");
		try
		{
			PhantomAssertions.assertTrue(_player.isNativeWorkManaged() && receiver.isNativeWorkManaged() && !human.isNativeWorkManaged(), "Managed/ordinary trigger input identity differs.");
			final var outcome = org.l2jmobius.gameserver.model.actor.PlayerNativeWork.runAtNativeWriteBoundary(_player, List.of(_player), "test059-trigger-origin", () -> _player.makeTriggerCast(skill, receiver));
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.actor.PlayerNativeWork.NativeBoundaryOutcome.EXECUTED, outcome, "Native trigger origin was not admitted.");
			PhantomAssertions.assertTrue(waitFor(() -> PhantomSupportEffectAuthority.status(receiver, skill, 0) == PhantomSupportEffectAuthority.Status.HEALTHY, 3000), "Native trigger lost the newly resolved managed recipient inside earned work.");
			PhantomAssertions.assertTrue(_player.getNativeWorkOwner().nativeObservationHealthy() && receiver.getNativeWorkOwner().nativeObservationHealthy(), "Valid synchronous trigger poisoned an owned lifetime.");
			human.makeTriggerCast(skill, _player);
			PhantomAssertions.assertTrue(waitFor(() -> PhantomSupportEffectAuthority.status(_player, skill, 0) == PhantomSupportEffectAuthority.Status.HEALTHY, 3000), "Ordinary-human native trigger control lost its effect.");
			context.record("raid059.trigger.result", "managed native effect=HEALTHY; exact owners healthy; ordinary-human native effect=HEALTHY; captured-earned fence retained; no SP/reward transaction bypass");
		}
		finally { for (Player actor : List.of(human, receiver, _player)) { if (actor.isInParty()) { PartyInvitationService.getInstance().leave(actor); } } }
	}

	private void testRaidFull059(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final RaidBoss boss = RaidBossSpawnManager.getInstance().getBosses().get(25199);
		PhantomAssertions.assertTrue((boss != null) && boss.isSpawned() && !boss.isDead() && !boss.isInvul() && (boss.getInstanceId() == 0) && (boss.getCurrentHp() >= boss.getMaxHp() * .99), "INVALID_INPUT: exact stock25199 is unavailable or not full HP.");
		PhantomAssertions.assertTrue(waitFor(() -> boss.getMinionList().getSpawnedMinions().size() == 4, 15_000), "INVALID_INPUT: declared four stock raid minions did not spawn naturally.");
		final var initialMinions = List.copyOf(boss.getMinionList().getSpawnedMinions());
		PhantomAssertions.assertTrue(initialMinions.stream().allMatch(value -> !value.isDead() && value.getCurrentHp() >= value.getMaxHp() * .99) && initialMinions.stream().filter(value -> value.getId() == 25200).count() == 3 && initialMinions.stream().filter(value -> value.getId() == 25201).count() == 1, "INVALID_INPUT: full stock minion denominator differs from three25200/one25201.");
		final var geo = GeoEngine.getInstance();
		final int centreX = boss.getX(), centreY = boss.getY(), centreZ = geo.getHeight(boss.getX(), boss.getY(), boss.getZ());
		final int nearZ = geo.getHeight(boss.getX() + 100, boss.getY(), boss.getZ());
		Location stagingInput = null;
		for (int radius : new int[] {1800,1200,900,600,300})
		{
		for (int direction = 0; direction < 8; direction++)
		{
			final double angle = Math.PI * direction / 4;
			final int x = centreX + (int) Math.round(Math.cos(angle) * radius), y = centreY + (int) Math.round(Math.sin(angle) * radius), z = geo.getHeight(x, y, centreZ);
			final boolean direct = geo.hasGeo(x, y) && geo.canMoveToTarget(x, y, z, centreX, centreY, centreZ, 0);
			System.out.println("RAID059_STAGING radius=" + radius + "; direction=" + direction + "; xyz=" + x + ',' + y + ',' + z + "; boss=" + boss.getX() + ',' + boss.getY() + ',' + boss.getZ() + "; nearZ=" + nearZ + "; direct=" + direct + "; water=" + (ZoneManager.getInstance().getZone(x,y,z,WaterZone.class) != null));
			if (direct && (stagingInput == null || Boolean.getBoolean("phantom.test.raidMinionGuard059"))) { stagingInput = new Location(x, y, z); }
		}
		}
		PhantomAssertions.assertTrue(stagingInput != null, "BLOCKED_NATIVE_STAGING_INPUT: no bounded300..1800-radius approach reaches the unchanged stock raid by native movement.");
		if (Boolean.getBoolean("phantom.test.raidStagingOnly059")) { context.record("raid059.provenStaging", stagingInput); return; }
		final List<Player> actors = new ArrayList<>(List.of(_player));
		final List<Long> profileIds = new ArrayList<>(List.of(_profile.profileId()));
		for (int slot = 1; slot <= 7; slot++)
		{
			final var fixture = _environment.createCampaign059RaidCompanion(context, slot);
			final var profile = _repository.create(fixture.objectId()); _additionalProfiles058.add(profile); profileIds.add(profile.profileId());
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(profile.profileId()).status(), "Raid059 actor did not materialize.");
			actors.add(World.getInstance().getPlayer(fixture.objectId()));
		}
		final Player human = ensureObserver();
		prepareCompanionCombatAtLevel058(_player, 90, 2500, 368, 79, 6373, 6374, 6375, 6376, 6377, 6378);
		prepareCompanionCombatAtLevel058(actors.get(1), 88, 6580, 345, 79, 6373, 6374, 6375, 6376, 6378);
		prepareCompanionCombatAtLevel058(actors.get(2), 92, 7575, 343, 79, 6379, 6380, 6381, 6382);
		PhantomAssertions.assertTrue(actors.get(2).getInventory().addItem(ItemProcessType.REWARD, 1345, 2000, null, this) != null, "Raid059 S arrows are unavailable.");
		prepareCompanionCombatAtLevel058(actors.get(3), 97, 6579, 1335, 79, 6383, 6384, 6385, 6386);
		prepareCompanionCombatAtLevel058(actors.get(4), 105, 6579, 1013, 79, 6383, 6384, 6385, 6386);
		prepareCompanionCombatAtLevel058(actors.get(5), 88, 6580, 345, 79, 6373, 6374, 6375, 6376, 6378);
		prepareCompanionCombatAtLevel058(actors.get(6), 98, 6579, 0, 79, 6383, 6384, 6385, 6386);
		prepareCompanionCombatAtLevel058(actors.get(7), 107, 6580, 0, 79, 6373, 6374, 6375, 6376, 6378);
		prepareCompanionCombatAtLevel058(human, 88, 6580, 345, Boolean.getBoolean("phantom.test.raidLevelGuard059") ? 80 : 79, 6373, 6374, 6375, 6376, 6378);
		final List<Player> roster = new ArrayList<>(actors); roster.add(human);
		for (Player actor : roster)
		{
			for (int jewelId : new int[] {920,858,858,889,889}) { final Item jewel = actor.getInventory().addItem(ItemProcessType.REWARD, jewelId, 1, null, this); PhantomAssertions.assertTrue(jewel != null, "Declared stock Tateossian jewellery is unavailable."); actor.getInventory().equipItem(jewel); }
			final int physicalShot = actor == _player ? 1466 : 1467, magicShot = actor == _player ? 3951 : 3952;
			PhantomAssertions.assertTrue(actor.getInventory().addItem(ItemProcessType.REWARD, physicalShot, 3000, null, this) != null && actor.getInventory().addItem(ItemProcessType.REWARD, magicShot, 1000, null, this) != null, "Declared grade-matched raid shots are unavailable.");
			PhantomAssertions.assertTrue(actor.getInventory().addItem(ItemProcessType.REWARD, 3031, 300, null, this) != null, "Declared native Spirit Ore is unavailable.");
			actor.addAutoSoulShot(magicShot);
			actor.addAutoSoulShot(actor == _player ? 1466 : 1467);
			for (var learn : _progression.classSkillLearns(actor.getActiveClass()).stream().filter(value -> value.minimumCharacterLevel() <= actor.getLevel()).sorted(Comparator.comparingInt(value -> value.skillLevel())).toList())
			{
				final Skill skill = SkillData.getInstance().getSkill(learn.skillId(), learn.skillLevel());
				if ((skill != null) && (actor.getSkillLevel(learn.skillId()) < learn.skillLevel())) { actor.addSkill(skill, true); }
			}
			actor.setInvul(false); actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
			if (actor == actors.get(3)) { actor.setCurrentMp(actor.getMaxMp() * .30); }
			// Same native spawn cell is the existing companion-composition input pattern; no player collision is fabricated.
			final int actorX = stagingInput.getX(), actorY = stagingInput.getY(), actorZ = stagingInput.getZ();
			PhantomAssertions.assertTrue(geo.canMoveToTarget(actorX, actorY, actorZ, centreX, centreY, centreZ, 0), "INVALID_INPUT: one declared raid member lacks a native approach before enrollment.");
			actor.decayMe(); actor.setXYZInvisible(actorX, actorY, actorZ); actor.spawnMe(); actor.revalidateZone(true); actor.setRunning(); actor.standUp();
		}
		for (Player actor : roster) { actor.refreshOverloaded(); context.record("raid059.nativeLoad." + actor.getObjectId(), "load=" + actor.getCurrentLoad() + "; max=" + actor.getMaxLoad() + "; penalty=" + actor.getWeightPenalty() + "; overloaded=" + actor.isOverloaded() + "; movementDisabled=" + actor.isMovementDisabled()); }
		PhantomAssertions.assertTrue(roster.stream().noneMatch(value -> value.isOverloaded() || value.isMovementDisabled()), "INVALID_INPUT: declared raid inventory or native state prevents ordinary movement before enrollment.");
		human.addAutoSoulShot(1467);
		final Party nativeParty = new Party(_player, PartyDistributionType.FINDERS_KEEPERS); _player.setParty(nativeParty);
		for (Player actor : roster.subList(1, roster.size())) { actor.joinParty(nativeParty); }
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults()); progression.start();
		try
		{
			final var backend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
			final var authority = new org.l2jmobius.gameserver.phantoms.raid.L2jPhantomRaidAuthority();
			final var readiness = new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidReadinessService(_query, backend, authority);
			final var ready = readiness.assess(MemberRef.phantom(_profile.profileId(), _player.getObjectId()), "raid.25199");
			context.record("raid059.preflight", "INDEPENDENT mobile full9/9 legal79 input (one human80 ONLY for guard case); stock boss=" + boss.getId() + "; fullHP=" + boss.getMaxHp() + "; minions=" + initialMinions.stream().map(value -> value.getId() + ":HP" + value.getCurrentHp() + '/' + value.getMaxHp()).toList() + "; minions25200/25201=71/native curse unchanged/max79; TEST roster=" + roster.stream().map(value -> value.getObjectId() + ":class" + value.getActiveClass() + ":level" + value.getLevel() + ":MDef" + value.getMDef(boss, null)).toList() + "; stock A/S gear +Tateossian jewellery/current class-tree skills<=actual level; one native grade SS3000+BSS1000 per member/S arrows2000/SpiritOre300/native autoSS+BSS; native load/movement guard before enrollment; human native assist tank target; readiness=" + ready.status() + '/' + ready.reason() + "; fixed45min encounter deadline; first90s combined boss+minion DPS/MP feasibility; no rescue/refill/HP edit/roster change during battle");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus.GROUP_READY, ready.status(), "Current native raid readiness cannot admit the declared high-level encounter.");
			context.record("raid059.recoveryInput", "Independent episode: healer starts with30% native MP before enrollment; TEST human assists tank and accepts only an actual native resurrection request; death keeps full9 denominator; final9 living regroup required; no direct revival/resource writes during measurement");
			if (Boolean.getBoolean("phantom.test.raidMinionGuard059"))
			{
				final Monster child = initialMinions.stream().min(Comparator.comparingInt(Monster::getObjectId)).orElseThrow();
				final var token = (org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken) () -> false;
				final String authorityHash = "59".repeat(32);
				final double childHp = child.getCurrentHp(), actorMp = _player.getCurrentMp();
				final long shots = _player.getInventory().getInventoryItemCount(1466, -1);
				final var foreign = new org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest(_profile.profileId(), child.getObjectId(), 0, child.getInstanceId(), org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind.RAID, NpcKind.MONSTER, authorityHash, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 20_000, token, boss.getObjectId() + 1_000_000, boss.getId());
				PhantomAssertions.assertEquals(PhantomCombatService.StartStatus.REJECTED_TARGET, _combat.startRaidSession(foreign).status(), "Foreign native boss object authorized a raid minion.");
				final var ordinary = _combat.startSession(new PhantomCombatRequest(_profile.profileId(), child.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, false, 20_000, token));
				PhantomAssertions.assertFalse(ordinary.accepted(), "Generic FARM acquired a stock raid minion.");
				final var exact = new org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest(_profile.profileId(), child.getObjectId(), 0, child.getInstanceId(), org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind.RAID, NpcKind.MONSTER, authorityHash, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 20_000, token, boss.getObjectId(), boss.getId());
				try
				{
					final var started = _combat.startRaidSession(exact);
					PhantomAssertions.assertTrue(started.accepted(), "Exact live native parent rejected its own minion: " + started.status());
					final boolean damaged = waitFor(() -> child.getCurrentHp() < childHp - 1 || _player.isDead(), 15_000);
					context.record("raid059.nativeMinion", "parent=" + boss.getId() + '/' + boss.getObjectId() + "; child=" + child.getId() + '/' + child.getObjectId() + "; HP=" + childHp + "->" + child.getCurrentHp() + "; actorMP=" + actorMp + "->" + _player.getCurrentMp() + "; A-shots=" + shots + "->" + _player.getInventory().getInventoryItemCount(1466, -1) + "; ordinary=" + ordinary.status() + "; current=" + _combat.find(_profile.profileId()));
					PhantomAssertions.assertTrue(damaged && child.getCurrentHp() < childHp - 1 && !_player.isDead() && (_player.getCurrentMp() < actorMp || _player.getInventory().getInventoryItemCount(1466, -1) < shots) && !boss.isDead(), "Exact native minion action lacks real damage/cost or fabricated boss death.");
				}
				finally { _combat.cancel(_profile.profileId()); consumeTerminal(); }
				return;
			}
			final var navigation = new PhantomNavigationService(new PhantomMetrics()); navigation.start();
			final var routes = new PhantomPartyRouteCoordinator(navigation, _combat);
			final var goals = new PhantomGoalStateStore(_repository);
			final long goalId = 58005830059L, deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(45);
			goals.insert(_profile.profileId(), new PhantomGoal(goalId, org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.PREPARE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("raid.content", "raid.25199"), 1, 0, null, List.of(), null, "raid.prepare", 500, 0, 0, deadline, Map.of(), "task059.native_full_raid", 0));
			final var assembly = new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService(goals, readiness, new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidRecruitmentService(readiness, backend), backend, authority, () -> _topology, routes, System::currentTimeMillis, System::nanoTime);
			final var runtime = new org.l2jmobius.gameserver.phantoms.raid.L2jPhantomRaidAttemptRuntime(_combat, new PhantomPartyTactics(_combat, backend), routes, () -> _topology.snapshot().canonicalHash(), System::nanoTime);
			final var attempts = new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService(goals, assembly, readiness, backend, authority, new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterCatalog(), org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry.getInstance(), runtime, System::currentTimeMillis, System::nanoTime, () -> org.l2jmobius.gameserver.config.NpcConfig.RAID_DISABLE_CURSE);
			final Map<Integer,String> lastDamage = new java.util.concurrent.ConcurrentHashMap<>();
			final Set<Integer> observedDeaths = new java.util.LinkedHashSet<>();
			final var deathCause = new java.util.concurrent.atomic.AtomicReference<String>("");
			final Map<Player,List<org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener>> observations = new java.util.LinkedHashMap<>();
			for (Player actor : roster)
			{
				final var damageListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(actor, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DAMAGE_RECEIVED, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageReceived event) ->
				{
					if (event.getTarget() == actor) { lastDamage.put(actor.getObjectId(), "source=" + (event.getAttacker() instanceof Npc npc ? npc.getId() + "/" + npc.getObjectId() : event.getAttacker() == null ? "none" : Integer.toString(event.getAttacker().getObjectId())) + "; skill=" + (event.getSkill() == null ? "physical" : event.getSkill().getId() + ":" + event.getSkill().getLevel()) + "; damage=" + event.getDamage()); }
				}, this);
				final var deathListener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(actor, org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DEATH, (org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath event) ->
				{
					if (event.getTarget() == actor) { deathCause.compareAndSet("", "target=" + actor.getObjectId() + "; source=" + (event.getAttacker() instanceof Npc npc ? npc.getId() + "/" + npc.getObjectId() : event.getAttacker() == null ? "none" : Integer.toString(event.getAttacker().getObjectId()))); }
				}, this);
				actor.addListener(damageListener); actor.addListener(deathListener); observations.put(actor, List.of(damageListener, deathListener));
			}
			context.record("raid059.bardCapabilities", backend.capabilities(MemberRef.phantom(profileIds.get(7), actors.get(7).getObjectId()), _player.getObjectId()).stream().filter(value -> Set.of("combat.buff","combat.dance").contains(value.capabilityKey())).map(value -> value.capabilityKey() + ':' + value.actionSkillId() + ':' + value.actionSkillLevel() + ':' + value.readinessReason()).toList());
			try
			{
				var assembled = assembly.advance(_profile.profileId(), goalId, 0);
				final long gatherDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
				long nextGatherProgress = 0;
				while ((assembled.readyReceipt() == null) && !assembled.status().terminal() && (System.nanoTime() < gatherDeadline))
				{
					if (human.getAI().getIntention() != Intention.FOLLOW) { human.getAI().setIntention(Intention.FOLLOW, _player); }
					assembled = assembly.advance(_profile.profileId(), goalId, 0);
					if (System.nanoTime() >= nextGatherProgress) { System.out.println("RAID059_GATHER status=" + assembled.status() + "; routes=" + routes.snapshot() + "; XYZ=" + roster.stream().map(value -> value.getObjectId() + ":" + value.getLocation()).toList()); nextGatherProgress = System.nanoTime() + TimeUnit.SECONDS.toNanos(15); }
					Thread.sleep(25);
				}
				human.getAI().setIntention(Intention.IDLE);
				context.record("raid059.assembly", assembled.status() + "/" + assembled.reasonKey() + "; native members=" + nativeParty.getMemberCount());
				PhantomAssertions.assertTrue(assembled.readyReceipt() != null && nativeParty.getMemberCount() == 9, "Full9 native assembly did not produce an exact ready receipt.");
				final double hpBefore = boss.getCurrentHp(); final long expBefore = roster.stream().mapToLong(Player::getExp).sum();
				final long tankExpBefore = _player.getExp();
				final double totalHpBefore = hpBefore + initialMinions.stream().mapToDouble(Monster::getCurrentHp).sum();
				context.record("raid059.supportPreparation", "NEW independent episode: current native learned MajorHeal1401/Bishop, Might1068/Shield1040/Haste1086/Prophet and physical dances271/274/275/BladeDancer; existing support owner prepares<=120s before offense, incoming damage starts defense immediately; TEST human waits for first accepted combat; no direct effects/cost/HP/MP writes inside measurement");
				final long[] damage = new long[actors.size()]; final boolean[] spentMp = new boolean[actors.size()];
				final double[] previousMp = actors.stream().mapToDouble(Player::getCurrentMp).toArray();
				double previousTankHp = _player.getCurrentHp(), previousTankMax = _player.getMaxHp(), previousHealerMaxMp = actors.get(3).getMaxMp();
				boolean healed = false, recharged = false, aggroCast = false, buffed = false, danced = false; long aggroSince = 0, heldAggro = 0, humanDamage = 0;
				long battleStart = 0; long nextProgress = System.nanoTime(); boolean feasibilityChecked = false;
				var result = attempts.advance(_profile.profileId(), goalId, 0);
				if (Boolean.getBoolean("phantom.test.raidLevelGuard059"))
				{
					PhantomAssertions.assertFalse(org.l2jmobius.gameserver.config.NpcConfig.RAID_DISABLE_CURSE, "Native curse guard input was disabled before the episode.");
					context.record("raid059.levelGuard", "one native human80/mixed9; result=" + result.status() + '/' + result.reasonKey() + "; acceptedCombat=" + _combat.metrics().sessionsAccepted());
					PhantomAssertions.assertTrue(result.status() == org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AttemptStatus.ABORTED && result.reasonKey().equals("raid.attempt.member_level_rejected") && _combat.metrics().sessionsAccepted() == 0 && boss.getCurrentHp() == hpBefore && roster.stream().noneMatch(Player::isDead), "Above-minion-ceiling force reached native combat or used a false refusal."); return;
				}
				while (!result.status().terminal() && (System.currentTimeMillis() < deadline))
				{
					if (human.isReviveRequested()) { human.reviveAnswer(1); context.record("raid059.humanNativeResurrection", "Explicit TEST response to native healer request; same human identity/roster; no direct HP or MP write"); }
					final Monster assistedTarget = _player.getTarget() instanceof Monster target && !target.isDead() && target.getInstanceId() == human.getInstanceId() ? target : boss;
					if (_combat.metrics().sessionsAccepted() > 0 && !human.isDead() && !assistedTarget.isDead() && !human.isCastingNow())
					{
						if (human.getTarget() != assistedTarget) { human.abortAttack(); human.setTarget(assistedTarget); }
						if (!human.isAttackingNow()) { human.getAI().setIntention(Intention.ATTACK, assistedTarget); }
					}
					result = attempts.advance(_profile.profileId(), goalId, 0);
					final long now = System.nanoTime();
					if (battleStart == 0 && _combat.metrics().sessionsAccepted() > 0) { battleStart = now; context.record("raid059.combatClock", "first accepted native combat after bounded support preparation; whole episode45min deadline unchanged"); }
					final double seconds = battleStart == 0 ? 0 : (now - battleStart) / 1_000_000_000.0;
					final double previousHealerMp = previousMp[3];
					for (int index = 0; index < actors.size(); index++)
					{
						final var actor = actors.get(index); final var contribution = boss.getAggroList().get(actor);
						damage[index] = Math.max(damage[index], contribution == null ? 0 : contribution.getDamage()); spentMp[index] |= actor.getCurrentMp() + 1 < previousMp[index]; previousMp[index] = actor.getCurrentMp();
					}
					final var humanContribution = boss.getAggroList().get(human); humanDamage = Math.max(humanDamage, humanContribution == null ? 0 : humanContribution.getDamage());
					aggroCast |= _player.getLastSkillCast() != null && _player.getLastSkillCast().getId() == 28;
					if (boss.getMostHated() == _player && boss.getTarget() == _player) { if (aggroSince == 0) { aggroSince = now; } heldAggro = Math.max(heldAggro, now - aggroSince); } else { aggroSince = 0; }
					healed |= spentMp[3] && _player.getMaxHp() == previousTankMax && _player.getCurrentHp() > previousTankHp + 100 && actors.get(3).getLastSkillCast() != null && Set.of(1217,1335,1401).contains(actors.get(3).getLastSkillCast().getId());
					recharged |= spentMp[4] && actors.get(3).getMaxMp() == previousHealerMaxMp && actors.get(3).getCurrentMp() > previousHealerMp + 50 && actors.get(4).getLastSkillCast() != null && actors.get(4).getLastSkillCast().getId() == 1013;
					previousTankHp = _player.getCurrentHp(); previousTankMax = _player.getMaxHp(); previousHealerMaxMp = actors.get(3).getMaxMp();
					final Skill buff = actors.get(6).getKnownSkill(1356);
					buffed |= spentMp[6] && buff != null && org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.status(_player, buff, 0) == org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status.HEALTHY;
					for (int danceId : new int[] {271,365}) { final Skill dance = actors.get(7).getKnownSkill(danceId); danced |= spentMp[7] && dance != null && org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.status(_player, dance, 0) == org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status.HEALTHY; }
					final double netDps = (hpBefore - boss.getCurrentHp()) / Math.max(1, seconds);
					final var liveMinions = boss.getMinionList().getSpawnedMinions().stream().filter(value -> !value.isDead()).toList();
					final double totalHp = boss.getCurrentHp() + liveMinions.stream().mapToDouble(Monster::getCurrentHp).sum();
					final double combinedDps = (totalHpBefore - totalHp) / Math.max(1, seconds);
					if (now >= nextProgress)
					{
						System.out.println("RAID059_PROGRESS seconds=" + (int) seconds + "; status=" + result.status() + '/' + result.reasonKey() + "; bossHP=" + boss.getCurrentHp() + '/' + boss.getMaxHp() + "; netDPS=" + netDps + "; ETA=" + (netDps > 0 ? boss.getCurrentHp() / netDps : -1) + "; members=" + roster.stream().map(value -> value.getObjectId() + ":HP" + (int) value.getCurrentHp() + ":MP" + (int) value.getCurrentMp() + ":distance" + (int) value.calculateDistance3D(boss)).toList() + "; claims=" + profileIds.stream().map(id -> _combat.find(id).map(value -> id + ":" + value.phase() + '/' + value.result()).orElse(id + ":none")).toList());
						System.out.println("RAID059_MINIONS remaining=" + liveMinions.stream().map(value -> value.getId() + ":" + value.getObjectId() + ":HP" + (int) value.getCurrentHp()).toList() + "; combinedHP=" + totalHp + '/' + totalHpBefore + "; combinedDPS=" + combinedDps + "; combinedETA=" + (combinedDps > 0 ? totalHp / combinedDps : -1));
						System.out.flush(); nextProgress = now + TimeUnit.SECONDS.toNanos(30);
					}
					for (Player actor : roster)
					{
						if (actor.isDead() && observedDeaths.add(actor.getObjectId())) { context.record("raid059.deathCause." + actor.getObjectId(), deathCause.get() + "; lastNativeDamage=" + lastDamage + "; minions=" + liveMinions.stream().map(value -> value.getId() + ":HP" + (int) value.getCurrentHp()).toList() + "; exactClaims=" + profileIds.stream().map(id -> _combat.find(id).map(value -> id + ":" + value.targetObjectId() + ':' + value.result()).orElse(id + ":none")).toList()); }
					}
					if (!feasibilityChecked && seconds >= 90)
					{
						feasibilityChecked = true;
						context.record("raid059.feasibility90", "bossNetDPS=" + netDps + "; combinedDPS=" + combinedDps + "; remainingBossAndMinionHP=" + totalHp + "; MP=" + roster.stream().map(value -> value.getObjectId() + ":" + value.getCurrentMp() + '/' + value.getMaxMp()).toList());
						PhantomAssertions.assertTrue(combinedDps > 0 && (totalHp / combinedDps) < TimeUnit.MINUTES.toSeconds(40), "BLOCKED_NATIVE_CAPACITY: measured full9 boss+minion DPS/regen cannot finish within the predeclared45min bound.");
					}
					Thread.sleep(25);
				}
				context.record("raid059.terminal", result.status() + "/" + result.reasonKey() + "; dead=" + boss.isDead() + "; receipt=" + result.terminalReceipt() + "; groupEXP=" + expBefore + "->" + roster.stream().mapToLong(Player::getExp).sum());
				context.record("raid059.nativeRoles", "healed=" + healed + "; recharge=" + recharged + "; buff=" + buffed + "; dance=" + danced + "; aggroCast=" + aggroCast + "; heldMs=" + TimeUnit.NANOSECONDS.toMillis(heldAggro) + "; damage=" + java.util.Arrays.toString(damage) + "; humanDamage=" + humanDamage + "; native MPcost=" + java.util.Arrays.toString(spentMp));
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AttemptStatus.VICTORY, result.status(), "Declared native encounter did not reach stock victory/reward boundary.");
				PhantomAssertions.assertTrue(boss.isDead() && result.terminalReceipt().actualTargetDeathObserved() && result.terminalReceipt().nativeLootComplete() && _player.getExp() > tankExpBefore, "Native death/stock reward/loot pipeline was not confirmed for the same living tank.");
				PhantomAssertions.assertTrue(healed && aggroCast && heldAggro >= TimeUnit.SECONDS.toNanos(3) && damage[1] > 0 && damage[2] > 0 && damage[5] > 0 && humanDamage > 0, "Full encounter lacks actual native tank/physical roles/human contribution/healing effect and cost.");
				PhantomAssertions.assertTrue(buffed && danced && recharged, "Declared native buffer/dancer/recharger did not produce actual support effects/cost.");
				final long expAfter = roster.stream().mapToLong(Player::getExp).sum();
				for (int repeat = 0; repeat < 5; repeat++) { PhantomAssertions.assertEquals(result.terminalReceipt(), attempts.advance(_profile.profileId(), goalId, 0).terminalReceipt(), "Terminal raid replay changed the native receipt."); }
				PhantomAssertions.assertEquals(expAfter, roster.stream().mapToLong(Player::getExp).sum(), "Terminal callback replay awarded EXP twice.");
				final var regroupContext = new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RetreatContext(result.terminalReceipt().attemptAuthorityHash(), result.terminalReceipt().profile(), assembled.readyReceipt(), null, 0, System.nanoTime() + TimeUnit.SECONDS.toNanos(60), () -> false);
				var regroup = runtime.advanceRetreat(regroupContext, backend.currentForce(MemberRef.phantom(_profile.profileId(), _player.getObjectId())).snapshot());
				while (regroup.status() == org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RuntimeStatus.INTERMEDIATE && System.nanoTime() < regroupContext.logicalDeadlineNanos())
				{
					if (human.getAI().getIntention() != Intention.FOLLOW) { human.abortAttack(); human.getAI().setIntention(Intention.FOLLOW, _player); }
					regroup = runtime.advanceRetreat(regroupContext, backend.currentForce(MemberRef.phantom(_profile.profileId(), _player.getObjectId())).snapshot()); Thread.sleep(25);
				}
				context.record("raid059.regroup", regroup.status() + "/" + regroup.reasonKey() + "; roster=" + nativeParty.getMemberCount() + "; XYZ=" + roster.stream().map(Player::getLocation).toList());
				PhantomAssertions.assertTrue(regroup.status() == org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RuntimeStatus.COMPLETE && nativeParty.getMemberCount() == 9 && roster.stream().allMatch(value -> !value.isDead() && value.calculateDistance3D(_player) <= 350), "Post-victory native9/9 regroup did not complete through the existing owner.");
				for (Player actor : roster) { actor.abortAttack(); actor.getAI().setIntention(Intention.IDLE); actor.storeMe(); }
			}
			finally
			{
				observations.forEach((actor, listeners) -> listeners.forEach(actor::removeListener));
				attempts.beginStop(); assembly.beginStop(); runtime.beginStop(); routes.beginStop(); navigation.beginStop();
				final long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				while (!navigation.finishStop() && (System.nanoTime() < stopDeadline)) { Thread.sleep(20); }
				for (Player actor : roster) { actor.abortAttack(); actor.getAI().setIntention(Intention.IDLE); }
			}
		}
		finally
		{
			if (_player.isInParty()) { PartyInvitationService.getInstance().leave(_player); }
			progression.beginStop(); progression.finishStop();
		}
	}

	private void testRaidTactics058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		// Pre-enrollment selection is deterministic. This is a tactics episode, not a completed encounter.
		final GeoEngine geo = GeoEngine.getInstance();
		final RaidBoss boss = RaidBossSpawnManager.getInstance().getBosses().values().stream()
			.filter(candidate -> candidate.isSpawned() && !candidate.isDead() && !candidate.isInvul() && (candidate.getLevel() >= 72) && (candidate.getLevel() <= 80) && (candidate.getInstanceId() == 0) && (candidate.getCurrentHp() >= candidate.getMaxHp() * .99))
			.filter(candidate -> _query.findNpc(candidate.getId()).filter(fact -> fact.kind() == NpcKind.RAID_BOSS).isPresent())
			.filter(candidate -> geo.hasGeo(candidate.getX(), candidate.getY()) && geo.canMoveToTarget(candidate.getX(), candidate.getY(), candidate.getZ(), candidate.getX() + 100, candidate.getY(), candidate.getZ(), 0))
			.sorted(Comparator.comparingInt(RaidBoss::getLevel).thenComparingInt(RaidBoss::getId)).findFirst().orElseThrow(() -> new AssertionError("INVALID_INPUT: no live stock72..80 raid with declared geo approach."));
		final var fixture = _environment.createCampaign058Companion(context, 1);
		final PhantomProfile mageProfile = _repository.create(fixture.objectId());
		_additionalProfiles058.add(mageProfile);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(mageProfile.profileId()).status(), "Raid TEST mage did not materialize.");
		final Player mage = World.getInstance().getPlayer(fixture.objectId());
		final var healerFixture = _environment.createCampaign058Companion(context, 2);
		final PhantomProfile healerProfile = _repository.create(healerFixture.objectId());
		_additionalProfiles058.add(healerProfile);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(healerProfile.profileId()).status(), "Raid TEST healer did not materialize.");
		final Player healer = World.getInstance().getPlayer(healerFixture.objectId());
		final Player human = ensureObserver();
		prepareCompanionCombat058(_player, 90, 2500, 368, 6373, 6374, 6375, 6376, 6377, 6378);
		final int aggroLevel = _progression.classSkillLearns(90).stream().filter(learn -> (learn.skillId() == 28) && (learn.minimumCharacterLevel() <= 80)).mapToInt(learn -> learn.skillLevel()).max().orElseThrow();
		_player.addSkill(SkillData.getInstance().getSkill(28, aggroLevel), true);
		prepareCompanionCombat058(mage, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		prepareCompanionCombat058(healer, 97, 6579, 1335, 6383, 6384, 6385, 6386);
		for (Player actor : List.of(_player, mage, healer))
		{
			for (var learn : _progression.classSkillLearns(actor.getActiveClass()).stream().filter(learn -> learn.minimumCharacterLevel() <= 80).sorted(Comparator.comparingInt(learn -> learn.skillLevel())).toList())
			{
				final Skill skill = SkillData.getInstance().getSkill(learn.skillId(), learn.skillLevel());
				if ((skill != null) && (actor.getSkillLevel(learn.skillId()) < learn.skillLevel())) { actor.addSkill(skill, true); }
			}
			actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
		}
		mage.addSkill(SkillData.getInstance().getSkill(1339, 101), true);
		prepareCompanionCombat058(human, 5, 0, 0, 6373, 6374, 6375, 6376, 6378);
		int offset = 100;
		for (Player actor : List.of(_player, mage, healer, human))
		{
			actor.decayMe(); actor.setXYZInvisible(boss.getX() + offset, boss.getY(), geo.getHeight(boss.getX() + offset, boss.getY(), boss.getZ())); actor.spawnMe(); actor.revalidateZone(true); actor.setRunning(); actor.standUp();
			offset += 40;
		}
		// Canonical native membership; no new invitation-policy claim in this focused raid test.
		final Party party = new Party(human, PartyDistributionType.FINDERS_KEEPERS);
		human.setParty(party); _player.joinParty(party); mage.joinParty(party); healer.joinParty(party);
		final PhantomProgressionService progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _query), PhantomProgressionPolicy.productionDefaults());
		progression.start();
		final L2jPhantomPartyBackend partyBackend = new L2jPhantomPartyBackend(_repository, _materialization, progression);
		final PhantomPartyTactics tactics = new PhantomPartyTactics(_combat, partyBackend);
		final List<MemberRef> roster = List.of(MemberRef.real(human.getObjectId()), MemberRef.phantom(_profile.profileId(), _player.getObjectId()), MemberRef.phantom(mageProfile.profileId(), mage.getObjectId()), MemberRef.phantom(healerProfile.profileId(), healer.getObjectId()));
		final String authority = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(("TASK058:raid-tactics:" + boss.getObjectId() + ':' + _player.getObjectId() + ':' + mage.getObjectId()).getBytes(StandardCharsets.UTF_8)));
		final double hp = boss.getCurrentHp(); final double mp = mage.getCurrentMp(); final double tankMp = _player.getCurrentMp();
		final double healerMp = healer.getCurrentMp();
		final Skill currentHeal = healer.getKnownSkill(1217);
		PhantomAssertions.assertTrue(currentHeal != null && currentHeal.getLevel() >= 33, "Declared full class-tree raid healer lacks current Greater Heal.");
		context.record("raid058.input", "TACTICS_ONLY; stock manager boss=" + boss.getId() + "/" + boss.getObjectId() + "; level=" + boss.getLevel() + "; HP=" + hp + "; XYZ=" + boss.getX() + ',' + boss.getY() + ',' + boss.getZ() + "; TEST human=" + human.getObjectId() + "; tank=" + _player.getObjectId() + "; mage=" + mage.getObjectId() + "; healer=" + healer.getObjectId() + "; learned28:" + aggroLevel + "/1339:101/current1217:" + currentHeal.getLevel() + "; level80/all class-tree skills<=80 fixture; stock S gear; tankHP=" + _player.getCurrentHp() + "/pDef=" + _player.getPDef(boss) + "; 25s max; no boss reset, rescue or reward claim");
		boolean spaced = false; boolean aggroCast = false; boolean magicCast = false; boolean healed = false; long heldSince = 0; long held = 0; long mageDamage = 0; long healSequence = 0; double previousHp = _player.getCurrentHp();
		ExternalActionLease healing = null;
		try
		{
			PhantomAssertions.assertTrue(!human.isGM() && !_player.isGM() && !mage.isGM(), "Raid TEST actor is GM.");
			for (var input : List.of(Map.entry(_profile.profileId(), PhantomCombatMode.MELEE_PHYSICAL), Map.entry(mageProfile.profileId(), PhantomCombatMode.RANGED_MAGIC)))
			{
				final var request = new PhantomRaidCombatRequest(input.getKey(), boss.getObjectId(), boss.getId(), 0, org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind.RAID, NpcKind.RAID_BOSS, authority, input.getValue(), false, false, 80, 30_000, () -> false);
				PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startRaidSession(request).status(), "Live exact stock raid tactics admission failed for " + input + '.');
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
			while ((System.nanoTime() < deadline) && !_player.isDead() && !mage.isDead() && !boss.isDead())
			{
				if ((healing != null) && !healer.isCastingNow() && !healer.isAttackingNow()) { healing.complete(); healing = null; }
				if (healing == null)
				{
					final Map<MemberRef, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot> snapshots = new java.util.LinkedHashMap<>();
					for (MemberRef member : roster) { snapshots.put(member, partyBackend.memberSnapshot(member).orElseThrow()); }
					final var heal = tactics.plan(roster.getFirst(), roster, snapshots).stream().filter(directive -> (directive.actor().profileId() == healerProfile.profileId()) && (directive.kind() == DirectiveKind.HEAL_MEMBER)).findFirst();
					if (heal.isPresent()) { healing = tactics.dispatch(heal.orElseThrow(), "raid058.heal." + ++healSequence, deadline, () -> false).orElse(null); }
				}
					healed |= (_player.getCurrentHp() > previousHp + 100) && (healer.getLastSkillCast() != null) && PhantomSupportEffectAuthority.supportsCapability(healer.getLastSkillCast(), "combat.heal") && (healer.getSkillLevel(healer.getLastSkillCast().getId()) == healer.getLastSkillCast().getLevel());
				previousHp = _player.getCurrentHp();
				spaced |= mage.isMoving() && mage.hasAI() && (mage.getAI().getIntention() == Intention.MOVE_TO);
				aggroCast |= (_player.getLastSkillCast() != null) && (_player.getLastSkillCast().getId() == 28);
				magicCast |= (mage.getLastSkillCast() != null) && (mage.getLastSkillCast().getId() == 1339) && (mage.getLastSkillCast().getLevel() == 101);
				final var contribution = boss.getAggroList().get(mage);
				mageDamage = Math.max(mageDamage, contribution == null ? 0 : contribution.getDamage());
				if ((boss.getMostHated() == _player) && (boss.getTarget() == _player))
				{
					if (heldSince == 0) { heldSince = System.nanoTime(); }
					held = Math.max(held, System.nanoTime() - heldSince);
				}
				else { heldSince = 0; }
				if (spaced && aggroCast && magicCast && healed && (mageDamage > 0) && (held >= TimeUnit.SECONDS.toNanos(3)) && (boss.getCurrentHp() + 10 < hp) && (mage.getCurrentMp() + 1 < mp)) { break; }
				Thread.sleep(25);
			}
			context.record("raid058.result", "bossHP=" + hp + "->" + boss.getCurrentHp() + "; mageMP=" + mp + "->" + mage.getCurrentMp() + "; tankMP=" + tankMp + "->" + _player.getCurrentMp() + "; healerMP=" + healerMp + "->" + healer.getCurrentMp() + "; actualHeal=" + healed + "; healerCasting=" + healer.isCastingNow() + "; healerIntention=" + healer.getAI().getIntention() + "; healerLastSkill=" + (healer.getLastSkillCast() == null ? "none" : healer.getLastSkillCast().getId() + ":" + healer.getLastSkillCast().getLevel()) + "; spaced=" + spaced + "; aggroCast=" + aggroCast + "; enhancedMagic=" + magicCast + "; mageActualDamage=" + mageDamage + "; heldMs=" + TimeUnit.NANOSECONDS.toMillis(held) + "; alive=" + !_player.isDead() + '/' + !mage.isDead() + '/' + !healer.isDead() + '/' + !human.isDead() + "; encounter/reward/regroup NOT_CLOSED");
			PhantomAssertions.assertTrue(spaced && aggroCast && magicCast && healed && (healer.getCurrentMp() + 1 < healerMp) && (mageDamage > 0) && (held >= TimeUnit.SECONDS.toNanos(3)) && (boss.getCurrentHp() + 10 < hp) && (mage.getCurrentMp() + 1 < mp), "Declared stock raid tactics did not complete actual spacing/aggro/damage/cost.");
			PhantomAssertions.assertTrue(!_player.isDead() && !mage.isDead() && !healer.isDead() && !human.isDead(), "A declared raid tactics participant died.");
		}
		finally
		{
			if (healing != null) { healing.close(); }
			_combat.cancel(_profile.profileId()); _combat.cancel(mageProfile.profileId()); consumeTerminal();
			if (human.isInParty()) { PartyInvitationService.getInstance().leave(human); }
			progression.beginStop(); progression.finishStop();
		}
	}

	private void testRangedGear058(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		prepareCompanionCombat058(_player, 92, 7575, 343, 6379, 6380, 6381, 6382);
		final var resolver = PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_PHYSICAL, lease, 4).isPresent(), "Declared equipped TEST bow has no loadout.");
			final Item bow = _player.getInventory().unEquipItemInSlot(org.l2jmobius.gameserver.model.itemcontainer.Inventory.PAPERDOLL_RHAND);
			PhantomAssertions.assertTrue(bow != null && bow.getId() == 7575, "TEST bow gear transition did not unequip its exact weapon.");
			PhantomAssertions.assertFalse(lease.supportsNormalAttack(PhantomCombatMode.RANGED_PHYSICAL), "Unarmed body retained ranged normal attack readiness.");
			PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_PHYSICAL, lease, 4).isEmpty(), "Current bow removal retained a cached ranged loadout.");
			_player.getInventory().equipItem(bow);
			PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_PHYSICAL, lease, 4).isPresent(), "Re-equipping the same owned bow did not restore readiness.");
			context.record("spacing058.gear", "native bow7575 equipped→unequipped→same item equipped; same lease/resolver; no item grant/action during transition");
		}
	}

	private void testSubclassSkills058(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true);
		prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		_player.addSkill(SkillData.getInstance().getSkill(1339, 101), true);
		_player.storeMe();
		// Declared native subclass fixture input; this does not claim the subclass quest or natural career.
		PhantomAssertions.assertTrue(_player.addSubClass(9, 1), "Canonical TEST subclass preparation failed.");
		final var resolver = PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query);
		try
		{
			_player.setActiveClass(1);
			PhantomAssertions.assertEquals(1, _player.getClassIndex(), "Canonical subclass switch did not occur.");
			PhantomAssertions.assertEquals(9, _player.getActiveClass(), "Native subclass active class differs.");
			PhantomAssertions.assertEquals(null, _player.getKnownSkill(1339), "Former base-class skill leaked into native subclass.");
			try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4).isEmpty(), "Resolver retained base magic on the native subclass.");
			}
		}
		finally { _player.setActiveClass(0); }
		PhantomAssertions.assertEquals(94, _player.getActiveClass(), "Native base class did not restore.");
		PhantomAssertions.assertEquals(101, _player.getKnownSkill(1339).getLevel(), "Enhanced base skill did not restore after subclass.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.dematerialize(_profile.profileId()).status(), "Native skill save/dematerialization failed.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Native skill rematerialization failed.");
		_player = World.getInstance().getPlayer(_environment.primary().objectId());
		try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4).orElseThrow().selectedSkills().contains(new SelectedSkill(1339, 101)), "Persisted native reload lost current learned rank or retained a stale loadout.");
		}
		context.record("skills058.subclass", "native addSubClass9/index1 TEST precondition -> setActiveClass1:base1339 absent/magic unavailable -> setActiveClass0:94/1339:101 restored -> native dematerialize/materialize:current101 capability ready; same resolver; subclass quest NOT claimed");
	}

	private void testLearnedOffense058(PhantomTestContext context) throws Exception
	{
		resetActor(true);
		prepareCompanionCombat058(_player, 94, 6579, 1339, 6383, 6384, 6385, 6386);
		final Skill enhanced = SkillData.getInstance().getSkill(1339, 101);
		PhantomAssertions.assertTrue(enhanced != null && enhanced.getLevel() == 101, "Declared stock enhanced offense is absent.");
		_player.addSkill(enhanced, true); // Declared TEST learned-rank input before this episode.
		final var resolver = PhantomCombatCapabilityResolver.fromGameKnowledge(() -> _query);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final var loadout = resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4);
			context.record("skills058.offense.input", "TEST class94/80; stock1339:101; staff6579/robeS; catalog evidence remains1339:1; actor=" + _player.getObjectId());
			PhantomAssertions.assertTrue(loadout.isPresent() && loadout.get().selectedSkills().contains(new SelectedSkill(1339, 101)), "Current learned enhanced rank was lost behind old catalog evidence: " + loadout);
			PhantomAssertions.assertFalse(lease.supportsSkill(new SelectedSkill(1339, 1), PhantomCombatMode.RANGED_MAGIC), "Stale rank was accepted as the current learned skill.");
		}
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final double hpBefore = target.getCurrentHp();
		final double mpBefore = _player.getCurrentMp();
		PhantomAssertions.assertTrue(_combat.startSession(request(target, PhantomCombatMode.RANGED_MAGIC, false, false)).accepted(), "Enhanced native offense was not admitted.");
		await(() -> (target.getCurrentHp() < hpBefore) && (_player.getCurrentMp() < mpBefore), "Enhanced native cast produced no damage/cost.");
		final Skill actual = _player.getLastSkillCast();
		PhantomAssertions.assertTrue(actual != null && actual.getId() == 1339 && actual.getLevel() == 101, "Native action did not use the actual enhanced rank.");
		context.record("skills058.offense.result", "nativeSkill=" + actual.getId() + ':' + actual.getLevel() + "; hp=" + hpBefore + "->" + target.getCurrentHp() + "; mp=" + mpBefore + "->" + _player.getCurrentMp());
		_combat.cancel(_profile.profileId());
		consumeTerminal();
		// Separate declared context boundary, after the previous episode has ended.
		_player.setPlayerClass(92);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(resolver.resolve(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4).isEmpty(), "Resolver reused a former class capability.");
		}
	}

	private void testCanonicalCast() throws Exception
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Deterministic offensive skill is unavailable.");
		_player.addSkill(skill, false);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final double initialHp = target.getCurrentHp();
		final double initialMp = _player.getCurrentMp();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID, 1), PhantomCombatMode.RANGED_MAGIC), "Real hostile one-target skill was rejected.");
		}
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.RANGED_MAGIC, false, false));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Supported magic loadout was not accepted.");
		await(() -> _player.isCastingNow() || (_player.getCurrentSkill() != null) || (target.getCurrentHp() < initialHp) || (_player.getCurrentMp() < initialMp), "Canonical CAST produced no observable cast state or effect.");
		PhantomAssertions.assertTrue((_player.getCurrentMp() <= initialMp) && (target.getCurrentHp() <= initialHp), "Canonical cast fabricated HP or MP.");
		_combat.cancel(_profile.profileId());
		consumeTerminal();
	}

	private void testMandatoryShots061(PhantomTestContext context) throws Exception
	{
		resetActor(true); _player.setBaseClass(0); _player.setPlayerClass(0); _player.getStat().setLevel((byte) 19);
		prepareLearningField059(context);
		final var weapon = _player.getInventory().addItem(ItemProcessType.REWARD, 68, 1, _player, this); _player.getInventory().equipItem(weapon);
		final var preferences = List.copyOf(_player.getAutoSoulShot()); for (int item : preferences) { _player.removeAutoSoulShot(item); }
		_player.setChargedShot(ShotType.SOULSHOTS, false); _player.setChargedShot(ShotType.SPIRITSHOTS, false); _player.setChargedShot(ShotType.BLESSED_SPIRITSHOTS, false);
		final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) _player.getNativeWorkOwner(); final boolean previousPolicy = scope.requiresOffensiveShots(); scope.requireOffensiveShots(true);
		final var target = spawnExactPointMonster(20035, Double.MAX_VALUE); final double hp = target.getCurrentHp();
		try
		{
			_player.setTarget(target); _player.doAttack(target);
			Thread.sleep(2500);
			PhantomAssertions.assertTrue(target.getCurrentHp() == hp && !_player.isAttackingNow(), "Phantom started an uncharged native ATTACK.");
			_player.setBaseClass(10); _player.setPlayerClass(10); _player.setLearningClass(_player.getPlayerClass());
			final var skill = SkillData.getInstance().getSkill(1177, 1); _player.addSkill(skill, false);
			_player.setCurrentMp(_player.getMaxMp()); // Separate negative CAST input, before its measurement.
			final double mp = _player.getCurrentMp(); _player.setTarget(target); _player.doCast(skill);
			Thread.sleep(2500);
			PhantomAssertions.assertTrue(target.getCurrentHp() == hp && _player.getCurrentMp() == mp && !_player.isCastingNow(), "Phantom started an uncharged native CAST or paid MP for a refused cast.");
			context.record("shots061.mandatory", "real native attack/cast without a paid charge refused; no target damage/MP cost; native earned actions were not cancelled");
		}
		finally { scope.requireOffensiveShots(previousPolicy); _player.abortAttack(); _player.abortCast(); _player.getAI().setIntention(Intention.IDLE); target.deleteMe(); for (int item : preferences) { _player.addAutoSoulShot(item); } }
	}

	private void testContinuousShots061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true); _player.setPlayerClass(0); _player.getStat().setLevel((byte) 19);
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat(), 20_000), "Previous native CAST did not leave combat stance.");
		for (int id : new int[] {68, 24, 31}) { final var item = _player.getInventory().addItem(ItemProcessType.REWARD, id, 1, _player, this); _player.getInventory().equipItem(item); }
		_player.refreshExpertisePenalty(); _player.refreshOverloaded(); _player.setCurrentHp(_player.getMaxHp());
		prepareLearningField059(context);
		final var shots = _player.getInventory().addItem(ItemProcessType.REWARD, 1835, 30, _player, this);
		final var wrong = _player.getInventory().addItem(ItemProcessType.REWARD, 1463, 30, _player, this);
		final var target = spawnExactPointMonster(20035, Double.MAX_VALUE);
		final long before = shots.getCount(), wrongBefore = wrong.getCount(); final double hp = target.getCurrentHp();
		final int perHit = _player.getActiveWeaponItem().getSoulShotCount();
		context.record("loadout061.physicalInput", "Independent TEST Fighter19/native68+24+31; native20035/Spawn; finite30 no-grade/30 D shots before measurement; one ATTACK, no pulse recharge by test");
		try
		{
			try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				PhantomAssertions.assertEquals(ShotOutcome.ACTIVATED, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Physical native charge failed.");
				PhantomAssertions.assertTrue(_player.getAutoSoulShot().contains(1835) && !_player.getAutoSoulShot().contains(1463), "Product did not register native continuous shots for the exact weapon grade.");
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.attack(target.getObjectId()), "Continuous native attack was not issued.");
				PhantomAssertions.assertTrue(waitFor(() -> shots.getCount() <= before - 3L * perHit && target.getCurrentHp() < hp, 12_000), "One continuous native ATTACK did not recharge shots across two real hits.");
				PhantomAssertions.assertEquals(wrongBefore, wrong.getCount(), "Wrong-grade shots were consumed.");
				lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
			}
			PhantomAssertions.assertFalse(_player.getAutoSoulShot().contains(1835), "Closed Combat lease left its auto-shot preference running.");
			context.record("loadout061.physicalObserved", "stock=" + before + "->" + shots.getCount() + "; targetHP=" + hp + "->" + target.getCurrentHp() + "; native auto-shot and wrong-grade conservation; no added charge after ATTACK");
		}
		finally { _player.abortAttack(); _player.getAI().setIntention(Intention.IDLE); target.deleteMe(); destroyInventoryItem(shots); destroyInventoryItem(wrong); }
	}

	private void testBlessedShots061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true); _player.setPlayerClass(10); _player.getStat().setLevel((byte) 20);
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat(), 20_000), "Previous native physical shot control did not leave combat stance.");
		final var catalog = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var grade = org.l2jmobius.gameserver.model.item.type.CrystalType.D;
		final var weapon = catalog.buyOffers().stream().map(offer -> org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(offer.itemId())).filter(item -> item instanceof org.l2jmobius.gameserver.model.item.Weapon value && value.isMagicWeapon() && value.getCrystalType() == grade && value.getSpiritShotCount() > 0).map(item -> (org.l2jmobius.gameserver.model.item.Weapon) item).distinct().min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).orElseThrow();
		final var equipped = _player.getInventory().addItem(ItemProcessType.REWARD, weapon.getId(), 1, _player, this); _player.getInventory().equipItem(equipped);
		final var items = catalog.supplies().stream().map(supply -> org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> item instanceof org.l2jmobius.gameserver.model.item.EtcItem && item.getDefaultAction() == org.l2jmobius.gameserver.model.item.type.ActionType.SPIRITSHOT && item.getCrystalType() == grade).toList();
		final var blessed = items.stream().filter(item -> ((org.l2jmobius.gameserver.model.item.EtcItem) item).isBlessed()).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).orElseThrow();
		final var regular = items.stream().filter(item -> !((org.l2jmobius.gameserver.model.item.EtcItem) item).isBlessed()).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).orElseThrow();
		final var blessedStock = _player.getInventory().addItem(ItemProcessType.REWARD, blessed.getId(), 30, _player, this);
		final var regularStock = _player.getInventory().addItem(ItemProcessType.REWARD, regular.getId(), 30, _player, this);
		final var skill = SkillData.getInstance().getSkill(1177, 1); _player.addSkill(skill, false);
		_player.refreshExpertisePenalty(); _player.refreshOverloaded(); _player.setCurrentHp(_player.getMaxHp()); _player.setCurrentMp(_player.getMaxMp());
		prepareLearningField059(context);
		final var target = spawnExactPointMonster(20001, Double.MAX_VALUE);
		final long before = blessedStock.getCount(), regularBefore = regularStock.getCount();
		final double hpBefore = target.getCurrentHp(), mpBefore = _player.getCurrentMp();
		_player.addAutoSoulShot(regular.getId());
		context.record("loadout061.magicInput", "Independent native HumanMystic20; D magic weapon=" + weapon.getId() + "; blessed=" + blessed.getId() + "; regular=" + regular.getId() + "; finite30 each; preexisting regular auto-shot; native1177:1 before measurement");
		try
		{
			try (var lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				PhantomAssertions.assertEquals(ShotOutcome.ACTIVATED, lease.activateShot(PhantomCombatMode.RANGED_MAGIC), "Native magic charge failed.");
				PhantomAssertions.assertTrue(_player.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) && _player.getAutoSoulShot().contains(blessed.getId()) && !_player.getAutoSoulShot().contains(regular.getId()), "Regular native auto-shot masked the preferred exact-grade blessed spiritshot.");
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.cast(target.getObjectId(), new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(1177, 1), PhantomCombatMode.RANGED_MAGIC), "Native blessed-backed CAST was not issued.");
				PhantomAssertions.assertTrue(waitFor(() -> blessedStock.getCount() <= before - 2L * weapon.getSpiritShotCount() && target.getCurrentHp() < hpBefore && _player.getCurrentMp() < mpBefore, 12_000), "Native CAST did not damage its target, spend MP and recharge the blessed spiritshot.");
				PhantomAssertions.assertEquals(regularBefore, regularStock.getCount(), "Preferred blessed selection consumed regular spiritshots.");
				lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
			}
			PhantomAssertions.assertTrue(!_player.getAutoSoulShot().contains(blessed.getId()) && _player.getAutoSoulShot().contains(regular.getId()), "Combat cleanup lost the preexisting native shot preference.");
			context.record("loadout061.magicObserved", "blessed=" + before + "->" + blessedStock.getCount() + "; regular=" + regularBefore + "->" + regularStock.getCount() + "; targetHP=" + hpBefore + "->" + target.getCurrentHp() + "; MP=" + mpBefore + "->" + _player.getCurrentMp() + "; preexisting auto-shot restored");
		}
		finally { _player.removeAutoSoulShot(regular.getId()); _player.abortCast(); _player.getAI().setIntention(Intention.IDLE); target.deleteMe(); destroyInventoryItem(blessedStock); destroyInventoryItem(regularStock); }
	}

	private void testEquipmentEligibility061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true); _player.setBaseClass(0); _player.setPlayerClass(0); _player.getStat().setExp(_player.getStat().getExpForLevel(20)); _player.getStat().setLevel((byte) 20); _player.removeSkill(239); _player.giveAvailableAutoGetSkills();
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat(), 20_000), "Equipment control did not leave native combat stance.");
		prepareLearningField059(context);
		final var catalog = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var nativeItems = catalog.buyOffers().stream().map(offer -> org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(offer.itemId())).distinct().toList();
		final var weapon = nativeItems.stream().filter(item -> item instanceof org.l2jmobius.gameserver.model.item.Weapon value && !value.isMagicWeapon() && value.getItemType() == org.l2jmobius.gameserver.model.item.type.WeaponType.SWORD && value.getCrystalType() == org.l2jmobius.gameserver.model.item.type.CrystalType.D && value.checkCondition(_player, _player, false)).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).orElseThrow();
		final var armor = nativeItems.stream().filter(item -> item instanceof org.l2jmobius.gameserver.model.item.Armor value && value.getBodyPart() == org.l2jmobius.gameserver.model.item.enums.BodyPart.CHEST && value.getItemType() == org.l2jmobius.gameserver.model.item.type.ArmorType.LIGHT && value.getCrystalType() == org.l2jmobius.gameserver.model.item.type.CrystalType.D && value.checkCondition(_player, _player, false)).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).orElseThrow();
		final var overgrade = nativeItems.stream().filter(item -> item instanceof org.l2jmobius.gameserver.model.item.Weapon value && value.getCrystalType().getLevel() > _player.getExpertiseLevel()).findFirst().orElseThrow();
		final long exp = _player.getExp(), sp = _player.getSp(), adena = _player.getAdena();
		PhantomAssertions.assertEquals(1, _player.getExpertiseLevel(), "Declared native20 input has no free D expertise.");
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.power(_player, weapon) > 0 && org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.power(_player, armor) > 0, "Equipment preference did not read actual native item combat stats.");
		context.record("equipment061.eligibilityInput", "Independent native Fighter20/expertise1; real NPC-catalog D weapon=" + weapon.getId() + "; D light chest=" + armor.getId() + "; overgrade=" + overgrade.getId() + "; eligibility reads only, no ordinary equipment/quest/SP grants");
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.compatible(_player, weapon), "Supply cannot prepare a native expertise-legal weapon.");
		PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.compatible(_player, armor), "Supply cannot prepare native expertise-legal armor.");
		PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney.compatible(_player, overgrade), "Equipment aspiration bypassed native expertise.");
		PhantomAssertions.assertTrue(_player.getExp() == exp && _player.getSp() == sp && _player.getAdena() == adena, "Read-only equipment eligibility changed earned resources.");
	}

	private void testOrdinaryShots061(PhantomTestContext context) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		resetActor(true); _player.setPlayerClass(10); _player.getStat().setLevel((byte) 7);
		PhantomAssertions.assertTrue(waitFor(() -> !_player.isInCombat(), 20_000), "Previous native shot control did not leave combat stance.");
		final var weapon = _player.getInventory().addItem(ItemProcessType.REWARD, 6, 1, _player, this); _player.getInventory().equipItem(weapon);
		final var stock = new ArrayList<Item>();
		for (int id : new int[] {1835, 2509, 3947, 3948}) { stock.add(_player.getInventory().addItem(ItemProcessType.REWARD, id, 30, _player, this)); }
		_player.refreshExpertisePenalty(); _player.refreshOverloaded(); prepareLearningField059(context);
		_player.addAutoSoulShot(2509);
		final var goals = new PhantomGoalStateStore(_repository);
		final var anchor = _topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(Comparator.comparingDouble(value -> Math.hypot(value.point().x() - _player.getX(), value.point().y() - _player.getY()))).orElseThrow();
		final var farm = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_profile.profileId())), new PhantomDomainRef("npc", Integer.toString(_combatPoint.npcId())), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, _combatPoint.npcId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture061.ordinary_shots", 1);
		goals.insert(_profile.profileId(), farm);
		final var candidates = new PhantomCandidateRegistry(); final var handlers = new PhantomStepHandlerRegistry(); candidates.seal(); handlers.seal();
		final var engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1); engine.start();
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(_profile.profileId()), "Ordinary shot input did not attach its real farm root.");
		final var autoPlay = new PhantomVisibleAutoPlay(_materialization, () -> engine, _ -> true);
		context.record("loadout061.ordinaryInput", "Independent native Mage7/NG wand6; finite30 physical, regular magic, blessed NG and blessed D; preexisting regular auto-shot; real Decision/AutoPlay pools; no monster or combat progress claimed");
		try
		{
			PhantomAssertions.assertTrue(autoPlay.start(_profile.profileId(), farm), "Ordinary native AutoPlay registration failed.");
			PhantomAssertions.assertTrue(_player.getAutoSoulShot().contains(3947) && !_player.getAutoSoulShot().contains(1835) && !_player.getAutoSoulShot().contains(2509) && !_player.getAutoSoulShot().contains(3948), "Ordinary AutoPlay registered the wrong shot type, grade or regular quality: " + _player.getAutoSoulShot());
			autoPlay.stop(_profile.profileId());
			PhantomAssertions.assertTrue(_player.getAutoSoulShot().contains(2509) && !_player.getAutoSoulShot().contains(3947), "Ordinary stop lost the preexisting native shot preference.");
			context.record("loadout061.ordinaryObserved", "Real AutoPlay selected exact NG blessed only; stop restored the prior regular preference; physical and wrong-grade stocks=" + stock.get(0).getCount() + "," + stock.get(3).getCount());
		}
		finally
		{
			autoPlay.stop(_profile.profileId()); engine.beginStop(); engine.finishStop();
			final var saved = goals.load(_profile.profileId()).orElseThrow(); goals.delete(_profile.profileId(), saved.rowVersion());
			for (var item : stock) { _player.removeAutoSoulShot(item.getId()); destroyInventoryItem(item); }
			destroyInventoryItem(weapon);
		}
	}

	private void testCanonicalShot() throws Exception
	{
		resetActor(true);
		final Item weapon = ensureWeapon();
		final Item shots = _player.getInventory().addItem(ItemProcessType.REWARD, SOULSHOT_ITEM_ID, 5, _player, this);
		PhantomAssertions.assertTrue(shots != null, "Could not create test-owned soulshot fixture.");
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.ACTIVATED, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Canonical soulshot activation failed.");
			PhantomAssertions.assertEquals(before - 1, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Canonical soulshot activation consumed an unexpected count.");
			PhantomAssertions.assertTrue(_player.isChargedShot(ShotType.SOULSHOTS), "Canonical soulshot handler did not charge the weapon.");
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.attack(target.getObjectId()), "Canonical shot-backed attack was not issued.");
			await(() -> _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1) <= before - 2 && target.getCurrentHp() < target.getMaxHp(), "Canonical attack did not discharge and natively recharge the soulshot.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, target.getObjectId(), null, 0));
		}
		target.deleteMe();
		destroyInventoryItem(shots);
		PhantomAssertions.assertTrue(weapon.isEquipped(), "Shot test unexpectedly unequipped its weapon.");
	}

	private void testMissingShot()
	{
		resetActor(true);
		ensureWeapon();
		// Independent missing-stock input; the previous real hit may have recharged its last paid shot.
		_player.setChargedShot(ShotType.SOULSHOTS, false);
		final Item lingeringShots = _player.getInventory().getItemByItemId(SOULSHOT_ITEM_ID);
		if (lingeringShots != null)
		{
			destroyInventoryItem(lingeringShots);
		}
		final long before = _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ShotOutcome.UNAVAILABLE, lease.activateShot(PhantomCombatMode.MELEE_PHYSICAL), "Missing shot was not reported as unavailable.");
		}
		PhantomAssertions.assertEquals(before, _player.getInventory().getInventoryItemCount(SOULSHOT_ITEM_ID, -1), "Missing shot path fabricated inventory.");
		PhantomAssertions.assertFalse(_player.isChargedShot(ShotType.SOULSHOTS), "Missing shot path fabricated charge.");
		destroyInventoryItem(_player.getInventory().getItemByItemId(WEAPON_ITEM_ID));
	}

	private void testCanonicalLoot() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		final long before = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		final PhantomCombatService.StartResult started = _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, true));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Loot combat was not accepted.");
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill the test-owned loot target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Canonical monster drop did not create a ground item.");
		try
		{
			await(() -> World.getInstance().findObject(dropped.getObjectId()) == null, "Canonical PlayerAI pickup did not remove the ground item.");
			final PhantomCombatSessionSnapshot terminal = awaitTerminal();
			PhantomAssertions.assertTrue((terminal.result() == PhantomCombatResult.VICTORY_LOOTED) || (terminal.result() == PhantomCombatResult.VICTORY_LOOT_PARTIAL), "Canonical loot produced unexpected result " + terminal.result() + ".");
			PhantomAssertions.assertEquals(before + 1, _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Ground item and inventory conservation failed.");
		}
		finally
		{
			consumeTerminal();
			final long added = _player.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - before;
			if (added > 0)
			{
				final Item adena = _player.getInventory().getItemByItemId(ADENA_ITEM_ID);
				PhantomAssertions.assertTrue(_player.getInventory().destroyItem(ItemProcessType.DESTROY, adena, added, _player, this) != null, "Could not restore fixture adena baseline.");
			}
		}
	}

	private void testForbiddenTargets()
	{
		resetActor(true);
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned Player target.");
		_observer.setXYZInvisible(_player.getX() + 20, _player.getY(), _player.getZ());
		_observer.spawnMe();
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(_observer.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "Player target was accepted.");

		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		final RaidBoss raid = spawn(new RaidBoss(template), 30);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(raid.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "RaidBoss target was accepted.");
		final GrandBoss grand = spawn(new GrandBoss(template), 40);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, _combat.startSession(request(grand.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL)).status(), "GrandBoss target was accepted.");

		final Monster observed = spawnNormalMonster(targetMaximumHp());
		observed.setTarget(_player);
		_player.getAttackByList().clear();
		_player.getAttackByList().add(observed);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertTrue(lease.observedAttackers(16).stream().anyMatch(entry -> entry.targetObjectId() == observed.getObjectId()), "Valid normal-monster threat was not observed.");
			observed.setInvul(true);
			PhantomAssertions.assertTrue(lease.observedAttackers(16).isEmpty(), "Forbidden invulnerable threat entered the table.");
		}
		finally
		{
			observed.setInvul(false);
			_player.getAttackByList().clear();
		}
	}

	private void testOwnedCancellation() throws Exception
	{
		resetActor(true);
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		final Monster foreign = spawnNormalMonster(targetMaximumHp());
		foreign.setXYZ(_player.getX() + 60, _player.getY(), _player.getZ());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(owned, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Owned-action combat did not start.");
		await(() -> _player.hasAI() && (_player.getAI().getIntention() == Intention.ATTACK) && (_player.getAI().getAttackTarget() == owned), "Combat did not establish the owned attack.");
		_player.setTarget(foreign);
		_player.getAI().setIntention(Intention.ATTACK, foreign);
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Combat cancellation was not accepted.");
		PhantomAssertions.assertEquals(Intention.ATTACK, _player.getAI().getIntention(), "Combat cancellation stopped a foreign action.");
		PhantomAssertions.assertEquals(foreign, _player.getAI().getAttackTarget(), "Combat cancellation replaced a foreign target.");
		consumeTerminal();
	}

	private void testPlayerDeath() throws Exception
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Death-observation combat did not start.");
		PhantomAssertions.assertTrue(_player.doDie(target), "Could not kill the test-owned actor.");
		final PhantomCombatSessionSnapshot terminal = awaitTerminal();
		PhantomAssertions.assertEquals(PhantomCombatResult.PLAYER_DEAD, terminal.result(), "Actor death did not produce PLAYER_DEAD.");
		await(() -> (_combat.snapshot().actorLeases() == 0) && (_combat.snapshot().currentWorkers() == 0), "Player death retained combat lease or worker ownership.");
	}

	private void testNormalTownRespawn() throws Exception
	{
		PhantomAssertions.assertTrue(_player.isDead(), "Respawn case did not inherit the canonical dead actor.");
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, _combat.respawnTown(new PhantomRespawnRequest(_profile.profileId(), () -> false)), "Restricted normal-town respawn was not accepted.");
		await(() -> !_player.isDead() && !_player.isPendingRevive(), "Canonical normal-town teleport did not revive the actor.");
		PhantomAssertions.assertEquals(0, _player.getInstanceId(), "Normal-town respawn retained an instance.");
		PhantomAssertions.assertTrue(_player.isSpawned() && !_player.isTeleporting(), "Headless normal-town respawn did not complete canonical teleport lifecycle.");
		consumeTerminal();
	}

	private void testDematerializationDrain() throws Exception
	{
		resetActor(true);
		relocateToCombatPoint();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, _combat.startSession(request(target, PhantomCombatMode.MELEE_PHYSICAL, false, false)).status(), "Lease-drain combat did not start.");
		final AtomicReference<PhantomMaterializationService.DematerializeResult> result = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread dematerialize = new Thread(() ->
		{
			try
			{
				result.set(_materialization.dematerialize(_profile.profileId()));
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		}, "Task012-dematerialization-drain");
		dematerialize.start();
		Thread.sleep(200);
		PhantomAssertions.assertTrue(dematerialize.isAlive(), "Dematerialization passed an active combat ActionLease.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, _combat.cancel(_profile.profileId()), "Could not cancel combat for materialization drain.");
		dematerialize.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(dematerialize.isAlive(), "Dematerialization did not complete after combat lease release.");
		if (failure.get() != null)
		{
			throw new AssertionError("Dematerialization failed.", failure.get());
		}
		PhantomAssertions.assertTrue(result.get() != null, "Dematerialization returned no result.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), "Dematerialization failed after combat cancellation.");
		consumeTerminal();
	}

	private void testNoPacketRoute() throws Exception
	{
		final Path combatRoot = _moduleRoot.resolve("java/org/l2jmobius/gameserver/phantoms/combat");
		final String source;
		try (var paths = Files.walk(combatRoot))
		{
			source = paths.filter(path -> path.toString().endsWith(".java")).sorted().map(path ->
			{
				try
				{
					return Files.readString(path, StandardCharsets.UTF_8);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
			}).reduce("", String::concat);
		}
		PhantomAssertions.assertFalse(source.contains("network.clientpackets"), "Combat production code imports a client packet.");
		PhantomAssertions.assertFalse(source.contains("network.serverpackets"), "Combat production code imports a server packet.");
		PhantomAssertions.assertFalse(source.contains("sendPacket("), "Combat production code sends a packet directly.");
		PhantomAssertions.assertFalse(source.contains("RequestRestartPoint"), "Combat production code simulates restart packet handling.");
	}

	private void testOtherPlayerPickupEvidence()
	{
		resetActor(true);
		final Player observer = ensureObserver();
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill other-player pickup fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create other-player ground item.");
		final long observerBefore = observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.getDropProtection().unprotect();
			dropped.setOwnerId(0);
			observer.doPickupItem(dropped);
			PhantomAssertions.assertEquals(observerBefore + 1, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1), "Other test player did not acquire the item.");
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Other-player pickup was attributed to the phantom.");
		}
		finally
		{
			destroyInventoryCount(observer, ADENA_ITEM_ID, observer.getInventory().getInventoryItemCount(ADENA_ITEM_ID, -1) - observerBefore);
			destroyGroundItem(dropped);
		}
	}

	private void testDespawnEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill despawn fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create despawn ground item.");
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			dropped.decayMe();
			PhantomAssertions.assertEquals(LootObservation.LOST_WITHOUT_ACQUISITION, lease.observeLoot(candidate), "Despawn was attributed to the phantom.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testRangeLossEvidence()
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill range-loss fixture.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create range-loss ground item.");
		final int x = _player.getX();
		final int y = _player.getY();
		final int z = _player.getZ();
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final LootCandidate candidate = exactCandidate(lease, dropped);
			_player.setXYZ(x + 1000, y, z);
			PhantomAssertions.assertEquals(LootObservation.INELIGIBLE, lease.observeLoot(candidate), "Out-of-radius item was attributed to the phantom.");
		}
		finally
		{
			_player.setXYZ(x, y, z);
			destroyGroundItem(dropped);
		}
	}

	private void testPickupCleanup(boolean stop)
	{
		resetActor(true);
		final Monster target = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(target.doDie(_player), "Could not kill exact PICK_UP target.");
		final Item dropped = target.dropItem(_player, ADENA_ITEM_ID, 1);
		PhantomAssertions.assertTrue(dropped != null, "Could not create exact PICK_UP item.");
		dropped.setXYZ(_player.getX() + 200, _player.getY(), _player.getZ());
		try
		{
			try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
			{
				exactCandidate(lease, dropped);
				PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(dropped.getObjectId()), "Canonical PICK_UP was not issued.");
				PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Canonical PICK_UP intention was not established.");
				PhantomAssertions.assertEquals(dropped, _player.getTarget(), "Canonical PICK_UP did not own the exact item.");
				lease.cancelOwnedAction(new PhantomOwnedAction(stop ? 2 : 1, target.getObjectId(), null, dropped.getObjectId()));
			}
			PhantomAssertions.assertEquals(Intention.IDLE, _player.getAI().getIntention(), "Exact PICK_UP intention survived cleanup.");
			PhantomAssertions.assertTrue(_player.getTarget() == null, "Exact PICK_UP target survived cleanup.");
		}
		finally
		{
			destroyGroundItem(dropped);
		}
	}

	private void testForeignCastAndPickup()
	{
		resetActor(true);
		_player.setPlayerClass(MAGIC_CLASS_ID);
		_player.getStat().setLevel((byte) 85);
		final Skill skill = SkillData.getInstance().getSkill(MAGIC_SKILL_ID, 1);
		PhantomAssertions.assertTrue(skill != null, "Foreign CAST skill is unavailable.");
		_player.addSkill(skill, false);
		_player.enableSkill(skill);
		_player.setCurrentMp(_player.getMaxMp());
		final Monster owned = spawnNormalMonster(targetMaximumHp());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			final org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill staleSelected = new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(MAGIC_SKILL_ID + 1, 1);
			_player.abortCast();
			_player.setTarget(owned);
			_player.getAI().setIntention(Intention.CAST, skill, owned);
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Foreign CAST intention was not established.");
			lease.cancelOwnedAction(new PhantomOwnedAction(1, owned.getObjectId(), staleSelected, 0));
			PhantomAssertions.assertEquals(Intention.CAST, _player.getAI().getIntention(), "Stale cleanup stopped a foreign CAST.");
			PhantomAssertions.assertEquals(owned, _player.getAI().getCastTarget(), "Stale cleanup replaced a foreign CAST target.");
			PhantomAssertions.assertEquals(owned, _player.getTarget(), "Stale cleanup cleared the selected target of a foreign CAST.");
		}
		finally
		{
			_player.abortCast();
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
		}

		final Monster dropTarget = spawnNormalMonster(targetMaximumHp());
		PhantomAssertions.assertTrue(dropTarget.doDie(_player), "Could not kill foreign PICK_UP fixture.");
		final Item ownedItem = dropTarget.dropItem(_player, ADENA_ITEM_ID, 1);
		final Item foreignItem = ItemManager.createItem(ItemProcessType.LOOT, ADENA_ITEM_ID, 1, _player, this);
		PhantomAssertions.assertTrue((ownedItem != null) && (foreignItem != null), "Could not create foreign PICK_UP items.");
		foreignItem.dropMe(_player, _player.getX() + 100, _player.getY(), _player.getZ());
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertEquals(ActionOutcome.ISSUED, lease.pickUp(ownedItem.getObjectId()), "Owned PICK_UP was not issued.");
			_player.setTarget(foreignItem);
			_player.getAI().setIntention(Intention.PICK_UP, foreignItem);
			lease.cancelOwnedAction(new PhantomOwnedAction(2, dropTarget.getObjectId(), null, ownedItem.getObjectId()));
			PhantomAssertions.assertEquals(Intention.PICK_UP, _player.getAI().getIntention(), "Stale cleanup stopped a foreign PICK_UP.");
			PhantomAssertions.assertEquals(foreignItem, _player.getTarget(), "Stale cleanup replaced a foreign PICK_UP target.");
		}
		finally
		{
			_player.getAI().setIntention(Intention.IDLE);
			_player.setTarget(null);
			destroyGroundItem(ownedItem);
			destroyGroundItem(foreignItem);
		}
	}

	private void testPositiveSkillRejected()
	{
		resetActor(true);
		final Skill positive = SkillData.getInstance().getSkill(1040, 1);
		PhantomAssertions.assertTrue(positive != null, "Known positive one-target skill is unavailable.");
		_player.addSkill(positive, false);
		try (PhantomCombatActorLease lease = Optional.ofNullable(_backend.tryAcquireActor(_profile.profileId())).orElseThrow())
		{
			PhantomAssertions.assertFalse(lease.supportsSkill(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(1040, 1), PhantomCombatMode.RANGED_MAGIC), "Known positive one-target skill was accepted.");
		}
	}

	private PhantomCombatRequest request(Monster target, PhantomCombatMode mode, boolean shots, boolean loot)
	{
		return new PhantomCombatRequest(_profile.profileId(), target.getObjectId(), mode, shots, loot, 30_000, () -> false);
	}

	private PhantomCombatRequest request(int targetObjectId, PhantomCombatMode mode)
	{
		return new PhantomCombatRequest(_profile.profileId(), targetObjectId, mode, false, false, 30_000, () -> false);
	}

	private PhantomPvpCombatRequest pvpRequest(Player target, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source source, PhantomCombatMode mode, boolean forceUse)
	{
		return new PhantomPvpCombatRequest(_profile.profileId(), target.getObjectId(), source, PVP_AUTHORITY_HASH, mode, forceUse, false, 1, 30_000, () -> false);
	}

	private PhantomCombatSessionSnapshot awaitTerminal() throws Exception
	{
		await(() -> _combat.find(_profile.profileId()).map(snapshot -> snapshot.result().terminal()).orElse(false), "Combat session did not become terminal.");
		return _combat.find(_profile.profileId()).orElseThrow();
	}

	private void awaitCombatOutcome(Monster target, String label) throws Exception
	{
		try
		{
			await(() -> target.isDead() || target.isAlikeDead() || _combat.find(_profile.profileId()).map(value -> value.result().terminal()).orElse(false), label + " neither killed nor terminated.");
		}
		catch (AssertionError error)
		{
			throw new AssertionError(label + " neither killed nor terminated: session=" + _combat.find(_profile.profileId()).orElse(null) + ", actorIntention=" + _player.getAI().getIntention() + ", actorTarget=" + _player.getTarget() + ", attackTarget=" + _player.getAI().getAttackTarget() + ", actorAttacking=" + _player.isAttackingNow() + ", targetHp=" + target.getCurrentHp(), error);
		}
	}

	private void consumeTerminal()
	{
		_combat.consumeTerminal(_profile.profileId());
	}

	private Player ensureObserver()
	{
		if (_observer == null)
		{
			_observer = Player.load(_environment.observer().objectId());
			PhantomAssertions.assertTrue(_observer != null, "Could not load the test-owned observer.");
			_observer.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
			_observer.spawnMe();
		}
		return _observer;
	}

	private Player preparePvpTarget()
	{
		final Player target = ensureObserver();
		if (target.isDead())
		{
			target.doRevive();
		}
		target.abortAttack();
		target.abortCast();
		target.setInvul(false);
		target.setTarget(null);
		target.getAI().setIntention(Intention.IDLE);
		target.getStat().setLevel((byte) 85);
		target.setCurrentHp(target.getMaxHp());
		target.setCurrentMp(target.getMaxMp());
		target.setCurrentCp(target.getMaxCp());
		if (target.isSpawned())
		{
			target.decayMe();
		}
		target.setXYZInvisible(_player.getX() + 30, _player.getY(), _player.getZ());
		target.spawnMe();
		target.revalidateZone(true);
		return target;
	}

	private static LootCandidate exactCandidate(PhantomCombatActorLease lease, Item item)
	{
		return lease.lootCandidates(32, 300).stream().filter(candidate -> candidate.worldObjectId() == item.getObjectId()).findFirst().orElseThrow(() -> new AssertionError("Exact real ground item was not observed."));
	}

	private static void destroyInventoryCount(Player owner, int itemId, long count)
	{
		if (count <= 0)
		{
			return;
		}
		final Item item = owner.getInventory().getItemByItemId(itemId);
		PhantomAssertions.assertTrue((item != null) && (owner.getInventory().destroyItem(ItemProcessType.DESTROY, item, count, owner, PhantomCombatServerIntegrationSuite.class) != null), "Could not restore test-owned inventory baseline.");
	}

	private static void destroyGroundItem(Item item)
	{
		if ((item != null) && (World.getInstance().findObject(item.getObjectId()) != null))
		{
			item.resetOwnerTimer();
			if (item.isSpawned())
			{
				item.decayMe();
			}
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomCombatServerIntegrationSuite.class);
		}
	}

	private void resetActor(boolean revive)
	{
		if (_combat != null)
		{
			_combat.cancel(_profile.profileId());
			consumeTerminal();
		}
		if (_player == null)
		{
			return;
		}
		if (revive && _player.isDead())
		{
			_player.doRevive();
		}
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		_player.abortAttack();
		_player.abortCast();
		_player.setCurrentSkill(null, false, false);
		_player.setInvul(false);
		_player.setTarget(null);
		_player.getAI().setIntention(Intention.IDLE);
		if (_player.getPlayerClass().getId() != MELEE_CLASS_ID)
		{
			_player.setPlayerClass(MELEE_CLASS_ID);
		}
		_player.getStat().setLevel((byte) 85);
		_player.setCurrentHp(_player.getMaxHp());
		_player.setCurrentMp(_player.getMaxMp());
		_player.setCurrentCp(_player.getMaxCp());
	}

	private Item ensureWeapon()
	{
		Item weapon = _player.getInventory().getItemByItemId(WEAPON_ITEM_ID);
		if (weapon == null)
		{
			weapon = _player.getInventory().addItem(ItemProcessType.REWARD, WEAPON_ITEM_ID, 1, _player, this);
		}
		PhantomAssertions.assertTrue(weapon != null, "Could not create the test-owned weapon.");
		if (!weapon.isEquipped())
		{
			_player.getInventory().equipItem(weapon);
		}
		return weapon;
	}

	private void destroyInventoryItem(Item item)
	{
		if (item != null)
		{
			_player.getInventory().destroyItem(ItemProcessType.DESTROY, item, _player, this);
		}
	}

	private Monster spawnNormalMonster(double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		final Monster monster = spawn(new Monster(template), 20 + (_worldFixtures.size() * 5));
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		return monster;
	}

	private Monster spawnAtDistance(double hp, int distance)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		PhantomAssertions.assertTrue(template != null, "Normal-monster template is unavailable.");
		final Monster monster = new Monster(template);
		monster.setInstanceId(0);
		monster.spawnMe(_player.getX() + distance, _player.getY(), _player.getZ());
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		_worldFixtures.add(monster);
		return monster;
	}

	private Monster spawnWithTerritory(int npcId, NpcSpawnTerritory territory, double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "Negative-control normal-monster template is unavailable.");
		final Monster monster = spawn(new Monster(template), 20 + (_worldFixtures.size() * 5), territory);
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		return monster;
	}

	private Monster spawnExactPointMonster(int npcId, double hp)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		PhantomAssertions.assertTrue(template != null, "Exact-point normal-monster template is unavailable.");
		final Monster monster = new Monster(template);
		monster.setInstanceId(0);
		final int offset = 20 + (_worldFixtures.size() * 5);
		try
		{
			final Spawn sourceSpawn = new Spawn(template);
			sourceSpawn.setXYZ(_player.getX() + offset, _player.getY(), _player.getZ());
			monster.setSpawn(sourceSpawn);
		}
		catch (ReflectiveOperationException exception)
		{
			throw new AssertionError("Could not bind exact-point negative-control Spawn.", exception);
		}
		monster.spawnMe(_player.getX() + offset, _player.getY(), _player.getZ());
		monster.setCurrentHp(Math.min(hp, monster.getMaxHp()));
		_worldFixtures.add(monster);
		return monster;
	}

	private <T extends Monster> T spawn(T monster, int xOffset)
	{
		return spawn(monster, xOffset, _sourceTerritory);
	}

	private <T extends Monster> T spawn(T monster, int xOffset, NpcSpawnTerritory territory)
	{
		monster.setInstanceId(0);
		if (territory != null)
		{
			try
			{
				final Spawn sourceSpawn = new Spawn(monster.getTemplate());
				sourceSpawn.setXYZ(_player.getX() + xOffset, _player.getY(), _player.getZ());
				sourceSpawn.setSpawnTerritory(territory);
				monster.setSpawn(sourceSpawn);
			}
			catch (ReflectiveOperationException exception)
			{
				throw new AssertionError("Could not bind factual source Spawn.", exception);
			}
		}
		monster.spawnMe(_player.getX() + xOffset, _player.getY(), _player.getZ());
		_worldFixtures.add(monster);
		return monster;
	}

	private double targetMaximumHp()
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(_combatPoint.npcId());
		return Math.max(1, template.getBaseHpMax());
	}

	private SpawnFact selectCombatPoint()
	{
		final List<NpcFact> candidates = _knowledge.query().snapshot().npcById().values().stream().filter(fact -> (fact.kind() == NpcKind.MONSTER) && fact.attackable() && fact.targetable()).sorted(Comparator.comparingInt(NpcFact::level).thenComparingInt(NpcFact::npcId)).toList();
		for (NpcFact candidate : candidates)
		{
			final Optional<SpawnFact> point = _knowledge.query().snapshot().spawnFactsByNpc().getOrDefault(candidate.npcId(), List.of()).stream().filter(fact -> (fact.pointKind() == SpawnPointKind.EXACT) && (fact.instanceId() == 0)).findFirst();
			if (point.isPresent())
			{
				return point.orElseThrow();
			}
		}
		throw new AssertionError("No deterministic normal-monster spawn fact is available.");
	}

	private void relocateToCombatPoint()
	{
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(_combatPoint.x(), _combatPoint.y(), _combatPoint.z());
		_player.spawnMe();
		_player.revalidateZone(true);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static boolean waitFor(BooleanSupplier condition, long waitMillis) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		return condition.getAsBoolean();
	}

	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override
			public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal)
			{
				return SignalDelivery.ACCEPTED;
			}

			@Override
			public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
			{
				return SignalDelivery.ACCEPTED;
			}
		};
	}

	private static String canonicalDigest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static final class DeterministicSpoilMonster extends Monster
	{
		private int _effectiveLevel = 1;

		private DeterministicSpoilMonster(NpcTemplate template)
		{
			super(template);
		}

		@Override
		public int getLevel()
		{
			return _effectiveLevel;
		}

		private void restoreTemplateLevel()
		{
			_effectiveLevel = getTemplate().getLevel();
		}
	}

	private void cleanup() throws Exception
	{
		if (_mode == Mode.ACQUISITION)
		{
			RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = _spoilChanceRateBaseline;
		}
		Throwable failure = null;
		try
		{
			if (_acquisition != null)
			{
				_acquisition.beginStop();
				PhantomAssertions.assertTrue(_acquisition.finishStop(), "Acquisition integration service did not stop cleanly.");
			}
			if (_combat != null)
			{
				_combat.beginStop();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (!_combat.finishStop() && (System.nanoTime() < deadline))
				{
					Thread.sleep(10);
				}
				PhantomAssertions.assertTrue(_combat.finishStop(), "Combat service did not stop cleanly.");
			}
			if ((_mode == Mode.QOL009_SERVITOR) && (_player != null))
			{
				// setPlayerClass schedules a 100 ms refresh, then sendSkillList schedules a 300 ms future.
				Thread.sleep(500);
				PhantomAssertions.assertTrue(waitFor(() -> !PhantomHeadlessPlayerTestEnvironment.liveFutureFields(_player).contains("Player._skillListTask"), WAIT_MILLIS), "Test-owned class refresh did not drain before materialization shutdown.");
				final var nativeScope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) _player.getNativeWorkOwner();
				System.out.println("QOL009_ABORTED_NATIVE_CAST_DRAIN_BEFORE " + nativeScope.snapshot());
				PhantomAssertions.assertTrue(waitFor(() -> nativeScope.outstanding() == 0, 30_000), "Test-aborted native summon callbacks did not retire at their original due time: " + nativeScope.snapshot());
				System.out.println("QOL009_ABORTED_NATIVE_CAST_DRAIN_AFTER " + nativeScope.snapshot());
			}
			for (Monster fixture : List.copyOf(_worldFixtures))
			{
				if (fixture.isSpawned())
				{
					fixture.deleteMe();
				}
			}
			_worldFixtures.clear();
			if (_observer != null)
			{
				clearSummon(_observer);
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
			if (_materialization != null)
			{
				final var stopped = _materialization.shutdown();
				if ((stopped.state() != PhantomMaterializationService.ServiceState.STOPPED) && Set.of(Mode.PVP_CURRENT_058, Mode.INSTANCES_058, Mode.RIFT_NATIVE_058, Mode.EARNED_LEARNING_058, Mode.TELEPORT_CLEANUP_058).contains(_mode))
				{
					System.err.println("NATIVE058_CLEANUP_FACTS online=" + _player.isOnline() + "; client=" + (_player.getClient() == null ? "null" : _player.getClient().getClass().getName()) + "; worldPlayer=" + World.getInstance().getPlayer(_player.getObjectId()) + "; worldObject=" + World.getInstance().findObject(_player.getObjectId()) + "; autoSave=" + org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(_player.getObjectId()) + "; futures=" + PhantomHeadlessPlayerTestEnvironment.liveFutureFields(_player));
				}
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, stopped.state(), "Materialization service did not stop.");
			}
			if ((_repository != null) && (_profile != null))
			{
				_repository.find(_profile.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
				for (PhantomProfile additional : _additionalProfiles058)
				{
					_repository.find(additional.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
				}
				_additionalProfiles058.clear();
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Game Knowledge service did not stop.");
			}
			if (_backgroundProduction != null)
			{
				_backgroundProduction.close();
				_backgroundProduction = null;
			}
			if (_player != null)
			{
				clearSummon(_player);
				_environment.assertClean(_environment.primary(), _player);
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
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
