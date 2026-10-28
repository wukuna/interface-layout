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
package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** A separate, native-size view of the equipped shield's existing special action. */
@Singleton
public final class ShieldSpecialOrb extends Overlay
{
    // Verified from native scripts 10177/10159 and item definitions on 2026-10-28.
    // This is a specific charge system, not a catch-all for every shield ability.
    static final int SHIELD_KIND_PARAM = 2731;
    static final int CHARGES_VARBIT = 16438;
    static final int SHIELD_SPRITE = 8605;
    static final int MAX_CHARGES = 20;
    static final int SPECIAL_COST_ENUM = 906;
    private static final int WIDTH = 57, HEIGHT = 34;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private InterfaceLayoutConfig config;
    @Inject private MenuButtonSprites sprites;
    @Inject private TooltipManager tooltips;
    private volatile boolean active;
    private State painted;
    private final Rectangle hitbox = new Rectangle();
    private MenuEntry projected;
    private State projectedState;

    public ShieldSpecialOrb()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_LOW);
        setMovable(true);
        setSnappable(true);
    }

    @Override public String getName() { return "INTERFACE_LAYOUT_SHIELD_SPECIAL"; }
    public void start() { active = true; }
    public void stop()
    {
        active = false;
        final MenuEntry oldEntry = projected;
        clientThread.invoke(() -> {
            Menu menu = client.getMenu();
            if (menu != null && oldEntry != null)
                for (MenuEntry entry : menu.getMenuEntries())
                    if (entry == oldEntry) menu.removeMenuEntry(entry);
            if (!active) { painted = null; hitbox.setBounds(0, 0, 0, 0); }
        });
    }

    State resolve()
    {
        if (!active || !config.showShieldSpecialOrb() || client.getGameState() != GameState.LOGGED_IN) return null;
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        Item shield = equipment == null ? null : equipment.getItem(5);
        if (shield == null || shield.getId() < 0 || shield.getQuantity() <= 0) return null;
        ItemComposition item = client.getItemDefinition(shield.getId());
        if (item == null || item.getIntValue(SHIELD_KIND_PARAM) != 1) return null;
        EnumComposition costs = client.getEnum(SPECIAL_COST_ENUM);
        int cost = costs == null ? 0 : costs.getIntValue(shield.getId());
        if (cost <= 0 || cost > MAX_CHARGES) return null;
        Widget orb = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);
        Widget button = client.getWidget(InterfaceID.Orbs.SPECBUTTON);
        Widget icon = client.getWidget(InterfaceID.Orbs.SPECENERGY_ICON);
        if (orb == null || button == null || icon == null || orb.isHidden() || button.isHidden()
            || button.getOnOpListener() == null) return null;
        int operation = operation(button.getActions(), icon.getSpriteId());
        if (operation == 0) return null;
        int charges = client.getVarbitValue(CHARGES_VARBIT);
        if (charges < 0 || charges > MAX_CHARGES) return null;
        return new State(shield.getId(), client.getTopLevelInterfaceId(), orb, button, operation, charges, cost,
            button.getActions()[operation - 1]);
    }

    static int operation(String[] actions, int primaryIcon)
    {
        if (actions == null) return 0;
        // These are the game's existing operations. No scripts or packets are invoked.
        if (actions.length > 1 && ("Use Offhand".equals(actions[1]) || "Deactivate Offhand".equals(actions[1]))) return 2;
        if (primaryIcon == SHIELD_SPRITE && actions.length > 0
            && ("Use".equals(actions[0]) || "Deactivate".equals(actions[0]))) return 1;
        return 0;
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        painted = null;
        hitbox.setBounds(0, 0, 0, 0);
        State state = resolve();
        if (state == null) return null;
        Widget backing = client.getWidget(InterfaceID.Orbs.SPECENERGY_BACKING);
        BufferedImage frame = sprites.get(backing);
        BufferedImage fill = sprites.getSprite(state.action.startsWith("Deactivate") ? 1608 : 1607);
        BufferedImage empty = sprites.getSprite(1059);
        BufferedImage shield = sprites.getSprite(SHIELD_SPRITE);
        if (frame == null || fill == null || empty == null || shield == null) return null;
        boolean wideFrame = backing != null && (backing.getSpriteId() == 5791 || backing.getSpriteId() == 5792);
        int width = wideFrame ? 65 : WIDTH;
        int circleX = width - 30;
        int baseline = wideFrame ? 22 : 26;
        int previousX = getBounds().x, previousY = getBounds().y;
        if (getPreferredLocation() == null && getPreferredPosition() == null)
        {
            Rectangle nativeBounds = state.orb.getBounds();
            int x = nativeBounds.x - width - 4, y = nativeBounds.y;
            if (x < 0) { x = nativeBounds.x; y += HEIGHT + 4; }
            getBounds().setLocation(Math.max(0, Math.min(client.getCanvasWidth() - width, x)),
                Math.max(0, Math.min(client.getCanvasHeight() - HEIGHT, y)));
        }
        Graphics2D draw = (Graphics2D) graphics.create();
        try
        {
            draw.translate(getBounds().x - previousX, getBounds().y - previousY);
            draw.drawImage(frame, 0, 0, width, HEIGHT, null);
            draw.drawImage(fill, circleX, 4, 26, 26, null);
            Graphics2D mask = (Graphics2D) draw.create();
            try
            {
                mask.clipRect(circleX, 4, 26, 26 * (MAX_CHARGES - state.charges) / MAX_CHARGES);
                mask.drawImage(empty, circleX, 4, 26, 26, null);
            }
            finally { mask.dispose(); }
            draw.drawImage(shield, circleX, 4, 26, 26, null);
            draw.setFont(FontManager.getRunescapeSmallFont());
            String number = Integer.toString(state.charges);
            int textX = (circleX + 3) / 2 - draw.getFontMetrics().stringWidth(number) / 2;
            draw.setColor(Color.BLACK); draw.drawString(number, textX + 1, baseline + 1);
            draw.setColor(state.charges < state.cost ? Color.RED : state.charges == MAX_CHARGES ? Color.GREEN : Color.YELLOW);
            draw.drawString(number, textX, baseline);
        }
        finally { draw.dispose(); }
        hitbox.setBounds(getBounds().x, getBounds().y, width, HEIGHT);
        painted = state;
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (!client.isMenuOpen() && mouse != null && hitbox.contains(mouse.getX(), mouse.getY()))
            tooltips.add(new Tooltip("Shield special: " + state.charges + "/" + MAX_CHARGES
                + " charges<br>Requires " + state.cost + " charges. Drag using RuneLite's overlay modifier."));
        return new Dimension(width, HEIGHT);
    }

    @Subscribe(priority = -20)
    public void onPostMenuSort(PostMenuSort event)
    {
        if (client.isMenuOpen()) return;
        State current = resolve();
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (!sameControl(painted, current) || mouse == null || !hitbox.contains(mouse.getX(), mouse.getY())) return;
        Menu menu = client.getMenu();
        if (menu == null) return;
        // Keep menus from other interfaces and overlay-editing controls above us.
        for (MenuEntry entry : menu.getMenuEntries())
            if (entry.getWidget() != null || entry.getType() == MenuAction.RUNELITE_OVERLAY
                || entry.getType() == MenuAction.RUNELITE_OVERLAY_CONFIG) return;
        for (MenuEntry entry : menu.getMenuEntries())
            if (entry.getType() != MenuAction.CANCEL) menu.removeMenuEntry(entry);
        projected = menu.createMenuEntry(-1).setOption(current.action)
            .setTarget("<col=ff9040>Shield Special Attack</col>").setType(MenuAction.CC_OP)
            .setIdentifier(current.operation).setParam0(-1).setParam1(current.button.getId());
        projectedState = current;
    }

    @Subscribe(priority = 20)
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuEntry() == projected && !sameControl(projectedState, resolve())) event.consume();
    }

    private static boolean sameControl(State a, State b)
    {
        return a != null && b != null && a.item == b.item && a.root == b.root && a.button == b.button
            && a.operation == b.operation && a.action.equals(b.action);
    }

    static final class State
    {
        final int item, root, operation, charges, cost;
        final Widget orb, button;
        final String action;
        State(int item, int root, Widget orb, Widget button, int operation, int charges, int cost, String action)
        {
            this.item = item; this.root = root; this.orb = orb; this.button = button;
            this.operation = operation; this.charges = charges; this.cost = cost; this.action = action;
        }
    }
}
