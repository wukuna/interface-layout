package com.interfacelayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import static org.mockito.Mockito.*;

/** Use the actual handler without adding classes to RuneLite's signed package. */
final class RealConfigProxy
{
    private RealConfigProxy() {}
    static <T extends Config> T create(Class<T> type, ConfigManager manager)
    {
        try
        {
            // Only persistence is mocked; use RuneLite's actual value conversion too.
            Method parser = ConfigManager.class.getDeclaredMethod("stringToObject", String.class, Type.class);
            parser.setAccessible(true);
            when(parser.invoke(manager, anyString(), any(Type.class))).thenCallRealMethod();
            Class<?> handlerClass = Class.forName("net.runelite.client.config.ConfigInvocationHandler");
            Constructor<?> constructor = handlerClass.getDeclaredConstructor(ConfigManager.class);
            constructor.setAccessible(true);
            InvocationHandler handler = (InvocationHandler) constructor.newInstance(manager);
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
        catch (ReflectiveOperationException exception)
        {
            throw new AssertionError("Could not construct RuneLite's configuration handler", exception);
        }
    }
}
