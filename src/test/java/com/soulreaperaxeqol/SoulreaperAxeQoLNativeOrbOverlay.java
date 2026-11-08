package com.soulreaperaxeqol;
import java.awt.*;
import net.runelite.client.ui.overlay.*;
public class SoulreaperAxeQoLNativeOrbOverlay extends Overlay {
    public int renders;
    public SoulreaperAxeQoLNativeOrbOverlay() { setLayer(OverlayLayer.ABOVE_WIDGETS); }
    @Override public Dimension render(Graphics2D graphics) { renders++; return null; }
}
