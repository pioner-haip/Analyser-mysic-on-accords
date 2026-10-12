package chordfinder;

/**
 * Нота как звуковысотный класс (0..11), C = 0.
 */
public enum Note {
    C(0), CS(1), D(2), DS(3), E(4), F(5), FS(6), G(7), GS(8), A(9), AS(10), B(11);

    public final int pitchClass;

    Note(int p) {
        this.pitchClass = p;
    }

    /** Транспонирует ноту на заданное число полутонов. */
    public Note transpose(int semitones) {
        int idx = (this.ordinal() + semitones) % 12;
        if (idx < 0) idx += 12;
        return values()[idx];
    }

    /** Разбор ноты из строки: C, C#, Db, F#, Bb и т.д. */
    public static Note fromString(String s) {
        String t = s.trim().toUpperCase().replace("♯", "#").replace("♭", "B");
        switch (t) {
            case "C": return C;
            case "C#": case "DB": return CS;
            case "D": return D;
            case "D#": case "EB": return DS;
            case "E": return E;
            case "F": return F;
            case "F#": case "GB": return FS;
            case "G": return G;
            case "G#": case "AB": return GS;
            case "A": return A;
            case "A#": case "BB": return AS;
            case "B": return B;
            default: throw new IllegalArgumentException("Неизвестная нота: " + s);
        }
    }

    /** Отображаемое имя (с диезом). */
    public String display() {
        switch (this) {
            case CS: return "C#";
            case DS: return "D#";
            case FS: return "F#";
            case GS: return "G#";
            case AS: return "A#";
            default: return name();
        }
    }
}
