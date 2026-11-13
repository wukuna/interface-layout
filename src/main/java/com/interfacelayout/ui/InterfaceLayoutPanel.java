package com.interfacelayout.ui;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.*;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.OrbTimerStyles;
import net.runelite.api.gameval.InterfaceID;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public final class InterfaceLayoutPanel extends PluginPanel
{
    private final InterfaceLayoutConfig config;
    private final LayoutSettings settings;
    private final LayoutControls controls;
    private final JPanel content=new JPanel(new BorderLayout());
    private final Map<String,JPanel> pages=new LinkedHashMap<>();
    private final Map<String,JButton> tabs=new LinkedHashMap<>();

    InterfaceLayoutPanel(InterfaceLayoutConfig config,LayoutSettings settings)
    {
        this.config=config;this.settings=settings;controls=new LayoutControls(settings);
        setLayout(new BorderLayout(0,10));setBorder(BorderFactory.createEmptyBorder(10,8,10,8));
        JPanel top=LayoutControls.column();JLabel title=new JLabel("Interface Layout");title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));top.add(title);
        JPanel navigation=new JPanel(new GridLayout(2,2,3,3));navigation.setOpaque(false);top.add(navigation);add(top,BorderLayout.NORTH);
        for(String name:new String[]{"Layout","Appearance","Visibility","Timers"}) {
            JPanel page=LayoutControls.column();pages.put(name,page);JButton button=new JButton(name);tabs.put(name,button);
            button.addActionListener(e->show(name));navigation.add(button);
        }
        content.setOpaque(false);add(content,BorderLayout.CENTER);
        layoutPage(pages.get("Layout"));appearancePage(pages.get("Appearance"));visibilityPage(pages.get("Visibility"));timerPage(pages.get("Timers"));
        show("Layout");refresh();
    }

    public void refresh() {controls.refresh();revalidate();repaint();}
    @Override public void onActivate() {refresh();}

    private void show(String name)
    {
        content.removeAll();content.add(pages.get(name),BorderLayout.NORTH);
        tabs.forEach((label,button)->button.setForeground(label.equals(name)?ColorScheme.BRAND_ORANGE:java.awt.Color.WHITE));
        revalidate();repaint();
    }

    private void layoutPage(JPanel p)
    {
        controls.heading(p,"Minimap & orbs");
        controls.choice(p,"positionMode","Positioning",LayoutSettings.Mode.values(),settings::mode,settings::mode);
        controls.hint(p,"Normal restores the usual positions and keeps your saved free layout. Free placement uses Alt-drag in resizable modes and has its own anchor points or can be used with Custom UI Anchors.");
        JPanel compact=controls.when(p,()->settings.mode()==LayoutSettings.Mode.COMPACT);
        controls.choice(compact,"orbLayout","Compact preset",new OrbLayout[]{OrbLayout.VERTICAL,OrbLayout.HORIZONTAL,OrbLayout.HORIZONTAL_WIDE,OrbLayout.CUSTOM},config::layout);
        controls.choice(compact,"verticalAnchor","Vertical anchor",VerticalAnchor.values(),config::verticalAnchor);
        controls.choice(compact,"horizontalAnchor","Horizontal anchor",HorizontalAnchor.values(),config::horizontalAnchor);
        controls.number(compact,"verticalYAdjustment","Vertical offset",config::verticalYAdjustment,0,200);
        controls.toggle(compact,"disableReordering","Keep hidden orb slots",config::disableReordering,false);
        controls.toggle(compact,"leaveEmptySpace","Keep preset container size",config::leaveEmptySpace,false);
        controls.toggle(compact,"enableOrbSwapping","Allow preset slot swaps",config::enableOrbSwapping,false);
        controls.hint(compact,"Slot swaps require Edit interface. Drag an orb onto another slot.");
        controls.heading(p,"Menu tabs");
        controls.choice(p,"menuLayout","Arrangement",MenuStoneController.Layout.values(),config::menuLayout);
        JPanel freeMenu=controls.when(p,()->config.menuLayout()==MenuStoneController.Layout.FREE_POSITION);
        controls.choice(freeMenu,"menuDirection","Initial direction",MenuStoneController.Direction.values(),config::menuDirection);
        controls.number(p,"menuSpacing","Tab spacing",config::menuSpacing,0,20);
        controls.heading(p,"Editing");
        controls.toggle(p,"editInterface","Edit interface",config::editInterface,false);
        controls.number(p,"editGrid","Grouping grid",config::editGrid,1,32);
        controls.hint(p,"Alt-drag moves bars and free orbs. Shift-drag joins or separates menu tabs.");
        controls.button(p,"Reset positions and groups...",()->settings.set("resetLayoutPositions",true));
        controls.heading(compact,"Legacy separate minimap");
        controls.toggle(compact,"enableMinimapOverlay","Show separate minimap",config::showMinimapInCompactView,false);
        JPanel legacy=controls.when(compact,config::showMinimapInCompactView);
        controls.toggle(legacy,"enableOverlayToggleOption","Include toggle action",config::showToggleOnMinimapButton,false);
        controls.toggle(legacy,"enableLogoutXOverlay","Show Logout-X",config::showOverlayLogoutX,false);
        controls.hint(legacy,"This older map does not support other plugins' minimap overlays. Free placement moves the native map instead.");
    }

    private void appearancePage(JPanel p)
    {
        controls.heading(p,"Menu buttons");
        controls.toggle(p,"menuOverlayButtons","Use overlay menu buttons",config::menuOverlayButtons,false);
        JPanel buttons=controls.when(p,config::menuOverlayButtons);
        controls.number(buttons,"menuGroupSize","Button size (%)",config::menuGroupSize,25,200);
        controls.choice(buttons,"menuOverlayIconStyle","Icon artwork",MenuIconStyle.values(),config::menuOverlayIconStyle);
        controls.toggle(buttons,"menuKeepNativeIcons","Keep native icon size",config::menuKeepNativeIcons,false);
        controls.toggle(p,"menuRestoreLogoutTab","Restore logout tab (Modern only)",config::restoreLogoutTab,false);
        controls.toggle(p,"allowClicksThroughGaps","Allow clicks through menu gaps",config::allowClicksThroughGaps,false);
        controls.heading(p,"Orbs · Beta");
        controls.choice(p,"orbAppearance","Orb appearance",new String[]{"Number tabs","Circles only"},
            ()->config.hideOrbTabs()?"Circles only":"Number tabs",v->settings.set("hideOrbTabs",v.equals("Circles only")));
        JPanel circular=controls.when(p,config::hideOrbTabs);
        controls.choice(circular,"orbContent","Orb contents",OrbContent.values(),config::orbContent);
        JPanel freeMap=controls.when(p,()->settings.mode()==LayoutSettings.Mode.FREE);
        controls.heading(freeMap,"Minimap");
        controls.choice(freeMap,"minimapFrame","Frame",MinimapFrame.values(),config::minimapFrame);
        JPanel rims=controls.when(freeMap,()->config.minimapFrame()==MinimapFrame.CIRCULAR);
        controls.hint(rims,"Circular rim uses custom rings. Native will not remove the compass circle attached to the minimap, even if the compass is Free Positioned.");
        controls.choice(rims,"orbRim","Shared circular rim",OrbRim.values(),config::orbRim);
        controls.hint(rims,"Applies to custom circular map, compass, shield and orb frames.");
        controls.heading(p,"Equipment · Beta");
        controls.toggle(p,"betaEquipmentPanel","Enhanced equipment panel",config::betaEquipmentPanel,false);
        JPanel equipment=controls.when(p,config::betaEquipmentPanel);
        controls.toggle(equipment,"betaEquipmentReducedTooltips","Reduced equipment tooltips",config::betaEquipmentReducedTooltips,false);
    }

    private void visibilityPage(JPanel p)
    {
        controls.hint(p,"Checked controls stay visible. Hiding them keeps their saved positions.");
        controls.heading(p,"Minimap & orbs");
        controls.toggle(p,"hideMinimap","Minimap",config::hideMinimap,true);
        controls.toggle(p,"hideCompass","Compass",config::hideCompass,true);
        controls.toggle(p,"hideHp","Hitpoints",config::hideHp,true);
        controls.toggle(p,"hidePrayer","Prayer",config::hidePray,true);
        controls.toggle(p,"hideRun","Run energy",config::hideRun,true);
        controls.toggle(p,"hideSpec","Special attack",config::hideSpec,true);
        JPanel special=controls.when(p,()->!config.hideSpec());
        controls.toggle(special,"showShieldSpecialOrb","Shield special orb · Beta",config::showShieldSpecialOrb,false);
        controls.toggle(special,"hideExtraSpecialAttackBar","Extra special-attack bar",config::hideExtraSpecialAttackBar,true);
        JPanel extraBar=controls.when(special,()->config.hideOrbTabs() && !config.hideExtraSpecialAttackBar());
        controls.hint(extraBar,"In Circles only, this bar sits below the special-attack orb, separate from its timer.");
        controls.toggle(p,"hideMinimapWithSidePanel","Hide map and orbs with side panel",config::hideMinimapWithSidePanel,false);
        controls.hint(p,"Hide with side panel applies in resizable Modern.");
        controls.heading(p,"Menu tabs");
        controls.toggle(p,"hideTopMenu","Combat–spellbook group",config::hideTopMenu,true);
        JPanel top=controls.when(p,()->!config.hideTopMenu());
        controls.toggle(top,"hideStone0","Combat",config::hideCombatStone,true);
        controls.toggle(top,"hideStone1","Skills",config::hideSkillsStone,true);
        controls.toggle(top,"hideStone2","Quests",config::hideQuestsStone,true);
        controls.toggle(top,"hideStone3","Inventory",config::hideInventoryStone,true);
        controls.toggle(top,"hideStone4","Equipment",config::hideEquipmentStone,true);
        controls.toggle(top,"hideStone5","Prayer",config::hidePrayerStone,true);
        controls.toggle(top,"hideStone6","Spellbook",config::hideSpellbookStone,true);
        controls.toggle(p,"hideBottomMenu","Clan–music group",config::hideBottomMenu,true);
        JPanel bottom=controls.when(p,()->!config.hideBottomMenu());
        controls.toggle(bottom,"hideStone7","Clan",config::hideClanStone,true);
        controls.toggle(bottom,"hideStone8","Account",config::hideAccountManagementStone,true);
        controls.toggle(bottom,"hideStone9","Friends",config::hideFriendsStone,true);
        controls.toggle(bottom,"hideStone10","Logout tab / Logout-X",config::hideLogoutStone,true);
        controls.toggle(bottom,"hideStone11","Settings",config::hideSettingsStone,true);
        controls.toggle(bottom,"hideStone12","Emotes",config::hideEmotesStone,true);
        controls.toggle(bottom,"hideStone13","Music",config::hideMusicStone,true);
        controls.heading(p,"Other map controls");
        controls.toggle(p,"hideXp","XP button",config::hideXp,true);
        controls.toggle(p,"hideWorld","World map",config::hideWorld,true);
        controls.toggle(p,"hideStore","Store",config::hideStore,true);
        controls.toggle(p,"hideActivity","Activity advisor",config::hideActivity,true);
        controls.toggle(p,"hideWiki","Wiki banner",config::hideWiki,true);
        controls.toggle(p,"hideLogoutX","Logout-X (Modern)",config::hideLogout,true);
        controls.toggle(p,"hideMinimapButton","Minimap toggle button",config::hideMinimapToggle,true);
        JPanel eye=controls.when(p,()->!config.hideMinimapToggle());
        controls.choice(eye,"minimapButtonPlacement","Button location",TogglePlacement.values(),config::minimapTogglePlacement);
        controls.toggle(eye,"rightClickToggleButtons","Require right-click",config::rightClickToggleButtons,false);
        controls.heading(p,"Classic borders");
        controls.toggle(p,"hideLeftBar","Left border",config::hideLeftBar,true);
        controls.toggle(p,"hideRightBar","Right border",config::hideRightBar,true);
    }

    private void timerPage(JPanel p)
    {
        controls.heading(p,"Orb timers · Beta");
        controls.toggle(p,"disableOrbTimers","Enable orb timers",config::disableOrbTimers,true);
        JPanel timers=controls.when(p,()->!config.disableOrbTimers());
        controls.toggle(timers,"orbHitpointsTimer","HP regeneration",config::orbHitpointsTimer,false);
        timerStyle(controls.when(timers,config::orbHitpointsTimer),"hitpointsTimerStyle","HP style",InterfaceID.Orbs.ORB_HEALTH);
        controls.toggle(timers,"orbPrayerTimer","Prayer drain / amount",config::orbPrayerTimer,false);
        JPanel prayerTimer=controls.when(timers,config::orbPrayerTimer);
        timerStyle(prayerTimer,"prayerTimerStyle","Prayer style",InterfaceID.Orbs.ORB_PRAYER);
        JPanel prayer=controls.when(prayerTimer,()->OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER)==NativeTimerStyle.TAG_METERS
            || OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER)==NativeTimerStyle.EIGHT_SLICES);
        controls.choice(prayer,"prayerOrbDisplay","Prayer display",PrayerDisplay.values(),config::prayerOrbDisplay);
        controls.hint(prayer,"Amount tracks current prayer. The thin countdown shows the next point drained; it resets when a point is lost.");
        controls.toggle(timers,"orbSpecialTimer","Special regeneration",config::orbSpecialTimer,false);
        timerStyle(controls.when(timers,config::orbSpecialTimer),"specialTimerStyle","Special style",InterfaceID.Orbs.ORB_SPECENERGY);
        controls.hint(timers,"Mix all four styles with Number tabs or Circles only. Tag meters sit below circular orbs.");
        controls.hint(p,"Prayer's blue inset stays unchanged. Native low-prayer flashes and existing potion-dose reminders stay active with their own settings.");
    }
    private void timerStyle(JPanel parent,String key,String label,int component)
    {
        controls.choice(parent,key,label,NativeTimerStyle.values(),()->OrbTimerStyles.resolve(config,component),
            value->settings.set(key,OrbTimerStyle.valueOf(value.name())));
    }

}
