package com.interfacelayout.layout.equipment;

import com.interfacelayout.InterfaceLayoutConfig;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EquipmentReadoutTest
{
    @Test public void sumsCurrentItemStatesWithoutMultiplyingAmmoStacks()
    {
        Client client=mock(Client.class); ItemManager manager=mock(ItemManager.class);
        ItemContainer worn=mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItems()).thenReturn(new Item[]{new Item(100,1),new Item(101,2223),new Item(-1,0)});
        when(manager.getItemStats(100)).thenReturn(new ItemStats(true,1,0,
            ItemEquipmentStats.builder().astab(71).amagic(-11).mdmg(2.5f).prayer(8).build()));
        when(manager.getItemStats(101)).thenReturn(new ItemStats(true,1,0,
            ItemEquipmentStats.builder().astab(3).rstr(60).mdmg(1).build()));
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(3);
        when(client.getWeight()).thenReturn(61);
        EquipmentReadout result=EquipmentReadout.capture(client,manager);
        assertTrue(result.complete);
        assertEquals("+74",result.value(0)); assertEquals("-11",result.value(3));
        assertEquals("+60",result.value(11)); assertEquals("+3.5%",result.value(12));
        assertEquals("Arceuus",result.spellbook); assertEquals(61,result.weight);
        verify(manager,never()).canonicalize(anyInt());
    }

    @Test public void missingContainerOrAnUnknownEquippedItemDoesNotInventTotals()
    {
        Client client=mock(Client.class); ItemManager manager=mock(ItemManager.class);
        assertEquals("?",EquipmentReadout.capture(client,manager).value(0));
        ItemContainer worn=mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItems()).thenReturn(new Item[]{new Item(100,1)});
        assertEquals("?",EquipmentReadout.capture(client,manager).value(12));
        when(worn.getItems()).thenReturn(new Item[0]);
        assertEquals("0%",EquipmentReadout.capture(client,manager).value(12));
    }

    @Test public void spellbooksAndOptInDefaultAreExplicit()
    {
        assertArrayEquals(new String[]{"Standard","Ancient","Lunar","Arceuus","Unknown"},
            new String[]{EquipmentReadout.book(0),EquipmentReadout.book(1),EquipmentReadout.book(2),EquipmentReadout.book(3),EquipmentReadout.book(9)});
        assertFalse(new InterfaceLayoutConfig() {}.betaEquipmentPanel());
    }
}
