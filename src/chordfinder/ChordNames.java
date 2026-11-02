package chordfinder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Преобразование ИМЁН аккордов для показа: транспонирование и упрощение.
 * Работает со строками, потому что в текстах песен встречаются аккорды вроде "Asus4" или "C/G",
 * которые наша модель Chord не хранит без потерь.
 */
public class ChordNames {
    private static final Pattern TOKEN = Pattern.compile("^([A-Ha-h])(#|b|♯|♭)?([^/]*)(?:/([A-Ha-h])(#|b|♯|♭)?)?$");

    /** Имя для показа: сдвиг на shift полутонов; при simplified — только мажор/минор без бас-ноты. */
    public static String display(String token, int shift, boolean simplified) {
        if (token == null) return "";
        String t = token.trim();
        Matcher m = TOKEN.matcher(t);
        if (!m.matches()) return t; // не аккорд (например, "N.C.") — показываем как есть

        Note root = note(m.group(1), m.group(2));
        if (root == null) return t;
        String suffix = m.group(3);
        String bass = "";
        if (m.group(4) != null && !simplified) {
            Note b = note(m.group(4), m.group(5));
            if (b != null) bass = "/" + b.transpose(shift).display();
        }
        if (simplified) {
            Chord c = Chord.parse(t);
            if (c == null) return t;
            return c.transpose(shift).simplified().name();
        }
        return root.transpose(shift).display() + suffix + bass;
    }

    private static Note note(String letter, String accidental) {
        String l = letter.toUpperCase();
        if (l.equals("H")) l = "B";
        try {
            return Note.fromString(l + (accidental == null ? "" : accidental));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
