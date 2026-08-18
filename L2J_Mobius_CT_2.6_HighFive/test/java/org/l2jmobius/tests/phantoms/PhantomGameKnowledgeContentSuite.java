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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser.CuratedData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeValidationException;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomGameKnowledgeContentSuite implements PhantomTestSuite
{
	private static final PhantomGameKnowledgePolicy POLICY = PhantomGameKnowledgePolicy.productionDefaults();
	private final List<Path> _temporaryRoots = new ArrayList<>();
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private ContentBackend _backend;
	private PhantomTopologyQuery _topology;
	private CuratedData _curated;
	private PhantomGameKnowledgeSnapshot _snapshot;
	private String _sourceXml;

	@Override
	public String id()
	{
		return "knowledge-content";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		MapRegionData.getInstance();
		SpawnData.getInstance();
		DoorData.getInstance();
		final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
		final PhantomTopologySnapshot topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		_topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
		_backend = new ContentBackend();
		_sourceXml = Files.readString(Path.of("data/phantoms/knowledge/high-five-core-v1.xml"), StandardCharsets.UTF_8);
		_curated = new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), _backend, POLICY).parse();
		_snapshot = builder(Path.of("data/phantoms/knowledge")).build();
		context.record("knowledge.content.datasetId", _snapshot.datasetId());
		context.record("knowledge.content.datasetVersion", _snapshot.datasetVersion());
		context.record("knowledge.content.capabilities", _snapshot.counts().classCapabilities());
		context.record("knowledge.content.entries", _snapshot.counts().contentRequirements());
		context.record("knowledge.content.ids", String.join(",", _snapshot.contentById().keySet().stream().sorted().toList()));
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		for (Path root : _temporaryRoots)
		{
			if (Files.exists(root))
			{
				try (var stream = Files.walk(root))
				{
					for (Path path : stream.sorted(Collections.reverseOrder()).toList())
					{
						Files.deleteIfExists(path);
					}
				}
			}
		}
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-versioned-dataset-metadata", _ -> testMetadata());
		registry.add("02-unknown-attribute-rejected", this::testUnknownAttribute);
		registry.add("03-unsupported-version-rejected", this::testVersion);
		registry.add("04-duplicate-capability-rejected", this::testDuplicate);
		registry.add("05-missing-source-rejected", this::testMissingSource);
		registry.add("06-invalid-evidence-skill-rejected", this::testInvalidEvidence);
		registry.add("07-missing-topology-reference-rejected", this::testMissingTopology);
		registry.add("08-unsatisfiable-requirement-rejected", this::testUnsatisfiable);
		registry.add("09-class-and-skill-evidence-parity", _ -> testEvidence());
		registry.add("10-terminal-class-coverage", _ -> testTerminalCoverage());
		registry.add("11-required-capability-coverage", _ -> testRequiredCapabilities());
		registry.add("12-no-class-name-inference", this::testNoNameInference);
		registry.add("13-content-source-evidence", _ -> testSources());
		registry.add("14-content-npc-references", _ -> testNpcReferences());
		registry.add("15-requirement-satisfiability", _ -> testSatisfiability());
		registry.add("16-rift-raid-grandboss-corpus", _ -> testRepresentativeContent());
		registry.add("17-curated-recommendation-authority", _ -> testAuthority());
		registry.add("18-no-party-solver-or-action-decision", this::testNoSolver);
	}

	private void testMetadata()
	{
		PhantomAssertions.assertEquals(1, _snapshot.schemaVersion(), "Knowledge schema version changed.");
		PhantomAssertions.assertEquals("high-five-core", _snapshot.datasetId(), "Curated dataset ID changed.");
		PhantomAssertions.assertEquals(1, _snapshot.datasetVersion(), "Curated dataset version changed.");
		PhantomAssertions.assertEquals(1L, _snapshot.generation(), "Knowledge one-build generation changed.");
	}

	private void testUnknownAttribute(PhantomTestContext context) throws Exception
	{
		assertParserRejects(context, "unknown", xml -> xml.replace("<knowledge ", "<knowledge unknown=\"x\" "));
	}

	private void testVersion(PhantomTestContext context) throws Exception
	{
		assertParserRejects(context, "version", xml -> xml.replace("schemaVersion=\"1\"", "schemaVersion=\"2\""));
	}

	private void testDuplicate(PhantomTestContext context) throws Exception
	{
		final int start = _sourceXml.indexOf("\t<classCapability");
		final int end = _sourceXml.indexOf("</classCapability>", start) + "</classCapability>".length();
		final String block = _sourceXml.substring(start, end);
		assertParserRejects(context, "duplicate", xml -> xml.substring(0, end) + "\n" + block + xml.substring(end));
	}

	private void testMissingSource(PhantomTestContext context) throws Exception
	{
		assertParserRejects(context, "missing-source", xml -> xml.replaceFirst("data/stats/players/skillTrees/3rdClass/Duelist.xml", "data/missing-evidence.xml"));
	}

	private void testInvalidEvidence(PhantomTestContext context) throws Exception
	{
		assertBuilderRejects(context, "invalid-evidence", xml -> xml.replaceFirst("<skill id=\"345\" level=\"1\"", "<skill id=\"999999\" level=\"1\""));
	}

	private void testMissingTopology(PhantomTestContext context) throws Exception
	{
		assertBuilderRejects(context, "missing-topology", xml -> xml.replaceFirst("contentKind=\"RIFT\"", "contentKind=\"RIFT\" topologyNodeId=\"missing.node\""));
	}

	private void testUnsatisfiable(PhantomTestContext context) throws Exception
	{
		assertBuilderRejects(context, "unsatisfiable", xml -> xml.replaceFirst("capabilityKey=\"combat.tank\" minimumCount=\"1\" minimumRank=\"800\"", "capabilityKey=\"combat.tank\" minimumCount=\"1\" minimumRank=\"1000\""));
	}

	private void testEvidence()
	{
		final BackendData data = _backend.load(POLICY);
		for (ClassCapabilityFact capability : _curated.classCapabilities())
		{
			final Set<SkillEvidence> skills = Set.copyOf(data.completeClassSkills().get(capability.classId()));
			PhantomAssertions.assertTrue(skills.containsAll(capability.evidenceSkills()), "Curated capability lacks complete-tree skill evidence.");
		}
	}

	private void testTerminalCoverage()
	{
		final Set<Integer> parents = new HashSet<>();
		_snapshot.classFacts().stream().map(ClassIntrinsicFact::parentClassId).filter(java.util.Objects::nonNull).forEach(parents::add);
		final Set<Integer> covered = new HashSet<>();
		_snapshot.classCapabilities().stream().filter(capability -> capability.capabilityKey().startsWith("combat.") || capability.capabilityKey().startsWith("profession.")).map(ClassCapabilityFact::classId).forEach(covered::add);
		for (ClassIntrinsicFact fact : _snapshot.classFacts())
		{
			if (!parents.contains(fact.classId()))
			{
				PhantomAssertions.assertTrue(covered.contains(fact.classId()), "Terminal PlayerClass lacks curated capability coverage.");
			}
		}
	}

	private void testRequiredCapabilities()
	{
		PhantomAssertions.assertTrue(_snapshot.classesByCapability().keySet().containsAll(PhantomGameKnowledgeBuilder.REQUIRED_CAPABILITIES), "Required capability-key coverage is incomplete.");
	}

	private void testNoNameInference(PhantomTestContext context) throws Exception
	{
		final String parser = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomCuratedKnowledgeParser.java"), StandardCharsets.UTF_8);
		final String builder = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertFalse(parser.contains("skillName") || parser.contains("className") || builder.contains("skillName") || builder.contains("className"), "Capability implementation infers semantics from localized/class names.");
	}

	private void testSources()
	{
		_snapshot.classCapabilities().forEach(fact -> fact.sourceRefs().forEach(source -> PhantomAssertions.assertTrue(_backend.sourceExists(source), "Capability evidence source is missing.")));
		_snapshot.contentRequirements().forEach(fact -> fact.sourceRefs().forEach(source -> PhantomAssertions.assertTrue(_backend.sourceExists(source), "Content evidence source is missing.")));
	}

	private void testNpcReferences()
	{
		for (ContentRequirementFact content : _snapshot.contentRequirements())
		{
			if (content.npcId() != null)
			{
				PhantomAssertions.assertTrue(_snapshot.npcById().containsKey(content.npcId()), "Content NPC reference is missing.");
			}
		}
	}

	private void testSatisfiability()
	{
		for (ContentRequirementFact content : _snapshot.contentRequirements())
		{
			content.requirements().forEach(requirement ->
			{
				final long count = _snapshot.classesByCapability().getOrDefault(requirement.capabilityKey(), List.of()).stream().filter(capability -> capability.rank() >= requirement.minimumRank()).map(ClassCapabilityFact::classId).distinct().count();
				PhantomAssertions.assertTrue(count >= requirement.minimumCount(), "Production content requirement is not satisfiable.");
			});
		}
	}

	private void testRepresentativeContent()
	{
		final ContentRequirementFact rift = _snapshot.contentById().get("rift.high-five-core");
		final ContentRequirementFact raid = _snapshot.contentById().get("raid.25001");
		final ContentRequirementFact epic = _snapshot.contentById().get("epic.29001");
		final ContentRequirementFact zaken = _snapshot.contentById().get("epic.zaken.83");
		PhantomAssertions.assertEquals(ContentKind.RIFT, rift.contentKind(), "Dimensional Rift recommendation is missing.");
		PhantomAssertions.assertEquals(25001, raid.npcId(), "RaidBoss recommendation identity changed.");
		PhantomAssertions.assertEquals(NpcKind.RAID_BOSS, _snapshot.npcById().get(raid.npcId()).kind(), "Raid content does not reference a real RaidBoss.");
		PhantomAssertions.assertEquals(29001, epic.npcId(), "GrandBoss recommendation identity changed.");
		PhantomAssertions.assertEquals(NpcKind.GRAND_BOSS, _snapshot.npcById().get(epic.npcId()).kind(), "Epic content does not reference a real GrandBoss.");
		PhantomAssertions.assertEquals(29181, zaken.npcId(), "Zaken83 recommendation identity changed.");
		PhantomAssertions.assertEquals(9, zaken.recommendedMinParty(), "Zaken83 minimum group changed.");
		PhantomAssertions.assertEquals(27, zaken.recommendedMaxParty(), "Zaken83 maximum group changed.");
	}

	private void testAuthority()
	{
		_snapshot.classCapabilities().forEach(fact -> PhantomAssertions.assertEquals(PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION, fact.authority(), "Capability authority changed."));
		_snapshot.contentRequirements().forEach(fact -> PhantomAssertions.assertEquals(PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION, fact.authority(), "Content authority changed."));
	}

	private void testNoSolver(PhantomTestContext context) throws Exception
	{
		final Path packageRoot = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/knowledge");
		try (var stream = Files.list(packageRoot))
		{
			for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList())
			{
				final String source = Files.readString(file, StandardCharsets.UTF_8);
				PhantomAssertions.assertFalse(source.contains("PartyOptimizer") || source.contains("CandidateRegistry") || source.contains("StepHandler") || source.contains("CombatAction") || source.contains("Commerce"), "Knowledge package contains solver/action behavior.");
			}
		}
	}

	private void assertParserRejects(PhantomTestContext context, String suffix, UnaryOperator<String> mutation) throws Exception
	{
		final Path directory = writeVariant(context, suffix, mutation.apply(_sourceXml));
		PhantomAssertions.assertThrows(PhantomGameKnowledgeValidationException.class, () -> new PhantomCuratedKnowledgeParser(directory, _backend, POLICY).parse(), "Strict curated parser accepted invalid content.");
	}

	private void assertBuilderRejects(PhantomTestContext context, String suffix, UnaryOperator<String> mutation) throws Exception
	{
		final Path directory = writeVariant(context, suffix, mutation.apply(_sourceXml));
		PhantomAssertions.assertThrows(PhantomGameKnowledgeValidationException.class, () -> builder(directory).build(), "Curated builder accepted invalid evidence/reference/satisfiability.");
	}

	private Path writeVariant(PhantomTestContext context, String suffix, String xml) throws Exception
	{
		final Path root = context.reportsDirectory().resolve("knowledge-content-" + ProcessHandle.current().pid() + "-" + suffix);
		Files.createDirectories(root);
		Files.writeString(root.resolve("variant.xml"), xml, StandardCharsets.UTF_8);
		_temporaryRoots.add(root);
		return root;
	}

	private PhantomGameKnowledgeBuilder builder(Path curatedDirectory)
	{
		return new PhantomGameKnowledgeBuilder(_backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), POLICY), new PhantomCuratedKnowledgeParser(curatedDirectory, _backend, POLICY), _topology, POLICY);
	}

	private static final class ContentBackend implements PhantomGameKnowledgeBackend
	{
		private final BackendData _data;
		private final L2jGameKnowledgeBackend _sourceBackend = new L2jGameKnowledgeBackend();

		private ContentBackend()
		{
			final ArrayList<ItemFact> items = new ArrayList<>();
			for (ItemTemplate item : ItemData.getInstance().getAllItems())
			{
				if (item != null)
				{
					final ItemCategory category = item instanceof Weapon ? ItemCategory.WEAPON : item instanceof Armor ? ItemCategory.ARMOR : ItemCategory.ETC;
					items.add(new ItemFact(item.getId(), category, item.getCrystalType().name(), item.getReferencePrice(), item.isStackable(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT));
				}
			}
			final List<NpcFact> npcs = NpcData.getInstance().getTemplates(_ -> true).stream().map(template -> new NpcFact(template.getId(), Byte.toUnsignedInt(template.getLevel()), kind(template), template.isAttackable(), template.isTargetable(), template.canBeSown(), template.getExp(), template.getSP(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)).toList();
			final List<ClassIntrinsicFact> classes = Arrays.stream(PlayerClass.values()).map(playerClass -> new ClassIntrinsicFact(playerClass.getId(), playerClass.getRace().name(), playerClass.level(), playerClass.isMage(), playerClass.isSummoner(), playerClass.getParent() == null ? null : playerClass.getParent().getId(), PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)).toList();
			final HashMap<Integer, List<SkillEvidence>> skills = new HashMap<>();
			for (ClassIntrinsicFact classFact : classes)
			{
				final ArrayList<SkillEvidence> evidence = new ArrayList<>();
				for (SkillLearn learn : SkillTreeData.getInstance().getCompleteClassSkillTree(PlayerClass.getPlayerClass(classFact.classId())).values())
				{
					if (SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()) != null)
					{
						evidence.add(new SkillEvidence(learn.getSkillId(), learn.getSkillLevel()));
					}
				}
				skills.put(classFact.classId(), evidence.stream().distinct().toList());
			}
			_data = new BackendData(items, npcs, List.of(), List.of(), List.of(), classes, skills);
		}

		private static NpcKind kind(NpcTemplate template)
		{
			return template.isType("GrandBoss") ? NpcKind.GRAND_BOSS : template.isType("RaidBoss") ? NpcKind.RAID_BOSS : template.isType("Monster") ? NpcKind.MONSTER : NpcKind.OTHER_ATTACKABLE;
		}

		@Override
		public BackendData load(PhantomGameKnowledgePolicy policy)
		{
			return _data;
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return _sourceBackend.sourceExists(relativeDatapackPath);
		}
	}
}
