/** Tests for a proposal, not Mobius integration tests. */
public final class RewardKernel029Test {
    private static int assertions;
    private static void eq(long want, long actual) { assertions++; if(want!=actual) throw new AssertionError(want+" != "+actual); }
    private static void close(double want, double actual) { assertions++; if(Math.abs(want-actual)>0.0001) throw new AssertionError(want+" != "+actual); }
    private static void rejected(Runnable work) { assertions++; try { work.run(); } catch(IllegalArgumentException expected) {return;} throw new AssertionError("accepted invalid input"); }
    public static void main(String[] args) {
        float[] points={1,240,241,2000,2001,13000,13001,16361,17000,17001,20000,421};
        int[] levels={0,0,1,1,2,2,3,3,3,4,4,1};
        for(int i=0;i<points.length;i++) eq(levels[i],RewardKernel029.level(points[i]));
        var ordinary=new RewardKernel029.Policy(true,false,1,false,1,1,1.5,2,2.5,3,0,0,0,0);
        var crossing=RewardKernel029.award(100,10,241,-2,ordinary,true);
        eq(150,crossing.exp()); eq(15,crossing.sp()); close(239,crossing.points());
        var next=RewardKernel029.award(100,10,crossing.points(),-2,ordinary,true);
        eq(100,next.exp()); eq(10,next.sp());
        var capped=new RewardKernel029.Policy(true,false,1,false,1,1,1.5,2,2.5,3,25,50,2,2);
        var cap=RewardKernel029.award(100,10,17001,-1,capped,true);
        eq(200,cap.exp()); eq(20,cap.sp()); // Add then clamp; not multiplying bonuses together.
        var noVitality=new RewardKernel029.Policy(false,false,1,false,1,1,1.5,2,2.5,3,0,0,0,0);
        var nv=RewardKernel029.award(100,10,16361,-100,noVitality,true);
        eq(100,nv.exp()); close(16361,nv.points());
        var noBonus=RewardKernel029.award(100,10,16361,-100,ordinary,false);
        eq(100,noBonus.exp()); close(16361,noBonus.points());
        var lucky=new RewardKernel029.Policy(true,true,1,false,1,1,1.5,2,2.5,3,0,0,0,0);
        close(421,RewardKernel029.award(100,10,421,-100,lucky,true).points());
        var consume0=new RewardKernel029.Policy(true,false,0,false,1,1,1.5,2,2.5,3,0,0,0,0);
        close(421,RewardKernel029.award(100,10,421,-100,consume0,true).points());
        var gain=new RewardKernel029.Policy(true,false,-1,false,2,1,1.5,2,2.5,3,0,0,0,0);
        close(441,RewardKernel029.award(100,10,421,-10,gain,true).points());
        close(1,RewardKernel029.award(100,10,1,-300,ordinary,true).points());
        close(20000,RewardKernel029.award(100,10,20000,-10,gain,true).points());
        close(16361,RewardKernel029.award(0,0,16361,-100,ordinary,true).points());
        close(239.5,RewardKernel029.award(100,10,241,-1.5f,ordinary,true).points());
        eq(239,RewardKernel029.award(100,10,241,-1.5f,ordinary,true).canonicalPoints());
        close(-100,RewardKernel029.targetDelta(100,1,9,100,100));
        close(0,RewardKernel029.targetDelta(0,1,900,100,100));
        close(0,RewardKernel029.targetDelta(100,1,0,100,100));
        rejected(()->RewardKernel029.level(0)); rejected(()->RewardKernel029.level(Float.NaN));
        rejected(()->RewardKernel029.award(-1,10,421,-100,ordinary,true));
        rejected(()->RewardKernel029.award(1,10,421,Float.NaN,ordinary,true));
        System.out.println("RewardKernel029Test PASS; assertions="+assertions);
    }
}
