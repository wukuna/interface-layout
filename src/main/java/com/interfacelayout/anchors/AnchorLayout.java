package com.interfacelayout.anchors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AnchorLayout
{
    public int version = 1;
    public List<Anchor> anchors = new ArrayList<>();

    public enum Arrangement { FREE, ROW, COLUMN, WRAP }
    public enum Alignment { START, CENTER, END }
    public enum Reference
    {
        TOP_LEFT(0, 0), TOP(0.5, 0), TOP_RIGHT(1, 0),
        LEFT(0, 0.5), CENTER(0.5, 0.5), RIGHT(1, 0.5),
        BOTTOM_LEFT(0, 1), BOTTOM(0.5, 1), BOTTOM_RIGHT(1, 1);
        public final double x, y;
        Reference(double x, double y) { this.x = x; this.y = y; }
        @Override public String toString() { return name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT); }
    }

    public static final class Anchor
    {
        public String id = UUID.randomUUID().toString();
        public String name = "Anchor";
        public boolean enabled = true;
        public boolean locked;
        public String parent = "";
        public Reference reference = Reference.TOP_LEFT;
        public int x = 30, y = 30, width = 240, height = 100;
        public int padding = 4, gap = 4;
        public Arrangement arrangement = Arrangement.FREE;
        public Alignment alignment = Alignment.START;
        public boolean background;
        public boolean border;
        public int backgroundColor = 0x8023262b;
        public int borderColor = 0xff64788c;
        public List<Member> members = new ArrayList<>();
        @Override public String toString() { return name; }
    }

    public static final class Member
    {
        public String target = "";
        public int x, y, width = 32, height = 32;
        public boolean reserveHidden = true;
    }

    public Anchor find(String id)
    {
        for (Anchor anchor : anchors) if (anchor.id.equals(id)) return anchor;
        return null;
    }

    public void validate()
    {
        require(version == 1, "Unsupported anchor layout version");
        require(anchors != null && anchors.size() <= 32, "Use at most 32 anchors");
        Set<String> ids = new HashSet<>();
        Map<String, String> owners = new HashMap<>();
        int count = 0;
        for (Anchor anchor : anchors)
        {
            require(anchor != null && text(anchor.id, 80) && ids.add(anchor.id), "Invalid or duplicate anchor");
            require(text(anchor.name, 60), "Use an anchor name of 1–60 characters");
            require(anchor.parent != null && anchor.parent.length() <= 240, "Invalid parent");
            require(anchor.reference != null && anchor.arrangement != null && anchor.alignment != null, "Invalid arrangement");
            require(offset(anchor.x) && offset(anchor.y), "Anchor offset is too large");
            require(size(anchor.width) && size(anchor.height), "Anchor size must be 1–16384");
            require(anchor.padding >= 0 && anchor.padding <= 100 && anchor.gap >= 0 && anchor.gap <= 100, "Use spacing from 0 to 100");
            require(anchor.members != null && anchor.members.size() <= 256, "Invalid members");
            count += anchor.members.size();
            for (Member member : anchor.members)
            {
                require(member != null && text(member.target, 240), "Invalid member");
                require(owners.putIfAbsent(member.target, anchor.id) == null, "A control can belong to only one anchor");
                require(offset(member.x) && offset(member.y) && size(member.width) && size(member.height), "Invalid member bounds");
            }
        }
        require(count <= 256, "Use at most 256 members");
        for (Anchor anchor : anchors)
        {
            Set<String> chain = new HashSet<>();
            Anchor current = anchor;
            while (current != null)
            {
                require(chain.add(current.id), "Anchor parents cannot form a cycle");
                String parentAnchor = owners.get(current.parent);
                current = parentAnchor == null ? null : find(parentAnchor);
            }
        }
    }

    private static boolean text(String text, int limit) { return text != null && !text.trim().isEmpty() && text.length() <= limit && text.chars().noneMatch(Character::isISOControl); }
    private static boolean offset(int value) { return value >= -32768 && value <= 32768; }
    private static boolean size(int value) { return value > 0 && value <= 16384; }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
