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
	private static final String REPORT = "TASK131_EVALUATION_REPORT_V1";
	private static final int MAX_BYTES = 8 * 1024 * 1024;
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

	private record Block(BlockPlan plan, String sceneId, String fixtureSha, String opponentSha,
		int opponentSize, BigDecimal budget, long deadline, Set<Role> requiredRoles) { }
	private record Audit(Document manifest, Map<String, Block> blocks, Map<Integer, BigDecimal> prices, Instant lockedAt) { }
	private record Occurrence(String boot, int bootOrder, String id, String sealSha, int attempt, int position, Block block) { }
	private record Participant(long epoch, Side side, Role role) { }
	private record Binding(Document document, Slot slot, long started, Map<Integer, Participant> participants) { }
	private record Evaluation(Episode episode, long elapsed, String boot, Raw raw) { }
	private record ProcessIdentity(long pid, Instant started) { }
	private record ActorEpoch(int object, long epoch) { }
	private record Action(int source, int target, Role role, String selected, int stage) { }
	private record Control(int target, String effect) { }
	private record ActiveControl(long started, String operation, boolean scored) { }
	private record Subject(String operation, int target) { }
	private record ItemPayment(String operation, int item) { }

	private PhantomLearning131Evaluation() { }

	public static void main(String[] args) throws IOException
	{
		if (args.length != 4)
		{
			throw new IllegalArgumentException("EVAL131_ARGUMENTS");
		}
		System.exit(run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), digest(args[3])));
	}

	static int run(Path manifest, Path originals, Path output, String evaluatorSha) throws IOException
	{
		physical(manifest.getParent());
		physical(output.getParent());
		require(originals.isAbsolute() && originals.normalize().equals(originals) && output.isAbsolute() && output.normalize().equals(output)
			&& !output.startsWith(originals) && !output.equals(manifest) && !output.equals(sidecar(manifest, "protocol"))
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
		final Document manifest = document(path, "FROZEN_EVALUATION131_V1", "schema", "bank", "protocolSha", "splitSha", "sourceSha", "jarSha",
			"configSha", "policySha", "evaluatorSha", "modelSchema", "candidateSha", "referenceSha", "referenceAt", "selectedAt", "lockedAt",
		"opponent.B0", "opponent.A1", "opponent.B1", "trainStartedAt");
		require(manifest.rows().isEmpty(), "MANIFEST_ROWS");
		final String bank = token(manifest.get("bank"));
		require(manifest.get("sourceSha").matches("[a-f0-9]{40}|[a-f0-9]{64}"), "SOURCE_SHA");
		for (String key : List.of("protocolSha", "splitSha", "jarSha", "configSha", "policySha", "evaluatorSha", "modelSchema",
			"candidateSha", "referenceSha", "opponent.B0", "opponent.A1", "opponent.B1"))
		{
			digest(manifest.get(key));
		}
		require(manifest.get("evaluatorSha").equals(evaluatorSha), "EVALUATOR_DRIFT");
		final Instant locked = Instant.parse(manifest.get("lockedAt"));
		final Instant reference = Instant.parse(manifest.get("referenceAt"));
		final Instant selected = Instant.parse(manifest.get("selectedAt"));
		require(reference.isBefore(selected) && !selected.isAfter(locked) && manifest.get("referenceSha").equals(manifest.get("opponent.A1"))
			&& !manifest.get("candidateSha").equals(manifest.get("referenceSha"))
			&& Set.of(manifest.get("opponent.B0"), manifest.get("opponent.A1"), manifest.get("opponent.B1")).size() == OPPONENTS.size(), "FROZEN_MODEL_LINEAGE");

		final Document protocol = document(sidecar(path, "protocol"), "AUDIT_PROTOCOL131_V1", "schema", "bank", "seed", "frozenAt");
		require(protocol.sha().equals(manifest.get("protocolSha")) && protocol.get("bank").equals(bank), "PROTOCOL_PIN");
		Long.parseLong(protocol.get("seed"));
		final Instant trainStarted = Instant.parse(manifest.get("trainStartedAt"));
		require(Instant.parse(protocol.get("frozenAt")).isBefore(trainStarted) && trainStarted.isBefore(reference), "PROTOCOL_FREEZE_ORDER");
		final Document splits = document(sidecar(path, "splits"), "AUDIT_SPLITS131_V1", "schema", "bank", "protocolSha");
		require(splits.get("bank").equals(bank) && splits.get("protocolSha").equals(protocol.sha()) && splits.sha().equals(manifest.get("splitSha")), "SPLIT_PIN");
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

		final var blocks = new LinkedHashMap<String, Block>();
		final var sceneIds = new HashSet<String>();
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
			require(sceneIds.add(sceneId) && seenEncounters.add(digest(row[6])), "SPLIT_LEAKAGE_OR_DUPLICATE_SCENE");
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
			require(blocks.putIfAbsent(plan.id(), new Block(plan, sceneId, digest(row[5]), manifest.get("opponent." + row[7]), opponentSize, budget, deadline, Set.copyOf(roles))) == null, "DUPLICATE_BLOCK");
		}
		require(blocks.size() == BANK_SIZE && !prices.isEmpty() && opponentLabels.equals(OPPONENTS) && groupSizes.equals(GROUP_SIZES)
			&& bankRoles.equals(EnumSet.complementOf(EnumSet.of(Role.OPPONENT))) && strataLevels.size() == Stratum.values().length
			&& strataLevels.values().stream().allMatch(count -> count.equals(Map.of(52, 2, 64, 2, 76, 4))), "INCOMPLETE_FROZEN_BANK");
		return new Audit(manifest, blocks, prices, locked);
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
			final Document boot = document(originals.resolve("boots").resolve(bootId).resolve("complete131.tsv"), "NATIVE_BOOT131_V1",
				"schema", "bank", "manifestSha", "bootId", "pid", "processStarted", "sourceSha", "jarSha", "configSha", "policySha");
			require(boot.sha().equals(digest(row[4])) && boot.get("manifestSha").equals(manifest.sha()) && boot.get("bootId").equals(bootId)
				&& boot.get("pid").equals(row[2]) && boot.get("processStarted").equals(row[3]), "OWNED_BOOT_PIN");
			for (String key : List.of("bank", "sourceSha", "jarSha", "configSha", "policySha"))
			{
				require(boot.get(key).equals(manifest.get(key)), "BOOT_DRIFT_" + key);
			}
			for (String[] member : boot.rows())
			{
				fields(member, "episode", 6);
				final Block block = audit.blocks().get(member[1]);
				final int position = Integer.parseInt(member[2]);
				final int attempt = Integer.parseInt(member[3]);
				final String episodeId = token(member[4]);
				require(block != null && position >= 0 && position < 4 && (attempt == 0 || attempt == 1) && episodeIds.add(episodeId), "EPISODE_IDENTITY");
				occurrences.computeIfAbsent(member[1], _ -> new ArrayList<>()).add(new Occurrence(bootId, bootIds.size(), episodeId, digest(member[5]), attempt, position, block));
			}
		}

		final var actorEpochs = new HashSet<ActorEpoch>();
		final var actorBlocks = new HashMap<Integer, String>();
		final var eventKeys = new HashSet<String>();
		final var rows = new ArrayList<Evaluation>();
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
				final Document seal = document(directory.resolve("complete131.tsv"), "NATIVE_EPISODE131_V1", "schema", "bindingSha", "eventsSha", "status", "elapsedNanos");
				require(seal.sha().equals(occurrence.sealSha()), "EPISODE_SEAL_PIN");
				final Binding binding = bind(directory, manifest, occurrence, seal);
				require(occurrence.bootOrder() >= previousBoot && (occurrence.bootOrder() != previousBoot || binding.started() >= previousEnd), "EXECUTED_BLOCK_ORDER");
				previousBoot = occurrence.bootOrder();
				previousEnd = binding.started();
				final Document evidence = document(directory.resolve("events131.tsv"), "NATIVE_EVENTS131_V1", "schema", "episodeId", "bindingSha");
				require(evidence.sha().equals(seal.get("eventsSha")) && evidence.get("episodeId").equals(occurrence.id()) && evidence.get("bindingSha").equals(binding.document().sha()), "RAW_EVENTS_PIN");
				if (retry)
				{
					require(seal.get("status").equals("PRE_ACTION_INFRA_RETRY") && seal.get("elapsedNanos").equals("0") && evidence.rows().isEmpty(), "RETRY_AFTER_ACTIVITY");
					continue;
				}
				require(seal.get("status").equals("DRAINED"), "DRAINED_NATIVE_EPISODE");
				for (var actor : binding.participants().entrySet())
				{
					final String priorBlock = actorBlocks.putIfAbsent(actor.getKey(), plan.id());
					require((priorBlock == null || priorBlock.equals(plan.id())) && actorEpochs.add(new ActorEpoch(actor.getKey(), actor.getValue().epoch())), "REUSED_ACTOR_COHORT_OR_EPOCH");
				}
				final long elapsed = positive(seal.get("elapsedNanos"));
				previousEnd = Math.addExact(binding.started(), elapsed);
				final var raw = new Raw(binding, occurrence.block(), audit.prices());
				for (String[] event : evidence.rows())
				{
					raw.accept(event, eventKeys);
				}
				rows.add(new Evaluation(raw.finish(occurrence, elapsed), elapsed, occurrence.boot(), raw));
			}
		}
		return report(audit, origins.sha(), plans, rows);
	}

	private static Binding bind(Path directory, Document manifest, Occurrence occurrence, Document seal) throws IOException
	{
		final Document binding = document(directory.resolve("binding131.tsv"), "NATIVE_BINDING131_V1", "schema", "manifestSha", "bootId", "blockId", "episodeId",
			"attempt", "position", "arm", "side", "sceneId", "fixtureSha", "opponentSha", "policySha", "modelSha", "startNanos");
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
			&& binding.get("fixtureSha").equals(block.fixtureSha()) && binding.get("opponentSha").equals(block.opponentSha())
			&& binding.get("policySha").equals(manifest.get("policySha")) && binding.get("modelSha").equals(model), "NATIVE_EPISODE_BINDING");
		final var participants = new HashMap<Integer, Participant>();
		for (String[] row : binding.rows())
		{
			fields(row, "actor", 5);
			final var actor = new Participant(positive(row[2]), Side.valueOf(row[3]), Role.valueOf(row[4]));
			require(participants.putIfAbsent(object(row[1]), actor) == null && (actor.side() != slot.side() || actor.role() != Role.OPPONENT), "BOUND_ACTOR");
		}
		final long scored = participants.values().stream().filter(a -> a.side() == slot.side()).count();
		require(scored == block.plan().scene().initialRosterSize() && participants.size() - scored == block.opponentSize(), "BOUND_ROSTER");
		return new Binding(binding, slot, Long.parseLong(binding.get("startNanos")), participants);
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
		private final Set<ItemPayment> _payments = new HashSet<>();
		private final Set<String> _unknownActions = new HashSet<>();
		private final Set<Integer> _deaths = new HashSet<>();
		private final Set<Integer> _creditedMobs = new HashSet<>();
		private final Set<Integer> _escaped = new HashSet<>();
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

		Raw(Binding binding, Block block, Map<Integer, BigDecimal> prices)
		{
			_binding = binding;
			_block = block;
			_prices = prices;
		}

		private void accept(String[] row, Set<String> eventKeys)
		{
			require(row.length >= 7 && row[0].equals("event") && eventKeys.add(digest(row[1])), "DUPLICATE_OR_INVALID_EVENT");
			final long offset = Math.subtractExact(Long.parseLong(row[2]), _binding.started());
			require(offset >= _lastEvent, "NATIVE_TIME_ORDER");
			_controlNanos = Math.addExact(_controlNanos, Math.multiplyExact(offset - _lastEvent, _scoredControl.size()));
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
					require(friendly != friendlyTarget && hp.signum() + cp.signum() > 0, "DAMAGE_EFFECT");
					_damageEvents.add(new Subject(operation, target));
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
					final BigDecimal mp = decimal(row[7], false);
					require(friendly == friendlyTarget && source != target, "MP_TRANSFER_EFFECT");
					if (friendly) { _mpTransfer = _mpTransfer.add(mp); }
					else { _opponentEffects++; }
					return friendly && role == Role.SUPPORT;
				}
				case "AGGRO", "BUFF" ->
				{
					length(row, 8);
					if (row[3].equals("AGGRO"))
					{
						decimal(row[7], false);
						require(friendly != friendlyTarget && _block.plan().scene().objective() == Objective.PVE_KILLS, "NATIVE_NPC_AGGRO_REQUIRED");
						if (friendly) { _aggro++; }
						return friendly && role == Role.TANK;
					}
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
								if (offset > active.started() && _actions.get(active.operation()).role() == Role.CONTROL)
								{
									_samples.computeIfAbsent(Role.CONTROL, _ -> new HashSet<>()).add(active.operation());
								}
							}
							else if (offset > active.started()) { _opponentEffects++; }
							return false;
						}
						case "INTERRUPT" -> { if (friendly) { _interrupts++; } else { _opponentEffects++; } }
					}
					return friendly && role == Role.CONTROL;
				}
				case "PAY" ->
				{
					length(row, 9);
					final int item = object(row[7]);
					final BigDecimal price = _prices.get(item);
					require(price != null && source == target && _payments.add(new ItemPayment(operation, item)), "PAID_RESOURCE_PIN_OR_DUPLICATE");
					final BigDecimal value = price.multiply(BigDecimal.valueOf(positive(row[8])));
					if (friendly) { _paid = _paid.add(value); }
				}
				case "DEATH" ->
				{
					length(row, 7);
					require(friendly != friendlyTarget && _damageEvents.contains(new Subject(operation, target)) && !_escaped.contains(target) && _deaths.add(target), "DUPLICATE_OR_ILLEGAL_DEATH");
				}
				case "KILL_CREDIT" ->
				{
					length(row, 7);
					require(_block.plan().scene().objective() == Objective.PVE_KILLS && friendly && !friendlyTarget && _deaths.contains(target) && _creditedMobs.add(target), "NATIVE_KILL_CREDIT");
					if (_creditedMobs.size() == _block.plan().scene().requiredProgress()) { _objectiveAt = offset; }
				}
				case "ESCAPE_ARRIVAL" ->
				{
					length(row, 7);
					require(friendly == friendlyTarget && !_deaths.contains(target) && _escaped.add(target), "NATIVE_ESCAPE_ARRIVAL");
					if (friendly && _block.plan().scene().objective() == Objective.ESCAPE && scoredCount(_escaped) == _block.plan().scene().requiredProgress()) { _objectiveAt = offset; }
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

		private Episode finish(Occurrence occurrence, long elapsed)
		{
			require(_lastEvent <= elapsed && _control.isEmpty() && _paid.compareTo(_block.budget()) <= 0, "DRAIN_TIME_CONTROL_OR_BUDGET");
			if (_executed == 0) { throw new Incomplete("NO_NATIVE_EXECUTION"); }
			final Scene scene = _block.plan().scene();
			final int deaths = scoredCount(_deaths);
			final int escaped = scoredCount(_escaped);
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
			require(outcome == Outcome.TIMEOUT ? elapsed >= _block.deadline() : elapsed <= _block.deadline(), "TERMINAL_DEADLINE");
			return new Episode(_block.plan().id(), occurrence.id(), occurrence.position(), _binding.slot(), outcome, progress, deaths, _paid.doubleValue(), _objectiveAt >= 0 ? _objectiveAt : elapsed);
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
			"objectiveElapsedNanos", "terminalElapsedNanos", "damageHP", "damageCP", "receivedHP", "receivedCP", "effectiveHeal", "overheal", "mpTransfer",
			"controlTargetNanos", "interrupts", "brokenCC", "aggroEffects", "buffEffects", "ownExecuted", "opponentExecuted", "opponentEffects", "score"));
		result.add(row("columns", "role", "episodeId", "role", "samples", "unknown"));
		for (Evaluation evaluation : evaluations)
		{
			final Episode episode = evaluation.episode();
			final Raw raw = evaluation.raw();
			final Scene scene = audit.blocks().get(episode.blockId()).plan().scene();
			result.add(row("episode", episode.blockId(), episode.episodeId(), evaluation.boot(), episode.position(), episode.slot().arm(), episode.slot().side(),
				episode.outcome(), episode.objectiveProgress(), episode.nativeDeaths(), raw._paid.toPlainString(), (long) episode.elapsed(), evaluation.elapsed(),
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

	private static Document document(Path path, String schema, String... keys) throws IOException
	{
		physical(path);
		require(Files.isRegularFile(path), "NONREGULAR_ORIGINAL");
		final byte[] bytes;
		try (var stream = Files.newInputStream(path))
		{
			bytes = stream.readNBytes(MAX_BYTES + 1);
		}
		require(bytes.length <= MAX_BYTES, "ORIGINAL_SIZE");
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
