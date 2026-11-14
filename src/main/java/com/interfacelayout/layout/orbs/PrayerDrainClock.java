package com.interfacelayout.layout.orbs;

import java.util.BitSet;

final class PrayerDrainClock {
    private BitSet phases=new BitSet(), next=new BitSet();
    private int drain,resistance,previousPoints=-1,cycleTicks,cycleLength=1;

    void reset() {
        phases.clear();next.clear();drain=0;resistance=0;previousPoints=-1;cycleTicks=0;cycleLength=1;
    }

    void tick(int rate,int threshold,int points) {
        if(rate<=0 || points<=0) {reset();previousPoints=points;return;}
        threshold=Math.max(1,threshold);
        if(drain!=rate || resistance!=threshold) {
            phases.clear();phases.set(0,threshold);
            cycleTicks=0;cycleLength=divideUp(threshold,rate);
        } else {
            int lost=previousPoints-points;
            boolean observed=lost>=0 && lost<=divideUp(threshold,rate);
            next.clear();
            for(int phase=phases.nextSetBit(0);phase>=0;phase=phases.nextSetBit(phase+1))
                advance(phase,rate,threshold,lost,observed);
            if(next.isEmpty()) {
                for(int phase=0;phase<threshold;phase++)advance(phase,rate,threshold,lost,observed);
                if(next.isEmpty())next.set(0,threshold);
            }
            BitSet old=phases;phases=next;next=old;
            if(lost>0) {
                cycleTicks=0;
                cycleLength=divideUp(threshold-(phases.length()-1),rate);
            } else cycleTicks++;
        }
        drain=rate;resistance=threshold;previousPoints=points;
    }

    private void advance(int phase,int rate,int threshold,int lost,boolean observed) {
        int charged=phase+rate;
        if(!observed || charged/threshold==lost)next.set(charged%threshold);
    }

    double progress(double fraction) {
        if(drain==0)return 0;
        return 1-Math.min(1,(cycleTicks+Math.max(0,Math.min(1,fraction)))/cycleLength);
    }

    private static int divideUp(int amount,int rate) {return Math.max(1,(amount+rate-1)/rate);}
}
