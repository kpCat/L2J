/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.CancelReturnConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomAreaControl131Checks
{
	private record Target(int objectId) { }

	public static void main(String[] args) throws Exception
	{
		final Target primary = new Target(101);
		final Target second = new Target(102);
		final Target replacement = new Target(102);
		final Target outsider = new Target(103);
		final List<Target> approved = List.of(primary, second);
		final List<Target> nativeList = new ArrayList<>(approved);
		final List<Target> pinned = PhantomAreaControl131.pinNativeRecipients(nativeList, approved, primary, 2);
		require(pinned.size() == 2 && pinned.get(0) == primary && pinned.get(1) == second, "exact stock AoE recipients");
		require(PhantomAreaControl131.pinNativeRecipients(List.of(primary, outsider), approved, primary, 2).isEmpty(), "no unapproved stock recipient");
		require(PhantomAreaControl131.pinNativeRecipients(List.of(primary, replacement), approved, primary, 2).isEmpty(), "same objectId is not the approved object");
		require(PhantomAreaControl131.pinNativeRecipients(List.of(primary, primary), approved, primary, 2).isEmpty(), "duplicate stock recipient");
		require(!PhantomAreaControl131.sameNativeRecipients(pinned, List.of(primary)), "native range prune invalidates AoE");
		require(!PhantomAreaControl131.sameNativeRecipients(pinned, List.of(primary, replacement)), "native recipient swap invalidates AoE");
		require(!PhantomAreaControl131.sameNativeRecipients(pinned, List.of(second, primary)), "native recipient order is pinned");
		require(PhantomAreaControl131.sameNativeRecipients(pinned, List.of(primary, second)), "exact list remains valid");
		nativeList.set(1, outsider);
		require(pinned.get(1) == second && !PhantomAreaControl131.sameNativeRecipients(pinned, nativeList), "published targets cannot alter the pinned list");
		require(PhantomAreaControl131.pinNativeRecipients(List.of(primary), List.of(primary), primary, 1).size() == 1, "exact cleanse singleton");
		require(PhantomAreaControl131.pinNativeRecipients(List.of(primary, second), List.of(primary), primary, 1).isEmpty(), "cleanse cannot fan out");
		final var pinnedSpawn = new PlayerNativeEvidence.Target(101, 0, 3);
		require(PhantomAreaControl131.sameMonsterSpawn(pinnedSpawn, new PlayerNativeEvidence.Target(101, 0, 3)), "same native spawn generation");
		require(!PhantomAreaControl131.sameMonsterSpawn(pinnedSpawn, new PlayerNativeEvidence.Target(101, 0, 4)), "stock NPC object reuse must not authorize a respawn");
		require(!PhantomAreaControl131.permitsResurrectionAnswer(null, null), "a request outside an exact scoped effect must not receive an automatic answer");
		System.out.println("TASK131_D3_EXACT_NATIVE_CAST_RECIPIENTS_PASS");
		if (args.length == 1 && "--stock".equals(args[0])) { stockSkillShapes(); }
	}

	/** Optional offline catalog check, run with dist/game as working directory. */
	private static void stockSkillShapes() throws Exception
	{
		final Path datapack = Path.of(".").toRealPath();
		ServerConfig.DATAPACK_ROOT = datapack.toFile();
		ServerConfig.SCRIPT_ROOT = datapack.resolve("data/scripts").toFile();
		ScriptEngine.getInstance().executeScript(ScriptEngine.EFFECT_MASTER_HANDLER_FILE);
		final var catalog = SkillData.getInstance();
		require(PhantomAreaControl131.supportsArea(catalog.getSkill(1171, 1)), "stock Blazing Circle Aura damage");
		require(PhantomAreaControl131.supportsArea(catalog.getSkill(1295, 1)), "stock Aqua Splash Area damage");
		require(PhantomAreaControl131.supportsArea(catalog.getSkill(1072, 1)), "stock Sleeping Cloud Area control");
		require(!PhantomAreaControl131.supportsArea(catalog.getSkill(1177, 1)), "stock Wind Strike is single-target");
		System.out.println("TASK131_STOCK_AREA_AND_CONTROL_SHAPES_PASS");
		final var cleanse = catalog.getSkill(1409, 1);
		require(PhantomSupportEffectAuthority.supportsCapability(cleanse, "combat.cleanse"), "stock Cleanse chooses the native debuff category");
		require(PhantomSupportEffectAuthority.supportsCapability(catalog.getSkill(1018, 3), "combat.cleanse"), "stock Purify uses the native abnormal slot bounds");
		require(!PhantomSupportEffectAuthority.supportsCapability(catalog.getSkill(1056, 1), "combat.cleanse"), "hostile buff Cancellation cannot be a friendly cleanse");
		final boolean previous = CancelReturnConfig.CANCEL_RETURN_ON;
		try
		{
			CancelReturnConfig.CANCEL_RETURN_ON = true;
			require(!PhantomSupportEffectAuthority.supportsCapability(cleanse, "combat.cleanse"), "unscoped delayed reapplication cannot be part of guarded Cleanse");
		}
		finally
		{
			CancelReturnConfig.CANCEL_RETURN_ON = previous;
		}
		System.out.println("TASK131_STOCK_EXACT_CLEANSE_SHAPES_PASS");
		require(PhantomAreaControl131.supportsSingleResurrection(catalog.getSkill(1016, 1)), "stock learned Resurrection is an exact PC_BODY action");
		require(!PhantomAreaControl131.supportsSingleResurrection(catalog.getSkill(1254, 1)), "Mass Resurrection cannot use the exact single-corpse answer");
		require(!PhantomAreaControl131.supportsSingleResurrection(cleanse), "an unrelated friendly skill cannot answer a resurrection proposal");
		System.out.println("TASK131_STOCK_SINGLE_CORPSE_SHAPE_PASS");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
