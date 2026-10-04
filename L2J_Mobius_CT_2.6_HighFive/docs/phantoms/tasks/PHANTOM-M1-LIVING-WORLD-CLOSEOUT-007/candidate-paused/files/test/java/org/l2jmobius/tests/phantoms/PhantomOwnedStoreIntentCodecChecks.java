package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Pure codec conservation controls; this does not prove native SQL/farming GREEN. */
public final class PhantomOwnedStoreIntentCodecChecks implements PhantomTestSuite
{
	private static final int POS1 = 0x504f5331;
	private static final int POZ1 = 0x504f5a31;
	private static final String HASH = "a".repeat(64);
	@Override public String id() { return "owned-store-intent-codec"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-component-bound-and-lossless-legacy", _ -> conservation());
		registry.add("02-strict-malformed-and-inflation-bound", _ -> malformed());
		registry.add("03-original-intent-guards", _ -> guards());
	}
	public static void main(String[] args) throws Exception
	{
		conservation(); malformed(); guards();
		System.out.println("OWNED_STORE_INTENT_CODEC_PASS groups=3 nativeSqlAndWorld=REQUIRED");
	}

	private static void conservation() throws Exception
	{
		final var large = intent(state(false, true), state(true, true));
		final byte[] legacyLarge = legacy(large);
		PhantomAssertions.assertTrue(legacyLarge.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Large pure fixture did not reproduce the native receipt envelope defect.");
		final byte[] encoded = large.encode();
		PhantomAssertions.assertTrue(encoded.length <= PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Owned receipt exceeds actual4096 component/SQL bound: " + encoded.length);
		PhantomAssertions.assertEquals(POZ1, ByteBuffer.wrap(encoded).getInt(), "Large receipt did not use documented compact format.");
		PhantomAssertions.assertEquals(large, PhantomOwnedStoreIntent.decode(encoded), "Lossless compact receipt changed exact fields/identity/epoch/hashes.");
		PhantomAssertions.assertTrue(Arrays.equals(encoded, large.encode()), "Compact output is nondeterministic.");
		final var small = intent(state(false, false), state(true, false));
		final byte[] old = legacy(small);
		PhantomAssertions.assertTrue(old.length <= 4096, "Legacy compatibility fixture exceeds durable component bound.");
		PhantomAssertions.assertTrue(Arrays.equals(old, small.encode()), "Small receipt changed existing POS1 bytes unnecessarily.");
		PhantomAssertions.assertEquals(small, PhantomOwnedStoreIntent.decode(old), "Existing durable POS1 receipt is not readable.");
		PhantomAssertions.assertEquals(1, PhantomOwnedStoreIntent.SCHEMA_VERSION, "Internal compression changed component schema.");
		final var excessive = intent(entropicState(31), entropicState(32));
		final var tooLarge = PhantomAssertions.assertThrows(IllegalArgumentException.class, excessive::encode, "Incompressible valid-model receipt must fail before SQL rather than exceed4096.");
		PhantomAssertions.assertEquals("OWNED_STORE_INTENT_TOO_LARGE", tooLarge.getMessage(), "Oversized compact output failed through an unrelated fixture/codec precondition.");
		System.out.println("OWNED_STORE_INTENT_CODEC_BYTES legacyLarge=" + legacyLarge.length + " compact=" + encoded.length + " legacySmall=" + old.length);
	}

	private static void malformed() throws Exception
	{
		final byte[] raw = legacy(intent(state(false, true), state(true, true)));
		final byte[] compact = compressed(raw, raw.length, false);
		PhantomAssertions.assertEquals(intent(state(false, true), state(true, true)), PhantomOwnedStoreIntent.decode(compact), "Valid confirmed zlib fixture was rejected.");
		rejected(Arrays.copyOf(compact, compact.length - 1), "truncated zlib");
		rejected(Arrays.copyOf(compact, compact.length + 1), "compressed trailing byte");
		rejected(Arrays.copyOf(compact, 4097), "compact input above actual component bound");
		final byte[] corrupt = compact.clone(); corrupt[corrupt.length - 1] ^= 1; rejected(corrupt, "corrupt checksum");
		rejected(compressed(raw, raw.length - 1, false), "declared size too small");
		rejected(compressed(raw, raw.length + 1, false), "declared size too large");
		rejected(compressed(new byte[100_000], 8449, false), "declared expansion above cap");
		rejected(compressed(new byte[100_000], 8448, false), "actual expansion above cap");
		rejected(compressed(raw, raw.length, true), "dictionary required");
		rejected(compressed(Arrays.copyOf(raw, raw.length + 1), raw.length + 1, false), "inflated POS1 trailing byte");
		final byte[] unknown = compact.clone(); unknown[0] ^= 1; rejected(unknown, "unknown format");
		rejected(new byte[4097], "oversized or malformed durable receipt");
		rejected(new byte[0], "empty receipt");
		rejected(null, "null receipt");
		final byte[] duplicate = new byte[compact.length * 2 - 8];
		System.arraycopy(compact, 0, duplicate, 0, compact.length);
		System.arraycopy(compact, 8, duplicate, compact.length, compact.length - 8);
		rejected(duplicate, "concatenated zlib streams");
	}

	private static void guards() throws Exception
	{
		final var before = state(false, false); final var after = state(true, false);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomOwnedStoreIntent(-1, 42, State.READY, "READY", HASH, HASH, before, after), "Negative row version accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomOwnedStoreIntent(7, 42, State.DEAD, "READY", HASH, HASH, before, after), "DEAD target with positive HP accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomOwnedStoreIntent(7, 42, State.READY, "VERIFY_PENDING", HASH, HASH, before, after), "Unproved predecessor accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomOwnedStoreIntent(7, 42, State.READY, "READY", "foreign", HASH, before, after), "Invalid hash accepted.");
		final byte[] wrongVersion = legacy(intent(before, after)); ByteBuffer.wrap(wrongVersion).putLong(4, -1); rejected(wrongVersion, "encoded invalid row version");
		final byte[] invalidTarget = legacy(intent(before, after)); invalidTarget[20] = (byte) 255; rejected(invalidTarget, "encoded target ordinal");
		final var foreign = new PhantomBackgroundState(after.state(), new Identity(2, 102, 0, 18, 1), after.progress(), after.vitals(), after.position(), after.combat(), after.loadout(), after.inventory(), after.autoGetSkills(), after.clock(), after.receipt(), after.hashes());
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> intent(before, foreign), "Foreign AFTER actor accepted.");
	}

	private static PhantomOwnedStoreIntent intent(PhantomBackgroundState before, PhantomBackgroundState after) { return new PhantomOwnedStoreIntent(7, 42, State.READY, "MATERIALIZED", HASH, "b".repeat(64), before, after); }
	private static PhantomBackgroundState state(boolean after, boolean large)
	{
		final List<Integer> ids = large ? java.util.stream.IntStream.range(1000, 1096).boxed().toList() : List.of(1000);
		final List<ItemObject> objects = new ArrayList<>();
		for (int i = 0; i < (large ? 120 : 1); i++) { objects.add(new ItemObject(2000 + i, ids.get(i % ids.size()), 10L + i + (after ? 3 : 0), true, ItemLocation.INVENTORY)); }
		final List<AutoGetSkill> skills = large ? java.util.stream.IntStream.range(1, 65).mapToObj(i -> new AutoGetSkill(i, after ? 2 : 1)).toList() : List.of(new AutoGetSkill(1, 1));
		return new PhantomBackgroundState(State.READY, new Identity(1, 101, 0, 18, 1), new Progress(after ? 12 : 11, after ? 120000 : 100000, after ? 300 : 200, 19), new Vitals(after ? 95 : 90, 100, after ? 45 : 40, 60, after ? 15 : 10, 20), new Position(0, after ? 900 : 800, 901, -100, 777, large ? "anchor".repeat(20) : "anchor"), new CombatFacts(ModelKind.MAGIC, 12, 13, 14, 15, 200, 300, 2, 3, 1, 1, 1, 1, 1, 1, 0.05), new Loadout(1, 1, 0, 3, 0, 0, 0, 0), new InventoryFacts(ids, objects, HASH, after ? 2000 : 1900, 9000, objects.size(), 160), skills, new Clock(after ? 55 : 54, 12, 13), new Receipt(large ? "r".repeat(128) : "", 9, after ? 11 : 10, HASH), new Hashes(HASH, "b".repeat(64), "c".repeat(64), "d".repeat(64)));
	}
	private static byte[] legacy(PhantomOwnedStoreIntent intent) throws Exception
	{
		final var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes))
		{
			out.writeInt(POS1); out.writeLong(intent.preparedRowVersion()); out.writeLong(intent.materializedAtNanos()); out.writeByte(intent.targetState().ordinal()); out.writeUTF(intent.previousState()); out.writeUTF(intent.previousPayloadHash()); out.writeUTF(intent.skillsHash());
			for (var state : List.of(intent.before(), intent.after())) { final byte[] payload = new PhantomBackgroundStateCodec().encode(state); out.writeInt(payload.length); out.write(payload); }
		}
		return bytes.toByteArray();
	}
	private static PhantomBackgroundState entropicState(long seed)
	{
		final var random = new java.util.Random(seed); final var base = state(false, false);
		final var unique = new java.util.TreeSet<Integer>();
		while (unique.size() < 96) { unique.add(random.nextInt(1, Integer.MAX_VALUE)); }
		final List<Integer> ids = List.copyOf(unique); final List<ItemObject> objects = new ArrayList<>();
		for (int i = 0; i < 100; i++) { objects.add(new ItemObject(((i + 1) * 16_000_000) + random.nextInt(15_999_999), ids.get(random.nextInt(ids.size())), random.nextLong(1, Long.MAX_VALUE), true, ItemLocation.INVENTORY)); }
		final var skills = java.util.stream.IntStream.range(1, 65).mapToObj(i -> new AutoGetSkill(i, random.nextInt(1, 65536))).toList();
		return new PhantomBackgroundState(base.state(), base.identity(), base.progress(), base.vitals(), new Position(0, random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(Integer.MAX_VALUE), randomText(random)), base.combat(), base.loadout(), new InventoryFacts(ids, objects, HASH, 12, 9000, 100, 160), skills, new Clock(random.nextLong(), 12, 13), new Receipt(randomText(random), 9, 10, randomText(random)), new Hashes(randomText(random), randomText(random), randomText(random), randomText(random)));
	}
	private static String randomText(java.util.Random random) { final var text = new StringBuilder(128); for (int i = 0; i < 128; i++) { text.append((char) random.nextInt(33, 127)); } return text.toString(); }
	private static byte[] compressed(byte[] raw, int declaredLength, boolean dictionary) throws Exception
	{
		final var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes); var deflater = new Deflater())
		{
			out.writeInt(POZ1); out.writeInt(declaredLength);
			if (dictionary) { deflater.setDictionary(new byte[] { 1, 2, 3 }); }
			deflater.setInput(raw); deflater.finish(); final byte[] buffer = new byte[512];
			while (!deflater.finished()) { final int count = deflater.deflate(buffer); PhantomAssertions.assertTrue(count > 0, "TEST compressor stalled."); out.write(buffer, 0, count); }
		}
		return bytes.toByteArray();
	}
	private static void rejected(byte[] payload, String reason) { PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomOwnedStoreIntent.decode(payload), "Malformed receipt accepted: " + reason); }
}
