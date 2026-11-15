package com.interfacelayout.anchors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import static com.interfacelayout.anchors.AnchorLayout.*;

public final class AnchorPanel extends JPanel
{
    private final AnchorCoordinator coordinator;
    private final AnchorTransfers transfers;
    private final AnchorTemplates templates;
    private final DefaultListModel<Anchor> items=new DefaultListModel<>();
    private final JList<Anchor> list=new JList<>(items);
    private final JPanel details=column();
    private final JLabel status=new JLabel();
    private final Timer timer;
    private long revision=Long.MIN_VALUE;
    private String session="";
    private boolean refreshing;
    private JComboBox<AnchorGeometry.Target> availableControl;
    private String memberAnchor="";
    private List<String> selectedMembers=List.of();
    private String formKey="";
    private final List<Consumer<Anchor>> fieldUpdates=new ArrayList<>(),memberUpdates=new ArrayList<>();

    public AnchorPanel(AnchorCoordinator coordinator,AnchorTransfers transfers,AnchorTemplates templates,ExternalAnchorAdapter external)
    {
        this.coordinator=coordinator;this.transfers=transfers;this.templates=templates;
        setLayout(new BorderLayout(0,6));setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);
        JPanel top=column();
        JCheckBox edit=new JCheckBox("Edit anchors");edit.setToolTipText("Hold your RuneLite drag hotkey (normally Alt) to drag controls, other movable overlays or infobox groups into anchors, or outside to detach. No selection under Other overlays is needed. Drag an anchor header to move it, or its corner to resize.");
        edit.addActionListener(event->coordinator.editing(edit.isSelected()));top.add(edit);
        JLabel dragHint=new JLabel("Drag overlays in with your drag hotkey.");dragHint.setToolTipText(edit.getToolTipText());top.add(dragHint);
        list.setVisibleRowCount(4);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);top.add(new JScrollPane(list));
        top.add(row(button("Add",this::add),button("Undo",coordinator::undo)));
        top.add(row(button("Duplicate",this::duplicate),button("Delete",this::delete)));
        list.addListSelectionListener(event->{if(!event.getValueIsAdjusting() && !refreshing)showSelection();});
        add(top,BorderLayout.NORTH);add(details,BorderLayout.CENTER);
        JPanel bottom=column();status.setForeground(new Color(230,185,90));bottom.add(status);
        bottom.add(row(button("Import…",this::importJson),button("Export…",this::exportJson)));
        bottom.add(button("Import Custom Anchors…",()->transfers.preview(this)));
        bottom.add(button("Shared template…",this::template));
        bottom.add(button("Other overlays…",()->external.choose(this)));
        bottom.add(button("Restore backup",coordinator::restoreBackup));
        add(bottom,BorderLayout.SOUTH);
        timer=new Timer(400,event->{refresh();edit.setSelected(coordinator.editing());});
        refresh();
    }
    @Override public void addNotify() {super.addNotify();coordinator.catalogActive(true);timer.start();}
    @Override public void removeNotify() {timer.stop();coordinator.catalogActive(false);super.removeNotify();}
    private void refresh()
    {
        AnchorCoordinator.Snapshot view=coordinator.snapshot();
        String text=view.message.isEmpty()?templates.status():view.message;
        if(text.isEmpty() && !view.geometry.overflow.isEmpty())text="Some members exceed the canvas.";
        status.setText(text.isEmpty()?" ":"<html>"+escape(text)+"</html>");
        if(revision==view.revision && session.equals(view.session)){refreshAvailable();return;}
        Anchor selected=list.getSelectedValue();String id=selected==null?null:selected.id;
        if(!session.equals(view.session))id=null;
        revision=view.revision;session=view.session;refreshing=true;
        items.clear();for(Anchor anchor:view.layout.anchors)items.addElement(anchor);
        int index=0;for(int n=0;n<items.size();n++)if(items.get(n).id.equals(id))index=n;
        if(!items.isEmpty())list.setSelectedIndex(index);refreshing=false;showSelection();
    }
    private void showSelection()
    {
        Anchor anchor=list.getSelectedValue();
        String key=session+"/"+(anchor==null?"":anchor.id+"/"+anchor.parent+"/"+anchor.arrangement+"/"+anchor.members.stream().map(m->m.target+":"+m.reserveHidden).collect(java.util.stream.Collectors.joining("|")))+coordinator.snapshot().targets.keySet();
        if(anchor!=null && key.equals(formKey)) {
            refreshing=true;
            try {fieldUpdates.forEach(update->update.accept(anchor));memberUpdates.forEach(update->update.accept(anchor));}
            finally {refreshing=false;}
            refreshAvailable();return;
        }
        formKey=key;fieldUpdates.clear();memberUpdates.clear();details.removeAll();availableControl=null;
        if(anchor==null) {details.add(new JLabel("Add an anchor to begin."));revalidate();repaint();return;}
        String editingSession=session;
        Consumer<Consumer<Anchor>> edit=change->{if(!refreshing && editingSession.equals(coordinator.snapshot().session))coordinator.edit(next->{Anchor a=next.find(anchor.id);if(a!=null)change.accept(a);});};
        JTextField name=new JTextField(anchor.name);field(details,"Name",name);
        JCheckBox enabled=new JCheckBox("Enabled",anchor.enabled),locked=new JCheckBox("Locked",anchor.locked);
        details.add(row(enabled,locked));
        JComboBox<Reference> reference=new JComboBox<>(Reference.values());reference.setSelectedItem(anchor.reference);field(details,"Relative to",reference);
        JComboBox<String> parent=new JComboBox<>();parent.addItem("Canvas");
        List<String> parents=new ArrayList<>();parents.add("");
        coordinator.snapshot().targets.values().forEach(target->{parent.addItem(target.name);parents.add(target.id);});
        if(!parents.contains(anchor.parent)) {parent.addItem(anchor.parent+" (unavailable)");parents.add(anchor.parent);}
        parent.setSelectedIndex(parents.indexOf(anchor.parent));field(details,"Parent",parent);
        JSpinner x=number(anchor.x,-32768,32768),y=number(anchor.y,-32768,32768),w=number(anchor.width,1,16384),h=number(anchor.height,1,16384);
        field(details,"X / Y",row(x,y));field(details,"Width / height",row(w,h));
        JComboBox<Arrangement> arrangement=new JComboBox<>(Arrangement.values());arrangement.setSelectedItem(anchor.arrangement);field(details,"Arrange",arrangement);
        JSpinner padding=number(anchor.padding,0,100),gap=number(anchor.gap,0,100);field(details,"Padding",padding);
        JPanel ordered=column();field(ordered,"Spacing",gap);
        JComboBox<Alignment> alignment=new JComboBox<>(Alignment.values());alignment.setSelectedItem(anchor.alignment);field(ordered,"Alignment",alignment);details.add(ordered);
        ordered.setVisible(anchor.arrangement!=Arrangement.FREE);
        JCheckBox background=new JCheckBox("Background",anchor.background),border=new JCheckBox("Border",anchor.border);
        details.add(row(background,border));
        int[] colors={anchor.backgroundColor,anchor.borderColor};
        details.add(row(button("Fill color",()->color(colors,0,value->edit.accept(a->a.backgroundColor=value))),button("Border color",()->color(colors,1,value->edit.accept(a->a.borderColor=value)))));
        Runnable rename=()->{String value=name.getText().trim();if(!value.isEmpty())edit.accept(a->a.name=value);};
        name.addActionListener(event->rename.run());
        name.addFocusListener(new java.awt.event.FocusAdapter(){@Override public void focusLost(java.awt.event.FocusEvent event){rename.run();}});
        name.setToolTipText("Saved when you press Enter or leave this field.");
        enabled.addActionListener(event->{boolean value=enabled.isSelected();edit.accept(a->a.enabled=value);});
        locked.addActionListener(event->{boolean value=locked.isSelected();edit.accept(a->a.locked=value);});
        reference.addActionListener(event->{Reference value=(Reference)reference.getSelectedItem();edit.accept(a->a.reference=value);});
        parent.addActionListener(event->{String value=parents.get(parent.getSelectedIndex());edit.accept(a->a.parent=value);});
        arrangement.addActionListener(event->{Arrangement value=(Arrangement)arrangement.getSelectedItem();ordered.setVisible(value!=Arrangement.FREE);edit.accept(a->a.arrangement=value);});
        alignment.addActionListener(event->{Alignment value=(Alignment)alignment.getSelectedItem();edit.accept(a->a.alignment=value);});
        background.addActionListener(event->{boolean value=background.isSelected();edit.accept(a->a.background=value);});
        border.addActionListener(event->{boolean value=border.isSelected();edit.accept(a->a.border=value);});
        changed(x,"anchorX",value->edit.accept(a->a.x=value));changed(y,"anchorY",value->edit.accept(a->a.y=value));
        changed(w,"anchorWidth",value->edit.accept(a->a.width=value));changed(h,"anchorHeight",value->edit.accept(a->a.height=value));
        changed(padding,"anchorPadding",value->edit.accept(a->a.padding=value));changed(gap,"anchorGap",value->edit.accept(a->a.gap=value));
        fieldUpdates.add(a->{
            if(!name.isFocusOwner())name.setText(a.name);enabled.setSelected(a.enabled);locked.setSelected(a.locked);
            reference.setSelectedItem(a.reference);parent.setSelectedIndex(parents.indexOf(a.parent));arrangement.setSelectedItem(a.arrangement);alignment.setSelectedItem(a.alignment);
            update(x,a.x);update(y,a.y);update(w,a.width);update(h,a.height);update(padding,a.padding);update(gap,a.gap);
            background.setSelected(a.background);border.setSelected(a.border);colors[0]=a.backgroundColor;colors[1]=a.borderColor;
        });
        details.add(new JLabel("Members"));
        members(anchor);
        JComboBox<AnchorGeometry.Target> available=new JComboBox<>();availableControl=available;refreshAvailable();
        details.add(available);details.add(button("Attach control",()-> {
            AnchorGeometry.Target target=(AnchorGeometry.Target)available.getSelectedItem();if(target==null)return;
            Rectangle bounds=coordinator.snapshot().geometry.anchors.get(anchor.id);
            coordinator.edit(next->{Anchor a=next.find(anchor.id);if(a==null)return;Member m=new Member();m.target=target.id;
                m.width=Math.max(1,target.bounds.width);m.height=Math.max(1,target.bounds.height);
                if(bounds!=null) {m.x=Math.max(0,target.bounds.x-bounds.x-a.padding);m.y=Math.max(0,target.bounds.y-bounds.y-a.padding);}
                a.members.add(m);});
        }));
        revalidate();repaint();
    }
    private void refreshAvailable()
    {
        if(availableControl==null)return;
        java.util.Set<String> owned=new java.util.HashSet<>();coordinator.snapshot().layout.anchors.forEach(a->a.members.forEach(m->owned.add(m.target)));
        List<AnchorGeometry.Target> targets=new ArrayList<>();coordinator.snapshot().targets.values().stream().filter(target->!owned.contains(target.id)).forEach(targets::add);
        boolean same=targets.size()==availableControl.getItemCount();for(int i=0;same&&i<targets.size();i++)same=targets.get(i).id.equals(availableControl.getItemAt(i).id);
        if(same)return;
        AnchorGeometry.Target selected=(AnchorGeometry.Target)availableControl.getSelectedItem();availableControl.removeAllItems();
        for(AnchorGeometry.Target target:targets){availableControl.addItem(target);if(selected!=null&&target.id.equals(selected.id))availableControl.setSelectedItem(target);}
    }
    private void members(Anchor anchor)
    {
        if(!memberAnchor.equals(anchor.id)){memberAnchor=anchor.id;selectedMembers=List.of();}
        JList<Member> members=new JList<>(new java.util.Vector<>(anchor.members));members.setName("anchorMembers");members.setVisibleRowCount(5);
        members.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);members.setToolTipText("Ctrl-click or Shift-click to select multiple controls.");
        members.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focused){
            Member member=(Member)value;AnchorGeometry.Target target=coordinator.snapshot().targets.get(member.target);
            JLabel label=(JLabel)super.getListCellRendererComponent(list,target==null?member.target+" (unavailable)":target.name,index,selected,focused);
            label.setToolTipText(target==null?"Unavailable · assignment retained":target.state.toString());return label;
        }});
        int[] selection=java.util.stream.IntStream.range(0,anchor.members.size()).filter(i->selectedMembers.contains(anchor.members.get(i).target)).toArray();
        if(selection.length>0)members.setSelectedIndices(selection);else if(!anchor.members.isEmpty())members.setSelectedIndex(0);
        JScrollPane scroll=new JScrollPane(members);scroll.setPreferredSize(new Dimension(180,102));scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,120));details.add(scroll);
        JPanel options=column();
        JButton up=button("Up",()->coordinator.reorderMembers(anchor.id,selection(members),-1));
        JButton down=button("Down",()->coordinator.reorderMembers(anchor.id,selection(members),1));
        JButton detach=button("Detach",()->coordinator.detach(selection(members)));
        details.add(row(up,down,detach));details.add(options);
        Runnable refresh=()->{
            selectedMembers=selection(members);boolean selected=!selectedMembers.isEmpty();up.setEnabled(selected);down.setEnabled(selected);detach.setEnabled(selected);
            options.removeAll();memberUpdates.clear();
            Anchor current=coordinator.snapshot().layout.find(anchor.id);
            List<Member> chosen=new ArrayList<>();if(current!=null)for(Member member:current.members)if(selectedMembers.contains(member.target))chosen.add(member);
            if(selected) {
                boolean all=chosen.stream().allMatch(member->member.reserveHidden),mixed=!all && chosen.stream().anyMatch(member->member.reserveHidden);
                JCheckBox reserve=new JCheckBox("Reserve hidden space",all);reserve.setToolTipText(mixed?"Mixed values. Toggle to apply to all selected controls.":"Apply to selected controls.");
                reserve.addActionListener(e->{boolean value=reserve.isSelected();List<String> ids=selection(members);coordinator.edit(next->{Anchor a=next.find(anchor.id);if(a!=null)a.members.stream().filter(m->ids.contains(m.target)).forEach(m->m.reserveHidden=value);});});options.add(reserve);
                if(chosen.size()==1 && anchor.arrangement==Arrangement.FREE) {
                    Member member=chosen.get(0);JSpinner x=number(member.x,-32768,32768),y=number(member.y,-32768,32768);field(options,"X / Y",row(x,y));
                    String editingSession=session;
                    java.util.function.BiConsumer<Boolean,Integer> offset=(horizontal,value)->{
                        if(!refreshing && editingSession.equals(coordinator.snapshot().session))coordinator.edit(next->{Anchor a=next.find(anchor.id);if(a!=null)a.members.stream().filter(m->m.target.equals(member.target)).forEach(m->{if(horizontal)m.x=value;else m.y=value;});});
                    };
                    changed(x,"memberX",value->offset.accept(true,value));changed(y,"memberY",value->offset.accept(false,value));
                    memberUpdates.add(a->a.members.stream().filter(m->m.target.equals(member.target)).findFirst().ifPresent(m->{update(x,m.x);update(y,m.y);}));
                }
            }
            revalidate();repaint();
        };
        members.addListSelectionListener(event->{if(!event.getValueIsAdjusting())refresh.run();});refresh.run();
    }
    private static List<String> selection(JList<Member> members){List<String> ids=new ArrayList<>();for(Member member:members.getSelectedValuesList())ids.add(member.target);return ids;}
    private void add() {coordinator.edit(next->{Anchor anchor=new Anchor();anchor.name="Anchor "+(next.anchors.size()+1);next.anchors.add(anchor);});}
    private void duplicate() {
        Anchor anchor=list.getSelectedValue();if(anchor==null)return;
        coordinator.duplicate(anchor.id);
    }
    private void delete() {Anchor anchor=list.getSelectedValue();if(anchor!=null)coordinator.delete(anchor.id);}
    private void importJson() {
        JTextArea text=new JTextArea(12,26);JScrollPane scroll=new JScrollPane(text);
        if(JOptionPane.showConfirmDialog(this,scroll,"Paste anchor layout (replaces current)",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)coordinator.importLayout(text.getText());
    }
    private void exportJson() {
        JTextArea text=new JTextArea(coordinator.exportLayout(),12,26);text.setLineWrap(true);text.setWrapStyleWord(true);text.selectAll();
        JOptionPane.showMessageDialog(this,new JScrollPane(text),"Copy anchor layout",JOptionPane.PLAIN_MESSAGE);
    }
    private void template() {
        Object action=JOptionPane.showInputDialog(this,"Share anchors across selected profiles.","Shared template",JOptionPane.PLAIN_MESSAGE,null,new String[]{"Create from this layout","Link existing template","Unlink this profile"},"Create from this layout");
        if(action==null)return;if(action.equals("Unlink this profile")){templates.unlink();return;}
        String name=JOptionPane.showInputDialog(this,"Template name (letters, numbers, spaces, hyphens):");if(name==null)return;
        if(action.equals("Create from this layout"))templates.create(name);else templates.link(name);
    }
    private void color(int[] colors,int index,Consumer<Integer> save) {Color color=JColorChooser.showDialog(this,"Anchor color",new Color(colors[index],true));if(color!=null){colors[index]=(colors[index]&0xff000000)|(color.getRGB()&0xffffff);save.accept(colors[index]);}}
    private static void changed(JSpinner spinner,String name,java.util.function.IntConsumer change) {spinner.setName(name);spinner.addChangeListener(event->change.accept(value(spinner)));}
    private static void update(JSpinner spinner,int value) {if(!((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().isFocusOwner())spinner.setValue(value);}
    private static JPanel column() {
        JPanel p=new JPanel(){@Override protected void addImpl(Component component,Object constraints,int index) {
            if(component instanceof JComponent)((JComponent)component).setAlignmentX(LEFT_ALIGNMENT);
            if(component instanceof JLabel)component.setForeground(new Color(215,215,215));
            super.addImpl(component,constraints,index);
        }};
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setAlignmentX(LEFT_ALIGNMENT);p.setOpaque(false);return p;
    }
    private static JPanel row(JComponent... components) {JPanel p=new JPanel(new GridLayout(1,components.length,3,0));p.setOpaque(false);p.setAlignmentX(LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));for(JComponent c:components)p.add(c);return p;}
    private static void field(JPanel parent,String label,JComponent control) {parent.add(new JLabel(label));control.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));parent.add(control);}
    private static JButton button(String label,Runnable action) {JButton b=new JButton(label);b.setAlignmentX(LEFT_ALIGNMENT);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));b.setPreferredSize(new Dimension(180,28));b.setMargin(new java.awt.Insets(2,3,2,3));b.setToolTipText(label);b.addActionListener(e->action.run());return b;}
    private static JSpinner number(int value,int min,int max) {return new JSpinner(new SpinnerNumberModel(value,min,max,1));}
    private static int value(JSpinner input) {return ((Number)input.getValue()).intValue();}
    private static String escape(String text) {return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
