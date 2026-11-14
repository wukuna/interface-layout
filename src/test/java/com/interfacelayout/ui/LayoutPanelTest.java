package com.interfacelayout.ui;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import com.interfacelayout.layout.orbs.OrbLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LayoutPanelTest
{
    private final InterfaceLayoutConfig config=mock(InterfaceLayoutConfig.class,CALLS_REAL_METHODS);
    private final LayoutSettings settings=new LayoutSettings();
    private final List<Runnable> pending=new ArrayList<>();

    private void setup()
    {
        settings.config=config;settings.configs=mock(ConfigManager.class);settings.thread=mock(ClientThread.class);
        doReturn(profile(1)).when(settings.configs).getProfile();
        doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(settings.thread).invokeLater(any(Runnable.class));
        settings.start();
    }

    @Test public void eachOrbHasFourChoicesInBothLayoutsAndOnlyTheChosenKeyIsWritten() throws Exception
    {
        setup();
        for(boolean circles:new boolean[]{false,true}) {
            when(config.hideOrbTabs()).thenReturn(circles);
            SwingUtilities.invokeAndWait(()->{
                InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);button(panel,"Timers").doClick();
                for(String key:new String[]{"hitpointsTimerStyle","prayerTimerStyle","specialTimerStyle"}) {
                    JComboBox<?> choice=(JComboBox<?>)control(panel,key);assertTrue(visible(choice));assertEquals(4,choice.getItemCount());
                }
                ((JComboBox<?>)control(panel,"hitpointsTimerStyle")).setSelectedItem(NativeTimerStyle.EIGHT_SLICES);
            });
            assertEquals(1,pending.size());pending.remove(0).run();
        }
        verify(settings.configs,times(2)).setConfiguration("interfacelayout","hitpointsTimerStyle",InterfaceLayoutConfig.OrbTimerStyle.EIGHT_SLICES);
        verify(settings.configs,never()).setConfiguration(eq("interfacelayout"),eq("prayerTimerStyle"),any());
        verify(settings.configs,never()).setConfiguration(eq("interfacelayout"),eq("specialTimerStyle"),any());
    }

    @Test public void sharedRimIsOnlyShownWithCircularMinimapFrame() throws Exception
    {
        setup();when(config.hideOrbTabs()).thenReturn(true);
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);button(panel,"Appearance").doClick();
            JComponent rim=control(panel,"orbRim");assertFalse(visible(rim));
            when(config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.CIRCULAR);panel.refresh();assertTrue(visible(rim));
            when(config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.NATIVE);panel.refresh();assertFalse(visible(rim));
        });
        assertTrue(pending.isEmpty());
    }

    @Test public void openingRefreshingAndVisitingTabsDoNotWriteSettings() throws Exception
    {
        setup();
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);
            panel.onActivate();panel.refresh();
            for(String tab:new String[]{"Layout","Appearance","Visibility","Timers"}) {
                button(panel,tab).doClick();panel.refresh();
            }
        });
        assertTrue(pending.isEmpty());verify(settings.configs,never()).setConfiguration(anyString(),anyString(),any());
    }

    @Test public void helpRemainsAvailableAfterConfigRefreshWithoutWritingSettings() throws Exception
    {
        setup();
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);
            button(panel,"Appearance").doClick();
            JComponent frame=control(panel,"minimapFrame");
            String help=frame.getToolTipText();assertNotNull(help);assertTrue(help.contains("compass"));
            when(config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.NATIVE);
            panel.refresh();assertEquals(help,frame.getToolTipText());
            assertTrue(frame.getAccessibleContext().getAccessibleDescription().contains("compass"));
            button(panel,"Timers").doClick();
            assertTrue(control(panel,"prayerTimerStyle").getToolTipText().contains("Eight slices"));
        });
        assertTrue(pending.isEmpty());verify(settings.configs,never()).setConfiguration(anyString(),anyString(),any());
    }

    @Test public void mirroringOnlyAppearsForNumberTabsAndGraveVisibilityIsIndependent() throws Exception
    {
        setup();
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);button(panel,"Appearance").doClick();
            when(config.hideOrbTabs()).thenReturn(false);panel.refresh();assertTrue(visible(control(panel,"mirrorPrayer")));
            when(config.hideOrbTabs()).thenReturn(true);panel.refresh();assertFalse(visible(control(panel,"mirrorPrayer")));
            button(panel,"Visibility").doClick();JCheckBox grave=(JCheckBox)control(panel,"hideGravestone");assertTrue(grave.isSelected());grave.doClick();
        });
        assertEquals(1,pending.size());pending.remove(0).run();verify(settings.configs).setConfiguration("interfacelayout","hideGravestone",true);
    }

    @Test public void dependentControlsRetainTheirValuesAndVisibilityFollowsParents() throws Exception
    {
        setup();when(config.orbPrayerTimer()).thenReturn(true);when(config.prayerOrbDisplay()).thenReturn(InterfaceLayoutConfig.PrayerDisplay.BOTH);
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);button(panel,"Timers").doClick();
            JComponent display=control(panel,"prayerOrbDisplay");assertFalse(visible(display));
            when(config.nativeOrbTimerStyle()).thenReturn(NativeTimerStyle.EIGHT_SLICES);panel.refresh();assertTrue(visible(display));
            when(config.disableOrbTimers()).thenReturn(true);panel.refresh();assertFalse(visible(display));
            when(config.disableOrbTimers()).thenReturn(false);panel.refresh();assertTrue(visible(display));
            assertEquals(InterfaceLayoutConfig.PrayerDisplay.BOTH,((JComboBox<?>)display).getSelectedItem());
            button(panel,"Visibility").doClick();
            JCheckBox hp=(JCheckBox)control(panel,"hideHp");assertTrue(hp.isSelected());hp.doClick();
        });
        assertEquals(1,pending.size());pending.remove(0).run();
        verify(settings.configs).setConfiguration("interfacelayout","hideHp",true);
    }

    @Test public void queuedEditsSurviveBackgroundSavesButNotProfileChangesOrShutdown()
    {
        setup();settings.set("hideHp",true);doReturn(profile(1)).when(settings.configs).getProfile();pending.remove(0).run();
        verify(settings.configs).setConfiguration("interfacelayout","hideHp",true);
        settings.set("hidePrayer",true);doReturn(profile(2)).when(settings.configs).getProfile();pending.remove(0).run();
        settings.set("hideRun",true);settings.stop();settings.start();pending.remove(0).run();
        verify(settings.configs,never()).setConfiguration(eq("interfacelayout"),eq("hidePrayer"),any());
        verify(settings.configs,never()).setConfiguration(eq("interfacelayout"),eq("hideRun"),any());
    }

    @Test public void modeChangesOnlyWriteModeAndVisibilityNeverSavedPositions()
    {
        setup();when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        when(config.editorLastCompactLayout()).thenReturn(OrbLayout.HORIZONTAL);
        settings.mode(LayoutSettings.Mode.NORMAL);pending.remove(0).run();
        verify(settings.configs).setConfiguration("interfacelayout","orbLayout",OrbLayout.HORIZONTAL);
        verify(settings.configs).setConfiguration("interfacelayout","hideMinimap",false);
        assertEquals(2,mockingDetails(settings.configs).getInvocations().stream().filter(i->i.getMethod().getName().equals("setConfiguration")).count());
    }

    @Test public void sidebarVisibilityAndPendingRefreshRespectLifecycle() throws Exception
    {
        setup();InterfaceLayoutSidebar sidebar=new InterfaceLayoutSidebar();sidebar.config=config;sidebar.settings=settings;sidebar.toolbar=mock(ClientToolbar.class);
        SwingUtilities.invokeAndWait(sidebar::start);verify(sidebar.toolbar).addNavigation(any());
        when(config.showSidePanel()).thenReturn(false);ConfigChanged event=new ConfigChanged();event.setGroup("interfacelayout");event.setKey("showSidePanel");
        sidebar.onConfigChanged(event);SwingUtilities.invokeAndWait(()->{});verify(sidebar.toolbar).removeNavigation(any());
        clearInvocations(sidebar.toolbar);
        SwingUtilities.invokeAndWait(()->{sidebar.onConfigChanged(event);sidebar.stop();});
        SwingUtilities.invokeAndWait(()->{});verifyNoInteractions(sidebar.toolbar);assertTrue(pending.isEmpty());
    }

    @Test public void renderPanelsAtNativeSidebarWidth() throws Exception
    {
        setup();when(config.nativeOrbTimerStyle()).thenReturn(NativeTimerStyle.EIGHT_SLICES);
        when(config.menuOverlayButtons()).thenReturn(true);
        SwingUtilities.invokeAndWait(()->{
            InterfaceLayoutPanel panel=new InterfaceLayoutPanel(config,settings);
            for(String tab:new String[]{"Layout","Appearance","Visibility","Timers"}) {
                button(panel,tab).doClick();panel.refresh();panel.setSize(225,panel.getPreferredSize().height);layout(panel);
                BufferedImage image=new BufferedImage(225,panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D draw=image.createGraphics();panel.printAll(draw);draw.dispose();
                try {
                    java.io.File file=new java.io.File("build/previews/"+tab+".png");file.getParentFile().mkdirs();ImageIO.write(image,"png",file);
                } catch(java.io.IOException e) {throw new AssertionError(e);}
                for(Component component:all(panel))if(component instanceof JCheckBox || component instanceof JComboBox) {
                    java.awt.Point location=SwingUtilities.convertPoint(component.getParent(),component.getLocation(),panel);
                    assertTrue(tab+" "+component.getName(),location.x+component.getWidth()<=225);
                }
            }
        });
    }

    private static ConfigProfile profile(long id) {ConfigProfile profile=mock(ConfigProfile.class);when(profile.getId()).thenReturn(id);return profile;}
    private static boolean visible(Component c) {for(Component p=c;p!=null;p=p.getParent())if(!p.isVisible())return false;return true;}
    private static JComponent control(Container parent,String key) {return (JComponent)all(parent).stream().filter(c->key.equals(c.getName())).findFirst().orElseThrow(AssertionError::new);}
    private static JButton button(Container parent,String label) {return (JButton)all(parent).stream().filter(c->c instanceof JButton && label.equals(((JButton)c).getText())).findFirst().orElseThrow(AssertionError::new);}
    private static List<Component> all(Container parent) {List<Component> result=new ArrayList<>();for(Component c:parent.getComponents()){result.add(c);if(c instanceof Container)result.addAll(all((Container)c));}return result;}
    private static void layout(Container c) {c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
