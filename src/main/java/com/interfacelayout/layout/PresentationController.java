package com.interfacelayout.layout;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbConstants;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;

/** Owns only the visibility of the native special orb's optional secondary meter. */
@Singleton
public class PresentationController
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private InterfaceLayoutConfig config;

    // The native orb update creates this dynamic layer and its rectangle children.
    // Match the layer's shape as well as its parent; never hide the static orb,
    // normal energy indicator, regeneration ring, number, or action widget.
    private static final int METER_WIDTH = 33;
    private static final int METER_HEIGHT = 4;
    private final Map<Widget, Boolean> originalVisibility = new IdentityHashMap<>();
    private volatile boolean running;
    private volatile int generation;

    public void start()
    {
        running = true;
        generation++;
        scheduleRefresh();
    }

    public void stop()
    {
        running = false;
        final int stoppedGeneration = ++generation;
        clientThread.invoke(() -> {
            if (!running && generation == stoppedGeneration) restore();
        });
    }

    private void scheduleRefresh()
    {
        final int requestedGeneration = generation;
        clientThread.invokeLater(() -> {
            if (running && generation == requestedGeneration) apply();
        });
    }

    private boolean isCurrent(Widget widget)
    {
        Widget orb = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);
        return orb != null && widget.getIndex() >= 0
            && orb.getChild(widget.getIndex()) == widget;
    }

    private void restore()
    {
        originalVisibility.forEach((widget, hidden) -> {
            if (isCurrent(widget) && widget.isSelfHidden() != hidden) widget.setHidden(hidden);
        });
        originalVisibility.clear();
    }

    private void apply()
    {
        if (!running || !config.hideExtraSpecialAttackBar() || client.getGameState() != GameState.LOGGED_IN)
        {
            restore();
            return;
        }
        originalVisibility.keySet().removeIf(widget -> !isCurrent(widget));
        Widget orb = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);
        if (orb == null) return;
        Widget[] children = orb.getDynamicChildren();
        if (children == null) return;
        for (Widget child : children)
        {
            if (child == null || child.getParentId() != orb.getId()
                || child.getType() != WidgetType.LAYER || child.getIndex() < 0
                || child.getOriginalWidth() != METER_WIDTH || child.getOriginalHeight() != METER_HEIGHT)
                continue;
            originalVisibility.putIfAbsent(child, child.isSelfHidden());
            if (!child.isSelfHidden()) child.setHidden(true);
        }
    }

    @Subscribe(priority = -3)
    public void onBeforeRender(BeforeRender event)
    {
        if (running) apply();
    }

    @Subscribe(priority = 3)
    public void onScriptPreFired(ScriptPreFired event)
    {
        // Release our flag before the game deletes/rebuilds the meter or hides it.
        if (running && event.getScriptId() == OrbConstants.Script.ORBS_UPDATE_SPECENERGY) restore();
    }

    @Subscribe(priority = -3)
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (running && event.getScriptId() == OrbConstants.Script.ORBS_UPDATE_SPECENERGY) apply();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!running) return;
        if (event.getGameState() != GameState.LOGGED_IN) restore();
        else apply();
    }

    @Subscribe(priority = -3)
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (running && event.getGroupId() == (InterfaceID.Orbs.ORB_SPECENERGY >>> 16)) apply();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (running && OrbConstants.ConfigGroup.GROUP_NAME.equals(event.getGroup())
            && "hideExtraSpecialAttackBar".equals(event.getKey())) scheduleRefresh();
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        if (running) scheduleRefresh();
    }
}
