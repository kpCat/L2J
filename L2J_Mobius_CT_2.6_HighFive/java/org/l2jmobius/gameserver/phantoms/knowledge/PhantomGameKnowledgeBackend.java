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
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassIntrinsicFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.RecipeFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;

/**
 * One build-time copy seam over authoritative server loaders.
 */
public interface PhantomGameKnowledgeBackend
{
	BackendData load(PhantomGameKnowledgePolicy policy);

	boolean sourceExists(String relativeDatapackPath);

	record BackendData(List<ItemFact> items, List<NpcFact> npcs, List<DropFact> drops, List<SpawnFact> spawns, List<RecipeFact> recipes, List<ClassIntrinsicFact> classes, Map<Integer, List<SkillEvidence>> completeClassSkills)
	{
		public BackendData
		{
			items = List.copyOf(items);
			npcs = List.copyOf(npcs);
			drops = List.copyOf(drops);
			spawns = List.copyOf(spawns);
			recipes = List.copyOf(recipes);
			classes = List.copyOf(classes);
			final HashMap<Integer, List<SkillEvidence>> copiedSkills = new HashMap<>();
			completeClassSkills.forEach((classId, skills) -> copiedSkills.put(classId, List.copyOf(skills)));
			completeClassSkills = Map.copyOf(copiedSkills);
		}
	}
}
