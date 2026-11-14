package com.interfacelayout.anchors;

import com.google.gson.*;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import static com.interfacelayout.anchors.AnchorLayout.*;

@Singleton
public final class AnchorTransfers
{
    @Inject private ConfigManager configs;
    @Inject private AnchorCoordinator coordinator;
    @Inject private ClientThread thread;
    @Inject private Client client;
    @Inject private Gson gson;
    public static final class Preview
    {
        public final AnchorLayout layout=new AnchorLayout();
        public final List<String> warnings=new ArrayList<>();
        public int mapped;
        public String description() {
            return layout.anchors.size()+" anchors; "+mapped+" recognized controls.\n"+
                (warnings.isEmpty()?"":String.join("\n",warnings)+"\n")+
                "Replaces this profile's anchors; Undo keeps the previous layout.\nCustom UI Anchors settings remain unchanged.";
        }
    }
    public void preview(Component parent)
    {
        String session=coordinator.snapshot().session;
        thread.invokeLater(()-> {
            if(!coordinator.snapshot().session.equals(session))return;
            try {
                Map<String,String> sources=new LinkedHashMap<>();
                String current=configs.getConfiguration("anchorcustomizer","regionJson");
                sources.put("Current saved layout",current==null?"[]":current);
                JsonElement profiles=parse(configs.getConfiguration("anchorcustomizer","regionProfiles"),"{}");
                if(profiles.isJsonObject())for(Map.Entry<String,JsonElement> entry:profiles.getAsJsonObject().entrySet())if(sources.size()<32 && entry.getValue().isJsonArray())sources.put(entry.getKey(),gson.toJson(entry.getValue()));
                String assignments=configs.getConfiguration("anchorcustomizer","overlayAssignments"),order=configs.getConfiguration("anchorcustomizer","overlayOrder");
                int spacing=2;try {String value=configs.getConfiguration("anchorcustomizer","stackSpacing");if(value!=null)spacing=Math.max(0,Math.min(100,Integer.parseInt(value)));}catch(NumberFormatException ignored){}
                final int gap=spacing;Map<String,List<String>> aliases=coordinator.aliases();Dimension canvas=new Dimension(client.getCanvasWidth(),client.getCanvasHeight());
                SwingUtilities.invokeLater(()-> {
                    String choice=(String)JOptionPane.showInputDialog(parent,"Choose the saved resolution to import.","Import Custom UI Anchors",JOptionPane.PLAIN_MESSAGE,null,sources.keySet().toArray(),"Current saved layout");
                    if(choice==null)return;
                    try {
                        Preview preview=convert(sources.get(choice),assignments,order,aliases,canvas,gap);
                        JTextArea text=new JTextArea(preview.description(),12,40);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
                        if(JOptionPane.showConfirmDialog(parent,new JScrollPane(text),"Review import",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION && coordinator.snapshot().session.equals(session))coordinator.importLayout(gson.toJson(preview.layout));
                    } catch(RuntimeException invalid) {JOptionPane.showMessageDialog(parent,invalid.getMessage(),"Cannot import anchors",JOptionPane.ERROR_MESSAGE);}
                });
            } catch(RuntimeException invalid) {SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(parent,invalid.getMessage(),"Cannot read saved anchors",JOptionPane.ERROR_MESSAGE));}
        });
    }
    static JsonElement parse(String json,String fallback)
    {
        String value=json==null||json.isBlank()?fallback:json;
        if(value.length()>262144)throw new IllegalArgumentException("Saved anchor data is too large");
        int depth=0;boolean quote=false,escape=false;
        for(char c:value.toCharArray()) {
            if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quote=false;}
            else if(c=='\"')quote=true;else if(c=='{'||c=='['){if(++depth>16)throw new IllegalArgumentException("Saved anchors are too deeply nested");}else if(c=='}'||c==']')depth--;
        }
        return new JsonParser().parse(value);
    }
    public static Preview convert(String regions,String assignments,String order,Map<String,List<String>> aliases,Dimension canvas,int gap)
    {
        if(canvas.width<=0 || canvas.height<=0)throw new IllegalArgumentException("Open the game canvas before importing");
        Preview result=new Preview();JsonElement rows=parse(regions,"[]"),links=parse(assignments,"{}"),ordering=parse(order,"{}");
        if(!rows.isJsonArray() || !links.isJsonObject() || rows.getAsJsonArray().size()>32 || links.getAsJsonObject().size()>256)throw new IllegalArgumentException("Unsupported saved anchor data");
        Map<Integer,Anchor> boxes=new LinkedHashMap<>();Map<String,String> names=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        aliases.forEach((id,values)->{names.put(id,id);for(String name:values){String prior=names.putIfAbsent(name,id);if(prior!=null&&!prior.equals(id))ambiguous.add(name);}});
        for(JsonElement value:rows.getAsJsonArray()) {
            if(!value.isJsonObject())throw new IllegalArgumentException("Invalid saved anchor");JsonObject row=value.getAsJsonObject();
            int id=integer(row,"id",-1);if(id<0 || boxes.containsKey(id))throw new IllegalArgumentException("Duplicate or missing saved box ID");
            Anchor anchor=new Anchor();anchor.name=text(row,"name","Box "+id);anchor.width=integer(row,"width",240);anchor.height=integer(row,"height",100);
            anchor.locked=flag(row,"locked");anchor.enabled=!flag(row,"disabled");anchor.padding=0;anchor.gap=gap;
            String constraint=text(row,"constraint","TOP_LEFT");
            try {anchor.reference=Reference.valueOf(constraint.replace("TOP_CENTER","TOP").replace("BOTTOM_CENTER","BOTTOM").replace("CENTER_LEFT","LEFT").replace("CENTER_RIGHT","RIGHT"));}
            catch(IllegalArgumentException ignored){result.warnings.add(anchor.name+": unsupported constraint; kept canvas position.");}
            int oldWidth=integer(row,"originW",canvas.width),oldHeight=integer(row,"originH",canvas.height);
            boolean origin=oldWidth>0&&oldHeight>0;
            anchor.x=integer(row,origin?"originX":"x",integer(row,"x",0))-(int)Math.round(((origin?oldWidth:canvas.width)-anchor.width)*anchor.reference.x);
            anchor.y=integer(row,origin?"originY":"y",integer(row,"y",0))-(int)Math.round(((origin?oldHeight:canvas.height)-anchor.height)*anchor.reference.y);
            String stacking=text(row,"stacking","VERTICAL");
            anchor.arrangement=stacking.equals("HORIZONTAL")?Arrangement.ROW:stacking.equals("VERTICAL")?Arrangement.COLUMN:stacking.startsWith("FILL")?Arrangement.WRAP:Arrangement.FREE;
            String align=text(row,"alignment","CENTER");anchor.alignment=align.contains("LEFT")||align.contains("TOP")?Alignment.START:align.contains("RIGHT")||align.contains("BOTTOM")?Alignment.END:Alignment.CENTER;
            if(flag(row,"pinToChat"))result.warnings.add(anchor.name+": chat pin needs a supported parent selected after import.");
            if("REVERSE".equals(text(row,"fillDirection","")) || "REVERSE".equals(text(row,"wrapDirection","")))result.warnings.add(anchor.name+": reverse wrapping needs its member order reviewed.");
            boxes.put(id,anchor);result.layout.anchors.add(anchor);
        }
        List<Map.Entry<String,JsonElement>> entries=new ArrayList<>(links.getAsJsonObject().entrySet());
        if(ordering.isJsonObject())entries.sort(java.util.Comparator.comparingInt(entry->{try{return ordering.getAsJsonObject().get(entry.getKey()).getAsInt();}catch(RuntimeException ignored){return Integer.MAX_VALUE;}}));
        Set<String> imported=new HashSet<>();
        for(Map.Entry<String,JsonElement> entry:entries) {
            String name=entry.getKey(),id=names.get(name);Anchor anchor=boxes.get(entry.getValue().getAsInt());
            if(anchor==null){result.warnings.add("Missing box: "+name);continue;}
            if(id==null || ambiguous.contains(name)){result.warnings.add("Unmapped: "+name);continue;}
            if(!imported.add(id)){result.warnings.add("Duplicate mode alias skipped: "+name);continue;}
            Member member=new Member();member.target=id;anchor.members.add(member);result.mapped++;
        }
        result.layout.validate();return result;
    }
    private static int integer(JsonObject object,String key,int fallback) {return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsInt():fallback;}
    private static String text(JsonObject object,String key,String fallback) {return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():fallback;}
    private static boolean flag(JsonObject object,String key) {return object.has(key)&&!object.get(key).isJsonNull()&&object.get(key).getAsBoolean();}
}
