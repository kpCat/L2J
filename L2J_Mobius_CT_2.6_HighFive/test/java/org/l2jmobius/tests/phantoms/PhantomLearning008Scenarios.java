/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** TEST-only canonical discovery; live native readiness is a separate admission. */
public final class PhantomLearning008Scenarios
{
	public record Capability(int classId, int skillId, int skillLevel, String source, double mpCost, int castMillis, int range) { }
	static record Slot(int ordinal, String split, String block, boolean experimental, int direction, int variant, PhantomTrainingScenarios.Scene scene)
	{
		String row() { return ordinal + "\t" + split + "\t" + block + "\t" + experimental + "\t" + direction + "\t" + variant + "\t" + scene.row(); }
		static Slot parse(String row)
		{
			var p = row.split("\t", 7); if (p.length != 7) { throw new IllegalArgumentException("Invalid008 slot"); }
			return new Slot(Integer.parseInt(p[0]), p[1], p[2], Boolean.parseBoolean(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), PhantomTrainingScenarios.Scene.parse(p[6]));
		}
	}
	static byte[] capabilityXml(Path game) throws Exception
	{
		var text = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<progression schemaVersion=\"1\" datasetId=\"learning008-test\" datasetVersion=\"1\">\n");
		for (int classId : List.of(103, 110)) { for (var c : canonical(game, classId, 77))
		{
			text.append("  <capabilityRule classId=\"").append(classId).append("\" capabilityKey=\"combat.ranged_magic_damage\" variantKey=\"learning008-s").append(c.skillId())
				.append("\" rank=\"850\" skillId=\"").append(c.skillId()).append("\" skillLevel=\"").append(c.skillLevel()).append("\" source=\"").append(c.source()).append("\" />\n");
		}}
		return text.append("</progression>\n").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
	static Path installOwned(Path installation) throws Exception
	{
		Path root = PhantomTrainingActors.physicalRoot(installation); var config = org.l2jmobius.gameserver.phantoms.learning.TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		if (!config.text("exploration008Enabled", "false").equals("true") || !config.text("exploration008Campaign", "").equals(org.l2jmobius.gameserver.phantoms.learning.ActionAdvantage008.CAMPAIGN))
		{ throw new IllegalArgumentException("Explicit008 TEST capability apply required"); }
		var path = root.resolve("game/data/phantoms/progression/learning008-capabilities.xml"); var bytes = capabilityXml(root.resolve("game"));
		if (!path.getParent().equals(path.getParent().toRealPath())) { throw new IllegalArgumentException("Physical private TEST data required"); }
		if (Files.exists(path)) { if (!path.equals(path.toRealPath()) || !java.util.Arrays.equals(bytes, Files.readAllBytes(path))) { throw new IllegalArgumentException("008 immutable capability artifact changed"); } }
		else { Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW); }
		return path;
	}
	static List<Slot> catalog(org.l2jmobius.gameserver.phantoms.learning.TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority)
	{
		var candidates = PhantomTrainingScenarios.discover(config, authority, config.seed(), 256);
		var used = new HashSet<String>(); var train = new ArrayList<PhantomTrainingScenarios.Scene>();
		for (int classId : List.of(94, 103))
		{
			train.add(select(candidates, used, classId, 77, "A", 0, 21798, "TRAIN"));
			train.add(select(candidates, used, classId, 78, "S", 2, 21799, "TRAIN"));
		}
		var tuning = List.of(select(candidates, used, 94, 78, "A", 2, 21799, "TUNING"), select(candidates, used, 103, 77, "S", 0, 21798, "TUNING"));
		var audit = List.of(select(candidates, used, 110, 77, "A", 2, 21799, "AUDIT"), select(candidates, used, 110, 78, "S", 0, 21798, "AUDIT"));
		var result = new ArrayList<Slot>();
		for (int slot = 0; slot < 32; slot++)
		{
			int block = slot / 2, cell = block % 4, visit = block / 4; var scene = train.get(cell);
			result.add(new Slot(slot, "TRAIN", "train-" + block, (slot % 2 == 1) == (visit % 2 == 0), scene.classId(), cell % 2, scene));
		}
		for (int slot = 32; slot < 40; slot++) { var scene = tuning.get((slot - 32) % 2); result.add(new Slot(slot, "TUNING", "tuning-" + slot, false, scene.classId(), slot % 2, scene)); }
		for (int slot = 40; slot < 48; slot++) { var scene = audit.get((slot - 40) % 2); result.add(new Slot(slot, "AUDIT", "audit-" + slot, false, 110, slot % 2, scene)); }
		for (int direction : List.of(94, 103, 110, 105)) { for (int pair = 0; pair < 2; pair++)
		{
			int classId = direction == 105 ? 94 : direction;
			var base = select(candidates, new HashSet<>(), classId, 77 + pair, pair == 0 ? "A" : "S", pair == 0 ? 0 : 2, pair == 0 ? 21798 : 21799, "EVAL");
			var skills = base.skills(); int enchant = 4;
			var scene = new PhantomTrainingScenarios.Scene(PhantomTrainingScenarios.physicalId(base.source(), classId, base.level(), base.grade(), base.x(), base.y(), base.z(), skills, 3, enchant), base.source(), base.npc(), classId, base.level(), base.grade(), enchant, base.x(), base.y(), base.z(), skills, "EVAL");
			result.add(new Slot(result.size(), "EVAL", "class" + direction + "-pair" + pair, false, direction, pair, scene));
		}}
		return List.copyOf(result);
	}
	private static PhantomTrainingScenarios.Scene select(List<PhantomTrainingScenarios.Scene> candidates, java.util.Set<String> used, int classId, int level, String grade, int enchant, int preferredNpc, String split)
	{
		var available = candidates.stream().filter(s -> s.classId() == classId && s.level() == level && s.grade().equals(grade) && s.weaponEnchant() == enchant && !used.contains(s.id()))
			.sorted(Comparator.comparingInt((PhantomTrainingScenarios.Scene s) -> s.npc() == preferredNpc ? 0 : 1).thenComparing(PhantomTrainingScenarios.Scene::id)).toList();
		if (available.isEmpty()) { throw new IllegalStateException("008_NO_CANONICAL_REACHABLE_SCENE class=" + classId + " level=" + level + " grade=" + grade + " enchant=" + enchant); }
		var scene = available.getFirst(); used.add(scene.id()); return scene.withSplit(split);
	}
	public static List<Integer> damageSkillIds(int classId)
	{
		return switch (classId)
		{
			case 94 -> List.of(1230, 1339);
			case 103 -> List.of(1235, 1340, 1342);
			case 110 -> List.of(1239, 1341, 1343);
			default -> List.<Integer>of();
		};
	}
	public static List<Capability> canonical(Path game, int classId, int level) throws Exception
	{
		var preferred = damageSkillIds(classId);
		if (preferred.isEmpty()) { return List.of(); }
		if (level < 1 || level > 85) { throw new IllegalArgumentException("Invalid canonical actor level"); }
		var parents = new HashMap<Integer, Integer>();
		var trees = new HashMap<Integer, List<Element>>();
		var sources = new HashMap<Element, String>();
		try (var files = Files.walk(game.resolve("data/stats/players/skillTrees")))
		{
			for (var file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".xml")).sorted().toList())
			{
				var document = xml(file); var nodes = document.getElementsByTagName("skillTree");
				for (int i = 0; i < nodes.getLength(); i++)
				{
					var tree = (Element) nodes.item(i);
					if (!tree.getAttribute("type").equals("classSkillTree")) { continue; }
					int id = Integer.parseInt(tree.getAttribute("classId"));
					if (tree.hasAttribute("parentClassId")) { parents.put(id, Integer.parseInt(tree.getAttribute("parentClassId"))); }
					var skills = tree.getElementsByTagName("skill");
					for (int n = 0; n < skills.getLength(); n++)
					{
						var skill = (Element) skills.item(n); trees.computeIfAbsent(id, _ -> new ArrayList<>()).add(skill);
						sources.put(skill, game.relativize(file).toString().replace('\\', '/'));
					}
				}
			}
		}
		var learned = new HashMap<Integer, Element>(); var visited = new HashSet<Integer>();
		for (int current = classId; current >= 0; current = parents.getOrDefault(current, -1))
		{
			if (!visited.add(current)) { throw new IllegalArgumentException("Cyclic canonical class tree"); }
			for (var skill : trees.getOrDefault(current, List.of()))
			{
				int id = Integer.parseInt(skill.getAttribute("skillId"));
				if (!preferred.contains(id) || Integer.parseInt(skill.getAttribute("getLevel")) > level
					|| !Boolean.parseBoolean(skill.getAttribute("learnedByNpc")) && !Boolean.parseBoolean(skill.getAttribute("autoGet"))) { continue; }
				var old = learned.get(id);
				if (old == null || Integer.parseInt(old.getAttribute("skillLevel")) < Integer.parseInt(skill.getAttribute("skillLevel"))) { learned.put(id, skill); }
			}
		}
		var result = new ArrayList<Capability>(); var definitions = new HashMap<Integer, Document>();
		for (int id : preferred)
		{
			var learn = learned.get(id); if (learn == null) { continue; }
			int rank = Integer.parseInt(learn.getAttribute("skillLevel")), band = id / 100 * 100;
			var document = definitions.get(band);
			if (document == null) { document = xml(game.resolve(String.format("data/stats/skills/%05d-%05d.xml", band, band + 99))); definitions.put(band, document); }
			Element definition = null; var nodes = document.getElementsByTagName("skill");
			for (int n = 0; n < nodes.getLength(); n++) { var skill = (Element) nodes.item(n); if (skill.getAttribute("id").equals(Integer.toString(id))) { definition = skill; break; } }
			if (definition == null || !text(definition, "targetType").equals("ONE")) { throw new IllegalArgumentException("Canonical single-target skill missing: " + id); }
			boolean damage = false; var effects = definition.getElementsByTagName("effect");
			for (int n = 0; n < effects.getLength(); n++) { var effect = (Element) effects.item(n); damage |= List.of("MagicalDamage", "HpDrain").contains(effect.getAttribute("name")); }
			if (!damage) { throw new IllegalArgumentException("Canonical DAMAGE effect missing: " + id); }
			double mp = scalar(definition, "mpConsume", rank) + scalar(definition, "mpInitialConsume", rank);
			result.add(new Capability(classId, id, rank, sources.get(learn), mp, (int) scalar(definition, "hitTime", rank), (int) scalar(definition, "castRange", rank)));
		}
		return result.stream().sorted(Comparator.comparingInt(Capability::skillId)).toList();
	}
	private static Document xml(Path path) throws Exception
	{
		if (Files.size(path) > 32L * 1024 * 1024) { throw new IllegalArgumentException("Canonical XML too large"); }
		var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		try (var input = Files.newInputStream(path)) { return factory.newDocumentBuilder().parse(input); }
	}
	public static boolean learned(int classId, List<Integer> selected, java.util.function.IntPredicate known)
	{
		var allowed = damageSkillIds(classId);
		return selected.size() >= 2 && selected.size() <= 4 && selected.stream().distinct().count() == selected.size()
			&& allowed.containsAll(selected) && selected.stream().allMatch(known::test);
	}
	private static String text(Element element, String tag)
	{
		var nodes = element.getElementsByTagName(tag); return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
	}
	private static double scalar(Element skill, String field, int rank)
	{
		String value = text(skill, field); if (value.isEmpty()) { return 0; }
		if (value.startsWith("#"))
		{
			var tables = skill.getElementsByTagName("table"); boolean found = false;
			for (int n = 0; n < tables.getLength(); n++)
			{
				var table = (Element) tables.item(n); if (!table.getAttribute("name").equals(value)) { continue; }
				var values = table.getTextContent().trim().split("\\s+");
				if (rank < 1 || rank > values.length) { throw new IllegalArgumentException("Canonical rank outside skill table"); }
				value = values[rank - 1]; found = true; break;
			}
			if (!found) { throw new IllegalArgumentException("Canonical skill table missing"); }
		}
		double result = Double.parseDouble(value);
		if (!Double.isFinite(result) || result < 0) { throw new IllegalArgumentException("Canonical skill fact invalid"); }
		return result;
	}
	private PhantomLearning008Scenarios() { }
}
