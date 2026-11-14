package com.interfacelayout.layout.orbs.widget.elements;

import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.util.SetValue;
import com.interfacelayout.util.ValueKey;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;

public enum Gravestone implements TargetWidget
{
    FIXED(InterfaceID.Toplevel.GRAVESTONE),
    CLASSIC(InterfaceID.ToplevelOsrsStretch.GRAVESTONE),
    MODERN(InterfaceID.ToplevelPreEoc.GRAVESTONE);

    private final int component;
    Gravestone(int component) { this.component=component; }
    @Override public int getComponentId() { return component; }
    @Override public Map<ValueKey, SetValue> getValueMap() { return Map.of(); }
}
