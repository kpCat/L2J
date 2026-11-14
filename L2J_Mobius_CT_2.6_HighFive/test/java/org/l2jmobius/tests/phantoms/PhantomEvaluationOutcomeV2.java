/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;

/** TEST-only outcome assessment; never supplies a reward, training credit or execution authority. */
public final class PhantomEvaluationOutcomeV2
{
	public record Identity(int objectId, long epoch, int instance)
	{ public Identity { if (objectId <= 0 || epoch <= 0 || instance < 0) { throw new IllegalArgumentException("EVAL012_IDENTITY"); } } }
	public record Authority(String sourceSha, String buildSha, String configSha, long modelVersion, String modelSha, String trainingStateSha)
	{
		public Authority { for (var value : List.of(sourceSha, buildSha, configSha, modelSha, trainingStateSha)) { sha(value); } if (modelVersion < 1) { throw new IllegalArgumentException("EVAL012_MODEL"); } }
	}
	public record Paid(String eventKey, String sourceSha, boolean diverged, double hp, double mp)
	{
		public Paid { sha(eventKey); sha(sourceSha); if (!Double.isFinite(hp) || hp <= 0 || !Double.isFinite(mp) || mp <= 0) { throw new IllegalArgumentException("EVAL012_PAID"); } }
	}
	public record Arm(String pairId, String direction, String side, int position, long seed, Authority authority, String physicalSha,
		List<Identity> actors, Identity target, boolean nativeAdmitted, boolean afterCut, boolean cancelled, long cutNanos, long terminalNanos, long deadlineNanos,
		TacticalEpisodeV3 episode, List<Paid> paid, Map<String, String> sourceHashes, String stratum, String refusal)
	{
		public Arm
		{
			sha(pairId); if (!physicalSha.isEmpty()) { sha(physicalSha); }
			if (!Set.of("SOLO_DAMAGE", "PARTY_DAMAGE", "PARTY_HEAL").contains(direction) || !Set.of("STOCK", "LEARNED").contains(side)
				|| position < 0 || position > 1 || authority == null || stratum == null || !stratum.matches("[A-Z0-9_]{1,64}")
				|| refusal == null || !refusal.matches("[A-Z0-9_:.-]{0,256}")) { throw new IllegalArgumentException("EVAL012_ENVELOPE"); }
			actors = List.copyOf(actors); paid = List.copyOf(paid); sourceHashes = Map.copyOf(sourceHashes);
			if (actors.size() > 9 || paid.size() > 2048 || sourceHashes.size() > 256) { throw new IllegalArgumentException("EVAL012_BOUND"); }
			for (var entry : sourceHashes.entrySet()) { if (!entry.getKey().matches("[A-Za-z0-9_.-]{1,128}")) { throw new IllegalArgumentException("EVAL012_SOURCE_NAME"); } sha(entry.getValue()); }
		}
	}
	public record Pair(Arm stock, Arm learned, boolean comparable, boolean bothGoalSucceeded, String reason, Integer winDifference, Double killTimeDeltaMillis) { }
	public static Pair compare(Arm a, Arm b)
	{
		final Arm stock = a.side().equals("STOCK") ? a : b, learned = a.side().equals("LEARNED") ? a : b;
		String reason = "";
		if (a.side().equals(b.side()) || a.position() == b.position()) { reason = "SIDES_OR_ORDER"; }
		else if (!a.pairId().equals(b.pairId()) || !a.direction().equals(b.direction()) || a.seed() != b.seed() || !a.stratum().equals(b.stratum())) { reason = "CONTEXT"; }
		else if (!a.authority().equals(b.authority())) { reason = "AUTHORITY"; }
		else if (!(reason = invalid(a)).isEmpty() || !(reason = invalid(b)).isEmpty()) { }
		else if (a.physicalSha().isEmpty() || !a.physicalSha().equals(b.physicalSha())) { reason = "PHYSICAL_FACTS"; }
		else if (identities(a).stream().anyMatch(x -> identities(b).stream().anyMatch(y -> x.objectId() == y.objectId())) || a.episode().id().equals(b.episode().id())) { reason = "REUSED_INCARNATION"; }
		else if (a.episode().eventKeys().stream().anyMatch(b.episode().eventKeys()::contains)) { reason = "REUSED_EVENT"; }
		if (!reason.isEmpty()) { return new Pair(stock, learned, false, false, "NOT_COMPARABLE_" + reason, null, null); }
		final boolean sw = goal(stock), lw = goal(learned), both = sw && lw;
		return new Pair(stock, learned, true, both, timeout(a) || timeout(b) ? "COMPARABLE_WITH_TIMEOUT" : "TERMINAL_COMPARABLE",
			(lw ? 1 : 0) - (sw ? 1 : 0), both ? (learned.terminalNanos() - learned.cutNanos() - stock.terminalNanos() + stock.cutNanos()) / 1e6 : null);
	}
	static List<Identity> identities(Arm arm) { final var result = new ArrayList<>(arm.actors()); if (arm.target() != null) { result.add(arm.target()); } return result; }
	static String invalid(Arm arm)
	{
		if (!arm.refusal().isEmpty()) { return arm.refusal(); }
		if (!arm.nativeAdmitted() || !arm.afterCut()) { return "PRE_CUT_OR_NOT_ADMITTED"; }
		if (arm.cancelled()) { return "CANCELLED"; }
		final var e = arm.episode();
		if (e == null || !arm.sourceHashes().containsKey("settlement.json")) { return "MISSING_SETTLEMENT"; }
		if (!e.sha256().equals(arm.sourceHashes().get("settlement.json"))) { return "SETTLEMENT_HASH"; }
		if (arm.cutNanos() <= 0 || e.startedNanos() != arm.cutNanos() || arm.terminalNanos() <= arm.cutNanos() || e.settledNanos() < arm.terminalNanos()
			|| arm.deadlineNanos() <= arm.cutNanos() || arm.terminalNanos() > arm.deadlineNanos()) { return "BOUNDARY"; }
		if (!Set.of("NATIVE_KILL", "NATIVE_ACTOR_DIED", "TIMEOUT").contains(e.reason())) { return "UNSUPPORTED_TERMINAL_REASON"; }
		if (arm.target() == null || arm.actors().isEmpty() || identities(arm).stream().map(Identity::objectId).distinct().count() != arm.actors().size() + 1
			|| arm.actors().stream().anyMatch(x -> x.instance() != arm.target().instance()) || e.actors().size() != arm.actors().size()
			|| e.actors().stream().anyMatch(x -> arm.actors().stream().noneMatch(y -> x.objectId() == y.objectId() && x.epoch() == y.epoch()))) { return "SETTLEMENT_INCARNATION"; }
		if (!e.scope().equals(arm.direction().startsWith("SOLO") ? "SOLO" : "PARTY")) { return "SCOPE"; }
		if (e.kills().stream().anyMatch(k -> k.targetObjectId() != arm.target().objectId() || k.spawnGeneration() != arm.target().epoch() || k.instanceId() != arm.target().instance()
			|| arm.actors().stream().noneMatch(x -> x.objectId() == k.creditedActorId()))) { return "FOREIGN_KILL"; }
		if (e.reason().equals("NATIVE_KILL") && e.kills().isEmpty()) { return "UNPROVEN_KILL"; }
		if (e.reason().equals("NATIVE_ACTOR_DIED") && e.observedDeaths() == 0) { return "UNPROVEN_DEATH"; }
		if (timeout(arm) && (arm.terminalNanos() != arm.deadlineNanos() || !e.kills().isEmpty() || e.observedDeaths() != 0 || dead(arm))) { return "UNPROVEN_TIMEOUT"; }
		if (arm.paid().stream().map(Paid::eventKey).distinct().count() != arm.paid().size() || arm.paid().stream().anyMatch(p -> !e.eventKeys().contains(p.eventKey()))) { return "FOREIGN_OR_DUPLICATE_PAID"; }
		return "";
	}
	static boolean goal(Arm arm) { return arm.episode() != null && !arm.episode().kills().isEmpty(); }
	static boolean dead(Arm arm) { return arm.episode() != null && arm.episode().actors().stream().anyMatch(TacticalEpisodeV3.Actor::dead); }
	static boolean hasDeath(Arm arm) { return arm.episode() != null && (arm.episode().observedDeaths() > 0 || dead(arm)); }
	static boolean timeout(Arm arm) { return arm.episode() != null && arm.episode().reason().equals("TIMEOUT"); }
	static String q(String value) { return PhantomLearning005Evaluation.q(value); }
	static void sha(String value) { PhantomLearning005Evaluation.sha(value); }
	static String hash(byte[] value) { return org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.sha256(value); }
	static String json(Map<String, ?> values)
	{
		return "{" + values.entrySet().stream().map(e -> q(e.getKey()) + ":" + jsonValue(e.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}";
	}
	static String jsonValue(Object value)
	{
		if (value == null) { return "null"; } if (value instanceof String s) { return q(s); }
		if (value instanceof Map<?, ?> m) { final var result = new LinkedHashMap<String, Object>(); m.forEach((k, v) -> result.put((String) k, v)); return json(result); }
		if (value instanceof Collection<?> c) { return "[" + c.stream().map(PhantomEvaluationOutcomeV2::jsonValue).collect(java.util.stream.Collectors.joining(",")) + "]"; }
		if (value instanceof Boolean || value instanceof Number) { return value.toString(); } throw new IllegalArgumentException("EVAL012_JSON_TYPE");
	}
	static Map<String, Object> fields(Object... pairs)
	{ final var result = new LinkedHashMap<String, Object>(); for (int i = 0; i < pairs.length; i += 2) { result.put((String) pairs[i], pairs[i + 1]); } return result; }
	static String pairJson(Pair pair)
	{
		return json(fields("schema", "EVAL_PAIR_V2", "cell", pair.stock().pairId(), "direction", pair.stock().direction(),
			"order", pair.stock().position() == 0 ? "STOCK_LEARNED" : "LEARNED_STOCK", "stratum", pair.stock().stratum(),
			"status", pair.reason(), "terminalComparable", pair.comparable(), "bothGoalSucceeded", pair.bothGoalSucceeded(), "winDifference", pair.winDifference(),
			"killTimeDeltaMillis", pair.killTimeDeltaMillis(), "stock", armSummary(pair.stock()), "learned", armSummary(pair.learned()), "trainingCredit", false));
	}
	static Map<String, Object> armSummary(Arm arm)
	{
		final var e = arm.episode();
		return fields("side", arm.side(), "refusal", arm.refusal(), "nativeAdmitted", arm.nativeAdmitted(), "afterCut", arm.afterCut(), "terminalSettled", invalid(arm).isEmpty(),
			"terminalReason", e == null ? null : e.reason(), "goalSuccess", invalid(arm).isEmpty() ? goal(arm) : null,
			"terminalDead", e == null ? null : dead(arm), "observedDeathsLowerBound", e == null ? null : e.observedDeaths(), "timeoutCensored", timeout(arm),
			"cutNanos", arm.cutNanos(), "terminalNanos", arm.terminalNanos(), "settledNanos", e == null ? null : e.settledNanos(), "deadlineNanos", arm.deadlineNanos(),
			"terminalDurationMillis", e == null ? null : (arm.terminalNanos() - arm.cutNanos()) / 1e6,
			"timeToKillMillis", invalid(arm).isEmpty() && goal(arm) ? (arm.terminalNanos() - arm.cutNanos()) / 1e6 : null,
			"paidActions", arm.paid().size(), "paidLegalDivergences", arm.paid().stream().filter(Paid::diverged).count(),
			"paidMetricsKnown", !arm.paid().isEmpty(), "selectedPaidHp", arm.paid().isEmpty() ? null : arm.paid().stream().mapToDouble(Paid::hp).sum(),
			"selectedPaidMp", arm.paid().isEmpty() ? null : arm.paid().stream().mapToDouble(Paid::mp).sum(),
			"remainingActors", e == null ? null : e.actors().stream().map(a -> fields("hp", a.hp(), "mp", a.mp(), "dead", a.dead(), "exp", a.exp(), "sp", a.sp())).toList(),
			"wholePartyDamage", null, "wholePartyMp", null, "completeDeaths", null, "economy", null,
			"physicalSha", arm.physicalSha(), "sourceReceiptHashes", new TreeMap<>(arm.sourceHashes()), "armV2Sha", hash(encode(arm)));
	}
	static Map<String, Object> statistics(List<Pair> pairs)
	{
		final var result = new LinkedHashMap<String, Object>();
		for (String scope : List.of("SOLO", "PARTY"))
		{
			final var group = pairs.stream().filter(p -> p.stock().direction().startsWith(scope)).toList(); final var valid = group.stream().filter(Pair::comparable).toList();
			final int n = valid.size(), learnedOnly = (int) valid.stream().filter(p -> p.winDifference() == 1).count(), stockOnly = (int) valid.stream().filter(p -> p.winDifference() == -1).count();
			final int both = (int) valid.stream().filter(Pair::bothGoalSucceeded).count(), neither = n - learnedOnly - stockOnly - both;
			final var sides = new LinkedHashMap<String, Object>();
			for (String side : List.of("STOCK", "LEARNED"))
			{
				final var arms = valid.stream().map(p -> side.equals("STOCK") ? p.stock() : p.learned()).toList(); final int wins = (int) arms.stream().filter(PhantomEvaluationOutcomeV2::goal).count();
				sides.put(side, fields("wins", wins, "deathArms", arms.stream().filter(PhantomEvaluationOutcomeV2::hasDeath).count(), "timeouts", arms.stream().filter(PhantomEvaluationOutcomeV2::timeout).count(),
					"successRateWilson95", wilson(wins, n), "paidLegalDivergences", arms.stream().flatMap(a -> a.paid().stream()).filter(Paid::diverged).count()));
			}
			result.put(scope, fields("terminalComparablePairs", n, "bothGoalSucceededPairs", both, "withDeathPairs", valid.stream().filter(p -> hasDeath(p.stock()) || hasDeath(p.learned())).count(),
				"boundedTimeoutPairs", valid.stream().filter(p -> timeout(p.stock()) || timeout(p.learned())).count(), "incomparablePairs", group.size() - n,
				"learnedWinsOnly", learnedOnly, "stockWinsOnly", stockOnly, "bothWin", both, "neitherWin", neither,
				"timeoutsUnknownFuture", valid.stream().flatMap(p -> java.util.stream.Stream.of(p.stock(), p.learned())).filter(PhantomEvaluationOutcomeV2::timeout).count(),
				"discordantPairs", learnedOnly + stockOnly, "exactTwoSidedSignP", sign(learnedOnly, stockOnly), "sides", sides,
				"strata", group.stream().map(p -> p.stock().stratum()).distinct().sorted().toList(), "gameVerdict", "INCONCLUSIVE", "confidence", "SMALL_N_DESCRIPTIVE_ONLY"));
		}
		return result;
	}
	static List<Pair> campaign(List<Pair> pairs)
	{
		final var owners = new HashMap<String, Integer>(); final var collisions = new HashSet<Integer>();
		for (int index = 0; index < pairs.size(); index++)
		{
			final var pair = pairs.get(index); final var keys = new HashSet<String>(); keys.add("pair/" + pair.stock().pairId());
			for (var arm : List.of(pair.stock(), pair.learned()))
			{
				for (var id : identities(arm)) { keys.add("object/" + id.objectId()); }
				if (arm.episode() != null) { keys.add("episode/" + arm.episode().id()); for (String event : arm.episode().eventKeys()) { keys.add("event/" + event); } }
			}
			for (String key : keys) { final var previous = owners.putIfAbsent(key, index); if (previous != null) { collisions.add(index); collisions.add(previous); } }
		}
		final var result = new ArrayList<Pair>(); for (int index = 0; index < pairs.size(); index++) { final var p = pairs.get(index); result.add(collisions.contains(index) ? new Pair(p.stock(), p.learned(), false, false, "NOT_COMPARABLE_CROSS_PAIR_REUSE", null, null) : p); } return List.copyOf(result);
	}
	static Object wilson(int wins, int n)
	{
		if (n == 0) { return null; } final double z = 1.959963984540054, p = wins / (double) n, d = 1 + z * z / n;
		final double center = (p + z * z / (2 * n)) / d, half = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d;
		return fields("lower", Math.max(0, center - half), "upper", Math.min(1, center + half), "nIndependentPairs", n);
	}
	static Double sign(int positive, int negative)
	{
		final int n = positive + negative; if (n == 0) { return null; } double probability = Math.scalb(1.0, -n), tail = probability;
		for (int k = 1; k <= Math.min(positive, negative); k++) { probability *= (n - k + 1.0) / k; tail += probability; }
		return Math.min(1, 2 * tail);
	}
	static byte[] encode(Arm arm)
	{
		try
		{
			final var bytes = new ByteArrayOutputStream(); final var out = new DataOutputStream(bytes); out.writeUTF("EVAL_ARM_V2");
			for (String s : List.of(arm.pairId(), arm.direction(), arm.side())) { out.writeUTF(s); } out.writeInt(arm.position()); out.writeLong(arm.seed());
			final var a = arm.authority(); for (String s : List.of(a.sourceSha(), a.buildSha(), a.configSha(), a.modelSha(), a.trainingStateSha())) { out.writeUTF(s); } out.writeLong(a.modelVersion());
			out.writeUTF(arm.physicalSha()); out.writeInt(arm.actors().size()); for (var id : arm.actors()) { identity(out, id); } out.writeBoolean(arm.target() != null); if (arm.target() != null) { identity(out, arm.target()); }
			out.writeBoolean(arm.nativeAdmitted()); out.writeBoolean(arm.afterCut()); out.writeBoolean(arm.cancelled()); out.writeLong(arm.cutNanos()); out.writeLong(arm.terminalNanos()); out.writeLong(arm.deadlineNanos());
			out.writeBoolean(arm.episode() != null); if (arm.episode() != null) { episode(out, arm.episode()); }
			out.writeInt(arm.paid().size()); for (var p : arm.paid()) { out.writeUTF(p.eventKey()); out.writeUTF(p.sourceSha()); out.writeBoolean(p.diverged()); out.writeDouble(p.hp()); out.writeDouble(p.mp()); }
			out.writeInt(arm.sourceHashes().size()); for (var entry : new TreeMap<>(arm.sourceHashes()).entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); }
			out.writeUTF(arm.stratum()); out.writeUTF(arm.refusal()); out.flush(); return bytes.toByteArray();
		} catch (IOException impossible) { throw new UncheckedIOException(impossible); }
	}
	static Arm decode(byte[] bytes)
	{
		if (bytes.length > 2_000_000) { throw new IllegalArgumentException("EVAL012_CODEC_BOUND"); }
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if (!in.readUTF().equals("EVAL_ARM_V2")) { throw new IllegalArgumentException("EVAL012_CODEC_VERSION"); }
			final String pair = in.readUTF(), direction = in.readUTF(), side = in.readUTF(); final int position = in.readInt(); final long seed = in.readLong();
			final String source = in.readUTF(), build = in.readUTF(), config = in.readUTF(), model = in.readUTF(), state = in.readUTF(); final long version = in.readLong();
			final var authority = new Authority(source, build, config, version, model, state); final String physical = in.readUTF(); final var actors = new ArrayList<Identity>();
			for (int count = count(in, 9); count > 0; count--) { actors.add(identity(in)); } final Identity target = in.readBoolean() ? identity(in) : null;
			final boolean admitted = in.readBoolean(), cut = in.readBoolean(), cancelled = in.readBoolean(); final long started = in.readLong(), terminal = in.readLong(), deadline = in.readLong();
			final var episode = in.readBoolean() ? episode(in) : null; final var paid = new ArrayList<Paid>();
			for (int count = count(in, 2048); count > 0; count--) { paid.add(new Paid(in.readUTF(), in.readUTF(), in.readBoolean(), in.readDouble(), in.readDouble())); }
			final var hashes = new TreeMap<String, String>(); for (int count = count(in, 256); count > 0; count--) { if (hashes.put(in.readUTF(), in.readUTF()) != null) { throw new IllegalArgumentException("EVAL012_CODEC_DUPLICATE"); } }
			final var arm = new Arm(pair, direction, side, position, seed, authority, physical, actors, target, admitted, cut, cancelled, started, terminal, deadline, episode, paid, hashes, in.readUTF(), in.readUTF());
			if (in.read() != -1 || !Arrays.equals(bytes, encode(arm))) { throw new IllegalArgumentException("EVAL012_CODEC_NONCANONICAL"); } return arm;
		} catch (IOException failure) { throw new IllegalArgumentException("EVAL012_CODEC_TRUNCATED", failure); }
	}
	static int count(DataInputStream in, int max) throws IOException { final int n = in.readInt(); if (n < 0 || n > max) { throw new IllegalArgumentException("EVAL012_CODEC_COUNT"); } return n; }
	static void identity(DataOutputStream out, Identity id) throws IOException { out.writeInt(id.objectId()); out.writeLong(id.epoch()); out.writeInt(id.instance()); }
	static Identity identity(DataInputStream in) throws IOException { return new Identity(in.readInt(), in.readLong(), in.readInt()); }
	static void nullableLong(DataOutputStream out, Long value) throws IOException { out.writeBoolean(value != null); if (value != null) { out.writeLong(value); } }
	static Long nullableLong(DataInputStream in) throws IOException { return in.readBoolean() ? in.readLong() : null; }
	static void episode(DataOutputStream out, TacticalEpisodeV3 e) throws IOException
	{
		out.writeUTF(e.id()); out.writeUTF(e.ownerNonce()); out.writeUTF(e.scope()); out.writeLong(e.startedNanos()); out.writeLong(e.settledNanos()); out.writeUTF(e.reason()); out.writeInt(e.actors().size());
		for (var a : e.actors()) { out.writeLong(a.profile()); out.writeInt(a.objectId()); out.writeLong(a.epoch()); out.writeInt(a.classId()); out.writeInt(a.level()); out.writeDouble(a.hp()); out.writeDouble(a.mp()); out.writeBoolean(a.dead()); nullableLong(out, a.exp()); nullableLong(out, a.sp()); }
		out.writeInt(e.kills().size()); for (var k : e.kills()) { out.writeInt(k.targetObjectId()); out.writeInt(k.npcId()); out.writeLong(k.spawnGeneration()); out.writeInt(k.instanceId()); out.writeInt(k.creditedActorId()); out.writeLong(k.nativeSequence()); }
		out.writeInt(e.eventKeys().size()); for (var key : e.eventKeys()) { out.writeUTF(key); } out.writeDouble(e.exactObservedDamage()); out.writeDouble(e.usefulObservedHeal()); out.writeDouble(e.exactObservedMp()); out.writeInt(e.observedDeaths());
	}
	static TacticalEpisodeV3 episode(DataInputStream in) throws IOException
	{
		final String id = in.readUTF(), owner = in.readUTF(), scope = in.readUTF(); final long started = in.readLong(), settled = in.readLong(); final String reason = in.readUTF(); final var actors = new ArrayList<TacticalEpisodeV3.Actor>();
		for (int n = count(in, 9); n > 0; n--) { actors.add(new TacticalEpisodeV3.Actor(in.readLong(), in.readInt(), in.readLong(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(), in.readBoolean(), nullableLong(in), nullableLong(in))); }
		final var kills = new ArrayList<TacticalEpisodeV3.Kill>(); for (int n = count(in, 16); n > 0; n--) { kills.add(new TacticalEpisodeV3.Kill(in.readInt(), in.readInt(), in.readLong(), in.readInt(), in.readInt(), in.readLong())); }
		final var events = new ArrayList<String>(); for (int n = count(in, 1024); n > 0; n--) { events.add(in.readUTF()); }
		return new TacticalEpisodeV3(id, owner, scope, started, settled, reason, actors, kills, events, in.readDouble(), in.readDouble(), in.readDouble(), in.readInt());
	}
	static void immutable(Path path, byte[] bytes) throws IOException
	{
		if (!path.toAbsolutePath().normalize().getParent().equals(path.toAbsolutePath().normalize().getParent().toRealPath())) { throw new IOException("EVAL012_PHYSICAL_OUTPUT"); }
		if (Files.exists(path)) { if (!path.equals(path.toRealPath()) || !Arrays.equals(Files.readAllBytes(path), bytes)) { throw new IOException("EVAL012_IMMUTABLE_CONFLICT"); } return; }
		Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
	}
	static void immutable(Path path, String text) throws IOException { immutable(path, text.getBytes(StandardCharsets.UTF_8)); }
	private PhantomEvaluationOutcomeV2() { }
}
