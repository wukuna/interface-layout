package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InterfaceEditorTest
{
    @Test public void ordinaryDragMovesEntireGroup() throws Exception { drag(false); }
    @Test public void shiftDragDetachesAndDropsIndividualStone() throws Exception { drag(true); }
    @Test public void altShiftDragDetachesStoneWithoutEnablingEditor() throws Exception { drag(true, true); }
    private void drag(boolean shift) throws Exception
    { drag(shift, false); }
    private void drag(boolean shift, boolean alt) throws Exception
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.individualDragging()).thenReturn(true);
        when(menu.groups()).thenReturn(Map.of(0, new Rectangle(0, 0, 33, 76)));
        when(menu.stones()).thenReturn(Map.of(0, new Rectangle(0, 0, 33, 36)));
        OrbController manager = mock(OrbController.class); manager.isEditingLayout = !alt;
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        ClientThread thread = mock(ClientThread.class);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
        FreePositionController orbs = mock(FreePositionController.class); when(orbs.bounds()).thenReturn(Map.of());
        InterfaceEditor editor = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(com.interfacelayout.layout.menu.MenuOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.menu.MenuOverlayBridge.class));
                bind(OrbController.class).toProvider(() -> manager);
                bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(FreePositionController.class).toProvider(() -> orbs);
                bind(ClientThread.class).toProvider(() -> thread);
            }
        }).getInstance(InterfaceEditor.class);
        java.awt.Graphics2D graphics = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { editor.render(graphics); } finally { graphics.dispose(); }
        Canvas canvas = new Canvas(); int modifiers = (shift ? InputEvent.SHIFT_DOWN_MASK : 0) | (alt ? InputEvent.ALT_DOWN_MASK : 0);
        java.lang.reflect.Constructor<net.runelite.client.input.MouseManager> constructor =
            net.runelite.client.input.MouseManager.class.getDeclaredConstructor(net.runelite.client.config.RuneLiteConfig.class);
        constructor.setAccessible(true);
        net.runelite.client.input.MouseManager inputs = constructor.newInstance(mock(net.runelite.client.config.RuneLiteConfig.class));
        net.runelite.client.input.MouseListener competingListener = mock(net.runelite.client.input.MouseListener.class);
        inputs.registerMouseListener(competingListener);
        inputs.registerMouseListener(0, editor);
        MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, modifiers, 10, 10, 1, false, MouseEvent.BUTTON1);
        inputs.processMousePressed(press); assertTrue(press.isConsumed());
        inputs.processMouseDragged(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 1, modifiers, 100, 110, 0, false, MouseEvent.NOBUTTON));
        inputs.processMouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, modifiers, 100, 110, 1, false, MouseEvent.BUTTON1));
        verifyNoInteractions(competingListener);
        if (shift) {
            verify(menu).detach(0, new Point(90, 100));
            verify(menu).dropStone(0, new Point(100, 110));
            verify(menu, never()).saveGroup(anyInt(), any(Point.class));
        } else {
            verify(menu).saveGroup(0, new Point(90, 100));
            verify(menu, never()).detach(anyInt(), any(Point.class));
        }
    }
}
