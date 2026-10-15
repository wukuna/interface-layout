package com.interfacelayout;

import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.edit.InterfaceEditor;
import java.lang.reflect.Field;
import net.runelite.api.events.FocusChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class EditorDragCancellationTest
{
    @Test public void losingFocusCancelsDragButReturningFocusDoesNotResumeIt() throws Exception
    {
        GameframeCoordinator coordinator = new GameframeCoordinator();
        InterfaceEditor editor = mock(InterfaceEditor.class);
        set(coordinator, "editor", editor); set(coordinator, "running", true);
        FocusChanged event = new FocusChanged(); event.setFocused(false);
        coordinator.onFocusChanged(event);
        event.setFocused(true); coordinator.onFocusChanged(event);
        verify(editor, times(1)).cancel();
    }

    @Test public void editAndLayoutChangesCancelDragBeforeQueuedRefresh() throws Exception
    {
        for (String key : new String[]{"editInterface", "menuLayout", "orbLayout"})
        {
            GameframeCoordinator coordinator = new GameframeCoordinator();
            InterfaceEditor editor = mock(InterfaceEditor.class);
            set(coordinator, "editor", editor);
            set(coordinator, "clientThread", mock(ClientThread.class));
            ConfigChanged event = new ConfigChanged();
            event.setGroup("interfacelayout"); event.setKey(key);
            coordinator.onConfigChanged(event);
            verify(editor).cancel();
        }
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = GameframeCoordinator.class.getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    @Test public void movableWindowGeometryRefreshesOnlyWhenChangedAndActive() throws Exception
    {
        GameframeCoordinator coordinator = spy(new GameframeCoordinator());
        net.runelite.api.Client client = mock(net.runelite.api.Client.class);
        com.interfacelayout.layout.menu.MenuStoneController menu = mock(com.interfacelayout.layout.menu.MenuStoneController.class);
        set(coordinator,"client",client); set(coordinator,"menu",menu); set(coordinator,"running",true);
        doNothing().when(coordinator).refresh();
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(menu.windowBoundsChanged()).thenReturn(true);
        coordinator.onBeforeRender(new net.runelite.api.events.BeforeRender());
        verify(coordinator).refresh();
        when(menu.windowBoundsChanged()).thenReturn(false);
        coordinator.onBeforeRender(new net.runelite.api.events.BeforeRender());
        verify(coordinator,times(1)).refresh();
        when(menu.windowBoundsChanged()).thenReturn(true);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGIN_SCREEN);
        coordinator.onBeforeRender(new net.runelite.api.events.BeforeRender());
        set(coordinator,"running",false);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        coordinator.onBeforeRender(new net.runelite.api.events.BeforeRender());
        verify(coordinator,times(1)).refresh();
    }
}
