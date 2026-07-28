/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.CapabilitySeed;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.SourceData;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomProgressionProductionCompositionSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final Map<String, List<String>> _expectedSources = new LinkedHashMap<>();
	private PhantomGameKnowledgeService _knowledge;
	private PhantomProgressionCatalog _catalog;
	private SourceData _sourceData;
	private int _knowledgeVariants;
	private int _curatedVariants;
	private List<String> _repeatHashes;

	@Override
	public String id()
	{
		return "progression-production-composition";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		try
		{
			final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
			final PhantomTopologySnapshot topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final PhantomTopologyQuery topologyQuery = new PhantomTopologyQuery(topology, topologyBackend, new PhantomTopologyMetrics());
			final PhantomGameKnowledgePolicy knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final L2jGameKnowledgeBackend knowledgeBackend = new L2jGameKnowledgeBackend();
			final PhantomGameKnowledgeBuilder knowledgeBuilder = new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, knowledgePolicy), topologyQuery, knowledgePolicy);
			_knowledge = new PhantomGameKnowledgeService(knowledgeBuilder);
			PhantomAssertions.assertTrue(_knowledge.start(), "Ordinary production Game Knowledge did not start.");
			final PhantomGameKnowledgeQuery query = _knowledge.query();
			_sourceData = new PhantomProgressionSourceParser(Path.of("."), PhantomProgressionPolicy.productionDefaults()).parse();
			enumerateExpected(query);
			final L2jProgressionBackend backend = new L2jProgressionBackend(null, Path.of("."), () -> query);
			final PhantomProgressionCatalogBuilder progressionBuilder = new PhantomProgressionCatalogBuilder();
			_catalog = progressionBuilder.build(backend.load(PhantomProgressionPolicy.productionDefaults()), PhantomProgressionPolicy.productionDefaults());
			final ArrayList<String> hashes = new ArrayList<>();
			for (int repeat = 0; repeat < 3; repeat++)
			{
				hashes.add(progressionBuilder.build(backend.load(PhantomProgressionPolicy.productionDefaults()), PhantomProgressionPolicy.productionDefaults()).combinedHash());
			}
			_repeatHashes = List.copyOf(hashes);
			context.record("progressionProductionComposition.knowledgeVariants", _knowledgeVariants);
			context.record("progressionProductionComposition.curatedVariants", _curatedVariants);
			context.record("progressionProductionComposition.totalVariants", _catalog.counts().capabilityRules());
			context.record("progressionProductionComposition.classGraphHash", _catalog.hashes().classGraphHash());
			context.record("progressionProductionComposition.skillLearningHash", _catalog.hashes().skillLearningHash());
			context.record("progressionProductionComposition.skillMechanicsHash", _catalog.hashes().skillMechanicsHash());
			context.record("progressionProductionComposition.equipmentHash", _catalog.hashes().equipmentHash());
			context.record("progressionProductionComposition.summonPetHash", _catalog.hashes().summonPetHash());
			context.record("progressionProductionComposition.capabilityRulesHash", _catalog.hashes().capabilityRulesHash());
			context.record("progressionProductionComposition.combinedHash", _catalog.combinedHash());
		}
		catch (Throwable throwable)
		{
			close();
			throw throwable;
		}
	}

	private void enumerateExpected(PhantomGameKnowledgeQuery query)
	{
		for (PlayerClass playerClass : PlayerClass.values())
		{
			String cursor = null;
			do
			{
				final KnowledgePage<ClassCapabilityFact> page = query.classCapabilities(playerClass.getId(), new PageRequest(256, cursor));
				for (ClassCapabilityFact fact : page.values())
				{
					for (var evidence : fact.evidenceSkills())
					{
						final SkillRef skill = new SkillRef(evidence.skillId(), evidence.skillLevel());
						putExpected(identity(fact.classId(), fact.capabilityKey(), L2jProgressionBackend.knowledgeVariantKey(skill)), fact.sourceRefs());
						_knowledgeVariants++;
					}
				}
				cursor = page.nextCursor();
			}
			while (cursor != null);
		}
		for (CapabilitySeed seed : _sourceData.capabilitySeeds())
		{
			putExpected(identity(seed.classId(), seed.capabilityKey(), seed.variantKey()), List.of(seed.sourcePath()));
			_curatedVariants++;
		}
	}

	private void putExpected(String identity, List<String> sources)
	{
		PhantomAssertions.assertTrue(_expectedSources.putIfAbsent(identity, List.copyOf(sources)) == null, "Independent source enumeration contains duplicate capability variant " + identity + '.');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-ordinary-game-knowledge-is-nonempty", _ -> PhantomAssertions.assertTrue(_knowledgeVariants > 0, "Production composition used inert Game Knowledge."));
		registry.add("02-curated-seeds-are-independently-parsed", _ -> PhantomAssertions.assertEquals(_sourceData.capabilitySeeds().size(), _curatedVariants, "Curated seed enumeration changed."));
		registry.add("03-source-count-is-exact", _ -> PhantomAssertions.assertEquals(_expectedSources.size(), _catalog.counts().capabilityRules(), "Production capability composition lost or invented variants."));
		registry.add("04-source-identity-parity", _ -> assertSourceParity());
		registry.add("05-source-provenance-parity", _ -> assertProvenanceParity());
		registry.add("06-three-rebuilds-are-identical", _ -> PhantomAssertions.assertEquals(List.of(_catalog.combinedHash(), _catalog.combinedHash(), _catalog.combinedHash()), _repeatHashes, "Production composition hash is nondeterministic."));
		registry.add("07-same-group-variants-survive", _ -> PhantomAssertions.assertTrue(_catalog.capabilities(93).stream().filter(rule -> rule.capabilityKey().equals("combat.stealth")).count() >= 2, "Same-group capability variants collapsed."));
		registry.add("08-action-skill-is-exact-evidence", _ -> _catalog.capabilities(93).forEach(rule -> PhantomAssertions.assertTrue(rule.evidenceSkills().contains(rule.actionSkill()), "Capability action skill lost exact evidence.")));
		registry.add("09-no-loader-workers-or-operations", _ -> PhantomAssertions.assertTrue(_knowledge.snapshot().state() == PhantomGameKnowledgeService.State.RUNNING, "Production composition lifecycle changed."));
	}

	private void assertSourceParity()
	{
		final Map<String, CapabilityRule> actual = actualRules();
		PhantomAssertions.assertEquals(_expectedSources.keySet(), actual.keySet(), "Independent and composed capability identity sets differ.");
	}

	private void assertProvenanceParity()
	{
		final Map<String, CapabilityRule> actual = actualRules();
		_expectedSources.forEach((identity, sources) -> PhantomAssertions.assertEquals(sources, actual.get(identity).sourcePaths(), "Capability provenance changed for " + identity + '.'));
	}

	private Map<String, CapabilityRule> actualRules()
	{
		final LinkedHashMap<String, CapabilityRule> result = new LinkedHashMap<>();
		String cursor = null;
		do
		{
			final var page = _catalog.capabilityRules(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest(cursor, 256));
			for (CapabilityRule rule : page.values())
			{
				final String identity = identity(rule.classIds().getFirst(), rule.capabilityKey(), rule.variantKey());
				PhantomAssertions.assertTrue(result.putIfAbsent(identity, rule) == null, "Composed catalog contains duplicate capability variant " + identity + '.');
			}
			cursor = page.nextCursor();
		}
		while (cursor != null);
		return Map.copyOf(result);
	}

	private static String identity(int classId, String capabilityKey, String variantKey)
	{
		return classId + ":" + capabilityKey + ":" + variantKey;
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		close();
	}

	private void close() throws Exception
	{
		Throwable failure = null;
		if (_knowledge != null)
		{
			try
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Production Game Knowledge did not stop.");
			}
			catch (Throwable throwable)
			{
				failure = throwable;
			}
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
