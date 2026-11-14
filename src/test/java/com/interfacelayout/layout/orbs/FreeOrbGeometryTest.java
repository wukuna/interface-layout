package com.interfacelayout.layout.orbs;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FreeOrbGeometryTest
{
    @Test public void circlesDragAwayFromEdgesWithoutMovingOtherControlsOrChangingOrder()
    {
        CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture();
        int[] forced={-1,-1},relative={100,70};
        doAnswer(i->{forced[0]=i.getArgument(0);forced[1]=i.getArgument(1);relative[0]=forced[0];relative[1]=forced[1];return null;})
            .when(f.parent).setForcedPosition(anyInt(),anyInt());
        doAnswer(i->{relative[0]=forced[0]>=0?forced[0]:f.parent.getOriginalX();relative[1]=forced[1]>=0?forced[1]:f.parent.getOriginalY();return null;})
            .when(f.parent).revalidate();
        when(f.parent.getRelativeX()).thenAnswer(i->relative[0]);when(f.parent.getRelativeY()).thenAnswer(i->relative[1]);
        doAnswer(i->{relative[0]=i.getArgument(0);return null;}).when(f.parent).setRelativeX(anyInt());
        doAnswer(i->{relative[1]=i.getArgument(0);return null;}).when(f.parent).setRelativeY(anyInt());
        when(f.parent.getBounds()).thenAnswer(i->com.interfacelayout.util.WidgetGeometry.bounds(f.parent));
        when(f.overlay.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        when(f.overlay.manager.isLoggedIn()).thenReturn(true);
        when(f.overlay.client.getCanvasWidth()).thenReturn(1000);
        when(f.overlay.client.getCanvasHeight()).thenReturn(700);
        WidgetManager widgets=mock(WidgetManager.class);
        when(widgets.getTargetWidget(Orbs.SPEC_ORB_CONTAINER)).thenReturn(f.parent);
        ConfigManager configs=mock(ConfigManager.class);Map<String,String> settings=new HashMap<>();
        when(configs.getConfiguration(anyString(),anyString())).thenAnswer(i->settings.get(i.getArgument(1)));
        doAnswer(i->{settings.put(i.getArgument(1),i.getArgument(2).toString());return null;})
            .when(configs).setConfiguration(anyString(),anyString(),any(Object.class));
        doAnswer(i->{settings.remove(i.getArgument(1));return null;}).when(configs).unsetConfiguration(anyString(),anyString());
        OverlayManager overlays=mock(OverlayManager.class);List<Overlay> added=new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i->added.add(i.getArgument(0)));
        WidgetStateStore states=new WidgetStateStore();
        com.google.inject.Injector injector=Guice.createInjector(new AbstractModule(){
            @Override protected void configure() {
                bind(Client.class).toInstance(f.overlay.client);bind(InterfaceLayoutConfig.class).toInstance(f.overlay.config);
                bind(OrbController.class).toProvider(()->f.overlay.manager);bind(WidgetManager.class).toProvider(()->widgets);
                bind(EditManager.class).toProvider(()->mock(EditManager.class));bind(ConfigManager.class).toInstance(configs);
                bind(MenuButtonSprites.class).toProvider(()->f.overlay.sprites);bind(OverlayManager.class).toInstance(overlays);
                bind(WidgetStateStore.class).toInstance(states);
            }
        });
        FreePositionController free=injector.getInstance(FreePositionController.class);
        FreeOrbOverlayBridge bridge=injector.getInstance(FreeOrbOverlayBridge.class);
        free.apply();bridge.start(()->fail("Rendering must not rebuild widgets mid-frame"));
        Overlay anchor=added.get(0);assertEquals(new Rectangle(123,70,34,34),anchor.getBounds());
        free.align(Orbs.SPEC_ORB_CONTAINER,EdgeAlignment.LEFT);states.restore();free.apply();bridge.sync();
        f.parent.revalidate();
        assertEquals(-23,f.parent.getRelativeX());
        assertEquals(new Rectangle(0,70,34,34),anchor.getBounds());
        anchor.setPreferredLocation(new Point(200,150));anchor.getBounds().setLocation(200,150);anchor.render(null);
        assertNull(free.alignment(Orbs.SPEC_ORB_CONTAINER));
        states.restore();free.apply();bridge.sync();
        assertEquals(new Rectangle(200,150,34,34),free.overlayBounds().get(Orbs.SPEC_ORB_CONTAINER));
        float priority=anchor.getPriority();settings.put("orbStackOrder","SPECIAL,HITPOINTS,PRAYER,RUN");bridge.sync();
        assertEquals(priority,anchor.getPriority(),0);assertEquals(1,added.size());
        f.refresh();f.render();assertEquals(34,free.overlayBounds().get(Orbs.SPEC_ORB_CONTAINER).width);
        bridge.stop();f.overlay.releaseForLayout();states.restore();
    }
}
