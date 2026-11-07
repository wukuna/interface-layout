package com.interfacelayout;

import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.MinimapClickProtection;
import com.interfacelayout.layout.orbs.OrbConstants.Script;
import com.interfacelayout.util.NativeOrbStateStore;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import java.lang.reflect.Field;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LayeredVisibilityRestoreTest
{
    @Test public void nativeRedrawSeesOriginalLogoutVisibilityAfterBothLayersHideIt() throws Exception
    {
        Fixture f = new Fixture(false);
        f.nativeStates.visibility(f.logout, true);
        f.layoutStates.visibility(f.logout, true);
        assertTrue(f.logout.isSelfHidden());
        f.coordinator.onScriptPreFired(new ScriptPreFired(Script.TOPLEVEL_REDRAW));
        assertFalse(f.logout.isSelfHidden());
        f.nativeStates.restore();
        f.layoutStates.restore();
        assertFalse(f.logout.isSelfHidden());
    }

    @Test public void redrawPreservesLogoutVisibilityHiddenByTheGame() throws Exception
    {
        Fixture f = new Fixture(true);
        f.nativeStates.visibility(f.logout, true);
        f.layoutStates.visibility(f.logout, true);
        f.coordinator.onScriptPreFired(new ScriptPreFired(Script.TOPLEVEL_REDRAW));
        assertTrue(f.logout.isSelfHidden());
    }

    @Test public void nestedLayoutScriptKeepsNativeVisibilitySetByTheOuterScript() throws Exception
    {
        Fixture f = new Fixture(false);
        f.nativeStates.visibility(f.logout, true);
        f.layoutStates.visibility(f.logout, true);
        f.coordinator.onScriptPreFired(new ScriptPreFired(Script.TOPLEVEL_REDRAW));
        assertFalse(f.logout.isSelfHidden());
        f.logout.setHidden(true);
        f.coordinator.onScriptPreFired(new ScriptPreFired(Script.TOPLEVEL_SUBCHANGE));
        assertTrue(f.logout.isSelfHidden());
    }

    private static final class Fixture
    {
        final Widget logout = mock(Widget.class);
        final NativeOrbStateStore nativeStates = new NativeOrbStateStore();
        final WidgetStateStore layoutStates = new WidgetStateStore();
        final GameframeCoordinator coordinator = new GameframeCoordinator();
        Fixture(boolean initiallyHidden) throws Exception
        {
            boolean[] hidden = {initiallyHidden};
            when(logout.isSelfHidden()).thenAnswer(i -> hidden[0]);
            doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(logout).setHidden(anyBoolean());
            set(coordinator, "states", layoutStates);
            set(coordinator, "nativeStates", nativeStates);
            set(coordinator, "minimapClicks", mock(MinimapClickProtection.class));
            set(coordinator, "menu", mock(MenuStoneController.class));
            set(coordinator, "expander", mock(WidgetBoundsExpander.class));
            set(coordinator, "running", true);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
