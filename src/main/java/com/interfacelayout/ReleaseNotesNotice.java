package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbConstants;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;

@Singleton
final class ReleaseNotesNotice
{
    static final String RELEASE = "2026-11-13-sidebar-and-beta-timers";
    static final String SEEN_KEY = "releaseNotesShown";
    private static final String[] UPDATES = {
        "Open the minimap sidebar icon for Menu Tabs, Orbs, Anchors and General settings; changes apply immediately.",
        "New built-in anchors: create Free, Row, Column or Wrap layouts, or import your Custom UI Anchors setup.",
        "Enable Edit anchors and use your RuneLite drag hotkey to attach compatible controls, overlays and infobox groups; drag outside to detach.",
        "Move, resize, lock and reorder anchors and members, with Undo and optional shared templates.",
        "Choose Normal, Free placement or Compact orbs, with four Beta timer styles and separate prayer amount and drain displays.",
        "Add run-energy and prayer-regeneration meters, with optional numeric infoboxes under each orb's settings.",
        "Prayer reminders and saved layouts are preserved; minimap, compass and special-attack positioning are more reliable.",
        "Reduced repeated orb and overlay work."
    };

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ChatMessageManager chat;
    private volatile boolean running;
    private volatile int generation;

    void start() { running = true; generation++; schedule(); }
    void stop() { running = false; generation++; }

    private void schedule()
    {
        final int scheduledGeneration = generation;
        clientThread.invokeLater(() -> {
            if (running && generation == scheduledGeneration) showIfNeeded();
        });
    }

    private void showIfNeeded()
    {
        if (client.getGameState() != GameState.LOGGED_IN
            || RELEASE.equals(configManager.getConfiguration(OrbConstants.ConfigGroup.GROUP_NAME, SEEN_KEY)))
            return;
        send("Updates:<br>* " + String.join("<br>* ", UPDATES));
        configManager.setConfiguration(OrbConstants.ConfigGroup.GROUP_NAME, SEEN_KEY, RELEASE);
    }

    private void send(String text)
    {
        String message = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("[")
            .append(ChatColorType.HIGHLIGHT).append("Interface Layout")
            .append(ChatColorType.NORMAL).append("] ").build() + text;
        chat.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(message).build());
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (running && event.getGameState() == GameState.LOGGED_IN) schedule();
    }

    @Subscribe public void onProfileChanged(ProfileChanged event)
    {

        generation++;
        if (running) schedule();
    }
}
