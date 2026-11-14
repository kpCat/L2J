/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.l2jmobius.gameserver.phantoms.learning.*;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationOutcomeV2.*;

/** Bounded analytics reader. Its only writes are new immutable V2 files in the supplied output directory. */
public final class PhantomEvaluationReplayV2
{
	static void require(boolean ok, String reason) { if (!ok) { throw new IllegalArgumentException("EVAL012_" + reason); } }
	static String text(Path path) throws Exception
	{
		require(path.toAbsolutePath().normalize().equals(path.toRealPath()) && Files.size(path) <= 2_000_000, "SOURCE_PATH_OR_BOUND");
		return Files.readString(path, StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "");
	}
	static Map<String, Object> object(Path path) throws Exception { return map(parse(text(path))); }
	@SuppressWarnings("unchecked") static Map<String, Object> map(Object value) { require(value instanceof Map<?, ?>, "JSON_OBJECT"); return (Map<String, Object>) value; }
	@SuppressWarnings("unchecked") static List<Object> list(Object value) { require(value instanceof List<?>, "JSON_ARRAY"); return (List<Object>) value; }
	static String string(Map<String, Object> map, String key) { require(map.get(key) instanceof String, "STRING_" + key); return (String) map.get(key); }
	static long number(Map<String, Object> map, String key) { require(map.get(key) instanceof Long, "INTEGER_" + key); return (Long) map.get(key); }
	static int integer(Map<String, Object> map, String key) { return Math.toIntExact(number(map, key)); }
	static double real(Map<String, Object> map, String key) { require(map.get(key) instanceof Number, "NUMBER_" + key); final double v = ((Number) map.get(key)).doubleValue(); require(Double.isFinite(v), "FINITE_" + key); return v; }
	static boolean bool(Map<String, Object> map, String key) { require(map.get(key) instanceof Boolean, "BOOLEAN_" + key); return (Boolean) map.get(key); }
	static Long nullable(Map<String, Object> map, String key) { require(map.containsKey(key), "MISSING_" + key); return map.get(key) == null ? null : number(map, key); }
	static Identity identity(String value)
	{ final String[] p = value.split(":", -1); require(p.length == 3, "IDENTITY_CODEC"); return new Identity(Integer.parseInt(p[0]), Long.parseLong(p[1]), Integer.parseInt(p[2])); }
	static TacticalEpisodeV3 episode(Map<String, Object> raw, long cut)
	{
		final var actors = new ArrayList<TacticalEpisodeV3.Actor>();
		for (var value : list(raw.get("actors")))
		{
			final var a = map(value); actors.add(new TacticalEpisodeV3.Actor(number(a, "profile"), integer(a, "objectId"), number(a, "epoch"), integer(a, "class"), integer(a, "level"),
				real(a, "remainingHp"), real(a, "remainingMp"), bool(a, "dead"), nullable(a, "nativeCreditedExp"), nullable(a, "nativeCreditedSp")));
		}
		final var kills = new ArrayList<TacticalEpisodeV3.Kill>();
		for (var value : list(raw.get("nativeKills"))) { final var k = map(value); kills.add(new TacticalEpisodeV3.Kill(integer(k, "targetObjectId"), integer(k, "npcId"), number(k, "spawnGeneration"), integer(k, "instanceId"), integer(k, "creditedActorId"), number(k, "nativeKillSequence"))); }
		final var e = new TacticalEpisodeV3(string(raw, "episode"), string(raw, "ownerNonce"), string(raw, "scope"), cut, Math.addExact(cut, number(raw, "durationNanos")), string(raw, "reason"), actors, kills,
			list(raw.get("eventKeys")).stream().map(String.class::cast).toList(), real(raw, "exactObservedDamage"), real(raw, "usefulObservedHeal"), real(raw, "exactObservedMp"), integer(raw, "observedDeathsLowerBound"));
		require(!bool(raw, "terminalRewardUsedForTraining") && bool(raw, "confirmedKill") == !kills.isEmpty() && string(raw, "outcome").equals(e.outcome()), "SETTLEMENT_CONTRACT"); return e;
	}
	static String single(String pattern, String body)
	{
		final var matcher = Pattern.compile(pattern).matcher(body); require(matcher.find(), "RECORD_FIELD"); final String value = matcher.group(1); require(!matcher.find(), "RECORD_DUPLICATE"); return value;
	}
	static LegalActionV3 action(String text)
	{
		final var match = Pattern.compile("LegalActionV3\\[family=([A-Z_]+), owner=([^,]+), profileId=([0-9]+), actorObjectId=([0-9]+), actorEpoch=([0-9]+), generation=([0-9]+), targetObjectId=([0-9]+), targetEpoch=([0-9]+), partyIdentity=(.*?), skillId=([0-9]+), skillLevel=([0-9]+), mpCost=([-+0-9.eE]+), castMillis=([0-9]+), range=([0-9]+), criticalSafety=(true|false), lawful=(true|false)\\]").matcher(text);
		require(match.matches(), "ACTION_RECORD_CODEC");
		final var a = new LegalActionV3(LegalActionV3.Family.valueOf(match.group(1)), match.group(2), Long.parseLong(match.group(3)), Integer.parseInt(match.group(4)), Long.parseLong(match.group(5)), Long.parseLong(match.group(6)),
			Integer.parseInt(match.group(7)), Long.parseLong(match.group(8)), match.group(9), Integer.parseInt(match.group(10)), Integer.parseInt(match.group(11)), Double.parseDouble(match.group(12)), Integer.parseInt(match.group(13)), Integer.parseInt(match.group(14)), Boolean.parseBoolean(match.group(15)), Boolean.parseBoolean(match.group(16)));
		require(a.toString().equals(text) && a.trainable() && !a.criticalSafety() && a.skillId() > 0 && a.skillLevel() > 0, "ACTION_NOT_LEGAL"); return a;
	}
	static Paid paid(Path path, Map<String, Object> time, TacticalEpisodeV3 episode, List<Identity> actors, Identity target, Authority authority) throws Exception
	{ return paid(path, time, episode, actors, target, authority, false); }
	private static Paid paid(Path path, Map<String, Object> time, TacticalEpisodeV3 episode, List<Identity> actors, Identity target, Authority authority, boolean original011HashesVerified) throws Exception
	{
		final var raw = object(path); require(integer(raw, "schemaVersion") == 6 && string(raw, "format").equals("IMMUTABLE_JAVA_RECORD_TEXT_V1"), "NATIVE_CODEC_VERSION"); sha(string(raw, "observationSha"));
		final String record = string(raw, "nativeRecord"), key = path.getFileName().toString().replace(".native.json", "");
		require(record.startsWith("TacticalReceiptV3[eventKey=" + key + ", attempt=Attempt[observation=TacticalObservationV3[") && episode.eventKeys().contains(key), "NATIVE_EVENT_IDENTITY");
		final String selectedText = single(", selected=(LegalActionV3\\[.*?\\]), baseline=LegalActionV3\\[", record);
		final String baselineText = single(", baseline=(LegalActionV3\\[.*?\\]), modelVersion=[0-9]+\\], beforeHp=", record);
		final var selected = action(selectedText); final var baseline = action(baselineText);
		final long decision = numberRecord(record, "TacticalObservationV3\\[decisionId=([0-9]+),");
		final long observedProfile = numberRecord(record, "TacticalObservationV3\\[decisionId=[0-9]+, capturedNanos=[0-9]+, profileId=([0-9]+),");
		final long observedActor = numberRecord(record, "TacticalObservationV3\\[decisionId=[0-9]+, capturedNanos=[0-9]+, profileId=[0-9]+, actorObjectId=([0-9]+),");
		final long observedEpoch = numberRecord(record, "TacticalObservationV3\\[decisionId=[0-9]+, capturedNanos=[0-9]+, profileId=[0-9]+, actorObjectId=[0-9]+, actorEpoch=([0-9]+),");
		require(selected.generation() == decision && selected.profileId() == observedProfile && selected.actorObjectId() == observedActor && selected.actorEpoch() == observedEpoch, "STALE_DECISION_OR_OBSERVATION");
		final String offeredText = single(", offered=\\[(.*?)\\], selected=LegalActionV3\\[", record); final var offers = new ArrayList<LegalActionV3>();
		final var matches = Pattern.compile("LegalActionV3\\[.*?\\]").matcher(offeredText); while (matches.find()) { offers.add(action(matches.group())); }
		require(offers.size() >= 2 && offers.size() <= 16 && offers.stream().distinct().count() == offers.size() && offers.contains(selected) && offers.contains(baseline), "NATIVE_OFFER_MASK");
		require(numberRecord(record, ", modelVersion=([0-9]+)\\], beforeHp=") == authority.modelVersion(), "PAID_MODEL_AUTHORITY");
		require(offers.stream().allMatch(a -> a.owner().equals(selected.owner()) && a.generation() == selected.generation() && a.family() == selected.family()
			&& actors.stream().anyMatch(id -> id.objectId() == a.actorObjectId() && id.epoch() == a.actorEpoch())
			&& episode.actors().stream().anyMatch(id -> id.profile() == a.profileId() && id.objectId() == a.actorObjectId())
			&& (a.family() == LegalActionV3.Family.DAMAGE ? a.targetObjectId() == target.objectId() && a.targetEpoch() == target.epoch()
				: actors.stream().anyMatch(id -> id.objectId() == a.targetObjectId() && id.epoch() == a.targetEpoch()))), "NATIVE_ACTION_INCARNATION");
		final double hp = Double.parseDouble(single("\\], beforeHp=[-+0-9.eE]+, afterHp=[-+0-9.eE]+, actualHp=([-+0-9.eE]+), actualMp=", record));
		final double mp = Double.parseDouble(single("\\], beforeHp=[-+0-9.eE]+, afterHp=[-+0-9.eE]+, actualHp=[-+0-9.eE]+, actualMp=([-+0-9.eE]+), elapsedNanos=", record));
		final long observed = numberRecord(record, "TacticalObservationV3\\[decisionId=[0-9]+, capturedNanos=([0-9]+),");
		require(observed >= episode.startedNanos() && observed <= number(time, "hpStartedNanos"), "OBSERVATION_BOUNDARY");
		final double before = Double.parseDouble(single("\\], beforeHp=([-+0-9.eE]+), afterHp=", record)), after = Double.parseDouble(single("\\], beforeHp=[-+0-9.eE]+, afterHp=([-+0-9.eE]+), actualHp=", record));
		require(Math.abs(hp - (selected.family() == LegalActionV3.Family.HEAL ? after - before : before - after)) < 1e-6
			&& numberRecord(record, ", elapsedNanos=([0-9]+), reward=") > 0, "PAID_NATIVE_DELTA");
		final long ownerGeneration = numberRecord(record, ", owner=SourceOwner\\[generation=([0-9]+), operationKey=[^]]+\\]\\]$");
		final String operation = single(", owner=SourceOwner\\[generation=[0-9]+, operationKey=([^]]+)\\]\\]$", record);
		require(ownerGeneration > 0 && !operation.equals("CONTRACT_FIXTURE"), "UNVERIFIABLE_NATIVE_OWNER");
		final Path witnessPath = path.resolveSibling(key + ".native-authority-v2.json");
		if (Files.exists(witnessPath))
		{
			final var witness = object(witnessPath); require(string(witness, "schema").equals("EVAL_NATIVE_AUTHORITY_V2") && string(witness, "eventKey").equals(key)
				&& number(witness, "ownerGeneration") == ownerGeneration && string(witness, "operationKey").equals(operation) && number(witness, "decisionId") == decision
				&& number(witness, "actorObjectId") == observedActor && number(witness, "actorEpoch") == observedEpoch && string(witness, "nativeRecordSha").equals(hash(Files.readAllBytes(path))) && !bool(witness, "trainingCredit"), "NATIVE_AUTHORITY_WITNESS");
		}
		else { require(original011HashesVerified && selected.family() == LegalActionV3.Family.DAMAGE, "MISSING_NATIVE_OWNER_WITNESS"); }
		if (selected.family() == LegalActionV3.Family.DAMAGE)
		{
			require(operation.matches("Combat/" + selected.profileId() + "/" + ownerGeneration + "/[1-9][0-9]*"), "NATIVE_OPERATION_IDENTITY");
			// Party may acquire another genuine combat session after CUT. Its action owner is not the initial reservation generation.
			// Legacy011 is admitted only after the published full-file inventory was verified; new arms require the native callback witness above.
			final String admission = text(path.resolveSibling("native-admission.json")); require(admission.startsWith("{\"admitted\":true,") && admission.contains("\"target\":" + target.objectId() + ","), "NATIVE_OWNER_ADMISSION");
		}
		return new Paid(key, hash(Files.readAllBytes(path)), !selected.equals(baseline), hp, mp);
	}
	static long numberRecord(String record, String expression) { return Long.parseLong(single(expression, record)); }
	static Arm readArm(Path work, String pair, String direction, int position, long seed, Authority authority, String stratum, boolean legacy) throws Exception
	{
		return readArm(work, pair, direction, position, seed, authority, stratum, legacy, string(object(work.resolve("reserved.json")), "side"));
	}
	static Map<String, String> sourceHashes(Path work) throws Exception
	{
		final var hashes = new TreeMap<String, String>();
		try (var paths = Files.list(work)) { for (var path : paths.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals("evaluation-arm-v2.bin")).toList()) { text(path); hashes.put(path.getFileName().toString(), hash(Files.readAllBytes(path))); } }
		return hashes;
	}
	static Arm readArm(Path work, String pair, String direction, int position, long seed, Authority authority, String stratum, boolean legacy, String expectedSide) throws Exception
	{
		try { return verifiedArm(work, pair, direction, position, seed, authority, stratum, legacy, expectedSide); }
		catch (java.io.IOException | IllegalArgumentException | ArithmeticException failure)
		{
			if (legacy) { throw failure; }
			Map<String, String> hashes; try { hashes = sourceHashes(work); } catch (java.io.IOException | IllegalArgumentException malformed) { hashes = Map.of(); }
			final String reason = failure instanceof java.nio.file.NoSuchFileException missing ? "MISSING_" + Path.of(missing.getFile()).getFileName().toString().replaceAll("[^A-Za-z0-9_.-]", "_").toUpperCase(Locale.ROOT)
				: failure.getMessage() != null && failure.getMessage().matches("EVAL012_[A-Z0-9_]{1,100}") ? failure.getMessage() : "INVALID_NATIVE_ARTIFACT";
			return new Arm(pair, direction, expectedSide, position, seed, authority, "", List.of(), null, false, false, false, 0, 0, 0, null, List.of(), hashes, stratum, reason);
		}
	}
	private static Arm verifiedArm(Path work, String pair, String direction, int position, long seed, Authority authority, String stratum, boolean legacy, String expectedSide) throws Exception
	{
		final var reserved = object(work.resolve("reserved.json")); require(string(reserved, "split").equals("EVAL") && !bool(reserved, "trainingCredit") && string(reserved, "episode").equals(work.getFileName().toString()), "RESERVATION");
		final var hashes = sourceHashes(work); final String side = string(reserved, "side"); require(side.equals(expectedSide), "RESERVED_SIDE");
		if (!Files.exists(work.resolve("cut.json")) || !Files.exists(work.resolve("settlement.json")))
		{ return new Arm(pair, direction, side, position, seed, authority, "", List.of(), null, false, false, false, 0, 0, 0, null, List.of(), hashes, stratum, Files.exists(work.resolve("precut-refused.json")) ? "PRE_CUT_REFUSED" : "MISSING_SETTLEMENT"); }
		final var cut = object(work.resolve("cut.json")); final var admission = object(work.resolve("encounter-admission.json")); final var parity = object(work.resolve("headless-live-parity.json")); final var result = object(work.resolve("result.json"));
		final String physical = string(cut, "comparisonCellSha");
		require(string(cut, "cell").equals(pair) && bool(result, "afterCut") && string(result, "episode").equals(work.getFileName().toString()) && string(result, "split").equals("EVAL")
			&& string(result, "exclusion").isEmpty() && !bool(cut, "trainingCredit") && !bool(admission, "trainingCredit"), "CUT_OR_RESULT");
		require(string(admission, "stage").equals("LIVE_ADMITTED") && bool(admission, "sealable") && !bool(admission, "afterCut") && string(admission, "physicalSha").equals(physical)
			&& string(parity, "expectedPhysicalSha").equals(physical) && string(parity, "actualPhysicalSha").equals(physical) && string(parity, "diff").equals("[]")
			&& physical.equals(hashes.get("admission-facts.json")), "ADMISSION_PHYSICAL_PARITY");
		final var actors = new ArrayList<Identity>(); Identity target = null;
		for (String line : text(work.resolve("incarnations.tsv")).lines().skip(1).toList())
		{
			final String[] f = line.split("\t", -1); require(f.length == 4, "INCARNATION_TSV"); final var id = identity(String.join(":", f[1], f[2], f[3]));
			if (f[0].equals("ACTOR")) { actors.add(id); } else { require(f[0].equals("TARGET") && target == null, "TARGET_TSV"); target = id; }
		}
		final var episode = episode(object(work.resolve("settlement.json")), number(cut, "cutNanos"));
		require(target != null && !actors.isEmpty(), "MISSING_INCARNATION");
		require(episode.id().equals(work.getFileName().toString()) && episode.scope().equals(string(result, "scope")), "EPISODE_IDENTITY");
		final var paid = new ArrayList<Paid>(); Long terminal = null;
		for (String event : episode.eventKeys())
		{
			final var time = object(work.resolve(event + ".evaluation-timing.json")); require(string(time, "eventKey").equals(event) && number(time, "cutNanos") == episode.startedNanos() && !bool(time, "trainingCredit"), "TIMING_IDENTITY");
			if (terminal == null) { terminal = number(time, "terminalNanos"); } require(terminal == number(time, "terminalNanos"), "INCONSISTENT_TERMINAL");
			if (bool(time, "included"))
			{
				require(number(time, "hpStartedNanos") >= episode.startedNanos() && number(time, "hpStartedNanos") <= terminal && number(time, "hpFinishedNanos") >= number(time, "hpStartedNanos")
					&& (number(time, "hpFinishedNanos") <= terminal || bool(time, "linkedLethal")) && number(time, "mpStartedNanos") >= episode.startedNanos()
					&& number(time, "mpStartedNanos") <= number(time, "mpFinishedNanos") && number(time, "mpFinishedNanos") <= terminal, "TIMING_WINDOW");
				if (bool(time, "linkedLethal")) { final Identity exact = target; require(episode.kills().stream().anyMatch(k -> k.targetObjectId() == exact.objectId() && k.spawnGeneration() == exact.epoch()), "LATE_WRITE_KILL_LINK"); }
				paid.add(paid(work.resolve(event + ".native.json"), time, episode, actors, target, authority, legacy));
			}
		}
		final var facts = object(work.resolve("admission-facts.json")); final String[] objective = string(facts, "objectiveTimeout").split("/", 3);
		require(objective.length == 3 && objective[0].equals("NATIVE_KILL"), "DEADLINE_FACT"); final long deadline = Math.addExact(episode.startedNanos(), Math.multiplyExact(Long.parseLong(objective[1]), 1_000_000_000L));
		if (legacy)
		{
			require(terminal != null && !episode.reason().equals("TIMEOUT"), "LEGACY_TERMINAL_NOT_AUTHENTICATABLE");
			final var lines = text(work.resolve("evaluation-arm.tsv")).split("\n", -1); final String[] f = lines[0].split("\t", -1);
			require(lines.length == 4 && f.length == 4 && f[0].equals("EVAL_ARM_V1") && f[1].equals(physical) && Set.of("true", "false").contains(f[2]) && Boolean.parseBoolean(f[2]) == episode.outcome().equals("COMPLETED")
				&& identity(f[3]).equals(target) && Arrays.stream(lines[1].split(",")).map(PhantomEvaluationReplayV2::identity).toList().equals(actors)
				&& (lines[2].isEmpty() ? List.<String>of() : List.of(lines[2].split(","))).equals(paid.stream().map(Paid::eventKey).toList()), "LEGACY_ARM_PARITY");
		}
		else
		{
			final var boundary = object(work.resolve("terminal-boundary-v2.json")); require(string(boundary, "schema").equals("EVAL_BOUNDARY_V2") && string(boundary, "episode").equals(episode.id())
				&& string(boundary, "ownerNonce").equals(episode.ownerNonce()) && number(boundary, "cutNanos") == episode.startedNanos() && number(boundary, "settledNanos") == episode.settledNanos()
				&& number(boundary, "deadlineNanos") == deadline && bool(boundary, "nativeAdmitted") && !bool(boundary, "stopRequested") && string(boundary, "settlementSha").equals(hashes.get("settlement.json")), "NATIVE_BOUNDARY_WITNESS");
			if (terminal != null) { require(terminal == number(boundary, "terminalNanos"), "BOUNDARY_TIMING_PARITY"); } terminal = number(boundary, "terminalNanos");
		}
		return new Arm(pair, direction, side, position, seed, authority, physical, actors, target, true, true, false, episode.startedNanos(), terminal, deadline, episode, paid, hashes, stratum, "");
	}
	static void publish(Path output, List<Pair> pairs) throws Exception
	{ publish(output, pairs, true); }
	static void publish(Path output, List<Pair> pairs, boolean complete) throws Exception
	{
		Files.createDirectories(output); require(output.toAbsolutePath().normalize().equals(output.toRealPath()), "OUTPUT_REPARSE");
		for (var pair : pairs)
		{
			for (var arm : List.of(pair.stock(), pair.learned())) { immutable(output.resolve(arm.pairId() + "." + arm.side() + ".eval-arm-v2.bin"), encode(arm)); }
			if (complete) { immutable(output.resolve(pair.stock().pairId() + ".eval-pair-v2.json"), pairJson(pair)); }
		}
		final String aggregate = "{\"schema\":\"EVAL_PAIRS_V2\",\"terminalComparablePairs\":" + pairs.stream().filter(Pair::comparable).count()
			+ ",\"pairs\":[" + pairs.stream().map(PhantomEvaluationOutcomeV2::pairJson).collect(java.util.stream.Collectors.joining(",")) + "],\"statistics\":" + json(statistics(pairs)) + ",\"trainingCredit\":false,\"verdict\":\"INCONCLUSIVE\"}";
		immutable(output.resolve(complete ? "evaluation-pairs-v2.json" : "partial-v2-" + hash(aggregate.getBytes(StandardCharsets.UTF_8)) + ".json"), aggregate);
	}
	public static void main(String[] args) throws Exception
	{
		require(args.length == 5 && args[0].equals("legacy011"), "REPLAY_ARGUMENTS"); final Path root = Path.of(args[1]).toAbsolutePath().normalize(), history = Path.of(args[2]).toAbsolutePath().normalize(), configPath = Path.of(args[3]).toAbsolutePath().normalize(), output = Path.of(args[4]).toAbsolutePath().normalize();
		require(!output.startsWith(root) && !root.startsWith(output) && !output.startsWith(history), "REPLAY_DISJOINT");
		final var config = TrainingConfig.load(configPath); require(config.sha256().equals("90ff81476f8ea87a9a621bc8d8a5e2ce7113c0a9d948d52f36d42dba60d303ff"), "ORIGINAL011_CONFIG_HASH");
		final var inventory = list(parse(text(history.resolve("native-files.json")))); final var before = new TreeMap<Path, String>(); Path directory = null;
		for (var item : inventory)
		{
			final var row = map(item); final Path path = root.resolve(string(row, "path")).normalize(); require(path.startsWith(root), "SOURCE_ESCAPE"); text(path);
			require(Files.size(path) == number(row, "bytes") && hash(Files.readAllBytes(path)).equals(string(row, "sha256")), "ORIGINAL011_HASH_MISMATCH"); before.put(path, string(row, "sha256"));
			if (path.getFileName().toString().equals("settlement.json")) { if (directory == null) { directory = path.getParent().getParent(); } require(directory.equals(path.getParent().getParent()), "AMBIGUOUS011"); }
		}
		require(directory != null, "MISSING011_NATIVE_RECEIPTS"); final var protocol = object(directory.resolve("protocol.json")); final var headless = object(history.resolve("headless.json")); final var build = map(headless.get("build"));
		final var authority = new Authority(string(build, "sourceFingerprint"), string(build, "jarSha").toLowerCase(Locale.ROOT), config.sha256(), number(protocol, "modelVersion"), string(protocol, "modelSha"), string(protocol, "trainingStateSha"));
		require(config.text("autonomous009ModelSha", "").equals(authority.modelSha()) && config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE) == authority.modelVersion(), "CONFIG_MODEL_PIN");
		final var rawPairs = object(directory.resolve("evaluation-pairs.json")); final var pairs = new ArrayList<Pair>();
		final var unchanged = new TreeMap<Path, String>(); for (String name : List.of("evaluation-pairs.json", "evaluation.complete", "protocol.json")) { final Path path = directory.resolve(name); unchanged.put(path, hash(Files.readAllBytes(path))); }
		try (var paths = Files.list(directory))
		{
			final var works = paths.filter(Files::isDirectory).filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}")).toList();
			for (var value : list(rawPairs.get("pairs")))
			{
				final var row = map(value); final String cell = string(row, "cell"), direction = string(row, "direction"), first = string(row, "order").equals("STOCK_LEARNED") ? "STOCK" : "LEARNED"; final var arms = new ArrayList<Arm>();
				for (var work : works) { final var cut = object(work.resolve("cut.json")); if (string(cut, "cell").equals(cell)) { arms.add(readArm(work, cell, direction, string(object(work.resolve("reserved.json")), "side").equals(first) ? 0 : 1, config.seed(), authority, "HISTORICAL_UNCLASSIFIED", true)); } }
				require(arms.size() == 2, "ORIGINAL011_PAIR_ARMS"); final var pair = compare(arms.getFirst(), arms.getLast()); require(pair.comparable(), pair.reason()); pairs.add(pair);
			}
		}
		require(pairs.size() == 4 && pairs.stream().filter(p -> p.stock().direction().startsWith("SOLO")).count() == 2, "ORIGINAL011_COVERAGE");
		final var identities = new HashSet<Integer>(); final var events = new HashSet<String>(); for (var pair : pairs) { for (var arm : List.of(pair.stock(), pair.learned())) { for (var id : identities(arm)) { require(identities.add(id.objectId()), "CROSS_PAIR_REUSE"); } for (String key : arm.episode().eventKeys()) { require(events.add(key), "CROSS_PAIR_EVENT_REUSE"); } } }
		publish(output, pairs);
		for (var item : before.entrySet()) { require(hash(Files.readAllBytes(item.getKey())).equals(item.getValue()), "HISTORICAL_RECEIPT_MUTATION"); }
		for (var item : unchanged.entrySet()) { require(hash(Files.readAllBytes(item.getKey())).equals(item.getValue()), "HISTORICAL_VERDICT_MUTATION"); }
		immutable(output.resolve("authentic-replay-v2.complete.json"), json(fields("schema", "AUTHENTIC011_REPLAY_V2", "originalFilesVerified", before.size(), "nativeArms", 8, "paidNativeEvents", events.size(),
			"legacyG4", "FAIL_UNCHANGED", "historicalPairsSha", unchanged.get(directory.resolve("evaluation-pairs.json")), "historicalCompleteSha", unchanged.get(directory.resolve("evaluation.complete")), "trainingCredit", false, "gameVerdict", "INCONCLUSIVE")));
		System.out.println("EVAL012_AUTHENTIC011_TYPED_REPLAY_GREEN pairs=" + pairs.size() + " paidEvents=" + events.size() + " originalFiles=" + before.size());
	}
	static Object parse(String text) { return new Json(text).read(); }
	/** No JSON library exists in this module. This bounded reader rejects duplicate keys and trailing input. */
	private static final class Json
	{
		private final String body; private int at;
		Json(String value) { require(value.length() <= 2_000_000, "JSON_BOUND"); body = value; }
		Object read() { final Object value = value(0); space(); require(at == body.length(), "JSON_TRAILING"); return value; }
		void space() { while (at < body.length() && " \t\r\n".indexOf(body.charAt(at)) >= 0) { at++; } }
		char next() { require(at < body.length(), "JSON_TRUNCATED"); return body.charAt(at++); }
		boolean take(char c) { space(); if (at < body.length() && body.charAt(at) == c) { at++; return true; } return false; }
		Object value(int depth)
		{
			require(depth <= 64, "JSON_DEPTH"); space(); require(at < body.length(), "JSON_TRUNCATED"); final char c = body.charAt(at);
			if (c == '"') { return quoted(); }
			if (take('{'))
			{
				final var map = new LinkedHashMap<String, Object>(); if (take('}')) { return map; }
				do { space(); final String key = quoted(); require(!map.containsKey(key) && take(':'), "JSON_DUPLICATE_OR_COLON"); map.put(key, value(depth + 1)); require(map.size() <= 8192, "JSON_OBJECT_BOUND"); } while (take(',')); require(take('}'), "JSON_OBJECT_END"); return map;
			}
			if (take('[')) { final var values = new ArrayList<Object>(); if (take(']')) { return values; } do { values.add(value(depth + 1)); require(values.size() <= 8192, "JSON_ARRAY_BOUND"); } while (take(',')); require(take(']'), "JSON_ARRAY_END"); return values; }
			for (String literal : List.of("true", "false", "null")) { if (body.startsWith(literal, at)) { at += literal.length(); return literal.equals("null") ? null : literal.equals("true"); } }
			final int start = at; while (at < body.length() && "-+0123456789.eE".indexOf(body.charAt(at)) >= 0) { at++; } final String number = body.substring(start, at);
			require(number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"), "JSON_NUMBER");
			if (number.contains(".") || number.contains("e") || number.contains("E")) { final double n = Double.parseDouble(number); require(Double.isFinite(n), "JSON_FINITE"); return n; } return Long.parseLong(number);
		}
		String quoted()
		{
			require(next() == '"', "JSON_STRING"); final var result = new StringBuilder();
			while (true)
			{
				final char c = next(); if (c == '"') { return result.toString(); } require(c >= 32, "JSON_CONTROL"); if (c != '\\') { result.append(c); continue; }
				final char escaped = next(); switch (escaped)
				{
					case '"', '\\', '/' -> result.append(escaped); case 'b' -> result.append('\b'); case 'f' -> result.append('\f'); case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
					case 'u' -> { require(at + 4 <= body.length(), "JSON_UNICODE"); result.append((char) Integer.parseInt(body.substring(at, at + 4), 16)); at += 4; }
					default -> throw new IllegalArgumentException("EVAL012_JSON_ESCAPE");
				}
			}
		}
	}
	private PhantomEvaluationReplayV2() { }
}
