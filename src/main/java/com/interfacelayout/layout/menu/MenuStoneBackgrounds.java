package com.interfacelayout.layout.menu;

import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/** Split Modern's tiled row art into native tiles below the original buttons/icons. */
@Singleton
public class MenuStoneBackgrounds
{
    @Inject private Client client;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    private final Map<Widget, List<Widget>> owned = new IdentityHashMap<>();

    public void place(int index, Point destination, int width, int height)
    {
        place(index, destination, width, height, true);
    }

    public void place(int index, Point destination, int width, int height, boolean movable)
    {
        Widget background = prepareRow(index < 7, movable);
        if (background == null || background.isHidden()) return;
        List<Widget> children = owned.get(background);
        Widget tile = background.createChild(-1, WidgetType.GRAPHIC);
        children.add(tile);
        tile.setSpriteId(background.getSpriteId());
        tile.setSpriteTiling(true);
        tile.setOpacity(background.getOpacity());
        tile.setBorderType(background.getBorderType());
        tile.setNoClickThrough(false);
        tile.setOriginalWidth(width); tile.setOriginalHeight(height);
        Point parent = WidgetGeometry.location(background);
        tile.setOriginalX(destination.x - parent.x); tile.setOriginalY(destination.y - parent.y);
        tile.revalidate();
    }
    /** Remove the shared row art even when every stone in that row is hidden. */
    public Widget prepareRow(boolean top)
    {
        return prepareRow(top, true);
    }

    public Widget prepareRow(boolean top, boolean movable)
    {
        Widget background = client.getWidget(top ? InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND
            : InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND);
        if (background == null || background.isHidden()) return background;
        if (!owned.containsKey(background))
        {
            states.captureRendering(background);
            if (movable) expander.allowChildrenAcrossCanvas(background);
            background.setType(WidgetType.LAYER);
            owned.put(background, new ArrayList<>());
        }
        return background;
    }
    public void restore()
    {
        owned.forEach((parent, children) -> {
            Widget[] current = parent.getChildren();
            if (current == null) return;
            Widget[] remaining = current.clone();
            for (Widget child : children)
            {
                int index = child.getIndex();
                if (index >= 0 && index < remaining.length && remaining[index] == child) remaining[index] = null;
            }
            int length = remaining.length;
            while (length > 0 && remaining[length - 1] == null) length--;
            parent.setChildren(Arrays.copyOf(remaining, length));
        });
        owned.clear();
    }
}
