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
package org.l2jmobius.gameserver.phantoms.population;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;

/**
 * Narrow persistence boundary used by the population owner and deterministic
 * in-memory scale tests.
 */
public interface PhantomPopulationPersistencePort
{
	List<ManagedSnapshot> loadManagedAfter(long exclusiveProfileId, int pageSize);

	ManagedSnapshot createShell(long generation, long creationOrdinal, long deterministicSeed);

	ManagedSnapshot reload(long profileId);

	CreationResult advanceCreation(ManagedSnapshot current);

	ManagedSnapshot updateState(ManagedSnapshot current, PhantomPopulationState next);

	PopulationInitializationContract validateAuthority(ManagedSnapshot snapshot);
}
