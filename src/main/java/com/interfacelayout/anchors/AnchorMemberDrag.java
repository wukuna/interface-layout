package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.interfacelayout.anchors.AnchorLayout.*;

final class AnchorMemberDrag
{
    final String session,target,generation;
    final long revision,epoch;
    final Dimension canvas,size;
    final Point pressed,offset,pointer;
    final double scaleX,scaleY;

    AnchorMemberDrag(AnchorCoordinator.Snapshot view,AnchorGeometry.Target control,Point point)
    {
        session=view.session;revision=view.revision;epoch=view.inputEpoch;canvas=new Dimension(view.canvas);
        target=control.id;generation=control.generation;
        scaleX=view.mouseScaleX;scaleY=view.mouseScaleY;
        Rectangle bounds=bounds(view,control);size=bounds.getSize();pressed=new Point(point);pointer=new Point(point);
        offset=new Point(point.x-bounds.x,point.y-bounds.y);
    }
    private AnchorMemberDrag(AnchorMemberDrag original,Point point)
    {
        session=original.session;revision=original.revision;epoch=original.epoch;canvas=original.canvas;size=original.size;
        target=original.target;generation=original.generation;pressed=original.pressed;offset=original.offset;pointer=new Point(point);
        scaleX=original.scaleX;scaleY=original.scaleY;
    }
    AnchorMemberDrag at(Point point){return new AnchorMemberDrag(this,point);}
    boolean moved(){return pressed.distanceSq(pointer)>=16;}
    Rectangle ghost(){return new Rectangle(pointer.x-offset.x,pointer.y-offset.y,size.width,size.height);}
    boolean valid(AnchorCoordinator.Snapshot view)
    {
        AnchorGeometry.Target control=view.targets.get(target);
        Anchor owner=owner(view.layout,target);
        return view.inputAllowed && session.equals(view.session) && revision==view.revision && epoch==view.inputEpoch && canvas.equals(view.canvas)
            && Double.compare(scaleX,view.mouseScaleX)==0 && Double.compare(scaleY,view.mouseScaleY)==0
            && control!=null && control.movable && control.state==AnchorGeometry.State.VISIBLE && generation.equals(control.generation)
            && size.equals(control.bounds.getSize()) && (owner==null || owner.enabled && !owner.locked);
    }
    static Anchor owner(AnchorLayout layout,String target)
    {
        for(Anchor anchor:layout.anchors)for(Member member:anchor.members)if(member.target.equals(target))return anchor;
        return null;
    }
    static Rectangle bounds(AnchorCoordinator.Snapshot view,AnchorGeometry.Target target)
    {return view.geometry.targets.getOrDefault(target.id,target.bounds);}
    static AnchorGeometry.Target pick(AnchorCoordinator.Snapshot view,Point point)
    {
        if(!view.inputAllowed)return null;
        AnchorGeometry.Target picked=null;long area=Long.MAX_VALUE;
        for(AnchorGeometry.Target control:view.targets.values()) {
            if(!control.movable || control.state!=AnchorGeometry.State.VISIBLE)continue;
            Anchor owner=owner(view.layout,control.id);if(owner!=null && (!owner.enabled || owner.locked))continue;
            Rectangle bounds=bounds(view,control);
            if(!bounds.contains(point))continue;
            if(!control.id.equals("banking.window") && view.windows.stream().anyMatch(window->window.contains(point)))continue;
            long next=(long)bounds.width*bounds.height;
            if(next<=area){picked=control;area=next;}
        }
        return picked;
    }
    Drop plan(AnchorCoordinator.Snapshot view,AnchorLayout next)
    {
        if(!moved() || !valid(view))return null;
        List<Anchor> anchors=new ArrayList<>(view.layout.anchors);Collections.reverse(anchors);
        for(Anchor anchor:anchors) {
            Rectangle box=view.geometry.anchors.get(anchor.id);
            if(!anchor.enabled || box==null || !box.contains(pointer))continue;
            if(anchor.locked)return null;
            Member member=null;
            for(Anchor source:next.anchors)for(java.util.Iterator<Member> iterator=source.members.iterator();iterator.hasNext();) {
                Member candidate=iterator.next();if(candidate.target.equals(target)){member=candidate;iterator.remove();}
            }
            if(member==null){member=new Member();member.target=target;}
            member.width=size.width;member.height=size.height;
            member.x=Math.max(0,pointer.x-offset.x-box.x-anchor.padding);member.y=Math.max(0,pointer.y-offset.y-box.y-anchor.padding);
            Anchor destination=next.find(anchor.id);int index=destination.members.size();
            if(anchor.arrangement!=Arrangement.FREE) {
                for(int i=0;i<destination.members.size();i++) {
                    Member neighbor=destination.members.get(i);Rectangle bounds=view.geometry.targets.get(neighbor.target);
                    if(bounds==null)continue;
                    boolean before=anchor.arrangement==Arrangement.ROW?pointer.x<bounds.getCenterX()
                        :anchor.arrangement==Arrangement.COLUMN?pointer.y<bounds.getCenterY()
                        :pointer.y<bounds.y || pointer.y<bounds.y+bounds.height && pointer.x<bounds.getCenterX();
                    if(before){index=i;break;}
                }
            }
            destination.members.add(index,member);
            try {
                next.validate();AnchorGeometry.Result placement=new AnchorGeometry().solve(next,view.targets,view.canvas);
                Rectangle resolved=placement.targets.get(target);
                return resolved==null?null:new Drop(anchor.id,anchor.name,resolved,placement.anchors.get(anchor.id));
            } catch(IllegalArgumentException invalid){return null;}
        }
        return null;
    }
    static final class Drop
    {
        final String anchor,name;
        final Rectangle member,bounds;
        Drop(String anchor,String name,Rectangle member,Rectangle bounds)
        {this.anchor=anchor;this.name=name;this.member=new Rectangle(member);this.bounds=new Rectangle(bounds);}
    }
}
