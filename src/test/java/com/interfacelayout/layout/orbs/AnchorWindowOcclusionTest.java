package com.interfacelayout.layout.orbs;

import com.google.gson.Gson;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.anchors.*;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.layout.menu.MenuOverlayBridge;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.*;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AnchorWindowOcclusionTest
{
    @Test public void coveredMapAndCompassKeepTheirAnchorAcrossFramesAndWindowClosure()throws Exception
    {
        for(boolean classic:new boolean[]{false,true})for(boolean custom:new boolean[]{false,true}) {
            Client client=mock(Client.class);when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(700);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            OrbController manager=mock(OrbController.class);when(manager.isLoggedIn()).thenReturn(true);when(manager.isClassicResizable()).thenReturn(classic);
            InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
            when(config.minimapFrame()).thenReturn(custom?InterfaceLayoutConfig.MinimapFrame.CIRCULAR:InterfaceLayoutConfig.MinimapFrame.NATIVE);
            TargetWidget mapTarget=classic?Minimap.CLASSIC_MINIMAP_MASK:Minimap.MODERN_MINIMAP_MASK,compassTarget=classic?Compass.CLASSIC_COMPASS:Compass.MODERN_COMPASS;
            Widget map=widget(client,mapTarget.getComponentId(),800,50,150,150),compass=widget(client,compassTarget.getComponentId(),760,40,33,33);
            when(map.getContentType()).thenReturn(1338);when(compass.getContentType()).thenReturn(1339);
            Widget bank=widget(client,InterfaceID.Bankmain.UNIVERSE,100,100,400,400);bank.setHidden(true);
            WidgetManager widgets=mock(WidgetManager.class);when(widgets.getTargetWidget(any())).thenAnswer(call->client.getWidget(((TargetWidget)call.getArgument(0)).getComponentId()));
            ConfigManager configs=mock(ConfigManager.class);Map<String,String> saved=new HashMap<>();
            when(configs.getConfiguration(anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(1)));
            doAnswer(call->{saved.put(call.getArgument(1),call.getArgument(2).toString());return null;}).when(configs).setConfiguration(anyString(),anyString(),any());
            ClientThread thread=mock(ClientThread.class);doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invoke(any(Runnable.class));
            doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
            EventBus events=new EventBus(error->{throw new AssertionError(error);});Gson gson=new Gson();WidgetStateStore states=new WidgetStateStore();
            FreePositionController free=new FreePositionController();inject(free,"client",client);inject(free,"config",config);inject(free,"manager",manager);inject(free,"configs",configs);
            inject(free,"widgets",widgets);inject(free,"editor",mock(EditManager.class));inject(free,"states",states);inject(free,"expander",mock(WidgetBoundsExpander.class));
            inject(free,"savedPositions",mock(LayoutPositionStore.class));inject(free,"sprites",mock(MenuButtonSprites.class));
            CircularOrbs circular=new CircularOrbs();circular.client=client;circular.thread=thread;circular.config=config;circular.manager=manager;circular.start();
            GameframeCoordinator frame=mock(GameframeCoordinator.class);
            doAnswer(call->{circular.releaseForLayout();states.restore();free.apply();circular.refreshAfterLayout();return null;}).when(frame).refresh();
            LayoutAnchorAdapter adapter=new LayoutAnchorAdapter();inject(adapter,"free",free);inject(adapter,"circular",circular);inject(adapter,"menu",mock(MenuStoneController.class));
            ShieldSpecialOrb shield=new ShieldSpecialOrb();inject(shield,"client",client);inject(shield,"config",config);
            inject(adapter,"frame",frame);inject(adapter,"shield",shield);inject(adapter,"events",events);inject(adapter,"thread",thread);
            inject(adapter,"orbBridge",mock(FreeOrbOverlayBridge.class));inject(adapter,"menuBridge",mock(MenuOverlayBridge.class));
            AnchorLayoutStore store=new AnchorLayoutStore();inject(store,"gson",gson);inject(store,"configs",configs);
            AnchorCoordinator coordinator=new AnchorCoordinator();inject(coordinator,"client",client);inject(coordinator,"thread",thread);inject(coordinator,"events",events);
            inject(coordinator,"configs",configs);inject(coordinator,"store",store);inject(coordinator,"gson",gson);
            coordinator.start();adapter.start();
            try {
                coordinator.edit(layout->{AnchorLayout.Anchor anchor=new AnchorLayout.Anchor();anchor.x=180;anchor.y=180;anchor.padding=0;anchor.arrangement=AnchorLayout.Arrangement.COLUMN;
                    for(String id:List.of("layout.minimap","layout.compass")){AnchorLayout.Member member=new AnchorLayout.Member();member.target=id;anchor.members.add(member);}layout.anchors.add(anchor);});
                frame.refresh();events.post(new BeforeRender());frame.refresh();events.post(new BeforeRender());Map<String,Rectangle> original=new HashMap<>(coordinator.snapshot().geometry.targets);assertEquals(2,original.size());
                bank.setHidden(false);clearInvocations(configs);
                for(int i=0;i<12;i++) {
                    frame.refresh();events.post(new BeforeRender());
                    assertTrue(map.isHidden());assertTrue(compass.isHidden());assertTrue(free.anchorOwned(mapTarget));assertTrue(free.anchorOwned(compassTarget));
                    assertEquals(original,coordinator.snapshot().geometry.targets);
                    assertEquals(AnchorGeometry.State.OCCLUDED,coordinator.snapshot().targets.get("layout.minimap").state);
                    assertEquals(AnchorGeometry.State.OCCLUDED,coordinator.snapshot().targets.get("layout.compass").state);
                }
                bank.setHidden(true);frame.refresh();events.post(new BeforeRender());assertFalse(map.isHidden());assertFalse(compass.isHidden());assertEquals(original,coordinator.snapshot().geometry.targets);
                circular.releaseForLayout();map.setHidden(true);compass.setHidden(true);circular.refreshAfterLayout();events.post(new BeforeRender());
                assertFalse(free.anchorOwned(mapTarget));assertFalse(free.anchorOwned(compassTarget));assertTrue(map.isHidden());assertTrue(compass.isHidden());
                assertEquals(2,store.snapshot().anchors.get(0).members.size());verify(configs,never()).setConfiguration(anyString(),anyString(),any());
            } finally {coordinator.stop();adapter.stop();circular.stop();}
        }
    }
    private static void inject(Object target,String field,Object value)throws Exception {var member=target.getClass().getDeclaredField(field);member.setAccessible(true);member.set(target,value);}
    private static Widget widget(Client client,int id,int x,int y,int width,int height)
    {
        Widget widget=CircularOrbInputTest.widget(client,id,null,x,y,width,height);AtomicBoolean hidden=new AtomicBoolean();Point forced=new Point(-1,-1);
        when(widget.isHidden()).thenAnswer(call->hidden.get());when(widget.isSelfHidden()).thenAnswer(call->hidden.get());doAnswer(call->{hidden.set(call.getArgument(0));return null;}).when(widget).setHidden(anyBoolean());
        when(widget.getRelativeX()).thenAnswer(call->forced.x<0?widget.getOriginalX():forced.x);when(widget.getRelativeY()).thenAnswer(call->forced.y<0?widget.getOriginalY():forced.y);
        doAnswer(call->{forced.setLocation(call.getArgument(0),call.getArgument(1));return widget;}).when(widget).setForcedPosition(anyInt(),anyInt());return widget;
    }
}
