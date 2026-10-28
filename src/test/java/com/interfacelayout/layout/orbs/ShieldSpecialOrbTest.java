package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ShieldSpecialOrbTest
{
    private final ShieldSpecialOrb overlay = new ShieldSpecialOrb();
    private final Client client = mock(Client.class);
    private final ClientThread thread = mock(ClientThread.class);
    private final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
    private final MenuButtonSprites sprites = mock(MenuButtonSprites.class);
    private final TooltipManager tooltips = mock(TooltipManager.class);
    private final ItemContainer equipment = mock(ItemContainer.class);
    private final ItemComposition definition = mock(ItemComposition.class);
    private final EnumComposition costs = mock(EnumComposition.class);
    private final Widget orb = mock(Widget.class), button = mock(Widget.class), icon = mock(Widget.class), backing = mock(Widget.class);
    private final Menu menu = mock(Menu.class);
    private final MenuEntry entry = mock(MenuEntry.class, RETURNS_SELF);

    @Before public void setup() throws Exception
    {
        inject("client", client); inject("clientThread", thread); inject("config", config);
        inject("sprites", sprites); inject("tooltips", tooltips);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(equipment.getItem(5)).thenReturn(new Item(34719, 1));
        when(client.getItemDefinition(34719)).thenReturn(definition);
        when(definition.getIntValue(ShieldSpecialOrb.SHIELD_KIND_PARAM)).thenReturn(1);
        when(client.getEnum(ShieldSpecialOrb.SPECIAL_COST_ENUM)).thenReturn(costs);
        when(costs.getIntValue(34719)).thenReturn(10);
        when(client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY)).thenReturn(orb);
        when(client.getWidget(InterfaceID.Orbs.SPECBUTTON)).thenReturn(button);
        when(client.getWidget(InterfaceID.Orbs.SPECENERGY_ICON)).thenReturn(icon);
        when(client.getWidget(InterfaceID.Orbs.SPECENERGY_BACKING)).thenReturn(backing);
        when(orb.getBounds()).thenReturn(new Rectangle(200, 100, 57, 34));
        when(button.getId()).thenReturn(InterfaceID.Orbs.SPECBUTTON);
        when(button.getOnOpListener()).thenReturn(new Object[]{2793});
        when(button.getActions()).thenReturn(new String[]{"Use", "Use Offhand"});
        when(icon.getSpriteId()).thenReturn(1610);
        when(client.getVarbitValue(ShieldSpecialOrb.CHARGES_VARBIT)).thenReturn(13);
        when(client.getCanvasWidth()).thenReturn(765); when(client.getCanvasHeight()).thenReturn(503);
        when(client.getTopLevelInterfaceId()).thenReturn(548);
        when(client.getMenu()).thenReturn(menu);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[0]);
        when(menu.createMenuEntry(-1)).thenReturn(entry);
        doAnswer(i -> { ((Runnable)i.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
        BufferedImage image = new BufferedImage(57, 34, BufferedImage.TYPE_INT_ARGB);
        when(sprites.get(backing)).thenReturn(image);
        when(sprites.getSprite(anyInt())).thenReturn(image);
        overlay.start();
    }

    @Test public void optionalByDefaultAndInactiveAfterStop()
    {
        assertFalse(config.showShieldSpecialOrb()); assertNull(overlay.resolve());
        when(config.showShieldSpecialOrb()).thenReturn(true);
        assertNotNull(overlay.resolve()); overlay.stop(); assertNull(overlay.resolve());
    }

    @Test public void independentShieldChargesAndActualOffhandOperation()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        ShieldSpecialOrb.State state = overlay.resolve();
        assertEquals(13, state.charges); assertEquals(2, state.operation);
        verify(client, never()).getVarpValue(3784); // Never uses Soulreaper stacks.
        when(button.getActions()).thenReturn(new String[]{"Use", "Deactivate Offhand"});
        assertEquals("Deactivate Offhand", overlay.resolve().action);
    }

    @Test public void shieldPrimaryUsesOnlyItsPrimaryAction()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(button.getActions()).thenReturn(new String[]{"Use"});
        assertNull(overlay.resolve()); // A weapon's primary action is not a shield action.
        when(icon.getSpriteId()).thenReturn(ShieldSpecialOrb.SHIELD_SPRITE);
        assertEquals(1, overlay.resolve().operation);
        when(button.getActions()).thenReturn(new String[]{"Deactivate"});
        assertEquals("Deactivate", overlay.resolve().action);
    }

    @Test public void twoHandedWeaponDoesNotCreateAnOffhand()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(equipment.getItem(3)).thenReturn(new Item(28338, 1));
        when(equipment.getItem(5)).thenReturn(null);
        assertNull(overlay.resolve());
        when(equipment.getItem(5)).thenReturn(new Item(-1, 0)); assertNull(overlay.resolve());
        when(equipment.getItem(5)).thenReturn(new Item(34719, 0)); assertNull(overlay.resolve());
    }

    @Test public void unknownShieldTypesAndUnavailableNativeActionsFailClosed()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(definition.getIntValue(ShieldSpecialOrb.SHIELD_KIND_PARAM)).thenReturn(2); assertNull(overlay.resolve());
        when(definition.getIntValue(ShieldSpecialOrb.SHIELD_KIND_PARAM)).thenReturn(1);
        when(button.getActions()).thenReturn(new String[]{"Use", "Something different"}); assertNull(overlay.resolve());
        when(button.getActions()).thenReturn(new String[]{"Use", "Use Offhand"});
        when(button.getOnOpListener()).thenReturn(null); assertNull(overlay.resolve());
    }

    @Test public void gameHiddenOrLoggedOutDoesNotExposeAnAction()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(orb.isHidden()).thenReturn(true); assertNull(overlay.resolve());
        when(orb.isHidden()).thenReturn(false); when(button.isHidden()).thenReturn(true); assertNull(overlay.resolve());
        when(button.isHidden()).thenReturn(false); when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertNull(overlay.resolve());
    }

    @Test public void pvpFollowsNativeAvailabilityAndRejectsClicksAfterNativeControlDisappears()
    {
        // The former PVP_SPEC_ORB flag now only identifies a PvP area.
        // Native orb use in PvP was enabled by Poll 78 on 2023-03-09.
        when(client.getVarbitValue(VarbitID.PVP_AREA_CLIENT)).thenReturn(1);
        paint(); when(client.getMouseCanvasPosition()).thenReturn(new Point(150, 115));
        overlay.onPostMenuSort(new PostMenuSort());
        verify(entry).setIdentifier(2);
        MenuOptionClicked available = new MenuOptionClicked(entry);
        overlay.onMenuOptionClicked(available); assertFalse(available.isConsumed());
        when(button.isHidden()).thenReturn(true);
        assertNull(overlay.resolve());
        MenuOptionClicked restricted = new MenuOptionClicked(entry);
        overlay.onMenuOptionClicked(restricted); assertTrue(restricted.isConsumed());
        when(button.isHidden()).thenReturn(false);
        when(button.getActions()).thenReturn(new String[]{"Use"});
        assertNull(overlay.resolve());
        MenuOptionClicked noOffhandAction = new MenuOptionClicked(entry);
        overlay.onMenuOptionClicked(noOffhandAction); assertTrue(noOffhandAction.isConsumed());
    }

    @Test public void zeroChargeOrbStillShowsButUnknownChargeRangeDoesNot()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(client.getVarbitValue(ShieldSpecialOrb.CHARGES_VARBIT)).thenReturn(0); assertEquals(0, overlay.resolve().charges);
        when(client.getVarbitValue(ShieldSpecialOrb.CHARGES_VARBIT)).thenReturn(20); assertEquals(20, overlay.resolve().charges);
        when(client.getVarbitValue(ShieldSpecialOrb.CHARGES_VARBIT)).thenReturn(21); assertNull(overlay.resolve());
    }

    @Test public void costComesFromNativeEquipmentDataAndUnknownCostsDoNotExposeControls()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        assertEquals(10, overlay.resolve().cost);
        when(costs.getIntValue(34719)).thenReturn(15); assertEquals(15, overlay.resolve().cost);
        when(costs.getIntValue(34719)).thenReturn(-1); assertNull(overlay.resolve());
        when(costs.getIntValue(34719)).thenReturn(21); assertNull(overlay.resolve());
        when(client.getEnum(ShieldSpecialOrb.SPECIAL_COST_ENUM)).thenReturn(null); assertNull(overlay.resolve());
    }

    @Test public void widerNativeFrameIsNotSquashed()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(backing.getSpriteId()).thenReturn(5791);
        Graphics2D graphics = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertEquals(new java.awt.Dimension(65, 34), overlay.render(graphics)); }
        finally { graphics.dispose(); }
        assertEquals(new java.awt.Point(131, 100), overlay.getBounds().getLocation());
    }

    @Test public void drawsAdjacentAndUserPositionPersistsAcrossNativeMovement()
    {
        paint(); assertEquals(new java.awt.Point(139, 100), overlay.getBounds().getLocation());
        overlay.setPreferredLocation(new java.awt.Point(400, 200));
        overlay.getBounds().setLocation(400, 200); // OverlayRenderer resolves preferred position.
        when(orb.getBounds()).thenReturn(new Rectangle(300, 300, 57, 34));
        paint(); assertEquals(new java.awt.Point(400, 200), overlay.getBounds().getLocation());
        overlay.setPreferredLocation(null); paint(); assertEquals(new java.awt.Point(239, 300), overlay.getBounds().getLocation());
    }

    @Test public void explicitClickTargetsOnlyExistingShieldOperationAndRejectsStaleMenu()
    {
        paint(); when(client.getMouseCanvasPosition()).thenReturn(new Point(150, 115));
        overlay.onPostMenuSort(new PostMenuSort());
        verify(entry).setType(MenuAction.CC_OP); verify(entry).setIdentifier(2);
        verify(entry).setParam1(InterfaceID.Orbs.SPECBUTTON); verify(entry).setParam0(-1);
        MenuOptionClicked valid = new MenuOptionClicked(entry); overlay.onMenuOptionClicked(valid); assertFalse(valid.isConsumed());
        when(equipment.getItem(5)).thenReturn(null);
        MenuOptionClicked stale = new MenuOptionClicked(entry); overlay.onMenuOptionClicked(stale); assertTrue(stale.isConsumed());
        MenuOptionClicked unrelated = new MenuOptionClicked(mock(MenuEntry.class));
        overlay.onMenuOptionClicked(unrelated); assertFalse(unrelated.isConsumed());
    }

    @Test public void missingSpritesNeverCreateAnInvisibleClickTarget()
    {
        when(sprites.getSprite(ShieldSpecialOrb.SHIELD_SPRITE)).thenReturn(null);
        when(config.showShieldSpecialOrb()).thenReturn(true);
        Graphics2D graphics = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertNull(overlay.render(graphics)); } finally { graphics.dispose(); }
        when(client.getMouseCanvasPosition()).thenReturn(new Point(150, 115));
        overlay.onPostMenuSort(new PostMenuSort()); verify(menu, never()).createMenuEntry(anyInt());
    }

    @Test public void foreignWidgetAndOverlayMenusAreNotRemoved()
    {
        paint(); when(client.getMouseCanvasPosition()).thenReturn(new Point(150, 115));
        MenuEntry foreign = mock(MenuEntry.class); when(foreign.getWidget()).thenReturn(mock(Widget.class));
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{foreign});
        overlay.onPostMenuSort(new PostMenuSort()); verify(menu, never()).removeMenuEntry(any());
        verify(menu, never()).createMenuEntry(anyInt());
        when(foreign.getWidget()).thenReturn(null); when(foreign.getType()).thenReturn(MenuAction.RUNELITE_OVERLAY);
        overlay.onPostMenuSort(new PostMenuSort()); verify(menu, never()).createMenuEntry(anyInt());
    }

    @Test public void stopRemovesOnlyTheProjectedEntry()
    {
        paint(); when(client.getMouseCanvasPosition()).thenReturn(new Point(150, 115));
        overlay.onPostMenuSort(new PostMenuSort());
        MenuEntry cancel = mock(MenuEntry.class);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{entry, cancel});
        overlay.stop(); verify(menu).removeMenuEntry(entry); verify(menu, never()).removeMenuEntry(cancel);
    }

    private void paint()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        Graphics2D graphics = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertEquals(new java.awt.Dimension(57, 34), overlay.render(graphics)); }
        finally { graphics.dispose(); }
    }
    private void inject(String name, Object value) throws Exception
    { Field field = ShieldSpecialOrb.class.getDeclaredField(name); field.setAccessible(true); field.set(overlay, value); }
}
