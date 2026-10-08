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
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
}
