package com.interfacelayout.layout.orbs;

import com.interfacelayout.util.WidgetGeometry;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

final class CompassLayer {
    private Widget parent, display, source;
    private int opacity;

    void restore(Client client) {
        if(display!=null)display.setHidden(true);
        if(source!=null && CircularOrbInput.current(client,source)) {
            if(source.getContentType()==0)source.setContentType(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
            if(source.getOpacity()==255)source.setOpacity(opacity);
        }
        source=null;
    }

    void clear(Client client) {
        restore(client);
        if(parent!=null && display!=null) {
            Widget[] children=parent.getChildren();
            if(children!=null)for(int i=0;i<children.length;i++)if(children[i]==display) {
                Widget[] retained=java.util.Arrays.copyOf(children,i==children.length-1?i:children.length);
                if(i<retained.length)retained[i]=null;
                parent.setChildren(retained);break;
            }
        }
        parent=null;display=null;
    }

    void refresh(Client client,Widget compass,Widget minimap) {
        Widget host=sharedParent(compass,minimap);
        if(host==null)return;
        if(parent!=host || !attached()) {
            clear(client);
            parent=host;
            display=parent.createChild(-1,WidgetType.GRAPHIC);
            if(display==null) {parent=null;return;}
        }
        Rectangle bounds=WidgetGeometry.bounds(compass),container=WidgetGeometry.bounds(parent);
        display.setContentType(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
        display.setSpriteId(compass.getSpriteId());
        display.setOriginalX(bounds.x-container.x+parent.getScrollX());
        display.setOriginalY(bounds.y-container.y+parent.getScrollY());
        display.setOriginalWidth(bounds.width);display.setOriginalHeight(bounds.height);
        display.setForcedPosition(-1,-1);
        display.setOpacity(compass.getOpacity());
        display.setNoClickThrough(false);display.setHasListener(false);
        display.setHidden(false);display.revalidate();
        source=compass;opacity=compass.getOpacity();
        compass.setContentType(0);compass.setOpacity(255);
    }

    private boolean attached() {
        if(parent==null || display==null)return false;
        Widget[] children=parent.getChildren();
        if(children!=null)for(Widget child:children)if(child==display)return true;
        return false;
    }

    private static Widget sharedParent(Widget compass,Widget minimap) {
        if(compass==null || minimap==null)return null;
        Set<Widget> ancestors=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Widget p=minimap.getParent();p!=null && ancestors.add(p);p=p.getParent()) { }
        Set<Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Widget p=compass.getParent();p!=null && visited.add(p);p=p.getParent())
            if(ancestors.contains(p))return p;
        return null;
    }
}
