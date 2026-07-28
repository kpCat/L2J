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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.combat;

public enum PhantomCombatMode
{
	MELEE_PHYSICAL("combat.melee_damage", false),
	RANGED_PHYSICAL("combat.ranged_physical_damage", false),
	RANGED_MAGIC("combat.ranged_magic_damage", true);

	private final String _capabilityKey;
	private final boolean _magic;

	PhantomCombatMode(String capabilityKey, boolean magic)
	{
		_capabilityKey = capabilityKey;
		_magic = magic;
	}

	public String capabilityKey()
	{
		return _capabilityKey;
	}

	public boolean magic()
	{
		return _magic;
	}

	public static PhantomCombatMode fromCode(long code)
	{
		if (code == 1)
		{
			return MELEE_PHYSICAL;
		}
		if (code == 2)
		{
			return RANGED_PHYSICAL;
		}
		if (code == 3)
		{
			return RANGED_MAGIC;
		}
		throw new IllegalArgumentException("Unsupported combat mode.");
	}
}
