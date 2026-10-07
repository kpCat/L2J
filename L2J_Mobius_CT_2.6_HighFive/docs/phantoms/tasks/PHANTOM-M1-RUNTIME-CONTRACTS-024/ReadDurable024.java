import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;

/** Read-only exported bytes through the existing repository codecs. */
class ReadDurable024
{
    public static void main(String[] args) throws Exception
    {
        for (String raw : Files.readAllLines(Path.of(args[0])))
        {
            final String[] row = raw.replace("\uFEFF", "").split("\t", 4);
            final byte[] bytes = HexFormat.of().parseHex(row[3]);
            if (row[2].equals("background.state"))
            {
                final var s = new PhantomBackgroundStateCodec().decode(bytes);
                System.out.println(row[0]+"\t"+row[1]+"\tstate\t"+s.state()+"\t"+PhantomBackgroundTransaction.payloadDigest(bytes)+"\t"+s.progress()+"\t"+s.position());
            }
            else if (row[2].equals("background.native-context"))
            {
                final var c = PhantomNativeContext.decode(bytes);
                System.out.println(row[0]+"\t"+row[1]+"\tcontext\t"+c.phase()+"\t"+c.stateRowVersion()+"\t"+c.stateDigest()+"\t"+c.afterEligibility()+"\t"+c.afterPoints()+"\t"+c.simulationEligible());
            }
            else if (row[2].equals("background.owned-store"))
            {
                final var i = PhantomOwnedStoreIntent.decode(bytes);
                System.out.println(row[0]+"\t"+row[1]+"\towned\t"+i.preparedRowVersion()+"\t"+i.materializedAtNanos()+"\t"+PhantomBackgroundTransaction.payloadDigest(bytes));
            }
        }
    }
}
