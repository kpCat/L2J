/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Deterministic build-time inventory and classification of every High Five quest
 * Java source and every rate-relevant AST site. This class is test infrastructure;
 * production does not load manifests or parse quest sources.
 */
public final class QuestRatesAstAuditor
{
	public static final String QUEST_ROOT = "dist/game/data/scripts/quests";
	private static final String ITEM_ROOT = "dist/game/data/stats/items";
	public static final String INVENTORY_PATH = "test/resources/phantoms/quest-rates/goal037-quest-inventory.tsv";
	public static final String SITES_PATH = "test/resources/phantoms/quest-rates/goal037-rate-sites.tsv";
	private static final String INVENTORY_HEADER = "path\tsha256\tsource_kind\tquest_id\tquest_name\trate_sites\tobjective_sites\tcontrol_sites\tterminal_item_sites\tadena_sites\txp_sp_sites\tnormal_drop_sites\tspoil_sites\tmanor_sites\texplicit_fixed_exceptions\tstatus";
	private static final String SITES_HEADER = "path\tsource_sha256\tline\tcolumn\tsite_fingerprint\tapi_or_expression\tsite_family\tsemantic_class\trate_authority\tdecision\tevidence";
	private static final Set<String> RANDOM_METHODS = Set.of("getRandom", "getRandomBoolean", "getRandomEntry");
	private static final Set<String> DIRECT_REWARD_METHODS = Set.of("addItem", "addAdena", "addExpAndSp");

	private QuestRatesAstAuditor()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			throw new IllegalArgumentException("Usage: QuestRatesAstAuditor <module-root> <generate|summary|verify|patch> [max-files]");
		}
		final Path moduleRoot = Path.of(args[0]);
		switch (args[1])
		{
			case "generate":
			{
				generate(moduleRoot);
				printSummary(scan(moduleRoot));
				break;
			}
			case "summary":
			{
				printSummary(scan(moduleRoot));
				break;
			}
			case "verify":
			{
				final Verification verification = verify(moduleRoot);
				verification.requireValid();
				printSummary(verification.audit());
				System.out.println("inventory_sha256=" + verification.inventorySha256() + " inventory_bytes=" + verification.inventoryBytes());
				System.out.println("sites_sha256=" + verification.sitesSha256() + " sites_bytes=" + verification.sitesBytes());
				break;
			}
			case "patch":
			{
				if (args.length != 3)
				{
					throw new IllegalArgumentException("Patch mode requires max-files.");
				}
				System.out.print(correctionPatch(moduleRoot, scan(moduleRoot), Integer.parseInt(args[2])));
				break;
			}
			default:
			{
				throw new IllegalArgumentException("Unknown mode: " + args[1]);
			}
		}
	}

	public static String correctionPatch(Path moduleRoot, Audit audit, int maxFiles) throws IOException
	{
		if (maxFiles <= 0)
		{
			throw new IllegalArgumentException("maxFiles must be positive.");
		}
		final Map<String, List<Site>> byPath = audit.sites().stream().filter(site -> site.decision() == Decision.REQUIRES_CORRECTION).filter(site -> site.apiOrExpression().startsWith("giveItems(")).collect(Collectors.groupingBy(Site::path, TreeMap::new, Collectors.toList()));
		if (byPath.isEmpty())
		{
			return "";
		}
		final StringBuilder patch = new StringBuilder("*** Begin Patch\n");
		int fileCount = 0;
		for (Map.Entry<String, List<Site>> entry : byPath.entrySet())
		{
			if (fileCount++ >= maxFiles)
			{
				break;
			}
			final Path source = moduleRoot.resolve(entry.getKey()).toAbsolutePath().normalize();
			final List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
			patch.append("*** Update File: ").append(source).append('\n');
			for (Site site : entry.getValue())
			{
				if ((site.semanticClass() == SemanticClass.TERMINAL_ITEM_REWARD) && (site.argumentCount() > 3))
				{
					throw new IllegalStateException("Canonical rewardItems has no compatible overload: " + site.path() + ":" + site.line() + " " + site.apiOrExpression());
				}
				final int lineIndex = Math.toIntExact(site.line() - 1);
				final String original = lines.get(lineIndex);
				final String replacement = site.semanticClass() == SemanticClass.CONTROL_SINGLETON ? "giveItemsWithoutQuestRate" : "rewardItems";
				final int methodIndex = original.indexOf("giveItems");
				if (methodIndex < 0)
				{
					throw new IllegalStateException("Expected giveItems token is absent: " + site.path() + ":" + site.line());
				}
				final String changed = original.substring(0, methodIndex) + replacement + original.substring(methodIndex + "giveItems".length());
				lines.set(lineIndex, changed);
				patch.append("@@\n-").append(original).append("\n+").append(changed).append('\n');
			}
		}
		return patch.append("*** End Patch\n").toString();
	}

	private static void printSummary(Audit audit)
	{
		System.out.println("sources=" + audit.sources().size() + " bytes=" + audit.totalBytes() + " sites=" + audit.sites().size() + " parse_failures=" + audit.parseFailures().size());
		for (SemanticClass semanticClass : SemanticClass.values())
		{
			System.out.println("semantic." + semanticClass + "=" + audit.sites().stream().filter(site -> site.semanticClass() == semanticClass).count());
		}
		for (Decision decision : Decision.values())
		{
			System.out.println("decision." + decision + "=" + audit.sites().stream().filter(site -> site.decision() == decision).count());
		}
	}

	public static Audit scan(Path moduleRoot) throws Exception
	{
		final Path canonicalModule = moduleRoot.toAbsolutePath().normalize();
		final Path questRoot = canonicalModule.resolve(QUEST_ROOT);
		if (!Files.isDirectory(questRoot))
		{
			throw new IllegalArgumentException("Quest source root is absent: " + questRoot);
		}

		final List<Path> files;
		try (var stream = Files.walk(questRoot))
		{
			files = stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java")).sorted(Comparator.comparing(path -> relative(canonicalModule, path))).toList();
		}
		if (files.isEmpty())
		{
			throw new IllegalStateException("Quest source corpus is empty.");
		}

		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null)
		{
			throw new IllegalStateException("A full JDK with the system Java compiler is required.");
		}
		final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		final ItemCatalog itemCatalog = ItemCatalog.load(canonicalModule.resolve(ITEM_ROOT));
		final List<MutableSource> mutableSources = new ArrayList<>(files.size());
		final List<Site> sites = new ArrayList<>();
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8))
		{
			final Iterable<? extends JavaFileObject> javaFiles = fileManager.getJavaFileObjectsFromPaths(files);
			final JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, List.of("-proc:none", "-source", "8", "-Xlint:none"), null, javaFiles);
			final List<CompilationUnitTree> units = new ArrayList<>();
			for (CompilationUnitTree unit : task.parse())
			{
				units.add(unit);
			}
			final Trees trees = Trees.instance(task);
			final SourcePositions positions = trees.getSourcePositions();
			for (CompilationUnitTree unit : units)
			{
				final Path sourcePath = sourcePath(unit);
				final String path = relative(canonicalModule, sourcePath);
				final byte[] bytes = Files.readAllBytes(sourcePath);
				final String sourceHash = sha256(bytes);
				final SourceFacts facts = new SourceFactsScanner().scan(unit);
				final MutableSource source = new MutableSource(path, sourceHash, bytes.length, sourceKind(path), questId(path), questName(path));
				mutableSources.add(source);
				final SiteScanner scanner = new SiteScanner(unit, positions, path, sourceHash, facts, itemCatalog);
				scanner.scan(unit, null);
				sites.addAll(scanner.sites());
			}
		}

		final List<String> parseFailures = diagnostics.getDiagnostics().stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).map(QuestRatesAstAuditor::diagnostic).sorted().toList();
		sites.sort(Site.ORDER);
		final Map<String, List<Site>> byPath = sites.stream().collect(Collectors.groupingBy(Site::path, TreeMap::new, Collectors.toList()));
		final List<Source> sources = new ArrayList<>(mutableSources.size());
		for (MutableSource mutable : mutableSources)
		{
			final List<Site> sourceSites = byPath.getOrDefault(mutable.path(), List.of());
			final EnumMap<SemanticClass, Long> counts = new EnumMap<>(SemanticClass.class);
			for (SemanticClass value : SemanticClass.values())
			{
				counts.put(value, sourceSites.stream().filter(site -> site.semanticClass() == value).count());
			}
			final long fixed = sourceSites.stream().filter(site -> site.decision() == Decision.EXPLICIT_FIXED_EXCEPTION).count();
			final boolean classified = sourceSites.stream().noneMatch(site -> site.decision() == Decision.REQUIRES_CORRECTION || site.semanticClass() == SemanticClass.UNSUPPORTED_REQUIRES_EVIDENCE);
			sources.add(new Source(mutable.path(), mutable.sha256(), mutable.bytes(), mutable.sourceKind(), mutable.questId(), mutable.questName(), sourceSites.size(), counts, fixed, classified ? "CLASSIFIED" : "REQUIRES_REVIEW"));
		}
		return new Audit(List.copyOf(sources), List.copyOf(sites), parseFailures, files.stream().mapToLong(QuestRatesAstAuditor::size).sum());
	}

	public static void generate(Path moduleRoot) throws Exception
	{
		final Audit audit = scan(moduleRoot);
		if (!audit.parseFailures().isEmpty())
		{
			throw new IllegalStateException("Quest AST parse failures:\n" + String.join("\n", audit.parseFailures()));
		}
		final Path inventory = moduleRoot.resolve(INVENTORY_PATH);
		final Path sites = moduleRoot.resolve(SITES_PATH);
		Files.createDirectories(inventory.getParent());
		Files.writeString(inventory, inventoryText(audit), StandardCharsets.UTF_8);
		Files.writeString(sites, sitesText(audit), StandardCharsets.UTF_8);
	}

	public static Verification verify(Path moduleRoot) throws Exception
	{
		final Audit audit = scan(moduleRoot);
		final String expectedInventory = readRequired(moduleRoot.resolve(INVENTORY_PATH));
		final String expectedSites = readRequired(moduleRoot.resolve(SITES_PATH));
		final List<String> failures = validateAgainst(audit, expectedInventory, expectedSites);
		return new Verification(audit, List.copyOf(failures), sha256(expectedInventory.getBytes(StandardCharsets.UTF_8)), expectedInventory.getBytes(StandardCharsets.UTF_8).length, sha256(expectedSites.getBytes(StandardCharsets.UTF_8)), expectedSites.getBytes(StandardCharsets.UTF_8).length);
	}

	public static List<String> validateAgainst(Audit audit, String expectedInventory, String expectedSites)
	{
		final List<String> failures = new ArrayList<>(audit.parseFailures());
		final String actualInventory = inventoryText(audit);
		final String actualSites = sitesText(audit);
		if (!expectedInventory.equals(actualInventory))
		{
			failures.add(firstDifference("quest inventory", expectedInventory, actualInventory));
		}
		if (!expectedSites.equals(actualSites))
		{
			failures.add(firstDifference("rate-site manifest", expectedSites, actualSites));
		}
		validateManifestRows(expectedInventory, expectedSites, failures);
		return List.copyOf(failures);
	}

	public static String inventoryText(Audit audit)
	{
		final StringBuilder result = new StringBuilder(INVENTORY_HEADER).append('\n');
		for (Source source : audit.sources())
		{
			result.append(source.path()).append('\t').append(source.sha256()).append('\t').append(source.sourceKind()).append('\t').append(source.questId()).append('\t').append(source.questName()).append('\t').append(source.rateSites()).append('\t').append(source.count(SemanticClass.OBJECTIVE_COLLECTION)).append('\t').append(source.count(SemanticClass.CONTROL_SINGLETON)).append('\t').append(source.count(SemanticClass.TERMINAL_ITEM_REWARD)).append('\t').append(source.count(SemanticClass.TERMINAL_ADENA_REWARD)).append('\t').append(source.count(SemanticClass.TERMINAL_XP_SP_REWARD)).append('\t').append(source.count(SemanticClass.NORMAL_DROP)).append('\t').append(source.count(SemanticClass.SPOIL)).append('\t').append(source.count(SemanticClass.MANOR)).append('\t').append(source.explicitFixedExceptions()).append('\t').append(source.status()).append('\n');
		}
		return result.toString();
	}

	public static String sitesText(Audit audit)
	{
		final StringBuilder result = new StringBuilder(SITES_HEADER).append('\n');
		for (Site site : audit.sites())
		{
			result.append(site.path()).append('\t').append(site.sourceSha256()).append('\t').append(site.line()).append('\t').append(site.column()).append('\t').append(site.fingerprint()).append('\t').append(field(site.apiOrExpression())).append('\t').append(site.siteFamily()).append('\t').append(site.semanticClass()).append('\t').append(site.rateAuthority()).append('\t').append(site.decision()).append('\t').append(field(site.evidence())).append('\n');
		}
		return result.toString();
	}

	private static void validateManifestRows(String inventoryText, String sitesText, List<String> failures)
	{
		validateHeader(inventoryText, INVENTORY_HEADER, "inventory", failures);
		validateHeader(sitesText, SITES_HEADER, "rate sites", failures);
		final Set<String> paths = new HashSet<>();
		for (String line : linesAfterHeader(inventoryText))
		{
			final String[] columns = line.split("\t", -1);
			if (columns.length != 16)
			{
				failures.add("Malformed inventory row: " + line);
				continue;
			}
			if (!paths.add(columns[0]))
			{
				failures.add("Duplicate inventory row: " + columns[0]);
			}
			if (!"CLASSIFIED".equals(columns[15]))
			{
				failures.add("Unclassified inventory source: " + columns[0] + " status=" + columns[15]);
			}
		}
		final Set<String> fingerprints = new HashSet<>();
		for (String line : linesAfterHeader(sitesText))
		{
			final String[] columns = line.split("\t", -1);
			if (columns.length != 11)
			{
				failures.add("Malformed rate-site row: " + line);
				continue;
			}
			if (!fingerprints.add(columns[4]))
			{
				failures.add("Duplicate rate-site fingerprint: " + columns[0] + ":" + columns[2] + " " + columns[4]);
			}
			try
			{
				SemanticClass.valueOf(columns[7]);
				RateAuthority.valueOf(columns[8]);
				final Decision decision = Decision.valueOf(columns[9]);
				if (decision == Decision.REQUIRES_CORRECTION)
				{
					failures.add("Unresolved rate-site decision: " + columns[0] + ":" + columns[2]);
				}
			}
			catch (IllegalArgumentException exception)
			{
				failures.add("Malformed semantic/rate/decision value: " + columns[0] + ":" + columns[2]);
			}
		}
	}

	private static List<String> linesAfterHeader(String text)
	{
		return text.lines().skip(1).filter(line -> !line.isBlank()).toList();
	}

	private static void validateHeader(String text, String header, String name, List<String> failures)
	{
		if (!text.startsWith(header + "\n"))
		{
			failures.add("Malformed " + name + " header.");
		}
	}

	private static String firstDifference(String name, String expected, String actual)
	{
		final List<String> expectedLines = expected.lines().toList();
		final List<String> actualLines = actual.lines().toList();
		final int bound = Math.min(expectedLines.size(), actualLines.size());
		for (int index = 0; index < bound; index++)
		{
			if (!expectedLines.get(index).equals(actualLines.get(index)))
			{
				return "Stale " + name + " at row " + (index + 1) + ": expected [" + expectedLines.get(index) + "] actual [" + actualLines.get(index) + "]";
			}
		}
		return "Stale " + name + ": expected rows=" + expectedLines.size() + " actual rows=" + actualLines.size();
	}

	private static String readRequired(Path path) throws IOException
	{
		if (!Files.isRegularFile(path))
		{
			throw new IllegalStateException("Required Goal037 manifest is absent: " + path);
		}
		return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static String field(String value)
	{
		return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}

	private static String diagnostic(Diagnostic<? extends JavaFileObject> diagnostic)
	{
		final String source = diagnostic.getSource() == null ? "<unknown>" : Path.of(diagnostic.getSource().toUri()).toString().replace('\\', '/');
		return source + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() + " " + diagnostic.getMessage(Locale.ROOT);
	}

	private static Path sourcePath(CompilationUnitTree unit)
	{
		final URI uri = unit.getSourceFile().toUri();
		return Path.of(uri).toAbsolutePath().normalize();
	}

	private static String relative(Path moduleRoot, Path source)
	{
		return moduleRoot.relativize(source.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static long size(Path path)
	{
		try
		{
			return Files.size(path);
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Could not read quest source size: " + path, exception);
		}
	}

	private static String sourceKind(String path)
	{
		if (path.endsWith("/QuestMasterHandler.java"))
		{
			return "MASTER";
		}
		if (path.endsWith("/AbstractSagaQuest.java"))
		{
			return "SHARED_ABSTRACT";
		}
		return path.contains("/Dummy/") ? "DUMMY" : "QUEST";
	}

	private static int questId(String path)
	{
		final String name = questName(path);
		if (name.matches("Q\\d{5}_.*"))
		{
			return Integer.parseInt(name.substring(1, 6));
		}
		return 0;
	}

	private static String questName(String path)
	{
		return path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static String compact(Tree tree)
	{
		return tree.toString().replaceAll("\\s+", " ").trim();
	}

	private static final class SourceFactsScanner extends TreePathScanner<Void, Void>
	{
		private final Map<String, Tree> _constants = new HashMap<>();
		private final Map<String, List<? extends Tree>> _arrays = new HashMap<>();
		private final List<Tree> _registeredExpressions = new ArrayList<>();
		private final List<Tree> _countedExpressions = new ArrayList<>();

		SourceFacts scan(CompilationUnitTree unit)
		{
			scan(unit, null);
			return new SourceFacts(expand(_registeredExpressions), expand(_countedExpressions), Map.copyOf(_constants));
		}

		@Override
		public Void visitVariable(VariableTree tree, Void unused)
		{
			if (tree.getInitializer() != null)
			{
				_constants.put(tree.getName().toString(), tree.getInitializer());
				if (tree.getInitializer() instanceof NewArrayTree array && array.getInitializers() != null)
				{
					_arrays.put(tree.getName().toString(), array.getInitializers());
				}
			}
			return super.visitVariable(tree, unused);
		}

		@Override
		public Void visitMethodInvocation(MethodInvocationTree tree, Void unused)
		{
			final String method = methodName(tree);
			if ("registerQuestItems".equals(method))
			{
				_registeredExpressions.addAll(tree.getArguments());
			}
			else if ("getQuestItemsCount".equals(method))
			{
				for (int index = 1; index < tree.getArguments().size(); index++)
				{
					_countedExpressions.add(tree.getArguments().get(index));
				}
			}
			return super.visitMethodInvocation(tree, unused);
		}

		private Set<String> expand(List<Tree> expressions)
		{
			final Set<String> result = new HashSet<>();
			for (Tree expression : expressions)
			{
				final String value = compact(expression);
				final List<? extends Tree> elements = _arrays.get(value);
				if (elements == null)
				{
					result.add(value);
				}
				else
				{
					for (Tree element : elements)
					{
						result.add(compact(element));
					}
				}
			}
			return Set.copyOf(result);
		}
	}

	private static final class SiteScanner extends TreePathScanner<Void, Void>
	{
		private final CompilationUnitTree _unit;
		private final SourcePositions _positions;
		private final String _path;
		private final String _sourceHash;
		private final SourceFacts _facts;
		private final ItemCatalog _itemCatalog;
		private final List<Site> _sites = new ArrayList<>();
		private final Deque<String> _methods = new ArrayDeque<>();

		SiteScanner(CompilationUnitTree unit, SourcePositions positions, String path, String sourceHash, SourceFacts facts, ItemCatalog itemCatalog)
		{
			_unit = unit;
			_positions = positions;
			_path = path;
			_sourceHash = sourceHash;
			_facts = facts;
			_itemCatalog = itemCatalog;
		}

		List<Site> sites()
		{
			return List.copyOf(_sites);
		}

		@Override
		public Void visitMethod(MethodTree tree, Void unused)
		{
			_methods.push(tree.getName().toString());
			try
			{
				return super.visitMethod(tree, unused);
			}
			finally
			{
				_methods.pop();
			}
		}

		@Override
		public Void visitMethodInvocation(MethodInvocationTree tree, Void unused)
		{
			final String method = methodName(tree);
			final boolean qualified = tree.getMethodSelect() instanceof MemberSelectTree;
			if (("giveItems".equals(method) || "giveItemsWithoutQuestRate".equals(method)) && (tree.getArguments().size() >= 2))
			{
				add(tree, classifyGiveItems(tree, method));
			}
			else if ("giveQuestItemsUpTo".equals(method))
			{
				add(tree, new Classification(SiteFamily.QUEST_GRANT, SemanticClass.OBJECTIVE_COLLECTION, RateAuthority.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, Decision.FIXED_BY_GOAL037, "Explicit cap-aware objective helper applies the quest-item amount multiplier once, clamps to the scripted cap, and leaves chance/condition authority in the script."));
			}
			else if ("giveItemRandomly".equals(method))
			{
				add(tree, new Classification(SiteFamily.QUEST_RANDOM_GRANT, SemanticClass.OBJECTIVE_COLLECTION, RateAuthority.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, Decision.CANONICAL, "Canonical giveItemRandomly scales only the granted amount; its script-native chance and cap remain unchanged."));
			}
			else if ("rewardItems".equals(method))
			{
				add(tree, new Classification(SiteFamily.QUEST_REWARD, SemanticClass.TERMINAL_ITEM_REWARD, RateAuthority.RATE_QUEST_REWARD_ITEM, Decision.CANONICAL, "Canonical Quest.rewardItems applies the configured item-category reward multiplier."));
			}
			else if ("giveAdena".equals(method))
			{
				final boolean appliesRates = tree.getArguments().size() >= 3 && "true".equals(compact(tree.getArguments().get(2)));
				add(tree, new Classification(SiteFamily.QUEST_REWARD, appliesRates ? SemanticClass.TERMINAL_ADENA_REWARD : SemanticClass.FIXED_SCRIPT_MECHANIC, appliesRates ? RateAuthority.RATE_QUEST_REWARD_ADENA : RateAuthority.FIXED_SCRIPT, appliesRates ? Decision.CANONICAL : Decision.EXPLICIT_FIXED_EXCEPTION, appliesRates ? "Canonical Quest.giveAdena(..., true) delegates to rate-aware rewardItems." : "The call explicitly disables quest reward rates; the exact source-hashed site preserves the scripted fixed amount."));
			}
			else if ("addExpAndSp".equals(method))
			{
				add(tree, qualified ? new Classification(SiteFamily.DIRECT_REWARD_MUTATION, SemanticClass.UNSUPPORTED_REQUIRES_EVIDENCE, RateAuthority.UNKNOWN, Decision.REQUIRES_CORRECTION, "Direct addExpAndSp bypasses the canonical Quest reward helper.") : new Classification(SiteFamily.QUEST_REWARD, SemanticClass.TERMINAL_XP_SP_REWARD, RateAuthority.RATE_QUEST_REWARD_XP_SP, Decision.CANONICAL, "Canonical Quest.addExpAndSp applies quest XP/SP and existing premium/stat authorities."));
			}
			else if ("getQuestItemsCount".equals(method))
			{
				add(tree, new Classification(SiteFamily.OBJECTIVE_CAP, SemanticClass.OBJECTIVE_COLLECTION, RateAuthority.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, Decision.CANONICAL, "Quest-item count/cap observation is source-hashed; amount scaling must not bypass its scripted cap or condition transition."));
			}
			else if (RANDOM_METHODS.contains(method) || isRndInvocation(tree) || isMathRandom(tree))
			{
				final boolean objectiveChance = objectiveChance(getCurrentPath());
				add(tree, new Classification(SiteFamily.CHANCE_OR_RANDOM, objectiveChance ? SemanticClass.OBJECTIVE_COLLECTION : SemanticClass.NOT_REWARD, objectiveChance ? RateAuthority.SCRIPT_NATIVE_CHANCE : RateAuthority.SCRIPT_NATIVE_RANDOMNESS, Decision.EXPLICIT_FIXED_EXCEPTION, objectiveChance ? "Script-native objective chance is classified independently from the quest-item amount multiplier." : "Randomness is source-hashed and does not directly grant or rate a reward at this AST site."));
			}
			else if (qualified && "addItem".equals(method) && tree.getMethodSelect() instanceof MemberSelectTree member && member.getExpression().toString().endsWith("IU"))
			{
				// InventoryUpdate packet construction is not an inventory mutation.
			}
			else if (qualified && "addItem".equals(method) && !_methods.isEmpty() && "exchangeCrystal".equals(_methods.peek()))
			{
				add(tree, new Classification(SiteFamily.DIRECT_REWARD_MUTATION, SemanticClass.FIXED_SCRIPT_MECHANIC, RateAuthority.FIXED_SCRIPT, Decision.EXPLICIT_FIXED_EXCEPTION, "The source-hashed exchangeCrystal conversion destroys one input crystal and creates one upgraded/broken crystal; applying quest reward rates would duplicate conversion output."));
			}
			else if (qualified && DIRECT_REWARD_METHODS.contains(method))
			{
				add(tree, new Classification(SiteFamily.DIRECT_REWARD_MUTATION, SemanticClass.UNSUPPORTED_REQUIRES_EVIDENCE, RateAuthority.UNKNOWN, Decision.REQUIRES_CORRECTION, "Direct reward-like mutation requires explicit canonical-helper evidence."));
			}
			return super.visitMethodInvocation(tree, unused);
		}

		@Override
		public Void visitMemberSelect(MemberSelectTree tree, Void unused)
		{
			if (tree.getExpression().toString().endsWith("RatesConfig"))
			{
				add(tree, new Classification(SiteFamily.DIRECT_RATE_FIELD, rateSemantic(tree.getIdentifier().toString()), rateAuthority(tree.getIdentifier().toString()), Decision.EXPLICIT_FIXED_EXCEPTION, "Direct rate-field arithmetic is pinned to this exact AST expression and reviewed by the Goal037 matrix."));
			}
			return super.visitMemberSelect(tree, unused);
		}

		private Classification classifyGiveItems(MethodInvocationTree tree, String method)
		{
			if ("giveItemsWithoutQuestRate".equals(method))
			{
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.CONTROL_SINGLETON, RateAuthority.FIXED_SCRIPT, Decision.FIXED_BY_GOAL037, "Explicit no-rate helper preserves lifecycle/control multiplicity independently from quest-item amount rates.");
			}
			if (tree.getArguments().size() < 2)
			{
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.UNSUPPORTED_REQUIRES_EVIDENCE, RateAuthority.UNKNOWN, Decision.REQUIRES_CORRECTION, "Unrecognized giveItems overload requires evidence.");
			}
			final boolean terminal = terminalContext(getCurrentPath());
			if (tree.getArguments().size() == 2)
			{
				return terminal ? new Classification(SiteFamily.QUEST_GRANT, SemanticClass.TERMINAL_ITEM_REWARD, RateAuthority.RATE_QUEST_REWARD_ITEM, Decision.REQUIRES_CORRECTION, "ItemHolder grant shares its nearest statement block with exitQuest and bypasses canonical rewardItems semantics.") : new Classification(SiteFamily.QUEST_GRANT, SemanticClass.FIXED_SCRIPT_MECHANIC, RateAuthority.FIXED_SCRIPT, Decision.EXPLICIT_FIXED_EXCEPTION, "Non-terminal ItemHolder grant is source-hashed and retains its scripted fixed semantics.");
			}
			final String item = compact(tree.getArguments().get(1));
			final Long itemId = integerValue(tree.getArguments().get(1), _facts.constants(), new HashSet<>());
			final boolean metadataKnown = (itemId != null) && _itemCatalog.itemIds().contains(itemId.intValue());
			final boolean questItem = metadataKnown && _itemCatalog.questItemIds().contains(itemId.intValue());
			final boolean registered = _facts.registeredItems().contains(item);
			final boolean combatMethod = !_methods.isEmpty() && Set.of("onKill", "onAttack", "onSpellFinished", "onSkillSee").contains(_methods.peek());
			final boolean counted = _facts.countedItems().contains(item);
			if (questItem || (!metadataKnown && registered))
			{
				final String metadataEvidence = questItem ? "Resolved item " + itemId + " is marked is_questitem=true in the datapack." : "The source registers this unresolved expression as a quest item.";
				if (terminal)
				{
					return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.CONTROL_SINGLETON, RateAuthority.FIXED_SCRIPT, Decision.REQUIRES_CORRECTION, metadataEvidence + " Its nearest statement block exits the quest, so multiplicity is lifecycle state rather than an economic reward.");
				}
				if (combatMethod && (counted || !transitionContext(getCurrentPath())))
				{
					return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.OBJECTIVE_COLLECTION, RateAuthority.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, Decision.CANONICAL, metadataEvidence + " The combat callback grants repeated objective progress; chance remains script-native.");
				}
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.CONTROL_SINGLETON, RateAuthority.FIXED_SCRIPT, Decision.REQUIRES_CORRECTION, metadataEvidence + " The non-combat grant is a handoff/progression payload whose scripted multiplicity must remain fixed.");
			}
			if (terminal)
			{
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.TERMINAL_ITEM_REWARD, RateAuthority.RATE_QUEST_REWARD_ITEM, Decision.REQUIRES_CORRECTION, metadataKnown ? "Resolved non-quest item " + itemId + " is granted in the nearest statement block that exits the quest and must use canonical rewardItems semantics." : "Unresolved non-registered item expression is granted in the nearest statement block that exits the quest and must use canonical rewardItems semantics.");
			}
			if (registered && combatMethod)
			{
				if (transitionContext(getCurrentPath()))
				{
					return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.CONTROL_SINGLETON, RateAuthority.FIXED_SCRIPT, Decision.REQUIRES_CORRECTION, "The combat callback advances quest lifecycle state while granting this registered item; its scripted singleton multiplicity must remain fixed.");
				}
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.OBJECTIVE_COLLECTION, RateAuthority.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, Decision.CANONICAL, "The source registers this item and grants it from a combat callback; the exact expression remains source-hashed.");
			}
			if (registered)
			{
				return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.CONTROL_SINGLETON, RateAuthority.FIXED_SCRIPT, Decision.REQUIRES_CORRECTION, "The source registers this non-combat item as quest lifecycle state; its scripted multiplicity must remain fixed.");
			}
			return new Classification(SiteFamily.QUEST_GRANT, SemanticClass.FIXED_SCRIPT_MECHANIC, RateAuthority.FIXED_SCRIPT, Decision.EXPLICIT_FIXED_EXCEPTION, "Non-terminal scripted item grant is source-hashed and retains fixed giveItems semantics.");
		}

		private boolean objectiveChance(TreePath path)
		{
			for (TreePath current = path; current != null; current = current.getParentPath())
			{
				final Tree leaf = current.getLeaf();
				if (leaf instanceof IfTree ifTree)
				{
					final String branch = compact(ifTree);
					return branch.contains("giveItems(") || branch.contains("giveQuestItemsUpTo(") || branch.contains("giveItemRandomly(");
				}
				if (leaf instanceof MethodInvocationTree invocation && "giveItemRandomly".equals(methodName(invocation)))
				{
					return true;
				}
				if (leaf instanceof MethodTree)
				{
					break;
				}
			}
			return false;
		}

		private boolean transitionContext(TreePath path)
		{
			for (TreePath current = path; current != null; current = current.getParentPath())
			{
				final Tree leaf = current.getLeaf();
				if ((leaf instanceof BlockTree) || (leaf instanceof CaseTree))
				{
					final String text = compact(leaf);
					return text.contains("setCond(") || text.contains("startQuest(") || text.contains("exitQuest(");
				}
				if (leaf instanceof MethodTree)
				{
					break;
				}
			}
			return false;
		}

		private boolean terminalContext(TreePath path)
		{
			boolean terminal = false;
			for (TreePath current = path; current != null; current = current.getParentPath())
			{
				final Tree leaf = current.getLeaf();
				if (leaf instanceof CaseTree)
				{
					return terminal || compact(leaf).contains("exitQuest(");
				}
				if ((leaf instanceof BlockTree) && ((current.getParentPath() == null) || !(current.getParentPath().getLeaf() instanceof MethodTree)))
				{
					terminal |= compact(leaf).contains("exitQuest(");
				}
				else if (leaf instanceof IfTree)
				{
					terminal |= compact(leaf).contains("exitQuest(");
				}
				if (leaf instanceof MethodTree)
				{
					return terminal;
				}
			}
			return terminal;
		}

		private void add(Tree tree, Classification classification)
		{
			final long start = _positions.getStartPosition(_unit, tree);
			if (start < 0)
			{
				throw new IllegalStateException("AST site has no source position: " + _path + " " + tree);
			}
			final long line = _unit.getLineMap().getLineNumber(start);
			final long column = _unit.getLineMap().getColumnNumber(start);
			final String expression = compact(tree);
			final String method = _methods.isEmpty() ? "<initializer>" : _methods.peek();
			final String fingerprint = sha256((_path + "|" + line + "|" + column + "|" + method + "|" + classification.siteFamily() + "|" + expression).getBytes(StandardCharsets.UTF_8));
			final int argumentCount = tree instanceof MethodInvocationTree invocation ? invocation.getArguments().size() : 0;
			_sites.add(new Site(_path, _sourceHash, line, column, fingerprint, expression, classification.siteFamily(), classification.semanticClass(), classification.rateAuthority(), classification.decision(), classification.evidence(), argumentCount));
		}
	}

	private static String methodName(MethodInvocationTree tree)
	{
		if (tree.getMethodSelect() instanceof IdentifierTree identifier)
		{
			return identifier.getName().toString();
		}
		if (tree.getMethodSelect() instanceof MemberSelectTree member)
		{
			return member.getIdentifier().toString();
		}
		return tree.getMethodSelect().toString();
	}

	private static boolean isRndInvocation(MethodInvocationTree tree)
	{
		return tree.getMethodSelect().toString().startsWith("Rnd.") || tree.getMethodSelect().toString().contains(".Rnd.");
	}

	private static boolean isMathRandom(MethodInvocationTree tree)
	{
		return "Math.random".equals(tree.getMethodSelect().toString());
	}

	private static SemanticClass rateSemantic(String field)
	{
		if (field.contains("SPOIL"))
		{
			return SemanticClass.SPOIL;
		}
		if (field.contains("MANOR"))
		{
			return SemanticClass.MANOR;
		}
		if (field.contains("DROP"))
		{
			return SemanticClass.NORMAL_DROP;
		}
		if (field.contains("QUEST_REWARD_XP") || field.contains("QUEST_REWARD_SP"))
		{
			return SemanticClass.TERMINAL_XP_SP_REWARD;
		}
		if (field.contains("QUEST_REWARD_ADENA"))
		{
			return SemanticClass.TERMINAL_ADENA_REWARD;
		}
		return SemanticClass.FIXED_SCRIPT_MECHANIC;
	}

	private static RateAuthority rateAuthority(String field)
	{
		if (field.contains("SPOIL"))
		{
			return RateAuthority.ORDINARY_SPOIL;
		}
		if (field.contains("MANOR"))
		{
			return RateAuthority.ORDINARY_MANOR;
		}
		if (field.contains("DROP"))
		{
			return RateAuthority.ORDINARY_DROP;
		}
		if (field.contains("QUEST_REWARD_XP") || field.contains("QUEST_REWARD_SP"))
		{
			return RateAuthority.RATE_QUEST_REWARD_XP_SP;
		}
		if (field.contains("QUEST_REWARD_ADENA"))
		{
			return RateAuthority.RATE_QUEST_REWARD_ADENA;
		}
		return RateAuthority.FIXED_SCRIPT;
	}

	private static Long integerValue(Tree expression, Map<String, Tree> constants, Set<String> resolving)
	{
		if (expression instanceof LiteralTree literal && literal.getValue() instanceof Number number)
		{
			return number.longValue();
		}
		if (expression instanceof ParenthesizedTree parenthesized)
		{
			return integerValue(parenthesized.getExpression(), constants, resolving);
		}
		if (expression instanceof TypeCastTree cast)
		{
			return integerValue(cast.getExpression(), constants, resolving);
		}
		if (expression instanceof IdentifierTree identifier)
		{
			final String name = identifier.getName().toString();
			if (!resolving.add(name))
			{
				return null;
			}
			try
			{
				final Tree initializer = constants.get(name);
				return initializer == null ? null : integerValue(initializer, constants, resolving);
			}
			finally
			{
				resolving.remove(name);
			}
		}
		if (expression instanceof UnaryTree unary)
		{
			final Long value = integerValue(unary.getExpression(), constants, resolving);
			if (value == null)
			{
				return null;
			}
			return switch (unary.getKind())
			{
				case UNARY_MINUS -> -value;
				case UNARY_PLUS -> value;
				case BITWISE_COMPLEMENT -> ~value;
				default -> null;
			};
		}
		if (expression instanceof BinaryTree binary)
		{
			final Long left = integerValue(binary.getLeftOperand(), constants, resolving);
			final Long right = integerValue(binary.getRightOperand(), constants, resolving);
			if ((left == null) || (right == null))
			{
				return null;
			}
			return switch (binary.getKind())
			{
				case PLUS -> left + right;
				case MINUS -> left - right;
				case MULTIPLY -> left * right;
				case DIVIDE -> right == 0 ? null : left / right;
				case REMAINDER -> right == 0 ? null : left % right;
				case LEFT_SHIFT -> left << right;
				case RIGHT_SHIFT -> left >> right;
				case UNSIGNED_RIGHT_SHIFT -> left >>> right;
				case AND -> left & right;
				case OR -> left | right;
				case XOR -> left ^ right;
				default -> null;
			};
		}
		return null;
	}

	private record ItemCatalog(Set<Integer> itemIds, Set<Integer> questItemIds)
	{
		static ItemCatalog load(Path itemRoot) throws IOException, XMLStreamException
		{
			if (!Files.isDirectory(itemRoot))
			{
				throw new IllegalArgumentException("Item data root is absent: " + itemRoot);
			}
			final List<Path> files;
			try (var stream = Files.walk(itemRoot))
			{
				files = stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList();
			}
			final Set<Integer> itemIds = new HashSet<>();
			final Set<Integer> questItemIds = new HashSet<>();
			final XMLInputFactory factory = XMLInputFactory.newFactory();
			factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
			for (Path file : files)
			{
				try (InputStream input = Files.newInputStream(file))
				{
					final XMLStreamReader reader = factory.createXMLStreamReader(input, StandardCharsets.UTF_8.name());
					Integer itemId = null;
					boolean questItem = false;
					try
					{
						while (reader.hasNext())
						{
							final int event = reader.next();
							if (event == XMLStreamConstants.START_ELEMENT)
							{
								if ("item".equals(reader.getLocalName()))
								{
									itemId = Integer.valueOf(reader.getAttributeValue(null, "id"));
									questItem = false;
								}
								else if ((itemId != null) && "set".equals(reader.getLocalName()) && "is_questitem".equals(reader.getAttributeValue(null, "name")) && "true".equalsIgnoreCase(reader.getAttributeValue(null, "val")))
								{
									questItem = true;
								}
							}
							else if ((event == XMLStreamConstants.END_ELEMENT) && "item".equals(reader.getLocalName()) && (itemId != null))
							{
								itemIds.add(itemId);
								if (questItem)
								{
									questItemIds.add(itemId);
								}
								itemId = null;
							}
						}
					}
					finally
					{
						reader.close();
					}
				}
			}
			return new ItemCatalog(Set.copyOf(itemIds), Set.copyOf(questItemIds));
		}
	}

	public enum SemanticClass
	{
		OBJECTIVE_COLLECTION,
		CONTROL_SINGLETON,
		TERMINAL_ITEM_REWARD,
		TERMINAL_ADENA_REWARD,
		TERMINAL_XP_SP_REWARD,
		FIXED_SCRIPT_MECHANIC,
		NORMAL_DROP,
		SPOIL,
		MANOR,
		NOT_REWARD,
		UNSUPPORTED_REQUIRES_EVIDENCE
	}

	public enum SiteFamily
	{
		QUEST_GRANT,
		QUEST_RANDOM_GRANT,
		QUEST_REWARD,
		CHANCE_OR_RANDOM,
		DIRECT_REWARD_MUTATION,
		DIRECT_RATE_FIELD,
		OBJECTIVE_CAP
	}

	public enum RateAuthority
	{
		QUEST_ITEM_DROP_AMOUNT_MULTIPLIER,
		RATE_QUEST_REWARD_ITEM,
		RATE_QUEST_REWARD_ADENA,
		RATE_QUEST_REWARD_XP_SP,
		SCRIPT_NATIVE_CHANCE,
		SCRIPT_NATIVE_RANDOMNESS,
		FIXED_SCRIPT,
		ORDINARY_DROP,
		ORDINARY_SPOIL,
		ORDINARY_MANOR,
		UNKNOWN
	}

	public enum Decision
	{
		CANONICAL,
		FIXED_BY_GOAL037,
		EXPLICIT_FIXED_EXCEPTION,
		REQUIRES_CORRECTION
	}

	public record Audit(List<Source> sources, List<Site> sites, List<String> parseFailures, long totalBytes)
	{
		public Audit
		{
			sources = List.copyOf(sources);
			sites = List.copyOf(sites);
			parseFailures = List.copyOf(parseFailures);
		}
	}

	public record Verification(Audit audit, List<String> failures, String inventorySha256, long inventoryBytes, String sitesSha256, long sitesBytes)
	{
		public Verification
		{
			failures = List.copyOf(failures);
		}

		public void requireValid()
		{
			if (!failures.isEmpty())
			{
				throw new AssertionError(String.join(System.lineSeparator(), failures));
			}
		}
	}

	public record Source(String path, String sha256, long bytes, String sourceKind, int questId, String questName, long rateSites, Map<SemanticClass, Long> counts, long explicitFixedExceptions, String status)
	{
		public Source
		{
			counts = Map.copyOf(counts);
		}

		long count(SemanticClass semanticClass)
		{
			return counts.getOrDefault(semanticClass, 0L);
		}
	}

	public record Site(String path, String sourceSha256, long line, long column, String fingerprint, String apiOrExpression, SiteFamily siteFamily, SemanticClass semanticClass, RateAuthority rateAuthority, Decision decision, String evidence, int argumentCount)
	{
		private static final Comparator<Site> ORDER = Comparator.comparing(Site::path).thenComparingLong(Site::line).thenComparingLong(Site::column).thenComparing(Site::fingerprint);
	}

	private record MutableSource(String path, String sha256, long bytes, String sourceKind, int questId, String questName)
	{
	}

	private record SourceFacts(Set<String> registeredItems, Set<String> countedItems, Map<String, Tree> constants)
	{
	}

	private record Classification(SiteFamily siteFamily, SemanticClass semanticClass, RateAuthority rateAuthority, Decision decision, String evidence)
	{
	}
}
