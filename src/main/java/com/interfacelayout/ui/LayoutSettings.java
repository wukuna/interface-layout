package com.interfacelayout.ui;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import static com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup.GROUP_NAME;

@Singleton
public final class LayoutSettings
{
    public enum Mode {
        NORMAL("Normal (top right)"), FREE("Free placement"), COMPACT("Compact orbs");
        private final String label;
        Mode(String label) {this.label=label;}
        @Override public String toString() {return label;}
    }

    @Inject ConfigManager configs;
    @Inject ClientThread thread;
    @Inject InterfaceLayoutConfig config;
    private volatile boolean active;
    private volatile int generation;

    void start() {active=true;generation++;}
    void stop() {active=false;generation++;}

    public void set(String key,Object value) {apply(()->configs.setConfiguration(GROUP_NAME,key,value));}

    public void moveOrb(com.interfacelayout.layout.orbs.OrbOrder orb,int step)
    {
        apply(()->configs.setConfiguration(GROUP_NAME,"orbStackOrder",
            com.interfacelayout.layout.orbs.OrbOrder.move(config.orbStackOrder(),orb,step)));
    }

    public Mode mode()
    {
        return config.layout()==OrbLayout.FREE_POSITION?Mode.FREE:config.hideMinimap()?Mode.COMPACT:Mode.NORMAL;
    }

    public void mode(Mode mode)
    {
        apply(()->{
            OrbLayout previous=config.layout();
            if(previous!=OrbLayout.FREE_POSITION)configs.setConfiguration(GROUP_NAME,"editorLastCompactLayout",previous);
            OrbLayout compact=config.editorLastCompactLayout();
            if(compact==null || compact==OrbLayout.FREE_POSITION)compact=OrbLayout.VERTICAL;
            configs.setConfiguration(GROUP_NAME,"orbLayout",mode==Mode.FREE?OrbLayout.FREE_POSITION:compact);
            configs.setConfiguration(GROUP_NAME,"hideMinimap",mode==Mode.COMPACT);
        });
    }

    public void apply(Runnable action)
    {
        ConfigProfile profile=configs.getProfile();
        if(!active || profile==null)return;
        long id=profile.getId();
        int token=generation;
        thread.invokeLater(()->{
            ConfigProfile current=configs.getProfile();
            if(active && token==generation && current!=null && current.getId()==id)action.run();
        });
    }
}
