package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class CircularOrbArtworkTest {
    @Test public void paddedAxeAndShieldIconsHaveTheSameVisibleSizeAsTightSprites() {
        BufferedImage padded=SoulreaperOrbCompatibilityTest.icon();
        BufferedImage tight=padded.getSubimage(9,6,8,14);
        for(com.interfacelayout.InterfaceLayoutConfig.OrbContent mode:com.interfacelayout.InterfaceLayoutConfig.OrbContent.values()) {
            BufferedImage a=new BufferedImage(34,34,2),b=new BufferedImage(34,34,2);
            Graphics2D ga=a.createGraphics(),gb=b.createGraphics();
            CircularOrbs.paintContent(ga,new Rectangle(0,0,34,34),padded,"100",Color.GREEN,mode,0,0);
            CircularOrbs.paintContent(gb,new Rectangle(0,0,34,34),tight,"100",Color.GREEN,mode,0,0);
            ga.dispose();gb.dispose();
            for(int y=0;y<34;y++)for(int x=0;x<34;x++)assertEquals(a.getRGB(x,y),b.getRGB(x,y));
        }
    }
    @Test public void rimIsContinuousAtEveryAngleWithClearCenterAndCorners() {
        CircularOrbArtwork art=new CircularOrbArtwork();
        for(OrbRim style:OrbRim.values())for(int diameter:new int[]{33,34,41,43,68}) {
            BufferedImage image=new BufferedImage(diameter,diameter,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();art.paint(g,new Rectangle(0,0,diameter,diameter),style);g.dispose();
            for(int angle=0;angle<360;angle++) {
                double radians=Math.toRadians(angle),radius=diameter/2.0-2;
                int x=(int)(diameter/2.0+Math.cos(radians)*radius);
                int y=(int)(diameter/2.0+Math.sin(radians)*radius);
                assertTrue("Gap at diameter "+diameter+" angle "+angle,(image.getRGB(x,y)>>>24)>220);
            }
            assertEquals("Native artwork remains visible",0,image.getRGB(diameter/2,diameter/2));
            assertEquals(0,image.getRGB(0,0));assertEquals(0,image.getRGB(diameter-1,diameter-1));
            int soft=0;
            for(int y=0;y<diameter;y++)for(int x=0;x<diameter;x++) {
                int alpha=image.getRGB(x,y)>>>24;if(alpha>0&&alpha<255)soft++;
            }
            assertTrue("Small curved edges have partial coverage",soft>20);
        }
    }

    @Test public void switchingRimColorPreservesCoverageAndRestoresCachedSilver() {
        CircularOrbArtwork art = new CircularOrbArtwork();
        BufferedImage[] images = new BufferedImage[3];
        OrbRim[] styles = {OrbRim.SILVER, OrbRim.DARK_GREY, OrbRim.SILVER};
        for (int i = 0; i < images.length; i++) {
            images[i] = new BufferedImage(34, 34, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = images[i].createGraphics();
            art.paint(g, new Rectangle(0, 0, 34, 34), styles[i]);
            g.dispose();
        }
        for (int y = 0; y < 34; y++) for (int x = 0; x < 34; x++) {
            int silver = images[0].getRGB(x, y), grey = images[1].getRGB(x, y);
            assertEquals(silver >>> 24, grey >>> 24);
            assertEquals(silver, images[2].getRGB(x, y));
            assertTrue(((grey >> 16) & 255) <= ((silver >> 16) & 255));
        }
        assertTrue(new Color(images[1].getRGB(17, 1), true).getRed()
            < new Color(images[0].getRGB(17, 1), true).getRed());
    }

    @Test public void rimStaysCircularAndCenteredAroundUnequalContentBounds() {
        Rectangle content=new Rectangle(50,70,26,28),circle=CircularOrbArtwork.around(content);
        assertEquals(36,circle.width);assertEquals(circle.width,circle.height);
        assertEquals(content.getCenterX(),circle.getCenterX(),.5);
        assertEquals(content.getCenterY(),circle.getCenterY(),.5);
        assertTrue(circle.contains(content));
    }

    @Test public void previewShowsProductionRimsAtNativeSizeAndDoubleSize() throws Exception {
        BufferedImage image=new BufferedImage(600,240,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(35,39,37));g.fillRect(0,0,600,240);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(Color.LIGHT_GRAY);
        g.drawString("B - Clean rim: actual renderer",18,24);
        g.drawString("Native sizes",18,50);g.drawString("2x detail",18,130);
        CircularOrbArtwork art=new CircularOrbArtwork();
        int x=140;
        for(int diameter:new int[]{34,34,34,34,43}) {
            BufferedImage rim=new BufferedImage(diameter,diameter,BufferedImage.TYPE_INT_ARGB);
            Graphics2D r=rim.createGraphics();art.paint(r,new Rectangle(0,0,diameter,diameter),OrbRim.SILVER);r.dispose();
            g.drawImage(rim,x,40,null);g.drawImage(rim,18+(x-140)*2,145,diameter*2,diameter*2,null);
            x+=60;
        }
        g.dispose();javax.imageio.ImageIO.write(image,"png",new java.io.File("build/clean-rim-preview.png"));
    }
}
