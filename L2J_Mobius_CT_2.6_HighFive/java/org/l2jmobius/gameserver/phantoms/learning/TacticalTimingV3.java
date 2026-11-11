/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Additive rich timing facts. Predictions are not native effect receipts or a permission to cast. */
public final class TacticalTimingV3
{
    public static final int EXTENSION_MAGIC = 0x54544D33, EXTENSION_VERSION = 1;
    public static final String FINGERPRINT = TacticalObservationV3.hash(("TacticalTimingV3/1/native-reuseHash-max-stamp-disabled/global-indefinite-unknown/"
        + "sampledNanos,reuseHash,remainingReuse,readyNow,nextAvailable,costsKnown,mpInitial,mpFinal,hp,baseKnown,baseHit,baseCool,kind,"
        + "nativeCastMillis,predictedEffectAfter,predictedBusyAfter,activeKnown,active2(slot,skill,level,targets16,start,effect,busy,nextPhase),interruptKnown,interruptNow,futureInterruptProbability,"
        + "threatHorizon,trajectory8(skill,level,target,start,effect,busy,nextReady,origin,outcome)/independent-state128-action64").getBytes(StandardCharsets.UTF_8));
    public enum Kind { NORMAL_STANDARD, CHANNELING, TOGGLE, SIMULTANEOUS, SPECIAL, UNKNOWN }
    public enum Origin { PREDICTED, NATIVE_OBSERVED, UNKNOWN }
    public enum Outcome { UNKNOWN, ISSUED, COMPLETED, INTERRUPTED, FAILED }

    /** Milliseconds or nanoseconds as explicitly named by the containing field; UNKNOWN is never measured zero. */
    public record Time(long value, boolean known)
    {
        public Time { if (value < 0 || !known && value != 0) { throw new IllegalArgumentException("Invalid temporal value"); } }
        public static Time known(long value) { return new Time(value,true); }
        public static Time unknown() { return new Time(0,false); }
    }
    public record ActiveCast(int slot, int skillId, int skillLevel, List<Integer> targetObjectIds,
        Time startedNanos, Time effectDeadlineNanos, Time busyDeadlineNanos, Time nextPhaseRemainingMillis)
    {
        public ActiveCast
        {
            if (slot < 0 || slot > 1 || skillId <= 0 || skillLevel <= 0 || targetObjectIds == null || targetObjectIds.isEmpty()
                || targetObjectIds.size() > 16 || targetObjectIds.stream().anyMatch(id -> id == null || id <= 0)
                || targetObjectIds.stream().distinct().count() != targetObjectIds.size())
            { throw new IllegalArgumentException("Invalid current native cast identity"); }
            targetObjectIds = List.copyOf(targetObjectIds);
            Objects.requireNonNull(startedNanos); Objects.requireNonNull(effectDeadlineNanos);
            Objects.requireNonNull(busyDeadlineNanos); Objects.requireNonNull(nextPhaseRemainingMillis);
        }
    }
    /** A bounded trajectory contract. No collector constructs a historical target or timestamp from lastSkillCast. */
    public record Step(int skillId, int skillLevel, int targetObjectId, Time startsAfterMillis, Time effectAfterMillis,
        Time busyAfterMillis, Time nextReadyAfterMillis, Origin origin, Outcome outcome)
    {
        public Step
        {
            if (skillId <= 0 || skillLevel <= 0 || targetObjectId <= 0) { throw new IllegalArgumentException("Invalid temporal step"); }
            Objects.requireNonNull(startsAfterMillis); Objects.requireNonNull(effectAfterMillis); Objects.requireNonNull(busyAfterMillis);
            Objects.requireNonNull(nextReadyAfterMillis); Objects.requireNonNull(origin); Objects.requireNonNull(outcome);
            if (origin != Origin.NATIVE_OBSERVED && outcome != Outcome.UNKNOWN) { throw new IllegalArgumentException("Predicted step cannot claim native completion or failure"); }
        }
    }
    public record Timing(long sampledNanos, int reuseHash, Time remainingReuseMillis, boolean readyNow, Time nextAvailableAfterMillis,
        boolean costsKnown, double mpInitial, double mpFinal, double hp, boolean baseKnown, int baseHitMillis, int baseCoolMillis,
        Kind kind, Time nativeCastMillis, Time predictedEffectAfterMillis, Time predictedBusyAfterMillis,
        boolean activeCastsKnown, List<ActiveCast> activeCasts, boolean interruptibleKnown, boolean interruptibleNow,
        TacticalObservationV3.Fact futureInterruptProbability, Time threatHorizonMillis, List<Step> trajectory)
    {
        public Timing
        {
            if (sampledNanos < 0 || !Double.isFinite(mpInitial) || !Double.isFinite(mpFinal) || !Double.isFinite(hp)
                || mpInitial < 0 || mpFinal < 0 || hp < 0 || !costsKnown && (mpInitial != 0 || mpFinal != 0 || hp != 0)
                || baseHitMillis < 0 || baseCoolMillis < 0 || !baseKnown && (baseHitMillis != 0 || baseCoolMillis != 0)
                || activeCasts == null || activeCasts.size() > 2 || trajectory == null || trajectory.size() > 8
                || !activeCastsKnown && !activeCasts.isEmpty())
            { throw new IllegalArgumentException("Timing outside native observation bounds"); }
            Objects.requireNonNull(remainingReuseMillis); Objects.requireNonNull(nextAvailableAfterMillis); Objects.requireNonNull(kind);
            Objects.requireNonNull(nativeCastMillis); Objects.requireNonNull(predictedEffectAfterMillis); Objects.requireNonNull(predictedBusyAfterMillis);
            Objects.requireNonNull(threatHorizonMillis); Objects.requireNonNull(futureInterruptProbability);
            if (!interruptibleKnown && interruptibleNow || futureInterruptProbability.known()
                && (futureInterruptProbability.value() < 0 || futureInterruptProbability.value() > 1)) { throw new IllegalArgumentException("Invalid interrupt fact"); }
            activeCasts = List.copyOf(activeCasts); trajectory = List.copyOf(trajectory);
            if (activeCasts.stream().map(ActiveCast::slot).distinct().count() != activeCasts.size()) { throw new IllegalArgumentException("Duplicate cast slot"); }
            if (readyNow && (!remainingReuseMillis.known() || remainingReuseMillis.value() != 0)) { throw new IllegalArgumentException("Cooldown cannot be ready now"); }
            if (kind != Kind.NORMAL_STANDARD && (nativeCastMillis.known() || predictedEffectAfterMillis.known() || predictedBusyAfterMillis.known()))
            { throw new IllegalArgumentException("Special execution timing must remain UNKNOWN"); }
        }
    }
    public static Timing unknown()
    {
        return new Timing(0,0,Time.unknown(),false,Time.unknown(),false,0,0,0,false,0,0,Kind.UNKNOWN,
            Time.unknown(),Time.unknown(),Time.unknown(),false,List.of(),false,false,TacticalObservationV3.Fact.unknown(),Time.unknown(),List.of());
    }
    /** Same shared reuse hash as native isSkillDisabled; short disables do not require a timestamp entry. */
    public static Time remainingReuse(long timestampRemaining, Long disabledDeadline, long wallNow, boolean globallyDisabled)
    {
        if (globallyDisabled || disabledDeadline != null && disabledDeadline == Long.MAX_VALUE) { return Time.unknown(); }
        final long disabled = disabledDeadline == null || disabledDeadline <= wallNow ? 0 : disabledDeadline - wallNow;
        return Time.known(Math.max(Math.max(0,timestampRemaining),disabled));
    }
    public static Time remainingReuse(Creature actor, Skill skill)
    {
        if (actor == null || skill == null) { return Time.unknown(); }
        final int hash = skill.getReuseHashCode();
        return remainingReuse(actor.getSkillRemainingReuseTime(hash),actor.getDisabledSkills().get(hash),System.currentTimeMillis(),actor.isAllSkillsDisabled());
    }
    public static Kind kind(Skill skill)
    {
        if (skill == null) { return Kind.UNKNOWN; }
        if (skill.isChanneling()) { return Kind.CHANNELING; }
        if (skill.isToggle()) { return Kind.TOGGLE; }
        if (skill.isSimultaneousCast()) { return Kind.SIMULTANEOUS; }
        return skill.isPassive() || skill.getFlyType() != null || skill.hasCastPreconditions() ? Kind.SPECIAL : Kind.NORMAL_STANDARD;
    }
    public static Time nativeCastDuration(Creature actor, Skill skill)
    {
        if (actor == null || kind(skill) != Kind.NORMAL_STANDARD) { return Time.unknown(); }
        final int duration = actor.calculateSkillCastTime(skill);
        return duration < 0 ? Time.unknown() : Time.known(duration);
    }
    /** Actor timing/resource gate only; the owning Party/Combat service still checks target/items/skill legality. */
    public static Timing observe(Creature actor, Skill skill)
    {
        if (actor == null || skill == null) { return unknown(); }
        final long sampled = System.nanoTime();
        final Time reuse = remainingReuse(actor,skill), duration = nativeCastDuration(actor,skill);
        final double initial = Math.max(0,actor.getStat().getMpInitialConsume(skill)), mp = Math.max(0,actor.getStat().getMpConsume(skill)), hp = Math.max(0,skill.getHpConsume());
        final boolean actorAvailable = !actor.isAlikeDead() && !actor.isOutOfControl() && !actor.isCastingNow() && !actor.isCastingSimultaneouslyNow()
            && !actor.isAllSkillsDisabled() && actor.getCurrentMp() >= initial + mp && actor.getCurrentHp() > hp
            && (skill.isStatic() || !(skill.isMagic() ? actor.isMuted() : actor.isPhysicalMuted()));
        final boolean ready = actorAvailable && reuse.known() && reuse.value() == 0;
        final Time next = actorAvailable && reuse.known() ? reuse : Time.unknown();
        // Standard duration >=400 reaches the existing native hit/finalizer boundary at that duration.
        // Short special scheduling is UNKNOWN here; do not reproduce the native scheduling formula.
        final Time effect = duration.known() && (duration.value() == 0 || duration.value() >= 400) ? duration : Time.unknown();
        final var nativeUse = actor.observeNativeMagicUse(16);
        final var active = new ArrayList<ActiveCast>(); boolean activeKnown = nativeUse.isCurrent();
        if (nativeUse.casting()) { activeKnown &= addActive(actor,active,nativeUse.primary(),0); }
        if (nativeUse.simultaneous()) { activeKnown &= addActive(actor,active,nativeUse.secondary(),1); }
        activeKnown &= nativeUse.isCurrent();
        final boolean interruptKnown = activeKnown && !nativeUse.simultaneous();
        final boolean interruptNow = interruptKnown && nativeUse.casting() && actor.canAbortCast();
        final boolean current = nativeUse.isCurrent();
        final boolean casting = nativeUse.casting() || nativeUse.simultaneous();
        return new Timing(sampled,skill.getReuseHashCode(),reuse,ready && !casting,casting ? Time.unknown() : next,true,initial,mp,hp,true,
            Math.max(0,skill.getHitTime()),Math.max(0,skill.getCoolTime()),kind(skill),duration,effect,effect,
            activeKnown && current,activeKnown && current ? active : List.of(),interruptKnown && current,interruptNow && current,
            TacticalObservationV3.Fact.unknown(),Time.unknown(),List.of());
    }
    private static boolean addActive(Creature actor, List<ActiveCast> active, MagicUseTask.TargetObservation cast, int slot)
    {
        if (cast == null || !cast.isCurrent() || cast.status() != MagicUseTask.TargetStatus.READY || cast.skill() == null) { return false; }
        final long nextPhase = actor.getNativeCastNextPhaseRemainingMillis(slot == 1);
        active.add(new ActiveCast(slot,cast.skill().getId(),cast.skill().getLevel(),cast.targets().stream().map(v -> v.getObjectId()).distinct().toList(),
            Time.unknown(),Time.unknown(),Time.unknown(),nextPhase < 0 ? Time.unknown() : Time.known(nextPhase)));
        return cast.isCurrent();
    }
    static void write(Timing value, DataOutput out) throws IOException
    {
        out.writeLong(value.sampledNanos()); out.writeInt(value.reuseHash()); time(value.remainingReuseMillis(),out); out.writeBoolean(value.readyNow()); time(value.nextAvailableAfterMillis(),out);
        out.writeBoolean(value.costsKnown()); out.writeDouble(value.mpInitial()); out.writeDouble(value.mpFinal()); out.writeDouble(value.hp());
        out.writeBoolean(value.baseKnown()); out.writeInt(value.baseHitMillis()); out.writeInt(value.baseCoolMillis()); out.writeInt(value.kind().ordinal());
        time(value.nativeCastMillis(),out); time(value.predictedEffectAfterMillis(),out); time(value.predictedBusyAfterMillis(),out);
        out.writeBoolean(value.activeCastsKnown()); out.writeInt(value.activeCasts().size());
        for (var active : value.activeCasts())
        {
            out.writeInt(active.slot()); out.writeInt(active.skillId()); out.writeInt(active.skillLevel()); out.writeInt(active.targetObjectIds().size());
            for (int target : active.targetObjectIds()) { out.writeInt(target); }
            time(active.startedNanos(),out); time(active.effectDeadlineNanos(),out); time(active.busyDeadlineNanos(),out); time(active.nextPhaseRemainingMillis(),out);
        }
        out.writeBoolean(value.interruptibleKnown()); out.writeBoolean(value.interruptibleNow());
        out.writeBoolean(value.futureInterruptProbability().known()); out.writeDouble(value.futureInterruptProbability().value());
        time(value.threatHorizonMillis(),out); out.writeInt(value.trajectory().size());
        for (var step : value.trajectory())
        {
            out.writeInt(step.skillId()); out.writeInt(step.skillLevel()); out.writeInt(step.targetObjectId());
            time(step.startsAfterMillis(),out); time(step.effectAfterMillis(),out); time(step.busyAfterMillis(),out); time(step.nextReadyAfterMillis(),out);
            out.writeInt(step.origin().ordinal()); out.writeInt(step.outcome().ordinal());
        }
    }
    static Timing read(DataInput in) throws IOException
    {
        final long sampled = in.readLong(); final int hash = in.readInt(); final Time reuse = time(in); final boolean ready = in.readBoolean(); final Time next = time(in);
        final boolean costs = in.readBoolean(); final double initial = in.readDouble(), mp = in.readDouble(), hp = in.readDouble();
        final boolean base = in.readBoolean(); final int hit = in.readInt(), cool = in.readInt(); final Kind kind = Kind.values()[bounded(in.readInt(),Kind.values().length - 1)];
        final Time duration = time(in), effect = time(in), busy = time(in); final boolean activeKnown = in.readBoolean();
        final var active = new ArrayList<ActiveCast>();
        for (int n = bounded(in.readInt(),2); n > 0; n--)
        {
            final int slot = in.readInt(), skill = in.readInt(), level = in.readInt(); final var targets = new ArrayList<Integer>();
            for (int t = bounded(in.readInt(),16); t > 0; t--) { targets.add(in.readInt()); }
            active.add(new ActiveCast(slot,skill,level,targets,time(in),time(in),time(in),time(in)));
        }
        final boolean interruptKnown = in.readBoolean(), interruptNow = in.readBoolean(), probabilityKnown = in.readBoolean();
        final var probability = new TacticalObservationV3.Fact(in.readDouble(),probabilityKnown);
        final Time horizon = time(in); final var trajectory = new ArrayList<Step>();
        for (int n = bounded(in.readInt(),8); n > 0; n--)
        {
            trajectory.add(new Step(in.readInt(),in.readInt(),in.readInt(),time(in),time(in),time(in),time(in),
                Origin.values()[bounded(in.readInt(),Origin.values().length - 1)],Outcome.values()[bounded(in.readInt(),Outcome.values().length - 1)]));
        }
        return new Timing(sampled,hash,reuse,ready,next,costs,initial,mp,hp,base,hit,cool,kind,duration,effect,busy,
            activeKnown,active,interruptKnown,interruptNow,probability,horizon,trajectory);
    }
    private static void time(Time value, DataOutput out) throws IOException { out.writeBoolean(value.known()); out.writeLong(value.value()); }
    private static Time time(DataInput in) throws IOException { final boolean known = in.readBoolean(); return new Time(in.readLong(),known); }
    private static int bounded(int value, int max) { if (value < 0 || value > max) { throw new IllegalArgumentException("Temporal bound"); } return value; }
    private TacticalTimingV3() { }
}
