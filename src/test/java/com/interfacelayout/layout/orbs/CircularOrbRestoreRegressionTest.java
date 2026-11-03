package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
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

public class CircularOrbRestoreRegressionTest {
    private static final class Fixture {
        final CircularOrbs orbs = new CircularOrbs();
        final EventBus bus = new EventBus();
        final List<Runnable> queued = new ArrayList<>();
        final Widget backing, button, number;
        Fixture() {
            orbs.client=mock(Client.class);orbs.manager=mock(OrbController.class);
            orbs.thread=mock(ClientThread.class);orbs.sprites=mock(MenuButtonSprites.class);
            orbs.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
            when(orbs.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(orbs.config.hideOrbTabs()).thenReturn(true);
            doAnswer(i->{queued.add(i.getArgument(0));return null;}).when(orbs.thread).invoke(any(Runnable.class));
            Widget parent=CircularOrbInputTest.widget(orbs.client,InterfaceID.Orbs.ORB_PRAYER,null,100,70,57,34);
            backing=CircularOrbInputTest.widget(orbs.client,InterfaceID.Orbs.PRAYER_BACKING,parent,0,0,57,34);
            button=CircularOrbInputTest.widget(orbs.client,InterfaceID.Orbs.PRAYERBUTTON,parent,0,0,57,34);
            CircularOrbInputTest.widget(orbs.client,InterfaceID.Orbs.PRAYER_INDICATOR,parent,27,4,26,26);
            Widget icon=CircularOrbInputTest.widget(orbs.client,com.interfacelayout.layout.orbs.widget.elements.Orbs.PRAYER_ORB_CONTAINER.getIconId(),parent,27,4,26,26);
            number=CircularOrbInputTest.widget(orbs.client,InterfaceID.Orbs.PRAYER_TEXT,parent,0,0,25,34);
            when(number.getText()).thenReturn("52");when(orbs.sprites.get(icon)).thenReturn(new BufferedImage(26,26,2));
            bus.register(orbs);orbs.start();bus.post(new BeforeRender());
        }
        void nativeAppearance() {
            assertEquals(57,backing.getOriginalWidth());assertEquals(57,button.getOriginalWidth());
            assertEquals(25,number.getOriginalWidth());assertEquals(0,backing.getOpacity());assertEquals(0,number.getOpacity());
        }
    }

    @Test public void nestedScriptReturnsCannotExposeTemporaryGeometryToTheirCaller() {
        Fixture f=new Fixture();assertEquals(34,f.backing.getOriginalWidth());
        f.bus.post(new ScriptPreFired(1000));f.bus.post(new ScriptPreFired(2000));
        f.bus.post(new ScriptPostFired(2000));f.nativeAppearance();

        f.backing.setOriginalX(1);f.button.setOriginalX(1);
        f.bus.post(new ScriptPostFired(1000));assertEquals(34,f.backing.getOriginalWidth());
        when(f.orbs.config.hideOrbTabs()).thenReturn(false);f.bus.post(new ClientTick());
        f.nativeAppearance();assertEquals(1,f.backing.getOriginalX());assertEquals(1,f.button.getOriginalX());
    }

    @Test public void disablingReleasesArtworkAndClickSpaceBeforeQueuedLayoutRefresh() {
        Fixture f=new Fixture();when(f.orbs.config.hideOrbTabs()).thenReturn(false);
        ConfigChanged changed=new ConfigChanged();changed.setGroup("interfacelayout");changed.setKey("hideOrbTabs");
        f.bus.post(changed);

        assertEquals(255,f.backing.getOpacity());assertEquals(1,f.queued.size());
        f.queued.remove(0).run();f.nativeAppearance();
        for(int frame=0;frame<5;frame++){f.bus.post(new BeforeRender());f.nativeAppearance();}
    }

    @Test public void abortedScriptDoesNotLeaveOrbsInNativeModeForever() {
        Fixture f=new Fixture();f.bus.post(new ScriptPreFired(1000));f.nativeAppearance();
        f.bus.post(new BeforeRender());assertEquals(34,f.button.getOriginalWidth());
        when(f.orbs.config.hideOrbTabs()).thenReturn(false);f.bus.post(new BeforeRender());f.nativeAppearance();
    }
}
