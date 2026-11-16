package com.interfacelayout.layout.orbs;

final class PrayerRegenerationClock
{
    static final int PERIOD = 12;
    private int previous = -1;
    private int remaining;
    private int elapsed;
    private boolean synchronizedPhase;

    void reset()
    {
        previous = -1;
        remaining = elapsed = 0;
        synchronizedPhase = false;
    }

    int tick(int value)
    {
        value = Math.max(0, value);
        int restored = 0;
        if (previous >= 0 && value != previous)
        {
            restored = previous - value == 1 ? 1 : 0;
            elapsed = 0;
            synchronizedPhase = value > 0;
        }
        else if (synchronizedPhase) elapsed = Math.min(PERIOD, elapsed + 1);
        if (value == 0) synchronizedPhase = false;
        previous = remaining = value;
        return restored;
    }

    boolean active() { return remaining > 0; }

    double nextTicks(double fraction)
    {
        if (!active()) return -1;
        return synchronizedPhase ? Math.max(0, PERIOD - elapsed - fraction) : Double.NaN;
    }

    double progress(double fraction)
    {
        double next = nextTicks(fraction);
        return Double.isFinite(next) && next >= 0 ? 1 - next / PERIOD : 0;
    }

    int remainingRestores() { return remaining; }
}
