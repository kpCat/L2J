/*
 * Copyright (c) 2013 L2jMobius
 */
package handlers.chat.commands.voiced;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorAdmissionProfile;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorLocalityTarget;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorStatus;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;

/** Read-only native admission status for a personal allowlisted real player. */
public class PhantomStatus implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"phantomstatus",
		"phantomlocalproof"
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
		if (command.equals("phantomlocalproof"))
		{
			return localProof(player, target);
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
			player.sendSysMessage("Phantom profile " + profileId + ": scheduler=" + (profile.scheduler() == null ? "absent" : profile.scheduler().effectiveState() + "/" + profile.scheduler().transitionStatus() + "/" + profile.scheduler().lastResult()) + ", worldPresent=" + ((profile.materialization() != null) && profile.materialization().worldPresent()) + ", busyReason=" + profile.busyReason() + ", nativeFailure=" + profile.lastMaterializationFailure() + ".");
			return true;
		}
		final OperatorStatus status = PhantomSystem.operatorStatus();
		player.sendSysMessage("Phantom status: runtime=" + status.runtimeState() + ", at=" + status.admission().evaluatedAt() + ", desired=" + status.admission().desiredActive() + ", eligible=" + status.admission().eligibleActive() + ", admitted=" + status.admission().admittedActive() + ", effectiveACTIVE=" + status.activityStateCounts().get(0) + ", worldMaterialized=" + status.worldMaterialized() + ", target=" + status.admission().activeTarget() + ", cap=" + status.admission().materializedCap() + ", pendingRebalance=" + status.admission().pendingRebalance() + ".");
		final PhantomPopulationEcologyService.Snapshot ecology = status.ecology();
		player.sendSysMessage("Phantom ecology: owner=" + status.periodicOwner() + ", pendingCatchup=" + ecology.pendingCatchup() + ", failures=" + ecology.failures() + ", due=" + ecology.periodicDueCalls() + ", overdue=" + ecology.periodicOverdueCalls() + ", running=" + ecology.periodicRunning() + ", blocked=" + ecology.periodicBlockedCalls() + ", queued=" + status.queueDue() + ", cadence=300-900s.");
		player.sendSysMessage("Phantom presence: available=" + status.presence().available() + ", busy=" + status.presence().busy() + ", externalBusy=" + status.presence().externalBusy() + ", offline=" + status.presence().offline() + ", activity=" + status.activityStateCounts() + ".");
		player.sendSysMessage("Phantom topology: registered=" + status.registry().registered() + ", resolved=" + status.registry().resolved() + ", buckets=" + status.registry().occupiedNodeBuckets() + ", queryLast=" + status.registry().lastCandidatesExamined() + ", queryMax=" + status.registry().maximumCandidatesExamined() + ", localSignaled=" + status.localSignaled() + ".");
		return true;
	}

	private static synchronized boolean localProof(Player player, String target)
	{
		if ((target != null) && !target.isBlank())
		{
			player.sendSysMessage("Использование: .phantomlocalproof");
			return true;
		}
		if (!player.isOnline() || player.hasHeadlessOutboundSession())
		{
			player.sendSysMessage("Проверка локальности доступна только реальному клиенту.");
			return true;
		}
		final Path localPlayRoot = ServerConfig.DATAPACK_ROOT.toPath().toAbsolutePath().normalize().getParent();
		if ((localPlayRoot == null) || !Files.isRegularFile(localPlayRoot.resolve("local-play.json")))
		{
			player.sendSysMessage("Проверка локальности выключена вне LocalPlay.");
			return true;
		}
		final Path permit = localPlayRoot.resolve("LIVE003_LOCALITY_PROOF_ONCE.txt");
		final String expected = "LIVE-003-RUNTIME-SCALE-10000|" + ProcessHandle.current().pid() + "|" + player.getObjectId();
		try
		{
			if (!Files.isRegularFile(permit) || !expected.equals(Files.readString(permit).trim()))
			{
				player.sendSysMessage("Одноразовый допуск LIVE-003 отсутствует или не совпадает.");
				return true;
			}
			final PhantomTopologyPoint human = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			final OperatorLocalityTarget chosen = PhantomSystem.operatorNearestLocalityTarget(human).orElse(null);
			if (chosen == null)
			{
				player.sendSysMessage("Нет READY, eligible и admitted профиля с сохранённой позицией в этом instance.");
				return true;
			}
			final OperatorAdmissionProfile current = PhantomSystem.operatorAdmissionProfile(chosen.profileId()).orElse(null);
			if ((current == null) || !current.admission().admitted() || current.admission().pendingRebalance())
			{
				player.sendSysMessage("Admission выбранного профиля изменился; повторите команду.");
				return true;
			}
			Files.delete(permit);
			final PhantomTopologyPoint point = chosen.committedPosition();
			player.sendSysMessage("LIVE-003: профиль " + chosen.profileId() + ", сохранённая позиция " + point.x() + "," + point.y() + "," + point.z() + ", topology=" + chosen.topologyNodeId() + ". Перемещаю только реального персонажа.");
			player.teleToLocation(new Location(point.x(), point.y(), point.z(), player.getHeading(), point.instanceId()), false);
			return true;
		}
		catch (IOException exception)
		{
			player.sendSysMessage("Ошибка одноразового допуска LIVE-003: " + exception.getClass().getSimpleName());
			return true;
		}
	}

	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
}
