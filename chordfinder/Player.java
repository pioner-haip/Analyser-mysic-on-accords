package chordfinder;

/** Общий интерфейс для проигрывания WAV или MIDI, чтобы UI не знал о разнице. */
public interface Player {
    void play();
    void pause();
    void stop();
    double getPositionSeconds();
    double getDurationSeconds();
    boolean isPlaying();
}
