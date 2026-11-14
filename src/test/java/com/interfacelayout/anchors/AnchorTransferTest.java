package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnchorTransferTest
{
    @Test public void explicitImportUsesOriginIntentAndReportsModeAliasesAndUnknowns()
    {
        var result=AnchorTransfers.convert("[{\"id\":1,\"name\":\"Orbs\",\"x\":3,\"y\":4,\"originX\":800,\"originY\":500,\"originW\":1000,\"originH\":700,\"width\":150,\"height\":100,\"constraint\":\"BOTTOM_RIGHT\"}]",
            "{\"classic prayer\":1,\"modern prayer\":1,\"missing\":1}","{}",Map.of("layout.orb.prayer",List.of("classic prayer","modern prayer")),new Dimension(500,400),2);
        assertEquals(1,result.mapped);assertEquals(2,result.warnings.size());assertEquals(-50,result.layout.anchors.get(0).x);assertEquals(-100,result.layout.anchors.get(0).y);
        assertEquals("layout.orb.prayer",result.layout.anchors.get(0).members.get(0).target);
    }
    @Test public void ambiguousAliasesAreNeverSilentlyAssigned()
    {
        var result=AnchorTransfers.convert("[{\"id\":1}]","{\"duplicate\":1}","{}",Map.of("a",List.of("duplicate"),"b",List.of("duplicate")),new Dimension(800,600),2);
        assertEquals(0,result.mapped);assertTrue(result.warnings.get(0).contains("Unmapped"));
    }
    @Test(expected=IllegalArgumentException.class) public void deeplyNestedForeignConfigIsRejected() {AnchorTransfers.parse("[".repeat(17)+"]".repeat(17),"[]");}
    @Test(expected=IllegalArgumentException.class) public void duplicateBoxIdsAreRejected() {AnchorTransfers.convert("[{\"id\":1},{\"id\":1}]","{}","{}",Map.of(),new Dimension(800,600),2);}
    @Test(expected=IllegalArgumentException.class) public void templateNamesCannotEscapeOwnedDirectory() {AnchorTemplates.Files.name("../outside");}
}
