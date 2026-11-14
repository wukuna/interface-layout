package com.interfacelayout.anchors;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.FocusChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AnchorDragEditorTest
{
    private com.google.inject.Injector injector;
    private final OverlayManager overlays=mock(OverlayManager.class);
    private final Client client=mock(Client.class);
    private final ClientThread thread=mock(ClientThread.class);
    private final ConfigManager configs=mock(ConfigManager.class);
    private final RuneLiteConfig settings=mock(RuneLiteConfig.class);
    private final KeyManager keys=mock(KeyManager.class);
    private final EventBus events=new EventBus(error->{throw new AssertionError(error);});
    private final List<Runnable> pending=new ArrayList<>();
    private final Map<String,String> values=new HashMap<>();
    private final Control control=new Control();
    private AnchorParticipant participant;
    private AnchorCoordinator coordinator;
    private AnchorLayoutStore store;
    private AnchorEditor editor;
    private MouseManager mice;
    private HotkeyListener hotkey;
    private String anchor;
    private static final class Control implements AnchorParticipant.Target
    {
        final Rectangle bounds=new Rectangle(50,60,34,34);
        String state="VISIBLE";
        public String id(){return "layout.orb.prayer";}
        public String name(){return "Prayer";}
        public Rectangle bounds(){return new Rectangle(bounds);}
        public String state(){return state;}
        public boolean available(){return state.equals("VISIBLE");}
        public boolean claim(){return true;}
        public void move(Point point){bounds.setLocation(point);}
        public void release(boolean keep){}
    }
    @Before public void setup()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(700);
        when(settings.dragHotkey()).thenReturn(new Keybind(KeyEvent.VK_UNDEFINED,MouseEvent.ALT_DOWN_MASK));
        when(configs.getConfiguration(anyString(),anyString())).thenAnswer(call->values.get(call.getArgument(0)+"/"+call.getArgument(1)));
        doAnswer(call->{assertEquals("interfacelayout",call.getArgument(0));values.put(call.getArgument(0)+"/"+call.getArgument(1),call.getArgument(2).toString());return null;}).when(configs).setConfiguration(anyString(),anyString(),any());
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invoke(any(Runnable.class));
        doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
        injector=Guice.createInjector(new AbstractModule(){@Override protected void configure(){bind(Client.class).toInstance(client);bind(ClientThread.class).toInstance(thread);bind(ConfigManager.class).toInstance(configs);bind(RuneLiteConfig.class).toInstance(settings);bind(KeyManager.class).toInstance(keys);bind(EventBus.class).toInstance(events);bind(Gson.class).toInstance(new Gson());bind(OverlayManager.class).toInstance(overlays);}});
        coordinator=injector.getInstance(AnchorCoordinator.class);store=injector.getInstance(AnchorLayoutStore.class);editor=injector.getInstance(AnchorEditor.class);mice=injector.getInstance(MouseManager.class);
        coordinator.start();participant=new AnchorParticipant("test",events,thread,()->List.of(control));participant.start();
        coordinator.edit(layout->{AnchorLayout.Anchor box=new AnchorLayout.Anchor();box.x=200;box.y=200;box.padding=0;layout.anchors.add(box);anchor=box.id;});flush();
        coordinator.editing(true);editor.start();ArgumentCaptor<net.runelite.client.input.KeyListener> listener=ArgumentCaptor.forClass(net.runelite.client.input.KeyListener.class);verify(keys).registerKeyListener(listener.capture());hotkey=(HotkeyListener)listener.getValue();hotkey.hotkeyPressed();frame();
    }
    @After public void close(){if(editor!=null)editor.stop();if(coordinator!=null)coordinator.stop();if(participant!=null)participant.stop();}
    private void frame(){events.post(new BeforeRender());}
    private void flush(){while(!pending.isEmpty())pending.remove(0).run();}
    private MouseEvent event(int type,int x,int y){return new MouseEvent(new Canvas(),type,1,MouseEvent.ALT_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1);}
    private void press(){assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,55,65)).isConsumed());}
    private void drag(int x,int y){assertTrue(mice.processMouseDragged(event(MouseEvent.MOUSE_DRAGGED,x,y)).isConsumed());}
    private void release(int x,int y){assertTrue(mice.processMouseReleased(event(MouseEvent.MOUSE_RELEASED,x,y)).isConsumed());}
    @Test public void dragCommitsOnceOnReleaseWithGhostAndUndoAndNoAwtClientReads()throws Exception
    {
        clearInvocations(client,configs);SwingUtilities.invokeAndWait(()->{press();drag(255,255);});verifyNoInteractions(client,configs);
        frame();assertTrue(store.snapshot().find(anchor).members.isEmpty());assertEquals(new Point(50,60),control.bounds.getLocation());verify(configs,never()).setConfiguration(anyString(),anyString(),any());
        assertEquals(new Rectangle(250,250,34,34),coordinator.memberPreview().drop.member);
        clearInvocations(client,configs);SwingUtilities.invokeAndWait(()->release(255,255));verifyNoInteractions(client,configs);flush();frame();
        assertEquals(1,store.snapshot().find(anchor).members.size());assertEquals(new Point(250,250),control.bounds.getLocation());
        verify(configs,times(1)).setConfiguration(eq("interfacelayout"),eq("anchorsV1"),any());coordinator.undo();flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
    }
    @Test public void consumedGestureNeverReachesAnotherDragHandlerOrGameClick()
    {
        MouseAdapter other=mock(MouseAdapter.class);mice.registerMouseListener(other);press();drag(250,250);release(250,250);
        assertTrue(mice.processMouseClicked(event(MouseEvent.MOUSE_CLICKED,250,250)).isConsumed());verifyNoInteractions(other);mice.unregisterMouseListener(other);
    }
    @Test public void droppingOutsideOrClickingWithoutDraggingDoesNotAttach()
    {
        press();drag(800,600);frame();assertNull(coordinator.memberPreview().drop);release(800,600);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        press();release(55,65);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
    }
    @Test public void focusLossOrReleasingHotkeyCancelsAndConsumesRelease()
    {
        press();drag(250,250);FocusChanged event=new FocusChanged();event.setFocused(false);events.post(event);release(250,250);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        frame();hotkey.hotkeyPressed();press();drag(250,250);hotkey.hotkeyReleased();release(250,250);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
    }
    @Test public void queuedDropCannotCrossProfilesResizeOrProviderRestart()
    {
        press();drag(250,250);release(250,250);when(client.getCanvasWidth()).thenReturn(800);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        when(client.getCanvasWidth()).thenReturn(1000);frame();press();drag(250,250);release(250,250);participant.stop();participant=new AnchorParticipant("replacement",events,thread,()->List.of(control));participant.start();flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        frame();press();drag(250,250);release(250,250);values.clear();events.post(new ProfileChanged());flush();assertTrue(store.snapshot().anchors.isEmpty());
    }
    @Test public void hiddenControlsAndOpenMenusCannotReceiveAQueuedDrop()
    {
        press();drag(250,250);release(250,250);control.state="HIDDEN";flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        control.state="VISIBLE";frame();press();drag(250,250);release(250,250);when(client.isMenuOpen()).thenReturn(true);flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
    }
    @Test public void ordinaryClicksAndShiftGroupingStayOutsideTheEditor()
    {
        hotkey.hotkeyReleased();assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,55,65)).isConsumed());hotkey.hotkeyPressed();
        MouseEvent shift=new MouseEvent(new Canvas(),MouseEvent.MOUSE_PRESSED,1,MouseEvent.ALT_DOWN_MASK|MouseEvent.SHIFT_DOWN_MASK,55,65,1,false,MouseEvent.BUTTON1);
        assertFalse(mice.processMousePressed(shift).isConsumed());
        coordinator.editing(false);assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,55,65)).isConsumed());
    }
    @Test public void stretchedInputIsConvertedOnceAheadOfTheNativeTranslator()throws Exception
    {
        when(client.isStretchedEnabled()).thenReturn(true);when(client.getRealDimensions()).thenReturn(new java.awt.Dimension(1000,700));when(client.getStretchedDimensions()).thenReturn(new java.awt.Dimension(2000,1400));
        var translator=new net.runelite.client.plugins.stretchedmode.TranslateMouseListener(client);mice.registerMouseListener(0,translator);
        events.post(new net.runelite.client.events.PluginChanged(mock(net.runelite.client.plugins.Plugin.class),true));frame();
        clearInvocations(client);SwingUtilities.invokeAndWait(()->{assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,110,130)).isConsumed());drag(510,510);release(510,510);});verifyNoInteractions(client);flush();frame();assertEquals(new Point(250,250),control.bounds.getLocation());
        hotkey.hotkeyReleased();MouseEvent ordinary=mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,1500,1000));assertEquals(new Point(750,500),ordinary.getPoint());mice.unregisterMouseListener(translator);
    }
    @Test public void changingScaleAfterReleaseCancelsTheQueuedDrop()
    {
        press();drag(250,250);release(250,250);when(client.isStretchedEnabled()).thenReturn(true);when(client.getRealDimensions()).thenReturn(new java.awt.Dimension(1000,700));when(client.getStretchedDimensions()).thenReturn(new java.awt.Dimension(1500,1050));flush();assertTrue(store.snapshot().find(anchor).members.isEmpty());
    }
    @Test public void enablingEditingPlacesItsListenerAheadOfAnExistingObserver()
    {
        var observer=new MouseAdapter(){@Override public MouseEvent mousePressed(MouseEvent event){throw new AssertionError("Another drag listener received the captured press");}};
        mice.registerMouseListener(0,observer);coordinator.editing(false);frame();coordinator.editing(true);frame();press();drag(250,250);release(250,250);flush();assertEquals(1,store.snapshot().find(anchor).members.size());mice.unregisterMouseListener(observer);
    }
    @Test public void duplicateAndExportUseTheCurrentSnapshotWithoutDuplicatingBindings()
    {
        press();drag(250,250);release(250,250);flush();frame();
        assertEquals(1,new Gson().fromJson(coordinator.exportLayout(),AnchorLayout.class).anchors.get(0).members.size());
        coordinator.duplicate(anchor);flush();AnchorLayout result=store.snapshot();assertEquals(2,result.anchors.size());assertTrue(result.anchors.get(1).members.isEmpty());assertNotEquals(result.anchors.get(0).id,result.anchors.get(1).id);
    }
    @Test public void headerIsOutsideTheContainerAndDraggingItMovesOnlyTheAnchor()
    {
        Rectangle bounds=coordinator.snapshot().geometry.anchors.get(anchor);Rectangle header=AnchorEditor.headerBounds(bounds,coordinator.snapshot().canvas);
        assertEquals(bounds.y-20,header.y);assertFalse(bounds.intersects(header));
        assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,bounds.x+30,bounds.y+10)).isConsumed());
        assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+30,header.y+8)).isConsumed());drag(header.x+60,header.y+38);release(header.x+60,header.y+38);flush();
        assertEquals(230,store.snapshot().find(anchor).x);assertEquals(230,store.snapshot().find(anchor).y);assertEquals(new Point(50,60),control.bounds.getLocation());
        Rectangle top=new Rectangle(10,0,200,100);assertFalse(top.intersects(AnchorEditor.headerBounds(top,new java.awt.Dimension(1000,700))));
    }
    @Test public void multiSelectionMovesTogetherAndGroupDetachHasOneUndo()
    {
        coordinator.edit(next->{for(String id:List.of("a","b","c","d")){AnchorLayout.Member member=new AnchorLayout.Member();member.target=id;next.find(anchor).members.add(member);}});flush();
        coordinator.reorderMembers(anchor,List.of("b","c"),-1);flush();assertEquals(List.of("b","c","a","d"),order());
        coordinator.reorderMembers(anchor,List.of("b","c"),1);flush();assertEquals(List.of("a","b","c","d"),order());
        coordinator.reorderMembers(anchor,List.of("a","c"),1);flush();assertEquals(List.of("b","a","d","c"),order());
        clearInvocations(configs);coordinator.detach(List.of("a","c"));flush();assertEquals(List.of("b","d"),order());verify(configs,times(1)).setConfiguration(eq("interfacelayout"),eq("anchorsV1"),any());
        coordinator.undo();flush();assertEquals(List.of("b","a","d","c"),order());
    }
    @Test public void headerCanReachTheTopEdgeWithoutEndingTheDrag()
    {
        Rectangle header=AnchorEditor.headerBounds(coordinator.snapshot().geometry.anchors.get(anchor),coordinator.snapshot().canvas);
        assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+30,header.y+8)).isConsumed());
        drag(header.x+30,7);frame();assertEquals(0,coordinator.snapshot().geometry.anchors.get(anchor).y);
        editor.mouseExited(event(MouseEvent.MOUSE_EXITED,header.x+30,-1));drag(header.x+30,-5);release(header.x+30,-5);flush();frame();
        assertEquals(0,coordinator.snapshot().geometry.anchors.get(anchor).y);assertEquals(0,store.snapshot().find(anchor).y);
        coordinator.undo();flush();assertEquals(200,store.snapshot().find(anchor).y);
    }
    @Test public void focusLossStillCancelsAHeaderDraggedAcrossTheEdge()
    {
        Rectangle header=AnchorEditor.headerBounds(coordinator.snapshot().geometry.anchors.get(anchor),coordinator.snapshot().canvas);
        mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+30,header.y+8));drag(header.x+30,-5);
        FocusChanged lost=new FocusChanged();lost.setFocused(false);events.post(lost);release(header.x+30,-5);flush();assertEquals(200,store.snapshot().find(anchor).y);
    }
    @Test public void stretchedHeaderCanReachTheTopEdgeAndReturnWithoutLosingItsOriginalOffset()
    {
        when(client.isStretchedEnabled()).thenReturn(true);when(client.getRealDimensions()).thenReturn(new java.awt.Dimension(1000,700));when(client.getStretchedDimensions()).thenReturn(new java.awt.Dimension(2000,1400));frame();
        Rectangle header=AnchorEditor.headerBounds(coordinator.snapshot().geometry.anchors.get(anchor),coordinator.snapshot().canvas);
        mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,2*(header.x+30),2*(header.y+8)));
        drag(2*(header.x+30),14);frame();assertEquals(0,coordinator.snapshot().geometry.anchors.get(anchor).y);
        drag(2*(header.x+30),2*(header.y+48));release(2*(header.x+30),2*(header.y+48));flush();assertEquals(240,store.snapshot().find(anchor).y);
    }
    @Test public void ordinaryOverlayAttachesByDraggingWithoutPriorSelectionAndPersistsAfterEditing()throws Exception
    {
        fixtures.OrdinaryOverlay overlay=new fixtures.OrdinaryOverlay();overlay.setPreferredLocation(new Point(40,50));
        when(overlays.anyMatch(any())).thenAnswer(call->((java.util.function.Predicate<net.runelite.client.ui.overlay.Overlay>)call.getArgument(0)).test(overlay));
        ExternalAnchorAdapter external=injector.getInstance(ExternalAnchorAdapter.class);external.start();
        try {
            frame();assertNull(values.get("interfacelayout/anchorExternalOverlaysV1"));
            SwingUtilities.invokeAndWait(()->{assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,100,70)).isConsumed());drag(270,240);release(270,240);});
            flush();frame();String id=store.snapshot().find(anchor).members.get(0).target;assertTrue(id.startsWith("external:"));
            assertEquals(new Point(210,220),overlay.getBounds().getLocation());coordinator.editing(false);frame();
            assertTrue(coordinator.snapshot().targets.containsKey(id));assertEquals(new Point(210,220),overlay.getBounds().getLocation());
            external.stop();assertEquals(new Point(40,50),overlay.getPreferredLocation());external.start();frame();assertEquals(new Point(210,220),overlay.getBounds().getLocation());
            coordinator.detach(id);flush();frame();assertEquals(net.runelite.client.ui.overlay.OverlayPosition.TOP_LEFT,overlay.getPosition());assertEquals(new Point(40,50),overlay.getPreferredLocation());
            assertNull(values.get("interfacelayout/anchorExternalOverlaysV1"));
        } finally {external.stop();}
    }
    private List<String> order(){return store.snapshot().find(anchor).members.stream().map(member->member.target).collect(java.util.stream.Collectors.toList());}
}
