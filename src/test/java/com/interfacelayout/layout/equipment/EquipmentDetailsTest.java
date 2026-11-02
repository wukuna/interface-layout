package com.interfacelayout.layout.equipment;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EquipmentDetailsTest {
    @Test public void speedFollowsRapidCastingAndWeaponEffects() {
        assertEquals(3,EquipmentDetails.adjustedSpeed(4,"Magic shortbow","Rapid",1,0));
        assertEquals(4,EquipmentDetails.adjustedSpeed(4,"Magic shortbow","Accurate",0,0));
        assertEquals(5,EquipmentDetails.adjustedSpeed(4,"Ancient staff","Casting",4,1));
        assertEquals(4,EquipmentDetails.adjustedSpeed(5,"Harmonised nightmare staff","Casting",4,0));
        assertEquals(5,EquipmentDetails.adjustedSpeed(5,"Harmonised nightmare staff","Casting",4,1));
        assertEquals(6,EquipmentDetails.adjustedSpeed(6,"Twinflame staff","Casting",4,0));
        assertEquals(3,EquipmentDetails.adjustedSpeed(6,"Obligator","Aggressive",2,0));
        assertEquals(6,EquipmentDetails.adjustedSpeed(6,"Obligator","Accurate",0,0));
        assertEquals(0,EquipmentDetails.adjustedSpeed(4,"Bow",null,0,0));
        assertEquals(0,EquipmentDetails.adjustedSpeed(0,"Unknown","Rapid",1,0));
    }
    @Test public void capturesCurrentGameSetAndStyleWithoutOpeningNativeStats() {
        Client client=mock(Client.class);ItemManager manager=mock(ItemManager.class);
        ItemContainer worn=mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItems()).thenReturn(new Item[]{null,null,null,new Item(123,1)});
        ItemComposition definition=mock(ItemComposition.class);when(definition.getName()).thenReturn("Magic shortbow");
        when(manager.getItemComposition(123)).thenReturn(definition);
        when(manager.getItemStats(123)).thenReturn(new ItemStats(true,1,0,ItemEquipmentStats.builder().aspeed(4).build()));
        EnumComposition categories=mock(EnumComposition.class),styles=mock(EnumComposition.class),sets=mock(EnumComposition.class);
        when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);when(categories.getIntValue(anyInt())).thenReturn(987);
        when(client.getEnum(987)).thenReturn(styles);when(styles.getIntVals()).thenReturn(new int[]{100,101});
        StructComposition rapid=mock(StructComposition.class);when(client.getStructComposition(101)).thenReturn(rapid);
        when(rapid.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Rapid");when(client.getVarpValue(VarPlayer.ATTACK_STYLE)).thenReturn(1);
        when(client.getEnum(3047)).thenReturn(sets);when(client.getVarbitValue(VarbitID.WORN_ITEMS_SET_TEXT)).thenReturn(3);
        when(sets.getStringValue(3)).thenReturn("<col=ffffff>Current set</col><br>Its active effect.");
        String text=EquipmentDetails.capture(client,manager);
        assertTrue(text.contains("3 ticks (1.8 s)"));assertTrue(text.contains("Current set Its active effect."));
        when(client.getVarbitValue(VarbitID.WORN_ITEMS_SET_TEXT)).thenReturn(0);
        assertFalse(EquipmentDetails.capture(client,manager).contains("Current set"));
        verify(client,never()).runScript(any(Object[].class));verify(client,never()).openInterface(anyInt(),anyInt(),anyInt());
    }
    @Test public void targetBonusesRemainConditionalAndFollowCurrentEquipment() {
        Set<String> gear=new HashSet<>(Arrays.asList("salve amulet(ei)","slayer helmet (i)","dragon hunter lance"));
        String effects=String.join(" ",EquipmentDetails.targetEffects(gear));
        assertTrue(effects.contains("assigned Slayer targets"));assertTrue(effects.contains("Does not stack"));
        assertTrue(effects.contains("draconic targets"));
        assertTrue(EquipmentDetails.targetEffects(Collections.emptySet()).isEmpty());
    }
    @Test public void longGameDescriptionsWrapWithoutKeepingMarkupOrEmptyLines() {
        String paragraph="<col=ff0000>A very long native description</col><br>"+"additional conditional effects ".repeat(10);
        String result=EquipmentDetails.wrap(Arrays.asList("<col=ffffff>Set effects</col>",paragraph));
        assertFalse(result.contains("ff0000"));assertFalse(result.contains("<br><br>"));
        for(String line:result.split("<br>"))if(!line.startsWith("<col="))assertTrue(line.length()<=55);
    }
}
