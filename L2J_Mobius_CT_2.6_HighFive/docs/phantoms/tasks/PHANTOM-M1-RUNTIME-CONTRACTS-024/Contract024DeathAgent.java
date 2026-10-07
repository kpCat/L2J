import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;

/** Separate TASK024 severe encounter. Stock NPC stats/AI and stock death recovery only. */
public final class Contract024DeathAgent
{
    private record Victim(long profile, int object, long epoch, long exp, Player initial, Spawn spawn, Monster npc) {}
    private static final Set<Long> STARTED = new HashSet<>();
    public static void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final Path specPath = Path.of(argument).toRealPath(); final Properties spec = new Properties();
        try (var r = Files.newBufferedReader(specPath, StandardCharsets.UTF_8)) { spec.load(r); }
        final Path runtime = Path.of(spec.getProperty("runtime")).toRealPath();
        if (!runtime.toString().matches(".*[\\\\/]contract024[a-h][\\\\/]runtime") || !Path.of("").toRealPath().equals(runtime.resolve("game")) || !specPath.startsWith(runtime.getParent())
            || !"TASK024_CONTRACT".equals(spec.getProperty("owner")) || !spec.getProperty("pid").equals(""+ProcessHandle.current().pid())) { throw new IllegalStateException("TASK024_DEATH_IDENTITY"); }
        final Path output = Path.of(spec.getProperty("output")).toRealPath();
        final var instant=ProcessHandle.current().info().startInstant().orElseThrow();
        final long ticks=621355968000000000L+instant.getEpochSecond()*10000000L+instant.getNano()/100;
        if(ticks/10000!=Long.parseLong(spec.getProperty("startTicks"))/10000) { throw new IllegalStateException("TASK024_DEATH_START_TICKS"); }
        if (!output.startsWith(runtime.getParent().getParent().getParent().resolve("docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/evidence"))) { throw new IllegalStateException("TASK024_DEATH_OUTPUT"); }
        synchronized (STARTED) { if (!STARTED.add(ProcessHandle.current().pid())) { throw new IllegalStateException("TASK024_DEATH_ONCE"); } }
        for (Player p : World.getInstance().getPlayers()) { if (p.getClient()!=null) { throw new IllegalStateException("TASK024_REAL_PRESENT"); } }
        final List<Victim> selected = new ArrayList<>();
        for (String key : spec.stringPropertyNames())
        {
            if (!key.startsWith("profile.")) { continue; }
            final String[] parts=spec.getProperty(key).split(","); final int object=Integer.parseInt(parts[0]); final long epoch=Long.parseLong(parts[1]);
            final Player p=World.getInstance().getPlayer(object); final var owner=p==null?null:p.getNativeWorkOwner();
            if (owner==null || !owner.isCurrent() || owner.epoch()!=epoch || owner.player()!=p || owner.sealed() || p.isDead() || p.getClient()!=null || !p.hasHeadlessOutboundSession() || p.getInstanceId()!=0) { throw new IllegalStateException("TASK024_DEATH_VICTIM"); }
            selected.add(new Victim(Long.parseLong(key.substring(8)),object,epoch,p.getExp(),p,null,null));
        }
        if (selected.size()!=2) { throw new IllegalStateException("TASK024_TWO_DEATH_VICTIMS"); }
        Files.writeString(output.resolve("selected.tsv"),"profile\tobject\tepoch\texp\n"+selected.stream().map(v->v.profile()+"\t"+v.object()+"\t"+v.epoch()+"\t"+v.exp()+"\n").reduce("",String::concat),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        ThreadPool.execute(() -> encounter(output,selected));
    }
    private static void encounter(Path output,List<Victim> selected)
    {
        final List<Victim> active=new ArrayList<>();
        try
        {
            for (Victim v:selected)
            {
                if (World.getInstance().getPlayer(v.object())!=v.initial() || v.initial().isDead() || v.initial().getNativeWorkOwner()==null || !v.initial().getNativeWorkOwner().isCurrent() || v.initial().getNativeWorkOwner().epoch()!=v.epoch()) { throw new IllegalStateException("TASK024_VICTIM_CHANGED_BEFORE_SPAWN"); }
                final Player p=v.initial(); final Spawn spawn=new Spawn(20933); spawn.setAmount(1); spawn.setXYZ(p.getX()+40,p.getY(),p.getZ()); spawn.setHeading(0); spawn.setRespawnDelay(0);
                if (spawn.init()!=1 || !(spawn.getLastSpawn() instanceof Monster npc)) { throw new IllegalStateException("TASK024_SEVERE_STOCK_NPC"); }
                spawn.stopRespawn(); active.add(new Victim(v.profile(),v.object(),v.epoch(),v.exp(),p,spawn,npc));
                npc.addDamageHate(p,0,100000); npc.getAI().setIntention(Intention.ATTACK,p);
            }
            poll(output,active,new HashSet<>(),new HashSet<>(),new HashMap<>(),System.nanoTime(),0);
        }
        catch(Exception e) { finish(output,active,"FIXTURE_ERROR:"+e); }
    }
    private static void poll(Path output,List<Victim> actors,Set<Long> dead,Set<Long> recovered,Map<Long,long[]> returnEvidence,long start,int sequence)
    {
        try
        {
            for (Player p:World.getInstance().getPlayers()) { if(p.getClient()!=null) { finish(output,actors,"REAL_PRESENT_STOPPED_OWNED_FIXTURE"); return; } }
            String text="seconds\tprofile\tobject\tepoch\tworld\tdead\thp\texp\trewards\tx\ty\tz\n";
            for(Victim v:actors)
            {
                if(v.initial().isDead()) { dead.add(v.profile()); if(v.npc().isSpawned()) { v.npc().deleteMe(); } }
                final Player current=World.getInstance().getPlayer(v.object()); final var owner=current==null?null:current.getNativeWorkOwner();
                final boolean exact=owner!=null && owner.isCurrent() && owner.player()==current && current.getClient()==null && current.hasHeadlessOutboundSession();
                final long reward=exact?owner.evidence().snapshot(System.nanoTime()).rewardSequence():0;
                if(dead.contains(v.profile()) && exact && !current.isDead())
                {
                    final long[] before=returnEvidence.computeIfAbsent(v.profile(),id->new long[]{owner.epoch(),reward});
                    if(before[0]==owner.epoch() && reward>before[1]) { recovered.add(v.profile()); }
                }
                final Player sample=exact?current:v.initial();
                text+=(System.nanoTime()-start)/1e9+"\t"+v.profile()+"\t"+v.object()+"\t"+(exact?owner.epoch():v.epoch())+"\t"+exact+"\t"+sample.isDead()+"\t"+sample.getCurrentHp()+"\t"+sample.getExp()+"\t"+reward+"\t"+sample.getX()+"\t"+sample.getY()+"\t"+sample.getZ()+"\n";
            }
            Files.writeString(output.resolve(String.format("sample-%03d.tsv",sequence)),text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(recovered.size()==2 || System.nanoTime()-start>=180_000_000_000L) { finish(output,actors,"NATIVE_DEATH="+dead+";RECOVERY_FARM="+recovered+";PASS="+(dead.size()==2 && recovered.size()==2)); }
            else { ThreadPool.schedule(()->poll(output,actors,dead,recovered,returnEvidence,start,sequence+1),1000); }
        }
        catch(Exception e) { finish(output,actors,"OBSERVATION_ERROR:"+e); }
    }
    private static void finish(Path output,List<Victim> actors,String result)
    {
        boolean cleaned=true;
        for(Victim v:actors) { try { v.spawn().stopRespawn(); v.npc().deleteMe(); cleaned&=World.getInstance().findObject(v.npc().getObjectId())==null; } catch(Exception e) { cleaned=false; } }
        try { Files.writeString(output.resolve("result.txt"),result+"\nEXACT_OWNED_NPC_CLEANUP="+cleaned+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); } catch(Exception e) { System.err.println("TASK024_DEATH_EXPORT:"+e); }
    }
}
