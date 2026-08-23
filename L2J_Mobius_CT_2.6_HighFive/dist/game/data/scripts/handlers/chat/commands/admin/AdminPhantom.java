/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package handlers.chat.commands.admin;

import java.util.StringJoiner;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.SelectionStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Snapshot;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorStatus;

public class AdminPhantom implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_phantom"
	};

	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		final String arguments = command.length() > 13 ? command.substring(13).trim() : "";
		if (arguments.equals("enable"))
		{
			sendControl(activeChar, "enable", PhantomSystem.operatorEnable());
			return true;
		}
		if (arguments.equals("drain"))
		{
			sendControl(activeChar, "drain", PhantomSystem.operatorDrain());
			return true;
		}
		if (arguments.equals("disable"))
		{
			sendControl(activeChar, "disable", PhantomSystem.operatorDisable());
			return true;
		}
		if (arguments.equals("status"))
		{
			sendStatus(activeChar);
			return true;
		}
		if (arguments.equals("trace clear"))
		{
			final Snapshot trace = PhantomSystem.clearOperatorTrace();
			activeChar.sendSysMessage(trace.enabled() ? "Phantom trace selection and history cleared." : "Phantom trace is disabled; storage remains zero.");
			return true;
		}
		if (arguments.startsWith("trace "))
		{
			final long profileId;
			try
			{
				profileId = Long.parseLong(arguments.substring(6).trim());
			}
			catch (NumberFormatException e)
			{
				sendUsage(activeChar);
				return false;
			}
			final SelectionStatus result = PhantomSystem.selectOperatorTrace(profileId);
			if (result == SelectionStatus.SELECTED)
			{
				activeChar.sendSysMessage("Phantom trace selected profileId=" + profileId + ".");
				sendTrace(activeChar, PhantomSystem.operatorStatus().selectedTrace());
			}
			else if (result == SelectionStatus.DISABLED)
			{
				activeChar.sendSysMessage("Phantom trace is disabled; storage remains zero.");
			}
			else
			{
				activeChar.sendSysMessage("Attached Phantom profile not found: " + profileId + ".");
			}
			return result == SelectionStatus.SELECTED;
		}
		sendUsage(activeChar);
		return false;
	}

	private static void sendControl(Player activeChar, String action, OperatorControlResult result)
	{
		activeChar.sendSysMessage("Phantom " + action + ": result=" + result.code() + ", desiredMode=" + result.desiredMode() + ", desiredRunning=" + result.desiredRuntimeEnabled() + ", runtimeConfigured=" + result.runtimeConfigured() + ", runtime=" + result.runtimeState() + ".");
	}

	private static void sendStatus(Player activeChar)
	{
		final OperatorStatus status = PhantomSystem.operatorStatus();
		activeChar.sendSysMessage("Phantom status: configured=" + status.configuredEnabled() + ", diagnostics=" + status.diagnosticsEnabled() + ", operatorMode=" + status.operatorMode() + ", desiredRunning=" + status.desiredRuntimeEnabled() + ", runtimeConfigured=" + status.runtimeConfigured() + ", runtime=" + status.runtimeState() + ".");
		activeChar.sendSysMessage("Phantom execution: scheduler=" + status.schedulerState() + ", decision=" + status.decisionState() + ", active=" + status.activeCurrent() + ", activePeak=" + status.activePeak() + ".");
		final var states = status.activityStateCounts();
		activeChar.sendSysMessage("Phantom activity: ACTIVE=" + states.get(0) + ", NEARBY_PERCEPTIBLE=" + states.get(1) + ", WARM=" + states.get(2) + ", BACKGROUND=" + states.get(3) + ", SLEEPING=" + states.get(4) + ".");
		activeChar.sendSysMessage("Phantom load: overload=" + status.overloadLevel() + ", overloadPeak=" + status.peakOverloadLevel() + ", ready=" + status.queueReady() + ", due=" + status.queueDue() + ", capacity=" + status.queueCapacity() + ", accepted=" + status.queueAccepted() + ", rejected=" + status.queueRejected() + ".");
		activeChar.sendSysMessage("Phantom shutdownFailures=" + status.shutdownFailures() + ".");
		sendTrace(activeChar, status.selectedTrace());
	}

	private static void sendTrace(Player activeChar, Snapshot trace)
	{
		activeChar.sendSysMessage("Phantom selected trace: enabled=" + trace.enabled() + ", profileId=" + trace.selectedProfileId() + ", attached=" + trace.attached() + ", size=" + trace.history().size() + "/" + trace.capacity() + ", recorded=" + trace.recorded() + ", dropped=" + trace.dropped() + ", health=" + trace.health() + ", ageMs=" + trace.ageMillis() + ", slowMs=" + trace.slowThresholdMillis() + ", stuckMs=" + trace.stuckThresholdMillis() + ".");
		if (trace.current() != null)
		{
			activeChar.sendSysMessage("Phantom current: " + format(trace.current()));
			activeChar.sendSysMessage("Phantom candidates: " + candidates(trace.current()));
		}
		for (DecisionView entry : trace.history())
		{
			activeChar.sendSysMessage("Phantom trace: " + format(entry) + " candidates=" + candidates(entry) + ".");
		}
	}

	private static String format(DecisionView view)
	{
		return "activity=" + view.activityState() + ", profileId=" + view.profileId() + ", goal=" + view.goalId() + "/" + view.goalType() + "/r" + view.goalRevision() + "/" + view.goalStatus() + ", runtime=" + view.runtimeState() + ", decision=" + view.decisionSequence() + ", candidate=" + view.candidateKey() + ", score=" + view.score() + ", plan=" + view.planId() + ", step=" + view.step() + ", attempt=" + view.attempt() + ", result=" + view.lastResult() + ", reason=" + view.reasonKey() + ".";
	}

	private static String candidates(DecisionView view)
	{
		final StringJoiner result = new StringJoiner(", ");
		view.topCandidates().forEach(candidate -> result.add(candidate.candidateKey() + ":" + candidate.score() + ":" + candidate.status() + ":" + candidate.reasonKey()));
		return result.length() == 0 ? "none" : result.toString();
	}

	private static void sendUsage(Player activeChar)
	{
		activeChar.sendSysMessage("Usage: //phantom enable | //phantom drain | //phantom disable | //phantom status | //phantom trace <profileId> | //phantom trace clear");
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
