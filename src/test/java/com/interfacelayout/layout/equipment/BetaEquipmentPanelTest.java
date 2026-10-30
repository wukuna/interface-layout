package com.interfacelayout.layout.equipment;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BetaEquipmentPanelTest
{
    @Test public void reducedTooltipsKeepValuesAndControlNamesWithoutLongExplanations()
    {
        Fixture f=new Fixture(); f.tab(); f.refresh();
        f.renderAt(30,210);
        ArgumentCaptor<Tooltip> tooltip=ArgumentCaptor.forClass(Tooltip.class);
        verify(f.panel.tooltips).add(tooltip.capture());
        assertTrue(tooltip.getValue().getText().contains("Base item bonuses"));
        when(f.panel.config.betaEquipmentReducedTooltips()).thenReturn(true);
        clearInvocations(f.panel.tooltips); f.renderAt(30,210);
        verify(f.panel.tooltips).add(tooltip.capture());
        assertEquals("Stab attack: 0<br>Stab defence: 0",tooltip.getValue().getText());
        clearInvocations(f.panel.tooltips); f.renderAt(12,248);
        verify(f.panel.tooltips).add(tooltip.capture()); assertEquals("Stats",tooltip.getValue().getText());
        assertFalse(new InterfaceLayoutConfig() {}.betaEquipmentReducedTooltips());
    }
    @Test public void onlyTheFourNativeUtilityButtonsMoveAndDisableRestoresThem()
    {
        Fixture f=new Fixture(); f.tab();
        Widget head=f.widget(InterfaceID.Wornitems.SLOT0,new Rectangle(77,4,36,36));
        f.refresh();
        for(int i=0;i<f.buttons.length;i++) {
            Widget button=f.buttons[i];
            assertEquals(7+i*21,button.getOriginalX()); assertEquals(243,button.getOriginalY());
            assertEquals(18,button.getWidth()); assertEquals(18,button.getHeight());
            assertSame(f.actions,button.getActions());
            assertTrue(f.icons[i].isSelfHidden());
            assertTrue(mockingDetails(button).getInvocations().stream().noneMatch(call ->
                call.getMethod().getName().startsWith("set") && !Arrays.asList("setPos","setSize").contains(call.getMethod().getName())));
        }
        verifyNoInteractions(head);
        when(f.panel.config.betaEquipmentPanel()).thenReturn(false); f.refresh();
        for(int i=0;i<f.buttons.length;i++) {
            assertEquals(7+i*45,f.buttons[i].getOriginalX());
            assertEquals(208,f.buttons[i].getOriginalY()); assertEquals(40,f.buttons[i].getWidth());
            assertFalse(f.icons[i].isSelfHidden());
        }
    }

    @Test public void hiddenUtilityAndDecorativeFrameKeepTheirNativeVisibility()
    {
        Fixture f=new Fixture(); f.tab();
        f.buttons[3].setHidden(true); f.icons[1].setHidden(true);
        Widget frame=f.widget(999,new Rectangle(0,0,40,40));
        int parentId=f.buttons[0].getId();
        when(frame.getId()).thenReturn(parentId); when(frame.getIndex()).thenReturn(0);
        when(f.buttons[0].getChild(0)).thenReturn(frame);
        when(f.buttons[0].getDynamicChildren()).thenReturn(new Widget[]{frame});
        f.refresh(); assertTrue(frame.isSelfHidden()); assertEquals(40,f.buttons[3].getWidth());
        when(f.panel.config.betaEquipmentPanel()).thenReturn(false); f.refresh();
        assertFalse(frame.isSelfHidden()); assertTrue(f.icons[1].isSelfHidden()); assertTrue(f.buttons[3].isSelfHidden());
    }

    @Test public void nativeScriptsCanChangeGeometryOrHideControlsWithoutBeingUndone()
    {
        Fixture f=new Fixture(); f.tab(); f.refresh();
        f.panel.onScriptPreFired(mock(ScriptPreFired.class));
        assertEquals(40,f.buttons[0].getWidth()); assertFalse(f.icons[0].isSelfHidden());
        f.buttons[0].setHidden(true); f.icons[0].setHidden(true);
        f.buttons[1].setPos(67,211,0,0);
        f.panel.onScriptPreFired(mock(ScriptPreFired.class));
        f.panel.onScriptPostFired(new ScriptPostFired(1));
        assertEquals(40,f.buttons[1].getWidth()); // Nested scripts must finish first.
        f.panel.onScriptPostFired(new ScriptPostFired(1));
        assertEquals(18,f.buttons[1].getWidth()); // Compact native hit areas restored before input.
        when(f.panel.config.betaEquipmentPanel()).thenReturn(false); f.refresh();
        assertTrue(f.buttons[0].isSelfHidden()); assertTrue(f.icons[0].isSelfHidden());
        assertEquals(67,f.buttons[1].getOriginalX()); assertEquals(211,f.buttons[1].getOriginalY());
    }

    @Test public void anotherPluginsGeometryAndReplacementWidgetsAreNotClobbered()
    {
        Fixture f=new Fixture(); f.tab(); f.refresh();
        Widget stale=f.buttons[0];
        f.widget(stale.getId(),new Rectangle(10,210,40,40));
        f.buttons[1].setPos(111,222,0,0);
        when(f.panel.config.betaEquipmentPanel()).thenReturn(false); f.refresh();
        assertEquals(18,stale.getWidth()); // Never touch a detached old widget.
        assertEquals(111,f.buttons[1].getOriginalX());
    }

    @Test public void bankAndExpandedEquipmentRemainNativeWithBetaEnabled()
    {
        for(boolean bank:new boolean[]{true,false}) {
            Fixture f=new Fixture(); Widget ring=f.full(bank); f.refresh();
            assertEquals(0,f.renderAt(30,250).getRGB(30,250));
            verifyNoInteractions(ring,f.nativeStat,f.panel.tooltips);
            verifyNoInteractions(f.widgets.get(bank?InterfaceID.Bankmain.WORNITEMS_CONTAINER:InterfaceID.Equipment.CONTENTS));
            verifyNoInteractions(f.widgets.get(bank?InterfaceID.Bankmain.STAT_GROUP:InterfaceID.Equipment.STAT_GROUP));
        }
    }

    @Test public void sidebarCanCoexistWithNativeEquipmentViewsAndRestoresOnClose()
    {
        for(boolean bank:new boolean[]{true,false}) {
            Fixture f=new Fixture(); Widget ring=f.full(bank); f.tab(); f.refresh();
            assertEquals(18,f.buttons[0].getWidth());
            assertNotEquals(0,f.renderAt(30,210).getRGB(30,210));
            verifyNoInteractions(ring,f.nativeStat);
            f.widgets.get(InterfaceID.Wornitems.UNIVERSE).setHidden(true); f.refresh();
            assertEquals(40,f.buttons[0].getWidth());
            assertEquals(0,f.renderAt(30,210).getRGB(30,210));
            verifyNoInteractions(ring,f.nativeStat);
        }
    }

    @Test public void shutdownAndStaleProfileCallbacksCannotMutateReenabledPanel()
    {
        Fixture f=new Fixture(); f.tab(); f.refresh();
        f.panel.onProfileChanged(mock(ProfileChanged.class));
        f.panel.stop(); f.panel.start(); f.refresh();
        f.queue.forEach(Runnable::run); f.queue.clear();
        assertEquals(18,f.buttons[0].getWidth());
        f.panel.stop(); f.queue.forEach(Runnable::run);
        assertEquals(40,f.buttons[0].getWidth());
    }

    @Test public void hiddenOrClosedRootCannotLeaveAnOrphanOverlay()
    {
        Fixture f=new Fixture(); f.tab(); f.refresh();
        f.widgets.get(InterfaceID.Wornitems.UNIVERSE).setHidden(true);
        BufferedImage result=f.renderAt(30,210);
        assertEquals(0,result.getRGB(30,210)); verifyNoInteractions(f.panel.tooltips);
    }

    static final class Fixture {
        final BetaEquipmentPanel panel=new BetaEquipmentPanel();
        final Map<Integer,Widget> widgets=new HashMap<>(); final List<Runnable> queue=new ArrayList<>();
        final String[] actions={"Native action"}; Widget[] buttons,icons; Widget nativeStat;
        Fixture() {
            panel.client=mock(Client.class); panel.clientThread=mock(ClientThread.class);
            panel.config=mock(InterfaceLayoutConfig.class); panel.items=mock(ItemManager.class);
            panel.sprites=mock(MenuButtonSprites.class); panel.tooltips=mock(TooltipManager.class);
            when(panel.config.betaEquipmentPanel()).thenReturn(true);
            when(panel.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(panel.client.getWidget(anyInt())).thenAnswer(call->widgets.get(call.getArgument(0)));
            when(panel.client.getSpriteOverrides()).thenReturn(Collections.emptyMap());
            when(panel.client.getWidgetSpriteOverrides()).thenReturn(Collections.emptyMap());
            ItemContainer container=mock(ItemContainer.class); when(container.getItems()).thenReturn(new Item[0]);
            when(panel.client.getItemContainer(InventoryID.WORN)).thenReturn(container);
            doAnswer(call->{queue.add(call.getArgument(0));return null;}).when(panel.clientThread).invoke(any(Runnable.class));
            doAnswer(call->{queue.add(call.getArgument(0));return null;}).when(panel.clientThread).invokeLater(any(Runnable.class));
            panel.start();
        }
        void refresh(){panel.onBeforeRender(new BeforeRender());}
        void tab() {
            widget(InterfaceID.Wornitems.UNIVERSE,new Rectangle(0,0,190,261));
            int[] ids={InterfaceID.Wornitems.EQUIPMENT,InterfaceID.Wornitems.PRICECHECKER,InterfaceID.Wornitems.DEATHKEEP,InterfaceID.Wornitems.CALL_FOLLOWER};
            int[] images={InterfaceID.Wornitems.EQUIPMENT_ICON,InterfaceID.Wornitems.ROOT_GRAPHIC2,InterfaceID.Wornitems.ROOT_GRAPHIC3,InterfaceID.Wornitems.ROOT_GRAPHIC4};
            buttons=new Widget[4]; icons=new Widget[4];
            for(int i=0;i<4;i++) {
                buttons[i]=widget(ids[i],new Rectangle(7+i*45,208,40,40)); when(buttons[i].getActions()).thenReturn(actions);
                icons[i]=widget(images[i],new Rectangle(10+i*45,210,32,32));
            }
        }
        Widget full(boolean bank) {
            widget(bank?InterfaceID.Bankmain.WORNITEMS_CONTAINER:InterfaceID.Equipment.CONTENTS,new Rectangle(0,0,bank?478:500,bank?290:324));
            widget(bank?InterfaceID.Bankmain.STAT_GROUP:InterfaceID.Equipment.STAT_GROUP,new Rectangle(0,0,500,324));
            int[] stats=bank?new int[]{InterfaceID.Bankmain.STABATT,InterfaceID.Bankmain.SLASHATT,InterfaceID.Bankmain.CRUSHATT,InterfaceID.Bankmain.MAGICATT,InterfaceID.Bankmain.RANGEATT,
                InterfaceID.Bankmain.STABDEF,InterfaceID.Bankmain.SLASHDEF,InterfaceID.Bankmain.CRUSHDEF,InterfaceID.Bankmain.MAGICDEF,InterfaceID.Bankmain.RANGEDEF,
                InterfaceID.Bankmain.MELEESTRENGTH,InterfaceID.Bankmain.RANGESTRENGTH,InterfaceID.Bankmain.MAGICDAMAGE,InterfaceID.Bankmain.PRAYER}:
                new int[]{InterfaceID.Equipment.STABATT,InterfaceID.Equipment.SLASHATT,InterfaceID.Equipment.CRUSHATT,InterfaceID.Equipment.MAGICATT,InterfaceID.Equipment.RANGEATT,
                InterfaceID.Equipment.STABDEF,InterfaceID.Equipment.SLASHDEF,InterfaceID.Equipment.CRUSHDEF,InterfaceID.Equipment.MAGICDEF,InterfaceID.Equipment.RANGEDEF,
                InterfaceID.Equipment.MELEESTRENGTH,InterfaceID.Equipment.RANGESTRENGTH,InterfaceID.Equipment.MAGICDAMAGE,InterfaceID.Equipment.PRAYER};
            for(int id:stats){Widget w=widget(id,new Rectangle(300,40,100,15));when(w.getText()).thenReturn("Bonus: +123");}
            nativeStat=widgets.get(stats[0]);
            return widget(bank?InterfaceID.Bankmain.WORNSLOT12:InterfaceID.Equipment.SLOT12,new Rectangle(127,bank?201:220,36,36));
        }
        Widget widget(int id,Rectangle bounds) {
            Widget w=mock(Widget.class); final int[] geometry={bounds.x,bounds.y,bounds.width,bounds.height,0,0,0,0};final boolean[] hidden={false};
            when(w.getId()).thenReturn(id); when(w.getIndex()).thenReturn(-1); when(w.getBounds()).thenReturn(bounds);
            when(w.getOriginalX()).thenAnswer(call->geometry[0]);when(w.getOriginalY()).thenAnswer(call->geometry[1]);
            when(w.getOriginalWidth()).thenAnswer(call->geometry[2]);when(w.getOriginalHeight()).thenAnswer(call->geometry[3]);
            when(w.getWidth()).thenAnswer(call->geometry[2]);when(w.getHeight()).thenAnswer(call->geometry[3]);
            when(w.getXPositionMode()).thenAnswer(call->geometry[4]);when(w.getYPositionMode()).thenAnswer(call->geometry[5]);
            when(w.getWidthMode()).thenAnswer(call->geometry[6]);when(w.getHeightMode()).thenAnswer(call->geometry[7]);
            when(w.isHidden()).thenAnswer(call->hidden[0]);when(w.isSelfHidden()).thenAnswer(call->hidden[0]);
            doAnswer(call->{hidden[0]=call.getArgument(0);return w;}).when(w).setHidden(anyBoolean());
            doAnswer(call->{geometry[0]=call.getArgument(0);geometry[1]=call.getArgument(1);geometry[4]=call.getArgument(2);geometry[5]=call.getArgument(3);return w;}).when(w).setPos(anyInt(),anyInt(),anyInt(),anyInt());
            doAnswer(call->{geometry[2]=call.getArgument(0);geometry[3]=call.getArgument(1);geometry[6]=call.getArgument(2);geometry[7]=call.getArgument(3);return w;}).when(w).setSize(anyInt(),anyInt(),anyInt(),anyInt());
            widgets.put(id,w);return w;
        }
        BufferedImage renderAt(int x,int y) {
            when(panel.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(x,y));
            BufferedImage result=new BufferedImage(700,500,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=result.createGraphics();try{panel.render(graphics);}finally{graphics.dispose();}return result;
        }
    }
}
