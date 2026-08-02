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
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Strict, content-addressed policy for the bounded acquisition kernel. */
public final class PhantomAcquisitionCatalog
{
	private static final int MAX_BYTES = 64 * 1024;
	private static final List<String> REQUIRED_REASONS = List.of("goal.invalid", "manor.harvester_missing", "manor.seed_missing", "quest.callback_timeout", "quest.cond_ineligible", "quest.item_cap", "quest.not_started", "quest.rule_unsupported", "quest.script_stale", "quest.target_unavailable", "source.ambiguous", "source.authority_stale", "source.exhausted", "source.ineligible", "source.inventory_capacity", "source.repeated_failure", "source.resource_reserve", "source.target_unavailable");
	private final String _hash;
	private final Limits _limits;
	private final Map<Method, MethodPolicy> _methods;
	private final SourceScoring _sourceScoring;
	private final SwitchPolicy _switchPolicy;
	private final RecipePlanning _recipePlanning;
	private final List<String> _reasonKeys;

	private PhantomAcquisitionCatalog(String hash, Limits limits, Map<Method, MethodPolicy> methods, SourceScoring sourceScoring, SwitchPolicy switchPolicy, RecipePlanning recipePlanning, List<String> reasonKeys)
	{
		_hash = hash;
		_limits = limits;
		_methods = Map.copyOf(methods);
		_sourceScoring = sourceScoring;
		_switchPolicy = switchPolicy;
		_recipePlanning = recipePlanning;
		_reasonKeys = List.copyOf(reasonKeys);
	}

	public static PhantomAcquisitionCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Acquisition policy size is invalid.");
			}
			strictUtf8(bytes);
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			require(root, "acquisitionPolicy", Set.of("id", "version"));
			if (!"high-five-acquisition-v1".equals(root.getAttribute("id")) || !"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Acquisition policy identity is invalid.");
			}
			final List<Element> sections = children(root);
			if (!sections.stream().map(Element::getTagName).toList().equals(List.of("limits", "methods", "sourceScoring", "switchPolicy", "recipePlanning", "reasonKeys")))
			{
				throw new IllegalArgumentException("Acquisition policy sections are not exact.");
			}
			final Limits limits = parseLimits(sections.get(0));
			final EnumMap<Method, MethodPolicy> methods = parseMethods(sections.get(1));
			final SourceScoring scoring = parseSourceScoring(sections.get(2));
			final SwitchPolicy switching = parseSwitchPolicy(sections.get(3));
			final RecipePlanning recipes = parseRecipePlanning(sections.get(4));
			final List<String> reasons = parseReasons(sections.get(5));
			if (!reasons.equals(REQUIRED_REASONS))
			{
				throw new IllegalArgumentException("Acquisition reason keys are not exact.");
			}
			return new PhantomAcquisitionCatalog(hash(bytes), limits, methods, scoring, switching, recipes, reasons);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load acquisition policy.", exception);
		}
	}

	public String hash()
	{
		return _hash;
	}

	public Limits limits()
	{
		return _limits;
	}

	public MethodPolicy method(Method method)
	{
		return _methods.get(method);
	}

	public SourceScoring sourceScoring()
	{
		return _sourceScoring;
	}

	public SwitchPolicy switchPolicy()
	{
		return _switchPolicy;
	}

	public RecipePlanning recipePlanning()
	{
		return _recipePlanning;
	}

	public List<String> reasonKeys()
	{
		return _reasonKeys;
	}

	private static Limits parseLimits(Element element)
	{
		require(element, "limits", Set.of("sourceCandidates", "areasPerSource", "recipesPerProduct", "recipeDepth", "recipeNodes", "deficits", "receipts", "failuresPerSource", "sourceSwitches", "operationsPerStep", "payloadBytes", "activeTargetDistance", "verificationAttempts", "manorAttemptsPerTarget", "harvestAttemptsPerCorpse", "questCallbackWaitMillis", "questRules", "questScripts", "questTargetNpcsPerRule", "questExpectedVarsPerRule", "questItemIdsPerRead", "methodBindings"));
		return new Limits(integer(element, "sourceCandidates"), integer(element, "areasPerSource"), integer(element, "recipesPerProduct"), integer(element, "recipeDepth"), integer(element, "recipeNodes"), integer(element, "deficits"), integer(element, "receipts"), integer(element, "failuresPerSource"), integer(element, "sourceSwitches"), integer(element, "operationsPerStep"), integer(element, "payloadBytes"), integer(element, "activeTargetDistance"), integer(element, "verificationAttempts"), integer(element, "manorAttemptsPerTarget"), integer(element, "harvestAttemptsPerCorpse"), integer(element, "questCallbackWaitMillis"), integer(element, "questRules"), integer(element, "questScripts"), integer(element, "questTargetNpcsPerRule"), integer(element, "questExpectedVarsPerRule"), integer(element, "questItemIdsPerRead"), integer(element, "methodBindings"));
	}

	private static EnumMap<Method, MethodPolicy> parseMethods(Element element)
	{
		require(element, "methods", Set.of());
		final EnumMap<Method, MethodPolicy> result = new EnumMap<>(Method.class);
		final List<Element> methods = children(element);
		if (!methods.stream().map(child -> child.getAttribute("key")).toList().equals(List.of("death_drop", "spoil_sweep", "recipe_preparation", "manor_crop", "quest_collection")))
		{
			throw new IllegalArgumentException("Acquisition methods are not in canonical order.");
		}
		for (Element child : methods)
		{
			require(child, "method", Set.of("key", "status", "preference"));
			final Method method = Method.fromKey(child.getAttribute("key"));
			if (result.put(method, new MethodPolicy(method, MethodStatus.valueOf(child.getAttribute("status")), integer(child, "preference"))) != null)
			{
				throw new IllegalArgumentException("Duplicate acquisition method.");
			}
		}
		if (!result.keySet().equals(Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP, Method.RECIPE_PREPARATION, Method.MANOR_CROP, Method.QUEST_COLLECTION)))
		{
			throw new IllegalArgumentException("Acquisition methods are incomplete.");
		}
		if ((result.get(Method.DEATH_DROP).status() != MethodStatus.EXECUTABLE) || (result.get(Method.SPOIL_SWEEP).status() != MethodStatus.EXECUTABLE) || (result.get(Method.RECIPE_PREPARATION).status() != MethodStatus.PLANNING_ONLY) || (result.get(Method.MANOR_CROP).status() != MethodStatus.EXECUTABLE) || (result.get(Method.QUEST_COLLECTION).status() != MethodStatus.EXECUTABLE))
		{
			throw new IllegalArgumentException("Acquisition method statuses do not match Checkpoint 2.");
		}
		return result;
	}

	private static SourceScoring parseSourceScoring(Element element)
	{
		require(element, "sourceScoring", Set.of("methodPreference", "preferredMethodBonus", "topologyCost", "levelGap", "chanceUtility", "spawnCapacity", "resourceReserve", "failurePenalty", "switchPenalty", "recipeLeafReuse", "ambiguityThreshold"));
		return new SourceScoring(integer(element, "methodPreference"), integer(element, "preferredMethodBonus"), integer(element, "topologyCost"), integer(element, "levelGap"), integer(element, "chanceUtility"), integer(element, "spawnCapacity"), integer(element, "resourceReserve"), integer(element, "failurePenalty"), integer(element, "switchPenalty"), integer(element, "recipeLeafReuse"), integer(element, "ambiguityThreshold"));
	}

	private static SwitchPolicy parseSwitchPolicy(Element element)
	{
		require(element, "switchPolicy", Set.of("failureThreshold", "cooldownMinutes"));
		return new SwitchPolicy(integer(element, "failureThreshold"), integer(element, "cooldownMinutes"));
	}

	private static RecipePlanning parseRecipePlanning(Element element)
	{
		require(element, "recipePlanning", Set.of("preferCraftEligible", "preferLeafReuse", "deferManor", "deferQuest"));
		return new RecipePlanning(bool(element, "preferCraftEligible"), bool(element, "preferLeafReuse"), bool(element, "deferManor"), bool(element, "deferQuest"));
	}

	private static List<String> parseReasons(Element element)
	{
		require(element, "reasonKeys", Set.of());
		final List<String> result = new ArrayList<>();
		for (Element child : children(element))
		{
			require(child, "reason", Set.of("key"));
			result.add(child.getAttribute("key"));
		}
		if ((new HashSet<>(result).size() != result.size()) || !result.equals(result.stream().sorted().toList()))
		{
			throw new IllegalArgumentException("Acquisition reason keys must be unique and sorted.");
		}
		return result;
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(element.getAttribute(name));
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid acquisition integer: " + name, exception);
		}
	}

	private static boolean bool(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw new IllegalArgumentException("Invalid acquisition boolean: " + name);
		}
		return Boolean.parseBoolean(value);
	}

	private static List<Element> children(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node instanceof Element element)
			{
				result.add(element);
			}
			else if ((node.getNodeType() == Node.TEXT_NODE) && !node.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Unexpected acquisition policy text.");
			}
		}
		return result;
	}

	private static void require(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()) || !attributes.stream().allMatch(element::hasAttribute) || !children(element).isEmpty() && !Set.of("acquisitionPolicy", "methods", "reasonKeys").contains(name))
		{
			throw new IllegalArgumentException("Invalid acquisition policy element: " + name);
		}
	}

	private static void strictUtf8(byte[] bytes) throws CharacterCodingException
	{
		StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
	}

	private static String hash(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	public enum Method
	{
		DEATH_DROP("death_drop", 1),
		SPOIL_SWEEP("spoil_sweep", 2),
		RECIPE_PREPARATION("recipe_preparation", 3),
		MANOR_CROP("manor_crop", 4),
		QUEST_COLLECTION("quest_collection", 5);

		private final String _key;
		private final int _code;

		Method(String key, int code)
		{
			_key = key;
			_code = code;
		}

		public String key()
		{
			return _key;
		}

		public int code()
		{
			return _code;
		}

		public static Method fromKey(String key)
		{
			return java.util.Arrays.stream(values()).filter(value -> value._key.equals(key)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown acquisition method."));
		}

		public static Method fromCode(int code)
		{
			return java.util.Arrays.stream(values()).filter(value -> value._code == code).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown acquisition method code."));
		}
	}

	public enum MethodStatus
	{
		EXECUTABLE,
		PLANNING_ONLY,
		DEFERRED_CHECKPOINT_2
	}

	public record Limits(int sourceCandidates, int areasPerSource, int recipesPerProduct, int recipeDepth, int recipeNodes, int deficits, int receipts, int failuresPerSource, int sourceSwitches, int operationsPerStep, int payloadBytes, int activeTargetDistance, int verificationAttempts, int manorAttemptsPerTarget, int harvestAttemptsPerCorpse, int questCallbackWaitMillis, int questRules, int questScripts, int questTargetNpcsPerRule, int questExpectedVarsPerRule, int questItemIdsPerRead, int methodBindings)
	{
		public Limits
		{
			if ((sourceCandidates != 8) || (areasPerSource != 4) || (recipesPerProduct != 4) || (recipeDepth != 6) || (recipeNodes != 48) || (deficits != 32) || (receipts != 8) || (failuresPerSource != 8) || (sourceSwitches != 4) || (operationsPerStep != 8) || (payloadBytes != 4096) || (activeTargetDistance != 2000) || (verificationAttempts != 3) || (manorAttemptsPerTarget != 3) || (harvestAttemptsPerCorpse != 3) || (questCallbackWaitMillis != 6000) || (questRules != 8) || (questScripts != 4) || (questTargetNpcsPerRule != 8) || (questExpectedVarsPerRule != 4) || (questItemIdsPerRead != 16) || (methodBindings != 1))
			{
				throw new IllegalArgumentException("Acquisition policy limits must match the checkpoint contract.");
			}
		}
	}

	public record MethodPolicy(Method method, MethodStatus status, int preference)
	{
		public MethodPolicy
		{
			if ((method == null) || (status == null) || (preference < 0) || (preference > 1000))
			{
				throw new IllegalArgumentException("Invalid acquisition method policy.");
			}
		}
	}

	public record SourceScoring(int methodPreference, int preferredMethodBonus, int topologyCost, int levelGap, int chanceUtility, int spawnCapacity, int resourceReserve, int failurePenalty, int switchPenalty, int recipeLeafReuse, int ambiguityThreshold)
	{
		public SourceScoring
		{
			if ((methodPreference < 0) || (preferredMethodBonus < 0) || (topologyCost < 0) || (levelGap < 0) || (chanceUtility < 0) || (spawnCapacity < 0) || (resourceReserve < 0) || (failurePenalty < 0) || (switchPenalty < 0) || (recipeLeafReuse < 0) || (ambiguityThreshold < 0))
			{
				throw new IllegalArgumentException("Invalid acquisition source scoring policy.");
			}
		}
	}

	public record SwitchPolicy(int failureThreshold, int cooldownMinutes)
	{
		public SwitchPolicy
		{
			if ((failureThreshold < 1) || (failureThreshold > 8) || (cooldownMinutes < 1))
			{
				throw new IllegalArgumentException("Invalid acquisition switch policy.");
			}
		}
	}

	public record RecipePlanning(boolean preferCraftEligible, boolean preferLeafReuse, boolean deferManor, boolean deferQuest)
	{
		public RecipePlanning
		{
			if (deferManor || deferQuest)
			{
				throw new IllegalArgumentException("Checkpoint 2 must execute manor and quest collection.");
			}
		}
	}
}
