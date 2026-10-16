package com.interfacelayout.layout.menu;

import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
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

/** Keep native row artwork below the original buttons/icons as stones move. */
@Singleton
public class MenuStoneBackgrounds
{
    @Inject private Client client;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    private final Map<Widget, List<Widget>> owned = new IdentityHashMap<>();
    private final Map<Widget, Rectangle> nativeBounds = new IdentityHashMap<>();
    public boolean isNativeRowVisible(boolean top)
    {
        return isNativeRowVisible(top ? InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND
            : InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND);
    }
    public boolean isNativeRowVisible(int component)
    {
        Widget row = client.getWidget(component);
        return row != null && !row.isHidden();
    }

    /** Move the full sprite with its native offset from the click targets intact. */
    public void translateRow(int component, int dx, int dy)
    {
        Widget background = client.getWidget(component);
        if (background == null || background.isHidden()) return;
        Point original = WidgetGeometry.location(background);
        expander.allowCanvasPosition(background);
        Point parent = WidgetGeometry.location(background.getParent());
        states.position(background, original.x + dx - parent.x, original.y + dy - parent.y);
    }

    /** Classic/Fixed use a complete row sprite, not Modern's repeating stone tile. */
    public void placeFromRow(int component, Point destination, Rectangle source, boolean movable)
    {
        Widget background = prepareRow(component, movable);
        if (background == null || background.isHidden()) return;
        Rectangle row = nativeBounds.get(background);
        Widget clip = background.createChild(-1, WidgetType.LAYER);
        owned.get(background).add(clip);
        Point parent = WidgetGeometry.location(background);
        clip.setOriginalX(destination.x - parent.x); clip.setOriginalY(destination.y - parent.y);
        clip.setOriginalWidth(source.width); clip.setOriginalHeight(source.height);
        clip.setNoClickThrough(false);
        clip.revalidate();
        Widget art = clip.createChild(-1, WidgetType.GRAPHIC);
        // Dynamic descendants may share the static component's sparse child array.
        // Journal both identities so restoration never leaves orphan artwork.
        owned.get(background).add(art);
        art.setSpriteId(background.getSpriteId());
        art.setSpriteTiling(background.getSpriteTiling());
        art.setOpacity(background.getOpacity());
        art.setBorderType(background.getBorderType());
        art.setNoClickThrough(false);
        art.setOriginalX(row.x - source.x); art.setOriginalY(row.y - source.y);
        art.setOriginalWidth(row.width); art.setOriginalHeight(row.height);
        art.revalidate();
    }

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
        return prepareRow(top ? InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND
            : InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND, movable);
    }

    public Widget prepareRow(int component, boolean movable)
    {
        Widget background = client.getWidget(component);
        if (background == null || background.isHidden()) return background;
        if (!owned.containsKey(background))
        {
            nativeBounds.put(background, WidgetGeometry.bounds(background));
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
        nativeBounds.clear();
    }
}
