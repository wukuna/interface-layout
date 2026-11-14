package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.interfacelayout.anchors.AnchorLayout.*;
import static com.interfacelayout.anchors.AnchorGeometry.*;

public class AnchorGeometryTest
{
    private final AnchorGeometry geometry = new AnchorGeometry();
    private final Map<String, Target> targets = new LinkedHashMap<>();
    private final AnchorLayout layout = new AnchorLayout();
    private final Anchor anchor = new Anchor();

    public AnchorGeometryTest()
    {
        anchor.x = -20; anchor.y = -30; anchor.reference = Reference.BOTTOM_RIGHT;
        anchor.width = 200; anchor.height = 100; anchor.padding = 0; anchor.gap = 5;
        layout.anchors.add(anchor);
        add(anchor, "layout.orb.prayer", 33, 33, State.VISIBLE);
    }

    private Member add(Anchor destination, String id, int width, int height, State state)
    {
        Member member = new Member(); member.target = id; member.width = width; member.height = height;
        destination.members.add(member);
        targets.put(id, new Target(id, id, new Rectangle(8, 9, width, height), state, true, false));
        return member;
    }

    @Test public void repeatedClampingNeverRewritesSavedCanvasIntent()
    {
        Dimension large = new Dimension(1400, 900), small = new Dimension(160, 90);
        Rectangle original = geometry.solve(layout, targets, large).targets.get("layout.orb.prayer");
        for (int i = 0; i < 100; i++)
        {
            assertEquals(new Rectangle(0, 0, 33, 33), geometry.solve(layout, targets, small).targets.get("layout.orb.prayer"));
            assertEquals(original, geometry.solve(layout, targets, large).targets.get("layout.orb.prayer"));
        }
        assertEquals(-20, anchor.x); assertEquals(-30, anchor.y);
        assertEquals(200, anchor.width); assertEquals(100, anchor.height);
    }

    @Test public void coveredAndMissingMembersKeepSlotsButExplicitlyHiddenCanCollapse()
    {
        anchor.arrangement = Arrangement.ROW;
        Member hidden = add(anchor, "layout.orb.hp", 40, 40, State.HIDDEN);
        add(anchor, "layout.orb.run", 33, 33, State.VISIBLE);
        Dimension canvas = new Dimension(800, 600);
        int full = geometry.solve(layout, targets, canvas).targets.get("layout.orb.run").x;
        hidden.reserveHidden = false;
        assertEquals(full - 45, geometry.solve(layout, targets, canvas).targets.get("layout.orb.run").x);
        targets.put(hidden.target, new Target(hidden.target, "", new Rectangle(0, 0, 40, 40), State.OCCLUDED, true, false));
        assertEquals(full, geometry.solve(layout, targets, canvas).targets.get("layout.orb.run").x);
        targets.remove(hidden.target);
        assertEquals(full, geometry.solve(layout, targets, canvas).targets.get("layout.orb.run").x);
        assertEquals(3, anchor.members.size());
    }

    @Test public void nativeModeSuspendsMovementWithoutLosingMembership()
    {
        String id = anchor.members.get(0).target;
        targets.put(id, new Target(id, "", new Rectangle(33, 33), State.UNAVAILABLE, false, false));
        assertTrue(geometry.solve(layout, targets, new Dimension(800, 600)).targets.isEmpty());
        assertEquals(id, anchor.members.get(0).target);
        targets.put(id, new Target(id, "", new Rectangle(33, 33), State.VISIBLE, true, false));
        assertTrue(geometry.solve(layout, targets, new Dimension(800, 600)).targets.containsKey(id));
    }

    @Test public void wrapKeepsStableOrderAndStartsEachRowAtTheSameInset()
    {
        anchor.reference = Reference.TOP_LEFT; anchor.x = 0; anchor.y = 0;
        anchor.width = 75; anchor.height = 1; anchor.padding = 2; anchor.arrangement = Arrangement.WRAP;
        add(anchor, "layout.orb.run", 33, 33, State.VISIBLE);
        add(anchor, "layout.orb.special", 33, 33, State.VISIBLE);
        Result result = geometry.solve(layout, targets, new Dimension(800, 600));
        assertEquals(new Rectangle(2, 2, 33, 33), result.targets.get("layout.orb.prayer"));
        assertEquals(new Rectangle(40, 2, 33, 33), result.targets.get("layout.orb.run"));
        assertEquals(new Rectangle(2, 40, 33, 33), result.targets.get("layout.orb.special"));
    }

    @Test public void parentsResolveIndependentlyOfStoredAnchorOrder()
    {
        Anchor child = new Anchor();
        child.parent = "layout.orb.prayer"; child.x = 5; child.y = 6;
        child.padding = 0; child.width = 20; child.height = 20;
        add(child, "chat.input-dock", 20, 20, State.VISIBLE);
        layout.anchors.add(0, child);
        Result result = geometry.solve(layout, targets, new Dimension(800, 600));
        Rectangle parent = result.targets.get("layout.orb.prayer");
        assertEquals(new Rectangle(parent.x + 5, parent.y + 6, 20, 20), result.targets.get("chat.input-dock"));
        targets.remove("layout.orb.prayer");
        assertFalse(geometry.solve(layout, targets, new Dimension(800, 600)).targets.containsKey("chat.input-dock"));
    }

    @Test public void invalidCanvasDoesNotCreatePlacements()
    {
        assertTrue(geometry.solve(layout, targets, new Dimension(0, 0)).targets.isEmpty());
    }
    @Test public void negativeVisibleOffsetsRemainInsideTheExpandedAnchorWithoutSavedDrift()
    {
        anchor.reference=Reference.BOTTOM_RIGHT;anchor.x=-10;anchor.y=-10;anchor.members.get(0).x=-25;anchor.members.get(0).y=-7;
        Result result=geometry.solve(layout,targets,new Dimension(800,600));assertTrue(result.anchors.get(anchor.id).contains(result.targets.get("layout.orb.prayer")));
        assertEquals(-25,anchor.members.get(0).x);assertEquals(-7,anchor.members.get(0).y);
    }
    @Test public void parentConstraintsUseTheAcceptedNativeMovementArea()
    {
        anchor.reference=Reference.TOP_LEFT;anchor.x=0;anchor.y=0;anchor.padding=0;
        String parent=anchor.members.get(0).target;targets.put(parent,new Target(parent,"bank",new Rectangle(200,100),State.VISIBLE,true,false,new Rectangle(40,50,700,500)));
        Anchor child=new Anchor();child.parent=parent;child.x=10;child.y=15;child.padding=0;child.width=20;child.height=20;add(child,"chat.input-dock",20,20,State.VISIBLE);layout.anchors.add(child);
        Result result=geometry.solve(layout,targets,new Dimension(800,600));assertEquals(40,result.targets.get(parent).x);assertEquals(50,result.targets.get(parent).y);assertEquals(new Rectangle(50,65,20,20),result.targets.get("chat.input-dock"));
    }

    @Test(expected = IllegalArgumentException.class) public void cyclicParentsAreRejected()
    {
        anchor.parent = "layout.orb.prayer"; layout.validate();
    }

    @Test(expected = IllegalArgumentException.class) public void duplicateMembershipIsRejected()
    {
        add(anchor, "layout.orb.prayer", 33, 33, State.VISIBLE); layout.validate();
    }
}
