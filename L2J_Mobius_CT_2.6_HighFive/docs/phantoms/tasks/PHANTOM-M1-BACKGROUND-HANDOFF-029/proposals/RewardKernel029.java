/**
 * Standalone arithmetic proposal based on pinned Mobius PlayerStat/Attackable.
 * This is NOT an adapter, eligibility proof or tested server implementation.
 * All rates below are supplied inputs; no live config is silently assumed.
 */
public final class RewardKernel029 {
    private RewardKernel029() { }
    public record Policy(boolean vitalityEnabled, boolean lucky, int consumeStat,
        boolean adventBlessing, double gainRate, double lostRate,
        double level1Rate, double level2Rate, double level3Rate, double level4Rate,
        double bonusExpPercent, double bonusSpPercent, double maxExpBonus, double maxSpBonus) {
        public Policy {
            double[] finite={gainRate,lostRate,level1Rate,level2Rate,level3Rate,level4Rate,
                bonusExpPercent,bonusSpPercent,maxExpBonus,maxSpBonus};
            for(double v:finite) if(!Double.isFinite(v)) throw new IllegalArgumentException("non-finite policy");
            if(gainRate<0 || lostRate<0 || level1Rate<0 || level2Rate<0 || level3Rate<0 || level4Rate<0)
                throw new IllegalArgumentException("negative rate");
        }
    }
    public record Result(long exp,long sp,float points) {
        public int canonicalPoints() { return (int)points; }
    }
    public static int level(float points) {
        valid(points);
        if(points<=240)return 0;
        if(points<=2000)return 1;
        if(points<=13000)return 2;
        if(points<=17000)return 3;
        return 4;
    }
    private static void valid(float points) {
        if(!Float.isFinite(points)||points<1||points>20000)throw new IllegalArgumentException("points");
    }
    public static double multiplier(float points, Policy p, boolean experience) {
        int l=p.adventBlessing()?4:level(points);
        double vitality=!p.vitalityEnabled()?1:switch(l) {
            case 1->p.level1Rate(); case 2->p.level2Rate(); case 3->p.level3Rate();
            case 4->p.level4Rate(); default->1;
        };
        double result=1;
        if(vitality>1)result+=vitality-1;
        double bonus=1+(experience?p.bonusExpPercent():p.bonusSpPercent())/100;
        if(bonus>1)result+=bonus-1;
        result=Math.max(result,1);
        double cap=experience?p.maxExpBonus():p.maxSpBonus();
        return cap>0?Math.min(result,cap):result;
    }
    /** base inputs already have native EXPSP/base SP truncation; no second rate multiplication. */
    public static Result award(long addExp,int addSp,float points,float nativeDelta,
                               Policy p,boolean useVitalityRate) {
        valid(points);
        if(p==null||addExp<0||addSp<0||!Float.isFinite(nativeDelta))throw new IllegalArgumentException("award input");
        double exp=addExp*(useVitalityRate?multiplier(points,p,true):1);
        double sp=addSp*(useVitalityRate?multiplier(points,p,false):1);
        if(!Double.isFinite(exp)||!Double.isFinite(sp)||exp>=Long.MAX_VALUE||sp>=Long.MAX_VALUE)
            throw new IllegalArgumentException("reward overflow");
        float after=points;
        if(addExp>0&&useVitalityRate)after=update(points,nativeDelta,p);
        return new Result(Math.round(exp),Math.round(sp),after);
    }
    public static float update(float points,float delta,Policy p) {
        valid(points);
        if(!Float.isFinite(delta))throw new IllegalArgumentException("delta");
        if(delta==0||!p.vitalityEnabled()||p.lucky())return points;
        float change=delta;
        if(change<0) {
            int stat=p.adventBlessing()?-10:p.consumeStat();
            if(stat==0)return points;
            if(stat<0)change=-change;
        }
        if(change>0)change*=p.gainRate(); else change*=p.lostRate();
        float result=change>0?Math.min(points+change,20000):Math.max(points+change,1);
        return Math.abs(result-points)<=1e-6?points:result;
    }
    /** Target terms must come from native template/exp method, not guessed constants. */
    public static float targetDelta(long damage,int npcLevel,long expReward,float baseHp,float maxHp) {
        if(damage<=0)return 0;
        if(!Float.isFinite(baseHp)||!Float.isFinite(maxHp)||baseHp<0||maxHp<0)
            throw new IllegalArgumentException("hp");
        float divider=npcLevel>0&&expReward>0?(baseHp*9*npcLevel*npcLevel)/(100*expReward):0;
        if(divider==0)return 0;
        return -Math.min(damage,maxHp)/divider;
    }
}
