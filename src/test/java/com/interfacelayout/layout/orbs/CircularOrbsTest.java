package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbsTest {
    private static Widget widget(CircularOrbs orb,int id,Rectangle bounds) {
        Widget w=mock(Widget.class);when(w.getId()).thenReturn(id);when(w.getBounds()).thenReturn(bounds);when(orb.client.getWidget(id)).thenReturn(w);
        AtomicInteger opacity=new AtomicInteger();when(w.getOpacity()).thenAnswer(i->opacity.get());doAnswer(i->{opacity.set(i.getArgument(0));return w;}).when(w).setOpacity(anyInt());return w;
    }
    @Test public void lifecyclePreservesNativeVisibilityScriptsAndActionsAndWaitsForArtwork() {
        CircularOrbs orb=new CircularOrbs();orb.client=mock(Client.class);orb.thread=mock(ClientThread.class);orb.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);orb.sprites=mock(MenuButtonSprites.class);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(orb.thread).invoke(any(Runnable.class));
        when(orb.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        Widget parent=widget(orb,InterfaceID.Orbs.ORB_HEALTH,new Rectangle(0,0,57,34));
        Widget backing=widget(orb,InterfaceID.Orbs.HEALTH_BACKING,new Rectangle(0,0,57,34));
        Widget fill=widget(orb,InterfaceID.Orbs.HEALTH_INDICATOR,new Rectangle(27,4,26,26));
        Widget icon=widget(orb,InterfaceID.Orbs.ORB_HEALTH_HEART_ICON,new Rectangle(27,4,26,26));
        Widget number=widget(orb,InterfaceID.Orbs.HEALTH_TEXT,new Rectangle(0,0,25,34));when(number.getText()).thenReturn("99");
        EventBus bus=new EventBus();bus.register(orb);orb.start();bus.post(new BeforeRender());assertEquals(0,backing.getOpacity());
        when(orb.config.hideOrbTabs()).thenReturn(true);bus.post(new BeforeRender());assertEquals(0,backing.getOpacity());
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
            CircularOrbs.paintFrame(g,backing,new Rectangle(0,0,57,34),new Rectangle(23,0,34,34));
            assertEquals(0,image.getRGB(10,15));assertEquals(0,image.getRGB(40,17));assertEquals(0,image.getRGB(23,0));
            CircularOrbs.paintContent(g,new Rectangle(23,0,34,34),icon,"99",Color.YELLOW,mode,0,0);g.dispose();
            int blue=0,yellow=0;for(int y=0;y<34;y++)for(int x=0;x<57;x++){blue+=image.getRGB(x,y)==Color.BLUE.getRGB()?1:0;yellow+=image.getRGB(x,y)==Color.YELLOW.getRGB()?1:0;}
            assertEquals(mode!=InterfaceLayoutConfig.OrbContent.NUMBER_ONLY,blue>0);assertEquals(mode!=InterfaceLayoutConfig.OrbContent.ICON_ONLY,yellow>0);
            Graphics2D p=preview.createGraphics();p.drawImage(image,offset-18,5,null);p.dispose();offset+=52;
        }
        javax.imageio.ImageIO.write(preview,"png",new java.io.File("build/circular-orb-modes-preview.png"));
    }
}
