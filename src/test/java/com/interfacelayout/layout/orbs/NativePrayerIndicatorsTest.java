package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import com.interfacelayout.InterfaceLayoutConfig.PrayerDisplay;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativePrayerIndicatorsTest
{
    private final InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
    private final Rectangle inset=new Rectangle(128,75,24,24),number=new Rectangle(100,70,25,34);

    @Test public void amountUsesActualMaximumAndDoesNotFollowTheCountdown()
    {
        for(NativeTimerStyle style:new NativeTimerStyle[]{NativeTimerStyle.TAG_METERS,NativeTimerStyle.EIGHT_SLICES}) {
            when(config.nativeOrbTimerStyle()).thenReturn(style);
            when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.AMOUNT);
            assertArrayEquals(paint(25,50,.1),paint(50,100,.9));
            assertFalse(Arrays.equals(paint(25,50,.1),paint(10,50,.1)));
            assertArrayEquals(paint(50,50,.1),paint(70,50,.1));
        }
    }

    @Test public void amountRemainsWithPrayersOffAndCountdownIsIndependentOfCurrentAmount()
    {
        for(NativeTimerStyle style:new NativeTimerStyle[]{NativeTimerStyle.TAG_METERS,NativeTimerStyle.EIGHT_SLICES}) {
            when(config.nativeOrbTimerStyle()).thenReturn(style);
            when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.BOTH);
            assertFalse(Arrays.equals(paint(50,99,0),new int[200*130]));
            assertFalse(Arrays.equals(paint(0,99,0),paint(50,99,0)));
            when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.DRAIN);
            assertArrayEquals(paint(15,50,.5),paint(50,99,.5));
            assertArrayEquals(new int[200*130],paint(0,0,0));
        }
    }

    @Test public void amountAndCountdownComposeWithoutCoveringEachOther()
    {
        for(NativeTimerStyle style:new NativeTimerStyle[]{NativeTimerStyle.TAG_METERS,NativeTimerStyle.EIGHT_SLICES}) {
            when(config.nativeOrbTimerStyle()).thenReturn(style);
            when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.AMOUNT);int[] amount=paint(50,99,.6);
            when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.BOTH);int[] both=paint(50,99,.6);
            int added=0;
            for(int i=0;i<amount.length;i++) {
                if((amount[i]>>>24)>0)assertEquals(style+" "+(i%200)+","+(i/200),amount[i],both[i]);
                else if((both[i]>>>24)>0)added++;
            }
            assertTrue(added>20);
        }
    }

    @Test public void allFourDrainStylesAreDistinctAndAnimateIndependentlyOfTheAmount() {
        java.util.List<int[]> styles=new java.util.ArrayList<>();
        when(config.prayerOrbDisplay()).thenReturn(PrayerDisplay.DRAIN);
        for(NativeTimerStyle style:NativeTimerStyle.values()) {
            when(config.nativeOrbTimerStyle()).thenReturn(style);
            int[] current=paint(50,99,.6);
            assertFalse(style.toString(),Arrays.equals(current,paint(50,99,.2)));
            assertArrayEquals(current,paint(20,99,.6));
            for(int[] previous:styles)assertFalse(style.toString(),Arrays.equals(previous,current));
            styles.add(current);
        }
    }

    private int[] paint(int current,int maximum,double progress)
    {
        BufferedImage image=new BufferedImage(200,130,BufferedImage.TYPE_INT_ARGB);Graphics2D draw=image.createGraphics();
        NativeOrbTimers.paint(draw,inset,number,InterfaceID.Orbs.ORB_PRAYER,config,OrbTimers.PRAYER_COLOR,progress,current,maximum);
        draw.dispose();return image.getRGB(0,0,200,130,null,0,200);
    }
}
