package com.interfacelayout.layout.orbs;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.*;

public class EdgeAlignmentTest
{
    @Test public void cornersAlignTheVisibleCircleInsteadOfItsInvisibleNumberTab()
    {
        Rectangle parent=new Rectangle(100,60,57,34),circle=new Rectangle(123,60,34,34);
        assertEquals(new Point(-23,0),EdgeAlignment.TOP_LEFT.place(parent,circle,parent.getLocation(),1000,700));
        assertEquals(new Point(943,666),EdgeAlignment.BOTTOM_RIGHT.place(parent,circle,parent.getLocation(),1000,700));
        assertEquals(new Point(708,470),EdgeAlignment.BOTTOM_RIGHT.place(parent,circle,parent.getLocation(),765,504));
    }

    @Test public void decorationExtensionsStayOnscreenAndSingleEdgesPreserveTheOtherAxis()
    {
        Rectangle parent=new Rectangle(100,60,57,34),decorated=new Rectangle(123,60,34,45);
        assertEquals(new Point(100,655),EdgeAlignment.BOTTOM.place(parent,decorated,parent.getLocation(),1000,700));
        assertEquals(new Point(-23,60),EdgeAlignment.LEFT.place(parent,decorated,parent.getLocation(),1000,700));
        assertEquals(new Point(-23,655),EdgeAlignment.LEFT.place(parent,decorated,new Point(-300,900),1000,700));
        assertNull(EdgeAlignment.parse("obsolete"));
    }
}
