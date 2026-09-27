package handlers.chat.commands.voiced;

import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotService;
import org.l2jmobius.gameserver.model.actor.Player;

/** User consent and emergency controls for the private LocalPlay pilot. */
public final class PlayTest implements IVoicedCommandHandler
{
	private static final String[] COMMANDS =
	{
		"playtest"
	};

	@Override
	public boolean onCommand(String command, Player player, String target)
	{
		final String value = target == null ? "" : target.trim();
		final LocalPlayPilotService pilot = LocalPlayPilotService.getInstance();
		final String message;
		if (value.startsWith("arm ") && (value.indexOf(' ', 4) < 0))
		{
			message = pilot.arm(player, value.substring(4));
		}
		else if ("status".equals(value))
		{
			message = pilot.status(player);
		}
		else if ("stop".equals(value))
		{
			message = pilot.stop(player);
		}
		else if ("off".equals(value))
		{
			message = pilot.off(player);
		}
		else
		{
			message = "Использование: .playtest arm <код> | status | stop | off";
		}
		if (player != null)
		{
			player.sendSysMessage(message);
		}
		return true;
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
