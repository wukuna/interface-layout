package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.util.LayoutGeometry;
import com.interfacelayout.util.LayoutPositionStore;
import java.awt.Dimension;
import java.awt.Point;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LayoutPositionStoreTest
{
    @Test public void cornersAndCenterSurviveUltrawideLaptopAndBackWithoutSavingResizes()
    {
        Fixture f = new Fixture(5120, 1440);
        Point[] original = {new Point(20, 30), new Point(5000, 30), new Point(5000, 1370),
            new Point(20, 1370), new Point(2560, 700)};
        Point[] small = {new Point(20, 30), new Point(1800, 30), new Point(1800, 1010),
            new Point(20, 1010), new Point(960, 520)};
        for (int i = 0; i < original.length; i++) f.store.save("group" + i, original[i]);
        Map<String, Object> saved = new HashMap<>(f.saved);
        for (int cycle = 0; cycle < 3; cycle++)
        {
            f.resize(1920, 1080);
            for (int i = 0; i < small.length; i++) assertEquals(small[i], f.store.load("group" + i, 33, 36));
            f.resize(5120, 1440);
            for (int i = 0; i < original.length; i++) assertEquals(original[i], f.store.load("group" + i, 33, 36));
        }
        assertEquals(saved, f.saved);
    }

    @Test public void neighboringMenuGroupsRetainTheirSpacing()
    {
        Fixture f = new Fixture(5120, 1440);
        f.store.save("bar", new Point(4900, 1200));
        f.store.save("orbs", new Point(4850, 1200));
        f.resize(1920, 1080);
        Point bar = f.store.load("bar", 200, 36), orbs = f.store.load("orbs", 40, 160);
        assertEquals(50, bar.x - orbs.x);
        assertEquals(bar.y, orbs.y);
    }

    @Test public void legacyPositionsCaptureReferenceOnlyWhenTheirCanvasIsKnown()
    {
        Fixture f = new Fixture(1000, 700);
        f.saved.put("legacy", new Point(5000, 1370));
        assertEquals(new Point(5000, 1370), f.store.load("legacy", 33, 36));
        assertEquals(1, f.saved.size());
        f.resize(5120, 1440);
        assertEquals(new Point(5000, 1370), f.store.load("legacy", 33, 36));
        assertEquals(new Dimension(5120, 1440), f.saved.get("legacy_viewport"));
        f.resize(1920, 1080);
        assertEquals(new Point(1800, 1010), f.store.load("legacy", 33, 36));
    }

    @Test public void clampingAndTemporaryZeroCanvasNeverDestroyOriginalPosition()
    {
        Fixture f = new Fixture(5120, 1440);
        Point original = new Point(3000, 1200);
        f.store.save("map", original);
        f.resize(765, 504);
        Point small = f.store.load("map", 160, 160);
        assertEquals(new Point(0, 264), LayoutGeometry.clamp(small, 160, 160, 765, 504));
        f.resize(0, 0);
        f.store.save("map", new Point());
        assertEquals(original, f.store.load("map", 160, 160));
        f.resize(5120, 1440);
        assertEquals(original, f.store.load("map", 160, 160));
    }

    @Test public void profileChangesAndResetDoNotReuseAnotherProfilesReference()
    {
        Fixture f = new Fixture(5120, 1440);
        f.store.save("map", new Point(5000, 1200));
        Map<String, Object> big = new HashMap<>(f.saved);
        f.saved.clear(); f.resize(1050, 688);
        assertNull(f.store.load("map", 33, 36));
        f.store.save("map", new Point(900, 600));
        assertEquals(new Point(900, 600), f.store.load("map", 33, 36));
        f.store.reset("map");
        assertTrue(f.saved.isEmpty());
        f.saved.putAll(big);
        assertEquals(new Point(930, 448), f.store.load("map", 33, 36));
    }

    @Test public void stretchedDisplayUsesLogicalCanvasAndUnchangedCanvasKeepsPosition()
    {
        Fixture f = new Fixture(2560, 720);
        when(f.client.isStretchedEnabled()).thenReturn(true);
        when(f.client.getStretchedDimensions()).thenReturn(new Dimension(5120, 1440));
        f.store.save("map", new Point(2400, 500));
        when(f.client.getStretchedDimensions()).thenReturn(new Dimension(2560, 1440));
        assertEquals(new Point(2400, 500), f.store.load("map", 160, 160));
        f.resize(960, 540);
        assertEquals(new Point(800, 320), f.store.load("map", 160, 160));
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final Map<String, Object> saved = new HashMap<>();
        final LayoutPositionStore store;
        Fixture(int width, int height)
        {
            resize(width, height);
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(i -> saved.get(i.getArgument(1)));
            doAnswer(i -> { saved.put(i.getArgument(1), i.getArgument(2)); return null; })
                .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { saved.remove(i.getArgument(1)); return null; }).when(configs).unsetConfiguration(anyString(), anyString());
            store = Guice.createInjector(new AbstractModule() {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client);
                    bind(ConfigManager.class).toInstance(configs);
                }
            }).getInstance(LayoutPositionStore.class);
        }
        void resize(int width, int height)
        {
            when(client.getCanvasWidth()).thenReturn(width);
            when(client.getCanvasHeight()).thenReturn(height);
        }
    }
}
