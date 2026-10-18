package com.interfacelayout.layout.sizing;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/** Own passive numeric labels, drawn with the HUD interface rather than above game windows. */
@Singleton
public class OrbTextOverlay extends Overlay
{
    @Inject private HudSizingController sizing;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController manager;

    public OrbTextOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.Orbs.ORB_HEALTH >>> 16);
    }

    public static boolean supports(Widget widget)
    {
        String text = widget.getText();
        int font = widget.getFontId();
        return widget.getType() == WidgetType.TEXT && widget.getOnOpListener() == null
            && (widget.getActions() == null || widget.getActions().length == 0)
            && (font == FontID.PLAIN_11 || font == FontID.PLAIN_12 || font == FontID.BOLD_12)
            && text != null && text.matches("(?:<col=[0-9a-fA-F]{1,6}>)?[0-9%.,kKmMbB+\\-]+(?:</col>)?");
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!config.scaleOrbText() || !manager.active || !manager.isLoggedIn() || manager.isMinimapMinimized()) return null;
        Graphics2D copy = (Graphics2D) graphics.create();
        try
        {
            for (Label label : sizing.labels())
            {
                Widget widget = label.widget;
                if (label.root.isHidden() || !supports(widget)) continue;
                Rectangle bounds = WidgetGeometry.bounds(widget);
                if (bounds.isEmpty()) continue;
                float alpha = (256 - Math.max(0, Math.min(255, widget.getOpacity())))
                    * (256 - Math.max(0, Math.min(255, label.root.getOpacity()))) / 65536f;
                copy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                Font nativeFont = widget.getFontId() == FontID.PLAIN_11 ? FontManager.getRunescapeSmallFont()
                    : widget.getFontId() == FontID.BOLD_12 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont();
                copy.setFont(nativeFont.deriveFont(nativeFont.getSize2D() * label.percent / 100f));
                FontMetrics metrics = copy.getFontMetrics();
                String text = Text.removeTags(widget.getText());
                int x = bounds.x;
                if (widget.getXTextAlignment() == 1) x += (bounds.width - metrics.stringWidth(text)) / 2;
                else if (widget.getXTextAlignment() == 2) x += bounds.width - metrics.stringWidth(text);
                int y = bounds.y + metrics.getAscent();
                if (widget.getYTextAlignment() == 1) y = bounds.y + (bounds.height - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
                else if (widget.getYTextAlignment() == 2) y = bounds.y + bounds.height - metrics.getDescent();
                if (widget.getTextShadowed()) { copy.setColor(Color.BLACK); copy.drawString(text, x + 1, y + 1); }
                // Numeric orb labels use one current color; preserve a wrapping native color tag.
                String raw = widget.getText();
                int color = widget.getTextColor();
                if (raw.startsWith("<col=")) color = Integer.parseInt(raw.substring(5, raw.indexOf('>')), 16);
                copy.setColor(new Color(color));
                copy.drawString(text, x, y);
            }
        }
        finally { copy.dispose(); }
        return null;
    }

    public static final class Label
    {
        final Widget root, widget;
        final int percent;
        public Label(Widget root, Widget widget, int percent) { this.root = root; this.widget = widget; this.percent = percent; }
    }
}
