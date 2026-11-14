package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.regenmeter.RegenMeterConfig;
import net.runelite.client.plugins.regenmeter.RegenMeterPlugin;
import net.runelite.client.plugins.prayer.PrayerConfig;
import net.runelite.client.plugins.prayer.PrayerPlugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativeOrbTimerCompatibilityTest
{
    final NativeOrbTimerCompatibility adapter=new NativeOrbTimerCompatibility();
    final List<Overlay> active=new ArrayList<>();
    public NativeOrbTimerCompatibilityTest() {
        adapter.client=mock(Client.class);adapter.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
        adapter.overlays=mock(OverlayManager.class);
        when(adapter.overlays.anyMatch(any())).thenAnswer(i->{Predicate<Overlay> p=i.getArgument(0);return new ArrayList<>(active).stream().anyMatch(p);});
        when(adapter.overlays.add(any())).thenAnswer(i->active.add(i.getArgument(0)));
        when(adapter.overlays.remove(any())).thenAnswer(i->active.remove(i.getArgument(0)));
    }
    Overlay create(String name,Object...args) throws Exception {
        Constructor<?> ctor=Class.forName(name).getDeclaredConstructors()[0];ctor.setAccessible(true);
        Overlay result=(Overlay)ctor.newInstance(args);active.add(result);return result;
    }
    Rectangle painted(int id,Rectangle circle,boolean replaced) {
        BufferedImage image=new BufferedImage(300,200,2);Graphics2D g=image.createGraphics();
        adapter.paint(g,id,circle,replaced);g.dispose();Rectangle result=new Rectangle();
        for(int y=0;y<200;y++)for(int x=0;x<300;x++)if((image.getRGB(x,y)>>>24)!=0)
            result=result.isEmpty()?new Rectangle(x,y,1,1):result.union(new Rectangle(x,y,1,1));
        return result;
    }
    @Test public void actualCoreRegenRingsFollowTheDisplayedCircleAndAvoidDuplicateCustomTimers() throws Exception {
        RegenMeterPlugin plugin=mock(RegenMeterPlugin.class);when(plugin.getSpecialPercentage()).thenReturn(1d);
        when(plugin.getHitpointsPercentage()).thenReturn(1d);
        RegenMeterConfig config=mock(RegenMeterConfig.class);when(config.showSpecial()).thenReturn(true);when(config.showHitpoints()).thenReturn(true);
        Overlay source=create("net.runelite.client.plugins.regenmeter.RegenMeterOverlay",adapter.client,plugin,config);
        adapter.refresh(true);assertEquals(OverlayLayer.MANUAL,source.getLayer());
        for(int component:new int[]{InterfaceID.Orbs.ORB_HEALTH,InterfaceID.Orbs.ORB_SPECENERGY}) {
            CircularOrbInputTest.widget(adapter.client,component,null,100,70,57,34);
            when(adapter.config.disableOrbTimers()).thenReturn(true);
            for(int x:new int[]{100,123,0,210}) {
                Rectangle circle=new Rectangle(x,70,34,34),pixels=painted(component,circle,false);
                assertFalse(pixels.isEmpty());assertTrue(circle.contains(pixels));
                assertEquals(circle.getCenterX(),pixels.getCenterX(),1);
            }
            when(adapter.config.disableOrbTimers()).thenReturn(false);
            assertTrue(painted(component,new Rectangle(100,70,34,34),false).isEmpty());
        }
        adapter.restore();assertEquals(OverlayLayer.ABOVE_WIDGETS,source.getLayer());assertTrue(active.contains(source));
    }
    @Test public void actualPrayerDoseAlertFollowsMirroringWithoutTakingOverItsSettings() throws Exception {
        PrayerConfig config=mock(PrayerConfig.class,CALLS_REAL_METHODS);when(config.showPrayerDoseIndicator()).thenReturn(true);
        when(config.showPrayerStatistics()).thenReturn(false);
        when(adapter.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);when(adapter.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        when(adapter.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(0,0));
        CircularOrbInputTest.widget(adapter.client,InterfaceID.Orbs.PRAYERBUTTON,null,100,70,57,34);
        Overlay source=create("net.runelite.client.plugins.prayer.PrayerDoseOverlay",adapter.client,mock(TooltipManager.class),mock(PrayerPlugin.class),config);
        Field restore=source.getClass().getDeclaredField("restoreAmount");restore.setAccessible(true);restore.set(source,30);
        adapter.refresh(true);
        Rectangle circle=new Rectangle(100,70,34,34),pixels=painted(InterfaceID.Orbs.ORB_PRAYER,circle,false);
        assertFalse(pixels.isEmpty());assertEquals(circle.getCenterX(),pixels.getCenterX(),1);
        active.remove(source);adapter.refresh(true);assertFalse(active.contains(source));
        assertEquals(OverlayLayer.ABOVE_WIDGETS,source.getLayer());
    }
}
