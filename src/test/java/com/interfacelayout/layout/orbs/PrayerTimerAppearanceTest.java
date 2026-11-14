package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.OrbTimerStyle;
import com.interfacelayout.InterfaceLayoutConfig.PrayerDisplay;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrayerTimerAppearanceTest {
    @Test public void sweepFillsTheInteriorWhileQuietRimsLeaveItClear() {
        BufferedImage sweep=render(OrbTimerStyle.INSET_SWEEP,PrayerDisplay.BOTH,.75,50);
        BufferedImage quiet=render(OrbTimerStyle.QUIET_RIMS,PrayerDisplay.BOTH,.75,50);
        assertTrue((sweep.getRGB(36,24)>>>24)>0);
        assertEquals(0,quiet.getRGB(36,24));
        assertTrue(NativeOrbTimerTest.pixels(sweep)>NativeOrbTimerTest.pixels(quiet)*2);
        assertEquals(0,sweep.getRGB(24,24));
        assertTrue((sweep.getRGB(36,24)>>>24)<100);
    }

    @Test public void eightSlicesSeparatePrayerAmountFromCountdownWithoutOuterDoubleRings() {
        BufferedImage both=render(OrbTimerStyle.EIGHT_SLICES,PrayerDisplay.BOTH,.75,50);
        BufferedImage amount=render(OrbTimerStyle.EIGHT_SLICES,PrayerDisplay.AMOUNT,.75,50);
        BufferedImage drain=render(OrbTimerStyle.EIGHT_SLICES,PrayerDisplay.DRAIN,.75,50);
        assertEquals(amount.getRGB(34,22),both.getRGB(34,22));
        assertTrue((amount.getRGB(34,22)>>>24)>30);
        assertTrue((amount.getRGB(26,38)>>>24)<15);
        assertTrue((drain.getRGB(26,38)>>>24)>30);
        assertEquals(0,amount.getRGB(45,30));
        assertTrue((both.getRGB(45,30)>>>24)>0);
        BufferedImage later=render(OrbTimerStyle.EIGHT_SLICES,PrayerDisplay.BOTH,.25,50);
        for(int y=20;y<40;y++)for(int x=20;x<40;x++)assertEquals(both.getRGB(x,y),later.getRGB(x,y));
        assertNotEquals(both.getRGB(30,45),later.getRGB(30,45));
    }

    private static BufferedImage render(OrbTimerStyle style,PrayerDisplay display,double progress,int amount) {
        InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
        when(config.prayerTimerStyle()).thenReturn(style);when(config.prayerOrbDisplay()).thenReturn(display);
        BufferedImage image=new BufferedImage(70,70,BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw=image.createGraphics();
        NativeOrbTimers.paint(draw,new Rectangle(15,15,30,30),new Rectangle(0,15,15,30),
            InterfaceID.Orbs.ORB_PRAYER,config,Color.CYAN,progress,amount,100);
        draw.dispose();return image;
    }
}
