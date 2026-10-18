package com.interfacelayout.layout.sizing;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpecRingClockTest
{
    @Test public void midCycleWaitsForObservedRegenThenTracksNormalAndLightbearerCycles()
    {
        SpecRingClock clock = new SpecRingClock();
        clock.tick(500, 1);
        for (int tick = 2; tick < 70; tick++) { clock.tick(500, tick); assertEquals(0, clock.progress(), 0); }
        clock.tick(600, 70);
        assertEquals(0.02, clock.progress(), 0.0001);
        clock.equipment(true);
        clock.tick(600, 71);
        assertEquals(0.04, clock.progress(), 0.0001);
        for (int tick = 72; tick <= 90; tick++) clock.tick(600, tick);
        double beforeSwitch = clock.progress();
        clock.equipment(true);
        assertEquals(beforeSwitch, clock.progress(), 0);
        clock.equipment(false);
        assertEquals(0, clock.progress(), 0);
    }

    @Test public void potionAndDeathChargeDoNotResetCycleButConcurrentNaturalRegenDoes()
    {
        SpecRingClock clock = new SpecRingClock();
        clock.tick(200, 1); clock.restart(10);
        clock.restore(2, 250); clock.restore(2, 150);
        clock.tick(600, 2);
        assertEquals(0.22, clock.progress(), 0.0001);
        clock.restore(3, 150);
        clock.tick(850, 3); // Accumulated grace covers all expected effects until it expires.
        assertEquals(0.24, clock.progress(), 0.0001);
        clock.tick(950, 6); // Natural regeneration, outside the effect grace.
        assertEquals(0.02, clock.progress(), 0.0001);
        clock.restore(7, 150);
        clock.tick(1000, 7);
        assertEquals(0, clock.progress(), 0);

        clock.reset(); clock.tick(200, 1); clock.restart(10);
        clock.restore(2, 250); clock.tick(550, 2); // 25% potion plus 10% regeneration.
        assertEquals(0.02, clock.progress(), 0.0001);
    }

    @Test public void pausesWaveStartsFullEnergyAndResetDoNotLeaveStaleRings()
    {
        SpecRingClock clock = new SpecRingClock();
        clock.tick(500, 1); clock.restart(20); clock.pause(2);
        for (int tick = 2; tick < 100; tick++) { clock.tick(500, tick); assertEquals(0, clock.progress(), 0); }
        clock.restart(0); clock.tick(500, 100);
        assertEquals(0.02, clock.progress(), 0.0001);
        clock.pause(101); clock.tick(600, 101); assertEquals(0, clock.progress(), 0);
        clock.tick(700, 102); assertTrue(clock.progress() > 0);
        clock.tick(1000, 103); assertEquals(0, clock.progress(), 0);
        clock.tick(500, 104); assertEquals(0.02, clock.progress(), 0.0001);
        clock.reset(); clock.tick(500, 105); assertEquals(0, clock.progress(), 0);
    }
}
