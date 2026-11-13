package com.interfacelayout.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.ColorScheme;

final class LayoutControls
{
    private final List<Runnable> updates=new ArrayList<>();
    private final LayoutSettings settings;
    private boolean refreshing;

    LayoutControls(LayoutSettings settings) {this.settings=settings;}

    static JPanel column()
    {
        JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);panel.setAlignmentX(0);
        return panel;
    }

    void refresh()
    {
        refreshing=true;
        try {for(Runnable update:updates)update.run();}
        finally {refreshing=false;}
    }

    void heading(JPanel parent,String text)
    {
        JLabel label=new JLabel(text);label.setForeground(ColorScheme.BRAND_ORANGE);
        label.setBorder(BorderFactory.createEmptyBorder(12,0,5,0));label.setAlignmentX(0);parent.add(label);
    }

    void hint(JPanel parent,String text)
    {
        JLabel label=new JLabel("<html><body style='width:130px'>"+text+"</body></html>");
        label.setForeground(Color.LIGHT_GRAY);label.setBorder(BorderFactory.createEmptyBorder(3,0,8,0));label.setAlignmentX(0);parent.add(label);
    }

    JPanel when(JPanel parent,BooleanSupplier visible)
    {
        JPanel panel=column();panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,2,0,0,new Color(78,76,66)),BorderFactory.createEmptyBorder(2,7,4,0)));
        parent.add(panel);updates.add(()->panel.setVisible(visible.getAsBoolean()));return panel;
    }

    void toggle(JPanel parent,String key,String label,BooleanSupplier current,boolean inverted)
    {
        JPanel row=new JPanel(new BorderLayout(6,0));row.setOpaque(false);row.setAlignmentX(0);
        JLabel text=new JLabel("<html><body style='width:120px'>"+label+"</body></html>");text.setForeground(Color.WHITE);
        JCheckBox box=new JCheckBox();box.setOpaque(false);box.setName(key);box.getAccessibleContext().setAccessibleName(label);
        row.add(text,BorderLayout.CENTER);row.add(box,BorderLayout.EAST);row.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));parent.add(row);
        updates.add(()->box.setSelected(current.getAsBoolean()!=inverted));
        box.addActionListener(e->{if(!refreshing)settings.set(key,box.isSelected()!=inverted);});
    }

    <T> void choice(JPanel parent,String name,String label,T[] values,Supplier<T> current,Consumer<T> change)
    {
        JComboBox<T> combo=new JComboBox<>(values);combo.setName(name);combo.getAccessibleContext().setAccessibleName(label);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE,25));combo.setAlignmentX(0);combo.setPreferredSize(new Dimension(195,25));
        label(parent,label,combo);
        updates.add(()->{T value=current.get();combo.setSelectedItem(value);combo.setToolTipText(value==null?label:value.toString());});
        combo.addActionListener(e->{if(!refreshing && combo.getSelectedIndex()>=0)change.accept(values[combo.getSelectedIndex()]);});
    }

    <T> void choice(JPanel parent,String key,String label,T[] values,Supplier<T> current)
    {
        choice(parent,key,label,values,current,value->settings.set(key,value));
    }

    void number(JPanel parent,String key,String label,IntSupplier current,int min,int max)
    {
        JSpinner spinner=new JSpinner(new SpinnerNumberModel(min,min,max,1));spinner.setName(key);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE,25));spinner.setAlignmentX(0);label(parent,label,spinner);
        updates.add(()->spinner.setValue(Math.max(min,Math.min(max,current.getAsInt()))));
        spinner.addChangeListener(e->{if(!refreshing)settings.set(key,spinner.getValue());});
    }

    void button(JPanel parent,String text,Runnable action)
    {
        JButton button=new JButton(text);button.setAlignmentX(0);button.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        button.addActionListener(e->action.run());parent.add(button);
    }

    private void label(JPanel parent,String text,JComponent control)
    {
        JLabel label=new JLabel(text);label.setForeground(Color.WHITE);label.setAlignmentX(0);label.setLabelFor(control);label.setBorder(BorderFactory.createEmptyBorder(5,0,3,0));
        parent.add(label);parent.add(control);
    }
}
