package com.interfacelayout.util;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public final class InterfaceWindows
{
    private static final int[] ROOTS = {
        InterfaceID.Bankmain.UNIVERSE, InterfaceID.BankpinKeypad.UNIVERSE,
        InterfaceID.Trademain.UNIVERSE,InterfaceID.Tradeconfirm.UNIVERSE,InterfaceID.Shopmain.UNIVERSE,
        InterfaceID.Collection.FRAME, InterfaceID.CollectionOverview.UNIVERSE,
        InterfaceID.SkillGuide.WINDOW, InterfaceID.SkillGuideV2.UNIVERSE, InterfaceID.Settings.UNIVERSE,
        InterfaceID.CaOverview.UNIVERSE, InterfaceID.CaTasks.UNIVERSE, InterfaceID.CaBosses.UNIVERSE,
        InterfaceID.CaBoss.UNIVERSE, InterfaceID.CaRewards.UNIVERSE,
        InterfaceID.Questjournal.UNIVERSE, InterfaceID.QuestjournalOverview.UNIVERSE,
        InterfaceID.AreaTask.UNIVERSE, InterfaceID.Journalscroll.UNIVERSE,
        InterfaceID.Questdisplay.UNIVERSE, InterfaceID.Questscroll.UNIVERSE, InterfaceID.QuestscrollSpeedrun.UNIVERSE
    };

    private InterfaceWindows() {}

    public static List<Rectangle> bounds(Client client)
    {
        List<Rectangle> result = new ArrayList<>();
        for (int id : ROOTS)
        {
            Widget window = client.getWidget(id);
            if (window == null || window.isHidden()) continue;
            Rectangle bounds = WidgetGeometry.bounds(window);
            if (!bounds.isEmpty()) result.add(bounds);
        }
        return List.copyOf(result);
    }

    public static boolean covered(Client client, Rectangle bounds)
    {
        return bounds(client).stream().anyMatch(window -> window.intersects(bounds));
    }
}
