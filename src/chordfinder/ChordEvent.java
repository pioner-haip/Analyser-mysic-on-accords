package chordfinder;

/**
 * Один распознанный аккорд: подпись (номер такта или отрезок времени) и, если известно,
 * точные границы в секундах. Для аудио start/end заданы всегда, для MIDI — по тактам.
 */
public class ChordEvent {
    public final String label;
    public final Chord chord; // может быть null, если аккорд не распознан
    public final double start, end; // секунды; NaN, если время неизвестно

    public ChordEvent(String label, Chord chord) {
        this(label, chord, Double.NaN, Double.NaN);
    }

    public ChordEvent(double start, double end, Chord chord) {
        this(formatTime(start) + "\u2013" + formatTime(end), chord, start, end);
    }

    private ChordEvent(String label, Chord chord, double start, double end) {
        this.label = label;
        this.chord = chord;
        this.start = start;
        this.end = end;
    }

    public double duration() {
        return end - start;
    }

    public static String formatTime(double seconds) {
        int total = (int) Math.max(0, Math.floor(seconds));
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
