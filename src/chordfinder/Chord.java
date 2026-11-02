package chordfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Chord {
    private static final Pattern ROOT = Pattern.compile("^([A-Ha-h])(#|b|♯|♭)?");

    public final Note root;
    public final ChordType type;

    public Chord(Note root, ChordType type) {
        this.root = root;
        this.type = type;
    }

    /** Ноты, входящие в аккорд. */
    public List<Note> notes() {
        List<Note> result = new ArrayList<>();
        for (int interval : type.intervals) {
            result.add(root.transpose(interval));
        }
        return result;
    }

    public boolean contains(Note n) {
        return notes().contains(n);
    }

    public String name() {
        return root.display() + type.suffix;
    }

    /** Тот же аккорд, сдвинутый на заданное число полутонов (для кнопок «Транспонировать»). */
    public Chord transpose(int semitones) {
        return new Chord(root.transpose(semitones), type);
    }

    /** Трезвучие с тем же корнем: G7 -> G, Am7 -> Am. */
    public Chord simplified() {
        ChordType t = type.triad();
        return t == type ? this : new Chord(root, t);
    }

    /**
     * Разбирает имя вида "Am", "C", "F#m", "G7", "Am7", "Cmaj7", "C/G" (бас после слэша отбрасывается).
     * Незнакомые надстройки (sus4, add9, 6 ...) сводятся к ближайшему из известных типов.
     */
    public static Chord parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.isEmpty()) return null;
        Matcher m = ROOT.matcher(s);
        if (!m.find()) return null;
        String letter = m.group(1).toUpperCase();
        if (letter.equals("H")) letter = "B"; // старая нотация, где H = си
        String accidental = m.group(2);
        Note root;
        try {
            root = Note.fromString(letter + (accidental != null ? accidental : ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Chord(root, parseType(s.substring(m.end())));
    }

    private static ChordType parseType(String r) {
        if (r.startsWith("maj7") || r.startsWith("M7") || r.startsWith("Δ")) return ChordType.MAJ7;
        if (r.startsWith("maj")) return ChordType.MAJOR;
        if (r.startsWith("dim") || r.startsWith("°")) return ChordType.DIMINISHED;
        if (r.matches("^(m|min|-)7.*")) return ChordType.MIN7;
        if (r.startsWith("m") || r.startsWith("min")) return ChordType.MINOR;
        if (r.matches("^(7|9|11|13).*")) return ChordType.DOM7;
        return ChordType.MAJOR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chord)) return false;
        Chord c = (Chord) o;
        return root == c.root && type == c.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, type);
    }

    @Override
    public String toString() {
        return name();
    }
}
