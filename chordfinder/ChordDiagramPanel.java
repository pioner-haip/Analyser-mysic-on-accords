package chordfinder;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Рисует один гриф с точками зажатия — как в бумажных самоучителях, только программно. */
public class ChordDiagramPanel extends JPanel {
    private final String title;
    private final int[] frets; // -1 = заглушена, 0 = открыта, иначе номер лада
    private static final int FRET_COUNT = 4;
    private static final int STRINGS = 6;

    public ChordDiagramPanel(String title, int[] frets) {
        this.title = title;
        this.frets = frets;
        setPreferredSize(new Dimension(150, 170));
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int left = 25, top = 35, width = 90, height = 90;
        int stringGap = width / (STRINGS - 1);
        int fretGap = height / FRET_COUNT;

        int minFret = Integer.MAX_VALUE;
        for (int f : frets) {
            if (f > 0) minFret = Math.min(minFret, f);
        }
        int base = (minFret != Integer.MAX_VALUE && minFret > FRET_COUNT) ? minFret - 1 : 0;

        g2.setColor(Color.BLACK);
        g2.drawString(title, 5, 15);

        if (base == 0) {
            g2.fillRect(left - 2, top - 4, width + 4, 4); // порожек (нулевой лад)
        } else {
            g2.drawString(String.valueOf(base + 1), left - 20, top + fretGap / 2 + 5);
        }

        for (int i = 0; i <= FRET_COUNT; i++) {
            g2.drawLine(left, top + i * fretGap, left + width, top + i * fretGap);
        }
        for (int i = 0; i < STRINGS; i++) {
            g2.drawLine(left + i * stringGap, top, left + i * stringGap, top + height);
        }

        for (int i = 0; i < STRINGS; i++) {
            int f = frets[i];
            int x = left + i * stringGap;
            if (f < 0) {
                g2.drawString("x", x - 4, top - 10);
            } else if (f == 0) {
                g2.drawString("o", x - 4, top - 10);
            } else {
                int relFret = f - base;
                if (relFret >= 1 && relFret <= FRET_COUNT) {
                    int y = top + (relFret - 1) * fretGap + fretGap / 2;
                    g2.fillOval(x - 6, y - 6, 12, 12);
                }
            }
        }
    }
}
