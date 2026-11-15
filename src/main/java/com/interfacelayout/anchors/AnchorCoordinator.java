package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import static com.interfacelayout.anchors.AnchorParticipant.*;

@Singleton
public final class AnchorCoordinator
{
    @Inject private Client client;
    @Inject private ClientThread thread;
    @Inject private EventBus events;
    @Inject private ConfigManager configs;
    @Inject private AnchorLayoutStore store;
    @Inject private com.google.gson.Gson gson;
    private final AnchorRegistry registry = new AnchorRegistry();
    private final AnchorGeometry geometry = new AnchorGeometry();
    private final Map<String, java.util.Set<String>> owned = new LinkedHashMap<>();
    private AnchorLayout layout = new AnchorLayout();
    private long revision = -1, request, nextHello;
    private String session = "";
    private volatile boolean active, editing, catalogActive;
    private volatile Snapshot view = new Snapshot("", new AnchorLayout(), new AnchorGeometry.Result(), Collections.emptyMap(), "", false, 0);
    private String message = "";
    private volatile Drag drag;
    private Drag lastDrag;
    private AnchorLayout draft;
    private volatile long inputEpoch;
    private volatile long profileEpoch;
    private volatile AnchorMemberDrag memberDrag;
    private volatile MemberPreview memberPreview;
    static final class MemberPreview
    {
        final AnchorMemberDrag drag;
        final AnchorMemberDrag.Drop drop;
        MemberPreview(AnchorMemberDrag drag,AnchorMemberDrag.Drop drop){this.drag=drag;this.drop=drop;}
    }
    public static final class Drag
    {
        public final String session,id;
        public final int dx,dy;
        public final boolean resize;
        public final Dimension canvas;
        public final Rectangle initial;
        public Drag(String session,String id,int dx,int dy,boolean resize) {this(session,id,dx,dy,resize,null);}
        public Drag(String session,String id,int dx,int dy,boolean resize,Dimension canvas) {this(session,id,dx,dy,resize,canvas,null);}
        public Drag(String session,String id,int dx,int dy,boolean resize,Dimension canvas,Rectangle initial) {this.session=session;this.id=id;this.dx=dx;this.dy=dy;this.resize=resize;this.canvas=canvas==null?null:new Dimension(canvas);this.initial=initial==null?null:new Rectangle(initial);}
    }

    public static final class Snapshot
    {
        public final String session;
        public final AnchorLayout layout;
        public final AnchorGeometry.Result geometry;
        public final Map<String, AnchorGeometry.Target> targets;
        public final String message;
        public final boolean canUndo;
        public final long revision;
        public final Dimension canvas;
        public final boolean inputAllowed;
        public final List<Rectangle> windows;
        public final long inputEpoch;
        public final double mouseScaleX,mouseScaleY;
        Snapshot(String session, AnchorLayout layout, AnchorGeometry.Result geometry, Map<String, AnchorGeometry.Target> targets, String message, boolean canUndo, long revision)
        {this(session,layout,geometry,targets,message,canUndo,revision,new Dimension());}
        Snapshot(String session, AnchorLayout layout, AnchorGeometry.Result geometry, Map<String, AnchorGeometry.Target> targets, String message, boolean canUndo, long revision,Dimension canvas)
        {this(session,layout,geometry,targets,message,canUndo,revision,canvas,false,Collections.emptyList(),0);}
        Snapshot(String session, AnchorLayout layout, AnchorGeometry.Result geometry, Map<String, AnchorGeometry.Target> targets, String message, boolean canUndo, long revision,Dimension canvas,boolean inputAllowed,List<Rectangle> windows,long inputEpoch)
        {this(session,layout,geometry,targets,message,canUndo,revision,canvas,inputAllowed,windows,inputEpoch,1,1);}
        Snapshot(String session, AnchorLayout layout, AnchorGeometry.Result geometry, Map<String, AnchorGeometry.Target> targets, String message, boolean canUndo, long revision,Dimension canvas,boolean inputAllowed,List<Rectangle> windows,long inputEpoch,double mouseScaleX,double mouseScaleY)
        { this.session=session; this.layout=layout; this.geometry=geometry; this.targets=targets; this.message=message; this.canUndo=canUndo; this.revision=revision;this.canvas=new Dimension(canvas);this.inputAllowed=inputAllowed;this.windows=windows;this.inputEpoch=inputEpoch;this.mouseScaleX=mouseScaleX;this.mouseScaleY=mouseScaleY; }
        java.awt.Point mousePoint(java.awt.event.MouseEvent event){return new java.awt.Point((int)(event.getX()*mouseScaleX),(int)(event.getY()*mouseScaleY));}
    }
    public void start()
    {
        active = true; events.register(this);
        thread.invoke(() -> { if (active) reset(); });
    }
    public void stop()
    {
        active = false; editing = false;invalidateInput(); events.unregister(this);
        thread.invoke(() -> { send("shutdown", Collections.emptyMap()); registry.clear(); owned.clear(); session=""; publish(new AnchorGeometry.Result(),Collections.emptyMap()); });
    }
    private void reset()
    {
        send("shutdown", Collections.emptyMap());
        session=UUID.randomUUID().toString(); request=0; nextHello=0; revision=-1;
        registry.clear(); owned.clear(); store.load(); message=""; editing=false;drag=null;lastDrag=null;draft=null;invalidateInput();
        layout=store.snapshot(); revision=store.revision();
        publish(new AnchorGeometry.Result(),Collections.emptyMap());
        send("hello", Collections.emptyMap());
    }
    @Subscribe public void onProfileChanged(ProfileChanged event) {profileEpoch++;invalidateInput();thread.invokeLater(() -> { if(active)reset(); }); }
    @Subscribe public void onGameStateChanged(net.runelite.api.events.GameStateChanged event){invalidateInput();}
    @Subscribe public void onFocusChanged(net.runelite.api.events.FocusChanged event){if(!event.isFocused())invalidateInput();}
    private void invalidateInput(){inputEpoch++;memberDrag=null;memberPreview=null;}
    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (!AnchorLayoutStore.GROUP.equals(event.getGroup()) || !AnchorLayoutStore.KEY.equals(event.getKey())) return;
        thread.invokeLater(() -> {
            if (!active) return;
            String current=configs.getConfiguration(AnchorLayoutStore.GROUP,AnchorLayoutStore.KEY);
            if (current==null || !current.equals(store.exportLayout())) { store.load(); message=""; }
        });
    }
    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if (!active || !NAMESPACE.equals(event.getNamespace())) return;
        thread.invoke(() -> {
            if(!active)return;
            Map<String,Object> data=event.getData();
            if(number(data.get("version"),-1)!=1)return;
            String token=string(data.get("provider"));
            if(token.isEmpty())return;
            if("ready".equals(event.getName())) { send("hello",Map.of("provider",token));return; }
            if(!session.equals(string(data.get("session"))))return;
            if("joined".equals(event.getName()))registry.join(token,string(data.get("owner")));
            else if("bye".equals(event.getName())) { registry.remove(token);owned.remove(token); }
            else if("measured".equals(event.getName()) && number(data.get("request"),-1)==request)
                registry.measure(token,request,data.get("targets"));
        });
    }
    @Subscribe(priority=-30) public void onBeforeRender(BeforeRender event)
    {
        if(!active || session.isEmpty())return;
        if(store.revision()!=revision) { layout=store.snapshot();revision=store.revision();drag=null;lastDrag=null;draft=null;memberDrag=null;memberPreview=null; }
        if(!view.canvas.equals(new Dimension(client.getCanvasWidth(),client.getCanvasHeight())))invalidateInput();
        long now=System.nanoTime();
        if(now>=nextHello) { nextHello=now+2000000000L;send("hello",Collections.emptyMap()); }
        if(layout.anchors.isEmpty() && !editing && !catalogActive && owned.values().stream().allMatch(java.util.Set::isEmpty))return;
        request++;
        send("measure",Map.of("request",request));
        Map<String,AnchorGeometry.Target> targets=registry.targets(request);
        Drag preview=drag;
        if(preview!=null && preview.session.equals(session) && preview!=lastDrag) {
            draft=store.snapshot();applyDrag(draft,preview);lastDrag=preview;
        }
        AnchorGeometry.Result result=geometry.solve(preview!=null && preview.session.equals(session) && draft!=null?draft:layout,targets,new Dimension(client.getCanvasWidth(),client.getCanvasHeight()));
        for(AnchorRegistry.Provider provider:registry.providers())
        {
            if(provider.measured!=request)continue;
            List<Map<String,Object>> placements=new ArrayList<>();
            java.util.Set<String> next=new java.util.LinkedHashSet<>();
            result.targets.forEach((id,bounds) -> {
                if(provider.targets.containsKey(id)) { placements.add(Map.of("id",id,"x",bounds.x,"y",bounds.y));next.add(id); }
            });
            java.util.Set<String> previous=owned.getOrDefault(provider.token,Collections.emptySet());
            List<String> release=new ArrayList<>(previous);release.removeAll(next);
            send("apply",Map.of("provider",provider.token,"request",request,"placements",placements,"release",release));
            owned.put(provider.token,next);
        }
        if(!result.targets.isEmpty())send("settle",Map.of("request",request));
        publish(result,targets);
        AnchorMemberDrag member=memberDrag;
        memberPreview=member!=null && member.valid(view)?new MemberPreview(member,member.plan(view,store.snapshot())):null;
    }
    private void publish(AnchorGeometry.Result result,Map<String,AnchorGeometry.Target> targets)
    {view=capture(result,targets);}
    private Snapshot capture(AnchorGeometry.Result result,Map<String,AnchorGeometry.Target> targets)
    {
        Dimension real=client.isStretchedEnabled()?client.getRealDimensions():null,surface=real==null?null:client.getStretchedDimensions();
        boolean scaled=real!=null && surface!=null && real.width>0 && real.height>0 && surface.width>0 && surface.height>0;
        return new Snapshot(session,layout,result,targets,store.error()==null?message:store.error(),store.canUndo(),revision,new Dimension(client.getCanvasWidth(),client.getCanvasHeight()),inputAllowed(),editing?com.interfacelayout.util.InterfaceWindows.bounds(client):Collections.emptyList(),inputEpoch,scaled?(double)real.width/surface.width:1,scaled?(double)real.height/surface.height:1);
    }
    private boolean inputAllowed(){return client.getGameState()==net.runelite.api.GameState.LOGGED_IN && !client.isMenuOpen() && !client.isWidgetSelected() && !(client.isDraggingWidget() && client.getDraggedWidget()!=null);}
    private void send(String name,Map<String,Object> extra)
    {
        Map<String,Object> data=new LinkedHashMap<>(extra);data.put("version",1);data.put("session",session);
        events.post(new PluginMessage(NAMESPACE,name,data));
    }
    public Snapshot snapshot() { return view; }
    public boolean editing() { return editing; }
    public void catalogActive(boolean value) {catalogActive=value;}
    public void editing(boolean value) { editing=value;if(!value){drag=null;invalidateInput();} }
    public void preview(Drag value) {drag=value;}
    void previewMember(AnchorMemberDrag value){memberDrag=value;if(value==null)memberPreview=null;}
    MemberPreview memberPreview(){return memberPreview;}
    void finishMemberDrag(AnchorMemberDrag value)
    {
        command(()-> {
            memberDrag=null;memberPreview=null;
            if(!editing || !inputAllowed() || value.epoch!=inputEpoch || value.revision!=store.revision() || store.error()!=null)return;
            request++;send("measure",Map.of("request",request));
            Map<String,AnchorGeometry.Target> targets=registry.targets(request);
            Snapshot current=capture(geometry.solve(layout,targets,new Dimension(client.getCanvasWidth(),client.getCanvasHeight())),targets);
            AnchorLayout next=store.snapshot();
            AnchorMemberDrag.Drop drop=value.plan(current,next);
            if(drop!=null) {
                store.replace(next);
                if(drop.anchor==null)releaseAtCurrent(value.target,drop.member.getLocation());
            }
        });
    }
    public void finishDrag(Drag value) {command(()->{drag=null;lastDrag=null;draft=null;if(value.canvas==null || value.canvas.equals(new Dimension(client.getCanvasWidth(),client.getCanvasHeight())))store.edit(next->applyDrag(next,value));});}
    private void applyDrag(AnchorLayout layout,Drag drag)
    {
        AnchorLayout.Anchor anchor=layout.find(drag.id);if(anchor==null || anchor.locked)return;
        if(drag.initial!=null) {
            if(drag.resize) {
                int minX=0,minY=0;
                if(anchor.arrangement==AnchorLayout.Arrangement.FREE)for(AnchorLayout.Member member:anchor.members) {
                    AnchorGeometry.Target target=view.targets.get(member.target);
                    if(target!=null && target.state==AnchorGeometry.State.HIDDEN && !member.reserveHidden)continue;
                    minX=Math.min(minX,member.x);minY=Math.min(minY,member.y);
                }
                anchor.width=Math.max(20,Math.min(16384,drag.initial.width+minX+drag.dx));
                anchor.height=Math.max(20,Math.min(16384,drag.initial.height+minY+drag.dy));
            }
            AnchorGeometry.Result resolved=geometry.solve(layout,view.targets,view.canvas);
            Rectangle bounds=resolved.anchors.get(anchor.id),reference=new Rectangle(view.canvas);
            if(bounds==null)return;
            if(!anchor.parent.isEmpty()) {
                AnchorGeometry.Target parent=view.targets.get(anchor.parent);if(parent==null)return;
                reference=resolved.targets.getOrDefault(anchor.parent,parent.bounds);
            }
            anchor.x=drag.initial.x+(drag.resize?0:drag.dx)-reference.x-(int)Math.round((reference.width-bounds.width)*anchor.reference.x);
            anchor.y=drag.initial.y+(drag.resize?0:drag.dy)-reference.y-(int)Math.round((reference.height-bounds.height)*anchor.reference.y);
        }
        else if(drag.resize) {
            int width=Math.max(20,Math.min(16384,anchor.width+drag.dx)),height=Math.max(20,Math.min(16384,anchor.height+drag.dy));
            anchor.x+=(int)Math.round((width-anchor.width)*anchor.reference.x);anchor.y+=(int)Math.round((height-anchor.height)*anchor.reference.y);
            anchor.width=width;anchor.height=height;
        } else {anchor.x+=drag.dx;anchor.y+=drag.dy;}
        anchor.x=Math.max(-32768,Math.min(32768,anchor.x));anchor.y=Math.max(-32768,Math.min(32768,anchor.y));
    }
    public void edit(Consumer<AnchorLayout> change) { command(() -> store.edit(change)); }
    public String exportLayout(){return gson.toJson(view.layout);}
    public void duplicate(String id)
    {
        edit(next->{
            AnchorLayout.Anchor source=next.find(id);if(source==null)return;
            AnchorLayout.Anchor copy=gson.fromJson(gson.toJson(source),AnchorLayout.Anchor.class);
            copy.id=UUID.randomUUID().toString();copy.name=source.name.substring(0,Math.min(53,source.name.length()))+" copy";copy.members.clear();
            copy.x=Math.min(32768,copy.x+12);copy.y=Math.min(32768,copy.y+12);next.anchors.add(copy);
        });
    }
    public void undo() { command(store::undo); }
    public void restoreBackup() { command(store::restoreBackup); }
    public void importLayout(String json) { command(() -> store.replace(store.decode(json))); }
    public void command(Runnable command)
    {
        String expected=view.session;
        long expectedProfile=profileEpoch;
        thread.invokeLater(() -> {
            if(!active || !session.equals(expected) || expectedProfile!=profileEpoch)return;
            try { command.run();message=""; } catch(IllegalArgumentException|IllegalStateException invalid) { message=invalid.getMessage(); }
            layout=store.snapshot();revision=store.revision();publish(view.geometry,view.targets);
        });
    }
    public void detach(String id)
    {detach(List.of(id));}
    public void detach(List<String> ids)
    {
        List<String> selected=List.copyOf(ids);
        command(() -> {
            selected.forEach(this::releaseAtCurrent);
            store.edit(next -> next.anchors.forEach(anchor -> anchor.members.removeIf(member -> selected.contains(member.target))));
        });
    }
    public void reorderMembers(String id,List<String> ids,int step)
    {
        java.util.Set<String> selected=new java.util.HashSet<>(ids);
        edit(next->{
            AnchorLayout.Anchor anchor=next.find(id);if(anchor==null || step==0)return;
            List<AnchorLayout.Member> members=anchor.members;
            for(int i=step<0?1:members.size()-2;step<0?i<members.size():i>=0;i+=step<0?1:-1) {
                int neighbor=i+(step<0?-1:1);
                if(selected.contains(members.get(i).target) && !selected.contains(members.get(neighbor).target))Collections.swap(members,i,neighbor);
            }
        });
    }
    public void delete(String id)
    {
        command(() -> {
            AnchorLayout.Anchor anchor=layout.find(id);
            if(anchor==null)return;
            anchor.members.forEach(member -> releaseAtCurrent(member.target));
            store.edit(next -> next.anchors.removeIf(value -> value.id.equals(id)));
        });
    }
    private void releaseAtCurrent(String id)
    {releaseAtCurrent(id,null);}
    private void releaseAtCurrent(String id,java.awt.Point location)
    {
        for(AnchorRegistry.Provider provider:registry.providers())
            if(owned.getOrDefault(provider.token,Collections.emptySet()).contains(id))
            {
                if(location!=null) {
                    request++;send("measure",Map.of("request",request));
                    send("apply",Map.of("provider",provider.token,"request",request,"placements",List.of(Map.of("id",id,"x",location.x,"y",location.y)),"release",Collections.emptyList()));
                }
                request++;send("measure",Map.of("request",request));
                send("apply",Map.of("provider",provider.token,"request",request,"placements",Collections.emptyList(),"release",List.of(id),"keepPosition",true));
                owned.get(provider.token).remove(id);
            }
    }
    public Map<String,List<String>> aliases()
    {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(AnchorRegistry.Provider provider:registry.providers())result.putAll(provider.aliases);
        return result;
    }
}
