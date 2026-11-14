package com.interfacelayout.layout.orbs;

import java.lang.reflect.Method;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrbTimersTest
{
    private static final int HP = InterfaceID.Orbs.ORB_HEALTH;
    private static final int SPEC = InterfaceID.Orbs.ORB_SPECENERGY;
    private static final int PRAYER = InterfaceID.Orbs.ORB_PRAYER;
    private final Client client = mock(Client.class);
    private final ItemManager items = mock(ItemManager.class);
    private final OrbTimers timers = new OrbTimers();
    private long now = 1_000_000_000L;

    public OrbTimersTest()
    {
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(80);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(500);
    }

    private void ticks(int count)
    {
        for (int i = 0; i < count; i++) timers.tick(client, items, now += 600_000_000L);
    }

    private void progress(int component, double expected)
    {
        assertEquals(expected, timers.progress(component, now), 0.000001);
    }

    @Test public void naturalHpRegenerationWrapsAtOneMinuteAndHidesAtFullHealth()
    {
        ticks(52); progress(HP, .5);
        ticks(50); progress(HP, 0);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        ticks(20); progress(HP, 0);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        ticks(1); progress(HP, .21);
    }

    @Test public void rapidHealRestartsTheCycleAndOverhealedHpRunsDown()
    {
        ticks(52);
        when(client.isPrayerActive(Prayer.RAPID_HEAL)).thenReturn(true);
        timers.resetHitpoints(); ticks(25); progress(HP, .5);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(110);
        ticks(5); progress(HP, .4);
        when(client.isPrayerActive(Prayer.RAPID_HEAL)).thenReturn(false);
        timers.resetHitpoints(); ticks(1); progress(HP, .99);
    }

    @Test public void specialCyclePausesAtFullEnergyAndRestartsOnUse()
    {
        ticks(25); progress(SPEC, .5);
        ticks(25); progress(SPEC, 0);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(1000);
        ticks(10); progress(SPEC, 0);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(250);
        ticks(1); progress(SPEC, .02);
    }

    @Test public void lightbearerPreservesRemainingTimeWhenEquippedLateInTheCycle()
    {
        ItemContainer worn = mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        ticks(40); progress(SPEC, .8);
        when(worn.contains(ItemID.LIGHTBEARER)).thenReturn(true);
        ticks(1); progress(SPEC, 16.0 / 25);
        ticks(9); progress(SPEC, 0);
        when(worn.contains(ItemID.LIGHTBEARER)).thenReturn(false);
        ticks(1); progress(SPEC, .02);
    }

    @Test public void interpolationStopsDuringLagAndCannotRunBackwards()
    {
        ticks(25);
        assertEquals(.51, timers.progress(SPEC, now + 300_000_000L), .000001);
        assertEquals(.52, timers.progress(SPEC, now + 60_000_000_000L), .000001);
        assertEquals(.5, timers.progress(SPEC, now - 10_000L), .000001);
    }

    private ItemContainer prayerBonus(int bonus)
    {
        ItemContainer worn = mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItems()).thenReturn(new Item[]{new Item(100, 1), new Item(-1, 0)});
        when(items.getItemStats(100)).thenReturn(new ItemStats(true, 0, 0,
            ItemEquipmentStats.builder().prayer(bonus).build()));
        return worn;
    }

    @Test public void prayersAddTogetherAndEquipmentBonusSlowsTheirDrain()
    {
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        when(client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        ticks(2); progress(PRAYER, .5);
        prayerBonus(30); ticks(1); progress(PRAYER, 1);
        when(client.isPrayerActive(Prayer.PIETY)).thenReturn(false);
        ticks(1); progress(PRAYER, 1);
    }

    @Test public void observedDrainStartsAFullCycleAndPotionGainsKeepThePhase()
    {
        prayerBonus(5);
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        ticks(6); progress(PRAYER, 1.0 / 6);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(79);
        ticks(1); progress(PRAYER, 1);
        double afterOneTick=timers.progress(PRAYER,now+600_000_000L);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(99);
        ticks(1); progress(PRAYER, afterOneTick);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(70);
        ticks(1); progress(PRAYER, 1);
    }

    @Test public void rapidDrainRestartsAtFullInsteadOfKeepingHalfARing()
    {
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        when(client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        ticks(1);progress(PRAYER,1);
        ticks(1);progress(PRAYER,.5);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(79);
        ticks(1);progress(PRAYER,1);
        ticks(1);progress(PRAYER,0);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(78);
        ticks(1);progress(PRAYER,1);
        assertEquals(.5,timers.progress(PRAYER,now+300_000_000L),.000001);
        assertEquals(0,timers.progress(PRAYER,now+600_000_000L),.000001);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(77);
        ticks(1);progress(PRAYER,1);
        assertEquals(.75,timers.progress(PRAYER,now+300_000_000L),.000001);
    }

    @Test public void inactiveOrExhaustedPrayerHasNoDrainRing()
    {
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        ticks(3); progress(PRAYER, .6);
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(false);
        ticks(1); progress(PRAYER, 0);
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        ticks(1); progress(PRAYER, 1);
        ticks(1); progress(PRAYER, .8);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
        ticks(1); progress(PRAYER, 0);
    }

    @Test public void unlistedEquipmentDoesNotDisableThePrayerCountdown()
    {
        prayerBonus(30);
        when(items.getItemStats(100)).thenReturn(null);
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        ticks(2); progress(PRAYER, .8);
        prayerBonus(30); ticks(1); progress(PRAYER, 1);
    }

    @Test public void resetClearsEveryRingAndItsClock()
    {
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        ticks(3);
        for (int component : new int[]{HP, SPEC, PRAYER}) assertTrue(timers.progress(component, now) > 0);
        timers.reset();
        for (int component : new int[]{HP, SPEC, PRAYER}) progress(component, 0);
        ticks(1); progress(HP, 0); progress(SPEC, .02); progress(PRAYER, 1);
    }

    @Test public void drainRatesAndUnlockedPrayerAliasesMatchRuneLite() throws Exception
    {
        Class<?> type = Class.forName("net.runelite.client.plugins.prayer.PrayerType");
        Method active = type.getDeclaredMethod("isActive", Client.class);
        Method rate = type.getDeclaredMethod("getDrainEffect");
        active.setAccessible(true); rate.setAccessible(true);
        for (int variant = 0; variant < 4; variant++)
        {
            when(client.getVarbitValue(VarbitID.BR_INGAME)).thenReturn(variant / 2);
            when(client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED)).thenReturn(variant % 2);
            when(client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED)).thenReturn(variant % 2);
            when(client.getVarbitValue(VarbitID.PRAYER_ZEAL_UNLOCKED)).thenReturn(variant % 2);
            for (Prayer selected : Prayer.values())
            {
                when(client.isPrayerActive(any(Prayer.class))).thenAnswer(call -> call.getArgument(0) == selected);
                int expected = 0;
                for (Object prayer : type.getEnumConstants())
                    if ((boolean) active.invoke(prayer, client)) expected += (int) rate.invoke(prayer);
                assertEquals(selected + " variant " + variant, expected, PrayerDrain.rate(client));
            }
            when(client.isPrayerActive(any(Prayer.class))).thenReturn(true);
            int expected = 0;
            for (Object prayer : type.getEnumConstants())
                if ((boolean) active.invoke(prayer, client)) expected += (int) rate.invoke(prayer);
            assertEquals(expected, PrayerDrain.rate(client));
        }
    }
}
