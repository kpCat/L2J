/* Copyright (c) 2013 L2jMobius */
package fixtures.economy070;

/** Exact two native constructors used by QuestMasterHandler, without unrelated quest initialization. */
public final class NativeQuestBootstrap070
{
	public static void main(String[] args)
	{
		if (!Boolean.getBoolean("phantom.task070.native")) { throw new IllegalArgumentException("Own070 native fixture only."); }
		new quests.Q00234_FatesWhisper.Q00234_FatesWhisper();
		new quests.Q00235_MimirsElixir.Q00235_MimirsElixir();
	}
}
