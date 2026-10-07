package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuStoneBackgrounds;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuStoneBackgroundsTest
{
    @Test public void nativeCompactionSplitsArtWithoutResizingItsClippingLayer()
    {
        Client client = mock(Client.class);
        Widget background = WidgetGeometryTest.widget(800, 500, 231, 36);
        Widget tile = mock(Widget.class);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND)).thenReturn(background);
        when(background.createChild(-1, WidgetType.GRAPHIC)).thenReturn(tile);
        WidgetBoundsExpander expander = mock(WidgetBoundsExpander.class);
        MenuStoneBackgrounds backgrounds = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
                bind(WidgetBoundsExpander.class).toInstance(expander);
            }
        }).getInstance(MenuStoneBackgrounds.class);
        backgrounds.place(1, new Point(833,500), 33,36,false);
        verifyNoInteractions(expander);
        verify(tile).setOriginalX(33); verify(tile).setOriginalY(0);
        verify(background, never()).setHidden(anyBoolean());
    }

    @Test public void emptyRowRemovesSharedBackgroundAndRestoresItsRendering()
    {
        Client client = mock(Client.class);
        Widget background = WidgetGeometryTest.widget(0, 0, 231, 36);
        when(background.getType()).thenReturn(WidgetType.GRAPHIC);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND)).thenReturn(background);
        WidgetStateStore states = new WidgetStateStore();
        MenuStoneBackgrounds backgrounds = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(WidgetStateStore.class).toInstance(states);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuStoneBackgrounds.class);
        backgrounds.prepareRow(true);
        verify(background).setType(WidgetType.LAYER);
        verify(background, never()).createChild(anyInt(), anyInt());
        backgrounds.restore(); states.restore();
        verify(background).setType(WidgetType.GRAPHIC);
    }
    @Test public void nativeTilesFollowDestinationAndRestoreWithoutDeletingForeignChildren()
    {
        Client client = mock(Client.class);
        Widget background = WidgetGeometryTest.widget(0, 0, 231, 36);
        Widget foreign = mock(Widget.class), tile = mock(Widget.class);
        when(background.getType()).thenReturn(WidgetType.GRAPHIC);
        when(background.getSpriteId()).thenReturn(1180);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND)).thenReturn(background);
        when(background.createChild(-1, WidgetType.GRAPHIC)).thenReturn(tile);
        when(tile.getIndex()).thenReturn(1);
        when(background.getChildren()).thenReturn(new Widget[]{foreign, tile});
        WidgetStateStore states = new WidgetStateStore();
        MenuStoneBackgrounds backgrounds = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(WidgetStateStore.class).toInstance(states);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuStoneBackgrounds.class);
        backgrounds.place(0, new Point(400, 200), 33, 36);
        verify(background).setType(WidgetType.LAYER);
        verify(tile).setSpriteId(1180); verify(tile).setSpriteTiling(true);
        verify(tile).setOriginalWidth(33); verify(tile).setOriginalHeight(36);
        verify(tile).setOriginalX(400); verify(tile).setOriginalY(200);
        verify(tile).setNoClickThrough(false);
        backgrounds.restore();
        org.mockito.ArgumentCaptor<Widget[]> children = org.mockito.ArgumentCaptor.forClass(Widget[].class);
        verify(background).setChildren(children.capture());
        assertArrayEquals(new Widget[]{foreign}, children.getValue());
        states.restore();
        verify(background).setType(WidgetType.GRAPHIC);
        verify(background, never()).deleteAllChildren();
    }
}
