package chordfinder;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Цвета, шрифты и кнопка в едином стиле — всё взято из макетов интерфейса (серая основа + синий акцент). */
public final class Theme {
    private Theme() {}

    public static final Color BG = new Color(0xF4F4F2);        // фон окна
    public static final Color BAR = new Color(0xE3E3E0);       // меню, нижние панели, вторичные кнопки
    public static final Color BORDER = new Color(0xA9A9A4);    // рамки панелей
    public static final Color TEXT = new Color(0x222222);
    public static final Color MUTED = new Color(0x555555);
    public static final Color GRID = new Color(0x555555);      // линии грифа
    public static final Color ACCENT = new Color(0x2F5FD0);    // текущий аккорд
    public static final Color HILITE = new Color(0xDFE8FB);    // подсветка текущей строки
    public static final Color WHITE = Color.WHITE;

    public static Font font(float size, int style) {
        return new Font(Font.SANS_SERIF, style, 12).deriveFont(size);
    }

    public static JButton button(String text, boolean primary) {
        return new FlatButton(text, primary);
    }

    /** Плоская кнопка: на любой системной теме выглядит одинаково (стандартные кнопки macOS игнорируют цвета). */
    public static class FlatButton extends JButton {
        private final boolean primary;

        FlatButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(font(13f, Font.PLAIN));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 28, 36);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(36, 36);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isEnabled();
            boolean press = getModel().isPressed() && on;
            boolean hover = getModel().isRollover() && on;

            Color fill, fg, line;
            if (primary) {
                fill = !on ? new Color(0x9A9A9A) : press ? Color.BLACK : hover ? new Color(0x3A3A3A) : TEXT;
                fg = Color.WHITE;
                line = fill;
            } else {
                fill = !on ? new Color(0xEEEEEC) : press ? new Color(0xC9C9C4) : hover ? new Color(0xD6D6D2) : BAR;
                fg = on ? TEXT : new Color(0x999999);
                line = on ? new Color(0x777777) : new Color(0xCCCCCA);
            }
            RoundRectangle2D rr = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(fill);
            g2.fill(rr);
            g2.setColor(line);
            g2.draw(rr);
            if (isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 6, 6));
            }
            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** Усечение длинного текста многоточием посередине (для имён файлов). */
    public static String ellipsize(String s, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(s) <= maxWidth) return s;
        String dots = "…";
        int keepEnd = Math.min(8, s.length() / 3);
        String end = s.substring(s.length() - keepEnd);
        int lo = 0, hi = s.length() - keepEnd;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + dots + end) <= maxWidth) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + dots + end;
    }

    public static void styleLabelButton(AbstractButton b) {
        b.setFont(font(13f, Font.PLAIN));
    }
}
