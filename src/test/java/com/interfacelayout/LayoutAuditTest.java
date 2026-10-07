package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuStoneBackgrounds;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LayoutAuditTest
{
    @Test public void hiddenMiddleStoneClosesVerticalGapAndHasNoBackground()
    { menuCase(true, false); }
    @Test public void hiddenMiddleStoneClosesNativeHorizontalGapAndHasNoBackground()
    { menuCase(false, false); }
    @Test public void hiddenSelectionGraphicDoesNotRemoveVisibleTabFromVerticalBar()
    { menuCase(true, true); }

    private void menuCase(boolean vertical, boolean selectionHidden)
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        Widget[] stones = new Widget[3];
        for (int index = 0; index < 3; index++)
        {
            stones[index] = WidgetGeometryTest.widget(600 + index * 33, 600, 33, 36);
            Widget icon = WidgetGeometryTest.widget(607 + index * 33, 605, 20, 20);
            when(client.getWidget(MenuStoneRegistry.MODERN[index][0])).thenReturn(stones[index]);
            when(client.getWidget(MenuStoneRegistry.MODERN[index][1])).thenReturn(icon);
        }
        when(stones[0].isHidden()).thenReturn(selectionHidden);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.menuLayout()).thenReturn(vertical ? MenuStoneController.Layout.VERTICAL_LEFT : MenuStoneController.Layout.VANILLA_HORIZONTAL);
        when(config.hideSkillsStone()).thenReturn(true); when(config.menuSpacing()).thenReturn(4);
        OrbController mode = mock(OrbController.class); when(mode.isLoggedIn()).thenReturn(true);
        MenuStoneBackgrounds backgrounds = mock(MenuStoneBackgrounds.class);
        MenuStoneController menu = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(OrbController.class).toProvider(() -> mode);
                bind(ConfigManager.class).toProvider(() -> mock(ConfigManager.class));
                bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                bind(MenuStoneBackgrounds.class).toProvider(() -> backgrounds);
            }
        }).getInstance(MenuStoneController.class);
        menu.apply();
        assertEquals(vertical ? new Rectangle(0, 0, 33, 36) : new Rectangle(600, 600, 33, 36), menu.stones().get(0));
        assertEquals(vertical ? new Rectangle(0, 40, 33, 36) : new Rectangle(637, 600, 33, 36), menu.stones().get(2));
        assertFalse(menu.stones().containsKey(1));
        verify(backgrounds).prepareRow(true, vertical);
        verify(backgrounds, never()).place(eq(1), any(Point.class), anyInt(), anyInt(), anyBoolean());
        verify(backgrounds).place(eq(0), any(Point.class), eq(33), eq(36), eq(vertical));
        verify(backgrounds).place(eq(2), any(Point.class), eq(33), eq(36), eq(vertical));
    }

    @Test public void freePositionDoesNotRunCompactRemapping() throws Exception
    {
        WidgetManager widgets = new WidgetManager();
        OrbController mode = mock(OrbController.class);
        when(mode.getCurrentLayout()).thenReturn(OrbLayout.FREE_POSITION);
        Field managerField = WidgetManager.class.getDeclaredField("manager");
        managerField.setAccessible(true); managerField.set(widgets, mode);
        // Without the guard this reaches the compact subsystem's slot manager.
        widgets.remapTargets(Orbs.HP_ORB_CONTAINER);
        verify(mode).getCurrentLayout();
    }

    @Test public void restoreAndReapplyCyclesKeepNativeMinimapAtRightAndMovedOrbAtSavedLocation()
    { restoreCycles(false); }
    @Test public void compactFreePositionHidesMinimapWithoutMovingSavedOrbAndRestoresVisibility()
    { restoreCycles(true); }
    private void restoreCycles(boolean compact)
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        Widget root = layoutWidget(0, 0, 1000, 700);
        Widget map = layoutWidget(790, 0, 210, 207);
        Widget minimap = layoutWidget(0, 0, 210, 180);
        Widget hp = layoutWidget(5, 10, 56, 36);
        when(map.getParent()).thenReturn(root); when(minimap.getParent()).thenReturn(map); when(hp.getParent()).thenReturn(map);
        when(map.getStaticChildren()).thenReturn(new Widget[]{minimap, hp});
        WidgetManager widgets = mock(WidgetManager.class); when(widgets.getTargetWidget(Orbs.HP_ORB_CONTAINER)).thenReturn(hp);
        when(widgets.getTargetWidget(Minimap.MODERN_MINIMAP)).thenReturn(minimap);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        when(config.hideMinimap()).thenReturn(compact);
        OrbController mode = mock(OrbController.class); when(mode.isLoggedIn()).thenReturn(true);
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration(eq("interfacelayout"), startsWith("free_modern_"), eq(Point.class)))
            .thenReturn(new Point(100, 200));
        WidgetStateStore states = new WidgetStateStore();
        com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(OrbController.class).toProvider(() -> mode);
                bind(ConfigManager.class).toProvider(() -> configs);
                bind(WidgetManager.class).toProvider(() -> widgets);
                bind(EditManager.class).toProvider(() -> mock(EditManager.class));
                bind(WidgetStateStore.class).toInstance(states);
            }
        });
        FreePositionController free = injector.getInstance(FreePositionController.class);
        WidgetBoundsExpander expander = injector.getInstance(WidgetBoundsExpander.class);
        for (int frame = 0; frame < 12; frame++)
        {
            states.restore(); expander.clear();
            assertEquals(new Point(790, 0), WidgetGeometry.location(minimap));
            assertFalse(minimap.isHidden());
            free.apply();
            assertEquals(new Point(100, 200), WidgetGeometry.location(hp));
            assertEquals(new Point(790, 0), WidgetGeometry.location(minimap));
            assertEquals(210, minimap.getWidth());
            assertEquals(compact, minimap.isHidden());
        }
    }

    /** Revalidation models native originals and forced positions; draw cache stays stale. */
    private static Widget layoutWidget(int x, int y, int width, int height)
    {
        Widget widget = mock(Widget.class);
        boolean[] hidden = {false};
        when(widget.isHidden()).thenAnswer(i -> hidden[0]); when(widget.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(widget).setHidden(anyBoolean());
        int[] original = {x, y, width, height}, relative = {x, y}, forced = {-1, -1};
        when(widget.getOriginalX()).thenAnswer(i -> original[0]); when(widget.getOriginalY()).thenAnswer(i -> original[1]);
        when(widget.getOriginalWidth()).thenAnswer(i -> original[2]); when(widget.getOriginalHeight()).thenAnswer(i -> original[3]);
        when(widget.getWidth()).thenAnswer(i -> original[2]); when(widget.getHeight()).thenAnswer(i -> original[3]);
        when(widget.getRelativeX()).thenAnswer(i -> relative[0]); when(widget.getRelativeY()).thenAnswer(i -> relative[1]);
        when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
        doAnswer(i -> { original[0] = i.getArgument(0); return null; }).when(widget).setOriginalX(anyInt());
        doAnswer(i -> { original[1] = i.getArgument(0); return null; }).when(widget).setOriginalY(anyInt());
        doAnswer(i -> { original[2] = i.getArgument(0); return null; }).when(widget).setOriginalWidth(anyInt());
        doAnswer(i -> { original[3] = i.getArgument(0); return null; }).when(widget).setOriginalHeight(anyInt());
        doAnswer(i -> { forced[0] = relative[0] = i.getArgument(0); forced[1] = relative[1] = i.getArgument(1); return null; })
            .when(widget).setForcedPosition(anyInt(), anyInt());
        doAnswer(i -> { relative[0] = forced[0] == -1 ? original[0] : forced[0]; relative[1] = forced[1] == -1 ? original[1] : forced[1]; return null; })
            .when(widget).revalidate();
        return widget;
    }
}
