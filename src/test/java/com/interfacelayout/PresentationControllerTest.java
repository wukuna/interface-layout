package com.interfacelayout;

import com.interfacelayout.layout.PresentationController;
import com.interfacelayout.layout.orbs.OrbConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PresentationControllerTest
{
    private final PresentationController controller = new PresentationController();
    private final Client client = mock(Client.class);
    private final ClientThread thread = mock(ClientThread.class);
    private final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
    private final Widget orb = mock(Widget.class);
    private final List<Runnable> pending = new ArrayList<>();
    private Widget meter;

    @Before public void setup() throws Exception
    {
        inject("client", client); inject("clientThread", thread); inject("config", config);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY)).thenReturn(orb);
        when(orb.getId()).thenReturn(InterfaceID.Orbs.ORB_SPECENERGY);
        doAnswer(i -> { ((Runnable)i.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
        doAnswer(i -> { pending.add(i.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        meter = meter(false);
        install(meter);
    }

    @Test public void defaultLeavesEveryWidgetUntouched()
    {
        assertFalse(config.hideExtraSpecialAttackBar());
        controller.start(); flush(); controller.onBeforeRender(new BeforeRender());
        verify(meter, never()).setHidden(anyBoolean());
        verify(orb, never()).setHidden(anyBoolean());
    }

    @Test public void hidesOnlySecondaryMeterAndDoesNotRewriteEveryFrame()
    {
        Widget number = mock(Widget.class);
        Widget regeneration = meter(false);
        when(regeneration.getOriginalWidth()).thenReturn(27);
        Widget unknownLayer = meter(false);
        when(unknownLayer.getOriginalHeight()).thenReturn(6);
        when(orb.getDynamicChildren()).thenReturn(new Widget[]{null, number, meter, regeneration, unknownLayer});
        enable();
        for (int i=0;i<20;i++) controller.onBeforeRender(new BeforeRender());
        assertTrue(meter.isSelfHidden());
        verify(meter, times(1)).setHidden(true);
        verify(orb, never()).setHidden(anyBoolean());
        verify(number, never()).setHidden(anyBoolean());
        verify(regeneration, never()).setHidden(anyBoolean());
        verify(unknownLayer, never()).setHidden(anyBoolean());
    }

    @Test public void disablingRestoresOriginallyVisibleMeter()
    {
        enable(); assertTrue(meter.isSelfHidden());
        when(config.hideExtraSpecialAttackBar()).thenReturn(false);
        ConfigChanged changed=new ConfigChanged(); changed.setGroup("interfacelayout"); changed.setKey("hideExtraSpecialAttackBar");
        controller.onConfigChanged(changed); flush();
        assertFalse(meter.isSelfHidden());
    }

    @Test public void disablingDoesNotRevealGameHiddenMeter()
    {
        meter=meter(true); install(meter); enable(); controller.stop();
        assertTrue(meter.isSelfHidden());
        verify(meter, never()).setHidden(false);
    }

    @Test public void nativeUpdateOwnsItsNewVisibility()
    {
        enable();
        controller.onScriptPreFired(new ScriptPreFired(OrbConstants.Script.ORBS_UPDATE_SPECENERGY));
        assertFalse(meter.isSelfHidden());
        meter.setHidden(true); // Native update now hides the meter itself.
        controller.onScriptPostFired(new ScriptPostFired(OrbConstants.Script.ORBS_UPDATE_SPECENERGY));
        clearInvocations(meter);
        controller.stop();
        assertTrue(meter.isSelfHidden()); verify(meter, never()).setHidden(false);
    }

    @Test public void weaponOrGameframeRebuildDiscardsOldWidgetAndHidesReplacement()
    {
        enable(); Widget old=meter;
        meter=meter(false); install(meter); clearInvocations(old);
        controller.onBeforeRender(new BeforeRender());
        assertTrue(meter.isSelfHidden());
        controller.stop();
        assertFalse(meter.isSelfHidden());
        verify(old, never()).setHidden(anyBoolean());
    }

    @Test public void queuedStartupCannotHideMeterAfterShutdown()
    {
        when(config.hideExtraSpecialAttackBar()).thenReturn(true);
        controller.start(); controller.stop(); flush();
        assertFalse(meter.isSelfHidden()); verify(meter, never()).setHidden(anyBoolean());
    }

    @Test public void missingOrbOrChildrenAreSafe()
    {
        when(client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY)).thenReturn(null);
        enable(); controller.onBeforeRender(new BeforeRender());
        when(client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY)).thenReturn(orb);
        when(orb.getDynamicChildren()).thenReturn(null);
        controller.onBeforeRender(new BeforeRender()); controller.stop();
    }

    private void enable() { when(config.hideExtraSpecialAttackBar()).thenReturn(true); controller.start(); flush(); }
    private void flush() { List<Runnable> work=new ArrayList<>(pending); pending.clear(); work.forEach(Runnable::run); }
    private void install(Widget widget) { when(orb.getDynamicChildren()).thenReturn(new Widget[]{widget}); when(orb.getChild(0)).thenReturn(widget); }
    private Widget meter(boolean initiallyHidden)
    {
        Widget widget=mock(Widget.class); boolean[] hidden={initiallyHidden};
        when(widget.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0]=i.getArgument(0); return null; }).when(widget).setHidden(anyBoolean());
        when(widget.getParentId()).thenReturn(InterfaceID.Orbs.ORB_SPECENERGY);
        when(widget.getType()).thenReturn(WidgetType.LAYER);
        when(widget.getIndex()).thenReturn(0);
        when(widget.getOriginalWidth()).thenReturn(33); when(widget.getOriginalHeight()).thenReturn(4);
        return widget;
    }
    private void inject(String name,Object value) throws Exception
    {
        Field field=PresentationController.class.getDeclaredField(name); field.setAccessible(true); field.set(controller,value);
    }
}
