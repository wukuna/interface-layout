package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbConstants.ConfigKeys;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.NativeOrbStateStore;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WorldMapVisibilityTest
{
    @Test public void eyeButtonIsRecreatedWhenGameReplacesChildrenOnSameParent() throws Exception
    {
        OrbController controller = mock(OrbController.class, CALLS_REAL_METHODS);
        WidgetManager widgets = mock(WidgetManager.class);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        Client client = mock(Client.class);
        Widget parent = mock(Widget.class), old = mock(Widget.class), replacement = mock(Widget.class);
        when(widgets.getOrbsParent()).thenReturn(parent);
        when(widgets.createMinimapButton(parent)).thenReturn(old, replacement);
        when(old.getParent()).thenReturn(parent);
        when(widgets.isAttachedChild(old)).thenReturn(true, false);
        inject(controller, OrbController.class, "widgetManager", widgets);
        inject(controller, OrbController.class, "config", config);
        inject(controller, OrbController.class, "client", client);
        com.interfacelayout.layout.orbs.widget.layout.edit.EditManager editor =
            mock(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class);
        inject(controller, OrbController.class, "editManager", editor);
        doReturn(false).when(controller).isMinimapMinimized();
        controller.createCustomChildren(); assertSame(old, controller.minimapButton);
        controller.createCustomChildren(); assertSame(old, controller.minimapButton);
        controller.createCustomChildren(); assertSame(replacement, controller.minimapButton);
        verify(widgets).clearChild(old);
        verify(replacement).setHidden(false);
        org.mockito.ArgumentCaptor<Object[]> op = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(replacement).setOnOpListener(op.capture());
        net.runelite.api.widgets.JavaScriptCallback callback = (net.runelite.api.widgets.JavaScriptCallback) op.getValue()[0];
        net.runelite.api.ScriptEvent click = mock(net.runelite.api.ScriptEvent.class);
        when(click.getOp()).thenReturn(com.interfacelayout.layout.orbs.OrbConstants.MenuOp.EDIT_MODE_OP_INDEX + 1);
        controller.isEditingLayout = false; callback.run(click); verify(editor).toggleEditMode(true);
        controller.isEditingLayout = true; callback.run(click); verify(editor).toggleEditMode(false);
    }
    private void inject(Object target, Class<?> type, String key, Object value) throws Exception
    {
        Field field = type.getDeclaredField(key); field.setAccessible(true); field.set(target, value);
    }
    @Test public void freePositionHidesAndRestoresWorldMapWithoutRemappingItsSavedPosition() throws Exception
    {
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        OrbController controller = mock(OrbController.class, CALLS_REAL_METHODS);
        WidgetManager widgets = spy(new WidgetManager()); Client client = mock(Client.class);
        Widget map = mock(Widget.class), tooltip = mock(Widget.class);
        boolean[] hidden = {false};
        when(map.isSelfHidden()).thenAnswer(call -> hidden[0]);
        doAnswer(call -> { hidden[0] = call.getArgument(0); return null; }).when(map).setHidden(anyBoolean());
        doReturn(map).when(widgets).getTargetWidget(Orbs.WORLD_MAP_CONTAINER);
        doReturn(tooltip).when(widgets).getTargetWidget(Orbs.WORLD_MAP_TOOLTIP);
        inject(widgets, WidgetManager.class, "nativeStates", new NativeOrbStateStore());
        inject(widgets, WidgetManager.class, "client", client);
        inject(controller, OrbController.class, "config", config);
        inject(controller, OrbController.class, "widgetManager", widgets);
        for (int i = 0; i < 3; i++)
        {
            when(config.hideWorld()).thenReturn(true); controller.hideOrbByConfig(ConfigKeys.HIDE_WORLD);
            assertTrue(hidden[0]);
            when(config.hideWorld()).thenReturn(false); controller.hideOrbByConfig(ConfigKeys.HIDE_WORLD);
            assertFalse(hidden[0]);
        }
        verify(map, never()).setForcedPosition(anyInt(), anyInt());
        verify(map, never()).setOriginalX(anyInt());
    }
}
