/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.EffectHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomMasterHandlerJava8CompatibilityGoal030CP2ASuite implements PhantomTestSuite
{
	private static final long SEED = 30003021L;

	@Override
	public String id()
	{
		return "master-handler-java8-compat-goal030cp2a";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP2A suite used the wrong seed.");
		ConfigLoader.init();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-canonical-master-handler-registers-native-whisper", this::testCanonicalMasterHandler);
	}

	private void testCanonicalMasterHandler(PhantomTestContext context) throws Exception
	{
		EffectHandler.getInstance().executeScript();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		final IChatHandler nativeWhisper = ChatHandler.getInstance().getHandler(ChatType.WHISPER);
		PhantomAssertions.assertTrue(nativeWhisper != null, "Native WHISPER handler is absent after canonical MasterHandler execution.");
		PhantomAssertions.assertEquals("handlers.chat.channels.ChatWhisper", nativeWhisper.getClass().getName(), "Canonical WHISPER handler class drifted.");
		context.record("goal030cp2a.masterHandler", ScriptEngine.MASTER_HANDLER_FILE + ",whisper=" + nativeWhisper.getClass().getName());
	}
}
