package com.interfacelayout;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Keep Swing dialogs off the game thread and collapse repeated reset requests. */
@Singleton
public class LayoutResetConfirmation
{
    private final AtomicBoolean pending = new AtomicBoolean();

    public void request(Runnable confirmed)
    {
        if (!pending.compareAndSet(false, true)) return;
        // ConfigChanged is posted synchronously by the settings checkbox. Retain
        // only that event's source, without inspecting RuneLite's private UI.
        AWTEvent event = SwingUtilities.isEventDispatchThread() ? EventQueue.getCurrentEvent() : null;
        JCheckBox checkbox = event != null && event.getSource() instanceof JCheckBox
            ? (JCheckBox) event.getSource() : null;
        SwingUtilities.invokeLater(() -> {
            try
            {
                int result = showDialog();
                if (result == JOptionPane.YES_OPTION) confirmed.run();
            }
            finally
            {
                pending.set(false);
                // setSelected updates the view without firing another ActionEvent.
                if (checkbox != null) checkbox.setSelected(false);
            }
        });
    }

    protected int showDialog()
    {
        return JOptionPane.showConfirmDialog(null,
            "Reset all saved orb positions, menu positions and stone groups?\n"
                + "This affects Fixed, Resizable Classic and Resizable Modern.\n"
                + "Your visibility settings will be kept.",
            "Reset Interface Layout?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    }
}
