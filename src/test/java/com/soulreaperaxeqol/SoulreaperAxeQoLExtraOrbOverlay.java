package com.soulreaperaxeqol;
import java.awt.*;
import net.runelite.client.ui.overlay.*;
public class SoulreaperAxeQoLExtraOrbOverlay extends Overlay {
    public int renders;
    public SoulreaperAxeQoLExtraOrbOverlay() { setLayer(OverlayLayer.ABOVE_WIDGETS); }
    @Override public Dimension render(Graphics2D graphics) { renders++; return null; }
}
