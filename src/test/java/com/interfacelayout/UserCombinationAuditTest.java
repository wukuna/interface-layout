package com.interfacelayout;
import com.google.inject.*;
import com.interfacelayout.layout.menu.*;
import com.interfacelayout.layout.orbs.*;
import com.interfacelayout.layout.orbs.widget.*;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.util.*;
import java.lang.reflect.*;
import java.awt.Rectangle;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class UserCombinationAuditTest {
 static void inject(Object o,Class<?> c,String name,Object value)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static Widget visible(int id){Widget w=mock(Widget.class);boolean[] h={false};when(w.getId()).thenReturn(id);when(w.getIndex()).thenReturn(-1);when(w.isSelfHidden()).thenAnswer(i->h[0]);doAnswer(i->{h[0]=i.getArgument(0);return null;}).when(w).setHidden(anyBoolean());return w;}
 @Test public void sidePanelAllModesPresetsAndCompetingVisibility()throws Exception{
  Client client=mock(Client.class);InterfaceLayoutConfig c=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);NativeOrbStateStore states=new NativeOrbStateStore();WidgetManager widgets=spy(new WidgetManager());OrbController controller=spy(new OrbController());Widget map=visible(Minimap.MODERN_MAP_MINIMAP.getComponentId()),orbs=visible(Minimap.MODERN_ORBS_CONTAINER.getComponentId());when(client.getWidget(map.getId())).thenReturn(map);when(client.getWidget(orbs.getId())).thenReturn(orbs);
  inject(widgets,WidgetManager.class,"client",client);inject(widgets,WidgetManager.class,"nativeStates",states);inject(controller,OrbController.class,"client",client);inject(controller,OrbController.class,"config",c);inject(controller,OrbController.class,"widgetManager",widgets);doNothing().when(controller).updateLogoutXPosition();doReturn(false).when(controller).isWikiPluginConfigEnabled();
  int cases=0;for(OrbLayout layout:OrbLayout.values())for(int mode=0;mode<3;mode++)for(int bits=0;bits<128;bits++){
   states.restore();map.setHidden(false);orbs.setHidden(false);when(c.layout()).thenReturn(layout);when(c.hideMinimap()).thenReturn((bits&1)!=0);when(c.hideMinimapWithSidePanel()).thenReturn((bits&2)!=0);doReturn(mode==0).when(controller).isFixedMode();doReturn(mode==1).when(controller).isClassicResizable();doReturn((bits&4)!=0).when(controller).isSidePanelHidden();doReturn((bits&8)!=0).when(controller).isMinimapMinimized();doReturn((bits&16)!=0).when(controller).isMinimapPluginConfigEnabled();controller.isCutsceneActive=(bits&32)!=0;boolean gameHidden=(bits&64)!=0;map.setHidden(gameHidden);
   controller.hideMinimapOnTabClose((bits&2)!=0);controller.flushSidePanelVisibility();boolean hide=mode==2&&(bits&2)!=0&&(bits&4)!=0&&(bits&8)==0&&(bits&16)==0&&(bits&32)==0;String label=layout+" mode="+mode+" flags="+bits;assertEquals(label,gameHidden||hide,map.isSelfHidden());assertEquals(label,hide,orbs.isSelfHidden());assertEquals(label,layout!=OrbLayout.FREE_POSITION&&mode!=0&&(bits&1)!=0&&(bits&8)==0,controller.isCompactLayout());
   controller.hideMinimapOnTabClose(false);controller.flushSidePanelVisibility();assertEquals(label,gameHidden,map.isSelfHidden());assertFalse(label,orbs.isSelfHidden());states.restore();cases++;clearInvocations(controller,widgets,client,map,orbs,c);
  }System.out.println("AUDIT side panel mode/preset/visibility states="+cases);
 }
 @Test public void menuHidePairsLayoutModesSpacingAndNarrowCanvas()throws Exception{
  Client client=mock(Client.class);int[] canvas={1000,700};when(client.getCanvasWidth()).thenAnswer(i->canvas[0]);when(client.getCanvasHeight()).thenAnswer(i->canvas[1]);Map<String,Boolean> flags=new HashMap<>();Map<String,Object> choices=new HashMap<>();InterfaceLayoutConfig c=mock(InterfaceLayoutConfig.class,i->flags.containsKey(i.getMethod().getName())?flags.get(i.getMethod().getName()):choices.containsKey(i.getMethod().getName())?choices.get(i.getMethod().getName()):i.callRealMethod());OrbController mode=mock(OrbController.class);when(mode.isLoggedIn()).thenReturn(true);WidgetStateStore states=new WidgetStateStore();MenuStoneBackgrounds bg=mock(MenuStoneBackgrounds.class);ConfigManager configs=mock(ConfigManager.class);
  Map<Integer,Widget> live=new HashMap<>();when(client.getWidget(anyInt())).thenAnswer(i->live.get(i.getArgument(0)));MenuStoneController menu=Guice.createInjector(new AbstractModule(){protected void configure(){bind(Client.class).toInstance(client);bind(InterfaceLayoutConfig.class).toInstance(c);bind(OrbController.class).toProvider(()->mode);bind(ConfigManager.class).toInstance(configs);bind(WidgetStateStore.class).toInstance(states);bind(MenuStoneBackgrounds.class).toInstance(bg);bind(WidgetBoundsExpander.class).toProvider(()->mock(WidgetBoundsExpander.class));}}).getInstance(MenuStoneController.class);
  List<String> keys=new ArrayList<>();for(Method m:InterfaceLayoutConfig.class.getDeclaredMethods())if(m.getReturnType()==boolean.class&&m.getName().endsWith("Stone"))keys.add(m.getName());Collections.sort(keys);List<Map<String,Boolean>> profiles=new ArrayList<>();for(boolean all:new boolean[]{false,true}){Map<String,Boolean> base=new HashMap<>();for(String k:keys)base.put(k,all);profiles.add(base);for(int a=0;a<keys.size();a++)for(int b=a+1;b<keys.size();b++)for(int bits=0;bits<4;bits++){Map<String,Boolean> f=new HashMap<>(base);f.put(keys.get(a),(bits&1)!=0);f.put(keys.get(b),(bits&2)!=0);profiles.add(f);}}
  int cases=0;for(int display=0;display<3;display++){
   when(mode.isFixedMode()).thenReturn(display==0);when(mode.isClassicResizable()).thenReturn(display==1);int[][] registry=display==0?MenuStoneRegistry.FIXED:display==1?MenuStoneRegistry.CLASSIC:MenuStoneRegistry.MODERN;live.clear();for(int n=0;n<registry.length;n++){Widget stone=visible(registry[n][0]),icon=visible(registry[n][1]);when(stone.getWidth()).thenReturn(33);when(stone.getHeight()).thenReturn(36);when(icon.getWidth()).thenReturn(20);when(icon.getHeight()).thenReturn(20);when(stone.getRelativeX()).thenReturn(400+(n%7)*33);when(stone.getRelativeY()).thenReturn(500+(n/7)*36);when(icon.isHidden()).thenAnswer(i->icon.isSelfHidden());when(stone.isHidden()).thenAnswer(i->stone.isSelfHidden());live.put(registry[n][0],stone);live.put(registry[n][1],icon);}
   for(MenuStoneController.Layout layout:MenuStoneController.Layout.values())for(Map<String,Boolean> profile:profiles){flags.clear();flags.putAll(profile);choices.put("menuLayout",layout);choices.put("menuSpacing",cases%3==0?0:cases%3==1?4:20);canvas[0]=cases%2==0?1000:200;canvas[1]=cases%2==0?700:100;states.restore();menu.apply();for(Rectangle r:menu.stones().values()){assertTrue("Negative stone extent",r.width>0&&r.height>0);assertTrue("Off-canvas at "+layout+" mode="+display+" "+profile,r.x>=0&&r.y>=0&&r.x+r.width<=canvas[0]&&r.y+r.height<=canvas[1]);}if(layout==MenuStoneController.Layout.HIDDEN)assertTrue(menu.stones().isEmpty());states.restore();for(Widget w:live.values())assertFalse("Disable must restore visibility",w.isSelfHidden());cases++;if(cases%50==0){clearInvocations(client,c,mode,configs,bg);for(Widget w:live.values())clearInvocations(w);}}
  }System.out.println("AUDIT menu-stone boolean pairs="+profiles.size()+" layout/mode/canvas cases="+cases);
 }
}
