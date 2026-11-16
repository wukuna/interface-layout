/*
 * Copyright (c) 2018, Sean Dewar <https://github.com/seandewar>
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

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemVariationMapping;

final class RunEnergyClock
{
    private static final int[] SLOTS = {0, 4, 7, 9, 10, 1};
    private static final int[] GRACEFUL = {ItemID.GRACEFUL_HOOD, ItemID.GRACEFUL_TOP, ItemID.GRACEFUL_LEGS,
        ItemID.GRACEFUL_GLOVES, ItemID.GRACEFUL_BOOTS, ItemID.GRACEFUL_CAPE};
    private static final int[] BOOST = {3, 4, 4, 3, 3, 3};
    private int previous = -1;
    private int energy;
    private int direction;
    private double unitsPerTick;

    void reset()
    {
        previous = -1;
        energy = direction = 0;
        unitsPerTick = 0;
    }

    void tick(Client client)
    {
        int current = Math.max(0, Math.min(10000, client.getEnergy()));
        int agility = Math.max(1, client.getBoostedSkillLevel(Skill.AGILITY));
        double recovery = (25 + agility / 6.0) * (1 + gracefulBoost(client.getItemContainer(InventoryID.WORN)) / 100.0) * .6;
        int weight = Math.max(0, Math.min(64, client.getWeight()));
        int depletion = Math.max(1, (int) ((60 + 67 * weight / 64) * (1 - agility / 300.0)));
        if (client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0) depletion = Math.max(1, (int) (depletion * .3));
        int change = previous < 0 ? 0 : current - previous;
        direction = 0;
        unitsPerTick = 0;
        if (change > 0 && change <= Math.ceil(recovery) + 1)
        {
            direction = 1;
            unitsPerTick = recovery;
        }
        else if (change < 0 && -change <= depletion + 1)
        {
            direction = -1;
            unitsPerTick = -change;
        }
        previous = energy = current;
    }

    static int gracefulBoost(ItemContainer equipment)
    {
        if (equipment == null || equipment.getItems() == null) return 0;
        Item[] worn = equipment.getItems();
        int boost = 0;
        for (int i = 0; i < SLOTS.length; i++)
        {
            Item item = SLOTS[i] < worn.length ? worn[SLOTS[i]] : null;
            if (item == null) continue;
            int id = ItemVariationMapping.map(item.getId());
            if (id == ItemVariationMapping.map(GRACEFUL[i]) || i == 5
                && (id == ItemVariationMapping.map(ItemID.SKILLCAPE_AGILITY) || id == ItemVariationMapping.map(ItemID.SKILLCAPE_MAX_WORN)))
                boost += BOOST[i];
        }
        return boost == 20 ? 30 : boost;
    }

    double progress(double fraction)
    {
        if (direction == 0 || energy <= 0 || energy >= 10000) return 0;
        double part = energy % 100;
        if (direction < 0 && part == 0) part = 100;
        return Math.max(0, Math.min(1, (part + direction * unitsPerTick * fraction) / 100));
    }

    double recoverySeconds(double fraction)
    {
        if (energy >= 10000) return -1;
        return direction > 0 ? Math.max(0, ((10000 - energy) / unitsPerTick - fraction) * .6) : Double.NaN;
    }

    double depletionSeconds(double fraction)
    {
        return direction < 0 && energy > 0 ? Math.max(0, (energy / unitsPerTick - fraction) * .6) : -1;
    }
}
