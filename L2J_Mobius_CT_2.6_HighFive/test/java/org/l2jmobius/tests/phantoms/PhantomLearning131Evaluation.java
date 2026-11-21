/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4.Intent;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4.AuditControl;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3.Family;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.NativeCastTrace;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.AuditRows;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.AggroFact;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.EscapeFact;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.Fact;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.HealFact;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.RechargeFact;
import org.l2jmobius.tests.phantoms.PhantomLearning131AuditOriginals.ItemLedger;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Arm;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.BlockPlan;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Episode;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Objective;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Scene;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Side;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Slot;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Stratum;

/** Read-only AUDIT verifier. Its trust roots are the locally frozen manifest and independently captured launch origins. */
public final class PhantomLearning131Evaluation
{
	private static final String REPORT = "TASK131_EVALUATION_REPORT_V2";
	static final int DOCUMENT_MAX_BYTES = 8 * 1024 * 1024;
	static final int PAID_MAX_BYTES = 32 * 1024 * 1024;
	static final String PAID_SCHEMA = "NATIVE_PAID131_V8";
	private static final Set<String> OPPONENTS = Set.of("B0", "A1", "B1");
	private static final Set<String> GROUP_SIZES = Set.of("1x2", "2x2", "3x5", "9x9");
	private static final int BANK_SIZE = 32;
	private static final int BOOT_LIMIT = 12;

	private enum Role { DAMAGE, HEAL, TANK, CONTROL, SUPPORT, OPPONENT }

	private record Document(String sha, Map<String, String> metadata, List<String[]> rows)
	{
		String get(String key)
		{
			final String value = metadata.get(key);
			require(value != null, "MISSING_" + key);
			return value;
		}
	}

	private record Block(BlockPlan plan, String sceneId, String fixtureSha, String opponent,
		int level, int opponentSize, BigDecimal budget, long deadline, Set<Role> requiredRoles) { }
	private record Protocol(String bank, Instant frozenAt, Map<String, Block> blocks, Map<Integer, BigDecimal> prices, Set<String> encounterFingerprints) { }
	record ScheduledArm(String bank, BlockPlan block, int position, String sceneId, String fixtureSha, String opponent, int level, int opponentSize)
	{
		Slot slot() { return block.order().get(position); }
	}
	record SoloDamageBinding(String episodeId, String sha, long started, long actorEpoch, long targetEpoch) { }
	record DuelDamageBinding(String episodeId, String sha, long started, long scoredEpoch, long opponentEpoch) { }
	record HealBinding(String episodeId, String sha, long started, Map<Integer, Long> playerEpochs, Set<Integer> scoredPlayers, Map<Integer, Long> npcEpochs) { }
	record PolicyArmBinding(String episodeId, String sha, long started, String modelSha, Map<Integer, Long> scoredEpochs) { }
	private record Audit(Document manifest, Map<String, Block> blocks, Map<Integer, BigDecimal> prices, Instant lockedAt, Set<String> encounters) { }
	private record Occurrence(String boot, int bootOrder, String id, String sealSha, String modelProofSha, int attempt, int position, Block block) { }
	private record Participant(long epoch, Side side, Role role) { }
	private record Binding(Document document, Slot slot, long started, Map<Integer, Participant> participants) { }
	private record EpisodeDocuments(Document events, Document paid) { }
	private record Evaluation(Episode episode, EpisodeWindow window, String boot, Raw raw) { }
	record EpisodeWindow(long started, long combatElapsed, long settledElapsed)
	{
		EpisodeWindow
		{
			require(started > 0 && combatElapsed > 0 && settledElapsed >= combatElapsed, "NATIVE_EPISODE_CLOCKS");
			Math.addExact(started, settledElapsed);
		}

		long combatEnd() { return started + combatElapsed; }
		long settledEnd() { return started + settledElapsed; }

		long offset(String kind, long stamp)
		{
			final long offset = Math.subtractExact(stamp, started);
			require(offset >= 0 && offset <= settledElapsed, "OUTSIDE_NATIVE_SETTLEMENT");
			require(offset <= combatElapsed || kind.equals("KILL_CREDIT") || kind.equals("CC_END") || kind.equals("ESCAPE_ARRIVAL"), "OUTSIDE_NATIVE_COMBAT");
			return offset;
		}
	}
	private record ProcessIdentity(long pid, Instant started) { }
	private record ActorEpoch(int object, long epoch) { }
	private record Action(int source, int target, Role role, String selected, int stage) { }
	private record Control(int target, String effect) { }
	private record ActiveControl(long started, String operation, boolean scored) { }
	private record Subject(String operation, int target) { }

	private PhantomLearning131Evaluation() { }

	public static void main(String[] args) throws IOException
	{
		if (args.length == 3 && args[0].equals("--plan"))
		{
			final var schedule = frozenSchedule(Path.of(args[1]), args[2]);
			System.out.println("EVAL131_FROZEN_PLAN_OK bank=" + schedule.getFirst().bank() + " blocks=" + BANK_SIZE + " slots=" + schedule.size());
			return;
		}
		if ((args.length == 4 || args.length == 6) && args[0].equals("--admission"))
		{
			final Path prior = args.length == 6 ? Path.of(args[4]) : null;
			final String priorSha = args.length == 6 ? digest(args[5]) : null;
			for (String row : admissionPlan(Path.of(args[1]), digest(args[2]), digest(args[3]), prior, priorSha)) { System.out.println(row); }
			return;
		}
		if (args.length != 4)
		{
			throw new IllegalArgumentException("EVAL131_ARGUMENTS");
		}
		System.exit(run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), digest(args[3])));
	}

	/** Read the immutable pre-TRAIN arm schedule without needing models, results, or a native origin. */
	static List<ScheduledArm> frozenSchedule(Path protocolPath, String expectedSha) throws IOException
	{
		final Protocol protocol = readProtocol(protocolPath, expectedSha);
		final var arms = new ArrayList<ScheduledArm>();
		for (Block block : orderedBlocks(protocol.blocks()))
		{
			for (int position = 0; position < block.plan().order().size(); position++)
			{
				arms.add(new ScheduledArm(protocol.bank(), block.plan(), position, block.sceneId(), block.fixtureSha(), block.opponent(), block.level(), block.opponentSize()));
			}
		}
		return List.copyOf(arms);
	}

	/** Return the controller's frozen slot order and budget after verifying the independent bank and C/R archive. */
	static List<String> admissionPlan(Path path, String manifestSha, String evaluatorSha) throws IOException
	{
		return admissionPlan(path, manifestSha, evaluatorSha, null, null);
	}

	static List<String> admissionPlan(Path path, String manifestSha, String evaluatorSha, Path priorPath, String priorSha) throws IOException
	{
		final Audit audit = readAudit(path, evaluatorSha);
		final Document manifest = audit.manifest();
		require(manifest.sha().equals(digest(manifestSha)), "ADMISSION_MANIFEST_PIN");
		require(!audit.lockedAt().isAfter(Instant.now()), "ADMISSION_BANK_NOT_FROZEN");
		final String bank = manifest.get("bank");
		if (bank.equals("bank-2"))
		{
			require(priorPath != null && priorSha != null, "ADMISSION_PRIOR_BANK_REQUIRED");
			final Protocol prior = readProtocol(priorPath, priorSha);
			require(prior.bank().equals("bank-1") && prior.frozenAt().isBefore(Instant.parse(manifest.get("trainStartedAt"))), "ADMISSION_PRIOR_BANK_PIN");
			require(Collections.disjoint(audit.encounters(), prior.encounterFingerprints()), "ADMISSION_BANK_REUSE");
		}
		else { require(bank.equals("bank-1") && priorPath == null && priorSha == null, "ADMISSION_BANK_SCOPE"); }
		verifyModelArchive(path, manifest);
		final var rows = new ArrayList<String>();
		rows.add(row("meta", "schema", "AUDIT_ADMISSION131_V1"));
		rows.add(row("meta", "manifestSha", manifest.sha()));
		for (String key : List.of("bank", "protocolSha", "splitSha", "sourceSha", "jarSha", "configSha", "policySha", "evaluatorSha",
			"candidateSha", "candidateHead", "referenceSha", "referenceHead", "lockedAt"))
		{
			rows.add(row("meta", key, manifest.get(key)));
		}
		for (Block block : orderedBlocks(audit.blocks()))
		{
			final var plan = block.plan();
			final boolean pvp = plan.stratum() == Stratum.DUEL_PVP || plan.stratum() == Stratum.GROUP_PVP;
			final int actors = plan.scene().initialRosterSize() + (pvp ? block.opponentSize() : 0);
			final long budgetSeconds = 1 + (block.deadline() - 1) / 1_000_000_000L;
			for (int position = 0; position < plan.order().size(); position++)
			{
				final String scene = plan.id() + "-" + position;
				require(scene.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}") && budgetSeconds < 28_800, "ADMISSION_SCENE_BOUNDS");
				rows.add(row("slot", scene, actors, budgetSeconds));
			}
		}
		return List.copyOf(rows);
	}

	private static List<Block> orderedBlocks(Map<String, Block> blocks)
	{
		return blocks.values().stream().sorted(Comparator.comparing((Block block) -> block.plan().stratum())
			.thenComparingInt(block -> block.plan().index())).toList();
	}

	private static void verifyModelArchive(Path path, Document manifest) throws IOException
	{
		ActionContinuationV4.verifyAuditModelOrder(modelArchive(path), manifest.get("referenceHead"), manifest.get("candidateHead"),
			manifest.get("referenceSha"), manifest.get("candidateSha"));
	}

	static int run(Path manifest, Path originals, Path output, String evaluatorSha) throws IOException
	{
		physical(manifest.getParent());
		physical(output.getParent());
		require(originals.isAbsolute() && originals.normalize().equals(originals) && output.isAbsolute() && output.normalize().equals(output)
			&& !output.startsWith(originals) && !output.startsWith(modelArchive(manifest)) && !output.equals(manifest) && !output.equals(sidecar(manifest, "protocol"))
			&& !output.equals(sidecar(manifest, "splits")) && !output.equals(sidecar(manifest, "origins")) && !Files.exists(output), "OUTPUT_SCOPE");
		final var lines = new ArrayList<String>();
		lines.add("meta\tschema\t" + REPORT);
		int exit = 0;
		try
		{
			final Audit audit = readAudit(manifest, evaluatorSha);
			physical(originals);
			require(Files.isDirectory(originals) && !manifest.startsWith(originals), "INDEPENDENT_MANIFEST_SCOPE");
			lines.addAll(evaluate(audit, manifest, originals));
		}
		catch (NoSuchFileException | Incomplete missing)
		{
			lines.add("meta\tstatus\tUNKNOWN");
			lines.add("meta\treason\t" + (missing instanceof Incomplete ? missing.getMessage() : "MISSING_SEALED_INPUT"));
			exit = 2;
		}
		catch (IOException | IllegalArgumentException | ArithmeticException | java.time.DateTimeException rejected)
		{
			lines.add("meta\tstatus\tREJECTED_EVIDENCE");
			lines.add("meta\treason\t" + (rejected.getMessage() != null && rejected.getMessage().matches("[A-Za-z0-9_.-]+")
				? rejected.getMessage() : "INVALID_SEALED_INPUT"));
			exit = 3;
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		return exit;
	}

	private static Audit readAudit(Path path, String evaluatorSha) throws IOException
	{
		final Document manifest = document(path, "FROZEN_EVALUATION131_V2", "schema", "bank", "protocolSha", "splitSha", "sourceSha", "jarSha",
			"configSha", "policySha", "evaluatorSha", "modelSchema", "candidateSha", "candidateHead", "referenceSha", "referenceHead", "referenceAt", "selectedAt", "lockedAt",
			"opponent.B0", "opponent.A1", "opponent.B1", "trainStartedAt");
		require(manifest.rows().isEmpty(), "MANIFEST_ROWS");
		final String bank = token(manifest.get("bank"));
		require(manifest.get("sourceSha").matches("[a-f0-9]{40}|[a-f0-9]{64}"), "SOURCE_SHA");
		for (String key : List.of("protocolSha", "splitSha", "jarSha", "configSha", "policySha", "evaluatorSha", "modelSchema",
			"candidateSha", "candidateHead", "referenceSha", "referenceHead", "opponent.B0", "opponent.A1", "opponent.B1"))
		{
			digest(manifest.get(key));
		}
		require(manifest.get("evaluatorSha").equals(evaluatorSha), "EVALUATOR_DRIFT");
		require(manifest.get("modelSchema").equals(ActionValueV4.SCHEMA), "AUDIT_MODEL_SCHEMA_DRIFT");
		final Instant locked = Instant.parse(manifest.get("lockedAt"));
		final Instant reference = Instant.parse(manifest.get("referenceAt"));
		final Instant selected = Instant.parse(manifest.get("selectedAt"));
		require(reference.isBefore(selected) && !selected.isAfter(locked) && manifest.get("referenceSha").equals(manifest.get("opponent.A1"))
			&& !manifest.get("candidateSha").equals(manifest.get("referenceSha")) && !manifest.get("candidateHead").equals(manifest.get("referenceHead"))
			&& Set.of(manifest.get("opponent.B0"), manifest.get("opponent.A1"), manifest.get("opponent.B1")).size() == OPPONENTS.size(), "FROZEN_MODEL_LINEAGE");

		final Protocol protocol = readProtocol(sidecar(path, "protocol"), manifest.get("protocolSha"));
		require(protocol.bank().equals(bank), "PROTOCOL_PIN");
		final Instant trainStarted = Instant.parse(manifest.get("trainStartedAt"));
		require(protocol.frozenAt().isBefore(trainStarted) && trainStarted.isBefore(reference), "PROTOCOL_FREEZE_ORDER");
		final Document splits = document(sidecar(path, "splits"), "AUDIT_SPLITS131_V1", "schema", "bank", "protocolSha");
		require(splits.get("bank").equals(bank) && splits.get("protocolSha").equals(manifest.get("protocolSha")) && splits.sha().equals(manifest.get("splitSha")), "SPLIT_PIN");
		final var seenEncounters = new HashSet<String>();
		final var splitSources = new HashSet<String>();
		final var splitNames = new HashSet<String>();
		for (String[] row : splits.rows())
		{
			fields(row, "source", 4);
			require(Set.of("TRAIN", "TUNING").contains(row[1]) && splitSources.add(digest(row[3])), "SPLIT_SOURCE");
			seenEncounters.add(digest(row[2]));
			splitNames.add(row[1]);
		}
		require(splitNames.size() == 2, "SPLIT_COVERAGE");
		require(Collections.disjoint(seenEncounters, protocol.encounterFingerprints()), "SPLIT_LEAKAGE_OR_DUPLICATE_SCENE");
		return new Audit(manifest, protocol.blocks(), protocol.prices(), locked, protocol.encounterFingerprints());
	}

	private static Protocol readProtocol(Path path, String expectedSha) throws IOException
	{
		final Document protocol = document(path, "AUDIT_PROTOCOL131_V1", "schema", "bank", "seed", "frozenAt");
		require(protocol.sha().equals(digest(expectedSha)), "PROTOCOL_PIN");
		final String bank = token(protocol.get("bank"));
		Long.parseLong(protocol.get("seed"));
		final Instant frozenAt = Instant.parse(protocol.get("frozenAt"));
		final var blocks = new LinkedHashMap<String, Block>();
		final var sceneIds = new HashSet<String>();
		final var encounterFingerprints = new HashSet<String>();
		final var opponentLabels = new HashSet<String>();
		final var prices = new HashMap<Integer, BigDecimal>();
		final var groupSizes = new HashSet<String>();
		final var strataLevels = new EnumMap<Stratum, Map<Integer, Integer>>(Stratum.class);
		final var bankRoles = EnumSet.noneOf(Role.class);
		for (String[] row : protocol.rows())
		{
			if (row[0].equals("price"))
			{
				fields(row, "price", 3);
				require(prices.putIfAbsent(object(row[1]), decimal(row[2], false)) == null, "DUPLICATE_PRICE");
				continue;
			}
			fields(row, "block", 20);
			final String sceneId = token(row[4]);
			require(sceneIds.add(sceneId) && encounterFingerprints.add(digest(row[6])), "SPLIT_LEAKAGE_OR_DUPLICATE_SCENE");
			require(OPPONENTS.contains(row[7]), "OPPONENT_LINEAGE");
			opponentLabels.add(row[7]);
			final Stratum stratum = Stratum.valueOf(row[2]);
			final int level = object(row[8]);
			require(level == 52 || level == 64 || (level >= 76 && level <= 85), "STRATUM_LEVEL");
			strataLevels.computeIfAbsent(stratum, _ -> new HashMap<>()).merge(Math.min(level, 76), 1, Integer::sum);
			final long deadline = positive(row[14]);
			final BigDecimal budget = decimal(row[13], false);
			final var scene = new Scene(Objective.valueOf(row[9]), object(row[10]), object(row[11]), budget.doubleValue(), deadline);
			final int opponentSize = object(row[12]);
			validateRoster(stratum, scene.initialRosterSize(), opponentSize, groupSizes);
			final var roles = EnumSet.noneOf(Role.class);
			for (String role : row[15].split(",", -1))
			{
				require(roles.add(Role.valueOf(role)), "DUPLICATE_REQUIRED_ROLE");
			}
			require(!roles.contains(Role.OPPONENT), "REQUIRED_ROLE");
			bankRoles.addAll(roles);
			final var order = new ArrayList<Slot>();
			for (int position = 16; position < row.length; position++)
			{
				final String[] slot = row[position].split(":", -1);
				require(slot.length == 2, "SLOT");
				order.add(new Slot(Arm.valueOf(slot[0]), Side.valueOf(slot[1])));
			}
			final var plan = new BlockPlan(token(row[1]), stratum, Integer.parseInt(row[3]), scene, order);
			require(blocks.putIfAbsent(plan.id(), new Block(plan, sceneId, digest(row[5]), row[7], level, opponentSize, budget, deadline, Set.copyOf(roles))) == null, "DUPLICATE_BLOCK");
		}
		require(blocks.size() == BANK_SIZE && !prices.isEmpty() && opponentLabels.equals(OPPONENTS) && groupSizes.equals(GROUP_SIZES)
			&& bankRoles.equals(EnumSet.complementOf(EnumSet.of(Role.OPPONENT))) && strataLevels.size() == Stratum.values().length
			&& strataLevels.values().stream().allMatch(count -> count.equals(Map.of(52, 2, 64, 2, 76, 4))), "INCOMPLETE_FROZEN_BANK");
		TrainingEvaluation131.validatePlan(blocks.values().stream().map(Block::plan).toList());
		return new Protocol(bank, frozenAt, Map.copyOf(blocks), Map.copyOf(prices), Set.copyOf(encounterFingerprints));
	}

	private static List<String> evaluate(Audit audit, Path manifestPath, Path originals) throws IOException
	{
		final Document manifest = audit.manifest();
		final Document origins = document(sidecar(manifestPath, "origins"), "AUDIT_ORIGINS131_V1", "schema", "manifestSha");
		require(origins.get("manifestSha").equals(manifest.sha()) && origins.rows().size() <= BOOT_LIMIT, "ORIGINS_MANIFEST");
		final var occurrences = new HashMap<String, List<Occurrence>>();
		final var bootIds = new HashSet<String>();
		final var processIds = new HashSet<ProcessIdentity>();
		final var episodeIds = new HashSet<String>();
		Instant previousProcessStart = null;
		for (String[] row : origins.rows())
		{
			fields(row, "boot", 5);
			final String bootId = token(row[1]);
			final var process = new ProcessIdentity(positive(row[2]), Instant.parse(row[3]));
			require(bootIds.add(bootId) && processIds.add(process) && !process.started().isBefore(audit.lockedAt()), "BOOT_IDENTITY_OR_FREEZE");
			require(previousProcessStart == null || process.started().isAfter(previousProcessStart), "BOOT_PROCESS_ORDER");
			previousProcessStart = process.started();
			final Document boot = document(originals.resolve("boots").resolve(bootId).resolve("complete131.tsv"), "NATIVE_BOOT131_V2",
				"schema", "bank", "manifestSha", "bootId", "pid", "processStarted", "sourceSha", "jarSha", "configSha", "policySha");
			require(boot.sha().equals(digest(row[4])) && boot.get("manifestSha").equals(manifest.sha()) && boot.get("bootId").equals(bootId)
				&& boot.get("pid").equals(row[2]) && boot.get("processStarted").equals(row[3]), "OWNED_BOOT_PIN");
			for (String key : List.of("bank", "sourceSha", "jarSha", "configSha", "policySha"))
			{
				require(boot.get(key).equals(manifest.get(key)), "BOOT_DRIFT_" + key);
			}
			for (String[] member : boot.rows())
			{
				fields(member, "episode", 7);
				final Block block = audit.blocks().get(member[1]);
				final int position = Integer.parseInt(member[2]);
				final int attempt = Integer.parseInt(member[3]);
				final String episodeId = token(member[4]);
				require(block != null && position >= 0 && position < 4 && (attempt == 0 || attempt == 1) && episodeIds.add(episodeId), "EPISODE_IDENTITY");
				final String modelProofSha = member[6].equals("NONE") ? member[6] : digest(member[6]);
				occurrences.computeIfAbsent(member[1], _ -> new ArrayList<>()).add(new Occurrence(bootId, bootIds.size(), episodeId, digest(member[5]),
					modelProofSha, attempt, position, block));
			}
		}

		final var actorEpochs = new HashSet<ActorEpoch>();
		final var actorBlocks = new HashMap<Integer, String>();
		final var eventKeys = new HashSet<String>();
		final var rows = new ArrayList<Evaluation>();
		boolean modelOrderVerified = false;
		final var plans = audit.blocks().values().stream().map(Block::plan)
			.sorted(Comparator.comparing(BlockPlan::stratum).thenComparingInt(BlockPlan::index)).toList();
		for (BlockPlan plan : plans)
		{
			final var block = occurrences.getOrDefault(plan.id(), List.of());
			if (block.size() < 4)
			{
				throw new Incomplete("INCOMPLETE_MATCHED_BLOCK");
			}
			require(block.size() == 4 || block.size() == 8, "WHOLE_BLOCK_RETRY");
			final var ordered = block.stream().sorted(Comparator.comparingInt(Occurrence::attempt).thenComparingInt(Occurrence::position)).toList();
			int previousBoot = 0;
			long previousEnd = Long.MIN_VALUE;
			for (int index = 0; index < ordered.size(); index++)
			{
				final Occurrence occurrence = ordered.get(index);
				final boolean retry = ordered.size() == 8 && index < 4;
				require(occurrence.attempt() == index / 4 && occurrence.position() == index % 4, "EPISODE_ORDER_OR_DUPLICATE_SLOT");
				final Path directory = originals.resolve("episodes").resolve(occurrence.id());
				final Document seal = readEpisodeSeal(directory, occurrence.sealSha());
				final Binding binding = bind(directory, manifest, occurrence, seal);
				require(occurrence.bootOrder() >= previousBoot && (occurrence.bootOrder() != previousBoot || binding.started() >= previousEnd), "EXECUTED_BLOCK_ORDER");
				previousBoot = occurrence.bootOrder();
				previousEnd = binding.started();
				final var documents = readEpisodeDocuments(directory, occurrence.id(), binding.document().sha(), seal.get("eventsSha"), seal.get("paidOriginalsSha"));
				final Document evidence = documents.events(), paid = documents.paid();
				if (retry)
				{
					require(seal.get("status").equals("PRE_ACTION_INFRA_RETRY") && seal.get("combatElapsedNanos").equals("0")
						&& seal.get("settledElapsedNanos").equals("0")
						&& occurrence.modelProofSha().equals("NONE") && evidence.rows().isEmpty() && paid.rows().isEmpty(), "RETRY_AFTER_ACTIVITY");
					continue;
				}
				require(seal.get("status").equals("DRAINED"), "DRAINED_NATIVE_EPISODE");
				for (var actor : binding.participants().entrySet())
				{
					final String priorBlock = actorBlocks.putIfAbsent(actor.getKey(), plan.id());
					require((priorBlock == null || priorBlock.equals(plan.id())) && actorEpochs.add(new ActorEpoch(actor.getKey(), actor.getValue().epoch())), "REUSED_ACTOR_COHORT_OR_EPOCH");
				}
				final var window = new EpisodeWindow(binding.started(), positive(seal.get("combatElapsedNanos")), positive(seal.get("settledElapsedNanos")));
				previousEnd = window.settledEnd();
				final boolean pvp = plan.stratum() == Stratum.DUEL_PVP || plan.stratum() == Stratum.GROUP_PVP;
				final var auditRows = PhantomLearning131AuditOriginals.decodeAuditRows(paid.rows(), occurrence.id());
				verifyNativeProjection(evidence.rows(), auditRows, epochs(binding.participants()), window, pvp);
				verifyEffectSides(auditRows, binding.participants());
				if (!modelOrderVerified && (binding.slot().arm() == Arm.C || binding.slot().arm() == Arm.R) && !occurrence.modelProofSha().equals("NONE"))
				{
					verifyModelArchive(manifestPath, manifest);
					modelOrderVerified = true;
				}
				verifyPolicyArm(manifestPath, manifest, directory, occurrence, binding, auditRows, window);
				final var raw = new Raw(binding, occurrence.block(), audit.prices(), window);
				for (String[] event : evidence.rows())
				{
					raw.accept(event, eventKeys);
				}
				rows.add(new Evaluation(raw.finish(occurrence), window, occurrence.boot(), raw));
			}
		}
		return report(audit, origins.sha(), plans, rows);
	}

	private static void verifyPolicyArm(Path manifestPath, Document manifest, Path directory, Occurrence occurrence, Binding binding,
		AuditRows originals, EpisodeWindow window) throws IOException
	{
		final Arm arm = binding.slot().arm();
		final var stock = stockControl(occurrence.block().plan().stratum());
		if (occurrence.modelProofSha().equals("NONE"))
		{
			throw new Incomplete(switch (arm)
			{
				case H -> "HEURISTIC_DISPATCH_MISSING";
				case S -> stock.name() + "_DISPATCH_MISSING";
				default -> "FROZEN_MODEL_DISPATCH_MISSING";
			});
		}
		final String head = switch (arm)
		{
			case H, S -> "NONE";
			case C -> manifest.get("candidateHead");
			case R -> manifest.get("referenceHead");
		};
		final Document proof = document(directory.resolve("policy131.tsv"), "NATIVE_FROZEN_POLICY131_V1", "schema", "episodeId", "bindingSha", "arm", "modelHead");
		require(proof.sha().equals(occurrence.modelProofSha()) && proof.get("episodeId").equals(occurrence.id()) && proof.get("bindingSha").equals(binding.document().sha())
			&& proof.get("arm").equals(arm.name()) && proof.get("modelHead").equals(head), "FROZEN_MODEL_ORIGIN_PIN");
		final var paid = new HashMap<String, NativeCastTrace>();
		for (var damage : originals.damage().values())
		{
			if (binding.participants().get(damage.actor()).side() == binding.slot().side()) { paid.put(damage.operationId(), damage.trace()); }
		}
		for (var heal : originals.heals().values())
		{
			final var cast = heal.original().cast();
			if (binding.participants().get(cast.actorObjectId()).side() == binding.slot().side()) { paid.put(heal.operationId(), cast); }
		}
		if (arm == Arm.H)
		{
			verifyHeuristicDispatch(occurrence.id(), occurrence.block().fixtureSha(), proof.rows(), paid, window);
		}
		else if (arm == Arm.S)
		{
			verifyControlDispatch(stock, occurrence.id(), occurrence.block().fixtureSha(), proof.rows(), paid, window);
		}
		else
		{
			verifyFrozenDispatch(modelArchive(manifestPath), head, occurrence.id(), occurrence.block().fixtureSha(), binding.document().get("modelSha"),
				proof.rows(), paid, window);
		}
	}

	static void verifyFrozenDispatch(Path lineage, String head, String episode, String fixtureSha, String modelSha, List<String[]> rows,
		Map<String, NativeCastTrace> paid, EpisodeWindow window) throws IOException
	{
		final var decisions = verifyDispatch(rows, episode, paid, window);
		final var plan = decisions.getFirst().plan();
		require(plan.policyId().equals("MODEL_V2") && plan.split().equals("AUDIT") && plan.episode().equals(episode)
			&& plan.sourceSha().equals(fixtureSha) && plan.modelSha().equals(modelSha), "FROZEN_DISPATCH_PLAN");
		ActionExplorationV4.frozenCommitted(plan, lineage, head).verifyFrozenLog(decisions);
	}

	static void verifyHeuristicDispatch(String episode, String fixtureSha, List<String[]> rows,
		Map<String, NativeCastTrace> paid, EpisodeWindow window)
	{
		verifyControlDispatch(AuditControl.HEURISTIC, episode, fixtureSha, rows, paid, window);
	}

	static void verifyStockPveDispatch(String episode, String fixtureSha, List<String[]> rows,
		Map<String, NativeCastTrace> paid, EpisodeWindow window)
	{
		verifyControlDispatch(AuditControl.STOCK_PVE, episode, fixtureSha, rows, paid, window);
	}

	static void verifyControlDispatch(AuditControl control, String episode, String fixtureSha, List<String[]> rows,
		Map<String, NativeCastTrace> paid, EpisodeWindow window)
	{
		final var decisions = verifyDispatch(rows, episode, paid, window);
		final var plan = decisions.getFirst().plan();
		require(plan.auditControl() == control && plan.episode().equals(episode) && plan.sourceSha().equals(fixtureSha), "CONTROL_DISPATCH_PLAN");
		ActionExplorationV4.verifyControlLog(control, decisions);
		require(!paid.isEmpty(), "CONTROL_PAID_DISPATCH_REQUIRED");
	}

	private static List<ActionExplorationV4.Decision> verifyDispatch(List<String[]> rows, String episode, Map<String, NativeCastTrace> paid,
		EpisodeWindow window)
	{
		require(!rows.isEmpty() && rows.size() <= 128, "MODEL_PROOF_ROW_LIMIT");
		final var decoder = Base64.getUrlDecoder();
		final var encoder = Base64.getUrlEncoder().withoutPadding();
		final var decisions = new ArrayList<ActionExplorationV4.Decision>();
		final var decisionKeys = new HashSet<String>();
		final var intents = new HashMap<String, Intent>();
		for (String[] row : rows)
		{
			length(row, 2);
			final byte[] bytes = decoder.decode(row[1]);
			switch (row[0])
			{
				case "decision" ->
				{
					final var decision = ActionExplorationV4.Decision.restore(bytes);
					require(row[1].equals(encoder.encodeToString(decision.bytes())) && decision.observation().capturedNanos() >= window.started()
						&& decision.capturedNanos() <= window.combatEnd() && decisionKeys.add(decision.sha256()), "MODEL_DECISION_ORIGIN");
					decisions.add(decision);
				}
				case "intent" ->
				{
					final var intent = Intent.restore(bytes);
					require(row[1].equals(encoder.encodeToString(intent.bytes())) && intent.capturedNanos() <= window.combatEnd()
						&& intents.putIfAbsent(PhantomLearning131AuditDamage.operationId(episode, intent.owner()), intent) == null, "MODEL_INTENT_ORIGIN");
				}
				default -> throw new IllegalArgumentException("EVAL131_MODEL_PROOF_ROW_KIND");
			}
		}
		require(!decisions.isEmpty(), "MISSING_FROZEN_MODEL_DECISIONS");
		final var used = new HashSet<String>();
		for (var intent : intents.values())
		{
			final String key = intent.decision().sha256();
			require(decisionKeys.contains(key) && used.add(key), "MODEL_INTENT_WITHOUT_UNIQUE_DECISION");
		}
		paid.forEach((operation, cast) -> require(matchesPolicyDispatch(intents.get(operation), cast), "MODEL_NATIVE_DISPATCH"));
		return decisions;
	}

	private static boolean matchesPolicyDispatch(Intent intent, NativeCastTrace cast)
	{
		if (intent == null || intent.capturedNanos() < cast.decisionNanos() || intent.capturedNanos() > cast.admittedNanos()) { return false; }
		final var decision = intent.decision();
		final var selected = decision.selected();
		final var target = decision.observation().entity(cast.targetObjectId());
		if (target == null) { return false; }
		final Family expected = switch (target.role())
		{
			case ALLY -> Family.HEAL;
			case ENEMY -> Family.DAMAGE;
			default -> null;
		};
		return expected != null && selected.family() == expected && decision.stock().family() == expected
			&& decision.offered().stream().allMatch(action -> action.family() == expected)
			&& selected.actorObjectId() == cast.actorObjectId() && selected.actorEpoch() == cast.actorEpoch()
			&& selected.targetObjectId() == cast.targetObjectId() && selected.targetEpoch() == cast.targetEpoch()
			&& selected.skillId() == cast.skillId() && selected.skillLevel() == cast.skillLevel()
			&& decision.stock().skillId() == cast.baselineSkillId() && decision.stock().skillLevel() == cast.baselineSkillLevel()
			&& TacticalObservationV3.hash(decision.observation().bytes()).equals(cast.observationSha());
	}

	private static Document readEpisodeSeal(Path directory, String sealSha) throws IOException
	{
		final Document seal = document(directory.resolve("complete131.tsv"), "NATIVE_EPISODE131_V3", "schema", "bindingSha", "eventsSha",
			"paidOriginalsSha", "status", "combatElapsedNanos", "settledElapsedNanos");
		require(seal.sha().equals(sealSha) && seal.rows().isEmpty(), "EPISODE_SEAL_PIN");
		return seal;
	}

	private static EpisodeDocuments readEpisodeDocuments(Path directory, String id, String bindingSha, String eventsSha, String paidSha) throws IOException
	{
		final Document events = document(directory.resolve("events131.tsv"), "NATIVE_EVENTS131_V1", "schema", "episodeId", "bindingSha");
		require(events.sha().equals(eventsSha) && events.get("episodeId").equals(id) && events.get("bindingSha").equals(bindingSha), "RAW_EVENTS_PIN");
		final Document paid = document(directory.resolve("paid131.tsv"), PAID_MAX_BYTES, PAID_SCHEMA, "schema", "episodeId", "bindingSha");
		require(paid.sha().equals(paidSha) && paid.get("episodeId").equals(id) && paid.get("bindingSha").equals(bindingSha), "PAID_ORIGINALS_PIN");
		return new EpisodeDocuments(events, paid);
	}

	static EpisodeWindow verifyClosedDamage(Path path, String episodeId, String bindingSha, boolean pvp, long combatEnd, long settled,
		List<String[]> events, List<String[]> paid) throws IOException
	{
		final Document binding = readBinding(path);
		require(binding.get("episodeId").equals(episodeId) && binding.sha().equals(bindingSha), "EPISODE_BINDING_PIN");
		final long started = positive(binding.get("startNanos"));
		final var window = new EpisodeWindow(started, Math.subtractExact(combatEnd, started), Math.subtractExact(settled, started));
		verifyClosedDamage(binding, episodeId, pvp, window, events, paid);
		return window;
	}

	static void verifySealedDamage(Path path, String episodeId, String sealSha, boolean pvp) throws IOException
	{
		final Document seal = readEpisodeSeal(path.getParent(), sealSha);
		require(seal.get("status").equals("DRAINED"), "DRAINED_NATIVE_EPISODE");
		verifyPersistedDamage(path, episodeId, seal.get("bindingSha"), pvp, positive(seal.get("combatElapsedNanos")),
			positive(seal.get("settledElapsedNanos")), seal.get("eventsSha"), seal.get("paidOriginalsSha"));
	}

	static void verifyPersistedDamage(Path path, String episodeId, String bindingSha, boolean pvp, long combatElapsed, long settledElapsed,
		String eventsSha, String paidSha) throws IOException
	{
		final Document binding = readBinding(path);
		require(binding.get("episodeId").equals(episodeId) && binding.sha().equals(bindingSha), "EPISODE_BINDING_PIN");
		final EpisodeDocuments originals = readEpisodeDocuments(path.getParent(), episodeId, bindingSha, eventsSha, paidSha);
		final var window = new EpisodeWindow(positive(binding.get("startNanos")), combatElapsed, settledElapsed);
		verifyClosedDamage(binding, episodeId, pvp, window, originals.events().rows(), originals.paid().rows());
	}

	private static void verifyClosedDamage(Document binding, String episodeId, boolean pvp, EpisodeWindow window,
		List<String[]> events, List<String[]> paid)
	{
		final var originals = PhantomLearning131AuditOriginals.decodeAuditRows(paid, episodeId);
		require(!originals.damage().isEmpty() || !originals.heals().isEmpty() || !originals.recharges().isEmpty() || !originals.aggro().isEmpty()
			|| !originals.escapes().isEmpty(), "NO_PAID_NATIVE_ORIGINALS");
		final var roster = participants(binding, Side.valueOf(binding.get("side")));
		verifyNativeProjection(events, originals, epochs(roster), window, pvp);
		verifyEffectSides(originals, roster);
	}

	private static void verifyEffectSides(AuditRows originals, Map<Integer, Participant> roster)
	{
		for (var damage : originals.damage().values())
		{
			require(roster.get(damage.actor()).side() != roster.get(damage.target()).side(), "DAMAGE_EFFECT_SIDE");
		}
		for (var heal : originals.heals().values())
		{
			final var cast = heal.original().cast();
			require(roster.get(cast.actorObjectId()).side() == roster.get(cast.targetObjectId()).side(), "HEAL_EFFECT_SIDE");
		}
		for (var recharge : originals.recharges().values())
		{
			final var cast = recharge.original().cast();
			require(roster.get(cast.actorObjectId()).side() == roster.get(cast.targetObjectId()).side(), "RECHARGE_EFFECT_SIDE");
		}
		for (var aggro : originals.aggro().values())
		{
			final var side = roster.get(aggro.cast().actor()).side();
			for (var change : aggro.effects().values())
			{
				require(roster.get(change.formerAlly()).side() == side && roster.get(change.target().objectId()).side() != side, "AGGRO_EFFECT_SIDE");
			}
		}
		for (var fact : originals.escapes().values())
		{
			final var original = fact.original();
			final var side = roster.get(original.cast().casterObjectId()).side();
			final var team = roster.entrySet().stream().filter(member -> member.getValue().side() == side).map(Map.Entry::getKey).toList();
			require(team.containsAll(original.approvedRecipients().keySet())
				&& (original.cast().skillId() != 1255 || team.size() == original.approvedRecipients().size()), "ESCAPE_EFFECT_SIDE_OR_PARTY");
		}
	}

	/** A raw event may affect a bank result only when this schema can prove its native origin. */
	static void verifyNativeProjection(List<String[]> events, AuditRows originals, Map<Integer, Long> epochs, EpisodeWindow window, boolean pvp)
	{
		final var damage = new HashSet<String>();
		final var healing = new HashSet<String>();
		final var recharging = new HashSet<String>();
		originals.damage().values().forEach(fact -> damage.add(fact.operationId()));
		originals.heals().values().forEach(fact -> healing.add(fact.operationId()));
		originals.recharges().values().forEach(fact -> recharging.add(fact.operationId()));
		for (String[] row : events)
		{
			require(row.length >= 7 && (switch (row[3])
			{
				case "PAY" -> true;
				case "DECISION", "ADMITTED", "EXECUTED" -> damage.contains(row[6]) || healing.contains(row[6]) || recharging.contains(row[6]);
				case "DAMAGE", "DEATH", "KILL_CREDIT" -> damage.contains(row[6]);
				case "HEAL" -> healing.contains(row[6]);
				case "MP_TRANSFER" -> recharging.contains(row[6]);
				case "AGGRO" -> originals.aggro().containsKey(row[6]);
				case "ESCAPE_ARRIVAL" -> originals.escapes().containsKey(row[6]);
				default -> false;
			}), "UNPROVEN_NATIVE_EVENT");
		}
		verifyPaidProjection(events, originals.damage(), epochs, window, pvp);
		verifyHealProjection(events, originals.heals(), epochs, window);
		verifyRechargeProjection(events, originals.recharges(), epochs, window);
		verifyAggroProjection(events, originals.aggro(), epochs, window, pvp);
		verifyEscapeProjection(events, originals.escapes(), epochs, originals.items().openedNanos(), window, pvp);
		verifyPaymentProjection(events, originals.items(), epochs, window);
	}

	private static void verifyEscapeProjection(List<String[]> events, Map<String, EscapeFact> originals, Map<Integer, Long> roster,
		long opened, EpisodeWindow window, boolean pvp)
	{
		require(pvp || originals.isEmpty(), "PVE_ESCAPE_ORIGINAL_NOT_SUPPORTED");
		int expectedEffects = 0;
		for (var fact : originals.values())
		{
			final var original = fact.original();
			require(original.cast().startedNanos() >= Math.max(opened, window.started()) && original.cast().startedNanos() < window.combatEnd()
				&& original.cast().completedNanos() <= window.settledEnd()
				&& original.approvedRecipients().entrySet().stream().allMatch(member -> member.getValue().equals(roster.get(member.getKey()))),
				"ESCAPE_EPOCH_OR_WINDOW");
			expectedEffects += fact.events().size();
		}
		final var used = new HashSet<String>();
		for (String[] event : events)
		{
			if (!event[3].equals("ESCAPE_ARRIVAL")) { continue; }
			final var original = originals.get(event[6]);
			require(original != null && String.join("\t", event).equals(original.events().get(event[1])) && used.add(event[1]), "ESCAPE_WITHOUT_NATIVE_ARRIVAL");
		}
		require(used.size() == expectedEffects, "INCOMPLETE_ESCAPE_PROJECTION");
	}

	private static void verifyAggroProjection(List<String[]> events, Map<String, AggroFact> originals, Map<Integer, Long> roster, EpisodeWindow window, boolean pvp)
	{
		require(!pvp || originals.isEmpty(), "PVP_NPC_AGGRO");
		int expectedEffects = 0;
		for (var fact : originals.values())
		{
			final var cast = fact.cast();
			require(roster.getOrDefault(cast.actor(), 0L) == cast.epoch() && cast.hitStartedNanos() >= window.started(), "AGGRO_CASTER_OR_WINDOW");
			for (var change : fact.effects().values())
			{
				require(roster.getOrDefault(change.formerAlly(), 0L) == change.formerEpoch()
					&& roster.getOrDefault(change.target().objectId(), 0L) == change.target().spawnGeneration()
					&& change.observedNanos() <= window.combatEnd(), "AGGRO_RECIPIENT_OR_WINDOW");
			}
			expectedEffects += fact.effects().size();
		}
		final var used = new HashSet<String>();
		for (String[] event : events)
		{
			if (!event[3].equals("AGGRO")) { continue; }
			length(event, 8);
			final var fact = originals.get(event[6]);
			final var change = fact == null ? null : fact.effects().get(event[1]);
			require(change != null && object(event[4]) == fact.cast().actor() && object(event[5]) == change.target().objectId()
				&& positive(event[2]) == change.observedNanos() && event[7].equals("1") && used.add(event[1]), "AGGRO_WITHOUT_MATCHING_NATIVE_SWITCH");
		}
		require(used.size() == expectedEffects, "INCOMPLETE_AGGRO_PROJECTION");
	}

	private static void verifyHealProjection(List<String[]> events, Map<String, HealFact> originals, Map<Integer, Long> roster, EpisodeWindow window)
	{
		final var operations = new HashMap<String, HealFact>();
		final var stages = new HashMap<String, Integer>();
		for (var fact : originals.values())
		{
			final var nativeHeal = fact.original();
			final var cast = nativeHeal.cast();
			require(operations.putIfAbsent(fact.operationId(), fact) == null
				&& roster.getOrDefault(cast.actorObjectId(), 0L) == cast.actorEpoch()
				&& roster.getOrDefault(cast.targetObjectId(), 0L) == cast.targetEpoch(), "HEAL_PARTICIPANT_EPOCH");
			require(nativeHeal.observation().capturedNanos() >= window.started() && healEffectNanos(fact) <= window.combatEnd()
				&& nativeHeal.mpFinishedNanos() <= window.settledEnd(), "HEAL_ORIGIN_OR_NATIVE_WINDOW");
		}
		final var used = new HashSet<String>();
		for (String[] event : events)
		{
			final var fact = operations.get(event[6]);
			if (fact == null) { continue; }
			final var cast = fact.original().cast();
			if (event[3].equals("HEAL"))
			{
				length(event, 9);
				require(stages.getOrDefault(event[6], 0) == 3 && event[1].equals(cast.eventKey()) && object(event[4]) == cast.actorObjectId()
					&& object(event[5]) == cast.targetObjectId() && positive(event[2]) == healEffectNanos(fact)
					&& decimal(event[7], true).compareTo(fact.effective()) == 0 && decimal(event[8], true).compareTo(fact.overheal()) == 0
					&& used.add(event[1]), "HEAL_WITHOUT_MATCHING_PAID_ORIGINAL");
			}
			else
			{
				final int stage = verifyPaidCastStage(event, cast, fact.operationId(), "HEAL");
				require(stages.getOrDefault(event[6], 0) == stage - 1, "HEAL_CAST_STAGE_ORDER");
				stages.put(event[6], stage);
			}
		}
		require(used.size() == originals.size(), "INCOMPLETE_HEAL_PROJECTION");
	}

	private static long healEffectNanos(HealFact fact)
	{
		final var original = fact.original();
		return original.hpWriteNanos() > 0 ? original.hpWriteNanos() : original.offeredNanos();
	}

	private static void verifyRechargeProjection(List<String[]> events, Map<String, RechargeFact> originals, Map<Integer, Long> roster, EpisodeWindow window)
	{
		final var operations = new HashMap<String, RechargeFact>();
		final var stages = new HashMap<String, Integer>();
		for (var fact : originals.values())
		{
			final var original = fact.original();
			final var cast = original.cast();
			require(operations.putIfAbsent(fact.operationId(), fact) == null && roster.getOrDefault(cast.actorObjectId(), 0L) == cast.actorEpoch()
				&& roster.getOrDefault(cast.targetObjectId(), 0L) == cast.targetEpoch(), "RECHARGE_PARTICIPANT_EPOCH");
			require(original.observation().capturedNanos() >= window.started() && fact.effectNanos() <= window.combatEnd()
				&& original.handlerFinishedNanos() <= window.settledEnd() && original.mpFinishedNanos() <= window.settledEnd(), "RECHARGE_NATIVE_WINDOW");
		}
		final var used = new HashSet<String>();
		for (String[] event : events)
		{
			final var fact = operations.get(event[6]);
			if (fact == null) { continue; }
			final var cast = fact.original().cast();
			if (event[3].equals("MP_TRANSFER"))
			{
				length(event, 8);
				require(stages.getOrDefault(event[6], 0) == 3 && event[1].equals(cast.eventKey()) && object(event[4]) == cast.actorObjectId()
					&& object(event[5]) == cast.targetObjectId() && positive(event[2]) == fact.effectNanos()
					&& decimal(event[7], true).compareTo(fact.effective()) == 0 && used.add(event[1]), "TRANSFER_WITHOUT_MATCHING_PAID_ORIGINAL");
			}
			else
			{
				final int stage = verifyPaidCastStage(event, cast, fact.operationId(), "SUPPORT");
				require(stages.getOrDefault(event[6], 0) == stage - 1, "RECHARGE_CAST_STAGE_ORDER");
				stages.put(event[6], stage);
			}
		}
		require(used.size() == originals.size(), "INCOMPLETE_RECHARGE_PROJECTION");
	}

	private static Map<Integer, Long> epochs(Map<Integer, Participant> roster)
	{
		final var epochs = new HashMap<Integer, Long>();
		roster.forEach((object, member) -> epochs.put(object, member.epoch()));
		return epochs;
	}

	/** A priced item write remains a cost even when no later cast or attack succeeded. */
	static void verifyPaymentProjection(List<String[]> events, ItemLedger ledger, Map<Integer, Long> roster, EpisodeWindow window)
	{
		require(ledger.openedNanos() >= window.started() && ledger.openedNanos() <= window.combatEnd()
			&& ledger.closedNanos() >= window.combatEnd() && ledger.closedNanos() <= window.settledEnd(), "ITEM_OBSERVATION_WINDOW");
		final var used = new HashSet<String>();
		for (String[] row : events)
		{
			require(row.length >= 7 && row[0].equals("event") && positive(row[2]) >= ledger.openedNanos(), "EVENT_BEFORE_ITEM_OBSERVATION");
			if (!row[3].equals("PAY"))
			{
				require(!ledger.debits().containsKey(row[6]), "ITEM_OPERATION_REUSED");
				continue;
			}
			length(row, 9);
			final var debit = ledger.debits().get(row[1]);
			require(debit != null && row[6].equals(row[1]) && object(row[4]) == debit.actorObjectId() && row[5].equals(row[4])
				&& roster.getOrDefault(debit.actorObjectId(), 0L) == debit.actorEpoch() && object(row[7]) == debit.itemId()
				&& positive(row[2]) == debit.observedNanos() && debit.observedNanos() <= window.combatEnd()
				&& positive(row[8]) == debit.before() - debit.after() && used.add(row[1]), "ITEM_DEBIT_PROJECTION");
		}
		require(used.size() == ledger.count(), "INCOMPLETE_ITEM_DEBIT_PROJECTION");
	}

	/** Reconstruct native cast stages, damage and deaths from separately sealed paid originals. */
	static void verifyPaidProjection(List<String[]> events, Map<String, Fact> originals, Map<Integer, Long> roster,
		EpisodeWindow window, boolean pvp)
	{
		final var paidOperations = new HashMap<String, Fact>();
		final var castStages = new HashMap<String, Integer>();
		final var deaths = new HashMap<String, Fact>();
		final var credits = new HashMap<String, Fact>();
		final var usedDamage = new HashSet<String>();
		final var usedDeaths = new HashSet<String>();
		final var recipients = new HashSet<Subject>();
		for (var fact : originals.values())
		{
			final var trace = fact.trace();
			require(trace != null && trace.decisionNanos() >= fact.capturedNanos() && trace.decisionNanos() <= trace.admittedNanos()
				&& trace.admittedNanos() <= trace.executedNanos() && trace.executedNanos() <= fact.atNanos()
				&& paidOperations.putIfAbsent(fact.operationId(), fact) == null, "PAID_CAST_ORIGINAL");
			require(roster.getOrDefault(fact.actor(), 0L) == fact.actorEpoch() && roster.getOrDefault(fact.target(), 0L) == fact.targetEpoch(),
				"PAID_PARTICIPANT_EPOCH");
			require(fact.pvp() == pvp && fact.capturedNanos() >= window.started() && fact.atNanos() <= window.combatEnd()
				&& fact.lethalNanos() <= window.combatEnd() && fact.lastNativeWriteNanos() <= window.settledEnd()
				&& fact.lastWitnessNanos() <= window.settledEnd() && fact.killCreditNanos() <= window.settledEnd(), "PAID_ORIGIN_OR_NATIVE_WINDOW");
			if (fact.lethalNanos() > 0) { require(deaths.putIfAbsent(fact.lethalKey(), fact) == null, "DUPLICATE_LETHAL_ORIGINAL"); }
			if (fact.killCreditNanos() > 0) { require(credits.putIfAbsent(fact.killCreditKey(), fact) == null, "DUPLICATE_KILL_CREDIT_ORIGINAL"); }
		}
		for (String[] event : events)
		{
			require(event.length >= 7 && "event".equals(event[0]), "PAID_EVENT_SHAPE");
			switch (event[3])
			{
				case "DECISION", "ADMITTED", "EXECUTED" ->
				{
					final var fact = paidOperations.get(event[6]);
					if (fact != null)
					{
						final int stage = verifyPaidCastStage(event, fact.trace(), fact.operationId(), null);
						require(castStages.getOrDefault(event[6], 0) == stage - 1, "PAID_CAST_STAGE_ORDER");
						castStages.put(event[6], stage);
					}
				}
				case "DAMAGE" ->
				{
					length(event, 9);
					final var fact = originals.get(event[1]);
					require(fact != null && castStages.getOrDefault(event[6], 0) == 3 && samePaidEndpoints(event, fact)
						&& positive(event[2]) == fact.atNanos() && decimal(event[7], true).compareTo(fact.hp()) == 0
						&& decimal(event[8], true).compareTo(fact.cp()) == 0, "DAMAGE_WITHOUT_MATCHING_PAID_ORIGINAL");
					require(usedDamage.add(fact.eventKey()) && recipients.add(new Subject(fact.operationId(), fact.target())), "DUPLICATE_PAID_DAMAGE");
				}
				case "DEATH" ->
				{
					length(event, 7);
					final var lethal = deaths.remove(event[1]);
					require(lethal != null && samePaidEndpoints(event, lethal) && usedDamage.contains(lethal.eventKey())
						&& positive(event[2]) == lethal.lethalNanos(), "DEATH_WITHOUT_MATCHING_PAID_ORIGINAL");
					usedDeaths.add(event[1]);
				}
				case "KILL_CREDIT" ->
				{
					length(event, 7);
					final var credit = credits.remove(event[1]);
					require(credit != null && samePaidEndpoints(event, credit) && usedDeaths.contains(credit.lethalKey())
						&& positive(event[2]) == credit.killCreditNanos(), "KILL_CREDIT_WITHOUT_MATCHING_PAID_ORIGINAL");
				}
			}
		}
		require(usedDamage.size() == originals.size() && deaths.isEmpty() && credits.isEmpty(), "INCOMPLETE_PAID_PROJECTION");
	}

	private static int verifyPaidCastStage(String[] event, NativeCastTrace trace, String operationId, String supportRole)
	{
		final String kind = event[3];
		final String skill = PhantomLearning131AuditOriginals.skillToken(trace.skillId(), trace.skillLevel());
		require(event[6].equals(operationId) && object(event[4]) == trace.actorObjectId() && object(event[5]) == trace.targetObjectId()
			&& event[1].equals(PhantomLearning131AuditOriginals.castStageKey(trace.eventKey(), kind)),
			"PAID_CAST_STAGE_BINDING");
		switch (kind)
		{
			case "DECISION":
				length(event, 10);
				require(positive(event[2]) == trace.decisionNanos() && (supportRole == null ? event[7].equals("DAMAGE") || event[7].equals("OPPONENT") : event[7].equals(supportRole))
					&& event[8].equals(skill) && event[9].equals(PhantomLearning131AuditOriginals.skillToken(trace.baselineSkillId(), trace.baselineSkillLevel())),
					"PAID_CAST_DECISION");
				return 1;
			case "ADMITTED":
				length(event, 8);
				require(positive(event[2]) == trace.admittedNanos() && event[7].equals(skill), "PAID_CAST_ADMISSION");
				return 2;
			case "EXECUTED":
				length(event, 8);
				require(positive(event[2]) == trace.executedNanos() && event[7].equals(skill), "PAID_CAST_EXECUTION");
				return 3;
			default:
				throw new IllegalArgumentException("PAID_CAST_STAGE_KIND");
		}
	}

	private static boolean samePaidEndpoints(String[] event, Fact fact)
	{
		return event[6].equals(fact.operationId()) && object(event[4]) == fact.actor() && object(event[5]) == fact.target();
	}

	private static Binding bind(Path directory, Document manifest, Occurrence occurrence, Document seal) throws IOException
	{
		final Document binding = readBinding(directory.resolve("binding131.tsv"));
		final Block block = occurrence.block();
		final Slot slot = block.plan().order().get(occurrence.position());
		final String model = switch (slot.arm())
		{
			case C -> manifest.get("candidateSha");
			case R -> manifest.get("referenceSha");
			case H, S -> "NONE";
		};
		require(seal.rows().isEmpty() && binding.sha().equals(seal.get("bindingSha")) && binding.get("manifestSha").equals(manifest.sha())
			&& binding.get("bootId").equals(occurrence.boot()) && binding.get("blockId").equals(block.plan().id()) && binding.get("episodeId").equals(occurrence.id())
			&& binding.get("attempt").equals(Integer.toString(occurrence.attempt())) && binding.get("position").equals(Integer.toString(occurrence.position()))
			&& binding.get("arm").equals(slot.arm().name()) && binding.get("side").equals(slot.side().name()) && binding.get("sceneId").equals(block.sceneId())
			&& binding.get("fixtureSha").equals(block.fixtureSha()) && binding.get("opponentSha").equals(manifest.get("opponent." + block.opponent()))
			&& binding.get("policySha").equals(manifest.get("policySha")) && binding.get("modelSha").equals(model), "NATIVE_EPISODE_BINDING");
		final var participants = participants(binding, slot.side());
		final long scored = participants.values().stream().filter(a -> a.side() == slot.side()).count();
		require(scored == block.plan().scene().initialRosterSize() && participants.size() - scored == block.opponentSize(), "BOUND_ROSTER");
		return new Binding(binding, slot, Long.parseLong(binding.get("startNanos")), participants);
	}

	private static Document readBinding(Path path) throws IOException
	{
		return document(path, "NATIVE_BINDING131_V1", "schema", "manifestSha", "bootId", "blockId", "episodeId",
			"attempt", "position", "arm", "side", "sceneId", "fixtureSha", "opponentSha", "policySha", "modelSha", "startNanos");
	}

	private static Map<Integer, Participant> participants(Document binding, Side side)
	{
		final var participants = new HashMap<Integer, Participant>();
		for (String[] row : binding.rows())
		{
			fields(row, "actor", 5);
			final var actor = new Participant(positive(row[2]), Side.valueOf(row[3]), Role.valueOf(row[4]));
			require(participants.putIfAbsent(object(row[1]), actor) == null && (actor.side() != side || actor.role() != Role.OPPONENT), "BOUND_ACTOR");
		}
		return participants;
	}

	/** Check the pre-action binding of the one native source/target this damage observer can witness. */
	static SoloDamageBinding soloDamageBinding(ScheduledArm scheduled, Path path, String expectedSha, int actorId, int targetId) throws IOException
	{
		require(scheduled.block().stratum() == Stratum.SOLO_PVE && actorId != targetId, "SOLO_DAMAGE_BINDING");
		final Document binding = scheduledBinding(scheduled, path, expectedSha, "SOLO_DAMAGE");
		final Slot slot = scheduled.slot();
		final var roster = participants(binding, slot.side());
		final Participant actor = roster.get(actorId), target = roster.get(targetId);
		require(roster.size() == 1 + scheduled.opponentSize() && roster.values().stream().filter(value -> value.side() == slot.side()).count() == 1
			&& actor != null && actor.side() == slot.side() && actor.role() == Role.DAMAGE
			&& target != null && target.side() != slot.side() && target.role() == Role.OPPONENT, "SOLO_DAMAGE_ROSTER");
		return new SoloDamageBinding(token(binding.get("episodeId")), binding.sha(), positive(binding.get("startNanos")), actor.epoch(), target.epoch());
	}

	static DuelDamageBinding duelDamageBinding(ScheduledArm scheduled, Path path, String expectedSha, int scoredId, int opponentId) throws IOException
	{
		require(scheduled.block().stratum() == Stratum.DUEL_PVP && scheduled.block().scene().initialRosterSize() == 1
			&& scheduled.opponentSize() == 1 && scoredId != opponentId, "DUEL_DAMAGE_BINDING");
		final Document binding = scheduledBinding(scheduled, path, expectedSha, "DUEL_DAMAGE");
		final Slot slot = scheduled.slot();
		final var roster = participants(binding, slot.side());
		final Participant scored = roster.get(scoredId), opponent = roster.get(opponentId);
		require(roster.size() == 2 && scored != null && scored.side() == slot.side() && scored.role() == Role.DAMAGE
			&& opponent != null && opponent.side() != slot.side(), "DUEL_DAMAGE_ROSTER");
		return new DuelDamageBinding(token(binding.get("episodeId")), binding.sha(), positive(binding.get("startNanos")), scored.epoch(), opponent.epoch());
	}

	static HealBinding healBinding(ScheduledArm scheduled, Path path, String expectedSha) throws IOException
	{
		final Stratum stratum = scheduled.block().stratum();
		require(stratum == Stratum.PARTY_PVE || stratum == Stratum.GROUP_PVP, "HEAL_GROUP_STRATUM");
		final Document binding = scheduledBinding(scheduled, path, expectedSha, "HEAL_GROUP");
		final Side side = scheduled.slot().side();
		final var roster = participants(binding, side);
		final long scored = roster.values().stream().filter(member -> member.side() == side).count();
		require(scored == scheduled.block().scene().initialRosterSize() && roster.size() - scored == scheduled.opponentSize(), "HEAL_GROUP_ROSTER");
		final var players = new HashMap<Integer, Long>();
		final var scoredPlayers = new HashSet<Integer>();
		final var npcs = new HashMap<Integer, Long>();
		roster.forEach((object, member) ->
		{
			if (member.side() == side) { scoredPlayers.add(object); }
			if (stratum == Stratum.GROUP_PVP || member.side() == side) { players.put(object, member.epoch()); }
			else { npcs.put(object, member.epoch()); }
		});
		return new HealBinding(token(binding.get("episodeId")), binding.sha(), positive(binding.get("startNanos")), Map.copyOf(players), Set.copyOf(scoredPlayers), Map.copyOf(npcs));
	}

	static PolicyArmBinding policyArmBinding(ScheduledArm scheduled, Path path, String expectedSha) throws IOException
	{
		final Slot slot = scheduled.slot();
		final var binding = scheduledBinding(scheduled, path, expectedSha, "MODEL_ARM");
		final var roster = participants(binding, slot.side());
		final var scored = new HashMap<Integer, Long>();
		roster.forEach((actor, member) -> { if (member.side() == slot.side()) { scored.put(actor, member.epoch()); } });
		require(scored.size() == scheduled.block().scene().initialRosterSize() && roster.size() - scored.size() == scheduled.opponentSize(), "AUDIT_POLICY_ARM_ROSTER");
		return new PolicyArmBinding(token(binding.get("episodeId")), binding.sha(), positive(binding.get("startNanos")), binding.get("modelSha"), Map.copyOf(scored));
	}

	static AuditControl stockControl(Stratum stratum)
	{
		return switch (stratum)
		{
			case SOLO_PVE -> AuditControl.STOCK_SOLO;
			case PARTY_PVE -> AuditControl.STOCK_PVE;
			case DUEL_PVP, GROUP_PVP -> AuditControl.STOCK_PVP;
		};
	}

	private static Document scheduledBinding(ScheduledArm scheduled, Path path, String expectedSha, String name) throws IOException
	{
		final Document binding = readBinding(path);
		final Slot slot = scheduled.slot();
		require(binding.sha().equals(digest(expectedSha))
			&& binding.get("blockId").equals(scheduled.block().id()) && binding.get("sceneId").equals(scheduled.sceneId())
			&& binding.get("fixtureSha").equals(scheduled.fixtureSha()) && binding.get("position").equals(Integer.toString(scheduled.position()))
			&& binding.get("arm").equals(slot.arm().name()) && binding.get("side").equals(slot.side().name())
			&& Set.of("0", "1").contains(binding.get("attempt")), name + "_BINDING");
		for (String key : List.of("manifestSha", "opponentSha", "policySha")) { digest(binding.get(key)); }
		token(binding.get("bootId"));
		if (slot.arm() == Arm.C || slot.arm() == Arm.R) { digest(binding.get("modelSha")); }
		else { require(binding.get("modelSha").equals("NONE"), name + "_MODEL"); }
		return binding;
	}

	private static final class Raw
	{
		private final Binding _binding;
		private final Block _block;
		private final Map<Integer, BigDecimal> _prices;
		private final Map<String, Action> _actions = new HashMap<>();
		private final Map<Control, ActiveControl> _control = new HashMap<>();
		private final Map<Integer, Integer> _scoredControl = new HashMap<>();
		private final Set<Subject> _damageEvents = new HashSet<>();
		private final Set<String> _unknownActions = new HashSet<>();
		private final Map<Integer, Long> _deaths = new HashMap<>();
		private final Set<Integer> _creditedMobs = new HashSet<>();
		private final Map<Integer, Long> _escaped = new HashMap<>();
		private final Set<String> _escapeCasts = new HashSet<>();
		private final Map<Role, Set<String>> _samples = new EnumMap<>(Role.class);
		private final Map<Role, Integer> _unknown = new EnumMap<>(Role.class);
		private BigDecimal _paid = BigDecimal.ZERO;
		private BigDecimal _damageHp = BigDecimal.ZERO;
		private BigDecimal _damageCp = BigDecimal.ZERO;
		private BigDecimal _receivedHp = BigDecimal.ZERO;
		private BigDecimal _receivedCp = BigDecimal.ZERO;
		private BigDecimal _heal = BigDecimal.ZERO;
		private BigDecimal _overheal = BigDecimal.ZERO;
		private BigDecimal _mpTransfer = BigDecimal.ZERO;
		private long _lastEvent;
		private long _objectiveAt = -1;
		private long _controlNanos;
		private int _interrupts;
		private int _brokenControl;
		private int _aggro;
		private int _buffs;
		private int _executed;
		private int _opponentExecuted;
		private int _opponentEffects;
		private final EpisodeWindow _window;

		Raw(Binding binding, Block block, Map<Integer, BigDecimal> prices, EpisodeWindow window)
		{
			_binding = binding;
			_block = block;
			_prices = prices;
			_window = window;
		}

		private void accept(String[] row, Set<String> eventKeys)
		{
			require(row.length >= 7 && row[0].equals("event") && eventKeys.add(digest(row[1])), "DUPLICATE_OR_INVALID_EVENT");
			final long offset = _window.offset(row[3], Long.parseLong(row[2]));
			require(offset >= _lastEvent, "NATIVE_TIME_ORDER");
			final long controlElapsed = Math.min(offset, _window.combatElapsed()) - Math.min(_lastEvent, _window.combatElapsed());
			_controlNanos = Math.addExact(_controlNanos, Math.multiplyExact(controlElapsed, _scoredControl.size()));
			_lastEvent = offset;
			final int source = object(row[4]);
			final int target = object(row[5]);
			final boolean friendly = scored(source);
			final boolean friendlyTarget = scored(target);
			final String operation = token(row[6]);
			final Action action = _actions.get(operation);
			switch (row[3])
			{
				case "DECISION" ->
				{
					length(row, 10);
					final Role role = Role.valueOf(row[7]);
					final String selected = token(row[8]);
					final String stock = token(row[9]);
					require(action == null && (!friendly || role != Role.OPPONENT) && (!friendly || _binding.slot().arm() != Arm.S || selected.equals(stock)), "DECISION_OR_STOCK_POLICY");
					_actions.put(operation, new Action(source, target, role, selected, 0));
				}
				case "ADMITTED", "EXECUTED" ->
				{
					length(row, 8);
					final int prior = row[3].equals("ADMITTED") ? 0 : 1;
					require(action != null && action.stage() == prior && action.source() == source && action.target() == target && action.selected().equals(row[7]), "EXECUTION_BINDING");
					_actions.put(operation, new Action(source, target, action.role(), action.selected(), prior + 1));
					if (prior == 1)
					{
						if (friendly) { _executed++; } else { _opponentExecuted++; }
					}
				}
				case "UNKNOWN" ->
				{
					length(row, 8);
					final Role role = Role.valueOf(row[7]);
					require(action != null && action.source() == source && role == action.role() && _unknownActions.add(operation), "UNKNOWN_ACTION_BINDING");
					if (friendly) { _unknown.merge(role, 1, Integer::sum); }
				}
				case "VIOLATION" -> throw new IllegalArgumentException("EVAL131_NATIVE_SAFETY_VIOLATION");
				case "PAY" ->
				{
					length(row, 9);
					final BigDecimal price = _prices.get(object(row[7]));
					require(price != null && source == target, "PAID_RESOURCE_PRICE");
					if (friendly) { _paid = _paid.add(price.multiply(BigDecimal.valueOf(positive(row[8])))); }
				}
				case "AGGRO" ->
				{
					length(row, 8);
					require(friendly && !friendlyTarget && _block.plan().scene().objective() == Objective.PVE_KILLS, "NATIVE_NPC_AGGRO_REQUIRED");
					_aggro++;
					_samples.computeIfAbsent(Role.TANK, _ -> new HashSet<>()).add(operation);
				}
				case "ESCAPE_ARRIVAL" ->
				{
					length(row, 7);
					if (offset > _window.combatElapsed()) { return; }
					require(friendly == friendlyTarget && !_deaths.containsKey(target) && _escaped.putIfAbsent(target, offset) == null, "NATIVE_ESCAPE_ARRIVAL");
					if (_escapeCasts.add(operation))
					{
						if (friendly) { _executed++; }
						else { _opponentExecuted++; }
					}
					if (friendly && _block.plan().scene().objective() == Objective.ESCAPE && scoredCount(_escaped.keySet()) == _block.plan().scene().requiredProgress())
					{
						_objectiveAt = offset;
					}
				}
				default ->
				{
					require(action != null && action.stage() == 2 && action.source() == source, "EFFECT_WITHOUT_EXECUTION");
					if (effect(row, source, target, friendly, friendlyTarget, offset, action))
					{
						_samples.computeIfAbsent(action.role(), _ -> new HashSet<>()).add(operation);
					}
				}
			}
		}

		private boolean effect(String[] row, int source, int target, boolean friendly, boolean friendlyTarget, long offset, Action action)
		{
			final Role role = action.role();
			final String operation = row[6];
			switch (row[3])
			{
				case "DAMAGE" ->
				{
					length(row, 9);
					final BigDecimal hp = decimal(row[7], true);
					final BigDecimal cp = decimal(row[8], true);
					require(friendly != friendlyTarget && target == action.target() && hp.signum() + cp.signum() > 0
						&& _damageEvents.add(new Subject(operation, target)), "DAMAGE_EFFECT");
					if (friendly) { _damageHp = _damageHp.add(hp); _damageCp = _damageCp.add(cp); }
					else { _receivedHp = _receivedHp.add(hp); _receivedCp = _receivedCp.add(cp); _opponentEffects++; }
					return friendly && role == Role.DAMAGE;
				}
				case "HEAL" ->
				{
					length(row, 9);
					final BigDecimal effective = decimal(row[7], true);
					final BigDecimal overheal = decimal(row[8], true);
					require(friendly == friendlyTarget && effective.signum() + overheal.signum() > 0, "HEAL_EFFECT");
					if (friendly) { _heal = _heal.add(effective); _overheal = _overheal.add(overheal); }
					else if (effective.signum() > 0) { _opponentEffects++; }
					return friendly && role == Role.HEAL && effective.signum() > 0;
				}
				case "MP_TRANSFER" ->
				{
					length(row, 8);
					final BigDecimal mp = decimal(row[7], true);
					require(friendly == friendlyTarget && source != target, "MP_TRANSFER_EFFECT");
					if (friendly) { _mpTransfer = _mpTransfer.add(mp); }
					else if (mp.signum() > 0) { _opponentEffects++; }
					return friendly && role == Role.SUPPORT && mp.signum() > 0;
				}
				case "BUFF" ->
				{
					length(row, 8);
					token(row[7]);
					require(friendly == friendlyTarget, "BUFF_EFFECT");
					if (friendly) { _buffs++; }
					else { _opponentEffects++; }
					return friendly && role == Role.SUPPORT;
				}
				case "CC_START", "CC_END", "CC_BROKEN", "INTERRUPT" ->
				{
					length(row, 8);
					require(friendly != friendlyTarget, "CONTROL_EFFECT");
					final var control = new Control(target, token(row[7]));
					switch (row[3])
					{
						case "CC_START" ->
						{
							require(_control.putIfAbsent(control, new ActiveControl(offset, operation, friendly)) == null, "DUPLICATE_CONTROL_START");
							if (friendly) { _scoredControl.merge(target, 1, Integer::sum); }
							return false;
						}
						case "CC_END", "CC_BROKEN" ->
						{
							final ActiveControl active = _control.remove(control);
							require(active != null && active.scored() == friendly, "UNBOUND_CONTROL_END");
							if (friendly)
							{
								_scoredControl.compute(target, (_, count) -> count == 1 ? null : count - 1);
								if (row[3].equals("CC_BROKEN")) { _brokenControl++; }
								if (Math.min(offset, _window.combatElapsed()) > active.started() && _actions.get(active.operation()).role() == Role.CONTROL)
								{
									_samples.computeIfAbsent(Role.CONTROL, _ -> new HashSet<>()).add(active.operation());
								}
							}
							else if (Math.min(offset, _window.combatElapsed()) > active.started()) { _opponentEffects++; }
							return false;
						}
						case "INTERRUPT" -> { if (friendly) { _interrupts++; } else { _opponentEffects++; } }
					}
					return friendly && role == Role.CONTROL;
				}
				case "DEATH" ->
				{
					length(row, 7);
					require(friendly != friendlyTarget && _damageEvents.contains(new Subject(operation, target)) && !_escaped.containsKey(target)
						&& _deaths.putIfAbsent(target, offset) == null, "DUPLICATE_OR_ILLEGAL_DEATH");
				}
				case "KILL_CREDIT" ->
				{
					length(row, 7);
					require(_block.plan().scene().objective() == Objective.PVE_KILLS && friendly && !friendlyTarget && _deaths.containsKey(target)
						&& _creditedMobs.add(target), "NATIVE_KILL_CREDIT");
				}
				default -> throw new IllegalArgumentException("EVAL131_UNSUPPORTED_NATIVE_EVENT");
			}
			return false;
		}

		private boolean scored(int actor)
		{
			final Participant participant = _binding.participants().get(actor);
			require(participant != null, "UNBOUND_EFFECT_ACTOR");
			return participant.side() == _binding.slot().side();
		}

		private int scoredCount(Set<Integer> actors)
		{
			return Math.toIntExact(actors.stream().filter(this::scored).count());
		}

		private Episode finish(Occurrence occurrence)
		{
			require(_control.isEmpty() && _paid.compareTo(_block.budget()) <= 0, "DRAIN_CONTROL_OR_BUDGET");
			if (_executed == 0) { throw new Incomplete("NO_NATIVE_EXECUTION"); }
			final Scene scene = _block.plan().scene();
			final int deaths = scoredCount(_deaths.keySet());
			final int escaped = scoredCount(_escaped.keySet());
			final boolean ownOut = deaths + escaped == scene.initialRosterSize();
			final boolean opponentOut = _deaths.size() - deaths + _escaped.size() - escaped == _block.opponentSize();
			final int progress = switch (scene.objective())
			{
				case PVE_KILLS -> _creditedMobs.size();
				case PVP_WIN -> 0;
				case ESCAPE -> Math.min(escaped, scene.requiredProgress());
			};
			final Outcome outcome = switch (scene.objective())
			{
				case PVE_KILLS -> progress >= scene.requiredProgress() ? Outcome.WIN : ownOut ? Outcome.LOSS : Outcome.TIMEOUT;
				case PVP_WIN -> ownOut && opponentOut ? Outcome.DRAW : ownOut ? Outcome.LOSS : opponentOut ? Outcome.WIN : Outcome.TIMEOUT;
				case ESCAPE -> progress == scene.requiredProgress() ? Outcome.ESCAPED : ownOut ? Outcome.LOSS : opponentOut ? Outcome.WIN : Outcome.TIMEOUT;
			};
			final long elapsed = _window.combatElapsed();
			require(outcome == Outcome.TIMEOUT ? elapsed >= _block.deadline() : elapsed <= _block.deadline(), "TERMINAL_DEADLINE");
			final long objectiveElapsed = objectiveElapsed(scene, outcome);
			return new Episode(_block.plan().id(), occurrence.id(), occurrence.position(), _binding.slot(), outcome, progress, deaths, _paid.doubleValue(), objectiveElapsed);
		}

		private long objectiveElapsed(Scene scene, Outcome outcome)
		{
			if (outcome == Outcome.WIN && scene.objective() == Objective.PVE_KILLS)
			{
				return _creditedMobs.stream().mapToLong(_deaths::get).sorted().skip(scene.requiredProgress() - 1L).findFirst().orElseThrow();
			}
			if (outcome == Outcome.WIN && scene.objective() == Objective.PVP_WIN)
			{
				return java.util.stream.Stream.concat(_deaths.entrySet().stream(), _escaped.entrySet().stream())
					.filter(entry -> !scored(entry.getKey())).mapToLong(Map.Entry::getValue).max().orElseThrow();
			}
			return _objectiveAt >= 0 ? _objectiveAt : _window.combatElapsed();
		}
	}

	private static void length(String[] row, int expected)
	{
		require(row.length == expected, "EVENT_FIELDS");
	}

	private static List<String> report(Audit audit, String originsSha, List<BlockPlan> plans, List<Evaluation> evaluations)
	{
		final var episodes = evaluations.stream().map(Evaluation::episode).toList();
		final var numeric = TrainingEvaluation131.compare(plans, episodes);
		final var result = new ArrayList<String>();
		final var byStratum = new EnumMap<Stratum, Map<Arm, Set<Role>>>(Stratum.class);
		final var required = new EnumMap<Stratum, Set<Role>>(Stratum.class);
		final var opposingActions = new EnumMap<Stratum, Set<Arm>>(Stratum.class);
		for (BlockPlan plan : plans)
		{
			required.computeIfAbsent(plan.stratum(), _ -> EnumSet.noneOf(Role.class)).addAll(audit.blocks().get(plan.id()).requiredRoles());
		}
		for (Evaluation evaluation : evaluations)
		{
			final Episode episode = evaluation.episode();
			final BlockPlan plan = audit.blocks().get(episode.blockId()).plan();
			final Raw raw = evaluation.raw();
			final Arm arm = episode.slot().arm();
			byStratum.computeIfAbsent(plan.stratum(), _ -> new EnumMap<>(Arm.class))
				.computeIfAbsent(arm, _ -> EnumSet.noneOf(Role.class)).addAll(raw._samples.keySet());
			if (raw._opponentEffects > 0)
			{
				opposingActions.computeIfAbsent(plan.stratum(), _ -> EnumSet.noneOf(Arm.class)).add(arm);
			}
		}
		boolean coverage = true;
		final var missingRoles = new ArrayList<String>();
		for (Stratum stratum : Stratum.values())
		{
			for (Arm arm : Arm.values())
			{
				for (Role role : required.get(stratum))
				{
					if (!byStratum.get(stratum).get(arm).contains(role))
					{
						coverage = false;
						missingRoles.add(row("roleCoverageMissing", stratum, arm, role));
					}
				}
				if ((stratum == Stratum.DUEL_PVP || stratum == Stratum.GROUP_PVP) && !opposingActions.getOrDefault(stratum, Set.of()).contains(arm))
				{
					coverage = false;
					missingRoles.add(row("roleCoverageMissing", stratum, arm, Role.OPPONENT));
				}
			}
		}
		result.add(row("meta", "status", !coverage ? "ROLE_COVERAGE_UNPROVEN" : numeric.meetsNumericThresholds() ? "Q_PASS" : "Q_NOT_MET"));
		result.add(row("meta", "bank", audit.manifest().get("bank")));
		result.add(row("meta", "blocks", plans.size()));
		result.add(row("meta", "episodes", episodes.size()));
		result.add(row("meta", "manifestSha", audit.manifest().sha()));
		result.add(row("meta", "originsSha", originsSha));
		for (String key : List.of("protocolSha", "splitSha", "sourceSha", "jarSha", "configSha", "policySha", "evaluatorSha", "candidateSha", "referenceSha"))
		{
			result.add(row("meta", key, audit.manifest().get(key)));
		}
		result.add(row("meta", "confidenceLower", "0.975"));
		result.add(row("meta", "roleCoverage", coverage ? "PASS" : "UNKNOWN"));
		result.addAll(missingRoles);
		for (Arm arm : List.of(Arm.H, Arm.S, Arm.R))
		{
			final var comparison = numeric.comparisons().get(arm);
			result.add(row("comparison", arm, comparison.mean(), comparison.lower()));
			for (Stratum stratum : Stratum.values())
			{
				result.add(row("stratum", arm, stratum, comparison.meansByStratum().get(stratum)));
			}
		}
		result.add(row("columns", "episode", "block", "episodeId", "boot", "arenaSlot", "arm", "side", "outcome", "progress", "deaths", "paidValue",
			"objectiveElapsedNanos", "terminalElapsedNanos", "settledElapsedNanos", "damageHP", "damageCP", "receivedHP", "receivedCP", "effectiveHeal", "overheal", "mpTransfer",
			"controlTargetNanos", "interrupts", "brokenCC", "aggroEffects", "buffEffects", "ownExecuted", "opponentExecuted", "opponentEffects", "score"));
		result.add(row("columns", "role", "episodeId", "role", "samples", "unknown"));
		for (Evaluation evaluation : evaluations)
		{
			final Episode episode = evaluation.episode();
			final Raw raw = evaluation.raw();
			final Scene scene = audit.blocks().get(episode.blockId()).plan().scene();
			result.add(row("episode", episode.blockId(), episode.episodeId(), evaluation.boot(), episode.position(), episode.slot().arm(), episode.slot().side(),
				episode.outcome(), episode.objectiveProgress(), episode.nativeDeaths(), raw._paid.toPlainString(), (long) episode.elapsed(), evaluation.window().combatElapsed(), evaluation.window().settledElapsed(),
				raw._damageHp.toPlainString(), raw._damageCp.toPlainString(), raw._receivedHp.toPlainString(), raw._receivedCp.toPlainString(), raw._heal.toPlainString(),
				raw._overheal.toPlainString(), raw._mpTransfer.toPlainString(), raw._controlNanos, raw._interrupts, raw._brokenControl, raw._aggro, raw._buffs,
				raw._executed, raw._opponentExecuted, raw._opponentEffects, TrainingEvaluation131.score(scene, episode)));
			for (Role role : EnumSet.complementOf(EnumSet.of(Role.OPPONENT)))
			{
				result.add(row("role", episode.episodeId(), role, raw._samples.getOrDefault(role, Set.of()).size(), raw._unknown.getOrDefault(role, 0)));
			}
			for (var actor : raw._binding.participants().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
			{
				result.add(row("actor", episode.episodeId(), actor.getKey(), actor.getValue().epoch(), actor.getValue().side(), actor.getValue().role()));
			}
		}
		return result;
	}

	private static String row(Object... columns)
	{
		final var line = new StringBuilder();
		for (Object column : columns)
		{
			if (!line.isEmpty()) { line.append('\t'); }
			line.append(column);
		}
		return line.toString();
	}

	private static void validateRoster(Stratum stratum, int roster, int opposition, Set<String> groupSizes)
	{
		require(roster <= 9 && opposition <= 128, "FROZEN_ROSTER_BOUND");
		switch (stratum)
		{
			case SOLO_PVE -> require(roster == 1, "SOLO_ROSTER");
			case PARTY_PVE -> require(roster > 1, "PARTY_ROSTER");
			case DUEL_PVP -> require(roster == 1 && opposition == 1, "DUEL_ROSTER");
			case GROUP_PVP ->
			{
				final String size = Math.min(roster, opposition) + "x" + Math.max(roster, opposition);
				require(GROUP_SIZES.contains(size), "GROUP_ROSTER");
				groupSizes.add(size);
			}
		}
	}

	private static Path sidecar(Path manifest, String name)
	{
		return manifest.resolveSibling(manifest.getFileName() + "." + name + ".tsv");
	}

	private static Path modelArchive(Path manifest)
	{
		return manifest.resolveSibling(manifest.getFileName() + ".lineage");
	}

	private static Document document(Path path, String schema, String... keys) throws IOException
	{
		return document(path, DOCUMENT_MAX_BYTES, schema, keys);
	}

	private static Document document(Path path, int maximumBytes, String schema, String... keys) throws IOException
	{
		physical(path);
		require(Files.isRegularFile(path), "NONREGULAR_ORIGINAL");
		require(Files.size(path) <= maximumBytes, "ORIGINAL_SIZE");
		final byte[] bytes;
		try (var stream = Files.newInputStream(path))
		{
			bytes = stream.readNBytes(maximumBytes + 1);
		}
		require(bytes.length <= maximumBytes, "ORIGINAL_SIZE");
		final String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		require(text.endsWith("\n") && !text.contains("\u0000"), "ORIGINAL_ENCODING");
		final var metadata = new HashMap<String, String>();
		final var rows = new ArrayList<String[]>();
		for (String line : text.lines().toList())
		{
			final String[] columns = line.split("\t", -1);
			if (columns[0].equals("meta"))
			{
				fields(columns, "meta", 3);
				require(rows.isEmpty() && !columns[2].isEmpty() && metadata.putIfAbsent(columns[1], columns[2]) == null, "DUPLICATE_OR_MISPLACED_META");
			}
			else
			{
				rows.add(columns);
			}
		}
		require(schema.equals(metadata.get("schema")) && metadata.keySet().equals(Set.of(keys)), "SCHEMA_OR_FIELDS");
		return new Document(sha(bytes), metadata, rows);
	}

	private static void physical(Path path) throws IOException
	{
		require(path != null && path.isAbsolute() && path.normalize().equals(path) && path.toRealPath().equals(path), "NONPHYSICAL_PATH");
	}

	private static String sha(byte[] bytes)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException(impossible);
		}
	}

	private static String digest(String value)
	{
		require(value.matches("[a-f0-9]{64}"), "SHA256_FORMAT");
		return value;
	}

	private static String token(String value)
	{
		require(value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}"), "IDENTITY_FORMAT");
		return value;
	}

	private static long positive(String value)
	{
		final long number = Long.parseLong(value);
		require(number > 0, "POSITIVE_NUMBER");
		return number;
	}

	private static int object(String value)
	{
		return Math.toIntExact(positive(value));
	}

	private static BigDecimal decimal(String value, boolean allowZero)
	{
		require(value.matches("[0-9]{1,18}(\\.[0-9]{1,9})?"), "DECIMAL_FORMAT");
		final var number = new BigDecimal(value);
		require(allowZero || number.signum() > 0, "POSITIVE_DECIMAL");
		return number;
	}

	private static void fields(String[] row, String marker, int count)
	{
		require(row.length == count && row[0].equals(marker), "ROW_FORMAT_" + marker);
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition)
		{
			throw new IllegalArgumentException("EVAL131_" + reason);
		}
	}

	private static final class Incomplete extends RuntimeException
	{
		Incomplete(String reason)
		{
			super(reason);
		}
	}
}
