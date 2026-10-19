/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.FactKind;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomRealPartyInvitationPolicy;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Two fresh native JVMs around ordinary058a restart; no persistent actor deletion or recreation. */
public final class PhantomSocialMemory058
{
	private static final long PROFILE = 11;
	private static final int HUMAN = 268435465;
	private static final int COMPANION = 268436865;
	private PhantomSocialMemory058() { }

	public static void main(String[] args) throws Exception
	{
		if ((args.length != 2) || !List.of("prepare059", "prepare-raid059", "upgrade-career061", "record", "recall", "recall-legacy").contains(args[0]) || !args[1].matches("[a-z0-9-]{1,30}") || !PhantomCampaign058PersistentLane.enabled()) { throw new IllegalArgumentException("Explicit persistent058a record/recall and unique episode label required."); }
		final boolean record = args[0].equals("record");
		final boolean prepare059 = args[0].equals("prepare059") || args[0].equals("prepare-raid059");
		final boolean production059 = Boolean.getBoolean("phantom.test.productionGrounding059");
		final Path lane = PhantomCampaign058PersistentLane.LANE;
		final boolean continuation = Boolean.getBoolean("phantom.test.continuation059");
		final boolean continuation061 = Boolean.getBoolean("phantom.test.continuation061");
		final boolean upgrade061 = args[0].equals("upgrade-career061");
		if (upgrade061 && (!continuation061 || !production059)) { throw new IllegalArgumentException("Exact061 known-generation upgrade requires production grounding."); }
		if (continuation061 && (!continuation || (!args[0].equals("recall-legacy") && !upgrade061))) { throw new IllegalArgumentException("TASK061 permits original recall or its exact known-generation upgrade only."); }
		if (args[0].equals("recall-legacy") && !continuation) { throw new IllegalArgumentException("Legacy recall requires explicit continuation evidence scope."); }
		final Path evidence = continuation061 ? lane.resolve("task061/m3-memory-" + args[1]) : continuation ? lane.resolve("task059/m3-memory-" + args[1]) : lane;
		if (continuation061) { PhantomAssertions.assertFalse(Files.exists(evidence), "TASK061 recall evidence cannot be replayed."); }
		PhantomCampaign058PersistentLane.validateFiles(PhantomCampaign058PersistentLane.MODULE, Path.of(System.getProperty("phantom.test.config", "")), true);
		// Reuse the existing exact ordinary-JAR allowlist and four-owner provenance check; never overlay product classes.
		for (int port : List.of(2138, 7838, 9138)) { try (var socket = new ServerSocket(port)) { PhantomAssertions.assertTrue(socket.isBound(), "Own058a must be stopped."); } }
		final Path receipt = (args[0].equals("recall-legacy") ? lane : evidence).resolve("m3-social-memory.record");
		if (record) { PhantomAssertions.assertFalse(Files.exists(receipt), "M3 recorded episode cannot be replayed."); Files.createDirectories(evidence); Files.createFile(evidence.resolve("m3-social-memory-" + args[1] + ".started")); }
		else if (!prepare059 && !upgrade061) { PhantomAssertions.assertTrue(Files.isRegularFile(receipt), "M3 original native episode is missing."); }
		if (prepare059) { PhantomAssertions.assertTrue(continuation && production059, "Exact TEST context preparation requires production grounding and059 evidence."); }
		final var context = new PhantomTestContext(58005828, PhantomCampaign058PersistentLane.MODULE, evidence.resolve("reports/m3-" + args[0]));
		PhantomCombatServerIntegrationSuite.assertCampaign058Jar(context);
		System.out.println("M3_INPUT phase=" + args[0] + "; episode=" + args[1] + "; profile=" + PROFILE + "; human=" + HUMAN + "; " + context.measurements());
		PhantomMaterializationService materialization = null;
		PhantomSocialService social = null;
		PhantomPartyCoordinator party = null;
		PhantomGameKnowledgeService knowledge = null;
		Player human = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(context);
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MapRegionData.getInstance();
			final var profiles = PhantomProfileRepository.open();
			PhantomAssertions.assertEquals(COMPANION, profiles.find(PROFILE).orElseThrow().characterObjectId(), "Prepared Scout identity changed.");
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(materialization.start(), "M3 materialization start failed.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(PROFILE).status(), "Prepared Scout was not materialized.");
			final Player companion = World.getInstance().getPlayer(COMPANION);
			human = Player.load(HUMAN);
			PhantomAssertions.assertTrue((human != null) && human.getName().equals("TestAdmin") && !human.isGM() && (companion != null), "Exact ordinary TEST actors missing.");
			human.setOnlineStatus(true, false); human.spawnMe();
			final var topologyBackend = new L2jTopologyValidationBackend();
			final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), knowledgePolicy), topology, knowledgePolicy));
			PhantomAssertions.assertTrue(knowledge.start(), "M3 knowledge start failed.");
			final var progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, Path.of("."), knowledge::query), PhantomProgressionPolicy.productionDefaults());
			progression.start();
			final var socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
			if (continuation061)
			{
				final var stored = new PhantomConversationStore(profiles).load(PROFILE).orElseThrow().state();
				final var current = PhantomSemanticGrounding.production(knowledge.query(), topology, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"))).hashes();
				System.out.println("M3_GROUNDING061 production=" + production059 + "; storedKnowledge=" + stored.knowledgeHash() + "; storedTopology=" + stored.topologyHash() + "; storedRole=" + stored.roleHash() + "; current=" + current);
			}
			social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(profiles, socialCatalog), 58005823, 32);
			PhantomAssertions.assertTrue(social.start(), "M3 social start failed.");
			final var personal = PhantomPersonalConversationStore.production(profiles);
			final var humanized = new PhantomHumanizedConversationService(PhantomHumanizedCatalog.loadV3(Path.of("data/phantoms"), true), personal, social, new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false));
			if (upgrade061)
			{
				upgradeCareerContext061(profiles, knowledge, topology, socialCatalog, evidence);
				progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Upgrade progression did not stop.");
				return;
			}
			if (prepare059)
			{
				if (args[0].equals("prepare-raid059")) { prepareRaidKnowledgeContext059(profiles, knowledge, topology, socialCatalog, evidence); }
				else { prepareProductionContext059(profiles, knowledge, topology, socialCatalog, evidence); }
				progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Prepared progression did not stop.");
				return;
			}
			if (production059 && !record)
			{
				final var recovered = new PhantomPartyStore(profiles).load(PROFILE).orElseThrow().state();
				PhantomAssertions.assertTrue(recovered.command() == null && !human.isInParty() && !companion.isInParty(), "Ordinary game restart retained an old HOLD or fabricated the previous native party.");
				System.out.println("M3_ORDINARY_RESTART_NO_STALE_HOLD status=" + recovered.status() + "; noNativeParty=true");
			}
			final var backend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			party = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), new PhantomGoalStateStore(profiles), backend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> topology.snapshot().canonicalHash(), System::nanoTime, 32, social, () -> System.currentTimeMillis() / 60000L);
			PhantomAssertions.assertTrue(party.start(), "M3 canonical coordinator start failed.");
			party.installManagedInvitationPolicy(PhantomRealPartyInvitationPolicy.POLICY_KEY, new PhantomRealPartyInvitationPolicy(PhantomRealPartyInvitationPolicy.loadSettings(Path.of("data/phantoms/party/high-five-real-party-invitation-v1.xml")), social, () -> System.currentTimeMillis() / 60000L));
			final SubjectRef subject = SubjectRef.character(HUMAN);
			try (var composition = new PhantomCompanionComposition058(profiles, materialization, topology, knowledge, social, party, human, humanized))
			{
				if (record)
				{
					final var beforeJoin059 = social.snapshot(PROFILE, subject, 24, now()).value().memories().stream().map(memory -> memory.eventId()).toList();
					final var invited = PartyInvitationService.getInstance().invite(human, companion, PartyDistributionType.FINDERS_KEEPERS.getId());
					for (int pulse = 0; (pulse < 128) && !human.isInParty(); pulse++) { party.onPulse(); Thread.sleep(10); }
					PhantomAssertions.assertTrue(human.isInParty() && (human.getParty() == companion.getParty()), "M3 actual human/Phantom canonical join failed: " + invited);
					for (int pulse = 0; pulse < 32; pulse++) { party.onPulse(); Thread.sleep(10); }
					System.out.println("M3_CANONICAL_JOIN party=" + party.snapshot() + "; social=" + social.snapshot());
					final var snapshot = social.snapshot(PROFILE, subject, 24, now()).value();
					final var joined = snapshot.memories().stream().filter(memory -> memory.eventKey().equals("party.member.joined") && memory.subject().equals(subject) && !beforeJoin059.contains(memory.eventId())).findFirst().orElseThrow(() -> new AssertionError("Fresh native joint event was not remembered for TestAdmin."));
					final String reply = composition.socialMessage(human, companion, "я люблю старый рок");
					PhantomAssertions.assertTrue(personal.load(PROFILE).isPresent(), "Delivered native statement did not persist bounded personal memory.");
					if (production059)
					{
						PhantomAssertions.assertTrue(composition.command059(context, human, companion, "стой здесь", party), "M3 production-grounded HOLD was not delivered.");
						composition.assertCommandReason059("command.holding");
						final var held059 = new PhantomPartyStore(profiles).load(PROFILE).orElseThrow().state().command();
						PhantomAssertions.assertTrue(held059 != null && held059.mode() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, "M3 HOLD was not durable before normal owner stop.");
						Files.writeString(evidence.resolve("hold-before-normal-stop.base64"), java.util.Base64.getEncoder().encodeToString(profiles.findComponent(PROFILE, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.COMPONENT_TYPE).orElseThrow().payload()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
						party.beginStop(); PhantomAssertions.assertTrue(party.finishStop(), "M3 normal party owner stop did not drain.");
						System.out.println("M3_NATIVE_HOLD_SAVED command=" + held059 + "; normalOwnerStop=true; next ordinary game boot must invalidate missing native party");
					}
					else
					{
						PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().leave(human), "M3 ordinary party leave failed.");
						party.onPulse();
					}
					Files.writeString(receipt, joined.eventId() + "\n" + snapshot.personality().seed() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
					System.out.println("M3_NATIVE_RECORDED event=" + joined.eventId() + "; subject=character.object:" + HUMAN + "; response=" + reply + "; boundedComponentBytes=" + profiles.findComponent(PROFILE, PhantomPersonalConversationStore.COMPONENT_TYPE).orElseThrow().payload().length);
				}
				else
				{
					final var expected = Files.readAllLines(receipt, StandardCharsets.UTF_8);
					final var component = profiles.findComponent(PROFILE, PhantomSocialModel.COMPONENT_TYPE).orElseThrow();
					final var snapshot = social.snapshot(PROFILE, subject, 24, now()).value();
					PhantomAssertions.assertTrue(snapshot.memories().stream().anyMatch(memory -> memory.eventId().equals(expected.get(0)) && memory.subject().equals(subject)), "Ordinary restart lost actual joint-event memory.");
					PhantomAssertions.assertEquals(expected.get(1), Long.toString(snapshot.personality().seed()), "Personality changed across restart.");
					PhantomAssertions.assertTrue(social.snapshot(PROFILE, SubjectRef.character(268436779), 24, now()).value().memories().stream().noneMatch(memory -> memory.eventId().equals(expected.get(0))), "Joint event leaked to a different character.");
					PhantomAssertions.assertEquals(component.rowVersion(), profiles.findComponent(PROFILE, PhantomSocialModel.COMPONENT_TYPE).orElseThrow().rowVersion(), "Read-only memory queries wrote social state.");
					final String reply = composition.socialMessage(human, companion, "что я люблю");
					PhantomAssertions.assertTrue(reply.contains("старый рок"), "Native delivered restart reply did not recall the actual counterpart's structured fact: " + reply);
					PhantomAssertions.assertTrue(PhantomPersonalConversationStore.recall(personal.load(PROFILE).orElseThrow().state(), "character.object:268436779", FactKind.LIKE, now()).isEmpty(), "Personal fact leaked to another character.");
					System.out.println("M3_NATIVE_RECALLED event=" + expected.get(0) + "; subject=character.object:" + HUMAN + "; response=" + reply + "; readOnlyQueries=true; isolated=true; generatedRecursion=false");
					final var beforeJoin = snapshot.memories().stream().map(memory -> memory.eventId()).toList();
					final var invited = PartyInvitationService.getInstance().invite(human, companion, PartyDistributionType.FINDERS_KEEPERS.getId());
					for (int pulse = 0; pulse < 64; pulse++) { party.onPulse(); Thread.sleep(10); }
					PhantomAssertions.assertTrue(human.isInParty() && (human.getParty() == companion.getParty()), "M3 new lawful join after restart failed.");
					final var newJoined = social.snapshot(PROFILE, subject, 24, now()).value().memories().stream().filter(memory -> memory.eventKey().equals("party.member.joined") && !beforeJoin.contains(memory.eventId())).findFirst().orElseThrow(() -> new AssertionError("New JVM invitation reused a historical joint-event receipt."));
					final long writes = social.snapshot().durableWrites();
					final var identity = invited.identity();
					party.terminal(new PartyInvitation(identity, HUMAN, human.getName(), COMPANION, companion.getName(), PartyDistributionType.FINDERS_KEEPERS, HUMAN, Long.MAX_VALUE), java.util.OptionalLong.empty(), java.util.OptionalLong.of(PROFILE), TerminalOutcome.ACCEPTED, "");
					for (int pulse = 0; pulse < 16; pulse++) { party.onPulse(); Thread.sleep(10); }
					PhantomAssertions.assertEquals(writes, social.snapshot().durableWrites(), "Exact accepted callback retry duplicated persisted social effects.");
					PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().leave(human), "M3 restarted normal party leave failed.");
					party.onPulse();
					System.out.println("M3_NEW_JVM_JOIN freshEvent=" + newJoined.eventId() + "; exactCallbackRetryWriteFree=true");
					Files.createDirectories(evidence);
					Files.writeString(evidence.resolve("m3-social-memory.complete"), "Two native JVMs around ordinary sameDB restart; owner UI QA_PENDING", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				}
			}
			progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "M3 progression did not stop.");
		}
		finally
		{
			if ((human != null) && human.isInParty()) { PartyInvitationService.getInstance().leave(human); }
			if (party != null) { party.beginStop(); party.finishStop(); }
			if (social != null) { social.beginStop(); social.finishStop(); }
			if (knowledge != null) { knowledge.beginStop(); knowledge.finishStop(); }
			if (materialization != null) { materialization.shutdown(); }
			// Player.java:12195/12309: deleteMe is native World teardown + online=false, not SQL character deletion.
			// This is the same storeMe/deleteMe lifecycle used by the already approved persistent scene helper.
			if (human != null) { human.stopAllTasks(); human.storeMe(); human.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static long now() { return System.currentTimeMillis() / 60000L; }

	/** Exact additive catalog generation migration for stopped cumulative058a; unknown old authority is refused. */
	private static void prepareRaidKnowledgeContext059(PhantomProfileRepository profiles, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomSocialCatalog socialCatalog, Path evidence) throws Exception
	{
		Files.createDirectories(evidence);
		final Path marker = evidence.resolve("production-context.prepare-raid059");
		PhantomAssertions.assertFalse(Files.exists(marker), "Known catalog generation migration cannot be replayed.");
		final Path curated = Path.of("data/phantoms/knowledge");
		try (var files = Files.list(curated))
		{
			PhantomAssertions.assertEquals(java.util.Set.of("high-five-core-v1.xml", "high-five-supported-raids-v1.xml"), files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()), "Unexpected curated source set; exact migration refused.");
		}
		final byte[] core = Files.readAllBytes(curated.resolve("high-five-core-v1.xml"));
		PhantomAssertions.assertEquals("CE11D37497D9B7FD4410A4A405F21BBF0A61A9D9C51D81E2D50A356ACE499915", java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(core)), "Historical core source differs; no blind knowledge rewrite.");
		final byte[] previousCore = Files.readAllBytes(PhantomCampaign058PersistentLane.LANE.resolve("task059/preimages/6D11AA8CD9F468EEDF5A9A192CE30359A7D3F82361629D24277F4EE9E33C8CB3/.phantom-local/contract058a/runtime/game/data/phantoms/knowledge/high-five-core-v1.xml"));
		PhantomAssertions.assertEquals("6D11AA8CD9F468EEDF5A9A192CE30359A7D3F82361629D24277F4EE9E33C8CB3", java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(previousCore)), "Exact prior runtime core preimage differs.");
		final Path prior = evidence.resolve("prior-curated-knowledge"); Files.createDirectory(prior);
		Files.write(prior.resolve("high-five-core-v1.xml"), previousCore, StandardOpenOption.CREATE_NEW);
		final var policy = PhantomGameKnowledgePolicy.productionDefaults();
		final var previousKnowledge = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(prior, new L2jGameKnowledgeBackend(), policy), topology, policy).build();
		final String previousHash = previousKnowledge.combinedHash().toUpperCase(java.util.Locale.ROOT);
		PhantomAssertions.assertEquals("4CB63B307250E3DE3DA97935368C0D0574642AA3420BDA3969752949724D3027", previousHash, "Native reconstruction of the exact prior knowledge generation failed.");
		final var roles = PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"));
		final var grounding = PhantomSemanticGrounding.production(knowledge.query(), topology, roles);
		final var hashes = grounding.hashes();
		final var pack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v3.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v3.tsv"), grounding);
		final var catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v3.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		final var store = new PhantomConversationStore(profiles);
		final var inventory = profiles.listManagedAfter(org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.COMPONENT_TYPE, 0, 256);
		PhantomAssertions.assertTrue(inventory.size() < 256, "Conversation inventory exceeds this exact migration bound.");
		for (var item : inventory)
		{
			final var state = store.load(item.profile().profileId()).orElseThrow().state();
			System.out.println("M3_CONTEXT_INVENTORY profile=" + item.profile().profileId() + "; knowledge=" + state.knowledgeHash() + "; mutationAllowed=" + (item.profile().profileId() == PROFILE));
		}
		final var before = store.load(PROFILE).orElseThrow(); final var old = before.state();
		final var executionCatalog = org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
		new org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore(profiles, executionCatalog).load(PROFILE).ifPresent(value ->
		{
			System.out.println("M3_EXECUTION_INVENTORY catalog=" + value.state().catalogHash() + "; entries=" + value.state().entries().size() + "; receipts=" + value.state().receipts().size());
			PhantomAssertions.assertTrue(value.state().entries().stream().allMatch(entry -> entry.terminal()), "Pending execution must reach its existing native lifecycle boundary before knowledge migration.");
		});
		PhantomAssertions.assertTrue(old.knowledgeHash().equals(previousHash) && old.topologyHash().equals(hashes.topologyHash()) && old.roleHash().equals(hashes.partyRoleHash()) && old.socialHash().equals(socialCatalog.hash()) && !old.knowledgeHash().equals(hashes.knowledgeHash()), "Only the reconstructed prior world generation may be migrated.");
		final var languageTarget = new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), old.knowledgeHash(), old.topologyHash(), old.roleHash(), old.socialHash(), old.logicalMinute(), List.of(), List.of());
		final var language = catalog.upgradeLanguageState(old, languageTarget).orElseThrow();
		final var target = new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), hashes.knowledgeHash(), hashes.topologyHash(), hashes.partyRoleHash(), old.socialHash(), language.logicalMinute(), language.sessions(), language.recentObservationHashes());
		final var protectedComponents = new java.util.LinkedHashMap<String,byte[]>();
		for (String type : List.of(PhantomSocialModel.COMPONENT_TYPE, PhantomPersonalConversationStore.COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.COMPONENT_TYPE)) { profiles.findComponent(PROFILE, type).ifPresent(value -> protectedComponents.put(type, value.payload())); }
		final var component = profiles.findComponent(PROFILE, org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(before.rowVersion(), component.rowVersion(), "Conversation changed before migration preimage.");
		Files.writeString(evidence.resolve("conversation-context-preimage.base64"), java.util.Base64.getEncoder().encodeToString(component.payload()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final var saved = store.save(PROFILE, before.rowVersion(), target);
		for (var entry : protectedComponents.entrySet()) { PhantomAssertions.assertTrue(java.util.Arrays.equals(entry.getValue(), profiles.findComponent(PROFILE, entry.getKey()).orElseThrow().payload()), "Migration changed memory or execution receipts: " + entry.getKey()); }
		PhantomAssertions.assertEquals(old.recentObservationHashes(), saved.state().recentObservationHashes(), "Migration lost inbound replay protection.");
		PhantomAssertions.assertEquals(old.sessions().size(), saved.state().sessions().size(), "Migration lost counterpart sessions.");
		Files.writeString(marker, "profile=" + PROFILE + "; oldRow=" + before.rowVersion() + "; newRow=" + saved.rowVersion() + "; reconstructedOldKnowledge=" + previousKnowledge.combinedHash() + "; current=" + hashes + "; memory/execution unchanged; replay/cooldowns retained; pending cleared", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("M3_KNOWN_GENERATION_MIGRATED " + Files.readString(marker));
	}

	/** Versioned additive topology upgrade, atomically limited to the two stopped owned schemas. */
	private static void upgradeCareerContext061(PhantomProfileRepository profiles, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomSocialCatalog socialCatalog, Path evidence) throws Exception
	{
		Files.createDirectories(evidence);
		final Path release = Path.of("D:/PhantomWorld/World-Career-061-candidate");
		PhantomAssertions.assertTrue(Files.isRegularFile(PhantomCampaign058PersistentLane.LANE.resolve("m3-social-memory.record")), "Original memory receipt required; no bootstrap.");
		final var owner = new java.util.Properties();
		try (var reader = Files.newBufferedReader(release.resolve("ownership061.properties"), StandardCharsets.UTF_8)) { owner.load(reader); }
		PhantomAssertions.assertTrue("TASK061_CAMPAIGN".equals(owner.getProperty("owner")) && "RELEASE_CANDIDATE".equals(owner.getProperty("role")) && "l2jmobiush5_phantom_release_061a".equals(owner.getProperty("schema")) && release.equals(Path.of(owner.getProperty("runtimeRoot", ""))), "Exact061 release binding required.");
		for (int port : List.of(Integer.parseInt(owner.getProperty("clientLoginPort")), 7841, 9141)) { try (var socket = new ServerSocket(port)) { PhantomAssertions.assertTrue(socket.isBound(), "Release061 must remain stopped during versioned upgrade."); } }
		PhantomAssertions.assertTrue(Files.isRegularFile(PhantomCampaign058PersistentLane.LANE.resolve("task061/release061-export.complete")) && !Files.exists(release.resolve("local-play/pids/GameServer.json")), "Upgrade requires the one final export and no prior release boot.");
		for (String file : List.of("data/Seeds.xml", "data/phantoms/knowledge/high-five-core-v1.xml", "data/phantoms/knowledge/high-five-supported-raids-v1.xml", "data/phantoms/party/high-five-party-roles-v1.xml"))
		{ PhantomAssertions.assertTrue(Files.readString(Path.of(file), StandardCharsets.UTF_8).replace("\r\n", "\n").equals(Files.readString(release.resolve("game").resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n")), "Source/release authority text differs beyond CRLF: " + file); }
		try (var sourceFiles = Files.list(Path.of("data/phantoms/topology")); var releaseFiles = Files.list(release.resolve("game/data/phantoms/topology")))
		{
			PhantomAssertions.assertEquals(sourceFiles.filter(path -> path.toString().endsWith(".xml")).map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()), releaseFiles.filter(path -> path.toString().endsWith(".xml")).map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()), "Source/release topology inventory differs.");
		}
		final Path prior = evidence.resolve("prior-topology"); Files.createDirectory(prior);
		try (var files = Files.list(Path.of("data/phantoms/topology")))
		{
			for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".xml")).toList())
			{
				final String name = file.getFileName().toString();
				PhantomAssertions.assertTrue(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").equals(Files.readString(release.resolve("game/data/phantoms/topology").resolve(name), StandardCharsets.UTF_8).replace("\r\n", "\n")), "Source/release topology differs beyond CRLF.");
				if (!name.equals("high-five-career-recovery.xml")) { Files.copy(file, prior.resolve(name)); }
			}
		}
		final var backend = new L2jTopologyValidationBackend();
		final var oldTopology = new PhantomTopologyQuery(new PhantomTopologyLoader(prior, backend, PhantomTopologyPolicy.productionDefaults()).load(1), backend, new PhantomTopologyMetrics());
		PhantomAssertions.assertTrue("CFF8CC0C758E460FE05752211258EF64F416F9DA86E6F47FCAFAA0F322CB51AC".equalsIgnoreCase(oldTopology.snapshot().canonicalHash()), "Exact prior topology reconstruction failed.");
		final var policy = PhantomGameKnowledgePolicy.productionDefaults();
		final var oldKnowledge = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), policy), oldTopology, policy).build();
		final var before = new PhantomConversationStore(profiles).load(PROFILE).orElseThrow(); final var old = before.state();
		PhantomAssertions.assertTrue(old.knowledgeHash().equalsIgnoreCase(oldKnowledge.combinedHash()) && old.knowledgeHash().equals("AD040BF1FE1784F3061044A7D4B8940BF7F51C37AB28A59F63FBACC944D51508") && old.topologyHash().equalsIgnoreCase(oldTopology.snapshot().canonicalHash()) && old.socialHash().equals(socialCatalog.hash()), "Unknown old context refused.");
		final var executionCatalog = org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
		new org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore(profiles, executionCatalog).load(PROFILE).ifPresent(value -> PhantomAssertions.assertTrue(value.state().entries().stream().allMatch(entry -> entry.terminal()), "Pending execution upgrade refused."));
		final var grounding = PhantomSemanticGrounding.production(knowledge.query(), topology, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")));
		final var hashes = grounding.hashes();
		PhantomAssertions.assertEquals(old.roleHash(), hashes.partyRoleHash(), "Role generation upgrade refused.");
		final var releaseTopology = new PhantomTopologyQuery(new PhantomTopologyLoader(release.resolve("game/data/phantoms/topology"), backend, PhantomTopologyPolicy.productionDefaults()).load(1), backend, new PhantomTopologyMetrics());
		final var releaseKnowledge = new PhantomGameKnowledgeBuilder(new L2jGameKnowledgeBackend(), new PhantomStaticManorParser(release.resolve("game/data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(release.resolve("game/data/phantoms/knowledge"), new L2jGameKnowledgeBackend(), policy), releaseTopology, policy).build();
		PhantomAssertions.assertEquals(topology.snapshot().canonicalHash(), releaseTopology.snapshot().canonicalHash(), "Release native topology generation differs.");
		PhantomAssertions.assertEquals(knowledge.query().snapshot().combinedHash(), releaseKnowledge.combinedHash(), "Release native knowledge generation differs.");
		final var pack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v3.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v3.tsv"), grounding);
		final var catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v3.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		final var releasePack = PhantomSemanticPack.load(release.resolve("game/data/phantoms/semantic/high-five-ru-semantic-v3.xml"), release.resolve("game/data/phantoms/semantic/high-five-ru-corpus-v3.tsv"), grounding);
		PhantomAssertions.assertTrue(pack.packHash().equals(releasePack.packHash()) && pack.corpusHash().equals(releasePack.corpusHash()) && catalog.hash().equals(PhantomConversationCatalog.load(release.resolve("game/data/phantoms/conversation/high-five-ru-conversation-v3.xml"), release.resolve("game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv")).hash()) && socialCatalog.hash().equals(PhantomSocialCatalog.load(release.resolve("game/data/phantoms/social/high-five-social-v1.xml")).hash()), "Release native conversation authority generation differs.");
		final var language = catalog.upgradeLanguageState(old, new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), old.knowledgeHash(), old.topologyHash(), old.roleHash(), old.socialHash(), old.logicalMinute(), List.of(), List.of())).orElseThrow();
		final var target = new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), hashes.knowledgeHash(), hashes.topologyHash(), old.roleHash(), old.socialHash(), language.logicalMinute(), language.sessions(), language.recentObservationHashes());
		PhantomAssertions.assertEquals(old.recentObservationHashes(), target.recentObservationHashes(), "Replay history lost.");
		PhantomAssertions.assertEquals(old.sessions().size(), target.sessions().size(), "Counterpart sessions lost.");
		final var codec = new org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStateCodec();
		final byte[] preimage = codec.encode(old); final byte[] upgraded = codec.encode(target);
		Files.write(evidence.resolve("conversation-context-preimage.bin"), preimage, StandardOpenOption.CREATE_NEW);
		Files.write(evidence.resolve("conversation-context-upgraded.bin"), upgraded, StandardOpenOption.CREATE_NEW);
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				for (String schema : List.of("l2jmobiush5_phantom_test_058a", "l2jmobiush5_phantom_release_061a"))
				{
					try (var check = connection.prepareStatement("SELECT row_version,payload FROM " + schema + ".phantom_profile_components WHERE profile_id=11 AND component_type='conversation.state' FOR UPDATE"); var rows = check.executeQuery())
					{ PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == before.rowVersion() && java.util.Arrays.equals(preimage, rows.getBytes(2)) && !rows.next(), "Exact exported context preimage differs; no upgrade."); }
				}
				for (String schema : List.of("l2jmobiush5_phantom_test_058a", "l2jmobiush5_phantom_release_061a"))
				{
					try (var update = connection.prepareStatement("UPDATE " + schema + ".phantom_profile_components SET payload=?,row_version=row_version+1 WHERE profile_id=11 AND component_type='conversation.state' AND row_version=? AND payload=?"))
					{ update.setBytes(1, upgraded); update.setLong(2, before.rowVersion()); update.setBytes(3, preimage); PhantomAssertions.assertEquals(1, update.executeUpdate(), "Versioned upgrade CAS failed."); }
				}
				connection.commit();
			}
			catch (Throwable failure) { connection.rollback(); throw failure; }
		}
		Files.writeString(evidence.resolve("context-upgrade061.complete"), "source058a+release061a; profile11 only; old generation reconstructed; rowVersion=" + before.rowVersion() + "->" + (before.rowVersion() + 1) + "; no other columns/rows; memory/execution/earned untouched; current=" + hashes, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("M3_KNOWN_GENERATION_UPGRADE061 " + Files.readString(evidence.resolve("context-upgrade061.complete")));
	}

	/** Explicit TEST setup migration, before a new episode; never a product authority bypass. */
	private static void prepareProductionContext059(PhantomProfileRepository profiles, PhantomGameKnowledgeService knowledge, PhantomTopologyQuery topology, PhantomSocialCatalog socialCatalog, Path evidence) throws Exception
	{
		Files.createDirectories(evidence);
		final Path marker = evidence.resolve("production-context.prepare059");
		PhantomAssertions.assertFalse(Files.exists(marker), "Exact TEST context preparation cannot be replayed.");
		final var roles = PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"));
		final var grounding = PhantomSemanticGrounding.production(knowledge.query(), topology, roles);
		PhantomAssertions.assertTrue(grounding.resolve(org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType.CAPABILITY, "combat.recharge").isPresent() && grounding.resolve(org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType.CAPABILITY, "combat.unknown059").isEmpty(), "Production grounding omitted a catalog capability or admitted an unknown key.");
		final var pack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v3.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v3.tsv"), grounding);
		final var catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v3.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		final var store = new PhantomConversationStore(profiles);
		final var before = store.load(PROFILE).orElseThrow();
		final var old = before.state();
		final var fixed = PhantomConversationIntegrationSuite.HASHES;
		PhantomAssertions.assertTrue(old.knowledgeHash().equals(fixed.knowledgeHash()) && old.topologyHash().equals(fixed.topologyHash()) && old.roleHash().equals(fixed.partyRoleHash()) && old.socialHash().equals(socialCatalog.hash()), "Only the exact historical TEST grounding may be prepared; unknown authority refused.");
		final var languageTarget = new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), old.knowledgeHash(), old.topologyHash(), old.roleHash(), old.socialHash(), old.logicalMinute(), List.of(), List.of());
		final var language = catalog.upgradeLanguageState(old, languageTarget).orElseThrow();
		final var hashes = grounding.hashes();
		final var target = new ConversationState(catalog.hash(), pack.packHash(), pack.corpusHash(), hashes.knowledgeHash(), hashes.topologyHash(), hashes.partyRoleHash(), old.socialHash(), language.logicalMinute(), language.sessions(), language.recentObservationHashes());
		final var component = profiles.findComponent(PROFILE, org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.COMPONENT_TYPE).orElseThrow();
		PhantomAssertions.assertEquals(before.rowVersion(), component.rowVersion(), "TEST context changed before preimage.");
		Files.writeString(evidence.resolve("conversation-context-preimage.base64"), java.util.Base64.getEncoder().encodeToString(component.payload()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final var saved = store.save(PROFILE, before.rowVersion(), target);
		Files.writeString(marker, "profile=" + PROFILE + "; oldRow=" + before.rowVersion() + "; newRow=" + saved.rowVersion() + "; exact fixed TEST grounding -> current production grounding; personal/joint memory, skills, items, SP untouched", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("M3_TEST_CONTEXT_PREPARED profile=" + PROFILE + "; oldRow=" + before.rowVersion() + "; newRow=" + saved.rowVersion() + "; productionHashes=" + hashes);
	}
}
