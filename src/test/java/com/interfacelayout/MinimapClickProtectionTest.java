package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.orbs.MinimapClickProtection;
import com.interfacelayout.layout.orbs.OrbConstants;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import org.mockito.Answers;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinimapClickProtectionTest
{
    private static class Fixture
    {
        final Client client = mock(Client.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class);
        final OrbController manager = mock(OrbController.class);
        final WidgetManager widgets = new WidgetManager();
        final MinimapClickProtection guard;
        Fixture()
        {
            guard = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client);
                    bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> manager);
                    bind(WidgetManager.class).toInstance(widgets);
                    bind(com.interfacelayout.layout.orbs.widget.layout.slot.SlotManager.class).toProvider(
                        () -> mock(com.interfacelayout.layout.orbs.widget.layout.slot.SlotManager.class));
                    bind(com.interfacelayout.layout.orbs.widget.layout.slot.SlotRegistry.class).toProvider(
                        () -> mock(com.interfacelayout.layout.orbs.widget.layout.slot.SlotRegistry.class));
                }
            }).getInstance(MinimapClickProtection.class);
            when(config.enableNoClickthrough()).thenReturn(true);
        }
        Widget map(int id, boolean initial)
        {
            Widget widget = mock(Widget.class, Answers.RETURNS_SELF);
            when(widget.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT);
            boolean[] blocked = {initial};
            when(widget.getNoClickThrough()).thenAnswer(i -> blocked[0]);
            doAnswer(i -> { blocked[0] = i.getArgument(0); return widget; }).when(widget).setNoClickThrough(anyBoolean());
            when(client.getWidget(id)).thenReturn(widget);
            return widget;
        }
    }

    @Test public void allDisplayModesProtectMapWithoutChangingItsNavigationOrGeometry()
    {
        int[] ids = {OrbConstants.Widgets.Fixed.MINIMAP_MASK, OrbConstants.Widgets.Classic.MINIMAP_MASK,
            OrbConstants.Widgets.Modern.MINIMAP_MASK};
        for (int mode = 0; mode < ids.length; mode++)
        {
            Fixture f = new Fixture();
            when(f.manager.isFixedMode()).thenReturn(mode == 0);
            when(f.manager.isClassicResizable()).thenReturn(mode == 1);
            Widget map = f.map(ids[mode], false);
            f.guard.apply(); f.guard.apply();
            assertTrue(map.getNoClickThrough());
            assertEquals(1338, map.getContentType());
            verify(map, never()).setContentType(anyInt());
            verify(map, never()).setHidden(anyBoolean());
            verify(map, never()).setOriginalWidth(anyInt());
            verify(map, never()).setOriginalHeight(anyInt());
            f.guard.restore();
            assertFalse(map.getNoClickThrough());
        }
    }

    @Test public void disablingAndReenablingRestoresOnlyTheOriginalFlag()
    {
        for (boolean initial : new boolean[]{false, true})
        {
            Fixture f = new Fixture();
            Widget map = f.map(OrbConstants.Widgets.Modern.MINIMAP_MASK, initial);
            f.guard.apply(); f.guard.restore();
            when(f.config.enableNoClickthrough()).thenReturn(false);
            f.guard.apply(); assertEquals(initial, map.getNoClickThrough());
            when(f.config.enableNoClickthrough()).thenReturn(true);
            f.guard.apply(); assertTrue(map.getNoClickThrough());
            f.guard.restore(); f.guard.restore();
            assertEquals(initial, map.getNoClickThrough());
        }
    }

    @Test public void hiddenOrNonMapWidgetsAreNeverMadeInteractive()
    {
        Fixture f = new Fixture();
        f.guard.apply(); // No map while logged out or loading.
        Widget map = f.map(OrbConstants.Widgets.Modern.MINIMAP_MASK, false);
        when(map.isHidden()).thenReturn(true);
        f.guard.apply(); assertFalse(map.getNoClickThrough());
        when(map.isHidden()).thenReturn(false);
        when(map.getContentType()).thenReturn(0);
        f.guard.apply(); assertFalse(map.getNoClickThrough());
        verify(map, never()).setHidden(anyBoolean());
    }

    @Test public void replacedWidgetsRetainTheirOwnOriginalFlags()
    {
        Fixture f = new Fixture();
        Widget old = f.map(OrbConstants.Widgets.Modern.MINIMAP_MASK, false);
        f.guard.apply();
        Widget replacement = f.map(OrbConstants.Widgets.Modern.MINIMAP_MASK, true);
        f.guard.restore(); f.guard.apply(); f.guard.restore();
        assertFalse(old.getNoClickThrough()); assertTrue(replacement.getNoClickThrough());
    }

    @Test public void detachedMinimapUsesSameSettingAndKeepsNativeContent()
    {
        Fixture f = new Fixture();
        for (boolean enabled : new boolean[]{true, false})
        {
            when(f.config.enableNoClickthrough()).thenReturn(enabled);
            Widget parent = mock(Widget.class), map = mock(Widget.class, Answers.RETURNS_SELF);
            when(parent.createChild(anyInt(), anyInt())).thenReturn(map);
            assertSame(map, f.widgets.createOverlayMinimap(parent));
            verify(map).setNoClickThrough(enabled);
            verify(map).setContentType(1338);
        }
    }
}
