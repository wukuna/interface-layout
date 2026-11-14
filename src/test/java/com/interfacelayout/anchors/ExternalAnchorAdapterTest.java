package com.interfacelayout.anchors;

import com.google.common.hash.Hashing;
import fixtures.OrdinaryOverlay;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExternalAnchorAdapterTest
{
    private static OverlayManager manager()throws Exception
    {
        var constructor=OverlayManager.class.getDeclaredConstructors()[0];constructor.setAccessible(true);Class<?>[] types=constructor.getParameterTypes();Object[] values=new Object[types.length];for(int i=0;i<types.length;i++)values[i]=mock(types[i]);return (OverlayManager)constructor.newInstance(values);
    }
    private static void inject(Object target,String key,Object value)throws Exception{Field field=target.getClass().getDeclaredField(key);field.setAccessible(true);field.set(target,value);}
    private static String id(Overlay overlay){return "external:"+Hashing.sha256().hashString(overlay.getClass().getName()+"\n"+overlay.getName(),StandardCharsets.UTF_8);}
    private static ExternalAnchorAdapter adapter(OverlayManager manager,ConfigManager config)throws Exception
    {
        ExternalAnchorAdapter adapter=new ExternalAnchorAdapter();inject(adapter,"overlays",manager);inject(adapter,"configs",config);inject(adapter,"coordinator",new AnchorCoordinator());return adapter;
    }
    @Test public void optedInOrdinaryOverlayMovesAndRestoresWithoutChangingItsSettings()throws Exception
    {
        OverlayManager manager=manager();ConfigManager config=mock(ConfigManager.class);OrdinaryOverlay overlay=new OrdinaryOverlay();manager.add(overlay);
        overlay.setPreferredLocation(new Point(80,90));overlay.setPreferredSize(new Dimension(130,40));
        ExternalAnchorAdapter adapter=adapter(manager,config);assertTrue(adapter.targets().isEmpty());
        when(config.getConfiguration("interfacelayout","anchorExternalOverlaysV1")).thenReturn("[\""+id(overlay)+"\"]");
        var target=adapter.targets().iterator().next();assertEquals("VISIBLE",target.state());assertTrue(target.claim());target.move(new Point(300,350));
        assertEquals(new Point(300,350),overlay.getBounds().getLocation());assertEquals(OverlayPosition.DYNAMIC,overlay.getPosition());assertEquals(OverlayLayer.UNDER_WIDGETS,overlay.getLayer());assertFalse(overlay.isMovable());
        target.release(false);assertEquals(OverlayPosition.TOP_LEFT,overlay.getPosition());assertEquals(new Point(80,90),overlay.getPreferredLocation());assertEquals(new Dimension(130,40),overlay.getPreferredSize());assertTrue(overlay.isMovable());assertTrue(overlay.isSnappable());
        verify(config,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void dynamicManualAndAmbiguousOverlaysAreExcluded()throws Exception
    {
        OrdinaryOverlay overlay=new OrdinaryOverlay();assertTrue(ExternalAnchorAdapter.supported(overlay));
        overlay.setPosition(OverlayPosition.DYNAMIC);assertFalse(ExternalAnchorAdapter.supported(overlay));
        overlay.setPosition(OverlayPosition.TOOLTIP);assertFalse(ExternalAnchorAdapter.supported(overlay));
        overlay.setPosition(OverlayPosition.TOP_LEFT);overlay.setLayer(OverlayLayer.MANUAL);assertFalse(ExternalAnchorAdapter.supported(overlay));
        overlay.setLayer(OverlayLayer.UNDER_WIDGETS);overlay.immovable();assertFalse(ExternalAnchorAdapter.supported(overlay));
        OverlayManager manager=manager();OrdinaryOverlay first=new OrdinaryOverlay(),second=new OrdinaryOverlay();manager.add(first);manager.add(second);
        ConfigManager config=mock(ConfigManager.class);when(config.getConfiguration("interfacelayout","anchorExternalOverlaysV1")).thenReturn("[\""+id(first)+"\"]");
        assertTrue(adapter(manager,config).targets().isEmpty());verify(config,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void nativeInfoboxGroupsCanBeDiscoveredAndPlacedDuringEditingWithoutOptIn()throws Exception
    {
        var constructor=net.runelite.client.ui.overlay.infobox.InfoBoxOverlay.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Overlay group=(Overlay)constructor.newInstance(null,null,null,null,null,"Timers",net.runelite.client.ui.overlay.components.ComponentOrientation.HORIZONTAL);
        OverlayManager manager=manager();manager.add(group);ConfigManager config=mock(ConfigManager.class);
        group.getBounds().setBounds(40,60,120,35);group.setPreferredLocation(new Point(40,60));
        ExternalAnchorAdapter adapter=adapter(manager,config);AnchorCoordinator coordinator=new AnchorCoordinator();inject(adapter,"coordinator",coordinator);
        assertTrue(adapter.targets().isEmpty());coordinator.editing(true);
        var target=adapter.targets().iterator().next();assertTrue(target.claim());target.move(new Point(250,300));
        assertEquals(new Point(250,300),group.getBounds().getLocation());target.release(false);
        assertEquals(OverlayPosition.TOP_LEFT,group.getPosition());assertEquals(new Point(40,60),group.getPreferredLocation());
        verify(config,never()).setConfiguration(anyString(),anyString(),any());
    }
}
