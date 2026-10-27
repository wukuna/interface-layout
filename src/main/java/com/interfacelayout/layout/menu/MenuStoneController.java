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
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

@Singleton
public class MenuStoneController
{
    private static final int[] WINDOW_ROOTS = {
        InterfaceID.Bankmain.UNIVERSE, InterfaceID.BankpinKeypad.UNIVERSE,
        InterfaceID.Collection.FRAME, InterfaceID.CollectionOverview.UNIVERSE,
        InterfaceID.SkillGuide.WINDOW, InterfaceID.SkillGuideV2.UNIVERSE, InterfaceID.Settings.UNIVERSE,
        InterfaceID.CaOverview.UNIVERSE, InterfaceID.CaTasks.UNIVERSE, InterfaceID.CaBosses.UNIVERSE,
        InterfaceID.CaBoss.UNIVERSE, InterfaceID.CaRewards.UNIVERSE,
        InterfaceID.Questjournal.UNIVERSE, InterfaceID.QuestjournalOverview.UNIVERSE,
        InterfaceID.AreaTask.UNIVERSE, InterfaceID.Journalscroll.UNIVERSE,
        InterfaceID.Questdisplay.UNIVERSE, InterfaceID.Questscroll.UNIVERSE, InterfaceID.QuestscrollSpeedrun.UNIVERSE
    };
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
    public static boolean managesModernLogout(InterfaceLayoutConfig config)
    {
        Layout layout=config.menuLayout();
        return config.restoreLogoutTab() || layout==Layout.FREE_POSITION
            || layout==Layout.VERTICAL_LEFT || layout==Layout.VERTICAL_RIGHT;
    }
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController orbs;
    @Inject private ConfigManager configs;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    @Inject private MenuStoneBackgrounds backgrounds;
    @Inject private MenuButtonOverlay buttons;
    @Inject private MenuOverlayState overlayState;
    private boolean temporarilyShown;
    private int dragSourceGroup = -1, dragStone = -1;
    private String dragMode;
    private boolean dragSourceHorizontal;
    private Rectangle stack = new Rectangle();
    private volatile Map<Integer, Rectangle> groupBounds = Map.of(), stoneBounds = Map.of();
    private volatile List<Rectangle> gapBounds = List.of(), windowBounds = List.of();

    public Rectangle bounds() { return new Rectangle(stack); }
    public void toggleShown() { temporarilyShown = !temporarilyShown; }
    public void resetSession() { dragStone = -1; temporarilyShown = false; stack = new Rectangle(); groupBounds = Map.of(); stoneBounds = Map.of(); gapBounds = List.of(); windowBounds = List.of(); }
    public boolean isBlockedGap(Point point)
    {
        return !config.allowClicksThroughGaps() && gapBounds.stream().anyMatch(bounds -> bounds.contains(point))
            && windowBounds.stream().noneMatch(bounds -> bounds.contains(point));
    }
    public Map<Integer, Rectangle> groups() { return copyBounds(groupBounds); }
    public Map<Integer, Rectangle> stones() { return copyBounds(stoneBounds); }
    /** Identify an existing native action, never infer an action from a saved rectangle alone. */
    public boolean isHoveredStoneAction(MenuEntry entry)
    {
        if (entry == null || temporarilyShown || !orbs.isLoggedIn()) return false;
        switch (entry.getType())
        {
            case WIDGET_TYPE_1: case WIDGET_TYPE_4: case WIDGET_TYPE_5: case CC_OP: break;
            default: return false;
        }
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return false;
        int[][] registry = orbs.isFixedMode() ? MenuStoneRegistry.FIXED
            : orbs.isClassicResizable() ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
        int component = entry.getWidget() == null ? entry.getParam1() : entry.getWidget().getId();
        for (Map.Entry<Integer, Rectangle> bounds : stoneBounds.entrySet())
        {
            int index = bounds.getKey();
            if (!bounds.getValue().contains(mouse.getX(), mouse.getY()) || hidden(index)
                || (component != registry[index][0] && component != registry[index][1])) continue;
            Widget stone = client.getWidget(registry[index][0]), icon = client.getWidget(registry[index][1]);
            // Modern's stone graphic is a selection highlight, so use its icon
            // for native visibility, just as apply() does.
            return stone != null && !(icon != null ? icon.isHidden() : stone.isHidden());
        }
        return false;
    }
    public boolean individualDragging() { return config.menuLayout() != Layout.VANILLA_HORIZONTAL; }
    private static Map<Integer, Rectangle> copyBounds(Map<Integer, Rectangle> bounds)
    {
        Map<Integer, Rectangle> result = new LinkedHashMap<>();
        bounds.forEach((id, rectangle) -> result.put(id, new Rectangle(rectangle)));
        return result;
    }
    public void restoreBackgrounds() { buttons.clear(); backgrounds.restore(); }
    public String mode() { return orbs.isFixedMode() ? "fixed" : orbs.isClassicResizable() ? "classic" : "modern"; }

    public String layoutMode() { return mode() + (config.menuOverlayButtons() ? "_overlay" : ""); }

    public void apply()
    {
        overlayState.sync(config.menuOverlayButtons());
        if (config.menuOverlayButtons()) overlayState.seedMode(mode());
        stack = new Rectangle();
        groupBounds = Map.of(); stoneBounds = Map.of();
        gapBounds = List.of();
        windowBounds = List.of();
        if (!orbs.isLoggedIn() || temporarilyShown) return;
        windowBounds = modalBounds();
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
        buttons.prepare(registry, new int[]{rowBackground(0), rowBackground(1)}, modern);
        initializeFreeRows(registry);
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
            // Native rows leave the bare X at the minimap; custom bars can group it
            // with or without the optional full-size tab background.
            if (modern && i == 10 && !managesModernLogout(config)) continue;
            Widget stone = client.getWidget(registry[i][0]);
            Widget icon = client.getWidget(registry[i][1]);
            if (stone == null) continue;
            int row = i / 7;
            if (!(modern && i == 10))
                nativeOrigins.merge(row, WidgetGeometry.location(stone),
                    (a, b) -> new Point(Math.min(a.x, b.x), Math.min(a.y, b.y)));
            if (hidden(i)) { states.hide(stone, true); states.hide(icon, true); continue; }
            // Modern's stone graphic is a selection highlight: its own hidden flag
            // must not remove an otherwise visible tab from the layout.
            boolean interactive = !(icon != null ? icon.isHidden() : stone.isHidden());
            boolean decoration = modern ? (i == 10 ? !stone.isHidden() : backgrounds.isNativeRowVisible(i < 7))
                : backgrounds.isNativeRowVisible(rowBackground(i / 7)) || !stone.isHidden();
            if ((!vertical && !compactRow[row]) || (!interactive && !decoration)) continue;
            Entry entry = new Entry(i, stone, icon);
            if (modern && i == 10)
            {
                // Reserve ordinary tab artwork, but keep both native widgets' sizes/actions.
                Widget peer = client.getWidget(registry[11][0]);
                if (peer == null || icon == null || !interactive || !backgrounds.isNativeRowVisible(false)) continue;
                entry = new Entry(i, stone, icon, config.restoreLogoutTab() ? peer.getWidth() : stone.getWidth(),
                    config.restoreLogoutTab() ? peer.getHeight() : stone.getHeight());
                Widget friends = client.getWidget(registry[9][0]);
                entry.sortX = friends == null ? WidgetGeometry.location(peer).x
                    : (WidgetGeometry.location(friends).x + WidgetGeometry.location(peer).x) / 2;
            }
            if (buttons.active())
            {
                buttons.dimensions(i, entry.width, entry.height, entry.stoneOffset, entry.iconOffset);
                entry.width = buttons.scaled(i, entry.width); entry.height = buttons.scaled(i, entry.height);
            }
            groups.computeIfAbsent(vertical ? group(i) : row, ignored -> new ArrayList<>()).add(entry);
        }
        Map<Integer, Rectangle> nextGroups = new LinkedHashMap<>(), nextStones = new LinkedHashMap<>();
        List<Rectangle> nextGaps = new ArrayList<>();
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
            boolean intact = false;
            for (Map.Entry<Integer, List<Entry>> group : groups.entrySet())
                intact |= intactNativeRow(group.getKey(), group.getValue()) == row;
            if (buttons.active()) { buttons.hideRow(rowBackground(row)); continue; }
            if (!modern && intact) continue;
            if (modern) backgrounds.prepareRow(row == 0, vertical || hasSavedOrigin(row) || (row == 1 && config.restoreLogoutTab()));
            else backgrounds.prepareRow(rowBackground(row), vertical || hasSavedOrigin(row));
        }
        for (Map.Entry<Integer, List<Entry>> group : groups.entrySet())
        {
            int id = group.getKey();
            List<Entry> entries = group.getValue();
            int sourceRow = intactNativeRow(id, entries);
            if (!buttons.active() && !modern && sourceRow >= 0)
            {
                // Native click targets have unequal widths/offsets and overlap the
                // shared row artwork. Packing them edge-to-edge shifts the icons.
                // Translate native artwork and targets together; split only for occlusion.
                Rectangle nativeRow = new Rectangle(entries.get(0).nativeBounds);
                for (Entry entry : entries) nativeRow.add(entry.nativeBounds);
                Point origin = hasSavedOrigin(id) ? loadOrigin(id, nativeRow.width, nativeRow.height, nativeRow.getLocation())
                    : nativeRow.getLocation();
                int dx = origin.x - nativeRow.x, dy = origin.y - nativeRow.y;
                boolean moved = dx != 0 || dy != 0;
                boolean split = entries.stream().anyMatch(entry -> covered(translated(entry.nativeBounds, dx, dy)));
                if (split) backgrounds.prepareRow(rowBackground(sourceRow), moved);
                else if (moved) backgrounds.translateRow(rowBackground(sourceRow), dx, dy);
                for (Entry entry : entries)
                {
                    Rectangle placed = translated(entry.nativeBounds, dx, dy);
                    if (moved)
                    {
                        expander.allowCanvasPosition(entry.stone);
                        if (entry.icon != null) expander.allowCanvasPosition(entry.icon);
                        move(entry.stone, placed.getLocation());
                        if (entry.icon != null) move(entry.icon, new Point(placed.x + entry.iconOffset.x, placed.y + entry.iconOffset.y));
                    }
                    if (covered(placed)) { states.hide(entry.stone, true); states.hide(entry.icon, true); }
                    else
                    {
                        if (split) backgrounds.placeFromRow(rowBackground(sourceRow), placed.getLocation(), entry.nativeBounds, moved);
                        if (entry.interactive) nextStones.put(entry.index, placed);
                    }
                }
                nextGroups.put(id, translated(nativeRow, dx, dy));
                continue;
            }
            entries.sort(Comparator.comparingInt(entry -> vertical ? order(entry.index)
                : entry.sortX));
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
            Rectangle previous = null;
            boolean previousInteractive = false;
            for (Entry entry : entries)
            {
                Point destination = new Point(origin.x + (horizontal ? offset : (width - entry.width) / 2),
                    origin.y + (horizontal ? (height - entry.height) / 2 : offset));
                // Closing gaps within a native row must not resize its clipping layers.
                boolean movable = vertical || hasSavedOrigin(id) || (modern && entry.index == 10);
                if (movable && !buttons.active())
                {
                    expander.allowCanvasPosition(entry.stone);
                    if (entry.icon != null) expander.allowCanvasPosition(entry.icon);
                }
                Rectangle placed = new Rectangle(destination.x, destination.y, entry.width, entry.height);
                boolean covered = windowBounds.stream().anyMatch(bounds -> bounds.intersects(placed));
                if (buttons.active())
                {
                    buttons.place(entry.index, placed, covered);
                    // Decoration-only native entries must not remain at their old
                    // position while their row is represented by overlay buttons.
                    states.hide(entry.stone, true); states.hide(entry.icon, true);
                }
                else
                {
                    if (modern && !covered && (entry.index != 10 || config.restoreLogoutTab()))
                        backgrounds.place(entry.index, destination, entry.width, entry.height, movable);
                    else if (!modern && !covered)
                        backgrounds.placeFromRow(rowBackground(entry.index / 7), destination, entry.nativeBounds, movable);
                    move(entry.stone, new Point(destination.x + entry.stoneOffset.x, destination.y + entry.stoneOffset.y));
                    if (entry.icon != null) move(entry.icon, new Point(destination.x + entry.iconOffset.x, destination.y + entry.iconOffset.y));
                    if (modern && entry.index == 10 && config.restoreLogoutTab() && !covered)
                        backgrounds.placeLogoutIcon(entry.icon,
                            new Point(destination.x + entry.iconOffset.x, destination.y + entry.iconOffset.y));
                    if (covered) { states.hide(entry.stone, true); states.hide(entry.icon, true); }
                }
                boolean interactive = !covered && entry.interactive;
                if (interactive) nextStones.put(entry.index, placed);
                if (interactive && previousInteractive && config.menuSpacing() > 0)
                {
                    Rectangle gap = horizontal
                        ? new Rectangle(previous.x + previous.width, origin.y, config.menuSpacing(), height)
                        : new Rectangle(origin.x, previous.y + previous.height, width, config.menuSpacing());
                    nextGaps.add(gap);
                }
                previous = placed; previousInteractive = interactive;
                offset += (horizontal ? entry.width : entry.height) + config.menuSpacing();
            }
            nextGroups.put(id, new Rectangle(origin.x, origin.y, width, height));
        }
        groupBounds = Map.copyOf(nextGroups); stoneBounds = Map.copyOf(nextStones);
        gapBounds = List.copyOf(nextGaps);
        stack = nextGroups.getOrDefault(0, new Rectangle());
    }

    private int rowBackground(int row)
    {
        if (orbs.isFixedMode()) return row == 0 ? InterfaceID.Toplevel.SIDE_TOP_CONTAINER_GRAPHIC0
            : InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER_GRAPHIC0;
        if (orbs.isClassicResizable()) return row == 0 ? InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC5
            : InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC3;
        return row == 0 ? InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND
            : InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND;
    }

    /** Complete horizontal rows retain their native artwork and click-target offsets. */
    private int intactNativeRow(int id, List<Entry> entries)
    {
        if (entries.size() != 7) return -1;
        int row = entries.get(0).index / 7;
        if (!untouchedNativeRow(row)) return -1;
        if (config.menuLayout() != Layout.VANILLA_HORIZONTAL
            && (config.menuLayout() != Layout.FREE_POSITION || !horizontal(id))) return -1;
        if (config.menuLayout() == Layout.VANILLA_HORIZONTAL) return row;
        List<Entry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingInt(entry -> order(entry.index)));
        for (int i = 0; i < 7; i++) if (ordered.get(i).index != row * 7 + i) return -1;
        return row;
    }

    private boolean untouchedNativeRow(int row)
    {
        if (config.menuSpacing() != 0) return false;
        for (int i = row * 7; i < row * 7 + 7; i++) if (hidden(i)) return false;
        return true;
    }

    private boolean covered(Rectangle bounds)
    {
        return windowBounds.stream().anyMatch(window -> window.intersects(bounds));
    }

    private static Rectangle translated(Rectangle bounds, int dx, int dy)
    {
        Rectangle result = new Rectangle(bounds); result.translate(dx, dy); return result;
    }

    private void initializeFreeRows(int[][] registry)
    {
        if (config.menuLayout() != Layout.FREE_POSITION) return;
        String mode = layoutMode();
        if (Boolean.TRUE.equals(configs.getConfiguration("interfacelayout", "menuSeparateRows_" + mode, Boolean.class))) return;
        // Split legacy implicit defaults too, even when their bar was moved.
        // Explicit memberships/orders are deliberate edits and must survive.
        java.util.Set<Integer> used = new java.util.HashSet<>(); used.add(0);
        boolean implicitBottom = false;
        for (int i = 0; i < 14; i++)
        {
            Integer saved = configs.getConfiguration("interfacelayout", "menuGroup_" + mode + "_" + i, Integer.class);
            if (saved != null && saved >= 0 && saved <= 14) used.add(saved);
            if (i >= 7 && saved == null
                && configs.getConfiguration("interfacelayout", "menuOrder_" + mode + "_" + i, Integer.class) == null) implicitBottom = true;
        }
        if (!implicitBottom) return;
        int bottomGroup = 1;
        while (used.contains(bottomGroup) && bottomGroup < 14) bottomGroup++;
        if (used.contains(bottomGroup)) return;
        Rectangle[] rows = new Rectangle[2];
        int[] widths = new int[2], heights = new int[2];
        for (int i = 0; i < registry.length; i++)
        {
            // Modern's Logout-X lives beside the minimap, not in the bottom row.
            if (!orbs.isFixedMode() && !orbs.isClassicResizable() && i == 10) continue;
            Widget stone = client.getWidget(registry[i][0]);
            if (stone == null) continue;
            Rectangle bounds = WidgetGeometry.bounds(stone);
            if (rows[i / 7] == null) rows[i / 7] = bounds; else rows[i / 7].add(bounds);
            widths[i / 7] = Math.max(widths[i / 7], bounds.width);
            heights[i / 7] += bounds.height + config.menuSpacing();
        }
        if (rows[0] == null || rows[1] == null) return;
        Point previousOrigin = configs.getConfiguration("interfacelayout", "menuPosition_" + mode, Point.class);
        if (previousOrigin != null)
        {
            int dx = previousOrigin.x - rows[0].x, dy = previousOrigin.y - rows[0].y;
            rows[0].translate(dx, dy); rows[1].translate(dx, dy);
        }
        if (config.menuDirection() == Direction.VERTICAL)
        {
            Point first = LayoutGeometry.clamp(rows[0].getLocation(), widths[0], heights[0] - config.menuSpacing(),
                client.getCanvasWidth(), client.getCanvasHeight());
            int secondX = first.x >= widths[1] + 8 ? first.x - widths[1] - 8 : first.x + widths[0] + 8;
            Point second = LayoutGeometry.clamp(new Point(secondX, first.y), widths[1], heights[1] - config.menuSpacing(),
                client.getCanvasWidth(), client.getCanvasHeight());
            rows[0].setLocation(first); rows[1].setLocation(second);
        }
        for (int i = 0; i < 14; i++)
            if (configs.getConfiguration("interfacelayout", "menuGroup_" + mode + "_" + i, Integer.class) == null
                && configs.getConfiguration("interfacelayout", "menuOrder_" + mode + "_" + i, Integer.class) == null)
                configs.setConfiguration("interfacelayout", "menuGroup_" + mode + "_" + i, i < 7 ? 0 : bottomGroup);
        configs.setConfiguration("interfacelayout", "menuSeparateRows_" + mode, true);
        configs.setConfiguration("interfacelayout", "menuBottomGroup_" + mode, bottomGroup);
        // A reused numeric slot must not inherit an old detached bar's anchor preferences.
        configs.setConfiguration("interfacelayout", "menuGeneration_" + mode + "_" + bottomGroup,
            groupGeneration(mode, bottomGroup) + 1);
        for (int row = 0; row < 2; row++)
        {
            int group = row == 0 ? 0 : bottomGroup;
            if (configs.getConfiguration("interfacelayout", "menuHorizontal_" + mode + "_" + group, Boolean.class) == null)
                configs.setConfiguration("interfacelayout", "menuHorizontal_" + mode + "_" + group,
                    config.menuDirection() == Direction.HORIZONTAL);
            if (row != 0 || previousOrigin == null) saveGroup(group, rows[row].getLocation());
        }
    }

    private List<Rectangle> modalBounds()
    {
        List<Rectangle> result = new ArrayList<>();
        // Use each window's actual frame, never a full-canvas INFINITY parent
        // or its resize preview. Bank/PIN retain their existing bounds sources.
        for (int id : WINDOW_ROOTS)
        {
            Widget window = client.getWidget(id);
            if (window == null || window.isHidden()) continue;
            Rectangle bounds = WidgetGeometry.bounds(window);
            if (!bounds.isEmpty()) result.add(bounds);
        }
        return List.copyOf(result);
    }
    public boolean windowBoundsChanged()
    {
        return orbs.isLoggedIn() && !temporarilyShown && !windowBounds.equals(modalBounds());
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
        if (config.menuLayout() == Layout.VANILLA_HORIZONTAL) return "menuNativePosition_" + layoutMode() + "_" + group;
        return group == 0 ? "menuPosition_" + layoutMode() : "menuGroupPosition_" + layoutMode() + "_" + group;
    }
    private int group(int index)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuGroup_" + layoutMode() + "_" + index, Integer.class);
        return value == null || value < 0 || value > 14 ? 0 : value;
    }
    public int groupForStone(int index) { return group(index); }
    public int groupGeneration(int id)
    { return groupGeneration(layoutMode(), id); }
    public int groupGeneration(String mode, int id)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuGeneration_" + mode + "_" + id, Integer.class);
        return value == null ? 0 : value;
    }
    private int order(int index)
    {
        Integer value = configs.getConfiguration("interfacelayout", "menuOrder_" + layoutMode() + "_" + index, Integer.class);
        return value == null ? index : value;
    }
    private boolean horizontal(int group)
    {
        Boolean saved = configs.getConfiguration("interfacelayout", "menuHorizontal_" + layoutMode() + "_" + group, Boolean.class);
        return saved != null ? saved : config.menuLayout() == Layout.FREE_POSITION
            && config.menuDirection() == Direction.HORIZONTAL;
    }
    public void saveGroup(int group, Point point) { configs.setConfiguration("interfacelayout", positionKey(group), point); }
    public void detach(int index, Point point)
    {
        int currentGroup = group(index), members = 0;
        dragStone = index; dragSourceGroup = currentGroup; dragMode = layoutMode();
        dragSourceHorizontal = horizontal(currentGroup);
        for (int member : stoneBounds.keySet()) if (group(member) == currentGroup) members++;
        if (members <= 1) { saveGroup(currentGroup, point); return; }
        int id = 1;
        java.util.Set<Integer> usedGroups = new java.util.HashSet<>();
        for (int member = 0; member < 14; member++) usedGroups.add(group(member));
        while (id < 14 && usedGroups.contains(id)) id++;
        // A reused numeric slot is a new bar, not the old bar's external anchor assignment.
        configs.setConfiguration("interfacelayout", "menuGeneration_" + layoutMode() + "_" + id, groupGeneration(id) + 1);
        configs.setConfiguration("interfacelayout", "menuGroup_" + layoutMode() + "_" + index, id);
        configs.setConfiguration("interfacelayout", "menuHorizontal_" + layoutMode() + "_" + id, false);
        saveGroup(id, point);
    }
    public void moveStone(int index, Point point) { saveGroup(group(index), point); }
    public void reattach(int index)
    {
        dragStone = -1;
        if (config.menuLayout() == Layout.FREE_POSITION
            && Boolean.TRUE.equals(configs.getConfiguration("interfacelayout", "menuSeparateRows_" + layoutMode(), Boolean.class)))
        {
            Integer bottom = configs.getConfiguration("interfacelayout", "menuBottomGroup_" + layoutMode(), Integer.class);
            configs.setConfiguration("interfacelayout", "menuGroup_" + layoutMode() + "_" + index, index < 7 ? 0 : bottom == null ? 1 : bottom);
        }
        else configs.unsetConfiguration("interfacelayout", "menuGroup_" + layoutMode() + "_" + index);
        configs.unsetConfiguration("interfacelayout", "menuOrder_" + layoutMode() + "_" + index);
    }
    public void dropStone(int index, Point cursor)
    {
        boolean returning = dragStone == index && layoutMode().equals(dragMode);
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
            configs.setConfiguration("interfacelayout", "menuGroup_" + layoutMode() + "_" + index, targetGroup);
            for (int position = 0; position < members.size(); position++)
                configs.setConfiguration("interfacelayout", "menuOrder_" + layoutMode() + "_" + members.get(position), position);
            configs.setConfiguration("interfacelayout", "menuHorizontal_" + layoutMode() + "_" + targetGroup, row);
            return;
        }
    }
    public void resetGroup(int group) { configs.unsetConfiguration("interfacelayout", positionKey(group)); }
    public void resetCurrent() { resetMode(layoutMode()); }
    private void resetMode(String mode)
    {
        dragStone = -1;
        configs.unsetConfiguration("interfacelayout", "menuSeparateRows_" + mode);
        configs.unsetConfiguration("interfacelayout", "menuBottomGroup_" + mode);
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
        for (String mode : new String[]{"fixed", "classic", "modern", "fixed_overlay", "classic_overlay", "modern_overlay"}) resetMode(mode);
    }
    private static final class Entry
    {
        final int index;
        int width, height;
        final Widget stone, icon;
        final Point iconOffset, stoneOffset;
        int sortX;
        final Rectangle nativeBounds;
        final boolean interactive;
        Entry(int index, Widget stone, Widget icon)
        {
            this.index = index; this.stone = stone; this.icon = icon;
            interactive = !(icon != null ? icon.isHidden() : stone.isHidden());
            width = stone.getWidth(); height = stone.getHeight();
            Point position = WidgetGeometry.location(stone), graphic = icon == null ? position : WidgetGeometry.location(icon);
            nativeBounds = new Rectangle(position.x, position.y, width, height);
            iconOffset = new Point(graphic.x - position.x, graphic.y - position.y);
            stoneOffset = new Point(); sortX = position.x;
        }
        Entry(int index, Widget stone, Widget icon, int width, int height)
        {
            this.index = index; this.stone = stone; this.icon = icon;
            interactive = icon != null && !icon.isHidden();
            this.width = Math.max(width, stone.getWidth()); this.height = Math.max(height, stone.getHeight());
            nativeBounds = WidgetGeometry.bounds(stone); sortX = nativeBounds.x;
            stoneOffset = new Point((this.width - stone.getWidth()) / 2, (this.height - stone.getHeight()) / 2);
            iconOffset = new Point((this.width - icon.getWidth()) / 2, (this.height - icon.getHeight()) / 2);
        }
    }
}
