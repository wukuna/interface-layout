package fixtures;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.*;

public final class OrdinaryOverlay extends Overlay
{
    public OrdinaryOverlay(){setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.UNDER_WIDGETS);getBounds().setBounds(40,50,120,35);}
    public void immovable(){setMovable(false);}
    @Override public String getName(){return "Example status";}
    @Override public Dimension render(Graphics2D graphics){return new Dimension(120,35);}
}
