import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;

/** Actual repository codecs and canonical SQL rows from one stopped consistent cut. */
class VerifyStoppedCut039
{
    static List<Map<String,String>> rows(Path path) throws Exception
    {
        var lines=Files.readAllLines(path,StandardCharsets.UTF_8);
        var header=lines.get(0).replace("\uFEFF", "").split("\t",-1);
        var result=new ArrayList<Map<String,String>>();
        for(int i=1;i<lines.size();i++)
        {
            if(lines.get(i).isBlank()) continue;
            var values=lines.get(i).split("\t",-1); var row=new HashMap<String,String>();
            if(values.length!=header.length) throw new IllegalArgumentException("SQL column count");
            for(int j=0;j<header.length;j++) row.put(header[j],values[j]);
            result.add(row);
        }
        return result;
    }
    static void require(boolean condition,String reason) { if(!condition) throw new IllegalArgumentException(reason); }
    static void exact(Map<String,String> row,String field,long expected) { require(Long.parseLong(row.get(field))==expected,field); }
    static void close(Map<String,String> row,String field,double expected) { require(Math.abs(Double.parseDouble(row.get(field))-expected)<=0.000001d,field); }
    public static void main(String[] args) throws Exception
    {
        var root=Path.of(args[0]); var chars=rows(root.resolve("characters.tsv"));
        var items=rows(root.resolve("items.tsv")); var skills=rows(root.resolve("skills.tsv"));
        var components=new HashMap<String,String[]>();
        for(var line:Files.readAllLines(root.resolve("durable-hex.tsv")))
        {
            if(line.isBlank()) continue; var row=line.replace("\uFEFF", "").split("\t",4);
            require(components.put(row[0]+":"+row[2],row)==null,"duplicate component");
        }
        int failures=0;
        for(var input:Files.readAllLines(Path.of(args[1])))
        {
            var fields=input.split("\t",-1); var pid=fields[0];
            try
            {
                var stateRow=components.get(pid+":background.state"); var contextRow=components.get(pid+":background.native-context");
                require(stateRow!=null && contextRow!=null && !components.containsKey(pid+":background.owned-store"),"state/context/pending");
                var bytes=HexFormat.of().parseHex(stateRow[3]); var codec=new PhantomBackgroundStateCodec(); var state=codec.decode(bytes);
                require(Arrays.equals(bytes,codec.encode(state)),"state codec roundtrip");
                var contextBytes=HexFormat.of().parseHex(contextRow[3]); var context=PhantomNativeContext.decode(contextBytes);
                require(Arrays.equals(contextBytes,context.encode()) && context.phase()==PhantomNativeContext.Phase.COMPLETED && context.binds(state.identity(),Long.parseLong(stateRow[1]),bytes),"PNC completed binding");
                var saved=chars.stream().filter(r->r.get("profileId").equals(pid)).toList(); require(saved.size()==1,"one character"); var c=saved.get(0);
                exact(c,"profileId",state.identity().profileId()); exact(c,"objectId",state.identity().characterObjectId()); exact(c,"classIndex",state.identity().classIndex()); exact(c,"classId",state.identity().activeClassId()); exact(c,"race",state.identity().raceOrdinal());
                exact(c,"level",state.progress().level()); exact(c,"exp",state.progress().experience()); exact(c,"sp",state.progress().skillPoints()); exact(c,"expBeforeDeath",state.progress().experienceBeforeDeath());
                close(c,"hp",state.vitals().currentHp()); close(c,"maxHp",state.vitals().maximumHp()); close(c,"mp",state.vitals().currentMp()); close(c,"maxMp",state.vitals().maximumMp()); close(c,"cp",state.vitals().currentCp()); close(c,"maxCp",state.vitals().maximumCp());
                exact(c,"x",state.position().x()); exact(c,"y",state.position().y()); exact(c,"z",state.position().z()); exact(c,"heading",state.position().heading()); exact(c,"vitality",context.afterPoints()); exact(c,"online",0);
                var inventory=MessageDigest.getInstance("SHA-256");
                var selectedItems=items.stream().filter(r->r.get("profileId").equals(pid) && Set.of("INVENTORY","PAPERDOLL").contains(r.get("loc"))).sorted(Comparator.comparingLong(r->Long.parseLong(r.get("object_id")))).toList();
                for(var item:selectedItems) for(var key:List.of("object_id","item_id","count","loc")) {inventory.update(item.get(key).getBytes(StandardCharsets.US_ASCII));inventory.update((byte)0);}
                require(HexFormat.of().formatHex(inventory.digest()).equals(state.inventory().canonicalHash()),"FULL inventory canonical hash");
                var skillBytes=new StringBuilder();
                skills.stream().filter(r->r.get("profileId").equals(pid) && r.get("class_index").equals(c.get("classIndex"))).sorted(Comparator.comparingLong(r->Long.parseLong(r.get("skill_id")))).forEach(r->skillBytes.append(r.get("skill_id")).append(':').append(r.get("skill_level")).append('\n'));
                var witness=new Properties(); try(var reader=Files.newBufferedReader(Path.of(fields[1]),StandardCharsets.UTF_8)){witness.load(reader);}
                require("true".equals(witness.getProperty("canonicalChecked")) && witness.getProperty("afterRowVersion").equals(stateRow[1]) && witness.getProperty("afterPayloadSha256").equals(PhantomBackgroundTransaction.payloadDigest(bytes)),"latest actual locked canonical witness");
                require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(skillBytes.toString().getBytes(StandardCharsets.US_ASCII))).equals(witness.getProperty("skillsSha256")),"class skills hash");
                var goalRow=components.get(pid+":goal.runtime");
                if(goalRow!=null) {var goalBytes=HexFormat.of().parseHex(goalRow[3]);var goalCodec=new PhantomGoalStateCodec();require(Arrays.equals(goalBytes,goalCodec.encode(goalCodec.decode(goalBytes))),"goal codec roundtrip");}
                System.out.println(pid+"\tPASS\tSTOPPED_CANONICAL_STATE_PNC_FULL_ITEMS_SKILLS_GOAL");
            }
            catch(Exception error){failures++;System.out.println(pid+"\tFAIL\t"+error.getClass().getSimpleName()+":"+error.getMessage());}
        }
        if(failures>0) System.exit(2);
    }
}
