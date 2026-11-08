package com.interfacelayout.layout.orbs;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.Config;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbCoreOverlayTest
{
    static final class Core
    {
        final Map<String,Object> dependencies=new HashMap<>();
        final TooltipManager tips=mock(TooltipManager.class);
        final Overlay overlay;
        Core(Client client,String name) throws Exception
        {
            dependencies.put(Client.class.getName(),client);
            dependencies.put(TooltipManager.class.getName(),tips);
            Class<?> type=Class.forName("net.runelite.client.plugins."+name);
            Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
            Object[] args=new Object[ctor.getParameterCount()];
            for(int i=0;i<args.length;i++) {
                Class<?> parameter=ctor.getParameterTypes()[i];
                args[i]=dependencies.computeIfAbsent(parameter.getName(),k->Config.class.isAssignableFrom(parameter)?mock(parameter,CALLS_REAL_METHODS):mock(parameter));
            }
            overlay=(Overlay)ctor.newInstance(args);
        }
        void stub(String type,String method,Object value) throws Exception
        {
            Class<?> c=Class.forName("net.runelite.client.plugins."+type);
            java.lang.reflect.Method m=c.getDeclaredMethod(method);m.setAccessible(true);
            when(m.invoke(dependencies.get(c.getName()))).thenReturn(value);
        }
        BufferedImage render()
        {
            BufferedImage image=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();try {overlay.render(g);}finally {g.dispose();}return image;
        }
    }

    @Test public void actualPrayerDoseRingAndTooltipsStayAlignedOnAndOffHover() throws Exception
    {
        CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture(com.interfacelayout.layout.orbs.widget.elements.Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
        Core core=new Core(f.overlay.client,"prayer.PrayerDoseOverlay");
        core.stub("prayer.PrayerConfig","showPrayerDoseIndicator",true);
        core.stub("prayer.PrayerConfig","showPrayerStatistics",true);
        core.stub("prayer.PrayerConfig","prayerDoseOrbStartColor",Color.CYAN);
        when(f.overlay.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        when(f.overlay.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(1);
        Field amount=core.overlay.getClass().getDeclaredField("restoreAmount");amount.setAccessible(true);amount.setInt(core.overlay,30);
        for(Point mouse:new Point[]{new Point(140,87),new Point(105,85),new Point(124,71),new Point(10,10),new Point(156,87)}) {
            clearInvocations(core.tips);
            when(f.overlay.client.getMouseCanvasPosition()).thenReturn(mouse);f.refresh();
            Rectangle painted=visible(core.render());
            assertEquals(140,painted.getCenterX(),1);assertEquals(87,painted.getCenterY(),1);
            assertTrue(painted.width>=36);
            boolean hovered=CircularOrbArtwork.contains(new Rectangle(123,70,34,34),mouse.getX(),mouse.getY());
            verify(core.tips,times(hovered?1:0)).add(any());
            assertEquals(hovered,f.button.getBounds().contains(mouse.getX(),mouse.getY()));
            assertFalse(f.backing.getBounds().isEmpty());assertFalse(f.number.getBounds().isEmpty());
        }
        when(f.overlay.config.hideOrbTabs()).thenReturn(false);f.refresh();
        assertEquals(new Rectangle(100,70,57,34),f.parent.getBounds());
        assertEquals(new Rectangle(100,70,57,34),f.button.getBounds());
    }

    @Test public void actualHpAndSpecialRegenRingsFollowTheirOwnCircularAnchors() throws Exception
    {
        com.interfacelayout.layout.orbs.widget.elements.Orbs[] types={com.interfacelayout.layout.orbs.widget.elements.Orbs.HP_ORB_CONTAINER,com.interfacelayout.layout.orbs.widget.elements.Orbs.SPEC_ORB_CONTAINER};
        int[] text={InterfaceID.Orbs.HEALTH_TEXT,InterfaceID.Orbs.SPECENERGY_TEXT};
        for(int i=0;i<types.length;i++) {
            CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture(types[i],text[i]);
            Core core=new Core(f.overlay.client,"regenmeter.RegenMeterOverlay");
            core.stub("regenmeter.RegenMeterConfig","showHitpoints",true);core.stub("regenmeter.RegenMeterConfig","showSpecial",true);
            core.stub("regenmeter.RegenMeterPlugin","getHitpointsPercentage",1.0);core.stub("regenmeter.RegenMeterPlugin","getSpecialPercentage",1.0);
            for(int move:new int[]{0,80}) {
                f.overlay.releaseForLayout();f.parent.setOriginalX(100+move);
                when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(10,10));f.refresh();
                Rectangle painted=visible(core.render());
                assertEquals(140+move,painted.getCenterX(),1);assertEquals(87,painted.getCenterY(),1);
                assertTrue(painted.width>=26);assertTrue(painted.width<=29);
            }
        }
    }

    @Test public void actualPrayerFlickIndicatorRemainsOnTheOrbWithoutHover() throws Exception
    {
        CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture(com.interfacelayout.layout.orbs.widget.elements.Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
        Core core=new Core(f.overlay.client,"prayer.PrayerFlickOverlay");
        core.stub("prayer.PrayerConfig","prayerFlickAlwaysOn",true);
        core.stub("prayer.PrayerConfig","prayerFlickLocation",net.runelite.client.plugins.prayer.PrayerFlickLocation.PRAYER_ORB);
        core.stub("prayer.PrayerConfig","prayerFlickColor",Color.CYAN);
        core.stub("prayer.PrayerPlugin","getTickProgress",Math.PI/2);
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(10,10));f.refresh();
        Rectangle painted=visible(core.render());
        assertEquals(140,painted.getCenterX(),1);assertEquals(87,painted.getCenterY(),1);
        assertTrue(painted.height>=34);
    }

    static Rectangle visible(BufferedImage image)
    {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0) {
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        return new Rectangle(left,top,right-left+1,bottom-top+1);
    }
}
