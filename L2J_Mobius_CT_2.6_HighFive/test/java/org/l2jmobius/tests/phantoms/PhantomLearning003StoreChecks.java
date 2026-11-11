/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Pure durable codec/split contracts. Fabricated receipts here never enter SQL or a native dataset. */
public final class PhantomLearning003StoreChecks
{
    private static Class<?> store, row;
    public static void main(String[] args) throws Exception
    {
        try { store = Class.forName("org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("V3 durable receipt codec/split admission is missing", missing); }
        row = Class.forName(store.getName() + "$Row");
        codec(); splits(); quality(); explicitAdmission();
        System.out.println("LEARNING003_STORE_CHECKS PASS codec=1 split=1 quality=1 explicitApply=1 origin=SYNTHETIC DB_CONNECTIONS=0");
    }
    private static Object sample(long actor, String split, String context, String event) throws Exception
    {
        final var facts = Map.of(Field.HP, Fact.known(.5), Field.MAX_HP, Fact.known(2000));
        final var self = new Entity(Role.SELF, (int) actor, 100, 0, 97, facts, List.of(), List.of(), List.of(), List.of());
        final var first = new Entity(Role.ALLY, 200, 101, 0, 90, facts, List.of(), List.of(), List.of(), List.of());
        final var second = new Entity(Role.ALLY, 201, 102, 0, 94, facts, List.of(), List.of(), List.of(), List.of());
        final var observation = new TacticalObservationV3(1, 10, actor, (int) actor, 100, "party", List.of(self, first, second));
        final var a = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", actor, (int) actor, 100, 1, 200, 101, "party", 1401, 11, 80, 1000, 600, false, true);
        final var b = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", actor, (int) actor, 100, 1, 201, 102, "party", 1401, 11, 80, 1000, 600, false, true);
        final var attempt = new HealEvidenceV3.Attempt(observation, List.of(a, b), a, b, 0, _ -> { });
        final var receipt = new HealEvidenceV3.Receipt(event.repeat(64), attempt, 500, 1000, 500, 80, 1000000000, .16f,
            new HealEvidenceV3.SourceOwner(712, "party.fixture.support.lease"));
        return row.getConstructor(String.class, String.class, HealEvidenceV3.Receipt.class).newInstance(split, context, receipt);
    }
    private static Object call(String method, Class<?>[] types, Object... args) throws Exception
    {
        try { return store.getMethod(method, types).invoke(null, args); }
        catch (InvocationTargetException failure)
        {
            if (failure.getCause() instanceof RuntimeException cause) { throw cause; }
            throw failure;
        }
    }
    private static void codec() throws Exception
    {
        final var original = sample(10, "TRAIN", "native-context-A", "a");
        final byte[] bytes = (byte[]) call("encode", new Class<?>[] {row}, original);
        final Object restored = call("decode", new Class<?>[] {byte[].class}, bytes);
        check("TRAIN".equals(row.getMethod("split").invoke(restored)), "split lost in receipt codec");
        check("native-context-A".equals(row.getMethod("contextKey").invoke(restored)), "actual context lost in receipt codec");
        final var receipt = (TacticalReceiptV3) row.getMethod("receipt").invoke(restored);
        check(receipt.owner().generation() == 712 && receipt.owner().operationKey().equals("party.fixture.support.lease"),
            "exact external support owner was lost from durable receipt");
        check(receipt.actualHp() == 500 && receipt.actualMp() == 80 && receipt.beforeHp() == 500 && receipt.afterHp() == 1000
            && receipt.attempt().offered().size() == 2 && receipt.attempt().baseline().targetObjectId() == 201
            && receipt.attempt().observation().self().classId() == 97, "rich native receipt changed");
        check(Arrays.equals(bytes, (byte[]) call("encode", new Class<?>[] {row}, restored)), "noncanonical codec");
        final byte[] bad = bytes.clone(); bad[0] ^= 1;
        reject(() -> call("decode", new Class<?>[] {byte[].class}, bad), "unknown codec accepted");
        reject(() -> call("decode", new Class<?>[] {byte[].class}, Arrays.copyOf(bytes, bytes.length - 1)), "truncation accepted");
        final byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        reject(() -> call("decode", new Class<?>[] {byte[].class}, trailing), "trailing bytes accepted");
    }
    private static void splits() throws Exception
    {
        final Object a = sample(10, "TRAIN", "scene-A", "a"), b = sample(11, "TUNING", "scene-B", "b"),
            c = sample(12, "AUDIT", "scene-C", "c"), d = sample(13, "AUDIT", "scene-D", "d");
        call("validateDataset", new Class<?>[] {List.class}, List.of(a,b,c,d));
        reject(() -> call("validateDataset", new Class<?>[] {List.class}, List.of(a,a)), "duplicate native credit accepted");
        final Object overlap = sample(10, "AUDIT", "scene-E", "e");
        reject(() -> call("validateDataset", new Class<?>[] {List.class}, List.of(a,overlap)), "actor leaked across splits");
        final Object context = sample(14, "AUDIT", "scene-A", "f");
        reject(() -> call("validateDataset", new Class<?>[] {List.class}, List.of(a,context)), "actual scene leaked across splits");
    }
    private static void quality() throws Exception
    {
        final Class<?> trainer = Class.forName("org.l2jmobius.gameserver.phantoms.learning.TacticalTrainerV3");
        final var quality = trainer.getMethod("qualityPasses", double.class, double.class, Double.class);
        check((boolean) quality.invoke(null, .04, .10, null), "held-out improvement rejected");
        check(!(boolean) quality.invoke(null, .10, .10, null), "unchanged constant accepted");
        check(!(boolean) quality.invoke(null, .04, .10, .03), "prior regression accepted");
        check(!(boolean) quality.invoke(null, Double.NaN, .10, null), "nonfinite accepted");
        check(!(boolean) quality.invoke(null, 0, 0, null), "zero-information audit accepted");
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void explicitAdmission() throws Exception
    {
        final java.lang.reflect.Method admission;
        try { admission = store.getMethod("admissionPermitted", String.class, String.class, String.class, String.class,
            int.class, double.class, double.class, Double.class, boolean.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Explicit TEST apply admission is missing", missing); }
        final String schema = TacticalObservationV3.SCHEMA, sha = "a".repeat(64);
        check(!(boolean) admission.invoke(null,"DRAFT",schema,sha,sha,3,.04,.10,null,false), "DRAFT cold-loaded without explicit apply");
        check((boolean) admission.invoke(null,"DRAFT",schema,sha,sha,3,.04,.10,null,true), "Qualified explicit TEST candidate rejected");
        check((boolean) admission.invoke(null,"ACCEPTED_TEST",schema,sha,sha,3,.04,.10,null,false), "Explicitly accepted exact checkpoint rejected");
        check(!(boolean) admission.invoke(null,"DRAFT",schema,sha,"b".repeat(64),3,.04,.10,null,true), "Apply ignored exact SHA");
        check(!(boolean) admission.invoke(null,"DRAFT","foreign-schema",sha,sha,3,.04,.10,null,true), "Apply ignored schema");
        check(!(boolean) admission.invoke(null,"DRAFT",schema,sha,sha,3,.12,.10,null,true), "Apply admitted failed quality");
        check(!(boolean) admission.invoke(null,"DRAFT",schema,sha,sha,0,.04,.10,null,true), "Apply admitted unchanged initialization");
    }
    private static void reject(Checked action, String message) throws Exception
    {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
