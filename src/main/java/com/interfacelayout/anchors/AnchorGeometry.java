package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.interfacelayout.anchors.AnchorLayout.*;

public final class AnchorGeometry
{
    public enum State { VISIBLE, OCCLUDED, HIDDEN, UNAVAILABLE, DISABLED }

    public static final class Target
    {
        public final String id, name, generation;
        public final Rectangle bounds;
        public final Rectangle limits;
        public final State state;
        public final boolean movable, resizable;
        public Target(String id, String name, Rectangle bounds, State state, boolean movable, boolean resizable)
        {this(id,name,bounds,state,movable,resizable,null);}
        public Target(String id, String name, Rectangle bounds, State state, boolean movable, boolean resizable,Rectangle limits)
        {this(id,name,bounds,state,movable,resizable,limits,"");}
        public Target(String id, String name, Rectangle bounds, State state, boolean movable, boolean resizable,Rectangle limits,String generation)
        {
            this.generation=generation;
            this.id = id; this.name = name; this.bounds = new Rectangle(bounds); this.state = state;
            this.movable = movable; this.resizable = resizable;
            this.limits=limits==null?null:new Rectangle(limits);
        }
        @Override public String toString() { return name; }
    }

    public static final class Result
    {
        public final Map<String, Rectangle> anchors = new LinkedHashMap<>();
        public final Map<String, Rectangle> targets = new LinkedHashMap<>();
        public final List<String> overflow = new ArrayList<>();
    }

    private static final class Cell
    {
        final Member member;
        final Dimension size;
        final Target target;
        final Point position = new Point();
        Cell(Member member, Target target)
        {
            this.member = member; this.target = target;
            this.size = target == null || target.bounds.isEmpty() ? new Dimension(member.width, member.height) : target.bounds.getSize();
        }
    }

    public Result solve(AnchorLayout layout, Map<String, Target> targets, Dimension canvas)
    {
        layout.validate();
        Result result = new Result();
        if (canvas.width <= 0 || canvas.height <= 0) return result;
        Map<String, Anchor> ownership = new LinkedHashMap<>();
        for (Anchor anchor : layout.anchors) for (Member member : anchor.members) ownership.put(member.target, anchor);
        List<Anchor> pending = new ArrayList<>(layout.anchors);
        for (int pass = 0; pass < layout.anchors.size(); pass++)
        {
            boolean progress = false;
            for (java.util.Iterator<Anchor> iterator = pending.iterator(); iterator.hasNext();)
            {
                Anchor anchor = iterator.next();
                if (!anchor.enabled) { iterator.remove(); continue; }
                Rectangle reference = new Rectangle(canvas);
                if (!anchor.parent.isEmpty())
                {
                    Anchor owner = ownership.get(anchor.parent);
                    Target parent = targets.get(anchor.parent);
                    if (owner != null && !result.targets.containsKey(anchor.parent)) continue;
                    if (parent == null || parent.state != State.VISIBLE && parent.state != State.OCCLUDED) continue;
                    reference = result.targets.getOrDefault(anchor.parent, parent.bounds);
                }
                place(anchor, targets, reference, canvas, result);
                iterator.remove(); progress = true;
            }
            if (!progress) break;
        }
        return result;
    }

    private void place(Anchor anchor, Map<String, Target> targets, Rectangle reference, Dimension canvas, Result result)
    {
        List<Cell> cells = new ArrayList<>();
        for (Member member : anchor.members)
        {
            Target target = targets.get(member.target);
            if (target != null && target.state == State.HIDDEN && !member.reserveHidden) continue;
            cells.add(new Cell(member, target));
        }
        int width = Math.max(anchor.width, anchor.padding * 2 + 1);
        int height = Math.max(anchor.height, anchor.padding * 2 + 1);
        int x = anchor.padding, y = anchor.padding, rowHeight = 0;
        int maxX = width, maxY = height;
        for (Cell cell : cells)
        {
            if (anchor.arrangement == Arrangement.FREE)
            {
                cell.position.setLocation(anchor.padding + cell.member.x, anchor.padding + cell.member.y);
            }
            else
            {
                if (anchor.arrangement == Arrangement.WRAP && x > anchor.padding && x + cell.size.width + anchor.padding > width)
                { x = anchor.padding; y += rowHeight + anchor.gap; rowHeight = 0; }
                cell.position.setLocation(x, y);
                if (anchor.arrangement == Arrangement.COLUMN) y += cell.size.height + anchor.gap;
                else x += cell.size.width + anchor.gap;
                rowHeight = Math.max(rowHeight, cell.size.height);
            }
            maxX = Math.max(maxX, cell.position.x + cell.size.width + anchor.padding);
            maxY = Math.max(maxY, cell.position.y + cell.size.height + anchor.padding);
        }
        int minX=0,minY=0;
        for(Cell cell:cells) {minX=Math.min(minX,cell.position.x-anchor.padding);minY=Math.min(minY,cell.position.y-anchor.padding);}
        width = maxX-minX; height = maxY-minY;
        if(minX!=0 || minY!=0)for(Cell cell:cells)cell.position.translate(-minX,-minY);
        if(anchor.arrangement==Arrangement.WRAP) {
            Map<Integer,Integer> rows=new LinkedHashMap<>();
            for(Cell cell:cells)rows.merge(cell.position.y,cell.position.x+cell.size.width,Math::max);
            for(Cell cell:cells)cell.position.x+=align(width-anchor.padding-rows.get(cell.position.y),anchor.alignment);
        }
        for (Cell cell : cells)
        {
            if (anchor.arrangement == Arrangement.ROW)
                cell.position.y = anchor.padding + align(height - 2 * anchor.padding - cell.size.height, anchor.alignment);
            if (anchor.arrangement == Arrangement.COLUMN)
                cell.position.x = anchor.padding + align(width - 2 * anchor.padding - cell.size.width, anchor.alignment);
        }
        int originX = reference.x + (int) Math.round((reference.width - width) * anchor.reference.x) + anchor.x;
        int originY = reference.y + (int) Math.round((reference.height - height) * anchor.reference.y) + anchor.y;
        originX = Math.max(0, Math.min(Math.max(0, canvas.width - width), originX));
        originY = Math.max(0, Math.min(Math.max(0, canvas.height - height), originY));
        Rectangle bounds = new Rectangle(originX, originY, width, height);
        result.anchors.put(anchor.id, bounds);
        for (Cell cell : cells)
        {
            Rectangle target = new Rectangle(originX + cell.position.x, originY + cell.position.y, cell.size.width, cell.size.height);
            if(cell.target!=null && cell.target.limits!=null) {
                Rectangle limit=cell.target.limits;
                target.x=Math.max(limit.x,Math.min(limit.x+Math.max(0,limit.width-target.width),target.x));
                target.y=Math.max(limit.y,Math.min(limit.y+Math.max(0,limit.height-target.height),target.y));
            }
            if (!new Rectangle(canvas).contains(target)) result.overflow.add(cell.member.target);
            if (cell.target != null && cell.target.movable
                && (cell.target.state == State.VISIBLE || cell.target.state == State.OCCLUDED))
                result.targets.put(cell.member.target, target);
        }
    }

    private static int align(int space, Alignment alignment)
    { return alignment == Alignment.CENTER ? space / 2 : alignment == Alignment.END ? space : 0; }
}
