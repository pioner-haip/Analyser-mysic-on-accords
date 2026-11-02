package chordfinder;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Окно «Текст песни»: вставить текст, открыть .txt или найти текст с аккордами на akkords.pro.
 * Если в тексте уже стоят аккорды в [скобках], они используются как есть; иначе на текст
 * раскладываются аккорды, найденные в записи.
 */
public class LyricsDialog {
    public static class Result {
        public final String text;
        public final boolean annotated;
        Result(String text, boolean annotated) { this.text = text; this.annotated = annotated; }
    }

    /** @return null, если окно закрыли кнопкой «Отмена» */
    public static Result show(Frame owner, String currentText, boolean annotated) {
        JDialog dialog = new JDialog(owner, "Текст песни", true);
        JTextArea area = new JTextArea(currentText == null ? "" : currentText, 16, 56);
        area.setFont(Theme.font(14f, Font.PLAIN));
        JCheckBox annotatedBox = new JCheckBox("В тексте уже есть аккорды в [квадратных скобках]", annotated);
        JLabel status = new JLabel(" ");
        status.setForeground(Theme.MUTED);
        status.setFont(Theme.font(12f, Font.PLAIN));

        // поиск на akkords.pro
        JTextField query = new JTextField(30);
        query.setToolTipText("Например: Кино Звезда по имени Солнце — или ссылка на страницу с аккордами");
        javax.swing.JButton find = Theme.button("Найти текст с аккордами", false);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("akkords.pro:"));
        top.add(query);
        top.add(find);

        final Result[] result = {null};
        find.addActionListener(e -> {
            String input = query.getText().trim();
            if (input.isEmpty()) { status.setText("Введите название песни или ссылку."); return; }
            find.setEnabled(false);
            status.setText("Ищу и загружаю…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception {
                    String url = input;
                    if (!input.startsWith("http")) {
                        url = SongImporter.searchSongUrl(input);
                        if (url == null) throw new IllegalStateException("Песню найти не удалось. Откройте страницу на akkords.pro "
                                + "и вставьте её ссылку в это поле (сайты, где аккорды — картинки, не подходят).");
                    }
                    return SongImporter.extractAnnotatedLyrics(SongImporter.fetchHtml(url));
                }
                @Override protected void done() {
                    find.setEnabled(true);
                    try {
                        String text = get();
                        if (text.isBlank()) { status.setText("На странице не нашлось текста с аккордами."); return; }
                        area.setText(text);
                        area.setCaretPosition(0);
                        annotatedBox.setSelected(true);
                        status.setText("Текст с аккордами загружен.");
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText("Ошибка: " + c.getMessage());
                    }
                }
            }.execute();
        });

        javax.swing.JButton openTxt = Theme.button("Открыть .txt…", false);
        openTxt.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Текстовые файлы", "txt"));
            if (fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            try {
                area.setText(new String(Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8));
                area.setCaretPosition(0);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Не удалось прочитать файл: " + ex.getMessage());
            }
        });
        javax.swing.JButton clear = Theme.button("Убрать текст", false);
        clear.addActionListener(e -> area.setText(""));
        javax.swing.JButton ok = Theme.button("Применить", true);
        javax.swing.JButton cancel = Theme.button("Отмена", false);
        ok.addActionListener(e -> { result[0] = new Result(area.getText(), annotatedBox.isSelected()); dialog.dispose(); });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel bottomRow = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        left.add(openTxt);
        left.add(clear);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.add(cancel);
        right.add(ok);
        bottomRow.add(left, BorderLayout.WEST);
        bottomRow.add(right, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        opts.add(annotatedBox);
        south.add(opts, BorderLayout.NORTH);
        south.add(status, BorderLayout.CENTER);
        south.add(bottomRow, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(area), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }
}
