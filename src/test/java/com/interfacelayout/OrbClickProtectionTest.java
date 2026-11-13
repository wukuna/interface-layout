package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class OrbClickProtectionTest {
    @Test public void nativeControlsProtectVisibleAndUnavailableButtonsInFixedAndResizableModes() throws Exception {
        for(boolean resized:new boolean[]{false,true})for(boolean hidden:new boolean[]{false,true}) {
            Client client=mock(Client.class);
            when(client.isResized()).thenReturn(resized);
            InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
            when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
            OrbController manager=new OrbController();
            WidgetManager widgets=mock(WidgetManager.class);
            EditingTransitionsTest.inject(manager,"client",client);
            EditingTransitionsTest.inject(manager,"config",config);
            EditingTransitionsTest.inject(manager,"widgetManager",widgets);
            for(TargetWidget orb:Orbs.SWAPPABLE_ORBS) {
                Widget layer=mock(Widget.class),backing=mock(Widget.class),button=mock(Widget.class),blocker=mock(Widget.class);
                when(client.getWidget(orb.getComponentId())).thenReturn(layer);
                when(client.getWidget(orb.getBackingId())).thenReturn(backing);
                when(client.getWidget(orb.getButtonId())).thenReturn(button);
                when(backing.getId()).thenReturn(orb.getBackingId());
                when(button.getId()).thenReturn(orb.getButtonId());
                when(button.isHidden()).thenReturn(hidden);
                when(widgets.createNoClick(layer,button)).thenReturn(blocker);
            }
            manager.updateNoClickThrough();
            for(TargetWidget orb:Orbs.SWAPPABLE_ORBS) {
                verify(widgets).setNoClickThrough(orb.getComponentId(),false);
                verify(widgets).setNoClickThrough(orb.getButtonId(),true);
                verify(widgets).setNoClickThrough(orb.getBackingId(),false);
                Widget button=client.getWidget(orb.getButtonId());
                verify(button,never()).setHidden(anyBoolean());
                verify(button,never()).setAction(anyInt(),anyString());
                if(hidden)verify(widgets).createNoClick(client.getWidget(orb.getComponentId()),button);
            }
        }
    }
}
