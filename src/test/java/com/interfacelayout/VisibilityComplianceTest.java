package com.interfacelayout;

import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.orbs.OrbConstants.Script;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.NativeOrbStateStore;
import com.interfacelayout.util.WidgetStateStore;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import java.lang.reflect.Field;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VisibilityComplianceTest
{
    @Test public void detachedMinimapRespectsMissingAndGameHiddenMaskButAllowsPluginHiding() throws Exception
    {
        boolean[] hidden = {false}; Widget mask = mock(Widget.class);
        when(mask.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(mask).setHidden(anyBoolean());
        NativeOrbStateStore compact = new NativeOrbStateStore(); WidgetStateStore free = new WidgetStateStore();
        GameframeCoordinator coordinator = new GameframeCoordinator();
        set(coordinator, "nativeStates", compact); set(coordinator, "states", free);
        WidgetManager widgets = mock(WidgetManager.class);
        OrbController manager = mock(OrbController.class, CALLS_REAL_METHODS);
        doReturn(true).when(manager).isMinimapOverlayEnabled(); doReturn(true).when(manager).isMinimapHidden();
        doReturn(false).when(manager).isMinimapMinimized(); doReturn(false).when(manager).isFixedMode();
        doReturn(true).when(manager).isLoggedIn(); set(manager, "gameframe", coordinator); set(manager, "widgetManager", widgets);
        assertTrue(manager.hideMinimapOverlay());
        when(widgets.getMinimapMask()).thenReturn(mask); assertFalse(manager.hideMinimapOverlay());
        free.visibility(mask, true); assertFalse(manager.hideMinimapOverlay());
        free.restoreVisibility(); hidden[0] = true; assertTrue(manager.hideMinimapOverlay());
    }
    @Test public void nativeSpecialAttackScriptCanHideComponentAndShutdownKeepsItHidden() throws Exception
    {
        boolean[] hidden = {false};
        Widget special = mock(Widget.class);
        when(special.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(special).setHidden(anyBoolean());
        NativeOrbStateStore journal = new NativeOrbStateStore(); journal.visibility(special, true);
        OrbController manager = mock(OrbController.class);
        doAnswer(i -> { journal.visibility(special, false); return null; }).when(manager).refreshVisibility();
        GameframeCoordinator coordinator = new GameframeCoordinator();
        set(coordinator, "running", true); set(coordinator, "nativeStates", journal); set(coordinator, "manager", manager);
        ScriptPreFired pre = new ScriptPreFired(Script.ORBS_UPDATE_SPECENERGY);
        coordinator.onScriptPreFired(pre); assertFalse(hidden[0]);
        hidden[0] = true; // The native script removes the special-attack control.
        ScriptPostFired post = new ScriptPostFired(Script.ORBS_UPDATE_SPECENERGY);
        coordinator.onScriptPostFired(post); assertTrue(hidden[0]);
        journal.restore(); assertTrue(hidden[0]);
    }
    private static void set(Object target, String name, Object value) throws Exception
    {
        Class<?> type = target instanceof OrbController ? OrbController.class : target.getClass();
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
