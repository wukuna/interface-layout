package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.gameval.InterfaceID;

public enum OrbOrder
{
    HITPOINTS("Hitpoints", Orbs.HP_ORB_CONTAINER, InterfaceID.Orbs.HEALTH_TEXT),
    PRAYER("Prayer", Orbs.PRAYER_ORB_CONTAINER, InterfaceID.Orbs.PRAYER_TEXT),
    RUN("Run energy", Orbs.RUN_ORB_CONTAINER, InterfaceID.Orbs.RUNENERGY_TEXT),
    SPECIAL("Special attack", Orbs.SPEC_ORB_CONTAINER, InterfaceID.Orbs.SPECENERGY_TEXT);

    public final Orbs target;
    public final int number;
    private final String label;

    OrbOrder(String label, Orbs target, int number)
    { this.label=label; this.target=target; this.number=number; }

    @Override public String toString() { return label; }

    public boolean mirrored(InterfaceLayoutConfig config)
    {
        switch (this)
        {
            case HITPOINTS: return config.mirrorHitpoints();
            case PRAYER: return config.mirrorPrayer();
            case RUN: return config.mirrorRun();
            default: return config.mirrorSpecial();
        }
    }

    public static List<OrbOrder> backToFront(String stored)
    {
        List<OrbOrder> result=new ArrayList<>();
        if (stored!=null) for (String name:stored.split(","))
            for (OrbOrder orb:values()) if (orb.name().equals(name.trim()) && !result.contains(orb)) result.add(orb);
        for (OrbOrder orb:values()) if (!result.contains(orb)) result.add(orb);
        return result;
    }

    public static OrbOrder forComponent(int component)
    { return Arrays.stream(values()).filter(orb->orb.target.getComponentId()==component).findFirst().orElse(null); }

    public static String move(String stored, OrbOrder orb, int step)
    {
        List<OrbOrder> result=backToFront(stored);
        int index=result.indexOf(orb), next=Math.max(0,Math.min(result.size()-1,index+step));
        result.remove(index); result.add(next,orb);
        return result.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
