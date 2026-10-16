import java.nio.file.Files;
import java.nio.file.Path;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;

/** Offline exact Player.load height rule; no Player, DB, or live JVM access. */
class ReadLoadHeight050
{
    public static void main(String[] args) throws Exception
    {
        ConfigLoader.init();
        var geo = GeoEngine.getInstance();
        int failed = 0;
        for (String line : Files.readAllLines(Path.of(args[0])))
        {
            String[] row = line.replace("\uFEFF", "").split("\t");
            int x = Integer.parseInt(row[2]);
            int y = Integer.parseInt(row[3]);
            int priorZ = Integer.parseInt(row[4]);
            int loadedZ = Integer.parseInt(row[5]);
            int expected = geo.getHeight(x, y, priorZ);
            boolean pass = geo.hasGeo(x, y) && expected == loadedZ;
            System.out.println("LOAD_HEIGHT050\t" + line + "\t" + expected + "\t" + pass);
            if (!pass) { failed++; }
        }
        if (failed != 0) { throw new IllegalStateException("Stock Player.load height mismatch: " + failed); }
    }
}
