package com.interfacelayout;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbConfigMigration;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbPositionStore;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbRegistry;
import com.interfacelayout.layout.orbs.widget.layout.edit.BindingManager;
import com.interfacelayout.layout.orbs.widget.layout.edit.DragListener;
import com.interfacelayout.layout.orbs.widget.layout.edit.DragState;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.layout.orbs.widget.layout.slot.SlotManager;
import com.interfacelayout.layout.orbs.widget.layout.slot.SlotRegistry;
import com.interfacelayout.layout.orbs.widget.overlay.MinimapOverlay;
import com.interfacelayout.util.LegacyConfigImporter;
import com.interfacelayout.util.NativeOrbStateStore;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import javax.inject.Singleton;

/** Keep the mutually dependent controllers in RuneLite's plugin child injector. */
public class InterfaceLayoutModule implements Module
{
    @Override public void configure(Binder binder)
    {
        for (Class<?> component : new Class<?>[]{
            OrbEventHandler.class, GameframeCoordinator.class, InterfaceEditor.class,
            MenuStoneController.class, com.interfacelayout.layout.menu.MenuStoneBackgrounds.class,
            com.interfacelayout.layout.menu.MenuOverlayBridge.class, FreePositionController.class, OrbController.class,
            OrbPositionStore.class, OrbConfigMigration.class, WidgetManager.class,
            HideOrbRegistry.class, BindingManager.class, DragListener.class, DragState.class,
            EditManager.class, SlotManager.class, SlotRegistry.class, MinimapOverlay.class,
            LegacyConfigImporter.class, NativeOrbStateStore.class, WidgetStateStore.class,
            WidgetBoundsExpander.class
        }) binder.bind(component).in(Singleton.class);
    }
}
