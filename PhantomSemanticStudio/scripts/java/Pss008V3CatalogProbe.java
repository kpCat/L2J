import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.*;
import org.w3c.dom.Element;

/** Test-only bridge. The operator script supplies only physical scratch paths. */
public final class Pss008V3CatalogProbe
{
    public static void main(String[] args) throws Exception
    {
        if (args.length == 3 && args[0].equals("--negative"))
        {
            Path scratch = Path.of(args[1]).toRealPath();
            Path root = own(scratch, args[2]);
            try { PhantomHumanizedCatalog.loadV3(root, true); }
            catch (IllegalArgumentException expected) { System.out.println("REJECTED_V3_NEGATIVE"); System.exit(3); }
            throw new IllegalStateException("Corrupt v3 XML accepted");
        }
        if (args.length != 9) throw new IllegalArgumentException("Expected scratch/baseline/stage/negative/spec/counts.");
        Path scratch = Path.of(args[0]).toRealPath();
        Path baselineRoot = own(scratch, args[1]);
        Path stageRoot = own(scratch, args[2]);
        Path negativeRoot = own(scratch, args[3]);
        Path spec = own(scratch, args[4]);
        int patterns = Integer.parseInt(args[5]), templates = Integer.parseInt(args[6]);
        int addedPatterns = Integer.parseInt(args[7]), addedTemplates = Integer.parseInt(args[8]);
        PhantomHumanizedCatalog baseline = PhantomHumanizedCatalog.loadV3(baselineRoot, true);
        PhantomHumanizedCatalog staged = PhantomHumanizedCatalog.loadV3(stageRoot, true);
        require(baseline.patternCount() == patterns && baseline.templateCount() == templates, "Baseline counters");
        require(staged.patternCount() == patterns + addedPatterns && staged.templateCount() == templates + addedTemplates, "Stage counters");
        require(!baseline.combinedHash().equals(staged.combinedHash()), "V3 combined hash unchanged");
        require(staged.combinedHash().equals(PhantomHumanizedCatalog.loadV3(stageRoot, true).combinedHash()), "Non-deterministic load");
        int checked = 0;
        for (String row : Files.readAllLines(spec, StandardCharsets.UTF_8))
        {
            String[] fields = row.split("\t", -1);
            require(fields.length == 6, "Invalid expected-ID specification");
            boolean pattern = fields[1].equals("PATTERN");
            require(fields[5].startsWith(pattern ? "semantic/humanized/v3/segments/" : "conversation/humanized/v3/segments/") && !fields[5].contains(".."), "Invalid target path");
            Path xml = own(scratch, stageRoot.resolve(fields[5]).toString());
            Element entry = entry(xml, pattern ? "pattern" : "template", fields[0]);
            require(entry.getAttribute("act").equals(fields[2]) && !entry.hasAttribute("override"), "Act/override mismatch");
            String text = entry.getAttribute(pattern ? "phrase" : "text");
            require(PhantomHumanizedCatalog.sha256(text).equals(fields[4]), "Candidate text hash mismatch");
            if (pattern)
            {
                var match = staged.understand(text).orElseThrow();
                require(match.patternId().equals(fields[0]) && match.act().equals(fields[2]) && match.topic().equals(fields[3]), "Actual v3 pattern not understood");
            }
            else
            {
                boolean found = false;
                // Sorted-ID + floorMod selection is deterministic. The effective global
                // template hard bound covers the eligible band/register fallback union.
                for (long selector = 0; selector < 32768 && !found; selector++)
                {
                    var selected = staged.select(fields[2], RelationshipBand.valueOf(entry.getAttribute("band")), Register.valueOf(entry.getAttribute("register")), ProfanityMode.NONE,
                        Variation.HIGH, false, false, "", "", "", "", selector, Set.of());
                    found = selected.templateId().equals(fields[0]) && selected.text().equals(text);
                }
                require(found, "Actual v3 template not selected in bounded probe");
                boolean wrongActRejected = false;
                try { staged.select("pss.absent.reply", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE,
                    Variation.HIGH, false, false, "", "", "", "", 0, Set.of()); }
                catch (IllegalArgumentException expected) { wrongActRejected = true; }
                require(wrongActRejected, "Wrong act not rejected");
                var hostile = staged.select(fields[2], RelationshipBand.HOSTILE, Register.NEUTRAL, ProfanityMode.NONE,
                    Variation.HIGH, false, false, "", "", "", "", 0, Set.of());
                if (entry.getAttribute("band").equals("FAMILIAR") || entry.getAttribute("register").equals("CASUAL"))
                    require(!hostile.templateId().equals(fields[0]), "Wrong band/register selected candidate");
                System.out.println("SELECTOR_NEGATIVE wrongAct=CHECKED register=" + (entry.getAttribute("register").equals("CASUAL") ? "CHECKED" : "NOT_APPLICABLE")
                    + " band=" + (entry.getAttribute("band").equals("FAMILIAR") ? "CHECKED" : "NOT_APPLICABLE"));
            }
            checked++;
        }
        require(checked == addedPatterns + addedTemplates && checked > 0, "Expected IDs/count mismatch");
        require(staged.understand("pss unmatched synthetic eight qzxwvu").isEmpty(), "Unmatched input unexpectedly matched");
        Path schemaRoot = scratch.resolve("negative-schema");
        try (var paths = Files.walk(stageRoot))
        {
            for (Path from : paths.toList())
            {
                require(!Files.isSymbolicLink(from), "Linked stage path");
                Path to = schemaRoot.resolve(stageRoot.relativize(from));
                if (Files.isDirectory(from)) Files.createDirectories(to); else Files.copy(from, to);
            }
        }
        // Deliberate duplicate only in a second physical scratch copy, never stage/source.
        String[] negativeEntry = Files.readAllLines(spec, StandardCharsets.UTF_8).get(0).split("\t", -1);
        boolean corruptPattern = negativeEntry[1].equals("PATTERN");
        Path corrupt = own(scratch, negativeRoot.resolve(negativeEntry[5]).toString());
        var document = document(corrupt);
        var records = document.getElementsByTagName(corruptPattern ? "pattern" : "template");
        Element duplicate = null;
        for (int i = 0; i < records.getLength(); i++) if (((Element)records.item(i)).getAttribute("id").equals(negativeEntry[0]))
            duplicate = (Element)records.item(i).cloneNode(true);
        require(duplicate != null, "Negative fixture missing selected ID");
        document.getElementsByTagName(corruptPattern ? "patterns" : "templates").item(0).appendChild(duplicate);
        var transform = TransformerFactory.newInstance().newTransformer();
        transform.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transform.transform(new DOMSource(document), new StreamResult(corrupt.toFile()));
        boolean rejected = false;
        try { PhantomHumanizedCatalog.loadV3(negativeRoot, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Duplicate v3 ID accepted");
        Path schemaFile = own(scratch, schemaRoot.resolve(negativeEntry[5]).toString());
        var schema = document(schemaFile);
        schema.getDocumentElement().setAttribute("unsupported", "true");
        transform.transform(new DOMSource(schema), new StreamResult(schemaFile.toFile()));
        boolean schemaRejected = false;
        try { PhantomHumanizedCatalog.loadV3(schemaRoot, true); }
        catch (IllegalArgumentException expected) { schemaRejected = true; }
        require(schemaRejected, "Invalid schema accepted");
        System.out.println("PASS_JAVA_STAGED_V3 baselinePatterns=" + baseline.patternCount() + " baselineTemplates=" + baseline.templateCount()
            + " stagedPatterns=" + staged.patternCount() + " stagedTemplates=" + staged.templateCount() + " checkedIds=" + checked
            + " baselineHash=" + baseline.combinedHash() + " stagedHash=" + staged.combinedHash() + " negative=DUPLICATE_AND_SCHEMA_REJECTED");
    }

    private static Path own(Path scratch, String value) throws Exception
    {
        Path path = Path.of(value).toAbsolutePath().normalize();
        require(path.startsWith(scratch) && !path.equals(scratch), "Path outside scratch");
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent()) require(!Files.isSymbolicLink(ancestor), "Linked path");
        return path;
    }
    private static org.w3c.dom.Document document(Path path) throws Exception
    {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }
    private static Element entry(Path path, String tag, String id) throws Exception
    {
        var entries = document(path).getElementsByTagName(tag);
        Element found = null;
        for (int i = 0; i < entries.getLength(); i++) if (((Element)entries.item(i)).getAttribute("id").equals(id))
        {
            require(found == null, "Duplicate expected XML ID"); found = (Element)entries.item(i);
        }
        require(found != null, "Missing expected XML ID"); return found;
    }
    private static void require(boolean condition, String message)
    {
        if (!condition) throw new IllegalStateException(message);
    }
}
