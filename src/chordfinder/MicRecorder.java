package chordfinder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Font;
import java.awt.Frame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/** Запись с микрофона в временный WAV (44,1 кГц, моно). Окно блокирующее: вернётся, когда запись остановлена. */
public class MicRecorder {
    /** @return временный WAV-файл или null, если запись отменена / микрофон недоступен */
    public static File record(Frame owner) {
        AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        if (!AudioSystem.isLineSupported(info)) {
            JOptionPane.showMessageDialog(owner, "Микрофон не найден или к нему нет доступа.\n"
                    + "Проверьте разрешения для Java в настройках системы.", "Запись", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        final TargetDataLine line;
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(fmt);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(owner, "Не удалось открыть микрофон: " + e.getMessage(), "Запись",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        line.start();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final boolean[] running = {true};
        Thread reader = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (running[0]) {
                int n = line.read(chunk, 0, chunk.length);
                if (n > 0) synchronized (buffer) { buffer.write(chunk, 0, n); }
            }
        }, "mic-reader");
        reader.setDaemon(true);
        reader.start();

        JDialog dialog = new JDialog(owner, "Запись с микрофона", true);
        JLabel status = new JLabel("● Идёт запись… 0:00");
        status.setFont(Theme.font(16f, Font.BOLD));
        status.setForeground(new java.awt.Color(0xC0392B));
        JLabel hint = new JLabel("Сыграйте песню рядом с микрофоном (лучше 20–30 секунд и больше).");
        hint.setFont(Theme.font(12f, Font.PLAIN));
        hint.setForeground(Theme.MUTED);
        JButton stop = Theme.button("Остановить и анализировать", true);
        JButton cancel = Theme.button("Отмена", false);
        final boolean[] accepted = {false};
        long t0 = System.currentTimeMillis();
        Timer timer = new Timer(250, e -> {
            long s = (System.currentTimeMillis() - t0) / 1000;
            status.setText(String.format("● Идёт запись… %d:%02d", s / 60, s % 60));
        });
        timer.start();
        stop.addActionListener(e -> { accepted[0] = true; dialog.dispose(); });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        p.setBackground(Theme.BG);
        p.add(status);
        p.add(Box.createVerticalStrut(6));
        p.add(hint);
        p.add(Box.createVerticalStrut(16));
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(stop);
        row.add(Box.createHorizontalStrut(10));
        row.add(cancel);
        p.add(row);
        dialog.setContentPane(p);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true); // блокируется до закрытия

        timer.stop();
        running[0] = false;
        line.stop();
        line.close();
        if (!accepted[0]) return null;

        byte[] bytes;
        synchronized (buffer) { bytes = buffer.toByteArray(); }
        if (bytes.length < 44100 * 2 * 2) {
            JOptionPane.showMessageDialog(owner, "Запись слишком короткая — нужно хотя бы 2 секунды.", "Запись",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        try {
            File out = File.createTempFile("mic-record-", ".wav");
            out.deleteOnExit();
            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(bytes), fmt, bytes.length / 2);
            AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
            return out;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(owner, "Не удалось сохранить запись: " + e.getMessage(), "Запись",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
