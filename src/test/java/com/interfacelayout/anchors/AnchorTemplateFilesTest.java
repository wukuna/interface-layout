package com.interfacelayout.anchors;

import com.google.gson.Gson;
import java.io.IOException;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AnchorTemplateFilesTest
{
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private AnchorTemplates.Files files()throws IOException{return new AnchorTemplates.Files(()->Filepath.Unchecked.getRooted(temporary.getRoot().toPath()),new Gson());}
    private AnchorLayout layout(String name){AnchorLayout layout=new AnchorLayout();AnchorLayout.Anchor anchor=new AnchorLayout.Anchor();anchor.name=name;layout.anchors.add(anchor);return layout;}
    @Test public void revisionComparePreventsSilentOverwriteAcrossClients()throws Exception
    {
        AnchorTemplates.Files a=files(),b=files();var first=a.write("shared",layout("first"),null,true);var snapshot=b.read("shared");
        var second=a.write("shared",layout("second"),first.revision,false);assertNotEquals(first.revision,second.revision);
        try {b.write("shared",layout("stale"),snapshot.revision,false);fail();}catch(IllegalStateException expected){}
        assertEquals("second",b.read("shared").layout.anchors.get(0).name);
    }
    @Test public void creationCannotReplaceAnExistingTemplate()throws Exception
    {
        AnchorTemplates.Files files=files();files.write("shared",layout("first"),null,true);
        try{files.write("shared",layout("second"),null,true);fail();}catch(IllegalArgumentException expected){}
        assertEquals("first",files.read("shared").layout.anchors.get(0).name);
    }
    @Test public void missingAndUnknownTemplatesLeaveCallersLayoutUntouched()throws Exception
    {
        AnchorTemplates.Files files=files();try{files.read("missing");fail();}catch(IOException expected){}
        Filepath.Unchecked.getRooted(temporary.getRoot().toPath()).joinSegment("future.json").write("{\"version\":2}");
        try{files.read("future");fail();}catch(IOException expected){}
    }
}
