package chordfinder;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;

/** Окно с вариантами аппликатуры выбранного аккорда (открытая позиция, баррэ). */
public class ChordVariantsDialog {
    public static void show(Component parent, String chordName) {
        Chord chord = Chord.parse(chordName);
        if (chord == null) return;
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(Theme.WHITE);
        for (ChordVoicing v : ChordLibrary.variations(chord)) {
            ChordDiagramPanel d = new ChordDiagramPanel(chordName + " — " + v.label, v.frets);
            d.setOpaque(true);
            d.setBackground(Theme.WHITE);
            panel.add(d);
        }
        JOptionPane.showMessageDialog(parent, panel, "Аппликатуры: " + chordName, JOptionPane.PLAIN_MESSAGE);
    }
}
