package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutPlugin;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Singleton
final class OrbTimerInfoBoxes
{
    @Inject Client client;
    @Inject InterfaceLayoutConfig config;
    @Inject InterfaceLayoutPlugin plugin;
    @Inject InfoBoxManager manager;
    @Inject SpriteManager sprites;
    private final Map<OrbTimerKind, TimerBox> boxes = new EnumMap<>(OrbTimerKind.class);

    void refresh(OrbTimers timers)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            clear();
            return;
        }
        for (OrbTimerKind kind : OrbTimerKind.values())
        {
            TimerBox box = boxes.get(kind);
            if (!kind.enabled.test(config))
            {
                if (box != null) manager.removeInfoBox(boxes.remove(kind));
                continue;
            }
            if (box == null)
            {
                box = new TimerBox(kind, timers);
                boxes.put(kind, box);
                manager.addInfoBox(box);
            }
            if (!box.iconReady)
            {
                BufferedImage image = sprites.getSprite(kind.sprite, 0);
                if (image != null)
                {
                    box.setImage(badge(image, kind.badge));
                    box.iconReady = true;
                    manager.updateInfoBoxImage(box);
                }
            }
        }
    }

    void clear()
    {
        for (TimerBox box : boxes.values()) manager.removeInfoBox(box);
        boxes.clear();
    }

    static String format(double seconds)
    {
        if (!Double.isFinite(seconds)) return "Sync";
        long value = (long) Math.ceil(Math.max(0, seconds));
        if (value < 60) return value + "s";
        if (value >= 3600) return value / 3600 + "h" + value % 3600 / 60 + "m";
        return value / 60 + ":" + (value % 60 < 10 ? "0" : "") + value % 60;
    }

    private static BufferedImage badge(BufferedImage icon, String label)
    {
        BufferedImage result = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = result.createGraphics();
        try
        {
            if (icon != null)
            {
                double scale = Math.min(1, 21.0 / Math.max(icon.getWidth(), icon.getHeight()));
                int width = (int) (icon.getWidth() * scale), height = (int) (icon.getHeight() * scale);
                draw.drawImage(icon, (24 - width) / 2, (24 - height) / 2, width, height, null);
            }
            draw.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            draw.setColor(new Color(25, 27, 29, 230));
            draw.fillRoundRect(14, 0, 10, 11, 3, 3);
            draw.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            draw.setColor(Color.WHITE);
            draw.drawString(label, 16, 9);
        }
        finally { draw.dispose(); }
        return result;
    }

    private final class TimerBox extends InfoBox
    {
        private final OrbTimerKind kind;
        private final OrbTimers timers;
        private boolean iconReady;

        TimerBox(OrbTimerKind kind, OrbTimers timers)
        {
            super(badge(null, kind.badge), plugin);
            this.kind = kind;
            this.timers = timers;
            setTooltip(kind.title + "<br>Estimated from your current stats and effects; updates each game tick."
                + (kind == OrbTimerKind.PRAYER_EMPTY ? "<br>Includes known potion restores; assumes current prayers stay active." : "")
                + (kind == OrbTimerKind.HP_FULL ? "<br>Assumes no further damage or external healing." : "")
                + (kind == OrbTimerKind.RUN_EMPTY ? "<br>Assumes the current observed running rate continues." : ""));
        }

        @Override public String getName() { return super.getName() + "_" + kind.name(); }
        @Override public String getText()
        {
            double seconds = timers.seconds(kind, System.nanoTime());
            return kind == OrbTimerKind.RUN_FULL && Double.isNaN(seconds) ? "Paused" : format(seconds);
        }
        @Override public Color getTextColor()
        {
            return kind == OrbTimerKind.RUN_FULL || kind == OrbTimerKind.RUN_EMPTY ? OrbTimers.RUN_COLOR
                : kind == OrbTimerKind.PRAYER_RESTORE ? OrbTimers.REGENERATION_COLOR : Color.WHITE;
        }
        @Override public boolean render()
        {
            double seconds = timers.seconds(kind, System.nanoTime());
            return seconds >= 0 || Double.isNaN(seconds);
        }
    }
}
