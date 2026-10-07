/*
 * Copyright (c) 2025, cue <https://github.com/its-cue>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.interfacelayout;

import com.google.inject.Provides;
import com.google.inject.Binder;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
@PluginDescriptor(
	name = "Interface Layout",
	description = "Manage native menu stones, minimap and orb layouts.",
	tags = {"compact", "orbs", "layout", "hide", "minimap", "resizable", "classic", "modern", "world", "map", "wiki", "swap", "overlay", "orb", "fixed"},
	conflicts = {"Compact Orbs", "Menu Stones Hider", "Fixed Resizable Hybrid", "Orb Hider", "Minimap Hider", "Movable Orbs"}
)
public class InterfaceLayoutPlugin extends Plugin
{
    @Inject private Provider<OrbEventHandler> events;
    @Inject private EventBus eventBus;

    @Override public void configure(Binder binder)
    {
        binder.install(new InterfaceLayoutModule());
    }

    @Override protected void startUp() throws Exception
    {
        OrbEventHandler handler = events.get();
        eventBus.register(handler);
        handler.startUp();
    }
    @Override protected void shutDown() throws Exception
    {
        OrbEventHandler handler = events.get();
        eventBus.unregister(handler);
        handler.shutDown();
    }
    @Override public void resetConfiguration() { events.get().resetConfiguration(); }
    @Provides InterfaceLayoutConfig provideConfig(ConfigManager configs)
    {
        return configs.getConfig(InterfaceLayoutConfig.class);
    }
}
