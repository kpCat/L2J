import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;

/** Offline current byte verification through actual codecs, following ReadDurable024. */
class VerifyNativeCommit039
{
    private static final PhantomBackgroundStateCodec CODEC = new PhantomBackgroundStateCodec();
    private static Properties props(String path) throws Exception
    {
        final var result = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
    private static void require(boolean condition, String reason)
    {
        if (!condition) { throw new IllegalArgumentException(reason); }
    }
    private static byte[] payload(Properties p) { return Base64.getDecoder().decode(p.getProperty("afterPayloadBase64")); }
    private static void exact(Properties p, String key, long expected)
    {
        require(Long.parseLong(p.getProperty(key)) == expected, key);
    }
    private static void close(Properties p, String key, double expected)
    {
        // Exactly the existing PhantomBackgroundTransaction.close threshold, no new tolerance.
        require(Math.abs(Double.parseDouble(p.getProperty(key)) - expected) <= 0.000001d, key);
    }
    private static void fields(Properties p, String prefix, PhantomBackgroundState s)
    {
        exact(p,prefix+"level",s.progress().level()); exact(p,prefix+"exp",s.progress().experience()); exact(p,prefix+"sp",s.progress().skillPoints()); exact(p,prefix+"expBeforeDeath",s.progress().experienceBeforeDeath());
        close(p,prefix+"hp",s.vitals().currentHp()); close(p,prefix+"maxHp",s.vitals().maximumHp()); close(p,prefix+"mp",s.vitals().currentMp()); close(p,prefix+"maxMp",s.vitals().maximumMp()); close(p,prefix+"cp",s.vitals().currentCp()); close(p,prefix+"maxCp",s.vitals().maximumCp());
        exact(p,prefix+"x",s.position().x()); exact(p,prefix+"y",s.position().y()); exact(p,prefix+"z",s.position().z()); exact(p,prefix+"heading",s.position().heading()); exact(p,prefix+"classId",s.identity().activeClassId()); exact(p,prefix+"race",s.identity().raceOrdinal());
    }
    private static String verify(String[] paths) throws Exception
    {
        final var row = props(paths[0]); final var contextRow = props(paths[1]); final var seal = props(paths[2]);
        require("SUCCESS".equals(row.getProperty("commitOutcome")) && "actual-owning-jdbc-commit".equals(row.getProperty("source")), "owning committed producer");
        require("true".equals(row.getProperty("canonicalChecked")) && "true".equals(row.getProperty("nativeAfterMatched")) && "true".equals(row.getProperty("nativeContextMatched")), "exact locked native AFTER comparison");
        require(row.getProperty("captureFailure", "").isEmpty() && contextRow.getProperty("captureFailure", "").isEmpty(), "capture gap");
        final byte[] bytes = payload(row); final var state = CODEC.decode(bytes);
        require(Arrays.equals(bytes, CODEC.encode(state)) && PhantomBackgroundTransaction.payloadDigest(bytes).equals(row.getProperty("afterPayloadSha256")), "state codec/hash");
        exact(row,"profileId",state.identity().profileId()); exact(row,"objectId",state.identity().characterObjectId()); exact(row,"classIndex",state.identity().classIndex());
        final byte[] receiptBytes = Base64.getDecoder().decode(row.getProperty("receiptBase64")); final var receipt = PhantomOwnedStoreIntent.decode(receiptBytes);
        require(PhantomBackgroundTransaction.payloadDigest(receiptBytes).equals(row.getProperty("receiptSha256")), "receipt hash");
        exact(row,"nativeEpoch",receipt.materializedAtNanos()); exact(row,"beforeRowVersion",receipt.preparedRowVersion()); exact(row,"afterRowVersion",Math.addExact(receipt.preparedRowVersion(),1));
        require(row.getProperty("beforePayloadSha256").equals(PhantomBackgroundTransaction.payloadDigest(CODEC.encode(receipt.after().withState(PhantomBackgroundState.State.VERIFY_PENDING)))), "exact prepared predecessor");
        require(Arrays.equals(bytes,CODEC.encode(receipt.after().withState(state.state()))), "actual completed state retains all native AFTER fields");
        require("FULL".equals(row.getProperty("inventoryScope")) && row.getProperty("inventorySha256").equals(state.inventory().canonicalHash()), "full locked inventory hash");
        require(row.getProperty("skillsSha256").equals(receipt.skillsHash()), "exact locked class skills hash");
        require(row.getProperty("checkedStateSha256").equals(row.getProperty("afterPayloadSha256")), "checked returned state");
        fields(row,"canonical.",state);
        final byte[] contextBytes = payload(contextRow); final var context = PhantomNativeContext.decode(contextBytes);
        require(PhantomBackgroundTransaction.payloadDigest(contextBytes).equals(contextRow.getProperty("afterPayloadSha256")), "PNC hash");
        require(row.getProperty("transactionSequence").equals(contextRow.getProperty("transactionSequence")) && row.getProperty("database").equals(contextRow.getProperty("database")) && row.getProperty("runId").equals(contextRow.getProperty("runId")), "same owning transaction namespace");
        require(context.phase() == PhantomNativeContext.Phase.COMPLETED && context.binds(state.identity(), Long.parseLong(row.getProperty("afterRowVersion")), bytes), "completed PNC binding");
        exact(row,"canonical.vitality",context.afterPoints());
        require("FINALIZED".equals(seal.getProperty("checkpointStage")) && "true".equals(seal.getProperty("exactArgument")) && "true".equals(seal.getProperty("nativeOwnerSealed")), "actual sealed finalized argument");
        require(seal.getProperty("receiptSha256").equals(row.getProperty("receiptSha256")) && seal.getProperty("epoch").equals(row.getProperty("nativeEpoch")), "same native receipt/epoch");
        require(seal.getProperty("codeSha").equals(row.getProperty("sourceSha")), "same source");
        final byte[] nativeState = Base64.getDecoder().decode(seal.getProperty("fullStateBase64"));
        require(Arrays.equals(nativeState,CODEC.encode(receipt.after())) && PhantomBackgroundTransaction.payloadDigest(nativeState).equals(seal.getProperty("afterPayloadSha256")), "native sealed full state");
        fields(seal,"",state); exact(seal,"classIndex",state.identity().classIndex()); exact(seal,"profileId",state.identity().profileId()); exact(seal,"objectId",state.identity().characterObjectId()); exact(seal,"vitality",context.afterPoints());
        require(seal.getProperty("inventoryHash").equals(row.getProperty("inventorySha256")) && seal.getProperty("skillsHash").equals(row.getProperty("skillsSha256")), "native full inventory and class skills parity");
        close(seal,"hp",Math.min(state.vitals().maximumHp(), Math.round(Double.parseDouble(seal.getProperty("rawHp")))));
        close(seal,"mp",Math.min(state.vitals().maximumMp(), Math.round(Double.parseDouble(seal.getProperty("rawMp")))));
        close(seal,"cp",Math.min(state.vitals().maximumCp(), Math.round(Double.parseDouble(seal.getProperty("rawCp")))));
        return "PASS";
    }
    public static void main(String[] args) throws Exception
    {
        int pass = 0, fail = 0;
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8))
        {
            if (line.isBlank()) { continue; }
            final String[] paths = line.split("\t", -1);
            try { System.out.println(paths[0]+"\t"+verify(paths)+"\tFULL_NATIVE_COMMIT_AND_PNC"); pass++; }
            catch (Exception error) { System.out.println(paths[0]+"\tFAIL\t"+error.getClass().getSimpleName()+":"+error.getMessage()); fail++; }
        }
        System.err.println("NATIVE_COMMIT039 pass="+pass+" fail="+fail);
        if(fail > 0) { System.exit(2); }
    }
}
