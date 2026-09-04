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

import java.util.List;
import java.util.StringJoiner;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDecisionReplay.ReplayResult;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.CountDelta;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.CurrentOperation;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.ReceiptView;
import org.l2jmobius.gameserver.phantoms.PhantomEconomicAuditView.RetainedSummary;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.SelectionStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Snapshot;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetPreview;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorEconomicAudit;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorReplayResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.AuditRecord;

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
		if (arguments.equals("replay capture"))
		{
			sendReplay(activeChar, PhantomSystem.operatorReplayCapture());
			return true;
		}
		if (arguments.equals("replay run"))
		{
			sendReplay(activeChar, PhantomSystem.operatorReplayRun());
			return true;
		}
		if (arguments.equals("replay clear"))
		{
			sendReplay(activeChar, PhantomSystem.operatorReplayClear());
			return true;
		}
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
		if (arguments.equals("reset preview"))
		{
			sendResetPreview(activeChar, PhantomSystem.operatorResetPreview());
			return true;
		}
		if (arguments.equals("reset cancel"))
		{
			activeChar.sendSysMessage(PhantomSystem.operatorResetCancel() ? "Phantom reset confirmation cancelled." : "Phantom reset: no confirmation is armed.");
			return true;
		}
		if (arguments.startsWith("reset confirm "))
		{
			final String[] values = arguments.substring("reset confirm ".length()).trim().split("\\s+");
			if ((values.length < 1) || (values.length > 2) || ((values.length == 2) && !values[1].equals("reseed")))
			{
				sendUsage(activeChar);
				return false;
			}
			sendResetResult(activeChar, PhantomSystem.operatorResetConfirm(values[0], values.length == 2));
			return true;
		}
		if (arguments.equals("status"))
		{
			sendStatus(activeChar);
			return true;
		}
		if (arguments.startsWith("economy "))
		{
			final long profileId;
			try
			{
				profileId = Long.parseLong(arguments.substring(8).trim());
			}
			catch (NumberFormatException e)
			{
				sendUsage(activeChar);
				return false;
			}
			sendEconomicAudit(activeChar, PhantomSystem.operatorEconomicAudit(profileId));
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

	private static void sendReplay(Player activeChar, OperatorReplayResult result)
	{
		activeChar.sendSysMessage("Phantom replay: result=" + result.code() + ", profileId=" + result.profileId() + ", frames=" + result.frameCount() + ", digest=" + (result.digest() == null ? "none" : result.digest()) + ".");
		if (result.replay() != null)
		{
			final ReplayResult replay = result.replay();
			activeChar.sendSysMessage("Phantom replay diagnostics: health=" + replay.finalHealth() + ", slow/stuck/attention=" + replay.firstSlowFrame() + "/" + replay.firstStuckFrame() + "/" + replay.firstAttentionFrame() + ", candidates=" + replay.candidateVerified() + "/" + replay.candidateUnverifiable() + "/" + replay.candidateMismatch() + ".");
			if (replay.firstFailureFrame() >= 0)
			{
				activeChar.sendSysMessage("Phantom replay failure: frame=" + replay.firstFailureFrame() + ", reason=" + replay.failureReason() + ".");
			}
		}
	}

	private static void sendControl(Player activeChar, String action, OperatorControlResult result)
	{
		activeChar.sendSysMessage("Phantom " + action + ": result=" + result.code() + ", desiredMode=" + result.desiredMode() + ", desiredRunning=" + result.desiredRuntimeEnabled() + ", runtimeConfigured=" + result.runtimeConfigured() + ", runtime=" + result.runtimeState() + ".");
	}

	private static void sendResetPreview(Player activeChar, ResetPreview preview)
	{
		activeChar.sendSysMessage("Phantom reset preview: safe=" + preview.safe() + ", identities=" + preview.identities() + ", characters=" + preview.characters() + ", accounts=" + preview.accounts() + ", snapshot=" + preview.snapshotHash() + ".");
		activeChar.sendSysMessage("Phantom reset will delete/detach: " + preview.deleteCounts() + ".");
		activeChar.sendSysMessage("Phantom reset will preserve world/history: " + preview.preserveCounts() + ".");
		if (!preview.blockers().isEmpty())
		{
			activeChar.sendSysMessage("Phantom reset blocked: " + preview.blockers() + ".");
			return;
		}
		activeChar.sendSysMessage("Phantom reset armed until epochMs=" + preview.expiresAt() + ". Confirm once with //phantom reset confirm " + preview.confirmationToken() + " [reseed].");
	}

	private static void sendResetResult(Player activeChar, ResetResult result)
	{
		activeChar.sendSysMessage("Phantom reset: result=" + result.code() + ", identities=" + result.identities() + ", committed=" + result.resetCommitted() + ", reseeded=" + result.reseeded() + ", detail=" + result.detail());
	}

	private static void sendStatus(Player activeChar)
	{
		final OperatorStatus status = PhantomSystem.operatorStatus();
		activeChar.sendSysMessage("Phantom status: configured=" + status.configuredEnabled() + ", diagnostics=" + status.diagnosticsEnabled() + ", operatorMode=" + status.operatorMode() + ", desiredRunning=" + status.desiredRuntimeEnabled() + ", runtimeConfigured=" + status.runtimeConfigured() + ", runtime=" + status.runtimeState() + ".");
		activeChar.sendSysMessage("Phantom execution: scheduler=" + status.schedulerState() + ", decision=" + status.decisionState() + ", active=" + status.activeCurrent() + ", activePeak=" + status.activePeak() + ".");
		final List<Long> states = status.activityStateCounts();
		activeChar.sendSysMessage("Phantom activity: ACTIVE=" + states.get(0) + ", NEARBY_PERCEPTIBLE=" + states.get(1) + ", WARM=" + states.get(2) + ", BACKGROUND=" + states.get(3) + ", SLEEPING=" + states.get(4) + ".");
		activeChar.sendSysMessage("Phantom load: overload=" + status.overloadLevel() + ", overloadPeak=" + status.peakOverloadLevel() + ", ready=" + status.queueReady() + ", due=" + status.queueDue() + ", capacity=" + status.queueCapacity() + ", accepted=" + status.queueAccepted() + ", rejected=" + status.queueRejected() + ".");
		activeChar.sendSysMessage("Phantom shutdownFailures=" + status.shutdownFailures() + ".");
		sendTrace(activeChar, status.selectedTrace());
	}

	private static void sendEconomicAudit(Player activeChar, OperatorEconomicAudit result)
	{
		activeChar.sendSysMessage("Phantom economy audit: profileId=" + result.profileId() + ", result=" + result.code() + ".");
		if (result.snapshot() == null)
		{
			return;
		}
		final PhantomEconomicAuditView.Snapshot snapshot = result.snapshot();
		final CurrentOperation current = snapshot.current();
		if (current.status() == PhantomEconomicAuditView.CurrentStatus.AVAILABLE)
		{
			activeChar.sendSysMessage("Phantom economy current: operation=" + current.operationId() + ", goal=" + current.goalId() + "/r" + current.goalRevision() + ", kind=" + current.kind() + ", state=" + current.state() + ", attempt=" + current.attempt() + ", reservations=" + current.reservationCount() + ".");
		}
		else
		{
			activeChar.sendSysMessage("Phantom economy current: " + current.status().name().toLowerCase() + ".");
		}
		final RetainedSummary summary = snapshot.retainedSummary();
		activeChar.sendSysMessage("Phantom economy retained-window (max 256, not lifetime): rows=" + summary.retainedRows() + ", states=" + summary.stateCounts() + ", itemsConsumed=" + summary.itemsConsumed() + ", itemsProduced=" + summary.itemsProduced() + ", adenaSource=" + summary.adenaSource() + ", adenaSink=" + summary.adenaSink() + ", crystalsProduced=" + summary.crystalsProduced() + ", targetItemsDestroyed=" + summary.targetItemsDestroyed() + ", totalsSaturated=" + summary.totalsSaturated() + ".");
		for (int index = 0; index < Math.min(PhantomEconomicAuditView.RENDER_LIMIT, snapshot.newestAudit().size()); index++)
		{
			final AuditRecord audit = snapshot.newestAudit().get(index);
			activeChar.sendSysMessage("Phantom economy retained row: auditId=" + audit.auditId() + ", operation=" + audit.operationId() + ", kind=" + audit.kind() + ", state=" + audit.state() + ", result=" + audit.result() + ", reason=" + audit.reason() + ", items=" + audit.itemsConsumed() + "/" + audit.itemsProduced() + ", adena=" + audit.adenaSource() + "/" + audit.adenaSink() + ", crystals=" + audit.crystalsProduced() + ", destroyed=" + audit.targetItemsDestroyed() + ".");
		}
		final ReceiptView receipt = snapshot.latestReceipt();
		if (receipt == null)
		{
			activeChar.sendSysMessage("Phantom economy latest receipt: none.");
			return;
		}
		activeChar.sendSysMessage("Phantom economy latest receipt: operation=" + receipt.operationKey() + ", goal=" + receipt.goalId() + "/r" + receipt.goalRevision() + ", kind=" + receipt.kind() + ", state=" + receipt.state() + ", resume=" + receipt.resumeCount() + ".");
		activeChar.sendSysMessage("Phantom economy receipt deltas: primary=" + delta(receipt.primary()) + ", secondary=" + delta(receipt.secondary()) + ", object=" + delta(receipt.object()) + ", positionChanged=" + receipt.positionChanged() + ".");
	}

	private static String delta(CountDelta delta)
	{
		return delta.before() + "->" + delta.expectedAfter() + " (" + (delta.delta() >= 0 ? "+" : "") + delta.delta() + ")";
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
		activeChar.sendSysMessage("Usage: //phantom enable | //phantom drain | //phantom disable | //phantom reset preview | //phantom reset confirm <TOKEN> [reseed] | //phantom reset cancel | //phantom status | //phantom trace <profileId> | //phantom trace clear | //phantom replay capture | //phantom replay run | //phantom replay clear | //phantom economy <profileId>");
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
