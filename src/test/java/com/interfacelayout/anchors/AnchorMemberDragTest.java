package com.interfacelayout.anchors;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.interfacelayout.anchors.AnchorLayout.*;

public class AnchorMemberDragTest
{
    private final AnchorLayout layout=new AnchorLayout();
    private final Map<String,AnchorGeometry.Target> targets=new LinkedHashMap<>();
    private final Dimension canvas=new Dimension(1000,700);
    private Anchor add(String name,int x,int y,Arrangement arrangement) {Anchor a=new Anchor();a.name=name;a.x=x;a.y=y;a.width=200;a.height=120;a.padding=0;a.arrangement=arrangement;layout.anchors.add(a);return a;}
    private AnchorGeometry.Target target(String id,int x,int y,int w,int h) {var t=new AnchorGeometry.Target(id,id,new Rectangle(x,y,w,h),AnchorGeometry.State.VISIBLE,true,false,null,"provider");targets.put(id,t);return t;}
    private void member(Anchor anchor,String id) {Member m=new Member();m.target=id;anchor.members.add(m);}
    private AnchorLayout copy(){return new Gson().fromJson(new Gson().toJson(layout),AnchorLayout.class);}
    private AnchorCoordinator.Snapshot view(List<Rectangle> windows) {return new AnchorCoordinator.Snapshot("session",layout,new AnchorGeometry().solve(layout,targets,canvas),targets,"",false,4,canvas,true,windows,2);}
    @Test public void transferPreservesHiddenPolicyAndFreeGrabOffset()
    {
        Anchor a=add("A",50,60,Arrangement.FREE),b=add("B",400,300,Arrangement.FREE);var t=target("prayer",50,60,34,34);member(a,t.id);a.members.get(0).reserveHidden=false;
        var view=view(List.of());var drag=new AnchorMemberDrag(view,t,new Point(55,65)).at(new Point(455,355));AnchorLayout next=copy();var drop=drag.plan(view,next);
        assertNotNull(drop);assertEquals(new Rectangle(450,350,34,34),drop.member);assertTrue(next.find(a.id).members.isEmpty());
        assertEquals(1,next.find(b.id).members.size());assertFalse(next.find(b.id).members.get(0).reserveHidden);assertEquals(1,a.members.size());
    }
    @Test public void rowAndColumnDropsInsertAtPointerWithoutMovingExistingPreviewMembers()
    {
        for(Arrangement mode:List.of(Arrangement.ROW,Arrangement.COLUMN)) {
            layout.anchors.clear();targets.clear();Anchor box=add("B",200,200,mode);box.gap=10;
            var moving=target("new",30,30,30,30);target("first",200,200,30,30);target("second",240,240,30,30);member(box,"first");member(box,"second");
            var view=view(List.of());Point pointer=mode==Arrangement.ROW?new Point(238,210):new Point(210,238);
            var drag=new AnchorMemberDrag(view,moving,new Point(35,35)).at(pointer);AnchorLayout next=copy();assertNotNull(drag.plan(view,next));
            assertEquals(List.of("first","new","second"),next.find(box.id).members.stream().map(m->m.target).collect(java.util.stream.Collectors.toList()));
            assertEquals(2,box.members.size());
        }
    }
    @Test public void reorderWithinWrapDoesNotDuplicateMembership()
    {
        Anchor box=add("B",200,200,Arrangement.WRAP);box.width=70;box.gap=5;
        var moving=target("moving",200,235,30,30);target("a",200,200,30,30);target("b",235,200,30,30);member(box,"a");member(box,"b");member(box,"moving");
        var view=view(List.of());var drag=new AnchorMemberDrag(view,moving,new Point(205,240)).at(new Point(202,202));AnchorLayout next=copy();var drop=drag.plan(view,next);
        assertNotNull(drop);assertEquals("moving",next.find(box.id).members.get(0).target);assertEquals(3,next.find(box.id).members.size());assertEquals(new Point(200,200),drop.member.getLocation());
    }
    @Test public void lockedDisabledOutsideAndCyclicDestinationsDoNotAcceptDrops()
    {
        Anchor box=add("B",200,200,Arrangement.FREE);var t=target("prayer",30,30,30,30);var view=view(List.of());var drag=new AnchorMemberDrag(view,t,new Point(35,35)).at(new Point(220,220));
        box.locked=true;assertNull(drag.plan(view(List.of()),copy()));box.locked=false;box.enabled=false;assertNull(drag.plan(view(List.of()),copy()));box.enabled=true;
        assertNull(drag.at(new Point(900,650)).plan(view(List.of()),copy()));
        box.parent=t.id;box.x=200;box.y=200;var cyclic=view(List.of());assertNull(drag.at(new Point(240,240)).plan(cyclic,copy()));
    }
    @Test public void pickingUsesVisibleControlBoundsAndRejectsBankCoveredControls()
    {
        target("layout.minimap",200,200,150,150);var compass=target("layout.compass",200,200,34,34);
        assertEquals(compass,AnchorMemberDrag.pick(view(List.of()),new Point(210,210)));
        Rectangle bank=new Rectangle(100,100,600,500);assertNull(AnchorMemberDrag.pick(view(List.of(bank)),new Point(210,210)));
        var window=target("banking.window",100,100,600,500);assertEquals(window,AnchorMemberDrag.pick(view(List.of(bank)),new Point(210,210)));
    }
    @Test public void changedGenerationSizeRevisionAndCanvasRejectOldGestures()
    {
        add("B",200,200,Arrangement.FREE);var t=target("prayer",30,30,30,30);var view=view(List.of());var drag=new AnchorMemberDrag(view,t,new Point(35,35)).at(new Point(220,220));
        targets.put(t.id,new AnchorGeometry.Target(t.id,t.name,t.bounds,t.state,true,false,null,"replacement"));assertFalse(drag.valid(view(List.of())));
        target(t.id,30,30,34,34);assertFalse(drag.valid(view(List.of())));
        targets.put(t.id,t);canvas.width=900;assertFalse(drag.valid(view(List.of())));
        assertFalse(drag.valid(new AnchorCoordinator.Snapshot("session",layout,view.geometry,targets,"",false,5,new Dimension(1000,700),true,List.of(),2)));
    }
}
