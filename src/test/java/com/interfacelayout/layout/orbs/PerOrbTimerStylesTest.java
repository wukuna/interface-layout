package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import com.interfacelayout.InterfaceLayoutConfig.OrbTimerStyle;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PerOrbTimerStylesTest
{
    @Test public void existingChoicesAreInheritedWithoutWritingOrMigratingSettings()
    {
        InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
        for(NativeTimerStyle style:NativeTimerStyle.values()) {
            when(config.nativeOrbTimerStyle()).thenReturn(style);
            when(config.hideOrbTabs()).thenReturn(false);assertEquals(style,OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_HEALTH));
            when(config.hideOrbTabs()).thenReturn(true);assertEquals(NativeTimerStyle.QUIET_RIMS,OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER));
        }
        when(config.hitpointsTimerStyle()).thenReturn(OrbTimerStyle.EIGHT_SLICES);
        when(config.prayerTimerStyle()).thenReturn(OrbTimerStyle.TAG_METERS);
        when(config.specialTimerStyle()).thenReturn(OrbTimerStyle.INSET_SWEEP);
        for(boolean circle:new boolean[]{true,false}) {
            when(config.hideOrbTabs()).thenReturn(circle);
            assertEquals(NativeTimerStyle.EIGHT_SLICES,OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_HEALTH));
            assertEquals(NativeTimerStyle.TAG_METERS,OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER));
            assertEquals(NativeTimerStyle.INSET_SWEEP,OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_SPECENERGY));
        }
    }

    @Test public void allStylesRenderInBothLayoutsAndSwitchingOneOrbLeavesTheOthersUnchanged()
    {
        for(boolean circles:new boolean[]{true,false})for(NativeTimerStyle style:NativeTimerStyle.values()) {
            CircularOrbOverlayTest.Fixture f=fixture(Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
            when(f.overlay.config.hideOrbTabs()).thenReturn(circles);
            when(f.overlay.config.prayerTimerStyle()).thenReturn(OrbTimerStyle.valueOf(style.name()));f.refresh();
            int[] first=pixels(f.render());
            when(f.overlay.config.hitpointsTimerStyle()).thenReturn(OrbTimerStyle.EIGHT_SLICES);f.refresh();assertArrayEquals(first,pixels(f.render()));
            when(f.overlay.config.orbPrayerTimer()).thenReturn(false);f.refresh();assertFalse(Arrays.equals(first,pixels(f.render())));
        }
    }

    @Test public void circularPrayerAmountAndDrainHaveSeparateSpaceAndPreserveDoseReminder() throws Exception
    {
        for(NativeTimerStyle style:new NativeTimerStyle[]{NativeTimerStyle.TAG_METERS,NativeTimerStyle.EIGHT_SLICES})
            for(boolean first:new boolean[]{true,false}) {
                CircularOrbOverlayTest.Fixture f=fixture(Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
                when(f.overlay.config.prayerTimerStyle()).thenReturn(OrbTimerStyle.valueOf(style.name()));f.refresh();
                CircularOrbCoreOverlayTest.Core core=new CircularOrbCoreOverlayTest.Core(f.overlay.client,"prayer.PrayerDoseOverlay");
                core.stub("prayer.PrayerConfig","showPrayerDoseIndicator",true);core.stub("prayer.PrayerConfig","prayerDoseOrbStartColor",Color.MAGENTA);
                java.lang.reflect.Field amount=core.overlay.getClass().getDeclaredField("restoreAmount");amount.setAccessible(true);amount.setInt(core.overlay,30);
                java.lang.reflect.Field pulse=core.overlay.getClass().getDeclaredField("startOfLastTick");pulse.setAccessible(true);pulse.set(core.overlay,java.time.Instant.EPOCH);
                BufferedImage expected=core.render(),baseline=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
                when(f.overlay.config.disableOrbTimers()).thenReturn(true);Graphics2D base=baseline.createGraphics();
                if(first)core.overlay.render(base);f.overlay.render(base);if(!first)core.overlay.render(base);base.dispose();
                when(f.overlay.config.disableOrbTimers()).thenReturn(false);
                BufferedImage actual=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();
                if(first)core.overlay.render(g);f.overlay.render(g);if(!first)core.overlay.render(g);g.dispose();int checked=0;
                for(int y=0;y<200;y++)for(int x=0;x<400;x++)if(expected.getRGB(x,y)==Color.MAGENTA.getRGB() && baseline.getRGB(x,y)==expected.getRGB(x,y)) {
                    assertEquals(style+" "+x+","+y,expected.getRGB(x,y),actual.getRGB(x,y));checked++;
                }
                assertTrue(checked>20);
            }
    }

    private CircularOrbOverlayTest.Fixture fixture(Orbs orb,int number)
    {
        CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture(orb,number);
        when(f.overlay.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);when(f.overlay.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
        when(f.overlay.client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);f.overlay.onGameTick(new GameTick());
        f.overlay.timers.tick(f.overlay.client,null,Long.MAX_VALUE/2);f.refresh();return f;
    }
    private int[] pixels(BufferedImage image) {return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}
}
