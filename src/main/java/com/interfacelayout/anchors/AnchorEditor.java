package com.interfacelayout.anchors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.FocusChanged;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.ui.overlay.*;

@Singleton
public final class AnchorEditor extends MouseAdapter
{
    @Inject private AnchorCoordinator coordinator;
    @Inject private MouseManager mice;
    @Inject private KeyManager keys;
    @Inject private RuneLiteConfig config;
    @Inject private EventBus events;
    @Inject private OverlayManager overlays;
    private volatile boolean held,active;
    private HotkeyListener hotkey;
    private final Overlay background=new Drawing(false),handles=new Drawing(true);
    private Point pressed;
    private String anchor,session;
    private boolean resize,captured,swallow;
    private long revision;
    private Dimension canvas;
    private AnchorMemberDrag member;
    private boolean firstListener;
    private double scaleX,scaleY;
    private boolean headerDrag,headerAbove;
    private int headerGrabY,anchorTop;
    public void start()
    {
        active=true;hotkey=new HotkeyListener(config::dragHotkey) {
            @Override public void hotkeyPressed() {held=true;}
            @Override public void hotkeyReleased() {held=false;cancel();}
        };
        keys.registerKeyListener(hotkey);mice.registerMouseListener(0,this);events.register(this);overlays.add(background);overlays.add(handles);
    }
    public void stop() {active=false;cancel();keys.unregisterKeyListener(hotkey);mice.unregisterMouseListener(this);events.unregister(this);overlays.remove(background);overlays.remove(handles);}
    @Subscribe public void onFocusChanged(FocusChanged event) {if(!event.isFocused()){held=false;cancel();}}
    @Subscribe public synchronized void onPluginChanged(net.runelite.client.events.PluginChanged event){firstListener=false;cancel();}
    @Subscribe(priority=-40) public synchronized void onBeforeRender(net.runelite.api.events.BeforeRender event)
    {
        boolean editing=active && coordinator.editing();
        if(editing && !firstListener){mice.unregisterMouseListener(this);mice.registerMouseListener(0,this);}
        firstListener=editing;
        if(captured && !valid())cancel();
    }
    private synchronized void cancel() {swallow|=captured;captured=false;pressed=null;anchor=null;member=null;coordinator.preview(null);coordinator.previewMember(null);}
    @Override public synchronized MouseEvent mousePressed(MouseEvent event)
    {
        swallow=false;
        if(!active || !held || !coordinator.editing() || event.isConsumed() || event.getButton()!=MouseEvent.BUTTON1)return event;
        if(event.isShiftDown() && (config.dragHotkey().getModifiers() & java.awt.event.InputEvent.SHIFT_DOWN_MASK)==0 && config.dragHotkey().getKeyCode()!=java.awt.event.KeyEvent.VK_SHIFT)return event;
        AnchorCoordinator.Snapshot view=coordinator.snapshot();
        if(!view.inputAllowed)return event;
        Point point=view.mousePoint(event);
        member=null;headerDrag=false;
        List<AnchorLayout.Anchor> anchors=new ArrayList<>(view.layout.anchors);Collections.reverse(anchors);
        AnchorGeometry.Target control=AnchorMemberDrag.pick(view,point);
        for(AnchorLayout.Anchor candidate:anchors) {
            Rectangle bounds=view.geometry.anchors.get(candidate.id);
            if(!candidate.enabled || candidate.locked || bounds==null)continue;
            Rectangle header=headerBounds(bounds,view.canvas);boolean title=header.contains(point);
            if(!title && !bounds.contains(point))continue;
            Rectangle grip=new Rectangle(bounds.x+bounds.width-12,bounds.y+bounds.height-12,12,12);
            boolean hit=grip.contains(point);
            if(!title && !hit && control!=null)continue;
            if(!title && !hit && point.y>bounds.y+5 && point.x>bounds.x+5 && point.x<bounds.x+bounds.width-5 && point.y<bounds.y+bounds.height-5)continue;
            headerDrag=title;headerAbove=header.y<bounds.y;headerGrabY=point.y-header.y;anchorTop=bounds.y;
            pressed=point;anchor=candidate.id;session=view.session;revision=view.revision;canvas=new Dimension(view.canvas);scaleX=view.mouseScaleX;scaleY=view.mouseScaleY;resize=hit;captured=true;event.consume();break;
        }
        if(!captured && control!=null){member=new AnchorMemberDrag(view,control,point);captured=true;event.consume();}
        return event;
    }
    @Override public synchronized MouseEvent mouseDragged(MouseEvent event)
    {
        if(!captured)return event;event.consume();
        if(!valid()){cancel();return event;}
        Point point=coordinator.snapshot().mousePoint(event);
        if(member!=null){member=member.at(point);coordinator.previewMember(member);return event;}
        coordinator.preview(anchorDrag(point));return event;
    }
    @Override public synchronized MouseEvent mouseReleased(MouseEvent event)
    {
        if(event.getButton()!=MouseEvent.BUTTON1)return event;
        if(!captured){if(swallow)event.consume();return event;}event.consume();swallow=true;
        if(valid()) {
            Point point=coordinator.snapshot().mousePoint(event);
            if(member!=null)coordinator.finishMemberDrag(member.at(point));
            else coordinator.finishDrag(anchorDrag(point));
        } else {coordinator.preview(null);coordinator.previewMember(null);}
        captured=false;member=null;return event;
    }
    @Override public synchronized MouseEvent mouseClicked(MouseEvent event) {if(swallow){event.consume();swallow=false;}return event;}
    @Override public synchronized MouseEvent mouseExited(MouseEvent event){if(captured && !headerDrag)cancel();return event;}
    private AnchorCoordinator.Drag anchorDrag(Point point)
    {
        int dy=headerDrag && headerAbove && point.y<=headerGrabY?-anchorTop:point.y-pressed.y;
        return new AnchorCoordinator.Drag(session,anchor,point.x-pressed.x,dy,resize,canvas);
    }
    private boolean valid() {AnchorCoordinator.Snapshot view=coordinator.snapshot();return active && coordinator.editing() && view.inputAllowed && (member!=null?member.valid(view):view.session.equals(session) && view.revision==revision && view.canvas.equals(canvas) && scaleX==view.mouseScaleX && scaleY==view.mouseScaleY);}
    static Rectangle headerBounds(Rectangle bounds,Dimension canvas)
    {
        int height=18,width=Math.min(canvas.width,Math.max(80,bounds.width)),x=Math.max(0,Math.min(canvas.width-width,bounds.x));
        if(bounds.y>=height+2)return new Rectangle(x,bounds.y-height-2,width,height);
        if(bounds.y+bounds.height+height+2<=canvas.height)return new Rectangle(x,bounds.y+bounds.height+2,width,height);
        return new Rectangle(x,bounds.y-height-2,width,height);
    }
    private final class Drawing extends Overlay
    {
        private final boolean edit;
        Drawing(boolean edit) {this.edit=edit;setPosition(OverlayPosition.DYNAMIC);setLayer(edit?OverlayLayer.ABOVE_WIDGETS:OverlayLayer.UNDER_WIDGETS);setPriority(Overlay.PRIORITY_LOW);}
        @Override public Dimension render(Graphics2D graphics)
        {
            if(!active || edit && !coordinator.editing())return null;
            AnchorCoordinator.Snapshot view=coordinator.snapshot();Graphics2D g=(Graphics2D)graphics.create();
            try {
                for(AnchorLayout.Anchor anchor:view.layout.anchors) {
                    Rectangle bounds=view.geometry.anchors.get(anchor.id);if(bounds==null)continue;
                    if(edit) {
                        g.setColor(anchor.locked?new Color(125,130,135):new Color(80,180,235));g.setStroke(new BasicStroke(1));g.draw(bounds);
                        Rectangle header=headerBounds(bounds,view.canvas);Graphics2D label=(Graphics2D)g.create();
                        try {label.clip(header);label.setColor(new Color(28,32,37,235));label.fill(header);label.setColor(anchor.locked?new Color(155,160,165):new Color(110,200,245));label.draw(header);label.drawString(anchor.name+(anchor.locked?" · locked":""),header.x+5,header.y+13);}finally{label.dispose();}
                        if(!anchor.locked)g.fillRect(bounds.x+bounds.width-7,bounds.y+bounds.height-7,6,6);
                    } else {
                        if(anchor.background){g.setColor(new Color(anchor.backgroundColor,true));g.fill(bounds);}
                        if(anchor.border){g.setColor(new Color(anchor.borderColor,true));g.draw(bounds);}
                    }
                }
                AnchorCoordinator.MemberPreview preview=coordinator.memberPreview();
                if(edit && preview!=null && preview.drag.moved() && preview.drag.valid(view)) {
                    AnchorMemberDrag.Drop drop=preview.drop;Rectangle ghost=drop==null?preview.drag.ghost():drop.member;
                    if(drop!=null){g.setColor(new Color(75,185,240,45));g.fill(drop.bounds);g.setColor(new Color(95,200,255));g.setStroke(new BasicStroke(2));g.draw(drop.bounds);}
                    g.setColor(drop==null?new Color(225,175,90,55):new Color(95,200,255,80));g.fill(ghost);
                    g.setColor(drop==null?new Color(225,175,90):new Color(130,220,255));g.setStroke(new BasicStroke(2));g.draw(ghost);
                    g.drawString(drop==null?"Drop onto an unlocked anchor":"Release to place in "+drop.name,Math.max(4,Math.min(view.canvas.width-190,ghost.x)),Math.max(14,ghost.y-5));
                }
            } finally {g.dispose();}
            return null;
        }
    }
}
