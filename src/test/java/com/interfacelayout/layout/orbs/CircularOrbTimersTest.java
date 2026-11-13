package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbContent;
import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbTimersTest
{
    private CircularOrbOverlayTest.Fixture fixture(Orbs orb, int text)
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture(orb, text);
        when(f.overlay.client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(f.overlay.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        when(f.overlay.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        when(f.overlay.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(1);
        when(f.overlay.client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(10, 10));
        f.overlay.onGameTick(new GameTick());
        for (int i = 0; i < 23; i++) f.overlay.timers.tick(f.overlay.client, null, System.nanoTime());
        f.refresh();
        return f;
    }

    private int pixels(BufferedImage image, Color color)
    {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getRGB(x, y) == color.getRGB()) count++;
        return count;
    }

    @Test public void ownTimersRemainVisibleOverIconsAndMoveWithEveryOrbStyle()
    {
        Orbs[] orbs = {Orbs.HP_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER, Orbs.PRAYER_ORB_CONTAINER};
        int[] text = {InterfaceID.Orbs.HEALTH_TEXT, InterfaceID.Orbs.SPECENERGY_TEXT, InterfaceID.Orbs.PRAYER_TEXT};
        Color[] color = {OrbTimers.HITPOINTS_COLOR, OrbTimers.SPECIAL_COLOR, OrbTimers.PRAYER_COLOR};
        for (int i = 0; i < orbs.length; i++) for (OrbContent mode : OrbContent.values())
            for (OrbRim rim : OrbRim.values()) for (int move : new int[]{0, 30})
            {
                CircularOrbOverlayTest.Fixture f = fixture(orbs[i], text[i]);
                when(f.overlay.config.orbContent()).thenReturn(mode);
                when(f.overlay.config.orbRim()).thenReturn(rim);
                f.overlay.releaseForLayout(); f.parent.setOriginalX(100 + move); f.refresh();
                BufferedImage actual = f.render();
                assertTrue(orbs[i] + " " + mode + " " + rim, pixels(actual, color[i]) >= 5);
                for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
                    if (actual.getRGB(x, y) == color[i].getRGB())
                    {
                        double radius = Math.hypot(x + .5 - (140 + move), y + .5 - 87);
                        int insetRadius = orbs[i] == Orbs.SPEC_ORB_CONTAINER ? 14 : 13;
                        assertTrue(radius > insetRadius - 2 && radius < insetRadius + 2);
                    }
            }
    }

    @Test public void togglesHiddenWidgetsAndEditingSuppressOnlyOurTimers()
    {
        CircularOrbOverlayTest.Fixture f = fixture(Orbs.PRAYER_ORB_CONTAINER, InterfaceID.Orbs.PRAYER_TEXT);
        when(f.overlay.config.orbPrayerTimer()).thenReturn(false);
        assertEquals(0, pixels(f.render(), OrbTimers.PRAYER_COLOR));
        when(f.overlay.config.orbPrayerTimer()).thenReturn(true);
        when(f.parent.isHidden()).thenReturn(true); f.refresh();
        assertEquals(0, pixels(f.render(), OrbTimers.PRAYER_COLOR));
        when(f.parent.isHidden()).thenReturn(false);
        when(f.overlay.config.editInterface()).thenReturn(true); f.refresh();
        assertEquals(0, pixels(f.render(), OrbTimers.PRAYER_COLOR));
        when(f.overlay.config.editInterface()).thenReturn(false); f.refresh();
        assertTrue(pixels(f.render(), OrbTimers.PRAYER_COLOR) > 0);
        when(f.overlay.config.hideOrbTabs()).thenReturn(false); f.refresh();
        assertTrue(pixels(f.render(), OrbTimers.PRAYER_COLOR)>0);
        when(f.overlay.config.disableOrbTimers()).thenReturn(true);
        assertEquals(0,pixels(f.render(),OrbTimers.PRAYER_COLOR));
        assertEquals(57, f.parent.getOriginalWidth());
    }

    @Test public void theRealDoseReminderSurvivesBothRenderOrdersAndDisablingDrain() throws Exception
    {
        for (boolean reminderFirst : new boolean[]{true, false}) for (boolean drain : new boolean[]{true, false})
        {
            CircularOrbOverlayTest.Fixture f = fixture(Orbs.PRAYER_ORB_CONTAINER, InterfaceID.Orbs.PRAYER_TEXT);
            when(f.overlay.config.orbPrayerTimer()).thenReturn(drain);
            CircularOrbCoreOverlayTest.Core core = new CircularOrbCoreOverlayTest.Core(f.overlay.client, "prayer.PrayerDoseOverlay");
            core.stub("prayer.PrayerConfig", "showPrayerDoseIndicator", true);
            core.stub("prayer.PrayerConfig", "prayerDoseOrbStartColor", Color.MAGENTA);
            Field amount = core.overlay.getClass().getDeclaredField("restoreAmount");
            amount.setAccessible(true); amount.setInt(core.overlay, 30);
            Field pulse = core.overlay.getClass().getDeclaredField("startOfLastTick");
            pulse.setAccessible(true); pulse.set(core.overlay, java.time.Instant.EPOCH);
            BufferedImage expected = core.render();
            BufferedImage actual = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = actual.createGraphics();
            if (reminderFirst) core.overlay.render(g);
            f.overlay.render(g);
            if (!reminderFirst) core.overlay.render(g);
            g.dispose();
            int checked = 0;
            for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++)
                if ((expected.getRGB(x, y) >>> 24) != 0 && Math.hypot(x + .5 - 140, y + .5 - 87) > 18)
                {
                    assertEquals(expected.getRGB(x, y), actual.getRGB(x, y)); checked++;
                }
            assertTrue("Outer reminder remains visible", checked > 20);
            assertEquals(drain, pixels(actual, OrbTimers.PRAYER_COLOR) > 0);
        }
    }

    @Test public void coreRegenCannotLeaveASecondArcUnderTheCustomOrb() throws Exception
    {
        CircularOrbOverlayTest.Fixture f = fixture(Orbs.HP_ORB_CONTAINER, InterfaceID.Orbs.HEALTH_TEXT);
        CircularOrbCoreOverlayTest.Core core = new CircularOrbCoreOverlayTest.Core(f.overlay.client, "regenmeter.RegenMeterOverlay");
        core.stub("regenmeter.RegenMeterConfig", "showHitpoints", true);
        core.stub("regenmeter.RegenMeterPlugin", "getHitpointsPercentage", 1.0);
        BufferedImage withCore = core.render();
        BufferedImage withoutCore = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        long tick = System.nanoTime() + 60_000_000_000L;
        f.overlay.timers.tick(f.overlay.client, null, tick);
        Graphics2D a = withCore.createGraphics(), b = withoutCore.createGraphics();
        f.overlay.render(a); f.overlay.render(b); a.dispose(); b.dispose();
        for (int y = 0; y < withCore.getHeight(); y++) for (int x = 0; x < withCore.getWidth(); x++)
            assertEquals("No duplicate native arc at " + x + "," + y, withoutCore.getRGB(x, y), withCore.getRGB(x, y));
    }

    @Test public void lifecycleResetsTimersEvenWhenStopAndStartShareAQueuedCleanup()
    {
        CircularOrbOverlayTest.Fixture f = fixture(Orbs.SPEC_ORB_CONTAINER, InterfaceID.Orbs.SPECENERGY_TEXT);
        assertTrue(pixels(f.render(), OrbTimers.SPECIAL_COLOR) > 0);
        f.overlay.stop(); f.overlay.start(); f.refresh();
        assertEquals(0, pixels(f.render(), OrbTimers.SPECIAL_COLOR));
        f.overlay.onGameTick(new GameTick());
        assertEquals(.02, f.overlay.timers.progress(InterfaceID.Orbs.ORB_SPECENERGY, 0), .000001);
        for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING})
        {
            GameStateChanged event = new GameStateChanged(); event.setGameState(state);
            f.overlay.onGameStateChanged(event);
            assertEquals(0, f.overlay.timers.progress(InterfaceID.Orbs.ORB_SPECENERGY, System.nanoTime()), 0);
            f.overlay.onGameTick(new GameTick());
        }
        VarbitChanged rapid = new VarbitChanged(); rapid.setVarbitId(VarbitID.PRAYER_RAPIDHEAL);
        f.overlay.onVarbitChanged(rapid);
        assertEquals(0, f.overlay.timers.progress(InterfaceID.Orbs.ORB_HEALTH, 0), 0);
    }
}
