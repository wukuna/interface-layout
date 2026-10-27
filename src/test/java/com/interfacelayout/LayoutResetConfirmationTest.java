package com.interfacelayout;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LayoutResetConfirmationTest
{
    @Test public void yesNoAndCloseClearCheckboxWithoutFiringAnotherAction() throws Exception
    {
        for (int result : new int[]{JOptionPane.YES_OPTION, JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION})
        {
            Runnable accepted = mock(Runnable.class);
            LayoutResetConfirmation confirmation = new LayoutResetConfirmation() {
                @Override protected int showDialog() { return result; }
            };
            JCheckBox checkbox = new JCheckBox();
            java.awt.event.ActionListener listener = mock(java.awt.event.ActionListener.class);
            checkbox.addActionListener(listener);
            checkbox.setSelected(true);
            // Dispatch a real Swing event whose source is the reset checkbox.
            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                new java.awt.event.InvocationEvent(checkbox, () -> confirmation.request(accepted)));
            SwingUtilities.invokeAndWait(() -> {});
            SwingUtilities.invokeAndWait(() -> {});
            assertFalse(checkbox.isSelected());
            verifyNoInteractions(listener);
            verify(accepted, times(result == JOptionPane.YES_OPTION ? 1 : 0)).run();
            checkbox.setSelected(true);
            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                new java.awt.event.InvocationEvent(checkbox, () -> confirmation.request(accepted)));
            SwingUtilities.invokeAndWait(() -> {});
            SwingUtilities.invokeAndWait(() -> {});
            assertFalse(checkbox.isSelected());
            verify(accepted, times(result == JOptionPane.YES_OPTION ? 2 : 0)).run();
        }
    }
}
