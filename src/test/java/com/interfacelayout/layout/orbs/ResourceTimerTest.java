package com.interfacelayout.layout.orbs;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResourceTimerTest
{
    @Test public void runRecoveryFollowsAgilityAndStopsWhenEnergyDoesNotRecover()
    {
        Client client = mock(Client.class);
        when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(1);
        RunEnergyClock clock = new RunEnergyClock();
        when(client.getEnergy()).thenReturn(5000); clock.tick(client);
        assertTrue(Double.isNaN(clock.recoverySeconds(0)));
        when(client.getEnergy()).thenReturn(5015); clock.tick(client);
        assertEquals(.15, clock.progress(0), 1e-8);
        assertEquals(4985 / (25 + 1.0 / 6), clock.recoverySeconds(0), 1e-8);
        assertTrue(clock.progress(.5) > clock.progress(0));
        clock.tick(client);
        assertEquals(0, clock.progress(.5), 0);
        assertTrue(Double.isNaN(clock.recoverySeconds(.5)));
        when(client.getEnergy()).thenReturn(10000); clock.tick(client);
        assertEquals(-1, clock.recoverySeconds(0), 0);
    }

    @Test public void observedDepletionIncludesEnduranceAndStaminaWithoutInventingCharges()
    {
        Client client = mock(Client.class);
        when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(1);
        RunEnergyClock clock = new RunEnergyClock();
        when(client.getEnergy()).thenReturn(5000); clock.tick(client);
        when(client.getEnergy()).thenReturn(4950); clock.tick(client);
        assertEquals(59.4, clock.depletionSeconds(0), 1e-8);
        assertTrue(clock.progress(.5) < clock.progress(0));
        when(client.getVarbitValue(VarbitID.STAMINA_ACTIVE)).thenReturn(1);
        when(client.getEnergy()).thenReturn(4933); clock.tick(client);
        assertEquals(4933 / 17.0 * .6, clock.depletionSeconds(0), 1e-8);
        when(client.getEnergy()).thenReturn(5933); clock.tick(client);
        assertEquals(0, clock.progress(0), 0);
        assertEquals(-1, clock.depletionSeconds(0), 0);
    }

    @Test public void gracefulPiecesAndSetBonusIncludeTrimmedAgilityCape()
    {
        ItemContainer worn = mock(ItemContainer.class);
        Item[] items = new Item[14];
        items[0] = new Item(ItemID.GRACEFUL_HOOD, 1);
        items[4] = new Item(ItemID.GRACEFUL_TOP, 1);
        items[7] = new Item(ItemID.GRACEFUL_LEGS, 1);
        items[9] = new Item(ItemID.GRACEFUL_GLOVES, 1);
        items[10] = new Item(ItemID.GRACEFUL_BOOTS, 1);
        items[1] = new Item(ItemID.SKILLCAPE_AGILITY_TRIMMED, 1);
        when(worn.getItems()).thenReturn(items);
        assertEquals(30, RunEnergyClock.gracefulBoost(worn));
        items[0] = null;
        assertEquals(17, RunEnergyClock.gracefulBoost(worn));
        assertEquals(0, RunEnergyClock.gracefulBoost(null));
    }

    @Test public void potionClockSynchronizesAfterLoginAndResetsOnDoseOrExpiry()
    {
        PrayerRegenerationClock clock = new PrayerRegenerationClock();
        assertEquals(0, clock.tick(30));
        assertTrue(Double.isNaN(clock.nextTicks(0)));
        assertEquals(1, clock.tick(29));
        assertEquals(12, clock.nextTicks(0), 0);
        for (int i = 0; i < 11; i++) clock.tick(29);
        assertEquals(.5, clock.nextTicks(.5), 0);
        assertEquals(1, clock.tick(28));
        assertEquals(12, clock.nextTicks(0), 0);
        assertEquals(0, clock.tick(66));
        assertEquals(12, clock.nextTicks(0), 0);
        assertEquals(0, clock.tick(0));
        assertEquals(-1, clock.nextTicks(0), 0);
        clock.reset(); clock.tick(0); clock.tick(66);
        assertEquals(12, clock.nextTicks(0), 0);
    }

    @Test public void regenerationDoesNotHideSimultaneousPrayerDrainOrRefillEarly()
    {
        PrayerDrainClock clock = new PrayerDrainClock();
        int points = 80, phase = 0;
        clock.tick(12, 60, points);
        for (int tick = 1; tick < 150; tick++)
        {
            phase += 12;
            int lost = phase / 60;
            phase %= 60;
            int restored = tick % 12 == 0 ? 1 : 0;
            double before = clock.progress(1);
            points += restored - lost;
            clock.tick(12, 60, points, restored);
            if (lost > 0) assertEquals(1, clock.progress(0), 1e-8);
            else assertTrue(clock.progress(0) <= before + 1e-8);
        }
    }

    @Test public void prayerEmptyEstimateIncludesOnlyRemainingPotionPulses()
    {
        PrayerDrainClock clock = new PrayerDrainClock();
        clock.tick(12, 60, 11); clock.tick(12, 60, 10);
        assertEquals(50, clock.emptyTicks(10, 99, -1, 0), 0);
        assertEquals(75, clock.emptyTicks(10, 99, 12, 5), 0);
        assertEquals(5, clock.emptyTicks(1, 99, 12, 5), 0);
        assertTrue(clock.emptyTicks(10, 10, 1, 5) <= 75);
    }

    @Test public void separateNextAndFullEstimatesAccountForLightbearerAndHealingGear()
    {
        Client client = mock(Client.class);
        ItemContainer equipment = mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
        when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.JEWL_BRACELET_REGEN, 1), new Item(ItemID.SKILLCAPE_HITPOINTS, 1)});
        when(equipment.contains(ItemID.LIGHTBEARER)).thenReturn(true);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(89);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(500);
        OrbTimers timers = new OrbTimers();
        long now = 1_000_000_000;
        timers.tick(client, null, now);
        assertEquals(120, timers.seconds(OrbTimerKind.HP_FULL, now) - timers.seconds(OrbTimerKind.HP_NEXT, now), 1e-8);
        assertEquals(60, timers.seconds(OrbTimerKind.SPECIAL_FULL, now) - timers.seconds(OrbTimerKind.SPECIAL_NEXT, now), 1e-8);
        double next = timers.seconds(OrbTimerKind.SPECIAL_NEXT, now);
        assertEquals(next - .6, timers.seconds(OrbTimerKind.SPECIAL_NEXT, now + 90_000_000_000L), 1e-8);
        timers.reset();
        for (OrbTimerKind kind : OrbTimerKind.values()) assertEquals(-1, timers.seconds(kind, now), 0);
    }

    @Test public void numericalTextRoundsUpAndHandlesLongAndUnknownTimers()
    {
        assertEquals("1s", OrbTimerInfoBoxes.format(.1));
        assertEquals("1:01", OrbTimerInfoBoxes.format(60.2));
        assertEquals("2h1m", OrbTimerInfoBoxes.format(7260));
        assertEquals("Sync", OrbTimerInfoBoxes.format(Double.NaN));
    }

    @Test public void inactivePrayersDoNotShowAnEmptyCountdownWhilePotionPhaseSynchronizes()
    {
        Client client=mock(Client.class);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        when(client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER)).thenReturn(40);
        OrbTimers timers=new OrbTimers();long now=1_000_000_000;
        timers.tick(client,null,now);
        assertEquals(-1,timers.seconds(OrbTimerKind.PRAYER_EMPTY,now),0);
        assertTrue(Double.isNaN(timers.seconds(OrbTimerKind.PRAYER_RESTORE,now)));
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        timers.tick(client,null,now+600_000_000);
        assertTrue(Double.isNaN(timers.seconds(OrbTimerKind.PRAYER_EMPTY,now+600_000_000)));
    }
}
