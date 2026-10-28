/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.List;
import java.util.Set;

/** Finite source-recipe selection shared by the two explicitly separate grant owners. */
public final class PhantomCraftRecipePolicy
{
    private PhantomCraftRecipePolicy() { }
    public record Recipe(int id, int rank) { }
    public static int startingClass(int kind, List<Integer> allowed, int fallback) { return kind == 2 && allowed.contains(53) ? 53 : fallback; }
    public static boolean creator(int classId) { return classId == 56 || classId == 57 || classId == 118; }
    public static List<Integer> select(List<Recipe> catalog, int rank, long offset, int quota, Set<Integer> learned)
    {
        if (quota < 1 || quota > 12 || rank < 0 || catalog.size() > 4096) { throw new IllegalArgumentException("Craft selection outside bounds."); }
        final var eligible = catalog.stream().filter(recipe -> recipe.rank() > 0 && recipe.rank() <= rank && !learned.contains(recipe.id())).sorted(java.util.Comparator.comparingInt(Recipe::id)).toList();
        final var result = new java.util.ArrayList<Integer>();
        for (int i = 0; i < Math.min(quota, eligible.size()); i++) { result.add(eligible.get((int) Math.floorMod(offset + i, eligible.size())).id()); }
        return result.stream().sorted().toList();
    }
}
