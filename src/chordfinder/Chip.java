package chordfinder;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Небольшая кнопка-«чип» с названием аккорда; активная (звучит сейчас) подсвечивается синим. */
public class Chip extends JButton {
    private boolean active;

    public Chip(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Theme.font(13f, Font.BOLD));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Показать варианты аппликатуры");
    }

    public void setActive(boolean a) {
        if (active != a) { active = a; repaint(); }
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Math.max(44, fm.stringWidth(getText()) + 22), 28);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        RoundRectangle2D rr = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8);
        Color fill = active ? Theme.HILITE : getModel().isRollover() ? new Color(0xF0F0EE) : Theme.WHITE;
        g2.setColor(fill);
        g2.fill(rr);
        g2.setColor(active ? Theme.ACCENT : new Color(0x777777));
        g2.setStroke(new BasicStroke(active ? 2f : 1f));
        g2.draw(rr);
        g2.setColor(active ? Theme.ACCENT : Theme.TEXT);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
