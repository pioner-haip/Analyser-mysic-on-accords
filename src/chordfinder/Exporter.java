package chordfinder;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Сохранение результата: текст с аккордами (.txt) или таблица «время — аккорд» (.csv). */
public class Exporter {
    public static void export(Component parent, Song song, SheetBuilder.Sheet sheet) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Экспорт");
        FileNameExtensionFilter txt = new FileNameExtensionFilter("Текст с аккордами (*.txt)", "txt");
        FileNameExtensionFilter csv = new FileNameExtensionFilter("Таблица «время — аккорд» (*.csv)", "csv");
        chooser.addChoosableFileFilter(csv);
        chooser.addChoosableFileFilter(txt);
        chooser.setFileFilter(txt);
        chooser.setAcceptAllFileFilterUsed(false);
        String base = song.name.replaceFirst("\\.[^.]+$", "");
        chooser.setSelectedFile(new File(base + ".txt"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        boolean asCsv = chooser.getFileFilter() == csv || file.getName().toLowerCase().endsWith(".csv");
        String ext = asCsv ? ".csv" : ".txt";
        if (!file.getName().toLowerCase().endsWith(ext)) file = new File(file.getParentFile(), file.getName() + ext);
        if (file.exists() && JOptionPane.showConfirmDialog(parent, "Файл «" + file.getName() + "» уже есть. Заменить?",
                "Экспорт", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(file.toPath(), (asCsv ? toCsv(sheet) : toText(song, sheet)).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Не удалось сохранить файл: " + e.getMessage(), "Экспорт",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    static String toText(Song song, SheetBuilder.Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append(song.name).append('\n');
        sb.append("Тональность: ").append(song.key.name()).append(", темп: ").append(Math.round(song.bpm)).append(" BPM\n\n");
        sb.append(SheetBuilder.toText(sheet));
        return sb.toString();
    }

    static String toCsv(SheetBuilder.Sheet sheet) {
        StringBuilder sb = new StringBuilder("start_sec,end_sec,chord\n");
        for (SheetBuilder.Slot s : sheet.slots) {
            sb.append(String.format(Locale.ROOT, "%.2f,%.2f,%s%n", s.start, s.end, s.name));
        }
        return sb.toString();
    }
}
