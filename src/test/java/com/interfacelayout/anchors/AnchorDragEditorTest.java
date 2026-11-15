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
        boolean kept;
        public void release(boolean keep){kept=keep;}
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
    @Test public void attachedMemberDropsOutsideAtThePointerAndUndoReattaches()throws Exception
    {
        press();drag(255,255);release(255,255);flush();frame();clearInvocations(configs,client);
        SwingUtilities.invokeAndWait(()->{assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,255,255)).isConsumed());drag(805,605);});
        verifyNoInteractions(client,configs);frame();assertEquals(1,store.snapshot().find(anchor).members.size());
        assertNotNull(coordinator.memberPreview().drop);assertEquals(new Rectangle(800,600,34,34),coordinator.memberPreview().drop.member);
        release(805,605);flush();frame();assertTrue(store.snapshot().find(anchor).members.isEmpty());
        assertEquals(new Point(800,600),control.bounds.getLocation());assertTrue(control.kept);assertFalse(participant.owns(control.id()));
        verify(configs,times(1)).setConfiguration(eq("interfacelayout"),eq("anchorsV1"),any());
        frame();assertEquals(new Point(800,600),control.bounds.getLocation());coordinator.undo();flush();frame();
        assertEquals(1,store.snapshot().find(anchor).members.size());assertEquals(new Point(250,250),control.bounds.getLocation());
    }
    @Test public void cancelledDragOutKeepsMembershipAndPlacement()
    {
        press();drag(255,255);release(255,255);flush();frame();
        mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,255,255));drag(805,605);hotkey.hotkeyReleased();release(805,605);flush();frame();
        assertEquals(1,store.snapshot().find(anchor).members.size());assertEquals(new Point(250,250),control.bounds.getLocation());
    }
    @Test public void arrangementSelectionSavesWithoutApplyAndSurvivesPanelRefreshAndReload()throws Exception
    {
        AnchorPanel[] panel=new AnchorPanel[1];
        SwingUtilities.invokeAndWait(()->panel[0]=new AnchorPanel(coordinator,new AnchorTransfers(),new AnchorTemplates(),new ExternalAnchorAdapter()));
        for(AnchorLayout.Arrangement arrangement:List.of(AnchorLayout.Arrangement.COLUMN,AnchorLayout.Arrangement.ROW,AnchorLayout.Arrangement.WRAP,AnchorLayout.Arrangement.FREE)) {
            SwingUtilities.invokeAndWait(()->arrangement(panel[0]).setSelectedItem(arrangement));flush();frame();
            assertEquals(arrangement,store.snapshot().find(anchor).arrangement);
            SwingUtilities.invokeAndWait(()->{try{var refresh=AnchorPanel.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(panel[0]);}catch(Exception error){throw new AssertionError(error);}});
            assertEquals(arrangement,arrangement(panel[0]).getSelectedItem());store.load();frame();assertEquals(arrangement,store.snapshot().find(anchor).arrangement);
        }
    }
    private static javax.swing.JComboBox<?> arrangement(java.awt.Container parent)
    {
        for(java.awt.Component child:parent.getComponents()) {
            if(child instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>)child).getItemAt(0) instanceof AnchorLayout.Arrangement)return (javax.swing.JComboBox<?>)child;
            if(child instanceof java.awt.Container){var found=arrangement((java.awt.Container)child);if(found!=null)return found;}
        }
        return null;
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
    @Test public void clampedAnchorCanMoveAwayFromTheCanvasEdgeImmediately()
    {
        coordinator.edit(next->{next.find(anchor).x=5000;next.find(anchor).y=5000;});flush();frame();
        Rectangle before=coordinator.snapshot().geometry.anchors.get(anchor),header=AnchorEditor.headerBounds(before,coordinator.snapshot().canvas);
        mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+30,header.y+8));drag(header.x-20,header.y-52);release(header.x-20,header.y-52);flush();frame();
        Rectangle expected=new Rectangle(before);expected.translate(-50,-60);assertEquals(expected,coordinator.snapshot().geometry.anchors.get(anchor));
    }
    @Test public void resizeStartsAtVisibleSizeWhenMembersHaveExpandedTheAnchor()
    {
        press();drag(255,255);release(255,255);flush();
        coordinator.edit(next->{next.find(anchor).width=20;next.find(anchor).height=20;});flush();frame();
        Rectangle before=coordinator.snapshot().geometry.anchors.get(anchor);int x=before.x+before.width-3,y=before.y+before.height-3;
        mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,x,y));drag(x+20,y+30);release(x+20,y+30);flush();frame();
        Rectangle expected=new Rectangle(before);expected.width+=20;expected.height+=30;assertEquals(expected,coordinator.snapshot().geometry.anchors.get(anchor));
    }
    @Test public void resizingReferencedAnchorsWithNegativeOffsetsPreservesTheirTopLeftAndUndo()
    {
        for(AnchorLayout.Reference reference:List.of(AnchorLayout.Reference.BOTTOM_RIGHT,AnchorLayout.Reference.CENTER)) {
            coordinator.edit(next->{var a=next.find(anchor);a.reference=reference;a.x=-100;a.y=-80;a.width=150;a.height=100;a.members.clear();
                var m=new AnchorLayout.Member();m.target=control.id();m.x=-20;m.y=-10;a.members.add(m);});flush();frame();
            Rectangle before=coordinator.snapshot().geometry.anchors.get(anchor);int x=before.x+before.width-3,y=before.y+before.height-3;
            mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,x,y));drag(x+30,y+20);release(x+30,y+20);flush();frame();
            Rectangle expected=new Rectangle(before);expected.width+=30;expected.height+=20;assertEquals(expected,coordinator.snapshot().geometry.anchors.get(anchor));
            coordinator.undo();flush();frame();assertEquals(before,coordinator.snapshot().geometry.anchors.get(anchor));
        }
    }
    @Test public void clickingAHeaderOrResizeGripDoesNotRewriteTheAnchor()
    {
        Rectangle bounds=coordinator.snapshot().geometry.anchors.get(anchor),header=AnchorEditor.headerBounds(bounds,coordinator.snapshot().canvas);long revision=store.revision();
        for(Point point:List.of(new Point(header.x+10,header.y+8),new Point(bounds.x+bounds.width-3,bounds.y+bounds.height-3))) {
            mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,point.x,point.y));release(point.x,point.y);flush();frame();assertEquals(revision,store.revision());
        }
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
            coordinator.detach(id);flush();frame();assertEquals(net.runelite.client.ui.overlay.OverlayPosition.TOP_LEFT,overlay.getPosition());assertEquals(new Point(210,220),overlay.getPreferredLocation());
            assertNull(values.get("interfacelayout/anchorExternalOverlaysV1"));
        } finally {external.stop();}
    }
    @Test public void addingAnotherAnchorKeepsBothHeadersAndCornersOperable()throws Exception {
        AnchorPanel panel=panel();
        SwingUtilities.invokeAndWait(()->button(panel,"Add").doClick());flush();frame();
        assertEquals(2,store.snapshot().anchors.size());
        for(AnchorLayout.Anchor value:store.snapshot().anchors)moveAndResize(value.id);
    }
    @Test public void completedNativeDragDoesNotDisableEveryAnchorAfterAddingAnother()throws Exception {
        AnchorPanel panel=panel();SwingUtilities.invokeAndWait(()->button(panel,"Add").doClick());flush();frame();
        when(client.isDraggingWidget()).thenReturn(true);when(client.getDraggedWidget()).thenReturn(null);frame();
        for(AnchorLayout.Anchor value:store.snapshot().anchors)moveAndResize(value.id);
    }
    @Test public void activeNativeDragStillBlocksAnchorMovement()throws Exception {
        AnchorPanel panel=panel();SwingUtilities.invokeAndWait(()->button(panel,"Add").doClick());flush();frame();
        when(client.isDraggingWidget()).thenReturn(true);when(client.getDraggedWidget()).thenReturn(mock(net.runelite.api.widgets.Widget.class));frame();
        for(AnchorLayout.Anchor value:store.snapshot().anchors) {
            Rectangle r=coordinator.snapshot().geometry.anchors.get(value.id),header=AnchorEditor.headerBounds(r,coordinator.snapshot().canvas);
            assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+5,header.y+5)).isConsumed());
            assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,r.x+r.width-3,r.y+r.height-3)).isConsumed());
        }
    }
    @Test public void enabledAndLockedApplyImmediatelyAndReleaseOrProtectMembers()throws Exception {
        press();drag(255,255);release(255,255);flush();frame();AnchorPanel panel=panel();
        assertNull(button(panel,"Apply"));assertNull(button(panel,"Set offset"));
        SwingUtilities.invokeAndWait(()->check(panel,"Enabled").doClick());flush();frame();
        assertFalse(store.snapshot().find(anchor).enabled);assertFalse(participant.owns(control.id()));assertFalse(coordinator.snapshot().geometry.anchors.containsKey(anchor));
        refresh(panel);SwingUtilities.invokeAndWait(()->check(panel,"Enabled").doClick());flush();frame();
        assertTrue(store.snapshot().find(anchor).enabled);assertTrue(participant.owns(control.id()));
        SwingUtilities.invokeAndWait(()->check(panel,"Locked").doClick());flush();frame();
        assertTrue(store.snapshot().find(anchor).locked);assertTrue(participant.owns(control.id()));
        Rectangle r=coordinator.snapshot().geometry.anchors.get(anchor),header=AnchorEditor.headerBounds(r,coordinator.snapshot().canvas);
        assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,header.x+5,header.y+5)).isConsumed());
        assertFalse(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,r.x+r.width-3,r.y+r.height-3)).isConsumed());
        refresh(panel);SwingUtilities.invokeAndWait(()->check(panel,"Locked").doClick());flush();frame();moveAndResize(anchor);
    }
    @Test public void immediateFieldEditsDoNotOverwriteOtherChangesAndKeepTheForm()throws Exception {
        AnchorPanel panel=panel();javax.swing.JSpinner x=spinner(panel,"anchorX");
        coordinator.edit(next->next.find(anchor).y=350);
        SwingUtilities.invokeAndWait(()->{x.setValue(420);spinner(panel,"anchorPadding").setValue(8);check(panel,"Border").doClick();check(panel,"Background").doClick();});
        flush();frame();AnchorLayout.Anchor result=store.snapshot().find(anchor);
        assertEquals(420,result.x);assertEquals(350,result.y);assertEquals(8,result.padding);assertTrue(result.border);assertTrue(result.background);
        refresh(panel);assertSame(x,spinner(panel,"anchorX"));assertEquals(350,spinner(panel,"anchorY").getValue());
        long revision=store.revision();refresh(panel);flush();assertEquals(revision,store.revision());
    }
    @Test public void freeMemberOffsetsSaveWithoutASeparateButton()throws Exception {
        press();drag(255,255);release(255,255);flush();frame();AnchorPanel panel=panel();
        SwingUtilities.invokeAndWait(()->{spinner(panel,"memberX").setValue(70);spinner(panel,"memberY").setValue(80);});flush();frame();
        AnchorLayout.Member member=store.snapshot().find(anchor).members.get(0);assertEquals(70,member.x);assertEquals(80,member.y);
        assertEquals(new Point(270,280),control.bounds.getLocation());
    }
    private void moveAndResize(String id) {
        frame();Rectangle before=new Rectangle(coordinator.snapshot().geometry.anchors.get(id)),header=AnchorEditor.headerBounds(before,coordinator.snapshot().canvas);
        int x=header.x+10,y=header.y+8;
        assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,x,y)).isConsumed());drag(x+25,y+35);frame();release(x+25,y+35);flush();frame();
        Rectangle moved=new Rectangle(coordinator.snapshot().geometry.anchors.get(id));assertEquals(before.x+25,moved.x);assertEquals(before.y+35,moved.y);
        x=moved.x+moved.width-3;y=moved.y+moved.height-3;
        assertTrue(mice.processMousePressed(event(MouseEvent.MOUSE_PRESSED,x,y)).isConsumed());drag(x+40,y+45);frame();release(x+40,y+45);flush();frame();
        Rectangle sized=coordinator.snapshot().geometry.anchors.get(id);assertEquals(moved.width+40,sized.width);assertEquals(moved.height+45,sized.height);
    }
    private AnchorPanel panel()throws Exception {
        AnchorPanel[] result=new AnchorPanel[1];SwingUtilities.invokeAndWait(()->result[0]=new AnchorPanel(coordinator,new AnchorTransfers(),new AnchorTemplates(),new ExternalAnchorAdapter()));return result[0];
    }
    private static void refresh(AnchorPanel panel)throws Exception {
        SwingUtilities.invokeAndWait(()->{try{var method=AnchorPanel.class.getDeclaredMethod("refresh");method.setAccessible(true);method.invoke(panel);}catch(Exception failure){throw new AssertionError(failure);}});
    }
    private static javax.swing.JButton button(java.awt.Container parent,String text){return (javax.swing.JButton)find(parent,c->c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton)c).getText()));}
    private static javax.swing.JCheckBox check(java.awt.Container parent,String text){return (javax.swing.JCheckBox)find(parent,c->c instanceof javax.swing.JCheckBox && text.equals(((javax.swing.JCheckBox)c).getText()));}
    private static javax.swing.JSpinner spinner(java.awt.Container parent,String name){return (javax.swing.JSpinner)find(parent,c->c instanceof javax.swing.JSpinner && name.equals(c.getName()));}
    private static java.awt.Component find(java.awt.Container parent,java.util.function.Predicate<java.awt.Component> match) {
        for(java.awt.Component child:parent.getComponents()) {
            if(match.test(child))return child;
            if(child instanceof java.awt.Container){java.awt.Component found=find((java.awt.Container)child,match);if(found!=null)return found;}
        }
        return null;
    }
    private List<String> order(){return store.snapshot().find(anchor).members.stream().map(member->member.target).collect(java.util.stream.Collectors.toList());}
}
