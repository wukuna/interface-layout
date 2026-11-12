/*
 * Copyright (c) 2018, Raqes <j.raqes@gmail.com>
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
import net.runelite.api.Prayer;
import net.runelite.api.gameval.VarbitID;

final class PrayerDrain
{
    private PrayerDrain() {}

    static int rate(Client client)
    {
        int total = 0;
        boolean lms = client.getVarbitValue(VarbitID.BR_INGAME) != 0;
        boolean deadeye = !lms && client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0;
        boolean vigour = !lms && client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0;
        boolean zeal = lms || client.getVarbitValue(VarbitID.PRAYER_ZEAL_UNLOCKED) != 0;
        for (Prayer prayer : Prayer.values())
        {
            if (prayer == Prayer.EAGLE_EYE && deadeye || prayer == Prayer.DEADEYE && !deadeye
                || prayer == Prayer.MYSTIC_MIGHT && vigour || prayer == Prayer.MYSTIC_VIGOUR && !vigour
                || prayer == Prayer.PIETY && zeal || prayer == Prayer.ZEAL && !zeal) continue;
            if (client.isPrayerActive(prayer)) total += cost(prayer);
        }
        return total;
    }

    private static int cost(Prayer prayer)
    {
        switch (prayer)
        {
            case THICK_SKIN:
            case BURST_OF_STRENGTH:
            case CLARITY_OF_THOUGHT:
            case SHARP_EYE:
            case MYSTIC_WILL:
            case RAPID_RESTORE:
            case RP_RUINOUS_GRACE:
                return 1;
            case RAPID_HEAL:
            case PROTECT_ITEM:
            case PRESERVE:
            case RP_BERSERKER:
                return 2;
            case RETRIBUTION:
            case RP_WRATH:
                return 3;
            case RP_REJUVENATION:
                return 4;
            case ROCK_SKIN:
            case SUPERHUMAN_STRENGTH:
            case IMPROVED_REFLEXES:
            case HAWK_EYE:
            case MYSTIC_LORE:
            case REDEMPTION:
                return 6;
            case RP_VINDICATION:
                return 9;
            case STEEL_SKIN:
            case ULTIMATE_STRENGTH:
            case INCREDIBLE_REFLEXES:
            case PROTECT_FROM_MAGIC:
            case PROTECT_FROM_MISSILES:
            case PROTECT_FROM_MELEE:
            case EAGLE_EYE:
            case MYSTIC_MIGHT:
            case CHIVALRY:
            case DEADEYE:
            case MYSTIC_VIGOUR:
            case RP_METABOLISE:
            case RP_REBUKE:
                return 12;
            case RP_DAMPEN_MAGIC:
            case RP_DAMPEN_RANGED:
            case RP_DAMPEN_MELEE:
            case RP_FUMUS_VOW:
            case RP_UMBRA_VOW:
            case RP_CRUORS_VOW:
            case RP_GLACIES_VOW:
                return 14;
            case SMITE:
            case RP_ANCIENT_STRENGTH:
            case RP_ANCIENT_SIGHT:
            case RP_ANCIENT_WILL:
            case RP_PROTECT_ITEM:
            case RP_PURGE:
                return 18;
            case RP_TRINITAS:
                return 22;
            case PIETY:
            case ZEAL:
            case RIGOUR:
            case AUGURY:
                return 24;
            case RP_DECIMATE:
            case RP_ANNIHILATE:
            case RP_VAPORISE:
            case RP_INTENSIFY:
                return 28;
            default:
                return 0;
        }
    }
}
