package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.layout.slot.SlotManager;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LifecycleTest
{
    @Test public void sidePanelVarcRefreshRunsAfterNativeWorkAndCancelsOnDisable() throws Exception
    {
        Fixture f = new Fixture();
        f.handler.startUp(); f.queue.forEach(Runnable::run); f.queue.clear();
        clearInvocations(f.manager);
        net.runelite.api.events.VarClientIntChanged event = new net.runelite.api.events.VarClientIntChanged(
            com.interfacelayout.layout.orbs.OrbConstants.VarClient.SIDE_PANEL_ID);
        f.handler.onVarClientIntChanged(event);
        verify(f.manager, never()).hideMinimapOnTabClose(anyBoolean());
        assertEquals(1, f.queue.size());
        f.queue.forEach(Runnable::run); f.queue.clear();
        verify(f.manager).hideMinimapOnTabClose(false);
        clearInvocations(f.manager);
        f.handler.onVarClientIntChanged(event);
        f.handler.shutDown();
        f.queue.forEach(Runnable::run);
        verify(f.manager, never()).hideMinimapOnTabClose(anyBoolean());
    }

    @Test public void leavingLoggedInReleasesSidePanelVisibility()
    {
        Fixture f = new Fixture();
        net.runelite.api.events.GameStateChanged event = mock(net.runelite.api.events.GameStateChanged.class);
        for (net.runelite.api.GameState state : new net.runelite.api.GameState[]{
            net.runelite.api.GameState.HOPPING, net.runelite.api.GameState.LOGIN_SCREEN,
            net.runelite.api.GameState.LOGGING_IN, net.runelite.api.GameState.LOADING})
        {
            when(event.getGameState()).thenReturn(state);
            f.handler.onGameStateChanged(event);
        }
        verify(f.manager, times(4)).hideMinimapOnTabClose(false);
    }

    @Test public void inactiveConfigChangeCannotQueueWorkBeforeInitialization()
    {
        Fixture f = new Fixture();
        net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
        event.setGroup("interfacelayout"); event.setKey("editInterface");
        f.handler.onConfigChanged(event); assertTrue(f.queue.isEmpty());
    }
    @Test public void queuedEditingChangeCannotRunAfterPluginReenabled() throws Exception
    {
        Fixture f = new Fixture(); f.handler.startUp();
        f.queue.forEach(Runnable::run); f.queue.clear();
        net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
        event.setGroup("interfacelayout"); event.setKey("editInterface");
        f.handler.onConfigChanged(event);
        f.handler.shutDown(); f.handler.startUp();
        f.queue.forEach(Runnable::run);
        verify(f.editor, never()).toggleEditMode(anyBoolean());
    }
    @Test public void disableBeforeStartupRunsCancelsNativeInitialization() throws Exception
    {
        Fixture f = new Fixture();
        f.handler.startUp();
        f.handler.shutDown();
        assertFalse(f.manager.active);
        f.queue.forEach(Runnable::run);
        verify(f.slots, never()).init();
        verify(f.frame, never()).start();
        verify(f.frame).stop(); verify(f.manager).reset();
    }

    @Test public void rapidReenableRunsOnlyTheNewestStartup() throws Exception
    {
        Fixture f = new Fixture();
        f.handler.startUp(); f.handler.shutDown(); f.handler.startUp();
        f.queue.forEach(Runnable::run);
        assertTrue(f.manager.active);
        verify(f.slots, times(1)).init();
        verify(f.frame, times(1)).start();
        verify(f.frame, times(1)).stop();
    }

    private static final class Fixture
    {
        final List<Runnable> queue = new ArrayList<>();
        final OrbController manager = mock(OrbController.class);
        final GameframeCoordinator frame = mock(GameframeCoordinator.class);
        final SlotManager slots = mock(SlotManager.class);
        final com.interfacelayout.layout.orbs.widget.layout.edit.EditManager editor =
            mock(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class);
        final OrbEventHandler handler;
        Fixture()
        {
            ClientThread thread = mock(ClientThread.class);
            doAnswer(i -> { queue.add(i.getArgument(0)); return null; }).when(thread).invoke(any(Runnable.class));
            doAnswer(i -> { queue.add(i.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
            handler = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(mock(Client.class));
                    bind(ClientThread.class).toInstance(thread);
                    bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
                    bind(InterfaceLayoutConfig.class).toInstance(mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS));
                    bind(ChatMessageManager.class).toInstance(mock(ChatMessageManager.class));
                    bind(KeyManager.class).toInstance(mock(KeyManager.class));
                    bind(MouseManager.class).toInstance(mock(MouseManager.class));
                    bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
                    bind(EventBus.class).toInstance(new EventBus());
                    bind(OrbController.class).toProvider(() -> manager);
                    bind(GameframeCoordinator.class).toProvider(() -> frame);
                    bind(SlotManager.class).toProvider(() -> slots);
                    bind(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class).toProvider(() -> editor);
                }
            }).getInstance(OrbEventHandler.class);
        }
    }
}
