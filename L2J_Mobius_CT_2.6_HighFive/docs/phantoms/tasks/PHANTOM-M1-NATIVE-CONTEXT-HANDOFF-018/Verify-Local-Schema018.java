import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.l2jmobius.tests.phantoms.PhantomTestSchemaManifest;

class VerifyLocalSchema018
{
    public static void main(String[] args) throws Exception
    {
        var module = Path.of(args[0]);
        var original = Path.of(args[1]);
        var current = PhantomTestSchemaManifest.inventory(module);
        var source = PhantomTestSchemaManifest.inventory(original);
        var byPath = source.stream().collect(Collectors.toMap(value -> value.relativePath(), Function.identity()));
        if (current.size() != source.size()) { throw new IllegalStateException("Schema inventory differs."); }
        for (var script : current)
        {
            var old = byPath.get(script.relativePath());
            if (old == null || !Files.readString(module.resolve(script.relativePath())).replace("\r\n", "\n").equals(Files.readString(original.resolve(old.relativePath())).replace("\r\n", "\n")))
            {
                throw new IllegalStateException("Semantic schema differs: " + script.relativePath());
            }
        }
        var snapshot = PhantomTestSchemaManifest.current(module);
        PhantomTestSchemaManifest.writeAtomic(PhantomTestSchemaManifest.localPath(module), snapshot);
        System.out.println("Normalized exact schema parity=" + current.size() + "; local-only manifest=" + snapshot + "; no database access or schema mutation.");
    }
}
