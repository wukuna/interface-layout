/*
 * BSD 2-Clause License
 * 
 * Copyright (c) 2026, Anuraag Venkat
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.interfacelayout.layout.sizing;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbController;
import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.regenmeter.RegenMeterPlugin;
import net.runelite.client.util.Text;

/** Read-only integration. Only our own rings are managed; original rings need manual setup. */
@Singleton
public class OrbRingTimers
{
    @Inject private Client client;
    @Inject private PluginManager plugins;
    @Inject private InterfaceLayoutConfig config;
    @Inject private ConfigManager configs;
    @Inject private OrbController manager;
    private final SpecRingClock clock = new SpecRingClock();
    private RegenMeterPlugin regen;
    private Plugin special;
    private boolean enabled, warned;
    private int region = -1;

    public void reset()
    {
        clock.reset();
        regen = null; special = null;
        enabled = warned = false;
        region = -1;
    }

    @Subscribe(priority = -1)
    public void onGameTick(GameTick event)
    {
        if (!manager.active || !config.orbTimerCompatibility() || client.getGameState() != GameState.LOGGED_IN)
        {
            reset();
            return;
        }
        RegenMeterPlugin nextRegen = null;
        Plugin nextSpecial = null;
        for (Plugin plugin : plugins.getPlugins())
        {
            if (!plugins.isPluginActive(plugin)) continue;
            if (plugin instanceof RegenMeterPlugin) nextRegen = (RegenMeterPlugin) plugin;
            if ("Special Attack Timers".equals(plugin.getName())
                || "Special Attack Timers TEST".equals(plugin.getName())) nextSpecial = plugin;
        }
        regen = nextRegen;
        if (special != nextSpecial)
        {
            special = nextSpecial;
            clock.reset();
            region = -1;
        }
        enabled = true;
        if (special != null)
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            clock.equipment(equipment != null && equipment.contains(ItemID.LIGHTBEARER));
            int nextRegion = templateRegion();
            if (nextRegion != -1)
            {
                if (region != nextRegion && (region == 7216 || doom(region))) clock.resume();
                region = nextRegion;
            }
            Player player = client.getLocalPlayer();
            if (player != null)
            {
                WorldPoint point = player.getWorldLocation();
                if (point.getX() == 1311 && point.getY() == 9556) clock.resume();
            }
            clock.tick(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT), client.getTickCount());
        }
        boolean originals = (regen != null && ((config.orbHpRing() && original("regenmeter", "showHitpoints"))
            || (config.orbSpecRing() && original("regenmeter", "showSpecial"))))
            || (special != null && config.orbSpecRing() && original("specialattacktimers", "showCircularOverlay"));
        if (originals && !warned)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Interface Layout: Resizing Orb Timers (Beta) requires turning OFF the original rings in Regeneration Meter (Show hitpoints regen / Show spec. attack regen) and Special Attack Timers (Show Circular Overlay). Keep the timer plugins enabled.", null);
        }
        warned = originals;
    }

    private boolean original(String group, String key)
    {
        return !"false".equalsIgnoreCase(configs.getConfiguration(group, key));
    }

    public double hp()
    {
        return enabled && config.orbTimerCompatibility() && config.orbHpRing() && regen != null
            && plugins.isPluginActive(regen) ? regen.getHitpointsPercentage() : 0;
    }

    public double spec()
    {
        if (!enabled || !config.orbTimerCompatibility() || !config.orbSpecRing()) return 0;
        if (special != null && plugins.isPluginActive(special)) return clock.progress();
        return regen != null && plugins.isPluginActive(regen) ? regen.getSpecialPercentage() : 0;
    }

    public Color specColor()
    {
        if (special != null)
        {
            Color color = configs.getConfiguration("specialattacktimers", "circleColor", Color.class);
            return color != null ? color : Color.CYAN;
        }
        return new Color(43, 215, 255);
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.LOGGING_IN) reset();
    }

    @Subscribe public void onChatMessage(ChatMessage event)
    {
        if (!tracking() || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
            || event.getMessage() == null) return;
        String message = Text.removeTags(event.getMessage().replaceAll("@[a-z0-9_]+@", ""));
        int tick = client.getTickCount(), currentRegion = templateRegion();
        if (currentRegion == 7216)
        {
            if (message.matches("Wave (1[0-2]|[1-9]) completed!.*")) clock.pause(tick);
            else if (message.matches("Wave: (1[0-2]|[1-9])")) clock.restart(0);
            else if (message.contains("Search the chest nearby")) clock.resume();
        }
        if (doom(currentRegion) && message.startsWith("Delve level:") && message.contains("duration:")) clock.pause(tick);
        if (message.equals("You drink some of your surge potion.")) clock.restore(tick, 250);
        else if (message.contains("Some of your special attack energy has been restored")) clock.restore(tick, 150);
    }

    @Subscribe public void onNpcSpawned(NpcSpawned event)
    {
        if (tracking() && doom(templateRegion()) && event.getNpc().getName() != null
            && event.getNpc().getName().contains("Doom")) clock.restart(2);
    }

    private boolean tracking()
    {
        return manager.active && config.orbTimerCompatibility() && enabled && special != null
            && plugins.isPluginActive(special) && client.getGameState() == GameState.LOGGED_IN;
    }

    private static boolean doom(int region) { return region == 13668 || region == 14180; }
    private int templateRegion()
    {
        Player player = client.getLocalPlayer();
        if (player == null) return -1;
        WorldPoint point = player.getWorldLocation();
        if (client.isInInstancedRegion())
        {
            LocalPoint local = LocalPoint.fromWorld(client, point);
            if (local != null)
            {
                WorldPoint template = WorldPoint.fromLocalInstance(client, local);
                if (template != null) point = template;
            }
        }
        return point.getRegionID();
    }
}
