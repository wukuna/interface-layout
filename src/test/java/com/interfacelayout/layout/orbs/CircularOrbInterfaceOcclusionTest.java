package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbInterfaceOcclusionTest
{
    @Test public void coveringInterfacesHideOnlyIntersectingOrbsAndClosingRestoresThem()
    {
        Orbs[] orbs = {Orbs.HP_ORB_CONTAINER, Orbs.PRAYER_ORB_CONTAINER, Orbs.RUN_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER};
        int[] texts = {InterfaceID.Orbs.HEALTH_TEXT, InterfaceID.Orbs.PRAYER_TEXT, InterfaceID.Orbs.RUNENERGY_TEXT, InterfaceID.Orbs.SPECENERGY_TEXT};
        for (int window : new int[]{InterfaceID.Bankmain.UNIVERSE, InterfaceID.BankpinKeypad.UNIVERSE,
            InterfaceID.Collection.FRAME, InterfaceID.Settings.UNIVERSE})
        for (int i = 0; i < orbs.length; i++)
        for (InterfaceLayoutConfig.OrbContent mode : InterfaceLayoutConfig.OrbContent.values())
        {
            CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture(orbs[i], texts[i]);
            visibility(f.parent);
            when(f.overlay.config.orbContent()).thenReturn(mode);
            Widget modal = modal(f, window, new Rectangle(130, 75, 20, 20));
            Rectangle original = f.parent.getBounds();
            f.refresh();
            assertTrue(f.parent.isHidden());
            assertTrue(CircularOrbCoreOverlayTest.visible(f.render()).isEmpty());
            assertEquals(original, f.parent.getBounds());
            modal.setHidden(true); f.refresh();
            assertFalse(f.parent.isHidden());
            assertFalse(CircularOrbCoreOverlayTest.visible(f.render()).isEmpty());
            assertTrue(f.button.getBounds().contains(140, 87));
            modal.setOriginalX(10); modal.setHidden(false); f.refresh();
            assertFalse(f.parent.isHidden());
            assertFalse(CircularOrbCoreOverlayTest.visible(f.render()).isEmpty());
        }
    }

    @Test public void coveredOrbMenuEntriesCannotReplaceBankActions()
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture();
        visibility(f.parent);
        modal(f, InterfaceID.Bankmain.UNIVERSE, new Rectangle(130, 75, 20, 20));
        Menu menu = mock(Menu.class);
        MenuEntry bank = mock(MenuEntry.class), orb = mock(MenuEntry.class), hover = mock(MenuEntry.class);
        when(bank.getType()).thenReturn(MenuAction.CC_OP);
        when(bank.getParam1()).thenReturn(InterfaceID.Bankmain.UNIVERSE);
        when(orb.getParam1()).thenReturn(InterfaceID.Orbs.SPECBUTTON);
        when(hover.getWidget()).thenReturn(f.hover);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{bank, orb, hover});
        when(f.overlay.client.getMenu()).thenReturn(menu);
        f.refresh(); f.overlay.onPostMenuSort(new PostMenuSort());
        verify(menu).setMenuEntries(new MenuEntry[]{bank});
    }

    @Test public void scriptRestorationPreservesGameHidingAndDoesNotTouchReplacementWidgets()
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture();
        visibility(f.parent);
        Widget bank = modal(f, InterfaceID.Bankmain.UNIVERSE, new Rectangle(130, 75, 20, 20));
        f.refresh(); assertTrue(f.parent.isSelfHidden());
        f.overlay.onScriptPreFired(new ScriptPreFired(1000));
        assertFalse(f.parent.isSelfHidden());
        f.parent.setHidden(true); bank.setHidden(true); f.refresh();
        assertTrue(f.parent.isSelfHidden());
        f.parent.setHidden(false); bank.setHidden(false); f.refresh();
        Widget replacement = mock(Widget.class);
        when(f.overlay.client.getWidget(f.parent.getId())).thenReturn(replacement);
        f.overlay.releaseForLayout();
        verify(replacement, never()).setHidden(anyBoolean());
    }

    @Test public void nativePrayerDoseReminderAlsoStaysBehindTheWindow() throws Exception
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture(Orbs.PRAYER_ORB_CONTAINER, InterfaceID.Orbs.PRAYER_TEXT);
        visibility(f.parent);
        when(f.button.isHidden()).thenAnswer(call -> f.parent.isHidden());
        CircularOrbCoreOverlayTest.Core core = new CircularOrbCoreOverlayTest.Core(f.overlay.client, "prayer.PrayerDoseOverlay");
        core.stub("prayer.PrayerConfig", "showPrayerDoseIndicator", true);
        core.stub("prayer.PrayerConfig", "showPrayerStatistics", true);
        core.stub("prayer.PrayerConfig", "prayerDoseOrbStartColor", java.awt.Color.CYAN);
        when(f.overlay.client.getRealSkillLevel(net.runelite.api.Skill.PRAYER)).thenReturn(99);
        when(f.overlay.client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER)).thenReturn(1);
        java.lang.reflect.Field amount = core.overlay.getClass().getDeclaredField("restoreAmount");
        amount.setAccessible(true); amount.setInt(core.overlay, 30);
        f.refresh(); assertFalse(CircularOrbCoreOverlayTest.visible(core.render()).isEmpty());
        Widget bank = modal(f, InterfaceID.Bankmain.UNIVERSE, new Rectangle(130, 75, 20, 20));
        f.refresh(); clearInvocations(core.tips);
        assertTrue(CircularOrbCoreOverlayTest.visible(core.render()).isEmpty());
        verifyNoInteractions(core.tips);
        bank.setHidden(true); f.refresh();
        assertFalse(CircularOrbCoreOverlayTest.visible(core.render()).isEmpty());
    }

    @Test public void disablingAndEditingRestoreTheNativeOrb()
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture();
        visibility(f.parent);
        modal(f, InterfaceID.Bankmain.UNIVERSE, new Rectangle(130, 75, 20, 20));
        f.refresh(); assertTrue(f.parent.isSelfHidden());
        when(f.overlay.config.editInterface()).thenReturn(true); f.refresh();
        assertFalse(f.parent.isSelfHidden());
        when(f.overlay.config.editInterface()).thenReturn(false); f.refresh();
        assertTrue(f.parent.isSelfHidden());
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
            .when(f.overlay.thread).invoke(any(Runnable.class));
        f.overlay.stop(); assertFalse(f.parent.isSelfHidden());
        assertEquals(0, f.backing.getOpacity());
    }

    private static Widget modal(CircularOrbOverlayTest.Fixture f, int id, Rectangle bounds)
    {
        Widget modal = CircularOrbInputTest.widget(f.overlay.client, id, null, bounds.x, bounds.y, bounds.width, bounds.height);
        visibility(modal);
        return modal;
    }

    static void visibility(Widget widget)
    {
        AtomicBoolean hidden = new AtomicBoolean();
        when(widget.isSelfHidden()).thenAnswer(call -> hidden.get());
        when(widget.isHidden()).thenAnswer(call -> hidden.get());
        doAnswer(call -> { hidden.set(call.getArgument(0)); return widget; }).when(widget).setHidden(anyBoolean());
    }
}
