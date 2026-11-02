package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

public class ChordLibraryDialog extends JDialog {
    public ChordLibraryDialog(Frame owner) {
        super(owner, "Справочник аккордов", false);
        setLayout(new BorderLayout());

        DefaultListModel<Chord> model = new DefaultListModel<>();
        for (Chord c : ChordLibrary.allChords()) model.addElement(c);
        JList<Chord> list = new JList<>(model);
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) ->
                new JLabel("  " + value.name()));
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel voicingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Chord selected = list.getSelectedValue();
            if (selected == null) return;
            voicingsPanel.removeAll();
            for (ChordVoicing v : ChordLibrary.variations(selected)) {
                voicingsPanel.add(new ChordDiagramPanel(selected.name() + " - " + v.label, v.frets));
            }
            voicingsPanel.revalidate();
            voicingsPanel.repaint();
        });

        add(new JScrollPane(list), BorderLayout.WEST);
        add(new JScrollPane(voicingsPanel), BorderLayout.CENTER);
        list.setSelectedIndex(0);

        setSize(720, 400);
        setLocationRelativeTo(owner);
    }
}
