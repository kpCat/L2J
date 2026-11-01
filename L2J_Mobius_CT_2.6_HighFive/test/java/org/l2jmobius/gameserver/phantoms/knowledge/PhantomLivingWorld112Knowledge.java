/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.List;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;

/** Immutable unit fixture at the existing knowledge boundary; never installed in a server. */
public final class PhantomLivingWorld112Knowledge
{
	public static PhantomGameKnowledgeQuery query()
	{
		final var authority = PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT;
		final var npc = new NpcFact(20001, 3, NpcKind.MONSTER, true, true, false, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		final var spawn = new SpawnFact(20001, 0, 0, 1, 2, 3, 1, 1, SpawnPointKind.EXACT, "field", null, authority);
		final var area = new SpawnAreaFact(20001, 0, "field", null, 1, 1, List.of(spawn), authority);
		final var snapshot = new PhantomGameKnowledgeSnapshot("living112-fixture", 1, "t", PhantomGameKnowledgePolicy.productionDefaults(),
			List.of(), List.of(npc), List.of(), List.of(spawn), List.of(area), List.of(), List.of(), List.of(), List.of(), List.of());
		final var service = new PhantomGameKnowledgeService(() -> snapshot);
		if (!service.start()) { throw new AssertionError("TASK112_KNOWLEDGE_START"); }
		return service.query();
	}
}
