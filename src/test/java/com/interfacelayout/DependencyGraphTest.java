package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.orbs.OrbController;
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

public class DependencyGraphTest
{
    @Test public void everyConfigGetterHasRuneLiteMetadata()
    {
        for (java.lang.reflect.Method method : InterfaceLayoutConfig.class.getDeclaredMethods())
        {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            assertNotNull(method.getName() + " must be supported by RuneLite's configuration proxy",
                method.getAnnotation(net.runelite.client.config.ConfigItem.class));
        }
    }
    @Test public void runeLiteChildInjectorLoadsPluginWithItsOwnConfigProvider()
    {
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfig(InterfaceLayoutConfig.class)).thenReturn(config);
        Injector parent = Guice.createInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ConfigManager.class).toInstance(configs);
                bind(ChatMessageManager.class).toInstance(mock(ChatMessageManager.class));
                bind(KeyManager.class).toInstance(mock(KeyManager.class));
                bind(MouseManager.class).toInstance(mock(MouseManager.class));
                bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
                bind(EventBus.class).toInstance(new EventBus());
            }
        });
        InterfaceLayoutPlugin plugin = new InterfaceLayoutPlugin();
        Injector child = parent.createChildInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(InterfaceLayoutPlugin.class).toInstance(plugin);
                install(plugin);
            }
        });
        assertSame(plugin, child.getInstance(InterfaceLayoutPlugin.class));
        assertNotNull(child.getInstance(OrbEventHandler.class));
        assertSame(config, child.getInstance(InterfaceLayoutConfig.class));
    }
    @Test public void unifiedControllersResolveWithSharedSingletonState()
    {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
                bind(ChatMessageManager.class).toInstance(mock(ChatMessageManager.class));
                bind(KeyManager.class).toInstance(mock(KeyManager.class));
                bind(MouseManager.class).toInstance(mock(MouseManager.class));
                bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
                bind(EventBus.class).toInstance(new EventBus());
                bind(InterfaceLayoutConfig.class).toInstance(mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS));
            }
        });
        assertNotNull(injector.getInstance(InterfaceLayoutPlugin.class));
        assertSame(injector.getInstance(OrbController.class), injector.getInstance(OrbController.class));
        assertSame(injector.getInstance(InterfaceEditor.class), injector.getInstance(InterfaceEditor.class));
        assertSame(injector.getInstance(GameframeCoordinator.class), injector.getInstance(GameframeCoordinator.class));
    }
}
