package chordfinder;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Лист «аккорды над текстом»: рисуется вручную, чтобы аккорд стоял точно над нужным словом при любом шрифте.
 * Текущая строка подсвечена, текущий аккорд — синий. Клик по аккорду или строке перематывает песню туда.
 */
public class ChordSheetPanel extends JComponent implements Scrollable {
    private static final int PAD = 12, LINE_H = 50, GAP = 6, COL_W = 80, BLANK_H = 22;

    private final Font chordFont = Theme.font(14f, Font.BOLD);
    private final Font lyricFont = Theme.font(14f, Font.PLAIN);
    private final Font subFont = Theme.font(12f, Font.PLAIN);

    private SheetBuilder.Sheet sheet;
    private int current = -1;
    private DoubleConsumer onSeek = t -> {};
    private final List<Rectangle> hitRects = new ArrayList<>();
    private final List<Integer> hitSlots = new ArrayList<>();

    public ChordSheetPanel() {
        setOpaque(true);
        setBackground(Theme.WHITE);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { click(e); }
        });
    }

    public void onSeek(DoubleConsumer c) { this.onSeek = c; }

    public void setSheet(SheetBuilder.Sheet sheet) {
        this.sheet = sheet;
        this.current = -1;
        revalidate();
        repaint();
    }

    /** Подсветить аккорд с данным номером и прокрутить так, чтобы его строка была видна. */
    public void setCurrentSlot(int slot) {
        if (slot == current) return;
        int oldLine = lineOf(current);
        current = slot;
        int line = lineOf(slot);
        repaint();
        if (line >= 0 && line != oldLine) {
            Rectangle r = lineRect(line);
            // небольшой запас сверху и снизу, чтобы соседние строки тоже были видны (но не выше начала листа)
            int top = Math.max(0, r.y - LINE_H);
            scrollRectToVisible(new Rectangle(0, top, Math.max(1, r.width), (r.y - top) + r.height + LINE_H));
        }
    }

    private int lineOf(int slot) {
        return sheet == null || slot < 0 || slot >= sheet.slots.size() ? -1 : sheet.slots.get(slot).line;
    }

    private int lineHeight(SheetBuilder.Line l) {
        return l.lyric.trim().isEmpty() && l.marks.isEmpty() ? BLANK_H : LINE_H;
    }

    private Rectangle lineRect(int index) {
        int y = PAD;
        for (int i = 0; i < index; i++) y += lineHeight(sheet.lines.get(i)) + GAP;
        return new Rectangle(0, y, getWidth(), lineHeight(sheet.lines.get(index)));
    }

    // ---- размеры -------------------------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        if (sheet == null || sheet.lines.isEmpty()) return new Dimension(200, 120);
        FontMetrics fm = getFontMetrics(lyricFont);
        int w = 0, h = PAD * 2;
        for (SheetBuilder.Line l : sheet.lines) {
            int lw = sheet.timeline ? l.marks.size() * COL_W : fm.stringWidth(l.lyric) + 40;
            w = Math.max(w, lw + PAD * 2);
            h += lineHeight(l) + GAP;
        }
        return new Dimension(w, h);
    }

    // ---- рисование -------------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        hitRects.clear();
        hitSlots.clear();

        if (sheet == null || sheet.lines.isEmpty()) {
            g2.setColor(Theme.MUTED);
            g2.setFont(lyricFont);
            g2.drawString("Аккордов нет", PAD, PAD + 18);
            g2.dispose();
            return;
        }

        FontMetrics cfm = g2.getFontMetrics(chordFont);
        FontMetrics lfm = g2.getFontMetrics(lyricFont);
        FontMetrics sfm = g2.getFontMetrics(subFont);
        int curLine = lineOf(current);
        int y = PAD;
        for (int li = 0; li < sheet.lines.size(); li++) {
            SheetBuilder.Line line = sheet.lines.get(li);
            int h = lineHeight(line);
            if (li == curLine) {
                g2.setColor(Theme.HILITE);
                g2.fillRoundRect(4, y - 4, getWidth() - 8, h + 6, 8, 8);
            }
            int prevEnd = Integer.MIN_VALUE;
            for (int mi = 0; mi < line.marks.size(); mi++) {
                SheetBuilder.Mark m = line.marks.get(mi);
                String text = sheet.slots.get(m.slot).name;
                int x;
                if (sheet.timeline) {
                    x = PAD + mi * COL_W;
                } else {
                    int pos = Math.min(m.pos, line.lyric.length());
                    x = PAD + lfm.stringWidth(line.lyric.substring(0, pos));
                    if (x < prevEnd + 12) x = prevEnd + 12;   // соседние аккорды не налезают друг на друга
                }
                int cw = cfm.stringWidth(text);
                prevEnd = x + cw;
                boolean now = m.slot == current;
                g2.setFont(chordFont);
                g2.setColor(now ? Theme.ACCENT : Theme.TEXT);
                g2.drawString(text, x, y + cfm.getAscent());
                if (now) g2.fillRect(x, y + cfm.getAscent() + 3, cw, 2);
                if (m.sub != null) {
                    g2.setFont(subFont);
                    g2.setColor(Theme.MUTED);
                    g2.drawString(m.sub, x, y + 26 + sfm.getAscent());
                }
                hitRects.add(new Rectangle(x - 3, y - 2, Math.max(cw, 24) + 6, 26));
                hitSlots.add(m.slot);
            }
            if (!line.lyric.isEmpty()) {
                g2.setFont(lyricFont);
                g2.setColor(new Color(0x333333));
                g2.drawString(line.lyric, PAD, y + 26 + lfm.getAscent());
            }
            y += h + GAP;
        }
        g2.dispose();
    }

    // ---- мышь -------------------------------------------------------------------------------------

    private void click(MouseEvent e) {
        if (sheet == null) return;
        for (int i = 0; i < hitRects.size(); i++) {
            if (hitRects.get(i).contains(e.getPoint())) {
                onSeek.accept(sheet.slots.get(hitSlots.get(i)).start);
                return;
            }
        }
        int y = PAD;
        for (int li = 0; li < sheet.lines.size(); li++) {
            SheetBuilder.Line line = sheet.lines.get(li);
            int h = lineHeight(line);
            if (e.getY() >= y - 4 && e.getY() < y + h + GAP && !line.marks.isEmpty()) {
                onSeek.accept(sheet.slots.get(line.marks.get(0).slot).start);
                return;
            }
            y += h + GAP;
        }
    }

    // ---- Scrollable ---------------------------------------------------------------------------------

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(400, 300); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(r.height - 50, 50); }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getParent().getWidth() >= getPreferredSize().width;
    }
}
