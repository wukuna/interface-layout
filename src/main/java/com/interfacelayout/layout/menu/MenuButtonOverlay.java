/*
 * Native menu-operation projection adapted from Custom Game Tabs.
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2026, Frederick Campbell
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.interfacelayout.layout.menu;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Optional replacement tab surfaces. Native widgets keep their dimensions. */
@Singleton
public class MenuButtonOverlay extends Overlay
{
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private WidgetStateStore states;
    @Inject private MenuButtonSprites sprites;
    @Inject private EventBus eventBus;
    private final Map<Integer, Button> prepared = new HashMap<>();
    private final List<Button> placed = new ArrayList<>();
    private boolean active;
    private boolean modern;
    private int root;

    public MenuButtonOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_LOW);
    }

    public void clear() { active = false; prepared.clear(); placed.clear(); }
    public void stop() { clear(); sprites.clear(); }
    public boolean active() { return active; }
    public int scaled(int size)
    {
        int percent = Math.max(25, Math.min(200, config.menuGroupSize()));
        return Math.max(1, (int) Math.round(size * percent / 100.0));
    }

    /** Called after native visibility/geometry restoration, before layout edits. */
    public void prepare(int[][] registry, int[] rows, boolean modern)
    {
        clear();
        if (!config.menuOverlayButtons()) return;
        this.modern = modern;
        root = client.getTopLevelInterfaceId();
        for (int index = 0; index < registry.length; index++)
        {
            Widget stone = client.getWidget(registry[index][0]), icon = client.getWidget(registry[index][1]);
            if (stone == null || icon == null || icon.isHidden()) continue;
            String[] actions = stone.getActions();
            // These controls must still be ordinary, script-operated tab buttons.
            // Fail back to native layout as a whole on an unfamiliar gameframe.
            if (stone.getOnOpListener() == null || actions == null || actions.length == 0
                || actions[0] == null || actions[0].isBlank()) { clear(); return; }
            if (stone.getWidth() <= 0 || stone.getHeight() <= 0 || sprites.get(icon) == null)
            { clear(); return; }
            prepared.put(index, new Button(index, stone, icon, client.getWidget(rows[index / 7])));
        }
        active = !prepared.isEmpty();
    }

    public void hideRow(int component) { states.hide(client.getWidget(component), true); }

    public void dimensions(int index, int width, int height, Point stoneOffset, Point iconOffset)
    {
        Button button = prepared.get(index);
        if (button == null) return;
        button.width = width; button.height = height;
        button.stoneOffset = new Point(stoneOffset); button.iconOffset = new Point(iconOffset);
    }

    public void place(int index, Rectangle bounds, boolean covered)
    {
        Button button = prepared.get(index);
        if (button == null) return;
        states.hide(button.stone, true); states.hide(button.icon, true);
        if (covered) return;
        button.bounds = new Rectangle(bounds);
        placed.add(button);
    }

    private boolean current()
    {
        return active && config.menuOverlayButtons() && client.getGameState() == GameState.LOGGED_IN
            && client.getTopLevelInterfaceId() == root;
    }

    private boolean current(Button button)
    {
        return current() && client.getWidget(button.stone.getId()) == button.stone
            && client.getWidget(button.icon.getId()) == button.icon
            && !states.isHiddenByGame(button.icon);
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!current()) return null;
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        for (Button button : placed)
        {
            if (!current(button)) continue;
            // An unknown native interface at the pointer wins over this overlay.
            if (mouse != null && button.bounds.contains(mouse.getX(), mouse.getY()) && foreignInterfaceMenu()) continue;
            Graphics2D draw = (Graphics2D) graphics.create();
            try
            {
                draw.clip(button.bounds);
                draw.translate(button.bounds.x, button.bounds.y);
                draw.scale((double) button.bounds.width / button.width, (double) button.bounds.height / button.height);
                draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                if (button.row != null && button.rowVisible && !(modern && button.index == 10 && !config.restoreLogoutTab()))
                {
                    Rectangle area = modern ? new Rectangle(0, 0, button.width, button.height)
                        : new Rectangle(button.rowBounds.x - button.nativeBounds.x,
                            button.rowBounds.y - button.nativeBounds.y, button.rowBounds.width, button.rowBounds.height);
                    paint(draw, button.row, area, modern || button.row.getSpriteTiling());
                }
                if (!states.isHiddenByGame(button.stone))
                    paint(draw, button.stone, new Rectangle(button.stoneOffset.x, button.stoneOffset.y,
                        button.stone.getWidth(), button.stone.getHeight()), button.stone.getSpriteTiling());
                paint(draw, button.icon, new Rectangle(button.iconOffset.x, button.iconOffset.y,
                    button.icon.getWidth(), button.icon.getHeight()), button.icon.getSpriteTiling());
            }
            finally { draw.dispose(); }
            if (!client.isMenuOpen() && mouse != null && button.bounds.contains(mouse.getX(), mouse.getY()))
            {
                graphics.setColor(new Color(255, 210, 100, 140));
                graphics.drawRect(button.bounds.x, button.bounds.y, button.bounds.width - 1, button.bounds.height - 1);
            }
        }
        return null;
    }

    private void paint(Graphics2D graphics, Widget widget, Rectangle area, boolean tiled)
    {
        BufferedImage image = sprites.get(widget);
        if (image == null || area.width <= 0 || area.height <= 0) return;
        Graphics2D draw = (Graphics2D) graphics.create();
        try
        {
            draw.setComposite(AlphaComposite.SrcOver.derive((256 - Math.max(0, Math.min(255, widget.getOpacity()))) / 256f));
            if (tiled)
            {
                draw.setPaint(new TexturePaint(image, new Rectangle(area.x, area.y, image.getWidth(), image.getHeight())));
                draw.fill(area);
            }
            else
            {
                int x1 = widget.isFlippedHorizontally() ? image.getWidth() : 0, x2 = image.getWidth() - x1;
                int y1 = widget.isFlippedVertically() ? image.getHeight() : 0, y2 = image.getHeight() - y1;
                draw.drawImage(image, area.x, area.y, area.x + area.width, area.y + area.height, x1, y1, x2, y2, null);
            }
        }
        finally { draw.dispose(); }
    }

    @Subscribe(priority = -20)
    public void onPostMenuSort(PostMenuSort event)
    {
        if (!current() || client.isMenuOpen() || foreignInterfaceMenu()) return;
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return;
        for (Button button : placed)
        {
            if (!button.bounds.contains(mouse.getX(), mouse.getY()) || !current(button)) continue;
            String[] actions = button.stone.getActions();
            if (actions == null || button.stone.getOnOpListener() == null) return;
            Menu menu = client.getMenu();
            if (menu == null) return;
            // An explicit click on a visible tab occupies this surface; remove
            // world entries underneath it, retaining the existing Cancel entry.
            for (MenuEntry entry : menu.getMenuEntries())
                if (entry.getType() != MenuAction.CANCEL) menu.removeMenuEntry(entry);
            for (int slot = Math.min(10, actions.length) - 1; slot >= 0; slot--)
            {
                if (actions[slot] == null || actions[slot].isBlank()) continue;
                MenuEntry entry = menu.createMenuEntry(-1).setOption(actions[slot]).setTarget("")
                    .setType(slot < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY)
                    .setIdentifier(slot + 1).setParam0(-1).setParam1(button.stone.getId());
                // Preserve RuneLite's registered widget options (e.g. spellbook).
                // The game executes its normal widget operation on a user click.
                eventBus.post(new MenuEntryAdded(entry));
            }
            return;
        }
    }

    private boolean foreignInterfaceMenu()
    {
        Menu menu = client.getMenu();
        if (menu == null) return false;
        for (MenuEntry entry : menu.getMenuEntries())
        {
            if (entry.getType() == MenuAction.RUNELITE_OVERLAY || entry.getType() == MenuAction.RUNELITE_OVERLAY_CONFIG) return true;
            Widget widget = entry.getWidget();
            if (widget == null) continue;
            boolean own = false;
            for (Button button : placed) own |= widget == button.stone || widget == button.icon;
            if (!own) return true;
        }
        return false;
    }

    private static final class Button
    {
        final int index;
        final Widget stone, icon, row;
        final Rectangle nativeBounds, rowBounds;
        final boolean rowVisible;
        Rectangle bounds;
        int width, height;
        Point stoneOffset, iconOffset;
        Button(int index, Widget stone, Widget icon, Widget row)
        {
            this.index = index; this.stone = stone; this.icon = icon; this.row = row;
            nativeBounds = WidgetGeometry.bounds(stone);
            rowBounds = WidgetGeometry.bounds(row);
            rowVisible = row != null && !row.isHidden();
        }
    }
}
