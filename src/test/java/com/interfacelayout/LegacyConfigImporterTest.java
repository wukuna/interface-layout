package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.util.LegacyConfigImporter;
import java.util.List;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class LegacyConfigImporterTest
{
    private LegacyConfigImporter importer(ConfigManager configs)
    {
        return Guice.createInjector(new AbstractModule() {
            @Override protected void configure() { bind(ConfigManager.class).toInstance(configs); }
        }).getInstance(LegacyConfigImporter.class);
    }
    @Test public void preservesExistingDestinationAndSourceConfiguration()
    {
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfigurationKeys("compactorbs.")).thenReturn(List.of("compactorbs.hideHp", "compactorbs.hideRun"));
        when(configs.getConfigurationKeys("menustoneshider.")).thenReturn(List.of("menustoneshider.hideTopMenu"));
        when(configs.getConfiguration("compactorbs", "hideHp")).thenReturn("true");
        when(configs.getConfiguration("interfacelayout", "hideRun")).thenReturn("false");
        when(configs.getConfiguration("menustoneshider", "hideTopMenu")).thenReturn("true");
        importer(configs).importOnce();
        verify(configs).setConfiguration("interfacelayout", "hideHp", "true");
        verify(configs).setConfiguration("interfacelayout", "hideTopMenu", "true");
        verify(configs, never()).setConfiguration(eq("interfacelayout"), eq("hideRun"), any());
        verify(configs, never()).unsetConfiguration(anyString(), anyString());
    }
    @Test public void completedImportDoesNotReadOrCopyLegacyValuesAgain()
    {
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration("interfacelayout", "legacyImported", Boolean.class)).thenReturn(true);
        importer(configs).importOnce();
        verify(configs, never()).getConfigurationKeys(anyString());
    }
}
