package com.interfacelayout.layout.equipment;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** Compacts only four utility controls. Equipment slots and native operations are untouched. */
@Singleton
public class BetaEquipmentPanel extends Overlay
{
    private static final int[] BUTTONS={InterfaceID.Wornitems.EQUIPMENT,InterfaceID.Wornitems.PRICECHECKER,InterfaceID.Wornitems.DEATHKEEP,InterfaceID.Wornitems.CALL_FOLLOWER};
    private static final int[] ICONS={InterfaceID.Wornitems.EQUIPMENT_ICON,InterfaceID.Wornitems.ROOT_GRAPHIC2,InterfaceID.Wornitems.ROOT_GRAPHIC3,InterfaceID.Wornitems.ROOT_GRAPHIC4};
    private static final String[] LABELS={"Stats","Prices","Death","Follower"};
    @Inject Client client;
    @Inject ClientThread clientThread;
    @Inject InterfaceLayoutConfig config;
    @Inject ItemManager items;
    @Inject MenuButtonSprites sprites;
    @Inject TooltipManager tooltips;
    private final Map<Widget,Geometry> moved=new IdentityHashMap<>();
    private final Set<Widget> hidden=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<View> views=new ArrayList<>();
    private EquipmentReadout data;
    private volatile boolean dirty=true,running;
    private volatile int generation;
    private int scriptDepth;
    private boolean refreshAfterScript;

    public BetaEquipmentPanel() {
        setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    public void start() {running=true;generation++;dirty=true;}
    public void stop() {
        running=false;int stopped=++generation;
        clientThread.invoke(()->{if(!running && stopped==generation){restore();data=null;scriptDepth=0;refreshAfterScript=false;}});
    }
    @Subscribe public void onGameTick(GameTick event) {dirty=true;}
    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) {if(event.getContainerId()==InventoryID.WORN)dirty=true;}
    @Subscribe public void onConfigChanged(ConfigChanged event) {dirty=true;}
    @Subscribe public void onProfileChanged(ProfileChanged event) {
        dirty=true;int requested=++generation;
        clientThread.invokeLater(()->{if(running && requested==generation){restore();data=null;}});
    }
    @Subscribe(priority=3) public void onScriptPreFired(ScriptPreFired event) {
        // Release owned geometry/visibility before native scripts update, hide or
        // rebuild these widgets. Reapply only after the outer script finishes.
        // This also preserves native page changes and activity-disabled controls.
        if(!running)return;
        scriptDepth++;
        if(!moved.isEmpty() || !hidden.isEmpty()){restore();refreshAfterScript=true;}
    }
    @Subscribe(priority=-3) public void onScriptPostFired(ScriptPostFired event) {
        if(!running)return;
        scriptDepth=Math.max(0,scriptDepth-1);
        if(scriptDepth==0 && refreshAfterScript) {
            refreshAfterScript=false;
            // Restore the compact geometry before subsequent native menu hit-testing,
            // rather than leaving the old button positions active until the next draw.
            onBeforeRender(null);
        }
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if(event.getGameState()!=GameState.LOGGED_IN){restore();data=null;dirty=true;}
    }
    @Subscribe(priority=-3) public void onBeforeRender(BeforeRender event) {
        if(event!=null){scriptDepth=0;refreshAfterScript=false;}
        if(!running || !config.betaEquipmentPanel() || client.getGameState()!=GameState.LOGGED_IN) {restore();data=null;dirty=true;return;}
        views.clear();
        Widget tab=client.getWidget(InterfaceID.Wornitems.UNIVERSE);
        if(!visible(tab)) {restore();return;}
        if(dirty || data==null) {data=EquipmentReadout.capture(client,items);dirty=false;}
        Set<Widget> retained=Collections.newSetFromMap(new IdentityHashMap<>());
        if(visible(tab) && tab.getWidth()>=184 && tab.getHeight()>=261) {
            Rectangle bounds=tab.getBounds();Rectangle footer=new Rectangle(bounds.x+3,bounds.y+198,184,EquipmentReadoutRenderer.HEIGHT);
            boolean[] available=new boolean[4];
            for(int i=0;i<BUTTONS.length;i++) {
                Widget button=client.getWidget(BUTTONS[i]),icon=client.getWidget(ICONS[i]);
                if(!visible(button)) continue;
                available[i]=true;
                place(button,7+i*21,243,18,18);retained.add(button);
                hide(icon,retained);
                // The button's dynamic descendants are decorative native frame pieces.
                // Retain the native parent and its action/listeners, so clicks remain native.
                Widget[] frame=button.getDynamicChildren();
                if(frame!=null) for(Widget piece:frame) hide(piece,retained);
            }
            views.add(new View(tab,footer,data,available));
        }
        restoreUnused(retained);
    }
    private static boolean visible(Widget widget) {return widget!=null && !widget.isHidden();}
    private void hide(Widget widget,Set<Widget> retained) {
        if(widget==null)return;retained.add(widget);
        if(!widget.isSelfHidden()) {hidden.add(widget);widget.setHidden(true);}
    }
    private void place(Widget widget,int x,int y,int width,int height) {
        Geometry current=Geometry.read(widget),saved=moved.get(widget);
        if(saved==null || !current.equals(saved.applied)) {saved=current;moved.put(widget,saved);}
        Geometry desired=new Geometry(x,y,width,height,0,0,0,0);
        if(!desired.equals(current)){desired.apply(widget);widget.revalidate();}
        saved.applied=desired;
    }
    private boolean current(Widget widget) {
        if(widget.getIndex()<0)return client.getWidget(widget.getId())==widget;
        Widget parent=client.getWidget(widget.getId());return parent!=null && parent.getChild(widget.getIndex())==widget;
    }
    private void restoreUnused(Set<Widget> retained) {
        moved.entrySet().removeIf(entry->{if(retained.contains(entry.getKey()))return false;
            Widget widget=entry.getKey();if(current(widget) && Geometry.read(widget).equals(entry.getValue().applied)){entry.getValue().apply(widget);widget.revalidate();}return true;});
        hidden.removeIf(widget->{if(retained.contains(widget))return false;if(current(widget) && widget.isSelfHidden())widget.setHidden(false);return true;});
    }
    private void restore() {views.clear();restoreUnused(Collections.emptySet());}
    @Override public Dimension render(Graphics2D graphics) {
        if(!running || !config.betaEquipmentPanel() || data==null)return null;
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();
        for(View view:views) {
            if(!visible(view.root) || !current(view.root))continue;
            boolean reducedTooltips=config.betaEquipmentReducedTooltips();
            List<EquipmentReadoutRenderer.Hint> hints=EquipmentReadoutRenderer.draw(graphics,view.bounds,view.data,reducedTooltips);
            for(int i=0;i<4;i++) if(view.available[i]) {
                Rectangle r=new Rectangle(view.bounds.x+4+i*21,view.bounds.y+45,18,18);
                Graphics2D g=(Graphics2D)graphics.create();try {
                    g.setColor(new Color(55,53,44));g.fillRect(r.x,r.y,r.width,r.height);
                    g.setColor(new Color(126,113,83));g.drawRect(r.x,r.y,17,17);
                    BufferedImage icon=sprites.get(client.getWidget(ICONS[i]));
                    if(icon!=null)g.drawImage(icon,r.x+2,r.y+2,14,14,null);
                    else {g.setFont(new Font("Arial",Font.BOLD,9));g.setColor(Color.WHITE);g.drawString(LABELS[i].substring(0,1),r.x+5,r.y+12);}
                } finally {g.dispose();}
                hints.add(new EquipmentReadoutRenderer.Hint(r,LABELS[i]+(reducedTooltips?"":" - native equipment control")));
            }
            if(tooltips!=null && mouse!=null && !client.isMenuOpen())for(var hint:hints)if(hint.bounds.contains(mouse.getX(),mouse.getY()))tooltips.add(new Tooltip(hint.text));
        }
        return null;
    }
    private static final class View {
        final Widget root;final Rectangle bounds;final EquipmentReadout data;final boolean[] available;
        View(Widget root,Rectangle bounds,EquipmentReadout data,boolean[] available){this.root=root;this.bounds=bounds;this.data=data;this.available=available;}
    }
    private static final class Geometry {
        final int[] values;Geometry applied;
        Geometry(int... values){this.values=values;}
        static Geometry read(Widget w){return new Geometry(w.getOriginalX(),w.getOriginalY(),w.getOriginalWidth(),w.getOriginalHeight(),w.getXPositionMode(),w.getYPositionMode(),w.getWidthMode(),w.getHeightMode());}
        void apply(Widget w){w.setPos(values[0],values[1],values[4],values[5]);w.setSize(values[2],values[3],values[6],values[7]);}
        @Override public boolean equals(Object o){return o instanceof Geometry && Arrays.equals(values,((Geometry)o).values);}
        @Override public int hashCode(){return Arrays.hashCode(values);}
    }
}
