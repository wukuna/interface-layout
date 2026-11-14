package com.interfacelayout.anchors;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.interfacelayout.anchors.AnchorParticipant.*;

public final class AnchorRegistry
{
    public static final class Provider
    {
        public final String token, name;
        public final Map<String, AnchorGeometry.Target> targets = new LinkedHashMap<>();
        public final Map<String, List<String>> aliases = new LinkedHashMap<>();
        public long measured = -1;
        Provider(String token, String name) { this.token = token; this.name = name; }
    }
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    public boolean join(String token, String owner)
    {
        if (token.isEmpty() || owner.isEmpty() || providers.size() >= 32 && !providers.containsKey(token)) return false;
        providers.putIfAbsent(token, new Provider(token, owner)); return true;
    }
    public void remove(String token) { providers.remove(token); }
    public void clear() { providers.clear(); }
    public List<Provider> providers() { return new ArrayList<>(providers.values()); }
    public void measure(String token, long request, Object rows)
    {
        Provider provider = providers.get(token);
        if (provider == null || request <= provider.measured || !(rows instanceof List) || ((List<?>) rows).size() > 256) return;
        Map<String, AnchorGeometry.Target> next = new LinkedHashMap<>();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        for (Object row : (List<?>) rows)
        {
            if (!(row instanceof Map)) return;
            Map<?, ?> value = (Map<?, ?>) row;
            String id = string(value.get("id")), name = string(value.get("name"));
            int x = integer(value.get("x")), y = integer(value.get("y"));
            int width = integer(value.get("width")), height = integer(value.get("height"));
            if (id.isEmpty() || next.containsKey(id) || Math.abs((long) x) > 1000000 || Math.abs((long) y) > 1000000 || width < 0 || height < 0 || width > 16384 || height > 16384) return;
            AnchorGeometry.State state;
            try { state = AnchorGeometry.State.valueOf(string(value.get("state"))); } catch (IllegalArgumentException invalid) { return; }
            Rectangle limits=null;
            if(value.get("limits") instanceof Map) {
                Map<?,?> box=(Map<?,?>)value.get("limits");int lx=integer(box.get("x")),ly=integer(box.get("y")),lw=integer(box.get("width")),lh=integer(box.get("height"));
                if(Math.abs((long)lx)>1000000 || Math.abs((long)ly)>1000000 || lw<=0 || lh<=0 || lw>16384 || lh>16384)return;
                limits=new Rectangle(lx,ly,lw,lh);
            }
            next.put(id, new AnchorGeometry.Target(id, name.isEmpty() ? id : name, new Rectangle(x,y,width,height), state,
                Boolean.TRUE.equals(value.get("movable")), Boolean.TRUE.equals(value.get("resizable")),limits,token));
            List<String> names = new ArrayList<>();
            Object old = value.get("aliases");
            if (old instanceof List && ((List<?>) old).size() <= 32)
                for (Object alias : (List<?>) old) { String text = string(alias); if (!text.isEmpty()) names.add(text); }
            aliases.put(id, names);
        }
        provider.targets.clear(); provider.targets.putAll(next);
        provider.aliases.clear(); provider.aliases.putAll(aliases); provider.measured = request;
    }
    public Map<String, AnchorGeometry.Target> targets(long request)
    {
        Map<String, AnchorGeometry.Target> result = new LinkedHashMap<>();
        java.util.Set<String> duplicate = new java.util.HashSet<>();
        for (Provider provider : providers.values()) if (provider.measured == request)
            provider.targets.forEach((id,target) -> { if (result.putIfAbsent(id,target) != null) duplicate.add(id); });
        duplicate.forEach(result::remove);
        return Collections.unmodifiableMap(result);
    }
}
