package com.interfacelayout.ui;

import com.interfacelayout.InterfaceLayoutConfig;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import static com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup.GROUP_NAME;

@Singleton
public final class InterfaceLayoutSidebar
{
    @Inject InterfaceLayoutConfig config;
    @Inject LayoutSettings settings;
    @Inject ClientToolbar toolbar;
    private InterfaceLayoutPanel panel;
    private NavigationButton navigation;
    private volatile boolean active;
    private volatile int generation;
    private boolean added;
    private final AtomicBoolean refreshQueued=new AtomicBoolean();

    public void start()
    {
        active=true;generation++;settings.start();
        panel=new InterfaceLayoutPanel(config,settings);
        navigation=NavigationButton.builder().tooltip("Interface Layout").icon(icon()).priority(7).panel(panel).build();
        refresh();
    }

    public void stop()
    {
        active=false;generation++;refreshQueued.set(false);settings.stop();
        if(added)toolbar.removeNavigation(navigation);
        added=false;panel=null;navigation=null;
    }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if(GROUP_NAME.equals(event.getGroup()))queueRefresh();
    }

    @Subscribe public void onProfileChanged(ProfileChanged event) {queueRefresh();}

    private void queueRefresh()
    {
        if(!active || !refreshQueued.compareAndSet(false,true))return;
        int token=generation;
        SwingUtilities.invokeLater(()->{refreshQueued.set(false);if(active && token==generation)refresh();});
    }

    private void refresh()
    {
        if(!active)return;
        boolean show=config.showSidePanel();
        if(show!=added) {
            if(show)toolbar.addNavigation(navigation);else toolbar.removeNavigation(navigation);
            added=show;
        }
        panel.refresh();
    }

    private static BufferedImage icon()
    {
        return net.runelite.client.util.ImageUtil.loadImageResource(InterfaceLayoutSidebar.class, "sidebar-icon.png");
    }
}
