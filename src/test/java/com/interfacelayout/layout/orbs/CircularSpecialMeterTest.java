package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbTimerStyle;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularSpecialMeterTest
{
    private static final Color FILL=new Color(225,95,170);
    private final CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture();
    private Widget meter,fill;

    private void setup()
    {
        meter=CircularOrbInputTest.widget(f.overlay.client,InterfaceID.Orbs.ORB_SPECENERGY,f.parent,0,26,33,4);
        when(f.overlay.client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY)).thenReturn(f.parent);
        when(meter.getIndex()).thenReturn(0);when(meter.getParentId()).thenReturn(InterfaceID.Orbs.ORB_SPECENERGY);when(meter.getType()).thenReturn(WidgetType.LAYER);
        AtomicBoolean hidden=new AtomicBoolean();when(meter.isHidden()).thenAnswer(i->hidden.get());when(meter.isSelfHidden()).thenAnswer(i->hidden.get());
        doAnswer(i->{hidden.set(i.getArgument(0));return meter;}).when(meter).setHidden(anyBoolean());
        fill=CircularOrbInputTest.widget(f.overlay.client,7004,meter,0,0,20,4);
        when(fill.getType()).thenReturn(WidgetType.RECTANGLE);when(fill.isFilled()).thenReturn(true);when(fill.getTextColor()).thenReturn(FILL.getRGB());
        when(meter.getChildren()).thenReturn(new Widget[]{fill});when(f.parent.getDynamicChildren()).thenReturn(new Widget[]{meter});when(f.parent.getChild(0)).thenReturn(meter);
    }

    @Test public void meterMovesBelowOrbAndTracksNativeFillWithoutChangingItsInput()
    {
        setup();Rectangle original=meter.getBounds();f.refresh();assertTrue(meter.isSelfHidden());
        assertEquals(new Rectangle(124,107,20,4),colored(f.render()));
        fill.setOriginalWidth(9);f.refresh();assertEquals(new Rectangle(124,107,9,4),colored(f.render()));
        f.overlay.releaseForLayout();assertFalse(meter.isSelfHidden());assertEquals(original,meter.getBounds());
        verify(meter,never()).setAction(anyInt(),anyString());verify(fill,never()).setOriginalHeight(anyInt());
    }

    @Test public void tagTimerGetsItsOwnGapAndBothMoveWithTheOrb()
    {
        setup();when(f.overlay.config.specialTimerStyle()).thenReturn(OrbTimerStyle.TAG_METERS);
        f.refresh();assertEquals(new Rectangle(124,111,20,4),colored(f.render()));
        f.overlay.releaseForLayout();f.parent.setOriginalX(130);f.refresh();assertEquals(new Rectangle(154,111,20,4),colored(f.render()));
    }

    @Test public void coveredMeterStaysBehindBankAndOtherOrbs()
    {
        setup();Widget bank=CircularOrbInputTest.widget(f.overlay.client,InterfaceID.Bankmain.UNIVERSE,null,120,106,50,15);
        f.refresh();assertTrue(meter.isSelfHidden());assertTrue(colored(f.render()).isEmpty());
        when(bank.isHidden()).thenReturn(true);f.refresh();assertFalse(colored(f.render()).isEmpty());
    }

    @Test public void nativeHiddenOrUnsupportedContentIsNeverForcedVisibleOrReplaced()
    {
        setup();meter.setHidden(true);f.refresh();assertTrue(colored(f.render()).isEmpty());f.overlay.releaseForLayout();assertTrue(meter.isSelfHidden());
        meter.setHidden(false);when(fill.getType()).thenReturn(WidgetType.TEXT);f.refresh();assertFalse(meter.isSelfHidden());assertTrue(colored(f.render()).isEmpty());
    }

    @Test public void leavingCircularModeOrEditingRestoresTheNativeMeter()
    {
        setup();f.refresh();assertTrue(meter.isSelfHidden());
        when(f.overlay.config.hideOrbTabs()).thenReturn(false);f.refresh();assertFalse(meter.isSelfHidden());assertTrue(colored(f.render()).isEmpty());
        when(f.overlay.config.hideOrbTabs()).thenReturn(true);f.refresh();assertTrue(meter.isSelfHidden());
        when(f.overlay.config.editInterface()).thenReturn(true);f.refresh();assertFalse(meter.isSelfHidden());assertTrue(colored(f.render()).isEmpty());
    }

    private Rectangle colored(BufferedImage image)
    {
        Rectangle bounds=new Rectangle();
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==FILL.getRGB())
            bounds=bounds.isEmpty()?new Rectangle(x,y,1,1):bounds.union(new Rectangle(x,y,1,1));
        return bounds;
    }
}
