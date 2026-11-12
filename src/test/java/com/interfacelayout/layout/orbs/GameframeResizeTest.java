package com.interfacelayout.layout.orbs;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.menu.MenuOverlayBridge;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.CircularOrbs;
import com.interfacelayout.layout.orbs.FreeOrbOverlayBridge;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.MinimapClickProtection;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.NativeOrbStateStore;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ProfileChanged;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class GameframeResizeTest
{
    @Test public void canvasResizeRefreshesOnceAndProfileSwitchClearsResolvedPositions()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvasWidth()).thenReturn(5120);
        when(client.getCanvasHeight()).thenReturn(1440);
        CircularOrbs circular = new CircularOrbs();
        circular.client = client;
        MenuStoneController menu = mock(MenuStoneController.class);
        FreePositionController free = mock(FreePositionController.class);
        MenuOverlayBridge menuOverlays = mock(MenuOverlayBridge.class);
        FreeOrbOverlayBridge freeOverlays = mock(FreeOrbOverlayBridge.class);
        GameframeCoordinator frame = Guice.createInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(client);
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(FreePositionController.class).toProvider(() -> free);
                bind(MenuOverlayBridge.class).toProvider(() -> menuOverlays);
                bind(FreeOrbOverlayBridge.class).toProvider(() -> freeOverlays);
                bind(InterfaceEditor.class).toProvider(() -> mock(InterfaceEditor.class));
                bind(EditManager.class).toProvider(() -> mock(EditManager.class));
                bind(CircularOrbs.class).toProvider(() -> circular);
                bind(MinimapClickProtection.class).toProvider(() -> mock(MinimapClickProtection.class));
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                bind(WidgetStateStore.class).toProvider(() -> mock(WidgetStateStore.class));
                bind(NativeOrbStateStore.class).toProvider(() -> mock(NativeOrbStateStore.class));
            }
        }).getInstance(GameframeCoordinator.class);
        frame.start();
        clearInvocations(menu, free, menuOverlays, freeOverlays);
        frame.onBeforeRender(new BeforeRender());
        verify(free, never()).apply();
        when(client.getCanvasWidth()).thenReturn(1920);
        when(client.getCanvasHeight()).thenReturn(1080);
        frame.onBeforeRender(new BeforeRender());
        frame.onBeforeRender(new BeforeRender());
        verify(menu).apply(); verify(free).apply();
        verify(menuOverlays).sync(); verify(freeOverlays).sync();
        frame.onProfileChanged(mock(ProfileChanged.class));
        verify(menu).resetSession();
        verify(menuOverlays).profileChanged(); verify(freeOverlays).profileChanged();
        when(client.getCanvasWidth()).thenReturn(0);
        when(client.getCanvasHeight()).thenReturn(0);
        frame.onBeforeRender(new BeforeRender());
        verify(free, times(1)).apply();
        when(client.getCanvasWidth()).thenReturn(5120);
        when(client.getCanvasHeight()).thenReturn(1440);
        frame.onBeforeRender(new BeforeRender());
        verify(free, times(2)).apply();
    }
}
