import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.tests.phantoms.PhantomM1TimerChecks;

/** TEST-only real ScriptEngine Quest bootstrap, kept outside the production script tree. */
public final class M1TimerBootstrap extends Quest
{
	public M1TimerBootstrap() { super(-1); }
	@Override public String onEvent(String event, Npc npc, Player player)
	{
		PhantomM1TimerChecks.onQuestEvent(player);
		return null;
	}
	public static void main(String[] args) { new M1TimerBootstrap(); }
}
