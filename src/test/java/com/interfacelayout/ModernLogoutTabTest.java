package com.interfacelayout;

import com.google.inject.*;
import com.interfacelayout.layout.menu.*;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.*;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ModernLogoutTabTest
{
    @Test public void nativeRowsLeaveBareModernXAtMinimap()
    {
        for (MenuStoneController.Layout layout : new MenuStoneController.Layout[]{MenuStoneController.Layout.VANILLA_HORIZONTAL,MenuStoneController.Layout.HIDDEN})
        {
            Fixture f=new Fixture(2,layout,false);
            when(f.config.hideLogoutStone()).thenReturn(true);
            when(f.config.hideBottomMenu()).thenReturn(true);
            f.menu.apply();
            assertFalse(f.menu.stones().containsKey(10));
            verify(f.stones[10],never()).setForcedPosition(anyInt(),anyInt());
            verify(f.icons[10],never()).setForcedPosition(anyInt(),anyInt());
            verify(f.stones[10],never()).setHidden(anyBoolean());
            verify(f.icons[10],never()).setHidden(anyBoolean());
            verify(f.backgrounds,never()).place(eq(10),any(),anyInt(),anyInt(),anyBoolean());
        }
    }
    @Test public void bareModernXParticipatesInCustomMenuGroupsWithoutBackground()
    {
        for(MenuStoneController.Layout layout : new MenuStoneController.Layout[]{MenuStoneController.Layout.VERTICAL_LEFT,
            MenuStoneController.Layout.VERTICAL_RIGHT,MenuStoneController.Layout.FREE_POSITION})
        {
            Fixture f=new Fixture(2,layout,false); f.menu.apply();
            Rectangle slot=f.menu.stones().get(10); assertNotNull(slot); assertEquals(21,slot.width);
            verify(f.stones[10]).setForcedPosition(slot.x,slot.y);
            verify(f.icons[10]).setForcedPosition(slot.x,slot.y);
            verify(f.backgrounds,never()).place(eq(10),any(),anyInt(),anyInt(),anyBoolean());
        }
    }
    @Test public void restoredModernXIsCenteredWithoutResizingAndReturnsOnDisable()
    {
        for(MenuStoneController.Layout layout : new MenuStoneController.Layout[]{MenuStoneController.Layout.VANILLA_HORIZONTAL,
            MenuStoneController.Layout.VERTICAL_LEFT,MenuStoneController.Layout.VERTICAL_RIGHT,MenuStoneController.Layout.FREE_POSITION})
        {
            Fixture f=new Fixture(2,layout,true); f.menu.apply();
            Rectangle slot=f.menu.stones().get(10); assertNotNull(slot);
            assertEquals(33,slot.width); assertEquals(36,slot.height);
            verify(f.stones[10]).setForcedPosition(slot.x+6,slot.y+7);
            verify(f.icons[10]).setForcedPosition(slot.x+6,slot.y+7);
            verify(f.expander).allowCanvasPosition(f.stones[10]);
            verify(f.expander).allowCanvasPosition(f.icons[10]);
            verify(f.stones[10],never()).setOriginalWidth(anyInt());
            verify(f.stones[10],never()).setOriginalHeight(anyInt());
            verify(f.backgrounds).place(eq(10),eq(slot.getLocation()),eq(33),eq(36),anyBoolean());
            verify(f.backgrounds).placeLogoutIcon(f.icons[10], new java.awt.Point(slot.x+6,slot.y+7));
            f.menu.restoreBackgrounds(); f.states.restore();
            verify(f.stones[10]).setForcedPosition(-1,-1);
            clearInvocations(f.stones[10],f.icons[10],f.backgrounds);
            when(f.config.restoreLogoutTab()).thenReturn(false); f.menu.apply();
            if(layout==MenuStoneController.Layout.VANILLA_HORIZONTAL)
            { assertFalse(f.menu.stones().containsKey(10)); verify(f.stones[10],never()).setForcedPosition(anyInt(),anyInt()); }
            else { assertNotNull(f.menu.stones().get(10)); assertEquals(21,f.menu.stones().get(10).width); }
        }
    }
    @Test public void fixedAndClassicRetainLogoutTabWithEitherPreference()
    {
        for(int mode=0;mode<2;mode++) for(boolean restore:new boolean[]{false,true})
        {
            Fixture f=new Fixture(mode,MenuStoneController.Layout.VERTICAL_LEFT,restore); f.menu.apply();
            assertNotNull(f.menu.stones().get(10));
            assertEquals(33,f.menu.stones().get(10).width);
        }
    }
    @Test public void restoredTabRespectsHiddenNativeControlAndMenuVisibility()
    {
        Fixture f=new Fixture(2,MenuStoneController.Layout.VERTICAL_LEFT,true);
        when(f.icons[10].isHidden()).thenReturn(true); f.menu.apply();
        assertFalse(f.menu.stones().containsKey(10));
        verify(f.icons[10],never()).setHidden(false);
        verify(f.backgrounds,never()).place(eq(10),any(),anyInt(),anyInt(),anyBoolean());
        when(f.icons[10].isHidden()).thenReturn(false); when(f.config.hideLogoutStone()).thenReturn(true);
        f.menu.apply(); verify(f.icons[10]).setHidden(true); assertFalse(f.menu.stones().containsKey(10));
    }
    private static final class Fixture
    {
        final InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
        final WidgetStateStore states=new WidgetStateStore();
        final MenuStoneBackgrounds backgrounds=mock(MenuStoneBackgrounds.class);
        final WidgetBoundsExpander expander=mock(WidgetBoundsExpander.class);
        final Widget[] stones=new Widget[14], icons=new Widget[14];
        final MenuStoneController menu;
        Fixture(int mode,MenuStoneController.Layout layout,boolean restore)
        {
            Client client=mock(Client.class); OrbController orbs=mock(OrbController.class);
            when(client.getCanvasWidth()).thenReturn(1200); when(client.getCanvasHeight()).thenReturn(900);
            when(orbs.isLoggedIn()).thenReturn(true); when(orbs.isFixedMode()).thenReturn(mode==0); when(orbs.isClassicResizable()).thenReturn(mode==1);
            when(config.menuLayout()).thenReturn(layout); when(config.restoreLogoutTab()).thenReturn(restore);
            when(backgrounds.isNativeRowVisible(anyBoolean())).thenReturn(true);
            int[][] registry=mode==0?MenuStoneRegistry.FIXED:mode==1?MenuStoneRegistry.CLASSIC:MenuStoneRegistry.MODERN;
            for(int i=0;i<14;i++)
            {
                boolean x=mode==2 && i==10;
                stones[i]=WidgetGeometryTest.widget(x?1100:800+(i%7)*33,x?10:500+(i/7)*40,x?21:33,x?21:36);
                icons[i]=WidgetGeometryTest.widget(x?1100:804+(i%7)*33,x?10:502+(i/7)*40,x?21:25,x?21:32);
                when(client.getWidget(registry[i][0])).thenReturn(stones[i]); when(client.getWidget(registry[i][1])).thenReturn(icons[i]);
            }
            menu=Guice.createInjector(new AbstractModule(){protected void configure(){
                bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(OrbController.class).toProvider(() -> orbs); bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
                bind(WidgetStateStore.class).toInstance(states); bind(MenuStoneBackgrounds.class).toProvider(() -> backgrounds);
                bind(WidgetBoundsExpander.class).toProvider(() -> expander);
            }}).getInstance(MenuStoneController.class);
        }
    }
}
