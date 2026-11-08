package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.Menu;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbsTest {
    @Test public void inputSurvivesScriptsAndRestoresOnEditingDisablingLogoutAndStop() {
        CircularOrbs orb=new CircularOrbs();orb.client=mock(Client.class);orb.manager=mock(OrbController.class);orb.thread=mock(ClientThread.class);orb.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);orb.sprites=mock(MenuButtonSprites.class);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(orb.thread).invoke(any(Runnable.class));
        when(orb.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(orb.config.hideOrbTabs()).thenReturn(true);
        com.interfacelayout.layout.orbs.widget.elements.Orbs[] types={com.interfacelayout.layout.orbs.widget.elements.Orbs.HP_ORB_CONTAINER,com.interfacelayout.layout.orbs.widget.elements.Orbs.PRAYER_ORB_CONTAINER,com.interfacelayout.layout.orbs.widget.elements.Orbs.RUN_ORB_CONTAINER,com.interfacelayout.layout.orbs.widget.elements.Orbs.SPEC_ORB_CONTAINER};
        int[] texts={InterfaceID.Orbs.HEALTH_TEXT,InterfaceID.Orbs.PRAYER_TEXT,InterfaceID.Orbs.RUNENERGY_TEXT,InterfaceID.Orbs.SPECENERGY_TEXT};
        Widget[] buttons=new Widget[4];
        for(int n=0;n<4;n++) {
            com.interfacelayout.layout.orbs.widget.elements.Orbs type=types[n];
            Widget parent=CircularOrbInputTest.widget(orb.client,type.getComponentId(),null,100,n*40,57,34);
            Widget backing=CircularOrbInputTest.widget(orb.client,type.getBackingId(),parent,0,0,57,34);
            buttons[n]=CircularOrbInputTest.widget(orb.client,type.getButtonId(),parent,0,0,57,34);
            CircularOrbInputTest.widget(orb.client,type.getIndicatorId(),parent,27,4,26,26);
            Widget icon=CircularOrbInputTest.widget(orb.client,type.getIconId(),parent,27,4,26,26);
            Widget number=CircularOrbInputTest.widget(orb.client,texts[n],parent,0,0,25,34);when(number.getText()).thenReturn("99");
            when(orb.sprites.get(backing)).thenReturn(new BufferedImage(57,34,2));when(orb.sprites.get(icon)).thenReturn(new BufferedImage(26,26,2));
        }
        orb.start();EventBus bus=new EventBus();bus.register(orb);
        for(InterfaceLayoutConfig.OrbContent mode:InterfaceLayoutConfig.OrbContent.values()) {
            when(orb.config.orbContent()).thenReturn(mode);
            for(int hovered=0;hovered<buttons.length;hovered++) {
                when(orb.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,hovered*40+17));
                bus.post(new ClientTick());
                for(int n=0;n<buttons.length;n++)assertEquals(n==hovered?34:0,buttons[n].getOriginalWidth());
                bus.post(new ScriptPreFired(446));for(Widget button:buttons)assertEquals(57,button.getOriginalWidth());
                bus.post(new ScriptPostFired(446));
                for(int n=0;n<buttons.length;n++)assertEquals(n==hovered?34:0,buttons[n].getOriginalWidth());
            }
        }
        Menu menu=mock(Menu.class);when(orb.client.getMenu()).thenReturn(menu);
        MenuEntry action=mock(MenuEntry.class),unrelated=mock(MenuEntry.class);
        when(action.getParam1()).thenReturn(types[0].getButtonId());when(action.getType()).thenReturn(MenuAction.CC_OP);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{unrelated,action});when(orb.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(105,15));
        bus.post(new PostMenuSort());verify(menu).setMenuEntries(new MenuEntry[]{unrelated});clearInvocations(menu);
        when(orb.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,15));bus.post(new PostMenuSort());verify(menu,never()).setMenuEntries(any());
        when(orb.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(105,15));when(orb.client.isMenuOpen()).thenReturn(true);bus.post(new PostMenuSort());verify(menu,never()).setMenuEntries(any());
        when(orb.config.editInterface()).thenReturn(true);bus.post(new BeforeRender());for(Widget button:buttons)assertEquals(57,button.getOriginalWidth());
        when(orb.config.editInterface()).thenReturn(false);bus.post(new ClientTick());
        when(orb.config.hideOrbTabs()).thenReturn(false);bus.post(new ClientTick());for(Widget button:buttons)assertEquals(57,button.getOriginalWidth());
        when(orb.config.hideOrbTabs()).thenReturn(true);bus.post(new BeforeRender());
        GameStateChanged logout=new GameStateChanged();logout.setGameState(GameState.LOGIN_SCREEN);bus.post(logout);for(Widget button:buttons)assertEquals(57,button.getOriginalWidth());
        bus.post(new BeforeRender());orb.stop();for(Widget button:buttons)assertEquals(57,button.getOriginalWidth());bus.unregister(orb);
    }

    @Test public void asynchronousLayoutRefreshCannotCaptureCollapsedOrbSizes() throws Exception {
        CircularOrbs circular=new CircularOrbs();circular.client=mock(Client.class);circular.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
        circular.manager=mock(OrbController.class);circular.thread=mock(ClientThread.class);circular.sprites=mock(MenuButtonSprites.class);
        when(circular.config.hideOrbTabs()).thenReturn(true);when(circular.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(circular.thread).invoke(any(Runnable.class));
        Widget parent=CircularOrbInputTest.widget(circular.client,InterfaceID.Orbs.ORB_HEALTH,null,100,70,57,34);
        Widget backing=CircularOrbInputTest.widget(circular.client,InterfaceID.Orbs.HEALTH_BACKING,parent,0,0,57,34);
        CircularOrbInputTest.widget(circular.client,InterfaceID.Orbs.HEALTH_INDICATOR,parent,27,4,26,26);
        circular.start();circular.onBeforeRender(new BeforeRender());assertEquals(0,parent.getOriginalWidth());
        com.interfacelayout.layout.GameframeCoordinator frame=new com.interfacelayout.layout.GameframeCoordinator();
        com.interfacelayout.util.WidgetStateStore states=new com.interfacelayout.util.WidgetStateStore();
        FreePositionController free=mock(FreePositionController.class);
        java.util.Map<String,Object> fields=new java.util.HashMap<>();
        fields.put("states",states);fields.put("free",free);fields.put("running",true);
        fields.put("menu",mock(com.interfacelayout.layout.menu.MenuStoneController.class));
        fields.put("minimapClicks",mock(MinimapClickProtection.class));fields.put("expander",mock(com.interfacelayout.util.WidgetBoundsExpander.class));
        fields.put("menuOverlays",mock(com.interfacelayout.layout.menu.MenuOverlayBridge.class));fields.put("freeOverlays",mock(FreeOrbOverlayBridge.class));
        for(java.util.Map.Entry<String,Object> entry:fields.entrySet()) {
            java.lang.reflect.Field field=frame.getClass().getDeclaredField(entry.getKey());field.setAccessible(true);field.set(frame,entry.getValue());
        }
        java.lang.reflect.Field provider=frame.getClass().getDeclaredField("circular");provider.setAccessible(true);
        provider.set(frame,(javax.inject.Provider<CircularOrbs>)()->circular);
        doAnswer(i->{assertEquals("Only native geometry may enter layout snapshots",57,parent.getOriginalWidth());states.capture(parent);return null;}).when(free).apply();
        for(int i=0;i<6;i++) {
            frame.refresh();circular.onBeforeRender(new BeforeRender());
            BufferedImage image=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);Graphics2D draw=image.createGraphics();
            circular.render(draw);draw.dispose();assertNotEquals(0,image.getRGB(140,87));
        }
        circular.stop();frame.prepareNativeLayout();assertEquals(57,parent.getOriginalWidth());assertEquals(0,backing.getOpacity());
    }

    private static Widget widget(CircularOrbs orb,int id,Rectangle bounds) {
        Widget w=mock(Widget.class);when(w.getId()).thenReturn(id);when(w.getBounds()).thenReturn(bounds);when(orb.client.getWidget(id)).thenReturn(w);
        when(w.getRelativeX()).thenReturn(bounds.x);when(w.getRelativeY()).thenReturn(bounds.y);when(w.getWidth()).thenReturn(bounds.width);when(w.getHeight()).thenReturn(bounds.height);
        AtomicInteger opacity=new AtomicInteger();when(w.getOpacity()).thenAnswer(i->opacity.get());doAnswer(i->{opacity.set(i.getArgument(0));return w;}).when(w).setOpacity(anyInt());return w;
    }
    @Test public void lifecyclePreservesNativeVisibilityAndActionsWhileArtworkLoads() {
        CircularOrbs orb=new CircularOrbs();orb.client=mock(Client.class);orb.manager=mock(OrbController.class);orb.thread=mock(ClientThread.class);orb.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);orb.sprites=mock(MenuButtonSprites.class);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(orb.thread).invoke(any(Runnable.class));
        when(orb.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        Widget parent=widget(orb,InterfaceID.Orbs.ORB_HEALTH,new Rectangle(0,0,57,34));
        Widget backing=widget(orb,InterfaceID.Orbs.HEALTH_BACKING,new Rectangle(0,0,57,34));
        Widget fill=widget(orb,InterfaceID.Orbs.HEALTH_INDICATOR,new Rectangle(27,4,26,26));
        Widget icon=widget(orb,InterfaceID.Orbs.ORB_HEALTH_HEART_ICON,new Rectangle(27,4,26,26));
        Widget number=widget(orb,InterfaceID.Orbs.HEALTH_TEXT,new Rectangle(0,0,25,34));when(number.getText()).thenReturn("99");
        EventBus bus=new EventBus();bus.register(orb);orb.start();bus.post(new BeforeRender());assertEquals(0,backing.getOpacity());
        when(orb.config.hideOrbTabs()).thenReturn(true);bus.post(new BeforeRender());assertEquals(255,backing.getOpacity());
        when(orb.sprites.get(backing)).thenReturn(new BufferedImage(57,34,2));when(orb.sprites.get(icon)).thenReturn(new BufferedImage(26,26,2));
        bus.post(new BeforeRender());assertEquals(255,backing.getOpacity());assertEquals(255,number.getOpacity());assertEquals(0,fill.getOpacity());
        bus.post(new ScriptPreFired(446));assertEquals(0,backing.getOpacity());backing.setOpacity(60);
        bus.post(new BeforeRender());assertEquals(255,backing.getOpacity());
        when(parent.isHidden()).thenReturn(true);bus.post(new BeforeRender());assertEquals(60,backing.getOpacity());
        verify(parent,never()).setHidden(anyBoolean());verify(fill,never()).setHidden(anyBoolean());verify(icon,never()).setOriginalX(anyInt());
        when(parent.isHidden()).thenReturn(false);bus.post(new BeforeRender());orb.stop();assertEquals(60,backing.getOpacity());assertEquals(0,number.getOpacity());
        orb.start();bus.post(new BeforeRender());when(orb.config.hideOrbTabs()).thenReturn(false);bus.post(new BeforeRender());assertEquals(60,backing.getOpacity());bus.unregister(orb);
    }
    @Test public void circularFrameLeavesFillUntouchedAndAllThreeContentsAreDistinct() throws Exception {
        BufferedImage backing=new BufferedImage(57,34,2),icon=new BufferedImage(26,26,2);Graphics2D b=backing.createGraphics();b.setColor(Color.ORANGE);b.fillRect(0,0,57,34);b.dispose();
        Graphics2D i=icon.createGraphics();i.setColor(Color.BLUE);i.fillOval(0,0,26,26);i.dispose();
        BufferedImage preview=new BufferedImage(160,60,2);int offset=0;
        for(InterfaceLayoutConfig.OrbContent mode:InterfaceLayoutConfig.OrbContent.values()) {
            BufferedImage image=new BufferedImage(57,34,2);Graphics2D g=image.createGraphics();
            new CircularOrbArtwork().paint(g,new Rectangle(23,0,34,34));
            assertEquals(0,image.getRGB(10,15));assertEquals(0,image.getRGB(40,17));assertEquals(0,image.getRGB(23,0));
            CircularOrbs.paintContent(g,new Rectangle(23,0,34,34),icon,"99",Color.YELLOW,mode,0,0);g.dispose();
            int blue=0,yellow=0;for(int y=0;y<34;y++)for(int x=0;x<57;x++){blue+=image.getRGB(x,y)==Color.BLUE.getRGB()?1:0;yellow+=image.getRGB(x,y)==Color.YELLOW.getRGB()?1:0;}
            assertEquals(mode!=InterfaceLayoutConfig.OrbContent.NUMBER_ONLY,blue>0);assertEquals(mode!=InterfaceLayoutConfig.OrbContent.ICON_ONLY,yellow>0);
            Graphics2D p=preview.createGraphics();p.drawImage(image,offset-18,5,null);p.dispose();offset+=52;
        }
        javax.imageio.ImageIO.write(preview,"png",new java.io.File("build/circular-orb-modes-preview.png"));
    }
}
