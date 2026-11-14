package com.interfacelayout.layout.orbs;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrayerDrainSynchronizationTest {
    private static final int PRAYER=InterfaceID.Orbs.ORB_PRAYER;
    private static final long TICK=600_000_000L;

    @Test public void observedLossesDoNotRepeatedlyRefillAPartlyDrainedRingAtAnyStartingPhase() {
        Client client=mock(Client.class,withSettings().stubOnly());ItemManager items=mock(ItemManager.class,withSettings().stubOnly());
        ItemContainer equipment=mock(ItemContainer.class,withSettings().stubOnly());
        java.util.concurrent.atomic.AtomicInteger sampledPoints=new java.util.concurrent.atomic.AtomicInteger();
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenAnswer(call->sampledPoints.get());
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
        when(equipment.getItems()).thenReturn(new Item[]{new Item(100,1)});
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        when(client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        for(int bonus:new int[]{0,5,11,19,30,47}) {
            when(items.getItemStats(100)).thenReturn(new ItemStats(true,0,0,ItemEquipmentStats.builder().prayer(bonus).build()));
            int resistance=60+2*bonus;
            for(int initial:new int[]{0,resistance/3,resistance/2,resistance-1}) {
                OrbTimers timer=new OrbTimers();int charge=initial,points=99;long now=TICK;
                sampledPoints.set(points);timer.tick(client,items,now);
                boolean observed=false;
                for(int tick=1;tick<50;tick++) {
                    charge+=36;int lost=charge/resistance;charge%=resistance;
                    double before=timer.progress(PRAYER,now+TICK);
                    if(lost>0 && observed)assertEquals("bonus="+bonus+", phase="+initial+", tick="+tick,0,before,1e-8);
                    points-=lost;now+=TICK;
                    sampledPoints.set(points);timer.tick(client,items,now);
                    if(lost>0) {assertEquals(1,timer.progress(PRAYER,now),1e-8);observed=true;}
                    else assertTrue(timer.progress(PRAYER,now)<=before+1e-8);
                }
            }
        }
    }

    @Test public void everyStartingPhaseAndMixedDrainRateCompletesBeforeTheNextObservedLoss() {
        for(int resistance:new int[]{60,70,82,98,120,154})for(int rate:new int[]{1,12,18,24,36,54,72})
            for(int initial=0;initial<resistance;initial++) {
                PrayerDrainClock clock=new PrayerDrainClock();int charge=initial,points=1000;
                clock.tick(rate,resistance,points);boolean observed=false;
                for(int tick=1;tick<400;tick++) {
                    charge+=rate;int lost=charge/resistance;charge%=resistance;
                    double before=clock.progress(1);
                    if(lost>0 && observed)assertEquals(0,before,1e-8);
                    points-=lost;clock.tick(rate,resistance,points);
                    if(lost>0) {assertEquals(1,clock.progress(0),1e-8);observed=true;}
                    else assertTrue(clock.progress(0)<=before+1e-8);
                }
            }
    }

    @Test public void unevenTickDeliveryDoesNotRefillBeforeAnObservedLoss() {
        PrayerDrainClock clock=new PrayerDrainClock();clock.tick(12,82,99);
        double previous=1;
        for(int tick=0;tick<6;tick++) {
            for(double fraction:new double[]{0,.2,.6,1,1.3}) {
                double progress=clock.progress(fraction);
                assertTrue(progress<=previous+1e-8);previous=progress;
            }
            clock.tick(12,82,99);
        }
        clock.tick(12,82,98);assertEquals(1,clock.progress(0),1e-8);
    }

    @Test public void potionGainsDoNotRefillTheCountdownAndDrainChangesRestartWithTheNewRate() {
        PrayerDrainClock clock=new PrayerDrainClock();clock.tick(36,82,80);
        clock.tick(36,82,80);clock.tick(36,82,79);
        double before=clock.progress(1);clock.tick(36,82,99);
        assertTrue(clock.progress(0)<=before+1e-8);
        clock.tick(12,82,99);assertEquals(1,clock.progress(0),1e-8);
        clock.tick(0,82,99);assertEquals(0,clock.progress(.5),1e-8);
        clock.tick(12,82,99);assertEquals(1,clock.progress(0),1e-8);
        clock.tick(12,82,0);assertEquals(0,clock.progress(.5),1e-8);
        clock.reset();assertEquals(0,clock.progress(0),1e-8);
    }

    @Test public void unexpectedExternalLossAndMissedUpdatesRecoverWithoutInventingRefills() {
        PrayerDrainClock clock=new PrayerDrainClock();clock.tick(36,82,99);
        clock.tick(36,82,80);assertEquals(1,clock.progress(0),1e-8);
        double previous=1;
        for(int tick=0;tick<12;tick++) {
            clock.tick(36,82,80);double progress=clock.progress(0);
            assertTrue(progress<=previous+1e-8);assertTrue(progress>=0);previous=progress;
        }
        clock.tick(36,82,79);assertEquals(1,clock.progress(0),1e-8);
    }
}
