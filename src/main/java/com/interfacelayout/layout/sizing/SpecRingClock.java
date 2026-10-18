/*
 * BSD 2-Clause License
 * 
 * Copyright (c) 2026, Anuraag Venkat
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.interfacelayout.layout.sizing;

/**
 * Regeneration-only clock adapted from AnuVen/Special Attack Timers (BSD-2-Clause).
 * Copyright (c) 2026, Anuraag Venkat. See LICENSE and THIRD_PARTY_NOTICES.md.
 * No infoboxes, notifications or potion cooldowns are replaced.
 */
final class SpecRingClock
{
    private int cycle = 50, remaining = 50, previous = -1;
    private int ignoreUntil = -1, restoreAmount, pausedTick = -1;
    private boolean synchronizedClock, paused;

    void reset()
    {
        cycle = remaining = 50;
        previous = ignoreUntil = pausedTick = -1;
        restoreAmount = 0;
        synchronizedClock = paused = false;
    }

    void equipment(boolean lightbearer)
    {
        int nextCycle = lightbearer ? 25 : 50;
        if (cycle == nextCycle) return;
        remaining = lightbearer ? Math.min(remaining, 25) : 50;
        cycle = nextCycle;
    }

    void restore(int tick, int amount)
    {
        if (tick > ignoreUntil) restoreAmount = 0;
        ignoreUntil = tick + 2;
        restoreAmount += amount;
    }

    void pause(int tick) { paused = true; pausedTick = tick; }
    void resume() { paused = false; }
    void restart(int elapsed)
    {
        paused = false;
        remaining = Math.max(1, cycle - elapsed);
        synchronizedClock = true;
    }

    void tick(int energy, int tick)
    {
        if (previous >= 0 && energy > previous)
        {
            int increase = energy - previous;
            if (tick > ignoreUntil || increase > restoreAmount)
            {
                remaining = cycle;
                synchronizedClock = true;
                if (tick != pausedTick) paused = false;
            }
        }
        previous = energy;
        if (energy >= 1000)
        {
            remaining = cycle;
            synchronizedClock = true;
        }
        else if (!paused && --remaining <= 0) remaining = cycle;
    }

    double progress()
    {
        return !synchronizedClock || paused || previous < 0 || previous >= 1000
            ? 0 : 1.0 - remaining / (double) cycle;
    }
}
