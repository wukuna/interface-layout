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
    private final PrayerDrainClock prayer = new PrayerDrainClock();
    private long lastTick;
    private boolean ready;
    private final RunEnergyClock run = new RunEnergyClock();
    private final PrayerRegenerationClock regeneration = new PrayerRegenerationClock();
    private int currentHitpoints, maximumHitpoints, healing = 1;
    private int specialEnergy, currentPrayer, maximumPrayer;
    static final Color RUN_COLOR = new Color(239, 208, 87);
    static final Color REGENERATION_COLOR = new Color(139, 231, 174);

    void reset()
    {
        hpTicks = -2;
        hpPeriod = 100;
        hpDirection = 0;
        specialTicks = 0;
        specialPeriod = 50;
        specialActive = false;
        lightbearer = false;
        prayer.reset();
        run.reset();
        regeneration.reset();
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
        specialEnergy = Math.max(0, Math.min(1000, client.getVarpValue(VarPlayerID.SA_ENERGY)));
        specialActive = specialEnergy < 1000;
        specialTicks = specialActive ? (specialTicks + 1) % specialPeriod : 0;

        boolean hpCape = worn(equipment, ItemID.SKILLCAPE_HITPOINTS) || worn(equipment, ItemID.SKILLCAPE_MAX_WORN);
        hpPeriod = client.isPrayerActive(Prayer.RAPID_HEAL) || hpCape ? 50 : 100;
        healing = worn(equipment, ItemID.JEWL_BRACELET_REGEN) ? 2 : 1;
        hpTicks = (hpTicks + 1) % hpPeriod;
        currentHitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
        maximumHitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
        hpDirection = Integer.compare(maximumHitpoints, currentHitpoints);

        int drain = PrayerDrain.rate(client);
        int points = client.getBoostedSkillLevel(Skill.PRAYER);
        int bonus = 0;
        if (drain > 0 && equipment != null)
        {
            for (Item item : equipment.getItems())
            {
                if (item == null || item.getId() < 0) continue;
                ItemStats stats = items == null ? null : items.getItemStats(item.getId());
                if (stats != null && stats.getEquipment() != null) bonus += stats.getEquipment().getPrayer();
            }
        }
        maximumPrayer = client.getRealSkillLevel(Skill.PRAYER);
        int restored = regeneration.tick(client.getVarbitValue(net.runelite.api.gameval.VarbitID.PRAYER_REGENERATION_POTION_TIMER));
        if (restored > 0 && currentPrayer >= maximumPrayer && points >= maximumPrayer) restored = -1;
        currentPrayer = points;
        prayer.tick(drain,60+2*bonus,points,restored);
        run.tick(client);
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
        if (component == InterfaceID.Orbs.ORB_PRAYER)return prayer.progress(fraction);
        if (component == InterfaceID.Orbs.ORB_RUNENERGY)return run.progress(fraction);
        return 0;
    }

    void paint(Graphics2D graphics, Rectangle circle, int component, InterfaceLayoutConfig config, long now)
    {
        paint(graphics,circle,component,config,now,0,0);
    }

    void paint(Graphics2D graphics,Rectangle circle,int component,InterfaceLayoutConfig config,long now,int currentPrayer,int maximumPrayer)
    {
        paintDrain(graphics,circle,component,config,now,currentPrayer,maximumPrayer);
        paintRegeneration(graphics,circle,null,component,config,now);
    }

    private void paintDrain(Graphics2D graphics,Rectangle circle,int component,InterfaceLayoutConfig config,long now,int currentPrayer,int maximumPrayer)
    {
        Color color = color(component, config);
        if (color == null) return;
        double progress = progress(component, now);
        if(OrbTimerStyles.resolve(config,component)!=InterfaceLayoutConfig.NativeTimerStyle.QUIET_RIMS) {
            NativeOrbTimers.paintCircular(graphics,circle,component,config,color,progress,currentPrayer,maximumPrayer);
            return;
        }
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

    void paintInset(Graphics2D graphics, Rectangle inset, Rectangle number, int component,
        InterfaceLayoutConfig config, long now, int currentPrayer, int maximumPrayer)
    {
        Color color = color(component, config);
        if (color != null) NativeOrbTimers.paint(graphics,inset,number,component,config,color,
            progress(component,now),currentPrayer,maximumPrayer);
        paintRegeneration(graphics,inset,number,component,config,now);
    }

    private static Color color(int component, InterfaceLayoutConfig config)
    {
        if (config.disableOrbTimers()) return null;
        if (component == InterfaceID.Orbs.ORB_HEALTH && config.orbHitpointsTimer()) return HITPOINTS_COLOR;
        if (component == InterfaceID.Orbs.ORB_SPECENERGY && config.orbSpecialTimer()) return SPECIAL_COLOR;
        if (component == InterfaceID.Orbs.ORB_PRAYER && config.orbPrayerTimer()) return PRAYER_COLOR;
        if (component == InterfaceID.Orbs.ORB_RUNENERGY && config.orbRunTimer()) return RUN_COLOR;
        return null;
    }

    private static boolean worn(ItemContainer equipment,int base)
    {
        if(equipment==null || equipment.getItems()==null)return false;
        int mapped=net.runelite.client.game.ItemVariationMapping.map(base);
        for(Item item:equipment.getItems())if(item!=null && item.getId()>0
            && net.runelite.client.game.ItemVariationMapping.map(item.getId())==mapped)return true;
        return false;
    }

    private double fraction(long now)
    {
        return Math.max(0,Math.min(1,(now-lastTick)/(double)TICK_NANOS));
    }

    double seconds(OrbTimerKind kind,long now)
    {
        if(!ready)return -1;
        double fraction=fraction(now);
        switch(kind)
        {
            case HP_NEXT:
            case HP_FULL:
                if(hpDirection<=0 || currentHitpoints<=0)return -1;
                int heals=(maximumHitpoints-currentHitpoints+healing-1)/healing;
                return Math.max(0,hpPeriod-Math.max(0,hpTicks)-fraction
                    +(kind==OrbTimerKind.HP_FULL?(heals-1)*hpPeriod:0))*.6;
            case SPECIAL_NEXT:
            case SPECIAL_FULL:
                if(!specialActive)return -1;
                int restores=(1000-specialEnergy+99)/100;
                return Math.max(0,specialPeriod-specialTicks-fraction
                    +(kind==OrbTimerKind.SPECIAL_FULL?(restores-1)*specialPeriod:0))*.6;
            case RUN_FULL:return run.recoverySeconds(fraction);
            case RUN_EMPTY:return run.depletionSeconds(fraction);
            case PRAYER_RESTORE:
                double ticks=regeneration.nextTicks(fraction);
                return ticks<0?-1:ticks*.6;
            case PRAYER_EMPTY:
                double empty=prayer.emptyTicks(currentPrayer,maximumPrayer,regeneration.nextTicks(0),regeneration.remainingRestores());
                if(empty>=0 && regeneration.active() && !Double.isFinite(regeneration.nextTicks(0)))return Double.NaN;
                return empty<0?-1:Math.max(0,empty-fraction)*.6;
            default:return -1;
        }
    }

    private void paintRegeneration(Graphics2D graphics,Rectangle bounds,Rectangle number,int component,
        InterfaceLayoutConfig config,long now)
    {
        if(ready && component==InterfaceID.Orbs.ORB_PRAYER && !config.disableOrbTimers() && config.orbPrayerRegenerationTimer())
            NativeOrbTimers.paintRegeneration(graphics,bounds,number,config,REGENERATION_COLOR,regeneration.progress(fraction(now)));
    }

    static void paintWedge(Graphics2D graphics, Rectangle inset, Color color, double progress)
    {
        if (!Double.isFinite(progress) || progress <= 0 || inset.width < 8 || inset.height < 8) return;
        progress = Math.min(1, progress);
        Graphics2D draw = (Graphics2D) graphics.create();
        try
        {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.clip(new java.awt.geom.Ellipse2D.Double(inset.x, inset.y, inset.width, inset.height));
            draw.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 52));
            draw.fill(new Arc2D.Double(inset.x, inset.y, inset.width, inset.height, 90, -360 * progress, Arc2D.PIE));
            if (progress < 1)
            {
                double angle = 2 * Math.PI * progress;
                draw.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 112));
                draw.setStroke(new BasicStroke(.8f));
                draw.draw(new java.awt.geom.Line2D.Double(inset.getCenterX(), inset.getCenterY(),
                    inset.getCenterX() + Math.sin(angle) * inset.width / 2,
                    inset.getCenterY() - Math.cos(angle) * inset.height / 2));
            }
        }
        finally
        {
            draw.dispose();
        }
    }
}
