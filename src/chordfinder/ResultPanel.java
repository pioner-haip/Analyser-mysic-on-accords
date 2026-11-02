package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Экран 2 «Результат анализа»: слева управление, в центре аккорды и текст, справа предыдущий / текущий / следующий
 * аккорд с аппликатурой. Здесь же живёт состояние просмотра (сдвиг тональности, режим «упрощённые», текст песни).
 */
public class ResultPanel extends JPanel {
    private final Runnable onNewSong;
    private final Consumer<String> onStatus;

    private Song song;
    private int shift = 0;
    private boolean simplified = true;
    private List<String> lyricsLines;     // null = текста нет, показываем ленту
    private String lyricsText = "";
    private boolean annotated;
    private SheetBuilder.Sheet sheet;
    private int lastSlot = -2;
    private boolean playing;

    // левая панель
    private final JLabel fileLabel = new JLabel();
    private final JButton playBtn = Theme.button("Играть", true);
    private final JButton pauseBtn = Theme.button("Пауза", false);
    private final SeekBar seekBar = new SeekBar();
    private final JLabel timeLabel = new JLabel("0:00 / 0:00");
    private final JLabel keyLabel = new JLabel();
    private final JLabel tempoLabel = new JLabel();
    private final JCheckBox simpleBox = new JCheckBox("Упрощённые аккорды", true);
    private final JLabel shiftLabel = new JLabel("0");
    // средняя
    // ряд «чипов» занимает ровно столько высоты, сколько нужно (иначе BoxLayout растягивает его пустым местом)
    private final JPanel chipsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 8)) {
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    };
    private final ChordSheetPanel sheetPanel = new ChordSheetPanel();
    private final Map<String, Chip> chips = new HashMap<>();
    // правая
    private final ChordCard prevCard = new ChordCard("Предыдущий", false);
    private final ChordCard curCard = new ChordCard("Сейчас играет", true);
    private final ChordCard nextCard = new ChordCard("Следующий", false);

    private final Timer timer = new Timer(40, e -> tick());

    public ResultPanel(Runnable onNewSong, Consumer<String> onStatus) {
        this.onNewSong = onNewSong;
        this.onStatus = onStatus;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(buildLeft(), BorderLayout.WEST);
        body.add(buildMiddle(), BorderLayout.CENTER);
        body.add(buildRight(), BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        playBtn.addActionListener(e -> play());
        pauseBtn.addActionListener(e -> pause());
        seekBar.onSeek(f -> { if (song != null) seekTo(f * song.duration); });
        sheetPanel.onSeek(this::seekTo);
        simpleBox.addActionListener(e -> { simplified = simpleBox.isSelected(); rebuild(); });
        for (ChordCard c : new ChordCard[]{prevCard, curCard, nextCard}) {
            c.onClick(name -> ChordVariantsDialog.show(this, name));
        }
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "toggle");
        getActionMap().put("toggle", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { togglePlay(); }
        });
    }

    // ---- построение панелей ---------------------------------------------------------------------

    private JPanel pane(String title, int width) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        if (width > 0) p.setPreferredSize(new Dimension(width, 100));
        JLabel h = new JLabel(title, JLabel.CENTER);
        h.setFont(Theme.font(15f, Font.BOLD));
        h.setForeground(Theme.TEXT);
        h.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)));
        fullWidth(h);
        p.add(h);
        p.add(Box.createVerticalStrut(12));
        return p;
    }

    private static void fullWidth(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(12f, Font.PLAIN));
        l.setForeground(Theme.MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildLeft() {
        JPanel p = pane("Файл и управление", 210);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER), p.getBorder()));

        p.add(section("ФАЙЛ"));
        p.add(Box.createVerticalStrut(6));
        fileLabel.setFont(Theme.font(13f, Font.PLAIN));
        fileLabel.setForeground(Theme.MUTED);
        fileLabel.setOpaque(true);
        fileLabel.setBackground(Theme.WHITE);
        fileLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        fileLabel.setText("название.wav");
        fullWidth(fileLabel);
        p.add(fileLabel);
        p.add(Box.createVerticalStrut(16));

        p.add(section("ВОСПРОИЗВЕДЕНИЕ"));
        p.add(Box.createVerticalStrut(6));
        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        buttons.add(playBtn);
        buttons.add(pauseBtn);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.add(buttons);
        p.add(Box.createVerticalStrut(10));
        seekBar.setAlignmentX(LEFT_ALIGNMENT);
        p.add(seekBar);
        p.add(Box.createVerticalStrut(4));
        timeLabel.setFont(Theme.font(12f, Font.PLAIN));
        timeLabel.setForeground(Theme.MUTED);
        timeLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(timeLabel);
        p.add(Box.createVerticalStrut(16));

        p.add(section("ПАРАМЕТРЫ"));
        p.add(Box.createVerticalStrut(8));
        keyLabel.setFont(Theme.font(13f, Font.PLAIN));
        keyLabel.setAlignmentX(LEFT_ALIGNMENT);
        tempoLabel.setFont(Theme.font(13f, Font.PLAIN));
        tempoLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(keyLabel);
        p.add(Box.createVerticalStrut(4));
        p.add(tempoLabel);
        p.add(Box.createVerticalStrut(10));
        simpleBox.setOpaque(false);
        simpleBox.setFont(Theme.font(13f, Font.PLAIN));
        simpleBox.setAlignmentX(LEFT_ALIGNMENT);
        simpleBox.setToolTipText("Показывать только мажор и минор: G7 → G, Am7 → Am");
        p.add(simpleBox);
        p.add(Box.createVerticalStrut(10));

        JLabel tr = new JLabel("Транспонировать");
        tr.setFont(Theme.font(13f, Font.PLAIN));
        tr.setAlignmentX(LEFT_ALIGNMENT);
        p.add(tr);
        p.add(Box.createVerticalStrut(6));
        Chip minus = new Chip("−");
        Chip plus = new Chip("+");
        minus.setToolTipText("Ниже на полтона");
        plus.setToolTipText("Выше на полтона");
        shiftLabel.setFont(Theme.font(13f, Font.BOLD));
        minus.addActionListener(e -> changeShift(-1));
        plus.addActionListener(e -> changeShift(+1));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(minus);
        row.add(plus);
        row.add(shiftLabel);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(row);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildMiddle() {
        JPanel p = pane("Аккорды и текст", 0);
        p.add(section("ВСЕ АККОРДЫ ПЕСНИ"));
        chipsPanel.setOpaque(false);
        chipsPanel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(chipsPanel);
        p.add(Box.createVerticalStrut(4));
        JScrollPane scroll = new JScrollPane(sheetPanel);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        p.add(scroll);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER), p.getBorder()));
        return p;
    }

    private JPanel buildRight() {
        JPanel p = pane("Аккорды", 250);
        JPanel stack = new JPanel(new GridBagLayout());
        stack.setOpaque(false);
        stack.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.insets = new java.awt.Insets(0, 0, 12, 0);
        c.gridy = 0; c.weighty = 0;
        stack.add(prevCard, c);
        c.gridy = 1; c.weighty = 1;
        stack.add(curCard, c);
        c.gridy = 2; c.weighty = 0; c.insets = new java.awt.Insets(0, 0, 0, 0);
        stack.add(nextCard, c);
        p.add(stack);
        return p;
    }

    private JPanel buildBottom() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BAR);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)));
        JButton again = Theme.button("Новая песня", false);
        JButton export = Theme.button("Экспорт…", true);
        again.addActionListener(e -> onNewSong.run());
        export.addActionListener(e -> exportSheet());
        bar.add(again, BorderLayout.WEST);
        bar.add(export, BorderLayout.EAST);
        return bar;
    }

    // ---- загрузка песни и пересборка листа --------------------------------------------------------

    public void loadSong(Song s) {
        closeSong();
        this.song = s;
        this.shift = 0;
        this.simplified = true;
        this.simpleBox.setSelected(true);
        this.lyricsLines = null;
        this.lyricsText = "";
        this.annotated = false;
        this.playing = false;
        FontMetrics fm = fileLabel.getFontMetrics(fileLabel.getFont());
        fileLabel.setText(Theme.ellipsize(s.name, fm, 160));
        fileLabel.setToolTipText(s.file.getAbsolutePath());
        tempoLabel.setText("Темп: " + Math.round(s.bpm) + " BPM");
        updateButtons();
        rebuild();
        showPosition(0);
    }

    /** Останавливает звук и освобождает ресурсы (при закрытии песни или окна). */
    public void closeSong() {
        timer.stop();
        if (song != null && song.player != null) song.player.close();
        song = null;
        playing = false;
    }

    public Song getSong() { return song; }

    private void rebuild() {
        if (song == null) return;
        List<ChordEvent> events = song.events(simplified);
        sheet = lyricsLines == null
                ? SheetBuilder.timeline(events, shift, simplified)
                : SheetBuilder.withLyrics(events, lyricsLines, annotated, shift, simplified, song.duration);
        sheetPanel.setSheet(sheet);
        lastSlot = -2;

        chipsPanel.removeAll();
        chips.clear();
        for (String name : sheet.distinctChords()) {
            Chip chip = new Chip(name);
            chip.addActionListener(e -> ChordVariantsDialog.show(this, name));
            chips.put(name, chip);
            chipsPanel.add(chip);
        }
        chipsPanel.revalidate();
        chipsPanel.repaint();

        keyLabel.setText("<html>Тональность: <b>" + ChordNames.display(song.key.name(), shift, false) + "</b></html>");
        shiftLabel.setText(shift == 0 ? "0" : (shift > 0 ? "+" : "") + shift);
        showPosition(currentTime());
    }

    private void changeShift(int d) {
        if (song == null) return;
        shift = Math.max(-11, Math.min(11, shift + d));
        rebuild();
    }

    // ---- воспроизведение --------------------------------------------------------------------------

    private double currentTime() {
        return song != null && song.player != null ? song.player.getPositionSeconds() : 0;
    }

    public void togglePlay() {
        if (playing) pause(); else play();
    }

    private void play() {
        if (song == null) return;
        if (song.player == null) {
            JOptionPane.showMessageDialog(this, "Звук воспроизвести не удалось (нет звукового устройства), "
                    + "но аккорды можно смотреть и экспортировать.", "Воспроизведение", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        song.player.play();
        playing = true;
        timer.start();
        updateButtons();
    }

    private void pause() {
        if (song == null || song.player == null) return;
        song.player.pause();
        playing = false;
        timer.stop();
        updateButtons();
    }

    private void seekTo(double seconds) {
        if (song == null) return;
        if (song.player != null) song.player.seek(seconds);
        showPosition(seconds);
    }

    private void tick() {
        if (song == null || song.player == null) return;
        Player pl = song.player;
        double t = pl.getPositionSeconds();
        showPosition(t);
        if (playing && !pl.isPlaying()) { // дошли до конца
            pl.stop();
            playing = false;
            timer.stop();
            updateButtons();
            showPosition(0);
        }
    }

    private void updateButtons() {
        playBtn.setEnabled(!playing);
        pauseBtn.setEnabled(playing);
    }

    /** Показывает, что в момент t звучит такой-то аккорд: подсветка в листе, карточки справа, полоса и время. */
    public void showPosition(double t) {
        if (song == null) return;
        seekBar.setFraction(song.duration > 0 ? t / song.duration : 0);
        timeLabel.setText(ChordEvent.formatTime(t) + " / " + ChordEvent.formatTime(song.duration));
        if (sheet == null) return;
        int idx = sheet.slotAt(t);
        if (idx == lastSlot) return;
        lastSlot = idx;
        sheetPanel.setCurrentSlot(idx);
        String cur = idx >= 0 ? sheet.slots.get(idx).name : null;
        curCard.setChord(cur);
        prevCard.setChord(idx > 0 ? sheet.slots.get(idx - 1).name : null);
        nextCard.setChord(idx >= 0 && idx + 1 < sheet.slots.size() ? sheet.slots.get(idx + 1).name : null);
        for (Map.Entry<String, Chip> e : chips.entrySet()) e.getValue().setActive(e.getKey().equals(cur));
    }

    // ---- команды из меню ------------------------------------------------------------------------

    public void editLyrics(Frame owner) {
        if (song == null) return;
        LyricsDialog.Result r = LyricsDialog.show(owner, lyricsText, annotated);
        if (r == null) return;
        lyricsText = r.text;
        annotated = r.annotated;
        List<String> lines = new ArrayList<>();
        boolean prevBlank = true;
        for (String l : r.text.split("\\R")) {
            boolean blank = l.trim().isEmpty();
            if (blank && prevBlank) continue;      // не копим подряд идущие пустые строки
            lines.add(blank ? "" : l.stripTrailing());
            prevBlank = blank;
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        lyricsLines = lines.isEmpty() ? null : lines;
        rebuild();
        if (onStatus != null) onStatus.accept(lyricsLines == null ? "Текст убран — показана лента аккордов"
                : annotated ? "Текст с аккордами загружен (время аккордов оценено равномерно)"
                            : "Аккорды из записи наложены на текст");
    }

    public void exportSheet() {
        if (song != null && sheet != null) Exporter.export(this, song, sheet);
    }

    // ---- для тестов и снимков экрана -------------------------------------------------------------

    void setSimplifiedForTest(boolean v) { simplified = v; simpleBox.setSelected(v); rebuild(); }
    void setShiftForTest(int s) { shift = s; rebuild(); }
    void setLyricsForTest(List<String> lines, boolean annotated) { this.lyricsLines = lines; this.annotated = annotated; rebuild(); }
}
