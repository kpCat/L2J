import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;

/** Read-only exported bytes, decoded by the existing versioned repository codecs. */
class ReadDurable023
{
    public static void main(String[] args) throws Exception
    {
        for (String line : Files.readAllLines(Path.of(args[0])))
        {
            String[] row = line.split("\t", 4);
            byte[] bytes = HexFormat.of().parseHex(row[3]);
            if (row[2].equals("background.catchup"))
            {
                var state = new PhantomBackgroundCatchupStateCodec().decode(bytes);
                System.out.println(row[0] + "\t" + row[1] + "\tcatchup=" + state.status() + ";goal=" + state.goalId() + "/" + state.goalRevision() + ";claim=" + state.requestId() + ";failure=" + state.failureReason() + ";hashes=" + state.authorityHashes());
            }
            else if (row[2].equals("background.state"))
            {
                var state = new PhantomBackgroundStateCodec().decode(bytes);
                System.out.println(row[0] + "\t" + row[1] + "\tstate=" + state.state() + ";progress=" + state.progress() + ";vitals=" + state.vitals() + ";hashes=" + state.hashes() + ";receipt=" + state.receipt());
            }
        }
    }
}
