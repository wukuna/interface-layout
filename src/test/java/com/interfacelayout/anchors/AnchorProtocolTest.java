package com.interfacelayout.anchors;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AnchorProtocolTest
{
    private final EventBus bus=new EventBus(error->{throw new AssertionError(error);});
    private final ClientThread thread=mock(ClientThread.class);
    private final ConfigManager configs=mock(ConfigManager.class);
    private final Client client=mock(Client.class);
    private final Map<String,String> values=new HashMap<>();
    private final List<PluginMessage> messages=new ArrayList<>();
    private AnchorCoordinator coordinator;
    private AnchorLayoutStore store;
    public static final class Target implements AnchorParticipant.Target
    {
        final String id;
        final Rectangle bounds=new Rectangle(50,60,34,34);
        int moves,claims,releases,resizes;
        boolean available=true;
        String state="VISIBLE";
        Target(String id){this.id=id;}
        public String id(){return id;}
        public String name(){return id;}
        public Rectangle bounds(){return new Rectangle(bounds);}
        public boolean available(){return available;}
        public String state(){return state;}
        public boolean claim(){claims++;return true;}
        public void move(Point point){moves++;bounds.setLocation(point);}
        public void release(boolean keep){releases++;}
        public void resize(java.awt.Dimension size){resizes++;}
    }
    public final class Recorder {@Subscribe public void onPluginMessage(PluginMessage event){messages.add(event);}}
    @Before public void setup()
    {
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invoke(any(Runnable.class));
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(700);
        when(configs.getConfiguration(anyString(),anyString())).thenAnswer(call->values.get(call.getArgument(0)+"/"+call.getArgument(1)));
        doAnswer(call->{assertEquals("interfacelayout",call.getArgument(0));values.put(call.getArgument(0)+"/"+call.getArgument(1),call.getArgument(2).toString());return null;}).when(configs).setConfiguration(anyString(),anyString(),any());
        var injector=Guice.createInjector(new AbstractModule(){@Override protected void configure(){bind(Gson.class).toInstance(new Gson());bind(Client.class).toInstance(client);bind(ClientThread.class).toInstance(thread);bind(ConfigManager.class).toInstance(configs);bind(EventBus.class).toInstance(bus);}});
        coordinator=injector.getInstance(AnchorCoordinator.class);store=injector.getInstance(AnchorLayoutStore.class);bus.register(new Recorder());
    }
    private AnchorParticipant participant(Target target){AnchorParticipant result=new AnchorParticipant("test",bus,thread,()->List.of(target));result.start();return result;}
    private void attach(String id){coordinator.edit(layout->{AnchorLayout.Anchor anchor=new AnchorLayout.Anchor();anchor.x=100;anchor.y=120;anchor.padding=0;AnchorLayout.Member member=new AnchorLayout.Member();member.target=id;anchor.members.add(member);layout.anchors.add(anchor);});}
    private void frame(){bus.post(new BeforeRender());}
    @Test public void bothStartupOrdersDiscoverAndApplyWithoutResizeOrFrameWrites()
    {
        Target first=new Target("first");AnchorParticipant a=participant(first);coordinator.start();attach(first.id);frame();
        assertEquals(new Point(100,120),first.bounds.getLocation());assertEquals(1,first.claims);assertEquals(0,first.resizes);
        clearInvocations(configs);for(int i=0;i<100;i++)frame();verify(configs,never()).setConfiguration(anyString(),anyString(),any());
        a.stop();assertEquals(1,first.releases);
        Target second=new Target("second");participant(second);attach(second.id);frame();assertEquals(1,second.moves);
    }
    @Test public void hiddenAndUnavailableMembersKeepBindingsAndRecover()
    {
        coordinator.start();Target target=new Target("prayer");participant(target);attach(target.id);frame();
        target.state="HIDDEN";target.available=false;frame();assertEquals(1,target.releases);assertEquals(1,store.snapshot().anchors.get(0).members.size());
        target.state="VISIBLE";target.available=true;frame();assertEquals(2,target.claims);
        target.state="OCCLUDED";frame();assertEquals(2,target.claims);
    }
    @Test public void duplicateProvidersCannotBothOwnOneControl()
    {
        coordinator.start();Target a=new Target("same"),b=new Target("same");participant(a);participant(b);attach("same");frame();assertEquals(0,a.claims);assertEquals(0,b.claims);
    }
    @Test public void profileSwitchAndLateApplyCannotRestoreOldOwnership()
    {
        coordinator.start();Target target=new Target("prayer");participant(target);attach(target.id);frame();
        PluginMessage apply=messages.stream().filter(message->message.getName().equals("apply")).reduce((a,b)->b).get();int before=target.moves;
        values.clear();bus.post(new ProfileChanged());frame();bus.post(apply);assertEquals(before,target.moves);assertTrue(coordinator.snapshot().layout.anchors.isEmpty());
    }
    @Test public void duplicateAndOutOfOrderRepliesDoNotMoveAControlTwice()
    {
        coordinator.start();Target target=new Target("prayer");participant(target);attach(target.id);frame();
        PluginMessage apply=messages.stream().filter(message->message.getName().equals("apply")).reduce((a,b)->b).get();int before=target.moves;
        bus.post(apply);assertEquals(before,target.moves);frame();before=target.moves;bus.post(apply);assertEquals(before,target.moves);
    }
    @Test public void disabledEngineRestoresAllOwnersAndNoFurtherFramesMoveControls()
    {
        coordinator.start();Target target=new Target("prayer");participant(target);attach(target.id);frame();coordinator.stop();int moves=target.moves;frame();assertEquals(1,target.releases);assertEquals(moves,target.moves);
    }
    @Test public void registryRejectsOversizedMalformedAndDuplicateMeasurements()
    {
        AnchorRegistry registry=new AnchorRegistry();assertTrue(registry.join("token","test"));
        Map<String,Object> target=Map.of("id","p","name","p","state","VISIBLE","x",0,"y",0,"width",34,"height",34,"movable",true);
        registry.measure("token",1,List.of(target,target));assertTrue(registry.targets(1).isEmpty());
        registry.measure("token",2,List.of(target));assertEquals(1,registry.targets(2).size());
        Map<String,Object> bad=new HashMap<>(target);bad.put("width",Integer.MAX_VALUE);registry.measure("token",3,List.of(bad));assertTrue(registry.targets(3).isEmpty());
        registry.measure("token",1,List.of(target));assertEquals(1,registry.targets(2).size());
    }
    @Test public void dragPreviewWritesOnlyOnReleaseAndUndoRestoresIntent()
    {
        coordinator.start();attach("prayer");frame();String id=store.snapshot().anchors.get(0).id;clearInvocations(configs);
        var drag=new AnchorCoordinator.Drag(coordinator.snapshot().session,id,25,30,false);coordinator.preview(drag);frame();
        assertEquals(125,coordinator.snapshot().geometry.anchors.get(id).x);assertEquals(100,store.snapshot().anchors.get(0).x);verify(configs,never()).setConfiguration(anyString(),anyString(),any());
        coordinator.finishDrag(drag);assertEquals(125,store.snapshot().anchors.get(0).x);coordinator.undo();assertEquals(100,store.snapshot().anchors.get(0).x);
    }
}
