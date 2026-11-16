/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.*;

/** A bounded tie-break inside stock Party support, with rescue and dispatch authority retained by its owner. */
public final class TacticalPolicyV3
{
	public enum Mode { OFF, SHADOW, ON }
	public static final TacticalPolicyV3 OFF = new TacticalPolicyV3(Mode.OFF, 0, null, 20, 16, 2000, _ -> { });
	private static final Semaphore CPU_SLOT = new Semaphore(1);
	private final Mode _mode;
	private final long _version, _cooldownNanos;
	private final TacticalModelV3 _model;
	private final int _maximumEntities, _maximumActions;
	private final Consumer<HealEvidenceV3.Receipt> _sink;
	private volatile Consumer<TacticalReceiptV3> _damageSink = _ -> { };
	private volatile Consumer<TacticalFailureV3> _failureSink = _ -> { };
	private volatile TrainExplorationV1 _exploration;
	private volatile ActionExplorationV4 _experiment;
	/** Mutually exclusive, pre-CUT TEST-only owner; ordinary configuration has no entry point. */
	public void actionExplorationTest(ActionExplorationV4 session)
	{
		if (!Boolean.getBoolean("phantom.learning.train") || this == OFF || session == null || _exploration != null || !_last.isEmpty()
			|| _actionValueMode != Mode.ON || _actionValue == null || !session.plan().modelSha().equals(_actionValue.sha256()))
		{ throw new IllegalArgumentException("V4 experiment requires exclusive frozen TEST owner/model"); }
		_experiment = session;
	}
	private volatile Mode _actionValueMode = Mode.OFF;
	private volatile ActionValueV4 _actionValue;
	private volatile Consumer<ActionValueDecision> _actionValueSink = _ -> { };
	public record ActionValueDecision(LegalActionV3 selected, List<Double> scores, boolean consulted, String reason, String modelSha, long nanos)
	{ public ActionValueDecision { scores = List.copyOf(scores); } }
	/** Explicit TEST-only pre-CUT binding. Ordinary configuration never installs V4. */
	public void actionValueTest(Mode mode, ActionValueV4 model, Consumer<ActionValueDecision> sink)
	{
		if (!Boolean.getBoolean("phantom.learning.train") || this == OFF || mode == null || sink == null || !_last.isEmpty())
		{ throw new IllegalArgumentException("V4 requires separate explicit pre-combat TEST policy"); }
		_actionValue = model; _actionValueSink = sink; _actionValueMode = mode;
	}
	/** Pure rank over already validated proposals; execution authority remains with the caller. */
	public static ActionValueDecision actionValueChoice(Mode mode, ActionValueV4 model, TacticalObservationV3 observation,
		List<LegalActionV3> offered, LegalActionV3 stock, boolean critical, long deadline)
	{
		final long started = System.nanoTime();
		final var scores = new ArrayList<Double>(); String reason = "OFF"; boolean consulted = false;
		LegalActionV3 selected = stock;
		try
		{
			if (mode == null || mode == Mode.OFF) { reason = "OFF"; }
			else if (critical) { reason = "CRITICAL_STOCK"; }
			else if (model == null || model.updates() == 0) { reason = "MODEL_UNREADY"; }
			else if (System.nanoTime() >= deadline) { reason = "BUDGET_5MS"; }
			else if (observation == null || offered == null || offered.size() < 2 || offered.size() > 16 || !offered.contains(stock)) { reason = "MISSING_OBSERVATION_OR_OPTIONS"; }
			else
			{
				new TacticalReceiptV3.Attempt(observation, offered, stock, stock, 0);
				if (offered.stream().anyMatch(a -> a.family() != LegalActionV3.Family.DAMAGE)) { reason = "FAMILY_UNTRAINED"; }
				else
				{
					double best = -Double.MAX_VALUE, second = best; LegalActionV3 winner = stock;
					for (var action : offered)
					{
						final double score = model.score(observation, action); scores.add(score);
						if (score > best) { second = best; best = score; winner = action; } else { second = Math.max(second, score); }
					}
					consulted = true;
					if (System.nanoTime() >= deadline) { reason = "BUDGET_5MS"; }
					else if (mode != Mode.ON) { reason = "SHADOW"; }
					else if (best - second < .025) { reason = "MARGIN"; }
					else { selected = winner; reason = selected.equals(stock) ? "V4_STOCK" : "V4_REORDERED"; }
				}
			}
		}
		catch (RuntimeException invalid) { selected = stock; reason = "INVALID_STOCK"; }
		return new ActionValueDecision(selected, scores, consulted, reason, model == null ? "" : model.sha256(), System.nanoTime() - started);
	}
	private final ConcurrentHashMap<Long, Long> _last = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, HealEvidenceV3.Attempt> _pending = new ConcurrentHashMap<>();
	private final AtomicLong _generation = new AtomicLong();
	private final ConcurrentHashMap<String,LongAdder> _reasons=new ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentLinkedQueue<String> _reasonSamples=new java.util.concurrent.ConcurrentLinkedQueue<>();
	private final AtomicLong _reasonSampleCount=new AtomicLong();
	private volatile Float _lastMargin;
	private void reason(String reason,long actor,int target)
	{
		_reasons.computeIfAbsent(reason,_ -> new LongAdder()).increment();
		if(_reasonSampleCount.getAndIncrement()<64){_reasonSamples.add("{\"reason\":"+TrainingStore.quote(reason)+",\"actorProfile\":"+actor+",\"target\":"+target+"}");}
	}
	public String diagnostics()
	{
		final var sorted=new TreeMap<String,Long>(_reasons.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().sum())));
		return "{\"counters\":{"+sorted.entrySet().stream().map(e->TrainingStore.quote(e.getKey())+":"+e.getValue()).collect(java.util.stream.Collectors.joining(","))+"},\"samples\":["+String.join(",",_reasonSamples)+"],\"sampleLimit\":64,\"lastMargin\":"+_lastMargin+"}";
	}
	public final LongAdder evaluated = new LongAdder(), reordered = new LongAdder(), overBudget = new LongAdder(), fallback = new LongAdder();
	private volatile HealEvidenceV3.Attempt _latest;
	private final Timing _snapshotTime = new Timing(), _encodeTime = new Timing(), _scoreTime = new Timing(), _totalTime = new Timing(), _dispatchTime = new Timing(), _cpuTime = new Timing(), _allocations = new Timing();
	private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
	public TacticalPolicyV3(Mode mode, long version, TacticalModelV3 model, int maximumEntities, int maximumActions, int cooldownMillis, Consumer<HealEvidenceV3.Receipt> sink)
	{
		if (mode == null || maximumEntities < 3 || maximumEntities > 20 || maximumActions < 2 || maximumActions > 16 || cooldownMillis < 1000 || cooldownMillis > 60000
			|| sink == null || model != null && version <= 0 || mode == Mode.ON && model == null) { throw new IllegalArgumentException("Invalid bounded V3 policy"); }
		_mode = mode; _version = version; _model = model; _maximumEntities = maximumEntities; _maximumActions = maximumActions; _cooldownNanos = cooldownMillis * 1_000_000L; _sink = sink;
	}
	public HealEvidenceV3.Attempt latest() { return _latest; }
	public Mode mode() { return _mode; }
	/** Explicit pre-CUT owned TRAIN binding. Ordinary ON inference cannot receive an exploration window. */
	public void trainExploration(TrainExplorationV1 session)
	{
		if (session != null && _experiment != null) { throw new IllegalArgumentException("Only one experiment owner per decision"); }
		if(session!=null&&(_mode!=Mode.SHADOW||_model==null||session.modelVersion()!=_version||!session.modelSha().equals(_model.sha256())))
		{throw new IllegalArgumentException("Exploration requires exact SHADOW TEST model authority");}
		_exploration=session;
	}
	public void damageObserver(Consumer<TacticalReceiptV3> sink) { _damageSink = Objects.requireNonNull(sink); }
	void recordDamage(TacticalReceiptV3 receipt) { _damageSink.accept(receipt); }
	public void failureObserver(Consumer<TacticalFailureV3> sink) { _failureSink = Objects.requireNonNull(sink); }
	void recordFailure(TacticalFailureV3 failure) { _failureSink.accept(failure); }
	public record DamageChoice(LearningPolicy.Candidate selected, TacticalReceiptV3.Attempt attempt) { }

	/** Reuses the existing Combat candidate filter/exact lease. No new executor or owner priority. */
	DamageChoice damage(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest request, int actorObjectId,
		List<LearningPolicy.Candidate> candidates, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill baseline)
	{
		if (_mode == Mode.OFF || candidates.size() < 2 || candidates.size() > _maximumActions || SkillData.getInstance().catalogReloaded()) { reason("DAMAGE_OFFER_OR_MODE",request.profileId(),request.targetObjectId());return null; }
		final long now = System.nanoTime(), profile = request.profileId();
		if (_last.containsKey(profile) && now - _last.get(profile) < _cooldownNanos || _last.size() >= 256 && !_last.containsKey(profile)) { reason("COOLDOWN_OR_ACTOR_CAP",profile,request.targetObjectId());return null; }
		if (!CPU_SLOT.tryAcquire()) { reason("CPU_SLOT",profile,request.targetObjectId());fallback.increment(); return null; }
		final long allocated = allocated(), cpu = THREADS.getCurrentThreadCpuTime();
		try
		{
			_last.put(profile, now);
			if (!(World.getInstance().findObject(actorObjectId) instanceof Player actor)
				|| !(World.getInstance().findObject(request.targetObjectId()) instanceof org.l2jmobius.gameserver.model.actor.instance.Monster target)
				|| actor.isAlikeDead() || target.isAlikeDead() || actor.isCastingNow() || actor.isAttackingNow()) { return null; }
			final long generation = _generation.incrementAndGet(), snapshotStarted = System.nanoTime();
			final var observation = TacticalSnapshotV3.collect(actor, profile, generation, _maximumEntities);
			_snapshotTime.add(System.nanoTime() - snapshotStarted);
			if (observation.entity(target.getObjectId()) == null) { return null; }
			final var offered = new ArrayList<LegalActionV3>();
			int stock = -1;
			for (int index = 0; index < candidates.size(); index++)
			{
				final var candidate = candidates.get(index);
				final var skill = actor.getKnownSkill(candidate.skill().skillId());
				if (!candidate.exact().getAsBoolean() || skill == null || skill.getLevel() != candidate.skill().skillLevel()
					|| actor.isSkillDisabled(skill) || !actor.checkDoCastConditions(skill) || !skill.checkCondition(actor, target, false)
					|| !skill.isDamage() || skill.getAffectRange() > 0 || actor.getCurrentMp() < candidate.mpCost()) { return null; }
				offered.add(new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", profile, actor.getObjectId(), observation.actorEpoch(), generation,
					target.getObjectId(), TacticalSnapshotV3.epoch(target), observation.partyIdentity(), skill.getId(), skill.getLevel(), candidate.mpCost(),
					skill.getHitTime(), Math.max(0, actor.getMagicalAttackRange(skill)), false, true));
				if (candidate.skill().equals(baseline)) { stock = index; }
			}
			if (stock < 0) { return null; }
			int selected = stock;
			if (_model != null)
			{
				final long encodeStarted = System.nanoTime();
				final var encoded = _model.encode(observation.state());
				_encodeTime.add(System.nanoTime() - encodeStarted);
				final long scoreStarted = System.nanoTime();
				float best = -Float.MAX_VALUE, second = best;
				for (int i = 0; i < offered.size(); i++)
				{
					final float score = _model.scoreEncoded(encoded, offered.get(i).features(observation));
					if (score > best) { second = best; best = score; selected = i; } else { second = Math.max(second, score); }
				}
				_scoreTime.add(System.nanoTime() - scoreStarted); evaluated.increment();
				_lastMargin=best-second;
				if (_mode != Mode.ON || best - second < .025f) { reason(_mode!=Mode.ON?"SHADOW":"MARGIN",profile,target.getObjectId());selected = stock; }
			}
			if (System.nanoTime() - now > 5_000_000L) { reason("BUDGET_5MS",profile,target.getObjectId());overBudget.increment(); selected = stock; }
			else if(_exploration!=null&&!_exploration.spent())
			{
				var session=_exploration;
				var decision=session.offer(observation,offered,offered.get(stock),actor.getNativeWorkOwner()!=null&&actor.getNativeWorkOwner().isCurrent()
					&&candidates.stream().allMatch(c->c.exact().getAsBoolean()),actor.getCurrentHp()>actor.getMaxHp()*.30);
				if(decision!=null)
				{
					if(System.nanoTime()-now>5_000_000L){decision=decision.failed("BUDGET_5MS");overBudget.increment();selected=stock;}
					else{selected=offered.indexOf(decision.selected());}
					session.record(decision);reason(decision.fallback().isEmpty()?decision.reason():decision.fallback(),profile,target.getObjectId());
				}
			}
			if (_experiment != null)
			{
				final boolean safe = actor.getNativeWorkOwner() != null && actor.getNativeWorkOwner().isCurrent()
					&& actor.getCurrentHp() > actor.getMaxHp() * .30 && candidates.stream().allMatch(c -> c.exact().getAsBoolean());
				final var model = actionValueChoice(_actionValueMode, _actionValue, observation, offered, offered.get(stock), !safe, now + 5_000_000L);
				final var decision = _experiment.choose(observation, offered, offered.get(stock), model.selected(), safe, now + 5_000_000L);
				selected = offered.indexOf(decision.selected());
				_actionValueSink.accept(new ActionValueDecision(decision.selected(), model.scores(), model.consulted(), decision.mode(), model.modelSha(), model.nanos()));
				reason(decision.mode(), profile, target.getObjectId());
			}
			else if (_actionValueMode != Mode.OFF)
			{
				final var decision = actionValueChoice(_actionValueMode, _actionValue, observation, offered, offered.get(stock),
					_exploration != null || actor.getCurrentHp() <= actor.getMaxHp() * .30, now + 5_000_000L);
				selected = offered.indexOf(decision.selected());
				_actionValueSink.accept(decision); reason("V4_" + decision.reason(), profile, target.getObjectId());
			}
			reason(selected==stock?"DAMAGE_STOCK":"DAMAGE_REORDER",profile,target.getObjectId());
			if (selected != stock) { reordered.increment(); }
			return new DamageChoice(candidates.get(selected), new TacticalReceiptV3.Attempt(observation, offered, offered.get(selected), offered.get(stock), _version));
		}
		catch (RuntimeException unavailable) { fallback.increment(); return null; }
		finally
		{
			_totalTime.add(System.nanoTime() - now);
			if (cpu >= 0) { _cpuTime.add(THREADS.getCurrentThreadCpuTime() - cpu); }
			if (allocated >= 0) { _allocations.add(allocated() - allocated); }
			CPU_SLOT.release();
		}
	}
	public List<TacticalDirective> rank(List<TacticalDirective> stock, Map<MemberRef, MemberSnapshot> snapshots)
	{
		if (_mode == Mode.OFF || SkillData.getInstance().catalogReloaded()) { return stock; }
		final var result = new ArrayList<>(stock);
		final var actors = new LinkedHashSet<MemberRef>();
		for (var directive : stock) { if (directive.kind() == DirectiveKind.HEAL_MEMBER) { actors.add(directive.actor()); } }
		for (MemberRef ref : actors)
		{
			final var choices = stock.stream().filter(v -> v.actor().equals(ref) && v.kind() == DirectiveKind.HEAL_MEMBER).toList();
			if (choices.size() < 2 || choices.size() > _maximumActions) {reason("HEAL_OFFER_COUNT",ref.profileId(),0);continue;}
			if (!safeToRank(choices, snapshots)) {reason(safetyReason(choices,snapshots),ref.profileId(),choices.getFirst().targetObjectId());continue;}
			final long now = System.nanoTime(), previous = _last.getOrDefault(ref.profileId(), Long.MIN_VALUE);
			if (_last.containsKey(ref.profileId()) && now - previous < _cooldownNanos || _last.size() >= 256 && !_last.containsKey(ref.profileId())) {reason("COOLDOWN_OR_ACTOR_CAP",ref.profileId(),0);continue;}
			if (!CPU_SLOT.tryAcquire()) { reason("CPU_SLOT",ref.profileId(),0);fallback.increment(); continue; }
			final long allocated = allocated(), cpu = THREADS.getCurrentThreadCpuTime();
			try
			{
				_last.put(ref.profileId(), now); _pending.remove(ref.profileId());
				if (!(World.getInstance().findObject(ref.characterObjectId()) instanceof Player actor) || actor.getParty() == null || actor.isCastingNow() || actor.isAttackingNow()) { continue; }
				final long generation = _generation.incrementAndGet();
				final long snapshotStarted = System.nanoTime();
				final var observation = TacticalSnapshotV3.collect(actor, ref.profileId(), generation, _maximumEntities);
				_snapshotTime.add(System.nanoTime() - snapshotStarted);
				final var offered = new ArrayList<LegalActionV3>();
				for (var directive : choices)
				{
					final var action = legal(actor, directive, observation, generation);
					if (action == null) { offered.clear(); break; }
					offered.add(action);
				}
				if (offered.size() != choices.size()) { fallback.increment(); continue; }
				int selected = 0; float best = -Float.MAX_VALUE, second = best;
				if (_model != null)
				{
					final long encodeStarted = System.nanoTime();
					final var encoded = _model.encode(observation.state());
					_encodeTime.add(System.nanoTime() - encodeStarted);
					final long scoreStarted = System.nanoTime();
					for (int index = 0; index < offered.size(); index++)
					{
						final float score = _model.scoreEncoded(encoded, offered.get(index).features(observation));
						if (score > best) { second = best; best = score; selected = index; } else { second = Math.max(second, score); }
					}
					_scoreTime.add(System.nanoTime() - scoreStarted); evaluated.increment();
					_lastMargin=best-second;
					if (best - second < .025f || _mode != Mode.ON) { reason(_mode!=Mode.ON?"SHADOW":"MARGIN",ref.profileId(),0);selected = 0; }
				}
				if (System.nanoTime() - now > 5_000_000L) { reason("BUDGET_5MS",ref.profileId(),0);overBudget.increment(); selected = 0; }
				else if(_exploration!=null&&!_exploration.spent())
				{
					var session=_exploration;
					var decision=session.offer(observation,offered,offered.getFirst(),actor.getNativeWorkOwner()!=null&&actor.getNativeWorkOwner().isCurrent()
						&&offered.stream().allMatch(a->World.getInstance().findObject(a.targetObjectId()) instanceof Player p&&p.getNativeWorkOwner()!=null&&p.getNativeWorkOwner().isCurrent()&&TacticalSnapshotV3.epoch(p)==a.targetEpoch()),safeToRank(choices,snapshots)
						&&actor.getCurrentHp()>actor.getMaxHp()*.30&&actor.getAttackByList().isEmpty()
						&&offered.stream().allMatch(a->World.getInstance().findObject(a.targetObjectId()) instanceof Player p&&p.getCurrentHp()>p.getMaxHp()*.25&&p.getAttackByList().isEmpty()));
					if(decision!=null)
					{
						if(System.nanoTime()-now>5_000_000L){decision=decision.failed("BUDGET_5MS");overBudget.increment();selected=0;}
						else{selected=offered.indexOf(decision.selected());}
						session.record(decision);reason(decision.fallback().isEmpty()?decision.reason():decision.fallback(),ref.profileId(),decision.selected().targetObjectId());
					}
				}
				reason(selected==0?"HEAL_STOCK":"HEAL_REORDER",ref.profileId(),offered.get(selected).targetObjectId());
				if (selected > 0)
				{
					final int first = result.indexOf(choices.getFirst()), chosen = result.indexOf(choices.get(selected));
					Collections.swap(result, first, chosen); reordered.increment();
				}
				final var attempt = new HealEvidenceV3.Attempt(observation, offered, offered.get(selected), offered.getFirst(), _version, _sink, this::recordFailure);
				_pending.put(ref.profileId(), attempt); _latest = attempt;
			}
			catch (RuntimeException unavailable) { fallback.increment(); }
			finally
			{
				_totalTime.add(System.nanoTime() - now);
				if (cpu >= 0) { _cpuTime.add(THREADS.getCurrentThreadCpuTime() - cpu); }
				if (allocated >= 0) { _allocations.add(allocated() - allocated); }
				CPU_SLOT.release();
			}
		}
		return List.copyOf(result);
	}
	public static boolean safeToRank(List<TacticalDirective> choices, Map<MemberRef, MemberSnapshot> snapshots)
	{
		if (choices.size() < 2) { return false; }
		final MemberRef actor = choices.getFirst().actor();
		for (var directive : choices)
		{
			final var target = snapshots.get(directive.targetMember()); final var source = snapshots.get(actor);
			if (!directive.actor().equals(actor) || directive.kind() != DirectiveKind.HEAL_MEMBER || target == null || source == null || target.dead() || source.dead()
				|| target.hpPercent() <= 25 || source.hpPercent() <= 30 || !target.attackerObjectIds().isEmpty() || !source.attackerObjectIds().isEmpty() || directive.priority() >= 9000) { return false; }
		}
		return true;
	}
	static String safetyReason(List<TacticalDirective> choices,Map<MemberRef,MemberSnapshot> snapshots)
	{
		if(choices.size()<2){return "HEAL_OFFER_COUNT";}
		for(var choice:choices)
		{
			final var source=snapshots.get(choice.actor());final var target=snapshots.get(choice.targetMember());
			if(source==null||target==null){return "STALE_SNAPSHOT";}
			if(!source.attackerObjectIds().isEmpty()){return "ACTOR_UNDER_ATTACK";}
			if(!target.attackerObjectIds().isEmpty()){return "TARGET_UNDER_ATTACK";}
			if(target.hpPercent()<=25||source.hpPercent()<=30||choice.priority()>=9000){return "EMERGENCY";}
		}
		return "UNSAFE_PREDICATE";
	}
	private LegalActionV3 legal(Player actor, TacticalDirective directive, TacticalObservationV3 observation, long generation)
	{
		if (!(World.getInstance().findObject(directive.targetObjectId()) instanceof Player target) || target.getParty() != actor.getParty() || target.isAlikeDead() || target.getNativeWorkOwner() == null || !target.getNativeWorkOwner().isCurrent()) { reason("STALE_OWNER_OR_TARGET",directive.actor().profileId(),directive.targetObjectId());return null; }
		final var skill = actor.getKnownSkill(directive.actionSkillId()); final var entity = observation.entity(target.getObjectId());
		if (skill == null || entity == null || skill.getLevel() != directive.actionSkillLevel() || !PhantomSupportEffectAuthority.supportsCapability(skill, "combat.heal") || skill.getAffectRange() > 0
			|| skill.isChanneling() || skill.isSimultaneousCast() || skill.getFlyType() != null || actor.isSkillDisabled(skill) || !actor.checkDoCastConditions(skill) || !skill.checkCondition(actor, target, false)
			|| actor.calculateDistance3D(target) > actor.getMagicalAttackRange(skill) || !entity.fact(Field.LOS).known() || entity.fact(Field.LOS).value() != 1) { reason(skill==null?"MISSING_SKILL":entity==null?"STALE_SNAPSHOT":!entity.fact(Field.LOS).known()||entity.fact(Field.LOS).value()!=1?"MISSING_LOS":"SKILL_NOT_READY_OR_LEGAL",directive.actor().profileId(),directive.targetObjectId());return null; }
		final double cost = actor.getStat().getMpConsume(skill) + actor.getStat().getMpInitialConsume(skill);
		if (cost <= 0 || actor.getCurrentMp() < cost) { return null; }
		return new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", directive.actor().profileId(), actor.getObjectId(), TacticalSnapshotV3.epoch(actor), generation,
			target.getObjectId(), TacticalSnapshotV3.epoch(target), observation.partyIdentity(), skill.getId(), skill.getLevel(), cost, skill.getHitTime(), Math.max(0, actor.getMagicalAttackRange(skill)), false, true);
	}
	public <T> T dispatch(TacticalDirective directive, java.util.function.Supplier<T> nativeAction)
	{
		final var attempt = _pending.remove(directive.actor().profileId());
		if (attempt == null || attempt.selected().targetObjectId() != directive.targetObjectId() || attempt.selected().skillId() != directive.actionSkillId()) { return nativeAction.get(); }
		final long start = System.nanoTime();
		try { return HealEvidenceV3.withAttempt(attempt, nativeAction); }
		finally { _dispatchTime.add(System.nanoTime() - start); }
	}
	private static long allocated()
	{
		return THREADS instanceof com.sun.management.ThreadMXBean extended && extended.isThreadAllocatedMemorySupported() && extended.isThreadAllocatedMemoryEnabled()
			? extended.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
	}
	public String timing()
	{
		return "snapshotNanos=" + _snapshotTime + " encodeNanos=" + _encodeTime + " scoreNanos=" + _scoreTime + " totalNanos=" + _totalTime
			+ " dispatchNanos=" + _dispatchTime + " cpuNanos=" + _cpuTime + " allocatedBytes=" + _allocations;
	}
	private static final class Timing
	{
		private final long[] _values = new long[512];
		private final AtomicLong _count = new AtomicLong();
		void add(long value) { if (value >= 0) { final long index = _count.getAndIncrement(); _values[(int) (index % _values.length)] = value; } }
		@Override public String toString()
		{
			final int count = (int) Math.min(_count.get(), _values.length); if (count == 0) { return "{n:0}"; }
			final long[] values = Arrays.copyOf(_values, count); Arrays.sort(values);
			return "{n:" + _count.get() + ",p50:" + values[(count - 1) / 2] + ",p95:" + values[(int) Math.ceil(count * .95) - 1] + ",p99:" + values[(int) Math.ceil(count * .99) - 1] + ",max:" + values[count - 1] + "}";
		}
	}
}
