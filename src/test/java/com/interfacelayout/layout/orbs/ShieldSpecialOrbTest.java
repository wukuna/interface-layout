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

    @Test public void specialMirrorFlipsTheShieldFrameButKeepsItsSymbolUpright() {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        overlay.setPreferredLocation(new java.awt.Point());overlay.getBounds().setLocation(0,0);
        BufferedImage frame=new BufferedImage(57,34,BufferedImage.TYPE_INT_ARGB);
        frame.setRGB(0,0,java.awt.Color.RED.getRGB());when(sprites.get(backing)).thenReturn(frame);
        BufferedImage symbol=new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);
        symbol.setRGB(0,13,java.awt.Color.MAGENTA.getRGB());symbol.setRGB(25,13,java.awt.Color.CYAN.getRGB());
        when(sprites.getSprite(ShieldSpecialOrb.SHIELD_SPRITE)).thenReturn(symbol);
        for(boolean mirrored:new boolean[]{false,true}) {
            when(config.mirrorSpecial()).thenReturn(mirrored);
            BufferedImage image=new BufferedImage(100,50,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            overlay.render(g);g.dispose();int x=mirrored?4:27;
            assertEquals(java.awt.Color.RED.getRGB(),image.getRGB(mirrored?56:0,0));
            assertEquals(java.awt.Color.MAGENTA.getRGB(),image.getRGB(x,17));
            assertEquals(java.awt.Color.CYAN.getRGB(),image.getRGB(x+25,17));
        }
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
        verify(client, never()).getVarpValue(3784);
        when(button.getActions()).thenReturn(new String[]{"Use", "Deactivate Offhand"});
        assertEquals("Deactivate Offhand", overlay.resolve().action);
    }

    @Test public void shieldPrimaryUsesOnlyItsPrimaryAction()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(button.getActions()).thenReturn(new String[]{"Use"});
        assertNull(overlay.resolve());
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
        overlay.getBounds().setLocation(400, 200);
        when(orb.getBounds()).thenReturn(new Rectangle(300, 300, 57, 34));
        paint(); assertEquals(new java.awt.Point(400, 200), overlay.getBounds().getLocation());
        overlay.setPreferredLocation(null); paint(); assertEquals(new java.awt.Point(239, 300), overlay.getBounds().getLocation());
    }

    @Test public void circularShieldKeepsItsPositionWhenNativeInputCollapsesAndIgnoresCorners()
    {
        when(config.hideOrbTabs()).thenReturn(true); when(config.showShieldSpecialOrb()).thenReturn(true);
        CircularOrbInputTest.widget(client, InterfaceID.Orbs.SPECENERGY_INDICATOR, null, 227, 104, 26, 26);
        for(Rectangle nativeBounds:new Rectangle[]{new Rectangle(200,100,57,34),new Rectangle(240,117,0,0)}) {
            when(orb.getBounds()).thenReturn(nativeBounds);
            Graphics2D graphics=new BufferedImage(765,503,BufferedImage.TYPE_INT_ARGB).createGraphics();
            try { assertEquals(new java.awt.Dimension(34,34),overlay.render(graphics)); }
            finally { graphics.dispose(); }
            assertEquals(new java.awt.Point(185,100),overlay.getBounds().getLocation());
        }
        when(client.getMouseCanvasPosition()).thenReturn(new Point(186,101));
        overlay.onPostMenuSort(new PostMenuSort()); verify(menu,never()).createMenuEntry(anyInt());
        when(client.getMouseCanvasPosition()).thenReturn(new Point(202,117));
        overlay.onPostMenuSort(new PostMenuSort()); verify(menu).createMenuEntry(-1);
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


    @Test public void shieldClicksSurviveCircularOrbMenuFilteringAtEveryVisibleEdge() {
        when(config.hideOrbTabs()).thenReturn(true);when(config.showShieldSpecialOrb()).thenReturn(true);
        when(orb.getId()).thenReturn(InterfaceID.Orbs.ORB_SPECENERGY);
        when(orb.getRelativeX()).thenReturn(200);when(orb.getRelativeY()).thenReturn(100);
        when(orb.getWidth()).thenReturn(57);when(orb.getHeight()).thenReturn(34);
        when(backing.getId()).thenReturn(InterfaceID.Orbs.SPECENERGY_BACKING);
        when(backing.getParent()).thenReturn(orb);when(backing.getWidth()).thenReturn(57);when(backing.getHeight()).thenReturn(34);
        when(button.getParent()).thenReturn(orb);when(button.getWidth()).thenReturn(57);when(button.getHeight()).thenReturn(34);
        CircularOrbInputTest.widget(client,InterfaceID.Orbs.SPECENERGY_INDICATOR,null,227,104,26,26);
        CircularOrbs circular=new CircularOrbs();circular.client=client;circular.config=config;circular.sprites=sprites;
        circular.manager=mock(OrbController.class);circular.thread=thread;circular.start();
        circular.onClientTick(new net.runelite.api.events.ClientTick());
        java.util.List<MenuEntry> entries=new java.util.ArrayList<>();
        when(menu.getMenuEntries()).thenAnswer(i->entries.toArray(new MenuEntry[0]));
        when(menu.createMenuEntry(-1)).thenAnswer(i->{entries.add(entry);return entry;});
        doAnswer(i->{entries.clear();java.util.Collections.addAll(entries,(MenuEntry[])i.getArgument(0));return null;}).when(menu).setMenuEntries(any());
        when(entry.getParam1()).thenReturn(InterfaceID.Orbs.SPECBUTTON);when(entry.getType()).thenReturn(MenuAction.CC_OP);
        net.runelite.client.eventbus.EventBus bus=new net.runelite.client.eventbus.EventBus();bus.register(overlay);bus.register(circular);
        for(boolean moved:new boolean[]{false,true}) for(InterfaceLayoutConfig.OrbContent mode:InterfaceLayoutConfig.OrbContent.values()) {
            when(config.orbContent()).thenReturn(mode);
            if(moved){overlay.setPreferredLocation(new java.awt.Point(400,200));overlay.getBounds().setLocation(400,200);}
            Graphics2D draw=new BufferedImage(765,503,BufferedImage.TYPE_INT_ARGB).createGraphics();
            draw.translate(overlay.getBounds().x,overlay.getBounds().y);
            try{assertEquals(new java.awt.Dimension(34,34),overlay.render(draw));}finally{draw.dispose();}
            for(int[] point:new int[][]{{17,17},{0,17},{33,17},{17,0},{17,33},{0,0},{-4,17}}){
                entries.clear();int x=overlay.getBounds().x+point[0],y=overlay.getBounds().y+point[1];
                when(client.getMouseCanvasPosition()).thenReturn(new Point(x,y));bus.post(new PostMenuSort());
                assertEquals("Shield target must survive native orb filtering",CircularOrbArtwork.contains(new Rectangle(overlay.getBounds().x,overlay.getBounds().y,34,34),x,y),entries.contains(entry));
            }
        }
    }

    @Test public void coveredShieldReleasesInputEvenWhenBankOpensBetweenPaintingAndClicking()
    {
        when(config.showShieldSpecialOrb()).thenReturn(true);
        when(config.hideOrbTabs()).thenReturn(true);
        overlay.setPreferredLocation(new java.awt.Point(300, 100));
        overlay.getBounds().setLocation(300, 100);
        Graphics2D draw = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            assertEquals(new java.awt.Dimension(34, 34), overlay.render(draw));
            when(client.getMouseCanvasPosition()).thenReturn(new Point(317, 117));
            overlay.onPostMenuSort(new PostMenuSort());
            verify(menu).createMenuEntry(-1);
            Widget bank = CircularOrbInputTest.widget(client, InterfaceID.Bankmain.UNIVERSE, null, 310, 110, 20, 20);
            clearInvocations(menu, tooltips);
            overlay.onPostMenuSort(new PostMenuSort());
            verify(menu, never()).createMenuEntry(anyInt());
            MenuOptionClicked event = new MenuOptionClicked(entry);
            overlay.onMenuOptionClicked(event);
            assertTrue(event.isConsumed());
            assertNull(overlay.render(draw));
            verifyNoInteractions(tooltips);
            when(bank.isHidden()).thenReturn(true);
            assertEquals(new java.awt.Dimension(34, 34), overlay.render(draw));
            assertEquals(new java.awt.Point(300, 100), overlay.getBounds().getLocation());
        }
        finally { draw.dispose(); }
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
