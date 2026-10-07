package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import java.lang.reflect.Field;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SettingsPanelTest
{
    @Test public void menuVisibilityAndSavedPositionsDoNotTurnEditingOff() throws Exception
    {
        OrbEventHandler handler = new OrbEventHandler();
        OrbController manager = mock(OrbController.class); manager.isEditingLayout = true;
        ClientThread thread = mock(ClientThread.class); EditManager editor = mock(EditManager.class);
        inject(handler, "manager", manager); inject(handler, "clientThread", thread); inject(handler, "editManager", editor);
        for (String key : new String[]{"hideStone0", "hideStone13", "hideTopMenu", "hideBottomMenu", "menuLayout",
            "menuDirection", "menuSpacing", "menuNativePosition_modern_0", "menuGroup_modern_0", "menuPosition_modern"})
        {
            ConfigChanged event = new ConfigChanged(); event.setGroup("interfacelayout"); event.setKey(key);
            handler.onConfigChanged(event);
            assertTrue(key, manager.isEditingLayout);
        }
        verifyNoInteractions(thread, editor);
    }
    @Test public void orbVisibilityCheckboxesAreVisibleInOneSection() throws Exception
    {
        for (String method : new String[]{"hideCompass", "hideHp", "hidePray", "hideRun", "hideSpec", "hideXp", "hideWorld",
            "hideStore", "hideActivity", "hideWiki", "hideLogout"})
        {
            ConfigItem item = InterfaceLayoutConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
            assertFalse(method, item.hidden()); assertEquals("hideAndSwapUpdate", item.section());
        }
    }
    @Test public void editingControlIsAtTopAndOldMovedToEditModeNoticeIsGone() throws Exception
    {
        ConfigItem edit = InterfaceLayoutConfig.class.getMethod("editInterface").getAnnotation(ConfigItem.class);
        assertEquals("", edit.section()); assertEquals(0, edit.position());
        assertTrue(edit.description().contains("Shift-drag"));
        // Void config methods render as clickable buttons, not explanatory labels.
        for (java.lang.reflect.Method method : InterfaceLayoutConfig.class.getDeclaredMethods())
            assertNotEquals(method.getName(), void.class, method.getReturnType());
    }
    private static void inject(Object target, String field, Object value) throws Exception
    {
        Field member = target.getClass().getDeclaredField(field); member.setAccessible(true); member.set(target, value);
    }
}
