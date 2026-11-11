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
    static final String RELEASE = "2026-11-10-free-minimap";
    static final String SEEN_KEY = "releaseNotesShown";
    private static final String[] UPDATES = {
        "Alt-drag and anchor the native minimap in Free Position in Classic and Modern resizable modes.",
        "Use matching Silver or Dark grey rings for the free minimap and compass.",
        "The compass follows the minimap until positioned separately; hidden controls retain their saved positions.",
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
