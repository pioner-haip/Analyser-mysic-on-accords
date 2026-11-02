package chordfinder;

/** Общий интерфейс для проигрывания WAV или MIDI, чтобы UI не знал о разнице. */
public interface Player {
    void play();
    void pause();
    void stop();
    /** Перемотка на заданную секунду (можно во время воспроизведения). */
    void seek(double seconds);
    double getPositionSeconds();
    double getDurationSeconds();
    boolean isPlaying();
    /** Освободить звуковые ресурсы (при закрытии песни). */
    void close();
}
