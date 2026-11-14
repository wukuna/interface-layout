package com.interfacelayout.anchors;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnchorPanelTest
{
    @Test public void editorFitsTheSidebarAndRendersItsOwnControls()throws Exception
    {
        AnchorCoordinator coordinator=new AnchorCoordinator();AnchorLayout layout=new AnchorLayout();AnchorLayout.Anchor anchor=new AnchorLayout.Anchor();anchor.name="Minimap and orbs";layout.anchors.add(anchor);
        Field field=AnchorCoordinator.class.getDeclaredField("view");field.setAccessible(true);field.set(coordinator,new AnchorCoordinator.Snapshot("test",layout,new AnchorGeometry.Result(),Map.of(),"",false,1));
        SwingUtilities.invokeAndWait(()->{
            AnchorPanel panel=new AnchorPanel(coordinator,new AnchorTransfers(),new AnchorTemplates(),new ExternalAnchorAdapter());
            panel.setSize(209,panel.getPreferredSize().height);layout(panel);assertTrue(panel.getHeight()>400);
            BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{java.io.File file=new java.io.File("build/previews/Anchors.png");file.getParentFile().mkdirs();ImageIO.write(image,"png",file);}catch(java.io.IOException failure){throw new AssertionError(failure);}
            check(panel,panel);
        });
    }
    @Test public void memberListSharesControlsAndRetainsMultipleSelectionsAfterReordering()throws Exception
    {
        AnchorCoordinator coordinator=new AnchorCoordinator();AnchorLayout layout=new AnchorLayout();AnchorLayout.Anchor anchor=new AnchorLayout.Anchor();anchor.name="Minimap and orbs";layout.anchors.add(anchor);
        for(String id:List.of("Prayer","Minimap","Compass","Special attack")){AnchorLayout.Member member=new AnchorLayout.Member();member.target=id;anchor.members.add(member);}
        Field field=AnchorCoordinator.class.getDeclaredField("view");field.setAccessible(true);field.set(coordinator,new AnchorCoordinator.Snapshot("test",layout,new AnchorGeometry.Result(),Map.of(),"",false,1));
        SwingUtilities.invokeAndWait(()->{
            AnchorPanel panel=new AnchorPanel(coordinator,new AnchorTransfers(),new AnchorTemplates(),new ExternalAnchorAdapter());
            JList<?> members=memberList(panel);assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,members.getSelectionMode());members.setSelectedIndices(new int[]{1,2});
            assertEquals(1,buttons(panel,"Up").size());assertEquals(1,buttons(panel,"Down").size());assertEquals(1,buttons(panel,"Detach").size());
            java.util.Collections.rotate(anchor.members.subList(0,3),-1);
            try{field.set(coordinator,new AnchorCoordinator.Snapshot("test",layout,new AnchorGeometry.Result(),Map.of(),"",false,2));var refresh=AnchorPanel.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(panel);}catch(Exception failure){throw new AssertionError(failure);}
            members=memberList(panel);assertArrayEquals(new int[]{0,1},members.getSelectedIndices());
            panel.setSize(209,panel.getPreferredSize().height);layout(panel);check(panel,panel);
            for(String text:List.of("Up","Down","Detach")){JButton button=buttons(panel,text).get(0);assertTrue(button.getWidth()>50);}
            BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{java.io.File file=new java.io.File("build/previews/AnchorMembers.png");file.getParentFile().mkdirs();ImageIO.write(image,"png",file);}catch(java.io.IOException failure){throw new AssertionError(failure);}
        });
    }
    private static JList<?> memberList(Container parent){for(Component child:parent.getComponents()){if(child instanceof JList && "anchorMembers".equals(child.getName()))return (JList<?>)child;if(child instanceof Container){JList<?> found=memberList((Container)child);if(found!=null)return found;}}return null;}
    private static List<JButton> buttons(Container parent,String text){List<JButton> result=new ArrayList<>();for(Component child:parent.getComponents()){if(child instanceof JButton && text.equals(((JButton)child).getText()))result.add((JButton)child);if(child instanceof Container)result.addAll(buttons((Container)child,text));}return result;}
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
    private static void check(Container root,Container parent){for(Component child:parent.getComponents()){if(child.isVisible() && child instanceof JComboBox){var point=SwingUtilities.convertPoint(child.getParent(),child.getLocation(),root);assertTrue(point.x+child.getWidth()<=root.getWidth());}if(child instanceof Container)check(root,(Container)child);}}
}
