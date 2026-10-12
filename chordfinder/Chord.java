package chordfinder;

import java.util.ArrayList;
import java.util.List;

public class Chord {
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

    /** Разбирает имя вида "Am", "C", "F#m", "C/G" (бас после слэша отбрасывается). */
    public static Chord parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Ha-h])(#|b)?").matcher(s);
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
        String remainder = s.substring(m.end());
        boolean minor = remainder.startsWith("m") && !remainder.startsWith("maj");
        return new Chord(root, minor ? ChordType.MINOR : ChordType.MAJOR);
    }

    @Override
    public String toString() {
        return name();
    }
}
