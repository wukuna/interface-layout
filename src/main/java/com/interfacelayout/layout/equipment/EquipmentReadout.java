package com.interfacelayout.layout.equipment;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Base equipment bonuses; visible native equipment text supersedes these totals. */
public final class EquipmentReadout
{
    public final double[] bonuses;
    public final boolean complete, nativeValues;
    public final String spellbook;
    public final int weight;
    EquipmentReadout(double[] bonuses,boolean complete,boolean nativeValues,String spellbook,int weight) {
        this.bonuses=bonuses.clone();this.complete=complete;this.nativeValues=nativeValues;this.spellbook=spellbook;this.weight=weight;
    }
    static EquipmentReadout capture(Client client,ItemManager manager) {
        double[] totals=new double[14];boolean complete=true;
        ItemContainer container=client.getItemContainer(InventoryID.WORN);
        if(container==null) complete=false;
        else for(Item item:container.getItems()) if(item!=null && item.getId()>=0 && item.getQuantity()>0) {
            ItemStats stats=manager.getItemStats(item.getId());
            if(stats==null || stats.getEquipment()==null) {complete=false;continue;}
            add(totals,stats.getEquipment());
        }
        return new EquipmentReadout(totals,complete,false,book(client.getVarbitValue(VarbitID.SPELLBOOK)),client.getWeight());
    }
    static void add(double[] values,ItemEquipmentStats stats) {
        double[] item={stats.getAstab(),stats.getAslash(),stats.getAcrush(),stats.getAmagic(),stats.getArange(),
            stats.getDstab(),stats.getDslash(),stats.getDcrush(),stats.getDmagic(),stats.getDrange(),
            stats.getStr(),stats.getRstr(),stats.getMdmg(),stats.getPrayer()};
        for(int i=0;i<values.length;i++) values[i]+=item[i];
    }
    static String book(int value) {
        switch(value) {case 0:return "Standard";case 1:return "Ancient";case 2:return "Lunar";case 3:return "Arceuus";default:return "Unknown";}
    }
    static Double nativeBonus(String text) {
        if(text==null) return null;
        String value=text.replaceAll("<[^>]*>","");int colon=value.lastIndexOf(':');
        if(colon<0) return null;
        value=value.substring(colon+1).replace("%","").replace(" ","").trim();
        try {double result=Double.parseDouble(value);return Double.isFinite(result)?result:null;}
        catch(NumberFormatException e) {return null;}
    }
    public String value(int index) {
        if(!complete)return "?";
        double value=bonuses[index];String number=Math.abs(value-Math.rint(value))<0.001
            ?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,"%.1f",value);
        return (value>0?"+":"")+number+(index==12?"%":"");
    }
}
