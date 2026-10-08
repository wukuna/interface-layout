/*
 * BSD 2-Clause License
 * 
 * Copyright (c) 2025, Richardant
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 */
/* Adapted hiding mappings from Menu Stones Hider.
 * Copyright (c) 2025, Richardant. BSD-2-Clause: see LICENSE.
 */
package com.interfacelayout.layout.menu;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.util.LayoutGeometry;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import com.interfacelayout.util.WidgetGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

@Singleton
public class MenuStoneController
{
    public enum Layout
    {
        VANILLA_HORIZONTAL("Native rows"), FREE_POSITION("Free position"),
        VERTICAL_LEFT("Vertical left"), VERTICAL_RIGHT("Vertical right"), HIDDEN("Hidden");
        private final String label;
        Layout(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum Direction
    {
        VERTICAL("Column"), HORIZONTAL("Row");
        private final String label;
        Direction(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController orbs;
    @Inject private ConfigManager configs;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    @Inject private MenuStoneBackgrounds backgrounds;
    private boolean temporarilyShown;
    private int dragSourceGroup = -1, dragStone = -1;
    private String dragMode;
    private boolean dragSourceHorizontal;
    private Rectangle stack = new Rectangle();
    private volatile Map<Integer, Rectangle> groupBounds = Map.of(), stoneBounds = Map.of();

    public Rectangle bounds() { return new Rectangle(stack); }
    public void toggleShown() { temporarilyShown = !temporarilyShown; }
    public void resetSession() { dragStone = -1; temporarilyShown = false; stack = new Rectangle(); groupBounds = Map.of(); stoneBounds = Map.of(); }
    public Map<Integer, Rectangle> groups() { return copyBounds(groupBounds); }
    public Map<Integer, Rectangle> stones() { return copyBounds(stoneBounds); }
    public boolean individualDragging() { return config.menuLayout() != Layout.VANILLA_HORIZONTAL; }
    private static Map<Integer, Rectangle> copyBounds(Map<Integer, Rectangle> bounds)
    {
        Map<Integer, Rectangle> result = new LinkedHashMap<>();
        bounds.forEach((id, rectangle) -> result.put(id, new Rectangle(rectangle)));
        return result;
    }
    public void restoreBackgrounds() { backgrounds.restore(); }
    public String mode() { return orbs.isFixedMode() ? "fixed" : orbs.isClassicResizable() ? "classic" : "modern"; }

    public void apply()
    {
        stack = new Rectangle();
        groupBounds = Map.of(); stoneBounds = Map.of();
        if (!orbs.isLoggedIn() || temporarilyShown) return;
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC1, config.hideLeftBar());
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC2, config.hideRightBar());
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC5, config.hideTopMenu());
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_TOP, config.hideTopMenu());
        hideDecoration(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER, config.hideTopMenu());
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC3, config.hideBottomMenu());
        hideDecoration(InterfaceID.ToplevelOsrsStretch.SIDE_BOTTOM, config.hideBottomMenu());
        hideDecoration(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER, config.hideBottomMenu());
        hideDecoration(InterfaceID.Toplevel.SIDE_TOP_CONTAINER, config.hideTopMenu());
        hideDecoration(InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER, config.hideBottomMenu());
        int[][] registry = orbs.isFixedMode() ? MenuStoneRegistry.FIXED
            : orbs.isClassicResizable() ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
        boolean vertical = config.menuLayout() == Layout.VERTICAL_LEFT || config.menuLayout() == Layout.VERTICAL_RIGHT
            || config.menuLayout() == Layout.FREE_POSITION;
        boolean modern = !orbs.isFixedMode() && !orbs.isClassicResizable();
        // Own both native rows consistently, so visibility does not switch between
        // built-in and plugin overlay preferences (and lose the dragged origin).
        boolean[] compactRow = {!vertical, !vertical};
        for (int i = 0; i < registry.length; i++)
            if (hidden(i) && !(modern && i == 10)) compactRow[i / 7] = true;
        if (!vertical)
            for (int row = 0; row < 2; row++)
                compactRow[row] |= configs.getConfiguration("interfacelayout", positionKey(row), Point.class) != null;
        Map<Integer, Point> nativeOrigins = new LinkedHashMap<>();
        Map<Integer, List<Entry>> groups = new TreeMap<>();
        for (int i = 0; i < registry.length; i++)
        {
            Widget stone = client.getWidget(registry[i][0]);
            Widget icon = client.getWidget(registry[i][1]);
            if (stone == null) continue;
            int row = i / 7;
            if (!(modern && i == 10))
                nativeOrigins.merge(row, WidgetGeometry.location(stone),
                    (a, b) -> new Point(Math.min(a.x, b.x), Math.min(a.y, b.y)));
            if (hidden(i)) { states.hide(stone, true); states.hide(icon, true); continue; }
            // Modern Logout-X belongs to the minimap, not either native tab row.
            if (!vertical && modern && i == 10) continue;
            // Modern's stone graphic is a selection highlight: its own hidden flag
            // must not remove an otherwise visible tab from the layout.
            if ((!vertical && !compactRow[row]) || (icon != null ? icon.isHidden() : stone.isHidden())) continue;
            groups.computeIfAbsent(vertical ? group(i) : row, ignored -> new ArrayList<>()).add(new Entry(i, stone, icon));
        }
        Map<Integer, Rectangle> nextGroups = new LinkedHashMap<>(), nextStones = new LinkedHashMap<>();
        // Keep the entire native arrangement when any requested group cannot fit.
        for (Map.Entry<Integer, List<Entry>> group : groups.entrySet())
        {
            boolean row = !vertical || horizontal(group.getKey());
            int length = -config.menuSpacing(), cross = 0;
            for (Entry entry : group.getValue())
            {
                length += (row ? entry.width : entry.height) + config.menuSpacing();
                cross = Math.max(cross, row ? entry.height : entry.width);
            }
            if (length > (row ? client.getCanvasWidth() : client.getCanvasHeight())
                || cross > (row ? client.getCanvasHeight() : client.getCanvasWidth())) return;
        }
        for (int row = 0; row < 2; row++)
        {
            if (!vertical && !compactRow[row]) continue;
            if (modern) backgrounds.prepareRow(row == 0, vertical || hasSavedOrigin(row));
            else if (orbs.isClassicResizable()) hideDecoration(row == 0
                ? InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC5
                : InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC3, true);
            else hideDecoration(row == 0 ? InterfaceID.Toplevel.SIDE_TOP_CONTAINER_GRAPHIC0
                : InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER_GRAPHIC0, true);
        }
        for (Map.Entry<Integer, List<Entry>> group : groups.entrySet())
        {
            int id = group.getKey();
            List<Entry> entries = group.getValue();
            entries.sort(Comparator.comparingInt(entry -> vertical ? order(entry.index)
                : WidgetGeometry.location(entry.stone).x));
            boolean horizontal = !vertical || horizontal(id);
            int width = 0, height = 0;
            for (Entry entry : entries)
            {
                if (horizontal) { width += entry.width + config.menuSpacing(); height = Math.max(height, entry.height); }
                else { width = Math.max(width, entry.width); height += entry.height + config.menuSpacing(); }
            }
            if (horizontal) width -= config.menuSpacing(); else height -= config.menuSpacing();
            if (width > client.getCanvasWidth() || height > client.getCanvasHeight()) continue;
            Point origin = loadOrigin(id, width, height, vertical ? null : nativeOrigins.getOrDefault(id, new Point()));
            int offset = 0;
            for (Entry entry : entries)
            {
                Point destination = new Point(origin.x + (horizontal ? offset : (width - entry.width) / 2),
                    origin.y + (horizontal ? (height - entry.height) / 2 : offset));
                // Closing gaps within a native row must not resize its clipping layers.
                boolean movable = vertical || hasSavedOrigin(id);
                if (movable)
                {
                    expander.allowCanvasPosition(entry.stone);
                    if (entry.icon != null) expander.allowCanvasPosition(entry.icon);
                }
                if (modern)
                    backgrounds.place(entry.index, destination, entry.width, entry.height, movable);
                move(entry.stone, destination);
                if (entry.icon != null) move(entry.icon, new Point(destination.x + entry.iconOffset.x, destination.y + entry.iconOffset.y));
                nextStones.put(entry.index, new Rectangle(destination.x, destination.y, entry.width, entry.height));
                offset += (horizontal ? entry.width : entry.height) + config.menuSpacing();
            }
            nextGroups.put(id, new Rectangle(origin.x, origin.y, width, height));
        }
        groupBounds = Map.copyOf(nextGroups); stoneBounds = Map.copyOf(nextStones);
        stack = nextGroups.getOrDefault(0, new Rectangle());
    }

    private boolean hasSavedOrigin(int group)
    {
        return configs.getConfiguration("interfacelayout", positionKey(group), Point.class) != null;
    }

    private boolean hidden(int index)
    {
        return config.menuLayout() == Layout.HIDDEN || hideIndividualStone(index) || (config.hiddenStones() & (1 << index)) != 0
            || (index < 7 ? config.hideTopMenu() : config.hideBottomMenu());
    }
    // Config interfaces are RuneLite proxies: only annotated getters belong there.
    private boolean hideIndividualStone(int index)
    {
        switch (index)
        {
            case 0: return config.hideCombatStone();
            case 1: return config.hideSkillsStone();
            case 2: return config.hideQuestsStone();
            case 3: return config.hideInventoryStone();
            case 4: return config.hideEquipmentStone();
            case 5: return config.hidePrayerStone();
            case 6: return config.hideSpellbookStone();
            case 7: return config.hideClanStone();
            case 8: return config.hideAccountManagementStone();
            case 9: return config.hideFriendsStone();
            case 10: return config.hideLogoutStone();
            case 11: return config.hideSettingsStone();
            case 12: return config.hideEmotesStone();
            case 13: return config.hideMusicStone();
            default: return false;
        }
    }
    private void hideDecoration(int id, boolean hidden) { states.hide(client.getWidget(id), hidden); }
    private void move(Widget widget, Point destination)
    {
        Rectangle parent = WidgetGeometry.bounds(widget.getParent());
        Point relative = LayoutGeometry.relative(destination, parent);
        states.position(widget, relative.x, relative.y);
    }
    private Point loadOrigin(int group, int width, int height, Point nativeOrigin)
    {
        Point saved = configs.getConfiguration("interfacelayout", positionKey(group), Point.class);
        Point point = saved != null ? saved : nativeOrigin != null ? nativeOrigin : new Point(config.menuLayout() == Layout.VERTICAL_RIGHT
            || config.menuLayout() == Layout.FREE_POSITION
            ? client.getCanvasWidth() - width : 0, 0);
        return LayoutGeometry.clamp(point, width, height, client.getCanvasWidth(), client.getCanvasHeight());
    }
    public void saveOrigin(Point point)
    {
        saveGroup(0, point);
    }
    private String positionKey(int group)
    {
        if (config.menuLayout() == Layout.VANILLA_HORIZONTAL) return "menuNativePosition_" + mode() + "_" + group;
        return group == 0 ? "menuPosition_" + mode() : "menuGroupPosition_" + mode() + "_" + group;
    }
    private int group(int index)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuGroup_" + mode() + "_" + index, Integer.class);
        return value == null || value < 0 || value > 14 ? 0 : value;
    }
    public int groupForStone(int index) { return group(index); }
    public int groupGeneration(int id)
    { return groupGeneration(mode(), id); }
    public int groupGeneration(String mode, int id)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuGeneration_" + mode + "_" + id, Integer.class);
        return value == null ? 0 : value;
    }
    private int order(int index)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuOrder_" + mode() + "_" + index, Integer.class);
        return value == null ? index : value;
    }
    private boolean horizontal(int group)
    {
        Boolean saved = configs.getConfiguration("interfacelayout", "menuHorizontal_" + mode() + "_" + group, Boolean.class);
        return saved != null ? saved : config.menuLayout() == Layout.FREE_POSITION
            && config.menuDirection() == Direction.HORIZONTAL;
    }
    public void saveGroup(int group, Point point) { configs.setConfiguration("interfacelayout", positionKey(group), point); }
    public void detach(int index, Point point)
    {
        int currentGroup = group(index), members = 0;
        dragStone = index; dragSourceGroup = currentGroup; dragMode = mode();
        dragSourceHorizontal = horizontal(currentGroup);
        for (int member : stoneBounds.keySet()) if (group(member) == currentGroup) members++;
        if (members <= 1) { saveGroup(currentGroup, point); return; }
        int id = 1;
        java.util.Set<Integer> usedGroups = new java.util.HashSet<>();
        for (int member = 0; member < 14; member++) usedGroups.add(group(member));
        while (id < 14 && usedGroups.contains(id)) id++;
        // A reused numeric slot is a new bar, not the old bar's external anchor assignment.
        configs.setConfiguration("interfacelayout", "menuGeneration_" + mode() + "_" + id, groupGeneration(id) + 1);
        configs.setConfiguration("interfacelayout", "menuGroup_" + mode() + "_" + index, id);
        configs.setConfiguration("interfacelayout", "menuHorizontal_" + mode() + "_" + id, false);
        saveGroup(id, point);
    }
    public void moveStone(int index, Point point) { saveGroup(group(index), point); }
    public void reattach(int index)
    {
        dragStone = -1;
        configs.unsetConfiguration("interfacelayout", "menuGroup_" + mode() + "_" + index);
        configs.unsetConfiguration("interfacelayout", "menuOrder_" + mode() + "_" + index);
    }
    public void dropStone(int index, Point cursor)
    {
        boolean returning = dragStone == index && mode().equals(dragMode);
        dragStone = -1;
        Map.Entry<Integer, Rectangle> closest = null;
        double distance = Double.MAX_VALUE;
        for (Map.Entry<Integer, Rectangle> candidate : stoneBounds.entrySet())
        {
            if (candidate.getKey() == index) continue;
            Rectangle nearby = new Rectangle(candidate.getValue()); nearby.grow(18, 18);
            if (!nearby.contains(cursor)) continue;
            double candidateDistance = cursor.distanceSq(candidate.getValue().getCenterX(), candidate.getValue().getCenterY());
            if (candidateDistance < distance) { distance = candidateDistance; closest = candidate; }
        }
        if (closest != null)
        {
            Map.Entry<Integer, Rectangle> candidate = closest;
            int targetGroup = group(candidate.getKey());
            Rectangle target = candidate.getValue();
            int dx = cursor.x - (target.x + target.width / 2), dy = cursor.y - (target.y + target.height / 2);
            int visibleMembers = 0;
            for (int member : stoneBounds.keySet())
                if (member != index && group(member) == targetGroup) visibleMembers++;
            // Existing bars keep their axis, including a two-stone bar temporarily
            // reduced to one member during this drag. Only a new pair chooses an axis.
            boolean row = returning && targetGroup == dragSourceGroup ? dragSourceHorizontal
                : visibleMembers > 1 ? horizontal(targetGroup) : Math.abs(dx) > Math.abs(dy);
            boolean before = row ? dx < 0 : dy < 0;
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < 14; i++) if (i != index && group(i) == targetGroup) members.add(i);
            members.sort(Comparator.comparingInt(this::order).thenComparingInt(Integer::intValue));
            int insertion = members.indexOf(candidate.getKey()) + (before ? 0 : 1);
            members.add(insertion, index);
            configs.setConfiguration("interfacelayout", "menuGroup_" + mode() + "_" + index, targetGroup);
            for (int position = 0; position < members.size(); position++)
                configs.setConfiguration("interfacelayout", "menuOrder_" + mode() + "_" + members.get(position), position);
            configs.setConfiguration("interfacelayout", "menuHorizontal_" + mode() + "_" + targetGroup, row);
            return;
        }
    }
    public void resetGroup(int group) { configs.unsetConfiguration("interfacelayout", positionKey(group)); }
    public void resetCurrent() { resetMode(mode()); }
    private void resetMode(String mode)
    {
        dragStone = -1;
        configs.unsetConfiguration("interfacelayout", "menuPosition_" + mode);
        for (int row = 0; row < 2; row++) configs.unsetConfiguration("interfacelayout", "menuNativePosition_" + mode + "_" + row);
        for (int i = 0; i < 14; i++)
        {
            configs.unsetConfiguration("interfacelayout", "menuGroup_" + mode + "_" + i);
            configs.unsetConfiguration("interfacelayout", "menuOrder_" + mode + "_" + i);
        }
        for (int i = 0; i <= 14; i++)
        {
            configs.unsetConfiguration("interfacelayout", "menuGroupPosition_" + mode + "_" + i);
            configs.unsetConfiguration("interfacelayout", "menuHorizontal_" + mode + "_" + i);
        }
    }
    public void resetAll()
    {
        for (String mode : new String[]{"fixed", "classic", "modern"}) resetMode(mode);
    }
    private static final class Entry
    {
        final int index, width, height;
        final Widget stone, icon;
        final Point iconOffset;
        Entry(int index, Widget stone, Widget icon)
        {
            this.index = index; this.stone = stone; this.icon = icon;
            width = stone.getWidth(); height = stone.getHeight();
            Point position = WidgetGeometry.location(stone), graphic = icon == null ? position : WidgetGeometry.location(icon);
            iconOffset = new Point(graphic.x - position.x, graphic.y - position.y);
        }
    }
}
