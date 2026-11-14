package com.interfacelayout.anchors;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AnchorLayoutStoreTest
{
    private final Map<String, String> values = new HashMap<>();
    private final ConfigManager configs = mock(ConfigManager.class);
    private AnchorLayoutStore store;

    @Before public void setup()
    {
        when(configs.getConfiguration(anyString(), anyString())).thenAnswer(call -> values.get(call.getArgument(0) + "/" + call.getArgument(1)));
        doAnswer(call -> {
            assertEquals("interfacelayout", call.getArgument(0));
            values.put(call.getArgument(0) + "/" + call.getArgument(1), call.getArgument(2).toString());
            return null;
        }).when(configs).setConfiguration(anyString(), anyString(), any());
        store = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Gson.class).toInstance(new Gson());
                bind(ConfigManager.class).toInstance(configs);
            }
        }).getInstance(AnchorLayoutStore.class);
        store.load();
    }

    @Test public void initialLoadAndReadsWriteNothing()
    {
        assertTrue(store.snapshot().anchors.isEmpty());
        store.exportLayout();
        assertTrue(values.isEmpty());
    }

    @Test public void editsRenameAndUndoPreserveStableIdentitiesAcrossReloads()
    {
        store.edit(layout -> layout.anchors.add(new AnchorLayout.Anchor()));
        String id = store.snapshot().anchors.get(0).id;
        store.edit(layout -> layout.anchors.get(0).name = "Prayer and map");
        assertEquals(id, store.snapshot().anchors.get(0).id);
        store.undo();
        assertEquals("Anchor", store.snapshot().anchors.get(0).name);
        store.load();
        assertEquals(id, store.snapshot().anchors.get(0).id);
        assertFalse(store.canUndo());
    }

    @Test public void corruptDataUsesBackupWithoutOverwritingTheOriginal()
    {
        store.edit(layout -> layout.anchors.add(new AnchorLayout.Anchor()));
        store.edit(layout -> layout.anchors.get(0).name = "Renamed");
        values.put("interfacelayout/anchorsV1", "broken");
        store.load();
        assertNotNull(store.error());
        assertEquals("Anchor", store.snapshot().anchors.get(0).name);
        assertEquals("broken", values.get("interfacelayout/anchorsV1"));
        store.restoreBackup();
        assertNull(store.error());
    }

    @Test public void aDifferentProfilesMissingDataCannotInheritPreviousAnchors()
    {
        store.edit(layout -> layout.anchors.add(new AnchorLayout.Anchor()));
        values.clear();
        store.load();
        assertTrue(store.snapshot().anchors.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class) public void futureSchemasAreNotSilentlyRewritten()
    { store.decode("{\"version\":2}"); }

    @Test public void invalidEditsLeaveTheLastGoodLayoutIntact()
    {
        store.edit(layout -> layout.anchors.add(new AnchorLayout.Anchor()));
        String old = store.exportLayout();
        try { store.edit(layout -> layout.anchors.get(0).width = -5); fail(); }
        catch (IllegalArgumentException expected) { assertEquals(old, store.exportLayout()); }
    }
}
