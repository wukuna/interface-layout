package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbRegistry;
import com.interfacelayout.layout.orbs.widget.layout.edit.BindingManager;
import com.interfacelayout.layout.orbs.widget.layout.edit.DragState;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.NativeOrbStateStore;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EditingTransitionsTest
{
    static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    private static class Fixture
    {
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final OrbController manager = mock(OrbController.class);
        final WidgetManager widgets = mock(WidgetManager.class);
        final BindingManager bindings = new BindingManager();
        final EditManager editor = new EditManager();
        final Widget parent = mock(Widget.class), mask = mock(Widget.class);
        Fixture() throws Exception
        {
            when(manager.isLoggedIn()).thenReturn(true);
            when(widgets.getMapParent()).thenReturn(parent);
            when(widgets.getMinimapMask()).thenReturn(mask);
            when(parent.getNoClickThrough()).thenReturn(true);
            inject(editor, "config", config); inject(editor, "manager", manager);
            inject(editor, "widgetManager", widgets); inject(editor, "bindingManager", bindings);
            inject(editor, "client", mock(Client.class)); inject(editor, "dragState", new DragState());
            inject(editor, "hideConfig", new HideOrbRegistry());
            inject(editor, "nativeStates", new NativeOrbStateStore());
        }
    }
    @Test public void switchingToFreePositionClosesPresetSessionEvenWithoutHandlers() throws Exception
    {
        Fixture f = new Fixture();
        when(f.config.layout()).thenReturn(OrbLayout.VERTICAL);
        f.editor.toggleEditMode(true);
        assertTrue(f.manager.isEditingLayout);
        when(f.config.editInterface()).thenReturn(true);
        when(f.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        f.editor.toggleEditMode(false);
        assertFalse(f.manager.isEditingLayout);
        verify(f.widgets).restoreMinimapRendering(f.mask);
        verify(f.parent, times(2)).setNoClickThrough(true);
        verify(f.manager).saveConfig("editInterface", false);
        assertTrue(f.bindings.all().isEmpty());
    }
    @Test public void closingDoesNotOverwriteNewVisibilityWithStaleBindings() throws Exception
    {
        Fixture f = new Fixture();
        when(f.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        f.manager.isEditingLayout = true;
        f.bindings.bind(mock(Widget.class), Orbs.HP_ORB_CONTAINER, null, null, null, true);
        f.editor.toggleEditMode(false);
        assertTrue(f.bindings.all().isEmpty());
        verify(f.manager, never()).saveConfig("hideHp", true);
        f.editor.toggleEditMode(true); f.editor.toggleEditMode(false);
        assertFalse(f.manager.isEditingLayout);
    }
    @Test public void repeatedDisableSynchronizesCheckboxWithoutRebuilding() throws Exception
    {
        Fixture f = new Fixture();
        when(f.config.editInterface()).thenReturn(true);
        f.editor.toggleEditMode(false);
        verify(f.manager).saveConfig("editInterface", false);
        verify(f.manager, never()).rebuildLayout();
    }
    @Test public void editingWhileLoggedOutCannotLeaveCheckboxEnabled() throws Exception
    {
        Fixture f = new Fixture();
        when(f.manager.isLoggedIn()).thenReturn(false);
        when(f.config.editInterface()).thenReturn(true);
        f.editor.toggleEditMode(true);
        assertFalse(f.manager.isEditingLayout);
        verify(f.manager).saveConfig("editInterface", false);
        verify(f.widgets, never()).removeMinimapRendering();
    }
}
