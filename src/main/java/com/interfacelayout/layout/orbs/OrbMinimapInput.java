package com.interfacelayout.layout.orbs;

import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

final class OrbMinimapInput
{
    private final Map<Widget,Integer> contents=new IdentityHashMap<>();

    void block(Widget map)
    { block(map,true); }

    void block(Widget map,boolean suppress)
    {
        if(map==null || map.isHidden() || map.getContentType()!=OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT)return;
        contents.putIfAbsent(map,map.getContentType());if(suppress)map.setContentType(0);
    }

    void prepareRender(Client client)
    {
        contents.forEach((map,content)->{if(CircularOrbInput.current(client,map) && map.getContentType()==0)map.setContentType(content);});
    }

    void resumeInput(Client client)
    {
        contents.forEach((map,content)->{if(CircularOrbInput.current(client,map) && map.getContentType()==content)map.setContentType(0);});
    }

    void restore(Client client)
    { prepareRender(client);contents.clear(); }
}
