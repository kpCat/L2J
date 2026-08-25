/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveredObservation;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchHandle;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomPlayerMaterializationSpike;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerFixture;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomHeadlessWhisperDeliveryGoal030CP2CSuite implements PhantomTestSuite
{
	private static final long SEED = 30003023L;
	private static final String TEXT = "cp2c whisper";
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private IChatHandler _whisper;

	@Override
	public String id()
	{
		return "headless-whisper-delivery-goal030cp2c";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP2C suite used the wrong seed.");
		_environment.initialize(context);
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		_whisper = ChatHandler.getInstance().getHandler(ChatType.WHISPER);
		PhantomAssertions.assertTrue(_whisper != null, "Native WHISPER handler is absent after canonical MasterHandler execution.");
		PhantomAssertions.assertEquals("handlers.chat.channels.ChatWhisper", _whisper.getClass().getName(), "Canonical WHISPER handler class drifted.");
		context.record("goal030cp2c.masterHandler", ScriptEngine.MASTER_HANDLER_FILE + ",whisper=" + _whisper.getClass().getName());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-headless-receiver-is-deliverable", this::testHeadlessReceiverDelivery);
		registry.add("02-transportless-receiver-remains-offline", this::testTransportlessReceiverRejected);
		registry.add("03-chat-whisper-source-owns-offline-semantics", this::testSourceContract);
	}

	private void testHeadlessReceiverDelivery(PhantomTestContext context) throws Exception
	{
		final HeadlessPlayerOutboundSession receiverOutput = new HeadlessPlayerOutboundSession(16, 128, 32);
		final HeadlessPlayerOutboundSession senderOutput = new HeadlessPlayerOutboundSession(16, 128, 32);
		final PhantomPlayerMaterializationSpike receiverSpike = spike(_environment.primary(), receiverOutput);
		final PhantomPlayerMaterializationSpike senderSpike = spike(_environment.observer(), senderOutput);
		final List<DeliveredObservation> delivered = new ArrayList<>();
		Player receiver = null;
		Player sender = null;
		try
		{
			receiverSpike.materialize();
			senderSpike.materialize();
			receiver = receiverSpike.getPlayer();
			sender = senderSpike.getPlayer();
			PhantomAssertions.assertEquals(receiver, World.getInstance().getPlayer(receiver.getName()), "Headless receiver is not World-resolved by name.");
			PhantomAssertions.assertEquals(null, receiver.getClient(), "Headless receiver unexpectedly has a GameClient.");
			PhantomAssertions.assertTrue(receiver.hasHeadlessOutboundSession(), "Headless receiver has no HEADLESS outbound session.");
			PhantomAssertions.assertFalse(receiver.isInOfflineMode(), "Headless receiver was classified as offline.");

			final long receiverEffectsBefore = receiverOutput.snapshot().effectCount();
			final long senderEffectsBefore = senderOutput.snapshot().effectCount();
			final int receiverCreatureSayBefore = packetCount(receiverOutput, "CreatureSay");
			final int receiverObjectId = receiver.getObjectId();
			final int senderCreatureSayBefore = packetCount(senderOutput, "CreatureSay");
			final DispatchHandle dispatch = ChatObservationService.getInstance().openClientDispatch(sender.getObjectId(), sender.getName(), ChatType.WHISPER, receiver.getName(), TEXT, System.currentTimeMillis());
			try (AutoCloseable registration = ChatObservationService.getInstance().register(observation ->
			{
				delivered.add(observation);
				return true;
			}); dispatch)
			{
				_whisper.onChat(ChatType.WHISPER, sender, receiver.getName(), TEXT);
			}

			PhantomAssertions.assertEquals(receiverEffectsBefore + 1, receiverOutput.snapshot().effectCount(), "Headless receiver did not execute exactly one WHISPER packet effect.");
			PhantomAssertions.assertEquals(senderEffectsBefore + 1, senderOutput.snapshot().effectCount(), "Human sender did not receive exactly one canonical WHISPER echo.");
			PhantomAssertions.assertEquals(receiverCreatureSayBefore + 1, packetCount(receiverOutput, "CreatureSay"), "Headless receiver did not execute CreatureSay exactly once.");
			PhantomAssertions.assertEquals(senderCreatureSayBefore + 1, packetCount(senderOutput, "CreatureSay"), "Human sender did not receive the canonical CreatureSay echo.");
			PhantomAssertions.assertEquals(2, dispatch.deliveries(), "Canonical WHISPER dispatch did not publish both real delivery effects.");
			PhantomAssertions.assertTrue(delivered.stream().anyMatch(observation -> observation.recipientObjectId() == receiverObjectId), "ChatObservationService did not publish the headless receiver delivery.");
			context.record("goal030cp2c.headless", "handler=" + _whisper.getClass().getName() + ",client=null,headless=" + receiver.hasHeadlessOutboundSession() + ",offline=" + receiver.isInOfflineMode() + ",receiverEffects=1,receiverCreatureSay=1,dispatchDeliveries=" + dispatch.deliveries());
		}
		finally
		{
			senderSpike.cleanup();
			receiverSpike.cleanup();
		}
		_environment.assertClean(_environment.observer(), sender);
		_environment.assertClean(_environment.primary(), receiver);
	}

	private void testTransportlessReceiverRejected(PhantomTestContext context) throws Exception
	{
		final HeadlessPlayerOutboundSession senderOutput = new HeadlessPlayerOutboundSession(16, 128, 16);
		final PhantomPlayerMaterializationSpike senderSpike = spike(_environment.primary(), senderOutput);
		Player sender = null;
		Player receiver = null;
		try
		{
			senderSpike.materialize();
			sender = senderSpike.getPlayer();
			receiver = Player.load(_environment.observer().objectId());
			PhantomAssertions.assertTrue(receiver != null, "Could not load the transportless receiver fixture.");
			receiver.setOnlineStatus(true, true);
			receiver.spawnMe();
			PhantomAssertions.assertEquals(receiver, World.getInstance().getPlayer(receiver.getName()), "Transportless receiver is not World-visible.");
			PhantomAssertions.assertEquals(null, receiver.getClient(), "Transportless receiver unexpectedly has a GameClient.");
			PhantomAssertions.assertFalse(receiver.hasHeadlessOutboundSession(), "Transportless receiver unexpectedly has a HEADLESS outbound session.");
			PhantomAssertions.assertTrue(receiver.isInOfflineMode(), "Transportless receiver was not classified as offline.");

			final long senderEffectsBefore = senderOutput.snapshot().effectCount();
			final int senderSystemMessageBefore = packetCount(senderOutput, "SystemMessage");
			final DispatchHandle dispatch = ChatObservationService.getInstance().openClientDispatch(sender.getObjectId(), sender.getName(), ChatType.WHISPER, receiver.getName(), TEXT, System.currentTimeMillis());
			try (dispatch)
			{
				_whisper.onChat(ChatType.WHISPER, sender, receiver.getName(), TEXT);
			}

			PhantomAssertions.assertEquals(0, dispatch.deliveries(), "Transportless receiver incorrectly received a WHISPER delivery effect.");
			PhantomAssertions.assertEquals(senderEffectsBefore + 1, senderOutput.snapshot().effectCount(), "Offline rejection did not emit exactly one sender result.");
			PhantomAssertions.assertEquals(senderSystemMessageBefore + 1, packetCount(senderOutput, "SystemMessage"), "Offline rejection did not use the existing sender SystemMessage path.");
			context.record("goal030cp2c.offlineNegative", "client=null,headless=" + receiver.hasHeadlessOutboundSession() + ",offline=" + receiver.isInOfflineMode() + ",deliveries=" + dispatch.deliveries() + ",senderSystemMessage=1");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(receiver);
			senderSpike.cleanup();
		}
		_environment.assertClean(_environment.observer(), receiver);
		_environment.assertClean(_environment.primary(), sender);
	}

	private void testSourceContract(PhantomTestContext context) throws Exception
	{
		final Path source = context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/channels/ChatWhisper.java");
		final String content = Files.readString(source);
		PhantomAssertions.assertTrue(content.contains("if (receiver.isInOfflineMode())"), "ChatWhisper does not use Player's canonical offline predicate.");
		PhantomAssertions.assertFalse(content.contains("receiver.getClient()"), "ChatWhisper still owns a direct receiver GameClient predicate.");
		context.record("goal030cp2c.sourceContract", "receiver.isInOfflineMode=true,receiver.getClient=false");
	}

	private static PhantomPlayerMaterializationSpike spike(PhantomHeadlessPlayerFixture fixture, HeadlessPlayerOutboundSession output)
	{
		return new PhantomPlayerMaterializationSpike(fixture.objectId(), PhantomIdentityLeaseRegistry.getInstance(), output, new PhantomActionFacade(), PhantomPlayerMaterializationSpike.FailureInjector.none());
	}

	private static int packetCount(HeadlessPlayerOutboundSession output, String packetClass)
	{
		return (int) output.snapshot().recordedPacketClasses().stream().filter(packetClass::equals).count();
	}
}
