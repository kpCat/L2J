/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.PlayerReference;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialReceiptLedger;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialReceiptLedger.Receipt;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialReceiptLedger.ReceiptStatus;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

public final class PhantomActivationGateSuite implements PhantomTestSuite
{
	public enum Mode
	{
		SOCIAL,
		SEMANTIC
	}

	private static final long SEED = 20002001L;
	private final Mode _mode;
	private final List<Long> _profiles = new ArrayList<>();
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _repository;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomSemanticUnderstandingService _semantic;
	private PhantomSemanticPack _pack;

	public PhantomActivationGateSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode == Mode.SOCIAL ? "social-activation" : "semantic-activation";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 020 Checkpoint 1 used the wrong seed.");
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		_repository = PhantomProfileRepository.open();
		if (_mode == Mode.SEMANTIC)
		{
			MapRegionData.getInstance();
			SpawnData.getInstance();
			DoorData.getInstance();
			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
			final L2jGameKnowledgeBackend knowledgeBackend = new L2jGameKnowledgeBackend();
			_knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, policy), topology, policy));
			PhantomAssertions.assertTrue(_knowledge.start(), "Production Game Knowledge did not start for semantic activation.");
			final PhantomPartyRoleCatalog roles = PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"));
			final var authority = PhantomSemanticGrounding.production(_knowledge.query(), topology, roles);
			_pack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v1.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), authority);
			_semantic = PhantomSemanticUnderstandingService.loaded(_pack);
			PhantomAssertions.assertTrue(_semantic.start(), "Production semantic service did not start for activation.");
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_semantic != null)
		{
			_semantic.beginStop();
			_semantic.finishStop();
		}
		if (_knowledge != null)
		{
			_knowledge.beginStop();
			_knowledge.finishStop();
		}
		for (long profileId : _profiles)
		{
			_repository.find(profileId).ifPresent(profile -> _repository.delete(profileId, profile.rowVersion()));
		}
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.SOCIAL)
		{
			social(registry);
		}
		else
		{
			semantic(registry);
		}
	}

	private void social(PhantomTestRegistry registry)
	{
		registry.add("01-receipt-worst-case-round-trips-below-component-bound", context ->
		{
			final List<Receipt> receipts = new ArrayList<>();
			for (int index = 0; index < PhantomSocialReceiptLedger.MAX_RECEIPTS; index++)
			{
				receipts.add(new Receipt(PhantomSocialModel.sha256("receipt|" + index), 1007, 1000 + index, 130600 + index, (index & 1) == 0 ? ReceiptStatus.APPLIED : ReceiptStatus.STALE));
			}
			receipts.sort(java.util.Comparator.comparing(Receipt::eventId));
			final PhantomSocialReceiptLedger ledger = new PhantomSocialReceiptLedger(receipts);
			final byte[] encoded = ledger.encode();
			PhantomAssertions.assertTrue(encoded.length <= 4096, "Maximum social receipt ledger exceeds the component envelope.");
			PhantomAssertions.assertEquals(ledger.receipts(), PhantomSocialReceiptLedger.decode(encoded).receipts(), "Social receipt round-trip changed exact receipts.");
			context.record("social.receipts.worstCaseBytes", encoded.length);
		});

		registry.add("02-generic-multi-component-write-rolls-back-on-late-conflict", context ->
		{
			final PhantomProfile profile = profile();
			final List<PhantomProfileComponent> inserted = _repository.mutateComponentsAtomically(profile.profileId(), List.of(new ComponentMutation("activation.a", -1, 1, new byte[] { 1 }), new ComponentMutation("activation.b", -1, 1, new byte[] { 2 })));
			PhantomAssertions.assertEquals(2, inserted.size(), "Atomic insert did not return both winners.");
			PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> _repository.mutateComponentsAtomically(profile.profileId(), List.of(new ComponentMutation("activation.a", inserted.get(0).rowVersion(), 1, new byte[] { 3 }), new ComponentMutation("activation.b", inserted.get(1).rowVersion() + 1, 1, new byte[] { 4 }))), "Late component conflict did not abort the atomic transaction.");
			PhantomAssertions.assertEquals((byte) 1, _repository.findComponent(profile.profileId(), "activation.a").orElseThrow().payload()[0], "First mutation escaped a rolled-back transaction.");
		});

		registry.add("03-stale-and-late-events-are-atomic-monotonic-and-idempotent", context ->
		{
			final PhantomProfile profile = profile();
			final AtomicLong clock = new AtomicLong(200000);
			final PhantomSocialCatalog catalog = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
			final PhantomSocialService service = new PhantomSocialService(catalog, new PhantomSocialStore(_repository, catalog), 18001801L, 16, clock::get);
			PhantomAssertions.assertTrue(service.start(), "Activation social service did not start.");
			try
			{
				PhantomAssertions.assertTrue(service.ensurePersonality(profile.profileId()).available(), "Activation social components were not initialized.");
				final var before = service.snapshot(profile.profileId(), SubjectRef.character(90001), 24, clock.get()).value();
				final SocialEvent stale = event(profile.profileId(), "stale", "party.member.joined", 1);
				PhantomAssertions.assertEquals(Status.STALE, service.record(stale).status(), "Expired social event was not durably classified STALE.");
				PhantomAssertions.assertEquals(Status.IDEMPOTENT, service.record(stale).status(), "Exact stale receipt was not idempotent before pruning by another event.");
				final var afterStale = service.snapshot(profile.profileId(), SubjectRef.character(90001), 24, clock.get()).value();
				PhantomAssertions.assertEquals(before.relationship(), afterStale.relationship(), "STALE event changed relationship or agreement state.");
				clock.set(10000);
				final SocialEvent late = event(profile.profileId(), "late", "party.member.joined", 100000);
				PhantomAssertions.assertEquals(Status.RECORDED, service.record(late).status(), "Live late social event was not applied.");
				final var stored = new PhantomSocialStore(_repository, catalog).load(profile.profileId()).orElseThrow();
				PhantomAssertions.assertEquals(stored.stateRowVersion(), stored.receiptRowVersion(), "social.state and social.receipts did not advance in the same transaction.");
				PhantomAssertions.assertTrue(stored.state().logicalMinute() >= 200000, "Social logical minute moved backwards after an out-of-order event.");
			}
			finally
			{
				service.beginStop();
				PhantomAssertions.assertTrue(service.finishStop(), "Activation social service did not drain.");
			}
		});
	}

	private void semantic(PhantomTestRegistry registry)
	{
		registry.add("01-domain-identity-and-slot-contracts-are-strict", context ->
		{
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PlayerReference(new PhantomDomainRef("profile", "name"), "Name"), "Non-numeric profile identity was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PlayerReference(new PhantomDomainRef("character.object", Long.toString((long) Integer.MAX_VALUE + 1)), "Name"), "Out-of-range character identity was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("npc", "30080"), 0, 1), "Cross-namespace slot value was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.IntentCandidate("party.invite", 8000, List.of(SlotValue.quantity(1, 0, 1), SlotValue.quantity(2, 2, 3)), "accept.matched", List.of(), 1, 0), "Duplicate slot types were accepted.");
		});

		registry.add("02-production-authority-resolves-all-seven-grounded-families", context ->
		{
			PhantomAssertions.assertEquals("36C1E6E0F9793DF07006FBC2A4DFD7B4AE5888E8A4661F978F03B78875E2F23A", _pack.packHash(), "Pinned semantic pack hash changed.");
			PhantomAssertions.assertEquals("CEBCCE4B1E9CE864B9695969FDACE06BFBE732B5FE69FF5BDB8DE89B9500EDBD", _pack.corpusHash(), "Pinned semantic corpus hash changed.");
			for (var sample : Map.of(SlotType.ITEM, "где взять адену", SlotType.NPC, "где кларисса", SlotType.CONTENT, "что нужно для рифт", SlotType.TOPOLOGY_NODE, "идем в город гиран", SlotType.LOCATION, "идем к центр гирана", SlotType.CAPABILITY, "нужно лечение", SlotType.PARTY_ROLE, "нужен хилер").entrySet())
			{
				final var result = _semantic.understand(sample.getValue(), InputContext.empty());
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, result.status(), "Production authority alias did not resolve: " + sample.getKey());
				PhantomAssertions.assertTrue(result.slots().stream().anyMatch(slot -> slot.type() == sample.getKey()), "Production authority result omitted slot: " + sample.getKey());
			}
			context.record("semantic.production.knowledgeHash", _pack.authorityHashes().knowledgeHash());
			context.record("semantic.production.topologyHash", _pack.authorityHashes().topologyHash());
			context.record("semantic.production.roleHash", _pack.authorityHashes().partyRoleHash());
		});

		registry.add("03-pattern-budget-fragment-and-start-drain-fail-closed", context ->
		{
			final Path xml = context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml");
			final Path corpus = context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv");
			final String source = Files.readString(xml, StandardCharsets.UTF_8);
			final Path slotFirst = Files.createTempFile("semantic-slot-first-", ".xml");
			final Path adjacent = Files.createTempFile("semantic-adjacent-", ".xml");
			final Path budget = Files.createTempFile("semantic-budget-", ".xml");
			try
			{
				Files.writeString(slotFirst, source.replace("пригласи {TARGET_PLAYER}", "{TARGET_PLAYER} пригласи"), StandardCharsets.UTF_8);
				Files.writeString(adjacent, source.replace("{QUANTITY} штук {ITEM}", "{QUANTITY} {ITEM}"), StandardCharsets.UTF_8);
				final StringBuilder budgetPatterns = new StringBuilder();
				for (int index = 0; index < 88; index++)
				{
					budgetPatterns.append("\n\t\t\t<pattern id=\"budget.").append(index).append("\" text=\"где взять {ITEM}\"/>");
				}
				Files.writeString(budget, source.replace("<pattern id=\"acquire.missing\" text=\"где взять\"/>", "<pattern id=\"acquire.missing\" text=\"где взять\"/>" + budgetPatterns), StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(slotFirst, corpus, productionAuthority()), "Slot-first semantic pattern was accepted.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(adjacent, corpus, productionAuthority()), "Adjacent semantic slots were accepted.");
				final PhantomSemanticUnderstandingService limited = PhantomSemanticUnderstandingService.loaded(PhantomSemanticPack.load(budget, corpus, productionAuthority()));
				limited.start();
				PhantomAssertions.assertEquals("clarify.complexity", limited.understand("где взять 5 штук адены", InputContext.empty()).reasonKey(), "Incomplete candidate search selected a partial winner.");
				limited.beginStop();
				limited.finishStop();
				final var fragment = _semantic.resolveFragment("адена", InputContext.empty(), Set.of(SlotType.ITEM));
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, fragment.status(), "Typed fragment resolver did not resolve an exact ITEM alias.");

				final CountDownLatch entered = new CountDownLatch(1);
				final CountDownLatch release = new CountDownLatch(1);
				final PhantomSemanticUnderstandingService racing = new PhantomSemanticUnderstandingService(() ->
				{
					entered.countDown();
					try
					{
						release.await(3, TimeUnit.SECONDS);
					}
					catch (InterruptedException exception)
					{
						Thread.currentThread().interrupt();
					}
					return _pack;
				});
				final Thread loader = new Thread(racing::start, "semantic-activation-loader");
				loader.start();
				PhantomAssertions.assertTrue(entered.await(2, TimeUnit.SECONDS), "Semantic activation loader did not enter its start claim.");
				racing.beginStop();
				release.countDown();
				loader.join(3000);
				PhantomAssertions.assertTrue(racing.finishStop(), "Semantic finishStop did not drain the racing start claim.");
				PhantomAssertions.assertEquals(PhantomSemanticUnderstandingService.State.STOPPED, racing.snapshot().state(), "Racing semantic loader published after stop.");
			}
			finally
			{
				Files.deleteIfExists(slotFirst);
				Files.deleteIfExists(adjacent);
				Files.deleteIfExists(budget);
			}
		});
	}

	private PhantomSemanticGrounding.Authority productionAuthority()
	{
		final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
		final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final PhantomTopologyQuery topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
		final PhantomPartyRoleCatalog roles = PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"));
		return PhantomSemanticGrounding.production(_knowledge.query(), topology, roles);
	}

	private PhantomProfile profile()
	{
		final PhantomProfile profile = _repository.create(null);
		_profiles.add(profile.profileId());
		return profile;
	}

	private static SocialEvent event(long ownerProfileId, String identity, String eventKey, long happenedMinute)
	{
		return new SocialEvent(ownerProfileId, PhantomSocialModel.sha256("activation.event|" + identity), eventKey, SubjectRef.character(90001), happenedMinute, 1000, PhantomSocialModel.sha256("activation.evidence|" + identity));
	}
}
