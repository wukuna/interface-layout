package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import com.interfacelayout.InterfaceLayoutConfig.OrbTimerStyle;
import net.runelite.api.gameval.InterfaceID;

public final class OrbTimerStyles
{
    private OrbTimerStyles() {}

    public static NativeTimerStyle resolve(InterfaceLayoutConfig config,int component)
    {
        OrbTimerStyle selected=component==InterfaceID.Orbs.ORB_HEALTH?config.hitpointsTimerStyle():
            component==InterfaceID.Orbs.ORB_PRAYER?config.prayerTimerStyle():config.specialTimerStyle();
        if(selected!=null && selected!=OrbTimerStyle.CURRENT)return NativeTimerStyle.valueOf(selected.name());
        if(config.hideOrbTabs())return NativeTimerStyle.QUIET_RIMS;
        NativeTimerStyle legacy=config.nativeOrbTimerStyle();
        return legacy==null?NativeTimerStyle.INSET_SWEEP:legacy;
    }
}
