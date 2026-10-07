package com.interfacelayout.layout;

import com.interfacelayout.layout.orbs.OrbConstants.Script;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.Client;
import com.interfacelayout.util.NativeOrbStateStore;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;

/** Coordinates new native layout mutations around RuneScape's own layout scripts. */
@Singleton
public class GameframeCoordinator
{
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    @Inject private MenuStoneController menu;
    @Inject private com.interfacelayout.layout.menu.MenuOverlayBridge menuOverlays;
    @Inject private FreePositionController free;
    @Inject private InterfaceEditor editor;
    @Inject private EditManager compactEditor;
    @Inject private OrbController manager;
    @Inject private ClientThread clientThread;
    @Inject private Client client;
    @Inject private NativeOrbStateStore nativeStates;
    private boolean running;
    private int layoutDepth;
    private int visibilityDepth;
    private volatile boolean refreshPending;

    public void start() { running = true; editor.setRefresh(this::refresh); refresh(); menuOverlays.start(this::refresh); }
    public void stop() { running = false; layoutDepth = 0; visibilityDepth = 0; editor.cancel(); menuOverlays.stop(); restore(); menu.resetSession(); }
    private void restore() { menu.restoreBackgrounds(); states.restore(); expander.clear(); }
    public void prepareNativeLayout() { restore(); }
    public boolean isHiddenByGame(net.runelite.api.widgets.Widget widget)
    {
        return widget == null || nativeStates.isHiddenByGame(widget, states);
    }
    public void refresh()
    {
        if (!running || layoutDepth > 0) return;
        restore(); menu.apply(); free.apply(); menuOverlays.sync();
    }
    public void toggleShown() { if (running) { menu.toggleShown(); refresh(); } }
    private synchronized void scheduleRefresh()
    {
        if (refreshPending) return;
        refreshPending = true;
        clientThread.invokeLater(() -> { refreshPending = false; refresh(); });
    }
    public void resetPositions()
    {
        editor.cancel(); free.resetAll(); menu.resetAll(); menuOverlays.resetAll(); manager.resetSavedPositionConfigs();
        refresh();
    }
    private boolean layoutScript(int id)
    {
        return id == Script.TOPLEVEL_REDRAW || id == Script.TOPLEVEL_SUBCHANGE
            || id == Script.PROC_TOPLEVEL_SUBCHANGE || id == Script.TOPLEVEL_SIDE_CUSTOMIZE
            || id == Script.TOPLEVEL_RESIZE_CUSTOMIZE || id == Script.ORBS_UPDATE_STORE
            || id == Script.ORBS_UPDATE_ACTIVITY || id == Script.WORLD_MAP_UPDATE
            || id == Script.WIKI_ICON_INIT || id == Script.WIKI_ICON_UPDATE;
    }
    @Subscribe(priority = 2) public void onScriptPreFired(ScriptPreFired event)
    {
        if (running && visibilityScript(event.getScriptId()) && visibilityDepth++ == 0)
            nativeStates.restoreVisibility();
        if (running && layoutScript(event.getScriptId()))
        {
            if (layoutDepth++ == 0) restore();
        }
    }
    @Subscribe(priority = -2) public void onScriptPostFired(ScriptPostFired event)
    {
        if (running && visibilityScript(event.getScriptId()))
        {
            visibilityDepth = Math.max(0, visibilityDepth - 1);
            if (visibilityDepth == 0) manager.refreshVisibility();
        }
        if (layoutScript(event.getScriptId()))
        {
            layoutDepth = Math.max(0, layoutDepth - 1);
            if (event.getScriptId() == Script.TOPLEVEL_SUBCHANGE) editor.cancel();
            refresh();
        }
    }
    @Subscribe(priority = -2) public void onWidgetLoaded(WidgetLoaded event)
    {
        // Native instances may have been replaced: cancel any handle drag before remapping.
        editor.cancel();
        states.discardRetired(client);
        nativeStates.discardRetired(client);
        refresh();
    }
    @Subscribe(priority = -2) public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            editor.cancel();
            if (manager.isEditingLayout) compactEditor.toggleEditMode(false);
            restore();
            layoutDepth = 0;
            visibilityDepth = 0;
        }
        else refresh();
    }
    private boolean visibilityScript(int id)
    {
        return layoutScript(id) || id == Script.ORBS_UPDATE_HEALTH
            || id == Script.ORBS_UPDATE_SPECENERGY || id == Script.TOPLEVEL_COMPASS_SETOPS;
    }
    @Subscribe(priority = -2) public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("interfacelayout")) return;
        scheduleRefresh();
    }
    @Subscribe(priority = -2) public void onProfileChanged(ProfileChanged event)
    {
        editor.cancel(); menu.resetSession();
        scheduleRefresh();
    }
}
