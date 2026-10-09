/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;

/** Causal serialization regression from the exact private actor1149 guard fixture; no DB writes. */
public final class PhantomInventoryCapacity031Suite implements PhantomTestSuite
{
    private final byte[] original;
    private final PhantomBackgroundStateCodec codec = new PhantomBackgroundStateCodec();
    private final PhantomBackgroundState baseline;
    public PhantomInventoryCapacity031Suite(Path fixture) throws Exception
    {
        if (Files.size(fixture) > 8194) { throw new IllegalArgumentException("Bounded private fixture required"); }
        original = java.util.HexFormat.of().parseHex(Files.readString(fixture).strip());
        baseline = codec.decode(original);
    }
    public static void main(String[] args) throws Exception
    {
        var context = new PhantomTestContext(31003102, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("inventory-capacity031", new PhantomInventoryCapacity031Suite(Path.of(args[2])), context));
    }
    @Override public String id() { return "inventory-capacity031"; }
    @Override public void register(PhantomTestRegistry registry)
    {
        registry.add("C01-legacy-byte-and-pnc-binding-parity", context ->
        {
            PhantomAssertions.assertEquals(1149L, baseline.identity().profileId(), "Wrong causal actor fixture.");
            PhantomAssertions.assertEquals(157, baseline.inventory().objects().size(), "Wrong pre-failure inventory projection.");
            PhantomAssertions.assertTrue(Arrays.equals(original, codec.encode(baseline)), "Existing payload/PNC hash binding changed.");
            context.record("legacy.bytes", original.length);
            int count=0, byteParity=0;
            try (var rows=Files.lines(context.moduleRoot().resolve(".phantom-local/contract031a/preboot-durable-hex.tsv")))
            {
                var iterator=rows.iterator();
                while(iterator.hasNext())
                {
                    var parts=iterator.next().split("\t");
                    if(parts.length!=4 || !parts[2].equals("background.state")) { continue; }
                    var bytes=java.util.HexFormat.of().parseHex(parts[3]);
                    var decoded=codec.decode(bytes); count++;
                    if(bytes[5]==2 && bytes[7]==2)
                    {
                        PhantomAssertions.assertTrue(Arrays.equals(bytes,codec.encode(decoded)),"Existing v2 PNC payload binding changed for profile="+parts[0]); byteParity++;
                    }
                }
            }
            PhantomAssertions.assertEquals(10000,count,"Incomplete immutable preboot parity inventory.");
            context.record("preboot.decoded",count); context.record("preboot.v2ByteParity",byteParity);
        });
        registry.add("C02-exact-157-plus8-native-capacity-roundtrip", context ->
        {
            var objects = new ArrayList<>(baseline.inventory().objects());
            var reward = objects.stream().filter(o -> o.location() == ItemLocation.INVENTORY && !o.stackable()).findFirst().orElseThrow();
            int next = objects.getLast().objectId();
            for (int n=1; n<=8; n++) { objects.add(new ItemObject(next+n, reward.itemId(), 1, false, ItemLocation.INVENTORY)); }
            var source = baseline.inventory();
            var inventory = new InventoryFacts(source.mutableItemIds(), objects, source.canonicalHash(), source.currentLoad(), source.maximumLoad(), 166, 250);
            var expanded = withInventory(inventory);
            var payload = codec.encode(expanded);
            PhantomAssertions.assertTrue(payload.length <= 4096, "Original durable payload limit changed.");
            PhantomAssertions.assertEquals(expanded, codec.decode(payload), "Compact representation lost exact item objects/counts.");
            PhantomAssertions.assertTrue(Arrays.equals(payload, codec.encode(codec.decode(payload))), "Compact bytes not canonical.");
            context.record("expanded.objects", objects.size()); context.record("expanded.bytes", payload.length);
        });
        registry.add("C03-hard-payload-and-native-capacity-bounds", context ->
        {
            var objects = new ArrayList<ItemObject>();
            for (int n=1; n<=250; n++) { objects.add(new ItemObject(100000+n, Integer.MAX_VALUE, Long.MAX_VALUE, true, ItemLocation.INVENTORY)); }
            for (int n=251; n<=275; n++) { objects.add(new ItemObject(100000+n, Integer.MAX_VALUE, 1, false, ItemLocation.PAPERDOLL)); }
            var oversized = new InventoryFacts(List.of(Integer.MAX_VALUE), objects, "", 0, 1000, 250, 250);
            var payload = codec.encode(withInventory(oversized));
            PhantomAssertions.assertTrue(payload.length <= 4096, "Compact storage exceeded original4096 byte limit.");
            PhantomAssertions.assertEquals(withInventory(oversized), codec.decode(payload), "Full250+25 projection lost objects.");
            rejected(() -> codec.decode(Arrays.copyOf(payload,4097)), "Decoder widened durable4096 byte bound.");
            var forged = payload.clone(); java.nio.ByteBuffer.wrap(forged).putInt(8,8193);
            rejected(() -> codec.decode(forged), "Declared expansion bypassed8192 byte bound.");
            rejected(() -> codec.decode(Arrays.copyOf(payload,payload.length+1)), "Trailing compressed bytes accepted.");
            objects.add(new ItemObject(100276, Integer.MAX_VALUE, 1, false, ItemLocation.PAPERDOLL));
            rejected(() -> new InventoryFacts(List.of(Integer.MAX_VALUE), objects, "", 0, 1000, 250, 250), "Native representation bound expanded silently.");
        });
    }
    private static void rejected(Runnable action, String message)
    {
        boolean caught=false;
        try { action.run(); } catch (IllegalArgumentException expected) { caught=true; }
        PhantomAssertions.assertTrue(caught,message);
    }
    private PhantomBackgroundState withInventory(InventoryFacts inventory)
    {
        return new PhantomBackgroundState(baseline.state(),baseline.identity(),baseline.progress(),baseline.vitals(),baseline.position(),baseline.combat(),baseline.loadout(),inventory,baseline.autoGetSkills(),baseline.clock(),baseline.receipt(),baseline.hashes());
    }
}
