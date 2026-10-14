import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;

/** Offline actual witness bytes through the existing codecs; no Player or JDBC access. */
class DecodeLifeReceipts043
{
    public static void main(String[] args) throws Exception
    {
        for (String file : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8))
        {
            if (file.isBlank()) { continue; }
            var p = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) { p.load(reader); }
            String component = p.getProperty("componentName");
            for (String side : java.util.List.of("before", "after"))
            {
                String payload = p.getProperty(side + "PayloadBase64");
                if (payload == null || payload.equals("ABSENT") || payload.isEmpty()) { continue; }
                byte[] bytes = Base64.getDecoder().decode(payload);
                if (component.equals("background.state"))
                {
                    var s = new PhantomBackgroundStateCodec().decode(bytes);
                    System.out.println(file + "\t" + side + "\tSTATE\t" + s.identity().profileId() + "\t" + s.progress().experience() + "\t" + s.progress().skillPoints() + "\t" + s.receipt().operationKey() + "\t" + s.receipt().activityGeneration() + "\t" + s.receipt().tickSequence());
                }
                else if (component.equals("background.catchup"))
                {
                    var s = new PhantomBackgroundCatchupStateCodec().decode(bytes);
                    System.out.println(file + "\t" + side + "\tCATCHUP\t" + p.getProperty("profileId") + "\t" + s.cursorEpochMinute() + "\t" + s.targetEpochMinute() + "\t" + s.requestId() + "\t" + s.intervalOrdinal() + "\t" + s.status());
                }
            }
        }
    }
}
