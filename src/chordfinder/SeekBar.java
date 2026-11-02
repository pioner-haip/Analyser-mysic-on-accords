package chordfinder;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.DoubleConsumer;

/** Полоса прогресса воспроизведения; по клику или перетаскиванию перематывает песню. */
public class SeekBar extends JComponent {
    private double fraction = 0;
    private DoubleConsumer onSeek = f -> {};
    private boolean dragging;

    public SeekBar() {
        setPreferredSize(new Dimension(100, 14));
        setMinimumSize(new Dimension(40, 14));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragging = true; seek(e); }
            @Override public void mouseDragged(MouseEvent e) { if (dragging) seek(e); }
            @Override public void mouseReleased(MouseEvent e) { dragging = false; }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void onSeek(DoubleConsumer c) { this.onSeek = c; }

    public void setFraction(double f) {
        if (dragging) return; // пока тянем ползунок, не перебиваем его таймером
        this.fraction = Math.max(0, Math.min(1, f));
        repaint();
    }

    private void seek(MouseEvent e) {
        fraction = Math.max(0, Math.min(1, e.getX() / (double) Math.max(1, getWidth())));
        repaint();
        onSeek.accept(fraction);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = 10, y = (getHeight() - h) / 2, w = getWidth();
        g2.setColor(Theme.WHITE);
        g2.fill(new RoundRectangle2D.Double(0, y, w - 1, h, 6, 6));
        g2.setClip(new RoundRectangle2D.Double(0, y, w - 1, h, 6, 6));
        g2.setColor(Theme.ACCENT);
        g2.fillRect(0, y, (int) Math.round((w - 1) * fraction), h);
        g2.setClip(null);
        g2.setColor(Theme.BORDER);
        g2.draw(new RoundRectangle2D.Double(0, y, w - 1, h, 6, 6));
        g2.dispose();
    }
}
