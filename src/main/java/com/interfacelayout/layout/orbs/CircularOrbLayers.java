package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

final class CircularOrbLayers
{
    private final List<Layer> layers = new ArrayList<>();

    static CircularOrbLayers capture(Widget parent, Widget backing, Widget button, Widget icon,
        MenuButtonSprites sprites, Consumer<Widget> hide)
    {
        CircularOrbLayers result = new CircularOrbLayers();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        result.capture(parent, backing, button, icon, WidgetGeometry.bounds(parent), sprites, hide, visited);
        return result;
    }

    private void capture(Widget widget, Widget backing, Widget button, Widget icon, Rectangle clip,
        MenuButtonSprites sprites, Consumer<Widget> hide, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget) || widget.isHidden()) return;
        Rectangle bounds = WidgetGeometry.bounds(widget);
        Rectangle visible = clip.intersection(bounds);
        if (visible.isEmpty()) return;
        if (widget != backing && widget != button && widget != icon && widget.getType() == WidgetType.GRAPHIC
            && widget.getOpacity() < 255)
        {
            BufferedImage image = sprites.getPadded(widget);
            if (image != null)
            {
                layers.add(new Layer(image, bounds, visible, widget.getOpacity()));
                hide.accept(widget);
            }
        }
        if (widget.getType() == WidgetType.LAYER)
            for (Widget child : children(widget)) capture(child, backing, button, icon, visible, sprites, hide, visited);
    }

    static List<Widget> children(Widget widget)
    {
        List<Widget> result = new ArrayList<>();
        Set<Widget> added = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget[] group : new Widget[][]{widget.getStaticChildren(), widget.getChildren(), widget.getDynamicChildren(),widget.getNestedChildren()})
            if (group != null) for (Widget child : group) if (child != null && added.add(child)) result.add(child);
        return result;
    }

    void paint(Graphics2D graphics, Rectangle circle)
    {
        for (Layer layer : layers)
        {
            Graphics2D draw = (Graphics2D) graphics.create();
            try
            {
                draw.clip(new Ellipse2D.Double(circle.x, circle.y, circle.width, circle.height));
                draw.clip(layer.clip);
                draw.setComposite(AlphaComposite.SrcOver.derive((255 - layer.opacity) / 255f));
                draw.drawImage(layer.image, layer.bounds.x, layer.bounds.y, layer.bounds.width, layer.bounds.height, null);
            }
            finally { draw.dispose(); }
        }
    }

    private static final class Layer
    {
        final BufferedImage image;
        final Rectangle bounds, clip;
        final int opacity;
        Layer(BufferedImage image, Rectangle bounds, Rectangle clip, int opacity)
        {
            this.image = image; this.bounds = bounds; this.clip = clip; this.opacity = opacity;
        }
    }
}
