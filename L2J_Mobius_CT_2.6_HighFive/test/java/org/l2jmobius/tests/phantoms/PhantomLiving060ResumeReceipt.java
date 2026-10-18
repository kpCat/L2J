/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** One-use TEST receipt between ordinary, separately launched058t JVMs; never edits game state. */
final class PhantomLiving060ResumeReceipt
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_058t", OWNER = "TASK060_LIVING_RESUME";
	private static final Set<String> KEYS = Set.of("format", "owner", "schema", "phase", "receiptId", "account", "name", "objectId", "skillId", "itemBaseline", "profileId", "rootId", "rootRevision", "rootHash", "childHash", "paymentKey", "jarHash", "exp", "sp", "adena", "stock", "x", "y", "z", "startExp", "startSp", "pid", "vmStart");
	record Saved(String receiptId, PhantomHeadlessPlayerFixture fixture, long profileId, long rootId, long rootRevision, String rootHash, String childHash, String paymentKey, String jarHash, long exp, long sp, long adena, long stock, int x, int y, int z, long startExp, long startSp, long pid, long vmStart)
	{
		Saved
		{
			if (receiptId == null || !receiptId.matches("living-resume060-[1-9][0-9]?") || fixture == null || !fixture.accountName().matches("[A-Za-z0-9_]{1,45}") || !fixture.characterName().matches("[A-Za-z0-9_]{1,35}") || fixture.objectId() <= 0 || fixture.skillId() < 0 || fixture.fixtureItemBaseline() < 0 || profileId <= 705 || rootId <= 0 || rootRevision < 0 || exp <= startExp || sp <= startSp || startExp < 0 || startSp < 0 || adena < 0 || stock < 1 || stock > 600 || pid <= 0 || vmStart <= 0) { throw new IllegalArgumentException("Invalid own060 resume receipt identity/earned bounds."); }
			for (String hash : new String[] {rootHash, childHash, paymentKey, jarHash}) { if (hash == null || !hash.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid060 resume hash."); } }
			new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, 0);
		}
	}
	static String receiptId() { return System.getProperty("phantom.test.livingResumeReceipt060", "living-resume060-1"); }
	private static long vmStart() { return ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli(); }
	private static String hash(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
	private static String jarHash() throws Exception
	{
		final Path jar = Path.of(System.getProperty("phantom.contract058.semantic.jar"));
		if (!Files.isRegularFile(jar) || Files.size(jar) > 64 * 1024 * 1024) { throw new IllegalArgumentException("Invalid current060 semantic JAR."); }
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
	}
	private static Path path(PhantomTestContext context) throws Exception
	{
		if (!receiptId().matches("living-resume060-[1-9][0-9]?")) { throw new IllegalArgumentException("Invalid060 receipt name."); }
		final Path base = context.moduleRoot().toAbsolutePath().normalize();
		if (!base.equals(Path.of("D:/Codex/worktrees/phantom-dev/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive")) || !base.toRealPath().equals(base)) { throw new IllegalArgumentException("Resume requires exact OWN."); }
		final Path parent = base.resolve(".phantom-local/contract058a/task060");
		if (!parent.toRealPath().equals(parent)) { throw new IllegalArgumentException("Resume receipt alias forbidden."); }
		return parent.resolve(receiptId() + ".properties");
	}
	static void requireAbsent(PhantomTestContext context) throws Exception
	{
		if (Files.exists(path(context)) || Files.exists(path(context).resolveSibling(receiptId() + ".pending"))) { throw new IllegalStateException("Resume evidence already exists; no replay/reset."); }
	}
	static Saved capture(PhantomTestContext context, PhantomHeadlessPlayerFixture fixture, long profileId, PhantomProfileRepository profiles, Player player, long startExp, long startSp, int itemId) throws Exception
	{
		final var root = new PhantomGoalStateStore(profiles).load(profileId).orElseThrow().goal();
		final var component = profiles.findComponent(profileId, PhantomSupplyIntentStore.COMPONENT).orElseThrow();
		final var child = new PhantomGoalStateCodec().decode(component.payload());
		final var payment = new PhantomCommerceReceiptStore(profiles).find(profileId).orElseThrow().receipt();
		PhantomAssertions.assertTrue(child.status() == PhantomGoalStatus.ACTIVE && PhantomSupplyIntentStore.bound(child, root) && payment.state() == PhantomCommerceReceipt.State.COMMITTED && payment.goalId() == child.goalId() && payment.goalRevision() == child.revision() && payment.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && payment.request().itemId() == itemId, "Only an exact paid active return intent may survive this TEST phase.");
		return new Saved(receiptId(), fixture, profileId, root.goalId(), root.revision(), hash(new PhantomGoalStateCodec().encode(root)), hash(component.payload()), payment.operationKey(), jarHash(), player.getExp(), player.getSp(), player.getAdena(), player.getInventory().getInventoryItemCount(itemId, -1), player.getX(), player.getY(), player.getZ(), startExp, startSp, ProcessHandle.current().pid(), vmStart());
	}
	static void write(PhantomTestContext context, Saved saved) throws Exception
	{
		requireAbsent(context);
		final Path target = path(context), pending = target.resolveSibling(receiptId() + ".pending");
		Files.writeString(pending, text(properties(saved)), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
		context.record("life060.resumeSaved", "ordinary owners stopped; one-use receipt=" + target + "; profile=" + saved.profileId() + "; object=" + saved.fixture().objectId() + "; EXP/SP=" + saved.exp() + '/' + saved.sp() + "; Adena/stock=" + saved.adena() + '/' + saved.stock() + "; payment=" + saved.paymentKey() + "; JVM=" + saved.pid() + '/' + saved.vmStart());
	}
	static Saved read(PhantomTestContext context, PhantomProfileRepository profiles) throws Exception
	{
		final Path source = path(context);
		if (!Files.isRegularFile(source) || Files.size(source) > 4096 || !source.toRealPath().equals(source)) { throw new IllegalArgumentException("Missing/bounded exact resume receipt required."); }
		final Properties properties = new Properties(); properties.load(new StringReader(Files.readString(source, StandardCharsets.UTF_8)));
		final Saved saved = parse(properties, jarHash());
		if (!saved.receiptId().equals(receiptId()) || saved.pid() == ProcessHandle.current().pid() && saved.vmStart() == vmStart()) { throw new IllegalArgumentException("Resume requires another JVM incarnation and exact receipt name."); }
		final var profile = profiles.find(saved.profileId()).orElseThrow();
		final var root = new PhantomGoalStateStore(profiles).load(saved.profileId()).orElseThrow().goal();
		final var component = profiles.findComponent(saved.profileId(), PhantomSupplyIntentStore.COMPONENT).orElseThrow();
		final var child = new PhantomGoalStateCodec().decode(component.payload());
		final var payment = new PhantomCommerceReceiptStore(profiles).find(saved.profileId()).orElseThrow().receipt();
		final var binding = PhantomSupplyIntentStore.Binding.parse(child);
		PhantomAssertions.assertTrue(profile.characterObjectId() == saved.fixture().objectId() && root.goalId() == saved.rootId() && root.revision() == saved.rootRevision() && hash(new PhantomGoalStateCodec().encode(root)).equals(saved.rootHash()) && hash(component.payload()).equals(saved.childHash()) && PhantomSupplyIntentStore.bound(child, root) && child.status() == PhantomGoalStatus.ACTIVE && child.deadlineEpochMillis() > System.currentTimeMillis() && (binding.classId() == 0 || binding.classId() == 53) && binding.classIndex() == 0 && binding.actorObjectId() == saved.fixture().objectId() && payment.state() == PhantomCommerceReceipt.State.COMMITTED && payment.goalId() == child.goalId() && payment.goalRevision() == child.revision() && payment.operationKey().equals(saved.paymentKey()), "Saved060 profile/root/paid intent authority changed; no recreation or deadline extension.");
		return saved;
	}
	static void verifyLoaded(PhantomTestContext context, Saved saved, Player player, int itemId, int classId)
	{
		PhantomAssertions.assertTrue(player.getObjectId() == saved.fixture().objectId() && player.getAccountName().equals(saved.fixture().accountName()) && player.getName().equals(saved.fixture().characterName()) && player.getActiveClass() == classId && player.getClassIndex() == 0 && player.getInstanceId() == 0 && player.getX() == saved.x() && player.getY() == saved.y() && Math.abs((long) player.getZ() - saved.z()) <= 50 && player.getExp() == saved.exp() && player.getSp() == saved.sp() && player.getAdena() == saved.adena() && player.getInventory().getInventoryItemCount(itemId, -1) == saved.stock(), "Ordinary materialization changed saved060 identity/position/earned/funds/stock before continuation.");
		context.record("life060.resumeLoaded", "same saved actor/root/child/payment; oldJVM=" + saved.pid() + '/' + saved.vmStart() + "; currentJVM=" + ProcessHandle.current().pid() + '/' + vmStart() + "; EXP/SP=" + player.getExp() + '/' + player.getSp() + "; Adena/stock=" + player.getAdena() + '/' + saved.stock() + "; native XYZ=" + player.getX() + ',' + player.getY() + ',' + player.getZ() + "; no TEST resources/goals/XYZ/spawns on continuation");
	}
	static void consumed(PhantomTestContext context, Saved saved) throws Exception
	{
		final Path target = path(context), pending = target.resolveSibling(receiptId() + ".pending");
		final Properties properties = new Properties(); properties.load(new StringReader(Files.readString(target, StandardCharsets.UTF_8)));
		if (!parse(properties, saved.jarHash()).equals(saved)) { throw new IllegalStateException("Resume receipt changed; completion refused."); }
		properties.setProperty("phase", "CONSUMED");
		Files.writeString(pending, text(properties), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		context.record("life060.resumeConsumed", "same058t DB/JAR; two ordinary JVMs; prior payment key=" + saved.paymentKey() + "; one-use receipt consumed after saved new useful farm");
	}
	private static Properties properties(Saved s)
	{
		final Properties p = new Properties();
		final String[] values = {"1", OWNER, SCHEMA, "SAVED", s.receiptId(), s.fixture().accountName(), s.fixture().characterName(), Integer.toString(s.fixture().objectId()), Integer.toString(s.fixture().skillId()), Long.toString(s.fixture().fixtureItemBaseline()), Long.toString(s.profileId()), Long.toString(s.rootId()), Long.toString(s.rootRevision()), s.rootHash(), s.childHash(), s.paymentKey(), s.jarHash(), Long.toString(s.exp()), Long.toString(s.sp()), Long.toString(s.adena()), Long.toString(s.stock()), Integer.toString(s.x()), Integer.toString(s.y()), Integer.toString(s.z()), Long.toString(s.startExp()), Long.toString(s.startSp()), Long.toString(s.pid()), Long.toString(s.vmStart())};
		final String[] keys = {"format", "owner", "schema", "phase", "receiptId", "account", "name", "objectId", "skillId", "itemBaseline", "profileId", "rootId", "rootRevision", "rootHash", "childHash", "paymentKey", "jarHash", "exp", "sp", "adena", "stock", "x", "y", "z", "startExp", "startSp", "pid", "vmStart"};
		for (int index = 0; index < keys.length; index++) { p.setProperty(keys[index], values[index]); } return p;
	}
	private static String text(Properties p) { final var value = new StringBuilder(); for (String key : new TreeSet<>(p.stringPropertyNames())) { value.append(key).append('=').append(p.getProperty(key)).append('\n'); } return value.toString(); }
	private static long number(Properties p, String key) { return Long.parseLong(p.getProperty(key)); }
	private static Saved parse(Properties p, String expectedJar)
	{
		if (!p.stringPropertyNames().equals(KEYS) || !"1".equals(p.getProperty("format")) || !OWNER.equals(p.getProperty("owner")) || !SCHEMA.equals(p.getProperty("schema")) || !"SAVED".equals(p.getProperty("phase")) || !expectedJar.equals(p.getProperty("jarHash"))) { throw new IllegalArgumentException("Unknown/wrong owner/schema/phase/JAR resume receipt."); }
		return new Saved(p.getProperty("receiptId"), new PhantomHeadlessPlayerFixture(p.getProperty("account"), p.getProperty("name"), Math.toIntExact(number(p, "objectId")), Math.toIntExact(number(p, "skillId")), number(p, "itemBaseline")), number(p, "profileId"), number(p, "rootId"), number(p, "rootRevision"), p.getProperty("rootHash"), p.getProperty("childHash"), p.getProperty("paymentKey"), p.getProperty("jarHash"), number(p, "exp"), number(p, "sp"), number(p, "adena"), number(p, "stock"), Math.toIntExact(number(p, "x")), Math.toIntExact(number(p, "y")), Math.toIntExact(number(p, "z")), number(p, "startExp"), number(p, "startSp"), number(p, "pid"), number(p, "vmStart"));
	}
	static void models(PhantomTestContext context) throws Exception
	{
		final String hash = "a".repeat(64);
		final var saved = new Saved("living-resume060-1", new PhantomHeadlessPlayerFixture("Task060", "Task060", 268663999, 0, 0), 999, 123, 1, hash, hash, hash, hash, 100, 10, 9000, 120, 10000, 10000, 0, 50, 0, 1, 1);
		final Properties roundtrip = new Properties(); roundtrip.load(new StringReader(text(properties(saved))));
		PhantomAssertions.assertEquals(saved, parse(roundtrip, hash), "Resume receipt roundtrip changed its ownership or earned fence.");
		for (String key : new String[] {"owner", "schema", "phase", "jarHash", "unknown"})
		{
			final var invalid = properties(saved); invalid.setProperty(key, key.equals("schema") ? "l2jmobiush5_phantom_release_059a" : key.equals("phase") ? "CONSUMED" : "changed");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> parse(invalid, hash), "Wrong resume receipt authority accepted: " + key);
		}
		context.record("life060.resumeReceiptModels", "PURE models only; exact keys/version/058t/owner/JAR/phase/identity/earned bounds; consumed replay rejected; no game rows/files written");
	}
}
