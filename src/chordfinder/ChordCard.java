package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Карточка аккорда в правом столбце: подпись, имя и схема аппликатуры. Компактная — для соседних, крупная — для текущего. */
public class ChordCard extends JPanel {
    private final boolean large;
    private final JLabel caption = new JLabel();
    private final JLabel name = new JLabel();
    private final ChordDiagramPanel diagram;
    private String chordName;

    public ChordCard(String captionText, boolean large) {
        this.large = large;
        setOpaque(false);
        caption.setText(captionText);
        caption.setFont(Theme.font(12f, Font.PLAIN));
        caption.setForeground(Theme.MUTED);
        name.setFont(Theme.font(20f, Font.BOLD));
        name.setForeground(Theme.TEXT);
        diagram = large ? new ChordDiagramPanel(null, null, 110, 121, false)
                        : new ChordDiagramPanel(null, null, 60, 66, false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Показать варианты аппликатуры");

        if (large) {
            setLayout(new BorderLayout(0, 2));
            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            caption.setAlignmentX(CENTER_ALIGNMENT);
            name.setAlignmentX(CENTER_ALIGNMENT);
            top.add(caption);
            top.add(name);
            add(top, BorderLayout.NORTH);
            add(diagram, BorderLayout.CENTER);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            setPreferredSize(new Dimension(200, 190));
        } else {
            setLayout(new BorderLayout(8, 0));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(javax.swing.Box.createVerticalGlue());
            text.add(caption);
            text.add(name);
            text.add(javax.swing.Box.createVerticalGlue());
            add(text, BorderLayout.CENTER);
            add(diagram, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setPreferredSize(new Dimension(200, 100));
            setMinimumSize(new Dimension(150, 90));
        }
        setChord(null);
    }

    /** Подключает действие по клику на карточку (показ вариантов аппликатуры). */
    public void onClick(java.util.function.Consumer<String> action) {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (chordName != null) action.accept(chordName); }
        });
    }

    public void setChord(String chordName) {
        this.chordName = chordName;
        name.setText(chordName == null ? "—" : chordName);
        Chord c = chordName == null ? null : Chord.parse(chordName);
        if (c == null) {
            diagram.setVoicing(null, null);
        } else {
            ChordVoicing v = ChordLibrary.easiest(c);
            diagram.setVoicing(null, v.frets);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float bw = large ? 2f : 1f;
        RoundRectangle2D rr = new RoundRectangle2D.Double(bw / 2, bw / 2, getWidth() - bw, getHeight() - bw, 12, 12);
        g2.setColor(Theme.WHITE);
        g2.fill(rr);
        g2.setColor(large ? Theme.ACCENT : Theme.BORDER);
        g2.setStroke(new BasicStroke(bw));
        g2.draw(rr);
        g2.dispose();
    }
}
