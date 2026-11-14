package com.interfacelayout.anchors;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public final class AnchorLayoutStore
{
    public static final String GROUP = "interfacelayout";
    public static final String KEY = "anchorsV1";
    private static final String BACKUP = "anchorsLastGoodV1";
    private static final int LIMIT = 262144;
    @Inject private Gson gson;
    @Inject private ConfigManager configs;
    private AnchorLayout current = new AnchorLayout();
    private final Deque<String> undo = new ArrayDeque<>();
    private String error;
    private long revision;
    public long revision() { return revision; }

    public void load()
    {
        revision++;
        undo.clear(); error = null; current = new AnchorLayout();
        String raw = configs.getConfiguration(GROUP, KEY);
        if (raw == null || raw.trim().isEmpty()) return;
        try { current = decode(raw); }
        catch (RuntimeException invalid)
        {
            error = "Saved anchors could not be loaded. Import a valid layout or restore the last saved copy.";
            String backup = configs.getConfiguration(GROUP, BACKUP);
            try { if (backup != null) current = decode(backup); } catch (RuntimeException ignored) { }
        }
    }

    public AnchorLayout snapshot() { return decode(gson.toJson(current)); }
    public String exportLayout() { return gson.toJson(current); }
    public String error() { return error; }
    public boolean canUndo() { return !undo.isEmpty(); }

    public AnchorLayout decode(String json)
    {
        if (json == null || json.length() > LIMIT) throw new IllegalArgumentException("Anchor file is too large");
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (char value : json.toCharArray())
        {
            if (quoted)
            {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            }
            else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') { if (++depth > 16) throw new IllegalArgumentException("Anchor file is too deeply nested"); }
            else if (value == '}' || value == ']') depth--;
        }
        AnchorLayout layout = gson.fromJson(json, AnchorLayout.class);
        if (layout == null) throw new IllegalArgumentException("Missing anchor layout");
        layout.validate();
        return layout;
    }

    public void edit(Consumer<AnchorLayout> change)
    {
        if (error != null) throw new IllegalStateException(error);
        AnchorLayout next = snapshot();
        change.accept(next);
        replace(next);
    }

    public void replace(AnchorLayout layout)
    {
        layout.validate();
        String next = gson.toJson(layout);
        if (next.length() > LIMIT) throw new IllegalArgumentException("Anchor layout is too large");
        String previous = exportLayout();
        if (next.equals(previous) && error == null) return;
        undo.addFirst(previous);
        while (undo.size() > 20) undo.removeLast();
        configs.setConfiguration(GROUP, BACKUP, previous);
        current = decode(next); error = null;
        revision++;
        configs.setConfiguration(GROUP, KEY, next);
    }

    public void undo()
    {
        if (undo.isEmpty()) return;
        String previous = undo.removeFirst();
        current = decode(previous); error = null;
        revision++;
        configs.setConfiguration(GROUP, KEY, previous);
    }

    public void restoreBackup()
    {
        String backup = configs.getConfiguration(GROUP, BACKUP);
        if (backup == null) throw new IllegalArgumentException("No saved anchor backup");
        replace(decode(backup));
    }
}
