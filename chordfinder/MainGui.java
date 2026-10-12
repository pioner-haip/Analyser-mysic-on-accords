package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Графическая версия: загружаешь MIDI/WAV — аккорды с таймингом появляются сразу,
 * без ввода текста. Можно вставить или загрузить текст песни, чтобы получить
 * подсветку строк под музыку.
 */
public class MainGui extends JFrame {
    private static final Color ACCENT = new Color(46, 90, 160);
    private static final Color HIGHLIGHT = new Color(255, 244, 168);

    private Player player;
    private List<Chord> chordsOnly = new ArrayList<>();
    private List<ChordEvent> lastEvents = new ArrayList<>();
    private double bpm = 120;
    private List<TimedSheetBuilder.TimedLine> timedSheet = new ArrayList<>();

    private final JTextArea lyricsInput = new JTextArea(6, 40);
    private final JTextField songQueryField = new JTextField(30);
    private final JCheckBox annotatedBox = new JCheckBox("В тексте уже есть аккорды в [квадратных скобках]");
    private final JLabel statusLabel = new JLabel("Файл не загружен");
    private final JLabel bpmLabel = new JLabel("Темп: -");
    private final DefaultListModel<TimedSheetBuilder.TimedLine> listModel = new DefaultListModel<>();
    private final JList<TimedSheetBuilder.TimedLine> lyricsList = new JList<>(listModel);
    private final javax.swing.Timer timer = new javax.swing.Timer(200, e -> tick());

    public MainGui() {
        super("Подбор аккордов - плеер с подсветкой текста");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        setSize(860, 660);
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Подбор аккордов");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(ACCENT);
        header.add(title, BorderLayout.WEST);

        JPanel source = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadMidi = new JButton("Загрузить MIDI");
        JButton loadWav = new JButton("Загрузить WAV");
        JButton library = new JButton("Справочник аккордов");
        source.add(loadMidi);
        source.add(loadWav);
        source.add(library);
        header.add(source, BorderLayout.EAST);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT));
        status.add(statusLabel);
        status.add(Box.createHorizontalStrut(20));
        status.add(bpmLabel);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(status, BorderLayout.SOUTH);

        loadMidi.addActionListener(e -> loadMidiFile());
        loadWav.addActionListener(e -> loadWavFile());
        library.addActionListener(e -> new ChordLibraryDialog(this).setVisible(true));
        return wrapper;
    }

    private JPanel buildCenter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(titled("Текст с аккордами"));
        lyricsList.setCellRenderer(new LineRenderer());
        lyricsList.setFixedCellHeight(44);
        lyricsList.setBackground(Color.WHITE);
        lyricsList.setToolTipText("Клик по строке - показать варианты зажатия её аккордов");
        lyricsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = lyricsList.locationToIndex(e.getPoint());
                if (idx < 0 || idx >= listModel.getSize()) return;
                showChordVariationsForLine(listModel.getElementAt(idx));
            }
        });
        panel.add(new JScrollPane(lyricsList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel importPanel = new JPanel(new BorderLayout(4, 4));
        importPanel.setBorder(titled("Импорт текста с аккордами (akkords.pro) - название песни или ссылка"));
        songQueryField.setToolTipText("Например: Кино Звезда по имени Солнце — или прямая ссылка на страницу разбора");
        JButton importBtn = new JButton("Найти и загрузить текст с аккордами");
        JPanel importControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        importControls.add(songQueryField);
        importControls.add(importBtn);
        importPanel.add(importControls, BorderLayout.CENTER);
        importBtn.addActionListener(e -> importSongText());
        bottom.add(importPanel);

        JPanel lyricsPanel = new JPanel(new BorderLayout(4, 4));
        lyricsPanel.setBorder(titled("Текст песни"));
        lyricsInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        lyricsInput.setLineWrap(false);
        lyricsPanel.add(new JScrollPane(lyricsInput), BorderLayout.CENTER);

        JPanel lyricsControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadText = new JButton("Загрузить текст (.txt)");
        JButton applyLyrics = new JButton("Наложить аккорды на текст");
        lyricsControls.add(loadText);
        lyricsControls.add(annotatedBox);
        lyricsControls.add(applyLyrics);
        lyricsPanel.add(lyricsControls, BorderLayout.SOUTH);
        bottom.add(lyricsPanel);

        JPanel playback = new JPanel(new FlowLayout(FlowLayout.LEFT));
        playback.setBorder(titled("Воспроизведение"));
        JButton playBtn = new JButton("Играть");
        JButton pauseBtn = new JButton("Пауза");
        JButton stopBtn = new JButton("Стоп");
        playback.add(playBtn);
        playback.add(pauseBtn);
        playback.add(stopBtn);
        bottom.add(playback);

        loadText.addActionListener(e -> loadTextFile());
        applyLyrics.addActionListener(e -> applyLyrics());
        playBtn.addActionListener(e -> { if (player != null) { player.play(); timer.start(); } });
        pauseBtn.addActionListener(e -> { if (player != null) { player.pause(); timer.stop(); } });
        stopBtn.addActionListener(e -> {
            if (player != null) { player.stop(); timer.stop(); lyricsList.clearSelection(); }
        });

        return bottom;
    }

    private TitledBorder titled(String text) {
        TitledBorder b = BorderFactory.createTitledBorder(text);
        b.setTitleColor(ACCENT);
        return b;
    }

    private void loadMidiFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("MIDI файлы", "mid", "midi"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            List<List<Note>> measures = MidiImporter.importMeasures(file);
            List<Note> all = new ArrayList<>();
            for (List<Note> m : measures) all.addAll(m);
            KeyEstimator.KeyResult kr = KeyEstimator.estimate(all);
            chordsOnly = ChordSuggester.suggestProgression(measures, kr.tonic, kr.scale);
            bpm = TempoEstimator.fromMidi(file);
            player = new MidiPlayer(file);

            lastEvents = new ArrayList<>();
            for (int i = 0; i < chordsOnly.size(); i++) {
                lastEvents.add(new ChordEvent("Такт " + (i + 1), chordsOnly.get(i)));
            }
            double secondsPerMeasure = 4 * 60.0 / bpm;
            timedSheet = buildTimelineFromEvents(lastEvents, false, secondsPerMeasure);
            refreshListModel();

            statusLabel.setText("Загружено: " + file.getName() + " (" + kr.tonic.display()
                    + " " + (kr.scale == ScaleType.MAJOR ? "major" : "minor") + ")");
            bpmLabel.setText("Темп: " + Math.round(bpm) + " BPM");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось загрузить MIDI: " + ex.getMessage());
        }
    }

    private void loadWavFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("WAV файлы", "wav"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            statusLabel.setText("Анализирую аудио...");
            AudioLoader.Samples samples = AudioLoader.load(file);
            List<ChordEvent> events = AudioChordDetector.analyze(samples);
            chordsOnly = new ArrayList<>();
            for (ChordEvent ev : events) if (ev.chord != null) chordsOnly.add(ev.chord);
            bpm = TempoEstimator.fromAudio(samples);
            player = new WavPlayer(file);

            lastEvents = events;
            timedSheet = buildTimelineFromEvents(lastEvents, true, 0);
            refreshListModel();

            statusLabel.setText("Загружено: " + file.getName());
            bpmLabel.setText("Темп: " + Math.round(bpm) + " BPM");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось загрузить WAV: " + ex.getMessage());
        }
    }

    /** Ищет и вытаскивает текст с аккордами с akkords.pro по названию или по прямой ссылке. */
    private void importSongText() {
        String input = songQueryField.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите название песни или ссылку на страницу с аккордами.");
            return;
        }
        try {
            String url = input;
            if (!input.startsWith("http")) {
                statusLabel.setText("Ищу песню на akkords.pro...");
                String found = SongImporter.searchSongUrl(input);
                if (found == null) {
                    JOptionPane.showMessageDialog(this,
                            "Не удалось найти песню автоматически. Откройте akkords.pro, найдите нужную "
                            + "страницу и вставьте её ссылку в это же поле.\n"
                            + "(Учтите: сайты, где аккорды даны картинкой, например pereborom.ru, "
                            + "так не работают — там нужно смотреть вручную.)");
                    return;
                }
                url = found;
            }
            statusLabel.setText("Загружаю текст с аккордами...");
            String html = SongImporter.fetchHtml(url);
            String annotated = SongImporter.extractAnnotatedLyrics(html);
            if (annotated.isBlank()) {
                JOptionPane.showMessageDialog(this,
                        "Не нашёл текст песни на странице. Возможно, аккорды там даны картинкой "
                        + "(как на pereborom.ru) — тогда автоматически не вытащить.");
                return;
            }
            lyricsInput.setText(annotated);
            annotatedBox.setSelected(true);
            statusLabel.setText("Текст с аккордами загружен: " + url);
            if (player != null) {
                applyLyrics();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось загрузить со страницы: " + ex.getMessage());
        }
    }

    /** Клик по строке в списке — показывает вариации аппликатуры для её аккордов. */
    private void showChordVariationsForLine(TimedSheetBuilder.TimedLine line) {
        if (line.chordLine == null || line.chordLine.isBlank()) return;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String t : line.chordLine.trim().split("\\s+")) {
            if (!t.isBlank()) names.add(t);
        }
        if (names.isEmpty()) return;

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (String name : names) {
            Chord chord = Chord.parse(name);
            if (chord == null) continue;
            for (ChordVoicing v : ChordLibrary.variations(chord)) {
                panel.add(new ChordDiagramPanel(chord.name() + " - " + v.label, v.frets));
            }
        }
        if (panel.getComponentCount() == 0) return;
        JOptionPane.showMessageDialog(this, panel, "Варианты аккордов для этой строки", JOptionPane.PLAIN_MESSAGE);
    }

    private void loadTextFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String content = new String(Files.readAllBytes(chooser.getSelectedFile().toPath()), StandardCharsets.UTF_8);
            lyricsInput.setText(content);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось прочитать файл: " + ex.getMessage());
        }
    }

    /** Аккорды сразу после загрузки файла — без текста песни, просто по времени/тактам. */
    private List<TimedSheetBuilder.TimedLine> buildTimelineFromEvents(
            List<ChordEvent> events, boolean parseTimeLabels, double secondsPerUnit) {
        List<TimedSheetBuilder.TimedLine> result = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            ChordEvent e = events.get(i);
            String name = e.chord != null ? e.chord.name() : "-";
            double start, end;
            if (parseTimeLabels) {
                String[] parts = e.label.split("\u2013");
                start = parseTime(parts[0]);
                end = parseTime(parts[1]);
            } else {
                start = i * secondsPerUnit;
                end = (i + 1) * secondsPerUnit;
            }
            result.add(new TimedSheetBuilder.TimedLine(start, end, name, ""));
        }
        return result;
    }

    private double parseTime(String mmss) {
        String[] p = mmss.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private void refreshListModel() {
        listModel.clear();
        for (TimedSheetBuilder.TimedLine tl : timedSheet) listModel.addElement(tl);
    }

    private void applyLyrics() {
        if (player == null) {
            JOptionPane.showMessageDialog(this, "Сначала загрузите MIDI или WAV файл.");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String l : lyricsInput.getText().split("\n")) {
            if (!l.trim().isEmpty()) lines.add(l);
        }
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите или загрузите текст песни.");
            return;
        }
        List<LyricsSheet.Line> sheet = annotatedBox.isSelected()
                ? LyricsSheet.parse(lines)
                : AutoOverlay.overlay(lines, chordsOnly);
        timedSheet = TimedSheetBuilder.build(sheet, player.getDurationSeconds());
        refreshListModel();
    }

    private void tick() {
        if (player == null || timedSheet.isEmpty()) return;
        double pos = player.getPositionSeconds();
        int idx = -1;
        for (int i = 0; i < timedSheet.size(); i++) {
            if (pos >= timedSheet.get(i).start && pos < timedSheet.get(i).end) {
                idx = i;
                break;
            }
        }
        if (idx == -1 && pos >= timedSheet.get(timedSheet.size() - 1).end) {
            idx = timedSheet.size() - 1;
        }
        if (idx >= 0) {
            lyricsList.setSelectedIndex(idx);
            lyricsList.ensureIndexIsVisible(idx);
        }
        if (!player.isPlaying()) timer.stop();
    }

    private class LineRenderer extends JPanel implements ListCellRenderer<TimedSheetBuilder.TimedLine> {
        private final JLabel chordLabel = new JLabel();
        private final JLabel lyricLabel = new JLabel();

        LineRenderer() {
            setLayout(new BorderLayout());
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 15);
            chordLabel.setFont(mono.deriveFont(Font.BOLD));
            chordLabel.setForeground(ACCENT);
            lyricLabel.setFont(mono);
            add(chordLabel, BorderLayout.NORTH);
            add(lyricLabel, BorderLayout.CENTER);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends TimedSheetBuilder.TimedLine> list,
                TimedSheetBuilder.TimedLine value, int index, boolean isSelected, boolean cellHasFocus) {
            chordLabel.setText(value.chordLine.isEmpty() ? " " : value.chordLine);
            lyricLabel.setText(value.lyricLine.isEmpty() ? " " : value.lyricLine);
            setBackground(isSelected ? HIGHLIGHT : Color.WHITE);
            return this;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // остаёмся на теме по умолчанию, если системная недоступна
        }
        SwingUtilities.invokeLater(() -> new MainGui().setVisible(true));
    }
}
