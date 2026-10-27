package com.interfacelayout.layout.menu;

import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.eventbus.Subscribe;

/** Preserve the game's visible stone action when object swaps change menu order. */
@Singleton
public class MenuStoneClickPriority
{
    private static final Set<MenuAction> OBJECT_ACTIONS = EnumSet.of(
        MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
        MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
        MenuAction.GAME_OBJECT_FIFTH_OPTION);
    @Inject private Client client;
    @Inject private MenuStoneController stones;
    private Menu capturedMenu;
    private MenuEntry nativeTop;
    private Point mouse;
    private MenuAction type;
    private int identifier, param0, param1;
    private final NativeMenuListener nativeListener = new NativeMenuListener();

    public NativeMenuListener nativeListener() { return nativeListener; }

    public final class NativeMenuListener
    {
        @Subscribe(priority = 1)
        public void onPostMenuSort(PostMenuSort event) { beforeSwaps(event); }
    }

    public void clear() { capturedMenu = null; nativeTop = null; mouse = null; }

    public void beforeSwaps(PostMenuSort event)
    {
        clear();
        if (client.isMenuOpen() || client.getGameState() != GameState.LOGGED_IN) return;
        Menu menu = client.getMenu();
        if (menu == null) return;
        MenuEntry[] entries = menu.getMenuEntries();
        if (entries.length == 0) return;
        MenuEntry top = entries[entries.length - 1];
        // An overlapping game window must already win here. Never manufacture
        // an action for a stone that the native menu builder did not select.
        if (!stones.isHoveredStoneAction(top)) return;
        capturedMenu = menu; nativeTop = top; mouse = client.getMouseCanvasPosition();
        type = top.getType(); identifier = top.getIdentifier();
        param0 = top.getParam0(); param1 = top.getParam1();
    }

    @Subscribe(priority = -10)
    public void onPostMenuSort(PostMenuSort event) { afterSwaps(event); blockGapActions(); }

    private void blockGapActions()
    {
        if (client.isMenuOpen() || client.getGameState() != GameState.LOGGED_IN) return;
        Point point = client.getMouseCanvasPosition();
        if (point == null || !stones.isBlockedGap(new java.awt.Point(point.getX(), point.getY()))) return;
        Menu menu = client.getMenu();
        if (menu == null) return;
        MenuEntry[] entries = menu.getMenuEntries();
        // A game window or other UI entry takes precedence over gap protection.
        // Only a native Cancel-only menu is safe; never create synthetic actions.
        MenuEntry cancel = null;
        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.CANCEL) { cancel = entry; continue; }
            switch (entry.getType())
            {
                case WALK: case GAME_OBJECT_FIRST_OPTION: case GAME_OBJECT_SECOND_OPTION:
                case GAME_OBJECT_THIRD_OPTION: case GAME_OBJECT_FOURTH_OPTION: case GAME_OBJECT_FIFTH_OPTION:
                case EXAMINE_OBJECT: case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION:
                case NPC_FOURTH_OPTION: case NPC_FIFTH_OPTION: case EXAMINE_NPC:
                case GROUND_ITEM_FIRST_OPTION: case GROUND_ITEM_SECOND_OPTION: case GROUND_ITEM_THIRD_OPTION:
                case GROUND_ITEM_FOURTH_OPTION: case GROUND_ITEM_FIFTH_OPTION: case EXAMINE_ITEM_GROUND:
                case WIDGET_TARGET_ON_GAME_OBJECT: case WIDGET_TARGET_ON_NPC: case WIDGET_TARGET_ON_GROUND_ITEM:
                    break;
                default: return;
            }
        }
        if (cancel != null && entries.length > 1) menu.setMenuEntries(new MenuEntry[]{cancel});
    }

    public void afterSwaps(PostMenuSort event)
    {
        Menu menu = capturedMenu;
        MenuEntry action = nativeTop;
        Point position = mouse;
        clear(); // No menu entry can leak into another frame or open right-click menu.
        if (action == null || client.isMenuOpen() || client.getGameState() != GameState.LOGGED_IN
            || client.getMenu() != menu || !position.equals(client.getMouseCanvasPosition())
            || action.getType() != type || action.getIdentifier() != identifier
            || action.getParam0() != param0 || action.getParam1() != param1
            || !stones.isHoveredStoneAction(action)) return;
        MenuEntry[] entries = menu.getMenuEntries();
        if (entries.length == 0 || !OBJECT_ACTIONS.contains(entries[entries.length - 1].getType())) return;
        for (int i = 0; i < entries.length - 1; i++)
        {
            if (entries[i] != action) continue;
            // Move the same native entry. Preserve callbacks, parameters, and
            // the relative order of every other entry; no action is replayed.
            System.arraycopy(entries, i + 1, entries, i, entries.length - i - 1);
            entries[entries.length - 1] = action;
            menu.setMenuEntries(entries);
            return;
        }
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event) { clear(); }
}
