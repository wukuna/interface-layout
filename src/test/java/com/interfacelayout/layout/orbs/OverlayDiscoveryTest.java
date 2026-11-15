package com.interfacelayout.layout.orbs;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayDiscoveryTest {
    static final class Provider extends Overlay {
        @Override public Dimension render(Graphics2D graphics) { return null; }
    }
    @Test public void repeatedRefreshesAreBoundedAndProviderChangesAreDiscovered() {
        OverlayManager manager=mock(OverlayManager.class);
        List<Overlay> active=new ArrayList<>();
        when(manager.anyMatch(any())).thenAnswer(call -> {
            Predicate<Overlay> predicate=call.getArgument(0);
            return active.stream().anyMatch(predicate);
        });
        OverlayDiscovery discovery=new OverlayDiscovery(Provider.class.getName());
        for(int i=0;i<100;i++)assertTrue(discovery.find(manager,1).isEmpty());
        verify(manager,times(1)).anyMatch(any());
        Provider first=new Provider();active.add(first);
        discovery.clear();assertEquals(List.of(first),discovery.find(manager,1));
        active.clear();assertTrue(discovery.find(manager,2).isEmpty());
        Provider replacement=new Provider();active.add(replacement);
        assertEquals(List.of(replacement),discovery.find(manager,3));
        discovery.clear();active.clear();assertTrue(discovery.find(manager,3).isEmpty());
    }
}
