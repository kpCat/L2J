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
		else
		{
			message = switch (value)
			{
				case "status" -> pilot.status(player);
				case "stop" -> pilot.stop(player);
				case "off" -> pilot.off(player);
				default -> "Использование: .playtest arm <код> | status | stop | off";
			};
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
