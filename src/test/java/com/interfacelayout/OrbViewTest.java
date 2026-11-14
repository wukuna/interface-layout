package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import java.lang.reflect.Field;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrbViewTest
{
    @Test public void perOrbChoicesLoadThroughRuneLiteConfigProxy()
    {
        for(InterfaceLayoutConfig.OrbTimerStyle style:InterfaceLayoutConfig.OrbTimerStyle.values()) {
            ConfigManager configs=mock(ConfigManager.class);
            for(String key:new String[]{"hitpointsTimerStyle","prayerTimerStyle","specialTimerStyle"})
                when(configs.getConfiguration("interfacelayout",key)).thenReturn(style.name());
            InterfaceLayoutConfig config=RealConfigProxy.create(InterfaceLayoutConfig.class,configs);
            assertEquals(style,config.hitpointsTimerStyle());assertEquals(style,config.prayerTimerStyle());assertEquals(style,config.specialTimerStyle());
        }
    }
    @Test public void betaTimerChoicesLoadThroughRuneLiteConfigProxy()
    {
        for(InterfaceLayoutConfig.NativeTimerStyle style:InterfaceLayoutConfig.NativeTimerStyle.values())
            for(InterfaceLayoutConfig.PrayerDisplay display:InterfaceLayoutConfig.PrayerDisplay.values()) {
                ConfigManager configs=mock(ConfigManager.class);
                when(configs.getConfiguration("interfacelayout","nativeOrbTimerStyle")).thenReturn(style.name());
                when(configs.getConfiguration("interfacelayout","prayerOrbDisplay")).thenReturn(display.name());
                InterfaceLayoutConfig config=RealConfigProxy.create(InterfaceLayoutConfig.class,configs);
                assertEquals(style,config.nativeOrbTimerStyle());assertEquals(display,config.prayerOrbDisplay());
            }
    }
    private OrbController controller(OrbLayout layout, boolean compact) throws Exception
    {
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration("interfacelayout", "orbLayout")).thenReturn(layout.name());
        when(configs.getConfiguration("interfacelayout", "hideMinimap")).thenReturn(Boolean.toString(compact));
        InterfaceLayoutConfig config = RealConfigProxy.create(InterfaceLayoutConfig.class, configs);
        OrbController controller = mock(OrbController.class, CALLS_REAL_METHODS);
        doReturn(false).when(controller).isFixedMode();
        doReturn(false).when(controller).isMinimapMinimized();
        Field field = OrbController.class.getDeclaredField("config");
        field.setAccessible(true); field.set(controller, config);
        return controller;
    }

    @Test public void rimColorLoadsThroughRuneLiteConfigProxy() {
        for (InterfaceLayoutConfig.OrbRim style : InterfaceLayoutConfig.OrbRim.values()) {
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration("interfacelayout", "orbRim")).thenReturn(style.name());
            assertEquals(style, RealConfigProxy.create(InterfaceLayoutConfig.class, configs).orbRim());
        }
        assertEquals(InterfaceLayoutConfig.OrbRim.SILVER, new InterfaceLayoutConfig() {}.orbRim());
    }

    @Test public void compactToggleControlsEveryRetainedPreset() throws Exception
    {
        for (OrbLayout layout : new OrbLayout[]{OrbLayout.VERTICAL, OrbLayout.HORIZONTAL, OrbLayout.HORIZONTAL_WIDE, OrbLayout.CUSTOM})
        {
            assertTrue(layout.name(), controller(layout, true).isCompactLayout());
            assertTrue(layout.name(), controller(layout, true).isMinimapHidden());
            assertFalse(layout.name(), controller(layout, false).isCompactLayout());
            assertFalse(layout.name(), controller(layout, false).isMinimapHidden());
        }
    }
    @Test public void freePositionHonorsCompactVisibilityWithoutPresetReflow() throws Exception
    {
        for (boolean compact : new boolean[]{false, true})
        {
            OrbController controller = controller(OrbLayout.FREE_POSITION, compact);
            assertFalse(controller.isCompactLayout());
            assertEquals(compact, controller.isMinimapHidden());
        }
    }
    @Test public void compactToggleUsesExistingMinimapKeyBehindSidePanel() throws Exception
    {
        ConfigItem item = InterfaceLayoutConfig.class.getMethod("hideMinimap").getAnnotation(ConfigItem.class);
        assertTrue(item.hidden());
        assertEquals("Hide minimap", item.name());
        assertEquals("hideAndSwapUpdate", item.section());
        assertEquals("hideMinimap", item.keyName());
    }
}
