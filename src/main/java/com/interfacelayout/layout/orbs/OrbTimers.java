/*
 * Copyright (c) 2019, Sean Dewar <https://github.com/seandewar>
 * Copyright (c) 2018, Abex
 * Copyright (c) 2018, Zimaya <https://github.com/Zimaya>
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

final class OrbTimers
{
    static final Color HITPOINTS_COLOR = new Color(255, 12, 5);
    static final Color SPECIAL_COLOR = new Color(0, 207, 255);
    static final Color PRAYER_COLOR = new Color(120, 185, 255);
    private static final long TICK_NANOS = Constants.GAME_TICK_LENGTH * 1_000_000L;
    private int hpTicks = -2;
    private int hpPeriod = 100;
    private int hpDirection;
    private int specialTicks;
    private int specialPeriod = 50;
    private boolean specialActive;
    private boolean lightbearer;
    private int prayerCharge;
    private int prayerDrain;
    private int prayerResistance = 60;
    private int previousPrayer = -1;
    private long lastTick;
    private boolean ready;

    void reset()
    {
        hpTicks = -2;
        hpPeriod = 100;
        hpDirection = 0;
        specialTicks = 0;
        specialPeriod = 50;
        specialActive = false;
        lightbearer = false;
        prayerCharge = 0;
        prayerDrain = 0;
        prayerResistance = 60;
        previousPrayer = -1;
        ready = false;
    }

    void resetHitpoints()
    {
        hpTicks = 0;
    }

    void tick(Client client, ItemManager items, long now)
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        boolean equipped = equipment != null && equipment.contains(ItemID.LIGHTBEARER);
        if (equipped != lightbearer)
        {
            specialTicks = Math.max(0, specialTicks - 25);
            lightbearer = equipped;
        }
        specialPeriod = lightbearer ? 25 : 50;
        specialActive = client.getVarpValue(VarPlayerID.SA_ENERGY) < 1000;
        specialTicks = specialActive ? (specialTicks + 1) % specialPeriod : 0;

        hpPeriod = client.isPrayerActive(Prayer.RAPID_HEAL) ? 50 : 100;
        hpTicks = (hpTicks + 1) % hpPeriod;
        hpDirection = Integer.compare(client.getRealSkillLevel(Skill.HITPOINTS),
            client.getBoostedSkillLevel(Skill.HITPOINTS));

        int drain = PrayerDrain.rate(client);
        int points = client.getBoostedSkillLevel(Skill.PRAYER);
        int bonus = 0;
        boolean statsReady = true;
        if (drain > 0 && equipment != null)
        {
            for (Item item : equipment.getItems())
            {
                if (item == null || item.getId() < 0) continue;
                ItemStats stats = items == null ? null : items.getItemStats(item.getId());
                if (stats == null) statsReady = false;
                else if (stats.getEquipment() != null) bonus += stats.getEquipment().getPrayer();
            }
        }
        prayerResistance = Math.max(1, 60 + 2 * bonus);
        if (drain == 0 || prayerDrain == 0 || points <= 0 || !statsReady)
        {
            prayerCharge = 0;
        }
        else
        {
            int charge = prayerCharge + drain;
            int lost = previousPrayer - points;
            prayerCharge = lost > 0 && lost != charge / prayerResistance
                ? 0 : charge % prayerResistance;
        }
        prayerDrain = points > 0 && statsReady ? drain : 0;
        previousPrayer = points;
        lastTick = now;
        ready = true;
    }

    double progress(int component, long now)
    {
        if (!ready) return 0;
        double fraction = Math.max(0, Math.min(1, (now - lastTick) / (double) TICK_NANOS));
        if (component == InterfaceID.Orbs.ORB_HEALTH && hpDirection != 0 && hpTicks >= 0)
        {
            double value = Math.min(1, (hpTicks + fraction) / hpPeriod);
            return hpDirection > 0 ? value : 1 - value;
        }
        if (component == InterfaceID.Orbs.ORB_SPECENERGY && specialActive)
            return Math.min(1, (specialTicks + fraction) / specialPeriod);
        if (component == InterfaceID.Orbs.ORB_PRAYER && prayerDrain > 0)
            return 1 - Math.min(1, (prayerCharge + fraction * prayerDrain) / prayerResistance);
        return 0;
    }

    void paint(Graphics2D graphics, Rectangle circle, int component, InterfaceLayoutConfig config, long now)
    {
        Color color;
        if (component == InterfaceID.Orbs.ORB_HEALTH && config.orbHitpointsTimer()) color = HITPOINTS_COLOR;
        else if (component == InterfaceID.Orbs.ORB_SPECENERGY && config.orbSpecialTimer()) color = SPECIAL_COLOR;
        else if (component == InterfaceID.Orbs.ORB_PRAYER && config.orbPrayerTimer()) color = PRAYER_COLOR;
        else return;
        double progress = progress(component, now);
        if (progress <= 0 || circle.width <= 10 || circle.height <= 10) return;
        Graphics2D draw = (Graphics2D) graphics.create();
        try
        {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw.setStroke(new BasicStroke(2f));
            draw.setColor(color);
            int inset = component == InterfaceID.Orbs.ORB_SPECENERGY ? 3 : 4;
            draw.draw(new Arc2D.Double(circle.x + inset, circle.y + inset, circle.width - 2 * inset,
                circle.height - 2 * inset, 90, -360 * progress, Arc2D.OPEN));
        }
        finally
        {
            draw.dispose();
        }
    }
}
