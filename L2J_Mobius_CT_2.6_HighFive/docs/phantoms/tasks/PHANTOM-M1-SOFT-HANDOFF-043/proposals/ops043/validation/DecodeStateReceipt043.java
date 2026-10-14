import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;

/** Existing codec only; emit receipt identities and earned scalar totals, with no database connection. */
class DecodeStateReceipt043
{
    private static void emit(String label,PhantomBackgroundState s)
    {
        System.out.println(label+"\t"+s.identity().profileId()+"\t"+s.progress().experience()+"\t"+s.progress().skillPoints()+"\t"+s.receipt().operationKey());
    }
    public static void main(String[] args) throws Exception
    {
        var codec=new PhantomBackgroundStateCodec();
        for(String line:Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8))
        {
            var entry=line.split("\t",2);
            if(entry[0].equals("CUT"))
            {
                for(String raw:Files.readAllLines(Path.of(entry[1]),StandardCharsets.UTF_8))
                {
                    var row=raw.replace("\uFEFF", "").split("\t",4);
                    if(row[2].equals("background.state")){emit("CUT:"+row[0],codec.decode(HexFormat.of().parseHex(row[3])));}
                }
            }
            else
            {
                var p=new Properties();try(var r=Files.newBufferedReader(Path.of(entry[1]),StandardCharsets.UTF_8)){p.load(r);}
                emit(entry[1],codec.decode(Base64.getDecoder().decode(p.getProperty("afterPayloadBase64"))));
            }
        }
    }
}
