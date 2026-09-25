/*
 * Copyright (c) 2013 L2jMobius
 */
package handlers.chat.commands.voiced;

import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorAdmissionProfile;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorStatus;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;

/** Read-only native admission status for a personal allowlisted real player. */
public class PhantomStatus implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"phantomstatus"
	};

	@Override
	public boolean onCommand(String command, Player player, String target)
	{
		if (!PersonalCharacterQoLService.getInstance().isPersonalUser(player))
		{
			if (player != null)
			{
				player.sendSysMessage("Phantom status: personal access denied.");
			}
			return true;
		}
		final String argument = target == null ? "" : target.trim();
		if (!argument.isEmpty())
		{
			final long profileId;
			try
			{
				profileId = Long.parseLong(argument);
			}
			catch (NumberFormatException e)
			{
				player.sendSysMessage("Usage: .phantomstatus [profileId]");
				return true;
			}
			if (profileId <= 0)
			{
				player.sendSysMessage("Usage: .phantomstatus [profileId]");
				return true;
			}
			final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(profileId).orElse(null);
			if (profile == null)
			{
				player.sendSysMessage("Phantom profile " + profileId + ": not found in population manager.");
				return true;
			}
			player.sendSysMessage("Phantom profile " + profileId + ": population=" + profile.admission().populationState() + ", desired=" + profile.admission().desiredState() + ", eligible=" + profile.admission().eligible() + ", admitted=" + profile.admission().admitted() + ", reason=" + profile.admission().reason() + ", pendingRebalance=" + profile.admission().pendingRebalance() + ".");
			player.sendSysMessage("Phantom profile " + profileId + ": scheduler=" + (profile.scheduler() == null ? "absent" : profile.scheduler().effectiveState() + "/" + profile.scheduler().transitionStatus() + "/" + profile.scheduler().lastResult()) + ", worldPresent=" + ((profile.materialization() != null) && profile.materialization().worldPresent()) + ", nativeFailure=" + profile.lastMaterializationFailure() + ".");
			return true;
		}
		final OperatorStatus status = PhantomSystem.operatorStatus();
		player.sendSysMessage("Phantom status: runtime=" + status.runtimeState() + ", at=" + status.admission().evaluatedAt() + ", desired=" + status.admission().desiredActive() + ", eligible=" + status.admission().eligibleActive() + ", admitted=" + status.admission().admittedActive() + ", effectiveACTIVE=" + status.activityStateCounts().get(0) + ", worldMaterialized=" + status.worldMaterialized() + ".");
		player.sendSysMessage("Phantom admission: target=" + status.admission().activeTarget() + ", cap=" + status.admission().materializedCap() + ", pendingRebalance=" + status.admission().pendingRebalance() + " (independent snapshots).");
		final PhantomPopulationEcologyService.Snapshot ecology = status.ecology();
		player.sendSysMessage("Phantom ecology: enabled=" + ecology.enabled() + ", pendingCatchup=" + ecology.pendingCatchup() + ", failures=" + ecology.failures() + ".");
		player.sendSysMessage("Phantom presence: available=" + status.presence().available() + ", busy=" + status.presence().busy() + ", offline=" + status.presence().offline() + "; activity=" + status.activityStateCounts() + ".");
		player.sendSysMessage("Phantom background: due=" + ecology.periodicDueCalls() + ", overdue=" + ecology.periodicOverdueCalls() + ", running=" + ecology.periodicRunning() + ", blocked=" + ecology.periodicBlockedCalls() + ", queued=" + status.queueDue() + ", cadence=300-900s.");
		return true;
	}

	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
}
