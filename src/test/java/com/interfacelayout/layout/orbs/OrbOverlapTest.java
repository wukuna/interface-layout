package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.layout.orbs.widget.elements.Gravestone;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrbOverlapTest
{
    @Test public void compassOverlapBlocksMapNavigationOnlyWhileHovered() {
        for(boolean circles:new boolean[]{false,true}) {
            CircularOrbs overlay=overlay(circles);when(overlay.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
            Widget map=CircularOrbInputTest.widget(overlay.client,OrbConstants.Widgets.Modern.MINIMAP_MASK,null,90,90,150,150);
            Widget compass=CircularOrbInputTest.widget(overlay.client,OrbConstants.Widgets.Modern.COMPASS,null,110,110,33,33);
            when(compass.getContentType()).thenReturn(1339);
            AtomicInteger content=new AtomicInteger(1338);
            when(map.getContentType()).thenAnswer(i->content.get());
            doAnswer(i->{content.set(i.getArgument(0));return map;}).when(map).setContentType(anyInt());
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(126,126));
            overlay.start();overlay.onClientTick(new ClientTick());assertEquals(0,map.getContentType());
            overlay.onBeforeRender(new BeforeRender());assertEquals(1338,map.getContentType());
            paint(overlay);assertEquals(0,map.getContentType());
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(200,180));
            overlay.onClientTick(new ClientTick());assertEquals(1338,map.getContentType());
            overlay.stop();verify(compass,never()).setAction(anyInt(),anyString());
        }
    }

    @Test public void frontOrbOwnsTheOverlapAndMapNavigationResumesOutsideIt()
    {
        for(boolean circles:new boolean[]{false,true})
        {
            CircularOrbs overlay=overlay(circles);
            Widget hp=orb(overlay,OrbOrder.HITPOINTS,100,100,Color.RED);
            Widget prayer=orb(overlay,OrbOrder.PRAYER,100,100,Color.BLUE);
            Widget map=CircularOrbInputTest.widget(overlay.client,OrbConstants.Widgets.Modern.MINIMAP_MASK,null,90,90,150,150);
            AtomicInteger content=new AtomicInteger(1338);
            when(map.getContentType()).thenAnswer(i->content.get());doAnswer(i->{content.set(i.getArgument(0));return map;}).when(map).setContentType(anyInt());
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,117));
            overlay.start();overlay.onClientTick(new ClientTick());
            assertEquals(0,hp.getWidth());assertTrue(prayer.getWidth()>0);assertEquals(0,map.getContentType());
            overlay.onBeforeRender(new BeforeRender());assertEquals(1338,map.getContentType());
            overlay.releaseForLayout();overlay.refreshAfterLayout();assertEquals(1338,map.getContentType());
            BufferedImage image=paint(overlay);
            assertEquals(Color.BLUE.getRGB(),image.getRGB(140,117));assertEquals(0,map.getContentType());
            Menu menu=mock(Menu.class);when(overlay.client.getMenu()).thenReturn(menu);
            MenuEntry hpAction=entry(OrbOrder.HITPOINTS,MenuAction.CC_OP),prayerAction=entry(OrbOrder.PRAYER,MenuAction.CC_OP),walk=entry(OrbOrder.RUN,MenuAction.WALK);
            MenuEntry inventory=mock(MenuEntry.class);when(inventory.getType()).thenReturn(MenuAction.CC_OP);when(inventory.getParam1()).thenReturn(12345);
            when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{walk,hpAction,inventory,prayerAction});
            overlay.onPostMenuSort(new PostMenuSort());verify(menu).setMenuEntries(new MenuEntry[]{prayerAction});
            when(overlay.config.orbStackOrder()).thenReturn("PRAYER,HITPOINTS,RUN,SPECIAL");
            overlay.onClientTick(new ClientTick());assertEquals(0,prayer.getWidth());assertTrue(hp.getWidth()>0);
            assertEquals(Color.RED.getRGB(),paint(overlay).getRGB(140,117));
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(210,180));
            overlay.onClientTick(new ClientTick());assertEquals(1338,map.getContentType());
            overlay.stop();assertEquals(57,hp.getWidth());assertEquals(57,prayer.getWidth());assertEquals(1338,map.getContentType());
        }
    }

    @Test public void mirrorMovesContentsAndTagMetersWithoutReversingSymbolsOrAccumulatingDrift()
    {
        CircularOrbs overlay=overlay(false);orb(overlay,OrbOrder.PRAYER,100,100,Color.BLUE);
        Widget icon=overlay.client.getWidget(OrbOrder.PRAYER.target.getIconId());
        Widget fill=overlay.client.getWidget(OrbOrder.PRAYER.target.getIndicatorId());
        Widget number=overlay.client.getWidget(OrbOrder.PRAYER.number);
        BufferedImage symbol=new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);
        symbol.setRGB(0,13,Color.YELLOW.getRGB());symbol.setRGB(25,13,Color.GREEN.getRGB());
        when(overlay.sprites.getPadded(icon)).thenReturn(symbol);
        when(overlay.config.mirrorPrayer()).thenReturn(true);
        overlay.start();
        for(int frame=0;frame<8;frame++)
        {
            overlay.onClientTick(new ClientTick());
            assertEquals(4,fill.getOriginalX());assertEquals(32,number.getOriginalX());
            BufferedImage image=paint(overlay);
            assertEquals(Color.YELLOW.getRGB(),image.getRGB(104,117));assertEquals(Color.GREEN.getRGB(),image.getRGB(129,117));
            Rectangle meter=NativeOrbTimers.tagBounds(new Rectangle(105,105,24,24),number.getBounds());
            assertTrue(meter.width>0);assertTrue(number.getBounds().contains(meter));
            overlay.onScriptPreFired(new ScriptPreFired(446));assertEquals(27,fill.getOriginalX());assertEquals(0,number.getOriginalX());
            overlay.onScriptPostFired(new ScriptPostFired(446));
        }
        when(overlay.config.hideOrbTabs()).thenReturn(true);overlay.onClientTick(new ClientTick());assertEquals(27,fill.getOriginalX());
        overlay.stop();assertEquals(27,icon.getOriginalX());assertEquals(0,number.getOriginalX());
    }

    @Test public void graveVisibilityNeverUnhidesAnInactiveGraveAndRestoresOwnedHiding()
    {
        CircularOrbs overlay=overlay(false);
        Widget grave=CircularOrbInputTest.widget(overlay.client,Gravestone.MODERN.getComponentId(),null,200,50,40,40);
        AtomicBoolean hidden=new AtomicBoolean(true);
        when(grave.isHidden()).thenAnswer(i->hidden.get());when(grave.isSelfHidden()).thenAnswer(i->hidden.get());
        doAnswer(i->{hidden.set(i.getArgument(0));return grave;}).when(grave).setHidden(anyBoolean());
        overlay.start();overlay.onClientTick(new ClientTick());assertTrue(hidden.get());verify(grave,never()).setHidden(false);
        hidden.set(false);when(overlay.config.hideGravestone()).thenReturn(true);overlay.onClientTick(new ClientTick());assertTrue(hidden.get());
        overlay.onScriptPreFired(new ScriptPreFired(1));assertFalse(hidden.get());
        hidden.set(true);overlay.onScriptPostFired(new ScriptPostFired(1));
        when(overlay.config.hideGravestone()).thenReturn(false);overlay.onClientTick(new ClientTick());assertTrue(hidden.get());
        hidden.set(false);when(overlay.config.hideGravestone()).thenReturn(true);overlay.onClientTick(new ClientTick());overlay.stop();assertFalse(hidden.get());
    }

    @Test public void normalModeDoesNotReplaceTheCompassFrame()
    {
        CircularOrbs overlay=overlay(true);
        Widget compass=CircularOrbInputTest.widget(overlay.client,OrbConstants.Widgets.Modern.COMPASS,null,100,50,33,33);
        when(compass.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
        Widget frame=CircularOrbInputTest.widget(overlay.client,777,null,96,46,41,41);overlay.manager.compassFrame=frame;
        overlay.start();overlay.onClientTick(new ClientTick());assertEquals(0,frame.getOpacity());
        BufferedImage image=new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB);Graphics2D draw=image.createGraphics();
        overlay.frames().render(draw);draw.dispose();assertEquals(0,image.getRGB(100,65));overlay.stop();
    }

    @Test public void mapGuardDoesNotRestoreOverANewerGameContentTypeOrIntoAReplacement()
    {
        Client client=mock(Client.class);Widget map=CircularOrbInputTest.widget(client,42,null,0,0,150,150);
        AtomicInteger content=new AtomicInteger(1338);when(map.getContentType()).thenAnswer(i->content.get());
        doAnswer(i->{content.set(i.getArgument(0));return map;}).when(map).setContentType(anyInt());
        OrbMinimapInput input=new OrbMinimapInput();input.block(map);assertEquals(0,content.get());
        content.set(1339);input.restore(client);assertEquals(1339,content.get());
        content.set(1338);input.block(map);Widget replacement=mock(Widget.class);when(client.getWidget(42)).thenReturn(replacement);
        input.restore(client);verifyNoInteractions(replacement);
    }

    @Test public void aBankCoveringTheOrbsReleasesMapInputAndRemovesTheirArtwork()
    {
        for(boolean circles:new boolean[]{false,true})
        {
            CircularOrbs overlay=overlay(circles);Widget button=orb(overlay,OrbOrder.PRAYER,100,100,Color.BLUE);
            Widget parent=button.getParent();AtomicBoolean hidden=new AtomicBoolean();
            when(parent.isHidden()).thenAnswer(i->hidden.get());when(parent.isSelfHidden()).thenAnswer(i->hidden.get());
            doAnswer(i->{hidden.set(i.getArgument(0));return parent;}).when(parent).setHidden(anyBoolean());
            Widget bank=CircularOrbInputTest.widget(overlay.client,net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE,null,80,80,200,200);
            AtomicBoolean bankHidden=new AtomicBoolean();when(bank.isHidden()).thenAnswer(i->bankHidden.get());
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,117));
            overlay.start();overlay.onBeforeRender(new BeforeRender());assertTrue(hidden.get());
            assertEquals(Color.MAGENTA.getRGB(),paint(overlay).getRGB(140,117));
            bankHidden.set(true);overlay.onBeforeRender(new BeforeRender());assertFalse(hidden.get());
            assertEquals(Color.BLUE.getRGB(),paint(overlay).getRGB(140,117));overlay.stop();
        }
    }

    @Test public void malformedOrdersKeepEveryOrbExactlyOnce()
    {
        assertEquals(List.of(OrbOrder.PRAYER,OrbOrder.HITPOINTS,OrbOrder.RUN,OrbOrder.SPECIAL),OrbOrder.backToFront("PRAYER,unknown,PRAYER"));
        assertEquals(4,OrbOrder.backToFront(null).size());
        assertEquals("PRAYER,HITPOINTS,RUN,SPECIAL",OrbOrder.move(null,OrbOrder.HITPOINTS,1));
    }

    @Test public void aResourcePackWithAHollowBackingStillHasAClickableOrbFace()
    {
        CircularOrbs overlay=overlay(false);Widget button=orb(overlay,OrbOrder.PRAYER,100,100,Color.BLUE);
        Widget backing=overlay.client.getWidget(OrbOrder.PRAYER.target.getBackingId());
        when(overlay.sprites.getPadded(backing)).thenReturn(new BufferedImage(57,34,BufferedImage.TYPE_INT_ARGB));
        when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,117));
        overlay.start();overlay.onClientTick(new ClientTick());assertTrue(button.getWidth()>0);
        when(overlay.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(101,101));
        overlay.onClientTick(new ClientTick());assertEquals(0,button.getWidth());overlay.stop();
    }

    private static CircularOrbs overlay(boolean circles)
    {
        CircularOrbs overlay=new CircularOrbs();overlay.client=mock(Client.class);overlay.manager=mock(OrbController.class);
        overlay.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);overlay.sprites=mock(MenuButtonSprites.class);overlay.thread=mock(ClientThread.class);
        when(overlay.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(overlay.config.hideOrbTabs()).thenReturn(circles);
        when(overlay.config.hideMinimap()).thenReturn(false);when(overlay.config.disableOrbTimers()).thenReturn(true);
        when(overlay.config.orbContent()).thenReturn(InterfaceLayoutConfig.OrbContent.ICON_ONLY);
        doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(overlay.thread).invoke(any(Runnable.class));return overlay;
    }

    private static Widget orb(CircularOrbs overlay,OrbOrder orb,int x,int y,Color color)
    {
        Widget root=CircularOrbInputTest.widget(overlay.client,orb.target.getComponentId(),null,x,y,57,34);
        Widget backing=CircularOrbInputTest.widget(overlay.client,orb.target.getBackingId(),root,0,0,57,34);
        Widget button=CircularOrbInputTest.widget(overlay.client,orb.target.getButtonId(),root,0,0,57,34);
        Widget fill=CircularOrbInputTest.widget(overlay.client,orb.target.getIndicatorId(),root,27,4,26,26);
        Widget icon=CircularOrbInputTest.widget(overlay.client,orb.target.getIconId(),root,27,4,26,26);
        Widget number=CircularOrbInputTest.widget(overlay.client,orb.number,root,0,0,25,34);
        when(root.getType()).thenReturn(WidgetType.LAYER);when(number.getType()).thenReturn(WidgetType.TEXT);when(number.getText()).thenReturn("");
        when(root.getStaticChildren()).thenReturn(new Widget[]{backing,fill,icon,number,button});
        for(Widget widget:new Widget[]{backing,fill,icon})
        {
            when(widget.getType()).thenReturn(WidgetType.GRAPHIC);
            BufferedImage image=new BufferedImage(widget.getWidth(),widget.getHeight(),BufferedImage.TYPE_INT_ARGB);
            Graphics2D draw=image.createGraphics();draw.setColor(color);draw.fillRect(0,0,image.getWidth(),image.getHeight());draw.dispose();
            when(overlay.sprites.getPadded(widget)).thenReturn(image);when(overlay.sprites.get(widget)).thenReturn(image);
        }
        return button;
    }

    private static BufferedImage paint(CircularOrbs overlay)
    {
        BufferedImage image=new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB);Graphics2D draw=image.createGraphics();
        draw.setColor(Color.MAGENTA);draw.fillRect(0,0,300,300);overlay.render(draw);draw.dispose();return image;
    }

    private static MenuEntry entry(OrbOrder orb,MenuAction action)
    { MenuEntry entry=mock(MenuEntry.class);when(entry.getParam1()).thenReturn(orb.target.getButtonId());when(entry.getType()).thenReturn(action);return entry; }
}
