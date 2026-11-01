package com.interfacelayout.layout.orbs;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class CircularOrbArtworkTest {
    @Test public void repairsTabJunctionSmoothsEdgesAndKeepsNativeFillVisible() {
        BufferedImage sprite=new BufferedImage(57,34,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sprite.createGraphics();g.setColor(new Color(95,145,190));g.fillRect(40,0,17,34);g.dispose();
        BufferedImage image=new BufferedImage(57,34,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();
        new CircularOrbArtwork().paint(g,sprite,new Rectangle(0,0,57,34),new Rectangle(23,0,34,34));g.dispose();
        assertEquals("Missing tab-side rim is reconstructed",image.getRGB(56,17),image.getRGB(23,17));
        assertNotEquals(0,image.getRGB(23,17));assertEquals("Live fill stays visible",0,image.getRGB(40,17));
        assertEquals("No removed tab artwork",0,image.getRGB(10,17));assertEquals(0,image.getRGB(23,0));
        int soft=0;for(int y=0;y<34;y++)for(int x=23;x<57;x++){int a=image.getRGB(x,y)>>>24;if(a>0&&a<255)soft++;}
        assertTrue("Curved edge uses partial coverage",soft>20);
    }

    @Test public void cacheFollowsPackReloadAndRelativeGeometryWithoutMutatingSprites() {
        CircularOrbArtwork art=new CircularOrbArtwork();BufferedImage first=solid(Color.BLUE),next=solid(Color.RED);
        BufferedImage result=new BufferedImage(150,40,2);Graphics2D g=result.createGraphics();
        art.paint(g,first,new Rectangle(0,0,57,34),new Rectangle(23,0,34,34));
        art.paint(g,next,new Rectangle(60,0,57,34),new Rectangle(83,0,34,34));g.dispose();
        assertEquals(Color.BLUE.getRGB(),result.getRGB(25,17));assertEquals(Color.RED.getRGB(),result.getRGB(85,17));
        assertEquals(Color.BLUE.getRGB(),first.getRGB(0,0));assertEquals(Color.RED.getRGB(),next.getRGB(0,0));
    }
    private static BufferedImage solid(Color color){BufferedImage i=new BufferedImage(57,34,2);Graphics2D g=i.createGraphics();g.setColor(color);g.fillRect(0,0,57,34);g.dispose();return i;}
}
