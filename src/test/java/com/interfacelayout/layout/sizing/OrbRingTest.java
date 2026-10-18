package com.interfacelayout.layout.sizing;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.regenmeter.RegenMeterPlugin;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrbRingTest
{
    private static class Fixture
    {
        final Client client = mock(Client.class);
        final PluginManager plugins = mock(PluginManager.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final ConfigManager configs = mock(ConfigManager.class);
        final OrbController manager = mock(OrbController.class);
        final RegenMeterPlugin regen = mock(RegenMeterPlugin.class);
        final Plugin special = mock(Plugin.class);
        final OrbRingTimers timers;
        Fixture()
        {
            manager.active = true;
            when(manager.isLoggedIn()).thenReturn(true);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(config.orbTimerCompatibility()).thenReturn(true);
            when(special.getName()).thenReturn("Special Attack Timers");
            when(regen.getName()).thenReturn("Regeneration Meter");
            when(plugins.getPlugins()).thenReturn(Arrays.asList(regen, special));
            when(plugins.isPluginActive(regen)).thenReturn(true);
            when(regen.getHitpointsPercentage()).thenReturn(0.5);
            when(regen.getSpecialPercentage()).thenReturn(0.7);
            timers = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client);
                    bind(PluginManager.class).toProvider(() -> plugins);
                    bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(ConfigManager.class).toInstance(configs);
                    bind(OrbController.class).toProvider(() -> manager);
                }
            }).getInstance(OrbRingTimers.class);
        }
        void tick(int energy, int tick)
        {
            when(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)).thenReturn(energy);
            when(client.getTickCount()).thenReturn(tick);
            timers.onGameTick(new GameTick());
        }
        void message(String message)
        {
            ChatMessage event = mock(ChatMessage.class);
            when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
            when(event.getMessage()).thenReturn(message);
            timers.onChatMessage(event);
        }
    }

    @Test public void readsEnabledProviderWithoutRequiringOriginalRingsOrChangingItsSettings()
    {
        Fixture f = new Fixture();
        when(f.configs.getConfiguration(anyString(), anyString())).thenReturn("false");
        f.tick(500, 1);
        assertEquals(0.5, f.timers.hp(), 0);
        assertEquals(0.7, f.timers.spec(), 0);
        verify(f.client, never()).addChatMessage(any(), anyString(), anyString(), any());
        verify(f.configs, never()).setConfiguration(anyString(), anyString(), any(Object.class));
    }

    @Test public void warnsOnceUntilResolvedAndNeverWritesOtherConfigs()
    {
        Fixture f = new Fixture(); f.tick(500, 1); f.tick(500, 2);
        verify(f.client, times(1)).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains("Resizing Orb Timers (Beta)"), isNull());
        when(f.configs.getConfiguration(anyString(), anyString())).thenReturn("false");
        f.tick(500, 3);
        when(f.configs.getConfiguration(anyString(), anyString())).thenReturn("true");
        f.tick(500, 4);
        verify(f.client, times(2)).addChatMessage(eq(ChatMessageType.GAMEMESSAGE), eq(""), contains("Resizing Orb Timers (Beta)"), isNull());
        verify(f.configs, never()).setConfiguration(anyString(), anyString(), any(Object.class));
    }

    @Test public void specialTimersTakePriorityWithWarmupAndFallbackWhenStopped()
    {
        Fixture f = new Fixture(); when(f.plugins.isPluginActive(f.special)).thenReturn(true);
        f.tick(500, 1); assertEquals(0, f.timers.spec(), 0);
        f.tick(600, 2); assertEquals(0.02, f.timers.spec(), 0.0001);
        assertEquals(0.5, f.timers.hp(), 0);
        when(f.plugins.isPluginActive(f.special)).thenReturn(false);
        f.tick(600, 3); assertEquals(0.7, f.timers.spec(), 0);
        when(f.plugins.isPluginActive(f.regen)).thenReturn(false);
        assertEquals(0, f.timers.spec(), 0); assertEquals(0, f.timers.hp(), 0);
        f.timers.reset(); assertEquals(0, f.timers.hp(), 0);
    }

    @Test public void waveMessagesAreScopedAndLoginResetLosesOldPhase()
    {
        Fixture f = new Fixture(); when(f.plugins.isPluginActive(f.special)).thenReturn(true);
        Player player = mock(Player.class); when(f.client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        f.tick(500, 1); f.tick(600, 2);
        f.message("Wave 1 completed!"); assertTrue(f.timers.spec() > 0);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(28 * 64, 48 * 64, 0)); // 7216
        f.message("<col=ff0000>Wave 1 completed!</col>"); assertEquals(0, f.timers.spec(), 0);
        f.message("@mes_hl_red@Wave: 2"); f.tick(600, 3); assertTrue(f.timers.spec() > 0);
        f.message("Wave 2 completed!");
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        f.tick(600, 4); assertTrue(f.timers.spec() > 0);
        GameStateChanged logout = mock(GameStateChanged.class); when(logout.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        f.timers.onGameStateChanged(logout); f.tick(600, 5); assertEquals(0, f.timers.spec(), 0);
    }

    @Test public void independentRingTogglesAndFeatureDisableDoNotAffectProviders()
    {
        Fixture f = new Fixture(); f.tick(500, 1);
        when(f.config.orbHpRing()).thenReturn(false); assertEquals(0, f.timers.hp(), 0); assertEquals(0.7, f.timers.spec(), 0);
        when(f.config.orbTimerCompatibility()).thenReturn(false); assertEquals(0, f.timers.spec(), 0);
        f.tick(500, 2); when(f.config.orbTimerCompatibility()).thenReturn(true);
        assertEquals(0, f.timers.spec(), 0);
    }

    @Test public void renderingFollowsSmallMovedIndicatorAndLeavesGraphicsAndVisibilityAlone()
    {
        Fixture f = new Fixture(); f.tick(500, 1);
        Widget root = mock(Widget.class), indicator = mock(Widget.class);
        when(root.getId()).thenReturn(Orbs.HP_ORB_CONTAINER.getComponentId());
        when(root.getRelativeX()).thenReturn(60); when(root.getRelativeY()).thenReturn(30);
        when(indicator.getParent()).thenReturn(root); when(indicator.getParentId()).thenReturn(Orbs.HP_ORB_CONTAINER.getComponentId());
        when(indicator.getRelativeX()).thenReturn(10); when(indicator.getRelativeY()).thenReturn(5);
        when(indicator.getWidth()).thenReturn(13); when(indicator.getHeight()).thenReturn(13);
        when(f.client.getWidget(root.getId())).thenReturn(root);
        when(f.client.getWidget(Orbs.HP_ORB_CONTAINER.getIndicatorId())).thenReturn(indicator);
        OrbRingOverlay overlay = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(f.client);
                bind(InterfaceLayoutConfig.class).toInstance(f.config);
                bind(OrbController.class).toProvider(() -> f.manager);
                bind(OrbRingTimers.class).toProvider(() -> f.timers);
            }
        }).getInstance(OrbRingOverlay.class);
        BufferedImage image = new BufferedImage(150, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics(); graphics.setColor(Color.MAGENTA);
        Stroke original = graphics.getStroke(); overlay.render(graphics);
        assertEquals(Color.MAGENTA, graphics.getColor()); assertEquals(original, graphics.getStroke());
        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getRGB(x, y) != 0) { pixels++; assertTrue(x >= 69 && x <= 84 && y >= 34 && y <= 49); }
        assertTrue(pixels > 0);
        when(root.isHidden()).thenReturn(true);
        BufferedImage hidden = new BufferedImage(150, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hiddenGraphics = hidden.createGraphics(); overlay.render(hiddenGraphics); hiddenGraphics.dispose();
        for (int y = 0; y < hidden.getHeight(); y++) for (int x = 0; x < hidden.getWidth(); x++) assertEquals(0, hidden.getRGB(x, y));
        verify(root, never()).setHidden(anyBoolean()); verify(indicator, never()).setHidden(anyBoolean());
        graphics.dispose();
    }
}
