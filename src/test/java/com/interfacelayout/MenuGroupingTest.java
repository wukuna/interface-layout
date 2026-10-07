package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuStoneBackgrounds;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuGroupingTest
{
    private final Map<String, Object> settings = new HashMap<>();
    @Test public void detachDoesNotReuseGroupOwnedByHiddenOrUnavailableStone()
    {
        settings.put("menuGroup_modern_8", 1);
        MenuStoneController menu = controller(); menu.apply();
        menu.detach(0, new Point(400, 200));
        assertEquals(2, settings.get("menuGroup_modern_0"));
        assertEquals(1, settings.get("menuGroup_modern_8"));
    }
    private MenuStoneController controller()
    {
        return controller(2);
    }
    private MenuStoneController controller(int count)
    {
        return controller(count, MenuStoneController.Layout.VERTICAL_LEFT);
    }
    private MenuStoneController controller(int count, MenuStoneController.Layout arrangement)
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        for (int index = 0; index < count; index++)
        {
            Widget stone = WidgetGeometryTest.widget(index * 33, 600, 33, 36);
            when(client.getWidget(MenuStoneRegistry.MODERN[index][0])).thenReturn(stone);
        }
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(invocation -> settings.get(invocation.getArgument(1)));
        doAnswer(invocation -> { settings.put(invocation.getArgument(1), invocation.getArgument(2)); return null; })
            .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
        doAnswer(invocation -> { settings.remove(invocation.getArgument(1)); return null; })
            .when(configs).unsetConfiguration(anyString(), anyString());
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.menuLayout()).thenReturn(arrangement);
        when(config.hideSkillsStone()).thenAnswer(i -> Boolean.TRUE.equals(settings.get("hideStone1")));
        when(config.menuSpacing()).thenReturn(4);
        OrbController mode = mock(OrbController.class); when(mode.isLoggedIn()).thenReturn(true);
        return Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(ConfigManager.class).toInstance(configs);
                bind(OrbController.class).toProvider(() -> mode);
                bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                bind(MenuStoneBackgrounds.class).toProvider(() -> mock(MenuStoneBackgrounds.class));
            }
        }).getInstance(MenuStoneController.class);
    }
    @Test public void freeMenuGroupKeepsSavedOriginWhenStoneVisibilityChanges()
    {
        MenuStoneController menu = controller(2, MenuStoneController.Layout.FREE_POSITION);
        menu.saveGroup(0, new Point(400, 200)); menu.apply();
        settings.put("hideStone1", true); menu.apply();
        assertEquals(new Rectangle(400, 200, 33, 36), menu.bounds());
        assertTrue(menu.individualDragging());
        settings.put("hideStone1", false); menu.apply();
        assertEquals(new Rectangle(400, 200, 33, 76), menu.bounds());
    }
    @Test public void compactedNativeRowCanMoveAndStaysMovedAfterShowingStone()
    {
        MenuStoneController menu = controller(2, MenuStoneController.Layout.VANILLA_HORIZONTAL);
        settings.put("hideStone1", true); menu.apply();
        assertFalse(menu.groups().isEmpty());
        menu.saveGroup(0, new Point(400, 200)); menu.apply();
        assertEquals(new Rectangle(400, 200, 33, 36), menu.groups().get(0));
        settings.put("hideStone1", false); menu.apply();
        assertEquals(new Rectangle(400, 200, 70, 36), menu.groups().get(0));
        assertEquals(new Point(400, 200), settings.get("menuNativePosition_modern_0"));
    }
    @Test public void dropInsertsBesideMiddleNeighborRatherThanAtGroupEnd()
    {
        MenuStoneController menu = controller(4); menu.apply();
        menu.detach(3, new Point(400, 200)); menu.apply();
        menu.dropStone(3, new Point(16, 77)); menu.apply();
        assertEquals(new Rectangle(0, 80, 33, 36), menu.stones().get(3));
        assertEquals(new Rectangle(0, 120, 33, 36), menu.stones().get(2));
    }
    @Test public void detachCreatesIndependentGroupAndPersistsItsPosition()
    {
        MenuStoneController menu = controller(); menu.apply();
        assertEquals(new Rectangle(0, 0, 33, 76), menu.bounds());
        menu.detach(0, new Point(400, 200)); menu.apply();
        assertEquals(new Rectangle(400, 200, 33, 36), menu.stones().get(0));
        assertEquals(new Rectangle(0, 0, 33, 36), menu.stones().get(1));
        menu.moveStone(0, new Point(500, 300)); menu.apply();
        MenuStoneController reloaded = controller(); reloaded.apply();
        assertEquals(new Rectangle(500, 300, 33, 36), reloaded.stones().get(0));
    }
    @Test public void droppingBesideStoneCreatesRowWithExactSpacingAndWholeGroupDrag()
    {
        MenuStoneController menu = controller(); menu.apply();
        menu.detach(0, new Point(300, 200)); menu.apply();
        menu.dropStone(0, new Point(900, 500)); // Finish detaching before creating a new pair.
        menu.detach(0, new Point(300, 200)); menu.apply();
        menu.dropStone(0, new Point(40, 18)); menu.apply();
        assertEquals(new Rectangle(0, 0, 70, 36), menu.bounds());
        assertEquals(new Rectangle(37, 0, 33, 36), menu.stones().get(0));
        menu.saveGroup(0, new Point(900, 600)); menu.apply();
        assertEquals(new Rectangle(900, 600, 70, 36), menu.bounds());
    }
    @Test public void droppingBelowStoneCreatesColumnAndModeResetClearsGrouping()
    {
        MenuStoneController menu = controller(); menu.apply();
        menu.detach(0, new Point(300, 200)); menu.apply();
        menu.dropStone(0, new Point(16, 43)); menu.apply();
        assertEquals(new Rectangle(0, 40, 33, 36), menu.stones().get(0));
        settings.put("menuGroup_classic_0", 2);
        menu.resetCurrent(); menu.apply();
        assertEquals(new Rectangle(0, 0, 33, 36), menu.stones().get(0));
        assertEquals(2, settings.get("menuGroup_classic_0"));
    }
    @Test public void repeatedOutAndBackDragPreservesMainAndCustomBarAxes()
    {
        for (int group : new int[]{0, 3}) for (boolean row : new boolean[]{false, true})
            for (int count : new int[]{2, 4})
            {
                settings.clear();
                settings.put("menuHorizontal_modern_" + group, row);
                settings.put(group == 0 ? "menuPosition_modern" : "menuGroupPosition_modern_" + group, new Point(100, 100));
                for (int i = 0; i < count; i++) settings.put("menuGroup_modern_" + i, group);
                MenuStoneController menu = controller(count); menu.apply();
                Rectangle original = menu.groups().get(group);
                for (int cycle = 0; cycle < 5; cycle++)
                {
                    menu.detach(0, new Point(500, 300)); menu.apply();
                    Rectangle neighbor = menu.stones().get(1);
                    // Deliberately approach from the opposite axis.
                    menu.dropStone(0, new Point(neighbor.x + (row ? 16 : 40), neighbor.y + (row ? 43 : 18)));
                    menu.apply();
                    assertEquals(row, settings.get("menuHorizontal_modern_" + group));
                    assertEquals(original, menu.groups().get(group));
                }
            }
    }
    @Test public void JoiningAnotherExistingBarKeepsItsAxis()
    {
        settings.put("menuGroup_modern_0", 2);
        settings.put("menuGroupPosition_modern_2", new Point(500, 300));
        MenuStoneController menu = controller(3); menu.apply();
        menu.detach(0, new Point(500, 300)); menu.apply();
        menu.dropStone(0, new Point(40, 18)); menu.apply();
        assertEquals(false, settings.get("menuHorizontal_modern_0"));
        assertEquals(new Rectangle(0, 0, 33, 116), menu.bounds());
    }
}
