package com.interfacelayout;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class InterfaceLayoutPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(InterfaceLayoutPlugin.class);
        RuneLite.main(args);
    }
}
