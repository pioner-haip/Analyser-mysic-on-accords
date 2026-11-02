package chordfinder;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Рисует аппликатуру: 6 струн, 5 ладов, точки зажатия, крестики (глушить) и кружки (открытая струна).
 * Рисование идёт в условных координатах 100x110 и масштабируется под размер компонента,
 * поэтому одну и ту же панель можно использовать и мелкой, и крупной.
 */
public class ChordDiagramPanel extends JPanel {
    private static final int FRETS = 5;
    private static final int STRINGS = 6;
    // геометрия в условных единицах (как в макете интерфейса)
    private static final double LEFT = 15, RIGHT = 85, TOP = 25, BOTTOM = 105;
    private static final double W = 100, H = 110;

    private String title;
    private int[] frets;
    private boolean showTitle;

    /** Старый вариант: фиксированный размер и подпись над грифом (справочник, диалог вариантов). */
    public ChordDiagramPanel(String title, int[] frets) {
        this(title, frets, 150, 170, true);
    }

    public ChordDiagramPanel(String title, int[] frets, int width, int height, boolean showTitle) {
        this.title = title;
        this.frets = frets;
        this.showTitle = showTitle;
        setPreferredSize(new Dimension(width, height));
        setOpaque(false);
    }

    public void setVoicing(String title, int[] frets) {
        this.title = title;
        this.frets = frets;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int titleH = 0;
        if (showTitle && title != null) {
            g2.setColor(Theme.TEXT);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, 4, fm.getAscent() + 2);
            titleH = fm.getHeight() + 4;
        }

        double availW = getWidth(), availH = getHeight() - titleH;
        double scale = Math.min(availW / W, availH / H);
        if (scale <= 0) { g2.dispose(); return; }
        double ox = (availW - W * scale) / 2.0;
        double oy = titleH + (availH - H * scale) / 2.0;
        g2.transform(new AffineTransform(scale, 0, 0, scale, ox, oy));

        if (frets == null || !ChordLibrary.hasShape(new ChordVoicing("", frets))) {
            g2.setColor(Theme.MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 9f));
            g2.drawString("нет аппликатуры", 8, 60);
            g2.dispose();
            return;
        }

        int minFret = Integer.MAX_VALUE, maxFret = 0;
        for (int f : frets) {
            if (f > 0) { minFret = Math.min(minFret, f); maxFret = Math.max(maxFret, f); }
        }
        int base = (minFret != Integer.MAX_VALUE && maxFret > FRETS) ? minFret - 1 : 0;

        double sg = (RIGHT - LEFT) / (STRINGS - 1);
        double fg = (BOTTOM - TOP) / FRETS;

        g2.setColor(Theme.GRID);
        g2.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < STRINGS; i++) {
            double x = LEFT + i * sg;
            g2.draw(new Line2D.Double(x, TOP, x, BOTTOM));
        }
        for (int i = 0; i <= FRETS; i++) {
            double y = TOP + i * fg;
            if (i == 0 && base == 0) g2.setStroke(new BasicStroke(3f));
            else g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Line2D.Double(LEFT, y, RIGHT, y));
        }

        if (base > 0) {
            g2.setColor(Theme.TEXT);
            g2.setFont(getFont().deriveFont(Font.BOLD, 9f));
            g2.drawString((base + 1) + "fr", 0.5f, (float) (TOP + fg / 2 + 3));
        }

        // барре: линия поверх нескольких струн на самом низком использованном ладу
        drawBarre(g2, base, sg, fg);

        g2.setColor(Theme.TEXT);
        for (int i = 0; i < STRINGS; i++) {
            int f = frets[i];
            double x = LEFT + i * sg;
            if (f < 0) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Line2D.Double(x - 3.5, 10.5, x + 3.5, 17.5));
                g2.draw(new Line2D.Double(x + 3.5, 10.5, x - 3.5, 17.5));
            } else if (f == 0) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Double(x - 3.5, 10.5, 7, 7));
            } else {
                int rel = f - base;
                if (rel >= 1 && rel <= FRETS) {
                    double y = TOP + (rel - 1) * fg + fg / 2;
                    g2.fill(new Ellipse2D.Double(x - 5.5, y - 5.5, 11, 11));
                }
            }
        }
        g2.dispose();
    }

    private void drawBarre(Graphics2D g2, int base, double sg, double fg) {
        int min = Integer.MAX_VALUE;
        for (int f : frets) if (f > 0) min = Math.min(min, f);
        if (min == Integer.MAX_VALUE) return;
        int first = -1, last = -1, count = 0;
        for (int i = 0; i < STRINGS; i++) {
            if (frets[i] == min) { if (first < 0) first = i; last = i; count++; }
        }
        boolean isBarre = count >= 3 || (count == 2 && last - first >= 4);
        if (!isBarre) return;
        int rel = min - base;
        if (rel < 1 || rel > FRETS) return;
        double y = TOP + (rel - 1) * fg + fg / 2;
        double x1 = LEFT + first * sg, x2 = LEFT + last * sg;
        g2.setColor(Theme.TEXT);
        g2.fill(new RoundRectangle2D.Double(x1 - 5.5, y - 5.5, x2 - x1 + 11, 11, 11, 11));
    }
}
