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
        for(String name:new String[]{"Menu Tabs","Orbs","Anchors","General"}) {
            JPanel page=LayoutControls.column();pages.put(name,page);JButton button=new JButton(name);tabs.put(name,button);
            button.addActionListener(e->show(name));navigation.add(button);
        }
        content.setOpaque(false);add(content,BorderLayout.CENTER);
        menuPage(pages.get("Menu Tabs"));orbPage(pages.get("Orbs"));generalPage(pages.get("General"));
        tabs.get("Anchors").setEnabled(false);
        show("Orbs");refresh();
    }

    public void addAnchors(com.interfacelayout.anchors.AnchorCoordinator anchors,com.interfacelayout.anchors.AnchorTransfers transfers,com.interfacelayout.anchors.AnchorTemplates templates,com.interfacelayout.anchors.ExternalAnchorAdapter external)
    {
        JPanel page=new com.interfacelayout.anchors.AnchorPanel(anchors,transfers,templates,external);pages.put("Anchors",page);
        tabs.get("Anchors").setEnabled(true);
    }
    public void refresh() {controls.refresh();revalidate();repaint();}
    @Override public void onActivate() {refresh();}

    private void show(String name)
    {
        content.removeAll();content.add(pages.get(name),BorderLayout.NORTH);
        tabs.forEach((label,button)->button.setForeground(label.equals(name)?ColorScheme.BRAND_ORANGE:java.awt.Color.WHITE));
        revalidate();repaint();
    }

    private void menuPage(JPanel p)
    {
        controls.choice(p,"menuLayout","Arrangement",MenuStoneController.Layout.values(),config::menuLayout);
        JPanel freeMenu=controls.when(p,()->config.menuLayout()==MenuStoneController.Layout.FREE_POSITION);
        controls.choice(freeMenu,"menuDirection","Initial direction",MenuStoneController.Direction.values(),config::menuDirection);
        controls.number(p,"menuSpacing","Tab spacing",config::menuSpacing,0,20);
        controls.toggle(p,"menuOverlayButtons","Use overlay menu buttons",config::menuOverlayButtons,false);
        JPanel buttons=controls.when(p,config::menuOverlayButtons);
        controls.number(buttons,"menuGroupSize","Button size (%)",config::menuGroupSize,25,200);
        controls.choice(buttons,"menuOverlayIconStyle","Icon artwork",MenuIconStyle.values(),config::menuOverlayIconStyle);
        controls.toggle(buttons,"menuKeepNativeIcons","Keep native icon size",config::menuKeepNativeIcons,false);
        controls.toggle(p,"menuRestoreLogoutTab","Restore logout tab (Modern only)",config::restoreLogoutTab,false);
        controls.toggle(p,"allowClicksThroughGaps","Allow clicks through menu gaps",config::allowClicksThroughGaps,false);
        JPanel visibility=controls.section(p,"Visible menu tabs",false);
        controls.toggle(visibility,"hideTopMenu","Combat–spellbook group",config::hideTopMenu,true);
        JPanel top=controls.when(visibility,()->!config.hideTopMenu());
        controls.toggle(top,"hideStone0","Combat",config::hideCombatStone,true);
        controls.toggle(top,"hideStone1","Skills",config::hideSkillsStone,true);
        controls.toggle(top,"hideStone2","Quests",config::hideQuestsStone,true);
        controls.toggle(top,"hideStone3","Inventory",config::hideInventoryStone,true);
        controls.toggle(top,"hideStone4","Equipment",config::hideEquipmentStone,true);
        controls.toggle(top,"hideStone5","Prayer",config::hidePrayerStone,true);
        controls.toggle(top,"hideStone6","Spellbook",config::hideSpellbookStone,true);
        controls.toggle(visibility,"hideBottomMenu","Clan–music group",config::hideBottomMenu,true);
        JPanel bottom=controls.when(visibility,()->!config.hideBottomMenu());
        controls.toggle(bottom,"hideStone7","Clan",config::hideClanStone,true);
        controls.toggle(bottom,"hideStone8","Account",config::hideAccountManagementStone,true);
        controls.toggle(bottom,"hideStone9","Friends",config::hideFriendsStone,true);
        controls.toggle(bottom,"hideStone10","Logout tab / Logout-X",config::hideLogoutStone,true);
        controls.toggle(bottom,"hideStone11","Settings",config::hideSettingsStone,true);
        controls.toggle(bottom,"hideStone12","Emotes",config::hideEmotesStone,true);
        controls.toggle(bottom,"hideStone13","Music",config::hideMusicStone,true);
    }

    private void orbPage(JPanel p)
    {
        controls.choice(p,"positionMode","Positioning",LayoutSettings.Mode.values(),settings::mode,settings::mode);
        controls.tooltip("positionMode","Normal restores the usual positions without clearing your free layout. Free placement supports Alt-drag in resizable modes, its own anchor points, or Custom UI Anchors.");
        JPanel compact=controls.when(p,()->settings.mode()==LayoutSettings.Mode.COMPACT);
        controls.choice(compact,"orbLayout","Compact preset",new OrbLayout[]{OrbLayout.VERTICAL,OrbLayout.HORIZONTAL,OrbLayout.HORIZONTAL_WIDE,OrbLayout.CUSTOM},config::layout);
        controls.choice(compact,"verticalAnchor","Vertical anchor",VerticalAnchor.values(),config::verticalAnchor);
        controls.choice(compact,"horizontalAnchor","Horizontal anchor",HorizontalAnchor.values(),config::horizontalAnchor);
        controls.number(compact,"verticalYAdjustment","Vertical offset",config::verticalYAdjustment,0,200);
        controls.toggle(compact,"disableReordering","Keep hidden orb slots",config::disableReordering,false);
        controls.toggle(compact,"leaveEmptySpace","Keep preset container size",config::leaveEmptySpace,false);
        controls.toggle(compact,"enableOrbSwapping","Allow preset slot swaps",config::enableOrbSwapping,false);
        controls.tooltip("enableOrbSwapping","With Edit interface enabled, drag an orb onto another preset slot.");
        controls.heading(compact,"Legacy separate minimap");
        controls.toggle(compact,"enableMinimapOverlay","Show separate minimap",config::showMinimapInCompactView,false);
        JPanel legacy=controls.when(compact,config::showMinimapInCompactView);
        controls.toggle(legacy,"enableOverlayToggleOption","Include toggle action",config::showToggleOnMinimapButton,false);
        controls.toggle(legacy,"enableLogoutXOverlay","Show Logout-X",config::showOverlayLogoutX,false);
        controls.tooltip("enableMinimapOverlay","This legacy map does not support other plugins' minimap overlays. Free placement moves the native map.");
        controls.choice(p,"orbAppearance","Orb appearance",new String[]{"Number tabs","Circles only"},
            ()->config.hideOrbTabs()?"Circles only":"Number tabs",v->settings.set("hideOrbTabs",v.equals("Circles only")));
        JPanel circular=controls.when(p,config::hideOrbTabs);
        controls.choice(circular,"orbContent","Orb contents",OrbContent.values(),config::orbContent);
        controls.toggle(p,"disableOrbTimers","Enable Beta timers",config::disableOrbTimers,true);
        JPanel map=controls.section(p,"Minimap & compass",true);
        controls.toggle(map,"hideMinimap","Show minimap",config::hideMinimap,true);
        controls.toggle(map,"hideCompass","Show compass",config::hideCompass,true);
        JPanel freeMap=controls.when(map,()->settings.mode()==LayoutSettings.Mode.FREE);
        controls.choice(freeMap,"minimapFrame","Frame",MinimapFrame.values(),config::minimapFrame);
        JPanel rims=controls.when(freeMap,()->config.minimapFrame()==MinimapFrame.CIRCULAR);
        controls.tooltip("minimapFrame","Circular rim uses custom rings. Native keeps the attached compass circle, even when the compass is positioned separately.");
        controls.choice(rims,"orbRim","Shared circular rim",OrbRim.values(),config::orbRim);
        controls.tooltip("orbRim","Applies to custom circular minimap, compass, shield and orb frames.");
        controls.toggle(map,"hideMinimapButton","Minimap toggle button",config::hideMinimapToggle,true);
        JPanel eye=controls.when(map,()->!config.hideMinimapToggle());
        controls.choice(eye,"minimapButtonPlacement","Button location",TogglePlacement.values(),config::minimapTogglePlacement);
        controls.toggle(eye,"rightClickToggleButtons","Require right-click",config::rightClickToggleButtons,false);
        controls.toggle(map,"hideMinimapWithSidePanel","Hide with side panel",config::hideMinimapWithSidePanel,false);
        controls.tooltip("hideMinimapWithSidePanel","Hides the map and orbs with the side panel in resizable Modern mode.");
        JPanel hp=controls.section(p,"Hitpoints",false);
        controls.toggle(hp,"hideHp","Show hitpoints orb",config::hideHp,true);
        mirror(hp,"mirrorHitpoints",config::mirrorHitpoints);
        timer(hp,"orbHitpointsTimer",config::orbHitpointsTimer,"hitpointsTimerStyle",InterfaceID.Orbs.ORB_HEALTH);
        JPanel prayer=controls.section(p,"Prayer",false);
        controls.toggle(prayer,"hidePrayer","Show prayer orb",config::hidePray,true);
        mirror(prayer,"mirrorPrayer",config::mirrorPrayer);
        JPanel prayerTimer=timer(prayer,"orbPrayerTimer",config::orbPrayerTimer,"prayerTimerStyle",InterfaceID.Orbs.ORB_PRAYER);
        JPanel prayerDisplay=controls.when(prayerTimer,()->OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER)==NativeTimerStyle.TAG_METERS
            || OrbTimerStyles.resolve(config,InterfaceID.Orbs.ORB_PRAYER)==NativeTimerStyle.EIGHT_SLICES);
        controls.choice(prayerDisplay,"prayerOrbDisplay","Prayer display",PrayerDisplay.values(),config::prayerOrbDisplay);
        controls.tooltip("prayerOrbDisplay","Amount tracks remaining prayer; Countdown tracks the next point drained. Both uses two tag meters, or amount slices with a thin countdown arc.");
        controls.tooltip("orbPrayerTimer","Native low-prayer flashes and potion-dose reminders keep their own settings.");
        JPanel run=controls.section(p,"Run energy",false);
        controls.toggle(run,"hideRun","Show run energy orb",config::hideRun,true);
        mirror(run,"mirrorRun",config::mirrorRun);
        JPanel special=controls.section(p,"Special attack",false);
        controls.toggle(special,"hideSpec","Show special attack orb",config::hideSpec,true);
        mirror(special,"mirrorSpecial",config::mirrorSpecial);
        timer(special,"orbSpecialTimer",config::orbSpecialTimer,"specialTimerStyle",InterfaceID.Orbs.ORB_SPECENERGY);
        JPanel specialExtras=controls.when(special,()->!config.hideSpec());
        controls.toggle(specialExtras,"showShieldSpecialOrb","Shield special orb · Beta",config::showShieldSpecialOrb,false);
        controls.toggle(specialExtras,"hideExtraSpecialAttackBar","Extra special-attack bar",config::hideExtraSpecialAttackBar,true);
        controls.tooltip("hideExtraSpecialAttackBar","Show the additional special-attack meter below the orb, separate from its timer.");
        JPanel other=controls.section(p,"Other map controls",false);
        controls.toggle(other,"hideGravestone","Grave indicator",config::hideGravestone,true);
        controls.toggle(other,"hideXp","XP button",config::hideXp,true);
        controls.toggle(other,"hideWorld","World map",config::hideWorld,true);
        controls.toggle(other,"hideStore","Store",config::hideStore,true);
        controls.toggle(other,"hideActivity","Activity advisor",config::hideActivity,true);
        controls.toggle(other,"hideWiki","Wiki banner",config::hideWiki,true);
        controls.toggle(other,"hideLogoutX","Logout-X (Modern)",config::hideLogout,true);
        JPanel stacking=controls.section(p,"Stacking · front first",false);
        controls.orbOrder(stacking,config::orbStackOrder);
        controls.tooltip("orbStackOrder","The front orb receives clicks where orbs overlap. Icons, numbers and timers move together.");
    }

    private void generalPage(JPanel p)
    {
        controls.toggle(p,"editInterface","Edit interface",config::editInterface,false);
        controls.number(p,"editGrid","Grouping grid",config::editGrid,1,32);
        controls.tooltip("editInterface","Alt-drag to move; Shift-drag to group tabs. Shift-right-click a free control to align it to an edge. Alt-drag releases alignment. Detach custom anchors before edge alignment; move the grave indicator while a grave is active.");
        controls.heading(p,"Equipment · Beta");
        controls.toggle(p,"betaEquipmentPanel","Enhanced equipment panel",config::betaEquipmentPanel,false);
        JPanel equipment=controls.when(p,config::betaEquipmentPanel);
        controls.toggle(equipment,"betaEquipmentReducedTooltips","Reduced equipment tooltips",config::betaEquipmentReducedTooltips,false);
        controls.heading(p,"Classic borders");
        controls.toggle(p,"hideLeftBar","Left border",config::hideLeftBar,true);
        controls.toggle(p,"hideRightBar","Right border",config::hideRightBar,true);
        controls.button(p,"Reset positions and groups...",()->settings.set("resetLayoutPositions",true));
    }

    private void mirror(JPanel p,String key,java.util.function.BooleanSupplier value)
    {
        controls.toggle(controls.when(p,()->!config.hideOrbTabs()),key,"Mirror number tab",value,false);
        controls.tooltip(key,"Swap the orb and number tab while keeping symbols and numbers upright.");
    }
    private JPanel timer(JPanel p,String key,java.util.function.BooleanSupplier enabled,String style,int component)
    {
        JPanel timers=controls.when(p,()->!config.disableOrbTimers());
        controls.toggle(timers,key,"Show Beta timer",enabled,false);
        JPanel options=controls.when(timers,enabled);
        controls.choice(options,style,"Timer style",NativeTimerStyle.values(),()->OrbTimerStyles.resolve(config,component),
            value->settings.set(style,OrbTimerStyle.valueOf(value.name())));
        controls.tooltip(style,"Inset sweep shades the orb; Tag meters use small bars; Quiet rims use a thin arc; Eight slices use wedges. All styles support number tabs and circles. Tag meters sit below circles.");
        return options;
    }

}
