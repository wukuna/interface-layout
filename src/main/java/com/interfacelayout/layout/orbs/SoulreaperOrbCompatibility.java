package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.util.InterfaceWindows;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.ImageUtil;

@Singleton
public final class SoulreaperOrbCompatibility extends Overlay
{
    private static final String GROUP = "soulreaperaxeqol";
    private static final String EXTRA = "com.soulreaperaxeqol.SoulreaperAxeQoLExtraOrbOverlay";
    private static final String NATIVE = "com.soulreaperaxeqol.SoulreaperAxeQoLNativeOrbOverlay";
    @Inject Client client;
    @Inject ConfigManager configs;
    @Inject InterfaceLayoutConfig config;
    @Inject OverlayManager overlays;
    private final Map<Overlay, OverlayLayer> replaced = new IdentityHashMap<>();
    private final CircularOrbArtwork artwork = new CircularOrbArtwork();
    private final BufferedImage timer = new BufferedImage(34,34,BufferedImage.TYPE_INT_ARGB);
    private Overlay extra, nativeOrb, imageOwner;
    private BufferedImage specialIcon, axeIcon;
    private Rectangle anchor;

    public SoulreaperOrbCompatibility() {
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    void refresh(boolean enabled) {
        anchor = null;
        if (!enabled) { restore(); return; }
        List<Overlay> found = new ArrayList<>();
        overlays.anyMatch(overlay -> {
            String type = overlay.getClass().getName();
            if (EXTRA.equals(type) || NATIVE.equals(type)) found.add(overlay);
            return false;
        });
        replaced.entrySet().removeIf(entry -> {
            if(found.contains(entry.getKey()))return false;
            if(entry.getKey().getLayer()==OverlayLayer.MANUAL)entry.getKey().setLayer(entry.getValue());
            return true;
        });
        extra = null; nativeOrb = null;
        for (Overlay overlay : found) {
            if (EXTRA.equals(overlay.getClass().getName())) extra = overlay;
            else nativeOrb = overlay;
        }
        if (extra == null) { restore(); return; }
        if (imageOwner != extra) {
            imageOwner = extra;
            try {
                specialIcon = ImageUtil.loadImageResource(extra.getClass(), "/special_attack_sprite.png");
                axeIcon = ImageUtil.loadImageResource(extra.getClass(), "/soulreaper_sprite.png");
            } catch (RuntimeException unavailable) { specialIcon = null; axeIcon = null; }
        }
        if (specialIcon == null || axeIcon == null) { restore(); return; }
        for (Overlay overlay : found) {
            OverlayLayer layer = overlay.getLayer();
            if (layer != OverlayLayer.MANUAL) {
                replaced.put(overlay, layer);
                overlays.remove(overlay); overlay.setLayer(OverlayLayer.MANUAL); overlays.add(overlay);
            }
        }
        OverlayLayer layer = replaced.get(extra);
        if (layer != null && getLayer() != layer) {
            boolean registered = overlays.remove(this);
            setLayer(layer);
            if (registered) overlays.add(this);
        }
    }

    void restore() {
        for (Map.Entry<Overlay, OverlayLayer> entry : replaced.entrySet()) {
            Overlay overlay = entry.getKey();
            if (overlay.getLayer() == OverlayLayer.MANUAL) {
                boolean registered = overlays.remove(overlay);
                overlay.setLayer(entry.getValue());
                if(registered)overlays.add(overlay);
            }
        }
        replaced.clear(); extra = null; nativeOrb = null; anchor = null;
    }

    void anchor(Rectangle circle) { anchor = new Rectangle(circle); }

    void paintNativeTimer(Graphics2D draw, Rectangle circle) {
        if (nativeOrb != null && equipped()) paintTimer(draw, nativeOrb, circle, false);
    }

    @Override public Dimension render(Graphics2D graphics) {
        Widget parent = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);
        if (anchor == null || extra == null || parent == null || parent.isHidden()
            || !config.hideOrbTabs() || config.editInterface()) return null;
        boolean worn = equipped();
        int stacks = Math.max(0, Math.min(5, client.getVarpValue(VarPlayerID.SOULREAPER_STACKS)));
        if (!worn && stacks == 0) return null;
        int amount = worn ? Math.max(0, Math.min(100, client.getVarpValue(VarPlayerID.SA_ENERGY)/10)) : stacks;
        int maximum = worn ? 100 : 5;
        Rectangle circle = new Rectangle(anchor);
        circle.translate(number("offsetX",22), number("offsetY",25));
        if (InterfaceWindows.covered(client, circle)) return null;
        Color fill = color(worn ? "specOrbColour" : "soulreaperOrbColour", worn ? 0x2798bd : 0x8d4bd3);
        Graphics2D draw = (Graphics2D) graphics.create();
        try {
            artwork.paintBackground(draw,circle);
            Graphics2D body = (Graphics2D) draw.create();
            try {
                body.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                body.clip(new Ellipse2D.Double(circle.x+4,circle.y+4,26,26));
                int height = 26*amount/maximum;
                body.clipRect(circle.x+4,circle.y+30-height,26,height);
                body.setPaint(new GradientPaint(circle.x,circle.y+4,fill.brighter(),circle.x,circle.y+30,fill.darker()));
                body.fillRect(circle.x+4,circle.y+4,26,26);
            } finally { body.dispose(); }
            paintTimer(draw,extra,circle,true);
            artwork.paint(draw,circle,config.orbRim());
            Color text = Color.getHSBColor((float)amount/maximum/3,1,1);
            CircularOrbs.paintContent(draw,circle,worn?specialIcon:axeIcon,Integer.toString(amount),text,config.orbContent(),0,0);
        } finally { draw.dispose(); }
        return null;
    }

    private boolean equipped() {
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        Item weapon = equipment == null ? null : equipment.getItem(3);
        return weapon != null && (weapon.getId()==ItemID.SOULREAPER || weapon.getId()==ItemID.SOULREAPER_AXE_ORN);
    }

    private void paintTimer(Graphics2D graphics, Overlay source, Rectangle circle, boolean offset) {
        Widget widget = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);
        if (widget == null || widget.isHidden()) return;
        Rectangle bounds = widget.getBounds();
        double x = bounds.getCenterX()+12+(offset?number("offsetX",22):0);
        double y = bounds.getCenterY()+(offset?number("offsetY",25):0);
        Graphics2D capture = timer.createGraphics();
        try {
            capture.setComposite(AlphaComposite.Clear); capture.fillRect(0,0,34,34);
            capture.setComposite(AlphaComposite.SrcOver); capture.translate(17-x,17-y);
            try { source.render(capture); } catch (RuntimeException unavailable) { return; }
        } finally { capture.dispose(); }
        Graphics2D draw = (Graphics2D) graphics.create();
        try {
            Area ring = new Area(new Ellipse2D.Double(circle.x+4,circle.y+4,26,26));
            ring.subtract(new Area(new Ellipse2D.Double(circle.x+6,circle.y+6,22,22)));
            draw.clip(ring); draw.drawImage(timer,circle.x,circle.y,null);
        } finally { draw.dispose(); }
    }

    private int number(String key,int fallback) {
        String value = configs.getConfiguration(GROUP,key);
        try { return value == null ? fallback : Math.max(-10000,Math.min(10000,Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private Color color(String key,int fallback) {
        String value = configs.getConfiguration(GROUP,key);
        try { return new Color(value == null ? fallback : Integer.parseInt(value)); }
        catch (NumberFormatException ignored) { return new Color(fallback); }
    }
}
