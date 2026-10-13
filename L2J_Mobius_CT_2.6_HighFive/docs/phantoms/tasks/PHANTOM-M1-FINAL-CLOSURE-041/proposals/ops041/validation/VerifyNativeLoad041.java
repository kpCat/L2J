import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;

/** Offline comparison at actual markMaterialized commit; pre-context load Z is explicitly separate. */
class VerifyNativeLoad041
{
    private static Properties props(String file) throws Exception
    {
        var p=new Properties(); try(var r=Files.newBufferedReader(Path.of(file),StandardCharsets.UTF_8)){p.load(r);} return p;
    }
    private static void require(boolean value,String reason){if(!value){throw new IllegalArgumentException(reason);}}
    private static void equal(Properties p,String key,long value){require(Long.parseLong(p.getProperty(key))==value,key);}
    public static void main(String[] args) throws Exception
    {
        int failures=0;
        for(String line:Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8))
        {
            var paths=line.split("\t");
            try
            {
                var commit=props(paths[0]); var birth=props(paths[1]);
                require("actual-owning-jdbc-commit".equals(commit.getProperty("source")) && "SUCCESS".equals(commit.getProperty("commitOutcome")) && "markMaterialized".equals(commit.getProperty("producer")),"actual committed native load boundary");
                require("true".equals(commit.getProperty("canonicalChecked")) && "FULL".equals(commit.getProperty("inventoryScope")) && commit.getProperty("captureFailure","").isEmpty(),"locked full canonical comparison");
                byte[] bytes=Base64.getDecoder().decode(commit.getProperty("afterPayloadBase64")); var s=new PhantomBackgroundStateCodec().decode(bytes);
                require(s.state()==PhantomBackgroundState.State.MATERIALIZED && PhantomBackgroundTransaction.payloadDigest(bytes).equals(commit.getProperty("afterPayloadSha256")),"actual materialized payload");
                require("true".equals(birth.getProperty("nativeBirthBoundary")) && "true".equals(birth.getProperty("worldAbsentAtRegister")) && "PRE_CONTEXT_NATIVE_LOAD".equals(birth.getProperty("load041Boundary")),"actual pre-World native birth");
                equal(birth,"profileId",s.identity().profileId()); equal(birth,"objectId",s.identity().characterObjectId()); equal(birth,"load041ClassIndex",s.identity().classIndex()); equal(birth,"load041ClassId",s.identity().activeClassId()); equal(birth,"load041Level",s.progress().level());
                equal(birth,"initialExp",s.progress().experience()); equal(birth,"initialSp",s.progress().skillPoints()); equal(birth,"load041ExpBeforeDeath",s.progress().experienceBeforeDeath()); equal(birth,"initialX",s.position().x()); equal(birth,"initialY",s.position().y());
                require(birth.getProperty("load041InventoryHash").equals(s.inventory().canonicalHash()) && birth.getProperty("load041InventoryHash").equals(commit.getProperty("inventorySha256")),"complete native load inventory");
                require(birth.getProperty("load041SkillsHash").equals(commit.getProperty("skillsSha256")),"complete native load active class skills");
                // afterPlayerLoad's executed markMaterialized path checks/restores canonical Z and binds PNC before this commit.
                System.out.println(paths[0]+"\tPASS\tprofile="+s.identity().profileId()+";initialGroundedZ="+birth.getProperty("initialZ")+";verifiedCanonicalZ="+s.position().z()+";exp="+s.progress().experience()+";sp="+s.progress().skillPoints()+";operationKey="+s.receipt().operationKey());
            }
            catch(Exception failure){failures++;System.out.println(paths[0]+"\tFAIL\t"+failure);}
        }
        if(failures>0){System.exit(2);}
    }
}
