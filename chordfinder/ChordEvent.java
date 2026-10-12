package chordfinder;

/** Один распознанный аккорд с подписью (номер такта или временной отрезок). */
public class ChordEvent {
    public final String label;
    public final Chord chord; // может быть null, если аккорд не распознан

    public ChordEvent(String label, Chord chord) {
        this.label = label;
        this.chord = chord;
    }
}
