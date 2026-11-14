package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CompassToggleIntegrationTest {
    private static final int[] COMPASSES={InterfaceID.Toplevel.MAPCONTAINER_GRAPHIC5,
        OrbConstants.Widgets.Classic.COMPASS,OrbConstants.Widgets.Modern.COMPASS};
    private static final int[] CLICKS={InterfaceID.Toplevel.COMPASSCLICK,
        InterfaceID.ToplevelOsrsStretch.COMPASSCLICK,InterfaceID.ToplevelPreEoc.COMPASSCLICK};
    private static final class Fixture {
        final CircularOrbs orbs=new CircularOrbs();
        final EventBus bus=new EventBus();
        final List<Runnable> queued=new ArrayList<>();
        final Widget compass,click,background;
        final Rectangle compassBounds,clickBounds;
        Fixture(int mode,int x) {
            orbs.client=mock(Client.class);orbs.manager=mock(OrbController.class);
            orbs.thread=mock(ClientThread.class);orbs.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
            orbs.sprites=mock(MenuButtonSprites.class);
            when(orbs.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(orbs.config.hideMinimap()).thenReturn(true);
            when(orbs.manager.isFixedMode()).thenReturn(mode==0);when(orbs.manager.isClassicResizable()).thenReturn(mode==1);
            doAnswer(i->{queued.add(i.getArgument(0));return null;}).when(orbs.thread).invoke(any(Runnable.class));
            compass=CircularOrbInputTest.widget(orbs.client,COMPASSES[mode],null,x,50,35,35);
            when(compass.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
            click=CircularOrbInputTest.widget(orbs.client,CLICKS[mode],null,x-1,49,37,37);
            when(click.getActions()).thenReturn(new String[]{"Look North","Look East","Look South","Look West"});
            click.setNoClickThrough(true);
            background=CircularOrbInputTest.widget(orbs.client,1,null,x-4,46,43,43);
            background.setOpacity(40);when(background.getSpriteId()).thenReturn(100);

            if(mode!=0)orbs.manager.compassFrame=background;
            compassBounds=compass.getBounds();clickBounds=click.getBounds();
            clearInvocations(compass,click,background);
            bus.register(orbs);orbs.start();
        }
        void setCircular(boolean value) {
            when(orbs.config.hideOrbTabs()).thenReturn(value);
            ConfigChanged event=new ConfigChanged();event.setGroup("interfacelayout");event.setKey("hideOrbTabs");
            bus.post(event);flush();bus.post(new BeforeRender());
        }
        void flush() {while(!queued.isEmpty())queued.remove(0).run();}
        int paintedPixels() {
            BufferedImage image=new BufferedImage(400,150,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();orbs.frames().render(g);g.dispose();
            int pixels=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=0)pixels++;
            return pixels;
        }
        void unchangedNativeCompass() {
            assertEquals(compassBounds,compass.getBounds());assertEquals(clickBounds,click.getBounds());
            assertTrue(click.getNoClickThrough());assertEquals(0,compass.getOpacity());
            assertArrayEquals(new String[]{"Look North","Look East","Look South","Look West"},click.getActions());
            for(Widget w:new Widget[]{compass,click}) {
                verify(w,never()).setOriginalX(anyInt());verify(w,never()).setOriginalY(anyInt());
                verify(w,never()).setOriginalWidth(anyInt());verify(w,never()).setOriginalHeight(anyInt());
                verify(w,never()).setForcedPosition(anyInt(),anyInt());verify(w,never()).setAction(anyInt(),anyString());
                verify(w,never()).setHidden(anyBoolean());verify(w,never()).setNoClickThrough(anyBoolean());
            }
            assertEquals(new Rectangle(compassBounds.x-4,46,43,43),background.getBounds());
            assertEquals(100,background.getSpriteId());verify(background,never()).setSpriteId(anyInt());
        }
    }

    @Test public void repeatedTogglesRestoreBackgroundAndPreserveClickAreaInEveryDisplayMode() {
        for(int mode=0;mode<3;mode++)for(int x:new int[]{50,250}) {
            Fixture f=new Fixture(mode,x);
            for(int toggle=0;toggle<4;toggle++) {
                f.setCircular(true);assertTrue(f.paintedPixels()>0);
                assertEquals(mode==0?40:255,f.background.getOpacity());f.unchangedNativeCompass();
                f.setCircular(false);assertEquals(40,f.background.getOpacity());assertEquals(0,f.paintedPixels());
                f.unchangedNativeCompass();
            }
        }
    }

    @Test public void nestedLayoutUpdatesKeepTheOriginalFrameAvailableUntilTheyFinish() {
        Fixture f=new Fixture(2,100);f.setCircular(true);
        f.bus.post(new ScriptPreFired(1000));f.bus.post(new ScriptPreFired(2000));
        f.bus.post(new ScriptPostFired(2000));assertEquals(40,f.background.getOpacity());assertEquals(0,f.paintedPixels());
        f.background.setOpacity(75);f.bus.post(new ScriptPostFired(1000));assertEquals(255,f.background.getOpacity());
        f.setCircular(false);assertEquals(75,f.background.getOpacity());f.unchangedNativeCompass();
    }

    @Test public void editingLogoutAndPluginDisableReleaseTheFrameWithoutUnhidingGameWidgets() {
        Fixture f=new Fixture(1,100);f.setCircular(true);
        when(f.orbs.config.editInterface()).thenReturn(true);f.bus.post(new BeforeRender());
        assertEquals(40,f.background.getOpacity());assertEquals(0,f.paintedPixels());
        when(f.orbs.config.editInterface()).thenReturn(false);f.bus.post(new BeforeRender());
        when(f.compass.isHidden()).thenReturn(true);when(f.click.isHidden()).thenReturn(true);
        f.setCircular(false);assertEquals(40,f.background.getOpacity());assertTrue(f.compass.isHidden());assertTrue(f.click.isHidden());
        when(f.compass.isHidden()).thenReturn(false);when(f.click.isHidden()).thenReturn(false);f.setCircular(true);
        GameStateChanged logout=new GameStateChanged();logout.setGameState(GameState.LOGIN_SCREEN);f.bus.post(logout);
        assertEquals(40,f.background.getOpacity());assertEquals(0,f.paintedPixels());
        f.bus.post(new BeforeRender());f.orbs.stop();f.flush();
        assertEquals(40,f.background.getOpacity());assertEquals(0,f.paintedPixels());f.unchangedNativeCompass();
    }
}
