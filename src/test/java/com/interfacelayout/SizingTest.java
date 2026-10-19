package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.sizing.*;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SizingTest
{
    @Test public void windowsHideOnlyOverlappingOrbsAtNormalAndReducedSizes()
    {
        for (int percent : new int[]{75,100})
        {
            Fixture f = new Fixture(); f.install(); when(f.config.sizeOrbHp()).thenReturn(percent);
            Widget prayer = widget(Orbs.PRAYER_ORB_CONTAINER.getComponentId(),250,100,57,34);
            when(f.widgets.getTargetWidget(Orbs.PRAYER_ORB_CONTAINER)).thenReturn(prayer);
            when(f.root.getOnVarTransmitListener()).thenReturn(new Object[]{98765});
            f.sizing.apply();
            f.sizing.occlude(java.util.List.of(new java.awt.Rectangle(99,99,70,45)));
            assertTrue(f.root.isSelfHidden()); assertFalse(prayer.isSelfHidden());
            assertTrue(f.sizing.active()); assertTrue(f.sizing.nativeScript(98765));
            f.states.restore(); f.sizing.apply(); f.sizing.occlude(java.util.List.of());
            assertFalse(f.root.isSelfHidden()); assertFalse(prayer.isSelfHidden());
            verify(f.root,never()).setOnOpListener(any(Object[].class));
        }
    }

    @Test public void windowProtectionUsesFinalPositionsAndNeverRevealsGameHiddenOrbs()
    {
        Fixture f = new Fixture(); f.install(); f.sizing.apply();
        f.root.setForcedPosition(300,300);
        f.sizing.occlude(java.util.List.of(new java.awt.Rectangle(99,99,70,45)));
        assertFalse(f.root.isHidden());
        f.sizing.occlude(java.util.List.of(new java.awt.Rectangle(300,300,1,1)));
        assertTrue(f.root.isHidden()); f.states.restore(); assertFalse(f.root.isHidden());
        f.root.setHidden(true); clearInvocations(f.root);
        f.sizing.apply(); f.sizing.occlude(java.util.List.of(new java.awt.Rectangle(300,300,100,100)));
        f.states.restore(); assertTrue(f.root.isHidden()); verify(f.root,never()).setHidden(false);
    }

    @Test public void nativeVisibilityAndShutdownSurviveWindowProtectionWithoutResizing()
    {
        Fixture f = new Fixture(); f.install();
        com.interfacelayout.layout.menu.MenuStoneController menu = mock(com.interfacelayout.layout.menu.MenuStoneController.class);
        when(menu.windows()).thenReturn(java.util.List.of(new java.awt.Rectangle(99,99,70,45)));
        com.interfacelayout.layout.GameframeCoordinator coordinator = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(InterfaceLayoutConfig.class).toInstance(f.config);
                bind(net.runelite.api.Client.class).toInstance(mock(net.runelite.api.Client.class));
                bind(net.runelite.client.callback.ClientThread.class).toInstance(mock(net.runelite.client.callback.ClientThread.class));
                bind(WidgetManager.class).toProvider(() -> f.widgets); bind(OrbController.class).toProvider(() -> f.manager);
                bind(WidgetStateStore.class).toInstance(f.states); bind(HudSizingController.class).toInstance(f.sizing);
                bind(com.interfacelayout.layout.menu.MenuStoneController.class).toProvider(() -> menu);
                bind(com.interfacelayout.layout.menu.MenuOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.menu.MenuOverlayBridge.class));
                bind(com.interfacelayout.layout.orbs.FreePositionController.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.FreePositionController.class));
                bind(com.interfacelayout.layout.orbs.FreeOrbOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.FreeOrbOverlayBridge.class));
                bind(com.interfacelayout.layout.edit.InterfaceEditor.class).toProvider(() -> mock(com.interfacelayout.layout.edit.InterfaceEditor.class));
                bind(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class));
            }
        }).getInstance(com.interfacelayout.layout.GameframeCoordinator.class);
        coordinator.start(); assertTrue(f.root.isHidden()); assertEquals(57,f.root.getWidth());
        coordinator.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(446));
        assertFalse(f.root.isHidden());
        f.root.setHidden(true); // The game now hides this orb, independently of our window protection.
        coordinator.onScriptPostFired(new net.runelite.api.events.ScriptPostFired(446));
        coordinator.stop(); assertTrue(f.root.isHidden());
    }

    @Test public void scaledLabelsDrawAtTheirMovedWidgetAndDoNotChangeSharedGraphics()
    {
        Fixture f = new Fixture(); f.install(); f.manager.active = true;
        when(f.config.scaleOrbText()).thenReturn(true); when(f.config.sizeOrbHp()).thenReturn(50);
        when(f.child.getType()).thenReturn(WidgetType.TEXT);
        when(f.child.getFontId()).thenReturn(net.runelite.api.FontID.PLAIN_11);
        when(f.child.getText()).thenReturn("<col=00ff00>60</col>");
        f.sizing.apply();
        OrbTextOverlay overlay = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(HudSizingController.class).toProvider(() -> f.sizing);
                bind(InterfaceLayoutConfig.class).toInstance(f.config);
                bind(OrbController.class).toProvider(() -> f.manager);
            }
        }).getInstance(OrbTextOverlay.class);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(300,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics(); java.awt.Font original = graphics.getFont();
        try {
            overlay.render(graphics); assertEquals(original, graphics.getFont());
            int painted = 0;
            for (int y = 0; y < 300; y++) for (int x = 0; x < 300; x++)
                if ((image.getRGB(x,y) >>> 24) != 0) {
                    painted++; assertTrue(x >= 109 && x <= 128 && y >= 99 && y <= 116);
                }
            assertTrue(painted > 0);
            when(f.root.isHidden()).thenReturn(true);
            java.awt.image.BufferedImage hidden = new java.awt.image.BufferedImage(300,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D hiddenGraphics = hidden.createGraphics();
            try { overlay.render(hiddenGraphics); } finally { hiddenGraphics.dispose(); }
            assertArrayEquals(new int[90000], hidden.getRGB(0,0,300,300,null,0,300));
        } finally { graphics.dispose(); }
    }
    @Test public void optionalNumericLabelsRestoreAcrossConfigNativeUpdatesAndLogout()
    {
        Fixture f = new Fixture(); f.install(); when(f.config.sizeOrbHp()).thenReturn(50);
        when(f.child.getType()).thenReturn(WidgetType.TEXT);
        when(f.child.getFontId()).thenReturn(net.runelite.api.FontID.PLAIN_11);
        when(f.child.getText()).thenReturn("<col=00ff00>60</col>");
        f.sizing.apply(); assertTrue(f.sizing.labels().isEmpty());
        f.states.restore(); when(f.config.scaleOrbText()).thenReturn(true); f.sizing.apply();
        assertEquals(1, f.sizing.labels().size()); verify(f.child).setHidden(true);
        f.states.restore(); verify(f.child).setHidden(false);
        when(f.child.getText()).thenReturn("59"); f.sizing.apply(); assertEquals(1, f.sizing.labels().size());
        f.states.restore(); when(f.config.scaleOrbText()).thenReturn(false); f.sizing.apply();
        assertTrue(f.sizing.labels().isEmpty());
        f.states.restore(); when(f.config.scaleOrbText()).thenReturn(true);
        when(f.manager.isLoggedIn()).thenReturn(false); f.sizing.apply(); assertTrue(f.sizing.labels().isEmpty());
        verify(f.child, never()).setText(anyString()); verify(f.child, never()).setFontId(anyInt());
    }

    @Test public void hiddenInteractiveUnknownAndFullSizeTextRemainsNative()
    {
        for (int variant = 0; variant < 6; variant++)
        {
            Fixture f = new Fixture(); f.install(); when(f.config.scaleOrbText()).thenReturn(true);
            when(f.config.sizeOrbHp()).thenReturn(50);
            when(f.child.getType()).thenReturn(WidgetType.TEXT);
            when(f.child.getFontId()).thenReturn(net.runelite.api.FontID.PLAIN_11);
            when(f.child.getText()).thenReturn("99");
            if (variant == 0) when(f.child.isHidden()).thenReturn(true);
            if (variant == 1) when(f.child.getOnOpListener()).thenReturn(new Object[]{123});
            if (variant == 2) when(f.child.getActions()).thenReturn(new String[]{"Use"});
            if (variant == 3) when(f.child.getText()).thenReturn("<img=0>99");
            if (variant == 4) when(f.child.getFontId()).thenReturn(99999);
            if (variant == 5) when(f.config.sizeOrbHp()).thenReturn(100);
            f.sizing.apply(); assertTrue(f.sizing.labels().isEmpty());
            verify(f.child, never()).setHidden(anyBoolean());
        }
    }
    @Test public void groupAndIndividualSettingsCanOnlyShrink()
    {
        Fixture f = new Fixture();
        for (int group : new int[]{-1, 0, 20, 50, 75, 99, 100, 150, Integer.MAX_VALUE})
            for (int individual : new int[]{-1, 0, 50, 75, 100, 200})
            {
                when(f.config.menuGroupSize()).thenReturn(group);
                when(f.config.sizeStoneInventory()).thenReturn(individual);
                int size = f.settings.stone(3); assertTrue(size >= 50 && size <= 100);
            }
        when(f.config.menuGroupSize()).thenReturn(80); when(f.config.sizeStoneInventory()).thenReturn(75);
        assertEquals(60, f.settings.stone(3)); assertEquals(80, f.settings.stone(5));
        assertEquals(80, f.settings.stone(6)); assertEquals(100, f.settings.stone(99));
        assertEquals(0, SizingSettings.pixels(0, 50));
    }
    @Test public void sizingRestoreDoesNotUndoNativeSelectionHoverOrResourcePackSpriteChanges()
    {
        Fixture f = new Fixture(); f.install(); when(f.config.sizeOrbHp()).thenReturn(50);
        when(f.child.getSpriteId()).thenReturn(111);
        f.sizing.apply();
        when(f.child.getSpriteId()).thenReturn(222); // Native selection/hover or skin changes after sizing.
        f.states.restore();
        assertEquals(222, f.child.getSpriteId());
        verify(f.child, never()).setSpriteId(anyInt());
        verify(f.child, never()).setType(anyInt());
        verify(f.child, never()).setContentType(anyInt());
        assertTrue(f.child.getSpriteTiling());
    }

    @Test public void defaultsAreNoOpAndNeverLookUpOtherInterfaces()
    {
        Fixture f = new Fixture(); f.sizing.apply();
        verifyNoInteractions(f.widgets, f.root, f.child);
        for (int i = 0; i < 14; i++) assertEquals(100, f.settings.stone(i));
    }
    @Test public void resizedNativeChildrenAndClickZonesRestoreWithoutCompounding()
    {
        Fixture f = new Fixture(); f.install();
        when(f.config.orbGroupSize()).thenReturn(75); when(f.config.sizeOrbHp()).thenReturn(80);
        when(f.child.getOnOpListener()).thenReturn(new Object[]{98765});
        when(f.root.getOnVarTransmitListener()).thenReturn(new Object[]{448});
        for (int n = 0; n < 4; n++)
        {
            f.sizing.apply();
            assertEquals(34, f.root.getWidth()); assertEquals(22, f.root.getHeight());
            assertEquals(16, f.child.getWidth()); assertEquals(16, f.child.getHeight());
            assertEquals(new Point(112, 102), WidgetGeometry.location(f.child));
            assertTrue(f.sizing.nativeScript(448)); assertTrue(f.sizing.nativeScript(98765));
            assertFalse(f.child.getSpriteTiling());
            f.states.restore();
            assertEquals(57, f.root.getWidth()); assertEquals(36, f.root.getHeight());
            assertEquals(26, f.child.getWidth()); assertTrue(f.child.getSpriteTiling());
            assertEquals(new Point(120, 104), WidgetGeometry.location(f.child));
        }
        verify(f.root, never()).setHidden(anyBoolean()); verify(f.child, never()).setHidden(anyBoolean());
        verify(f.child, never()).setOnOpListener(any(Object[].class));
        f.sizing.resetSession(); assertFalse(f.sizing.nativeScript(448));
    }
    @Test public void missingHiddenAndMinimizedControlsStayUntouched()
    {
        Fixture f = new Fixture(); f.install(); when(f.config.orbGroupSize()).thenReturn(50);
        when(f.root.isHidden()).thenReturn(true); f.sizing.apply();
        verify(f.root, never()).setOriginalWidth(anyInt());
        when(f.root.isHidden()).thenReturn(false); when(f.manager.isMinimapMinimized()).thenReturn(true);
        f.sizing.apply(); verify(f.root, never()).setOriginalWidth(anyInt());
        when(f.manager.isMinimapMinimized()).thenReturn(false); when(f.manager.isLoggedIn()).thenReturn(false);
        f.sizing.apply(); verify(f.root, never()).setOriginalWidth(anyInt());
    }
    @Test public void modelForeignInterfaceAndScrollingContentRejectWholeControl()
    {
        for (int variant = 0; variant < 3; variant++)
        {
            Fixture f = new Fixture(); f.install(); when(f.config.orbGroupSize()).thenReturn(50);
            if (variant == 0) when(f.child.getType()).thenReturn(WidgetType.MODEL);
            if (variant == 1) when(f.child.getId()).thenReturn(999 << 16);
            if (variant == 2) when(f.child.getScrollY()).thenReturn(1);
            f.sizing.apply(); verify(f.root, never()).setOriginalWidth(anyInt());
            verify(f.child, never()).setOriginalWidth(anyInt());
        }
    }
    @Test public void returningTo100RestoresOriginalSize()
    {
        Fixture f = new Fixture(); f.install(); when(f.config.sizeOrbHp()).thenReturn(50);
        f.sizing.apply(); assertEquals(29, f.root.getWidth());
        f.states.restore(); when(f.config.sizeOrbHp()).thenReturn(100); f.sizing.apply();
        assertEquals(57, f.root.getWidth()); assertEquals(36, f.root.getHeight());
    }
    @Test public void nativeOrbScriptsSeeOriginalGeometryAndShutdownRestoresNewNativeValues()
    {
        Fixture f = new Fixture(); f.install(); when(f.config.sizeOrbHp()).thenReturn(50);
        com.interfacelayout.layout.GameframeCoordinator coordinator = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(net.runelite.api.Client.class).toProvider(() -> mock(net.runelite.api.Client.class));
                bind(net.runelite.client.callback.ClientThread.class).toProvider(() -> mock(net.runelite.client.callback.ClientThread.class));
                bind(WidgetStateStore.class).toInstance(f.states);
                bind(com.interfacelayout.layout.orbs.MinimapClickProtection.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.MinimapClickProtection.class));
                bind(OrbController.class).toProvider(() -> f.manager);
                bind(HudSizingController.class).toProvider(() -> f.sizing);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                bind(com.interfacelayout.layout.menu.MenuStoneController.class).toProvider(() -> mock(com.interfacelayout.layout.menu.MenuStoneController.class));
                bind(com.interfacelayout.layout.menu.MenuOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.menu.MenuOverlayBridge.class));
                bind(com.interfacelayout.layout.orbs.FreePositionController.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.FreePositionController.class));
                bind(com.interfacelayout.layout.orbs.FreeOrbOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.FreeOrbOverlayBridge.class));
                bind(com.interfacelayout.layout.edit.InterfaceEditor.class).toProvider(() -> mock(com.interfacelayout.layout.edit.InterfaceEditor.class));
                bind(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class).toProvider(() -> mock(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class));
            }
        }).getInstance(com.interfacelayout.layout.GameframeCoordinator.class);
        coordinator.start(); assertEquals(29, f.root.getWidth());
        coordinator.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(446));
        assertEquals(57, f.root.getWidth()); assertEquals(26, f.child.getHeight());
        f.child.setOriginalHeight(10); when(f.child.getOriginalHeight()).thenReturn(10);
        coordinator.onScriptPostFired(new net.runelite.api.events.ScriptPostFired(446));
        assertEquals(29, f.root.getWidth()); assertEquals(5, f.child.getHeight());
        coordinator.stop(); assertEquals(57, f.root.getWidth()); assertEquals(10, f.child.getHeight());
    }

    private static Widget widget(int id, int x, int y, int width, int height)
    {
        Widget w = WidgetGeometryTest.widget(x,y,width,height); when(w.getId()).thenReturn(id);
        boolean[] hidden = {false};
        when(w.isSelfHidden()).thenAnswer(i -> hidden[0]); when(w.isHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return w; }).when(w).setHidden(anyBoolean());
        doAnswer(i -> { int px = i.getArgument(0), py = i.getArgument(1);
            when(w.getRelativeX()).thenReturn(px < 0 ? x : px);
            when(w.getRelativeY()).thenReturn(py < 0 ? y : py); return null;
        }).when(w).setForcedPosition(anyInt(), anyInt());
        boolean[] tiling = {true}; when(w.getSpriteTiling()).thenAnswer(i -> tiling[0]);
        doAnswer(i -> { tiling[0] = i.getArgument(0); return w; }).when(w).setSpriteTiling(anyBoolean());
        return w;
    }
    private static class Fixture
    {
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final WidgetManager widgets = mock(WidgetManager.class);
        final OrbController manager = mock(OrbController.class);
        final WidgetStateStore states = new WidgetStateStore();
        final Widget root = widget(Orbs.HP_ORB_CONTAINER.getComponentId(),100,100,57,36);
        final Widget child = widget(Orbs.HP_ORB_CONTAINER.getComponentId()+1,20,4,26,26);
        final HudSizingController sizing;
        final SizingSettings settings;
        Fixture() {
            when(manager.isLoggedIn()).thenReturn(true);
            com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(InterfaceLayoutConfig.class).toInstance(config); bind(WidgetManager.class).toProvider(() -> widgets);
                    bind(OrbController.class).toProvider(() -> manager); bind(WidgetStateStore.class).toInstance(states);
                    bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                }
            });
            sizing = injector.getInstance(HudSizingController.class); settings = injector.getInstance(SizingSettings.class);
        }
        void install() {
            when(widgets.getTargetWidget(Orbs.HP_ORB_CONTAINER)).thenReturn(root);
            when(root.getStaticChildren()).thenReturn(new Widget[]{child}); when(child.getParent()).thenReturn(root);
            when(child.getType()).thenReturn(WidgetType.GRAPHIC);
        }
    }
}
