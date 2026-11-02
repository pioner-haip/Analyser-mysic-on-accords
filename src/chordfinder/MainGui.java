package chordfinder;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Главное окно «Chord Analyzer». Два экрана (см. макеты): «Старт» — выбрать/перетащить файл или записать звук,
 * «Результат» — три панели с аккордами. Тяжёлый анализ идёт в фоновом потоке, поэтому окно не «замерзает»,
 * а прогресс виден и анализ можно отменить.
 */
public class MainGui extends JFrame {
    private static final String START = "start", RESULT = "result";

    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel status = new JLabel("Готово к анализу");
    private final StartPanel start;
    private final ResultPanel result;
    private final JMenuItem miNew = new JMenuItem("Новая песня");
    private final JMenuItem miExport = new JMenuItem("Экспорт…");
    private final JMenuItem miLyrics = new JMenuItem("Текст песни…");

    private SwingWorker<Song, Double> worker;
    private boolean busy;

    public MainGui() {
        super("Chord Analyzer");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
        });

        start = new StartPanel(new StartPanel.Actions() {
            @Override public void choose() { chooseFile(); }
            @Override public void record() { recordFromMic(); }
            @Override public void open(File file) { openFile(file); }
            @Override public void cancel() { cancelAnalysis(); }
        });
        result = new ResultPanel(this::newSong, this::setStatus);

        center.add(start, START);
        center.add(result, RESULT);
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Theme.BAR);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        status.setFont(Theme.font(12f, Font.PLAIN));
        status.setForeground(Theme.MUTED);
        statusBar.add(status, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(center, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setJMenuBar(buildMenu());
        installDropTarget();
        showScreen(START);

        setMinimumSize(new Dimension(820, 560));
        setSize(940, 680);
        setLocationRelativeTo(null);
    }

    // ---- меню -------------------------------------------------------------------------------

    private JMenuBar buildMenu() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Файл");
        JMenuItem open = new JMenuItem("Открыть файл…");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask));
        open.addActionListener(e -> chooseFile());
        JMenuItem rec = new JMenuItem("Записать с микрофона…");
        rec.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, mask));
        rec.addActionListener(e -> recordFromMic());
        JMenu recent = new JMenu("Недавние");
        recent.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) {
                recent.removeAll();
                List<RecentFiles.Entry> list = RecentFiles.list(8);
                if (list.isEmpty()) {
                    JMenuItem none = new JMenuItem("(пусто)");
                    none.setEnabled(false);
                    recent.add(none);
                }
                for (RecentFiles.Entry en : list) {
                    JMenuItem it = new JMenuItem(en.file.getName());
                    it.addActionListener(ev -> openFile(en.file));
                    recent.add(it);
                }
            }
            @Override public void menuDeselected(MenuEvent e) {}
            @Override public void menuCanceled(MenuEvent e) {}
        });
        miNew.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, mask));
        miNew.addActionListener(e -> newSong());
        miExport.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, mask));
        miExport.addActionListener(e -> result.exportSheet());
        JMenuItem quit = new JMenuItem("Выход");
        quit.addActionListener(e -> quit());
        file.add(open);
        file.add(rec);
        file.add(recent);
        file.addSeparator();
        file.add(miNew);
        file.add(miExport);
        file.addSeparator();
        file.add(quit);

        JMenu edit = new JMenu("Правка");
        miLyrics.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));
        miLyrics.addActionListener(e -> result.editLyrics(this));
        JMenuItem library = new JMenuItem("Справочник аккордов…");
        library.addActionListener(e -> new ChordLibraryDialog(this).setVisible(true));
        edit.add(miLyrics);
        edit.add(library);

        JMenu help = new JMenu("Справка");
        JMenuItem how = new JMenuItem("Как это работает");
        how.addActionListener(e -> showHowItWorks());
        JMenuItem about = new JMenuItem("О программе");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Chord Analyzer\nРаспознавание гитарных аккордов по аудио (WAV/MP3) и MIDI.",
                "О программе", JOptionPane.INFORMATION_MESSAGE));
        help.add(how);
        help.add(about);

        bar.add(file);
        bar.add(edit);
        bar.add(help);
        return bar;
    }

    private void showHowItWorks() {
        JOptionPane.showMessageDialog(this,
                "Как программа находит аккорды:\n"
                + "1. Звук режется на короткие кадры, в каждом считается «хрома» — сколько энергии на каждой из 12 нот.\n"
                + "2. Хрома сравнивается с шаблонами аккордов; бас помогает найти корень.\n"
                + "3. Алгоритм Витерби выбирает самую правдоподобную последовательность: лишние смены «дорого стоят».\n"
                + "4. Аккорды, звучащие меньше ~6% песни, отбрасываются, и разбор повторяется только по оставшимся.\n\n"
                + "Ограничения: бой и ритм не распознаются, септаккорды определяются осторожно, а при сложной\n"
                + "аранжировке (много инструментов и вокал) возможны ошибки. Режим «Упрощённые аккорды» даёт\n"
                + "самый надёжный результат.",
                "Как это работает", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- переключение экранов ------------------------------------------------------------------

    private void showScreen(String name) {
        cards.show(center, name);
        boolean res = RESULT.equals(name);
        miNew.setEnabled(res);
        miExport.setEnabled(res);
        miLyrics.setEnabled(res);
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void newSong() {
        result.closeSong();
        start.setIdle();
        start.refreshRecent();
        setTitle("Chord Analyzer");
        setStatus("Готово к анализу");
        showScreen(START);
    }

    private void quit() {
        cancelAnalysis();
        result.closeSong();
        dispose();
        System.exit(0);
    }

    // ---- открытие файлов -----------------------------------------------------------------------

    private void chooseFile() {
        if (busy) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите песню");
        FileNameExtensionFilter audio = new FileNameExtensionFilter("Аудио и MIDI (wav, mp3, mid)",
                "wav", "wave", "mp3", "m4a", "ogg", "flac", "aif", "aiff", "mid", "midi");
        chooser.addChoosableFileFilter(audio);
        chooser.setFileFilter(audio);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) openFile(chooser.getSelectedFile());
    }

    private void recordFromMic() {
        if (busy) return;
        File wav = MicRecorder.record(this);
        if (wav != null) analyze(wav, "Запись с микрофона", false);
    }

    /** Открыть и проанализировать файл (аудио или MIDI). */
    public void openFile(File file) {
        analyze(file, file.getName(), true);
    }

    private void analyze(File file, String shownName, boolean remember) {
        if (busy) return;
        if (!file.isFile()) {
            JOptionPane.showMessageDialog(this, "Файл не найден:\n" + file, "Открытие", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String lower = file.getName().toLowerCase();
        boolean midi = lower.endsWith(".mid") || lower.endsWith(".midi");

        result.closeSong();   // звук предыдущей песни не должен продолжать играть под экраном «Старт»
        busy = true;
        start.setBusy(shownName);
        showScreen(START);
        setStatus("Анализирую «" + shownName + "»…");

        worker = new SwingWorker<Song, Double>() {
            @Override protected Song doInBackground() throws Exception {
                if (midi) return Song.fromMidi(file);
                File playable = AudioConverter.toWav(file);   // для MP3 вызывает ffmpeg
                return Song.fromAudio(file, playable, this::publishProgress);
            }
            private void publishProgress(double p) { publish(p); }

            @Override protected void process(List<Double> chunks) {
                start.setProgress(chunks.get(chunks.size() - 1));
            }

            @Override protected void done() {
                busy = false;
                try {
                    Song song = get();
                    if (remember) {
                        RecentFiles.add(file);
                        start.refreshRecent();
                    }
                    result.loadSong(song);
                    showScreen(RESULT);
                    setTitle(shownName + " — Chord Analyzer");
                    setStatus("Аккордов: " + song.simple.size() + " · тональность "
                            + song.key.name() + " · темп " + Math.round(song.bpm) + " BPM");
                } catch (CancellationException e) {
                    start.setIdle();
                    setStatus("Анализ отменён");
                } catch (ExecutionException e) {
                    start.setIdle();
                    setStatus("Не удалось проанализировать файл");
                    JOptionPane.showMessageDialog(MainGui.this, explain(e.getCause()), "Ошибка анализа",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    start.setIdle();
                }
            }
        };
        worker.execute();
    }

    private void cancelAnalysis() {
        if (worker != null && !worker.isDone()) worker.cancel(true);
    }

    private static String explain(Throwable t) {
        if (t instanceof UnsupportedAudioFileException) {
            return "Не удалось прочитать файл как аудио.\nПоддерживаются WAV/AIFF/AU, а также MP3 и другие форматы, "
                    + "если на компьютере установлен ffmpeg.";
        }
        if (t instanceof OutOfMemoryError) {
            return "Файл слишком большой для доступной памяти. Запустите программу с параметром -Xmx2g или возьмите отрывок песни.";
        }
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    // ---- перетаскивание файлов -------------------------------------------------------------------

    private void installDropTarget() {
        new DropTarget(getContentPane(), DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void dragEnter(DropTargetDragEvent e) {
                if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && !busy) {
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                    start.setDragOver(true);
                } else {
                    e.rejectDrag();
                }
            }
            @Override public void dragExit(DropTargetEvent e) { start.setDragOver(false); }
            @Override public void drop(DropTargetDropEvent e) {
                start.setDragOver(false);
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    e.dropComplete(true);
                    if (!files.isEmpty()) {
                        File f = files.get(0);
                        SwingUtilities.invokeLater(() -> openFile(f));
                    }
                } catch (Exception ex) {
                    e.dropComplete(false);
                }
            }
            @Override public void dragOver(DropTargetDragEvent e) {}
        });
    }

    // ---- запуск ---------------------------------------------------------------------------------

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // остаёмся на теме по умолчанию, если системная недоступна
        }
        SwingUtilities.invokeLater(() -> {
            MainGui gui = new MainGui();
            gui.setVisible(true);
            if (args.length > 0) gui.openFile(new File(args[0])); // можно запустить сразу с файлом: java ... MainGui song.wav
        });
    }
}
