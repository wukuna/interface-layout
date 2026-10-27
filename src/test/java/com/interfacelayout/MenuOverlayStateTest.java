package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuOverlayState;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MenuOverlayStateTest
{
    @Test public void switchingBackRestoresNativeOptionsAndKeepsOverlayEdits()
    {
        Fixture f = new Fixture();
        f.values.put("menuLayout", "FREE_POSITION"); f.values.put("menuSpacing", "3"); f.values.put("hideStone5", "true");
        f.state.sync(true);
        f.values.put("menuLayout", "VERTICAL_LEFT"); f.values.put("menuSpacing", "8"); f.values.remove("hideStone5");
        f.state.sync(false);
        assertEquals("FREE_POSITION", f.values.get("menuLayout")); assertEquals("3", f.values.get("menuSpacing"));
        assertEquals("true", f.values.get("hideStone5"));
        f.state.sync(true);
        assertEquals("VERTICAL_LEFT", f.values.get("menuLayout")); assertEquals("8", f.values.get("menuSpacing"));
        assertNull(f.values.get("hideStone5"));
        // Restart with overlays already enabled must not replace the native backup.
        f.state.sync(true); f.state.sync(false);
        assertEquals("FREE_POSITION", f.values.get("menuLayout"));
    }

    @Test public void positionsAndMembershipCopyOnceForEachModeWithoutChangingNativeKeys()
    {
        Fixture f = new Fixture();
        for (String mode : new String[]{"fixed", "classic", "modern"})
        {
            f.values.put("menuPosition_" + mode, "10:20");
            f.values.put("menuGroup_" + mode + "_4", "7");
            f.state.seedMode(mode);
            assertEquals("10:20", f.values.get("menuPosition_" + mode + "_overlay"));
            assertEquals("7", f.values.get("menuGroup_" + mode + "_overlay_4"));
            f.values.put("menuPosition_" + mode + "_overlay", "90:100");
            f.state.seedMode(mode);
            assertEquals("90:100", f.values.get("menuPosition_" + mode + "_overlay"));
            assertEquals("10:20", f.values.get("menuPosition_" + mode));
            assertEquals("7", f.values.get("menuGroup_" + mode + "_4"));
        }
    }

    @Test public void profileSwitchUsesItsOwnSnapshotAndUnsetOptionsReturnToDefaults()
    {
        Fixture f = new Fixture();
        f.state.sync(true); f.values.put("menuSpacing", "9"); f.state.sync(false);
        assertNull(f.values.get("menuSpacing"));
        Map<String, String> profileA = new HashMap<>(f.values);
        f.values.clear(); f.values.put("menuSpacing", "2");
        f.state.sync(true); f.values.put("menuSpacing", "5"); f.state.sync(false);
        assertEquals("2", f.values.get("menuSpacing"));
        f.values.clear(); f.values.putAll(profileA);
        f.state.sync(true); assertEquals("9", f.values.get("menuSpacing"));
        f.state.sync(false); assertNull(f.values.get("menuSpacing"));
    }

    private static final class Fixture
    {
        final Map<String, String> values = new HashMap<>();
        final MenuOverlayState state;
        Fixture()
        {
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(eq("interfacelayout"), anyString())).thenAnswer(inv -> values.get(inv.getArgument(1)));
            doAnswer(inv -> { values.put(inv.getArgument(1), String.valueOf((Object) inv.getArgument(2))); return null; })
                .when(configs).setConfiguration(eq("interfacelayout"), anyString(), any());
            doAnswer(inv -> { values.remove(inv.getArgument(1)); return null; }).when(configs).unsetConfiguration(eq("interfacelayout"), anyString());
            state = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() { bind(ConfigManager.class).toInstance(configs); }
            }).getInstance(MenuOverlayState.class);
        }
    }
}
