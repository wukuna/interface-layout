package com.interfacelayout;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.NativeOrbStateStore;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SidePanelVisibilityTest
{
    @Test public void intermediateCloseThenTabSwitchNeverHidesVisibleParents() throws Exception
    {
        Fixture f = new Fixture();
        f.controller.hideMinimapOnTabClose(true);
        assertFalse(f.map.isSelfHidden());
        doReturn(false).when(f.controller).isSidePanelHidden();
        f.controller.hideMinimapOnTabClose(true);
        f.controller.flushSidePanelVisibility();
        verify(f.map, never()).setHidden(true);
        verify(f.orbs, never()).setHidden(true);
    }

    @Test public void intermediateOpenThenCloseKeepsHiddenParentsUntilFinalUpdate() throws Exception
    {
        Fixture f = new Fixture();
        f.apply(true);
        clearInvocations(f.map, f.orbs);
        doReturn(false).when(f.controller).isSidePanelHidden();
        f.controller.hideMinimapOnTabClose(true);
        doReturn(true).when(f.controller).isSidePanelHidden();
        f.controller.hideMinimapOnTabClose(true);
        verify(f.map, never()).setHidden(anyBoolean());
        f.controller.flushSidePanelVisibility();
        assertTrue(f.map.isSelfHidden());
        assertTrue(f.orbs.isSelfHidden());
        clearInvocations(f.map);
        f.controller.flushSidePanelVisibility();
        verify(f.map, never()).setHidden(anyBoolean());
    }

    @Test public void disablingCancelsPendingHideAndRestoresImmediately() throws Exception
    {
        Fixture f = new Fixture();
        f.apply(true);
        f.controller.hideMinimapOnTabClose(true);
        f.controller.hideMinimapOnTabClose(false);
        assertFalse(f.map.isSelfHidden());
        f.controller.flushSidePanelVisibility();
        assertFalse(f.map.isSelfHidden());
        f.controller.hideMinimapOnTabClose(true);
        when(f.config.hideMinimapWithSidePanel()).thenReturn(false);
        f.controller.flushSidePanelVisibility();
        assertFalse(f.map.isSelfHidden());
    }

    @Test public void normalAndCompactLayoutsFollowSidePanelWithoutChangingChildren() throws Exception
    {
        for (OrbLayout layout : OrbLayout.values())
        {
            for (boolean hideMinimap : new boolean[]{false, true})
            {
                Fixture f = new Fixture();
                when(f.config.layout()).thenReturn(layout);
                when(f.config.hideMinimap()).thenReturn(hideMinimap);
                Widget child = mock(Widget.class);
                when(child.isSelfHidden()).thenReturn(true);
                when(f.map.getChildren()).thenReturn(new Widget[]{child});
                for (int i = 0; i < 3; i++)
                {
                    doReturn(true).when(f.controller).isSidePanelHidden();
                    f.apply(true);
                    assertTrue(f.map.isSelfHidden());
                    assertTrue(f.orbs.isSelfHidden());
                    doReturn(false).when(f.controller).isSidePanelHidden();
                    f.apply(true);
                    assertFalse(f.map.isSelfHidden());
                    assertFalse(f.orbs.isSelfHidden());
                }
                verify(child, never()).setHidden(anyBoolean());
                verify(f.map, never()).setOriginalX(anyInt());
                verify(f.map, never()).setType(anyInt());
                assertEquals(hideMinimap, f.config.hideMinimap());
            }
        }
    }

    @Test public void reopeningPreservesNativeHiddenParents() throws Exception
    {
        Fixture f = new Fixture();
        f.map.setHidden(true);
        f.apply(true);
        f.apply(false);
        assertTrue(f.map.isSelfHidden());
        assertFalse(f.orbs.isSelfHidden());
    }

    @Test public void guardsReleasePreviouslyOwnedVisibility() throws Exception
    {
        for (int guard = 0; guard < 6; guard++)
        {
            Fixture f = new Fixture();
            f.apply(true);
            assertTrue(f.map.isSelfHidden());
            switch (guard)
            {
                case 0: f.controller.isCutsceneActive = true; break;
                case 1: doReturn(true).when(f.controller).isMinimapMinimized(); break;
                case 2: doReturn(true).when(f.controller).isMinimapPluginConfigEnabled(); break;
                case 3: doReturn(true).when(f.controller).isWikiPluginConfigEnabled(); break;
                case 4: doReturn(true).when(f.controller).isFixedMode(); break;
                case 5: doReturn(true).when(f.controller).isClassicResizable(); break;
                default: fail();
            }
            f.apply(true);
            assertFalse("guard " + guard, f.map.isSelfHidden());
            assertFalse("guard " + guard, f.orbs.isSelfHidden());
            f.apply(false);
            assertFalse(f.map.isSelfHidden());
        }
    }

    @Test public void wikiInitializationDefersHidingUntilBannerExists() throws Exception
    {
        Fixture f = new Fixture();
        doReturn(true).when(f.controller).isWikiPluginConfigEnabled();
        Widget wiki = mock(Widget.class);
        doReturn(wiki).when(f.widgets).getTargetWidget(Orbs.WIKI_ICON_CONTAINER);
        f.apply(true);
        assertFalse(f.map.isSelfHidden());
        when(wiki.getChildren()).thenReturn(new Widget[]{mock(Widget.class)});
        f.apply(true);
        assertTrue(f.map.isSelfHidden());
        assertTrue(f.controller.wikiPluginBannerExists);
    }

    @Test public void nativeScriptVisibilityAndShutdownJournalArePreserved() throws Exception
    {
        Fixture f = new Fixture();
        f.apply(true);
        f.states.restoreVisibility();
        assertFalse(f.map.isSelfHidden());
        f.map.setHidden(true); // A native script now owns this flag.
        f.apply(true);
        f.states.restore(); // The same final restoration used by reset().
        assertTrue(f.map.isSelfHidden());
        assertFalse(f.orbs.isSelfHidden());
    }

    @Test public void replacementWidgetsCaptureFreshNativeVisibility() throws Exception
    {
        Fixture f = new Fixture();
        f.apply(true);
        Widget replacement = widget(Minimap.MODERN_MAP_MINIMAP.getComponentId());
        when(f.client.getWidget(replacement.getId())).thenReturn(replacement);
        f.states.discardRetired(f.client);
        f.apply(true);
        assertTrue(replacement.isSelfHidden());
        f.apply(false);
        assertFalse(replacement.isSelfHidden());
        assertFalse(f.orbs.isSelfHidden());
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final NativeOrbStateStore states = new NativeOrbStateStore();
        final WidgetManager widgets = spy(new WidgetManager());
        final OrbController controller = spy(new OrbController());
        final Widget map = widget(Minimap.MODERN_MAP_MINIMAP.getComponentId());
        final Widget orbs = widget(Minimap.MODERN_ORBS_CONTAINER.getComponentId());
        Fixture() throws Exception
        {
            when(config.hideMinimapWithSidePanel()).thenReturn(true);
            when(client.getWidget(map.getId())).thenReturn(map);
            when(client.getWidget(orbs.getId())).thenReturn(orbs);
            inject(widgets, "client", client);
            inject(widgets, "nativeStates", states);
            inject(controller, "client", client);
            inject(controller, "config", config);
            inject(controller, "widgetManager", widgets);
            doReturn(false).when(controller).isFixedMode();
            doReturn(false).when(controller).isClassicResizable();
            doReturn(true).when(controller).isSidePanelHidden();
            doReturn(false).when(controller).isMinimapMinimized();
            doReturn(false).when(controller).isMinimapPluginConfigEnabled();
            doReturn(false).when(controller).isWikiPluginConfigEnabled();
            doNothing().when(controller).updateLogoutXPosition();
        }

        void apply(boolean hide)
        {
            controller.hideMinimapOnTabClose(hide);
            controller.flushSidePanelVisibility();
        }
    }

    private static Widget widget(int id)
    {
        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(id);
        when(widget.getIndex()).thenReturn(-1);
        boolean[] hidden = {false};
        when(widget.isSelfHidden()).thenAnswer(call -> hidden[0]);
        doAnswer(call -> { hidden[0] = call.getArgument(0); return null; })
            .when(widget).setHidden(anyBoolean());
        return widget;
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Class<?> type = target instanceof OrbController ? OrbController.class : WidgetManager.class;
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
