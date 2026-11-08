package com.interfacelayout.layout.menu;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import javax.inject.Singleton;

@Singleton
public final class MenuIconCache
{
    private final Executor executor;
    private final Map<BufferedImage, Source> sources = new LinkedHashMap<>(32, .75f, true);
    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private boolean working;

    public MenuIconCache() { this(ForkJoinPool.commonPool()); }
    public MenuIconCache(Executor executor) { this.executor = executor; }

    public synchronized BufferedImage get(BufferedImage image, int width, int height)
    {
        if (image == null || width < 1 || height < 1 || width > 256 || height > 256
            || image.getWidth() > 256 || image.getHeight() > 256) return null;
        Source source = sources.get(image);
        if (source == null)
        {
            BufferedImage snapshot = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            snapshot.setRGB(0, 0, image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()), 0, image.getWidth());
            source = new Source(snapshot); sources.put(image, source);
            if (sources.size() > 32)
            {
                BufferedImage oldest = sources.keySet().iterator().next();
                Source retired = sources.remove(oldest); retired.retired = true;
                pending.removeIf(request -> request.source == retired);
            }
        }
        int size = width << 16 | height;
        Request request = source.sizes.get(size);
        if (request == null)
        {
            if (pending.size() >= 32) return null;
            request = new Request(source, size, width, height);
            source.sizes.put(size, request); pending.addLast(request);
            if (source.sizes.size() > 4)
            {
                int oldest = source.sizes.keySet().iterator().next();
                pending.remove(source.sizes.remove(oldest));
            }
            if (!working)
            {
                working = true;
                try { executor.execute(this::drain); }
                catch (RuntimeException rejected) { working = false; clear(); }
            }
        }
        return request.image;
    }

    public synchronized void clear()
    {
        sources.values().forEach(source -> source.retired = true);
        sources.clear(); pending.clear();
    }

    private void drain()
    {
        while (true)
        {
            Request request;
            synchronized (this)
            {
                request = pending.pollFirst();
                if (request == null) { working = false; return; }
                if (request.source.retired || request.source.sizes.get(request.size) != request) continue;
            }
            BufferedImage result = null;
            try
            {
                if (request.source.artwork == null) request.source.artwork = MenuIconArtwork.trace(request.source.snapshot);
                result = request.source.artwork.render(request.width, request.height);
            }
            catch (RuntimeException unavailable) { }
            synchronized (this)
            {
                if (!request.source.retired && request.source.sizes.get(request.size) == request)
                    request.image = result;
            }
        }
    }

    private static final class Source
    {
        final BufferedImage snapshot;
        final Map<Integer, Request> sizes = new LinkedHashMap<>(4, .75f, true);
        MenuIconArtwork artwork;
        boolean retired;
        Source(BufferedImage snapshot) { this.snapshot = snapshot; }
    }

    private static final class Request
    {
        final Source source;
        final int size, width, height;
        BufferedImage image;
        Request(Source source, int size, int width, int height)
        { this.source = source; this.size = size; this.width = width; this.height = height; }
    }
}
