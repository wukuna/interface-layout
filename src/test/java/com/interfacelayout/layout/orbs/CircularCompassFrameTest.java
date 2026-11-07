package com.interfacelayout.layout.orbs;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularCompassFrameTest {
    @Test public void followsLiveCompassInAllModesWithoutChangingNeedleOrActions() {
        int[] ids={InterfaceID.Toplevel.MAPCONTAINER_GRAPHIC5,OrbConstants.Widgets.Classic.COMPASS,
            OrbConstants.Widgets.Modern.COMPASS};
        for(int mode=0;mode<ids.length;mode++) {
            Client client=mock(Client.class);OrbController manager=mock(OrbController.class);
            when(manager.isFixedMode()).thenReturn(mode==0);when(manager.isClassicResizable()).thenReturn(mode==1);
            Widget widget=CircularOrbInputTest.widget(client,ids[mode],null,100,50,35,35);
            when(widget.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
            when(widget.getBounds()).thenReturn(new Rectangle(100,50,35,35));
            CircularCompassFrame frame=new CircularCompassFrame();frame.refresh(client,manager);
            BufferedImage image=render(frame);assertNotEquals(0,image.getRGB(97,67));
            assertEquals("The native needle is never redrawn",0,image.getRGB(117,67));
            verify(widget,never()).setSpriteId(anyInt());verify(widget,never()).setAction(anyInt(),anyString());
            verify(widget,never()).setOpacity(anyInt());verify(widget,never()).setHidden(anyBoolean());
            frame.restore(manager);widget.setOriginalX(200);widget.setOriginalY(100);frame.refresh(client,manager);
            image=render(frame);assertNotEquals(0,image.getRGB(197,117));assertEquals(0,image.getRGB(97,67));
            when(widget.isHidden()).thenReturn(true);assertEmpty(render(frame));
            frame.restore(manager);frame.refresh(client,manager);assertEmpty(render(frame));
        }
    }

    @Test public void restoresOwnedCompactFrameAndHonorsHiddenAndMinimizedStates() {
        Client client=mock(Client.class);OrbController manager=mock(OrbController.class);
        Widget compass=CircularOrbInputTest.widget(client,OrbConstants.Widgets.Modern.COMPASS,null,100,50,35,35);
        when(compass.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
        Widget nativeFrame=CircularOrbInputTest.widget(client,1,null,96,46,43,43);nativeFrame.setOpacity(40);
        manager.compassFrame=nativeFrame;
        CircularCompassFrame frame=new CircularCompassFrame();
        frame.refresh(client,manager);assertEquals(255,nativeFrame.getOpacity());
        frame.restore(manager);assertEquals(40,nativeFrame.getOpacity());assertEmpty(render(frame));
        when(manager.isCompassHidden()).thenReturn(true);frame.refresh(client,manager);
        assertEmpty(render(frame));assertEquals(40,nativeFrame.getOpacity());
        when(manager.isCompassHidden()).thenReturn(false);when(manager.isMinimapMinimized()).thenReturn(true);
        frame.refresh(client,manager);assertEmpty(render(frame));assertEquals(40,nativeFrame.getOpacity());
        when(manager.isMinimapMinimized()).thenReturn(false);frame.refresh(client,manager);
        nativeFrame.setOpacity(90);frame.restore(manager);assertEquals("Keep newer changes",90,nativeFrame.getOpacity());
        frame.refresh(client,manager);Widget replacement=mock(Widget.class);manager.compassFrame=replacement;
        frame.restore(manager);verifyNoInteractions(replacement);
        when(compass.getContentType()).thenReturn(0);frame.refresh(client,manager);assertEmpty(render(frame));
    }

    private static BufferedImage render(CircularCompassFrame frame) {
        BufferedImage image=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();frame.paint(g,new CircularOrbArtwork());g.dispose();return image;
    }
    private static void assertEmpty(BufferedImage image) {
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)assertEquals(0,image.getRGB(x,y));
    }
}
