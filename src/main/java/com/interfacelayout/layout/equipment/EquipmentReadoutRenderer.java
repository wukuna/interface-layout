package com.interfacelayout.layout.equipment;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Compact readout for the equipment sidebar card. */
public final class EquipmentReadoutRenderer
{
    public static final int HEIGHT=63;
    public static final class Hint {
        final Rectangle bounds;final String text;
        Hint(Rectangle bounds,String text) {this.bounds=bounds;this.text=text;}
    }
    public static List<Hint> draw(Graphics2D graphics,Rectangle bounds,EquipmentReadout data,boolean reducedTooltips) {
        List<Hint> hints=new ArrayList<>();Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.clip(bounds);g.setColor(new Color(25,23,19,235));g.fillRoundRect(bounds.x,bounds.y,bounds.width,bounds.height,5,5);
            g.setColor(new Color(104,90,65));g.drawRoundRect(bounds.x,bounds.y,bounds.width-1,bounds.height-1,5,5);
            g.setFont(new Font("Arial",Font.PLAIN,9));
            String[] labels={"Stab","Slash","Crush","Magic","Range"};
            int step=10;
            int labelWidth=12,cell=(bounds.width-labelWidth-4)/5;
            g.setColor(new Color(165,155,135));g.drawString("A",bounds.x+3,bounds.y+2*step);g.drawString("D",bounds.x+3,bounds.y+3*step);
            for(int i=0;i<5;i++) {
                int x=bounds.x+labelWidth+i*cell;
                g.setColor(new Color(210,185,125));center(g,labels[i],x,bounds.y+step,cell);
                g.setColor(new Color(255,229,115));center(g,data.value(i),x,bounds.y+2*step,cell);
                g.setColor(new Color(208,212,220));center(g,data.value(i+5),x,bounds.y+3*step,cell);
                hints.add(new Hint(new Rectangle(x,bounds.y,cell,3*step+2),labels[i]+" attack: "+data.value(i)+"<br>"+labels[i]+" defence: "+data.value(i+5)+(reducedTooltips?"":"<br>"+source(data))));
            }
            String[] other={"S "+data.value(10),"R "+data.value(11),"M "+data.value(12),"P "+data.value(13),data.weight+"kg"};
            String[] names={"Melee strength","Ranged strength","Magic damage","Prayer bonus","Total carried weight, including inventory (client value)"};
            for(int i=0;i<5;i++) {
                int x=bounds.x+i*bounds.width/5;g.setColor(new Color(230,205,145));center(g,other[i],x,bounds.y+4*step+1,bounds.width/5);
                String name=i==4 && reducedTooltips?"Carried weight":names[i];
                hints.add(new Hint(new Rectangle(x,bounds.y+3*step+2,bounds.width/5,step+1),name+": "+other[i].replaceFirst("^[SRMP] ","")+(i==4 || reducedTooltips?"":"<br>"+source(data))));
            }
            int bookX=bounds.x+88,bookWidth=bounds.width-92;
            g.setColor(new Color(160,203,213));center(g,data.spellbook,bookX,bounds.y+57,bookWidth);
            hints.add(new Hint(new Rectangle(bookX,bounds.y+4*step+4,bookWidth,18),"Current spellbook: "+data.spellbook));
        } finally {g.dispose();}
        return hints;
    }
    private static String source(EquipmentReadout data) {
        return data.complete?
            "Base item bonuses. Conditional weapon, set and target effects may differ; open equipment stats for the game's effective values.":
            "Equipment data is incomplete. Unknown totals are not shown as zero.";
    }
    private static void center(Graphics2D g,String text,int x,int baseline,int width) {
        g.drawString(text,x+Math.max(0,(width-g.getFontMetrics().stringWidth(text))/2),baseline);
    }
}
