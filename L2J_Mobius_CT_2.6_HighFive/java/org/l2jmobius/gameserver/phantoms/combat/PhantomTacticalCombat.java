/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.LongSupplier;

/** Deterministic projection. Only the caller can attest and submit the first action. */
public final class PhantomTacticalCombat
{
	public static final int MAX_EXPANSIONS = 96;
	public static final int MAX_SOURCES = 32;
	private static final int OPTIONS = 12;
	private static final int WIDTH = 6;
	private static final int DEPTH = 3;
	private static final double HORIZON = 6;

	public enum Kind { PHYSICAL, MAGIC, DRAIN, PERIODIC, HEAL, DEFENSE, CONTROL, ROOT, SILENCE, INTERRUPT, DEBUFF, CHARGE, SOUL, MOVE, RETREAT, REST, WAIT, UNKNOWN }

	/** Amount is in native points, or a fractional multiplier for timed defense/debuff. */
	public record Effect(Kind kind, double amount, double seconds, double likelihood) { }
	public record Action(int id, int reuseGroup, boolean ready, boolean projectable, double seconds, double reuse,
		double mp, double hp, double itemPrice, int charges, int souls, boolean redundant, List<Effect> effects)
	{
		public Action { effects = List.copyOf(effects); }
	}
	public record Source(double dps, double burst, boolean incoming, boolean reachable, boolean casting, boolean primary,
		double arrivalSeconds, double confidence, boolean cpEligible)
	{
		public Source(double dps, double burst, boolean incoming, boolean reachable, boolean casting, boolean primary,
			double arrivalSeconds, double confidence)
		{
			this(dps, burst, incoming, reachable, casting, primary, arrivalSeconds, confidence, true);
		}
	}
	public record Scene(double hp, double maximumHp, double cp, double mp, double maximumMp, int charges, int souls,
		double targetHp, double maximumTargetHp, double targetRegen, double outgoingDps, boolean pvp, boolean truncated, List<Source> sources)
	{
		public Scene(double hp, double maximumHp, double cp, double mp, double maximumMp, int charges, int souls,
			double targetHp, double maximumTargetHp, double targetRegen, boolean pvp, boolean truncated, List<Source> sources)
		{
			this(hp, maximumHp, cp, mp, maximumMp, charges, souls, targetHp, maximumTargetHp, targetRegen, 0, pvp, truncated, sources);
		}
		public Scene { sources = List.copyOf(sources.subList(0, Math.min(MAX_SOURCES, sources.size()))); }
	}
	public record Threat(double lowDps, double incomingDps, double highDps, double pendingBurst, double confidence,
		double dangerSeconds, double neutralizeSeconds, int attackers, boolean truncated) { }
	public record Decision(Action first, Threat threat, int expansions, boolean budgetLimited) { }

	private record State(double hp, double cp, double mp, int charges, int souls, double enemyHp, double elapsed,
		double controlUntil, double rootUntil, double defenseUntil, double defense, double debuffUntil, double debuff,
		double periodicUntil, double periodic, boolean burstSettled, boolean ended) { }
	private record Step(Action action, Node parent, State state, double score) { }
	private record Node(Action first, Step last, double score, int depth) { }
	private record Option(Action action, double utility) { }

	private static final Comparator<Option> BEST_OPTION = Comparator.comparingDouble(Option::utility).reversed()
		.thenComparingInt(option -> option.action().id());
	private static final Comparator<Node> BEST_NODE = Comparator.comparingDouble(Node::score).reversed()
		.thenComparingInt(node -> node.first().id());

	private PhantomTacticalCombat() { }

	public static Threat threat(Scene scene)
	{
		double low = 0, expected = 0, high = 0, burst = 0, hpOnly = 0, hpBurst = 0;
		double confidence = scene.truncated() ? 0.45 : 1;
		int count = 0;
		for (Source source : scene.sources())
		{
			if (!source.incoming())
			{
				// A possible pull affects the upper band only.
				if (source.reachable()) { high += source.dps() * 0.3; }
				continue;
			}
			count++;
			confidence = Math.min(confidence, source.confidence());
			final double immediate = source.reachable() && source.arrivalSeconds() <= 1 ? source.dps() : 0;
			low += immediate * source.confidence() * 0.65;
			expected += immediate;
			if (!source.cpEligible()) { hpOnly += immediate; }
			high += (source.reachable() ? source.dps() : source.dps() * 0.15) * (2 - source.confidence());
			if (source.reachable() && source.casting())
			{
				burst += source.burst();
				if (!source.cpEligible()) { hpBurst += source.burst(); }
			}
		}
		if (scene.truncated()) { high += Math.max(high * 0.25, scene.maximumHp() * 0.04); }
		final double pool = scene.hp() + (scene.pvp() ? scene.cp() : 0);
		final double margin = Math.max(0, pool - burst - scene.maximumHp() * 0.18);
		final double hpMargin = Math.max(0, scene.hp() - hpBurst - scene.maximumHp() * 0.18);
		final double poolDanger = expected > 0 ? margin / expected : Double.POSITIVE_INFINITY;
		final double hpDanger = hpOnly > 0 ? hpMargin / hpOnly : Double.POSITIVE_INFINITY;
		final double net = scene.outgoingDps() - scene.targetRegen();
		return new Threat(low, expected, high, burst, confidence, Math.min(poolDanger, hpDanger),
			net > 0 ? scene.targetHp() / net : Double.POSITIVE_INFINITY, count, scene.truncated());
	}

	public static Decision choose(Scene scene, List<Action> actions, LongSupplier clock, long deadline)
	{
		final Threat threat = threat(scene);
		final State initial = new State(scene.hp(), scene.pvp() ? scene.cp() : 0, scene.mp(), scene.charges(), scene.souls(),
			scene.targetHp(), 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
		List<Node> beam = List.of(new Node(null, null, 0, 0));
		Node best = null;
		int expanded = 0;
		for (int depth = 0; depth < DEPTH; depth++)
		{
			final List<Node> next = new ArrayList<>(WIDTH * OPTIONS);
			for (Node parent : beam)
			{
				final State state = parent.last() == null ? initial : parent.last().state();
				if (state.ended()) { continue; }
				final List<Option> scored = new ArrayList<>(actions.size());
				for (Action action : actions)
				{
					if (clock.getAsLong() - deadline >= 0)
					{
						return new Decision(best == null ? null : best.first(), threat, expanded, true);
					}
					if (!feasible(parent, state, action)) { continue; }
					scored.add(new Option(action, utility(scene, threat, state, action)));
				}
				scored.sort(BEST_OPTION);
				for (Option option : diverse(scored))
				{
					if (expanded == MAX_EXPANSIONS || clock.getAsLong() - deadline >= 0)
					{
						return new Decision(best == null ? null : best.first(), threat, expanded, true);
					}
					expanded++;
					final Step step = apply(scene, threat, parent, state, option.action());
					final Node node = new Node(depth == 0 ? step.action() : parent.first(), step, step.score(), depth + 1);
					if (best == null || BEST_NODE.compare(node, best) < 0) { best = node; }
					next.add(node);
				}
			}
			if (next.isEmpty()) { break; }
			next.sort(BEST_NODE);
			beam = next.subList(0, Math.min(WIDTH, next.size()));
		}
		return new Decision(best == null ? null : best.first(), threat, expanded, false);
	}

	private static boolean feasible(Node parent, State state, Action action)
	{
		if (parent.depth() == 0 ? !action.ready() : !action.projectable()) { return false; }
		if (action.redundant() || action.effects().isEmpty() || state.hp() <= action.hp() || state.mp() < action.mp()
			|| state.charges() < action.charges() || state.souls() < action.souls() || state.elapsed() + action.seconds() > HORIZON)
		{
			return false;
		}
		for (Step last = parent.last(); last != null; last = last.parent().last())
		{
			if (last.action().reuseGroup() == action.reuseGroup() && (action.itemPrice() > 0
				|| state.elapsed() < last.state().elapsed() + Math.max(0, last.action().reuse() - last.action().seconds()))) { return false; }
		}
		return true;
	}

	private static double utility(Scene scene, Threat threat, State state, Action action)
	{
		double damage = 0, relief = 0;
		for (Effect effect : action.effects())
		{
			final double value = effect.amount() * effect.likelihood();
			switch (effect.kind())
			{
				case PHYSICAL, MAGIC -> damage += value;
				case PERIODIC -> damage += value * Math.min(Math.min(effect.seconds(), 2), threat.neutralizeSeconds());
				case HEAL -> relief += Math.min(value, scene.maximumHp() - state.hp());
				case CONTROL, INTERRUPT -> relief += effect.likelihood() * threat.pendingBurst();
				case ROOT -> relief += effect.likelihood() * threat.incomingDps() * 0.1;
				case DEFENSE -> relief += threat.incomingDps() * value;
				default -> { }
			}
		}
		return Math.min(damage, state.enemyHp()) / Math.max(1, scene.maximumTargetHp())
			+ relief / Math.max(1, scene.maximumHp()) - action.mp() / Math.max(1, scene.maximumMp())
			- action.itemPrice() - action.seconds() * 0.1;
	}

	private static List<Option> diverse(List<Option> scored)
	{
		if (scored.size() <= OPTIONS) { return scored; }
		final List<Option> result = new ArrayList<>(OPTIONS);
		final EnumSet<Kind> represented = EnumSet.noneOf(Kind.class);
		for (Option option : scored)
		{
			final Kind family = option.action().effects().getFirst().kind();
			if (represented.add(family)) { result.add(option); }
			if (result.size() == OPTIONS) { return result; }
		}
		for (Option option : scored)
		{
			if (!result.contains(option)) { result.add(option); }
			if (result.size() == OPTIONS) { break; }
		}
		return result;
	}

	private static Step apply(Scene scene, Threat threat, Node parent, State from, Action action)
	{
		final double seconds = action.seconds();
		final double elapsed = from.elapsed() + seconds;
		double incoming = 0;
		double bypassCp = 0;
		double burst = 0;
		for (Source source : scene.sources())
		{
			if (!source.reachable() || !source.incoming() && !source.primary()) { continue; }
			final double rootDelay = source.primary() && source.arrivalSeconds() > from.elapsed() && !source.casting() ? from.rootUntil() : 0;
			final double start = Math.max(Math.max(source.arrivalSeconds(), rootDelay), source.primary() ? from.controlUntil() : 0);
			double damage = source.dps() * (source.incoming() ? 1 : 0.45) * Math.max(0, elapsed - Math.max(from.elapsed(), start));
			if (!from.burstSettled() && source.casting() && seconds >= 0.6 && (!source.primary() || from.controlUntil() <= from.elapsed()))
			{
				burst += source.burst();
				damage += source.burst();
			}
			if (!source.cpEligible()) { bypassCp += damage; }
			else { incoming += damage; }
		}
		bypassCp += (threat.highDps() - threat.incomingDps()) * (scene.truncated() ? 0.15 : 0.05) * seconds;
		if (from.defenseUntil() > from.elapsed())
		{
			incoming *= 1 - from.defense();
			bypassCp *= 1 - from.defense();
		}
		final double cp = Math.max(0, from.cp() - incoming);
		final double lost = Math.max(0, incoming - from.cp()) + bypassCp + action.hp();
		double hp = from.hp() - lost;
		double mp = from.mp() - action.mp();
		int charges = from.charges() - action.charges(), souls = from.souls() - action.souls();
		double damage = from.periodic() * Math.max(0, Math.min(seconds, from.periodicUntil() - from.elapsed()));
		double controlUntil = from.controlUntil(), rootUntil = from.rootUntil(), defenseUntil = from.defenseUntil(), defense = from.defense();
		double debuffUntil = from.debuffUntil(), debuff = from.debuff();
		double periodicUntil = from.periodicUntil(), periodic = from.periodic();
		double relief = 0;
		boolean interrupted = false;
		boolean escaped = false;
		if (hp > 0)
		{
			for (Effect effect : action.effects())
			{
				final double amount = effect.amount() * effect.likelihood();
				switch (effect.kind())
				{
					case PHYSICAL, MAGIC -> damage += amount * (from.debuffUntil() > elapsed ? 1 + from.debuff() : 1);
					case DRAIN -> hp += Math.min(Math.min(from.enemyHp(), damage) * amount, scene.maximumHp() - hp);
					case HEAL -> hp += Math.min(amount, scene.maximumHp() - hp);
					case CONTROL ->
					{
						final double duration = effect.seconds() * effect.likelihood();
						if (controlUntil <= elapsed)
						{
							controlUntil = elapsed + duration;
							relief += Math.min(1, duration) * threat.incomingDps() * 0.45;
							interrupted = !from.burstSettled() && burst == 0;
						}
					}
					case ROOT -> { if (rootUntil <= elapsed) { rootUntil = elapsed + effect.seconds() * effect.likelihood(); } }
					case INTERRUPT -> interrupted = !from.burstSettled() && burst == 0;
					case DEFENSE -> { defenseUntil = elapsed + effect.seconds(); defense = Math.min(0.8, amount); }
					case DEBUFF -> { debuffUntil = elapsed + effect.seconds(); debuff = Math.min(0.5, amount); }
					case PERIODIC ->
					{
						if (periodicUntil <= elapsed)
						{
							periodicUntil = elapsed + Math.min(effect.seconds(), threat.neutralizeSeconds());
							periodic = amount;
						}
					}
					case CHARGE -> charges = Math.min(20, charges + (int) amount);
					case SOUL -> souls = Math.min(40, souls + (int) amount);
					case MOVE -> relief += amount;
					case RETREAT -> { relief += amount; escaped = true; }
					case REST -> { hp = Math.min(scene.maximumHp(), hp + amount); mp = Math.min(scene.maximumMp(), mp + amount); }
					case SILENCE, WAIT, UNKNOWN -> { }
				}
			}
		}
		if (interrupted)
		{
			final double likelihood = action.effects().stream().filter(effect -> effect.kind() == Kind.CONTROL || effect.kind() == Kind.INTERRUPT)
				.mapToDouble(Effect::likelihood).max().orElse(0);
			relief += scene.sources().stream().filter(source -> source.primary() && source.casting() && source.reachable())
				.mapToDouble(Source::burst).sum() * likelihood;
		}
		final double dealt = Math.min(from.enemyHp(), damage);
		final double enemyHp = dealt >= from.enemyHp() ? 0 : Math.min(scene.maximumTargetHp(), from.enemyHp() - dealt + scene.targetRegen() * seconds);
		final boolean won = enemyHp <= 0 && hp > 0;
		final double survivalWeight = (scene.pvp() ? 1.5 : 1) * (from.hp() < scene.maximumHp() * 0.45 ? 2.4 : 1);
		final double progress = (dealt / Math.max(1, scene.maximumTargetHp()) * 1.6) + (won ? 1.3 : 0);
		final double survival = (hp - from.hp() + relief + (cp - from.cp()) * 0.6) / Math.max(1, scene.maximumHp()) * survivalWeight;
		final double price = action.mp() / Math.max(1, scene.maximumMp()) * (from.mp() < scene.maximumMp() * 0.35 ? 1.15 : 0.7)
			+ action.itemPrice() + 0.12 * seconds;
		final double utility = hp <= 0 ? -8 : progress + survival - price;
		final double score = parent.score() + utility * Math.pow(0.72, parent.depth());
		final State next = new State(hp, cp, mp, charges, souls, enemyHp, elapsed, controlUntil, rootUntil, defenseUntil, defense, debuffUntil,
			debuff, periodicUntil, periodic, from.burstSettled() || burst > 0 || interrupted, hp <= 0 || won || escaped);
		return new Step(action, parent, next, score);
	}
}
