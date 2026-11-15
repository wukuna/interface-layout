package com.interfacelayout.layout.orbs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

final class OverlayDiscovery
{
    private final List<String> names;
    private List<Overlay> found = Collections.emptyList();
    private int cycle;
    private boolean valid;

    OverlayDiscovery(String... names) { this.names = Arrays.asList(names); }

    List<Overlay> find(OverlayManager manager, int currentCycle)
    {
        if (!valid || cycle != currentCycle)
        {
            List<Overlay> matches = new ArrayList<>();
            manager.anyMatch(overlay -> {
                if (names.contains(overlay.getClass().getName())) matches.add(overlay);
                return false;
            });
            found = matches;
            cycle = currentCycle;
            valid = true;
        }
        return found;
    }

    void clear() { valid = false; found = Collections.emptyList(); }
}
