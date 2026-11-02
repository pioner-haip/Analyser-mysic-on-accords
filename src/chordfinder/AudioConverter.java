package chordfinder;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Java Sound читает WAV/AIFF/AU, но не MP3. Если на компьютере установлена программа ffmpeg,
 * вызываем её, чтобы превратить MP3 (и M4A, OGG, FLAC) во временный WAV. Без ffmpeg выдаём понятную подсказку.
 */
public class AudioConverter {
    public static boolean isNativelySupported(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".wav") || n.endsWith(".wave") || n.endsWith(".aif") || n.endsWith(".aiff") || n.endsWith(".au");
    }

    /** Возвращает файл, который умеет читать Java Sound (исходный или временный WAV). */
    public static File toWav(File input) throws Exception {
        if (isNativelySupported(input)) return input;
        File out = File.createTempFile("chordanalyzer-", ".wav");
        out.deleteOnExit();
        Process p;
        try {
            p = new ProcessBuilder("ffmpeg", "-y", "-v", "error", "-i", input.getAbsolutePath(),
                    "-ar", "44100", out.getAbsolutePath()).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IllegalStateException("Формат «" + extension(input) + "» Java напрямую не читает.\n"
                    + "Установите программу ffmpeg (приложение вызовет её само) или сначала сохраните песню как WAV.");
        }
        p.getInputStream().readAllBytes();
        if (!p.waitFor(180, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Конвертация файла заняла слишком много времени.");
        }
        if (p.exitValue() != 0 || out.length() == 0) {
            throw new IllegalStateException("ffmpeg не смог прочитать файл «" + input.getName() + "».");
        }
        return out;
    }

    private static String extension(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "?" : n.substring(dot);
    }
}
