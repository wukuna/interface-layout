package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NativeOrbTimerTest {
    private CircularOrbOverlayTest.Fixture fixture(Orbs orb,int number) {
        CircularOrbOverlayTest.Fixture f=new CircularOrbOverlayTest.Fixture(orb,number);
        when(f.overlay.config.hideOrbTabs()).thenReturn(false);
        when(f.overlay.client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(f.overlay.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        when(f.overlay.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(54);
        when(f.overlay.client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        when(f.overlay.client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
        f.overlay.onGameTick(new GameTick());
        for(int i=0;i<23;i++){
            when(f.overlay.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(54-(i+1)/5);
            f.overlay.timers.tick(f.overlay.client,null,Long.MAX_VALUE/2);
        }
        f.refresh();
        return f;
    }

    @Test public void nativeStylesStayWithinTheirOrbAndLeaveWidgetGeometryAndInputAlone() {
        Orbs[] orbs={Orbs.HP_ORB_CONTAINER,Orbs.PRAYER_ORB_CONTAINER,Orbs.SPEC_ORB_CONTAINER};
        int[] numbers={InterfaceID.Orbs.HEALTH_TEXT,InterfaceID.Orbs.PRAYER_TEXT,InterfaceID.Orbs.SPECENERGY_TEXT};
        for(int i=0;i<orbs.length;i++)for(boolean fixed:new boolean[]{true,false})for(NativeTimerStyle style:NativeTimerStyle.values()) {
            CircularOrbOverlayTest.Fixture f=fixture(orbs[i],numbers[i]);
            when(f.overlay.manager.isFixedMode()).thenReturn(fixed);
            when(f.overlay.config.nativeOrbTimerStyle()).thenReturn(style);
            Widget[] widgets={f.parent,f.backing,f.button,f.indicator,f.icon,f.number};
            clearInvocations((Object[])widgets);
            f.refresh();
            BufferedImage image=f.render();
            assertTrue(pixels(image)>10);
            Rectangle inset=new Rectangle(128,75,24,24);
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                if((image.getRGB(x,y)>>>24)>0) {
                    assertTrue(new Rectangle(100,69,58,36).contains(x,y));
                    if(orbs[i]==Orbs.PRAYER_ORB_CONTAINER && style==NativeTimerStyle.QUIET_RIMS) {
                        double dx=x+.5-inset.getCenterX(),dy=y+.5-inset.getCenterY();
                        assertTrue(style+" "+x+","+y,dx*dx+dy*dy>=10*10);
                    }
                }
            assertEquals(new Rectangle(100,70,57,34),f.parent.getBounds());
            assertEquals(new Rectangle(100,70,25,34),f.number.getBounds());
            for(Widget widget:widgets) {
                verify(widget,never()).setHidden(anyBoolean());
                verify(widget,never()).setOpacity(anyInt());
                verify(widget,never()).setOriginalWidth(anyInt());
                verify(widget,never()).setForcedPosition(anyInt(),anyInt());
                verify(widget,never()).setNoClickThrough(anyBoolean());
                verify(widget,never()).setAction(anyInt(),anyString());
            }
        }
    }

    @Test public void paddedPackFillAndDraggingKeepWedgeCenteredOnVisibleInset() {
        CircularOrbOverlayTest.Fixture f=fixture(Orbs.HP_ORB_CONTAINER,InterfaceID.Orbs.HEALTH_TEXT);
        BufferedImage padded=new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=padded.createGraphics();graphics.setColor(Color.WHITE);graphics.fillOval(0,2,22,22);graphics.dispose();
        when(f.overlay.sprites.getPadded(f.indicator)).thenReturn(padded);
        f.parent.setOriginalX(120);f.parent.revalidate();f.refresh();
        Rectangle expected=new Rectangle(148,77,20,20);
        BufferedImage image=f.render();assertTrue(pixels(image)>10);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if((image.getRGB(x,y)>>>24)>0)assertTrue(expected.contains(x,y));
    }

    @Test public void hiddenCoveredDisabledEditingAndLoggedOutOrbsShowNoWedge() {
        CircularOrbOverlayTest.Fixture f=fixture(Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
        when(f.overlay.config.orbPrayerTimer()).thenReturn(false);assertEquals(0,pixels(f.render()));
        when(f.overlay.config.orbPrayerTimer()).thenReturn(true);
        when(f.parent.isHidden()).thenReturn(true);f.refresh();assertEquals(0,pixels(f.render()));
        when(f.parent.isHidden()).thenReturn(false);
        when(f.overlay.config.editInterface()).thenReturn(true);f.refresh();assertEquals(0,pixels(f.render()));
        when(f.overlay.config.editInterface()).thenReturn(false);
        Widget bank=CircularOrbInputTest.widget(f.overlay.client,InterfaceID.Bankmain.UNIVERSE,null,120,70,100,80);
        f.refresh();assertEquals(0,pixels(f.render()));
        when(bank.isHidden()).thenReturn(true);f.refresh();assertTrue(pixels(f.render())>0);
        GameStateChanged logout=new GameStateChanged();logout.setGameState(GameState.LOGIN_SCREEN);
        when(f.overlay.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        f.overlay.onGameStateChanged(logout);f.refresh();assertEquals(0,pixels(f.render()));
    }

    @Test public void clockwiseFillAndCounterclockwiseDepletionUseTheSameTransparentSector() {
        Rectangle inset=new Rectangle(10,10,24,24);
        BufferedImage quarter=wedge(inset,.25),threeQuarters=wedge(inset,.75),full=wedge(inset,1);
        assertNotEquals(0,quarter.getRGB(27,17));
        assertEquals(0,quarter.getRGB(17,17));
        assertEquals(0,quarter.getRGB(17,27));
        assertNotEquals(0,threeQuarters.getRGB(17,27));
        assertEquals(0,threeQuarters.getRGB(17,17));
        assertNotEquals(0,full.getRGB(17,17));
        assertTrue(pixels(quarter)<pixels(threeQuarters));
        assertTrue(pixels(threeQuarters)<pixels(full));
        assertEquals(0,pixels(wedge(inset,0)));
        assertEquals(0,pixels(wedge(inset,Double.NaN)));
    }

    @Test public void outerPotionReminderSurvivesAllStylesInEitherRenderOrder() throws Exception {
        for(boolean first:new boolean[]{true,false})for(NativeTimerStyle style:NativeTimerStyle.values()) {
            CircularOrbOverlayTest.Fixture f=fixture(Orbs.PRAYER_ORB_CONTAINER,InterfaceID.Orbs.PRAYER_TEXT);
            when(f.overlay.config.nativeOrbTimerStyle()).thenReturn(style);
            CircularOrbCoreOverlayTest.Core core=new CircularOrbCoreOverlayTest.Core(f.overlay.client,"prayer.PrayerDoseOverlay");
            core.stub("prayer.PrayerConfig","showPrayerDoseIndicator",true);
            core.stub("prayer.PrayerConfig","prayerDoseOrbStartColor",Color.MAGENTA);
            java.lang.reflect.Field amount=core.overlay.getClass().getDeclaredField("restoreAmount");
            amount.setAccessible(true);amount.setInt(core.overlay,30);
            java.lang.reflect.Field pulse=core.overlay.getClass().getDeclaredField("startOfLastTick");
            pulse.setAccessible(true);pulse.set(core.overlay,java.time.Instant.EPOCH);
            BufferedImage expected=core.render(),actual=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=actual.createGraphics();
            if(first)core.overlay.render(graphics);
            f.overlay.render(graphics);
            if(!first)core.overlay.render(graphics);
            graphics.dispose();int checked=0;
            Rectangle inset=new Rectangle(128,75,24,24);
            for(int y=0;y<expected.getHeight();y++)for(int x=0;x<expected.getWidth();x++)
                if(!inset.contains(x,y) && (expected.getRGB(x,y)>>>24)>0) {
                    assertEquals(style+" "+x+","+y,expected.getRGB(x,y),actual.getRGB(x,y));checked++;
                }
            assertTrue(checked>20);
        }
    }

    private static BufferedImage wedge(Rectangle inset,double progress) {
        BufferedImage image=new BufferedImage(50,50,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();OrbTimers.paintWedge(graphics,inset,OrbTimers.PRAYER_COLOR,progress);graphics.dispose();return image;
    }
    static int pixels(BufferedImage image) {
        int count=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0)count++;
        return count;
    }
}
