package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Экран 1 «Старт»: область «перетащите файл», кнопки выбора/записи и список недавних песен. Во время анализа показывает прогресс. */
public class StartPanel extends JPanel {
    public interface Actions {
        void choose();
        void record();
        void open(File file);
        void cancel();
    }

    private final DropBox dropBox;
    private final JPanel recentList = new JPanel();
    private final JLabel busyTitle = new JLabel();
    private final JLabel busyHint = new JLabel("Это занимает несколько секунд");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final Actions actions;
    private boolean dragOver;

    public StartPanel(Actions actions) {
        this.actions = actions;
        this.dropBox = new DropBox(); // после создания подписей и индикатора, которые он использует
        setBackground(Theme.BG);
        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.NONE;   // блок фиксированной ширины 560, по центру окна
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setPreferredSize(new Dimension(560, 380));
        column.setMaximumSize(new Dimension(560, 600));

        dropBox.setAlignmentX(LEFT_ALIGNMENT);
        dropBox.setPreferredSize(new Dimension(560, 250));
        dropBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        column.add(dropBox);
        column.add(Box.createVerticalStrut(28));

        JLabel recentTitle = new JLabel("НЕДАВНИЕ");
        recentTitle.setFont(Theme.font(12f, Font.PLAIN));
        recentTitle.setForeground(Theme.MUTED);
        recentTitle.setAlignmentX(LEFT_ALIGNMENT);
        column.add(recentTitle);
        column.add(Box.createVerticalStrut(8));
        recentList.setOpaque(false);
        recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
        recentList.setAlignmentX(LEFT_ALIGNMENT);
        column.add(recentList);
        column.add(Box.createVerticalGlue());
        add(column, c);
        refreshRecent();
    }

    // ---- состояния ------------------------------------------------------------------------

    public void setBusy(String fileName) {
        busyTitle.setText("Анализирую «" + fileName + "»…");
        progress.setIndeterminate(true);
        dropBox.show("busy");
        setDragOver(false);
    }

    public void setProgress(double fraction) {
        if (progress.isIndeterminate()) progress.setIndeterminate(false);
        progress.setValue((int) Math.round(fraction * 1000));
    }

    public void setIdle() {
        dropBox.show("idle");
    }

    public void setDragOver(boolean over) {
        if (dragOver != over) { dragOver = over; dropBox.repaint(); }
    }

    public void refreshRecent() {
        recentList.removeAll();
        List<RecentFiles.Entry> list = RecentFiles.list(3);
        if (list.isEmpty()) {
            recentList.add(new RecentRow(null, null));
        } else {
            for (RecentFiles.Entry e : list) {
                recentList.add(new RecentRow(e.file, e.openedAt));
                recentList.add(Box.createVerticalStrut(6));
            }
        }
        recentList.revalidate();
        recentList.repaint();
    }

    // ---- внутренние компоненты --------------------------------------------------------------

    /** Область с пунктирной рамкой: внутри либо приглашение выбрать файл, либо индикатор анализа. */
    private class DropBox extends JPanel {
        private final CardLayout cards = new CardLayout();

        DropBox() {
            setOpaque(false);
            setLayout(cards);
            add(buildIdle(), "idle");
            add(buildBusy(), "busy");
        }

        void show(String name) { cards.show(this, name); }

        private JPanel buildIdle() {
            JPanel p = centered();
            JLabel title = new JLabel("Загрузите песню для анализа");
            title.setFont(Theme.font(22f, Font.BOLD));
            title.setForeground(Theme.TEXT);
            JLabel hint = new JLabel("WAV или MP3 · перетащите файл сюда");
            hint.setFont(Theme.font(14f, Font.PLAIN));
            hint.setForeground(Theme.MUTED);
            JButton choose = Theme.button("Выбрать файл…", true);
            JButton rec = Theme.button("Записать с микрофона", false);
            JLabel or = new JLabel("или");
            or.setForeground(Theme.MUTED);
            or.setFont(Theme.font(13f, Font.PLAIN));
            choose.addActionListener(e -> actions.choose());
            rec.addActionListener(e -> actions.record());
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
            row.setOpaque(false);
            row.add(choose);
            row.add(or);
            row.add(rec);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            p.add(Box.createVerticalGlue());
            p.add(center(title));
            p.add(Box.createVerticalStrut(12));
            p.add(center(hint));
            p.add(Box.createVerticalStrut(22));
            p.add(row);
            p.add(Box.createVerticalGlue());
            return p;
        }

        private JPanel buildBusy() {
            JPanel p = centered();
            busyTitle.setFont(Theme.font(18f, Font.BOLD));
            busyTitle.setForeground(Theme.TEXT);
            busyHint.setFont(Theme.font(13f, Font.PLAIN));
            busyHint.setForeground(Theme.MUTED);
            progress.setPreferredSize(new Dimension(360, 14));
            progress.setMaximumSize(new Dimension(360, 14));
            JButton cancel = Theme.button("Отмена", false);
            cancel.addActionListener(e -> actions.cancel());
            p.add(Box.createVerticalGlue());
            p.add(center(busyTitle));
            p.add(Box.createVerticalStrut(8));
            p.add(center(busyHint));
            p.add(Box.createVerticalStrut(18));
            p.add(center(progress));
            p.add(Box.createVerticalStrut(18));
            p.add(center(cancel));
            p.add(Box.createVerticalGlue());
            return p;
        }

        private JPanel centered() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            return p;
        }

        private JPanel center(java.awt.Component c) {
            JPanel w = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            w.setOpaque(false);
            w.add(c);
            w.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.getPreferredSize().height)); // не растягиваться по высоте
            return w;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D rr = new RoundRectangle2D.Double(1.5, 1.5, getWidth() - 3, getHeight() - 3, 16, 16);
            g2.setColor(dragOver ? Theme.HILITE : Theme.WHITE);
            g2.fill(rr);
            g2.setColor(dragOver ? Theme.ACCENT : Theme.BORDER);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
            g2.draw(rr);
            g2.dispose();
        }
    }

    /** Строка списка недавних: имя файла слева, дата справа; по клику файл открывается снова. */
    private class RecentRow extends JPanel {
        private boolean hover;

        RecentRow(File file, Long when) {
            setOpaque(false);
            setLayout(new java.awt.BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setPreferredSize(new Dimension(560, 36));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setAlignmentX(LEFT_ALIGNMENT);
            JLabel name = new JLabel(file == null ? "Пока ничего нет — откройте первую песню" : file.getName());
            name.setFont(Theme.font(13f, Font.PLAIN));
            name.setForeground(file == null ? Theme.MUTED : Theme.TEXT);
            add(name, java.awt.BorderLayout.CENTER);
            if (file != null) {
                JLabel date = new JLabel(new SimpleDateFormat("dd.MM.yyyy").format(new Date(when)));
                date.setFont(Theme.font(13f, Font.PLAIN));
                date.setForeground(Theme.MUTED);
                add(date, java.awt.BorderLayout.EAST);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(file.getAbsolutePath());
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { actions.open(file); }
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D rr = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(hover ? Theme.HILITE : Theme.WHITE);
            g2.fill(rr);
            g2.setColor(hover ? Theme.ACCENT : Theme.BORDER);
            g2.draw(rr);
            g2.dispose();
        }
    }
}
