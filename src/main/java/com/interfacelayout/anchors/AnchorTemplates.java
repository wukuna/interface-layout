package com.interfacelayout.anchors;

import com.google.gson.Gson;
import com.interfacelayout.InterfaceLayoutPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;

@Singleton
public final class AnchorTemplates
{
    public static final String KEY="anchorTemplate";
    private static final String REVISION="anchorTemplateRevision",HASH="anchorTemplateHash";
    interface Directory {Filepath get() throws IOException;}
    static final class Document
    {
        int version=1;
        String revision=UUID.randomUUID().toString();
        AnchorLayout layout;
    }
    static final class Files
    {
        private final Directory directory;
        private final Gson gson;
        Files(Directory directory,Gson gson){this.directory=directory;this.gson=gson;}
        static String name(String name) {
            String clean=name==null?"":name.trim().toLowerCase(java.util.Locale.ROOT);
            if(!clean.matches("[a-z0-9][a-z0-9 _-]{0,47}"))throw new IllegalArgumentException("Use 1–48 letters, numbers, spaces or hyphens.");
            return clean;
        }
        Document read(String name) throws IOException
        {
            try(InputStream input=directory.get().joinSegment(name(name)+".json").openInputStream()) {
                byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)throw new IOException("Template is too large");
                Document document=gson.fromJson(AnchorTransfers.parse(new String(bytes,StandardCharsets.UTF_8),"{}"),Document.class);
                if(document==null || document.version!=1 || document.revision==null || document.revision.length()>80 || document.layout==null)throw new IOException("Invalid template");
                document.layout.validate();return document;
            }
        }
        Document write(String name,AnchorLayout layout,String expected,boolean create) throws IOException
        {
            name=name(name);layout.validate();Filepath root=directory.get();root.createDirectories();
            try(FileChannel channel=root.joinSegment(name+".lock").openFileChannel(StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                FileLock lock=channel.tryLock()) {
                if(lock==null)throw new IOException("Template is busy; try again shortly.");
                Filepath target=root.joinSegment(name+".json");
                if(create && target.exists())throw new IllegalArgumentException("That template already exists. Link it or choose a new name.");
                if(!create && (!target.exists() || !read(name).revision.equals(expected)))throw new IllegalStateException("Template changed in another client. Link it again to reload, or unlink to keep your local edits.");
                Document document=new Document();document.layout=layout;String json=gson.toJson(document);
                if(json.length()>262144)throw new IllegalArgumentException("Template is too large");
                Filepath temporary=root.createTempFile("anchor-",".tmp");
                try {
                    temporary.write(json);
                    try {temporary.moveTo(target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(java.nio.file.AtomicMoveNotSupportedException unsupported){temporary.moveTo(target,StandardCopyOption.REPLACE_EXISTING);}
                } finally {temporary.deleteIfExists();}
                return document;
            }
        }
    }
    @Inject private InterfaceLayoutPlugin plugin;
    @Inject private AnchorCoordinator coordinator;
    @Inject private AnchorLayoutStore store;
    @Inject private ClientThread thread;
    @Inject private ConfigManager configs;
    @Inject private ScheduledExecutorService executor;
    @Inject private Gson gson;
    private Files files;
    private ScheduledFuture<?> poll;
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile boolean active;
    private volatile String status="";
    private String session="",linked="",base="",revision="";
    private boolean conflict;
    private volatile long operation;

    public void start() {
        active=true;files=new Files(plugin::anchorDirectory,gson);
        poll=executor.scheduleWithFixedDelay(()->thread.invokeLater(this::reconcile),2,2,TimeUnit.SECONDS);
    }
    public void stop() {active=false;if(poll!=null)poll.cancel(false);poll=null;operation++;}
    public String status() {return status;}
    public void create(String name) {explicit(name,true);}
    public void link(String name) {explicit(name,false);}
    public void unlink() {
        coordinator.command(()->{operation++;for(String key:new String[]{KEY,REVISION,HASH})configs.unsetConfiguration(AnchorLayoutStore.GROUP,key);linked="";base="";revision="";conflict=false;status="";});
    }
    private void explicit(String input,boolean create)
    {
        coordinator.command(()-> {
            String name=Files.name(input);AnchorCoordinator.Snapshot view=coordinator.snapshot();String token=view.session;
            AnchorLayout layout=store.snapshot();long expected=store.revision(),ticket=++operation;
            if(!busy.compareAndSet(false,true)){status="Template operation is still running.";return;}
            executor.execute(()-> {
                try {
                    if(!valid(token,ticket))return;
                    Document document=create?files.write(name,layout,null,true):files.read(name);
                    thread.invokeLater(()-> {
                        if(!valid(token,ticket))return;
                        if(store.revision()!=expected){status="Layout changed while loading. Try the template action again.";return;}
                        store.replace(document.layout);configs.setConfiguration(AnchorLayoutStore.GROUP,KEY,name);
                        session=token;linked=name;acknowledge(document);conflict=false;status="Linked: "+name;
                    });
                } catch(Exception failure){failure(token,ticket,failure);} finally {busy.set(false);}
            });
        });
    }
    private boolean valid(String token,long ticket) {return active && ticket==operation && token.equals(coordinator.snapshot().session);}
    private void failure(String token,long ticket,Exception failure)
    {
        thread.invokeLater(()->{if(valid(token,ticket)){status=failure instanceof IllegalStateException?failure.getMessage():"Template unavailable; local layout retained. "+(failure.getMessage()==null?"":failure.getMessage());if(failure instanceof IllegalStateException)conflict=true;}});
    }
    private void reconcile()
    {
        if(!active || busy.get())return;
        AnchorCoordinator.Snapshot view=coordinator.snapshot();String selected=configs.getConfiguration(AnchorLayoutStore.GROUP,KEY);
        selected=selected==null?"":selected;
        if(!session.equals(view.session) || !linked.equals(selected)) {session=view.session;linked=selected;base=saved(HASH);revision=saved(REVISION);conflict=false;status="";operation++;}
        if(linked.isEmpty() || conflict || view.session.isEmpty())return;
        String token=session,name=linked,json=store.exportLayout(),baseline=base,expectedRevision=revision;
        long expected=store.revision(),ticket=operation;
        AnchorLayout layout=store.snapshot();
        if(!busy.compareAndSet(false,true))return;
        executor.execute(()-> {
            try {
                if(!valid(token,ticket))return;
                Document remote=files.read(name);
                if(!baseline.isEmpty() && !hash(json).equals(baseline)) {
                    if(!remote.revision.equals(expectedRevision))throw new IllegalStateException("Template changed in another client. Link it again to reload, or unlink to keep your local edits.");
                    if(!valid(token,ticket))return;
                    remote=files.write(name,layout,expectedRevision,false);
                }
                Document result=remote;
                thread.invokeLater(()-> {
                    if(!valid(token,ticket) || store.revision()!=expected)return;
                    String updated=gson.toJson(result.layout);
                    if(!json.equals(updated))store.replace(result.layout);
                    acknowledge(result);status="Linked: "+name;
                });
            } catch(Exception failure){failure(token,ticket,failure);} finally {busy.set(false);}
        });
    }
    private String saved(String key) {String value=configs.getConfiguration(AnchorLayoutStore.GROUP,key);return value==null?"":value;}
    private void acknowledge(Document document)
    {
        base=hash(gson.toJson(document.layout));revision=document.revision;
        if(!base.equals(saved(HASH)))configs.setConfiguration(AnchorLayoutStore.GROUP,HASH,base);
        if(!revision.equals(saved(REVISION)))configs.setConfiguration(AnchorLayoutStore.GROUP,REVISION,revision);
    }
    private static String hash(String value) {return com.google.common.hash.Hashing.sha256().hashString(value,StandardCharsets.UTF_8).toString();}
}
