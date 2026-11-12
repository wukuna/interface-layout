package com.interfacelayout.layout.orbs;
import com.interfacelayout.InterfaceLayoutConfig;
import com.soulreaperaxeqol.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SoulreaperOrbCompatibilityTest {
    static class Fixture {
        final SoulreaperOrbCompatibility adapter=new SoulreaperOrbCompatibility();
        final SoulreaperAxeQoLExtraOrbOverlay extra=new SoulreaperAxeQoLExtraOrbOverlay();
        final SoulreaperAxeQoLNativeOrbOverlay nativeOrb=new SoulreaperAxeQoLNativeOrbOverlay();
        final List<Overlay> active=new ArrayList<>();
        final Widget parent;
        Fixture() throws Exception {
            adapter.client=mock(Client.class);adapter.configs=mock(ConfigManager.class);
            adapter.config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);adapter.overlays=mock(OverlayManager.class);
            when(adapter.config.hideOrbTabs()).thenReturn(true);
            active.add(extra);active.add(nativeOrb);active.add(adapter);
            when(adapter.overlays.anyMatch(any())).thenAnswer(i->{Predicate<Overlay> predicate=i.getArgument(0);return new ArrayList<>(active).stream().anyMatch(predicate);});
            when(adapter.overlays.remove(any())).thenAnswer(i->active.remove(i.getArgument(0)));
            when(adapter.overlays.add(any())).thenAnswer(i->active.add(i.getArgument(0)));
            parent=CircularOrbInputTest.widget(adapter.client,InterfaceID.Orbs.ORB_SPECENERGY,null,100,70,57,34);
            ItemContainer equipment=mock(ItemContainer.class);
            when(adapter.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(equipment);
            when(equipment.getItem(3)).thenReturn(new Item(net.runelite.api.gameval.ItemID.SOULREAPER,1));
            when(adapter.client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(1000);
            when(adapter.configs.getConfiguration("soulreaperaxeqol","offsetX")).thenReturn("0");
            when(adapter.configs.getConfiguration("soulreaperaxeqol","offsetY")).thenReturn("-35");
            set("imageOwner",extra);set("specialIcon",icon());set("axeIcon",icon());
        }
        void set(String name,Object value) throws Exception {Field f=SoulreaperOrbCompatibility.class.getDeclaredField(name);f.setAccessible(true);f.set(adapter,value);}
        void refresh() {adapter.refresh(true);adapter.anchor(new Rectangle(123,70,34,34));}
        BufferedImage render() {BufferedImage image=new BufferedImage(200,130,2);Graphics2D g=image.createGraphics();adapter.render(g);g.dispose();return image;}
    }
    @Test public void replacesOnlyRecognizedDisplaysAndRestoresTheirLatestLayers() throws Exception {
        Fixture f=new Fixture();Overlay other=mock(Overlay.class);f.active.add(other);
        f.extra.setLayer(OverlayLayer.UNDER_WIDGETS);f.refresh();
        assertEquals(OverlayLayer.MANUAL,f.extra.getLayer());assertEquals(OverlayLayer.MANUAL,f.nativeOrb.getLayer());
        assertEquals(OverlayLayer.UNDER_WIDGETS,f.adapter.getLayer());
        f.extra.setLayer(OverlayLayer.ABOVE_WIDGETS);f.refresh();f.adapter.restore();
        assertEquals(OverlayLayer.ABOVE_WIDGETS,f.extra.getLayer());assertEquals(OverlayLayer.ABOVE_WIDGETS,f.nativeOrb.getLayer());
        verify(other,never()).setLayer(any());assertEquals(4,f.active.size());
    }
    @Test public void providerShutdownIsNotUndoneAndItsObjectsCanRestartNormally() throws Exception {
        Fixture f=new Fixture();f.refresh();f.active.remove(f.extra);f.active.remove(f.nativeOrb);f.adapter.refresh(true);
        assertFalse(f.active.contains(f.extra));assertFalse(f.active.contains(f.nativeOrb));
        assertEquals(OverlayLayer.ABOVE_WIDGETS,f.extra.getLayer());assertEquals(OverlayLayer.ABOVE_WIDGETS,f.nativeOrb.getLayer());
        f.active.add(f.extra);f.active.add(f.nativeOrb);f.refresh();f.adapter.refresh(false);
        assertEquals(OverlayLayer.ABOVE_WIDGETS,f.extra.getLayer());
    }
    @Test public void contentModesUseSavedOffsetsWithoutASecondSideTab() throws Exception {
        Fixture f=new Fixture();
        for(InterfaceLayoutConfig.OrbContent mode:InterfaceLayoutConfig.OrbContent.values()) {
            when(f.adapter.config.orbContent()).thenReturn(mode);f.refresh();BufferedImage image=f.render();
            assertEquals(0,image.getRGB(105,50));assertEquals(0,image.getRGB(123,35));
            assertEquals(mode!=InterfaceLayoutConfig.OrbContent.NUMBER_ONLY,count(image,Color.MAGENTA)>0);
            assertEquals(mode!=InterfaceLayoutConfig.OrbContent.ICON_ONLY,count(image,Color.GREEN)>0);
        }
        assertTrue(f.extra.renders>0);
        when(f.parent.isHidden()).thenReturn(true);assertEquals(0,countVisible(f.render()));
        verify(f.adapter.configs,never()).setConfiguration(anyString(),anyString(),any(Object.class));
    }
    @Test public void coveredExtraOrbUsesItsOwnOffsetAndReturnsWhenTheWindowCloses() throws Exception {
        Fixture f=new Fixture();f.refresh();assertTrue(countVisible(f.render())>0);
        Widget bank=CircularOrbInputTest.widget(f.adapter.client,InterfaceID.Bankmain.UNIVERSE,null,125,40,20,20);
        assertEquals(0,countVisible(f.render()));
        when(bank.isHidden()).thenReturn(true);assertTrue(countVisible(f.render())>0);
        verify(f.adapter.configs,never()).setConfiguration(anyString(),anyString(),any(Object.class));
    }
    @Test public void residualStacksStayVisibleAfterSwitchingWeaponAndZeroStacksDisappear() throws Exception {
        Fixture f=new Fixture();when(f.adapter.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(null);
        when(f.adapter.client.getVarpValue(VarPlayerID.SOULREAPER_STACKS)).thenReturn(3);f.refresh();assertTrue(countVisible(f.render())>0);
        when(f.adapter.client.getVarpValue(VarPlayerID.SOULREAPER_STACKS)).thenReturn(0);assertEquals(0,countVisible(f.render()));
    }
    static BufferedImage icon() {BufferedImage image=new BufferedImage(26,26,2);Graphics2D g=image.createGraphics();g.setColor(Color.MAGENTA);g.fillRect(9,6,8,14);g.dispose();return image;}
    static int count(BufferedImage image,Color color) {int n=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==color.getRGB())n++;return n;}
    static int countVisible(BufferedImage image) {int n=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=0)n++;return n;}
}
