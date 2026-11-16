package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutPlugin;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimerInfoBoxLifecycleTest
{
    @Test public void independentBoxesKeepStableIdentitiesAndClearOnLogoutAndDisable()
    {
        OrbTimerInfoBoxes boxes = new OrbTimerInfoBoxes();
        boxes.client = mock(Client.class);
        boxes.config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        boxes.plugin = new InterfaceLayoutPlugin();
        boxes.manager = mock(InfoBoxManager.class);
        boxes.sprites = mock(SpriteManager.class);
        when(boxes.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        OrbTimers timers = new OrbTimers();
        when(boxes.client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(boxes.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        when(boxes.client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(500);
        timers.tick(boxes.client, null, System.nanoTime());
        boxes.refresh(timers);
        verifyNoInteractions(boxes.manager);
        when(boxes.config.hpNextInfoBox()).thenReturn(true);
        when(boxes.config.hpFullInfoBox()).thenReturn(true);
        when(boxes.config.specialNextInfoBox()).thenReturn(true);
        when(boxes.config.specialFullInfoBox()).thenReturn(true);
        when(boxes.sprites.getSprite(anyInt(), eq(0))).thenReturn(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB));
        boxes.refresh(timers);
        ArgumentCaptor<InfoBox> added = ArgumentCaptor.forClass(InfoBox.class);
        verify(boxes.manager, times(4)).addInfoBox(added.capture());
        HashSet<String> names = new HashSet<>();
        for (InfoBox box : added.getAllValues())
        {
            assertTrue(names.add(box.getName()));
            assertTrue(box.render());
            assertNotNull(box.getText());
            assertTrue(box.getTooltip().contains("Estimated"));
        }
        boxes.refresh(timers);
        verify(boxes.manager, times(4)).addInfoBox(any());
        when(boxes.config.hpNextInfoBox()).thenReturn(false);
        boxes.refresh(timers);
        verify(boxes.manager).removeInfoBox(added.getAllValues().get(0));
        when(boxes.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        boxes.refresh(timers);
        verify(boxes.manager, times(4)).removeInfoBox(any());
        boxes.clear();
        verify(boxes.manager, times(4)).removeInfoBox(any());
        verify(boxes.client, never()).getPlayers();
        verify(boxes.client, never()).getNpcs();
    }

    @Test public void potionAndRunMetersUseSeparateSpaceInBothOrbPresentations()
    {
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.runTimerStyle()).thenReturn(InterfaceLayoutConfig.OrbTimerStyle.TAG_METERS);
        when(config.orbRunTimer()).thenReturn(true);
        when(config.prayerTimerStyle()).thenReturn(InterfaceLayoutConfig.OrbTimerStyle.TAG_METERS);
        when(config.orbPrayerRegenerationTimer()).thenReturn(true);
        assertEquals(4, NativeOrbTimers.extraHeight(config, net.runelite.api.gameval.InterfaceID.Orbs.ORB_RUNENERGY));
        assertEquals(10, NativeOrbTimers.extraHeight(config, net.runelite.api.gameval.InterfaceID.Orbs.ORB_PRAYER));
        when(config.orbPrayerTimer()).thenReturn(false);
        assertEquals(4, NativeOrbTimers.extraHeight(config, net.runelite.api.gameval.InterfaceID.Orbs.ORB_PRAYER));
        when(config.disableOrbTimers()).thenReturn(true);
        assertEquals(0, NativeOrbTimers.extraHeight(config, net.runelite.api.gameval.InterfaceID.Orbs.ORB_RUNENERGY));
    }

    @Test public void everyNewMeterStyleRendersInsideItsProvidedClip()
    {
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        for (InterfaceLayoutConfig.OrbTimerStyle style : InterfaceLayoutConfig.OrbTimerStyle.values())
        {
            when(config.runTimerStyle()).thenReturn(style);
            when(config.prayerTimerStyle()).thenReturn(style);
            for (boolean circular : new boolean[]{false, true})
            {
                when(config.hideOrbTabs()).thenReturn(circular);
                BufferedImage image = new BufferedImage(120,100,BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D draw = image.createGraphics();
                draw.setClip(8,8,104,80);
                java.awt.Rectangle circle = new java.awt.Rectangle(15,15,38,38);
                java.awt.Rectangle number = circular ? null : new java.awt.Rectangle(60,15,40,32);
                if (circular) NativeOrbTimers.paintCircular(draw,circle,net.runelite.api.gameval.InterfaceID.Orbs.ORB_RUNENERGY,config,OrbTimers.RUN_COLOR,.7,0,0);
                else NativeOrbTimers.paint(draw,circle,number,net.runelite.api.gameval.InterfaceID.Orbs.ORB_RUNENERGY,config,OrbTimers.RUN_COLOR,.7,0,0);
                draw.dispose();
                assertTrue(visiblePixels(image)>0);
                BufferedImage potion = new BufferedImage(120,100,BufferedImage.TYPE_INT_ARGB);
                draw = potion.createGraphics(); draw.setClip(8,8,104,80);
                NativeOrbTimers.paintRegeneration(draw,circle,number,config,OrbTimers.REGENERATION_COLOR,.6);
                draw.dispose();
                assertTrue(style.name(),visiblePixels(potion)>0);
                for (int y=0;y<100;y++) for (int x=0;x<120;x++)
                    if(x<8 || y<8 || x>=112 || y>=88) assertEquals(0,potion.getRGB(x,y));
            }
        }
    }

    private static int visiblePixels(BufferedImage image)
    {
        int count=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0)count++;
        return count;
    }
}
